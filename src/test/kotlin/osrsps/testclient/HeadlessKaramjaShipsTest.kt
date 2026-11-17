package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.KaramjaShipLines
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
 * Karamja step K1.1 through the headless client: Captain Tobias's talk taking 30 coins and sailing to Musa Point's deck
 * (the ship journey screen up meanwhile), his Musa Point option doing the same at once or refusing without the fare, the
 * Pandemonium refused; the customs officer's talk sailing back to Port Sarim, and her search taking rum and ending the
 * trip; the gangplanks crossing between deck and dock. test-runner/examples/karamja-ships.json does the same on the
 * real map.
 */
class HeadlessKaramjaShipsTest {
    private fun npc(id: Int, name: String, internal: String, travel: String) =
        NpcType(id, name, ops = listOf("Talk-to", null, travel, "The Pandemonium", null), internalName = internal)

    private val tobias = npc(KaramjaShips.CAPTAIN_TOBIAS, "Captain Tobias", "captain_tobias", "Musa Point")
    private val customs = npc(KaramjaShips.CUSTOMS_OFFICER, "Customs officer", "customs_officer", "Port Sarim")

    private fun plank(id: Int, x: Int, z: Int, level: Int) = ZoneLoc(id, LocShape.CENTREPIECE_STRAIGHT, 0, x, z, level)

    /** As the 242 map has them: each `_on` plank over the dock's bridge tiles (level 0), each `_off` on the deck (level 1). */
    private val planks = listOf(
        plank(KaramjaShips.SARIM_SHIP_PLANK_ON, 2956, 3145, 0),
        plank(KaramjaShips.SARIM_SHIP_PLANK_OFF, 2956, 3144, 1),
        plank(KaramjaShips.KARAMJA_SHIP_PLANK_ON, 3030, 3217, 0),
        plank(KaramjaShips.KARAMJA_SHIP_PLANK_OFF, 3031, 3217, 1),
    )

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(KaramjaShips.RUM.max() + 1)
        objs[KaramjaShips.COINS] = ObjType(KaramjaShips.COINS, "Coins", stackable = true)
        for (rum in KaramjaShips.RUM) objs[rum] = ObjType(rum, "Karamjan rum")
        World(
            crcs,
            CollisionMap().apply { markLoaded(2956 shr 6, 3143 shr 6); markLoaded(3028 shr 6, 3216 shr 6) },
            random = GameRandom(7), objTypes = ObjTypes(objs), npcTypes = NpcTypes.of(tobias, customs),
            npcSpawns = listOf(NpcSpawn(tobias, 3028, 3216, wander = 0, source = "test"), NpcSpawn(customs, 2953, 3147, wander = 0, source = "test")),
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

    private fun HeadlessClient.sees(vararg lines: Line) {
        for (line in lines) {
            awaitDialogue(line.key) { it is DialogueView.Chat && it.text == DialogueText.NONE[line] }
            continueDialogue()
        }
    }

    private fun HeadlessClient.choose(option: Int) {
        awaitDialogue("a menu") { it is DialogueView.Menu }
        chooseOption(option)
    }

    private fun HeadlessClient.seen(text: String) = messages().count { it.text == text }

    private fun HeadlessClient.npcIndex(server: TestServer, id: Int) = server.world.npcs.all.first { it.type.id == id }.index

    /** Waits out the crossing: the journey screen up, then the player on [deck] with the arrival box. */
    private fun HeadlessClient.sails(deck: Coord, arrives: Line) {
        await("the ship journey screen") { if (KaramjaShips.JOURNEY in openInterfaces()) Unit else null }
        await("the landing on $deck") { position?.takeIf { it == deck } }
        awaitDialogue("the arrival box") { it is DialogueView.Message && it.text == DialogueText.NONE[arrives] }
        continueDialogue()
        assertTrue(KaramjaShips.JOURNEY !in openInterfaces(), "the journey screen closes on landing")
    }

    private val musaDeck = Coord(2956, 3143, 1)
    private val sarimDeck = Coord(3032, 3217, 1)

    @Test
    fun `Captain Tobias sails to Musa Point for 30 coins, and the gangplank leads ashore`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ada").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.command("item ${KaramjaShips.COINS} 100")
                client.await("the coins") { if (client.count(KaramjaShips.COINS) == 100L) Unit else null }
                client.tele(3029, 3216)
                with(KaramjaShipLines) {
                    client.npcOp(client.npcIndex(server, KaramjaShips.CAPTAIN_TOBIAS), KaramjaShips.TALK_TO)
                    client.sees(SEAMAN_GREETING)
                    client.choose(1)
                    client.sees(YES_PLEASE, WHERE)
                    client.choose(1)
                    client.sees(TO_MUSA_POINT)
                    client.sails(musaDeck, ARRIVES_MUSA_POINT)
                    assertEquals(70L, client.count(KaramjaShips.COINS), "the 30-coin fare")
                    assertEquals(1, client.seen("[karamja_ship.paid fare=30]"), "the fare's message")
                }
                client.locOp(KaramjaShips.SARIM_SHIP_PLANK_OFF, 2956, 3144, KaramjaShips.CROSS)
                client.await("ashore on Musa Point's dock") { client.position?.takeIf { it == Coord(2956, 3146, 0) } }
                client.locOp(KaramjaShips.SARIM_SHIP_PLANK_ON, 2956, 3145, KaramjaShips.CROSS)
                client.await("back aboard") { client.position?.takeIf { it == musaDeck } }
                assertEquals(70L, client.count(KaramjaShips.COINS), "a gangplank costs nothing")
            }
        }
    }

    @Test
    fun `the Musa Point option sails at once, but not without the fare, and the Pandemonium is shut`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ben").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.tele(3029, 3216)
                val index = client.npcIndex(server, KaramjaShips.CAPTAIN_TOBIAS)
                client.npcOp(index, KaramjaShips.TRAVEL)
                client.await("the no-fare message") { if (client.seen("[karamja_ship.not_enough_coins]") == 1) Unit else null }
                client.npcOp(index, KaramjaShips.PANDEMONIUM)
                client.await("the Pandemonium refused") { if (client.seen("[karamja_ship.pandemonium_closed]") == 1) Unit else null }
                assertEquals(Coord(3029, 3216, 0), client.position, "neither sails")

                client.command("item ${KaramjaShips.COINS} 29")
                client.await("29 coins") { if (client.count(KaramjaShips.COINS) == 29L) Unit else null }
                client.npcOp(index, KaramjaShips.TRAVEL)
                client.await("the second no-fare message") { if (client.seen("[karamja_ship.not_enough_coins]") == 2) Unit else null }
                assertEquals(29L, client.count(KaramjaShips.COINS), "29 coins aren't taken")

                client.command("item ${KaramjaShips.COINS} 1")
                client.await("30 coins") { if (client.count(KaramjaShips.COINS) == 30L) Unit else null }
                client.npcOp(index, KaramjaShips.TRAVEL)
                client.sails(musaDeck, KaramjaShipLines.ARRIVES_MUSA_POINT)
                assertEquals(0L, client.count(KaramjaShips.COINS), "the fare")
            }
        }
    }

    @Test
    fun `the customs officer takes rum, then sails back to Port Sarim for the fare`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("cat").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.command("item ${KaramjaShips.COINS} 50")
                client.command("item 431 2")
                client.await("coins and rum") { if (client.count(431) == 2L && client.count(KaramjaShips.COINS) == 50L) Unit else null }
                client.tele(2953, 3146)
                val index = client.npcIndex(server, KaramjaShips.CUSTOMS_OFFICER)
                with(KaramjaShipLines) {
                    client.npcOp(index, KaramjaShips.TALK_TO)
                    client.sees(CUSTOMS_HELP)
                    client.choose(1)
                    client.sees(JOURNEY, SPOT_INSPECTION, SMUGGLING)
                    client.choose(1)
                    client.sees(PERSONAL_USE)
                    client.await("the rum taken") { if (client.seen("[karamja_ship.confiscated]") == 1) Unit else null }
                    client.await("both bottles gone") { if (client.count(431) == 0L) Unit else null }
                    assertEquals(50L, client.count(KaramjaShips.COINS), "no fare taken")
                    assertEquals(Coord(2953, 3146, 0), client.position, "still on Karamja")

                    client.npcOp(index, KaramjaShips.TALK_TO)
                    client.sees(CUSTOMS_HELP)
                    client.choose(1)
                    client.sees(JOURNEY, KNOW_YOU, BOARDING_CHARGE)
                    client.choose(1)
                    client.sees(OK)
                    client.sails(sarimDeck, ARRIVES_PORT_SARIM)
                    assertEquals(20L, client.count(KaramjaShips.COINS), "the 30-coin fare")
                }
                client.locOp(KaramjaShips.KARAMJA_SHIP_PLANK_OFF, 3031, 3217, KaramjaShips.CROSS)
                client.await("ashore on Port Sarim's dock") { client.position?.takeIf { it == Coord(3029, 3217, 0) } }
            }
        }
    }

    @Test
    fun `the customs officer's Port Sarim option searches too, and her other talk is about customs`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dee").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.command("item ${KaramjaShips.COINS} 30")
                client.command("item 3164 1")
                client.await("coins and rum") { if (client.count(3164) == 1L && client.count(KaramjaShips.COINS) == 30L) Unit else null }
                client.tele(2953, 3146)
                val index = client.npcIndex(server, KaramjaShips.CUSTOMS_OFFICER)
                with(KaramjaShipLines) {
                    client.npcOp(index, KaramjaShips.TALK_TO)
                    client.sees(CUSTOMS_HELP)
                    client.choose(2)
                    client.sees(CUSTOMS_ASK_SAY, NOT_THAT_SORT)
                }
                client.npcOp(index, KaramjaShips.TRAVEL)
                client.await("the rum taken") { if (client.seen("[karamja_ship.confiscated]") == 1 && client.count(3164) == 0L) Unit else null }
                assertEquals(30L, client.count(KaramjaShips.COINS), "no fare taken with the rum")
                client.npcOp(index, KaramjaShips.TRAVEL)
                client.sails(sarimDeck, KaramjaShipLines.ARRIVES_PORT_SARIM)
                assertEquals(0L, client.count(KaramjaShips.COINS), "the fare")
            }
        }
    }

    companion object {
        const val BACKPACK = 93
    }
}
