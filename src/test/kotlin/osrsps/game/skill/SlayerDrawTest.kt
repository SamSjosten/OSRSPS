package osrsps.game.skill

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.GameRandom

/**
 * A master's draw against its weights (OSRS Wiki, Slayer task, Task weighting: a task's chance is its weight over the
 * sum of the weights the player may be given), with Turael's list as the 242 cache's slayer_master_task has it.
 */
class SlayerDrawTest {
    // Turael's 24 entries (task id to weight), from the 242 cache, read 2026-11-15.
    private val turael = listOf(
        3 to 7, 1 to 6, 6 to 8, 5 to 6, 4 to 6, 57 to 7, 68 to 8, 63 to 8, 7 to 7, 10 to 7, 8 to 7, 13 to 7,
        12 to 7, 37 to 8, 76 to 7, 75 to 8, 53 to 6, 62 to 8, 22 to 7, 2 to 7, 9 to 7, 11 to 7, 39 to 8, 38 to 8,
    ).map { (id, weight) -> SlayerMasterTask(1, SlayerTask(6000 + id, id, "task $id", "Task $id"), weight, 15, 30) }

    @Test
    fun `each task comes up as often as its weight says`() {
        val random = GameRandom(11)
        val draws = 200_000
        val counts = HashMap<Int, Int>()
        repeat(draws) { counts.merge(Slayer.draw(random, turael)!!.task.id, 1, Int::plus) }
        val total = turael.sumOf { it.weight }.toDouble()
        for (entry in turael) {
            val expected = entry.weight / total
            val seen = counts.getValue(entry.task.id) / draws.toDouble()
            assertTrue(abs(seen - expected) < 0.004, "task ${entry.task.id}: ${"%.4f".format(seen)}, want ${"%.4f".format(expected)}")
        }
    }

    @Test
    fun `a task taken out of the list moves its share to the rest`() {
        val random = GameRandom(3)
        val choices = turael.filter { it.task.id != 6 }
        repeat(10_000) { assertTrue(Slayer.draw(random, choices)!!.task.id != 6) }
    }

    @Test
    fun `nothing to draw from gives nothing`() {
        assertNull(Slayer.draw(GameRandom(1), emptyList()))
    }
}
