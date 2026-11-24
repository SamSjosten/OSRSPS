package osrsps.game.skill

import osrsps.game.Inventory
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** Combination runes' and the binding necklace's words, in the dialogue file. */
object CombinationRuneLines : Lines("runecraft_combo", "Combination rune") {
    val BIND = line("bind", "the message on crafting combination runes; {rune} is the rune obj's name as the cache has it, Air rune for example", "rune")
    val NO_TALISMAN = line("no_talisman", "runes used on an altar without the talisman for them")
    val NO_ESSENCE = line("no_essence", "runes or a talisman used on an altar without pure essence")
    val NO_RUNES = line("no_runes", "a talisman used on an altar without the runes for it")
    val LEVEL = line("level", "below the combination rune's level; {level} is it", "level")
    val NECKLACE_CHECK = line("necklace_check", "a binding necklace's Check; {charges} is the uses left", "charges")
    val NECKLACE_CRUMBLES = line("necklace_crumbles", "a binding necklace's last use: it crumbles")
}

/**
 * One combination rune made at [altar] from the other element's runes ([input], with its talisman [talisman]): the
 * rune, its level and XP in tenths for each rune made there.
 */
class ComboRecipe(val key: String, val altar: Int, val input: Int, val talisman: Int, val rune: Int, val level: Int, val xpTenths: Int)

/**
 * Runecraft, step RC3: combination runes and the binding necklace.
 *
 * Sources: the OSRS Wiki's Runecraft page (Combination runes, read 2026-11-15) and each elemental altar's page: the
 * runes of one half, their talisman (not a tiara) and as much pure essence, used on the other half's altar (the runes
 * or the talisman used on it), make one combination rune for each pair at 50% each; failures are lost, the talisman
 * is used up whatever the outcome, and XP is per rune made, lower at the lower-levelled altar (mist 8 at air and 8.5 at
 * water, dust 8.3 and 9, mud 9.3 and 9.5, smoke 8.5 and 9.5, steam 9.3 and 10, lava 10 and 10.5). Binding necklace:
 * 100% success, 16 uses, one a click, kept by the player and not the necklace (Binding necklace).
 *
 * The necklace's uses are kept in [osrsps.game.Charges] as the uses spent (`binding_necklace`), so a fresh necklace
 * after one crumbles starts at 16. Inferred: no multiples (the Wiki's Multiple runes table has no combination rows);
 * the level is the current level. Guessed: everything happens on the click's tick (or the arrival tick, with no arrive delay) with the Craft-rune animation and
 * spotanim; the order of the refusals; each message's wording. Out: Magic Imbue (the Lunar spellbook), the necklace's
 * Destroy, aether runes (Magic Imbue and the soul altar).
 */
class CombinationRunes(private val world: World) {
    fun register() {
        for (recipe in RECIPES) {
            for (obj in listOf(recipe.input, recipe.talisman)) {
                world.handlers.onObjOnLoc(recipe.altar, obj) { combine(recipe) }
            }
        }
        world.handlers.onObj(BINDING_NECKLACE, CHECK) { check(player) }
        world.handlers.onWorn(BINDING_NECKLACE, WORN_CHECK) { check(player) }
    }

    private fun check(player: Player) {
        player.message(world.textFor(player)[CombinationRuneLines.NECKLACE_CHECK, "charges" to NECKLACE_USES - player.charges[CHARGES]])
    }

    private suspend fun ScriptContext.combine(recipe: ComboRecipe) {
        // Inferred: no arriveDelay, as for Craft-rune (the Wiki's Runecraft history, 23 March 2022, removed the delay after
        // using essence on an altar).
        val text = world.textFor(player)
        val inventory = player.inventory
        if (player.skills.currentLevel(Skill.RUNECRAFT) < recipe.level) return player.message(text[CombinationRuneLines.LEVEL, "level" to recipe.level])
        if (inventory.count(recipe.talisman) == 0L) return player.message(text[CombinationRuneLines.NO_TALISMAN])
        val essence = inventory.count(Mining.PURE_ESSENCE)
        if (essence == 0L) return player.message(text[CombinationRuneLines.NO_ESSENCE])
        val runes = inventory.count(recipe.input)
        if (runes == 0L) return player.message(text[CombinationRuneLines.NO_RUNES])
        // Pure essence never stacks, so a backpack holds at most 28 pairs: the rolls below and the XP stay small.
        val pairs = minOf(essence, runes, Inventory.INV_CAPACITY.toLong()).toInt()
        val necklace = player.equipment[WearPos.NECK]?.id == BINDING_NECKLACE
        val made = if (necklace) pairs else (0 until pairs).count { world.random.chance(1, 2) }
        val runeType = world.objTypes[recipe.rune] ?: error("No obj type ${recipe.rune} for ${recipe.key} runes")
        if (inventory.count(recipe.rune) + made > Inventory.MAX_STACK) return player.message(text[RuneCraftingLines.FULL])
        removeCount(inventory, Mining.PURE_ESSENCE, pairs)
        removeCount(inventory, recipe.input, pairs)
        removeCount(inventory, recipe.talisman, 1)
        if (made > 0) inventory.add(runeType, made)
        if (made > 0) world.diaries.made(player, recipe.rune, made)
        player.anim(RuneCrafting.ANIM)
        player.spotanim(RuneCrafting.SPOTANIM, RuneCrafting.SPOTANIM_HEIGHT)
        if (made > 0) player.skills.addTenths(Skill.RUNECRAFT, recipe.xpTenths * made)
        player.message(text[CombinationRuneLines.BIND, "rune" to runeType.name])
        if (necklace) useNecklace(player)
    }

    /** One use of the worn binding necklace; the last crumbles it, and the next starts at 16. */
    private fun useNecklace(player: Player) {
        val used = player.charges[CHARGES] + 1
        if (used < NECKLACE_USES) {
            player.charges[CHARGES] = used
            return
        }
        player.charges[CHARGES] = 0
        player.equipment[WearPos.NECK] = null
        player.message(world.textFor(player)[CombinationRuneLines.NECKLACE_CRUMBLES])
    }

    /** Takes [count] of [id] from the inventory's slots, first slot first. */
    private fun removeCount(inventory: Inventory, id: Int, count: Int) {
        var left = count
        for ((slot, obj) in inventory.contents()) {
            if (left == 0) break
            if (obj.id != id) continue
            left -= inventory.remove(slot, left)
        }
    }

    companion object {
        const val BINDING_NECKLACE = 5521 // magic_emerald_necklace
        const val CHECK = 3
        const val WORN_CHECK = 1
        const val NECKLACE_USES = 16
        const val CHARGES = "binding_necklace"

        private fun altar(key: String) = Runecraft.ALTARS.first { it.key == key }

        private fun recipe(key: String, at: String, from: String, fromRune: Int, rune: Int, level: Int, xpTenths: Int) =
            ComboRecipe(key, altar(at).altar, fromRune, altar(from).talisman, rune, level, xpTenths)

        private const val AIR = 556
        private const val WATER = 555
        private const val EARTH = 557
        private const val FIRE = 554

        /** Each rune at both its altars, the lower-levelled first. */
        val RECIPES: List<ComboRecipe> = listOf(
            recipe("mist", "air", "water", WATER, 4695, 6, 80),
            recipe("mist", "water", "air", AIR, 4695, 6, 85),
            recipe("dust", "air", "earth", EARTH, 4696, 10, 83),
            recipe("dust", "earth", "air", AIR, 4696, 10, 90),
            recipe("mud", "water", "earth", EARTH, 4698, 13, 93),
            recipe("mud", "earth", "water", WATER, 4698, 13, 95),
            recipe("smoke", "air", "fire", FIRE, 4697, 15, 85),
            recipe("smoke", "fire", "air", AIR, 4697, 15, 95),
            recipe("steam", "water", "fire", FIRE, 4694, 19, 93),
            recipe("steam", "fire", "water", WATER, 4694, 19, 100),
            recipe("lava", "earth", "fire", FIRE, 4699, 23, 100),
            recipe("lava", "fire", "earth", EARTH, 4699, 23, 105),
        )
    }
}
