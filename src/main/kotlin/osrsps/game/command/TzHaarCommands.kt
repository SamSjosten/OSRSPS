package osrsps.game.command

import osrsps.game.World
import osrsps.game.tzhaar.FightCaveWaves
import osrsps.game.tzhaar.FightCaves
import osrsps.game.tzhaar.Inferno
import osrsps.game.tzhaar.InfernoWaves

/**
 * `::fightcave`: a TzHaar Fight Cave of your own at any wave of any rotation (Fight Caves step FC1), and what your run is
 * at. `::fightcave 63 5` puts you in at TzTok-Jad in rotation 5; leave the rotation out for one from the world's random
 * (`::seed` replays it). `::fightcave next` takes the wave's monsters out, so the next one comes as if you had killed
 * them; `::fightcave leave` ends the run as the exit does, Tokkul and all.
 */
fun CommandRegistry.registerTzHaarCommands(world: World) {
    val caves = world.fightCaves
    val usage = "Usage: ::fightcave [wave [rotation]] | ::fightcave status | ::fightcave next | ::fightcave leave"

    register("fightcave", "::fightcave [wave [rotation]] | status | next | leave", admin = true) {
        when (args.firstOrNull()?.lowercase()) {
            "status" -> {
                val run = caves.runOf(player) ?: fail("You're not in a fight cave.")
                val alive = run.monsters.filter { !it.dead }
                val coming = if (run.nextWaveTick >= 0) ", in ${run.nextWaveTick - world.tick} ticks" else ""
                reply("Wave ${run.wave}$coming, rotation ${run.rotation}: ${alive.size} alive (${alive.joinToString { "${it.type.name} #${it.index}" }})")
            }
            "next" -> {
                val run = caves.runOf(player) ?: fail("You're not in a fight cave.")
                if (run.nextWaveTick >= 0) fail("Wave ${run.wave} hasn't come in yet.")
                for (npc in run.monsters.toList()) if (world.npcs[npc.index] === npc) world.removeNpc(npc)
                run.monsters.clear()
                reply("Took wave ${run.wave}'s monsters out.")
            }
            "leave" -> {
                caves.runOf(player) ?: fail("You're not in a fight cave.")
                caves.leave(player)
                reply("Left the fight cave.")
            }
            else -> {
                if (args.size > 2) fail(usage)
                val wave = args.getOrNull(0)?.let { it.toIntOrNull() ?: fail(usage) } ?: 1
                if (wave !in 1..FightCaveWaves.WAVES) fail("Waves are 1 to ${FightCaveWaves.WAVES}.")
                val rotation = args.getOrNull(1)?.let { it.toIntOrNull() ?: fail(usage) } ?: caves.randomRotation()
                if (rotation !in 1..FightCaveWaves.ROTATIONS) fail("Rotations are 1 to ${FightCaveWaves.ROTATIONS}.")
                world.scripts.interrupt(player)
                if (!caves.start(player, wave, rotation)) fail("No room for another fight cave.")
                reply("Fight cave: wave $wave of rotation $rotation, in ${FightCaves.FIRST_WAVE_TICKS} ticks.")
            }
        }
    }

    val inferno = world.inferno
    val infernoUsage = "Usage: ::inferno [wave] | ::inferno status | ::inferno next | ::inferno sacrifice | ::inferno leave"

    // `::inferno`: an Inferno of your own at any wave (Inferno step IN1). `::inferno 67` puts you in at JalTok-Jad, with
    // the supports your varbits say are standing (all three from wave 1); `::inferno sacrifice` counts your fire cape as
    // given to TzHaar-Ket-Keh, so the entrance shows Jump-in.
    register("inferno", "::inferno [wave] | status | next | sacrifice | leave", admin = true) {
        when (args.firstOrNull()?.lowercase()) {
            "status" -> {
                val run = inferno.runOf(player) ?: fail("You're not in the Inferno.")
                val alive = run.monsters.filter { !it.dead }
                val coming = if (run.nextWaveTick >= 0) ", in ${run.nextWaveTick - world.tick} ticks" else ""
                val supports = run.pillars.joinToString { "${it.index + 1}: ${it.npc.hitpoints}" }.ifEmpty { "none" }
                reply("Wave ${run.wave}$coming: ${alive.size} alive (${alive.joinToString { "${it.type.name} #${it.index}" }}); supports $supports")
            }
            "next" -> {
                val run = inferno.runOf(player) ?: fail("You're not in the Inferno.")
                if (run.nextWaveTick >= 0) fail("Wave ${run.wave} hasn't come in yet.")
                for (npc in run.monsters.toList()) if (world.npcs[npc.index] === npc) world.removeNpc(npc)
                run.monsters.clear()
                reply("Took wave ${run.wave}'s monsters out.")
            }
            "sacrifice" -> {
                inferno.sacrifice(player)
                reply(if (inferno.sacrificed(player)) "Your fire cape counts as given: the Inferno is open to you." else "This cache has no inferno_sacrificed_firecape varbit.")
            }
            "leave" -> {
                inferno.runOf(player) ?: fail("You're not in the Inferno.")
                inferno.leave(player)
                reply("Left the Inferno.")
            }
            else -> {
                if (args.size > 1) fail(infernoUsage)
                val wave = args.getOrNull(0)?.let { it.toIntOrNull() ?: fail(infernoUsage) } ?: 1
                if (wave !in 1..InfernoWaves.WAVES) fail("Waves are 1 to ${InfernoWaves.WAVES}.")
                world.scripts.interrupt(player)
                if (!inferno.start(player, wave)) fail("No room for another Inferno.")
                reply("Inferno: wave $wave, in ${Inferno.FIRST_WAVE_TICKS} ticks.")
            }
        }
    }
}
