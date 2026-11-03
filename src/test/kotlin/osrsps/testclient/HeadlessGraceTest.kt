package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.RoguesDen
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.shop.ShopLines
import osrsps.game.shop.ShopScreen
import osrsps.game.shop.ShopTypes
import osrsps.game.skill.Agility
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Agility step 3 through the headless client: Grace's shop from the real shops.json entry, paid in marks of grace and
 * buying the graceful pieces back at 80%, and the way down to her in the Rogues' Den and back up.
 */
class HeadlessGraceTest {
    private val mark = ObjType(Agility.MARK_OF_GRACE, "Mark of grace", stackable = true, cost = 1)
    private val pieces = listOf(
        ObjType(HOOD, "Graceful hood", cost = 35), ObjType(TOP, "Graceful top", cost = 55), ObjType(LEGS, "Graceful legs", cost = 60),
        ObjType(GLOVES, "Graceful gloves", cost = 30), ObjType(BOOTS, "Graceful boots", cost = 40), ObjType(CAPE, "Graceful cape", cost = 40),
        ObjType(AMYLASE_PACK, "Amylase pack", cost = 10),
    )
    private val objs = ObjTypes.of(ObjType(COINS, "Coins", stackable = true, tradeable = true), mark, *pieces.toTypedArray())
    private val grace = NpcType(GRACE, "Grace", ops = listOf("Talk-to", null, "Trade", "View Laps", "Toggle Counter"), internalName = "rooftops_grace")
    private val shops = ShopTypes.parse(
        ShopTypes::class.java.getResource(ShopTypes.RESOURCE)!!.readText(), NpcTypes.of(grace), objs,
    ).let { all -> ShopTypes(listOf(all["graces_graceful_clothing"]!!)) }

    private fun loc(id: Int, name: String, op: String) = LocType(id, name, listOf(op, null, null, null, null), LocCollisionType(width = 1, length = 1))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                markLoaded(2905 shr 6, 3537 shr 6)
                markLoaded(3061 shr 6, 4984 shr 6)
            },
            objTypes = objs, npcSpawns = listOf(NpcSpawn(grace, 3221, 3220, source = "test")), shopTypes = shops,
            varTypes = VarTypes(6_000, mapOf(ShopScreen.VARBIT_QUANTITY to VarbitType(5_100, 0, 2))),
            locTypes = LocTypes(
                listOf(loc(RoguesDen.TRAPDOOR, "Trapdoor", "Enter"), loc(RoguesDen.DOOR, "Door", "Open"), loc(RoguesDen.PASSAGEWAY, "Passageway", "Enter"))
                    .associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(RoguesDen.TRAPDOOR, LocShape.CENTREPIECE_STRAIGHT, 0, 2905, 3537, 0))
                add(ZoneLoc(RoguesDen.DOOR, LocShape.CENTREPIECE_STRAIGHT, 0, 3061, 4984, 1))
                add(ZoneLoc(RoguesDen.PASSAGEWAY, LocShape.CENTREPIECE_STRAIGHT, 0, 3061, 4986, 1))
                // Copies elsewhere, as the den's maze has the door's type: they lead nowhere.
                for ((id, x) in listOf(RoguesDen.TRAPDOOR to 3224, RoguesDen.PASSAGEWAY to 3226, RoguesDen.DOOR to 3228)) {
                    add(ZoneLoc(id, LocShape.CENTREPIECE_STRAIGHT, 0, x, 3218, 0))
                }
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.held(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.awaitHeld(id: Int, count: Int) =
        await("the backpack to hold $count of $id") { if (held(id) == count) Unit else null }

    private fun HeadlessClient.slotOf(id: Int) = inventory(Inventory.INV).indexOfFirst { it?.id == id }

    private fun HeadlessClient.at(x: Int, z: Int, level: Int, what: String) =
        await(what) { position?.takeIf { it.x == x && it.z == z && it.level == level } }

    @Test
    fun `Grace sells the graceful pieces for marks of grace and buys them back at 80 percent`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item ${Agility.MARK_OF_GRACE} 100")
                client.awaitHeld(Agility.MARK_OF_GRACE, 100)
                val npc = client.await("Grace in view") { client.npcs().firstOrNull { it.id == GRACE } }
                client.npcOp(npc.index, TRADE)
                client.await("the shop") { if (ShopScreen.MAIN in client.openInterfaces()) Unit else null }
                client.await("the stock") { client.inventory(INV).firstOrNull()?.takeIf { it.id == HOOD && it.count == 100 } }
                assertEquals(listOf(HOOD, TOP, LEGS, GLOVES, BOOTS, CAPE, AMYLASE_PACK), client.inventory(INV).map { it!!.id })

                val words = DialogueText.NONE
                client.ifButton(ShopScreen.MAIN, ShopScreen.MAIN_ITEMS, 6, 1, HOOD)
                client.awaitMessage("the hood's price") {
                    it == words[ShopLines.COSTS_IN, "item" to "Graceful hood", "price" to "35", "currency" to "Mark of grace"]
                }
                client.ifButton(ShopScreen.MAIN, ShopScreen.MAIN_ITEMS, 2, 1, HOOD)
                client.awaitHeld(HOOD, 1)
                client.awaitHeld(Agility.MARK_OF_GRACE, 65)

                // Back at 80% of 35; the hood can't be traded, but she buys what she sells.
                client.ifButton(ShopScreen.SIDE, ShopScreen.SIDE_ITEMS, 1, client.slotOf(HOOD), HOOD)
                client.awaitMessage("the hood's buy-back") {
                    it == words[ShopLines.BUYS_FOR_IN, "item" to "Graceful hood", "price" to "28", "currency" to "Mark of grace"]
                }
                client.ifButton(ShopScreen.SIDE, ShopScreen.SIDE_ITEMS, 2, client.slotOf(HOOD), HOOD)
                client.awaitHeld(HOOD, 0)
                client.awaitHeld(Agility.MARK_OF_GRACE, 93)

                // The marks themselves don't sell, and 93 buy one pair of legs at 60 but not two.
                client.ifButton(ShopScreen.SIDE, ShopScreen.SIDE_ITEMS, 2, client.slotOf(Agility.MARK_OF_GRACE), Agility.MARK_OF_GRACE)
                client.awaitMessage("no selling marks") { it == words[ShopLines.CANT_SELL_CURRENCY, "currency" to "Mark of grace"] }
                client.ifButton(ShopScreen.MAIN, ShopScreen.MAIN_ITEMS, 3, 3, LEGS)
                client.awaitMessage("not enough marks") { it == words[ShopLines.NOT_ENOUGH_CURRENCY, "currency" to "Mark of grace"] }
                client.awaitHeld(LEGS, 1)
                client.awaitHeld(Agility.MARK_OF_GRACE, 33)
                assertEquals(0, client.held(COINS))
            }
        }
    }

    @Test
    fun `the inn's trapdoor goes down to the den, its door lets the player through, and the passageway goes back up`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele 2907 3537 0")
                client.at(2907, 3537, 0, "in the inn")
                client.locOp(RoguesDen.TRAPDOOR, 2905, 3537, 1)
                client.at(RoguesDen.ARRIVAL.x, RoguesDen.ARRIVAL.z, RoguesDen.ARRIVAL.level, "down in the den")
                client.locOp(RoguesDen.DOOR, 3061, 4984, 1)
                client.at(RoguesDen.DOOR_ROOM.x, RoguesDen.DOOR_ROOM.z, 1, "through the door into the room")
                client.locOp(RoguesDen.DOOR, 3061, 4984, 1)
                client.at(RoguesDen.DOOR_ALCOVE.x, RoguesDen.DOOR_ALCOVE.z, 1, "back in the alcove")
                client.locOp(RoguesDen.PASSAGEWAY, 3061, 4986, 1)
                client.at(RoguesDen.INN.x, RoguesDen.INN.z, RoguesDen.INN.level, "back up in the inn")
            }
        }
    }

    @Test
    fun `the den's trapdoor, passageway and door types anywhere else are nothing interesting`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.at(3222, 3218, 0, "on the spawn")
                for ((id, x) in listOf(RoguesDen.TRAPDOOR to 3224, RoguesDen.PASSAGEWAY to 3226, RoguesDen.DOOR to 3228)) {
                    val seen = client.messages().count { it.text == "[game.nothing_interesting]" }
                    client.locOp(id, x, 3218, 1)
                    client.await("nothing interesting from $id") { client.messages().count { it.text == "[game.nothing_interesting]" }.takeIf { it > seen } }
                    client.awaitTicks(2)
                    val at = client.position!!
                    assertEquals(0, at.level, "still on the surface after $id")
                    assertTrue(at.z in 3216..3220, "still by the copies after $id, at ${at.x}, ${at.z}")
                }
            }
        }
    }

    private companion object {
        const val COINS = 995
        const val GRACE = 5919
        const val INV = 279
        const val TRADE = 3
        const val HOOD = 11_850
        const val CAPE = 11_852
        const val TOP = 11_854
        const val LEGS = 11_856
        const val GLOVES = 11_858
        const val BOOTS = 11_860
        const val AMYLASE_PACK = 12_641
    }
}
