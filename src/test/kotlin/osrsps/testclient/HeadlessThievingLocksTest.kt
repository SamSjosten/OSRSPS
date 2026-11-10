package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.ThievingLocks
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Thieving 2 step T2 through the headless client: the 10-coin chest's level, its trap set off by Open, and its search
 * for 10 coins and 7.8 XP, empty until it relocks; the blood rune chest's second trap; the steel arrowtip chest picked
 * with a used lockpick; East Ardougne's door locked from outside, picked through and opened from inside; and Ardougne
 * Castle's door, whose trap hits for 10% of full Hitpoints. The rl-client script test-runner/examples/thieving-t2.json
 * does the same in the real East Ardougne.
 */
class HeadlessThievingLocksTest {
    private fun type(id: Int, name: String, ops: List<String?>, interact: Int = 2) =
        LocType(id, name, ops = ops + List(5 - ops.size) { null }, collision = LocCollisionType(width = 1, length = 1, interactType = interact))

    private val coinChest = ZoneLoc(ThievingLocks.CHEST_10_COINS, LocShape.CENTREPIECE_STRAIGHT, 0, 3220, 3218, 0)
    private val bloodChest = ZoneLoc(ThievingLocks.CHEST_BLOOD, LocShape.CENTREPIECE_STRAIGHT, 0, 3220, 3221, 0)
    private val tipChest = ZoneLoc(ThievingLocks.CHEST_ARROWTIPS, LocShape.CENTREPIECE_STRAIGHT, 0, 3220, 3224, 0)

    /** On its tile's west edge: outside is the door's own tile, x 3226; inside is x 3225. */
    private val northDoor = ZoneLoc(ThievingLocks.DOOR_EAST_ARDOUGNE_NORTH, LocShape.WALL_STRAIGHT, 0, 3226, 3218, 0)
    private val castleDoor = ZoneLoc(ThievingLocks.DOOR_ARDOUGNE_CASTLE, LocShape.WALL_STRAIGHT, 0, 3226, 3222, 0)

    private fun server(dir: Path, seed: Long = 5) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(POT + 1)
        for (id in listOf(ThievingLocks.COINS, ThievingLocks.BLOOD_RUNE, ThievingLocks.STEEL_ARROWTIPS, ThievingLocks.NATURE_RUNE)) {
            objs[id] = ObjType(id, "Stack $id", stackable = true)
        }
        objs[ThievingLocks.LOCKPICK] = ObjType(ThievingLocks.LOCKPICK, "Lockpick")
        objs[POT] = ObjType(POT, "Pot")
        val chestOps = listOf("Open", "Search for traps")
        val doorOps = listOf("Open", "Pick-lock")
        World(
            crcs,
            CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6); markLoaded(2584 shr 6, 3337 shr 6) },
            random = GameRandom(seed), objTypes = ObjTypes(objs),
            locTypes = LocTypes(
                listOf(
                    type(ThievingLocks.CHEST_10_COINS, "Chest", chestOps),
                    type(ThievingLocks.CHEST_BLOOD, "Chest", chestOps),
                    type(ThievingLocks.CHEST_ARROWTIPS, "Chest", chestOps),
                    type(ThievingLocks.EMPTY_TRAP_CHEST, "Chest", chestOps),
                    type(ThievingLocks.EMPTY_PICK_CHEST, "Chest", chestOps),
                    type(ThievingLocks.OPEN_CHEST, "Chest", emptyList()),
                    type(ThievingLocks.DOOR_EAST_ARDOUGNE_NORTH, "Door", doorOps),
                    type(ThievingLocks.DOOR_ARDOUGNE_CASTLE, "Door", doorOps),
                    type(ThievingLocks.DOOR_OPEN, "Door", emptyList(), interact = 0),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { listOf(coinChest, bloodChest, tipChest, northDoor, castleDoor).forEach(::add) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.seen(text: String) = messages().count { it.text == text }

    private fun HeadlessClient.awaitSeen(text: String, times: Int = 1) = await("$text x$times") { if (seen(text) >= times) Unit else null }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.locAt(x: Int, z: Int) = locChanges().lastOrNull { it.coord.x == x && it.coord.z == z }?.id

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.level(skill: String, id: Int, level: Int) {
        command("setlevel $skill $level")
        await("$skill $level") { stat(id)?.takeIf { it.level == level } }
    }

    @Test
    fun `the 10-coin chest finds nothing below 13, springs its trap on Open, and gives 10 coins and 7,8 XP when searched`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                val alice = server.world.playerNamed("alice")!!
                client.level("hitpoints", HITPOINTS, 99)
                client.level("thieving", THIEVING, 12)
                client.tele(3221, 3218)
                client.locOp(coinChest.id, coinChest.x, coinChest.z, ThievingLocks.OP_SECOND)
                client.awaitSeen("[thieving_lock.nothing]")
                assertEquals(0, client.count(ThievingLocks.COINS))

                // floor(99 * 12 / 100) + 3 = 14.
                client.locOp(coinChest.id, coinChest.x, coinChest.z, ThievingLocks.OP_OPEN)
                client.awaitSeen("[thieving_lock.chest_trap]")
                client.await("the trap's blow") { if (alice.hitpoints == 99 - 14) Unit else null }

                client.level("thieving", THIEVING, 13)
                val tenths = alice.skills.tenths(Skill.THIEVING)
                client.locOp(coinChest.id, coinChest.x, coinChest.z, ThievingLocks.OP_SECOND)
                for (key in listOf("search", "trap_found", "trap_disabled", "chest_opened", "treasure")) client.awaitSeen("[thieving_lock.$key]")
                client.await("10 coins") { if (client.count(ThievingLocks.COINS) == 10) Unit else null }
                assertEquals(tenths + 78, alice.skills.tenths(Skill.THIEVING))
                client.await("the empty chest") { if (client.locAt(coinChest.x, coinChest.z) == ThievingLocks.EMPTY_TRAP_CHEST) Unit else null }
                client.locOp(ThievingLocks.EMPTY_TRAP_CHEST, coinChest.x, coinChest.z, ThievingLocks.OP_SECOND)
                client.awaitSeen("[thieving_lock.looted]")
                client.await("the chest relocked") { if (server.world.locs.exists(coinChest)) Unit else null }
            }
        }
    }

    @Test
    fun `the blood rune chest's second trap sends the looter to East Ardougne, and the arrowtip chest wants a lockpick`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.level("thieving", THIEVING, 99)
                client.tele(3221, 3224)
                client.locOp(tipChest.id, tipChest.x, tipChest.z, ThievingLocks.OP_OPEN)
                client.awaitSeen("[thieving_lock.chest_locked]")
                client.locOp(tipChest.id, tipChest.x, tipChest.z, ThievingLocks.OP_SECOND)
                client.awaitSeen("[thieving_lock.need_lockpick]")
                client.command("item ${ThievingLocks.LOCKPICK} 1")
                client.await("the lockpick") { if (client.count(ThievingLocks.LOCKPICK) == 1) Unit else null }
                val slot = client.inventory(BACKPACK).indexOfFirst { it?.id == ThievingLocks.LOCKPICK }
                client.locTarget(tipChest.id, tipChest.x, tipChest.z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slot, ThievingLocks.LOCKPICK)
                client.awaitSeen("[thieving_lock.pick_done]")
                client.await("5 steel arrowtips and 20 coins") {
                    if (client.count(ThievingLocks.STEEL_ARROWTIPS) == 5 && client.count(ThievingLocks.COINS) == 20) Unit else null
                }
                client.await("the looted chest") { if (client.locAt(tipChest.x, tipChest.z) == ThievingLocks.EMPTY_PICK_CHEST) Unit else null }

                client.tele(3221, 3221)
                client.locOp(bloodChest.id, bloodChest.x, bloodChest.z, ThievingLocks.OP_SECOND)
                client.awaitSeen("[thieving_lock.second_trap]")
                client.await("East Ardougne") { client.position?.takeIf { it == Coord(2584, 3337, 0) } }
                assertEquals(2, client.count(ThievingLocks.BLOOD_RUNE))
                assertEquals(520, client.count(ThievingLocks.COINS))
            }
        }
    }

    @Test
    fun `a chest whose loot wouldn't fit gives nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.level("thieving", THIEVING, 99)
                client.command("item $POT 28")
                client.await("a full backpack") { if (client.count(POT) == 28) Unit else null }
                client.tele(3221, 3218)
                client.locOp(coinChest.id, coinChest.x, coinChest.z, ThievingLocks.OP_SECOND)
                client.awaitSeen("[thieving_lock.no_room]")
                assertEquals(0, client.count(ThievingLocks.COINS))
                assertTrue(server.world.locs.exists(coinChest), "the chest still full")
            }
        }
    }

    @Test
    fun `East Ardougne's door is locked from outside, picked through, and opens from inside, and the castle door's trap hits`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                val carol = server.world.playerNamed("carol")!!
                client.level("hitpoints", HITPOINTS, 99)
                client.level("thieving", THIEVING, 1)
                client.tele(3226, 3218)
                client.locOp(northDoor.id, northDoor.x, northDoor.z, ThievingLocks.OP_OPEN)
                client.awaitSeen("[thieving_lock.door_locked]")
                val tenths = carol.skills.tenths(Skill.THIEVING)
                var tries = 0
                while (client.seen("[thieving_lock.pick_done]") == 0) {
                    assertTrue(++tries <= 40, "a pick works in 40 tries at 13%")
                    val fails = client.seen("[thieving_lock.pick_fail]")
                    client.locOp(northDoor.id, northDoor.x, northDoor.z, ThievingLocks.OP_SECOND)
                    client.await("the pick's result") {
                        if (client.seen("[thieving_lock.pick_fail]") > fails || client.seen("[thieving_lock.pick_done]") > 0) Unit else null
                    }
                }
                client.await("inside") { client.position?.takeIf { it == Coord(3225, 3218, 0) } }
                assertEquals(tenths + 38, carol.skills.tenths(Skill.THIEVING))
                client.await("the door shut") { if (server.world.locs.exists(northDoor)) Unit else null }

                client.locOp(northDoor.id, northDoor.x, northDoor.z, ThievingLocks.OP_OPEN)
                client.awaitSeen("[thieving_lock.through]")
                client.await("outside") { client.position?.takeIf { it == Coord(3226, 3218, 0) } }

                // Thieving 1: the castle door's trap is 201/256 to miss, and a blow is 10% of 99.
                client.tele(3226, 3222)
                tries = 0
                while (client.seen("[thieving_lock.lock_trap]") == 0) {
                    assertTrue(++tries <= 60, "the trap goes off in 60 tries at 21%")
                    val traps = client.seen("[thieving_lock.lock_trap]")
                    client.locOp(castleDoor.id, castleDoor.x, castleDoor.z, ThievingLocks.OP_SECOND)
                    val boxed = client.await("the try's end") {
                        when {
                            client.seen("[thieving_lock.lock_trap]") > traps -> false
                            (client.dialogueView as? DialogueView.Message)?.text == "[thieving_lock.door_level level=61]" -> true
                            else -> null
                        }
                    }
                    if (boxed) {
                        client.continueDialogue()
                        client.await("the box shut") { if (client.dialogueView == null) Unit else null }
                    }
                }
                client.await("the trap's blow") { if (carol.hitpoints == 99 - 9) Unit else null }
                assertEquals(Coord(3226, 3222, 0), client.position)
            }
        }
    }

    private companion object {
        const val POT = 1931
        const val BACKPACK = 93
        const val THIEVING = 17
        const val HITPOINTS = 3
    }
}
