package osrsps.game.content

import osrsps.game.Movement
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.map.Tile

/** The Wilderness Agility Course dispenser's words, in the dialogue file. */
object WildernessAgilityLines : Lines("wilderness_agility", "Wilderness Agility Course") {
    val TICKET = line("ticket", "Tag on the agility dispenser after a lap: the ticket it gives")
    val REDEEMED = line("redeemed", "Redeem on the agility dispenser: the tickets taken and the XP they gave", "count", "xp")
    val NO_TICKETS = line("no_tickets", "Redeem on the agility dispenser with no tickets")
}

/**
 * The Wilderness Agility Course around its obstacles (Wilderness step W6.4; the OSRS Wiki's Wilderness Agility Course,
 * Agility dispenser, Wilderness agility ticket and Wilderness Agility Course Dungeon pages, read 2026-11-15). The way-in
 * door, the gates out and the five obstacles are agility.json's ([osrsps.game.skill.Agility]); this holds the rest.
 *
 * - **The dispenser** (`wildy_agility_pillar` [DISPENSER] at 3005,3936): Tag restores the player's run energy every time,
 *   and after a lap that counted gives one Wilderness agility ticket ([TICKET]), once a lap. Redeem takes every ticket in
 *   the inventory for Agility XP, 200 each, 210 each for 11 to 50, 220 for 51 to 100 and 230 for 101 or more (the Wiki's
 *   table: the rate of the whole batch goes by its size).
 * - **The dungeon below**: the ladder past the ropeswing ([LADDER_DOWN] at 3005,3963) climbs down to [BELOW], and the
 *   ladder there (`ladder_from_cellar`) climbs back up with every other dungeon ladder of that type
 *   ([climbDungeonLadder]). The ropeswing and log falls land there (agility.json).
 * - **Singles-plus combat** across the course ([onCourse]): a player can attack someone a monster is fighting.
 *
 * GUESSED: Redeem takes every ticket at once (the Wiki gives no count prompt); the ticket waits for the Tag rather than
 * coming with the lap, and one lap's Tag can't give two; the landing below the ladder. Not built: the 150,000 coin fee
 * (Pay) and the loot it buys, which need the looting bag (not built), and the lap streak that goes with it; the
 * surface skeletons the April 2024 update added (the Wiki gives no tiles).
 */
object WildernessAgility {
    const val DISPENSER = 53224
    const val TICKET = 29460
    const val LADDER_DOWN = 14758
    val LADDER_DOWN_AT = Tile(3005, 3963)
    val BELOW = Tile(3005, 10362)

    /**
     * Inside the course's walls, from the gates north past the ladder: singles-plus combat ([SinglesPlus]; the Wiki's
     * Wilderness Agility Course and Singles-plus combat pages: "the entire course", since 10 April 2024). INFERRED: the
     * bounds, from the obstacles' tiles and the gates in the 242 map; the ridge south of the gates and the dungeon below
     * stay single-way (the Wiki names neither).
     */
    fun onCourse(x: Int, z: Int): Boolean = x in 2989..3008 && z in 3931..3967

    /** The [osrsps.game.Charges] counter: 1 when a lap has counted and the dispenser hasn't been tagged since. */
    const val UNTAGGED = "wilderness_agility_untagged"

    /** The Wiki's ticket table: the least tickets in a batch, and the XP each then gives. */
    private val RATES = listOf(101 to 230, 51 to 220, 11 to 210, 1 to 200)

    /** The XP for redeeming [count] tickets at once. */
    fun redeemXp(count: Int): Long {
        if (count <= 0) return 0
        val each = RATES.first { count >= it.first }.second
        return count.toLong() * each
    }

    internal fun tag(world: World, player: Player) {
        player.runEnergy = Movement.MAX_ENERGY
        if (player.charges[UNTAGGED] == 0) return
        player.charges[UNTAGGED] = 0
        val type = world.objTypes[TICKET] ?: ObjType(TICKET, "Wilderness agility ticket", stackable = true)
        world.groundObjs.give(player, type, 1)
        world.message(player, WildernessAgilityLines.TICKET)
    }

    internal fun redeem(world: World, player: Player) {
        val held = player.inventory.count(TICKET).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (held == 0) return world.message(player, WildernessAgilityLines.NO_TICKETS)
        val xp = redeemXp(held)
        Ned.take(player, TICKET, held)
        // XP in tenths stays well inside an Int: at most 230 XP a ticket for a stack the inventory can hold.
        player.skills.addTenths(Skill.AGILITY, (xp * 10).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        world.message(player, WildernessAgilityLines.REDEEMED, "count" to held, "xp" to xp)
    }
}

fun registerWildernessAgility(world: World) {
    with(WildernessAgility) {
        val lap = world.agility.onLap
        world.agility.onLap = { player, course ->
            lap(player, course)
            if (course.key == "wilderness") player.charges[UNTAGGED] = 1
        }
        world.handlers.onLoc(DISPENSER, 1) { _ ->
            arriveDelay()
            tag(world, player)
        }
        world.handlers.onLoc(DISPENSER, 3) { _ ->
            arriveDelay()
            redeem(world, player)
        }
        world.handlers.onLoc(LADDER_DOWN, 1) { op ->
            val loc = op.loc!!
            arriveDelay()
            if (loc.x != LADDER_DOWN_AT.x || loc.z != LADDER_DOWN_AT.z) return@onLoc world.message(player, osrsps.game.GameLines.NOTHING_INTERESTING)
            player.anim(Climbing.ANIM_PICKUP_FLOOR)
            delay(1)
            world.teleport(player, BELOW.x, BELOW.z, 0)
        }
    }
}
