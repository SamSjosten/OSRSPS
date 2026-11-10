package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Gathering 2, step G5: the cannonballs against the Wiki's cannonball pages. */
class CannonballsTest {
    @Test
    fun `each bar's cannonball has the Wiki's level and XP`() {
        // Bronze 5 / 8.8, iron 20 / 17.2, steel 35 / 25.6, mithril 55 / 34, adamant 75 / 42.4, rune 90 / 50.8.
        assertEquals(
            listOf(
                listOf(2349, 31906, 5, 88), listOf(2351, 31908, 20, 172), listOf(2353, 2, 35, 256),
                listOf(2359, 31910, 55, 340), listOf(2361, 31912, 75, 424), listOf(2363, 31914, 90, 508),
            ),
            Cannonballs.BALLS.map { listOf(it.bar, it.obj, it.level, it.xpTenths) },
        )
    }

    @Test
    fun `the plain mould casts one bar into four, the double two into eight for twice the XP, both 8 then 10 ticks`() {
        for (ball in Cannonballs.BALLS) {
            val single = Cannonballs.SINGLE.getValue(ball.bar)
            assertEquals(listOf(4, 1, ball.xpTenths), listOf(single.amount, single.inputs.single().count, single.xpTenths))
            assertEquals(listOf(Cannonballs.AMMO_MOULD), single.tools)
            val double = Cannonballs.DOUBLE.getValue(ball.bar)
            assertTrue(double.batch)
            // Two units of one bar each, four cannonballs and the bar's XP a unit: 8 and twice the XP for two bars.
            assertEquals(listOf(2, 4, 1, ball.xpTenths), listOf(double.amount, double.batchEach, double.inputs.single().count, double.xpTenths))
            assertEquals(listOf(Cannonballs.DOUBLE_AMMO_MOULD), double.tools)
            for (recipe in listOf(single, double)) assertEquals(listOf(8, 10, 10), listOf(recipe.first, recipe.second, recipe.interval))
        }
    }
}
