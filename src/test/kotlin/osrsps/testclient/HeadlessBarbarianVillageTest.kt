package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.BarbarianVillage
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
 * Edgeville piece step 5 through the headless client: Peksa's yes opens his Helmet Shop, the longhall's double doors
 * open together, and the lookout tower's ladders take a player to Hunding at the top and back down. The rl-client
 * script test-runner/examples/barbarian-village.json does the same in the real client, with spinning and pottery.
 */
class HeadlessBarbarianVillageTest {
    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = 1, length = 1))

    private val locTypes = LocTypes(
        listOf(
            type(BarbarianVillage.LONGHALL_DOOR_LEFT, "Longhall door", "Open"),
            type(BarbarianVillage.LONGHALL_DOOR_LEFT_OPEN, "Longhall door", "Close"),
            type(BarbarianVillage.LONGHALL_DOOR_RIGHT, "Longhall door", "Open"),
            type(BarbarianVillage.LONGHALL_DOOR_RIGHT_OPEN, "Longhall door", "Close"),
            type(BarbarianVillage.TOWER_LADDER, "Ladder", "Climb-up"),
            type(BarbarianVillage.TOWER_LADDER_TOP, "Ladder", "Climb-down"),
        ).associateBy { it.id },
    )

    private val helm = ObjType(HELM, "Bronze med helm", cost = 24, tradeable = true)
    private val peksa = NpcType(BarbarianVillage.PEKSA, "Peksa", ops = listOf("Talk-to", null, "Trade", null, null), internalName = "peksa")
    private val hunding = NpcType(BarbarianVillage.HUNDING, "Hunding", ops = listOf("Talk-to", null, null, null, null), internalName = "fai_barbarian_9")

    private val shops = ShopTypes(
        listOf(
            ShopType(
                "peksas_helmet_shop", "Peksa's Helmet Shop", invId = SHOP_INV, size = 40, general = false, sellAt = 1000, buyAt = 600,
                changePer = 10, stock = listOf(ShopStock(helm, 5, 100)), npcs = listOf(peksa.id),
            ),
        ),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply { markLoaded(3078 shr 6, 3430 shr 6) },
            locTypes = locTypes,
            objTypes = ObjTypes.of(helm),
            npcTypes = NpcTypes.of(peksa, hunding),
            npcSpawns = listOf(NpcSpawn(peksa, 3076, 3429, wander = 0, source = "test"), NpcSpawn(hunding, 3096, 3428, 2, wander = 0, source = "test")),
            shopTypes = shops,
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(BarbarianVillage.LONGHALL_DOOR_LEFT, LocShape.WALL_STRAIGHT, 1, 3078, 3435, 0))
                add(ZoneLoc(BarbarianVillage.LONGHALL_DOOR_RIGHT, LocShape.WALL_STRAIGHT, 1, 3079, 3435, 0))
                add(ZoneLoc(BarbarianVillage.TOWER_LADDER, LocShape.CENTREPIECE_STRAIGHT, 2, 3096, 3433, 0))
                add(ZoneLoc(BarbarianVillage.TOWER_LADDER_TOP, LocShape.CENTREPIECE_STRAIGHT, 2, 3096, 3433, 1))
                add(ZoneLoc(BarbarianVillage.TOWER_LADDER, LocShape.CENTREPIECE_STRAIGHT, 2, 3097, 3433, 1))
                add(ZoneLoc(BarbarianVillage.TOWER_LADDER_TOP, LocShape.CENTREPIECE_STRAIGHT, 2, 3097, 3433, 2))
            },
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

    private fun HeadlessClient.talkTo(id: Int) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, BarbarianVillage.TALK_TO)
    }

    @Test
    fun `Peksa's yes opens his Helmet Shop`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3077, 3429, 0)
                client.talkTo(peksa.id)
                client.chat("peksa.greeting")
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.await("the shop") { if (ShopScreen.MAIN in client.openInterfaces()) Unit else null }
                client.await("its stock") { client.inventory(SHOP_INV).takeIf { it == listOf(InvObj(HELM, 5)) } }
            }
        }
    }

    @Test
    fun `the longhall's double doors open together`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3078, 3434, 0)
                client.locOp(BarbarianVillage.LONGHALL_DOOR_LEFT, 3078, 3435, 1)
                client.await("both leaves open") {
                    val ids = client.locChanges().map { it.id }
                    if (BarbarianVillage.LONGHALL_DOOR_LEFT_OPEN in ids && BarbarianVillage.LONGHALL_DOOR_RIGHT_OPEN in ids) Unit else null
                }
            }
        }
    }

    @Test
    fun `the lookout tower's ladders go up to Hunding and back down`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3096, 3432, 0)
                client.locOp(BarbarianVillage.TOWER_LADDER, 3096, 3433, 1)
                client.await("the first floor") { client.position?.takeIf { it.level == 1 } }
                client.tele(3097, 3432, 1)
                client.locOp(BarbarianVillage.TOWER_LADDER, 3097, 3433, 1)
                client.await("the top") { client.position?.takeIf { it.level == 2 } }
                client.talkTo(hunding.id)
                client.chat("hunding.hello")
                client.chat("hunding.what_doing")
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(3)
                client.chat("hunding.kill")
                client.chat("hunding.kill_reply")
                client.locOp(BarbarianVillage.TOWER_LADDER_TOP, 3097, 3433, 1)
                client.await("back on the first floor") { client.position?.takeIf { it.level == 1 } }
            }
        }
    }

    private companion object {
        const val HELM = 1139
        const val SHOP_INV = 10
    }
}
