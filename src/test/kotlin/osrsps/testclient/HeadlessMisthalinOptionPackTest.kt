package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.Crops
import osrsps.game.content.MisthalinOptionPack
import osrsps.game.content.SearchLines
import osrsps.game.content.SignpostLines
import osrsps.game.content.VarrockReadingLines
import osrsps.game.content.WildBushLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import osrsps.map.CollisionMap

/**
 * Misthalin fixes 3 through the headless client: the wild cadava bush gives two berries and is then empty, the always
 * empty one says so, a signpost opens the compass with its own four lines, the Exam Centre's says its one, the field
 * east of the Varrock mine picks like any other, and the boxes have nothing in them. The rl-client script
 * test-runner/examples/misthalin-fixes-3.json does the same on the live cache.
 */
class HeadlessMisthalinOptionPackTest {
    private fun locType(id: Int, name: String, op: String, opIndex: Int = 1, size: Int = 1) =
        LocType(id, name, ops = List(5) { if (it == opIndex - 1) op else null }, collision = LocCollisionType(width = size, length = size))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Crops.ONION + 1)
        objs[MisthalinOptionPack.CADAVA_BERRIES] = ObjType(MisthalinOptionPack.CADAVA_BERRIES, "Cadava berries", internalName = "cadavaberries")
        objs[Crops.ONION] = ObjType(Crops.ONION, "Onion", internalName = "onion")
        val types = buildList {
            MisthalinOptionPack.CADAVA.forEach { add(locType(it, "Cadava bush", "Pick-from")) }
            add(locType(MisthalinOptionPack.CADAVA_ALWAYS_EMPTY, "Cadava bush", "Pick-from"))
            add(locType(MisthalinOptionPack.SIGNPOST_LUMBRIDGE, "Signpost", "Read", size = 2))
            add(locType(MisthalinOptionPack.SIGNPOST_EXAM_CENTRE, "Signpost", "Read"))
            add(locType(MisthalinOptionPack.BOXES, "Boxes", "Search"))
            add(locType(MisthalinOptionPack.COMBAT_SCROLL, "Combat Scroll", "Read"))
            add(locType(MisthalinOptionPack.VTAM_PLAQUE, "Plaque", "Read"))
            add(locType(AVIUM_ONION, "Onion", "Pick", opIndex = 2))
        }.associateBy { it.id }
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            locTypes = LocTypes(types),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(MisthalinOptionPack.CADAVA[0], LocShape.CENTREPIECE_STRAIGHT, 0, 3220, 3220, 0))
                add(ZoneLoc(MisthalinOptionPack.CADAVA_ALWAYS_EMPTY, LocShape.CENTREPIECE_STRAIGHT, 0, 3218, 3220, 0))
                add(ZoneLoc(MisthalinOptionPack.SIGNPOST_LUMBRIDGE, LocShape.CENTREPIECE_STRAIGHT, 0, 3235, 3228, 0))
                add(ZoneLoc(MisthalinOptionPack.SIGNPOST_EXAM_CENTRE, LocShape.CENTREPIECE_STRAIGHT, 0, 3226, 3222, 0))
                add(ZoneLoc(MisthalinOptionPack.BOXES, LocShape.CENTREPIECE_STRAIGHT, 0, 3224, 3222, 0))
                add(ZoneLoc(MisthalinOptionPack.COMBAT_SCROLL, LocShape.CENTREPIECE_STRAIGHT, 0, 3226, 3218, 0))
                add(ZoneLoc(MisthalinOptionPack.VTAM_PLAQUE, LocShape.CENTREPIECE_STRAIGHT, 0, 3228, 3218, 0))
                add(ZoneLoc(AVIUM_ONION, LocShape.CENTREPIECE_STRAIGHT, 0, 3222, 3224, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    @Test
    fun `a wild cadava bush gives two berries, then has none, the first coming back on time, and the south-western one never has any`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                val (full, one, none) = MisthalinOptionPack.CADAVA
                fun bush() = server.world.locs.at(3220, 3220, 0).single().id

                client.locOp(full, 3220, 3220, MisthalinOptionPack.PICK_FROM)
                client.await("the first berry") { if (client.count(MisthalinOptionPack.CADAVA_BERRIES) == 1) Unit else null }
                assertEquals(one, bush())
                val firstBack = server.world.locs.revertDue(server.world.locs.at(3220, 3220, 0).single())

                client.locOp(one, 3220, 3220, MisthalinOptionPack.PICK_FROM)
                client.await("the second berry") { if (client.count(MisthalinOptionPack.CADAVA_BERRIES) == 2) Unit else null }
                assertEquals(none, bush())
                assertEquals(firstBack, server.world.locs.revertDue(server.world.locs.at(3220, 3220, 0).single()), "picking the second berry put off the first")
                assertTrue(server.world.locs.reverting(3220, 3220, 0), "the bush never grows back")

                client.locOp(none, 3220, 3220, MisthalinOptionPack.PICK_FROM)
                client.seen(DialogueText.NONE[WildBushLines.NO_BERRIES])
                assertEquals(2, client.count(MisthalinOptionPack.CADAVA_BERRIES))

                client.locOp(MisthalinOptionPack.CADAVA_ALWAYS_EMPTY, 3218, 3220, MisthalinOptionPack.PICK_FROM)
                client.seen(DialogueText.NONE[WildBushLines.ALWAYS_EMPTY])
                assertEquals(2, client.count(MisthalinOptionPack.CADAVA_BERRIES))
            }
        }
    }

    @Test
    fun `the Combat Scroll and the VTAM plaque each read out their own box, which then closes`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                for ((id, x, line) in listOf(
                    Triple(MisthalinOptionPack.COMBAT_SCROLL, 3226, VarrockReadingLines.COMBAT_SCROLL),
                    Triple(MisthalinOptionPack.VTAM_PLAQUE, 3228, VarrockReadingLines.VTAM_PLAQUE),
                )) {
                    client.locOp(id, x, 3218, MisthalinOptionPack.READ)
                    val text = DialogueText.NONE[line]
                    client.awaitDialogue(text) { it is DialogueView.Message && it.text == text }
                    client.continueDialogue()
                    client.await("the box to close") { if (client.dialogueView == null) Unit else null }
                }
            }
        }
    }

    @Test
    fun `the signpost west of the Lum bridge opens the compass with its own lines`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.locOp(MisthalinOptionPack.SIGNPOST_LUMBRIDGE, 3235, 3228, MisthalinOptionPack.READ)
                client.await("the compass") { if (MisthalinOptionPack.COMPASS in client.openInterfaces()) Unit else null }
                fun text(component: Int) = client.await("component $component's text") { client.text(MisthalinOptionPack.COMPASS, component) }
                assertEquals(DialogueText.NONE[SignpostLines.WEST_LUM_NORTH], text(MisthalinOptionPack.COMPASS_NORTH))
                assertEquals(DialogueText.NONE[SignpostLines.WEST_LUM_EAST], text(MisthalinOptionPack.COMPASS_EAST))
                assertEquals(DialogueText.NONE[SignpostLines.WEST_LUM_SOUTH], text(MisthalinOptionPack.COMPASS_SOUTH))
                assertEquals(DialogueText.NONE[SignpostLines.WEST_LUM_WEST], text(MisthalinOptionPack.COMPASS_WEST))
            }
        }
    }

    @Test
    fun `the Exam Centre's signpost, the boxes and the field's onion`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.locOp(MisthalinOptionPack.SIGNPOST_EXAM_CENTRE, 3226, 3222, MisthalinOptionPack.READ)
                client.seen(DialogueText.NONE[SignpostLines.EXAM_CENTRE])

                client.locOp(MisthalinOptionPack.BOXES, 3224, 3222, MisthalinOptionPack.SEARCH)
                client.seen(DialogueText.NONE[SearchLines.BOXES_EMPTY])

                client.locOp(AVIUM_ONION, 3222, 3224, Crops.PICK)
                client.seen(DialogueText.NONE[Crops.ONIONS.picked])
                client.await("an onion") { if (client.count(Crops.ONION) == 1) Unit else null }
                assertTrue(server.world.locs.find(AVIUM_ONION, 3222, 3224, 0) == null, "the onion is still there")
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val AVIUM_ONION = 51837
    }
}
