package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.World
import osrsps.game.content.MuseumAdventurerLines
import osrsps.game.content.NaturalHistorianLines
import osrsps.game.content.VarrockMuseumPeople
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Varrock step 9b through the headless client: Historian Minas, Art Critic Jacques, the teachers on the ground and
 * second floors, a schoolgirl and a schoolboy, Torrcs's five talks and Marfet's four, Orlando Smith, and the four
 * natural historians (the first talk once, then each section's menu and a lecture). The rl-client script
 * test-runner/examples/varrock-museum-people.json does the same in the real museum.
 */
class HeadlessVarrockMuseumPeopleTest {
    private val talk = listOf("Talk-to", null, null, null, null)
    private fun npc(id: Int, name: String, internal: String) = NpcType(id, name, ops = talk, internalName = internal)

    private val minas = npc(VarrockMuseumPeople.HISTORIAN_MINAS, "Historian Minas", "vm_timeline_historian")
    private val jacques = npc(VarrockMuseumPeople.ART_CRITIC, "Art Critic Jacques", "vm_art_critic")
    private val teacher = npc(VarrockMuseumPeople.TEACHER_AND_GIRL, "Teacher and pupil", "vm_teacher_girl_multi")
    private val girl = npc(1921, "Schoolgirl", "vm_school_girl_blonde")
    private val boy = npc(1920, "Schoolboy", "vm_school_boy_blonde")
    private val torrcs = npc(VarrockMuseumPeople.TORRCS, "Torrcs", "vm_advent_01")
    private val marfet = npc(VarrockMuseumPeople.MARFET, "Marfet", "vm_advent_02")
    private val orlando = npc(VarrockMuseumPeople.ORLANDO_SMITH, "Orlando Smith", "vm_nathis_apprentice")
    private val historians = listOf(
        npc(VarrockMuseumPeople.HISTORIAN_NORTH, "Natural historian", "vm_natural_historian_north"),
        npc(VarrockMuseumPeople.HISTORIAN_SOUTH, "Natural historian", "vm_natural_historian_south"),
        npc(VarrockMuseumPeople.HISTORIAN_EAST, "Natural historian", "vm_natural_historian_east"),
        npc(VarrockMuseumPeople.HISTORIAN_WEST, "Natural historian", "vm_natural_historian_west"),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val upstairs = listOf(minas, jacques, torrcs, marfet, girl, boy).mapIndexed { i, type ->
            NpcSpawn(type, 3254 + i * 2, 3450, wander = 0, source = "test")
        }
        val teachers = listOf(NpcSpawn(teacher, 3260, 3452, wander = 0, source = "test"), NpcSpawn(teacher, 3261, 3451, level = 2, wander = 0, source = "test"))
        val basement = (historians + orlando).mapIndexed { i, type -> NpcSpawn(type, 1752 + i * 2, 4958, wander = 0, source = "test") }
        World(
            crcs, CollisionMap().apply { markLoaded(3254 shr 6, 3450 shr 6); markLoaded(1758 shr 6, 4958 shr 6) },
            npcTypes = NpcTypes.of(minas, jacques, teacher, girl, boy, torrcs, marfet, orlando, *historians.toTypedArray()),
            npcSpawns = upstairs + teachers + basement,
            // A fixed seed, so the random talks come round the same way on every run.
            random = GameRandom(7),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int = 0) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.talkTo(id: Int) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, VarrockMuseumPeople.TALK_TO)
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

    /** The text of the chat box showing now. */
    private fun HeadlessClient.chatText() = (awaitDialogue("a chat box") { it is DialogueView.Chat } as DialogueView.Chat).text

    @Test
    fun `Historian Minas talks every way`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3254, 3448)
                val first = "museum,information,other_sections"
                val second = "new_dig,information,other_sections"
                client.walk(
                    minas.id, "historian_minas", "hello", "welcome", "1:$first", "museum", "museum_answer",
                    "1:$second", "new_dig", "new_dig_1", "new_dig_2", "new_dig_3", "new_dig_thanks",
                    "2:$second", "information_said", "information_1", "no_new_quests", "no_matter", "go_on",
                )
                // The sections menu loops until the player walks away.
                val sections = "natural_history,dig_site,cleaning,timeline"
                client.talkTo(minas.id)
                client.steps(
                    "historian_minas", "hello", "welcome", "3:$first", "other_sections", "other_sections_answer",
                    "1:$sections", "natural_history_said", "natural_history_1", "natural_history_2", "natural_history_where", "natural_history_see", "natural_history_guess",
                    "2:$sections", "dig_site_said", "dig_site_answer",
                    "3:$sections", "cleaning_said", "cleaning_answer", "qualifications", "earth_science", "passed", "passed_answer",
                    "4:$sections", "timeline_said", "timeline_1", "timeline_2", "timeline_3", "timeline_wow",
                )
                client.awaitDialogue("the sections menu again") { it is DialogueView.Menu && it.options.first() == "[historian_minas.natural_history]" }
            }
        }
    }

    @Test
    fun `Art Critic Jacques, the teachers, the school visitors and Orlando Smith talk every way`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3256, 3448)
                val who = (1..7).map { "who_$it" }.toTypedArray()
                client.walk(jacques.id, "art_critic_jacques", "hello", "greetings", "1:who,what", "who", *who)
                client.walk(jacques.id, "art_critic_jacques", "hello", "greetings", "2:who,what", "what", "critique", "1:just_a_painting,yes_i_see", "just_a_painting_said", "just")
                client.walk(jacques.id, "art_critic_jacques", "hello", "greetings", "2:who,what", "what", "critique", "2:just_a_painting,yes_i_see", "yes_i_see", "look_at", "so_it_is", "no_words")

                client.tele(3260, 3450)
                client.walk(teacher.id, "museum_teacher", "ground_teacher", "ground_pupil")
                client.tele(3261, 3449, 2)
                client.talkTo(teacher.id)
                client.steps("museum_teacher", "upstairs_pupil", "upstairs_teacher")
                client.over()

                client.tele(3262, 3448)
                for ((id, prefix) in listOf(girl.id to "museum_schoolgirl", boy.id to "museum_schoolboy")) {
                    client.talkTo(id)
                    val text = client.chatText()
                    assertTrue(text in (1..9).map { "[$prefix.random_$it]" }, text)
                    client.continueDialogue()
                    client.over()
                }

                client.tele(1762, 4956)
                val opening = (1..10).map { "opening_$it" }.toTypedArray()
                client.walk(orlando.id, "orlando_smith", *opening, "1:sure,no_thanks", "sure", "sure_1", "sure_2", "will_do")
                client.walk(orlando.id, "orlando_smith", *opening, "2:sure,no_thanks", "no_thanks_said", "fair_dinkum")
            }
        }
    }

    @Test
    fun `Torrcs has five talks at random and Marfet four`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3258, 3448)
                for ((id, expected) in listOf(torrcs.id to setOf(1, 2, 3, 4, 5), marfet.id to setOf(1, 2, 3, 5))) {
                    val seen = mutableSetOf<Int>()
                    var talks = 0
                    while (seen != expected) {
                        assertTrue(++talks < 80, "only $seen in $talks talks")
                        client.talkTo(id)
                        val first = client.chatText()
                        val n = Regex("""\[museum_adventurer\.talk_(\d)_1]""").matchEntire(first)!!.groupValues[1].toInt()
                        assertTrue(n in expected, "talk $n")
                        seen += n
                        client.continueDialogue()
                        val boxes = MuseumAdventurerLines.TALKS[n - 1].length
                        client.steps("museum_adventurer", *(2..boxes).map { "talk_${n}_$it" }.toTypedArray())
                        client.over()
                    }
                }
            }
        }
    }

    @Test
    fun `a natural historian's first talk comes once, then each section's menu and lectures`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(1756, 4956)
                val north = "lizards,tortoises,dragons,wyverns,enough"
                val first = (1..6).map { "first_$it" }.toTypedArray()
                client.walk(
                    VarrockMuseumPeople.HISTORIAN_NORTH, "natural_historian", *first, "4:$north", "wyverns_said", "wyverns_intro", "follow",
                    *(1..10).map { "wyverns_$it" }.toTypedArray(), "wyverns_end",
                )
                client.walk(VarrockMuseumPeople.HISTORIAN_NORTH, "natural_historian", "hello_again", "hoping", "5:$north", "enough", "nonsense", "science")
                client.walk(
                    VarrockMuseumPeople.HISTORIAN_SOUTH, "natural_historian", "hello_again", "hoping", "1:natural_history,terrorbirds,kalphites,enough",
                    "natural_history", *NaturalHistorianLines.NATURAL_HISTORY_LINES.map { it.key.substringAfter('.') }.toTypedArray(),
                )
                client.walk(
                    VarrockMuseumPeople.HISTORIAN_EAST, "natural_historian", "hello_again", "hoping", "2:snails,monkeys,sea_slugs,snakes,enough",
                    "monkeys", "monkeys_intro", "follow", *(1..8).map { "monkeys_$it" }.toTypedArray(), "monkeys_end",
                )
                client.walk(
                    VarrockMuseumPeople.HISTORIAN_WEST, "natural_historian", "hello_again", "hoping", "3:camels,leeches,moles,penguins,enough",
                    "moles", "moles_intro", "follow", *(1..7).map { "moles_$it" }.toTypedArray(), "moles_end",
                )
                assertEquals(1, server.world.playerNamed("dave")!!.charges[VarrockMuseumPeople.MET])
            }
        }
    }
}
