package osrsps.game.skill.magic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.combat.RuneCost
import osrsps.game.combat.Spells

/** `skills/bolt-enchant.json` against the OSRS Wiki's Enchant Crossbow Bolt table (read 2026-11-10). */
class BoltEnchantDataTest {
    private val text = BoltEnchantData::class.java.getResource(BoltEnchantData.RESOURCE)!!.readText()

    /** Every name the file has, as an obj; the spell objs with one cosmic rune. */
    private fun objTypes(): ObjTypes {
        val names = Regex("\"([0-9a-z_+]+)\"").findAll(text).map { it.groupValues[1] }
            .filter { it.contains("bolt") }.distinct().toList()
        val objs = names.mapIndexed { i, name ->
            val params = if (name.first().isDigit()) mapOf<Int, Any>(Spells.PARAM_RUNES[0].first to COSMIC, Spells.PARAM_RUNES[0].second to 1) else emptyMap()
            ObjType(100 + i, name, params = params, internalName = name)
        }
        return ObjTypes(arrayOfNulls<ObjType>(100 + objs.size).also { array -> objs.forEach { array[it.id] = it } })
    }

    @Test
    fun `the ten enchantments have the Wiki's levels and XP, in its order`() {
        val data = BoltEnchantData.parse(text, objTypes())
        assertEquals(
            listOf(4 to 90, 7 to 175, 14 to 190, 24 to 295, 27 to 370, 29 to 330, 49 to 590, 57 to 670, 68 to 780, 87 to 970),
            data.enchantments.map { it.level to it.xpTenths },
        )
        assertEquals(listOf(2, 2, 2, 2, 2, 2, 2, 3, 2, 3), data.enchantments.map { it.bolts.size }, "dragon bolts for all, ascension for diamond and onyx")
        assertEquals(listOf(RuneCost(COSMIC, 1)), data.enchantments[0].runes)
    }

    @Test
    fun `a spell name that doesn't start with its level fails the file`() {
        val bad = text.replace("\"spell\": \"04_enchant_bolt_opal\", \"level\": 4", "\"spell\": \"04_enchant_bolt_opal\", \"level\": 5")
        assertFailsWith<IllegalArgumentException> { BoltEnchantData.parse(bad, objTypes()) }
    }

    private companion object {
        const val COSMIC = 564
    }
}
