package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.zone.ZoneLoc
import osrsps.game.script.ScriptContext

/**
 * Musa Point's words: Luthas's and Zembo's transcripts, the banana crate's lines from Transcript:Pirate's Treasure, and
 * the banana trees' (read 2026-11-17).
 */
object MusaPointLines : Lines("musa_point", "Transcript:Luthas; Transcript:Zembo; Transcript:Pirate's Treasure; Banana tree") {
    val LUTHAS_HELLO = line("luthas_hello", "Luthas's standard greeting")
    val EMPLOYMENT = line("employment", "his menu, option 1, which the player then says")
    val CRATE_READY = line("crate_ready", "Luthas: there's a crate ready to be loaded")
    val DEMAND = line("demand", "Luthas: the demand from Wydin's shop")
    val PAY_30 = line("pay_30", "Luthas: fill it with bananas for 30 gold")
    val CUSTOMS_ANNOYING = line("customs_annoying", "his menu, the customs-officer option, which the player then says")
    val KNOW_HER = line("know_her", "Luthas: he knows her pretty well")
    val NO_SEARCH = line("no_search", "Luthas: she doesn't search his crates any more")
    val WHEREABOUTS = line("whereabouts", "the player: where do they go")
    val WYDIN_SHOP = line("wydin_shop", "Luthas: a shop in Port Sarim run by Wydin")
    val TASK_DONE_YET = line("task_done_yet", "after accepting: Luthas asks if the task is done")
    val WHAT_AGAIN = line("what_again", "after accepting, option 1, which the player then says")
    val TASK_AGAIN = line("task_again", "Luthas repeats the task")
    val NOT_FULL = line("not_full", "after accepting, option 2 (the option's words)")
    val NOT_FULL_SAY = line("not_full_say", "option 2: what the player says (ends in three dots, the option doesn't)")
    val COME_BACK = line("come_back", "Luthas: come back when it is")
    val WHERE_DELIVERED = line("where_delivered", "the delivery option, which the player then says")
    val SELL_TO_WYDIN = line("sell_to_wydin", "Luthas: he sells them to Wydin")
    val FILLED = line("filled", "after completing: the player says the crate is filled")
    val WELL_DONE = line("well_done", "Luthas pays")
    val ANOTHER_CRATE = line("another_crate", "after completing, option 1, which the player then says")
    val YES_CERTAINLY = line("yes_certainly", "Luthas agrees")
    val NEW_CRATE = line("new_crate", "Luthas: the old crate is on the ship, a new one in its place")
    val ON_MY_WAY = line("on_my_way", "after completing, option 2 (the option's words, no full stop)")
    val ON_MY_WAY_SAY = line("on_my_way_say", "option 2: what the player says (with a full stop)")

    val PACK_ONE = line("pack_one", "a banana used on the crate")
    val PACK_ALL = line("pack_all", "the crate's Fill option")
    val CRATE_FULL = line("crate_full", "the message on packing into a full crate")
    val CRATE_HAS = line("crate_has", "the crate's Search, fewer than 10 bananas inside (the Wiki's 'banana[s]': banana for 1)", "count", "s")
    val CRATE_FULL_SEARCH = line("crate_full_search", "the crate's Search, 10 bananas inside")

    val BANANA_PICKED = line("banana_picked", "INFERRED words wanted: Pick on a banana tree (the Wiki gives no message)")
    val BANANA_NO_ROOM = line("banana_no_room", "INFERRED words wanted: Pick on a banana tree with a full inventory")
    val TREE_EMPTY = line("tree_empty", "INFERRED words wanted: Search on an empty banana tree")

    val ZEMBO_HELLO = line("zembo_hello", "Zembo's greeting")
    val ZEMBO_YES = line("zembo_yes", "his menu, option 1 (the shop opens, with no line from the player)")
    val ZEMBO_NO = line("zembo_no", "option 2, which the player then says")
}

object MusaShopKeeperLines : GeneralStoreLines("musa_shop_keeper", "Transcript:Shop keeper (Musa Point)", "shop keeper")

object MusaShopAssistantLines : GeneralStoreLines("musa_shop_assistant", "Transcript:Shop assistant (Musa Point)", "shop assistant")

/**
 * Musa Point (Karamja step K1.2), as the Wiki's Luthas, Zembo, Banana tree, Banana plantation, Karamja General Store and
 * Karamja Wines, Spirits, and Beers. pages and their transcripts have it:
 *
 * - **Luthas** (`luthas`) gives the banana job: fill his crate with 10 bananas and he pays 30 coins, then sets out an
 *   empty crate for another. His talk follows the job: before taking it, after, and with the crate full.
 * - **The crate** (`bananacrate` beside his house) takes bananas one at a time (a banana used on it) or all at once
 *   (Fill), up to 10; Search counts them. The crate is each player's own, kept in a varp of ours
 *   ([CustomVars.bananaCrateVarp]).
 * - **The banana trees** on the plantation give a banana per Pick, five to a tree, and then stand empty until they
 *   regrow, five minutes after the last pick.
 * - **Zembo** (`zembo`) and the **general store**'s keeper and assistant talk and open their shops (shops.json).
 *
 * Every account has finished Pirate's Treasure, so the rum-in-the-crate steps are left out. Inferred: the crate taking
 * bananas before the job is taken (the Wiki doesn't say it can't); the trees' pick and empty messages (none on the Wiki,
 * keys only); that each pick restarts the tree's five minutes. Guessed: the chathead faces; Fill with no bananas doing
 * nothing. Left out: Zembo's barcrawl option (Alfred Grimhand's Barcrawl), Luthas's clue step, the rum steps.
 */
object MusaPoint {
    const val LUTHAS = 3647
    const val ZEMBO = 13655
    const val SHOP_KEEPER = 2825
    const val SHOP_ASSISTANT = 2826
    const val TALK_TO = 1

    const val CRATE = 2072
    const val SEARCH = 1
    const val FILL = 2
    const val CRATE_SIZE = 10
    const val PAY = 30
    const val BANANA = 1963
    const val COINS = 995

    /** The five fruiting trees, full first; each Pick turns one into the next. */
    val TREES = 2073..2077
    const val TREE_EMPTY = 2078
    const val PICK = 1
    const val TREE_SEARCH = 1
    const val REGROW_TICKS = 500
    const val ANIM_PICK = 827

    private const val COUNT_MASK = 0xF
    private const val ACCEPTED = 0x10

    private fun varp(player: Player): Int? = player.vars.types.namedVarps[CustomVars.BANANA_CRATE]

    /** Bananas in the player's crate. */
    fun inCrate(player: Player): Int = varp(player)?.let { player.vars[it] and COUNT_MASK } ?: 0

    /** Whether the player has taken Luthas's job. */
    fun accepted(player: Player): Boolean = varp(player)?.let { player.vars[it] and ACCEPTED != 0 } ?: false

    internal fun set(player: Player, count: Int, accepted: Boolean) {
        val varp = varp(player) ?: return
        player.vars[varp] = count.coerceIn(0, CRATE_SIZE) or (if (accepted) ACCEPTED else 0)
    }

    /** Luthas's talk; returns true when he pays for a full crate. */
    internal suspend fun Dialogue.luthas(): Boolean = with(MusaPointLines) {
        val full = inCrate(player) >= CRATE_SIZE
        when {
            full && accepted(player) -> {
                chatPlayer(ChatAnim.HAPPY, FILLED)
                chatNpc(ChatAnim.HAPPY, WELL_DONE)
                return@with true
            }
            accepted(player) -> {
                chatNpc(ChatAnim.QUIZ, TASK_DONE_YET)
                when (choice(WHAT_AGAIN, NOT_FULL, WHERE_DELIVERED, CUSTOMS_ANNOYING)) {
                    1 -> {
                        chatPlayer(ChatAnim.QUIZ, WHAT_AGAIN)
                        chatNpc(ChatAnim.NEUTRAL, TASK_AGAIN)
                    }
                    2 -> {
                        chatPlayer(ChatAnim.NEUTRAL, NOT_FULL_SAY)
                        chatNpc(ChatAnim.NEUTRAL, COME_BACK)
                    }
                    3 -> whereDelivered()
                    else -> customs()
                }
            }
            else -> {
                chatNpc(ChatAnim.HAPPY, LUTHAS_HELLO)
                when (choice(EMPLOYMENT, CUSTOMS_ANNOYING)) {
                    1 -> {
                        chatPlayer(ChatAnim.QUIZ, EMPLOYMENT)
                        chatNpc(ChatAnim.HAPPY, CRATE_READY)
                        chatNpc(ChatAnim.NEUTRAL, DEMAND)
                        chatNpc(ChatAnim.NEUTRAL, PAY_30)
                        set(player, inCrate(player), true)
                    }
                    else -> customs()
                }
            }
        }
        false
    }

    /** After paying: the menu of his after-completing talk. */
    internal suspend fun Dialogue.luthasPaid() = with(MusaPointLines) {
        when (choice(ANOTHER_CRATE, ON_MY_WAY, WHERE_DELIVERED, CUSTOMS_ANNOYING)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, ANOTHER_CRATE)
                chatNpc(ChatAnim.HAPPY, YES_CERTAINLY)
                chatNpc(ChatAnim.NEUTRAL, NEW_CRATE)
                set(player, 0, true)
            }
            2 -> chatPlayer(ChatAnim.HAPPY, ON_MY_WAY_SAY)
            3 -> whereDelivered()
            else -> customs()
        }
    }

    private suspend fun Dialogue.whereDelivered() = with(MusaPointLines) {
        chatPlayer(ChatAnim.QUIZ, WHERE_DELIVERED)
        chatNpc(ChatAnim.NEUTRAL, SELL_TO_WYDIN)
    }

    private suspend fun Dialogue.customs() = with(MusaPointLines) {
        chatPlayer(ChatAnim.QUIZ, CUSTOMS_ANNOYING)
        chatNpc(ChatAnim.NEUTRAL, KNOW_HER)
        chatNpc(ChatAnim.NEUTRAL, NO_SEARCH)
        chatPlayer(ChatAnim.QUIZ, WHEREABOUTS)
        chatNpc(ChatAnim.NEUTRAL, WYDIN_SHOP)
    }

    /** Zembo's talk; true when the player asked to see his shop. */
    internal suspend fun Dialogue.zembo(): Boolean = with(MusaPointLines) {
        chatNpc(ChatAnim.HAPPY, ZEMBO_HELLO)
        if (choice(ZEMBO_YES, ZEMBO_NO) == 1) return@with true
        chatPlayer(ChatAnim.NEUTRAL, ZEMBO_NO)
        false
    }
}

fun registerMusaPoint(world: World) {
    with(MusaPoint) {
        fun openShop(player: Player, npc: Int) {
            world.shopTypes.ofNpc(npc)?.let { world.shopScreen.open(player, it) }
        }

        world.handlers.onNpc(LUTHAS, TALK_TO) { op ->
            var paid = false
            world.dialogues.start(this, op.npc) { paid = luthas() }
            if (!paid) return@onNpc
            // He pays, then the crate is shipped: an empty one stands in its place, and the job ends until asked again.
            set(player, 0, false)
            world.objTypes[COINS]?.let { player.inventory.add(it, PAY) }
            world.dialogues.start(this, op.npc) { luthasPaid() }
        }

        world.handlers.onLoc(CRATE, SEARCH) {
            arriveDelay()
            val count = inCrate(player)
            if (count >= CRATE_SIZE) {
                world.dialogues.start(this) { mesbox(MusaPointLines.CRATE_FULL_SEARCH) }
            } else {
                world.message(player, MusaPointLines.CRATE_HAS, "count" to count.toString(), "s" to if (count == 1) "" else "s")
            }
        }
        world.handlers.onLoc(CRATE, FILL) { pack(world, all = true) }
        world.handlers.onObjOnLoc(CRATE, BANANA) { pack(world, all = false) }

        for (tree in TREES) {
            world.handlers.onLoc(tree, PICK) { op -> pickBanana(world, op.loc!!) }
        }
        world.handlers.onLoc(TREE_EMPTY, TREE_SEARCH) {
            arriveDelay()
            world.message(player, MusaPointLines.TREE_EMPTY)
        }

        world.handlers.onNpc(ZEMBO, TALK_TO) { op ->
            var trade = false
            world.dialogues.start(this, op.npc) { trade = zembo() }
            if (trade) openShop(player, ZEMBO)
        }
        for ((id, lines) in listOf(SHOP_KEEPER to MusaShopKeeperLines, SHOP_ASSISTANT to MusaShopAssistantLines)) {
            world.handlers.onNpc(id, TALK_TO) { op ->
                var trade = false
                world.dialogues.start(this, op.npc) { trade = with(LumbridgeShopkeepers) { keeper(lines) } }
                if (trade) openShop(player, id)
            }
        }
    }
}

/** Packs one banana, or as many as fit, into the player's crate. */
private suspend fun ScriptContext.pack(world: World, all: Boolean) = with(MusaPoint) {
    arriveDelay()
    val count = inCrate(player)
    val held = player.inventory.count(BANANA).toInt()
    if (held == 0) return@with
    if (count >= CRATE_SIZE) return@with world.message(player, MusaPointLines.CRATE_FULL)
    val packed = if (all) minOf(held, CRATE_SIZE - count) else 1
    Ned.take(player, BANANA, packed)
    set(player, count + packed, accepted(player))
    world.dialogues.start(this@pack) { mesbox(if (all) MusaPointLines.PACK_ALL else MusaPointLines.PACK_ONE) }
}

/** One banana off a tree, which then shows one fewer; the tree is full again five minutes after its last pick. */
private suspend fun ScriptContext.pickBanana(world: World, loc: ZoneLoc) = with(MusaPoint) {
    if (player.inventory.freeSlots == 0) return@with world.message(player, MusaPointLines.BANANA_NO_ROOM)
    player.anim(ANIM_PICK)
    delay(1)
    // Someone else may have picked it this tick.
    if (!world.locs.exists(loc)) return@with
    world.locs.replace(loc, loc.id + 1) ?: return@with
    world.locs.restoreAfter(loc.x, loc.z, loc.level, loc.layer, REGROW_TICKS)
    world.objTypes[BANANA]?.let { player.inventory.add(it, 1) }
    world.message(player, MusaPointLines.BANANA_PICKED)
}
