package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.Inventory
import osrsps.game.Scenarios
import osrsps.game.Skill
import osrsps.game.raid.ArchiveRaids

/** The raid tester's real scenario files: in the server's list, each landing in front of the Archive Door kitted out. */
class RaidTesterTest {
    private val scenarios = Scenarios.load()

    @Test
    fun `every loadout he offers is a scenario the server loads`() {
        assertTrue(Scenarios.FILES.containsAll(RaidTester.LOADOUTS), "${RaidTester.LOADOUTS} not all in Scenarios.FILES")
    }

    @Test
    fun `each loadout starts in the lobby in front of the Archive Door with 99 combat stats and a full inventory`() {
        val combat = listOf(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.HITPOINTS, Skill.RANGED, Skill.MAGIC, Skill.PRAYER)
        for (name in RaidTester.LOADOUTS) {
            val scenario = scenarios[name]!!
            assertEquals(Triple(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z, 0), Triple(scenario.x, scenario.z, scenario.level), name)
            for (skill in combat) assertEquals(99, scenario.skills[skill]?.level, "$name: ${skill.displayName}")
            assertEquals(Inventory.INV_CAPACITY, scenario.inventory.size, name)
            assertTrue(scenario.equipment.size >= 10, "$name wears ${scenario.equipment.size} items")
        }
    }
}
