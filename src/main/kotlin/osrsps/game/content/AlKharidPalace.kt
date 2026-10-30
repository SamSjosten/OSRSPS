package osrsps.game.content

import osrsps.game.NpcType
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.shop.ShopScreen

/** Chancellor Hassan's lines; the words are in the dialogue file, from the Wiki's Transcript:Chancellor Hassan, read 2026-10-30. */
object HassanLines : Lines("hassan", "Transcript:Chancellor Hassan") {
    val THANK_YOU = line("thank_you", "after Prince Ali Rescue: his one line, and the talk ends")
    val NOT_A_THIEF = line("not_a_thief", "the player tries the big window after the quest: his line")
    val WINDOW_NEEDS = line("window_needs", "the box after that: the shortcut needs the hard Desert diary and Agility 70")
}

/** Osman's lines; the words are in the dialogue file, from the Wiki's Transcript:Osman (after Contact!), read 2026-10-30. */
object OsmanLines : Lines("osman", "Transcript:Osman") {
    val HELLO_AGAIN = line("hello_again", "after Contact!: his first line")
    val SQIRKS = line("sqirks", "the first menu, option 1, which the player then says")
    val SOPHANEM = line("sophanem", "the first menu, option 2, which the player then says")
    val NEED_TO_GO = line("need_to_go", "the first menu, option 3, which the player then says")
    val UNTIL_NEXT_TIME = line("until_next_time", "his goodbye, after either way of leaving")
    val SOPHANEM_REPLY = line("sophanem_reply", "his answer about Sophanem")
    val DAGGERS = line("daggers", "the Sophanem menu, option 1, which the player then says")
    val DAGGER_OFFER = line("dagger_offer", "his offer of a Keris for 20,000 coins")
    val TAKE_ONE = line("take_one", "the dagger menu, option 1, which the player then says")
    val HANDS_DAGGER = line("hands_dagger", "the box when he sells a Keris")
    val NO_COINS = line("no_coins", "a Keris without 20,000 coins: the player's line")
    val NO_ROOM = line("no_room", "a Keris with a full backpack: the player's line")
    val TOO_MUCH = line("too_much", "the dagger menu, option 2, which the player then says")
    val RECONSIDER = line("reconsider", "his answer to that")
    val MENAPHOS = line("menaphos", "the Sophanem menu, option 2, which the player then says")
    val MENAPHOS_REPLY = line("menaphos_reply", "his answer about Menaphos")
    val NOT_INVOLVED = line("not_involved", "the player's line after it")
    val NO = line("no", "his one-word answer")
    val SOMETHING_ELSE = line("something_else", "the Sophanem menu, option 3, which the player then says")
    val AS_YOU_WISH = line("as_you_wish", "his answer, before the first menu again")
    val SHOULD_GO = line("should_go", "the Sophanem menu, option 4, which the player then says")

    val HAVE_JUICE = line("have_juice", "the player holds sq'irk juice: the player's line")
    val THIEVING_TIPS = line("thieving_tips", "the box when he takes the juice; {xp} is the Thieving experience", "xp")
    val THANK_YOU = line("thank_you", "his line after taking the juice")
    val WILL_DO = line("will_do", "the player's last line")
    val SQIRKS_MENU = line("sqirks_menu", "no juice: the player's line before the sq'irks menu")
    val WHERE = line("where", "the sq'irks menu, option 1, which the player then says")
    val WHERE_1 = line("where_1", "where: his first answer")
    val WHAT_HAPPENED = line("what_happened", "where: the player's question")
    val WHERE_2 = line("where_2", "where: his answer")
    val SO_ALL = line("so_all", "where: the player's next line")
    val WHERE_3 = line("where_3", "where: his answer")
    val OH_COME_ON = line("oh_come_on", "where: the player's next line")
    val WHERE_4 = line("where_4", "where: his answer about the garden")
    val THIS_SHOULD = line("this_should", "where: the player's next line")
    val WHERE_5 = line("where_5", "where: his last line, before the where menu")
    val ANOTHER = line("another", "the where menu, option 1, which the player then says")
    val WHAT_ELSE = line("what_else", "his answer, before the sq'irks menu again")
    val THANKS_INFO = line("thanks_info", "the where menu, option 2, which the player then says; the talk ends")
    val WHY = line("why", "the sq'irks menu, option 2, which the player then says")
    val WHY_REPLY = line("why_reply", "his answer")
    val HOW = line("how", "the sq'irks menu, option 3, which the player then says")
    val HOW_REPLY = line("how_reply", "his answer")
    val REWARD = line("reward", "the sq'irks menu, option 4, which the player then says")
    val REWARD_1 = line("reward_1", "reward: his first answer")
    val HOW_MUCH = line("how_much", "reward: the player's question")
    val REWARD_2 = line("reward_2", "reward: his answer")
    val SOUNDS_FAIR = line("sounds_fair", "reward: the player's last line")
    val NECTAR = line("nectar", "the sq'irks menu, option 5, which the player then says")
    val NECTAR_1 = line("nectar_1", "nectar: his answer")
    val SALIVATING = line("salivating", "nectar: the box describing him")
    val WOULDNT = line("wouldnt", "nectar: the player's line")
    val NECTAR_2 = line("nectar_2", "nectar: his answer")
    val I_SEE = line("i_see", "nectar: the player's last line")
}

/** Prince Ali's lines in the palace; the words are in the dialogue file, from the Wiki's Transcript:Prince Ali Rescue ("Talking to Prince Ali"), read 2026-10-30. */
object PrinceAliLines : Lines("prince_ali", "Transcript:Prince Ali Rescue") {
    val GOOD_TO_SEE = line("good_to_see", "his first line")
    val RESTING = line("resting", "his second line")
    val BEST = line("best", "the player's line")
}

/**
 * Al Kharid Palace (OSRS Wiki, Al Kharid Palace, read 2026-10-30), as after Prince Ali Rescue and Contact!: Chancellor
 * Hassan in the throne room, Osman outside, and Prince Ali home again. The chests and drawers, the ladder and the large
 * doors already work by type (Lumbridge Castle's and step 1's). The Emir is never seen.
 *
 * - Hassan (4285) has one line after the quest. The big windows (`kharid_bigwindow`, 33348) are a shortcut that needs
 *   the hard Desert diary; diaries aren't built, so every player gets his refusal and the box saying what it needs.
 * - Osman (4286) after Contact!: the Sophanem talk with a Keris for 20,000 coins, and the Sorceress's Garden talk; he
 *   takes every sq'irk juice the player holds for Thieving experience (Wiki, Sq'irk juice: 350, 1,350, 2,350 and 3,000 a
 *   glass). He gives no free Keris: every account counts as having done Contact!, so as having taken Kaleef's (guessed).
 * - Prince Ali: the map spawns `prince_ali_palace` (11645), which varp 273 (Prince Ali Rescue) at 100 or more shows as
 *   Prince Ali (11580). The Wiki gives no tile, so he stands in the throne room by Hassan (guessed).
 *
 * Guessed: the expressions, that the Keris is refused for a full backpack only when paying won't free a slot, and that
 * "Let's talk about something else" goes back to the first menu with no greeting.
 */
object AlKharidPalace {
    const val HASSAN = 4285
    const val OSMAN = 4286
    const val PRINCE_ALI = 11580
    const val PRINCE_ALI_PALACE = 11645
    const val TALK_TO = 1

    const val BIG_WINDOW = 33348
    const val ENTER = 1

    const val KERIS = 10581
    const val KERIS_PRICE = 20_000

    /** Sq'irk juices (`osman_squirk_j_*`) and the Thieving experience Osman gives a glass, in tenths. */
    val JUICES = linkedMapOf(
        10851 to 3_500, // winter
        10848 to 13_500, // spring
        10850 to 23_500, // autumn
        10849 to 30_000, // summer
    )

    internal suspend fun Dialogue.hassan() = chatNpc(ChatAnim.HAPPY, HassanLines.THANK_YOU)

    internal suspend fun Dialogue.bigWindow(hassan: NpcType) = with(HassanLines) {
        chatNpc(hassan, hassan.name, ChatAnim.ANGRY, NOT_A_THIEF)
        mesbox(WINDOW_NEEDS)
    }

    internal suspend fun Dialogue.princeAli() = with(PrinceAliLines) {
        chatNpc(ChatAnim.HAPPY, GOOD_TO_SEE)
        chatNpc(ChatAnim.NEUTRAL, RESTING)
        chatPlayer(ChatAnim.HAPPY, BEST)
    }

    /** Osman's talk; [give] hands over what the player bought. */
    internal suspend fun Dialogue.osman(give: (Int) -> Unit) = with(OsmanLines) {
        chatNpc(ChatAnim.SHIFTY, HELLO_AGAIN)
        while (true) {
            when (choice(SQIRKS, SOPHANEM, NEED_TO_GO)) {
                1 -> return@with sqirks()
                2 -> {
                    chatPlayer(ChatAnim.NEUTRAL, SOPHANEM)
                    chatNpc(ChatAnim.SHIFTY, SOPHANEM_REPLY)
                    if (!sophanem(give)) return@with
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, NEED_TO_GO)
                    chatNpc(ChatAnim.SHIFTY, UNTIL_NEXT_TIME)
                    return@with
                }
            }
        }
    }

    /** The Sophanem menu, until the player leaves (false) or asks to talk about something else (true). */
    private suspend fun Dialogue.sophanem(give: (Int) -> Unit): Boolean = with(OsmanLines) {
        var back: Boolean? = null
        while (back == null) {
            when (choice(DAGGERS, MENAPHOS, SOMETHING_ELSE, SHOULD_GO)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, DAGGERS)
                    chatNpc(ChatAnim.SHIFTY, DAGGER_OFFER)
                    if (choice(TAKE_ONE, TOO_MUCH) == 1) {
                        chatPlayer(ChatAnim.NEUTRAL, TAKE_ONE)
                        buyKeris(give)
                    } else {
                        chatPlayer(ChatAnim.NEUTRAL, TOO_MUCH)
                        chatNpc(ChatAnim.SHIFTY, RECONSIDER)
                    }
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, MENAPHOS)
                    chatNpc(ChatAnim.SHIFTY, MENAPHOS_REPLY)
                    chatPlayer(ChatAnim.QUIZ, NOT_INVOLVED)
                    chatNpc(ChatAnim.SHIFTY, NO)
                }
                3 -> {
                    chatPlayer(ChatAnim.NEUTRAL, SOMETHING_ELSE)
                    chatNpc(ChatAnim.SHIFTY, AS_YOU_WISH)
                    back = true
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, SHOULD_GO)
                    chatNpc(ChatAnim.SHIFTY, UNTIL_NEXT_TIME)
                    back = false
                }
            }
        }
        back == true
    }

    private suspend fun Dialogue.buyKeris(give: (Int) -> Unit) = with(OsmanLines) {
        val coins = player.inventory.count(ShopScreen.COINS)
        when {
            coins < KERIS_PRICE -> chatPlayer(ChatAnim.SAD, NO_COINS)
            player.inventory.freeSlots == 0 && coins != KERIS_PRICE.toLong() -> chatPlayer(ChatAnim.SAD, NO_ROOM)
            else -> {
                AlKharidTraders.pay(player, KERIS_PRICE)
                give(KERIS)
                objbox(KERIS, HANDS_DAGGER)
            }
        }
    }

    /** The sq'irks talk: the juice handed in if the player has any, else the menu about the garden. */
    private suspend fun Dialogue.sqirks() = with(OsmanLines) {
        val xp = takeJuice(player)
        if (xp > 0) {
            chatPlayer(ChatAnim.HAPPY, HAVE_JUICE)
            mesbox(THIEVING_TIPS, "xp" to xp / 10)
            chatNpc(ChatAnim.HAPPY, THANK_YOU)
            chatPlayer(ChatAnim.HAPPY, WILL_DO)
            return@with
        }
        chatPlayer(ChatAnim.NEUTRAL, SQIRKS_MENU)
        while (true) {
            when (choice(WHERE, WHY, HOW, REWARD, NECTAR)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, WHERE)
                    chatNpc(ChatAnim.SHIFTY, WHERE_1)
                    chatPlayer(ChatAnim.QUIZ, WHAT_HAPPENED)
                    chatNpc(ChatAnim.SAD, WHERE_2)
                    chatPlayer(ChatAnim.QUIZ, SO_ALL)
                    chatNpc(ChatAnim.SHIFTY, WHERE_3)
                    chatPlayer(ChatAnim.CONFUSED, OH_COME_ON)
                    chatNpc(ChatAnim.SHIFTY, WHERE_4)
                    chatPlayer(ChatAnim.QUIZ, THIS_SHOULD)
                    chatNpc(ChatAnim.SHIFTY, WHERE_5)
                    if (choice(ANOTHER, THANKS_INFO) == 2) {
                        chatPlayer(ChatAnim.HAPPY, THANKS_INFO)
                        return@with
                    }
                    chatPlayer(ChatAnim.QUIZ, ANOTHER)
                    chatNpc(ChatAnim.SHIFTY, WHAT_ELSE)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, WHY)
                    chatNpc(ChatAnim.SHIFTY, WHY_REPLY)
                }
                3 -> {
                    chatPlayer(ChatAnim.QUIZ, HOW)
                    chatNpc(ChatAnim.SHIFTY, HOW_REPLY)
                }
                4 -> {
                    chatPlayer(ChatAnim.QUIZ, REWARD)
                    chatNpc(ChatAnim.SHIFTY, REWARD_1)
                    chatPlayer(ChatAnim.QUIZ, HOW_MUCH)
                    chatNpc(ChatAnim.SHIFTY, REWARD_2)
                    chatPlayer(ChatAnim.HAPPY, SOUNDS_FAIR)
                }
                else -> {
                    chatPlayer(ChatAnim.QUIZ, NECTAR)
                    chatNpc(ChatAnim.HAPPY, NECTAR_1)
                    mesbox(SALIVATING)
                    chatPlayer(ChatAnim.QUIZ, WOULDNT)
                    chatNpc(ChatAnim.SHIFTY, NECTAR_2)
                    chatPlayer(ChatAnim.NEUTRAL, I_SEE)
                }
            }
        }
    }

    /** Takes every sq'irk juice [player] holds and gives the Thieving experience; the experience in tenths, 0 for none. */
    fun takeJuice(player: Player): Int {
        var tenths = 0
        for ((slot, held) in player.inventory.contents()) {
            val each = JUICES[held.id] ?: continue
            tenths += each * player.inventory.remove(slot, held.count)
        }
        if (tenths > 0) player.skills.addTenths(Skill.THIEVING, tenths)
        return tenths
    }
}

fun registerAlKharidPalace(world: World) {
    with(AlKharidPalace) {
        world.handlers.onNpc(HASSAN, TALK_TO) { op -> world.dialogues.start(this, op.npc) { hassan() } }
        world.handlers.onNpc(PRINCE_ALI, TALK_TO) { op -> world.dialogues.start(this, op.npc) { princeAli() } }
        world.handlers.onNpc(OSMAN, TALK_TO) { op ->
            val give = { obj: Int -> world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } ?: Unit }
            world.dialogues.start(this, op.npc) { osman(give) }
        }
        world.handlers.onLoc(BIG_WINDOW, ENTER) {
            arriveDelay()
            val hassan = world.npcTypes[HASSAN] ?: return@onLoc
            world.dialogues.start(this) { bigWindow(hassan) }
        }
    }
}
