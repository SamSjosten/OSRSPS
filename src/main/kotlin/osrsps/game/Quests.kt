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
data class Quest(
    val row: Int,
    val name: String,
    val progress: QuestVar,
    val endState: Int,
    val questPoints: Int,
    val kind: QuestKind = QuestKind.QUEST,
)

/**
 * The quest table's type column (0 quest, 1 miniquest) and its parent_quest column (set on Recipe for Disaster's
 * parts). The quest list counts each kind on its own.
 */
enum class QuestKind { QUEST, SUBQUEST, MINIQUEST }

/**
 * Every account counts as having finished every quest (Sam's call, 2026-10-21): no quest content is built, and
 * content that checks quest progress sees it complete.
 */
class Quests(val all: List<Quest>) {
    /** Quest points for finishing everything; the quest tab reads them from varp 101 (qp). */
    val totalQuestPoints: Int = all.sumOf { it.questPoints }

    private fun count(kind: QuestKind): Int = all.count { it.kind == kind }

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
        // The quest list's footer reads these (client procs questlist_completed and questlist_qp); left at 0 it
        // showed "Completed: 0/0" and "Quest Points: 342/0" beside a list of finished quests.
        // Inferred: live's quests_total_count leaves out miniquests and Recipe for Disaster's parts, as each has its
        // own completed counter (subquests_completed_count, miniquests_completed_count); not checked against a live
        // account's tab.
        val counters = mapOf(
            VARBIT_QP_MAX to totalQuestPoints,
            VARBIT_QUESTS_COMPLETED to count(QuestKind.QUEST),
            VARBIT_QUESTS_TOTAL to count(QuestKind.QUEST),
            VARBIT_SUBQUESTS_COMPLETED to count(QuestKind.SUBQUEST),
            VARBIT_MINIQUESTS_COMPLETED to count(QuestKind.MINIQUEST),
        )
        for ((varbit, value) in counters) {
            if (vars.types.varbit(varbit) != null) vars.setVarbit(varbit, value)
        }
        for ((varbit, value) in AFTER_QUESTS) {
            if (vars.types.varbit(varbit) != null && vars.getVarbit(varbit) < value) vars.setVarbit(varbit, value)
        }
    }

    companion object {
        /** Varp qp in the cache's names. */
        const val VARP_QUEST_POINTS = 101

        /** Varbit qp_max: the quest points there are, the number after the slash in the quest list's footer. */
        const val VARBIT_QP_MAX = 1782

        /** Varbit quests_completed_count: quests finished, not counting miniquests or Recipe for Disaster's parts. */
        const val VARBIT_QUESTS_COMPLETED = 6347

        /** Varbit quests_total_count: quests there are, counted the same way. */
        const val VARBIT_QUESTS_TOTAL = 11877

        /** Varbit subquests_completed_count: Recipe for Disaster's parts finished. */
        const val VARBIT_SUBQUESTS_COMPLETED = 13987

        /** Varbit miniquests_completed_count. */
        const val VARBIT_MINIQUESTS_COMPLETED = 14162

        /**
         * Varbits outside the quest table that a finished quest leaves set, as varbit to value: what the world shows
         * after the quest, such as an NPC's options. Each one is named for the content that reads it.
         */
        val AFTER_QUESTS: Map<Int, Int> = mapOf(
            // gobdip_met_aggie: Goblin Diplomacy has the player meet Aggie, who then shows Dyes ([osrsps.game.content.Aggie]).
            13597 to 1,
            // ikov_lucien_vis: after Temple of Ikov on Lucien's side, he shows in his house, Talk-to only (inferred;
            // [osrsps.game.content.EdgevillePeople]).
            10753 to 1,
            // wgs_tfa_shortcut_unlocked: While Guthix Sleeps opens the Chasm of Tears' cavity to The Fractured Archive's
            // lobby (inferred from the name; [osrsps.game.content.ArchiveLobby]).
            16354 to 1,
            // captain_harlan_intro: Mudfoot's Big Adventure starts with Captain Harlan, so after it he greets the player
            // as one he has met (inferred; [osrsps.game.content.Harlan]).
            16780 to 1,
            // burgh_temple_trapdoor: In Aid of the Myreque opens the trapdoor down to Paterdomus's secret library with the
            // temple library key, and it stays open (Wiki, Temple library key; [osrsps.game.content.Paterdomus]).
            1982 to 1,
            // dttd_ham_trapdoor_state: Death to the Dorgeshuun leaves the hidden trapdoor below the H.A.M. Hideout open,
            // Climb-down, as the quest's later steps climb down it without a pick (inferred from the Wiki's quick guide;
            // [osrsps.game.content.HamStorerooms]).
            2270 to 2,
            // soulbane_tolna_pres, soulbane_riftrope_pres, soulbane_warning_pres: after A Soul's Bane, Tolna stands at the
            // top of his rift, the rope hangs in it and the warning signs stand round it (inferred from the names and the
            // Wiki's post-quest pictures; [osrsps.game.content.LumberYard]).
            2025 to 1,
            2032 to 1,
            2034 to 1,
            // Garden of Tranquillity leaves Varrock Palace's garden grown: each flower bed, the vines and the three rose
            // bushes at their fully grown stage (7), both orchid pots likewise (the Wiki: the quest ends once every patch
            // is fully grown and both statues are in place). The White Tree is set fully grown (8), and [osrsps.game.content.PalaceGarden]
            // hangs its fruit on it. The two statues stand on their plinths (2 shows each placed), which leaves
            // Lumbridge's statue of a former king gone from its plinth (the same varbit, 964, shows statue_plinth_lumbridge
            // at 2). Varbit names: garden_delphinium_patch and so on, as the multilocs that read them are named.
            979 to 7, 980 to 7, 981 to 7, 982 to 7, 983 to 7, 987 to 7,
            984 to 7, 986 to 7,
            985 to 8,
            963 to 2, 964 to 2,
            // Daddy's Home leaves Marlo's house mended: each piece of furniture and the carpet at its _fixed form (3).
            10563 to 3, 10564 to 3, 10565 to 3, 10566 to 3, 10567 to 3, 10568 to 3, 10569 to 3,
            // Demon Slayer: Sir Prysin's sword case (qip_ds_silverlight_case) stands empty once he has handed over
            // Silverlight (inferred from its two forms, full and empty).
            2567 to 1,
            // bolren_got_orbs: Tree Gnome Village ends with the orbs back with King Bolren, and his village's spirit tree
            // (ent) shows Travel, Talk-to and Last-destination from 2, the lowest value whose form has them (inferred;
            // [osrsps.game.content.SpiritTrees]).
            598 to 2,
            // The Lost Tribe's varbit (532) ends the quest at 11, which shows Kazgar and Mistag with only Talk-to and
            // Follow; their Mines/Watermill/Cellar forms (lost_tribe_guide_3ops, lost_tribe_mistag_3ops) are the multinpcs'
            // entry 12, inferred to be where Death to the Dorgeshuun leaves it. 12 changes nothing else that reads it
            // (Sigmund in the H.A.M. store and the cellar wall read the same at 11 and 12). Nightly 2026-11-23.
            532 to 12,
            // The Restless Ghost: the Wizards' Tower basement's altar (restless_ghost_altar) bare, the skull back in the
            // coffin (1, restless_ghost_altar_no_skull).
            2130 to 1,
            // Enlightened Journey, with every balloon route flown (Sam, 19 Nov 2026: all routes open): each station's basket
            // ready, zep_multi_basket (Entrana) and zep_multi_piccard (Taverley) at 2, the quest's last step, and
            // zep_multi_cast, _gno, _craft and _varr at 1 (inferred from the baskets' multiloc forms;
            // [osrsps.game.content.Balloons]).
            2867 to 2, 2868 to 2, 2869 to 1, 2870 to 1, 2871 to 1, 2872 to 1,
            // vm_digsite_tablet, vm_digsite_talisman: The Dig Site fills the Varrock Museum's tablet and talisman cases
            // (Wiki, Display case numbers 42 and 43; [osrsps.game.content.MuseumDisplays]).
            3643 to 1,
            3644 to 1,
            // golem_retrieved_statuette: The Golem takes the statuette from the museum's terracotta case, which then
            // shows empty, its Study saying it was stolen (Wiki, Display case number 30; [osrsps.game.content.MuseumDisplays]).
            355 to 1,
        )

        val NONE = Quests(emptyList())
    }
}
