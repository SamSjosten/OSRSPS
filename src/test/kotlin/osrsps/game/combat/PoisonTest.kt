package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.game.InvObj
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.dialogue.DialogueText

/** Poison and venom in the poison varp, their hits, the immunity wearing off, and the antipoisons and sanfew curing them. */
class PoisonTest {
    private val hits = mutableListOf<Pair<Int, Int>>()
    private val messages = mutableListOf<String>()
    private val poison = Poison(
        hit = { _, damage, hitsplat -> hits += damage to hitsplat },
        words = { DialogueText.NONE },
        message = { _, text -> messages += text },
    )

    private val alice = Player(1, "alice", 3222, 3218, 0).apply {
        for (skill in Skill.entries) skills.setLevel(skill, 99)
    }

    private fun varp() = alice.vars[Poison.VARP]

    /** Runs [ticks] of [Poison.process]. */
    private fun run(ticks: Int) = repeat(ticks) { poison.process(alice) }

    private fun dose(id: Int, internal: String, name: String) = ObjType(id, name, iops = listOf("Drink", null, null, null, "Drop"), internalName = internal)

    /** Four doses named by [name] with `{n}` for the count, at ids [first] to [first] + 3. */
    private fun doses(first: Int, internal: String, name: String) =
        (4 downTo 1).mapIndexed { i, n -> dose(first + i, internal.replace("{n}", "$n"), "$name($n)") }

    private val antipoison = doses(200, "{n}doseantipoison", "Antipoison")
    private val superantipoison = doses(210, "{n}dose2antipoison", "Superantipoison")
    private val antidote = doses(220, "antidote++{n}", "Antidote++")
    private val antivenom = doses(230, "antivenom{n}", "Anti-venom")
    private val antivenomPlus = doses(240, "antivenom+{n}", "Anti-venom+")
    private val sanfew = doses(250, "sanfew_salve_{n}_dose", "Sanfew serum")
    private val objTypes = ObjTypes.of(
        *(antipoison + superantipoison + antidote + antivenom + antivenomPlus + sanfew).toTypedArray(),
        ObjType(229, "Vial", internalName = "vial_empty"),
    )
    private val potions = Potions(Potions.load(objTypes), message = { _, _ -> }, poison = poison)

    private fun drink(potion: List<ObjType>, tick: Long = 0) {
        alice.potionDelayUntil = 0
        alice.inventory[0] = InvObj(potion[0].id, 1)
        assertTrue(potions.drink(alice, 0, potion[0].id, tick))
    }

    @Test
    fun `poison of 20 hits 4 five times every 30 ticks, then 3, going down by one a hit`() {
        assertTrue(poison.poison(alice, 20))
        assertEquals(20, varp())
        assertEquals(listOf("[poison.poisoned]"), messages, "with no words, the key")
        run(1)
        assertEquals(Poison.TICKS, alice.poisonTicks, "the timer starts on the first tick poisoned")
        run(Poison.TICKS - 1)
        assertTrue(hits.isEmpty(), "the first hit comes a full 30 ticks after")
        run(1)
        assertEquals(listOf(4 to Poison.POISON_HITSPLAT), hits)
        assertEquals(19, varp())
        run(Poison.TICKS * 4)
        run(Poison.TICKS)
        assertEquals(listOf(4, 4, 4, 4, 4, 3), hits.map { it.first })
        assertEquals(14, varp())
    }

    @Test
    fun `poison runs out at 0, and the timer stops`() {
        poison.poison(alice, 2)
        run(1 + Poison.TICKS * 2)
        assertEquals(listOf(1, 1), hits.map { it.first })
        assertEquals(0, varp())
        run(1)
        assertEquals(-1, alice.poisonTicks)
    }

    @Test
    fun `a weaker poison does nothing, a stronger one replaces it, and neither says anything a second time`() {
        poison.poison(alice, 25)
        assertFalse(poison.poison(alice, 20))
        assertEquals(25, varp())
        assertTrue(poison.poison(alice, 30))
        assertEquals(30, varp())
        assertEquals(1, messages.size)
    }

    @Test
    fun `venom hits 6 and 2 more each time up to 20, and poison never turns it into poison`() {
        assertTrue(poison.envenom(alice))
        assertEquals(Poison.VENOM, varp())
        assertFalse(poison.poison(alice, 30))
        run(1 + Poison.TICKS * 10)
        assertEquals(listOf(6, 8, 10, 12, 14, 16, 18, 20, 20, 20), hits.map { it.first })
        assertTrue(hits.all { it.second == Poison.VENOM_HITSPLAT })
        assertEquals(Poison.VENOM + 10, varp(), "the varp counts on past the cap")
    }

    @Test
    fun `venom replaces poison`() {
        poison.poison(alice, 20)
        assertTrue(poison.envenom(alice))
        assertEquals(Poison.VENOM, varp())
        assertEquals(listOf("[poison.poisoned]"), messages, "already poisoned, so nothing more is said")
    }

    @Test
    fun `an antipoison cures poison and leaves 5 periods of immunity that wear off a period at a time`() {
        poison.poison(alice, 20)
        run(1)
        drink(antipoison)
        assertEquals(-5, varp())
        assertFalse(poison.poison(alice, 30), "immune")
        run(Poison.TICKS)
        assertEquals(-4, varp())
        run(Poison.TICKS * 4)
        assertEquals(0, varp())
        assertTrue(hits.isEmpty())
        assertTrue(poison.poison(alice, 20))
    }

    @Test
    fun `an antipoison's immunity doesn't shorten a longer one`() {
        drink(superantipoison)
        assertEquals(-20, varp())
        drink(antipoison)
        assertEquals(-20, varp())
    }

    @Test
    fun `poison immunity alone doesn't keep venom off`() {
        drink(superantipoison)
        assertTrue(poison.envenom(alice))
        assertEquals(Poison.VENOM, varp())
    }

    @Test
    fun `an antipoison while envenomed turns the venom into poison at the same damage, and a second dose cures it`() {
        poison.envenom(alice)
        run(1 + Poison.TICKS * 2)
        assertEquals(listOf(6, 8), hits.map { it.first })
        drink(antipoison)
        // The next venom hit would have been 10; poison of 50 hits 10.
        assertEquals(50, varp())
        run(Poison.TICKS)
        assertEquals(10 to Poison.POISON_HITSPLAT, hits.last())
        drink(antipoison)
        assertEquals(-5, varp())
    }

    @Test
    fun `antidote++ gives 40 periods, past the venom cut-off, so it keeps venom off for a period or two`() {
        drink(antidote)
        assertEquals(-40, varp())
        assertFalse(poison.envenom(alice))
        run(1 + Poison.TICKS * 2)
        assertEquals(-38, varp())
        assertTrue(poison.envenom(alice), "at -38 venom gets through")
    }

    @Test
    fun `anti-venom cures venom outright and keeps it off`() {
        poison.envenom(alice)
        drink(antivenom)
        assertEquals(-41, varp())
        assertFalse(poison.envenom(alice))
        drink(antivenomPlus)
        assertEquals(-50, varp())
    }

    @Test
    fun `sanfew serum restores like a restore and cures poison for 6 minutes`() {
        alice.skills.setCurrentLevel(Skill.ATTACK, 50)
        alice.skills.setCurrentLevel(Skill.PRAYER, 10)
        poison.poison(alice, 20)
        drink(sanfew)
        // 99 * 30% + 4 = 33.
        assertEquals(83, alice.skills.currentLevel(Skill.ATTACK))
        assertEquals(43, alice.skills.currentLevel(Skill.PRAYER))
        assertEquals(-20, varp())
        assertEquals(sanfew[1].id, alice.inventory[0]?.id)
    }

    @Test
    fun `the timer waits while an interface is open, and the hit comes as soon as it closes`() {
        poison.poison(alice, 20)
        run(Poison.TICKS)
        alice.chatModal = 1
        run(5)
        assertTrue(hits.isEmpty())
        alice.chatModal = -1
        run(1)
        assertEquals(listOf(4), hits.map { it.first })
    }

    @Test
    fun `nothing counts while dead, and clearing ends poison and immunity alike`() {
        poison.poison(alice, 20)
        run(1)
        alice.deathTick = 0
        run(Poison.TICKS * 2)
        assertTrue(hits.isEmpty())
        poison.clear(alice)
        assertEquals(0, varp())
        assertEquals(-1, alice.poisonTicks)
    }

    @Test
    fun `describe says what the varp means`() {
        assertEquals("Not poisoned or envenomed (poison varp 0).", poison.describe(alice))
        poison.poison(alice, 20)
        run(1)
        assertEquals("Poisoned, hitting 4, the next in 30 ticks (poison varp 20).", poison.describe(alice))
        alice.vars[Poison.VARP] = -41
        assertEquals("Immune to poison and venom for 41 more periods, the next in 30 ticks (poison varp -41).", poison.describe(alice))
    }
}
