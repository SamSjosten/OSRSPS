package osrsps.game.content

import java.util.concurrent.ConcurrentHashMap
import osrsps.game.FrameSlot
import osrsps.game.Interfaces
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.WearPos
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/** The fairy rings' words. */
object FairyRingLines : Lines("fairy_ring", "Fairy_ring") {
    val NO_STAFF = line("no_staff", "any ring option without a dramen or lunar staff wielded (the Wiki's Fairy ring, Trivia)")
}

/**
 * One fairy ring code, a row of the cache's `fairyring` table ([osrsps.cache.FairyRingLoader]). [id] is the dials'
 * positions as three digits (the first dial's A, D, C, B are 0 to 3, the second's I, L, K, J, the third's P, S, R, Q), so
 * it is what the dials' varbits spell. [state] is the code's number in the rings' Last-destination option (varbit
 * fairyring_lastloc). [destination] is null for the codes that lead nowhere. [apparition] is the creature that can come
 * through with the player when they leave from this code's ring; [noStaffReturn] lets the ring's Zanaris option work
 * without a staff, for the rings nothing else reaches. [description] is the code's travel log line (the cache's, empty
 * for the codes that lead nowhere), shown in the log's entry [textComponent] beside the star [faveIconComponent]; both
 * are -1 for a code the log has no entry for.
 */
data class FairyRingCode(
    val id: Int,
    val state: Int,
    val code: String,
    val destination: Destination?,
    val apparition: Int? = null,
    val noStaffReturn: Boolean = false,
    val description: String = "",
    val textComponent: Int = -1,
    val faveIconComponent: Int = -1,
) {
    /** The code as the log writes it, a space between the letters ("D K R"). */
    val spaced: String get() = code.toCharArray().joinToString(" ")
}

/** Every code, by the dials that spell it and by the tile its ring stands on. */
class FairyRingCodes(val all: List<FairyRingCode>) {
    private val byId = all.associateBy { it.id }
    private val byState = all.associateBy { it.state }
    private val byTile = all.filter { it.destination != null }.associateBy { it.destination }

    fun dialled(first: Int, second: Int, third: Int): FairyRingCode? = byId[first * 100 + second * 10 + third]

    fun byId(id: Int): FairyRingCode? = byId[id]

    fun byState(state: Int): FairyRingCode? = byState[state]

    fun byCode(code: String): FairyRingCode? = all.firstOrNull { it.code.equals(code, ignoreCase = true) }

    /** The code whose ring stands on [x], [z], [level]: each ring's code lands on the ring's own tile. */
    fun at(x: Int, z: Int, level: Int): FairyRingCode? = byTile[Destination(x, z, level)]

    companion object {
        val NONE = FairyRingCodes(emptyList())
    }
}

/**
 * The fairy rings (travel networks step T1). Every ring in the world is one loc, `fairyring_minorhub` (29495: Zanaris,
 * Configure, Last-destination, Favourites), but Zanaris's own, `fairyring_homehub` (29560: no Zanaris option). Every
 * account has finished the quests, so the network is open; the rings still work only for a player wielding a dramen or
 * lunar staff (the Wiki's Fairy ring page), and refuse with [FairyRingLines.NO_STAFF] otherwise.
 *
 * - Configure opens the dials (interface `fairyrings`, 398). Each dial button turns its dial a quarter: the client turns
 *   its own copy of the dial's varbit at once (client script fairyrings_op) and the server sets its varbit to match, or
 *   the dial would snap back. Confirm reads the three dials and goes to the code they spell.
 * - Zanaris goes to Zanaris's ring (BKS). Last-destination goes straight to the code last dialled: the option shows
 *   only once one has been ([VARBIT_LASTLOC_SET]), naming it by [VARBIT_LASTLOC] (the cache's multiop3 list).
 * - Where each code lands, and which codes lead nowhere, is the cache's `fairyring` table, read at start: each code
 *   lands on its ring's own tile, which is where the 242 map places the ring.
 * - Configure opens the travel log (interface `fairyrings_log`, 381) in the side panel beside the dials (step T2). The
 *   log lists each code the player has landed on, by the cache's `fairyrings_log_*` varbits ([VISITED], from client
 *   script 7866, which the world map's ring tooltips read too), with the table's line for it; the client hides an
 *   entry whose text the server left empty and sorts the rest (client script fairyrings_sort_update). An entry's Use
 *   code sets the dials to it; Add Favourite (the entry's second option, or its star) puts it in the first free of the
 *   ten favourite slots (varbits `fairyring_fave_1` to `_10`, the code's state plus 64), which the log lists on top
 *   and the rings' Favourites option offers as sub-options. A favourite's Remove Favourite (its second option, or its
 *   star) clears its slot.
 *
 * Sources: the 242 cache (the loc types and their ops, the `fairyring` table, interface 398 and client scripts
 * fairyrings_op, fairyrings_confirm and fairyrings_rotate, the varbits, seqs `human_fairy_vanish` and
 * `human_fairy_appear`, spotanim `fairy_flower_ring`); the OSRS Wiki's Fairy ring page (read 19 Nov 2026) for the staff
 * rule, its message, and the creatures that come through (1 in 6, rolled on every trip, Mod Ash's tweet it cites).
 *
 * Inferred: a code that leads nowhere lands a few tiles from Zanaris's ring, as the RS3 Wiki's Fairy ring page says
 * ([INVALID_LANDING]); the OSRS Wiki doesn't say. The player stays where they stand to use a ring: the ring is a 1 by 1
 * loc that blocks walking, on its centre tile, which the trip lands on.
 * Guessed: the trip's pace ([VANISH_TICKS], [APPEAR_TICKS]: the vanish seq runs 79 client cycles, 2.6 ticks); the
 * graphic on the player's start; a creature coming through landing on a free tile beside the player and leaving after
 * [APPARITION_TICKS]; DIQ (the player-owned house's ring, which needs a fairy ring in the house's garden) leading
 * nowhere until houses have gardens.
 * Inferred: a code counts as visited when a trip lands on its ring, whichever option sent the player there (Zanaris's
 * BKS too); the log shows the visited codes only (the Wiki's Fairy ring, Travel log: codes are "logged" as they're
 * used). `fairyrings_favourite_opvis`, which shows the rings' Favourites option for any value from 1, holds one bit for
 * each slot in use.
 * Guessed: Add Favourite on a code already in a slot, or with all ten slots full, does nothing; Remove Favourite leaves
 * the other slots where they are (the log lists the slots in order and skips the empty ones, so nothing shows a gap).
 * The log's hideout line (a code outside the table) stays hidden. Tele Block doesn't stop the rings: the Wiki's Tele
 * Block page blocks "most forms of teleportation" without naming them, and its Fairy ring page doesn't say. No code in
 * the cache's table lands in the Wilderness and no ring stands in it, so the rings need no Wilderness check.
 * Not built yet: Zanaris itself (step T3), the elite Lumbridge & Draynor Diary freeing the rings from the staff (no
 * diaries yet), the house's spiritual fairy tree, the log's search box (the client's own).
 */
class FairyRings(private val world: World, val codes: FairyRingCodes) {
    /** The ring each player dials at while the dials are open. */
    private val dialling = ConcurrentHashMap<Player, ZoneLoc>()

    /** How many players have the dials open, for the tests. */
    internal val dialsOpen: Int get() = dialling.size

    fun register() {
        world.handlers.onLoc(MINOR_HUB, ZANARIS) { op -> zanaris(op.loc ?: return@onLoc) }
        for (ring in RINGS) {
            world.handlers.onLoc(ring, CONFIGURE) { op -> configure(op.loc ?: return@onLoc) }
            world.handlers.onLoc(ring, LAST_DESTINATION) { op -> lastDestination(op.loc ?: return@onLoc) }
        }
        for ((dial, varbit) in DIALS.withIndex()) {
            world.handlers.onButton(INTERFACE, CLOCKWISE[dial]) { turn(varbit, 1) }
            world.handlers.onButton(INTERFACE, ANTICLOCKWISE[dial]) { turn(varbit, 3) }
        }
        world.handlers.onButton(INTERFACE, CONFIRM) { confirm() }
        world.interfaces.onClose(INTERFACE) { player -> dialling.remove(player) }
        for (ring in RINGS) world.handlers.onLoc(ring, FAVOURITES) { op -> favourite(op.loc ?: return@onLoc, op.sub) }
        for (code in codes.all) {
            if (code.textComponent >= 0) {
                world.handlers.onButton(LOG, code.textComponent) { op -> if (op.option == 1) useCode(code) else addFavourite(code) }
            }
            if (code.faveIconComponent >= 0) world.handlers.onButton(LOG, code.faveIconComponent) { addFavourite(code) }
        }
        for (slot in FAVES.indices) {
            world.handlers.onButton(LOG, FAVE_TEXT[slot]) { op -> if (op.option == 1) useFavourite(slot) else removeFavourite(slot) }
            world.handlers.onButton(LOG, FAVE_ICON[slot]) { removeFavourite(slot) }
        }
    }

    /** Lets [player] go when they leave the world: a lost connection doesn't close the dials, so no close hook runs. */
    fun forget(player: Player) {
        dialling.remove(player)
    }

    /** The code in favourite slot [slot] (0 to 9), or null for an empty one. */
    fun favourite(player: Player, slot: Int): FairyRingCode? {
        val varbit = FAVES[slot]
        if (player.vars.types.varbit(varbit) == null) return null
        val value = player.vars.getVarbit(varbit)
        return if (value < FAVE_SET) null else codes.byState(value - FAVE_SET)
    }

    /** Whether the player has landed on [code]'s ring, which puts it in their travel log. */
    fun visited(player: Player, code: FairyRingCode): Boolean {
        val varbit = VISITED[code.id] ?: return false
        return player.vars.types.varbit(varbit) != null && player.vars.getVarbit(varbit) != 0
    }

    /** The code the player's dials spell now. */
    fun dialled(player: Player): FairyRingCode? {
        val (first, second, third) = DIALS.map { if (player.vars.types.varbit(it) != null) player.vars.getVarbit(it) else 0 }
        return codes.dialled(first, second, third)
    }

    private suspend fun ScriptContext.zanaris(ring: ZoneLoc) {
        arriveDelay()
        val here = codes.at(ring.x, ring.z, ring.level)
        // The rings nothing else reaches send a stranded player home without a staff (the Wiki's Fairy ring, Trivia).
        if (here?.noStaffReturn != true && !hasStaff(player)) return world.message(player, FairyRingLines.NO_STAFF)
        travel(ring, codes.byCode(ZANARIS_CODE), remember = false)
    }

    private suspend fun ScriptContext.configure(ring: ZoneLoc) {
        arriveDelay()
        if (!hasStaff(player)) return world.message(player, FairyRingLines.NO_STAFF)
        dialling[player] = ring
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, INTERFACE)
        for (component in CLOCKWISE + ANTICLOCKWISE + CONFIRM) {
            world.interfaces.setEvents(player, INTERFACE, component, -1, -1, Interfaces.IF_EVENT_OP1)
        }
        world.interfaces.openModal(player, FrameSlot.SIDEMODAL, LOG)
        for (code in codes.all) {
            if (code.textComponent >= 0) {
                world.interfaces.setEvents(player, LOG, code.textComponent, -1, -1, Interfaces.IF_EVENT_OP1 or Interfaces.IF_EVENT_OP2)
            }
            if (code.faveIconComponent >= 0) world.interfaces.setEvents(player, LOG, code.faveIconComponent, -1, -1, Interfaces.IF_EVENT_OP1)
            // Only the visited codes' lines are sent: an opened log starts with every entry's text empty, so hidden.
            if (code.textComponent >= 0 && code.description.isNotEmpty() && visited(player, code)) {
                world.interfaces.setText(player, LOG, code.textComponent, code.description)
            }
        }
        for (slot in FAVES.indices) {
            world.interfaces.setEvents(player, LOG, FAVE_TEXT[slot], -1, -1, Interfaces.IF_EVENT_OP1 or Interfaces.IF_EVENT_OP2)
            world.interfaces.setEvents(player, LOG, FAVE_ICON[slot], -1, -1, Interfaces.IF_EVENT_OP1)
        }
        showFavourites(player)
    }

    /** Writes the ten favourite slots into the open log and has the client lay the log out again. */
    private fun showFavourites(player: Player) {
        for (slot in FAVES.indices) {
            val code = favourite(player, slot)
            world.interfaces.setText(player, LOG, FAVE_TEXT[slot], code?.description ?: "")
            world.interfaces.setText(player, LOG, FAVE_CODE[slot], code?.spaced ?: "")
        }
        world.interfaces.runClientScript(player, SORT_UPDATE, listOf(LOG shl 16 or CONTENTS, LOG shl 16 or SCROLLBAR, 1, 1, LOG shl 16 or DIVIDER, LOG shl 16 or FAVES_LAYER))
    }

    /** Use code, on a log entry or a favourite: the dials turn to [code], ready for Confirm. */
    private fun ScriptContext.useCode(code: FairyRingCode) {
        if (!logOpen() || !visited(player, code) || DIALS.any { !known(it) }) return
        // The id's digits are the dials' positions.
        player.vars.setVarbit(DIALS[0], code.id / 100)
        player.vars.setVarbit(DIALS[1], code.id / 10 % 10)
        player.vars.setVarbit(DIALS[2], code.id % 10)
    }

    private fun ScriptContext.useFavourite(slot: Int) {
        useCode(favourite(player, slot) ?: return)
    }

    private fun ScriptContext.addFavourite(code: FairyRingCode) {
        // The client shows the entry only for a visited code, so a click on another is forged.
        if (!logOpen() || !visited(player, code) || FAVES.any { !known(it) }) return
        if (FAVES.indices.any { favourite(player, it) == code }) return
        val slot = FAVES.indices.firstOrNull { favourite(player, it) == null } ?: return
        player.vars.setVarbit(FAVES[slot], FAVE_SET + code.state)
        favouritesChanged()
    }

    private fun ScriptContext.removeFavourite(slot: Int) {
        if (!logOpen() || favourite(player, slot) == null) return
        player.vars.setVarbit(FAVES[slot], 0)
        favouritesChanged()
    }

    private fun ScriptContext.favouritesChanged() {
        if (known(VARBIT_FAVOURITE_OPVIS)) {
            player.vars.setVarbit(VARBIT_FAVOURITE_OPVIS, FAVES.indices.filter { favourite(player, it) != null }.sumOf { 1 shl it })
        }
        showFavourites(player)
    }

    /** The rings' Favourites option, sub-option [sub] (1 to 10) for that slot: straight to the code, like Last-destination. */
    private suspend fun ScriptContext.favourite(ring: ZoneLoc, sub: Int) {
        arriveDelay()
        // The client offers only the slots in use.
        val code = if (sub in 1..FAVES.size) favourite(player, sub - 1) else null
        if (code == null) return
        if (!hasStaff(player)) return world.message(player, FairyRingLines.NO_STAFF)
        travel(ring, code, remember = true)
    }

    private fun ScriptContext.logOpen() = player.modals[FrameSlot.MAINMODAL] == INTERFACE && player.modals[FrameSlot.SIDEMODAL] == LOG

    private suspend fun ScriptContext.lastDestination(ring: ZoneLoc) {
        arriveDelay()
        // The client hides the option until a code has been dialled; a click without one is forged.
        if (!known(VARBIT_LASTLOC_SET) || player.vars.getVarbit(VARBIT_LASTLOC_SET) == 0) return
        if (!hasStaff(player)) return world.message(player, FairyRingLines.NO_STAFF)
        travel(ring, codes.byState(player.vars.getVarbit(VARBIT_LASTLOC)), remember = true)
    }

    private fun ScriptContext.turn(varbit: Int, quarters: Int) {
        if (player.modals[FrameSlot.MAINMODAL] != INTERFACE || !known(varbit)) return
        player.vars.setVarbit(varbit, (player.vars.getVarbit(varbit) + quarters) % 4)
    }

    private suspend fun ScriptContext.confirm() {
        val ring = dialling[player] ?: return
        // Dials left open by a move that doesn't close them (::tele) would send the player from wherever they went.
        if (player.modals[FrameSlot.MAINMODAL] != INTERFACE || !beside(player, ring) || !world.locs.exists(ring)) return
        if (!hasStaff(player)) return world.message(player, FairyRingLines.NO_STAFF)
        val code = dialled(player)
        world.interfaces.closeModals(player)
        travel(ring, code, remember = true)
    }

    /**
     * The trip from [ring] to [code]'s ring, or to [INVALID_LANDING] for a code that leads nowhere: the vanish, the
     * move, the appearance. [remember] makes it the ring's Last-destination.
     */
    private suspend fun ScriptContext.travel(ring: ZoneLoc, code: FairyRingCode?, remember: Boolean) {
        val from = codes.at(ring.x, ring.z, ring.level)
        if (remember && code != null && known(VARBIT_LASTLOC) && known(VARBIT_LASTLOC_SET)) {
            player.vars.setVarbit(VARBIT_LASTLOC, code.state)
            player.vars.setVarbit(VARBIT_LASTLOC_SET, 1)
        }
        player.anim(VANISH_ANIM)
        player.spotanim(RING_SPOTANIM)
        delay(VANISH_TICKS)
        val to = code?.destination ?: INVALID_LANDING
        world.teleport(player, to.x, to.z, to.level)
        val visited = code?.takeIf { it.destination != null }?.let { VISITED[it.id] }
        if (visited != null && known(visited)) player.vars.setVarbit(visited, 1)
        player.anim(APPEAR_ANIM)
        from?.apparition?.let { apparition(it, to) }
        delay(APPEAR_TICKS)
    }

    /** One time in six, the creature of the ring the player left comes through too, rolled on every trip, a wrong code's too. */
    private fun apparition(npcId: Int, at: Destination) {
        if (world.random.between(1, APPARITION_CHANCE) != 1) return
        val type = world.npcTypes[npcId] ?: return
        val tile = APPARITION_OFFSETS.map { (dx, dz) -> Destination(at.x + dx, at.z + dz, at.level) }
            .firstOrNull { world.canStand(it.x, it.z, it.level) } ?: return
        val npc = world.spawnNpc(NpcSpawn(type, tile.x, tile.z, tile.level, wander = 0, source = "fairy ring apparition", respawns = false)) ?: return
        world.npcScripts.queue(npc, APPARITION_TICKS) { world.removeNpc(it) }
    }

    private fun ScriptContext.known(varbit: Int) = player.vars.types.varbit(varbit) != null

    companion object {
        /** `fairyring_minorhub`: every ring but Zanaris's. */
        const val MINOR_HUB = 29495

        /** `fairyring_homehub`: Zanaris's ring, BKS. */
        const val HOME_HUB = 29560

        val RINGS = listOf(MINOR_HUB, HOME_HUB)

        const val ZANARIS = 1
        const val CONFIGURE = 2
        const val LAST_DESTINATION = 3

        /** Interface `fairyrings`: the three dials. */
        const val INTERFACE = 398

        /** `1_clockwise`, `2_clockwise`, `3_clockwise`: a quarter turn on (+1). */
        val CLOCKWISE = listOf(19, 21, 23)

        /** `1_anticlockwise` and so on: a quarter turn back (+3, as fairyrings_op adds it). */
        val ANTICLOCKWISE = listOf(20, 22, 24)

        const val CONFIRM = 26

        /** Op4, Favourites: its sub-options 1 to 10 are the slots. */
        const val FAVOURITES = 4

        /** Interface `fairyrings_log`: the travel log, in the side panel. */
        const val LOG = 381
        const val CONTENTS = 8
        const val FAVES_LAYER = 9
        const val DIVIDER = 10
        const val SCROLLBAR = 166

        /** `fave_1` to `_10` (the line, Use code and Remove Favourite), `fave_code_1` to `_10`, `fave_icon_1` to `_10` (the star). */
        val FAVE_TEXT = (141..150).toList()
        val FAVE_CODE = (151..160).toList()
        val FAVE_ICON = listOf(161, 162, 163, 164, 165, 167, 168, 169, 170, 171)

        /** Client script `fairyrings_sort_update`: lays the log out from its components' texts. */
        const val SORT_UPDATE = 402

        /** Varbits `fairyring_fave_1` to `_10`: a slot's code state plus [FAVE_SET], 0 for an empty slot. */
        val FAVES = (20240..20249).toList()
        const val FAVE_SET = 64

        /** Varbit `fairyrings_favourite_opvis`: nonzero shows the rings' Favourites option. */
        const val VARBIT_FAVOURITE_OPVIS = 20239

        /**
         * Each code's `fairyrings_log_*` varbit, by the code's id: client script 7866's switch, which the world map's
         * ring tooltips read as "visited". Codes that lead nowhere have none.
         */
        val VISITED: Map<Int, Int> = mapOf(
            1 to 16660, 2 to 3989, 3 to 3990, 10 to 4968, 11 to 3991, 12 to 3992, 13 to 3993, 20 to 13840, 21 to 3994,
            22 to 9570, 23 to 3995, 30 to 9818, 31 to 3996, 32 to 3997, 33 to 3998, 100 to 3256, 101 to 3999,
            102 to 4000, 103 to 5344, 110 to 18313, 111 to 4001, 112 to 4002, 113 to 4003, 120 to 4004, 121 to 4005,
            122 to 4006, 130 to 4007, 132 to 4008, 200 to 4009, 201 to 4892, 202 to 7855, 203 to 4010, 210 to 4011,
            211 to 4012, 212 to 4620, 220 to 4013, 221 to 4014, 222 to 4015, 223 to 11192, 232 to 4016, 233 to 18312,
            300 to 4017, 301 to 4018, 303 to 4019, 310 to 4020, 311 to 15255, 312 to 4021, 313 to 13598, 320 to 4022,
            321 to 5365, 322 to 4023, 323 to 4024, 330 to 9551, 331 to 4389, 332 to 4025,
        )

        /** Varbits `fairyring_1` to `_3`: each dial's position, 0 to 3. */
        val DIALS = listOf(3985, 3986, 3987)

        /** Varbit `fairyring_lastloc`: the last code's state; `fairyring_lastloc_set` (bit 6 of the same varp) shows the option. */
        const val VARBIT_LASTLOC = 5374
        const val VARBIT_LASTLOC_SET = 20250

        const val ZANARIS_CODE = "BKS"

        /** Seqs `human_fairy_vanish` and `human_fairy_appear`, and spotanim `fairy_flower_ring`. */
        const val VANISH_ANIM = 3265
        const val APPEAR_ANIM = 3266
        const val RING_SPOTANIM = 569

        /** Guessed: the move comes 3 ticks after the click, and the player can act 3 ticks after landing. */
        const val VANISH_TICKS = 3
        const val APPEAR_TICKS = 3

        /** Inferred from the RS3 Wiki ("a few spaces away from the main fairy ring in Zanaris"); the tile is guessed. */
        val INVALID_LANDING = Destination(2412, 4431, 0)

        const val APPARITION_CHANCE = 6

        /** Guessed: how long a creature that came through stays. */
        const val APPARITION_TICKS = 50
        private val APPARITION_OFFSETS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, -1 to -1, 1 to -1, -1 to 1)

        /** A dramen staff or a lunar staff. */
        val STAVES = setOf(LumbridgeSwamp.DRAMEN_STAFF, LumbridgeSwamp.LUNAR_STAFF)

        fun hasStaff(player: Player): Boolean = player.equipment[WearPos.WEAPON]?.id in STAVES

        /** Whether [player] stands within a tile of [ring], which a 1 by 1 loc is used from. */
        fun beside(player: Player, ring: ZoneLoc): Boolean =
            player.level == ring.level && maxOf(kotlin.math.abs(player.x - ring.x), kotlin.math.abs(player.z - ring.z)) <= 1
    }
}
