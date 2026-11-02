package osrsps.game.command

import osrsps.game.World
import osrsps.game.content.Lodestones

/**
 * `::lodestone` opens the lodestone map from anywhere, and `::lodestone <site>` starts the teleport to that
 * stone: the network's own rules apply (only activated stones, not in combat, not above level 20 Wilderness, the full
 * cast), so it's for every player, as RS3's Home Teleport opens the network from anywhere.
 */
fun CommandRegistry.registerLodestoneCommands(world: World) {
    val slugs = Lodestones.SITES.joinToString("|") { it.slug }
    register("lodestone", "::lodestone [$slugs]", admin = false) {
        // A command skips the click path's checks, so this one makes its own: a dead player's body would move, and
        // with an interface open or mid-action the cast's weak queue couldn't run (closing the interface would clear it).
        if (player.dead) fail("You can't use ::lodestone while you are dead.")
        if (world.scripts.busy(player)) fail("You can't use ::lodestone with an interface open or in the middle of something.")
        if (args.size > 1) fail("Usage: ::lodestone [$slugs]")
        val site = args.firstOrNull()?.let { Lodestones.site(it) ?: fail("No lodestone called '$it': ::lodestone [$slugs]") }
        if (site == null) world.lodestones.open(player) else world.scripts.start(player) { with(world.lodestones) { teleportTo(site) } }
    }
}
