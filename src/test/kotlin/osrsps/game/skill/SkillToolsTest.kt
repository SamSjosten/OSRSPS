package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.InvObj
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos

class SkillToolsTest {
    private val bronze = SkillTool(BRONZE, 1, 879)
    private val rune = SkillTool(RUNE, 41, 867)
    private val axes = SkillTools(Skill.WOODCUTTING, listOf(rune, bronze))
    private val alice = Player(1, "alice", 3222, 3218, 0)

    private fun carry(id: Int) = alice.inventory.add(ObjType(id, "Axe"), 1)

    @Test
    fun `no axe at all`() {
        assertEquals(ToolCheck.None, axes.best(alice))
    }

    @Test
    fun `the best axe the level allows, from the backpack`() {
        carry(BRONZE)
        carry(RUNE)
        assertEquals(ToolCheck.Found(bronze), axes.best(alice))
        alice.skills.setLevel(Skill.WOODCUTTING, 41)
        assertEquals(ToolCheck.Found(rune), axes.best(alice))
    }

    @Test
    fun `a wielded axe counts`() {
        alice.skills.setLevel(Skill.WOODCUTTING, 50)
        alice.equipment[WearPos.WEAPON] = InvObj(RUNE, 1)
        assertEquals(ToolCheck.Found(rune), axes.best(alice))
    }

    @Test
    fun `only axes above the level`() {
        carry(RUNE)
        assertEquals(ToolCheck.TooLow(41), axes.best(alice))
    }

    @Test
    fun `a boost lets a tool be used early`() {
        carry(RUNE)
        alice.skills.setLevel(Skill.WOODCUTTING, 38)
        alice.skills.setCurrentLevel(Skill.WOODCUTTING, 41)
        assertEquals(ToolCheck.Found(rune), axes.best(alice))
    }

    private companion object {
        const val BRONZE = 1351
        const val RUNE = 1359
    }
}
