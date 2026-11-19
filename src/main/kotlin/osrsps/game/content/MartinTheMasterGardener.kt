package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

private val logger = KotlinLogging.logger {}

/** Martin the Master Gardener's lines; the words are in the dialogue file, from the Wiki's Transcript:Martin the Master Gardener, read 2026-11-19. */
object MartinLines : Lines("martin", "Transcript:Martin the Master Gardener") {
    val ASK_CAPE = line("ask_cape", "first menu, option 1: ask about the Skillcape of Farming (no line from the player)")
    val ASK_QUEST = line("ask_quest", "first menu, option 2: ask about the quest (no line from the player)")
    val WHAT_CAPE = line("what_cape", "below 99 Farming: the player asks about his cape")
    val CAPE_ANSWER = line("cape_answer", "below 99 Farming: his answer")
    val SKILLCAPE = line("skillcape", "at 99 Farming, cape menu: Skillcape")
    val HOOD = line("hood", "at 99 Farming, cape menu: Hood")
    val CAN_I_BUY = line("can_i_buy", "Skillcape: what the player says")
    val PRICE = line("price", "Skillcape: his price, 99000 coins")
    val NOT_PAYING = line("not_paying", "price menu, option 1, which the player then says")
    val NO_SKIN = line("no_skin", "option 1: his answer")
    val SURE = line("sure", "price menu, option 2, which the player then says")
    val NO_MONEY = line("no_money", "option 2 with fewer than 99,000 coins: the player's line")
    val COME_BACK = line("come_back", "option 2 with fewer than 99,000 coins: his answer")
    val NO_SPACE = line("no_space", "option 2 without room for the cape and hood: his answer")
    val SOLD = line("sold", "option 2, sold: his line")
    val ANOTHER_HOOD = line("another_hood", "Hood: what the player says")
    val HOOD_GIVEN = line("hood_given", "Hood: the box as he hands it over")
    val SOLVED = line("solved", "the quest (after Fairytale II): what the player says")
    val RESERVE = line("reserve", "the quest: his first answer")
    val GODFATHER = line("godfather", "the quest: the player's second line")
    val CROPS = line("crops", "the quest: his last answer")
}

/**
 * Martin the Master Gardener (`martin_the_master_farmer` 5832) by the Group of Advanced Gardeners' pig pen in Draynor's
 * market (Wiki: Martin the Master Gardener, 3078,3258). Talk-to follows the transcript's standard dialogue: the Farming
 * cape (99 Farming, 99,000 coins, cape and hood, trimmed with another 99, as the Wiki's stock note says) and the quest
 * talk as after Fairytale II. Pickpocket is a Master Farmer's ([osrsps.game.skill.Pickpocketing]), as the Wiki's
 * Pickpocketing section gives his chart and table as the Master Farmer's.
 *
 * Guessed: every chathead expression; that the hood needs a Farming cape owned and a free slot (the transcript gives
 * neither), as Wilfred's. Left out: the overhead after a recent theft ("You're the thief...") and the Fairytale quests
 * themselves.
 */
object MartinTheMasterGardener {
    const val ID = 5832
    const val TALK_TO = 1

    /** skillcape_farming, its trimmed one and skillcape_farming_hood. */
    const val FARMING_CAPE = 9810
    const val FARMING_CAPE_TRIMMED = 9811
    const val FARMING_HOOD = 9812

    const val COINS = 995
    const val CAPE_PRICE = 99_000
    const val MAX_LEVEL = 99

    internal suspend fun Dialogue.talk(give: (Player, Int) -> Unit) = with(MartinLines) {
        if (choice(ASK_CAPE, ASK_QUEST) == 2) {
            chatPlayer(ChatAnim.HAPPY, SOLVED)
            chatNpc(ChatAnim.NEUTRAL, RESERVE)
            chatPlayer(ChatAnim.NEUTRAL, GODFATHER)
            chatNpc(ChatAnim.ANGRY, CROPS)
            return@with
        }
        if (player.skills.baseLevel(Skill.FARMING) < MAX_LEVEL) {
            chatPlayer(ChatAnim.QUIZ, WHAT_CAPE)
            chatNpc(ChatAnim.HAPPY, CAPE_ANSWER)
            return@with
        }
        if (choice(SKILLCAPE, HOOD) == 2) {
            chatPlayer(ChatAnim.QUIZ, ANOTHER_HOOD)
            val ownsCape = Wilfred.owns(player, FARMING_CAPE) || Wilfred.owns(player, FARMING_CAPE_TRIMMED)
            if (ownsCape && player.inventory.freeSlots >= 1) {
                give(player, FARMING_HOOD)
                objbox(FARMING_HOOD, HOOD_GIVEN)
            }
            return@with
        }
        chatPlayer(ChatAnim.QUIZ, CAN_I_BUY)
        chatNpc(ChatAnim.NEUTRAL, PRICE)
        if (choice(NOT_PAYING, SURE) == 1) {
            chatPlayer(ChatAnim.ANGRY, NOT_PAYING)
            chatNpc(ChatAnim.NEUTRAL, NO_SKIN)
            return@with
        }
        chatPlayer(ChatAnim.HAPPY, SURE)
        val coins = player.inventory.count(COINS)
        when {
            coins < CAPE_PRICE -> {
                chatPlayer(ChatAnim.SAD, NO_MONEY)
                chatNpc(ChatAnim.NEUTRAL, COME_BACK)
            }
            // The coins' slot frees up when they are all spent.
            player.inventory.freeSlots + (if (coins == CAPE_PRICE.toLong()) 1 else 0) < 2 -> chatNpc(ChatAnim.NEUTRAL, NO_SPACE)
            else -> {
                var left = CAPE_PRICE
                for ((slot, held) in player.inventory.contents()) {
                    if (left == 0) break
                    if (held.id == COINS) left -= player.inventory.remove(slot, left)
                }
                val trimmed = Skill.entries.any { it != Skill.FARMING && player.skills.baseLevel(it) >= MAX_LEVEL }
                give(player, if (trimmed) FARMING_CAPE_TRIMMED else FARMING_CAPE)
                give(player, FARMING_HOOD)
                chatNpc(ChatAnim.HAPPY, SOLD)
            }
        }
    }
}

/** Martin's Talk-to. His Pickpocket is [osrsps.game.skill.Pickpocketing]'s. */
fun registerMartinTheMasterGardener(world: World) {
    val give: (Player, Int) -> Unit = { player, obj ->
        world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } ?: logger.warn { "No item $obj in the cache" }
    }
    world.handlers.onNpc(MartinTheMasterGardener.ID, MartinTheMasterGardener.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(MartinTheMasterGardener) { talk(give) } }
    }
}
