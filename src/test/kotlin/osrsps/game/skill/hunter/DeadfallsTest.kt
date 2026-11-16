package osrsps.game.skill.hunter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.zone.ZoneLoc
import osrsps.map.LocShape
import osrsps.map.Tile

/** Hunter step 4's data against the Wiki: levels, XP, drops, the two-deadfall limit, and the sides a kebbit comes in by. */
class DeadfallsTest {
    @Test
    fun `levels and XP are the Deadfall page's, after the Summer Sweep Up`() {
        assertEquals(listOf(23 to 1024, 33 to 1344, 37 to 1472, 51 to 1600), Deadfalls.CREATURES.map { it.level to it.xpTenths })
        assertEquals(2, Deadfalls.KIND.maxLaid)
        assertEquals(8L, Deadfalls.KIND.firstLook)
        assertTrue(Deadfalls.KIND.endsOnMiss && !Deadfalls.KIND.returnsItem && !Deadfalls.KIND.playerBlocks)
    }

    @Test
    fun `every kebbit leaves bones and its own part`() {
        assertTrue(Deadfalls.CREATURES.all { c -> c.loot.first().obj == Deadfalls.BONES })
        assertEquals(
            listOf(Deadfalls.KEBBIT_CLAWS, Deadfalls.BARB_TAIL_HARPOON, Deadfalls.KEBBIT_SPIKE, Deadfalls.KEBBIT_TEETH),
            Deadfalls.CREATURES.map { it.loot[1].obj },
        )
    }

    @Test
    fun `a kebbit is caught along either long side, mirrored from the north or east`() {
        val boulder = ZoneLoc(Deadfalls.BOULDER, LocShape.CENTREPIECE_STRAIGHT, 0, 100, 200, 0)
        val flat = Trap("x", Deadfalls.KIND, 100, 200, 0, boulder, 0)
        assertEquals(listOf(Tile(100, 199), Tile(101, 199), Tile(100, 201), Tile(101, 201)), Deadfalls.KIND.catchTiles(flat))
        val turned = Trap("x", Deadfalls.KIND, 100, 200, 0, boulder.copy(angle = 1), 1)
        assertEquals(listOf(Tile(99, 200), Tile(99, 201), Tile(101, 200), Tile(101, 201)), Deadfalls.KIND.catchTiles(turned))
        val prickly = Deadfalls.CREATURES.first { it.npc == Deadfalls.PRICKLY_KEBBIT }
        assertEquals(19218, Deadfalls.KIND.catchingLoc(flat, prickly, 100, 199))
        assertEquals(19219, Deadfalls.KIND.catchingLoc(flat, prickly, 101, 201))
        assertTrue((Deadfalls.KIND.mirrored.values + Deadfalls.CREATURES.flatMap { listOf(it.catching, it.full) }).all(Deadfalls.KIND::owns))
        assertTrue(Deadfalls.LOGS.none { it == 19669 || it == 10810 })
    }
}
