package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.Skill
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.content.Kudos
import osrsps.game.content.MUSEUM_VARBITS
import osrsps.game.content.MuseumFinds
import osrsps.game.content.NaturalHistoryQuiz
import osrsps.game.content.OrlandoSmithLines
import osrsps.game.content.VarrockMuseum
import osrsps.game.content.VarrockMuseumPeople
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.LevelUps
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Kudos step 2 through the headless client: Orlando's quiz taken on, a plaque's questions (a wrong answer changes
 * nothing, three right ones fill the display for 2 Kudos), the plaque once done, Orlando's talk as the displays fill and
 * his XP once all fourteen are done, and the information clerk's first reward and Natural History answer. The real
 * plaques are on the Natural History floor; here two stand by the spawn.
 */
class HeadlessNaturalHistoryQuizTest {
    private val talk = listOf("Talk-to", null, null, null, null)
    private val orlando = NpcType(VarrockMuseumPeople.ORLANDO_SMITH, "Orlando Smith", ops = talk, internalName = "vm_nathis_apprentice")
    private val clerk = NpcType(VarrockMuseum.CLERK, "Information clerk", ops = talk, internalName = "vm_info_booth_lady")

    private val snail = NaturalHistoryQuiz.DISPLAYS.single { it.animal == "snail" }
    private val dragon = NaturalHistoryQuiz.DISPLAYS.single { it.animal == "dragon" }
    private val plaques = listOf(snail, dragon).mapIndexed { i, d -> ZoneLoc(d.plaque, LocShape.CENTREPIECE_STRAIGHT, 0, 3216 + 2 * i, 3214, 0) }

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            locTypes = LocTypes(
                listOf(snail, dragon).map { LocType(it.plaque, "Plaque", listOf("Study", null, null, null, null), LocCollisionType(width = 1, length = 1)) }
                    .associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply { plaques.forEach(::add) },
            npcTypes = NpcTypes.of(orlando, clerk),
            npcSpawns = listOf(NpcSpawn(orlando, 3224, 3220, wander = 0, source = "test"), NpcSpawn(clerk, 3230, 3220, wander = 0, source = "test")),
            varTypes = VarTypes(6_000, MUSEUM_VARBITS),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.ready() = await("the login rebuild") { position }

    private fun HeadlessClient.npcIndex(id: Int) = await("NPC $id in view") { npcs().firstOrNull { it.id == id } }.index

    private fun HeadlessClient.chats(vararg keys: String) {
        for (key in keys) {
            awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
            continueDialogue()
        }
    }

    private fun HeadlessClient.pick(option: Int, vararg keys: String) {
        val texts = keys.map { "[$it]" }
        awaitDialogue("the menu $texts") { it is DialogueView.Menu && it.options == texts }
        chooseOption(option)
    }

    private fun HeadlessClient.over() = await("the talk over") { if (dialogueView == null) Unit else null }

    /** Clicks through the level-up boxes for [skills], in turn, which follow a talk that gave XP (::keys on). */
    private fun HeadlessClient.levelUps(vararg skills: Skill) {
        for (skill in skills) {
            await("${skill.displayName}'s level-up box") {
                val body = texts(LevelUps.INTERFACE).values.joinToString()
                if (LevelUps.INTERFACE in openInterfaces() && "levelup.body skill=${skill.displayName} " in body && "skill=${skill.displayName}]" in body) Unit else null
            }
            resumePause(LevelUps.INTERFACE, LevelUps.CONTINUE)
        }
        await("the level-up boxes gone") { if (LevelUps.INTERFACE !in openInterfaces()) Unit else null }
    }

    private fun HeadlessClient.seen(text: String) = messages().count { it.text == text }

    private fun HeadlessClient.awaitSeen(text: String, times: Int = 1) = await("$text x$times") { if (seen(text) >= times) Unit else null }

    /** A varbit's value from the varps the client holds, by [MUSEUM_VARBITS]. */
    private fun HeadlessClient.varbit(id: Int): Int {
        val type = MUSEUM_VARBITS.getValue(id)
        return varp(type.baseVar) shr type.startBit and ((1 shl (type.endBit - type.startBit + 1)) - 1)
    }

    private fun HeadlessClient.awaitVarbit(id: Int, value: Int) = await("varbit $id = $value") { if (varbit(id) == value) Unit else null }

    private fun HeadlessClient.setVarbit(id: Int, value: Int) {
        command("varbit $id $value")
        awaitVarbit(id, value)
    }

    private fun HeadlessClient.study(display: NaturalHistoryQuiz.Display) {
        val loc = plaques.single { it.id == display.plaque }
        locOp(loc.id, loc.x, loc.z, NaturalHistoryQuiz.STUDY)
    }

    /** Studies [display]'s plaque and waits for its question; returns the question's number (0 to 5). */
    private fun HeadlessClient.question(display: NaturalHistoryQuiz.Display): Int {
        val before = text(NaturalHistoryQuiz.INTERFACE, NaturalHistoryQuiz.QUESTION)
        study(display)
        val prefix = "[natural_history_quiz.${display.animal}_q"
        // The question and its answers, once all four have arrived: never the one before, as the plaque doesn't ask the
        // same question twice running and another display's have another prefix.
        val text = await("a ${display.animal} question") {
            val question = text(NaturalHistoryQuiz.INTERFACE, NaturalHistoryQuiz.QUESTION)
            val last = text(NaturalHistoryQuiz.INTERFACE, NaturalHistoryQuiz.ANSWERS[2])
            question?.takeIf { NaturalHistoryQuiz.INTERFACE in openInterfaces() && it != before && it.startsWith(prefix) && last == it.removeSuffix("]") + "_a3]" }
        }
        assertEquals(display.number.toString(), text(NaturalHistoryQuiz.INTERFACE, NaturalHistoryQuiz.DISPLAY_NUMBER))
        assertEquals(false, hidden(NaturalHistoryQuiz.INTERFACE, NaturalHistoryQuiz.QUIZ_TEXT))
        val q = text.removePrefix(prefix).removeSuffix("]").toInt() - 1
        for (a in 0..2) assertEquals("${prefix}${q + 1}_a${a + 1}]", text(NaturalHistoryQuiz.INTERFACE, NaturalHistoryQuiz.ANSWERS[a]))
        return q
    }

    private fun HeadlessClient.answer(answer: Int) {
        ifButton(NaturalHistoryQuiz.INTERFACE, NaturalHistoryQuiz.ANSWERS[answer], NaturalHistoryQuiz.ANSWER_OP)
        await("the plaque closed") { if (NaturalHistoryQuiz.INTERFACE !in openInterfaces()) Unit else null }
    }

    private fun HeadlessClient.orlandoSays(vararg keys: String) {
        npcOp(npcIndex(orlando.id), VarrockMuseumPeople.TALK_TO)
        chats(*keys)
        over()
    }

    private fun HeadlessClient.takeOnQuiz() {
        npcOp(npcIndex(orlando.id), VarrockMuseumPeople.TALK_TO)
        chats(*OrlandoSmithLines.OPENING_LINES.map { it.key }.toTypedArray())
        pick(1, "orlando_smith.sure", "orlando_smith.no_thanks")
        chats("orlando_smith.sure", "orlando_smith.sure_1", "orlando_smith.sure_2", "orlando_smith.will_do")
        over()
        awaitVarbit(NaturalHistoryQuiz.VARBIT_AGREED, 1)
    }

    @Test
    fun `three right answers at a plaque fill its display for 2 Kudos, a wrong one changing nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                client.command("keys on")
                // Before Orlando's asked, the plaque is only a quiz to wonder at.
                client.study(snail)
                client.awaitSeen("[natural_history_quiz.not_started]")
                assertTrue(NaturalHistoryQuiz.INTERFACE !in client.openInterfaces())

                // Turned down, he asks again next time.
                client.npcOp(client.npcIndex(orlando.id), VarrockMuseumPeople.TALK_TO)
                client.chats(*OrlandoSmithLines.OPENING_LINES.map { it.key }.toTypedArray())
                client.pick(2, "orlando_smith.sure", "orlando_smith.no_thanks")
                client.chats("orlando_smith.no_thanks_said", "orlando_smith.fair_dinkum")
                client.over()
                assertEquals(0, client.varbit(NaturalHistoryQuiz.VARBIT_AGREED))
                client.takeOnQuiz()
                client.orlandoSays("orlando_smith.how_coming", "orlando_smith.not_yet", "orlando_smith.no_worries", "orlando_smith.will_do")

                // A wrong answer: Orlando says so, and the display stays at 0.
                var q = client.question(snail)
                client.answer((snail.right[q] + 1) % 3)
                client.chats("natural_history_quiz.wrong")
                client.over()
                assertEquals(0, client.varbit(snail.varbit))
                // The next question isn't the one just asked.
                val after = client.question(snail)
                assertTrue(after != q, "asked question $q again")
                q = after

                // Three right answers.
                for (right in 1..3) {
                    if (right > 1) q = client.question(snail)
                    client.answer(snail.right[q])
                    client.chats("natural_history_quiz.right")
                    if (right < 3) {
                        client.over()
                        client.awaitVarbit(snail.varbit, right)
                    }
                }
                client.awaitSeen("[natural_history_quiz.kudos]")
                client.chats("natural_history_quiz.all_of_them")
                client.over()
                client.awaitVarbit(snail.varbit, NaturalHistoryQuiz.DONE)
                client.awaitVarbit(Kudos.VARBIT_KUDOS, 2)

                // Done, the plaque names the animal instead.
                client.study(snail)
                client.awaitSeen("[natural_history_quiz.snail_name]")
                assertTrue(NaturalHistoryQuiz.INTERFACE !in client.openInterfaces())
                client.orlandoSays("orlando_smith.how_coming", "orlando_smith.getting_there", "orlando_smith.doing_great", "orlando_smith.righty_ho", "orlando_smith.good_luck")

                // Closed without an answer, the question is gone: a stale answer click does nothing.
                client.question(dragon)
                client.ifButton(NaturalHistoryQuiz.INTERFACE, NaturalHistoryQuiz.CLOSE, 1)
                client.await("the plaque closed") { if (NaturalHistoryQuiz.INTERFACE !in client.openInterfaces()) Unit else null }
                client.ifButton(NaturalHistoryQuiz.INTERFACE, NaturalHistoryQuiz.ANSWERS[0], NaturalHistoryQuiz.ANSWER_OP)
                client.command("keys on")
                client.awaitSeen("Dialogue shows keys", 2)
                assertEquals(null, client.dialogueView)
                assertEquals(0, client.varbit(dragon.varbit))
            }
        }
    }

    @Test
    fun `the last display done, Orlando gives his XP once, and the clerk her first reward`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                client.command("keys on")
                client.takeOnQuiz()
                // Every display but the dragon's done; the dragon's at two.
                for (display in NaturalHistoryQuiz.DISPLAYS - dragon) client.setVarbit(display.varbit, 3)
                client.setVarbit(dragon.varbit, 2)

                val q = client.question(dragon)
                client.answer(dragon.right[q])
                client.chats("natural_history_quiz.right")
                client.awaitSeen("[natural_history_quiz.kudos]")
                client.chats("natural_history_quiz.all_of_them", "natural_history_quiz.finished", "natural_history_quiz.come_over")
                client.over()
                client.awaitVarbit(Kudos.VARBIT_KUDOS, 28)

                client.npcOp(client.npcIndex(orlando.id), VarrockMuseumPeople.TALK_TO)
                client.chats("orlando_smith.how_coming", "orlando_smith.all_done", "orlando_smith.beaut", "orlando_smith.glad", "orlando_smith.chat_boss", "orlando_smith.experienced")
                client.awaitSeen("[natural_history_quiz.xp]")
                client.chats("orlando_smith.thank_you", "orlando_smith.take_care", "orlando_smith.goodbye")
                // Slayer and Hunter both go from 1 to 9.
                client.levelUps(Skill.SLAYER, Skill.HUNTER)
                client.over()
                assertEquals(1_000, client.stat(Skill.SLAYER.id)?.xp)
                assertEquals(1_000, client.stat(Skill.HUNTER.id)?.xp)
                assertEquals(1, client.varbit(NaturalHistoryQuiz.VARBIT_REWARD_GIVEN))
                // Once only.
                client.orlandoSays("orlando_smith.thanks_help")
                assertEquals(1_000, client.stat(Skill.SLAYER.id)?.xp)

                // With the Dig Site's five as well, 78 Kudos: the clerk's first reward, then her Natural History answer.
                for (case in MuseumFinds.CASES) client.setVarbit(case.varbit, 1)
                client.npcOp(client.npcIndex(clerk.id), VarrockMuseum.TALK_TO)
                client.chats("museum_clerk.reward_1", "museum_clerk.reward_2")
                client.pick(1, "museum_clerk.teach_me", "museum_clerk.reward_no")
                client.chats("museum_clerk.teach_me")
                client.awaitDialogue("the reward box") { it == DialogueView.Message("[museum_clerk.reward_mining]") }
                client.continueDialogue()
                client.chats("museum_clerk.wow", "museum_clerk.anything_else")
                assertEquals(1_000, client.stat(Skill.MINING.id)?.xp)
                assertEquals(1, client.varbit(Kudos.VARBIT_REWARD_50))
                client.pick(4, "museum_clerk.map", "museum_clerk.dig_site", "museum_clerk.timeline", "museum_clerk.natural_history", "museum_clerk.kudos")
                client.chats("museum_clerk.natural_history_said", "museum_clerk.natural_history_about", "museum_clerk.natural_history_done")
                client.pick(3, "museum_clerk.living", "museum_clerk.something_else", "museum_clerk.bye")
                client.chats("museum_clerk.bye_said", "museum_clerk.goodbye")
                // Mining from 1 to 9.
                client.levelUps(Skill.MINING)
                client.over()
            }
        }
    }
}
