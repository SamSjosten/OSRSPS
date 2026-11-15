package osrsps.game.skill

import osrsps.game.Skill

/**
 * One Slayer task as the cache's slayer_task table (113) has it: [id] is what varp slayer_target holds and what an NPC
 * type's param 50 names ([Slayer.PARAM_CATEGORY]); [nameLower] is how the client words it ("cows"). [row] is the db row,
 * which the other tables point at.
 */
class SlayerTask(
    val row: Int,
    val id: Int,
    val nameLower: String,
    val nameUpper: String,
    /** The combat level a master checks before assigning it (min_comlevel), 0 for none. */
    val minCombat: Int = 0,
    /** Every one of these base levels is needed (min_stat_requirement_all), as Slayer 20 is for rockslugs. */
    val statsAll: List<Pair<Skill, Int>> = emptyList(),
    /** One of these base levels is needed (min_stat_requirement_any), as Strength or Agility 60 is for aviansies. */
    val statsAny: List<Pair<Skill, Int>> = emptyList(),
    /**
     * The slayer_unlock row that, once bought, stops it being assigned (block_unlock), as Stop the Wyvern does fossil
     * island wyverns (6435); null for none.
     */
    val blockedBy: Int? = null,
    /** By slayer_unlock row, the amounts an extend unlock gives instead (extension_min_max). */
    val extensions: Map<Int, IntRange> = emptyMap(),
)

/**
 * One master's entry for a task in the cache's slayer_master_task table (114): its [weight] in the draw and the amount,
 * [min] to [max]. [unlockedBy] is the slayer_unlock row it needs bought before the master gives it (task_unlock), as
 * Chaeldar's TzHaar does; [areas] are slayer_area ids, Konar's places.
 */
class SlayerMasterTask(
    val master: Int,
    val task: SlayerTask,
    val weight: Int,
    val min: Int,
    val max: Int,
    val areas: List<Int> = emptyList(),
    val unlockedBy: Int? = null,
)

/**
 * A Slayer reward from the cache's slayer_unlock table (117): its [bit] in the unlock varps and its [cost] in points;
 * whether it may be turned off again ([refundable], though the points don't come back); and which of the rewards screen's
 * lists shows it ([list]: 0 Unlock, 1 Extend, 2 the Rewards tab's own list).
 */
class SlayerUnlock(val row: Int, val bit: Int, val cost: Int, val refundable: Boolean = false, val list: Int = 0)

/**
 * Slayer's data, read from the cache by [osrsps.cache.SlayerLoader] so it stays live's: every task, every master's list
 * and the rewards. Tests build their own.
 */
class SlayerData(
    tasks: Collection<SlayerTask>,
    masterTasks: Collection<SlayerMasterTask>,
    unlocks: Collection<SlayerUnlock> = emptyList(),
) {
    /** By task id (slayer_target's value). */
    val tasks: Map<Int, SlayerTask> = tasks.associateBy { it.id }

    /** By master id ([SlayerMaster.id]), in the cache's row order. */
    val masterTasks: Map<Int, List<SlayerMasterTask>> = masterTasks.groupBy { it.master }

    /** By slayer_unlock row. */
    val unlocks: Map<Int, SlayerUnlock> = unlocks.associateBy { it.row }

    /** [master]'s entry for [task], if it gives it. */
    fun entry(master: Int, task: Int): SlayerMasterTask? = masterTasks[master]?.firstOrNull { it.task.id == task }

    companion object {
        val NONE = SlayerData(emptyList(), emptyList())
    }
}
