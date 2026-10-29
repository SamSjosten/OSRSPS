package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.Player
import osrsps.game.Skill

/** Boosts wearing off and drains coming back, with Rapid Restore and Preserve. */
class StatRestoreTest {
    private val prayersOn = mutableSetOf<String>()
    private val restore = StatRestore { _, effect -> effect in prayersOn }
    private val alice = Player(1, "alice", 3222, 3218, 0).apply {
        for (skill in Skill.entries) skills.setLevel(skill, 90)
    }

    /** Runs [ticks] ticks of [StatRestore.process]. */
    private fun run(ticks: Int) = repeat(ticks) { restore.process(alice) }

    private fun level(skill: Skill) = alice.skills.currentLevel(skill)

    @Test
    fun `a boost loses a level a minute on a cycle that runs from login, not from the boost`() {
        run(60)
        alice.skills.setCurrentLevel(Skill.ATTACK, 95)
        alice.skills.setCurrentLevel(Skill.WOODCUTTING, 93)
        run(39)
        assertEquals(95, level(Skill.ATTACK))
        run(1)
        assertEquals(94, level(Skill.ATTACK), "40 ticks after the boost, as the cycle began at login")
        assertEquals(92, level(Skill.WOODCUTTING))
        run(100)
        assertEquals(93, level(Skill.ATTACK))
        run(200)
        assertEquals(91, level(Skill.ATTACK))
        assertEquals(90, level(Skill.WOODCUTTING))
        run(300)
        assertEquals(90, level(Skill.WOODCUTTING), "never below the base level")
    }

    @Test
    fun `boosted Prayer goes down on the combat cycle, and Hitpoints on its own one that Rapid Heal starts again`() {
        alice.skills.setCurrentLevel(Skill.HITPOINTS, 99)
        alice.skills.setCurrentLevel(Skill.PRAYER, 92)
        run(100)
        assertEquals(98, level(Skill.HITPOINTS))
        assertEquals(91, level(Skill.PRAYER))
        run(90)
        // What Prayers does as Rapid Heal goes on, in the tick's input.
        alice.statTimers.hitpoints.restart()
        run(10)
        assertEquals(90, level(Skill.PRAYER))
        assertEquals(98, level(Skill.HITPOINTS), "held by Rapid Heal")
        run(90)
        assertEquals(98, level(Skill.HITPOINTS), "the tick it went on and 99 more")
        run(1)
        assertEquals(97, level(Skill.HITPOINTS))
    }

    @Test
    fun `a drained stat gets a level back a minute, but Hitpoints and Prayer don't`() {
        alice.skills.setCurrentLevel(Skill.DEFENCE, 85)
        alice.skills.setCurrentLevel(Skill.MINING, 89)
        alice.skills.setCurrentLevel(Skill.HITPOINTS, 50)
        alice.skills.setCurrentLevel(Skill.PRAYER, 10)
        run(99)
        assertEquals(85, level(Skill.DEFENCE))
        run(1)
        assertEquals(86, level(Skill.DEFENCE))
        assertEquals(90, level(Skill.MINING))
        run(400)
        assertEquals(90, level(Skill.DEFENCE), "never above the base level")
        assertEquals(50, level(Skill.HITPOINTS), "hitpoints come back by regeneration, not here")
        assertEquals(10, level(Skill.PRAYER))
    }

    @Test
    fun `Rapid Restore adds a cycle of its own, counted from when it goes on, for drains alone`() {
        alice.skills.setCurrentLevel(Skill.STRENGTH, 80)
        alice.skills.setCurrentLevel(Skill.RANGED, 95)
        run(30)
        prayersOn += Prayers.RAPID_RESTORE
        run(70)
        assertEquals(81, level(Skill.STRENGTH), "the usual cycle, at tick 100")
        assertEquals(94, level(Skill.RANGED))
        run(29)
        assertEquals(81, level(Skill.STRENGTH))
        run(1)
        assertEquals(82, level(Skill.STRENGTH), "Rapid Restore's, 100 ticks after it went on")
        assertEquals(94, level(Skill.RANGED), "boosts aren't touched")
        prayersOn.clear()
        run(100)
        assertEquals(83, level(Skill.STRENGTH), "off, only the usual cycle")
    }

    @Test
    fun `Preserve on for 15 seconds makes a boost cycle a minute and a half`() {
        prayersOn += Prayers.PRESERVE
        alice.skills.setCurrentLevel(Skill.MAGIC, 99)
        alice.skills.setCurrentLevel(Skill.AGILITY, 95)
        alice.skills.setCurrentLevel(Skill.DEFENCE, 85)
        run(100)
        assertEquals(99, level(Skill.MAGIC))
        assertEquals(95, level(Skill.AGILITY))
        assertEquals(86, level(Skill.DEFENCE), "drains still come back each minute")
        run(50)
        assertEquals(98, level(Skill.MAGIC))
        assertEquals(94, level(Skill.AGILITY))
    }

    @Test
    fun `Preserve turned on too late in a cycle doesn't count for it, and off it ends a lengthened cycle at once`() {
        alice.skills.setCurrentLevel(Skill.ATTACK, 99)
        run(76)
        prayersOn += Prayers.PRESERVE
        run(24)
        assertEquals(98, level(Skill.ATTACK), "on for 24 ticks when the cycle ended: not yet")
        run(76)
        run(24)
        assertEquals(98, level(Skill.ATTACK), "on for 100 ticks: the cycle runs on")
        run(20)
        prayersOn.clear()
        run(1)
        assertEquals(97, level(Skill.ATTACK), "121 ticks in, off: past a minute, so the level goes")
    }

    @Test
    fun `Preserve turned off in a lengthened cycle's last 12 seconds keeps the cycle long`() {
        prayersOn += Prayers.PRESERVE
        alice.skills.setCurrentLevel(Skill.ATTACK, 99)
        run(130)
        prayersOn.clear()
        run(19)
        assertEquals(99, level(Skill.ATTACK), "turned off with 20 ticks to go")
        run(1)
        assertEquals(98, level(Skill.ATTACK))
        run(99)
        assertEquals(98, level(Skill.ATTACK), "the next cycle is a minute again")
        run(1)
        assertEquals(97, level(Skill.ATTACK))
    }

    @Test
    fun `a boost cycle's segment is kept over a logout, and starts again at login`() {
        alice.skills.setCurrentLevel(Skill.ATTACK, 99)
        run(59)
        val save = StatRestore.segment(alice.statTimers.combatBoost)
        assertEquals(2, save)
        StatRestore.resume(alice.statTimers.combatBoost, save)
        run(60)
        assertEquals(99, level(Skill.ATTACK), "19 ticks into the segment at logout; the login tick and 60 after it")
        run(1)
        assertEquals(98, level(Skill.ATTACK))
        alice.statTimers.combatBoost.ticks = 140
        assertEquals(4, StatRestore.segment(alice.statTimers.combatBoost), "past a minute with Preserve: the last segment")
    }

    @Test
    fun `nothing changes while the player is dead`() {
        alice.skills.setCurrentLevel(Skill.ATTACK, 99)
        alice.deathTick = 0
        run(300)
        assertEquals(99, level(Skill.ATTACK))
    }
}
