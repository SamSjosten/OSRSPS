package osrsps.cache.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HslPaletteTest {
    @Test
    fun `packs and unpacks the three fields`() {
        val hsl = HslPalette.pack(63, 7, 127)
        assertEquals(0xFFFF, hsl)
        assertEquals(63, HslPalette.hue(hsl))
        assertEquals(7, HslPalette.saturation(hsl))
        assertEquals(127, HslPalette.luminance(hsl))
        assertEquals(0x1234, HslPalette.pack(HslPalette.hue(0x1234), HslPalette.saturation(0x1234), HslPalette.luminance(0x1234)))
    }

    @Test
    fun `matches the client's palette for known entries`() {
        // Luminance 0 is black, which the palette stores as 1 so 0 can mean "no colour"; full luminance is white.
        assertEquals(1, HslPalette.toRgb(HslPalette.pack(0, 0, 0)))
        val white = HslPalette.toRgb(HslPalette.pack(0, 0, 127))
        assertTrue(listOf(white shr 16 and 255, white shr 8 and 255, white and 255).all { it >= 250 }, "white came out ${white.toString(16)}")
        // A grey: no saturation (the half-step offset leaves a little), mid luminance, then the brightness curve.
        val grey = HslPalette.toRgb(HslPalette.pack(0, 0, 64))
        assertTrue(grey shr 16 and 255 >= grey and 255, "red ${grey shr 16 and 255} should not be below blue ${grey and 255}")
        assertTrue(grey shr 16 and 255 in 140..170, "mid grey came out ${grey.toString(16)}")
    }

    @Test
    fun `the nearest entry of a palette colour is that entry`() {
        for (hsl in listOf(0x1234, 0x7F, HslPalette.pack(20, 6, 70), HslPalette.pack(40, 5, 60))) {
            assertEquals(hsl, HslPalette.nearest(HslPalette.rgb[hsl]), "hsl ${hsl.toString(16)}")
        }
    }

    @Test
    fun `nearest finds a close hue and luminance for an arbitrary colour`() {
        val hsl = HslPalette.nearest(0xCC3311)
        val rgb = HslPalette.rgb[hsl]
        assertTrue(rgb shr 16 and 255 > rgb shr 8 and 255, "a red should come back red-dominant: ${rgb.toString(16)}")
        assertTrue(HslPalette.hue(hsl) <= 4 || HslPalette.hue(hsl) >= 60, "hue ${HslPalette.hue(hsl)} is not near red")
    }
}
