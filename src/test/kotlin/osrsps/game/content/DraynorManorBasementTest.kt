package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.content.DraynorManorBasement.Door
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile

/** Draynor step 13: which basement doors open for which levers (2004Scape), walked through the Wiki's solution. */
class DraynorManorBasementTest {
    private fun bits(vararg down: Char) = down.fold(0) { acc, c -> acc or (1 shl (c - 'A')) }

    private fun open(levers: Int) = Door.entries.filter { DraynorManorBasement.opens(it, levers) }.toSet()

    @Test
    fun `with every lever up no door opens`() {
        assertEquals(emptySet(), open(0))
    }

    @Test
    fun `the Wiki's solution opens the doors it walks through, ending at door 9`() {
        // Ernest the Chicken: pull B, then A, down; door 1.
        assertEquals(setOf(Door.D4TO7), open(bits('A', 'B')))
        // D down; doors 2 and 3.
        assertEquals(setOf(Door.D4TO5, Door.D5TO6, Door.D5TO8), open(bits('A', 'B', 'D')))
        // B and A up; doors 3, 4 and 5.
        assertEquals(setOf(Door.D5TO6, Door.D5TO8, Door.D3TO6), open(bits('D')))
        // E and F down; doors 6 and 7.
        assertTrue(open(bits('D', 'E', 'F')).containsAll(setOf(Door.D1TO2, Door.D2TO3, Door.D5TO6)))
        // C down; doors 7 and 6 back.
        assertTrue(open(bits('C', 'D', 'E', 'F')).containsAll(setOf(Door.D1TO2, Door.D2TO3, Door.D5TO6)))
        // E up; doors 6, 8 and 3, then door 9 and the oil can.
        val last = open(bits('C', 'D', 'F'))
        assertTrue(last.containsAll(setOf(Door.D2TO5, Door.D5TO8, Door.D8TO9, Door.D2TO3)), last.toString())
    }

    @Test
    fun `a door moves the player to its far side`() {
        val west = ZoneLoc(Door.D1TO2.id, 10, 0, 3105, 9765, 0)
        // From the west the player goes onto the door's tile, or past it when it can't be stood on.
        assertEquals(Tile(3105, 9765), DraynorManorBasement.farSide(west, 3104, 9765) { _, _ -> true })
        assertEquals(Tile(3106, 9765), DraynorManorBasement.farSide(west, 3104, 9765) { _, _ -> false })
        assertEquals(Tile(3104, 9765), DraynorManorBasement.farSide(west, 3106, 9765) { _, _ -> false })
        val south = ZoneLoc(Door.D2TO5.id, 10, 3, 3102, 9763, 0)
        assertEquals(Tile(3102, 9764), DraynorManorBasement.farSide(south, 3102, 9762) { _, _ -> false })
        assertEquals(Tile(3102, 9762), DraynorManorBasement.farSide(south, 3102, 9764) { _, _ -> false })
    }
}
