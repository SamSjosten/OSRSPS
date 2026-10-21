package osrsps.game.command

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.map.CollisionMap

class SkillCommandsTest {
    private val world = World(IntArray(0), CollisionMap())
    private val player = Player(1, "test", 3222, 3218, 0)
    private val replies = mutableListOf<String>()

    private fun run(input: String) = world.commands.execute(player, input, replies::add)

    @Test
    fun `setlevel attack 99 sets the level and the combat level`() {
        run("setlevel attack 99")
        assertEquals(listOf("Attack set to level 99 (13,034,431 xp). Combat level 3 -> 35."), replies)
        assertEquals(99, player.skills.baseLevel(Skill.ATTACK))
        assertEquals(99, player.skills.currentLevel(Skill.ATTACK))
        assertEquals(35, player.skills.combatLevel)
    }

    @Test
    fun `setlevel all and lower`() {
        run("setlevel all 99")
        run("setlevel hp 10")
        assertEquals(
            listOf(
                "Every skill set to level 99 (13,034,431 xp). Combat level 3 -> 126.",
                "Hitpoints set to level 10 (1,154 xp). Combat level 126 -> 103.",
            ),
            replies,
        )
        assertEquals(10, player.hitpoints)
    }

    @Test
    fun `addxp takes tenths and shows level ups`() {
        run("addxp cooking 17.5")
        run("addxp cooking 65.5")
        run("addxp 2 1000")
        assertEquals(
            listOf(
                "Cooking: 17 xp, level 1.",
                "Cooking: 83 xp, level 1 -> 2.",
                "Strength: 1,000 xp, level 1 -> 9. Combat level 3 -> 6.",
            ),
            replies,
        )
        assertEquals(830, player.skills.tenths(Skill.COOKING))
    }

    @Test
    fun `boost sets the current level only`() {
        run("boost hitpoints 1")
        assertEquals(listOf("Hitpoints is now 1/10"), replies)
        assertEquals(1, player.hitpoints)
        assertEquals(3, player.skills.combatLevel)
    }

    @Test
    fun `refuses bad input and changes nothing`() {
        player.skills.flush { _, _, _ -> }
        run("setlevel attack")
        run("setlevel magic 100")
        run("setlevel fishing x")
        run("setlevel dungeoneering 5")
        run("addxp attack -1")
        run("addxp attack 1.25")
        run("addxp attack 200000001")
        run("boost attack 256")
        assertEquals(
            listOf(
                "Usage: ::setlevel skill|all level",
                "Levels are 1 to 99",
                "Usage: ::setlevel skill|all level ('x' is not a number)",
                "No skill 'dungeoneering'",
                "Usage: ::addxp skill xp (a number with at most one decimal place)",
                "Usage: ::addxp skill xp (a number with at most one decimal place)",
                "Usage: ::addxp skill xp (a number with at most one decimal place)",
                "Current levels are 0 to 255",
            ),
            replies,
        )
        val changed = mutableListOf<Skill>()
        player.skills.flush { skill, _, _ -> changed.add(skill) }
        assertEquals(emptyList(), changed)
    }
}
