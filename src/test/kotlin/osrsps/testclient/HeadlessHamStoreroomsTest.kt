package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.HamHideout
import osrsps.game.content.HamStorerooms
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.PatrolStop
import osrsps.game.skill.Pickpocketing
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Misthalin piece 7 step 4b through the headless client: the hidden trapdoor open and climbed down, the ladder back up,
 * a storeroom door locked, picked (0.5 XP a fail, 3.8 a success) and left, a guard in sight attacking a picker unless a
 * failed pickpocket has settled it, the guards' pickpocket, a small chest using its key, the cracks and the large door.
 * The rl-client script test-runner/examples/ham-storerooms.json does the same in the real storerooms.
 */
class HeadlessHamStoreroomsTest {
    private fun type(id: Int, name: String, ops: List<String?>, multiloc: Boolean = false) =
        LocType(id, name, ops = ops, collision = LocCollisionType(width = 1, length = 1, interactType = 0), multiloc = multiloc)

    private val trapdoor = ZoneLoc(HamStorerooms.TRAPDOOR, LocShape.CENTREPIECE_STRAIGHT, 3, 3166, 9622, 0)
    private val ladder = ZoneLoc(HamStorerooms.LADDER, LocShape.CENTREPIECE_STRAIGHT, 3, 2567, 5185, 0)

    /** The south-west room's door (west edge) and the south-east room's (east edge). */
    private val westDoor = ZoneLoc(HamStorerooms.DOOR, LocShape.WALL_STRAIGHT, 0, 2567, 5192, 0)
    private val eastDoor = ZoneLoc(HamStorerooms.DOOR, LocShape.WALL_STRAIGHT, 2, 2576, 5192, 0)
    private val southCrack = ZoneLoc(HamStorerooms.CRACK, LocShape.WALL_STRAIGHT, 3, 2569, 5190, 0)
    private val steelChest = ZoneLoc(15722, LocShape.CENTREPIECE_STRAIGHT, 2, 2573, 5193, 0)
    private val largeLeft = ZoneLoc(HamStorerooms.LARGE_DOOR_LEFT, LocShape.WALL_STRAIGHT, 3, 2572, 5204, 0)
    private val largeRight = ZoneLoc(HamStorerooms.LARGE_DOOR_RIGHT, LocShape.WALL_STRAIGHT, 3, 2571, 5204, 0)

    private val guardA = NpcType(HamStorerooms.GUARDS[0], "Guard", combatLevel = 22, ops = listOf("Pickpocket", "Attack", "Talk-to", null, null), internalName = "dttd_ham_guard_1_postquest")
    private val guardB = NpcType(HamStorerooms.GUARDS[1], "Guard", combatLevel = 22, ops = listOf("Pickpocket", "Attack", "Talk-to", null, null), internalName = "dttd_ham_guard_2_postquest")

    private fun server(dir: Path, seed: Long = 5) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Pickpocketing.HAM_POUCH + 1)
        for (loot in HamStorerooms.GUARD_LOOT) if (loot.obj >= 0) objs[loot.obj] = ObjType(loot.obj, "Loot ${loot.obj}", stackable = true)
        for (roll in HamStorerooms.CHEST_LOOT) for ((obj, _) in roll.items) objs[obj] = ObjType(obj, "Jewel $obj")
        objs[HamHideout.LOCKPICK] = ObjType(HamHideout.LOCKPICK, "Lockpick")
        objs[Pickpocketing.HAM_POUCH] = ObjType(Pickpocketing.HAM_POUCH, "Coin pouch", stackable = true, iops = listOf("Open-all", "Open", null, null, "Destroy"))
        objs[COINS] = ObjType(COINS, "Coins", stackable = true)
        World(
            crcs,
            CollisionMap().apply { markLoaded(3166 shr 6, 9622 shr 6); markLoaded(2567 shr 6, 5185 shr 6) },
            // dttd_ham_trapdoor_state, bits 22 and 23 of dttd_base (794), as the 242 cache has it.
            varTypes = VarTypes(6_000, mapOf(HamStorerooms.TRAPDOOR_VARBIT to VarbitType(794, 22, 23))),
            random = GameRandom(seed), objTypes = ObjTypes(objs),
            locTypes = LocTypes(
                listOf(
                    type(HamStorerooms.TRAPDOOR, "null", listOf(null, null, null, null, null), multiloc = true),
                    type(HamStorerooms.LADDER, "Ladder", listOf("Climb-up", null, null, null, null)),
                    type(HamStorerooms.DOOR, "Door", listOf("Open", null, null, "Pick-lock", null)),
                    type(HamStorerooms.DOOR_OPEN, "Door", listOf("Close", null, null, null, null)),
                    type(HamStorerooms.CRACK, "Crack", listOf("Squeeze-through", null, null, null, null)),
                    type(15722, "Small chest", listOf("Open", null, null, null, null)),
                    type(HamStorerooms.LARGE_DOOR_LEFT, "Large door", listOf("Open", null, null, null, null)),
                    type(HamStorerooms.LARGE_DOOR_LEFT_OPEN, "Large door", listOf("Close", null, null, null, null)),
                    type(HamStorerooms.LARGE_DOOR_RIGHT, "Large door", listOf("Open", null, null, null, null)),
                    type(HamStorerooms.LARGE_DOOR_RIGHT_OPEN, "Large door", listOf("Close", null, null, null, null)),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { listOf(trapdoor, ladder, westDoor, eastDoor, southCrack, steelChest, largeLeft, largeRight).forEach(::add) },
            npcTypes = NpcTypes.of(guardA, guardB),
            npcSpawns = listOf(
                // A patrols the corridor west of the rooms; B stands in the south corridor, in sight of both doors.
                NpcSpawn(guardA, 2566, 5199, patrol = listOf(PatrolStop(2566, 5201, 3), PatrolStop(2566, 5199, 3)), source = "test"),
                NpcSpawn(guardB, 2571, 5189, source = "test"),
            ),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int) = await("$x, $z") { position?.takeIf { it == Coord(x, z, 0) } }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.seen(text: String) = messages().count { it.text == text }

    private fun HeadlessClient.awaitSeen(text: String, times: Int = 1) = await("$text x$times") { if (seen(text) >= times) Unit else null }

    private fun HeadlessClient.setLevel(skill: String, id: Int, level: Int) {
        command("setlevel $skill $level")
        await("$skill $level") { stat(id)?.takeIf { it.level == level } }
    }

    /** One Pick-lock on [door]: true when it worked. */
    private fun HeadlessClient.pick(door: ZoneLoc): Boolean {
        val done = seen(DONE)
        val fails = seen(FAIL)
        locOp(door.id, door.x, door.z, HamStorerooms.OP_PICK)
        return await("the pick's result") {
            when {
                seen(DONE) > done -> true
                seen(FAIL) > fails -> false
                else -> null
            }
        }
    }

    @Test
    fun `the hidden trapdoor is open after the quest and leads down to the storerooms' ladder and back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                val alice = server.world.playerNamed("alice")!!
                assertEquals(HamStorerooms.TRAPDOOR_OPEN, alice.vars.getVarbit(HamStorerooms.TRAPDOOR_VARBIT), "open, Climb-down")
                client.tele(3166, 9623)
                client.locOp(trapdoor.id, trapdoor.x, trapdoor.z, 1)
                client.awaitAt(HamStorerooms.BELOW.first, HamStorerooms.BELOW.second)
                client.locOp(ladder.id, ladder.x, ladder.z, 1)
                client.awaitAt(HamStorerooms.ABOVE.first, HamStorerooms.ABOVE.second)
            }
        }
    }

    @Test
    fun `a storeroom door is locked from the corridor, needs Thieving 23, and lets the picker in and out`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.tele(2566, 5192)
                client.locOp(westDoor.id, westDoor.x, westDoor.z, HamStorerooms.OP_OPEN)
                client.awaitSeen("[ham_storerooms.door_locked]")

                client.setLevel("thieving", THIEVING, 22)
                client.locOp(westDoor.id, westDoor.x, westDoor.z, HamStorerooms.OP_PICK)
                client.awaitSeen("[ham_storerooms.door_level level=23]")

                // Thieving 23 with no lockpick: 26%; each fail is 0.5 XP, and the player stays in the corridor.
                client.setLevel("thieving", THIEVING, 23)
                val bob = server.world.playerNamed("bob")!!
                val startTenths = bob.skills.tenths(osrsps.game.Skill.THIEVING)
                var fails = 0
                var wins = 0
                while (fails < 2) {
                    if (!client.pick(westDoor)) {
                        fails++
                        assertEquals(Coord(2566, 5192, 0), client.position)
                    } else {
                        wins++
                        client.awaitAt(2567, 5192)
                        client.tele(2566, 5192)
                    }
                }
                assertEquals(startTenths + 5 * fails + 38 * wins, bob.skills.tenths(osrsps.game.Skill.THIEVING), "0.5 XP a fail, 3.8 a success")

                // With a lockpick at 99 it always works (60 to 300): 3.8 XP, through to the room's side.
                client.setLevel("thieving", THIEVING, 99)
                client.command("item ${HamHideout.LOCKPICK} 1")
                client.await("the lockpick") { if (client.count(HamHideout.LOCKPICK) > 0) Unit else null }
                val xp = client.stat(THIEVING)!!.xp
                assertTrue(client.pick(westDoor))
                client.awaitAt(2567, 5192)
                client.await("3.8 XP") { client.stat(THIEVING)?.takeIf { it.xp >= xp + 3 } }

                // From inside, Open lets the player out, no pick. The door has shut again by then.
                client.await("the door shut") { if (server.world.locs.exists(westDoor)) Unit else null }
                client.locOp(westDoor.id, westDoor.x, westDoor.z, HamStorerooms.OP_OPEN)
                client.awaitAt(2566, 5192)
                // The east-edge door: in from the corridor east of it.
                client.tele(2577, 5192)
                assertTrue(client.pick(eastDoor))
                client.awaitAt(2576, 5192)
            }
        }
    }

    @Test
    fun `a guard who sees a pick attacks, unless a failed pickpocket has settled it, which also ends its patrol`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                val carol = server.world.playerNamed("carol")!!
                client.commandReply("god") { it.startsWith("You can") }
                client.setLevel("thieving", THIEVING, 20)
                val a = server.world.npcs.all.first { it.type.id == guardA.id }
                val b = server.world.npcs.all.first { it.type.id == guardB.id }
                client.tele(2567, 5199)
                val view = client.await("guard A in view") { client.npcs().firstOrNull { it.id == guardA.id } }
                var wins = 0
                while (!HamStorerooms.isSettled(a)) {
                    client.await("the wait to end") { if (server.world.tick >= carol.nextPickpocketTick && !server.world.statusEffects.isStunned(carol)) Unit else null }
                    val before = client.seen(SUCCESS)
                    val fails = client.seen("[pickpocket.fail who=guard]")
                    client.npcOp(view.index, HamStorerooms.GUARD_PICKPOCKET)
                    client.await("the pickpocket's result") {
                        if (client.seen(SUCCESS) > before || client.seen("[pickpocket.fail who=guard]") > fails) Unit else null
                    }
                    if (client.seen(SUCCESS) > before) wins++
                }
                assertEquals(Int.MAX_VALUE, a.patrolWait, "its patrol ends")
                assertEquals(2566 to 5199, a.destination?.let { it.x to it.z } ?: (a.x to a.z), "back to its post")
                assertEquals(0, client.seen("[ham_hideout.concussed]") + client.seen("[ham_hideout.bundled_out]"), "no concussions down here")

                // Both guards see the corridor west of the south-west door; only B, unsettled, turns on the picker.
                client.awaitTicks(Pickpocketing.COMBAT_TICKS + 2)
                client.setLevel("thieving", THIEVING, 99)
                client.tele(2566, 5192)
                client.locOp(westDoor.id, westDoor.x, westDoor.z, HamStorerooms.OP_PICK)
                client.awaitSeen("[ham_storerooms.door_pick]")
                client.await("guard B's attack") { if (b.target === carol) Unit else null }
                assertNull(a.target, "the settled guard leaves the picker be")
                assertTrue(wins >= 0)
            }
        }
    }

    @Test
    fun `a guard's pocket gives 22 XP and its table, keys among it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                val dave = server.world.playerNamed("dave")!!
                client.commandReply("god") { it.startsWith("You can") }
                client.setLevel("thieving", THIEVING, 99)
                client.tele(2571, 5190)
                val view = client.await("guard B in view") { client.npcs().firstOrNull { it.id == guardB.id } }
                val keys = listOf(HamStorerooms.STEEL_KEY, HamStorerooms.BRONZE_KEY, HamStorerooms.SILVER_KEY, HamStorerooms.IRON_KEY)
                repeat(60) {
                    if (keys.any { client.count(it) > 0 }) return@repeat
                    client.await("the wait to end") { if (server.world.tick >= dave.nextPickpocketTick && !server.world.statusEffects.isStunned(dave)) Unit else null }
                    val before = client.seen(SUCCESS)
                    val fails = client.seen("[pickpocket.fail who=guard]")
                    val xp = client.stat(THIEVING)!!.xp
                    client.npcOp(view.index, HamStorerooms.GUARD_PICKPOCKET)
                    client.await("the pickpocket's result") {
                        if (client.seen(SUCCESS) > before || client.seen("[pickpocket.fail who=guard]") > fails) Unit else null
                    }
                    if (client.seen(SUCCESS) > before) {
                        client.await("22.2 XP") { client.stat(THIEVING)?.takeIf { it.xp in xp + 22..xp + 23 } }
                    }
                    Unit
                }
                assertTrue(keys.any { client.count(it) > 0 }, "a key at 40% a pocket in 60 tries")
            }
        }
    }

    @Test
    fun `a small chest needs its key, which breaks, and gives the table's coins or jewellery`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(2573, 5192)
                client.locOp(steelChest.id, steelChest.x, steelChest.z, 1)
                client.awaitSeen("[ham_storerooms.chest_locked]")
                // A bronze key doesn't open the steel chest.
                client.command("item ${HamStorerooms.BRONZE_KEY} 1")
                client.await("the bronze key") { if (client.count(HamStorerooms.BRONZE_KEY) > 0) Unit else null }
                client.locOp(steelChest.id, steelChest.x, steelChest.z, 1)
                client.awaitSeen("[ham_storerooms.chest_locked]", 2)
                assertEquals(1, client.count(HamStorerooms.BRONZE_KEY))

                val jewels = HamStorerooms.CHEST_LOOT.flatMap { roll -> roll.items.map { it.first } }.toSet()
                client.command("item ${HamStorerooms.STEEL_KEY} 1")
                client.await("the steel key") { if (client.count(HamStorerooms.STEEL_KEY) > 0) Unit else null }
                client.locOp(steelChest.id, steelChest.x, steelChest.z, 1)
                client.awaitSeen("[ham_storerooms.key_breaks key=steel]")
                client.await("the key gone") { if (client.count(HamStorerooms.STEEL_KEY) == 0) Unit else null }
                assertEquals(1, client.count(HamStorerooms.BRONZE_KEY), "only its own key goes")
                assertTrue(client.inventory(BACKPACK).filterNotNull().all { it.id in jewels || it.id == HamStorerooms.BRONZE_KEY })
            }
        }
    }

    @Test
    fun `the cracks lead into the south-west room and out, and the large room's double door opens`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("frank").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(2569, 5189)
                client.locOp(southCrack.id, southCrack.x, southCrack.z, 1)
                client.awaitAt(2569, 5190)
                client.locOp(southCrack.id, southCrack.x, southCrack.z, 1)
                client.awaitAt(2569, 5189)

                client.tele(2572, 5203)
                client.locOp(largeLeft.id, largeLeft.x, largeLeft.z, 1)
                client.await("both leaves open") {
                    if (!server.world.locs.exists(largeLeft) && !server.world.locs.exists(largeRight)) Unit else null
                }
            }
        }
    }

    @Test
    fun `the chest table is the Wiki's 150 and the guard's keys are a tenth each`() {
        assertEquals(HamStorerooms.CHEST_SLOTS, HamStorerooms.CHEST_LOOT.sumOf { it.weight })
        assertEquals(75, HamStorerooms.CHEST_LOOT.first().weight)
        val weights = HamStorerooms.GUARD_LOOT.associate { it.obj to it.weight(1) }
        for (key in listOf(HamStorerooms.STEEL_KEY, HamStorerooms.BRONZE_KEY, HamStorerooms.SILVER_KEY, HamStorerooms.IRON_KEY)) {
            assertEquals(550, weights[key])
        }
        assertEquals(5_500, HamStorerooms.GUARD_LOOT.sumOf { it.weight(1) }, "the Wiki's whole table, in 5,500ths")
    }

    private companion object {
        const val THIEVING = 17
        const val COINS = 995
        const val BACKPACK = 93
        const val DONE = "[ham_storerooms.door_pick_done]"
        const val FAIL = "[ham_storerooms.door_pick_fail]"
        const val SUCCESS = "[pickpocket.success who=guard]"
    }
}
