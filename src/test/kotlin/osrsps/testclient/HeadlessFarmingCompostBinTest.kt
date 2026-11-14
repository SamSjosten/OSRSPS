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
 * Farming step 7 through the headless client, at Falador's compost bin: 15 weeds put in, the bin closed, rotted through
 * two 40-minute ticks, opened and emptied a bucket at a time for 4.5 XP each; 15 watermelons made into supercompost,
 * turned to ultracompost with 25 volcanic ash, two buckets taken and the rest dumped; 14 tomatoes and a potato making
 * plain compost, turned to supercompost with a dose of compost potion. The bin is `farming_transmit_e`, read here as
 * bits 0 to 7 of varp 1000. The rl-client script test-runner/examples/farming-compost-bin.json does the same in the
 * real client.
 */
class HeadlessFarmingCompostBinTest {
    private val clock = AtomicLong(1_794_614_400_000L)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = Array<ObjType?>(OBJS) { ObjType(it, "Obj $it") }
        for ((id, name) in mapOf(
            WEEDS to "Weeds", BUCKET to "Bucket", COMPOST to "Compost", SUPERCOMPOST to "Supercompost",
            ULTRACOMPOST to "Ultracompost", WATERMELON to "Watermelon", ASH to "Volcanic ash", TOMATO to "Tomato",
            POTATO to "Potato", POTION_4 to "Compost potion(4)", POTION_3 to "Compost potion(3)",
        )) objs[id] = ObjType(id, name)
        World(
            crcs, CollisionMap().apply { markLoaded(BIN_X shr 6, BIN_Z shr 6) }, objTypes = ObjTypes(objs),
            varTypes = VarTypes(6_000, mapOf(TRANSMIT_E to VarbitType(1000, 0, 7)), 21_000),
            random = GameRandom(1),
            locTypes = LocTypes(mapOf(BIN to LocType(BIN, "null", List(5) { null }, LocCollisionType(width = 1, length = 1), multiloc = true))),
            staticLocs = StaticLocs().apply { add(ZoneLoc(BIN, LocShape.CENTREPIECE_STRAIGHT, 0, BIN_X, BIN_Z, 0)) },
            farmingClock = clock::get,
            farmingDiseases = false,
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.slotOf(id: Int) = inventory(BACKPACK).indexOfFirst { it?.id == id }

    private fun HeadlessClient.seen(text: String, timeoutMillis: Long = 10_000) =
        await(text, timeoutMillis) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.bin() = varp(1000) and 0xFF

    private fun HeadlessClient.awaitBin(value: Int, timeoutMillis: Long = 10_000) =
        await("the bin at $value (${bin()})", timeoutMillis) { if (bin() == value) Unit else null }

    private fun HeadlessClient.xp() = stat(FARMING)?.xp ?: 0

    private fun HeadlessClient.give(item: Int, count: Int = 1) {
        val before = count(item)
        command("item $item $count")
        await("item $item") { if (count(item) >= before + count) Unit else null }
    }

    private fun HeadlessClient.awaitCount(id: Int, count: Int, timeoutMillis: Long = 10_000) =
        await("$count of item $id (${count(id)})", timeoutMillis) { if (count(id) == count) Unit else null }

    private fun HeadlessClient.useOnBin(item: Int) = locTarget(BIN, BIN_X, BIN_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(item), item)

    private fun HeadlessClient.binOp(op: Int) = locOp(BIN, BIN_X, BIN_Z, op)

    private fun HeadlessClient.setUp() {
        await("the login rebuild") { position }
        command("tele $BIN_X ${BIN_Z - 1}")
        await("beside the bin") { position?.takeIf { it.x == BIN_X && it.z == BIN_Z - 1 } }
        command("farmclock pause")
        assertEquals(0, bin())
    }

    /** Closed, rotted through two 40-minute ticks (any 16 farming ticks hold two) and opened: [ready], then [open]. */
    private fun HeadlessClient.rotAndOpen(rotting: Int, ready: Int, open: Int) {
        binOp(1)
        seen("[farming.bin_closed]")
        awaitBin(rotting)
        binOp(1)
        seen("[farming.bin_rotting]")
        command("farmtick 16")
        awaitBin(ready)
        binOp(1)
        awaitBin(open)
    }

    @Test
    fun `fifteen weeds make fifteen buckets of compost, taken for 4 point 5 XP each`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.setUp()
                // The stack goes in one a tick until the bin is full: 15 of the 20, the rest kept.
                client.give(WEEDS, 20)
                client.useOnBin(WEEDS)
                client.awaitBin(15, 20_000)
                client.awaitCount(WEEDS, 5)
                assertEquals(15, client.messages().count { it.text == "[farming.bin_added item=weeds]" })
                client.useOnBin(WEEDS)
                client.seen("[farming.bin_full]")

                client.rotAndOpen(rotting = 31, ready = 94, open = 30)
                client.seen("[farming.bin_opened compost=compost]")

                // No bucket, no compost; then a bucket a time, 4.5 XP each, until the bin is empty.
                client.binOp(1)
                client.seen("[farming.bin_need_bucket]")
                val base = client.xp() * 10
                client.give(BUCKET, 15)
                client.binOp(1)
                client.seen("[farming.bin_emptied]", 60_000)
                client.awaitBin(0)
                client.awaitCount(COMPOST, 15)
                assertEquals(0, client.count(BUCKET))
                client.await("67.5 XP") { if (client.xp() == (base + 15 * 45) / 10) Unit else null }
            }
        }
    }

    @Test
    fun `watermelons make supercompost, volcanic ash makes it ultracompost, and Dump throws the rest away`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.setUp()
                client.give(WATERMELON, 15)
                client.useOnBin(WATERMELON)
                client.awaitBin(47, 20_000)
                client.rotAndOpen(rotting = 95, ready = 126, open = 62)
                client.seen("[farming.bin_opened compost=supercompost]")

                // 24 ash is too little; 25 turns the whole bin.
                client.give(ASH, 24)
                client.useOnBin(ASH)
                client.seen("[farming.bin_need_ash]")
                client.give(ASH)
                client.useOnBin(ASH)
                client.seen("[farming.bin_upgraded compost=ultracompost]")
                client.awaitBin(190)
                assertEquals(0, client.count(ASH))

                // Two buckets used on the bin: two ultracompost, then no bucket left.
                client.give(BUCKET, 2)
                client.useOnBin(BUCKET)
                client.seen("[farming.bin_need_bucket]", 30_000)
                client.awaitCount(ULTRACOMPOST, 2)
                client.awaitBin(188)

                client.binOp(5)
                client.seen("[farming.bin_dumped]")
                client.awaitBin(0)
            }
        }
    }

    @Test
    fun `tomatoes with a potato make plain compost, which a dose of compost potion makes supercompost`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.setUp()
                client.give(TOMATO, 14)
                client.useOnBin(TOMATO)
                client.awaitBin(142, 20_000)
                client.give(POTATO)
                client.useOnBin(POTATO)
                client.seen("[farming.bin_mixed]")
                client.awaitBin(15)
                client.rotAndOpen(rotting = 31, ready = 94, open = 30)
                client.give(POTION_4)
                client.useOnBin(POTION_4)
                client.seen("[farming.bin_upgraded compost=supercompost]")
                client.awaitBin(62)
                client.awaitCount(POTION_3, 1)
                client.binOp(5)
                client.awaitBin(0)
            }
        }
    }

    private companion object {
        const val OBJS = 25_000
        const val BACKPACK = 93
        const val FARMING = 19
        const val TRANSMIT_E = 4775
        const val BIN = 7836
        const val BIN_X = 3056
        const val BIN_Z = 3312
        const val WEEDS = 6055
        const val BUCKET = 1925
        const val COMPOST = 6032
        const val SUPERCOMPOST = 6034
        const val ULTRACOMPOST = 21483
        const val WATERMELON = 5982
        const val ASH = 21622
        const val TOMATO = 1982
        const val POTATO = 1942
        const val POTION_4 = 6470
        const val POTION_3 = 6472
    }
}
