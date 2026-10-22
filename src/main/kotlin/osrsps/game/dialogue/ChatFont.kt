package osrsps.game.dialogue

import java.nio.charset.Charset

/**
 * A font's glyph widths, which the server needs to know how many lines a dialogue's text takes: that picks the chathead
 * animation, the line spacing and where a long text goes onto a second page. The client wraps the text itself; we wrap
 * it the same way to know what it will show.
 *
 * Dialogues use font 497 (q8_full in RSMod's names), as every text component of chat_left, chat_right, messagebox and
 * the objectboxes does in the 241 cache. Its metrics are a 257-byte file: the advance of each of the 256 cp1252
 * characters, then the ascent. [osrsps.cache.FontLoader] reads it.
 */
class ChatFont(private val advances: IntArray) {
    init {
        require(advances.size == 256) { "A font has 256 advances, not ${advances.size}" }
    }

    /** How wide [c] is drawn. A no-break space is as wide as a space. */
    fun advance(c: Char): Int {
        val char = if (c == NO_BREAK_SPACE) ' ' else c
        val bytes = char.toString().toByteArray(CP1252)
        return advances[bytes[0].toInt() and 0xFF]
    }

    /**
     * Splits [text] into the lines the client draws it on in a component [width] pixels wide. Inferred from the client's
     * behaviour, not taken from its code: a line breaks at the last space that fits (the space is dropped) or after the
     * last hyphen (which stays), `<br>` always breaks, a word too long for a line overflows it, and tags such as
     * `<col=ff0000>` take no room. `<lt>` and `<gt>` are drawn as `<` and `>`; `<img=n>` is taken as [IMG_WIDTH] wide,
     * a guess, as the mod icon sprites aren't read. Text with no lines (empty) gives one empty line.
     */
    fun splitLines(text: String, width: Int): List<String> {
        val lines = ArrayList<String>()
        val line = StringBuilder()
        var lineWidth = 0
        // Where the line would end and the next start if it broke at the last space or hyphen; -1 for none yet.
        var end = -1
        var nextStart = -1
        var widthAtBreak = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '<') {
                val close = text.indexOf('>', i)
                if (close > i) {
                    val tag = text.substring(i + 1, close)
                    i = close + 1
                    if (tag == "br") {
                        lines += line.toString()
                        line.clear()
                        lineWidth = 0
                        end = -1
                        nextStart = -1
                        continue
                    }
                    line.append('<').append(tag).append('>')
                    lineWidth += tagWidth(tag)
                    continue
                }
            }
            line.append(c)
            lineWidth += advance(c)
            if (c == ' ') {
                end = line.length - 1
                nextStart = line.length
                widthAtBreak = lineWidth
            }
            if (lineWidth > width && nextStart >= 0) {
                lines += line.substring(0, end)
                val rest = line.substring(nextStart)
                line.clear()
                line.append(rest)
                lineWidth -= widthAtBreak
                end = -1
                nextStart = -1
            }
            if (c == '-') {
                end = line.length
                nextStart = line.length
                widthAtBreak = lineWidth
            }
            i++
        }
        if (line.isNotEmpty() || lines.isEmpty()) lines += line.toString()
        return lines
    }

    private fun tagWidth(tag: String): Int = when {
        tag == "lt" -> advance('<')
        tag == "gt" -> advance('>')
        tag.startsWith("img=") -> IMG_WIDTH
        else -> 0
    }

    companion object {
        /** The font every dialogue text component uses in the 241 cache. */
        const val DIALOGUE_FONT = 497

        /** Guessed width of a mod icon or other `<img=n>` in a line; the icons are about 13 pixels wide. */
        const val IMG_WIDTH = 13

        private const val NO_BREAK_SPACE = ' '
        private val CP1252: Charset = Charset.forName("windows-1252")

        /**
         * For tests and a cache without the font: every character 6 pixels wide, about the font's average. Line counts
         * from it are rough, so the chathead animation and line spacing can be off; the client still wraps correctly.
         */
        val APPROXIMATE = ChatFont(IntArray(256) { 6 })
    }
}
