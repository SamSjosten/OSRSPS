package osrsps.game.content

import kotlin.math.abs
import osrsps.game.GameLines
import osrsps.game.GroundObjs
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.npc.Npc
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc
import osrsps.game.zone.ZoneObj
import osrsps.map.Tile

/**
 * Draynor Manor's inside (Draynor piece step 12a), as the 241 cache's map has it (map square 48,52, levels 0 to 2), after
 * Ernest the Chicken, which every account counts as done.
 *
 * - The panelled doors pair by name ([osrsps.game.loc.Doors]); the wardrobes are Edgeville's type ([Edgeville]); the
 *   sacks are the castle's ([LumbridgeCastle]); the crates and the upstairs bookcase are searched and hold nothing
 *   (guessed: the messages are the castle's and the village's).
 * - The front doors lock behind the player ([DraynorManorGrounds]); the way out is the back door (`hauntedbackdoor` 136
 *   in the east room, the Wiki: "an exit in the eastern-most room"). From inside it lets the player out onto the tile
 *   past it and shuts again; from outside it won't open (guessed, as are its open type `hauntedbackdoor_inactive` and the
 *   [DOOR_TICKS] it stands open).
 * - The main staircase (`draynor_manor_stairs_up` 2x4 at 3108,3362 and `_down` 2x2 above it) isn't spiral: it takes the
 *   player off its far end, as Draynor's other straight stairs do ([DraynorVillage]). The spiral stairs to the top floor
 *   (`draynor_spiralstairs` and `sarim_spiralstairstop`) are [climbStairs]'s. Both inferred, not seen on live.
 * - The bookcases (`hauntedbookcasel` 155 and `r` 156 at 3097,3358 and 3359) open from the east only, as 2004Scape's
 *   quest_haunted script has them: they turn into an invisible wall with the open bookcase beside each for [PASSAGE_TICKS]
 *   and the player is put in the room behind, on the tile the script names. The lever there (`hauntedleverup` 160)
 *   pulls down for [LEVER_TICKS] and lets the player back out two tiles east. The basement's ladder is step 13.
 * - The closet behind the stairs (`closet_door` 131 at 3107,3367) is locked; the Ernest the Chicken key (275), which
 *   can't be had once the quest is done, unlocks it and the player walks through as 2004Scape's script has it. Taking the
 *   rubber tube there sets the skeleton on the player (the Wiki, Skeleton (Draynor Manor)).
 * - The top floor's south room door (`whistledoor` 22) opens and shuts behind the player; the Holy Grail whistles aren't
 *   spawned, as Holy Grail is members' and its napkin isn't modelled.
 */
object DraynorManor {
    const val PANELLED_DOOR = 11470
    const val BACK_DOOR = 136
    const val BACK_DOOR_OPEN = 11448
    const val CLOSET_DOOR = 131
    const val CLOSET_DOOR_OPEN = 1532
    const val WHISTLE_DOOR = 22
    const val WHISTLE_DOOR_OPEN = 1536
    const val OPEN = 1

    /** How long a door the player walks through stands open. Guessed. */
    const val DOOR_TICKS = 3

    const val STAIRS_UP = 11498
    const val STAIRS_DOWN = 11499
    const val SPIRAL_UP = 11511
    const val SPIRAL_DOWN = 9584
    const val CLIMB = 1

    /** Where the main staircase, by the tile of the piece clicked, puts the player (off its far end; inferred). */
    val STAIRS: Map<Triple<Int, Int, Int>, Tile> = mapOf(
        Triple(3108, 3362, 0) to Tile(3108, 3366),
        Triple(3108, 3364, 1) to Tile(3108, 3361),
    )

    const val BOOKCASE_L = 155
    const val BOOKCASE_R = 156
    const val BOOKCASE_OPEN = 157
    const val INVISIBLE_WALL = 38848
    const val SEARCH = 1

    /** The room behind the bookcases: where each one's Search puts the player (2004Scape's end_coord params). */
    val BEHIND: Map<Int, Tile> = mapOf(BOOKCASE_L to Tile(3096, 3358), BOOKCASE_R to Tile(3096, 3359))

    /** How long the bookcases stand open (2004Scape: 3 ticks). */
    const val PASSAGE_TICKS = 3

    const val LEVER = 160
    const val LEVER_DOWN = 161
    const val PULL = 1

    /** How long the lever stays down (2004Scape: 4 ticks). */
    const val LEVER_TICKS = 4

    /** human_leverdown. */
    const val ANIM_LEVER = 834

    const val CRATE = 11485
    const val CRATES = 11486
    const val POLY_BOOKCASE = 9611

    const val CLOSET_KEY = 275
    const val RUBBER_TUBE = 276

    /** draynor_skeleton, in the closet. */
    const val SKELETON = 3565

    /** human_pickuptable. */
    const val ANIM_PICKUP_TABLE = 832

    /** The skeleton turning on whoever took the tube; tests swap it for a recorder. Called on the game thread. */
    @Volatile internal var setOn: (World, Npc, Player) -> Unit = { world, npc, player -> world.meleeCombat.provoke(npc, player, world.tick) }

    /** Where the player stands to search the bookcases: east of them. */
    fun eastOf(bookcase: ZoneLoc, x: Int): Boolean = x > bookcase.x

    /** Whether a player at [x], [z] is inside the manor's east room, north of the back door (it is on the room's wall). */
    fun insideBackDoor(door: ZoneLoc, x: Int, z: Int): Boolean = x == door.x && z < door.z

    /**
     * The tile on the other side of wall [door] from a player at [x], [z]: a door on the west edge of its tile (angle 0)
     * joins it to the tile west of it, and so on round. Someone on neither tile goes onto the door's own.
     */
    fun otherSide(door: ZoneLoc, x: Int, z: Int): Tile {
        val (dx, dz) = when (door.angle and 3) {
            0 -> -1 to 0
            1 -> 0 to 1
            2 -> 1 to 0
            else -> 0 to -1
        }
        return if (x == door.x && z == door.z) Tile(door.x + dx, door.z + dz) else Tile(door.x, door.z)
    }
}

fun registerDraynorManor(world: World) {
    with(DraynorManor) {
        // Their open forms are other doors' too: closing one by hand turns it back into this door, not that one.
        world.doors.addClosedType(BACK_DOOR, BACK_DOOR_OPEN)
        world.doors.addClosedType(CLOSET_DOOR, CLOSET_DOOR_OPEN)
        world.doors.addClosedType(WHISTLE_DOOR, WHISTLE_DOOR_OPEN)
        world.handlers.onLoc(BACK_DOOR, OPEN) { op ->
            val door = op.loc!!
            if (!insideBackDoor(door, player.x, player.z)) return@onLoc world.message(player, ManorGroundsLines.BACK_DOOR_LOCKED)
            walkThrough(world, door, BACK_DOOR_OPEN, Tile(door.x, door.z))
        }
        world.handlers.onLoc(CLOSET_DOOR, OPEN) { world.message(player, ManorGroundsLines.CLOSET_LOCKED) }
        world.handlers.onObjOnLoc(CLOSET_DOOR, CLOSET_KEY) { op ->
            val door = op.loc!!
            world.message(player, ManorGroundsLines.CLOSET_UNLOCK)
            world.message(player, ManorGroundsLines.CLOSET_THROUGH)
            walkThrough(world, door, CLOSET_DOOR_OPEN, otherSide(door, player.x, player.z))
        }
        world.handlers.onLoc(WHISTLE_DOOR, OPEN) { op ->
            val door = op.loc!!
            walkThrough(world, door, WHISTLE_DOOR_OPEN, otherSide(door, player.x, player.z))
        }
        for (id in listOf(STAIRS_UP, STAIRS_DOWN)) {
            world.handlers.onLoc(id, CLIMB) { op ->
                val loc = op.loc!!
                val to = STAIRS[Triple(loc.x, loc.z, loc.level)] ?: return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
                world.teleport(player, to.x, to.z, if (loc.id == STAIRS_UP) loc.level + 1 else loc.level - 1)
            }
        }
        world.handlers.onLoc(SPIRAL_UP, CLIMB) { op -> climbStairs(world, op.loc!!, 1) }
        world.handlers.onLoc(SPIRAL_DOWN, CLIMB) { op -> climbStairs(world, op.loc!!, -1) }
        for (id in listOf(BOOKCASE_L, BOOKCASE_R)) {
            world.handlers.onLoc(id, SEARCH) { op ->
                val bookcase = op.loc!!
                // From inside, as 2004Scape has it, nothing happens: the lever is the way out.
                if (!eastOf(bookcase, player.x)) return@onLoc
                world.message(player, ManorGroundsLines.SECRET_DOOR)
                openPassage(world, bookcase.level, BEHIND.getValue(bookcase.id))
            }
        }
        world.handlers.onLoc(LEVER, PULL) { op ->
            val lever = op.loc!!
            world.message(player, ManorGroundsLines.LEVER_OPENS)
            player.anim(ANIM_LEVER)
            world.locs.replace(lever, LEVER_DOWN, LEVER_TICKS)
            // 2004Scape: a step north, a tick, then out two tiles east of where the player pulled it.
            val x = player.x
            val z = player.z
            world.teleport(player, x, z + 1, lever.level)
            delay(1)
            openPassage(world, lever.level, Tile(x + 2, z + 1))
        }
        registerSearch(world, CRATE, SEARCH, SearchLines.CRATE_EMPTY)
        registerSearch(world, CRATES, SEARCH, SearchLines.CRATE_EMPTY)
        registerSearch(world, POLY_BOOKCASE, SEARCH, DraynorSearchLines.BOOKCASE_EMPTY)
        world.handlers.onGroundObj(RUBBER_TUBE, GroundObjs.TAKE_OPTION) { op -> takeRubberTube(world, op.groundObj!!) }
    }
}

/**
 * Swings [door] open as [openType] for [DraynorManor.DOOR_TICKS], on the world's loc timer as the jail's cell door does,
 * and walks the player through to [to].
 */
private fun ScriptContext.walkThrough(world: World, door: ZoneLoc, openType: Int, to: Tile) {
    world.doors.openAs(door, openType, shutAfter = DraynorManor.DOOR_TICKS)
    player.waypoints.clear()
    player.waypoints.add(to)
}

/**
 * The bookcases beside the room behind them (whichever of the two is on [level] there) open for
 * [DraynorManor.PASSAGE_TICKS]: each becomes an invisible wall, with the open bookcase on the tile beyond it, and the player
 * is put on [dest].
 */
private fun ScriptContext.openPassage(world: World, level: Int, dest: Tile) = with(DraynorManor) {
    for ((id, dz) in listOf(BOOKCASE_L to -1, BOOKCASE_R to 1)) {
        val bookcase = world.locs.at(BOOKCASE_X, if (id == BOOKCASE_L) BOOKCASE_Z else BOOKCASE_Z + 1, level).firstOrNull { it.id == id } ?: continue
        world.locs.replace(bookcase, INVISIBLE_WALL, PASSAGE_TICKS)
        world.locs.add(ZoneLoc(BOOKCASE_OPEN, bookcase.shape, bookcase.angle, bookcase.x, bookcase.z + dz, bookcase.level), PASSAGE_TICKS)
    }
    world.teleport(player, dest.x, dest.z, level)
}

private const val BOOKCASE_X = 3097
private const val BOOKCASE_Z = 3358

/** Take on the closet's rubber tube: as any Take, then the skeleton beside it turns on the player. */
private suspend fun ScriptContext.takeRubberTube(world: World, obj: ZoneObj) {
    if (player.x != obj.x || player.z != obj.z) {
        delay(1)
        player.anim(DraynorManor.ANIM_PICKUP_TABLE)
    }
    if (!world.groundObjs.take(player, obj)) return
    val skeleton = world.npcs.all.firstOrNull {
        it.type.id == DraynorManor.SKELETON && !it.dead && it.level == obj.level && abs(it.x - obj.x) <= 4 && abs(it.z - obj.z) <= 4
    } ?: return
    DraynorManor.setOn(world, skeleton, player)
}
