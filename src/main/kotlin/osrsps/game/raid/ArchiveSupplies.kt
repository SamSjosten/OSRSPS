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

    /** As [player] comes into a run: no credits from an earlier one. */
    fun entered(player: Player) = setCredits(player, 0)

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
