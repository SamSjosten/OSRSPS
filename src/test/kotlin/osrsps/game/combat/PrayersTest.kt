package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.game.Equipment
import osrsps.game.InvObj
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos

/** The prayer book: the file read against the cache's prayer objs, turning prayers on and off, conflicts, drain. */
class PrayersTest {
    // Shaped like the 241 cache's prayer objs: 630 the bit, 1751 the prayer book component, 1752 the name, 1753 the
    // level, 1755 the sound.
    private fun prayerObj(id: Int, internal: String, name: String, bit: Int, level: Int, sound: Int) = ObjType(
        id, "Null", internalName = internal,
        params = mapOf(630 to bit, 1751 to (541 shl 16 or (9 + bit)), 1752 to name, 1753 to level, 1755 to sound),
    )

    private val thickSkin = prayerObj(20803, "placeholder_xmas16_ball_inflated_red", "Thick Skin", 0, 1, 2690)
    private val burst = prayerObj(20804, "xmas16_ball_inflated_green", "Burst of Strength", 1, 4, 2688)
    private val clarity = prayerObj(20805, "placeholder_xmas16_ball_inflated_green", "Clarity of Thought", 2, 7, 2664)
    private val rockSkin = prayerObj(20806, "xmas16_ball_inflated_blue", "Rock Skin", 3, 10, 2684)
    private val protectMagic = prayerObj(20815, "placeholder_xmas16_marionette_unpainted", "Protect from Magic", 12, 37, 2675)
    private val protectMelee = prayerObj(20817, "placeholder_xmas16_marionette_blue", "Protect from Melee", 14, 43, 2676)
    private val smite = prayerObj(20820, "xmas16_marionette_red", "Smite", 17, 52, 2686)
    private val sharpEye = prayerObj(20821, "placeholder_xmas16_marionette_red", "Sharp Eye", 18, 8, 2685)
    private val piety = prayerObj(20829, "placeholder_xmas16_blue_partyhat", "Piety", 26, 70, 3825)
    private val holySymbol = ObjType(1718, "Holy symbol", wearpos1 = WearPos.NECK, params = mapOf(11 to 15), internalName = "holy_symbol")
    private val objTypes = ObjTypes.of(thickSkin, burst, clarity, rockSkin, protectMagic, protectMelee, smite, sharpEye, piety, holySymbol)

    private val messages = mutableListOf<String>()
    private val sounds = mutableListOf<Int>()
    private val overheads = mutableListOf<Int>()
    private val prayers = Prayers(
        Prayers.load(objTypes), Equipment(objTypes), message = { _, text -> messages += text }, sound = { _, id -> sounds += id },
        setOverhead = { _, icon -> overheads += icon },
    )
    private val alice = Player(1, "alice", 3222, 3218, 0).apply {
        skills.setLevel(Skill.PRAYER, 99)
        skills.setLevel(Skill.DEFENCE, 99)
    }

    private fun prayer(name: String) = prayers.find(name)!!

    private fun click(name: String) = assertTrue(prayers.click(alice, prayer(name).component, 1))

    private fun on(): List<String> = prayers.all.filter { prayers.isActive(alice, it) }.map { it.name }

    @Test
    fun `the file is read against the cache's prayer objs, skipping the ones it doesn't have`() {
        // The test cache has 9 of the file's 29.
        assertEquals(9, prayers.all.size)
        val melee = prayer("Protect from Melee")
        assertEquals(Prayer(protectMelee.id, "Protect from Melee", 14, 23, 43, 2676, 12, setOf("overhead"), overhead = 0, protects = AttackType.CRUSH), melee)
        assertEquals(70, prayer("Piety").defence)
        val renamed = ObjTypes.of(prayerObj(20803, "placeholder_xmas16_ball_inflated_red", "Thin Skin", 0, 1, 2690))
        val e = assertFailsWith<IllegalArgumentException> { Prayers.load(renamed) }
        assertTrue(e.message!!.contains("Thin Skin"), e.message)
    }

    @Test
    fun `a prayer turned on turns off the ones it shares a group with, and makes its sound`() {
        click("Thick Skin")
        click("Clarity of Thought")
        click("Burst of Strength")
        assertEquals(listOf("Thick Skin", "Burst of Strength", "Clarity of Thought"), on())
        click("Rock Skin")
        assertEquals(listOf("Burst of Strength", "Clarity of Thought", "Rock Skin"), on())
        // Sharp Eye boosts ranged attack and strength: it turns both melee ones off but leaves the Defence one.
        click("Sharp Eye")
        assertEquals(listOf("Rock Skin", "Sharp Eye"), on())
        click("Piety")
        assertEquals(listOf("Piety"), on())
        assertEquals(listOf(2690, 2664, 2688, 2684, 2685, 3825), sounds)
        // Clicked again, it goes off.
        click("Piety")
        assertEquals(emptyList(), on())
        assertEquals(Prayers.DEACTIVATE_SOUND, sounds.last())
        assertEquals(0, alice.vars[Prayers.VARP_PRAYERS])
    }

    @Test
    fun `overhead prayers replace each other, and the icon follows`() {
        click("Protect from Melee")
        click("Thick Skin")
        assertEquals(0, alice.overheadIcon)
        click("Smite")
        assertEquals(listOf("Thick Skin", "Smite"), on())
        assertEquals(4, alice.overheadIcon)
        click("Smite")
        assertEquals(-1, alice.overheadIcon)
        assertEquals(listOf(0, 4, -1), overheads)
    }

    @Test
    fun `too low a Prayer or Defence level, or no points, is refused`() {
        alice.skills.setLevel(Skill.PRAYER, 69)
        click("Piety")
        assertEquals("You need a Prayer level of 70 to use Piety.", messages.last())
        alice.skills.setLevel(Skill.PRAYER, 70)
        alice.skills.setLevel(Skill.DEFENCE, 69)
        click("Piety")
        assertEquals("You need a Prayer level of 70 and a Defence level of 70 to use Piety.", messages.last())
        alice.skills.setLevel(Skill.DEFENCE, 70)
        alice.skills.setCurrentLevel(Skill.PRAYER, 0)
        click("Piety")
        // The client says this one itself.
        assertEquals(2, messages.size)
        assertEquals(emptyList(), on())
        alice.skills.setCurrentLevel(Skill.PRAYER, 1)
        click("Piety")
        assertEquals(listOf("Piety"), on())
    }

    @Test
    fun `a prayer counts from the tick after it is turned on, and drains from then`() {
        prayers.startTick(alice)
        click("Protect from Melee")
        assertTrue(prayers.isActive(alice, prayer("Protect from Melee")))
        assertFalse(prayers.protects(alice, AttackType.SLASH))
        prayers.drain(alice)
        assertEquals(0, alice.prayerDrainCounter)
        prayers.startTick(alice)
        assertTrue(prayers.protects(alice, AttackType.STAB))
        assertFalse(prayers.protects(alice, AttackType.MAGIC))
        // Drain 12 against resistance 60: the counter passes 60 on the 6th tick, then every 5th (OSRS Wiki: a point
        // every 3 seconds).
        val lost = mutableListOf<Int>()
        for (tick in 1..16) {
            prayers.drain(alice)
            lost += 99 - alice.skills.currentLevel(Skill.PRAYER)
            prayers.startTick(alice)
        }
        assertEquals(listOf(0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3), lost)
    }

    @Test
    fun `turned off and on in the same tick, a prayer keeps counting but doesn't drain`() {
        click("Protect from Magic")
        prayers.startTick(alice)
        prayers.drain(alice)
        assertEquals(12, alice.prayerDrainCounter)
        prayers.startTick(alice)
        click("Protect from Magic")
        click("Protect from Magic")
        assertTrue(prayers.protects(alice, AttackType.MAGIC))
        prayers.drain(alice)
        // With every prayer off for a moment the counter starts again.
        assertEquals(0, alice.prayerDrainCounter)
        prayers.startTick(alice)
        prayers.drain(alice)
        assertEquals(12, alice.prayerDrainCounter)
        assertEquals(99, alice.skills.currentLevel(Skill.PRAYER))
    }

    @Test
    fun `the worn prayer bonus slows the drain, and at no points every prayer goes off`() {
        alice.equipment[WearPos.NECK] = InvObj(holySymbol.id, 1)
        assertEquals(90, prayers.drainResistance(alice))
        alice.skills.setCurrentLevel(Skill.PRAYER, 2)
        click("Protect from Melee")
        click("Thick Skin")
        prayers.startTick(alice)
        // 13 a tick against 90: points go on the 7th tick and the 14th.
        repeat(13) { prayers.drain(alice) }
        assertEquals(1, alice.skills.currentLevel(Skill.PRAYER))
        assertEquals(listOf("Thick Skin", "Protect from Melee"), on())
        prayers.drain(alice)
        assertEquals(0, alice.skills.currentLevel(Skill.PRAYER))
        assertEquals(emptyList(), on())
        assertEquals(-1, alice.overheadIcon)
        assertEquals(Prayers.OUT_OF_POINTS, messages.last())
        assertEquals(Prayers.DEPLETE_SOUND, sounds.last())
        assertEquals(0, alice.prayerDrainCounter)
    }
}
