package osrsps.testclient

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.AllSettings
import osrsps.game.AllSettings.Companion.CATEGORIES_CLICKZONE
import osrsps.game.AllSettings.Companion.CLOSE
import osrsps.game.AllSettings.Companion.DECLUTTER_TOGGLE
import osrsps.game.AllSettings.Companion.DROPDOWN_BUTTONS
import osrsps.game.AllSettings.Companion.DROPDOWN_CLOSE
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
import osrsps.game.GameFrame
import osrsps.game.SettingsSide
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.World.Companion.LOGOUT_BUTTON
import osrsps.game.World.Companion.LOGOUT_TAB
import osrsps.game.script.ScriptContext
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
            server.awaitNobodyOn()
            server.login("bob").use { client ->
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
    fun `a new account gets the defaults at its first login, with the settings sent before the interfaces`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("hank").use { client ->
                client.await("tut2_default_settings") { client.clientScripts().firstOrNull { it.id == AllSettings.TUT2_DEFAULT_SETTINGS_SCRIPT } }
                // Combat on F1 and prayer on F5, the volumes script9255 unmutes to, the teleport warnings shown.
                client.awaitBits(SIDESTONE_KEYBINDINGS_1, 0, 5, 1)
                client.awaitBits(SIDESTONE_KEYBINDINGS_1, 25, 5, 5)
                client.awaitBits(SIDESTONE_KEYBINDINGS_1, 15, 5, 13)
                assertEquals(100, client.varp(SettingsSide.VARP_MASTER_VOLUME))
                assertEquals(20, client.varp(SettingsSide.VARP_MUSIC_VOLUME))
                assertEquals(3, (client.varp(RAIDS_PERM_TRANSMIT) ushr 10) and 3)
                // The resizable frame follows, as from the layout dropdown.
                client.await("the resizable frame") { Unit.takeIf { GameFrame.RESIZABLE_CLASSIC.interfaceId in client.openInterfaces() } }
                val prots = client.packets().map { it.prot.name }
                assertTrue(prots.indexOfFirst { it.startsWith("VARP_") } in 0 until prots.indexOf("IF_OPENTOP"), "varps before IF_OPENTOP: $prots")

                client.ifButton(LOGOUT_TAB, LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.awaitNobodyOn()
            // The next login isn't a new account's: no defaults again, and what was saved stays.
            server.login("hank").use { client ->
                client.awaitBits(SIDESTONE_KEYBINDINGS_1, 0, 5, 1)
                client.awaitTicks(3)
                assertEquals(0, client.clientScripts().count { it.id == AllSettings.TUT2_DEFAULT_SETTINGS_SCRIPT })
            }
        }
    }

    @Test
    fun `the buttons switch their section's warnings, reset its colours, and restore the keys and volumes`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ivy").use { client ->
                open(client)
                // Warnings, the fifth tab drawn: off, then on again, each button made the selected setting.
                client.ifButton(INTERFACE, CATEGORIES_CLICKZONE, 1, sub = 4)
                client.awaitBits(VARP_SETTINGS_TRACKING, 4, 4, WARNINGS)
                client.awaitBits(RAIDS_PERM_TRANSMIT, 10, 2, 3)
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 4)
                client.awaitBits(RAIDS_PERM_TRANSMIT, 10, 2, 0)
                client.awaitBits(VARP_SETTINGS_TRACKING, 8, 10, AllSettings.DISABLE_TELEPORT_WARNINGS)
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 3)
                client.awaitBits(RAIDS_PERM_TRANSMIT, 10, 2, 3)

                // A colour picked, then reset by its section's button.
                client.ifButton(INTERFACE, CATEGORIES_CLICKZONE, 1, sub = 5)
                client.awaitBits(VARP_SETTINGS_TRACKING, 4, 4, AUDIO)
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 1)
                client.await("the colour picker") { Unit.takeIf { AllSettings.COLOUR_PALLET in client.openInterfaces() } }
                client.resumeCount(0x123456)
                client.await("the colour saved") { Unit.takeIf { client.varp(CHAT_COLOUR_TRADE_OPAQUE) == 0x123457 } }
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 2)
                client.await("the colour reset") { Unit.takeIf { client.varp(CHAT_COLOUR_TRADE_OPAQUE) == 0 } }

                // Master muted from the side panel and combat's key taken off, then both restored.
                client.ifButton(SettingsSide.INTERFACE, SettingsSide.MASTER_ICON, 1)
                client.await("master muted") { Unit.takeIf { client.varp(SettingsSide.VARP_MASTER_VOLUME) == 0 } }
                client.ifButton(INTERFACE, CATEGORIES_CLICKZONE, 1, sub = 3)
                client.awaitBits(VARP_SETTINGS_TRACKING, 4, 4, CONTROLS)
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 1)
                client.ifButton(INTERFACE, DROPDOWN_BUTTONS, 1, sub = 2)
                client.awaitBits(SIDESTONE_KEYBINDINGS_1, 0, 5, 0)
                client.ifButton(INTERFACE, CATEGORIES_CLICKZONE, 1, sub = 5)
                client.awaitBits(VARP_SETTINGS_TRACKING, 4, 4, AUDIO)
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 4)
                client.await("master back") { Unit.takeIf { client.varp(SettingsSide.VARP_MASTER_VOLUME) == 100 } }
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 5)
                client.awaitBits(SIDESTONE_KEYBINDINGS_1, 0, 5, 1)
                // Playlist 1's first and last tracks wiped, playlist 2's first left alone.
                client.command("::varp $PLAYLIST_1_FIRST 7")
                client.command("::varp ${PLAYLIST_1_FIRST + AllSettings.PLAYLIST_VARPS - 1} 9")
                client.command("::varp ${PLAYLIST_1_FIRST + AllSettings.PLAYLIST_VARPS} 5")
                client.await("the playlists filled") { Unit.takeIf { client.varp(PLAYLIST_1_FIRST + AllSettings.PLAYLIST_VARPS) == 5 } }
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 6)
                client.awaitBits(VARP_SETTINGS_TRACKING, 8, 10, 490)
                client.await("playlist 1 wiped") {
                    Unit.takeIf { client.varp(PLAYLIST_1_FIRST) == 0 && client.varp(PLAYLIST_1_FIRST + AllSettings.PLAYLIST_VARPS - 1) == 0 }
                }
                assertEquals(5, client.varp(PLAYLIST_1_FIRST + AllSettings.PLAYLIST_VARPS))
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

    @Test
    fun `a dropdown or keybind stores the option picked from its list, and holds past a relog`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                open(client)
                assertTrue(client.opEnabled(INTERFACE, DROPDOWN_BUTTONS, 2, 1))
                // Tab 3 (Leagues has none): its rows from clickzone child 0.
                client.ifButton(INTERFACE, CATEGORIES_CLICKZONE, 1, sub = 3)
                client.awaitBits(VARP_SETTINGS_TRACKING, 4, 4, CONTROLS)

                // Chatbox scrollbar side, option 1 of 2 (its third child, 3 * 1 + 2).
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 0)
                client.ifButton(INTERFACE, DROPDOWN_BUTTONS, 1, sub = 5)
                client.awaitBits(CHAT_FILTER_ASSIST, 28, 1, 1)
                client.awaitBits(VARP_SETTINGS_TRACKING, 8, 10, SCROLLBAR_SIDE)
                // The list closed with the pick: another click on the options does nothing.
                client.ifButton(INTERFACE, DROPDOWN_BUTTONS, 1, sub = 2)
                // An option's other two children have no op, and "Close dropdown" closes the list.
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 0)
                client.ifButton(INTERFACE, DROPDOWN_BUTTONS, 1, sub = 1)
                client.ifButton(INTERFACE, DROPDOWN_CLOSE, 1)
                client.ifButton(INTERFACE, DROPDOWN_BUTTONS, 1, sub = 2)
                client.awaitTicks(2)
                assertEquals(1, (client.varp(CHAT_FILTER_ASSIST) ushr 28) and 1)

                // Combat tab on F5, then prayer on F5 too: combat loses it. Option 14 is past the list's end.
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 1)
                client.ifButton(INTERFACE, DROPDOWN_BUTTONS, 1, sub = 3 * 5 + 2)
                client.awaitBits(SIDESTONE_KEYBINDINGS_1, 0, 5, 5)
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 2)
                client.ifButton(INTERFACE, DROPDOWN_BUTTONS, 1, sub = 3 * 14 + 2)
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 2)
                client.ifButton(INTERFACE, DROPDOWN_BUTTONS, 1, sub = 3 * 5 + 2)
                client.awaitBits(SIDESTONE_KEYBINDINGS_1, 25, 5, 5)
                client.awaitBits(SIDESTONE_KEYBINDINGS_1, 0, 5, 0)
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 1)
                client.ifButton(INTERFACE, DROPDOWN_BUTTONS, 1, sub = 3 * 13 + 2)
                client.awaitBits(SIDESTONE_KEYBINDINGS_1, 0, 5, 13)

                // Login/logout notifications, two varbits behind one getter.
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 5)
                client.ifButton(INTERFACE, DROPDOWN_BUTTONS, 1, sub = 3 * 2 + 2)
                client.awaitBits(CHAT_FILTER_ASSIST_2, 0, 2, 2)

                // Run invert needs roofs hidden first; its list never opens until then.
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 6)
                client.ifButton(INTERFACE, DROPDOWN_BUTTONS, 1, sub = 3 * 2 + 2)
                client.awaitTicks(2)
                assertEquals(0, (client.varp(SETTINGS_VARP_3) ushr 17) and 3)

                client.ifButton(LOGOUT_TAB, LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.awaitNobodyOn()
            server.login("erin").use { client ->
                client.awaitBits(CHAT_FILTER_ASSIST, 28, 1, 1)
                client.awaitBits(SIDESTONE_KEYBINDINGS_1, 0, 5, 13)
                client.awaitBits(SIDESTONE_KEYBINDINGS_1, 25, 5, 5)
                client.awaitBits(CHAT_FILTER_ASSIST_2, 0, 2, 2)
            }
        }
    }

    @Test
    fun `the volume sliders and the layout dropdown reach the server`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("frank").use { client ->
                open(client)
                client.ifButton(INTERFACE, CATEGORIES_CLICKZONE, 1, sub = 3)
                client.awaitBits(VARP_SETTINGS_TRACKING, 4, 4, CONTROLS)
                // settings_master_volume_pos sends (319, volume) from the slider's clickzone child; a child that isn't
                // that slider's is ignored.
                client.ifScriptTrigger(INTERFACE, SETTINGS_CLICKZONE, VOLUME_SCRIPT, 319, 2, sub = 0)
                client.ifScriptTrigger(INTERFACE, SETTINGS_CLICKZONE, VOLUME_SCRIPT, 319, 40, sub = 3)
                client.await("master volume 40") { Unit.takeIf { client.varp(SettingsSide.VARP_MASTER_VOLUME) == 40 } }
                client.ifScriptTrigger(INTERFACE, SETTINGS_CLICKZONE, VOLUME_SCRIPT, 319, 250, sub = 3)
                client.await("master volume 100") { Unit.takeIf { client.varp(SettingsSide.VARP_MASTER_VOLUME) == 100 } }

                // "Game client layout" option 1, resizable classic: the frame moves and the window with it.
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 4)
                client.ifButton(INTERFACE, DROPDOWN_BUTTONS, 1, sub = 3 * 1 + 2)
                client.await("the resizable frame") { Unit.takeIf { GameFrame.RESIZABLE_CLASSIC.interfaceId in client.openInterfaces() } }
                assertTrue(INTERFACE in client.openInterfaces())
            }
        }
    }

    @Test
    fun `a number row asks for an amount and a colour row opens the picker, both held past a relog`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("gina").use { client ->
                open(client)
                client.ifButton(INTERFACE, CATEGORIES_CLICKZONE, 1, sub = 3)
                client.awaitBits(VARP_SETTINGS_TRACKING, 4, 4, CONTROLS)

                // The loot notification value: meslayer_mode7 with the row's prompt, then the amount.
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 7)
                client.await("the amount prompt") {
                    client.clientScripts().lastOrNull { it.id == ScriptContext.MESLAYER_MODE7 }?.takeIf { it.args == listOf("[settings.loot_notification_value]") }
                }
                client.awaitBits(VARP_SETTINGS_TRACKING, 8, 10, LOOT_VALUE)
                client.resumeCount(5_000)
                client.awaitBits(OPTION_LOOTNOTIFICATION, 3, 29, 5_000)
                // Too much for the varbit's 29 bits: the most it holds.
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 7)
                client.await("the second prompt") { client.clientScripts().count { it.id == ScriptContext.MESLAYER_MODE7 }.takeIf { it == 2 } }
                client.resumeCount(Int.MAX_VALUE)
                client.awaitBits(OPTION_LOOTNOTIFICATION, 3, 29, (1 shl 29) - 1)

                // Public chat's colour: the picker over the window, told the row's colour (-1, its default).
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 8)
                client.await("the colour picker") { Unit.takeIf { AllSettings.COLOUR_PALLET in client.openInterfaces() } }
                client.await("colour_pallet run") {
                    client.clientScripts().lastOrNull { it.id == AllSettings.COLOUR_PALLET_SCRIPT }?.takeIf { it.args == listOf((INTERFACE shl 16) or AllSettings.POPUP, -1) }
                }
                client.awaitBits(VARP_SETTINGS_TRACKING, 8, 10, PUBLIC_CHAT_COLOUR)
                client.resumeCount(0x123456)
                client.await("the colour saved") { Unit.takeIf { client.varp(CHAT_COLOUR_PUBLIC_OPAQUE) == 0x123457 } }
                client.await("the picker closed") { Unit.takeIf { AllSettings.COLOUR_PALLET !in client.openInterfaces() } }
                // Cancel sends 2^31 - 1, which changes nothing; the picker is told the colour now set.
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 8)
                client.await("colour_pallet run again") {
                    client.clientScripts().lastOrNull { it.id == AllSettings.COLOUR_PALLET_SCRIPT }?.takeIf { it.args.last() == 0x123456 }
                }
                client.resumeCount(Int.MAX_VALUE)
                client.await("the picker closed again") { Unit.takeIf { AllSettings.COLOUR_PALLET !in client.openInterfaces() } }
                assertEquals(0x123457, client.varp(CHAT_COLOUR_PUBLIC_OPAQUE))
                // Walking drops the wait for a colour and closes the picker; a Save after that changes nothing.
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 8)
                client.await("the picker for the walk") { Unit.takeIf { AllSettings.COLOUR_PALLET in client.openInterfaces() } }
                client.walk(3223, 3218)
                client.await("the picker closed by the walk") { Unit.takeIf { AllSettings.COLOUR_PALLET !in client.openInterfaces() } }
                client.resumeCount(0x000001)
                client.awaitTicks(2)
                assertEquals(0x123457, client.varp(CHAT_COLOUR_PUBLIC_OPAQUE))
                // A second input drops the first: a number prompt closes when a colour row opens the picker, and the
                // picker closes when a number row asks for an amount.
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 7)
                client.await("the prompt before the picker") { client.clientScripts().count { it.id == ScriptContext.MESLAYER_MODE7 }.takeIf { it == 3 } }
                val closes = client.clientScripts().count { it.id == ScriptContext.MESLAYER_CLOSE }
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 8)
                client.await("the prompt closed by the picker") { client.clientScripts().count { it.id == ScriptContext.MESLAYER_CLOSE }.takeIf { it == closes + 1 } }
                client.await("the picker after the prompt") { Unit.takeIf { AllSettings.COLOUR_PALLET in client.openInterfaces() } }
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 7)
                client.await("the picker closed by the prompt") { Unit.takeIf { AllSettings.COLOUR_PALLET !in client.openInterfaces() } }
                client.await("the prompt after the picker") { client.clientScripts().count { it.id == ScriptContext.MESLAYER_MODE7 }.takeIf { it == 4 } }
                client.resumeCount(7)
                client.awaitBits(OPTION_LOOTNOTIFICATION, 3, 29, 7)
                assertEquals(0x123457, client.varp(CHAT_COLOUR_PUBLIC_OPAQUE))
                // Closing the window drops a number prompt it asked for: the prompt closes, and an amount after that
                // changes nothing.
                client.ifButton(INTERFACE, SETTINGS_CLICKZONE, 1, sub = 7)
                client.await("the fifth prompt") { client.clientScripts().count { it.id == ScriptContext.MESLAYER_MODE7 }.takeIf { it == 5 } }
                client.ifButton(INTERFACE, CLOSE, 1)
                client.await("the prompt closed with the window") {
                    client.clientScripts().lastOrNull { it.id == ScriptContext.MESLAYER_CLOSE }?.takeIf { it.args == listOf(ScriptContext.MESLAYER_COUNT) }
                }
                client.resumeCount(1)
                client.awaitTicks(2)
                assertEquals(7, (client.varp(OPTION_LOOTNOTIFICATION) ushr 3) and ((1 shl 29) - 1))

                client.ifButton(LOGOUT_TAB, LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.awaitNobodyOn()
            server.login("gina").use { client ->
                client.awaitBits(OPTION_LOOTNOTIFICATION, 3, 29, 7)
                client.await("the colour") { Unit.takeIf { client.varp(CHAT_COLOUR_PUBLIC_OPAQUE) == 0x123457 } }
            }
        }
    }

    @Test
    fun `the chat filters are sent back, saved and sent again at login`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("hal").use { client ->
                client.await("the login rebuild") { client.position }
                // A new account's filters are all on.
                assertEquals(Triple(0, 0, 0), client.await("the login's filters") { client.chatFilters() })
                client.setChatFilters(2, 1, 3)
                client.await("the filters echoed") { client.chatFilters()?.takeIf { it == Triple(2, 1, 3) } }
                client.ifButton(LOGOUT_TAB, LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.awaitNobodyOn()
            server.login("hal").use { client ->
                client.await("the saved filters") { client.chatFilters()?.takeIf { it == Triple(2, 1, 3) } }
            }
        }
    }

    /** The last public and trade filters (CHAT_FILTER_SETTINGS, each p1Alt1) and private one the server sent. */
    private fun HeadlessClient.chatFilters(): Triple<Int, Int, Int>? {
        val packets = packets()
        val both = packets.lastOrNull { it.prot.name == "CHAT_FILTER_SETTINGS" } ?: return null
        val private = packets.lastOrNull { it.prot.name == "CHAT_FILTER_SETTINGS_PRIVATECHAT" } ?: return null
        fun alt1(byte: Byte) = (byte.toInt() - 128) and 0xff
        return Triple(alt1(both.payload[0]), private.payload[0].toInt() and 0xff, alt1(both.payload[1]))
    }

    private fun open(client: HeadlessClient) {
        client.await("the login rebuild") { client.position }
        client.ifButton(SettingsSide.INTERFACE, SettingsSide.SETTINGS_OPEN, 1)
        // The dropdown list's events are the last the window sets, so once they're in, every row and tab is clickable.
        client.await("the window") {
            Unit.takeIf { INTERFACE in client.openInterfaces() && client.opEnabled(INTERFACE, DROPDOWN_BUTTONS, CATALOG.maxOptions * 3 - 1, 1) }
        }
    }

    /** Waits until [width] bits of varp [varp], from bit [start], hold [value]. */
    private fun HeadlessClient.awaitBits(varp: Int, start: Int, width: Int, value: Int) =
        await("varp $varp bits $start+$width = $value") { Unit.takeIf { (varp(varp) ushr start) and ((1 shl width) - 1) == value } }

    /**
     * Waits until the world has let every player go: it saves a player before it frees their slot, and counts the
     * slots at the end of that cycle. Until then a login as the same name is refused as still on.
     */
    private fun TestServer.awaitNobodyOn() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (world.playerCount != 0) {
            check(System.nanoTime() < deadline) { "Timed out waiting for the world to let the last player go" }
            Thread.sleep(10)
        }
    }

    private companion object {
        const val SETTINGS_VARP_1 = 2856
        const val SETTINGS_VARP_2 = 3074
        const val CHAT_FILTER_ASSIST = 1055
        const val MOBILE_OPTIONS_TRANSMIT = 1795
        const val OPTIONS_VARP = 3552
        const val VARP_CHATFILTER_DISABLED = 1074
        const val LEAGUE_VARP = 4000
        const val SIDESTONE_KEYBINDINGS_1 = 1224
        const val CHAT_FILTER_ASSIST_2 = 3053
        const val SETTINGS_VARP_3 = 3075
        const val OPTION_LOOTNOTIFICATION = 427
        const val CHAT_COLOUR_PUBLIC_OPAQUE = 2992
        const val LOOT_VALUE = 39
        const val PUBLIC_CHAT_COLOUR = 87
        const val TRADE_CHAT_COLOUR = 89
        const val CHAT_COLOUR_TRADE_OPAQUE = 2993
        const val RAIDS_PERM_TRANSMIT = 4001
        const val PLAYLIST_1_FIRST = AllSettings.PLAYLIST_VARPS_START
        val VOLUME_SCRIPT = AllSettings.SCRIPT_TRIGGERS.keys.single()

        const val ROOFS = 7
        const val CHAT = 2
        const val CONTROLS = 3
        const val WARNINGS = 7
        const val AUDIO = 1
        const val SCROLLBAR_SIDE = 1

        /** The 241 cache's option_hide_rooftops and wiki_icon_disabled, and a made-up one for the league row. */
        val TEST_VARBITS = mapOf(
            12378 to VarbitType(SETTINGS_VARP_2, 31, 31),
            10113 to VarbitType(MOBILE_OPTIONS_TRANSMIT, 6, 6),
            90000 to VarbitType(LEAGUE_VARP, 0, 0),
            // chatbox_scrollbarside, stone_combat_key, stone_prayer_key, runinvert_mode.
            6374 to VarbitType(CHAT_FILTER_ASSIST, 28, 28),
            4675 to VarbitType(SIDESTONE_KEYBINDINGS_1, 0, 4),
            4680 to VarbitType(SIDESTONE_KEYBINDINGS_1, 25, 29),
            13132 to VarbitType(SETTINGS_VARP_3, 17, 18),
            // bounty_teleport_warning and dareeyak_teleport_warning.
            236 to VarbitType(RAIDS_PERM_TRANSMIT, 10, 10),
            6284 to VarbitType(RAIDS_PERM_TRANSMIT, 11, 11),
        )

        /** enum 1161, the keybinds' key enum. */
        val F_KEYS = (1..13).associateWith { it }

        fun row(
            struct: Int, id: Int, kind: Int = SettingRow.KIND_TOGGLE, vararg params: Pair<Int, Int>,
            requires: List<SettingDependency> = emptyList(), options: Int = 0,
        ) = SettingRow(struct, mapOf(SettingRow.PARAM_ID to id, SettingRow.PARAM_KIND to kind) + params, requires, options = options)

        val ROWS = listOf(
            row(1, ROOFS), row(2, 0, kind = 5), row(3, 8), row(4, 9),
            row(5, 100),
            row(6, 161), row(7, 162, requires = listOf(SettingDependency(6, 1))), row(8, 37),
            row(9, 300, SettingRow.KIND_TOGGLE, AllSettings.PARAM_LEAGUE_ONLY to 1),
            // Controls: scrollbar side, two keybinds, master volume, client layout, login notifications, run invert.
            row(10, SCROLLBAR_SIDE, SettingRow.KIND_DROPDOWN, options = 2),
            row(11, 16, SettingRow.KIND_KEYBIND, options = 14), row(12, 17, SettingRow.KIND_KEYBIND, options = 14),
            row(13, 319, SettingRow.KIND_SLIDER),
            row(14, AllSettings.CLIENT_LAYOUT_DROPDOWN, SettingRow.KIND_DROPDOWN, options = 3),
            row(15, AllSettings.LOGIN_LOGOUT_DROPDOWN, SettingRow.KIND_DROPDOWN, options = 3),
            row(16, 207, SettingRow.KIND_DROPDOWN, options = 4, requires = listOf(SettingDependency(1, 1))),
            // Then a number (the loot notification value) and a colour (public chat, opaque).
            row(17, LOOT_VALUE, SettingRow.KIND_NUMBER), row(18, PUBLIC_CHAT_COLOUR, SettingRow.KIND_COLOUR),
            // Warnings: a header, two teleport warnings and the buttons that switch them all.
            row(19, 0, SettingRow.KIND_HEADER), row(20, 60), row(21, 61),
            row(22, AllSettings.ENABLE_TELEPORT_WARNINGS, SettingRow.KIND_BUTTON),
            row(23, AllSettings.DISABLE_TELEPORT_WARNINGS, SettingRow.KIND_BUTTON),
            // A colour and its reset button in one section; the volume, keybind and playlist buttons in the next.
            row(24, 0, SettingRow.KIND_HEADER), row(25, TRADE_CHAT_COLOUR, SettingRow.KIND_COLOUR), row(26, 107, SettingRow.KIND_BUTTON),
            row(27, 0, SettingRow.KIND_HEADER), row(28, AllSettings.RESET_VOLUMES, SettingRow.KIND_BUTTON),
            row(29, AllSettings.RESTORE_KEYBINDS, SettingRow.KIND_BUTTON), row(30, 490, SettingRow.KIND_BUTTON),
        ).associateBy { it.struct }

        /** Shaped like enum 422: Activities, Popout (enhanced client only), Chat, Leagues. */
        val CATALOG = SettingsCatalog(
            listOf(
                SettingsCategory(3620, 0, emptyMap(), listOf(1, 2, 3, 4).map(ROWS::getValue)),
                SettingsCategory(4494, 8, mapOf(AllSettings.PARAM_ENHANCED_ONLY to 1), listOf(ROWS.getValue(5))),
                SettingsCategory(913, CHAT, emptyMap(), listOf(6, 7, 8).map(ROWS::getValue)),
                SettingsCategory(AllSettings.LEAGUES_CATEGORY_STRUCT, 9, emptyMap(), listOf(ROWS.getValue(9))),
                SettingsCategory(2000, CONTROLS, emptyMap(), (10..18).map(ROWS::getValue)),
                SettingsCategory(2100, WARNINGS, emptyMap(), (19..23).map(ROWS::getValue)),
                SettingsCategory(2200, AUDIO, emptyMap(), (24..30).map(ROWS::getValue)),
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
                60 to SettingVar.Varbit(236),
                61 to SettingVar.Varbit(6284),
            ),
            mapOf(SCROLLBAR_SIDE to SettingVar.Varbit(6374), 207 to SettingVar.Varbit(13132)),
            mapOf(16 to SettingVar.Mapped(SettingVar.Varbit(4675), F_KEYS, 0), 17 to SettingVar.Mapped(SettingVar.Varbit(4680), F_KEYS, 0)),
            numbers = mapOf(LOOT_VALUE to SettingVar.Varbit(AllSettings.VARBIT_LOOT_NOTIFICATION_VALUE)),
            colours = mapOf(
                PUBLIC_CHAT_COLOUR to SettingVar.Offset(SettingVar.Varp(CHAT_COLOUR_PUBLIC_OPAQUE), -1),
                TRADE_CHAT_COLOUR to SettingVar.Offset(SettingVar.Varp(CHAT_COLOUR_TRADE_OPAQUE), -1),
            ),
        )
    }
}
