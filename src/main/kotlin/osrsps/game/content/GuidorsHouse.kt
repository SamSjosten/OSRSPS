package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Lines
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile

/** Julie's lines at Guidor's bedroom door (the Wiki's Transcript:Julie, "Opening Bedroom door without Priest gown"). */
object JulieLines : Lines("julie", "Transcript:Julie") {
    val DOOR_1 = line("door_1", "Julie, to a player opening Guidor's bedroom door without a priest gown")
    val DOOR_2 = line("door_2", "player's reply")
    val DOOR_3 = line("door_3", "Julie: only a priest may see him")
    val DOOR_4 = line("door_4", "player's last line")
}

/**
 * Guidor's house in Varrock's fenced south-east corner, as Biohazard leaves it (post-quest content step 3). Guidor and
 * his talk are [VarrockSweepPeople]'s; this is the way in to him. From the OSRS Wiki's Biohazard, Guidor and
 * Transcript:Julie pages (read 2026-11-19); tiles and angles from the 242 cache's map.
 *
 * - **The gate** (`guidorgatelclosed`/`_r` 2050 and 2051, on the west edge of 3264,3405 and 3264,3406) into the fenced
 *   corner: both leaves swing open (2052, 2053) for [MemberGates.OPEN_TICKS] and the player is put through in a tick,
 *   keeping their row, as the [MemberGates] do. The quest's search at the gate (the vials) is over, so it opens for all.
 * - **The bedroom door** (`guidordoor` 2032, on the east edge of 3282,3382; Guidor's bed is east of it): a player
 *   wearing the priest gown's top and bottom (426, 428) goes through; anyone else gets Julie's four lines at the door.
 *   From Guidor's side it lets the player out.
 *
 * - Inferred: the gown still wanted after the quest (the transcript's door lines name no quest stage, and the Wiki's
 *   Guidor page says his door can't be opened in free-to-play, so it is still a door with a rule).
 * - Guessed: every chathead expression; Julie's chathead shown with no Julie standing there (she isn't on mejrs's live
 *   list); the gate's and door's open look and two ticks, the door from inside letting anyone out.
 */
object GuidorsHouse {
    const val GATE_LEFT = 2050
    const val GATE_RIGHT = 2051
    const val GATE_LEFT_OPEN = 2052
    const val GATE_RIGHT_OPEN = 2053
    const val DOOR = 2032
    const val DOOR_OPEN = 2033
    const val JULIE = 1109
    const val OPEN = 1

    const val PRIEST_GOWN_TOP = 426
    const val PRIEST_GOWN_BOTTOM = 428

    val GATE = MemberGates.Gate(listOf(Tile(3264, 3405), Tile(3264, 3406)), edge = 0)
    val DOOR_TILE = Tile(3282, 3382)

    /** Guidor's side of his door: east of its edge. */
    fun insideRoom(x: Int) = x > DOOR_TILE.x

    /**
     * The gate's crossing tile on the player's side, nearest them: west of the fence (x 3263) or on the gate's own
     * column (3264), on one of its two rows. Null when the player isn't beside it.
     */
    fun crossingFrom(x: Int, z: Int): Tile? {
        val inner = GATE.tiles.first().x
        val rows = GATE.tiles.minOf { it.z }..GATE.tiles.maxOf { it.z }
        val tile = Tile(if (x < inner) inner - 1 else inner, z.coerceIn(rows))
        return tile.takeIf { maxOf(kotlin.math.abs(x - it.x), kotlin.math.abs(z - it.z)) <= 1 }
    }

    fun wearsGown(player: Player) = player.equipment.count(PRIEST_GOWN_TOP) > 0 && player.equipment.count(PRIEST_GOWN_BOTTOM) > 0
}

fun registerGuidorsHouse(world: World) = with(GuidorsHouse) {
    for (leaf in listOf(GATE_LEFT, GATE_RIGHT)) {
        world.handlers.onLoc(leaf, OPEN) { op ->
            val loc = op.loc!!
            // The gate can be reached from beside it along the fence too (north of the north leaf, south of the south
            // one), so a player there steps onto the crossing tile on their side first.
            val from = crossingFrom(player.x, player.z)
            val to = if (GATE.level == loc.level && GATE.tiles.any { it.x == loc.x && it.z == loc.z } && from != null) MemberGates.through(GATE, from.x, from.z) else null
            if (from == null || to == null) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            arriveDelay()
            val beside = player.x != from.x || player.z != from.z
            for (tile in GATE.tiles) {
                val closed = world.locs[tile.x, tile.z, loc.level, loc.layer]?.takeIf { it.id == GATE_LEFT || it.id == GATE_RIGHT } ?: continue
                world.doors.openAs(closed, if (closed.id == GATE_LEFT) GATE_LEFT_OPEN else GATE_RIGHT_OPEN, shutAfter = MemberGates.OPEN_TICKS)
            }
            if (beside) {
                // Walked: a step would end this script, so it is the last thing done.
                player.waypoints.clear()
                player.waypoints.add(from)
                player.waypoints.add(to)
                return@onLoc
            }
            delay(1)
            world.teleport(player, to.x, to.z, loc.level)
        }
    }
    world.handlers.onLoc(DOOR, OPEN) { op ->
        val door: ZoneLoc = op.loc!!
        if (door.x != DOOR_TILE.x || door.z != DOOR_TILE.z || door.level != 0) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
        arriveDelay()
        val inside = insideRoom(player.x)
        if (!inside && !wearsGown(player)) {
            val julie = world.npcTypes[JULIE] ?: return@onLoc
            world.dialogues.start(this) {
                chatNpc(julie, julie.name, ChatAnim.ANGRY, JulieLines.DOOR_1)
                chatPlayer(ChatAnim.NEUTRAL, JulieLines.DOOR_2)
                chatNpc(julie, julie.name, ChatAnim.NEUTRAL, JulieLines.DOOR_3)
                chatPlayer(ChatAnim.NEUTRAL, JulieLines.DOOR_4)
            }
            return@onLoc
        }
        // The tile on the player's side of the door's edge, then the one across it.
        val east = Tile(DOOR_TILE.x + 1, DOOR_TILE.z)
        val (from, to) = if (inside) east to DOOR_TILE else DOOR_TILE to east
        // Another player may have opened it during the arrival delay: then it is already open, and the player just walks.
        val closed = world.locs[door.x, door.z, door.level, door.layer]?.takeIf { it.id == DOOR }
        if (closed != null) world.doors.openAs(closed, DOOR_OPEN, shutAfter = MemberGates.OPEN_TICKS)
        player.waypoints.clear()
        if (player.x != from.x || player.z != from.z) player.waypoints.add(from)
        player.waypoints.add(to)
    }
}
