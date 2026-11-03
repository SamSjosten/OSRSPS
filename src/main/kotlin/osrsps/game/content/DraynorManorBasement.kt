package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile

/** What the manor basement's levers, doors and coffin say. */
object ManorBasementLines : Lines("draynor_basement", "Draynor Manor basement (2004Scape's quest_haunted and quest_vampire scripts; the Wiki's Coffin (Vampyre Slayer))") {
    val PULL = line("pull", "Pull on a lever: which one, and which way it went (2004Scape)", "lever", "dir")
    val CLUNK = line("clunk", "after each pull (2004Scape)")
    val INSPECT = line("inspect", "Inspect on a lever (2004Scape)", "dir")
    val UP = line("up", "the word for a lever that is up, in the lines above")
    val DOWN = line("down", "the word for a lever that is down, in the lines above")
    val LOCKED = line("locked", "a door that won't open, which the client doesn't offer (2004Scape)")
    val COFFIN = line("coffin", "Open on Count Draynor's coffin once Vampyre Slayer is done (the Wiki, Coffin (Vampyre Slayer))")
}

/**
 * Draynor Manor's basement (Draynor piece step 13), after Ernest the Chicken and Vampyre Slayer, which every account
 * counts as done.
 *
 * - The ladder in the room behind the bookcases (`puzzle_ladder_top` 133 at 3092,3362) goes down to 3116,9754, and the
 *   one below (`puzzle_ladder` 132 at 3117,9754) back up to 3092,3361, as 2004Scape's script has them. Both put every
 *   lever back up.
 * - The six levers (`levera` 146 to `leverf` 151) and nine doors (`1to2` 137 to `5to8` 145) are multilocs on varbits
 *   `ernestlever_a` 1788 to `_f` 1793 and `ernestdoor_1to2` 1794 to `_5to8` 1802: a lever shows up or down
 *   (`levera_up`/`_down` and so on) and a door shows shut, with no option, or ajar with Open (`ernest_doorclosed` 11449,
 *   `ernest_doorajar` 11450). Which doors open for which levers is 2004Scape's (`[oploc1,_haunted_door]`, [opens]); the
 *   Wiki's solution walks through it. Pulling a lever flips it and sets the doors' varbits again.
 * - A door the player walks through moves them to its far side: each door stands on the tile east (angle 0) or north
 *   (angle 3) of the wall it fills, so the far side is the tile west or south of it, or the door's own tile, or the one
 *   past it when the door's tile can't be stood on (inferred: the shape-10 doors block their tile here).
 * - The oil can respawns in room 9 and a bronze med helm by the coffin (obj-spawns/draynor-manor.tsv).
 * - Count Draynor's coffin (46237 at 3077,9775) is sealed shut once the quest is done (the Wiki).
 *
 * Guessed: the pull animation for both ways (`human_leverdown` 834, as 2004Scape notes OSRS uses newer ones), that the
 * door move takes no time, and every line's being a game message.
 */
object DraynorManorBasement {
    const val LADDER_DOWN = 133
    const val LADDER_UP = 132
    val LADDER_DOWN_TO = Tile(3116, 9754)
    val LADDER_UP_TO = Tile(3092, 3361)
    const val CLIMB = 1

    /** Levers A to F: the multiloc ids, and their varbits (1 is down). */
    val LEVERS: List<Int> = (146..151).toList()
    val LEVER_VARBITS: List<Int> = (1788..1793).toList()
    const val PULL = 1
    const val INSPECT = 2

    /** The doors by name, their multiloc ids and varbits (1 is open). */
    enum class Door(val id: Int, val varbit: Int) {
        D1TO2(137, 1794), D2TO3(138, 1795), D4TO5(139, 1796), D5TO6(140, 1797), D8TO9(141, 1798),
        D2TO5(142, 1799), D3TO6(143, 1800), D4TO7(144, 1801), D5TO8(145, 1802),
    }
    const val OPEN = 1

    const val COFFIN = 46237
    const val COFFIN_OPEN = 1

    /** human_leverdown. */
    const val ANIM_LEVER = 834

    /**
     * Whether [door] opens with the levers at [levers] (bit 0 for A to bit 5 for F, set for down), as 2004Scape's
     * `[oploc1,_haunted_door]` has it.
     */
    fun opens(door: Door, levers: Int): Boolean {
        fun down(i: Int) = levers shr i and 1 == 1
        val (a, b, c, d, e) = listOf(down(0), down(1), down(2), down(3), down(4))
        val f = down(5)
        return when (door) {
            Door.D1TO2 -> !a && !b && d && e && f
            Door.D2TO3 -> !b && d && f
            Door.D4TO5 -> a && b && d
            Door.D5TO6 -> d
            Door.D8TO9 -> !e && f
            Door.D2TO5 -> !a && !b && c && d && !e && f
            Door.D3TO6 -> !b && d && !f
            Door.D4TO7 -> a && b && !c && !d && !e && !f
            Door.D5TO8 -> (!c && d) || (!a && !b && c && d && !e && f)
        }
    }

    /** The levers' bits for [player], or 0 where the cache hasn't the varbits (tests). */
    fun levers(player: Player): Int = LEVER_VARBITS.foldIndexed(0) { i, bits, varbit ->
        if (player.vars.types.varbit(varbit) != null && player.vars.getVarbit(varbit) == 1) bits or (1 shl i) else bits
    }

    /** Sets [player]'s levers to [bits] and each door's varbit to match. */
    fun setLevers(player: Player, bits: Int) {
        for ((i, varbit) in LEVER_VARBITS.withIndex()) {
            if (player.vars.types.varbit(varbit) != null) player.vars.setVarbit(varbit, bits shr i and 1)
        }
        for (door in Door.entries) {
            if (player.vars.types.varbit(door.varbit) != null) player.vars.setVarbit(door.varbit, if (opens(door, bits)) 1 else 0)
        }
    }

    /**
     * Where a player at [x], [z] comes out through [door]: the doors fill the west (angle 0) or south (angle 3) edge of
     * their own tile, so from that side the player goes onto the door's tile, or past it when [canStand] says it
     * can't be stood on; from the door's side, to the tile beyond the wall.
     */
    fun farSide(door: ZoneLoc, x: Int, z: Int, canStand: (Int, Int) -> Boolean): Tile {
        val west = door.angle and 3 == 0
        val outside = if (west) x < door.x else z < door.z
        if (!outside) return if (west) Tile(door.x - 1, door.z) else Tile(door.x, door.z - 1)
        if (canStand(door.x, door.z)) return Tile(door.x, door.z)
        return if (west) Tile(door.x + 1, door.z) else Tile(door.x, door.z + 1)
    }
}

private suspend fun ScriptContext.climbTo(world: World, to: Tile, level: Int, anim: Int) {
    arriveDelay()
    player.anim(anim)
    delay(1)
    DraynorManorBasement.setLevers(player, 0)
    world.teleport(player, to.x, to.z, level)
}

fun registerDraynorManorBasement(world: World) {
    with(DraynorManorBasement) {
        world.handlers.onLoc(LADDER_DOWN, CLIMB) { op -> climbTo(world, LADDER_DOWN_TO, op.loc!!.level, Climbing.ANIM_PICKUP_FLOOR) }
        world.handlers.onLoc(LADDER_UP, CLIMB) { op -> climbTo(world, LADDER_UP_TO, op.loc!!.level, Climbing.ANIM_REACH_FOR_LADDER) }
        for ((i, id) in LEVERS.withIndex()) {
            val letter = ('A' + i).toString()
            world.handlers.onLoc(id, PULL) {
                val bits = levers(player)
                val wasDown = bits shr i and 1 == 1
                setLevers(player, bits xor (1 shl i))
                player.anim(ANIM_LEVER)
                val words = world.textFor(player)
                world.message(player, ManorBasementLines.PULL, "lever" to letter, "dir" to words[if (wasDown) ManorBasementLines.UP else ManorBasementLines.DOWN])
                world.message(player, ManorBasementLines.CLUNK)
            }
            world.handlers.onLoc(id, INSPECT) {
                val down = levers(player) shr i and 1 == 1
                val words = world.textFor(player)
                world.message(player, ManorBasementLines.INSPECT, "dir" to words[if (down) ManorBasementLines.DOWN else ManorBasementLines.UP])
            }
        }
        for (door in DraynorManorBasement.Door.entries) {
            world.handlers.onLoc(door.id, OPEN) { op ->
                val loc = op.loc!!
                // The client offers Open only on an ajar door; anything else asking is checked here too.
                if (!opens(door, levers(player))) return@onLoc world.message(player, ManorBasementLines.LOCKED)
                val to = farSide(loc, player.x, player.z) { x, z -> world.canStand(x, z, loc.level) }
                world.teleport(player, to.x, to.z, loc.level)
            }
        }
        world.handlers.onLoc(COFFIN, COFFIN_OPEN) { world.message(player, ManorBasementLines.COFFIN) }
    }
}
