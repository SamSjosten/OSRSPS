package osrsps.game.content.randomevents

import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import osrsps.game.FrameSlot
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.bank.BankScreen
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** Capt' Arnav's lines; the words are in the dialogue file, from his transcript (read 2026-11-19). */
object CaptArnavLines : Lines("capt_arnav", "Transcript:Capt' Arnav") {
    val AVAST = line("avast", "Overhead dialogue: upon spawning in", "name")
    val AHOY = line("ahoy", "Overhead dialogue: waiting dialogue 1", "name")
    val PARLEY = line("parley", "Overhead dialogue: waiting dialogue 2")
    val NEED_HELP = line("need_help", "Overhead dialogue: waiting dialogue 3", "name")
    val TREASURE = line("treasure", "Overhead dialogue: waiting dialogue 4", "name")
    val IGNORING = line("ignoring", "Overhead dialogue: waiting dialogue 5")
    val OLD_PIRATE = line("old_pirate", "Overhead dialogue: waiting dialogue 6", "name")
    val KNOWS = line("knows", "Overhead dialogue: waiting dialogue 7", "name")
    val MAYBE_LATER = line("maybe_later", "Overhead dialogue: if the player does not respond in time")
    val DUG_UP = line("dug_up", "Standard dialogue: his first line (the laddie form)")
    val FIDDLY = line("fiddly", "Standard dialogue: his second line")
    val YES = line("yes", "Standard dialogue: the first option, and the player's words")
    val NO = line("no", "Standard dialogue: the second option, and the player's words")
    val COLUMNS = line("columns", "Standard dialogue: the first option, his answer")
    val WELL_DONE = line("well_done", "Solving the puzzle")
    val NOWHERE_NEAR = line("nowhere_near", "Failing the puzzle once: his first line")
    val MISUNDERSTOOD = line("misunderstood", "Failing the puzzle once: the player's answer")
    val ARROWS = line("arrows", "Failing the puzzle once: his second line")
    val MIDDLE_ROW = line("middle_row", "Failing the puzzle once: his third line")
    val CLEARER = line("clearer", "Failing the puzzle once: his fourth line")
    val TRY_AGAIN = line("try_again", "Failing the puzzle once: the player's last line")
    val SOMEONE_ELSE = line("someone_else", "Failing the puzzle twice")
    val NOT_THE_PERSON = line("not_the_person", "Other people's Capt' Arnav")

    // The words under the columns, one per picture (enum_1546's four models); no source gives them yet.
    val PICTURE_0 = line("picture_0", "the word under a column for picture 0 (enum_1546 default, model 7752); words not found")
    val PICTURE_1 = line("picture_1", "the word under a column for picture 1 (model 20104); words not found")
    val PICTURE_2 = line("picture_2", "the word under a column for picture 2 (model 7751); words not found")
    val PICTURE_3 = line("picture_3", "the word under a column for picture 3 (model 7753); words not found")
}

/**
 * Capt' Arnav's chest (OSRS Wiki, Capt' Arnav and Capt' Arnav's Chest pages and his transcript, read 2026-11-19). He
 * comes as NPC 5426 (Talk-to, Dismiss) with a chest whose lock is too fiddly for him. The lock is the window
 * pirate_combilock (26): three columns of four pictures each (enum_1546), rolled by their up and down arrows, each with a
 * word underneath; Unlock with every middle picture matching its word opens it, for 125 coins, a gold ring, a gold
 * necklace or a gold bar (a quarter each). The columns are varbits pirate_combilock_left, _centre and _right on varp
 * macro_transmit, which the client turns as it is clicked (pirate_combilock_rotate_*: up adds 1, down 3, modulo 4) and
 * the server mirrors. Wrong once, he explains again and the window reopens; wrong twice, he leaves with nothing.
 *
 * Guessed: each column's word and starting picture at random, the words able to repeat; the laddie form for every
 * player. Left out: the columns' words (no source gives them; the keys are held empty).
 */
object CaptArnav : RandomEvent("capt_arnav", 5426) {
    const val INTERFACE = 26
    val UP = listOf(5, 8, 11)
    val DOWN = listOf(6, 9, 12)
    val TEXTS = listOf(22, 23, 24)
    const val CONFIRM = 25
    const val PICTURES = 4

    /** pirate_combilock_left, _centre and _right. */
    val COLUMN_VARBITS = listOf(9585, 9593, 9594)

    const val COINS = 995
    const val COIN_COUNT = 125
    const val GOLD_RING = 1635
    const val GOLD_NECKLACE = 1654
    const val GOLD_BAR = 2357

    private val WORDS = with(CaptArnavLines) { listOf(PICTURE_0, PICTURE_1, PICTURE_2, PICTURE_3) }

    override val greeting = CaptArnavLines.AVAST
    override val nags = with(CaptArnavLines) { listOf(AHOY, PARLEY, NEED_HELP, TREASURE, IGNORING, OLD_PIRATE, KNOWS) }
    override val leaving = CaptArnavLines.MAYBE_LATER
    override val notYours = CaptArnavLines.NOT_THE_PERSON

    override suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean = with(CaptArnavLines) {
        chatNpc(ChatAnim.HAPPY, DUG_UP)
        chatNpc(ChatAnim.NEUTRAL, FIDDLY)
        if (choice(YES, NO) != 1) {
            chatPlayer(ChatAnim.NEUTRAL, NO)
            return true
        }
        chatPlayer(ChatAnim.HAPPY, YES)
        chatNpc(ChatAnim.NEUTRAL, COLUMNS)
        val wanted = List(3) { world.random.nextInt(PICTURES) }
        val columns = IntArray(3) { world.random.nextInt(PICTURES) }
        for (attempt in 1..2) {
            delay(1)
            open(world, player, wanted, columns)
            while (true) {
                val (picked, _) = context.pauseButtons(INTERFACE, UP + DOWN + CONFIRM, -1..-1)
                if (picked == CONFIRM) break
                val up = UP.indexOf(picked)
                val column = if (up >= 0) up else DOWN.indexOf(picked)
                columns[column] = (columns[column] + if (up >= 0) 1 else PICTURES - 1) % PICTURES
                setVarbit(player, COLUMN_VARBITS[column], columns[column])
            }
            world.interfaces.closeModals(player)
            if ((0 until 3).all { columns[it] == wanted[it] }) {
                // The reward, and him marked as done, before his line: shutting it neither loses nor repeats it.
                reward(world, events)
                events.claimed(player)
                chatNpc(ChatAnim.HAPPY, WELL_DONE)
                return true
            }
            if (attempt == 2) break
            chatNpc(ChatAnim.ANGRY, NOWHERE_NEAR)
            chatPlayer(ChatAnim.SAD, MISUNDERSTOOD)
            chatNpc(ChatAnim.NEUTRAL, ARROWS)
            chatNpc(ChatAnim.NEUTRAL, MIDDLE_ROW)
            chatNpc(ChatAnim.QUIZ, CLEARER)
            chatPlayer(ChatAnim.NEUTRAL, TRY_AGAIN)
        }
        // Done before his line, so shutting it gives no third try.
        events.claimed(player)
        chatNpc(ChatAnim.ANGRY, SOMEONE_ELSE)
        true
    }

    private fun Dialogue.open(world: World, player: Player, wanted: List<Int>, columns: IntArray) {
        for (i in 0 until 3) setVarbit(player, COLUMN_VARBITS[i], columns[i])
        world.interfaces.sendVarpNow(player, MACRO_TRANSMIT)
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, INTERFACE)
        for (i in 0 until 3) player.session.queue(IfSetText(INTERFACE, TEXTS[i], text(WORDS[wanted[i]])))
        player.session.queue(IfSetEventsV2(INTERFACE, CONFIRM, -1, -1, 0, BankScreen.opMask(1..1)))
    }

    private fun Dialogue.reward(world: World, events: RandomEvents) {
        when (world.random.nextInt(4)) {
            0 -> events.reward(player, COINS, COIN_COUNT)
            1 -> events.reward(player, GOLD_RING)
            2 -> events.reward(player, GOLD_NECKLACE)
            else -> events.reward(player, GOLD_BAR)
        }
    }

    private fun setVarbit(player: Player, id: Int, value: Int) {
        if (player.vars.types.varbit(id) != null) player.vars.setVarbit(id, value)
    }

    /** macro_transmit. */
    const val MACRO_TRANSMIT = 531

    /** Interface and component names in the rev 242 cache, for [osrsps.cache.GameFrameCheck]. */
    val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>>
        get() = mapOf(
            INTERFACE to (
                "pirate_combilock" to mapOf(
                    5 to "left_up", 6 to "left_down", 8 to "centre_up", 9 to "centre_down", 11 to "right_up", 12 to "right_down",
                    22 to "left_text", 23 to "centre_text", 24 to "right_text", CONFIRM to "confirm",
                )
                ),
        )
}
