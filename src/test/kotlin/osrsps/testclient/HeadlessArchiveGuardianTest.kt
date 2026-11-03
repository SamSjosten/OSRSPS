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
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.Deaths
import osrsps.game.combat.NpcCombat
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.raid.ArchiveGuardian
import osrsps.game.raid.ArchiveRaids
import osrsps.game.raid.ArchiveRoom
import osrsps.game.raid.ArchiveRun
import osrsps.game.raid.PartyScaling
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * The Archive Guardian (raid step 16a) through the headless client: waiting in its hall till a member touches it, then
 * its shield, its fall to the ground with the grounded hitpoints, its lift-off at 30% and its death, which does its
 * room; the raid's instance multi-way; its laser shared by the players on its line; and melee kept off it while it
 * levitates, but for a halberd. The rl-client script test-runner/examples/archive-guardian.json fights it in the real
 * client.
 */
class HeadlessArchiveGuardianTest {
    private val stats = NpcStats(attack = 250, defence = 260, strength = 240, hitpoints = 550, ranged = 280, magic = 250)
    private val flying = NpcType(ArchiveGuardian.FLYING, "Archive Guardian", size = 5, ops = listOf(null, "Attack", null, null, null), internalName = "tfa_guardian", stats = stats)
    private val grounded = NpcType(
        ArchiveGuardian.GROUNDED, "Archive Guardian", size = 5, ops = listOf(null, "Attack", null, null, null), internalName = "tfa_guardian_unshielded",
        stats = stats.copy(hitpoints = 1100),
    )
    private val dormant = NpcType(ArchiveGuardian.DORMANT, "Inert Guardian", size = 5, ops = listOf("Touch", null, null, null, null), internalName = "tfa_guardian_start")
    private val grave = NpcType(Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null))
    private val types = NpcTypes.of(flying, grounded, dormant, grave)

    private val halberd = ObjType(
        HALBERD, "Bronze halberd", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, internalName = "bronze_halberd", category = 66,
    )

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
            locTypes = locTypes, staticLocs = staticLocs, objTypes = ObjTypes(arrayOfNulls<ObjType>(HALBERD + 1).also { it[HALBERD] = halberd }),
            npcTypes = types, npcCombat = NpcCombat.load(types, SeqNames.NONE), partyScaling = PartyScaling.load(types),
            encounterDefs = listOfNotNull(ArchiveGuardian.def(types)), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    /** `::raid`'s line on the fight the player is in. */
    private fun HeadlessClient.fight(): String = commandReply("raid") { it.startsWith("guardian #") || it.startsWith("You're not") }

    private fun HeadlessClient.awaitFight(what: String, predicate: (String) -> Boolean): String = await(what) { fight().takeIf(predicate) }

    /** `::raid log 200`'s lines. */
    private fun HeadlessClient.fightLog(): List<String> {
        val from = messages().size
        command("raid log 200")
        await("::raid log's lines") { messages().drop(from).takeIf { lines -> lines.any { Regex("^\\d+ \\w+: ").containsMatchIn(it.text) } } }
        return messages().drop(from).map { it.text }
    }

    @Test
    fun `the Guardian waits in its hall till touched, then fights through its shield, the ground and the air, and its death does the room`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                alice.commandReply("god on") { it.startsWith("You can't be hurt") }
                alice.tele(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z)
                alice.locOp(ArchiveRaids.ARCHIVE_DOOR, 4123, 5562, ArchiveRaids.ENTER)
                alice.await("into the instance") { alice.position?.takeIf { server.world.instances.inDynamicArea(it.x, it.z) } }
                val raids = server.world.archiveRaids
                val run: ArchiveRun = assertNotNull(server.world.parties.of(server.world.playerNamed("alice")!!)?.let(raids::of))
                // The whole instance is multi-way; the world outside it isn't.
                val inside = raids.tile(run, ArchiveRaids.NEXUS_X, ArchiveRaids.NEXUS_Z)
                assertTrue(server.world.multiCombat.at(0, inside.x, inside.z))
                assertFalse(server.world.multiCombat.at(0, 3222, 3218))

                // Into the hall: nothing begins till the Guardian is touched.
                alice.commandReply("raid tp guardian") { it.startsWith("To ") }
                val waiting = alice.await("the waiting Guardian in view") { alice.npcs().firstOrNull { it.id == ArchiveGuardian.DORMANT } }
                val at = raids.tile(run, ArchiveRaids.GUARDIAN_X, ArchiveRaids.GUARDIAN_Z)
                assertEquals(Coord(at.x, at.z, 0), waiting.coord)
                assertTrue(alice.fight().startsWith("You're not in a fight"), "the hall walked into, the fight not begun")
                assertTrue(ArchiveRoom.GUARDIAN !in run.started)
                alice.npcOp(waiting.index, ArchiveGuardian.TOUCH)

                // Awake: the levitating form, with the shield's hitpoints for a party of one (counted as two).
                val shield = alice.awaitFight("the shield phase") { it.contains("phase 1 (shield)") }
                assertTrue(shield.contains("Archive Guardian 550/550 hp; 1 players"), shield)
                assertTrue(ArchiveRoom.GUARDIAN in run.started)
                val boss = assertNotNull(run.guardian)
                assertEquals(ArchiveGuardian.FLYING, boss.type.id)
                alice.await("its first shot") { alice.fightLog().takeIf { lines -> lines.any { it.endsWith("shield: shoots alice") } } }
                assertTrue(alice.fightLog().any { it.endsWith("shield: takes on alice") })

                // The shield gone, it holds at 0 and drops with the grounded hitpoints, full.
                assertEquals("Hit Archive Guardian #${boss.index} for 550: 0/550 hp", alice.commandReply("hit 550 ${boss.index}") { it.startsWith("Hit ") })
                val ground = alice.awaitFight("the grounded phase") { it.contains("phase 2 (grounded)") }
                assertTrue(ground.contains("Archive Guardian 1100/1100 hp"), ground)
                assertEquals(ArchiveGuardian.GROUNDED, boss.type.id)
                assertFalse(boss.dead)

                // At 30% it lifts off for good, keeping the grounded hitpoints.
                alice.commandReply("hit 770 ${boss.index}") { it.startsWith("Hit ") }
                val air = alice.awaitFight("the airborne phase") { it.contains("phase 3 (airborne)") }
                assertTrue(air.contains("Archive Guardian 330/1100 hp"), air)
                assertEquals(ArchiveGuardian.FLYING, boss.type.id)
                alice.await("its laser") { alice.fightLog().takeIf { lines -> lines.any { it.endsWith("airborne: laser hits alice for 0") } } }

                // Dead, its room is done and the first row of doors opens.
                alice.commandReply("hit 330 ${boss.index}") { it.startsWith("Hit ") }
                alice.await("the Guardian's room done") { alice.commandReply("raid run") { it.startsWith("Archive run") }.takeIf { it.contains("done Archive Guardian;") } }
                assertTrue(ArchiveRoom.GUARDIAN in run.cleared)
                assertNull(run.fights[ArchiveRoom.GUARDIAN])
            }
        }
    }

    @Test
    fun `the laser is shared by the players on its line`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    for (client in listOf(alice, bob)) {
                        client.await("the login rebuild") { client.position }
                        client.commandReply("setlevel hitpoints 99") { it.contains("99") || it.startsWith("Usage") }
                        client.commandReply("heal") { it.startsWith("Healed") }
                    }
                    // Both on the boss's south-west tile, so whichever it aims at, the other is on the line too.
                    bob.tele(3222, 3218)
                    alice.tele(3222, 3218)
                    alice.commandReply("raid guardian phase=airborne seed=7") { it.startsWith("Started") }
                    assertTrue(alice.fight().contains("Archive Guardian 330/1100 hp; 2 players"))
                    val lines = alice.await("the laser on both") {
                        alice.fightLog().filter { it.contains("laser hits") }.takeIf { it.size >= 2 }
                    }
                    // 80 for two players in the fight, half each.
                    assertEquals(listOf("airborne: laser hits alice for 40", "airborne: laser hits bob for 40"), lines.take(2).map { it.substringAfter(' ') })
                    assertEquals(59, server.world.playerNamed("alice")!!.hitpoints)
                    assertEquals(59, server.world.playerNamed("bob")!!.hitpoints)
                }
            }
        }
    }

    @Test
    fun `levitating, it is out of melee's reach but for a halberd, and grounded anyone can hit it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                alice.commandReply("god on") { it.startsWith("You can't be hurt") }
                alice.tele(3222, 3218)
                alice.commandReply("raid guardian") { it.startsWith("Started") }
                // Beside it, unarmed.
                alice.tele(3221, 3220)
                val boss = alice.await("the Guardian in view") { alice.npcs().firstOrNull { it.id == ArchiveGuardian.FLYING } }
                val from = alice.messages().size
                alice.npcOp(boss.index, ATTACK)
                alice.await("the refusal") { alice.messages().drop(from).firstOrNull { it.text == "[combat.flying]" } }
                assertNull(server.world.playerNamed("alice")!!.interaction)

                // A halberd reaches it.
                alice.command("item $HALBERD 1")
                val slot = alice.await("the halberd") { alice.inventory(BACKPACK).indexOfFirst { it?.id == HALBERD }.takeIf { it >= 0 } }
                alice.ifButton(INVENTORY, INVENTORY_ITEMS, IOP_BUTTON_OPS[WIELD - 1], slot, HALBERD)
                alice.await("the halberd worn") { alice.inventory(WORN).getOrNull(WearPos.WEAPON)?.takeIf { it.id == HALBERD } }
                val again = alice.messages().size
                alice.npcOp(boss.index, ATTACK)
                alice.await("a swing of the halberd") { server.world.playerNamed("alice")!!.attackedNpc?.takeIf { it.index == boss.index } }
                assertTrue(alice.messages().drop(again).none { it.text == "[combat.flying]" })

                // Grounded, bare fists do.
                alice.commandReply("raid stop") { it.startsWith("Stopped") }
                alice.tele(3222, 3218)
                alice.commandReply("raid guardian phase=grounded") { it.startsWith("Started") }
                server.login("bob").use { bob ->
                    bob.await("the login rebuild") { bob.position }
                    bob.commandReply("god on") { it.startsWith("You can't be hurt") }
                    bob.tele(3221, 3220)
                    val down = bob.await("the grounded Guardian in view") { bob.npcs().firstOrNull { it.id == ArchiveGuardian.FLYING } }
                    assertEquals(ArchiveGuardian.GROUNDED, server.world.npcs[down.index]!!.type.id)
                    val third = bob.messages().size
                    bob.npcOp(down.index, ATTACK)
                    bob.await("a punch") { server.world.playerNamed("bob")!!.attackedNpc?.takeIf { it.index == down.index } }
                    assertTrue(bob.messages().drop(third).none { it.text == "[combat.flying]" })
                }
            }
        }
    }

    @Test
    fun `the laser's line runs from the boss's middle through its target, and its damage grows with the fight`() {
        // A 5 by 5 boss at 100, 100: its middle is 102, 102.
        val east = ArchiveGuardian.line(100, 100, 5, osrsps.map.Tile(105, 102))
        assertTrue(osrsps.map.Tile(103, 102) in east && osrsps.map.Tile(110, 102) in east)
        assertFalse(osrsps.map.Tile(101, 102) in east, "not behind the boss")
        assertFalse(osrsps.map.Tile(105, 103) in east)
        val diagonal = ArchiveGuardian.line(100, 100, 5, osrsps.map.Tile(104, 104))
        assertTrue(osrsps.map.Tile(103, 103) in diagonal && osrsps.map.Tile(106, 106) in diagonal)
        assertEquals(setOf(osrsps.map.Tile(102, 102)), ArchiveGuardian.line(100, 100, 5, osrsps.map.Tile(102, 102)))
        assertEquals(ArchiveGuardian.LASER_LENGTH, east.size)
        // 80 for one or two, so a player alone can live through it; from three it kills one player who takes it alone.
        assertEquals(listOf(80, 80, 120, 320), listOf(1, 2, 3, 8).map(ArchiveGuardian::laserDamage))
    }

    private companion object {
        const val HALBERD = 3190
        const val ATTACK = 2
        const val BACKPACK = 93
        const val WORN = 94
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
        const val WIELD = 2
        val IOP_BUTTON_OPS = intArrayOf(2, 3, 4, 6, 7)
    }
}
