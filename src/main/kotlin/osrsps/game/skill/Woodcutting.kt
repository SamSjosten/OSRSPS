package osrsps.game.skill

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/** Woodcutting's words, in the dialogue file. */
object WoodcuttingLines : Lines("woodcutting", "Woodcutting") {
    val SWING = line("swing", "the message on starting to chop")
    val GET_LOGS = line("get_logs", "the message for each log", "logs")
    val LEVEL = line("level", "the message when the tree needs a higher level", "level")
    val NO_AXE = line("no_axe", "the message with no axe the player's level can use")
    val INVENTORY_FULL = line("inventory_full", "the message when the backpack is full")
}

/** An axe: its tool for [SkillTools], and the tier that picks its success pair on each tree. */
class Axe(val tool: SkillTool, val tier: String)

/** One kind of tree; see woodcutting.json for what each number means. */
class TreeType(
    val key: String,
    val level: Int,
    val xpTenths: Int,
    val logs: Int,
    val respawnLow: Int,
    val respawnHigh: Int,
    /** The tree timer in ticks; null for trees that fall after one log. */
    val despawn: Int?,
    /** Success pairs (low, high) by axe tier. */
    val rates: Map<String, Pair<Int, Int>>,
)

/** woodcutting.json: the axes, best first, and every tree loc with its stump. */
class WoodcuttingData(val axes: List<Axe>, val trees: Map<Int, TreeType>, val stumps: Map<Int, Int>) {
    val tools = SkillTools(Skill.WOODCUTTING, axes.map { it.tool })
    private val tiers = axes.associate { it.tool.obj to it.tier }

    fun tier(axe: SkillTool): String = tiers.getValue(axe.obj)

    companion object {
        const val RESOURCE = "/skills/woodcutting.json"

        fun load(): WoodcuttingData {
            val text = WoodcuttingData::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            return parse(text)
        }

        fun parse(text: String): WoodcuttingData {
            val root = Json.parseToJsonElement(text).jsonObject
            val axes = root.getValue("axes").jsonArray.map { it.jsonObject }.map { axe ->
                Axe(SkillTool(axe.int("obj"), axe.int("level"), axe.int("anim")), axe.getValue("tier").jsonPrimitive.content)
            }
            val trees = HashMap<Int, TreeType>()
            val stumps = HashMap<Int, Int>()
            for (tree in root.getValue("trees").jsonArray.map { it.jsonObject }) {
                val respawn = tree.getValue("respawn").jsonArray.map { it.jsonPrimitive.int }
                val rates = tree.getValue("rates").jsonObject.mapValues { (_, pair) ->
                    pair.jsonArray.map { it.jsonPrimitive.int }.let { it[0] to it[1] }
                }
                val type = TreeType(
                    tree.getValue("key").jsonPrimitive.content, tree.int("level"), tree.int("xpTenths"), tree.int("logs"),
                    respawn[0], respawn[1], tree.getValue("despawn").jsonPrimitive.intOrNull, rates,
                )
                require(axes.all { it.tier in rates }) { "Tree ${type.key} has no success pair for some axe tier" }
                for (loc in tree.getValue("locs").jsonArray.map { it.jsonObject }) {
                    require(trees.put(loc.int("tree"), type) == null) { "Tree loc ${loc.int("tree")} is listed twice" }
                    stumps[loc.int("tree")] = loc.int("stump")
                }
            }
            return WoodcuttingData(axes, trees, stumps)
        }

        private fun JsonObject.int(name: String) = getValue(name).jsonPrimitive.int
    }
}

/**
 * Chopping trees: Chop down on a tree, or an axe used on one, starts an action that re-runs every tick ([World.repeatOp])
 * and rolls for a log every [CYCLE] ticks on the skill clock, with the tree's success pair for the best axe the player
 * can use. Regular, dead and evergreen trees fall after one log; the others have a tree timer that runs down one a
 * tick while anyone chops and back up one a tick while nobody does, and fall at the first log after it runs out.
 *
 * Sources: the OSRS Wiki's Woodcutting, tree and axe pages (read 2026-10-29) for levels, XP, rolls every 4 ticks, success
 * charts, timers and respawns; the flow of each tick (checks, the swing message on the click, the animation again on
 * each roll) is the 2004Scape project's woodcut script. Guessed: the timer's run-down and refill rate of one a tick, one
 * run-down a tick however many chop, and falling on the first log after it runs out (the Wiki gives only the totals);
 * the animation starting on the click's own tick (2004Scape waits a tick). No chopping sound is sent: the 241 synth ids
 * have no names to find it by.
 */
class Woodcutting(private val world: World, val data: WoodcuttingData, private val nests: BirdNests) {
    private class TreeTimer(var left: Int, var lastTick: Long)

    /** Timed trees being chopped, by tile and level. */
    private val timers = HashMap<Long, TreeTimer>()

    fun register() {
        for ((locId, tree) in data.trees) {
            val option = chopOption(locId)
            world.handlers.onLoc(locId, option) { op -> chop(op, tree) }
            for (axe in data.axes) {
                world.handlers.onObjOnLoc(locId, axe.tool.obj) { op ->
                    // An axe used on a tree chops it, as clicking Chop down does.
                    chop(Op(OpTarget.LOC, locId, option, loc = op.loc), tree)
                }
            }
        }
    }

    /** The tree's chop option (Chop down, or Chop on a few), 1 to 5; 1 when the cache doesn't say (tests). */
    private fun chopOption(locId: Int): Int {
        val ops = world.locs.types[locId]?.ops ?: return 1
        val index = ops.indexOfFirst { it != null && it.startsWith("Chop") }
        return if (index >= 0) index + 1 else 1
    }

    private suspend fun ScriptContext.chop(op: Op, tree: TreeType) {
        val loc = op.loc ?: return
        // A box opened since the last tick (the level-up a log gave) ends the action, as RS2's busy() check does in the
        // 2004Scape engine. Guessed for OSRS that it ends rather than waits; live play shows a level-up stopping you.
        if (op.repeat && player.modalOpen) return stop()
        val text = world.textFor(player)
        if (player.skills.currentLevel(Skill.WOODCUTTING) < tree.level) {
            player.message(text[WoodcuttingLines.LEVEL, "level" to tree.level])
            return stop()
        }
        val axe = when (val check = data.tools.best(player)) {
            is ToolCheck.Found -> check.tool
            else -> {
                player.message(text[WoodcuttingLines.NO_AXE])
                return stop()
            }
        }
        if (player.inventory.freeSlots == 0) {
            player.message(text[WoodcuttingLines.INVENTORY_FULL])
            return stop()
        }
        if (!op.repeat) {
            player.message(text[WoodcuttingLines.SWING])
            player.anim(axe.anim)
        }
        val timer = chopped(loc, tree)
        if (SkillClock.step(player, tick, CYCLE) == ClockStep.DUE) {
            player.anim(axe.anim)
            val (low, high) = tree.rates.getValue(data.tier(axe))
            if (SuccessRoll.roll(world.random, low, high, player.skills.currentLevel(Skill.WOODCUTTING))) {
                val logs = world.objTypes[tree.logs] ?: error("No obj type ${tree.logs} for ${tree.key} logs")
                player.inventory.add(logs, 1)
                player.skills.addTenths(Skill.WOODCUTTING, tree.xpTenths)
                player.message(text[WoodcuttingLines.GET_LOGS, "logs" to logs.name.lowercase()])
                nests.roll(player)
                if (timer == null || timer.left <= 0) {
                    fell(loc, tree)
                    return stop()
                }
            }
        }
        world.repeatOp(player, op)
    }

    /** Ends the action: the player stops swinging. */
    private fun ScriptContext.stop() {
        player.anim(-1)
    }

    /** Counts this tick's chopping on [loc]'s timer; null for a tree that falls after one log. */
    private fun ScriptContext.chopped(loc: ZoneLoc, tree: TreeType): TreeTimer? {
        val despawn = tree.despawn ?: return null
        val key = key(loc)
        val timer = timers[key]
        if (timer == null) {
            // The first cut starts the timer, and counts as its first tick.
            return TreeTimer(despawn - 1, tick).also { timers[key] = it }
        }
        if (timer.lastTick == tick) return timer
        val idle = (tick - timer.lastTick - 1).coerceAtMost(despawn.toLong()).toInt()
        timer.left = (timer.left + idle).coerceAtMost(despawn) - 1
        timer.lastTick = tick
        return timer
    }

    private fun fell(loc: ZoneLoc, tree: TreeType) {
        timers.remove(key(loc))
        val stump = data.stumps.getValue(loc.id)
        world.locs.replace(loc, stump, revertAfter = world.random.between(tree.respawnLow, tree.respawnHigh))
    }

    private fun key(loc: ZoneLoc): Long = (loc.level.toLong() shl 28) or (loc.x.toLong() shl 14) or loc.z.toLong()

    companion object {
        /** A roll every 4 ticks: the Wiki's tree pages. */
        const val CYCLE = 4
    }
}
