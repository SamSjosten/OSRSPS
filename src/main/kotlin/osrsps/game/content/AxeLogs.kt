package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.Lines

/** What taking the axe off the logs says (keys only). */
object AxeLogsLines : Lines("axe_logs", "Bronze axe") {
    val NO_ROOM = line("no_room", "Take-axe on logs with a full inventory; words guessed")
}

/**
 * Logs with a bronze axe stuck in them (`log_withaxe`, beside Draynor's willows by the Friendly Forester, and the same
 * type wherever else the map has it). Take-axe gives the bronze axe and leaves plain logs (`log_withoutaxe`) until the
 * axe is back 30 seconds later: the Wiki's Bronze axe page ("Bronze axes on logs respawn in 30 seconds"; its infobox's
 * 100 ticks is the ground spawn's). Guessed: no message, no animation, and a full inventory keeps the axe on the logs.
 */
object AxeLogs {
    const val WITH_AXE = 5581
    const val WITHOUT_AXE = 5582
    const val BRONZE_AXE = 1351
    const val TAKE_AXE = 1

    /** 30 seconds, in ticks. */
    const val RESPAWN_TICKS = 50
}

fun registerAxeLogs(world: World) {
    world.handlers.onLoc(AxeLogs.WITH_AXE, AxeLogs.TAKE_AXE) { op ->
        val loc = op.loc!!
        if (player.inventory.freeSlots == 0) return@onLoc world.message(player, AxeLogsLines.NO_ROOM)
        // Someone else may have taken it on the same tick.
        if (!world.locs.exists(loc)) return@onLoc
        val axe = world.objTypes[AxeLogs.BRONZE_AXE] ?: return@onLoc
        world.locs.replace(loc, AxeLogs.WITHOUT_AXE, revertAfter = AxeLogs.RESPAWN_TICKS)
        player.inventory.add(axe, 1)
    }
}
