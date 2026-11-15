package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.GameRandom
import osrsps.game.ObjType
import osrsps.game.combat.Drop

/** Wilderness step W4.1: the group loot split's shares, floors and uniques (the Wiki's Callisto page). */
class WildernessBossesTest {
    private val coins = ObjType(995, "Coins", stackable = true)
    private val bones = ObjType(532, "Big bones")
    private val unique = ObjType(UNIQUE, "Unique")

    @Test
    fun `a solo kill gets all the solo loot`() {
        val shares = WildernessBosses.shares(mapOf("a" to 225))
        assertEquals(listOf("a"), shares.map { it.key })
        assertEquals(1.0, shares.single().factor)
        assertEquals(100, WildernessBosses.scale(100, shares.single().factor))
    }

    @Test
    fun `three players get their share of the damage, but never under 25%`() {
        val shares = WildernessBosses.shares(mapOf("a" to 600, "b" to 300, "c" to 100))
        assertEquals(listOf("a", "b", "c"), shares.map { it.key }, "by damage")
        assertEquals(listOf(60, 30, 25), shares.map { WildernessBosses.scale(100, it.factor) })
        assertEquals(1, WildernessBosses.scale(2, shares[2].factor), "never under 1")
    }

    @Test
    fun `of twelve players only the top ten get loot`() {
        val damage = (1..12).associate { "p$it" to it * 10 }
        val shares = WildernessBosses.shares(damage)
        assertEquals(10, shares.size)
        assertEquals((12 downTo 3).map { "p$it" }, shares.map { it.key })
        assertTrue(shares.all { it.factor >= 0.25 })
    }

    @Test
    fun `an Ironman under 7% gets 5%, and at 7% or more 25%`() {
        val low = WildernessBosses.shares(mapOf("a" to 940, "iron" to 60), ironmen = setOf("iron")).last()
        assertEquals(0.06, low.factor, 1e-9)
        assertEquals(6, WildernessBosses.scale(100, low.factor))
        val tiny = WildernessBosses.shares(mapOf("a" to 990, "iron" to 10), ironmen = setOf("iron")).last()
        assertEquals(5, WildernessBosses.scale(100, tiny.factor))
        val enough = WildernessBosses.shares(mapOf("a" to 930, "iron" to 70), ironmen = setOf("iron")).last()
        assertEquals(25, WildernessBosses.scale(100, enough.factor))
        val plain = WildernessBosses.shares(mapOf("a" to 990, "b" to 10)).last()
        assertEquals(25, WildernessBosses.scale(100, plain.factor), "the 5% is for Ironmen only")
    }

    @Test
    fun `each player rolls their own scaled loot, and a kill gives one unique at most, to one player`() {
        val shares = WildernessBosses.shares(mapOf("a" to 700, "b" to 300))
        // Every roll a unique beside 100 coins and the bones: the players' rolls lose theirs, the kill's gives one.
        val drops = WildernessBosses.split(shares, setOf(UNIQUE), GameRandom(1)) { listOf(Drop(bones, 1), Drop(coins, 100), Drop(unique, 1)) }
        assertEquals(listOf(Drop(bones, 1), Drop(coins, 70)), drops.getValue("a").filter { it.type.id != UNIQUE })
        assertEquals(listOf(Drop(bones, 1), Drop(coins, 30)), drops.getValue("b").filter { it.type.id != UNIQUE })
        assertEquals(1, drops.values.sumOf { list -> list.count { it.type.id == UNIQUE } })
    }

    @Test
    fun `the unique goes to players with odds of their damage`() {
        val shares = WildernessBosses.shares(mapOf("a" to 750, "b" to 250))
        val random = GameRandom(9)
        var toA = 0
        repeat(4000) {
            val drops = WildernessBosses.split(shares, setOf(UNIQUE), random) { listOf(Drop(unique, 1)) }
            if (drops.getValue("a").isNotEmpty()) toA++
        }
        assertTrue(toA in 2850..3150, "about three in four to the 75% player, got $toA of 4000")
    }

    private companion object {
        const val UNIQUE = 12603
    }
}
