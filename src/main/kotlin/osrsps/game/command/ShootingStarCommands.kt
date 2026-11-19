package osrsps.game.command

import osrsps.game.World
import osrsps.game.skill.ShootingStars
import osrsps.game.skill.StarRegion
import osrsps.game.skill.StarSite
import osrsps.game.skill.Telescope

/**
 * `::star` for testing shooting stars ([ShootingStars]). With `::seed n` set first, the sites, sizes and timings the
 * cycle picks repeat run to run.
 *
 * - `::star` says where the star is (site, size, layer mined) and when and where the next lands.
 * - `::star sites` lists the sites.
 * - `::star land [site|here] [size]` lands one now: at a site (a random one when left out), or `here`, its south-west
 *   tile two tiles north of the player; size 1 to 9 (random 6 to 9 when left out).
 * - `::star next ticks` plans the next landing that many ticks from now.
 * - `::star layer` ends the current layer now; `::star clear` takes the star away.
 * - `::star bonus [n]` shows or sets the player's own discovery bonus.
 * - `::star scout oak|teak|mahogany` looks through that telescope, as the study's will (Construction).
 */
fun CommandRegistry.registerShootingStarCommands(world: World) {
    val stars = world.shootingStars
    val usage = "Usage: ::star [sites | land [site|here] [size] | next ticks | layer | clear | bonus [n] | scout oak|teak|mahogany]"

    register("star", "::star [sites|land|next|layer|clear|bonus|scout]", admin = true) {
        when (args.firstOrNull()?.lowercase()) {
            null -> {
                val star = stars.star
                val now = if (star == null) "No star has landed." else
                    "A size-${star.size} star at ${star.site.name} (${star.site.x}, ${star.site.z}), ${star.percent}% of the layer mined" +
                        (if (star.landing) ", landing" else "") + "."
                val next = if (stars.nextLanding < 0) " No next star is planned." else {
                    val ticks = stars.nextLanding - world.tick
                    " The next lands in $ticks ticks (${ticks * 600 / 60_000} minutes) at ${stars.nextSite?.name}."
                }
                reply(now + next)
            }
            "sites" -> reply(stars.sites.all.joinToString { "${it.key} (${it.x}, ${it.z})" })
            "land" -> {
                if (args.size > 3) fail(usage)
                val where = args.getOrNull(1)?.lowercase()
                val site = when (where) {
                    null -> world.random.pick(stars.sites.rolled)
                    "here" -> StarSite("here", "here", HERE, player.x, player.z + 2)
                    else -> stars.sites[where] ?: fail("No star site '$where'. ::star sites lists them")
                }
                if (player.level != 0) fail("Stars land on the ground floor")
                val size = args.getOrNull(2)?.let { it.toIntOrNull()?.takeIf { s -> s in 1..ShootingStars.MAX_SIZE } ?: fail(usage) }
                    ?: ShootingStars.SIZE_WEIGHTS.keys.let { world.random.pick(it.toList()) }
                world.scripts.interrupt(player)
                stars.land(site, size, world.tick)
                reply("A size-$size star lands at ${site.name} (${site.x}, ${site.z}).")
            }
            "next" -> {
                val ticks = args.getOrNull(1)?.toIntOrNull()?.takeIf { it >= 1 && args.size == 2 } ?: fail(usage)
                stars.schedule(world.tick, ticks)
                reply("The next star lands in $ticks ticks at ${stars.nextSite?.name}.")
            }
            "layer" -> {
                val star = stars.star ?: fail("No star has landed.")
                stars.finishLayer(world.tick)
                reply(if (star.size >= 1) "The star is size ${star.size} now." else "The star has disintegrated.")
            }
            "clear" -> {
                if (stars.star == null) fail("No star has landed.")
                stars.clear()
                reply("The star is gone.")
            }
            "bonus" -> {
                if (args.size == 1) {
                    reply("Your star discovery bonus: ${stars.bonus(player)} dust.")
                    return@register
                }
                val n = args.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..ShootingStars.DISCOVERY_BONUS && args.size == 2 } ?: fail(usage)
                stars.setBonus(player, n)
                reply("Your star discovery bonus is $n dust.")
            }
            "scout" -> {
                val telescope = args.getOrNull(1)?.takeIf { args.size == 2 }
                    ?.let { name -> Telescope.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } } ?: fail(usage)
                world.scripts.interrupt(player)
                world.scripts.start(player) { with(stars) { lookThrough(telescope) } }
            }
            else -> fail(usage)
        }
    }
}

/** The region of `::star land here`: no telescope sees it. */
private val HERE = StarRegion("here", "here")
