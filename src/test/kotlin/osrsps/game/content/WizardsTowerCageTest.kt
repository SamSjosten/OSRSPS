package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.map.CollisionMap
import osrsps.map.LineOfSight
import osrsps.map.LocCollision
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import osrsps.map.Reach

/**
 * Draynor step 14: the Wizards' Tower's caged lesser demon. The second floor's cage is walled as the 241 map has it:
 * the tower's internal walls on the west (`fai_wiztower_internal_walls` 12695 along 3107's east edge), its upper walls
 * on the south and east (12674), and `railing_modelled` 24097 along the north, which the cache lets projectiles through.
 * From outside, a spell or a shot sees the demon, but no one stands where a melee hit lands, and the demon can't hit
 * anyone either.
 */
class WizardsTowerCageTest {
    private val map = CollisionMap().apply { markLoaded(3110 shr 6, 3157 shr 6) }
    private val walls = LocCollisionType()
    private val railing = LocCollisionType(blocksProjectile = false)

    init {
        val collision = LocCollision(map)
        for (z in 3156..3161) collision.addLoc(walls, 3107, z, LEVEL, LocShape.WALL_STRAIGHT, 2)
        for (x in 3106..3112) collision.addLoc(walls, x, 3155, LEVEL, LocShape.WALL_STRAIGHT, 1)
        for (z in 3156..3158) collision.addLoc(walls, 3113, z, LEVEL, LocShape.WALL_STRAIGHT, 0)
        for (x in 3108..3112) collision.addLoc(railing, x, 3159, LEVEL, LocShape.WALL_STRAIGHT, 3)
    }

    /** The tiles just outside the cage on the second floor, where a player can stand beside it. */
    private val outside = (3108..3112).map { it to 3159 } + (3156..3158).map { 3113 to it } + (3156..3158).map { 3107 to it } +
        (3108..3112).map { it to 3155 }

    @Test
    fun `from just outside the railing a spell or a shot sees the demon`() {
        for (x in 3108..3112) {
            assertTrue(LineOfSight.clear(map, LEVEL, x, 3160, 1, DEMON_X, DEMON_Z, SIZE, SIZE), "from $x, 3160")
            assertTrue(LineOfSight.clear(map, LEVEL, x, 3159, 1, DEMON_X, DEMON_Z, SIZE, SIZE), "from $x, 3159")
        }
    }

    @Test
    fun `no tile outside the cage is in melee reach of the demon, either way`() {
        for ((x, z) in outside) {
            assertFalse(Reach.reachedEntity(map, LEVEL, x, z, 1, DEMON_X, DEMON_Z, SIZE, SIZE), "player at $x, $z reaches it")
            // Wherever it moves in the cage, it reaches no one outside.
            for (dx in 3108..3111) for (dz in 3156..3157) {
                assertFalse(Reach.reachedEntity(map, LEVEL, dx, dz, SIZE, x, z, 1, 1), "demon at $dx, $dz reaches $x, $z")
            }
        }
    }

    private companion object {
        const val LEVEL = 2
        const val DEMON_X = 3110
        const val DEMON_Z = 3157
        const val SIZE = 2
    }
}
