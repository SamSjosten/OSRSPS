package osrsps.game.content

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.Op
import osrsps.game.script.ScriptContext
import osrsps.game.skill.SuccessRoll

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
 * - Jimmy the Chisel's cell ([CELL_DOOR], the Door (H.A.M. Hideout Jail) page): Open says it is locked; Pick-lock from
 *   inside keeps trying until it works ([cellChance]: 4% to 99% at Thieving 1 to 99 on the first try, or 24% to 118%
 *   with a lockpick, rising in four equal steps over the tries to 27% to 123%, or 47% to 141%; starting again resets
 *   it), each fail 0.5 XP, and lets the player out for 3.8 XP.
 * - Pickpocketing H.A.M. members ([osrsps.game.skill.Pickpocketing]): each blow that lands is a concussion ([caught]);
 *   Agility takes some blows instead, which don't count ([avoids]: 1/256 at Agility 1 to 255/256 at 99, the Wiki's
 *   chart). The third concussion bundles the player out, unless their H.A.M. clothing keeps them in (each piece 4% less
 *   likely to be thrown out); out is the surface by the trapdoor or Jimmy's cell. Leaving or being thrown out starts
 *   the count again.
 * - A pick takes 9 ticks, the Wiki's Tick manipulation page's normal trapdoor pick.
 *
 * Guessed: the messages but the three the H.A.M. Member page quotes; the trapdoor's chance moving linearly with Thieving
 * between the Wiki's two ends, with none rising on repeated fails (a Jagex moderator's word on the Trapdoor page); the
 * cell door's chances moving linearly with Thieving between the Wiki's ends, its tries taking the trapdoor's 9 ticks,
 * and no numbing there; the even chance of the surface or the cell; the tiles the player lands on. Left out: the
 * trapdoor's occasional 1 XP on a fail (the Wiki gives no rate), the 4-tick manipulated pick, Talk-to on the guards,
 * Johanhus, Jimmy and the seated members (quest talk), the archers and mages (quest-only), Sigmund, the storerooms
 * (step 4b).
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
    const val CELL_FAIL_XP_TENTHS = 5

    /** A pick takes 9 ticks (the Wiki's Tick manipulation page, the trapdoor's; the cell door's guessed the same). */
    const val PICK_TICKS = 9

    /** The cell door's chance rises over this many fails, in equal steps. */
    const val CELL_STEPS = 4
    const val CONCUSSIONS_OUT = 3
    const val CLOTHING_PERCENT = 4

    /** [Player.charges] names: the trapdoor picked open, and concussions since coming in. */
    const val OPEN = "ham_trapdoor_open"
    const val CONCUSSIONS = "ham_concussions"

    /** [Player.charges] names for a pick in progress: the tick this try ends, and the cell door's fails so far. */
    const val PICK_DUE = "ham_pick_due"
    const val CELL_FAILS = "ham_cell_fails"

    /** A Pick-lock's chance in hundredths at Thieving [level]: 4 to 98, or 24 to 99 [withLockpick]. */
    fun pickChance(level: Int, withLockpick: Boolean): Int {
        val (low, high) = if (withLockpick) 24 to 99 else 4 to 98
        val at = (level.coerceIn(1, 99) - 1) / 98.0
        return (low + (high - low) * at).toInt()
    }

    /**
     * The cell door's chance in hundredths at Thieving [level] after [fails] failed tries: 4 to 99 first, rising to 27
     * to 123, or 24 to 118 rising to 47 to 141 [withLockpick]. 100 or more always works.
     */
    fun cellChance(level: Int, withLockpick: Boolean, fails: Int): Int {
        val (first, last) = if (withLockpick) (24 to 118) to (47 to 141) else (4 to 99) to (27 to 123)
        val at = (level.coerceIn(1, 99) - 1) / 98.0
        val start = first.first + (first.second - first.first) * at
        val end = last.first + (last.second - last.first) * at
        return (start + (end - start) * fails.coerceIn(0, CELL_STEPS) / CELL_STEPS).toInt()
    }

    /** Agility's chance to take a blow, as [SuccessRoll] ends: 1/256 at level 1 to 255/256 at 99 (the Wiki's chart). */
    const val AVOID_LOW = 0
    const val AVOID_HIGH = 254

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
        if (SuccessRoll.roll(world.random, AVOID_LOW, AVOID_HIGH, player.skills.currentLevel(Skill.AGILITY))) {
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

    /**
     * One Pick-Lock try on the trapdoor; a fail numbs the fingers, Thieving down 1. Like the cell door's, the try ticks
     * on through [World.repeatOp], so a click or a walk abandons it.
     */
    internal suspend fun ScriptContext.pickTrapdoor(world: World, op: Op) {
        if (!op.repeat) {
            arriveDelay()
            if (trapdoorOpen(player)) return
            world.message(player, HamHideoutLines.PICK)
            startTry()
        }
        if (!waited(world, op)) return
        val thieving = player.skills.currentLevel(Skill.THIEVING)
        if (world.random.nextInt(100) < pickChance(thieving, hasLockpick(player))) {
            picked(world)
            setTrapdoor(player, true)
            return
        }
        world.message(player, HamHideoutLines.PICK_FAIL)
        player.skills.setCurrentLevel(Skill.THIEVING, (thieving - 1).coerceAtLeast(0))
    }

    /** Starts a try: it ends [PICK_TICKS] ticks from now. */
    private fun ScriptContext.startTry() {
        player.charges[PICK_DUE] = (tick + PICK_TICKS).toInt()
    }

    /** True once this try's ticks are up; otherwise runs [op] again next tick and is false. */
    private fun ScriptContext.waited(world: World, op: Op): Boolean {
        if (tick >= player.charges[PICK_DUE]) return true
        world.repeatOp(player, op)
        return false
    }

    /**
     * Pick-lock on the cell door from inside: tries until it works, each fail raising the next try's chance. The op runs
     * again each tick ([World.repeatOp], as a gathering action does) rather than holding the player in a delay, so a
     * click or a walk stops it, and clicking again starts the count afresh.
     */
    internal suspend fun ScriptContext.pickCell(world: World, op: Op) {
        if (!op.repeat) {
            arriveDelay()
            if (!inCell(player)) return world.message(player, HamHideoutLines.CELL_LOCKED)
            world.message(player, HamHideoutLines.PICK)
            player.charges[CELL_FAILS] = 0
            startTry()
        }
        if (!waited(world, op)) return
        val fails = player.charges[CELL_FAILS]
        val chance = cellChance(player.skills.currentLevel(Skill.THIEVING), hasLockpick(player), fails)
        if (world.random.nextInt(100) < chance) {
            player.charges[CELL_FAILS] = 0
            picked(world)
            world.teleport(player, OUTSIDE_CELL.first, OUTSIDE_CELL.second, OUTSIDE_CELL.third)
            return
        }
        world.message(player, HamHideoutLines.PICK_FAIL)
        player.skills.addTenths(Skill.THIEVING, CELL_FAIL_XP_TENTHS)
        player.charges[CELL_FAILS] = fails + 1
        startTry()
        world.repeatOp(player, op)
    }

    private fun hasLockpick(player: Player) = player.inventory.count(LOCKPICK) > 0

    private fun ScriptContext.picked(world: World) {
        player.skills.addTenths(Skill.THIEVING, PICK_XP_TENTHS)
        world.message(player, HamHideoutLines.PICK_DONE)
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
    world.handlers.onLoc(TRAPDOOR, OP_PICK) { op -> pickTrapdoor(world, op) }
    world.handlers.onLoc(LADDER, 1) {
        arriveDelay()
        player.anim(Climbing.ANIM_REACH_FOR_LADDER)
        delay(1)
        player.charges[CONCUSSIONS] = 0
        world.teleport(player, SURFACE.first, SURFACE.second, SURFACE.third)
    }
    world.handlers.onLoc(CELL_DOOR, 1) { world.message(player, HamHideoutLines.CELL_LOCKED) }
    world.handlers.onLoc(CELL_DOOR, OP_PICK) { op -> pickCell(world, op) }
}
