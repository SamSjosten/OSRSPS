package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.PlayerLook
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.VarrockStores
import osrsps.game.look.MakeoverCatalog
import osrsps.game.look.MakeoverCatalog.Style
import osrsps.game.look.MakeoverPage
import osrsps.game.look.MakeoverScreen
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Looks step 2 through the headless client: Thessalia's change top and legwear options and her Makeover option open
 * the makeover screen on its tops or bottoms; a style or swatch shows on the player until Apply or Confirm keeps it or
 * the screen closes without it; Confirm after her talk gets one of her six lines; the burger menu switches pages; clicks
 * between children or past the last do nothing. Looks step 3: the hair and facial hair pages, and the body types page
 * (205): body type, skin colour and pronouns, each click mirrored in the varps the client set itself. The rl-client script test-runner/examples/varrock-thessalia-makeover.json
 * makes the same clicks in the real client.
 */
class HeadlessMakeoverTest {
    private val thessalia = NpcType(VarrockStores.THESSALIA, "Thessalia", ops = listOf("Talk-to", null, "Trade", "Makeover", null), internalName = "thessalia_normal")

    // The first styles of the 242 cache's pages, with their kits, and its colour counts.
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

    private fun server(dir: Path, settings: osrsps.game.SettingsCatalog? = null) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, npcTypes = NpcTypes.of(thessalia),
            npcSpawns = listOf(NpcSpawn(thessalia, 3222, 3220, source = "test")),
            // makeover_interface_type and makeover_bodytype are bits 0-4 and 5 of makeover_temp (3787) in the 242 cache.
            varTypes = VarTypes(
                6_000,
                osrsps.game.SettingsSide.VARBITS + osrsps.game.AllSettings.VARBITS + mapOf(
                    MakeoverScreen.VARBIT_INTERFACE_TYPE to VarbitType(MAKEOVER_TEMP, 0, 4),
                    MakeoverScreen.VARBIT_BODYTYPE to VarbitType(MAKEOVER_TEMP, 5, 5),
                    // settings_transmit_pronouns, bits 24-25 of settings_tracking (2855).
                    osrsps.game.AllSettings.VARBIT_PRONOUNS to VarbitType(SETTINGS_TRACKING, 24, 25),
                ),
            ),
            makeoverCatalog = catalog, settings = settings ?: osrsps.game.SettingsCatalog.NONE, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.awaitOpen(page: MakeoverPage) = await("the makeover screen on ${page.name}") {
        if (MakeoverScreen.INTERFACE in openInterfaces() && varp(MAKEOVER_TEMP) and 0x1F == page.type &&
            opEnabled(MakeoverScreen.INTERFACE, MakeoverScreen.ITEM_AREA, 0, 1)
        ) Unit else null
    }

    private fun HeadlessClient.awaitClosed() = await("the makeover screen closed") { if (MakeoverScreen.INTERFACE !in openInterfaces()) Unit else null }

    private fun HeadlessClient.awaitKits(description: String, matches: (List<Int>, List<Int>) -> Boolean) =
        await(description) { appearance()?.takeIf { matches(it.kits, it.colours) } }

    private fun HeadlessClient.click(component: Int, sub: Int) = ifButton(MakeoverScreen.INTERFACE, component, 1, sub)

    private fun HeadlessClient.thessalia() = await("Thessalia in view") { npcs().firstOrNull { it.id == thessalia.id } }

    /** Thessalia's talk through to her change top (1) or legwear (2) option and her catalogue line. */
    private fun HeadlessClient.talkToMakeover(option: Int) {
        npcOp(thessalia().index, VarrockStores.TALK_TO)
        val read = listOf("greeting", "what_got", "offer", "more", "more_1", "more_2", "more_3", "more_4")
        val menus = mapOf(
            "greeting" to (1 to listOf("what_got", "buy", "no_thanks")),
            "offer" to (1 to listOf("more", "buy", "no_thanks")),
            "more_4" to (option to listOf("top", "legwear", "buy", "no_thanks")),
        )
        for (key in read) {
            awaitDialogue("thessalia.$key") { it is DialogueView.Chat && it.text == "[thessalia.$key]" }
            continueDialogue()
            val (pick, keys) = menus[key] ?: continue
            awaitDialogue("her menu after $key") { it is DialogueView.Menu && it.options == keys.map { k -> "[thessalia.$k]" } }
            chooseOption(pick)
        }
        awaitDialogue("thessalia.catalogue") { it is DialogueView.Chat && it.text == "[thessalia.catalogue]" }
        continueDialogue()
    }

    @Test
    fun `change top opens the tops, a style and a swatch preview on the player, and Confirm keeps them with one of her lines`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.talkToMakeover(1)
                client.awaitOpen(MakeoverPage.TORSOS)
                assertEquals(0, client.varp(MakeoverScreen.VARP_SELECTED), "the default torso, kit 18, is the first style")
                assertEquals(0, client.varp(MakeoverScreen.VARP_COLOUR))
                assertTrue(client.opEnabled(MakeoverScreen.INTERFACE, MakeoverScreen.COLOURS, 56, 1), "29 swatches, children 0 to 57")
                // The second style: its backing is child 5.
                client.click(MakeoverScreen.ITEM_AREA, 5)
                client.awaitKits("the jacket's kit previewed") { kits, _ -> kits[PlayerLook.BodyPart.TORSO] == 24 }
                client.await("the second style lit") { if (client.varp(MakeoverScreen.VARP_SELECTED) == 5) Unit else null }
                client.click(MakeoverScreen.COLOURS, 6)
                client.awaitKits("torso colour 3 previewed") { _, colours -> colours[PlayerLook.ColourPart.TORSO.ordinal] == 3 }
                client.click(MakeoverScreen.SELECT_AND_CLOSE, 0)
                client.awaitClosed()
                val said = client.awaitDialogue("one of her compliments") { it is DialogueView.Chat }
                assertTrue((said as DialogueView.Chat).text in (1..6).map { "[thessalia.compliment_$it]" }, "said ${said.text}")
                client.continueDialogue()
                val kept = client.commandReply("look") { it.startsWith("Look:") }
                assertTrue("torso 24" in kept && "colours hair 0, torso 3" in kept, kept)
            }
        }
    }

    @Test
    fun `her Makeover option opens the tops, and closing without Apply puts the saved look back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.npcOp(client.thessalia().index, VarrockStores.MAKEOVER_OP)
                client.awaitOpen(MakeoverPage.TORSOS)
                client.click(MakeoverScreen.ITEM_AREA, 10)
                client.awaitKits("the third style previewed") { kits, _ -> kits[PlayerLook.BodyPart.TORSO] == 105 }
                client.closeModal()
                client.awaitClosed()
                client.awaitKits("the saved torso back") { kits, _ -> kits == PlayerLook.DEFAULT.kits }
                assertTrue("torso 18" in client.commandReply("look") { it.startsWith("Look:") })
            }
        }
    }

    @Test
    fun `Apply keeps a preview with the screen open, legwear opens the bottoms, and the burger menu switches pages`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.talkToMakeover(2)
                client.awaitOpen(MakeoverPage.BOTTOMS)
                client.click(MakeoverScreen.ITEM_AREA, 5)
                client.click(MakeoverScreen.SELECT, 0)
                client.awaitKits("the second bottoms previewed") { kits, _ -> kits[PlayerLook.BodyPart.LEGS] == 38 }
                client.await("still open") { if (MakeoverScreen.INTERFACE in client.openInterfaces()) Unit else null }
                // The burger menu's Shoes, child 10 + 2 × 6; the screen reopens on it, the applied bottoms kept.
                client.click(MakeoverScreen.BURGER_MENU_FRAME, MakeoverScreen.menuChild(MakeoverPage.SHOES.type))
                client.awaitOpen(MakeoverPage.SHOES)
                client.click(MakeoverScreen.COLOURS, 10)
                client.awaitKits("feet colour 5 previewed") { _, colours -> colours[PlayerLook.ColourPart.FEET.ordinal] == 5 }
                client.closeModal()
                client.awaitClosed()
                client.awaitKits("the bottoms kept, the shoe colour gone") { kits, colours ->
                    kits[PlayerLook.BodyPart.LEGS] == 38 && colours[PlayerLook.ColourPart.FEET.ordinal] == 0
                }
                // No line after a makeover she didn't offer in talk; here the screen closed without a Confirm.
                assertEquals(null, client.dialogueView)
            }
        }
    }

    @Test
    fun `clicks between children, past the last style or swatch, or between the burger menu's pages change nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.npcOp(client.thessalia().index, VarrockStores.MAKEOVER_OP)
                client.awaitOpen(MakeoverPage.TORSOS)
                // A style's name (child 4 of the first), the fourth style (none), swatch 29 (none), a swatch's frame,
                // the burger menu's highlight behind Hair (child 9) and its backing (child 0).
                for ((component, sub) in listOf(
                    MakeoverScreen.ITEM_AREA to 4, MakeoverScreen.ITEM_AREA to 15, MakeoverScreen.COLOURS to 58, MakeoverScreen.COLOURS to 3,
                    MakeoverScreen.BURGER_MENU_FRAME to MakeoverScreen.menuChild(0) - 1, MakeoverScreen.BURGER_MENU_FRAME to 0,
                )) client.click(component, sub)
                client.click(MakeoverScreen.SELECT_AND_CLOSE, 0)
                client.awaitClosed()
                assertEquals(PlayerLook.DEFAULT.kits, client.appearance()?.kits)
                assertTrue("torso 18" in client.commandReply("look") { it.startsWith("Look:") } && client.varp(MAKEOVER_TEMP) and 0x1F == 3)
            }
        }
    }

    @Test
    fun `the hair pages change hair, facial hair and hair colour`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.npcOp(client.thessalia().index, VarrockStores.MAKEOVER_OP)
                client.awaitOpen(MakeoverPage.TORSOS)
                client.click(MakeoverScreen.BURGER_MENU_FRAME, MakeoverScreen.menuChild(MakeoverPage.HAIR.type))
                client.awaitOpen(MakeoverPage.HAIR)
                client.click(MakeoverScreen.ITEM_AREA, 10)
                client.click(MakeoverScreen.COLOURS, 58)
                client.awaitKits("the third hairstyle in hair colour 29") { kits, colours -> kits[PlayerLook.BodyPart.HAIR] == 2 && colours[0] == 29 }
                client.click(MakeoverScreen.SELECT, 0)
                client.click(MakeoverScreen.BURGER_MENU_FRAME, MakeoverScreen.menuChild(MakeoverPage.FACIAL_HAIR.type))
                client.awaitOpen(MakeoverPage.FACIAL_HAIR)
                // The default jaw, kit 10, is the second facial hair style.
                assertEquals(5, client.varp(MakeoverScreen.VARP_SELECTED))
                assertEquals(29, client.varp(MakeoverScreen.VARP_COLOUR), "the hair colour, shared with the hair page")
                client.click(MakeoverScreen.ITEM_AREA, 0)
                client.click(MakeoverScreen.SELECT_AND_CLOSE, 0)
                client.awaitClosed()
                val kept = client.commandReply("look") { it.startsWith("Look:") }
                assertTrue("hair 2, jaw 14" in kept && "colours hair 29" in kept, kept)
            }
        }
    }

    @Test
    fun `the body types page switches body type keeping each style, sets skin colour and pronouns, and refuses locked colours`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("fred").use { client ->
                client.await("the login rebuild") { client.position }
                client.npcOp(client.thessalia().index, VarrockStores.MAKEOVER_OP)
                client.awaitOpen(MakeoverPage.TORSOS)
                client.click(MakeoverScreen.ITEM_AREA, 5)
                client.click(MakeoverScreen.SELECT, 0)
                client.click(MakeoverScreen.BURGER_MENU_FRAME, MakeoverScreen.menuChild(MakeoverScreen.BODY_TYPES_TYPE))
                client.await("the body types page") {
                    if (MakeoverScreen.BODY_TYPES in client.openInterfaces() && client.opEnabled(MakeoverScreen.BODY_TYPES, MakeoverScreen.BT_COLOUR, 13, 1)) Unit else null
                }
                assertEquals(listOf(0, 0, 0), listOf(MakeoverScreen.VARP_IF1, MakeoverScreen.VARP_IF2, MakeoverScreen.VARP_IF3).map(client::varp))
                client.ifButton(MakeoverScreen.BODY_TYPES, MakeoverScreen.BT_BODY_TYPE_B, 1)
                // Each part's style kept as body type B's kit of it: the applied second top (24) is 60.
                val typeB = listOf(45, 292, 60, 61, 67, 70, 79)
                client.await("body type B previewed") { client.appearance()?.takeIf { it.bodyType == 1 && it.kits == typeB } }
                client.await("if1 and if3 mirrored") { if (client.varp(MakeoverScreen.VARP_IF1) == 1 && client.varp(MakeoverScreen.VARP_IF3) == 1) Unit else null }
                // Child 0 is skin colour 7 (enum 999's order); child 9 is colour 9, a Halloween one, locked.
                client.ifButton(MakeoverScreen.BODY_TYPES, MakeoverScreen.BT_COLOUR, 1, 0)
                client.ifButton(MakeoverScreen.BODY_TYPES, MakeoverScreen.BT_COLOUR, 1, 9)
                client.ifButton(MakeoverScreen.BODY_TYPES, MakeoverScreen.BT_THEY_THEM, 1)
                client.await("they/them mirrored") { if (client.varp(MakeoverScreen.VARP_IF3) == 2) Unit else null }
                client.await("skin 7 previewed") { client.appearance()?.takeIf { it.colours[PlayerLook.ColourPart.SKIN.ordinal] == 7 } }
                assertEquals(7, client.varp(MakeoverScreen.VARP_IF2))
                // Back to A keeps they/them.
                client.ifButton(MakeoverScreen.BODY_TYPES, MakeoverScreen.BT_BODY_TYPE_A, 1)
                client.ifButton(MakeoverScreen.BODY_TYPES, MakeoverScreen.BT_BODY_TYPE_B, 1)
                client.ifButton(MakeoverScreen.BODY_TYPES, MakeoverScreen.BT_CONFIRM, 1)
                client.awaitClosed()
                client.await("the body types page closed") { if (MakeoverScreen.BODY_TYPES !in client.openInterfaces()) Unit else null }
                val kept = client.commandReply("look") { it.startsWith("Look:") }
                assertTrue("body type B; kits hair 45, jaw 292, torso 60" in kept && "skin 7" in kept && "pronouns they/them" in kept, kept)
                // The All Settings window's Pronouns shows it too.
                client.await("settings_transmit_pronouns 2") { if (client.varp(SETTINGS_TRACKING) shr 24 and 3 == 2) Unit else null }
            }
        }
    }

    @Test
    fun `logging out with a preview showing keeps the saved look`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("frank").use { client ->
                client.await("the login rebuild") { client.position }
                client.npcOp(client.thessalia().index, VarrockStores.MAKEOVER_OP)
                client.awaitOpen(MakeoverPage.TORSOS)
                client.click(MakeoverScreen.ITEM_AREA, 10)
                client.awaitKits("the third style previewed") { kits, _ -> kits[PlayerLook.BodyPart.TORSO] == 105 }
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.loginAgain("frank").use { client ->
                client.await("the login rebuild") { client.position }
                assertEquals(PlayerLook.DEFAULT.kits, client.await("the saved look") { client.appearance() }.kits)
                assertTrue(MakeoverScreen.INTERFACE !in client.openInterfaces())
            }
        }
    }

    @Test
    fun `picking pronouns in the All Settings window changes the look's pronoun, kept over a relog`(@TempDir dir: Path) {
        val settings = pronounSettings()
        server(dir, settings).use { server ->
            server.login("gina").use { client ->
                client.await("the login rebuild") { client.position }
                client.pickPronounInSettings(2)
                client.await("settings_transmit_pronouns 2") { if (client.varp(SETTINGS_TRACKING) shr 24 and 3 == 2) Unit else null }
                client.await("the look's pronoun") { client.commandReply("look") { it.startsWith("Look:") }.takeIf { "pronouns they/them" in it } }
                assertTrue("body type A" in client.commandReply("look") { it.startsWith("Look:") }, "the body type stays")
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.loginAgain("gina").use { client ->
                client.await("the login rebuild") { client.position }
                assertTrue("pronouns they/them" in client.commandReply("look") { it.startsWith("Look:") })
                client.await("settings_transmit_pronouns 2 at login") { if (client.varp(SETTINGS_TRACKING) shr 24 and 3 == 2) Unit else null }
            }
        }
    }

    /** One tab holding only Pronouns (setting 387, a dropdown of enum 5500's three options), read from settings_transmit_pronouns. */
    private fun pronounSettings(): osrsps.game.SettingsCatalog {
        val pronouns = osrsps.game.SettingRow(
            994, mapOf(osrsps.game.SettingRow.PARAM_ID to osrsps.game.AllSettings.PRONOUNS_DROPDOWN, osrsps.game.SettingRow.PARAM_KIND to osrsps.game.SettingRow.KIND_DROPDOWN), options = 3,
        )
        return osrsps.game.SettingsCatalog(
            listOf(osrsps.game.SettingsCategory(923, 0, emptyMap(), listOf(pronouns))), mapOf(994 to pronouns), emptyMap(),
            mapOf(osrsps.game.AllSettings.PRONOUNS_DROPDOWN to osrsps.game.SettingVar.Varbit(osrsps.game.AllSettings.VARBIT_PRONOUNS)), emptyMap(),
        )
    }

    /** Opens the All Settings window and picks pronoun [option] (0 he/him, 1 she/her, 2 they/them) from row 0's dropdown. */
    private fun HeadlessClient.pickPronounInSettings(option: Int) {
        ifButton(osrsps.game.SettingsSide.INTERFACE, osrsps.game.SettingsSide.SETTINGS_OPEN, 1)
        await("the window's dropdown options") {
            Unit.takeIf { osrsps.game.AllSettings.INTERFACE in openInterfaces() && opEnabled(osrsps.game.AllSettings.INTERFACE, osrsps.game.AllSettings.DROPDOWN_BUTTONS, 3 * 2 + 2, 1) }
        }
        // Row 0's Select, then the option, whose third child (3n + 2) has the op.
        ifButton(osrsps.game.AllSettings.INTERFACE, osrsps.game.AllSettings.SETTINGS_CLICKZONE, 1, sub = 0)
        ifButton(osrsps.game.AllSettings.INTERFACE, osrsps.game.AllSettings.DROPDOWN_BUTTONS, 1, sub = 3 * option + 2)
    }

    @Test
    fun `a pronoun picked in All Settings with the body types page open is what its Confirm keeps`(@TempDir dir: Path) {
        server(dir, pronounSettings()).use { server ->
            server.login("hana").use { client ->
                client.await("the login rebuild") { client.position }
                client.npcOp(client.thessalia().index, VarrockStores.MAKEOVER_OP)
                client.awaitOpen(MakeoverPage.TORSOS)
                client.click(MakeoverScreen.BURGER_MENU_FRAME, MakeoverScreen.menuChild(MakeoverScreen.BODY_TYPES_TYPE))
                client.await("the body types page") { if (MakeoverScreen.BODY_TYPES in client.openInterfaces()) Unit else null }
                client.pickPronounInSettings(2)
                client.await("they/them in if3") { if (client.varp(MakeoverScreen.VARP_IF3) == 2) Unit else null }
                assertTrue(MakeoverScreen.BODY_TYPES in client.openInterfaces(), "the settings floater leaves the page open")
                client.ifButton(MakeoverScreen.BODY_TYPES, MakeoverScreen.BT_CONFIRM, 1)
                client.await("the body types page closed") { if (MakeoverScreen.BODY_TYPES !in client.openInterfaces()) Unit else null }
                assertTrue("pronouns they/them" in client.commandReply("look") { it.startsWith("Look:") })
                client.await("settings_transmit_pronouns still 2") { if (client.varp(SETTINGS_TRACKING) shr 24 and 3 == 2) Unit else null }
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
        const val MAKEOVER_TEMP = 3787
        const val SETTINGS_TRACKING = 2855
    }
}
