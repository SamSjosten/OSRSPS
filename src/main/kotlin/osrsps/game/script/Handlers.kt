package osrsps.game.script

/** What a player can click an option on. */
enum class OpTarget {
    /** A loc (object) in the world, by loc type id. */
    LOC,

    /** An NPC, by NPC type id. */
    NPC,

    /** An item (obj) in the inventory, by obj type id. */
    OBJ,

    /** An interface component, by [Op.button]'s packed id. */
    BUTTON,
}

/**
 * One click on an option: [option] is 1 to 5 for locs, NPCs and items (op1 to op5, as in the cache's option lists) and
 * 1 to 10 for buttons. [sub] is the button's slot (an inventory slot, a dropdown entry), -1 when there is none.
 */
data class Op(val target: OpTarget, val id: Int, val option: Int, val sub: Int = -1) {
    val interfaceId: Int get() = id ushr 16
    val componentId: Int get() = id and 0xFFFF

    override fun toString(): String = when (target) {
        OpTarget.BUTTON -> "button $interfaceId:$componentId op $option sub $sub"
        else -> "${target.name.lowercase()} $id op $option"
    }

    companion object {
        fun button(interfaceId: Int, componentId: Int, option: Int, sub: Int = -1) =
            Op(OpTarget.BUTTON, packButton(interfaceId, componentId), option, sub)

        fun packButton(interfaceId: Int, componentId: Int): Int {
            require(interfaceId in 0..0xFFFF && componentId in 0..0xFFFF) { "Bad component $interfaceId:$componentId" }
            return (interfaceId shl 16) or componentId
        }
    }
}

/** A handler gets the click that started it, for the button's slot or the option that was picked. */
typealias Handler = suspend ScriptContext.(Op) -> Unit

/**
 * Content handlers, by what was clicked and which option. A handler registered for [ANY_OPTION] catches every option
 * that has no handler of its own.
 *
 * Only clicks on buttons reach handlers so far; locs come with task 11, items with task 13 and NPCs with task 17, each
 * after walking to the target.
 */
class Handlers {
    private val handlers = HashMap<Key, Handler>()

    private data class Key(val target: OpTarget, val id: Int, val option: Int)

    fun onLoc(id: Int, option: Int, handler: Handler) = register(OpTarget.LOC, id, option, handler)

    fun onNpc(id: Int, option: Int, handler: Handler) = register(OpTarget.NPC, id, option, handler)

    fun onObj(id: Int, option: Int, handler: Handler) = register(OpTarget.OBJ, id, option, handler)

    fun onButton(interfaceId: Int, componentId: Int, option: Int = ANY_OPTION, handler: Handler) =
        register(OpTarget.BUTTON, Op.packButton(interfaceId, componentId), option, handler)

    fun register(target: OpTarget, id: Int, option: Int, handler: Handler) {
        val maxOption = if (target == OpTarget.BUTTON) MAX_BUTTON_OPTION else MAX_OPTION
        require(option == ANY_OPTION || option in 1..maxOption) { "Bad option $option for a ${target.name.lowercase()}" }
        require(id >= 0) { "Bad id $id" }
        val key = Key(target, id, option)
        require(key !in handlers) { "${Op(target, id, option)} already has a handler" }
        handlers[key] = handler
    }

    fun find(op: Op): Handler? = handlers[Key(op.target, op.id, op.option)] ?: handlers[Key(op.target, op.id, ANY_OPTION)]

    companion object {
        const val ANY_OPTION = 0
        const val MAX_OPTION = 5
        const val MAX_BUTTON_OPTION = 10
    }
}
