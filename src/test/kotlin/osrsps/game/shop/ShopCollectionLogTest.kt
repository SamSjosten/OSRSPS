package osrsps.game.shop

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player

/** Collection log step 1: what a shop that logs its sales (Grace's) sells goes in the log as it is bought. */
class ShopCollectionLogTest {
    private val mark = ObjType(11849, "Mark of grace", stackable = true)
    private val hood = ObjType(11850, "Graceful hood", cost = 35)
    private val axe = ObjType(1351, "Bronze axe", cost = 16)
    private val objTypes = ObjTypes.of(mark, hood, axe, ObjType(ShopScreen.COINS, "Coins", stackable = true))
    private val logged = mutableListOf<Pair<String, Int>>()

    private fun screen(collectionLog: Boolean) = ShopScreen(
        ShopTypes(listOf(ShopType("grace", "Grace's", 279, 40, false, 1000, 800, 0, listOf(ShopStock(hood, 100, 1), ShopStock(axe, 5, 1)), emptyList(), currency = mark.id, collectionLog = collectionLog))),
        objTypes,
        openModal = { player, slot, id -> player.modals[slot] = id },
        examine = { _, _, _ -> },
        send = { _, _ -> },
        logged = { player, type -> logged += player.username to type.id },
    )

    private fun buyAll(screen: ShopScreen): Player {
        val player = Player(1, "alice", 3222, 3218, 0)
        player.inventory.add(mark, 1_000)
        screen.open(player, screen["grace"]!!.type)
        // Slot 1 is the hood (sub 0 is the highlight): Buy 5, then the axe once.
        screen.click(player, ShopScreen.MAIN, ShopScreen.MAIN_ITEMS, 1, hood.id, 3)
        screen.click(player, ShopScreen.MAIN, ShopScreen.MAIN_ITEMS, 2, axe.id, 2)
        return player
    }

    @Test
    fun `each item bought is logged, one at a time`() {
        buyAll(screen(collectionLog = true))
        assertEquals(List(5) { "alice" to hood.id } + ("alice" to axe.id), logged)
    }

    @Test
    fun `a shop that doesn't log its sales logs nothing`() {
        val player = buyAll(screen(collectionLog = false))
        assertEquals(5L, player.inventory.count(hood.id))
        assertEquals(emptyList(), logged)
    }
}
