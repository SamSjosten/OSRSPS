package osrsps.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.trail.StepType
import osrsps.game.trail.TrailProgress
import osrsps.game.trail.Trail
import osrsps.game.trail.TrailSaves
import osrsps.game.trail.TrailTarget
import osrsps.game.trail.TrailTier
import osrsps.game.trail.TrailTile

class TrailLoaderTest {
    private fun coord(x: Int, z: Int, level: Int = 0) = (level shl 28) or (x shl 14) or z

    private val rows = mapOf(
        // Targets: a dig spot and an NPC.
        10 to DbRow(TrailLoader.TARGET_COORD, mapOf(0 to listOf(coord(3166, 3361)), 1 to listOf("a description"))),
        11 to DbRow(TrailLoader.TARGET_NPC, mapOf(0 to listOf(3227), 2 to listOf(coord(3221, 3218, 1)), 3 to listOf("a description"))),
        // An easy map (its item 2713), an easy cryptic talk step, a hard coordinate with a fight.
        20 to DbRow(StepType.MAP.table, mapOf(0 to listOf(107), 1 to listOf(1), 2 to listOf(10), 4 to listOf(1))),
        21 to DbRow(StepType.CRYPTIC.table, mapOf(0 to listOf(636), 1 to listOf(1), 2 to listOf("Some words."), 3 to listOf(11), 10 to listOf(1))),
        22 to DbRow(StepType.COORDINATE.table, mapOf(0 to listOf(432), 1 to listOf(3), 2 to listOf("01.30N 20.01E"), 3 to listOf(10), 5 to listOf(99), 6 to listOf(1))),
        // A beginner step, which no item names, and an easy one no item names (X Marks the Spot's), which is left out.
        23 to DbRow(StepType.MAP.table, mapOf(0 to listOf(101), 1 to listOf(0), 2 to listOf(10), 4 to listOf(1))),
        24 to DbRow(StepType.MAP.table, mapOf(0 to listOf(140), 1 to listOf(1), 2 to listOf(10), 4 to listOf(1))),
        // X Marks the Spot's own clue (difficulty 6).
        25 to DbRow(StepType.CIPHER.table, mapOf(0 to listOf(900), 1 to listOf(6), 2 to listOf("ESBZ"), 3 to listOf(11), 6 to listOf(1))),
    )

    private val data = TrailLoader.build(rows, mapOf(20 to listOf(2713), 21 to listOf(19814), 22 to listOf(12554, 12555)))

    @Test
    fun `steps take their tier, words, targets, items and fight from the clue helper rows`() {
        assertEquals(setOf("map:107", "cryptic:636", "coordinate:432", "map:101"), data.steps.keys)
        val map = data["map:107"]!!
        assertEquals(TrailTier.EASY, map.tier)
        assertNull(map.text)
        assertEquals(listOf(TrailTarget.Dig(TrailTile(3166, 3361, 0))), map.targets)
        assertEquals(map, data.byItem[2713])

        val cryptic = data["cryptic:636"]!!
        assertEquals("Some words.", cryptic.text)
        assertEquals(listOf(TrailTarget.Npc(3227, -1, TrailTile(3221, 3218, 1))), cryptic.targets)

        val coordinate = data["coordinate:432"]!!
        assertEquals(TrailTier.HARD, coordinate.tier)
        assertTrue(coordinate.combat)
        assertEquals(listOf(12554, 12555), coordinate.items)
        assertEquals(TrailTier.BEGINNER, data["map:101"]!!.tier)
    }

    @Test
    fun `a trail under way survives a save`() {
        val progress = TrailProgress()
        progress[TrailTier.EASY] = Trail("map:107", 3, mutableListOf("cryptic:636"))
        progress[TrailTier.BEGINNER] = Trail("map:101", 1)
        val back = TrailSaves.fromJson(TrailSaves.toJson(progress))
        assertEquals("map:107", back[TrailTier.EASY]!!.step)
        assertEquals(3, back[TrailTier.EASY]!!.length)
        assertEquals(listOf("cryptic:636"), back[TrailTier.EASY]!!.done)
        assertEquals(1, back[TrailTier.BEGINNER]!!.length)
        assertNull(back[TrailTier.HARD])
    }
}
