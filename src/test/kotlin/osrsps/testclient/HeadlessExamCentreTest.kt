package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.ExamCentre
import osrsps.game.dialogue.Chatbox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Misthalin piece 7 step 10 through the headless client: an examiner giving a lost trowel back, the Researcher, Terry
 * Balando's menu and finds used on him (nuggets for gold ore, liquid to nitroglycerin, a muddy tray handed back), the
 * Exam Centre's cupboards and a bookcase, the two winches down to the Digsite Dungeon with their Agility check and the
 * ropes back up, and Doug Deeping. The rl-client script test-runner/examples/exam-centre.json does the same on the real
 * map.
 */
class HeadlessExamCentreTest {
    private fun npc(id: Int, name: String, vararg ops: String?) =
        NpcType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, internalName = "test_$id")

    private val examiner = npc(ExamCentre.EXAMINERS.first(), "Examiner", "Talk-to")
    private val researcher = npc(ExamCentre.RESEARCHER, "Researcher", "Talk-to")
    private val terry = npc(ExamCentre.TERRY, "Archaeological expert", "Talk-to")
    private val doug = npc(ExamCentre.DOUG, "Doug Deeping", "Talk-to", null, "Steal-from")

    private val cupboardPick = ZoneLoc(ExamCentre.CUPBOARD_PICK, LocShape.CENTREPIECE_STRAIGHT, 0, 3355, 3337, 0)
    private val bookcase = ZoneLoc(ExamCentre.BOOKCASES.first(), LocShape.CENTREPIECE_STRAIGHT, 0, 3366, 3332, 0)
    private val westWinch = ZoneLoc(ExamCentre.WEST_WINCH, LocShape.CENTREPIECE_STRAIGHT, 0, 3352, 3420, 0)
    private val privateWinch = ZoneLoc(ExamCentre.PRIVATE_WINCH, LocShape.CENTREPIECE_STRAIGHT, 0, 3370, 3430, 0)
    private val ropeWest = ZoneLoc(ExamCentre.ROPE_WEST, LocShape.CENTREPIECE_STRAIGHT, 0, 3369, 9762, 0)
    private val ropePrivate = ZoneLoc(ExamCentre.ROPE_PRIVATE, LocShape.CENTREPIECE_STRAIGHT, 0, 3352, 9752, 0)

    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = 1, length = 1, interactType = 1))

    private fun server(dir: Path, spawns: List<Triple<NpcType, Int, Int>>) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(5000)
        for (id in listOf(ExamCentre.ROPE, ExamCentre.TROWEL, ExamCentre.ROCK_PICK, ExamCentre.SPECIMEN_JAR, ExamCentre.GOLD_ORE, ExamCentre.UNIDENTIFIED_LIQUID, ExamCentre.NITROGLYCERIN, 671, 679)) {
            objs[id] = ObjType(id, "Item $id")
        }
        objs[ExamCentre.NUGGETS] = ObjType(ExamCentre.NUGGETS, "Nuggets", stackable = true)
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(52, 52)
                markLoaded(52, 53)
                markLoaded(52, 152)
            },
            objTypes = ObjTypes(objs),
            npcTypes = NpcTypes.of(examiner, researcher, terry, doug),
            npcSpawns = spawns.map { (type, x, z) -> NpcSpawn(type, x, z, source = "test") },
            locTypes = LocTypes(
                listOf(
                    type(ExamCentre.CUPBOARD_PICK, "Cupboard", "Open"),
                    type(ExamCentre.CUPBOARD_PICK_OPEN, "Cupboard", "Close", "Search"),
                    type(ExamCentre.BOOKCASES.first(), "Bookcase", "Search"),
                    type(ExamCentre.WEST_WINCH, "Winch", "Operate"),
                    type(ExamCentre.PRIVATE_WINCH, "Winch", "Operate"),
                    type(ExamCentre.ROPE_WEST, "Rope", "Climb-up"),
                    type(ExamCentre.ROPE_PRIVATE, "Rope", "Climb-up"),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { listOf(cupboardPick, bookcase, westWinch, privateWinch, ropeWest, ropePrivate).forEach(::add) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.ready() = await("the login rebuild") { position }

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
        val menu = awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
        assertEquals(keys.map { "[$it]" }, menu.options)
        chooseOption(option)
    }

    private fun HeadlessClient.awaitClosed() = await("the dialogue to close") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.count(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.awaitCount(id: Int, count: Int) = await("$count of item $id") { if (count(id) == count) Unit else null }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { inventory(Inventory.INV).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.seen(text: String) = messages().count { it.text == text }

    private fun HeadlessClient.awaitSeen(text: String, times: Int = 1) = await("$text x$times") { if (seen(text) >= times) Unit else null }

    private fun HeadlessClient.npcIndex(id: Int) = await("NPC $id in view") { npcs().firstOrNull { it.id == id } }.index

    private fun HeadlessClient.talk(id: Int, op: Int = 1) = npcOp(npcIndex(id), op)

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        command("item $id $count")
        awaitCount(id, count(id) + count)
    }

    private fun HeadlessClient.useOn(id: Int, npc: Int) = npcTarget(npcIndex(npc), InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(id), id)

    private fun HeadlessClient.awaitAt(x: Int, z: Int, level: Int = 0) = await("at $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        awaitAt(x, z)
    }

    @Test
    fun `an examiner gives a lost trowel back once, and the Researcher talks about the tools`(@TempDir dir: Path) {
        server(dir, listOf(Triple(examiner, 3362, 3341), Triple(researcher, 3366, 3341))).use { server ->
            server.login("alice").use { client ->
                client.ready()
                client.tele(3364, 3341)
                client.talk(examiner.id)
                client.chats("exam_centre.zaros_altar", "exam_centre.great_scholar")
                client.pick(2, "exam_centre.thanks", "exam_centre.lost_trowel")
                client.chats("exam_centre.lost_trowel", "exam_centre.another_trowel")
                client.awaitClosed()
                client.awaitCount(ExamCentre.TROWEL, 1)
                client.talk(examiner.id)
                client.chats("exam_centre.zaros_altar", "exam_centre.great_scholar")
                client.pick(2, "exam_centre.thanks", "exam_centre.lost_trowel")
                client.chats("exam_centre.lost_trowel", "exam_centre.look_in_backpack")
                client.awaitClosed()
                assertEquals(1, client.count(ExamCentre.TROWEL))

                client.talk(researcher.id)
                client.chats("exam_centre.big_find")
                client.pick(1, "panning_guide.tools", "panning_guide.thank_you")
                client.chats(
                    "panning_guide.tools", "panning_guide.rock_picks", "panning_guide.sample_jars", "panning_guide.jars_everywhere",
                    "panning_guide.brush", "panning_guide.brush_shortage", "panning_guide.sneaky", "panning_guide.exams",
                    "panning_guide.anything_else", "panning_guide.terry", "panning_guide.ok_thanks",
                )
                client.awaitClosed()
            }
        }
    }

    @Test
    fun `Terry's menu, three nuggets for a gold ore, the liquid named, a find and a muddy tray`(@TempDir dir: Path) {
        server(dir, listOf(Triple(terry, 3355, 3335))).use { server ->
            server.login("bob").use { client ->
                client.ready()
                client.tele(3355, 3334)
                client.talk(terry.id)
                client.pick(1, "terry.dt1_option", "terry.dig_option")
                client.chats("terry.dt1_ask", "terry.dt1_thanks")
                client.awaitClosed()
                client.talk(terry.id)
                client.pick(2, "terry.dt1_option", "terry.dig_option")
                client.chats("terry.studying", "terry.zaros", "terry.great_find")
                client.awaitClosed()

                client.give(ExamCentre.NUGGETS, 2)
                client.useOn(ExamCentre.NUGGETS, terry.id)
                client.chats("terry.nuggets", "terry.nuggets_few")
                client.awaitClosed()
                client.give(ExamCentre.NUGGETS, 2)
                client.useOn(ExamCentre.NUGGETS, terry.id)
                client.chats("terry.nuggets", "terry.nuggets_exchanged", "terry.excellent")
                client.awaitClosed()
                client.awaitCount(ExamCentre.GOLD_ORE, 1)
                assertEquals(1, client.count(ExamCentre.NUGGETS))

                client.give(ExamCentre.UNIDENTIFIED_LIQUID)
                client.useOn(ExamCentre.UNIDENTIFIED_LIQUID, terry.id)
                client.chats("terry.do_you_know", "terry.where_get", "terry.from_barrels", "terry.nitro_dangerous")
                client.awaitClosed()
                assertEquals(listOf(0, 1), listOf(client.count(ExamCentre.UNIDENTIFIED_LIQUID), client.count(ExamCentre.NITROGLYCERIN)))

                client.give(671)
                client.useOn(671, terry.id)
                client.chats("terry.have_a_look_this", "terry.skull")
                client.awaitClosed()

                client.give(679)
                client.useOn(679, terry.id)
                client.chats("terry.searched_tray", "terry.not_remember", "terry.dirty_hands")
                client.awaitSeen("[terry.hands_back]")
                assertEquals(1, client.count(679))
            }
        }
    }

    @Test
    fun `the north cupboard gives one rock pick, and a bookcase's books are taken`(@TempDir dir: Path) {
        server(dir, emptyList()).use { server ->
            server.login("carol").use { client ->
                client.ready()
                client.tele(3355, 3336)
                client.locOp(cupboardPick.id, cupboardPick.x, cupboardPick.z, ExamCentre.OPEN)
                client.await("the cupboard open") { if (server.world.locs.at(3355, 3337, 0).any { it.id == ExamCentre.CUPBOARD_PICK_OPEN }) Unit else null }
                client.locOp(ExamCentre.CUPBOARD_PICK_OPEN, cupboardPick.x, cupboardPick.z, ExamCentre.SEARCH)
                client.awaitDialogue("the rock pick box") { it is DialogueView.Other && it.interfaceId == Chatbox.OBJECTBOX }
                client.resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
                client.awaitClosed()
                assertEquals(1, client.count(ExamCentre.ROCK_PICK))
                client.locOp(ExamCentre.CUPBOARD_PICK_OPEN, cupboardPick.x, cupboardPick.z, ExamCentre.SEARCH)
                client.awaitSeen("[exam_centre.cupboard_empty]")
                assertEquals(1, client.count(ExamCentre.ROCK_PICK))

                client.tele(3366, 3333)
                client.locOp(bookcase.id, bookcase.x, bookcase.z, 1)
                client.box("exam_centre.earth_sciences")
                client.awaitClosed()
            }
        }
    }

    @Test
    fun `the west winch wants a rope and 10 Agility, the private winch none, and each rope climbs back beside its winch`(@TempDir dir: Path) {
        server(dir, listOf(Triple(doug, 3351, 9756))).use { server ->
            server.login("dave").use { client ->
                client.ready()
                client.tele(westWinch.x, westWinch.z - 1)
                client.locOp(westWinch.id, westWinch.x, westWinch.z, ExamCentre.OPERATE)
                client.chats("digsite_dungeon.could_fit")
                client.awaitClosed()
                assertEquals(1, client.seen("[digsite_dungeon.operate]"))
                assertEquals(1, client.seen("[digsite_dungeon.bucket]"))

                client.give(ExamCentre.ROPE)
                client.locTarget(westWinch.id, westWinch.x, westWinch.z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(ExamCentre.ROPE), ExamCentre.ROPE)
                client.awaitSeen("[digsite_dungeon.need_agility]")
                assertEquals(1, client.seen("[digsite_dungeon.tie_rope]"))
                assertEquals(Coord(westWinch.x, westWinch.z - 1, 0), client.position)

                client.command("setlevel agility 10")
                val xp = client.await("Agility 10") { client.stat(AGILITY)?.takeIf { it.level == 10 } }.xp
                client.locTarget(westWinch.id, westWinch.x, westWinch.z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(ExamCentre.ROPE), ExamCentre.ROPE)
                client.awaitAt(ExamCentre.WEST_BOTTOM.x, ExamCentre.WEST_BOTTOM.z)
                client.awaitSeen("[digsite_dungeon.cavern]")
                client.await("2 Agility XP") { client.stat(AGILITY)?.takeIf { it.xp == xp + 2 } }
                assertEquals(1, client.count(ExamCentre.ROPE))

                client.locOp(ropeWest.id, ropeWest.x, ropeWest.z, ExamCentre.CLIMB_UP)
                client.awaitAt(ExamCentre.WEST_TOP.x, ExamCentre.WEST_TOP.z)

                client.tele(privateWinch.x, privateWinch.z - 1)
                client.locOp(privateWinch.id, privateWinch.x, privateWinch.z, ExamCentre.OPERATE)
                client.awaitAt(ExamCentre.PRIVATE_BOTTOM.x, ExamCentre.PRIVATE_BOTTOM.z)
                client.awaitSeen("[digsite_dungeon.cavern]", 2)

                client.talk(doug.id)
                client.chats("digsite_dungeon.doug_hello")
                client.awaitClosed()
                client.talk(doug.id, ExamCentre.STEAL_FROM)
                client.chats("digsite_dungeon.doug_steal", "digsite_dungeon.doug_sorry")
                client.awaitClosed()

                client.locOp(ropePrivate.id, ropePrivate.x, ropePrivate.z, ExamCentre.CLIMB_UP)
                client.awaitAt(ExamCentre.PRIVATE_TOP.x, ExamCentre.PRIVATE_TOP.z)
            }
        }
    }

    private companion object {
        const val AGILITY = 16
    }
}
