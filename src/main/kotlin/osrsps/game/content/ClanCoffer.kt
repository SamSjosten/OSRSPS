package osrsps.game.content

import osrsps.game.FrameSlot
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.shop.ShopScreen
import osrsps.game.social.ClanStore

/** The clan coffer's lines (guessed: the Wiki gives none). */
object ClanCofferLines : Lines("clan_coffer", "Clan Coffer") {
    val NOT_MEMBER = line("not_member", "Use on a clan's coffer by someone not in that clan (guessed)")
    val NO_WITHDRAW = line("no_withdraw", "a withdraw button below the clan's Withdraw from coffer permission (guessed)")
    val NO_DEPOSIT = line("no_deposit", "a deposit button below the clan's Deposit into coffer permission (guessed)")
    val EMPTY = line("empty", "withdrawing from an empty coffer (guessed)")
    val NO_COINS = line("no_coins", "depositing with no coins in the inventory (guessed)")
    val FULL = line("full", "depositing into a coffer at its limit (guessed)", "limit")
    val NO_ROOM = line("no_room", "withdrawing with no room for the coins (guessed)")
}

/**
 * The clan coffer in each clan hall (clan_hall_storage 41626, Use, south of the bank at 1748, 5472: level 1 in the map
 * files, over a bridge flag, so level 0 in play).
 * Sources: the OSRS Wiki's Clan hall and Clan Coffer pages, read 2026-11-19 ("The Clan Coffer near the bank is used to store or withdraw
 * up to 50,000,000 coins at a time. A certain rank is required to withdraw any amount which will always notify all
 * members of the clan. Any member of the clan is able to deposit into the coffer except for Ironmen."; the Coffer page:
 * it "holds up to 50,000,000 coins"), and the 242
 * cache: clans_storage_main (696, "Coffer", the coins and the withdraw buttons) and clans_storage_side (697, the
 * inventory's coins and the deposit buttons), each with buttons 1, 1k, 1m, 10m, X and All; the coins in it are varclan_3
 * ([osrsps.game.social.Clans.VARCLAN_COFFER]); the permissions are varclansettings 19 and 20.
 *
 * - Use opens both for a member of the hall's clan; anyone else is told they aren't one (guessed line).
 * - A withdraw button takes that many (All: everything) for those the Withdraw from coffer permission allows, as many as
 *   the coffer has and the inventory's coin stack has room for, and tells every online member.
 * - A deposit button puts that many in from the inventory (All: every coin), for those the Deposit into coffer permission
 *   allows, up to [LIMIT] in the coffer. Nobody here is an Ironman, and every account counts as secured, so the
 *   Coffer page's two refusals for those never come up.
 * - X asks for the amount first.
 *
 * Guessed: every line and the default withdraw rank (the owner).
 */
class ClanCoffer(private val world: World) {
    fun register() {
        world.handlers.onLoc(COFFER, USE) {
            arriveDelay()
            open(player)
        }
        for ((component, amount) in MAIN_BUTTONS) world.handlers.onButton(MAIN, component, 1) { withdraw(this, amount) }
        for ((component, amount) in SIDE_BUTTONS) world.handlers.onButton(SIDE, component, 1) { deposit(this, amount) }
    }

    private fun clanHere(player: Player) = world.clanHall.clanAt(player.x, player.z)?.let { world.clans.clanById(it) }

    fun open(player: Player) {
        val clan = clanHere(player)
        if (clan == null || clan.member(player.saveKey) == null) return world.message(player, ClanCofferLines.NOT_MEMBER)
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, MAIN)
        world.interfaces.openModal(player, FrameSlot.SIDEMODAL, SIDE)
    }

    private suspend fun amount(context: ScriptContext, button: Int): Int? {
        if (button != X) return button
        val typed = context.countDialog()
        return typed.takeIf { it > 0 }
    }

    /** The tick of each player's last coffer transfer, by save key: one per [COOLDOWN_TICKS] (Codex on PR 954). */
    private val lastTransfer = HashMap<String, Long>()

    /** The tick of each clan's last coffer transfer, by clan id: one per tick, whoever moves them (exploit review R58C-3). */
    private val lastClanTransfer = HashMap<Long, Long>()

    /** Whether [player] may move [clan]'s coins now; marks the tick when so. */
    private fun ready(player: Player, clan: ClanStore.Clan): Boolean {
        val last = lastTransfer[player.saveKey]
        if (last != null && world.tick - last < COOLDOWN_TICKS) return false
        if (lastClanTransfer[clan.id] == world.tick) return false
        // Only the transfers still cooling down are kept (exploit review R59C-1, the same growth as the party chest's).
        lastTransfer.entries.removeIf { world.tick - it.value >= COOLDOWN_TICKS }
        lastClanTransfer.entries.removeIf { it.value != world.tick }
        lastTransfer[player.saveKey] = world.tick
        lastClanTransfer[clan.id] = world.tick
        return true
    }

    private suspend fun withdraw(context: ScriptContext, button: Int) {
        val player = context.player
        if (player.modals[FrameSlot.MAINMODAL] != MAIN) return
        val first = clanHere(player)?.takeIf { it.member(player.saveKey) != null } ?: return
        if (!world.clans.mayWithdraw(player, first)) return world.message(player, ClanCofferLines.NO_WITHDRAW)
        val wanted = amount(context, button) ?: return
        // X waited on the amount: still in the hall's clan, still allowed (Codex on PR 954).
        if (player.modals[FrameSlot.MAINMODAL] != MAIN) return
        val clan = clanHere(player)?.takeIf { it === first && it.member(player.saveKey) != null } ?: return
        if (!world.clans.mayWithdraw(player, clan)) return world.message(player, ClanCofferLines.NO_WITHDRAW)
        if (clan.coffer <= 0) return world.message(player, ClanCofferLines.EMPTY)
        val room = (Int.MAX_VALUE.toLong() - player.inventory.count(COINS)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val hasSlot = player.inventory.count(COINS) > 0 || player.inventory.freeSlots > 0
        val count = minOf(if (button == ALL) clan.coffer else wanted, clan.coffer, room)
        if (count <= 0 || !hasSlot) return world.message(player, ClanCofferLines.NO_ROOM)
        val type = world.objTypes[COINS] ?: return
        if (!ready(player, clan)) return
        // The coffer's side is written first, then the player's: a stop between the two loses the coins rather than
        // making them twice (Codex on PR 954). A coffer that can't be written gives nothing (exploit review R58C-2).
        if (!world.clans.changeCoffer(clan, -count, player)) return
        val added = player.inventory.add(type, count)
        if (added < count) world.clans.changeCoffer(clan, count - added, player)
        world.save(player)
    }

    private suspend fun deposit(context: ScriptContext, button: Int) {
        val player = context.player
        if (player.modals[FrameSlot.SIDEMODAL] != SIDE) return
        val first = clanHere(player)?.takeIf { it.member(player.saveKey) != null } ?: return
        if (!world.clans.mayDeposit(player, first)) return world.message(player, ClanCofferLines.NO_DEPOSIT)
        val wanted = amount(context, button) ?: return
        // X waited on the amount: still in the hall's clan, still allowed (Codex on PR 954).
        if (player.modals[FrameSlot.SIDEMODAL] != SIDE) return
        val clan = clanHere(player)?.takeIf { it === first && it.member(player.saveKey) != null } ?: return
        if (!world.clans.mayDeposit(player, clan)) return world.message(player, ClanCofferLines.NO_DEPOSIT)
        val held = player.inventory.count(COINS).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (held <= 0) return world.message(player, ClanCofferLines.NO_COINS)
        val space = LIMIT - clan.coffer
        if (space <= 0) return world.message(player, ClanCofferLines.FULL, "limit" to LIMIT)
        val type = world.objTypes[COINS] ?: return
        if (!ready(player, clan)) return
        var left = minOf(if (button == ALL) held else wanted, held, space)
        val count = left
        for ((slot, obj) in player.inventory.contents()) {
            if (left == 0) break
            if (obj.id == COINS) left -= player.inventory.remove(slot, left)
        }
        // The player's side is written first, then the coffer's: a stop between the two loses the coins rather than
        // making them twice (Codex on PR 954). When the player's save can't be written the coins go back and the coffer
        // isn't credited (exploit review R58C-1); when the coffer can't be, they go back too.
        val moved = count - left
        if (!world.save(player) || !world.clans.changeCoffer(clan, moved, player)) {
            player.inventory.add(type, moved)
            world.save(player)
        }
    }

    companion object {
        /** clan_hall_storage, the Clan Coffer. */
        const val COFFER = 41626
        const val USE = 1

        const val MAIN = 696
        const val SIDE = 697

        const val COINS = ShopScreen.COINS

        /** The buttons' amounts: X asks, All is everything. */
        const val X = -1
        const val ALL = -2

        /** clans_storage_main's a, b, c, d, x and all, and clans_storage_side's. */
        val MAIN_BUTTONS = mapOf(7 to 1, 8 to 1_000, 9 to 1_000_000, 10 to 10_000_000, 11 to X, 12 to ALL)
        val SIDE_BUTTONS = mapOf(5 to 1, 6 to 1_000, 7 to 1_000_000, 8 to 10_000_000, 9 to X, 10 to ALL)

        /**
         * Ticks between one player's coffer transfers, since each writes the clan file and the player's save at once
         * (guessed: live's pace isn't known; Codex on PR 954).
         */
        const val COOLDOWN_TICKS = 2L

        /** The most coins a coffer holds (the Wiki's Clan Coffer page). */
        const val LIMIT = 50_000_000

        /** The names the startup check ([osrsps.cache.GameFrameCheck]) holds these ids to. */
        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>> = mapOf(
            MAIN to ("clans_storage_main" to mapOf(7 to "a", 8 to "b", 9 to "c", 10 to "d", 11 to "x", 12 to "all")),
            SIDE to ("clans_storage_side" to mapOf(5 to "a", 6 to "b", 7 to "c", 8 to "d", 9 to "x", 10 to "all")),
        )
    }
}
