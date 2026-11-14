package osrsps.testclient

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Farming step 4 through the headless client, at Lumbridge's tree patch: a plant pot filled, an acorn planted in it,
 * watered and grown into a sapling at the next farming tick; the sapling planted with a spade, grown, checked, chopped
 * to its stump and the stump dug up for roots; then a tree made ill, pruned well with secateurs, made ill again and
 * left to die, and cleared. The patch is read as the client draws it: `farming_transmit_a`, bits 0 to 7 of varp 529.
 * The rl-client script test-runner/examples/farming-tree.json does the same in the real client.
 */
class HeadlessFarmingTreeTest {
    private val clock = AtomicLong(1_794_614_400_000L)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        // Every obj known (bird nests can fall from the logs), the farming ones named.
        val objs = Array<ObjType?>(OBJS) { ObjType(it, "Obj $it") }
        for ((id, name) in mapOf(
            RAKE to "Rake", SPADE to "Spade", WEEDS to "Weeds", TROWEL to "Gardening trowel", POT_EMPTY to "Plant pot",
            POT_FILLED to "Plant pot", SEEDLING to "Oak seedling", WATERED to "Oak seedling (w)", SAPLING to "Oak sapling",
            AXE to "Bronze axe", LOGS to "Oak logs", ROOTS to "Oak roots", SECATEURS to "Secateurs", LEAVES to "Oak leaves",
        )) objs[id] = ObjType(id, name)
        objs[ACORN] = ObjType(ACORN, "Acorn", stackable = true)
        for (id in listOf(5331) + (5333..5340)) objs[id] = ObjType(id, "Watering can")
        World(
            crcs, CollisionMap().apply { markLoaded(X shr 6, Z shr 6) }, objTypes = ObjTypes(objs),
            varTypes = VarTypes(6_000, mapOf(TRANSMIT_A to VarbitType(529, 0, 7)), 21_000),
            random = GameRandom(1),
            locTypes = LocTypes(mapOf(TREE to LocType(TREE, "null", List(5) { null }, LocCollisionType(width = 3, length = 3), multiloc = true))),
            staticLocs = StaticLocs().apply { add(ZoneLoc(TREE, LocShape.CENTREPIECE_STRAIGHT, 0, TREE_X, TREE_Z, 0)) },
            farmingClock = clock::get,
            farmingDiseases = false,
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.slotOf(id: Int) = inventory(BACKPACK).indexOfFirst { it?.id == id }

    private fun HeadlessClient.seen(text: String, timeoutMillis: Long = 10_000) =
        await(text, timeoutMillis) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.tree() = varp(529) and 0xFF

    private fun HeadlessClient.awaitTree(value: Int, timeoutMillis: Long = 10_000) =
        await("the tree patch at $value", timeoutMillis) { if (tree() == value) Unit else null }

    private fun HeadlessClient.xp() = stat(FARMING)?.xp ?: 0

    private fun HeadlessClient.give(item: Int, count: Int = 1) {
        val before = count(item)
        command("item $item $count")
        await("item $item") { if (count(item) >= before + count) Unit else null }
    }

    private fun HeadlessClient.awaitCount(id: Int, count: Int) =
        await("$count of item $id (${count(id)})") { if (count(id) == count) Unit else null }

    private fun HeadlessClient.useOnTree(item: Int) = locTarget(TREE, TREE_X, TREE_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(item), item)

    private fun HeadlessClient.useOn(item: Int, target: Int) =
        ifButtonT(InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(item), item, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(target), target)

    private fun HeadlessClient.toLumbridge() {
        await("the login rebuild") { position }
        command("tele $X $Z")
        await("at the Lumbridge tree patch") { position?.takeIf { it.x == X && it.z == Z } }
        // 99 Farming: a stump always comes up, with four roots; no level-up box stops a chop.
        command("setlevel farming 99")
        command("setlevel woodcutting 99")
        await("99 Farming") { if (stat(FARMING)?.level == 99) Unit else null }
        command("farmclock pause")
    }

    @Test
    fun `a seedling grows into a sapling that is planted, grown, checked, chopped and dug up`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.toLumbridge()
                assertEquals(0, client.tree())
                client.give(RAKE)
                client.locOp(TREE, TREE_X, TREE_Z, 1)
                client.seen("[farming.raked]")
                client.awaitTree(3)

                // A plant pot filled from the bare patch; no trowel needed for that.
                client.give(POT_EMPTY)
                client.useOnTree(POT_EMPTY)
                client.seen("[farming.pot_filled]")
                client.awaitCount(POT_FILLED, 1)

                // An acorn in the pot (with a trowel), watered, and a sapling at the next farming tick.
                client.give(ACORN)
                client.useOn(ACORN, POT_FILLED)
                client.seen("[farming.need_trowel]")
                client.give(TROWEL)
                client.useOn(ACORN, POT_FILLED)
                client.seen("[farming.seedling_planted seed=acorn]")
                client.awaitCount(SEEDLING, 1)
                client.give(CAN_8)
                client.useOn(CAN_8, SEEDLING)
                client.seen("[farming.seedling_watered]")
                client.awaitCount(WATERED, 1)
                client.awaitCount(CAN_8 - 1, 1)
                client.command("farmtick 1")
                client.awaitCount(SAPLING, 1)
                // The bare patch grew a weed meanwhile.
                client.awaitTree(2)
                val beforeWeed = client.xp()
                client.locOp(TREE, TREE_X, TREE_Z, 1)
                client.awaitTree(3)
                client.await("the weed's XP") { if (client.xp() == beforeWeed + 4) Unit else null }

                // Planted with a spade: the pot back and the planting's 14 XP now.
                val before = client.xp()
                client.useOnTree(SAPLING)
                client.seen("[farming.need_spade]")
                client.give(SPADE)
                client.useOnTree(SAPLING)
                client.seen("[farming.sapling_planted sapling=oak sapling patch=[farming.patch_tree]]")
                client.awaitTree(8)
                client.awaitCount(POT_EMPTY, 1)
                client.await("the planting XP") { if (client.xp() == before + 14) Unit else null }

                // Four 40-minute growths (any 32 farming ticks hold four), then Check-health for 467.3 XP.
                client.command("farmtick 32")
                client.awaitTree(12)
                client.locOp(TREE, TREE_X, TREE_Z, 1)
                client.seen("[farming.checked]")
                client.awaitTree(13)
                client.await("the check-health XP") { if (client.xp() == (before * 10 + 140 + 4673) / 10) Unit else null }

                // Chopped until it falls (1 log in 8), with room made if the pack fills first.
                client.give(AXE)
                client.locOp(TREE, TREE_X, TREE_Z, 1)
                fun fulls() = client.messages().count { it.text == "[woodcutting.inventory_full]" }
                var full = fulls()
                val end = System.currentTimeMillis() + 120_000
                while (client.tree() != 14) {
                    check(System.currentTimeMillis() < end) { "The oak never fell" }
                    if (fulls() > full) {
                        full = fulls()
                        client.command("empty")
                        client.await("an empty pack") { if (client.count(LOGS) == 0) Unit else null }
                        client.give(AXE)
                        client.locOp(TREE, TREE_X, TREE_Z, 1)
                    }
                    Thread.sleep(50)
                }
                client.seen("[woodcutting.get_logs logs=oak logs]")

                // The stump dug up: four roots at 99, never a miss, and bare soil.
                client.command("empty")
                client.await("an empty pack") { if (client.count(AXE) == 0) Unit else null }
                client.locOp(TREE, TREE_X, TREE_Z, 1)
                client.seen("[farming.need_spade]")
                client.give(SPADE)
                client.locOp(TREE, TREE_X, TREE_Z, 1)
                client.seen("[farming.stump_cleared]")
                client.awaitTree(3)
                client.awaitCount(ROOTS, 4)
            }
        }
    }

    @Test
    fun `a diseased tree is pruned well with secateurs, and one left ill dies and is cleared`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.toLumbridge()
                client.give(RAKE)
                client.give(SPADE)
                client.locOp(TREE, TREE_X, TREE_Z, 1)
                client.awaitTree(3)
                client.give(SAPLING)
                client.useOnTree(SAPLING)
                client.awaitTree(8)
                // The first growth can't bring disease.
                client.command("farmtick 8")
                client.awaitTree(9)

                client.command("farmdisease lumbridge_tree")
                client.awaitTree(73)
                client.locOp(TREE, TREE_X, TREE_Z, 1)
                client.seen("[farming.need_secateurs]")
                client.give(SECATEURS)
                client.locOp(TREE, TREE_X, TREE_Z, 1)
                client.seen("[farming.pruned]", 60_000)
                client.awaitTree(9)
                client.awaitCount(LEAVES, 1)

                // Ill again and left: dead at the next 80-minute tick, and cleared with a spade for no XP.
                client.command("farmdisease lumbridge_tree")
                client.awaitTree(73)
                client.command("farmtick 16")
                client.awaitTree(137)
                val before = client.xp()
                client.locOp(TREE, TREE_X, TREE_Z, 1)
                client.seen("[farming.cleared]")
                client.awaitTree(3)
                assertEquals(before, client.xp())
            }
        }
    }

    private companion object {
        const val OBJS = 25_000
        const val BACKPACK = 93
        const val FARMING = 19
        const val TRANSMIT_A = 4771
        const val TREE = 8391
        const val TREE_X = 3192
        const val TREE_Z = 3230
        const val X = 3193
        const val Z = 3229
        const val RAKE = 5341
        const val SPADE = 952
        const val WEEDS = 6055
        const val TROWEL = 5325
        const val POT_EMPTY = 5350
        const val POT_FILLED = 5354
        const val ACORN = 5312
        const val SEEDLING = 5358
        const val WATERED = 5364
        const val SAPLING = 5370
        const val CAN_8 = 5340
        const val AXE = 1351
        const val LOGS = 1521
        const val ROOTS = 6043
        const val SECATEURS = 5329
        const val LEAVES = 6022
    }
}
