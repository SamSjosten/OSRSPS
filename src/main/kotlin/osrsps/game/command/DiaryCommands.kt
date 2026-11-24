package osrsps.game.command

import osrsps.game.World
import osrsps.game.content.DiaryArea
import osrsps.game.content.DiaryTask
import osrsps.game.content.DiaryTier

/**
 * `::diary` shows the typing player's task counts in each diary built here; `::diary complete <area> <tier> [n|all]`
 * marks one task (by its number in the journal) or the whole tier done, with the messages a real completion gives;
 * `::diary reset <area> [tier] [n]` takes tasks back off, quietly; `::diary journal <area>` opens the journal. So a
 * test or a plugin check can start at any point in a diary.
 */
fun CommandRegistry.registerDiaryCommands(world: World) {
    val areas = DiaryArea.entries.joinToString("|") { it.slug }
    val tiers = DiaryTier.entries.joinToString("|") { it.name.lowercase() }
    val usage = "::diary [complete <$areas> <$tiers> [n|all] | reset <$areas> [$tiers] [n] | journal <$areas>]"
    register("diary", usage) {
        val diaries = world.diaries
        fun area(i: Int) = args.getOrNull(i)?.let { DiaryArea.forSlug(it) ?: fail("No diary '$it': $areas") } ?: fail("Usage: $usage")
        fun tier(i: Int) = args.getOrNull(i)?.let { arg -> DiaryTier.entries.firstOrNull { it.name.equals(arg, ignoreCase = true) } ?: fail("No tier '$arg': $tiers") }
        fun tasks(area: DiaryArea, tier: DiaryTier?, which: String?): List<DiaryTask> {
            val pool = if (tier == null) area.tasks else area.tasks(tier)
            if (which == null || which.equals("all", ignoreCase = true)) return pool
            val n = which.toIntOrNull() ?: fail("Usage: $usage")
            return listOf(pool.firstOrNull { it.number == n } ?: fail("No task $n in ${area.slug} ${tier?.name?.lowercase()}"))
        }
        when (args.firstOrNull()?.lowercase()) {
            null -> reply(DiaryArea.entries.joinToString("; ") { area ->
                "${area.slug} " + DiaryTier.entries.joinToString(" ") { "${diaries.count(player, area, it)}/${area.tasks(it).size}" }
            })
            "complete" -> {
                if (args.size !in 3..4) fail("Usage: $usage")
                val area = area(1)
                val done = tasks(area, tier(2), args.getOrNull(3)).count { diaries.complete(player, it) }
                reply("$done task(s) done in ${area.slug}.")
            }
            "reset" -> {
                if (args.size !in 2..4) fail("Usage: $usage")
                val area = area(1)
                val tier = tier(2)
                if (args.size == 4 && tier == null) fail("Usage: $usage")
                tasks(area, tier, args.getOrNull(3)).forEach { diaries.undo(player, it) }
                reply("Reset ${area.slug}${tier?.let { " " + it.name.lowercase() } ?: ""}.")
            }
            "journal" -> {
                if (args.size != 2) fail("Usage: $usage")
                if (world.scripts.busy(player)) fail("You can't open a journal with an interface open or in the middle of something.")
                diaries.openJournal(player, area(1))
            }
            else -> fail("Usage: $usage")
        }
    }
}
