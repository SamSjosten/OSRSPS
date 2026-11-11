package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
import osrsps.game.content.LumberYard
import osrsps.game.dialogue.Chatbox
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
import osrsps.map.LocShape

/**
 * Misthalin piece 7 step 6 through the headless client: the Sawmill operator's talk, his Make-X box of planks with its
 * fees and refusals, his shop on Trade (his op 4), the Lumber Yard's crates and barrels, the broken fence, the Earth
 * Altar's ruins, Tolna as he shows after A Soul's Bane, his rift and its warning signs. The rl-client script
 * test-runner/examples/lumber-yard.json does the same on the real map.
 */
class HeadlessLumberYardTest {
    private val operator = NpcType(
        LumberYard.SAWMILL_OPERATOR, "Sawmill operator", ops = listOf("Talk-to", null, "Buy-plank", "Trade", null), internalName = "poh_sawmill_opp",
    )
    private val mewing = NpcType(LumberYard.MEWING_CRATE, "Crate", ops = listOf("Search", null, null, null, null), internalName = "kittens_mew")
    private val tolnaShown = NpcType(LumberYard.TOLNA_SHOWN, "Tolna", ops = listOf("Talk-to", null, null, null, null), internalName = "soulbane_tolna_top")
    private val tolna = NpcType(LumberYard.TOLNA, multiVarbit = TOLNA_PRES, multiNpcs = intArrayOf(-1, LumberYard.TOLNA_SHOWN, -1), internalName = "soulbane_tolna_multi")

    private fun type(id: Int, name: String, op: String, multiloc: Boolean = false) =
        LocType(id, name, ops = listOf(op, null, null, null, null), collision = LocCollisionType(width = 1, length = 1, interactType = 0), multiloc = multiloc)

    private val crate = ZoneLoc(LumberYard.CRATE, LocShape.CENTREPIECE_STRAIGHT, 0, 3226, 3222, 0)
    private val barrel = ZoneLoc(LumberYard.BARREL, LocShape.CENTREPIECE_STRAIGHT, 0, 3228, 3222, 0)
    private val ruins = ZoneLoc(LumberYard.EARTH_RUINS, LocShape.CENTREPIECE_STRAIGHT, 0, 3218, 3222, 0)
    private val rift = ZoneLoc(LumberYard.RIFTS.first(), LocShape.CENTREPIECE_STRAIGHT, 0, 3216, 3216, 0)
    private val sign = ZoneLoc(LumberYard.WARNING_SIGN, LocShape.CENTREPIECE_STRAIGHT, 0, 3218, 3216, 0)
    private val fence = ZoneLoc(FENCE, LocShape.CENTREPIECE_STRAIGHT, 0, 3308, 3492, 0)

    private val saw = ObjType(SAW, "Saw", cost = 13, tradeable = true)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(LumberYard.PLANKS.maxOf { maxOf(it.plank, it.log) } + 1)
        for (plank in LumberYard.PLANKS) {
            objs[plank.plank] = ObjType(plank.plank, "Plank ${plank.plank}", tradeable = true)
            objs[plank.log] = ObjType(plank.log, "Logs ${plank.log}", tradeable = true)
        }
        objs[LumberYard.COINS] = ObjType(LumberYard.COINS, "Coins", stackable = true)
        objs[LumberYard.EARTH_TALISMAN] = ObjType(LumberYard.EARTH_TALISMAN, "Earth talisman")
        objs[SAW] = saw
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                markLoaded(fence.x shr 6, fence.z shr 6)
            },
            objTypes = ObjTypes(objs),
            npcTypes = NpcTypes.of(operator, mewing, tolna, tolnaShown),
            npcSpawns = listOf(
                NpcSpawn(operator, 3222, 3220, source = "test"), NpcSpawn(mewing, 3224, 3216, source = "test"),
                NpcSpawn(tolna, 3220, 3214, source = "test"),
            ),
            shopTypes = ShopTypes(
                listOf(
                    ShopType(
                        "construction_supplies", "Construction supplies", invId = 395, size = 5, general = false, sellAt = 1300, buyAt = 500,
                        changePer = 0, stock = listOf(ShopStock(saw, 1000, 10)), npcs = listOf(operator.id),
                    ),
                ),
            ),
            locTypes = LocTypes(
                listOf(
                    type(LumberYard.CRATE, "Crate", "Search"),
                    type(LumberYard.BARREL, "Barrel", "Search"),
                    type(LumberYard.EARTH_RUINS, "Mysterious ruins", "Enter"),
                    type(LumberYard.RIFTS.first(), "Rift", "Enter"),
                    type(LumberYard.WARNING_SIGN, "Warning sign", "Read"),
                    type(FENCE, "Broken fence", "Climb-over"),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { listOf(crate, barrel, ruins, rift, sign, fence).forEach(::add) },
            varTypes = VarTypes(VARP_COUNT, mapOf(TOLNA_PRES to VarbitType(VARP_SOULBANE, 31, 31))),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.ready() = await("the login rebuild") { position }

    private fun HeadlessClient.npc(id: Int, op: Int) {
        val npc = await("NPC $id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, op)
    }

    private fun HeadlessClient.chats(vararg keys: String) {
        for (key in keys) {
            awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
            continueDialogue()
        }
    }

    private fun HeadlessClient.menu(vararg keys: String) {
        val menu = awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
        assertEquals(keys.map { "[$it]" }, menu.options)
    }

    private fun HeadlessClient.awaitClosed() = await("the dialogue to close") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.count(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.give(id: Int, count: Int) {
        val before = count(id)
        command("item $id $count")
        await("$count of $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.seen(text: String) = messages().count { it.text == text }

    private fun HeadlessClient.awaitSeen(text: String, times: Int = 1) = await("$text x$times") { if (seen(text) >= times) Unit else null }

    private fun HeadlessClient.boxes() = clientScripts().filter { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }

    /**
     * Waits for Make-X box number [box] (counting from 1 since login), checks it offers the seven planks, and picks plank
     * [index], [quantity] of it.
     */
    private fun HeadlessClient.choosePlank(box: Int, index: Int, quantity: Int) {
        val setup = await("Make-X box $box") { boxes().getOrNull(box - 1) }
        for (plank in LumberYard.PLANKS) assertTrue(plank.plank in setup.args, "the box shows ${plank.plank}: ${setup.args}")
        resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM + index, quantity)
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    @Test
    fun `the operator's talk makes planks for their fees, as many as the coins pay for, and says what each costs`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                client.give(OAK_LOGS, 3)
                client.give(LumberYard.COINS, 600)
                client.npc(LumberYard.SAWMILL_OPERATOR, LumberYard.TALK_TO)
                client.chats("sawmill_operator.hello")
                client.menu("sawmill_operator.make_planks", "sawmill_operator.supplies", "sawmill_operator.good")
                client.chooseOption(1)
                client.chats("sawmill_operator.make_planks")
                client.choosePlank(1, 1, 3)
                // 600 coins pay for two oak planks at 250 each.
                client.chats("sawmill_operator.cost cost=250")
                client.awaitClosed()
                assertEquals(2, client.count(OAK_PLANK))
                assertEquals(1, client.count(OAK_LOGS))
                assertEquals(100, client.count(LumberYard.COINS))

                client.npc(LumberYard.SAWMILL_OPERATOR, LumberYard.TALK_TO)
                client.chats("sawmill_operator.hello")
                client.menu("sawmill_operator.make_planks", "sawmill_operator.supplies", "sawmill_operator.good")
                client.chooseOption(3)
                client.chats("sawmill_operator.good", "sawmill_operator.come_back")
                client.awaitClosed()
            }
        }
    }

    @Test
    fun `Buy-plank opens the box at once, makes every plank chosen when the coins cover them, and refuses without the logs`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.give(LOGS, 5)
                client.give(LumberYard.COINS, 1000)
                client.npc(LumberYard.SAWMILL_OPERATOR, LumberYard.BUY_PLANK)
                // Asked for more than are held: the five logs held are made.
                client.choosePlank(1, 0, 10)
                client.await("five planks") { if (client.count(PLANK) == 5) Unit else null }
                assertEquals(0, client.count(LOGS))
                assertEquals(500, client.count(LumberYard.COINS))
                client.awaitClosed()

                client.npc(LumberYard.SAWMILL_OPERATOR, LumberYard.BUY_PLANK)
                client.choosePlank(2, 2, 1)
                client.chats("sawmill_operator.more_logs")
                client.awaitClosed()
                assertEquals(500, client.count(LumberYard.COINS))
            }
        }
    }

    @Test
    fun `Trade, his op 4, and the talk's second option open Construction supplies`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready()
                client.npc(LumberYard.SAWMILL_OPERATOR, TRADE)
                client.await("the shop") { if (ShopScreen.MAIN in client.openInterfaces()) Unit else null }
                client.tele(3222, 3218)
                client.await("the shop closed") { if (ShopScreen.MAIN !in client.openInterfaces()) Unit else null }

                client.npc(LumberYard.SAWMILL_OPERATOR, LumberYard.TALK_TO)
                client.chats("sawmill_operator.hello")
                client.menu("sawmill_operator.make_planks", "sawmill_operator.supplies", "sawmill_operator.good")
                client.chooseOption(2)
                client.chats("sawmill_operator.supplies", "sawmill_operator.of_course")
                client.await("the shop from the talk") { if (ShopScreen.MAIN in client.openInterfaces()) Unit else null }
            }
        }
    }

    @Test
    fun `the crates and barrels hold nothing, and the Earth Altar's ruins wait for Runecraft`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.ready()
                client.npc(LumberYard.MEWING_CRATE, LumberYard.SEARCH)
                client.awaitSeen("[search.crate_empty]")
                client.locOp(crate.id, crate.x, crate.z, LumberYard.SEARCH)
                client.awaitSeen("[search.crate_empty]", 2)
                client.locOp(barrel.id, barrel.x, barrel.z, LumberYard.SEARCH)
                client.awaitSeen("[game.nothing_interesting]")

                client.locOp(ruins.id, ruins.x, ruins.z, LumberYard.ENTER)
                client.awaitSeen(RUNECRAFT_CLOSED)
                client.give(LumberYard.EARTH_TALISMAN, 1)
                val slot = client.inventory(Inventory.INV).indexOfFirst { it?.id == LumberYard.EARTH_TALISMAN }
                client.locTarget(ruins.id, ruins.x, ruins.z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slot, LumberYard.EARTH_TALISMAN)
                client.awaitSeen(RUNECRAFT_CLOSED, 2)
                assertEquals(1, client.count(LumberYard.EARTH_TALISMAN))
            }
        }
    }

    @Test
    fun `Tolna shows as after A Soul's Bane and talks, his rift waits for its dungeon and the signs read`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.ready()
                assertEquals(1, (client.varp(VARP_SOULBANE) ushr 31) and 1, "Tolna shown at the top, as after the quest")
                client.npc(LumberYard.TOLNA, LumberYard.TALK_TO)
                client.chats("tolna.training", "tolna.rift", "tolna.not_many", "tolna.harder", "tolna.thanks")
                client.awaitClosed()

                client.locOp(rift.id, rift.x, rift.z, LumberYard.ENTER)
                client.awaitSeen("[tolna_rift.dungeon_later]")
                client.locOp(sign.id, sign.x, sign.z, LumberYard.READ)
                client.awaitDialogue("the sign") { it == DialogueView.Message("[tolna_rift.sign]") }
                client.continueDialogue()
                client.awaitClosed()
            }
        }
    }

    @Test
    fun `the broken fence is climbed both ways`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("frank").use { client ->
                client.ready()
                client.tele(3308, 3491)
                client.locOp(fence.id, fence.x, fence.z, 1)
                client.await("inside the yard") { client.position?.takeIf { it == Coord(3308, 3493, 0) } }
                client.locOp(fence.id, fence.x, fence.z, 1)
                client.await("back outside") { client.position?.takeIf { it == Coord(3308, 3491, 0) } }
            }
        }
    }

    private companion object {
        const val LOGS = 1511
        const val PLANK = 960
        const val OAK_LOGS = 1521
        const val OAK_PLANK = 8778
        const val SAW = 8794
        const val FENCE = 2618
        const val TRADE = 4
        const val TOLNA_PRES = 2025
        const val VARP_SOULBANE = 709
        const val VARP_COUNT = 5_000
        const val RUNECRAFT_CLOSED = "[lumbridge_swamp.runecraft_closed]"
    }
}
