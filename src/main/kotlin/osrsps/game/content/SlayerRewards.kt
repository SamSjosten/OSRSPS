package osrsps.game.content

import osrsps.game.FrameSlot
import osrsps.game.Inventory
import osrsps.game.Interfaces
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.skill.Slayer
import osrsps.game.skill.SlayerMaster
import osrsps.game.skill.SlayerUnlock

/** The rewards screen's game messages; the client words its own boxes, so these are only the server's refusals. */
object SlayerRewardsLines : Lines("slayer_rewards", "Slayer Rewards") {
    val NOT_ENOUGH_POINTS = line("not_enough_points", "an unlock, extend, cancel or block the player hasn't the points for; words unsourced", "cost")
    val NO_TASK = line("no_task", "Cancel or Block with no task; words unsourced")
    val CANT_BLOCK = line("cant_block", "Block on a task that can't be blocked (a boss task, TzTok-Jad, TzKal-Zuk); words unsourced")
    val ALREADY_BLOCKED = line("already_blocked", "Block on a task this master already has blocked; words unsourced")
    val NO_BLOCK_SLOT = line("no_block_slot", "Block with every block slot the player may use full; words unsourced")
    val ALL_EXTENDED = line("all_extended", "Extend everything with every extend already bought; words unsourced")
    val NO_ROOM = line("no_room", "a Buy with no room in the inventory for even one; words unsourced")
    val ALREADY_OWN = line("already_own", "a Buy of a rune pouch or looting bag the player already has in the inventory or bank; words unsourced", "item")
    val NEED_HERBLORE = line("need_herblore", "a Buy of the herb sack below 58 Herblore; words unsourced")
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
 * reads).
 *
 * The Buy tab (Slayer step 5) lists enum_840's six items, at enum_842's points for one or for enum_841's set (250 broad
 * bolts or arrows), as the client draws them (slayer_rewards_buy_draw): ops 2 to 5 on buy_items' child buy 1, 5, 10 or 50,
 * and the herb sack, rune pouch and looting bag only 1. The herb sack needs base Herblore 58, and a rune pouch or looting
 * bag is refused to a player who already has one anywhere (the OSRS Wiki's item pages). Guessed: a Buy takes as many as
 * the points, the inventory and the stack limit allow, and refuses only when not even one fits. Left out: Task Storage's store and unstore, and the content restrictions on some unlocks (Leagues and the like).
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

        /** slayer_unlock's list_position lists the client draws: the unlocks, the extends and the helmet recolours. */
        private const val UNLOCK_LIST = 0
        private const val EXTEND_LIST = 1
        private const val RECOLOUR_LIST = 2

        /** The Buy tab's items, drawn as children 0 to 5 in enum_840's order. */
        const val BUY_ITEMS = 25

        /** The Buy tab's ops: op 2 buys 1 (or one set), 3 five, 4 ten and 5 fifty. */
        private val BUY_AMOUNTS = mapOf(2 to 1, 3 to 5, 4 to 10, 5 to 50)
        private const val BUY_OPS = (1 shl 1) or (1 shl 2) or (1 shl 3) or (1 shl 4)

        /**
         * One of the Buy tab's items: its points for [per] of it, whether only one is bought at a time, the menu names
         * of the items a player may own only one of among them ([oneOf], empty for any number), and the base level of
         * [skill] it needs.
         */
        data class Buy(
            val obj: Int, val cost: Int, val per: Int = 1, val single: Boolean = false,
            val oneOf: Set<String> = emptySet(), val skill: Skill? = null, val level: Int = 0,
        )

        /** enum_840's items with enum_842's costs and enum_841's set sizes (242 cache). */
        val BUY = listOf(
            Buy(11866, 75), // slayer_ring_8
            Buy(11875, 35, per = 250), // slayer_broad_bolt
            Buy(4160, 35, per = 250), // slayer_broad_arrows
            // The OSRS Wiki's Herb sack: "Buying one requires level 58 in Herblore", not boostable.
            Buy(13226, 750, single = true, skill = Skill.HERBLORE, level = 58), // slayer_herb_sack
            // The OSRS Wiki's Rune pouch: "Players cannot own more than one rune pouch". Inferred: the divine one counts.
            Buy(12791, 750, single = true, oneOf = setOf("rune pouch", "divine rune pouch")), // bh_rune_pouch
            // The OSRS Wiki's Looting bag: "Only one looting bag can be in a player's possession at a time" (closed or open).
            Buy(11941, 10, single = true, oneOf = setOf("looting bag")), // looting_bag
        )

        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>>
            get() = mapOf(INTERFACE to ("slayer_rewards" to mapOf(CONFIRM_BUTTON to "confirm_button", BUY_ITEMS to "buy_items")))
    }

    private val slayer get() = world.slayer

    fun register() {
        for (master in SlayerMasters.BUILT) world.handlers.onNpc(master.npc, REWARDS) { open(player, master) }
        world.handlers.onButton(INTERFACE, CONFIRM_BUTTON) { op ->
            if (player.modals[FrameSlot.MAINMODAL] == INTERFACE) confirm(player, op.sub)
        }
        world.handlers.onButton(INTERFACE, BUY_ITEMS) { op ->
            if (player.modals[FrameSlot.MAINMODAL] == INTERFACE) buy(player, op.sub, op.option)
        }
    }

    fun open(player: Player, master: SlayerMaster) {
        player.vars.setVarbit(VARBIT_MASTER_IN_FOCUS, master.id)
        showTask(player)
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, INTERFACE)
        world.interfaces.setEvents(player, INTERFACE, CONFIRM_BUTTON, 0, LAST_CHILD, Interfaces.IF_EVENT_OP1)
        world.interfaces.setEvents(player, INTERFACE, BUY_ITEMS, 0, BUY.lastIndex, BUY_OPS)
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
            else -> slayer.data.unlocks.values.firstOrNull { it.bit == child && buyable(it) }?.let { toggle(player, it.bit, it.cost, it.refundable) }
        }
        showTask(player)
    }

    /**
     * Whether the screen offers [unlock]: a row on one of the lists the client draws (slayer_unlock's list column: 0 the
     * unlocks, 1 the extends, 2 the helmet recolours; -1 rows are drawn on none) within the confirm box's children. A
     * modified client's other child ids buy nothing (exploit review round 35, R35B-2). The Wiki's Slayer Rewards page
     * names no prerequisite for any row, so none is asked.
     */
    private fun buyable(unlock: SlayerUnlock): Boolean = unlock.bit in 0..LAST_CHILD && unlock.list in UNLOCK_LIST..RECOLOUR_LIST

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
        val slot = slayer.blockSlots(player).firstOrNull { slayer.blockedIn(player, master, it) == 0 }
            ?: return player.message(text[SlayerRewardsLines.NO_BLOCK_SLOT])
        if (!pay(player, master.blockCost)) return
        slayer.setBlocked(player, master, slot, task.id)
        slayer.clear(player)
    }

    /** Op [op] on the Buy tab's child [child]. */
    fun buy(player: Player, child: Int, op: Int) {
        val buy = BUY.getOrNull(child) ?: return
        val asked = BUY_AMOUNTS[op]?.takeIf { op == 2 || !buy.single } ?: return
        val type = world.objTypes[buy.obj] ?: return
        val text = world.textFor(player)
        if (buy.skill != null && player.skills.baseLevel(buy.skill) < buy.level) {
            player.message(text[SlayerRewardsLines.NEED_HERBLORE])
            return
        }
        if (buy.oneOf.isNotEmpty() && owns(player, buy.oneOf)) {
            player.message(text[SlayerRewardsLines.ALREADY_OWN, "item" to type.name])
            return
        }
        val have = player.inventory.count(buy.obj)
        val room = when {
            // A stack takes only as many sets as fit under the stack limit.
            type.stackable && have > 0 -> minOf(asked.toLong(), (Inventory.MAX_STACK - have) / buy.per).toInt()
            type.stackable -> if (player.inventory.freeSlots > 0) asked else 0
            else -> minOf(asked, player.inventory.freeSlots / buy.per)
        }
        val afford = minOf(room, slayer.points(player) / buy.cost)
        if (afford == 0) {
            if (room == 0) player.message(text[SlayerRewardsLines.NO_ROOM])
            else player.message(text[SlayerRewardsLines.NOT_ENOUGH_POINTS, "cost" to buy.cost])
            return
        }
        slayer.setPoints(player, slayer.points(player) - afford * buy.cost)
        player.inventory.add(type, afford * buy.per)
    }

    /** Whether the inventory, equipment or bank holds an item called one of [names] (not a note or placeholder). */
    private fun owns(player: Player, names: Set<String>): Boolean =
        sequenceOf(player.inventory, player.equipment, player.bank).flatMap { it.contents().asSequence() }.any { (_, obj) ->
            val type = world.objTypes[obj.id]
            type != null && !type.isCert && !type.isPlaceholder && type.name.lowercase() in names
        }
}
