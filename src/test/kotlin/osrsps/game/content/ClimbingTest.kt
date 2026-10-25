package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.World
import osrsps.game.content.LumbridgeCastle.CELLAR_LADDER
import osrsps.game.content.LumbridgeCastle.KITCHEN_TRAPDOOR
import osrsps.game.content.LumbridgeCastle.LADDER_BOTTOM
import osrsps.game.content.LumbridgeCastle.LADDER_MIDDLE
import osrsps.game.content.LumbridgeCastle.LADDER_TOP
import osrsps.game.content.LumbridgeCastle.STAIRS_BOTTOM
import osrsps.game.content.LumbridgeCastle.STAIRS_MIDDLE
import osrsps.game.content.LumbridgeCastle.STAIRS_TOP
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocShape

class ClimbingTest {
    private fun stairs(id: Int, angle: Int, x: Int, z: Int, level: Int) = ZoneLoc(id, LocShape.CENTREPIECE_STRAIGHT, angle, x, z, level)

    // The castle's two staircases, placed as the 241 cache has them: the bottom and middle ones 2x2, the top one 1x1.
    private val southBottom = stairs(STAIRS_BOTTOM, 3, 3204, 3207, 0)
    private val southMiddle = stairs(STAIRS_MIDDLE, 3, 3204, 3207, 1)
    private val southTop = stairs(STAIRS_TOP, 3, 3205, 3208, 2)
    private val northBottom = stairs(STAIRS_BOTTOM, 0, 3204, 3229, 0)
    private val northMiddle = stairs(STAIRS_MIDDLE, 0, 3204, 3229, 1)
    private val northTop = stairs(STAIRS_TOP, 0, 3205, 3229, 2)

    @Test
    fun `going up the castle's stairs comes out on the same tile on every floor`() {
        assertEquals(Destination(3205, 3209, 1), Climbing.stairsUp(southBottom, 2, 2))
        assertEquals(Destination(3205, 3209, 2), Climbing.stairsUp(southBottom, 2, 2, levels = 2))
        assertEquals(Destination(3205, 3209, 2), Climbing.stairsUp(southMiddle, 2, 2))
        assertEquals(Destination(3206, 3229, 1), Climbing.stairsUp(northBottom, 2, 2))
        assertEquals(Destination(3206, 3229, 2), Climbing.stairsUp(northMiddle, 2, 2))
    }

    @Test
    fun `going down the castle's stairs comes out beside them on the other side`() {
        assertEquals(Destination(3206, 3208, 1), Climbing.stairsDown(southTop, 1, 1))
        assertEquals(Destination(3206, 3208, 0), Climbing.stairsDown(southTop, 1, 1, levels = 2))
        assertEquals(Destination(3206, 3208, 0), Climbing.stairsDown(southMiddle, 2, 2))
        assertEquals(Destination(3205, 3228, 1), Climbing.stairsDown(northTop, 1, 1))
        assertEquals(Destination(3205, 3228, 0), Climbing.stairsDown(northMiddle, 2, 2))
    }

    @Test
    fun `stairs turned a quarter swap their width and length`() {
        // 1 wide and 3 long before rotation. At angle 2 the 3 tiles run along z, and going down steps past their end;
        // at angle 1 they run along x, and going down steps 3 tiles west.
        assertEquals(Destination(3200, 3203, 0), Climbing.stairsDown(stairs(1, 2, 3200, 3200, 1), 1, 3))
        assertEquals(Destination(3197, 3200, 0), Climbing.stairsDown(stairs(1, 1, 3200, 3200, 1), 1, 3))
    }

    @Test
    fun `every castle climbing option has a handler`() {
        val world = World(IntArray(0), CollisionMap())
        val options = mapOf(
            STAIRS_BOTTOM to 1..2, STAIRS_MIDDLE to 1..3, STAIRS_TOP to 1..2,
            LADDER_BOTTOM to 1..1, LADDER_MIDDLE to 1..3, LADDER_TOP to 1..1,
            KITCHEN_TRAPDOOR to 1..1, CELLAR_LADDER to 1..1,
        )
        for ((id, ops) in options) {
            for (option in ops) assertTrue(world.handlers.find(Op(OpTarget.LOC, id, option)) != null, "loc $id op $option")
        }
    }
}
