package osrsps.game.script

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class HandlersTest {
    private val handlers = Handlers()

    @Test
    fun `handlers are found by target, id and option`() {
        val open: Handler = {}
        val close: Handler = {}
        handlers.onLoc(1530, 1, open)
        handlers.onLoc(1530, 2, close)
        assertSame(open, handlers.find(Op(OpTarget.LOC, 1530, 1)))
        assertSame(close, handlers.find(Op(OpTarget.LOC, 1530, 2)))
        assertNull(handlers.find(Op(OpTarget.LOC, 1530, 3)))
        assertNull(handlers.find(Op(OpTarget.NPC, 1530, 1)))
    }

    @Test
    fun `an any-option handler catches options without their own`() {
        val any: Handler = {}
        val first: Handler = {}
        handlers.onButton(149, 0, handler = any)
        handlers.onButton(149, 0, 1, first)
        assertSame(first, handlers.find(Op.button(149, 0, 1)))
        assertSame(any, handlers.find(Op.button(149, 0, 7, sub = 3)))
        assertNull(handlers.find(Op.button(149, 1, 1)))
    }

    @Test
    fun `button ids pack the interface and component`() {
        val op = Op.button(160, 29, 1)
        assertEquals(160, op.interfaceId)
        assertEquals(29, op.componentId)
        assertEquals("button 160:29 op 1 sub -1", op.toString())
    }

    @Test
    fun `a second handler for the same click, or a bad option, is refused`() {
        handlers.onNpc(3077, 1) {}
        assertFailsWith<IllegalArgumentException> { handlers.onNpc(3077, 1) {} }
        assertFailsWith<IllegalArgumentException> { handlers.onObj(1351, 6) {} }
        assertFailsWith<IllegalArgumentException> { handlers.onButton(149, 0, 11) {} }
        handlers.onButton(149, 0, 10) {}
    }
}
