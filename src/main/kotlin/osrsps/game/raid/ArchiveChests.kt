package osrsps.game.raid

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.combat.Drop
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn

private val logger = KotlinLogging.logger {}

/** The reward vault's own lines. Their words on live aren't known yet: no source has them. */
object ArchiveVaultLines : Lines("tfa_vault", "Chest_(The_Fractured_Archive)") {
    val NOT_YOURS = line("chest_not_yours", "chat, to a player who clicks Open on a chest of the reward vault that isn't their own")
    val NO_ROOM = line("no_room", "chat, taking loot from the chest's window with no room in the inventory for all of it")
    val DISCARD_ASK = line("discard_ask", "the title of the Yes/No menu on the chest window's Discard-all")
    val DISCARD_YES = line("discard_yes", "that menu, option 1: everything in the chest is destroyed")
    val DISCARD_NO = line("discard_no", "that menu, option 2")
    val CLAIM_FIRST = line("claim_first", "chat, at the lobby's Archive Door with loot of an earlier run still unclaimed in the Loot Wheelbarrow")
}

/**
 * The reward vault's chests (raid step 29a): what a run's end pays, and the chest each member opens for it.
 *
 * - **The pay.** As Ocellus's room is done ("at the end of the raid", the Wiki's chest page) the loot is rolled once
 *   for the party ([ArchiveLoot.roll]) and kept on the run ([ArchiveRun.loot]). Guessed: that it pays the party's
 *   members who are in the run then, and nobody who left before; that the size a death's penalty is worked from is
 *   those of theirs who earn a share ([ArchiveLoot.sharingSize]); that the folio and the fragments a member "owns" are those in their inventory, bank or worn, or broken
 *   already (the fragment's varbit).
 * - **The chests.** Eight tiles round the vault's island, "one per party slot" (tsps capture notes), each chest a
 *   multi-NPC on `tfa_vault_own_chest` (241 cache: `tfa_rewards_chest_vault_N_closed` and `_open`, 16736 to 16751; the
 *   varbit at N shows chest N as the player's own). Guessed: that a slot is a member's place in the party at the run's
 *   end, that only the slots of members paid have a chest, and that the chests are there from the pay on.
 * - **Open** on one's own chest plays `chest_guthixian02_open01` and turns it into a Rift with Search (tsps capture
 *   notes), the tick after: the animation is 30 client cycles, a tick (241 cache). Inferred: the tick. "Each player
 *   can only open their own chest" (the Wiki): another's refuses with [ArchiveVaultLines.NOT_YOURS]. Guessed: that it
 *   says anything.
 *
 * - **Search** on the Rift one's own chest leaves opens the chest's window over the loot ([ArchiveChestWindow], raid
 *   step 29b); another's refuses as its Open does (live took Search off others' Rifts on 3 November 2026, the Wiki;
 *   the 241 cache still has it). What isn't claimed there waits in the lobby's Loot Wheelbarrow, and the Archive Door
 *   lets nobody into a new run with loot waiting ([ArchiveVaultLines.CLAIM_FIRST]). Guessed: that refusal, as the
 *   Theatre of Blood's chest outside holds a player to their last loot; and that a chest never opened pays into the
 *   Wheelbarrow all the same.
 *
 * Not in yet: the unique's own chest on the northern platform (16730) and the purple braziers that show one was
 * found, so the one who finds it has it in their own chest here; the loot's graphics; the collection log.
 */
class ArchiveChests(private val world: World, private val raids: ArchiveRaids) {
    private var loaded: ArchiveLoot? = null

    /** The loot table, read from the resources ([ArchiveLoot.RESOURCE]) for the world's items; a test puts its own. */
    var table: ArchiveLoot
        get() = loaded ?: ArchiveLoot.load(world.objTypes).also { loaded = it }
        set(value) {
            loaded = value
        }

    fun register() {
        // Read at the start, so a file that doesn't parse stops the server rather than a run's end.
        table
        // A chest's option names the type the player is shown, their own or not; which it is, is the run's to say.
        for (type in listOf(CLOSED_OTHERS, CLOSED_OWN)) world.handlers.onNpc(type, OPEN) { op -> open(player, op.npc!!) }
        for (type in listOf(OPEN_OTHERS, OPEN_OWN)) world.handlers.onNpc(type, SEARCH) { op -> search(player, op.npc!!) }
        // The Loot Wheelbarrow is a multiloc: the client names the loc on the map, whichever it shows, and only the
        // open one, shown with loot waiting, has Search.
        world.handlers.onLoc(ArchiveChestWindow.WHEELBARROW, ArchiveChestWindow.SEARCH) {
            if (world.archiveChestWindow.waiting(player)) world.archiveChestWindow.open(player)
        }
    }

    /**
     * [run] is over: its loot is rolled for the party's members in it, in the party's order, and each one's chest is
     * put in the vault. Once only.
     *
     * The party's size for the poor line and the death penalty is its [ArchiveLoot.sharingSize], not everyone paid:
     * idle alts don't lower the bar (exploit review round 25, R25A-2). The run is marked paid before anything else, so
     * nothing pays twice, and every member's loot is in their chest before any chest is placed or shown: a chest that
     * fails to place, or a varbit that fails to send, can't keep the loot from those after it (R25A-4).
     */
    fun pay(run: ArchiveRun) {
        if (run.loot != null) return
        val present = raids.present(run).associateBy { it.saveKey }
        val paid = run.party.members.withIndex().filter { (slot, member) -> slot < SLOTS.size && member.saveKey in present }
        val rules = table
        val scores = paid.map { (_, member) -> (run.points[member.saveKey] ?: 0) to (run.deaths[member.saveKey] ?: 0) }
        val size = ArchiveLoot.sharingSize(scores)
        val members = paid.mapIndexed { index, (_, member) ->
            val (points, deaths) = scores[index]
            LootMember(
                member.saveKey,
                ArchiveLoot.contribution(points, deaths, size),
                ownsFolio = rules.folioForms.any { owns(member, it) },
                fragments = rules.fragments.filter { owns(member, it.type.id) || varbit(member, it.unlocked) != 0 }.map { it.type.id }.toSet(),
            )
        }
        val result = rules.roll(world.random, members, size)
        run.loot = result
        for ((slot, member) in paid) {
            run.chestSlots[member.saveKey] = slot
            world.archiveChestWindow.store(member, result.chests[member.saveKey].orEmpty())
            countClear(member)
        }
        for ((slot, member) in paid) {
            try {
                placeChest(run, slot, open = false)
                showOwn(run, member)
                world.archiveChestWindow.showClaimable(member)
            } catch (e: Exception) {
                logger.error(e) { "$run: chest ${slot + 1} of the reward vault, ${member.username}'s, wasn't shown; their loot waits all the same" }
            }
        }
        logger.info { "$run paid ${members.joinToString { "${it.key} (${it.contribution})" }} as a party of $size; unique: ${result.unique ?: "none"}" }
    }

    /**
     * One more clear of the raid for [player]: `total_completed_thefracturedarchive`, which their first 5 clears' cheaper
     * graves go by ([osrsps.game.combat.GraveFees]). Guessed: that a clear is a run whose end paid them.
     */
    private fun countClear(player: Player) {
        val varp = osrsps.game.combat.GraveFees.VARP_ARCHIVE_CLEARS
        if (varp < player.vars.types.varpCount) player.vars[varp] = player.vars[varp] + 1
    }

    /**
     * Whether [player] has [item] anywhere of their own: carried, worn or banked; and, since those were each a way to
     * keep it out of sight for a roll (exploit review round 24, R24A-4), in the guide prices screen, offered in a trade
     * not yet done, in their grave, or held by a Grand Exchange offer. Guessed: live's own check isn't known. One given
     * to someone else or dropped is no longer theirs, and that stays so.
     */
    internal fun owns(player: Player, item: Int): Boolean =
        player.inventory.count(item) > 0 || player.bank.count(item) > 0 || player.equipment.count(item) > 0 ||
            player.priceChecker.count(item) > 0 ||
            player.trade?.let { trade -> !trade.over && trade.sideOf(player).offer.count(item) > 0 } == true ||
            world.deaths.graveOf(player)?.items?.let { it.count(item) > 0 } == true ||
            player.ge.contents().any { (_, offer) ->
                offer.item == item && (offer.collectItems > 0 || offer.type == osrsps.game.ge.OfferType.SELL && !offer.finished)
            }

    private fun varbit(player: Player, id: Int): Int = if (player.vars.types.varbit(id) != null) player.vars.getVarbit(id) else 0

    /** [player]'s own chest among [run]'s shows as theirs: `tfa_vault_own_chest` at its number, 0 with none. */
    fun showOwn(run: ArchiveRun, player: Player) {
        world.interfaces.setVarbitNow(player, OWN_CHEST, run.chestSlots[player.saveKey]?.plus(1) ?: 0)
    }

    private fun placeChest(run: ArchiveRun, slot: Int, open: Boolean) {
        run.chests.remove(slot)?.let { if (world.npcs[it.index] === it) world.removeNpc(it) }
        // A world without the chests (a test's cache): the loot is rolled all the same, with nothing to open.
        val type = world.npcTypes[chestType(slot, open)] ?: return
        val (x, z) = SLOTS[slot]
        val at = raids.tile(run, x, z)
        val npc = world.spawnNpc(NpcSpawn(type, at.x, at.z, ArchiveRaids.REWARD_LEVEL, source = "$run", respawns = false))
        if (npc == null) logger.warn { "$run: no NPC slot for chest ${slot + 1} of the reward vault" } else run.chests[slot] = npc
    }

    /** Open on a chest of the vault: [player]'s own opens a tick on; another's refuses. */
    fun open(player: Player, npc: Npc) {
        val run = raids.at(player.x, player.z) ?: return
        val slot = run.chests.entries.firstOrNull { it.value === npc }?.key ?: return
        if (run.chestSlots[player.saveKey] != slot) {
            player.message(world.textFor(player)[ArchiveVaultLines.NOT_YOURS])
            return
        }
        if (slot in run.opened || slot in run.opening) return
        npc.effects.anim(OPEN_ANIM)
        run.opening[slot] = world.tick + OPEN_TICKS
    }

    /** Search on the Rift a chest leaves: [player]'s own opens its window over their loot; another's refuses. */
    fun search(player: Player, npc: Npc) {
        val run = raids.at(player.x, player.z) ?: return
        val slot = run.chests.entries.firstOrNull { it.value === npc }?.key ?: return
        if (run.chestSlots[player.saveKey] != slot) {
            player.message(world.textFor(player)[ArchiveVaultLines.NOT_YOURS])
            return
        }
        if (slot in run.opened) world.archiveChestWindow.open(player)
    }

    /** Each tick: a chest whose opening is over is the Rift it leaves. */
    fun process(run: ArchiveRun) {
        if (run.opening.isEmpty()) return
        for ((slot, due) in run.opening.entries.toList()) {
            if (world.tick < due) continue
            run.opening -= slot
            run.opened += slot
            placeChest(run, slot, open = true)
        }
    }

    /** What `::raid loot` shows [player] of [run]'s pay: who found the unique, and their own chest. */
    fun describe(run: ArchiveRun, player: Player): String {
        val loot = run.loot ?: return "No loot yet: Ocellus's room isn't done."
        val unique = if (loot.unique == null) "no unique" else "a unique in chest ${run.chestSlots.getValue(loot.unique) + 1}"
        val slot = run.chestSlots[player.saveKey] ?: return "Loot for ${loot.chests.size}, $unique. You have no chest."
        val state = when (slot) {
            in run.opened -> "open"
            in run.opening -> "opening"
            else -> "closed"
        }
        val left = player.archiveChest.contents().joinToString { (_, obj) -> "${world.objTypes[obj.id]?.name ?: obj.id} x${obj.count}" }.ifEmpty { "nothing" }
        return "Loot for ${loot.chests.size}, $unique. Your chest is ${slot + 1}, $state: ${describe(loot.chests[player.saveKey].orEmpty())}. Unclaimed: $left."
    }

    private fun describe(drops: List<Drop>): String = drops.joinToString { "${it.type.name} x${it.count}" }.ifEmpty { "nothing" }

    companion object {
        /** tfa_vault_own_chest: the number, 1 to 8, of the chest shown as the player's own; 0 for none. */
        const val OWN_CHEST = 16433

        /** tfa_rewards_chest_vault_closed_notmine and _mine, what a closed chest shows as: Open. */
        const val CLOSED_OTHERS = 16732
        const val CLOSED_OWN = 16733

        /** tfa_rewards_chest_vault_open_notmine and _mine, the Rift an opened chest shows as: Search. */
        const val OPEN_OTHERS = 16734
        const val OPEN_OWN = 16735

        /** tfa_rewards_chest_vault_1_closed: chest N closed is this and 2 (N - 1) more, open one more again. */
        const val FIRST_CHEST = 16736
        const val OPEN = 1
        const val SEARCH = 1

        /** chest_guthixian02_open01, and its length in ticks (30 client cycles). */
        const val OPEN_ANIM = 15101
        const val OPEN_TICKS = 1

        /** The chests' tiles by slot, on the vault's level (tsps capture notes). */
        val SLOTS = listOf(4124 to 5793, 4130 to 5793, 4121 to 5796, 4133 to 5796, 4120 to 5800, 4134 to 5800, 4121 to 5803, 4133 to 5803)

        /** The NPC that is chest [slot] (from 0), closed or [open]. */
        fun chestType(slot: Int, open: Boolean): Int = FIRST_CHEST + 2 * slot + if (open) 1 else 0
    }
}
