package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.content.CooksGuild
import osrsps.game.content.MillLaneMill
import osrsps.game.content.MillLines
import osrsps.game.dialogue.DialogueText
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
 * Edgeville piece step 9 through the headless client: the Head chef turns away a player below 32 Cooking at the front
 * door and lets one with 32 and a chef's hat in; the north-east room's door wants 99 Cooking; the spiral stairs go up
 * and down the guild's three floors, and their type elsewhere does nothing; Romily Weaklax's Trade opens the Pie Shop.
 * The rl-client script test-runner/examples/cooks-guild.json does the same in the real client.
 */
class HeadlessCooksGuildTest {
    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = 1, length = 1))

    private val locTypes = LocTypes(
        listOf(
            type(CooksGuild.FRONT_DOOR, "Door", "Open"),
            type(CooksGuild.FRONT_DOOR_OPEN, "Door", null, "Close"),
            type(CooksGuild.NORTH_DOOR, "Door", "Open"),
            type(CooksGuild.STAIRS_BOTTOM, "Staircase", "Climb-up"),
            type(CooksGuild.STAIRS_MIDDLE, "Staircase", "Climb", "Climb-up", "Climb-down"),
            type(CooksGuild.STAIRS_TOP, "Staircase", "Climb-down"),
            type(CooksGuild.HOPPER, "Hopper", "Fill"),
            type(CooksGuild.CONTROLS, "Hopper controls", "Operate"),
            LocType(CooksGuild.FLOUR_BIN, collision = LocCollisionType(width = 1, length = 1), multiloc = true),
        ).associateBy { it.id },
    )

    private val hat = ObjType(CHEFS_HAT, "Chef's hat", wearpos1 = WearPos.HEAD)
    private val pie = ObjType(REDBERRY_PIE, "Redberry pie", cost = 60, tradeable = true)
    private val grain = ObjType(MillLaneMill.GRAIN, "Grain")
    private val pot = ObjType(MillLaneMill.POT, "Pot")
    private val flour = ObjType(MillLaneMill.POT_OF_FLOUR, "Pot of flour")
    private val coins = ObjType(CooksGuild.COINS, "Coins", stackable = true)
    private val cape = ObjType(CooksGuild.COOKING_CAPE, "Cooking cape", wearpos1 = WearPos.CAPE)
    private val hood = ObjType(CooksGuild.COOKING_HOOD, "Cooking hood", wearpos1 = WearPos.HEAD)
    private val chef = NpcType(CooksGuild.HEAD_CHEF, "Head chef", ops = listOf("Talk-to", null, null, null, null), internalName = "cooks_guild_head_chef")
    private val romily = NpcType(CooksGuild.ROMILY, "Romily Weaklax", ops = listOf("Talk-to", null, "Trade", null, null), internalName = "merchant_pies")

    private val shops = ShopTypes(
        listOf(
            ShopType(
                "pie_shop", "Pie Shop", invId = SHOP_INV, size = 40, general = false, sellAt = 1000, buyAt = 550,
                changePer = 10, stock = listOf(ShopStock(pie, 5, 100)), npcs = listOf(romily.id),
            ),
        ),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply { markLoaded(3143 shr 6, 3447 shr 6) },
            locTypes = locTypes,
            objTypes = ObjTypes.of(hat, pie, coins, cape, hood, grain, pot, flour),
            npcTypes = NpcTypes.of(chef, romily),
            npcSpawns = listOf(
                NpcSpawn(chef, 3141, 3445, wander = 0, source = "test"),
                NpcSpawn(romily, 3139, 3448, wander = 0, source = "test"),
            ),
            shopTypes = shops,
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(CooksGuild.FRONT_DOOR, LocShape.WALL_STRAIGHT, 1, 3143, 3443, 0))
                add(ZoneLoc(CooksGuild.NORTH_DOOR, LocShape.WALL_STRAIGHT, 0, 3144, 3452, 0))
                add(ZoneLoc(CooksGuild.STAIRS_BOTTOM, LocShape.CENTREPIECE_STRAIGHT, 1, 3144, 3447, 0))
                add(ZoneLoc(CooksGuild.STAIRS_MIDDLE, LocShape.CENTREPIECE_STRAIGHT, 1, 3144, 3447, 1))
                add(ZoneLoc(CooksGuild.STAIRS_TOP, LocShape.CENTREPIECE_STRAIGHT, 1, 3144, 3447, 2))
                // The bottom stairs' type away from the guild.
                add(ZoneLoc(CooksGuild.STAIRS_BOTTOM, LocShape.CENTREPIECE_STRAIGHT, 1, 3150, 3440, 0))
                add(ZoneLoc(CooksGuild.HOPPER, LocShape.CENTREPIECE_STRAIGHT, 0, 3142, 3452, 2))
                add(ZoneLoc(CooksGuild.CONTROLS, LocShape.CENTREPIECE_STRAIGHT, 0, 3141, 3453, 2))
                add(ZoneLoc(CooksGuild.FLOUR_BIN, LocShape.CENTREPIECE_STRAIGHT, 1, 3140, 3449, 0))
            },
            varTypes = VarTypes(
                6_000,
                mapOf(MillLaneMill.VARBIT_FLOUR to VarbitType(VARP_FLOUR, 0, 4), MillLaneMill.VARBIT_SHOW_FLOUR to VarbitType(VARP_SHOW_FLOUR, 0, 0)),
            ),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.chat(key: String) {
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
        continueDialogue()
    }

    private fun HeadlessClient.menuPick(option: Int) {
        awaitDialogue("the menu") { it is DialogueView.Menu }
        chooseOption(option)
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).count { it?.id == id }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    @Test
    fun `the Head chef turns away a low level and lets in 32 Cooking in a chef's hat`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3143, 3443, 0)
                client.locOp(CooksGuild.FRONT_DOOR, 3143, 3443, 1)
                client.awaitDialogue("the refusal") { it is DialogueView.Chat && it.text == "[head_chef.door_level]" }
                client.continueDialogue()
                client.awaitTicks(2)
                assertEquals(Coord(3143, 3443, 0), client.position)

                client.command("setlevel cooking 32")
                client.commandReply("pause") { it.startsWith("Paused") }
                server.world.playerNamed("alice")!!.equipment[WearPos.HEAD] = osrsps.game.InvObj(CHEFS_HAT, 1)
                client.commandReply("resume") { it.startsWith("Resumed") }
                client.locOp(CooksGuild.FRONT_DOOR, 3143, 3443, 1)
                client.await("inside the guild") { client.position?.takeIf { it == Coord(3143, 3444, 0) } }
                // The door stays shut behind the player, so no one can follow without the check.
                kotlin.test.assertTrue(client.locChanges().none { it.id == CooksGuild.FRONT_DOOR_OPEN })
                // And back out, with no check on the way out.
                client.awaitTicks(3)
                client.locOp(CooksGuild.FRONT_DOOR, 3143, 3443, 1)
                client.await("outside again") { client.position?.takeIf { it == Coord(3143, 3443, 0) } }
            }
        }
    }

    @Test
    fun `the north-east room's door wants 99 Cooking`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3143, 3452, 0)
                client.locOp(CooksGuild.NORTH_DOOR, 3144, 3452, 1)
                client.seen("[cooks_guild.bank_door]")
                assertEquals(Coord(3143, 3452, 0), client.position)
                client.command("setlevel cooking 99")
                client.locOp(CooksGuild.NORTH_DOOR, 3144, 3452, 1)
                client.await("in the room") { client.position?.takeIf { it == Coord(3144, 3452, 0) } }
                client.locOp(CooksGuild.NORTH_DOOR, 3144, 3452, 1)
                client.await("back out") { client.position?.takeIf { it == Coord(3143, 3452, 0) } }
            }
        }
    }

    @Test
    fun `the stairs go up and down the three floors, and their type elsewhere does nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3145, 3447, 0)
                client.locOp(CooksGuild.STAIRS_BOTTOM, 3144, 3447, 1)
                client.await("the first floor") { client.position?.takeIf { it.level == 1 } }
                val first = client.position!!
                client.locOp(CooksGuild.STAIRS_MIDDLE, 3144, 3447, 2)
                client.await("the top floor") { client.position?.takeIf { it.level == 2 } }
                client.locOp(CooksGuild.STAIRS_TOP, 3144, 3447, 1)
                client.await("back on the first floor") { client.position?.takeIf { it.level == 1 } }
                client.tele(first.x, first.z, 1)
                client.locOp(CooksGuild.STAIRS_MIDDLE, 3144, 3447, 3)
                client.await("the ground floor") { client.position?.takeIf { it.level == 0 } }

                client.tele(3151, 3440, 0)
                client.locOp(CooksGuild.STAIRS_BOTTOM, 3150, 3440, 1)
                client.seen("[game.nothing_interesting]")
                assertEquals(Coord(3151, 3440, 0), client.position)
            }
        }
    }

    @Test
    fun `at 99 the Head chef sells the cape and hood for 99,000 coins, then a free hood`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3142, 3445, 0)
                client.command("setlevel cooking 99")
                client.command("item ${CooksGuild.COINS} 99000")
                client.await("the coins") { client.inventory(BACKPACK).firstOrNull { it?.id == CooksGuild.COINS }?.takeIf { it.count == 99_000 } }
                val chef = client.await("the Head chef in view") { client.npcs().firstOrNull { it.id == CooksGuild.HEAD_CHEF } }
                client.npcOp(chef.index, CooksGuild.TALK_TO)
                client.chat("head_chef.master")
                client.menuPick(2)
                client.chat("head_chef.buy")
                client.chat("head_chef.price")
                client.menuPick(2)
                client.chat("head_chef.sure")
                client.chat("head_chef.sold")
                client.await("the cape and hood, and no coins") {
                    val ids = client.inventory(BACKPACK).filterNotNull().map { it.id }
                    if (ids.sorted() == listOf(CooksGuild.COOKING_CAPE, CooksGuild.COOKING_HOOD)) Unit else null
                }

                // An owner asks for another hood, for free.
                client.npcOp(chef.index, CooksGuild.TALK_TO)
                client.chat("head_chef.master")
                client.menuPick(2)
                client.menuPick(2)
                client.chat("head_chef.hood_ask")
                client.chat("head_chef.hood_given")
                client.await("a second hood") {
                    if (client.inventory(BACKPACK).count { it?.id == CooksGuild.COOKING_HOOD } == 2) Unit else null
                }
            }
        }
    }

    @Test
    fun `grain through the top floor's hopper and controls into the ground floor's bin and a pot`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("frank").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item ${MillLaneMill.GRAIN}")
                client.command("item ${MillLaneMill.POT}")
                client.await("the grain and pot") { if (client.count(MillLaneMill.GRAIN) == 1 && client.count(MillLaneMill.POT) == 1) Unit else null }
                client.tele(3142, 3451, 2)
                client.locOp(CooksGuild.HOPPER, 3142, 3452, MillLaneMill.FILL)
                client.seen(DialogueText.NONE[MillLines.PUT_GRAIN])
                client.await("the grain gone") { if (client.count(MillLaneMill.GRAIN) == 0) Unit else null }
                client.tele(3141, 3452, 2)
                client.locOp(CooksGuild.CONTROLS, 3141, 3453, MillLaneMill.OPERATE)
                client.seen(DialogueText.NONE[MillLines.OPERATED])
                client.await("a pot's worth in the bin") { if (client.varp(VARP_FLOUR) == 1) Unit else null }
                client.tele(3141, 3448, 0)
                client.locOp(CooksGuild.FLOUR_BIN, 3140, 3449, MillLaneMill.EMPTY)
                client.seen(DialogueText.NONE[MillLines.LAST_POT])
                client.await("a pot of flour") {
                    if (client.count(MillLaneMill.POT_OF_FLOUR) == 1 && client.count(MillLaneMill.POT) == 0) Unit else null
                }
            }
        }
    }

    @Test
    fun `Romily Weaklax's Trade opens the Pie Shop`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3140, 3448, 0)
                val npc = client.await("Romily in view") { client.npcs().firstOrNull { it.id == romily.id } }
                client.npcOp(npc.index, CooksGuild.TRADE)
                client.await("the shop") { if (ShopScreen.MAIN in client.openInterfaces()) Unit else null }
                client.await("its stock") { client.inventory(SHOP_INV).takeIf { it == listOf(InvObj(REDBERRY_PIE, 5)) } }
            }
        }
    }

    private companion object {
        const val CHEFS_HAT = 1949
        const val REDBERRY_PIE = 2325
        const val SHOP_INV = 352
        const val BACKPACK = 93
        const val VARP_FLOUR = 203
        const val VARP_SHOW_FLOUR = 695
    }
}
