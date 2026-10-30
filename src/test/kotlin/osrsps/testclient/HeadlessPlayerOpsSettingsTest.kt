package osrsps.testclient

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.AllSettings
import osrsps.game.AllSettings.Companion.INTERFACE
import osrsps.game.AllSettings.Companion.SETTINGS_CLICKZONE
import osrsps.game.PlayerOps
import osrsps.game.SettingRow
import osrsps.game.SettingVar
import osrsps.game.SettingsCatalog
import osrsps.game.SettingsCategory
import osrsps.game.SettingsSide
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.World.Companion.LOGOUT_BUTTON
import osrsps.game.World.Companion.LOGOUT_TAB
import osrsps.map.CollisionMap

/**
 * The All Settings toggles "Player Trade options" and "Player Report options" over the wire: clicking one takes its
 * option off other players' menus at once, clicking it again puts it back, and a hidden option stays hidden after a
 * relog. The catalog is one category with the two rows, at their real ids.
 */
class HeadlessPlayerOpsSettingsTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            varTypes = VarTypes(6_000, SettingsSide.VARBITS + AllSettings.VARBITS), settings = CATALOG, cycleMillis = millis,
        )
    }

    private fun open(client: HeadlessClient) {
        client.await("the login rebuild") { client.position }
        client.ifButton(SettingsSide.INTERFACE, SettingsSide.SETTINGS_OPEN, 1)
        client.await("the rows clickable") { Unit.takeIf { INTERFACE in client.openInterfaces() && client.opEnabled(INTERFACE, SETTINGS_CLICKZONE, 1, 1) } }
    }

    @Test
    fun `the toggles take Trade with and Report off the menu and put them back, and a hidden one stays hidden after a relog`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("Trade with and Report at login") {
                    client.playerOps().takeIf { it[PlayerOps.TRADE] == "Trade with" && it[PlayerOps.REPORT] == "Report" }
                }
                open(client)
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 0)
                client.await("Trade with gone") { Unit.takeIf { PlayerOps.TRADE in client.playerOps() && client.playerOps()[PlayerOps.TRADE] == null } }
                assertEquals(1, (client.varp(TRADE_VARP) ushr TRADE_BIT) and 1)
                assertEquals("Report", client.playerOps()[PlayerOps.REPORT])
                assertEquals("Follow", client.playerOps()[PlayerOps.FOLLOW])

                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 1)
                client.await("Report gone") { Unit.takeIf { client.playerOps()[PlayerOps.REPORT] == null } }
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 1)
                client.await("Report back") { Unit.takeIf { client.playerOps()[PlayerOps.REPORT] == "Report" } }

                client.ifButton(LOGOUT_TAB, LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.awaitNobodyOn()
            server.login("alice").use { client ->
                client.await("the login's options") { client.playerOps().takeIf { it[PlayerOps.REPORT] == "Report" } }
                assertEquals(null, client.playerOps()[PlayerOps.TRADE], "Trade with still hidden")
            }
        }
    }

    private fun TestServer.awaitNobodyOn() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (world.playerCount != 0) {
            check(System.nanoTime() < deadline) { "Timed out waiting for the world to let the last player go" }
            Thread.sleep(10)
        }
    }

    private companion object {
        /** tradeoption_disabled is bit 29 of varp 2903 (VarbitType(2903, 29, 29)). */
        const val TRADE_VARP = 2903
        const val TRADE_BIT = 29

        fun row(struct: Int, id: Int) = SettingRow(struct, mapOf(SettingRow.PARAM_ID to id, SettingRow.PARAM_KIND to SettingRow.KIND_TOGGLE))

        val ROWS = listOf(row(1, AllSettings.PLAYER_TRADE_OPTIONS), row(2, AllSettings.PLAYER_REPORT_OPTIONS)).associateBy { it.struct }

        val CATALOG = SettingsCatalog(
            listOf(SettingsCategory(3620, 0, emptyMap(), ROWS.values.toList())),
            ROWS,
            mapOf(
                AllSettings.PLAYER_TRADE_OPTIONS to SettingVar.Varbit(PlayerOps.VARBIT_TRADE_HIDDEN),
                AllSettings.PLAYER_REPORT_OPTIONS to SettingVar.Varbit(PlayerOps.VARBIT_REPORT_HIDDEN),
            ),
            emptyMap(),
        )
    }
}
