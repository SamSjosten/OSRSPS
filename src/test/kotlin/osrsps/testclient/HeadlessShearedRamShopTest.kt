package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.ShearedRam
import osrsps.game.npc.NpcSpawn
import osrsps.game.shop.ShopScreen
import osrsps.game.shop.ShopTypes
import osrsps.map.CollisionMap

/**
 * The Sheared Ram's Bartender's Trade (op 3 in the 242 cache, added in the 3 November 2026 update) through the headless
 * client, from the real shops.json entry: beer and cider at 2 coins, cooked meat at 4, none bought back.
 */
class HeadlessShearedRamShopTest {
    private val bartender = NpcType(ShearedRam.BARTENDER, "Bartender", ops = listOf("Talk-to", null, "Trade", null, null), internalName = "ram_bartender")

    private val objs = ObjTypes.of(
        ObjType(COINS, "Coins", stackable = true, tradeable = true),
        ObjType(BEER, "Beer", cost = 2, tradeable = true),
        ObjType(CIDER, "Cider", cost = 2, tradeable = true),
        ObjType(MEAT, "Cooked meat", cost = 4, tradeable = true),
    )

    private val shops = ShopTypes.parse(ShopTypes::class.java.getResource(ShopTypes.RESOURCE)!!.readText(), NpcTypes.of(bartender), objs)
        .let { all -> ShopTypes(listOf(all["sheared_ram"]!!)) }

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3232 shr 6, 3241 shr 6) }, objTypes = objs, npcTypes = NpcTypes.of(bartender),
            npcSpawns = listOf(NpcSpawn(bartender, 3232, 3241, source = "test")), shopTypes = shops,
            varTypes = VarTypes(6_000, mapOf(ShopScreen.VARBIT_QUANTITY to VarbitType(5_100, 0, 2))), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.held(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    @Test
    fun `Trade opens the inn's shop, and a beer costs 2 coins and cooked meat 4`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("rae").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele 3232 3239 0")
                client.command("item $COINS 10")
                client.await("the coins") { if (client.held(COINS) == 10) Unit else null }
                val npc = client.await("the bartender in view") { client.npcs().firstOrNull { it.id == ShearedRam.BARTENDER } }
                client.npcOp(npc.index, 3)
                client.await("the shop") { if (ShopScreen.MAIN in client.openInterfaces()) Unit else null }
                client.await("the stock") {
                    client.inventory(PUB_LUMBRIDGE).takeIf { it == listOf(InvObj(BEER, 1000), InvObj(CIDER, 1000), InvObj(MEAT, 1000)) }
                }
                client.await("Buy on the first item") { if (client.opEnabled(ShopScreen.MAIN, ShopScreen.MAIN_ITEMS, 1, 2)) Unit else null }
                client.ifButton(ShopScreen.MAIN, ShopScreen.MAIN_ITEMS, 2, 1, BEER)
                client.await("a beer for 2") { if (client.held(BEER) == 1 && client.held(COINS) == 8) Unit else null }
                client.ifButton(ShopScreen.MAIN, ShopScreen.MAIN_ITEMS, 2, 3, MEAT)
                client.await("cooked meat for 4") { if (client.held(MEAT) == 1 && client.held(COINS) == 4) Unit else null }
                assertEquals(1, client.held(BEER))
            }
        }
    }

    private companion object {
        const val COINS = 995
        const val BEER = 1917
        const val CIDER = 5763
        const val MEAT = 2142
        /** pub_lumbridge in the 242 cache. */
        const val PUB_LUMBRIDGE = 1034
    }
}
