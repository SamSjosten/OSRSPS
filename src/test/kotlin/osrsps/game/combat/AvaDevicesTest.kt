package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.InvObj
import osrsps.game.Player
import osrsps.game.WearPos

/** Draynor step 12c: which Ava's device is worn and whether the torso stops it (OSRS Wiki, Ava's device). */
class AvaDevicesTest {
    private val player = Player(1, "alice", 3093, 3357, 0)

    private fun wear(cape: Int?, body: Int? = null): Int {
        player.equipment[WearPos.CAPE] = cape?.let { InvObj(it, 1) }
        player.equipment[WearPos.BODY] = body?.let { InvObj(it, 1) }
        return AvaDevices.recovery(player)
    }

    @Test
    fun `each device sends back the Wiki's share, and nothing else in the cape slot does`() {
        assertEquals(60, wear(AvaDevices.ATTRACTOR))
        assertEquals(72, wear(AvaDevices.ACCUMULATOR))
        assertEquals(80, wear(AvaDevices.ASSEMBLER))
        assertEquals(0, wear(null))
        assertEquals(0, wear(1007)) // a red cape
    }

    @Test
    fun `metallic torsos stop it, and the Wiki's compatible ones don't`() {
        assertEquals(0, wear(AvaDevices.ACCUMULATOR, 1127)) // rune platebody
        assertEquals(0, wear(AvaDevices.ACCUMULATOR, 4720)) // Dharok's platebody
        assertEquals(0, wear(AvaDevices.ATTRACTOR, 4069)) // decorative armour
        assertEquals(72, wear(AvaDevices.ACCUMULATOR, 1117)) // bronze platebody: compatible
        assertEquals(72, wear(AvaDevices.ACCUMULATOR, 1135)) // green d'hide body
        assertEquals(72, wear(AvaDevices.ACCUMULATOR, 1105)) // steel chainbody: not on the list
    }

    @Test
    fun `none breaks less than a fifth, and none sends more than the rest back`() {
        assertEquals(20, AvaDevices.BROKEN)
        assertEquals(true, AvaDevices.RECOVERED.values.all { it <= 100 - AvaDevices.BROKEN })
    }
}
