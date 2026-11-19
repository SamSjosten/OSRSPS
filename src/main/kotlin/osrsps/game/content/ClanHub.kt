package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.social.ClanStore

/** The Clan Hub's Clerk's lines; from the Wiki's Transcript:Clerk (Clan Hub). */
object ClerkLines : Lines("clan_clerk", "Transcript:Clerk (Clan Hub)") {
    val GOOD_DAY = line("good_day", "Talk-to, his first line")
    val WHO = line("who", "option: Who are you?, which the player then says")
    val NAME = line("name", "his reply to Who are you?: his name, and gathering people into clans")
    val WHAT_DO = line("what_do", "option: What do you do here?, which the player then says")
    val MISSION = line("mission", "his reply to What do you do here?: administering clans")
    val WHAT_CLAN = line("what_clan", "option: What is a clan?, which the player then says")
    val CLANS_1 = line("clans_1", "his reply to What is a clan?, line 1: groups with shared interests")
    val CLANS_2 = line("clans_2", "line 2: skills, monsters, battling other clans")
    val CLANS_3 = line("clans_3", "line 3: like-minded company, the clan community")
    val GIVE = line("give", "option: I'm in a clan. Can you give me anything?, which the player then says")
    val GOODBYE = line("goodbye", "option: Goodbye., which the player then says")
    val COME_BACK = line("come_back", "his reply to Goodbye.: come back any time")
    val WHY_HERE = line("why_here", "option after What do you do here?: why here specifically, which the player then says")
    val GATHER = line("gather", "his reply: he goes where people gather, the Grand Exchange")
    val CROWDED = line("crowded", "the player: a crowded place")
    val CORNER = line("corner", "his reply: the charming corner for the clan hub")
    val WHAT_HUB = line("what_hub", "option: What's the clan hub?, which the player then says")
    val HUB_1 = line("hub_1", "his reply, line 1: his corner, where clans are born and recruits found")
    val HUB_2 = line("hub_2", "line 2: members reach their Clan Hall from here")
    val HOW_JOIN = line("how_join", "option after What is a clan?: How do I join a clan?, which the player then says")
    val JOIN_1 = line("join_1", "his reply, line 1: form your own, or apply to one")
    val JOIN_2 = line("join_2", "line 2: four friends and time in the world to form one")
    val JOIN_3 = line("join_3", "line 3: the clan recruitment board")
    val OFFER = line("offer", "option after What is a clan?: What can a clan offer me?, which the player then says")
    val OFFER_1 = line("offer_1", "his reply, line 1: an official, hierarchical gathering")
    val OFFER_2 = line("offer_2", "line 2: a chat channel, a Clan Hall and ranks")
    val OFFER_3 = line("offer_3", "line 3: ranks from pre-sets set permissions")
    val OFFER_4 = line("offer_4", "line 4: for example kicking, or the Clan Coffer")
    val WHAT_COFFER = line("what_coffer", "option: What is the Clan Coffer?, which the player then says")
    val COFFER = line("coffer", "his reply: the shared coin store")
    val WHAT_HALL = line("what_hall", "option: What is a Clan Hall?, which the player then says")
    val HALL = line("hall", "his reply: a private area, ranks or guests let in")
    val HOUSE = line("house", "the player: why not a house instead?")
    val BOTH = line("both", "his reply: you can have both")
    val NO_SPACE = line("no_space", "I'm in a clan... with a full inventory: cloaks and vexilla, but no space")
    val FREE_SPACE = line("free_space", "the player: better free up some space")
    val ANYTHING_ELSE = line("anything_else", "his reply: anything else to ask")
    val CERTAINLY = line("certainly", "I'm in a clan... with room: cloaks and vexilla, before the items box")
    val THANKS = line("thanks", "the player, once the inventory is full after taking them")
    val NOT_IN_CLAN = line("not_in_clan", "I'm in a clan... from someone in none (guessed: the transcript doesn't have it)")
    val TITLE = line("title", "the items box's title (guessed: the transcript doesn't give it)")
}

/**
 * Misthalin's game-wide systems, the Clan Hub (the south-east corner of the Grand Exchange): its Clerk, who hands clan
 * members a clan cloak or vexillum in their clan's colour. The Hub's founding panel and the clans themselves are
 * [osrsps.game.social.Clans]. Sources: the OSRS Wiki's Clerk (Clan Hub), Transcript:Clerk (Clan Hub), Clan cloak and Clan
 * vexillum pages, read 2026-11-19, and the 242 cache (clan_clerk 10759, ops Talk-to and Items; the cloaks and vexilla).
 *
 * - Talk-to follows the transcript's tree: four first questions and Goodbye, with each answer offering the others again
 *   as it shows. "I'm in a clan. Can you give me anything?" says he has no room for them with a full inventory, else
 *   opens a box of the cloak and the vexillum, one taken per pick, opened again while there is room; once the
 *   inventory is full the player says thanks.
 * - Items (op 3) opens the same box at once (inferred: the Wiki doesn't say what it does).
 * - The cloak and vexillum are the clan's colour ([osrsps.game.social.Clans.colour], the clan's motif, which the clan
 *   settings page's motif button changes): one item of each colour in the cache, black to pink.
 *
 * Guessed: what he says to someone in no clan (a line of our own), the box's title, that he gives one at a time
 * whatever the box's quantity, and that he stands still (mejrs's live list has him on one tile).
 */
object ClanHub {
    const val CLERK = 10759
    const val TALK_TO = 1
    const val ITEMS = 3

    /** The clan cloaks and vexilla, by colour in enum_3819's order (the cache: black, orange, blue, ..., pink). */
    val CLOAKS = intArrayOf(25712, 25714, 25715, 25716, 25717, 25718, 25719, 25720)
    val VEXILLA = intArrayOf(25721, 25723, 25724, 25725, 25726, 25727, 25728, 25729)

    private enum class Topic { WHO, WHAT_DO, WHAT_CLAN, GIVE, GOODBYE, WHY_HERE, WHAT_HUB, HOW_JOIN, OFFER, COFFER, HALL }

    internal suspend fun Dialogue.clerk(world: World) {
        chatNpc(ChatAnim.HAPPY, ClerkLines.GOOD_DAY)
        talk(world, listOf(Topic.WHO, Topic.WHAT_DO, Topic.WHAT_CLAN, Topic.GIVE, Topic.GOODBYE))
    }

    /** The menu of [topics], then the answer picked; answers that offer more menus loop through here. */
    private suspend fun Dialogue.talk(world: World, start: List<Topic>) {
        var topics = start
        while (true) {
            val picked = topics[choice(*topics.map(::option).toTypedArray()) - 1]
            topics = answer(world, picked) ?: return
        }
    }

    private fun option(topic: Topic) = when (topic) {
        Topic.WHO -> ClerkLines.WHO
        Topic.WHAT_DO -> ClerkLines.WHAT_DO
        Topic.WHAT_CLAN -> ClerkLines.WHAT_CLAN
        Topic.GIVE -> ClerkLines.GIVE
        Topic.GOODBYE -> ClerkLines.GOODBYE
        Topic.WHY_HERE -> ClerkLines.WHY_HERE
        Topic.WHAT_HUB -> ClerkLines.WHAT_HUB
        Topic.HOW_JOIN -> ClerkLines.HOW_JOIN
        Topic.OFFER -> ClerkLines.OFFER
        Topic.COFFER -> ClerkLines.WHAT_COFFER
        Topic.HALL -> ClerkLines.WHAT_HALL
    }

    /** Says [topic]'s answer; returns the menu that follows it as the transcript has it, or null when the talk ends. */
    private suspend fun Dialogue.answer(world: World, topic: Topic): List<Topic>? = with(ClerkLines) {
        chatPlayer(ChatAnim.QUIZ, option(topic))
        when (topic) {
            Topic.WHO -> {
                chatNpc(ChatAnim.HAPPY, NAME)
                listOf(Topic.WHAT_DO, Topic.WHAT_CLAN, Topic.GIVE, Topic.GOODBYE)
            }
            Topic.WHAT_DO -> {
                chatNpc(ChatAnim.HAPPY, MISSION)
                listOf(Topic.WHY_HERE, Topic.WHO, Topic.WHAT_CLAN, Topic.GIVE, Topic.GOODBYE)
            }
            Topic.WHAT_CLAN -> {
                chatNpc(ChatAnim.HAPPY, CLANS_1)
                chatNpc(ChatAnim.HAPPY, CLANS_2)
                chatNpc(ChatAnim.HAPPY, CLANS_3)
                listOf(Topic.HOW_JOIN, Topic.OFFER, Topic.GIVE, Topic.GOODBYE)
            }
            Topic.WHY_HERE -> {
                chatNpc(ChatAnim.HAPPY, GATHER)
                chatPlayer(ChatAnim.NEUTRAL, CROWDED)
                chatNpc(ChatAnim.HAPPY, CORNER)
                listOf(Topic.WHAT_HUB, Topic.WHAT_CLAN, Topic.GIVE, Topic.GOODBYE)
            }
            Topic.WHAT_HUB -> {
                chatNpc(ChatAnim.HAPPY, HUB_1)
                chatNpc(ChatAnim.HAPPY, HUB_2)
                // "previous": the menu after Sure, but why are you here specifically?
                listOf(Topic.WHAT_HUB, Topic.WHAT_CLAN, Topic.GIVE, Topic.GOODBYE)
            }
            Topic.HOW_JOIN -> {
                chatNpc(ChatAnim.HAPPY, JOIN_1)
                chatNpc(ChatAnim.HAPPY, JOIN_2)
                chatNpc(ChatAnim.HAPPY, JOIN_3)
                // The transcript repeats his first line here, then gives no menu: the talk ends (inferred).
                chatNpc(ChatAnim.HAPPY, JOIN_1)
                null
            }
            Topic.OFFER -> {
                chatNpc(ChatAnim.HAPPY, OFFER_1)
                chatNpc(ChatAnim.HAPPY, OFFER_2)
                chatNpc(ChatAnim.HAPPY, OFFER_3)
                chatNpc(ChatAnim.HAPPY, OFFER_4)
                OFFER_MENU
            }
            Topic.COFFER -> {
                chatNpc(ChatAnim.HAPPY, COFFER)
                OFFER_MENU
            }
            Topic.HALL -> {
                chatNpc(ChatAnim.HAPPY, HALL)
                chatPlayer(ChatAnim.QUIZ, HOUSE)
                chatNpc(ChatAnim.HAPPY, BOTH)
                // "initial": back to the first menu.
                listOf(Topic.WHO, Topic.WHAT_DO, Topic.WHAT_CLAN, Topic.GIVE, Topic.GOODBYE)
            }
            Topic.GOODBYE -> {
                chatNpc(ChatAnim.HAPPY, COME_BACK)
                null
            }
            Topic.GIVE -> give(world)
        }
    }

    private val OFFER_MENU = listOf(Topic.COFFER, Topic.HALL, Topic.GOODBYE, Topic.GIVE)

    /** I'm in a clan. Can you give me anything?: the items, or why not, then the first menu again or the end. */
    private suspend fun Dialogue.give(world: World): List<Topic>? = with(ClerkLines) {
        val clan = world.clans.clanOf(player)
        if (clan == null) {
            chatNpc(ChatAnim.NEUTRAL, NOT_IN_CLAN)
            return null
        }
        if (player.inventory.freeSlots == 0) {
            chatNpc(ChatAnim.NEUTRAL, NO_SPACE)
            chatPlayer(ChatAnim.NEUTRAL, FREE_SPACE)
            chatNpc(ChatAnim.HAPPY, ANYTHING_ELSE)
            // "other": the first menu but this option.
            return listOf(Topic.WHO, Topic.WHAT_DO, Topic.WHAT_CLAN, Topic.GOODBYE)
        }
        chatNpc(ChatAnim.HAPPY, CERTAINLY)
        items(world, clan)
        if (player.inventory.freeSlots == 0) chatPlayer(ChatAnim.HAPPY, THANKS)
        return null
    }

    /**
     * The box of [clan]'s cloak and vexillum, opened again after each pick while the inventory has room. Each pick checks
     * again that the player is still in the clan (they may have left while the box was open) and takes its colour then.
     */
    internal suspend fun Dialogue.items(world: World, clan: ClanStore.Clan? = world.clans.clanOf(player)) {
        if (clan == null) return chatNpc(ChatAnim.NEUTRAL, ClerkLines.NOT_IN_CLAN)
        if (player.inventory.freeSlots == 0) return chatNpc(ChatAnim.NEUTRAL, ClerkLines.NO_SPACE)
        while (player.inventory.freeSlots > 0) {
            val colour = world.clans.colour(clan)
            val (index, quantity) = makeX(ClerkLines.TITLE, listOf(CLOAKS[colour], VEXILLA[colour]), 1)
            if (quantity <= 0) return
            val now = world.clans.clanOf(player)?.takeIf { it.id == clan.id } ?: return
            val id = (if (index == 0) CLOAKS else VEXILLA)[world.clans.colour(now)]
            val type = world.objTypes[id] ?: return
            if (player.inventory.add(type, 1) == 0) return
        }
    }
}

/** The Clan Hub's Clerk. */
fun registerClanHub(world: World) {
    with(ClanHub) {
        world.handlers.onNpc(CLERK, TALK_TO) { op -> world.dialogues.start(this, op.npc) { clerk(world) } }
        world.handlers.onNpc(CLERK, ITEMS) { op -> world.dialogues.start(this, op.npc) { items(world) } }
    }
}
