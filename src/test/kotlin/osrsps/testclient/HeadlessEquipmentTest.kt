package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.EquipmentTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos
import osrsps.game.WearRequirements
import osrsps.game.World
import osrsps.map.CollisionMap

/**
 * Task 15's checks in RuneLite, through the headless client: Wield from the inventory, the stats screen, two-handed
 * weapons and shields, a level requirement, and equipment kept over a logout. The rl-client script
 * test-runner/examples/equipment-wield.json makes the same clicks in the real client.
 */
class HeadlessEquipmentTest {
    // Made-up items shaped like the 241 cache's, as in EquipmentTest.
    private val axe = ObjType(AXE, "Bronze axe", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, params = mapOf(1 to 4, 10 to 5, 14 to 5), category = 35)
    private val twoHander = ObjType(TWO_HANDER, "Bronze 2h sword", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, wearpos2 = WearPos.SHIELD, params = mapOf(14 to 7), category = 61)
    private val shield = ObjType(SHIELD, "Wooden shield", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.SHIELD, params = mapOf(5 to 4, 6 to 5))
    private val whip = ObjType(WHIP, "Abyssal whip", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, params = mapOf(1 to 82), internalName = "abyssal_whip", category = 150)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(WHIP + 1)
        for (type in listOf(axe, twoHander, shield, whip)) objs[type.id] = type
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            // combat_weapon_category is bits 0 to 5 of varp 843 in the 241 cache.
            varTypes = VarTypes(6_000, mapOf(VARBIT_WEAPON_CATEGORY to VarbitType(VARP_WEAPON_CATEGORY, 0, 5))),
            // The real requirements file, so the whip needs the level it does in the game.
            wearRequirements = WearRequirements.load(ObjTypes(objs)),
            cycleMillis = millis,
        )
    }

    /** Clicks [option] (iop 1-5) on the first [id] in the backpack, as the inventory's IF_BUTTONX: iop2 is op 3. */
    private fun HeadlessClient.itemOp(id: Int, iop: Int) {
        val slot = await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }
        ifButton(INVENTORY, INVENTORY_ITEMS, IOP_BUTTON_OPS[iop - 1], slot, id)
    }

    /** Waits for [text] among every message so far: both lines of a refusal can arrive in the same tick. */
    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    /** Waits for the stats screen's [component] to say [text]: its lines come one IF_SETTEXT each. */
    private fun HeadlessClient.awaitText(component: Int, text: String) =
        await("\"$text\" on the stats screen") { text(STATS, component)?.takeIf { it == text } }

    /** Waits for the combat tab's weapon name and category line, which the server sets as IF_SETTEXT. */
    private fun HeadlessClient.awaitCombatTab(title: String, category: String) {
        await("\"$title\" on the combat tab") { text(COMBAT, COMBAT_TITLE)?.takeIf { it == title } }
        await("\"$category\" on the combat tab") { text(COMBAT, COMBAT_CATEGORY)?.takeIf { it == category } }
    }

    private fun HeadlessClient.worn(wearpos: Int): Int? = inventory(WORN).getOrNull(wearpos)?.id

    private fun HeadlessClient.awaitWorn(wearpos: Int, id: Int?) =
        await("${id ?: "nothing"} in worn slot $wearpos") { if (worn(wearpos) == id) Unit else null }

    @Test
    fun `Wield puts the axe in the weapon slot, the combat tab gets its name and category and the stats screen its bonuses`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.awaitCombatTab("Unarmed", "Category: Unarmed")
                client.command("item $AXE")
                client.itemOp(AXE, WIELD)
                client.awaitWorn(WearPos.WEAPON, AXE)
                client.await("the axe out of the backpack") { if (client.inventory(BACKPACK).none { it?.id == AXE }) Unit else null }
                client.await("the axe category") { if (client.varp(VARP_WEAPON_CATEGORY) and 0x3F == AXE_CATEGORY) Unit else null }
                client.awaitCombatTab("Bronze axe", "Category: Axe")

                client.ifButton(WORNITEMS, WORNITEMS_VIEW_STATS, 1)
                client.awaitText(SLASH_ATT, "Slash: +4")
                client.awaitText(MELEE_STRENGTH, "Melee strength: +5")
                client.awaitText(SPEED_BASE, "Base: 3.0s")
                assertEquals(true, STATS in client.openInterfaces() && STATS_SIDE in client.openInterfaces())

                client.ifButton(WORNITEMS, WORNITEMS_WEAPON, REMOVE)
                client.awaitWorn(WearPos.WEAPON, null)
                client.await("the axe back in the backpack") { client.inventory(BACKPACK).firstOrNull { it?.id == AXE } }
                client.await("unarmed again") { if (client.varp(VARP_WEAPON_CATEGORY) and 0x3F == 0) Unit else null }
                client.awaitCombatTab("Unarmed", "Category: Unarmed")
            }
        }
    }

    @Test
    fun `Equip on the stats screen keeps it open, and a two-handed weapon takes the shield off`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $SHIELD")
                client.command("item $TWO_HANDER")
                client.ifButton(WORNITEMS, WORNITEMS_VIEW_STATS, 1)
                client.await("the stats screen") { if (STATS in client.openInterfaces()) Unit else null }
                val slot = client.await("the shield") { client.inventory(BACKPACK).indexOfFirst { it?.id == SHIELD }.takeIf { it >= 0 } }
                client.ifButton(STATS_SIDE, STATS_SIDE_ITEMS, 1, slot, SHIELD)
                client.awaitWorn(WearPos.SHIELD, SHIELD)
                client.awaitText(STAB_DEF, "Stab: +4")
                assertEquals(true, STATS in client.openInterfaces())

                client.itemOp(TWO_HANDER, WIELD)
                client.awaitWorn(WearPos.WEAPON, TWO_HANDER)
                client.awaitWorn(WearPos.SHIELD, null)
                client.await("the shield back in the backpack") { client.inventory(BACKPACK).firstOrNull { it?.id == SHIELD } }
            }
        }
    }

    @Test
    fun `the whip needs 70 Attack, and what is worn is still worn after a logout`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $WHIP")
                client.itemOp(WHIP, WIELD)
                client.seen("You are not a high enough level to use this item.")
                client.seen("You need to have an Attack level of 70.")
                assertEquals(null, client.worn(WearPos.WEAPON))

                client.command("setlevel attack 70")
                client.await("Attack 70") { client.stat(ATTACK)?.takeIf { it.level == 70 } }
                client.itemOp(WHIP, WIELD)
                client.awaitWorn(WearPos.WEAPON, WHIP)
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            // The save is written as the player leaves the world; a login a moment too early finds them still in it.
            server.loginAgain("carol").use { client ->
                client.awaitWorn(WearPos.WEAPON, WHIP)
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
        const val AXE = 1351
        const val TWO_HANDER = 1307
        const val SHIELD = 1171
        const val WHIP = 4151
        const val BACKPACK = 93
        const val WORN = 94
        const val ATTACK = 0

        const val WIELD = 2
        /** The inventory's button op for iop1 to iop5 (InventoryTab.IOP_BY_BUTTON_OP). */
        val IOP_BUTTON_OPS = intArrayOf(2, 3, 4, 6, 7)

        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
        // The server's own component ids: the stats screen's moved with cache 241.4 (#80).
        const val WORNITEMS = EquipmentTab.WORNITEMS
        const val WORNITEMS_VIEW_STATS = EquipmentTab.WORNITEMS_VIEW_STATS
        /** wornitems:slot3, the weapon. */
        const val WORNITEMS_WEAPON = 18
        const val REMOVE = 1
        const val STATS = EquipmentTab.STATS
        const val STATS_SIDE = EquipmentTab.STATS_SIDE
        const val STATS_SIDE_ITEMS = EquipmentTab.STATS_SIDE_ITEMS
        const val SLASH_ATT = EquipmentTab.SLASH_ATT
        const val STAB_DEF = EquipmentTab.STAB_DEF
        const val MELEE_STRENGTH = EquipmentTab.MELEE_STRENGTH
        const val SPEED_BASE = EquipmentTab.SPEED_BASE
        const val COMBAT = EquipmentTab.COMBAT
        const val COMBAT_TITLE = EquipmentTab.COMBAT_TITLE
        const val COMBAT_CATEGORY = EquipmentTab.COMBAT_CATEGORY

        const val VARBIT_WEAPON_CATEGORY = 357
        const val VARP_WEAPON_CATEGORY = 843
        const val AXE_CATEGORY = 1
    }
}
