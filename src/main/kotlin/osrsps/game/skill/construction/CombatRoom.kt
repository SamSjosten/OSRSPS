package osrsps.game.skill.construction

import osrsps.game.Player
import osrsps.game.PlayerEffects
import osrsps.game.World
import osrsps.game.WearPos
import osrsps.game.content.arriveDelay
import osrsps.game.script.ScriptContext
import osrsps.game.skill.Agility
import osrsps.game.zone.ZoneLoc
import kotlin.math.abs
import kotlin.math.max

/**
 * Construction step CON6a (the combat room): what its built furniture does before anyone fights, from the Wiki's Combat
 * room, Glove rack, Weapons rack, Extra weapons rack and Boxing gloves pages (read 2026-11-26). Building it is
 * [Furniture]'s.
 *
 * - Racks: Search gives the rack's practice kit, free and without end, as the kitchen's larders do ([takeSupplies]).
 *   The glove rack has the red and the blue boxing gloves; the weapons rack adds the wooden shortsword and wooden
 *   shield; the extra weapons rack adds the pugel. Each stays in the house ([HOUSE_ONLY]): the Boxing gloves page says
 *   they "are destroyed if a player tries to leave a house with them"; that the rest are the same is INFERRED from
 *   their house-only names (poh_wooden_sword, poh_wooden_shield, poh_pugel).
 * - The rings' ropes (Climb-over) and the ranging pedestals' magic barriers (Walk-through) take the player across, from
 *   whichever side they are on. Climbing plays `human_get_over_combatring_nogloves`, or the `_redgloves` or `_bluegloves`
 *   one with those gloves worn (the cache's names). GUESSED: the crossing takes [CROSS_TICKS]; the barrier has no
 *   animation.
 *
 * Next (CON6b, CON6c): the combat dummy's Attach and Attack, and fighting in the rings, the pedestals and on the beam.
 * Left out: the ornate dummy's Swap (its other kinds).
 */
class CombatRoom(private val world: World, private val houses: Houses) {
    fun register() {
        for ((loc, kit) in RACKS) {
            world.handlers.onLoc(loc, Kitchen.SEARCH) { op ->
                arriveDelay()
                takeSupplies(world, houses, this, op.loc ?: return@onLoc, kit)
            }
        }
        for (loc in ROPES + BARRIERS) {
            world.handlers.onLoc(loc, CROSS) { op ->
                arriveDelay()
                cross(this, op.loc ?: return@onLoc)
            }
        }
    }

    /** Across [loc], a rope or a barrier, to the tile on its other side: from the side [context]'s player is nearer. */
    private suspend fun cross(context: ScriptContext, loc: ZoneLoc) {
        val player = context.player
        if (houses.houseAt(loc.x, loc.z) == null) return
        val (a, b) = sides(loc) ?: return
        val from = if (distance(player, a) <= distance(player, b)) a else b
        val to = if (from == a) b else a
        if (player.x != from.first || player.z != from.second) {
            if (distance(player, from) > 1) return
            world.teleport(player, from.first, from.second, loc.level)
            context.delay(1)
        }
        if (!world.locs.exists(loc) || houses.houseAt(loc.x, loc.z) == null) return
        player.waypoints.clear()
        if (loc.id in ROPES) player.anim(climbAnim(player))
        val angle = Agility.angle(to.first - from.first, to.second - from.second)
        player.effects.exactMove(PlayerEffects.ExactMove(from.first, from.second, to.first, to.second, 0, CROSS_TICKS * 30, angle))
        context.delay(CROSS_TICKS)
        world.teleport(player, to.first, to.second, loc.level)
    }

    private fun distance(player: Player, tile: Pair<Int, Int>) = max(abs(player.x - tile.first), abs(player.z - tile.second))

    companion object {
        /** The racks' Search and the ropes' Climb-over and the barriers' Walk-through: op 1. */
        const val CROSS = 1

        /** GUESSED: how long climbing over a rope or walking through a barrier takes. */
        const val CROSS_TICKS = 2

        const val RED_GLOVES = 7671
        const val BLUE_GLOVES = 7673
        const val WOODEN_SWORD = 7675
        const val WOODEN_SHIELD = 7676
        const val PUGEL = 7679

        /** `human_get_over_combatring_nogloves`, `_redgloves`, `_bluegloves`. */
        const val CLIMB_ANIM = 3688
        const val CLIMB_RED_ANIM = 3689
        const val CLIMB_BLUE_ANIM = 3690

        /** The glove rack, weapons rack and extra weapons rack, and what each gives (the racks' Wiki pages). */
        val RACKS: Map<Int, List<Int>> = mapOf(
            13381 to listOf(RED_GLOVES, BLUE_GLOVES),
            13382 to listOf(RED_GLOVES, BLUE_GLOVES, WOODEN_SWORD, WOODEN_SHIELD),
            13383 to listOf(RED_GLOVES, BLUE_GLOVES, WOODEN_SWORD, WOODEN_SHIELD, PUGEL),
        )

        /** The boxing, fencing and combat rings' ropes, each with Climb-over (poh_boxing_ringwall_*, poh_fencing_ringwall, poh_combat_ringwall). */
        val ROPES: Set<Int> = setOf(13129, 13130, 13131, 13132, 13133, 13137)

        /** The ranging pedestals' magic barriers, with Walk-through (poh_magic_circle_wall and _off_wall). */
        val BARRIERS: Set<Int> = setOf(13145, 13146)

        /** What the combat room's racks give that can't leave the house. */
        val HOUSE_ONLY: Set<Int> = setOf(RED_GLOVES, BLUE_GLOVES, WOODEN_SWORD, WOODEN_SHIELD, PUGEL)

        /** The climb's animation for what [player] wears on their hands: the red or the blue boxing gloves, or neither. */
        fun climbAnim(player: Player): Int = when (player.equipment[WearPos.WEAPON]?.id) {
            RED_GLOVES -> CLIMB_RED_ANIM
            BLUE_GLOVES -> CLIMB_BLUE_ANIM
            else -> CLIMB_ANIM
        }

        /**
         * The two tiles [loc] stands between: a straight wall (shape 0) between its tile and the next one on its side
         * (angle 0 west, 1 north, 2 east, 3 south); a corner post (shape 3) between its tile and the diagonal one at its
         * corner (0 north-west, 1 north-east, 2 south-east, 3 south-west). Null for any other shape.
         */
        fun sides(loc: ZoneLoc): Pair<Pair<Int, Int>, Pair<Int, Int>>? {
            val (dx, dz) = when (loc.shape) {
                WALL_STRAIGHT -> when (loc.angle) { 0 -> -1 to 0; 1 -> 0 to 1; 2 -> 1 to 0; else -> 0 to -1 }
                WALL_CORNER -> when (loc.angle) { 0 -> -1 to 1; 1 -> 1 to 1; 2 -> 1 to -1; else -> -1 to -1 }
                else -> return null
            }
            return (loc.x to loc.z) to (loc.x + dx to loc.z + dz)
        }

        private const val WALL_STRAIGHT = 0
        private const val WALL_CORNER = 3
    }
}

/** Every obj a house gives that stays in it: taken from whoever leaves with it, carried or worn ([Houses]). */
object HouseOnly {
    val OBJS: Set<Int> = Kitchen.HOUSE_ONLY + CombatRoom.HOUSE_ONLY
}
