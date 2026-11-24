package osrsps.game.content.randomevents

import net.rsprot.protocol.game.outgoing.interfaces.IfSetHide
import osrsps.game.FrameSlot
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** The Sandwich lady's lines; the words are in the dialogue file, from her transcript (read 2026-11-19). */
object SandwichLadyLines : Lines("sandwich_lady", "Transcript:Sandwich lady") {
    val GREETING = line("greeting", "Overhead dialogue: upon spawning in for the player", "name")
    val DID_YOU_HEAR = line("did_you_hear", "Overhead dialogue: while waiting, dialogue 1", "name")
    val DELIVERY = line("delivery", "Overhead dialogue: while waiting, dialogue 2", "name")
    val FOR_FUN = line("for_fun", "Overhead dialogue: while waiting, dialogue 3")
    val ALL_TYPES = line("all_types", "Overhead dialogue: while waiting, dialogue 4", "name")
    val SPECIALLY = line("specially", "Overhead dialogue: while waiting, dialogue 5", "name")
    val MAYBE_LATER = line("maybe_later", "Overhead dialogue: if the player does not respond in time")
    val OFFER = line("offer", "Standard dialogue: her offer, {food} the food's name in lower case", "food")
    val FILLS_YOU_UP = line("fills_you_up", "Selecting a food: the correct one")
    val DIDNT_SAY = line("didnt_say", "Selecting a food: an incorrect one")
    val NOT_YOU = line("not_you", "Other people's Sandwich lady", "name")
}

/**
 * The Sandwich lady (NPC 5510, macro_sandwich_lady_npc; Talk-to, Dismiss), from the OSRS Wiki's Sandwich lady page and
 * transcript (read 2026-11-19): she names one of her seven refreshments, each 1 in 7, and opens her tray
 * (sandwich_tray, interface 297); taking that one gives it (a baguette is stale 1 time in 64), any other gets nothing.
 * The tray's seven buttons always hold the same food (the cache's models: baguette, triangle sandwich, square
 * sandwich, roll, meat pie, kebab, chocolate bar); the client only turns them. Key shows the names and Back hides them.
 *
 * Guessed: Key and Back swap the two layers on the server (the buttons transmit to it); closing the tray ends the talk
 * with no food, and she stays until her minute is up; chathead expressions; that she goes at once after.
 */
object SandwichLady : RandomEvent("sandwich_lady", 5510) {
    const val INTERFACE = 297
    const val KEY_LAYER = 4
    const val TRAY_LAYER = 5
    const val KEY_BUTTON = 13
    const val BACK_BUTTON = 30

    /** The tray's buttons, sandwich_tray_0 to 6, and the food each holds. */
    val TRAY: List<Pair<Int, Int>> = listOf(
        6 to 6961, // baguette
        7 to 6962, // triangle_sandwich
        8 to 6965, // square_sandwich
        9 to 6963, // roll
        10 to 2327, // meat_pie
        11 to 1971, // kebab
        12 to 1973, // chocolate_bar
    )

    const val BAGUETTE = 6961
    const val STALE_BAGUETTE = 20590
    const val STALE_CHANCE = 64

    override val greeting = SandwichLadyLines.GREETING
    override val nags = with(SandwichLadyLines) { listOf(DID_YOU_HEAR, DELIVERY, FOR_FUN, ALL_TYPES, SPECIALLY) }
    override val leaving = SandwichLadyLines.MAYBE_LATER
    override val notYours = SandwichLadyLines.NOT_YOU

    override suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean {
        val (_, offered) = TRAY[world.random.nextInt(TRAY.size)]
        val name = (world.objTypes[offered]?.name ?: "sandwich").lowercase()
        chatNpc(ChatAnim.HAPPY, SandwichLadyLines.OFFER, "food" to name)
        // A tick's wait: the continue's own handling closes the chatbox, and with it a script already waiting again.
        delay(1)
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, INTERFACE)
        val buttons = TRAY.map { it.first } + KEY_BUTTON + BACK_BUTTON
        var picked: Int
        while (true) {
            picked = context.pauseButtons(INTERFACE, buttons, -1..-1).first
            when (picked) {
                KEY_BUTTON -> showKey(true)
                BACK_BUTTON -> showKey(false)
                else -> break
            }
        }
        world.interfaces.closeModals(player)
        val food = TRAY.first { it.first == picked }.second
        // Picked: the food (if right) and the end come before her line, so shutting it can't give another pick.
        if (food == offered) {
            val stale = food == BAGUETTE && world.random.nextInt(STALE_CHANCE) == 0
            events.reward(player, if (stale) STALE_BAGUETTE else food)
        }
        events.claimed(player)
        if (food == offered) chatNpc(ChatAnim.HAPPY, SandwichLadyLines.FILLS_YOU_UP) else chatNpc(ChatAnim.ANGRY, SandwichLadyLines.DIDNT_SAY)
        return true
    }

    private fun Dialogue.showKey(show: Boolean) {
        player.session.queue(IfSetHide(INTERFACE, KEY_LAYER, !show))
        player.session.queue(IfSetHide(INTERFACE, TRAY_LAYER, show))
    }

    /** Interface and component names in the rev 242 cache, for [osrsps.cache.GameFrameCheck]. */
    val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>>
        get() = mapOf(
            INTERFACE to (
                "sandwich_tray" to mapOf(
                    KEY_LAYER to "sandwich_key", TRAY_LAYER to "sandwhich_refreshment_layer", KEY_BUTTON to "sandwich_key_button",
                    BACK_BUTTON to "sandwich_back_button",
                ) + TRAY.mapIndexed { i, (com, _) -> com to "sandwich_tray_$i" }
                ),
        )
}
