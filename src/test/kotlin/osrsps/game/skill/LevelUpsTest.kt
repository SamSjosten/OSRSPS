package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import osrsps.game.Interfaces
import osrsps.game.PlayerSkills
import osrsps.game.Skill
import osrsps.game.dialogue.DialogueText
import osrsps.game.script.Handlers
import osrsps.game.script.ScriptRunner

class LevelUpsTest {
    private val unlocks = SkillUnlocks(mapOf(Skill.WOODCUTTING to setOf(15, 30)))

    /** Only the jingle choice is tested here; the box and message go through the headless client. */
    private val levelUps = LevelUps(Interfaces(), ScriptRunner(Handlers(), {}) { 0L }, unlocks) { DialogueText.NONE }

    private fun jingle(skill: Skill, level: Int) = levelUps.jingle(skill, level)

    @Test
    fun `a skill plays its own jingle, or the unlocks one at a level that unlocks something`() {
        assertEquals(69, jingle(Skill.WOODCUTTING, 14))
        assertEquals(70, jingle(Skill.WOODCUTTING, 15))
        assertEquals(53, jingle(Skill.MINING, 2))
        assertEquals(51, jingle(Skill.MAGIC, 2))
        assertEquals(63, jingle(Skill.SMITHING, 2))
        assertEquals(41, jingle(Skill.FISHING, 2))
        assertEquals(33, jingle(Skill.COOKING, 2))
        assertEquals(39, jingle(Skill.FIREMAKING, 2))
    }

    @Test
    fun `the skills with level-banded jingles`() {
        assertEquals(47, jingle(Skill.HITPOINTS, 49))
        assertEquals(48, jingle(Skill.HITPOINTS, 50))
        assertEquals(65, jingle(Skill.STRENGTH, 2))
        assertEquals(66, jingle(Skill.STRENGTH, 99))
        assertEquals(49, jingle(Skill.HUNTER, 10))
        assertEquals(50, jingle(Skill.HUNTER, 11))
        assertEquals(32, jingle(Skill.CONSTRUCTION, 40))
        assertEquals(31, jingle(Skill.CONSTRUCTION, 41))
        assertEquals(28, jingle(Skill.AGILITY, 2))
        assertNull(jingle(Skill.SAILING, 2))
    }

    @Test
    fun `every skill has a layer in levelup_display, each its own`() {
        val layers = Skill.entries.map(LevelUps::layer)
        assertEquals(layers.size, layers.toSet().size)
        assertEquals(53, LevelUps.layer(Skill.WOODCUTTING))
        assertEquals(36, LevelUps.layer(Skill.MINING))
    }

    @Test
    fun `a or an`() {
        assertEquals("an", LevelUps.article(Skill.ATTACK))
        assertEquals("an", LevelUps.article(Skill.AGILITY))
        assertEquals("a", LevelUps.article(Skill.WOODCUTTING))
    }

    @Test
    fun `XP past a level marks the skill once, and a raised combat level remembers where it was`() {
        val skills = PlayerSkills()
        skills.addTenths(Skill.WOODCUTTING, 830)
        skills.addTenths(Skill.WOODCUTTING, 10)
        assertEquals(listOf(Skill.WOODCUTTING), skills.takeAdvanced())
        assertEquals(emptyList(), skills.takeAdvanced())
        assertNull(skills.takeCombatFrom())

        skills.addTenths(Skill.ATTACK, 13_034_431 * 10)
        assertEquals(listOf(Skill.ATTACK), skills.takeAdvanced())
        assertEquals(3, skills.takeCombatFrom())
        assertNull(skills.takeCombatFrom())
    }

    @Test
    fun `setting a level is not a level-up`() {
        val skills = PlayerSkills()
        skills.setLevel(Skill.MINING, 50)
        assertEquals(emptyList(), skills.takeAdvanced())
    }
}
