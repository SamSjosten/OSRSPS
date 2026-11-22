package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.AllSettings
import osrsps.game.PlayerLook
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.look.DesignScreen
import osrsps.game.look.MakeoverCatalog
import osrsps.game.look.MakeoverCatalog.Style
import osrsps.game.look.MakeoverPage
import osrsps.map.CollisionMap

/**
 * Looks step 4 through the headless client: a new account gets the Character Creator (679) at its first login; its
 * part and colour arrows step through the styles and colours, wrapping, and each change goes on the player at once;
 * body type B switches every part to its body type B style and lights B; the Pronouns dropdown sets the pronoun;
 * Confirm closes it, and the next login doesn't open it again. The rl-client script
 * test-runner/examples/look-design-screen.json makes the same clicks in the real client on a fresh account.
 */
class HeadlessDesignTest {
    // The first styles of the 242 cache's pages, with their kits, as HeadlessMakeoverTest has them.
    private val catalog = MakeoverCatalog(
        mapOf(
            MakeoverPage.HAIR to MakeoverCatalog.Page(listOf(Style(0, 45), Style(1, 47), Style(2, 48)), 30),
            MakeoverPage.FACIAL_HAIR to MakeoverCatalog.Page(listOf(Style(14, 296), Style(10, 292)), 30),
            MakeoverPage.TORSOS to MakeoverCatalog.Page(listOf(Style(18, 56), Style(24, 60), Style(105, 89)), 29),
            MakeoverPage.SLEEVES to MakeoverCatalog.Page(listOf(Style(26, 61), Style(27, 63)), 29),
            MakeoverPage.BOTTOMS to MakeoverCatalog.Page(listOf(Style(36, 70), Style(38, 72)), 29),
            MakeoverPage.SHOES to MakeoverCatalog.Page(listOf(Style(42, 79), Style(43, 80)), 6),
            MakeoverPage.WRISTWEAR to MakeoverCatalog.Page(listOf(Style(33, 67), Style(34, 68)), 0),
        ),
    )

    private fun server(dir: Path, catalog: MakeoverCatalog = this.catalog) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            // player_design_bodytype is bit 2 of tutorial_temp (1797) in the 242 cache; settings_transmit_pronouns
            // bits 24-25 of settings_tracking (2855).
            varTypes = VarTypes(
                6_000,
                mapOf(
                    DesignScreen.VARBIT_BODYTYPE to VarbitType(TUTORIAL_TEMP, 2, 2),
                    AllSettings.VARBIT_PRONOUNS to VarbitType(SETTINGS_TRACKING, 24, 25),
                ),
            ),
            makeoverCatalog = catalog, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.awaitDesign() = await("the Character Creator") { if (DesignScreen.INTERFACE in openInterfaces()) Unit else null }

    private fun HeadlessClient.awaitLook(description: String, matches: (AppearanceView) -> Boolean) = await(description) { appearance()?.takeIf(matches) }

    private fun HeadlessClient.click(component: Int, sub: Int = -1) = ifButton(DesignScreen.INTERFACE, component, 1, sub)

    /** The left (by -1) or right arrow of body part [part]. */
    private fun HeadlessClient.partArrow(part: Int, by: Int) = click(DesignScreen.PART_ARROWS.first + 4 * part + if (by < 0) 0 else 1)

    private fun HeadlessClient.colourArrow(part: PlayerLook.ColourPart, by: Int) = click(DesignScreen.COLOUR_ARROWS.first + 4 * part.ordinal + if (by < 0) 0 else 1)

    @Test
    fun `a new account gets the Character Creator, whose arrows step the parts and colours on the player`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.awaitDesign()
                assertEquals(PlayerLook.DEFAULT.kits, client.appearance()?.kits)
                client.partArrow(PlayerLook.BodyPart.HAIR, 1)
                client.awaitLook("the second hairstyle") { it.kits[PlayerLook.BodyPart.HAIR] == 1 }
                // Back to the first, then past it to the last.
                client.partArrow(PlayerLook.BodyPart.HAIR, -1)
                client.partArrow(PlayerLook.BodyPart.HAIR, -1)
                client.awaitLook("the last hairstyle, wrapped") { it.kits[PlayerLook.BodyPart.HAIR] == 2 }
                // The default jaw (10) is the facial hair page's second style; the right arrow wraps to the first.
                client.partArrow(PlayerLook.BodyPart.JAW, 1)
                client.awaitLook("the first facial hair") { it.kits[PlayerLook.BodyPart.JAW] == 14 }
                client.partArrow(PlayerLook.BodyPart.FEET, 1)
                client.awaitLook("the second shoes") { it.kits[PlayerLook.BodyPart.FEET] == 43 }
                client.colourArrow(PlayerLook.ColourPart.HAIR, -1)
                client.awaitLook("the last hair colour") { it.colours[PlayerLook.ColourPart.HAIR.ordinal] == 29 }
                // Skin steps through the unlocked colours only: left of 0 is 12, right of 7 is 12 too.
                client.colourArrow(PlayerLook.ColourPart.SKIN, -1)
                client.awaitLook("skin 12") { it.colours[PlayerLook.ColourPart.SKIN.ordinal] == 12 }
                client.colourArrow(PlayerLook.ColourPart.SKIN, 1)
                client.awaitLook("skin 0, wrapped") { it.colours[PlayerLook.ColourPart.SKIN.ordinal] == 0 }
                // A part's layer and text aren't arrows.
                for (component in listOf(17, 18, 48, 49)) client.click(component)
                client.click(DesignScreen.CONFIRM)
                client.await("the creator closed") { if (DesignScreen.INTERFACE !in client.openInterfaces()) Unit else null }
                val kept = client.commandReply("look") { it.startsWith("Look:") }
                assertTrue("kits hair 2, jaw 14," in kept && "feet 43" in kept && "colours hair 29" in kept, kept)
            }
        }
    }

    @Test
    fun `body type B switches every part and lights B, the pronoun dropdown sets the pronoun, and the next login has no creator`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.awaitDesign()
                // The three pronouns, children 1 to 3, whose events can arrive just after the screen.
                client.await("the pronoun options enabled") { if (client.opEnabled(DesignScreen.INTERFACE, DesignScreen.PRONOUNS_BUTTONS, 3, 1)) Unit else null }
                client.click(DesignScreen.BODY_TYPE_B)
                val b = client.awaitLook("body type B") { it.bodyType == PlayerLook.BODY_TYPE_B }
                assertEquals(listOf(45, 292, 56, 61, 67, 70, 79), b.kits)
                client.await("B lit") { if (client.varp(TUTORIAL_TEMP) shr 2 and 1 == 1) Unit else null }
                client.partArrow(PlayerLook.BodyPart.HAIR, 1)
                client.awaitLook("body type B's second hairstyle") { it.kits[PlayerLook.BodyPart.HAIR] == 47 }
                // They/them, the third option: child 3.
                client.click(DesignScreen.PRONOUNS_BUTTONS, 3)
                client.await("settings_transmit_pronouns 2") { if (client.varp(SETTINGS_TRACKING) shr 24 and 3 == 2) Unit else null }
                assertTrue("pronouns they/them" in client.commandReply("look") { it.startsWith("Look:") })
                client.click(DesignScreen.CONFIRM)
                client.await("the creator closed") { if (DesignScreen.INTERFACE !in client.openInterfaces()) Unit else null }
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.loginAgain("bob").use { client ->
                client.await("the login rebuild") { client.position }
                val look = client.awaitLook("the designed look") { true }
                assertEquals(PlayerLook.BODY_TYPE_B, look.bodyType)
                assertEquals(47, look.kits[PlayerLook.BodyPart.HAIR])
                // A few ticks for anything the login would open.
                client.commandReply("look") { it.startsWith("Look:") }
                assertFalse(DesignScreen.INTERFACE in client.openInterfaces(), "only a new account gets the creator")
            }
        }
    }

    @Test
    fun `without the cache's makeover pages there is no creator`(@TempDir dir: Path) {
        server(dir, MakeoverCatalog.NONE).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("look") { it.startsWith("Look:") }
                assertFalse(DesignScreen.INTERFACE in client.openInterfaces())
                // A click without it open does nothing.
                client.click(DesignScreen.BODY_TYPE_B)
                assertTrue("body type A" in client.commandReply("look") { it.startsWith("Look:") })
            }
        }
    }

    /** Logs in once the server has let the last login of [name] go: until then a login is refused as still on. */
    private fun TestServer.loginAgain(name: String): HeadlessClient {
        repeat(50) {
            runCatching { login(name) }.onSuccess { return it }
            Thread.sleep(100)
        }
        return login(name)
    }

    private companion object {
        const val TUTORIAL_TEMP = 1797
        const val SETTINGS_TRACKING = 2855
    }
}
