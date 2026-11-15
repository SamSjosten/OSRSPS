package osrsps.game.command

import osrsps.game.World
import osrsps.game.skill.SlayerMaster

/**
 * `::slayer` shows the typing player's task, streak and points; `::slayer task <task> [amount] [master]` gives them a
 * task straight away (by its id or its name, as the cache words it: `cows`, `rockslugs`), `::slayer points n` and
 * `::slayer streak n` set those, and `::slayer clear` takes the task away, so a test starts anywhere in a task.
 */
fun CommandRegistry.registerSlayerCommands(world: World) {
    val usage = "::slayer [task <id|name> [amount] [master] | points n | streak n | clear]"
    register("slayer", usage) {
        val slayer = world.slayer
        when (args.firstOrNull()?.lowercase()) {
            null -> {
                val task = slayer.task(player)
                val what = if (task == null) "No task" else "${slayer.remaining(player)} ${task.nameLower} (task ${task.id}) from ${slayer.master(player)?.name?.lowercase() ?: "no master"}"
                reply("$what; streak ${slayer.streak(player)}, ${slayer.points(player)} points.")
            }
            "task" -> {
                if (args.size < 2) fail("Usage: $usage")
                // The name may be several words ("fever spiders"): an amount and a master come after it, if given.
                var end = args.size
                val master = args.last().let { last -> SlayerMaster.entries.firstOrNull { it.name.equals(last, ignoreCase = true) } }
                    ?.also { end-- } ?: SlayerMaster.AYA
                val amount = args.getOrNull(end - 1)?.toIntOrNull()?.takeIf { end > 2 }?.also { end-- }
                val name = args.subList(1, end).joinToString(" ")
                val task = name.toIntOrNull()?.let { slayer.data.tasks[it] }
                    ?: slayer.data.tasks.values.firstOrNull { it.nameLower.equals(name, ignoreCase = true) }
                    ?: fail("No Slayer task '$name'")
                val count = amount ?: slayer.data.entry(master.id, task.id)?.max ?: 15
                if (count !in 1..MAX_AMOUNT) fail("A task is 1 to $MAX_AMOUNT kills")
                slayer.assign(player, master, task, count)
                reply("Task: $count ${task.nameLower} from ${master.name.lowercase()}.")
            }
            "points", "streak" -> {
                val value = args.getOrNull(1)?.toIntOrNull()?.takeIf { args.size == 2 && it >= 0 } ?: fail("Usage: $usage")
                if (args[0].equals("points", ignoreCase = true)) slayer.setPoints(player, value) else slayer.setStreak(player, value)
                reply("Streak ${slayer.streak(player)}, ${slayer.points(player)} points.")
            }
            "clear" -> {
                slayer.clear(player)
                reply("No task.")
            }
            else -> fail("Usage: $usage")
        }
    }
}

/** The most kills `::slayer task` gives: slayer_count is a whole varp, but no task runs near this. */
private const val MAX_AMOUNT = 10_000
