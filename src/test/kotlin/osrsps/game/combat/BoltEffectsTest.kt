package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.Npcs
import osrsps.map.CollisionMap

/** The enchanted bolts' effects on monsters, against the OSRS Wiki's (e) bolt pages (read 2026-11-10). */
class BoltEffectsTest {
    private val npcs = Npcs(CollisionMap().apply { markLoaded(50, 50) }, GameRandom(1))

    private fun npc(hitpoints: Int = 10_000, ranged: Int = 1): Npc =
        npcs.add(NpcSpawn(NpcType(2668, "Dummy", internalName = "dummy", stats = NpcStats(hitpoints = hitpoints, ranged = ranged)), 3215, 3210, wander = 0, source = "test"))!!

    private val player = Player(1, "alice", 3210, 3210, 0).apply { skills.setLevel(Skill.RANGED, 99) }

    /** How often [effect] fires in 20,000 shots, hit or missed. */
    private fun rate(effect: BoltEffect, hit: Boolean): Double {
        val random = GameRandom(3)
        val target = npc()
        return (1..20_000).count { BoltEffects.roll(effect, random, player, target, hit, 30) { 0 }.fired } / 20_000.0
    }

    @Test
    fun `each effect fires at the Wiki's chance, and the on-hit ones never on a miss`() {
        val chances = mapOf(
            BoltEffect.OPAL to 0.05, BoltEffect.PEARL to 0.06, BoltEffect.SAPPHIRE to 0.25, BoltEffect.EMERALD to 0.55,
            BoltEffect.RUBY to 0.06, BoltEffect.DIAMOND to 0.10, BoltEffect.DRAGONSTONE to 0.06, BoltEffect.ONYX to 0.11,
        )
        for ((effect, chance) in chances) {
            val rate = rate(effect, hit = true)
            assertTrue(kotlin.math.abs(rate - chance) < 0.015, "$effect fired ${rate * 100}% of hits, the Wiki gives ${chance * 100}%")
        }
        for (effect in listOf(BoltEffect.SAPPHIRE, BoltEffect.EMERALD, BoltEffect.DRAGONSTONE, BoltEffect.ONYX)) {
            assertEquals(0.0, rate(effect, hit = false), "$effect needs a hit")
        }
        for (effect in listOf(BoltEffect.OPAL, BoltEffect.PEARL, BoltEffect.RUBY, BoltEffect.DIAMOND)) {
            assertTrue(rate(effect, hit = false) > 0.03, "$effect fires on a miss too")
        }
    }

    @Test
    fun `topaz and jade do nothing to a monster and take no draw`() {
        for (effect in listOf(BoltEffect.TOPAZ, BoltEffect.JADE)) {
            assertFalse(BoltEffects.rolls(effect))
            val random = GameRandom(3)
            val next = GameRandom(3).nextInt(1000)
            assertEquals(12, BoltEffects.roll(effect, random, player, npc(), true, 30) { 12 }.damage)
            assertEquals(next, random.nextInt(1000), "no draw taken")
        }
    }

    /** The first roll of [effect] that fires, from seed 3 on. */
    private fun fired(effect: BoltEffect, target: Npc, hit: Boolean, roll: (Int) -> Int): BoltEffects.Rolled {
        val random = GameRandom(3)
        repeat(10_000) { BoltEffects.roll(effect, random, player, target, hit, 30, roll).takeIf { it.fired }?.let { return it } }
        error("$effect never fired")
    }

    @Test
    fun `what each one does to the hit`() {
        // Opal: 99 Ranged / 10 more, landing even on a miss; pearl / 20; dragonstone / 5.
        assertEquals(BoltEffects.Rolled(true, 9, true, 30 + 9), fired(BoltEffect.OPAL, npc(), hit = false) { it })
        assertEquals(BoltEffects.Rolled(true, 10 + 4, true, 30 + 4), fired(BoltEffect.PEARL, npc(), hit = true) { 10 })
        assertEquals(BoltEffects.Rolled(true, 10 + 19, true, 30 + 19), fired(BoltEffect.DRAGONSTONE, npc(), hit = true) { 10 })
        // Onyx: a fifth more. Diamond: a sure hit on a max hit 15% higher (30 to 34).
        assertEquals(24, fired(BoltEffect.ONYX, npc(), hit = true) { 20 }.damage)
        // The max hit for the hitsplat follows the effect: onyx's fifth more of 30 is 36.
        assertEquals(36, fired(BoltEffect.ONYX, npc(), hit = true) { 20 }.max)
        assertEquals(BoltEffects.Rolled(true, 34, true, 34), fired(BoltEffect.DIAMOND, npc(), hit = false) { it })
        // Ruby: a fifth of the monster's hitpoints now, at most 100.
        assertEquals(40, fired(BoltEffect.RUBY, npc(hitpoints = 200), hit = false) { 0 }.damage)
        assertEquals(100, fired(BoltEffect.RUBY, npc(hitpoints = 10_000), hit = true) { 0 }.damage)
    }

    @Test
    fun `ruby doesn't fire for a shooter who can't spare a hitpoint`() {
        val low = Player(2, "bob", 3210, 3210, 0).apply { skills.setLevel(Skill.RANGED, 99); hitpoints = 9 }
        val random = GameRandom(3)
        val target = npc()
        assertEquals(0, (1..5_000).count { BoltEffects.roll(BoltEffect.RUBY, random, low, target, true, 30) { 0 }.fired })
        low.hitpoints = 10
        assertTrue((1..5_000).count { BoltEffects.roll(BoltEffect.RUBY, random, low, target, true, 30) { 0 }.fired } > 0)
    }

    @Test
    fun `sapphire gives an eighth of a tenth of the monster's Ranged as prayer, and onyx heals a quarter of the damage`() {
        player.skills.setLevel(Skill.PRAYER, 70)
        player.skills.setCurrentLevel(Skill.PRAYER, 50)
        BoltEffects.landed(BoltEffect.SAPPHIRE, player, npc(ranged = 300), 10)
        assertEquals(53, player.skills.currentLevel(Skill.PRAYER))
        player.skills.setLevel(Skill.HITPOINTS, 99)
        player.hitpoints = 50
        BoltEffects.landed(BoltEffect.ONYX, player, npc(), 30)
        assertEquals(57, player.hitpoints)
        assertEquals(5, BoltEffects.rubyCost(player))
    }
}
