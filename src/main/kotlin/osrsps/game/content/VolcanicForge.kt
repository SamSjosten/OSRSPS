package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.InvObj
import osrsps.game.World
import osrsps.game.dialogue.Lines

/** The Volcanic Forge's words (keys only; the words are in data/dialogue.txt). */
object VolcanicForgeLines : Lines("volcanic_forge", "Volcanic Forge") {
    val FORGED = line("forged", "a shard used on the forge with all three of its ward's shards in the backpack (the Wiki's Volcanic Forge)")
}

/**
 * The Volcanic Forge at the Blighted Volcano (Wilderness step W4.4b; the Wiki's Volcanic Forge, Malediction ward and
 * Odium ward, read 2026-11-15): `wilderness_volcanoforge` 26755, 2 tiles at 3366,3937. A ward's shard used on it with
 * all three of that ward's shards in the backpack (shard 1 from the Chaos Fanatic, 2 from the crazy archaeologist, 3
 * from Scorpia) forges them into the ward.
 *
 * GUESSED until recorded: the usual nothing-happens message for a shard without the other two, and for anything else used
 * on it; no animation, and the player not moved by the blowing back the message tells of. Left out: the gold ring's goblin.
 */
object VolcanicForge {
    const val FORGE = 26755

    /** A ward and its three shards, 1 to 3. */
    class Ward(val ward: Int, val shards: List<Int>)

    val MALEDICTION = Ward(11924, listOf(11931, 11932, 11933))
    val ODIUM = Ward(11926, listOf(11928, 11929, 11930))
    val WARDS = listOf(MALEDICTION, ODIUM)

    fun register(world: World) {
        world.handlers.onObjOnLoc(FORGE) { op ->
            // The arrival first, then the shards' slots, so nothing moved in between is used (exploit review round 39).
            arriveDelay()
            val ward = WARDS.firstOrNull { op.obj in it.shards }
            val inv = player.inventory
            val slots = ward?.shards?.map { shard -> inv.contents().firstOrNull { it.second.id == shard }?.first }
            if (ward == null || slots == null || slots.any { it == null }) return@onObjOnLoc world.message(player, GameLines.NOTHING_INTERESTING)
            for (slot in slots) inv.remove(slot!!, 1)
            inv[slots.first()!!] = InvObj(ward.ward, 1)
            world.message(player, VolcanicForgeLines.FORGED)
        }
    }
}
