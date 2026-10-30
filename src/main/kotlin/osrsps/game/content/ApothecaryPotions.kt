package osrsps.game.content

import net.rsprot.protocol.game.outgoing.interfaces.IfSetObject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetText
import osrsps.game.FrameSlot
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.script.ScriptContext
import osrsps.game.shop.ShopScreen

/**
 * The Apothecary's potions screen (`apothecary_potions`, interface 556), opened by his Potions and by asking him in his
 * talk. From the Wiki's potion pages: a strength potion(4) for a limpwurt root, red spiders' eggs and 5 coins; an
 * antipoison(4) for a limpwurt root, cadava berries and 5 coins; an energy potion(4) for two limpwurt roots and
 * chocolate dust.
 *
 * The screen, from the 241 cache: three buttons (components 2, 7 and 12) whose ops are Buy-1, Buy-5, Buy-X and
 * Buy-All, each with a model (4, 9, 14), a title (5, 10, 15) and what it takes (6, 11, 16), which the server fills.
 * Buying makes as many of the potion as asked, held and paid for, at once. Guessed: the energy potion's 5 coins (the
 * Wiki doesn't give a price), the model zoom, that the screen stays open after a purchase, and every message.
 */
object ApothecaryPotions {
    const val INTERFACE = 556
    const val POTIONS_OP = 3
    const val BUY_1 = 1
    const val BUY_5 = 2
    const val BUY_X = 3
    const val BUY_ALL = 4
    private const val ZOOM = 100

    const val LIMPWURT_ROOT = 225
    const val RED_SPIDERS_EGGS = 223
    const val CHOCOLATE_DUST = 1975
    const val CADAVA_BERRIES = 753

    /** One potion: its button component, what it takes (item to count), its coins and its words. */
    data class Potion(
        val button: Int,
        val potion: Int,
        val name: String,
        val takes: Map<Int, Int>,
        val coins: Int,
        val title: Line,
        val contents: Line,
    ) {
        val model get() = button + 2
        val titleText get() = button + 3
        val contentsText get() = button + 4
    }

    val POTIONS = listOf(
        Potion(2, 113, "strength potions", mapOf(LIMPWURT_ROOT to 1, RED_SPIDERS_EGGS to 1), 5, ApothecaryPotionsLines.STRENGTH, ApothecaryPotionsLines.STRENGTH_CONTENTS),
        Potion(7, 3008, "energy potions", mapOf(LIMPWURT_ROOT to 2, CHOCOLATE_DUST to 1), 5, ApothecaryPotionsLines.ENERGY, ApothecaryPotionsLines.ENERGY_CONTENTS),
        Potion(12, 2446, "antipoisons", mapOf(LIMPWURT_ROOT to 1, CADAVA_BERRIES to 1), 5, ApothecaryPotionsLines.ANTIPOISON, ApothecaryPotionsLines.ANTIPOISON_CONTENTS),
    )

    fun open(world: World, player: Player) {
        val text = world.textFor(player)
        for (potion in POTIONS) {
            player.session.queue(IfSetObject(INTERFACE, potion.model, potion.potion, ZOOM))
            player.session.queue(IfSetText(INTERFACE, potion.titleText, text[potion.title]))
            player.session.queue(IfSetText(INTERFACE, potion.contentsText, text[potion.contents]))
        }
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, INTERFACE)
    }

    /** How many of [potion] [player] has the ingredients and coins for. */
    fun affordable(player: Player, potion: Potion): Int {
        val byIngredients = potion.takes.minOf { (obj, count) -> player.inventory.count(obj) / count }
        val byCoins = player.inventory.count(ShopScreen.COINS) / potion.coins
        return minOf(byIngredients, byCoins).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** Makes up to [wanted] of [potion] for [player]; returns how many it made. */
    fun make(world: World, player: Player, potion: Potion, wanted: Int): Int =
        make(player, potion, wanted, world.textFor(player), world.objTypes, player::message)

    internal fun make(player: Player, potion: Potion, wanted: Int, text: DialogueText, objTypes: ObjTypes, message: (String) -> Unit): Int {
        if (potion.takes.any { (obj, count) -> player.inventory.count(obj) < count }) {
            message(text[ApothecaryPotionsLines.NO_INGREDIENTS, "potion" to potion.name])
            return 0
        }
        if (player.inventory.count(ShopScreen.COINS) < potion.coins) {
            message(text[ApothecaryPotionsLines.NO_COINS, "potion" to potion.name])
            return 0
        }
        val count = minOf(wanted, affordable(player, potion))
        if (count < 1) return 0
        AlKharidTraders.pay(player, count * potion.coins)
        for ((obj, each) in potion.takes) {
            var left = count * each
            for ((slot, held) in player.inventory.contents()) {
                if (left == 0) break
                if (held.id == obj) left -= player.inventory.remove(slot, left)
            }
        }
        objTypes[potion.potion]?.let { player.inventory.add(it, count) }
        message(
            if (count == 1) text[ApothecaryPotionsLines.MADE_ONE, "potion" to potion.name.removeSuffix("s")]
            else text[ApothecaryPotionsLines.MADE_SOME, "count" to count, "potion" to potion.name],
        )
        return count
    }

    private suspend fun ScriptContext.buy(world: World, potion: Potion, op: Int) {
        if (player.modals[FrameSlot.MAINMODAL] != INTERFACE) return
        val wanted = when (op) {
            BUY_1 -> 1
            BUY_5 -> 5
            BUY_X -> countDialog()
            else -> Int.MAX_VALUE
        }
        if (wanted < 1 || player.modals[FrameSlot.MAINMODAL] != INTERFACE) return
        make(world, player, potion, wanted)
    }

    fun register(world: World) {
        world.handlers.onNpc(VarrockTraders.APOTHECARY, POTIONS_OP) { open(world, player) }
        for (potion in POTIONS) {
            for (op in BUY_1..BUY_ALL) world.handlers.onButton(INTERFACE, potion.button, op) { buy(world, potion, op) }
        }
    }
}
