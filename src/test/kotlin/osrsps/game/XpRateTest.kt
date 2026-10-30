package osrsps.game

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir
import osrsps.map.CollisionMap

class XpRateTest {
    @Test
    fun `1x by default leaves every XP drop exact`() {
        val skills = PlayerSkills()
        skills.addTenths(Skill.COOKING, 175)
        skills.addTenths(Skill.COOKING, 3)
        assertEquals(178, skills.tenths(Skill.COOKING))
        assertEquals("1x", XpRate().toString())
    }

    @Test
    fun `a rate multiplies XP drops, kept in tenths and rounded down`() {
        val rate = XpRate()
        val skills = PlayerSkills(rate)
        rate.set(25)
        skills.addTenths(Skill.PRAYER, 45)
        assertEquals(112, skills.tenths(Skill.PRAYER), "4.5 x 2.5 = 11.25, kept as 11.2")
        rate.set(1)
        skills.addTenths(Skill.PRAYER, 45)
        assertEquals(116, skills.tenths(Skill.PRAYER), "0.1x: 4.5 gives 0.4")
    }

    @Test
    fun `a high rate still stops at 200M`() {
        val rate = XpRate().apply { set(XpRate.MAX) }
        val skills = PlayerSkills(rate)
        skills.addTenths(Skill.ATTACK, Int.MAX_VALUE / 2)
        assertEquals(Experience.MAX_TENTHS, skills.tenths(Skill.ATTACK))
        assertEquals(99, skills.baseLevel(Skill.ATTACK))
    }

    @Test
    fun `addExactTenths ignores the rate`() {
        val rate = XpRate().apply { set(50) }
        val skills = PlayerSkills(rate)
        skills.addExactTenths(Skill.MINING, 175)
        assertEquals(175, skills.tenths(Skill.MINING))
    }

    @Test
    fun `parse takes up to one decimal place from 0,1 to 1000`() {
        assertEquals(10, XpRate.parse("1"))
        assertEquals(25, XpRate.parse("2.5"))
        assertEquals(50, XpRate.parse("5x"))
        assertEquals(1, XpRate.parse("0.1"))
        assertEquals(10_000, XpRate.parse("1000"))
        assertNull(XpRate.parse("0"))
        assertNull(XpRate.parse("1000.1"))
        assertNull(XpRate.parse("1.25"))
        assertNull(XpRate.parse("-2"))
        assertNull(XpRate.parse("fast"))
        assertFailsWith<IllegalArgumentException> { XpRate().set(0) }
    }

    @Test
    fun `the rate lasts across a restart, and a bad file is 1x`(@TempDir dir: Path) {
        val file = dir.resolve(XpRate.FILE_NAME)
        XpRate(file).set(30)
        assertEquals(30, XpRate(file).tenths)
        assertEquals(10, XpRate(file).restore(), "restore goes back to the rate before, after a restart too")
        assertEquals(10, XpRate(file).tenths)
        Files.writeString(file, "# hand edited\n\n7.5\n")
        assertEquals(75, XpRate(file).tenths)
        Files.writeString(file, "lots\n")
        assertEquals(10, XpRate(file).tenths)
    }

    @Test
    fun `xprate shows, sets and restores the rate players log in with`() {
        val world = World(IntArray(0), CollisionMap())
        val player = world.loadPlayer(1, "test")
        val replies = mutableListOf<String>()
        fun run(input: String) = world.commands.execute(player, input, replies::add)
        run("xprate")
        run("xprate 5")
        player.skills.addTenths(Skill.WOODCUTTING, 250)
        run("addxp woodcutting 25")
        run("xprate restore")
        run("xprate 1.25")
        run("xprate 2 3")
        assertEquals(
            listOf(
                "XP rate: 1x",
                "XP rate set to 5x (was 1x)",
                "Woodcutting: 150 xp, level 2.",
                "XP rate back to 1x",
                "XP rates are 0.1 to 1000, with at most one decimal place",
                "Usage: ::xprate [rate|restore]",
            ),
            replies,
        )
        assertEquals(1500, player.skills.tenths(Skill.WOODCUTTING), "125 XP at 5x, then ::addxp's exact 25")
    }
}
