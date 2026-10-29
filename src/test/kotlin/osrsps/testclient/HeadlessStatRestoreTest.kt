package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.Prayers
import osrsps.game.combat.StatRestore
import osrsps.map.CollisionMap

/**
 * A boost wearing off and a drain coming back through the headless client: a level a minute, each sent as a stat
 * update; a boost's cycle kept in its segment over a logout; and Rapid Heal clicked on starting boosted Hitpoints' cycle
 * again. The rl-client script test-runner/examples/stats-restore.json watches the same in the real client.
 */
class HeadlessStatRestoreTest {
    // Shaped like the 241 cache's Rapid Heal obj: 630 the bit, 1751 the prayer book component, 1752 the name, 1753 the
    // level, 1755 the sound.
    private val rapidHeal = ObjType(
        RAPID_HEAL_OBJ, "Null", internalName = "xmas16_ball_deflated_green",
        params = mapOf(630 to 7, 1751 to (Prayers.PRAYERBOOK shl 16 or RAPID_HEAL), 1752 to "Rapid Heal", 1753 to 22, 1755 to 2679),
    )

    private fun praying(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(RAPID_HEAL_OBJ + 1)
        objs[RAPID_HEAL_OBJ] = rapidHeal
        val objTypes = ObjTypes(objs)
        World(crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objTypes, prayerList = Prayers.load(objTypes), cycleMillis = millis)
    }

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

    @Test
    fun `a boost's cycle keeps its segment over a logout and starts it again at login`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel attack 50") { it.contains("Attack") }
                client.commandReply("boost attack 55") { it == "Attack is now 55/50" }
                client.commandReply("statcycles") { it.startsWith("Boost and drain cycles started again") }
                // 30 ticks in: the second of the cycle's five segments.
                client.awaitTicks(30)
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.loginAgain("bob").use { client ->
                val login = client.await("the login's stats") { if (client.stat(Skill.ATTACK.id)?.level == 55) client.statTick(Skill.ATTACK.id) else null }
                client.commandReply("hp") { it.startsWith("You have") }
                val off = client.await("the level off", timeoutMillis = 30_000) {
                    if (client.stat(Skill.ATTACK.id)?.level == 54) client.statTick(Skill.ATTACK.id) else null
                }
                assertEquals(StatRestore.CYCLE_TICKS - StatRestore.SEGMENT_TICKS, off - login, "the second segment's start and the three after it")
            }
        }
    }

    @Test
    fun `Rapid Heal clicked on starts boosted Hitpoints' cycle again, a minute from the click`(@TempDir dir: Path) {
        praying(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel prayer 99") { it.contains("Prayer") }
                client.commandReply("setlevel hitpoints 99") { it.contains("Hitpoints") }
                client.commandReply("boost hitpoints 110") { it == "Hitpoints is now 110/99" }
                client.commandReply("statcycles") { it.startsWith("Boost and drain cycles started again") }
                client.awaitTicks(50)
                client.ifButton(Prayers.PRAYERBOOK, RAPID_HEAL, 1)
                val on = client.await("Rapid Heal on") { if (client.varp(Prayers.VARP_PRAYERS) != 0) client.varpTick(Prayers.VARP_PRAYERS) else null }
                client.commandReply("hp") { it.startsWith("You have 110/99") }
                val off = client.await("the hitpoint off", timeoutMillis = 30_000) {
                    if (client.stat(Skill.HITPOINTS.id)?.level == 109) client.statTick(Skill.HITPOINTS.id) else null
                }
                assertEquals(StatRestore.CYCLE_TICKS, off - on)
            }
        }
    }

    /** Logs in once the server has let the last login of [name] go: until then a login is refused as still on. */
    private fun TestServer.loginAgain(name: String): HeadlessClient {
        repeat(50) {
            runCatching { login(name) }.onSuccess { return it }
            Thread.sleep(100)
        }
        return login(name)
    }

    private companion object {
        // prayerbook:prayer1 is 541:9, so Rapid Heal, prayer 8, is 16.
        const val RAPID_HEAL = 16
        const val RAPID_HEAL_OBJ = 20810
    }
}
