package osrsps.game.script

import osrsps.game.zone.ZoneLoc
import osrsps.game.zone.ZoneObj

/** What a player can click an option on. */
enum class OpTarget {
    /** A loc (object) in the world, by loc type id. */
    LOC,

    /** An NPC, by NPC type id. */
    NPC,

    /** An item (obj) in the inventory, by obj type id. */
    OBJ,

    /** An item (obj) on the ground, by obj type id, once the player has reached it. */
    GROUND_OBJ,

    /** An interface component, by [Op.button]'s packed id. */
    BUTTON,

    /** A worn item's own options (a ring's teleports), by obj type id, with the equipment slot as [Op.sub]. */
    WORN,
}

/**
 * One click on an option: [option] is 1 to 5 for locs, NPCs and items (op1 to op5, as in the cache's option lists), 1 to
 * 8 for worn items and 1 to 10 for buttons. [sub] is the button's slot (an inventory slot, a dropdown entry), -1 when there is none.
 * [loc] is the loc clicked, for a loc op; [groundObj] the obj clicked on the ground, for [OpTarget.GROUND_OBJ].
 */
data class Op(
    val target: OpTarget,
    val id: Int,
    val option: Int,
    val sub: Int = -1,
    val loc: ZoneLoc? = null,
    val groundObj: ZoneObj? = null,
) {
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
 * Clicks on buttons, on inventory items (iop1 to iop5, with the slot as [Op.sub]) and on worn items' own options reach
 * handlers straight away; clicks on locs and ground objs once the player has walked to them (see
 * [osrsps.game.Interaction]). NPCs come with task 17, also after walking to them.
 */
class Handlers {
    private val handlers = HashMap<Key, Handler>()

    private data class Key(val target: OpTarget, val id: Int, val option: Int)

    fun onLoc(id: Int, option: Int, handler: Handler) = register(OpTarget.LOC, id, option, handler)

    fun onNpc(id: Int, option: Int, handler: Handler) = register(OpTarget.NPC, id, option, handler)

    fun onObj(id: Int, option: Int, handler: Handler) = register(OpTarget.OBJ, id, option, handler)

    /** An option on obj [id] on the ground. A handler for op3 replaces the built-in Take for that obj. */
    fun onGroundObj(id: Int, option: Int, handler: Handler) = register(OpTarget.GROUND_OBJ, id, option, handler)

    /** A worn item's option [option], 1 to 8: the item's params 451 to 458, which the equipment tab shows after Remove. */
    fun onWorn(id: Int, option: Int, handler: Handler) = register(OpTarget.WORN, id, option, handler)

    fun onButton(interfaceId: Int, componentId: Int, option: Int = ANY_OPTION, handler: Handler) =
        register(OpTarget.BUTTON, Op.packButton(interfaceId, componentId), option, handler)

    fun register(target: OpTarget, id: Int, option: Int, handler: Handler) {
        val maxOption = when (target) {
            OpTarget.BUTTON -> MAX_BUTTON_OPTION
            OpTarget.WORN -> MAX_WORN_OPTION
            else -> MAX_OPTION
        }
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
        const val MAX_WORN_OPTION = 8
    }
}
