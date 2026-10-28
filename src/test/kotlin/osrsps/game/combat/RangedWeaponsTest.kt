package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.SpotanimNames

class RangedWeaponsTest {
    private val text = RangedWeapons::class.java.getResource(RangedWeapons.RESOURCE)!!.readText()
    private val root = Json.parseToJsonElement(text).jsonObject

    /** A stand-in for every item the file names, numbered from 1 in file order. */
    private val names = listOf("weapons", "ammo").flatMap { key ->
        root[key]!!.jsonArray.flatMap { entry -> entry.jsonObject["items"]!!.jsonArray.map { it.jsonPrimitive.content } }
    }
    private val objTypes = ObjTypes.of(*names.mapIndexed { i, name -> ObjType(i + 1, name, internalName = name) }.toTypedArray())

    /** Every animation and spotanim the file names, numbered from 1. */
    private fun named(vararg keys: String): Map<String, Int> {
        val all = listOf("weapons", "ammo").flatMap { list ->
            root[list]!!.jsonArray.flatMap { entry -> keys.mapNotNull { entry.jsonObject[it]?.jsonPrimitive?.content } }
        }
        return all.distinct().withIndex().associate { (i, name) -> name to i + 1 }
    }

    private val weapons = RangedWeapons.parse(text, objTypes, SeqNames(named("anim")), SpotanimNames(named("launch", "doubleLaunch", "travel")))

    private fun type(name: String) = objTypes.find(name)!!

    private fun fires(weapon: String, ammo: String) = weapons.weapon(type(weapon))!!.fires(weapons.ammo(type(ammo))!!)

    @Test
    fun `the file lists every bow, crossbow and ammunition it names`() {
        assertEquals(names.size, weapons.weaponCount + weapons.ammoCount)
        assertEquals(25, weapons.weaponCount)
        assertEquals(16, weapons.ammoCount)
    }

    @Test
    fun `each weapon fires its own tier and lower, of its own kind`() {
        assertTrue(fires("shortbow", "iron_arrow"))
        assertFalse(fires("shortbow", "steel_arrow"))
        assertTrue(fires("yew_longbow", "rune_arrow"))
        assertFalse(fires("magic_shortbow", "dragon_arrow"))
        assertTrue(fires("magic_shortbow", "amethyst_arrow"))
        assertTrue(fires("darkbow", "dragon_arrow"))
        // The Wiki's iron crossbow: "up to and including iron bolts".
        assertTrue(fires("xbows_crossbow_iron", "xbows_crossbow_bolts_blurite"))
        assertFalse(fires("xbows_crossbow_iron", "xbows_crossbow_bolts_steel"))
        assertFalse(fires("xbows_crossbow_runite", "dragon_bolts"))
        assertTrue(fires("acb", "dragon_bolts"))
        assertFalse(fires("xbows_crossbow_dragon", "dragon_arrow"))
        assertFalse(fires("darkbow", "bolt"))
    }

    @Test
    fun `ranges are the Wiki's Attack range table`() {
        assertEquals(7, weapons.weapon(type("maple_shortbow"))!!.range)
        assertEquals(10, weapons.weapon(type("maple_longbow"))!!.range)
        assertEquals(10, weapons.weapon(type("darkbow"))!!.range)
        assertEquals(7, weapons.weapon(type("xbows_crossbow_runite"))!!.range)
        assertEquals(8, weapons.weapon(type("zaryte_xbow"))!!.range)
        assertEquals(2, weapons.weapon(type("darkbow"))!!.shots)
        assertEquals(1, weapons.weapon(type("shortbow"))!!.shots)
    }

    @Test
    fun `bolts have no launch graphic, arrows have one for one and for two`() {
        val bolt = weapons.ammo(type("bolt"))!!
        assertEquals(-1, bolt.launch)
        assertTrue(bolt.travel > 0)
        val arrow = weapons.ammo(type("rune_arrow"))!!
        assertTrue(arrow.launch > 0 && arrow.doubleLaunch > 0 && arrow.doubleLaunch != arrow.launch)
    }

    @Test
    fun `without a gameval index graphics are left out, an unknown name fails, an unknown item is skipped`() {
        val bare = RangedWeapons.parse(text, objTypes, SeqNames.NONE, SpotanimNames.NONE)
        assertEquals(-1, bare.weapon(type("shortbow"))!!.anim)
        assertEquals(-1, bare.ammo(type("bronze_arrow"))!!.travel)
        val error = assertFailsWith<IllegalArgumentException> {
            RangedWeapons.parse(text, objTypes, SeqNames(named("anim")), SpotanimNames(mapOf("something_else" to 1)))
        }
        assertTrue("no spotanim called" in error.message!!, error.message)
        val fewer = RangedWeapons.parse(text, ObjTypes.of(ObjType(1, "Shortbow", internalName = "shortbow")), SeqNames.NONE, SpotanimNames.NONE)
        assertEquals(1, fewer.weaponCount)
        assertNull(fewer.ammo(type("bronze_arrow")))
    }
}
