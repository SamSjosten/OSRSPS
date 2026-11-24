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
import osrsps.game.skill.Ourania
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/** Runecraft step RC6 through the headless client: down the ladder to the Ourania Altar and random runes from pure essence. */
class HeadlessOuraniaTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(9075 + 1)
        objs[Mining.RUNE_ESSENCE] = ObjType(Mining.RUNE_ESSENCE, "Rune essence")
        objs[Mining.PURE_ESSENCE] = ObjType(Mining.PURE_ESSENCE, "Pure essence")
        for (rune in Ourania.RUNES) objs[rune.obj] = ObjType(rune.obj, "${rune.key} rune", stackable = true)
        val map = CollisionMap().apply {
            markLoaded(2452 shr 6, 3231 shr 6)
            markLoaded(3015 shr 6, 5630 shr 6)
            markLoaded(3059 shr 6, 5578 shr 6)
        }
        World(
            crcs, map, objTypes = ObjTypes(objs), random = GameRandom(5),
            locTypes = LocTypes(
                mapOf(
                    Ourania.SURFACE_LADDER to LocType(Ourania.SURFACE_LADDER, "Ladder", listOf("Climb", null, null, null, null)),
                    Ourania.ALTAR to LocType(Ourania.ALTAR, "Altar", listOf("Craft-rune", null, null, null, null), LocCollisionType(width = 3, length = 3)),
                ),
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(Ourania.SURFACE_LADDER, LocShape.CENTREPIECE_STRAIGHT, 2, LADDER_X, LADDER_Z, 0))
                add(ZoneLoc(Ourania.ALTAR, LocShape.CENTREPIECE_STRAIGHT, 0, ALTAR_X, ALTAR_Z, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    @Test
    fun `the ladder leads down and pure essence becomes one random rune each, for 1,7 times the XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(LADDER_X + 1, LADDER_Z + 1)
                client.locOp(Ourania.SURFACE_LADDER, LADDER_X, LADDER_Z, Ourania.CLIMB)
                client.await("the cave") { client.position?.takeIf { it == Coord(Ourania.CAVE_LANDING_X, Ourania.CAVE_LANDING_Z, 0) } }
                client.command("setlevel runecraft 99")
                client.await("level 99") { client.stat(RUNECRAFT)?.takeIf { it.level == 99 } }
                client.tele(ALTAR_X + 1, ALTAR_Z - 1)
                client.command("item ${Mining.RUNE_ESSENCE} 1")
                client.await("rune essence") { if (client.count(Mining.RUNE_ESSENCE) == 1) Unit else null }
                client.locOp(Ourania.ALTAR, ALTAR_X, ALTAR_Z, Ourania.CRAFT_RUNE)
                client.await("the refusal") { client.messages().firstOrNull { it.text == "[runecraft_ourania.rune_essence]" } }
                client.command("item ${Mining.PURE_ESSENCE} 25")
                client.await("pure essence") { if (client.count(Mining.PURE_ESSENCE) == 25) Unit else null }
                val start = server.world.playerNamed("alice")!!.skills.tenths(osrsps.game.Skill.RUNECRAFT)
                client.locOp(Ourania.ALTAR, ALTAR_X, ALTAR_Z, Ourania.CRAFT_RUNE)
                client.await("the runes") { client.messages().firstOrNull { it.text == "[runecraft_ourania.bind]" } }
                client.await("the essence gone") { if (client.count(Mining.PURE_ESSENCE) == 0) Unit else null }
                val made = Ourania.RUNES.associateWith { client.count(it.obj) }
                assertEquals(25, made.values.sum())
                assertEquals(1, client.count(Mining.RUNE_ESSENCE))
                val expected = made.entries.sumOf { (rune, n) -> rune.xpTenths * n } * 17 / 10
                assertEquals(start + expected, server.world.playerNamed("alice")!!.skills.tenths(osrsps.game.Skill.RUNECRAFT))
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val RUNECRAFT = 20
        const val LADDER_X = 2452
        const val LADDER_Z = 3231
        const val ALTAR_X = 3059
        const val ALTAR_Z = 5578
    }
}
