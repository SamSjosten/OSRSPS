package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.game.ObjType
import osrsps.game.WearPos

class EntranaSearchTest {
    private fun item(name: String, slot: Int = -1, bonus: Int = 0) =
        ObjType(1, name, wearpos1 = slot, params = if (bonus != 0) mapOf(STAB_DEFENCE to bonus) else emptyMap())

    @Test
    fun `weapons and armour with bonuses are barred, capes included`() {
        assertTrue(EntranaSearch.prohibited(item("Rune platebody", WearPos.BODY, 82)))
        assertTrue(EntranaSearch.prohibited(item("Bronze dagger", WearPos.WEAPON, 4)))
        assertTrue(EntranaSearch.prohibited(item("Fire cape", WearPos.CAPE, 11)))
    }

    @Test
    fun `the page's zero-bonus items are barred too`() {
        assertTrue(EntranaSearch.prohibited(item("Ham joint", WearPos.WEAPON)))
        assertTrue(EntranaSearch.prohibited(item("Swift blade", WearPos.WEAPON)))
        assertTrue(EntranaSearch.prohibited(item("Butterfly net", WearPos.WEAPON)))
        assertTrue(EntranaSearch.prohibited(item("Baby impling jar")))
        assertTrue(EntranaSearch.prohibited(item("Saradomin banner", WearPos.WEAPON)))
        assertTrue(EntranaSearch.prohibited(item("Cannon barrels")))
    }

    @Test
    fun `the page's exceptions, jewellery, runes and bonusless weapons pass`() {
        assertFalse(EntranaSearch.prohibited(item("Blue wizard robe", WearPos.BODY, 2)))
        assertFalse(EntranaSearch.prohibited(item("Leather gloves", WearPos.HANDS, 1)))
        assertFalse(EntranaSearch.prohibited(item("Saradomin cape", WearPos.CAPE, 1)))
        assertFalse(EntranaSearch.prohibited(item("Holy book", WearPos.SHIELD, 8)))
        assertFalse(EntranaSearch.prohibited(item("Yellow robe top", WearPos.BODY, 1)))
        assertFalse(EntranaSearch.prohibited(item("Amulet of strength", WearPos.NECK, 10)))
        assertFalse(EntranaSearch.prohibited(item("Fire rune")))
        assertFalse(EntranaSearch.prohibited(item("Rubber chicken", WearPos.WEAPON)))
        assertFalse(EntranaSearch.prohibited(item("Mystic robe top", WearPos.BODY, 0)))
        assertTrue(EntranaSearch.prohibited(item("Mystic robe top", WearPos.BODY, 20)))
    }

    private companion object {
        const val STAB_DEFENCE = 5
    }
}
