package osrsps.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WearRequirementsTest {
    private val pegasian = ObjType(13237, "Pegasian boots", wearpos1 = WearPos.FEET, internalName = "pegasian_boots")
    private val pickaxe = ObjType(1273, "Mithril pickaxe", wearpos1 = WearPos.WEAPON, internalName = "mithril_pickaxe")
    private val types = ObjTypes.of(pegasian, pickaxe)

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

    @Test
    fun `a tool needs the level to wield it, not the level to use it`() {
        // The Wiki's Pickaxe article: 20 Attack to wield a mithril pickaxe, 21 Mining to mine with it.
        assertEquals(listOf(WearRequirement(Skill.ATTACK, 20)), WearRequirements.load(types)[pickaxe])
    }

    @Test
    fun `every crossbow and bolt in the shipped table needs Ranged, not Attack`() {
        // The iron crossbow read attack=26; the Wiki gives 26 Ranged (third review, 30 Oct 2026).
        val text = WearRequirements::class.java.getResource(WearRequirements.RESOURCE)!!.readText()
        val crossbows = text.lines().filter { it.startsWith("xbows_crossbow") }
        assertTrue(crossbows.size > 10, "${crossbows.size} lines")
        for (line in crossbows) {
            val levels = line.split('\t')[1]
            assertTrue("ranged=" in levels && "attack=" !in levels, line)
        }
    }

    @Test
    fun `gem-tipped bolts need their metal's Ranged level, enchanted or not`() {
        // Exploit sweep round 25 (R25B-2): pearl bolts had no row once they could be fired. Emerald bolts read 46 and
        // diamond 61 where their mithril and adamant metal (and their enchanted rows) need 36 and 46; the Wiki's Emerald
        // bolts page gives 36.
        val text = WearRequirements::class.java.getResource(WearRequirements.RESOURCE)!!.readText()
        val levels = text.lines().filter { !it.startsWith("#") && '\t' in it }.associate { it.split('\t').let { (name, level) -> name to level } }
        val metals = mapOf(
            "pearl_bolt" to 26, "xbows_crossbow_bolts_iron_tipped_pearl_enchanted" to 26,
            "xbows_crossbow_bolts_mithril_tipped_emerald" to 36, "xbows_crossbow_bolts_mithril_tipped_emerald_enchanted" to 36,
            "xbows_crossbow_bolts_adamantite_tipped_diamond" to 46, "xbows_crossbow_bolts_adamantite_tipped_diamond_enchanted" to 46,
        )
        for ((name, level) in metals) assertEquals("ranged=$level", levels[name], name)
    }
}
