package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.JournalTab
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.content.DIARY_VARBITS
import osrsps.game.content.Diaries
import osrsps.map.CollisionMap

/**
 * The diaries through the headless client: `::diary complete` marks a task with its message and sends the varps the
 * side panel draws from; the quest tab's Diaries page enables each row's Open; Open fills the journal scroll, the done
 * task struck through, before opening it and sizing it; a tier's last task gives the tier's message too; the bits
 * survive a new login; and `::diary reset` takes them back off.
 */
class HeadlessDiariesTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            varTypes = VarTypes(4_000, DIARY_VARBITS), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.ready() = await("the login rebuild") { position }

    private fun HeadlessClient.seen(prefix: String) = messages().count { it.text.startsWith(prefix) }

    private fun HeadlessClient.awaitVarp(id: Int, value: Int) = await("varp $id at $value") { Unit.takeIf { varp(id) == value } }

    @Test
    fun `a task done shows in the panel's vars and the journal`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready()
                client.command("diary complete lumbridge easy 5")
                client.await("the task message") { Unit.takeIf { client.seen("[diary.task_done") == 1 } }
                client.awaitVarp(LUMB_DRAY, 1 shl 5)
                // lumbridge_easy_count is the low nibble of achievement_diary_taskcounts4; started is bit 5 of 1188.
                client.awaitVarp(TASKCOUNTS4, 1)
                client.awaitVarp(ACHIEVEMENT_DIARY, 1 shl 5)

                client.ifButton(JournalTab.SIDE_JOURNAL, JournalTab.DIARIES.button, 1)
                client.await("the Open option on Lumbridge's row") {
                    Unit.takeIf { client.opEnabled(Diaries.PANEL, Diaries.PANEL_TASKBOX, LUMBRIDGE_ROW, Diaries.PANEL_OPEN) }
                }
                assertTrue((0 until Diaries.PANEL_ROWS).all { client.opEnabled(Diaries.PANEL, Diaries.PANEL_TASKBOX, it, Diaries.PANEL_OPEN) })

                client.ifButton(Diaries.PANEL, Diaries.PANEL_TASKBOX, Diaries.PANEL_OPEN, sub = LUMBRIDGE_ROW)
                client.await("the journal open") { Unit.takeIf { Diaries.JOURNAL in client.openInterfaces() } }
                val texts = client.texts(Diaries.JOURNAL)
                assertTrue(texts.getValue(Diaries.JOURNAL_TITLE).startsWith("[diary_lumbridge.window"))
                assertEquals("[diary_lumbridge.title]", texts.getValue(Diaries.FIRST_LINE))
                val tasks = (0 until Diaries.LINES).mapNotNull { texts[Diaries.FIRST_LINE + it] }
                assertEquals("<str>[diary_lumbridge_tasks.easy_5]", tasks.single { "easy_5]" in it })
                assertEquals("[diary_lumbridge_tasks.easy_4]", tasks.single { "easy_4]" in it })
                assertEquals("", texts.getValue(Diaries.FIRST_LINE + Diaries.LINES - 1))
                val sized = client.await("the line count script") { client.clientScripts().lastOrNull { it.id == Diaries.LINE_COUNT_SCRIPT } }
                assertEquals(listOf<Any>(1, 1 + 4 * 2 + 41), sized.args)
                client.closeModal()

                client.command("diary complete lumbridge easy all")
                client.await("the tier message") { Unit.takeIf { client.seen("[diary.tier_done") == 1 } }
                assertEquals(12, client.seen("[diary.task_done"))
                client.awaitVarp(TASKCOUNTS4, 12)
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            val gone = System.nanoTime() + 10_000_000_000L
            while (server.world.playerNamed("alice") != null && System.nanoTime() < gone) Thread.sleep(20)
            server.login("alice").use { client ->
                client.ready()
                client.awaitVarp(TASKCOUNTS4, 12)
                assertEquals((1..12).sumOf { 1 shl it }, client.varp(LUMB_DRAY))
                client.command("diary reset lumbridge")
                client.awaitVarp(TASKCOUNTS4, 0)
                client.awaitVarp(LUMB_DRAY, 0)
                client.awaitVarp(ACHIEVEMENT_DIARY, 0)
            }
        }
    }

    private companion object {
        const val LUMB_DRAY = 1194
        const val ACHIEVEMENT_DIARY = 1188
        const val TASKCOUNTS4 = 1722

        /** Lumbridge & Draynor's key in enum_595, so its row's slot. */
        const val LUMBRIDGE_ROW = 6
    }
}
