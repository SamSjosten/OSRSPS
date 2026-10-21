package osrsps.game.npc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NpcExaminesTest {
    @Test
    fun `the resource file has Hans and the cows`() {
        val examines = NpcExamines.load()
        assertEquals("Servant of the Duke of Lumbridge.", examines[3105])
        assertEquals("Converts grass to beef.", examines[2790])
        assertEquals("Young and tender; nearly ready for the slaughter.", examines[2794])
        assertNull(examines[1])
    }

    @Test
    fun `entries need an id, a text and a source, once each`() {
        assertFailsWith<IllegalArgumentException> { NpcExamines.parse("""{ "examines": [ { "npc": 1, "text": "Hi." } ] }""") }
        assertFailsWith<IllegalArgumentException> {
            NpcExamines.parse("""{ "examines": [ { "npc": 1, "text": "A", "source": "s" }, { "npc": 1, "text": "B", "source": "s" } ] }""")
        }
    }
}
