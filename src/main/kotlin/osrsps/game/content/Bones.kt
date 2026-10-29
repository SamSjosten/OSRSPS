package osrsps.game.content

import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.script.ScriptContext

/**
 * Burying bones: Bury (iop1) on bones gives Prayer XP.
 *
 * XP from each item's OSRS Wiki page (read 2026-10-29). How a bury plays out is the 2004Scape project's script (RS2 as
 * of 2004), not checked against live: the first message, the animation and the player stopping where they are, a
 * one-tick delay (clicks meanwhile do nothing, so a bury every two ticks at best), and then the bones go, the XP comes
 * and the second message shows. 2004Scape plays synth bones_down too; its 241 id isn't known yet, so no sound is sent.
 * Bones that only other pieces drop come with those pieces.
 */
object Bones {
    /** Prayer XP in tenths, by obj type id (gameval names in the comments). */
    val XP_TENTHS: Map<Int, Int> = mapOf(
        526 to 45, // bones
        528 to 45, // bones_burnt
        532 to 150, // big_bones
        536 to 720, // dragon_bones
    )

    const val BURY = 1

    /** human_pickupfloor. */
    const val ANIM_BURY = 827

    const val DIG = "You dig a hole in the ground..."
    const val BURIED = "You bury the bones."
}

fun registerBones(world: World) {
    for ((id, xp) in Bones.XP_TENTHS) {
        val type = world.objTypes[id] ?: continue
        if (type.iop(Bones.BURY) != "Bury") continue
        world.handlers.onObj(id, Bones.BURY) { op -> bury(op.sub, id, xp) }
    }
}

private suspend fun ScriptContext.bury(slot: Int, id: Int, xp: Int) {
    player.message(Bones.DIG)
    player.anim(Bones.ANIM_BURY)
    player.interaction = null
    player.waypoints.clear()
    delay(1)
    // Clicks are refused while delayed, so only something like a death or a ::reset can have emptied the slot.
    if (player.inventory[slot]?.id != id) return
    player.inventory.remove(slot, 1)
    player.skills.addTenths(Skill.PRAYER, xp)
    player.message(Bones.BURIED)
}
