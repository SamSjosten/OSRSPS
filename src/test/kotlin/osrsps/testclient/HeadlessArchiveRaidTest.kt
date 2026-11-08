package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.combat.Deaths
import osrsps.game.combat.Spells
import osrsps.game.content.HomeTeleport
import osrsps.game.content.LodestoneMap
import osrsps.game.content.Lodestones
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.raid.ArchiveRaids
import osrsps.game.raid.ArchiveRoom
import osrsps.game.raid.ArchiveRun
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import osrsps.map.Tile

/**
 * Raid step 15 through the headless client, on the raid's map squares with its doors, Passageways and transporters
 * where the 241.4 cache has them (and open floor round them): a run through the empty rooms by the west doors and one
 * by the east doors, rows that open in order and a row's other door shut once one is chosen, a death that leaves the
 * party standing, and the wipe back to the lobby with gravestones. The rl-client script
 * test-runner/examples/archive-raid-flow.json does the runs in the real client.
 */
class HeadlessArchiveRaidTest {
    private fun type(id: Int, name: String, width: Int, length: Int, vararg ops: String?) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = width, length = length))

    private val locTypes = LocTypes(
        (
            listOf(
                type(ArchiveRaids.ARCHIVE_DOOR, "Archive Door", 9, 1, "Enter"),
                type(ArchiveRaids.PASSAGEWAY, "Passageway", 5, 2, "Exit"),
                type(ArchiveRaids.NEXUS_PASSAGEWAY, "Passageway", 7, 2, "Exit"),
            ) + ArchiveRaids.DOORS.map { type(it, "Door", 5, 1, "Enter", "Quick-enter") } +
                ArchiveRaids.TRANSPORTERS.map { type(it, "Archive transporter", 1, 1, "Use", "Quick-use") }
            ).associateBy { it.id },
    )

    /** Each room's Passageway and transporter, as the cache places them. */
    private val roomLocs = mapOf(
        ArchiveRoom.EZEKIEL to (Triple(4085, 5533, 3) to Tile(4073, 5535)),
        ArchiveRoom.BORK to (Triple(4164, 5597, 1) to Tile(4180, 5599)),
        ArchiveRoom.MOIA to (Triple(4086, 5661, 1) to Tile(4074, 5663)),
        ArchiveRoom.DREADNOTH to (Triple(4169, 5661, 1) to Tile(4177, 5663)),
        ArchiveRoom.DAGONHAI to (Triple(4088, 5597, 1) to Tile(4079, 5599)),
        ArchiveRoom.XARTH to (Triple(4169, 5533, 1) to Tile(4178, 5535)),
    )

    private fun doorX(room: ArchiveRoom) = if (room.side == osrsps.game.raid.DoorSide.WEST) 4113 else 4141

    private val staticLocs = StaticLocs().apply {
        add(ZoneLoc(ArchiveRaids.ARCHIVE_DOOR, LocShape.CENTREPIECE_STRAIGHT, 0, 4123, 5562, 0))
        for (level in listOf(0, 2, 3)) {
            for (room in ArchiveRoom.entries.filter { it.door >= 0 }) {
                add(ZoneLoc(room.door, LocShape.CENTREPIECE_STRAIGHT, if (room.side == osrsps.game.raid.DoorSide.WEST) 3 else 1, doorX(room), ArchiveRaids.doorZ(room.stage), level))
            }
        }
        add(ZoneLoc(ArchiveRaids.NEXUS_PASSAGEWAY, LocShape.CENTREPIECE_STRAIGHT, 0, 4124, 5625, 3))
        for ((room, locs) in roomLocs) {
            val (passage, transporter) = locs
            add(ZoneLoc(ArchiveRaids.PASSAGEWAY, LocShape.CENTREPIECE_STRAIGHT, passage.third, passage.first, passage.second, 0))
            add(ZoneLoc(room.transporter, LocShape.CENTREPIECE_STRAIGHT, 1, transporter.x, transporter.z, 0))
        }
    }

    private val axe = ObjType(1351, "Bronze axe", cost = 16, internalName = "bronze_axe")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            // Lumbridge's square too, for Home Teleport's landing.
            CollisionMap().apply {
                for (x in 63..65) for (z in 86..88) markLoaded(x, z)
                markLoaded(Lodestones.LUMBRIDGE.x shr 6, Lodestones.LUMBRIDGE.z shr 6)
            },
            locTypes = locTypes,
            staticLocs = staticLocs,
            objTypes = ObjTypes.of(axe),
            npcTypes = NpcTypes.of(NpcType(Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null))),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int = 0) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.awaitAt(tile: Tile, what: String) =
        assertEquals(Coord(tile.x, tile.z, 0), await(what) { position?.takeIf { it == Coord(tile.x, tile.z, 0) } }, what)

    private fun HeadlessClient.runSays(): String = commandReply("raid run") { it.startsWith("Archive run") || it.startsWith("You're not") }

    private fun HeadlessClient.awaitRun(what: String, predicate: (String) -> Boolean) {
        await(what) { runSays().takeIf(predicate) }
    }

    private fun TestServer.run(username: String): ArchiveRun =
        assertNotNull(world.parties.of(world.playerNamed(username)!!)?.let(world.archiveRaids::of), "$username's run")

    /** Through the Archive Door from the lobby, into the nexus's entrance. Returns the run. */
    private fun HeadlessClient.enter(server: TestServer, username: String): ArchiveRun {
        tele(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z)
        locOp(ArchiveRaids.ARCHIVE_DOOR, 4123, 5562, ArchiveRaids.ENTER)
        await("into the instance") { position?.takeIf { server.world.instances.inDynamicArea(it.x, it.z) } }
        val run = server.run(username)
        awaitAt(server.world.archiveRaids.tile(run, ArchiveRaids.NEXUS_X, ArchiveRaids.NEXUS_Z), "the nexus's entrance")
        return run
    }

    /** Through [room]'s nexus door, and back out by its transporter, or its Passageway when [passageway]. */
    private fun HeadlessClient.through(server: TestServer, run: ArchiveRun, room: ArchiveRoom, passageway: Boolean = false) {
        val raids = server.world.archiveRaids
        val door = raids.tile(run, doorX(room), ArchiveRaids.doorZ(room.stage))
        locOp(room.door, door.x, door.z, ArchiveRaids.ENTER)
        awaitAt(raids.tile(run, room.entryX, room.entryZ), "${room.boss}'s room")
        awaitRun("${room.boss}'s room done") { "${room.boss}'s".isNotEmpty() && it.contains(room.boss) && room in run.cleared }
        val (passage, transporter) = roomLocs.getValue(room)
        if (passageway) {
            val at = raids.tile(run, passage.first, passage.second)
            locOp(ArchiveRaids.PASSAGEWAY, at.x, at.z, ArchiveRaids.EXIT)
        } else {
            val at = raids.tile(run, transporter.x, transporter.z)
            locOp(room.transporter, at.x, at.z, ArchiveRaids.USE)
        }
        awaitAt(raids.tile(run, room.doorFront.x, room.doorFront.z), "out in front of ${room.boss}'s door")
    }

    /** The level of the cache's nexus the client draws the instance's nexus from. */
    private fun HeadlessClient.nexusLevel(server: TestServer, run: ArchiveRun): Int? {
        val hall = server.world.archiveRaids.tile(run, 4127, 5590)
        return regionZones()?.get(Triple(hall.x shr 3, hall.z shr 3, 0))?.level
    }

    private fun HeadlessClient.goThrough(server: TestServer, username: String, rooms: List<ArchiveRoom>, passagewayIn: ArchiveRoom? = null) {
        val run = enter(server, username)
        assertTrue(runSays().contains("next Archive Guardian"))
        assertEquals(ArchiveRaids.NEXUS_START, nexusLevel(server, run))
        // Into the hall: the Guardian's fight, done at once with no Guardian yet.
        command("raid tp guardian")
        awaitRun("the Guardian's room done") { ArchiveRoom.GUARDIAN in run.cleared }
        await("the nexus with the Guardian's corpse") { nexusLevel(server, run)?.takeIf { it == ArchiveRaids.NEXUS_CAMP } }
        for (room in rooms) through(server, run, room, passageway = room == passagewayIn)
        await("the nexus with its Passageway north") { nexusLevel(server, run)?.takeIf { it == ArchiveRaids.NEXUS_OCELLUS } }
        val passage = server.world.archiveRaids.tile(run, 4124, 5625)
        locOp(ArchiveRaids.NEXUS_PASSAGEWAY, passage.x, passage.z, ArchiveRaids.EXIT)
        awaitAt(server.world.archiveRaids.tile(run, ArchiveRoom.OCELLUS.entryX, ArchiveRoom.OCELLUS.entryZ), "Ocellus's room")
        awaitRun("the run complete") { it.contains("; complete;") }
        // Solo, every room's points are the one player's.
        assertTrue(runSays().contains("points $username ${ArchiveRaids.ROOM_POINTS * 5}"), runSays())
        // Out by teleport: the instance ends behind them.
        tele(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z)
        await("the instance ended") { if (server.world.instances.all.none { it === run.instance }) Unit else null }
    }

    @Test
    fun `a run goes through the empty rooms by the west doors, and another by the east doors`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.goThrough(server, "alice", listOf(ArchiveRoom.EZEKIEL, ArchiveRoom.MOIA, ArchiveRoom.DAGONHAI))
                client.goThrough(server, "alice", listOf(ArchiveRoom.BORK, ArchiveRoom.DREADNOTH, ArchiveRoom.XARTH), passagewayIn = ArchiveRoom.DREADNOTH)
            }
        }
    }

    @Test
    fun `a row opens once the room before is done, and its other door stays shut once one is chosen`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                val run = client.enter(server, "bob")
                val raids = server.world.archiveRaids
                // The first row before the Guardian is done: the door walked to, and nothing.
                assertEquals(ArchiveRaids.NEXUS_START, client.nexusLevel(server, run))
                val front = raids.tile(run, ArchiveRoom.EZEKIEL.doorFront.x, ArchiveRoom.EZEKIEL.doorFront.z)
                client.tele(front.x, front.z)
                // In the hall, so the Guardian's fight begins (and ends) first; then the row opens.
                client.awaitRun("the Guardian's room done") { ArchiveRoom.GUARDIAN in run.cleared }
                client.through(server, run, ArchiveRoom.EZEKIEL)
                // Bork's door, east across the row: walked to, then shut for the run.
                val bork = raids.tile(run, 4141, ArchiveRaids.doorZ(2))
                client.locOp(ArchiveRoom.BORK.door, bork.x, bork.z, ArchiveRaids.ENTER)
                client.await("at Bork's door") { client.position?.takeIf { it.x >= bork.x - 2 && it.z in bork.z..bork.z + 4 } }
                client.awaitTicks(3)
                assertEquals(null, raids.roomAt(run, client.position!!.x, client.position!!.z).takeIf { it != ArchiveRoom.GUARDIAN })
                // The third row isn't open yet either.
                val dagonhai = raids.tile(run, 4113, ArchiveRaids.doorZ(4))
                client.locOp(ArchiveRoom.DAGONHAI.door, dagonhai.x, dagonhai.z, ArchiveRaids.QUICK_ENTER)
                client.await("at the Dagon'hai door") { client.position?.takeIf { it.x <= dagonhai.x + 2 && it.z in dagonhai.z..dagonhai.z + 4 } }
                client.awaitTicks(3)
                assertEquals(ArchiveRoom.GUARDIAN, raids.roomAt(run, client.position!!.x, client.position!!.z), "still in the nexus's hall")
                // The second row is: Quick-enter goes straight in too.
                val moia = raids.tile(run, 4113, ArchiveRaids.doorZ(3))
                client.locOp(ArchiveRoom.MOIA.door, moia.x, moia.z, ArchiveRaids.QUICK_ENTER)
                client.awaitAt(raids.tile(run, ArchiveRoom.MOIA.entryX, ArchiveRoom.MOIA.entryZ), "Moia's room")
            }
        }
    }

    @Test
    fun `a death leaves the party standing, nobody comes in once the Guardian's fight began, and the last death wipes it to the lobby with graves`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { carol ->
                server.login("dave").use { dave ->
                    server.login("erin").use { erin ->
                        for (client in listOf(carol, dave, erin)) client.await("the login rebuild") { client.position }
                        carol.commandReply("raidparty invite dave") { it.startsWith("Invited") }
                        dave.commandReply("raidparty accept carol") { it.startsWith("You joined") }
                        carol.commandReply("raidparty invite erin") { it.startsWith("Invited") }
                        erin.commandReply("raidparty accept carol") { it.startsWith("You joined") }
                        val run = carol.enter(server, "carol")
                        assertEquals(run, dave.enter(server, "dave"))
                        val raids = server.world.archiveRaids
                        carol.command("raid tp guardian")
                        carol.awaitRun("the Guardian's room done") { ArchiveRoom.GUARDIAN in run.cleared }
                        // Erin is too late: the Archive Door leaves her in the lobby.
                        erin.tele(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z)
                        erin.locOp(ArchiveRaids.ARCHIVE_DOOR, 4123, 5562, ArchiveRaids.ENTER)
                        erin.await("Erin at the door") { erin.position?.takeIf { it.z == 5561 } }
                        erin.awaitTicks(3)
                        assertTrue(!server.world.instances.inDynamicArea(erin.position!!.x, erin.position!!.z), "Erin in the lobby")
                        // Five axes each: three are kept on a death that loses them.
                        for (client in listOf(carol, dave)) client.commandReply("item bronze axe 5") { it.contains("Bronze axe") }
                        // Carol dies with Dave standing: down, in the entrance, everything kept.
                        carol.command("hit 99 me")
                        carol.awaitAt(raids.tile(run, ArchiveRaids.NEXUS_X, ArchiveRaids.NEXUS_Z), "Carol down in the entrance")
                        carol.await("Carol's axes kept") { carol.inventory(INVENTORY).count { it?.id == axe.id }.takeIf { it == 5 } }
                        assertTrue(carol.runSays().contains("down: carol"), carol.runSays())
                        // Dave dies: nobody stands, so both go to the lobby, each with a grave of two axes there.
                        dave.command("hit 99 me")
                        val lobby = Tile(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z)
                        carol.awaitAt(lobby, "Carol wiped to the lobby")
                        dave.awaitAt(lobby, "Dave wiped to the lobby")
                        for ((client, name) in listOf(carol to "carol", dave to "dave")) {
                            client.await("$name's three axes") { client.inventory(INVENTORY).count { it?.id == axe.id }.takeIf { it == 3 } }
                            val grave = assertNotNull(server.world.deaths.graveOf(server.world.playerNamed(name)!!))
                            assertEquals(Triple(lobby.x, lobby.z, 0), Triple(grave.npc.x, grave.npc.z, grave.npc.level), "$name's grave")
                            assertEquals(2, grave.items.contents().sumOf { it.second.count })
                        }
                        // The party lasts through the wipe, and its next run starts afresh.
                        assertEquals(3, server.world.parties.of(server.world.playerNamed("carol")!!)?.size)
                        carol.await("the wiped instance ended") { if (server.world.instances.all.none { it === run.instance }) Unit else null }
                        val next = carol.enter(server, "carol")
                        assertTrue(next !== run && !next.wiped)
                        // A member who leaves the party is sent out of its run.
                        carol.commandReply("raidparty leave") { it.contains("left") || it.contains("Left") }
                        carol.await("Carol out of the run") { carol.position?.takeIf { it == Coord(lobby.x, lobby.z, 0) } }
                    }
                }
            }
        }
    }

    @Test
    fun `a member who falls as a room is done gets up standing, and deaths at once in a wipe each count`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { carol ->
                server.login("dave").use { dave ->
                    server.login("frank").use { frank ->
                        // Frank, out of the party, holds the clock: a dead player's commands wait for them to get up.
                        for (client in listOf(carol, dave, frank)) client.await("the login rebuild") { client.position }
                        carol.commandReply("raidparty invite dave") { it.startsWith("Invited") }
                        dave.commandReply("raidparty accept carol") { it.startsWith("You joined") }
                        val run = carol.enter(server, "carol")
                        assertEquals(run, dave.enter(server, "dave"))
                        val carolPlayer = server.world.playerNamed("carol")!!
                        // The clock held, Carol falls in the entrance and Dave goes into the hall: the Guardian's fight
                        // begins and is done on the next tick, while she lies dead.
                        frank.commandReply("pause") { it.contains("aused after tick") }
                        carol.commandReply("hit 99 me") { it.startsWith("Hit yourself") }
                        dave.commandReply("raid tp guardian") { it.startsWith("To ") }
                        frank.stepTicks(1)
                        assertTrue(ArchiveRoom.GUARDIAN in run.cleared, "the Guardian's room done")
                        assertTrue(carolPlayer.dead, "Carol still dead as it was done")
                        frank.commandReply("resume") { it.startsWith("Resumed") }
                        carol.await("Carol up") { if (carolPlayer.dead) null else Unit }
                        assertTrue(run.downed.isEmpty(), carol.runSays())
                        // Both fall before either gets up: the run wipes, and each death counts.
                        frank.commandReply("pause") { it.contains("aused after tick") }
                        carol.commandReply("hit 99 me") { it.startsWith("Hit yourself") }
                        dave.commandReply("hit 99 me") { it.startsWith("Hit yourself") }
                        frank.commandReply("resume") { it.startsWith("Resumed") }
                        val lobby = Tile(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z)
                        carol.awaitAt(lobby, "Carol wiped to the lobby")
                        dave.awaitAt(lobby, "Dave wiped to the lobby")
                        assertTrue(run.wiped)
                        assertEquals(mapOf("carol" to 2, "dave" to 1), run.deaths.mapKeys { (key, _) -> if (key == carolPlayer.saveKey) "carol" else "dave" })
                    }
                }
            }
        }
    }

    /** The logout button, and the server's logout. */
    private fun HeadlessClient.logout() {
        ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
        await("the logout") { packets().firstOrNull { it.prot.name == "LOGOUT" } }
    }

    /** [username] has a grave of [lost] axes in the lobby, and it is theirs alone. */
    private fun TestServer.assertGraveInLobby(saveKey: String, username: String, lost: Int) {
        val grave = assertNotNull(world.deaths.all.singleOrNull { it.owner == saveKey }, "$username's grave")
        assertEquals(Triple(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z, 0), Triple(grave.npc.x, grave.npc.z, grave.npc.level), "$username's grave in the lobby")
        assertEquals(lost, grave.items.contents().sumOf { it.second.count }, "$username's grave")
    }

    @Test
    fun `a member down who teleports out or leaves the party dies of it, with a grave in the lobby`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { carol ->
                server.login("dave").use { dave ->
                    server.login("erin").use { erin ->
                        for (client in listOf(carol, dave, erin)) client.await("the login rebuild") { client.position }
                        carol.commandReply("raidparty invite dave") { it.startsWith("Invited") }
                        dave.commandReply("raidparty accept carol") { it.startsWith("You joined") }
                        carol.commandReply("raidparty invite erin") { it.startsWith("Invited") }
                        erin.commandReply("raidparty accept carol") { it.startsWith("You joined") }
                        val run = carol.enter(server, "carol")
                        assertEquals(run, dave.enter(server, "dave"))
                        assertEquals(run, erin.enter(server, "erin"))
                        val raids = server.world.archiveRaids
                        val entrance = raids.tile(run, ArchiveRaids.NEXUS_X, ArchiveRaids.NEXUS_Z)
                        // Carol and Erin fall with Dave standing: down in the entrance, their five axes kept.
                        for ((client, name) in listOf(carol to "carol", erin to "erin")) {
                            client.commandReply("item bronze axe 5") { it.contains("Bronze axe") }
                            client.command("hit 99 me")
                            client.awaitAt(entrance, "$name down in the entrance")
                            val player = server.world.playerNamed(name)!!
                            client.await("$name down") { if (player.saveKey in run.downed && !player.dead) Unit else null }
                            client.await("$name's axes kept") { client.inventory(INVENTORY).count { it?.id == axe.id }.takeIf { it == 5 } }
                        }
                        val says = dave.runSays()
                        assertTrue(says.contains("down: carol, erin"), says)
                        val lobby = Tile(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z)
                        // Carol teleports out (as Home Teleport or a lodestone would), here to beside the lobby, the only
                        // map loaded: she dies of it, and comes back in the lobby with three axes and a grave of two there.
                        carol.command("tele ${ArchiveRaids.LOBBY_X + 3} ${ArchiveRaids.LOBBY_Z - 3} 0")
                        carol.awaitAt(lobby, "Carol dead of it, in the lobby")
                        carol.await("Carol's three axes") { carol.inventory(INVENTORY).count { it?.id == axe.id }.takeIf { it == 3 } }
                        server.assertGraveInLobby(server.world.playerNamed("carol")!!.saveKey, "carol", 2)
                        // Erin leaves the party: sent out, and dead of it the same way.
                        erin.commandReply("raidparty leave") { it.contains("left") || it.contains("Left") }
                        erin.awaitAt(lobby, "Erin dead of it, in the lobby")
                        erin.await("Erin's three axes") { erin.inventory(INVENTORY).count { it?.id == axe.id }.takeIf { it == 3 } }
                        server.assertGraveInLobby(server.world.playerNamed("erin")!!.saveKey, "erin", 2)
                        // Dave still stands: the run goes on, with nobody down, each escape a death.
                        assertTrue(!run.wiped, "the run goes on")
                        assertTrue(run.downed.isEmpty(), run.downed.toString())
                        assertEquals(mapOf("carol" to 2, "erin" to 2), run.deaths.mapKeys { (key, _) -> server.world.onlinePlayers.first { it.saveKey == key }.username })
                    }
                }
            }
        }
    }

    @Test
    fun `a member down who waits out the combat timer and logs out dies of it, and logs in again in the lobby`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { dave ->
                val carol = server.login("carol")
                val carolKey: String
                carol.use {
                    for (client in listOf(carol, dave)) client.await("the login rebuild") { client.position }
                    carol.commandReply("raidparty invite dave") { it.startsWith("Invited") }
                    dave.commandReply("raidparty accept carol") { it.startsWith("You joined") }
                    val run = carol.enter(server, "carol")
                    assertEquals(run, dave.enter(server, "dave"))
                    carolKey = server.world.playerNamed("carol")!!.saveKey
                    carol.commandReply("item bronze axe 5") { it.contains("Bronze axe") }
                    carol.command("hit 99 me")
                    carol.awaitAt(server.world.archiveRaids.tile(run, ArchiveRaids.NEXUS_X, ArchiveRaids.NEXUS_Z), "Carol down in the entrance")
                    val carolPlayer = server.world.playerNamed("carol")!!
                    carol.await("Carol down") { if (carolKey in run.downed && !carolPlayer.dead) Unit else null }
                    carol.await("Carol's axes kept") { carol.inventory(INVENTORY).count { it?.id == axe.id }.takeIf { it == 5 } }
                    // Out of combat once its timer runs out, she logs out.
                    carol.awaitTicks(World.LOGOUT_COMBAT_TICKS.toInt() + 1)
                    carol.logout()
                    dave.await("Carol gone") { if (server.world.playerNamed("carol") == null) Unit else null }
                    assertTrue(!run.wiped && run.downed.isEmpty(), run.downed.toString())
                    assertEquals(2, run.deaths[carolKey])
                }
                server.assertGraveInLobby(carolKey, "carol", 2)
                // Saved where the death left her: the lobby, with the three axes it kept.
                server.login("carol").use { again ->
                    again.awaitAt(Tile(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z), "Carol back in the lobby")
                    again.await("Carol's three axes") { again.inventory(INVENTORY).count { it?.id == axe.id }.takeIf { it == 3 } }
                }
            }
        }
    }

    /**
     * Home Teleport to Lumbridge's lodestone for each of [casters] at once, with the world paused by [clock]: the cast,
     * the map, the pick, held for the next tick. Returns once the picks are in, the world still paused.
     */
    private fun homeTeleportsPicked(clock: HeadlessClient, vararg casters: HeadlessClient) {
        for (caster in casters) caster.ifButton(Spells.SPELLBOOK, HomeTeleport.COMPONENT, HomeTeleport.CAST)
        for (caster in casters) caster.awaitCycles(2)
        clock.stepTicks(1)
        for (caster in casters) {
            caster.await("the lodestone map") {
                if (LodestoneMap.INTERFACE in caster.openInterfaces() && caster.opEnabled(LodestoneMap.INTERFACE, LodestoneMap.CLOSE, -1, LodestoneMap.CLOSE_OP)) Unit else null
            }
        }
        for (caster in casters) caster.ifButton(LodestoneMap.INTERFACE, LodestoneMap.row(Lodestones.LUMBRIDGE), LodestoneMap.TELEPORT)
        for (caster in casters) caster.awaitCycles(2)
    }

    private fun HeadlessClient.homeTeleportPicked(clock: HeadlessClient) = homeTeleportsPicked(clock, this)

    @Test
    fun `a member down who Home Teleports out on the tick the last one standing does still dies of it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("frank").use { frank ->
                server.login("dave").use { dave ->
                    server.login("carol").use { carol ->
                        // Frank, out of the party, holds the clock.
                        for (client in listOf(carol, dave, frank)) client.await("the login rebuild") { client.position }
                        carol.commandReply("raidparty invite dave") { it.startsWith("Invited") }
                        dave.commandReply("raidparty accept carol") { it.startsWith("You joined") }
                        val run = carol.enter(server, "carol")
                        assertEquals(run, dave.enter(server, "dave"))
                        val carolPlayer = server.world.playerNamed("carol")!!
                        carol.commandReply("item bronze axe 5") { it.contains("Bronze axe") }
                        carol.command("hit 99 me")
                        carol.await("Carol down") { if (carolPlayer.saveKey in run.downed && !carolPlayer.dead) Unit else null }
                        carol.awaitTicks(World.LOGOUT_COMBAT_TICKS.toInt() + 1)
                        // Both teleport out together: the instance is empty, and ends, before the next look for escapes.
                        frank.commandReply("pause") { it.contains("aused after tick") }
                        homeTeleportsPicked(frank, carol, dave)
                        var ticks = 0
                        while (run.instance.contains(carolPlayer.x, carolPlayer.z)) {
                            check(++ticks <= 60) { "no landing within 60 ticks of the picks" }
                            frank.stepTicks(1)
                        }
                        frank.stepTicks(2)
                        frank.commandReply("resume") { it.startsWith("Resumed") }
                        assertTrue(server.world.instances.all.none { it === run.instance }, "the run's instance ended")
                        carol.awaitAt(Tile(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z), "Carol dead of it, in the lobby")
                        carol.await("Carol's three axes") { carol.inventory(INVENTORY).count { it?.id == axe.id }.takeIf { it == 3 } }
                        server.assertGraveInLobby(carolPlayer.saveKey, "carol", 2)
                    }
                }
            }
        }
    }

    @Test
    fun `a member down who logs out on the tick their Home Teleport lands out of the run still dies of it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { dave ->
                server.login("carol").use { carol ->
                    for (client in listOf(carol, dave)) client.await("the login rebuild") { client.position }
                    val carolPlayer = server.world.playerNamed("carol")!!
                    val stone = Lodestones.LUMBRIDGE.let { Tile(it.x + 1, it.z + 1) }
                    fun landed() = carolPlayer.x == stone.x && carolPlayer.z == stone.z
                    // First, out of the raid: how many ticks after its pick the teleport lands, the world held by Dave.
                    carol.tele(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z)
                    dave.commandReply("pause") { it.contains("aused after tick") }
                    carol.homeTeleportPicked(dave)
                    var ticks = 0
                    while (!landed()) {
                        check(++ticks <= 60) { "no landing within 60 ticks of the pick" }
                        dave.stepTicks(1)
                    }
                    dave.commandReply("resume") { it.startsWith("Resumed") }
                    carol.commandReply("raidparty invite dave") { it.startsWith("Invited") }
                    dave.commandReply("raidparty accept carol") { it.startsWith("You joined") }
                    val run = carol.enter(server, "carol")
                    assertEquals(run, dave.enter(server, "dave"))
                    carol.commandReply("item bronze axe 5") { it.contains("Bronze axe") }
                    carol.command("hit 99 me")
                    carol.await("Carol down") { if (carolPlayer.saveKey in run.downed && !carolPlayer.dead) Unit else null }
                    // Out of combat, so she may cast and log out.
                    carol.awaitTicks(World.LOGOUT_COMBAT_TICKS.toInt() + 1)
                    val carolKey = carolPlayer.saveKey
                    dave.commandReply("pause") { it.contains("aused after tick") }
                    carol.homeTeleportPicked(dave)
                    dave.stepTicks(ticks - 1)
                    assertTrue(run.instance.contains(carolPlayer.x, carolPlayer.z), "still in the run the tick before the landing")
                    // Logout on the landing tick: the run's look for escapes that tick comes before the teleport.
                    carol.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                    carol.awaitCycles(2)
                    dave.stepTicks(1)
                    assertTrue(landed(), "landed on the logout's tick")
                    dave.await("Carol gone") { if (server.world.playerNamed("carol") == null) Unit else null }
                    dave.commandReply("resume") { it.startsWith("Resumed") }
                    server.assertGraveInLobby(carolKey, "carol", 2)
                    assertTrue(run.downed.isEmpty(), run.downed.toString())
                }
            }
        }
    }

    private companion object {
        const val INVENTORY = 93
    }
}
