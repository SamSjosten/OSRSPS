package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.BankGuardLines
import osrsps.game.content.MissSchismLines
import osrsps.game.content.Said
import osrsps.game.content.WiseOldMan
import osrsps.game.content.WomLines
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Draynor piece step 6 through the headless client and the registered handlers: the Wise Old Man's free hood, the
 * bank guard's recording setting varbit 960 (wom_bankjob), the telescope's talk and Miss Schism's news changing with
 * it. The rl-client script test-runner/examples/draynor-wise-old-man.json does the same in Draynor.
 */
class HeadlessWiseOldManTest {
    private val talkOps = listOf("Talk-to", null, null, null, null)
    private val wom = NpcType(WiseOldMan.ID, "Wise Old Man", ops = talkOps)
    private val missSchism = NpcType(WiseOldMan.MISS_SCHISM, "Miss Schism", ops = talkOps)
    private val bankGuard = NpcType(WiseOldMan.BANK_GUARD, "Bank guard", ops = talkOps)

    private val telescope = LocType(
        WiseOldMan.TELESCOPE, "Telescope", ops = listOf("Observe", null, null, null, null), collision = LocCollisionType(width = 1, length = 1),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(WiseOldMan.QUEST_HOOD + 1)
        objs[WiseOldMan.COINS] = ObjType(WiseOldMan.COINS, "Coins", stackable = true)
        objs[WiseOldMan.QUEST_HOOD] = ObjType(WiseOldMan.QUEST_HOOD, "Quest point hood")
        objs[WiseOldMan.QUEST_CAPE] = ObjType(WiseOldMan.QUEST_CAPE, "Quest point cape")
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            npcTypes = NpcTypes.of(wom, missSchism, bankGuard),
            npcSpawns = listOf(
                NpcSpawn(wom, 3221, 3220, source = "test"), NpcSpawn(missSchism, 3223, 3220, source = "test"),
                NpcSpawn(bankGuard, 3225, 3220, source = "test"),
            ),
            locTypes = LocTypes(mapOf(telescope.id to telescope)),
            staticLocs = StaticLocs().apply { add(ZoneLoc(WiseOldMan.TELESCOPE, LocShape.CENTREPIECE_STRAIGHT, 0, 3222, 3216, 0)) },
            varTypes = VarTypes(6_000, mapOf(WiseOldMan.BANKJOB_VARBIT to VarbitType(BANKJOB_VARP, 0, 1))),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.opNpc(id: Int, op: Int) {
        val npc = await("$id in view") { npcs().firstOrNull { it.id == id } }
        npcOp(npc.index, op)
    }

    private fun HeadlessClient.page(key: String) {
        awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
        continueDialogue()
    }

    private fun HeadlessClient.pages(talk: List<Said>) = talk.forEach { page(it.line.key) }

    private fun HeadlessClient.pick(option: Int) {
        awaitDialogue("the menu") { it is DialogueView.Menu }
        chooseOption(option)
    }

    private fun HeadlessClient.ended() = await("the talk to end") { if (dialogueView == null) Unit else null }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    @Test
    fun `the Wise Old Man gives the hood free`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.opNpc(WiseOldMan.ID, WiseOldMan.TALK_TO)
                client.awaitDialogue("the greeting") { it is DialogueView.Chat && it.text.startsWith("[wise_old_man.greetings") }
                client.continueDialogue()
                client.pick(1)
                client.page("wise_old_man.hood")
                client.await("the hood") { if (client.count(WiseOldMan.QUEST_HOOD) == 1) Unit else null }
            }
        }
    }

    @Test
    fun `the bank guard's recording sets wom_bankjob, which the telescope and Miss Schism follow`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.locOp(WiseOldMan.TELESCOPE, 3222, 3216, WiseOldMan.OBSERVE)
                client.pages(WomLines.TELESCOPE + WomLines.TELESCOPE_UNSEEN)
                client.ended()

                client.opNpc(WiseOldMan.MISS_SCHISM, WiseOldMan.TALK_TO)
                client.page("miss_schism.news_opener")
                client.pick(1)
                client.pages(MissSchismLines.UNSEEN_TALK)
                client.ended()

                client.opNpc(WiseOldMan.BANK_GUARD, WiseOldMan.TALK_TO)
                client.page("wom_bankguard.yes_q")
                client.pages(BankGuardLines.FIRST_TALK)
                client.pick(1)
                client.page("wom_bankguard.see")
                client.page("wom_bankguard.see_long")
                client.pick(1)
                client.page("wom_bankguard.show")
                client.page("wom_bankguard.show_how")
                for (key in listOf("eyes", "plays", "end")) {
                    client.awaitDialogue(key) { it is DialogueView.Message && it.text == "[wom_bankguard.$key]" }
                    client.continueDialogue()
                }
                client.ended()
                client.await("wom_bankjob seen") { if (client.varp(BANKJOB_VARP) == WiseOldMan.SEEN) Unit else null }

                client.locOp(WiseOldMan.TELESCOPE, 3222, 3216, WiseOldMan.OBSERVE)
                client.pages(WomLines.TELESCOPE + WomLines.TELESCOPE_SEEN)
                client.ended()

                client.opNpc(WiseOldMan.MISS_SCHISM, WiseOldMan.TALK_TO)
                client.page("miss_schism.news_opener")
                client.pick(1)
                client.pages(MissSchismLines.SEEN_TALK)
                client.pick(3)
                client.page("miss_schism.enough")
                client.page("miss_schism.enough_answer")
                client.ended()
                assertEquals(WiseOldMan.SEEN, client.varp(BANKJOB_VARP))
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        /** A free varp for the test's varbit 960. */
        const val BANKJOB_VARP = 4000
    }
}
