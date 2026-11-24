package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.content.BalloonLines
import osrsps.game.content.BalloonStation
import osrsps.game.content.Balloons
import osrsps.game.content.Destination
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * The balloon transport system (travel networks step T5) through the headless client: a basket's Use opens the map and
 * a destination's Travel burns its log and lands beside that basket; the Firemaking level, the log and the 40 kg limit
 * each stop a trip with their message; Assistant Serf's Talk-to opens the same map; a trip to Entrana is searched for
 * weapons and armour; a Travel click with no map open does nothing. The rl-client script
 * test-runner/examples/balloons.json flies from Varrock to Castle Wars and back in the real client.
 */
class HeadlessBalloonsTest {
    private val stations = listOf(BalloonStation.VARROCK, BalloonStation.CASTLE_WARS, BalloonStation.ENTRANA)

    private fun basket(station: BalloonStation) =
        ZoneLoc(station.basket, LocShape.CENTREPIECE_STRAIGHT, 0, station.basketTile.x, station.basketTile.z, 0)

    private val serf = NpcType(BalloonStation.VARROCK.assistants.first(), "Assistant Serf", ops = listOf("Talk-to", null, null, "Fly", null))
    private val auguste = NpcType(BalloonStation.ENTRANA.assistants.first(), "Auguste", ops = listOf("Talk-to", null, null, "Fly", null))

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                for (station in stations) markLoaded(station.basketTile.x shr 6, station.basketTile.z shr 6)
            },
            random = GameRandom(7),
            objTypes = ObjTypes.of(
                ObjType(BalloonStation.VARROCK.log, "Willow logs", weight = 2000),
                ObjType(BalloonStation.CASTLE_WARS.log, "Yew logs", weight = 1500),
                ObjType(BalloonStation.ENTRANA.log, "Logs", weight = 2000),
                ObjType(ANCHOR, "Anchor", weight = 39_500),
                ObjType(PLATEBODY, "Rune platebody", wearpos1 = WearPos.BODY, params = mapOf(STAB_DEFENCE to 82), weight = 9979),
                ObjType(FIRE_CAPE, "Fire cape", wearpos1 = WearPos.CAPE, params = mapOf(STAB_DEFENCE to 11), weight = 1814),
            ),
            npcTypes = NpcTypes.of(serf, auguste),
            npcSpawns = listOf(NpcSpawn(serf, 3298, 3483, wander = 0, source = "test")),
            locTypes = LocTypes(
                stations.map { LocType(it.basket, "Basket", listOf("Use", null, null, null, null), LocCollisionType(width = 2, length = 2)) }
                    .associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { stations.forEach { add(basket(it)) } },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(to: Destination) {
        command("tele ${to.x} ${to.z} ${to.level}")
        await("at $to") { position?.takeIf { it == Coord(to.x, to.z, to.level) } }
    }

    private fun HeadlessClient.awaitAt(to: Destination) {
        await("at $to", timeoutMillis = 10_000) { position?.takeIf { it == Coord(to.x, to.z, to.level) } }
    }

    private fun HeadlessClient.useBasket(station: BalloonStation) {
        locOp(station.basket, station.basketTile.x, station.basketTile.z, Balloons.USE)
        await("the map") { if (Balloons.MAP in openInterfaces()) Unit else null }
    }

    private fun HeadlessClient.seen(text: String, after: Int = 0) =
        await(text) { messages().drop(after).firstOrNull { it.text == text } }

    @Test
    fun `Use opens the map and Travel burns the destination's log and lands beside its basket`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel firemaking 50")
                client.command("item ${BalloonStation.CASTLE_WARS.log} 2")
                client.tele(BalloonStation.VARROCK.landing)
                client.useBasket(BalloonStation.VARROCK)
                client.ifButton(Balloons.MAP, BalloonStation.CASTLE_WARS.button, 1)
                client.awaitAt(BalloonStation.CASTLE_WARS.landing)
                assertFalse(Balloons.MAP in client.openInterfaces())
                assertEquals(1L, client.inventory(BACKPACK).filterNotNull().filter { it.id == BalloonStation.CASTLE_WARS.log }.sumOf { it.count.toLong() })
                // A click on the station the player is at does nothing.
                client.useBasket(BalloonStation.CASTLE_WARS)
                client.ifButton(Balloons.MAP, BalloonStation.CASTLE_WARS.button, 1)
                client.awaitTicks(Balloons.FLIGHT_TICKS + 2)
                assertEquals(Coord(BalloonStation.CASTLE_WARS.landing.x, BalloonStation.CASTLE_WARS.landing.z, 0), client.position)
            }
        }
    }

    @Test
    fun `the Firemaking level, the log and the weight limit each stop a trip`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                val at = BalloonStation.VARROCK.landing
                client.tele(at)
                client.command("setlevel firemaking 49")
                client.useBasket(BalloonStation.VARROCK)
                client.ifButton(Balloons.MAP, BalloonStation.CASTLE_WARS.button, 1)
                client.seen("[${BalloonLines.LEVEL.key} level=50]")
                client.command("setlevel firemaking 50")
                var before = client.messages().size
                client.ifButton(Balloons.MAP, BalloonStation.CASTLE_WARS.button, 1)
                client.seen("[${BalloonLines.NEED_LOGS.key} logs=[${BalloonLines.YEW_LOGS.key}]]", before)
                client.command("item ${BalloonStation.CASTLE_WARS.log}")
                client.command("item $ANCHOR")
                client.await("the anchor") { client.inventory(BACKPACK).firstOrNull { it?.id == ANCHOR } }
                client.awaitTicks(2)
                // 39.5 kg and the log's 1.5: the log doesn't count, so this is within the limit; a platebody isn't.
                client.command("item $PLATEBODY")
                client.await("the platebody") { client.inventory(BACKPACK).firstOrNull { it?.id == PLATEBODY } }
                client.awaitTicks(2)
                before = client.messages().size
                client.ifButton(Balloons.MAP, BalloonStation.CASTLE_WARS.button, 1)
                client.seen("[${BalloonLines.TOO_HEAVY.key} limit=40]", before)
                assertEquals(Coord(at.x, at.z, 0), client.position)
                client.command("empty")
                client.command("item ${BalloonStation.CASTLE_WARS.log}")
                client.command("item $ANCHOR")
                client.await("the anchor again") { client.inventory(BACKPACK).firstOrNull { it?.id == ANCHOR } }
                client.awaitTicks(2)
                client.ifButton(Balloons.MAP, BalloonStation.CASTLE_WARS.button, 1)
                client.awaitAt(BalloonStation.CASTLE_WARS.landing)
            }
        }
    }

    @Test
    fun `Assistant Serf's Yes opens the map, and a trip to Entrana is searched for armour`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel firemaking 20")
                client.command("item ${BalloonStation.ENTRANA.log} 2")
                client.command("item $PLATEBODY")
                client.tele(BalloonStation.VARROCK.landing)
                val npc = client.await("Serf in view") { client.npcs().firstOrNull { it.id == serf.id } }
                client.npcOp(npc.index, Balloons.TALK_TO)
                client.awaitDialogue("the greeting") { it is DialogueView.Chat && it.text == "[${BalloonLines.GREETING.key}]" }
                client.continueDialogue()
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.await("the map") { if (Balloons.MAP in client.openInterfaces()) Unit else null }
                val here = client.position
                client.ifButton(Balloons.MAP, BalloonStation.ENTRANA.button, 1)
                client.awaitDialogue("the search") { it is DialogueView.Chat && it.text == "[${BalloonLines.ENTRANA_SEARCH.key}]" }
                client.continueDialogue()
                client.awaitDialogue("searched") { it is DialogueView.Message && it.text == "[${BalloonLines.SEARCHED.key}]" }
                client.continueDialogue()
                client.awaitDialogue("the refusal") { it is DialogueView.Chat && it.text == "[${BalloonLines.ENTRANA_REFUSE.key}]" }
                client.continueDialogue()
                client.await("the dialogue closed") { if (client.dialogueView == null) Unit else null }
                assertEquals(here, client.position)
                assertEquals(2L, client.inventory(BACKPACK).filterNotNull().filter { it.id == BalloonStation.ENTRANA.log }.sumOf { it.count.toLong() })
                // A cape with a defence bonus is armour too (the Wiki's Entrana page).
                client.command("empty")
                client.command("item ${BalloonStation.ENTRANA.log}")
                client.command("item $FIRE_CAPE")
                client.await("the cape") { client.inventory(BACKPACK).firstOrNull { it?.id == FIRE_CAPE } }
                client.npcOp(npc.index, Balloons.FLY)
                client.await("the map for the cape") { if (Balloons.MAP in client.openInterfaces()) Unit else null }
                client.ifButton(Balloons.MAP, BalloonStation.ENTRANA.button, 1)
                client.awaitDialogue("the cape's search") { it is DialogueView.Chat && it.text == "[${BalloonLines.ENTRANA_SEARCH.key}]" }
                client.continueDialogue()
                client.awaitDialogue("the cape searched") { it is DialogueView.Message && it.text == "[${BalloonLines.SEARCHED.key}]" }
                client.continueDialogue()
                client.awaitDialogue("the cape refused") { it is DialogueView.Chat && it.text == "[${BalloonLines.ENTRANA_REFUSE.key}]" }
                client.continueDialogue()
                client.await("the refusal closed") { if (client.dialogueView == null) Unit else null }
                assertEquals(here, client.position)
                // Without either, Fly goes straight to the map and the search lets the player through.
                client.command("empty")
                client.command("item ${BalloonStation.ENTRANA.log}")
                client.await("the cape gone") { if (client.inventory(BACKPACK).none { it?.id == FIRE_CAPE || it?.id == PLATEBODY }) Unit else null }
                client.npcOp(npc.index, Balloons.FLY)
                client.await("the map again") { if (Balloons.MAP in client.openInterfaces()) Unit else null }
                client.ifButton(Balloons.MAP, BalloonStation.ENTRANA.button, 1)
                client.awaitDialogue("the search") { it is DialogueView.Chat && it.text == "[${BalloonLines.ENTRANA_SEARCH.key}]" }
                client.continueDialogue()
                client.awaitDialogue("searched") { it is DialogueView.Message && it.text == "[${BalloonLines.SEARCHED.key}]" }
                client.continueDialogue()
                client.awaitAt(BalloonStation.ENTRANA.landing)
            }
        }
    }

    @Test
    fun `a Travel click with no map open does nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel firemaking 99")
                client.command("item ${BalloonStation.CASTLE_WARS.log}")
                val at = BalloonStation.VARROCK.landing
                client.tele(at)
                client.ifButton(Balloons.MAP, BalloonStation.CASTLE_WARS.button, 1)
                // Nor with the map left open after moving away from the basket.
                client.useBasket(BalloonStation.VARROCK)
                client.command("tele 3222 3218 0")
                client.await("away") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.ifButton(Balloons.MAP, BalloonStation.CASTLE_WARS.button, 1)
                client.awaitTicks(Balloons.FLIGHT_TICKS + 2)
                assertEquals(Coord(3222, 3218, 0), client.position)
            }
        }
    }

    private companion object {
        const val ANCHOR = 10_000
        const val PLATEBODY = 1127
        const val FIRE_CAPE = 6570
        const val STAB_DEFENCE = 5
        const val BACKPACK = 93
    }
}
