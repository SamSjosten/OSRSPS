package osrsps.game.content.randomevents

import net.rsprot.protocol.game.outgoing.interfaces.IfSetObject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import osrsps.game.FrameSlot
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

/**
 * The certer brothers' lines, the same words for Giles, Miles and Niles (their three transcripts, read 2026-11-19).
 * The answers' words aren't on the Wiki: those keys wait for a recording.
 */
object CerterLines : Lines("certer", "Transcript:Giles, Transcript:Miles and Transcript:Niles (the same words)") {
    val GREETING = line("greeting", "Overhead dialogue: upon spawning in for the player", "name")
    val TALK_TO_ME = line("talk_to_me", "Overhead dialogue: while waiting, dialogue 1", "name")
    val ARE_YOU_THERE = line("are_you_there", "Overhead dialogue: while waiting, dialogue 2", "name")
    val LIKE_TO_TALK = line("like_to_talk", "Overhead dialogue: while waiting, dialogue 3", "name")
    val EHEM = line("ehem", "Overhead dialogue: while waiting, dialogue 4", "name")
    val MAYBE_LATER = line("maybe_later", "Overhead dialogue: if the player does not respond in time")
    val RIGHT = line("right", "Standard dialogue: answering correctly")
    val WRONG = line("wrong", "Standard dialogue: answering incorrectly")
    val SOMEONE_ELSE = line("someone_else", "Talking to another player's certer", "name")

    val FISH = line("answer_fish", "an answer in the window (macro_certer_texta to c) for the fish; no source yet")
    val SWORD = line("answer_sword", "an answer for the sword; no source yet")
    val BATTLEAXE = line("answer_battleaxe", "an answer for the battleaxe; no source yet")
    val HELM = line("answer_helm", "an answer for the medium helm; no source yet")
    val SHIELD = line("answer_shield", "an answer for the kiteshield; no source yet")
    val SHEARS = line("answer_shears", "an answer for the shears; no source yet")
    val SPADE = line("answer_spade", "an answer for the spade; no source yet")
    val RING = line("answer_ring", "an answer for the ring; no source yet")
    val NECKLACE = line("answer_necklace", "an answer for the necklace; no source yet")
}

/**
 * The Certer event (OSRS Wiki, Certer, Giles, Miles and Niles pages and transcripts, read 2026-11-19): one of the three
 * brothers asks the player to name an item. Talk-to opens macro_certer (interface 184), the item turning in
 * macro_certer_item with three answers beside it (texta to textc, picked with macro_certer_a to c); the right answer
 * gets one of [Gifts] (the Certer page's table is the same), a wrong one nothing, and either ends the event. The items
 * are the ten the Certer page's trivia names ("hex edit detected", objs 6189 to 6198: two fish, a sword, a
 * battleaxe, a medium helm, a kiteshield, shears, a spade, a ring and a necklace).
 *
 * Guessed: the two wrong answers are two other kinds of item, at random, and the right one's place among the three is
 * random; the item's zoom (the component's 400); closing the window ends the talk with no answer, and the brother
 * stays until his minute is up; chathead expressions; that he goes at once after the answer. The answers' words wait
 * for a recording. Left out: the underwater brothers (5439 to 5441).
 */
object Certer : RandomEvent("certer", 5436) {
    const val NILES = 5436
    const val MILES = 5437
    const val GILES = 5438

    /** One of the three brothers comes, each as likely (guessed: the Wiki doesn't say). */
    override val npcIds = listOf(NILES, MILES, GILES)

    override val greeting = CerterLines.GREETING
    override val nags = with(CerterLines) { listOf(TALK_TO_ME, ARE_YOU_THERE, LIKE_TO_TALK, EHEM) }
    override val leaving = CerterLines.MAYBE_LATER
    override val notYours = CerterLines.SOMEONE_ELSE

    override suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean = with(Certers) {
        val item = ITEMS[world.random.nextInt(ITEMS.size)]
        val wrong = ANSWERS.filter { it != item.answer }.toMutableList()
        val choices = MutableList(3) { wrong.removeAt(world.random.nextInt(wrong.size)) }
        val right = world.random.nextInt(3)
        choices[right] = item.answer
        val words = world.textFor(player)
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, INTERFACE)
        player.session.queue(IfSetObject(INTERFACE, ITEM, item.obj, ZOOM))
        for (i in 0 until 3) player.session.queue(IfSetText(INTERFACE, TEXTS[i], words[choices[i]]))
        val (picked, _) = context.pauseButtons(INTERFACE, BUTTONS, -1..-1)
        world.interfaces.closeModals(player)
        // Answered: the gift (if right) and the end come before his line, so shutting it can't give another try.
        val correct = BUTTONS.indexOf(picked) == right
        if (correct) Gifts.roll(world).let { events.reward(player, it.id, it.count) }
        events.claimed(player)
        chatNpc(if (correct) ChatAnim.HAPPY else ChatAnim.SAD, if (correct) CerterLines.RIGHT else CerterLines.WRONG)
        true
    }
}

object Certers {
    const val INTERFACE = 184
    const val ITEM = 7
    val TEXTS = listOf(1, 2, 3)
    val BUTTONS = listOf(8, 9, 10)
    const val ZOOM = 400

    /** An item the brothers show, and its answer. */
    data class Item(val obj: Int, val answer: Line)

    val ITEMS: List<Item> = with(CerterLines) {
        listOf(
            Item(6189, FISH), Item(6190, FISH), Item(6191, SWORD), Item(6192, BATTLEAXE), Item(6193, HELM),
            Item(6194, SHIELD), Item(6195, SHEARS), Item(6196, SPADE), Item(6197, RING), Item(6198, NECKLACE),
        )
    }

    val ANSWERS: List<Line> = ITEMS.map { it.answer }.distinct()

    /** Interface and component names in the rev 242 cache, for [osrsps.cache.GameFrameCheck]. */
    val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>>
        get() = mapOf(
            INTERFACE to (
                "macro_certer" to mapOf(
                    1 to "macro_certer_texta", 2 to "macro_certer_textb", 3 to "macro_certer_textc", ITEM to "macro_certer_item",
                    8 to "macro_certer_a", 9 to "macro_certer_b", 10 to "macro_certer_c",
                )
                ),
        )
}
