package osrsps.game

/**
 * An item type (obj) from the cache's config archive 10, with the fields the server uses. Model and drawing fields
 * stay in the cache; the client reads them itself.
 */
class ObjType(
    val id: Int,
    /** The name shown in menus; "null" when the cache gives none, as the client has it. */
    val name: String = DEFAULT_NAME,
    /** Examine text, null when the cache has none. */
    val desc: String? = null,
    /** The item's value, which alchemy and shops start from. */
    val cost: Int = 1,
    val stackable: Boolean = false,
    val members: Boolean = false,
    /** Can be traded on the Grand Exchange (opcode 65, RSMod's `stockmarket`). */
    val tradeable: Boolean = false,
    /** Ground options op1 to op5 (index 0 to 4), null where there is none. */
    val ops: List<String?> = DEFAULT_OPS,
    /** Inventory options iop1 to iop5 (index 0 to 4), null where there is none. */
    val iops: List<String?> = DEFAULT_IOPS,
    /**
     * The equipment slot the item is worn in ([WearPos]), -1 if it can't be worn. [wearpos2] and [wearpos3] are slots
     * the item covers on the player's model: a full helm hides the hair and jaw, a two-handed weapon the shield.
     */
    val wearpos1: Int = -1,
    val wearpos2: Int = -1,
    val wearpos3: Int = -1,
    /** In grams. */
    val weight: Int = 0,
    /** The noted (or unnoted) version of the item, -1 if there is none. */
    val certlink: Int = -1,
    /** Set on a noted item: the note model it is drawn with. */
    val certtemplate: Int = -1,
    /** The bank placeholder of the item, or the item a placeholder stands for. */
    val placeholderlink: Int = -1,
    val placeholdertemplate: Int = -1,
    /** Integer and string params by param id. */
    val params: Map<Int, Any> = emptyMap(),
    /** The name in the cache's gameval index, e.g. `bronze_axe`; null when the cache has no gameval names. */
    val internalName: String? = null,
    /** The item's category (opcode 94), which groups items such as axes or scimitars; -1 for none. */
    val category: Int = -1,
) {
    val isCert: Boolean get() = certtemplate != -1

    val isPlaceholder: Boolean get() = placeholdertemplate != -1

    /** The inventory option [option] (1 to 5), or null if the item has none there. */
    fun iop(option: Int): String? = iops.getOrNull(option - 1)

    fun intParam(id: Int, default: Int = 0): Int = params[id] as? Int ?: default

    fun stringParam(id: Int): String? = params[id] as? String

    val wearable: Boolean get() = wearpos1 in 0 until WearPos.COUNT

    /** Takes both hands: a weapon that also covers the shield slot, as bows and 2h swords do in the cache. */
    val twoHanded: Boolean get() = wearpos1 == WearPos.WEAPON && (wearpos2 == WearPos.SHIELD || wearpos3 == WearPos.SHIELD)

    /** High and low alchemy give 60% and 40% of the value, rounded down (wiki, from memory; not checked here). */
    val highAlch: Int get() = (cost.toLong() * 60 / 100).toInt()
    val lowAlch: Int get() = (cost.toLong() * 40 / 100).toInt()

    override fun toString(): String = "$name ($id)"

    companion object {
        const val DEFAULT_NAME = "null"
        val DEFAULT_OPS: List<String?> = listOf(null, null, "Take", null, null)
        val DEFAULT_IOPS: List<String?> = listOf(null, null, null, null, "Drop")

        /** Param no_alchemy in RSMod's symbol table: 1 when the item can't be alchemised. */
        const val PARAM_NO_ALCHEMY = 295
    }
}

/** Every item type in the cache, by id. */
class ObjTypes(private val types: Array<ObjType?>) {
    private val byInternalName: Map<String, ObjType> by lazy {
        types.filterNotNull().filter { it.internalName != null }.associateBy { it.internalName!! }
    }

    /** The lowest-id item that isn't a note or placeholder called [name] in menus, ignoring case. */
    private val byName: Map<String, ObjType> by lazy {
        val map = HashMap<String, ObjType>()
        for (type in types) {
            if (type == null || type.isCert || type.isPlaceholder || type.name == ObjType.DEFAULT_NAME) continue
            map.putIfAbsent(type.name.lowercase(), type)
        }
        map
    }

    /** One more than the highest id. */
    val size: Int get() = types.size

    operator fun get(id: Int): ObjType? = types.getOrNull(id)

    /** By gameval name, such as `bronze_axe`, ignoring case. */
    fun find(internalName: String): ObjType? = byInternalName[internalName.lowercase()]

    /** By menu name, such as `Bronze axe`, ignoring case. Several items can share a name; this gives the first. */
    fun findByName(name: String): ObjType? = byName[name.lowercase()]

    companion object {
        val NONE = ObjTypes(emptyArray())

        fun of(vararg types: ObjType): ObjTypes {
            val array = arrayOfNulls<ObjType>((types.maxOfOrNull { it.id } ?: -1) + 1)
            for (type in types) array[type.id] = type
            return ObjTypes(array)
        }
    }
}
