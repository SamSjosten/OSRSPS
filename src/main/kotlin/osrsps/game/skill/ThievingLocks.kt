package osrsps.game.skill

import osrsps.game.Inventory
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.arriveDelay
import osrsps.game.content.message
import osrsps.game.dialogue.Lines
import osrsps.game.loc.Doors
import osrsps.game.script.Op
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/** The words of Thieving's chests and pick-lock doors, in the dialogue file. */
object ThievingLockLines : Lines("thieving_lock", "Thieving 2 step T2: the trapped chests and pick-lock doors") {
    val SEARCH = line("search", "Search for traps on a chest: the first message")
    val NOTHING = line("nothing", "Search for traps below the chest's level")
    val TRAP_FOUND = line("trap_found", "Search for traps: the trap found (the live line ends in a comma and a space)")
    val TRAP_DISABLED = line("trap_disabled", "Search for traps: the trap disabled")
    val CHEST_OPENED = line("chest_opened", "a chest opened, searched or picked")
    val TREASURE = line("treasure", "a chest's loot given")
    val SECOND_TRAP = line("second_trap", "the blood rune and Ardougne Castle chests' teleport after the loot")
    val CHEST_TRAP = line("chest_trap", "Open on a trapped chest without searching: the trap goes off")
    val CHEST_LOCKED = line("chest_locked", "Open on the steel arrowtip chest")
    val CHEST_LEVEL = line("chest_level", "Search for traps on the steel arrowtip chest below level 47: the message box")
    val NEED_LOCKPICK = line("need_lockpick", "a lock that needs a lockpick, with none in the backpack")
    val LOOTED = line("looted", "any option on a looted chest")
    val NO_ROOM = line("no_room", "a chest whose loot wouldn't fit (GUESSED: 2004Scape's gives it regardless)")
    val PICK = line("pick", "Pick-lock on a locked door, or on the steel arrowtip chest: the attempt")
    val LOCK_TRAP = line("lock_trap", "a pick-lock door's trap goes off")
    val DOOR_LEVEL = line("door_level", "Pick-lock below the door's level: the message box, with the level", "level")
    val PICK_FAIL = line("pick_fail", "a failed Pick-lock")
    val PICK_DONE = line("pick_done", "a Pick-lock that works")
    val DOOR_LOCKED = line("door_locked", "Open on a locked door from outside")
    val ALREADY_UNLOCKED = line("already_unlocked", "Pick-lock on a locked door from inside")
    val THROUGH = line("through", "going through a locked door from inside, by Open or Pick-lock")
}

/** One item a chest always gives: [count] of [obj]. */
class ChestLoot(val obj: Int, val count: Int)

/**
 * A chest to search for traps (or, [lockpick], to pick): [level], XP, its [loot], the [empty] loc it shows for
 * [respawnTicks] after, the damage an Open does ([trapDamage], from the player's current and full Hitpoints), and where
 * a second trap sends the looter ([teleport]: x, z, level), if anywhere.
 */
class TrapChest(
    val loc: Int,
    val level: Int,
    val xpTenths: Int,
    val respawnTicks: Int,
    val loot: List<ChestLoot>,
    val empty: Int = ThievingLocks.EMPTY_TRAP_CHEST,
    val lockpick: Boolean = false,
    val trapDamage: (current: Int, full: Int) -> Int = ThievingLocks::natureTrapDamage,
    val teleport: Triple<Int, Int, Int>? = null,
)

/**
 * A door locked from outside: [level], XP, the pick's chart ([low] to [high] out of 256), and the trap roll before it, if
 * any ([trapLow] to [trapHigh]), whose blow [trapDamage] takes from the current and full Hitpoints.
 */
class LockedDoor(
    val loc: Int,
    val level: Int,
    val xpTenths: Int,
    val low: Int,
    val high: Int,
    val trapLow: Int = 0,
    val trapHigh: Int = 0,
    val trapDamage: (current: Int, full: Int) -> Int = { _, _ -> 0 },
) {
    val trapped: Boolean get() = trapHigh > 0
}

/**
 * Thieving 2 step T2: the trapped chests and the pick-lock doors, from the OSRS Wiki's Chest (10 coins), Chest (nature
 * runes), Chest (50 coins), Chest (steel arrowtips), Chest (blood runes), Chest (Ardougne Castle), Door (East Ardougne),
 * Door (Ross' House), Door (Chaos Druid Tower) and Door (Ardougne Castle) pages (read 2026-11-10), with 2004Scape's
 * thieving scripts (LostCityRS/Content, skill_thieving) for the steps and the words. The locs are the cache's, where the
 * cache map puts them; only their options are new.
 *
 * Chests: Search for traps finds the trap, disables it, opens the chest and gives the loot, two ticks apart, and the
 * chest stands open 4 ticks then empty for the Wiki's time (7, 15, 50, 90, 135 and 500 seconds). Below the level it
 * finds nothing. Open instead sets the trap off: `floor(HP * 12 / 100) + 3` from the current Hitpoints (the nature rune
 * chest's page; the 10 and 50 coin and Ardougne Castle chests' "substantial" and "high" damage are GUESSED the same), or
 * the blood rune chest's `(9 * HP + 225) / 50`. The blood rune chest sends the looter to East Ardougne (2584, 3337) and
 * the castle's to Witchaven (2696, 3281). The steel arrowtip chest is locked: Search for traps, or a lockpick used on
 * it, picks it with a lockpick held, every time.
 *
 * Doors: Open from outside (the door's own tile's side of its wall, as 2004Scape has it) says it is locked; from inside it
 * lets the player through,
 * as Pick-lock from inside does too. Pick-lock from outside: the Chaos Druid Tower's, Ardougne Castle's and Ross' doors
 * first roll for a trap (2004Scape's charts; Ross' 32 to 250 gives the Wiki's 77% at 13), whose blow is 6% of full
 * Hitpoints, 10% of full, or 10% of current (none at 10 or less), and a sprung trap ends the try. Then the level, then
 * the pick's chart (the Wiki's 32 to 250 for East Ardougne's doors; 2004Scape's 8 to 75 for the tower's and 4 to 40 for
 * the castle's and Ross'), and a pick that works lets the player through with the XP. Going through moves the player
 * across and leaves the door shut, so nobody slips in behind (exploit sweep round 25, R25B-1; the cache pairs these
 * doors with no open type).
 *
 * Guessed too: the room check before a chest's loot. Left out: the Dorgesh-Kaan and Rogues' Castle chests, the doors
 * that need a lockpick (`toollock`), the Ardougne sewer gates, the Ardougne diary, the gas random event.
 */
object ThievingLocks {
    const val LOCKPICK = 1523
    const val COINS = 995
    const val NATURE_RUNE = 561
    const val BLOOD_RUNE = 565
    const val STEEL_ARROWTIPS = 41
    const val RAW_SHARK = 383
    const val ADAMANTITE_ORE = 449
    const val UNCUT_SAPPHIRE = 1623

    const val CHEST_10_COINS = 11735
    const val CHEST_NATURE = 11736
    const val CHEST_50_COINS = 11737
    const val CHEST_BLOOD = 11738
    const val CHEST_ARDOUGNE_CASTLE = 11739
    const val EMPTY_TRAP_CHEST = 11740
    const val EMPTY_PICK_CHEST = 11741
    const val CHEST_ARROWTIPS = 11742
    const val OPEN_CHEST = 11743

    const val DOOR_EAST_ARDOUGNE_NORTH = 11719
    const val DOOR_EAST_ARDOUGNE_SOUTH = 11720
    const val DOOR_CHAOS_DRUID_TOWER = 11723
    const val DOOR_ARDOUGNE_CASTLE = 11724
    const val DOOR_ROSS = 40178

    /** `inactivepoordoor`: the door shown open while a player goes through (GUESSED type). */

    const val OP_OPEN = 1
    const val OP_SECOND = 2

    const val ANIM_SEARCH = 537
    const val ANIM_OPEN = 536

    /** How long a looted chest stands open before it shows empty. */
    const val OPEN_TICKS = 4

    /** The nature rune chest's trap: `floor(current * 12 / 100) + 3`. */
    fun natureTrapDamage(current: Int, full: Int): Int = current * 12 / 100 + 3

    /** The blood rune chest's trap: `(9 * current + 225) / 50`. */
    fun bloodTrapDamage(current: Int, full: Int): Int = (9 * current + 225) / 50

    val CHESTS: Map<Int, TrapChest> = listOf(
        TrapChest(CHEST_10_COINS, 13, 78, 12, listOf(ChestLoot(COINS, 10))),
        TrapChest(CHEST_NATURE, 28, 250, 25, listOf(ChestLoot(NATURE_RUNE, 1), ChestLoot(COINS, 3))),
        TrapChest(CHEST_50_COINS, 43, 1250, 83, listOf(ChestLoot(COINS, 50))),
        TrapChest(
            CHEST_ARROWTIPS, 47, 1500, 150, listOf(ChestLoot(STEEL_ARROWTIPS, 5), ChestLoot(COINS, 20)),
            empty = EMPTY_PICK_CHEST, lockpick = true,
        ),
        TrapChest(
            CHEST_BLOOD, 59, 2500, 225, listOf(ChestLoot(BLOOD_RUNE, 2), ChestLoot(COINS, 500)),
            trapDamage = ::bloodTrapDamage, teleport = Triple(2584, 3337, 0),
        ),
        TrapChest(
            CHEST_ARDOUGNE_CASTLE, 72, 5000, 833,
            listOf(ChestLoot(COINS, 1000), ChestLoot(RAW_SHARK, 1), ChestLoot(ADAMANTITE_ORE, 1), ChestLoot(UNCUT_SAPPHIRE, 1)),
            teleport = Triple(2696, 3281, 0),
        ),
    ).associateBy { it.loc }

    val DOORS: Map<Int, LockedDoor> = listOf(
        LockedDoor(DOOR_EAST_ARDOUGNE_NORTH, 1, 38, 32, 250),
        LockedDoor(DOOR_EAST_ARDOUGNE_SOUTH, 16, 150, 32, 250),
        LockedDoor(DOOR_ROSS, 13, 150, 4, 40, 32, 250) { current, _ -> if (current <= 10) 0 else current / 10 },
        LockedDoor(DOOR_CHAOS_DRUID_TOWER, 46, 375, 8, 75, 220, 280) { _, full -> full * 6 / 100 },
        LockedDoor(DOOR_ARDOUGNE_CASTLE, 61, 500, 4, 40, 200, 270) { _, full -> full * 10 / 100 },
    ).associateBy { it.loc }

    /**
     * True when a player at [x], [z] is in line with [door]'s own tile across its wall: outside, where it is locked
     * (2004Scape's check_axis_locactive, whose "entering" is the locked side).
     */
    fun outside(door: ZoneLoc, x: Int, z: Int): Boolean = if (door.angle % 2 == 1) z == door.z else x == door.x

    fun inside(door: ZoneLoc, x: Int, z: Int): Boolean = !outside(door, x, z)

    /** The tile through [door] from [x], [z]: the tile past its wall from outside, its own tile from inside. */
    fun through(door: ZoneLoc, x: Int, z: Int): Pair<Int, Int> {
        if (inside(door, x, z)) return door.x to door.z
        val (dx, dz) = Doors.SWING[door.angle]
        return door.x + dx to door.z + dz
    }

    /** True when [chest]'s loot fits: a free slot for each item not already a stack held, and room in each stack. */
    fun fits(player: Player, chest: TrapChest, stackable: (Int) -> Boolean): Boolean {
        var slots = 0
        for (item in chest.loot) {
            if (!stackable(item.obj)) {
                slots++
                continue
            }
            val held = player.inventory.count(item.obj)
            if (held == 0L) slots++
            if (held + item.count > Inventory.MAX_STACK) return false
        }
        return player.inventory.freeSlots >= slots
    }

    fun register(world: World) {
        for (chest in CHESTS.values) {
            if (chest.lockpick) {
                world.handlers.onLoc(chest.loc, OP_OPEN) { arriveDelay(); world.message(player, ThievingLockLines.CHEST_LOCKED) }
                world.handlers.onLoc(chest.loc, OP_SECOND) { op -> pickChest(world, op, chest) }
                world.handlers.onObjOnLoc(chest.loc, LOCKPICK) { op -> pickChest(world, op, chest) }
            } else {
                world.handlers.onLoc(chest.loc, OP_OPEN) { op -> openTrapped(world, op, chest) }
                world.handlers.onLoc(chest.loc, OP_SECOND) { op -> searchChest(world, op, chest) }
            }
        }
        for (empty in listOf(EMPTY_TRAP_CHEST, EMPTY_PICK_CHEST)) {
            for (option in listOf(OP_OPEN, OP_SECOND)) {
                world.handlers.onLoc(empty, option) { arriveDelay(); world.message(player, ThievingLockLines.LOOTED) }
            }
        }
        for (door in DOORS.values) {
            world.handlers.onLoc(door.loc, OP_OPEN) { op -> openDoor(world, op) }
            world.handlers.onLoc(door.loc, OP_SECOND) { op -> pickDoor(world, op, door) }
        }
    }

    private suspend fun ScriptContext.openTrapped(world: World, op: Op, chest: TrapChest) {
        val loc = op.loc ?: return
        arriveDelay()
        // Another thief may have looted it, leaving it open, while this one walked up.
        if (!world.locs.exists(loc)) return
        world.message(player, ThievingLockLines.CHEST_TRAP)
        player.anim(ANIM_SEARCH)
        delay(1)
        val damage = chest.trapDamage(player.hitpoints, player.skills.baseLevel(Skill.HITPOINTS))
        world.meleeCombat.hitPlayer(player, null, damage, tick)
    }

    private suspend fun ScriptContext.searchChest(world: World, op: Op, chest: TrapChest) {
        val loc = op.loc ?: return
        arriveDelay()
        world.message(player, ThievingLockLines.SEARCH)
        player.anim(ANIM_SEARCH)
        if (player.skills.currentLevel(Skill.THIEVING) < chest.level) return world.message(player, ThievingLockLines.NOTHING)
        if (!fits(player, chest) { world.objTypes[it]?.stackable == true }) return world.message(player, ThievingLockLines.NO_ROOM)
        world.message(player, ThievingLockLines.TRAP_FOUND)
        delay(2)
        world.message(player, ThievingLockLines.TRAP_DISABLED)
        delay(2)
        world.message(player, ThievingLockLines.CHEST_OPENED)
        delay(2)
        loot(world, loc, chest)
    }

    private suspend fun ScriptContext.pickChest(world: World, op: Op, chest: TrapChest) {
        val loc = op.loc ?: return
        arriveDelay()
        world.message(player, ThievingLockLines.PICK)
        if (player.skills.currentLevel(Skill.THIEVING) < chest.level) {
            world.dialogues.start(this) { mesbox(ThievingLockLines.CHEST_LEVEL) }
            return
        }
        if (player.inventory.count(LOCKPICK) == 0L) return world.message(player, ThievingLockLines.NEED_LOCKPICK)
        if (!fits(player, chest) { world.objTypes[it]?.stackable == true }) return world.message(player, ThievingLockLines.NO_ROOM)
        world.message(player, ThievingLockLines.PICK_DONE)
        delay(2)
        world.message(player, ThievingLockLines.CHEST_OPENED)
        delay(2)
        loot(world, loc, chest)
    }

    /** Gives [chest]'s loot and XP, unless another thief emptied it first; then open, then empty for its time. */
    private suspend fun ScriptContext.loot(world: World, loc: ZoneLoc, chest: TrapChest) {
        if (!world.locs.exists(loc)) return world.message(player, ThievingLockLines.LOOTED)
        // The room may have gone in the ticks the search took.
        if (!fits(player, chest) { world.objTypes[it]?.stackable == true }) return world.message(player, ThievingLockLines.NO_ROOM)
        // The world turns it empty and relocks it, so the stages go on even if the looter dies or logs out.
        world.locs.replaceInStages(loc, OPEN_CHEST, OPEN_TICKS, chest.empty, chest.respawnTicks) ?: return
        world.message(player, ThievingLockLines.TREASURE)
        player.anim(ANIM_OPEN)
        for (item in chest.loot) {
            val type = world.objTypes[item.obj] ?: error("No obj type ${item.obj} for chest ${chest.loc}")
            player.inventory.add(type, item.count)
        }
        player.skills.addTenths(Skill.THIEVING, chest.xpTenths)
        val (x, z, level) = chest.teleport ?: return
        delay(OPEN_TICKS)
        world.message(player, ThievingLockLines.SECOND_TRAP)
        world.teleport(player, x, z, level)
    }

    private suspend fun ScriptContext.openDoor(world: World, op: Op) {
        val door = op.loc ?: return
        arriveDelay()
        // Another thief may have gone through, swinging the door open, while this one walked up.
        if (!world.locs.exists(door)) return
        if (!inside(door, player.x, player.z)) return world.message(player, ThievingLockLines.DOOR_LOCKED)
        world.message(player, ThievingLockLines.THROUGH)
        goThrough(world, door)
    }

    private suspend fun ScriptContext.pickDoor(world: World, op: Op, data: LockedDoor) {
        val door = op.loc ?: return
        arriveDelay()
        // Another thief may have gone through, swinging the door open, while this one walked up.
        if (!world.locs.exists(door)) return
        if (inside(door, player.x, player.z)) {
            world.message(player, ThievingLockLines.ALREADY_UNLOCKED)
            delay(1)
            world.message(player, ThievingLockLines.THROUGH)
            return goThrough(world, door)
        }
        world.message(player, ThievingLockLines.PICK)
        val thieving = player.skills.currentLevel(Skill.THIEVING)
        if (data.trapped && !SuccessRoll.roll(world.random, data.trapLow, data.trapHigh, thieving)) {
            world.message(player, ThievingLockLines.LOCK_TRAP)
            val damage = data.trapDamage(player.hitpoints, player.skills.baseLevel(Skill.HITPOINTS))
            if (damage > 0) world.meleeCombat.hitPlayer(player, null, damage, tick)
            return
        }
        if (thieving < data.level) {
            world.dialogues.start(this) { mesbox(ThievingLockLines.DOOR_LEVEL, "level" to data.level) }
            return
        }
        if (!SuccessRoll.roll(world.random, data.low, data.high, thieving)) return world.message(player, ThievingLockLines.PICK_FAIL)
        world.message(player, ThievingLockLines.PICK_DONE)
        player.skills.addTenths(Skill.THIEVING, data.xpTenths)
        goThrough(world, door)
    }

    /**
     * Puts the player on [door]'s far side and leaves it shut. 2004Scape's open_and_close_door swings it open for a
     * moment, which would let anyone walk through behind them, whatever their level (R25B-1).
     */
    private suspend fun ScriptContext.goThrough(world: World, door: ZoneLoc) {
        if (!world.locs.exists(door)) return
        val (x, z) = through(door, player.x, player.z)
        world.teleport(player, x, z, door.level)
    }
}
