package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.Dusuri
import osrsps.game.npc.NpcSpawn
import osrsps.game.shop.ShopScreen
import osrsps.game.shop.ShopTypes
import osrsps.game.skill.ShootingStars
import osrsps.map.CollisionMap

/**
 * Shooting stars step 2 through the headless client: Dusuri as the multi-NPC he is, Trade only once he has met the
 * player, his shop from the real shops.json entry paid in stardust, and opening a bag full of gems and a soft clay pack.
 */
class HeadlessDusuriTest {
    private val open = listOf("Open", null, null, null, "Destroy")
    private val objs = ObjTypes.of(
        ObjType(ShootingStars.STARDUST, "Stardust", stackable = true, cost = 50),
        ObjType(RING, "Celestial ring (uncharged)", cost = 2000),
        ObjType(FRAGMENT, "Star fragment", cost = 3000),
        ObjType(Dusuri.BAG_FULL_OF_GEMS, "Bag full of gems", cost = 300, iops = open),
        ObjType(Dusuri.SOFT_CLAY_PACK, "Soft clay pack", cost = 150, iops = open),
        ObjType(Dusuri.SOFT_CLAY, "Soft clay", certlink = Dusuri.SOFT_CLAY + 1),
        ObjType(Dusuri.SOFT_CLAY + 1, "Soft clay", stackable = true, certlink = Dusuri.SOFT_CLAY, certtemplate = 799),
        *(Dusuri.GEM_TABLE.map { it.obj } + Dusuri.UNCUT_ONYX).flatMap { gem ->
            listOf(ObjType(gem, "Gem $gem", certlink = gem + 1), ObjType(gem + 1, "Gem $gem", stackable = true, certlink = gem, certtemplate = 799))
        }.toTypedArray(),
    )
    private val before = NpcType(Dusuri.BEFORE, "Dusuri", ops = listOf("Talk-to", null, null, null, null), internalName = "star_trader_1op")
    private val after = NpcType(Dusuri.AFTER, "Dusuri", ops = listOf("Talk-to", null, "Trade", null, null), internalName = "star_trader_2op")
    private val dusuri = NpcType(STAR_TRADER, "Dusuri", multiVarbit = Dusuri.VARBIT_MET, multiNpcs = intArrayOf(Dusuri.BEFORE, Dusuri.AFTER), internalName = "star_trader")
    private val shops = ShopTypes.parse(
        ShopTypes::class.java.getResource(ShopTypes.RESOURCE)!!.readText(), NpcTypes.of(before, after, dusuri), objs,
    ).let { all -> ShopTypes(listOf(all["dusuris_star_shop"]!!)) }

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            objTypes = objs, npcTypes = NpcTypes.of(before, after, dusuri),
            npcSpawns = listOf(NpcSpawn(dusuri, 3221, 3220, source = "test")), shopTypes = shops,
            varTypes = VarTypes(
                6_000,
                mapOf(
                    ShopScreen.VARBIT_QUANTITY to VarbitType(5_100, 0, 2),
                    ShootingStars.VARBIT_MINED to VarbitType(STAR_TRANSMIT, 0, 0),
                    Dusuri.VARBIT_MET to VarbitType(STAR_TRANSMIT, 1, 1),
                ),
            ),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.held(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.awaitHeld(id: Int, count: Int) =
        await("the backpack to hold $count of $id") { if (held(id) == count) Unit else null }

    private fun HeadlessClient.slotOf(id: Int) = inventory(Inventory.INV).indexOfFirst { it?.id == id }

    @Test
    fun `Dusuri gains Trade after the introduction, and sells for stardust at the Wiki's prices`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                val npc = client.await("Dusuri in view") { client.npcs().firstOrNull { it.id == STAR_TRADER } }
                // Before meeting him, Trade does nothing: he shows only Talk-to.
                client.npcOp(npc.index, TRADE)
                client.awaitTicks(3)
                assertEquals(false, ShopScreen.MAIN in client.openInterfaces())
                client.npcOp(npc.index, TALK_TO)
                for (key in listOf("hello", "what_do_you_do", "i_specialise", "where_from", "in_stars", "shooting_stars", "they_fall", "happy_to_trade", "bear_in_mind")) {
                    client.awaitDialogue(key) { it is osrsps.testclient.DialogueView.Chat && it.text == "[dusuri.$key]" }
                    client.continueDialogue()
                }
                client.awaitDialogue("good_luck") { it is osrsps.testclient.DialogueView.Chat && it.text == "[dusuri.good_luck]" }
                client.continueDialogue()

                client.command("item ${ShootingStars.STARDUST} 2500")
                client.awaitHeld(ShootingStars.STARDUST, 2500)
                client.npcOp(npc.index, TRADE)
                client.await("the shop") { if (ShopScreen.MAIN in client.openInterfaces()) Unit else null }
                client.await("the stock") { client.inventory(SHOP_INV).firstOrNull()?.takeIf { it.id == RING && it.count == 50 } }
                assertEquals(listOf(RING, FRAGMENT, Dusuri.BAG_FULL_OF_GEMS, Dusuri.SOFT_CLAY_PACK), client.inventory(SHOP_INV).map { it!!.id })
                client.ifButton(ShopScreen.MAIN, ShopScreen.MAIN_ITEMS, 2, 1, RING)
                client.awaitHeld(RING, 1)
                client.awaitHeld(ShootingStars.STARDUST, 500)
                client.ifButton(ShopScreen.MAIN, ShopScreen.MAIN_ITEMS, 2, 3, Dusuri.BAG_FULL_OF_GEMS)
                client.awaitHeld(Dusuri.BAG_FULL_OF_GEMS, 1)
                client.awaitHeld(ShootingStars.STARDUST, 200)
                client.ifButton(ShopScreen.MAIN, ShopScreen.MAIN_ITEMS, 2, 4, Dusuri.SOFT_CLAY_PACK)
                client.awaitHeld(Dusuri.SOFT_CLAY_PACK, 1)
                client.awaitHeld(ShootingStars.STARDUST, 50)
                // The ring back at 80% of 2000.
                client.ifButton(ShopScreen.SIDE, ShopScreen.SIDE_ITEMS, 2, client.slotOf(RING), RING)
                client.awaitHeld(RING, 0)
                client.awaitHeld(ShootingStars.STARDUST, 1650)
            }
        }
    }

    @Test
    fun `a bag full of gems opens to 40 noted uncut gems and a soft clay pack to 100 noted soft clay`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item ${Dusuri.BAG_FULL_OF_GEMS}")
                client.command("item ${Dusuri.SOFT_CLAY_PACK}")
                client.awaitHeld(Dusuri.SOFT_CLAY_PACK, 1)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, IOP1, client.slotOf(Dusuri.BAG_FULL_OF_GEMS), Dusuri.BAG_FULL_OF_GEMS)
                client.awaitHeld(Dusuri.BAG_FULL_OF_GEMS, 0)
                val notes = (Dusuri.GEM_TABLE.map { it.obj } + Dusuri.UNCUT_ONYX).map { it + 1 }
                val gems = client.await("40 noted gems") { notes.sumOf { client.held(it) }.takeIf { it == 40 } }
                assertEquals(40, gems)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, IOP1, client.slotOf(Dusuri.SOFT_CLAY_PACK), Dusuri.SOFT_CLAY_PACK)
                client.awaitHeld(Dusuri.SOFT_CLAY + 1, 100)
                client.awaitHeld(Dusuri.SOFT_CLAY_PACK, 0)
            }
        }
    }

    @Test
    fun `a pack with no room for what it holds stays shut`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                // 23 star fragments and the bag: four free slots and the bag's own, one short of the six gems it could hold,
                // so it stays shut whatever it would have rolled.
                client.command("item $FRAGMENT 23")
                client.command("item ${Dusuri.BAG_FULL_OF_GEMS}")
                client.awaitHeld(Dusuri.BAG_FULL_OF_GEMS, 1)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, IOP1, client.slotOf(Dusuri.BAG_FULL_OF_GEMS), Dusuri.BAG_FULL_OF_GEMS)
                client.await("the refusal") { client.messages().firstOrNull { it.text == "[dusuri.pack_space]" } }
                assertEquals(1, client.held(Dusuri.BAG_FULL_OF_GEMS))
            }
        }
    }

    private companion object {
        const val STAR_TRADER = 10633
        const val RING = 25539
        const val FRAGMENT = 25547
        const val SHOP_INV = 1016
        const val TALK_TO = 1
        const val TRADE = 3
        const val IOP1 = 2
        const val STAR_TRANSMIT = 2924
    }
}
