package osrsps.game.content

import osrsps.game.GameRandom
import osrsps.game.GroundObjs
import osrsps.game.Player
import osrsps.game.SettingsSide
import osrsps.game.World
import osrsps.game.combat.Drop
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc

/** The Wilderness bosses' shared words (keys only; the words are in data/dialogue.txt). */
object WildernessBossLines : Lines("wilderness_boss", "Kill count") {
    val KILL_COUNT = line("kill_count", "a Wilderness boss killed: the player's kill count of it (the Wiki's Kill count page)", "boss", "count")
}

/**
 * A Wilderness boss (piece 4): its NPC [types], the name `::wboss` takes, its kill count's varp (the cache's
 * `total_<boss>_kills`, -1 for none), whether a kill's loot is [group] loot split by damage, and its [uniques], the items
 * that drop at most once a kill.
 */
class WildernessBoss(
    val name: String,
    val types: Set<Int>,
    val killCountVarp: Int,
    val group: Boolean = false,
    val uniques: Set<Int> = emptySet(),
)

/**
 * The rules every Wilderness boss shares (Wilderness step W4.1):
 *
 * - **Group loot** (the Wiki's Callisto, Vet'ion and Venenatis pages: "drops, including uniques, will be split
 *   proportionally based on damage done. Up to 10 players may obtain drops per kill in this manner, and each of the top
 *   10 players will receive at least 25% of the solo loot quantity. Ironmen must deal 7% of the overall damage to be
 *   eligible for 25% of the solo loot quantity. Ironmen dealing less than 7% damage will receive a minimum of 5%"). The
 *   top 10 by damage each get their own roll of the boss's table, every count scaled to their share of the damage (at
 *   least 25%, or an Ironman's 5% under 7%, at least 1), on the boss's tile and theirs alone at first. INFERRED: each
 *   player's own roll, and how uniques are split: they come out of the players' rolls, and one more roll for the kill
 *   gives at most one, to a player picked with odds of their share of the damage, so a kill's unique chance is a solo
 *   kill's.
 * - **Kill counts**: every player who gets loot (the killer, for a boss without group loot) counts the kill in its
 *   varp and is told it. INFERRED: the count going to every looter of a group kill.
 * - **`::wboss`** spawns any boss built so far next to the player, for tests.
 *
 * Each boss step registers its boss here ([register]).
 */
class WildernessBosses(private val world: World) {
    private val byType = HashMap<Int, WildernessBoss>()
    private val byName = LinkedHashMap<String, WildernessBoss>()

    /**
     * Damage dealt to each living boss NPC, by player save key, in the order they first hit it, and which of them hit it
     * as Ironmen, kept with the damage so one who logs out before the kill keeps their 5% floor.
     */
    private class Tally {
        val damage = LinkedHashMap<String, Int>()
        val ironmen = HashSet<String>()
    }

    private val damage = HashMap<Npc, Tally>()

    val all: Collection<WildernessBoss> get() = byName.values

    fun register(boss: WildernessBoss) {
        require(boss.name.lowercase() !in byName) { "${boss.name} registered twice" }
        byName[boss.name.lowercase()] = boss
        for (type in boss.types) byType[type] = boss
        world.npcScripts.onDeath(boss.types) { npc -> died(npc, boss) }
    }

    operator fun get(name: String): WildernessBoss? = byName[name.lowercase()]

    /** [player] dealt [dealt] to [npc]: counted toward its loot if it is a boss. */
    fun damaged(npc: Npc, player: Player, dealt: Int) {
        if (byType[npc.type.id] == null) return
        val tally = damage.getOrPut(npc) { Tally() }
        tally.damage.merge(player.saveKey, dealt, Int::plus)
        if (ironman(player)) tally.ironmen += player.saveKey
    }

    /**
     * [npc] has left the world. A boss taken out alive (`::delnpc`, say) gives no loot, so its tally goes now; a dead
     * one's stays for [loot], which comes straight after its body goes.
     */
    fun removed(npc: Npc) {
        if (npc.hitpoints > 0) damage.remove(npc)
    }

    /** How many boss NPCs have a damage tally, for tests: none once each has died or gone. */
    internal val tallies: Int get() = damage.size

    private fun died(npc: Npc, boss: WildernessBoss) {
        val looters = if (boss.group) shares(npc).map { it.key } else listOfNotNull((npc.attacker ?: npc.lastAttacker)?.saveKey)
        if (boss.killCountVarp < 0) return
        for (key in looters) {
            val player = world.onlinePlayers.firstOrNull { it.saveKey == key } ?: continue
            if (boss.killCountVarp !in 0 until player.vars.types.varpCount) continue
            player.vars[boss.killCountVarp] = player.vars[boss.killCountVarp] + 1
            world.message(player, WildernessBossLines.KILL_COUNT, "boss" to npc.type.name, "count" to player.vars[boss.killCountVarp])
        }
    }

    private fun shares(npc: Npc): List<Share> {
        val tally = damage[npc] ?: return emptyList()
        return shares(tally.damage, tally.ironmen)
    }

    private fun ironman(player: Player): Boolean =
        player.vars.types.varbit(SettingsSide.VARBIT_IRONMAN) != null && player.vars.getVarbit(SettingsSide.VARBIT_IRONMAN) != 0

    /**
     * Puts down [npc]'s loot if it is a boss with group loot, and returns true; false leaves it to the usual drop. Its
     * damage tally goes either way.
     */
    fun loot(npc: Npc): Boolean {
        // Most NPCs that die are no boss: they cost one lookup here, no more.
        val boss = byType[npc.type.id] ?: return false
        val shares = shares(npc)
        damage.remove(npc)
        if (!boss.group) return false
        val def = world.npcDrops[npc.type] ?: return true
        val drops = split(shares, boss.uniques, world.random) { def.roll(world.random) }
        for ((key, items) in drops) {
            val player = world.onlinePlayers.firstOrNull { it.saveKey == key }
            for (drop in items) {
                world.groundObjs.addPrivate(drop.type, drop.count, npc.x, npc.z, npc.level, key, GroundObjs.DROP_PRIVATE_TICKS, GroundObjs.LOOT_DESPAWN_TICKS)
                player?.let { world.lootNotifications.dropped(it, drop.type, drop.count) }
            }
        }
        return true
    }

    /** [key]'s share of a kill: [damage] of [total], and the least part of a solo kill's loot it gets, in percent. */
    data class Share(val key: String, val damage: Int, val total: Int, val floorPercent: Int) {
        /** The part of a solo kill's counts this share gets, from 0 to 1 (for reading; [scale] counts exactly). */
        val factor: Double get() = maxOf(damage.toDouble() / total, floorPercent / 100.0)

        /** [count] scaled to this share, rounded down in whole numbers (no floating point), never below 1. */
        fun scale(count: Int): Int {
            val part = maxOf(damage.toLong() * 100, floorPercent.toLong() * total)
            return maxOf(1L, count.toLong() * part / (100L * total)).toInt()
        }
    }

    companion object {
        const val MAX_LOOTERS = 10
        const val FLOOR_PERCENT = 25
        const val IRONMAN_ELIGIBLE_PERCENT = 7
        const val IRONMAN_FLOOR_PERCENT = 5

        /**
         * The top [MAX_LOOTERS] of [damage] (by save key; ties to whoever hit first), each with its floor: 25%, or 5%
         * for one of [ironmen] under 7% of the damage.
         */
        fun shares(damage: Map<String, Int>, ironmen: Set<String> = emptySet()): List<Share> {
            val total = damage.values.sum()
            if (total <= 0) return emptyList()
            return damage.entries.filter { it.value > 0 }.sortedByDescending { it.value }.take(MAX_LOOTERS).map { (key, dealt) ->
                val floor = if (key in ironmen && dealt * 100 < total * IRONMAN_ELIGIBLE_PERCENT) IRONMAN_FLOOR_PERCENT else FLOOR_PERCENT
                Share(key, dealt, total, floor)
            }
        }

        /**
         * Each share's loot: its own [roll] with [uniques] taken out and every count scaled, then one more roll for the
         * kill whose first unique, if any, goes to a share picked with odds of its damage.
         */
        fun split(shares: List<Share>, uniques: Set<Int>, random: GameRandom, roll: () -> List<Drop>): Map<String, List<Drop>> {
            val out = LinkedHashMap<String, MutableList<Drop>>()
            for (share in shares) {
                out[share.key] = roll().filter { it.type.id !in uniques }.mapTo(ArrayList()) { Drop(it.type, share.scale(it.count)) }
            }
            if (shares.isEmpty() || uniques.isEmpty()) return out
            val unique = roll().firstOrNull { it.type.id in uniques } ?: return out
            val total = shares.sumOf { it.damage }
            var pick = random.nextInt(total)
            val winner = shares.first { share -> (pick < share.damage).also { pick -= share.damage } }
            out.getValue(winner.key) += unique
            return out
        }
    }
}
