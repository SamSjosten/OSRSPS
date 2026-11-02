package osrsps.cache

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Writes a loc type of our own as config archive 6 holds one, with the opcodes [LocTypeDecoder] reads back: the
 * name (2), the models (7, the 241 cache's list of 4-byte model ids without shape types, which a tree uses), the
 * footprint (14, 15), walking and projectiles (17, 18), the options (30 to 34) and a varbit transform (77: the
 * client draws one of the [Transform]'s children, picked by the player's varbit, so each player sees their own).
 * Everything else is left at the client's defaults. The decoder reads the result, so the server's view of the loc is
 * the client's.
 */
object LocTypeEncoder {
    /** Models are ids in index 7 (the cache's, or the overlay's); [ops] are op1 to op5, null for none. */
    class Definition(
        val name: String,
        val models: List<Int>,
        val ops: List<String?> = List(5) { null },
        val width: Int = 1,
        val length: Int = 1,
        /** False for a loc players walk over, which blocks no projectile either (opcode 17). */
        val solid: Boolean = true,
        /** False for a solid loc arrows and spells pass (opcode 18). */
        val blocksProjectiles: Boolean = true,
        /** The children this loc stands in for, by the player's varbit; null for a loc that is always itself. */
        val transform: Transform? = null,
    ) {
        init {
            require(name.isNotEmpty() && name.none { it == '\u0000' }) { "Bad loc name '$name'" }
            require(models.isNotEmpty() && models.size <= 255 && models.all { it >= 0 }) { "Bad model list $models" }
            require(ops.size == 5) { "ops are op1 to op5" }
            require(ops.all { it == null || (it.isNotEmpty() && it.none { c -> c == '\u0000' }) }) { "Bad option in $ops" }
            require(width in 1..255 && length in 1..255) { "A loc is 1 to 255 tiles each way, not $width by $length" }
        }
    }

    /**
     * Opcode 77: the client shows `children[value of varbit]` in this loc's place, and nothing when the value is past
     * the list (as RuneLite's ObjectComposition.getImpostor reads it). [NONE] for a child draws nothing.
     */
    class Transform(val varbit: Int, val children: List<Int>) {
        init {
            require(varbit in 0 until NONE) { "Bad varbit $varbit" }
            require(children.size in 1..256) { "A transform has 1 to 256 children, not ${children.size}" }
            require(children.all { it == NONE || it in 0 until NONE }) { "Bad child in $children" }
        }
    }

    /** An id the client reads as "none" in a transform: no varp, or no child to draw. */
    const val NONE = 0xFFFF

    private val CP1252: Charset = Charset.forName("windows-1252")

    fun encode(definition: Definition): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(7)
        out.write(definition.models.size)
        for (model in definition.models) {
            out.write(model ushr 24)
            out.write(model ushr 16 and 0xFF)
            out.write(model ushr 8 and 0xFF)
            out.write(model and 0xFF)
        }
        out.write(2)
        out.string(definition.name)
        if (definition.width != 1) {
            out.write(14)
            out.write(definition.width)
        }
        if (definition.length != 1) {
            out.write(15)
            out.write(definition.length)
        }
        if (!definition.solid) out.write(17) else if (!definition.blocksProjectiles) out.write(18)
        for ((i, op) in definition.ops.withIndex()) {
            if (op == null) continue
            out.write(30 + i)
            out.string(op)
        }
        definition.transform?.let { transform ->
            out.write(77)
            out.short(transform.varbit)
            out.short(NONE)
            out.write(transform.children.size - 1)
            for (child in transform.children) out.short(child)
        }
        out.write(0)
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.short(value: Int) {
        write(value ushr 8 and 0xFF)
        write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.string(text: String) {
        write(text.toByteArray(CP1252))
        write(0)
    }
}
