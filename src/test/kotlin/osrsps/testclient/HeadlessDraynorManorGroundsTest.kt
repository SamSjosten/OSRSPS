package osrsps.testclient

import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.DraynorManorGrounds
import osrsps.game.content.ManorGroundsLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Draynor piece step 11 through the headless client and the registered handlers: the manor's front doors, compost heap
 * and fountain placed as the 241 cache has them on open ground, Veronica's talk, an undead tree's Chop and swipe, and
 * the vines. The rl-client script test-runner/examples/draynor-manor-grounds.json does the same on the real map.
 */
class HeadlessDraynorManorGroundsTest {
    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = 1, length = 1))

    private val types = LocTypes(
        listOf(
            type(DraynorManorGrounds.FRONT_DOOR_L, "Large door", "Open"),
            type(DraynorManorGrounds.FRONT_DOOR_R, "Large door", "Open"),
            type(DraynorManorGrounds.COMPOST_HEAP, "Compost heap", "Search"),
            type(DraynorManorGrounds.FOUNTAIN, "Fountain", "Search"),
        ).associateBy { it.id },
    )

    private val locs = listOf(
        ZoneLoc(DraynorManorGrounds.FRONT_DOOR_L, LocShape.WALL_STRAIGHT, 1, 3108, 3353, 0),
        ZoneLoc(DraynorManorGrounds.FRONT_DOOR_R, LocShape.WALL_STRAIGHT, 1, 3109, 3353, 0),
        ZoneLoc(DraynorManorGrounds.COMPOST_HEAP, LocShape.CENTREPIECE_STRAIGHT, 0, 3084, 3360, 0),
        ZoneLoc(DraynorManorGrounds.FOUNTAIN, LocShape.CENTREPIECE_STRAIGHT, 0, 3087, 3334, 0),
    )

    private val veronica = NpcType(DraynorManorGrounds.VERONICA, "Veronica", ops = listOf("Talk-to", null, null, null, null))
    private val choppable = NpcType(DraynorManorGrounds.UNDEAD_TREE_CHOPPABLE, "Undead tree", ops = listOf("Chop", null, null, null, null))
    private val unchoppable = NpcType(UNCHOPPABLE, "Tree")

    /** The map's undead tree: [unchoppable] until Animal Magnetism is done (varbit 3185 at 240), [choppable] after. */
    private val tree = NpcType(
        DraynorManorGrounds.UNDEAD_TREE, "null", multiVarbit = ANMA_MAIN,
        multiNpcs = IntArray(242) { if (it == 240) DraynorManorGrounds.UNDEAD_TREE_CHOPPABLE else if (it == 0) UNCHOPPABLE else -1 },
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(BRONZE_AXE + 1)
        objs[DraynorManorGrounds.SPADE] = ObjType(DraynorManorGrounds.SPADE, "Spade", iops = listOf(null, null, null, null, "Drop"))
        objs[BRONZE_AXE] = ObjType(BRONZE_AXE, "Bronze axe", iops = listOf(null, null, null, null, "Drop"))
        val map = CollisionMap().apply {
            markLoaded(3108 shr 6, 3353 shr 6)
            markLoaded(3084 shr 6, 3360 shr 6)
        }
        World(
            crcs, map, objTypes = ObjTypes(objs), locTypes = types, staticLocs = StaticLocs().apply { locs.forEach(::add) },
            npcTypes = NpcTypes.of(veronica, choppable, unchoppable, tree),
            npcSpawns = listOf(NpcSpawn(veronica, 3110, 3330, source = "test"), NpcSpawn(tree, 3107, 3337, source = "test")),
            varTypes = VarTypes(6_000, mapOf(ANMA_MAIN to VarbitType(ANMA_VARP, 0, 7))),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.seen(line: osrsps.game.dialogue.Line) = DialogueText.NONE[line].let { text ->
        await(text) { messages().firstOrNull { it.text == text } }
    }

    @Test
    fun `the front doors let the player in from the south, slam shut and won't open from inside`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3108, 3352, 0)
                client.locOp(DraynorManorGrounds.FRONT_DOOR_L, 3108, 3353, DraynorManorGrounds.OPEN)
                assertEquals(Coord(3108, 3354, 0), client.await("inside") { client.position?.takeIf { it.z == 3354 } })
                client.seen(ManorGroundsLines.DOORS_SLAM)
                client.locOp(DraynorManorGrounds.FRONT_DOOR_R, 3109, 3353, DraynorManorGrounds.OPEN)
                client.seen(ManorGroundsLines.DOORS_WONT_OPEN)
                assertEquals(3354, client.position!!.z)
            }
        }
    }

    @Test
    fun `the compost heap won't be searched by hand, gives a spade nothing, and the fountain is full of dead fish`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3085, 3360, 0)
                client.locOp(DraynorManorGrounds.COMPOST_HEAP, 3084, 3360, DraynorManorGrounds.SEARCH)
                client.awaitDialogue("the message box") { it is DialogueView.Message && it.text == "[draynor_manor.compost_hands]" }
                client.continueDialogue()
                client.command("item ${DraynorManorGrounds.SPADE}")
                val slot = client.await("the spade") { client.inventory(BACKPACK).indexOfFirst { it?.id == DraynorManorGrounds.SPADE }.takeIf { it >= 0 } }
                client.locTarget(DraynorManorGrounds.COMPOST_HEAP, 3084, 3360, InventoryTab.INTERFACE, InventoryTab.ITEMS, slot, DraynorManorGrounds.SPADE)
                client.seen(ManorGroundsLines.COMPOST_DIG)
                client.seen(ManorGroundsLines.COMPOST_NOTHING)

                client.tele(3088, 3334, 0)
                client.locOp(DraynorManorGrounds.FOUNTAIN, 3087, 3334, DraynorManorGrounds.SEARCH)
                client.awaitDialogue("the dead fish") { it is DialogueView.Chat && it.text == "[draynor_manor.fountain_fish]" }
                client.continueDialogue()
            }
        }
    }

    @Test
    fun `Veronica thanks the player for rescuing Ernest`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3110, 3331, 0)
                val npc = client.await("Veronica in view") { client.npcs().firstOrNull { it.id == DraynorManorGrounds.VERONICA } }
                client.npcOp(npc.index, DraynorManorGrounds.TALK_TO)
                for (key in listOf("thanks", "where", "warty")) {
                    client.awaitDialogue("veronica.$key") { it is DialogueView.Chat && it.text == "[veronica.$key]" }
                    client.continueDialogue()
                }
            }
        }
    }

    @Test
    fun `an undead tree after Animal Magnetism bounces an axe, needs one, and swipes a player beside it`(@TempDir dir: Path) {
        server(dir).use { server ->
            val swipes = CopyOnWriteArrayList<Int>()
            server.world.manorHazards.swipe = { _, damage -> swipes += damage }
            server.login("dan").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("varbit $ANMA_MAIN 240")
                client.tele(3108, 3337, 0)
                val npc = client.await("the tree in view") { client.npcs().firstOrNull { it.id == DraynorManorGrounds.UNDEAD_TREE } }
                client.npcOp(npc.index, DraynorManorGrounds.CHOP)
                client.seen(ManorGroundsLines.TREE_NO_AXE)
                client.command("item $BRONZE_AXE")
                client.await("the axe") { if (client.inventory(BACKPACK).any { it?.id == BRONZE_AXE }) Unit else null }
                client.npcOp(npc.index, DraynorManorGrounds.CHOP)
                client.seen(ManorGroundsLines.TREE_BOUNCE)
                client.await("two swipes") { if (swipes.size >= 2) Unit else null }
                assertTrue(swipes.all { it in 0..DraynorManorGrounds.TREE_MAX_HIT }, swipes.toString())
                // Two tiles off, it can't reach.
                client.tele(3109, 3339, 0)
                val before = swipes.size
                Thread.sleep(800) // eight ticks at the test server's 100 ms
                assertTrue(swipes.size <= before + 1, "a swipe or none in flight, then none: $swipes")
            }
        }
    }

    @Test
    fun `the vines whip a player in their zone every five ticks and stop once they leave its reach`(@TempDir dir: Path) {
        server(dir).use { server ->
            val whips = CopyOnWriteArrayList<Int>()
            server.world.manorHazards.whip = { _, damage -> whips += damage }
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3115, 3340, 0)
                client.seen(ManorGroundsLines.VINE)
                client.await("two whips") { if (whips.size >= 2) Unit else null }
                assertEquals(listOf(1, 1), whips.take(2))
                client.tele(3125, 3340, 0)
                val after = whips.size
                Thread.sleep(800) // eight ticks at the test server's 100 ms
                assertTrue(whips.size <= after + 1, "a whip or none in flight, then none: $whips")
            }
        }
    }

    private companion object {
        const val ANMA_MAIN = 3185
        /** A free varp for the test's varbit 3185; the cache's base var isn't needed here. */
        const val ANMA_VARP = 4000
        const val UNCHOPPABLE = 4416
        const val BRONZE_AXE = 1351
        const val BACKPACK = 93
    }
}
