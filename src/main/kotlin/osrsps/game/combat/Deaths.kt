package osrsps.game.combat

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.math.abs
import net.rsprot.protocol.common.game.outgoing.inv.InventoryObject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.inv.UpdateInvFull
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.FrameSlot
import osrsps.game.InvObj
import osrsps.game.Inventory
import osrsps.game.Movement
import osrsps.game.NpcType
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.SettingsSide
import osrsps.game.Skill
import osrsps.game.dialogue.DialogueText
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.Handlers
import osrsps.game.script.Op
import osrsps.game.skill.ArceuusRunecraft

private val logger = KotlinLogging.logger {}

/**
 * Where a player who died comes back ([toX], [toZ], [toLevel]) and where their grave goes ([graveX], [graveZ],
 * [graveLevel]), when not at the spawn and on the tile they died on: a death in an instance, whose tile they can't get
 * back to, or in a raid. With [keepItems] they lose nothing: a raid death while the party still stands. [coord] is where
 * the grave counts as made, for its fee ([GraveFees]): a raid's death counts where it was in the cache's map, not in
 * the run's copy of it; -1 for where they fell. A [safe] death keeps everything and costs no Hardcore status: a
 * player-owned house's, or a safe minigame's (the Fight Cave; the Wiki's Ironman Mode, Hardcore Ironman: "Safe deaths,
 * such as those in minigames, will not cause the player to become a standard Ironman").
 */
data class DeathPlace(
    val keepItems: Boolean,
    val graveX: Int,
    val graveZ: Int,
    val graveLevel: Int,
    val toX: Int,
    val toZ: Int,
    val toLevel: Int,
    val coord: Int = -1,
    val safe: Boolean = false,
    /**
     * Only where they come back is changed, as by Krystilia's Edgeville respawn: the death is otherwise as anywhere, so
     * a Wilderness one still counts as PvP, its items going to the killer (exploit review round 49, R49C-1).
     */
    val respawnOnly: Boolean = false,
)

/** A player's gravestone: the [npc] standing where they died, holding what they lost in [items]. */
class Grave(
    /** [Player.saveKey] of whose it is. */
    val owner: String,
    val npc: Npc,
    val items: Inventory = Inventory(Deaths.GRAVE_INV, Deaths.GRAVE_CAPACITY),
    /** Where it counts as made, for its fee: `gravestone_creation_death_coord` (level shl 28 or x shl 14 or z). */
    val coord: Int = -1,
    /** Raised while its owner was in a `::scenario`, so it holds the scenario's items and goes with it ([Deaths.dropScenarioGrave]). */
    val scenario: Boolean = false,
) {
    /** Ticks until it collapses; counts down only while the owner is logged in and nobody has it open. */
    var ticksLeft: Int = Deaths.GRAVE_TICKS
        internal set

    /** Whether a later death added to it (`gravestone_has_merged`), which loses a content's discount. */
    var merged: Boolean = false
        internal set

    /** Whether its fee is paid, so its paid items can be taken. A later death's items lock it again. */
    var paid: Boolean = false
        internal set
}

/**
 * Player death and gravestones (OSRS Wiki, Death and Gravestone).
 *
 * A player with no hitpoints left dies where they stand ([die]): "Oh dear, you are dead!" has already been said (see
 * [MeleeCombat]), their death animation plays, and for [DEATH_TICKS] they can do nothing. Then ([respawn]) they keep
 * their [KEPT_ITEMS] most valuable items and the rest go into a gravestone on the death tile: NPC [GRAVE] with Check
 * and Loot. They come back at Lumbridge with full hitpoints, every stat back to its level and full run energy.
 *
 * The grave stands for [GRAVE_TICKS] (15 minutes) of the owner's logged-in time; the timer stops while they have it open.
 * Loot opens the gravestone interface ([GRAVESTONE], inv [GRAVE_INV]), whose items have Take and whose button is Take-All.
 * Dying again while it stands adds to it and starts its timer again. When it collapses its contents are lost: Death's
 * Office, where they would go on live, isn't built. Nobody else can check or loot it.
 *
 * From the Wiki: the message, three items kept (one more with Protect Item; none skulled), valued at
 * the Grand Exchange price or the alchemy value, 15 minutes that pause while logged out or with the interface open,
 * a second death adding to the grave, and death restoring stats and run energy. Guessed and marked below: the ticks
 * the body lies before the respawn, ties in the value order ([value]) going by worn then inventory slot, that each
 * unit of a stack counts as an item, and what Check says.
 *
 * Fees (raid step 31): a grave's items are kept in value order, most valuable first, and those worth 100,000 or more
 * a unit are in its paid list until the fee ([GraveFees]) is paid with Unlock, from the bank (Death's Coffer isn't
 * built). The interface's client script splits the lists and shows each item's fee from three varbits, the slots where
 * the items under 100,000, 1,000,000 and 10,000,000 start, plus one ([VARBIT_FREE_FROM], [VARBIT_UNDER_1M],
 * [VARBIT_UNDER_10M]), and the grave's own discount from where it was made ([VARP_DEATH_COORD]) and whether it was
 * merged ([VARBIT_MERGED]); clientscript gravestone_transmit_data ([TRANSMIT_DATA]) gives it the fee and the coffer,
 * and a fee turns the paid list's button into Unlock (gravestone_generic_resynchdata). All from the 241 cache's
 * scripts (osrs-dumps). Guessed: every message's words; that a merged grave must be unlocked again; that Unlock with
 * too few coins in the bank takes nothing. Not in: Death's Coffer, Death's Office, the incinerator ("Discard items to
 * reduce a fee").
 *
 * Every death here is a dangerous one, as no safe-death content is built: a Hardcore Ironman's death makes them an
 * Ironman (the Wiki's Hardcore Ironman page; for The Fractured Archive its page: "always a dangerous death"). Not in:
 * a Hardcore Group Ironman's lives, as groups aren't built; the death's broadcast.
 */
class Deaths(
    private val objTypes: ObjTypes,
    /** NPC type [GRAVE]; null when the cache has no NPCs (tests), and then the lost items are simply lost. */
    private val graveType: NpcType?,
    private val currentTick: () -> Long,
    private val players: () -> Sequence<Player>,
    private val findPlayer: (saveKey: String) -> Player?,
    private val spawnNpc: (NpcSpawn) -> Npc?,
    private val removeNpc: (Npc) -> Unit,
    /** Puts a respawning player at the spawn. */
    private val teleportToSpawn: (Player) -> Unit,
    private val openModal: (Player, Int) -> Unit,
    private val closeModals: (Player) -> Unit,
    /** Sets a varbit and sends it ahead of the queue, so an interface opening now reads it. */
    private val setVarbitNow: (Player, Int, Int) -> Unit,
    /** Drops the player's scripts: a delayed one (a ladder half climbed) must not carry on once they are dead. */
    private val cancelScripts: (Player) -> Unit = {},
    /** Turns every prayer off ([Prayers.deactivateAll]). */
    private val prayersOff: (Player) -> Unit = {},
    /** Whether Protect Item counts for the player this tick ([Prayers.has]). */
    private val protectItem: (Player) -> Boolean = { false },
    private val send: (Player, OutgoingGameMessage) -> Unit = { player, message -> player.session.queue(message) },
    private val message: (Player, String) -> Unit = Player::message,
    /** Examine on a grave item: the inventory's examine. */
    private val examine: (Player, ObjType, Int) -> Unit = { _, _, _ -> },
    /** What an item counts for when choosing the kept items ([osrsps.game.ItemPrices.deathValue]). */
    private val value: (ObjType) -> Long = { it.cost.toLong() },
    /** Each player's words for the game messages ([CombatLines]). */
    private val words: (Player) -> DialogueText = { DialogueText.NONE },
    /** Ends poison, venom and their immunity ([Poison.clear]). */
    private val cure: (Player) -> Unit = {},
    /** Where a dying player comes back and their grave goes, when not at the spawn and where they fell. */
    private val placeOf: (Player) -> DeathPlace? = { null },
    /** Puts a respawning player on a tile, for a [DeathPlace]. */
    private val teleport: (Player, Int, Int, Int) -> Unit = { _, _, _, _ -> },
    /** Drops a count of an item under the player, seen by them alone at first, as a drop from the backpack is. */
    private val drop: (Player, ObjType, Int) -> Unit = { _, _, _ -> },
    /** Sets a varp and sends it ahead of the queue, as [setVarbitNow] does a varbit. */
    private val setVarpNow: (Player, Int, Int) -> Unit = { player, varp, value -> if (varp < player.vars.types.varpCount) player.vars[varp] = value },
    /**
     * Takes a count of an item out of the player's bank, as a fee, keeping its tabs right ([osrsps.game.bank.Bank.take]):
     * with only the slots emptied, a tab whose last item the coins were stayed counted (Codex, PR 614).
     */
    private val takeFromBank: (Player, Int, Int) -> Unit = { player, id, count ->
        var left = count
        for ((slot, obj) in player.bank.contents()) {
            if (left == 0) break
            if (obj.id == id) left -= player.bank.remove(slot, minOf(left, obj.count))
        }
    },
    /** The player has just fallen, before their death animation: a raid counts the death then ([osrsps.game.raid.ArchiveRaids.fell]). */
    private val fell: (Player) -> Unit = {},
    /** Takes a random event's own items off the player ([EventAreas.takePlaceItems]), before anything is sorted. */
    private val takePlaceItems: (Player) -> Unit = {},
    /** Who killed the player, by save key, if it is a PvP death ([PvpCombat.killerOf]); null for any other. */
    private val killerOf: (Player) -> String? = { null },
    /**
     * Puts a count of the victim's item down at a tile for a killer (a save key) alone for [PVP_LOOT_PRIVATE_TICKS],
     * then for everyone, gone after [PVP_LOOT_DESPAWN_TICKS].
     */
    private val dropForKiller: (victim: Player, killer: String, ObjType, Int, Int, Int, Int) -> Unit = { _, _, _, _, _, _, _ -> },
    /** Writes the player's save now, as a PvP death does before the killer's loot goes down; false if it couldn't be. */
    private val saveNow: (Player) -> Boolean = { true },
    /** A PvP death's lost Wilderness lairs' fee, in coins for the killer ([osrsps.game.content.WildernessLairFee.forfeit]); 0 for none. */
    private val forfeit: (Player) -> Int = { 0 },
    /** A death with no player killer, where the player fell: a Revenant Caves death takes the fee ([osrsps.game.content.RevenantCaves.lose]). */
    private val diedUnkilled: (Player) -> Unit = {},
    /** Whether an item is a supply lost for good in a Wilderness death with no player killer: food, a potion dose, a phoenix necklace. */
    private val supply: (Int) -> Boolean = { false },
) {
    private val graves = LinkedHashMap<Npc, Grave>()

    /** The grave each player has open, by [Player.saveKey]; stale once they close it, so always checked against the modal. */
    private val viewing = HashMap<String, Grave>()

    /** Every grave standing, for tests and commands. */
    val all: Collection<Grave> get() = graves.values

    fun graveOf(player: Player): Grave? = graves.values.firstOrNull { it.owner == player.saveKey }

    fun register(handlers: Handlers) {
        handlers.onNpc(GRAVE, CHECK) { op -> check(player, op.npc!!) }
        handlers.onNpc(GRAVE, LOOT) { op -> loot(player, op.npc!!) }
        handlers.onButton(GRAVESTONE, FREEITEMS, EXAMINE_OP) { op -> examineItem(player, op.sub) }
        handlers.onButton(GRAVESTONE, FREEITEMS) { op -> take(player, op.sub) }
        handlers.onButton(GRAVESTONE, FREEBUTTON) { takeAll(player) }
        handlers.onButton(GRAVESTONE, PAYITEMS, EXAMINE_OP) { op -> examineItem(player, op.sub) }
        handlers.onButton(GRAVESTONE, PAYITEMS) { op -> takePaid(player, op.sub) }
        handlers.onButton(GRAVESTONE, PAYBUTTON) { payButton(player) }
    }

    /** [player] has no hitpoints left: they fall where they stand and lie there until [process] respawns them. */
    fun die(player: Player) {
        if (player.dead) return
        player.deathTick = currentTick()
        closeModals(player)
        // Guessed: live's death clears the player's queue too, so a climb or a search in progress never finishes.
        cancelScripts(player)
        // Protect Item counts if it is on as they fall; the prayers then go off (guessed: as they fall, not at the respawn).
        // A skull keeps none of the three (OSRS Wiki, Items Kept on Death); the skull then goes (guessed).
        player.deathKeptItems = (if (player.skullTicks > 0) 0 else KEPT_ITEMS) + if (protectItem(player)) 1 else 0
        // The skull and the hit-back rights go at the respawn, and only with a death that loses items (respawn).
        player.deathKiller = if (pvpDeath(player)) killerOf(player) else null
        prayersOff(player)
        player.interaction = null
        player.waypoints.clear()
        player.anim(DEATH_ANIM)
        fell(player)
    }

    /**
     * Whether [player]'s death can be a PvP one, its loot for a killer: only on a tile where players may fight
     * ([osrsps.game.Wilderness.pvpLevel] above 0, so not the safe rows or Ferox Enclave), outside any instance or raid
     * ([placeOf] null, or one that only moves the respawn), and not in a `::scenario` (whose items go with its grave). A hit that came in the Wilderness
     * with the death somewhere else is a death like any other (exploit review round 36, R36A-1 and R36A-2).
     */
    private fun pvpDeath(player: Player): Boolean =
        osrsps.game.Wilderness.pvpLevel(player.x, player.z) > 0 && placeOf(player).let { it == null || it.respawnOnly } && player.scenario == null

    /** Counts the graves down (a grave raised this tick starts next tick), and respawns the players whose death animation is over. */
    fun process() {
        if (graves.isNotEmpty()) {
            val open = viewing.mapNotNull { (owner, _) -> findPlayer(owner)?.let(::opened) }.toSet()
            for (grave in graves.values.toList()) {
                if (grave in open || findPlayer(grave.owner) == null) continue
                if (--grave.ticksLeft <= 0) collapse(grave)
            }
        }
        val tick = currentTick()
        for (player in players()) {
            if (player.dead && tick - player.deathTick >= DEATH_TICKS) respawn(player)
        }
    }

    /**
     * Puts [player] back in the world: their items sorted into kept and lost, the lost ones in their grave, their stats
     * and run energy back, and themself at the spawn; or as [place] says, [placeOf]'s by default. Also run for a dead
     * player who logs out, so nobody saves dead, and by a raid's wipe for the party members it had downed.
     */
    fun respawn(player: Player, place: DeathPlace? = placeOf(player)) {
        // A raid's wipe respawns members still on their feet: they die first, as anyone does, so a trade hands its
        // offers back, the price checker keeps its items for the sorting below, and Protect Item counts as they fall.
        if (!player.dead) die(player)
        player.deathTick = -1
        // A random event's own items stay in its place, out of what is kept or lost, the grave included.
        takePlaceItems(player)
        if (place?.safe != true) loseHardcore(player)
        val keep = place?.keepItems == true || place?.safe == true
        if (!keep) {
            // A skull goes with a death that loses items (guessed), and with it the hit-back rights it gave those
            // attacked first (Skulls); a raid death that keeps everything keeps both (exploit review round 36, R36A-3).
            player.skullTicks = 0
            player.attackedFirst.clear()
        }
        // The kept items are sorted with the price checker's among them, before it is emptied, so a valuable one being
        // checked can still be kept.
        val sorted = if (keep) emptyList() else dropItems(player)
        val over = unchecked(player)
        // Everything kept, what the price checker held and the backpack can't take goes under them where they come
        // back, as closing the screen with a full backpack drops it; left in the closed checker, the next save put it
        // in the bank, which banked from inside a raid (exploit review round 12, RAID-1).
        val lost = if (keep) emptyList() else sorted + over
        player.deathKeptItems = KEPT_ITEMS
        val charges = lostCharges(player, lost)
        val killer = player.deathKiller.takeIf { !keep }
        player.deathKiller = null
        player.pvpDamage.clear()
        val forKiller = ArrayList<InvObj>()
        // A death in the Wilderness with no player killer deletes the food, potions and phoenix necklaces it loses, rather
        // than putting them in the grave (OSRS Wiki, Grave: "while in the Wilderness or on a PvP world, food, potions, and
        // phoenix necklaces cannot be sent to a grave or dropped, and are instead deleted"; exploit review R65X-1).
        val graved = if (killer == null && !keep && pvpDeath(player)) (lost + charges).filterNot { supply(it.id) } else lost + charges
        val buried = if (killer == null) graved else toKiller(player, lost + charges, forKiller)
        // Guessed: fragments lost with their obj are gone, not put in the grave as anything. Checked after toKiller,
        // which can hand a deep-Wilderness death's fragments obj back to the backpack (exploit review round 42, R42A-1).
        if (lostLast(player, lost, setOf(ArceuusRunecraft.FRAGMENTS))) player.charges[ArceuusRunecraft.FRAGMENT_CHARGES] = 0
        if (killer != null) {
            val fee = forfeit(player)
            if (fee > 0) forKiller += InvObj(COINS, fee)
        } else if (!keep) {
            diedUnkilled(player)
        }
        if (buried.isNotEmpty()) bury(player, buried, place)
        val fellX = player.x
        val fellZ = player.z
        val fellLevel = player.level
        for (skill in Skill.entries) player.skills.setCurrentLevel(skill, player.skills.baseLevel(skill))
        player.runEnergy = Movement.MAX_ENERGY
        cure(player)
        // The death seq's last frame lasts 20,000 client cycles (seq 836 in the 241 cache), so the client holds the
        // dead pose, and holds back the player's steps, until the animation is stopped. Stopping it on respawn is
        // inferred from that; live never shows the pose after the respawn.
        player.anim(-1)
        if (place == null) teleportToSpawn(player) else teleport(player, place.toX, place.toZ, place.toLevel)
        if (keep) for (obj in over) drop(player, objTypes[obj.id] ?: ObjType(obj.id), obj.count)
        if (killer != null && forKiller.isNotEmpty()) {
            // Saved, respawned and without the lost items, before any reaches the ground: a crash after the killer
            // picks one up can't give it back to the victim too. A save that couldn't be written puts them in the
            // victim's grave instead, where they fell, so nothing is handed over until the removal is on disk.
            if (saveNow(player)) {
                for (obj in forKiller) dropForKiller(player, killer, objTypes[obj.id] ?: ObjType(obj.id), obj.count, fellX, fellZ, fellLevel)
            } else {
                logger.warn { "${player.username}'s save failed after a PvP death: ${forKiller.size} items to the grave, not to $killer" }
                bury(player, forKiller, place, fellX, fellZ, fellLevel)
            }
        }
    }

    /**
     * A PvP death's [lost] items (OSRS Wiki, Items Kept on Death, Wilderness): the tradeable ones go into [forKiller],
     * which [respawn] drops where [player] fell for the killer alone for a minute, then for everyone for four more.
     * Untradeables at level 20 or below go to the grave, as returned; above it, one with no combat use is kept, and the
     * rest are gone. Inferred: an item that
     * can be worn has a combat use and one that can't hasn't. Not in: untradeables that break or turn into coins or
     * components for the killer, and Trouver parchment locks.
     */
    private fun toKiller(player: Player, lost: List<InvObj>, forKiller: MutableList<InvObj>): List<InvObj> {
        val deep = osrsps.game.Wilderness.level(player.x, player.z) > UNTRADEABLES_TO_GRAVE_LEVEL
        val buried = ArrayList<InvObj>()
        for (obj in lost) {
            val type = objTypes[obj.id] ?: ObjType(obj.id)
            when {
                // Player-to-player tradeable: coins and platinum tokens too, which aren't on the Grand Exchange.
                type.tradeable || type.id == osrsps.game.ge.GrandExchange.COINS || type.id == osrsps.game.ge.GrandExchange.PLATINUM_TOKEN -> forKiller += obj
                !deep -> buried += obj
                !type.wearable -> {
                    val over = obj.count - player.inventory.add(type, obj.count)
                    if (over > 0) buried += InvObj(obj.id, over)
                }
                else -> logger.info { "${player.username} lost untradeable ${type.name} x${obj.count} to a PvP death above level 20" }
            }
        }
        return buried
    }

    /**
     * Takes everything but the [Player.deathKeptItems] most valuable units out of [player]'s inventory and equipment, returning what
     * came out. A stack counts a unit per item, so three coins of a stack of ten are kept and seven go. Guessed; the
     * Wiki says only that stacks "count as one item" for the kept-item count, which can't be what it means for a
     * stack of coins.
     */
    private fun dropItems(player: Player): List<InvObj> {
        class Held(val inv: Inventory, val slot: Int, val obj: InvObj, val value: Long)
        val held = ArrayList<Held>()
        // The price checker's items are carried too: the death closed the screen without giving them back.
        for (inv in listOf(player.equipment, player.inventory, player.priceChecker)) {
            for ((slot, obj) in inv.contents()) held += Held(inv, slot, obj, objTypes[obj.id]?.let(value) ?: 0)
        }
        // Stable, so ties keep worn-then-inventory slot order.
        held.sortByDescending { it.value }
        var keep = player.deathKeptItems
        val lost = ArrayList<InvObj>()
        for (item in held) {
            val kept = minOf(keep, item.obj.count)
            keep -= kept
            val gone = item.obj.count - kept
            if (gone == 0) continue
            item.inv.remove(item.slot, gone)
            lost += InvObj(item.obj.id, gone)
        }
        return lost
    }

    /**
     * Puts the items [dropItems] kept out of the price checker back in [player]'s backpack, which the death has all but
     * emptied, and empties the checker; returns any that still don't fit, for the grave, or to drop under them when a
     * death [keeps][DeathPlace.keepItems] everything. Before this, closing the screen on a death put them in the bank,
     * out of the grave (fourth exploit review, 30 Oct 2026).
     */
    private fun unchecked(player: Player): List<InvObj> {
        val left = ArrayList<InvObj>()
        for ((slot, obj) in player.priceChecker.contents()) {
            player.priceChecker[slot] = null
            val type = objTypes[obj.id] ?: ObjType(obj.id)
            val over = obj.count - player.inventory.add(type, obj.count)
            if (over > 0) left += InvObj(obj.id, over)
        }
        return left
    }

    /**
     * The charges of [player]'s last blowpipe or powered staff, as items, when [lost] took it; they are then gone from
     * the player. They are kept with a kept or banked one. Charges are the player's, not the item's
     * ([osrsps.game.Charges]), so before this a death kept them all on top of the three kept items; the exploit reviews
     * of 29 Oct 2026 found it for the blowpipe and then for Tumeken's shadow. Guessed: live keeps the charges on the item
     * in the grave; with no charges on items here, the grave holds them as what they were made of, a blowpipe's darts
     * and scales and a staff's runes.
     */
    private fun lostCharges(player: Player, lost: List<InvObj>): List<InvObj> {
        val charges = ArrayList<InvObj>()
        if (lostLast(player, lost, setOf(Blowpipe.LOADED, Blowpipe.EMPTY))) {
            val darts = player.charges[Blowpipe.DARTS]
            if (darts > 0) charges += InvObj(player.charges[Blowpipe.DART_TYPE], darts)
            val scales = player.charges[Blowpipe.SCALES]
            if (scales > 0) charges += InvObj(Blowpipe.SCALE, scales)
            for (name in listOf(Blowpipe.DART_TYPE, Blowpipe.DARTS, Blowpipe.SCALES)) player.charges[name] = 0
        }
        for (staff in PoweredStaves.ALL) {
            if (!lostLast(player, lost, setOf(staff.charged, staff.uncharged))) continue
            val left = player.charges[staff.charge]
            if (left > 0) for (cost in staff.runesPerCharge) charges += InvObj(cost.rune, cost.count * left)
            player.charges[staff.charge] = 0
        }
        return charges
    }

    /**
     * Whether [lost] holds one of [ids] and none is left in [player]'s inventory, equipment or bank. One in the bank
     * keeps the charges too: they are shared, so moving them would empty one never at risk.
     */
    private fun lostLast(player: Player, lost: List<InvObj>, ids: Set<Int>): Boolean =
        lost.any { it.id in ids } && listOf(player.inventory, player.equipment, player.bank).none { inv -> inv.contents().any { (_, obj) -> obj.id in ids } }

    /**
     * Puts [lost] in [player]'s grave, raising one on their tile, or [place]'s grave tile, if they have none; an existing
     * grave starts its timer again.
     */
    private fun bury(player: Player, lost: List<InvObj>, place: DeathPlace?, x: Int = player.x, z: Int = player.z, level: Int = player.level) {
        val coord = place?.coord?.takeIf { it >= 0 } ?: GraveFees.coord(x, z, level)
        val existing = graveOf(player)
        // A death in a scenario never goes into the grave from before it: that grave stays after ::reset, so the
        // scenario's items would come out real (exploit review 40, R40C-3). They're the scenario's, so they go.
        if (player.scenario != null && existing != null && !existing.scenario) {
            logger.info { "${player.username} died in scenario ${player.scenario} with a grave from before it: ${lost.size} scenario items gone" }
            return
        }
        val grave = existing ?: if (place == null) raise(player, x, z, level, coord) else raise(player, place.graveX, place.graveZ, place.graveLevel, coord)
        if (existing != null) {
            grave!!.merged = true
            grave.paid = false
        }
        if (grave == null) {
            logger.warn { "${player.username} lost ${lost.size} items with no grave to put them in" }
            return
        }
        for (obj in lost) {
            val type = objTypes[obj.id] ?: ObjType(obj.id)
            val added = grave.items.add(type, obj.count)
            // Guessed: a grave holds 120 slots; anything past that is lost.
            if (added < obj.count) logger.warn { "${player.username}'s grave is full: ${obj.count - added} of ${type.name} lost" }
        }
        sort(grave)
        grave.ticksLeft = GRAVE_TICKS
        reshow(grave)
    }

    /** Sends [grave] again to whoever has it open. */
    private fun reshow(grave: Grave) {
        for (viewer in players()) if (viewing[viewer.saveKey] === grave && hasOpen(viewer)) sendGrave(viewer, grave)
    }

    /**
     * Whether [grave]'s items are in value order by today's prices. Which are free, and what the rest cost, go by where
     * the first item under 100,000 stands, so a grave sorted by older prices could free a valuable item after its first
     * fell in price (an `::itemprices refresh`; exploit review round 25, R25A-3).
     */
    private fun inOrder(grave: Grave): Boolean =
        grave.items.contents().zipWithNext().all { (a, b) -> unitValue(a.second.id) >= unitValue(b.second.id) }

    /** Puts [grave]'s items in value order, most valuable a unit first; ties keep their order. */
    private fun sort(grave: Grave) {
        val items = grave.items.contents().map { it.second }.sortedByDescending { unitValue(it.id) }
        grave.items.clear()
        for (obj in items) grave.items.add(objTypes[obj.id] ?: ObjType(obj.id), obj.count)
    }

    private fun unitValue(id: Int): Long = objTypes[id]?.let(value) ?: 0

    /** The first slot of [grave] whose unit is worth under [limit]; the items before it are worth that or more. */
    private fun firstUnder(grave: Grave, limit: Long): Int {
        val items = grave.items.contents()
        return items.firstOrNull { (_, obj) -> unitValue(obj.id) < limit }?.first ?: ((items.maxOfOrNull { it.first } ?: -1) + 1)
    }

    /** Where [grave]'s free list starts: its first item worth under 100,000 a unit. */
    private fun freeFrom(grave: Grave): Int = firstUnder(grave, FREE_UNDER)

    /** What [player] owes to unlock [grave]'s paid list: 0 once paid, or with nothing in it. */
    fun fee(player: Player, grave: Grave): Long {
        if (grave.paid) return 0
        val paid = grave.items.contents().filter { (slot, _) -> slot < freeFrom(grave) }.map { (_, obj) -> unitValue(obj.id) to obj.count }
        return GraveFees.total(player, paid, grave.coord, grave.merged)
    }

    private fun raise(player: Player, x: Int, z: Int, level: Int, coord: Int): Grave? {
        val type = graveType ?: return null
        val npc = spawnNpc(NpcSpawn(type, x, z, level, source = "${player.username}'s death")) ?: return null
        val grave = Grave(player.saveKey, npc, coord = coord, scenario = player.scenario != null)
        graves[npc] = grave
        return grave
    }

    /**
     * Moves every grave whose NPC [inside] holds for to [x], [z], [level], with its owner, items and time left: for a
     * place that is going away (an instance ending). Anyone with one open has it closed.
     */
    fun moveGraves(inside: (Npc) -> Boolean, x: Int, z: Int, level: Int) {
        for (grave in graves.values.filter { inside(it.npc) }) {
            graves.remove(grave.npc)
            removeNpc(grave.npc)
            closeViewers(grave)
            val npc = spawnNpc(NpcSpawn(grave.npc.spawn.type, x, z, level, source = grave.npc.spawn.source))
            if (npc == null) {
                logger.warn { "No room to move ${grave.owner}'s grave to $x, $z, $level: ${grave.items.contents().size} slots lost" }
                continue
            }
            graves[npc] = Grave(grave.owner, npc, grave.items, grave.coord, grave.scenario).also {
                it.ticksLeft = grave.ticksLeft
                it.merged = grave.merged
                it.paid = grave.paid
            }
        }
    }

    private fun closeViewers(grave: Grave) {
        viewing.values.removeIf { it === grave }
        for (viewer in players()) if (viewing[viewer.saveKey] == null && hasOpen(viewer)) closeModals(viewer)
    }

    /**
     * [player]'s grave goes, with no word, if a `::scenario` raised it: a test script that died part way (and so never
     * got its `::reset` in) leaves no grave for the next, whose own death would be added to it as a merged grave's and
     * lose a content's discount (nightly 2026-11-15: archive-raid-death-costs paid 500 for 125 after
     * archive-guardian-entrance's death). A grave from before the scenario holds the player's own items and stays.
     */
    fun dropScenarioGrave(player: Player) {
        val grave = graveOf(player)?.takeIf { it.scenario } ?: return
        graves.remove(grave.npc)
        removeNpc(grave.npc)
        closeViewers(grave)
    }

    /** The grave falls: its NPC goes and its contents with it (Death's Office isn't built), and the owner is told. */
    private fun collapse(grave: Grave) {
        graves.remove(grave.npc)
        removeNpc(grave.npc)
        closeViewers(grave)
        val owner = findPlayer(grave.owner) ?: return
        if (!grave.items.isEmpty()) message(owner, words(owner)[CombatLines.GRAVE_COLLAPSED])
    }

    private fun check(player: Player, npc: Npc) {
        val grave = graves[npc] ?: return
        if (grave.owner != player.saveKey) {
            message(player, words(player)[CombatLines.GRAVE_NOT_YOURS])
            return
        }
        val minutes = (grave.ticksLeft + TICKS_PER_MINUTE - 1) / TICKS_PER_MINUTE
        message(player, if (minutes == 1) words(player)[CombatLines.GRAVE_MINUTE] else words(player)[CombatLines.GRAVE_MINUTES, "minutes" to minutes])
    }

    private fun loot(player: Player, npc: Npc) {
        val grave = graves[npc] ?: return
        if (grave.owner != player.saveKey) {
            message(player, words(player)[CombatLines.GRAVE_NOT_YOURS])
            return
        }
        viewing[player.saveKey] = grave
        if (!inOrder(grave)) sort(grave)
        openModal(player, GRAVESTONE)
        sendGrave(player, grave)
        // The item slots' Take (op 1) and Examine (op 10), and the Take-All and Unlock buttons, in the second event
        // word, where op n is bit n - 1 (see Interfaces.IF_EVENT_OP1).
        for (items in listOf(FREEITEMS, PAYITEMS)) send(player, IfSetEventsV2(GRAVESTONE, items, 0, GRAVE_CAPACITY - 1, 0, (1 shl 0) or (1 shl 9)))
        for (button in listOf(FREEBUTTON, PAYBUTTON)) send(player, IfSetEventsV2(GRAVESTONE, button, -1, -1, 0, 1 shl 0))
    }

    /**
     * What the gravestone shows of [grave]: where its value bands start, where it was made and whether merged, which
     * the client works each paid item's fee out from, its items, and the fee to unlock them.
     */
    private fun sendGrave(player: Player, grave: Grave) {
        setVarbitNow(player, VARBIT_UNDER_10M, firstUnder(grave, 10_000_000) + 1)
        setVarbitNow(player, VARBIT_UNDER_1M, firstUnder(grave, 1_000_000) + 1)
        setVarbitNow(player, VARBIT_FREE_FROM, freeFrom(grave) + 1)
        setVarbitNow(player, VARBIT_MERGED, if (grave.merged) 1 else 0)
        setVarpNow(player, VARP_DEATH_COORD, grave.coord)
        sendItems(player, grave)
        // The coffer is empty: Death's Coffer isn't built.
        send(player, RunClientScript(TRANSMIT_DATA, listOf(fee(player, grave).toInt(), 0)))
    }

    private fun sendItems(player: Player, grave: Grave) {
        val slots = grave.items.contents().toMap()
        val count = (slots.keys.maxOrNull() ?: -1) + 1
        send(player, UpdateInvFull(GRAVE_INV, count) { slot -> slots[slot]?.let { InventoryObject(slot, it.id, it.count) } ?: InventoryObject.NULL })
    }

    /**
     * The grave [player] has open now, or null: the gravestone open, the grave still standing, and the player beside it.
     * The last is a guard: the gravestone closes when the player walks, but an interface opened some other way
     * mustn't empty a grave from across the map.
     */
    private fun opened(player: Player): Grave? {
        val grave = viewing[player.saveKey] ?: return null
        if (!hasOpen(player) || graves[grave.npc] !== grave) return null
        val npc = grave.npc
        if (npc.level != player.level || maxOf(abs(npc.x - player.x), abs(npc.z - player.z)) > GRAVE_REACH) return null
        // Prices changed with the gravestone open: the grave is put in order and shown again, and this click, made on
        // what it showed before, does nothing.
        if (!inOrder(grave)) {
            sort(grave)
            reshow(grave)
            return null
        }
        return grave
    }

    private fun hasOpen(player: Player) = player.modals[FrameSlot.MAINMODAL] == GRAVESTONE

    private fun examineItem(player: Player, slot: Int) {
        val grave = opened(player) ?: return
        if (slot !in 0 until GRAVE_CAPACITY) return
        val obj = grave.items[slot] ?: return
        val type = objTypes[obj.id] ?: return
        examine(player, type, obj.count)
    }

    /** Take on the free list: only its own items, those worth under 100,000 a unit. */
    private fun take(player: Player, slot: Int) {
        val grave = opened(player) ?: return
        if (slot !in freeFrom(grave) until GRAVE_CAPACITY) return
        if (!takeSlot(player, grave, slot)) message(player, words(player)[CombatLines.GRAVE_NO_SPACE])
        finishTaking(player, grave)
    }

    /** Take-All on the free list. */
    private fun takeAll(player: Player) {
        val grave = opened(player) ?: return
        takeEach(player, grave, grave.items.contents().map { it.first }.filter { it >= freeFrom(grave) })
    }

    /** Take on the paid list: only once its fee is paid (or there is none). */
    private fun takePaid(player: Player, slot: Int) {
        val grave = opened(player) ?: return
        if (slot !in 0 until freeFrom(grave)) return
        if (fee(player, grave) > 0) {
            message(player, words(player)[CombatLines.GRAVE_LOCKED])
            return
        }
        if (!takeSlot(player, grave, slot)) message(player, words(player)[CombatLines.GRAVE_NO_SPACE])
        finishTaking(player, grave)
    }

    /** The paid list's button: Unlock while a fee is owed, then Take-All. */
    private fun payButton(player: Player) {
        val grave = opened(player) ?: return
        val fee = fee(player, grave)
        if (fee == 0L) {
            takeEach(player, grave, grave.items.contents().map { it.first }.filter { it < freeFrom(grave) })
            return
        }
        // From the bank, Death's Coffer not being built (the Wiki: "deducted from Death's Coffer, if available, or else
        // from the bank"). Guessed: too few coins takes none.
        if (player.bank.count(COINS) < fee) {
            message(player, words(player)[CombatLines.GRAVE_CANT_PAY, "fee" to String.format(java.util.Locale.ROOT, "%,d", fee)])
            return
        }
        takeFromBank(player, COINS, fee.toInt())
        grave.paid = true
        message(player, words(player)[CombatLines.GRAVE_PAID, "fee" to String.format(java.util.Locale.ROOT, "%,d", fee)])
        sendGrave(player, grave)
    }

    private fun takeEach(player: Player, grave: Grave, slots: List<Int>) {
        for (slot in slots) {
            if (!takeSlot(player, grave, slot)) {
                message(player, words(player)[CombatLines.GRAVE_NO_SPACE])
                break
            }
        }
        finishTaking(player, grave)
    }

    /**
     * A Hardcore Ironman's death makes them an Ironman: every death here is a dangerous one. Guessed: that they are told
     * so in a game message, whose words aren't known.
     */
    private fun loseHardcore(player: Player) {
        val types = player.vars.types
        if (types.varbit(SettingsSide.VARBIT_IRONMAN) == null || player.vars.getVarbit(SettingsSide.VARBIT_IRONMAN) != HARDCORE_IRONMAN) return
        setVarbitNow(player, SettingsSide.VARBIT_IRONMAN, IRONMAN)
        message(player, words(player)[CombatLines.HARDCORE_LOST])
    }

    /** Moves what fits of [slot] into the inventory; false when none of it fit. */
    private fun takeSlot(player: Player, grave: Grave, slot: Int): Boolean {
        val obj = grave.items[slot] ?: return true
        val type = objTypes[obj.id] ?: ObjType(obj.id)
        val taken = player.inventory.add(type, obj.count)
        if (taken == 0) return false
        grave.items.remove(slot, taken)
        return true
    }

    /** An emptied grave goes; otherwise the viewer sees what is left. */
    private fun finishTaking(player: Player, grave: Grave) {
        if (grave.items.isEmpty()) {
            closeModals(player)
            graves.remove(grave.npc)
            removeNpc(grave.npc)
            viewing.values.removeIf { it === grave }
        } else {
            sendGrave(player, grave)
        }
    }

    companion object {
        /** A PvP death's loot is the killer's alone for a minute, then everyone's for four (OSRS Wiki, Items Kept on Death). */
        const val PVP_LOOT_PRIVATE_TICKS = 100
        const val PVP_LOOT_DESPAWN_TICKS = 500

        /** Untradeables lost in a PvP death at this Wilderness level or below go to the grave (OSRS Wiki, Items Kept on Death). */
        const val UNTRADEABLES_TO_GRAVE_LEVEL = 20

        /** Every interface and component id above with its gameval name in the rev 242 cache, for [osrsps.cache.GameFrameCheck]. */
        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>>
            get() = mapOf(
                GRAVESTONE to ("gravestone_generic" to mapOf(FREEITEMS to "freeitems", FREEBUTTON to "freebutton", PAYITEMS to "payitems", PAYBUTTON to "paybutton")),
            )

        /** NPC "Grave" (cache rev 241): op1 Check, op3 Loot. 9857 is the same with an angel. */
        const val GRAVE = 9856
        const val CHECK = 1
        const val LOOT = 3

        /** Interface gravestone_generic: the free items' layer, their Take-All button; the paid items', and Unlock. */
        const val GRAVESTONE = 672
        const val FREEITEMS = 6
        const val FREEBUTTON = 8
        const val PAYITEMS = 13
        const val PAYBUTTON = 15

        /** Clientscript gravestone_transmit_data (fee, coffer). */
        const val TRANSMIT_DATA = 3478

        /** gravestone_feethreshold_under1m and _under10m; [VARBIT_FREE_FROM] is _under100k. */
        const val VARBIT_UNDER_1M = 10473
        const val VARBIT_UNDER_10M = 10474

        /** gravestone_has_merged; gravestone_creation_death_coord. */
        const val VARBIT_MERGED = 15023
        const val VARP_DEATH_COORD = 3916

        /** What a unit must be worth to be in the paid list. */
        const val FREE_UNDER = 100_000L

        const val COINS = 995

        /** The `ironman` varbit's Hardcore Ironman, and the Ironman it becomes. */
        const val HARDCORE_IRONMAN = 3
        const val IRONMAN = 1

        /** How far from the grave, in tiles either way, its gravestone's buttons still work. A guard, not from live. */
        const val GRAVE_REACH = 2

        /** The gravestone's inv: 120 slots. */
        const val GRAVE_INV = 525
        const val GRAVE_CAPACITY = 120

        /** gravestone_feethreshold_under100k: the interface's free list starts at this varbit's value minus one. */
        const val VARBIT_FREE_FROM = 10472

        /** Examine on an item slot, as the inventory's. */
        const val EXAMINE_OP = 10

        /** human_death. */
        const val DEATH_ANIM = 836

        /** Ticks the player lies dead before the respawn. Guessed from the death animation's length. */
        const val DEATH_TICKS = 5

        /** The Wiki's three items kept on death with no Protect Item and no skull. */
        const val KEPT_ITEMS = 3

        /** 15 minutes (OSRS Wiki, Gravestone). */
        const val GRAVE_TICKS = 1500
        const val TICKS_PER_MINUTE = 100
    }
}
