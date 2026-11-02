package osrsps.game.command

import kotlin.math.abs
import kotlin.math.max
import osrsps.game.World
import osrsps.game.encounter.Encounter
import osrsps.game.npc.NpcSpawn
import osrsps.game.raid.ArchiveRaids
import osrsps.game.raid.ArchiveRoom
import osrsps.game.raid.PartyScaling

/**
 * `::raid`: starts a boss fight where you stand, at a phase, for a party size and on a seed, so a fight can be played
 * from any point and the same way twice (raid step 12); and shows, logs and stops the fight you are in. The fight's
 * room and gear sets come with each boss's step. In an instance the fight covers the instance; elsewhere, the
 * [FIGHT_RADIUS] tiles round the boss's spawn. In an Archive run (raid step 15), `::raid run` shows it, `::raid tp`
 * goes to a room of it or the nexus and `::raid clear` marks the room you're in done, for testing.
 */
fun CommandRegistry.registerRaidCommands(world: World) {
    val encounters = world.encounters
    val usage = "Usage: ::raid [boss [phase=name|n] [party=n] [seed=n]] | ::raid log [lines] | ::raid digest [ticks] | ::raid stop | " +
        "::raid run | ::raid tp <nexus|room> | ::raid clear"

    register("raid", "::raid [boss [phase=] [party=] [seed=]] | log | digest | stop | run | tp | clear", admin = true) {
        fun here(): Encounter? = encounters.all.firstOrNull { !it.ended && it.boss.level == player.level && it.covers(player.x, player.z) }
        fun mine(): Encounter = here() ?: fail("You're not in a fight.")
        when (args.firstOrNull()?.lowercase()) {
            null -> {
                val fight = here()
                if (fight == null) {
                    val names = encounters.defs.values.map { it.name }.sorted()
                    reply(if (names.isEmpty()) "You're not in a fight. No bosses are in yet." else "You're not in a fight. Bosses: ${names.joinToString()}")
                    return@register
                }
                reply(describe(fight))
            }
            "log" -> {
                if (args.size > 2) fail(usage)
                val count = args.getOrNull(1)?.let { it.toIntOrNull()?.takeIf { n -> n in 1..Encounter.LOG_LINES } ?: fail("The lines must be 1 to ${Encounter.LOG_LINES}") } ?: DEFAULT_LOG_LINES
                val fight = mine()
                for (line in fight.lines.toList().takeLast(count)) reply(line)
            }
            "digest" -> {
                if (args.size > 2) fail(usage)
                val fight = mine()
                val upTo = args.getOrNull(1)?.let { it.toLongOrNull()?.takeIf { n -> n >= 0 } ?: fail("The ticks must be 0 or more") } ?: fight.processedTicks
                // This tick's part of the fight runs after commands, so it isn't in the log yet.
                if (upTo > fight.processedTicks) fail("The fight has run to tick ${fight.processedTicks} so far")
                // Its log keeps the last lines only; a digest missing the first would differ from another run's.
                if (fight.droppedLines > 0) fail("The log has let go of its first ${fight.droppedLines} lines, so it can't be summed up from the start")
                val lines = fight.lines.filter { it.substringBefore(' ').toLong() <= upTo }
                reply("${fight.def.name} to tick $upTo: ${lines.size} lines, digest ${"%08x".format(lines.joinToString("\n").hashCode())}")
            }
            "run" -> {
                if (args.size != 1) fail(usage)
                val run = world.archiveRaids.at(player.x, player.z) ?: world.parties.of(player)?.let(world.archiveRaids::of)
                    ?: fail("You're not in an Archive run.")
                reply(world.archiveRaids.describe(run))
            }
            "tp" -> {
                // Testing only: to a room of the run you're in, or the nexus, as its door or passage would take you.
                if (args.size != 2) fail(usage)
                val raids = world.archiveRaids
                val run = raids.at(player.x, player.z) ?: fail("You're not in an Archive run.")
                val name = args[1].lowercase()
                val (tile, room) = if (name == "nexus") {
                    raids.tile(run, ArchiveRaids.NEXUS_X, ArchiveRaids.NEXUS_Z) to null
                } else {
                    val room = ArchiveRoom.named(name) ?: fail("Rooms: nexus, ${ArchiveRoom.entries.joinToString { it.name.lowercase() }}")
                    raids.tile(run, room.entryX, room.entryZ) to room
                }
                world.teleport(player, tile.x, tile.z, 0)
                reply("To ${room?.boss?.let { "$it's room" } ?: "the nexus"} at ${tile.x}, ${tile.z}.")
            }
            "clear" -> {
                // Testing only: the room you're in counts as done, as if its boss died.
                if (args.size != 1) fail(usage)
                val raids = world.archiveRaids
                val run = raids.at(player.x, player.z) ?: fail("You're not in an Archive run.")
                val room = raids.roomAt(run, player.x, player.z) ?: fail("You're in the nexus.")
                if (room in run.cleared) fail("${room.boss}'s room is done already.")
                if (room.stage != run.stage) fail("${room.boss}'s room isn't the run's next.")
                raids.clear(run, room)
                reply("Cleared ${room.boss}'s room.")
            }
            "stop" -> {
                if (args.size != 1) fail(usage)
                val fight = mine()
                encounters.stop(fight)
                world.removeNpc(fight.boss)
                reply("Stopped ${fight.def.name}.")
            }
            else -> {
                val def = encounters.defs[args[0].lowercase()] ?: fail("No boss '${args[0]}'. ::raid lists them")
                var phase = 0
                var party: Int? = null
                var seed: Long? = null
                for (arg in args.drop(1)) {
                    val key = arg.substringBefore('=', "").lowercase()
                    val value = arg.substringAfter('=')
                    when (key) {
                        "phase" -> phase = value.toIntOrNull()?.let { it - 1 }?.takeIf { it in def.phases.indices } ?: def.phase(value)
                            ?: fail("${def.name}'s phases: ${def.phases.mapIndexed { i, p -> "${i + 1} ${p.name}" }.joinToString()}")
                        "party" -> party = value.toIntOrNull()?.takeIf { it in 1..PartyScaling.MAX_PARTY } ?: fail("The party size must be 1 to ${PartyScaling.MAX_PARTY}")
                        "seed" -> seed = value.toLongOrNull() ?: fail("The seed must be a whole number")
                        else -> fail(usage)
                    }
                }
                here()?.let { fail("You're in ${it.def.name} already: ::raid stop first") }
                val instance = world.instances.at(player.x, player.z)
                val spawnX = player.x
                val spawnZ = player.z
                val area: (Int, Int) -> Boolean = instance?.let { it::contains }
                    ?: { x, z -> max(abs(x - spawnX), abs(z - spawnZ)) <= FIGHT_RADIUS }
                val npc = world.spawnNpc(NpcSpawn(def.boss, spawnX, spawnZ, player.level, source = spawnSource(player.username), respawns = false))
                    ?: fail("No free NPC slot")
                val size = party ?: world.parties.of(player)?.size ?: 1
                val hitpoints = world.partyScaling.scale(npc, size)
                // Before the fight's first step, so every roll in it comes from the seed.
                seed?.let(world.random::reseed)
                val fight = encounters.start(def, npc, area, phase)
                val scaled = hitpoints?.let { ", $it hp for a party of $size" }.orEmpty()
                val seeded = seed?.let { ", seed $it" }.orEmpty()
                reply("Started ${def.name} #${fight.id} on ${npc.type.name} #${npc.index} at ${npc.x}, ${npc.z}, phase ${phase + 1} (${def.phases[phase].name})$scaled$seeded.")
            }
        }
    }
}

private fun describe(fight: Encounter): String {
    val boss = fight.boss
    val phase = fight.def.phases[fight.phase]
    return "${fight.def.name} #${fight.id}: phase ${fight.phase + 1} (${phase.name}) for ${fight.phaseTick} of ${fight.ticks} ticks; " +
        "${boss.type.name} ${boss.hitpoints}/${boss.maxHitpoints} hp; ${fight.players.size} players, ${fight.adds.size} adds, ${fight.activeHazards.size} hazards"
}

/** How far from the boss's spawn a fight outside an instance reaches. Testing only; the raid's rooms are instances. */
private const val FIGHT_RADIUS = 32

private const val DEFAULT_LOG_LINES = 10
