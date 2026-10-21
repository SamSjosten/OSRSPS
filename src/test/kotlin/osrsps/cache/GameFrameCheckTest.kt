package osrsps.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.EquipmentTab
import osrsps.game.FrameSlot
import osrsps.game.GameFrame

class GameFrameCheckTest {
    /** Gameval names as the 241 cache has them, built from our own tables. */
    private fun names(): MutableMap<Int, InterfaceNames> {
        val names = HashMap<Int, InterfaceNames>()
        for (frame in GameFrame.entries) {
            names[frame.interfaceId] = InterfaceNames(frame.cacheName, FrameSlot.entries.associate { frame.component(it) to it.cacheName })
        }
        for (overlay in GameFrame.overlays) names[overlay.interfaceId] = InterfaceNames(overlay.cacheName, emptyMap())
        for ((id, expected) in EquipmentTab.CACHE_NAMES) names[id] = InterfaceNames(expected.first, expected.second)
        return names
    }

    @Test
    fun `matching names report nothing`() {
        val names = names()
        assertEquals(emptyList(), GameFrameCheck.check { names[it] })
    }

    @Test
    fun `a moved component and a renamed interface are reported`() {
        val names = names()
        names[548] = InterfaceNames("toplevel", names.getValue(548).components + (41 to "com_41"))
        names[149] = InterfaceNames("inventory_new", emptyMap())
        assertEquals(
            listOf("toplevel component 41 should be mainmodal but is com_41", "interface 149 should be inventory but is inventory_new"),
            GameFrameCheck.check { names[it] },
        )
    }

    @Test
    fun `decodes a gameval interface entry`() {
        val data = "toplevel\u0000".toByteArray() + byteArrayOf(0, 41) + "mainmodal\u0000".toByteArray() +
            byteArrayOf(1, 0) + "side\u0000".toByteArray() + byteArrayOf(-1, -1)
        assertEquals(InterfaceNames("toplevel", mapOf(41 to "mainmodal", 256 to "side")), GameFrameCheck.decode(data))
    }
}
