package osrsps.game.content.randomevents

import osrsps.game.World
import osrsps.game.content.XpLamp
import osrsps.game.content.message
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** Count Check's lines as a random event; the words are in the dialogue file, from his transcript (read 2026-11-19). */
object CountCheckEventLines : Lines("count_check_event", "Transcript:Count Check (random event)") {
    val GREETING = line("greeting", "Overhead dialogue: upon spawning in for the player", "name")
    val LET_ME_CHECK = line("let_me_check", "Overhead dialogue: while waiting, dialogue 1", "name")
    val COME_TO_CHECK = line("come_to_check", "Overhead dialogue: while waiting, dialogue 2", "name")
    val IGNORED = line("ignored", "Overhead dialogue: if the player does not respond in time")
    val PASS = line("pass", "Standard dialogue: the player has a Jagex Account and a bank PIN")
    val NOT_YOU = line("not_you", "Other player's event", "name")
}

/**
 * Count Check as a random event (NPC 12551, macro_countcheck_surface; Talk-to, Dismiss), from the OSRS Wiki's Count
 * Check (random event) page and transcript (read 2026-11-19): he checks the account's security and, passed, gives a
 * lamp ([XpLamp]). He is one of the three event NPCs that follow their player into a bank.
 *
 * Inferred: every account here passes, as the plan says (no Jagex Accounts here, and a bank PIN is the player's
 * business), so only his passing words are used. Guessed: his chathead expression, and that he goes at once after the
 * lamp. Left out: his third waiting line (the transcript has it, but with no words), and the underwater Count (12552).
 * Not this: the Count Check in Lumbridge (Church.kt), who keeps the `count_check` keys.
 */
object CountCheckEvent : RandomEvent("count_check", 12551) {
    override val entersBanks = true
    override val greeting = CountCheckEventLines.GREETING
    override val nags = listOf(CountCheckEventLines.LET_ME_CHECK, CountCheckEventLines.COME_TO_CHECK)
    override val leaving = CountCheckEventLines.IGNORED
    override val notYours = CountCheckEventLines.NOT_YOU

    override suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean {
        chatNpc(ChatAnim.LAUGH, CountCheckEventLines.PASS)
        events.reward(player, XpLamp.LAMP)
        return true
    }
}

/** The Drunken Dwarf's lines; the words are in the dialogue file, from his transcript (read 2026-11-19). */
object DrunkenDwarfLines : Lines("drunken_dwarf", "Transcript:Drunken Dwarf") {
    val GREETING = line("greeting", "Overhead dialogue: upon spawning in for the player", "name")
    val HEY = line("hey", "Overhead dialogue: while waiting, dialogue 1", "name")
    val ARE_YOU_DER = line("are_you_der", "Overhead dialogue: while waiting, dialogue 2", "name")
    val DUNT_IGNORE = line("dunt_ignore", "Overhead dialogue: while waiting, dialogue 3", "name")
    val GOT_SOMETHING = line("got_something", "Overhead dialogue: while waiting, dialogue 4", "name")
    val TALK_TO_IKLE_ME = line("talk_to_ikle_me", "Overhead dialogue: while waiting, dialogue 5", "name")
    val DOESNT_LIKE_ME = line("doesnt_like_me", "Overhead dialogue: if the player does not respond in time")
    val KEBAB_STORIES = line("kebab_stories", "Standard dialogue: the player has completed the Forgettable Tale... quest")
    val NOT_MY_MATEY = line("not_my_matey", "Other people's Drunken Dwarf")
}

/**
 * The Drunken Dwarf (NPC 322, macro_dwarf; Talk-to, Dismiss), from the OSRS Wiki's Drunken Dwarf page and transcript
 * (read 2026-11-19): Talk-to gives a kebab and a beer. Every account here has every quest, so he remembers the
 * player's kebab stories (Forgettable Tale...). Guessed: his chathead expression (drunk), and that he goes at once.
 */
object DrunkenDwarf : RandomEvent("drunken_dwarf", 322) {
    const val KEBAB = 1971
    const val BEER = 1917

    override val greeting = DrunkenDwarfLines.GREETING
    override val nags = with(DrunkenDwarfLines) { listOf(HEY, ARE_YOU_DER, DUNT_IGNORE, GOT_SOMETHING, TALK_TO_IKLE_ME) }
    override val leaving = DrunkenDwarfLines.DOESNT_LIKE_ME
    override val notYours = DrunkenDwarfLines.NOT_MY_MATEY

    override suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean {
        chatNpc(ChatAnim.DRUNK, DrunkenDwarfLines.KEBAB_STORIES)
        events.reward(player, KEBAB)
        events.reward(player, BEER)
        return true
    }
}

/** Rick Turpentine's lines; the words are in the dialogue file, from his transcript (read 2026-11-19). */
object RickTurpentineLines : Lines("rick_turpentine", "Transcript:Rick Turpentine") {
    val GREETING = line("greeting", "Overhead dialogue: upon spawning in for the player", "name")
    val A_MOMENT = line("a_moment", "Overhead dialogue: while waiting, dialogue 1")
    val SPEAK_TO_ME = line("speak_to_me", "Overhead dialogue: while waiting, dialogue 2", "name")
    val MAYBE_LATER = line("maybe_later", "Overhead dialogue: if the player does not respond in time")
    val LUCKY_DAY = line("lucky_day", "Standard dialogue")
    val SOMEONE_ELSE = line("someone_else", "Talking to another player's Rick Turpentine")
}

/**
 * Rick Turpentine (NPC 375, macro_highwayman; Talk-to, Dismiss), from the OSRS Wiki's Rick Turpentine page and
 * transcript (read 2026-11-19): Talk-to gives one of [Gifts]. The words say sirrah, milord and the like by the player's
 * look; the words file holds the one form, as the body type isn't kept here. Guessed: his chathead expression, and
 * that he goes at once.
 */
object RickTurpentine : RandomEvent("rick_turpentine", 375) {
    override val greeting = RickTurpentineLines.GREETING
    override val nags = listOf(RickTurpentineLines.A_MOMENT, RickTurpentineLines.SPEAK_TO_ME)
    override val leaving = RickTurpentineLines.MAYBE_LATER
    override val notYours = RickTurpentineLines.SOMEONE_ELSE

    override suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean {
        chatNpc(ChatAnim.HAPPY, RickTurpentineLines.LUCKY_DAY)
        val gift = Gifts.roll(world)
        events.reward(player, gift.id, gift.count)
        return true
    }
}

/** The Mysterious Old Man's lines; the words are in the dialogue file, from his transcript (read 2026-11-19). */
object MysteriousOldManLines : Lines("mysterious_old_man", "Transcript:Mysterious Old Man") {
    val NAME = line("name", "Random event overhead dialogue: dialogue 1", "name")
    val HELLO = line("hello", "Random event overhead dialogue: dialogue 2", "name")
    val TALK_TO_ME = line("talk_to_me", "Random event overhead dialogue: dialogue 3", "name")
    val ARE_YOU_THERE = line("are_you_there", "Random event overhead dialogue: dialogue 4", "name")
    val LIKE_TO_TALK = line("like_to_talk", "Random event overhead dialogue: dialogue 5", "name")
    val MAYBE_LATER = line("maybe_later", "Random event overhead dialogue: if the player does not respond in time")
    val PRESENT = line("present", "Standard dialogue: hand out item version")
    val ON_GROUND = line("on_ground", "Hand out item version: the message without enough inventory space", "item")
    val SOMEONE_ELSE = line("someone_else", "If the event is not yours")
}

/**
 * The Mysterious Old Man's gift (NPC 6750, macro_mysterious_old_man; Talk-to, Dismiss), from the OSRS Wiki's page and
 * transcript (read 2026-11-19): lonely, he gives one of [Gifts], with a message when it has to go on the ground. His
 * other two forms, which take the player to the Maze and the Mime, come with those events. Live picks one of his five
 * waiting lines for his first words too, as here. Guessed: his chathead expression; that he goes at once; the
 * message's item as "<count> x <name>" for more than one (the transcript shows only the reward's name).
 */
object MysteriousOldMan : RandomEvent("mysterious_old_man", 6750) {
    override val nags = with(MysteriousOldManLines) { listOf(NAME, HELLO, TALK_TO_ME, ARE_YOU_THERE, LIKE_TO_TALK) }
    override val leaving = MysteriousOldManLines.MAYBE_LATER
    override val notYours = MysteriousOldManLines.SOMEONE_ELSE

    override suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean {
        chatNpc(ChatAnim.HAPPY, MysteriousOldManLines.PRESENT)
        val gift = Gifts.roll(world)
        if (!events.reward(player, gift.id, gift.count)) {
            val name = world.objTypes[gift.id]?.name ?: "?"
            world.message(player, MysteriousOldManLines.ON_GROUND, "item" to if (gift.count > 1) "${gift.count} x $name" else name)
        }
        return true
    }
}
