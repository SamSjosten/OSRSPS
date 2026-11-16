package osrsps.game.skill

import osrsps.game.Inventory
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.arriveDelay
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** The Ourania Altar's words, in the dialogue file. */
object OuraniaLines : Lines("runecraft_ourania", "Ourania Altar") {
    val NO_ESSENCE = line("no_essence", "Craft-rune on the Ourania Altar with no pure essence")
    val RUNE_ESSENCE = line("rune_essence", "Craft-rune on the Ourania Altar holding only rune essence")
    val BIND = line("bind", "the Ourania Altar crafting its random runes")
    val FULL = line("full", "Craft-rune when one of the runes made wouldn't fit on its stack")
}

/**
 * Runecraft, step RC6: the Ourania Altar (the ZMI altar) and the way down to it.
 *
 * Sources: the OSRS Wiki's Ourania Altar page and its Module:Ourania Altar Table (read 2026-11-15): pure essence only,
 * no level, one random rune per essence (no multiples) from a table per ten-level band (1-9, 10-19 ... 90-98, 99), a
 * number below 10,000 against the band's thresholds (Mod Ash's own example for level 20 matches them); XP 1.7 times the
 * rune's own per essence. The 242 cache and map: the altar `rc_zmi_dungeon_cracked_center_altar` (29631) at 3059,5578,
 * the ladders `rc_zmi_dungeon_entrance` (29635, 2452,3231) and `_ladder` (29636, 3015,5630), the crack
 * `rc_zmi_dungeon_wall_crack_entrance` (29626, 3053,5587) and its `_exit` (29627, 3055,5586).
 *
 * Guessed: the band follows the current level, so boosts count; the tiles each ladder and the crack put the player on (beside the other end, from the map's collision); no
 * delay or animation on the ladders and the crack; the runes and XP on the click's tick (or the arrival tick: no arrive delay, by the Wiki's 23 March 2022 change) with the Craft-rune animation;
 * Craft-rune refuses when a rune's stack would overflow; every message's wording. Out: Eniola's bank for runes, the
 * cave's monsters on the short path, daeyalt essence, the medium Ardougne diary's extra runes, the Raiments of the
 * Eye, Ourania Teleport (Lunar), the no-trading rule for pure essence in the cave.
 */
class Ourania(private val world: World) {
    fun register() {
        world.handlers.onLoc(ALTAR, CRAFT_RUNE) { craft() }
        world.handlers.onLoc(SURFACE_LADDER, CLIMB) { arriveDelay(); world.teleport(player, CAVE_LANDING_X, CAVE_LANDING_Z, 0) }
        world.handlers.onLoc(CAVE_LADDER, CLIMB) { arriveDelay(); world.teleport(player, SURFACE_LANDING_X, SURFACE_LANDING_Z, 0) }
        world.handlers.onLoc(CRACK, SQUEEZE) { arriveDelay(); world.teleport(player, ALTAR_SIDE_X, ALTAR_SIDE_Z, 0) }
        world.handlers.onLoc(CRACK_EXIT, SQUEEZE) { arriveDelay(); world.teleport(player, PATH_SIDE_X, PATH_SIDE_Z, 0) }
    }

    private suspend fun ScriptContext.craft() {
        // No arriveDelay: the Wiki's Runecraft history (23 March 2022) removed the delay after using essence on an altar.
        val text = world.textFor(player)
        val inventory = player.inventory
        val essence = inventory.count(Mining.PURE_ESSENCE).toInt()
        if (essence == 0) {
            return player.message(text[if (inventory.count(Mining.RUNE_ESSENCE) > 0) OuraniaLines.RUNE_ESSENCE else OuraniaLines.NO_ESSENCE])
        }
        val level = player.skills.currentLevel(Skill.RUNECRAFT)
        val made = IntArray(RUNES.size)
        repeat(essence) { made[RUNES.indexOf(roll(level))]++ }
        val counts = RUNES.withIndex().filter { made[it.index] > 0 }.map { it.value to made[it.index] }
        if (!fits(player, counts)) return player.message(text[OuraniaLines.FULL])
        removeAll(inventory, Mining.PURE_ESSENCE, essence)
        var xpTenths = 0
        for ((rune, count) in counts) {
            inventory.add(world.objTypes[rune.obj] ?: error("No obj type ${rune.obj} for ${rune.key} runes"), count)
            xpTenths += rune.xpTenths * count
        }
        player.anim(RuneCrafting.ANIM)
        player.spotanim(RuneCrafting.SPOTANIM, RuneCrafting.SPOTANIM_HEIGHT)
        player.skills.addTenths(Skill.RUNECRAFT, xpTenths * 17 / 10)
        player.message(text[OuraniaLines.BIND])
    }

    /** One rune for [level] from the band's thresholds. */
    fun roll(level: Int): OuraniaRune {
        val thresholds = BANDS[band(level)]
        val pick = world.random.nextInt(RANGE)
        return RUNES[thresholds.indexOfFirst { pick < it }]
    }

    /** Every stack fits, and the new stacks fit in the slots the essence leaves (it all goes). */
    private fun fits(player: Player, counts: List<Pair<OuraniaRune, Int>>): Boolean {
        val inventory = player.inventory
        if (counts.any { (rune, count) -> inventory.count(rune.obj) + count > Inventory.MAX_STACK }) return false
        val newStacks = counts.count { (rune, _) -> inventory.count(rune.obj) == 0L }
        val essenceSlots = inventory.contents().count { it.second.id == Mining.PURE_ESSENCE }
        return newStacks <= inventory.freeSlots + essenceSlots
    }

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
        const val CLIMB = 1
        const val SQUEEZE = 1
        const val ALTAR = 29631 // rc_zmi_dungeon_cracked_center_altar
        const val SURFACE_LADDER = 29635 // rc_zmi_dungeon_entrance
        const val CAVE_LADDER = 29636 // rc_zmi_dungeon_entrance_ladder
        const val CRACK = 29626 // rc_zmi_dungeon_wall_crack_entrance
        const val CRACK_EXIT = 29627 // rc_zmi_dungeon_wall_crack_entrance_exit

        const val CAVE_LANDING_X = 3016
        const val CAVE_LANDING_Z = 5629
        const val SURFACE_LANDING_X = 2453
        const val SURFACE_LANDING_Z = 3232
        const val ALTAR_SIDE_X = 3057
        const val ALTAR_SIDE_Z = 5587
        const val PATH_SIDE_X = 3052
        const val PATH_SIDE_Z = 5587

        const val RANGE = 10_000

        /** The runes, rarest first as the Wiki's table orders them, with their own XP per essence in tenths. */
        val RUNES: List<OuraniaRune> = listOf(
            OuraniaRune("soul", 566, 110), OuraniaRune("blood", 565, 105), OuraniaRune("death", 560, 100),
            OuraniaRune("law", 563, 95), OuraniaRune("nature", 561, 90), OuraniaRune("astral", 9075, 87),
            OuraniaRune("chaos", 562, 85), OuraniaRune("cosmic", 564, 80), OuraniaRune("body", 559, 75),
            OuraniaRune("fire", 554, 70), OuraniaRune("earth", 557, 65), OuraniaRune("water", 555, 60),
            OuraniaRune("mind", 558, 55), OuraniaRune("air", 556, 50),
        )

        /** Each band's thresholds, in [RUNES]' order: a pick below a threshold (and not an earlier one) gives that rune. */
        val BANDS: List<List<Int>> = listOf(
            listOf(2, 7, 15, 30, 60, 105, 165, 250, 400, 700, 1300, 2500, 5000, 10000),
            listOf(3, 9, 21, 45, 85, 145, 225, 400, 1000, 2200, 4600, 6700, 8500, 10000),
            listOf(8, 23, 55, 110, 220, 430, 850, 1650, 3250, 4750, 6150, 7500, 8800, 10000),
            listOf(20, 60, 120, 250, 500, 1000, 2000, 4000, 5300, 6500, 7600, 8500, 9300, 10000),
            listOf(40, 120, 240, 500, 1000, 2000, 4000, 5500, 6500, 7300, 8050, 8750, 9400, 10000),
            listOf(80, 250, 600, 1300, 2650, 4150, 5250, 6250, 7000, 7700, 8350, 8950, 9500, 10000),
            listOf(100, 300, 700, 1500, 3050, 4450, 5500, 6450, 7200, 7900, 8500, 9050, 9550, 10000),
            listOf(200, 700, 1700, 3500, 5000, 6200, 7100, 7800, 8300, 8700, 9100, 9400, 9700, 10000),
            listOf(400, 1000, 2450, 3900, 5250, 6300, 7100, 7800, 8400, 8900, 9300, 9600, 9800, 10000),
            listOf(650, 1650, 3300, 4750, 6100, 7100, 7800, 8400, 8900, 9300, 9600, 9800, 9900, 10000),
            listOf(900, 2200, 3750, 5200, 6550, 7500, 8100, 8600, 9000, 9300, 9600, 9800, 9900, 10000),
        )

        /** The band for [level]: 0 for 1-9, 1 for 10-19 ... 9 for 90-98, 10 for 99 and up (boosted). */
        fun band(level: Int): Int = if (level >= 99) 10 else (level / 10).coerceIn(0, 9)
    }
}

/** A rune the Ourania Altar can make: its obj and its own altar's XP per essence in tenths. */
class OuraniaRune(val key: String, val obj: Int, val xpTenths: Int)
