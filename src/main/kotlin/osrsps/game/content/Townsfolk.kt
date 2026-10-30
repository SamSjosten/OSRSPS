package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.GameRandom
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

private val logger = KotlinLogging.logger {}

/**
 * The lines of a Man's or a Woman's Talk-to: one of 23 short conversations, the same in shape for both, each from its
 * own transcript's "Standard dialogue" (read 2026-10-25). The numbers are the transcript's.
 */
abstract class TownsfolkLines(prefix: String, page: String, who: String) : Lines(prefix, page) {
    val HELLO = line("hello", "the player's greeting, which opens every one")
    val GOBLINS = line("goblins", "dialogue 1: the $who's line")
    val GOBLINS_REPLY = line("goblins_reply", "dialogue 1: the player's answer")
    val HELP = line("help", "dialogue 2: the $who's question")
    val TRADE = line("trade", "dialogue 2, option 1 (also dialogue 15): what the player asks")
    val NO_TRADE = line("no_trade", "dialogue 2, option 1 (also dialogue 15): the $who's answer")
    val QUEST = line("quest", "dialogue 2, option 2 (also dialogue 16): what the player asks")
    val NO_QUEST = line("no_quest", "dialogue 2, option 2 (also dialogue 16): the $who's answer")
    val ENEMIES = line("enemies", "dialogue 2, option 3 (also dialogue 17): what the player asks")
    val UNDERGROUND = line("underground", "dialogue 2, option 3 (also dialogue 17): the $who's answer")
    val OUT_OF_THE_WAY = line("out_of_the_way", "dialogue 3: the $who's line")
    val FINE = line("fine", "dialogue 4: the $who's line")
    val FINE_REPLY = line("fine_reply", "dialogue 4: the player's answer")
    val WEATHER = line("weather", "dialogue 5: the $who's line")
    val VERY_WELL = line("very_well", "dialogue 6: the $who's line")
    val WHO_ARE_YOU = line("who_are_you", "dialogue 7: the $who's question")
    val ADVENTURER = line("adventurer", "dialogue 7: the player's answer")
    val NOBLE = line("noble", "dialogue 7: the $who's last line")
    val HURRY = line("hurry", "dialogue 8: the $who's line")
    val NEW_KING = line("new_king", "dialogue 9: the $who's line")
    val NOT_BAD = line("not_bad", "dialogue 10: the $who's line")
    val FIGHT = line("fight", "dialogue 11: the $who's line")
    val BUSY = line("busy", "dialogue 12: the $who's line")
    val GREETING = line("greeting", "dialogue 13: the $who's line")
    val MIND_YOUR_BUSINESS = line("mind_your_business", "dialogue 14: the $who's line")
    val NO_CHANGE = line("no_change", "dialogue 18: the $who's line")
    val KILLINGS = line("killings", "dialogue 19: the $who's line")
    val WONT_BUY = line("wont_buy", "dialogue 20: the $who's line")
    val CLASSIFIED = line("classified", "dialogue 21: the $who's line")
    val FLIER = line("flier", "dialogue 22: the $who's line, with the flier")
    val CASUAL = line("casual", "dialogue 23: the $who's line")
}

object ManLines : TownsfolkLines("man", "Transcript:Man", "Man")

object WomanLines : TownsfolkLines("woman", "Transcript:Woman", "Woman")

/**
 * Men and women: Talk-to is one of the transcript's 23 conversations. From the Wiki: the conversations, and that one
 * talk in 128 offers a flier (the Flier page). Guessed: that the other 22 are equally likely, every chathead expression,
 * and that the flier goes in before the line (at the player's feet with a full inventory). Dialogue 11's man or woman
 * attacks the player on live; with no combat yet (Sam, 2026-10-25), they only say their line.
 */
object Townsfolk {
    /**
     * Man (man, man2, man3, man_indoor) and Woman (woman, woman2, woman3), the ones in Lumbridge, and Varrock's own:
     * varrock_man1 (3014, the Man page's Blue Moon Inn version) and varrock_woman1 (3015, taken to be the Woman page's
     * match, guessed).
     */
    val MEN = listOf(3014, 3106, 3107, 3108, 6818)
    val WOMEN = listOf(3015, 3111, 3112, 3113)
    const val TALK_TO = 1

    /** Flier (`flier`), which advertises Bob's Axes. */
    const val FLIER = 956

    /** One talk in this many offers a flier (Wiki, Flier). */
    const val FLIER_CHANCE = 128

    /** Dialogues 1 to 23 but 22 (the flier). */
    private val OTHERS = (1..23).filter { it != 22 }

    /** Which of the 23 conversations a talk has, numbered as in the transcript. */
    fun pick(random: GameRandom): Int = if (random.nextInt(FLIER_CHANCE) == 0) 22 else random.pick(OTHERS)

    internal suspend fun Dialogue.talk(world: World, lines: TownsfolkLines, dialogue: Int = pick(world.random)) = with(lines) {
        chatPlayer(ChatAnim.HAPPY, HELLO)
        when (dialogue) {
            1 -> {
                chatNpc(ChatAnim.SAD, GOBLINS)
                chatPlayer(ChatAnim.NEUTRAL, GOBLINS_REPLY)
            }
            2 -> {
                chatNpc(ChatAnim.HAPPY, HELP)
                when (choice(TRADE, QUEST, ENEMIES)) {
                    1 -> askTrade(lines)
                    2 -> askQuest(lines)
                    else -> askEnemies(lines)
                }
            }
            3 -> chatNpc(ChatAnim.ANGRY, OUT_OF_THE_WAY)
            4 -> {
                chatNpc(ChatAnim.HAPPY, FINE)
                chatPlayer(ChatAnim.HAPPY, FINE_REPLY)
            }
            5 -> chatNpc(ChatAnim.HAPPY, WEATHER)
            6 -> chatNpc(ChatAnim.HAPPY, VERY_WELL)
            7 -> {
                chatNpc(ChatAnim.QUIZ, WHO_ARE_YOU)
                chatPlayer(ChatAnim.NEUTRAL, ADVENTURER)
                chatNpc(ChatAnim.NEUTRAL, NOBLE)
            }
            8 -> chatNpc(ChatAnim.CONFUSED, HURRY)
            9 -> chatNpc(ChatAnim.NEUTRAL, NEW_KING)
            10 -> chatNpc(ChatAnim.NEUTRAL, NOT_BAD)
            11 -> chatNpc(ChatAnim.ANGRY, FIGHT)
            12 -> chatNpc(ChatAnim.NEUTRAL, BUSY)
            13 -> chatNpc(ChatAnim.HAPPY, GREETING)
            14 -> chatNpc(ChatAnim.ANGRY, MIND_YOUR_BUSINESS)
            15 -> askTrade(lines)
            16 -> askQuest(lines)
            17 -> askEnemies(lines)
            18 -> chatNpc(ChatAnim.NEUTRAL, NO_CHANGE)
            19 -> chatNpc(ChatAnim.SAD, KILLINGS)
            20 -> chatNpc(ChatAnim.NEUTRAL, WONT_BUY)
            21 -> chatNpc(ChatAnim.SHIFTY, CLASSIFIED)
            22 -> {
                giveFlier(world)
                chatNpc(ChatAnim.HAPPY, FLIER)
            }
            else -> chatNpc(ChatAnim.HAPPY, CASUAL)
        }
    }

    private suspend fun Dialogue.askTrade(lines: TownsfolkLines) {
        chatPlayer(ChatAnim.QUIZ, lines.TRADE)
        chatNpc(ChatAnim.NEUTRAL, lines.NO_TRADE)
    }

    private suspend fun Dialogue.askQuest(lines: TownsfolkLines) {
        chatPlayer(ChatAnim.QUIZ, lines.QUEST)
        chatNpc(ChatAnim.NEUTRAL, lines.NO_QUEST)
    }

    private suspend fun Dialogue.askEnemies(lines: TownsfolkLines) {
        chatPlayer(ChatAnim.QUIZ, lines.ENEMIES)
        chatNpc(ChatAnim.NEUTRAL, lines.UNDERGROUND)
    }

    private fun Dialogue.giveFlier(world: World) {
        world.objTypes[FLIER]?.let { world.groundObjs.give(player, it, 1) } ?: logger.warn { "No flier in the cache" }
    }
}

/** Men's and women's Talk-to. Attack and Pickpocket do nothing yet. */
fun registerTownsfolk(world: World) {
    for ((ids, lines) in listOf(Townsfolk.MEN to ManLines, Townsfolk.WOMEN to WomanLines)) {
        for (id in ids) {
            world.handlers.onNpc(id, Townsfolk.TALK_TO) { op ->
                world.dialogues.start(this, op.npc) { with(Townsfolk) { talk(world, lines) } }
            }
        }
    }
}
