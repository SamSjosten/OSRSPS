package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.XpRate
import osrsps.map.CollisionMap

/**
 * The server-wide XP rate through the headless client: `::xprate` multiplies the XP a bone gives and lasts across a
 * restart. The rl-client script test-runner/examples/xp-rate.json does it in the real client.
 */
class HeadlessXpRateTest {
    private val bones = ObjType(BONES, "Bones", iops = listOf("Bury", null, null, null, "Drop"), internalName = "bones")

    /** Each start gets a folder of its own (the test server writes a cache there); [rateFile] is the one they share. */
    private fun server(dir: Path, rateFile: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(BONES + 1)
        objs[BONES] = bones
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            xpRate = XpRate(rateFile), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.buryOne() {
        val slot = await("bones in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == BONES }.takeIf { it >= 0 } }
        ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, BURY_BUTTON_OP, slot, BONES)
    }

    @Test
    fun `a bone gives 4,5 XP by default and 11,25 at 2,5x, which lasts across a restart`(@TempDir dir: Path) {
        val rateFile = dir.resolve(XpRate.FILE_NAME)
        server(dir.resolve("first"), rateFile).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("xprate")
                client.seen("XP rate: 1x")
                client.command("item $BONES 2")
                client.buryOne()
                assertEquals(4, client.await("Prayer XP") { client.stat(PRAYER)?.takeIf { it.xp > 0 } }.xp)

                client.command("xprate 2.5")
                client.seen("XP rate set to 2.5x (was 1x)")
                // 45 + 112 (112.5 rounded down to a tenth) = 157 tenths.
                client.buryOne()
                assertEquals(15, client.await("15 Prayer XP") { client.stat(PRAYER)?.takeIf { it.xp > 4 } }.xp)
            }
        }
        server(dir.resolve("second"), rateFile).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("xprate")
                client.seen("XP rate: 2.5x")
                client.command("xprate restore")
                client.seen("XP rate back to 1x")
            }
        }
        server(dir.resolve("third"), rateFile).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("xprate")
                client.seen("XP rate: 1x")
            }
        }
    }

    private companion object {
        const val BONES = 526
        const val BACKPACK = 93
        const val PRAYER = 5

        /** iop1 is button op 2 (see InventoryTab.click). */
        const val BURY_BUTTON_OP = 2
    }
}
