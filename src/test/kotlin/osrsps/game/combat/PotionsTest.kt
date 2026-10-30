package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import osrsps.game.InvObj
import osrsps.game.Movement
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos

/** Drinking: boosts, restores, the brews, divine potions, the drink delay, doses and the potion file. */
class PotionsTest {
    private fun dose(id: Int, internal: String, name: String) = ObjType(id, name, iops = listOf("Drink", null, null, null, "Drop"), internalName = internal)

    /** Four doses of the potion with [stem], at ids [first] to [first] + 3, most first. */
    private fun doses(first: Int, stem: String, name: String) = (4 downTo 1).mapIndexed { i, n -> dose(first + i, "${n}dose$stem", "$name($n)") }

    private val superCombat = doses(100, "2combat", "Super combat potion")
    private val saradominBrew = doses(110, "potionofsaradomin", "Saradomin brew")
    private val zamorakBrew = doses(120, "potionofzamorak", "Zamorak brew")
    private val superRestore = doses(130, "2restore", "Super restore")
    private val prayerPotion = doses(140, "prayerrestore", "Prayer potion")
    private val divineCombat = doses(150, "divinecombat", "Divine super combat potion")
    private val energy = doses(160, "1energy", "Energy potion")
    private val vial = ObjType(229, "Vial", internalName = "vial_empty")
    private val wrench = ObjType(6714, "Holy wrench", internalName = "deal_wrench_blessed")
    private val ring = ObjType(13202, "Ring of the gods (i)", internalName = "nzone_rotg")
    private val shark = ObjType(385, "Shark", iops = listOf("Eat"), internalName = "shark")
    private val objTypes = ObjTypes.of(
        *(superCombat + saradominBrew + zamorakBrew + superRestore + prayerPotion + divineCombat + energy).toTypedArray(),
        vial, wrench, ring, shark,
    )
    private val messages = mutableListOf<String>()
    private val data = Potions.load(objTypes)
    private val potions = Potions(data, message = { _, text -> messages += text })
    private val restore = StatRestore(divineHolds = data.divineHolds)

    // The divine timers as the 241 cache has them: divinecombat_potion_time is bits 0 to 8 of divine_potion_4; the
    // varp id here is made up.
    private val varTypes = VarTypes(6_000, mapOf(DIVINE_COMBAT_VARBIT to VarbitType(5_000, 0, 8)))

    private val alice = Player(1, "alice", 3222, 3218, 0, varTypes).apply {
        for (skill in Skill.entries) skills.setLevel(skill, 99)
    }

    private fun give(vararg items: ObjType) {
        for ((slot, item) in items.withIndex()) alice.inventory[slot] = InvObj(item.id, 1)
    }

    private fun drink(slot: Int, tick: Long) = assertTrue(potions.drink(alice, slot, alice.inventory[slot]!!.id, tick))

    private fun level(skill: Skill) = alice.skills.currentLevel(skill)

    @Test
    fun `the file is read against the cache, each dose of a potion found by its stem`() {
        val potion = potions.all.getValue(superCombat[0].id)
        assertEquals("super combat potion", potion.name)
        assertEquals(superCombat.map { it.id }, potion.doses)
        assertEquals(PotionLines.DRINK, potion.message)
        assertEquals(PotionLines.DRINK_POTION, potions.all.getValue(superRestore[0].id).message, "Super restore: the words add 'potion'")
        assertEquals(PotionLines.DRINK_BREW, potions.all.getValue(saradominBrew[0].id).message)
        assertTrue(potions.all.getValue(divineCombat[0].id).divine)
        assertEquals(7 * 4, potions.all.size, "the other potions in the file aren't in this cache")
    }

    @Test
    fun `a dose boosts from the base level, leaves the next dose and says how many are left`() {
        give(superCombat[0])
        alice.skills.setCurrentLevel(Skill.STRENGTH, 80)
        alice.skills.setCurrentLevel(Skill.DEFENCE, 110)
        drink(0, 10)
        // 99 * 15% + 5 = 19.
        assertEquals(118, level(Skill.ATTACK))
        assertEquals(99, level(Skill.STRENGTH), "a drained stat gets the boost on its current level")
        assertEquals(118, level(Skill.DEFENCE))
        assertEquals(superCombat[1].id, alice.inventory[0]?.id)
        assertEquals(Foods.EAT_ANIM, alice.effects.anim?.id)
        assertEquals(listOf("[potion.drink potion=super combat potion]", "[potion.doses_left doses=3]"), messages)
        drink(0, 13)
        assertEquals(118, level(Skill.ATTACK), "boosts don't stack")
        assertEquals(118, level(Skill.STRENGTH))
    }

    @Test
    fun `the drink delay is 3 ticks on its own timer, holds back ordinary food but not an attack`() {
        give(superCombat[3], prayerPotion[0], prayerPotion[1])
        alice.skills.setCurrentLevel(Skill.PRAYER, 10)
        // Food eaten this tick: the potion still goes down.
        alice.foodDelayUntil = 13
        alice.comboFoodDelayUntil = 10
        alice.nextAttackTick = 12
        drink(0, 10)
        assertEquals(vial.id, alice.inventory[0]?.id, "the last dose leaves a vial")
        assertEquals("[potion.finished]", messages.last())
        drink(1, 12)
        assertEquals(10, level(Skill.PRAYER))
        drink(1, 13)
        // 99 * 25% + 7 = 31.
        assertEquals(41, level(Skill.PRAYER))
        assertEquals(12, alice.nextAttackTick)
        assertEquals(16, alice.foodDelayUntil, "food after a potion waits out the drink delay")
        assertEquals(10, alice.comboFoodDelayUntil, "a combo food can still follow")
    }

    @Test
    fun `a Saradomin brew heals past the level and boosts Defence, and lowers the rest by their current levels`() {
        give(saradominBrew[0])
        alice.skills.setCurrentLevel(Skill.MAGIC, 50)
        drink(0, 10)
        // 99 * 15% + 2 = 16; 99 * 20% + 2 = 21.
        assertEquals(115, level(Skill.HITPOINTS))
        assertEquals(120, level(Skill.DEFENCE))
        // 99 - (9 + 2); 50 - (5 + 2).
        assertEquals(88, level(Skill.ATTACK))
        assertEquals(43, level(Skill.MAGIC))
        assertEquals("[potion.drink_brew potion=saradomin brew]", messages.first())
    }

    @Test
    fun `a Zamorak brew boosts, lowers Defence, takes hitpoints down to 1 at the least and gives prayer points`() {
        give(zamorakBrew[0], zamorakBrew[1])
        alice.skills.setCurrentLevel(Skill.PRAYER, 50)
        drink(0, 10)
        assertEquals(99 + 21, level(Skill.ATTACK))
        assertEquals(99 + 13, level(Skill.STRENGTH))
        assertEquals(99 - 11, level(Skill.DEFENCE))
        assertEquals(99 - 11, alice.hitpoints, "12% of 99")
        assertEquals(11, alice.effects.pendingHits.single().value)
        assertEquals(59, level(Skill.PRAYER))
        alice.hitpoints = 1
        drink(1, 13)
        assertEquals(1, alice.hitpoints)
    }

    @Test
    fun `a super restore brings every stat but Hitpoints back toward its level, and a holy wrench adds to the prayer points`() {
        give(superRestore[0], superRestore[1], wrench)
        alice.skills.setCurrentLevel(Skill.ATTACK, 50)
        alice.skills.setCurrentLevel(Skill.WOODCUTTING, 90)
        alice.skills.setCurrentLevel(Skill.PRAYER, 0)
        alice.hitpoints = 50
        drink(0, 10)
        // 99 * 25% + 8 = 32; 99 * 27% + 8 = 34 with the wrench.
        assertEquals(82, level(Skill.ATTACK))
        assertEquals(99, level(Skill.WOODCUTTING), "never past the level")
        assertEquals(34, level(Skill.PRAYER))
        assertEquals(50, alice.hitpoints)
        alice.inventory[2] = null
        alice.equipment[WearPos.RING] = InvObj(ring.id, 1)
        drink(1, 13)
        assertEquals(68, level(Skill.PRAYER), "the ring counts worn")
    }

    @Test
    fun `a divine potion needs more than 10 hitpoints, takes 10, and holds its boost for five minutes`() {
        give(divineCombat[0])
        alice.hitpoints = 10
        drink(0, 10)
        assertEquals("[potion.divine_hitpoints]", messages.single())
        assertEquals(99, level(Skill.ATTACK))
        assertEquals(divineCombat[0].id, alice.inventory[0]?.id)
        alice.hitpoints = 50
        drink(0, 10)
        assertEquals(40, alice.hitpoints)
        assertEquals(118, level(Skill.ATTACK))
        alice.skills.setCurrentLevel(Skill.MINING, 105)
        // The drinking tick's own count, then the client sees the five minutes.
        restore.process(alice)
        assertEquals(Potions.DIVINE_TICKS, alice.vars.getVarbit(DIVINE_COMBAT_VARBIT))
        repeat(Potions.DIVINE_TICKS - 1) { restore.process(alice) }
        assertEquals(118, level(Skill.ATTACK))
        assertEquals(118, level(Skill.DEFENCE))
        assertEquals(100, level(Skill.MINING), "other boosts wear off as usual")
        restore.process(alice)
        assertEquals(99, level(Skill.ATTACK), "back to the level at once, 500 ticks after the drinking tick")
        assertEquals(99, level(Skill.STRENGTH))
        assertEquals(0, alice.vars.getVarbit(DIVINE_COMBAT_VARBIT))
    }

    @Test
    fun `a scenario starting ends the divine hold`() {
        give(divineCombat[0])
        drink(0, 10)
        potions.clearDivine(alice)
        assertEquals(0, alice.vars.getVarbit(DIVINE_COMBAT_VARBIT))
        restore.process(alice)
        assertEquals(118, level(Skill.ATTACK), "the levels are left as they are")
    }

    @Test
    fun `an energy potion gives back 15% run energy`() {
        give(energy[0])
        alice.runEnergy = 5_000
        drink(0, 10)
        assertEquals(6_500, alice.runEnergy)
        alice.runEnergy = Movement.MAX_ENERGY - 100
        drink(0, 13)
        assertEquals(Movement.MAX_ENERGY, alice.runEnergy)
    }

    @Test
    fun `a bad entry names itself`() {
        val bad = """{"vial": "vial_empty", "potions": [{"potion": "2combat", "effects": [{"boost": ["fletchcooking"]}]}]}"""
        val error = assertFailsWith<IllegalArgumentException> { Potions.parse(bad, objTypes) }
        assertEquals("/potions.json: potions entry 1: no skill 'fletchcooking'", error.message)
    }

    private companion object {
        const val DIVINE_COMBAT_VARBIT = 13663
    }
}
