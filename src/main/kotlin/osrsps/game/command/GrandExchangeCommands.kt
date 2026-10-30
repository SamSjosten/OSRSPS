package osrsps.game.command

import osrsps.game.World
import osrsps.game.ge.GeAccount
import osrsps.game.ge.GeOffer
import osrsps.game.ge.GrandExchange
import osrsps.game.ge.OfferType
import osrsps.game.ge.PlaceResult
import java.util.Locale

/**
 * The Grand Exchange's offers without its screens: place, list, abort and collect with `::geoffer`, and for tests
 * `::gefill` (fill an offer at its own price) and `::gemarket` (stop or start the bot market).
 */
fun CommandRegistry.registerGrandExchangeCommands(world: World) {
    val ge = world.grandExchange
    val usage = "::geoffer buy|sell item quantity price, ::geoffer list, ::geoffer abort|collect slot"

    register("geoffer", usage) {
        when (args.firstOrNull()?.lowercase()) {
            "buy", "sell" -> {
                // The last two words are the quantity and price, so names can have spaces: ::geoffer buy bronze axe 2 50.
                if (args.size < 4) fail("Usage: ::geoffer ${args[0].lowercase()} item quantity price")
                val quantity = parseCount(args[args.size - 2]) ?: fail("'${args[args.size - 2]}' is not a quantity")
                val price = parsePrice(args.last()) ?: fail("'${args.last()}' is not a price")
                val text = args.subList(1, args.size - 2).joinToString(" ")
                val type = findItem(world, text) ?: fail("No item '$text'")
                val buying = args[0].lowercase() == "buy"
                // Offers go in the first free slot.
                val slot = player.ge.freeSlot()
                val result = if (buying) ge.buy(player, type.id, quantity, price) else ge.sell(player, type.id, quantity, price)
                reply(
                    when (result) {
                        PlaceResult.PLACED -> "Placed: ${describe(world, slot to player.ge[slot]!!)}"
                        PlaceResult.NO_FREE_SLOT -> "Every slot is taken."
                        PlaceResult.NOT_TRADEABLE -> "${type.name} can't be traded on the Grand Exchange."
                        PlaceResult.NOT_ON_EXCHANGE -> "${type.name} has no guide price, so it isn't on the Grand Exchange."
                        PlaceResult.BAD_AMOUNT -> "The quantity and price must be at least 1."
                        PlaceResult.TOO_EXPENSIVE -> "That offer is worth more than ${format(GrandExchange.MAX_TOTAL)} coins."
                        PlaceResult.NOT_ENOUGH_COINS -> "Not enough coins and platinum tokens in the inventory."
                        PlaceResult.NOT_ENOUGH_ITEMS -> "Not enough ${type.name} in the inventory."
                        PlaceResult.NO_ROOM_FOR_CHANGE -> "No room for the change."
                    },
                )
            }
            "list" -> {
                val offers = player.ge.contents()
                if (offers.isEmpty()) reply("No offers.")
                for (offer in offers) reply(describe(world, offer))
            }
            "abort", "collect" -> {
                val slot = args.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0 until GeAccount.SLOTS } ?: fail("Usage: ::geoffer ${args[0].lowercase()} slot (0 to 7)")
                if (args[0].lowercase() == "abort") {
                    reply(if (ge.abort(player, slot)) "Aborted slot $slot." else "Slot $slot has nothing to abort.")
                } else {
                    val notes = args.getOrNull(2)?.lowercase() != "items"
                    reply(if (ge.collect(player, slot, notes)) "Collected from slot $slot." else "Nothing collected from slot $slot.")
                }
            }
            else -> fail("Usage: $usage")
        }
    }

    register("gefill", "::gefill slot [amount]") {
        val slot = args.firstOrNull()?.toIntOrNull()?.takeIf { it in 0 until GeAccount.SLOTS } ?: fail("Usage: ::gefill slot [amount]")
        val amount = args.getOrNull(1)?.let { parseCount(it) ?: fail("'$it' is not an amount") } ?: Int.MAX_VALUE
        val filled = ge.fill(player, slot, amount)
        reply(if (filled > 0) "Filled ${format(filled.toLong())} in slot $slot." else "Slot $slot has nothing to fill.")
    }

    register("gemarket", "::gemarket [on|off]") {
        when (args.singleOrNull()?.lowercase()) {
            null -> {}
            "on" -> ge.marketOpen = true
            "off" -> ge.marketOpen = false
            else -> fail("Usage: ::gemarket [on|off]")
        }
        reply("The Grand Exchange market is ${if (ge.marketOpen) "on" else "off"}.")
    }
}

/** "Slot 0: buy 2 x Bronze axe (1351) at 50, 1/2 done for 40, to collect 1 item and 10 coins". */
private fun describe(world: World, slotted: Pair<Int, GeOffer>): String {
    val (slot, offer) = slotted
    val name = world.objTypes[offer.item]?.name ?: "item ${offer.item}"
    val state = when {
        offer.aborted -> ", aborted"
        offer.completed == offer.quantity -> ", complete"
        else -> ""
    }
    return "Slot $slot: ${if (offer.type == OfferType.BUY) "buy" else "sell"} ${format(offer.quantity.toLong())} x $name (${offer.item}) " +
        "at ${format(offer.price)}, ${format(offer.completed.toLong())}/${format(offer.quantity.toLong())} done for ${format(offer.exchanged)}$state, " +
        "to collect ${format(offer.collectItems.toLong())} items and ${format(offer.collectCoins)} coins"
}

/** "500", "10k", "2.5m" or "1b" to a price from 1 up; null for anything else. */
internal fun parsePrice(text: String): Long? {
    val multiplier = when (text.lowercase().lastOrNull()) {
        'k' -> 1_000.0
        'm' -> 1_000_000.0
        'b' -> 1_000_000_000.0
        else -> 1.0
    }
    val digits = if (multiplier == 1.0) text else text.dropLast(1)
    if (multiplier == 1.0) return digits.toLongOrNull()?.takeIf { it >= 1 }
    val value = digits.toBigDecimalOrNull() ?: return null
    return value.multiply(multiplier.toBigDecimal()).takeIf { it.stripTrailingZeros().scale() <= 0 }?.toLong()?.takeIf { it >= 1 }
}

private fun format(n: Long): String = String.format(Locale.ROOT, "%,d", n)
