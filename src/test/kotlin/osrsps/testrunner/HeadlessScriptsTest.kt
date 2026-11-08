package osrsps.testrunner

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.content.CustomLocs
import osrsps.game.content.CustomVars
import osrsps.game.content.Lodestones
import osrsps.game.loc.LocTypes
import osrsps.map.CollisionMap
import osrsps.testclient.TestServer

/**
 * The runner's own scripts played through [HeadlessGame] on the tests' small server (no cache, open ground around
 * Lumbridge), so the bridge between the runner and the headless client is checked in every build. The examples
 * themselves need the live cache: `./gradlew headlessScripts`.
 */
class HeadlessScriptsTest {
    private fun script(json: String) = Script.parse("test.json", json)

    @Test
    fun `a script's commands, walk and location checks play through the headless client`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            ScriptSession("test", { server.login(it) }, HeadlessGame.Types.of(server.world)).use { session ->
                val result = session.run(
                    script(
                        """
                        {"account": "test", "steps": [
                          {"step": "waitForLogin"},
                          {"step": "command", "text": "::tele 3222 3218 0", "expectMessage": "Teleported to 3222, 3218, 0"},
                          {"step": "assertLocation", "x": 3222, "y": 3218, "plane": 0},
                          {"step": "walk", "x": 3225, "y": 3218},
                          {"step": "waitTicks", "ticks": 2},
                          {"step": "assertLocation", "x": 3225, "y": 3218}
                        ]}
                        """.trimIndent(),
                    ),
                    "test",
                )
                assertEquals(Result.PASS, result.result, result.failure)
                assertEquals(6, result.steps.count { it.outcome == Result.PASS })
            }
        }
    }

    @Test
    fun `a step that never holds fails with what the headless client saw`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            ScriptSession("test", { server.login(it) }, HeadlessGame.Types.of(server.world)).use { session ->
                val result = session.run(
                    script(
                        """
                        {"account": "test", "steps": [
                          {"step": "waitForLogin"},
                          {"step": "command", "text": "::tele 3222 3218 0", "expectMessage": "Teleported"},
                          {"step": "assertLocation", "x": 3230, "y": 3218, "timeoutMs": 1500},
                          {"step": "waitTicks", "ticks": 1}
                        ]}
                        """.trimIndent(),
                    ),
                    "test",
                )
                assertEquals(Result.FAIL, result.result)
                assertTrue(result.failure!!.startsWith("step 3 (assert location 3230, 3218): timed out"), result.failure)
                assertTrue("3222, 3218" in result.failure!!, result.failure)
                assertEquals("skipped", result.steps.last().outcome)
            }
        }
    }

    /** The lodestone script's minimap icon check, on a world with the stones: the headless backend reads the element off the server's loc types. */
    @Test
    fun `assertMapIcon reads the stone's map element, and says what the loc names instead`(@TempDir dir: Path) {
        val vars = CustomVars.after(VarTypes.NONE)
        val server = TestServer(dir) { crcs, millis ->
            World(
                crcs, CollisionMap().apply { for (site in Lodestones.SITES) markLoaded(site.x shr 6, site.z shr 6) },
                customVars = vars, locTypes = LocTypes.NONE.with(CustomLocs(vars).types), cycleMillis = millis,
            )
        }
        server.use {
            ScriptSession("test", { server.login(it) }, HeadlessGame.Types.of(server.world)).use { session ->
                val result = session.run(
                    script(
                        """
                        {"account": "test", "steps": [
                          {"step": "waitForLogin"},
                          {"step": "command", "text": "::tele 3236 3221 0", "expectMessage": "Teleported to 3236, 3221, 0"},
                          {"step": "assertMapIcon", "loc": "Lodestone", "x": 3232, "y": 3220, "icon": ${Lodestones.MAP_ELEMENT}},
                          {"step": "assertMapIcon", "loc": "Lodestone", "x": 3232, "y": 3220, "icon": 1, "timeoutMs": 500}
                        ]}
                        """.trimIndent(),
                    ),
                    "test",
                )
                assertEquals(Result.FAIL, result.result)
                assertEquals("icon ${Lodestones.MAP_ELEMENT}", result.steps[2].detail)
                assertEquals(
                    "step 4 (assert map icon 1 on Lodestone at 3232, 3220): timed out after 500 ms: loc \"Lodestone\" at 3232, 3220 has map icon ${Lodestones.MAP_ELEMENT}, not 1",
                    result.failure,
                )
            }
        }
    }

    @Test
    fun `a script for another account is refused, as the plugin refuses it`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            ScriptSession("test", { server.login(it) }, HeadlessGame.Types.of(server.world)).use { session ->
                val result = session.run(script("""{"account": "someone", "steps": [{"step": "waitForLogin"}]}"""), "test")
                assertEquals(Result.FAIL, result.result)
                assertTrue("not in the plugin's test accounts" in result.failure!!, result.failure)
            }
        }
    }

    @Test
    fun `the reset between scripts reads, and the pairs are the -test and -test2 files`() {
        for (account in ScriptSession.ACCOUNTS) assertEquals(8, Script.parse("_between.json", HeadlessScripts.resetScript(account)).steps.size)
        assertTrue(HeadlessScripts.pairReason("social-trade-test.json") != null)
        assertTrue(HeadlessScripts.pairReason("social-trade-test2.json") != null)
        assertNull(HeadlessScripts.pairReason("social-ignore-2.json"))
        assertNull(HeadlessScripts.pairReason("lumbridge-bank.json"))
    }

    @Test
    fun `every example script reads`() {
        val examples = Path.of("test-runner/examples").listDirectoryEntries("*.json")
        assertTrue(examples.size > 100, "found ${examples.size} examples")
        for (file in examples) Script.parse(file.name, file.readText())
    }

    @Test
    fun `the options name the scripts, a folder of them, and where results go`(@TempDir dir: Path) {
        Files.writeString(dir.resolve("b.json"), "{}")
        Files.writeString(dir.resolve("a.json"), "{}")
        val options = HeadlessScripts.Options.parse(arrayOf("--out", "o", "--tick-ms", "100", dir.toString()))
        assertEquals(listOf("a.json", "b.json"), options.scripts.map { it.name })
        assertEquals(Path.of("o"), options.outDir)
        assertEquals(100L, options.tickMillis)
    }

    @Test
    fun `two scripts of one name are refused, as their results would overwrite each other`(@TempDir dir: Path) {
        Files.createDirectories(dir.resolve("x"))
        Files.writeString(dir.resolve("a.json"), "{}")
        Files.writeString(dir.resolve("x/a.json"), "{}")
        val refused = assertFailsWith<IllegalArgumentException> { HeadlessScripts.Options.parse(arrayOf(dir.toString(), dir.resolve("x").toString())) }
        assertTrue("a.json" in refused.message!!, refused.message)
    }
}
