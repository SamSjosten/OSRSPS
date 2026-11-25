package osrsps.game.command

import osrsps.game.World
import osrsps.game.trail.TrailTier

/**
 * `::clue` shows the typing player's trails under way; `::clue <tier> [step]` gives them a clue scroll of that tier on
 * a new trail, at the step named (`map:107`, as `::clue` and saves name steps) or a random built one; `::clue <tier>
 * steps` lists the tier's built steps with their tiles; `::clue <tier> done <count>` sets the caskets of the tier
 * opened (so the next casket's milestone can be tried); `::clue clear` drops every trail, and `::clue reset` the
 * counts and opened scroll cases too; `::clue lucky on|off` makes every skilling clue roll and casket master clue roll hit for the typing player.
 * Tests and runner scripts start a trail anywhere with it.
 */
fun CommandRegistry.registerTrailCommands(world: World) {
    val usage = "::clue [<tier> [step | steps | done <count>] | clear | reset | lucky on|off]"
    register("clue", usage) {
        val trails = world.trails
        val first = args.firstOrNull()?.lowercase()
        when {
            first == null -> {
                val all = player.trails.all()
                if (all.isEmpty()) reply("No trail.")
                for ((tier, trail) in all) reply("${tier.key}: ${trail.step}, ${trail.done.size} of ${trail.length} done.")
            }
            first == "clear" -> {
                for (tier in TrailTier.entries) player.trails[tier] = null
                reply("No trail.")
            }
            first == "lucky" -> {
                val on = when (args.getOrNull(1)?.lowercase()) {
                    "on" -> true
                    "off" -> false
                    else -> fail("Usage: $usage")
                }
                if (on) trails.skilling.lucky += player.saveKey else trails.skilling.lucky -= player.saveKey
                reply(if (on) "Every skilling and casket clue roll hits." else "Skilling and casket clue rolls as the Wiki's.")
            }
            first == "reset" -> {
                player.trails.replaceWith(osrsps.game.trail.TrailProgress())
                reply("No trail, no caskets opened, no scroll cases.")
            }
            else -> {
                val tier = TrailTier.byKey(first) ?: fail("Usage: $usage")
                val arg = args.getOrNull(1)
                if (arg == "done") {
                    val count = args.getOrNull(2)?.toIntOrNull()?.takeIf { it >= 0 } ?: fail("Usage: $usage")
                    player.trails.setCompleted(tier, count)
                    reply("${tier.key}: $count caskets opened, cap ${trails.cap(player, tier)}.")
                    return@register
                }
                if (arg == "steps") {
                    val pool = trails.pool(tier)
                    reply("${pool.size} ${tier.key} steps built: " + pool.joinToString { "${it.key} ${it.targets.first().tile}" })
                    return@register
                }
                val step = arg?.let { key -> trails.data[key] ?: fail("No step '$key'") }
                if (step != null && step.tier != tier) fail("$step isn't ${tier.key}")
                if (trails.holdsClue(player, tier)) fail("Already holding a ${tier.key} clue")
                val item = trails.start(player, tier, step) ?: fail("No ${tier.key} steps built")
                val type = world.objTypes[item] ?: fail("No item $item")
                if (player.inventory.add(type, 1) == 0) {
                    player.trails[tier] = null
                    fail("No room")
                }
                val trail = player.trails[tier]!!
                reply("Clue (${tier.key}): ${trail.step}, ${trail.length} steps.")
            }
        }
    }
}
