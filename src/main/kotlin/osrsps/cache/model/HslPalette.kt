package osrsps.cache.model

import kotlin.math.abs
import kotlin.math.pow

/**
 * The client's 16-bit HSL colours: 6 bits of hue, 3 of saturation and 7 of luminance, turned into RGB the way the
 * client builds its palette (RuneLite's JagexColor, a port of the client's code). [nearest] goes the other way for
 * the importer: the palette entry closest to an RGB colour, so every colour a model is given is one the client can
 * show.
 */
object HslPalette {
    /** The brightness the palette is built with; the client's default ("Normal") is 0.8 (RuneLite's BRIGHTNESS_LOW). */
    const val BRIGHTNESS = 0.8

    private const val HUE_OFFSET = 0.5 / 64.0
    private const val SATURATION_OFFSET = 0.5 / 8.0

    fun pack(hue: Int, saturation: Int, luminance: Int): Int = (hue and 63) shl 10 or ((saturation and 7) shl 7) or (luminance and 127)
    fun hue(hsl: Int): Int = hsl shr 10 and 63
    fun saturation(hsl: Int): Int = hsl shr 7 and 7
    fun luminance(hsl: Int): Int = hsl and 127

    /** The RGB (0xRRGGBB) the client shows for [hsl] at [brightness]. */
    fun toRgb(hsl: Int, brightness: Double = BRIGHTNESS): Int {
        val hue = hue(hsl) / 64.0 + HUE_OFFSET
        val saturation = saturation(hsl) / 8.0 + SATURATION_OFFSET
        val luminance = luminance(hsl) / 128.0
        val chroma = (1.0 - abs(2.0 * luminance - 1.0)) * saturation
        val x = chroma * (1 - abs((hue * 6.0) % 2.0 - 1.0))
        val lightness = luminance - chroma / 2
        var r = lightness
        var g = lightness
        var b = lightness
        when ((hue * 6.0).toInt()) {
            0 -> { r += chroma; g += x }
            1 -> { g += chroma; r += x }
            2 -> { g += chroma; b += x }
            3 -> { b += chroma; g += x }
            4 -> { b += chroma; r += x }
            else -> { r += chroma; b += x }
        }
        var rgb = ((r * 256.0).toInt() shl 16) or ((g * 256.0).toInt() shl 8) or (b * 256.0).toInt()
        rgb = adjustForBrightness(rgb, brightness)
        return if (rgb == 0) 1 else rgb
    }

    private fun adjustForBrightness(rgb: Int, brightness: Double): Int {
        val r = ((rgb shr 16) / 256.0).pow(brightness)
        val g = ((rgb shr 8 and 255) / 256.0).pow(brightness)
        val b = ((rgb and 255) / 256.0).pow(brightness)
        return ((r * 256.0).toInt() shl 16) or ((g * 256.0).toInt() shl 8) or (b * 256.0).toInt()
    }

    /** Every packed HSL value's RGB at [BRIGHTNESS], indexed by the packed value. */
    val rgb: IntArray by lazy { IntArray(65536) { toRgb(it) } }

    /** The packed HSL whose RGB at [BRIGHTNESS] is closest to [rgb] (0xRRGGBB), by distance in RGB. */
    fun nearest(rgb: Int): Int {
        val r = rgb shr 16 and 255
        val g = rgb shr 8 and 255
        val b = rgb and 255
        var best = 0
        var bestDistance = Int.MAX_VALUE
        val palette = this.rgb
        for (hsl in palette.indices) {
            val c = palette[hsl]
            val dr = (c shr 16 and 255) - r
            val dg = (c shr 8 and 255) - g
            val db = (c and 255) - b
            val distance = dr * dr + dg * dg + db * db
            if (distance < bestDistance) {
                bestDistance = distance
                best = hsl
                if (distance == 0) break
            }
        }
        return best
    }
}
