package osrsps.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class EquipmentTest {
    // Made-up items shaped like the 241 cache's: wearpos, the two-handed shield slot and bonus params.
    private val axe = ObjType(1351, "Bronze axe", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, params = mapOf(0 to -2, 1 to 4, 10 to 5, 14 to 5), internalName = "bronze_axe", category = 35)
    private val sword = ObjType(1277, "Bronze sword", wearpos1 = WearPos.WEAPON, params = mapOf(0 to 4, 14 to 4), internalName = "bronze_sword", category = 25)
    private val twoHander = ObjType(1307, "Bronze 2h sword", wearpos1 = WearPos.WEAPON, wearpos2 = WearPos.SHIELD, params = mapOf(14 to 7), internalName = "bronze_2h_sword", category = 61)
    private val shield = ObjType(1171, "Wooden shield", wearpos1 = WearPos.SHIELD, params = mapOf(5 to 4, 6 to 5), internalName = "wooden_shield")
    private val whip = ObjType(4151, "Abyssal whip", wearpos1 = WearPos.WEAPON, params = mapOf(434 to 0, 436 to 70, 1 to 82), internalName = "abyssal_whip", category = 150)
    private val mithrilHelm = ObjType(1159, "Mithril full helm", wearpos1 = WearPos.HEAD, wearpos2 = WearPos.HAIR, wearpos3 = WearPos.JAW, internalName = "mithril_full_helm")
    private val arrows = ObjType(882, "Bronze arrow", stackable = true, wearpos1 = WearPos.AMMO, params = mapOf(12 to 7), internalName = "bronze_arrow")
    private val occult = ObjType(12002, "Occult necklace", wearpos1 = WearPos.NECK, params = mapOf(3 to 12, 299 to 50))
    private val coins = ObjType(995, "Coins", stackable = true)
    // Gear's ranged strength is param 189, not the ammunition's 12.
    private val pegasians = ObjType(13237, "Pegasian boots", wearpos1 = WearPos.FEET, params = mapOf(4 to 12, 189 to 1), internalName = "pegasian_boots")
    private val cape = ObjType(2000, "Test cape", wearpos1 = WearPos.CAPE, internalName = "test_cape")
    private val types = ObjTypes.of(axe, sword, twoHander, shield, whip, mithrilHelm, arrows, occult, coins, pegasians, cape)
    private val requirements = WearRequirements.parse(
        """
        # A comment line, then made-up levels in the file's layout.
        abyssal_whip	attack=70	Abyssal whip
        mithril_full_helm	defence=20	Mithril full helm
        test_cape	defence=5 combat=20	Made up: two levels, one of them combat
        """.trimIndent(),
        types,
    )
    private val equipment = Equipment(types, requirements)
    private val player = Player(1, "test", 3222, 3218, 0)

    @Test
    fun `wearing moves the item into its slot`() {
        player.inventory.add(axe, 1)
        assertEquals(EquipResult.Done, equipment.wear(player, 0))
        assertEquals(InvObj(1351, 1), player.equipment[WearPos.WEAPON])
        assertNull(player.inventory[0])
    }

    @Test
    fun `the replaced item goes into the slot the new one left`() {
        player.inventory.add(coins, 5)
        player.inventory.add(sword, 1)
        player.equipment[WearPos.WEAPON] = InvObj(1351, 1)
        assertEquals(EquipResult.Done, equipment.wear(player, 1))
        assertEquals(InvObj(1277, 1), player.equipment[WearPos.WEAPON])
        assertEquals(InvObj(1351, 1), player.inventory[1])
    }

    @Test
    fun `a two-handed weapon takes off the weapon and the shield, and needs room for the second`() {
        player.equipment[WearPos.WEAPON] = InvObj(1351, 1)
        player.equipment[WearPos.SHIELD] = InvObj(1171, 1)
        player.inventory.add(twoHander, 1)
        player.inventory.add(coins, 1)
        repeat(26) { player.inventory.add(sword, 1) }
        assertEquals(EquipResult.NoSpace, equipment.wear(player, 0))
        assertEquals(InvObj(1351, 1), player.equipment[WearPos.WEAPON])

        player.inventory.remove(27)
        assertEquals(EquipResult.Done, equipment.wear(player, 0))
        assertEquals(InvObj(1307, 1), player.equipment[WearPos.WEAPON])
        assertNull(player.equipment[WearPos.SHIELD])
        assertEquals(InvObj(1351, 1), player.inventory[0])
        assertEquals(InvObj(1171, 1), player.inventory[27])
    }

    @Test
    fun `a two-handed weapon with only a shield worn puts the shield in its slot`() {
        player.equipment[WearPos.SHIELD] = InvObj(1171, 1)
        repeat(27) { player.inventory.add(sword, 1) }
        player.inventory.add(twoHander, 1)
        assertEquals(EquipResult.Done, equipment.wear(player, 27))
        assertEquals(InvObj(1171, 1), player.inventory[27])
        assertNull(player.equipment[WearPos.SHIELD])
    }

    @Test
    fun `a shield takes off a two-handed weapon`() {
        player.equipment[WearPos.WEAPON] = InvObj(1307, 1)
        player.inventory.add(shield, 1)
        assertEquals(EquipResult.Done, equipment.wear(player, 0))
        assertEquals(InvObj(1171, 1), player.equipment[WearPos.SHIELD])
        assertNull(player.equipment[WearPos.WEAPON])
        assertEquals(InvObj(1307, 1), player.inventory[0])
    }

    @Test
    fun `a shield stays on with a one-handed weapon`() {
        player.equipment[WearPos.SHIELD] = InvObj(1171, 1)
        player.inventory.add(axe, 1)
        assertEquals(EquipResult.Done, equipment.wear(player, 0))
        assertEquals(InvObj(1171, 1), player.equipment[WearPos.SHIELD])
    }

    @Test
    fun `levels from the requirements file are checked against base levels`() {
        player.inventory.add(whip, 1)
        assertEquals(EquipResult.LevelTooLow(WearRequirement(Skill.ATTACK, 70)), equipment.wear(player, 0))
        player.skills.setCurrentLevel(Skill.ATTACK, 70)
        assertEquals(EquipResult.LevelTooLow(WearRequirement(Skill.ATTACK, 70)), equipment.wear(player, 0), "a boost isn't enough")
        player.skills.setLevel(Skill.ATTACK, 70)
        assertEquals(EquipResult.Done, equipment.wear(player, 0))
    }

    @Test
    fun `every level an item lists is checked, the combat level too`() {
        player.inventory.add(cape, 1)
        player.skills.setLevel(Skill.DEFENCE, 5)
        assertEquals(EquipResult.LevelTooLow(WearRequirement(null, 20)), equipment.wear(player, 0))
        player.skills.setLevel(Skill.HITPOINTS, 20)
        player.skills.setLevel(Skill.ATTACK, 25)
        player.skills.setLevel(Skill.STRENGTH, 25)
        assertEquals(EquipResult.Done, equipment.wear(player, 0), "combat level ${player.skills.combatLevel}")
    }

    @Test
    fun `items the file doesn't list need nothing, whatever their params say`() {
        // The cache's 434-437 params hold other things on some items (the Crafting level that makes a battlestaff).
        val staff = ObjType(1393, "Fire battlestaff", wearpos1 = WearPos.WEAPON, params = mapOf(434 to 12, 436 to 62), internalName = "fire_battlestaff")
        assertEquals(emptyList(), requirements[staff])
        assertEquals(listOf(WearRequirement(Skill.DEFENCE, 20)), requirements[mithrilHelm])
        assertEquals(emptyList(), requirements[axe])
    }

    @Test
    fun `a bad line fails the file`() {
        for (line in listOf("abyssal_whip\tattack", "abyssal_whip\tattack=100", "abyssal_whip\tcooking=0", "abyssal_whip\tluck=5", "abyssal_whip")) {
            assertFailsWith<IllegalArgumentException>(line) { WearRequirements.parse(line, types) }
        }
        assertEquals(1, WearRequirements.parse("abyssal_whip\tattack=70\nno_such_item\tattack=1", types).size)
    }

    @Test
    fun `ammo of the same kind joins the worn stack`() {
        player.equipment[WearPos.AMMO] = InvObj(882, 100)
        player.inventory.add(arrows, 50)
        assertEquals(EquipResult.Done, equipment.wear(player, 0))
        assertEquals(InvObj(882, 150), player.equipment[WearPos.AMMO])
        assertNull(player.inventory[0])
    }

    @Test
    fun `items that can't be worn, and empty slots, are ignored`() {
        player.inventory.add(coins, 1)
        assertEquals(EquipResult.Ignored, equipment.wear(player, 0))
        assertEquals(EquipResult.Ignored, equipment.wear(player, 5))
        assertEquals(EquipResult.Ignored, equipment.remove(player, WearPos.HEAD))
    }

    @Test
    fun `removing needs a free slot, or the item's stack for ammo`() {
        player.equipment[WearPos.WEAPON] = InvObj(1351, 1)
        player.equipment[WearPos.AMMO] = InvObj(882, 10)
        player.inventory.add(arrows, 5)
        repeat(27) { player.inventory.add(sword, 1) }
        assertEquals(EquipResult.NoSpace, equipment.remove(player, WearPos.WEAPON))
        assertEquals(EquipResult.Done, equipment.remove(player, WearPos.AMMO))
        assertEquals(InvObj(882, 15), player.inventory[0])
        player.inventory.remove(5)
        assertEquals(EquipResult.Done, equipment.remove(player, WearPos.WEAPON))
        assertEquals(InvObj(1351, 1), player.inventory[5])
        assertEquals(emptyList(), player.equipment.contents())
    }

    @Test
    fun `bonuses add up over everything worn`() {
        player.equipment[WearPos.WEAPON] = InvObj(1351, 1)
        player.equipment[WearPos.SHIELD] = InvObj(1171, 1)
        player.equipment[WearPos.AMMO] = InvObj(882, 10)
        player.equipment[WearPos.NECK] = InvObj(12002, 1)
        val bonuses = equipment.bonuses(player)
        assertEquals(listOf(-2, 4, 0, 12, 0), listOf(bonuses.stabAttack, bonuses.slashAttack, bonuses.crushAttack, bonuses.magicAttack, bonuses.rangedAttack))
        assertEquals(listOf(4, 5), listOf(bonuses.stabDefence, bonuses.slashDefence))
        assertEquals(5, bonuses.meleeStrength)
        assertEquals(7, bonuses.rangedStrength)
        assertEquals(50, bonuses.magicDamage)
        assertEquals(5, bonuses.attackSpeed)
        player.equipment[WearPos.WEAPON] = null
        assertEquals(4, equipment.bonuses(player).attackSpeed, "unarmed")
    }

    @Test
    fun `gear's ranged strength adds to the ammunition's`() {
        player.equipment[WearPos.AMMO] = InvObj(882, 10)
        player.equipment[WearPos.FEET] = InvObj(13237, 1)
        assertEquals(8, equipment.bonuses(player).rangedStrength)
        assertEquals(12, equipment.bonuses(player).rangedAttack)
        assertEquals(1, EquipmentBonuses.rangedStrength(pegasians))
        assertEquals(7, EquipmentBonuses.rangedStrength(arrows))
    }
}
