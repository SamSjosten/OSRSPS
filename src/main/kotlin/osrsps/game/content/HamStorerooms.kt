package osrsps.game.content

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.script.Op
import osrsps.game.script.ScriptContext
import osrsps.game.skill.PocketLoot
import osrsps.game.skill.SuccessRoll
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile
import java.util.Collections
import java.util.WeakHashMap

/** The H.A.M. Storerooms' words, in the dialogue file. */
object HamStoreroomsLines : Lines("ham_storerooms", "OSRS Wiki, H.A.M. Storerooms, Door (H.A.M. Storerooms), Small chest and Steel key (H.A.M.), read 2026-11-10") {
    val DOOR_LOCKED = line("door_locked", "Open on a storeroom's locked door from the corridor; words unsourced")
    val DOOR_LEVEL = line("door_level", "Pick-lock on a storeroom door below the Thieving level; words unsourced", "level")
    val DOOR_PICK = line("door_pick", "Pick-lock on a storeroom door: the attempt; words unsourced")
    val DOOR_PICK_FAIL = line("door_pick_fail", "a failed Pick-lock on a storeroom door; words unsourced")
    val DOOR_PICK_DONE = line("door_pick_done", "a Pick-lock on a storeroom door that works; words unsourced")
    val CHEST_LOCKED = line("chest_locked", "Open on a small chest without its key; words unsourced")
    val KEY_BREAKS = line("key_breaks", "a small chest opened: the key used up (the Steel key (H.A.M.) page's message, each key's own colour)", "key")
    val CHEST_NO_ROOM = line("chest_no_room", "Open on a small chest with too little room for what it may hold; words unsourced")
}

/**
 * The H.A.M. Storerooms (Misthalin piece 7 step 4b), below the H.A.M. Hideout, from the OSRS Wiki's H.A.M. Storerooms,
 * Hidden trapdoor, Door (H.A.M. Storerooms), Small chest, Guard (H.A.M. Storerooms) and Steel key (H.A.M.) pages (read
 * 2026-11-10) and the rev 242 cache. Every account counts as having done Death to the Dorgeshuun ([osrsps.game.Quests]),
 * so the way down is open:
 *
 * - The hidden trapdoor south of the Deacon's podium ([TRAPDOOR], `dttd_ham_trapdoor`, varbit
 *   `dttd_ham_trapdoor_state` [TRAPDOOR_VARBIT] 2: open, Climb-down) leads to the foot of the storerooms' ladder
 *   ([LADDER]), which climbs back up beside it.
 * - Four storerooms with locked doors ([DOOR], `dttd_ham_door`): Open from the corridor says it is locked, Pick-lock
 *   needs Thieving 23 and works as [SuccessRoll] 10 to 254, or 60 to 300 with a lockpick in the backpack (the Wiki's
 *   chart), for 3.8 XP, or 0.5 XP on a fail, and lets the player in. From inside, Open (or Pick-lock) lets them out.
 * - The south-west room's two wall cracks ([CRACK]) lead in and out without a lock.
 * - The large room to the north, through its unlocked double door ([LARGE_DOOR_LEFT], [LARGE_DOOR_RIGHT]).
 * - The small chests ([CHESTS]): each opens with its own key, used up ([KEY_BREAKS]), and rolls the Wiki's table
 *   ([CHEST_LOOT], 20 outcomes out of 150, some giving several pieces together).
 * - The five guards ([GUARDS], `dttd_ham_guard_N_postquest`, level 22) patrol the corridors. A guard who sees a player
 *   start picking a door ([Aggression.sees], within [SIGHT_TILES]) attacks them, unless a failed pickpocket has sent it
 *   back to its post ([settle]): then it stops patrolling and no longer attacks. Pickpocketing them
 *   ([osrsps.game.skill.Pickpocketing]): level 20, 22.2 XP, 1 to 3 damage, the Wiki's table ([GUARD_LOOT]) with a key
 *   of each colour at 1/10 each and the H.A.M. members' coin pouch. A catch doesn't throw the player out.
 *
 * Guessed: the messages but the key's; the tiles the ladder and trapdoor land on; a door pick's 9 ticks (the hideout
 * trapdoor's) and one try a click; Open from inside letting the player out with no pick; the guards' patrols (corridor
 * to corridor, [GUARD_WAIT] ticks at each end), how far they see, and that a settled guard stays settled for as long
 * as the world runs; the guards' pickpocket chance (the H.A.M. members' 135 to 239: the Wiki has no chart yet) and the
 * usual 9-tick stun; 0 coins giving nothing; needing [CHEST_ROOM] free slots to open a chest. Left out: the easy clue
 * (no Treasure Trails yet; counted as nothing, as the members'), the chests' open look, the crack's squeeze animation,
 * Talk-to on the guards (quest talk), aggression when spoken to without the H.A.M. robes.
 */
object HamStorerooms {
    /** `dttd_ham_trapdoor`, the hideout's hidden trapdoor; its varbit's 2 shows `dttd_ham_trapdoor_open`, Climb-down. */
    const val TRAPDOOR = 15766
    const val TRAPDOOR_VARBIT = 2270
    const val TRAPDOOR_OPEN = 2

    /** `dttd_ladder_up_out_of_ham_storerooms`. */
    const val LADDER = 15747

    /** `dttd_ham_door`: op1 Open, op4 Pick-lock; and `poordooropen`, the plain door it shares its models with, open. */
    const val DOOR = 15759
    const val DOOR_OPEN = 1536
    const val OP_OPEN = 1
    const val OP_PICK = 4

    /** `dttd_cave_walls_cracked`, the south-west room's two cracks: op1 Squeeze-through. */
    const val CRACK = 15731

    /** The large room's double door: `poordoor_doubler_inner` (east) and `poordoor_double_inner` (west), and their open forms. */
    const val LARGE_DOOR_LEFT = 13096
    const val LARGE_DOOR_LEFT_OPEN = 13097
    const val LARGE_DOOR_RIGHT = 13094
    const val LARGE_DOOR_RIGHT_OPEN = 13095

    /** Steel, bronze, silver and iron keys (`dttd_key_1` to `dttd_key_4`). */
    const val STEEL_KEY = 8866
    const val BRONZE_KEY = 8867
    const val SILVER_KEY = 8868
    const val IRON_KEY = 8869

    /**
     * Each small chest and the key it takes: `dttd_ham_chest_1` the south-east room's and the large room's two (steel),
     * `_2` the south-west's (bronze), `_3` the north-west's (silver), `_4` the north-east's (iron).
     */
    val CHESTS = mapOf(15722 to STEEL_KEY, 15723 to BRONZE_KEY, 15724 to SILVER_KEY, 15726 to IRON_KEY)

    /** The keys' colours, for [HamStoreroomsLines.KEY_BREAKS]. */
    private val KEY_COLOURS = mapOf(STEEL_KEY to "steel", BRONZE_KEY to "bronze", SILVER_KEY to "silver", IRON_KEY to "iron")

    /** `dttd_ham_guard_1_postquest` to `_5_postquest`. */
    val GUARDS = listOf(4522, 4523, 4524, 4525, 4526)
    const val GUARD_PICKPOCKET = 1

    /** The trapdoor's foot (beside the ladder, east of it) and the hideout tile north of the trapdoor. */
    val BELOW = Triple(2568, 5185, 0)
    val ABOVE = Triple(3166, 9623, 0)

    const val DOOR_LEVEL = 23
    const val DOOR_LOW = 10
    const val DOOR_HIGH = 254
    const val DOOR_LOCKPICK_LOW = 60
    const val DOOR_LOCKPICK_HIGH = 300
    const val DOOR_XP_TENTHS = 38
    const val DOOR_FAIL_XP_TENTHS = 5

    /** A door's Pick-lock takes the hideout trapdoor's [HamHideout.PICK_TICKS]; the door stands open this long. */
    const val PICK_TICKS = HamHideout.PICK_TICKS
    const val DOOR_OPEN_TICKS = 2

    /** How far a guard sees a door being picked, and its wait at each end of its patrol. */
    const val SIGHT_TILES = 8
    const val GUARD_WAIT = 3

    /** The most pieces one chest roll gives, so the room it needs. */
    const val CHEST_ROOM = 4

    const val COINS = 995

    /** [Player.charges] name: the tick a door pick's try ends. */
    const val PICK_DUE = "ham_store_pick_due"

    /**
     * The Wiki's guard table in 5,500ths, which it fills: its x/500 lines as 11x, x/2750 as 2x, a key 550 (1/10). The
     * coin pouch is the H.A.M. members' ([osrsps.game.skill.Pickpocketing.HAM_POUCH]).
     */
    val GUARD_LOOT: List<PocketLoot> by lazy {
        fun guard(obj: Int, weight: Int, max: Int = 1) = PocketLoot(obj, 1, max) { weight }
        listOf(
            guard(BRONZE_KEY, 550), guard(IRON_KEY, 550), guard(SILVER_KEY, 550), guard(STEEL_KEY, 550),
            guard(882, 99, 13), // bronze arrow 1-13, 9/500
            guard(1351, 99), guard(1205, 99), guard(1265, 99), // bronze axe, dagger, pickaxe
            guard(1349, 99), guard(1203, 99), guard(1267, 99), // iron axe, dagger, pickaxe
            guard(1129, 99), // leather body
            guard(4298, 33), guard(4300, 33), guard(4302, 33), guard(4304, 33), guard(4306, 33), guard(4308, 33), guard(4310, 33), // H.A.M. clothing, 3/500
            guard(886, 66, 13), // steel arrow 1-13, 6/500
            guard(1353, 66), guard(1207, 66), guard(1269, 66), // steel axe, dagger, pickaxe
            guard(688, 132), // buttons, 12/500
            guard(osrsps.game.skill.Pickpocketing.HAM_POUCH, 495), // coin pouch, 45/500
            guard(314, 99, 7), // feather 1-7
            guard(946, 66), guard(1511, 99), guard(1733, 66), guard(321, 66), guard(2138, 66), // knife, logs, needle, raw anchovies, raw chicken
            guard(1734, 99, 10), // thread 1-10
            guard(590, 66), guard(1625, 66), guard(453, 66), guard(1739, 99), // tinderbox, uncut opal, coal, cowhide
            guard(697, 132), guard(686, 132), // damaged armour, rusty sword
            guard(199, 36), guard(201, 18), guard(203, 12), // grimy guam, marrentill, tarromin: 18, 9 and 6 /2750
            guard(440, 66), guard(1627, 66), // iron ore, uncut jade
            guard(-1, 66), // the easy clue, 6/500, left out
        )
    }

    /** One chest outcome: [weight] out of 150, and what it gives together, each as an obj id to a count range. */
    class ChestRoll(val weight: Int, val items: List<Pair<Int, IntRange>>)

    private const val SAPPHIRE_NECKLACE = 1656
    private const val SAPPHIRE_AMULET = 1694
    private const val SAPPHIRE_RING = 1637

    private fun roll(weight: Int, vararg objs: Int) = ChestRoll(weight, objs.map { it to 1..1 })

    /** The Wiki's Small chest table, out of 150. */
    val CHEST_LOOT = listOf(
        ChestRoll(75, listOf(COINS to 0..99)),
        roll(6, SAPPHIRE_NECKLACE),
        roll(2, 1601, SAPPHIRE_AMULET, SAPPHIRE_NECKLACE, SAPPHIRE_RING), // diamond and the sapphire set
        roll(3, 1660), // ruby necklace
        roll(5, 1635), // gold ring
        roll(6, SAPPHIRE_AMULET, SAPPHIRE_NECKLACE, SAPPHIRE_RING),
        roll(4, 1639, SAPPHIRE_AMULET, SAPPHIRE_NECKLACE), // emerald ring
        roll(6, SAPPHIRE_AMULET, SAPPHIRE_NECKLACE),
        roll(2, 1662), // diamond necklace
        roll(5, 1692), // gold amulet
        roll(2, 1643, SAPPHIRE_AMULET, SAPPHIRE_NECKLACE), // diamond ring
        roll(5, 1607, SAPPHIRE_AMULET, SAPPHIRE_NECKLACE, SAPPHIRE_RING), // sapphire
        roll(4, 1658), // emerald necklace
        roll(4, 1696, SAPPHIRE_NECKLACE), // emerald amulet
        roll(6, 1654), // gold necklace
        roll(2, 1700, SAPPHIRE_NECKLACE), // diamond amulet
        roll(3, 1603, SAPPHIRE_AMULET, SAPPHIRE_NECKLACE, SAPPHIRE_RING), // ruby
        roll(3, 1641, SAPPHIRE_AMULET, SAPPHIRE_NECKLACE), // ruby ring
        roll(3, 1698, SAPPHIRE_NECKLACE), // ruby amulet
        roll(4, 1605, SAPPHIRE_AMULET, SAPPHIRE_NECKLACE, SAPPHIRE_RING), // emerald
    )
    const val CHEST_SLOTS = 150

    /** Guards a failed pickpocket has sent back to their posts, with the tick each spawned on. */
    private val settled: MutableMap<Npc, Long> = Collections.synchronizedMap(WeakHashMap())

    fun isSettled(guard: Npc): Boolean = settled[guard] == guard.spawnTick

    /** A failed pickpocket on [guard]: it walks back to its post and stops patrolling there. */
    fun settle(guard: Npc) {
        if (guard.type.id !in GUARDS) return
        settled[guard] = guard.spawnTick
        guard.destination = Tile(guard.spawn.x, guard.spawn.z)
        guard.patrolWait = Int.MAX_VALUE
    }

    /** Whether [player] stands on the room's side of [door]: east of a west-edge door, west of an east-edge one. */
    fun inside(door: ZoneLoc, player: Player): Boolean = if (door.angle == 0) player.x >= door.x else player.x <= door.x

    /** The tile on the room's side of [door], and the corridor's. */
    fun insideTile(door: ZoneLoc) = Tile(door.x, door.z)
    fun outsideTile(door: ZoneLoc) = Tile(if (door.angle == 0) door.x - 1 else door.x + 1, door.z)

    /** One chest roll from [world]'s random source. */
    fun rollChest(world: World): List<Pair<Int, Int>> {
        var roll = world.random.nextInt(CHEST_SLOTS)
        for (outcome in CHEST_LOOT) {
            if (roll < outcome.weight) return outcome.items.map { (obj, range) -> obj to world.random.between(range.first, range.last) }
            roll -= outcome.weight
        }
        error("Unreachable")
    }

    /** Guards who see [player] start a pick and aren't settled turn on them. */
    private fun ScriptContext.alertGuards(world: World) {
        for (guard in world.npcs.inPlay) {
            if (guard.type.id !in GUARDS || guard.dead || guard.level != player.level || isSettled(guard)) continue
            if (maxOf(kotlin.math.abs(guard.x - player.x), kotlin.math.abs(guard.z - player.z)) > SIGHT_TILES) continue
            if (guard.target != null || !world.aggression.sees(guard, player)) continue
            world.meleeCombat.aggress(guard, player, tick)
        }
    }

    /** Through [door] from whichever side: it swings open for [DOOR_OPEN_TICKS] and the player walks across. */
    private suspend fun ScriptContext.throughDoor(world: World, door: ZoneLoc) {
        val comingIn = !inside(door, player)
        val from = if (comingIn) outsideTile(door) else insideTile(door)
        val to = if (comingIn) insideTile(door) else outsideTile(door)
        if (player.x != from.x || player.z != from.z) {
            world.teleport(player, from.x, from.z, door.level)
            delay(1)
        }
        if (!world.locs.exists(door)) return
        world.doors.openAs(door, DOOR_OPEN, shutAfter = DOOR_OPEN_TICKS)
        player.waypoints.clear()
        player.waypoints.add(to)
    }

    internal suspend fun ScriptContext.openDoor(world: World, door: ZoneLoc) {
        arriveDelay()
        if (!inside(door, player)) return world.message(player, HamStoreroomsLines.DOOR_LOCKED)
        throughDoor(world, door)
    }

    /**
     * Pick-lock on a storeroom door: from inside it just opens; from the corridor, one [PICK_TICKS] try (through
     * [World.repeatOp], so a click or a walk abandons it), which guards in sight answer by attacking.
     */
    internal suspend fun ScriptContext.pickDoor(world: World, op: Op) {
        val door = op.loc ?: return
        if (!op.repeat) {
            arriveDelay()
            if (inside(door, player)) return throughDoor(world, door)
            if (player.skills.currentLevel(Skill.THIEVING) < DOOR_LEVEL) {
                return world.message(player, HamStoreroomsLines.DOOR_LEVEL, "level" to DOOR_LEVEL)
            }
            world.message(player, HamStoreroomsLines.DOOR_PICK)
            alertGuards(world)
            player.charges[PICK_DUE] = (tick + PICK_TICKS).toInt()
        }
        if (tick < player.charges[PICK_DUE]) return world.repeatOp(player, op)
        val lockpick = player.inventory.count(HamHideout.LOCKPICK) > 0
        val (low, high) = if (lockpick) DOOR_LOCKPICK_LOW to DOOR_LOCKPICK_HIGH else DOOR_LOW to DOOR_HIGH
        if (!SuccessRoll.roll(world.random, low, high, player.skills.currentLevel(Skill.THIEVING))) {
            player.skills.addTenths(Skill.THIEVING, DOOR_FAIL_XP_TENTHS)
            return world.message(player, HamStoreroomsLines.DOOR_PICK_FAIL)
        }
        player.skills.addTenths(Skill.THIEVING, DOOR_XP_TENTHS)
        world.message(player, HamStoreroomsLines.DOOR_PICK_DONE)
        throughDoor(world, door)
    }

    /** Squeeze-through a crack: across its wall, to the tile on the other side. */
    internal suspend fun ScriptContext.squeeze(world: World, crack: ZoneLoc) {
        arriveDelay()
        // The cracks are on the south edge (angle 3) or the north edge (angle 1) of the room's tile.
        val outside = if (crack.angle == 3) crack.z - 1 else crack.z + 1
        val z = if (player.z == crack.z) outside else crack.z
        delay(1)
        world.teleport(player, crack.x, z, crack.level)
    }

    /** Open on a small chest: its key is used up and the table rolled. */
    internal suspend fun ScriptContext.openChest(world: World, chest: ZoneLoc) {
        arriveDelay()
        val key = CHESTS[chest.id] ?: return
        val slot = player.inventory.contents().firstOrNull { it.second.id == key }?.first
        if (slot == null) return world.message(player, HamStoreroomsLines.CHEST_LOCKED)
        if (player.inventory.freeSlots < CHEST_ROOM) return world.message(player, HamStoreroomsLines.CHEST_NO_ROOM)
        // Rolled before the key goes, so a coin stack too full for the coins keeps the key and loses nothing.
        val reward = rollChest(world).filter { it.second > 0 }
        val coins = reward.filter { it.first == COINS }.sumOf { it.second.toLong() }
        if (player.inventory.count(COINS) + coins > Int.MAX_VALUE) return world.message(player, HamStoreroomsLines.CHEST_NO_ROOM)
        player.anim(Searching.ANIM_OPEN)
        player.inventory.remove(slot, 1)
        world.message(player, HamStoreroomsLines.KEY_BREAKS, "key" to KEY_COLOURS.getValue(key))
        for ((obj, count) in reward) world.objTypes[obj]?.let { player.inventory.add(it, count) }
    }

    fun trapdoorOpen(player: Player): Boolean =
        player.vars.types.varbit(TRAPDOOR_VARBIT) == null || player.vars.getVarbit(TRAPDOOR_VARBIT) == TRAPDOOR_OPEN
}

fun registerHamStorerooms(world: World) = with(HamStorerooms) {
    world.handlers.onLoc(TRAPDOOR, 1) {
        arriveDelay()
        if (!trapdoorOpen(player)) return@onLoc
        player.anim(Climbing.ANIM_PICKUP_FLOOR)
        delay(1)
        world.teleport(player, BELOW.first, BELOW.second, BELOW.third)
    }
    world.handlers.onLoc(LADDER, 1) {
        arriveDelay()
        player.anim(Climbing.ANIM_REACH_FOR_LADDER)
        delay(1)
        world.teleport(player, ABOVE.first, ABOVE.second, ABOVE.third)
    }
    // Closing the opened door by hand puts the locked storeroom door back, not the plain door 1536 shuts into.
    world.doors.addClosedType(DOOR, DOOR_OPEN)
    world.handlers.onLoc(DOOR, OP_OPEN) { op -> openDoor(world, op.loc!!) }
    world.handlers.onLoc(DOOR, OP_PICK) { op -> pickDoor(world, op) }
    world.handlers.onLoc(CRACK, 1) { op -> squeeze(world, op.loc!!) }
    for (chest in CHESTS.keys) world.handlers.onLoc(chest, 1) { op -> openChest(world, op.loc!!) }
    world.doors.registerDouble(LARGE_DOOR_LEFT, LARGE_DOOR_LEFT_OPEN, LARGE_DOOR_RIGHT, LARGE_DOOR_RIGHT_OPEN)
}
