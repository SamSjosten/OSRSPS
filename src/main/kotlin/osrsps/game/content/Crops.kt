package osrsps.game.content

import osrsps.game.World
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

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
        val picked: String,
        val noRoom: String,
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

    val ONIONS = Crop(listOf(ONION_LOC), ONION, 50, "You pick an onion.", "You don't have room for this onion.")
    val POTATOES = Crop(listOf(POTATO_LOC), POTATO, 50, "You pick a potato.", "You don't have room for this potato.")
    val WHEAT = Crop(WHEAT_LOCS, GRAIN, 20, "You pick some grain.", "You can't carry any more grain.")

    val ALL = listOf(ONIONS, POTATOES, WHEAT)
}

fun registerCrops(world: World) {
    for (crop in Crops.ALL) {
        for (loc in crop.locs) world.handlers.onLoc(loc, Crops.PICK) { op -> pick(world, op.loc!!, crop) }
    }
}

private suspend fun ScriptContext.pick(world: World, loc: ZoneLoc, crop: Crops.Crop) {
    if (player.inventory.freeSlots == 0) {
        player.message(crop.noRoom)
        return
    }
    player.anim(Crops.ANIM_PICK)
    delay(1)
    // Someone else may have picked it this tick.
    if (!world.locs.exists(loc)) return
    world.locs.remove(loc, crop.respawnTicks)
    world.objTypes[crop.item]?.let { player.inventory.add(it, 1) }
    player.message(crop.picked)
}
