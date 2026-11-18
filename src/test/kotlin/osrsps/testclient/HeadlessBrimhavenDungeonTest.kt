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
import osrsps.game.content.BrimhavenDungeon
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.shop.ShopScreen
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Karamja step K3.3 through the headless client: Saniboch refusing the shut entrance without 875 coins and taking them
 * through his talk or his Pay option, the entrance using up a one-visit fee and the exit leading back out, a vine chopped
 * through with an axe and the level and parted by hand on the way back, and the entrance pipe needing 22 Agility only
 * going deeper. test-runner/examples/brimhaven-dungeon.json does the same on the real map.
 */
class HeadlessBrimhavenDungeonTest {
    private val saniboch = NpcType(BrimhavenDungeon.SANIBOCH, "Saniboch", ops = listOf("Talk-to", null, "Pay", null, null), internalName = "tribesman_doorman")
    private val coins = ObjType(ShopScreen.COINS, "Coins", stackable = true)
    private val axe = ObjType(BRONZE_AXE, "Bronze axe")

    /** As the 242 map has them. */
    private val locs = listOf(
        ZoneLoc(BrimhavenDungeon.ENTRANCE, 10, 0, 2743, 3153, 0),
        ZoneLoc(BrimhavenDungeon.EXIT, 10, 0, 2714, 9564, 0),
        ZoneLoc(VINE, 10, 3, 2690, 9564, 0),
        ZoneLoc(PIPE, 10, 1, 2655, 9571, 0),
        ZoneLoc(PIPE_SOUTH, 10, 3, 2655, 9567, 0),
    )

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply { for (loc in locs) markLoaded(loc.x shr 6, loc.z shr 6) },
            objTypes = ObjTypes.of(coins, axe), npcTypes = NpcTypes.of(saniboch),
            npcSpawns = listOf(NpcSpawn(saniboch, 2745, 3152, wander = 0, source = "test")),
            locTypes = LocTypes(locs.associate { it.id to LocType(it.id, "Loc", listOf("Op", null, null, null, null), LocCollisionType(width = 1, length = 1, interactType = 0)) }),
            staticLocs = StaticLocs().apply { locs.forEach(::add) },
            varTypes = VARS, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.coins() = inventory(Inventory.INV).filterNotNull().filter { it.id == ShopScreen.COINS }.sumOf { it.count.toLong() }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("at $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.arrives(x: Int, z: Int) = await("at $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }

    private fun HeadlessClient.chat(key: String) =
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[saniboch.$key]" }.also { continueDialogue() }

    private fun HeadlessClient.box(key: String) =
        awaitDialogue(key) { it is DialogueView.Message && it.text == "[saniboch.$key]" }.also { continueDialogue() }

    private fun HeadlessClient.menu(option: Int): DialogueView.Menu {
        val menu = awaitDialogue("a menu") { it is DialogueView.Menu } as DialogueView.Menu
        chooseOption(option)
        return menu
    }

    private fun HeadlessClient.closed() = await("the dialogue to close") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.message(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.ready() {
        await("the login rebuild") { position }
        commandReply("keys on") { it == "Dialogue shows keys" }
    }

    private fun HeadlessClient.sanibochIndex() = await("Saniboch in view") { npcs().firstOrNull { it.id == BrimhavenDungeon.SANIBOCH } }.index

    @Test
    fun `the shut entrance sends to Saniboch, whose talk takes 875 coins for one visit, and the exit leads out`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ada").use { client ->
                client.ready()
                client.tele(2744, 3152)
                client.locOp(BrimhavenDungeon.ENTRANCE, 2743, 3153, BrimhavenDungeon.ENTER)
                client.chat("riff_raff")
                client.closed()
                client.command("item ${ShopScreen.COINS} 1000")
                client.await("the coins") { if (client.coins() == 1000L) Unit else null }
                client.npcOp(client.sanibochIndex(), BrimhavenDungeon.TALK_TO)
                client.chat("greeting")
                client.menu(1)
                client.chat("can_i_go")
                client.chat("charge")
                assertEquals(4, client.menu(1).options.size)
                client.chat("heres_875")
                client.box("give_875")
                client.chat("many_thanks")
                client.closed()
                assertEquals(125L, client.coins(), "the 875-coin fee")
                client.await("the paid varbit") { if (client.varp(DOOR_VARP) == BrimhavenDungeon.PAID_ONCE) Unit else null }
                client.locOp(BrimhavenDungeon.ENTRANCE, 2743, 3153, BrimhavenDungeon.ENTER)
                client.arrives(BrimhavenDungeon.INSIDE.x, BrimhavenDungeon.INSIDE.z)
                assertEquals(0, client.varp(DOOR_VARP), "the visit uses up the fee")
                client.locOp(BrimhavenDungeon.EXIT, 2714, 9564, BrimhavenDungeon.LEAVE)
                client.arrives(BrimhavenDungeon.OUTSIDE.x, BrimhavenDungeon.OUTSIDE.z)
                client.locOp(BrimhavenDungeon.ENTRANCE, 2743, 3153, BrimhavenDungeon.ENTER)
                client.chat("riff_raff")
            }
        }
    }

    @Test
    fun `Saniboch's Pay option asks once for 875 coins when the player hasn't a million`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ben").use { client ->
                client.ready()
                client.command("item ${ShopScreen.COINS} 875")
                client.await("the coins") { if (client.coins() == 875L) Unit else null }
                client.tele(2744, 3152)
                client.npcOp(client.sanibochIndex(), BrimhavenDungeon.PAY)
                assertEquals("[saniboch.pay_title]", client.menu(1).title)
                client.box("give_875")
                client.chat("many_thanks")
                client.closed()
                assertEquals(0L, client.coins())
                client.npcOp(client.sanibochIndex(), BrimhavenDungeon.PAY)
                client.chat("lovely_coins")
                client.menu(1)
                client.chat("okay_thanks")
                client.closed()
                client.locOp(BrimhavenDungeon.ENTRANCE, 2743, 3153, BrimhavenDungeon.ENTER)
                client.arrives(BrimhavenDungeon.INSIDE.x, BrimhavenDungeon.INSIDE.z)
            }
        }
    }

    @Test
    fun `a vine wants an axe and the level going deeper but parts by hand, and the entrance pipe wants 22 Agility only going deeper`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("cal").use { client ->
                client.ready()
                client.tele(2691, 9564)
                client.locOp(VINE, 2690, 9564, BrimhavenDungeon.CHOP)
                client.message("[woodcutting.level level=10]")
                client.command("setlevel woodcutting 10")
                client.await("level 10") { client.stat(WOODCUTTING)?.takeIf { it.level == 10 } }
                client.locOp(VINE, 2690, 9564, BrimhavenDungeon.CHOP)
                client.message("[woodcutting.no_axe]")
                client.command("item $BRONZE_AXE 1")
                client.await("the axe") { client.inventory(Inventory.INV).filterNotNull().firstOrNull { it.id == BRONZE_AXE } }
                client.locOp(VINE, 2690, 9564, BrimhavenDungeon.CHOP)
                client.arrives(2689, 9564)
                client.command("empty")
                client.await("an empty backpack") { if (client.inventory(Inventory.INV).all { it == null }) Unit else null }
                client.locOp(VINE, 2690, 9564, BrimhavenDungeon.CHOP)
                client.arrives(2691, 9564)

                client.tele(2655, 9566)
                client.locOp(PIPE_SOUTH, 2655, 9567, 1)
                client.message("[agility.brimhaven_dungeon_entrance_pipe_level level=22]")
                client.tele(2655, 9573)
                client.locOp(PIPE, 2655, 9571, 1)
                client.arrives(2655, 9566)
            }
        }
    }

    private companion object {
        const val BRONZE_AXE = 1351
        const val VINE = 21731
        const val PIPE = 21728
        const val PIPE_SOUTH = 60841
        const val WOODCUTTING = 8
        const val DOOR_VARP = 5_100

        /** karam_dungeon_doordata sits alone on a spare varp here. */
        val VARS = VarTypes(6_000, mapOf(BrimhavenDungeon.DOOR_VARBIT to VarbitType(DOOR_VARP, 0, 3)))
    }
}
