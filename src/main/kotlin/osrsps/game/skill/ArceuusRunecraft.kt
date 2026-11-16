package osrsps.game.skill

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.Op
import osrsps.game.script.ScriptContext

/** Arceuus runecrafting's words, in the dialogue file. */
object ArceuusRunecraftLines : Lines("runecraft_arceuus", "Arceuus runecraft") {
    val CHIP_LEVEL = line("chip_level", "Chip on dense runestone below 38 Mining or 38 Crafting")
    val CHIP_NO_CHISEL = line("chip_no_chisel", "Chip on dense runestone without a chisel")
    val CHIP_NO_PICKAXE = line("chip_no_pickaxe", "Chip on dense runestone without a pickaxe it can use")
    val CHIP_FULL = line("chip_full", "Chip on dense runestone with a full backpack")
    val CHIP_START = line("chip_start", "the first Chip on dense runestone")
    val CHIP_BLOCK = line("chip_block", "a dense essence block chipped off")
    val DEPLETED = line("depleted", "the runestone depleting as a block comes off")
    val CHECK_DEPLETED = line("check_depleted", "Check (or Chip) on a depleted runestone")
    val VENERATE_NOTHING = line("venerate_nothing", "Venerate on the Dark Altar without dense essence blocks")
    val VENERATE = line("venerate", "the Dark Altar turning dense essence blocks into dark essence blocks")
    val CHISEL_LEVEL = line("chisel_level", "a chisel on a dark essence block below 38 Crafting")
    val CHISEL_FULL = line("chisel_full", "a chisel on a dark essence block holding as many fragments as can be held")
    val FRAGMENTS_COUNT = line("fragments_count", "Count on dark essence fragments; {count} is how many", "count")
    val BIND_LEVEL = line("bind_level", "Bind on the Blood or Soul Altar below its level; {level} is it", "level")
    val BIND_NOTHING = line("bind_nothing", "Bind on the Blood or Soul Altar without dark essence fragments")
    val BIND = line("bind", "the Blood or Soul Altar binding fragments into runes; {rune} is the rune obj's name as the cache has it", "rune")
}

/** One of Kourend's altars that bind dark essence fragments: its loc, rune, level and XP in tenths per fragment. */
class FragmentAltar(val key: String, val altar: Int, val rune: Int, val level: Int, val xpTenths: Int)

/**
 * Runecraft, step RC5: Arceuus's dense runestone, the Dark Altar, dark essence fragments and the Blood and Soul Altars.
 *
 * Sources: the OSRS Wiki (read 2026-11-15): Dense runestone (38 Mining and 38 Crafting, a pickaxe and a chisel, one
 * block every 9 ticks whatever the pickaxe, 12 Mining and 8 Crafting XP, a runestone stays with a 210-230 skilling
 * chance, per player), Dense essence block, Dark Altar (Venerate turns every dense block into a dark one, a Prayer point
 * each, down to 0 and still working, 2.5 Runecraft XP each), Dark essence fragments (a chisel on a dark block gives 4,
 * 8 Crafting XP; no more than 108 held), Blood Altar (Kourend) and Soul Altar (Kourend) (Bind: every fragment into a
 * rune, 23.8 XP each at 77, 29.7 at 90, boostable); the 242 cache for the locs (runestones 8981 and 10796, multilocs
 * on varbits arceuus_runestone_1 and _2, 0 mineable and 1 depleted; altars 27978-27980), the objs (13445, 13446, 7938
 * `bigblankrune`, which never stacks) and the animations arceuus_chisel_essence (7201) and _essenceblock (7202). The
 * 242 map for the tiles.
 *
 * Inferred: the fragments are one backpack obj that never stacks while the count is kept by the server (the cache's
 * `stackable=never` against the Wiki's counts); we keep it in [osrsps.game.Charges] (`dark_essence_fragments`), and it
 * is 0 whenever the obj is gone. Guessed: a depleted runestone wakes when the other one depletes (live re-rolls both on
 * re-entry); chiselling does every block that fits in one go, at once; Venerate and Bind on the click's tick (Venerate
 * with no animation, Bind with the Craft-rune animation and spotanim); the chip animation is 7201 and the chisel's 7202;
 * the refusal order and every message's wording. Out: the Kourend & Kebos diary's double blocks and extra blood runes,
 * blood essence, scarred extracts, the agility shortcuts, teleport tablets at the lectern, the Abyss's blood and soul
 * rifts, fragments vanishing on death, reanimation and heads on the Dark Altar.
 */
class ArceuusRunecraft(private val world: World) {
    fun register() {
        for ((loc, varbit) in RUNESTONES) {
            world.handlers.onLoc(loc, CHIP) { op -> chip(op, varbit) }
        }
        world.handlers.onLoc(DARK_ALTAR, VENERATE) { venerate() }
        for (altar in ALTARS) world.handlers.onLoc(altar.altar, BIND) { bind(altar) }
        world.handlers.onObjOnObj(CHISEL, DARK_BLOCK) { chisel(player) }
        world.handlers.onObj(FRAGMENTS, COUNT) {
            player.message(world.textFor(player)[ArceuusRunecraftLines.FRAGMENTS_COUNT, "count" to fragments(player)])
        }
    }

    /** The fragments [player] holds: the saved count while the obj is in the backpack, else none. */
    fun fragments(player: Player): Int = if (player.inventory.count(FRAGMENTS) > 0) player.charges[FRAGMENT_CHARGES] else 0

    /**
     * [obj] left [player]'s backpack by Drop. Guessed: dropped fragments are lost, like a dropped pouch's essence, so the
     * count goes to 0 once no fragments obj is left and an obj picked up again holds none.
     */
    fun dropped(player: Player, obj: Int) {
        if (obj == FRAGMENTS && player.inventory.count(FRAGMENTS) == 0L) player.charges[FRAGMENT_CHARGES] = 0
    }

    private fun setFragments(player: Player, count: Int) {
        val inventory = player.inventory
        val held = inventory.count(FRAGMENTS) > 0
        when {
            count > 0 && !held -> inventory.add(world.objTypes[FRAGMENTS] ?: error("No obj type $FRAGMENTS for fragments"), 1)
            // Every fragments obj goes, so one picked up again after a drop doesn't linger empty.
            count == 0 -> for (slot in inventory.contents().filter { it.second.id == FRAGMENTS }.map { it.first }) inventory.remove(slot, 1)
        }
        player.charges[FRAGMENT_CHARGES] = count
    }

    private suspend fun ScriptContext.chip(op: Op, varbit: Int) {
        if (op.loc == null) return
        if (op.repeat && player.modalOpen) return stop()
        val text = world.textFor(player)
        // Op 1 is Chip on the mineable runestone and Check on the depleted one; the client sends the multiloc either way.
        if (player.vars.getVarbit(varbit) != 0) {
            player.message(text[ArceuusRunecraftLines.CHECK_DEPLETED])
            return stop()
        }
        if (player.skills.currentLevel(Skill.MINING) < LEVEL || player.skills.currentLevel(Skill.CRAFTING) < LEVEL) {
            player.message(text[ArceuusRunecraftLines.CHIP_LEVEL])
            return stop()
        }
        if (player.inventory.count(CHISEL) == 0L) {
            player.message(text[ArceuusRunecraftLines.CHIP_NO_CHISEL])
            return stop()
        }
        if (world.mining.tools.best(player) !is ToolCheck.Found) {
            player.message(text[ArceuusRunecraftLines.CHIP_NO_PICKAXE])
            return stop()
        }
        if (player.inventory.freeSlots == 0) {
            player.message(text[ArceuusRunecraftLines.CHIP_FULL])
            return stop()
        }
        if (!op.repeat) {
            player.message(text[ArceuusRunecraftLines.CHIP_START])
            player.anim(CHIP_ANIM)
        }
        // The click's first block is a whole interval away; after a block the next one is, as in Mining.
        if (SkillClock.step(player, tick, CHIP_TICKS + if (op.repeat) 0 else 1) == ClockStep.DUE) {
            player.anim(CHIP_ANIM)
            player.inventory.add(world.objTypes[DENSE_BLOCK] ?: error("No obj type $DENSE_BLOCK for dense essence"), 1)
            player.skills.addTenths(Skill.MINING, 120)
            player.skills.addTenths(Skill.CRAFTING, 80)
            player.message(text[ArceuusRunecraftLines.CHIP_BLOCK])
            if (!SuccessRoll.roll(world.random, STAY_LOW, STAY_HIGH, player.skills.currentLevel(Skill.MINING))) {
                for ((_, other) in RUNESTONES) player.vars.setVarbit(other, if (other == varbit) 1 else 0)
                player.message(text[ArceuusRunecraftLines.DEPLETED])
                return stop()
            }
        }
        world.repeatOp(player, op)
    }

    private fun ScriptContext.stop() {
        player.anim(-1)
    }

    private suspend fun ScriptContext.venerate() {
        // Inferred: no arriveDelay at the Arceuus altars, as for Craft-rune (the Wiki's Runecraft history, 23 March 2022,
        // removed the delay after using essence on an altar).
        val text = world.textFor(player)
        val inventory = player.inventory
        val slots = inventory.contents().filter { it.second.id == DENSE_BLOCK }.map { it.first }
        if (slots.isEmpty()) return player.message(text[ArceuusRunecraftLines.VENERATE_NOTHING])
        val dark = world.objTypes[DARK_BLOCK] ?: error("No obj type $DARK_BLOCK for dark essence")
        for (slot in slots) inventory[slot] = osrsps.game.InvObj(dark.id, 1)
        val prayer = player.skills.currentLevel(Skill.PRAYER)
        player.skills.setCurrentLevel(Skill.PRAYER, maxOf(0, prayer - slots.size))
        player.skills.addTenths(Skill.RUNECRAFT, VENERATE_XP_TENTHS * slots.size)
        player.message(text[ArceuusRunecraftLines.VENERATE])
    }

    private fun chisel(player: Player) {
        val text = world.textFor(player)
        if (player.skills.currentLevel(Skill.CRAFTING) < LEVEL) return player.message(text[ArceuusRunecraftLines.CHISEL_LEVEL])
        val held = fragments(player)
        val room = (MAX_FRAGMENTS - held) / FRAGMENTS_PER_BLOCK
        val inventory = player.inventory
        val blocks = inventory.contents().filter { it.second.id == DARK_BLOCK }.map { it.first }.take(room)
        if (blocks.isEmpty()) return player.message(text[ArceuusRunecraftLines.CHISEL_FULL])
        for (slot in blocks) inventory.remove(slot, 1)
        setFragments(player, held + blocks.size * FRAGMENTS_PER_BLOCK)
        player.anim(CHISEL_ANIM)
        player.skills.addTenths(Skill.CRAFTING, CHISEL_XP_TENTHS * blocks.size)
    }

    private suspend fun ScriptContext.bind(altar: FragmentAltar) {
        // Inferred: no arriveDelay, as in venerate.
        val text = world.textFor(player)
        if (player.skills.currentLevel(Skill.RUNECRAFT) < altar.level) {
            return player.message(text[ArceuusRunecraftLines.BIND_LEVEL, "level" to altar.level])
        }
        val count = fragments(player)
        if (count == 0) return player.message(text[ArceuusRunecraftLines.BIND_NOTHING])
        val runeType = world.objTypes[altar.rune] ?: error("No obj type ${altar.rune} for ${altar.key} runes")
        if (player.inventory.count(altar.rune) + count > osrsps.game.Inventory.MAX_STACK) {
            return player.message(text[RuneCraftingLines.FULL])
        }
        setFragments(player, 0)
        // The fragments' slot is free now, so the runes have one even in a full backpack.
        player.inventory.add(runeType, count)
        player.anim(RuneCrafting.ANIM)
        player.spotanim(RuneCrafting.SPOTANIM, RuneCrafting.SPOTANIM_HEIGHT)
        player.skills.addTenths(Skill.RUNECRAFT, altar.xpTenths * count)
        player.message(text[ArceuusRunecraftLines.BIND, "rune" to runeType.name])
    }

    companion object {
        const val CHIP = 1
        const val VENERATE = 1
        const val BIND = 1
        const val COUNT = 1

        const val LEVEL = 38
        const val CHIP_TICKS = 9
        const val STAY_LOW = 210
        const val STAY_HIGH = 230
        const val CHIP_ANIM = 7201 // arceuus_chisel_essence
        const val CHISEL_ANIM = 7202 // arceuus_chisel_essenceblock
        const val VENERATE_XP_TENTHS = 25
        const val CHISEL_XP_TENTHS = 80
        const val FRAGMENTS_PER_BLOCK = 4
        const val MAX_FRAGMENTS = 108
        const val FRAGMENT_CHARGES = "dark_essence_fragments"

        const val DENSE_BLOCK = 13445 // arceuus_essence_block
        const val DARK_BLOCK = 13446 // arceuus_essence_block_dark
        const val FRAGMENTS = 7938 // bigblankrune
        const val CHISEL = 1755

        const val DARK_ALTAR = 27979 // archeus_altar_dark
        const val BLOOD_ALTAR = 27978 // archeus_altar_blood
        const val SOUL_ALTAR = 27980 // archeus_altar_soul

        /** The two runestones (the multilocs at 1762,3856 and 1762,3844) and their varbits. */
        val RUNESTONES: List<Pair<Int, Int>> = listOf(
            8981 to 4927, // arceuus_runestone_base_1, arceuus_runestone_1
            10796 to 4928, // arceuus_runestone_base_2, arceuus_runestone_2
        )

        val ALTARS: List<FragmentAltar> = listOf(
            FragmentAltar("blood", BLOOD_ALTAR, 565, 77, 238),
            FragmentAltar("soul", SOUL_ALTAR, 566, 90, 297),
        )
    }
}
