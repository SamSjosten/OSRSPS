package osrsps.game.dialogue

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.cache.FontLoader

class ChatFontTest {
    /** Every character 1 pixel wide, so a width is a character count. */
    private val font = ChatFont(IntArray(256) { 1 })

    @Test
    fun `breaks at the last space that fits and drops it`() {
        assertEquals(listOf("one two", "three"), font.splitLines("one two three", 9))
        assertEquals(listOf("one two three"), font.splitLines("one two three", 13))
    }

    @Test
    fun `breaks after a hyphen and keeps it`() {
        assertEquals(listOf("well-", "known"), font.splitLines("well-known", 7))
    }

    @Test
    fun `br always breaks and tags take no room`() {
        assertEquals(listOf("<col=ff0000>red</col>", "next"), font.splitLines("<col=ff0000>red</col><br>next", 3))
    }

    @Test
    fun `lt and gt are drawn one character wide`() {
        assertEquals(listOf("a<lt>b", "c"), font.splitLines("a<lt>b c", 3))
    }

    @Test
    fun `a word too long for a line overflows it`() {
        assertEquals(listOf("abcdefgh", "ij"), font.splitLines("abcdefgh ij", 4))
    }

    @Test
    fun `empty text is one empty line`() {
        assertEquals(listOf(""), font.splitLines("", 10))
    }

    @Test
    fun `widths are by cp1252 byte, a no-break space as wide as a space`() {
        val advances = IntArray(256) { 1 }
        advances[' '.code] = 4
        advances[0x80] = 9 // the euro sign in cp1252
        val font = ChatFont(advances)
        assertEquals(9, font.advance('€'))
        assertEquals(4, font.advance(' '))
    }

    @Test
    fun `the cache's 257-byte font gives its advances`() {
        val data = ByteArray(257) { (it % 7).toByte() }
        val font = FontLoader.decode(data)!!
        assertEquals(3, font.advance('\u000A'))
        assertEquals(null, FontLoader.decode(ByteArray(10)))
        assertEquals(null, FontLoader.decode(null))
    }
}
