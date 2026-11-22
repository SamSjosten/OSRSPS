package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.MusaPoint
import osrsps.game.content.MusaPointLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
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
 * Karamja step K1.2 through the headless client: Luthas's banana job (taken, a banana packed, the rest by Fill, a full
 * crate refusing more, his pay of 30 coins and the job taken again), a banana tree giving one banana a Pick and showing
 * one fewer until it stands empty, and Zembo's and the general store keeper's talk. test-runner/examples/musa-point.json
 * does the same on the real map.
 */
class HeadlessMusaPointTest {
    private val trade = listOf("Talk-to", null, "Trade", null, null)
    private val luthas = NpcType(MusaPoint.LUTHAS, "Luthas", ops = listOf("Talk-to", null, null, null, null), internalName = "luthas")
    private val zembo = NpcType(MusaPoint.ZEMBO, "Zembo", ops = trade, internalName = "zembo")
    private val keeper = NpcType(MusaPoint.SHOP_KEEPER, "Shop keeper", ops = trade, internalName = "generalshopkeeper7")

    private val banana = ObjType(MusaPoint.BANANA, "Banana", cost = 2, tradeable = true)
    private val beer = ObjType(BEER, "Beer", cost = 2, tradeable = true)
    private val objs = arrayOfNulls<ObjType>(MusaPoint.BANANA + 1).apply {
        this[MusaPoint.COINS] = ObjType(MusaPoint.COINS, "Coins", stackable = true)
        this[MusaPoint.BANANA] = banana
        this[BEER] = beer
    }

    private val shops = ShopTypes(
        listOf(
            ShopType("karamja_wines_spirits_and_beers", "Karamja Wines, Spirits, and Beers.", invId = ZEMBO_INV, size = 3, general = false, sellAt = 1000, buyAt = 700, changePer = 20, stock = listOf(ShopStock(beer, 3, 100)), npcs = listOf(zembo.id)),
        ),
    )

    private fun loc(id: Int, x: Int, z: Int) = ZoneLoc(id, LocShape.CENTREPIECE_STRAIGHT, 0, x, z, 0)

    private val crate = loc(MusaPoint.CRATE, CRATE_X, CRATE_Z)
    private val tree = loc(MusaPoint.TREES.first, TREE_X, TREE_Z)

    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = listOf(*ops) + List(5 - ops.size) { null }, collision = LocCollisionType(width = 1, length = 1))

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply { markLoaded(CRATE_X shr 6, CRATE_Z shr 6) },
            random = GameRandom(7), objTypes = ObjTypes(objs), npcTypes = NpcTypes.of(luthas, zembo, keeper),
            npcSpawns = listOf(
                NpcSpawn(luthas, 2939, 3154, wander = 0, source = "test"),
                NpcSpawn(zembo, 2925, 3143, wander = 0, source = "test"),
                NpcSpawn(keeper, 2930, 3143, wander = 0, source = "test"),
            ),
            locTypes = LocTypes(
                mapOf(MusaPoint.CRATE to type(MusaPoint.CRATE, "Crate", "Search", "Fill")) +
                    MusaPoint.TREES.associateWith { type(it, "Banana tree", "Pick") } +
                    (MusaPoint.TREE_EMPTY to type(MusaPoint.TREE_EMPTY, "Banana tree", "Search")),
            ),
            staticLocs = StaticLocs().apply { add(crate); add(tree) },
            shopTypes = shops,
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.count(id: Int): Long = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count.toLong() }

    private fun HeadlessClient.sees(vararg lines: Line) {
        for (line in lines) {
            awaitDialogue(line.key) { it is DialogueView.Chat && it.text == DialogueText.NONE[line] }
            continueDialogue()
        }
    }

    private fun HeadlessClient.box(line: Line) {
        awaitDialogue(line.key) { it is DialogueView.Message && it.text == DialogueText.NONE[line] }
        continueDialogue()
    }

    private fun HeadlessClient.choose(option: Int) {
        awaitDialogue("a menu") { it is DialogueView.Menu }
        chooseOption(option)
    }

    private fun HeadlessClient.talkTo(id: Int) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, MusaPoint.TALK_TO)
    }

    private fun HeadlessClient.seen(text: String) = messages().count { it.text == text }

    @Test
    fun `Luthas pays 30 coins for a crate filled with 10 bananas`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ada").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.tele(2939, 3153)
                with(MusaPointLines) {
                    client.talkTo(MusaPoint.LUTHAS)
                    client.sees(LUTHAS_HELLO)
                    client.choose(1)
                    client.sees(EMPLOYMENT, CRATE_READY, DEMAND, PAY_30)
                    client.await("the job taken") { if (client.dialogueView == null) Unit else null }
                    val player = server.world.playerNamed("ada")!!
                    assertTrue(MusaPoint.accepted(player), "the job is taken")

                    client.talkTo(MusaPoint.LUTHAS)
                    client.sees(TASK_DONE_YET)
                    client.choose(2)
                    client.sees(NOT_FULL_SAY, COME_BACK)

                    client.command("item ${MusaPoint.BANANA} 11")
                    client.await("the bananas") { if (client.count(MusaPoint.BANANA) == 11L) Unit else null }
                    client.tele(CRATE_X, CRATE_Z - 1)
                    val slot = client.inventory(BACKPACK).indexOfFirst { it?.id == MusaPoint.BANANA }
                    client.locTarget(MusaPoint.CRATE, CRATE_X, CRATE_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slot, MusaPoint.BANANA)
                    client.box(PACK_ONE)
                    assertEquals(10L, client.count(MusaPoint.BANANA))
                    client.locOp(MusaPoint.CRATE, CRATE_X, CRATE_Z, MusaPoint.SEARCH)
                    client.awaitMessage("one banana inside") { it == "[musa_point.crate_has count=1 s=]" }

                    client.locOp(MusaPoint.CRATE, CRATE_X, CRATE_Z, MusaPoint.FILL)
                    client.box(PACK_ALL)
                    client.await("one banana left over") { if (client.count(MusaPoint.BANANA) == 1L) Unit else null }
                    client.locOp(MusaPoint.CRATE, CRATE_X, CRATE_Z, MusaPoint.FILL)
                    client.awaitMessage("the full crate") { it == "[musa_point.crate_full]" }
                    assertEquals(1L, client.count(MusaPoint.BANANA), "a full crate takes no more")
                    client.locOp(MusaPoint.CRATE, CRATE_X, CRATE_Z, MusaPoint.SEARCH)
                    client.box(CRATE_FULL_SEARCH)

                    client.tele(2939, 3153)
                    client.talkTo(MusaPoint.LUTHAS)
                    client.sees(FILLED, WELL_DONE)
                    client.choose(1)
                    client.sees(ANOTHER_CRATE, YES_CERTAINLY, NEW_CRATE)
                    client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }
                    assertEquals(30L, client.count(MusaPoint.COINS), "30 coins for the crate")
                    assertEquals(0, MusaPoint.inCrate(player), "an empty crate in its place")
                    assertTrue(MusaPoint.accepted(player), "the job taken again")
                }
            }
        }
    }

    @Test
    fun `Luthas's 30 coins go on the ground when the backpack is full`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("cal").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.commandReply("varp banana_crate 26") { it.startsWith("Varp banana_crate") }
                client.command("item $BEER 28")
                client.await("a full backpack") { if (client.count(BEER) == 28L) Unit else null }
                client.tele(2939, 3153)
                with(MusaPointLines) {
                    client.talkTo(MusaPoint.LUTHAS)
                    client.sees(FILLED, WELL_DONE)
                    client.choose(2)
                }
                client.await("the coins on the ground") {
                    client.groundObjs().firstOrNull { it.id == MusaPoint.COINS && it.count == MusaPoint.PAY }
                }
                assertEquals(0, MusaPoint.inCrate(server.world.playerNamed("cal")!!))
            }
        }
    }

    @Test
    fun `a banana tree gives a banana a pick, five in all, then stands empty`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ben").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.tele(TREE_X, TREE_Z - 1)
                for ((picked, id) in MusaPoint.TREES.withIndex()) {
                    client.locOp(id, TREE_X, TREE_Z, MusaPoint.PICK)
                    client.await("banana ${picked + 1}") { if (client.count(MusaPoint.BANANA) == picked + 1L) Unit else null }
                    client.await("the tree showing ${id + 1}") { client.locChanges().firstOrNull { it.id == id + 1 && it.coord == Coord(TREE_X, TREE_Z, 0) } }
                }
                assertEquals(5, client.seen("[musa_point.banana_picked]"))
                client.locOp(MusaPoint.TREE_EMPTY, TREE_X, TREE_Z, MusaPoint.TREE_SEARCH)
                client.awaitMessage("the empty tree") { it == "[musa_point.tree_empty]" }
                assertEquals(5L, client.count(MusaPoint.BANANA))
            }
        }
    }

    @Test
    fun `Zembo's yes opens his shop, and the store keeper's no thanks doesn't`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("cal").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.tele(2927, 3144)
                client.talkTo(MusaPoint.SHOP_KEEPER)
                client.awaitDialogue("musa_shop_keeper.help") { it is DialogueView.Chat && it.text == "[musa_shop_keeper.help]" }
                client.continueDialogue()
                client.choose(2)
                client.awaitDialogue("musa_shop_keeper.no_thanks") { it is DialogueView.Chat && it.text == "[musa_shop_keeper.no_thanks]" }
                client.continueDialogue()
                client.await("the dialogue to close") { if (client.dialogueView == null) Unit else null }
                assertTrue(ShopScreen.MAIN !in client.openInterfaces(), "no shop from no thanks")

                client.talkTo(MusaPoint.ZEMBO)
                client.sees(MusaPointLines.ZEMBO_HELLO)
                client.choose(1)
                client.await("Zembo's shop") { if (ShopScreen.MAIN in client.openInterfaces()) Unit else null }
                client.await("its stock") { client.inventory(ZEMBO_INV).takeIf { it == listOf(InvObj(BEER, 3)) } }
            }
        }
    }

    private companion object {
        const val CRATE_X = 2939
        const val CRATE_Z = 3149
        const val TREE_X = 2930
        const val TREE_Z = 3155
        const val BEER = 1917
        const val ZEMBO_INV = 29
        const val BACKPACK = 93
    }
}
