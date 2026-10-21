package osrsps.game

/** Where a quest keeps its progress: a whole varp or a varbit. */
sealed interface QuestVar {
    data class Varp(val id: Int) : QuestVar
    data class Varbit(val id: Int) : QuestVar
}

/**
 * One row of the cache's quest table. The client counts a quest finished once its progress var is at least
 * [endState] (client script 4029, quest_status_get), so that is the value we set.
 */
data class Quest(val row: Int, val name: String, val progress: QuestVar, val endState: Int, val questPoints: Int)

/**
 * Every account counts as having finished every quest (Sam's call, 2026-10-21): no quest content is built, and
 * content that checks quest progress sees it complete.
 */
class Quests(val all: List<Quest>) {
    /** Quest points for finishing everything; the quest tab reads them from varp 101 (qp). */
    val totalQuestPoints: Int = all.sumOf { it.questPoints }

    /** The value each progress var ends at. Quests that share a var (Recipe for Disaster's) take the highest. */
    private val endValues: Map<QuestVar, Int> =
        all.groupingBy { it.progress }.fold(Int.MIN_VALUE) { acc, quest -> maxOf(acc, quest.endState) }

    /** Sets every quest to finished. Called at login; a var already past its end value is left alone. */
    fun completeAll(vars: PlayerVars) {
        for ((progress, end) in endValues) {
            when (progress) {
                is QuestVar.Varp -> if (vars[progress.id] < end) vars[progress.id] = end
                is QuestVar.Varbit -> if (vars.getVarbit(progress.id) < end) vars.setVarbit(progress.id, end)
            }
        }
        vars[VARP_QUEST_POINTS] = totalQuestPoints
    }

    companion object {
        /** Varp qp in the cache's names. */
        const val VARP_QUEST_POINTS = 101

        val NONE = Quests(emptyList())
    }
}
