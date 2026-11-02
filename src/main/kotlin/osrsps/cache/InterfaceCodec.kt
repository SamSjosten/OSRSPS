package osrsps.cache

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import osrsps.cache.overlay.Js5Group

/**
 * Reads and writes an interface's components as the interface index (3) holds them: one group an interface, one file
 * a component, in the "if3" form every OSRS interface uses (RuneLite's InterfaceLoader.decodeIf3, read 2026-11-02 from
 * its checkout of 2026-10-30). The kinds we write: a [LAYER] (holds children; clickable with an op), a [RECT], a
 * [TEXT], a [GRAPHIC] (a sprite from index 8) and a [LINE]. Models (type 6) aren't written. Every listener is left
 * empty: what a component does is the server's, through its ops (IF_SETEVENTS_V2 enables them; the definition's own
 * click mask holds only the old, dead op bits) and IF_SETTEXT, IF_SETHIDE and the rest.
 */
object InterfaceCodec {
    const val INDEX = 3
    const val LAYER = 0
    const val RECT = 3
    const val TEXT = 4
    const val GRAPHIC = 5
    const val LINE = 9

    /** Position and size modes: [ABSOLUTE] from the parent's left or top, [CENTRE] of the parent, [OPPOSITE] from its right or bottom. */
    const val ABSOLUTE = 0
    const val CENTRE = 1
    const val OPPOSITE = 2

    /** Text alignment: [LEFT]/[TOP], [CENTRE], [RIGHT]/[BOTTOM]. */
    const val LEFT = 0
    const val RIGHT = 2
    const val TOP = 0
    const val BOTTOM = 2

    const val NO_PARENT = -1
    const val NO_SPRITE = -1
    const val NO_FONT = -1
    private const val LISTENERS = 18
    private const val TRIGGERS = 3
    private val CP1252: Charset = Charset.forName("windows-1252")

    /**
     * One component: its file id [id] in the group, [type], and what its type reads. [parent] is a component id of the
     * same interface or [NO_PARENT]. [ops] are its right-click options (1-based for IF_BUTTON's op), [name] the target
     * the menu shows after the op (RS's `<col=ff9040>` names). Modes: see [ABSOLUTE]. The rest is as the client reads it.
     */
    data class Component(
        val id: Int,
        val type: Int,
        val x: Int = 0,
        val y: Int = 0,
        val width: Int = 0,
        val height: Int = 0,
        val parent: Int = NO_PARENT,
        val hidden: Boolean = false,
        val xMode: Int = ABSOLUTE,
        val yMode: Int = ABSOLUTE,
        val widthMode: Int = ABSOLUTE,
        val heightMode: Int = ABSOLUTE,
        val contentType: Int = 0,
        val ops: List<String> = emptyList(),
        val name: String = "",
        val targetVerb: String = "",
        val clickMask: Int = 0,
        // A layer's.
        val scrollWidth: Int = 0,
        val scrollHeight: Int = 0,
        val noClickThrough: Boolean = false,
        // A graphic's.
        val sprite: Int = NO_SPRITE,
        val texture: Int = 0,
        val tiled: Boolean = false,
        val opacity: Int = 0,
        val border: Int = 0,
        val shadow: Int = 0,
        val flippedVertically: Boolean = false,
        val flippedHorizontally: Boolean = false,
        // A text's, a rect's and a line's colour; a text's font and layout; a rect's fill; a line's width and direction.
        val colour: Int = 0,
        val font: Int = NO_FONT,
        val text: String = "",
        val lineHeight: Int = 0,
        val xAlign: Int = LEFT,
        val yAlign: Int = TOP,
        val shadowed: Boolean = false,
        val filled: Boolean = false,
        val lineWidth: Int = 1,
        val lineDown: Boolean = false,
    ) {
        init {
            require(id in 0..0xFFFF) { "A component id is 0 to 65535, not $id" }
            require(type in listOf(LAYER, RECT, TEXT, GRAPHIC, LINE)) { "Component $id: type $type isn't one we write" }
            require(parent == NO_PARENT || parent in 0..0xFFFF) { "Component $id: parent $parent" }
            require(ops.size <= 10) { "Component $id: ${ops.size} ops" }
            require(width in 0..0xFFFF && (height in 0..0xFFFF || type == LINE)) { "Component $id: $width by $height" }
        }
    }

    fun encode(component: Component): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0xFF)
        out.write(component.type)
        out.short(component.contentType)
        out.short(component.x)
        out.short(component.y)
        out.short(component.width)
        out.short(component.height)
        out.write(component.widthMode)
        out.write(component.heightMode)
        out.write(component.xMode)
        out.write(component.yMode)
        out.short(if (component.parent == NO_PARENT) 0xFFFF else component.parent)
        out.write(if (component.hidden) 1 else 0)
        when (component.type) {
            LAYER -> {
                out.short(component.scrollWidth)
                out.short(component.scrollHeight)
                out.write(if (component.noClickThrough) 1 else 0)
            }
            GRAPHIC -> {
                out.int(component.sprite)
                out.short(component.texture)
                out.write(if (component.tiled) 1 else 0)
                out.write(component.opacity)
                out.write(component.border)
                out.int(component.shadow)
                out.write(if (component.flippedVertically) 1 else 0)
                out.write(if (component.flippedHorizontally) 1 else 0)
            }
            TEXT -> {
                out.short(if (component.font == NO_FONT) 0xFFFF else component.font)
                out.string(component.text)
                out.write(component.lineHeight)
                out.write(component.xAlign)
                out.write(component.yAlign)
                out.write(if (component.shadowed) 1 else 0)
                out.int(component.colour)
            }
            RECT -> {
                out.int(component.colour)
                out.write(if (component.filled) 1 else 0)
                out.write(component.opacity)
            }
            LINE -> {
                out.write(component.lineWidth)
                out.int(component.colour)
                out.write(if (component.lineDown) 1 else 0)
            }
        }
        out.write(component.clickMask ushr 16 and 0xFF)
        out.write(component.clickMask ushr 8 and 0xFF)
        out.write(component.clickMask and 0xFF)
        out.string(component.name)
        out.write(component.ops.size)
        for (op in component.ops) out.string(op)
        out.write(0) // drag dead zone
        out.write(0) // drag dead time
        out.write(0) // drag render behaviour
        out.string(component.targetVerb)
        repeat(LISTENERS + TRIGGERS) { out.write(0) }
        return out.toByteArray()
    }

    /** The group an interface is served as: its components as files, in id order. Ids are 0 to n - 1, with none missing. */
    fun encodeGroup(components: List<Component>): ByteArray {
        require(components.isNotEmpty()) { "An interface has at least one component" }
        require(components.map { it.id }.sorted() == components.indices.toList()) { "Component ids run 0 to ${components.size - 1} with none missing" }
        for (component in components) {
            require(component.parent == NO_PARENT || components.any { it.id == component.parent }) { "Component ${component.id}'s parent ${component.parent} isn't in the interface" }
        }
        return Js5Group.pack(components.associate { it.id to encode(it) })
    }

    /** The components of a group of [count] files (the reference table's count), by id. */
    fun decodeGroup(data: ByteArray, count: Int): List<Component> =
        Js5Group.unpack(data, (0 until count).toList()).map { (id, bytes) -> decode(id, bytes) }

    fun decode(id: Int, data: ByteArray): Component {
        val input = Input(data)
        require(input.byte() == 0xFF) { "Component $id isn't if3" }
        val type = input.byte()
        val contentType = input.short()
        val x = input.signedShort()
        val y = input.signedShort()
        val width = input.short()
        val height = if (type == LINE) input.signedShort() else input.short()
        val widthMode = input.signedByte()
        val heightMode = input.signedByte()
        val xMode = input.signedByte()
        val yMode = input.signedByte()
        val parent = input.short().let { if (it == 0xFFFF) NO_PARENT else it }
        val hidden = input.byte() == 1
        var component = Component(id, type, x, y, width, height, parent, hidden, xMode, yMode, widthMode, heightMode, contentType)
        component = when (type) {
            LAYER -> component.copy(scrollWidth = input.short(), scrollHeight = input.short(), noClickThrough = input.byte() == 1)
            GRAPHIC -> component.copy(
                sprite = input.int(), texture = input.short(), tiled = input.byte() == 1, opacity = input.byte(), border = input.byte(),
                shadow = input.int(), flippedVertically = input.byte() == 1, flippedHorizontally = input.byte() == 1,
            )
            TEXT -> component.copy(
                font = input.short().let { if (it == 0xFFFF) NO_FONT else it }, text = input.string(), lineHeight = input.byte(),
                xAlign = input.byte(), yAlign = input.byte(), shadowed = input.byte() == 1, colour = input.int(),
            )
            RECT -> component.copy(colour = input.int(), filled = input.byte() == 1, opacity = input.byte())
            LINE -> component.copy(lineWidth = input.byte(), colour = input.int(), lineDown = input.byte() == 1)
            else -> error("Component $id is of type $type, which isn't read")
        }
        val clickMask = (input.byte() shl 16) or (input.byte() shl 8) or input.byte()
        val name = input.string()
        val ops = List(input.byte()) { input.string() }
        repeat(3) { input.byte() }
        val targetVerb = input.string()
        repeat(LISTENERS) { require(input.byte() == 0) { "Component $id has a listener, which isn't read" } }
        repeat(TRIGGERS) { require(input.byte() == 0) { "Component $id has a trigger, which isn't read" } }
        require(input.atEnd) { "Component $id has ${input.left} bytes past its end" }
        return component.copy(clickMask = clickMask, name = name, ops = ops, targetVerb = targetVerb)
    }

    private class Input(private val data: ByteArray) {
        private var pos = 0
        val atEnd: Boolean get() = pos == data.size
        val left: Int get() = data.size - pos
        fun byte(): Int = data[pos++].toInt() and 0xFF
        fun signedByte(): Int = data[pos++].toInt()
        fun short(): Int = (byte() shl 8) or byte()
        fun signedShort(): Int = short().toShort().toInt()
        fun int(): Int = (short() shl 16) or short()
        fun string(): String {
            val end = (pos until data.size).first { data[it] == 0.toByte() }
            val value = String(data, pos, end - pos, CP1252)
            pos = end + 1
            return value
        }
    }

    private fun ByteArrayOutputStream.short(value: Int) {
        write(value ushr 8 and 0xFF)
        write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.int(value: Int) {
        write(value ushr 24 and 0xFF)
        write(value ushr 16 and 0xFF)
        write(value ushr 8 and 0xFF)
        write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.string(value: String) {
        require('\u0000' !in value) { "A string holds no NUL" }
        write(value.toByteArray(CP1252))
        write(0)
    }
}
