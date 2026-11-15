package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** The Mage Arena bank's words: Gundai's and Lundail's transcripts (the Wiki's Transcript pages) and the sack's knife. */
object MageArenaBankLines : Lines("mage_arena_bank", "Transcript:Gundai; Transcript:Lundail; Sack (Wilderness)") {
    val GUNDAI_HELLO = line("gundai_hello", "the player's greeting to Gundai: what is he doing out here")
    val GUNDAI_BANKER = line("gundai_banker", "Gundai: the only banker around these dangerous parts")
    val GUNDAI_BANK = line("gundai_bank", "option 1: access the bank")
    val GUNDAI_PIN = line("gundai_pin", "option 2: PIN settings")
    val GUNDAI_COLLECT = line("gundai_collect", "option 3: collect items")
    val GUNDAI_NOW_I_KNOW = line("gundai_now_i_know", "option 4, which the player then says: well, now I know")
    val GUNDAI_KNOWLEDGE = line("gundai_knowledge", "Gundai's answer: knowledge is power")

    val LUNDAIL_HELLO = line("lundail_hello", "Lundail's greeting")
    val LUNDAIL_SELLING = line("lundail_selling", "option 1, which the player then says: what is he selling")
    val LUNDAIL_RUNES = line("lundail_runes", "Lundail: he sells rune stones; then his shop opens")
    val LUNDAIL_BUILDING = line("lundail_building", "option 2, which the player then says: the big old building above")
    val LUNDAIL_ARENA = line("lundail_arena", "Lundail: the mage battle arena")
    val LUNDAIL_WOW = line("lundail_wow", "the player: Wow.")
    val LUNDAIL_FRIED = line("lundail_fried", "Lundail: few return, most get fried")
    val LUNDAIL_NOTICED = line("lundail_noticed", "the player: they did notice the smell")
    val LUNDAIL_CLAIM = line("lundail_claim", "option 3: claim free runes")
    val LUNDAIL_NEED_DIARY = line("lundail_need_diary", "Lundail on Claim-runes without the easy Wilderness Diary")

    val SACK_KNIFE = line("sack_knife", "INFERRED words wanted: the sack between the webs giving a knife (the Wiki gives no message)")
}

/**
 * The Mage Arena bank under the hut north of the arena (Wilderness step W3.7), reached by the levers of step W3.2, as the
 * Wiki's Mage Arena bank, Gundai, Lundail, Lundail's Arena-side Rune Shop. and Sack (Wilderness) pages have it:
 *
 * - **Gundai** (`magearena_banker` 1600) talks as his transcript has him, and opens the bank, its PIN settings and the
 *   Grand Exchange collection box, from Talk-to or his Bank and Collect options, across his counter as other bankers.
 * - **Lundail** (`magearena_runeshop` 1601) talks as his transcript has him; Trade opens his rune shop (shops.json).
 *   Claim-runes, and his menu's third option, refuse: the free runes need the easy Wilderness Diary, which isn't built.
 * - **The sack** (14743 at 3093,3956, between the hut's two webs) gives a knife to a player without one, so nobody is
 *   stuck between the webs.
 *
 * Inferred: the sack's other searches being the empty sacks' line. Guessed: the chathead faces. Left out: Kolodion and the
 * Mage Arena (a miniquest, so no quests), the chamber guardian's god staves and the sparkling pool's god cape chamber
 * (both follow Mage Arena I), the loot chest (no loot keys yet).
 */
object MageArenaBank {
    const val GUNDAI = 1600
    const val LUNDAIL = 1601
    const val TALK_TO = 1
    const val CLAIM_RUNES = 4

    const val SACK = 14743
    const val SACK_X = 3093
    const val SACK_Z = 3956
    const val SEARCH = 1
    const val KNIFE = 946
}

fun registerMageArenaBank(world: World) {
    with(MageArenaBank) {
        world.npcInteractions.reachOverCounter(GUNDAI)
        world.handlers.onNpc(GUNDAI, TALK_TO) { op ->
            var ask = Bankers.Ask.NOTHING
            world.dialogues.start(this, op.npc) { ask = gundai() }
            when (ask) {
                Bankers.Ask.BANK -> world.bankScreen.open(player)
                Bankers.Ask.PIN -> world.bankPinSettings.open(player)
                Bankers.Ask.COLLECT -> world.grandExchangeScreen.openCollectionBox(player)
                Bankers.Ask.NOTHING -> Unit
            }
        }
        world.handlers.onNpc(GUNDAI, Bankers.BANK) { world.bankScreen.open(player) }
        world.handlers.onNpc(GUNDAI, Bankers.COLLECT) { world.grandExchangeScreen.openCollectionBox(player) }

        world.handlers.onNpc(LUNDAIL, TALK_TO) { op ->
            var trade = false
            world.dialogues.start(this, op.npc) { trade = lundail() }
            if (trade) world.shopTypes.ofNpc(LUNDAIL)?.let { world.shopScreen.open(player, it) }
        }
        world.handlers.onNpc(LUNDAIL, CLAIM_RUNES) { op ->
            world.dialogues.start(this, op.npc) { chatNpc(ChatAnim.NEUTRAL, MageArenaBankLines.LUNDAIL_NEED_DIARY) }
        }

        world.handlers.onLoc(SACK, SEARCH) { op ->
            val loc = op.loc!!
            arriveDelay()
            val between = loc.level == 0 && loc.x == SACK_X && loc.z == SACK_Z
            if (!between || player.inventory.count(KNIFE) > 0) return@onLoc world.message(player, SearchLines.SACKS_EMPTY)
            val knife = world.objTypes[KNIFE] ?: return@onLoc
            if (player.inventory.freeSlots == 0) return@onLoc world.message(player, SearchLines.SACKS_EMPTY)
            player.inventory.add(knife, 1)
            world.message(player, MageArenaBankLines.SACK_KNIFE)
        }
    }
}

private suspend fun Dialogue.gundai(): Bankers.Ask = with(MageArenaBankLines) {
    chatPlayer(ChatAnim.NEUTRAL, GUNDAI_HELLO)
    chatNpc(ChatAnim.NEUTRAL, GUNDAI_BANKER)
    when (choice(GUNDAI_BANK, GUNDAI_PIN, GUNDAI_COLLECT, GUNDAI_NOW_I_KNOW)) {
        1 -> return Bankers.Ask.BANK
        2 -> return Bankers.Ask.PIN
        3 -> return Bankers.Ask.COLLECT
    }
    chatPlayer(ChatAnim.NEUTRAL, GUNDAI_NOW_I_KNOW)
    chatNpc(ChatAnim.NEUTRAL, GUNDAI_KNOWLEDGE)
    Bankers.Ask.NOTHING
}

/** Lundail's talk: true when it ends in his shop. */
private suspend fun Dialogue.lundail(): Boolean = with(MageArenaBankLines) {
    chatNpc(ChatAnim.NEUTRAL, LUNDAIL_HELLO)
    when (choice(LUNDAIL_SELLING, LUNDAIL_BUILDING, LUNDAIL_CLAIM)) {
        1 -> {
            chatPlayer(ChatAnim.NEUTRAL, LUNDAIL_SELLING)
            chatNpc(ChatAnim.NEUTRAL, LUNDAIL_RUNES)
            return true
        }
        2 -> {
            chatPlayer(ChatAnim.NEUTRAL, LUNDAIL_BUILDING)
            chatNpc(ChatAnim.NEUTRAL, LUNDAIL_ARENA)
            chatPlayer(ChatAnim.NEUTRAL, LUNDAIL_WOW)
            chatNpc(ChatAnim.NEUTRAL, LUNDAIL_FRIED)
            chatPlayer(ChatAnim.NEUTRAL, LUNDAIL_NOTICED)
        }
        else -> chatNpc(ChatAnim.NEUTRAL, LUNDAIL_NEED_DIARY)
    }
    false
}
