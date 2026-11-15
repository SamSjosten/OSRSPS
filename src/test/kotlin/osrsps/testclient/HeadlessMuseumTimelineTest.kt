package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.content.Kudos
import osrsps.game.content.MUSEUM_VARBITS
import osrsps.game.content.MuseumTimeline
import osrsps.game.content.MuseumTimelineLines
import osrsps.game.content.VarrockMuseum
import osrsps.game.content.VarrockMuseumPeople
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Kudos step 3 through the headless client: Historian Minas hears every quest story in one talk, filling the timeline
 * displays and giving the five lamps; What Lies Below waits for the Dagon'hai history book, which he keeps; then he hears
 * no new quests. The information clerk's Timeline answer follows: nothing told, some told, all told.
 */
class HeadlessMuseumTimelineTest {
    private val talk = listOf("Talk-to", null, null, null, null)
    private val minas = NpcType(VarrockMuseumPeople.HISTORIAN_MINAS, "Historian Minas", ops = talk, internalName = "vm_timeline_historian")
    private val clerk = NpcType(VarrockMuseum.CLERK, "Information clerk", ops = talk, internalName = "vm_info_booth_lady")

    private val lamps = MuseumTimeline.Story.entries.mapNotNull { it.lamp }

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(30_000)
        for (id in lamps + MuseumTimeline.DAGONHAI_HISTORY) objs[id] = ObjType(id, "Item $id")
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            objTypes = ObjTypes(objs),
            npcTypes = NpcTypes.of(minas, clerk),
            npcSpawns = listOf(NpcSpawn(minas, 3224, 3220, wander = 0, source = "test"), NpcSpawn(clerk, 3230, 3220, wander = 0, source = "test")),
            varTypes = VarTypes(6_000, MUSEUM_VARBITS),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.talkTo(id: Int) = npcOp(await("NPC $id in view") { npcs().firstOrNull { it.id == id } }.index, VarrockMuseumPeople.TALK_TO)

    private fun HeadlessClient.chats(vararg keys: String) {
        for (key in keys) {
            awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
            continueDialogue()
        }
    }

    private fun HeadlessClient.box(key: String) {
        awaitDialogue(key) { it == DialogueView.Message("[$key]") }
        continueDialogue()
    }

    private fun HeadlessClient.pick(option: Int, vararg keys: String) {
        val texts = keys.map { "[$it]" }
        awaitDialogue("the menu $texts") { it is DialogueView.Menu && it.options == texts }
        chooseOption(option)
    }

    private fun HeadlessClient.over() = await("the talk over") { if (dialogueView == null) Unit else null }

    /** Reads through [boxes], each a chat box or a message box; one with the player's name shows it after the key. */
    private fun HeadlessClient.story(boxes: List<MuseumTimelineLines.Box>) {
        for (box in boxes) {
            val key = box.line.key
            if (box.speaker == MuseumTimelineLines.Speaker.BOX) {
                box(key)
                continue
            }
            awaitDialogue(key) { it is DialogueView.Chat && (it.text == "[$key]" || it.text.startsWith("[$key name=")) }
            continueDialogue()
        }
    }

    private fun HeadlessClient.lamp(dov: Boolean = false) {
        chats(if (dov) "historian_minas_timeline.lamp_dov" else "historian_minas_timeline.lamp", "historian_minas_timeline.tat", "historian_minas_timeline.lamp_welcome")
        box("historian_minas_timeline.lamp_received")
    }

    private fun HeadlessClient.information() {
        talkTo(minas.id)
        chats("historian_minas.hello", "historian_minas.welcome")
        pick(2, "historian_minas.museum", "historian_minas.information", "historian_minas.other_sections")
        chats("historian_minas.information_said", "historian_minas.information_1")
    }

    private fun HeadlessClient.clerkTimeline(first: String, second: String) {
        talkTo(clerk.id)
        chats("museum_clerk.welcome")
        pick(3, "museum_clerk.map", "museum_clerk.dig_site", "museum_clerk.timeline", "museum_clerk.natural_history", "museum_clerk.kudos")
        chats("museum_clerk.timeline_said", "museum_clerk.timeline_about", "museum_clerk.$first", "museum_clerk.$second")
        chats("museum_clerk.numbers", "museum_clerk.numbers_1", "museum_clerk.numbers_2", "museum_clerk.numbers_ok")
        pick(2, "museum_clerk.something_else", "museum_clerk.bye")
        chats("museum_clerk.bye_said", "museum_clerk.goodbye")
        over()
    }

    private fun HeadlessClient.carried(id: Int) = inventory(Inventory.INV).count { it?.id == id }

    @Test
    fun `Minas hears every story once, What Lies Below with the book, and the clerk's Timeline answer follows`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("keys on")
                // The clerk's first reward claimed, so she goes straight to her topics at 75 Kudos.
                client.setVarbit(Kudos.VARBIT_REWARD_50, 1)
                client.clerkTimeline("timeline_help_1", "timeline_help_2")

                client.information()
                for (story in MuseumTimeline.Story.entries) {
                    client.story(story.boxes)
                    if (story == MuseumTimeline.Story.WHAT_LIES_BELOW) client.story(MuseumTimelineLines.WHAT_LIES_BELOW_NO_BOOK)
                    if (story.lamp != null) client.lamp(dov = story == MuseumTimeline.Story.DEFENDER_OF_VARROCK)
                }
                client.over()
                client.awaitVarbit(Kudos.VARBIT_KUDOS, 75)
                for (lamp in lamps) assertEquals(1, client.carried(lamp), "lamp $lamp")
                assertEquals(2, client.varbit(MuseumTimeline.VARBIT_STAFF_OF_ARMADYL))
                assertEquals(2, client.varbit(MuseumTimeline.VARBIT_SALVE_KNIGHTS))
                assertEquals(1, client.varbit(MuseumTimeline.VARBIT_SHIELD_SHOWN))
                assertEquals(0, client.varbit(MuseumTimeline.VARBIT_SUROK))
                client.clerkTimeline("timeline_partial_1", "timeline_partial_2")

                // With the book, only What Lies Below is left, and he keeps the book.
                client.command("item ${MuseumTimeline.DAGONHAI_HISTORY}")
                client.await("the book carried") { if (client.carried(MuseumTimeline.DAGONHAI_HISTORY) == 1) Unit else null }
                client.information()
                client.story(MuseumTimeline.Story.WHAT_LIES_BELOW.boxes)
                client.story(MuseumTimelineLines.WHAT_LIES_BELOW_BOOK)
                client.over()
                client.awaitVarbit(Kudos.VARBIT_KUDOS, 80)
                assertEquals(1, client.varbit(MuseumTimeline.VARBIT_SUROK))
                assertEquals(0, client.carried(MuseumTimeline.DAGONHAI_HISTORY))
                for (lamp in lamps) assertEquals(1, client.carried(lamp), "lamp $lamp, not given twice")

                client.information()
                client.chats("historian_minas.no_new_quests", "historian_minas.no_matter", "historian_minas.go_on")
                client.over()
                client.clerkTimeline("timeline_done_1", "timeline_done_2")
            }
        }
    }
}
