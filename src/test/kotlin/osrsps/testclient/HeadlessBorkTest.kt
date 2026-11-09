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
 * Bork and the Smorks (raid step 18a) through the headless client: their fight beginning with the first of the party
 * into their room, both scaled to the party, Bork taking that player on and beating his gong; the Smorks firing at
 * everyone Bork isn't after; Bork's flat armour rising with the Smorks' damage and taking that off hits; and the room
 * done only once both are dead, whichever goes first. The rl-client script test-runner/examples/archive-bork.json
 * fights them in the real client.
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

    /** Through the Guardian's hall (done at once) to Bork's room, his fight begun. */
    private fun HeadlessClient.toBork(run: ArchiveRun) {
        commandReply("raid tp guardian") { it.startsWith("To ") }
        await("the Guardian's room done") { ArchiveRoom.GUARDIAN.takeIf { it in run.cleared } }
        commandReply("raid tp bork") { it.startsWith("To ") }
        await("their fight") { fight().takeIf { it.startsWith("bork #") } }
    }

    private fun lines(run: ArchiveRun): List<String> = run.fights[ArchiveRoom.BORK]?.lines?.toList().orEmpty()

    private fun HeadlessClient.awaitLine(run: ArchiveRun, what: String, predicate: (String) -> Boolean): String =
        try {
            await(what) { lines(run).lastOrNull(predicate) }
        } catch (e: AssertionError) {
            throw AssertionError("${e.message}; the fight's last lines: ${lines(run).takeLast(12)}", e)
        }

    private fun npc(run: ArchiveRun, type: Int): Npc = assertNotNull(run.fights[ArchiveRoom.BORK]).adds.first { it.type.id == type }

    @Test
    fun `his flat armour rises with the Smorks' damage, beside and near are his reach, and the cart shields whoever is behind it`() {
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
        // The cart on 15..17 x 10..12, east of him: whoever is east of it is behind it; north-east of him isn't.
        assertTrue(Bork.shields(15, 10, 3, 10, 10, 4, 18, 11))
        assertTrue(Bork.shields(15, 10, 3, 10, 10, 4, 19, 12))
        assertFalse(Bork.shields(15, 10, 3, 10, 10, 4, 16, 15))
        assertFalse(Bork.shields(15, 10, 3, 10, 10, 4, 14, 11), "between him and the cart")
        assertFalse(Bork.shields(15, 10, 3, 10, 10, 4, 16, 11), "on the cart itself")
    }

    @Test
    fun `their fight begins with the first into their room, Bork beats at her, and the room is done once both are dead`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                val run = alice.intoRun(server, "alice")
                alice.commandReply("raid tp guardian") { it.startsWith("To ") }
                alice.await("the Guardian's room done") { ArchiveRoom.GUARDIAN.takeIf { it in run.cleared } }
                alice.commandReply("raid tp bork") { it.startsWith("To ") }
                val begun = alice.await("their fight") { alice.fight().takeIf { it.startsWith("bork #") } }
                // A party of one counts as two: the Wiki's 1,500 and 600.
                assertTrue(begun.contains("Bork 1500/1500 hp, the Smorks 600/600 hp; 1 players, 2 adds"), begun)
                assertTrue(ArchiveRoom.BORK in run.started && ArchiveRoom.BORK !in run.cleared)
                val world = server.world
                val cart = world.archiveRaids.tile(run, ArchiveRaids.BORK_CART_X, ArchiveRaids.BORK_CART_Z)
                val theSmorks = npc(run, Bork.SMORKS)
                val theBork = npc(run, Bork.BORK)
                assertEquals(cart, Tile(theSmorks.spawn.x, theSmorks.spawn.z))
                assertEquals(Tile(cart.x - Bork.BORK_WEST, cart.z), Tile(theBork.spawn.x, theBork.spawn.z))
                // She, the first in, is his tank: he walks to her and beats.
                alice.awaitLine(run, "Bork beating at alice") { it.contains("beat hits alice for") }
                assertSame(world.playerNamed("alice"), theBork.target)
                assertTrue(lines(run).any { it.endsWith("fight: Bork takes on alice") })
                assertTrue(lines(run).any { it.endsWith("fight: Bork's armour 8, magic 0") })
                // Alone, she's his: the Smorks fire at no one.
                assertTrue(lines(run).none { it.contains("the Smorks fire") }, lines(run).toString())
                // A quarter off the Smorks: two more armour, and magic resistance, off each hit on him. By name, from
                // further off than ::hit's search reaches: it finds them in the fight she's in.
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
                    alice.toBork(run)
                    bob.commandReply("raid tp bork") { it.startsWith("To ") }
                    val theSmorks = npc(run, Bork.SMORKS)
                    val theBork = npc(run, Bork.BORK)
                    // A party of two: the Wiki's 1,500 and 600 still.
                    assertEquals(1500, theBork.maxHitpoints)
                    assertEquals(600, theSmorks.maxHitpoints)
                    val volley = alice.awaitLine(run, "a volley at bob") { it.contains("the Smorks fire") && it.endsWith(" at bob") }
                    assertTrue(volley.contains("bones at bob") || volley.contains("potions at bob"), volley)
                    assertTrue(lines(run).none { it.contains("the Smorks fire") && it.contains("alice") }, lines(run).toString())
                    // Their volleys come 3 ticks after his beats, the first after his first.
                    val volleyTicks = lines(run).filter { it.contains("the Smorks fire") }.map { it.substringBefore(' ').toLong() }
                    assertTrue(volleyTicks.all { it >= Bork.ATTACK_SPEED + Bork.SMORKS_OFFSET && it % Bork.ATTACK_SPEED == Bork.SMORKS_OFFSET.toLong() }, volleyTicks.toString())
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
