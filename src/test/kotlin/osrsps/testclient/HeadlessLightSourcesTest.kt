package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.Cooking
import osrsps.game.skill.LightSources
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Misthalin piece 7 step 2a through the headless client: a tinderbox lights a light source at its Firemaking level,
 * Extinguish puts it out, a candle goes into an empty candle lantern, and an unlit torch lights on a range. The rl-client
 * script test-runner/examples/light-sources.json does the same in the real client.
 */
class HeadlessLightSourcesTest {
    private fun lightable(unlit: Int, lit: Int, name: String, extinguishOp: Int): List<ObjType> {
        val ops = MutableList<String?>(5) { null }.apply { set(extinguishOp - 1, LightSources.EXTINGUISH) }
        return listOf(ObjType(unlit, name, tradeable = true), ObjType(lit, name, iops = ops))
    }

    private val objs = listOf(ObjType(LightSources.TINDERBOX, "Tinderbox", tradeable = true), ObjType(LightSources.CANDLE_LANTERN_EMPTY, "Empty candle lantern", tradeable = true)) +
        // As the 242 cache has them: Extinguish is iop1 on candles, iop2 on lanterns, iop4 on torches.
        lightable(LightSources.CANDLE, LightSources.LIT_CANDLE, "Candle", 1) +
        lightable(LightSources.CANDLE_LANTERN, LightSources.CANDLE_LANTERN_LIT, "Candle lantern", 2) +
        lightable(LightSources.OIL_LANTERN, LightSources.OIL_LANTERN_LIT, "Oil lantern", 2) +
        lightable(LightSources.UNLIT_TORCH, LightSources.LIT_TORCH, "Unlit torch", 4)

    private val range = ZoneLoc(Cooking.RANGE, LocShape.CENTREPIECE_STRAIGHT, 0, 3212, 3215, 0)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3212 shr 6, 3215 shr 6) },
            random = GameRandom(1), objTypes = ObjTypes.of(*objs.toTypedArray()),
            locTypes = LocTypes(mapOf(Cooking.RANGE to LocType(Cooking.RANGE, "Range", ops = listOf("Cook", null, null, null, null), collision = LocCollisionType(width = 1, length = 1)))),
            staticLocs = StaticLocs().apply { add(range) }, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.held(id: Int) = inventory(BACKPACK).count { it?.id == id }

    private fun HeadlessClient.awaitHeld(id: Int) = await("item $id held") { if (held(id) == 1) Unit else null }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.use(id: Int, target: Int) =
        ifButtonT(InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(id), id, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(target), target)

    @Test
    fun `a tinderbox lights a candle, and Extinguish puts it out`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item ${LightSources.TINDERBOX}")
                client.command("item ${LightSources.CANDLE}")
                client.use(LightSources.TINDERBOX, LightSources.CANDLE)
                client.awaitHeld(LightSources.LIT_CANDLE)
                client.seen("[light_source.lit item=candle]")
                assertEquals(0, client.held(LightSources.CANDLE))

                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 2, client.slotOf(LightSources.LIT_CANDLE), LightSources.LIT_CANDLE)
                client.awaitHeld(LightSources.CANDLE)
                client.seen("[light_source.extinguished item=candle]")
                assertEquals(0, client.held(LightSources.LIT_CANDLE))
            }
        }
    }

    @Test
    fun `an oil lantern needs 26 Firemaking, the other way round too`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item ${LightSources.TINDERBOX}")
                client.command("item ${LightSources.OIL_LANTERN}")
                client.use(LightSources.OIL_LANTERN, LightSources.TINDERBOX)
                client.seen("[light_source.level level=26 item=oil lantern]")
                assertEquals(1, client.held(LightSources.OIL_LANTERN))

                client.command("setlevel firemaking 26")
                client.await("Firemaking 26") { client.stat(FIREMAKING)?.takeIf { it.level == 26 } }
                client.use(LightSources.OIL_LANTERN, LightSources.TINDERBOX)
                client.awaitHeld(LightSources.OIL_LANTERN_LIT)
            }
        }
    }

    @Test
    fun `a candle goes into an empty candle lantern, but a lit one needs 4 Firemaking`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item ${LightSources.CANDLE_LANTERN_EMPTY}")
                client.command("item ${LightSources.LIT_CANDLE}")
                client.use(LightSources.LIT_CANDLE, LightSources.CANDLE_LANTERN_EMPTY)
                client.seen("[light_source.level level=4 item=candle lantern]")
                assertEquals(1, client.held(LightSources.LIT_CANDLE))

                client.command("item ${LightSources.CANDLE}")
                client.use(LightSources.CANDLE_LANTERN_EMPTY, LightSources.CANDLE)
                client.awaitHeld(LightSources.CANDLE_LANTERN)
                client.seen("[light_source.lantern_filled item=candle lantern]")
                assertEquals(0, client.held(LightSources.CANDLE))
                assertEquals(0, client.held(LightSources.CANDLE_LANTERN_EMPTY))
            }
        }
    }

    @Test
    fun `an unlit torch lights on a range`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele 3212 3213 0")
                client.await("the teleport") { client.position?.takeIf { it == Coord(3212, 3213, 0) } }
                client.command("item ${LightSources.UNLIT_TORCH}")
                val slot = client.slotOf(LightSources.UNLIT_TORCH)
                client.locTarget(range.id, range.x, range.z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slot, LightSources.UNLIT_TORCH)
                client.awaitHeld(LightSources.LIT_TORCH)
                client.seen("[light_source.lit item=unlit torch]")
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val FIREMAKING = 11
    }
}
