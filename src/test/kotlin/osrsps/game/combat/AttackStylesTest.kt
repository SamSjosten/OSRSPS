package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import osrsps.game.Skill

class AttackStylesTest {
    @Test
    fun `reads a style from the combat tab's tooltip`() {
        assertEquals(
            AttackStyle(0, "Punch", AttackStance.ACCURATE, AttackType.CRUSH, listOf(Skill.ATTACK)),
            AttackStyles.parse(0, "Punch", "(Accurate)<br>(Crush)<br>(Attack XP)"),
        )
        assertEquals(
            AttackStyle(2, "Lunge", AttackStance.CONTROLLED, AttackType.STAB, listOf(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE)),
            AttackStyles.parse(2, "Lunge", "(Controlled)<br>(Stab)<br>(Shared XP)"),
        )
        assertEquals(AttackType.RANGED, AttackStyles.parse(1, "Rapid", "(Rapid)<br>(Standard)<br>(Ranged XP)").type)
        // The 241 cache's crossbow longrange: heavy, and XP to Ranged and Defence.
        assertEquals(
            AttackStyle(3, "Longrange", AttackStance.LONGRANGE, AttackType.RANGED, listOf(Skill.RANGED, Skill.DEFENCE), RangedWeight.HEAVY),
            AttackStyles.parse(3, "Longrange", "(Longrange)<br>(Heavy)<br>(Ranged XP)<br>(Defence XP)"),
        )
        assertEquals(RangedWeight.LIGHT, AttackStyles.parse(0, "Accurate", "(Accurate)<br>(Light)<br>(Ranged XP)").weight)
        assertNull(AttackStyles.parse(0, "Punch", "(Accurate)<br>(Crush)<br>(Attack XP)").weight)
        assertEquals(
            AttackStyle(3, "Block", AttackStance.OTHER, AttackType.NONE, emptyList()),
            AttackStyles.parse(3, "Block", "(Block)<br>(No XP)"),
        )
    }

    @Test
    fun `builds the table from dbtable 78's rows`() {
        val problems = ArrayList<String>()
        val styles = AttackStyles.fromRows(
            listOf(
                listOf<Any>(0) to listOf<Any>(
                    0, "Punch", "(Accurate)<br>(Crush)<br>(Attack XP)", 100,
                    1, "Kick", "(Aggressive)<br>(Crush)<br>(Strength XP)", 101,
                    3, "Block", "(Defensive)<br>(Crush)<br>(Defence XP)", 102,
                ),
                listOf<Any>(9) to listOf<Any>(0, "Chop", "(Accurate)<br>(Slash)<br>(Attack XP)"),
            ),
            problems,
        )
        assertEquals(AttackStyles.DEFAULT.styles(0), styles.styles(0))
        assertEquals(1, styles.categories)
        assertEquals("Kick", styles.style(0, 1)?.name)
        assertNull(styles.style(0, 2))
        assertEquals(1, problems.size)
    }
}
