package osrsps.game.content

import osrsps.game.InvObj
import osrsps.game.Player
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.combat.Teleblock
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** The cowbell amulet's lines (keys only; the words are in data/dialogue.txt). */
object CowbellLines : Lines("cowbell_amulet", "Transcript:Cowbell amulet") {
    val RING = line("ring", "Ring away from Brutus's field")
    val RING_NOTHING = line("ring_nothing", "Ring in Brutus's field while he is alive")
    val RING_APPROACHING = line("ring_approaching", "Ring in Brutus's field while he is dead: he comes back sooner")
    val ADDED = line("added", "air runes used on the amulet: {amount} added, {s} after rune, {total} charges, {charges_s} after charge", "amount", "s", "total", "charges_s")
    val FULL = line("full", "air runes used on a fully charged amulet")
    val UNCHARGED = line("uncharged", "Uncharge: {amount} air runes back, {s} after rune", "amount", "s")
    val CHECK = line("check", "Check: {amount} charges, {s} after charge", "amount", "s")
    val RAN_OUT = line("ran_out", "the teleport that used the last charge (in red)")
    val NO_CHARGES = line("no_charges", "Teleport on an amulet with no charges left (ours)")
    val LOST = line("lost", "the player to Gillie, the amulet lost (Transcript:Gillie Groats)")
    val ANOTHER = line("another", "Gillie: here's another (Transcript:Gillie Groats)")
    val HANDS = line("hands", "Gillie hands over an amulet (Transcript:Gillie Groats)")
    val THANKS = line("thanks", "the player thanks Gillie (Transcript:Gillie Groats)")
    val NO_PROBLEM = line("no_problem", "Gillie: no problem (Transcript:Gillie Groats)")
}

/**
 * The cowbell amulet (Brutus step 4; the OSRS Wiki's Cowbell amulet and its transcript, and Transcript:Gillie Groats,
 * read 2026-11-22; ids and options from the 242 cache): Gillie Groats hands one over (The Ides of Milk counts as done)
 * to a player who has none, again if they lose it.
 *
 * - **Charge**: air runes used on it (or its Charge), as many as the player says, one charge each up to [MAX_CHARGES].
 *   **Check** tells the charges; **Uncharge** gives back an air rune for each.
 * - **Teleport** to the Lumbridge cow field for a charge, not above level 20 Wilderness; the last charge leaves it empty.
 * - **Ring**: in Brutus's field while he is dead, he comes back [RING_RESPAWN_TICKS] ticks later, sooner or later than
 *   he would have ("ringing the cowbell amulet too late ... can result in a longer respawn time").
 * - **Worn**, dairy cows are milked every [MILK_TICKS] ticks ("in 6 ticks (3.6s) instead of 7").
 *
 * GUESSED until recorded: the charges belong to the player, not to each amulet (as every charged item here does; live
 * keeps them on each amulet), and an amulet from Gillie to a player who had lost theirs starts empty, the old charges
 * gone with it; Charge doing what air runes used on it do; the teleport's animation and graphic (the standard
 * spellbook's) and its 3 ticks, landing within 2 tiles of [DESTINATION] (the Wiki's r=2); the amulet's own options only
 * (its worn options left out). Left out: the cows at the destination saying "Moo?".
 */
object CowbellAmulet {
    const val EMPTY = 33103
    const val CHARGED = 33104
    val AMULETS = listOf(EMPTY, CHARGED)
    const val AIR_RUNE = 556

    const val CHARGES = "cowbell_amulet"
    const val MAX_CHARGES = 1_000

    // The empty amulet's options: Wear, Ring, Charge; the charged one's: Teleport, Wear, Ring, Check, Uncharge.
    const val TELEPORT = 1
    const val RING = 3
    const val CHARGE = 4
    const val CHECK = 4
    const val UNCHARGE = 5

    const val RING_RESPAWN_TICKS = 13
    const val MILK_TICKS = 6

    val DESTINATION = osrsps.map.Tile(3259, 3277)
    const val DESTINATION_RADIUS = 2

    const val TELEPORT_ANIM = 714
    const val TELEPORT_SPOTANIM = 111
    const val TELEPORT_SPOTANIM_HEIGHT = 92
    const val TELEPORT_TICKS = 3

    fun worn(player: Player) = AMULETS.any { player.equipment.count(it) > 0 }

    fun anywhere(player: Player) = AMULETS.any { player.inventory.count(it) > 0 || player.equipment.count(it) > 0 || player.bank.count(it) > 0 }

    private fun s(count: Int) = if (count == 1) "" else "s"

    /** Turns one amulet in the backpack (or worn) of id [from] into [to]. */
    private fun swap(player: Player, from: Int, to: Int) {
        player.inventory.contents().firstOrNull { it.second.id == from }?.let { (slot, _) ->
            player.inventory[slot] = InvObj(to, 1)
            return
        }
        val worn = player.equipment.contents().firstOrNull { it.second.id == from } ?: return
        player.equipment[worn.first] = InvObj(to, 1)
    }

    /** Air runes on the amulet, or its Charge: how many to add, then that many taken for as many charges. */
    suspend fun ScriptContext.charge(world: World) {
        val have = player.charges[CHARGES]
        if (have >= MAX_CHARGES) return world.message(player, CowbellLines.FULL)
        val runes = player.inventory.count(AIR_RUNE)
        if (runes <= 0L) return
        val wanted = countDialog()
        val added = minOf(wanted.toLong(), runes, (MAX_CHARGES - have).toLong()).toInt()
        if (added <= 0) return
        val slot = player.inventory.contents().firstOrNull { it.second.id == AIR_RUNE }?.first ?: return
        if (player.inventory.remove(slot, added) != added) return
        player.charges[CHARGES] = have + added
        swap(player, EMPTY, CHARGED)
        val total = player.charges[CHARGES]
        world.dialogues.start(this) { objbox(CHARGED, text(CowbellLines.ADDED, "amount" to added, "s" to s(added), "total" to total, "charges_s" to s(total))) }
    }

    suspend fun ScriptContext.uncharge(world: World) {
        val have = player.charges[CHARGES]
        if (have > 0) {
            val type = world.objTypes[AIR_RUNE] ?: return
            player.charges[CHARGES] = 0
            // Into the backpack, or under the player when it has no room.
            world.groundObjs.give(player, type, have)
        }
        swap(player, CHARGED, EMPTY)
        world.dialogues.start(this) { objbox(EMPTY, text(CowbellLines.UNCHARGED, "amount" to have, "s" to s(have))) }
    }

    fun check(world: World, player: Player) {
        val have = player.charges[CHARGES]
        world.message(player, CowbellLines.CHECK, "amount" to have, "s" to s(have))
    }

    suspend fun ScriptContext.teleport(world: World) {
        val have = player.charges[CHARGES]
        if (have <= 0) {
            swap(player, CHARGED, EMPTY)
            return world.message(player, CowbellLines.NO_CHARGES)
        }
        val refusal = Teleblock.refusal(player, Wilderness.level(player.x, player.z))
        if (refusal.isNotEmpty()) {
            for (line in refusal) world.message(player, line)
            return
        }
        player.charges[CHARGES] = have - 1
        if (have == 1) {
            swap(player, CHARGED, EMPTY)
            world.message(player, CowbellLines.RAN_OUT)
        }
        world.interfaces.closeModals(player)
        player.scripts.dropWeak()
        player.waypoints.clear()
        player.anim(TELEPORT_ANIM)
        player.spotanim(TELEPORT_SPOTANIM, TELEPORT_SPOTANIM_HEIGHT)
        delay(TELEPORT_TICKS)
        val x = DESTINATION.x + world.random.between(-DESTINATION_RADIUS, DESTINATION_RADIUS)
        val z = DESTINATION.z + world.random.between(-DESTINATION_RADIUS, DESTINATION_RADIUS)
        world.teleport(player, x, z, 0)
        player.anim(-1)
    }

    /** Ring: in the player's own field with Brutus dead, he comes back [RING_RESPAWN_TICKS] ticks from now. */
    suspend fun ScriptContext.ring(world: World, used: Int) {
        val run = world.brutusPens.runOf(player)
        val line = when {
            run == null -> CowbellLines.RING
            world.npcs.all.any { it.type.id == Brutus.BOSS && !it.dead && run.instance.contains(it.x, it.z) } -> CowbellLines.RING_NOTHING
            world.meleeCombat.respawnOn(Brutus.BOSS, world.tick + RING_RESPAWN_TICKS) { x, z -> run.instance.contains(x, z) } -> CowbellLines.RING_APPROACHING
            else -> CowbellLines.RING_NOTHING
        }
        world.dialogues.start(this) { objbox(used, line) }
    }

    /** Gillie to a player with no amulet anywhere: another, empty. */
    suspend fun ScriptContext.another(world: World, gillie: osrsps.game.npc.Npc) {
        world.dialogues.start(this, gillie) {
            chatPlayer(ChatAnim.SAD, CowbellLines.LOST)
            chatNpc(ChatAnim.HAPPY, CowbellLines.ANOTHER)
            val type = world.objTypes[EMPTY]
            if (type != null && !anywhere(player)) {
                world.groundObjs.give(player, type, 1)
                // The lost amulet's charges went with it.
                player.charges[CHARGES] = 0
            }
            objbox(EMPTY, CowbellLines.HANDS)
            chatPlayer(ChatAnim.HAPPY, CowbellLines.THANKS)
            chatNpc(ChatAnim.HAPPY, CowbellLines.NO_PROBLEM)
        }
    }
}

fun registerCowbellAmulet(world: World) {
    with(CowbellAmulet) {
        world.handlers.onObjOnObj(AIR_RUNE, EMPTY) { charge(world) }
        world.handlers.onObjOnObj(AIR_RUNE, CHARGED) { charge(world) }
        world.handlers.onObj(EMPTY, CHARGE) { charge(world) }
        world.handlers.onObj(EMPTY, RING) { ring(world, EMPTY) }
        world.handlers.onObj(CHARGED, RING) { ring(world, CHARGED) }
        world.handlers.onObj(CHARGED, TELEPORT) { teleport(world) }
        world.handlers.onObj(CHARGED, CHECK) { check(world, player) }
        world.handlers.onObj(CHARGED, UNCHARGE) { uncharge(world) }
    }
}
