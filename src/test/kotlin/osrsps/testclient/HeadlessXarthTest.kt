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
import osrsps.game.World
import osrsps.game.combat.Deaths
import osrsps.game.combat.NpcCombat
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.raid.ArchiveRaids
import osrsps.game.raid.Bork
import osrsps.game.raid.ArchiveRoom
import osrsps.game.raid.ArchiveRun
import osrsps.game.raid.PartyScaling
import osrsps.game.raid.Xarth
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import osrsps.map.Tile

/**
 * Xarth (raid step 19a) through the headless client: his fight beginning with the first of the party onto his arena,
 * him and his three heaps where the tsps capture notes have them, scaled to the party; his phase one's three cycles of
 * five fireballs, the AoE binding everyone, the flame blast and the wave; the wave's wedge towards the heap that falls,
 * all round the third time; the falling heap sheltering whoever hides behind it while the wave hits whoever doesn't;
 * the heap falling; phase two after the third wave; and the room done once he dies. The rl-client script
 * test-runner/examples/archive-xarth.json fights him in the real client.
 */
class HeadlessXarthTest {
    private val xarth = NpcType(
        Xarth.BOSS, "Xarth", size = 3, ops = listOf(null, "Attack", null, null, null), internalName = "tfa_xarth",
        stats = NpcStats(attack = 250, defence = 170, strength = 225, hitpoints = 2600, ranged = 220, magic = 350),
    )
    private val heap = NpcType(Xarth.HEAP, "Rubble", size = 2, internalName = "tfa_xarth_rubble_npc")
    private val heapDown = NpcType(Xarth.HEAP_DOWN, "Rubble", size = 2, internalName = "tfa_xarth_rubble_npc_destroyed")
    private val corpse = NpcType(
        Xarth.CORPSE, "Corpse", ops = listOf(null, "Attack", null, null, null), internalName = "tfa_xarth_corpse",
        stats = NpcStats(attack = 50, defence = 100, strength = 40, hitpoints = 62, ranged = 1, magic = 100),
    )
    private val grave = NpcType(Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null))
    private val types = NpcTypes.of(xarth, heap, heapDown, corpse, grave)

    private val locTypes = LocTypes(
        mapOf(
            ArchiveRaids.ARCHIVE_DOOR to LocType(ArchiveRaids.ARCHIVE_DOOR, "Archive Door", ops = listOf("Enter", null, null, null, null), collision = LocCollisionType(width = 9, length = 1)),
            Xarth.LOC_BLOCKING to LocType(Xarth.LOC_BLOCKING, collision = LocCollisionType(blocksProjectile = false)),
            Xarth.LOC_HEAP_BLOCKING to LocType(Xarth.LOC_HEAP_BLOCKING, collision = LocCollisionType(width = 2, length = 2, blocksProjectile = false)),
            // Guessed, as in Xarth.kt: a fire on the floor doesn't block the way.
            Xarth.LOC_ARENA_FIRE to LocType(Xarth.LOC_ARENA_FIRE, collision = LocCollisionType(interactType = 0, blocksProjectile = false)),
        ),
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
            encounterDefs = listOfNotNull(Xarth.def(types)), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    /** `::raid`'s line on the fight the player is in. */
    private fun HeadlessClient.fight(): String = commandReply("raid") { it.startsWith("xarth #") || it.startsWith("You're not") }

    private fun party(clients: List<Pair<HeadlessClient, String>>) {
        val (leader, leaderName) = clients.first()
        for ((client, name) in clients.drop(1)) {
            leader.commandReply("raidparty invite $name") { it.startsWith("Invited") }
            client.commandReply("raidparty accept $leaderName") { it.startsWith("You joined") }
        }
    }

    private fun HeadlessClient.intoRun(server: TestServer, username: String): ArchiveRun {
        await("the login rebuild") { position }
        commandReply("god on") { it.startsWith("You can't be hurt") }
        command("varp ${EquipmentTab.VARP_AUTO_RETALIATE_OFF} 1")
        await("auto retaliate off") { if (varp(EquipmentTab.VARP_AUTO_RETALIATE_OFF) == 1) Unit else null }
        tele(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z)
        locOp(ArchiveRaids.ARCHIVE_DOOR, 4123, 5562, ArchiveRaids.ENTER)
        await("into the instance") { position?.takeIf { server.world.instances.inDynamicArea(it.x, it.z) } }
        return assertNotNull(server.world.parties.of(server.world.playerNamed(username)!!)?.let(server.world.archiveRaids::of))
    }

    /** The rows before his done (no bosses in this world: each is done as it is walked into), and into his room. */
    private fun HeadlessClient.toXarthRoom(run: ArchiveRun) {
        for (room in listOf(ArchiveRoom.GUARDIAN, ArchiveRoom.BORK, ArchiveRoom.DREADNOTH)) {
            commandReply("raid tp ${room.name.lowercase()}") { it.startsWith("To ") }
            await("${room.boss}'s room done") { room.takeIf { it in run.cleared } }
        }
        commandReply("raid tp xarth") { it.startsWith("To ") }
    }

    /** Where his room's transporter puts a player: the west edge of his arena (this world has no transporter). */
    private fun HeadlessClient.ontoArena(server: TestServer, run: ArchiveRun) {
        val at = server.world.archiveRaids.tile(run, ArchiveRaids.XARTH_ARENA_X, ArchiveRaids.XARTH_ARENA_Z)
        tele(at.x, at.z)
    }

    private fun lines(run: ArchiveRun): List<String> = run.fights[ArchiveRoom.XARTH]?.lines?.toList().orEmpty()

    private fun HeadlessClient.awaitLine(run: ArchiveRun, what: String, predicate: (String) -> Boolean): String =
        try {
            await(what, timeoutMillis = 30_000) { lines(run).lastOrNull(predicate) }
        } catch (e: AssertionError) {
            throw AssertionError("${e.message}; the fight's last lines: ${lines(run).takeLast(12)}", e)
        }

    private fun tickOf(line: String): Long = line.substringBefore(' ').toLong()

    private fun boss(run: ArchiveRun): Npc = assertNotNull(run.fights[ArchiveRoom.XARTH]).boss

    @Test
    fun `the wave's wedge, the heap's shelter, the fall order and the burn`() {
        // Him on 100..102 x 100..102. South: a row out is his width plus a tile each side, a tile wider every row.
        assertTrue(Xarth.inWedge(100, 100, 3, Xarth.Side.SOUTH, 99, 99))
        assertTrue(Xarth.inWedge(100, 100, 3, Xarth.Side.SOUTH, 103, 99))
        assertFalse(Xarth.inWedge(100, 100, 3, Xarth.Side.SOUTH, 104, 99))
        assertTrue(Xarth.inWedge(100, 100, 3, Xarth.Side.SOUTH, 105, 97))
        assertFalse(Xarth.inWedge(100, 100, 3, Xarth.Side.SOUTH, 101, 100), "his own row")
        assertTrue(Xarth.inWedge(100, 100, 3, Xarth.Side.EAST, 103, 101))
        assertTrue(Xarth.inWedge(100, 100, 3, Xarth.Side.EAST, 105, 104))
        assertTrue(Xarth.inWedge(100, 100, 3, Xarth.Side.EAST, 105, 105), "3 out, 3 wider each side")
        assertFalse(Xarth.inWedge(100, 100, 3, Xarth.Side.EAST, 105, 106))
        assertTrue(Xarth.inWedge(100, 100, 3, Xarth.Side.NORTH, 101, 103))
        // Each heap lies in the wedge that points at it.
        for (heap in Xarth.Heap.entries) {
            assertTrue(Xarth.inWedge(100, 100, 3, heap.side, 100 + heap.dx, 100 + heap.dz), heap.name)
            assertTrue(Xarth.inArena(100, 100, 100 + heap.dx + 1, 100 + heap.dz + 1), heap.name)
        }
        // All round, the whole floor but his own tiles; a wedge, only its part of it.
        val all = Xarth.waveTiles(100, 100, null)
        assertTrue(all.none { it.x in 100..102 && it.z in 100..102 })
        assertTrue(Tile(94, 94) in all && Tile(108, 108) in all)
        assertFalse(Tile(94, 93) in all, "a corner")
        val south = Xarth.waveTiles(100, 100, Xarth.Side.SOUTH)
        assertTrue(south.all { it.z < 100 } && south.isNotEmpty())
        assertTrue(south.size < all.size / 2)
        // The south heap on 103..104 x 96..97: straight out from his middle past it, sheltered; beside it, not.
        val heapX = 100 + Xarth.Heap.SOUTH.dx
        val heapZ = 100 + Xarth.Heap.SOUTH.dz
        assertEquals(Xarth.Shelter.FULLY, Xarth.shelter(100, 100, heapX, heapZ, 105, 93))
        assertEquals(Xarth.Shelter.FULLY, Xarth.shelter(100, 100, heapX, heapZ, 103, 96), "on the heap")
        assertEquals(Xarth.Shelter.NONE, Xarth.shelter(100, 100, heapX, heapZ, 99, 94))
        assertEquals(Xarth.Shelter.NONE, Xarth.shelter(100, 100, heapX, heapZ, 103, 98), "between him and it")
        // Hidden from his nearest footprint tile but not from his middle: the smaller explosion.
        val partly = south.filter { Xarth.shelter(100, 100, heapX, heapZ, it.x, it.z) == Xarth.Shelter.PARTLY }
        assertTrue(partly.isNotEmpty())
        // The line test itself: a tile in the way, short of the end, blocks.
        assertTrue(Xarth.blocked(0, 0, 4, 0) { x, z -> x == 2 && z == 0 })
        assertFalse(Xarth.blocked(0, 0, 4, 0) { x, z -> x == 4 && z == 0 }, "the end tile itself")
        assertFalse(Xarth.blocked(0, 0, 4, 4) { x, z -> x == 2 && z == 0 })
        // South or north first, then east, then the other.
        assertEquals(listOf(Xarth.Heap.SOUTH, Xarth.Heap.EAST, Xarth.Heap.NORTH), Xarth.fallOrder(southFirst = true))
        assertEquals(listOf(Xarth.Heap.NORTH, Xarth.Heap.EAST, Xarth.Heap.SOUTH), Xarth.fallOrder(southFirst = false))
        // A burn hotter for each stack, up to its most.
        // One stack: Sam's clip's 7s and 8.
        assertEquals(7..8, Xarth.burnRange(1))
        assertEquals(10..11, Xarth.burnRange(2))
        assertEquals(13..14, Xarth.burnRange(3))
        assertEquals(13..14, Xarth.burnRange(9))
        // His melee and ranged max hits drop with his Strength or Ranged drained, as a drained NPC's max hit does.
        assertEquals(34, Xarth.drainedMax(34, 225, 225))
        assertEquals(20, Xarth.drainedMax(34, 225, 125))
        assertEquals(1, Xarth.drainedMax(34, 225, 0))
        // A Corpse heads for the tile beside him along his nearest side, never one off a corner.
        assertEquals(Tile(99, 101), Xarth.besideTile(100, 100, 95, 101))
        assertEquals(Tile(101, 103), Xarth.besideTile(100, 100, 101, 108))
        assertEquals(Tile(99, 100), Xarth.besideTile(100, 100, 92, 95))
        assertEquals(Tile(100, 99), Xarth.besideTile(100, 100, 97, 92))
        for (x in 90..112) for (z in 90..112) {
            if (x in 100..102 && z in 100..102) continue
            val to = Xarth.besideTile(100, 100, x, z)
            assertTrue(Bork.beside(100, 100, Xarth.SIZE, to.x, to.z), "from $x,$z to $to")
        }
    }

    @Test
    fun `his fight begins on his arena, and phase one is three cycles of fireballs, the AoE, the flame blast and a wave`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.toXarthRoom(run)
                // In at the way in, west of his arena: nothing yet.
                alice.awaitTicks(3)
                assertFalse(ArchiveRoom.XARTH in run.started)
                alice.ontoArena(server, run)
                val begun = alice.await("his fight") { alice.fight().takeIf { it.startsWith("xarth #") } }
                // A party of one counts as two: the Wiki's 2,600. Three heaps.
                assertTrue(begun.contains("Xarth 2600/2600 hp, 3 heaps standing, 0 waves, 0 fires, 0 corpses; 1 players, 3 adds"), begun)
                val world = server.world
                val at = world.archiveRaids.tile(run, ArchiveRaids.XARTH_X, ArchiveRaids.XARTH_Z)
                val him = boss(run)
                assertEquals(at, Tile(him.x, him.z))
                val heaps = assertNotNull(run.fights[ArchiveRoom.XARTH]).adds.filter { it.type.id == Xarth.HEAP }
                assertEquals(Xarth.Heap.entries.map { Tile(at.x + it.dx, at.z + it.dz) }.toSet(), heaps.map { Tile(it.x, it.z) }.toSet())
                // Five fireballs four ticks apart from tick 2, all at her, the only one there.
                alice.awaitLine(run, "the fifth fireball") { line -> line.contains(": fireball at alice") && lines(run).count { it.contains(": fireball at ") } >= 5 }
                val fireballs = lines(run).filter { it.contains(": fireball at ") }.map(::tickOf)
                assertEquals((0 until Xarth.MAGIC_ATTACKS).map { Xarth.FIRST_CYCLE + Xarth.MAGIC_GAP * it.toLong() }, fireballs.take(5))
                // The AoE binds her; the flame blast 3 ticks on, the wave 3 after that, and the first heap falls.
                val aoe = alice.awaitLine(run, "the AoE") { it.endsWith(": AoE binds alice") }
                assertEquals((Xarth.FIRST_CYCLE + Xarth.AOE_AT).toLong(), tickOf(aoe))
                assertTrue(world.playerNamed("alice")!!.stunnedUntil > 0, "bound")
                val blast = alice.awaitLine(run, "the flame blast") { it.endsWith(": flame blast") }
                assertEquals(Xarth.BLAST_AFTER_AOE.toLong(), tickOf(blast) - tickOf(aoe))
                val wave = alice.awaitLine(run, "the first wave") { it.contains(": wave 1 ") }
                assertEquals(Xarth.WAVE_AFTER_BLAST.toLong(), tickOf(wave) - tickOf(blast))
                val order = lines(run).first { it.contains("the heaps fall ") }.substringAfter("the heaps fall ").split(", ")
                assertTrue(order == listOf("south", "east", "north") || order == listOf("north", "east", "south"), order.toString())
                assertTrue(wave.endsWith("wave 1 ${order[0]}, ${order[0]} heap falling"), wave)
                alice.awaitLine(run, "the first heap down") { it.endsWith(": the ${order[0]} heap falls") }
                // The next cycle 31 ticks after the first; the third wave all round, then phase two.
                val third = alice.awaitLine(run, "the third wave") { it.contains(": wave 3 ") }
                assertTrue(third.endsWith("wave 3 all round, ${order[2]} heap falling"), third)
                assertEquals(2L * Xarth.CYCLE, tickOf(third) - tickOf(wave))
                val second = lines(run).first { it.contains(": wave 2 ") }
                assertTrue(second.endsWith("wave 2 east, east heap falling"), second)
                val two = alice.awaitLine(run, "phase two") { it.contains(": phase two: ") }
                assertEquals(1L, tickOf(two) - tickOf(third), "phase two the tick after the third wave")
                alice.await("every heap down") { assertNotNull(run.fights[ArchiveRoom.XARTH]).adds.takeIf { adds -> adds.none { it.type.id == Xarth.HEAP } } }
                // He dies: the room is done, and nothing of his fight is left.
                alice.commandReply("hit 2600 ${him.index}") { it.startsWith("Hit ") }
                alice.await("his room done") { ArchiveRoom.XARTH.takeIf { it in run.cleared } }
                assertNull(run.fights[ArchiveRoom.XARTH])
                assertTrue(world.npcs.all.none { it.type.id == Xarth.HEAP || it.type.id == Xarth.HEAP_DOWN || it.type.id == Xarth.CORPSE })
                // His body goes once his death animation is over (npc-combat.json's 6 ticks).
                alice.await("his body gone") { him.takeIf { world.npcs[it.index] !== it } }
            }
        }
    }

    @Test
    fun `in phase two the fires spread and burn, he shoots and swings, and Corpses that walk to him, heal him and set off the blast`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                alice.commandReply("god on") { it.startsWith("You can't be hurt") }
                // Auto retaliate off, or a shot hitting her walks her off the fire she stands on.
                alice.command("varp ${EquipmentTab.VARP_AUTO_RETALIATE_OFF} 1")
                alice.await("auto retaliate off") { if (alice.varp(EquipmentTab.VARP_AUTO_RETALIATE_OFF) == 1) Unit else null }
                val start = assertNotNull(alice.position)
                alice.commandReply("raid xarth phase=two seed=19") { it.startsWith("Started") }
                val world = server.world
                val fight = world.encounters.all.single()
                val him = fight.boss
                val lines = { fight.lines.toList() }
                fun awaitLine(what: String, predicate: (String) -> Boolean): String = try {
                    alice.await(what, timeoutMillis = 30_000) { lines().lastOrNull(predicate) }
                } catch (e: AssertionError) {
                    throw AssertionError("${e.message}; the fight's last lines: ${lines().takeLast(12)}", e)
                }
                assertEquals(Tile(start.x, start.z), Tile(him.x, him.z))
                // Out from under him, five tiles west of his footprint.
                alice.tele(him.x - 5, him.z + 1)
                // The first fires, each 1 to 7 tiles off his footprint, none under him.
                val opened = awaitLine("phase two") { it.contains(": phase two: ") }
                val fires = opened.substringAfter(" fires, on ").split(' ').map { s -> s.split(',').let { Tile(it[0].toInt(), it[1].toInt()) } }
                assertEquals(Xarth.TWO_FIRES, fires.size, opened)
                for (f in fires) assertTrue(Bork.distance(him.x, him.z, Xarth.SIZE, f.x, f.z) in Xarth.FIRE_OUT_MIN..Xarth.FIRE_OUT_MAX, "$f")
                assertTrue(fires.all { world.locs.at(it.x, it.z, him.level).any { l -> l.id == Xarth.LOC_ARENA_FIRE } }, "the fires' locs")
                // He's hurt, so a Corpse's heal shows.
                alice.commandReply("hit 1000 ${him.index}") { it.startsWith("Hit ") }
                // His first two shots, 4 ticks apart, at her (the only one there).
                awaitLine("the second shot") { lines().count { l -> l.contains(": shoots at alice") } >= 2 }
                val shots = lines().filter { it.contains(": shoots at alice") }.map(::tickOf)
                assertEquals(4L, shots[1] - shots[0])
                // The cycle: two Corpses raised 2 ticks apart, each rising 3 ticks on at an edge tile.
                val raised = awaitLine("a corpse raised") { it.contains(": raises a corpse at ") }
                assertEquals(Xarth.TWO_CYCLE_FROM.toLong() - Xarth.TWO_SHOTS_AT[0], tickOf(raised) - shots[0])
                val risen = awaitLine("a corpse risen") { it.contains(": a corpse rises at ") }
                assertEquals(Xarth.CORPSE_AFTER.toLong(), tickOf(risen) - tickOf(raised))
                assertEquals(raised.substringAfter(" at "), risen.substringAfter(" at "))
                val edge = risen.substringAfter(" at ").split(',').let { Tile(it[0].toInt(), it[1].toInt()) }
                assertTrue(Xarth.inArena(him.x, him.z, edge.x, edge.z))
                assertTrue(Xarth.Side.entries.any { !Xarth.inArena(him.x, him.z, edge.x + it.dx, edge.z + it.dz) }, "$edge on the edge")
                // They walk to him; the first absorbed heals him twice its 62, the second sets off the blast.
                val first = awaitLine("the first absorbed") { it.contains(": absorbs a corpse with 62 left, 1 absorbed") }
                assertTrue(lines().any { it.endsWith(": heals 124") && tickOf(it) == tickOf(first) }, lines().takeLast(8).toString())
                assertTrue(him.hitpoints >= 1600 + 124, "${him.hitpoints}")
                val second = awaitLine("the second absorbed") { it.contains(", 2 absorbed") }
                val blast = awaitLine("the flame blast") { it.endsWith(": flame blast") && tickOf(it) >= tickOf(second) }
                assertEquals(tickOf(second), tickOf(blast))
                val all = awaitLine("the blast") { it.endsWith(": the blast all round") }
                assertEquals(Xarth.WAVE_AFTER_BLAST.toLong(), tickOf(all) - tickOf(blast))
                assertTrue(lines().any { it.contains(": blast hits alice for 0") }, lines().takeLast(8).toString())
                assertTrue(lines().any { it.contains(": alice burns, ") && tickOf(it) == tickOf(all) }, "set alight")
                // The fires spread: 3 more, each next to one burning.
                val spread = awaitLine("the spread") { it.contains(": the fires spread to ") }
                val more = spread.substringAfter(" spread to ").split(' ').map { s -> s.split(',').let { Tile(it[0].toInt(), it[1].toInt()) } }
                assertEquals(Xarth.SPREAD_FIRES, more.size)
                assertTrue(alice.commandReply("raid") { it.startsWith("xarth #") }.contains("${Xarth.TWO_FIRES + Xarth.SPREAD_FIRES} fires"))
                // On a fire she's hurt and set alight.
                val fire = fires.first()
                alice.tele(fire.x, fire.z)
                val under = awaitLine("the fire under her") { it.contains(": fire under alice for 0") }
                // Still on it, the fire sets her alight again just as the burn's first tick is due: that tick still lands.
                val again = awaitLine("the fire under her again") { it.contains(": fire under alice for 0") && tickOf(it) > tickOf(under) }
                assertEquals(Xarth.FIRE_EVERY.toLong(), tickOf(again) - tickOf(under))
                // Once, never twice.
                assertEquals(1, lines().count { it.contains(": burn on alice for ") && tickOf(it) == tickOf(again) }, lines().takeLast(8).toString())
                // Beside him he swings instead of shooting.
                alice.tele(him.x - 1, him.z + 1)
                awaitLine("his swing") { it.contains(": swings at alice for 0") }
                // He dies: the Corpses, fires and his footprint go with his fight.
                alice.commandReply("hit 9000 ${him.index}") { it.startsWith("Hit ") }
                alice.await("his fight over") { world.encounters.all.isEmpty().takeIf { it } }
                assertTrue(world.npcs.all.none { it.type.id == Xarth.CORPSE })
                assertTrue(fires.none { world.locs.at(it.x, it.z, him.level).any { l -> l.id == Xarth.LOC_ARENA_FIRE } })
            }
        }
    }

    @Test
    fun `a Corpse frozen stands where it is, even beside him, and one killed is never absorbed`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                alice.commandReply("god on") { it.startsWith("You can't be hurt") }
                alice.commandReply("raid xarth phase=two seed=5") { it.startsWith("Started") }
                val world = server.world
                val fight = world.encounters.all.single()
                val him = fight.boss
                alice.tele(him.x - 5, him.z + 1)
                alice.await("a corpse", timeoutMillis = 30_000) { fight.adds.firstOrNull { it.type.id == Xarth.CORPSE } }
                val corpses = alice.await("two corpses", timeoutMillis = 30_000) { fight.adds.filter { it.type.id == Xarth.CORPSE }.takeIf { it.size >= 2 } }
                val (frozen, killed) = corpses.sortedBy { it.index }
                // Frozen, it doesn't move; killed, it dies where it stands and is never absorbed.
                assertTrue(world.npcs.freeze(frozen, 20))
                val at = Tile(frozen.x, frozen.z)
                alice.commandReply("hit 62 ${killed.index}") { it.startsWith("Hit ") }
                alice.awaitTicks(8)
                assertEquals(at, Tile(frozen.x, frozen.z))
                assertTrue(fight.lines.none { it.contains(": absorbs a corpse") }, fight.lines.takeLast(8).toString())
                // Frozen beside him it isn't absorbed (the Wiki's freeze a tile out); once it thaws it is.
                world.npcs.teleport(frozen, him.x - 1, him.z + 1)
                alice.awaitTicks(4)
                assertTrue(fight.lines.none { it.contains(": absorbs a corpse") }, fight.lines.takeLast(8).toString())
                alice.await("absorbed once it thaws", timeoutMillis = 30_000) { frozen.takeIf { world.npcs[it.index] !== it } }
                assertTrue(fight.lines.any { it.contains(": absorbs a corpse with 62 left") }, fight.lines.takeLast(8).toString())
                alice.await("the killed one gone") { killed.takeIf { world.npcs[it.index] !== it } }
            }
        }
    }

    @Test
    fun `the wave hits whoever stands in it and spares whoever hides behind the heap that falls`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    val clients = listOf(alice to "alice", bob to "bob")
                    for ((client, _) in clients) client.await("the login rebuild") { client.position }
                    party(clients)
                    val run = alice.intoRun(server, "alice")
                    assertEquals(run, bob.intoRun(server, "bob"))
                    alice.toXarthRoom(run)
                    alice.ontoArena(server, run)
                    alice.await("his fight") { alice.fight().takeIf { it.startsWith("xarth #") } }
                    bob.commandReply("raid tp xarth") { it.startsWith("To ") }
                    val him = boss(run)
                    // A party of two: the Wiki's 2,600 still.
                    assertEquals(2600, him.maxHitpoints)
                    val first = Xarth.Heap.valueOf(lines(run).first { it.contains("the heaps fall ") }.substringAfter("the heaps fall ").substringBefore(',').uppercase())
                    val heapX = him.x + first.dx
                    val heapZ = him.z + first.dz
                    val wedge = Xarth.waveTiles(him.x, him.z, first.side)
                    val hidden = wedge.first { Xarth.shelter(him.x, him.z, heapX, heapZ, it.x, it.z) == Xarth.Shelter.FULLY && !(it.x - heapX in 0..1 && it.z - heapZ in 0..1) }
                    val open = wedge.first { Xarth.shelter(him.x, him.z, heapX, heapZ, it.x, it.z) == Xarth.Shelter.NONE && Xarth.Heap.entries.none { h -> it.x - (him.x + h.dx) in 0..1 && it.z - (him.z + h.dz) in 0..1 } }
                    alice.tele(hidden.x, hidden.z)
                    bob.tele(open.x, open.z)
                    val wave = alice.awaitLine(run, "the first wave") { it.contains(": wave 1 ") }
                    val hits = lines(run).filter { it.contains(": wave hits ") && tickOf(it) == tickOf(wave) }
                    assertEquals(1, hits.size, lines(run).takeLast(12).toString())
                    val hit = hits.single()
                    assertTrue(hit.contains("wave hits bob for 0"), hit)
                    // AoE first: both bound in place, so where they stood is where it found them.
                    assertTrue(lines(run).any { it.endsWith(": AoE binds alice, bob") }, lines(run).toString())
                }
            }
        }
    }

    @Test
    fun `one of the party still at the room's arrival, behind the wall, is not in his fight`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    val clients = listOf(alice to "alice", bob to "bob")
                    for ((client, _) in clients) client.await("the login rebuild") { client.position }
                    party(clients)
                    val run = alice.intoRun(server, "alice")
                    assertEquals(run, bob.intoRun(server, "bob"))
                    alice.toXarthRoom(run)
                    bob.commandReply("raid tp xarth") { it.startsWith("To ") }
                    alice.ontoArena(server, run)
                    alice.await("his fight") { alice.fight().takeIf { it.startsWith("xarth #") } }
                    val him = boss(run)
                    val where = assertNotNull(bob.position)
                    assertFalse(Xarth.inArena(him.x, him.z, where.x, where.z), "bob at $where")
                    alice.awaitLine(run, "the AoE") { it.contains(": AoE binds ") }
                    // Only alice bound, and every fireball at her: bob is in the room but not on the arena.
                    assertTrue(lines(run).any { it.endsWith(": AoE binds alice") }, lines(run).toString())
                    assertTrue(lines(run).none { it.contains("bob") }, lines(run).toString())
                    assertFalse(server.world.playerNamed("bob")!!.stunnedUntil > 0, "bob bound")
                }
            }
        }
    }

    @Test
    fun `with too few NPC slots for his heaps his room isn't started, and his fight begins once there are`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.toXarthRoom(run)
                // Every free slot but two taken: Xarth and one heap fit, the other heaps don't.
                val npcs = server.world.npcs
                val filler = NpcType(1, "Filler")
                val fillers = generateSequence { npcs.add(NpcSpawn(filler, 1000, 1000, source = "test")) }.toList()
                assertTrue(fillers.size > 1000, "${fillers.size} fillers")
                npcs.remove(fillers[0])
                npcs.remove(fillers[1])
                alice.ontoArena(server, run)
                alice.awaitTicks(5)
                assertFalse(ArchiveRoom.XARTH in run.started, "his room started without his heaps")
                assertNull(run.fights[ArchiveRoom.XARTH])
                assertTrue(npcs.all.none { it.type.id == Xarth.BOSS || it.type.id == Xarth.HEAP }, "what did spawn, taken back")
                for (npc in fillers.drop(2)) npcs.remove(npc)
                val begun = alice.await("his fight") { alice.fight().takeIf { it.startsWith("xarth #") } }
                assertTrue(begun.contains("Xarth 2600/2600 hp, 3 heaps standing"), begun)
            }
        }
    }
}
