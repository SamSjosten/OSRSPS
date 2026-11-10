package osrsps.testclient

import java.nio.file.Path
import kotlin.math.sign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
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
import osrsps.game.combat.Prayers
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
import osrsps.map.Tile

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
    private val vine = NpcType(ArchiveGuardian.VINE, "Vine", internalName = "archive_guardian_tentacle")
    private val grave = NpcType(Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null))
    private val types = NpcTypes.of(flying, grounded, dormant, vine, grave)

    // The inert guardians' three forms, as the 241.4 cache has them: only the awake one has Attack and its hitpoints.
    private val asleep = NpcType(ArchiveGuardian.BATTERY_INACTIVE, "Inert Guardian", size = 2, internalName = "tfa_guardian_battery_inactive")
    private val awake = NpcType(
        ArchiveGuardian.BATTERY_ACTIVE, "Inert Guardian", size = 2, ops = listOf(null, "Attack", null, null, null), internalName = "tfa_guardian_battery_active",
        stats = NpcStats(attack = 1, defence = 50, strength = 1, hitpoints = 180, ranged = 1, magic = 50),
    )
    private val spent = NpcType(ArchiveGuardian.BATTERY_SPENT, "Inert Guardian", size = 2, internalName = "tfa_guardian_battery_destroyed")
    private val withBatteries = NpcTypes.of(flying, grounded, dormant, vine, grave, asleep, awake, spent)

    private val halberd = ObjType(
        HALBERD, "Bronze halberd", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, internalName = "bronze_halberd", category = 66,
    )

    // Retribution's prayer obj, shaped like the 241 cache's (HeadlessPrayerTest): 630 the bit, 1751 the prayer book
    // component, 1752 the name, 1753 the level, 1755 the sound.
    private val retribution = ObjType(
        RETRIBUTION_OBJ, "Null", internalName = "xmas16_marionette_green",
        params = mapOf(630 to 15, 1751 to (Prayers.PRAYERBOOK shl 16 or RETRIBUTION), 1752 to "Retribution", 1753 to 46, 1755 to 2679),
    )
    private val protectFromMelee = ObjType(
        PROTECT_FROM_MELEE_OBJ, "Null", internalName = "placeholder_xmas16_marionette_blue",
        params = mapOf(630 to 14, 1751 to (Prayers.PRAYERBOOK shl 16 or PROTECT_FROM_MELEE), 1752 to "Protect from Melee", 1753 to 43, 1755 to 2676),
    )
    private val objTypes = ObjTypes(
        arrayOfNulls<ObjType>(RETRIBUTION_OBJ + 1).also {
            it[HALBERD] = halberd
            it[RETRIBUTION_OBJ] = retribution
            it[PROTECT_FROM_MELEE_OBJ] = protectFromMelee
        },
    )

    private val locTypes = LocTypes(
        mapOf(ArchiveRaids.ARCHIVE_DOOR to LocType(ArchiveRaids.ARCHIVE_DOOR, "Archive Door", ops = listOf("Enter", null, null, null, null), collision = LocCollisionType(width = 9, length = 1))),
    )
    private val staticLocs = StaticLocs().apply { add(ZoneLoc(ArchiveRaids.ARCHIVE_DOOR, LocShape.CENTREPIECE_STRAIGHT, 0, 4123, 5562, 0)) }

    private fun server(dir: Path, batteries: Boolean = false) = TestServer(dir) { crcs, millis ->
        val types = if (batteries) withBatteries else types
        World(
            crcs,
            CollisionMap().apply {
                for (x in 63..65) for (z in 86..88) markLoaded(x, z)
                markLoaded(3222 shr 6, 3218 shr 6)
            },
            locTypes = locTypes, staticLocs = staticLocs, objTypes = objTypes, prayerList = Prayers.load(objTypes),
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

    /** Bob joins Alice's party: outside an instance, a `::raid` fight takes in only its starter's party. */
    private fun party(alice: HeadlessClient, bob: HeadlessClient) {
        alice.commandReply("raidparty invite bob") { it.startsWith("Invited") }
        bob.commandReply("raidparty accept alice") { it.startsWith("You joined") }
    }

    /**
     * `::raid log 200`'s lines, all of them. A plain `::raid` after it marks their end: its reply comes after the last of
     * them, so a slow read can't cut the log short, or leave lines over to count again in the next call's.
     */
    private fun HeadlessClient.fightLog(): List<String> {
        val from = messages().size
        command("raid log 200")
        command("raid")
        val lines = await("::raid log's lines") {
            val after = messages().drop(from).map { it.text }
            val end = after.indexOfFirst { it.startsWith("guardian #") || it.startsWith("You're not") }
            if (end < 0) null else after.take(end)
        }
        return lines
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
    fun `grounded, it lifts off behind a whole shield after 35 ticks, then 41, its hitpoints carried over each time`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                alice.commandReply("god on") { it.startsWith("You can't be hurt") }
                alice.tele(3222, 3218)
                alice.commandReply("raid guardian") { it.startsWith("Started") }
                val fight = server.world.encounters.all.single()
                val boss = fight.boss
                fun starts(phase: String) = fight.lines.filter { it.endsWith(" $phase: phase starts") }.map { it.substringBefore(' ').toLong() }
                fun tickOf(end: String) = fight.lines.last { it.endsWith(end) }.substringBefore(' ').toLong()

                // The shield broken, it holds at 0 for 2 ticks, then drops with its hitpoints full.
                alice.commandReply("hit 550 ${boss.index}") { it.startsWith("Hit ") }
                assertTrue(alice.awaitFight("its first time down") { it.contains("(grounded)") }.contains("Archive Guardian 1100/1100 hp"))
                assertEquals(ArchiveGuardian.SHIELD_DOWN_TICKS.toLong(), starts("grounded").single() - tickOf("shield: shield broken"))
                alice.commandReply("hit 100 ${boss.index}") { it.contains("1000/1100 hp") }

                // 35 ticks on it is up again, its shield whole, with nothing yet at 30%.
                val up = alice.awaitFight("its shield again") { it.contains("(shield)") }
                assertTrue(up.contains("Archive Guardian 550/550 hp"), up)
                assertEquals(ArchiveGuardian.FLYING, boss.type.id)
                assertEquals(ArchiveGuardian.GROUNDED_FIRST.toLong(), starts("shield").last() - starts("grounded").single())
                assertTrue(fight.lines.any { it.endsWith("shield: lifts off with 1000 hp left, its shield whole: 550") }, fight.lines.toString())

                // Down again: what it had left, not its full hitpoints. This time for 41 ticks.
                alice.commandReply("hit 550 ${boss.index}") { it.startsWith("Hit ") }
                val down = alice.awaitFight("its second time down") { it.contains("(grounded)") }
                assertTrue(down.contains("Archive Guardian 1000/1100 hp"), down)
                assertEquals(ArchiveGuardian.GROUNDED, boss.type.id)
                alice.awaitFight("its shield a third time") { it.contains("(shield)") }
                assertEquals(ArchiveGuardian.GROUNDED_LATER.toLong(), starts("shield").last() - starts("grounded").last())

                // Down a third time and taken to 30%: 4 ticks later it is in the air for good, with no shield.
                alice.commandReply("hit 550 ${boss.index}") { it.startsWith("Hit ") }
                alice.awaitFight("its third time down") { it.contains("(grounded)") }
                alice.commandReply("hit 670 ${boss.index}") { it.contains("330/1100 hp") }
                val low = fight.ticks
                val air = alice.awaitFight("the airborne phase") { it.contains("(airborne)") }
                assertTrue(air.contains("Archive Guardian 330/1100 hp"), air)
                assertTrue(starts("airborne").single() - low in ArchiveGuardian.AIRBORNE_DELAY - 1L..ArchiveGuardian.AIRBORNE_DELAY + 1L, "${starts("airborne")} after $low")
            }
        }
    }

    @Test
    fun `raid clear on the Guardian before it is touched gives its room's points to whoever stands in its hall`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                alice.tele(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z)
                alice.locOp(ArchiveRaids.ARCHIVE_DOOR, 4123, 5562, ArchiveRaids.ENTER)
                alice.await("into the instance") { alice.position?.takeIf { server.world.instances.inDynamicArea(it.x, it.z) } }
                alice.commandReply("raid tp guardian") { it.startsWith("To ") }
                alice.await("the waiting Guardian in view") { alice.npcs().firstOrNull { it.id == ArchiveGuardian.DORMANT } }
                // As the VM's flow script does (2026-11-03): before, the room was done with nobody's points.
                assertEquals("Cleared Archive Guardian's room.", alice.commandReply("raid clear") { it.startsWith("Cleared") || it.startsWith("You") })
                val run = alice.commandReply("raid run") { it.startsWith("Archive run") }
                assertTrue(run.contains("done Archive Guardian;") && run.contains("points alice ${ArchiveRaids.ROOM_POINTS}"), run)
                alice.await("the waiting Guardian gone") { alice.npcs().none { it.id == ArchiveGuardian.DORMANT }.takeIf { it } }
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
                    party(alice, bob)
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
    fun `a laser that kills a player whose Retribution kills the boss lands on nobody after them`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    for (client in listOf(alice, bob)) client.await("the login rebuild") { client.position }
                    // Alice on 10 hitpoints, with Retribution (prayer 16) at 99 Prayer: up to 24 back on her death.
                    alice.commandReply("setlevel prayer 99") { it.contains("Prayer") }
                    alice.commandReply("heal") { it.startsWith("Healed") }
                    alice.await("99 prayer points") { alice.stat(PRAYER)?.takeIf { it.level == 99 } }
                    alice.ifButton(Prayers.PRAYERBOOK, RETRIBUTION, 1)
                    alice.await("Retribution on") { if (alice.varp(Prayers.VARP_PRAYERS) == 1 shl 15) Unit else null }
                    bob.commandReply("setlevel hitpoints 99") { it.contains("99") || it.startsWith("Usage") }
                    bob.commandReply("heal") { it.startsWith("Healed") }
                    party(alice, bob)
                    // Both on the boss's south-west tile, so both are on the line, Alice first.
                    bob.tele(3222, 3218)
                    alice.tele(3222, 3218)
                    alice.commandReply("raid guardian phase=airborne seed=$SEED") { it.startsWith("Started") }
                    val boss = server.world.npcs.all.single { it.type.id == ArchiveGuardian.FLYING }
                    val fight = server.world.encounters.all.single { it.boss === boss }
                    alice.commandReply("hit 329 ${boss.index}") { it.startsWith("Hit ") }
                    alice.await("the Guardian dead") { if (boss.dead) Unit else null }
                    // The fight ended with the boss's death, on the tick the laser landed, so its log is whole: read
                    // from the server, as Alice, dead, is in no fight for ::raid log.
                    val laser = fight.lines.toList().filter { it.contains("laser hits") }.map { it.substringAfter(' ') }
                    assertEquals(listOf("airborne: laser hits alice for 10"), laser)
                    assertEquals(99, server.world.playerNamed("bob")!!.hitpoints)
                }
            }
        }
    }

    @Test
    fun `when its tank leaves the hall it takes on someone still in the fight and keeps to them`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    for (client in listOf(alice, bob)) {
                        client.await("the login rebuild") { client.position }
                        client.commandReply("god on") { it.startsWith("You can't be hurt") }
                    }
                    alice.commandReply("raidparty invite bob") { it.startsWith("Invited") }
                    bob.commandReply("raidparty accept alice") { it.startsWith("You joined") }
                    for (client in listOf(alice, bob)) {
                        client.tele(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z)
                        client.locOp(ArchiveRaids.ARCHIVE_DOOR, 4123, 5562, ArchiveRaids.ENTER)
                        client.await("into the instance") { client.position?.takeIf { server.world.instances.inDynamicArea(it.x, it.z) } }
                        client.commandReply("raid tp guardian") { it.startsWith("To ") }
                    }
                    val raids = server.world.archiveRaids
                    val run: ArchiveRun = assertNotNull(server.world.parties.of(server.world.playerNamed("alice")!!)?.let(raids::of))
                    val waiting = alice.await("the waiting Guardian in view") { alice.npcs().firstOrNull { it.id == ArchiveGuardian.DORMANT } }
                    alice.npcOp(waiting.index, ArchiveGuardian.TOUCH)
                    val boss = alice.await("the Guardian awake") { run.guardian?.takeIf { it.target != null } }
                    val first = assertNotNull(boss.target)
                    val (gone, stays) = if (first.username == "alice") alice to bob else bob to alice
                    val other = if (stays === alice) "alice" else "bob"
                    // Out of the hall into the nexus's entrance, still well within its reach.
                    val entrance = raids.tile(run, ArchiveRaids.GUARDIAN_X + 2, ArchiveRaids.HALL_Z - 2)
                    gone.tele(entrance.x, entrance.z)
                    // The fight's log, from the one still in it.
                    stays.await("two shots at $other") {
                        stays.fightLog().filter { it.endsWith("shield: shoots $other") }.takeIf { it.size >= 2 }
                    }
                    assertEquals(other, boss.target?.username)
                    assertEquals(1, stays.fightLog().count { it.endsWith("shield: takes on $other") }, "taken on once, then kept")
                }
            }
        }
    }

    @Test
    fun `levitating, it holds its fire at a tank out of its range, and shoots them back in it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                alice.commandReply("god on") { it.startsWith("You can't be hurt") }
                alice.tele(3222, 3218)
                alice.commandReply("raid guardian") { it.startsWith("Started") }
                // 15 tiles east of its east edge (3226): in the fight, past its range of 10.
                alice.tele(3241, 3220)
                alice.await("a held shot") { alice.fightLog().takeIf { lines -> lines.any { it.endsWith("shield: can't reach alice") } } }
                val held = alice.fightLog().count { it.endsWith("shield: shoots alice") }
                alice.awaitTicks(6)
                assertEquals(held, alice.fightLog().count { it.endsWith("shield: shoots alice") }, "no shot while out of range")
                // Back within 10 tiles, it shoots again.
                alice.tele(3232, 3220)
                alice.await("a shot") { alice.fightLog().count { it.endsWith("shield: shoots alice") }.takeIf { it > held } }
            }
        }
    }

    @Test
    fun `a new mushroom never grows on a tile whose last one hasn't burst yet`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                alice.commandReply("god on") { it.startsWith("You can't be hurt") }
                alice.tele(3222, 3218)
                // Seed 6 put the second wave's mushroom on the first's tile while that one waited out its fuse (exploit
                // review round 14): the first's burst then cleared the tile, hiding the second while it still went off.
                alice.commandReply("raid guardian seed=6") { it.startsWith("Started") }
                alice.tele(3241, 3220)
                val two = alice.await("two mushrooms") { alice.fightLog().mapNotNull { MUSHROOM_AT.find(it) }.takeIf { it.size >= 2 } }
                assertNotEquals(two[0].value, two[1].value, "the second on another tile")
                val (x, z) = two[1].destructured
                alice.await("the second ready") { server.world.locs.find(ArchiveGuardian.MUSHROOM_ACTIVE, x.toInt(), z.toInt(), 0) }
            }
        }
    }

    @Test
    fun `a mushroom grows by each player and bursts when someone comes within a tile, poisoning and wearying them, and splashes those two off`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                alice.commandReply("setlevel hitpoints 99") { it.contains("99") || it.startsWith("Usage") }
                alice.commandReply("heal") { it.startsWith("Healed") }
                val player = server.world.playerNamed("alice")!!
                alice.tele(3222, 3218)
                alice.commandReply("raid guardian") { it.startsWith("Started") }
                // Out of its range, so it holds its fire, and still in the fight.
                alice.tele(3241, 3220)
                val grown = alice.await("a mushroom") { alice.fightLog().firstNotNullOfOrNull { MUSHROOM_AT.find(it) } }
                val (mx, mz) = grown.destructured.let { (x, z) -> x.toInt() to z.toInt() }
                assertEquals(2, maxOf(kotlin.math.abs(mx - 3241), kotlin.math.abs(mz - 3220)), "two tiles from her")
                alice.await("the mushroom ready") { server.world.locs.find(ArchiveGuardian.MUSHROOM_ACTIVE, mx, mz, 0) }
                player.runEnabled = true
                // Paused and stepped, so the weariness is counted in game ticks. Beside it: it bursts a tick after.
                alice.commandReply("pause") { it.startsWith("Paused after tick") }
                val beside = if (mx > 3241) mx - 1 else mx + 1
                alice.command("tele $beside $mz 0")
                val fight = server.world.encounters.all.single()
                var stepped = 0
                while (fight.lines.none { BURST.containsMatchIn(it) }) {
                    assertTrue(++stepped <= 4, "no burst after $stepped ticks: ${fight.lines.takeLast(5)}")
                    alice.stepTicks(1)
                }
                val burst = fight.lines.firstNotNullOf { BURST.find(it)?.groupValues?.get(1)?.toInt() }
                assertTrue(burst in ArchiveGuardian.MUSHROOM_MIN..ArchiveGuardian.MUSHROOM_MAX, "burst for $burst")
                assertTrue(server.world.poison.value(player) > 0, "poisoned")
                assertFalse(player.runEnabled, "run off")
                // Weary for the burst's tick's movement and the nine after it: ten in all.
                assertEquals(ArchiveGuardian.WEARY_TICKS, player.wearyTicks)
                alice.stepTicks(ArchiveGuardian.WEARY_TICKS - 1)
                assertEquals(1, player.wearyTicks, "still weary on the tenth tick")
                alice.stepTicks(1)
                assertEquals(0, player.wearyTicks)
                alice.commandReply("resume") { it.startsWith("Resumed") }
                alice.await("the mushroom gone") { if (server.world.locs.at(mx, mz, 0).none { it.id in MUSHROOMS }) Unit else null }

                // The weariness wears off, counted down by the world, before the poison has hit or weakened; the next
                // mushroom wearies her again though the poison is no stronger.
                alice.await("the weariness gone") { if (player.wearyTicks == 0) Unit else null }
                val second = alice.await("a second mushroom") {
                    alice.fightLog().mapNotNull { MUSHROOM_AT.find(it) }.getOrNull(1)
                }
                val (sx, sz) = second.destructured.let { (x, z) -> x.toInt() to z.toInt() }
                alice.await("the second mushroom ready") { server.world.locs.find(ArchiveGuardian.MUSHROOM_ACTIVE, sx, sz, 0) }
                assertEquals(ArchiveGuardian.MUSHROOM_POISON, server.world.poison.value(player), "still poisoned as strongly")
                val (px, pz) = player.x to player.z
                // A tile from it, on her side.
                alice.tele(sx - (sx - px).sign, sz - (sz - pz).sign)
                alice.await("the second burst") { if (alice.fightLog().count { BURST.containsMatchIn(it) } >= 2) Unit else null }
                assertTrue(player.wearyTicks > 0, "weary again")

                // The next one, left alone, bursts by itself, splashing her two tiles off.
                val splash = alice.await("a splash", timeoutMillis = 30_000) {
                    alice.fightLog().firstNotNullOfOrNull { SPLASH.find(it)?.groupValues?.get(1)?.toInt() }
                }
                assertTrue(splash in ArchiveGuardian.SPLASH_MIN..ArchiveGuardian.SPLASH_MAX, "splash for $splash")
            }
        }
    }

    @Test
    fun `with its tank out of its shot it turns on someone in it, so a tank hiding in the hall can't keep it off the rest`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    for (client in listOf(alice, bob)) {
                        client.await("the login rebuild") { client.position }
                        client.commandReply("setlevel hitpoints 99") { it.contains("99") || it.startsWith("Usage") }
                        client.commandReply("heal") { it.startsWith("Healed") }
                    }
                    party(alice, bob)
                    // Bob outside the fight's 32 tiles as it starts, so Alice is its tank.
                    bob.tele(3260, 3218)
                    alice.tele(3222, 3218)
                    alice.commandReply("raid guardian") { it.startsWith("Started") }
                    val fight = server.world.encounters.all.single()
                    val aliceP = server.world.playerNamed("alice")!!
                    val bobP = server.world.playerNamed("bob")!!
                    alice.await("Alice its tank") { fight.boss.target?.takeIf { it === aliceP } }
                    // Alice 12 tiles east of its east edge: out of its range of 10, still close enough that it keeps her.
                    // Bob 3 off its east edge, in its range.
                    alice.tele(3238, 3220)
                    bob.tele(3229, 3220)
                    alice.await("the turn") { fight.lines.toList().firstOrNull { it.endsWith("shield: turns from alice to bob") } }
                    alice.await("a shot at Bob", timeoutMillis = 15_000) { fight.lines.toList().firstOrNull { it.endsWith("shield: shoots bob") } }
                    assertSame(bobP, fight.boss.target)
                    // With nobody in its shot, it holds its fire on the one it has.
                    bob.tele(3238, 3216)
                    alice.await("holding its fire", timeoutMillis = 15_000) { fight.lines.toList().firstOrNull { it.endsWith("shield: can't reach bob") } }
                }
            }
        }
    }

    @Test
    fun `four inert guardians stand round it, three awake while its shield is up, each one charged taking 40% off the shield, and three ground it`(@TempDir dir: Path) {
        server(dir, batteries = true).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                alice.commandReply("setlevel hitpoints 99") { it.contains("99") || it.startsWith("Usage") }
                alice.commandReply("heal") { it.startsWith("Healed") }
                alice.tele(3222, 3218)
                alice.commandReply("raid guardian") { it.startsWith("Started") }
                // Out of its range, so it holds its fire.
                alice.tele(3241, 3220)
                val fight = server.world.encounters.all.single()
                val boss = fight.boss
                val batteries = fight.adds.filter { it.spawn.type.id == ArchiveGuardian.BATTERY_INACTIVE }
                // Its middle is 3224,3220: NW, NE, SW, SE.
                assertEquals(listOf(3215 to 3221, 3232 to 3221, 3215 to 3211, 3232 to 3211), batteries.map { it.x to it.z })
                assertEquals(
                    listOf(ArchiveGuardian.BATTERY_ACTIVE, ArchiveGuardian.BATTERY_ACTIVE, ArchiveGuardian.BATTERY_ACTIVE, ArchiveGuardian.BATTERY_INACTIVE),
                    batteries.map { it.type.id },
                )
                // Each with the awake form's 180 hitpoints, though put in asleep (Codex on #481: asleep, it has the default).
                assertEquals(listOf(180, 180, 180, 180), batteries.map { it.hitpoints })
                assertEquals(listOf(180, 180, 180, 180), batteries.map { it.maxHitpoints })
                val shield = boss.maxHitpoints
                assertEquals(550, shield, "a party of one's shield")
                val (nw, ne, sw, se) = batteries

                // The south-east one is asleep: beaten to 0, it charges nothing.
                alice.commandReply("hit 180 ${se.index}") { it.startsWith("Hit ") }
                val from = fight.processedTicks
                alice.await("two of the fight's ticks") { if (fight.processedTicks >= from + 2) Unit else null }
                assertEquals(shield, boss.hitpoints, "the south-east one charged nothing")
                assertEquals(ArchiveGuardian.BATTERY_INACTIVE, se.type.id)

                alice.commandReply("hit 180 ${nw.index}") { it.startsWith("Hit ") }
                val share = shield * ArchiveGuardian.BATTERY_SHIELD_PERCENT / 100
                alice.await("the north-west one charged") {
                    fight.lines.toList().firstOrNull { it.endsWith("shield: inert guardian at 3215,3221 charged: shield takes $share") }
                }
                assertEquals(shield - share, boss.hitpoints)
                assertEquals(ArchiveGuardian.BATTERY_SPENT, nw.type.id)
                assertFalse(nw.dead, "spent, not dead")

                alice.commandReply("hit 180 ${ne.index}") { it.startsWith("Hit ") }
                alice.await("the north-east one charged") { if (ne.type.id == ArchiveGuardian.BATTERY_SPENT) Unit else null }
                assertEquals(shield - 2 * share, boss.hitpoints)
                // The third takes the rest of the shield (220 of 110 left): it falls to the ground.
                alice.commandReply("hit 180 ${sw.index}") { it.startsWith("Hit ") }
                alice.await("grounded") { fight.lines.toList().firstOrNull { it.contains("grounded: phase starts") } }
                assertTrue(fight.lines.any { it.endsWith("shield: inert guardian at 3215,3211 charged: shield takes 110") }, fight.lines.takeLast(8).toString())
                assertEquals(
                    listOf(ArchiveGuardian.BATTERY_SPENT, ArchiveGuardian.BATTERY_SPENT, ArchiveGuardian.BATTERY_SPENT, ArchiveGuardian.BATTERY_INACTIVE),
                    batteries.map { it.type.id },
                )
                server.world.encounters.stop(fight)
                alice.await("the inert guardians gone with the fight") { if (batteries.none { server.world.npcs[it.index] === it }) Unit else null }
            }
        }
    }

    @Test
    fun `airborne, its laser breaks the inert guardian behind its target, halving the laser and hurting it, but never into kill range`(@TempDir dir: Path) {
        server(dir, batteries = true).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                alice.commandReply("setlevel hitpoints 99") { it.contains("99") || it.startsWith("Usage") }
                alice.commandReply("heal") { it.startsWith("Healed") }
                alice.tele(3222, 3218)
                alice.commandReply("raid guardian phase=airborne") { it.startsWith("Started") }
                val fight = server.world.encounters.all.single()
                val boss = fight.boss
                val (nw, ne) = fight.adds.filter { it.spawn.type.id == ArchiveGuardian.BATTERY_INACTIVE }
                assertEquals(ArchiveGuardian.BATTERY_INACTIVE, ne.type.id, "asleep while it flies, but there for the laser")
                // From its middle, 3224,3220, through her at 3228,3221 the line crosses 3232,3222: the north-east one.
                alice.tele(3228, 3221)
                val most = boss.maxHitpoints
                assertEquals(330, boss.hitpoints, "30% of a party of one's 1,100")
                val first = alice.await("the laser on the north-east one", timeoutMillis = 30_000) {
                    fight.lines.toList().firstOrNull { it.contains("laser breaks the inert guardian at 3232,3221") }
                }
                assertTrue(first.endsWith("Guardian takes ${most * ArchiveGuardian.BATTERY_BEAM_PERCENT / 100}"), first)
                val hit = alice.await("the laser on her") { fight.lines.toList().firstNotNullOfOrNull { LASER_HIT.find(it)?.groupValues?.get(1)?.toInt() } }
                assertEquals(ArchiveGuardian.laserDamage(1) * (100 - ArchiveGuardian.BATTERY_LASER_CUT_PERCENT) / 100, hit, "the laser halved")
                assertEquals(330 - 110, boss.hitpoints)
                assertEquals(ArchiveGuardian.BATTERY_SPENT, ne.type.id)

                // Near kill range (10%, 110), the next one takes it only down to there: 120 left, 10 taken.
                alice.commandReply("hit 100 ${boss.index}") { it.startsWith("Hit ") }
                assertEquals(120, boss.hitpoints)
                alice.commandReply("heal") { it.startsWith("Healed") }
                // Through her at 3220,3221 the line crosses 3216,3222: the north-west one.
                alice.tele(3220, 3221)
                val second = alice.await("the laser on the north-west one", timeoutMillis = 40_000) {
                    fight.lines.toList().firstOrNull { it.contains("laser breaks the inert guardian at 3215,3221") }
                }
                assertTrue(second.endsWith("Guardian takes 10"), second)
                assertEquals(most * ArchiveGuardian.KILL_RANGE_PERCENT / 100, boss.hitpoints)
                assertEquals(ArchiveGuardian.BATTERY_SPENT, nw.type.id)
            }
        }
    }

    @Test
    fun `a player attacking an inert guardian stops once it is charged, as it can't be attacked any more`(@TempDir dir: Path) {
        server(dir, batteries = true).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    for (client in listOf(alice, bob)) {
                        client.await("the login rebuild") { client.position }
                        client.commandReply("god on") { it.startsWith("You can't be hurt") }
                    }
                    alice.tele(3222, 3218)
                    alice.commandReply("raid guardian") { it.startsWith("Started") }
                    // Out of its range, so it holds its fire at her.
                    alice.tele(3241, 3220)
                    val fight = server.world.encounters.all.single()
                    val nw = fight.adds.single { it.spawn.type.id == ArchiveGuardian.BATTERY_INACTIVE && it.x == 3215 && it.z == 3221 }
                    assertEquals(ArchiveGuardian.BATTERY_ACTIVE, nw.type.id)
                    // Bob, not in her party so not in the fight (nothing shoots at him), punches its last hitpoint away.
                    bob.commandReply("setlevel attack 99") { it.contains("Attack") }
                    bob.commandReply("setlevel strength 99") { it.contains("Strength") }
                    alice.commandReply("npchp 1 ${nw.index}") { it.contains("hitpoints") }
                    bob.tele(3214, 3221)
                    bob.await("the inert guardian in view") { bob.npcs().firstOrNull { it.index == nw.index } }
                    val bobP = server.world.playerNamed("bob")!!
                    bob.npcOp(nw.index, ATTACK)
                    bob.await("his first punch") { bobP.attackedNpc?.takeIf { it === nw } }
                    bob.await("it charged") { if (nw.type.id == ArchiveGuardian.BATTERY_SPENT) Unit else null }
                    // Before (Codex on #481): it held at 0 and he kept on at it, as a ranger would with their arrows.
                    bob.await("his attack stopped") { if (bobP.interaction == null) Unit else null }
                }
            }
        }
    }

    @Test
    fun `a laser at a target past its reach breaks no inert guardian, as none is behind them`(@TempDir dir: Path) {
        server(dir, batteries = true).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                alice.commandReply("god on") { it.startsWith("You can't be hurt") }
                // Its middle at 3256,3220, the north-west inert guardian on 3247..3248,3221..3222. Alice 33 tiles west, past
                // the laser's 32: its line runs through that guardian, which is between them, not behind her (Codex on #481).
                alice.tele(3254, 3218)
                alice.commandReply("raid guardian phase=airborne") { it.startsWith("Started") }
                alice.tele(3223, 3228)
                val fight = server.world.encounters.all.single()
                val nw = fight.adds.single { it.spawn.type.id == ArchiveGuardian.BATTERY_INACTIVE && it.x == 3247 && it.z == 3221 }
                assertTrue(Tile(3248, 3222) in ArchiveGuardian.line(fight.boss.x, fight.boss.z, fight.boss.type.size, Tile(3223, 3228)))
                alice.await("the laser on her") { fight.lines.toList().firstOrNull { it.contains("laser hits alice") } }
                assertTrue(fight.lines.none { it.contains("laser breaks") }, fight.lines.joinToString("\n"))
                assertEquals(ArchiveGuardian.BATTERY_INACTIVE, nw.type.id)
            }
        }
    }

    @Test
    fun `grounded, it sends a vine up beside each player away from it, swiping round it three times, for 11 at most under Protect from Melee`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                alice.commandReply("setlevel hitpoints 99") { it.contains("99") || it.startsWith("Usage") }
                alice.commandReply("setlevel prayer 99") { it.contains("Prayer") }
                alice.commandReply("heal") { it.startsWith("Healed") }
                alice.await("99 prayer points") { alice.stat(PRAYER)?.takeIf { it.level == 99 } }
                alice.ifButton(Prayers.PRAYERBOOK, PROTECT_FROM_MELEE, 1)
                alice.await("Protect from Melee on") { if (alice.varp(Prayers.VARP_PRAYERS) == 1 shl 14) Unit else null }
                alice.tele(3222, 3218)
                alice.commandReply("raid guardian phase=grounded") { it.startsWith("Started") }
                // Three tiles east of its east edge (3226), level with its middle: the vine comes up a tile further east.
                alice.tele(3229, 3220)
                alice.await("a vine") { alice.fightLog().takeIf { lines -> lines.any { it.endsWith("grounded: add Vine at 3230,3220") } } }
                val hits = alice.await("six swipes on her") {
                    alice.fightLog().mapNotNull { VINE_HIT.find(it)?.groupValues?.get(1)?.toInt() }.takeIf { it.size >= 6 }
                }
                assertTrue(hits.all { it in 0..ArchiveGuardian.VINE_PROTECTED_MAX }, hits.toString())
                // Each vine goes after its third swipe: never more than the three that overlap.
                val fight = server.world.encounters.all.single()
                assertTrue(fight.adds.size <= ArchiveGuardian.VINE_SWIPES + 2, "${fight.adds.size} vines up")
            }
        }
    }

    @Test
    fun `outside an instance its fight takes in only the starter's party, not whoever else stands near`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    for (client in listOf(alice, bob)) {
                        client.await("the login rebuild") { client.position }
                        client.commandReply("god on") { it.startsWith("You can't be hurt") }
                    }
                    // As on the VMs (2026-11-03): another account in Lumbridge, beside the boss but not in the party.
                    bob.tele(3221, 3217)
                    alice.tele(3222, 3218)
                    alice.commandReply("raid guardian") { it.startsWith("Started") }
                    val fight = server.world.encounters.all.single()
                    val bobP = server.world.playerNamed("bob")!!
                    assertEquals(listOf("alice"), fight.players.map { it.username })
                    assertTrue(alice.fight().contains("; 1 players"), alice.fight())
                    // In her party, he is in it.
                    party(alice, bob)
                    alice.await("Bob in the fight") { fight.players.takeIf { bobP in it } }
                    assertTrue(alice.fight().contains("; 2 players"), alice.fight())
                }
            }
        }
    }

    @Test
    fun `outside an instance its fight ends when its starter dies or logs out, so they don't come back into it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                // On 10 hitpoints beside it: its first shot kills her. Before (nightly 2026-11-08), she came back at
                // Lumbridge in its reach and was killed again and again.
                alice.tele(3222, 3218)
                alice.commandReply("raid guardian") { it.startsWith("Started") }
                val fight = server.world.encounters.all.single()
                val boss = fight.boss
                val player = server.world.playerNamed("alice")!!
                alice.await("her death") { if (player.dead) Unit else null }
                alice.await("the fight called off") { if (fight.ended) Unit else null }
                assertTrue(fight.lines.any { it.endsWith("ends: alice died") }, fight.lines.joinToString("\n"))
                assertTrue(server.world.encounters.all.isEmpty())
                assertTrue(server.world.npcs[boss.index] !== boss, "its boss taken out")
                alice.await("her back up") { if (!player.dead) Unit else null }

                // Bob starts one and his client goes: once the world lets him go, the fight goes with him. Stepped by
                // game ticks, as his connection is kept for a reconnect that long.
                val bob = server.login("bob")
                bob.await("the login rebuild") { bob.position }
                bob.commandReply("god on") { it.startsWith("You can't be hurt") }
                bob.tele(3222, 3218)
                bob.commandReply("raid guardian") { it.startsWith("Started") }
                val second = server.world.encounters.all.single()
                alice.commandReply("pause") { it.startsWith("Paused after tick") }
                bob.close()
                alice.stepTicks(World.LINGER_TICKS.toInt() + 5)
                assertTrue(second.ended, "the fight called off as he left")
                assertTrue(second.lines.any { it.endsWith("ends: bob left") }, second.lines.joinToString("\n"))
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
        val MUSHROOM_AT = Regex("mushroom at (\\d+),(\\d+) by alice")
        val LASER_HIT = Regex("laser hits alice for (\\d+)")
        val BURST = Regex("mushroom bursts on alice for (\\d+)")
        val SPLASH = Regex("mushroom splashes alice for (\\d+)")
        val VINE_HIT = Regex("vine hits alice for (\\d+)")
        const val HALBERD = 3190

        /** prayerbook:prayer1 is 541:9, so Retribution, prayer 16, is 24. */
        const val RETRIBUTION = 24
        const val RETRIBUTION_OBJ = 20818
        const val PROTECT_FROM_MELEE = 23
        const val PROTECT_FROM_MELEE_OBJ = 20817
        val MUSHROOMS = setOf(ArchiveGuardian.MUSHROOM_SPAWN, ArchiveGuardian.MUSHROOM_ACTIVE, ArchiveGuardian.MUSHROOM_DESPAWN)
        const val PRAYER = 5

        /** A seed whose Retribution roll on Alice's death is high enough to kill a Guardian on 1 hitpoint. */
        const val SEED = 7L
        const val ATTACK = 2
        const val BACKPACK = 93
        const val WORN = 94
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
        const val WIELD = 2
        val IOP_BUTTON_OPS = intArrayOf(2, 3, 4, 6, 7)
    }
}
