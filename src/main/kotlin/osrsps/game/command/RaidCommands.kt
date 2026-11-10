package osrsps.game.command

import kotlin.math.abs
import kotlin.math.max
import osrsps.game.Player
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
 * [FIGHT_RADIUS] tiles round the boss's spawn, for its starter and their party only, ending if the starter dies or
 * logs out. In an Archive run (raid step 15), `::raid run` shows it, `::raid tp` goes to a room of it or the nexus and
 * `::raid clear` marks the room you're in done, and `::raid hold` freezes the boss of the fight you're in where it
 * stands (Bork, in his and the Smorks'), as a kite keeps it off, for testing; a room whose fight never began gives its points to whoever
 * of the party stands in it.
 */
fun CommandRegistry.registerRaidCommands(world: World) {
    val encounters = world.encounters
    val usage = "Usage: ::raid [boss [phase=name|n] [party=n] [seed=n]] | ::raid log [lines] | ::raid digest [ticks] | ::raid stop | " +
        "::raid run | ::raid tp <nexus|vault|room|x z> | ::raid travel | ::raid clear | ::raid hold [ticks]"

    register("raid", "::raid [boss [phase=] [party=] [seed=]] | log | digest | stop | run | tp | travel | clear | hold", admin = true) {
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
                val raids = world.archiveRaids
                val where = when {
                    !run.instance.contains(player.x, player.z) -> "outside it"
                    else -> {
                        val room = raids.roomAt(run, player.x, player.z)?.let { if (it == ArchiveRoom.GUARDIAN) "in the nexus's hall" else "in ${it.boss}'s room" }
                            ?: if (raids.inRewardRoom(run, player.x, player.z)) "in the reward vault" else "in the nexus's entrance"
                        // The cache's tile, as ::raid tp x z takes it: scripts can't know where the instance lies.
                        val x = player.x - run.instance.baseX + ArchiveRaids.FROM_X
                        val z = player.z - run.instance.baseZ + ArchiveRaids.FROM_Z
                        "$room, at $x, $z of the raid"
                    }
                }
                reply("${raids.describe(run)}. You're $where.")
            }
            "tp" -> {
                // Testing only: to a room of the run you're in, or the nexus, as its door or passage would take you.
                if (args.size != 2 && args.size != 3) fail(usage)
                val raids = world.archiveRaids
                val run = raids.at(player.x, player.z) ?: fail("You're not in an Archive run.")
                if (args.size == 3) {
                    // A cache tile of the raid, mapped into the run's instance, on the level of the room it is in: for
                    // scripts, which can't know where the instance lies (Ocellus's hooded figure is out of view from
                    // where his room is come into).
                    val x = args[1].toIntOrNull() ?: fail(usage)
                    val z = args[2].toIntOrNull() ?: fail(usage)
                    val tile = raids.tile(run, x, z)
                    if (!run.instance.contains(tile.x, tile.z)) fail("$x, $z isn't in the raid.")
                    val level = raids.levelAt(run, tile.x, tile.z)
                    world.teleport(player, tile.x, tile.z, level)
                    reply("To $x, $z of the raid at ${tile.x}, ${tile.z}, $level.")
                    return@register
                }
                val name = args[1].lowercase()
                if (name == "vault") {
                    // As the Blade's Travel lands a player, the count to its Rift out starting with the first.
                    raids.arrive(run, player)
                    val tile = raids.tile(run, ArchiveRaids.REWARD_X, ArchiveRaids.REWARD_Z)
                    reply("To the reward vault at ${tile.x}, ${tile.z}, ${ArchiveRaids.REWARD_LEVEL}.")
                    return@register
                }
                val (tile, room) = if (name == "nexus") {
                    raids.tile(run, ArchiveRaids.NEXUS_X, ArchiveRaids.NEXUS_Z) to null
                } else {
                    val room = ArchiveRoom.named(name) ?: fail("Rooms: nexus, vault, ${ArchiveRoom.entries.joinToString { it.name.lowercase() }}")
                    raids.tile(run, room.entryX, room.entryZ) to room
                }
                world.teleport(player, tile.x, tile.z, room?.level ?: ArchiveRaids.NEXUS_PLANE)
                reply("To ${room?.boss?.let { "$it's room" } ?: "the nexus"} at ${tile.x}, ${tile.z}.")
            }
            "travel" -> {
                // Testing only: as the Blade's Travel where Ocellus fell, to the reward vault four ticks on.
                if (args.size != 1) fail(usage)
                if (!world.archiveRaids.travel(player)) fail("You can't travel to the reward vault: only from Ocellus's room, once it is done.")
                reply("Travelling to the reward vault.")
            }
            "clear" -> {
                // Testing only: the room you're in counts as done, as if its boss died.
                if (args.size != 1) fail(usage)
                val raids = world.archiveRaids
                val run = raids.at(player.x, player.z) ?: fail("You're not in an Archive run.")
                val room = raids.roomAt(run, player.x, player.z) ?: fail("You're in the nexus.")
                if (room in run.cleared) fail("${room.boss}'s room is done already.")
                if (room.stage != run.stage) fail("${room.boss}'s room isn't the run's next.")
                raids.clearNow(run, room)
                reply("Cleared ${room.boss}'s room.")
            }
            "hold" -> {
                if (args.size > 2) fail(usage)
                val ticks = args.getOrNull(1)?.let { it.toIntOrNull()?.takeIf { n -> n in 1..MAX_HOLD_TICKS } ?: fail("The ticks must be 1 to $MAX_HOLD_TICKS") } ?: DEFAULT_HOLD_TICKS
                val fight = mine()
                // The one that walks: the boss, or whom the fight names when its boss is only its anchor (Bork's).
                val npcs = fight.def.held?.invoke(fight) ?: listOf(fight.boss)
                if (npcs.isEmpty()) fail("Nothing of ${fight.def.name} is left to hold.")
                // A command runs in the tick's input phase, before the tick counts freezes down, which takes one off
                // at once: one more, so it stands this tick and the rest of the ticks asked.
                val held = npcs.filter { world.npcs.freeze(it, ticks + 1) }
                if (held.isEmpty()) fail("${npcs.joinToString { it.type.name }} can't be held now: held already, or not long after a hold.")
                reply("Held ${held.joinToString { it.type.name }} for $ticks ticks.")
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
                // Outside an instance, only the starter and their party are in it: an open-world test fight in
                // Lumbridge would otherwise take in whoever else stands there, such as the other test VM's account
                // (VM 2026-11-03: a mushroom grew by test2 on 217 during 215's script).
                val starter = player
                val admits: (Player) -> Boolean = if (instance != null) {
                    { true }
                } else {
                    { it === starter || world.parties.of(starter)?.let { party -> world.parties.of(it) === party } == true }
                }
                // Outside an instance it ends with its starter's death or logout, as nothing else would end it: they
                // would come back to Lumbridge inside its reach and be killed again and again (nightly 2026-11-08,
                // test2 left mid-fight by a failed script, then stuck dying).
                val calledOff: () -> String? = if (instance != null) {
                    { null }
                } else {
                    {
                        when {
                            starter.dead -> "${starter.username} died"
                            starter.loggedOut || world.onlinePlayers.none { it === starter } -> "${starter.username} left"
                            else -> null
                        }
                    }
                }
                val fight = encounters.start(def, npc, area, phase, admits, calledOff)
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
        "${fight.def.status?.invoke(fight) ?: "${boss.type.name} ${boss.hitpoints}/${boss.maxHitpoints} hp"}; " +
        "${fight.players.size} players, ${fight.adds.size} adds, ${fight.activeHazards.size} hazards"
}

/** How far from the boss's spawn a fight outside an instance reaches. Testing only; the raid's rooms are instances. */
private const val FIGHT_RADIUS = 32

private const val DEFAULT_LOG_LINES = 10

/** `::raid hold`'s ticks unless given, and the most it takes. */
private const val DEFAULT_HOLD_TICKS = 20
private const val MAX_HOLD_TICKS = 200
