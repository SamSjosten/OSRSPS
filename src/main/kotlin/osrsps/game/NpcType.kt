package osrsps.game

/**
 * An NPC type from the cache's config archive 9, with the fields the server uses. Models, colours and the rest of how
 * the NPC is drawn stay in the cache; the client reads them itself.
 */
class NpcType(
    val id: Int,
    /** The name shown in menus; "null" when the cache gives none, as the client has it. */
    val name: String = DEFAULT_NAME,
    /** Tiles along each side: a cow is 2, so it covers 2x2 tiles from its south-west corner. */
    val size: Int = 1,
    /** Options op1 to op5 (index 0 to 4), null where there is none. Examine is added by the client. */
    val ops: List<String?> = DEFAULT_OPS,
    /** -1 when the menu shows no level. */
    val combatLevel: Int = -1,
    val standAnim: Int = -1,
    val walkAnim: Int = -1,
    /** A multi-NPC picks which of [multiNpcs] it shows by this varbit (or varp, if the varbit is -1). */
    val multiVarbit: Int = -1,
    val multiVarp: Int = -1,
    /** The types a multi-NPC can show, by the var's value; the last entry is the default, -1 for none. */
    val multiNpcs: IntArray = IntArray(0),
    /** False when the client gives the NPC no menu at all (opcode 107). */
    val interactable: Boolean = true,
    /** Integer, long and string params by param id. */
    val params: Map<Int, Any> = emptyMap(),
    /** The name in the cache's gameval index, e.g. `hans`; null when the cache has no gameval names. */
    val internalName: String? = null,
) {
    /** The option [option] (1 to 5), or null if the NPC has none there. */
    fun op(option: Int): String? = ops.getOrNull(option - 1)

    override fun toString(): String = "$name ($id)"

    companion object {
        const val DEFAULT_NAME = "null"
        val DEFAULT_OPS: List<String?> = listOf(null, null, null, null, null)
    }
}

/** Every NPC type in the cache, by id. */
class NpcTypes(private val types: Array<NpcType?>) {
    private val byInternalName: Map<String, NpcType> by lazy {
        types.filterNotNull().filter { it.internalName != null }.associateBy { it.internalName!! }
    }

    /** The lowest-id type called [name] in menus, ignoring case. Several types share most names (there are 5 cows). */
    private val byName: Map<String, NpcType> by lazy {
        val map = HashMap<String, NpcType>()
        for (type in types) {
            if (type == null || type.name == NpcType.DEFAULT_NAME) continue
            map.putIfAbsent(type.name.lowercase(), type)
        }
        map
    }

    /** One more than the highest id. */
    val size: Int get() = types.size

    operator fun get(id: Int): NpcType? = types.getOrNull(id)

    /** By gameval name, such as `hans` or `cow2_calf`, ignoring case. */
    fun find(internalName: String): NpcType? = byInternalName[internalName.lowercase()]

    /** By menu name, such as `Cow calf`, ignoring case. Several types can share a name; this gives the first. */
    fun findByName(name: String): NpcType? = byName[name.lowercase()]

    /** By id, gameval name or menu name, in that order: what `::npc` and spawn files accept. */
    fun lookup(text: String): NpcType? {
        text.toIntOrNull()?.let { return get(it) }
        return find(text.replace(' ', '_')) ?: findByName(text)
    }

    companion object {
        val NONE = NpcTypes(emptyArray())

        fun of(vararg types: NpcType): NpcTypes {
            val array = arrayOfNulls<NpcType>((types.maxOfOrNull { it.id } ?: -1) + 1)
            for (type in types) array[type.id] = type
            return NpcTypes(array)
        }
    }
}
