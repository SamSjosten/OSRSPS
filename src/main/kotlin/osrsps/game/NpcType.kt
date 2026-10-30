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
    /** Options shown only while a var is in a range (cache opcode 252), in cache order. */
    val conditionalOps: List<ConditionalOp> = emptyList(),
    /** Sub-options (cache opcodes 251 and 253), in cache order. */
    val subOps: List<SubOp> = emptyList(),
    /** Combat levels, cache opcodes 74 to 79. */
    val stats: NpcStats = NpcStats.DEFAULT,
) {
    /** The option [option] (1 to 5), or null if the NPC has none there. Conditional options are not looked at. */
    fun op(option: Int): String? = ops.getOrNull(option - 1)

    /**
     * The option [option] (1 to 5) a player sees, with [value] reading a conditional option's var for them (null when
     * it can't be read): the first conditional option for that slot whose var is in range, else the plain one.
     * The slot index is 0-based like opcodes 30 to 34 (RuneLite's cache loader stores both the same way); that a matching
     * conditional option replaces the plain one is inferred, not checked against the client.
     */
    fun op(option: Int, value: (ConditionalOp) -> Int?): String? {
        for (conditional in conditionalOps) {
            if (conditional.index != option - 1) continue
            val current = value(conditional) ?: continue
            if (current in conditional.min..conditional.max) return conditional.text
        }
        return op(option)
    }

    /**
     * Sub-option [sub] of option [option] (1 to 5) as a player sees it, with [value] reading a conditional sub-option's
     * var as for [op]; null when there is none. A conditional sub-option whose var is in range wins over a plain one,
     * as for options (inferred).
     */
    fun subOp(option: Int, sub: Int, value: (ConditionalOp) -> Int?): String? {
        val forSlot = subOps.filter { it.index == option - 1 && it.sub == sub }
        for (subOp in forSlot) {
            val condition = subOp.condition ?: continue
            val current = value(condition) ?: continue
            if (current in condition.min..condition.max) return subOp.text
        }
        return forSlot.firstOrNull { it.condition == null }?.text
    }

    override fun toString(): String = "$name ($id)"

    companion object {
        const val DEFAULT_NAME = "null"
        val DEFAULT_OPS: List<String?> = listOf(null, null, null, null, null)
    }
}

/**
 * An NPC's combat levels, as the cache gives them: opcodes 74 to 79 in this order (RuneLite's NpcLoader). Checked against
 * the OSRS Wiki's stats for 2,611 of 2,894 monsters in the 241 cache (the rest are Wiki versions the cache doesn't tell
 * apart); a man is 1/1/1/7/1/1 on both.
 */
data class NpcStats(
    val attack: Int = 1,
    val defence: Int = 1,
    val strength: Int = 1,
    val hitpoints: Int = 1,
    val ranged: Int = 1,
    val magic: Int = 1,
) {
    companion object {
        val DEFAULT = NpcStats()

        /** From the six levels in opcode order (74 attack to 79 magic). */
        fun of(levels: IntArray): NpcStats = NpcStats(levels[0], levels[1], levels[2], levels[3], levels[4], levels[5])
    }

    /** The level of [stat]. */
    operator fun get(stat: NpcStat): Int = when (stat) {
        NpcStat.ATTACK -> attack
        NpcStat.DEFENCE -> defence
        NpcStat.STRENGTH -> strength
        NpcStat.RANGED -> ranged
        NpcStat.MAGIC -> magic
    }
}

/** A monster's levels that can be drained ([osrsps.game.combat.NpcDrains]); its Hitpoints are its health instead. */
enum class NpcStat {
    ATTACK, DEFENCE, STRENGTH, RANGED, MAGIC;

    companion object {
        fun parse(text: String): NpcStat = entries.firstOrNull { it.name.equals(text, ignoreCase = true) }
            ?: throw IllegalArgumentException("no stat '$text': ${entries.joinToString { it.name.lowercase() }}")
    }
}

/**
 * An option at slot [index] (0 to 4) shown while varbit [varbit], or varp [varp] when [varbit] is -1, is in [min] to
 * [max]. [text] is null for "Hidden".
 */
class ConditionalOp(val index: Int, val varp: Int, val varbit: Int, val min: Int, val max: Int, val text: String?)

/**
 * Sub-option [sub] of the option at slot [index] (0 to 4). [text] is null for "Hidden". [condition] is null for a plain
 * sub-option (opcode 251), else the var range it is shown in (opcode 253).
 */
class SubOp(val index: Int, val sub: Int, val text: String?, val condition: ConditionalOp? = null)

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
