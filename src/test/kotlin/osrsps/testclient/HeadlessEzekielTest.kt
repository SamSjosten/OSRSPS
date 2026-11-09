package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.Deaths
import osrsps.game.combat.NpcCombat
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.Npc
import osrsps.game.raid.ArchiveRaids
import osrsps.game.raid.ArchiveRoom
import osrsps.game.raid.ArchiveRun
import osrsps.game.raid.Ezekiel
import osrsps.game.raid.PartyScaling
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import osrsps.map.Tile

/**
 * Ezekiel (raid step 17a) through the headless client: his fight beginning with the first of the party into his room,
 * scaled to the party, him taking that player on and walking to them; his cleave sparing the one behind him; his
 * lightning on everyone when his tank is out of reach; the cross after every second swing and a perfect dodge's
 * special attack energy; and his death doing the room. The rl-client script test-runner/examples/archive-ezekiel.json
 * fights him in the real client.
 */
class HeadlessEzekielTest {
    private val ezekiel = NpcType(
        Ezekiel.BOSS, "Ezekiel", size = 3, ops = listOf(null, "Attack", null, null, null), internalName = "tfa_ezekiel",
        stats = NpcStats(attack = 250, defence = 200, strength = 150, hitpoints = 3000, ranged = 80, magic = 200),
    )
    private val grave = NpcType(Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null))
    private val types = NpcTypes.of(ezekiel, grave)

    private val locTypes = LocTypes(
        mapOf(ArchiveRaids.ARCHIVE_DOOR to LocType(ArchiveRaids.ARCHIVE_DOOR, "Archive Door", ops = listOf("Enter", null, null, null, null), collision = LocCollisionType(width = 9, length = 1))),
    )
    private val staticLocs = StaticLocs().apply { add(ZoneLoc(ArchiveRaids.ARCHIVE_DOOR, LocShape.CENTREPIECE_STRAIGHT, 0, 4123, 5562, 0)) }

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                for (x in 63..65) for (z in 86..88) markLoaded(x, z)
                markLoaded(3222 shr 6, 3218 shr 6)
            },
            locTypes = locTypes, staticLocs = staticLocs,
            npcTypes = types, npcCombat = NpcCombat.load(types, SeqNames.NONE), partyScaling = PartyScaling.load(types),
            encounterDefs = listOfNotNull(Ezekiel.def(types)), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    /** `::raid`'s line on the fight the player is in. */
    private fun HeadlessClient.fight(): String = commandReply("raid") { it.startsWith("ezekiel #") || it.startsWith("You're not") }

    /** `::raid log 200`'s lines, all of them, a plain `::raid` after them marking their end. */
    private fun HeadlessClient.fightLog(): List<String> {
        val from = messages().size
        command("raid log 200")
        command("raid")
        return await("::raid log's lines") {
            val after = messages().drop(from).map { it.text }
            val end = after.indexOfFirst { it.startsWith("ezekiel #") || it.startsWith("You're not") }
            if (end < 0) null else after.take(end)
        }
    }

    private fun HeadlessClient.awaitLog(what: String, predicate: (String) -> Boolean): List<String> =
        await(what) { fightLog().takeIf { lines -> lines.any(predicate) } }

    /** [names] into one party, the first leading. */
    private fun party(clients: List<Pair<HeadlessClient, String>>) {
        val (leader, leaderName) = clients.first()
        for ((client, name) in clients.drop(1)) {
            leader.commandReply("raidparty invite $name") { it.startsWith("Invited") }
            client.commandReply("raidparty accept $leaderName") { it.startsWith("You joined") }
        }
    }

    /** Into the run, through the Guardian's hall (done as it is walked into, its boss not in this world) and on. */
    private fun HeadlessClient.intoRun(server: TestServer, username: String): ArchiveRun {
        await("the login rebuild") { position }
        commandReply("god on") { it.startsWith("You can't be hurt") }
        tele(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z)
        locOp(ArchiveRaids.ARCHIVE_DOOR, 4123, 5562, ArchiveRaids.ENTER)
        await("into the instance") { position?.takeIf { server.world.instances.inDynamicArea(it.x, it.z) } }
        return assertNotNull(server.world.parties.of(server.world.playerNamed(username)!!)?.let(server.world.archiveRaids::of))
    }

    /** Walks to [to], in the instance, where `::tele` doesn't go. */
    private fun HeadlessClient.walkTo(to: Tile) {
        walk(to.x, to.z)
        await("the walk to ${to.x}, ${to.z}") { position?.takeIf { it == Coord(to.x, to.z, 0) } }
    }

    private fun bossOf(run: ArchiveRun): Npc = assertNotNull(run.fights[ArchiveRoom.EZEKIEL]).boss

    @Test
    fun `the cross is its middle and two tiles each way, and the safe spot is his side away from the tank`() {
        assertEquals(9, Ezekiel.cross(10, 10).size)
        assertTrue(Tile(12, 10) in Ezekiel.cross(10, 10) && Tile(10, 8) in Ezekiel.cross(10, 10))
        assertFalse(Tile(11, 11) in Ezekiel.cross(10, 10), "a diagonal step is off it")
        // Him on 10..12; his tank east of him: the west side is safe, the north and south sides aren't.
        val east = Tile(13, 11)
        assertTrue((9..13).all { z -> z !in 9..13 || Ezekiel.behind(10, 10, 3, east, 9, z) })
        assertFalse(Ezekiel.behind(10, 10, 3, east, 11, 13))
        assertFalse(Ezekiel.behind(10, 10, 3, east, 11, 9))
        // His tank off his north-east corner: only the south-west corner is safe.
        val corner = Tile(13, 13)
        assertTrue(Ezekiel.behind(10, 10, 3, corner, 9, 9))
        assertFalse(Ezekiel.behind(10, 10, 3, corner, 9, 11))
        assertFalse(Ezekiel.behind(10, 10, 3, corner, 11, 9))
    }

    @Test
    fun `his fight begins with the first into his room, he walks to them and swings, and his death does the room`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.commandReply("raid tp guardian") { it.startsWith("To ") }
                alice.await("the Guardian's room done") { ArchiveRoom.GUARDIAN.takeIf { it in run.cleared } }
                assertTrue(alice.fight().startsWith("You're not in a fight"))
                alice.commandReply("raid tp ezekiel") { it.startsWith("To ") }
                val begun = alice.await("his fight") { alice.fight().takeIf { it.startsWith("ezekiel #") } }
                // A party of one counts as two: the Wiki's 3,000.
                assertTrue(begun.contains("Ezekiel 3000/3000 hp; 1 players"), begun)
                assertTrue(ArchiveRoom.EZEKIEL in run.started && ArchiveRoom.EZEKIEL !in run.cleared)
                val boss = bossOf(run)
                val at = server.world.archiveRaids.tile(run, ArchiveRaids.EZEKIEL_X, ArchiveRaids.EZEKIEL_Z)
                assertEquals(Tile(at.x, at.z), Tile(boss.spawn.x, boss.spawn.z))
                // She, the first in, is his tank: he walks to her and swings.
                val lines = alice.awaitLog("him swinging at alice, beside her") { it.endsWith("fight: swings at alice") }
                assertEquals(1, lines.count { it.contains("takes on") }, lines.joinToString("\n"))
                assertTrue(lines.any { it.endsWith("fight: takes on alice") })

                alice.commandReply("hit 3000 ${boss.index}") { it.startsWith("Hit ") }
                alice.await("his room done") { alice.commandReply("raid run") { it.startsWith("Archive run") }.takeIf { it.contains("Ezekiel") && ArchiveRoom.EZEKIEL in run.cleared } }
                assertNull(run.fights[ArchiveRoom.EZEKIEL])
            }
        }
    }

    @Test
    fun `his cleave hits those round him but the one behind him, and every second swing brings a cross`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    server.login("carol").use { carol ->
                        val clients = listOf(alice to "alice", bob to "bob", carol to "carol")
                        for ((client, _) in clients) client.await("the login rebuild") { client.position }
                        party(clients)
                        val run = alice.intoRun(server, "alice")
                        for ((client, name) in clients.drop(1)) assertEquals(run, client.intoRun(server, name))
                        alice.commandReply("raid tp guardian") { it.startsWith("To ") }
                        alice.await("the Guardian's room done") { ArchiveRoom.GUARDIAN.takeIf { it in run.cleared } }
                        alice.commandReply("raid tp ezekiel") { it.startsWith("To ") }
                        alice.awaitLog("him swinging at alice") { it.endsWith("fight: swings at alice") }
                        val boss = bossOf(run)
                        // A party of three: the Wiki's 4,350.
                        assertEquals(4350, boss.maxHitpoints)
                        // Bob off a side beside alice's, Carol behind him, opposite alice.
                        val a = server.world.playerNamed("alice")!!
                        val dx = if (a.x < boss.x) -1 else if (a.x >= boss.x + 3) 1 else 0
                        val dz = if (a.z < boss.z) -1 else if (a.z >= boss.z + 3) 1 else 0
                        val (bobAt, carolAt) = when {
                            dx != 0 -> Tile(boss.x + 1, boss.z + 3) to Tile(if (dx > 0) boss.x - 1 else boss.x + 3, boss.z + 1)
                            else -> Tile(boss.x + 3, boss.z + 1) to Tile(boss.x + 1, if (dz > 0) boss.z - 1 else boss.z + 3)
                        }
                        bob.walkTo(bobAt)
                        carol.walkTo(carolAt)
                        val lines = alice.awaitLog("a swing cleaving bob") { it.endsWith("fight: cleaves bob") }
                        assertTrue(lines.none { it.endsWith("fight: cleaves carol") }, lines.joinToString("\n"))
                        // His second swing, and with it a cross on each of them.
                        val crossed = alice.awaitLog("the cross") { it.contains("fight: hazard on") }
                        assertTrue(crossed.count { it.endsWith("fight: swings at alice") } >= 2, crossed.joinToString("\n"))
                    }
                }
            }
        }
    }

    @Test
    fun `with his tank out of reach he casts lightning on everyone`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.commandReply("raid tp guardian") { it.startsWith("To ") }
                alice.await("the Guardian's room done") { ArchiveRoom.GUARDIAN.takeIf { it in run.cleared } }
                alice.commandReply("raid tp ezekiel") { it.startsWith("To ") }
                // She comes in at the room's east end, far from him: his first attack is due before he can reach her.
                alice.awaitLog("his lightning") { it.endsWith("fight: can't reach his tank: lightning on alice") }
                // It lands the tick after.
                alice.awaitLog("the lightning landing on her") { it.contains("fight: hit alice for") }
            }
        }
    }

    @Test
    fun `a perfect dodge of the cross gives back three percent of special attack energy`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.commandReply("raid tp guardian") { it.startsWith("To ") }
                alice.await("the Guardian's room done") { ArchiveRoom.GUARDIAN.takeIf { it in run.cleared } }
                alice.commandReply("raid tp ezekiel") { it.startsWith("To ") }
                alice.awaitLog("his first swing") { it.endsWith("fight: swings at alice") }
                val player = server.world.playerNamed("alice")!!
                server.world.specialAttacks.setEnergy(player, 500)
                // Paused, tick by tick to the cross, then on it till its last tick and a diagonal step off as it lands.
                alice.commandReply("pause") { it.contains("aused after tick") }
                var ticks = 0
                while (alice.fightLog().none { it.contains("fight: hazard on") }) {
                    check(++ticks <= 20) { "no cross within 20 ticks" }
                    alice.stepTicks(1)
                }
                val boss = bossOf(run)
                alice.stepTicks(Ezekiel.CROSS_DELAY - 2)
                // A diagonal step away from him, clicked now: she's on the cross as the tick before its landing looks, and
                // steps off in that tick's movement, so it lands on nobody.
                val sx = if (player.x < boss.x) -1 else 1
                val sz = if (player.z < boss.z) -1 else 1
                val from = Tile(player.x, player.z)
                alice.walk(player.x + sx, player.z + sz)
                alice.awaitCycles(2)
                alice.stepTicks(1)
                assertEquals(Tile(from.x + sx, from.z + sz), Tile(player.x, player.z))
                alice.stepTicks(1)
                alice.commandReply("resume") { it.startsWith("Resumed") }
                alice.awaitLog("the dodge") { it.endsWith("fight: alice dodged the cross") }
                assertEquals(500 + Ezekiel.DODGE_ENERGY, server.world.specialAttacks.energy(player))
            }
        }
    }
}
