package osrsps.game.content

import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/**
 * The Crafting tutor's lines; the words are in the dialogue file, from the Wiki's Transcript:Crafting tutor (Standard
 * dialogue), read 2026-10-25. "Training" has a block for Crafting 1 to 19, one for members at 10 to 19, and one for 20
 * and up; "items" has one part per topic.
 */
object CraftingTutorLines : Lines("crafting_tutor", "Transcript:Crafting tutor") {
    val HELLO = line("hello", "the player's greeting")
    val OFFER = line("offer", "the tutor's first line")
    val TRAINING = line("training", "option 1: how to train, which the player then says")
    val ITEMS = line("items", "option 2: what can be made, which the player then says")
    val NO_THANKS = line("no_thanks", "option 3, which the player then says")
    val COME_BACK = line("come_back", "option 3: the tutor's answer")
    val THANKS = line("thanks", "training (every level): the player's thanks")
    val ANYTHING_ELSE = line("anything_else", "training (every level) and after the topics: the tutor asking if there's anything else")
    val LOW_HIDE_ARMOUR = line("low_hide_armour", "training, level 1 to 19: first line, cow-hide armour")
    val LOW_NEEDLE = line("low_needle", "training, level 1 to 19: second line, needle and thread")
    val LOW_SPINNING = line("low_spinning", "training, level 1 to 19: third line, the spinning wheel")
    val MID_FLAX = line("mid_flax", "training, members at 10 to 19: first line, flax")
    val MID_HIDE_ARMOUR = line("mid_hide_armour", "training, members at 10 to 19: second line, cow-hide armour")
    val HIGH_OPEN = line("high_open", "training, 20 to 98: first line")
    val HIGH_MAXED = line("high_maxed", "training, 99: first line")
    val HIGH_JEWELLERY = line("high_jewellery", "training, 20 and up: jewellery")
    val HIGH_ARMOUR = line("high_armour", "training, 20 and up: more armour")
    val HIGH_HIDES = line("high_hides", "training, 20 and up, members: dragon and other hides")
    val HIGH_GLASS = line("high_glass", "training, 20 and up, members: glass blowing")
    val LIST = line("list", "items: the tutor's list")
    val WHICH_TOPIC = line("which_topic", "items: the tutor asking which to hear about")
    val ARMOUR = line("armour", "items, topic option: armour, which the player then says")
    val POTTERY = line("pottery", "items, topic option: pottery, which the player then says")
    val GLASS = line("glass", "items, topic option: glass blowing, which the player then says")
    val JEWELLERY = line("jewellery", "items, topic option: jewellery, which the player then says")
    val WEAPONS = line("weapons", "items, topic option: weapons, which the player then says")
    val NO_MORE = line("no_more", "items, topic option shown after one topic: no more, which the player then says")
    val MORE_FROM_LIST = line("more_from_list", "items, after each topic: the tutor asking about more from the list")
    val ARMOUR_1 = line("armour_1", "items, armour: the tutor's first line")
    val ARMOUR_2 = line("armour_2", "items, armour: second line")
    val ARMOUR_3 = line("armour_3", "items, armour: third line, shields")
    val ARMOUR_4 = line("armour_4", "items, armour: fourth line, helmets")
    val ARMOUR_REPLY = line("armour_reply", "items, armour: the player's answer")
    val POTTERY_1 = line("pottery_1", "items, pottery: the tutor's first line")
    val POTTERY_2 = line("pottery_2", "items, pottery: second line")
    val POTTERY_3 = line("pottery_3", "items, pottery: third line")
    val POTTERY_4 = line("pottery_4", "items, pottery: fourth line, the barbarians")
    val POTTERY_5 = line("pottery_5", "items, pottery: fifth line, what can be made")
    val POTTERY_REPLY = line("pottery_reply", "items, pottery: the player's answer")
    val GLASS_1 = line("glass_1", "items, glass blowing: the tutor's first line")
    val GLASS_2 = line("glass_2", "items, glass blowing: second line")
    val GLASS_3 = line("glass_3", "items, glass blowing: third line")
    val GLASS_REPLY = line("glass_reply", "items, glass blowing: the player's answer")
    val JEWELLERY_1 = line("jewellery_1", "items, jewellery: the tutor's first line")
    val JEWELLERY_2 = line("jewellery_2", "items, jewellery: second line")
    val JEWELLERY_3 = line("jewellery_3", "items, jewellery: third line")
    val JEWELLERY_4 = line("jewellery_4", "items, jewellery: fourth line, enchanting")
    val JEWELLERY_REPLY = line("jewellery_reply", "items, jewellery: the player's answer")
    val WEAPONS_1 = line("weapons_1", "items, weapons: the tutor's first line")
    val WEAPONS_2 = line("weapons_2", "items, weapons: second line, battlestaves")
    val WEAPONS_3 = line("weapons_3", "items, weapons: third line, the orb")
    val WEAPONS_4 = line("weapons_4", "items, weapons: fourth line, the magic")
    val WEAPONS_5 = line("weapons_5", "items, weapons: fifth line, silver")
    val WEAPONS_REPLY = line("weapons_reply", "items, weapons: the player's answer")
}

/**
 * The Crafting tutor (NPC 3220) on the castle's first floor. From the Wiki: the conversation and what each part depends
 * on. Every account here has done Sheep Shearer, so the Farmer Fred line never shows, and the world is a members' one.
 * Guessed: every chathead expression; that the three training blocks don't overlap (members at 10 to 19 get only theirs);
 * that after the training talk the first menu comes back, without the greeting; and that the topic menu leaves out the
 * topics already heard.
 */
object CraftingTutor {
    const val ID = 3220
    const val TALK_TO = 1

    internal suspend fun Dialogue.talk() = with(CraftingTutorLines) {
        chatPlayer(ChatAnim.HAPPY, HELLO)
        chatNpc(ChatAnim.HAPPY, OFFER)
        var menu = listOf(TRAINING, ITEMS, NO_THANKS)
        while (true) {
            when (menu[choice(*menu.toTypedArray()) - 1]) {
                TRAINING -> {
                    chatPlayer(ChatAnim.QUIZ, TRAINING)
                    training(player.skills.baseLevel(Skill.CRAFTING))
                    chatPlayer(ChatAnim.HAPPY, THANKS)
                    chatNpc(ChatAnim.HAPPY, ANYTHING_ELSE)
                    menu = listOf(TRAINING, ITEMS, NO_THANKS)
                }
                ITEMS -> {
                    chatPlayer(ChatAnim.QUIZ, ITEMS)
                    topics()
                    chatNpc(ChatAnim.HAPPY, ANYTHING_ELSE)
                    menu = listOf(TRAINING, NO_THANKS)
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
                    chatNpc(ChatAnim.HAPPY, COME_BACK)
                    return@with
                }
            }
        }
    }

    private suspend fun Dialogue.training(level: Int) = with(CraftingTutorLines) {
        when {
            level < 10 -> {
                chatNpc(ChatAnim.NEUTRAL, LOW_HIDE_ARMOUR)
                chatNpc(ChatAnim.NEUTRAL, LOW_NEEDLE)
                chatNpc(ChatAnim.NEUTRAL, LOW_SPINNING)
            }
            level < 20 -> {
                chatNpc(ChatAnim.NEUTRAL, MID_FLAX)
                chatNpc(ChatAnim.NEUTRAL, MID_HIDE_ARMOUR)
            }
            else -> {
                chatNpc(ChatAnim.HAPPY, if (level < 99) HIGH_OPEN else HIGH_MAXED)
                chatNpc(ChatAnim.NEUTRAL, HIGH_JEWELLERY)
                chatNpc(ChatAnim.NEUTRAL, HIGH_ARMOUR)
                chatNpc(ChatAnim.NEUTRAL, HIGH_HIDES)
                chatNpc(ChatAnim.NEUTRAL, HIGH_GLASS)
            }
        }
    }

    /** The list of things to make, then a menu of the topics not yet heard until the player wants no more. */
    private suspend fun Dialogue.topics() = with(CraftingTutorLines) {
        chatNpc(ChatAnim.NEUTRAL, LIST)
        chatNpc(ChatAnim.QUIZ, WHICH_TOPIC)
        val left = mutableListOf(ARMOUR, POTTERY, GLASS, JEWELLERY, WEAPONS)
        while (left.isNotEmpty()) {
            val options = if (left.size < 5) left + NO_MORE else left
            val topic = options[choice(*options.toTypedArray()) - 1]
            chatPlayer(ChatAnim.QUIZ, topic)
            if (topic == NO_MORE) return@with
            left -= topic
            val (said, reply) = when (topic) {
                ARMOUR -> listOf(ARMOUR_1, ARMOUR_2, ARMOUR_3, ARMOUR_4) to ARMOUR_REPLY
                POTTERY -> listOf(POTTERY_1, POTTERY_2, POTTERY_3, POTTERY_4, POTTERY_5) to POTTERY_REPLY
                GLASS -> listOf(GLASS_1, GLASS_2, GLASS_3) to GLASS_REPLY
                JEWELLERY -> listOf(JEWELLERY_1, JEWELLERY_2, JEWELLERY_3, JEWELLERY_4) to JEWELLERY_REPLY
                else -> listOf(WEAPONS_1, WEAPONS_2, WEAPONS_3, WEAPONS_4, WEAPONS_5) to WEAPONS_REPLY
            }
            for (line in said) chatNpc(ChatAnim.NEUTRAL, line)
            chatPlayer(ChatAnim.HAPPY, reply)
            if (left.isNotEmpty()) chatNpc(ChatAnim.QUIZ, MORE_FROM_LIST)
        }
    }
}

/** The Crafting tutor's Talk-to. */
fun registerCraftingTutor(world: World) {
    world.handlers.onNpc(CraftingTutor.ID, CraftingTutor.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(CraftingTutor) { talk() } }
    }
}
