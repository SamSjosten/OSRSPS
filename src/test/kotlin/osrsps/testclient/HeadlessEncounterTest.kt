package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.World
import osrsps.game.encounter.CycleStep
import osrsps.game.encounter.EncounterDef
import osrsps.game.encounter.PhaseDef
import osrsps.game.raid.PartyScaling
import osrsps.map.CollisionMap
import osrsps.map.Tile

/**
 * The encounter framework (raid step 12) through the headless client and `::raid`: a test boss plays the same 100
 * ticks on two runs with the same seed and differently on another, and its phases change on its hitpoints and on
 * time, with adds and tile hazards, ending when the boss leaves.
 */
class HeadlessEncounterTest {
    private val guardian = NpcType(16659, "Archive Guardian", size = 5, internalName = "tfa_guardian", stats = NpcStats(hitpoints = 550))
    private val man = NpcType(3106, "Man", internalName = "man", stats = NpcStats(hitpoints = 7))
    private val types = NpcTypes.of(guardian, man)

    /** Random from start to finish: who it hits for how much, where its hazards fall, and an add at a random spot. */
    private val drill = EncounterDef(
        "drill", guardian,
        listOf(
            PhaseDef(
                "strikes",
                cycle = listOf(
                    CycleStep(4) { randomPlayer()?.let { hit(it, roll(0, 20)) } },
                    CycleStep(3) {
                        val target = randomPlayer() ?: return@CycleStep
                        val tiles = List(3) { Tile(target.x + roll(-2, 2), target.z + roll(-2, 2)) }
                        hazard(tiles, warning = -1, warnTicks = 2, ticks = 3, minDamage = 1, maxDamage = 6)
                    },
                ),
                endsAfter = 40,
            ),
            PhaseDef(
                "swarm",
                cycle = listOf(CycleStep(5) { spawnAdd(man, boss.x + roll(-4, 4), boss.z + 6) }, CycleStep(2) { randomPlayer()?.let { hit(it, roll(5, 15)) } }),
            ),
        ),
    )

    /** No rolls: a hazard 2 east of the boss every other tick, an add when it falls to half, then a quiet phase. */
    private val steps = EncounterDef(
        "steps", guardian,
        listOf(
            PhaseDef(
                "open",
                cycle = listOf(CycleStep(2) { hazard(listOf(Tile(boss.x + 6, boss.z)), warning = -1, warnTicks = 1, ticks = 1, minDamage = 1, maxDamage = 1) }),
                endsAtPercent = 50,
            ),
            PhaseDef("adds", onStart = { spawnAdd(man, boss.x + 6, boss.z + 2) }, endsAfter = 10),
            PhaseDef("last"),
        ),
    )

    /** The world the test server runs, for a boss that takes itself out mid-step. */
    private lateinit var running: World

    /** A hazard on every player's tile, then the boss leaves in the same step: nothing should land. */
    private val vanish = EncounterDef(
        "vanish", guardian,
        listOf(
            PhaseDef(
                "open",
                cycle = listOf(
                    CycleStep(2) {
                        hazard(players.map { Tile(it.x, it.z) }, warning = -1, warnTicks = 0, ticks = 5, minDamage = 1, maxDamage = 1)
                        running.removeNpc(boss)
                    },
                    CycleStep(5) {},
                ),
            ),
        ),
    )

    /** A 3-damage hazard on every player's tile at once, for one tick, then nothing for a while. */
    private val zap = EncounterDef(
        "zap", guardian,
        listOf(
            PhaseDef(
                "open",
                cycle = listOf(
                    CycleStep(0) { hazard(players.map { Tile(it.x, it.z) }, warning = -1, warnTicks = 0, ticks = 1, minDamage = 3, maxDamage = 3) },
                    CycleStep(50) {},
                ),
            ),
        ),
    )

    /** Goes round: up and down three times, then on to the one after, whose own next names a phase the fight hasn't. */
    private var rounds = 0
    private val round = EncounterDef(
        "round", guardian,
        listOf(
            PhaseDef("up", endsAfter = 2),
            PhaseDef("down", endsAfter = 3, onStart = { rounds++ }, next = { if (rounds < 3) "up" else null }),
            PhaseDef("last", endsAfter = 4, next = { "nowhere" }),
        ),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, npcTypes = types, partyScaling = PartyScaling.load(types),
            encounterDefs = listOf(drill, round, steps, vanish, zap), cycleMillis = millis,
        ).also { running = it }
    }

    /** Fights the drill on [seed] and returns `::raid digest 100`. */
    private fun drillDigest(dir: Path, seed: Long): String = server(dir).use { server ->
        server.login("alice").use { alice ->
            alice.await("the login rebuild") { alice.position }
            alice.commandReply("god on") { it.startsWith("You can't be hurt") }
            assertEquals(
                "Started drill #1 on Archive Guardian #1 at 3222, 3218, phase 1 (strikes), 550 hp for a party of 1, seed $seed.",
                alice.commandReply("raid drill seed=$seed") { it.startsWith("Started") || it.startsWith("No") || it.startsWith("Usage") },
            )
            alice.await("100 ticks of the fight", timeoutMillis = 30_000) {
                val from = alice.messages().size
                alice.command("raid")
                alice.await("::raid's line") { alice.messages().drop(from).firstOrNull { it.text.startsWith("drill #1") }?.text }
                    .takeIf { Regex("of (\\d+) ticks").find(it)!!.groupValues[1].toInt() > 100 }
            }
            alice.commandReply("raid digest 100") { it.startsWith("drill to tick 100") }
        }
    }

    @Test
    fun `a test boss plays the same 100 ticks on two runs with the same seed, and not on another`(@TempDir dir: Path) {
        val first = drillDigest(dir.resolve("a"), 42)
        val second = drillDigest(dir.resolve("b"), 42)
        val other = drillDigest(dir.resolve("c"), 43)
        assertEquals(first, second)
        assertNotEquals(first, other)
        // Both phases ran: 40 ticks of strikes then the swarm, so there is plenty in the log.
        val lines = Regex("(\\d+) lines").find(first)!!.groupValues[1].toInt()
        assertTrue(lines > 30, first)
    }

    @Test
    fun `phases change at half hitpoints and on time, with an add and a hazard, and the fight ends with its boss`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                val start = alice.commandReply("raid steps party=3") { it.startsWith("Started") }
                assertEquals("Started steps #1 on Archive Guardian #1 at 3222, 3218, phase 1 (open), 800 hp for a party of 3.", start)
                // Onto the hazard's tile, 6 east of the boss's south-west tile, clear of its 5 x 5.
                alice.commandReply("tele 3228 3218 0") { it.startsWith("Teleported") }
                alice.await("a hazard hit") {
                    val from = alice.messages().size
                    alice.command("raid log 20")
                    alice.awaitTicks(1)
                    alice.messages().drop(from).firstOrNull { it.text.endsWith("open: hazard hit alice for 1") }
                }

                alice.commandReply("npchp 400 1") { it.contains("now on 400 hitpoints") }
                val adds = alice.await("the adds phase") {
                    val from = alice.messages().size
                    alice.command("raid")
                    alice.await("::raid's line") { alice.messages().drop(from).firstOrNull { it.text.startsWith("steps #1") }?.text }
                        .takeIf { it.contains("phase 2 (adds)") }
                }
                assertTrue("400/800 hp; 1 players, 1 adds" in adds, adds)
                alice.await("the last phase, 10 ticks on", timeoutMillis = 10_000) {
                    val from = alice.messages().size
                    alice.command("raid")
                    alice.await("::raid's line") { alice.messages().drop(from).firstOrNull { it.text.startsWith("steps #1") }?.text }
                        .takeIf { it.contains("phase 3 (last)") }
                }

                // The boss leaving ends the fight and takes its add.
                alice.commandReply("delnpc 1") { it.startsWith("Removed") }
                alice.awaitTicks(2)
                assertEquals("You're not in a fight. Bosses: drill, round, steps, vanish, zap", alice.commandReply("raid") { it.startsWith("You're not") })
                assertEquals("No NPCs within 15 tiles.", alice.commandReply("npcs") { it.startsWith("No NPCs") || it.contains("NPC") })
            }
        }
    }

    @Test
    fun `a phase goes on to the one it names, an earlier one too, or to the one after it, and a name it hasn't ends the fight`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                alice.commandReply("raid round") { it.startsWith("Started") }
                val fight = server.world.encounters.all.single()
                alice.await("the fight ended", timeoutMillis = 10_000) { fight.ended.takeIf { it } }
                val first = fight.lines.first().substringBefore(' ').toLong()
                val told = fight.lines.map { line -> "${line.substringBefore(' ').toLong() - first} ${line.substringAfter(' ')}" }
                assertEquals(
                    listOf(
                        "0 up: phase starts", "2 down: phase starts", "5 up: phase starts", "7 down: phase starts", "10 up: phase starts",
                        "12 down: phase starts", "15 last: phase starts", "19 last: ends: no phase 'nowhere' to go on to",
                    ),
                    told,
                )
            }
        }
    }

    @Test
    fun `a boss that leaves in a step takes the hazard it just put down with it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                alice.commandReply("raid vanish") { it.startsWith("Started") }
                alice.awaitTicks(6)
                assertEquals("You're not in a fight. Bosses: drill, round, steps, vanish, zap", alice.commandReply("raid") { it.startsWith("You're not") })
                assertEquals("You have 10/10 hitpoints.", alice.commandReply("hp") { it.startsWith("You have") })
            }
        }
    }

    @Test
    fun `a hazard due as the fight starts lands once, whether the world is paused or running`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                alice.commandReply("pause") { it.isNotEmpty() }
                alice.commandReply("raid zap") { it.startsWith("Started") }
                assertEquals("You have 7/10 hitpoints.", alice.commandReply("hp") { it.startsWith("You have") })
                // The hit counts as combat (a cast cut short, a logout held), though from no one to hit back at.
                val player = server.world.playerNamed("alice")!!
                assertEquals(server.world.tick, player.attackedTick)
                assertEquals(null, player.attackedBy)
                alice.commandReply("step 2") { it.isNotEmpty() }
                alice.awaitTicks(2)
                alice.commandReply("resume") { it.isNotEmpty() }
                alice.awaitTicks(2)
                assertEquals("You have 7/10 hitpoints.", alice.commandReply("hp") { it.startsWith("You have") }, "landed once")
                alice.commandReply("raid stop") { it.startsWith("Stopped") }

                alice.commandReply("raid zap") { it.startsWith("Started") }
                alice.awaitTicks(3)
                assertEquals("You have 4/10 hitpoints.", alice.commandReply("hp") { it.startsWith("You have") }, "and once more running")
            }
        }
    }
}
