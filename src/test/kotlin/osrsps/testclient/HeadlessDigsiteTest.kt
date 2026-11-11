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
import osrsps.game.content.Digsite
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.Pickpocketing
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Misthalin piece 7 step 9a through the headless client: the Digsite's workmen, students, Elissa and the Panning guide
 * talking after The Dig Site; the canal barge after Bone Voyage (boarding and leaving by talk and by option, Fossil
 * Island refused); the foreman, the busy workmen, Captain Errdo; the workers' gate turning the player away; a
 * signpost; and Steal-from on a workman and Pickpocket on the teddy student. The rl-client script
 * test-runner/examples/digsite.json does the same on the real map.
 */
class HeadlessDigsiteTest {
    private fun npc(id: Int, name: String, vararg ops: String?) =
        NpcType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, internalName = "test_$id")

    private val workman = npc(Digsite.WORKMEN.first(), "Digsite workman", "Talk-to", null, "Steal-from")
    private val skull = npc(Digsite.STUDENT_SKULL, "Student", "Talk-to")
    private val cup = npc(Digsite.STUDENT_CUP, "Student", "Talk-to")
    private val teddy = npc(Digsite.STUDENT_TEDDY, "Student", "Talk-to", null, "Pickpocket")
    private val elissa = npc(Digsite.ELISSA, "Elissa", "Talk-to")
    private val guide = npc(Digsite.PANNING_GUIDE, "Panning guide", "Talk-to")
    private val canalGuard = npc(Digsite.CANAL_GUARD, "Barge guard", "Talk-to", null, "Embark", "Quick-Travel")
    private val bargeGuard = npc(Digsite.BARGE_GUARD, "Barge guard", "Talk-to", null, "Disembark")
    private val lead = npc(Digsite.LEAD_NAVIGATOR, "Lead Navigator", "Talk-to", null, "Travel")
    private val junior = npc(Digsite.JUNIOR_NAVIGATOR, "Junior Navigator", "Talk-to", null, "Travel")
    private val foreman = npc(Digsite.FOREMAN, "Barge foreman", "Talk-to")
    private val edWood = npc(Digsite.ED_WOOD, "Ed Wood", "Talk-to")
    private val errdo = npc(Digsite.CAPTAIN_ERRDO, "Captain Errdo", null, null, "Talk-to")
    private val gateGuard = npc(Digsite.GATE_GUARD, "Museum guard", "Talk-to")

    private val gate = ZoneLoc(Digsite.GATE_LEFT, LocShape.WALL_STRAIGHT, 0, Digsite.GATE_X, Digsite.GATE_Z, 0)
    private val sign = ZoneLoc(2369, LocShape.CENTREPIECE_STRAIGHT, 0, 3226, 3222, 0)

    private fun server(dir: Path, spawns: List<Triple<NpcType, Int, Int>>, upstairs: Set<NpcType> = emptySet()) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(2000)
        for (id in listOf(995, 670, 954, 1925, 1059, 952, 673)) objs[id] = ObjType(id, "Item $id", stackable = id == 995)
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                markLoaded(Digsite.ON_BARGE_X shr 6, Digsite.ON_BARGE_Z shr 6)
                markLoaded(Digsite.GATE_X shr 6, Digsite.GATE_Z shr 6)
            },
            objTypes = ObjTypes(objs),
            npcTypes = NpcTypes.of(*spawns.map { it.first }.distinct().toTypedArray()),
            npcSpawns = spawns.map { (type, x, z) -> NpcSpawn(type, x, z, level = if (type in upstairs) 1 else 0, source = "test") },
            locTypes = LocTypes(
                listOf(
                    LocType(Digsite.GATE_LEFT, "Gate", ops = listOf("Open", null, null, null, null), collision = LocCollisionType(width = 1, length = 1)),
                    LocType(2369, "Signpost", ops = listOf("Read", null, null, null, null), collision = LocCollisionType(width = 1, length = 1)),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { listOf(gate, sign).forEach(::add) },
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

    private fun HeadlessClient.menu(vararg keys: String) {
        val menu = awaitDialogue("the menu") { it is DialogueView.Menu } as DialogueView.Menu
        assertEquals(keys.map { "[$it]" }, menu.options)
    }

    private fun HeadlessClient.pick(option: Int, vararg keys: String) {
        menu(*keys)
        chooseOption(option)
    }

    private fun HeadlessClient.awaitClosed() = await("the dialogue to close") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.count(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.seen(text: String) = messages().count { it.text == text }

    private fun HeadlessClient.awaitSeen(text: String, times: Int = 1) = await("$text x$times") { if (seen(text) >= times) Unit else null }

    private fun HeadlessClient.op(id: Int, op: Int) {
        val npc = await("NPC $id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, op)
    }

    private fun HeadlessClient.awaitAt(x: Int, z: Int, level: Int) =
        await("at $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }

    @Test
    fun `the workmen, the students, Elissa and the Panning guide talk as after The Dig Site`(@TempDir dir: Path) {
        val spawns = listOf(Triple(workman, 3220, 3214), Triple(skull, 3222, 3214), Triple(cup, 3224, 3214), Triple(teddy, 3218, 3214), Triple(elissa, 3220, 3224), Triple(guide, 3224, 3224))
        server(dir, spawns).use { server ->
            server.login("alice").use { client ->
                client.ready()
                client.op(workman.id, Digsite.TALK_TO)
                client.chats("digsite_people.hello", "digsite_people.great_archaeologist")
                client.awaitClosed()
                client.op(skull.id, Digsite.TALK_TO)
                client.chats("digsite_people.famous")
                client.awaitClosed()
                client.op(cup.id, Digsite.TALK_TO)
                client.chats("digsite_people.great_explorer")
                client.awaitClosed()
                client.op(teddy.id, Digsite.TALK_TO)
                client.chats("digsite_people.hello", "digsite_people.thanks_again")
                client.awaitClosed()

                client.op(elissa.id, Digsite.TALK_TO)
                client.chats("elissa.hello")
                client.pick(1, "elissa.what_do_you_do", "elissa.what_place")
                client.chats("elissa.what_do_you_do", "elissa.architecture")
                client.awaitClosed()
                client.op(elissa.id, Digsite.TALK_TO)
                client.chats("elissa.hello")
                client.pick(2, "elissa.what_do_you_do", "elissa.what_place")
                client.chats("elissa.what_place", "elissa.great_city")
                client.pick(1, "elissa.varrock", "elissa.what_happened")
                client.chats("elissa.varrock_said", "elissa.buried")
                client.awaitClosed()
                client.op(elissa.id, Digsite.TALK_TO)
                client.chats("elissa.hello")
                client.pick(2, "elissa.what_do_you_do", "elissa.what_place")
                client.chats("elissa.what_place", "elissa.great_city")
                client.pick(2, "elissa.varrock", "elissa.what_happened")
                client.chats("elissa.what_happened", "elissa.no_one_knows", "elissa.destruction", "elissa.civilizations")
                client.awaitClosed()

                val intro = arrayOf("panning_guide.who", "panning_guide.i_am", "panning_guide.excellent", "panning_guide.how_it_works", "panning_guide.gold")
                client.op(guide.id, Digsite.TALK_TO)
                client.chats(*intro)
                client.pick(1, "panning_guide.tools", "panning_guide.thank_you")
                client.chats(
                    "panning_guide.tools", "panning_guide.rock_picks", "panning_guide.sample_jars", "panning_guide.jars_everywhere",
                    "panning_guide.brush", "panning_guide.brush_shortage", "panning_guide.sneaky", "panning_guide.exams",
                    "panning_guide.anything_else", "panning_guide.terry", "panning_guide.ok_thanks",
                )
                client.awaitClosed()
                client.op(guide.id, Digsite.TALK_TO)
                client.chats(*intro)
                client.pick(2, "panning_guide.tools", "panning_guide.thank_you")
                client.chats("panning_guide.thank_you")
                client.awaitClosed()
            }
        }
    }

    @Test
    fun `the canal guard boards the player by talk or Embark, refuses Fossil Island, and the barge's guard lets them off`(@TempDir dir: Path) {
        val spawns = listOf(Triple(canalGuard, 3220, 3214), Triple(lead, 3222, 3214), Triple(junior, 3224, 3214), Triple(bargeGuard, Digsite.ON_BARGE_X, Digsite.ON_BARGE_Z - 1))
        server(dir, spawns, upstairs = setOf(bargeGuard)).use { server ->
            server.login("bob").use { client ->
                client.ready()
                val menu = arrayOf("digsite_barge.board", "digsite_barge.fossil_island", "digsite_barge.no_thanks")
                client.op(canalGuard.id, Digsite.TALK_TO)
                client.chats("digsite_barge.hello", "digsite_barge.can_i_help")
                client.pick(2, *menu)
                client.box("digsite_barge.fossil_later")
                client.awaitClosed()
                client.op(canalGuard.id, Digsite.QUICK_TRAVEL)
                client.awaitSeen("[digsite_barge.fossil_later]")

                client.op(lead.id, Digsite.TALK_TO)
                client.chats("digsite_barge.hello", "digsite_barge.travel_fossil")
                val navigator = client.awaitDialogue("the navigator's menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(listOf("[digsite_barge.yes_please]", "[digsite_barge.no_thanks]"), navigator.options)
                client.chooseOption(2)
                client.chats("digsite_barge.no_thanks")
                client.awaitClosed()
                client.op(lead.id, Digsite.TRAVEL)
                client.awaitSeen("[digsite_barge.fossil_later]", 2)
                client.op(junior.id, Digsite.TALK_TO)
                client.chats("digsite_barge.hello", "digsite_barge.lead_navigator")
                client.awaitClosed()
                client.op(junior.id, Digsite.TRAVEL)
                client.chats("digsite_barge.lead_navigator_travel")
                client.awaitClosed()

                client.op(canalGuard.id, Digsite.TALK_TO)
                client.chats("digsite_barge.hello", "digsite_barge.can_i_help")
                client.pick(1, *menu)
                client.chats("digsite_barge.board", "digsite_barge.absolutely_board")
                client.awaitAt(Digsite.ON_BARGE_X, Digsite.ON_BARGE_Z, 1)
                client.awaitSeen("[digsite_barge.helps_board]")

                client.op(bargeGuard.id, Digsite.TALK_TO)
                client.chats("digsite_barge.hello", "digsite_barge.can_i_help")
                client.pick(2, "digsite_barge.get_off", "digsite_barge.no_thanks")
                client.chats("digsite_barge.no_thanks")
                client.awaitClosed()
                client.op(bargeGuard.id, Digsite.TALK_TO)
                client.chats("digsite_barge.hello", "digsite_barge.can_i_help")
                client.pick(1, "digsite_barge.get_off", "digsite_barge.no_thanks")
                client.chats("digsite_barge.get_off", "digsite_barge.absolutely_off")
                client.awaitAt(Digsite.ON_QUAY_X, Digsite.ON_QUAY_Z, 0)
                client.awaitSeen("[digsite_barge.helps_off]")

                client.command("tele 3220 3216 0")
                client.awaitAt(3220, 3216, 0)
                client.op(canalGuard.id, Digsite.EMBARK)
                client.awaitAt(Digsite.ON_BARGE_X, Digsite.ON_BARGE_Z, 1)
                client.op(bargeGuard.id, Digsite.DISEMBARK)
                client.awaitAt(Digsite.ON_QUAY_X, Digsite.ON_QUAY_Z, 0)
                client.awaitSeen("[digsite_barge.helps_off]", 2)
            }
        }
    }

    @Test
    fun `the foreman thanks the player, Ed Wood is busy, Captain Errdo's glider is broken, and a signpost reads`(@TempDir dir: Path) {
        server(dir, listOf(Triple(foreman, 3220, 3214), Triple(edWood, 3222, 3214), Triple(errdo, 3224, 3214))).use { server ->
            server.login("carol").use { client ->
                client.ready()
                client.op(foreman.id, Digsite.TALK_TO)
                client.chats("digsite_barge.foreman_thanks name=carol")
                client.awaitClosed()
                val ed = client.await("Ed Wood") { client.npcs().firstOrNull { it.id == edWood.id } }
                client.npcOp(ed.index, Digsite.TALK_TO)
                val said = client.await("Ed Wood's line") { client.npcSaid().firstOrNull { it.index == ed.index } }
                assertTrue(said.text in (1..6).map { "[digsite_barge.busy_$it]" }, said.text)
                client.op(errdo.id, Digsite.ERRDO_TALK)
                client.chats("digsite_barge.embarrassing", "digsite_barge.what_happened", "digsite_barge.landing_gear")
                client.awaitClosed()
                client.locOp(sign.id, sign.x, sign.z, Digsite.READ)
                client.awaitSeen("[digsite_sign.level_3]")
            }
        }
    }

    @Test
    fun `the workers' gate's guard turns the player away, by talk and at the gate`(@TempDir dir: Path) {
        server(dir, listOf(Triple(gateGuard, Digsite.GATE_X - 1, Digsite.GATE_Z - 1))).use { server ->
            server.login("dave").use { client ->
                client.ready()
                client.command("tele ${Digsite.GATE_X - 2} ${Digsite.GATE_Z} 0")
                client.awaitAt(Digsite.GATE_X - 2, Digsite.GATE_Z, 0)
                client.op(gateGuard.id, Digsite.TALK_TO)
                client.chats("digsite_barge.workmans_gate")
                client.box("digsite_barge.need_kudos")
                client.awaitClosed()
                client.locOp(gate.id, gate.x, gate.z, 1)
                val guard = client.npcs().first { it.id == gateGuard.id }
                client.await("the guard's shout") { client.npcSaid().firstOrNull { it.index == guard.index && it.text == "[digsite_barge.workmans_gate_only]" } }
                client.chats("digsite_barge.workmans_gate")
                client.box("digsite_barge.need_kudos")
                client.awaitClosed()
                assertTrue(client.position!!.x < Digsite.GATE_X)
            }
        }
    }

    /** Tries [who]'s pocket until one works, waiting out each try (and a failed one's stun) in server ticks. */
    private fun HeadlessClient.pickUntilSuccess(id: Int, who: String) {
        val success = "[pickpocket.success who=$who]"
        val fail = "[pickpocket.fail who=$who]"
        await("a pickpocket on the $who that works", timeoutMillis = 60_000) {
            if (seen(success) > 0) return@await Unit
            val failed = seen(fail)
            op(id, 3)
            await("the try's outcome") { if (seen(success) > 0 || seen(fail) > failed) Unit else null }
            if (seen(fail) > failed) awaitTicks(Pickpocketing.ATTEMPT_TICKS + Pickpocketing.FAIL_TICKS + 1)
            null
        }
    }

    @Test
    fun `a workman's pocket gives something from his table and the teddy student her teddy`(@TempDir dir: Path) {
        server(dir, listOf(Triple(workman, 3220, 3214), Triple(teddy, 3224, 3214))).use { server ->
            server.login("erin").use { client ->
                client.ready()
                client.command("setlevel thieving 99")
                client.await("level 99") { client.stat(THIEVING)?.takeIf { it.level == 99 } }
                client.pickUntilSuccess(workman.id, "workman")
                val table = listOf(995, 670, 954, 1925, 1059, 952)
                client.await("a workman's item") { if (table.any { client.count(it) > 0 }) Unit else null }
                client.awaitTicks(Pickpocketing.WORKMAN_ATTEMPT_TICKS)
                client.pickUntilSuccess(teddy.id, "student")
                client.await("the teddy") { if (client.count(673) > 0) Unit else null }
            }
        }
    }

    @Test
    fun `a workman's pickpocket takes twice as long as the student's`(@TempDir dir: Path) {
        server(dir, listOf(Triple(workman, 3220, 3214), Triple(teddy, 3224, 3214))).use { server ->
            server.login("fred").use { client ->
                client.ready()
                client.command("setlevel thieving 99")
                client.await("level 99") { client.stat(THIEVING)?.takeIf { it.level == 99 } }
                // Paused and stepped, so the wait from the attempt to its outcome is counted in game ticks.
                fun outcomes(who: String) = client.seen("[pickpocket.success who=$who]") + client.seen("[pickpocket.fail who=$who]")
                fun ticksToOutcome(id: Int, who: String): Int {
                    client.commandReply("pause") { it.startsWith("Paused after tick") }
                    val before = outcomes(who)
                    client.op(id, 3)
                    var steps = 0
                    while (client.seen("[pickpocket.attempt who=$who]") == 0) {
                        client.stepTicks(1)
                        assertTrue(++steps < 20, "no attempt on the $who")
                    }
                    var ticks = 0
                    while (outcomes(who) == before) {
                        client.stepTicks(1)
                        assertTrue(++ticks < 20, "no outcome on the $who")
                    }
                    client.commandReply("resume") { it.startsWith("Resumed") }
                    return ticks
                }
                assertEquals(Pickpocketing.WORKMAN_ATTEMPT_TICKS - 1, ticksToOutcome(workman.id, "workman"))
                client.awaitTicks(Pickpocketing.ATTEMPT_TICKS + Pickpocketing.FAIL_TICKS + 1)
                assertEquals(Pickpocketing.ATTEMPT_TICKS - 1, ticksToOutcome(teddy.id, "student"))
            }
        }
    }

    private companion object {
        const val THIEVING = 17
    }
}
