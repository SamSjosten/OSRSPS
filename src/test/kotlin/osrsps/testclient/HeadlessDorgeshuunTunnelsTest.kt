package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.DorgeshuunTunnels
import osrsps.game.content.LostTribeLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.shop.ShopScreen
import osrsps.game.shop.ShopStock
import osrsps.game.shop.ShopType
import osrsps.game.shop.ShopTypes
import osrsps.game.skill.AgilityLines
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Post-quest content step 6 through the headless client: the Lost Tribe's two holes (the cellar's at Agility 13), the
 * three guides' travel by option and by talk, Mistag buying ore, Nardok's talk opening his shop, the water mill cellar's
 * trapdoor, ladder and tunnel, and Sigmund's chest. The rl-client script test-runner/examples/dorgeshuun-tunnels.json does
 * the same in the real client.
 */
class HeadlessDorgeshuunTunnelsTest {
    private val ironOre = ObjType(DorgeshuunTunnels.IRON_ORE, "Iron ore")
    private val silverOre = ObjType(DorgeshuunTunnels.SILVER_ORE, "Silver ore")
    private val coins = ObjType(DorgeshuunTunnels.COINS, "Coins", stackable = true)
    private val boneClub = ObjType(5018, "Bone club", cost = 600)

    private val kazgar = NpcType(DorgeshuunTunnels.KAZGAR, "Kazgar", ops = listOf("Talk-to", null, "Mines", "Watermill", null), internalName = "lost_tribe_guide_3ops")
    private val mistag = NpcType(DorgeshuunTunnels.MISTAG, "Mistag", ops = listOf("Talk-to", null, "Cellar", "Watermill", null), internalName = "lost_tribe_mistag_3ops")
    private val dartog = NpcType(DorgeshuunTunnels.DARTOG, "Dartog", ops = listOf("Talk-to", null, "Mines", "Cellar", null), internalName = "dttd_maze_guide_there")
    private val nardok = NpcType(DorgeshuunTunnels.NARDOK, "Nardok", ops = listOf("Talk-to", null, "Trade", null, null), internalName = "dttd_bone_dealer")

    private fun type(id: Int, name: String, op: String) =
        LocType(id, name, ops = listOf(op, null, null, null, null), collision = LocCollisionType(width = 1, length = 1))

    private val cellarHole = ZoneLoc(CELLAR_HOLE, LocShape.CENTREPIECE_STRAIGHT, 0, 3220, 9618, 0)
    private val mazeHole = ZoneLoc(MAZE_HOLE, LocShape.CENTREPIECE_STRAIGHT, 0, 3224, 9602, 0)
    private val trapdoor = ZoneLoc(DorgeshuunTunnels.MILL_TRAPDOOR, LocShape.GROUND_DECOR, 0, 3230, 3286, 0)
    private val ladder = ZoneLoc(DorgeshuunTunnels.MILL_LADDER, LocShape.CENTREPIECE_STRAIGHT, 2, 3240, 9695, 0)
    private val mazeTunnel = ZoneLoc(DorgeshuunTunnels.TUNNEL_MAZE_SIDE, LocShape.CENTREPIECE_STRAIGHT, 1, 3245, 9649, 0)
    private val millTunnel = ZoneLoc(DorgeshuunTunnels.TUNNEL_MILL_SIDE, LocShape.CENTREPIECE_STRAIGHT, 1, 3245, 9661, 0)
    private val chest = ZoneLoc(DorgeshuunTunnels.SIGMUND_CHEST, LocShape.CENTREPIECE_STRAIGHT, 2, 3209, 3217, 1)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                for ((x, z) in listOf(3220 to 9618, 3220 to 9695, 3320 to 9615, 3230 to 3286, 3209 to 3217)) markLoaded(x shr 6, z shr 6)
            },
            objTypes = ObjTypes.of(ironOre, silverOre, coins, boneClub),
            locTypes = LocTypes(
                listOf(
                    type(CELLAR_HOLE, "Hole", "Squeeze-through"), type(MAZE_HOLE, "Hole", "Squeeze-through"),
                    type(DorgeshuunTunnels.MILL_TRAPDOOR, "Trapdoor", "Go-down"), type(DorgeshuunTunnels.MILL_LADDER, "Ladder", "Climb-up"),
                    type(DorgeshuunTunnels.TUNNEL_MAZE_SIDE, "Tunnel", "Squeeze-through"),
                    type(DorgeshuunTunnels.TUNNEL_MILL_SIDE, "Tunnel", "Squeeze-through"), type(DorgeshuunTunnels.SIGMUND_CHEST, "Chest", "Open"),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { listOf(cellarHole, mazeHole, trapdoor, ladder, mazeTunnel, millTunnel, chest).forEach(::add) },
            npcTypes = NpcTypes.of(kazgar, mistag, dartog, nardok),
            npcSpawns = listOf(
                NpcSpawn(kazgar, 3230, 9611, wander = 0, source = "test"),
                NpcSpawn(mistag, 3320, 9615, wander = 0, source = "test"),
                NpcSpawn(dartog, 3246, 9648, wander = 0, source = "test"),
                NpcSpawn(nardok, 3322, 9608, wander = 0, source = "test"),
            ),
            shopTypes = ShopTypes(
                listOf(
                    ShopType(
                        "nardoks_bone_weapons", "Nardok's Bone Weapons", invId = 413, size = 1, general = false, sellAt = 1000, buyAt = 600, changePer = 20,
                        stock = listOf(ShopStock(boneClub, 10, 20)), npcs = listOf(nardok.id),
                    ),
                ),
            ),
            random = GameRandom(5),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int = 0) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int, level: Int = 0) =
        assertEquals(Coord(x, z, level), await("$x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } })

    private fun HeadlessClient.chat(key: String) {
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
        continueDialogue()
    }

    private fun HeadlessClient.pick(option: Int, vararg keys: String) {
        val texts = keys.map { "[$it]" }
        awaitDialogue("the menu $texts") { it is DialogueView.Menu && it.options == texts }
        chooseOption(option)
    }

    private fun HeadlessClient.shownTheWay(guide: String) {
        val text = DialogueText.NONE[LostTribeLines.SHOWS_THE_WAY, "guide" to guide]
        awaitDialogue(text) { it is DialogueView.Message && it.text == text }
        continueDialogue()
    }

    private fun HeadlessClient.npc(type: NpcType) = await("${type.name} in view") { npcs().firstOrNull { it.id == type.id } }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    @Test
    fun `the cellar's hole needs Agility 13, and both holes go both ways`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel agility 12")
                client.tele(3219, 9618)
                client.locOp(CELLAR_HOLE, cellarHole.x, cellarHole.z, 1)
                client.awaitMessage("the level") { it == DialogueText.NONE[AgilityLines.LUMBRIDGE_CELLAR_HOLE_LEVEL, "level" to 13] }
                client.command("setlevel agility 13")
                client.locOp(CELLAR_HOLE, cellarHole.x, cellarHole.z, 1)
                client.awaitAt(3221, 9618)
                client.awaitTicks(1)
                client.locOp(CELLAR_HOLE, cellarHole.x, cellarHole.z, 1)
                client.awaitAt(3219, 9618)

                client.tele(3224, 9604)
                client.locOp(MAZE_HOLE, mazeHole.x, mazeHole.z, 1)
                client.awaitAt(3224, 9600)
                client.awaitTicks(1)
                client.locOp(MAZE_HOLE, mazeHole.x, mazeHole.z, 1)
                client.awaitAt(3224, 9604)
            }
        }
    }

    @Test
    fun `the guides take the player between the cellar, the mines and the water mill tunnel`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                // Kazgar's Mines option, straight there.
                client.tele(3228, 9611)
                client.npcOp(client.npc(kazgar).index, DorgeshuunTunnels.TRAVEL_1)
                client.shownTheWay("Kazgar")
                client.awaitAt(DorgeshuunTunnels.MINES.x, DorgeshuunTunnels.MINES.z)

                // Mistag's talk, to the water mill cellar.
                client.npcOp(client.npc(mistag).index, DorgeshuunTunnels.TALK_TO)
                client.chat("mistag.greeting")
                client.pick(5, "mistag.sell", "mistag.why", "mistag.arm", "mistag.out", "mistag.mill")
                client.chat("mistag.mill")
                client.chat("mistag.come_back")
                client.shownTheWay("Mistag")
                client.awaitAt(DorgeshuunTunnels.WATERMILL.x, DorgeshuunTunnels.WATERMILL.z)

                // Dartog's talk, to the cellar, after asking who he is.
                client.npcOp(client.npc(dartog).index, DorgeshuunTunnels.TALK_TO)
                client.chat("dartog.greeting")
                client.pick(1, "dartog.who", "dartog.mines", "dartog.cellar", "dartog.fine")
                client.chat("dartog.who")
                client.chat("dartog.who_1")
                client.await("the talk over") { if (client.dialogueView == null) Unit else null }
                client.npcOp(client.npc(dartog).index, DorgeshuunTunnels.TALK_TO)
                client.chat("dartog.greeting")
                client.pick(3, "dartog.who", "dartog.mines", "dartog.cellar", "dartog.fine")
                client.chat("dartog.cellar")
                client.chat("dartog.cellar_1")
                client.shownTheWay("Dartog")
                client.awaitAt(DorgeshuunTunnels.CELLAR.x, DorgeshuunTunnels.CELLAR.z)

                // Kazgar's talk: who he is, then the water mill cellar from his second menu.
                client.npcOp(client.npc(kazgar).index, DorgeshuunTunnels.TALK_TO)
                client.chat("kazgar.greeting")
                client.pick(1, "kazgar.who", "kazgar.mines", "kazgar.mill", "kazgar.bye")
                client.chat("kazgar.who")
                client.chat("kazgar.who_1")
                client.pick(2, "kazgar.mines", "kazgar.mill", "kazgar.later")
                client.chat("kazgar.mill")
                client.chat("kazgar.mill_1")
                client.shownTheWay("Kazgar")
                client.awaitAt(DorgeshuunTunnels.WATERMILL.x, DorgeshuunTunnels.WATERMILL.z)

                // Dartog's Cellar option.
                client.npcOp(client.npc(dartog).index, DorgeshuunTunnels.TRAVEL_2)
                client.shownTheWay("Dartog")
                client.awaitAt(DorgeshuunTunnels.CELLAR.x, DorgeshuunTunnels.CELLAR.z)
            }
        }
    }

    @Test
    fun `Mistag buys ore at his price and Nardok's talk opens his shop`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3319, 9614)
                // No ore: he says to come back with some.
                client.npcOp(client.npc(mistag).index, DorgeshuunTunnels.TALK_TO)
                client.chat("mistag.greeting")
                client.pick(1, "mistag.sell", "mistag.why", "mistag.arm", "mistag.out", "mistag.mill")
                client.chat("mistag.sell")
                client.chat("mistag.sell_none")
                client.await("the talk over") { if (client.dialogueView == null) Unit else null }

                client.command("item ${DorgeshuunTunnels.IRON_ORE} 3")
                client.command("item ${DorgeshuunTunnels.SILVER_ORE} 1")
                client.await("the ore") { if (client.count(DorgeshuunTunnels.SILVER_ORE) == 1) Unit else null }
                // A no keeps the ore.
                client.npcOp(client.npc(mistag).index, DorgeshuunTunnels.TALK_TO)
                client.chat("mistag.greeting")
                client.pick(1, "mistag.sell", "mistag.why", "mistag.arm", "mistag.out", "mistag.mill")
                client.chat("mistag.sell")
                client.chat("mistag.sell_offer coins=100")
                client.pick(2, "mistag.sell_ok", "mistag.sell_no")
                client.chat("mistag.sell_no_say")
                client.await("the talk over") { if (client.dialogueView == null) Unit else null }
                assertEquals(3, client.count(DorgeshuunTunnels.IRON_ORE))
                // A yes: 3 iron at 13.6 and a silver at 60 make 100 coins (100.8 rounded down).
                client.npcOp(client.npc(mistag).index, DorgeshuunTunnels.TALK_TO)
                client.chat("mistag.greeting")
                client.pick(1, "mistag.sell", "mistag.why", "mistag.arm", "mistag.out", "mistag.mill")
                client.chat("mistag.sell")
                client.chat("mistag.sell_offer coins=100")
                client.pick(1, "mistag.sell_ok", "mistag.sell_no")
                client.chat("mistag.sell_ok_say")
                client.await("the coins") { if (client.count(DorgeshuunTunnels.COINS) == 100) Unit else null }
                assertEquals(0, client.count(DorgeshuunTunnels.IRON_ORE))
                assertEquals(0, client.count(DorgeshuunTunnels.SILVER_ORE))

                client.npcOp(client.npc(nardok).index, DorgeshuunTunnels.TALK_TO)
                client.chat("nardok.greeting")
                client.pick(1, "nardok.what", "nardok.no", "nardok.whisper")
                for (key in listOf("nardok.what", "nardok.what_1", "nardok.what_2", "nardok.what_3", "nardok.what_4")) client.chat(key)
                client.pick(3, "nardok.might", "nardok.no", "nardok.technique")
                for (key in listOf("nardok.technique", "nardok.technique_1", "nardok.technique_2", "nardok.technique_3", "nardok.technique_4", "nardok.technique_5")) {
                    client.chat(key)
                }
                // The technique is asked once.
                client.pick(1, "nardok.might", "nardok.no")
                client.chat("nardok.might")
                client.await("the shop") { if (ShopScreen.MAIN in client.openInterfaces()) Unit else null }
            }
        }
    }

    @Test
    fun `the water mill cellar's trapdoor, ladder and tunnel, and Sigmund's chest`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3230, 3285)
                client.locOp(trapdoor.id, trapdoor.x, trapdoor.z, 1)
                client.awaitAt(DorgeshuunTunnels.MILL_BELOW.x, DorgeshuunTunnels.MILL_BELOW.z)
                client.locOp(ladder.id, ladder.x, ladder.z, 1)
                client.awaitAt(DorgeshuunTunnels.MILL_ABOVE.x, DorgeshuunTunnels.MILL_ABOVE.z)

                client.tele(3245, 9662)
                client.locOp(millTunnel.id, millTunnel.x, millTunnel.z, 1)
                client.awaitAt(DorgeshuunTunnels.TUNNEL_MAZE_END.x, DorgeshuunTunnels.TUNNEL_MAZE_END.z)
                client.locOp(mazeTunnel.id, mazeTunnel.x, mazeTunnel.z, 1)
                client.awaitAt(DorgeshuunTunnels.TUNNEL_MILL_END.x, DorgeshuunTunnels.TUNNEL_MILL_END.z)

                client.tele(3208, 3217, 1)
                client.locOp(chest.id, chest.x, chest.z, 1)
                client.awaitMessage("the chest") { it == DialogueText.NONE[LostTribeLines.CHEST_FOUND] }
            }
        }
    }

    private companion object {
        const val CELLAR_HOLE = 6898
        const val MAZE_HOLE = 6912
        const val BACKPACK = 93
    }
}
