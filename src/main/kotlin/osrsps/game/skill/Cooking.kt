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
    val COOKED_LOBSTER = line("cooked_lobster", "Lobster: the message on cooking one")
    val BURNT_LOBSTER = line("burnt_lobster", "Lobster: the message on burning one")
    val COOKED_TUNA = line("cooked_tuna", "Tuna: the message on cooking one")
    val BURNT_TUNA = line("burnt_tuna", "Tuna: the message on burning one")
    val COOKED_SWORDFISH = line("cooked_swordfish", "Swordfish: the message on cooking one")
    val BURNT_SWORDFISH = line("burnt_swordfish", "Swordfish: the message on burning one")
    val COOKED_SHARK = line("cooked_shark", "Shark: the message on cooking one")
    val BURNT_SHARK = line("burnt_shark", "Shark: the message on burning one")
    val COOKED_KARAMBWAN = line("cooked_karambwan", "Cooked karambwan: the message on cooking one thoroughly")
    val COOKED_POISON_KARAMBWAN = line("cooked_poison_karambwan", "Poison karambwan: the message on cooking one poorly")
    val COOKED_MONKFISH = line("cooked_monkfish", "Monkfish: the message on cooking one")
    val BURNT_MONKFISH = line("burnt_monkfish", "Monkfish: the message on burning one")
    val COOKED_ANGLERFISH = line("cooked_anglerfish", "Anglerfish: the message on cooking one")
    val BURNT_ANGLERFISH = line("burnt_anglerfish", "Anglerfish: the message on burning one")
    val BURNT_KARAMBWAN = line("burnt_karambwan", "Karambwan: the message on burning one, either way")
    val COOKED_MACKEREL = line("cooked_mackerel", "Mackerel: the message on cooking one")
    val BURNT_MACKEREL = line("burnt_mackerel", "Mackerel: the message on burning one")
    val COOKED_COD = line("cooked_cod", "Cod: the message on cooking one")
    val BURNT_COD = line("burnt_cod", "Cod: the message on burning one")
    val COOKED_BASS = line("cooked_bass", "Bass: the message on cooking one")
    val BURNT_BASS = line("burnt_bass", "Bass: the message on burning one")
    val COOKED_PIE = line("cooked_pie", "Apple pie and the other pies: the message on baking one", "item")
    val BURNT_PIE = line("burnt_pie", "Burnt pie: the message on burning any pie", "item")
    val COOKED_PIZZA = line("cooked_pizza", "Plain pizza: the message on baking one")
    val BURNT_PIZZA = line("burnt_pizza", "Burnt pizza: the message on burning one")
    val COOKED_CAKE = line("cooked_cake", "Cake: the message on baking one")
    val BURNT_CAKE = line("burnt_cake", "Burnt cake: the message on burning one")
    val COOKED_PITTA = line("cooked_pitta", "Pitta bread: the message on baking one")
    val BURNT_PITTA = line("burnt_pitta", "Burnt pitta bread: the message on burning one")
    val NO_ROOM = line("no_room", "Cake: the message when there is no room for the cake tin back (guessed)")
}

/**
 * One food: raw, cooked and burnt objs, the level and XP in tenths, and its success pairs (out of 256): on a fire (and
 * on a range, unless [rangeLow] and [rangeHigh] say otherwise), and on the Lumbridge Castle range. [rangeOnly] food
 * (bread) won't cook on a fire. [returns] comes back with each piece, cooked or burnt: the cake tin (-1 for none).
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
    val rangeLow: Int = low,
    val rangeHigh: Int = high,
    val returns: Int = -1,
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
 * cooking cape, the Hosidius range, wines, stews and dairy, and the sounds (the 241 synth ids have no names).
 * Lobster, tuna, swordfish and shark came with Fishing and Cooking 2's step FC1; mackerel, cod and bass with FC2.
 *
 * Monkfish and anglerfish came with step FC4; pies, pizza, cake and pitta bread with FC5a, whose mixing is
 * [CookingMixes].
 *
 * Karambwan (step FC3) cooks two ways from the one raw fish, so the box always asks, showing the cooked karambwan first
 * (the Wiki's change of 28 February 2024 made it the default) and the poison karambwan second. Out: the poison
 * karambwan's 1-tick first piece (the Wiki's Poison karambwan recipe) and the warning before making one.
 */
class Cooking(private val world: World) {
    /** What each player is cooking, by the fire or range they chose it at. */
    private class Job(val food: Food, var left: Int, val loc: ZoneLoc) {
        /** Whether the first piece's animation was played. */
        var animated = false
    }

    private val jobs = WeakHashMap<Player, Job>()

    fun register() {
        for (loc in FIRES + COOK_FIRES + RANGES) {
            val option = cookOption(loc)
            // A fire has no options: the re-run uses option 1, which the client never sends for it.
            world.handlers.onLoc(loc, option) { op ->
                if (op.repeat) cook(op) else if (loc in RANGES || loc in COOK_FIRES) chooseAtRange(op)
            }
            for (food in FOODS.distinctBy { it.raw }) {
                world.handlers.onObjOnLoc(loc, food.raw) { op -> chooseFood(op.loc ?: return@onObjOnLoc, option, food.raw) }
            }
        }
        CookingMixes.register(world)
    }

    /** The range's Cook option, 1 to 5; 1 for a fire, or when the cache doesn't say (tests). */
    private fun cookOption(locId: Int): Int {
        val ops = world.locs.types[locId]?.ops ?: return 1
        val index = ops.indexOfFirst { it == "Cook" }
        return if (index >= 0) index + 1 else 1
    }

    /**
     * Raw food used on a fire or range: that food, how many if there are several; for a raw food that cooks more than
     * one way (karambwan), which way too, so the box always asks.
     */
    private suspend fun ScriptContext.chooseFood(loc: ZoneLoc, option: Int, raw: Int) {
        val ways = FOODS.filter { it.raw == raw }
        val held = player.inventory.count(raw).toInt()
        if (ways.size == 1) {
            val food = ways[0]
            if (!canCook(loc, food)) return
            val quantity = if (held <= 1) 1 else askMakeX(listOf(food.raw), held).second
            start(loc, option, food, quantity)
            return
        }
        val (index, quantity) = askMakeX(ways.map { it.cooked }, maxOf(held, 1))
        start(loc, option, ways[index], quantity)
    }

    /** Cook on a range: every kind of raw food the player holds, which and how many. */
    private suspend fun ScriptContext.chooseAtRange(op: Op) {
        val loc = op.loc ?: return
        // A raw food that cooks more than one way shows each, by what it makes (guessed: the others show the raw food).
        val held = FOODS.filter { player.inventory.count(it.raw) > 0 }.take(MAX_KINDS)
        if (held.isEmpty()) {
            // Guessed: Cook with nothing to cook says what a raw food would.
            player.message(world.textFor(player)[GameLines.NOTHING_INTERESTING])
            return
        }
        val most = held.maxOf { player.inventory.count(it.raw) }.toInt()
        val (index, quantity) = if (held.size == 1 && most == 1) {
            0 to 1
        } else {
            askMakeX(held.map { food -> if (held.count { it.raw == food.raw } > 1) food.cooked else food.raw }, most)
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
        // After the box too: a boost can wear off while it is open, and the box may have picked another way to cook.
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
        val step = SkillClock.step(player, tick, CYCLE)
        // The clock is shared, so one still running from the last skill (fishing just before) skips STARTED: the first
        // piece animates anyway.
        if (step == ClockStep.STARTED || !job.animated) {
            player.anim(anim)
            job.animated = true
        }
        when (step) {
            ClockStep.STARTED, ClockStep.WAITING -> {}
            ClockStep.DUE -> {
                // Guessed: with no slot for the cake tin to come back to, the cake isn't put in.
                if (job.food.returns >= 0 && player.inventory.contents().size >= player.inventory.capacity) {
                    player.message(world.textFor(player)[CookingLines.NO_ROOM])
                    return finish()
                }
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
        val (low, high) = when (loc.id) {
            LUMBRIDGE_RANGE -> food.lumbridgeLow to food.lumbridgeHigh
            in RANGES -> food.rangeLow to food.rangeHigh
            else -> food.low to food.high
        }
        val text = world.textFor(player)
        if (SuccessRoll.roll(world.random, low, high, player.skills.currentLevel(Skill.COOKING))) {
            val cooked = world.objTypes[food.cooked] ?: error("No obj type ${food.cooked} for cooked food")
            player.inventory.add(cooked, 1)
            player.skills.addTenths(Skill.COOKING, food.xpTenths)
            player.message(text.get(food.cookedLine, *named(food.cookedLine, cooked.name)))
        } else {
            val burnt = world.objTypes[food.burnt] ?: error("No obj type ${food.burnt} for burnt food")
            player.inventory.add(burnt, 1)
            player.message(text.get(food.burntLine, *named(food.burntLine, world.objTypes[food.cooked]?.name ?: "")))
        }
        if (food.returns >= 0) player.inventory.add(world.objTypes[food.returns] ?: error("No obj type ${food.returns}"), 1)
    }

    /** The cooked food's name, in lower case, for a line that asks for `item` (the pies'). */
    private fun named(line: Line, name: String): Array<Pair<String, Any>> =
        if ("item" in line.args) arrayOf("item" to name.lowercase()) else emptyArray()

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

        /**
         * Permanent fires with a Cook option (`fire_cook`, Draynor Village's south of the bank): a fire for the burn
         * chart and the animation, but Cook asks which food as a range does (the Wiki's Permanent fire page lists it
         * with the fires that have Cook).
         */
        const val DRAYNOR_FIRE = 43475
        val COOK_FIRES = setOf(DRAYNOR_FIRE)

        /** Lumbridge Castle's kitchen range (cooksquestrange) and the plain range (range), by gameval name. */
        const val LUMBRIDGE_RANGE = 114
        const val RANGE = 26181

        /** 100_dave_stove: Evil Dave's stove in Edgeville, a range by its Cook option (the Wiki's Edgeville page lists it with the ranges). */
        const val EVIL_DAVE_STOVE = 12269
        /** fai_varrock_range: the Cooks' Guild's ranges. */
        const val COOKS_GUILD_RANGE = 7183

        /**
         * fai_varrock_range_with_pipe (Range) and fai_varrock_posh_stove (Oven): Varrock's ranges in its houses and
         * kitchens, both with Cook. That the oven cooks as a range does is guessed from its Cook option.
         */
        const val VARROCK_RANGE = 7184
        const val VARROCK_OVEN = 7421
        val RANGES = setOf(LUMBRIDGE_RANGE, RANGE, EVIL_DAVE_STOVE, COOKS_GUILD_RANGE, VARROCK_RANGE, VARROCK_OVEN)

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

        /**
         * The cage and harpoon fish (step FC1): levels, XP and pairs are the Wiki's food pages' (read 2026-11-10), whose
         * burn charts give each pair. Swordfish and shark burn less on a range than on a fire; none of the four is on the
         * Lumbridge range's list of foods it helps (the Wiki's Cooking range (Lumbridge Castle) page), so it cooks them as
         * any range does. Tuna burns to burntfish4, the Wiki's Burnt fish (tuna).
         */
        val LOBSTER = Food(377, 379, 381, 40, 1200, 38, 332, 38, 332, CookingLines.COOKED_LOBSTER, CookingLines.BURNT_LOBSTER)
        val TUNA = Food(359, 361, 367, 30, 1000, 58, 372, 58, 372, CookingLines.COOKED_TUNA, CookingLines.BURNT_TUNA)
        val SWORDFISH = Food(
            371, 373, 375, 45, 1400, 18, 292, 30, 310, CookingLines.COOKED_SWORDFISH, CookingLines.BURNT_SWORDFISH,
            rangeLow = 30, rangeHigh = 310,
        )
        val SHARK = Food(
            383, 385, 387, 80, 2100, 1, 202, 1, 232, CookingLines.COOKED_SHARK, CookingLines.BURNT_SHARK,
            rangeLow = 1, rangeHigh = 232,
        )

        /**
         * The big net's fish (step FC2), from the Wiki's Mackerel, Cod and Bass pages (read 2026-11-10): mackerel and
         * cod are on the Lumbridge range's list and burn less there; cod burns less on a range than a fire. Mackerel
         * burns to burntfish3 (Burnt fish (herring)), cod to burntfish2 (Burnt fish (trout)), bass to burntfish4 (Burnt
         * fish (tuna), where the Wiki's Burnt fish (bass) redirects).
         */
        val MACKEREL = Food(353, 355, 357, 10, 600, 98, 452, 108, 472, CookingLines.COOKED_MACKEREL, CookingLines.BURNT_MACKEREL)
        val COD = Food(
            341, 339, 343, 18, 750, 83, 422, 93, 442, CookingLines.COOKED_COD, CookingLines.BURNT_COD, rangeLow = 88, rangeHigh = 432,
        )
        val BASS = Food(363, 365, 367, 43, 1300, 33, 312, 33, 312, CookingLines.COOKED_BASS, CookingLines.BURNT_BASS)

        /**
         * Karambwan (step FC3), from the Wiki's Raw karambwan, Cooked karambwan and Poison karambwan pages (read
         * 2026-11-10): thoroughly from 30 for 190 XP, poorly from 1 for 80, both burning by the one chart, 70/255 on a
         * fire or any range (it stops burning at 99). The Cooked karambwan page notes the game checks only level 1 for
         * the thorough way, the quest asking 30; the 30 stands here, as quests count as done.
         */
        val KARAMBWAN = Food(
            3142, 3144, 3148, 30, 1900, 70, 255, 70, 255, CookingLines.COOKED_KARAMBWAN, CookingLines.BURNT_KARAMBWAN,
        )
        val POISON_KARAMBWAN = Food(
            3142, 3146, 3148, 1, 800, 70, 255, 70, 255, CookingLines.COOKED_POISON_KARAMBWAN, CookingLines.BURNT_KARAMBWAN,
        )

        /**
         * Monkfish and anglerfish (step FC4), from the Wiki's Monkfish and Anglerfish pages (read 2026-11-10): monkfish
         * from 62 for 150 XP, 11/275 on a fire and 13/280 on a range; anglerfish from 84 for 230 XP, 1/200 on a fire and
         * 1/220 on a range. Neither is on the Lumbridge range's list, so it cooks them as any range. Out: monkfish's 1-tick
         * cook without the box (the Wiki's Monkfish recipe note).
         */
        val MONKFISH = Food(
            7944, 7946, 7948, 62, 1500, 11, 275, 13, 280, CookingLines.COOKED_MONKFISH, CookingLines.BURNT_MONKFISH,
            rangeLow = 13, rangeHigh = 280,
        )
        val ANGLERFISH = Food(
            13439, 13441, 13443, 84, 2300, 1, 200, 1, 220, CookingLines.COOKED_ANGLERFISH, CookingLines.BURNT_ANGLERFISH,
            rangeLow = 1, rangeHigh = 220,
        )

        /**
         * Pies, pizza, cake and pitta bread (step FC5a), baked on a range only, from the Wiki's food pages (read
         * 2026-11-10), whose burn charts give each range pair. Redberry, meat, botanical and mushroom pies burn less on
         * the Lumbridge range (its page's list and each pie's chart); the rest cook there as on any range. Every pie burns
         * to the one burnt pie; a cake gives its tin back, cooked or burnt (the Uncooked cake page). Out: the pies' 1-tick
         * first bake with no box, the Lunar Bake Pie spell, the Hosidius range.
         */
        private fun bake(raw: Int, cooked: Int, burnt: Int, level: Int, xpTenths: Int, low: Int, high: Int, line: Line, burntLine: Line,
                         lumbridgeLow: Int = low, lumbridgeHigh: Int = high, returns: Int = -1) =
            Food(raw, cooked, burnt, level, xpTenths, low, high, lumbridgeLow, lumbridgeHigh, line, burntLine, rangeOnly = true, returns = returns)

        const val BURNT_PIE = 2329
        val REDBERRY_PIE = bake(2321, 2325, BURNT_PIE, 10, 780, 98, 452, CookingLines.COOKED_PIE, CookingLines.BURNT_PIE, 108, 462)
        val MEAT_PIE = bake(2319, 2327, BURNT_PIE, 20, 1100, 78, 412, CookingLines.COOKED_PIE, CookingLines.BURNT_PIE, 88, 432)
        val MUD_PIE = bake(7168, 7170, BURNT_PIE, 29, 1280, 58, 372, CookingLines.COOKED_PIE, CookingLines.BURNT_PIE)
        val APPLE_PIE = bake(2317, 2323, BURNT_PIE, 30, 1300, 58, 372, CookingLines.COOKED_PIE, CookingLines.BURNT_PIE)
        val GARDEN_PIE = bake(7176, 7178, BURNT_PIE, 34, 1380, 48, 352, CookingLines.COOKED_PIE, CookingLines.BURNT_PIE)
        val FISH_PIE = bake(7186, 7188, BURNT_PIE, 47, 1640, 38, 332, CookingLines.COOKED_PIE, CookingLines.BURNT_PIE)
        val BOTANICAL_PIE = bake(19656, 19662, BURNT_PIE, 52, 1800, 20, 300, CookingLines.COOKED_PIE, CookingLines.BURNT_PIE, 108, 462)
        val MUSHROOM_PIE = bake(21684, 21690, BURNT_PIE, 60, 2000, 17, 285, CookingLines.COOKED_PIE, CookingLines.BURNT_PIE, 140, 450)
        val ADMIRAL_PIE = bake(7196, 7198, BURNT_PIE, 70, 2100, 15, 270, CookingLines.COOKED_PIE, CookingLines.BURNT_PIE)
        val DRAGONFRUIT_PIE = bake(22789, 22795, BURNT_PIE, 73, 2200, 8, 250, CookingLines.COOKED_PIE, CookingLines.BURNT_PIE)
        val WILD_PIE = bake(7206, 7208, BURNT_PIE, 85, 2400, 1, 222, CookingLines.COOKED_PIE, CookingLines.BURNT_PIE)
        val SUMMER_PIE = bake(7216, 7218, BURNT_PIE, 95, 2600, 1, 212, CookingLines.COOKED_PIE, CookingLines.BURNT_PIE)
        val PIZZA = bake(2287, 2289, 2305, 35, 1430, 48, 352, CookingLines.COOKED_PIZZA, CookingLines.BURNT_PIZZA)
        const val CAKE_TIN = 1887
        val CAKE = bake(1889, 1891, 1903, 40, 1800, 38, 332, CookingLines.COOKED_CAKE, CookingLines.BURNT_CAKE, returns = CAKE_TIN)
        val PITTA = bake(1863, 1865, 1867, 58, 400, 118, 492, CookingLines.COOKED_PITTA, CookingLines.BURNT_PITTA)

        val FOODS = listOf(
            SHRIMPS, ANCHOVIES, SARDINE, HERRING, TROUT, PIKE, SALMON, BEEF, RAT_MEAT, BEAR_MEAT, CHICKEN, BREAD,
            LOBSTER, TUNA, SWORDFISH, SHARK, MACKEREL, COD, BASS, KARAMBWAN, POISON_KARAMBWAN, MONKFISH, ANGLERFISH,
            REDBERRY_PIE, MEAT_PIE, MUD_PIE, APPLE_PIE, GARDEN_PIE, FISH_PIE, BOTANICAL_PIE, MUSHROOM_PIE, ADMIRAL_PIE,
            DRAGONFRUIT_PIE, WILD_PIE, SUMMER_PIE, PIZZA, CAKE, PITTA,
        )
    }
}
