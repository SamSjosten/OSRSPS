package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.content.FarmerBea
import osrsps.game.content.GuidorsHouse
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.shop.ShopScreen
import osrsps.game.shop.ShopStock
import osrsps.game.shop.ShopType
import osrsps.game.shop.ShopTypes
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Post-quest content step 3 through the headless client: Guidor's gate both ways, his bedroom door refused by Julie
 * without the priest gown and opening with it, and Farmer Bea's talk every way, her dart swap and the supplies she gives.
 * The rl-client script test-runner/examples/guidor-bea.json does the same in the real client.
 */
class HeadlessGuidorBeaTest {
    private val wear = listOf(null, "Wear", null, null, "Drop")
    private val top = ObjType(GuidorsHouse.PRIEST_GOWN_TOP, "Priest gown", iops = wear, wearpos1 = WearPos.BODY)
    private val bottom = ObjType(GuidorsHouse.PRIEST_GOWN_BOTTOM, "Priest gown", iops = wear, wearpos1 = WearPos.LEGS)
    private val waspDart = ObjType(FarmerBea.WASP_DART, "Wasp dart", stackable = true)
    private val killerDart = ObjType(FarmerBea.KILLER_WASP_DART, "Killer wasp dart", stackable = true)
    private val supplies = ObjType(FarmerBea.SUPPLIES, "Bea's supplies", iops = listOf("Open", null, null, null, "Destroy"))
    private val rewards = FarmerBea.REWARDS.map { ObjType(it.item, "Reward ${it.item}", stackable = it.item !in listOf(LEATHER, HARD_LEATHER)) }

    private val julie = NpcType(GuidorsHouse.JULIE, "Julie", internalName = "guidors_wife")
    private val bea = NpcType(FarmerBea.BEA, "Farmer Bea", ops = listOf("Talk-to", null, "Trade", "Swap-darts", null), internalName = "baw_bea_3ops")

    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = (ops.toList() + List(5 - ops.size) { null }), collision = LocCollisionType(width = 1, length = 1))

    private val gateLeft = ZoneLoc(GuidorsHouse.GATE_LEFT, LocShape.WALL_STRAIGHT, 0, 3264, 3405, 0)
    private val gateRight = ZoneLoc(GuidorsHouse.GATE_RIGHT, LocShape.WALL_STRAIGHT, 0, 3264, 3406, 0)
    private val door = ZoneLoc(GuidorsHouse.DOOR, LocShape.WALL_STRAIGHT, 2, 3282, 3382, 0)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3263 shr 6, 3405 shr 6); markLoaded(3264 shr 6, 3405 shr 6); markLoaded(3282 shr 6, 3382 shr 6); markLoaded(3316 shr 6, 3376 shr 6) },
            objTypes = ObjTypes.of(top, bottom, waspDart, killerDart, supplies, *rewards.toTypedArray()),
            locTypes = LocTypes(
                listOf(
                    type(GuidorsHouse.GATE_LEFT, "Gate", "Open"), type(GuidorsHouse.GATE_RIGHT, "Gate", "Open"),
                    type(GuidorsHouse.GATE_LEFT_OPEN, "null"), type(GuidorsHouse.GATE_RIGHT_OPEN, "null"),
                    type(GuidorsHouse.DOOR, "Bedroom door", "Open"), type(GuidorsHouse.DOOR_OPEN, "Bedroom door"),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { listOf(gateLeft, gateRight, door).forEach(::add) },
            npcTypes = NpcTypes.of(julie, bea),
            npcSpawns = listOf(NpcSpawn(bea, 3316, 3376, wander = 0, source = "test")),
            shopTypes = ShopTypes(
                listOf(
                    ShopType(
                        "beas_hunting_gear", "Bea's Hunting Gear", invId = 33, size = 1, general = false, sellAt = 1300, buyAt = 400, changePer = 20,
                        stock = listOf(ShopStock(waspDart, 5, 10)), npcs = listOf(bea.id),
                    ),
                ),
            ),
            random = GameRandom(7),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int = 0) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int) =
        assertEquals(Coord(x, z, 0), await("$x, $z") { position?.takeIf { it == Coord(x, z, 0) } })

    private fun HeadlessClient.chat(key: String) {
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
        continueDialogue()
    }

    private fun HeadlessClient.pick(option: Int, vararg keys: String) {
        val texts = keys.map { "[$it]" }
        awaitDialogue("the menu $texts") { it is DialogueView.Menu && it.options == texts }
        chooseOption(option)
    }

    private fun HeadlessClient.over() = await("the talk over") { if (dialogueView == null && ShopScreen.MAIN !in openInterfaces()) Unit else null }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.wear(id: Int, pos: Int) {
        command("item $id 1")
        val slot = await("$id") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }
        ifButton(INVENTORY, INVENTORY_ITEMS, IOP_BUTTON_OPS[WEAR - 1], slot, id)
        await("$id worn") { inventory(WORN).getOrNull(pos)?.takeIf { it.id == id } }
    }

    @Test
    fun `Guidor's gate lets players through both ways, and his door wants the priest gown`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3263, 3405)
                client.locOp(gateLeft.id, gateLeft.x, gateLeft.z, GuidorsHouse.OPEN)
                client.awaitAt(3264, 3405)
                client.awaitTicks(3)
                // Either leaf, keeping the player's row.
                client.locOp(gateRight.id, gateRight.x, gateRight.z, GuidorsHouse.OPEN)
                client.awaitAt(3263, 3405)
                client.awaitTicks(3)
                // From beside the gate along the fence: onto its crossing tile, then through.
                client.tele(3264, 3407)
                client.locOp(gateRight.id, gateRight.x, gateRight.z, GuidorsHouse.OPEN)
                client.awaitAt(3263, 3406)

                client.tele(3281, 3382)
                client.locOp(door.id, door.x, door.z, GuidorsHouse.OPEN)
                for (key in listOf("julie.door_1", "julie.door_2", "julie.door_3", "julie.door_4")) client.chat(key)
                client.over()
                assertEquals(Coord(3282, 3382, 0), client.position)

                client.wear(GuidorsHouse.PRIEST_GOWN_TOP, WearPos.BODY)
                // Half the gown isn't enough.
                client.locOp(door.id, door.x, door.z, GuidorsHouse.OPEN)
                client.chat("julie.door_1")
                client.chat("julie.door_2")
                client.chat("julie.door_3")
                client.chat("julie.door_4")
                client.over()
                client.wear(GuidorsHouse.PRIEST_GOWN_BOTTOM, WearPos.LEGS)
                client.locOp(door.id, door.x, door.z, GuidorsHouse.OPEN)
                client.awaitAt(3283, 3382)
                val world = server.world
                client.await("the door shut") { world.locs[3282, 3382, 0, 0]?.takeIf { it.id == GuidorsHouse.DOOR } }
                client.command("empty")
                // Out again from Guidor's side.
                client.locOp(door.id, door.x, door.z, GuidorsHouse.OPEN)
                client.awaitAt(3282, 3382)
            }
        }
    }

    @Test
    fun `Farmer Bea talks every way, swaps darts for her supplies, and the supplies open`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3316, 3377)
                val menu = arrayOf("farm", "for_sale", "darts", "tips", "bye")
                fun talk() {
                    val npc = client.await("Bea in view") { client.npcs().firstOrNull { it.id == bea.id } }
                    client.npcOp(npc.index, FarmerBea.TALK_TO)
                    client.chat("farmer_bea.greeting")
                }
                fun pick(n: Int) = client.pick(n, *menu.map { "farmer_bea.$it" }.toTypedArray())
                val tips = arrayOf("farmer_bea.tips_local", "farmer_bea.tips_far", "farmer_bea.tips_back")
                talk()
                pick(1)
                listOf("farm", "farm_1", "farm_2", "farm_3").forEach { client.chat("farmer_bea.$it") }
                pick(3)
                listOf("darts", "darts_rule").forEach { client.chat("farmer_bea.$it") }
                pick(4)
                listOf("tips", "tips_1").forEach { client.chat("farmer_bea.$it") }
                client.pick(1, *tips)
                listOf("tips_local", "tips_local_1", "tips_local_2", "tips_local_3").forEach { client.chat("farmer_bea.$it") }
                client.pick(2, *tips)
                listOf("tips_far", "tips_far_1", "tips_far_2").forEach { client.chat("farmer_bea.$it") }
                client.pick(3, *tips)
                pick(5)
                client.chat("farmer_bea.bye")
                client.over()
                talk()
                pick(2)
                listOf("for_sale", "for_sale_1").forEach { client.chat("farmer_bea.$it") }
                client.await("the shop") { if (ShopScreen.MAIN in client.openInterfaces()) Unit else null }
                client.closeModal()
                client.over()

                // Swap-darts: too few, then 35 wasp darts, then 20 killer wasp darts.
                val npc = client.await("Bea in view") { client.npcs().firstOrNull { it.id == bea.id } }
                client.command("item ${FarmerBea.WASP_DART} 34")
                client.await("34 darts") { if (client.count(FarmerBea.WASP_DART) == 34) Unit else null }
                client.npcOp(npc.index, FarmerBea.SWAP_DARTS)
                client.await("not enough") { client.messages().firstOrNull { it.text == "[farmer_bea.not_enough]" } }
                client.command("item ${FarmerBea.WASP_DART} 6")
                client.command("item ${FarmerBea.KILLER_WASP_DART} 20")
                client.await("the darts") { if (client.count(FarmerBea.WASP_DART) == 40 && client.count(FarmerBea.KILLER_WASP_DART) == 20) Unit else null }
                client.npcOp(npc.index, FarmerBea.SWAP_DARTS)
                client.await("a bag for wasp darts") { if (client.count(FarmerBea.SUPPLIES) == 1 && client.count(FarmerBea.WASP_DART) == 5) Unit else null }
                client.awaitTicks(1)
                client.npcOp(npc.index, FarmerBea.SWAP_DARTS)
                client.await("a bag for killer wasp darts") { if (client.count(FarmerBea.SUPPLIES) == 2 && client.count(FarmerBea.KILLER_WASP_DART) == 0) Unit else null }

                // Open both: each bag gives one of the table's rewards.
                repeat(2) {
                    val slot = client.await("a bag") { client.inventory(BACKPACK).indexOfFirst { it?.id == FarmerBea.SUPPLIES }.takeIf { it >= 0 } }
                    val left = client.count(FarmerBea.SUPPLIES)
                    client.ifButton(INVENTORY, INVENTORY_ITEMS, IOP_BUTTON_OPS[0], slot, FarmerBea.SUPPLIES)
                    client.await("the bag opened") { if (client.count(FarmerBea.SUPPLIES) == left - 1) Unit else null }
                }
                // Too little room for six leather: the bag stays shut, however often it's tried.
                val before = client.inventory(BACKPACK).filterNotNull().map { it.id to it.count }
                client.command("item ${FarmerBea.SUPPLIES} 1")
                client.await("a third bag") { if (client.count(FarmerBea.SUPPLIES) == 1) Unit else null }
                val held = client.inventory(BACKPACK).count { it != null }
                client.command("item ${GuidorsHouse.PRIEST_GOWN_TOP} ${BACKPACK_SIZE - 4 - held}")
                client.await("the backpack nearly full") { if (client.inventory(BACKPACK).count { it != null } == BACKPACK_SIZE - 4) Unit else null }
                val bag = client.inventory(BACKPACK).indexOfFirst { it?.id == FarmerBea.SUPPLIES }
                repeat(3) {
                    client.ifButton(INVENTORY, INVENTORY_ITEMS, IOP_BUTTON_OPS[0], bag, FarmerBea.SUPPLIES)
                    client.awaitTicks(1)
                }
                assertEquals(1, client.count(FarmerBea.SUPPLIES), "the bag unopened")
                client.await("no room") { client.messages().firstOrNull { it.text == "[game.no_space]" } }
                assertEquals(before, client.inventory(BACKPACK).filterNotNull().filter { it.id != FarmerBea.SUPPLIES && it.id != GuidorsHouse.PRIEST_GOWN_TOP }.map { it.id to it.count })

                // Leather and hard leather come one a slot; the rest stack.
                val got = client.inventory(BACKPACK).filterNotNull().filter { obj -> FarmerBea.REWARDS.any { it.item == obj.id } }
                    .groupBy { it.id }.mapValues { (_, objs) -> objs.sumOf { it.count } }
                assertTrue(got.isNotEmpty(), "rewards in $got")
                for ((id, count) in got) {
                    val reward = FarmerBea.REWARDS.first { it.item == id }
                    assertTrue(count in reward.min..reward.max * 2, "$id x $count")
                }
            }
        }
    }

    @Test
    fun `the supplies roll every reward in its range, at about the Wiki's rates`() {
        val world = World(IntArray(0), CollisionMap(), random = GameRandom(3))
        val counts = HashMap<Int, Int>()
        repeat(60_000) {
            val (item, count) = FarmerBea.roll(world)
            val reward = FarmerBea.REWARDS.first { it.item == item }
            assertTrue(count in reward.min..reward.max)
            counts.merge(item, 1, Int::plus)
        }
        for (reward in FarmerBea.REWARDS) {
            val got = counts[reward.item] ?: 0
            assertTrue(got in reward.weight * 1000 - 400..reward.weight * 1000 + 400, "${reward.item}: $got of 60,000")
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val BACKPACK_SIZE = 28
        const val WORN = 94
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
        const val WEAR = 2
        val IOP_BUTTON_OPS = intArrayOf(2, 3, 4, 6, 7)
        const val LEATHER = 1741
        const val HARD_LEATHER = 1743
    }
}
