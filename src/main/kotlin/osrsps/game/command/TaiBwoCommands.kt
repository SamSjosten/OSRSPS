package osrsps.game.command

import osrsps.game.content.TaiBwoEvents
import osrsps.game.content.TaiBwoEvents.Event

/**
 * `::tbwevent <event>` makes the next event roll of the player's Tai Bwo Wannai hack that event, whatever the counter:
 * the tuber, a gem rock or one of the creatures (Karamja step K4.2b), so a test needn't wait on ~64 ticks of hacking.
 */
fun CommandRegistry.registerTaiBwoCommands() {
    val events: Map<String, Event> = mapOf("tuber" to Event.Tuber, "gems" to Event.GemRock) +
        TaiBwoEvents.Creature.entries.associate { it.name.lowercase() to Event.Monster(it) }
    val names = events.keys.joinToString("|")
    register("tbwevent", "::tbwevent <$names>") {
        val event = args.singleOrNull()?.lowercase()?.let(events::get) ?: fail("Usage: ::tbwevent <$names>")
        TaiBwoEvents.force(player, event)
        reply("Your next Tai Bwo Wannai jungle event: ${args.single().lowercase()}")
    }
}
