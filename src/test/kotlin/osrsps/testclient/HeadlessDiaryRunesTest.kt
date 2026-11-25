package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.Mining
import osrsps.game.skill.RuneCrafting
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/** The Varrock easy diary's "Craft some Earth runes" through the real altar, next to air runes that do nothing. */
class HeadlessDiaryRunesTest {
    private val air = RuneCrafting.RECIPES.first { it.key == "air" }
    private val earth = RuneCrafting.RECIPES.first { it.key == "earth" }

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val ids = listOf(Mining.RUNE_ESSENCE, air.rune, earth.rune)
        val objs = arrayOfNulls<ObjType>(ids.max() + 1)
        for (id in ids) objs[id] = ObjType(id, "Obj $id", stackable = id != Mining.RUNE_ESSENCE)
        fun altar(id: Int) = LocType(id, "Altar $id", listOf("Craft-rune", null, null, null, null), LocCollisionType(width = 3, length = 3))
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            objTypes = ObjTypes(objs), random = GameRandom(3),
            locTypes = LocTypes(listOf(altar(air.altar), altar(earth.altar)).associateBy { it.id }),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(air.altar, LocShape.CENTREPIECE_STRAIGHT, 0, AIR_X, ALTAR_Z, 0))
                add(ZoneLoc(earth.altar, LocShape.CENTREPIECE_STRAIGHT, 0, EARTH_X, ALTAR_Z, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.tasksDone() = messages().count { it.text.startsWith("[diary.task_done") }

    @Test
    fun `earth runes crafted are the Varrock easy task, and air runes aren't`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel runecraft 9")
                client.await("level 9") { client.stat(RUNECRAFT)?.takeIf { it.level == 9 } }

                client.command("tele ${AIR_X - 2} $ALTAR_Z 0")
                client.await("by the air altar") { client.position?.takeIf { it.x == AIR_X - 2 } }
                client.command("item ${Mining.RUNE_ESSENCE} 2")
                client.await("the essence") { if (client.count(Mining.RUNE_ESSENCE) == 2) Unit else null }
                client.locOp(air.altar, AIR_X, ALTAR_Z, 1)
                client.await("air runes") { if (client.count(air.rune) > 0) Unit else null }
                client.awaitTicks(2)
                assertEquals(0, client.tasksDone(), "air runes")

                client.command("tele ${EARTH_X - 2} $ALTAR_Z 0")
                client.await("by the earth altar") { client.position?.takeIf { it.x == EARTH_X - 2 } }
                client.command("item ${Mining.RUNE_ESSENCE} 2")
                client.await("more essence") { if (client.count(Mining.RUNE_ESSENCE) == 2) Unit else null }
                client.locOp(earth.altar, EARTH_X, ALTAR_Z, 1)
                client.await("earth runes") { if (client.count(earth.rune) > 0) Unit else null }
                client.await("the diary task") { Unit.takeIf { client.tasksDone() == 1 } }
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val RUNECRAFT = 20
        const val AIR_X = 3216
        const val EARTH_X = 3226
        const val ALTAR_Z = 3222
    }
}
