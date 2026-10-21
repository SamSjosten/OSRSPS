package osrsps.cache

import com.displee.cache.CacheLibrary
import osrsps.game.FrameSlot
import osrsps.game.GameFrame
import java.nio.charset.Charset

/**
 * Checks [GameFrame]'s interface and component ids against the names in the cache's gameval index, which Jagex
 * ships with the cache from about rev 230. A mismatch means a revision moved something and the frame will show a
 * missing or wrong panel.
 */
object GameFrameCheck {
    private const val GAMEVAL_INDEX = 24
    private const val GAMEVAL_INTERFACES = 14
    private val CP1252: Charset = Charset.forName("windows-1252")

    /** The problems found, empty when every id matches. [names] looks up an interface's gameval entry by id. */
    fun check(names: (Int) -> InterfaceNames?): List<String> {
        val problems = mutableListOf<String>()
        for (frame in GameFrame.entries) {
            val interfaceNames = names(frame.interfaceId)
            if (interfaceNames == null || interfaceNames.name != frame.cacheName) {
                problems += "interface ${frame.interfaceId} should be ${frame.cacheName} but is ${interfaceNames?.name}"
                continue
            }
            for (slot in FrameSlot.entries) {
                val component = frame.component(slot)
                val actual = interfaceNames.components[component]
                if (actual != slot.cacheName) {
                    problems += "${frame.cacheName} component $component should be ${slot.cacheName} but is $actual"
                }
            }
        }
        for (overlay in GameFrame.overlays) {
            val actual = names(overlay.interfaceId)?.name
            if (actual != overlay.cacheName) problems += "interface ${overlay.interfaceId} should be ${overlay.cacheName} but is $actual"
        }
        return problems
    }

    fun check(library: CacheLibrary): List<String> {
        if (library.index(GAMEVAL_INDEX) == null) return listOf("the cache has no gameval index ($GAMEVAL_INDEX) to check against")
        return check { id -> library.data(GAMEVAL_INDEX, GAMEVAL_INTERFACES, id)?.let(::decode) }
    }

    /** One interface's gameval entry: its name, then (u16 component, string) pairs ending with 0xFFFF. */
    fun decode(data: ByteArray): InterfaceNames {
        var pos = 0
        fun string(): String {
            val end = (pos until data.size).first { data[it] == 0.toByte() }
            val value = String(data, pos, end - pos, CP1252)
            pos = end + 1
            return value
        }
        val name = string()
        val components = HashMap<Int, String>()
        while (true) {
            val component = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
            pos += 2
            if (component == 0xFFFF) break
            components[component] = string()
        }
        return InterfaceNames(name, components)
    }
}

data class InterfaceNames(val name: String, val components: Map<Int, String>)
