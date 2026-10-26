package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.GameRandom

class CombatMathTest {
    @Test
    fun `a new account punching on accurate`() {
        // Attack 1 + 3 accurate + 8, no bonus: 12 * 64.
        assertEquals(768, CombatMath.playerAttackRoll(1, AttackStance.ACCURATE, 0))
        // Strength 1 + 8, no bonus: (9 * 64 + 320) / 640 = 1.4, so 1.
        assertEquals(1, CombatMath.playerMaxHit(1, AttackStance.ACCURATE, 0))
        assertEquals(576, CombatMath.playerDefenceRoll(1, AttackStance.ACCURATE, 0))
    }

    @Test
    fun `stances add to the levels they train`() {
        assertEquals((1 + 8) * 64, CombatMath.playerAttackRoll(1, AttackStance.AGGRESSIVE, 0))
        assertEquals((1 + 1 + 8) * 64, CombatMath.playerAttackRoll(1, AttackStance.CONTROLLED, 0))
        assertEquals((1 + 3 + 8) * 64, CombatMath.playerDefenceRoll(1, AttackStance.DEFENSIVE, 0))
        assertEquals((1 + 3 + 8) * 64, CombatMath.playerDefenceRoll(1, AttackStance.LONGRANGE, 0))
        // 99 Strength on aggressive with +82 (a whip and nothing else): (110 * 146 + 320) / 640 = 25.6.
        assertEquals(25, CombatMath.playerMaxHit(99, AttackStance.AGGRESSIVE, 82))
    }

    @Test
    fun `NPC max hits from their levels match the Wiki's`() {
        // OSRS Wiki infoboxes: every one of these has Strength 1.
        assertEquals(1, CombatMath.npcMaxHit(1, 0)) // Man, Woman
        assertEquals(0, CombatMath.npcMaxHit(1, -53)) // Rat
        assertEquals(0, CombatMath.npcMaxHit(1, -37)) // Imp
        assertEquals(1, CombatMath.npcMaxHit(1, -15)) // Cow
        assertEquals(1, CombatMath.npcMaxHit(1, -20)) // Cow calf
    }

    @Test
    fun `NPC rolls add 9 to the level`() {
        assertEquals(10 * 43, CombatMath.npcDefenceRoll(1, -21))
        assertEquals(10 * 64, CombatMath.npcAttackRoll(1, 0))
    }

    @Test
    fun `hit chance follows the Wiki's two cases`() {
        // Attack higher: 1 - (430 + 2) / (2 * 769).
        assertEquals(1 - 432.0 / 1538, CombatMath.hitChance(768, 430), 1e-9)
        // Defence higher or equal: 430 / (2 * 769).
        assertEquals(430.0 / 1538, CombatMath.hitChance(430, 768), 1e-9)
        assertEquals(0.0, CombatMath.hitChance(-5, 100))
    }

    @Test
    fun `rolled hits come out at the formula's chance`() {
        val random = GameRandom(7)
        val tries = 200_000
        val hits = (1..tries).count { CombatMath.rollHits(random, 768, 430) }
        val expected = CombatMath.hitChance(768, 430)
        assertTrue(kotlin.math.abs(hits.toDouble() / tries - expected) < 0.005, "hit ${hits.toDouble() / tries}, expected $expected")
    }

    @Test
    fun `damage is 0 to the max hit, each as likely`() {
        val random = GameRandom(3)
        val counts = IntArray(4)
        repeat(40_000) { counts[CombatMath.rollDamage(random, 3)]++ }
        for (count in counts) assertTrue(count in 9_500..10_500, counts.joinToString())
    }

    @Test
    fun `xp in tenths`() {
        assertEquals(40, CombatMath.styleXpTenths(1, 1))
        assertEquals(13, CombatMath.styleXpTenths(1, 3))
        assertEquals(40, CombatMath.styleXpTenths(3, 3))
        assertEquals(13, CombatMath.hitpointsXpTenths(1))
        assertEquals(133, CombatMath.hitpointsXpTenths(10))
    }
}
