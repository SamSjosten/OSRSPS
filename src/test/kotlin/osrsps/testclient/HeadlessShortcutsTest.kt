package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.AgilityData
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Agility step 7 through the headless client: the Misthalin shortcuts with their real locs (as the 241 map places them)
 * and agility.json, on open ground (no cache collision in tests) but for the rivers around the stepping stones, which are
 * blocked as the map has them, so the stones are used from the bank. Each is crossed there and back. The rl-client script
 * test-runner/examples/agility-shortcuts.json runs them in the real world.
 */
class HeadlessShortcutsTest {
    /** A shortcut: its loc where the map has it, and its two sides; [backLoc] is the far end's loc, for a tunnel. */
    private class Shortcut(
        val id: Int, val name: String, val option: String, val shape: Int, val angle: Int, val x: Int, val z: Int,
        val sideA: Pair<Int, Int>, val sideB: Pair<Int, Int>, val level: Int, val backLoc: Triple<Int, Int, Int>? = null,
    )

    private val shortcuts = listOf(
        Shortcut(16518, "Fence", "Jump-over", 0, 3, 3240, 3335, 3240 to 3335, 3240 to 3334, 13),
        Shortcut(16529, "Underwall tunnel", "Climb-into", 10, 0, 3138, 3516, 3137 to 3517, 3142 to 3512, 21, Triple(16530, 3141, 3513)),
        Shortcut(16533, "Stepping stone", "Jump-onto", 22, 3, 3151, 3363, 3149 to 3363, 3154 to 3363, 31),
        Shortcut(19032, "Underwall tunnel", "Climb-into", 10, 2, 3067, 3257, 3066 to 3257, 3070 to 3257, 42, Triple(19036, 3069, 3257)),
        Shortcut(62422, "Broken fence", "Squeeze-Through", 0, 2, 3085, 3352, 3085 to 3352, 3086 to 3352, 49),
        Shortcut(62412, "Stepping stone", "Jump-to", 22, 0, 3193, 3340, 3193 to 3338, 3193 to 3346, 52, Triple(62412, 3193, 3344)),
    )

    private val text = AgilityData::class.java.getResource(AgilityData.RESOURCE)!!.readText()

    private fun locs() = shortcuts.flatMap { s ->
        listOf(ZoneLoc(s.id, s.shape, s.angle, s.x, s.z, 0)) + listOfNotNull(s.backLoc?.let { (id, x, z) -> ZoneLoc(id, s.shape, (s.angle + 2) % 4, x, z, 0) })
    }

    private fun server(dir: Path, agilityData: AgilityData = AgilityData.parse(text)) = TestServer(dir, cycleMillis = 30) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                for (s in shortcuts) markLoaded(s.x shr 6, s.z shr 6)
                for ((xs, zs) in RIVERS) for (x in xs) for (z in zs) add(x, z, 0, CollisionFlag.FLOOR)
            },
            locTypes = LocTypes(
                shortcuts.associate {
                    it.id to LocType(it.id, it.name, listOf(it.option, null, null, null, null), LocCollisionType(width = 1, length = 1, interactType = 0))
                } + shortcuts.mapNotNull { s -> s.backLoc?.let { it.first to LocType(it.first, s.name, listOf(s.option, null, null, null, null), LocCollisionType(width = 1, length = 1, interactType = 0)) } },
            ),
            staticLocs = StaticLocs().apply { for (loc in locs()) add(loc) },
            agilityData = agilityData, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.teleTo(x: Int, z: Int) {
        command("tele $x $z 0")
        await("at $x, $z") { position?.takeIf { it.x == x && it.z == z && it.level == 0 } }
    }

    /**
     * Crosses by [id] to [to], then waits two ticks: the landing shows a tick before the crossing's script ends, and a
     * click while it still runs is dropped (the player is delayed), as the runner script's waitTicks allows for.
     */
    private fun HeadlessClient.crossTo(id: Int, x: Int, z: Int, to: Pair<Int, Int>) {
        locOp(id, x, z, 1)
        await("across to $to by $id") { position?.takeIf { it.x == to.first && it.z == to.second && it.level == 0 } }
        awaitTicks(2)
    }

    @Test
    fun `each shortcut goes there and back, the far end of a tunnel or the stones from its own loc`(@TempDir dir: Path) {
        val sure = AgilityData.parse(text.replace(SUCCESS, "\"success\": [256, 256]"))
        server(dir, sure).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel agility 52")
                val before = client.await("level 52") { client.stat(AGILITY)?.takeIf { it.level == 52 } }.xp
                for (s in shortcuts) {
                    client.teleTo(s.sideA.first, s.sideA.second)
                    client.crossTo(s.id, s.x, s.z, s.sideB)
                    val (backId, backX, backZ) = s.backLoc ?: Triple(s.id, s.x, s.z)
                    client.crossTo(backId, backX, backZ, s.sideA)
                }
                // 3 XP twice on the stone, 1.5 twice through the manor fence, 5 twice on the south stones: 19 XP.
                assertEquals(before + 19, client.await("the shortcuts' XP") { client.stat(AGILITY)?.takeIf { it.xp >= before + 19 } }.xp)
            }
        }
    }

    @Test
    fun `below its level a shortcut says so, and the stone can fail onto the far bank for 1 to 3 damage and 1 XP`(@TempDir dir: Path) {
        val falls = AgilityData.parse(text.replace(SUCCESS, "\"success\": [-1, -1]"))
        server(dir, falls).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel hitpoints 10")
                client.await("10 hitpoints") { client.stat(HITPOINTS)?.takeIf { it.level == 10 } }
                client.teleTo(3240, 3335)
                client.locOp(16518, 3240, 3335, 1)
                client.await("the level message") { client.messages().firstOrNull { it.text == "[agility.varrock_fence_level level=13]" } }
                assertEquals(3335, client.position!!.z)
                client.command("setlevel agility 31")
                client.await("level 31") { client.stat(AGILITY)?.takeIf { it.level == 31 } }
                val before = client.stat(AGILITY)!!.xp
                client.teleTo(3149, 3363)
                client.crossTo(16533, 3151, 3363, 3154 to 3363)
                val hp = client.await("the hit") { client.stat(HITPOINTS)?.takeIf { it.level < 10 } }.level
                assertTrue(hp in 7..9, "1 to 3 damage, left at $hp")
                assertEquals(before + 1, client.await("the fall's XP") { client.stat(AGILITY)?.takeIf { it.xp > before } }.xp)
            }
        }
    }

    private companion object {
        const val AGILITY = 16
        const val HITPOINTS = 3

        /**
         * The river tiles around each stepping stone (x range, z range), from the 241 map's blocked tiles: the banks the
         * stones are jumped from stay open.
         */
        val RIVERS = listOf(3150..3153 to 3360..3366, 3190..3196 to 3339..3345)

        /** The stone's success pair, as agility.json writes it. */
        val SUCCESS = Regex("\"success\": \\[\\s*225,\\s*255\\s*]")
    }
}
