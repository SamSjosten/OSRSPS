package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.combat.AttackType
import osrsps.game.content.ChampionsChallenge.Champion
import osrsps.game.content.ChampionsChallenge.Rule

class ChampionsChallengeTest {
    @Test
    fun `Larxus's lines load first without looping through the champions and their rules`() {
        assertEquals(10, LarxusLines.RULES.size)
        assertEquals(Champion.entries.toSet(), ChampionsChestLines.REWARD.keys)
        assertEquals(LarxusLines.ONLY_MELEE, Champion.GIANT.rule.refusal)
    }

    @Test
    fun `each duel's rule allows and bars the attack types the Wiki's table says`() {
        val melee = listOf(AttackType.STAB, AttackType.SLASH, AttackType.CRUSH)
        assertTrue(Rule.ONLY_MAGIC.allows(AttackType.MAGIC))
        assertFalse((melee + AttackType.RANGED).any { Rule.ONLY_MAGIC.allows(it) })
        assertTrue(Rule.ONLY_RANGED.allows(AttackType.RANGED))
        assertFalse((melee + AttackType.MAGIC).any { Rule.ONLY_RANGED.allows(it) })
        assertTrue(melee.all { Rule.ONLY_MELEE.allows(it) })
        assertFalse(Rule.ONLY_MELEE.allows(AttackType.MAGIC) || Rule.ONLY_MELEE.allows(AttackType.RANGED))
        assertFalse(melee.any { Rule.NO_MELEE.allows(it) })
        assertTrue(Rule.NO_MELEE.allows(AttackType.MAGIC) && Rule.NO_MELEE.allows(AttackType.RANGED))
        assertFalse(Rule.NO_MAGIC.allows(AttackType.MAGIC))
        assertFalse(Rule.NO_RANGED.allows(AttackType.RANGED))
        for (rule in listOf(Rule.NO_SPECIAL, Rule.WEAPON_ONLY, Rule.NO_PRAYER, Rule.NOTHING_WORN, Rule.WORN_ONLY)) {
            assertTrue(AttackType.entries.all { rule.allows(it) }, rule.name)
            assertNull(rule.refusal, rule.name)
        }
    }

    @Test
    fun `the champions hold their own bits of varp 617 and their own scrolls`() {
        assertEquals((0..10).toList(), Champion.entries.map { it.bit }.sorted())
        assertEquals((6798..6808).toList(), Champion.entries.map { it.scroll }.sorted())
        assertEquals(Champion.ZOMBIE, Champion.ofScroll(6807))
        assertNull(Champion.ofScroll(ChampionsChallenge.LEON_SCROLL), "Leon's scroll arranges nothing")
        assertEquals(Champion.LEON, Champion.ofNpc(3360))
        assertTrue(ChampionsChallenge.inBasement(3187, 9758, 0))
        assertFalse(ChampionsChallenge.inBasement(3187, 9758, 1))
    }
}
