package osrsps.cache

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import osrsps.cache.InterfaceCodec.Component
import osrsps.cache.overlay.Js5Group

class InterfaceCodecTest {
    private fun roundtrip(component: Component) = assertEquals(component, InterfaceCodec.decode(component.id, InterfaceCodec.encode(component)))

    @Test
    fun `a bare layer is laid out as the client reads if3`() {
        assertContentEquals(
            byteArrayOf(
                -1, 0, // if3, a layer
                0, 0, // content type
                0, 0, 0, 0, 0, 0, 0, 0, // x, y, width, height
                0, 0, 0, 0, // width, height, x and y modes
                -1, -1, // no parent
                0, // shown
                0, 0, 0, 0, 0, // scroll width and height, click-through
                0, 0, 0, // the dead click mask
                0, // no name
                0, // no ops
                0, 0, 0, // drag
                0, // no target verb
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // 18 listeners
                0, 0, 0, // 3 triggers
            ),
            InterfaceCodec.encode(Component(0, InterfaceCodec.LAYER)),
        )
    }

    @Test
    fun `each kind we write comes back as it went in`() {
        roundtrip(
            Component(
                0, InterfaceCodec.LAYER, x = 10, y = -5, width = 400, height = 300, xMode = InterfaceCodec.CENTRE, yMode = InterfaceCodec.CENTRE,
                scrollWidth = 10, scrollHeight = 20, noClickThrough = true, ops = listOf("Teleport", "Examine"), name = "<col=ff9040>Lumbridge</col>",
                targetVerb = "Use", clickMask = 0x123456, contentType = 1337,
            ),
        )
        roundtrip(Component(1, InterfaceCodec.RECT, parent = 0, width = 16, height = 16, widthMode = InterfaceCodec.OPPOSITE, colour = 0x3E3529, filled = true, opacity = 40))
        roundtrip(
            Component(
                2, InterfaceCodec.TEXT, parent = 0, width = 100, height = 20, font = 497, text = "Lodestone Network", lineHeight = 14,
                xAlign = InterfaceCodec.CENTRE, yAlign = InterfaceCodec.BOTTOM, shadowed = true, colour = 0xFF981F,
            ),
        )
        roundtrip(
            Component(
                3, InterfaceCodec.GRAPHIC, parent = 0, width = 26, height = 23, sprite = 65000, texture = 7, tiled = true, opacity = 128,
                border = 2, shadow = 0x333333, flippedVertically = true, flippedHorizontally = true, hidden = true,
            ),
        )
        roundtrip(Component(4, InterfaceCodec.LINE, parent = 0, width = 50, height = -20, lineWidth = 2, colour = 0xFFFFFF, lineDown = true))
        roundtrip(Component(5, InterfaceCodec.GRAPHIC, sprite = InterfaceCodec.NO_SPRITE))
        roundtrip(Component(6, InterfaceCodec.TEXT, font = InterfaceCodec.NO_FONT))
    }

    @Test
    fun `an interface is its components as the files of one group`() {
        val components = listOf(
            Component(0, InterfaceCodec.LAYER, width = 100, height = 100),
            Component(1, InterfaceCodec.RECT, parent = 0, width = 100, height = 100, colour = 1, filled = true),
            Component(2, InterfaceCodec.TEXT, parent = 0, text = "x", font = 495),
        )
        val group = InterfaceCodec.encodeGroup(components)
        assertEquals(components, InterfaceCodec.decodeGroup(group, components.size))
        val files = Js5Group.unpack(group, listOf(0, 1, 2))
        for (component in components) assertContentEquals(InterfaceCodec.encode(component), files[component.id], "file ${component.id}")
    }

    @Test
    fun `refuses an interface with a gap in its ids or a parent it doesn't hold`() {
        val gap = listOf(Component(0, InterfaceCodec.LAYER), Component(2, InterfaceCodec.RECT, parent = 0))
        assertContains(assertFailsWith<IllegalArgumentException> { InterfaceCodec.encodeGroup(gap) }.message!!, "0 to 1")
        val orphan = listOf(Component(0, InterfaceCodec.LAYER), Component(1, InterfaceCodec.RECT, parent = 7))
        assertContains(assertFailsWith<IllegalArgumentException> { InterfaceCodec.encodeGroup(orphan) }.message!!, "parent 7")
        assertContains(assertFailsWith<IllegalArgumentException> { InterfaceCodec.encodeGroup(emptyList()) }.message!!, "at least one")
    }

    @Test
    fun `refuses a kind it doesn't write and a component it doesn't read`() {
        assertContains(assertFailsWith<IllegalArgumentException> { Component(0, 6) }.message!!, "type 6")
        assertContains(assertFailsWith<IllegalArgumentException> { InterfaceCodec.decode(0, byteArrayOf(0, 0)) }.message!!, "isn't if3")
        val withListener = InterfaceCodec.encode(Component(0, InterfaceCodec.LAYER)).also { it[it.size - 21] = 1 }
        assertContains(assertFailsWith<IllegalArgumentException> { InterfaceCodec.decode(0, withListener) }.message!!, "listener")
        val withTrigger = InterfaceCodec.encode(Component(0, InterfaceCodec.LAYER)).also { it[it.size - 1] = 1 }
        assertContains(assertFailsWith<IllegalArgumentException> { InterfaceCodec.decode(0, withTrigger) }.message!!, "trigger")
        val tooLong = InterfaceCodec.encode(Component(0, InterfaceCodec.LAYER)) + byteArrayOf(0)
        assertContains(assertFailsWith<IllegalArgumentException> { InterfaceCodec.decode(0, tooLong) }.message!!, "past its end")
        assertContains(assertFailsWith<IllegalArgumentException> { InterfaceCodec.encode(Component(0, InterfaceCodec.TEXT, text = "a\u0000b")) }.message!!, "NUL")
    }
}
