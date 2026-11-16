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
    fun `each loadout starts in the lobby in front of the Archive Door with 99 combat stats, one slot free and the standard spellbook`() {
        val combat = listOf(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.HITPOINTS, Skill.RANGED, Skill.MAGIC, Skill.PRAYER)
        for (name in RaidTester.LOADOUTS) {
            val scenario = scenarios[name]!!
            assertEquals(Triple(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z, 0), Triple(scenario.x, scenario.z, scenario.level), name)
            for (skill in combat) assertEquals(99, scenario.skills[skill]?.level, "$name: ${skill.displayName}")
            // One slot free, so a two-handed weapon can go on over a weapon and a shield (Codex review on #774).
            assertEquals(Inventory.INV_CAPACITY - 1, scenario.inventory.size, name)
            // The standard spellbook, for the Surges its runes are for, whatever book the player had open.
            assertEquals(mapOf(SPELLBOOK_VARBIT to 0), scenario.varbits, name)
            assertTrue(scenario.equipment.size >= 10, "$name wears ${scenario.equipment.size} items")
        }
    }

    private companion object {
        /** spellbook in the 241 cache: varp 439 bits 0 and 1 ([osrsps.game.combat.Spellbook]). */
        const val SPELLBOOK_VARBIT = 4070
    }
}
