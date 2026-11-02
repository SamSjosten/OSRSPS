package osrsps.game.encounter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import osrsps.game.NpcType

class EncounterDefTest {
    private val boss = NpcType(16659, "Archive Guardian")

    @Test
    fun `an encounter needs a one-word name, phases with names of their own, and cycles that take time`() {
        assertEquals("Bad encounter name 'two words'", assertFailsWith<IllegalArgumentException> { EncounterDef("two words", boss, listOf(PhaseDef("a"))) }.message)
        assertEquals("x has no phases", assertFailsWith<IllegalArgumentException> { EncounterDef("x", boss, emptyList()) }.message)
        assertEquals("x has two phases with the same name", assertFailsWith<IllegalArgumentException> { EncounterDef("x", boss, listOf(PhaseDef("a"), PhaseDef("a"))) }.message)
        assertEquals("a's cycle takes no time", assertFailsWith<IllegalArgumentException> { PhaseDef("a", cycle = listOf(CycleStep(0) {})) }.message)
        assertEquals("a ends at 100%", assertFailsWith<IllegalArgumentException> { PhaseDef("a", endsAtPercent = 100) }.message)
        assertEquals("a ends after 0 ticks", assertFailsWith<IllegalArgumentException> { PhaseDef("a", endsAfter = 0) }.message)
        assertFailsWith<IllegalArgumentException> { CycleStep(-1) {} }
    }

    @Test
    fun `phases are found by name, ignoring case`() {
        val def = EncounterDef("x", boss, listOf(PhaseDef("Flying"), PhaseDef("Grounded")))
        assertEquals(1, def.phase("grounded"))
        assertNull(def.phase("enrage"))
    }
}
