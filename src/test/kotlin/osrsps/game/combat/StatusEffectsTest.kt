package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.game.Player

/** Stuns: how long they last, the birds, stopping what the player was doing, the optional hit, and a second stun. */
class StatusEffectsTest {
    private var tick = 100L
    private val stopped = mutableListOf<Player>()
    private val hits = mutableListOf<Int>()
    private val effects = StatusEffects(currentTick = { tick }, stop = { stopped += it }, hit = { _, _, damage -> hits += damage })

    private val alice = Player(1, "alice", 3222, 3218, 0)

    @Test
    fun `a stun lasts its ticks from the tick it lands, then ends`() {
        assertTrue(effects.stun(alice, StatusEffects.PICKPOCKET_TICKS))
        assertEquals(109, alice.stunnedUntil)
        assertTrue(effects.isStunned(alice, 100))
        assertTrue(effects.isStunned(alice, 108))
        assertFalse(effects.isStunned(alice, 109))
        assertEquals("Stunned for 9 more ticks.", effects.describe(alice))
        tick = 108
        assertEquals("Stunned for 1 more tick.", effects.describe(alice))
        tick = 109
        assertEquals("Not stunned.", effects.describe(alice))
    }

    @Test
    fun `a stun stops what the player was doing and shows the birds`() {
        effects.stun(alice, 5)
        assertEquals(listOf(alice), stopped)
        val birds = alice.effects.pendingSpotanims.values.single()
        assertEquals(StatusEffects.STUN_SPOTANIM, birds.id)
        assertEquals(StatusEffects.STUN_HEIGHT, birds.height)
        assertTrue(hits.isEmpty(), "no damage asked for")
    }

    @Test
    fun `a stun can bring a hit and its own spotanim`() {
        effects.stun(alice, StatusEffects.PICKPOCKET_TICKS, damage = 1, spotanim = StatusEffects.THIEVING_SPOTANIM)
        assertEquals(listOf(1), hits)
        assertEquals(StatusEffects.THIEVING_SPOTANIM, alice.effects.pendingSpotanims.values.single().id)
    }

    @Test
    fun `a second stun while stunned does nothing, and one after it ends lands`() {
        effects.stun(alice, 5)
        tick = 104
        assertFalse(effects.stun(alice, 9, damage = 2))
        assertEquals(105, alice.stunnedUntil, "not lengthened")
        assertTrue(hits.isEmpty())
        tick = 105
        assertTrue(effects.stun(alice, 3))
        assertEquals(108, alice.stunnedUntil)
    }

    @Test
    fun `clearing ends a stun`() {
        effects.stun(alice, 9)
        effects.clear(alice)
        assertFalse(effects.isStunned(alice))
    }
}
