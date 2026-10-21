package osrsps.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SkillsTest {
    private val skills = PlayerSkills()

    private fun flushed(): List<Triple<Skill, Int, Int>> =
        mutableListOf<Triple<Skill, Int, Int>>().also { out -> skills.flush { s, level, xp -> out.add(Triple(s, level, xp)) } }

    @Test
    fun `skill ids follow the client's order`() {
        assertEquals(24, Skill.COUNT)
        assertEquals(0, Skill.ATTACK.id)
        assertEquals(3, Skill.HITPOINTS.id)
        assertEquals(22, Skill.CONSTRUCTION.id)
        assertEquals(23, Skill.SAILING.id)
        assertEquals(Skill.RUNECRAFT, Skill.find("RC"))
        assertEquals(Skill.HITPOINTS, Skill.find("3"))
        assertEquals(Skill.DEFENCE, Skill.find("Defence"))
        assertNull(Skill.find("24"))
        assertNull(Skill.find("dungeoneering"))
    }

    @Test
    fun `xp table matches the wiki`() {
        assertEquals(0, Experience.xpForLevel(1))
        assertEquals(83, Experience.xpForLevel(2))
        assertEquals(1_154, Experience.xpForLevel(10))
        assertEquals(101_333, Experience.xpForLevel(50))
        assertEquals(737_627, Experience.xpForLevel(70))
        assertEquals(1_986_068, Experience.xpForLevel(80))
        assertEquals(6_517_253, Experience.xpForLevel(92))
        assertEquals(13_034_431, Experience.xpForLevel(99))
        assertEquals(1, Experience.levelForTenths(829))
        assertEquals(2, Experience.levelForTenths(830))
        assertEquals(98, Experience.levelForTenths(130_344_309))
        assertEquals(99, Experience.levelForTenths(Experience.MAX_TENTHS))
    }

    @Test
    fun `a new account has 10 hitpoints, level 1 elsewhere and combat 3, all sent once`() {
        assertEquals(10, skills.baseLevel(Skill.HITPOINTS))
        assertEquals(1_154, skills.xp(Skill.HITPOINTS))
        assertEquals(1, skills.currentLevel(Skill.SAILING))
        assertEquals(3, skills.combatLevel)
        val first = flushed()
        assertEquals(Skill.entries, first.map { it.first })
        assertEquals(Triple(Skill.HITPOINTS, 10, 1_154), first[3])
        assertEquals(emptyList(), flushed())
    }

    @Test
    fun `combat level follows the wiki formula`() {
        for (skill in listOf(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.HITPOINTS, Skill.PRAYER)) skills.setLevel(skill, 99)
        // 99 melee and prayer, 1 ranged and magic: 0.25 * (99 + 99 + 49) + 0.325 * 198 = 126.1.
        assertEquals(126, skills.combatLevel)
        val ranger = PlayerSkills().apply { setLevel(Skill.RANGED, 99) }
        // 0.25 * (1 + 10 + 0) + 0.325 * 148 = 50.85.
        assertEquals(50, ranger.combatLevel)
        val attackOnly = PlayerSkills().apply { setLevel(Skill.ATTACK, 99) }
        // 0.25 * 11 + 0.325 * 100 = 35.25.
        assertEquals(35, attackOnly.combatLevel)
    }

    @Test
    fun `adding xp levels up and carries a boost over`() {
        flushed()
        skills.setCurrentLevel(Skill.ATTACK, 5)
        assertEquals(2, skills.addTenths(Skill.ATTACK, 1_740))
        assertEquals(3, skills.baseLevel(Skill.ATTACK))
        assertEquals(7, skills.currentLevel(Skill.ATTACK))
        assertEquals(listOf(Triple(Skill.ATTACK, 7, 174)), flushed())
        assertEquals(0, skills.addTenths(Skill.ATTACK, 5))
        assertEquals(1_745, skills.tenths(Skill.ATTACK))
        skills.addTenths(Skill.ATTACK, Experience.MAX_TENTHS)
        assertEquals(200_000_000, skills.xp(Skill.ATTACK))
        assertEquals(99, skills.baseLevel(Skill.ATTACK))
    }

    @Test
    fun `hitpoints are the current hitpoints level`() {
        val player = Player(1, "test", 3222, 3218, 0)
        assertEquals(10, player.hitpoints)
        player.hitpoints = 4
        assertEquals(4, player.skills.currentLevel(Skill.HITPOINTS))
        assertEquals(10, player.skills.baseLevel(Skill.HITPOINTS))
    }

    @Test
    fun `refuses levels and xp out of range`() {
        flushed()
        assertFailsWith<IllegalArgumentException> { skills.setLevel(Skill.ATTACK, 0) }
        assertFailsWith<IllegalArgumentException> { skills.setLevel(Skill.ATTACK, 100) }
        assertFailsWith<IllegalArgumentException> { skills.setCurrentLevel(Skill.ATTACK, 256) }
        assertFailsWith<IllegalArgumentException> { skills.setTenths(Skill.ATTACK, -1) }
        assertFailsWith<IllegalArgumentException> { skills.addTenths(Skill.ATTACK, -1) }
        assertEquals(emptyList(), flushed())
    }

    @Test
    fun `resendAll sends every skill again`() {
        skills.flush { _, _, _ -> }
        skills.resendAll()
        var count = 0
        skills.flush { _, _, _ -> count++ }
        assertEquals(Skill.COUNT, count)
    }
}
