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
import osrsps.game.raid.ArchiveRoom
import osrsps.game.raid.ArchiveRun
import osrsps.game.raid.Bork
import osrsps.game.raid.PartyScaling
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import osrsps.map.Tile

/**
 * Bork and the Smorks (raid steps 18a and 18b) through the headless client: their fight beginning with the first of the
 * party into their room, both scaled to the party, Bork taking that player on and beating his gong; the Smorks firing
 * at everyone Bork isn't after; their napalm leaving oil that burns; the heavy cannonball lured onto Bork dazing him,
 * his armour gone; his stomp sparing the tower's shadow and naming his next tank; Bork's flat armour rising with the
 * Smorks' damage and taking that off hits; and the room done only once both are dead, whichever goes first. The
 * rl-client script test-runner/examples/archive-bork.json fights them in the real client.
 */
class HeadlessBorkTest {
    private val bork = NpcType(
        Bork.BORK, "Bork", size = 4, ops = listOf(null, "Attack", null, null, null), internalName = "tfa_bork",
        stats = NpcStats(attack = 200, defence = 300, strength = 200, hitpoints = 1500, ranged = 200, magic = 90),
    )
    private val smorks = NpcType(
        Bork.SMORKS, "Smorks", size = 3, ops = listOf(null, "Attack", null, null, null), internalName = "tfa_smork",
        stats = NpcStats(attack = 1, defence = 220, strength = 1, hitpoints = 600, ranged = 185, magic = 185),
    )
    private val anchor = NpcType(Bork.ANCHOR, "Bork", internalName = "tfa_bork_smork_hp_ui_handler")
    private val grave = NpcType(Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null))
    private val types = NpcTypes.of(bork, smorks, anchor, grave)

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
            encounterDefs = listOfNotNull(Bork.def(types)), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    /** `::raid`'s line on the fight the player is in. */
    private fun HeadlessClient.fight(): String = commandReply("raid") { it.startsWith("bork #") || it.startsWith("You're not") }

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

    /** Through the Guardian's hall (done at once) to Bork's room and onto his arena's floor, his fight begun. */
    private fun HeadlessClient.toBork(server: TestServer, run: ArchiveRun) {
        commandReply("raid tp guardian") { it.startsWith("To ") }
        await("the Guardian's room done") { ArchiveRoom.GUARDIAN.takeIf { it in run.cleared } }
        commandReply("raid tp bork") { it.startsWith("To ") }
        ontoFloor(server, run)
        await("their fight") { fight().takeIf { it.startsWith("bork #") } }
    }

    /** Where their room's transporter puts a player: the west edge of the arena's floor (this world has no transporter). */
    private fun HeadlessClient.ontoFloor(server: TestServer, run: ArchiveRun) {
        val at = server.world.archiveRaids.tile(run, ArchiveRaids.BORK_ARENA_X, ArchiveRaids.BORK_ARENA_Z)
        tele(at.x, at.z)
    }

    private fun lines(run: ArchiveRun): List<String> = run.fights[ArchiveRoom.BORK]?.lines?.toList().orEmpty()

    private fun HeadlessClient.awaitLine(run: ArchiveRun, what: String, predicate: (String) -> Boolean): String =
        try {
            await(what) { lines(run).lastOrNull(predicate) }
        } catch (e: AssertionError) {
            throw AssertionError("${e.message}; the fight's last lines: ${lines(run).takeLast(12)}", e)
        }

    private fun tickOf(line: String): Long = line.substringBefore(' ').toLong()

    private fun npc(run: ArchiveRun, type: Int): Npc = assertNotNull(run.fights[ArchiveRoom.BORK]).adds.first { it.type.id == type }

    @Test
    fun `his flat armour rises with the Smorks' damage, beside and near are his reach, and the tower shades whoever is behind it`() {
        assertEquals(8 to 0, Bork.armourFor(600, 600))
        assertEquals(8 to 0, Bork.armourFor(451, 600), "24.8% lost")
        assertEquals(10 to 2, Bork.armourFor(450, 600), "25% lost")
        assertEquals(12 to 4, Bork.armourFor(300, 600))
        assertEquals(14 to 6, Bork.armourFor(150, 600))
        assertEquals(14 to 6, Bork.armourFor(0, 600), "the Smorks dead: the most of both")
        // Bork on 10..13.
        assertTrue(Bork.beside(10, 10, 4, 9, 12))
        assertTrue(Bork.beside(10, 10, 4, 12, 14))
        assertFalse(Bork.beside(10, 10, 4, 9, 9), "off his corner")
        assertTrue(Bork.near(10, 10, 4, 9, 9, 1), "a beat reaches his corners")
        assertTrue(Bork.near(10, 10, 4, 11, 11, 1), "and under him")
        assertFalse(Bork.near(10, 10, 4, 15, 12, 1))
        assertTrue(Bork.near(10, 10, 4, 16, 16, 3))
        assertFalse(Bork.near(10, 10, 4, 17, 12, 3))
        assertEquals(0, Bork.distance(10, 10, 4, 13, 13))
        assertEquals(1, Bork.distance(10, 10, 4, 9, 14))
        assertEquals(3, Bork.distance(10, 10, 4, 16, 11))
        // The tower on 15..17 x 10..12, east of him: whoever is east of it is in its shadow; north-east of him isn't.
        assertTrue(Bork.shadowed(15, 10, 3, 10, 10, 4, 19, 11))
        assertTrue(Bork.shadowed(15, 10, 3, 10, 10, 4, 22, 11))
        assertFalse(Bork.shadowed(15, 10, 3, 10, 10, 4, 16, 15))
        assertFalse(Bork.shadowed(15, 10, 3, 10, 10, 4, 14, 11), "between him and the tower")
        assertFalse(Bork.shadowed(15, 10, 3, 10, 10, 4, 16, 11), "on the tower itself")
        // Their room's floor: 15 by 17 round the tower's, without its corners.
        assertTrue(Bork.inArena(100, 100, 94, 94))
        assertFalse(Bork.inArena(100, 100, 94, 93), "a corner")
        assertFalse(Bork.inArena(100, 100, 108, 109), "a corner")
        assertTrue(Bork.inArena(100, 100, 108, 108))
        assertFalse(Bork.inArena(100, 100, 109, 100))
    }

    @Test
    fun `napalm splashes towards the tower, and their cycles are a volley, napalm and volleys before the heavy cannonball`() {
        // The tower on 100..102: straight west of it, the tile and the 3 wide row two deep beyond it.
        assertEquals(setOf(Tile(95, 101)) + (96..97).flatMap { x -> (100..102).map { Tile(x, it) } }, Bork.splash(100, 100, 3, 95, 101))
        // Straight south.
        assertEquals(setOf(Tile(101, 95)) + (96..97).flatMap { z -> (100..102).map { Tile(it, z) } }, Bork.splash(100, 100, 3, 101, 95))
        // On a diagonal: the 2 by 2 beyond.
        assertEquals(setOf(Tile(97, 97), Tile(98, 98), Tile(98, 99), Tile(99, 98), Tile(99, 99)), Bork.splash(100, 100, 3, 97, 97))
        // Right beside the tower: never on it.
        assertEquals(setOf(Tile(99, 101)), Bork.splash(100, 100, 3, 99, 101))
        val v = Bork.Shot.VOLLEY
        val n = Bork.Shot.NAPALM
        val c = Bork.Shot.CANNONBALL
        assertEquals(listOf(v, n, v, c), Bork.firstCycle(1))
        assertEquals(listOf(v, n, v, v, c), Bork.firstCycle(2))
        assertEquals(listOf(v, v, n, v, v, c), Bork.laterCycle(2))
        assertEquals(listOf(v, v, v, n, v, c), Bork.laterCycle(3))
        // The frenzy: two or three volleys and napalm, then five and napalm over and over; beside the tower, the splash
        // runs away from it.
        assertEquals(listOf(v, v, n), Bork.frenzyStart(2))
        assertEquals(listOf(v, v, v, v, v, n), Bork.frenzyRound())
        assertEquals(setOf(Tile(99, 101)) + (97..98).flatMap { x -> (100..102).map { Tile(x, it) } }, Bork.splash(100, 100, 3, 99, 101, away = true))
        // The tempo: a stage for every 25% the Smorks have lost, up to 3.
        assertEquals(0, Bork.tempoFor(600, 600))
        assertEquals(0, Bork.tempoFor(451, 600))
        assertEquals(1, Bork.tempoFor(450, 600))
        assertEquals(2, Bork.tempoFor(300, 600))
        assertEquals(3, Bork.tempoFor(150, 600))
        assertEquals(3, Bork.tempoFor(0, 600))
    }

    @Test
    fun `their fight begins with the first into their room, Bork beats at her, and the room is done once both are dead`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.commandReply("raid tp guardian") { it.startsWith("To ") }
                alice.await("the Guardian's room done") { ArchiveRoom.GUARDIAN.takeIf { it in run.cleared } }
                alice.commandReply("raid tp bork") { it.startsWith("To ") }
                // In at the way in, out of Bork's reach past the narrow gap in his arena's wall: nothing yet.
                alice.awaitTicks(3)
                assertFalse(ArchiveRoom.BORK in run.started)
                alice.ontoFloor(server, run)
                val begun = alice.await("their fight") { alice.fight().takeIf { it.startsWith("bork #") } }
                // A party of one counts as two: the Wiki's 1,500 and 600.
                assertTrue(begun.contains("Bork 1500/1500 hp, the Smorks 600/600 hp; 1 players, 2 adds"), begun)
                assertTrue(ArchiveRoom.BORK in run.started && ArchiveRoom.BORK !in run.cleared)
                val world = server.world
                val cart = world.archiveRaids.tile(run, ArchiveRaids.BORK_CART_X, ArchiveRaids.BORK_CART_Z)
                val theSmorks = npc(run, Bork.SMORKS)
                val theBork = npc(run, Bork.BORK)
                assertEquals(cart, Tile(theSmorks.spawn.x, theSmorks.spawn.z))
                assertEquals(Tile(cart.x + Bork.BORK_EAST, cart.z + Bork.BORK_NORTH), Tile(theBork.spawn.x, theBork.spawn.z))
                // She, the first in, is his tank: he walks to her and beats.
                alice.awaitLine(run, "Bork beating at alice") { it.contains("beat hits alice for") }
                assertSame(world.playerNamed("alice"), theBork.target)
                assertTrue(lines(run).any { it.endsWith("fight: Bork takes on alice") })
                assertTrue(lines(run).any { it.endsWith("fight: Bork's armour 8, magic 0") })
                // Alone, she's his: the Smorks fire at no one.
                assertTrue(lines(run).none { it.contains("the Smorks fire") }, lines(run).toString())
                // A quarter off the Smorks: two more armour, and magic resistance, off each hit on him. By name, from
                // further off than ::hit's search reaches, back at the way in: it finds them in the fight she's in.
                alice.commandReply("raid tp bork") { it.startsWith("To ") }
                assertTrue(maxOf(kotlin.math.abs(world.playerNamed("alice")!!.x - theSmorks.x), kotlin.math.abs(world.playerNamed("alice")!!.z - theSmorks.z)) > 15)
                alice.commandReply("hit 150 tfa_smork") { it.startsWith("Hit ") }
                alice.awaitLine(run, "his armour up") { it.endsWith("fight: Bork's armour 10, magic 2") }
                assertEquals(10, world.meleeCombat.armoured(theBork, 20, magic = false))
                assertEquals(18, world.meleeCombat.armoured(theBork, 20, magic = true))
                assertEquals(0, world.meleeCombat.armoured(theBork, 7, magic = false), "not below 0")
                assertEquals(0, world.meleeCombat.armoured(theSmorks, 0, magic = false))
                assertEquals(20, world.meleeCombat.armoured(theSmorks, 20, magic = false), "the Smorks have none")
                // Bork dead first, by her own swing at his last hitpoint: the armour comes off as it's rolled, before
                // what he has left caps it, so an armoured hit can still finish him. The Smorks fight on, and the room
                // isn't done.
                alice.commandReply("hit 1499 ${theBork.index}") { it.contains("1/1500 hp") }
                alice.commandReply("setlevel strength 99") { it.contains("set to level 99") }
                alice.commandReply("setlevel attack 99") { it.contains("set to level 99") }
                alice.commandReply("sure max") { it.startsWith("Your attacks can't miss and") }
                alice.npcOp(theBork.index, 2)
                alice.await("her swing killing him") { theBork.takeIf { it.dead } }
                alice.awaitLine(run, "Bork down") { it.endsWith("fight: Bork down") }
                alice.await("Bork's body gone") { theBork.takeIf { world.npcs[it.index] !== it } }
                alice.awaitTicks(3)
                assertFalse(ArchiveRoom.BORK in run.cleared)
                val still = alice.fight()
                assertTrue(still.contains("Bork 0/1500 hp, the Smorks 450/600 hp"), still)
                // Then the Smorks: once both are gone, the room is done and the fight's anchor with it.
                alice.commandReply("hit 450 ${theSmorks.index}") { it.startsWith("Hit ") }
                alice.await("their room done") { ArchiveRoom.BORK.takeIf { it in run.cleared } }
                assertNull(run.fights[ArchiveRoom.BORK])
                assertTrue(world.npcs.all.none { it.type.id == Bork.ANCHOR || it.type.id == Bork.BORK || it.type.id == Bork.SMORKS })
            }
        }
    }

    @Test
    fun `the Smorks fire at everyone Bork isn't after, and with them dead first his armour is at its most till he dies`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    val clients = listOf(alice to "alice", bob to "bob")
                    for ((client, _) in clients) client.await("the login rebuild") { client.position }
                    party(clients)
                    val run = alice.intoRun(server, "alice")
                    assertEquals(run, bob.intoRun(server, "bob"))
                    alice.toBork(server, run)
                    bob.commandReply("raid tp bork") { it.startsWith("To ") }
                    val theSmorks = npc(run, Bork.SMORKS)
                    val theBork = npc(run, Bork.BORK)
                    // A party of two: the Wiki's 1,500 and 600 still.
                    assertEquals(1500, theBork.maxHitpoints)
                    assertEquals(600, theSmorks.maxHitpoints)
                    val volley = alice.awaitLine(run, "a volley at bob") { it.contains("the Smorks fire") && it.endsWith(" at bob") }
                    assertTrue(volley.contains("bones at bob") || volley.contains("potions at bob"), volley)
                    assertTrue(lines(run).none { it.contains("the Smorks fire") && it.contains("alice") }, lines(run).toString())
                    // Their first volley 13 ticks in, napalm a spacing after it.
                    alice.awaitLine(run, "napalm called") { it.endsWith("fight: the Smorks call napalm") }
                    assertEquals(Bork.FIRST_VOLLEY.toLong(), tickOf(lines(run).first { it.contains("the Smorks fire") }))
                    assertEquals((Bork.FIRST_VOLLEY + Bork.ATTACK_SPEED).toLong(), tickOf(lines(run).first { it.contains("the Smorks call napalm") }))
                    assertEquals(Bork.FIRST_GONG.toLong(), tickOf(lines(run).first { it.contains("beat on") }))
                    // The Smorks dead first: Bork's armour at its most, and the room done only with him dead too.
                    alice.commandReply("hit 600 ${theSmorks.index}") { it.startsWith("Hit ") }
                    alice.awaitLine(run, "the Smorks down") { it.endsWith("fight: the Smorks down") }
                    alice.awaitLine(run, "his armour at its most") { it.endsWith("fight: Bork's armour 14, magic 6") }
                    alice.awaitTicks(Bork.ATTACK_SPEED)
                    assertFalse(ArchiveRoom.BORK in run.cleared)
                    alice.commandReply("hit 1500 ${theBork.index}") { it.startsWith("Hit ") }
                    alice.await("their room done") { ArchiveRoom.BORK.takeIf { it in run.cleared } }
                }
            }
        }
    }

    @Test
    fun `napalm leaves oil that burns, the heavy cannonball lured onto Bork dazes him, and his stomp spares the tower's shadow`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    val clients = listOf(alice to "alice", bob to "bob")
                    for ((client, _) in clients) client.await("the login rebuild") { client.position }
                    party(clients)
                    val run = alice.intoRun(server, "alice")
                    assertEquals(run, bob.intoRun(server, "bob"))
                    alice.toBork(server, run)
                    val world = server.world
                    val cart = world.archiveRaids.tile(run, ArchiveRaids.BORK_CART_X, ArchiveRaids.BORK_CART_Z)
                    val theSmorks = npc(run, Bork.SMORKS)
                    val theBork = npc(run, Bork.BORK)
                    // Both on their room's floor, west of the tower: alice, first in, his tank. Neither hits back, so each
                    // stays where they're put.
                    for (client in listOf(alice, bob)) {
                        client.command("varp ${EquipmentTab.VARP_AUTO_RETALIATE_OFF} 1")
                        client.await("auto retaliate off") { if (client.varp(EquipmentTab.VARP_AUTO_RETALIATE_OFF) == 1) Unit else null }
                    }
                    alice.tele(cart.x - 4, cart.z + 1)
                    bob.tele(cart.x - 4, cart.z - 4)
                    // Napalm at bob, who stays put: oil under him a tick after it splashes, burning him.
                    val aimed = alice.awaitLine(run, "napalm at bob") { it.contains("fight: napalm at ") }
                    assertTrue(aimed.endsWith("(bob)"), aimed)
                    val at = aimed.substringAfter("napalm at ").substringBefore(' ')
                    assertEquals("${cart.x - 4},${cart.z - 4}", at)
                    val oil = alice.awaitLine(run, "the oil") { it.contains("fight: oil on ") }
                    assertTrue(oil.substringAfter("oil on ").split(' ').contains(at), oil)
                    assertEquals(Bork.NAPALM_CAST + Bork.NAPALM_FLIGHT + Bork.OIL_AFTER_SPLASH.toLong(), tickOf(oil) - tickOf(lines(run).first { it.contains("the Smorks call napalm") }))
                    // The oil burns him the tick it appears, and holds him in combat though it comes from no one.
                    val burnt = alice.awaitLine(run, "the oil burning bob") { it.contains("fight: oil burns bob for") }
                    assertEquals(tickOf(oil), tickOf(lines(run).first { it.contains("fight: oil burns bob for") }))
                    val fight = assertNotNull(run.fights[ArchiveRoom.BORK])
                    val began = generateSequence { world.tick - fight.phaseTick }.zipWithNext().first { (a, b) -> a == b }.first
                    assertTrue(world.playerNamed("bob")!!.attackedTick >= began + tickOf(burnt), "bob's combat lock from the oil")
                    alice.awaitLine(run, "bob on fire") { it.contains("fight: burn on bob for") }
                    // Bork beside alice, standing; bob under him, where the heavy cannonball will follow him.
                    alice.awaitLine(run, "Bork beating at alice") { it.contains("beat hits alice for") }
                    bob.tele(theBork.x + 1, theBork.z + 1)
                    val shot = alice.awaitLine(run, "the heavy cannonball") { it.contains("fight: heavy cannonball at ") }
                    assertTrue(shot.endsWith("(bob)"), shot)
                    assertEquals(Bork.CANNON_SHOT.toLong(), tickOf(shot) - tickOf(lines(run).last { it.contains("the Smorks call a heavy cannonball") }))
                    val dazed = alice.awaitLine(run, "Bork dazed") { it.endsWith("fight: Bork dazed") }
                    assertEquals(Bork.CANNON_FLIGHT.toLong(), tickOf(dazed) - tickOf(shot))
                    // Dazed: no armour, and melee always hits him.
                    alice.awaitLine(run, "his armour gone") { it.endsWith("fight: Bork's armour 0, magic 0") }
                    assertEquals(20, world.meleeCombat.armoured(theBork, 20, magic = false))
                    assertTrue(theBork.meleeSureUntil > world.tick)
                    // Bob into the tower's shadow before the stomp; alice stays beside Bork, out in the open.
                    val shade = (cart.x + Bork.ARENA_WEST..cart.x + Bork.ARENA_EAST).flatMap { x -> (cart.z + Bork.ARENA_SOUTH..cart.z + Bork.ARENA_NORTH).map { Tile(x, it) } }
                        .first { Bork.inArena(cart.x, cart.z, it.x, it.z) && Bork.shadowed(theSmorks.x, theSmorks.z, 3, theBork.x, theBork.z, 4, it.x, it.z) }
                    bob.tele(shade.x, shade.z)
                    val stomps = alice.awaitLine(run, "Bork stomping") { it.endsWith("fight: Bork stomps") }
                    assertEquals(Bork.DAZE_TICKS.toLong(), tickOf(stomps) - tickOf(dazed))
                    alice.awaitLine(run, "his armour back") { it.endsWith("fight: Bork's armour 8, magic 0") }
                    val call = alice.awaitLine(run, "his next tank") { it.endsWith("fight: Bork takes on bob") }
                    assertEquals(Bork.STOMP_CALL.toLong(), tickOf(call) - tickOf(stomps))
                    val waves = lines(run).filter { it.contains("fight: shockwave on ") }
                    assertEquals(Bork.STOMP_WAVES.map { tickOf(stomps) + it }, waves.map(::tickOf))
                    assertTrue(waves.all { it.endsWith("shockwave on alice, the tower shading bob") }, waves.toString())
                    // His next gong 19 ticks into the stomp, and the Smorks' next cycle 25.
                    alice.awaitLine(run, "his next gong") { it.contains("beat on") && tickOf(it) > tickOf(stomps) }
                    assertEquals(Bork.STOMP_GONG.toLong(), tickOf(lines(run).first { it.contains("beat on") && tickOf(it) > tickOf(stomps) }) - tickOf(stomps))
                    val next = alice.awaitLine(run, "their next volley") { it.contains("the Smorks fire") && tickOf(it) > tickOf(stomps) }
                    assertEquals(Bork.CYCLE_AFTER_STOMP.toLong(), tickOf(next) - tickOf(stomps))
                    assertTrue(next.endsWith(" at alice"), "bob is his tank now: $next")
                }
            }
        }
    }

    @Test
    fun `the tempo rises as the Smorks fall, and with Bork dead they go into their frenzy at everyone`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    val clients = listOf(alice to "alice", bob to "bob")
                    for ((client, _) in clients) client.await("the login rebuild") { client.position }
                    party(clients)
                    val run = alice.intoRun(server, "alice")
                    assertEquals(run, bob.intoRun(server, "bob"))
                    alice.toBork(server, run)
                    bob.commandReply("raid tp bork") { it.startsWith("To ") }
                    bob.ontoFloor(server, run)
                    val theSmorks = npc(run, Bork.SMORKS)
                    val theBork = npc(run, Bork.BORK)
                    // A quarter off the Smorks before his first gong: every gong 6 ticks apart from then.
                    alice.commandReply("hit 150 ${theSmorks.index}") { it.startsWith("Hit ") }
                    val tempo = alice.awaitLine(run, "the tempo up") { it.endsWith("fight: tempo 1, every 6 ticks") }
                    assertTrue(tickOf(tempo) < Bork.FIRST_GONG, tempo)
                    alice.awaitLine(run, "three gongs") { line -> lines(run).count { it.contains("beat on") } >= 5 && line.contains("beat on") }
                    val gongs = lines(run).filter { it.contains("beat on") }.map(::tickOf)
                    assertEquals(listOf(8L, 10L, 14L, 16L, 20L), gongs.take(5))
                    // Three quarters off: the tempo at its most.
                    alice.commandReply("hit 300 ${theSmorks.index}") { it.startsWith("Hit ") }
                    alice.awaitLine(run, "the tempo at its most") { it.endsWith("fight: tempo 3, every 4 ticks") }
                    // Bork dead first: 11 ticks on, the Smorks' frenzy, a volley at everyone every 2 ticks, napalm after
                    // two or three.
                    alice.commandReply("hit 1500 ${theBork.index}") { it.startsWith("Hit ") }
                    val down = alice.awaitLine(run, "Bork down") { it.endsWith("fight: Bork down") }
                    val frenzy = alice.awaitLine(run, "their frenzy") { it.endsWith("fight: the Smorks' frenzy") }
                    assertEquals((Bork.BORK_GONE + Bork.FRENZY_AFTER).toLong(), tickOf(frenzy) - tickOf(down))
                    alice.awaitLine(run, "napalm in the frenzy") { it.endsWith("fight: the Smorks call napalm") && tickOf(it) > tickOf(frenzy) }
                    val volleys = lines(run).filter { it.contains("the Smorks fire") && tickOf(it) >= tickOf(frenzy) }
                    assertTrue(volleys.size in 2..3, volleys.toString())
                    assertTrue(volleys.all { it.endsWith(" at alice, bob") }, volleys.toString())
                    assertEquals(volleys.indices.map { tickOf(frenzy) + Bork.FRENZY_SPACING * it }, volleys.map(::tickOf))
                }
            }
        }
    }

    @Test
    fun `with the Smorks dead first Bork stomps over the whole room every 45 ticks`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.toBork(server, run)
                val theSmorks = npc(run, Bork.SMORKS)
                alice.commandReply("hit 600 ${theSmorks.index}") { it.startsWith("Hit ") }
                val down = alice.awaitLine(run, "the Smorks down") { it.endsWith("fight: the Smorks down") }
                alice.awaitLine(run, "the tempo at its most") { it.endsWith("fight: tempo 3, every 4 ticks") }
                val stomp = alice.awaitLine(run, "his stomp") { it.endsWith("fight: Bork stomps") }
                assertEquals(Bork.LONE_STOMP_EVERY.toLong(), tickOf(stomp) - tickOf(down))
                alice.awaitLine(run, "a shockwave on her") { it.endsWith("fight: shockwave on alice") }
                // No napalm, volleys or cannonballs from Smorks that are gone.
                assertTrue(lines(run).none { tickOf(it) > tickOf(down) && (it.contains("the Smorks fire") || it.contains("the Smorks call")) }, lines(run).toString())
            }
        }
    }

    @Test
    fun `with too few NPC slots free their room isn't started, and their fight begins once there are`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.commandReply("raid tp guardian") { it.startsWith("To ") }
                alice.await("the Guardian's room done") { ArchiveRoom.GUARDIAN.takeIf { it in run.cleared } }
                // Every free slot but two taken: the anchor and the Smorks fit, Bork doesn't.
                val npcs = server.world.npcs
                val filler = NpcType(1, "Filler")
                val fillers = generateSequence { npcs.add(NpcSpawn(filler, 1000, 1000, source = "test")) }.toList()
                assertTrue(fillers.size > 1000, "${fillers.size} fillers")
                npcs.remove(fillers[0])
                npcs.remove(fillers[1])
                alice.commandReply("raid tp bork") { it.startsWith("To ") }
                alice.ontoFloor(server, run)
                alice.awaitTicks(5)
                assertFalse(ArchiveRoom.BORK in run.started, "their room started without Bork")
                assertNull(run.fights[ArchiveRoom.BORK])
                assertTrue(npcs.all.none { it.type.id == Bork.ANCHOR || it.type.id == Bork.SMORKS }, "what did spawn, taken back")
                // Slots free: with her still in their room, the fight begins.
                for (npc in fillers.drop(2)) npcs.remove(npc)
                val begun = alice.await("their fight") { alice.fight().takeIf { it.startsWith("bork #") } }
                assertTrue(begun.contains("Bork 1500/1500 hp, the Smorks 600/600 hp"), begun)
                assertTrue(ArchiveRoom.BORK in run.started && ArchiveRoom.BORK !in run.cleared)
            }
        }
    }
}
