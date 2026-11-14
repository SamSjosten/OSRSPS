package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

/**
 * The six cleaning-area archaeologists' lines, the same for each (Transcript:Barnabus Hurma, Marius Giste, Caden Azro,
 * Thias Leacke, Sinco Doar and Tinse Torpe, read 2026-11-14).
 */
object MuseumArchaeologistLines : Lines("museum_archaeologist", "Transcript:Barnabus Hurma") {
    val GREETINGS = (1..3).map { line("greeting_$it", "his greeting, dialogue $it of the three he picks from at random") }
    val HELP = line("help", "the menu's option 1, which the player then says")
    val NEED_EQUIPMENT_1 = line("need_equipment_1", "option 1 without the tools carried and the gloves and boots worn: the first of two boxes")
    val NEED_EQUIPMENT_2 = line("need_equipment_2", "the same: the second box")
    val HAVE_EQUIPMENT = line("have_equipment", "option 1 with the tools and gear: his first line")
    val CLEAN_SOME_1 = line("clean_some_1", "option 1 with the tools and gear: the first of two boxes after it")
    val CLEAN_SOME_2 = line("clean_some_2", "the same: the second box")
    val INTERESTING = line("interesting", "the menu's option 2, which the player then says")
    val LETS_LOOK = line("lets_look", "option 2: his answer, before the find")
    val NO_THANKS = line("no_thanks", "the menu's option 3")
    val NO_THANKS_SAID = line("no_thanks_said", "option 3: what the player says (not the option's words)")
    val TAKE_A_LOOK = line("take_a_look", "an item used on him: what the player says first")
    val DONT_NEED = line("dont_need", "any other item used on him: his answer")
    val ALREADY = line("already", "a display case's find shown again once it is on display: his answer")

    val NECKLACE = (1..6).map { line("necklace_$it", "the clean necklace: box $it of six, the player's and his in turn (the player's first)") }
    val NECKLACE_TAKES = line("necklace_takes", "the clean necklace: the item box as he takes it")
    val NECKLACE_SHOWS_1 = line("necklace_shows_1", "the clean necklace: the item box as he shows the spell, the first of two")
    val NECKLACE_SHOWS_2 = line("necklace_shows_2", "the second item box, ending in the red 'You can make enchanted ruby necklaces.'")
    val NECKLACE_RECAP = line("necklace_recap", "the clean necklace: the player's recap after the boxes")
    val NECKLACE_WELL_DONE = line("necklace_well_done", "the clean necklace: his last line")

    val ARROWHEADS = line("arrowheads", "arrowheads: what the player says")
    val ARROWHEADS_1 = line("arrowheads_1", "arrowheads: his answer, the first of two boxes")
    val ARROWHEADS_2 = line("arrowheads_2", "arrowheads: the second box")
    val ARROWHEADS_OK = line("arrowheads_ok", "arrowheads: the player's last line")
    val JEWELLERY = line("jewellery", "jewellery: what the player says after 'Take a look at this...'")
    val JEWELLERY_1 = line("jewellery_1", "jewellery: his answer")
    val JEWELLERY_OK = line("jewellery_ok", "jewellery: the player's answer")
    val JEWELLERY_COINS = line("jewellery_coins", "jewellery: the item box as he hands over 5 coins")
    val VASE = line("vase", "the old chipped vase: what the player says")
    val VASE_1 = line("vase_1", "the vase: his answer, the first of two boxes")
    val VASE_2 = line("vase_2", "the vase: the second box")
    val VASE_OK = line("vase_ok", "the vase: the player's last line")
    val LOST_IT = line("lost_it", "option 2 with no find carried: the player's line")
    val NEVER_HAD = line("never_had", "option 2 with no find carried: his answer")
}

/** What each display case's find says: shown the first time ([first], the player's and his in turn), sent back to the case, and after. */
class CaseFindLines(val said: Line, val first: List<Line>, val reply: Line, val backAgain: Line, val goNow: Line)

/** The five display-case finds' lines, from the same transcripts. */
object MuseumCaseFindLines : Lines("museum_case_find", "Transcript:Barnabus Hurma") {
    private fun find(name: String, what: String, answers: Int): CaseFindLines = CaseFindLines(
        line(name, "$what: what the player says, the first time and once it's on display"),
        (1..answers).map { line("${name}_$it", "$what, shown the first time: his answer, box $it of $answers") },
        line("${name}_reply", "$what, shown the first time: the player's last line, naming the case"),
        line("${name}_back_again", "$what, shown again before it's in its case: his question"),
        line("${name}_go_now", "$what, shown again before it's in its case: the player's answer"),
    )

    val OLD_COIN = find("old_coin", "the old coin", 2)
    val ANCIENT_COIN = find("ancient_coin", "the ancient coin", 5)
    val ANCIENT_SYMBOL = find("ancient_symbol", "the ancient symbol", 1)
    val OLD_SYMBOL = find("old_symbol", "the old symbol", 2)
    val POTTERY = find("pottery", "pottery", 2)
}

/** The display cases' messages: none is on the Wiki, so all are guessed (the Wiki says pottery's case gives none). */
object MuseumCaseLines : Lines("museum_case", "Kudos") {
    val PLACED = line("placed", "OUR OWN: a find put in its display case: {item} (not pottery)", "item")
    val NOT_SHOWN = line("not_shown", "OUR OWN: a find used on its case before an archaeologist sent the player there")
    val FULL = line("full", "OUR OWN: a find used on its case once it is already on display")
}

/**
 * The Dig Site exhibit's finds (Kudos step 1): the six archaeologists in the cleaning area (`vm_archaeologist1_npc_dummy`
 * to `6`, 1903 to 1908, standing at their cleaning tables) look at what the player found, by talk or by the item used on
 * them, and send the five that belong on display to their cases; used on its case, a find goes on display for 10 Kudos
 * ([Kudos]). The clean necklace teaches the Digsite pendant's enchantment (`vm_necklace`).
 *
 * From the archaeologists' transcripts (read 2026-11-14), the Wiki's Kudos, Uncleaned find, Clean necklace, Old symbol,
 * Old coin, Pottery and display case pages, and the 242 cache: each case is a multiloc on its find's varbit, showing the
 * find once it is set (12137 old symbol, case 36; 12138 ancient symbol, 37; 12234 ancient coin, 44; 15484 old coin, 45;
 * 12139 pottery, 22). Putting a symbol, a coin or the necklace away takes every copy from the backpack and the bank, as
 * the Wiki has it; pottery takes the one used, and still turns up when cleaning.
 *
 * Inferred: the archaeologist's "Back again?" means he remembers sending the player to a case, kept here in the saved
 * charges as [TOLD]; a case takes its find only after that. Guessed: every expression, the cases' messages
 * ([MuseumCaseLines]), the necklace's last two lines coming after its item boxes, a second necklace shown after the
 * first going the same way, and "Take a look at this..." standing in for the jewellery's own first line when the
 * jewellery is used on him. Left out: Study on the cases (their interface, `vm_digsite` 528), and the Digsite pendant's
 * enchantment itself (no Lvl-3 Enchant on jewellery yet).
 */
object MuseumFinds {
    val ARCHAEOLOGISTS = (1903..1908).toList()
    const val TALK_TO = 1

    /** Saved charges: a bit per [CASES] index, set once an archaeologist has sent the player to that find's case. */
    const val TOLD = "museum_case_told"

    const val COINS = 995

    /** A display case: its loc, its find, the varbit that shows the find in it, and the find's lines. */
    class Case(val loc: Int, val obj: Int, val varbit: Int, val lines: CaseFindLines, val takesAll: Boolean = true)

    /** In the order the archaeologist looks for them after the necklace (the transcript's). */
    val CASES = listOf(
        Case(15484, MuseumCleaning.OLD_COIN, Kudos.VARBIT_OLD_COIN, MuseumCaseFindLines.OLD_COIN),
        Case(12234, MuseumCleaning.ANCIENT_COIN, Kudos.VARBIT_ANCIENT_COIN, MuseumCaseFindLines.ANCIENT_COIN),
        Case(12138, MuseumCleaning.ANCIENT_SYMBOL, Kudos.VARBIT_ANCIENT_SYMBOL, MuseumCaseFindLines.ANCIENT_SYMBOL),
        Case(12137, MuseumCleaning.OLD_SYMBOL, Kudos.VARBIT_OLD_SYMBOL, MuseumCaseFindLines.OLD_SYMBOL),
        Case(12139, MuseumCleaning.POTTERY, Kudos.VARBIT_POTTERY, MuseumCaseFindLines.POTTERY, takesAll = false),
    )

    fun displayed(player: Player, case: Case): Boolean = player.vars.getVarbit(case.varbit) == 1

    fun told(player: Player, case: Case): Boolean = (player.charges[TOLD] shr CASES.indexOf(case)) and 1 == 1

    private fun tell(player: Player, case: Case) {
        player.charges[TOLD] = player.charges[TOLD] or (1 shl CASES.indexOf(case))
    }

    /** Whether the player has the necklace's secret, so cleaning no longer turns one up. */
    fun knowsNecklace(player: Player): Boolean = player.vars.getVarbit(Kudos.VARBIT_NECKLACE) == 1

    /** Takes every [obj] the player carries or banks. */
    private fun takeAll(world: World, player: Player, obj: Int) {
        for (slot in 0 until player.inventory.capacity) if (player.inventory[slot]?.id == obj) player.inventory.remove(slot)
        world.bank.take(player.bank, world.bankScreen.tabs(player), obj, Int.MAX_VALUE)
    }

    private fun carries(player: Player, obj: Int) = player.inventory.count(obj) > 0

    /** The finds an archaeologist talks about, in the transcript's order, but the case finds'. */
    private val OTHER_FINDS = listOf(MuseumCleaning.ARROWHEADS, MuseumCleaning.JEWELLERY, MuseumCleaning.OLD_CHIPPED_VASE)

    /** Everything an archaeologist will look at, for the item-on-him handlers. */
    val SHOWABLE = listOf(MuseumCleaning.CLEAN_NECKLACE) + CASES.map { it.obj } + OTHER_FINDS

    /** The find he looks at when the player says they found something: the first carried, in his order. */
    fun firstCarried(player: Player): Int? = SHOWABLE.firstOrNull { carries(player, it) }

    internal suspend fun Dialogue.archaeologist(world: World, greeting: Int) = with(MuseumArchaeologistLines) {
        chatNpc(ChatAnim.HAPPY, GREETINGS[greeting])
        when (choice(HELP, INTERESTING, NO_THANKS)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, HELP)
                if (!MuseumCleaning.equipped(player)) {
                    chatNpc(ChatAnim.NEUTRAL, NEED_EQUIPMENT_1)
                    chatNpc(ChatAnim.NEUTRAL, NEED_EQUIPMENT_2)
                } else {
                    chatNpc(ChatAnim.HAPPY, HAVE_EQUIPMENT)
                    chatNpc(ChatAnim.NEUTRAL, CLEAN_SOME_1)
                    chatNpc(ChatAnim.NEUTRAL, CLEAN_SOME_2)
                }
            }
            2 -> {
                chatPlayer(ChatAnim.HAPPY, INTERESTING)
                chatNpc(ChatAnim.QUIZ, LETS_LOOK)
                val find = firstCarried(player)
                if (find == null) {
                    chatPlayer(ChatAnim.SAD, LOST_IT)
                    chatNpc(ChatAnim.LAUGH, NEVER_HAD)
                } else {
                    show(world, find, used = false)
                }
            }
            else -> chatPlayer(ChatAnim.NEUTRAL, NO_THANKS_SAID)
        }
    }

    /** [obj] used on an archaeologist. */
    internal suspend fun Dialogue.usedOn(world: World, obj: Int) = with(MuseumArchaeologistLines) {
        chatPlayer(ChatAnim.HAPPY, TAKE_A_LOOK)
        if (obj in SHOWABLE) show(world, obj, used = true) else chatNpc(ChatAnim.NEUTRAL, DONT_NEED)
    }

    /** He looks at [obj], which the player carries. [used]: it was used on him, so "Take a look at this..." is said already. */
    private suspend fun Dialogue.show(world: World, obj: Int, used: Boolean) = with(MuseumArchaeologistLines) {
        val case = CASES.firstOrNull { it.obj == obj }
        when {
            case != null -> showCaseFind(case)
            obj == MuseumCleaning.CLEAN_NECKLACE -> necklace(world)
            obj == MuseumCleaning.ARROWHEADS -> {
                chatPlayer(ChatAnim.NEUTRAL, ARROWHEADS)
                chatNpc(ChatAnim.NEUTRAL, ARROWHEADS_1)
                chatNpc(ChatAnim.NEUTRAL, ARROWHEADS_2)
                chatPlayer(ChatAnim.NEUTRAL, ARROWHEADS_OK)
            }
            obj == MuseumCleaning.JEWELLERY -> {
                if (!used) chatPlayer(ChatAnim.HAPPY, TAKE_A_LOOK)
                chatPlayer(ChatAnim.HAPPY, JEWELLERY)
                chatNpc(ChatAnim.HAPPY, JEWELLERY_1)
                chatPlayer(ChatAnim.HAPPY, JEWELLERY_OK)
                // Checked again after the talk's clicks: the jewellery could have been dropped meanwhile.
                if (MuseumCleaning.removeOne(player, MuseumCleaning.JEWELLERY)) {
                    MuseumCleaning.give(world, player, COINS, 5)
                    objbox(COINS, JEWELLERY_COINS)
                }
            }
            else -> {
                chatPlayer(ChatAnim.NEUTRAL, VASE)
                chatNpc(ChatAnim.NEUTRAL, VASE_1)
                chatNpc(ChatAnim.NEUTRAL, VASE_2)
                chatPlayer(ChatAnim.NEUTRAL, VASE_OK)
            }
        }
    }

    private suspend fun Dialogue.showCaseFind(case: Case) = with(case.lines) {
        when {
            displayed(player, case) -> {
                chatPlayer(ChatAnim.QUIZ, said)
                chatNpc(ChatAnim.NEUTRAL, MuseumArchaeologistLines.ALREADY)
            }
            told(player, case) -> {
                chatNpc(ChatAnim.QUIZ, backAgain)
                chatPlayer(ChatAnim.NEUTRAL, goNow)
            }
            else -> {
                chatPlayer(ChatAnim.QUIZ, said)
                for (line in first) chatNpc(ChatAnim.HAPPY, line)
                tell(player, case)
                chatPlayer(ChatAnim.HAPPY, reply)
            }
        }
    }

    private suspend fun Dialogue.necklace(world: World) = with(MuseumArchaeologistLines) {
        for ((i, line) in NECKLACE.withIndex()) if (i % 2 == 0) chatPlayer(ChatAnim.QUIZ, line) else chatNpc(ChatAnim.HAPPY, line)
        // He takes it here; checked again, as the talk's clicks give time to drop it.
        if (!carries(player, MuseumCleaning.CLEAN_NECKLACE)) return@with
        takeAll(world, player, MuseumCleaning.CLEAN_NECKLACE)
        player.vars.setVarbit(Kudos.VARBIT_NECKLACE, 1)
        objbox(MuseumCleaning.CLEAN_NECKLACE, NECKLACE_TAKES)
        objbox(MuseumCleaning.CLEAN_NECKLACE, NECKLACE_SHOWS_1)
        objbox(MuseumCleaning.CLEAN_NECKLACE, NECKLACE_SHOWS_2)
        chatPlayer(ChatAnim.HAPPY, NECKLACE_RECAP)
        chatNpc(ChatAnim.HAPPY, NECKLACE_WELL_DONE)
    }

    /** [case]'s find used on its case: on display once an archaeologist has sent the player there. */
    fun putInCase(world: World, player: Player, case: Case) {
        when {
            displayed(player, case) -> return world.message(player, MuseumCaseLines.FULL)
            !told(player, case) -> return world.message(player, MuseumCaseLines.NOT_SHOWN)
        }
        if (case.takesAll) {
            if (!carries(player, case.obj)) return
            takeAll(world, player, case.obj)
        } else if (!MuseumCleaning.removeOne(player, case.obj)) {
            return
        }
        player.vars.setVarbit(case.varbit, 1)
        Kudos.refresh(player)
        if (case.takesAll) world.objTypes[case.obj]?.let { world.message(player, MuseumCaseLines.PLACED, "item" to it.name) }
    }
}

fun registerMuseumFinds(world: World) {
    with(MuseumFinds) {
        for (archaeologist in ARCHAEOLOGISTS) {
            world.handlers.onNpc(archaeologist, TALK_TO) { op ->
                val greeting = world.random.nextInt(MuseumArchaeologistLines.GREETINGS.size)
                world.dialogues.start(this, op.npc) { archaeologist(world, greeting) }
            }
            world.handlers.onObjOnNpc(archaeologist) { op -> world.dialogues.start(this, op.npc) { usedOn(world, op.obj) } }
        }
        for (case in CASES) {
            world.handlers.onObjOnLoc(case.loc, case.obj) {
                arriveDelay()
                putInCase(world, player, case)
            }
        }
    }
}
