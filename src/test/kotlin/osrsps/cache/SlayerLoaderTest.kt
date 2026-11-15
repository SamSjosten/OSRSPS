package osrsps.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import osrsps.game.Skill

class SlayerLoaderTest {
    private fun task(vararg columns: Pair<Int, List<Any>>) = DbRow(SlayerLoader.TASK_TABLE, columns.toMap())

    // Shaped as osrs-dumps' rev 242 dump.dbrow has them: cows, rockslugs (a Slayer level, an extend, a block) and Kree'arra (a boss, id -1).
    private val cows = task(0 to listOf(6), 8 to listOf(1, 5, 21), 9 to listOf("cows"), 10 to listOf("Cows"))
    private val rockslugs = task(
        0 to listOf(51), 1 to listOf(20), 2 to listOf(20, Skill.SLAYER.ordinal), 9 to listOf("rockslugs"), 10 to listOf("Rockslugs"),
        15 to listOf(6413, 100, 150), 18 to listOf(6393),
    )
    private val kreearra = task(0 to listOf(-1), 2 to listOf(70, Skill.RANGED.ordinal), 9 to listOf("Kree'arra"), 10 to listOf("Kree'arra"))

    private fun master(master: Int, task: Int, weight: Int, min: Int, max: Int, vararg more: Pair<Int, List<Any>>) =
        DbRow(SlayerLoader.MASTER_TASK_TABLE, mapOf(0 to listOf(master), 1 to listOf(task), 2 to listOf(weight), 3 to listOf(min), 4 to listOf(max)) + more)

    private val data = SlayerLoader.build(
        mapOf(6219 to cows, 6264 to rockslugs, 545 to kreearra),
        mapOf(
            4200 to master(3, 6264, 7, 40, 90),
            4100 to master(1, 6219, 8, 15, 30),
            4300 to master(8, 6219, 8, 120, 170, 5 to listOf(12, 13), 6 to listOf(6411)),
        ),
        mapOf(6393 to DbRow(SlayerLoader.UNLOCK_TABLE, mapOf(0 to listOf(1), 1 to listOf(10)))),
    )

    @Test
    fun `tasks by id, with the table's defaults, and no boss rows`() {
        assertEquals(setOf(6, 51), data.tasks.keys)
        val cows = data.tasks.getValue(6)
        assertEquals("cows", cows.nameLower)
        assertEquals("Cows", cows.nameUpper)
        assertEquals(0, cows.minCombat)
        assertEquals(emptyList(), cows.statsAll)
        assertNull(cows.blockedBy)
    }

    @Test
    fun `stat requirements read level then stat, extends by unlock, and the unlock that blocks it`() {
        val slugs = data.tasks.getValue(51)
        assertEquals(20, slugs.minCombat)
        assertEquals(listOf(Skill.SLAYER to 20), slugs.statsAll)
        assertEquals(6393, slugs.blockedBy)
        assertEquals(mapOf(6413 to 100..150), slugs.extensions)
    }

    @Test
    fun `masters' lists in row order, with Konar's areas and a bought task`() {
        assertEquals(listOf(6), data.masterTasks.getValue(1).map { it.task.id })
        assertEquals(15..30, data.entry(1, 6)!!.let { it.min..it.max })
        assertEquals(7, data.entry(3, 51)!!.weight)
        val konar = data.entry(8, 6)!!
        assertEquals(listOf(12, 13), konar.areas)
        assertEquals(6411, konar.unlockedBy)
        assertNull(data.entry(1, 51))
    }

    @Test
    fun `rewards by row`() {
        val unlock = data.unlocks.getValue(6393)
        assertEquals(1, unlock.bit)
        assertEquals(10, unlock.cost)
    }
}
