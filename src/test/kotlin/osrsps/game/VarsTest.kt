package osrsps.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VarsTest {
    private val types = VarTypes(
        200,
        mapOf(
            1 to VarbitType(10, 4, 6), // 3 bits
            2 to VarbitType(10, 0, 3),
            3 to VarbitType(11, 0, 31),
            4 to VarbitType(12, 31, 31),
        ),
    )
    private val vars = PlayerVars(types)

    private fun flushed(): List<Pair<Int, Int>> = mutableListOf<Pair<Int, Int>>().also { out -> vars.flush { id, v -> out.add(id to v) } }

    @Test
    fun `varbits are slices of their varp and leave the other bits alone`() {
        vars[10] = -1
        vars.setVarbit(1, 2)
        assertEquals(2, vars.getVarbit(1))
        assertEquals(15, vars.getVarbit(2))
        assertEquals(-1 and (0b111 shl 4).inv() or (2 shl 4), vars[10])
    }

    @Test
    fun `full-width and top-bit varbits`() {
        vars.setVarbit(3, -5)
        assertEquals(-5, vars[11])
        assertEquals(-5, vars.getVarbit(3))
        vars.setVarbit(4, 1)
        assertEquals(Int.MIN_VALUE, vars[12])
        assertEquals(1, vars.getVarbit(4))
        assertEquals(1, types.varbit(4)!!.mask)
        assertEquals(-1, types.varbit(3)!!.mask)
    }

    @Test
    fun `refuses values and ids out of range`() {
        assertFailsWith<IllegalArgumentException> { vars.setVarbit(1, 8) }
        assertFailsWith<IllegalArgumentException> { vars.setVarbit(1, -1) }
        assertFailsWith<IllegalArgumentException> { vars.setVarbit(99, 0) }
        assertFailsWith<IllegalArgumentException> { vars[200] = 1 }
        assertFailsWith<IllegalArgumentException> { vars[-1] }
        assertEquals(emptyList(), flushed())
    }

    @Test
    fun `flush sends each changed varp once with its latest value`() {
        vars[5] = 1
        vars.setVarbit(2, 3)
        vars.setVarbit(1, 1)
        vars[5] = 7
        vars[6] = 0 // unchanged from 0, so not sent
        assertEquals(listOf(5 to 7, 10 to (3 or (1 shl 4))), flushed())
        assertEquals(emptyList(), flushed())
    }

    @Test
    fun `the run orb is varp 173`() {
        val player = Player(1, "test", 3222, 3218, 0)
        assertFalse(player.runEnabled)
        player.vars[World.VARP_RUN] = 1
        assertTrue(player.runEnabled)
        player.runEnabled = false
        assertEquals(0, player.vars[World.VARP_RUN])
    }
}
