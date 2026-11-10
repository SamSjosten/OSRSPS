package osrsps.game.content

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** The H.A.M. Hideout's words, in the dialogue file. */
object HamHideoutLines : Lines("ham_hideout", "OSRS Wiki, H.A.M. Hideout, Trapdoor (H.A.M. Hideout) and H.A.M. Member, read 2026-11-10") {
    val TRAPDOOR_LOCKED = line("trapdoor_locked", "Open on the hideout's locked trapdoor; words unsourced")
    val PICK = line("pick", "Pick-lock on the trapdoor or the cell door: the attempt; words unsourced")
    val PICK_FAIL = line("pick_fail", "a failed Pick-lock: the fingers numbed, Thieving down 1 (the Trapdoor page); words unsourced")
    val PICK_DONE = line("pick_done", "a Pick-lock that works; words unsourced")
    val CELL_LOCKED = line("cell_locked", "Open on Jimmy the Chisel's cell door; words unsourced")
    val CONCUSSED = line("concussed", "caught pickpocketing a H.A.M. member: the blow lands (the H.A.M. Member page's message)")
    val AVOIDED = line("avoided", "caught pickpocketing a H.A.M. member: Agility takes some of the blow (the H.A.M. Member page's message)")
    val BUNDLED_OUT = line("bundled_out", "the third concussion: thrown out or jailed (the H.A.M. Member page's message)")
}

/**
 * The H.A.M. Hideout (Misthalin piece 7 step 4a), from the OSRS Wiki's H.A.M. Hideout, Trapdoor (H.A.M. Hideout),
 * H.A.M. Member and H.A.M. Guard pages (read 2026-11-10) and the rev 242 cache:
 *
 * - The trapdoor west of Lumbridge's general store (`ham_multi_trapdoor` [TRAPDOOR], varbit `ham_thief` 235: 1 shows it
 *   open, otherwise shut). Shut: Open says it is locked; Pick-Lock works at 4% at Thieving 1 to 98% at 99, or 24% to
 *   99% with a lockpick in the backpack, gives 3.8 XP (the infobox), and a fail numbs the fingers, Thieving down 1.
 *   Open: Climb-down to the foot of the ladder, Close. The ladder ([LADDER]) climbs back up beside it.
 * - Jimmy the Chisel's cell ([CELL_DOOR]): Open says it is locked; Pick-lock from inside at the trapdoor's rates lets
 *   the player out.
 * - Pickpocketing H.A.M. members ([osrsps.game.skill.Pickpocketing]): each blow that lands is a concussion ([caught]);
 *   Agility takes some blows instead, which don't count. The third concussion bundles the player out, unless their
 *   H.A.M. clothing keeps them in (each piece 4% less likely to be thrown out); out is the surface by the trapdoor or
 *   Jimmy's cell. Leaving or being thrown out starts the count again.
 *
 * Guessed: the messages but the three the H.A.M. Member page quotes; the chance moving linearly with Thieving between
 * the Wiki's two ends, with none rising on repeated fails (a Jagex moderator's word on the Trapdoor page); the 3 ticks a
 * pick takes; the cell door at the trapdoor's rates and XP, with no numbing; Agility's share of the blows (its level in
 * hundredths); the even chance of the surface or the cell; the tiles the player lands on. Left out: the trapdoor's
 * occasional 1 XP on a fail (the Wiki gives no rate), Talk-to on the guards, Johanhus, Jimmy and the seated members
 * (quest talk), the archers and mages (quest-only), Sigmund, the storerooms (step 4b).
 */
object HamHideout {
    const val TRAPDOOR = 5492
    const val LADDER = 5493
    const val CELL_DOOR = 5501
    const val LOCKPICK = 1523

    /** ham_thief: 1 shows the trapdoor open. */
    const val TRAPDOOR_VARBIT = 235

    /** The trapdoor's options by what it shows: shut, op1 Open and op5 Pick-Lock; open, op1 Climb-down and op2 Close. */
    const val OP_OPEN_OR_CLIMB = 1
    const val OP_CLOSE = 2
    const val OP_PICK = 5

    /** Beside the trapdoor (3166, 3252), south-west of it; and the foot of the ladder (3149, 9653), south of it. */
    val SURFACE = Triple(3165, 3251, 0)
    val INSIDE = Triple(3149, 9652, 0)

    /** Jimmy's cell: its door on the west edge of 3183, 9611; inside, and the corridor just west of the door. */
    val CELL_X = 3183..3187
    val CELL_Z = 9607..9612
    val CELL = Triple(3184, 9611, 0)
    val OUTSIDE_CELL = Triple(3182, 9611, 0)

    /** The H.A.M. clothing: shirt, robe, hood, cloak, logo, gloves and boots. */
    val CLOTHING = listOf(4298, 4300, 4302, 4304, 4306, 4308, 4310)

    const val PICK_XP_TENTHS = 38
    const val PICK_TICKS = 3
    const val CONCUSSIONS_OUT = 3
    const val CLOTHING_PERCENT = 4

    /** [Player.charges] names: the trapdoor picked open, and concussions since coming in. */
    const val OPEN = "ham_trapdoor_open"
    const val CONCUSSIONS = "ham_concussions"

    /** A Pick-lock's chance in hundredths at Thieving [level]: 4 to 98, or 24 to 99 [withLockpick]. */
    fun pickChance(level: Int, withLockpick: Boolean): Int {
        val (low, high) = if (withLockpick) 24 to 99 else 4 to 98
        val at = (level.coerceIn(1, 99) - 1) / 98.0
        return (low + (high - low) * at).toInt()
    }

    /** The chance in hundredths that a third concussion throws the player out: 4 less for each H.A.M. piece worn. */
    fun ejectChance(pieces: Int): Int = 100 - CLOTHING_PERCENT * pieces.coerceIn(0, CLOTHING.size)

    fun piecesWorn(player: Player): Int = CLOTHING.count { player.equipment.count(it) > 0 }

    fun inCell(player: Player) = player.level == 0 && player.x in CELL_X && player.z in CELL_Z

    fun trapdoorOpen(player: Player) = player.charges[OPEN] != 0

    internal fun setTrapdoor(player: Player, open: Boolean) {
        player.charges[OPEN] = if (open) 1 else 0
        if (player.vars.types.varbit(TRAPDOOR_VARBIT) != null) player.vars.setVarbit(TRAPDOOR_VARBIT, if (open) 1 else 0)
    }

    /**
     * [player], caught pickpocketing a member: Agility may take the blow (no concussion); otherwise it counts, and the
     * third throws them out, unless their clothing keeps them in this time.
     */
    fun caught(world: World, player: Player) {
        if (world.random.nextInt(100) < player.skills.currentLevel(Skill.AGILITY)) {
            world.message(player, HamHideoutLines.AVOIDED)
            return
        }
        world.message(player, HamHideoutLines.CONCUSSED)
        val concussions = player.charges[CONCUSSIONS] + 1
        player.charges[CONCUSSIONS] = concussions
        if (concussions < CONCUSSIONS_OUT) return
        if (world.random.nextInt(100) >= ejectChance(piecesWorn(player))) return
        world.message(player, HamHideoutLines.BUNDLED_OUT)
        player.charges[CONCUSSIONS] = 0
        val (x, z, level) = if (world.random.nextInt(2) == 0) SURFACE else CELL
        world.teleport(player, x, z, level)
    }

    internal suspend fun ScriptContext.pickTrapdoor(world: World) {
        arriveDelay()
        if (trapdoorOpen(player)) return
        if (pick(world, numbs = true)) setTrapdoor(player, true)
    }

    internal suspend fun ScriptContext.pickCell(world: World) {
        arriveDelay()
        if (!inCell(player)) return world.message(player, HamHideoutLines.CELL_LOCKED)
        if (pick(world, numbs = false)) world.teleport(player, OUTSIDE_CELL.first, OUTSIDE_CELL.second, OUTSIDE_CELL.third)
    }

    /** One Pick-lock try: true when it works. A fail [numbs] the fingers, Thieving down 1. */
    private suspend fun ScriptContext.pick(world: World, numbs: Boolean): Boolean {
        world.message(player, HamHideoutLines.PICK)
        delay(PICK_TICKS)
        val thieving = player.skills.currentLevel(Skill.THIEVING)
        if (world.random.nextInt(100) < pickChance(thieving, player.inventory.count(LOCKPICK) > 0)) {
            player.skills.addTenths(Skill.THIEVING, PICK_XP_TENTHS)
            world.message(player, HamHideoutLines.PICK_DONE)
            return true
        }
        world.message(player, HamHideoutLines.PICK_FAIL)
        if (numbs) player.skills.setCurrentLevel(Skill.THIEVING, (thieving - 1).coerceAtLeast(0))
        return false
    }
}

fun registerHamHideout(world: World) = with(HamHideout) {
    world.handlers.onLoc(TRAPDOOR, OP_OPEN_OR_CLIMB) {
        arriveDelay()
        if (!trapdoorOpen(player)) return@onLoc world.message(player, HamHideoutLines.TRAPDOOR_LOCKED)
        player.anim(Climbing.ANIM_PICKUP_FLOOR)
        delay(1)
        player.charges[CONCUSSIONS] = 0
        world.teleport(player, INSIDE.first, INSIDE.second, INSIDE.third)
    }
    world.handlers.onLoc(TRAPDOOR, OP_CLOSE) {
        arriveDelay()
        if (trapdoorOpen(player)) setTrapdoor(player, false)
    }
    world.handlers.onLoc(TRAPDOOR, OP_PICK) { pickTrapdoor(world) }
    world.handlers.onLoc(LADDER, 1) {
        arriveDelay()
        player.anim(Climbing.ANIM_REACH_FOR_LADDER)
        delay(1)
        player.charges[CONCUSSIONS] = 0
        world.teleport(player, SURFACE.first, SURFACE.second, SURFACE.third)
    }
    world.handlers.onLoc(CELL_DOOR, 1) { world.message(player, HamHideoutLines.CELL_LOCKED) }
    world.handlers.onLoc(CELL_DOOR, OP_PICK) { pickCell(world) }
}
