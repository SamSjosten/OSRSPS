package osrsps.cache

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SpriteCodecTest {
    private val red = 0xFFFF0000.toInt()
    private val green = 0xFF00FF00.toInt()
    private val blue = 0xFF0000FF.toInt()

    private fun assertSprite(expected: Sprite, actual: Sprite) {
        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)
        assertContentEquals(expected.pixels, actual.pixels)
    }

    @Test
    fun `an opaque sprite comes back as it went in`() {
        val sprite = Sprite(3, 2, intArrayOf(red, green, blue, blue, green, red))
        val data = SpriteCodec.encode(sprite)
        // 1 flag byte, 6 indices, 3 palette colours of 3 bytes, 2 + 2 + 1 group bytes, 4 shorts a frame, 2 count bytes.
        assertEquals(1 + 6 + 9 + 5 + 8 + 2, data.size)
        val frames = SpriteCodec.decode(data)
        assertEquals(1, frames.size)
        assertSprite(sprite, frames[0])
    }

    @Test
    fun `the layout is the client's, pixels then palette then the sizes and the count`() {
        val data = SpriteCodec.encode(Sprite(2, 1, intArrayOf(red, 0)))
        assertContentEquals(
            byteArrayOf(
                0, // no alpha plane, row by row
                1, 0, // palette entry 1 then the transparent entry 0
                -1, 0, 0, // the palette: red, entry 0 left out
                0, 2, 0, 1, // the group's largest width and height
                1, // palette size less one
                0, 0, 0, 0, // the frame's x and y offsets
                0, 2, 0, 1, // its width and height
                0, 1, // one frame
            ),
            data,
        )
    }

    @Test
    fun `transparent pixels are palette entry 0 and come back clear`() {
        val sprite = Sprite(2, 2, intArrayOf(red, 0, 0x00123456, green))
        val back = SpriteCodec.decode(SpriteCodec.encode(sprite))[0]
        assertContentEquals(intArrayOf(red, 0, 0, green), back.pixels)
    }

    @Test
    fun `a sprite with translucent pixels carries an alpha plane`() {
        val half = 0x80FF0000.toInt()
        val sprite = Sprite(2, 1, intArrayOf(half, green))
        val data = SpriteCodec.encode(sprite)
        assertEquals(2, data[0].toInt(), "the alpha flag")
        assertSprite(sprite, SpriteCodec.decode(data)[0])
    }

    @Test
    fun `black is drawn as the next darkest colour, as entry 0 is transparent`() {
        val black = 0xFF000000.toInt()
        val back = SpriteCodec.decode(SpriteCodec.encode(Sprite(1, 1, intArrayOf(black))))[0]
        assertEquals(0xFF000001.toInt(), back[0, 0])
    }

    @Test
    fun `several frames share a palette and each fills the group's largest size`() {
        val small = Sprite(1, 1, intArrayOf(red))
        val big = Sprite(2, 2, intArrayOf(red, green, green, red))
        val frames = SpriteCodec.decode(SpriteCodec.encode(listOf(small, big)))
        assertEquals(2, frames.size)
        assertSprite(Sprite(2, 2, intArrayOf(red, 0, 0, 0)), frames[0])
        assertSprite(big, frames[1])
    }

    @Test
    fun `reads a frame stored column by column`() {
        // The vertical flag: indices run down each column, so entry 1 at (0, 0), 2 at (0, 1), 3 at (1, 0), 4 at (1, 1).
        val data = byteArrayOf(
            1, 1, 2, 3, 4,
            1, 0, 0, 2, 0, 0, 3, 0, 0, 4, 0, 0,
            0, 2, 0, 2, 4, 0, 0, 0, 0, 0, 2, 0, 2, 0, 1,
        )
        val frame = SpriteCodec.decode(data)[0]
        assertContentEquals(intArrayOf(0xFF010000.toInt(), 0xFF030000.toInt(), 0xFF020000.toInt(), 0xFF040000.toInt()), frame.pixels)
    }

    @Test
    fun `a sprite of too many colours is reduced to the palette's 255 first`() {
        // 16 by 16 pixels, each its own grey: 256 colours, one too many (none black, which entry 0 would draw clear).
        val sprite = Sprite(16, 16, IntArray(256) { 0xFF808080.toInt() + it })
        val reduced = SpriteCodec.quantize(sprite)
        val colours = reduced.pixels.map { it and 0xFFFFFF }.toSet()
        assertTrue(colours.size <= SpriteCodec.MAX_COLOURS, "${colours.size} colours")
        assertTrue(colours.size > 100, "a coarse cut, not a wipe: ${colours.size} colours")
        assertSprite(reduced, SpriteCodec.decode(SpriteCodec.encode(sprite))[0])
        val few = Sprite(1, 2, intArrayOf(red, green))
        assertSame(few, SpriteCodec.quantize(few), "a sprite that fits is left alone")
    }

    @Test
    fun `refuses what isn't a sprite`() {
        assertContains(assertFailsWith<IllegalArgumentException> { SpriteCodec.decode(byteArrayOf(1, 2, 3)) }.message!!, "9 bytes")
        assertContains(assertFailsWith<IllegalArgumentException> { SpriteCodec.decode(ByteArray(20) { 0 }) }.message!!, "0 frames")
        assertContains(assertFailsWith<IllegalArgumentException> { Sprite(0, 1, IntArray(0)) }.message!!, "0 by 1")
        assertContains(assertFailsWith<IllegalArgumentException> { Sprite(2, 2, IntArray(3)) }.message!!, "4 pixels, not 3")
        assertContains(assertFailsWith<IllegalArgumentException> { SpriteCodec.encode(emptyList()) }.message!!, "at least one frame")
    }
}
