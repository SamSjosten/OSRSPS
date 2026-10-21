package osrsps.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlayerEffectsTest {
    private val player = Player(1, "test", 3222, 3218, 0)
    private val sent = mutableListOf<String>()

    private fun flush() = player.effects.flush(
        anim = { sent += "anim ${it.id} ${it.delay}" },
        spotanim = { slot, spot -> sent += "spotanim $slot ${spot.id} ${spot.height} ${spot.delay}" },
        sound = { sent += "sound ${it.id} ${it.loops} ${it.delay}" },
        jingle = { sent += "jingle $it" },
    )

    @Test
    fun `sends what was set once, then nothing`() {
        player.anim(866)
        player.spotanim(85, height = 92, delay = 10)
        player.sound(3813)
        player.jingle(2)
        flush()
        assertEquals(listOf("anim 866 0", "spotanim 0 85 92 10", "sound 3813 1 0", "jingle 2"), sent)
        sent.clear()
        flush()
        assertEquals(emptyList(), sent)
    }

    @Test
    fun `the last animation and jingle of a cycle win, every sound plays`() {
        player.anim(866)
        player.anim(855, delay = 5)
        player.jingle(1)
        player.jingle(2)
        player.sound(3813)
        player.sound(200, loops = 2)
        flush()
        assertEquals(listOf("anim 855 5", "sound 3813 1 0", "sound 200 2 0", "jingle 2"), sent)
    }

    @Test
    fun `spotanims keep one per slot, sent in slot order`() {
        player.spotanim(86, slot = 3)
        player.spotanim(85)
        player.spotanim(111, slot = 3)
        flush()
        assertEquals(listOf("spotanim 0 85 0 0", "spotanim 3 111 0 0"), sent)
    }

    @Test
    fun `-1 stops an animation and clears a spotanim slot`() {
        player.anim(-1)
        player.spotanim(-1, slot = 2)
        flush()
        assertEquals(listOf("anim -1 0", "spotanim 2 -1 0 0"), sent)
    }

    @Test
    fun `refuses values the client can't take, before anything is sent`() {
        assertFailsWith<IllegalArgumentException> { player.anim(65536) }
        assertFailsWith<IllegalArgumentException> { player.anim(866, delay = -1) }
        assertFailsWith<IllegalArgumentException> { player.anim(866, delay = 256) }
        assertFailsWith<IllegalArgumentException> { player.spotanim(85, slot = 256) }
        assertFailsWith<IllegalArgumentException> { player.spotanim(85, height = 65536) }
        assertFailsWith<IllegalArgumentException> { player.sound(-1) }
        assertFailsWith<IllegalArgumentException> { player.sound(3813, loops = 256) }
        assertFailsWith<IllegalArgumentException> { player.jingle(-1) }
        flush()
        assertEquals(emptyList(), sent)
    }
}
