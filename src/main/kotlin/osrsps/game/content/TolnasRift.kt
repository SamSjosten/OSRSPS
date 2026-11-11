package osrsps.game.content

import osrsps.game.World
import osrsps.game.script.ScriptContext
import osrsps.map.Tile

/**
 * Misthalin piece 7 step 6b: Tolna's rift dungeon as it is after A Soul's Bane, below the rift south of the Lumber Yard.
 * Sources: the OSRS Wiki's Tolna's rift, Rift (Tolna's rift), Angry bear, Angry unicorn, Angry giant rat, Angry goblin,
 * Fear reaper, Confusion beast and Hopeless creature pages, read 2026-11-11, mejrs's live NPC list and the 241 cache.
 *
 * - The rift's rope (`soulbane_falloff2_rope_multi` 13968, which shows `soulbane_falloff2_rope` 13970 once the rope is
 *   tied, as it is for every account): Enter climbs down into the lobby, beside the rope. The rift's other holes refuse
 *   with [TolnaRiftLines.ROPE_ONLY], the Rift page's line for a hole without the rope.
 * - The rope in the lobby (`soulbane_rope_up` 13999): Climb-up puts the player back at the top, beside Tolna.
 * - The post-quest dungeon is the 64 by 64 square at x 3072 to 3135, z 5248 to 5311: the lobby in its middle, the Anger
 *   room north-west, the Fear room north-east, the Confusion room south-east and the Hopeless room south-west, all open
 *   to the lobby, from which the Wiki's Tolna's rift page has players go into any room they choose. mejrs's list saw the rooms'
 *   monsters in the dungeon's old place, 192 tiles east and 4544 north; moved by that, every one stands on open floor in
 *   its room of this square, so tolnas-rift.json puts them there. The rest of the dungeon in the cache (x 2944 to 3071 and
 *   the copies below z 5248) is the quest's: its weapon rack, fire wall, black hole, dark holes and confusing doors aren't
 *   reachable after the quest and aren't built.
 * - The monsters (npc-combat.json, npc-drops.json): aggressive, no drops but bones from the angry giant rat nearest the
 *   entrance. Any weapon hurts them and the hopeless creatures die once (inferred: the Wiki gives the rift as combat
 *   training after the quest, and the anger weapons and the three forms only for the quest).
 *
 * Guessed: the tiles the climbs land on, their animations (a ladder's) and one-tick waits, and which of the two rat ids is
 * the one nearest the entrance (soulbane_rat2, at 3094, 5286). Left out: the Varrock diary's re-entry task (no diaries
 * yet), the quest's rooms and Tolna's room.
 */
object TolnasRift {
    /** `soulbane_falloff2_rope_multi`, on the map, and `soulbane_falloff2_rope`, what it shows with the rope tied. */
    const val ROPE_RIFT = 13968
    const val ROPE_RIFT_SHOWN = 13970

    /** The rift's other holes: `soulbane_falloff1` to `_12` but the rope's (13969 is the multi's ropeless form). */
    val HOLES = listOf(13967, 13969) + (13971..13980)
    const val ENTER = 1

    /** `soulbane_rope_up`, hanging in the lobby at 3105, 5279. */
    const val ROPE_UP = 13999
    const val CLIMB_UP = 1

    /** Where Enter on the rope lands: beside the rope, south of it (guessed). */
    val LOBBY = Tile(3105, 5278)

    /** Where Climb-up lands: the open tile west of the rope, beside Tolna (guessed). */
    val TOP = Tile(3309, 3452)

    /** The post-quest dungeon: one map square. */
    val DUNGEON_X = 3072..3135
    val DUNGEON_Z = 5248..5311
}

/** The rope down or up: [anim], a tick, then [to] on level 0 (guessed, as a ladder). */
private suspend fun ScriptContext.climbRope(world: World, to: Tile, anim: Int) {
    arriveDelay()
    player.anim(anim)
    delay(1)
    world.teleport(player, to.x, to.z, 0)
}

/** The rift's rope and holes, and the rope back up from the lobby. */
fun registerTolnasRift(world: World) {
    with(TolnasRift) {
        for (rope in listOf(ROPE_RIFT, ROPE_RIFT_SHOWN)) {
            world.handlers.onLoc(rope, ENTER) { climbRope(world, LOBBY, Climbing.ANIM_PICKUP_FLOOR) }
        }
        for (hole in HOLES) world.handlers.onLoc(hole, ENTER) { world.message(player, TolnaRiftLines.ROPE_ONLY) }
        world.handlers.onLoc(ROPE_UP, CLIMB_UP) { climbRope(world, TOP, Climbing.ANIM_REACH_FOR_LADDER) }
    }
}
