package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

object EdgevilleShopKeeperLines : GeneralStoreLines("edgeville_shop_keeper", "Transcript:Shop keeper (Edgeville)", "shop keeper")

object EdgevilleShopAssistantLines : GeneralStoreLines("edgeville_shop_assistant", "Transcript:Shop assistant (Edgeville)", "shop assistant")

/** Oziach's lines after Dragon Slayer I; the words are in the dialogue file, from the Wiki's Transcript:Oziach, read 2026-10-30. */
object OziachLines : Lines("oziach", "Transcript:Oziach") {
    val GREETING = line("greeting", "after Dragon Slayer I: his first line")
    val BUY = line("buy", "option 1: buying a rune platebody, which the player then says before the shop opens")
    val NOT_FRIEND = line("not_friend", "option 2, which the player then says")
    val NOT_FRIEND_REPLY = line("not_friend_reply", "option 2: his answer")
    val NICE_DAY = line("nice_day", "option 3, which the player then says")
    val NICE_DAY_REPLY = line("nice_day_reply", "option 3: his answer")
    val MAZE_KEY = line("maze_key", "option 4: the key to Melzar's Maze, which the player then says")
    val MAZE_KEY_REPLY = line("maze_key_reply", "option 4: his answer, sending the player to the Champions' Guild's Guildmaster")
}

/** Richard's lines; the words are in the dialogue file, from the Wiki's Transcript:Richard (merchant), read 2026-10-30. */
object RichardLines : Lines("richard", "Transcript:Richard (merchant)") {
    val GREETING = line("greeting", "his first line: an offer of capes")
    val WHAT_FOR = line("what_for", "option 1: what the capes are for, which the player then says")
    val EXPLAIN_1 = line("explain_1", "option 1: his answer, first line")
    val EXPLAIN_2 = line("explain_2", "option 1: second line")
    val WANT_ONE = line("want_one", "option 1: his question, before the menu again")
    val YES = line("yes", "option 2: yes, and the shop opens with no line from the player")
    val NO = line("no", "option 3: no, which the player then says")
}

/**
 * Edgeville's shops (Edgeville piece step 2): the General Store's Shop keeper (2821) and Shop assistant (2822), Oziach
 * (822) and Richard (`wilderness_capeseller_6`, 2200). Their stock, prices and Trade are [registerShops]'s, from
 * shops.json; here is their Talk-to. From the Wiki: each conversation, and which option opens the shop. Oziach talks as
 * after Dragon Slayer I (every account has every quest done). Expressions are guessed, as the Wiki notes none.
 */
object EdgevilleShops {
    const val SHOP_KEEPER = 2821
    const val SHOP_ASSISTANT = 2822
    const val OZIACH = 822
    const val RICHARD = 2200
    const val TALK_TO = 1

    /** Oziach's talk after Dragon Slayer I; true when the player asked to buy. */
    internal suspend fun Dialogue.oziach(): Boolean = with(OziachLines) {
        chatNpc(ChatAnim.NEUTRAL, GREETING)
        when (choice(BUY, NOT_FRIEND, NICE_DAY, MAZE_KEY)) {
            1 -> {
                chatPlayer(ChatAnim.NEUTRAL, BUY)
                return@with true
            }
            2 -> {
                chatPlayer(ChatAnim.ANGRY, NOT_FRIEND)
                chatNpc(ChatAnim.ANGRY, NOT_FRIEND_REPLY)
            }
            3 -> {
                chatPlayer(ChatAnim.HAPPY, NICE_DAY)
                chatNpc(ChatAnim.NEUTRAL, NICE_DAY_REPLY)
            }
            else -> {
                chatPlayer(ChatAnim.QUIZ, MAZE_KEY)
                chatNpc(ChatAnim.NEUTRAL, MAZE_KEY_REPLY)
            }
        }
        false
    }

    /** Richard's talk, which offers its menu again after explaining the capes; true when the player asked to buy. */
    internal suspend fun Dialogue.richard(): Boolean = with(RichardLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        var option = choice(WHAT_FOR, YES, NO)
        while (option == 1) {
            chatPlayer(ChatAnim.QUIZ, WHAT_FOR)
            chatNpc(ChatAnim.NEUTRAL, EXPLAIN_1)
            chatNpc(ChatAnim.NEUTRAL, EXPLAIN_2)
            chatNpc(ChatAnim.QUIZ, WANT_ONE)
            option = choice(WHAT_FOR, YES, NO)
        }
        if (option == 3) chatPlayer(ChatAnim.NEUTRAL, NO)
        option == 2
    }
}

fun registerEdgevilleShops(world: World) {
    fun openShop(player: Player, npc: Int) {
        world.shopTypes.ofNpc(npc)?.let { world.shopScreen.open(player, it) }
    }
    with(EdgevilleShops) {
        for ((id, lines) in listOf(SHOP_KEEPER to EdgevilleShopKeeperLines, SHOP_ASSISTANT to EdgevilleShopAssistantLines)) {
            world.handlers.onNpc(id, TALK_TO) { op ->
                var trade = false
                world.dialogues.start(this, op.npc) { trade = with(LumbridgeShopkeepers) { keeper(lines) } }
                if (trade) openShop(player, id)
            }
        }
        world.handlers.onNpc(OZIACH, TALK_TO) { op ->
            var trade = false
            world.dialogues.start(this, op.npc) { trade = oziach() }
            if (trade) openShop(player, OZIACH)
        }
        world.handlers.onNpc(RICHARD, TALK_TO) { op ->
            var trade = false
            world.dialogues.start(this, op.npc) { trade = richard() }
            if (trade) openShop(player, RICHARD)
        }
    }
}
