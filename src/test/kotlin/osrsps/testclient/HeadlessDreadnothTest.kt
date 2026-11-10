package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.EquipmentTab
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames
import osrsps.game.Skill
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
import osrsps.game.raid.Dreadnoth
import osrsps.game.raid.PartyScaling
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import osrsps.map.Tile

/**
 * Dreadnoth the Jellied (raid step 20a) through the headless client: his fight beginning with the first of the party onto
 * his arena, scaled to the party; his crawl a tile every two ticks after that player and his swing every four at
 * whoever is round him; his waves at 85%, 65% and 45%, one, two and three volleys six ticks apart, each a slam on
 * everyone and three shots at each player; the jellies rising where the shots landed, going for the nearest player and
 * draining their prayer as they hit; jelly on the floor where a jelly falls and by him at each volley, burning whoever
 * stands in it; and his death doing the room. The rl-client script test-runner/examples/archive-dreadnoth.json fights
 * him in the real client.
 */
class HeadlessDreadnothTest {
    private val dreadnoth = NpcType(
        Dreadnoth.BOSS, "Dreadnoth the Jellied", size = 5, ops = listOf(null, "Attack", null, null, null), internalName = "tfa_jelly_boss",
        stats = NpcStats(attack = 280, defence = 125, strength = 285, hitpoints = 2100, ranged = 250, magic = 220),
    )
    private val jelly = NpcType(
        Dreadnoth.JELLY, "Jelly", ops = listOf(null, "Attack", null, null, null), internalName = "tfa_jelly_minion",
        stats = NpcStats(attack = 200, defence = 100, strength = 160, hitpoints = 75, ranged = 1, magic = 100),
    )
    private val grave = NpcType(Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null))
    private val types = NpcTypes.of(dreadnoth, jelly, grave)

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
            encounterDefs = listOfNotNull(Dreadnoth.def(types)), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    /** `::raid`'s line on the fight the player is in. */
    private fun HeadlessClient.fight(): String = commandReply("raid") { it.startsWith("dreadnoth #") || it.startsWith("You're not") }

    /** [clients] into one party, the first leading. */
    private fun party(clients: List<Pair<HeadlessClient, String>>) {
        val (leader, leaderName) = clients.first()
        for ((client, name) in clients.drop(1)) {
            leader.commandReply("raidparty invite $name") { it.startsWith("Invited") }
            client.commandReply("raidparty accept $leaderName") { it.startsWith("You joined") }
        }
    }

    /** Into the run, under `::god`, from the lobby's Archive Door. */
    private fun HeadlessClient.intoRun(server: TestServer, username: String): ArchiveRun {
        await("the login rebuild") { position }
        commandReply("god on") { it.startsWith("You can't be hurt") }
        tele(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z)
        locOp(ArchiveRaids.ARCHIVE_DOOR, 4123, 5562, ArchiveRaids.ENTER)
        await("into the instance") { position?.takeIf { server.world.instances.inDynamicArea(it.x, it.z) } }
        return assertNotNull(server.world.parties.of(server.world.playerNamed(username)!!)?.let(server.world.archiveRaids::of))
    }

    /** Through the Guardian's hall and Bork's room (both done as they're walked into, neither built in this world). */
    private fun HeadlessClient.toThirdRow(run: ArchiveRun) {
        commandReply("raid tp guardian") { it.startsWith("To ") }
        await("the Guardian's room done") { ArchiveRoom.GUARDIAN.takeIf { it in run.cleared } }
        commandReply("raid tp bork") { it.startsWith("To ") }
        await("Bork's room done") { ArchiveRoom.BORK.takeIf { it in run.cleared } }
    }

    /**
     * Where his room's transporter puts a player: the west edge of his arena (this world has no transporter loc). With
     * auto retaliate off, so they stay where they're put.
     */
    private fun HeadlessClient.ontoArena(server: TestServer, run: ArchiveRun) {
        command("varp ${EquipmentTab.VARP_AUTO_RETALIATE_OFF} 1")
        await("auto retaliate off") { if (varp(EquipmentTab.VARP_AUTO_RETALIATE_OFF) == 1) Unit else null }
        commandReply("raid tp dreadnoth") { it.startsWith("To ") }
        val at = server.world.archiveRaids.tile(run, Dreadnoth.ARENA_ENTRY_X, Dreadnoth.ARENA_ENTRY_Z)
        tele(at.x, at.z)
    }

    private fun lines(run: ArchiveRun): List<String> = run.fights[ArchiveRoom.DREADNOTH]?.lines?.toList().orEmpty()

    private fun HeadlessClient.awaitLine(run: ArchiveRun, what: String, predicate: (String) -> Boolean): String =
        try {
            await(what) { lines(run).lastOrNull(predicate) }
        } catch (e: AssertionError) {
            throw AssertionError("${e.message}; the fight's last lines: ${lines(run).takeLast(16)}", e)
        }

    private fun tickOf(line: String): Long = line.substringBefore(' ').toLong()

    private fun boss(run: ArchiveRun): Npc = assertNotNull(run.fights[ArchiveRoom.DREADNOTH]).boss

    private fun jellies(run: ArchiveRun): List<Npc> = assertNotNull(run.fights[ArchiveRoom.DREADNOTH]).adds.filter { it.type.id == Dreadnoth.JELLY }

    /** Hits him down to [percent] of his hitpoints. */
    private fun HeadlessClient.hitTo(run: ArchiveRun, percent: Int) {
        val him = boss(run)
        val damage = him.hitpoints - him.maxHitpoints * percent / 100
        commandReply("hit $damage ${him.index}") { it.startsWith("Hit ") }
    }

    @Test
    fun `his reach is round him and under him, and his arena is the tsps notes'`() {
        // Him on 10..14.
        assertTrue(Dreadnoth.inReach(10, 10, 5, 9, 9), "off his corner")
        assertTrue(Dreadnoth.inReach(10, 10, 5, 12, 12), "under him")
        assertTrue(Dreadnoth.inReach(10, 10, 5, 15, 14))
        assertFalse(Dreadnoth.inReach(10, 10, 5, 16, 12))
        assertFalse(Dreadnoth.inReach(10, 10, 5, 8, 10))
        // 4181-4198 x 5656-5670 round his 4189,5661.
        assertTrue(Dreadnoth.inArena(Dreadnoth.X, Dreadnoth.Z, 4181, 5656))
        assertTrue(Dreadnoth.inArena(Dreadnoth.X, Dreadnoth.Z, 4198, 5670))
        assertTrue(Dreadnoth.inArena(Dreadnoth.X, Dreadnoth.Z, Dreadnoth.ARENA_ENTRY_X, Dreadnoth.ARENA_ENTRY_Z))
        assertFalse(Dreadnoth.inArena(Dreadnoth.X, Dreadnoth.Z, 4180, 5663), "the way in, past the transporter")
        assertFalse(Dreadnoth.inArena(Dreadnoth.X, Dreadnoth.Z, 4190, 5671))
        assertFalse(Dreadnoth.inArena(Dreadnoth.X, Dreadnoth.Z, 4199, 5660))
        // Shifted with him, as in a run's instance.
        assertTrue(Dreadnoth.inArena(Dreadnoth.X + 1000, Dreadnoth.Z + 64, 4181 + 1000, 5656 + 64))
    }

    @Test
    fun `his fight begins with the first onto his arena, he crawls a tile every two ticks to her and swings every four, and his death does the room`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.toThirdRow(run)
                // At the way into his room nothing begins: his arena is past the room's transporter.
                alice.commandReply("raid tp dreadnoth") { it.startsWith("To ") }
                alice.awaitTicks(3)
                assertFalse(ArchiveRoom.DREADNOTH in run.started, "his fight begun at the way in")
                alice.ontoArena(server, run)
                val begun = alice.await("his fight") { alice.fight().takeIf { it.startsWith("dreadnoth #") } }
                // A party of one counts as two: the Wiki's 2,100.
                assertTrue(begun.contains("Dreadnoth the Jellied 2100/2100 hp, 0 jellies, 0 tiles of jelly; 1 players"), begun)
                assertTrue(ArchiveRoom.DREADNOTH in run.started && ArchiveRoom.DREADNOTH !in run.cleared)
                val him = boss(run)
                val at = server.world.archiveRaids.tile(run, Dreadnoth.X, Dreadnoth.Z)
                assertEquals(at, Tile(him.spawn.x, him.spawn.z))
                // She, the first in, is his target: four tiles off, he crawls a tile every two ticks, then swings at her
                // every four.
                assertTrue(lines(run).any { it.endsWith("fight: takes on alice") })
                val first = alice.awaitLine(run, "his first swing") { it.endsWith("fight: swings at alice") }
                val crawls = lines(run).filter { it.contains("fight: crawls to ") }.map(::tickOf)
                assertEquals(4, crawls.size, "${lines(run)}; him from ${him.spawn.x},${him.spawn.z}, alice at ${alice.position}")
                assertEquals(crawls.indices.map { crawls.first() + Dreadnoth.CRAWL_EVERY * it }, crawls)
                assertTrue(tickOf(first) >= crawls.last(), first)
                alice.awaitLine(run, "his second swing") { it.endsWith("fight: swings at alice") && tickOf(it) > tickOf(first) }
                val swings = lines(run).filter { it.endsWith("fight: swings at alice") }.map(::tickOf)
                assertEquals(Dreadnoth.ATTACK_SPEED.toLong(), swings[1] - swings[0])
                // No wave yet, nor any jelly.
                assertTrue(lines(run).none { it.contains("wave") }, lines(run).toString())

                alice.commandReply("hit 2100 ${him.index}") { it.startsWith("Hit ") }
                alice.await("his room done") { ArchiveRoom.DREADNOTH.takeIf { it in run.cleared } }
                assertNull(run.fights[ArchiveRoom.DREADNOTH])
            }
        }
    }

    @Test
    fun `at 85 percent a wave's volley slams her and shoots her three times, a jelly rises where they landed and drains her prayer, and jelly on the floor burns`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.toThirdRow(run)
                alice.commandReply("setlevel prayer 99") { it.contains("set to level 99") }
                alice.ontoArena(server, run)
                alice.await("his fight") { alice.fight().takeIf { it.startsWith("dreadnoth #") } }
                val world = server.world
                alice.hitTo(run, 85)
                val wave = alice.awaitLine(run, "the first wave") { it.contains("fight: wave 1 at ") }
                assertTrue(wave.endsWith(": 1 volleys"), wave)
                // The jump now, then a tick later the slam, the jelly by him and the shots.
                assertTrue(lines(run).any { it.endsWith("fight: jumps") && tickOf(it) == tickOf(wave) })
                val shoots = alice.awaitLine(run, "the shots at her") { it.contains("fight: shoots at alice on ") }
                assertEquals(Dreadnoth.VOLLEY_LANDS.toLong(), tickOf(shoots) - tickOf(wave))
                assertTrue(lines(run).any { it.contains("fight: slam hits alice for ") && tickOf(it) == tickOf(shoots) })
                val by = lines(run).first { it.contains("fight: jelly by him on ") }
                assertEquals(Dreadnoth.VOLLEY_TILES, by.substringAfter(" on ").split(' ').size, by)
                // The three shots land 3, 4 and 5 ticks after they're thrown; 8 after, a jelly rises on her tile then.
                val rise = alice.awaitLine(run, "the jellies rising") { it.endsWith("jellies rise") }
                val shots = lines(run).filter { it.contains("fight: shot hits alice for ") }.map { tickOf(it) - tickOf(shoots) }
                assertEquals(Dreadnoth.SHOT_LANDS.map(Int::toLong), shots)
                assertEquals(Dreadnoth.JELLIES_AFTER.toLong(), tickOf(rise) - tickOf(shoots))
                assertTrue(rise.endsWith("fight: 1 jellies rise"), rise)
                val aimed = shoots.substringAfter(" on ").split(',').map(String::toInt).let { (x, z) -> Tile(x, z) }
                val theJelly = jellies(run).single()
                assertEquals(aimed, Tile(theJelly.spawn.x, theJelly.spawn.z))
                // She stands still: it comes out from under her and hits her, draining her prayer by its roll.
                val hit = alice.awaitLine(run, "the jelly hitting her") { it.contains("fight: a jelly hits alice for ") }
                val drained = hit.substringAfter("draining ").substringBefore(' ').toInt()
                assertTrue(drained >= 1, hit)
                assertTrue(world.playerNamed("alice")!!.skills.currentLevel(Skill.PRAYER) < 99)
                // It falls: jelly on its tile, burning her when she stands in it.
                alice.commandReply("hit 75 ${theJelly.index}") { it.startsWith("Hit ") }
                val falls = alice.awaitLine(run, "the jelly falling") { it.contains("fight: a jelly falls at ") }
                val fell = falls.substringAfter(" at ").split(',').map(String::toInt).let { (x, z) -> Tile(x, z) }
                alice.tele(fell.x, fell.z)
                val burnt = alice.awaitLine(run, "jelly burning her") { it.contains("fight: jelly burns alice for ") }
                assertTrue(burnt.substringAfter(" for ").toInt() == 0, "::god: $burnt")
                val floor = by.substringAfter(" on ").split(' ').map { it.split(',').map(String::toInt).let { (x, z) -> Tile(x, z) } }.toSet() + fell
                assertTrue(alice.fight().contains("0 jellies, ${floor.size} tiles of jelly"))
            }
        }
    }

    @Test
    fun `the third wave is three volleys six ticks apart, each shooting both of them, and he stands still through it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    val clients = listOf(alice to "alice", bob to "bob")
                    for ((client, _) in clients) client.await("the login rebuild") { client.position }
                    party(clients)
                    val run = alice.intoRun(server, "alice")
                    assertEquals(run, bob.intoRun(server, "bob"))
                    alice.toThirdRow(run)
                    alice.ontoArena(server, run)
                    bob.ontoArena(server, run)
                    alice.await("his fight") { alice.fight().takeIf { it.startsWith("dreadnoth #") } }
                    // A party of two: the Wiki's 2,100 still.
                    assertEquals(2100, boss(run).maxHitpoints)
                    alice.hitTo(run, 85)
                    alice.awaitLine(run, "the first wave's jellies") { it.endsWith("jellies rise") }
                    alice.hitTo(run, 65)
                    val second = alice.awaitLine(run, "the second wave") { it.contains("fight: wave 2 at ") }
                    assertTrue(second.endsWith(": 2 volleys"), second)
                    alice.awaitLine(run, "the second wave's jellies") { it.endsWith("jellies rise") && tickOf(it) > tickOf(second) }
                    alice.hitTo(run, 45)
                    val third = alice.awaitLine(run, "the third wave") { it.contains("fight: wave 3 at ") }
                    val rise = alice.awaitLine(run, "the third wave's jellies") { it.endsWith("jellies rise") && tickOf(it) > tickOf(third) }
                    val jumps = lines(run).filter { it.endsWith("fight: jumps") && tickOf(it) >= tickOf(third) }.map(::tickOf)
                    assertEquals((0 until 3).map { tickOf(third) + Dreadnoth.VOLLEY_GAP * it }, jumps)
                    for (name in listOf("alice", "bob")) {
                        val shot = lines(run).filter { it.contains("fight: shoots at $name on ") && tickOf(it) > tickOf(third) }.map(::tickOf)
                        assertEquals(jumps.map { it + Dreadnoth.VOLLEY_LANDS }, shot, name)
                    }
                    // He neither crawls nor swings through it; the jellies rise 8 ticks after the last volley's shots.
                    assertTrue(lines(run).none { tickOf(it) in tickOf(third) until jumps.last() + Dreadnoth.VOLLEY_LANDS && (it.contains("crawls to") || it.contains("swings at")) }, lines(run).toString())
                    assertEquals(jumps.last() + Dreadnoth.VOLLEY_LANDS + Dreadnoth.JELLIES_AFTER, tickOf(rise))
                    assertTrue(jellies(run).size <= Dreadnoth.MAX_JELLIES)
                    // No more waves after the third.
                    alice.hitTo(run, 30)
                    alice.awaitTicks(10)
                    assertTrue(lines(run).none { it.contains("fight: wave 4") }, lines(run).toString())
                }
            }
        }
    }

    @Test
    fun `an enrage jump lands centred on a player within five tiles, else five tiles towards them, never west of the arena's x 4184`() {
        val x = Dreadnoth.X
        val z = Dreadnoth.Z
        // His middle 4191,5663: a player 3 tiles off, landed on.
        assertEquals(Tile(4192, 5664), Dreadnoth.landingFor(x, z, 5, 4194, 5666, x, z))
        // 8 tiles north: 5 tiles that way.
        assertEquals(Tile(4189, 5666), Dreadnoth.landingFor(x, z, 5, 4191, 5671, x, z))
        // From the arena's east edge, a player far to the west and a little south: due west 5, kept to x 4184 or east.
        val east = Tile(4194, 5661)
        assertEquals(Tile(4189, 5661), Dreadnoth.landingFor(east.x, east.z, 5, 4180, 5662, x, z))
        // A diagonal: south-west, kept on the arena.
        assertEquals(Tile(4189, 5656), Dreadnoth.landingFor(east.x, east.z, 5, 4185, 5652, x, z))
        // Never west of 4184, nor past the arena's other edges.
        assertEquals(4184, Dreadnoth.landingFor(4186, z, 5, 4181, 5663, x, z).x)
        val far = Dreadnoth.landingFor(x, z, 5, 4198, 5670, x, z)
        assertTrue(far.x + 4 <= Dreadnoth.ARENA_MAX_X && far.z + 4 <= Dreadnoth.ARENA_MAX_Z, far.toString())
    }

    @Test
    fun `at 25 percent he enrages, jumps at her 5 ticks on and every 8 after, absorbs the jelly he lands on and heals off her`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.toThirdRow(run)
                alice.commandReply("setlevel hitpoints 99") { it.contains("set to level 99") }
                alice.ontoArena(server, run)
                alice.await("his fight") { alice.fight().takeIf { it.startsWith("dreadnoth #") } }
                // Just north of him, so a landing centred on her stays on his arena.
                val spot = server.world.archiveRaids.tile(run, 4192, 5667)
                alice.tele(spot.x, spot.z)
                // A jelly first, risen on her tile and come out beside her.
                alice.hitTo(run, 85)
                alice.awaitLine(run, "the jelly rising") { it.endsWith("fight: 1 jellies rise") }
                alice.awaitLine(run, "the jelly hitting her") { it.contains("fight: a jelly hits alice for ") }
                val theJelly = jellies(run).single()
                alice.hitTo(run, 25)
                val enrage = alice.awaitLine(run, "his enrage") { it.contains("enrage: enrage at ") }
                // His first jump 5 ticks on, at her (the only one), landing centred on her 3 ticks after.
                val jump = alice.awaitLine(run, "his first jump") { it.contains("enrage: jumps at alice, landing on ") }
                assertEquals(Dreadnoth.FIRST_JUMP.toLong(), tickOf(jump) - tickOf(enrage))
                val her = assertNotNull(alice.position)
                assertTrue(jump.endsWith("landing on ${her.x - 2},${her.z - 2}"), "$jump; alice at $her")
                // She steps out of ::god just before he lands: the slam is dealt, and he heals by it.
                alice.commandReply("god off") { it.startsWith("You can be hurt") }
                val crushed = alice.awaitLine(run, "his landing on her") { it.contains("enrage: lands on alice for ") }
                alice.commandReply("god on") { it.startsWith("You can't be hurt") }
                assertEquals(Dreadnoth.JUMP_LANDS.toLong(), tickOf(crushed) - tickOf(jump))
                val dealt = crushed.substringAfter(" for ").substringBefore(',').toInt()
                assertTrue(dealt in Dreadnoth.CRUSH_MIN..Dreadnoth.CRUSH_MAX, crushed)
                assertTrue(lines(run).any { it.contains("enrage: heals off alice: heals $dealt") }, lines(run).toString())
                // The jelly beside her was under him: absorbed, healing him by what it had left.
                assertTrue(lines(run).any { it.contains("enrage: absorbs a jelly: heals ${theJelly.maxHitpoints}") }, lines(run).toString())
                assertTrue(jellies(run).isEmpty())
                // The shot and the splatter at her; the splatter lies as jelly on her tile 6 ticks on.
                val splatter = alice.awaitLine(run, "the splatter") { it.contains("enrage: splatter lands on ${her.x},${her.z}") }
                assertEquals(Dreadnoth.SPLATTER_LANDS.toLong(), tickOf(splatter) - tickOf(crushed))
                // Every 8 ticks after; no swings in the enrage.
                alice.awaitLine(run, "his second jump") { it.contains("enrage: jumps at alice") && tickOf(it) > tickOf(jump) }
                val jumps = lines(run).filter { it.contains("enrage: jumps at alice") }.map(::tickOf)
                assertEquals(Dreadnoth.JUMP_EVERY.toLong(), jumps[1] - jumps[0])
                assertTrue(lines(run).none { it.contains("enrage: swings at") }, lines(run).toString())
            }
        }
    }
}
