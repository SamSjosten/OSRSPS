package osrsps.game.content

import osrsps.cache.LocTypeEncoder
import osrsps.game.World
import osrsps.game.zone.ZoneLoc
import osrsps.map.LocShape

/**
 * Lodestones, custom content after RS3's lodestone network: a stone at each major town that a player activates once
 * and can then teleport to. This is step 4 of the plan, the stone itself: two loc types of our own, dormant and
 * active, drawn with the models converted from the RS3 rip (`data/models/65000.dat` and `65001.dat` on the server),
 * and the Lumbridge stone placed for Sam to judge the look in game. Activating, the per-player state and the
 * teleports come with the rules step; until then Activate shows the active stone to everyone for [PREVIEW_TICKS], to
 * show both looks, and Teleport does nothing.
 */
object Lodestones {
    const val DORMANT = 65000
    const val ACTIVE = 65001
    const val DORMANT_MODEL = 65000
    const val ACTIVE_MODEL = 65001
    const val ACTIVATE = 1
    const val TELEPORT = 1

    /** How long Activate shows the active stone: 30 seconds. */
    const val PREVIEW_TICKS = 50

    /** The stone is 3 tiles across and flat, so players walk over it and arrive on it, as in RS3. */
    private fun type(model: Int, op: String) = LocTypeEncoder.Definition(
        "Lodestone", listOf(model), ops = listOf(op, null, null, null, null), width = 3, length = 3, solid = false,
    )

    val DORMANT_TYPE = CustomLocs.Custom(DORMANT, type(DORMANT_MODEL, "Activate"))
    val ACTIVE_TYPE = CustomLocs.Custom(ACTIVE, type(ACTIVE_MODEL, "Teleport"))

    /**
     * The Lumbridge stone, by its south-west tile: centred on 3233, 3221, outside the castle's east gate on the way to
     * the bridge, where RS3 has it. Guessed from RS3 server sources (the RS3 Wiki's page gives no coordinates);
     * `::pos` on the spot and a change here move it.
     */
    val LUMBRIDGE = ZoneLoc(DORMANT, LocShape.CENTREPIECE_STRAIGHT, 0, 3232, 3220, 0)

    val ALL = listOf(LUMBRIDGE)
}

/** A world whose loc types don't include the lodestone (a test's world) gets no stones: the server's always do. */
fun registerLodestones(world: World) {
    if (world.locs.types[Lodestones.DORMANT] != null) for (stone in Lodestones.ALL) world.locs.add(stone)
    world.handlers.onLoc(Lodestones.DORMANT, Lodestones.ACTIVATE) { op ->
        op.loc?.let { world.locs.replace(it, Lodestones.ACTIVE, revertAfter = Lodestones.PREVIEW_TICKS) }
    }
}
