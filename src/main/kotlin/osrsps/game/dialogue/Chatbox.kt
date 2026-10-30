package osrsps.game.dialogue

/**
 * The chatbox interfaces dialogues open into chatbox:chatmodal, and their components. Every id here was read by name
 * from the live 241 cache's gameval index (cache of 2026-10-14); [CACHE_NAMES] lets [osrsps.cache.GameFrameCheck] repeat
 * that check on every server start, so a revision that moves one shows in the log.
 *
 * Client scripts were checked against the same cache for their argument counts only: what each does is from the
 * RSMod project's packet captures (rev 233), not from our own recordings.
 */
object Chatbox {
    /** chatbox, the overlay in the frame's chat container, and its chatmodal, where dialogues open. */
    const val CHATBOX = 162
    const val CHATMODAL = 568

    /** chat_left: an NPC talking, head on the left. */
    const val CHAT_LEFT = 231

    /** chat_right: the player talking, head on the right. Its components have the same ids as chat_left's. */
    const val CHAT_RIGHT = 217
    const val CHAT_HEAD = 2
    const val CHAT_NAME = 4
    const val CHAT_CONTINUE = 5
    const val CHAT_TEXT = 6

    /** chatmenu: "Select an option" and up to five options, the slots 1 up of its options component. */
    const val CHATMENU = 219
    const val CHATMENU_OPTIONS = 1

    /** messagebox: plain text with no head. */
    const val MESSAGEBOX = 229
    const val MESSAGEBOX_TEXT = 3
    const val MESSAGEBOX_CONTINUE = 4

    /** objectbox: an item and text. Script [OBJBOX_SET_BUTTONS] makes its continue button in universe, slots 0 and 1. */
    const val OBJECTBOX = 193
    const val OBJECTBOX_UNIVERSE = 0
    const val OBJECTBOX_ITEM = 1
    const val OBJECTBOX_TEXT = 2

    /** objectbox_double: two items and text. */
    const val OBJECTBOX_DOUBLE = 11
    const val OBJECTBOX_DOUBLE_MODEL1 = 1
    const val OBJECTBOX_DOUBLE_TEXT = 2
    const val OBJECTBOX_DOUBLE_MODEL2 = 3
    const val OBJECTBOX_DOUBLE_PAUSEBUTTON = 4

    /**
     * skillmulti: the Make-X box, "how many of which?". Script [SKILLMULTI_SETUP_SCRIPT] lays it out; item buttons a to r
     * (components [SKILLMULTI_FIRST_ITEM] up) send resume_pausebutton with the quantity chosen as the slot.
     */
    const val SKILLMULTI = 270
    const val SKILLMULTI_FIRST_ITEM = 15
    const val SKILLMULTI_MAX_ITEMS = 18
    const val SKILLMULTI_MAX_QUANTITY = 28

    /**
     * skillmulti_setup: mode, title, the most that can be made, 18 objs (-1 for none) and the quantity to start on.
     * Mode 0 shows the quantity row with exact amounts (enums 5178 and 1810 in the 241 cache).
     */
    const val SKILLMULTI_SETUP_SCRIPT = 2046
    const val SKILLMULTI_MODE_MAKE = 0

    /** Varbit chatmodal_unclamp (10670): 0 keeps the chatmodal to the chatbox's size, 1 lets it grow (RSMod's names). */
    const val VARBIT_CHATMODAL_UNCLAMP = 10670
    const val UNCLAMP_FIXED = 0
    const val UNCLAMP_INFINITE = 1

    /** No arguments; resets the chatbox background before a dialogue opens (RSMod's toplevel_chatbox_resetbackground). */
    const val RESET_BACKGROUND_SCRIPT = 2379

    /** Takes alignH, alignV, line height and the packed text component: lays the dialogue's lines out. */
    const val TEXT_ALIGN_SCRIPT = 600

    /** Takes the title and the options joined by `|` (RSMod's chatbox_multi_init). */
    const val MULTI_INIT_SCRIPT = 58

    /** Takes the continue text; builds objectbox's continue button (RSMod's objbox_setbuttons). */
    const val OBJBOX_SET_BUTTONS = 2868

    /** Takes one string, which RSMod always sends as "0", after a messagebox. What it does isn't known to us. */
    const val MESSAGEBOX_SCRIPT = 1508

    /** The interface event bit that lets the client send resume_pausebutton for a component. */
    const val EVENT_PAUSE_BUTTON = 1

    /** RuneLite's ChatMessageType DIALOG and MESBOX: each dialogue page also goes to the chat history as one of these. */
    const val CHATTYPE_DIALOG = 114
    const val CHATTYPE_MESBOX = 115

    const val CONTINUE_TEXT = "Click here to continue"
    const val SELECT_AN_OPTION = "Select an option"

    /** The names [GameFrameCheck][osrsps.cache.GameFrameCheck] checks the ids above against. */
    val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>> = mapOf(
        CHATBOX to ("chatbox" to mapOf(CHATMODAL to "chatmodal")),
        CHAT_LEFT to ("chat_left" to chatNames()),
        CHAT_RIGHT to ("chat_right" to chatNames()),
        CHATMENU to ("chatmenu" to mapOf(CHATMENU_OPTIONS to "options")),
        MESSAGEBOX to ("messagebox" to mapOf(MESSAGEBOX_TEXT to "text", MESSAGEBOX_CONTINUE to "continue")),
        OBJECTBOX to ("objectbox" to mapOf(OBJECTBOX_UNIVERSE to "universe", OBJECTBOX_ITEM to "item", OBJECTBOX_TEXT to "text")),
        SKILLMULTI to ("skillmulti" to mapOf(SKILLMULTI_FIRST_ITEM to "a", SKILLMULTI_FIRST_ITEM + SKILLMULTI_MAX_ITEMS - 1 to "r")),
        OBJECTBOX_DOUBLE to (
            "objectbox_double" to mapOf(
                OBJECTBOX_DOUBLE_MODEL1 to "model1", OBJECTBOX_DOUBLE_TEXT to "text", OBJECTBOX_DOUBLE_MODEL2 to "model2",
                OBJECTBOX_DOUBLE_PAUSEBUTTON to "pausebutton",
            )
        ),
    )

    private fun chatNames() = mapOf(CHAT_HEAD to "head", CHAT_NAME to "name", CHAT_CONTINUE to "continue", CHAT_TEXT to "text")
}
