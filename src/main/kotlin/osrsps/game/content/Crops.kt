package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/** Picking's messages, 2004Scape's and RSMod's (the same), not checked against live. */
object CropLines : Lines("crop", "Onion") {
    val ONION_PICKED = line("onion_picked", "an onion picked")
    val ONION_NO_ROOM = line("onion_no_room", "Pick on an onion with a full inventory")
    val POTATO_PICKED = line("potato_picked", "a potato picked (the Potato page)")
    val POTATO_NO_ROOM = line("potato_no_room", "Pick on a potato with a full inventory")
    val GRAIN_PICKED = line("grain_picked", "wheat picked (the Wheat page)")
    val GRAIN_NO_ROOM = line("grain_no_room", "Pick on wheat with a full inventory")
}

/**
 * Crops that grow wild in fields and are picked by hand: onions on Fred's farm, potatoes west of Mill Lane Mill, and
 * wheat around the mill and elsewhere. Pick (op2) takes the crop for a while and gives one of it.
 *
 * How a pick plays out is the 2004Scape project's pickables script, not checked against live: with the inventory full
 * only a message; otherwise the human_pickupfloor animation, a tick, and then the crop goes and the player gets it. The
 * messages are 2004Scape's and RSMod's (the same words). Respawn times: the OSRS Wiki, Onion and Wheat (50 and 20
 * ticks); potato 50 is 2004Scape's and RSMod's, the Wiki gives none. Left out, as the Wiki gives no chance: the seeds a
 * field sometimes gives. Guessed: that live also moves a player with a full inventory onto the crop's tile, as
 * 2004Scape does, is left out.
 */
object Crops {
    class Crop(
        /** The loc types that are this crop. */
        val locs: List<Int>,
        val item: Int,
        val respawnTicks: Int,
        val picked: Line,
        val noRoom: Line,
    )

    const val PICK = 2

    /** human_pickupfloor. */
    const val ANIM_PICK = 827

    const val ONION_LOC = 3366
    const val ONION = 1957
    const val POTATO_LOC = 312
    const val POTATO = 1942

    /** wheat, and the 241 cache's three fai_varrock_wheat types that the Lumbridge fields use. */
    val WHEAT_LOCS = listOf(313, 15506, 15507, 15508)
    const val GRAIN = 1947

    val ONIONS = Crop(listOf(ONION_LOC), ONION, 50, CropLines.ONION_PICKED, CropLines.ONION_NO_ROOM)
    val POTATOES = Crop(listOf(POTATO_LOC), POTATO, 50, CropLines.POTATO_PICKED, CropLines.POTATO_NO_ROOM)
    val WHEAT = Crop(WHEAT_LOCS, GRAIN, 20, CropLines.GRAIN_PICKED, CropLines.GRAIN_NO_ROOM)

    val ALL = listOf(ONIONS, POTATOES, WHEAT)
}

fun registerCrops(world: World) {
    for (crop in Crops.ALL) {
        for (loc in crop.locs) world.handlers.onLoc(loc, Crops.PICK) { op -> pick(world, op.loc!!, crop) }
    }
}

private suspend fun ScriptContext.pick(world: World, loc: ZoneLoc, crop: Crops.Crop) {
    if (player.inventory.freeSlots == 0) {
        world.message(player, crop.noRoom)
        return
    }
    player.anim(Crops.ANIM_PICK)
    delay(1)
    // Someone else may have picked it this tick.
    if (!world.locs.exists(loc)) return
    world.locs.remove(loc, crop.respawnTicks)
    world.objTypes[crop.item]?.let { player.inventory.add(it, 1) }
    world.message(player, crop.picked)
}
