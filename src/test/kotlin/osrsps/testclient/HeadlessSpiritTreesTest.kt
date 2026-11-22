package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.Destination
import osrsps.game.content.SpiritTreeLines
import osrsps.game.content.SpiritTreeSpot
import osrsps.game.content.SpiritTrees
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * The spirit trees (travel networks step T4) through the headless client: the travel menu from Travel and from Talk-to,
 * a trip by way of the bark box, Last-destination, and the trees' refusals (the tree you're at, a place with no tree,
 * your house, Laguna Aurorae). The rl-client script test-runner/examples/spirit-trees.json does the same at the Grand
 * Exchange in the real client, with the cache's types.
 */
class HeadlessSpiritTreesTest {
    private fun tree(id: Int, spot: SpiritTreeSpot) = ZoneLoc(id, LocShape.CENTREPIECE_STRAIGHT, 0, spot.tree!!.x, spot.tree!!.z, 0)

    private val ge = tree(SpiritTrees.SMALL_TREE, SpiritTreeSpot.GRAND_EXCHANGE)
    private val feldip = tree(SpiritTrees.SMALL_TREE, SpiritTreeSpot.FELDIP_HILLS)
    private val stronghold = tree(SpiritTrees.STRONGHOLD_TREE, SpiritTreeSpot.GNOME_STRONGHOLD)
    private val village = tree(SpiritTrees.BIG_TREE, SpiritTreeSpot.TREE_GNOME_VILLAGE)

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val trees = listOf(ge, feldip, stronghold, village)
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                for (loc in trees) markLoaded(loc.x shr 6, loc.z shr 6)
            },
            random = GameRandom(7),
            npcTypes = NpcTypes.of(
                NpcType(SpiritTrees.SMALL_HEAD, "Spirit tree"),
                NpcType(SpiritTrees.BIG_HEAD, "Spirit tree"),
            ),
            locTypes = LocTypes(
                listOf(
                    LocType(SpiritTrees.SMALL_TREE, "Spirit tree", OPS, LocCollisionType()),
                    LocType(SpiritTrees.STRONGHOLD_TREE, "Spirit tree", OPS, LocCollisionType()),
                    // Tree Gnome Village's tree is a multiloc on bolren_got_orbs, as in the cache; its options show from 2.
                    LocType(
                        SpiritTrees.BIG_TREE, "null", collision = LocCollisionType(), multiloc = true, multiVarbit = BOLREN_GOT_ORBS,
                        multiLocs = listOf(-1, -1, VILLAGE_FORM, VILLAGE_FORM, -1),
                    ),
                    LocType(VILLAGE_FORM, "Spirit tree", OPS, LocCollisionType()),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { trees.forEach(::add) },
            varTypes = VarTypes(
                6_000,
                mapOf(
                    SpiritTrees.VARBIT_PREVIOUS to VarbitType(TREES_VARP, 0, 4),
                    BOLREN_GOT_ORBS to VarbitType(TREES_VARP, 8, 10),
                ),
            ),
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

    /** Waits for the travel menu, checks its entries are the fourteen places' keys in order, and picks [spot]. */
    private fun HeadlessClient.pick(spot: SpiritTreeSpot) {
        await("the travel menu") { if (SpiritTrees.MENU in openInterfaces()) Unit else null }
        val script = await("the menu's script") { clientScripts().lastOrNull { it.id == SpiritTrees.MENU_SCRIPT } }
        assertEquals(SpiritTreeSpot.entries.map { "[${it.label.key}]" }, (script.args[1] as String).split('|'))
        resumePause(SpiritTrees.MENU, SpiritTrees.MENU_ENTRIES, spot.ordinal)
    }

    /** Continues the bark box, then waits for the landing at [to]. */
    private fun HeadlessClient.trip(to: SpiritTreeSpot) {
        awaitDialogue("the bark box") { it is DialogueView.Message && it.text == "[${SpiritTreeLines.BARK.key}]" }
        continueDialogue()
        awaitAt(to.landing!!)
        assertEquals(to.number, varp(TREES_VARP) and 0x1F)
    }

    /** Waits for the tree's chathead to say [line]. */
    private fun HeadlessClient.treeSays(line: osrsps.game.dialogue.Line) =
        awaitDialogue(line.key) { it is DialogueView.Chat && it.text == "[${line.key}]" }

    @Test
    fun `Travel opens the menu and a pick goes there by way of the bark box, and Last-destination goes back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(SpiritTreeSpot.GRAND_EXCHANGE.landing!!)
                client.locOp(SpiritTrees.SMALL_TREE, ge.x, ge.z, SpiritTrees.TRAVEL)
                client.pick(SpiritTreeSpot.FELDIP_HILLS)
                client.trip(SpiritTreeSpot.FELDIP_HILLS)
                assertFalse(SpiritTrees.MENU in client.openInterfaces())
                // Feldip's Last-destination names Feldip itself: the tree is already there.
                client.locOp(SpiritTrees.SMALL_TREE, feldip.x, feldip.z, SpiritTrees.LAST_DESTINATION)
                client.treeSays(SpiritTreeLines.ALREADY_HERE)
                client.continueDialogue()
                // On to the Stronghold, then from the Grand Exchange's tree Last-destination goes back there.
                client.locOp(SpiritTrees.SMALL_TREE, feldip.x, feldip.z, SpiritTrees.TRAVEL)
                client.pick(SpiritTreeSpot.GNOME_STRONGHOLD)
                client.trip(SpiritTreeSpot.GNOME_STRONGHOLD)
                client.tele(SpiritTreeSpot.GRAND_EXCHANGE.landing!!)
                client.locOp(SpiritTrees.SMALL_TREE, ge.x, ge.z, SpiritTrees.LAST_DESTINATION)
                client.trip(SpiritTreeSpot.GNOME_STRONGHOLD)
            }
        }
    }

    @Test
    fun `Last-destination before any trip does nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                val at = SpiritTreeSpot.GRAND_EXCHANGE.landing!!
                client.tele(at)
                client.locOp(SpiritTrees.SMALL_TREE, ge.x, ge.z, SpiritTrees.LAST_DESTINATION)
                client.awaitTicks(4)
                assertEquals(null, client.dialogueView)
                assertEquals(Coord(at.x, at.z, at.level), client.position)
            }
        }
    }

    @Test
    fun `the trees refuse their own place, places with no tree, your house and Laguna Aurorae`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                val at = SpiritTreeSpot.GRAND_EXCHANGE.landing!!
                client.tele(at)
                for ((spot, line) in listOf(
                    SpiritTreeSpot.GRAND_EXCHANGE to SpiritTreeLines.ALREADY_HERE,
                    SpiritTreeSpot.PORT_SARIM to SpiritTreeLines.NO_BRETHREN,
                    SpiritTreeSpot.POISON_WASTE to SpiritTreeLines.NO_BRETHREN,
                    SpiritTreeSpot.YOUR_HOUSE to SpiritTreeLines.NOT_IN_HOUSE,
                    SpiritTreeSpot.LAGUNA_AURORAE to SpiritTreeLines.LAGUNA_RESTRICTED,
                )) {
                    client.locOp(SpiritTrees.SMALL_TREE, ge.x, ge.z, SpiritTrees.TRAVEL)
                    client.pick(spot)
                    client.treeSays(line)
                    client.continueDialogue()
                    if (line == SpiritTreeLines.LAGUNA_RESTRICTED) {
                        client.awaitDialogue("the visit box") { it is DialogueView.Message && it.text == "[${SpiritTreeLines.LAGUNA_VISIT.key}]" }
                        client.continueDialogue()
                    }
                    client.await("the dialogue closed") { if (client.dialogueView == null) Unit else null }
                    assertEquals(Coord(at.x, at.z, at.level), client.position)
                    assertEquals(0, client.varp(TREES_VARP) and 0x1F)
                }
            }
        }
    }

    @Test
    fun `Talk-to greets, and Where can I go opens the menu, from Tree Gnome Village's tree once its quest is done`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                // Every account has finished Tree Gnome Village: bolren_got_orbs at 2, where the tree's options show.
                assertEquals(2, (client.varp(TREES_VARP) ushr 8) and 7)
                client.tele(SpiritTreeSpot.TREE_GNOME_VILLAGE.landing!!)
                client.locOp(SpiritTrees.BIG_TREE, village.x, village.z, SpiritTrees.TALK)
                client.treeSays(SpiritTreeLines.GREETING)
                client.continueDialogue()
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.awaitDialogue("the player's line") { it is DialogueView.Chat && it.text == "[${SpiritTreeLines.WHERE.key}]" }
                client.continueDialogue()
                client.treeSays(SpiritTreeLines.RELATED)
                client.continueDialogue()
                client.pick(SpiritTreeSpot.GRAND_EXCHANGE)
                client.trip(SpiritTreeSpot.GRAND_EXCHANGE)
                // No thanks ends it.
                client.locOp(SpiritTrees.SMALL_TREE, ge.x, ge.z, SpiritTrees.TALK)
                client.treeSays(SpiritTreeLines.GREETING)
                client.continueDialogue()
                client.awaitDialogue("the options") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.awaitDialogue("the player's no") { it is DialogueView.Chat && it.text == "[${SpiritTreeLines.NO_THANKS.key}]" }
                client.continueDialogue()
                client.await("the dialogue closed") { if (client.dialogueView == null) Unit else null }
                assertFalse(SpiritTrees.MENU in client.openInterfaces())
            }
        }
    }

    private companion object {
        val OPS = listOf("Travel", "Talk-to", "Last-destination", null, null)
        const val TREES_VARP = 3_000
        const val BOLREN_GOT_ORBS = 598
        const val VILLAGE_FORM = 26261
    }
}
