package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.AllSettings
import osrsps.game.GameFrame
import osrsps.game.Movement
import osrsps.game.Scenarios
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.map.CollisionMap

/**
 * The two run settings in All Settings, over the wire: "Ctrl+click to invert run mode" decides whether a ctrl-click
 * walks or runs, and "Energy threshold to re-enable running" turns run back on once energy that ran out is back. The
 * rl-client script test-runner/examples/settings-run.json covers the threshold in the real client (it can't hold ctrl).
 */
class HeadlessRunSettingsTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            varTypes = VarTypes(6_000, AllSettings.VARBITS), scenarios = Scenarios.load(), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.pause() = commandReply("pause") { it.startsWith("Paused after tick") }

    /** Tiles walked in the tick after a ctrl-click six tiles north: 1 is a walk, 2 a run. */
    private fun HeadlessClient.ctrlClickStep(): Int {
        val from = position!!
        walk(from.x, from.z + 6, keyCombination = 1)
        stepTicks(1)
        return position!!.z - from.z
    }

    @Test
    fun `ctrl-click walks or runs by the invert run mode setting`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.pause()
                // Run off, then on: what a ctrl-click does in each of enum 4074's modes.
                val expected = mapOf(
                    false to listOf(Movement.RUN_INVERT_NEVER to 1, Movement.RUN_INVERT_WALK_TO_RUN to 2, Movement.RUN_INVERT_RUN_TO_WALK to 1, Movement.RUN_INVERT_ALWAYS to 2),
                    true to listOf(Movement.RUN_INVERT_NEVER to 2, Movement.RUN_INVERT_WALK_TO_RUN to 2, Movement.RUN_INVERT_RUN_TO_WALK to 1, Movement.RUN_INVERT_ALWAYS to 1),
                )
                for ((run, modes) in expected) {
                    client.commandReply("varp $RUN_VARP ${if (run) 1 else 0}") { it.startsWith("Varp $RUN_VARP") }
                    for ((mode, tiles) in modes) {
                        client.commandReply("varbit ${Movement.VARBIT_RUN_INVERT_MODE} $mode") { it.startsWith("Varbit ${Movement.VARBIT_RUN_INVERT_MODE}") }
                        assertEquals(tiles, client.ctrlClickStep(), "run ${if (run) "on" else "off"}, mode $mode")
                    }
                }
            }
        }
    }

    @Test
    fun `run that ran out comes back on at the threshold, and run turned off by hand stays off`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.pause()
                client.commandReply("scenario lumbridge-run-low") { it.startsWith("Started scenario") }
                client.stepTicks(2)
                assertEquals(0, client.varp(RUN_VARP), "run went off with no energy")
                // 15 units a tick standing; 5% is 500, so 34 ticks.
                client.stepTicks(30)
                assertEquals(0, client.varp(RUN_VARP), "not yet at 5%")
                client.stepTicks(6)
                assertEquals(1, client.varp(RUN_VARP), "back on at 5%")

                // Out again, then turned off by hand before the threshold: it stays off.
                client.commandReply("scenario lumbridge-run-low") { it.startsWith("Started scenario") }
                client.stepTicks(2)
                assertEquals(0, client.varp(RUN_VARP))
                // On and off again on the run orb within one tick, so no tick sees run on in between.
                client.ifButton(GameFrame.ORBS, GameFrame.ORBS_RUN_BUTTON, 1)
                client.ifButton(GameFrame.ORBS, GameFrame.ORBS_RUN_BUTTON, 1)
                client.stepTicks(40)
                assertEquals(0, client.varp(RUN_VARP), "only energy that ran out turns run back on")
            }
        }
    }

    private companion object {
        const val RUN_VARP = 173
    }
}
