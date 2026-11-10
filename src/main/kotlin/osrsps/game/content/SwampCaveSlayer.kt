package osrsps.game.content

import osrsps.game.Player
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.CombatLines
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc

/** The rockslugs' and wall beasts' words, in the dialogue file. */
object SwampCaveSlayerLines : Lines("swamp_slayer", "OSRS Wiki, Rockslug, Bag of salt and Wall beast, read 2026-11-10") {
    val SALT_KILLS = line("salt_kills", "a bag of salt used on a rockslug at 4 hitpoints or less: it shrivels up and dies; words unsourced")
    val SALT_WASTED = line("salt_wasted", "a bag of salt used on a rockslug at 5 or more: the bag is used up for nothing; words unsourced")
    val HELMET_REPELS = line("helmet_repels", "a wall beast's grab repelled by a spiny or slayer helmet (the Wall beast page's message)")
    val GRABBED = line("grabbed", "a wall beast grabbing a player with neither helmet; words unsourced")
}

/**
 * The Lumbridge Swamp Caves' rockslugs (Misthalin piece 7 step 3b), from the OSRS Wiki's Rockslug and Bag of salt pages:
 * no hit takes one below 1 hitpoint ([osrsps.game.combat.NpcCombatDef.minHitpoints]), and down there it stops fighting
 * and wanders off. A bag of salt used on one at 4 or less finishes it; on one at 5 or more the bag is used up for
 * nothing; someone else's rockslug can't be salted in the single-way caves. A brine sabre wielded kills them outright ([osrsps.game.combat.Combat.finishers]).
 *
 * Guessed: the messages; no animation for the salting; no XP for the salt's kill. Out: the Slug salter perk (Slayer
 * rewards aren't built).
 */
object Rockslugs {
    val SLUGS = listOf(421, 422)
    const val BAG_OF_SALT = 4161
    const val BRINE_SABRE = 11037
    /** The most hitpoints a bag of salt finishes (the Wiki: "4 or less"). */
    const val SALT_AT = 4
}

fun registerRockslugs(world: World) {
    for (slug in Rockslugs.SLUGS) {
        world.combat.finishers[slug] = { player -> player.equipment[WearPos.WEAPON]?.id == Rockslugs.BRINE_SABRE }
        world.handlers.onObjOnNpc(slug, Rockslugs.BAG_OF_SALT) { op ->
            val npc = op.npc ?: return@onObjOnNpc
            if (npc.outOfPlay || world.npcs[npc.index] !== npc) return@onObjOnNpc
            if (world.combat.slayerGated(player, npc)) {
                world.message(player, CombatLines.SLAYER_LEVEL)
                return@onObjOnNpc
            }
            // Single-way: not someone else's rockslug, nor while another monster fights them, as any attack.
            if (world.meleeCombat.underAttackByAnother(player, npc)) {
                world.message(player, CombatLines.ALREADY_UNDER_ATTACK)
                return@onObjOnNpc
            }
            if (world.meleeCombat.foughtByAnother(player, npc)) {
                world.message(player, CombatLines.SOMEONE_ELSE)
                return@onObjOnNpc
            }
            val slot = op.sub.takeIf { it >= 0 && player.inventory[it]?.id == Rockslugs.BAG_OF_SALT } ?: return@onObjOnNpc
            player.inventory.remove(slot, 1)
            if (npc.hitpoints <= Rockslugs.SALT_AT) {
                world.message(player, SwampCaveSlayerLines.SALT_KILLS)
                world.meleeCombat.hit(npc, player, npc.hitpoints, world.tick, finishing = true)
            } else {
                world.message(player, SwampCaveSlayerLines.SALT_WASTED)
            }
        }
    }
}

/**
 * The Lumbridge Swamp Caves' wall beasts (Misthalin piece 7 step 3b), from the OSRS Wiki's Wall beast page. Each sits in
 * a hole in the wall ([HOLE], no options, never moving) and reaches for whoever steps onto the tile in front of it, the
 * one south of the hole for all seven (the 242 map: that is the only cave tile beside each). A player wearing a spiny
 * helmet or any slayer helmet repels it ("Your helmet repels the wall beast!") and it comes out as the attackable
 * [BEAST] and fights them; anyone else is grabbed: held for [GRAB_TICKS] and hurt for up to [GRAB_MAX_HIT] (the Wiki's
 * 18 without a spiny helmet) without a chance to fight back. Hurting one needs Slayer 35 (npc-combat.json). Out of a
 * fight for [BACK_TICKS], a beast goes back into its hole.
 *
 * Guessed: the grab's length, its even roll from 0 to 18 and its message; one grab each time the tile is stepped onto,
 * not again while the player stays; the animations used (the cache's wall_beast_specialattack_* and
 * human_wall_beast_specialattack_*); the ticks before it goes back in.
 */
class WallBeasts(private val world: World) {
    companion object {
        const val HOLE = 475
        const val BEAST = 476
        const val GRAB_TICKS = 4
        const val GRAB_MAX_HIT = 18
        const val BACK_TICKS = 20
        const val ANIM_GRAB = 1802
        const val ANIM_REPELLED = 1805
        const val ANIM_PLAYER_GRABBED = 1809
        const val SPINY_HELMET = 4551
    }

    /** By hole, the players it has reached for, each until they step off its tile. */
    private val reached = HashMap<Npc, MutableSet<Player>>()

    /** The grab's damage, 0 to [GRAB_MAX_HIT]; a test can fix it. */
    @Volatile
    internal var roll: () -> Int = { world.random.nextInt(GRAB_MAX_HIT + 1) }

    /** By beast, the last tick it had someone to fight. */
    private val lastFought = HashMap<Npc, Long>()

    /** Whether [player] wears a helmet that repels a wall beast: a spiny helmet, or any slayer helmet by its name. */
    fun helmeted(player: Player): Boolean {
        val worn = player.equipment[WearPos.HEAD] ?: return false
        if (worn.id == SPINY_HELMET) return true
        val name = world.objTypes[worn.id]?.name?.lowercase() ?: return false
        return name.endsWith("slayer helmet") || name.endsWith("slayer helmet (i)")
    }

    /** The hole whose front tile [player] stands on, if one is there and in it. */
    private fun holeFacing(player: Player): Npc? {
        if (player.level != 0) return null
        return world.npcs.near(player.x, player.z + 1, 0, 0).firstOrNull {
            it.spawn.type.id == HOLE && it.x == player.x && it.z == player.z + 1 && !it.outOfPlay
        }
    }

    /** One tick for [player], after their step: a hole in front of them reaches for them. */
    fun process(player: Player) {
        val facing = holeFacing(player)
        for ((hole, players) in reached) if (hole !== facing) players.remove(player)
        reached.values.removeIf { it.isEmpty() }
        if (player.dead || player.loggedOut) return
        val hole = facing ?: return
        if (hole.transformedTo != null) return
        if (!reached.getOrPut(hole) { HashSet() }.add(player)) return
        if (helmeted(player)) {
            world.message(player, SwampCaveSlayerLines.HELMET_REPELS)
            val beast = world.npcTypes[BEAST] ?: return
            world.npcs.transform(hole, beast, BACK_TICKS)
            hole.effects.anim(ANIM_REPELLED)
            world.meleeCombat.aggress(hole, player, world.tick)
        } else {
            grab(player, hole, roll())
        }
    }

    /**
     * [hole] grabs [player] for [damage]. The hit comes from the hole, a 0 too, so it puts them in combat (no logging
     * out); the stun keeps them from hitting back.
     */
    private fun grab(player: Player, hole: Npc, damage: Int) {
        hole.effects.anim(ANIM_GRAB)
        player.anim(ANIM_PLAYER_GRABBED)
        world.message(player, SwampCaveSlayerLines.GRABBED)
        val held = world.statusEffects.stun(player, GRAB_TICKS, damage, source = hole, spotanim = -1)
        // The stun deals only a hit over 0.
        if (held && damage == 0) world.meleeCombat.hitPlayer(player, hole, 0, world.tick)
    }

    /**
     * Once a tick: a beast goes back in [BACK_TICKS] after its fight's last tick, counting from whichever came last:
     * it fighting someone (even one who never hit back), being hit, or a player's attack at it setting off (so a shot
     * or spell still in the air never lands on the hole, which can't be fought).
     */
    fun keepFighting() {
        val players = world.onlinePlayers
        lastFought.keys.removeIf { it.transformedTo?.id != BEAST || it.dead || world.npcs[it.index] !== it }
        for (npc in world.npcs.all) {
            if (npc.spawn.type.id != HOLE || npc.transformedTo?.id != BEAST || npc.outOfPlay) continue
            if (npc.target != null) {
                lastFought[npc] = world.tick
                npc.transformTicks = ticksLeft(world.tick, world.tick)
                continue
            }
            var last = maxOf(npc.attackedTick, lastFought[npc] ?: Long.MIN_VALUE)
            for (player in players) if (player.attackedNpc === npc) last = maxOf(last, player.attackedNpcTick)
            npc.transformTicks = ticksLeft(world.tick, last)
        }
    }

    /**
     * The countdown to set on [now] for a beast whose fight's last tick was [last]: one more than what is left of
     * [BACK_TICKS], as [osrsps.game.npc.Npcs.countDownTransforms] takes one off straight after, so it goes back in on
     * [last] + [BACK_TICKS] exactly.
     */
    internal fun ticksLeft(now: Long, last: Long): Int = (BACK_TICKS + 1 - (now - last)).coerceIn(1L, BACK_TICKS + 1L).toInt()

    /** Forgets [player] as they leave the world. */
    fun forget(player: Player) {
        reached.values.forEach { it.remove(player) }
        reached.values.removeIf { it.isEmpty() }
    }
}
