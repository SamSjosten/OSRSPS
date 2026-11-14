package osrsps.game.skill

import java.util.concurrent.ConcurrentHashMap
import osrsps.game.FrameSlot
import osrsps.game.Interfaces
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/** The canoe stations' words, in the dialogue file. */
object CanoeLines : Lines("canoe", "Canoes") {
    val LEVEL = line("level", "below the Woodcutting level for canoes: Chop-down on the tree, or a canoe picked above the level (the box's own level 12 message is the client's)", "level")
    val ARRIVE_LUMBRIDGE = line("arrive_lumbridge", "on landing at Lumbridge")
    val ARRIVE_CHAMPIONS = line("arrive_champions", "on landing at the Champions' Guild")
    val ARRIVE_BARBARIAN = line("arrive_barbarian", "on landing at Barbarian Village")
    val ARRIVE_EDGEVILLE = line("arrive_edgeville", "on landing at Edgeville")
    val ARRIVE_FEROX = line("arrive_ferox", "on landing at the Ferox Enclave")
    val ARRIVE_POND = line("arrive_pond", "on landing at the Wilderness Pond")
    val SINKS = line("sinks", "after each landing: the canoe sinks")
    val POND_WALK = line("pond_walk", "after landing at the Wilderness Pond: no trees to make another canoe")
    val WILDERNESS = line("wilderness", "ours: the Ferox Enclave and the Wilderness Pond aren't open yet")
}

/** One of the River Lum's canoe stations: its loc, the varbit that holds its state for each player, and where you land. */
enum class CanoeStation(
    /** 1 to 5: the client's numbering (canoe_get_state), which canoe_startfrom holds. */
    val index: Int,
    val loc: Int,
    val varbit: Int,
    /** The destination it is in [Canoes.REACH]'s rows. */
    val destination: CanoeDestination,
) {
    LUMBRIDGE(1, 12163, 1839, CanoeDestination.LUMBRIDGE),
    CHAMPIONS_GUILD(2, 12164, 1840, CanoeDestination.CHAMPIONS_GUILD),
    BARBARIAN_VILLAGE(3, 12165, 1841, CanoeDestination.BARBARIAN_VILLAGE),
    EDGEVILLE(4, 12166, 1842, CanoeDestination.EDGEVILLE),
    FEROX_ENCLAVE(5, 39638, 10527, CanoeDestination.FEROX_ENCLAVE),
}

/**
 * Where a canoe can go, in the order of canoe_return_locations' six values. [component] is the button on canoe_map_lum;
 * [x], [z] the tile the player lands on, guessed (see [Canoes]).
 */
enum class CanoeDestination(val component: Int, val x: Int, val z: Int, val wilderness: Boolean = false) {
    LUMBRIDGE(7, 3240, 3242),
    CHAMPIONS_GUILD(12, 3199, 3344),
    BARBARIAN_VILLAGE(17, 3109, 3415),
    EDGEVILLE(22, 3132, 3510),
    WILDERNESS_POND(28, 3139, 3796, wilderness = true),
    FEROX_ENCLAVE(27, 3153, 3631, wilderness = true),
    ;

    val arrive get() = when (this) {
        LUMBRIDGE -> CanoeLines.ARRIVE_LUMBRIDGE
        CHAMPIONS_GUILD -> CanoeLines.ARRIVE_CHAMPIONS
        BARBARIAN_VILLAGE -> CanoeLines.ARRIVE_BARBARIAN
        EDGEVILLE -> CanoeLines.ARRIVE_EDGEVILLE
        WILDERNESS_POND -> CanoeLines.ARRIVE_POND
        FEROX_ENCLAVE -> CanoeLines.ARRIVE_FEROX
    }
}

/** The four canoes, 1 to 4 as the station varbits number them. */
enum class CanoeType(val number: Int, val level: Int, val xpTenths: Int, val component: Int) {
    LOG(1, 12, 300, 20),
    DUGOUT(2, 27, 600, 18),
    STABLE_DUGOUT(3, 42, 900, 12),
    WAKA(4, 57, 1500, 11),
}

/**
 * The River Lum's canoe stations (Gathering 2, step G3). Each station is a multiloc on a varbit of its own, so each
 * player sees their own: Chop-down on the tree (state 0) rolls every [CYCLE] ticks with the tree's pair for the best axe
 * until it falls (9, then 10); Shape-Canoe opens the canoeing box, where the canoes the player's level allows are offered
 * (the client builds the list from the level), and shaping rolls with the axe's own pair until the canoe is made (states 1
 * to 4) for its XP; Float pushes it into the water (5 to 8, animated, then 11 to 14); Paddle opens canoe_map_lum, where
 * the client offers the stations this canoe reaches from here (canoe_return_locations, by canoe_startfrom), and a pick
 * takes the player there. The canoe sinks on arrival and the station goes back to a tree. An unused canoe waits until
 * logout; starting on another station's tree clears it.
 *
 * Sources: the 242 cache for the locs, varbits, interfaces, client scripts (canoe_setup's level checks, the reach tables
 * of canoe_paddle_*) and the seqs; the OSRS Wiki's Canoe and Canoe station pages (read 2026-11-14) for levels, XP, the
 * shaping pairs by axe, the tree's pairs for bronze to black axes, one trip per canoe, and the Waka alone reaching the
 * Wilderness. The Lum canoes are post-quest (none needed). Quests are out.
 *
 * Guessed, to correct from a recording: the tree's success for mithril axes and up (always, the Wiki charts bronze to black
 * only); shaping rolling every 4 ticks, like chopping; the fall taking [FALL_TICKS], the push [PUSH_TICKS] and the trip
 * [TRIP_TICKS]; the player's push animation (canoeing_pushing_into_water) and carving animations by axe (the cache's
 * canoeing_carve seqs); the landing tiles, from the 2009scape project's canoe listener (a reimplementation, not live),
 * and the Ferox Enclave's, which nobody gives;
 * no message when chopping or shaping starts. Left out: the travel cutscene (its interface and fade), the sinking canoe
 * at the landing (a blocking loc on the landing tile), followers, the Wilderness warning (varbit wildy_canoe_warning),
 * and the Ardougne river's stations (another canoe_river).
 */
class Canoes(private val world: World, private val woodcutting: WoodcuttingData) {
    private class Shaping(val station: CanoeStation, val loc: ZoneLoc, var type: CanoeType? = null)

    /** Who has the shaping box open, or is shaping, and at which station. */
    private val shaping = ConcurrentHashMap<Player, Shaping>()

    /** The station each player paddles from while canoe_map_lum is open. */
    private val paddling = ConcurrentHashMap<Player, CanoeStation>()

    fun register() {
        for (station in CanoeStation.entries) {
            world.handlers.onLoc(station.loc, 1) { op -> use(op, station) }
        }
        for (type in CanoeType.entries) {
            world.handlers.onButton(SHAPING, type.component) { shape(type) }
        }
        for (destination in CanoeDestination.entries) {
            world.handlers.onButton(MAP, destination.component) { paddle(destination) }
        }
        world.interfaces.onClose(SHAPING) { player -> shaping[player]?.takeIf { it.type == null }?.let { shaping.remove(player) } }
        world.interfaces.onClose(MAP) { player -> paddling.remove(player) }
    }

    /** Clears [player]'s stations and canoe at logout: the varbits are saved, and an unused canoe goes at logout. */
    fun forget(player: Player) {
        shaping.remove(player)
        paddling.remove(player)
        for (station in CanoeStation.entries) setState(player, station, TREE)
        if (player.vars.types.varbit(VARBIT_CANOE_TYPE) != null) player.vars.setVarbit(VARBIT_CANOE_TYPE, 0)
    }

    private suspend fun ScriptContext.use(op: Op, station: CanoeStation) {
        val loc = op.loc ?: return
        when (val state = state(player, station)) {
            TREE -> chop(op, station)
            FALLEN -> if (op.repeat) carve(op, station) else openShaping(station, loc)
            FALLING -> {
                // A click lands only once the fall's delay is over, so the script that ends it was dropped (a death
                // mid-fall, Codex review on #659): the tree is down.
                setState(player, station, FALLEN)
                if (!op.repeat) openShaping(station, loc)
            }
            in 1..4 -> float(station, state)
            in 5..8 -> {
                // Only a push cut short (a logout mid-push, the state saved) leaves one here.
                setState(player, station, state + 6)
                openMap(station)
            }
            in 11..14 -> openMap(station)
        }
    }

    private suspend fun ScriptContext.chop(op: Op, station: CanoeStation) {
        if (op.repeat && player.modalOpen) return stop()
        val axe = checks() ?: return stop()
        if (!op.repeat) {
            // Starting on a tree clears a canoe made at any other station (OSRS Wiki, Canoe).
            for (other in CanoeStation.entries) if (other != station) setState(player, other, TREE)
            player.anim(axe.anim)
        }
        if (SkillClock.step(player, tick, CYCLE) == ClockStep.DUE) {
            player.anim(axe.anim)
            val pair = TREE_PAIRS[woodcutting.tier(axe)]
            if (pair == null || SuccessRoll.roll(world.random, pair.first, pair.second, level())) {
                player.anim(-1)
                setState(player, station, FALLING)
                delay(FALL_TICKS)
                setState(player, station, FALLEN)
                return
            }
        }
        world.repeatOp(player, op)
    }

    private fun ScriptContext.openShaping(station: CanoeStation, loc: ZoneLoc) {
        // Below level 12 (a drained level, as the tree needs 12) the client's canoe_setup closes the box with its own
        // message, so the server doesn't check here.
        shaping[player] = Shaping(station, loc)
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, SHAPING)
        for (type in CanoeType.entries) world.interfaces.setEvents(player, SHAPING, type.component, 0, 0, Interfaces.IF_EVENT_OP1)
    }

    private fun ScriptContext.shape(type: CanoeType) {
        val shape = shaping[player] ?: return
        world.interfaces.closeModals(player)
        if (state(player, shape.station) != FALLEN) return
        if (level() < type.level) {
            player.message(world.textFor(player)[CanoeLines.LEVEL, "level" to type.level])
            return
        }
        val axe = checks() ?: return
        shape.type = type
        shaping[player] = shape
        player.anim(CARVE[axe.obj] ?: axe.anim)
        world.repeatOp(player, Op(OpTarget.LOC, shape.station.loc, 1, loc = shape.loc, repeat = true))
    }

    private fun ScriptContext.carve(op: Op, station: CanoeStation) {
        val shape = shaping[player]
        val type = shape?.type
        if (shape == null || type == null || shape.station != station || player.modalOpen) return stop()
        val axe = checks() ?: return stop()
        // The canoe's own level on every roll, as a tree's is: a boost can run out mid-shaping (Codex review on #659).
        if (level() < type.level) {
            player.message(world.textFor(player)[CanoeLines.LEVEL, "level" to type.level])
            shaping.remove(player)
            return stop()
        }
        if (SkillClock.step(player, tick, CYCLE) == ClockStep.DUE) {
            player.anim(CARVE[axe.obj] ?: axe.anim)
            val (low, high) = SHAPE_PAIRS.getValue(woodcutting.tier(axe))
            if (SuccessRoll.roll(world.random, low, high, level())) {
                player.anim(-1)
                shaping.remove(player)
                setState(player, station, type.number)
                if (player.vars.types.varbit(VARBIT_CANOE_TYPE) != null) player.vars.setVarbit(VARBIT_CANOE_TYPE, type.number)
                player.skills.addTenths(Skill.WOODCUTTING, type.xpTenths)
                return
            }
        }
        world.repeatOp(player, op)
    }

    private suspend fun ScriptContext.float(station: CanoeStation, type: Int) {
        player.anim(PUSH_ANIM)
        setState(player, station, type + 4)
        delay(PUSH_TICKS)
        setState(player, station, type + 10)
    }

    private fun ScriptContext.openMap(station: CanoeStation) {
        paddling[player] = station
        world.interfaces.setVarbitNow(player, VARBIT_STARTFROM, station.index)
        world.interfaces.setVarbitNow(player, VARBIT_RIVER, RIVER_LUM)
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, MAP)
        for (destination in CanoeDestination.entries) {
            world.interfaces.setEvents(player, MAP, destination.component, -1, -1, Interfaces.IF_EVENT_OP1)
        }
    }

    private suspend fun ScriptContext.paddle(destination: CanoeDestination) {
        val station = paddling[player] ?: return
        val state = state(player, station)
        // The client offers only these; anything else is a stale or forged click.
        if (state !in 11..14 || !reaches(state - 10, station, destination)) return
        world.interfaces.closeModals(player)
        if (destination.wilderness && !WILDERNESS_OPEN) {
            player.message(world.textFor(player)[CanoeLines.WILDERNESS])
            return
        }
        setState(player, station, TREE)
        delay(TRIP_TICKS)
        world.teleport(player, destination.x, destination.z, 0)
        val text = world.textFor(player)
        player.message(text[destination.arrive])
        player.message(text[CanoeLines.SINKS])
        if (destination == CanoeDestination.WILDERNESS_POND) player.message(text[CanoeLines.POND_WALK])
    }

    /** The level and axe checks for chopping and shaping: the axe, or null after the message. */
    private fun ScriptContext.checks(): SkillTool? {
        val text = world.textFor(player)
        if (level() < CanoeType.LOG.level) {
            player.message(text[CanoeLines.LEVEL, "level" to CanoeType.LOG.level])
            return null
        }
        return when (val check = woodcutting.tools.best(player)) {
            is ToolCheck.Found -> check.tool
            else -> {
                player.message(text[WoodcuttingLines.NO_AXE])
                null
            }
        }
    }

    private fun ScriptContext.level(): Int = player.skills.currentLevel(Skill.WOODCUTTING)

    private fun ScriptContext.stop() {
        player.anim(-1)
    }

    companion object {
        /** Interface canoeing: the shaping box. */
        const val SHAPING = 416

        /** Interface canoe_map_lum: where to paddle to. */
        const val MAP = 953

        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>>
            get() = mapOf(
                SHAPING to ("canoeing" to mapOf(20 to "log", 18 to "dugout", 12 to "stable_dugout", 11 to "waka")),
                MAP to (
                    "canoe_map_lum" to mapOf(
                        7 to "destination_1", 12 to "destination_2", 17 to "destination_3", 22 to "destination_4",
                        27 to "destination_5", 28 to "destination_6",
                    )
                    ),
            )

        const val VARBIT_CANOE_TYPE = 1843
        const val VARBIT_STARTFROM = 1846
        const val VARBIT_RIVER = 20259
        const val RIVER_LUM = 0

        /** Station states, the multilocs' values: 1 to 4 a canoe on the bank, 5 to 8 being pushed in, 11 to 14 afloat. */
        const val TREE = 0
        const val FALLING = 9
        const val FALLEN = 10

        /** Off until the Wilderness is built: the Ferox Enclave and the Wilderness Pond refuse. */
        const val WILDERNESS_OPEN = false

        /** Rolls every 4 ticks: the Wiki for the tree, guessed for shaping. */
        const val CYCLE = 4

        /** Guessed from canoeing_station_animations (93 client cycles, about 3 ticks). */
        const val FALL_TICKS = 3
        const val PUSH_TICKS = 3

        /** Guessed: the travel cutscene's length, which we don't show. */
        const val TRIP_TICKS = 8

        /** canoeing_pushing_into_water, guessed as the player's animation for Float. */
        const val PUSH_ANIM = 3301

        /** The canoeing_carve seq for each axe, by obj (cache seq names). */
        val CARVE = mapOf(
            1351 to 3291, 1349 to 3290, 1353 to 3289, 1361 to 3288, 1355 to 3287, 1357 to 3286, 1359 to 3285,
            6739 to 3292, 13241 to 2116, 20011 to 7263, 23279 to 8302, 23673 to 8323,
        )

        /** The tree's pairs (Wiki, Canoe station); the tiers it doesn't chart are missing, guessed to always succeed. */
        val TREE_PAIRS = mapOf("bronze" to (70 to 200), "iron" to (96 to 296), "steel" to (144 to 360), "black" to (128 to 496))

        /** Shaping's pairs by axe, whatever the canoe (Wiki, Canoe). */
        val SHAPE_PAIRS = mapOf(
            "bronze" to (32 to 100), "iron" to (48 to 150), "steel" to (64 to 200), "black" to (72 to 225),
            "mithril" to (80 to 250), "adamant" to (96 to 300), "rune" to (112 to 350), "dragon" to (112 to 350),
            "crystal" to (112 to 350),
        )

        /**
         * canoe_paddle_log, _dugout, _stable_dugout and _stable_waka for stations 1 to 5: by canoe, then station, 0 where it
         * can go, in [CanoeDestination]'s order.
         */
        val REACH: List<List<List<Int>>> = listOf(
            listOf(listOf(1, 0, 1, 1, 1, 1), listOf(0, 1, 0, 1, 1, 1), listOf(1, 0, 1, 0, 1, 1), listOf(1, 1, 0, 1, 1, 1), listOf(1, 1, 1, 0, 1, 1)),
            listOf(listOf(1, 0, 0, 1, 1, 1), listOf(0, 1, 0, 0, 1, 1), listOf(0, 0, 1, 0, 1, 1), listOf(1, 0, 0, 1, 1, 1), listOf(1, 1, 0, 0, 1, 1)),
            listOf(listOf(1, 0, 0, 0, 1, 1), listOf(0, 1, 0, 0, 1, 1), listOf(0, 0, 1, 0, 1, 1), listOf(0, 0, 0, 1, 1, 1), listOf(1, 0, 0, 0, 1, 1)),
            listOf(listOf(1, 0, 0, 0, 0, 0), listOf(0, 1, 0, 0, 0, 0), listOf(0, 0, 1, 0, 0, 0), listOf(0, 0, 0, 1, 0, 0), listOf(0, 0, 0, 0, 0, 1)),
        )

        /** Whether canoe [type] (1 to 4) goes from [from] to [to]. */
        fun reaches(type: Int, from: CanoeStation, to: CanoeDestination): Boolean = REACH[type - 1][from.index - 1][to.ordinal] == 0

        /** [station]'s state for [player]; 0 when the cache has no such varbit (tests). */
        fun state(player: Player, station: CanoeStation): Int =
            if (player.vars.types.varbit(station.varbit) == null) TREE else player.vars.getVarbit(station.varbit)

        private fun setState(player: Player, station: CanoeStation, value: Int) {
            if (player.vars.types.varbit(station.varbit) != null) player.vars.setVarbit(station.varbit, value)
        }
    }
}
