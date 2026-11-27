package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.instance.Instance
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext
import osrsps.game.shop.ShopScreen
import osrsps.game.social.ClanStore

/** Raqueel's, Gem's, Gardener Jay Jr.'s and Hannibal's lines; the words are from the Wiki's transcripts of each. */
object ClanHallPeopleLines : Lines("clan_hall_people", "Clan hall people") {
    val R_HI_AGAIN = line("raqueel_hi_again", "Raqueel: the player's greeting")
    val R_GREETING_1 = line("raqueel_greeting_1", "Raqueel's greeting, the geese")
    val R_GREETING_2 = line("raqueel_greeting_2", "Raqueel's greeting, the new song")
    val R_GREETING_3 = line("raqueel_greeting_3", "Raqueel's greeting, the pie")
    val R_BUY = line("raqueel_buy", "the player: buy some beer (also the option)")
    val R_PRICE = line("raqueel_price", "Raqueel: the beer's price")
    val R_BUY_OPTION = line("raqueel_buy_option", "the option: buy beer for 5 coins")
    val R_TOO_EXPENSIVE = line("raqueel_too_expensive", "the option and the player: too expensive")
    val R_NOT_ENOUGH = line("raqueel_not_enough", "the player: without 5 coins")
    val R_COME_BACK = line("raqueel_come_back", "Raqueel: come back with the money")
    val R_PRICES = line("raqueel_prices", "Raqueel: she doesn't choose the prices")
    val R_FAIR = line("raqueel_fair", "the player: fair enough")
    val R_ABOUT = line("raqueel_about", "the option and the player: tell me more about yourself")
    val R_WHERE_BEGIN = line("raqueel_where_begin", "Raqueel: where to begin")
    val R_STORY_1 = line("raqueel_story_1", "Raqueel's story: Gem's name")
    val R_STORY_2 = line("raqueel_story_2", "Raqueel's story: the railing")
    val R_STORY_3 = line("raqueel_story_3", "Raqueel's story: purple")
    val R_STORY_4 = line("raqueel_story_4", "Raqueel's story: the geese")
    val R_FASCINATING = line("raqueel_fascinating", "the player after a story")
    val R_GO = line("raqueel_go", "the option and the player: goodbye")
    val R_SEE_YOU = line("raqueel_see_you", "Raqueel: see you later")

    val G_WHO = line("gem_who", "the player to Gem: who are you")
    val G_NAME = line("gem_name", "Gem: her name")
    val G_MISSED = line("gem_missed", "Raqueel, calling over: a missed spot")
    val G_GO = line("gem_go", "Gem: she has to go")

    val J_HELLO = line("jay_hello", "the player to Gardener Jay Jr.: hello")
    val J_BUSY = line("jay_busy", "Jay: go away")
    val J_DOING = line("jay_doing", "the player: busy doing what")
    val J_GARDENING = line("jay_gardening", "Jay: gardening")
    val J_HAND = line("jay_hand", "the player: need a hand")
    val J_NO = line("jay_no", "Jay: no")
    val J_ANGRY = line("jay_angry", "the player: why so angry")
    val J_FLOWERS = line("jay_flowers", "Jay: flowers good")
    val J_LEAVE = line("jay_leave", "the player: leaving him to it")

    val H_GREETING_1 = line("hannibal_greeting_1", "the player to Hannibal: excuse me")
    val H_GREETING_2 = line("hannibal_greeting_2", "the player to Hannibal: hello")
    val H_GREETING_3 = line("hannibal_greeting_3", "the player to Hannibal: hello there")
    val H_GREETING_4 = line("hannibal_greeting_4", "the player to Hannibal: hi there")
    val H_SLEEPING = line("hannibal_sleeping", "the box: he is sleeping")
    val H_PET_1 = line("hannibal_pet_1", "Pet, first message")
    val H_PET_1B = line("hannibal_pet_1b", "Pet, first message's second line")
    val H_PET_2 = line("hannibal_pet_2", "Pet, second message")
    val H_PET_2B = line("hannibal_pet_2b", "Pet, second message's second line")
    val H_FEED = line("hannibal_feed", "Feed: the player thinks of fish")
}

/**
 * The clan hall's people, put in each hall when it is made ([ClanHall]). Sources: the OSRS Wiki's pages and transcripts
 * of each, read 2026-11-19 (their map pins give where they stand), and the 242 cache:
 * - Four bankers (clan_hall_banker_0 to 3, 10734 to 10737; Talk-to, Bank, Collect) behind the bank booths (the Wiki's
 *   pin, a rectangle from 1747, 5478), who work as every banker does ([Bankers]).
 * - Raqueel the barmaid (10739 to 10746, by the clan's colour) at 1758, 5483 within 3 tiles: her standard talk (one of
 *   three greetings), beer for 5 coins, one of four stories, or goodbye.
 * - Gem (10748 to 10755, by colour) at 1758, 5479 within 3 tiles, whose talk Raqueel cuts off.
 * - Gardener Jay Jr. (clan_hall_gardener 10756) in the garden at 1773, 5485 within 6 tiles.
 * - Hannibal the cat (clan_medieval_stool_cat 11651; Pet, Feed, Talk) on his stool in the kitchen at 1748, 5466: Pet
 *   gives one of two pairs of messages; Feed has the player think of fish; Talk gives one of four greetings and the box
 *   that he is asleep, as without a catspeak amulet.
 *
 * Raqueel and Gem wear the colour the clan had when its hall was made (inferred from the eight versions; guessed: a
 * colour changed while the hall stands shows at its next making). Guessed: which banker stands at which booth, and the
 * chat animations.
 * Not built: Raqueel's first-time talk (nothing records it), the beer's own handover box, Hannibal's talk with a
 * catspeak amulet (five responses in turn, which nothing records either), fish used on him, and the hall's dog
 * (clan_hall_dog 10760, which Hannibal hears "sometimes").
 */
class ClanHallPeople(private val world: World) {
    fun register() {
        for (banker in BANKERS) {
            world.npcInteractions.reachOverCounter(banker)
            world.handlers.onNpc(banker, Bankers.TALK_TO) { op ->
                var ask = Bankers.Ask.NOTHING
                world.dialogues.start(this, op.npc) { ask = with(Bankers) { banker() } }
                when (ask) {
                    Bankers.Ask.BANK -> world.bankScreen.open(player)
                    Bankers.Ask.PIN -> world.bankPinSettings.open(player)
                    Bankers.Ask.COLLECT -> world.grandExchangeScreen.openCollectionBox(player)
                    Bankers.Ask.NOTHING -> Unit
                }
            }
            world.handlers.onNpc(banker, Bankers.BANK) { world.bankScreen.open(player) }
            world.handlers.onNpc(banker, Bankers.COLLECT) { world.grandExchangeScreen.openCollectionBox(player) }
        }
        for (raqueel in RAQUEEL) world.handlers.onNpc(raqueel, TALK_TO) { op -> world.dialogues.start(this, op.npc) { raqueel(this@onNpc) } }
        for (gem in GEM) {
            world.handlers.onNpc(gem, TALK_TO) { op ->
                val raqueelType = world.npcTypes[RAQUEEL[GEM.indexOf(gem)]]
                world.dialogues.start(this, op.npc) { gem(raqueelType) }
            }
        }
        world.handlers.onNpc(GARDENER, TALK_TO) { op -> world.dialogues.start(this, op.npc) { gardener() } }
        world.handlers.onNpc(HANNIBAL, PET) {
            val (first, second) = if (world.random.nextInt(2) == 0) {
                ClanHallPeopleLines.H_PET_1 to ClanHallPeopleLines.H_PET_1B
            } else {
                ClanHallPeopleLines.H_PET_2 to ClanHallPeopleLines.H_PET_2B
            }
            world.message(player, first)
            world.message(player, second)
        }
        world.handlers.onNpc(HANNIBAL, FEED) { op -> world.dialogues.start(this, op.npc) { chatPlayer(ChatAnim.NEUTRAL, ClanHallPeopleLines.H_FEED) } }
        world.handlers.onNpc(HANNIBAL, TALK) { op -> world.dialogues.start(this, op.npc) { hannibal() } }
    }

    /** Puts the hall's people into [instance], [clan]'s hall, just made. */
    fun spawn(instance: Instance, clan: ClanStore.Clan) {
        val colour = world.clans.colour(clan).coerceIn(0, RAQUEEL.size - 1)
        for ((id, at) in listOf(
            BANKERS[0] to SPOTS[0], BANKERS[1] to SPOTS[1], BANKERS[2] to SPOTS[2], BANKERS[3] to SPOTS[3],
            RAQUEEL[colour] to SPOTS[4], GEM[colour] to SPOTS[5], GARDENER to SPOTS[6], HANNIBAL to SPOTS[7],
        )) {
            val type = world.npcTypes[id] ?: continue
            val (x, z, wander) = at
            world.spawnNpc(
                NpcSpawn(
                    type, instance.baseX + x - ClanHall.TEMPLATE_X, instance.baseZ + z - ClanHall.TEMPLATE_Z, 0,
                    wander = wander, source = "clan hall", respawns = false,
                ),
            )
        }
    }

    private suspend fun Dialogue.raqueel(context: ScriptContext) = with(ClanHallPeopleLines) {
        chatPlayer(ChatAnim.HAPPY, R_HI_AGAIN)
        chatNpc(ChatAnim.HAPPY, listOf(R_GREETING_1, R_GREETING_2, R_GREETING_3)[world.random.nextInt(3)])
        when (choice(R_BUY, R_ABOUT, R_GO)) {
            1 -> {
                chatPlayer(ChatAnim.HAPPY, R_BUY)
                chatNpc(ChatAnim.HAPPY, R_PRICE)
                when (choice(R_BUY_OPTION, R_TOO_EXPENSIVE)) {
                    1 -> buyBeer(context)
                    2 -> {
                        chatPlayer(ChatAnim.NEUTRAL, R_TOO_EXPENSIVE)
                        chatNpc(ChatAnim.NEUTRAL, R_PRICES)
                        chatPlayer(ChatAnim.NEUTRAL, R_FAIR)
                    }
                }
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, R_ABOUT)
                chatNpc(ChatAnim.HAPPY, R_WHERE_BEGIN)
                chatNpc(ChatAnim.HAPPY, listOf(R_STORY_1, R_STORY_2, R_STORY_3, R_STORY_4)[world.random.nextInt(4)])
                chatPlayer(ChatAnim.NEUTRAL, R_FASCINATING)
            }
            3 -> {
                chatPlayer(ChatAnim.NEUTRAL, R_GO)
                chatNpc(ChatAnim.HAPPY, R_SEE_YOU)
            }
        }
    }

    /** 5 coins for a beer (the Wiki: "receives beer"). */
    private suspend fun Dialogue.buyBeer(context: ScriptContext): Unit = with(ClanHallPeopleLines) {
        val player = context.player
        val beer = world.objTypes[BEER]
        if (player.inventory.count(COINS) < PRICE || beer == null) {
            chatPlayer(ChatAnim.SAD, R_NOT_ENOUGH)
            chatNpc(ChatAnim.NEUTRAL, R_COME_BACK)
            return@with
        }
        // Room for the beer: the coins' slot frees up only when they are the last coins held.
        if (player.inventory.freeSlots == 0 && player.inventory.count(COINS) > PRICE) {
            world.message(player, osrsps.game.GameLines.NO_SPACE)
            return@with
        }
        var owed = PRICE
        for ((slot, obj) in player.inventory.contents()) {
            if (owed == 0) break
            if (obj.id == COINS) owed -= player.inventory.remove(slot, owed)
        }
        player.inventory.add(beer, 1)
    }

    private suspend fun Dialogue.gem(raqueel: osrsps.game.NpcType?) = with(ClanHallPeopleLines) {
        chatPlayer(ChatAnim.QUIZ, G_WHO)
        chatNpc(ChatAnim.NEUTRAL, G_NAME)
        if (raqueel != null) chatNpc(raqueel, raqueel.name, ChatAnim.HAPPY, G_MISSED)
        chatNpc(ChatAnim.SAD, G_GO)
    }

    private suspend fun Dialogue.gardener() = with(ClanHallPeopleLines) {
        chatPlayer(ChatAnim.HAPPY, J_HELLO)
        chatNpc(ChatAnim.ANGRY, J_BUSY)
        chatPlayer(ChatAnim.QUIZ, J_DOING)
        chatNpc(ChatAnim.ANGRY, J_GARDENING)
        chatPlayer(ChatAnim.QUIZ, J_HAND)
        chatNpc(ChatAnim.ANGRY, J_NO)
        chatPlayer(ChatAnim.CONFUSED, J_ANGRY)
        chatNpc(ChatAnim.ANGRY, J_FLOWERS)
        chatPlayer(ChatAnim.NEUTRAL, J_LEAVE)
    }

    private suspend fun Dialogue.hannibal() = with(ClanHallPeopleLines) {
        chatPlayer(ChatAnim.HAPPY, listOf(H_GREETING_1, H_GREETING_2, H_GREETING_3, H_GREETING_4)[world.random.nextInt(4)])
        mesbox(H_SLEEPING)
    }

    companion object {
        val BANKERS = listOf(10734, 10735, 10736, 10737)

        /** By the clan's colour, enum_3819's order: black, orange, blue, red, green, yellow, light blue, pink. */
        val RAQUEEL = (10739..10746).toList()
        val GEM = (10748..10755).toList()
        const val GARDENER = 10756
        const val HANNIBAL = 11651

        const val TALK_TO = 1
        const val PET = 1
        const val FEED = 3
        const val TALK = 4

        const val BEER = 1917
        const val COINS = ShopScreen.COINS
        const val PRICE = 5

        /**
         * Where each stands in the hall's template, and how far it wanders: the four bankers north of the booths
         * (1745 to 1748, 5477; level 1 in the map files, over a bridge flag), Raqueel, Gem, Jay and Hannibal by the
         * Wiki's pins.
         */
        val SPOTS = listOf(
            Triple(1745, 5478, 0), Triple(1746, 5478, 0), Triple(1747, 5478, 0), Triple(1748, 5478, 0),
            Triple(1758, 5483, 3), Triple(1758, 5479, 3), Triple(1773, 5485, 6), Triple(1748, 5466, 0),
        )
    }
}
