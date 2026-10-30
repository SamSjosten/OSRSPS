package osrsps.game.content

import net.rsprot.protocol.game.outgoing.interfaces.IfSetObject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import osrsps.game.FrameSlot
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.shop.ShopScreen

/** Ellis's lines; the words are in the dialogue file, from the Wiki's Transcript:Ellis, read 2026-10-30. */
object EllisLines : Lines("ellis", "Transcript:Ellis") {
    val GREETING = line("greeting", "his first line")
    val ONE_HIDE = line("one_hide", "carrying one hide: his offer to tan it")
    val SOME_HIDES = line("some_hides", "carrying several hides: his offer to tan them")
    val YES_PLEASE = line("yes_please", "that offer's menu: yes, and the tanning screen opens")
    val NO_THANKS = line("no_thanks", "that offer's menu: no, which the player then says")
    val AS_YOU_WISH = line("as_you_wish", "his answer to no")
    val BUY_LEATHER = line("buy_leather", "no hides: option 1, which the player then says")
    val HOW_IT_WORKS = line("how_it_works", "option 1: his answer, how he tans for a coin a hide")
    val WEAK = line("weak", "no hides: option 2, which the player then says")
    val NORMAL = line("normal", "option 2: his first line, about soft leather")
    val ALTERNATIVELY = line("alternatively", "option 2: hard leather")
    val DRAGONHIDES = line("dragonhides", "option 2: dragonhides")
    val BEAR_IN_MIND = line("bear_in_mind", "option 2: the player's thanks")
    val ALI = line("ali", "no hides, option 3 (after Rogue Trader): Ali Morrisane, which the player then says")
    val ALI_1 = line("ali_1", "the Ali Morrisane topic: his first line")
    val ALI_2 = line("ali_2", "the Ali Morrisane topic: second line")
    val ALI_3 = line("ali_3", "the Ali Morrisane topic: third line")
    val THREATS = line("threats", "the Ali Morrisane topic: the player asks what threats")
    val PROTECTED = line("protected", "the Ali Morrisane topic: his answer")
    val ARMOUR = line("armour", "the Ali Morrisane topic: his last line")
    val MAKE_THE_STUFF = line("make_the_stuff", "tanned leather used on him")
    val NO_THANKS_ITEM = line("no_thanks_item", "any other item used on him")
}

/**
 * The tanning screen's words: each slot's name and price (the interface has placeholders, "Hide" and "Cost", for the
 * server to fill), and the messages. The messages are 2004Scape's, which it marks as live's; the names, the price's
 * form and the plural message are guessed until recorded.
 */
object TanningLines : Lines("tanning", "Tanner") {
    val SOFT = line("soft", "slot 1's name: soft leather")
    val HARD = line("hard", "slot 2's name: hard leather")
    val SNAKESKIN = line("snakeskin", "slot 3's name: snakeskin")
    val SWAMP_SNAKESKIN = line("swamp_snakeskin", "slot 4's name: snakeskin from swamp snake hide")
    val GREEN = line("green", "slot 5's name: green dragon leather")
    val BLUE = line("blue", "slot 6's name: blue dragon leather")
    val RED = line("red", "slot 7's name: red dragon leather")
    val BLACK = line("black", "slot 8's name: black dragon leather")
    val PRICE = line("price", "each slot's price; {price} is the coins a hide", "price")
    val NO_HIDES = line("no_hides", "tanning with none of that slot's hide; {hide} is the hide", "hide")
    val NO_COINS = line("no_coins", "tanning without the coins for one; {leather} is the slot's leather", "leather")
    val TANNED_ONE = line("tanned_one", "after tanning one; {hide} is the hide", "hide")
    val TANNED_SOME = line("tanned_some", "after tanning several; {count} is how many, {hide} the hides", "count", "hide")
    val NO_MORE_COINS = line("no_more_coins", "after tanning, with hides left and too few coins for another; {leather} is the slot's leather", "leather")
}

/**
 * Ellis, the tanner north of the furnace (`ellis_tanner`, 3231), and the tanning screen (`tanner`, interface 324).
 *
 * From the Wiki: Ellis's talk (with hides carried he offers to tan them; without, two topics and, after Rogue Trader,
 * Ali Morrisane); Trade, and an untanned hide used on him, open the tanning screen; tanned leather or any other item
 * used on him gets a line. Ellis's eight tannings and their prices per hide: cowhide to soft leather 1, to hard leather
 * 3, snake hide to snakeskin 15, swamp snake hide to snakeskin 20, and each dragonhide to its leather 20.
 *
 * The screen: slots a to h, each with its leather's model (components 100-107), name (108-115), price (116-123) and Tan
 * All, X, 5 and 1 (124-155), from the 241 cache's component names. Tanning takes as many of the slot's hides as asked,
 * held and paid for, at once, and changes each into leather (2004Scape's tan_leather). Guessed: the slots' order (the
 * Wiki's table's), the model zoom, that the screen closes after a tanning, the messages' cases, the expressions.
 */
object Ellis {
    const val ID = 3231
    const val TALK_TO = 1
    const val TRADE = 3
    const val INTERFACE = 324

    private const val MODEL = 100
    private const val NAME = 108
    private const val PRICE = 116
    private const val ALL = 124
    private const val X = 132
    private const val FIVE = 140
    private const val ONE = 148
    private const val ZOOM = 100

    /** One slot: the hide it takes, the leather it makes, the coins a hide and its name's line. */
    data class Tanning(val hide: Int, val hideName: String, val leather: Int, val leatherName: String, val price: Int, val name: osrsps.game.dialogue.Line)

    val TANNINGS = listOf(
        Tanning(1739, "cowhides", 1741, "soft leather", 1, TanningLines.SOFT),
        Tanning(1739, "cowhides", 1743, "hard leather", 3, TanningLines.HARD),
        Tanning(6287, "snake hides", 6289, "snakeskin", 15, TanningLines.SNAKESKIN),
        Tanning(7801, "snake hides", 6289, "snakeskin", 20, TanningLines.SWAMP_SNAKESKIN),
        Tanning(1753, "green dragonhides", 1745, "green dragon leather", 20, TanningLines.GREEN),
        Tanning(1751, "blue dragonhides", 2505, "blue dragon leather", 20, TanningLines.BLUE),
        Tanning(1749, "red dragonhides", 2507, "red dragon leather", 20, TanningLines.RED),
        Tanning(1747, "black dragonhides", 2509, "black dragon leather", 20, TanningLines.BLACK),
    )

    private val HIDES = TANNINGS.map { it.hide }.toSet()
    private val LEATHERS = TANNINGS.map { it.leather }.toSet()

    /** Ellis's talk; true when the player asked him to tan their hides. */
    internal suspend fun Dialogue.talk(): Boolean = with(EllisLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        val hides = HIDES.sumOf { player.inventory.count(it) }
        if (hides > 0) {
            chatNpc(ChatAnim.HAPPY, if (hides == 1L) ONE_HIDE else SOME_HIDES)
            if (choice(YES_PLEASE, NO_THANKS) == 1) return@with true
            chatPlayer(ChatAnim.BORED, NO_THANKS)
            chatNpc(ChatAnim.NEUTRAL, AS_YOU_WISH)
            return@with false
        }
        when (choice(BUY_LEATHER, WEAK, ALI)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, BUY_LEATHER)
                chatNpc(ChatAnim.NEUTRAL, HOW_IT_WORKS)
            }
            2 -> {
                chatPlayer(ChatAnim.NEUTRAL, WEAK)
                chatNpc(ChatAnim.HAPPY, NORMAL)
                chatNpc(ChatAnim.HAPPY, ALTERNATIVELY)
                chatNpc(ChatAnim.HAPPY, DRAGONHIDES)
                chatPlayer(ChatAnim.BORED, BEAR_IN_MIND)
            }
            else -> {
                chatPlayer(ChatAnim.QUIZ, ALI)
                chatNpc(ChatAnim.ANGRY, ALI_1)
                chatNpc(ChatAnim.ANGRY, ALI_2)
                chatNpc(ChatAnim.LAUGH, ALI_3)
                chatPlayer(ChatAnim.QUIZ, THREATS)
                chatNpc(ChatAnim.NEUTRAL, PROTECTED)
                chatNpc(ChatAnim.HAPPY, ARMOUR)
            }
        }
        false
    }

    /** Opens the tanning screen, each slot filled in. */
    fun open(world: World, player: Player) {
        val text = world.textFor(player)
        for ((i, tanning) in TANNINGS.withIndex()) {
            player.session.queue(IfSetObject(INTERFACE, MODEL + i, tanning.leather, ZOOM))
            player.session.queue(IfSetText(INTERFACE, NAME + i, text[tanning.name]))
            player.session.queue(IfSetText(INTERFACE, PRICE + i, text[TanningLines.PRICE, "price" to tanning.price]))
        }
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, INTERFACE)
    }

    /** A Tan button: which slot and how many, or null for another component; [X] asks for the number. */
    fun button(component: Int): Pair<Int, Int>? = when (component) {
        in ALL until ALL + 8 -> component - ALL to Int.MAX_VALUE
        in X until X + 8 -> component - X to -1
        in FIVE until FIVE + 8 -> component - FIVE to 5
        in ONE until ONE + 8 -> component - ONE to 1
        else -> null
    }

    /** Tans up to [wanted] of slot [index]'s hides for [player]; returns how many it tanned. */
    fun tan(world: World, player: Player, index: Int, wanted: Int): Int =
        tan(player, index, wanted, world.textFor(player), world.objTypes, player::message)

    internal fun tan(player: Player, index: Int, wanted: Int, text: DialogueText, objTypes: ObjTypes, message: (String) -> Unit): Int {
        val tanning = TANNINGS[index]
        val held = player.inventory.count(tanning.hide).toInt()
        if (held < 1) {
            message(text[TanningLines.NO_HIDES, "hide" to tanning.hideName])
            return 0
        }
        val coins = player.inventory.count(ShopScreen.COINS).toInt()
        if (coins < tanning.price) {
            message(text[TanningLines.NO_COINS, "leather" to tanning.leatherName])
            return 0
        }
        val count = minOf(wanted, held, coins / tanning.price)
        if (count < 1) return 0
        AlKharidTraders.pay(player, count * tanning.price)
        var left = count
        for ((slot, obj) in player.inventory.contents()) {
            if (left == 0) break
            if (obj.id != tanning.hide) continue
            player.inventory.remove(slot, 1)
            left--
        }
        objTypes[tanning.leather]?.let { player.inventory.add(it, count) }
        message(
            if (count == 1) text[TanningLines.TANNED_ONE, "hide" to tanning.hideName]
            else text[TanningLines.TANNED_SOME, "count" to count, "hide" to tanning.hideName],
        )
        if (held > count && player.inventory.count(ShopScreen.COINS) < tanning.price) {
            message(text[TanningLines.NO_MORE_COINS, "leather" to tanning.leatherName])
        }
        return count
    }

    private suspend fun ScriptContext.tanButton(world: World, component: Int) {
        if (player.modals[FrameSlot.MAINMODAL] != INTERFACE) return
        val (index, amount) = button(component) ?: return
        val wanted = if (amount < 0) countDialog() else amount
        if (wanted < 1 || player.modals[FrameSlot.MAINMODAL] != INTERFACE) return
        world.interfaces.closeModals(player)
        tan(world, player, index, wanted)
    }

    fun register(world: World) {
        world.handlers.onNpc(ID, TALK_TO) { op ->
            var tan = false
            world.dialogues.start(this, op.npc) { tan = talk() }
            if (tan) open(world, player)
        }
        world.handlers.onNpc(ID, TRADE) { open(world, player) }
        world.handlers.onObjOnNpc(ID) { op ->
            when (op.obj) {
                in HIDES -> open(world, player)
                in LEATHERS -> world.dialogues.start(this, op.npc) { chatNpc(ChatAnim.CONFUSED, EllisLines.MAKE_THE_STUFF) }
                else -> world.dialogues.start(this, op.npc) { chatNpc(ChatAnim.CONFUSED, EllisLines.NO_THANKS_ITEM) }
            }
        }
        for (component in (ALL until ONE + 8)) {
            world.handlers.onButton(INTERFACE, component) { tanButton(world, component) }
        }
    }
}
