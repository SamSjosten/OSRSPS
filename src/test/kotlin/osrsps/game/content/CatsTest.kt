package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatsTest {
    @Test
    fun `each colour's kitten, cat and overgrown cat have their own item and NPC`() {
        assertEquals(18, Cats.CAT_OBJS.size)
        assertEquals(18, Cats.CAT_NPCS.size)
        assertEquals((1555..1572).toSet(), Cats.CAT_OBJS, "pet kittens, cats and overgrown cats in the cache's order")
        assertEquals(Cats.Kind(Cats.Colour.GREY_BLUE, Cats.Stage.CAT), Cats.kindOfObj(1566))
        assertEquals(5596, Cats.kindOfObj(1560)?.npc)
        assertEquals(null, Cats.kindOfObj(1573))
        // Gertrude's kittens are the six kittens' items.
        assertEquals(VarrockTownsfolk.KITTENS.toSet(), Cats.KINDS.filter { it.stage == Cats.Stage.KITTEN }.map { it.obj }.toSet())
    }

    @Test
    fun `a kitten eats raw and cooked fish, but not a raw cave eel`() {
        for (name in listOf("Raw sardine", "Sardine", "Raw karambwanji", "Shark", "Poison karambwan", "Cooked karambwan", "Raw karambwan", "Cave eel")) assertTrue(Cats.isFish(name), name)
        for (name in listOf("Raw cave eel", "Raw slimy eel", "Bread", "Fish offcuts")) assertFalse(Cats.isFish(name), name)
    }

    @Test
    fun `the care timers are the Wiki's minutes`() {
        assertEquals(24 * 100, Cats.HUNGER_TICKS - Cats.HUNGRY_AT, "hungry 24 minutes after a meal")
        assertEquals(3 * 100, Cats.HUNGRY_AT - Cats.VERY_HUNGRY_AT)
        assertEquals(25 * 100, Cats.ATTENTION_TICKS - Cats.ATTENTION_AT, "25 minutes fresh")
        assertEquals(7 * 100, Cats.ATTENTION_AT - Cats.REALLY_ATTENTION_AT)
        assertEquals(18 * 100, Cats.STROKE_TICKS - Cats.ATTENTION_AT)
        assertEquals(51 * 100, Cats.WOOL_TICKS - Cats.ATTENTION_AT)
        assertEquals(3 * 60 * 100, Cats.GROWN_CYCLES * Cats.GROWTH_CYCLE_TICKS, "grown after three hours")
    }
}
