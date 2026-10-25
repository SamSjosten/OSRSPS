package osrsps.game.command

import osrsps.game.PlayerEffects
import osrsps.game.World

/** Testing commands for animations, spotanims, sound effects and jingles on your own player. */
fun CommandRegistry.registerEffectCommands(world: World) {
    val ids = world.effectIds

    register("anim", "::anim id [delay]") {
        if (args.size !in 1..2) fail("Usage: ::anim id [delay]")
        val values = numbers("::anim id [delay]")
        val id = values[0]
        val delay = values.getOrElse(1) { 0 }
        if (id != -1 && !ids.seqExists(id)) fail("No animation $id in the cache")
        if (delay !in 0..PlayerEffects.MAX_ANIM_DELAY) fail("Delay must be 0 to ${PlayerEffects.MAX_ANIM_DELAY} client cycles")
        player.anim(id, delay)
        reply(if (id == -1) "Animation stopped" else "Animation $id")
    }

    register("spotanim", "::spotanim id [height] [delay] [slot]") {
        if (args.size !in 1..4) fail("Usage: ::spotanim id [height] [delay] [slot]")
        val values = numbers("::spotanim id [height] [delay] [slot]")
        val id = values[0]
        val height = values.getOrElse(1) { 0 }
        val delay = values.getOrElse(2) { 0 }
        val slot = values.getOrElse(3) { 0 }
        if (id != -1 && !ids.spotanimExists(id)) fail("No spotanim $id in the cache")
        if (height !in 0..MAX_SHORT) fail("Height must be 0 to $MAX_SHORT")
        checkDelay(delay)
        if (slot !in 0 until PlayerEffects.SPOTANIM_SLOTS) fail("Slot must be 0 to ${PlayerEffects.SPOTANIM_SLOTS - 1}")
        player.spotanim(id, height, delay, slot)
        reply(if (id == -1) "Spotanim slot $slot cleared" else "Spotanim $id in slot $slot")
    }

    register("sound", "::sound [id] [loops] [delay]") {
        if (args.size > 3) fail("Usage: ::sound [id] [loops] [delay]")
        val values = numbers("::sound [id] [loops] [delay]")
        val id = values.getOrElse(0) { DEFAULT_SOUND }
        val loops = values.getOrElse(1) { 1 }
        val delay = values.getOrElse(2) { 0 }
        if (!ids.synthExists(id)) fail("No sound $id in the cache")
        if (loops !in 1..MAX_LOOPS) fail("Loops must be 1 to $MAX_LOOPS")
        checkDelay(delay)
        player.sound(id, loops, delay)
        reply("Sound $id")
    }

    register("areasound", "::areasound id [radius] [loops]", admin = true) {
        if (args.size !in 1..3) fail("Usage: ::areasound id [radius] [loops]")
        val values = numbers("::areasound id [radius] [loops]")
        val id = values[0]
        val radius = values.getOrElse(1) { DEFAULT_AREA_RADIUS }
        val loops = values.getOrElse(2) { 1 }
        if (!ids.synthExists(id)) fail("No sound $id in the cache")
        if (radius !in 0..MAX_AREA_RADIUS) fail("Radius must be 0 to $MAX_AREA_RADIUS tiles")
        if (loops !in 1..MAX_LOOPS) fail("Loops must be 1 to $MAX_LOOPS")
        world.zones.soundArea(player.x, player.z, player.level, id, radius, loops)
        reply("Sound $id from ${player.x}, ${player.z}, heard $radius tiles away")
    }

    register("jingle", "::jingle id") {
        if (args.size != 1) fail("Usage: ::jingle id")
        val id = numbers("::jingle id")[0]
        if (!ids.jingleExists(id)) fail("No jingle $id in the cache")
        player.jingle(id)
        reply("Jingle $id")
    }
}

private fun CommandContext.numbers(usage: String): List<Int> =
    args.map { it.toIntOrNull() ?: fail("Usage: $usage ('$it' is not a number)") }

private fun CommandContext.checkDelay(delay: Int) {
    if (delay !in 0..MAX_SHORT) fail("Delay must be 0 to $MAX_SHORT client cycles")
}

private const val MAX_SHORT = 65535

/** Loops go to the client in one byte. */
private const val MAX_LOOPS = 255

/** Town crier bell ding (RuneLite's SoundEffectID.TOWN_CRIER_BELL_DING): short and easy to hear. */
private const val DEFAULT_SOUND = 3813

/** Zones.soundArea's own default. */
private const val DEFAULT_AREA_RADIUS = 5

/** RSProt: the client ignores an area sound's radius past 31 tiles. */
private const val MAX_AREA_RADIUS = 31
