package osrsps.game.content

import osrsps.game.FrameSlot
import osrsps.game.Interfaces
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

/**
 * What Study shows on each Varrock Museum display case and on the king's painting: the Wiki's Transcript:Display case
 * (Varrock Museum, number N) and Transcript:Painting (Varrock Museum), read 2026-11-19. A paragraph break is two `<br>`.
 */
object MuseumDisplayLines : Lines("museum_display", "Transcript:Display case (Varrock Museum)") {
    private fun case(number: Int, what: String) = line("case_$number", "display $number, Study: $what")

    val NO_INFORMATION = line("no_information", "an empty timeline case, Study (display 17's words before Observatory Quest's story)")

    val CASE_1 = case(1, "Barbarian Village")
    val CASE_2 = case(2, "the barbarian weapons (two paragraphs)")
    val CASE_3 = case(3, "the Dagon'hai tome, once What Lies Below's story is told")
    val CASE_4 = case(4, "the Black Knights' armour")
    val CASE_5 = case(5, "the bow and sword among runes")
    val CASE_6 = case(6, "the bridge over water")
    val CASE_7 = case(7, "the broken arrows: the Carnilleans (the text before Hazeel Cult's story)")
    val CASE_8 = case(8, "the druids' stone circle")
    val CASE_9 = case(9, "Excalibur, once Merlin's Crystal's story is told")
    val CASE_10 = case(10, "the gnome glider, before The Grand Tree's story")
    val CASE_10_AFTER = line("case_10_after", "display 10, Study: the gnome glider, after The Grand Tree's story")
    val CASE_11 = case(11, "the Infinity armour")
    val CASE_12 = case(12, "the Mage Training Arena guardian")
    val CASE_14 = case(14, "the map of Edgeville")
    val CASE_15 = case(15, "the map of Morytania")
    val CASE_16 = case(16, "the map of RuneScape, before Curse of the Empty Lord's story (two paragraphs after the age)")
    val CASE_16_AFTER = line("case_16_after", "display 16, Study: the map, after Curse of the Empty Lord's story")
    val CASE_17 = case(17, "the Observatory, once Observatory Quest's story is told")
    val CASE_18 = case(18, "the Phoenix crossbow and black arm bands")
    val CASE_19 = case(19, "the assorted items")
    val CASE_20 = case(20, "Robert the Strong, once Two Cats' story is told")
    val CASE_21 = case(21, "the runestones and rune essence rock")
    val CASE_22 = case(22, "the pottery, before the player's pottery is in it")
    val CASE_22_AFTER = line("case_22_after", "display 22, Study: the pottery, once the player's pottery is in it")
    val CASE_23 = case(23, "the seven priests' helms, once Priest in Peril's story is told")
    val CASE_23_AFTER = line("case_23_after", "display 23, Study: the helms, after In Aid of the Myreque's story")
    val CASE_24 = case(24, "the empty case before Shield of Arrav's story (two paragraphs after the age)")
    val CASE_24_AFTER = line("case_24_after", "display 24, Study: the shield, after Shield of Arrav's story (two paragraphs after the age)")
    val CASE_24_DEFENDER = line("case_24_defender", "display 24, Study: the shield, after Defender of Varrock's story (three paragraphs after the age)")
    val CASE_25 = case(25, "the Silverlight replica, before Demon Slayer's story")
    val CASE_25_AFTER = line("case_25_after", "display 25, Study: Silverlight, after Demon Slayer's story")
    val CASE_26 = case(26, "the skeleton")
    val CASE_27 = case(27, "the soil layers (two title lines, then six paragraphs)")
    val CASE_28 = case(28, "the Staff of Armadyl, the transcript's first text")
    val CASE_28_AFTER = line("case_28_after", "display 28, Study: the staff, the transcript's text after Temple of Ikov")
    val CASE_29 = case(29, "the star chart")
    val CASE_30 = case(30, "the terracotta statuette, before The Golem")
    val CASE_30_AFTER = line("case_30_after", "display 30, Study: after The Golem, the statuette stolen")
    val CASE_31 = case(31, "the three gods' symbols")
    val CASE_32 = case(32, "the werewolf skin")
    val CASE_33 = case(33, "the White Knights' armour (two paragraphs)")
    val CASE_34 = case(34, "the world map")
    val CASE_35 = case(35, "the Zamorakian symbol")
    val CASE_36 = case(36, "the old symbol")
    val CASE_37 = case(37, "the ancient symbol")
    val CASE_38 = case(38, "the vase")
    val CASE_40 = case(40, "the jewellery")
    val CASE_41 = case(41, "the arrowheads")
    val CASE_42 = case(42, "the Zarosian talisman")
    val CASE_43 = case(43, "the stone tablet")
    val CASE_44 = case(44, "the ancient coin")
    val CASE_45 = case(45, "the old coin")
    val CASE_46 = case(46, "the king's painting, before Making History's story")
    val CASE_46_AFTER = line("case_46_after", "display 46 (the painting), Study: after Making History's story")
    val CASE_47 = case(47, "the map of Ardougne")
}

/**
 * Study on the Varrock Museum's display cases (the Dig Site exhibit on the ground floor, the Timeline upstairs) and the
 * king's painting: it opens `vm_digsite` (528) for the Dig Site finds and `vm_timeline` (534) for the rest, with the
 * display's number and its words. 45 displays in all, numbered 1 to 47 (no 13 or 39).
 *
 * From the Wiki's display case pages and transcripts (read 2026-11-19), and the 242 cache: the locs and their multilocs
 * (each case's base loc, the varbit it follows), the two interfaces (osrs-dumps 2026-11-11-rev242). A case's words
 * follow the same varbits as what it shows: the Dig Site finds put in their cases ([MuseumFinds]), the stories told to
 * Historian Minas ([MuseumTimeline]), and `golem_retrieved_statuette` (355), which is set after The Golem
 * ([osrsps.game.Quests.AFTER_QUESTS]) so the terracotta case is empty, its statuette stolen, as the transcript has it.
 * The talisman and tablet cases (42 and 43) are filled after The Dig Site the same way.
 *
 * Inferred: an empty case (the Dig Site finds' before they are put in, the timeline's before their story) says display
 * 17's words before its story ([MuseumDisplayLines.NO_INFORMATION]), as display 17 does; display 21 says its one text whichever of its two pieces shows;
 * display 28's two texts are its staff shown for Temple of Ikov (1) and after Curse of the Empty Lord (2), the longer one
 * telling of Zamorak's rise, though the Wiki labels it "after Temple of Ikov"; display 7 keeps its first text after Hazeel
 * Cult's story, the Wiki giving another only for Hazeel's side, which this world has not picked. Left out: each display's
 * picture (the model components' models are set by the server and unknown), display 24's words during Defender of
 * Varrock, and Open on the terracotta case, which only shows before The Golem.
 */
object MuseumDisplays {
    const val STUDY = 1
    const val CLOSE = 1

    /** `vm_digsite`: the text and the display number. */
    const val DIGSITE = 528
    const val DIGSITE_TEXT = 2
    const val DIGSITE_NUMBER = 16

    /** `vm_timeline`. */
    const val TIMELINE = 534
    const val TIMELINE_TEXT = 2
    const val TIMELINE_NUMBER = 84

    const val VARBIT_GOLEM_STATUETTE = 355
    const val VARBIT_TABLET = 3643
    const val VARBIT_TALISMAN = 3644

    /** A display: its [number], the [loc] the map places, the interface it opens, and what it says to [player]. */
    class Display(val number: Int, val loc: Int, val interfaceId: Int, val text: (Player) -> Line)

    /** [varbit]'s value, 0 in a world whose cache has no such varbit (the headless tests that don't build the museum). */
    private fun Player.varbit(varbit: Int) = if (vars.types.varbit(varbit) == null) 0 else vars.getVarbit(varbit)

    private fun fixed(number: Int, loc: Int, line: Line, interfaceId: Int = TIMELINE) = Display(number, loc, interfaceId) { line }

    /** A case empty until [varbit] is set, then saying [line]. */
    private fun filled(number: Int, loc: Int, varbit: Int, line: Line, interfaceId: Int = TIMELINE) =
        Display(number, loc, interfaceId) { if (it.varbit(varbit) >= 1) line else MuseumDisplayLines.NO_INFORMATION }

    /** A case saying [before], then [after] once [varbit] reaches [at]. */
    private fun changes(number: Int, loc: Int, varbit: Int, before: Line, after: Line, at: Int = 1, interfaceId: Int = TIMELINE) =
        Display(number, loc, interfaceId) { if (it.varbit(varbit) >= at) after else before }

    val DISPLAYS: List<Display> = with(MuseumDisplayLines) {
        listOf(
            // The Dig Site exhibit, ground floor.
            filled(36, 12137, Kudos.VARBIT_OLD_SYMBOL, CASE_36, DIGSITE),
            filled(37, 12138, Kudos.VARBIT_ANCIENT_SYMBOL, CASE_37, DIGSITE),
            fixed(38, 24541, CASE_38, DIGSITE),
            fixed(40, 24544, CASE_40, DIGSITE),
            fixed(41, 24545, CASE_41, DIGSITE),
            filled(42, 12232, VARBIT_TALISMAN, CASE_42, DIGSITE),
            filled(43, 12233, VARBIT_TABLET, CASE_43, DIGSITE),
            filled(44, 12234, Kudos.VARBIT_ANCIENT_COIN, CASE_44, DIGSITE),
            filled(45, 15484, Kudos.VARBIT_OLD_COIN, CASE_45, DIGSITE),
            changes(22, 12139, Kudos.VARBIT_POTTERY, CASE_22, CASE_22_AFTER, interfaceId = DIGSITE),
            fixed(27, 24621, CASE_27),
            // The Timeline, first floor.
            fixed(1, 24645, CASE_1),
            fixed(2, 24650, CASE_2),
            fixed(4, 24647, CASE_4),
            fixed(5, 24651, CASE_5),
            fixed(6, 24641, CASE_6),
            fixed(7, 24655, CASE_7),
            fixed(8, 24629, CASE_8),
            fixed(11, 24648, CASE_11),
            fixed(15, 24640, CASE_15),
            changes(16, 24622, MuseumTimeline.VARBIT_STAFF_OF_ARMADYL, CASE_16, CASE_16_AFTER, at = 2),
            filled(17, 24642, MuseumTimeline.VARBIT_OBSERVATORY, CASE_17),
            fixed(19, 24628, CASE_19),
            filled(20, 24633, MuseumTimeline.VARBIT_ROBERT, CASE_20),
            fixed(21, 15485, CASE_21),
            Display(23, 24636, TIMELINE) {
                when (it.varbit(MuseumTimeline.VARBIT_SALVE_KNIGHTS)) { 0 -> NO_INFORMATION; 1 -> CASE_23; else -> CASE_23_AFTER }
            },
            Display(24, 24638, TIMELINE) {
                when {
                    it.varbit(MuseumTimeline.VARBIT_DEFENDER) >= 1 -> CASE_24_DEFENDER
                    it.varbit(MuseumTimeline.VARBIT_SHIELD_SHOWN) >= 1 -> CASE_24_AFTER
                    else -> CASE_24
                }
            },
            changes(25, 24649, MuseumTimeline.VARBIT_SILVERLIGHT, CASE_25, CASE_25_AFTER),
            Display(28, 24623, TIMELINE) {
                when (it.varbit(MuseumTimeline.VARBIT_STAFF_OF_ARMADYL)) { 0 -> NO_INFORMATION; 1 -> CASE_28; else -> CASE_28_AFTER }
            },
            fixed(29, 24635, CASE_29),
            changes(30, 24626, VARBIT_GOLEM_STATUETTE, CASE_30, CASE_30_AFTER),
            fixed(31, 24625, CASE_31),
            fixed(32, 24644, CASE_32),
            fixed(34, 24632, CASE_34),
            // The Timeline, second floor.
            filled(3, 24662, MuseumTimeline.VARBIT_SUROK, CASE_3),
            filled(9, 24653, MuseumTimeline.VARBIT_EXCALIBUR, CASE_9),
            changes(10, 24660, MuseumTimeline.VARBIT_GLIDER, CASE_10, CASE_10_AFTER),
            fixed(12, 24659, CASE_12),
            fixed(14, 24646, CASE_14),
            fixed(18, 24656, CASE_18),
            fixed(26, 24657, CASE_26),
            fixed(33, 24658, CASE_33),
            fixed(35, 24652, CASE_35),
            changes(46, 24620, MuseumTimeline.VARBIT_LATHAS, CASE_46, CASE_46_AFTER),
            fixed(47, 24661, CASE_47),
        )
    }

    fun isOpen(player: Player) = player.modals[FrameSlot.MAINMODAL].let { it == DIGSITE || it == TIMELINE }

    /** Study on [display]: its interface with its number and words. The client keeps a component's text, so each open sets both. */
    fun study(world: World, player: Player, display: Display) {
        val interfaces = world.interfaces
        val (text, number) = if (display.interfaceId == DIGSITE) DIGSITE_TEXT to DIGSITE_NUMBER else TIMELINE_TEXT to TIMELINE_NUMBER
        interfaces.openModal(player, FrameSlot.MAINMODAL, display.interfaceId)
        interfaces.setText(player, display.interfaceId, number, display.number.toString())
        interfaces.setText(player, display.interfaceId, text, world.textFor(player)[display.text(player)])
        interfaces.setEvents(player, display.interfaceId, CLOSE, -1, -1, Interfaces.IF_EVENT_OP1)
    }
}

fun registerMuseumDisplays(world: World) {
    with(MuseumDisplays) {
        for (display in DISPLAYS) world.handlers.onLoc(display.loc, STUDY) { study(world, player, display) }
        for (interfaceId in listOf(DIGSITE, TIMELINE)) {
            world.handlers.onButton(interfaceId, CLOSE) { if (isOpen(player)) world.interfaces.closeModals(player) }
        }
    }
}
