package osrsps.game.content

import osrsps.game.GameRandom
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

/**
 * The lines of Donie's or Gee's Talk-to, the same in shape for both, each from its own transcript's "Standard dialogue"
 * (read 2026-10-29). Every quest is done here, so only the "has completed" branches are keyed. An option the player
 * then says has one key for both.
 */
abstract class TownGuideLines(prefix: String, page: String, who: String) : Lines(prefix, page) {
    val HELLO = line("hello", "$who's first line, before every one of the four random menus")

    val WHERE_AM_I = line("where_am_i", "Where am I?, which the player then says")
    val TOWN = line("town", "Where am I?: $who's answer")
    val HOW_ARE_YOU = line("how_are_you", "How are you today?, which the player then says")
    val NOT_BAD = line("not_bad", "How are you today?: $who's first line")
    val WEATHER = line("weather", "How are you today?: the player's one-word question")
    val WEATHER_YES = line("weather_yes", "How are you today?: $who's second line")
    val WEATHER_MEANS = line("weather_means", "How are you today?: $who's third line, what weather is")
    val DOTS = line("dots", "How are you today?: the player's silent line")
    val PRETTY_FACE = line("pretty_face", "How are you today?: $who's last line")
    val QUESTS = line("quests", "Are there any quests I can do here? (the option)")
    val KNOW_QUESTS = line("know_quests", "what the player says after that option; also the option after Where am I?")
    val SHOE_LACE = line("shoe_lace", "Your shoe lace is untied., which the player then says")
    val NOT_UNTIED = line("not_untied", "shoe lace: $who's first line")
    val YOURE_RIGHT = line("youre_right", "shoe lace: the player's second line")
    val FOOL = line("fool", "shoe lace: $who's last line")
    val HAIRCUT = line("haircut", "Where can I get a haircut like yours?, which the player then says")
    val HAIR_LOOKS = line("hair_looks", "haircut: $who's first line")
    val OH_THANKS = line("oh_thanks", "haircut: the player's line")
    val HAIRDRESSER = line("hairdresser", "haircut: $who's second line, the hairdresser")
    val GENERAL_STORE = line("general_store", "haircut: $who's last line, the general store")
    val WHATS_UP = line("whats_up", "What's up?, which the player then says")
    val SKY = line("sky", "What's up?: $who's first line")
    val ASSUME = line("assume", "What's up?: the player's line")
    val ASSUME_REPLY = line("assume_reply", "What's up?: $who's last line")
    val STICK = line("stick", "Can I buy your stick?, which the player then says")
    val NOT_A_STICK = line("not_a_stick", "stick: $who's first line")
    val SHOW_ME = line("show_me", "stick: the player's second line")
    val LOW = line("low", "stick: $who's second line")
    val IS_A_STICK = line("is_a_stick", "stick: the player's third line")
    val OK_STICK = line("ok_stick", "stick: $who's last line")
    val GOOD_LUCK = line("good_luck", "stick: the player's last line")
    val VALUE = line("value", "Do you have anything of value which I can have?, which the player then says")
    val ASKING = line("asking", "value: $who's first line")
    val ER_YES = line("er_yes", "value: the player's line")
    val NO_VALUE = line("no_value", "value: $who's last line")

    val WHAT_KIND = line("what_kind", "quests: $who's question before the kinds menu")
    val FIGHT = line("fight", "kinds menu, option 1 (no line from the player follows)")
    val DANGEROUS = line("dangerous", "fight: $who's line before the dangers menu")
    val DEMONS = line("demons", "Big scary demons!, which the player then says")
    val DEMONS_1 = line("demons_1", "demons: $who's first line")
    val DEMONS_2 = line("demons_2", "demons: $who's second line")
    val DEMONS_3 = line("demons_3", "demons: $who's third line")
    val DEMONS_DONE = line("demons_done", "demons, after Demon Slayer: the player's line")
    val DEMONS_WELL_DONE = line("demons_well_done", "demons, after Demon Slayer: $who's line")
    val VAMPYRES = line("vampyres", "Vampyres!, which the player then says")
    val VAMPYRES_1 = line("vampyres_1", "vampyres: $who's first line")
    val VAMPYRES_2 = line("vampyres_2", "vampyres: $who's second line, Morgan")
    val VAMPYRES_DONE = line("vampyres_done", "vampyres, after Vampyre Slayer: the player's first line")
    val YEAH_YEAH = line("yeah_yeah", "vampyres, after Vampyre Slayer: $who's first line")
    val I_DID = line("i_did", "vampyres, after Vampyre Slayer: the player's second line")
    val NOT_FOOLING = line("not_fooling", "vampyres, after Vampyre Slayer: $who's second line")
    val HUH = line("huh", "vampyres, after Vampyre Slayer: the player's last line")
    val SMALL = line("small", "Small.. something small would be good., which the player then says")
    val SMALL_ISNT = line("small_isnt", "small: $who's first line")
    val YES_IT_CAN = line("yes_it_can", "small: the player's second line")
    val POINT_TAKEN = line("point_taken", "small: $who's second line")
    val IMPS = line("imps", "small: $who's last line before the quest check")
    val IMPS_DONE = line("imps_done", "small, after Imp Catcher: the player's line")
    val IMPS_WILL_BE = line("imps_will_be", "small, after Imp Catcher: $who's line")
    val ANOTHER_TIME = line("another_time", "Maybe another time., which the player then says, in every menu that has it")

    val EASY = line("easy", "Something easy please, I'm new here., which the player then says")
    val EASY_1 = line("easy_1", "easy: $who's first line")
    val EASY_2 = line("easy_2", "easy: $who's second line, the Lumbridge cook")
    val TELL_ME_ABOUT = line("tell_me_about", "the title of the Tell me about.. menus")
    val COOK = line("cook", "Tell me about..: The Lumbridge cook.")
    val COOK_SAID = line("cook_said", "the cook: what the player says")
    val COOK_1 = line("cook_1", "the cook: $who's first line")
    val COOK_2 = line("cook_2", "the cook: $who's second line")
    val COOK_DONE = line("cook_done", "the cook, after Cook's Assistant: the player's line")
    val COOK_OH_YES = line("cook_oh_yes", "the cook, after Cook's Assistant: $who's line")
    val TALISMAN = line("talisman", "Tell me about..: The Duke's strange talisman.")
    val TALISMAN_SAID = line("talisman_said", "the talisman: what the player says")
    val TALISMAN_1 = line("talisman_1", "the talisman: $who's line")
    val TALISMAN_DONE = line("talisman_done", "the talisman, after Rune Mysteries: the player's line")
    val TALISMAN_THANKS = line("talisman_thanks", "the talisman, after Rune Mysteries: $who's line")
    val ROMEO = line("romeo", "Tell me about..: Romeo and his girlfriend.")
    val ROMEO_SAID = line("romeo_said", "Romeo: what the player says")
    val ROMEO_1 = line("romeo_1", "Romeo: $who's first line")
    val ROMEO_2 = line("romeo_2", "Romeo: $who's second line")
    val ROMEO_DONE = line("romeo_done", "Romeo, after Romeo & Juliet: the player's first line")
    val REALLY = line("really", "Romeo, after Romeo & Juliet: $who's first line")
    val YUP = line("yup", "Romeo, after Romeo & Juliet: the player's second line")
    val HOW = line("how", "Romeo, after Romeo & Juliet: $who's second line")
    val JULIET = line("juliet", "Romeo, after Romeo & Juliet: the player's third line")
    val WELL_OK = line("well_ok", "Romeo, after Romeo & Juliet: $who's last line")

    val THINKER = line("thinker", "I'm a thinker rather than fighter, anything skill oriented?, which the player then says")
    val SKILLS_1 = line("skills_1", "thinker: $who's first line")
    val SKILLS_2 = line("skills_2", "thinker: $who's second line")
    val SKILLS_3 = line("skills_3", "thinker: $who's third line, Doric")
    val FRED = line("fred", "Tell me about..: Fred the farmer.")
    val FRED_SAID = line("fred_said", "Fred: what the player says")
    val FRED_1 = line("fred_1", "Fred: $who's line")
    val FRED_DONE = line("fred_done", "Fred, after Sheep Shearer: the player's first line")
    val FRED_CHICKENS = line("fred_chickens", "Fred, after Sheep Shearer: the player's second line")
    val CHICKENS_1 = line("chickens_1", "Fred, after Sheep Shearer: $who's first line")
    val CHICKENS_2 = line("chickens_2", "Fred, after Sheep Shearer: $who's second line")
    val DORIC = line("doric", "Tell me about..: Doric the dwarf.")
    val DORIC_SAID = line("doric_said", "Doric: what the player says")
    val DORIC_1 = line("doric_1", "Doric: $who's line")
    val DORIC_DONE = line("doric_done", "Doric, after Doric's Quest: the player's line")
    val DORIC_THANKS = line("doric_thanks", "Doric, after Doric's Quest: $who's line")

    val ALL_KINDS = line("all_kinds", "I want to do all kinds of things, do you know of anything like that?, which the player then says")
    val ALL_1 = line("all_1", "all kinds: $who's first line")
    val ALL_2 = line("all_2", "all kinds: $who's second line, Hetty")
    val ALL_3 = line("all_3", "all kinds: $who's third line, pirates")
    val ALL_4 = line("all_4", "all kinds: $who's fourth line")
    val HETTY = line("hetty", "Tell me about..: Hetty the Witch.")
    val HETTY_SAID = line("hetty_said", "Hetty: what the player says")
    val HETTY_1 = line("hetty_1", "Hetty: $who's line")
    val HETTY_DONE = line("hetty_done", "Hetty, after Witch's Potion: the player's first line")
    val DID_SHE = line("did_she", "Hetty, after Witch's Potion: $who's first line")
    val NOT_REALLY = line("not_really", "Hetty, after Witch's Potion: the player's second line")
    val RIGHT = line("right", "Hetty, after Witch's Potion: $who's last line")
    val PIRATE = line("pirate", "Tell me about..: Pirate's treasure.")
    val PIRATE_SAID = line("pirate_said", "Pirate's treasure: what the player says")
    val PIRATE_1 = line("pirate_1", "Pirate's treasure: $who's line")
    val PIRATE_DONE = line("pirate_done", "Pirate's treasure, after Pirate's Treasure: the player's first line")
    val YARR_INDEED = line("yarr_indeed", "Pirate's treasure, after the quest: $who's first line")
    val YARR = line("yarr", "Pirate's treasure, after the quest: the player's second line")
    val YARRR = line("yarrr", "Pirate's treasure, after the quest: $who's second line")
    val YARRRR = line("yarrrr", "Pirate's treasure, after the quest: the player's third line, in capitals")
    val ENOUGH = line("enough", "Pirate's treasure, after the quest: $who's last line")
    val SORRY = line("sorry", "Pirate's treasure, after the quest: the player's last line")
    val ERNEST = line("ernest", "Tell me about..: Ernest and Draynor Manor.")
    val ERNEST_SAID = line("ernest_said", "Ernest: what the player says")
    val ERNEST_1 = line("ernest_1", "Ernest: $who's first line")
    val ERNEST_2 = line("ernest_2", "Ernest: $who's second line")
    val ERNEST_DONE = line("ernest_done", "Ernest, after Ernest the Chicken: the player's first line")
    val A_CHICKEN = line("a_chicken", "Ernest, after Ernest the Chicken: $who's first line")
    val YEAH_CHICKEN = line("yeah_chicken", "Ernest, after Ernest the Chicken: the player's second line")
    val POOR_GUY = line("poor_guy", "Ernest, after Ernest the Chicken: $who's last line")
}

object DonieLines : TownGuideLines("donie", "Transcript:Donie", "Donie")

/** Gee's are Donie's, with one more line about small monsters. */
object GeeLines : TownGuideLines("gee", "Transcript:Gee", "Gee") {
    val SMALL_MONSTERS = line("small_monsters", "small: Gee's third line, speaking of small monsters (Donie hasn't it)")
}

/**
 * Donie (921) and Gee (6816), who walk around Lumbridge. From the Wiki: the greeting, then one of four menus picked at
 * random, and every branch after its quest is done (every quest is, here). Expressions are RSMod's (rev 233 scripts),
 * which also has Vampyres! said with no expression. Guessed: that the four menus are equally likely, and that the
 * player says nothing after "I fancy a bit of a fight" (the Wiki has them answer straight away).
 */
object DonieAndGee {
    const val DONIE = 921
    const val GEE = 6816
    const val TALK_TO = 1

    /** The four menus, numbered as the transcripts' "Dialogue 1" to "Dialogue 4". */
    fun pick(random: GameRandom): Int = random.nextInt(4) + 1

    internal suspend fun Dialogue.talk(lines: TownGuideLines, dialogue: Int) = with(lines) {
        chatNpc(ChatAnim.HAPPY, HELLO)
        val menu = when (dialogue) {
            1 -> listOf(WHERE_AM_I, HOW_ARE_YOU, QUESTS, HAIRCUT)
            2 -> listOf(WHATS_UP, QUESTS, STICK)
            3 -> listOf(VALUE, QUESTS, STICK)
            else -> listOf(WHERE_AM_I, HOW_ARE_YOU, QUESTS, SHOE_LACE)
        }
        answer(lines, menu[choice(*menu.toTypedArray()) - 1])
    }

    private suspend fun Dialogue.answer(lines: TownGuideLines, picked: Line): Unit = with(lines) {
        when (picked) {
            WHERE_AM_I -> {
                chatPlayer(ChatAnim.QUIZ, WHERE_AM_I)
                chatNpc(ChatAnim.LAUGH, TOWN)
                val next = listOf(HOW_ARE_YOU, KNOW_QUESTS, SHOE_LACE)
                answer(lines, next[choice(*next.toTypedArray()) - 1])
            }
            HOW_ARE_YOU -> {
                chatPlayer(ChatAnim.HAPPY, HOW_ARE_YOU)
                chatNpc(ChatAnim.HAPPY, NOT_BAD)
                chatPlayer(ChatAnim.LAUGH, WEATHER)
                chatNpc(ChatAnim.LAUGH, WEATHER_YES)
                chatNpc(ChatAnim.QUIZ, WEATHER_MEANS)
                chatPlayer(ChatAnim.QUIZ, DOTS)
                chatNpc(ChatAnim.LAUGH, PRETTY_FACE)
            }
            QUESTS, KNOW_QUESTS -> quests(lines)
            SHOE_LACE -> {
                chatPlayer(ChatAnim.LAUGH, SHOE_LACE)
                chatNpc(ChatAnim.ANGRY, NOT_UNTIED)
                chatPlayer(ChatAnim.LAUGH, YOURE_RIGHT)
                chatNpc(ChatAnim.ANGRY, FOOL)
            }
            HAIRCUT -> {
                chatPlayer(ChatAnim.QUIZ, HAIRCUT)
                chatNpc(ChatAnim.HAPPY, HAIR_LOOKS)
                chatPlayer(ChatAnim.ANGRY, OH_THANKS)
                chatNpc(ChatAnim.LAUGH, HAIRDRESSER)
                chatNpc(ChatAnim.HAPPY, GENERAL_STORE)
            }
            WHATS_UP -> {
                chatPlayer(ChatAnim.QUIZ, WHATS_UP)
                chatNpc(ChatAnim.QUIZ, SKY)
                chatPlayer(ChatAnim.QUIZ, ASSUME)
                chatNpc(ChatAnim.QUIZ, ASSUME_REPLY)
            }
            STICK -> {
                chatPlayer(ChatAnim.QUIZ, STICK)
                chatNpc(ChatAnim.ANGRY, NOT_A_STICK)
                chatPlayer(ChatAnim.QUIZ, SHOW_ME)
                chatNpc(ChatAnim.SAD, LOW)
                chatPlayer(ChatAnim.LAUGH, IS_A_STICK)
                chatNpc(ChatAnim.SAD, OK_STICK)
                chatPlayer(ChatAnim.LAUGH, GOOD_LUCK)
            }
            else -> {
                chatPlayer(ChatAnim.QUIZ, VALUE)
                chatNpc(ChatAnim.QUIZ, ASKING)
                chatPlayer(ChatAnim.QUIZ, ER_YES)
                chatNpc(ChatAnim.ANGRY, NO_VALUE)
            }
        }
    }

    private suspend fun Dialogue.quests(lines: TownGuideLines) = with(lines) {
        chatPlayer(ChatAnim.QUIZ, KNOW_QUESTS)
        chatNpc(ChatAnim.QUIZ, WHAT_KIND)
        when (choice(FIGHT, EASY, THINKER, ALL_KINDS, ANOTHER_TIME)) {
            1 -> dangerous(lines)
            2 -> {
                chatPlayer(ChatAnim.HAPPY, EASY)
                chatNpc(ChatAnim.HAPPY, EASY_1)
                chatNpc(ChatAnim.QUIZ, EASY_2)
                when (choice(COOK, TALISMAN, ROMEO, ANOTHER_TIME, title = text(TELL_ME_ABOUT))) {
                    1 -> {
                        chatPlayer(ChatAnim.QUIZ, COOK_SAID)
                        chatNpc(ChatAnim.LAUGH, COOK_1)
                        chatNpc(ChatAnim.QUIZ, COOK_2)
                        chatPlayer(ChatAnim.HAPPY, COOK_DONE)
                        chatNpc(ChatAnim.HAPPY, COOK_OH_YES)
                    }
                    2 -> {
                        chatPlayer(ChatAnim.HAPPY, TALISMAN_SAID)
                        chatNpc(ChatAnim.HAPPY, TALISMAN_1)
                        chatPlayer(ChatAnim.HAPPY, TALISMAN_DONE)
                        chatNpc(ChatAnim.HAPPY, TALISMAN_THANKS)
                    }
                    3 -> {
                        chatPlayer(ChatAnim.HAPPY, ROMEO_SAID)
                        chatNpc(ChatAnim.HAPPY, ROMEO_1)
                        chatNpc(ChatAnim.LAUGH, ROMEO_2)
                        chatPlayer(ChatAnim.HAPPY, ROMEO_DONE)
                        chatNpc(ChatAnim.QUIZ, REALLY)
                        chatPlayer(ChatAnim.HAPPY, YUP)
                        chatNpc(ChatAnim.QUIZ, HOW)
                        chatPlayer(ChatAnim.LAUGH, JULIET)
                        chatNpc(ChatAnim.QUIZ, WELL_OK)
                    }
                    else -> chatPlayer(ChatAnim.HAPPY, ANOTHER_TIME)
                }
            }
            3 -> {
                chatPlayer(ChatAnim.HAPPY, THINKER)
                chatNpc(ChatAnim.QUIZ, SKILLS_1)
                chatNpc(ChatAnim.HAPPY, SKILLS_2)
                chatNpc(ChatAnim.HAPPY, SKILLS_3)
                when (choice(FRED, DORIC, ANOTHER_TIME, title = text(TELL_ME_ABOUT))) {
                    1 -> {
                        chatPlayer(ChatAnim.HAPPY, FRED_SAID)
                        chatNpc(ChatAnim.QUIZ, FRED_1)
                        chatPlayer(ChatAnim.HAPPY, FRED_DONE)
                        chatPlayer(ChatAnim.SAD, FRED_CHICKENS)
                        chatNpc(ChatAnim.LAUGH, CHICKENS_1)
                        chatNpc(ChatAnim.HAPPY, CHICKENS_2)
                    }
                    2 -> {
                        chatPlayer(ChatAnim.HAPPY, DORIC_SAID)
                        chatNpc(ChatAnim.HAPPY, DORIC_1)
                        chatPlayer(ChatAnim.HAPPY, DORIC_DONE)
                        chatNpc(ChatAnim.HAPPY, DORIC_THANKS)
                    }
                    else -> chatPlayer(ChatAnim.HAPPY, ANOTHER_TIME)
                }
            }
            4 -> {
                chatPlayer(ChatAnim.HAPPY, ALL_KINDS)
                for (line in listOf(ALL_1, ALL_2, ALL_3, ALL_4)) chatNpc(ChatAnim.HAPPY, line)
                when (choice(HETTY, PIRATE, ERNEST, ANOTHER_TIME, title = text(TELL_ME_ABOUT))) {
                    1 -> {
                        chatPlayer(ChatAnim.HAPPY, HETTY_SAID)
                        chatNpc(ChatAnim.HAPPY, HETTY_1)
                        chatPlayer(ChatAnim.HAPPY, HETTY_DONE)
                        chatNpc(ChatAnim.SCARED, DID_SHE)
                        chatPlayer(ChatAnim.LAUGH, NOT_REALLY)
                        chatNpc(ChatAnim.ANGRY, RIGHT)
                    }
                    2 -> {
                        chatPlayer(ChatAnim.HAPPY, PIRATE_SAID)
                        chatNpc(ChatAnim.HAPPY, PIRATE_1)
                        chatPlayer(ChatAnim.ANGRY, PIRATE_DONE)
                        chatNpc(ChatAnim.LAUGH, YARR_INDEED)
                        chatPlayer(ChatAnim.ANGRY, YARR)
                        chatNpc(ChatAnim.LAUGH, YARRR)
                        chatPlayer(ChatAnim.ANGRY, YARRRR)
                        chatNpc(ChatAnim.ANGRY, ENOUGH)
                        chatPlayer(ChatAnim.SAD, SORRY)
                    }
                    3 -> {
                        chatPlayer(ChatAnim.HAPPY, ERNEST_SAID)
                        chatNpc(ChatAnim.HAPPY, ERNEST_1)
                        chatNpc(ChatAnim.HAPPY, ERNEST_2)
                        chatPlayer(ChatAnim.HAPPY, ERNEST_DONE)
                        chatNpc(ChatAnim.LAUGH, A_CHICKEN)
                        chatPlayer(ChatAnim.HAPPY, YEAH_CHICKEN)
                        chatNpc(ChatAnim.LAUGH, POOR_GUY)
                    }
                    else -> chatPlayer(ChatAnim.HAPPY, ANOTHER_TIME)
                }
            }
            else -> chatPlayer(ChatAnim.HAPPY, ANOTHER_TIME)
        }
    }

    private suspend fun Dialogue.dangerous(lines: TownGuideLines) = with(lines) {
        chatNpc(ChatAnim.QUIZ, DANGEROUS)
        when (choice(DEMONS, VAMPYRES, SMALL, ANOTHER_TIME)) {
            1 -> {
                chatPlayer(ChatAnim.HAPPY, DEMONS)
                chatNpc(ChatAnim.LAUGH, DEMONS_1)
                chatNpc(ChatAnim.QUIZ, DEMONS_2)
                chatNpc(ChatAnim.QUIZ, DEMONS_3)
                chatPlayer(ChatAnim.LAUGH, DEMONS_DONE)
                chatNpc(ChatAnim.HAPPY, DEMONS_WELL_DONE)
            }
            2 -> {
                chatPlayer(null, VAMPYRES)
                chatNpc(ChatAnim.LAUGH, VAMPYRES_1)
                chatNpc(ChatAnim.HAPPY, VAMPYRES_2)
                chatPlayer(ChatAnim.LAUGH, VAMPYRES_DONE)
                chatNpc(ChatAnim.LAUGH, YEAH_YEAH)
                chatPlayer(ChatAnim.ANGRY, I_DID)
                chatNpc(ChatAnim.LAUGH, NOT_FOOLING)
                chatPlayer(ChatAnim.ANGRY, HUH)
            }
            3 -> {
                chatPlayer(ChatAnim.HAPPY, SMALL)
                chatNpc(ChatAnim.QUIZ, SMALL_ISNT)
                chatPlayer(ChatAnim.ANGRY, YES_IT_CAN)
                chatNpc(ChatAnim.HAPPY, POINT_TAKEN)
                if (lines is GeeLines) chatNpc(ChatAnim.HAPPY, lines.SMALL_MONSTERS)
                chatNpc(ChatAnim.HAPPY, IMPS)
                chatPlayer(ChatAnim.HAPPY, IMPS_DONE)
                chatNpc(ChatAnim.LAUGH, IMPS_WILL_BE)
            }
            else -> chatPlayer(ChatAnim.HAPPY, ANOTHER_TIME)
        }
    }
}

/** Donie's and Gee's Talk-to. */
fun registerDonieAndGee(world: World) {
    for ((id, lines) in listOf(DonieAndGee.DONIE to DonieLines, DonieAndGee.GEE to GeeLines)) {
        world.handlers.onNpc(id, DonieAndGee.TALK_TO) { op ->
            world.dialogues.start(this, op.npc) { with(DonieAndGee) { talk(lines, pick(world.random)) } }
        }
    }
}
