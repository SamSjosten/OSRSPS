package osrsps.testclient

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
 * Farming step 6 through the headless client. At the Lumbridge hops patch: four hammerstone seeds planted with a seed
 * dibber, watered, grown through four 10-minute stages, harvested with a spade until the patch is empty (the planting
 * XP with the last hop), then three jute seeds planted. At the Champions' Guild bush patch: a redberry bush grown
 * through five 20-minute stages, checked, its berries picked (four at least), a berry grown back at the next 20-minute
 * tick, and the bush cleared with op1 once bare; then a bush made ill, pruned well with secateurs, grown, checked and
 * dug up with op5, Clear, while it still has berries. Both patches are `farming_transmit_a`, bits 0 to 7 of varp 529.
 * The rl-client script test-runner/examples/farming-hops-bush.json does the same in the real client.
 */
class HeadlessFarmingHopsBushTest {
    private val clock = AtomicLong(1_794_614_400_000L)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = Array<ObjType?>(OBJS) { ObjType(it, "Obj $it") }
        for ((id, name) in mapOf(
            RAKE to "Rake", SPADE to "Spade", DIBBER to "Seed dibber", WEEDS to "Weeds", CAN to "Watering can(8)",
            CAN_7 to "Watering can(7)", HAMMERSTONE_SEED to "Hammerstone seed", HAMMERSTONE to "Hammerstone hops",
            JUTE_SEED to "Jute seed", REDBERRY_SEED to "Redberry seed", REDBERRIES to "Redberries",
            SECATEURS to "Secateurs", LEAVES to "Leaves",
        )) objs[id] = ObjType(id, name)
        World(
            crcs, CollisionMap().apply {
                markLoaded(HOPS_X shr 6, HOPS_Z shr 6)
                markLoaded(BUSH_X shr 6, BUSH_Z shr 6)
            }, objTypes = ObjTypes(objs),
            varTypes = VarTypes(6_000, mapOf(TRANSMIT_A to VarbitType(529, 0, 7)), 21_000),
            random = GameRandom(1),
            locTypes = LocTypes(mapOf(
                HOPS to LocType(HOPS, "null", List(5) { null }, LocCollisionType(width = 1, length = 1), multiloc = true),
                BUSH to LocType(BUSH, "null", List(5) { null }, LocCollisionType(width = 2, length = 2), multiloc = true),
            )),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(HOPS, LocShape.CENTREPIECE_STRAIGHT, 0, HOPS_X, HOPS_Z, 0))
                add(ZoneLoc(BUSH, LocShape.CENTREPIECE_STRAIGHT, 0, BUSH_X, BUSH_Z, 0))
            },
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
        await("the patch at $value (${patch()})", timeoutMillis) { if (patch() == value) Unit else null }

    private fun HeadlessClient.xp() = stat(FARMING)?.xp ?: 0

    private fun HeadlessClient.give(item: Int, count: Int = 1) {
        val before = count(item)
        command("item $item $count")
        await("item $item") { if (count(item) >= before + count) Unit else null }
    }

    private fun HeadlessClient.awaitCount(id: Int, count: Int) =
        await("$count of item $id (${count(id)})") { if (count(id) == count) Unit else null }

    private fun HeadlessClient.awaitXp(xp: Int) = await("$xp XP (${xp()})") { if (xp() == xp) Unit else null }

    private fun HeadlessClient.to(x: Int, z: Int) {
        command("tele $x $z")
        await("at $x, $z") { position?.takeIf { it.x == x && it.z == z } }
    }

    private fun HeadlessClient.setUp() {
        await("the login rebuild") { position }
        command("setlevel farming 99")
        await("99 Farming") { if (stat(FARMING)?.level == 99) Unit else null }
        command("farmclock pause")
        give(RAKE)
        give(SPADE)
        give(DIBBER)
    }

    private fun HeadlessClient.useOnHops(item: Int) = locTarget(HOPS, HOPS_X, HOPS_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(item), item)

    private fun HeadlessClient.useOnBush(item: Int) = locTarget(BUSH, BUSH_X, BUSH_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(item), item)

    private fun HeadlessClient.bushOp(op: Int) = locOp(BUSH, BUSH_X, BUSH_Z, op)

    /** Raked and a redberry seed planted with a dibber: the bush patch at 5. */
    private fun HeadlessClient.plantRedberry() {
        bushOp(1)
        seen("[farming.raked]")
        awaitPatch(3)
        give(REDBERRY_SEED)
        useOnBush(REDBERRY_SEED)
        awaitPatch(5)
    }

    @Test
    fun `hammerstone hops grow from four seeds, are watered and harvested to the last, and jute takes three`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.setUp()
                client.to(HOPS_X, HOPS_Z - 1)
                assertEquals(0, client.patch())
                client.locOp(HOPS, HOPS_X, HOPS_Z, 1)
                client.seen("[farming.raked]")
                client.awaitPatch(3)

                // Three seeds are too few; four go in, and no XP until the patch is emptied.
                client.give(HAMMERSTONE_SEED, 3)
                client.useOnHops(HAMMERSTONE_SEED)
                client.seen("[farming.need_seeds count=4 patch=[farming.patch_hops]]")
                client.give(HAMMERSTONE_SEED)
                val base = client.xp() * 10
                client.useOnHops(HAMMERSTONE_SEED)
                client.seen("[farming.planted count=4 seeds=hammerstone seed patch=[farming.patch_hops]]")
                client.awaitPatch(4)
                assertEquals(0, client.count(HAMMERSTONE_SEED))
                assertEquals(base / 10, client.xp())

                client.give(CAN)
                client.useOnHops(CAN)
                client.seen("[farming.watered crop=hammerstone hops]")
                client.awaitPatch(132)

                // Four 10-minute growths (any 8 farming ticks hold four): grown. A pack with room for every hop.
                client.command("farmtick 8")
                client.awaitPatch(8)
                client.command("empty")
                client.awaitCount(SPADE, 0)
                client.give(SPADE)
                client.give(DIBBER)
                client.locOp(HOPS, HOPS_X, HOPS_Z, 1)
                client.seen("[farming.harvest_start patch=[farming.patch_hops]]")
                client.seen("[farming.empty patch=[farming.patch_hops]]", 120_000)
                client.awaitPatch(3)
                val hops = client.count(HAMMERSTONE)
                assertTrue(hops >= 3, "at least the patch's three lives: $hops")
                client.awaitXp((base + hops * 100 + 90) / 10)

                client.give(JUTE_SEED, 3)
                client.useOnHops(JUTE_SEED)
                client.seen("[farming.planted count=3 seeds=jute seed patch=[farming.patch_hops]]")
                client.awaitPatch(84)
            }
        }
    }

    @Test
    fun `a redberry bush grows, is checked, gives four berries or more, grows one back and is cleared bare`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.setUp()
                client.to(BUSH_X + 1, BUSH_Z - 1)
                client.plantRedberry()
                val base = client.xp() * 10
                assertEquals(0, client.count(REDBERRY_SEED))

                // Five 20-minute growths (any 20 farming ticks hold five), then Check-health for 64 XP and the planting's 11.5.
                client.command("farmtick 20")
                client.awaitPatch(250)
                client.bushOp(1)
                client.seen("[farming.checked]")
                client.awaitPatch(14)
                client.awaitXp((base + 115 + 640) / 10)

                // Picked by hand, 4.5 XP a berry, until none are left: four at least. A pack with room for them all.
                client.command("empty")
                client.awaitCount(RAKE, 0)
                client.give(SPADE)
                client.bushOp(1)
                client.seen("[farming.berries_gone]", 120_000)
                client.awaitPatch(10)
                val berries = client.count(REDBERRIES)
                assertTrue(berries >= 4, "at least four berries: $berries")
                client.awaitXp((base + 115 + 640 + berries * 45) / 10)
                assertEquals(berries, client.messages().count { it.text == "[farming.berry_picked produce=redberries]" })

                // A berry back at the next 20-minute tick, picked.
                client.command("farmtick 4")
                client.awaitPatch(11)
                client.bushOp(1)
                client.await("the bush bare again") { if (client.messages().count { it.text == "[farming.berries_gone]" } == 2) Unit else null }
                client.awaitPatch(10)
                val all = client.count(REDBERRIES)
                assertTrue(all > berries, "the berry grown back: $all")
                client.awaitXp((base + 115 + 640 + all * 45) / 10)

                // Bare, op1 is Clear: dug up with the spade, for no XP.
                val before = client.xp()
                client.bushOp(1)
                client.seen("[farming.bush_cleared]")
                client.awaitPatch(3)
                assertEquals(before, client.xp())
            }
        }
    }

    @Test
    fun `a diseased bush is pruned with secateurs for leaves, and a bush with berries is dug up with Clear`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.setUp()
                client.to(BUSH_X + 1, BUSH_Z - 1)
                client.plantRedberry()
                client.command("farmtick 4")
                client.awaitPatch(6)

                // Ill: a plant cure isn't the way, secateurs are.
                client.command("farmdisease champions_guild_bush")
                client.awaitPatch(70)
                client.bushOp(1)
                client.seen("[farming.need_secateurs]")
                client.give(SECATEURS)
                client.bushOp(1)
                client.seen("[farming.pruned]", 60_000)
                client.awaitPatch(6)
                client.awaitCount(LEAVES, 1)

                // Grown and checked, then op5, Clear, with all four berries still on it.
                client.command("farmtick 20")
                client.awaitPatch(250)
                client.bushOp(1)
                client.seen("[farming.checked]")
                client.awaitPatch(14)
                client.bushOp(5)
                client.seen("[farming.bush_cleared]")
                client.awaitPatch(3)
                assertEquals(0, client.count(REDBERRIES))
            }
        }
    }

    private companion object {
        const val OBJS = 25_000
        const val BACKPACK = 93
        const val FARMING = 19
        const val TRANSMIT_A = 4771
        const val HOPS = 8175
        const val HOPS_X = 3227
        const val HOPS_Z = 3313
        const val BUSH = 7577
        const val BUSH_X = 3181
        const val BUSH_Z = 3357
        const val RAKE = 5341
        const val SPADE = 952
        const val DIBBER = 5343
        const val WEEDS = 6055
        const val CAN = 5340
        const val CAN_7 = 5339
        const val HAMMERSTONE_SEED = 5307
        const val HAMMERSTONE = 5994
        const val JUTE_SEED = 5306
        const val REDBERRY_SEED = 5101
        const val REDBERRIES = 1951
        const val SECATEURS = 5329
        const val LEAVES = 6020
    }
}
