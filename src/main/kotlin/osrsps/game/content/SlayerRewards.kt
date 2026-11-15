package osrsps.game.content

import osrsps.game.FrameSlot
import osrsps.game.Interfaces
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.skill.Slayer
import osrsps.game.skill.SlayerMaster

/** The rewards screen's game messages; the client words its own boxes, so these are only the server's refusals. */
object SlayerRewardsLines : Lines("slayer_rewards", "Slayer Rewards") {
    val NOT_ENOUGH_POINTS = line("not_enough_points", "an unlock, extend, cancel or block the player hasn't the points for; words unsourced", "cost")
    val NO_TASK = line("no_task", "Cancel or Block with no task; words unsourced")
    val CANT_BLOCK = line("cant_block", "Block on a task that can't be blocked (a boss task, TzTok-Jad, TzKal-Zuk); words unsourced")
    val ALREADY_BLOCKED = line("already_blocked", "Block on a task this master already has blocked; words unsourced")
    val NO_BLOCK_SLOT = line("no_block_slot", "Block with every block slot the player may use full; words unsourced")
    val ALL_EXTENDED = line("all_extended", "Extend everything with every extend already bought; words unsourced")
}

/**
 * The Slayer rewards screen (interface 426, slayer_rewards; Slayer step 4), opened by a master's Rewards option or
 * "Look at rewards". The client draws all of it from the cache's slayer_unlock table and the unlock varps, and asks the
 * server only to do what its confirm box confirms: an op on confirm_button's child numbered for the action (the 242
 * client's scripts slayer_rewards_unlock_confirm, slayer_rewards_tasks_canceltask and _unblocktask, osrs-dumps):
 * an unlock's bit to buy it (or, for one the table marks refundable, to turn it off again with no points back), 75 to
 * buy every extend, 67 to cancel the task, 68 to block it, and 66 plus 3 to 8 or 12 to empty a block slot.
 *
 * Costs are the table's and the client's own: an unlock's cost column, the sum of the extends not yet bought for
 * "Extend everything" (inferred: the client shows a total it works out itself), 30 points to cancel, and each master's
 * own block cost ([SlayerMaster.blockCost]). Blocks belong to the master whose screen it is (slayer_master_in_focus):
 * since the 2025 update each master keeps their own block list, Aya and Spria sharing Turael's (OSRS Wiki, Slayer
 * Rewards). A block cancels the task, and a master never gives a task they have blocked ([Slayer.eligible]).
 *
 * The Rewards tab shows the task from varps if1 to if3 (count, task, boss); the server copies the task there while the
 * screen is open (inferred from the scripts reading them, since slayer_count and slayer_target are what the helper
 * reads). Left out: the Buy tab (with the shop, the next step), Task Storage's store and unstore, the seventh block
 * slot (the Lumbridge & Draynor Elite diary), and the content restrictions on some unlocks (Leagues and the like).
 * Guessed: the refusals' words and that they are game messages.
 */
class SlayerRewards(private val world: World) {
    companion object {
        const val INTERFACE = 426
        const val CONFIRM_BUTTON = 11
        const val REWARDS = 5

        /** The confirm box's children: an unlock's bit, or 66 plus the client's action number. */
        private const val ACTION_BASE = 66
        const val CANCEL = ACTION_BASE + 1
        const val BLOCK = ACTION_BASE + 2
        const val EXTEND_ALL = ACTION_BASE + 9
        private const val LAST_CHILD = ACTION_BASE + 12

        /** The child that empties block slot 1 to 7, by slot (the client's script slayer_rewards_tasks_blocked_draw). */
        val UNBLOCK = listOf(3, 4, 5, 6, 7, 12, 8).map { ACTION_BASE + it }

        /** slayer_master_in_focus: whose screen it is, for the block list and costs. */
        const val VARBIT_MASTER_IN_FOCUS = 17868

        /** if1 to if3: the task's count, task id and boss id, as the Rewards tab reads them. */
        const val VARP_IF_COUNT = 261
        const val VARP_IF_TASK = 262
        const val VARP_IF_BOSS = 263

        /** The boss task, TzTok-Jad's and TzKal-Zuk's, which the client offers no Block for. */
        private val UNBLOCKABLE = setOf(97, 98, 105)

        /** The extends' list in slayer_unlock's list_position. */
        private const val EXTEND_LIST = 1

        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>>
            get() = mapOf(INTERFACE to ("slayer_rewards" to mapOf(CONFIRM_BUTTON to "confirm_button")))
    }

    private val slayer get() = world.slayer

    fun register() {
        for (master in SlayerMasters.BUILT) world.handlers.onNpc(master.npc, REWARDS) { open(player, master) }
        world.handlers.onButton(INTERFACE, CONFIRM_BUTTON) { op ->
            if (player.modals[FrameSlot.MAINMODAL] == INTERFACE) confirm(player, op.sub)
        }
    }

    fun open(player: Player, master: SlayerMaster) {
        player.vars.setVarbit(VARBIT_MASTER_IN_FOCUS, master.id)
        showTask(player)
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, INTERFACE)
        world.interfaces.setEvents(player, INTERFACE, CONFIRM_BUTTON, 0, LAST_CHILD, Interfaces.IF_EVENT_OP1)
    }

    private fun showTask(player: Player) {
        val task = slayer.task(player)
        player.vars[VARP_IF_COUNT] = if (task == null) 0 else slayer.remaining(player)
        player.vars[VARP_IF_TASK] = task?.id ?: 0
        player.vars[VARP_IF_BOSS] = 0
    }

    private fun focus(player: Player): SlayerMaster =
        SlayerMaster.of(player.vars.getVarbit(VARBIT_MASTER_IN_FOCUS)) ?: SlayerMaster.AYA

    /** The confirm box's op on child [child]. */
    fun confirm(player: Player, child: Int) {
        when (child) {
            CANCEL -> cancel(player)
            BLOCK -> block(player, focus(player))
            EXTEND_ALL -> extendAll(player)
            in UNBLOCK -> slayer.setBlocked(player, focus(player), UNBLOCK.indexOf(child) + 1, 0)
            else -> slayer.data.unlocks.values.firstOrNull { it.bit == child }?.let { toggle(player, it.bit, it.cost, it.refundable) }
        }
        showTask(player)
    }

    /** Pays [cost] points if [player] has them; false, with the refusal, if not. */
    private fun pay(player: Player, cost: Int): Boolean {
        val points = slayer.points(player)
        if (points < cost) {
            player.message(world.textFor(player)[SlayerRewardsLines.NOT_ENOUGH_POINTS, "cost" to cost])
            return false
        }
        slayer.setPoints(player, points - cost)
        return true
    }

    private fun toggle(player: Player, bit: Int, cost: Int, refundable: Boolean) {
        if (slayer.hasBit(player, bit)) {
            if (refundable) slayer.setBit(player, bit, false)
            return
        }
        if (pay(player, cost)) slayer.setBit(player, bit, true)
    }

    private fun extendAll(player: Player) {
        val extends = slayer.data.unlocks.values.filter { it.list == EXTEND_LIST && !slayer.hasBit(player, it.bit) }
        if (extends.isEmpty()) return player.message(world.textFor(player)[SlayerRewardsLines.ALL_EXTENDED])
        if (pay(player, extends.sumOf { it.cost })) for (unlock in extends) slayer.setBit(player, unlock.bit, true)
    }

    private fun cancel(player: Player) {
        if (slayer.task(player) == null) return player.message(world.textFor(player)[SlayerRewardsLines.NO_TASK])
        if (pay(player, Slayer.CANCEL_COST)) slayer.clear(player)
    }

    private fun block(player: Player, master: SlayerMaster) {
        val text = world.textFor(player)
        val task = slayer.task(player) ?: return player.message(text[SlayerRewardsLines.NO_TASK])
        if (task.id in UNBLOCKABLE) return player.message(text[SlayerRewardsLines.CANT_BLOCK])
        if (slayer.blocked(player, master, task.id)) return player.message(text[SlayerRewardsLines.ALREADY_BLOCKED])
        val slot = (1..slayer.blockSlots(player)).firstOrNull { slayer.blockedIn(player, master, it) == 0 }
            ?: return player.message(text[SlayerRewardsLines.NO_BLOCK_SLOT])
        if (!pay(player, master.blockCost)) return
        slayer.setBlocked(player, master, slot, task.id)
        slayer.clear(player)
    }
}
