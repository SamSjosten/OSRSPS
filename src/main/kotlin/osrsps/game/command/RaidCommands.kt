package osrsps.game.command

import kotlin.math.abs
import kotlin.math.max
import osrsps.game.World
import osrsps.game.encounter.Encounter
import osrsps.game.npc.NpcSpawn
import osrsps.game.raid.PartyScaling

/**
 * `::raid`: starts a boss fight where you stand, at a phase, for a party size and on a seed, so a fight can be played
 * from any point and the same way twice (raid step 12); and shows, logs and stops the fight you are in. The fight's
 * room and gear sets come with each boss's step. In an instance the fight covers the instance; elsewhere, the
 * [FIGHT_RADIUS] tiles round the boss's spawn.
 */
fun CommandRegistry.registerRaidCommands(world: World) {
    val encounters = world.encounters
    val usage = "Usage: ::raid [boss [phase=name|n] [party=n] [seed=n]] | ::raid log [lines] | ::raid digest [ticks] | ::raid stop"

    register("raid", "::raid [boss [phase=] [party=] [seed=]] | log | digest | stop", admin = true) {
        fun here(): Encounter? = encounters.all.firstOrNull { it.boss.level == player.level && it.covers(player.x, player.z) }
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
                val upTo = args.getOrNull(1)?.let { it.toLongOrNull()?.takeIf { n -> n >= 0 } ?: fail("The ticks must be 0 or more") } ?: fight.ticks
                if (upTo > fight.ticks) fail("The fight is only ${fight.ticks} ticks in")
                val lines = fight.lines.filter { it.substringBefore(' ').toLong() <= upTo }
                reply("${fight.def.name} to tick $upTo: ${lines.size} lines, digest ${"%08x".format(lines.joinToString("\n").hashCode())}")
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
