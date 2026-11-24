package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.BalloonLines
import osrsps.game.content.BalloonStation
import osrsps.game.content.Balloons
import osrsps.game.content.Destination
import osrsps.game.content.LogStorage
import osrsps.game.content.LogStorageLines
import osrsps.game.dialogue.Chatbox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * The balloon log storage (travel networks step T6) through the headless client: Store asks which log and how many,
 * takes noted logs and stops at 100; using a log on the crate stores that log; Check gives each count; a trip with no
 * log in the backpack is paid from storage, and the ask preference asks which to use. The rl-client script
 * test-runner/examples/log-storage.json stores willow logs at Castle Wars and flies to Varrock on them in the real client.
 */
class HeadlessLogStorageTest {
    private val stations = listOf(BalloonStation.VARROCK, BalloonStation.CASTLE_WARS)

    /** Each station's crate as the 242 map places it. */
    private val crates = mapOf(
        BalloonStation.CASTLE_WARS to ZoneLoc(31575, LocShape.CENTREPIECE_STRAIGHT, 3, 2458, 3110, 0),
        BalloonStation.VARROCK to ZoneLoc(31578, LocShape.CENTREPIECE_STRAIGHT, 0, 3299, 3486, 0),
    )

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply { for (station in stations) markLoaded(station.basketTile.x shr 6, station.basketTile.z shr 6) },
            random = GameRandom(7),
            objTypes = ObjTypes.of(
                ObjType(WILLOW, "Willow logs", weight = 2000, certlink = WILLOW_NOTE),
                ObjType(WILLOW_NOTE, "Willow logs", stackable = true, certlink = WILLOW, certtemplate = 799),
                ObjType(YEW, "Yew logs", weight = 1500),
            ),
            locTypes = LocTypes(
                (
                    stations.map { LocType(it.basket, "Basket", listOf("Use", null, null, null, null), LocCollisionType(width = 2, length = 2)) } +
                        crates.values.map { LocType(it.id, "Log storage", listOf("Store", "Preferences", "Check", null, null), LocCollisionType()) }
                    ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                stations.forEach { add(ZoneLoc(it.basket, LocShape.CENTREPIECE_STRAIGHT, 0, it.basketTile.x, it.basketTile.z, 0)) }
                crates.values.forEach { add(it) }
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(to: Destination) {
        command("tele ${to.x} ${to.z} ${to.level}")
        await("at $to") { position?.takeIf { it == Coord(to.x, to.z, to.level) } }
        awaitTicks(2)
    }

    private fun HeadlessClient.crate(station: BalloonStation, op: Int) {
        val crate = crates.getValue(station)
        locOp(crate.id, crate.x, crate.z, op)
    }

    private fun HeadlessClient.useOnCrate(station: BalloonStation, obj: Int) {
        val crate = crates.getValue(station)
        val slot = inventory(BACKPACK).indexOfFirst { it?.id == obj }
        locTarget(crate.id, crate.x, crate.z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slot, obj)
    }

    private fun HeadlessClient.amount(count: Int, after: Int) {
        val prompt = "[${LogStorageLines.STORE_HOW_MANY.key}]"
        await("the amount prompt") { clientScripts().drop(after).firstOrNull { it.args.firstOrNull() == prompt } }
        resumeCount(count)
    }

    private fun HeadlessClient.stored(text: String) {
        await(text) { if (dialogueView is DialogueView.Other && text(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_TEXT) == text) Unit else null }
        resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
        await("the box closed") { if (dialogueView == null) Unit else null }
    }

    private fun HeadlessClient.seen(text: String, after: Int = 0) =
        await(text) { messages().drop(after).firstOrNull { it.text == text } }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count.toLong() }

    private fun check(logs: Int, willow: Int, yew: Int) =
        "[${LogStorageLines.CHECK.key} logs=$logs oak=0 willow=$willow yew=$yew magic=0]"

    @Test
    fun `Store asks which log and how many, takes noted logs and stops at 100`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(BalloonStation.CASTLE_WARS.landing)
                client.crate(BalloonStation.CASTLE_WARS, LogStorage.STORE)
                client.seen("[${LogStorageLines.NONE.key}]")
                client.command("item $WILLOW 3")
                client.command("item $YEW")
                client.await("the yew") { client.inventory(BACKPACK).firstOrNull { it?.id == YEW } }
                client.crate(BalloonStation.CASTLE_WARS, LogStorage.STORE)
                client.awaitDialogue("which logs") {
                    it is DialogueView.Menu && it.title == "[${LogStorageLines.STORE_WHICH.key}]" && it.options == listOf("Willow logs", "Yew logs")
                }
                var scripts = client.clientScripts().size
                client.chooseOption(1)
                client.amount(2, scripts)
                client.stored("[${LogStorageLines.STORED.key} logs=Willow logs amount=2]")
                assertEquals(1L, client.count(WILLOW))
                // One yew: no amount asked.
                client.useOnCrate(BalloonStation.CASTLE_WARS, YEW)
                client.stored("[${LogStorageLines.STORED.key} logs=Yew logs amount=1]")
                assertEquals(0L, client.count(YEW))
                // Noted willow logs, more than there is room for: the crate takes 98 and stops at 100.
                client.command("item $WILLOW_NOTE 150")
                client.await("the notes") { client.inventory(BACKPACK).firstOrNull { it?.id == WILLOW_NOTE } }
                scripts = client.clientScripts().size
                client.useOnCrate(BalloonStation.CASTLE_WARS, WILLOW_NOTE)
                client.amount(150, scripts)
                client.stored("[${LogStorageLines.STORED.key} logs=Willow logs amount=100]")
                assertEquals(52L, client.count(WILLOW_NOTE))
                val before = client.messages().size
                client.useOnCrate(BalloonStation.CASTLE_WARS, WILLOW)
                client.seen("[${LogStorageLines.FULL.key}]", before)
                // Stored at one crate, counted at another.
                client.tele(BalloonStation.VARROCK.landing)
                client.crate(BalloonStation.VARROCK, LogStorage.CHECK)
                client.awaitDialogue("the counts") { it is DialogueView.Message && it.text == check(0, 100, 1) }
            }
        }
    }

    @Test
    fun `a trip is paid from storage, and the ask preference asks which logs to use`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel firemaking 99")
                client.command("item $WILLOW 2")
                client.tele(BalloonStation.CASTLE_WARS.landing)
                val scripts = client.clientScripts().size
                client.crate(BalloonStation.CASTLE_WARS, LogStorage.STORE)
                client.amount(2, scripts)
                client.stored("[${LogStorageLines.STORED.key} logs=Willow logs amount=2]")

                // No willow in the backpack: the trip burns a stored one and says how many are left.
                client.locOp(BalloonStation.CASTLE_WARS.basket, BalloonStation.CASTLE_WARS.basketTile.x, BalloonStation.CASTLE_WARS.basketTile.z, Balloons.USE)
                client.await("the map") { if (Balloons.MAP in client.openInterfaces()) Unit else null }
                client.ifButton(Balloons.MAP, BalloonStation.VARROCK.button, 1)
                client.seen("[${LogStorageLines.LEFT.key} logs=[${BalloonLines.WILLOW_LOGS.key}] amount=1]")
                client.await("in Varrock", timeoutMillis = 10_000) { client.position?.takeIf { it == Coord(BalloonStation.VARROCK.landing.x, BalloonStation.VARROCK.landing.z, 0) } }

                // Ask every time: with willow in both, the player picks storage.
                client.crate(BalloonStation.VARROCK, LogStorage.PREFERENCES)
                client.awaitDialogue("the preferences box") { it is DialogueView.Message && it.text == "[${LogStorageLines.PREFERENCES.key}]" }
                client.continueDialogue()
                client.awaitDialogue("the preferences") {
                    it is DialogueView.Menu && it.options == listOf("[${LogStorageLines.STORAGE_FIRST.key}]", "[${LogStorageLines.ASK.key}]")
                }
                client.chooseOption(2)
                client.await("the menu closed") { if (client.dialogueView == null) Unit else null }
                client.command("item $WILLOW")
                client.tele(BalloonStation.CASTLE_WARS.landing)
                client.locOp(BalloonStation.CASTLE_WARS.basket, BalloonStation.CASTLE_WARS.basketTile.x, BalloonStation.CASTLE_WARS.basketTile.z, Balloons.USE)
                client.await("the map again") { if (Balloons.MAP in client.openInterfaces()) Unit else null }
                client.ifButton(Balloons.MAP, BalloonStation.VARROCK.button, 1)
                client.awaitDialogue("which logs to use") {
                    it is DialogueView.Menu && it.options == listOf("[${LogStorageLines.USE_INVENTORY.key}]", "[${LogStorageLines.USE_STORAGE.key}]")
                }
                client.chooseOption(2)
                client.seen("[${LogStorageLines.LEFT.key} logs=[${BalloonLines.WILLOW_LOGS.key}] amount=0]")
                client.await("in Varrock again", timeoutMillis = 10_000) { client.position?.takeIf { it == Coord(BalloonStation.VARROCK.landing.x, BalloonStation.VARROCK.landing.z, 0) } }
                assertEquals(1L, client.count(WILLOW))
                // The preference left at ask, the options leave it out.
                client.crate(BalloonStation.VARROCK, LogStorage.PREFERENCES)
                client.awaitDialogue("the preferences box again") { it is DialogueView.Message && it.text == "[${LogStorageLines.PREFERENCES.key}]" }
                client.continueDialogue()
                client.awaitDialogue("the preferences again") {
                    it is DialogueView.Menu && it.options == listOf("[${LogStorageLines.INVENTORY_FIRST.key}]", "[${LogStorageLines.STORAGE_FIRST.key}]")
                }
            }
        }
    }

    private companion object {
        const val WILLOW = 1519
        const val WILLOW_NOTE = 1520
        const val YEW = 1515
        const val BACKPACK = 93
    }
}
