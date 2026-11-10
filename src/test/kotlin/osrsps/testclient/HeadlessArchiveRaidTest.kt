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
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.combat.Deaths
import osrsps.game.combat.DropItem
import osrsps.game.combat.DropSlot
import osrsps.game.combat.DropTable
import osrsps.game.combat.Spells
import osrsps.game.content.HomeTeleport
import osrsps.game.content.LodestoneMap
import osrsps.game.content.Lodestones
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.raid.ArchiveChests
import osrsps.game.raid.ArchiveLoot
import osrsps.game.raid.ArchiveRaids
import osrsps.game.raid.ArchiveRoom
import osrsps.game.raid.ArchiveRun
import osrsps.game.trade.Trades
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
                type(ArchiveRaids.VAULT_PASSAGEWAY, "Passageway", 7, 2, "Exit"),
                type(ArchiveRaids.OCELLUS_DOOR_SHUT, "Archive Door", 7, 2),
                type(ArchiveRaids.OCELLUS_DOOR_OPEN, "Archive Door", 7, 2, "Enter", "Quick-enter"),
                type(ArchiveRaids.REWARD_RIFT, "Rift", 1, 1),
                type(ArchiveRaids.MERIT_SCROLL, "Merit scroll", 2, 1, "Read"),
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
        for (level in listOf(0, 2)) {
            for (room in ArchiveRoom.entries.filter { it.door >= 0 }) {
                add(ZoneLoc(room.door, LocShape.CENTREPIECE_STRAIGHT, if (room.side == osrsps.game.raid.DoorSide.WEST) 3 else 1, doorX(room), ArchiveRaids.doorZ(room.stage), level))
            }
        }
        // The vault's Passageway, across the south end of its corridor on the level above each of the nexus's.
        for (level in listOf(1, 3)) add(ZoneLoc(ArchiveRaids.VAULT_PASSAGEWAY, LocShape.CENTREPIECE_STRAIGHT, 0, 4124, 5625, level))
        for ((room, locs) in roomLocs) {
            val (passage, transporter) = locs
            add(ZoneLoc(ArchiveRaids.PASSAGEWAY, LocShape.CENTREPIECE_STRAIGHT, passage.third, passage.first, passage.second, 0))
            add(ZoneLoc(room.transporter, LocShape.CENTREPIECE_STRAIGHT, 1, transporter.x, transporter.z, 0))
        }
    }

    private val axe = ObjType(1351, "Bronze axe", cost = 16, internalName = "bronze_axe")
    private val pickaxe = ObjType(1265, "Bronze pickaxe", cost = 1, tradeable = true, iops = listOf(null, null, null, null, "Drop"), internalName = "bronze_pickaxe")

    /**
     * The reward vault's chests as the 241.4 cache has them: what one shows as, closed (Open) or the Rift it leaves
     * (Search), the player's own or not; and the eight, closed and open, each showing as the player's own with
     * `tfa_vault_own_chest` at its number and as another's otherwise.
     */
    private val chestTypes = listOf(
        NpcType(ArchiveChests.CLOSED_OTHERS, "Chest", ops = listOf("Open", null, null, null, null)),
        NpcType(ArchiveChests.CLOSED_OWN, "Chest", ops = listOf("Open", null, null, null, null)),
        NpcType(ArchiveChests.OPEN_OTHERS, "Rift", ops = listOf("Search", null, null, null, null)),
        NpcType(ArchiveChests.OPEN_OWN, "Rift", ops = listOf("Search", null, null, null, null)),
    ) + (0 until ArchiveChests.SLOTS.size).flatMap { slot ->
        listOf(false, true).map { open ->
            val (others, own) = if (open) ArchiveChests.OPEN_OTHERS to ArchiveChests.OPEN_OWN else ArchiveChests.CLOSED_OTHERS to ArchiveChests.CLOSED_OWN
            NpcType(ArchiveChests.chestType(slot, open), multiVarbit = ArchiveChests.OWN_CHEST, multiNpcs = IntArray(slot + 3) { if (it == slot + 1) own else others })
        }
    }

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            // Lumbridge's square too, for Home Teleport's landing.
            CollisionMap().apply {
                for (x in 63..65) for (z in 86..88) markLoaded(x, z)
                // The reward vault's square, two north of Ocellus's.
                markLoaded(ArchiveRaids.REWARD_SQUARE_X, ArchiveRaids.REWARD_SQUARE_Z)
                markLoaded(Lodestones.LUMBRIDGE.x shr 6, Lodestones.LUMBRIDGE.z shr 6)
            },
            locTypes = locTypes,
            staticLocs = staticLocs,
            objTypes = ObjTypes.of(axe, pickaxe),
            npcTypes = NpcTypes.of(
                NpcType(Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null)),
                // The reward vault's Rift out, as the 241.4 cache has it.
                NpcType(ArchiveRaids.RIFT_OUT, "Rift", size = 3, ops = listOf(ArchiveRaids.LEAVE, null, ArchiveRaids.QUICK_LEAVE, null, null)),
                *chestTypes.toTypedArray(),
            ),
            // tfa_vault_own_chest, where the 241.4 cache has it.
            varTypes = VarTypes(6_000, mapOf(ArchiveChests.OWN_CHEST to VarbitType(OWN_CHEST_VARP, 0, 3))),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int = 0) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.awaitAt(tile: Tile, what: String, level: Int = 0) =
        assertEquals(Coord(tile.x, tile.z, level), await(what) { position?.takeIf { it == Coord(tile.x, tile.z, level) } }, what)

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
        // Back onto the tile the door was gone through from when that is beside it, on its column; else the door's own.
        val back = run.leftFrom.entries.firstOrNull { it.key.second == room }?.value ?: room.doorFront
        awaitRun("${room.boss}'s room done") { "${room.boss}'s".isNotEmpty() && it.contains(room.boss) && room in run.cleared }
        val (passage, transporter) = roomLocs.getValue(room)
        if (passageway) {
            val at = raids.tile(run, passage.first, passage.second)
            locOp(ArchiveRaids.PASSAGEWAY, at.x, at.z, ArchiveRaids.EXIT)
        } else {
            val at = raids.tile(run, transporter.x, transporter.z)
            locOp(room.transporter, at.x, at.z, ArchiveRaids.USE)
        }
        awaitAt(raids.tile(run, back.x, back.z), "out beside ${room.boss}'s door")
    }

    /** The level of the cache's nexus the client draws the instance's nexus from, on the instance's [level]. */
    private fun HeadlessClient.nexusLevel(server: TestServer, run: ArchiveRun, level: Int = 0): Int? {
        val hall = server.world.archiveRaids.tile(run, 4127, 5590)
        return regionZones()?.get(Triple(hall.x shr 3, hall.z shr 3, level))?.level
    }

    private fun HeadlessClient.goThrough(server: TestServer, username: String, rooms: List<ArchiveRoom>, passagewayIn: ArchiveRoom? = null, outByExit: Boolean = false) {
        val run = enter(server, username)
        assertTrue(runSays().contains("next Archive Guardian"))
        assertEquals(ArchiveRaids.NEXUS_START, nexusLevel(server, run))
        assertEquals(ArchiveRaids.NEXUS_START + 1, nexusLevel(server, run, level = 1), "the level above it on the instance's level 1")
        // Into the hall: the Guardian's fight, done at once with no Guardian yet.
        command("raid tp guardian")
        awaitRun("the Guardian's room done") { ArchiveRoom.GUARDIAN in run.cleared }
        await("the nexus with the Guardian's corpse") { nexusLevel(server, run)?.takeIf { it == ArchiveRaids.NEXUS_CAMP } }
        assertEquals(ArchiveRaids.NEXUS_CAMP + 1, nexusLevel(server, run, level = 1), "the level above the camp's on the instance's level 1, as live copies it")
        // The Archive Door north is shut, and still blocks its tiles on the level laid over the nexus.
        val door = server.world.archiveRaids.tile(run, ArchiveRaids.OCELLUS_DOOR_X, ArchiveRaids.OCELLUS_DOOR_Z)
        fun doorOf(id: Int) = ZoneLoc(id, LocShape.CENTREPIECE_STRAIGHT, 0, door.x, door.z, ArchiveRaids.NEXUS_PLANE)
        fun shown(id: Int) = locChanges().any { it.id == id && it.coord == Coord(door.x, door.z, ArchiveRaids.NEXUS_PLANE) }
        assertTrue(server.world.locs.exists(doorOf(ArchiveRaids.OCELLUS_DOOR_SHUT)), "the Archive Door north, shut")
        for (dx in 0 until 7) for (dz in 0 until 2) {
            assertTrue(!server.world.canStand(door.x + dx, door.z + dz, ArchiveRaids.NEXUS_PLANE), "the door blocks +$dx, +$dz")
        }
        // A forged Enter on it does nothing: the shut door has no options, and the open one isn't there.
        command("raid tp ${ArchiveRaids.OCELLUS_DOOR_FRONT_X} ${ArchiveRaids.OCELLUS_DOOR_FRONT_Z}")
        awaitAt(server.world.archiveRaids.tile(run, ArchiveRaids.OCELLUS_DOOR_FRONT_X, ArchiveRaids.OCELLUS_DOOR_FRONT_Z), "in front of the Archive Door north")
        await("the shut Archive Door in view") { shown(ArchiveRaids.OCELLUS_DOOR_SHUT).takeIf { it } }
        locOp(ArchiveRaids.OCELLUS_DOOR_OPEN, door.x, door.z, ArchiveRaids.ENTER)
        awaitTicks(4)
        assertEquals(ArchiveRaids.NEXUS_PLANE, position?.level, "still in the nexus")
        assertTrue(ArchiveRoom.OCELLUS !in run.started)
        // North of the door the nexus's square is the vault's: the south end of its corridor, on the level above, shut
        // by the vault's own Passageway, which does nothing until the run is done.
        val raids = server.world.archiveRaids
        val corridor = raids.tile(run, 4127, 5628)
        assertEquals(ArchiveRoom.OCELLUS, raids.roomAt(run, corridor.x, corridor.z))
        assertTrue(!raids.inNexus(run, corridor.x, corridor.z))
        assertEquals(ArchiveRoom.GUARDIAN, raids.roomAt(run, door.x + 3, door.z - 1))
        assertTrue(raids.inNexus(run, door.x + 3, door.z - 1))
        val exitTile = raids.tile(run, 4124, 5625)
        val exit = ZoneLoc(ArchiveRaids.VAULT_PASSAGEWAY, LocShape.CENTREPIECE_STRAIGHT, 0, exitTile.x, exitTile.z, ArchiveRoom.OCELLUS.level)
        assertTrue(server.world.locs.exists(exit), "the vault's Passageway, from the cache's level above the nexus's")
        for (dx in 0 until 7) for (dz in 0 until 2) {
            assertTrue(!server.world.canStand(exitTile.x + dx, exitTile.z + dz, ArchiveRoom.OCELLUS.level), "the Passageway blocks +$dx, +$dz")
        }
        command("raid tp 4127 5628")
        awaitAt(corridor, "the south end of the vault's corridor", ArchiveRoom.OCELLUS.level)
        assertTrue(runSays().contains("You're in Ocellus's room, at 4127, 5628"), runSays())
        locOp(ArchiveRaids.VAULT_PASSAGEWAY, exitTile.x, exitTile.z, ArchiveRaids.EXIT)
        awaitTicks(4)
        // Walked to, a tile south, and no further.
        assertEquals(Coord(corridor.x, corridor.z - 1, ArchiveRoom.OCELLUS.level), position, "the vault's Passageway does nothing before the run is done")
        assertTrue(ArchiveRoom.OCELLUS !in run.started, "nor does standing in the vault begin Ocellus's room before its turn")
        command("raid tp ${ArchiveRaids.OCELLUS_DOOR_FRONT_X} ${ArchiveRaids.OCELLUS_DOOR_FRONT_Z}")
        awaitAt(server.world.archiveRaids.tile(run, ArchiveRaids.OCELLUS_DOOR_FRONT_X, ArchiveRaids.OCELLUS_DOOR_FRONT_Z), "back in front of the Archive Door north")
        for (room in rooms) through(server, run, room, passageway = room == passagewayIn)
        // Live stays on the camp's level for the visit after the fourth room too (Sam's recording); the door opens.
        assertEquals(ArchiveRaids.NEXUS_CAMP, nexusLevel(server, run))
        assertTrue(server.world.locs.exists(doorOf(ArchiveRaids.OCELLUS_DOOR_OPEN)), "the Archive Door north, open")
        assertTrue(!server.world.locs.exists(doorOf(ArchiveRaids.OCELLUS_DOOR_SHUT)))
        await("the open Archive Door in view") { (shown(ArchiveRaids.OCELLUS_DOOR_OPEN) && !shown(ArchiveRaids.OCELLUS_DOOR_SHUT)).takeIf { it } }
        locOp(ArchiveRaids.OCELLUS_DOOR_OPEN, door.x, door.z, if (outByExit) ArchiveRaids.QUICK_ENTER else ArchiveRaids.ENTER)
        // His arena is on the vault's upper floor, as live has it.
        awaitAt(server.world.archiveRaids.tile(run, ArchiveRoom.OCELLUS.entryX, ArchiveRoom.OCELLUS.entryZ), "Ocellus's room", ArchiveRoom.OCELLUS.level)
        awaitRun("the run complete") { it.contains("; complete;") }
        // Solo, every room's points are the one player's: four rooms' 100 and Ocellus's 300.
        assertTrue(runSays().contains("points $username ${ArchiveLoot.RUN_POINTS}"), runSays())
        // The run's end paid them, into the first chest: of this cache's two items the loot's file has none.
        val key = server.world.playerNamed(username)!!.saveKey
        assertEquals(listOf(key), run.loot?.chests?.keys?.toList())
        assertEquals(mapOf(key to 0), run.chestSlots.toMap())
        assertEquals("Loot for 1, no unique. Your chest is 1, closed: nothing.", commandReply("raid loot") { it.contains("oot") })
        assertTrue(server.world.locs.exists(exit), "the vault's Passageway is still there, on the camp's upper level")
        if (outByExit) {
            // Out by the vault's Passageway, down the corridor: to the lobby in front of the Archive Door.
            locOp(ArchiveRaids.VAULT_PASSAGEWAY, exit.x, exit.z, ArchiveRaids.EXIT)
            awaitAt(Tile(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z), "out to the lobby by the vault's Passageway")
        } else {
            // Out by the reward vault: the Blade's Travel (::raid travel stands in for the click), then its Rift out.
            assertEquals("Travelling to the reward vault.", commandReply("raid travel") { it.contains("ravel") })
            val vault = raids.tile(run, ArchiveRaids.REWARD_X, ArchiveRaids.REWARD_Z)
            awaitAt(vault, "the reward vault", ArchiveRaids.REWARD_LEVEL)
            assertTrue(runSays().contains("You're in the reward vault, at ${ArchiveRaids.REWARD_X}, ${ArchiveRaids.REWARD_Z} of the raid"), runSays())
            // Up the walk to the island: the Rift out is out of view from where the vault is come into.
            command("raid tp 4127 5790")
            awaitAt(raids.tile(run, 4127, 5790), "the north end of the vault's walk", ArchiveRaids.REWARD_LEVEL)
            val rift = await("the Rift out") { npcs().firstOrNull { it.id == ArchiveRaids.RIFT_OUT } }
            npcOp(rift.index, 3)
            awaitAt(Tile(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z), "out to the lobby by the Rift's Quick-leave")
        }
        // The instance ends behind them.
        await("the instance ended") { if (server.world.instances.all.none { it === run.instance }) Unit else null }
    }

    @Test
    fun `a run goes through the empty rooms by the west doors, and another by the east doors`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.goThrough(server, "alice", listOf(ArchiveRoom.EZEKIEL, ArchiveRoom.MOIA, ArchiveRoom.DAGONHAI))
                client.goThrough(server, "alice", listOf(ArchiveRoom.BORK, ArchiveRoom.DREADNOTH, ArchiveRoom.XARTH), passagewayIn = ArchiveRoom.DREADNOTH, outByExit = true)
            }
        }
    }

    @Test
    fun `a player comes back out of a room onto the tile they went through its door from, or beside the door`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                val run = client.enter(server, "carol")
                val raids = server.world.archiveRaids
                val key = server.world.playerNamed("carol")!!.saveKey
                client.command("raid tp guardian")
                client.awaitRun("the Guardian's room done") { ArchiveRoom.GUARDIAN in run.cleared }
                // The east column of Sam's recording, and the west one mirrored about the hall's middle.
                val room = ArchiveRoom.BORK
                assertEquals(Tile(4140, 5585), room.doorFront)
                assertEquals(Tile(4114, 5585), ArchiveRoom.EZEKIEL.doorFront)
                assertEquals(Tile(4140, 5598), ArchiveRoom.DREADNOTH.doorFront)
                // Through Bork's door from beside it, a tile south of the one the door gives by itself.
                val beside = Tile(ArchiveRaids.DOOR_FRONT_EAST_X, ArchiveRaids.doorZ(room.stage))
                val from = raids.tile(run, beside.x, beside.z)
                client.tele(from.x, from.z)
                val door = raids.tile(run, doorX(room), ArchiveRaids.doorZ(room.stage))
                client.locOp(room.door, door.x, door.z, ArchiveRaids.ENTER)
                client.awaitAt(raids.tile(run, room.entryX, room.entryZ), "Bork's room")
                assertEquals(beside, run.leftFrom[key to room], "the tile gone in from is kept")
                client.awaitRun("Bork's room done") { room in run.cleared }
                val transporter = roomLocs.getValue(room).second.let { raids.tile(run, it.x, it.z) }
                client.locOp(room.transporter, transporter.x, transporter.z, ArchiveRaids.USE)
                client.awaitAt(from, "back on the tile gone in from")
                assertTrue(client.runSays().endsWith("You're in the nexus's hall, at ${beside.x}, ${beside.z} of the raid."), "::raid run gives the cache's tile")
                assertEquals(null, run.leftFrom[key to room])
                // In again with no door gone through: out beside the door, on its own tile.
                client.commandReply("raid tp bork") { it.startsWith("To ") }
                client.awaitAt(raids.tile(run, room.entryX, room.entryZ), "Bork's room again")
                client.locOp(room.transporter, transporter.x, transporter.z, ArchiveRaids.USE)
                client.awaitAt(raids.tile(run, room.doorFront.x, room.doorFront.z), "out beside Bork's door")
            }
        }
    }

    @Test
    fun `a door, a Passageway and the transporter back out each move the player four ticks on, and not one who has gone elsewhere by then`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                val run = client.enter(server, "carol")
                val world = server.world
                val raids = world.archiveRaids
                val player = world.playerNamed("carol")!!
                val key = player.saveKey
                client.command("raid tp guardian")
                client.awaitRun("the Guardian's room done") { ArchiveRoom.GUARDIAN in run.cleared }
                val room = ArchiveRoom.BORK
                val front = raids.tile(run, room.doorFront.x, room.doorFront.z)
                val entry = raids.tile(run, room.entryX, room.entryZ)
                val door = raids.tile(run, doorX(room), ArchiveRaids.doorZ(room.stage))
                val (passage, transporter) = roomLocs.getValue(room).let { (p, t) -> raids.tile(run, p.first, p.second) to raids.tile(run, t.x, t.z) }

                /** Steps the paused world until her move is set down, and returns it. */
                fun setOff(what: String): osrsps.game.raid.RoomMove {
                    var waited = 0
                    while (key !in run.moving) {
                        check(++waited <= 30) { "$what not taken within 30 ticks" }
                        client.stepTicks(1)
                    }
                    return run.moving.getValue(key)
                }

                // The door, from beside it: still in the nexus for three ticks, in the room on the fourth. A second
                // click on the way doesn't start the count again.
                client.tele(front.x, front.z)
                client.commandReply("pause") { it.contains("aused after tick") }
                client.locOp(room.door, door.x, door.z, ArchiveRaids.ENTER)
                val into = setOff("the door")
                assertEquals(osrsps.game.raid.RoomMove(room, true, world.tick + ArchiveRaids.MOVE_DELAY), into, "four ticks from the door")
                client.locOp(room.door, door.x, door.z, ArchiveRaids.ENTER)
                client.stepTicks(ArchiveRaids.MOVE_DELAY - 1)
                assertEquals(into, run.moving[key], "the count not started again")
                assertEquals(Tile(front.x, front.z), Tile(player.x, player.z), "in a tick early")
                client.stepTicks(1)
                assertEquals(Tile(entry.x, entry.z), Tile(player.x, player.z))
                assertTrue(run.moving.isEmpty())
                assertTrue(room in run.started, "the room begun on the tick she arrived, not the one after")
                client.commandReply("resume") { it.startsWith("Resumed") }
                client.awaitRun("Bork's room done") { room in run.cleared }

                // The Passageway, after the walk to it: the same four ticks, then out in front of the door.
                client.commandReply("pause") { it.contains("aused after tick") }
                client.locOp(ArchiveRaids.PASSAGEWAY, passage.x, passage.z, ArchiveRaids.EXIT)
                val out = setOff("the Passageway")
                assertEquals(osrsps.game.raid.RoomMove(room, false, world.tick + ArchiveRaids.MOVE_DELAY), out)
                client.stepTicks(ArchiveRaids.MOVE_DELAY - 1)
                assertEquals(room, raids.roomAt(run, player.x, player.z), "out a tick early")
                client.stepTicks(1)
                assertEquals(Tile(front.x, front.z), Tile(player.x, player.z))

                // The transporter back out, and somewhere else before the four ticks are up: not moved from there.
                client.command("raid tp bork")
                client.stepTicks(1)
                assertEquals(Tile(entry.x, entry.z), Tile(player.x, player.z))
                client.locOp(room.transporter, transporter.x, transporter.z, ArchiveRaids.USE)
                val back = setOff("the transporter")
                assertEquals(osrsps.game.raid.RoomMove(room, false, world.tick + ArchiveRaids.MOVE_DELAY), back)
                client.command("raid tp guardian")
                client.stepTicks(1)
                val hall = Tile(player.x, player.z)
                assertEquals(ArchiveRoom.GUARDIAN, raids.roomAt(run, hall.x, hall.z))
                client.stepTicks(ArchiveRaids.MOVE_DELAY)
                assertEquals(hall, Tile(player.x, player.z), "moved from where she had gone")
                assertTrue(run.moving.isEmpty(), "her move done with all the same")
                client.commandReply("resume") { it.startsWith("Resumed") }
            }
        }
    }

    @Test
    fun `the Blade's Travel lands in the reward vault four ticks on, its Rift out comes fifteen ticks after the first in, and Leave goes to the lobby`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dana").use { client ->
                client.await("the login rebuild") { client.position }
                val run = client.enter(server, "dana")
                val world = server.world
                val raids = world.archiveRaids
                val player = world.playerNamed("dana")!!
                val key = player.saveKey
                val vault = raids.tile(run, ArchiveRaids.REWARD_X, ArchiveRaids.REWARD_Z)
                // The vault is two squares north of Ocellus's, on its own levels, with its Rift and Merit scroll put in.
                assertEquals(Tile(run.instance.baseX + 64 + 31, run.instance.baseZ + 4 * 64 + 19), vault)
                assertTrue(raids.inRewardRoom(run, vault.x, vault.z))
                assertEquals(null, raids.roomAt(run, vault.x, vault.z))
                assertEquals(ArchiveRaids.REWARD_LEVEL, raids.levelAt(run, vault.x, vault.z))
                for (level in 0..3) {
                    assertEquals(level, world.instances.referenceZone(vault.x shr 3, vault.z shr 3, level)?.level, "the vault's level $level, as the map has it")
                }
                for (loc in ArchiveRaids.REWARD_LOCS) {
                    val at = raids.tile(run, loc.x, loc.z)
                    assertTrue(world.locs.exists(ZoneLoc(loc.id, LocShape.CENTREPIECE_STRAIGHT, loc.angle, at.x, at.z, ArchiveRaids.REWARD_LEVEL)), "loc ${loc.id}")
                }
                // No Travel before Ocellus's room is done, nor from anywhere but his room.
                client.command("raid tp guardian")
                client.awaitRun("the Guardian's room done") { ArchiveRoom.GUARDIAN in run.cleared }
                assertTrue(client.commandReply("raid travel") { it.contains("ravel") }.startsWith("You can't travel"))
                for (room in listOf(ArchiveRoom.BORK, ArchiveRoom.DREADNOTH, ArchiveRoom.XARTH, ArchiveRoom.OCELLUS)) {
                    client.command("raid tp ${room.name.lowercase()}")
                    client.awaitRun("${room.boss}'s room done") { room in run.cleared }
                }
                assertTrue(run.completed)
                client.command("raid tp nexus")
                client.awaitAt(raids.tile(run, ArchiveRaids.NEXUS_X, ArchiveRaids.NEXUS_Z), "the nexus's entrance")
                assertTrue(client.commandReply("raid travel") { it.contains("ravel") }.startsWith("You can't travel"), "not from the nexus")
                client.command("raid tp ocellus")
                val arena = raids.tile(run, ArchiveRoom.OCELLUS.entryX, ArchiveRoom.OCELLUS.entryZ)
                client.awaitAt(arena, "Ocellus's room", ArchiveRoom.OCELLUS.level)

                // Travel: in his room for three ticks more, in the vault on the fourth; a second click leaves the count.
                client.commandReply("pause") { it.contains("aused after tick") }
                // A command runs at once on a paused world, so the count is from the tick it stands at.
                assertEquals("Travelling to the reward vault.", client.commandReply("raid travel") { it.contains("ravel") })
                val travel = run.moving.getValue(key)
                assertEquals(osrsps.game.raid.RoomMove(null, true, world.tick + ArchiveRaids.MOVE_DELAY), travel)
                client.commandReply("raid travel") { it.contains("ravel") }
                client.stepTicks(ArchiveRaids.MOVE_DELAY - 1)
                assertEquals(travel, run.moving[key], "the count not started again")
                assertEquals(Tile(arena.x, arena.z), Tile(player.x, player.z), "in a tick early")
                assertEquals(-1L, run.rewardTick)
                client.stepTicks(1)
                assertEquals(Triple(vault.x, vault.z, ArchiveRaids.REWARD_LEVEL), Triple(player.x, player.z, player.level))
                assertEquals(world.tick, run.rewardTick, "the first in starts the count to the Rift out")
                assertTrue(run.moving.isEmpty())

                // The Rift out: not for fourteen ticks, there on the fifteenth; a second arrival doesn't start it again.
                val since = run.rewardTick
                client.commandReply("raid tp vault") { it.startsWith("To the reward vault") }
                client.stepTicks(ArchiveRaids.RIFT_OUT_DELAY - 1)
                assertEquals(since, run.rewardTick)
                assertEquals(null, run.riftOut, "the Rift out a tick early")
                client.stepTicks(1)
                val rift = assertNotNull(run.riftOut, "the Rift out")
                val riftAt = raids.tile(run, ArchiveRaids.RIFT_OUT_X, ArchiveRaids.RIFT_OUT_Z)
                assertEquals(Triple(riftAt.x, riftAt.z, ArchiveRaids.REWARD_LEVEL), Triple(rift.x, rift.z, rift.level))
                client.stepTicks(3)
                assertTrue(run.riftOut === rift, "one Rift out only")
                client.commandReply("resume") { it.startsWith("Resumed") }

                // The client draws the vault from the map's own level 2, and sees its Rift and scroll.
                client.awaitAt(vault, "the reward vault", ArchiveRaids.REWARD_LEVEL)
                assertEquals(ArchiveRaids.REWARD_LEVEL, client.await("the vault's zones") { client.regionZones()?.get(Triple(vault.x shr 3, vault.z shr 3, ArchiveRaids.REWARD_LEVEL)) }.level)
                client.await("the vault's own locs in view") {
                    ArchiveRaids.REWARD_LOCS.all { loc -> client.locChanges().any { it.id == loc.id && it.coord.level == ArchiveRaids.REWARD_LEVEL } }.takeIf { it }
                }
                // Leave on the Rift out, from the island end of the walk (it is out of view from the south end): the
                // lobby, and the instance ends behind her.
                client.command("raid tp 4127 5790")
                client.awaitAt(raids.tile(run, 4127, 5790), "the north end of the vault's walk", ArchiveRaids.REWARD_LEVEL)
                val seen = client.await("the Rift out in view") { client.npcs().firstOrNull { it.id == ArchiveRaids.RIFT_OUT } }
                client.npcOp(seen.index, 1)
                client.awaitAt(Tile(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z), "out to the lobby by the Rift's Leave")
                client.await("the instance ended") { if (world.instances.all.none { it === run.instance }) Unit else null }
            }
        }
    }

    @Test
    fun `the run's end pays the members in it by their contribution, and each opens only their own chest, the Rift it leaves there a tick on`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { carol ->
                server.login("erin").use { erin ->
                    server.login("dave").use { dave ->
                        for (client in listOf(carol, erin, dave)) client.await("the login rebuild") { client.position }
                        // Erin is the party's second and Dave its third; she never goes in.
                        for ((client, name) in listOf(erin to "erin", dave to "dave")) {
                            carol.commandReply("raidparty invite $name") { it.startsWith("Invited") }
                            client.commandReply("raidparty accept carol") { it.startsWith("You joined") }
                        }
                        val run = carol.enter(server, "carol")
                        assertEquals(run, dave.enter(server, "dave"))
                        val world = server.world
                        val raids = world.archiveRaids
                        val carolKey = world.playerNamed("carol")!!.saveKey
                        val davePlayer = world.playerNamed("dave")!!
                        val daveKey = davePlayer.saveKey
                        // A table of this cache's two items: the pickaxe the unique, found every run, and two axes a
                        // standard roll, each roll five times over.
                        raids.chests.table = ArchiveLoot(
                            1, 4, DropTable(1, listOf(DropSlot(1, DropItem(pickaxe, 1, 1)))), 3, 1, DropTable(1, listOf(DropSlot(1, DropItem(axe, 2, 2)))),
                            null, 10, null, 15, emptySet(), emptyList(), 20, null,
                        )
                        // Carol does the first four rooms alone, and Dave falls once on the way.
                        carol.command("raid tp guardian")
                        carol.awaitRun("the Guardian's room done") { ArchiveRoom.GUARDIAN in run.cleared }
                        dave.command("hit 99 me")
                        carol.awaitRun("Dave down") { it.contains("down: dave") }
                        for (room in listOf(ArchiveRoom.BORK, ArchiveRoom.DREADNOTH, ArchiveRoom.XARTH)) {
                            carol.command("raid tp ${room.name.lowercase()}")
                            carol.awaitRun("${room.boss}'s room done") { room in run.cleared }
                        }
                        dave.await("Dave up") { if (davePlayer.dead || daveKey in run.downed) null else Unit }
                        assertEquals(null, run.loot, "no loot before the end")
                        assertEquals("No loot yet: Ocellus's room isn't done.", carol.commandReply("raid loot") { it.contains("oot") })
                        // Both in Ocellus's room as it begins, so its 300 are shared evenly: the run is done, and paid.
                        carol.commandReply("pause") { it.contains("aused after tick") }
                        carol.commandReply("raid tp ocellus") { it.startsWith("To ") }
                        dave.commandReply("raid tp ocellus") { it.startsWith("To ") }
                        carol.stepTicks(1)
                        assertTrue(run.completed)
                        assertEquals(mapOf(carolKey to 4 * ArchiveRaids.ROOM_POINTS + ArchiveRaids.OCELLUS_POINTS / 2, daveKey to ArchiveRaids.OCELLUS_POINTS / 2), run.points.toMap())
                        assertEquals(mapOf(daveKey to 1), run.deaths.toMap())
                        val loot = assertNotNull(run.loot, "the loot")
                        // The two in the run, each by their place in the party: Carol's chest the first, Dave's the third.
                        assertEquals(listOf(carolKey, daveKey), loot.chests.keys.toList())
                        assertEquals(mapOf(carolKey to 0, daveKey to 2), run.chestSlots.toMap())
                        assertEquals(setOf(0, 2), run.chests.keys)
                        for (slot in listOf(0, 2)) {
                            val chest = run.chests.getValue(slot)
                            val at = raids.tile(run, ArchiveChests.SLOTS[slot].first, ArchiveChests.SLOTS[slot].second)
                            assertEquals(ArchiveChests.chestType(slot, open = false), chest.type.id)
                            assertEquals(Triple(at.x, at.z, ArchiveRaids.REWARD_LEVEL), Triple(chest.x, chest.z, chest.level), "chest ${slot + 1}")
                        }
                        // One of them found the unique, and has it alone; the other has three rolls of ten axes.
                        val finder = assertNotNull(loot.unique, "the unique's finder")
                        assertTrue(finder == carolKey || finder == daveKey, finder)
                        for (key in listOf(carolKey, daveKey)) {
                            val expected = if (key == finder) listOf(pickaxe.id to 1) else listOf(axe.id to 30)
                            assertEquals(expected, loot.chests.getValue(key).map { it.type.id to it.count })
                        }
                        carol.commandReply("resume") { it.startsWith("Resumed") }
                        val uniqueIn = if (finder == carolKey) 1 else 3
                        val carols = if (finder == carolKey) "Bronze pickaxe x1" else "Bronze axe x30"
                        assertEquals("Loot for 2, a unique in chest $uniqueIn. Your chest is 1, closed: $carols.", carol.commandReply("raid loot") { it.contains("oot") })
                        assertEquals("Loot for 2, a unique in chest $uniqueIn. You have no chest.", erin.commandReply("raid loot") { it.contains("oot") })

                        // In the vault each one's own chest shows as theirs, by the varbit at its number.
                        val vault = raids.tile(run, ArchiveRaids.REWARD_X, ArchiveRaids.REWARD_Z)
                        for (client in listOf(carol, dave)) {
                            assertEquals("Travelling to the reward vault.", client.commandReply("raid travel") { it.contains("ravel") })
                            client.awaitAt(vault, "the reward vault", ArchiveRaids.REWARD_LEVEL)
                        }
                        carol.await("Carol's own chest, the first") { carol.varp(OWN_CHEST_VARP).takeIf { it and 15 == 1 } }
                        dave.await("Dave's own chest, the third") { dave.varp(OWN_CHEST_VARP).takeIf { it and 15 == 3 } }
                        // Beside her chest, on the island: Open, and it is the Rift a tick later.
                        val own = raids.tile(run, ArchiveChests.SLOTS[0].first, ArchiveChests.SLOTS[0].second)
                        carol.command("raid tp ${ArchiveChests.SLOTS[0].first + 1} ${ArchiveChests.SLOTS[0].second}")
                        carol.awaitAt(Tile(own.x + 1, own.z), "beside her chest", ArchiveRaids.REWARD_LEVEL)
                        val closed = carol.await("the two chests in view") {
                            carol.npcs().filter { it.id == ArchiveChests.chestType(0, false) || it.id == ArchiveChests.chestType(2, false) }.takeIf { it.size == 2 }
                        }
                        val hers = closed.first { it.id == ArchiveChests.chestType(0, false) }
                        assertEquals(Coord(own.x, own.z, ArchiveRaids.REWARD_LEVEL), hers.coord)
                        carol.commandReply("pause") { it.contains("aused after tick") }
                        carol.npcOp(hers.index, ArchiveChests.OPEN)
                        carol.stepTicks(1)
                        assertEquals(mapOf(0 to world.tick + ArchiveChests.OPEN_TICKS), run.opening.toMap(), "opening")
                        assertEquals(ArchiveChests.chestType(0, open = false), run.chests.getValue(0).type.id)
                        assertTrue(carol.commandReply("raid loot") { it.contains("oot") }.contains("Your chest is 1, opening: "))
                        carol.stepTicks(ArchiveChests.OPEN_TICKS)
                        assertTrue(run.opening.isEmpty() && run.opened == setOf(0), "open: ${run.opened}")
                        val rift = run.chests.getValue(0)
                        assertEquals(ArchiveChests.chestType(0, open = true), rift.type.id)
                        assertEquals(Triple(own.x, own.z, ArchiveRaids.REWARD_LEVEL), Triple(rift.x, rift.z, rift.level))
                        carol.commandReply("resume") { it.startsWith("Resumed") }
                        carol.await("her chest the Rift in view") {
                            carol.npcs().let { seen -> seen.any { it.id == ArchiveChests.chestType(0, true) } && seen.none { it.id == ArchiveChests.chestType(0, false) } }.takeIf { it }
                        }
                        assertTrue(carol.commandReply("raid loot") { it.contains("oot") }.contains("Your chest is 1, open: $carols."))
                        // Dave's chest isn't hers to open: a line, and it stays shut.
                        val his = closed.first { it.id == ArchiveChests.chestType(2, false) }
                        carol.npcOp(his.index, ArchiveChests.OPEN)
                        carol.awaitMessage("the refusal at another's chest") { it.contains("chest_not_yours") }
                        assertTrue(run.opening.isEmpty() && run.opened == setOf(0), "Dave's chest opened by Carol")
                        // He opens it himself.
                        val third = raids.tile(run, ArchiveChests.SLOTS[2].first, ArchiveChests.SLOTS[2].second)
                        dave.command("raid tp ${ArchiveChests.SLOTS[2].first + 1} ${ArchiveChests.SLOTS[2].second}")
                        dave.awaitAt(Tile(third.x + 1, third.z), "beside his chest", ArchiveRaids.REWARD_LEVEL)
                        val seen = dave.await("his chest in view") { dave.npcs().firstOrNull { it.id == ArchiveChests.chestType(2, false) } }
                        dave.npcOp(seen.index, ArchiveChests.OPEN)
                        dave.await("his chest open") { if (run.opened == setOf(0, 2)) Unit else null }
                        assertEquals(ArchiveChests.chestType(2, open = true), run.chests.getValue(2).type.id)
                    }
                }
            }
        }
    }

    @Test
    fun `a death once the run is done costs nothing and leaves them standing, in the nexus or the vault, and a lone one's is no wipe`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { carol ->
                server.login("dave").use { dave ->
                    for (client in listOf(carol, dave)) client.await("the login rebuild") { client.position }
                    carol.commandReply("raidparty invite dave") { it.startsWith("Invited") }
                    dave.commandReply("raidparty accept carol") { it.startsWith("You joined") }
                    val run = carol.enter(server, "carol")
                    assertEquals(run, dave.enter(server, "dave"))
                    val world = server.world
                    val raids = world.archiveRaids
                    val carolPlayer = world.playerNamed("carol")!!
                    for (room in listOf(ArchiveRoom.GUARDIAN, ArchiveRoom.BORK, ArchiveRoom.DREADNOTH, ArchiveRoom.XARTH, ArchiveRoom.OCELLUS)) {
                        carol.command("raid tp ${room.name.lowercase()}")
                        carol.awaitRun("${room.boss}'s room done") { room in run.cleared }
                    }
                    assertTrue(run.completed)
                    carol.awaitAt(raids.tile(run, ArchiveRoom.OCELLUS.entryX, ArchiveRoom.OCELLUS.entryZ), "Ocellus's room", ArchiveRoom.OCELLUS.level)
                    carol.commandReply("item bronze axe 5") { it.contains("Bronze axe") }
                    // Carol dies in his room with Dave standing (exploit review round 23, R23A-1: she was down for
                    // good): up at the nexus's entrance, standing, her axes kept, and the death not counted.
                    val entrance = raids.tile(run, ArchiveRaids.NEXUS_X, ArchiveRaids.NEXUS_Z)
                    carol.command("hit 99 me")
                    carol.awaitAt(entrance, "Carol back at the nexus's entrance")
                    carol.await("Carol up") { if (carolPlayer.dead) null else Unit }
                    assertTrue(run.downed.isEmpty() && run.deaths.isEmpty() && !run.wiped, carol.runSays())
                    carol.await("Carol's axes kept") { carol.inventory(INVENTORY).count { it?.id == axe.id }.takeIf { it == 5 } }
                    // Standing, so the way on is still hers: back into his room and by Travel to the vault.
                    val arena = raids.tile(run, ArchiveRoom.OCELLUS.entryX, ArchiveRoom.OCELLUS.entryZ)
                    carol.command("raid tp ocellus")
                    carol.awaitAt(arena, "Ocellus's room again", ArchiveRoom.OCELLUS.level)
                    // Dave leaves the party: Carol is the only one in the run.
                    dave.commandReply("raidparty leave") { it.contains("left") || it.contains("Left") }
                    dave.awaitAt(Tile(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z), "Dave out to the lobby")
                    assertEquals("Travelling to the reward vault.", carol.commandReply("raid travel") { it.contains("ravel") })
                    val vault = raids.tile(run, ArchiveRaids.REWARD_X, ArchiveRaids.REWARD_Z)
                    carol.awaitAt(vault, "the reward vault", ArchiveRaids.REWARD_LEVEL)
                    // She dies up the vault's walk, alone: no wipe, and up at its arrival tile with everything.
                    carol.command("raid tp 4127 5790")
                    carol.awaitAt(raids.tile(run, 4127, 5790), "the north end of the vault's walk", ArchiveRaids.REWARD_LEVEL)
                    carol.command("hit 99 me")
                    carol.awaitAt(vault, "Carol back at the vault's arrival tile", ArchiveRaids.REWARD_LEVEL)
                    carol.await("Carol up in the vault") { if (carolPlayer.dead) null else Unit }
                    assertTrue(run.downed.isEmpty() && run.deaths.isEmpty() && !run.wiped, carol.runSays())
                    assertTrue(world.instances.all.any { it === run.instance }, "the run still there")
                    // Out by the Rift, as anyone: the lobby, her five axes on her and no grave anywhere.
                    carol.command("raid tp 4127 5790")
                    carol.awaitAt(raids.tile(run, 4127, 5790), "the north end of the walk again", ArchiveRaids.REWARD_LEVEL)
                    val rift = carol.await("the Rift out in view") { carol.npcs().firstOrNull { it.id == ArchiveRaids.RIFT_OUT } }
                    carol.npcOp(rift.index, 1)
                    carol.awaitAt(Tile(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z), "out to the lobby by the Rift's Leave")
                    carol.await("Carol's axes still hers") { carol.inventory(INVENTORY).count { it?.id == axe.id }.takeIf { it == 5 } }
                    assertTrue(world.deaths.all.isEmpty(), "no grave: ${world.deaths.all.map { it.owner }}")
                }
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
    @Test
    fun `a member down can't trade or be traded with, and what they drop stays theirs alone`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { carol ->
                server.login("dave").use { dave ->
                    for (client in listOf(carol, dave)) client.await("the login rebuild") { client.position }
                    carol.commandReply("raidparty invite dave") { it.startsWith("Invited") }
                    dave.commandReply("raidparty accept carol") { it.startsWith("You joined") }
                    val run = carol.enter(server, "carol")
                    assertEquals(run, dave.enter(server, "dave"))
                    val carolPlayer = server.world.playerNamed("carol")!!
                    val davePlayer = server.world.playerNamed("dave")!!
                    // Standing, they can trade: Carol's request reaches Dave, who doesn't answer it yet.
                    carol.playerOp(dave.index, 4)
                    dave.await("the request while standing") { dave.messages().firstOrNull { it.type == Trades.CHATTYPE_TRADEREQ } }
                    val before = dave.messages().count { it.type == Trades.CHATTYPE_TRADEREQ }
                    carol.commandReply("item bronze pickaxe") { it.contains("Bronze pickaxe") }
                    carol.command("hit 99 me")
                    carol.await("Carol down") { if (carolPlayer.saveKey in run.downed && !carolPlayer.dead) Unit else null }
                    assertTrue(server.world.archiveRaids.isDown(carolPlayer))
                    // Down, she can't ask again, and Dave's answer to her earlier request opens no trade.
                    carol.playerOp(dave.index, 4)
                    dave.playerOp(carol.index, 4)
                    carol.awaitTicks(4)
                    assertEquals(before, dave.messages().count { it.type == Trades.CHATTYPE_TRADEREQ }, "a request from Carol down")
                    assertTrue(carol.messages().none { it.type == Trades.CHATTYPE_TRADEREQ }, "a request to Carol down")
                    assertEquals(null, carolPlayer.trade)
                    assertEquals(null, davePlayer.trade)
                    // Her pickaxe, tradeable, dropped while down: hers alone, never turning public for Dave to take.
                    val slot = carol.await("the pickaxe") { carol.inventory(INVENTORY).indexOfFirst { it?.id == pickaxe.id }.takeIf { it >= 0 } }
                    carol.ifButton(INVENTORY_IF, INVENTORY_ITEMS, DROP, slot, pickaxe.id)
                    val dropped = carol.await("the pickaxe on the ground") {
                        server.world.groundObjs.visibleAt(carolPlayer, carolPlayer.x, carolPlayer.z, carolPlayer.level).firstOrNull { it.id == pickaxe.id }
                    }
                    assertTrue(dropped.neverPublic, "the drop of a member down stays private")
                    assertEquals(carolPlayer.saveKey, dropped.owner)
                }
            }
        }
    }

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
    fun `a member down whose Home Teleport lands the tick before the room is done still dies of it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { dave ->
                server.login("carol").use { carol ->
                    for (client in listOf(carol, dave)) client.await("the login rebuild") { client.position }
                    carol.commandReply("raidparty invite dave") { it.startsWith("Invited") }
                    dave.commandReply("raidparty accept carol") { it.startsWith("You joined") }
                    val run = carol.enter(server, "carol")
                    assertEquals(run, dave.enter(server, "dave"))
                    val carolPlayer = server.world.playerNamed("carol")!!
                    // Dave stands at the way in, and Carol falls there.
                    carol.commandReply("item bronze axe 5") { it.contains("Bronze axe") }
                    carol.command("hit 99 me")
                    carol.await("Carol down") { if (carolPlayer.saveKey in run.downed && !carolPlayer.dead) Unit else null }
                    carol.awaitTicks(World.LOGOUT_COMBAT_TICKS.toInt() + 1)
                    dave.commandReply("pause") { it.contains("aused after tick") }
                    carol.homeTeleportPicked(dave)
                    var ticks = 0
                    while (run.instance.contains(carolPlayer.x, carolPlayer.z)) {
                        check(++ticks <= 60) { "no landing within 60 ticks of the pick" }
                        dave.stepTicks(1)
                    }
                    // The Guardian's room done in the next tick's input, before that tick's look for escapes. Dave goes
                    // in and clears it in that one input: a room with no boss built is done as soon as he stands in it.
                    dave.command("raid tp guardian")
                    dave.command("raid clear")
                    dave.awaitCycles(2)
                    dave.stepTicks(1)
                    assertTrue(ArchiveRoom.GUARDIAN in run.cleared, "the Guardian's room done")
                    dave.commandReply("resume") { it.startsWith("Resumed") }
                    carol.awaitAt(Tile(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z), "Carol dead of it, in the lobby")
                    server.assertGraveInLobby(carolPlayer.saveKey, "carol", 2)
                }
            }
        }
    }

    @Test
    fun `a member down whose Home Teleport has landed outside still drops only to themselves before the escape is settled`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { dave ->
                server.login("carol").use { carol ->
                    for (client in listOf(carol, dave)) client.await("the login rebuild") { client.position }
                    carol.commandReply("raidparty invite dave") { it.startsWith("Invited") }
                    dave.commandReply("raidparty accept carol") { it.startsWith("You joined") }
                    val run = carol.enter(server, "carol")
                    assertEquals(run, dave.enter(server, "dave"))
                    val carolPlayer = server.world.playerNamed("carol")!!
                    carol.commandReply("item bronze pickaxe") { it.contains("Bronze pickaxe") }
                    val slot = carol.await("the pickaxe") { carol.inventory(INVENTORY).indexOfFirst { it?.id == pickaxe.id }.takeIf { it >= 0 } }
                    carol.command("hit 99 me")
                    carol.await("Carol down") { if (carolPlayer.saveKey in run.downed && !carolPlayer.dead) Unit else null }
                    carol.awaitTicks(World.LOGOUT_COMBAT_TICKS.toInt() + 1)
                    dave.commandReply("pause") { it.contains("aused after tick") }
                    carol.homeTeleportPicked(dave)
                    var ticks = 0
                    while (run.instance.contains(carolPlayer.x, carolPlayer.z)) {
                        check(++ticks <= 60) { "no landing within 60 ticks of the pick" }
                        dave.stepTicks(1)
                    }
                    // Landed outside, the escape not yet settled: the next tick's input, her drop, comes before it is, and
                    // she dies of it that tick, the pickaxe left where she landed.
                    assertTrue(carolPlayer.saveKey in run.downed, "still down on landing")
                    assertTrue(server.world.archiveRaids.isDown(carolPlayer), "down, outside the instance")
                    carol.ifButton(INVENTORY_IF, INVENTORY_ITEMS, DROP, slot, pickaxe.id)
                    carol.awaitCycles(2)
                    val landed = Tile(carolPlayer.x, carolPlayer.z)
                    dave.stepTicks(1)
                    val dropped = assertNotNull(
                        server.world.groundObjs.visibleAt(carolPlayer, landed.x, landed.z, 0).firstOrNull { it.id == pickaxe.id },
                        "the pickaxe dropped where she landed",
                    )
                    assertTrue(dropped.neverPublic, "the drop of a member down stays private outside too")
                    dave.commandReply("resume") { it.startsWith("Resumed") }
                    carol.awaitAt(Tile(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z), "Carol dead of it, in the lobby")
                    assertTrue(!server.world.archiveRaids.isDown(carolPlayer), "no longer down once dead of it")
                }
            }
        }
    }

    @Test
    fun `a member down who leaves the party just before the room is done still dies of it`(@TempDir dir: Path) {
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
                        val erinPlayer = server.world.playerNamed("erin")!!
                        erin.commandReply("item bronze axe 5") { it.contains("Bronze axe") }
                        erin.command("hit 99 me")
                        erin.await("Erin down") { if (erinPlayer.saveKey in run.downed && !erinPlayer.dead) Unit else null }
                        // Between two ticks Erin leaves the party, still in the instance, and Dave goes into the
                        // Guardian's room and has it done: before the next tick could send her out.
                        dave.commandReply("pause") { it.contains("aused after tick") }
                        erin.commandReply("raidparty leave") { it.contains("left") || it.contains("Left") }
                        assertTrue(run.instance.contains(erinPlayer.x, erinPlayer.z), "Erin still in the instance")
                        dave.commandReply("raid tp guardian") { it.startsWith("To ") }
                        dave.commandReply("raid clear") { it.startsWith("Cleared") }
                        dave.stepTicks(1)
                        dave.commandReply("resume") { it.startsWith("Resumed") }
                        erin.awaitAt(Tile(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z), "Erin dead of it, in the lobby")
                        erin.await("Erin's three axes") { erin.inventory(INVENTORY).count { it?.id == axe.id }.takeIf { it == 3 } }
                        server.assertGraveInLobby(erinPlayer.saveKey, "erin", 2)
                    }
                }
            }
        }
    }

    @Test
    fun `a member down whose Home Teleport lands the tick before the last one standing dies still dies of it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("frank").use { frank ->
                server.login("dave").use { dave ->
                    server.login("carol").use { carol ->
                        // Frank, out of the party, holds the clock, and first times a Home Teleport from the pick to the landing.
                        for (client in listOf(carol, dave, frank)) client.await("the login rebuild") { client.position }
                        val frankPlayer = server.world.playerNamed("frank")!!
                        val stone = Tile(Lodestones.LUMBRIDGE.x + 1, Lodestones.LUMBRIDGE.z + 1)
                        frank.commandReply("pause") { it.contains("aused after tick") }
                        frank.homeTeleportPicked(frank)
                        var cast = 0
                        while (frankPlayer.x != stone.x || frankPlayer.z != stone.z) {
                            check(++cast <= 60) { "no landing within 60 ticks of the pick" }
                            frank.stepTicks(1)
                        }
                        frank.commandReply("resume") { it.startsWith("Resumed") }
                        carol.commandReply("raidparty invite dave") { it.startsWith("Invited") }
                        dave.commandReply("raidparty accept carol") { it.startsWith("You joined") }
                        val run = carol.enter(server, "carol")
                        assertEquals(run, dave.enter(server, "dave"))
                        val carolPlayer = server.world.playerNamed("carol")!!
                        val davePlayer = server.world.playerNamed("dave")!!
                        carol.commandReply("item bronze axe 5") { it.contains("Bronze axe") }
                        carol.command("hit 99 me")
                        carol.await("Carol down") { if (carolPlayer.saveKey in run.downed && !carolPlayer.dead) Unit else null }
                        carol.awaitTicks(World.LOGOUT_COMBAT_TICKS.toInt() + 1)
                        // Carol picks Home Teleport; Dave, the last standing, falls so that his death ends on the tick
                        // after her landing, before that tick's look for escapes, and wipes the run.
                        frank.commandReply("pause") { it.contains("aused after tick") }
                        carol.homeTeleportPicked(frank)
                        frank.stepTicks(cast - Deaths.DEATH_TICKS + 1)
                        dave.commandReply("hit 99 me") { it.endsWith(", dead") }
                        frank.stepTicks(Deaths.DEATH_TICKS - 1)
                        assertTrue(!run.instance.contains(carolPlayer.x, carolPlayer.z), "Carol landed out of the run")
                        assertTrue(davePlayer.dead && !run.wiped, "Dave still lying dead")
                        assertTrue(carolPlayer.saveKey in run.downed, run.downed.toString())
                        frank.stepTicks(1)
                        assertTrue(run.wiped, "Dave's death wiped the run")
                        frank.commandReply("resume") { it.startsWith("Resumed") }
                        carol.awaitAt(Tile(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z), "Carol dead of it, in the lobby")
                        server.assertGraveInLobby(carolPlayer.saveKey, "carol", 2)
                    }
                }
            }
        }
    }

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
        /** The varp tfa_vault_own_chest is the low four bits of (241.4 cache). */
        const val OWN_CHEST_VARP = 5922
        const val INVENTORY = 93
        const val INVENTORY_IF = 149
        const val INVENTORY_ITEMS = 0
        const val DROP = 7
    }
}
