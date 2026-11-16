package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.GroundObjs
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile

/** The muddy chest's lines (keys only; the words are in data/dialogue.txt). */
object LavaMazeLines : Lines("lava_maze", "Lava Maze") {
    val CHEST_LOCKED = line("chest_locked", "Open on the muddy chest without a muddy key")
    val CHEST_UNLOCK = line("chest_unlock", "Open on the muddy chest with a muddy key: the key goes and the chest opens")
    val CHEST_TREASURE = line("chest_treasure", "after the muddy chest opens: its loot is given")
}

/**
 * The Lava Maze and the Lava Maze Dungeon under it (Wilderness step W3.4b), as the 242 map has them.
 *
 * - **The dungeon's two ladders** go the "wrong" way round, as the Wiki's Lava Maze Dungeon page says: the west one in
 *   the lesser demons' pen (`wildymirrorladdertop1` 18987 at 3017,3849) comes out by the King Black Dragon's lever at
 *   the dungeon's east end (`wildymirrorladder1` 18988 at 3069,10256), and the one in the maze's fenced middle
 *   (`wildymirrorladdertop2` 18989 at 3069,3856) at its west end (`wildymirrorladder2` 18990 at 3017,10249), by the
 *   greater demons, black dragons and the runite rock. Each keeps the player's place beside the ladder (inferred).
 * - **The fence gates** (`membergatel`/`_r` 1727 and 1728) into the lesser demons' pen west of the maze (on the west
 *   edge of 3008,3849 and 3008,3850) and into the maze's fenced middle (on the east edge of 3071,3856 and 3071,3857)
 *   pass the player through as the Edgeville Dungeon's north gate does ([MemberGates]); the metal gates north of the
 *   middle (`metalgateclosedl`/`_r` 1568 and 1569 at 3075 and 3076,3867) open and close as double doors, as the
 *   Edgeville Dungeon's do.
 * - **The muddy chest** (`muddy_chestclosed` 170 at 3089,3859): Open with a muddy key takes the key and gives the
 *   loot the Wiki lists (the drop file's `muddy_chest` table) into the backpack, any that doesn't fit at the player's
 *   feet; the chest shows open (171) for [CHEST_OPEN_TICKS]. The words are held until sourced.
 * - The webs (`bigweb_slashable` 733) slash as the Varrock Sewers' do ([Webs]); the runite rocks mine by type; the
 *   King Black Dragon's lever (`dragonkinginlever` 1816) is nothing interesting until Wilderness step W4.6.
 *
 * Guessed until recorded: the climbs' animations and one-tick wait (as the other dungeon ladders), the chest's open
 * time, the loot going to the backpack. Left out: the stepping stone (82 Agility; Agility's shortcuts aren't built).
 */
object LavaMaze {
    /** A ladder at the top and the one it leads to below. */
    data class MirrorLadder(val top: Int, val topTile: Tile, val bottom: Int, val bottomTile: Tile)

    val WEST_LADDER = MirrorLadder(18987, Tile(3017, 3849), 18988, Tile(3069, 10256))
    val MIDDLE_LADDER = MirrorLadder(18989, Tile(3069, 3856), 18990, Tile(3017, 10249))
    val LADDERS = listOf(WEST_LADDER, MIDDLE_LADDER)

    const val CLIMB = 1

    const val CHEST = 170
    const val CHEST_OPEN = 171
    val CHEST_TILE = Tile(3089, 3859)
    const val MUDDY_KEY = 991
    const val CHEST_TABLE = "muddy_chest"
    const val OPEN = 1

    /** How long the chest shows open after a key (guessed). */
    const val CHEST_OPEN_TICKS = 3

    /** The metal gates north of the maze's middle: their leaves shut, and where they stand swung open, a tile north. */
    val METAL_GATE_TILES = listOf(Tile(3075, 3867), Tile(3076, 3867), Tile(3075, 3868), Tile(3076, 3868))

    fun isMetalGate(loc: ZoneLoc) = loc.level == 0 && METAL_GATE_TILES.any { it.x == loc.x && it.z == loc.z }

    /** Where ladder [from] (at [fromTile]) puts a player at [x], [z]: the same place beside the ladder at [toTile]. */
    fun across(fromTile: Tile, toTile: Tile, x: Int, z: Int) = Tile(toTile.x + (x - fromTile.x), toTile.z + (z - fromTile.z))
}

/**
 * Fence gates that pass the player through rather than swinging open for good: the Edgeville Dungeon's north gate
 * ([EdgevilleDungeon]) and the Lava Maze's two ([LavaMaze]). Each is two leaves (`membergatel`/`_r` 1727 and 1728, no
 * open types in the cache) on one edge of their tiles; Open swings both open for [OPEN_TICKS] and puts the player on
 * the other side in a tick, keeping their row or column. Guessed until recorded, as the north gate's.
 */
object MemberGates {
    /** Which edge of its tiles a gate stands on, as a wall's angle gives it: 0 west, 1 north, 2 east, 3 south. */
    data class Gate(val tiles: List<Tile>, val edge: Int, val level: Int = 0)

    const val LEFT = 1727
    const val RIGHT = 1728

    /** How long a gate stands open after a player goes through (guessed). */
    const val OPEN_TICKS = 2

    val EDGEVILLE_DUNGEON_NORTH = Gate(listOf(Tile(3131, 9917), Tile(3132, 9917)), edge = 1)
    val LAVA_MAZE_PEN = Gate(listOf(Tile(3008, 3849), Tile(3008, 3850)), edge = 0)
    val LAVA_MAZE_MIDDLE = Gate(listOf(Tile(3071, 3856), Tile(3071, 3857)), edge = 2)
    val ALL = listOf(EDGEVILLE_DUNGEON_NORTH, LAVA_MAZE_PEN, LAVA_MAZE_MIDDLE)

    fun of(loc: ZoneLoc): Gate? = ALL.firstOrNull { gate -> gate.level == loc.level && gate.tiles.any { it.x == loc.x && it.z == loc.z } }

    /**
     * Where [gate] takes a player at [x], [z]: across its edge from whichever side they stand on, in front of one of its
     * leaves; anyone else goes nowhere (null).
     */
    fun through(gate: Gate, x: Int, z: Int): Tile? = when (gate.edge) {
        0, 2 -> {
            if (gate.tiles.none { it.z == z }) null
            else {
                val inner = gate.tiles.first().x
                val outer = if (gate.edge == 0) inner - 1 else inner + 1
                when (x) {
                    inner -> Tile(outer, z)
                    outer -> Tile(inner, z)
                    else -> null
                }
            }
        }
        else -> {
            if (gate.tiles.none { it.x == x }) null
            else {
                val inner = gate.tiles.first().z
                val outer = if (gate.edge == 1) inner + 1 else inner - 1
                when (z) {
                    inner -> Tile(x, outer)
                    outer -> Tile(x, inner)
                    else -> null
                }
            }
        }
    }

    fun register(world: World) {
        for (leaf in listOf(LEFT, RIGHT)) {
            world.handlers.onLoc(leaf, 1) { op ->
                val loc = op.loc!!
                val gate = of(loc)
                val to = gate?.let { through(it, player.x, player.z) }
                if (gate == null || to == null) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
                arriveDelay()
                for (tile in gate.tiles) {
                    val closed = world.locs[tile.x, tile.z, loc.level, loc.layer]?.takeIf { it.id == LEFT || it.id == RIGHT } ?: continue
                    world.doors.openAs(closed, closed.id, shutAfter = OPEN_TICKS)
                }
                delay(1)
                world.teleport(player, to.x, to.z, loc.level)
            }
        }
    }
}

fun registerLavaMaze(world: World) {
    with(LavaMaze) {
        for (ladder in LADDERS) {
            world.handlers.onLoc(ladder.top, CLIMB) { op ->
                val loc = op.loc!!
                if (loc.x != ladder.topTile.x || loc.z != ladder.topTile.z) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
                mirrorClimb(world, across(ladder.topTile, ladder.bottomTile, player.x, player.z), Climbing.ANIM_PICKUP_FLOOR)
            }
            world.handlers.onLoc(ladder.bottom, CLIMB) { op ->
                val loc = op.loc!!
                if (loc.x != ladder.bottomTile.x || loc.z != ladder.bottomTile.z) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
                mirrorClimb(world, across(ladder.bottomTile, ladder.topTile, player.x, player.z), Climbing.ANIM_REACH_FOR_LADDER)
            }
        }
        world.handlers.onLoc(CHEST, OPEN) { op -> openChest(world, op.loc!!) }
    }
}

/** A climb to [to] on level 0: the tile is taken before the wait, as [climbLadder] takes it. */
private suspend fun ScriptContext.mirrorClimb(world: World, to: Tile, anim: Int) {
    arriveDelay()
    player.anim(anim)
    delay(1)
    world.teleport(player, to.x, to.z, 0)
}

private suspend fun ScriptContext.openChest(world: World, loc: ZoneLoc) {
    if (loc.x != LavaMaze.CHEST_TILE.x || loc.z != LavaMaze.CHEST_TILE.z) return world.message(player, GameLines.NOTHING_INTERESTING)
    fun keySlot() = player.inventory.contents().firstOrNull { (_, obj) -> obj.id == LavaMaze.MUDDY_KEY }?.first
    if (keySlot() == null) return world.message(player, LavaMazeLines.CHEST_LOCKED)
    arriveDelay()
    // Looked for again after the walk (round 37): the key may have gone meanwhile, and only a key taken opens it.
    val slot = keySlot() ?: return world.message(player, LavaMazeLines.CHEST_LOCKED)
    if (player.inventory.remove(slot, 1) != 1) return world.message(player, LavaMazeLines.CHEST_LOCKED)
    world.message(player, LavaMazeLines.CHEST_UNLOCK)
    world.locs.replace(loc, LavaMaze.CHEST_OPEN, revertAfter = LavaMaze.CHEST_OPEN_TICKS)
    val loot = world.npcDrops.chests[LavaMaze.CHEST_TABLE]?.roll(world.random).orEmpty()
    for (drop in loot) {
        val over = drop.count - player.inventory.add(drop.type, drop.count)
        if (over > 0) {
            world.groundObjs.addPrivate(drop.type, over, player.x, player.z, player.level, player.saveKey, GroundObjs.DROP_PRIVATE_TICKS, GroundObjs.LOOT_DESPAWN_TICKS)
        }
    }
    world.message(player, LavaMazeLines.CHEST_TREASURE)
}
