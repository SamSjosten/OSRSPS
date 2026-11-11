package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.game.zone.ZoneLoc
import osrsps.map.LocShape

/** Thieving 2 step T2's chests and doors against the OSRS Wiki's pages (read 2026-11-10) and 2004Scape's charts. */
class ThievingLocksTest {
    private fun chest(loc: Int) = ThievingLocks.CHESTS.getValue(loc)

    private fun door(loc: Int) = ThievingLocks.DOORS.getValue(loc)

    @Test
    fun `each chest has the Wiki's level, XP, loot and relock time`() {
        fun row(c: TrapChest) = listOf(c.level, c.xpTenths, c.respawnTicks) + c.loot.flatMap { listOf(it.obj, it.count) }
        with(ThievingLocks) {
            assertEquals(listOf(13, 78, 12, COINS, 10), row(chest(CHEST_10_COINS)))
            assertEquals(listOf(28, 250, 25, NATURE_RUNE, 1, COINS, 3), row(chest(CHEST_NATURE)))
            assertEquals(listOf(43, 1250, 83, COINS, 50), row(chest(CHEST_50_COINS)))
            assertEquals(listOf(47, 1500, 150, STEEL_ARROWTIPS, 5, COINS, 20), row(chest(CHEST_ARROWTIPS)))
            assertEquals(listOf(59, 2500, 225, BLOOD_RUNE, 2, COINS, 500), row(chest(CHEST_BLOOD)))
            assertEquals(
                listOf(72, 5000, 833, COINS, 1000, RAW_SHARK, 1, ADAMANTITE_ORE, 1, UNCUT_SAPPHIRE, 1),
                row(chest(CHEST_ARDOUGNE_CASTLE)),
            )
            assertTrue(chest(CHEST_ARROWTIPS).lockpick)
            assertEquals(EMPTY_PICK_CHEST, chest(CHEST_ARROWTIPS).empty)
            assertEquals(setOf(CHEST_BLOOD, CHEST_ARDOUGNE_CASTLE), CHESTS.values.filter { it.teleport != null }.map { it.loc }.toSet())
            assertEquals(Triple(2584, 3337, 0), chest(CHEST_BLOOD).teleport)
        }
    }

    @Test
    fun `the chest traps hit as the Wiki's formulas have it`() {
        // Nature rune chest: floor(HP * 12 / 100) + 3. Blood rune chest: (9 * HP + 225) / 50, 22 at 99.
        assertEquals(14, chest(ThievingLocks.CHEST_NATURE).trapDamage(99, 99))
        assertEquals(4, chest(ThievingLocks.CHEST_10_COINS).trapDamage(10, 99))
        assertEquals(22, chest(ThievingLocks.CHEST_BLOOD).trapDamage(99, 99))
        assertEquals(4, chest(ThievingLocks.CHEST_BLOOD).trapDamage(1, 99))
    }

    @Test
    fun `each door has its level, XP and charts`() {
        fun row(d: LockedDoor) = listOf(d.level, d.xpTenths, d.low, d.high, d.trapLow, d.trapHigh)
        with(ThievingLocks) {
            assertEquals(listOf(1, 38, 32, 250, 0, 0), row(door(DOOR_EAST_ARDOUGNE_NORTH)))
            assertEquals(listOf(16, 150, 32, 250, 0, 0), row(door(DOOR_EAST_ARDOUGNE_SOUTH)))
            assertEquals(listOf(13, 150, 4, 40, 32, 250), row(door(DOOR_ROSS)))
            assertEquals(listOf(46, 375, 8, 75, 220, 280), row(door(DOOR_CHAOS_DRUID_TOWER)))
            assertEquals(listOf(61, 500, 4, 40, 200, 270), row(door(DOOR_ARDOUGNE_CASTLE)))
        }
    }

    @Test
    fun `Ross' door's trap goes off 77% of the time at 13 and 2% at 99, as the Wiki has it`() {
        val ross = door(ThievingLocks.DOOR_ROSS)
        assertEquals(77, Math.round(100.0 * (256 - SuccessRoll.chance(ross.trapLow, ross.trapHigh, 13)) / 256).toInt())
        assertEquals(2, Math.round(100.0 * (256 - SuccessRoll.chance(ross.trapLow, ross.trapHigh, 99)) / 256).toInt())
    }

    @Test
    fun `the door traps take 10% of current, 6% of full or 10% of full Hitpoints`() {
        assertEquals(0, door(ThievingLocks.DOOR_ROSS).trapDamage(10, 99))
        assertEquals(5, door(ThievingLocks.DOOR_ROSS).trapDamage(59, 99))
        assertEquals(5, door(ThievingLocks.DOOR_CHAOS_DRUID_TOWER).trapDamage(10, 99))
        assertEquals(9, door(ThievingLocks.DOOR_ARDOUGNE_CASTLE).trapDamage(10, 99))
    }

    @Test
    fun `a door is locked from its own tile's side and leads through to the other`() {
        // West edge (angle 0): outside is x 3226, in line with the door's tile; inside is x 3225.
        val west = ZoneLoc(ThievingLocks.DOOR_EAST_ARDOUGNE_NORTH, LocShape.WALL_STRAIGHT, 0, 3226, 3218, 0)
        assertTrue(ThievingLocks.outside(west, 3226, 3218))
        assertFalse(ThievingLocks.outside(west, 3225, 3218))
        assertEquals(3225 to 3218, ThievingLocks.through(west, 3226, 3218))
        assertEquals(3226 to 3218, ThievingLocks.through(west, 3225, 3218))
        // North edge (angle 1): outside is z 3218; inside is z 3219.
        val north = west.copy(angle = 1)
        assertTrue(ThievingLocks.outside(north, 3226, 3218))
        assertEquals(3226 to 3219, ThievingLocks.through(north, 3226, 3218))
        assertEquals(3226 to 3218, ThievingLocks.through(north, 3226, 3219))
    }
}
