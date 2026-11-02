package osrsps.game.command

import osrsps.game.World
import osrsps.game.content.Lodestones

/**
 * `::lodestone` opens the lodestone teleport menu from anywhere, and `::lodestone <site>` starts the teleport to that
 * stone: the network's own rules apply (only activated stones, not in combat, not above level 20 Wilderness, the full
 * cast), so it's for every player, as RS3's Home Teleport opens the network from anywhere.
 */
fun CommandRegistry.registerLodestoneCommands(world: World) {
    val slugs = Lodestones.SITES.joinToString("|") { it.slug }
    register("lodestone", "::lodestone [$slugs]", admin = false) {
        // A non-admin command runs for a dead player; this one would move the body.
        if (player.dead) fail("You can't use ::lodestone while you are dead.")
        if (args.size > 1) fail("Usage: ::lodestone [$slugs]")
        val site = args.firstOrNull()?.let { Lodestones.site(it) ?: fail("No lodestone called '$it': ::lodestone [$slugs]") }
        world.scripts.start(player) {
            with(world.lodestones) { if (site == null) open() else teleportTo(site) }
        }
    }
}
