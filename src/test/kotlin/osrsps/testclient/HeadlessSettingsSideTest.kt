package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameFrame
import osrsps.game.SettingsSide
import osrsps.game.SettingsSide.Companion.ACCEPT_AID
import osrsps.game.SettingsSide.Companion.AREASOUNDS_SLIDER_BOBBLE
import osrsps.game.SettingsSide.Companion.ATTACK_PRIORITY_NPC_BUTTONS
import osrsps.game.SettingsSide.Companion.ATTACK_PRIORITY_PLAYER_BUTTONS
import osrsps.game.SettingsSide.Companion.AUDIO_TAB
import osrsps.game.SettingsSide.Companion.BOND_OPTIONS
import osrsps.game.SettingsSide.Companion.CONTROLS_TAB
import osrsps.game.SettingsSide.Companion.DISPLAY_TAB
import osrsps.game.SettingsSide.Companion.HOUSE_OPTIONS
import osrsps.game.SettingsSide.Companion.INTERFACE
import osrsps.game.SettingsSide.Companion.MASTER_ICON
import osrsps.game.SettingsSide.Companion.MASTER_SLIDER_BOBBLE
import osrsps.game.SettingsSide.Companion.MUSIC_ICON
import osrsps.game.SettingsSide.Companion.MUSIC_SLIDER_BOBBLE
import osrsps.game.SettingsSide.Companion.RUN_MODE
import osrsps.game.SettingsSide.Companion.SKULL_PREVENTION
import osrsps.game.SettingsSide.Companion.SOUND_ICON
import osrsps.game.SettingsSide.Companion.SOUND_SLIDER_BOBBLE
import osrsps.game.SettingsSide.Companion.VARP_AREASOUND_VOLUME
import osrsps.game.SettingsSide.Companion.VARP_ATTACK_PRIORITY_NPC
import osrsps.game.SettingsSide.Companion.VARP_ATTACK_PRIORITY_PLAYER
import osrsps.game.SettingsSide.Companion.VARP_MASTER_VOLUME
import osrsps.game.SettingsSide.Companion.VARP_MUSIC_VOLUME
import osrsps.game.SettingsSide.Companion.VARP_SOUND_VOLUME
import osrsps.game.SettingsSide.Companion.ZOOM_TOGGLE
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.World.Companion.LOGOUT_BUTTON
import osrsps.game.World.Companion.LOGOUT_TAB
import osrsps.map.CollisionMap

/**
 * The settings side panel's controls, sent as the 241 client sends them: each sets the varp the client already set
 * for itself, and every one is still set after a logout. The rl-client script test-runner/examples/settings-side.json
 * clicks the same controls in the real client.
 */
class HeadlessSettingsSideTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            varTypes = VarTypes(6_000, SettingsSide.VARBITS), cycleMillis = millis,
        )
    }

    @Test
    fun `the panel's tabs and toggles set what the client set`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.ifButton(INTERFACE, AUDIO_TAB, 1)
                client.awaitBits(PANEL_VARP, 10, 3, 1)
                client.ifButton(INTERFACE, DISPLAY_TAB, 1)
                client.awaitBits(PANEL_VARP, 10, 3, 2)
                client.ifButton(INTERFACE, CONTROLS_TAB, 1)
                client.awaitBits(PANEL_VARP, 10, 3, 0)

                client.ifButton(INTERFACE, ACCEPT_AID, 1)
                client.awaitBits(AID_VARP, 0, 1, 1)
                client.ifButton(INTERFACE, ACCEPT_AID, 1)
                client.awaitBits(AID_VARP, 0, 1, 0)

                client.ifButton(INTERFACE, SKULL_PREVENTION, 1)
                client.awaitBits(SETTINGS_VARP_3, 16, 1, 1)

                // Op 2 is Restore Default Zoom, the client's alone; op 1 turns scroll-wheel zoom off.
                client.ifButton(INTERFACE, ZOOM_TOGGLE, 2)
                client.ifButton(INTERFACE, ZOOM_TOGGLE, 1)
                client.awaitBits(GameFrame.STONE_ARRANGEMENT_VARP, 29, 1, 1)

                client.ifButton(INTERFACE, RUN_MODE, 1)
                client.await("run on") { client.varp(RUN_VARP).takeIf { it == 1 } }
                client.ifButton(INTERFACE, RUN_MODE, 1)
                client.await("run off") { client.varp(RUN_VARP).takeIf { it == 0 } }
            }
        }
    }

    @Test
    fun `an attack priority option is its child, from 1`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.ifButton(INTERFACE, ATTACK_PRIORITY_PLAYER_BUTTONS, 1, sub = 3)
                client.await("left click on players") { client.varp(VARP_ATTACK_PRIORITY_PLAYER).takeIf { it == 2 } }
                client.ifButton(INTERFACE, ATTACK_PRIORITY_NPC_BUTTONS, 1, sub = 4)
                client.await("hidden on NPCs") { client.varp(VARP_ATTACK_PRIORITY_NPC).takeIf { it == 3 } }

                // Child 0 is the dropdown's hidden backing, and the NPC dropdown has no fifth option.
                client.ifButton(INTERFACE, ATTACK_PRIORITY_PLAYER_BUTTONS, 1, sub = 0)
                client.ifButton(INTERFACE, ATTACK_PRIORITY_NPC_BUTTONS, 1, sub = 5)
                client.ifButton(INTERFACE, ATTACK_PRIORITY_PLAYER_BUTTONS, 1, sub = 1)
                client.await("combat level on players") { client.varp(VARP_ATTACK_PRIORITY_PLAYER).takeIf { it == 0 } }
                assertEquals(3, client.varp(VARP_ATTACK_PRIORITY_NPC))
            }
        }
    }

    @Test
    fun `a slider's script trigger sets its volume, and the icon mutes and brings it back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.ifScriptTrigger(INTERFACE, MASTER_SLIDER_BOBBLE, MASTER_TRIGGER, 37)
                client.await("master at 37") { client.varp(VARP_MASTER_VOLUME).takeIf { it == 37 } }
                client.ifScriptTrigger(INTERFACE, AREASOUNDS_SLIDER_BOBBLE, AREASOUNDS_TRIGGER, 250)
                client.await("area sounds at the top") { client.varp(VARP_AREASOUND_VOLUME).takeIf { it == 100 } }

                // A new account's sound effects are at 0, with nothing saved: unmuting gives script9255's 45.
                client.ifButton(INTERFACE, SOUND_ICON, 1)
                client.await("sounds at 45") { client.varp(VARP_SOUND_VOLUME).takeIf { it == 45 } }
                client.ifScriptTrigger(INTERFACE, SOUND_SLIDER_BOBBLE, SOUND_TRIGGER, 60)
                client.await("sounds at 60") { client.varp(VARP_SOUND_VOLUME).takeIf { it == 60 } }
                client.ifButton(INTERFACE, SOUND_ICON, 1)
                client.await("sounds muted") { client.varp(VARP_SOUND_VOLUME).takeIf { it == 0 } }
                client.awaitBits(SAVE_SOUNDS_VARP, 8, 7, 60)
                client.ifButton(INTERFACE, SOUND_ICON, 1)
                client.await("sounds back at 60") { client.varp(VARP_SOUND_VOLUME).takeIf { it == 60 } }

                client.ifButton(INTERFACE, MASTER_ICON, 1)
                client.await("master muted") { client.varp(VARP_MASTER_VOLUME).takeIf { it == 0 } }
                client.awaitBits(SAVE_SOUNDS_2_VARP, 0, 8, 37)

                // A script we don't know is dropped without harm.
                client.ifScriptTrigger(INTERFACE, MUSIC_SLIDER_BOBBLE, 12_345, 50)
                client.ifScriptTrigger(INTERFACE, MUSIC_SLIDER_BOBBLE, MUSIC_TRIGGER, 15)
                client.await("music at 15") { client.varp(VARP_MUSIC_VOLUME).takeIf { it == 15 } }
            }
        }
    }

    @Test
    fun `every setting is still set after a logout`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.ifButton(INTERFACE, DISPLAY_TAB, 1)
                client.ifButton(INTERFACE, ACCEPT_AID, 1)
                client.ifButton(INTERFACE, SKULL_PREVENTION, 1)
                client.ifButton(INTERFACE, ZOOM_TOGGLE, 1)
                client.ifButton(INTERFACE, ATTACK_PRIORITY_PLAYER_BUTTONS, 1, sub = 5)
                client.ifButton(INTERFACE, ATTACK_PRIORITY_NPC_BUTTONS, 1, sub = 2)
                client.ifScriptTrigger(INTERFACE, MUSIC_SLIDER_BOBBLE, MUSIC_TRIGGER, 80)
                client.ifButton(INTERFACE, MUSIC_ICON, 1)
                client.await("music muted") { client.varp(VARP_MUSIC_VOLUME).takeIf { it == 0 && client.varp(SAVE_SOUNDS_VARP) == 80 } }
                client.ifButton(LOGOUT_TAB, LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.loginAgain("dave").use { client ->
                client.awaitBits(PANEL_VARP, 10, 3, 2)
                client.awaitBits(AID_VARP, 0, 1, 1)
                client.awaitBits(SETTINGS_VARP_3, 16, 1, 1)
                client.awaitBits(GameFrame.STONE_ARRANGEMENT_VARP, 29, 1, 1)
                client.await("attack priorities") {
                    Unit.takeIf { client.varp(VARP_ATTACK_PRIORITY_PLAYER) == 4 && client.varp(VARP_ATTACK_PRIORITY_NPC) == 1 }
                }
                client.awaitBits(SAVE_SOUNDS_VARP, 0, 8, 80)
                assertEquals(0, client.varp(VARP_MUSIC_VOLUME))
            }
        }
    }

    @Test
    fun `a layout change keeps scroll-wheel zoom off`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.ifButton(INTERFACE, ZOOM_TOGGLE, 1)
                client.awaitBits(GameFrame.STONE_ARRANGEMENT_VARP, 29, 1, 1)
                // Resizable modern sets bit 8 of the same varp, and sends the whole varp.
                client.ifButton(INTERFACE, GameFrame.SETTINGS_CLIENT_LAYOUT_BUTTONS, 1, sub = 3)
                client.awaitBits(GameFrame.STONE_ARRANGEMENT_VARP, GameFrame.STONE_ARRANGEMENT_BIT, 1, 1)
                client.awaitBits(GameFrame.STONE_ARRANGEMENT_VARP, 29, 1, 1)
            }
        }
    }

    @Test
    fun `house and bond options say they aren't here`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("frank").use { client ->
                client.await("the login rebuild") { client.position }
                client.ifButton(INTERFACE, HOUSE_OPTIONS, 1)
                client.ifButton(INTERFACE, BOND_OPTIONS, 1)
                client.await("the house message") { client.messages().firstOrNull { "houses" in it.text } }
                client.await("the bond message") { client.messages().firstOrNull { "bonds" in it.text } }
            }
        }
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
        const val RUN_VARP = 173
        const val AID_VARP = 427
        const val PANEL_VARP = 1795
        const val SETTINGS_VARP_3 = 3075
        const val SAVE_SOUNDS_VARP = 3109
        const val SAVE_SOUNDS_2_VARP = 3797

        val MASTER_TRIGGER = 3_081_216_314L.toInt()
        val MUSIC_TRIGGER = 406_545_006
        val SOUND_TRIGGER = 3_225_222_424L.toInt()
        val AREASOUNDS_TRIGGER = 2_493_361_579L.toInt()
    }
}
