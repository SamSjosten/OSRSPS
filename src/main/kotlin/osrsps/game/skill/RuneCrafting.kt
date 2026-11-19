package osrsps.game.skill

import osrsps.game.Inventory
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.arriveDelay
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** Crafting runes' words, in the dialogue file. */
object RuneCraftingLines : Lines("runecraft_craft", "Runecraft") {
    val BIND = line("bind", "the message on crafting runes at an altar; {rune} is the rune obj's name as the cache has it, Air rune for example", "rune")
    val NO_ESSENCE = line("no_essence", "Craft-rune with no essence the altar takes")
    val PURE_ONLY = line("pure_only", "Craft-rune holding only rune essence at an altar that takes pure essence alone")
    val LEVEL = line("level", "Craft-rune below the rune's level; {level} is it", "level")
    val TIARA = line("tiara", "the message on binding a talisman into a tiara at its altar")
    val TIARA_NEEDS_TALISMAN = line("tiara_needs_talisman", "a tiara used on an altar without the talisman for it")
    val TIARA_NEEDS_TIARA = line("tiara_needs_tiara", "a talisman used on its altar without a plain (or gold) tiara")
    val FULL = line("full", "Craft-rune when the runes made wouldn't fit on the backpack's stack")
}

/**
 * One rune an altar crafts: the altar's loc, the rune obj, the level, XP in tenths per essence, the levels at which each
 * essence makes one more rune, and whether it takes pure essence alone.
 */
class RuneRecipe(
    val key: String,
    val altar: Int,
    val rune: Int,
    val level: Int,
    val xpTenths: Int,
    val multiples: List<Int>,
    val pureOnly: Boolean,
) {
    /** Runes each essence makes at [level]: one, and one more for each multiple level reached. */
    fun perEssence(level: Int): Int = 1 + multiples.count { level >= it }
}

/** Binding a talisman into a tiara at its altar: the talisman, the tiara it's bound into, its blank and XP in tenths. */
class TiaraRecipe(val altar: Int, val talisman: Int, val blank: Int, val tiara: Int, val xpTenths: Int)

/**
 * Runecraft, step RC2: crafting runes at the altars, and binding tiaras.
 *
 * Sources: the OSRS Wiki's Runecraft page (read 2026-11-15) for every rune's level, XP and essence, and its Multiple
 * runes table for the levels at which each essence makes more runes; each tiara's page for its XP (air 25 to blood and
 * wrath 52.5; elemental 40 and catalytic 60, on a gold tiara); the Tiara page for binding (the tiara or the talisman
 * used on the altar, both held, the talisman used up); the 242 cache for `human_runecraft` (791) and the `runecrafting`
 * spotanim (186).
 *
 * - Craft-rune turns every essence the altar takes into runes at once: rune essence or pure essence at the air, mind,
 *   water, earth, fire and body altars, pure essence alone at the rest. XP is per essence; the runes per essence are by
 *   the current level (boosts count, as the Wiki's note on earth runes at 104 says), and so is the level check.
 * - Rune essence is used before pure essence at the six altars that take both (inferred: both go at once on live, so
 *   which goes first only matters for the count, which is the same).
 * - The Astral Altar on the Lunar Isle stands in the open: Craft-rune works on it with no ruins (reach it with
 *   ::tele 2156 3864). No quests: Lunar Diplomacy counts as done.
 *
 * Guessed: the runes and XP arrive on the click's tick, or the arrival tick when the player walked (no arrive delay, by
 * the Wiki's 23 March 2022 change), with the animation and spotanim (height 100); binding a tiara waits [arriveDelay]; every message's wording is the Words thread's; Craft-rune refuses when the runes wouldn't
 * fit on the stack, rather than losing them. Out: daeyalt essence, the Raiments of the Eye, the abyssal lantern and
 * runic extracts; sunfire runes (the Shrine of Ralos, Varlamore); runecrafting cores (Guardians of the Rift).
 */
class RuneCrafting(private val world: World) {
    fun register() {
        for (recipe in RECIPES) {
            world.handlers.onLoc(recipe.altar, CRAFT_RUNE) { craft(recipe) }
        }
        for (tiara in TIARAS) {
            for (obj in listOf(tiara.talisman, tiara.blank)) {
                world.handlers.onObjOnLoc(tiara.altar, obj) { bindTiara(tiara) }
            }
        }
    }

    private suspend fun ScriptContext.craft(recipe: RuneRecipe) {
        // No arriveDelay: the Wiki's Runecraft history (23 March 2022) says the delay after using essence on an altar was removed.
        val text = world.textFor(player)
        val inventory = player.inventory
        val rune = inventory.count(Mining.RUNE_ESSENCE).takeIf { !recipe.pureOnly } ?: 0L
        val pure = inventory.count(Mining.PURE_ESSENCE)
        if (rune + pure == 0L) {
            return player.message(text[if (recipe.pureOnly && inventory.count(Mining.RUNE_ESSENCE) > 0) RuneCraftingLines.PURE_ONLY else RuneCraftingLines.NO_ESSENCE])
        }
        val level = player.skills.currentLevel(Skill.RUNECRAFT)
        if (level < recipe.level) return player.message(text[RuneCraftingLines.LEVEL, "level" to recipe.level])
        val essence = (rune + pure).toInt()
        val made = essence.toLong() * recipe.perEssence(level)
        val runeType = world.objTypes[recipe.rune] ?: error("No obj type ${recipe.rune} for ${recipe.key} runes")
        if (inventory.count(recipe.rune) + made > Inventory.MAX_STACK) return player.message(text[RuneCraftingLines.FULL])
        removeAll(inventory, Mining.RUNE_ESSENCE, rune.toInt())
        removeAll(inventory, Mining.PURE_ESSENCE, pure.toInt())
        // Every essence has left the backpack, so the stack has a slot even when it was full.
        inventory.add(runeType, made.toInt())
        world.diaries.made(player, recipe.rune, made.toInt())
        player.anim(ANIM)
        player.spotanim(SPOTANIM, SPOTANIM_HEIGHT)
        player.skills.addTenths(Skill.RUNECRAFT, recipe.xpTenths * essence)
        player.message(text[RuneCraftingLines.BIND, "rune" to runeType.name])
    }

    /**
     * Binds [recipe]: the item used on the altar picks it, so a gold tiara or an elemental talisman makes the elemental
     * tiara even when a plain tiara and the altar's own talisman are held too.
     */
    private suspend fun ScriptContext.bindTiara(recipe: TiaraRecipe) {
        arriveDelay()
        val text = world.textFor(player)
        val inventory = player.inventory
        if (inventory.count(recipe.talisman) == 0L || inventory.count(recipe.blank) == 0L) {
            return player.message(text[if (inventory.count(recipe.blank) > 0) RuneCraftingLines.TIARA_NEEDS_TALISMAN else RuneCraftingLines.TIARA_NEEDS_TIARA])
        }
        val tiaraType = world.objTypes[recipe.tiara] ?: error("No obj type ${recipe.tiara} for a tiara")
        removeAll(inventory, recipe.talisman, 1)
        val blankSlot = inventory.contents().first { it.second.id == recipe.blank }.first
        inventory.remove(blankSlot, 1)
        inventory.add(tiaraType, 1)
        player.anim(ANIM)
        player.spotanim(SPOTANIM, SPOTANIM_HEIGHT)
        player.skills.addTenths(Skill.RUNECRAFT, recipe.xpTenths)
        player.message(text[RuneCraftingLines.TIARA])
    }

    /** Takes [count] of [id] from the inventory's slots, first slot first. */
    private fun removeAll(inventory: Inventory, id: Int, count: Int) {
        var left = count
        for ((slot, obj) in inventory.contents()) {
            if (left == 0) break
            if (obj.id != id) continue
            left -= inventory.remove(slot, left)
        }
    }

    companion object {
        const val CRAFT_RUNE = 1
        const val ANIM = 791 // human_runecraft
        const val SPOTANIM = 186 // runecrafting
        const val SPOTANIM_HEIGHT = 100
        const val ASTRAL_ALTAR = 34771 // astral_altar
        const val TIARA = 5525 // tiara
        const val GOLD_TIARA = 26788 // tiara_gold

        private fun altarOf(key: String) = Runecraft.ALTARS.first { it.key == key }.altar

        /** Lowest level first, as the Wiki's Runecraft table lists them. */
        val RECIPES: List<RuneRecipe> = listOf(
            RuneRecipe("air", altarOf("air"), 556, 1, 50, listOf(11, 22, 33, 44, 55, 66, 77, 88, 99), pureOnly = false),
            RuneRecipe("mind", altarOf("mind"), 558, 2, 55, listOf(14, 28, 42, 56, 70, 84, 98), pureOnly = false),
            RuneRecipe("water", altarOf("water"), 555, 5, 60, listOf(19, 38, 57, 76, 95), pureOnly = false),
            RuneRecipe("earth", altarOf("earth"), 557, 9, 65, listOf(26, 52, 78, 104), pureOnly = false),
            RuneRecipe("fire", altarOf("fire"), 554, 14, 70, listOf(35, 70), pureOnly = false),
            RuneRecipe("body", altarOf("body"), 559, 20, 75, listOf(46, 92), pureOnly = false),
            RuneRecipe("cosmic", altarOf("cosmic"), 564, 27, 80, listOf(59), pureOnly = true),
            RuneRecipe("chaos", altarOf("chaos"), 562, 35, 85, listOf(74), pureOnly = true),
            RuneRecipe("astral", ASTRAL_ALTAR, 9075, 40, 87, listOf(82), pureOnly = true),
            RuneRecipe("nature", altarOf("nature"), 561, 44, 90, listOf(91), pureOnly = true),
            RuneRecipe("law", altarOf("law"), 563, 54, 95, listOf(95), pureOnly = true),
            RuneRecipe("death", altarOf("death"), 560, 65, 100, listOf(99), pureOnly = true),
            RuneRecipe("blood", altarOf("blood"), 565, 77, 105, emptyList(), pureOnly = true),
            RuneRecipe("wrath", altarOf("wrath"), 21880, 95, 80, emptyList(), pureOnly = true),
        )

        /** XP in tenths for each altar's own tiara (the tiaras' pages). */
        private val TIARA_XP = mapOf(
            "air" to 250, "mind" to 275, "water" to 300, "earth" to 325, "fire" to 350, "body" to 375, "cosmic" to 400,
            "chaos" to 425, "nature" to 450, "law" to 475, "death" to 500, "blood" to 525, "wrath" to 525,
        )

        /** Every altar's own tiara on a plain tiara, then the elemental and catalytic tiaras on gold ones at theirs. */
        val TIARAS: List<TiaraRecipe> = Runecraft.ALTARS.map { TiaraRecipe(it.altar, it.talisman, TIARA, it.tiara, TIARA_XP.getValue(it.key)) } +
            Runecraft.ALTARS.map {
                if (it.elemental) {
                    TiaraRecipe(it.altar, Runecraft.ELEMENTAL_TALISMAN, GOLD_TIARA, Runecraft.ELEMENTAL_TIARA, 400)
                } else {
                    TiaraRecipe(it.altar, Runecraft.CATALYTIC_TALISMAN, GOLD_TIARA, Runecraft.CATALYTIC_TIARA, 600)
                }
            }
    }
}
