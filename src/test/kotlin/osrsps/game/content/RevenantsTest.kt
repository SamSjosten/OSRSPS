package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.combat.AttackType

/** Wilderness step W5.2: how a revenant picks its style ([Revenants.style]). */
class RevenantsTest {
    private val none = mapOf(AttackType.MAGIC to 0, AttackType.RANGED to 0, AttackType.SLASH to 0)

    @Test
    fun `magic by default, and on a tie`() {
        assertEquals(AttackType.MAGIC, Revenants.style(AttackType.SLASH, beside = true, prayed = { false }) { none.getValue(it) })
    }

    @Test
    fun `the style the player's defence is lowest against, melee only beside them`() {
        val robes = mapOf(AttackType.MAGIC to 60, AttackType.RANGED to 10, AttackType.SLASH to 5)
        assertEquals(AttackType.SLASH, Revenants.style(AttackType.SLASH, beside = true, prayed = { false }) { robes.getValue(it) })
        assertEquals(AttackType.RANGED, Revenants.style(AttackType.SLASH, beside = false, prayed = { false }) { robes.getValue(it) })
    }

    @Test
    fun `never a style the player prays against`() {
        assertEquals(AttackType.RANGED, Revenants.style(AttackType.STAB, beside = false, prayed = { it == AttackType.MAGIC }) { none.getValue(it) })
        val plate = mapOf(AttackType.MAGIC to -30, AttackType.RANGED to 100, AttackType.STAB to 100)
        assertEquals(AttackType.RANGED, Revenants.style(AttackType.STAB, beside = true, prayed = { it == AttackType.MAGIC }) { plate.getValue(it) })
    }

    @Test
    fun `eleven kinds, with the Wiki's ids`() {
        assertEquals(listOf(7881) + (7931..7940).toList(), Revenants.KINDS.map { it.id })
    }
}
