package osrsps.game.skill

import java.util.WeakHashMap
import osrsps.game.FrameSlot
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/** Smithing's words, in the dialogue file. */
object SmithingLines : Lines("smithing", "Smithing") {
    val SMELT_TITLE = line("smelt_title", "the furnace's Make-X box title over the bars")
    val SMELT_LEVEL = line("smelt_level", "the message when a bar needs a higher level to smelt", "level", "bar")
    val SMELT_NO_ORE = line("smelt_no_ore", "the message when the player hasn't the ore for a bar", "bar")
    val SMELTED = line("smelted", "the message on taking a bar from the furnace", "bar")
    val IMPURE = line("impure", "the message when iron ore fails to become a bar")
    val NO_HAMMER = line("no_hammer", "the message at an anvil with no hammer")
    val NO_BARS = line("no_bars", "the message on Smith at an anvil with no bars")
    val ANVIL_LEVEL = line("anvil_level", "the message when an anvil item needs a higher level", "level", "item")
    val ANVIL_BARS = line("anvil_bars", "the message when an anvil item needs more bars than held", "bars", "bar", "item")
    val SMITHED = line("smithed", "the message on hammering out an item", "bar", "item")
}

/**
 * A bar: smelted at [level] from [ores] (obj id to how many) for [smeltXpTenths], and worth [smithXpTenths] a bar on the
 * anvil. [anvilType] is varbit smithing_bar_type's value for it (0 for the bars the anvil doesn't take); [key] names
 * the metal in messages, as mining's ore keys do.
 */
class Bar(
    val obj: Int,
    val key: String,
    val level: Int,
    val smeltXpTenths: Int,
    val ores: List<Pair<Int, Int>>,
    val smithXpTenths: Int,
    val anvilType: Int,
)

/**
 * Smelting at a furnace (the chatbox's Make-X box over the bars, as cooking's) and smithing at an anvil (interface
 * smithing, 312, which the client lays out itself from varbit smithing_bar_type and its own enums).
 */
class Smithing(private val world: World) {
    private class Job(val bar: Bar, val item: AnvilItem?, var left: Int, val loc: ZoneLoc)

    /** What each player is smelting or smithing. */
    private val jobs = WeakHashMap<Player, Job>()

    /** The anvil and bar each player has the anvil interface open for. */
    private val anvils = WeakHashMap<Player, Pair<ZoneLoc, Bar>>()

    fun register() {
        for (furnace in FURNACES) {
            val option = optionOf(furnace, "Smelt")
            world.handlers.onLoc(furnace, option) { op ->
                if (op.repeat) work(op) else chooseBar(op.loc ?: return@onLoc, option)
            }
            for (ore in BARS.flatMap { bar -> bar.ores.map { it.first } }.toSet()) {
                world.handlers.onObjOnLoc(furnace, ore) { op -> chooseBar(op.loc ?: return@onObjOnLoc, option) }
            }
        }
        for (anvil in ANVILS) {
            val option = optionOf(anvil, "Smith")
            world.handlers.onLoc(anvil, option) { op ->
                if (op.repeat) work(op) else openAnvil(op.loc ?: return@onLoc, null)
            }
            for (bar in BARS.filter { it.anvilType > 0 }) {
                world.handlers.onObjOnLoc(anvil, bar.obj) { op -> openAnvil(op.loc ?: return@onObjOnLoc, bar) }
            }
        }
        for ((component, quantity) in QUANTITY_BUTTONS) {
            world.handlers.onButton(INTERFACE, component) { setQuantity(quantity) }
        }
        for (component in AnvilItems.BY_BAR.values.flatten().map { it.component }.toSet()) {
            world.handlers.onButton(INTERFACE, component) { op -> smith(op.componentId) }
        }
    }

    /** The loc's option called [name], 1 to 5; 1 when the cache doesn't say (tests). */
    private fun optionOf(locId: Int, name: String): Int {
        val ops = world.locs.types[locId]?.ops ?: return 1
        val index = ops.indexOfFirst { it == name }
        return if (index >= 0) index + 1 else 1
    }

    // Smelting.

    /** Smelt, or ore used on a furnace: the bar and how many, from the Make-X box. */
    private suspend fun ScriptContext.chooseBar(loc: ZoneLoc, option: Int) {
        var picked = 0 to 1
        // Guessed: every free-to-play bar shows, whatever the player holds, and the box suggests All.
        world.dialogues.start(this) { picked = makeX(SmithingLines.SMELT_TITLE, BARS.map { it.obj }, MAX_QUANTITY) }
        val bar = BARS[picked.first]
        val text = world.textFor(player)
        if (player.skills.currentLevel(Skill.SMITHING) < bar.level) {
            player.message(text[SmithingLines.SMELT_LEVEL, "level" to bar.level, "bar" to bar.key])
            return
        }
        if (!hasOres(player, bar)) {
            player.message(text[SmithingLines.SMELT_NO_ORE, "bar" to bar.key])
            return
        }
        if (!world.locs.exists(loc)) return
        world.interfaces.closeChatModal(player)
        jobs[player] = Job(bar, null, picked.second, loc)
        // The Wiki's Bronze bar: "Make-X is 4, then 5. A single bar is 6." The 5-tick clock gives 4 then 5.
        if (picked.second == 1) {
            player.actionDelay = tick + SINGLE_BAR_TICKS
            player.anim(FURNACE_ANIM)
        }
        // As cooking's: the first tick runs with the interactions, so a due bar can't be taken twice on one tick.
        world.repeatOp(player, Op(OpTarget.LOC, loc.id, option, loc = loc, repeat = true))
    }

    private fun hasOres(player: Player, bar: Bar): Boolean = bar.ores.all { (ore, count) -> player.inventory.count(ore) >= count }

    private fun ScriptContext.smeltOne(bar: Bar) {
        for ((ore, count) in bar.ores) removeObj(player, ore, count)
        val text = world.textFor(player)
        // The Wiki's Iron bar: half of all iron ore is too impure at a furnace, whatever the level.
        if (bar === IRON && !world.random.chance(1, 2)) {
            player.message(text[SmithingLines.IMPURE])
            return
        }
        player.inventory.add(world.objTypes[bar.obj] ?: error("No obj type ${bar.obj} for a bar"), 1)
        player.skills.addTenths(Skill.SMITHING, bar.smeltXpTenths)
        player.message(text[SmithingLines.SMELTED, "bar" to bar.key])
    }

    // Smithing.

    /** Smith, or a bar used on an anvil: the anvil interface for [used], else for the best bar held. */
    private fun ScriptContext.openAnvil(loc: ZoneLoc, used: Bar?) {
        val text = world.textFor(player)
        val bar = used ?: anvilBar(player)
        if (bar == null) {
            player.message(text[SmithingLines.NO_BARS])
            return
        }
        if (player.inventory.count(HAMMER) < 1) {
            player.message(text[SmithingLines.NO_HAMMER])
            return
        }
        world.interfaces.setVarbitNow(player, VARBIT_BAR_TYPE, bar.anvilType)
        world.interfaces.sendVarpNow(player, VARP_QUANTITY)
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, INTERFACE)
        anvils[player] = loc to bar
    }

    /**
     * Guessed: Smith opens the anvil for the best bar held that the player's level smiths something from, else the
     * lowest bar held.
     */
    private fun anvilBar(player: Player): Bar? {
        val held = BARS.filter { it.anvilType > 0 && player.inventory.count(it.obj) > 0 }
        val level = player.skills.currentLevel(Skill.SMITHING)
        return held.lastOrNull { bar -> AnvilItems.BY_BAR.getValue(bar.obj).any { it.level <= level } } ?: held.firstOrNull()
    }

    /** A quantity button on the anvil: the client has already shown it picked; X asks for the number. */
    private suspend fun ScriptContext.setQuantity(quantity: Int) {
        if (player.modals[FrameSlot.MAINMODAL] != INTERFACE || quantity == QUANTITY_SOME) return
        player.vars[VARP_QUANTITY] = if (quantity == QUANTITY_X) countDialog().coerceIn(0, MAX_QUANTITY) else quantity
    }

    /** An item clicked on the anvil. */
    private fun ScriptContext.smith(component: Int) {
        if (player.modals[FrameSlot.MAINMODAL] != INTERFACE) return
        val (loc, bar) = anvils[player] ?: return
        val item = AnvilItems.BY_BAR.getValue(bar.obj).firstOrNull { it.component == component } ?: return
        val text = world.textFor(player)
        val name = itemName(item)
        if (player.skills.currentLevel(Skill.SMITHING) < item.level) {
            player.message(text[SmithingLines.ANVIL_LEVEL, "level" to item.level, "item" to name])
            return
        }
        if (player.inventory.count(bar.obj) < item.bars) {
            player.message(text[SmithingLines.ANVIL_BARS, "bars" to item.bars, "bar" to bar.key, "item" to name])
            return
        }
        // Guessed: a quantity of 0 (X cancelled) smiths one.
        val quantity = player.vars[VARP_QUANTITY].takeIf { it > 0 } ?: 1
        world.interfaces.closeModals(player)
        anvils.remove(player)
        if (!world.locs.exists(loc)) return
        // The last item made shows highlighted next time, and the space bar makes it again (smithing_item).
        if (player.vars.types.varbit(VARBIT_LAST_BAR) != null) {
            player.vars.setVarbit(VARBIT_LAST_BAR, bar.anvilType)
            player.vars.setVarbit(VARBIT_LAST_ITEM, component - FIRST_ITEM_COMPONENT + 1)
        }
        jobs[player] = Job(bar, item, quantity, loc)
        world.repeatOp(player, Op(OpTarget.LOC, loc.id, optionOf(loc.id, "Smith"), loc = loc, repeat = true))
    }

    private fun itemName(item: AnvilItem): String = world.objTypes[item.obj]?.name?.lowercase() ?: "item"

    private fun ScriptContext.smithOne(bar: Bar, item: AnvilItem) {
        removeObj(player, bar.obj, item.bars)
        player.inventory.add(world.objTypes[item.obj] ?: error("No obj type ${item.obj} on the anvil"), item.count)
        player.skills.addTenths(Skill.SMITHING, bar.smithXpTenths * item.bars)
        player.message(world.textFor(player)[SmithingLines.SMITHED, "bar" to bar.key, "item" to itemName(item)])
    }

    // Both.

    /** One tick of smelting or smithing: the animation as each starts, a bar or item every [CYCLE] ticks, then the re-run. */
    private fun ScriptContext.work(op: Op) {
        val loc = op.loc ?: return
        val job = jobs[player]
        if (job == null || job.loc != loc) return finish()
        // A level-up box opened since the last tick ends it, as for the other skills.
        if (player.modalOpen) return finish()
        if (job.left <= 0 || !canDoOne(job)) return finish()
        val anim = if (job.item == null) FURNACE_ANIM else ANVIL_ANIM
        when (SkillClock.step(player, tick, CYCLE)) {
            ClockStep.STARTED -> player.anim(anim)
            ClockStep.WAITING -> {}
            ClockStep.DUE -> {
                if (job.item == null) smeltOne(job.bar) else smithOne(job.bar, job.item)
                job.left--
                // The next one's animation comes with the clock starting again next tick.
                if (job.left <= 0 || !canDoOne(job)) return finish(stopAnim = false)
            }
        }
        world.repeatOp(player, op)
    }

    private fun ScriptContext.canDoOne(job: Job): Boolean {
        // Guessed: a boost wearing off below the level stops the job without a word.
        val level = player.skills.currentLevel(Skill.SMITHING)
        val item = job.item ?: return level >= job.bar.level && hasOres(player, job.bar)
        return level >= item.level && player.inventory.count(job.bar.obj) >= item.bars && player.inventory.count(HAMMER) > 0
    }

    private fun ScriptContext.finish(stopAnim: Boolean = true) {
        jobs.remove(player)
        if (stopAnim) player.anim(-1)
    }

    private fun removeObj(player: Player, id: Int, count: Int) {
        var left = count
        for ((slot, obj) in player.inventory.contents()) {
            if (left <= 0) break
            if (obj.id != id) continue
            left -= player.inventory.remove(slot, left)
        }
    }

    companion object {
        /** Interface smithing, the anvil. */
        const val INTERFACE = 312

        /** Its item buttons start at dagger, 9; enum 1101 numbers them from 1 for varbit smithing_item_lasttype. */
        const val FIRST_ITEM_COMPONENT = 9

        /** The quantity buttons make_1, make_5, make_10, make_x, make_all and make_some (the X amount). */
        const val QUANTITY_X = 0
        const val QUANTITY_SOME = -1
        val QUANTITY_BUTTONS = mapOf(3 to 1, 4 to 5, 5 to 10, 6 to QUANTITY_X, 7 to MAX_QUANTITY, 8 to QUANTITY_SOME)

        /** All, as the client's skillmain buttons have it. */
        const val MAX_QUANTITY = 28

        /** Varbit smithing_bar_type (varp 210 smithbars), varp makexcrafting, varbits smithing_bar_lasttype and smithing_item_lasttype. */
        const val VARBIT_BAR_TYPE = 3216
        const val VARP_QUANTITY = 2224
        const val VARBIT_LAST_BAR = 13894
        const val VARBIT_LAST_ITEM = 13895

        /** The Wiki: 5 ticks an item on the anvil; smelting 4, then 5 (the clock's first roll comes a tick early). */
        const val CYCLE = 5
        const val SINGLE_BAR_TICKS = 6

        /** human_furnace, human_smithing. */
        const val FURNACE_ANIM = 899
        const val ANVIL_ANIM = 898

        const val HAMMER = 2347

        /**
         * Furnaces by gameval name: fai_falador_furnace (Lumbridge's and Al Kharid's, Smelt is op2), furnace and
         * varrock_diary_furnace (Edgeville's). Anvils: the rusted anvil north of Lumbridge Castle and the plain anvil.
         */
        const val FURNACE = 24009
        val FURNACES = setOf(FURNACE, 2030, 16469)
        const val RUSTED_ANVIL = 39620
        const val ANVIL = 2097
        val ANVILS = setOf(RUSTED_ANVIL, ANVIL)

        /** Ores by gameval name: copper 436, tin 438, iron 440, silver 442, gold 444, mithril 447, adamantite 449, runite 451, coal 453. */
        val BRONZE = Bar(2349, "bronze", 1, 62, listOf(436 to 1, 438 to 1), 125, 1)
        val IRON = Bar(2351, "iron", 15, 125, listOf(440 to 1), 250, 2)
        val SILVER = Bar(2355, "silver", 20, 137, listOf(442 to 1), 0, 0)
        val STEEL = Bar(2353, "steel", 30, 175, listOf(440 to 1, 453 to 2), 375, 3)
        val GOLD = Bar(2357, "gold", 40, 225, listOf(444 to 1), 0, 0)
        val MITHRIL = Bar(2359, "mithril", 50, 300, listOf(447 to 1, 453 to 4), 500, 4)
        val ADAMANTITE = Bar(2361, "adamantite", 70, 375, listOf(449 to 1, 453 to 6), 625, 5)
        val RUNITE = Bar(2363, "runite", 85, 500, listOf(451 to 1, 453 to 8), 750, 6)

        /** Levels and XP from the Wiki's Smelting and Smithing pages, in the furnace box's order. */
        val BARS = listOf(BRONZE, IRON, SILVER, STEEL, GOLD, MITHRIL, ADAMANTITE, RUNITE)
    }
}
