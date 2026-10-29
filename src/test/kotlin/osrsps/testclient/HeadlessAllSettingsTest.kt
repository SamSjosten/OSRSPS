package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.AllSettings
import osrsps.game.AllSettings.Companion.CATEGORIES_CLICKZONE
import osrsps.game.AllSettings.Companion.CLOSE
import osrsps.game.AllSettings.Companion.DECLUTTER_TOGGLE
import osrsps.game.AllSettings.Companion.INTERFACE
import osrsps.game.AllSettings.Companion.SEARCH
import osrsps.game.AllSettings.Companion.SETTINGS_CLICKZONE
import osrsps.game.AllSettings.Companion.TOOLTIP_TOGGLE
import osrsps.game.AllSettings.Companion.VARP_SETTINGS_TRACKING
import osrsps.game.SettingDependency
import osrsps.game.SettingRow
import osrsps.game.SettingVar
import osrsps.game.SettingsCatalog
import osrsps.game.SettingsCategory
import osrsps.game.SettingsSide
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.World.Companion.LOGOUT_BUTTON
import osrsps.game.World.Companion.LOGOUT_TAB
import osrsps.map.CollisionMap

/**
 * The All Settings window and its toggles, clicked as the 241 client sends them: a button on the clickzone child the
 * client drew for the row. The catalog is a small one shaped like the cache's, with the real ids of the toggles it
 * uses. The rl-client script test-runner/examples/settings-toggles.json clicks the real window.
 */
class HeadlessAllSettingsTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            varTypes = VarTypes(6_000, SettingsSide.VARBITS + AllSettings.VARBITS + TEST_VARBITS), settings = CATALOG,
            cycleMillis = millis,
        )
    }

    @Test
    fun `the side panel's button opens the window with its rows and tabs clickable`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                assertFalse(INTERFACE in client.openInterfaces())
                client.ifButton(SettingsSide.INTERFACE, SettingsSide.SETTINGS_OPEN, 1)
                client.await("the window") { Unit.takeIf { INTERFACE in client.openInterfaces() } }
                client.await("the clickzone's events") { Unit.takeIf { client.opEnabled(INTERFACE, SETTINGS_CLICKZONE, CATALOG.slotCount - 1, 1) } }
                assertTrue(client.opEnabled(INTERFACE, SETTINGS_CLICKZONE, 0, 1))
                assertTrue(client.opEnabled(INTERFACE, CATEGORIES_CLICKZONE, 0, 1))
                assertTrue(client.opEnabled(INTERFACE, CATEGORIES_CLICKZONE, CATALOG.categories.size - 1, 1))
            }
        }
    }

    @Test
    fun `a toggle flips the var the client flipped, guards included, and holds past a relog`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                open(client)
                // Category 0: roofs, a header, data orbs, the wiki button.
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 0)
                client.awaitBits(SETTINGS_VARP_2, 31, 1, 1)
                client.awaitBits(VARP_SETTINGS_TRACKING, 8, 10, ROOFS)
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 1)
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 2)
                client.awaitBits(CHAT_FILTER_ASSIST, 3, 1, 1)
                // settings_wiki_lookup does nothing while the orbs are off.
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 3)
                client.awaitTicks(2)
                assertEquals(0, (client.varp(MOBILE_OPTIONS_TRANSMIT) ushr 6) and 1)
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 2)
                client.awaitBits(CHAT_FILTER_ASSIST, 3, 1, 0)
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 3)
                client.awaitBits(MOBILE_OPTIONS_TRANSMIT, 6, 1, 1)
                client.ifButton(LOGOUT_TAB, LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.loginAgain("bob").use { client ->
                client.awaitBits(SETTINGS_VARP_2, 31, 1, 1)
                client.awaitBits(MOBILE_OPTIONS_TRANSMIT, 6, 1, 1)
                assertEquals(0, (client.varp(CHAT_FILTER_ASSIST) ushr 3) and 1)
            }
        }
    }

    @Test
    fun `tabs and search number the rows as the client draws them`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                open(client)
                // Tab slots follow enum 422 without Leagues: 0 Activities, 1 Popout (blocked off the enhanced client), 2 Chat.
                client.ifButton(INTERFACE, CATEGORIES_CLICKZONE, 1, sub = 2)
                client.awaitBits(VARP_SETTINGS_TRACKING, 4, 4, CHAT)
                client.ifButton(INTERFACE, CATEGORIES_CLICKZONE, 1, sub = 1)
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 2)
                client.await("the profanity filter off") { Unit.takeIf { client.varp(VARP_CHATFILTER_DISABLED) == 1 } }
                assertEquals(CHAT, (client.varp(VARP_SETTINGS_TRACKING) ushr 4) and 15)

                // Re-failure notifications need failure notifications on; turning those off turns both off.
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 1)
                client.awaitTicks(2)
                assertEquals(0, (client.varp(MOBILE_OPTIONS_TRANSMIT) ushr 19) and 1)
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 0)
                client.awaitBits(MOBILE_OPTIONS_TRANSMIT, 16, 1, 1)
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 1)
                client.awaitBits(MOBILE_OPTIONS_TRANSMIT, 19, 1, 1)
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 0)
                client.awaitBits(MOBILE_OPTIONS_TRANSMIT, 16, 4, 0)

                // Searching numbers every row of every category that isn't blocked: Activities 0-3, Chat 4-6, Leagues 7.
                client.ifButton(INTERFACE, SEARCH, 1)
                client.awaitBits(VARP_SETTINGS_TRACKING, 0, 1, 1)
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 6)
                client.await("the profanity filter back on") { Unit.takeIf { client.varp(VARP_CHATFILTER_DISABLED) == 0 } }
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 0)
                client.awaitBits(SETTINGS_VARP_2, 31, 1, 1)
                // The Leagues row is only for league worlds.
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 7)
                client.ifButton(INTERFACE, CATEGORIES_CLICKZONE, 1, sub = 0)
                client.awaitBits(VARP_SETTINGS_TRACKING, 0, 18, 0)
                assertEquals(0, client.varp(LEAGUE_VARP))
            }
        }
    }

    @Test
    fun `the info and locked buttons flip, and close shuts the window`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                open(client)
                client.ifButton(INTERFACE, TOOLTIP_TOGGLE, 1)
                client.awaitBits(SETTINGS_VARP_1, 0, 1, 1)
                client.ifButton(INTERFACE, DECLUTTER_TOGGLE, 1)
                client.awaitBits(OPTIONS_VARP, 27, 1, 1)
                client.ifButton(INTERFACE, CLOSE, 1)
                client.await("the window closed") { Unit.takeIf { INTERFACE !in client.openInterfaces() } }
                // A click on a closed window changes nothing.
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 0)
                client.awaitTicks(2)
                assertEquals(0, (client.varp(SETTINGS_VARP_2) ushr 31) and 1)
                // Esc is the close button's op 2.
                open(client)
                client.ifButton(INTERFACE, CLOSE, 2)
                client.await("the window closed by Esc") { Unit.takeIf { INTERFACE !in client.openInterfaces() } }
            }
        }
    }

    private fun open(client: HeadlessClient) {
        client.await("the login rebuild") { client.position }
        client.ifButton(SettingsSide.INTERFACE, SettingsSide.SETTINGS_OPEN, 1)
        client.await("the window") { Unit.takeIf { INTERFACE in client.openInterfaces() && client.opEnabled(INTERFACE, SETTINGS_CLICKZONE, 0, 1) } }
    }

    /** Waits until [width] bits of varp [varp], from bit [start], hold [value]. */
    private fun HeadlessClient.awaitBits(varp: Int, start: Int, width: Int, value: Int) =
        await("varp $varp bits $start+$width = $value") { Unit.takeIf { (varp(varp) ushr start) and ((1 shl width) - 1) == value } }

    /** Logs in once the server has let the last login of [name] go: until then a login is refused as still on. */
    private fun TestServer.loginAgain(name: String): HeadlessClient {
        repeat(50) {
            runCatching { login(name) }.onSuccess { return it }
            Thread.sleep(100)
        }
        return login(name)
    }

    private companion object {
        const val SETTINGS_VARP_1 = 2856
        const val SETTINGS_VARP_2 = 3074
        const val CHAT_FILTER_ASSIST = 1055
        const val MOBILE_OPTIONS_TRANSMIT = 1795
        const val OPTIONS_VARP = 3552
        const val VARP_CHATFILTER_DISABLED = 1074
        const val LEAGUE_VARP = 4000

        const val ROOFS = 7
        const val CHAT = 2

        /** The 241 cache's option_hide_rooftops and wiki_icon_disabled, and a made-up one for the league row. */
        val TEST_VARBITS = mapOf(
            12378 to VarbitType(SETTINGS_VARP_2, 31, 31),
            10113 to VarbitType(MOBILE_OPTIONS_TRANSMIT, 6, 6),
            90000 to VarbitType(LEAGUE_VARP, 0, 0),
        )

        fun row(struct: Int, id: Int, kind: Int = SettingRow.KIND_TOGGLE, vararg params: Pair<Int, Int>, requires: List<SettingDependency> = emptyList()) =
            SettingRow(struct, mapOf(SettingRow.PARAM_ID to id, SettingRow.PARAM_KIND to kind) + params, requires)

        val ROWS = listOf(
            row(1, ROOFS), row(2, 0, kind = 5), row(3, 8), row(4, 9),
            row(5, 100),
            row(6, 161), row(7, 162, requires = listOf(SettingDependency(6, 1))), row(8, 37),
            row(9, 300, SettingRow.KIND_TOGGLE, AllSettings.PARAM_LEAGUE_ONLY to 1),
        ).associateBy { it.struct }

        /** Shaped like enum 422: Activities, Popout (enhanced client only), Chat, Leagues. */
        val CATALOG = SettingsCatalog(
            listOf(
                SettingsCategory(3620, 0, emptyMap(), listOf(1, 2, 3, 4).map(ROWS::getValue)),
                SettingsCategory(4494, 8, mapOf(AllSettings.PARAM_ENHANCED_ONLY to 1), listOf(ROWS.getValue(5))),
                SettingsCategory(913, CHAT, emptyMap(), listOf(6, 7, 8).map(ROWS::getValue)),
                SettingsCategory(AllSettings.LEAGUES_CATEGORY_STRUCT, 9, emptyMap(), listOf(ROWS.getValue(9))),
            ),
            ROWS,
            mapOf(
                ROOFS to SettingVar.Varbit(12378),
                8 to SettingVar.Varbit(AllSettings.VARBIT_ORBS_DISABLED),
                9 to SettingVar.Varbit(10113),
                100 to SettingVar.Varbit(10113),
                161 to SettingVar.Varbit(AllSettings.VARBIT_CA_FAILURE),
                162 to SettingVar.Conditional(AllSettings.VARBIT_CA_FAILURE, 1, SettingVar.Varbit(AllSettings.VARBIT_CA_REFAILURE)),
                37 to SettingVar.Varp(VARP_CHATFILTER_DISABLED),
                300 to SettingVar.Varbit(90000),
            ),
            emptyMap(),
        )
    }
}
