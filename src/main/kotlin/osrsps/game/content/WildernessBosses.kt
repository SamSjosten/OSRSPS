package osrsps.game.content

import osrsps.game.GameRandom
import osrsps.game.GroundObjs
import osrsps.game.Player
import osrsps.game.SettingsSide
import osrsps.game.World
import osrsps.game.combat.Drop
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.map.LineOfSight

/** The Wilderness bosses' shared words (keys only; the words are in data/dialogue.txt). */
object WildernessBossLines : Lines("wilderness_boss", "Kill count") {
    val KILL_COUNT = line("kill_count", "a Wilderness boss killed: the player's kill count of it (the Wiki's Kill count page)", "boss", "count")
}

/**
 * A Wilderness boss (piece 4): its NPC [types], the name `::wboss` takes, its kill count's varp (the cache's
 * `total_<boss>_kills`, -1 for none), whether a kill's loot is [group] loot split by damage, and its [uniques], the items
 * that drop at most once a kill. A [lair] boss's top damage dealer gets 10,000 off the lairs' fee ([WildernessLairFee]).
 */
class WildernessBoss(
    val name: String,
    val types: Set<Int>,
    val killCountVarp: Int,
    val group: Boolean = false,
    val uniques: Set<Int> = emptySet(),
    val lair: Boolean = false,
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
 * - **Solo loot**: a boss without group loot drops for whoever dealt it the most damage, as on live ("The player who
 *   has done the most damage will see the drop before the other players", the Wiki's Drops page), not whoever hit it last; for everyone when no
 *   player damaged it (exploit review round 39: one splash before the kill took another player's drop and kill count).
 *   INFERRED: ties to whoever hit it first.
 * - **Kill counts**: every player who gets loot (the top damager, for a boss without group loot) counts the kill in its
 *   varp and is told it. INFERRED: the count going to every looter of a group kill, and both loot and count going only
 *   to those still logged in at the kill: one who logged out keeps their damage in the total, so no one else's share
 *   grows, but gets nothing.
 * - **Out of reach from outside the fight**: a player outside a boss's fight area can't attack it (GUESSED: told they
 *   can't reach it). Exploit review round 39: the fight drops a target outside its area every tick, so one standing
 *   just outside hit the boss for free.
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

        /** The shares of those logged in on the killing tick, kept for the loot that shows as the body goes. */
        var atKill: List<Share>? = null
    }

    private val damage = HashMap<Npc, Tally>()

    val all: Collection<WildernessBoss> get() = byName.values

    fun register(boss: WildernessBoss) {
        require(boss.name.lowercase() !in byName) { "${boss.name} registered twice" }
        if (byType.isEmpty()) world.combat.outsideFight = ::outsideFight
        byName[boss.name.lowercase()] = boss
        for (type in boss.types) byType[type] = boss
        world.npcScripts.onDeath(boss.types) { npc -> died(npc, boss) }
    }

    operator fun get(name: String): WildernessBoss? = byName[name.lowercase()]

    /** Whether [npc] is a boss in a fight whose area [player] is outside. */
    private fun outsideFight(player: Player, npc: Npc): Boolean {
        if (byType[npc.type.id] == null) return false
        val fight = world.encounters.all.firstOrNull { it.boss === npc && !it.ended } ?: return false
        return !fight.covers(player.x, player.z)
    }

    /** [player] dealt [dealt] to [npc]: counted toward its loot if it is a boss. */
    fun damaged(npc: Npc, player: Player, dealt: Int) {
        if (byType[npc.type.id] == null) return
        val tally = damage.getOrPut(npc) { Tally() }
        tally.damage.merge(player.saveKey, dealt, Int::plus)
        if (ironman(player)) tally.ironmen += player.saveKey
    }

    /**
     * A dead boss's tally, held from its body going until [loot], which comes straight after in the same pass. One at
     * most: a body taken out any other way (`::delnpc` mid-death) never loots, and the next removal replaces it.
     */
    private var bodyGoing: Pair<Npc, Tally>? = null

    /**
     * [npc] has left the world: its tally goes now. A boss taken out alive (`::delnpc`, say) gives no loot; a dead one's
     * tally waits in [bodyGoing] for [loot].
     */
    fun removed(npc: Npc) {
        val tally = damage.remove(npc)
        bodyGoing = if (tally != null && npc.hitpoints <= 0) npc to tally else null
    }

    /** How many boss NPCs have a damage tally, for tests: none once each has died or gone. */
    internal val tallies: Int get() = damage.size

    private fun died(npc: Npc, boss: WildernessBoss) {
        // Who is logged in and at the fight now decides every reward, not who is when the loot shows a few ticks later.
        val atKill = shares(npc).also { damage[npc]?.atKill = it }
        if (boss.lair) {
            // The most damage among them; a tie goes to whoever hit first (inferred), the tally's first. Not the raw
            // tally, which still holds those who logged out or left (exploit review rounds 40 and 42, R40B-5).
            val top = atKill.firstOrNull()?.key
            world.onlinePlayers.firstOrNull { it.saveKey == top }?.let(WildernessLairFee::discount)
        }
        val looters = if (boss.group) atKill.map { it.key } else atKill.take(1).map { it.key }
        if (boss.killCountVarp < 0) return
        for (key in looters) {
            val player = world.onlinePlayers.firstOrNull { it.saveKey == key && !it.loggedOut } ?: continue
            if (boss.killCountVarp !in 0 until player.vars.types.varpCount) continue
            player.vars[boss.killCountVarp] = player.vars[boss.killCountVarp] + 1
            world.message(player, WildernessBossLines.KILL_COUNT, "boss" to npc.type.name, "count" to player.vars[boss.killCountVarp])
        }
    }

    private fun shares(npc: Npc): List<Share> {
        val tally = damage[npc] ?: return emptyList()
        // Not one who logged out this tick, still in the list until the end of it, nor one who has left the fight
        // (exploit review round 40, R40B-3).
        val present = world.onlinePlayers.filter { !it.loggedOut && atFight(it, npc) }.mapTo(HashSet()) { it.saveKey }
        return shares(tally.damage, tally.ironmen) { it in present }
    }

    /**
     * Whether [player] is still at [npc]'s fight: in its encounter's area, or for a boss without one within [PRESENT_TILES]
     * of it. Inferred: the Wiki's Drops pages give the loot to the most damage without saying whether one who has walked
     * away keeps it; one gone from the fight is taken not to.
     */
    private fun atFight(player: Player, npc: Npc): Boolean {
        if (player.level != npc.level) return false
        val fight = world.encounters.all.firstOrNull { it.boss === npc }
        if (fight != null) return fight.covers(player.x, player.z)
        // Edge to edge from the tiles it stands on, which run east and north of its origin (Codex review on #817).
        return LineOfSight.distance(npc.x, npc.z, npc.type.size, player.x, player.z) <= PRESENT_TILES
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
        bodyGoing?.takeIf { it.first === npc }?.let { damage[npc] = it.second }
        bodyGoing = null
        val shares = damage[npc]?.atKill ?: shares(npc)
        damage.remove(npc)
        if (!boss.group) {
            solo = Solo(npc, shares.firstOrNull()?.key)
            return false
        }
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

    /** Whose a solo boss's drop is, from [loot] to [soloLooter]: [key], or everyone's for null. */
    class Solo(val npc: Npc, val key: String?)

    private var solo: Solo? = null

    /**
     * For [npc], a boss without group loot that [loot] has just passed on: whose drop it is. Null for any other NPC, whose
     * drop goes to its killer.
     */
    fun soloLooter(npc: Npc): Solo? = solo?.takeIf { it.npc === npc }.also { solo = null }

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

        /** How near a boss without an encounter a damage dealer must be at its death to share in it: the view distance. */
        const val PRESENT_TILES = 15
        const val FLOOR_PERCENT = 25
        const val IRONMAN_ELIGIBLE_PERCENT = 7
        const val IRONMAN_FLOOR_PERCENT = 5

        /**
         * The top [MAX_LOOTERS] of [damage] (by save key; ties to whoever hit first) that are [eligible], each with its
         * floor: 25%, or 5% for one of [ironmen] under 7% of the damage. The total stays everyone's damage; only the
         * eligible take the places, so ten who left don't shut out an eleventh who stayed (Codex review on #817).
         */
        fun shares(damage: Map<String, Int>, ironmen: Set<String> = emptySet(), eligible: (String) -> Boolean = { true }): List<Share> {
            val total = damage.values.sum()
            if (total <= 0) return emptyList()
            return damage.entries.filter { it.value > 0 && eligible(it.key) }.sortedByDescending { it.value }.take(MAX_LOOTERS).map { (key, dealt) ->
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
