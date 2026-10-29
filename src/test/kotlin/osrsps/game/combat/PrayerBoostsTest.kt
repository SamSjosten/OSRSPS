package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import osrsps.game.Equipment
import osrsps.game.InvObj
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.SeqNames
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn

/** The prayers' boosts in the melee, ranged and magic formulas, with the real prayers file. */
class PrayerBoostsTest {
    // Shaped like the 241 cache's prayer objs: 630 the bit, 1751 the prayer book component, 1752 the name, 1753 the level.
    private fun prayerObj(id: Int, internal: String, name: String, bit: Int, level: Int) = ObjType(
        id, "Null", internalName = internal,
        params = mapOf(630 to bit, 1751 to (541 shl 16 or (9 + bit)), 1752 to name, 1753 to level, 1755 to 2690),
    )

    private val shortbow = ObjType(841, "Shortbow", wearpos1 = WearPos.WEAPON, wearpos2 = WearPos.SHIELD, params = mapOf(14 to 5), internalName = "shortbow", category = 64)
    private val objTypes = ObjTypes.of(
        shortbow,
        prayerObj(20803, "placeholder_xmas16_ball_inflated_red", "Thick Skin", 0, 1),
        prayerObj(20821, "placeholder_xmas16_marionette_red", "Sharp Eye", 18, 8),
        prayerObj(20827, "placeholder_xmas16_green_partyhat", "Rigour", 24, 74),
        prayerObj(20829, "placeholder_xmas16_blue_partyhat", "Piety", 26, 70),
        prayerObj(20830, "xmas16_teddy", "Augury", 27, 77),
    )
    private val prayers = Prayers(Prayers.load(objTypes), message = { _, _ -> }, sound = { _, _ -> })

    private val man = NpcType(3106, "Man", combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))
    private val mage = NpcType(3257, "Mage", combatLevel = 2, internalName = "mage", stats = NpcStats(hitpoints = 7))
    private val npcTypes = NpcTypes.of(man, mage)
    private val npcCombat = NpcCombat.parse(
        """{ "npcs": [ { "npcs": ["man"], "attackSpeed": 4, "maxHit": 1, "attackType": "crush", "attackBonus": 0, "strengthBonus": 0,
            "defence": { "stab": 0, "slash": 0, "crush": 0, "magic": 0, "light": 0, "standard": 0, "heavy": 0 },
            "anims": { "attack": "a", "defend": "b", "death": "c" }, "respawnTicks": 25, "source": "test" },
          { "npcs": ["mage"], "attackSpeed": 4, "maxHit": 1, "attackType": "magic", "attackBonus": 0, "strengthBonus": 0,
            "defence": { "stab": 0, "slash": 0, "crush": 0, "magic": 0, "light": 0, "standard": 0, "heavy": 0 },
            "anims": { "attack": "a", "defend": "b", "death": "c" }, "respawnTicks": 25, "source": "test" } ] }""",
        npcTypes, SeqNames.NONE,
    )
    private val styles = AttackStyles.fromRows(
        listOf(
            listOf<Any>(0) to listOf<Any>(
                0, "Punch", "(Accurate)<br>(Crush)<br>(Attack XP)", 0,
                1, "Kick", "(Aggressive)<br>(Crush)<br>(Strength XP)", 0,
                3, "Block", "(Defensive)<br>(Crush)<br>(Defence XP)", 0,
            ),
            listOf<Any>(3) to listOf<Any>(
                0, "Accurate", "(Accurate)<br>(Standard)<br>(Ranged XP)", 0,
                1, "Rapid", "(Rapid)<br>(Standard)<br>(Ranged XP)", 0,
                3, "Longrange", "(Longrange)<br>(Standard)<br>(Ranged XP)", 0,
            ),
        ),
    )
    private val combat = Combat(Equipment(objTypes), styles, npcCombat, prayerBoosts = prayers::boosts)
    private val spell = CombatSpell(
        obj = 0, name = "Test Blast", component = 0, level = 1, maxHit = 30, runes = emptyList(), xpTenths = 0, anim = -1,
        staffAnim = -1, casting = -1, travel = -1, impact = -1,
    )

    private val alice = Player(1, "alice", 3222, 3218, 0).apply {
        for (skill in listOf(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC, Skill.PRAYER)) skills.setLevel(skill, 99)
    }

    private fun npc(type: NpcType) = Npc(1, NpcSpawn(type, 3223, 3218, 0, source = "test"))

    /** Turns [name] on, then starts the next tick, from which it counts. */
    private fun pray(name: String) {
        val prayer = prayers.find(name)!!
        prayers.click(alice, prayer.component, 1)
        prayers.startTick(alice)
    }

    @Test
    fun `Piety multiplies Attack, Strength and Defence before the style bonus`() {
        val before = combat.matchup(alice, npc(man))!!
        // Punch is accurate: (99 + 3 + 8) * 64; ((99 + 8) * 64 + 320) / 640; (99 + 8) * 64.
        assertEquals(110 * 64, before.attackRoll)
        assertEquals(11, before.maxHit)
        assertEquals(107 * 64, before.playerDefenceRoll)
        // Turned on, it doesn't count until the next tick.
        prayers.click(alice, prayers.find("Piety")!!.component, 1)
        assertEquals(before, combat.matchup(alice, npc(man)))
        prayers.startTick(alice)
        val piety = combat.matchup(alice, npc(man))!!
        // 99 * 1.2 = 118.8, 99 * 1.23 = 121.77 and 99 * 1.25 = 123.75, each rounded down.
        assertEquals((118 + 3 + 8) * 64, piety.attackRoll)
        assertEquals(((121 + 8) * 64 + 320) / 640, piety.maxHit)
        assertEquals(13, piety.maxHit)
        assertEquals((123 + 8) * 64, piety.playerDefenceRoll)
        assertEquals(piety.playerDefenceRoll, combat.npcAttack(npc(man), alice)!!.defenceRoll)
    }

    @Test
    fun `a melee prayer does nothing for ranged, and Rigour boosts ranged accuracy, damage and Defence`() {
        alice.equipment[WearPos.WEAPON] = InvObj(shortbow.id, 1)
        pray("Piety")
        val piety = combat.rangedMatchup(alice, npc(man))!!
        assertEquals((99 + 3 + 8) * 64, piety.attackRoll)
        pray("Rigour")
        val rigour = combat.rangedMatchup(alice, npc(man))!!
        // 99 * 1.2 = 118.8 and 99 * 1.23 = 121.77, rounded down; accurate's +3 to both.
        assertEquals((118 + 3 + 8) * 64, rigour.attackRoll)
        assertEquals(((121 + 3 + 8) * 64 + 320) / 640, rigour.maxHit)
        assertEquals((123 + 8) * 64, combat.npcAttack(npc(man), alice)!!.defenceRoll)
    }

    @Test
    fun `Sharp Eye's damage boost is at least a level`() {
        alice.equipment[WearPos.WEAPON] = InvObj(shortbow.id, 1)
        alice.skills.setLevel(Skill.RANGED, 19)
        pray("Sharp Eye")
        val m = combat.rangedMatchup(alice, npc(man))!!
        // 19 * 1.05 = 19.95: accuracy stays at 19, damage goes to 20.
        assertEquals((19 + 3 + 8) * 64, m.attackRoll)
        assertEquals(((20 + 3 + 8) * 64 + 320) / 640, m.maxHit)
    }

    @Test
    fun `Augury boosts magic accuracy, damage and magic defence`() {
        val before = combat.magicMatchup(alice, npc(man), spell)!!
        assertEquals((99 + 8) * 64, before.attackRoll)
        assertEquals(30, before.maxHit)
        // Against a magic attack: 70% of Magic and 30% of Defence, each rounded down: 69 + 29.
        assertEquals((69 + 29 + 8) * 64, combat.npcAttack(npc(mage), alice)!!.defenceRoll)
        pray("Augury")
        val augury = combat.magicMatchup(alice, npc(man), spell)!!
        // 99 * 1.25 = 123.75; 30 * 1.04 = 31.2.
        assertEquals((123 + 8) * 64, augury.attackRoll)
        assertEquals(31, augury.maxHit)
        // 123 * 0.7 = 86.1 and 123 * 0.3 = 36.9.
        assertEquals((86 + 36 + 8) * 64, combat.npcAttack(npc(mage), alice)!!.defenceRoll)
        // Against melee only the Defence boost counts.
        assertEquals((123 + 8) * 64, combat.npcAttack(npc(man), alice)!!.defenceRoll)
    }

    @Test
    fun `prayers that boost different levels add up`() {
        pray("Thick Skin")
        assertEquals(PrayerBoosts(defence = 105), prayers.boosts(alice))
        alice.equipment[WearPos.WEAPON] = InvObj(shortbow.id, 1)
        pray("Sharp Eye")
        assertEquals(PrayerBoosts(defence = 105, ranged = 105, rangedStrength = 105, minRangedStrengthGain = 1), prayers.boosts(alice))
        // 99 * 1.05 = 103.95.
        assertEquals((103 + 8) * 64, combat.npcAttack(npc(man), alice)!!.defenceRoll)
    }

    @Test
    fun `the file's boosts are checked`() {
        val thickSkin = objTypes.find("placeholder_xmas16_ball_inflated_red")!!
        val types = ObjTypes.of(thickSkin)
        fun parse(boosts: String) = Prayers.parse(
            """{ "prayers": [ { "prayer": "placeholder_xmas16_ball_inflated_red", "name": "Thick Skin", "drain": 1, "boosts": $boosts } ] }""", types,
        )
        assertEquals(PrayerBoosts(defence = 105, magicDamage = 10), parse("""{"defence": 105, "magicDamage": 10}""").single().boosts)
        val unknown = assertFailsWith<IllegalArgumentException> { parse("""{"defense": 105}""") }
        assertTrue(unknown.message!!.contains("unknown boosts defense"), unknown.message)
        assertFailsWith<IllegalArgumentException> { parse("""{"defence": 5}""") }
    }
}
