package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.map.Tile

/** Where the sewers' east entrance puts a climbing player (the exploit review's round 11, R11C-2). */
class DraynorSewersLandingTest {
    private val trapdoor = DraynorSewers.EAST.trapdoor
    private val ladder = DraynorSewers.EAST.ladder

    @Test
    fun `a free tile keeps the player's side, and the trapdoor and ladder are 6399 apart`() {
        assertEquals(6399, ladder.z - trapdoor.z)
        assertEquals(Tile(3119, 9643), DraynorSewers.landing(trapdoor, ladder, 3119, 3244) { _, _ -> true })
        assertEquals(Tile(3118, 3245), DraynorSewers.landing(ladder, trapdoor, 3118, 9644) { _, _ -> true })
    }

    @Test
    fun `a blocked tile falls back to the first free one south, north, west or east`() {
        val blocked = setOf(Tile(3119, 9643), Tile(3118, 9642))
        assertEquals(Tile(3118, 9644), DraynorSewers.landing(trapdoor, ladder, 3119, 3244) { x, z -> Tile(x, z) !in blocked })
    }

    @Test
    fun `a player standing further off still lands beside the other end, never on it`() {
        assertEquals(Tile(3119, 9644), DraynorSewers.landing(trapdoor, ladder, 3125, 3250) { _, _ -> true })
        // Standing on the trapdoor's own tile (a teleport during the walk) comes out south of the ladder.
        assertEquals(Tile(3118, 9642), DraynorSewers.landing(trapdoor, ladder, 3118, 3244) { _, _ -> true })
    }

    @Test
    fun `with every tile blocked the player comes out south of it`() {
        assertEquals(Tile(3118, 9642), DraynorSewers.landing(trapdoor, ladder, 3119, 3244) { _, _ -> false })
    }
}
