package osrsps.game.command

import osrsps.game.FrameSlot
import osrsps.game.World

/** Testing commands: where am I, put me there, what tick is it, read the dialogue file again and show its keys. */
fun CommandRegistry.registerDevCommands(world: World) {
    register("pos", "::pos") {
        val p = player
        // Same order and separators as RuneLite's world location, so the coordinates paste straight into ::tele.
        reply("${p.x}, ${p.z}, ${p.level} (region ${((p.x shr 6) shl 8) or (p.z shr 6)}, local ${p.x and 63}, ${p.z and 63})")
    }

    register("tele", "::tele x z [level]") {
        // Commas are accepted too, so "::tele 3222, 3218, 0" works.
        val numbers = args.joinToString(" ").split(COORD_SEPARATORS).filter { it.isNotEmpty() }
        if (numbers.size !in 2..3) fail("Usage: ::tele x z [level]")
        val values = numbers.map { it.toIntOrNull() ?: fail("Usage: ::tele x z [level] ('$it' is not a number)") }
        val x = values[0]
        val z = values[1]
        val level = values.getOrElse(2) { player.level }
        if (x !in 0 until MAX_COORD || z !in 0 until MAX_COORD) fail("x and z must be 0 to ${MAX_COORD - 1}")
        if (level !in 0..3) fail("Level must be 0 to 3")
        if (!world.hasMap(x, z)) fail("No map at $x, $z: the cache has no terrain there")
        // As a click does: whatever the player had open, a dialogue included, closes and its script is dropped.
        world.scripts.interrupt(player)
        world.teleport(player, x, z, level)
        reply("Teleported to $x, $z, $level")
    }

    // For filling the dialogue file: save it, then this, and the next line said has the new words.
    register("dialogue", "::dialogue") {
        val read = world.loadDialogue()
        read.take(MAX_DIALOGUE_REPLIES).forEach { reply(it) }
        if (read.size > MAX_DIALOGUE_REPLIES) reply("... and ${read.size - MAX_DIALOGUE_REPLIES} more in the server log")
    }

    // For test scripts: with keys on, every dialogue and examine line shows its key whatever the dialogue file holds.
    register("keys", "::keys [on|off]") {
        player.showDialogueKeys = when (args.singleOrNull()?.lowercase()) {
            null -> !player.showDialogueKeys
            "on" -> true
            "off" -> false
            else -> fail("Usage: ::keys [on|off]")
        }
        reply(if (player.showDialogueKeys) "Dialogue shows keys" else "Dialogue shows words")
    }

    register("tick", "::tick") {
        reply("Tick ${world.tick}" + if (world.clock.paused) " (paused)" else "")
    }

    // Opens any interface bare, with none of the setup its content would send. Good enough to see it open,
    // move with a layout change and close.
    register("if", "::if id [side]") {
        if (args.size !in 1..2) fail("Usage: ::if id [side]")
        val id = args[0].toIntOrNull() ?: fail("Usage: ::if id [side] ('${args[0]}' is not a number)")
        val slot = when (args.getOrNull(1)?.lowercase()) {
            null, "main" -> FrameSlot.MAINMODAL
            "side" -> FrameSlot.SIDEMODAL
            else -> fail("Usage: ::if id [side]")
        }
        if (!world.interfaceExists(id)) fail("The cache has no interface $id")
        world.openModal(player, slot, id)
        reply("Opened interface $id. Walk or use its close button to close it.")
    }
}

private val COORD_SEPARATORS = Regex("[\\s,]+")

/** How many lines `::dialogue` puts in the chatbox: the summary and the first problems. */
private const val MAX_DIALOGUE_REPLIES = 6

/** Coordinates are 14 bits in the client's coord grid. */
private const val MAX_COORD = 1 shl 14
