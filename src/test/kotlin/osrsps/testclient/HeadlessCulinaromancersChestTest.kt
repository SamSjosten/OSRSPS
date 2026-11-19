package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.bank.BankScreen
import osrsps.game.content.CulinaromancersChest
import osrsps.game.content.WizardsTower
import osrsps.game.ge.GrandExchangeScreen
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.shop.ShopScreen
import osrsps.game.shop.ShopStock
import osrsps.game.shop.ShopType
import osrsps.game.shop.ShopTypes
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Post-quest content step 5 through the headless client: the Culinaromancer's Chest's Bank, Collect, Buy-food and
 * Buy-items (the map's multiloc id, which the bank rule doesn't see), and the Wizards' Tower basement's altar bare after
 * The Restless Ghost. The rl-client script test-runner/examples/culinaromancers-chest.json does the same in the real
 * client.
 */
class HeadlessCulinaromancersChestTest {
    private val gloves = ObjType(BARROWS_GLOVES, "Barrows gloves", cost = 100_000)
    private val chocolate = ObjType(CHOCOLATE, "Chocolate bar", cost = 10)

    private fun shop(key: String, inv: Int, stock: ObjType) = ShopType(
        key, "Culinaromancer's Chest", invId = inv, size = 1, general = false, sellAt = 1300, buyAt = 400, changePer = 20,
        stock = listOf(ShopStock(stock, 60, 1)), npcs = emptyList(),
    )

    private val chest = ZoneLoc(CulinaromancersChest.CHEST, LocShape.CENTREPIECE_STRAIGHT, 0, 3219, 9623, 0)
    private val altar = ZoneLoc(WizardsTower.RESTLESS_GHOST_ALTAR, LocShape.CENTREPIECE_STRAIGHT, 0, 3120, 9566, 0)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3219 shr 6, 9623 shr 6); markLoaded(3120 shr 6, 9566 shr 6) },
            objTypes = ObjTypes.of(gloves, chocolate),
            varTypes = VarTypes(6_000, mapOf(ALTAR_VARBIT to VarbitType(ALTAR_VARP, 0, 1), CHEST_VARBIT to VarbitType(ALTAR_VARP, 4, 7)), 21_000),
            locTypes = LocTypes(
                listOf(
                    // As the cache has it: the map's multiloc, showing the chest once the quest is far enough on.
                    LocType(
                        CulinaromancersChest.CHEST, "null", List(5) { null }, LocCollisionType(width = 1, length = 1), multiloc = true,
                        multiVarbit = CHEST_VARBIT, multiLocs = listOf(-1, -1, -1, CHEST_BASE, CHEST_BASE, CHEST_BASE, -1),
                    ),
                    LocType(CHEST_BASE, "Chest", listOf("Bank", null, "Collect", "Buy-food", "Buy-items"), LocCollisionType(width = 1, length = 1)),
                    LocType(
                        WizardsTower.RESTLESS_GHOST_ALTAR, "null", List(5) { null }, LocCollisionType(width = 1, length = 1), multiloc = true,
                        multiVarbit = ALTAR_VARBIT, multiLocs = listOf(ALTAR_SKULL, ALTAR_BARE, -1),
                    ),
                    LocType(ALTAR_SKULL, "Altar", listOf("Search", null, null, null, null), LocCollisionType(width = 1, length = 1)),
                    LocType(ALTAR_BARE, "Altar", listOf("Search", null, null, null, null), LocCollisionType(width = 1, length = 1)),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { add(chest); add(altar) },
            shopTypes = ShopTypes(listOf(shop(CulinaromancersChest.FOOD_SHOP, 365, chocolate), shop(CulinaromancersChest.ITEMS_SHOP, 375, gloves))),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.awaitOpen(id: Int, what: String) = await(what) { openInterfaces().takeIf { id in it } }

    private fun HeadlessClient.closeAll() {
        closeModal()
        await("closed") { if (BankScreen.MAIN !in openInterfaces() && ShopScreen.MAIN !in openInterfaces() && GrandExchangeScreen.COLLECTION_BOX !in openInterfaces()) Unit else null }
    }

    @Test
    fun `the chest banks, collects and opens both stores`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("varbit $CHEST_VARBIT 5")
                client.await("the chest shown") { if (client.varp(ALTAR_VARP) shr 4 and 15 == 5) Unit else null }
                client.tele(3219, 9622)
                client.locOp(chest.id, chest.x, chest.z, CulinaromancersChest.BANK)
                client.awaitOpen(BankScreen.MAIN, "the bank")
                client.closeAll()
                client.locOp(chest.id, chest.x, chest.z, CulinaromancersChest.COLLECT)
                client.awaitOpen(GrandExchangeScreen.COLLECTION_BOX, "the collection box")
                client.closeAll()
                client.locOp(chest.id, chest.x, chest.z, CulinaromancersChest.BUY_FOOD)
                client.await("the food store") { client.inventory(365).firstOrNull()?.takeIf { it.id == CHOCOLATE } }
                client.closeAll()
                client.locOp(chest.id, chest.x, chest.z, CulinaromancersChest.BUY_ITEMS)
                client.await("the items store") { client.inventory(375).firstOrNull()?.takeIf { it.id == BARROWS_GLOVES } }
                client.awaitOpen(ShopScreen.MAIN, "the shop")
            }
        }
    }

    @Test
    fun `the Restless Ghost's altar stands bare and searches to nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.await("the altar bare at login") { if (client.varp(ALTAR_VARP) and 1 == 1) Unit else null }
                client.tele(3120, 9565)
                val from = client.messages().size
                client.locOp(altar.id, altar.x, altar.z, WizardsTower.SEARCH)
                assertEquals("[game.nothing_interesting]", client.await("the search") { client.messages().drop(from).firstOrNull()?.text })
            }
        }
    }

    private companion object {
        const val BARROWS_GLOVES = 7462
        const val CHOCOLATE = 1973
        const val ALTAR_VARBIT = 2130
        const val ALTAR_VARP = 1000
        const val ALTAR_SKULL = 15050
        const val ALTAR_BARE = 15051
        const val CHEST_VARBIT = 1850
        const val CHEST_BASE = 12309
    }
}
