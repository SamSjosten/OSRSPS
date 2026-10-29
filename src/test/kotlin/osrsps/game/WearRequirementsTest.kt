package osrsps.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WearRequirementsTest {
    private val pegasian = ObjType(13237, "Pegasian boots", wearpos1 = WearPos.FEET, internalName = "pegasian_boots")
    private val types = ObjTypes.of(pegasian)

    @Test
    fun `a line holds any number of levels, the combat level among them`() {
        val requirements = WearRequirements.parse("pegasian_boots\tdefence=75 ranged=75 combat=40\tPegasian boots", types)
        assertEquals(
            listOf(WearRequirement(Skill.DEFENCE, 75), WearRequirement(Skill.RANGED, 75), WearRequirement(null, 40)),
            requirements[pegasian],
        )
    }

    @Test
    fun `an item the cache doesn't have is skipped and a bad level fails the file`() {
        assertEquals(0, WearRequirements.parse("no_such_item\tattack=40\tNowhere", types).size)
        assertFailsWith<IllegalArgumentException> { WearRequirements.parse("pegasian_boots\tdefence=100\tPegasian boots", types) }
        assertFailsWith<IllegalArgumentException> { WearRequirements.parse("pegasian_boots\tcooking\tPegasian boots", types) }
        assertFailsWith<IllegalArgumentException> { WearRequirements.parse("pegasian_boots\tsharpness=5\tPegasian boots", types) }
    }

    @Test
    fun `the shipped table reads, every line naming its Wiki page`() {
        val text = WearRequirements::class.java.getResource(WearRequirements.RESOURCE)!!.readText()
        WearRequirements.parse(text, ObjTypes.of())
        val lines = text.lines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue(lines.size > 2000, "${lines.size} lines")
        for (line in lines) assertEquals(3, line.split('\t').size, line)
        assertEquals(lines.size, lines.map { it.substringBefore('\t') }.toSet().size, "an item listed twice")
    }
}
