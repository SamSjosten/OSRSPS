package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

private val logger = KotlinLogging.logger {}

/**
 * Wilfred's lines; the words are in the dialogue file, from the Wiki's Transcript:Wilfred, read 2026-11-08. Keys marked
 * "missing" have no transcript.
 */
object WilfredLines : Lines("wilfred", "Transcript:Wilfred") {
    val GREETING = line("greeting", "Standard dialogue: his greeting")
    val CHOPPING = line("chopping", "first menu: advice on chopping trees, which the player then says")
    val CHOP_1 = line("chop_1", "chopping: an axe is all you need")
    val CHOP_2 = line("chop_2", "chopping: the kinds of tree")
    val CHOP_3 = line("chop_3", "chopping: rarer trees need more expertise")
    val TREE_HINT = line("tree_hint", "chopping: the tree icon's box (item 9713)")
    val THATS_IT = line("thats_it", "chopping: the player asking if that's it")
    val CHOP_4 = line("chop_4", "chopping: pretty much")
    val WC_GUIDE_HINT = line("wc_guide_hint", "chopping: the Skills panel box")
    val FIRES = line("fires", "first menu: advice on lighting fires, which the player then says")
    val FIRES_1 = line("fires_1", "fires: a tinderbox and logs")
    val FIRES_2 = line("fires_2", "fires: not much more advice")
    val FM_GUIDE_HINT = line("fm_guide_hint", "fires: the Skills panel box")
    val TOOLS = line("tools", "first menu: tools he could give, which the player then says")
    val HAS_AXE = line("has_axe", "tools, owning an axe: his offer of a tinderbox")
    val HAS_TINDERBOX = line("has_tinderbox", "tools, owning a tinderbox: his offer of an axe")
    val HAS_BOTH = line("has_both", "tools, owning both: his line")
    val YES_PLEASE = line("yes_please", "offer menu: yes please, which the player then says")
    val NO_THANKS = line("no_thanks", "offer menu: no thanks, which the player then says")
    val OFFER = line("offer", "tools, owning neither: his offer of an axe, a tinderbox or both")
    val AXE_PLEASE = line("axe_please", "tools menu: an axe, which the player then says")
    val TINDERBOX_PLEASE = line("tinderbox_please", "tools menu: a tinderbox, which the player then says")
    val BOTH_PLEASE = line("both_please", "tools menu: both, which the player then says")
    val NEITHER = line("neither", "tools menu: neither, which the player then says")
    val GIVES_AXE = line("gives_axe", "the box with the bronze axe")
    val GIVES_TINDERBOX = line("gives_tinderbox", "the box with the tinderbox")
    val GIVES_BOTH = line("gives_both", "the box with the bronze axe and the tinderbox")
    val NO_ROOM = line("no_room", "an axe or a tinderbox, a full backpack: his line (missing)")
    val NO_ROOM_BOTH = line("no_room_both", "both tools, fewer than two free slots: his line (missing)")
    val THANKS = line("thanks", "after a box: the player's thanks")
    val DONT_MENTION_IT = line("dont_mention_it", "after a box: his answer")
    val FAIR_ENOUGH = line("fair_enough", "no thanks or neither: his answer")
    val CAPE = line("cape", "first menu: the cape he's wearing, which the player then says")
    val CAPE_WHAT = line("cape_what", "the cape: his question")
    val BUY_CAPE = line("buy_cape", "cape menu: buying one, which the player then says")
    val HOOD = line("hood", "cape menu: another hood, which the player then says")
    val PRICE = line("price", "buying a cape: his price (missing)")
    val ACCEPT = line("accept", "price menu: buying it, which the player then says (missing)")
    val TOO_MUCH = line("too_much", "price menu: too much, which the player then says (missing)")
    val TOO_MUCH_REPLY = line("too_much_reply", "too much: his answer (missing)")
    val NOT_MASTER = line("not_master", "buying, under 99 Woodcutting: his refusal (missing)")
    val NO_MONEY = line("no_money", "buying, under 99,000 coins: the player's admission (missing)")
    val NO_MONEY_REPLY = line("no_money_reply", "buying, under 99,000 coins: his answer (missing)")
    val NO_SPACE_CAPE = line("no_space_cape", "buying, no room for the cape and hood: his line (missing)")
    val SOLD = line("sold", "buying, sold: his line (missing)")
    val HOOD_NO_CAPE = line("hood_no_cape", "another hood, owning no Woodcutting cape: his line (missing)")
    val HOOD_NO_SPACE = line("hood_no_space", "another hood, no room: his line (missing)")
    val HOOD_GIVEN = line("hood_given", "another hood, given: the box with the hood (missing)")
    val LEAVE = line("leave", "first menu: better be off, which the player then says")
    val LEAVE_REPLY = line("leave_reply", "better be off: his farewell (end of dialogue)")
}

/**
 * Wilfred (NPC 16808), the Woodsman tutor renamed in 242.1, north of the Sheared Ram.
 *
 * From the Wiki: the menu, the chopping and firemaking advice, the tools (an axe, a tinderbox, both, or only the one
 * the player lacks), and the cape talk's first lines. Guessed: every chathead expression; that the boxes show the old
 * tutor's tree icon (9713) and the Skills panel ones are plain message boxes; that "previous" after the tools is the
 * first menu; that owning counts carried, worn and banked (as the old tutor's did, so a free axe can't be sold for
 * coins from nothing) and any woodcutting axe counts as an axe; that without room for them he gives nothing and says
 * so, in lines of his own (the Wiki gives no no-room line; a gift dropped at the player's feet could be asked for again
 * and again); the cape purchase and hood (missing from the page) follow the old Woodsman tutor's:
 * 99 Woodcutting, 99,000 coins, room for cape and hood, trimmed with another 99.
 */
object Wilfred {
    const val ID = 16808
    const val TALK_TO = 1

    const val TREE_ICON = 9713
    const val BRONZE_AXE = 1351
    const val TINDERBOX = 590

    /** skillcape_woodcutting, its trimmed one and skillcape_woodcutting_hood. */
    const val WOODCUTTING_CAPE = 9807
    const val WOODCUTTING_CAPE_TRIMMED = 9808
    const val WOODCUTTING_HOOD = 9809

    const val COINS = 995
    const val CAPE_PRICE = 99_000
    const val MAX_LEVEL = 99

    /** Axes a woodcutter uses (bronze to dragon, 3rd age, infernal and crystal); guessed as the ones that count. */
    val WOODCUTTING_AXES = listOf(1351, 1349, 1353, 1361, 1355, 1357, 1359, 6739, 20011, 13241, 23673)

    internal suspend fun Dialogue.talk(give: (Player, Int) -> Unit) = with(WilfredLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        while (true) {
            when (choice(CHOPPING, FIRES, TOOLS, CAPE, LEAVE)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, CHOPPING)
                    chatNpc(ChatAnim.HAPPY, CHOP_1)
                    chatNpc(ChatAnim.NEUTRAL, CHOP_2)
                    chatNpc(ChatAnim.NEUTRAL, CHOP_3)
                    objbox(TREE_ICON, TREE_HINT)
                    chatPlayer(ChatAnim.QUIZ, THATS_IT)
                    chatNpc(ChatAnim.HAPPY, CHOP_4)
                    mesbox(WC_GUIDE_HINT)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, FIRES)
                    chatNpc(ChatAnim.HAPPY, FIRES_1)
                    chatNpc(ChatAnim.NEUTRAL, FIRES_2)
                    mesbox(FM_GUIDE_HINT)
                }
                3 -> tools(give)
                4 -> cape(give)
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, LEAVE)
                    chatNpc(ChatAnim.HAPPY, LEAVE_REPLY)
                    return@with
                }
            }
        }
    }

    private suspend fun Dialogue.tools(give: (Player, Int) -> Unit) = with(WilfredLines) {
        chatPlayer(ChatAnim.QUIZ, TOOLS)
        val axe = WOODCUTTING_AXES.any { owns(player, it) }
        val tinderbox = owns(player, TINDERBOX)
        when {
            axe && tinderbox -> chatNpc(ChatAnim.NEUTRAL, HAS_BOTH)
            axe || tinderbox -> {
                chatNpc(ChatAnim.HAPPY, if (axe) HAS_AXE else HAS_TINDERBOX)
                if (choice(YES_PLEASE, NO_THANKS) == 1) {
                    chatPlayer(ChatAnim.HAPPY, YES_PLEASE)
                    if (axe) handOver(give, TINDERBOX) else handOver(give, BRONZE_AXE)
                } else {
                    chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
                    chatNpc(ChatAnim.NEUTRAL, FAIR_ENOUGH)
                }
            }
            else -> {
                chatNpc(ChatAnim.HAPPY, OFFER)
                when (choice(AXE_PLEASE, TINDERBOX_PLEASE, BOTH_PLEASE, NEITHER)) {
                    1 -> {
                        chatPlayer(ChatAnim.HAPPY, AXE_PLEASE)
                        handOver(give, BRONZE_AXE)
                    }
                    2 -> {
                        chatPlayer(ChatAnim.HAPPY, TINDERBOX_PLEASE)
                        handOver(give, TINDERBOX)
                    }
                    3 -> {
                        chatPlayer(ChatAnim.HAPPY, BOTH_PLEASE)
                        if (player.inventory.freeSlots < 2) {
                            chatNpc(ChatAnim.NEUTRAL, NO_ROOM_BOTH)
                            return@with
                        }
                        give(player, BRONZE_AXE)
                        give(player, TINDERBOX)
                        doubleobjbox(BRONZE_AXE, TINDERBOX, text(GIVES_BOTH))
                        chatPlayer(ChatAnim.HAPPY, THANKS)
                        chatNpc(ChatAnim.HAPPY, DONT_MENTION_IT)
                    }
                    else -> {
                        chatPlayer(ChatAnim.NEUTRAL, NEITHER)
                        chatNpc(ChatAnim.NEUTRAL, FAIR_ENOUGH)
                    }
                }
            }
        }
    }

    private suspend fun Dialogue.handOver(give: (Player, Int) -> Unit, obj: Int) = with(WilfredLines) {
        if (player.inventory.freeSlots == 0) {
            chatNpc(ChatAnim.NEUTRAL, NO_ROOM)
            return@with
        }
        give(player, obj)
        objbox(obj, if (obj == BRONZE_AXE) GIVES_AXE else GIVES_TINDERBOX)
        chatPlayer(ChatAnim.HAPPY, THANKS)
        chatNpc(ChatAnim.HAPPY, DONT_MENTION_IT)
    }

    private suspend fun Dialogue.cape(give: (Player, Int) -> Unit) = with(WilfredLines) {
        chatPlayer(ChatAnim.QUIZ, CAPE)
        chatNpc(ChatAnim.NEUTRAL, CAPE_WHAT)
        if (choice(BUY_CAPE, HOOD) == 2) {
            chatPlayer(ChatAnim.QUIZ, HOOD)
            when {
                !owns(player, WOODCUTTING_CAPE) && !owns(player, WOODCUTTING_CAPE_TRIMMED) -> chatNpc(ChatAnim.NEUTRAL, HOOD_NO_CAPE)
                player.inventory.freeSlots < 1 -> chatNpc(ChatAnim.NEUTRAL, HOOD_NO_SPACE)
                else -> {
                    give(player, WOODCUTTING_HOOD)
                    objbox(WOODCUTTING_HOOD, HOOD_GIVEN)
                }
            }
            return@with
        }
        chatPlayer(ChatAnim.QUIZ, BUY_CAPE)
        chatNpc(ChatAnim.NEUTRAL, PRICE)
        if (choice(ACCEPT, TOO_MUCH) == 2) {
            chatPlayer(ChatAnim.SHOCKED, TOO_MUCH)
            chatNpc(ChatAnim.NEUTRAL, TOO_MUCH_REPLY)
            return@with
        }
        chatPlayer(ChatAnim.HAPPY, ACCEPT)
        val coins = player.inventory.count(COINS)
        when {
            player.skills.baseLevel(Skill.WOODCUTTING) < MAX_LEVEL -> chatNpc(ChatAnim.NEUTRAL, NOT_MASTER)
            coins < CAPE_PRICE -> {
                chatPlayer(ChatAnim.SAD, NO_MONEY)
                chatNpc(ChatAnim.NEUTRAL, NO_MONEY_REPLY)
            }
            player.inventory.freeSlots + (if (coins == CAPE_PRICE.toLong()) 1 else 0) < 2 -> chatNpc(ChatAnim.NEUTRAL, NO_SPACE_CAPE)
            else -> {
                var left = CAPE_PRICE
                for ((slot, held) in player.inventory.contents()) {
                    if (left == 0) break
                    if (held.id == COINS) left -= player.inventory.remove(slot, left)
                }
                val trimmed = Skill.entries.any { it != Skill.WOODCUTTING && player.skills.baseLevel(it) >= MAX_LEVEL }
                give(player, if (trimmed) WOODCUTTING_CAPE_TRIMMED else WOODCUTTING_CAPE)
                give(player, WOODCUTTING_HOOD)
                chatNpc(ChatAnim.HAPPY, SOLD)
            }
        }
    }

    /** Carried, worn or banked. */
    fun owns(player: Player, obj: Int): Boolean =
        player.inventory.count(obj) > 0 || player.equipment.count(obj) > 0 || player.bank.count(obj) > 0
}

/** Wilfred's Talk-to. */
fun registerWilfred(world: World) {
    val give: (Player, Int) -> Unit = { player, obj ->
        world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } ?: logger.warn { "No item $obj in the cache" }
    }
    world.handlers.onNpc(Wilfred.ID, Wilfred.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(Wilfred) { talk(give) } }
    }
}
