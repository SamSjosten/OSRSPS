package osrsps.game.skill.construction

import osrsps.game.World
import osrsps.game.content.arriveDelay

/**
 * Construction step CON4 (the workshop): what its built furniture does, from the Wiki's Workshop and Tool store pages
 * (read 2026-11-25). Building it, and upgrading a piece over the one before it, is [Furniture]'s.
 *
 * - Tool stores: Search on a rack gives its store's tools, free and without end, as the kitchen's larders do
 *   ([takeSupplies]). Each store's list is the Tool store page's. GUESSED: the watering can is the full one
 *   (`watering_can_8`; the page says only "watering can").
 *
 * Left out for now: the workbenches' Work-at (flatpacks), the clockmaker's bench's Craft, the repair benches and the
 * heraldry stands (they need flatpacks, toys and the family crests).
 */
class Workshop(private val world: World, private val houses: Houses) {
    fun register() {
        for ((loc, tools) in TOOLS) {
            world.handlers.onLoc(loc, Kitchen.SEARCH) { op ->
                arriveDelay()
                takeSupplies(world, houses, this, op.loc ?: return@onLoc, tools)
            }
        }
    }

    companion object {
        // By the cache's names: saw, hammer, chisel, shears; bucket, knife, spade, tinderbox; brown apron, glassblowing
        // pipe, needle; amulet, necklace, ring, holy, bracelet and tiara moulds; rake, spade, gardening trowel, seed dibber,
        // watering can(8), secateurs.
        val TOOLS: Map<Int, List<Int>> = mapOf(
            FurnitureBuilds.TOOLS_1 to listOf(8794, 2347, 1755, 1735),
            FurnitureBuilds.TOOLS_1 + 1 to listOf(1925, 946, 952, 590),
            FurnitureBuilds.TOOLS_1 + 2 to listOf(1757, 1785, 1733),
            FurnitureBuilds.TOOLS_1 + 3 to listOf(1595, 1597, 1592, 1599, 11065, 5523),
            FurnitureBuilds.TOOLS_1 + 4 to listOf(5341, 952, 5325, 5343, 5340, 5329),
        )
    }
}
