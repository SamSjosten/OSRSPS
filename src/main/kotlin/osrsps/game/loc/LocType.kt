package osrsps.game.loc

import osrsps.map.LocCollisionType

/** A loc (object) type from config archive 6, with what the server needs: its name, options, footprint and collision. */
data class LocType(
    val id: Int,
    /** The cache's display name; "null" for locs nobody sees a name for. */
    val name: String = "null",
    /** op1 to op5, null where the option is missing or hidden. */
    val ops: List<String?> = List(5) { null },
    val collision: LocCollisionType = LocCollisionType(),
    /**
     * Sides a player can't use the loc from, before rotation: 1 north, 2 east, 4 south, 8 west (opcode 69, the
     * client's "blockrange" flags).
     */
    val accessBlock: Int = 0,
    /** The type changes with a varp or varbit (opcodes 77 and 92), so its real options are on another type. */
    val multiloc: Boolean = false,
    /** The loc's name in the cache's gameval index, e.g. `poordoor`; null when the cache has none. */
    val internalName: String? = null,
) {
    val width: Int get() = collision.width
    val length: Int get() = collision.length

    /** The option number (1 to 5) whose text is [text], ignoring case, or 0 if it has none. */
    fun op(text: String): Int = ops.indexOfFirst { it.equals(text, ignoreCase = true) } + 1

    /** Whether option [option] (1 to 5) shows in the client's menu. */
    fun hasOp(option: Int): Boolean = ops.getOrNull(option - 1) != null
}

/** Every loc type in the cache, by id. */
class LocTypes(private val types: Map<Int, LocType>) {
    val size: Int get() = types.size

    operator fun get(id: Int): LocType? = types[id]

    private val byInternalName: Map<String, LocType> by lazy {
        types.values.filter { it.internalName != null }.associateBy { it.internalName!! }
    }

    fun byInternalName(name: String): LocType? = byInternalName[name]

    val all: Collection<LocType> get() = types.values

    companion object {
        val NONE = LocTypes(emptyMap())
    }
}
