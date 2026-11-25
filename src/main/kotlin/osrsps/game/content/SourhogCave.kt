package osrsps.game.content

import osrsps.game.World

/**
 * The Sourhog Cave east of Draynor Manor after A Porcine of Interest (post-quest step 8):
 *
 * - The strange hole by the River Lum ([HOLE], the map's multiloc on varbit 10582, which the quest's end value 40 shows
 *   as the roped hole 40309): Climb-down takes the player into the cave, below its exit rope.
 * - The exit rope ([ROPE], 3157,9714): Climb-up takes them back out beside the hole.
 * - The blockage ([BLOCKAGE], 3156,9704) between the rope and the sourhogs: an Agility shortcut in skills/agility.json.
 * - The eight sourhogs ([SOURHOG], npc-spawns/sourhog-cave.json): their fight in npc-combat.json, their spit burning the
 *   eyes of a player without reinforced goggles or a Slayer helmet ([osrsps.game.combat.NpcBlinding]); their drops in
 *   npc-drops.json; Spria's sourhog task (task 121) kills them.
 *
 * Sources: the OSRS Wiki's Sourhog Cave, Strange hole (Sourhog Cave), Sourhog and A Porcine of Interest pages, read
 * 2026-11-19; ids, varbit and tiles from the 242 cache. Inferred from the 242 map: the landing tiles, the one below the
 * rope (3157,9713) and the one south of the hole (3150,3346). Guessed: the climbing animations (the ladder's) and their
 * tick. Left out: the quest's skeleton and cutscene (the skeleton has no option after the quest), and the bronze
 * scimitar that spawns by it for the quest.
 */
object SourhogCave {
    /** porcine_hole: the map's multiloc. */
    const val HOLE = 40341
    /** porcine_cave_exit_rope. */
    const val ROPE = 40330
    /** porcine_cave_blockage. */
    const val BLOCKAGE = 40331
    /** sourhog. */
    const val SOURHOG = 10435

    const val CLIMB = 1

    /** The tile below the exit rope, where Climb-down lands. */
    val CAVE = DorgeshuunTunnels.Spot(3157, 9713)
    /** The tile south of the hole, where Climb-up lands. */
    val SURFACE = DorgeshuunTunnels.Spot(3150, 3346)
}

fun registerSourhogCave(world: World) = with(SourhogCave) {
    world.handlers.onLoc(HOLE, CLIMB) {
        arriveDelay()
        player.anim(Climbing.ANIM_PICKUP_FLOOR)
        delay(1)
        world.teleport(player, CAVE.x, CAVE.z, CAVE.level)
    }
    world.handlers.onLoc(ROPE, CLIMB) {
        arriveDelay()
        player.anim(Climbing.ANIM_REACH_FOR_LADDER)
        delay(1)
        world.teleport(player, SURFACE.x, SURFACE.z, SURFACE.level)
    }
}
