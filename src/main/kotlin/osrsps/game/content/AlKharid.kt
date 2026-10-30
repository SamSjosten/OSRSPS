package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc
import osrsps.map.LocShape

/** The Border Guards' lines; the words are in the dialogue file, from the Wiki's Transcript:Border Guard, read 2026-10-30. */
object BorderGuardLines : Lines("border_guard", "Transcript:Border Guard") {
    val COME_THROUGH = line("come_through", "the player asks to come through the gate")
    val FRIEND = line("friend", "after Prince Ali Rescue: the guard lets the player through free, a friend of Al-Kharid")
}

/**
 * The toll gate between Lumbridge and Al Kharid, as after Prince Ali Rescue (every account has every quest done).
 *
 * The map places the gate as multilocs 44598 (left, 3268, 3227) and 44599 (right, 3268, 3228) on the west edge of
 * their tiles. Varp 273 (Prince Ali Rescue) at 100 or more shows them as 44050 and 44051, whose only option is Open; the
 * Pay-toll(10gp) types 44052 and 44053 are the before-quest ones (cache, and the Wiki's Toll gate page). From the Wiki:
 * since 27 April 2022, Open after the quest opens the gate with no talk; Talk-to on a Border Guard gets two lines, then
 * the player goes through free.
 *
 * How the player goes through is 2004Scape's border_gate.rs2 (not yet recorded on live, so guessed): both leaves swing
 * open for [OPEN_TICKS] ticks as their open types (`inacmetalgateopenl`/`r` 1573/1574, the closed gates' models with no
 * options), the player turns to the far side and moves there at once. A player who talked to a guard first steps up to
 * the gate on their own side. The gate's sound is left out until a recording gives its id.
 */
object TollGate {
    const val LEFT = 44598
    const val RIGHT = 44599
    const val LEFT_OPEN = 1573
    const val RIGHT_OPEN = 1574
    const val OPEN = 1

    const val BORDER_GUARD_WEST = 4287
    const val BORDER_GUARD_EAST = 4288
    const val TALK_TO = 1

    /** The gate's tiles: both leaves on the west edge of x [X], the left one at [Z_LEFT], the right one north of it. */
    const val X = 3268
    const val Z_LEFT = 3227
    const val Z_RIGHT = 3228

    /** How long the leaves stay open: 2004Scape's loc_add duration. */
    const val OPEN_TICKS = 3

    /** Quarter turns clockwise each leaf makes as it opens: the left one three, the right one one, so they part. */
    private const val LEFT_TURN = 3
    private const val RIGHT_TURN = 1

    internal suspend fun Dialogue.borderGuard() = with(BorderGuardLines) {
        chatPlayer(ChatAnim.QUIZ, COME_THROUGH)
        chatNpc(ChatAnim.QUIZ, FRIEND)
    }

    /** Takes the player through the gate from whichever side they're on, stepping them up to it first if they aren't there. */
    suspend fun ScriptContext.passThrough(world: World) {
        val west = player.x < X
        val z = player.z.coerceIn(Z_LEFT, Z_RIGHT)
        val front = if (west) X - 1 else X
        if (player.x != front || player.z != z) {
            world.teleport(player, front, z, player.level)
            delay(1)
        }
        swingOpen(world, LEFT, LEFT_OPEN, Z_LEFT, LEFT_TURN)
        swingOpen(world, RIGHT, RIGHT_OPEN, Z_RIGHT, RIGHT_TURN)
        world.teleport(player, if (west) X else X - 1, z, player.level)
    }

    /** Swings the leaf [closed] at [X], [z] open as [open] for [OPEN_TICKS] ticks; nothing if it's already open. */
    private fun swingOpen(world: World, closed: Int, open: Int, z: Int, turn: Int) {
        val leaf = world.locs[X, z, 0, 0]?.takeIf { it.id == closed && it.shape == LocShape.WALL_STRAIGHT } ?: return
        // A closed leaf on the west edge (angle 0) swings onto the tile to the west, as every door does (see Doors).
        val (dx, dz) = SWING[leaf.angle and 3]
        world.locs.remove(leaf, revertAfter = OPEN_TICKS)
        world.locs.add(ZoneLoc(open, leaf.shape, (leaf.angle + turn) and 3, leaf.x + dx, leaf.z + dz, leaf.level), revertAfter = OPEN_TICKS)
    }

    /** By a closed door's angle: the tile its open type goes on. */
    private val SWING = listOf(-1 to 0, 0 to 1, 1 to 0, 0 to -1)
}

/**
 * Al Kharid (the Misthalin roadmap's Al Kharid piece): the toll gate from Lumbridge and its Border Guards, and the
 * town's doors that Jagex's names don't pair: the palace's large double doors (`bankdoor_l`/`_r`, open
 * `openbankdoor_l`/`_r`) and the curtains (`desertdoorclosed`/`desertdooropen`). The spawns are in
 * npc-spawns/al-kharid.json.
 */
object AlKharid {
    const val PALACE_DOOR_LEFT = 1513
    const val PALACE_DOOR_LEFT_OPEN = 1516
    const val PALACE_DOOR_RIGHT = 1511
    const val PALACE_DOOR_RIGHT_OPEN = 1512
    const val CURTAIN = 1533
    const val CURTAIN_OPEN = 1534
}

fun registerAlKharid(world: World) {
    registerAlKharidShopkeepers(world)
    registerAlKharidTraders(world)
    Ellis.register(world)
    registerAlKharidPalace(world)
    registerAlKharidCamels(world)
    with(AlKharid) {
        world.doors.registerDouble(PALACE_DOOR_LEFT, PALACE_DOOR_LEFT_OPEN, PALACE_DOOR_RIGHT, PALACE_DOOR_RIGHT_OPEN)
        world.doors.register(CURTAIN, CURTAIN_OPEN)
    }
    with(TollGate) {
        for (leaf in listOf(LEFT, RIGHT)) {
            world.handlers.onLoc(leaf, OPEN) { passThrough(world) }
        }
        for (guard in listOf(BORDER_GUARD_WEST, BORDER_GUARD_EAST)) {
            world.handlers.onNpc(guard, TALK_TO) { op ->
                world.dialogues.start(this, op.npc) { borderGuard() }
                passThrough(world)
            }
        }
    }
}
