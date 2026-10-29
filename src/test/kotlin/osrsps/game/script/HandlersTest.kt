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

    @Test
    fun `items used on NPCs are found by NPC and item, with a catch-all for the NPC`() {
        val bucket: Handler = {}
        val any: Handler = {}
        handlers.onObjOnNpc(3105, 1925, bucket)
        handlers.onObjOnNpc(3105, handler = any)
        assertSame(bucket, handlers.find(Op.objOnNpc(3105, 1925, 0)))
        // Obj 0 is a real item, not "any".
        assertSame(any, handlers.find(Op.objOnNpc(3105, 0, 0)))
        assertNull(handlers.find(Op.objOnNpc(3106, 1925, 0)))
        assertFailsWith<IllegalArgumentException> { handlers.register(OpTarget.OBJ_ON_NPC, 3105, 1) {} }
    }

    @Test
    fun `items used on locs are found by loc and item, with a catch-all for the loc`() {
        val grain: Handler = {}
        val any: Handler = {}
        handlers.onObjOnLoc(24961, 1947, grain)
        handlers.onObjOnLoc(24961, handler = any)
        assertSame(grain, handlers.find(Op.objOnLoc(24961, 1947, 0)))
        assertSame(any, handlers.find(Op.objOnLoc(24961, 0, 0)))
        assertNull(handlers.find(Op.objOnLoc(24962, 1947, 0)))
        // Keyed apart from items used on NPCs of the same id.
        assertNull(handlers.find(Op.objOnNpc(24961, 1947, 0)))
        assertFailsWith<IllegalArgumentException> { handlers.onObjOnLoc(24961, 1947) {} }
        assertFailsWith<IllegalArgumentException> { handlers.register(OpTarget.OBJ_ON_LOC, 24961, 1) {} }
    }
}
