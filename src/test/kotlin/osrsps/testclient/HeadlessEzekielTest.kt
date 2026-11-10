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
import osrsps.game.EquipmentTab
import osrsps.game.GameLines
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
import osrsps.game.npc.NpcSpawn
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
 * Ezekiel (raid steps 17a to 17c) through the headless client: his fight beginning with the first of the party into his room,
 * scaled to the party, him taking that player on and walking to them; his cleave sparing the one behind him; his
 * lightning on everyone when his tank is out of reach; the cross after every second swing and a perfect dodge's
 * special attack energy; his flying swords; his enrage at 35%, its arena, fence and strikes; and his death doing the
 * room. The rl-client script test-runner/examples/archive-ezekiel.json
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
    private val mark = NpcType(Ezekiel.SWORD_NOOP, "Animated sword", internalName = "tfa_ezekiel_sword_noop")
    private val grave = NpcType(Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null))
    private val navigator = NpcType(ArchiveRaids.NAVIGATOR, "Alliance Navigator", ops = listOf("Follow", null, "Quick-follow", null, null))
    private val types = NpcTypes.of(ezekiel, sword, mark, grave, navigator)

    private val locTypes = LocTypes(
        mapOf(ArchiveRaids.ARCHIVE_DOOR to LocType(ArchiveRaids.ARCHIVE_DOOR, "Archive Door", ops = listOf("Enter", null, null, null, null), collision = LocCollisionType(width = 9, length = 1))),
    )
    private val staticLocs = StaticLocs().apply { add(ZoneLoc(ArchiveRaids.ARCHIVE_DOOR, LocShape.CENTREPIECE_STRAIGHT, 0, 4123, 5562, 0)) }

    private fun server(dir: Path, types: NpcTypes = this.types) = TestServer(dir) { crcs, millis ->
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

    /**
     * Into Ezekiel's room and, as its transporter does, into his arena: at the tile it puts a player on, or [cacheX],
     * [cacheZ] (this world has no transporter loc, so a `::tele`).
     */
    private fun HeadlessClient.intoArena(
        server: TestServer,
        run: ArchiveRun,
        cacheX: Int = ArchiveRaids.EZEKIEL_ARENA_X,
        cacheZ: Int = ArchiveRaids.EZEKIEL_ARENA_Z,
    ) {
        commandReply("raid tp ezekiel") { it.startsWith("To ") }
        val at = server.world.archiveRaids.tile(run, cacheX, cacheZ)
        tele(at.x, at.z)
    }

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
        assertTrue(Ezekiel.inLane(lane, 18, 5, 3))
        assertFalse(Ezekiel.inLane(lane, 19, 5, 3), "lined up, but one tile of him past the lane's end")
        assertFalse(Ezekiel.inLane(lane, 9, 6, 3))
        // Its hits: 20 to 80 on him with its charge, and up to twice on a player.
        assertEquals(Ezekiel.SWORD_HIT, Ezekiel.swordDamage(0.0))
        assertEquals(50, Ezekiel.swordDamage(0.5))
        assertEquals(Ezekiel.SWORD_HIT_FULL, Ezekiel.swordDamage(1.0))
        assertEquals(Ezekiel.LANE_MIN * 2..Ezekiel.LANE_MAX * 2, Ezekiel.laneDamage(1.0))
    }

    @Test
    fun `his fight begins with the first into his arena, he walks to them and swings, and his death does the room`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.commandReply("raid tp guardian") { it.startsWith("To ") }
                alice.await("the Guardian's room done") { ArchiveRoom.GUARDIAN.takeIf { it in run.cleared } }
                assertTrue(alice.fight().startsWith("You're not in a fight"))
                // At the way into his room nothing begins: his arena is walled off from it, past the room's transporter.
                alice.commandReply("raid tp ezekiel") { it.startsWith("To ") }
                alice.awaitTicks(5)
                assertFalse(ArchiveRoom.EZEKIEL in run.started, "his fight begun at the way in")
                alice.intoArena(server, run)
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
    fun `with no NPC slot free his room isn't started, and his fight begins once one is`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.commandReply("raid tp guardian") { it.startsWith("To ") }
                alice.await("the Guardian's room done") { ArchiveRoom.GUARDIAN.takeIf { it in run.cleared } }
                // Every free slot taken, by NPCs standing still on one tile far off.
                val npcs = server.world.npcs
                val filler = NpcType(1, "Filler")
                val fillers = generateSequence { npcs.add(NpcSpawn(filler, 1000, 1000, source = "test")) }.toList()
                assertTrue(fillers.size > 1000, "${fillers.size} fillers")
                alice.intoArena(server, run)
                alice.awaitTicks(5)
                assertFalse(ArchiveRoom.EZEKIEL in run.started, "his room started without him")
                assertNull(run.fights[ArchiveRoom.EZEKIEL])
                // A slot frees: with her still in his room, his fight begins.
                for (npc in fillers) npcs.remove(npc)
                val begun = alice.await("his fight") { alice.fight().takeIf { it.startsWith("ezekiel #") } }
                assertTrue(begun.contains("Ezekiel 3000/3000 hp; 1 players"), begun)
                assertTrue(ArchiveRoom.EZEKIEL in run.started && ArchiveRoom.EZEKIEL !in run.cleared)
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
                // Auto retaliate off, so his swings don't turn her back to him on her way.
                alice.command("varp ${EquipmentTab.VARP_AUTO_RETALIATE_OFF} 1")
                alice.await("auto retaliate off") { if (alice.varp(EquipmentTab.VARP_AUTO_RETALIATE_OFF) == 1) Unit else null }
                alice.intoArena(server, run)
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
                        alice.intoArena(server, run)
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
                // In at the arena's far corner from him: his first attack is due before he can reach her.
                alice.intoArena(server, run, ArchiveRaids.EZEKIEL_X + Ezekiel.ARENA_WEST, ArchiveRaids.EZEKIEL_Z + Ezekiel.ARENA_SOUTH)
                alice.awaitLog("his lightning") { it.endsWith("fight: can't reach his tank: lightning on alice") }
                // It lands the tick after.
                alice.awaitLog("the lightning landing on her") { it.contains("fight: hit alice for") }
            }
        }
    }

    @Test
    fun `kiting him out of reach still brings the cross and the swords, his lightning counting as his swings do`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.commandReply("raid tp guardian") { it.startsWith("To ") }
                alice.await("the Guardian's room done") { ArchiveRoom.GUARDIAN.takeIf { it in run.cleared } }
                alice.intoArena(server, run, ArchiveRaids.EZEKIEL_X + Ezekiel.ARENA_WEST, ArchiveRaids.EZEKIEL_Z + Ezekiel.ARENA_SOUTH)
                alice.await("his fight") { alice.fight().takeIf { it.startsWith("ezekiel #") } }
                // Held where he stands as soon as it begins, as a kite keeps him off her: every attack is lightning, and the
                // second brings the cross.
                alice.commandReply("raid hold 100") { it.startsWith("Held Ezekiel") }
                val lines = alice.awaitLog("the cross") { it.contains("fight: cross on him") }
                val upToCross = lines.take(lines.indexOfFirst { it.contains("fight: cross on him") } + 1)
                assertEquals(2, upToCross.count { it.endsWith("fight: can't reach his tank: lightning on alice") }, lines.joinToString("\n"))
                assertTrue(upToCross.none { it.contains("swings at") }, lines.joinToString("\n"))
                alice.awaitLog("the swords with it") { it.contains("fight: sword at") }
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
                alice.intoArena(server, run)
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
                alice.intoArena(server, run)
                alice.await("his fight") { alice.fight().takeIf { it.startsWith("ezekiel #") } }
                val swords = alice.toVolley()
                assertTrue(swords.size in Ezekiel.SWORDS_MIN..Ezekiel.SWORDS_MAX, swords.toString())
                val boss = bossOf(run)
                // Each wholly on his arena, three by three from where it stands, at an end of its lane (R21A-3).
                for (sword in swords) {
                    assertTrue(Ezekiel.inArena(boss.spawn.x, boss.spawn.z, sword.at.x, sword.at.z), "$sword")
                    assertTrue(Ezekiel.inArena(boss.spawn.x, boss.spawn.z, sword.at.x + 2, sword.at.z + 2), "$sword")
                    val (along, across) = if (sword.alongX) sword.at.x - boss.spawn.x to sword.at.z else sword.at.z - boss.spawn.z to sword.at.x
                    assertEquals(sword.offset, across, "$sword")
                    val ends = if (sword.alongX) listOf(Ezekiel.ARENA_WEST, Ezekiel.ARENA_EAST - 2) else listOf(Ezekiel.ARENA_SOUTH, Ezekiel.ARENA_NORTH - 2)
                    assertTrue(along in ends, "$sword")
                }
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
                // Stunned, he neither walks nor swings.
                val swingsBefore = lines.count { it.endsWith("fight: swings at alice") || it.contains("lightning on") }
                val at = Tile(boss.x, boss.z)
                alice.stepTicks(Ezekiel.STUN_TICKS - 1)
                assertEquals(at, Tile(boss.x, boss.z))
                assertTrue(server.world.npcs.all.none { it.type.id == Ezekiel.SWORD }, "the swords are gone once flown")
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
                alice.intoArena(server, run)
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

    @Test
    fun `the enrage's arena is three by three east of him, fenced on its other sides, its marks on his tiles and its safe tiles its far column`() {
        val arena = Ezekiel.arenaFor(100, 200)
        assertEquals(Tile(103, 200), arena)
        val tiles = Ezekiel.arenaTiles(arena)
        assertEquals(9, tiles.size)
        assertEquals((103..105).toList(), tiles.map { it.x }.distinct().sorted())
        val fence = Ezekiel.fence(arena)
        assertEquals(11, fence.size)
        assertEquals(11, fence.toSet().size)
        assertTrue(fence.none { it in tiles })
        // Every tile round the arena but its west side, which is his (x 100 to 102).
        val round = (102..106).flatMap { x -> (199..203).map { z -> Tile(x, z) } }.filter { it !in tiles && it.x > 102 }
        assertEquals(round.toSet(), fence.toSet())
        for (row in 0 until 3) {
            val mark = Ezekiel.mark(arena, row)
            assertTrue(mark !in fence && mark.x == 102 && mark.z in 200..202, "$mark on his east column")
            val safe = Ezekiel.safeTile(arena, row)
            assertTrue(safe in tiles)
            assertEquals(Tile(mark.x + 3, mark.z), safe)
        }
        // As the captures have it: him at 4059,5534 from 4062,5537, the three by three 4062-4064 x 5534-5536, on his arena.
        val live = Ezekiel.arenaFor(4062 + Ezekiel.ENRAGE_DX, 5537 + Ezekiel.ENRAGE_DZ)
        assertEquals(Tile(4062, 5534), live)
        assertTrue((Ezekiel.arenaTiles(live) + Ezekiel.fence(live)).all { Ezekiel.inArena(4062, 5537, it.x, it.z) })
        assertTrue(Ezekiel.inArena(4062, 5537, 4056, 5528) && Ezekiel.inArena(4062, 5537, 4068, 5540))
        assertFalse(Ezekiel.inArena(4062, 5537, 4069, 5535) || Ezekiel.inArena(4062, 5537, 4060, 5541) || Ezekiel.inArena(4062, 5537, 4055, 5530))
    }

    @Test
    fun `nobody attacks him from outside his arena, beside him or not`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.commandReply("raid tp guardian") { it.startsWith("To ") }
                alice.await("the Guardian's room done") { ArchiveRoom.GUARDIAN.takeIf { it in run.cleared } }
                alice.intoArena(server, run)
                alice.await("his fight") { alice.fight().takeIf { it.startsWith("ezekiel #") } }
                alice.commandReply("raid hold 100") { it.startsWith("Held Ezekiel") }
                val world = server.world
                val boss = bossOf(run)
                val player = world.playerNamed("alice")!!
                assertNull(world.combat.barred(player, boss), "on his arena")
                // At the room's way in: shut out.
                alice.commandReply("raid tp ezekiel") { it.startsWith("To ") }
                alice.await("the way in") { if (world.combat.barred(player, boss) === GameLines.CANT_REACH) Unit else null }
                // Him at his arena's east edge, her beside him a tile past it: still shut out, and her Attack is refused.
                world.npcs.teleport(boss, boss.spawn.x + Ezekiel.ARENA_EAST - 2, boss.spawn.z)
                alice.tele(boss.spawn.x + Ezekiel.ARENA_EAST + 1, boss.spawn.z + 1)
                assertSame(GameLines.CANT_REACH, world.combat.barred(player, boss))
                alice.npcOp(boss.index, 2)
                alice.await("the refusal") { alice.messages().firstOrNull { it.text == "[game.cant_reach]" } }
                alice.awaitTicks(4)
                assertTrue(alice.npcHits().none { it.index == boss.index }, "${alice.npcHits()}")
                // A step onto it, south of him: her swing goes.
                alice.tele(boss.spawn.x + Ezekiel.ARENA_EAST - 1, boss.spawn.z - 1)
                assertNull(world.combat.barred(player, boss))
                alice.npcOp(boss.index, 2)
                alice.await("her swing") { alice.npcHits().firstOrNull { it.index == boss.index } }
            }
        }
    }

    @Test
    fun `his death brings the Alliance Navigator to his arena three ticks on, and following him leads back to the nexus four ticks later`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.commandReply("raid tp guardian") { it.startsWith("To ") }
                alice.await("the Guardian's room done") { ArchiveRoom.GUARDIAN.takeIf { it in run.cleared } }
                alice.intoArena(server, run)
                alice.await("his fight") { alice.fight().takeIf { it.startsWith("ezekiel #") } }
                val world = server.world
                val raids = world.archiveRaids
                val boss = bossOf(run)
                fun navigators() = world.npcs.all.filter { it.type.id == ArchiveRaids.NAVIGATOR }.toList()
                alice.commandReply("pause") { it.contains("aused after tick") }
                alice.commandReply("hit 3000 ${boss.index}") { it.startsWith("Hit ") }
                var ticks = 0
                while (ArchiveRoom.EZEKIEL !in run.cleared) {
                    check(++ticks <= 40) { "his room not done within 40 ticks" }
                    alice.stepTicks(1)
                }
                assertTrue(navigators().isEmpty(), "the Navigator there as the room is won")
                alice.stepTicks(ArchiveRaids.NAVIGATOR_DELAY - 1)
                assertTrue(navigators().isEmpty(), "the Navigator there early")
                alice.stepTicks(1)
                val navigator = navigators().single()
                val entry = raids.tile(run, ArchiveRaids.EZEKIEL_ARENA_X, ArchiveRaids.EZEKIEL_ARENA_Z)
                assertEquals(entry, Tile(navigator.x, navigator.z))
                alice.commandReply("resume") { it.startsWith("Resumed") }
                val front = raids.tile(run, ArchiveRoom.EZEKIEL.doorFront.x, ArchiveRoom.EZEKIEL.doorFront.z)
                // Follow: his one line, with her name, then out in front of the room's door, four ticks after it is clicked on.
                alice.npcOp(navigator.index, 1)
                val said = alice.await("his line") { alice.dialogueView as? DialogueView.Chat }
                assertEquals("Alliance Navigator", said.name)
                assertTrue(said.text.contains("tfa_navigator.onward"), said.text)
                assertTrue(raids.roomAt(run, alice.position!!.x, alice.position!!.z) == ArchiveRoom.EZEKIEL, "out before his line is done")
                alice.continueDialogue()
                alice.await("the nexus, in front of his room's door") { alice.position?.takeIf { it == Coord(front.x, front.z, 0) } }
                assertTrue(run.following.isEmpty(), "her follow done with")
                // Quick-follow, back on his arena: no line, and out four ticks after the option, counted in game ticks.
                alice.commandReply("raid tp ezekiel") { it.startsWith("To ") }
                alice.tele(entry.x, entry.z + 1)
                val player = world.playerNamed("alice")!!
                alice.commandReply("pause") { it.contains("aused after tick") }
                alice.npcOp(navigator.index, 3)
                var waited = 0
                while (player.saveKey !in run.following) {
                    check(++waited <= 5) { "her Quick-follow not taken within 5 ticks" }
                    alice.stepTicks(1)
                }
                alice.stepTicks(ArchiveRaids.FOLLOW_DELAY - 1)
                assertEquals(ArchiveRoom.EZEKIEL, raids.roomAt(run, player.x, player.z), "out a tick early")
                alice.stepTicks(1)
                assertEquals(Tile(front.x, front.z), Tile(player.x, player.z))
                assertNull(alice.dialogueView as? DialogueView.Chat)
                alice.commandReply("resume") { it.startsWith("Resumed") }
            }
        }
    }

    @Test
    fun `with no Navigator in the cache, whoever is on his arena is sent out three ticks after his death`(@TempDir dir: Path) {
        server(dir, NpcTypes.of(ezekiel, sword, mark, grave)).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.commandReply("raid tp guardian") { it.startsWith("To ") }
                alice.await("the Guardian's room done") { ArchiveRoom.GUARDIAN.takeIf { it in run.cleared } }
                alice.intoArena(server, run)
                alice.await("his fight") { alice.fight().takeIf { it.startsWith("ezekiel #") } }
                val raids = server.world.archiveRaids
                val boss = bossOf(run)
                alice.commandReply("hit 3000 ${boss.index}") { it.startsWith("Hit ") }
                val front = raids.tile(run, ArchiveRoom.EZEKIEL.doorFront.x, ArchiveRoom.EZEKIEL.doorFront.z)
                alice.await("the nexus, in front of his room's door") { alice.position?.takeIf { it == Coord(front.x, front.z, 0) } }
                assertTrue(ArchiveRoom.EZEKIEL in run.cleared)
            }
        }
    }

    private fun lines(run: ArchiveRun): List<String> = assertNotNull(run.fights[ArchiveRoom.EZEKIEL]).lines.toList()

    private fun HeadlessClient.stepUntil(run: ArchiveRun, what: String, limit: Int, predicate: (String) -> Boolean): String {
        var ticks = 0
        while (true) {
            lines(run).lastOrNull(predicate)?.let { return it }
            check(++ticks <= limit) { "no $what within $limit ticks: ${lines(run).takeLast(10)}" }
            stepTicks(1)
        }
    }

    @Test
    fun `at 35 percent he enrages, the swords out go unflown, a fence shuts her in beside him, and a strike spares only the unstruck mark's tile`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.commandReply("raid tp guardian") { it.startsWith("To ") }
                alice.await("the Guardian's room done") { ArchiveRoom.GUARDIAN.takeIf { it in run.cleared } }
                alice.intoArena(server, run)
                alice.await("his fight") { alice.fight().takeIf { it.startsWith("ezekiel #") } }
                val boss = bossOf(run)
                val world = server.world
                // Paused with a volley out, then down to 34%.
                alice.toVolley()
                assertTrue(world.npcs.all.any { it.type.id == Ezekiel.SWORD })
                alice.commandReply("hit ${boss.maxHitpoints * 66 / 100} ${boss.index}") { it.startsWith("Hit ") }
                alice.stepUntil(run, "the enrage", 3) { it.contains("enrage: enrage at") }
                assertTrue(world.npcs.all.none { it.type.id == Ezekiel.SWORD }, "the volley's swords gone")
                // The arena, once the transition is over.
                alice.stepUntil(run, "the arena", Ezekiel.ENRAGE_TRANSITION + 2) { it.contains("enrage: arena at") }
                val spot = Tile(boss.spawn.x + Ezekiel.ENRAGE_DX, boss.spawn.z + Ezekiel.ENRAGE_DZ)
                assertEquals(spot, Tile(boss.x, boss.z))
                val arena = Ezekiel.arenaFor(spot.x, spot.z)
                val tiles = Ezekiel.arenaTiles(arena)
                val player = world.playerNamed("alice")!!
                assertTrue(Tile(player.x, player.z) in tiles, "alice in the arena at ${player.x},${player.z}")
                val fence = world.npcs.all.filter { it.type.id == Ezekiel.SWORD_NOOP }.map { Tile(it.x, it.z) }.toSet()
                assertEquals(Ezekiel.fence(arena).toSet() + (0 until 3).map { Ezekiel.mark(arena, it) }, fence)
                assertTrue((tiles + fence).all { Ezekiel.inArena(boss.spawn.x, boss.spawn.z, it.x, it.z) }, "the enrage on his arena")
                // The volley's flight passes: no sword hits anyone, nor heals him.
                alice.commandReply("setlevel hitpoints 99") { it.contains("set to level 99") }
                alice.commandReply("heal") { it.startsWith("Healed to ") }
                alice.commandReply("god off") { it.contains("can be hurt") }
                // A strike with her on its safe tile: nothing lands on her.
                val first = alice.stepUntil(run, "a strike", Ezekiel.ENRAGE_VOLLEY_EVERY) { it.contains("enrage: lightning, safe row") }
                val safe = Ezekiel.safeTile(arena, first.substringAfterLast(' ').toInt())
                world.teleport(player, safe.x, safe.z, 0)
                val before = lines(run).size
                alice.stepTicks(Ezekiel.ENRAGE_STRIKE_DELAY)
                assertTrue(lines(run).drop(before).none { it.contains("lightning hits alice") }, lines(run).drop(before).toString())
                // The next with her on another: it lands, Protect from Magic off.
                alice.stepTicks(1)
                val second = alice.stepUntil(run, "the next strike", Ezekiel.ENRAGE_VOLLEY_EVERY) { it.contains("enrage: lightning, safe row") && it != first }
                val unsafe = tiles.first { it != Ezekiel.safeTile(arena, second.substringAfterLast(' ').toInt()) }
                world.teleport(player, unsafe.x, unsafe.z, 0)
                val hpBefore = player.hitpoints
                alice.stepTicks(Ezekiel.ENRAGE_STRIKE_DELAY)
                val hit = lines(run).last { it.contains("lightning hits alice for") }
                val dealt = hit.substringAfterLast(' ').toInt()
                assertTrue(dealt in Ezekiel.ENRAGE_MIN..Ezekiel.ENRAGE_MAX, hit)
                assertEquals(hpBefore - dealt, player.hitpoints)
                val all = lines(run)
                assertTrue(all.none { it.contains("sword hits him") || it.contains("heals") }, all.toString())
                alice.commandReply("god on") { it.startsWith("You can't be hurt") }
                alice.commandReply("resume") { it.startsWith("Resumed") }
                // The fence holds her in: a walk west ends back in the arena.
                alice.walk(arena.x - 5, arena.z + 1)
                alice.awaitTicks(8)
                assertTrue(Tile(player.x, player.z) in tiles, "alice still in the arena at ${player.x},${player.z}")
                assertTrue(lines(run).any { it.endsWith("enrage: alice back into the arena") }, lines(run).takeLast(10).toString())
                // His death does the room, as before.
                alice.commandReply("hit 3000 ${boss.index}") { it.startsWith("Hit ") }
                alice.await("his room done") { ArchiveRoom.EZEKIEL.takeIf { it in run.cleared } }
            }
        }
    }
}
