package osrsps.game.skill

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc
import osrsps.game.zone.ZoneObj
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap
import osrsps.map.LocShape
import osrsps.map.Steps
import osrsps.map.Tile

/** Firemaking's words, in the dialogue file. */
object FiremakingLines : Lines("firemaking", "Firemaking") {
    val ATTEMPT = line("attempt", "the message on starting to light logs")
    val CATCHES = line("catches", "the message when the fire is lit")
    val LEVEL = line("level", "the message when the logs need a higher level", "level", "logs")
    val CANT_HERE = line("cant_here", "the message where a fire can't be lit")
    val NO_TINDERBOX = line("no_tinderbox", "the message on Light with no tinderbox")
}

/** One kind of log: the level to burn it and the XP, in tenths. */
class LogType(val obj: Int, val level: Int, val xpTenths: Int)

/**
 * Lighting fires: a tinderbox used on logs (or logs on a tinderbox) drops the logs and lights them where the player
 * stands; Light on logs on the ground lights them there. The first try strikes the tinderbox and waits [CYCLE] - 1 ticks,
 * then a roll every [CYCLE] ticks re-runs the op on the logs ([World.repeatOp]) until one lights them. A lit fire burns
 * for 100 to 199 ticks and leaves ashes; the player steps west off it, else east, south or north.
 *
 * Sources: the OSRS Wiki's Firemaking page (read 2026-10-29) for levels, XP, where the player steps, and the success
 * chance (crowdsourced there: 65/256 at level 1 to 513/256 at 99, which is the 64/512 pair the 2004Scape project's
 * script rolls with); the rest is 2004Scape's firemaking script, not checked against live: the 4-tick clock (the next
 * log after a fire can roll 4 ticks after it, which is what makes lines of fires quick), the messages, where a fire
 * can't go (a tile with a loc on it or that blocks walking), the fire's burn time and the ashes. Guessed: the player
 * walks the step where 2004Scape moves them at once; fires can go in banks (no bank areas in our data yet). Out:
 * bows as tinderboxes (barbarian firemaking), coloured logs, Forester's campfires, a tinderbox used on logs on the
 * ground. No sounds: the 241 synth ids have no names.
 */
class Firemaking(private val world: World, private val collision: CollisionMap) {
    private class Fire(val loc: ZoneLoc, val outTick: Long)

    /** Fires burning, to leave ashes when they go out. */
    private val fires = ArrayList<Fire>()

    fun register() {
        for (log in LOGS) {
            val type = world.objTypes[log.obj] ?: continue
            val option = (1..5).firstOrNull { type.ops.getOrNull(it - 1) == "Light" } ?: LIGHT_OPTION
            world.handlers.onObjOnObj(TINDERBOX, log.obj) { op ->
                // Either way round, the log's slot is the one that isn't the tinderbox's.
                val slot = if (op.obj == log.obj) op.objSlot else op.sub
                lightHeld(slot, option, log)
            }
            world.handlers.onGroundObj(log.obj, option) { op -> light(op.groundObj ?: return@onGroundObj, option, log, op.repeat) }
        }
    }

    /** Puts out the fires whose time is up, leaving ashes. Call once a tick. */
    fun process() {
        if (fires.isEmpty()) return
        val tick = world.tick
        val out = fires.filter { it.outTick <= tick }
        if (out.isEmpty()) return
        fires.removeAll(out.toSet())
        val ashes = world.objTypes[ASHES] ?: return
        for (fire in out) {
            world.locs.remove(fire.loc)
            world.groundObjs.addPublic(ashes, 1, fire.loc.x, fire.loc.z, fire.loc.level, ASHES_DESPAWN_TICKS)
        }
    }

    private suspend fun ScriptContext.lightHeld(slot: Int, option: Int, log: LogType) {
        if (player.inventory[slot]?.id != log.obj) return
        if (!canLight(log, player.x, player.z)) return
        val obj = world.groundObjs.drop(player, slot) ?: return
        light(obj, option, log, repeat = false)
    }

    private suspend fun ScriptContext.light(obj: ZoneObj, option: Int, log: LogType, repeat: Boolean) {
        // As for woodcutting: a box opened since the last try (a level-up) ends it.
        if (repeat && player.modalOpen) return player.anim(-1)
        // Checked before every try, as 2004Scape does: the tinderbox may have been dropped, or a fire lit on the tile.
        if (!canLight(log, obj.x, obj.z)) return player.anim(-1)
        val text = world.textFor(player)
        when (SkillClock.step(player, tick, CYCLE)) {
            ClockStep.STARTED -> {
                player.anim(ANIM)
                player.message(text[FiremakingLines.ATTEMPT])
            }
            ClockStep.WAITING -> {}
            ClockStep.DUE -> {
                if (SuccessRoll.roll(world.random, LOW, HIGH, player.skills.currentLevel(Skill.FIREMAKING))) {
                    player.actionDelay = tick + CYCLE
                    lit(obj, log)
                    return
                }
                // A failed roll strikes again and waits a whole cycle, with no new message (2004Scape).
                player.actionDelay = tick + CYCLE
                player.anim(ANIM)
            }
        }
        world.repeatOp(player, Op(OpTarget.GROUND_OBJ, obj.id, option, groundObj = obj, repeat = true))
    }

    /** Whether [player] may light [log] on [x], [z], telling them why not. */
    private fun ScriptContext.canLight(log: LogType, x: Int, z: Int): Boolean {
        val text = world.textFor(player)
        if (player.skills.currentLevel(Skill.FIREMAKING) < log.level) {
            val name = world.objTypes[log.obj]?.name?.lowercase() ?: "logs"
            player.message(text[FiremakingLines.LEVEL, "level" to log.level, "logs" to name])
            return false
        }
        // Nor where a loc is due back, such as a picked crop: the fire would take over the tile and the crop would
        // never return (third review, 30 Oct 2026). Guessed; whether live allows a fire there is unchecked.
        val occupied = world.locs.at(x, z, player.level).isNotEmpty() || world.locs.reverting(x, z, player.level)
        if (occupied || collision[x, z, player.level] and CollisionFlag.BLOCK_WALK != 0) {
            player.message(text[FiremakingLines.CANT_HERE])
            return false
        }
        if (player.inventory.count(TINDERBOX) < 1) {
            player.message(text[FiremakingLines.NO_TINDERBOX])
            return false
        }
        return true
    }

    private fun ScriptContext.lit(obj: ZoneObj, log: LogType) {
        if (!world.groundObjs.remove(obj)) return
        stepOff(player, obj.x, obj.z)
        player.skills.addTenths(Skill.FIREMAKING, log.xpTenths)
        player.infos.playerInfo.avatar.extendedInfo.setFaceLoc(obj.x, obj.z, 1, 1, instant = false, walkMode = 0)
        player.message(world.textFor(player)[FiremakingLines.CATCHES])
        val duration = world.random.between(BURN_LOW, BURN_HIGH)
        val fire = ZoneLoc(FIRE, LocShape.CENTREPIECE_STRAIGHT, 0, obj.x, obj.z, obj.level)
        world.locs.add(fire)
        fires += Fire(fire, tick + duration)
    }

    /** Walks [player] a step off the logs' tile: west if they can, else east, south, north; nowhere if all are blocked. */
    private fun stepOff(player: Player, x: Int, z: Int) {
        player.anim(-1)
        if (player.x != x || player.z != z) return
        for ((dx, dz) in STEPS) {
            if (!Steps.canStep(collision, player.level, x, z, dx, dz)) continue
            player.waypoints.clear()
            player.waypoints.add(Tile(x + dx, z + dz))
            return
        }
    }

    companion object {
        const val TINDERBOX = 590 // tinderbox
        const val ASHES = 592 // ashes
        const val FIRE = 26185 // fire

        /** human_createfire. */
        const val ANIM = 733

        /** A roll every 4 ticks: 2004Scape's script. */
        const val CYCLE = 4

        /** The success pair: 65/256 at level 1, sure from 43 (Wiki, crowdsourced). */
        const val LOW = 64
        const val HIGH = 512

        /** 2004Scape: 100 + random(100) ticks. */
        const val BURN_LOW = 100
        const val BURN_HIGH = 199

        /** 2004Scape: ashes stay 100 ticks. */
        const val ASHES_DESPAWN_TICKS = 100

        /** Logs' op4 on the ground, the cache's "Light"; used when the type doesn't say (tests). */
        const val LIGHT_OPTION = 4

        private val STEPS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

        /** Levels and XP from the Wiki's Firemaking page; obj ids by gameval name. */
        val LOGS = listOf(
            LogType(1511, 1, 400), // logs
            LogType(2862, 1, 400), // achey_tree_logs
            LogType(1521, 15, 600), // oak_logs
            LogType(1519, 30, 900), // willow_logs
            LogType(6333, 35, 1050), // teak_logs
            LogType(10810, 42, 1250), // arctic_pine_log
            LogType(1517, 45, 1350), // maple_logs
            LogType(6332, 50, 1575), // mahogany_logs
            LogType(1515, 60, 2025), // yew_logs
            LogType(24691, 62, 960), // blisterwood_logs
            LogType(1513, 75, 3038), // magic_logs
            LogType(19669, 90, 3500), // redwood_logs
        )
    }
}
