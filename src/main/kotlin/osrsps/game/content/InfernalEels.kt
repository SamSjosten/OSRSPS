package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.QueueType
import osrsps.game.script.ScriptContext

/** The infernal eel's words, in the dialogue file. */
object InfernalEelLines : Lines("infernal_eel", "Infernal eel") {
    val EAT = line("eat", "the message on the eel's Eat (the Wiki's Infernal eel page)")
    val CRACKED = line("cracked", "the message as a hammer cracks one open (keys only: the Wiki gives no words)", "item")
}

/**
 * Cracking infernal eels (Karamja step K2.4), as the Wiki's Infernal eel page has it (read 2026-11-18): a hammer used
 * on one cracks the first eel in the backpack, and then the next, until none are left; each gives 14 to 20 Tokkul
 * (29 in 35), an onyx bolt tip (2 in 35) or 1 to 5 lava scale shards (4 in 35). Eat says it's too hard. The eels are
 * caught in Mor Ul Rek's inner city (Fishing's [osrsps.game.skill.Fishing.INFERNAL_EEL_BAIT]).
 *
 * Each crack after the first is a weak queue entry, so any other action stops it. Guessed: a crack every [CRACK_TICKS]
 * ticks, the animation (the hammer's [ANIM_CRACK]) and the message. Out: the quicker smashing a player gets by clicking
 * again.
 */
object InfernalEels {
    const val EEL = 21293
    const val HAMMER = 2347
    const val EAT = 4
    const val TOKKUL = 6529
    const val ONYX_BOLT_TIPS = 9194
    const val LAVA_SCALE_SHARD = 11994
    const val CRACK_TICKS = 2

    /** human_smithing: a hammer swung. */
    const val ANIM_CRACK = 898

    /** What one eel gives: the obj and how many, from a roll of 0 until 35 and a second roll for the count. */
    fun loot(roll: Int, between: (Int, Int) -> Int): Pair<Int, Int> = when {
        roll < 29 -> TOKKUL to between(14, 20)
        roll < 31 -> ONYX_BOLT_TIPS to 1
        else -> LAVA_SCALE_SHARD to between(1, 5)
    }

    /**
     * Cracks the first eel, then queues the next [CRACK_TICKS] later as a weak queue entry, so walking off, eating or
     * anything else the player starts stops the cracking, as Making's products do.
     */
    internal fun ScriptContext.crackAll(world: World) {
        if (player.inventory.count(HAMMER) <= 0) return
        val slot = player.inventory.contents().firstOrNull { it.second.id == EEL }?.first ?: return
        player.anim(ANIM_CRACK)
        player.inventory.remove(slot, 1)
        val (obj, count) = loot(world.random.nextInt(35)) { min, max -> world.random.between(min, max) }
        val type = world.objTypes[obj] ?: return
        world.groundObjs.give(player, type, count)
        world.message(player, InfernalEelLines.CRACKED, "item" to type.name)
        if (player.inventory.count(EEL) > 0) queue(QUEUE, QueueType.WEAK, CRACK_TICKS) { crackAll(world) }
    }

    const val QUEUE = "infernal_eels"
}

fun registerInfernalEels(world: World) {
    with(InfernalEels) {
        world.handlers.onObjOnObj(HAMMER, EEL) { crackAll(world) }
        world.handlers.onObj(EEL, EAT) { world.message(player, InfernalEelLines.EAT) }
    }
}
