package osrsps.game.skill

import osrsps.game.GameRandom
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.CombatMath
import osrsps.game.content.message
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc

/** Slayer's game messages. */
object SlayerLines : Lines("slayer", "Slayer") {
    val GEM_TASK = line("gem_task", "enchanted gem, Check, on a task: the task and how many are left; words unsourced", "task", "amount")
    val GEM_STREAK = line("gem_streak", "enchanted gem, Check: the task streak (the Wiki's Enchanted gem: it tells the streak, task or not); words unsourced", "streak")
    val GEM_NO_TASK = line("gem_no_task", "enchanted gem, Check, with no task; words unsourced")
    val TASK_DONE = line("task_done", "the last kill of a task: the message that the task is complete; words unsourced")
    val TASK_DONE_POINTS = line(
        "task_done_points",
        "the last kill of a task: the streak, the points it gave and the points held, and to go back to a master (the Wiki's Slayer reward point); words unsourced",
        "streak", "points", "total",
    )
}

/**
 * The masters, by the id the cache's slayer_master_task table and varbit slayer_master use, each with its NPC (the
 * cache's slayer_master_* types). With every quest done, Aya holds Turael's place (master 1) since While Guthix Sleeps,
 * Kuradal Duradel's (5) and Steve Nieve's (6) since Monkey Madness II (OSRS Wiki, Aya, Kuradal and Steve) and the points a task of theirs gives (OSRS Wiki, Slayer reward point, read
 * 2026-11-15): [points] for every task from the fifth, five times that every 10th, 15 every 50th, 25 every 100th, 35
 * every 250th and 50 every 1,000th. Konar's 18 and Steve's 12 are without the Elite diaries (no diaries are built).
 * [combat] and [slayer] are what a player needs before the master gives them a task (OSRS Wiki, Slayer Master).
 */
enum class SlayerMaster(val id: Int, val npc: Int, val points: Int, val combat: Int = 0, val slayer: Int = 1) {
    AYA(1, 13619, 0),
    MAZCHNA(2, 13620, 6, combat = 20),
    VANNAKA(3, 403, 8, combat = 40),
    CHAELDAR(4, 404, 10, combat = 70),
    KURADAL(5, 13623, 15, combat = 100, slayer = 50),
    STEVE(6, 6798, 12, combat = 85),
    KRYSTILIA(7, 7663, 25),
    KONAR(8, 8623, 18, combat = 75),
    SPRIA(9, 10432, 0),
    ;

    companion object {
        fun of(id: Int): SlayerMaster? = entries.firstOrNull { it.id == id }
    }
}

/**
 * The Slayer task engine (Slayer step 1). A task lives in live's own vars, so the client's helpers read it and a save
 * keeps it: varp slayer_target holds the task id, slayer_count how many are left, varbit slayer_master who gave it,
 * slayer_points the points and slayer_tasks_completed the streak (osrs-dumps' rev 242 varp and varbit names).
 *
 * A kill counts when the monster's type names the task in param 50 (the cache's own: cows carry 6, the cows task), and
 * gives Slayer XP equal to its hitpoints with its XP bonus (OSRS Wiki, Slayer: "equal to the monster's Hitpoints
 * multiplied by its experience bonus"). The last kill completes the task: the streak goes up and, from the fifth task,
 * the master's points are paid ([pointsFor]).
 *
 * The kill counts for whoever [osrsps.game.combat.MeleeCombat] counts as the killer, the player whose hit finished it.
 * When another player dealt the monster the most damage, the XP is split: half to them and half to the finisher, each
 * rounded down to a tenth, and each only if on the task (OSRS Wiki, Slayer/Experience table), and the kill counts
 * towards both their tasks, once for a player who did both (Jagex, Update:Creature of Fenkenstrain: "both the person
 * who dealt the killing blow and the person who did the most damage will be counted as the slayer"). Each is their
 * session now, so a player who logged out and back in since (a poisoned monster dying later) still gets it. Guessed:
 * of two players who dealt the same most damage, the first to deal it counts.
 * Slayer partners aren't built. Krystilia's tasks keep their own streak in slayer_wilderness_tasks_completed; she belongs to
 * the Wilderness, which isn't built, so it is left alone. Out: boss tasks, Konar's places, superiors (later steps).
 */
class Slayer(private val world: World, val data: SlayerData) {
    companion object {
        /** The NPC type param naming its task (osrs-dumps: cows 6, birds 5, rats 3). */
        const val PARAM_CATEGORY = 50

        const val VARP_COUNT = 394
        const val VARP_TARGET = 395
        const val VARBIT_MASTER = 4067
        const val VARBIT_POINTS = 4068
        const val VARBIT_STREAK = 4069

        /** slayer_wilderness_tasks_completed: Krystilia's streak, kept apart from the standard one. */
        const val VARBIT_WILDERNESS_STREAK = 5617

        /** The most points varbit slayer_points' 17 bits hold. Guessed: live caps there too. */
        const val MAX_POINTS = (1 shl 17) - 1

        /** The most the 14-bit streak varbit holds. */
        const val MAX_STREAK = (1 shl 14) - 1

        /** How far a streak past [MAX_STREAK] goes back: the longest milestone, so the bonuses keep their pace. */
        const val STREAK_WRAP = 1000

        const val ENCHANTED_GEM = 4155

        /** combat_level_reqs_disabled: 1 once the player has asked the masters to stop checking their combat level. */
        const val VARBIT_COMBAT_CHECK_OFF = 4745

        /** The bought rewards, by slayer_unlock bit: bits 0 to 31 in slayer_rewards_unlocks, 32 to 63 in _unlocks1, then _unlocks2. */
        val UNLOCK_VARPS = intArrayOf(1076, 1344, 5587)

        /** One of [choices], each with the chance its weight is of all their weights; null with none to draw. */
        fun draw(random: GameRandom, choices: List<SlayerMasterTask>): SlayerMasterTask? {
            val total = choices.sumOf { it.weight }
            if (total <= 0) return null
            var pick = random.nextInt(total)
            return choices.first { pick -= it.weight; pick < 0 }
        }

        /** Points for the [streak]th task from a master paying [base] a task (the Wiki's table: none before the fifth). */
        fun pointsFor(base: Int, streak: Int): Int = when {
            streak < 5 -> 0
            streak % 1000 == 0 -> base * 50
            streak % 250 == 0 -> base * 35
            streak % 100 == 0 -> base * 25
            streak % 50 == 0 -> base * 15
            streak % 10 == 0 -> base * 5
            else -> base
        }
    }

    /** [player]'s task, null with none (or none left to kill). */
    fun task(player: Player): SlayerTask? =
        if (player.vars[VARP_COUNT] > 0) data.tasks[player.vars[VARP_TARGET]] else null

    /** How many of [player]'s task are left. */
    fun remaining(player: Player): Int = player.vars[VARP_COUNT]

    fun master(player: Player): SlayerMaster? = SlayerMaster.of(player.vars.getVarbit(VARBIT_MASTER))

    fun points(player: Player): Int = player.vars.getVarbit(VARBIT_POINTS)

    fun streak(player: Player): Int = player.vars.getVarbit(VARBIT_STREAK)

    fun wildernessStreak(player: Player): Int = player.vars.getVarbit(VARBIT_WILDERNESS_STREAK)

    fun setPoints(player: Player, points: Int) = player.vars.setVarbit(VARBIT_POINTS, points.coerceIn(0, MAX_POINTS))

    fun setStreak(player: Player, streak: Int) = player.vars.setVarbit(VARBIT_STREAK, streak.coerceIn(0, MAX_STREAK))

    /** Whether [player] has bought the reward in slayer_unlock row [row]. */
    fun unlocked(player: Player, row: Int): Boolean {
        val bit = data.unlocks[row]?.bit ?: return false
        val varp = UNLOCK_VARPS.getOrNull(bit / 32) ?: return false
        return player.vars[varp] and (1 shl (bit % 32)) != 0
    }

    fun combatChecked(player: Player): Boolean = player.vars.getVarbit(VARBIT_COMBAT_CHECK_OFF) == 0

    fun setCombatChecked(player: Player, checked: Boolean) = player.vars.setVarbit(VARBIT_COMBAT_CHECK_OFF, if (checked) 0 else 1)

    /** Whether [player] is strong enough for [master] to give them tasks at all. */
    fun meets(player: Player, master: SlayerMaster): Boolean =
        player.skills.combatLevel >= master.combat && player.skills.baseLevel(Skill.SLAYER) >= master.slayer

    /**
     * Whether [master] may give [player] [entry]: with base levels (OSRS Wiki, Slayer task: "The player cannot boost their
     * Slayer level to obtain a task"), the task's combat level unless they've turned the check off, every one of its
     * stats and one of its either-or stats, the reward it needs bought, and not the reward that blocks it. Quests count
     * as done. Boss tasks, whose amount the player picks, aren't built.
     */
    fun eligible(player: Player, entry: SlayerMasterTask): Boolean {
        val task = entry.task
        if (entry.min <= 0 || entry.max < entry.min) return false
        if (combatChecked(player) && player.skills.combatLevel < task.minCombat) return false
        if (task.statsAll.any { (skill, level) -> player.skills.baseLevel(skill) < level }) return false
        if (task.statsAny.isNotEmpty() && task.statsAny.none { (skill, level) -> player.skills.baseLevel(skill) >= level }) return false
        if (entry.unlockedBy != null && !unlocked(player, entry.unlockedBy)) return false
        if (task.blockedBy != null && unlocked(player, task.blockedBy)) return false
        return true
    }

    /** [master]'s tasks [player] may be given. */
    fun choices(player: Player, master: SlayerMaster): List<SlayerMasterTask> =
        data.masterTasks[master.id].orEmpty().filter { eligible(player, it) }

    /**
     * Draws [master]'s next task for [player] by the cache's weights over what they may be given (OSRS Wiki, Slayer task,
     * Task weighting) and its amount, even from the entry's least to its most, or from an extend's when bought. Null when
     * there is nothing to give.
     */
    fun roll(player: Player, master: SlayerMaster): Pair<SlayerMasterTask, Int>? {
        val entry = draw(world.random, choices(player, master)) ?: return null
        val range = entry.task.extensions.entries.firstOrNull { unlocked(player, it.key) }?.value ?: entry.min..entry.max
        return entry to world.random.between(range.first, range.last)
    }

    /** Gives [player] [amount] of [task] from [master], replacing any task they had. */
    fun assign(player: Player, master: SlayerMaster, task: SlayerTask, amount: Int) {
        require(amount > 0) { "A task of $amount" }
        player.vars[VARP_TARGET] = task.id
        player.vars[VARP_COUNT] = amount
        player.vars.setVarbit(VARBIT_MASTER, master.id)
    }

    /** Takes [player]'s task away, streak and points untouched. */
    fun clear(player: Player) {
        player.vars[VARP_COUNT] = 0
        player.vars[VARP_TARGET] = 0
    }

    /** Whether [npc] counts for [player]'s task. */
    fun onTask(player: Player, npc: Npc): Boolean {
        val task = task(player) ?: return false
        return (npc.type.params[PARAM_CATEGORY] as? Int) == task.id
    }

    /** Slayer XP in tenths for a kill of [npc]: its hitpoints, with its XP bonus. */
    fun xpTenths(npc: Npc): Int =
        CombatMath.withXpBonus(npc.maxHitpoints * 10, world.combat.npcCombat[npc.type]?.xpBonusTenths ?: 0)

    /** [npc] died with [killer] the one who finished it: one off their task if it counts, with its XP. */
    /**
     * Damage dealt to each monster that is some task, by account (the save key, so a player who logs out and back in is
     * still one player), in the order they first hit it: whoever dealt it the most shares a kill's XP ([killed]). A
     * monster back at full hitpoints starts afresh, and one taken out of the world alive is forgotten ([removed]).
     */
    private val damage = HashMap<Npc, LinkedHashMap<String, Int>>()

    /** [player] dealt [npc] [dealt] damage (MeleeCombat's every hit from a player). */
    fun damaged(npc: Npc, player: Player, dealt: Int) {
        if (npc.type.params[PARAM_CATEGORY] !is Int) return
        if (npc.hitpoints + dealt >= npc.maxHitpoints) damage.remove(npc)
        damage.getOrPut(npc) { LinkedHashMap() }.merge(player.saveKey, dealt, Int::plus)
    }

    /** [npc] left the world without being killed (World.removeNpc). */
    fun removed(npc: Npc) {
        damage.remove(npc)
    }

    fun killed(npc: Npc, killer: Player?) {
        val dealt = damage.remove(npc)
        val topKey = dealt?.filterValues { it > 0 }?.maxByOrNull { it.value }?.key
        // A finisher who never did the monster any damage (a miss or splash before poison or venom finished it) gets
        // nothing: the top damager alone is credited (exploit review round 34, R34B-1). With no damage on record (an
        // admin's ::kill) the finisher is.
        val killerKey = killer?.saveKey?.takeIf { dealt.isNullOrEmpty() || (dealt[it] ?: 0) > 0 } ?: topKey ?: return
        val shared = topKey != null && topKey != killerKey
        val xp = xpTenths(npc)
        // Each one's session now, if they are in the world: not the one that hit, after a relog.
        val credited = if (shared) listOf(killerKey, topKey!!) else listOf(killerKey)
        for (key in credited) {
            val player = online(key) ?: continue
            if (!onTask(player, npc)) continue
            player.skills.addTenths(Skill.SLAYER, if (shared) xp / 2 else xp)
            val left = remaining(player) - 1
            player.vars[VARP_COUNT] = left
            if (left == 0) complete(player)
        }
    }

    /** The player in the world under save key [key], not logged out, or null. */
    private fun online(key: String): Player? = world.onlinePlayers.firstOrNull { it.saveKey == key && !it.loggedOut }

    private fun complete(player: Player) {
        // A streak past what the 14-bit varbit holds goes back 1,000, so every milestone (10, 50, 100, 250 and 1,000
        // all divide 1,000) still comes round on time: the Wiki's March 2023 update keeps a huge streak's bonuses
        // coming "as if you didn't have a ludicrous streak count". Guessed: how live stores it.
        val next = streak(player) + 1
        val streak = if (next > MAX_STREAK) next - STREAK_WRAP else next
        setStreak(player, streak)
        val points = pointsFor(master(player)?.points ?: 0, streak)
        setPoints(player, points(player) + points)
        // Guessed: the task var is cleared too, not only the count.
        player.vars[VARP_TARGET] = 0
        world.message(player, SlayerLines.TASK_DONE)
        world.message(player, SlayerLines.TASK_DONE_POINTS, "streak" to streak, "points" to points, "total" to points(player))
    }

    /** The enchanted gem's Check: the task and what is left, then the streak. */
    fun check(player: Player) {
        val task = task(player)
        if (task == null) world.message(player, SlayerLines.GEM_NO_TASK)
        else world.message(player, SlayerLines.GEM_TASK, "task" to task.nameLower, "amount" to remaining(player))
        world.message(player, SlayerLines.GEM_STREAK, "streak" to streak(player))
    }

    fun register() {
        val gem = world.objTypes[ENCHANTED_GEM] ?: return
        val check = gem.iops.indexOfFirst { it.equals("Check", ignoreCase = true) }.takeIf { it >= 0 }?.plus(1) ?: return
        world.handlers.onObj(ENCHANTED_GEM, check) { check(player) }
    }
}
