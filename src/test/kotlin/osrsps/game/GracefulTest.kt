package osrsps.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The graceful outfit's run energy bonus ([Graceful]), by the OSRS Wiki's Graceful outfit and Energy pages. */
class GracefulTest {
    private fun obj(id: Int, name: String) = ObjType(id, name, internalName = name)

    private val objs = ObjTypes.of(
        obj(HOOD, "graceful_hood"), obj(HOOD_WORN, "graceful_hood_worn"), obj(TOP, "graceful_top"), obj(LEGS, "graceful_legs"),
        obj(GLOVES, "graceful_gloves"), obj(BOOTS, "graceful_boots"), obj(CAPE, "graceful_cape"),
        obj(ARCEUUS_HOOD, "zeah_graceful_hood_arceuus"), obj(AGILITY_CAPE, "skillcape_agility"), obj(OTHER, "bronze_axe"),
    )
    private val graceful = Graceful(objs)

    private fun wearing(vararg worn: Pair<Int, Int>) = Player(1, "test", 3210, 3210, 0).apply {
        for ((slot, id) in worn) equipment[slot] = InvObj(id, 1)
    }

    @Test
    fun `pieces are known by their cache names, every colour but never the worn objs`() {
        assertEquals(Graceful.Piece.HOOD, Graceful.pieceOf("graceful_hood"))
        assertEquals(Graceful.Piece.HOOD, Graceful.pieceOf("graceful_hood_hallowed"))
        assertEquals(Graceful.Piece.TOP, Graceful.pieceOf("zeah_graceful_top_shayzien"))
        assertEquals(Graceful.Piece.CAPE, Graceful.pieceOf("skillcape_agility_trimmed"))
        assertNull(Graceful.pieceOf("graceful_hood_worn"))
        assertNull(Graceful.pieceOf("skillcape_agility_hood"))
        assertNull(Graceful.pieceOf("bronze_axe"))
    }

    @Test
    fun `each piece adds its own share, and the whole set 30 percent`() {
        assertEquals(0, graceful.restorePercent(wearing(WearPos.WEAPON to OTHER)))
        assertEquals(3, graceful.restorePercent(wearing(WearPos.HEAD to HOOD)))
        assertEquals(3, graceful.restorePercent(wearing(WearPos.HEAD to ARCEUUS_HOOD)))
        assertEquals(7, graceful.restorePercent(wearing(WearPos.HEAD to HOOD, WearPos.BODY to TOP)))
        val five = arrayOf(WearPos.HEAD to HOOD, WearPos.BODY to TOP, WearPos.LEGS to LEGS, WearPos.HANDS to GLOVES, WearPos.FEET to BOOTS)
        assertEquals(17, graceful.restorePercent(wearing(*five)))
        assertEquals(Graceful.FULL_SET, graceful.restorePercent(wearing(*five, WearPos.CAPE to CAPE)))
        // The Agility cape stands in for the graceful cape.
        assertEquals(Graceful.FULL_SET, graceful.restorePercent(wearing(*five, WearPos.CAPE to AGILITY_CAPE)))
    }

    @Test
    fun `the bonus multiplies the restore, rounded down`() {
        // ⌊1.3 × 24⌋ at 99 Agility, and ⌊1.3 × 15⌋ at 1: the Energy page's full-set rate.
        assertEquals(31, Graceful.boosted(24, Graceful.FULL_SET))
        assertEquals(19, Graceful.boosted(15, Graceful.FULL_SET))
        assertEquals(15, Graceful.boosted(15, 3))
        assertEquals(24, Graceful.boosted(24, 0))
    }

    private companion object {
        const val HOOD = 11_850
        const val HOOD_WORN = 11_851
        const val CAPE = 11_852
        const val TOP = 11_854
        const val LEGS = 11_856
        const val GLOVES = 11_858
        const val BOOTS = 11_860
        const val ARCEUUS_HOOD = 13_579
        const val AGILITY_CAPE = 9_771
        const val OTHER = 1_351
    }
}
