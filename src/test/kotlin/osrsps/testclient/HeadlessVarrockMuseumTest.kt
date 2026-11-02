package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.VarrockMuseum
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Varrock step 9a through the headless client: the museum's stairs down to the Natural History floor and back, the
 * back entrance's doors opening as a pair, the cleaning area's gate through its guard, and the curator, the
 * information clerk (with her map) and the back entrance's guard talking every way. The rl-client script
 * test-runner/examples/varrock-museum.json does the same in the real museum.
 */
class HeadlessVarrockMuseumTest {
    private val map = ObjType(VarrockMuseum.MUSEUM_MAP, "Museum map")

    private val talk = listOf("Talk-to", null, null, null, null)
    private val curator = NpcType(VarrockMuseum.CURATOR, "Curator Haig Halen", ops = listOf("Talk-to", null, "Pickpocket", null, null), internalName = "curator")
    private val clerk = NpcType(VarrockMuseum.CLERK, "Information clerk", ops = talk, internalName = "vm_info_booth_lady")
    private val gateGuard = NpcType(VarrockMuseum.GATE_GUARD, "Museum guard", ops = talk, internalName = "vm_museum_guard")
    private val doorGuard = NpcType(VarrockMuseum.DOOR_GUARD, "Museum guard", ops = talk, internalName = "vm_museum_guard3")

    private fun type(id: Int, name: String, op: String?, size: Int = 1) =
        LocType(id, name, ops = listOf(op, null, null, null, null), collision = LocCollisionType(width = size, length = size))

    private val types = LocTypes(
        listOf(
            type(VarrockMuseum.STAIRS_DOWN, "Stairs", "Walk-down", 3),
            type(VarrockMuseum.STAIRS_UP, "Stairs", "Walk-up", 3),
            type(VarrockMuseum.DOOR_LEFT, "Door", "Open"),
            type(VarrockMuseum.DOOR_LEFT_OPEN, "Door", "Close"),
            type(VarrockMuseum.DOOR_RIGHT, "Door", "Open"),
            type(VarrockMuseum.DOOR_RIGHT_OPEN, "Door", "Close"),
            type(VarrockMuseum.GATE, "Gate", "Open"),
        ).associateBy { it.id },
    )

    /** As the 241 map has them. */
    private val stairsDown = ZoneLoc(VarrockMuseum.STAIRS_DOWN, LocShape.CENTREPIECE_STRAIGHT, 1, 3255, 3451, 0)
    private val stairsUp = ZoneLoc(VarrockMuseum.STAIRS_UP, LocShape.CENTREPIECE_STRAIGHT, 0, 1758, 4959, 0)
    private val doorLeft = ZoneLoc(VarrockMuseum.DOOR_LEFT, LocShape.WALL_STRAIGHT, 1, 3265, 3441, 0)
    private val doorRight = ZoneLoc(VarrockMuseum.DOOR_RIGHT, LocShape.WALL_STRAIGHT, 1, 3264, 3441, 0)
    private val gate = ZoneLoc(VarrockMuseum.GATE, LocShape.WALL_STRAIGHT, 1, VarrockMuseum.GATE_X, VarrockMuseum.GATE_Z, 0)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3255 shr 6, 3451 shr 6); markLoaded(3264 shr 6, 3440 shr 6); markLoaded(1758 shr 6, 4959 shr 6) },
            objTypes = ObjTypes.of(map), locTypes = types,
            staticLocs = StaticLocs().apply { listOf(stairsDown, stairsUp, doorLeft, doorRight, gate).forEach(::add) },
            npcTypes = NpcTypes.of(curator, clerk, gateGuard, doorGuard),
            npcSpawns = listOf(
                NpcSpawn(curator, 3257, 3449, wander = 0, source = "test"),
                NpcSpawn(clerk, 3253, 3454, wander = 0, source = "test"),
                NpcSpawn(gateGuard, 3260, 3447, wander = 0, source = "test"),
                NpcSpawn(doorGuard, 3263, 3440, wander = 0, source = "test"),
            ),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int = 0) {
        command("tele $x $z $level")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int) =
        assertEquals(Coord(x, z, 0), await("$x, $z") { position?.takeIf { it == Coord(x, z, 0) } })

    private fun HeadlessClient.talkTo(id: Int) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, VarrockMuseum.TALK_TO)
    }

    private fun HeadlessClient.chat(key: String) {
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
        continueDialogue()
    }

    private fun HeadlessClient.pick(option: Int, vararg keys: String) {
        val texts = keys.map { "[$it]" }
        awaitDialogue("the menu $texts") { it is DialogueView.Menu && it.options == texts }
        chooseOption(option)
    }

    private fun HeadlessClient.over() = await("the talk over") { if (dialogueView == null) Unit else null }

    /** Reads on from each of [steps], a key under [prefix], or "n:key1,key2" to pick option n. */
    private fun HeadlessClient.steps(prefix: String, vararg steps: String) {
        for (step in steps) {
            val menu = step.substringBefore(':', "")
            if (menu.isEmpty()) chat("$prefix.$step") else pick(menu.toInt(), *step.substringAfter(':').split(',').map { "$prefix.$it" }.toTypedArray())
        }
    }

    private fun HeadlessClient.walk(id: Int, prefix: String, vararg steps: String) {
        talkTo(id)
        steps(prefix, *steps)
        over()
    }

    @Test
    fun `the stairs go down to the Natural History floor and back up`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3254, 3452)
                client.locOp(stairsDown.id, stairsDown.x, stairsDown.z, VarrockMuseum.WALK)
                client.awaitAt(1759, 4958)
                client.locOp(stairsUp.id, stairsUp.x, stairsUp.z, VarrockMuseum.WALK)
                client.awaitAt(3254, 3452)
            }
        }
    }

    @Test
    fun `the back entrance's doors open apart, both together`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3264, 3440)
                client.locOp(doorRight.id, doorRight.x, doorRight.z, 1)
                val world = server.world
                client.await("the doors open") { world.locs[3264, 3442, 0, 0]?.takeIf { it.id == VarrockMuseum.DOOR_RIGHT_OPEN } }
                val left = world.locs[3264, 3442, 0, 0]!!
                val right = world.locs[3265, 3442, 0, 0]!!
                assertEquals(VarrockMuseum.DOOR_LEFT_OPEN, right.id)
                // The _r leaf lies on its tile's west edge, the _l leaf on its tile's east edge: apart, not touching.
                assertEquals(0, left.angle)
                assertEquals(2, right.angle)
            }
        }
    }

    @Test
    fun `the gate guard asks once a login, then lets the player into the cleaning area and out again`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3261, 3448)
                // The first Open after login: he asks; looking around outside, the player stays put.
                client.locOp(gate.id, gate.x, gate.z, VarrockMuseum.OPEN)
                client.steps("museum_guard", "gate_ask", "2:gate_in,gate_outside", "gate_outside")
                client.over()
                assertTrue(client.position!!.z > VarrockMuseum.GATE_Z)
                // Asked once: Open now takes the player straight through, with no talk.
                client.locOp(gate.id, gate.x, gate.z, VarrockMuseum.OPEN)
                client.awaitAt(VarrockMuseum.GATE_X, VarrockMuseum.GATE_Z)
                assertEquals(null, client.dialogueView)
                // From inside, Open lets the player out.
                client.locOp(gate.id, gate.x, gate.z, VarrockMuseum.OPEN)
                client.awaitAt(VarrockMuseum.GATE_X, VarrockMuseum.GATE_Z + 1)
                // Talk-to the guard: no question either, through the gate.
                client.talkTo(gateGuard.id)
                client.awaitAt(VarrockMuseum.GATE_X, VarrockMuseum.GATE_Z)
            }
            // A new login: Talk-to asks again, and going in takes the player through.
            server.login("dan").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3261, 3448)
                client.walk(gateGuard.id, "museum_guard", "gate_ask", "1:gate_in,gate_outside")
                client.awaitAt(VarrockMuseum.GATE_X, VarrockMuseum.GATE_Z)
            }
        }
    }

    @Test
    fun `the curator talks every way`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3257, 3450)
                val menu = "fossil_island,news,treasure,medallion"
                client.walk(curator.id, "curator", "greeting", "1:$menu", "fossil_island_said", "fossil_island_answer", "1:fossils_for,drop_topic", "fossils_for", "fossils_for_answer")
                client.walk(curator.id, "curator", "greeting", "1:$menu", "fossil_island_said", "fossil_island_answer", "2:fossils_for,drop_topic", "drop_topic")
                client.walk(curator.id, "curator", "greeting", "2:$menu", "news", "news_1", "news_2")
                client.walk(curator.id, "curator", "greeting", "3:$menu", "treasure", "treasure_1", "treasure_2", "treasure_3")
                client.walk(curator.id, "curator", "greeting", "4:$menu", "no_pendant")
            }
        }
    }

    @Test
    fun `the information clerk talks every way and hands out a map, and the back entrance's guard talks`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3254, 3454)
                val topics = "map,dig_site,timeline,natural_history,kudos"
                val after = "something_else,bye"
                client.walk(
                    clerk.id, "museum_clerk", "welcome",
                    "2:$topics", "dig_site_said", "dig_site_about", "dig_site_clean_1", "dig_site_clean_2", "1:$after",
                    "3:$topics", "timeline_said", "timeline_about", "timeline_help_1", "timeline_help_2", "numbers", "numbers_1", "numbers_2", "numbers_ok", "1:$after",
                    "4:$topics", "natural_history_said", "natural_history_about", "orlando", "1:living,$after", "living_said", "living_answer", "1:$after",
                    "4:$topics", "natural_history_said", "natural_history_about", "orlando", "2:living,$after",
                    "5:$topics", "kudos_said", "kudos_about", "kudos_for", "kudos_for_1", "kudos_for_2", "qualify", "not_qualified", "kudos_thanks", "2:$after",
                    "bye_said", "goodbye",
                )
                client.walk(clerk.id, "museum_clerk", "welcome", "4:$topics", "natural_history_said", "natural_history_about", "orlando", "3:living,$after", "bye_said", "goodbye")
                client.walk(clerk.id, "museum_clerk", "welcome", "4:$topics", "natural_history_said", "natural_history_about", "orlando", "1:living,$after", "living_said", "living_answer", "2:$after", "bye_said", "goodbye")
                client.talkTo(clerk.id)
                client.steps("museum_clerk", "welcome", "1:$topics")
                client.awaitDialogue("the map box") { it is DialogueView.Message && it.text == "[museum_clerk.map_taken]" }
                client.continueDialogue()
                client.over()
                client.await("the museum map") { client.inventory(Inventory.INV).firstOrNull { it?.id == VarrockMuseum.MUSEUM_MAP } }

                client.tele(3263, 3439)
                client.walk(doorGuard.id, "museum_guard", "doors_1", "doors_2", "doors_3", "doors_4", "doors_5", "doors_6")
            }
        }
    }
}
