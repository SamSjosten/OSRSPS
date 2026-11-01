package osrsps.testclient

import osrsps.game.PlayerOps
import osrsps.game.World
import osrsps.game.dialogue.Chatbox
import osrsps.game.social.Reports
import osrsps.map.CollisionMap
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Report abuse: the report screen from Report on a player and the chatbox's Report button, and the report kept. */
class HeadlessReportTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, cycleMillis = millis,
            reportsFile = dir.resolve(Reports.FILE_NAME),
        )
    }

    private fun HeadlessClient.saw(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.awaitReportScreen(name: String?) {
        await("the report screen") { openInterfaces().takeIf { Reports.INTERFACE in it } }
        val init = await("the report screen's script") { clientScripts().lastOrNull { it.id == Reports.INIT_SCRIPT } }
        assertEquals(listOf<Any>(1, if (name != null) 1 else 0, name ?: ""), init.args.map { if (it is String) it else (it as Number).toInt() })
    }

    @Test
    fun `Report on a player opens the screen with their name, and a rule picked is kept with their last chat lines`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    // Spread over ticks: twelve lines in one cycle lost the last two (RSProt caps what it reads from a player a cycle).
                    for (i in 1..12) {
                        bob.say("line $i")
                        if (i % 4 == 0) bob.awaitTicks(2)
                    }
                    alice.await("bob's report option") { alice.playerOps()[PlayerOps.REPORT] }
                    alice.playerOp(bob.index, PlayerOps.REPORT)
                    alice.awaitReportScreen("bob")
                    alice.sendReport("bob", 15)
                    alice.saw("[report.received]")
                    alice.await("the screen closed") { if (Reports.INTERFACE in alice.openInterfaces()) null else Unit }
                    val report = server.world.reports.recent().single()
                    assertEquals("alice", report.reporter)
                    assertEquals("bob", report.name)
                    assertEquals(15, report.rule)
                    // The last ten lines he said, oldest first.
                    assertEquals((3..12).map { "line $it" }, report.chat)
                    // One line in the file, with the same.
                    val lines = Files.readAllLines(dir.resolve(Reports.FILE_NAME))
                    assertEquals(1, lines.size)
                    assertTrue(lines[0].contains("\"reporter\":\"alice\"") && lines[0].contains("\"name\":\"bob\"") && lines[0].contains("\"line 12\""), lines[0])
                    // A second snapshot, with the screen gone, isn't taken.
                    alice.sendReport("bob", 15)
                    alice.awaitTicks(2)
                    assertEquals(1, server.world.reports.recent().size)
                    assertEquals("alice reported bob: scamming (10 chat lines)", alice.commandReply("reports") { it.contains("reported") })
                }
            }
        }
    }

    @Test
    fun `the chatbox's Report button asks for a name, a rule the screen doesn't offer is refused, and Close shuts it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                assertEquals("No reports since the server started", alice.commandReply("reports") { it.startsWith("No reports") })
                alice.ifButton(Chatbox.CHATBOX, Reports.CHATBOX_BUTTON, Reports.OP_REPORT_ABUSE)
                alice.awaitReportScreen(null)
                // 3 isn't one of the screen's rules.
                alice.sendReport("nobody", 3)
                alice.awaitTicks(2)
                assertTrue(server.world.reports.recent().isEmpty())
                assertTrue(Reports.INTERFACE in alice.openInterfaces())
                alice.ifButton(Reports.INTERFACE, Reports.CLOSE, 1)
                alice.await("the screen closed") { if (Reports.INTERFACE in alice.openInterfaces()) null else Unit }
                // A name never seen: no chat lines, still kept.
                alice.ifButton(Chatbox.CHATBOX, Reports.CHATBOX_BUTTON, Reports.OP_REPORT_ABUSE)
                alice.awaitReportScreen(null)
                alice.sendReport("Some One", 7, mute = true)
                alice.saw("[report.received]")
                val report = server.world.reports.recent().single()
                assertEquals("Some One", report.name)
                assertTrue(report.chat.isEmpty())
                alice.await("the screen closed") { if (Reports.INTERFACE in alice.openInterfaces()) null else Unit }
            }
        }
    }
}
