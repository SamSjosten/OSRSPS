package osrsps.game.content

import net.rsprot.protocol.common.game.outgoing.inv.InventoryObject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.inv.UpdateInvFull
import osrsps.game.FrameSlot
import osrsps.game.GroundObjs
import osrsps.game.Inventory
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.bank.BankScreen
import osrsps.game.dialogue.Lines
import osrsps.game.instance.Instance
import osrsps.game.shop.ShopScreen
import osrsps.game.social.ClanStore
import osrsps.game.social.Clans
import osrsps.game.zone.ZoneLoc
import osrsps.map.LocShape

/** The clan hall party chest's and lever's lines (guessed: the Wiki gives none). */
object ClanPartyLines : Lines("clan_party", "Clan hall party chest") {
    val NO_DEPOSIT = line("no_deposit", "Deposit by a rank below the clan's Deposit in the party chest permission (guessed)")
    val NO_LEVER = line("no_lever", "Pull by a rank below the clan's Pull the lever permission (guessed)")
    val NOT_TRADEABLE = line("not_tradeable", "offering an item that can't be traded (guessed, as Falador's chest)")
    val OFFER_FULL = line("offer_full", "offering with the 8 offer slots taken (guessed)")
    val CHEST_FULL = line("chest_full", "Confirm with no room left in the 216-slot chest (guessed)")
    val EMPTY = line("empty", "Pull with nothing in the chest (guessed)")
    val NO_COINS = line("no_coins", "Pull without the coins it costs in the inventory (guessed)", "cost")
    val NO_ROOM = line("no_room", "Pull with no free tile in the hall for a balloon; no coins are taken (guessed)")
    val DROP_ON = line("drop_on", "Pull while the balloons of the last pull are still falling (guessed)")
    val PULLED = line("pulled", "the lever pulled: the clan hall is told (guessed)", "name")
    val MAY_DEPOSIT = line("may_deposit", "Check-permissions: may deposit (guessed)")
    val MAY_NOT_DEPOSIT = line("may_not_deposit", "Check-permissions: may not deposit (guessed)")
    val MAY_LEVER = line("may_lever", "Check-permissions: may pull the lever (guessed)")
    val MAY_NOT_LEVER = line("may_not_lever", "Check-permissions: may not pull the lever (guessed)")
    val MAY_BALLOONS = line("may_balloons", "Check-permissions: may get drops from balloons (guessed)")
    val MAY_NOT_BALLOONS = line("may_not_balloons", "Check-permissions: may not get drops from balloons (guessed)")
}

/**
 * The party chest and lever in each clan hall: clan_hall_party_room_chest_closed (46248, Open, Check-permissions) and
 * its open look (46249, Deposit, Shut, Check-permissions) at 1763, 5462, and clan_hall_party_room_lever (46250, Pull) at
 * 1764, 5463. Sources: the OSRS Wiki's Clan hall page, read 2026-11-19 ("works much like the chest in the Falador Party
 * Room"; "up to 216 unique items can be deposited, and players must pay 1,000 coins to pull the lever to begin the drop
 * party"; permissions set "the minimum rank required to deposit items, pulling the lever, and be eligible for items from
 * the party chest when popping balloons"; "if all non-guests leave the Clan Hall, all dropped and deposited items will be
 * deleted"), its Party Room page ("Players can donate items to the party chest, but cannot withdraw them again") and
 * Party balloon page ("A balloon will remain on the ground for 2 minutes before despawning"; Burst gives "a chance of an
 * item from the nearby chest"), and the 242 cache: the window clan_hall_partydrop_main (783) with partydrop_side (266)
 * beside it; the chest's contents are inv clan_hall_partychest (916, 216 slots), sent as the other copy (the client reads
 * it with invother_getobj); what is offered before Confirm is inv partyroom_tempinv (92, 8 slots); the quantity buttons'
 * pick is varbit farming_tools_selectedquantity (7792: 0 is 1, 1 is 5, 2 is All, 3 is X); the permissions are
 * varclansettings 94 to 96 ([Clans.VAR_PARTY_DEPOSIT]); the balloons are balloon_yellow to balloon3 (115 to 122, Burst)
 * and their burst looks (123 to 130).
 *
 * - Open and Shut swap the chest's look for everyone in the hall (guessed: it may be each player's own).
 *   Check-permissions says which of the three the player may do.
 * - Deposit opens the window for those the Deposit permission allows. Offer and Withdraw move items between the
 *   inventory and the offer (op 1 is the picked quantity, ops 2 to 5 are 1, 5, X and All, as partydrop_side_drawitem
 *   lays them out); only tradeable items go in (guessed, as Falador's). The offer sits in the player's price checker
 *   inventory, which saves, deaths and the raid's checks already count as carried, so nothing is lost if the window
 *   never closes; closing it gives the offer back. Confirm puts the offer in the chest, which can't be undone.
 * - Pull, for those the lever permission allows, costs 1,000 coins from the inventory and drops balloons over the
 *   hall's main room. Each stays 2 minutes (the Wiki). Burst pops it, and for those the balloons permission allows it
 *   may drop one chest slot under it, with the chance the chest's filled slots over the balloons still up, so the last
 *   balloons empty the chest (guessed: the Wiki says only that more items give a better chance). Bursting without the
 *   permission takes nothing from the chest, as the Wiki says of Ironmen.
 * - Each hall's chest goes with its instance when the hall empties.
 *
 * Guessed: every line, how many balloons fall ([balloonCount]), where ([DROP_AREA]), that they all fall at once, that
 * Pull needs something in the chest and no drop already falling, and that the chest stacks as an inventory does.
 * Not built: the drag reordering of partydrop_side, Party Pete's removing of cheap items (Falador only).
 */
class ClanParty(private val world: World) {
    private class Party(val instance: Instance) {
        val chest = Inventory(CHEST_INV, CHEST_SIZE)
        val balloons = ArrayList<ZoneLoc>()

        /** The tick of this chest's last confirm: one a tick, whoever confirms (exploit review R59C-1). */
        var lastConfirm = -1L
    }

    private val parties = HashMap<Long, Party>()

    /** The tick of each player's last confirm that moved items, by save key: one per [CONFIRM_TICKS] (Codex on PR 959). */
    private val lastConfirm = HashMap<String, Long>()

    fun register() {
        world.handlers.onLoc(CHEST_CLOSED, OPEN) { op ->
            arriveDelay()
            val loc = op.loc ?: return@onLoc
            world.locs.replace(loc, CHEST_OPEN)
        }
        world.handlers.onLoc(CHEST_OPEN, SHUT) { op ->
            arriveDelay()
            val loc = op.loc ?: return@onLoc
            world.locs.replace(loc, CHEST_CLOSED)
        }
        world.handlers.onLoc(CHEST_OPEN, DEPOSIT) {
            arriveDelay()
            open(player)
        }
        for (id in listOf(CHEST_CLOSED, CHEST_OPEN)) {
            world.handlers.onLoc(id, CHECK_PERMISSIONS) {
                arriveDelay()
                checkPermissions(player)
            }
        }
        world.handlers.onLoc(LEVER, PULL) {
            arriveDelay()
            pull(player)
        }
        for (id in BALLOONS) {
            world.handlers.onLoc(id, BURST) { op ->
                arriveDelay()
                val loc = op.loc ?: return@onLoc
                burst(player, loc)
            }
        }
        world.interfaces.onClose(MAIN, ::closed)
    }

    /** The clan whose hall [player] stands in, with that hall's party state. */
    private fun partyHere(player: Player): Pair<ClanStore.Clan, Party>? {
        val id = world.clanHall.clanAt(player.x, player.z) ?: return null
        val clan = world.clans.clanById(id) ?: return null
        val instance = world.instances.at(player.x, player.z) ?: return null
        val live = world.instances.all.toHashSet()
        parties.entries.removeIf { it.value.instance !in live }
        val party = parties[id]?.takeIf { it.instance === instance } ?: Party(instance).also { parties[id] = it }
        // The Wiki: "if all non-guests leave the Clan Hall, all dropped and deposited items will be deleted". Checked
        // whenever the party is used, so a guest left behind finds it empty (Codex on PR 959).
        val membersIn = world.onlinePlayers.any { clan.member(it.saveKey) != null && world.instances.at(it.x, it.z) === instance }
        if (!membersIn && (!party.chest.isEmpty() || party.balloons.isNotEmpty())) {
            party.chest.clear()
            for (balloon in party.balloons) world.locs.remove(balloon)
            party.balloons.clear()
            for (viewer in viewers(party)) sendChest(viewer, party)
        }
        return clan to party
    }

    /** The filled slots of the chest in hall [instance]; 0 when it has none. */
    fun chestCount(instance: Instance): Int = parties.values.firstOrNull { it.instance === instance }?.chest?.contents()?.size ?: 0

    fun isOpen(player: Player): Boolean = player.modals[FrameSlot.MAINMODAL] == MAIN

    fun open(player: Player) {
        val (clan, party) = partyHere(player) ?: return
        if (!world.clans.mayParty(player, clan, Clans.VAR_PARTY_DEPOSIT)) return world.message(player, ClanPartyLines.NO_DEPOSIT)
        world.interfaces.closeModals(player)
        world.interfaces.setVarbitNow(player, VARBIT_QUANTITY, quantity(player))
        sendChest(player, party)
        sendOffer(player)
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, MAIN)
        world.interfaces.openModal(player, FrameSlot.SIDEMODAL, SIDE)
        player.session.queue(IfSetEventsV2(SIDE, SIDE_ITEMS, 0, Inventory.INV_CAPACITY - 1, 0, ITEM_OPS))
        player.session.queue(IfSetEventsV2(MAIN, OFFER_ITEMS, 0, OFFER_SIZE - 1, 0, ITEM_OPS))
        player.session.queue(IfSetEventsV2(MAIN, CHEST_ITEMS, 0, CHEST_SIZE - 1, 0, BankScreen.opMask(10..10)))
    }

    /** The window closed: the offer not yet confirmed goes back, as the price checker's does (not on a death). */
    private fun closed(player: Player) {
        if (player.dead) return
        for ((slot, obj) in player.priceChecker.contents()) {
            val type = world.objTypes[obj.id] ?: continue
            player.priceChecker[slot] = null
            val left = obj.count - player.inventory.add(type, obj.count)
            if (left > 0) world.groundObjs.addPrivate(type, left, player.x, player.z, player.level, player.saveKey, GroundObjs.DROP_PRIVATE_TICKS, GroundObjs.DROP_DESPAWN_TICKS)
        }
    }

    /** A click on the window or the inventory beside it; false when it is on neither. */
    fun click(player: Player, interfaceId: Int, component: Int, sub: Int, op: Int, obj: Int): Boolean {
        if (interfaceId != MAIN && interfaceId != SIDE) return false
        if (!isOpen(player)) return true
        when {
            interfaceId == MAIN && component in QUANTITY_BUTTONS -> world.interfaces.setVarbitNow(player, VARBIT_QUANTITY, QUANTITY_BUTTONS.getValue(component))
            interfaceId == SIDE && component == SIDE_ITEMS -> clickItem(player, player.inventory, player.priceChecker, sub, op, obj, offering = true)
            interfaceId == MAIN && component == OFFER_ITEMS -> clickItem(player, player.priceChecker, player.inventory, sub, op, obj, offering = false)
            interfaceId == MAIN && component == CHEST_ITEMS && op == EXAMINE -> {
                // The chest of the hall the player is in, without partyHere's walk over every player (exploit review R59C-2).
                val instance = world.instances.at(player.x, player.z) ?: return true
                val party = parties.values.firstOrNull { it.instance === instance } ?: return true
                val item = party.chest[sub.coerceIn(0, CHEST_SIZE - 1)] ?: return true
                world.objTypes[item.id]?.let { world.inventoryTab.examine(player, it, item.count) }
            }
            interfaceId == MAIN && component == CONFIRM -> confirm(player)
        }
        return true
    }

    private fun clickItem(player: Player, from: Inventory, to: Inventory, slot: Int, op: Int, clicked: Int, offering: Boolean) {
        if (slot !in 0 until from.capacity) return
        val item = from[slot]?.takeIf { clicked < 0 || it.id == clicked } ?: return
        if (op == EXAMINE) {
            world.objTypes[item.id]?.let { world.inventoryTab.examine(player, it, item.count) }
            return
        }
        val amount = when (op) {
            1 -> PICKS[quantity(player)]
            2 -> 1
            3 -> 5
            4 -> X
            5 -> ALL
            else -> return
        }
        if (amount != X) return move(player, from, to, item.id, amount, offering)
        if (player.scripts.delayed) return
        world.scripts.start(player) {
            val typed = countDialog()
            if (typed > 0 && isOpen(player)) move(player, from, to, item.id, typed, offering)
        }
    }

    private fun quantity(player: Player): Int =
        if (player.vars.types.varbit(VARBIT_QUANTITY) != null) player.vars.getVarbit(VARBIT_QUANTITY).coerceIn(0, PICKS.size - 1) else 0

    /** Moves up to [count] of [id] from [from] to [to]; an offer takes tradeable items only, in its 8 slots. */
    private fun move(player: Player, from: Inventory, to: Inventory, id: Int, count: Int, offering: Boolean) {
        val type = world.objTypes[id] ?: return
        if (offering && !type.tradeable) return world.message(player, ClanPartyLines.NOT_TRADEABLE)
        var left = minOf(count.toLong(), from.count(id)).toInt()
        while (left > 0) {
            if (offering && !(type.stackable && to.count(id) > 0) && to.contents().size >= OFFER_SIZE) {
                world.message(player, ClanPartyLines.OFFER_FULL)
                break
            }
            val slot = from.contents().firstOrNull { it.second.id == id }?.first ?: break
            val added = to.add(type, minOf(left, from[slot]!!.count))
            if (added <= 0) break
            from.remove(slot, added)
            left -= added
        }
        sendOffer(player)
    }

    /** Confirm: the offer into the chest, for good; what doesn't fit stays offered. */
    private fun confirm(player: Player) {
        if (player.priceChecker.contents().isEmpty()) return
        // Each confirm writes the donor's save, so they're spaced out: a client can't stall the tick with saves. The
        // player's own cooldown is checked and marked first, before partyHere walks every online player, so spammed
        // clicks cost nothing (exploit review R62B-2); and the map keeps only the confirms still cooling down (R59C-1).
        val last = lastConfirm[player.saveKey]
        if (last != null && world.tick - last < CONFIRM_TICKS) return
        lastConfirm.entries.removeIf { world.tick - it.value >= CONFIRM_TICKS }
        lastConfirm[player.saveKey] = world.tick
        val (clan, party) = partyHere(player) ?: return
        if (!world.clans.mayParty(player, clan, Clans.VAR_PARTY_DEPOSIT)) return world.message(player, ClanPartyLines.NO_DEPOSIT)
        // One confirm per chest a tick, and one disk-writing action a tick across every clan (R59C-1, R60C-1).
        if (party.lastConfirm == world.tick) return
        if (!world.clans.takeDurableWrite(world.tick)) return
        party.lastConfirm = world.tick
        var full = false
        val moved = ArrayList<Pair<osrsps.game.ObjType, Int>>()
        for ((slot, obj) in player.priceChecker.contents()) {
            val type = world.objTypes[obj.id] ?: continue
            val added = party.chest.add(type, obj.count)
            if (added > 0) {
                player.priceChecker.remove(slot, added)
                moved += type to added
            }
            if (added < obj.count) full = true
        }
        // The donor's save is written before anyone can get the items from a balloon, so a crash can't give them back
        // to the donor as well (Codex on PR 959). If it can't be written, the offer goes back as it was.
        if (moved.isNotEmpty() && !world.save(player)) {
            for ((type, count) in moved) {
                var left = count
                for ((slot, obj) in party.chest.contents()) {
                    if (left == 0) break
                    if (obj.id == type.id) left -= party.chest.remove(slot, left)
                }
                player.priceChecker.add(type, count - left)
            }
            sendOffer(player)
            return
        }
        if (full) world.message(player, ClanPartyLines.CHEST_FULL)
        sendOffer(player)
        for (viewer in viewers(party)) sendChest(viewer, party)
    }

    private fun checkPermissions(player: Player) {
        val clan = partyHere(player)?.first ?: return
        val may = { id: Int -> world.clans.mayParty(player, clan, id) }
        world.message(player, if (may(Clans.VAR_PARTY_DEPOSIT)) ClanPartyLines.MAY_DEPOSIT else ClanPartyLines.MAY_NOT_DEPOSIT)
        world.message(player, if (may(Clans.VAR_PARTY_LEVER)) ClanPartyLines.MAY_LEVER else ClanPartyLines.MAY_NOT_LEVER)
        world.message(player, if (may(Clans.VAR_PARTY_BALLOONS)) ClanPartyLines.MAY_BALLOONS else ClanPartyLines.MAY_NOT_BALLOONS)
    }

    private fun pull(player: Player) {
        val (clan, party) = partyHere(player) ?: return
        if (!world.clans.mayParty(player, clan, Clans.VAR_PARTY_LEVER)) return world.message(player, ClanPartyLines.NO_LEVER)
        party.balloons.removeIf { !world.locs.exists(it) }
        if (party.balloons.isNotEmpty()) return world.message(player, ClanPartyLines.DROP_ON)
        val filled = party.chest.contents().size
        if (filled == 0) return world.message(player, ClanPartyLines.EMPTY)
        if (player.inventory.count(COINS) < COST) return world.message(player, ClanPartyLines.NO_COINS, "cost" to COST)
        val instance = party.instance
        val tiles = ArrayList<Pair<Int, Int>>()
        for (x in DROP_AREA.first.first..DROP_AREA.second.first) {
            for (z in DROP_AREA.first.second..DROP_AREA.second.second) {
                val wx = instance.baseX + x - ClanHall.TEMPLATE_X
                val wz = instance.baseZ + z - ClanHall.TEMPLATE_Z
                if (world.canStand(wx, wz, 0) && world.locs.at(wx, wz, 0).none { it.layer == GROUND_LAYER }) tiles += wx to wz
            }
        }
        // The free tiles are counted before the coins are taken, so a hall with none costs nothing (exploit review R59C-4).
        if (tiles.isEmpty()) return world.message(player, ClanPartyLines.NO_ROOM)
        var owed = COST
        for ((slot, obj) in player.inventory.contents()) {
            if (owed == 0) break
            if (obj.id == COINS) owed -= player.inventory.remove(slot, owed)
        }
        val count = minOf(balloonCount(filled), tiles.size)
        for (i in 0 until count) {
            val j = i + world.random.nextInt(tiles.size - i)
            tiles[i] = tiles[j].also { tiles[j] = tiles[i] }
            val (x, z) = tiles[i]
            val loc = ZoneLoc(world.random.pick(BALLOONS), LocShape.CENTREPIECE_STRAIGHT, 0, x, z, 0)
            world.locs.add(loc, revertAfter = BALLOON_TICKS)
            party.balloons += loc
        }
        for (other in world.onlinePlayers) {
            if (world.instances.at(other.x, other.z) === instance) world.message(other, ClanPartyLines.PULLED, "name" to player.username)
        }
    }

    private fun burst(player: Player, loc: ZoneLoc) {
        if (!world.locs.exists(loc)) return
        val found = partyHere(player)
        val party = found?.second?.takeIf { it.balloons.contains(loc) }
        // A party balloon is burst only by those the balloons permission allows, so others can't end a drop (exploit
        // review R59C-3; guessed: live's refusal isn't known).
        if (party != null && !world.clans.mayParty(player, found.first, Clans.VAR_PARTY_BALLOONS)) {
            return world.message(player, ClanPartyLines.MAY_NOT_BALLOONS)
        }
        val pop = POPS[BALLOONS.indexOf(loc.id).coerceAtLeast(0)]
        // The pop shows for a moment, then the tile is empty: not the balloon again (Codex on PR 959).
        world.locs.remove(loc)
        world.locs.add(loc.copy(id = pop), revertAfter = POP_TICKS)
        if (party == null) return
        party.balloons.removeIf { it == loc || !world.locs.exists(it) }
        val filled = party.chest.contents()
        if (filled.isEmpty()) return
        // This balloon counts among those still up.
        if (world.random.nextInt(party.balloons.size + 1) >= filled.size) return
        val (slot, item) = filled[world.random.nextInt(filled.size)]
        party.chest[slot] = null
        world.objTypes[item.id]?.let { world.groundObjs.addPublic(it, item.count, loc.x, loc.z, loc.level, GroundObjs.DROP_DESPAWN_TICKS) }
        for (viewer in viewers(party)) sendChest(viewer, party)
    }

    private fun viewers(party: Party): List<Player> =
        world.onlinePlayers.filter { isOpen(it) && world.instances.at(it.x, it.z) === party.instance }

    private fun sendChest(player: Player, party: Party) {
        player.session.queue(UpdateInvFull(OTHER, CHEST_INV, CHEST_SIZE) { slot -> objectAt(party.chest, slot) })
    }

    private fun sendOffer(player: Player) {
        player.session.queue(UpdateInvFull(OFFER_INV, OFFER_SIZE) { slot -> objectAt(player.priceChecker, slot) })
    }

    private fun objectAt(inv: Inventory, slot: Int): Long =
        inv[slot]?.let { InventoryObject(slot, it.id, it.count) } ?: InventoryObject.NULL

    companion object {
        const val CHEST_CLOSED = 46248
        const val CHEST_OPEN = 46249
        const val LEVER = 46250
        const val OPEN = 1
        const val DEPOSIT = 2
        const val SHUT = 3
        const val CHECK_PERMISSIONS = 4
        const val PULL = 1

        /** Ticks between one player's confirms. A guess: live's rate isn't known. */
        const val CONFIRM_TICKS = 2L
        const val BURST = 1

        /** balloon_yellow to balloon3, and each one's burst look. */
        val BALLOONS = (115..122).toList()
        val POPS = (123..130).toList()

        const val MAIN = 783
        const val SIDE = 266
        const val SIDE_ITEMS = 0
        const val CHEST_ITEMS = 11
        const val OFFER_ITEMS = 16
        const val CONFIRM = 17

        /** clan_hall_partydrop_main's 1, 5, X and All, to farming_tools_selectedquantity's values. */
        val QUANTITY_BUTTONS = mapOf(7 to 0, 8 to 1, 9 to 3, 10 to 2)
        const val VARBIT_QUANTITY = 7792

        /** The amounts: X asks, All is everything. */
        private const val X = -1
        private const val ALL = Int.MAX_VALUE
        private val PICKS = listOf(1, 5, ALL, X)
        private const val EXAMINE = 10
        private val ITEM_OPS = BankScreen.opMask(1..5) or BankScreen.opMask(10..10)

        const val CHEST_INV = 916
        const val CHEST_SIZE = 216
        const val OFFER_INV = 92
        const val OFFER_SIZE = 8

        /** Any combined id under -70000 sends an inventory's other copy, as a trade partner's offer goes. */
        private const val OTHER = -70_002

        const val COINS = ShopScreen.COINS
        const val COST = 1_000

        /** 2 minutes, the Wiki's Party balloon page; and how long a burst one shows (guessed). */
        const val BALLOON_TICKS = 200
        const val POP_TICKS = 2
        private const val GROUND_LAYER = 2

        /** The main room's floor in the hall's template, south-west and north-east corners (guessed from the map). */
        val DROP_AREA = (1752 to 5461) to (1767 to 5485)

        /** Balloons a pull drops for [filled] chest slots: two a slot, 20 to 120 (guessed). */
        fun balloonCount(filled: Int): Int = (filled * 2).coerceIn(20, 120)

        /** The names the startup check ([osrsps.cache.GameFrameCheck]) holds these ids to. */
        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>> = mapOf(
            MAIN to (
                "clan_hall_partydrop_main" to
                    mapOf(7 to "1", 8 to "5", 9 to "x", 10 to "all", CHEST_ITEMS to "chest_items", OFFER_ITEMS to "offer_items", CONFIRM to "confirm")
                ),
            SIDE to ("partydrop_side" to mapOf(SIDE_ITEMS to "items")),
        )
    }
}
