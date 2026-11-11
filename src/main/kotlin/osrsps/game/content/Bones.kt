package osrsps.game.content

import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** Burying's messages, the 2004Scape project's, not checked against live. */
object BonesLines : Lines("bones", "Bones") {
    val DIG = line("dig", "Bury, at once")
    val BURIED = line("buried", "Bury, a tick later as the bones go")
    val SCATTERED = line("scattered", "Scatter on demonic ashes, a tick later as the ashes go; words and timing guessed")
}

/**
 * Burying bones: Bury (iop1) on bones gives Prayer XP.
 *
 * XP from each item's OSRS Wiki page (read 2026-10-29). How a bury plays out is the 2004Scape project's script (RS2 as
 * of 2004), not checked against live: the first message, the animation and the player stopping where they are, a
 * one-tick delay (clicks meanwhile do nothing, so a bury every two ticks at best), and then the bones go, the XP comes
 * and the second message shows. 2004Scape plays synth bones_down too; its 241 id isn't known yet, so no sound is sent.
 * Bones that only other pieces drop come with those pieces.
 *
 * Demonic ashes scatter (iop1) for Prayer XP the same way; the Wiki gives the XP, while the animation, the one-tick
 * delay and the message are guessed.
 */
object Bones {
    /** Prayer XP in tenths, by obj type id (gameval names in the comments). */
    val XP_TENTHS: Map<Int, Int> = mapOf(
        526 to 45, // bones
        528 to 45, // bones_burnt
        532 to 150, // big_bones
        536 to 720, // dragon_bones
    )

    /**
     * The demonic ashes' Prayer XP in tenths, by obj type id: Remains#Demonic ashes and each ashes' page on the OSRS
     * Wiki (read 2026-11-10). Plain ashes (592) have no Scatter and give nothing.
     */
    val ASHES_XP_TENTHS: Map<Int, Int> = mapOf(
        25766 to 100, // fiendish_ashes
        25769 to 250, // vile_ashes
        25772 to 650, // malicious_ashes
        25775 to 850, // abyssal_ashes
        25778 to 1100, // infernal_ashes
    )

    const val BURY = 1

    /** Scatter, the ashes' iop1. */
    const val SCATTER = 1

    /** farming_ingredient_sprinkle; guessed, as the cache names no scatter animation. */
    const val ANIM_SCATTER = 2295

    /** human_pickupfloor. */
    const val ANIM_BURY = 827
}

fun registerBones(world: World) {
    for ((id, xp) in Bones.XP_TENTHS) {
        val type = world.objTypes[id] ?: continue
        if (type.iop(Bones.BURY) != "Bury") continue
        world.handlers.onObj(id, Bones.BURY) { op -> bury(world, op.sub, id, xp) }
    }
    for ((id, xp) in Bones.ASHES_XP_TENTHS) {
        val type = world.objTypes[id] ?: continue
        if (type.iop(Bones.SCATTER) != "Scatter") continue
        world.handlers.onObj(id, Bones.SCATTER) { op -> scatter(world, op.sub, id, xp) }
    }
}

/** Scatter: as a bury (one tick, then the ashes go and the XP comes), with one message at the end; guessed. */
private suspend fun ScriptContext.scatter(world: World, slot: Int, id: Int, xp: Int) {
    player.anim(Bones.ANIM_SCATTER)
    player.interaction = null
    player.waypoints.clear()
    delay(1)
    if (player.inventory[slot]?.id != id) return
    player.inventory.remove(slot, 1)
    player.skills.addTenths(Skill.PRAYER, xp)
    world.message(player, BonesLines.SCATTERED)
}

private suspend fun ScriptContext.bury(world: World, slot: Int, id: Int, xp: Int) {
    world.message(player, BonesLines.DIG)
    player.anim(Bones.ANIM_BURY)
    player.interaction = null
    player.waypoints.clear()
    delay(1)
    // Clicks are refused while delayed, so only something like a death or a ::reset can have emptied the slot.
    if (player.inventory[slot]?.id != id) return
    player.inventory.remove(slot, 1)
    player.skills.addTenths(Skill.PRAYER, xp)
    world.message(player, BonesLines.BURIED)
}
