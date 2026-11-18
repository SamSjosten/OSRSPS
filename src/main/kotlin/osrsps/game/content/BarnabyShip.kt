package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** Captain Barnaby's words, the Wiki's Transcript:Captain Barnaby (read 2026-11-18): one greeting for each dock. */
object BarnabyLines : Lines("barnaby", "Transcript:Captain Barnaby") {
    val GREETING_ARDOUGNE = line("greeting_ardougne", "his greeting on the East Ardougne dock: Rimmington or Brimhaven, 30 coins")
    val GREETING_BRIMHAVEN = line("greeting_brimhaven", "on the Brimhaven dock: Rimmington or Ardougne")
    val GREETING_RIMMINGTON = line("greeting_rimmington", "on the Rimmington dock: Ardougne or Brimhaven")
    val TO_ARDOUGNE = line("to_ardougne", "an option, which the player then says")
    val TO_BRIMHAVEN = line("to_brimhaven", "an option, which the player then says")
    val TO_RIMMINGTON = line("to_rimmington", "an option, which the player then says")
    val GOOD_THANKS = line("good_thanks", "the last option, which the player then says")
    val NO_COINS = line("no_coins", "his answer without the fare (also the message for his right-click options without it, inferred)")
    val SAIL_ARDOUGNE = line("sail_ardougne", "the box on boarding for Ardougne")
    val SAIL_BRIMHAVEN = line("sail_brimhaven", "the box on boarding for Brimhaven")
    val SAIL_RIMMINGTON = line("sail_rimmington", "the box on boarding for Rimmington")
}

/**
 * Captain Barnaby's ship between East Ardougne, Brimhaven and Rimmington (Karamja step K3.1), as the Wiki's Captain
 * Barnaby page and transcript have it (read 2026-11-18), with ids and tiles from the 242 cache and map:
 *
 * - **Captain Barnaby** stands on each dock (3650, which shows as 9250, at East Ardougne; 8764 at Brimhaven; 8763 at
 *   Rimmington) and takes a player to either of the other two for 30 coins, 15 wearing Karamja gloves, from his talk or
 *   his two right-click options.
 * - **The trip:** the coins go, the box says where the ship sails, and the player lands on the ship's deck at the other
 *   dock, to leave by its gangplank. Each ship's gangplanks cross between deck and dock both ways.
 *
 * East Ardougne and Rimmington are built only as far as their docks (Kandarin and Asgarnia aren't built yet), so no
 * lodestone comes with them; Brimhaven has Karamja's. Inferred: Karamja gloves halving his fare as the Karamja boat's
 * seamen do (the Wiki's transcript leaves that case out); the no-coins line as his right-click options' message.
 * Guessed: the deck tiles, the gangplanks' dock tiles, which of Rimmington's two ships is his (the one by him), the
 * chathead faces, and no crossing screen (the cache has no chart animation for his route). Left out: the Ring of
 * Charos(a)'s free trip, My Arm's Big Adventure, Sailing.
 */
object BarnabyShip {
    const val BARNABY_ARDOUGNE = 3650
    const val BARNABY_ARDOUGNE_SHOWN = 9250
    const val BARNABY_BRIMHAVEN = 8764
    const val BARNABY_RIMMINGTON = 8763
    const val TALK_TO = 1
    const val FIRST_PORT = 3
    const val SECOND_PORT = 4

    /** A dock: where a passenger lands on its ship's deck and the dock below its gangplank, and its gangplanks. */
    enum class Dock(
        val deck: Destination, val dock: Destination, val plankOn: Int, val plankOff: Int, val greeting: Line, val to: Line, val sail: Line,
        /** Where its gangplanks stand on the 242 map; a copy anywhere else does nothing (exploit review R50B-1's class). */
        val plankOnAt: Destination, val plankOffAt: Destination,
    ) {
        ARDOUGNE(Destination(2683, 3263, 1), Destination(2683, 3266, 0), 2085, 2086, BarnabyLines.GREETING_ARDOUGNE, BarnabyLines.TO_ARDOUGNE, BarnabyLines.SAIL_ARDOUGNE,
            Destination(2683, 3265, 0), Destination(2683, 3264, 1)),
        BRIMHAVEN(Destination(2775, 3234, 1), Destination(2772, 3234, 0), 2087, 2088, BarnabyLines.GREETING_BRIMHAVEN, BarnabyLines.TO_BRIMHAVEN, BarnabyLines.SAIL_BRIMHAVEN,
            Destination(2773, 3234, 0), Destination(2774, 3234, 1)),
        RIMMINGTON(Destination(2915, 3222, 1), Destination(2915, 3225, 0), 34669, 34670, BarnabyLines.GREETING_RIMMINGTON, BarnabyLines.TO_RIMMINGTON, BarnabyLines.SAIL_RIMMINGTON,
            Destination(2915, 3224, 0), Destination(2915, 3223, 1)),
    }

    /** Where Barnaby on [dock] goes: the two other docks, in his menu's (and right-click options') order. */
    fun destinations(dock: Dock): List<Dock> = when (dock) {
        Dock.ARDOUGNE -> listOf(Dock.RIMMINGTON, Dock.BRIMHAVEN)
        Dock.BRIMHAVEN -> listOf(Dock.RIMMINGTON, Dock.ARDOUGNE)
        Dock.RIMMINGTON -> listOf(Dock.ARDOUGNE, Dock.BRIMHAVEN)
    }

    /** His right-click options are the cache's: Brimhaven's ship lists Ardougne first, Ardougne's Brimhaven. */
    fun options(dock: Dock): List<Dock> = when (dock) {
        Dock.ARDOUGNE -> listOf(Dock.BRIMHAVEN, Dock.RIMMINGTON)
        Dock.BRIMHAVEN -> listOf(Dock.ARDOUGNE, Dock.RIMMINGTON)
        Dock.RIMMINGTON -> listOf(Dock.ARDOUGNE, Dock.BRIMHAVEN)
    }

    /** The NPC types Barnaby is on each dock, as the player sees them. */
    val BARNABYS = mapOf(
        Dock.ARDOUGNE to listOf(BARNABY_ARDOUGNE, BARNABY_ARDOUGNE_SHOWN),
        Dock.BRIMHAVEN to listOf(BARNABY_BRIMHAVEN),
        Dock.RIMMINGTON to listOf(BARNABY_RIMMINGTON),
    )

    /** His talk on [dock]: where the player paid to go, or null. */
    internal suspend fun Dialogue.barnaby(dock: Dock): Dock? = with(BarnabyLines) {
        chatNpc(ChatAnim.HAPPY, dock.greeting)
        val (first, second) = destinations(dock)
        val to = when (choice(first.to, second.to, GOOD_THANKS)) {
            1 -> first
            2 -> second
            else -> {
                chatPlayer(ChatAnim.NEUTRAL, GOOD_THANKS)
                return null
            }
        }
        chatPlayer(ChatAnim.NEUTRAL, to.to)
        val fare = KaramjaShips.fare(player)
        if (player.inventory.count(KaramjaShips.COINS) < fare) {
            chatNpc(ChatAnim.NEUTRAL, NO_COINS)
            return null
        }
        Ned.take(player, KaramjaShips.COINS, fare)
        to
    }

    /** The trip, once paid: the box, then the player on [to]'s deck. */
    internal suspend fun ScriptContext.sailTo(world: World, to: Dock) {
        world.dialogues.start(this) { mesbox(to.sail) }
        world.teleport(player, to.deck.x, to.deck.z, to.deck.level)
    }
}

fun registerBarnabyShip(world: World) {
    with(BarnabyShip) {
        for ((dock, types) in BARNABYS) {
            for (type in types) {
                world.handlers.onNpc(type, TALK_TO) { op ->
                    var to: BarnabyShip.Dock? = null
                    world.dialogues.start(this, op.npc) { to = barnaby(dock) }
                    to?.let { sailTo(world, it) }
                }
                for ((index, to) in options(dock).withIndex()) {
                    world.handlers.onNpc(type, FIRST_PORT + index) {
                        val fare = KaramjaShips.fare(player)
                        if (player.inventory.count(KaramjaShips.COINS) < fare) {
                            world.message(player, BarnabyLines.NO_COINS)
                            return@onNpc
                        }
                        Ned.take(player, KaramjaShips.COINS, fare)
                        sailTo(world, to)
                    }
                }
            }
            world.handlers.onLoc(dock.plankOn, KaramjaShips.CROSS) { op ->
                arriveDelay()
                if (!placedAt(world, op.loc, dock.plankOnAt)) return@onLoc
                world.teleport(player, dock.deck.x, dock.deck.z, dock.deck.level)
            }
            world.handlers.onLoc(dock.plankOff, KaramjaShips.CROSS) { op ->
                arriveDelay()
                if (!placedAt(world, op.loc, dock.plankOffAt)) return@onLoc
                world.teleport(player, dock.dock.x, dock.dock.z, dock.dock.level)
            }
        }
    }
}
