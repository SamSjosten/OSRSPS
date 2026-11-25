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
import osrsps.game.content.BrimhavenTown
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.shop.ShopScreen
import osrsps.game.shop.ShopStock
import osrsps.game.shop.ShopType
import osrsps.game.shop.ShopTypes
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Karamja step K3.2 through the headless client: Davon's talk opening his amulet store, the Dead Man's Chest's
 * bartender refusing grog without 3 coins and selling rum for 27, Alfonse's talk on karambwan, and the ropeswings to
 * Moss Giant Island and back, refused below 10 Agility. test-runner/examples/brimhaven-town.json does the same on the
 * real map.
 */
class HeadlessBrimhavenTownTest {
    private val ops = listOf("Talk-to", null, "Trade", null, null)
    private val davon = NpcType(BrimhavenTown.DAVON, "Davon", ops = ops, internalName = "davon")
    private val bartender = NpcType(BrimhavenTown.BARTENDER, "Bartender", ops = listOf("Talk-to", null, null, null, null), internalName = "deadmans_bartender")
    private val alfonse = NpcType(BrimhavenTown.ALFONSE, "Alfonse the waiter", ops = ops, internalName = "alfonse_the_waiter")
    private val coins = ObjType(ShopScreen.COINS, "Coins", stackable = true)
    private val amulet = ObjType(AMULET_OF_MAGIC, "Amulet of magic", cost = 900, tradeable = true)
    private val drinks = listOf(ObjType(BrimhavenTown.GROG, "Grog"), ObjType(BrimhavenTown.RUM, "Karamjan rum"))
    private val store = ShopType(
        "davons_amulet_store", "Davon's Amulet Store.", invId = 46, size = 5, general = false,
        sellAt = 1200, buyAt = 900, changePer = 20, stock = listOf(ShopStock(amulet, 0, 200)), npcs = listOf(BrimhavenTown.DAVON),
    )
    private val ropes = listOf(ZoneLoc(BRIMHAVEN_ROPE, 10, 1, 2705, 3209, 0), ZoneLoc(ISLAND_ROPE, 10, 3, 2703, 3205, 0))

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(2803 shr 6, 3152 shr 6)
                markLoaded(2793 shr 6, 3188 shr 6)
                markLoaded(2705 shr 6, 3209 shr 6)
            },
            objTypes = ObjTypes.of(coins, amulet, *drinks.toTypedArray()), npcTypes = NpcTypes.of(davon, bartender, alfonse),
            npcSpawns = listOf(
                NpcSpawn(davon, 2803, 3152, wander = 0, source = "test"),
                NpcSpawn(bartender, 2797, 3155, wander = 0, source = "test"),
                NpcSpawn(alfonse, 2793, 3188, wander = 0, source = "test"),
            ),
            locTypes = LocTypes(ropes.associate { it.id to LocType(it.id, "Ropeswing", listOf("Swing-on", null, null, null, null), LocCollisionType(width = 1, length = 1, interactType = 0)) }),
            staticLocs = StaticLocs().apply { ropes.forEach(::add) },
            shopTypes = ShopTypes(listOf(store)), varTypes = VARS, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.held(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count.toLong() }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("at $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.chat(key: String) =
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }.also { continueDialogue() }

    private fun HeadlessClient.menu(option: Int) {
        awaitDialogue("a menu") { it is DialogueView.Menu }
        chooseOption(option)
    }

    private fun HeadlessClient.talk(id: Int) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, BrimhavenTown.TALK_TO)
    }

    @Test
    fun `Davon's talk opens his store, and the bartender sells rum for 27 but no grog without 3 coins`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ada").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.tele(2803, 3153)
                client.talk(BrimhavenTown.DAVON)
                client.chat("davon.pssst")
                client.menu(1)
                client.await("the store") { if (ShopScreen.MAIN in client.openInterfaces()) Unit else null }
                client.command("tele 2797 3156 0")
                client.await("the store to close") { if (ShopScreen.MAIN !in client.openInterfaces()) Unit else null }

                client.talk(BrimhavenTown.BARTENDER)
                client.chat("deadmans_bartender.greeting")
                client.menu(2)
                client.chat("deadmans_bartender.grog")
                client.chat("deadmans_bartender.three_coins")
                client.chat("deadmans_bartender.no_money")
                client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }
                client.command("item ${ShopScreen.COINS} 30")
                client.await("the coins") { if (client.held(ShopScreen.COINS) == 30L) Unit else null }
                client.talk(BrimhavenTown.BARTENDER)
                client.chat("deadmans_bartender.greeting")
                client.menu(3)
                client.chat("deadmans_bartender.rum")
                client.chat("deadmans_bartender.twenty_seven")
                client.await("the rum") { if (client.held(BrimhavenTown.RUM) == 1L) Unit else null }
                assertEquals(3L, client.held(ShopScreen.COINS), "27 coins paid")
            }
        }
    }

    @Test
    fun `Alfonse tells where the karambwan come from`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ben").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.tele(2793, 3187)
                client.talk(BrimhavenTown.ALFONSE)
                client.chat("alfonse.welcome")
                client.menu(3)
                for (key in listOf("karambwan", "lubufu")) client.chat("alfonse.$key")
                client.menu(2)
                for (key in listOf("monopoly", "secret", "thanks")) client.chat("alfonse.$key")
                client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }
            }
        }
    }

    @Test
    fun `the ropeswings cross to Moss Giant Island and back from 10 Agility`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("cal").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.tele(2709, 3209)
                client.locOp(BRIMHAVEN_ROPE, 2705, 3209, 1)
                client.await("the level message") { client.messages().firstOrNull { it.text == "[agility.brimhaven_ropeswing_level level=10]" } }
                client.command("setlevel agility 10")
                val before = client.await("level 10") { client.stat(AGILITY)?.takeIf { it.level == 10 } }.xp
                client.tele(2709, 3209)
                client.locOp(BRIMHAVEN_ROPE, 2705, 3209, 1)
                client.await("on the island") { client.position?.takeIf { it == Coord(2704, 3209, 0) } }
                client.awaitTicks(2)
                client.tele(2704, 3205)
                client.locOp(ISLAND_ROPE, 2703, 3205, 1)
                client.await("back in Brimhaven") { client.position?.takeIf { it == Coord(2709, 3205, 0) } }
                assertEquals(before + 6, client.await("6 XP") { client.stat(AGILITY)?.takeIf { it.xp >= before + 6 } }.xp)
            }
        }
    }

    private companion object {
        const val AMULET_OF_MAGIC = 1727
        const val BRIMHAVEN_ROPE = 23568
        const val ISLAND_ROPE = 23569
        const val AGILITY = 16

        /** shop_quantity sits alone on a spare varp here, as in HeadlessShopTest. */
        val VARS = VarTypes(6_000, mapOf(ShopScreen.VARBIT_QUANTITY to VarbitType(5_100, 0, 2)))
    }
}
