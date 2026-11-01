package osrsps.game.raid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn

class PartyScalingTest {
    // The cache's two Archive Guardians (rev 241 NPC configs): flying, and grounded once its shield is down.
    private val flying = NpcType(16659, "Archive Guardian", size = 5, internalName = "tfa_guardian", stats = NpcStats(hitpoints = 550))
    private val grounded = NpcType(16660, "Archive Guardian", size = 5, internalName = "tfa_guardian_unshielded", stats = NpcStats(hitpoints = 1100))
    private val man = NpcType(3106, "Man", internalName = "man", stats = NpcStats(hitpoints = 7))
    private val scaling = PartyScaling.load(NpcTypes.of(flying, grounded, man))

    private fun npc(type: NpcType) = Npc(1, NpcSpawn(type, 3222, 3218, source = "test"))

    @Test
    fun `the Archive Guardian's hitpoints at 1, 2, 3 and 8 players are the Wiki's`() {
        // OSRS Wiki, Archive Guardian: flying 550 / 550 / 800 / 1,950 and grounded 1,100 / 1,100 / 1,600 / 3,910.
        assertEquals(listOf(550, 550, 800, 1950), listOf(1, 2, 3, 8).map { scaling.hitpoints(flying, it) })
        assertEquals(listOf(1100, 1100, 1600, 3910), listOf(1, 2, 3, 8).map { scaling.hitpoints(grounded, it) })
        // And the sizes between.
        assertEquals(listOf(1030, 1260, 1490, 1720), (4..7).map { scaling.hitpoints(flying, it) })
        assertEquals(listOf(2060, 2520, 2980, 3440), (4..7).map { scaling.hitpoints(grounded, it) })
    }

    @Test
    fun `a party's hitpoints for two match the cache's own, and a player alone counts as two`() {
        assertEquals(flying.stats.hitpoints, scaling.hitpoints(flying, 2))
        assertEquals(grounded.stats.hitpoints, scaling.hitpoints(grounded, 2))
        assertEquals(listOf(2, 2, 3, 8), listOf(1, 2, 3, 8).map(PartyScaling::counted))
        assertFailsWith<IllegalArgumentException> { PartyScaling.counted(0) }
        assertFailsWith<IllegalArgumentException> { PartyScaling.counted(9) }
    }

    @Test
    fun `scaling gives an NPC its party's hitpoints, full, and leaves one that doesn't scale alone`() {
        val guardian = npc(flying)
        assertEquals(1720, scaling.scale(guardian, 7))
        assertEquals(1720, guardian.hitpoints)
        assertEquals(1720, guardian.maxHitpoints)
        assertEquals(7, guardian.scaledFor)

        val solo = npc(grounded)
        assertEquals(1100, scaling.scale(solo, 1))
        assertEquals(2, solo.scaledFor)

        val bystander = npc(man)
        assertNull(scaling.scale(bystander, 8))
        assertEquals(7, bystander.maxHitpoints)
        assertEquals(0, bystander.scaledFor)
        assertTrue(man !in scaling)
    }

    @Test
    fun `a scaling file needs eight rising hitpoints and a source, and skips NPCs the cache doesn't have`() {
        val types = NpcTypes.of(flying)
        fun parse(entry: String) = PartyScaling.parse("""{ "npcs": [ $entry ] }""", types, "test.json")
        val short = assertFailsWith<IllegalArgumentException> { parse("""{ "npcs": ["tfa_guardian"], "hitpoints": [1, 2], "source": "x" }""") }
        assertEquals("test.json: entry 1: 2 hitpoints, not one for each party size 1 to 8", short.message)
        val falling = assertFailsWith<IllegalArgumentException> { parse("""{ "npcs": ["tfa_guardian"], "hitpoints": [8, 7, 6, 5, 4, 3, 2, 1], "source": "x" }""") }
        assertEquals("test.json: entry 1: hitpoints fall as the party grows", falling.message)
        val unsourced = assertFailsWith<IllegalArgumentException> { parse("""{ "npcs": ["tfa_guardian"], "hitpoints": [1, 2, 3, 4, 5, 6, 7, 8] }""") }
        assertEquals("test.json: entry 1: no \"source\"", unsourced.message)
        assertEquals(0, parse("""{ "npcs": ["tfa_moia"], "hitpoints": [1, 2, 3, 4, 5, 6, 7, 8], "source": "x" }""").size)
    }
}
