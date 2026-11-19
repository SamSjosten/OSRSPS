package osrsps.game.content

import osrsps.game.GroundObjs
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.combat.Drop
import osrsps.game.npc.Npc

/**
 * Scurrius's loot and kill count (Scurrius step 3), from the OSRS Wiki's Scurrius page, Drops (read 2026-11-19):
 *
 * - **MVP and non-MVP**: "only the player who deals the most damage" rolls the MVP table (npc-drops.json's entry for
 *   both versions); everyone else who damaged him rolls the non-MVP table (the chest table [NON_MVP_TABLE]). In a
 *   private lair the player is always the MVP. INFERRED: a tie goes to whoever hit him first, as the Wilderness bosses'
 *   does ([WildernessBosses]); only players logged in at the kill get anything.
 * - **The spine**: "only one player can receive a Scurrius' spine per kill". Both tables roll it at 1/33; the MVP's
 *   roll is looked at first, then the others' in order of damage, and any spine after the first is taken out.
 *   INFERRED: that order.
 * - **The pet**, Scurry at 1/3,000, is on the MVP table alone, as on live.
 * - **Where it lands**: on the centre of his 3x3 body, except long bones, curved bones and medium clues, on his
 *   south-west tile (the Wiki's words).
 * - **How long it stays**: three minutes in the public lair ([PUBLIC_DESPAWN_TICKS]); in a private lair big bones and
 *   raw rat meat go after two and a half ([PRIVATE_FOOD_DESPAWN_TICKS]) and the rest "stays on the ground longer".
 *   GUESSED: [PRIVATE_DESPAWN_TICKS], thirty minutes. Public loot is each looter's alone for the usual
 *   [GroundObjs.DROP_PRIVATE_TICKS] first; private loot is never anyone else's.
 * - **Kill count** (varp [Scurrius.KILL_COUNT_VARP], the cache's): every player who damaged him and is logged in at the
 *   kill counts it and is told the count. INFERRED: the count going to every damager, not only the MVP (the Wiki says
 *   only that Ironmen get it); the line's words (no transcript).
 */
class ScurriusLoot(private val world: World) {
    /** Damage dealt to each living Scurrius, by save key, in the order they first hit him. */
    private val damage = HashMap<Npc, LinkedHashMap<String, Int>>()

    /** Each dead Scurrius's damagers logged in at the kill, most damage first, kept until his body goes. */
    private val atKill = HashMap<Npc, List<String>>()

    /** [player] dealt [dealt] to [npc]: counted if it is Scurrius. */
    fun damaged(npc: Npc, player: Player, dealt: Int) {
        if (npc.type.id !in TYPES || dealt <= 0) return
        damage.getOrPut(npc) { LinkedHashMap() }.merge(player.saveKey, dealt, Int::plus)
    }

    /** [npc] died: who gets what is decided now, and each of them counts the kill. */
    fun died(npc: Npc) {
        val online = world.onlinePlayers.filter { !it.loggedOut }.associateBy { it.saveKey }
        // Ties keep the order of first hits: sortedByDescending is stable.
        val keys = damage.remove(npc).orEmpty().entries.filter { it.key in online }.sortedByDescending { it.value }.map { it.key }
        atKill[npc] = keys
        for (key in keys) {
            val player = online.getValue(key)
            if (Scurrius.KILL_COUNT_VARP !in 0 until player.vars.types.varpCount) continue
            player.vars[Scurrius.KILL_COUNT_VARP] = player.vars[Scurrius.KILL_COUNT_VARP] + 1
            world.message(player, ScurriusLairLines.KILL_COUNT, "count" to player.vars[Scurrius.KILL_COUNT_VARP])
        }
    }

    /** [npc] has left the world: a Scurrius taken out alive loses his tally. */
    fun removed(npc: Npc) {
        if (npc.hitpoints > 0) {
            damage.remove(npc)
            atKill.remove(npc)
        }
    }

    /**
     * Puts down [npc]'s loot if it is Scurrius, and returns true; false leaves any other NPC to the usual drop. With no
     * damager logged in, the MVP table goes down for everyone (guessed, as any NPC's drop with no killer).
     */
    fun loot(npc: Npc): Boolean {
        if (npc.type.id !in TYPES) return false
        val keys = atKill.remove(npc) ?: damage.remove(npc).orEmpty().keys.toList()
        val private = world.scurriusLairs.runAt(npc.x, npc.z) != null
        val mvpTable = world.npcDrops[npc.type]
        val others = world.npcDrops.chests[NON_MVP_TABLE]
        if (keys.isEmpty()) {
            mvpTable?.roll(world.random)?.forEach { put(npc, it, null, private) }
            return true
        }
        var spine = false
        keys.forEachIndexed { i, key ->
            val rolled = (if (i == 0) mvpTable else others)?.roll(world.random).orEmpty()
            for (drop in rolled) {
                if (drop.type.id == SPINE) {
                    if (spine) continue
                    spine = true
                }
                put(npc, drop, key, private)
            }
        }
        return true
    }

    private fun put(npc: Npc, drop: Drop, owner: String?, private: Boolean) {
        val corner = drop.type.id in ON_CORNER
        val x = if (corner) npc.x else npc.x + npc.type.size / 2
        val z = if (corner) npc.z else npc.z + npc.type.size / 2
        val despawn = when {
            !private -> PUBLIC_DESPAWN_TICKS
            drop.type.id in FOOD -> PRIVATE_FOOD_DESPAWN_TICKS
            else -> PRIVATE_DESPAWN_TICKS
        }
        if (owner == null) {
            world.groundObjs.addPublic(drop.type, drop.count, x, z, npc.level, despawn)
            return
        }
        world.groundObjs.addPrivate(drop.type, drop.count, x, z, npc.level, owner, GroundObjs.DROP_PRIVATE_TICKS, despawn, neverPublic = private || !drop.type.tradeable)
        world.onlinePlayers.firstOrNull { it.saveKey == owner }?.let { world.lootNotifications.dropped(it, drop.type, drop.count) }
    }

    companion object {
        val TYPES = setOf(Scurrius.BOSS_PUBLIC, Scurrius.BOSS_PRIVATE)
        const val NON_MVP_TABLE = "scurrius_non_mvp"
        const val SPINE = 28798
        const val PET = 28801
        const val BIG_BONES = 532
        const val RAW_RAT_MEAT = 2134
        const val LONG_BONE = 10976
        const val CURVED_BONE = 10977
        const val MEDIUM_CLUE = 2801
        val ON_CORNER = setOf(LONG_BONE, CURVED_BONE, MEDIUM_CLUE)
        val FOOD = setOf(BIG_BONES, RAW_RAT_MEAT)
        const val PUBLIC_DESPAWN_TICKS = 300
        const val PRIVATE_FOOD_DESPAWN_TICKS = 250
        const val PRIVATE_DESPAWN_TICKS = 3000
    }
}
