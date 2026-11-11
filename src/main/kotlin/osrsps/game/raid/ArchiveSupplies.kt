package osrsps.game.raid

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.InvObj
import osrsps.game.Inventory
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.npc.NpcSpawn
import osrsps.game.shop.OmnishopOwner
import osrsps.game.shop.OmnishopType
import osrsps.game.zone.ZoneLoc
import osrsps.map.LocShape

private val logger = KotlinLogging.logger {}

/**
 * The Quartermaster's Talk-to, from the Wiki's Transcript:Quartermaster (The Fractured Archive) (marked incomplete
 * there: the branch after "keep it in mind" isn't written down). The words are in the dialogue file.
 */
object QuartermasterLines : Lines("tfa_quartermaster", "Transcript:Quartermaster_(The_Fractured_Archive)") {
    val HELLO = line("hello", "Talk-to: what the player says first, a greeting")
    val GREETING = line("greeting", "his answer: the supplies he has for the party, and whether the player wants to see the stock")
    val RESUPPLY = line("resupply", "menu option 1 (and the second menu's option 1), which the player then says: they'd like to resupply")
    val WHO = line("who", "menu option 2, which the player then says: who he is")
    val FINE = line("fine", "menu option 3, which the player then says: fine for now, thanks")
    val AGREE = line("agree", "after resupply: his answer, before the shop opens")
    val INTRO = line("intro", "who he is: his answer, Liam the Quartermaster, who supports others in combat")
    val KEEP_IN_MIND = line("keep_in_mind", "the second menu, option 2, which the player then says: they'll keep it in mind; the player says it too after his advice")
    val ADVICE = line("advice", "after fine for now: his answer, that stock runs low quickly, so chances to resupply are few")
}

/**
 * The Quartermaster and his Alliance Supplies (raid step 21): a shop in the nexus for part of a run, paid for in Supply
 * Credits.
 *
 * - **When.** "He appears after the third boss encounter, Moia or Dreadnoth the Jellied, has been defeated" (the Wiki's
 *   Quartermaster page), with his Supplies Wheelbarrow beside him, "only on the visit after" that room (tsps capture
 *   notes: NPC 16761 `tfa_midraid_supplies_npc` at 4129,5609 and loc 63302 at 4130,5608, seen from a spectator). So
 *   he comes as the third row's room is done and goes as the fourth row's fight begins. Guessed: that he goes then
 *   rather than as the party walks through the door, his facing and the wheelbarrow's angle.
 * - **Resupply** (op 1) opens Alliance Supplies, the cache's omnishop row `tfa_midraid_supplies_shop` (17150) on the
 *   omnishop interface ([osrsps.game.shop.OmnishopScreen]); its prices are the cache's (Saradomin brew(4) and Super
 *   restore(4) 3 credits; Prayer potion(4), Manta ray, Halibut, Ranging potion(2), Super combat potion(2) 2; Giant
 *   krill and Prayer potion(2) 1; the Alliance log shown only). Each member has their own stock, the Wiki's shop
 *   page's ([STOCK]: 6 brews, 4 restores, 4 Prayer potion(4), 10 Manta rays, 5 Halibut, 10 krill, one of each
 *   two-dose potion). Guessed: that the stock is each member's own (the client counts it from inv 1030, which the
 *   server sends each player alone) rather than the party's.
 * - **The failure shop.** The row `tfa_midraid_supplies_shop_failure` (17151) adds Leftovers, free, "only offered with
 *   poor contribution" (the Wiki's shop page, marked as needing confirmation) or "when the first three fights went
 *   poorly, such as with multiple deaths" (the Wiki's Leftovers page). Guessed: it is offered once the party has died
 *   [FAILURE_DEATHS] times or more in the run, and [LEFTOVERS_STOCK] Leftovers. Leftovers heal 5 (the Wiki), in food.json.
 * - **Supply Credits**, `tfa_supply_points` (16381, seven bits), shown by the shop itself. Guessed: every member in
 *   the run as he comes has [CREDITS] (guides speak of 50 personal credits; the tsps notes estimate 10 for each room
 *   won), and they are gone with the next run (set to 0 as a player comes through the Archive Door).
 * - **Only for the raid.** What a member holds of the supplies' kinds as they get out of the run (a teleport, the Rift
 *   out, leaving the party, logging out, the instance's end) or lose it (a wipe, dying of an escape while down), beyond
 *   what they brought in through the Archive Door, is taken: doses of each potion, whatever its doses, and items of
 *   the rest ([settle]); what a member brought in and traded, or dropped, to another goes with its allowance ([traded],
 *   [pickedUp]), and none is made into another kind in the run ([keepsFromMaking]). Without
 *   it the stock, the ordinary items, left the raid to be banked or sold (exploit review round 26, R26A-1). Guessed: live's stock is the same items (the cache's rows), and whether live takes them back,
 *   and how, isn't known; nothing is said.
 * - **Talk-to** (op 3) is the Wiki's transcript ([QuartermasterLines]). Guessed: every chathead expression, and that
 *   "I'll keep that in mind" from his second menu ends the talk (his answer to it isn't written down). Not in: his overhead lines, and the cache's inactive Quartermaster (16762,
 *   Talk-to only), whose use isn't known.
 */
class ArchiveSupplies(private val world: World, private val raids: ArchiveRaids) {
    fun register() {
        world.handlers.onNpc(QUARTERMASTER, RESUPPLY) { op -> if (raids.at(player.x, player.z) != null) open(player) }
        world.handlers.onNpc(QUARTERMASTER, TALK_TO) { op ->
            world.dialogues.start(this, op.npc) { talk() }
        }
    }

    /** As [player] comes into [run]: no credits from an earlier one, and what they bring in of the supplies noted. */
    fun entered(run: ArchiveRun, player: Player) {
        setCredits(player, 0)
        settle(run, player)
        run.suppliesBrought[player.saveKey] = held(player)
    }

    /**
     * [player] is out of [run], or losing what they hold to it: what they hold of the supplies beyond what they brought
     * in is taken, the fewest doses first, a potion with more doses than is owed left with the rest: from the backpack
     * and from Guide prices' checker, whose items go back to the backpack as it closes but are left in it by a death
     * or a disconnect (Codex review on #636). A trade they are in is declined first, so its offer is back in the backpack
     * and taken with the rest: put out of the party mid-trade, nothing else declines it (Codex review on #636). Once a
     * player.
     */
    fun settle(run: ArchiveRun, player: Player) {
        if (player.saveKey !in run.suppliesBrought) return
        if (player.trade != null) world.trades.interrupt(player)
        val brought = run.suppliesBrought.remove(player.saveKey) ?: return
        // What they brought in and don't leave with, kept for their drops another member may yet pick up.
        val holding = held(player)
        val spare = brought.mapValues { (kind, units) -> units - (holding[kind] ?: 0) }.filterValues { it > 0 }
        if (spare.isNotEmpty()) run.suppliesLeft.merge(player.saveKey, spare) { a, b -> (a.keys + b.keys).associateWith { (a[it] ?: 0) + (b[it] ?: 0) } }
        val entries = listOf(player.inventory, player.priceChecker).flatMap { inv -> inv.contents().map { (slot, obj) -> Held(inv, slot, obj) } }
        val changes = excess(entries, brought)
        for ((held, obj) in changes) (held.where as Inventory)[held.slot] = obj
        if (changes.isNotEmpty()) logger.info { "$run: took back ${player.username}'s supplies from ${changes.size} slots" }
    }

    /**
     * [inventory] and [checked] (Guide prices' checker) as they are to be saved for [player]: without what a run would
     * take back from them, should they be saved still in it. A save while in a run, an autosave or the save at a
     * shutdown or a crash, is at the instance's exit, and the run is gone after a restart (Codex review on #636). So is
     * one made after they got out but before the next tick's start settles them, wherever they stand. Both
     * before the checker's items are put back, so none of them overflows into the saved bank.
     */
    fun saved(player: Player, inventory: Map<Int, InvObj>, checked: Map<Int, InvObj> = emptyMap()): Pair<Map<Int, InvObj>, Map<Int, InvObj>> {
        val run = raids.runs().firstOrNull { player.saveKey in it.suppliesBrought } ?: return inventory to checked
        val entries = inventory.map { (slot, obj) -> Held(INVENTORY, slot, obj) } + checked.map { (slot, obj) -> Held(CHECKED, slot, obj) }
        val changes = excess(entries, run.suppliesBrought.getValue(player.saveKey))
        if (changes.isEmpty()) return inventory to checked
        val outInventory = LinkedHashMap(inventory)
        val outChecked = LinkedHashMap(checked)
        for ((held, obj) in changes) {
            val out = if (held.where == INVENTORY) outInventory else outChecked
            if (obj == null) out.remove(held.slot) else out[held.slot] = obj
        }
        return outInventory to outChecked
    }

    /**
     * Before a trade's swap is saved: what [a] and [b] hold of the supplies, offers in, and their allowances now, for
     * [traded] after the swap and [untraded] if it is undone.
     */
    fun beforeTrade(a: Player, b: Player): TradeStart {
        val run = raids.at(a.x, a.z)
        return TradeStart(held(a, a.trade?.sideOf(a)?.offer), held(b, b.trade?.sideOf(b)?.offer), run?.suppliesBrought?.get(a.saveKey), run?.suppliesBrought?.get(b.saveKey))
    }

    class TradeStart internal constructor(val heldA: Map<Int, Int>, val heldB: Map<Int, Int>, val allowA: Map<Int, Int>?, val allowB: Map<Int, Int>?)

    /** A trade's swap undone: [a]'s and [b]'s allowances as they were. */
    fun untraded(a: Player, b: Player, start: TradeStart) {
        val run = raids.at(a.x, a.z) ?: return
        start.allowA?.let { run.suppliesBrought[a.saveKey] = it }
        start.allowB?.let { run.suppliesBrought[b.saveKey] = it }
    }

    /** An item in [where] (an [Inventory], or a save's map) at [slot]. */
    private data class Held(val where: Any, val slot: Int, val obj: InvObj)

    private object INVENTORY

    private object CHECKED

    /**
     * What [entries] become without what is held beyond [brought]: for each kind, the fewest doses first, a potion with
     * more doses than is owed left with the rest. Each change is an entry and what replaces it, null for nothing.
     */
    private fun excess(entries: List<Held>, brought: Map<Int, Int>): List<Pair<Held, InvObj?>> {
        val changes = ArrayList<Pair<Held, InvObj?>>()
        val byKind = entries.mapNotNull { e -> unitsOf(e.obj.id)?.let { (kind, each) -> Triple(kind, each, e) } }.groupBy { it.first }
        for ((kind, items) in byKind) {
            var over = items.sumOf { it.second * it.third.obj.count } - (brought[kind] ?: 0)
            for ((_, each, held) in items.sortedBy { it.second }) {
                if (over <= 0) break
                val units = each.toLong() * held.obj.count
                when {
                    units <= over -> changes += held to null.also { over -= units.toInt() }
                    each == 1 -> changes += held to InvObj(held.obj.id, held.obj.count - over).also { over = 0 }
                    else -> changes += held to doseOf(held.obj.id, each - over)?.let { InvObj(it, 1) }.also { over = 0 }
                }
            }
        }
        return changes
    }

    /**
     * Each tick, before the players' input and again after the NPCs: the supplies of members out of [run], by a
     * teleport, the Rift out or leaving the party, are taken back. Before the input, so one who left in the last
     * tick's script or movement can't drop them first and pick them up after (Codex review on #636).
     */
    fun settleOut(run: ArchiveRun, ended: Boolean) {
        for (key in run.suppliesBrought.keys.toList()) {
            val player = world.onlinePlayers.firstOrNull { it.saveKey == key } ?: continue
            if (ended || !run.instance.contains(player.x, player.z) || world.parties.of(player) !== run.party) settle(run, player)
        }
    }

    /**
     * [a] and [b], members of one run, have traded, holding [beforeA] and [beforeB] of the supplies before: what each
     * gave of what they brought in (beyond what they still hold of it) goes over to the other's allowance, so what a
     * member brought in and handed on isn't taken from the one who leaves with it. What they bought stays bought,
     * whoever holds it (Codex review on #636). Supplies dropped and picked up by another member: [pickedUp].
     */
    fun traded(a: Player, b: Player, start: TradeStart) {
        val beforeA = start.heldA
        val beforeB = start.heldB
        val run = raids.at(a.x, a.z) ?: return
        val allowA = run.suppliesBrought[a.saveKey]?.toMutableMap() ?: return
        val allowB = run.suppliesBrought[b.saveKey]?.toMutableMap() ?: return
        val afterA = held(a)
        val afterB = held(b)
        for (kind in beforeA.keys + beforeB.keys) {
            val gaveA = (beforeA[kind] ?: 0) - (afterA[kind] ?: 0)
            val gaveB = (beforeB[kind] ?: 0) - (afterB[kind] ?: 0)
            fun move(gave: Int, from: MutableMap<Int, Int>, after: Int, to: MutableMap<Int, Int>) {
                val spare = (from[kind] ?: 0) - after
                val moved = minOf(gave, spare)
                if (moved <= 0) return
                from[kind] = (from[kind] ?: 0) - moved
                to[kind] = (to[kind] ?: 0) + moved
            }
            move(gaveA, allowA, afterA[kind] ?: 0, allowB)
            move(gaveB, allowB, afterB[kind] ?: 0, allowA)
        }
        run.suppliesBrought[a.saveKey] = allowA
        run.suppliesBrought[b.saveKey] = allowB
    }

    /**
     * [taker], in a run, picked up [count] of [id] that the player saved as [ownerKey] ([owner] if still in the world)
     * dropped: as in a trade, what the owner brought in of it (beyond what they still hold) goes over to [taker]'s
     * allowance, so supplies a member brought in and handed on by the floor aren't taken from the one who leaves with
     * them. From [ArchiveRun.suppliesLeft] once the owner has got out. What was bought stays bought (Codex review on #636).
     */
    fun pickedUp(taker: Player, ownerKey: String, owner: Player?, id: Int, count: Int) {
        val (kind, each) = unitsOf(id) ?: return
        val run = raids.runs().firstOrNull { taker.saveKey in it.suppliesBrought } ?: return
        var wanted = each * count
        var moved = 0
        // First what they left on the floor as they got out, should they have come back in since (Codex review on #636).
        run.suppliesLeft[ownerKey]?.let { left ->
            val take = minOf(wanted, left[kind] ?: 0)
            if (take > 0) run.suppliesLeft[ownerKey] = left + (kind to (left[kind] ?: 0) - take)
            moved += take
            wanted -= take
        }
        // Then their allowance in the run, beyond what they still hold.
        val inRun = run.suppliesBrought[ownerKey]
        if (wanted > 0 && inRun != null && owner != null) {
            val take = minOf(wanted, (inRun[kind] ?: 0) - (held(owner)[kind] ?: 0))
            if (take > 0) {
                run.suppliesBrought[ownerKey] = inRun + (kind to (inRun[kind] ?: 0) - take)
                moved += take
            }
        }
        if (moved <= 0) return
        val allowTaker = run.suppliesBrought.getValue(taker.saveKey)
        run.suppliesBrought[taker.saveKey] = allowTaker + (kind to (allowTaker[kind] ?: 0) + moved)
    }

    /**
     * Whether [player], in a run, may not make [product] from [inputs]: any of the supplies' kinds made into another
     * kind (a Super restore into a Sanfew mixture), which would leave the run as an item [settle] doesn't know (Codex
     * review on #636). Guessed: live's answer isn't known; this gives "Nothing interesting happens".
     */
    fun keepsFromMaking(player: Player, inputs: List<Int>, product: Int): Boolean {
        if (raids.runs().none { player.saveKey in it.suppliesBrought }) return false
        val into = kindOf(product)
        return inputs.any { kindOf(it) in kinds && kindOf(it) != into }
    }

    /**
     * What [player] holds of the supplies' kinds: doses of each potion by its first dose obj, and items of the rest; with
     * what they have in Guide prices' checker, and [offered] (their trade offer) too.
     */
    internal fun held(player: Player, offered: Inventory? = null): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        for ((_, obj) in player.inventory.contents() + player.priceChecker.contents() + offered?.contents().orEmpty()) {
            val (kind, each) = unitsOf(obj.id) ?: continue
            out.merge(kind, each * obj.count, Int::plus)
        }
        return out
    }

    /** [id]'s kind and how many doses (or items) one is, if it is of the supplies' kinds. */
    private fun unitsOf(id: Int): Pair<Int, Int>? {
        val kind = kindOf(id)
        if (kind !in kinds) return null
        return kind to (doses(id) ?: 1)
    }

    /** A potion's first dose obj, or [id] itself for anything else (or with no potion data, as in a test world). */
    private fun kindOf(id: Int): Int = world.potions.all[id]?.doses?.firstOrNull() ?: id

    private val kinds: Set<Int> by lazy { (STOCK.keys + LEFTOVERS).map(::kindOf).toSet() }

    /** The doses of potion [id], from its name's "(n)"; null for anything else. */
    private fun doses(id: Int): Int? {
        if (world.potions.all[id] == null) return null
        return world.objTypes[id]?.name?.let { DOSES.find(it) }?.groupValues?.get(1)?.toInt()
    }

    /** The obj of [id]'s potion with [doses] doses, if there is one. */
    private fun doseOf(id: Int, doses: Int): Int? = world.potions.all[id]?.doses?.firstOrNull { doses(it) == doses }

    /** Each tick: the Quartermaster comes once the third row's room is done, and goes as the fourth row's begins. */
    fun process(run: ArchiveRun) {
        val wanted = ArchiveRoom.atStage(ARRIVES_AFTER).any { it in run.cleared } &&
            ArchiveRoom.atStage(ARRIVES_AFTER + 1).none { it in run.started } && !run.wiped
        if (wanted && run.quartermaster == null && !run.suppliesCame) arrive(run)
        if (!wanted && run.quartermaster != null) leave(run)
    }

    private fun arrive(run: ArchiveRun) {
        val type = world.npcTypes[QUARTERMASTER] ?: return
        val at = raids.tile(run, X, Z)
        val npc = world.spawnNpc(NpcSpawn(type, at.x, at.z, ArchiveRaids.NEXUS_PLANE, source = "$run", respawns = false)) ?: return
        run.quartermaster = npc
        run.suppliesCame = true
        val barrow = raids.tile(run, WHEELBARROW_X, WHEELBARROW_Z)
        world.locs.add(ZoneLoc(WHEELBARROW, LocShape.CENTREPIECE_STRAIGHT, 0, barrow.x, barrow.z, ArchiveRaids.NEXUS_PLANE))
        for (player in raids.present(run)) {
            setCredits(player, CREDITS)
            run.supplies.remove(player.saveKey)
            stockFor(run, player.saveKey)
        }
        logger.info { "$run: the Quartermaster came, ${raids.present(run).size} with $CREDITS credits" }
    }

    private fun leave(run: ArchiveRun) {
        run.quartermaster?.let { if (world.npcs[it.index] === it) world.removeNpc(it) }
        run.quartermaster = null
        val barrow = raids.tile(run, WHEELBARROW_X, WHEELBARROW_Z)
        world.locs.find(WHEELBARROW, barrow.x, barrow.z, ArchiveRaids.NEXUS_PLANE)?.let { world.locs.remove(it) }
        // Whoever still has his shop open sees it close.
        for (player in raids.present(run)) if (world.omnishopScreen.shopOf(player)?.row in listOf(SHOP, FAILURE_SHOP)) world.interfaces.closeModals(player)
    }

    /** Resupply: the shop, while he is there for [player]'s run. */
    fun open(player: Player) {
        val run = raids.at(player.x, player.z) ?: return
        if (run.quartermaster == null || player.saveKey !in run.entered) return
        val shop = shopFor(run) ?: return
        world.omnishopScreen.open(player, shop, Owner(run))
    }

    /** The shop [run] is offered: the failure row once the party has died [FAILURE_DEATHS] times. */
    private fun shopFor(run: ArchiveRun): OmnishopType? =
        world.omnishops[if (run.deaths.values.sum() >= FAILURE_DEATHS) FAILURE_SHOP else SHOP] ?: world.omnishops[SHOP]

    private suspend fun Dialogue.talk() = with(QuartermasterLines) {
        chatPlayer(ChatAnim.HAPPY, HELLO)
        chatNpc(ChatAnim.NEUTRAL, GREETING)
        when (choice(RESUPPLY, WHO, FINE)) {
            1 -> resupply()
            2 -> {
                chatPlayer(ChatAnim.QUIZ, WHO)
                chatNpc(ChatAnim.NEUTRAL, INTRO)
                when (choice(RESUPPLY, KEEP_IN_MIND)) {
                    1 -> resupply()
                    else -> chatPlayer(ChatAnim.NEUTRAL, KEEP_IN_MIND)
                }
            }
            else -> {
                chatPlayer(ChatAnim.NEUTRAL, FINE)
                chatNpc(ChatAnim.NEUTRAL, ADVICE)
                chatPlayer(ChatAnim.NEUTRAL, KEEP_IN_MIND)
            }
        }
    }

    private suspend fun Dialogue.resupply() {
        chatPlayer(ChatAnim.NEUTRAL, QuartermasterLines.RESUPPLY)
        chatNpc(ChatAnim.HAPPY, QuartermasterLines.AGREE)
        open(player)
    }

    private fun setCredits(player: Player, value: Int) {
        if (player.vars.types.varbit(CREDITS_VARBIT) == null) return
        world.interfaces.setVarbitNow(player, CREDITS_VARBIT, value.coerceIn(0, MAX_CREDITS))
    }

    private fun credits(player: Player): Int =
        if (player.vars.types.varbit(CREDITS_VARBIT) == null) 0 else player.vars.getVarbit(CREDITS_VARBIT)

    /**
     * [key]'s stock in [run]. Once the party has died enough for the failure shop, its Leftovers join the stock, once
     * per member, so a member whose stock was filled before the deaths gets them too, and nothing already bought is
     * refilled (Codex review on #631).
     */
    private fun stockFor(run: ArchiveRun, key: String): Inventory {
        val inv = run.supplies.getOrPut(key) { Inventory(STOCK_INV, STOCK_SLOTS).also { fill(it, run, key) } }
        if (shopFor(run)?.row == FAILURE_SHOP && run.suppliesLeftovers.add(key)) inv[STOCK.size] = InvObj(LEFTOVERS, LEFTOVERS_STOCK)
        return inv
    }

    private fun fill(inv: Inventory, run: ArchiveRun, key: String) {
        inv.clear()
        run.suppliesLeftovers.remove(key)
        for ((slot, entry) in STOCK.entries.withIndex()) inv[slot] = InvObj(entry.key, entry.value)
    }

    /** The shop's balance and stock for [run]'s members. */
    private inner class Owner(private val run: ArchiveRun) : OmnishopOwner {
        override fun balance(player: Player) = credits(player)

        override fun setBalance(player: Player, value: Int) = setCredits(player, value)

        override fun stock(player: Player): Inventory = stockFor(run, player.saveKey)
    }

    companion object {
        /** tfa_midraid_supplies_npc: Resupply (op 1), Talk-to (op 3); 241 cache. */
        const val QUARTERMASTER = 16761
        const val RESUPPLY = 1
        const val TALK_TO = 3

        /** Where he and his wheelbarrow (tfa_midraid_supplies_wheelbarrow, 2x2, no options) stand in the nexus; tsps capture notes. */
        const val X = 4129
        const val Z = 5609
        const val WHEELBARROW = 63302
        const val WHEELBARROW_X = 4130
        const val WHEELBARROW_Z = 5608

        /** He comes once the room of this row of doors is done: Moia's or Dreadnoth's. */
        const val ARRIVES_AFTER = 3

        /** The omnishop rows: tfa_midraid_supplies_shop and tfa_midraid_supplies_shop_failure. */
        const val SHOP = 17150
        const val FAILURE_SHOP = 17151

        /** The shop's inv, tfa_midraid_supplies_shop, ten slots. */
        const val STOCK_INV = 1030
        const val STOCK_SLOTS = 10

        /** tfa_supply_points: bits 1 to 7 of tfa_perm_transmit_1. */
        const val CREDITS_VARBIT = 16381
        const val MAX_CREDITS = 127

        /** Guessed: each member's credits as he comes. */
        const val CREDITS = 50

        /** Guessed: the deaths in a run that bring the failure shop. */
        const val FAILURE_DEATHS = 2

        /** Leftovers (tfa_midraid_supplies_leftovers); guessed, how many. */
        const val LEFTOVERS = 34730
        const val LEFTOVERS_STOCK = 5

        private val DOSES = Regex("""\((\d)\)$""")

        /** Each member's stock, by item, from the Wiki's Alliance Supplies page (read 2026-11-11). */
        val STOCK: Map<Int, Int> = linkedMapOf(
            6685 to 6, // Saradomin brew(4)
            3024 to 4, // Super restore(4)
            2434 to 4, // Prayer potion(4)
            391 to 10, // Manta ray
            32336 to 5, // Halibut
            32312 to 10, // Giant krill
            141 to 1, // Prayer potion(2)
            171 to 1, // Ranging potion(2)
            12699 to 1, // Super combat potion(2)
        )
    }
}
