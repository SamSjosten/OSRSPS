package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Skill
import osrsps.game.combat.StatRestore

/**
 * A boost wearing off and a drain coming back through the headless client: a level a minute, each sent as a stat
 * update. The rl-client script test-runner/examples/stats-restore.json watches the same in the real client.
 */
class HeadlessStatRestoreTest {
    @Test
    fun `a boost goes down and a drain comes back a level every 100 ticks`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel strength 50") { it.contains("Strength") }
                client.commandReply("setlevel mining 50") { it.contains("Mining") }
                client.commandReply("boost strength 55") { it == "Strength is now 55/50" }
                client.commandReply("boost mining 45") { it == "Mining is now 45/50" }
                // Counted by the ticks the updates were sent in, not when the waits see them, which a slow machine makes late.
                val first = client.await("the first level off", timeoutMillis = 30_000) {
                    if (client.stat(Skill.STRENGTH.id)?.level == 54) client.statTick(Skill.STRENGTH.id) else null
                }
                // Something sent now and then, as a client with nothing to send for 15 seconds is timed out.
                client.commandReply("hp") { it.startsWith("You have") }
                val second = client.await("the second", timeoutMillis = 30_000) {
                    if (client.stat(Skill.STRENGTH.id)?.level == 53) client.statTick(Skill.STRENGTH.id) else null
                }
                assertEquals(StatRestore.CYCLE_TICKS, second - first)
                client.commandReply("hp") { it.startsWith("You have") }
                client.await("the drain coming back", timeoutMillis = 30_000) { if (client.stat(Skill.MINING.id)?.level == 47) Unit else null }
            }
        }
    }
}
