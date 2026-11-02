package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.map.Tile

/** agility.json's format, and the step and angle sums the obstacles use. */
class AgilityDataTest {
    @Test
    fun `the shipped file loads`() {
        AgilityData.load()
    }

    @Test
    fun `every kind of move parses`() {
        val data = AgilityData.parse(
            """
            {"obstacles": [{
              "key": "test_wall", "loc": 100, "option": "Climb", "level": 10, "xpTenths": 55, "success": [-1, 200], "damage": [1, 5],
              "routes": [{
                "from": [3200, 3200, 0],
                "moves": [
                  {"type": "anim", "anim": 828, "ticks": 2},
                  {"type": "teleport", "to": [3200, 3201, 1]},
                  {"type": "walk", "path": [[3200, 3205], [3203, 3205]], "anim": 762},
                  {"type": "slide", "to": [3203, 3208, 1], "anim": 741, "startCycles": 5, "endCycles": 35, "ticks": 2}
                ],
                "fail": [{"type": "teleport", "to": [3199, 3199, 0]}]
              }]
            }]}
            """,
        )
        val wall = data.byLoc.getValue(100).single()
        assertEquals("Climb", wall.option)
        assertEquals(10, wall.level)
        assertEquals(55, wall.xpTenths)
        assertEquals(-1 to 200, wall.success)
        assertEquals(1..5, wall.damage)
        assertNull(wall.at)
        val route = wall.routes.single()
        assertEquals(AgilityTile(3200, 3200, 0), route.from)
        assertEquals(
            listOf(
                AgilityMove.Anim(828, 2),
                AgilityMove.Teleport(AgilityTile(3200, 3201, 1)),
                AgilityMove.Walk(listOf(Tile(3200, 3205), Tile(3203, 3205)), 762),
                AgilityMove.Slide(AgilityTile(3203, 3208, 1), 741, 5, 35, 2),
            ),
            route.moves,
        )
        assertEquals(listOf(AgilityMove.Teleport(AgilityTile(3199, 3199, 0))), route.fail)
    }

    private fun obstacle(key: String, loc: Int, at: String? = null, extra: String = "") =
        """{"key": "$key", "loc": $loc, "option": "Climb", "level": 1, "xpTenths": 10${at?.let { ", \"at\": $it" } ?: ""}$extra,
            "routes": [{"from": [3200, 3200, 0], "moves": [{"type": "anim", "anim": 828, "ticks": 1}]}]}"""

    @Test
    fun `two obstacles on one loc type need a tile each, and keys are unique`() {
        assertFailsWith<IllegalArgumentException> {
            AgilityData.parse("""{"obstacles": [${obstacle("a", 1)}, ${obstacle("b", 1, "[3200, 3200, 0]")}]}""")
        }
        assertFailsWith<IllegalArgumentException> {
            AgilityData.parse("""{"obstacles": [${obstacle("a", 1)}, ${obstacle("a", 2)}]}""")
        }
        val data = AgilityData.parse("""{"obstacles": [${obstacle("a", 1, "[3200, 3201, 0]")}, ${obstacle("b", 1, "[3200, 3202, 0]")}]}""")
        assertEquals(listOf("a", "b"), data.byLoc.getValue(1).map { it.key })
    }

    @Test
    fun `an obstacle that can fail needs both a success pair and fail moves`() {
        assertFailsWith<IllegalArgumentException> {
            AgilityData.parse("""{"obstacles": [${obstacle("a", 1, extra = ", \"success\": [10, 20]")}]}""")
        }
    }

    @Test
    fun `steps go one tile at a time through each turning point`() {
        assertEquals(
            listOf(Tile(3201, 3201), Tile(3202, 3202), Tile(3202, 3203), Tile(3201, 3203)),
            Agility.steps(Tile(3200, 3200), listOf(Tile(3202, 3202), Tile(3202, 3203), Tile(3201, 3203))),
        )
        assertEquals(emptyList(), Agility.steps(Tile(3200, 3200), listOf(Tile(3200, 3200))))
    }

    @Test
    fun `angles run south 0, west 512, north 1024, east 1536`() {
        assertEquals(0, Agility.angle(0, -3))
        assertEquals(512, Agility.angle(-2, 0))
        assertEquals(1024, Agility.angle(0, 1))
        assertEquals(1536, Agility.angle(4, 0))
        assertEquals(256, Agility.angle(-1, -1))
        assertEquals(1792, Agility.angle(1, -1))
    }

    @Test
    fun `the Draynor rooftop course is the Wiki's seven obstacles, 120 XP a lap`() {
        val data = AgilityData.load()
        val course = data.courses.single { it.key == "draynor" }
        assertEquals(listOf(50, 80, 70, 70, 100, 40, 790), course.obstacles.map { it.xpTenths })
        assertEquals(1_200, course.obstacles.sumOf { it.xpTenths } + course.lapXpTenths)
        assertEquals(listOf(11404, 11405, 11406, 11430, 11630, 11631, 11632), course.obstacles.map { it.loc })
        assertTrue(course.obstacles.all { it.level == 1 && it.success == null }, "nothing fails since the 2024 rework")
        assertEquals(AgilityLines.DRAYNOR_LAPS, course.lapLine)
    }

    @Test
    fun `a course must name listed obstacles, each on one course`() {
        val one = """{"key": "a", "loc": 1, "option": "Climb", "level": 1, "xpTenths": 10, "routes": [{"from": [3200, 3200, 0], "moves": []}]}"""
        assertFailsWith<IllegalStateException> {
            AgilityData.parse("""{"obstacles": [$one], "courses": [{"key": "c", "obstacles": ["b"], "lapXpTenths": 0, "lapLine": "agility.draynor_laps"}]}""")
        }
        assertFailsWith<IllegalArgumentException> {
            AgilityData.parse("""{"obstacles": [$one], "courses": [{"key": "c", "obstacles": ["a", "a"], "lapXpTenths": 0, "lapLine": "agility.draynor_laps"}]}""")
        }
    }
}
