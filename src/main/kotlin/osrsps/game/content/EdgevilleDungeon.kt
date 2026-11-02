package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile

/** The Edgeville Dungeon's lines (keys only; the words are in data/dialogue.txt). */
object EdgevilleDungeonLines : Lines("edgeville_dungeon", "Edgeville Dungeon") {
    /** Our own words, not live's: the Wilderness isn't built, so the gate into its part of the dungeon stays shut. */
    val GATE_CLOSED = line("gate_closed", "Open on the gate north of the dungeon's middle: ours, the Wilderness isn't built yet")
}

/**
 * The Edgeville Dungeon's north and middle (Edgeville piece step 10), under Edgeville, as the 241 cache's map has it
 * (map squares 48,154 and 49,154).
 *
 * - The trapdoor in the ruined house south of the bank (`trapdoor` 1579 at 3097,3468) opens into `trapdoor_open` 1581
 *   (Climb-down, Close) as furniture does ([swapFurniture]: the open animation, back shut after
 *   [Searching.CHANGE_TICKS]). Climbing down lands north of the ladder below (`ladder_from_cellar` 17385 at 3097,9867,
 *   a tile further south than a dungeon's 6400), on [LANDING]: the ladder stands against the passage's south wall, so
 *   the tiles south of it are behind that wall, and wherever the player stood by the trapdoor they come out in the
 *   passage. The ladder climbs up as the Lumbridge cellar's ([LumbridgeCastle]), from there onto the trapdoor's own
 *   tile. The trapdoor climbs only here; the type is elsewhere on the map too, where Climb-down is nothing interesting
 *   until a piece builds that one. The landing tile is guessed until recorded.
 * - The metal gates (`metalgateclosedl`/`_r` 1568 and 1569, into `metalgateopenl`/`_r` 1571 and 1572), west of the
 *   skeletons at 3103,9909 and by the mine at 3145,9870, open and close as double doors ([osrsps.game.loc.Doors]),
 *   only here: elsewhere on the map they are nothing interesting until their piece builds them.
 * - The odd looking wall west of the unaggressive skeletons (`oddwall_l` 1736 at 3094,9895 and `oddwall_r` 1734 above
 *   it): Push opens both halves (`openoddwall_l`/`_r` 1737 and 1735) for [WALL_OPEN_TICKS] and puts the player on the
 *   other side, as the Cooks' Guild's doors do. The open types have no options, so the wall shuts by itself; how long
 *   it stays open and how the player passes are guessed until recorded.
 * - The gate north of the middle (`membergatel`/`_r` 1727 and 1728 at 3131 and 3132,9917) leads into the Wilderness's
 *   part of the dungeon, which isn't built: Open refuses, in our own words. Elsewhere those types are nothing
 *   interesting until their piece builds them.
 * - The zombies, skeletons, hobgoblins, rats and giant spiders fight and drop as npc-combat.json and npc-drops.json
 *   have them (npc-spawns/edgeville-dungeon.json); the mine's rocks mine by type ([osrsps.game.skill.Mining]), the
 *   crates and sacks search by type. Vannaka stands by the gate without talk until Slayer is built, and the obstacle
 *   pipes are Agility shortcuts left out with the rest of Agility's shortcuts.
 */
object EdgevilleDungeon {
    const val TRAPDOOR = 1579
    const val TRAPDOOR_OPEN = 1581
    const val OPEN = 1
    const val CLIMB_DOWN = 1
    const val CLOSE = 2

    /** The trapdoor on the ground and the ladder under it. */
    val TRAPDOOR_TILE = Tile(3097, 3468)
    val LADDER_TILE = Tile(3097, 9867)

    /** Where climbing down puts the player: north of the ladder, in the passage. */
    val LANDING = Tile(3097, 9868)

    const val GATE_LEFT = 1568
    const val GATE_RIGHT = 1569
    const val GATE_LEFT_OPEN = 1571
    const val GATE_RIGHT_OPEN = 1572

    /** Open on the closed leaves, Close on the open ones. */
    const val GATE_OPTION = 1

    const val WALL_LEFT = 1736
    const val WALL_RIGHT = 1734
    const val WALL_LEFT_OPEN = 1737
    const val WALL_RIGHT_OPEN = 1735
    const val PUSH = 1

    /** How long the wall stands open after a push (guessed). */
    const val WALL_OPEN_TICKS = 2

    /** The wall's two halves: the left one at the south, the right one north of it, on the west edge of their tiles. */
    val WALL_TILES = listOf(Tile(3094, 9895), Tile(3094, 9896))

    const val NORTH_GATE_LEFT = 1727
    const val NORTH_GATE_RIGHT = 1728

    /** The north gate's two leaves. */
    val NORTH_GATE_TILES = listOf(Tile(3131, 9917), Tile(3132, 9917))

    const val VANNAKA = 403

    /** Whether [loc] is in the dungeon's north and middle, south of the north gate (map squares 48,154 and 49,154). */
    fun inMiddle(loc: ZoneLoc) = loc.level == 0 && loc.x in 3072..3199 && loc.z in 9856..9919

    fun isTrapdoor(loc: ZoneLoc) = loc.x == TRAPDOOR_TILE.x && loc.z == TRAPDOOR_TILE.z && loc.level == 0

    fun isWall(loc: ZoneLoc) = loc.level == 0 && WALL_TILES.any { it.x == loc.x && it.z == loc.z }

    fun isNorthGate(loc: ZoneLoc) = loc.level == 0 && NORTH_GATE_TILES.any { it.x == loc.x && it.z == loc.z }

    /**
     * Where a push on wall half [wall] puts a player at [x], [z]: the tile across the wall from them, when they stand on
     * either side of it (null otherwise, so nobody is carried through from anywhere else).
     */
    fun acrossWall(wall: ZoneLoc, x: Int, z: Int): Tile? {
        val inside = Tile(wall.x, wall.z)
        val outside = DraynorManor.otherSide(wall, wall.x, wall.z)
        return when {
            x == inside.x && z == inside.z -> outside
            x == outside.x && z == outside.z -> inside
            else -> null
        }
    }
}

fun registerEdgevilleDungeon(world: World) {
    with(EdgevilleDungeon) {
        world.handlers.onLoc(TRAPDOOR, OPEN) { op -> swapFurniture(world, op.loc!!, TRAPDOOR_OPEN, Searching.ANIM_OPEN) }
        world.handlers.onLoc(TRAPDOOR_OPEN, CLOSE) { op -> swapFurniture(world, op.loc!!, TRAPDOOR, Searching.ANIM_SHUT) }
        world.handlers.onLoc(TRAPDOOR_OPEN, CLIMB_DOWN) { op ->
            val loc = op.loc!!
            if (!isTrapdoor(loc)) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            arriveDelay()
            player.anim(Climbing.ANIM_PICKUP_FLOOR)
            delay(1)
            world.teleport(player, LANDING.x, LANDING.z, 0)
        }

        // Paired as double doors, but they swing only here: the 241 map has these gates in a dozen other places (the
        // Taverley Dungeon, the Wilderness and more), each its own piece's to open or keep shut.
        world.doors.registerDouble(GATE_LEFT, GATE_LEFT_OPEN, GATE_RIGHT, GATE_RIGHT_OPEN)
        for (leaf in listOf(GATE_LEFT, GATE_RIGHT, GATE_LEFT_OPEN, GATE_RIGHT_OPEN)) {
            world.handlers.onLoc(leaf, GATE_OPTION) { op ->
                val loc = op.loc!!
                if (!inMiddle(loc) || world.doors.toggle(loc, GATE_OPTION) == null) world.message(player, GameLines.NOTHING_INTERESTING)
            }
        }

        for (half in listOf(WALL_LEFT, WALL_RIGHT)) {
            world.handlers.onLoc(half, PUSH) { op ->
                val loc = op.loc!!
                val to = if (isWall(loc)) acrossWall(loc, player.x, player.z) else null
                if (to == null) {
                    world.message(player, GameLines.NOTHING_INTERESTING)
                    return@onLoc
                }
                arriveDelay()
                for (tile in WALL_TILES) {
                    val wall = world.locs[tile.x, tile.z, loc.level, loc.layer] ?: continue
                    val open = when (wall.id) {
                        WALL_LEFT -> WALL_LEFT_OPEN
                        WALL_RIGHT -> WALL_RIGHT_OPEN
                        else -> continue
                    }
                    world.locs.replace(wall, open, revertAfter = WALL_OPEN_TICKS)
                }
                delay(1)
                world.teleport(player, to.x, to.z, loc.level)
            }
        }

        for (leaf in listOf(NORTH_GATE_LEFT, NORTH_GATE_RIGHT)) {
            world.handlers.onLoc(leaf, OPEN) { op ->
                world.message(player, if (isNorthGate(op.loc!!)) EdgevilleDungeonLines.GATE_CLOSED else GameLines.NOTHING_INTERESTING)
            }
        }
    }
}
