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
 * Farming step 5 through the headless client, at Catherby's fruit tree patch: an apple sapling planted with a spade,
 * grown through six 160-minute stages, checked, its six apples picked, a seventh grown at the next 40-minute tick and
 * picked, the bare tree chopped down to its stump and the stump cleared; then a tree made ill, pruned well with
 * secateurs, made ill again and left to die, and cleared. The patch is `farming_transmit_a`, bits 0 to 7 of varp 529.
 * The rl-client script test-runner/examples/farming-fruit-tree.json does the same in the real client.
 */
class HeadlessFarmingFruitTreeTest {
    private val clock = AtomicLong(1_794_614_400_000L)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = Array<ObjType?>(OBJS) { ObjType(it, "Obj $it") }
        for ((id, name) in mapOf(
            RAKE to "Rake", SPADE to "Spade", WEEDS to "Weeds", POT_EMPTY to "Plant pot", SAPLING to "Apple sapling",
            AXE to "Rune axe", APPLE to "Cooking apple", SECATEURS to "Secateurs", LEAVES to "Leaves",
        )) objs[id] = ObjType(id, name)
        World(
            crcs, CollisionMap().apply { markLoaded(X shr 6, Z shr 6) }, objTypes = ObjTypes(objs),
            varTypes = VarTypes(6_000, mapOf(TRANSMIT_A to VarbitType(529, 0, 7)), 21_000),
            random = GameRandom(1),
            locTypes = LocTypes(mapOf(PATCH to LocType(PATCH, "null", List(5) { null }, LocCollisionType(width = 2, length = 2), multiloc = true))),
            staticLocs = StaticLocs().apply { add(ZoneLoc(PATCH, LocShape.CENTREPIECE_STRAIGHT, 0, PATCH_X, PATCH_Z, 0)) },
            farmingClock = clock::get,
            farmingDiseases = false,
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.slotOf(id: Int) = inventory(BACKPACK).indexOfFirst { it?.id == id }

    private fun HeadlessClient.seen(text: String, timeoutMillis: Long = 10_000) =
        await(text, timeoutMillis) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.patch() = varp(529) and 0xFF

    private fun HeadlessClient.awaitPatch(value: Int, timeoutMillis: Long = 10_000) =
        await("the fruit tree patch at $value (${patch()})", timeoutMillis) { if (patch() == value) Unit else null }

    private fun HeadlessClient.xp() = stat(FARMING)?.xp ?: 0

    private fun HeadlessClient.give(item: Int, count: Int = 1) {
        val before = count(item)
        command("item $item $count")
        await("item $item") { if (count(item) >= before + count) Unit else null }
    }

    private fun HeadlessClient.awaitCount(id: Int, count: Int) =
        await("$count of item $id (${count(id)})") { if (count(id) == count) Unit else null }

    private fun HeadlessClient.awaitXp(xp: Int) = await("$xp XP (${xp()})") { if (xp() == xp) Unit else null }

    private fun HeadlessClient.useOnPatch(item: Int) = locTarget(PATCH, PATCH_X, PATCH_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(item), item)

    private fun HeadlessClient.op1() = locOp(PATCH, PATCH_X, PATCH_Z, 1)

    private fun HeadlessClient.toCatherby() {
        await("the login rebuild") { position }
        command("tele $X $Z")
        await("at the Catherby fruit tree patch") { position?.takeIf { it.x == X && it.z == Z } }
        command("setlevel farming 99")
        await("99 Farming") { if (stat(FARMING)?.level == 99) Unit else null }
        command("farmclock pause")
    }

    /** Raked and an apple sapling planted with a spade: the patch at 8. */
    private fun HeadlessClient.plantApple() {
        give(RAKE)
        give(SPADE)
        op1()
        seen("[farming.raked]")
        awaitPatch(3)
        give(SAPLING)
        useOnPatch(SAPLING)
        awaitPatch(8)
    }

    @Test
    fun `an apple tree grows, is checked, gives six apples and a seventh later, and is chopped down and cleared`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.toCatherby()
                assertEquals(0, client.patch())
                client.give(RAKE)
                client.give(SPADE)
                client.op1()
                client.seen("[farming.raked]")
                client.awaitPatch(3)

                // Planted with a spade: the pot back, and no XP until the tree is checked.
                val base = client.xp() * 10
                client.give(SAPLING)
                client.useOnPatch(SAPLING)
                client.seen("[farming.sapling_planted sapling=apple sapling patch=[farming.patch_fruit_tree]]")
                client.awaitPatch(8)
                client.awaitCount(POT_EMPTY, 1)
                assertEquals(base / 10, client.xp())

                // Six 160-minute growths (any 192 farming ticks hold six), then Check-health for 1,199.5 XP and the planting's 22.
                client.command("farmtick 191")
                client.awaitPatch(13)
                client.command("farmtick 1")
                client.awaitPatch(34)
                client.op1()
                client.seen("[farming.checked]")
                client.awaitPatch(20)
                client.awaitXp((base + 220 + 11995) / 10)

                // An axe on a tree with apples is refused; the six are picked one by one, 8.5 XP each.
                client.give(AXE)
                client.useOnPatch(AXE)
                client.seen("[farming.pick_first]")
                client.op1()
                client.seen("[farming.fruit_gone]", 30_000)
                client.awaitPatch(14)
                client.awaitCount(APPLE, 6)
                client.awaitXp((base + 220 + 11995 + 6 * 85) / 10)
                assertEquals(6, client.messages().count { it.text == "[farming.fruit_picked produce=cooking apple]" })

                // One more apple at the next 40-minute tick.
                client.command("farmtick 8")
                client.awaitPatch(15)
                client.op1()
                client.seen("[farming.fruit_gone]")
                client.awaitCount(APPLE, 7)
                client.awaitPatch(14)

                // With none left, Chop-down (a rune axe at Woodcutting 1 is fine): one swing to the stump, no logs; the stump cleared with a spade, no roots.
                client.op1()
                client.seen("[farming.fruit_chopped]")
                client.awaitPatch(33)
                val before = client.xp()
                client.op1()
                client.seen("[farming.stump_cleared]")
                client.awaitPatch(3)
                assertEquals(before, client.xp())
                assertEquals(7, client.count(APPLE))
            }
        }
    }

    @Test
    fun `a diseased fruit tree is pruned well with secateurs, and one left ill dies and is cleared`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.toCatherby()
                client.plantApple()
                // The first growth can't bring disease.
                client.command("farmtick 32")
                client.awaitPatch(9)

                client.command("farmdisease catherby_fruit_tree")
                client.awaitPatch(21)
                client.op1()
                client.seen("[farming.need_secateurs]")
                client.give(SECATEURS)
                client.op1()
                client.seen("[farming.pruned]", 60_000)
                client.awaitPatch(9)
                client.awaitCount(LEAVES, 1)

                // Ill again and left: dead at the next disease tick, and cleared with a spade for the planting's 22 XP.
                client.command("farmdisease catherby_fruit_tree")
                client.awaitPatch(21)
                client.command("farmtick 64")
                client.awaitPatch(27)
                val before = client.xp()
                client.op1()
                client.seen("[farming.cleared]")
                client.awaitPatch(3)
                client.awaitXp(before + 22)
            }
        }
    }

    private companion object {
        const val OBJS = 25_000
        const val BACKPACK = 93
        const val FARMING = 19
        const val TRANSMIT_A = 4771
        const val PATCH = 7965
        const val PATCH_X = 2860
        const val PATCH_Z = 3433
        const val X = 2860
        const val Z = 3432
        const val RAKE = 5341
        const val SPADE = 952
        const val WEEDS = 6055
        const val POT_EMPTY = 5350
        const val SAPLING = 5496
        const val AXE = 1359 // a rune axe: fruit trees take any axe at any Woodcutting level
        const val APPLE = 1955
        const val SECATEURS = 5329
        const val LEAVES = 6020
    }
}
