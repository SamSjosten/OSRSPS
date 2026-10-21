package osrsps.game

import kotlin.test.Test
import kotlin.test.assertEquals

class WeaponsTest {
    @Test
    fun `weapons get their combat tab category from the item's category`() {
        assertEquals(Weapons.UNARMED, Weapons.category(null))
        assertEquals(Weapons.AXE, Weapons.category(ObjType(1351, "Bronze axe", category = 35)))
        assertEquals(Weapons.HACKSWORD, Weapons.category(ObjType(1321, "Bronze scimitar", category = 21)))
        assertEquals(Weapons.BLUNT_BLUDGEON, Weapons.category(ObjType(13263, "Abyssal bludgeon", internalName = "abyssal_bludgeon", category = 26)))
        assertEquals(Weapons.UNARMED, Weapons.category(ObjType(1, "Rubber chicken", category = 55)))
    }

    @Test
    fun `three-style weapons have no button 2`() {
        assertEquals(listOf(0, 1, 3), Weapons.styleButtons(Weapons.UNARMED))
        assertEquals(listOf(0, 1, 2, 3), Weapons.styleButtons(Weapons.AXE))
        assertEquals(listOf(0, 3), Weapons.styleButtons(Weapons.BULWARK))
    }

    @Test
    fun `stances follow the weapon`() {
        assertEquals(Weapons.HUMAN, Weapons.stance(null))
        assertEquals(Weapons.HUMAN, Weapons.stance(ObjType(1351, "Bronze axe", category = 35)))
        assertEquals(Weapons.TWO_HANDED_STANCE, Weapons.stance(ObjType(1307, "Bronze 2h sword", category = 61)))
        assertEquals(Weapons.GODSWORD_STANCE, Weapons.stance(ObjType(11802, "Armadyl godsword", category = 61)))
        assertEquals(Weapons.STAFF_STANCE, Weapons.stance(ObjType(1381, "Staff of air", category = 1)))
        assertEquals(Weapons.HUMAN, Weapons.stance(ObjType(6908, "Beginner wand", category = 1)))
        assertEquals(Weapons.WHIP_STANCE, Weapons.stance(ObjType(4151, "Abyssal whip", category = 150)))
    }
}
