package osrsps.game.content

import kotlin.math.max
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.map.Tile

/**
 * The Escape Caves (Wilderness step W4.11; the Wiki's Escape Caves, Grizzly bear (Escape Caves) and Poison spider
 * (Escape Caves), read 2026-11-15): map square 52_160, levels 33 to 40, multi-way, between Callisto's Den, Vet'ion's Rest
 * and the Silk Chasm.
 *
 * - **From the surface**: Enter on one of the four caves (`wild_cave_exit01` 47175) takes the lairs' shared fee
 *   ([WildernessLairFee]) and puts the player by that cave's way out below; Check-Fee as at the lairs. Players who come
 *   in this way get a debuff draining their prayer as long as they stay.
 * - **Out**: Exit or Climb-up on the four ways out (`wild_boss_escape_cave_exit01` 47147, `_exit02` 47148 twice and
 *   `_exit03` 47149) puts the player back outside the cave above.
 * - **From a lair** ([leaveLair], for the three big lairs' ways out): the player comes out of one of the three cave mouths
 *   (`wild_boss_escape_cave_exit_noop` 47151) at random; anyone leaving the same lair within 10 seconds (17 ticks) of the
 *   last comes out of the same one.
 * - **Lingering**: anyone in the caves for about 30 seconds (50 ticks) slowly loses prayer and run energy.
 * - Grizzly bears and poison spiders throughout (npc-spawns/escape-caves.json), typeless and never giving up.
 *
 * Read off the 242 cache's map: where each cave and way out stands, and the open tiles beside them where players come out.
 * GUESSED: which surface cave goes with which way out below (by where they lie: the south cave with the south way out,
 * the middle with the ladder, the north-west and north-east with the two by the north); the debuff's point every
 * [DEBUFF_TICKS]; lingering's point and [LINGER_ENERGY] every [LINGER_TICKS] after [LINGER_AFTER]. Left out: logging out or
 * switching worlds in a lair coming back through the caves.
 */
class EscapeCaves {
    /** A surface cave and the way out below that leads back to it, and the tiles players come out on at each end. */
    data class Pair(val surface: Tile, val outside: Tile, val exitId: Int, val exit: Tile, val inside: Tile)

    private class Visit(val since: Long, val debuff: Boolean) {
        /** The last tick its player was seen in the caves. */
        var seen: Long = since
    }

    /**
     * By save key, not the Player, so relogging (a new Player each login) keeps the debuff and the linger count (exploit
     * review round 48, R48B-1). Kept while they are offline, up to [FORGET_TICKS] after they were last seen in the caves
     * (so accounts that log out there and never come back don't pile up); dropped the first tick they are seen outside.
     */
    private val visits = HashMap<String, Visit>()
    private var pruned = 0L
    /** By lair: when its last player left and which cave mouth they came out of. */
    private val lastLeft = HashMap<String, kotlin.Pair<Long, Tile>>()

    fun debuffed(player: Player): Boolean = visits[player.saveKey]?.debuff == true

    /** How many accounts' visits are kept, online or not. */
    val remembered: Int get() = visits.size

    /**
     * The cave mouth a player leaving [lair] at [tick] comes out of: the last one's, within [SHARED_TICKS] of them, else
     * one of [LAIR_MOUTHS] by [roll] (0 until its size).
     */
    fun lairExit(lair: String, tick: Long, roll: (Int) -> Int): Tile {
        val last = lastLeft[lair]
        val mouth = if (last != null && tick - last.first <= SHARED_TICKS) last.second else LAIR_MOUTHS[roll(LAIR_MOUTHS.size)]
        lastLeft[lair] = tick to mouth
        return mouth
    }

    /** [player] leaves [lair] by its way out into the caves, with no debuff. */
    fun leaveLair(world: World, player: Player, lair: String) {
        val mouth = lairExit(lair, world.tick) { world.random.nextInt(it) }
        visits[player.saveKey] = Visit(world.tick, debuff = false)
        world.teleport(player, mouth.x, mouth.z, 0)
    }

    /** Through a surface cave, with the debuff. */
    fun enterFromSurface(world: World, player: Player, pair: Pair) {
        visits[player.saveKey] = Visit(world.tick, debuff = true)
        world.teleport(player, pair.inside.x, pair.inside.z, 0)
    }

    /** Every tick for every player: forgotten once out of the caves; the debuff's and lingering's drains while in. */
    fun tick(world: World, player: Player) {
        if (world.tick != pruned) {
            pruned = world.tick
            visits.values.removeIf { world.tick - it.seen > FORGET_TICKS }
        }
        if (player.level != 0 || !inCaves(player.x, player.z)) {
            visits.remove(player.saveKey)
            return
        }
        val visit = visits.getOrPut(player.saveKey) { Visit(world.tick, debuff = false) }
        visit.seen = world.tick
        val stayed = world.tick - visit.since
        if (stayed <= 0) return
        if (visit.debuff && stayed % DEBUFF_TICKS == 0L) drainPrayer(world, player, 1)
        if (stayed >= LINGER_AFTER && (stayed - LINGER_AFTER) % LINGER_TICKS == 0L) {
            drainPrayer(world, player, 1)
            player.runEnergy = max(0, player.runEnergy - LINGER_ENERGY)
        }
    }

    /** The last point gone, the prayers go off now, not a tick later when the prayers' own drain would see it. */
    private fun drainPrayer(world: World, player: Player, points: Int) {
        val now = player.skills.currentLevel(Skill.PRAYER)
        if (now <= 0) return
        player.skills.setCurrentLevel(Skill.PRAYER, max(0, now - points))
        if (now <= points) world.prayers.deactivateAll(player)
    }

    companion object {
        const val SURFACE_CAVE = 47175
        const val EXIT_SOUTH = 47147
        const val EXIT_NORTH = 47148
        const val EXIT_LADDER = 47149
        const val ENTER = 1
        const val CHECK_FEE = 2

        const val SHARED_TICKS = 17L
        const val DEBUFF_TICKS = 5L
        const val LINGER_AFTER = 50L
        const val LINGER_TICKS = 5L
        /** An offline account's visit is forgotten 5 minutes after it was last seen in the caves (GUESSED). */
        const val FORGET_TICKS = 500L
        /** 3% of the run energy's 10,000. */
        const val LINGER_ENERGY = 300

        val PAIRS = listOf(
            Pair(Tile(3282, 3774), Tile(3283, 3773), EXIT_SOUTH, Tile(3358, 10244), Tile(3359, 10246)),
            Pair(Tile(3284, 3807), Tile(3285, 3806), EXIT_LADDER, Tile(3361, 10273), Tile(3361, 10272)),
            Pair(Tile(3260, 3832), Tile(3261, 3831), EXIT_NORTH, Tile(3336, 10287), Tile(3338, 10286)),
            Pair(Tile(3320, 3830), Tile(3321, 3829), EXIT_NORTH, Tile(3382, 10287), Tile(3381, 10286)),
        )

        /** Where players leaving a lair come out: beside each of the three cave mouths. */
        val LAIR_MOUTHS = listOf(Tile(3341, 10254), Tile(3362, 10297), Tile(3377, 10256))

        /** The caves: their map square, on the ground level. */
        fun inCaves(x: Int, z: Int): Boolean = x shr 6 == 52 && z shr 6 == 160
    }
}

fun registerEscapeCaves(world: World) {
    val caves = world.escapeCaves
    world.handlers.onLoc(EscapeCaves.SURFACE_CAVE, EscapeCaves.ENTER) { op ->
        val loc = op.loc!!
        val pair = EscapeCaves.PAIRS.firstOrNull { it.surface.x == loc.x && it.surface.z == loc.z }
            ?: return@onLoc world.message(player, osrsps.game.GameLines.NOTHING_INTERESTING)
        arriveDelay()
        with(WildernessLairFee) { if (!payToEnter(world)) return@onLoc }
        caves.enterFromSurface(world, player, pair)
    }
    world.handlers.onLoc(EscapeCaves.SURFACE_CAVE, EscapeCaves.CHECK_FEE) {
        arriveDelay()
        if (WildernessLairFee.paid(player) || WildernessLairFee.fee(player) == 0) world.message(player, WildernessLairLines.FEE_PAID)
        else world.message(player, WildernessLairLines.FEE_DUE, "fee" to WildernessLairFee.fee(player))
    }
    for (id in listOf(EscapeCaves.EXIT_SOUTH, EscapeCaves.EXIT_NORTH, EscapeCaves.EXIT_LADDER)) {
        world.handlers.onLoc(id, 1) { op ->
            val loc = op.loc!!
            val pair = EscapeCaves.PAIRS.firstOrNull { it.exitId == id && it.exit.x == loc.x && it.exit.z == loc.z }
                ?: return@onLoc world.message(player, osrsps.game.GameLines.NOTHING_INTERESTING)
            arriveDelay()
            world.teleport(player, pair.outside.x, pair.outside.z, 0)
        }
    }
}
