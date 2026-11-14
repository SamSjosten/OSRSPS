package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

/**
 * Historian Minas's quest stories for the Timeline exhibit, from the Wiki's Transcript:Historian Minas (read 2026-11-14):
 * each story's boxes in order, the old lamp he gives after some, and the What Lies Below story's two endings.
 */
object MuseumTimelineLines : Lines("historian_minas_timeline", "Transcript:Historian Minas") {
    /** Who says a box: [PLAYER], [MINAS] or a message box ([BOX]). */
    enum class Speaker { PLAYER, MINAS, BOX }

    class Box(val speaker: Speaker, val line: Line)

    private fun boxes(story: String, pattern: String, where: String, nameIn: Int = -1): List<Box> = pattern.mapIndexed { i, c ->
        val speaker = when (c) {
            'P' -> Speaker.PLAYER
            'N' -> Speaker.MINAS
            else -> Speaker.BOX
        }
        val who = when (speaker) {
            Speaker.PLAYER -> "the player"
            Speaker.MINAS -> "Minas"
            Speaker.BOX -> "a message box"
        }
        val args = if (i == nameIn) arrayOf("name") else emptyArray()
        Box(speaker, line("${story}_${i + 1}", "$where: box ${i + 1} of ${pattern.length} ($who)", *args))
    }

    val DEMON_SLAYER = boxes("demon_slayer", "PN", "Demon Slayer's story")
    val SHIELD_OF_ARRAV = boxes("shield_of_arrav", "PNPNPN", "Shield of Arrav's story")
    val RUNE_MYSTERIES = boxes("rune_mysteries", "PNPN", "Rune Mysteries' story")
    val OBSERVATORY = boxes("observatory", "PN", "Observatory Quest's story")
    val MERLINS_CRYSTAL = boxes("merlins_crystal", "PNPNPNPN", "Merlin's Crystal's story, the last box with the player's name", nameIn = 7)
    val GRAND_TREE = boxes("grand_tree", "PNPNPN", "The Grand Tree's story")
    val PRIEST_IN_PERIL = boxes("priest_in_peril", "PNPN", "Priest in Peril's story")
    val MYREQUE = boxes("myreque", "PNPN", "In Aid of the Myreque's story")
    val TWO_CATS = boxes("two_cats", "PNPNNPNPN", "A Tail of Two Cats' story")
    val MAKING_HISTORY = boxes("making_history", "PNPNPN", "Making History's story")
    val TEMPLE_OF_IKOV = boxes("temple_of_ikov", "PNMN", "Temple of Ikov's story, on Lucien's side")
    val CURSE_OF_THE_EMPTY_LORD = boxes("curse_of_the_empty_lord", "PPNMN", "Curse of the Empty Lord's story")
    val HAZEEL_CULT = boxes("hazeel_cult", "PNPNPMN", "Hazeel Cult's story")
    val WHAT_LIES_BELOW = boxes("what_lies_below", "PNPNPNP", "What Lies Below's story, before its two endings")
    val WHAT_LIES_BELOW_NO_BOOK = boxes("what_lies_below_no_book", "MNPN", "What Lies Below's story, without the Dagon'hai history book")
    val WHAT_LIES_BELOW_BOOK = boxes("what_lies_below_book", "MPN", "What Lies Below's story, carrying the Dagon'hai history book")
    val DEFENDER_OF_VARROCK = boxes("defender_of_varrock", "PNPPN", "Defender of Varrock's story")

    val LAMP = line("lamp", "the old lamp after Shield of Arrav's and Merlin's Crystal's stories: Minas")
    val LAMP_DOV = line("lamp_dov", "the old lamp after Defender of Varrock's story: Minas (worded a little differently)")
    val TAT = line("tat", "after the lamp: the player's thanks")
    val LAMP_WELCOME = line("lamp_welcome", "after the lamp: Minas's reply")
    val LAMP_RECEIVED = line("lamp_received", "after the lamp: the message box")
}

/**
 * The Timeline exhibit's quest Kudos (Museum Kudos step 3): Historian Minas's "I have some information" hears, in the
 * Wiki's order, every quest story the player hasn't told him, filling its timeline display (the `vm_*` varbits on varp
 * `vm_displays` 1011, which the display cases' multilocs follow) for its Kudos, and for five of them gives an antique lamp.
 * Every quest counts complete here, so every story is there to tell; told, a story isn't told again.
 *
 * Kudos per the client's Kudos List (enum 1740 rows 19 to 33 and proc `vm_kudos_get_status`): 5 a story, 10 for Curse
 * of the Empty Lord, 80 in all. Temple of Ikov and Curse of the Empty Lord share `vm_staff_of_armadyl` (1 then 2), as
 * Priest in Peril and In Aid of the Myreque share `vm_salveknights`. What Lies Below fills its display only with the
 * Dagon'hai history book (11001) carried, which he keeps. The lamps are the cache's `vm_reward_*` antique lamps; the
 * Wiki's Kudos page gives one for Shield of Arrav, Merlin's Crystal and Making History (1,000 XP), Defender of Varrock
 * (5,000) and Curse of the Empty Lord (10,000).
 *
 * Guessed: every expression; Temple of Ikov told on Lucien's side (the project's Ikov = Lucien); the stories all told in
 * one talk, one after another, and the talk ending after the last; the display and lamp given as the story's display
 * line is said, so walking away later loses neither; a lamp that doesn't fit dropped under the player; Making History's
 * and Curse of the Empty Lord's lamps given with the Shield of Arrav lamp lines (the transcript shows no lamp for
 * either); `vm_soa_vis` (the shield's case shown) set with Shield of Arrav's display. Left out: rubbing the lamps (no lamp
 * interface yet) and the displays' Study.
 */
object MuseumTimeline {
    const val VARBIT_STAFF_OF_ARMADYL = 3650
    const val VARBIT_SALVE_KNIGHTS = 3651
    const val VARBIT_OBSERVATORY = 3652
    const val VARBIT_SILVERLIGHT = 3653
    const val VARBIT_LATHAS = 3654
    const val VARBIT_EXCALIBUR = 3655
    const val VARBIT_SHIELD_OF_ARRAV = 3656
    const val VARBIT_ROBERT = 3657
    const val VARBIT_GLIDER = 3658
    const val VARBIT_ARDOUGNE = 3659
    const val VARBIT_SUROK = 3660
    const val VARBIT_RUNES = 3661
    const val VARBIT_DEFENDER = 9678
    const val VARBIT_SHIELD_SHOWN = 9679

    /** `surok_tome`, Dagon'hai history. */
    const val DAGONHAI_HISTORY = 11001

    const val LAMP_MERLIN = 11185
    const val LAMP_ARRAV = 11186
    const val LAMP_HISTORY = 11187
    const val LAMP_GHOSTS = 11188
    const val LAMP_DEFENDER = 28820

    /** A story: told once [varbit] is at least [value]; [lamp] is given after it, with [lampLine]. */
    enum class Story(
        val boxes: List<MuseumTimelineLines.Box>,
        val varbit: Int,
        val value: Int = 1,
        val lamp: Int? = null,
        val lampLine: Line = MuseumTimelineLines.LAMP,
    ) {
        DEMON_SLAYER(MuseumTimelineLines.DEMON_SLAYER, VARBIT_SILVERLIGHT),
        SHIELD_OF_ARRAV(MuseumTimelineLines.SHIELD_OF_ARRAV, VARBIT_SHIELD_OF_ARRAV, lamp = LAMP_ARRAV),
        RUNE_MYSTERIES(MuseumTimelineLines.RUNE_MYSTERIES, VARBIT_RUNES),
        OBSERVATORY(MuseumTimelineLines.OBSERVATORY, VARBIT_OBSERVATORY),
        MERLINS_CRYSTAL(MuseumTimelineLines.MERLINS_CRYSTAL, VARBIT_EXCALIBUR, lamp = LAMP_MERLIN),
        GRAND_TREE(MuseumTimelineLines.GRAND_TREE, VARBIT_GLIDER),
        PRIEST_IN_PERIL(MuseumTimelineLines.PRIEST_IN_PERIL, VARBIT_SALVE_KNIGHTS),
        MYREQUE(MuseumTimelineLines.MYREQUE, VARBIT_SALVE_KNIGHTS, value = 2),
        TWO_CATS(MuseumTimelineLines.TWO_CATS, VARBIT_ROBERT),
        MAKING_HISTORY(MuseumTimelineLines.MAKING_HISTORY, VARBIT_LATHAS, lamp = LAMP_HISTORY),
        TEMPLE_OF_IKOV(MuseumTimelineLines.TEMPLE_OF_IKOV, VARBIT_STAFF_OF_ARMADYL),
        CURSE_OF_THE_EMPTY_LORD(MuseumTimelineLines.CURSE_OF_THE_EMPTY_LORD, VARBIT_STAFF_OF_ARMADYL, value = 2, lamp = LAMP_GHOSTS),
        HAZEEL_CULT(MuseumTimelineLines.HAZEEL_CULT, VARBIT_ARDOUGNE),
        WHAT_LIES_BELOW(MuseumTimelineLines.WHAT_LIES_BELOW, VARBIT_SUROK),
        DEFENDER_OF_VARROCK(MuseumTimelineLines.DEFENDER_OF_VARROCK, VARBIT_DEFENDER, lamp = LAMP_DEFENDER, lampLine = MuseumTimelineLines.LAMP_DOV),
    }

    /** False in worlds whose cache has no timeline varbits (the headless tests that don't build the museum). */
    fun built(player: Player) = player.vars.types.varbit(VARBIT_STAFF_OF_ARMADYL) != null

    fun told(player: Player, story: Story) = player.vars.getVarbit(story.varbit) >= story.value

    /** The stories not yet told, in the order he hears them. */
    fun untold(player: Player): List<Story> = if (!built(player)) emptyList() else Story.entries.filter { !told(player, it) }

    /** Fills [story]'s display, for its Kudos. */
    fun fill(player: Player, story: Story) {
        if (told(player, story)) return
        player.vars.setVarbit(story.varbit, story.value)
        if (story == Story.SHIELD_OF_ARRAV) player.vars.setVarbit(VARBIT_SHIELD_SHOWN, 1)
        Kudos.refresh(player)
    }

    /** Tells Minas every untold story, each filling its display. False with none to tell. */
    internal suspend fun Dialogue.tellStories(world: World): Boolean = with(MuseumTimelineLines) {
        if (untold(player).isEmpty()) return@with false
        for (story in Story.entries) {
            if (told(player, story)) continue
            if (story == Story.WHAT_LIES_BELOW) {
                say(story.boxes)
                val slot = (0 until player.inventory.capacity).firstOrNull { player.inventory[it]?.id == DAGONHAI_HISTORY }
                if (slot == null) {
                    say(WHAT_LIES_BELOW_NO_BOOK)
                    continue
                }
                say(WHAT_LIES_BELOW_BOOK.dropLast(1))
                player.inventory.remove(slot, 1)
                fill(player, story)
                say(WHAT_LIES_BELOW_BOOK.takeLast(1))
                continue
            }
            say(story.boxes.dropLast(1))
            fill(player, story)
            story.lamp?.let { lamp -> world.objTypes[lamp]?.let { world.groundObjs.give(player, it, 1) } }
            say(story.boxes.takeLast(1))
            if (story.lamp == null) continue
            chatNpc(ChatAnim.HAPPY, story.lampLine)
            chatPlayer(ChatAnim.HAPPY, TAT)
            chatNpc(ChatAnim.HAPPY, LAMP_WELCOME)
            mesbox(LAMP_RECEIVED)
        }
        true
    }

    private suspend fun Dialogue.say(boxes: List<MuseumTimelineLines.Box>) {
        for (box in boxes) {
            val values = if ("name" in box.line.args) arrayOf("name" to player.username) else emptyArray()
            when (box.speaker) {
                MuseumTimelineLines.Speaker.PLAYER -> chatPlayer(ChatAnim.HAPPY, box.line, *values)
                MuseumTimelineLines.Speaker.MINAS -> chatNpc(ChatAnim.HAPPY, box.line, *values)
                MuseumTimelineLines.Speaker.BOX -> mesbox(box.line, *values)
            }
        }
    }
}
