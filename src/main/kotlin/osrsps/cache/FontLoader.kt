package osrsps.cache

import com.displee.cache.CacheLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.dialogue.ChatFont

private val logger = KotlinLogging.logger {}

/**
 * Reads the dialogue font's metrics from the cache's font index (13), group [ChatFont.DIALOGUE_FONT]. In the live 241
 * cache (read 2026-10-22) every font there is a 257-byte file: 256 advances, one per cp1252 character, then the ascent.
 */
object FontLoader {
    private const val FONT_INDEX = 13

    fun loadDialogueFont(library: CacheLibrary): ChatFont {
        val data = library.data(FONT_INDEX, ChatFont.DIALOGUE_FONT, 0)
        return decode(data) ?: run {
            logger.warn { "No usable font ${ChatFont.DIALOGUE_FONT} in the cache; dialogue line counts will be rough" }
            ChatFont.APPROXIMATE
        }
    }

    /** The font in [data], or null if it isn't the 257-byte form. Other forms (with kerning) aren't used by 241. */
    fun decode(data: ByteArray?): ChatFont? {
        if (data == null || data.size != SIMPLE_SIZE) return null
        return ChatFont(IntArray(256) { data[it].toInt() and 0xFF })
    }

    private const val SIMPLE_SIZE = 257
}
