package osrsps.game.content

import kotlin.math.abs
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.HitsplatType
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.skill.LightSources
import osrsps.game.zone.ZoneLoc

/** The swamp caves' words, in the dialogue file. */
object SwampCavesLines : Lines("swamp_caves", "Lumbridge Swamp Caves") {
    val NEED_ROPE = line("need_rope", "Climb-down on the dark hole before a rope has been tied to it")
    val ROPE_TIED = line("rope_tied", "a rope used on the dark hole the first time: it is tied there for good")
    val ROPE_ALREADY = line("rope_already", "a rope used on the dark hole once one is tied there")
    val SIGN = line("sign", "Read on a warning sign by the dark hole, in a message box (the Wiki's transcript, four lines)")
    val INSECTS_WARN = line("insects_warn", "after 15 ticks in the dark with no light (the Wiki's Darkness page)")
    val INSECTS_SWARM = line("insects_swarm", "after 30 ticks in the dark with no light, as the bites start (the Wiki's Darkness page)")
    val GAS_FLARE = line("gas_flare", "walking into the gas with a naked flame (the Wiki's Light sources page)", "item")
    val GAS_EXPLODE = line("gas_explode", "the gas exploding, 7 ticks after the flare")
    val CHASM_CLOSED = line("chasm_closed", "Enter on the tunnel to the Chasm of Tears, which belongs to piece 8 (our own line)")
}

/**
 * The Lumbridge Swamp Caves' rules (Misthalin piece 7 step 2b): the way in and out, the dark, the gas and the tunnel
 * that refuses. The caves' monsters are step 3.
 *
 * - The dark hole (`goblin_cave_entrance` 5947, by the candle seller) needs a rope used on it once; varbit 279
 *   (`swamp_caves_roped_entrance`) remembers it, and Climb-down then goes down with no rope. The climbing rope (5946)
 *   below climbs back up. Both keep the player's tile, 6400 apart, as any dungeon ladder does ([climbDungeonLadder]).
 * - The warning signs (15566) show their words in a message box.
 * - The dark: [DARK] is every tile of the caves on level 0. With no light ([LightSources.hasLight]) a player is warned
 *   after [WARN_TICKS] and swarmed after [SWARM_TICKS], then takes 1 typeless damage every tick until they light
 *   something or leave; a light resets the count. A player within [ESCAPE_RANGE] tiles of the climbing rope climbs back
 *   up instead of being swarmed.
 * - The gas: on [GAS] tiles a naked flame ([LightSources.nakedFlames]) flares, and [GAS_TICKS] ticks later, if the
 *   player is still in the gas with one, it explodes for a quarter of their Hitpoints level and puts the naked flames
 *   out.
 * - The stepping stones (5948, 5949) are Agility shortcuts in skills/agility.json; a fall puts the lights out.
 * - The tunnel to the Chasm of Tears (6659) refuses: the Chasm is piece 8's.
 *
 * Sources: the OSRS Wiki's Lumbridge Swamp Caves, Darkness, Light sources (the 25% gas damage, the flare and the 7 ticks;
 * the caves page's older "12 damage" is not used), Dark hole and Warning sign pages (read 2026-11-09); ids, the varbit
 * and the tiles from the 242 cache and map. Guessed: the gas explosion's message, the escape climbing with no message,
 * where the auto-climb lands ([ROPE_TOP]), the rope's messages, and that the count restarts on leaving. Out: the
 * screen's darkness (varbit 278 `darkness_level`, whose client-side meaning isn't checked), refusing attacks with no
 * light, the Fire of Eternal Light (Construction), the Lumbridge cellar's hole and the Dorgesh-Kaan side (piece 8).
 */
object LumbridgeSwampCaves {
    const val DARK_HOLE = 5947
    const val CLIMBING_ROPE = 5946
    const val WARNING_SIGN = 15566
    const val CHASM_TUNNEL = 6659
    const val ROPE = 954

    const val CLIMB_DOWN = 1
    const val CLIMB = 1
    const val READ = 1
    const val ENTER = 1

    /** swamp_caves_roped_entrance: 1 once a rope is tied to the dark hole. */
    const val ROPED_VARBIT = 279

    /** The climbing rope's tile below, and where a player who escapes the dark lands above (beside the dark hole). */
    const val ROPE_X = 3169
    const val ROPE_Z = 9572
    val ROPE_TOP = Triple(3168, 3172, 0)

    const val WARN_TICKS = 15
    const val SWARM_TICKS = 30
    const val GAS_TICKS = 7
    const val ESCAPE_RANGE = 3

    val DARK: Set<Long> = load("lumbridge-swamp-caves-dark.txt")
    val GAS: Set<Long> = load("lumbridge-swamp-caves-gas.txt")

    private fun key(x: Int, z: Int) = (x.toLong() shl 32) or (z.toLong() and 0xffffffffL)

    fun isDark(x: Int, z: Int, level: Int) = level == 0 && key(x, z) in DARK

    fun isGas(x: Int, z: Int, level: Int) = level == 0 && key(x, z) in GAS

    fun nearRope(x: Int, z: Int) = maxOf(abs(x - ROPE_X), abs(z - ROPE_Z)) <= ESCAPE_RANGE

    /** The gas's damage: a quarter of the Hitpoints level, at least 1. */
    fun gasDamage(hitpointsLevel: Int) = (hitpointsLevel / 4).coerceAtLeast(1)

    /** Rows of "z x1-x2 x3-x4" in a resource under areas/, as tile keys. */
    internal fun parse(text: String): Set<Long> = buildSet {
        for (line in text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }) {
            val parts = line.split(' ')
            val z = parts[0].toInt()
            for (run in parts.drop(1)) {
                val (from, to) = run.split('-').map(String::toInt)
                for (x in from..to) add(key(x, z))
            }
        }
    }

    private fun load(name: String): Set<Long> =
        parse(LumbridgeSwampCaves::class.java.getResource("/areas/$name")!!.readText())

    /** Whether the player has tied a rope to the dark hole. */
    fun roped(player: Player) = player.vars.types.varbit(ROPED_VARBIT) != null && player.vars.getVarbit(ROPED_VARBIT) == 1

    internal suspend fun ScriptContext.climbDown(world: World, hole: ZoneLoc) {
        if (!roped(player)) return world.message(player, SwampCavesLines.NEED_ROPE)
        climbDungeonLadder(world, hole, down = true, anim = Climbing.ANIM_PICKUP_FLOOR)
    }

    internal suspend fun ScriptContext.tieRope(world: World, op: osrsps.game.script.Op) {
        arriveDelay()
        if (roped(player)) return world.message(player, SwampCavesLines.ROPE_ALREADY)
        if (player.inventory[op.sub]?.id != ROPE || player.vars.types.varbit(ROPED_VARBIT) == null) return
        player.inventory.remove(op.sub, 1)
        player.vars.setVarbit(ROPED_VARBIT, 1)
        world.message(player, SwampCavesLines.ROPE_TIED)
    }
}

/**
 * Each player's time in the caves' dark and gas, run once a tick from [World] after the movement phase. A player out
 * of the dark has no entry.
 */
class SwampCavesHazards(private val world: World) {
    private class State(var darkTicks: Int = 0, var gasTicks: Int = 0)

    private val states = HashMap<Player, State>()

    /** Typeless damage from the insects or the gas, as a hit from no one; tests swap it for a recorder. */
    @Volatile internal var hurt: (Player, Int) -> Unit = { player, damage ->
        world.meleeCombat.statusHit(player, damage, HitsplatType.DAMAGE_ME, HitsplatType.DAMAGE_OTHER)
    }

    fun process(player: Player): Unit = with(LumbridgeSwampCaves) {
        if (player.loggedOut || !isDark(player.x, player.z, player.level)) {
            states.remove(player)
            return
        }
        val state = states.getOrPut(player) { State() }
        gas(player, state)
        if (LightSources.hasLight(player)) {
            state.darkTicks = 0
            return
        }
        state.darkTicks++
        when {
            state.darkTicks == WARN_TICKS -> world.message(player, SwampCavesLines.INSECTS_WARN)
            state.darkTicks < SWARM_TICKS -> {}
            state.darkTicks == SWARM_TICKS && nearRope(player.x, player.z) -> {
                states.remove(player)
                world.teleport(player, ROPE_TOP.first, ROPE_TOP.second, ROPE_TOP.third)
            }
            else -> {
                if (state.darkTicks == SWARM_TICKS) world.message(player, SwampCavesLines.INSECTS_SWARM)
                hurt(player, 1)
            }
        }
    }

    private fun gas(player: Player, state: State): Unit = with(LumbridgeSwampCaves) {
        val flames = LightSources.nakedFlames(player)
        if (flames.isEmpty() || !isGas(player.x, player.z, player.level)) {
            state.gasTicks = 0
            return
        }
        if (state.gasTicks == 0) {
            val name = world.objTypes[flames.first().lit]?.name?.lowercase() ?: flames.first().name
            world.message(player, SwampCavesLines.GAS_FLARE, "item" to name)
        }
        state.gasTicks++
        if (state.gasTicks < GAS_TICKS) return
        state.gasTicks = 0
        LightSources.extinguish(player) { it.nakedFlame }
        world.message(player, SwampCavesLines.GAS_EXPLODE)
        hurt(player, gasDamage(player.skills.baseLevel(Skill.HITPOINTS)))
    }

    /** Forgets everyone, as a reset between tests would. */
    internal fun clear() = states.clear()
}

fun registerLumbridgeSwampCaves(world: World) {
    with(LumbridgeSwampCaves) {
        world.handlers.onLoc(DARK_HOLE, CLIMB_DOWN) { op -> climbDown(world, op.loc!!) }
        world.handlers.onObjOnLoc(DARK_HOLE, ROPE) { op -> tieRope(world, op) }
        world.handlers.onLoc(CLIMBING_ROPE, CLIMB) { op ->
            climbDungeonLadder(world, op.loc!!, down = false, anim = Climbing.ANIM_REACH_FOR_LADDER)
        }
        world.handlers.onLoc(WARNING_SIGN, READ) {
            arriveDelay()
            world.dialogues.start(this) { mesbox(SwampCavesLines.SIGN) }
        }
        world.handlers.onLoc(CHASM_TUNNEL, ENTER) {
            arriveDelay()
            world.message(player, SwampCavesLines.CHASM_CLOSED)
        }
    }
}
