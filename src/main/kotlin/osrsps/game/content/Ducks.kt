package osrsps.game.content

import kotlin.math.abs
import kotlin.math.sign
import osrsps.game.GameRandom
import osrsps.game.Player
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcScripts
import osrsps.game.npc.Npcs
import osrsps.map.CollisionMap
import osrsps.map.LineOfSight
import osrsps.map.Tile

/** What ducks and ducklings say over their heads; the words are in the dialogue file, from the Wiki's Duck and Ducklings pages. */
object DuckLines : Lines("duck", "Duck") {
    val QUACK = line("quack", "a duck's call now and then, and a drake's when a duck beside it is killed (RSMod)")
    val CALL = line("call", "the female duck's call to her ducklings (Duck page, under ducklings)")
    val EEP = line("eep", "the ducklings' answer to her call, and their call after they mourn her")
    val CHEEP = line("cheep", "the ducklings' cry when the duck they follow is killed")
}

/**
 * The ducks of Lumbridge's river and the pond by Fred's farm, RSMod's duck scripts: drakes and female ducks swim, keeping
 * to the water ([osrsps.game.npc.MoveRestrict.BLOCKED] in their spawn file), and quack now and then; ducklings follow a
 * female duck, answer her call, and cry and scatter when she is killed. Ducks and ducklings can be fought (npc-combat
 * .json) and drop nothing (the Wiki lists no drops). The Wiki's Duck page says fired ammunition is lost when a duck isn't
 * on land; [osrsps.game.World] loses any that lands on water.
 *
 * Timings are RSMod's: a duck's first quack 25 ticks after it appears and a duckling's first call 50, then one every 50
 * to 100 ticks; a duckling looks for a female duck within 2 tiles every 11 ticks; and on a duck's death the ducklings
 * within a tile of it cry 2 ticks later and swim off up to 9 tiles, calling 2 ticks after that, while a drake beside it
 * quacks. The quack sound is synth 413 (RSMod's `quack`). Guessed and marked below: the attackable duckling (2001) by
 * Fred's farm acts as the ducklings group (2002) does, as the Wiki's Duckling page says it follows female ducks too;
 * which tick of the 11 a duckling looks on; and that a scattering duckling picks a water tile.
 *
 * The ducks and drakes that walk on land by Mill Lane Mill and Groats' farm (2003, 2004) have no options, wander and,
 * as the Wiki's Duck (Common) and Drake (bird) pages say, run away from a player who comes close. How close and how far
 * they run are guessed ([FLEE_RANGE], [FLEE_TILES]).
 */
class Ducks(
    private val npcs: Npcs,
    private val scripts: NpcScripts,
    private val collision: CollisionMap,
    private val random: GameRandom,
    private val currentTick: () -> Long,
    /** The dialogue file's words, for what they say. */
    private val text: () -> DialogueText,
    /** Everyone logged in, whom the land ducks run from. */
    private val players: () -> List<Player>,
    /** Plays a sound from an NPC's tile for those nearby. */
    private val sound: (Npc, Int) -> Unit,
) {
    fun register() {
        scripts.onTimer(DUCKS, DUCK_FIRST_TIMER) { duck ->
            scripts.queue(duck, 1) { say(it, DuckLines.QUACK) }
            nextTimer()
        }
        scripts.onTimer(DUCKLINGS, DUCKLING_FIRST_TIMER) { duckling ->
            val duck = nearest(duckling, FEMALE_DUCK, CALL_RANGE)
            if (duck != null) {
                say(duck, DuckLines.CALL)
                scripts.queue(duckling, 2) { say(it, DuckLines.EEP) }
            }
            nextTimer()
        }
        scripts.onTick(DUCKLINGS) { duckling -> hunt(duckling) }
        scripts.onDeath(DUCKS) { duck -> mourn(duck) }
        scripts.onTick(LAND_DUCKS) { duck -> flee(duck) }
    }

    /** Every [HUNT_RATE] ticks a duckling without a duck looks for one close by and follows her. */
    private fun hunt(duckling: Npc) {
        // Guessed: which of the ticks it looks on, spread by its index.
        if ((currentTick() + duckling.index) % HUNT_RATE != 0L) return
        if (duckling.leader != null || duckling.following != null || duckling.target != null) return
        val duck = nearest(duckling, FEMALE_DUCK, HUNT_RANGE) ?: return
        npcs.followNpc(duckling, duck)
    }

    private fun mourn(duck: Npc) {
        for (duckling in DUCKLINGS.flatMap { around(duck, it, MOURN_RANGE) }) {
            scripts.queue(duckling, 2) { cry(it) }
        }
        around(duck, DRAKE, MOURN_RANGE).firstOrNull()?.let { drake -> scripts.queue(drake, 2) { say(it, DuckLines.QUACK) } }
    }

    /** A duckling's cry for its duck; one that isn't following or fighting anything swims off and calls. */
    private fun cry(duckling: Npc) {
        say(duckling, DuckLines.CHEEP)
        if (duckling.leader != null || duckling.following != null || duckling.target != null) return
        scatterTile(duckling)?.let { duckling.destination = it }
        scripts.queue(duckling, 2) { say(it, DuckLines.EEP) }
    }

    /** A tile 1 to [SCATTER_RANGE] tiles from [duckling] it can swim on; guessed, as RSMod picks any tile in the ring. */
    private fun scatterTile(duckling: Npc): Tile? {
        repeat(SCATTER_TRIES) {
            val x = duckling.x + random.between(-SCATTER_RANGE, SCATTER_RANGE)
            val z = duckling.z + random.between(-SCATTER_RANGE, SCATTER_RANGE)
            if ((x != duckling.x || z != duckling.z) && npcs.canStand(duckling, x, z)) return Tile(x, z)
        }
        return null
    }

    /** A land duck that isn't walking anywhere runs [FLEE_TILES] tiles straight away from the nearest player close by. */
    private fun flee(duck: Npc) {
        if (duck.destination != null) return
        val player = players()
            .filter { it.level == duck.level && !it.loggedOut && abs(it.x - duck.x) <= FLEE_RANGE && abs(it.z - duck.z) <= FLEE_RANGE }
            .minByOrNull { maxOf(abs(it.x - duck.x), abs(it.z - duck.z)) } ?: return
        val dx = (duck.x - player.x).sign
        val dz = (duck.z - player.z).sign
        // Standing on the player's tile, it runs west (guessed).
        val (sx, sz) = if (dx == 0 && dz == 0) -1 to 0 else dx to dz
        duck.destination = Tile(duck.x + sx * FLEE_TILES, duck.z + sz * FLEE_TILES)
    }

    /** The nearest NPC of [type] within [range] tiles of [npc] that it can see, nearest first and then by index. */
    private fun nearest(npc: Npc, type: Int, range: Int): Npc? = around(npc, type, range).firstOrNull()

    private fun around(npc: Npc, type: Int, range: Int): List<Npc> =
        npcs.near(npc.x, npc.z, npc.level, range)
            .filter { it !== npc && it.spawn.type.id == type && !it.dead }
            .filter { LineOfSight.clear(collision, npc.level, npc.x, npc.z, npc.type.size, it.x, it.z, it.type.size, it.type.size) }
            .sortedWith(compareBy({ maxOf(abs(it.x - npc.x), abs(it.z - npc.z)) }, { it.index }))

    private fun say(npc: Npc, line: Line) {
        npc.effects.say(text()[line])
        sound(npc, SYNTH_QUACK)
    }

    private fun nextTimer() = random.between(TIMER_MIN, TIMER_MAX)

    companion object {
        /** duck and duck_female; both show as "Duck". */
        const val DRAKE = 1838
        const val FEMALE_DUCK = 1839
        val DUCKS = listOf(DRAKE, FEMALE_DUCK)

        /** duckling (attackable, by Fred's farm) and duck_update_ducklings (the river's "Ducklings", no options). */
        const val DUCKLING = 2001
        const val DUCKLINGS_GROUP = 2002
        val DUCKLINGS = listOf(DUCKLING, DUCKLINGS_GROUP)

        /** duck_update_on_land_duck and duck_update_on_land_drake. */
        val LAND_DUCKS = listOf(2003, 2004)

        const val SYNTH_QUACK = 413
        const val DUCK_FIRST_TIMER = 25
        const val DUCKLING_FIRST_TIMER = 50
        const val TIMER_MIN = 50
        const val TIMER_MAX = 100
        const val HUNT_RATE = 11
        const val HUNT_RANGE = 2
        const val CALL_RANGE = 10
        const val MOURN_RANGE = 1
        const val SCATTER_RANGE = 9
        private const val SCATTER_TRIES = 20

        /** Guessed: how close a player comes before a land duck runs, and how far it runs. */
        const val FLEE_RANGE = 2
        const val FLEE_TILES = 4
    }
}
