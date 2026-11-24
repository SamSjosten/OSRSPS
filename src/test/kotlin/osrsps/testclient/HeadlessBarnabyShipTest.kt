package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InvObj
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.content.BarnabyLines
import osrsps.game.content.BarnabyShip
import osrsps.game.content.KaramjaShips
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Karamja step K3.1 through the headless client: Captain Barnaby's talk on Brimhaven's dock taking 30 coins and landing
 * the player on Ardougne's deck, its gangplanks crossing both ways, and his right-click option refusing without the fare
 * and taking 15 from a player in Karamja gloves. test-runner/examples/barnaby-ship.json does the same on the real map.
 */
class HeadlessBarnabyShipTest {
    private fun barnaby(id: Int, internal: String, first: String, second: String) =
        NpcType(id, "Captain Barnaby", ops = listOf("Talk-to", null, first, second, null), internalName = internal)

    private val ardougne = barnaby(BarnabyShip.BARNABY_ARDOUGNE, "captain_barnaby", "Brimhaven", "Rimmington")
    private val brimhaven = barnaby(BarnabyShip.BARNABY_BRIMHAVEN, "captain_barnaby_karamja", "Ardougne", "Rimmington")

    private fun plank(id: Int, x: Int, z: Int, level: Int) = ZoneLoc(id, LocShape.CENTREPIECE_STRAIGHT, 0, x, z, level)

    /** As the 242 map has them: each `_on` plank by the dock, each `_off` by the deck. */
    private val planks = listOf(
        plank(BarnabyShip.Dock.ARDOUGNE.plankOn, 2683, 3265, 0),
        plank(BarnabyShip.Dock.ARDOUGNE.plankOff, 2683, 3264, 1),
        plank(BarnabyShip.Dock.BRIMHAVEN.plankOn, 2773, 3234, 0),
        plank(BarnabyShip.Dock.BRIMHAVEN.plankOff, 2774, 3234, 1),
    )

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(KaramjaShips.KARAMJA_GLOVES.max() + 1)
        objs[KaramjaShips.COINS] = ObjType(KaramjaShips.COINS, "Coins", stackable = true)
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(2683 shr 6, 3263 shr 6)
                markLoaded(2683 shr 6, 3266 shr 6)
                markLoaded(2775 shr 6, 3234 shr 6)
                markLoaded(2679 shr 6, 3275 shr 6)
            },
            objTypes = ObjTypes(objs), npcTypes = NpcTypes.of(ardougne, brimhaven),
            npcSpawns = listOf(NpcSpawn(ardougne, 2679, 3275, wander = 0, source = "test"), NpcSpawn(brimhaven, 2773, 3229, wander = 0, source = "test")),
            locTypes = LocTypes(planks.map { it.id }.distinct().associateWith { LocType(it, "Gangplank", ops = listOf("Cross", null, null, null, null), collision = LocCollisionType(width = 1, length = 1)) }),
            staticLocs = StaticLocs().apply { planks.forEach(::add) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int = 0) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.count(id: Int): Long = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count.toLong() }

    private fun HeadlessClient.coins(count: Int) {
        command("item ${KaramjaShips.COINS} $count")
        await("the coins") { if (count(KaramjaShips.COINS) == count.toLong()) Unit else null }
    }

    private fun HeadlessClient.sees(vararg lines: Line) {
        for (line in lines) {
            awaitDialogue(line.key) { it is DialogueView.Chat && it.text == DialogueText.NONE[line] }
            continueDialogue()
        }
    }

    private fun HeadlessClient.sails(line: Line, deck: BarnabyShip.Dock) {
        awaitDialogue(line.key) { it is DialogueView.Message && it.text == DialogueText.NONE[line] }
        continueDialogue()
        await("the landing on $deck's deck") { position?.takeIf { it == Coord(deck.deck.x, deck.deck.z, deck.deck.level) } }
    }

    private fun HeadlessClient.npcIndex(server: TestServer, id: Int) = server.world.npcs.all.first { it.type.id == id }.index

    @Test
    fun `Barnaby sails from Brimhaven to Ardougne for 30 coins, and the gangplanks cross`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ada").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.coins(100)
                client.tele(2773, 3230)
                with(BarnabyLines) {
                    client.npcOp(client.npcIndex(server, BarnabyShip.BARNABY_BRIMHAVEN), BarnabyShip.TALK_TO)
                    client.sees(GREETING_BRIMHAVEN)
                    val menu = client.awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
                    assertEquals(listOf(TO_RIMMINGTON, TO_ARDOUGNE, GOOD_THANKS).map { DialogueText.NONE[it] }, menu.options)
                    client.chooseOption(2)
                    client.sees(TO_ARDOUGNE)
                    client.sails(SAIL_ARDOUGNE, BarnabyShip.Dock.ARDOUGNE)
                }
                assertEquals(70L, client.count(KaramjaShips.COINS), "the 30-coin fare")
                client.locOp(BarnabyShip.Dock.ARDOUGNE.plankOff, 2683, 3264, KaramjaShips.CROSS)
                client.await("ashore on Ardougne's dock") { client.position?.takeIf { it == Coord(2683, 3266, 0) } }
                client.locOp(BarnabyShip.Dock.ARDOUGNE.plankOn, 2683, 3265, KaramjaShips.CROSS)
                client.await("back aboard") { client.position?.takeIf { it == Coord(2683, 3263, 1) } }
                assertEquals(70L, client.count(KaramjaShips.COINS), "a gangplank costs nothing")
            }
        }
    }

    @Test
    fun `his Brimhaven option refuses without the fare, and Karamja gloves halve it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ben").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.tele(2680, 3276)
                val index = client.npcIndex(server, BarnabyShip.BARNABY_ARDOUGNE)
                client.npcOp(index, BarnabyShip.FIRST_PORT)
                client.await("the refusal") { client.messages().firstOrNull { it.text == "[barnaby.no_coins]" } }
                client.coins(15)
                server.world.playerNamed("ben")!!.equipment[WearPos.HANDS] = InvObj(KaramjaShips.KARAMJA_GLOVES.first(), 1)
                client.npcOp(index, BarnabyShip.FIRST_PORT)
                client.sails(BarnabyLines.SAIL_BRIMHAVEN, BarnabyShip.Dock.BRIMHAVEN)
                assertEquals(0L, client.count(KaramjaShips.COINS), "the gloves' 15-coin fare")
                client.locOp(BarnabyShip.Dock.BRIMHAVEN.plankOff, 2774, 3234, KaramjaShips.CROSS)
                client.await("ashore on Brimhaven's dock") { client.position?.takeIf { it == Coord(2772, 3234, 0) } }
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
    }
}
