package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.script.QueueType
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile

/** Leela's lines once Prince Ali Rescue is done (the Wiki's Transcript:Leela, read 2026-10-30). */
object LeelaLines : Lines("leela", "Transcript:Leela") {
    val FRIEND = line("friend", "after Prince Ali Rescue: Leela's first line")
    val HERE = line("here", "the player's answer")
    val POINT = line("point", "Leela's second line")
    val LUCK = line("luck", "the player's last line")
}

/** The jail's cell door. */
object JailLines : Lines("draynor_jail", "Draynor Village jail") {
    val LOCKED = line("locked", "opening the cell door from outside (2004Scape's quest_prince script)")
}

/**
 * The jail east of the village and Leela's talk (Draynor piece step 5), after Prince Ali Rescue, which every account
 * counts as done.
 *
 * - The four jail guards (`jailguard` 4276 and its three `…_patrol` types) fight as npc-combat.json has them and drop
 *   bones; the 241 types have Attack only (the Wiki: Talk-to went with Beneath Cursed Sands). They're aggressive, as
 *   the Wiki has them ([osrsps.game.combat.Aggression]).
 * - Joe, Lady Keli and the jailed Prince Ali are gone after the quest: the 241 cache's `joe`, `lady_keli` and
 *   `prince_ali_prison` show nothing once varp 273 is past 100, 40 and 90. None is spawned.
 * - The cell door (`alidoor` 2881) lets the player out from inside and is locked from outside, as 2004Scape's script
 *   has it: opened, it swings as the posh door (`poshdooropen` 1541) does, the player walks through, and it shuts
 *   behind them. Guessed: the swing and the two ticks open. The bronze key that unlocked it during the quest isn't
 *   handled (no account has one).
 *
 * Every chathead expression is guessed.
 */
object DraynorJail {
    const val LEELA = 4274
    const val TALK_TO = 1

    const val CELL_DOOR = 2881
    const val CELL_DOOR_OPEN = 1541
    const val OPEN = 1

    /** How long the cell door stands open while the player walks through. Guessed. */
    const val OPEN_TICKS = 2

    internal suspend fun Dialogue.leela(): Unit = with(LeelaLines) {
        chatNpc(ChatAnim.HAPPY, FRIEND)
        chatPlayer(ChatAnim.HAPPY, HERE)
        chatNpc(ChatAnim.NEUTRAL, POINT)
        chatPlayer(ChatAnim.HAPPY, LUCK)
    }

    /** Whether the player stands on the cell's side of [door], which is the tile it is on (it is on the tile's north wall). */
    fun inside(door: ZoneLoc, x: Int, z: Int): Boolean = z <= door.z && x == door.x
}

fun registerDraynorJail(world: World) {
    with(DraynorJail) {
        world.handlers.onNpc(LEELA, TALK_TO) { op -> world.dialogues.start(this, op.npc) { leela() } }
        world.handlers.onLoc(CELL_DOOR, OPEN) { op -> cellDoor(world, op.loc!!) }
    }
}

private suspend fun ScriptContext.cellDoor(world: World, door: ZoneLoc) {
    if (!DraynorJail.inside(door, player.x, player.z)) {
        world.message(player, JailLines.LOCKED)
        return
    }
    val opened = world.doors.openAs(door, DraynorJail.CELL_DOOR_OPEN)
    player.waypoints.clear()
    if (player.z < door.z) player.waypoints.add(Tile(door.x, door.z))
    player.waypoints.add(Tile(door.x, door.z + 1))
    // Shut by a soft queue, not after a delay: a delayed player doesn't walk.
    queue("draynor_cell_door", QueueType.SOFT, DraynorJail.OPEN_TICKS) {
        if (world.locs.exists(opened)) world.doors.closeAs(opened, DraynorJail.CELL_DOOR)
    }
}
