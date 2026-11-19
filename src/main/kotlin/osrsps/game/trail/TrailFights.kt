package osrsps.game.trail

import osrsps.game.GroundObjs
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn

/**
 * The fights of Treasure Trails (step TT7): a coordinate's wizard (or a hot-cold step's guardian) and an emote's double
 * agent (the clue helper's combat_encounter: the NPCs that fight), and the monsters that drop a key step's key
 * (cluehelper_target_key). Sources: the Wiki's Treasure Trails, coordinate and emote clue guides and Double agent page,
 * read 2026-11-19.
 *
 * - Dig on a dig step with a fight, or the emote of an emote step with one, brings its NPCs out beside the player, going
 *   for them; nothing else happens until they are dead. Once the last falls the step's fight is won (kept on the trail
 *   and saved), and the same Dig or emote then counts as it would with no fight.
 * - The fight ends with no win if the player logs out, leaves it for [FIGHT_TICKS], or the trail moves on; its NPCs
 *   go, and the next Dig or emote brings them back.
 * - One of a key step's monsters, killed by a player holding that step's clue but not its key (backpack or bank),
 *   drops the key under it, for that player only. Search or Open on the step's chest or drawers with the key takes it
 *   and gives the next clue; without it the player is told it's locked.
 *
 * Guessed: all of an encounter's NPCs come at once; they come on free tiles beside the player with no spotanim or shout;
 * how long they wait; the key dropped every kill, under the monster; the key used up.
 */
class TrailFights(private val world: World, private val trails: TreasureTrails) {
    private class Fight(val tier: TrailTier, val step: String, var until: Long, val npcs: MutableList<Npc>)

    private val fights = HashMap<Player, Fight>()

    /** Whether [player] has [step]'s fight under way. */
    fun fighting(player: Player, step: ClueStep): Boolean = fights[player]?.step == step.key

    /**
     * Brings out [step]'s fight for [player], who holds its clue of [tier], unless it is under way already (then it just
     * waits longer). False when none of its NPCs could come.
     */
    fun start(player: Player, tier: TrailTier, step: ClueStep): Boolean {
        fights[player]?.let { fight ->
            if (fight.step == step.key) {
                fight.until = world.tick + FIGHT_TICKS
                return true
            }
            end(player)
        }
        val tiles = listOf(1 to 0, 0 to 1, -1 to 0, 0 to -1, 1 to 1, -1 to -1, 1 to -1, -1 to 1)
            .map { (dx, dz) -> player.x + dx to player.z + dz }.filter { (x, z) -> world.canStand(x, z, player.level) }
        val npcs = ArrayList<Npc>()
        for ((i, id) in step.combatNpcs.withIndex()) {
            val type = world.npcTypes[id] ?: continue
            val (x, z) = tiles.getOrNull(i) ?: (player.x to player.z)
            val npc = world.spawnNpc(NpcSpawn(type, x, z, player.level, source = "trail fight", respawns = false)) ?: continue
            world.meleeCombat.aggress(npc, player, world.tick)
            npcs += npc
        }
        if (npcs.isEmpty()) return false
        fights[player] = Fight(tier, step.key, world.tick + FIGHT_TICKS, npcs)
        return true
    }

    /** An NPC of a fight died: when it was the last, its player's step's fight is won. */
    fun died(npc: Npc) {
        val (player, fight) = fights.entries.firstOrNull { npc in it.value.npcs }?.toPair() ?: return
        fight.npcs.remove(npc)
        if (fight.npcs.isNotEmpty()) return
        fights.remove(player)
        player.trails[fight.tier]?.takeIf { it.step == fight.step }?.fought = true
    }

    /**
     * One of a key step's monsters died: [killer] (its last attacker), holding that step's clue but not its key, gets
     * the key under it.
     */
    fun dropKey(npc: Npc, killer: Player?) {
        killer ?: return
        for ((_, obj) in killer.inventory.contents()) {
            val tier = trails.tierOf(obj.id) ?: continue
            val step = trails.data.byItem[obj.id] ?: continue
            val key = step.targets.first() as? TrailTarget.Key ?: continue
            if (npc.type.id !in key.npcs || step !in trails.pool(tier) || killer.trails[tier]?.step != step.key) continue
            if (killer.inventory.count(key.key) > 0 || killer.bank.count(key.key) > 0) return
            val type = world.objTypes[key.key] ?: return
            world.groundObjs.addPrivate(type, 1, npc.x, npc.z, npc.level, killer.saveKey, GroundObjs.DROP_PRIVATE_TICKS, GroundObjs.DROP_DESPAWN_TICKS)
            return
        }
    }

    /** Ends fights whose player has gone, waited too long or moved on. Run once a tick. */
    fun process() {
        val over = fights.filter { (player, fight) -> player.loggedOut || world.tick >= fight.until || player.trails[fight.tier]?.step != fight.step }
        for (player in over.keys) end(player)
    }

    private fun end(player: Player) {
        val fight = fights.remove(player) ?: return
        for (npc in fight.npcs) if (!npc.dead) world.removeNpc(npc)
    }

    companion object {
        /** How long a fight's NPCs wait for the player, in ticks. Guessed. */
        const val FIGHT_TICKS = 200
    }
}
