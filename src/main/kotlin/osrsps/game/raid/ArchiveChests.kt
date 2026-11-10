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
}

/**
 * The reward vault's chests (raid step 29a): what a run's end pays, and the chest each member opens for it.
 *
 * - **The pay.** As Ocellus's room is done ("at the end of the raid", the Wiki's chest page) the loot is rolled once
 *   for the party ([ArchiveLoot.roll]) and kept on the run ([ArchiveRun.loot]). Guessed: that it pays the party's
 *   members who are in the run then, and nobody who left before; that the size a death's penalty is worked from is
 *   theirs; that the folio and the fragments a member "owns" are those in their inventory, bank or worn, or broken
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
 * Not in yet (raid steps 29b and on): Search and the chest's window (`tfa_chests`, 973), so nothing is taken from a
 * chest yet; the lobby's Loot Wheelbarrow for what is left behind; the unique's own chest on the northern platform
 * (16730) and the purple braziers that show one was found, so the one who finds it has it in their own chest here;
 * the loot's graphics; the collection log.
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
    }

    /**
     * [run] is over: its loot is rolled for the party's members in it, in the party's order, and each one's chest is
     * put in the vault. Once only.
     */
    fun pay(run: ArchiveRun) {
        if (run.loot != null) return
        val present = raids.present(run).associateBy { it.saveKey }
        val paid = run.party.members.withIndex().filter { (slot, member) -> slot < SLOTS.size && member.saveKey in present }
        val rules = table
        val members = paid.map { (_, member) ->
            val key = member.saveKey
            LootMember(
                key,
                ArchiveLoot.contribution(run.points[key] ?: 0, run.deaths[key] ?: 0, paid.size),
                ownsFolio = rules.folioForms.any { owns(member, it) },
                fragments = rules.fragments.filter { owns(member, it.type.id) || varbit(member, it.unlocked) != 0 }.map { it.type.id }.toSet(),
            )
        }
        val result = rules.roll(world.random, members)
        run.loot = result
        for ((slot, member) in paid) {
            run.chestSlots[member.saveKey] = slot
            placeChest(run, slot, open = false)
            showOwn(run, member)
        }
        logger.info { "$run paid ${members.joinToString { "${it.key} (${it.contribution})" }}; unique: ${result.unique ?: "none"}" }
    }

    private fun owns(player: Player, item: Int): Boolean =
        player.inventory.count(item) > 0 || player.bank.count(item) > 0 || player.equipment.count(item) > 0

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
        return "Loot for ${loot.chests.size}, $unique. Your chest is ${slot + 1}, $state: ${describe(loot.chests[player.saveKey].orEmpty())}."
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

        /** chest_guthixian02_open01, and its length in ticks (30 client cycles). */
        const val OPEN_ANIM = 15101
        const val OPEN_TICKS = 1

        /** The chests' tiles by slot, on the vault's level (tsps capture notes). */
        val SLOTS = listOf(4124 to 5793, 4130 to 5793, 4121 to 5796, 4133 to 5796, 4120 to 5800, 4134 to 5800, 4121 to 5803, 4133 to 5803)

        /** The NPC that is chest [slot] (from 0), closed or [open]. */
        fun chestType(slot: Int, open: Boolean): Int = FIRST_CHEST + 2 * slot + if (open) 1 else 0
    }
}
