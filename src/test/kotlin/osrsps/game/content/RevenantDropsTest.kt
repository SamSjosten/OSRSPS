package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.GameRandom
import osrsps.game.ObjType

/** Wilderness step W5.3: the revenants' shared drops ([RevenantDrops]), against the Wiki's Template:Revenants/Drops. */
class RevenantDropsTest {
    private val types = { id: Int -> ObjType(id, "item $id", stackable = true) }

    @Test
    fun `A and B as the template works them out`() {
        // Revenant dragon, combat 135: A 200, B 205, so no coins; revenant imp, combat 7: A 1100, B 37.
        assertEquals(200, RevenantDrops.a(135))
        assertEquals(205, RevenantDrops.b(135))
        assertEquals(1100, RevenantDrops.a(7))
        assertEquals(37, RevenantDrops.b(7))
        assertEquals(RevenantDrops.MAIN_OF, RevenantDrops.MAIN.sumOf { it.weight })
        assertEquals(100, RevenantDrops.SECONDARY.sumOf { it.weight })
        assertTrue(RevenantDrops.STATUETTES.sumOf { it.weight } <= RevenantDrops.STATUETTES_OF)
        assertTrue(RevenantDrops.STATUETTES_SKULLED.sumOf { it.weight } <= RevenantDrops.STATUETTES_SKULLED_OF)
    }

    @Test
    fun `ether every kill, in even counts up to the level's most`() {
        val random = GameRandom(1)
        repeat(2_000) {
            val ether = RevenantDrops.roll(random, 7940, 135, false, types = types).filter { it.type.id == RevenantDrops.ETHER }
            assertEquals(1, ether.size)
            assertTrue(ether[0].count % 2 == 0 && ether[0].count in 2..24, "${ether[0].count}")
        }
    }

    @Test
    fun `an imp mostly drops coins, a dragon never, and a dragon's uniques come about as often as the Wiki says`() {
        val random = GameRandom(2)
        val kills = 200_000
        val imp = (1..kills).count { RevenantDrops.roll(random, 7881, 7, false, types = types).any { it.type.id == RevenantDrops.COINS } }
        // (A - B) / A = 1063 / 1100.
        assertTrue(imp in (kills * 0.955).toInt()..(kills * 0.977).toInt(), "$imp")
        var coins = 0
        var uniques = 0
        val ids = setOf(RevenantDrops.AVARICE, RevenantDrops.CRAWS_BOW, RevenantDrops.THAMMARONS_SCEPTRE, RevenantDrops.VIGGORAS_CHAINMACE)
        repeat(kills) {
            val drops = RevenantDrops.roll(random, 7940, 135, true, types = types)
            if (drops.any { it.type.id == RevenantDrops.COINS }) coins++
            if (drops.any { it.type.id in ids }) uniques++
        }
        assertEquals(0, coins)
        // Skulled: 1 in 200 * 14.667 = 2,933, so about 68 in 200,000.
        assertTrue(uniques in 40..100, "$uniques")
    }

    @Test
    fun `skulled, no dragon med helm`() {
        val random = GameRandom(3)
        repeat(100_000) { assertTrue(RevenantDrops.roll(random, 7940, 135, true, types = types).none { it.type.id == 1149 }) }
    }

    @Test
    fun `a looting bag now and then, never for a killer who owns one`() {
        val random = GameRandom(4)
        val bags = (1..2_000).count { RevenantDrops.roll(random, 7881, 7, false, types = types).any { it.type.id == RevenantDrops.LOOTING_BAG } }
        // An imp, combat 7: 1 in 15, so about 133 in 2,000.
        assertTrue(bags in 90..180, "$bags")
        repeat(2_000) {
            assertTrue(RevenantDrops.roll(random, 7881, 7, false, ownsBag = true, types = types).none { it.type.id == RevenantDrops.LOOTING_BAG })
        }
    }

    @Test
    fun `the noted drops come as notes`() {
        // Battlestaves, rune armour and weapons, dragon armour and weapons, bars, ore, hide, planks and the uncut
        // dragonstone by their noted ids; coal, yew and magic logs too.
        val noted = setOf(1392, 1164, 1128, 1080, 1202, 1348, 4088, 4586, 1216, 1306, 454, 2362, 452, 2364, 1748, 8784, 1516, 1514, 1632)
        assertTrue(noted.all { id -> RevenantDrops.MAIN.any { it.item == id } })
        val unnoted = setOf(1391, 1163, 1127, 1079, 1201, 1347, 4087, 4585, 1215, 1305, 453, 2361, 451, 2363, 1747, 8783, 1515, 1513, 1631)
        assertTrue(RevenantDrops.MAIN.none { it.item in unnoted })
    }
}
