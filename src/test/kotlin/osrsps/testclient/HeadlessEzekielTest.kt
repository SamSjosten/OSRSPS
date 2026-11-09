package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
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
import osrsps.game.npc.Npcs
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
    private val sword = NpcType(
        Ezekiel.SWORD, "Animated sword", ops = listOf(null, "Attack", null, null, null), internalName = "tfa_ezekiel_sword",
        stats = NpcStats(hitpoints = 100),
    )
    private val grave = NpcType(Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null))
    private val types = NpcTypes.of(ezekiel, sword, grave)

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
    fun `the cross is two bands his width through him, the safe spot is his side away from the tank, and a sword's lane`() {
        val cross = Ezekiel.cross(10, 10, 3)
        assertEquals(2 * 2 * Ezekiel.CROSS_REACH * 3, cross.size)
        assertTrue(Tile(13, 11) in cross && Tile(11, 9) in cross && Tile(10 - Ezekiel.CROSS_REACH, 12) in cross)
        assertFalse(Tile(13 + Ezekiel.CROSS_REACH, 11) in cross, "past its reach")
        assertFalse(Tile(11, 11) in cross, "his own tiles")
        assertFalse(Tile(13, 13) in cross, "off his corner: a diagonal step from the end of a side is off it")
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
        // A lane across x at z 5 to 7: him at z 5 is lined up with it, a tile off isn't.
        val lane = Ezekiel.lane(true, 5, 3, 0, 20)
        assertEquals(63, lane.size)
        assertTrue(Tile(0, 5) in lane && Tile(20, 7) in lane && Tile(3, 8) !in lane)
        assertTrue(Ezekiel.linedUp(true, 5, 9, 5))
        assertFalse(Ezekiel.linedUp(true, 5, 9, 6))
        assertTrue(Ezekiel.linedUp(false, 4, 4, 30))
        // Its hits: 20 to 80 on him with its charge, and up to twice on a player.
        assertEquals(Ezekiel.SWORD_HIT, Ezekiel.swordDamage(0.0))
        assertEquals(50, Ezekiel.swordDamage(0.5))
        assertEquals(Ezekiel.SWORD_HIT_FULL, Ezekiel.swordDamage(1.0))
        assertEquals(Ezekiel.LANE_MIN * 2..Ezekiel.LANE_MAX * 2, Ezekiel.laneDamage(1.0))
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
    fun `he walks after his tank past the seven tiles an ordinary monster keeps to of its spawn`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.commandReply("raid tp guardian") { it.startsWith("To ") }
                alice.await("the Guardian's room done") { ArchiveRoom.GUARDIAN.takeIf { it in run.cleared } }
                alice.commandReply("raid tp ezekiel") { it.startsWith("To ") }
                alice.await("his fight") { alice.fight().takeIf { it.startsWith("ezekiel #") } }
                val boss = bossOf(run)
                // Twelve tiles east of his spawn: within the range a fighting monster chases to, past the one it walks to.
                val far = Tile(boss.spawn.x + 12, boss.spawn.z + 1)
                // She stops a tile short, where his body is in the way: no matter, he's past his seven tiles either way.
                alice.walk(far.x, far.z)
                alice.await("him walking past his seven tiles") { boss.takeIf { it.x - it.spawn.x > Npcs.MAX_RANGE } }
                alice.awaitLog("him swinging at alice") { it.endsWith("fight: swings at alice") }
                assertSame(server.world.playerNamed("alice"), boss.target)
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
                        val crossed = alice.awaitLog("the cross") { it.contains("fight: cross on him") }
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
                val boss = bossOf(run)
                // To the end of the side she's beside, where a diagonal step out takes her off his corner, off the cross.
                val (end, off) = when {
                    player.x == boss.x - 1 || player.x == boss.x + 3 -> {
                        val sx = if (player.x < boss.x) -1 else 1
                        Tile(player.x, boss.z + 2) to Tile(player.x + sx, boss.z + 3)
                    }
                    else -> {
                        val sz = if (player.z < boss.z) -1 else 1
                        Tile(boss.x + 2, player.z) to Tile(boss.x + 3, player.z + sz)
                    }
                }
                alice.walkTo(end)
                server.world.specialAttacks.setEnergy(player, 500)
                // Paused, tick by tick to the cross, then on it till its last tick and a diagonal step off as it lands.
                alice.commandReply("pause") { it.contains("aused after tick") }
                val crossesBefore = alice.fightLog().count { it.contains("fight: cross on him") }
                var ticks = 0
                while (alice.fightLog().count { it.contains("fight: cross on him") } == crossesBefore) {
                    check(++ticks <= 20) { "no cross within 20 ticks" }
                    alice.stepTicks(1)
                }
                assertTrue(Tile(player.x, player.z) in Ezekiel.cross(boss.x, boss.z, 3), "she's on it, beside him")
                alice.stepTicks(Ezekiel.CROSS_DELAY - 2)
                // Clicked now: she's on the cross as the tick before its landing looks, and steps off in that tick's
                // movement, so it lands on nobody.
                alice.walk(off.x, off.z)
                alice.awaitCycles(2)
                alice.stepTicks(1)
                assertEquals(off, Tile(player.x, player.z))
                assertFalse(off in Ezekiel.cross(boss.x, boss.z, 3))
                alice.stepTicks(1)
                alice.commandReply("resume") { it.startsWith("Resumed") }
                alice.awaitLog("the dodge") { it.endsWith("fight: alice dodged the cross") }
                assertEquals(500 + Ezekiel.DODGE_ENERGY, server.world.specialAttacks.energy(player))
            }
        }
    }

    /** The swords of the volley out, from the fight's log: where each stands, across x or not, and its lane's offset. */
    private data class LoggedSword(val at: Tile, val alongX: Boolean, val offset: Int)

    private fun swordsIn(lines: List<String>): List<LoggedSword> {
        val pattern = Regex("""fight: sword at (\d+),(\d+) across ([xz]) at (\d+)$""")
        return lines.mapNotNull { pattern.find(it) }.map { m ->
            val (x, z, axis, offset) = m.destructured
            LoggedSword(Tile(x.toInt(), z.toInt()), axis == "x", offset.toInt())
        }
    }

    /** Paused, tick by tick to his first volley of swords; its swords. */
    private fun HeadlessClient.toVolley(): List<LoggedSword> {
        commandReply("pause") { it.contains("aused after tick") }
        var ticks = 0
        while (true) {
            val swords = swordsIn(fightLog())
            if (swords.isNotEmpty()) return swords
            check(++ticks <= 40) { "no swords within 40 ticks" }
            stepTicks(1)
        }
    }

    @Test
    fun `a charged sword lined up with him hits him harder and stuns him`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.commandReply("raid tp guardian") { it.startsWith("To ") }
                alice.await("the Guardian's room done") { ArchiveRoom.GUARDIAN.takeIf { it in run.cleared } }
                alice.commandReply("raid tp ezekiel") { it.startsWith("To ") }
                alice.await("his fight") { alice.fight().takeIf { it.startsWith("ezekiel #") } }
                val swords = alice.toVolley()
                assertTrue(swords.size in Ezekiel.SWORDS_MIN..Ezekiel.SWORDS_MAX, swords.toString())
                val boss = bossOf(run)
                val first = swords.first()
                val npc = server.world.npcs.all.first { it.type.id == Ezekiel.SWORD && it.x == first.at.x && it.z == first.at.z }
                // Half charged: 50 of its 100.
                alice.commandReply("hit 50 ${npc.index}") { it.startsWith("Hit ") }
                alice.stepTicks(Ezekiel.SWORD_LAUNCH - 1)
                // Lined up with its lane as it flies, and with none of the others.
                val others = swords.drop(1)
                val (x, z) = if (first.alongX) {
                    val x = (boss.spawn.x - 6..boss.spawn.x + 6).first { x -> others.none { !it.alongX && Ezekiel.linedUp(false, it.offset, x, first.offset) } }
                    x to first.offset
                } else {
                    val z = (boss.spawn.z - 6..boss.spawn.z + 6).first { z -> others.none { it.alongX && Ezekiel.linedUp(true, it.offset, first.offset, z) } }
                    first.offset to z
                }
                server.world.npcs.teleport(boss, x, z)
                val before = boss.hitpoints
                alice.stepTicks(1)
                val lines = alice.fightLog()
                assertTrue(lines.any { it.endsWith("fight: sword hits him for 50") }, lines.joinToString("\n"))
                assertTrue(lines.any { it.endsWith("fight: stunned for ${Ezekiel.STUN_TICKS} ticks") })
                assertTrue(lines.none { it.contains("heals") })
                assertEquals(before - 50, boss.hitpoints)
                assertTrue(server.world.npcs.all.none { it.type.id == Ezekiel.SWORD }, "the swords are gone once flown")
                // Stunned, he neither walks nor swings.
                val swingsBefore = lines.count { it.endsWith("fight: swings at alice") || it.contains("lightning on") }
                val at = Tile(boss.x, boss.z)
                alice.stepTicks(Ezekiel.STUN_TICKS - 1)
                assertEquals(at, Tile(boss.x, boss.z))
                assertEquals(swingsBefore, alice.fightLog().count { it.endsWith("fight: swings at alice") || it.contains("lightning on") })
                alice.commandReply("resume") { it.startsWith("Resumed") }
            }
        }
    }

    @Test
    fun `with no sword lined up with him he heals`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.commandReply("raid tp guardian") { it.startsWith("To ") }
                alice.await("the Guardian's room done") { ArchiveRoom.GUARDIAN.takeIf { it in run.cleared } }
                alice.commandReply("raid tp ezekiel") { it.startsWith("To ") }
                alice.await("his fight") { alice.fight().takeIf { it.startsWith("ezekiel #") } }
                val boss = bossOf(run)
                val swords = alice.toVolley()
                alice.commandReply("hit 1000 ${boss.index}") { it.startsWith("Hit ") }
                alice.stepTicks(Ezekiel.SWORD_LAUNCH - 1)
                // Somewhere lined up with no lane.
                val spot = (boss.spawn.x - 8..boss.spawn.x + 8).flatMap { x -> (boss.spawn.z - 8..boss.spawn.z + 8).map { z -> x to z } }
                    .first { (x, z) -> swords.none { Ezekiel.linedUp(it.alongX, it.offset, x, z) } }
                server.world.npcs.teleport(boss, spot.first, spot.second)
                val before = boss.hitpoints
                alice.stepTicks(1)
                val lines = alice.fightLog()
                val heal = boss.maxHitpoints * Ezekiel.HEAL_PERCENT / 100
                assertTrue(lines.any { it.endsWith("fight: no sword hit him: heals $heal") }, lines.joinToString("\n"))
                assertTrue(lines.none { it.contains("sword hits him") })
                assertEquals(before + heal, boss.hitpoints)
                alice.commandReply("resume") { it.startsWith("Resumed") }
            }
        }
    }
}
