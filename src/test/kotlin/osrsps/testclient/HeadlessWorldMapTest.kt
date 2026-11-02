package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.AllSettings
import osrsps.game.FrameSlot
import osrsps.game.GameFrame
import osrsps.game.SettingsSide
import osrsps.game.World
import osrsps.game.WorldMapScreen
import osrsps.map.CollisionMap

/**
 * The world map over the game protocol: the globe orb's op opens interface 595 in the frame's floater, the orb again
 * closes it, so does its close button (op 1, and Esc's op 2), it stays open while the player walks, and it takes the
 * floater over from All Settings. The rl-client script test-runner/examples/world-map.json makes the same clicks in the
 * real client, where the map is drawn.
 */
class HeadlessWorldMapTest {
    private fun HeadlessClient.globe() = ifButton(GameFrame.ORBS, GameFrame.ORBS_WORLDMAP, 1)

    private fun HeadlessClient.mapOpen(): Boolean = WorldMapScreen.INTERFACE in openInterfaces()

    @Test
    fun `the globe opens the world map in the floater, the globe and the close button shut it, and walking leaves it open`(@TempDir dir: Path) {
        TestServer(dir) { crcs, millis -> World(crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, cycleMillis = millis) }.use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                assertFalse(client.mapOpen(), "closed at login")

                client.globe()
                client.await("the world map") { Unit.takeIf { client.mapOpen() } }
                val fixed = GameFrame.FIXED
                assertEquals(WorldMapScreen.INTERFACE, client.subAt(fixed.interfaceId, fixed.component(FrameSlot.FLOATER)), "in toplevel:floater")

                client.globe()
                client.await("closed by the orb") { Unit.takeIf { !client.mapOpen() } }

                client.globe()
                client.await("the world map again") { Unit.takeIf { client.mapOpen() } }
                client.ifButton(WorldMapScreen.INTERFACE, WorldMapScreen.CLOSE, 1)
                client.await("closed by its button") { Unit.takeIf { !client.mapOpen() } }

                client.globe()
                client.await("the world map a third time") { Unit.takeIf { client.mapOpen() } }
                client.ifButton(WorldMapScreen.INTERFACE, WorldMapScreen.CLOSE, 2)
                client.await("closed by Esc") { Unit.takeIf { !client.mapOpen() } }

                // An overlay, not a modal: a walk leaves it where it is.
                client.globe()
                client.await("the world map once more") { Unit.takeIf { client.mapOpen() } }
                val from = client.position!!
                client.walk(from.x + 2, from.z)
                client.await("the walk") { client.position?.takeIf { it == Coord(from.x + 2, from.z) } }
                client.awaitTicks(1)
                assertTrue(client.mapOpen(), "still open after walking")

                // The floater holds one window: the globe takes it from All Settings, and the map's close leaves settings alone.
                client.ifButton(SettingsSide.INTERFACE, SettingsSide.SETTINGS_OPEN, 1)
                client.await("All Settings") { Unit.takeIf { AllSettings.INTERFACE in client.openInterfaces() } }
                assertFalse(client.mapOpen(), "settings took the floater")
                client.ifButton(WorldMapScreen.INTERFACE, WorldMapScreen.CLOSE, 1)
                client.awaitTicks(2)
                assertTrue(AllSettings.INTERFACE in client.openInterfaces(), "the map's close button doesn't shut settings")
                client.globe()
                client.await("the map over settings") { Unit.takeIf { client.mapOpen() } }
                assertFalse(AllSettings.INTERFACE in client.openInterfaces())
            }
        }
    }
}
