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
import osrsps.game.World
import osrsps.game.content.VarrockTownsfolk
import osrsps.game.dialogue.Chatbox
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/**
 * Varrock step 8a through the headless client: every townsperson's talk every way it goes; Gertrude's kitten for 100
 * coins, through her talk and her Kitten option, not for less and not while one is owned; Charlie the Tramp and the
 * tramp each taking a coin, and the tramp's grumble when refused. The rl-client script
 * test-runner/examples/varrock-townsfolk.json talks to them in the real city.
 */
class HeadlessVarrockTownsfolkTest {
    private val coins = ObjType(COINS, "Coins", stackable = true)
    private val kittens = VarrockTownsfolk.KITTENS.map { ObjType(it, "Pet kitten", cost = 0, tradeable = false) }
    private val cat = ObjType(PET_CAT, "Pet cat", cost = 0, tradeable = false)
    private val overgrown = ObjType(OVERGROWN_CAT, "Overgrown cat", cost = 0, tradeable = false)
    private val lazy = ObjType(LAZY_CAT, "Lazy cat", cost = 0, tradeable = false)

    private val talk = listOf("Talk-to", null, null, null, null)
    private fun npc(id: Int, name: String, internal: String, ops: List<String?> = talk) = NpcType(id, name, ops = ops, internalName = internal)

    private val all = listOf(
        npc(VarrockTownsfolk.ROMEO, "Romeo", "romeo"),
        npc(VarrockTownsfolk.JULIET, "Juliet", "juliet"),
        npc(VarrockTownsfolk.DRAUL_LEPTOC, "Draul Leptoc", "draul_leptoc"),
        npc(VarrockTownsfolk.PHILLIPA, "Phillipa", "phillipa"),
        npc(VarrockTownsfolk.GERTRUDE, "Gertrude", "gertrude_post", listOf("Talk-to", null, "Kitten", null, null)),
        npc(VarrockTownsfolk.SHILOP, "Shilop", "shilop"),
        npc(VarrockTownsfolk.WILOUGH, "Wilough", "wilough"),
        npc(VarrockTownsfolk.PHILOP, "Philop", "philop"),
        npc(VarrockTownsfolk.KANEL, "Kanel", "kanel"),
        npc(VarrockTownsfolk.ARIS, "Aris", "aris"),
        npc(VarrockTownsfolk.CHARLIE, "Charlie the Tramp", "tramppg"),
        npc(VarrockTownsfolk.TRAMP, "Tramp", "tramp"),
        npc(VarrockTownsfolk.DR_HARLOW, "Dr Harlow", "dr_harlow_vis"),
        npc(VarrockTownsfolk.JONNY_THE_BEARD, "Jonny the Beard", "jonny_the_beard_2op"),
        npc(VarrockTownsfolk.IFFIE, "Iffie", "varrock_granny_1"),
        npc(VarrockTownsfolk.TOWN_CRIER, "Town Crier", "pmod_town_crier_varrock"),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes.of(coins, cat, overgrown, lazy, *kittens.toTypedArray()),
            npcTypes = NpcTypes.of(*all.toTypedArray()),
            npcSpawns = all.mapIndexed { i, type -> NpcSpawn(type, 3214 + (i % 8) * 2, 3215 + (i / 8) * 6, wander = 0, source = "test") },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.held(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }
    private fun HeadlessClient.kittens() = VarrockTownsfolk.KITTENS.sumOf { held(it) }

    private fun HeadlessClient.op(id: Int, option: Int = VarrockTownsfolk.TALK_TO) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, option)
    }

    private fun HeadlessClient.chat(key: String) {
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
        continueDialogue()
    }

    /** An item box, whose text the headless client doesn't read: [key] names it for the failure message. */
    private fun HeadlessClient.objbox(key: String) {
        awaitDialogue(key) { it is DialogueView.Other && it.interfaceId == Chatbox.OBJECTBOX }
        resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
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
        op(id)
        steps(prefix, *steps)
        over()
    }

    private fun HeadlessClient.coinsNow(count: Int) {
        command("empty")
        if (count > 0) command("item $COINS $count")
        await("$count coins") { if (held(COINS) == count && kittens() == 0) Unit else null }
    }

    @Test
    fun `the townsfolk talk every way`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.walk(VarrockTownsfolk.ROMEO, "romeo", "dreadful", "cousin")
                client.walk(VarrockTownsfolk.JULIET, "juliet", "crypt", "worthless", "indigestion", "go_away")
                client.walk(VarrockTownsfolk.DRAUL_LEPTOC, "draul_leptoc", "proud")
                client.walk(VarrockTownsfolk.PHILLIPA, "phillipa", "romeo")
                client.walk(VarrockTownsfolk.SHILOP, "shilop", "hello", "strangers", "not_old")
                client.walk(VarrockTownsfolk.WILOUGH, "wilough", "hello", "strangers", "not_old")
                for (child in listOf(VarrockTownsfolk.PHILOP, VarrockTownsfolk.KANEL)) {
                    client.op(child)
                    client.chat("playing_child.hello")
                    client.awaitDialogue("ignored") { it is DialogueView.Message && it.text == "[playing_child.ignored]" }
                    client.continueDialogue()
                    client.over()
                }
                val aris = arrayOf("greeting", "demon")
                val menu = "how_knew,thanks,not_young"
                client.walk(VarrockTownsfolk.ARIS, "aris", *aris, "1:$menu", "how_knew", "knowing")
                client.walk(VarrockTownsfolk.ARIS, "aris", *aris, "2:$menu", "thanks")
                client.walk(VarrockTownsfolk.ARIS, "aris", *aris, "3:$menu", "not_young", "grand_scheme", "1:how_old,fair", "how_old", "riddle", "never_mind")
                client.walk(VarrockTownsfolk.ARIS, "aris", *aris, "3:$menu", "not_young", "grand_scheme", "2:how_old,fair", "fair", "wise")
                client.walk(VarrockTownsfolk.DR_HARLOW, "dr_harlow", "drink", "enough")
                client.walk(VarrockTownsfolk.JONNY_THE_BEARD, "jonny_the_beard", "not_interested")
                client.walk(VarrockTownsfolk.IFFIE, "iffie", "hello", "count", "sister")

                val crier = "pmods,rules,tip,goodbye"
                val pmods = "what_is,what_can,become,cannot"
                client.op(VarrockTownsfolk.TOWN_CRIER)
                client.steps(
                    "town_crier", "greeting", "1:$crier", "pmods", "explain", "1:$pmods", "what_is", "trusted",
                    "1:$crier", "pmods", "explain", "2:$pmods", "what_can", "powers", "1:$crier", "pmods", "explain", "3:$pmods", "become", "tips",
                    "1:$crier", "pmods", "explain", "4:$pmods", "cannot", "limits", "2:$crier", "rules", "book", "3:$crier", "tip",
                )
                val tips = (1..27).map { "[town_crier.tip_$it]" }
                client.awaitDialogue("a tip") { it is DialogueView.Chat && it.text in tips }
                client.continueDialogue()
                client.steps("town_crier", "4:$crier", "goodbye", "farewell")
                client.over()
            }
        }
    }

    @Test
    fun `Gertrude sells a kitten for 100 coins, not for less and not while one is owned`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                val opening = arrayOf("hello_again", "ratcatchers", "strange", "help")
                client.walk(VarrockTownsfolk.GERTRUDE, "gertrude", *opening, "2:kittens,bye", "bye")
                client.coinsNow(99)
                client.walk(VarrockTownsfolk.GERTRUDE, "gertrude", *opening, "1:kittens,bye", "kittens", "cost", "short")
                client.op(VarrockTownsfolk.GERTRUDE, VarrockTownsfolk.KITTEN_OP)
                client.chat("gertrude.not_enough")
                client.over()
                assertEquals(99, client.held(COINS))

                client.coinsNow(100)
                client.walk(VarrockTownsfolk.GERTRUDE, "gertrude", *opening, "1:kittens,bye", "kittens", "cost", "2:yes,no", "no")
                client.op(VarrockTownsfolk.GERTRUDE)
                client.steps("gertrude", *opening, "1:kittens,bye", "kittens", "cost", "1:yes,no", "yes")
                client.objbox("gertrude.given")
                client.over()
                assertEquals(1, client.kittens())
                assertEquals(0, client.held(COINS))

                // One kitten at a time: her Kitten option turns the player away while one is held.
                client.command("item $COINS 100")
                client.await("100 coins") { if (client.held(COINS) == 100) Unit else null }
                client.op(VarrockTownsfolk.GERTRUDE, VarrockTownsfolk.KITTEN_OP)
                client.chat("gertrude.wait")
                client.over()
                assertEquals(100, client.held(COINS))

                client.coinsNow(100)
                client.op(VarrockTownsfolk.GERTRUDE, VarrockTownsfolk.KITTEN_OP)
                client.objbox("gertrude.given")
                client.over()
                assertEquals(1, client.await("a kitten") { client.kittens().takeIf { it == 1 } })
                assertEquals(0, client.held(COINS))

                // A grown cat stops her too; an overgrown one doesn't, as she sells another once a cat is overgrown, nor does
                // a lazy or wily cat, kept beside a kitten or cat.
                client.coinsNow(100)
                client.command("item $PET_CAT 1")
                client.await("the cat") { if (client.held(PET_CAT) == 1) Unit else null }
                client.op(VarrockTownsfolk.GERTRUDE, VarrockTownsfolk.KITTEN_OP)
                client.chat("gertrude.wait")
                client.over()
                assertEquals(100, client.held(COINS))
                for ((other, what) in listOf(OVERGROWN_CAT to "overgrown cat", LAZY_CAT to "lazy cat")) {
                    client.coinsNow(100)
                    client.command("item $other 1")
                    client.await("the $what") { if (client.held(other) == 1) Unit else null }
                    client.op(VarrockTownsfolk.GERTRUDE, VarrockTownsfolk.KITTEN_OP)
                    client.objbox("gertrude.given")
                    client.over()
                    assertEquals(1, client.await("a kitten") { client.kittens().takeIf { it == 1 } })
                    assertEquals(0, client.held(COINS))
                }
            }
        }
    }

    @Test
    fun `Charlie and the tramp each take a coin, and the tramp grumbles when refused`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                val menu = "who,none,job,here,alley"
                client.walk(VarrockTownsfolk.CHARLIE, "charlie", "spare", "1:$menu", "who", "name", "2:$menu", "none", "thanks_anyway")
                client.walk(VarrockTownsfolk.CHARLIE, "charlie", "spare", "3:$menu", "job", "curse")
                client.walk(VarrockTownsfolk.CHARLIE, "charlie", "spare", "4:$menu", "here", "no_money", "thanks_anyway")
                client.walk(VarrockTownsfolk.CHARLIE, "charlie", "spare", "5:$menu", "alley", "black_arm", "1:warning,join", "warning", "welcome")
                client.walk(VarrockTownsfolk.CHARLIE, "charlie", "spare", "5:$menu", "alley", "black_arm", "2:warning,join", "join", "member")
                client.walk(VarrockTownsfolk.TRAMP, "tramp", "spare", "no_money", "doubt")

                client.coinsNow(3)
                client.walk(VarrockTownsfolk.CHARLIE, "charlie", "spare", "4:$menu", "here", "thanks", "1:no_problem,hint", "no_problem")
                client.walk(VarrockTownsfolk.CHARLIE, "charlie", "spare", "4:$menu", "here", "thanks", "2:no_problem,hint", "hint", "food")
                client.walk(VarrockTownsfolk.TRAMP, "tramp", "spare", "1:give,earn", "give", "thanks")
                assertEquals(0, client.held(COINS))

                client.coinsNow(1)
                client.walk(VarrockTownsfolk.TRAMP, "tramp", "spare", "2:give,earn", "earn", "shrug")
                assertEquals(1, client.held(COINS))
                val tramp = client.await("the tramp in view") { client.npcs().firstOrNull { it.id == VarrockTownsfolk.TRAMP } }
                val grumbles = (1..3).map { "[tramp.overhead_$it]" }
                client.await("the tramp grumbling") { client.npcSaid().firstOrNull { it.index == tramp.index && it.text in grumbles } }
            }
        }
    }

    private companion object {
        const val COINS = 995
        const val PET_CAT = 1561
        const val OVERGROWN_CAT = 1567
        const val LAZY_CAT = 6549
    }
}
