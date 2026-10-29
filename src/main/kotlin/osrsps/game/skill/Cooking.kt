package osrsps.game.skill

import java.util.WeakHashMap
import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/** Cooking's words, in the dialogue file. */
object CookingLines : Lines("cooking", "Cooking") {
    val TITLE = line("title", "the Make-X box's title over the food to cook")
    val LEVEL = line("level", "the message when the food needs a higher level", "level")
    val RANGE_ONLY = line("range_only", "the message on using food that only cooks on a range on a fire")
    val COOKED_SHRIMPS = line("cooked_shrimps", "Shrimps: the message on cooking them")
    val BURNT_SHRIMPS = line("burnt_shrimps", "Shrimps: the message on burning them")
    val COOKED_ANCHOVIES = line("cooked_anchovies", "Anchovies: the message on cooking them")
    val BURNT_ANCHOVIES = line("burnt_anchovies", "Anchovies: the message on burning them")
    val COOKED_SARDINE = line("cooked_sardine", "Sardine: the message on cooking one")
    val BURNT_SARDINE = line("burnt_sardine", "Sardine: the message on burning one")
    val COOKED_HERRING = line("cooked_herring", "Herring: the message on cooking one")
    val BURNT_HERRING = line("burnt_herring", "Herring: the message on burning one")
    val COOKED_TROUT = line("cooked_trout", "Trout: the message on cooking one")
    val BURNT_TROUT = line("burnt_trout", "Trout: the message on burning one")
    val COOKED_PIKE = line("cooked_pike", "Pike: the message on cooking one")
    val BURNT_PIKE = line("burnt_pike", "Pike: the message on burning one")
    val COOKED_SALMON = line("cooked_salmon", "Salmon: the message on cooking one")
    val BURNT_SALMON = line("burnt_salmon", "Salmon: the message on burning one")
    val COOKED_MEAT = line("cooked_meat", "Cooked meat: the message on cooking raw beef, rat or bear meat")
    val BURNT_MEAT = line("burnt_meat", "Cooked meat: the message on burning it")
    val COOKED_CHICKEN = line("cooked_chicken", "Cooked chicken: the message on cooking it")
    val BURNT_CHICKEN = line("burnt_chicken", "Cooked chicken: the message on burning it")
    val COOKED_BREAD = line("cooked_bread", "Bread: the message on baking it")
    val BURNT_BREAD = line("burnt_bread", "Bread: the message on burning it")
}

/**
 * One food: raw, cooked and burnt objs, the level and XP in tenths, and its success pairs (out of 256): on a fire or
 * range, and on the Lumbridge Castle range. [rangeOnly] food (bread) won't cook on a fire.
 */
class Food(
    val raw: Int,
    val cooked: Int,
    val burnt: Int,
    val level: Int,
    val xpTenths: Int,
    val low: Int,
    val high: Int,
    val lumbridgeLow: Int,
    val lumbridgeHigh: Int,
    val cookedLine: Line,
    val burntLine: Line,
    val rangeOnly: Boolean = false,
)

/**
 * Cooking: raw food used on a fire or range, or Cook on a range, cooks it. With more than one of the food (or, on a
 * range, more than one kind) the Make-X box asks which and how many; with one, it cooks straight away. Each piece takes
 * [CYCLE] ticks on the skill clock and rolls its success pair (the Lumbridge Castle range has its own, lower burn rate);
 * a miss burns it. The action re-runs every tick on the fire or range ([World.repeatOp]) until the count is done, the
 * food runs out, a box opens (a level-up) or the fire goes out.
 *
 * Sources: the OSRS Wiki's food pages and Cooking (read 2026-10-29) for levels, XP, 4 ticks a piece, which foods need a
 * range, and the success charts, which each pair is fitted to (`CookingTest` checks the points): fire and range share a
 * chart for these foods, the Lumbridge range has its own. Guessed: the Make-X box showing the raw food, its quantity
 * starting on all of it, cooking straight away with only one, the first piece 3 ticks after the choice (as the other
 * gathering skills' first roll), and the burnt items (by the Wiki's Burnt fish pages). Out: cooking gauntlets, the
 * cooking cape, the Hosidius range, wines and other multi-step food, and the sounds (the 241 synth ids have no names).
 */
class Cooking(private val world: World) {
    /** What each player is cooking, by the fire or range they chose it at. */
    private class Job(val food: Food, var left: Int, val loc: ZoneLoc)

    private val jobs = WeakHashMap<Player, Job>()

    fun register() {
        for (loc in FIRES + RANGES) {
            val option = cookOption(loc)
            // A fire has no options: the re-run uses option 1, which the client never sends for it.
            world.handlers.onLoc(loc, option) { op ->
                if (op.repeat) cook(op) else if (loc in RANGES) chooseAtRange(op)
            }
            for (food in FOODS) {
                world.handlers.onObjOnLoc(loc, food.raw) { op -> chooseFood(op.loc ?: return@onObjOnLoc, option, food) }
            }
        }
    }

    /** The range's Cook option, 1 to 5; 1 for a fire, or when the cache doesn't say (tests). */
    private fun cookOption(locId: Int): Int {
        val ops = world.locs.types[locId]?.ops ?: return 1
        val index = ops.indexOfFirst { it == "Cook" }
        return if (index >= 0) index + 1 else 1
    }

    /** Raw food used on a fire or range: that food, how many if there are several. */
    private suspend fun ScriptContext.chooseFood(loc: ZoneLoc, option: Int, food: Food) {
        if (!canCook(loc, food)) return
        val held = player.inventory.count(food.raw).toInt()
        val quantity = if (held <= 1) 1 else askMakeX(listOf(food.raw), held).second
        start(loc, option, food, quantity)
    }

    /** Cook on a range: every kind of raw food the player holds, which and how many. */
    private suspend fun ScriptContext.chooseAtRange(op: Op) {
        val loc = op.loc ?: return
        val held = FOODS.filter { player.inventory.count(it.raw) > 0 }.distinctBy { it.raw }.take(MAX_KINDS)
        if (held.isEmpty()) {
            // Guessed: Cook with nothing to cook says what a raw food would.
            player.message(world.textFor(player)[GameLines.NOTHING_INTERESTING])
            return
        }
        val most = held.maxOf { player.inventory.count(it.raw) }.toInt()
        val (index, quantity) = if (held.size == 1 && most == 1) {
            0 to 1
        } else {
            askMakeX(held.map { it.raw }, most)
        }
        val food = held[index]
        if (!canCook(loc, food)) return
        start(loc, op.option, food, quantity)
    }

    /** The Make-X box over [objs]: the index picked and how many. */
    private suspend fun ScriptContext.askMakeX(objs: List<Int>, most: Int): Pair<Int, Int> {
        var picked = 0 to 1
        world.dialogues.start(this) { picked = makeX(CookingLines.TITLE, objs, most) }
        return picked
    }

    private fun ScriptContext.start(loc: ZoneLoc, option: Int, food: Food, quantity: Int) {
        if (!world.locs.exists(loc)) return
        // Again after the box: a boost can wear off while it is open.
        if (!canCook(loc, food)) return
        // The box closes as cooking starts, rather than after this script, so the first tick doesn't see it open.
        world.interfaces.closeChatModal(player)
        jobs[player] = Job(food, quantity, loc)
        // The first tick runs with the interactions rather than here: run here as well, it could cook a due piece that
        // the interaction then cooks again on the same tick.
        world.repeatOp(player, Op(OpTarget.LOC, loc.id, option, loc = loc, repeat = true))
    }

    /** Whether [player] can cook [food] on [loc], telling them why not. */
    private fun ScriptContext.canCook(loc: ZoneLoc, food: Food): Boolean {
        val text = world.textFor(player)
        if (food.rangeOnly && loc.id !in RANGES) {
            player.message(text[CookingLines.RANGE_ONLY])
            return false
        }
        if (player.skills.currentLevel(Skill.COOKING) < food.level) {
            player.message(text[CookingLines.LEVEL, "level" to food.level])
            return false
        }
        return true
    }

    /** One tick of cooking: the animation as each piece starts, a piece every [CYCLE] ticks, then the re-run. */
    private fun ScriptContext.cook(op: Op) {
        val loc = op.loc ?: return
        val job = jobs[player]
        if (job == null || job.loc != loc) return finish()
        // A level-up box opened since the last tick ends it, as for the other gathering skills.
        if (player.modalOpen) return finish()
        if (job.left <= 0 || player.inventory.count(job.food.raw) < 1) return finish()
        // Guessed: a boost wearing off below the food's level stops the job without a word.
        if (player.skills.currentLevel(Skill.COOKING) < job.food.level) return finish()
        val range = loc.id in RANGES
        val anim = if (range) RANGE_ANIM else FIRE_ANIM
        when (SkillClock.step(player, tick, CYCLE)) {
            ClockStep.STARTED -> player.anim(anim)
            ClockStep.WAITING -> {}
            ClockStep.DUE -> {
                cookOne(job.food, loc)
                job.left--
                // The next piece's animation comes with the clock starting again next tick.
                if (job.left <= 0 || player.inventory.count(job.food.raw) < 1) return finish(stopAnim = false)
            }
        }
        world.repeatOp(player, op)
    }

    private fun ScriptContext.cookOne(food: Food, loc: ZoneLoc) {
        val slot = player.inventory.contents().firstOrNull { it.second.id == food.raw }?.first ?: return
        player.inventory.remove(slot, 1)
        val lumbridge = loc.id == LUMBRIDGE_RANGE
        val low = if (lumbridge) food.lumbridgeLow else food.low
        val high = if (lumbridge) food.lumbridgeHigh else food.high
        val text = world.textFor(player)
        if (SuccessRoll.roll(world.random, low, high, player.skills.currentLevel(Skill.COOKING))) {
            val cooked = world.objTypes[food.cooked] ?: error("No obj type ${food.cooked} for cooked food")
            player.inventory.add(cooked, 1)
            player.skills.addTenths(Skill.COOKING, food.xpTenths)
            player.message(text[food.cookedLine])
        } else {
            val burnt = world.objTypes[food.burnt] ?: error("No obj type ${food.burnt} for burnt food")
            player.inventory.add(burnt, 1)
            player.message(text[food.burntLine])
        }
    }

    private fun ScriptContext.finish(stopAnim: Boolean = true) {
        jobs.remove(player)
        if (stopAnim) player.anim(-1)
    }

    companion object {
        /** The Wiki: 4 ticks a piece. */
        const val CYCLE = 4

        /** human_cooking, human_firecooking. */
        const val RANGE_ANIM = 896
        const val FIRE_ANIM = 897

        /** The fire Firemaking lights. */
        val FIRES = setOf(Firemaking.FIRE)

        /** Lumbridge Castle's kitchen range (cooksquestrange) and the plain range (range), by gameval name. */
        const val LUMBRIDGE_RANGE = 114
        const val RANGE = 26181
        val RANGES = setOf(LUMBRIDGE_RANGE, RANGE)

        /** The Make-X box shows up to 18 kinds. */
        private const val MAX_KINDS = 18

        /** Levels and XP from the Wiki's food pages; pairs fitted to their charts. Obj ids by gameval name. */
        val SHRIMPS = Food(317, 315, 7954, 1, 300, 128, 512, 138, 532, CookingLines.COOKED_SHRIMPS, CookingLines.BURNT_SHRIMPS)
        val ANCHOVIES = Food(321, 319, 323, 1, 300, 128, 512, 138, 532, CookingLines.COOKED_ANCHOVIES, CookingLines.BURNT_ANCHOVIES)
        val SARDINE = Food(327, 325, 369, 1, 400, 118, 492, 128, 512, CookingLines.COOKED_SARDINE, CookingLines.BURNT_SARDINE)
        val HERRING = Food(345, 347, 357, 5, 500, 108, 472, 118, 492, CookingLines.COOKED_HERRING, CookingLines.BURNT_HERRING)
        val TROUT = Food(335, 333, 343, 15, 700, 88, 432, 98, 452, CookingLines.COOKED_TROUT, CookingLines.BURNT_TROUT)
        val PIKE = Food(349, 351, 343, 20, 800, 78, 412, 88, 432, CookingLines.COOKED_PIKE, CookingLines.BURNT_PIKE)
        val SALMON = Food(331, 329, 343, 25, 900, 68, 392, 78, 402, CookingLines.COOKED_SALMON, CookingLines.BURNT_SALMON)
        val BEEF = Food(2132, 2142, 2146, 1, 300, 128, 512, 138, 532, CookingLines.COOKED_MEAT, CookingLines.BURNT_MEAT)
        val RAT_MEAT = Food(2134, 2142, 2146, 1, 300, 128, 512, 138, 532, CookingLines.COOKED_MEAT, CookingLines.BURNT_MEAT)
        val BEAR_MEAT = Food(2136, 2142, 2146, 1, 300, 128, 512, 138, 532, CookingLines.COOKED_MEAT, CookingLines.BURNT_MEAT)
        val CHICKEN = Food(2138, 2140, 2144, 1, 300, 128, 512, 138, 532, CookingLines.COOKED_CHICKEN, CookingLines.BURNT_CHICKEN)
        val BREAD = Food(2307, 2309, 2311, 1, 400, 118, 492, 128, 512, CookingLines.COOKED_BREAD, CookingLines.BURNT_BREAD, rangeOnly = true)

        val FOODS = listOf(SHRIMPS, ANCHOVIES, SARDINE, HERRING, TROUT, PIKE, SALMON, BEEF, RAT_MEAT, BEAR_MEAT, CHICKEN, BREAD)
    }
}
