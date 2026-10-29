package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.SpotanimNames

class SpellsTest {
    // Spell objs shaped like the 241 cache's: 601 name, 604 level, 596 the packed spellbook component, rune and count
    // pairs from 365, 1935 the base max hit.
    private val fireStrike = spellObj(3279, "13_fire_strike", "Fire Strike", 13, 19, 8, MIND to 1, FIRE to 3, AIR to 2)
    private val windStrike = spellObj(3273, "01_wind_strike", "Wind Strike", 1, 11, 2, MIND to 1, AIR to 1)
    private val runes = listOf(AIR to "airrune", FIRE to "firerune", MIND to "mindrune").map { (id, name) -> ObjType(id, name, stackable = true, internalName = name) }
    private val staffOfFire = ObjType(1387, "Staff of fire", internalName = "staff_of_fire")
    private val objTypes = ObjTypes.of(fireStrike, windStrike, staffOfFire, *runes.toTypedArray())

    private val seqs = SeqNames(mapOf("human_caststrike" to 711, "human_caststrike_staff" to 1162))
    private val spotanims = SpotanimNames(
        mapOf("firestrike_casting" to 99, "firestrike_travel" to 100, "firestrike_impact" to 101, "failedspell_impact" to 85),
    )

    private fun file(spells: String = FIRE_STRIKE, staves: String = """{ "items": ["staff_of_fire"], "runes": ["firerune"] }""") =
        """{ "splash": "failedspell_impact", "spells": [ $spells ], "staves": [ $staves ] }"""

    @Test
    fun `a spell's level, runes, max hit and component come from its obj, the rest from the file`() {
        val spells = Spells.parse(file(), objTypes, seqs, spotanims)
        val spell = spells.spell(19)!!
        assertEquals(CombatSpell(3279, "Fire Strike", 19, 13, 8, listOf(RuneCost(MIND, 1), RuneCost(FIRE, 3), RuneCost(AIR, 2)), 115, 711, 1162, 99, 100, 101), spell)
        assertNull(spells.spell(11))
        assertEquals(85, spells.splash)
        assertEquals(setOf(FIRE), spells.runesFrom(staffOfFire.id))
        assertEquals(emptySet(), spells.runesFrom(null))
        assertEquals(emptySet(), spells.runesFrom(FIRE))
        // No "autocast": a spell the autocast menu doesn't list.
        assertEquals(-1, spell.autocast)
    }

    @Test
    fun `a spell's autocast key picks it from the autocast menu`() {
        val spells = Spells.parse(file(FIRE_STRIKE.replace("\"xp\"", "\"autocast\": 4, \"xp\"")), objTypes, seqs, spotanims)
        assertEquals(4, spells.spell(19)!!.autocast)
        assertEquals(spells.spell(19), spells.autocast(4))
        assertNull(spells.autocast(1))
    }

    @Test
    fun `without gameval names the graphics and animations are left out`() {
        val spell = Spells.parse(file(), objTypes, SeqNames.NONE, SpotanimNames.NONE).spell(19)!!
        assertEquals(listOf(-1, -1, -1, -1, -1), listOf(spell.anim, spell.staffAnim, spell.casting, spell.travel, spell.impact))
    }

    @Test
    fun `spells, staves and runes the cache doesn't have are skipped`() {
        val spells = Spells.parse(
            file(
                spells = "$FIRE_STRIKE, " + FIRE_STRIKE.replace("13_fire_strike", "99_unknown_spell"),
                staves = """{ "items": ["staff_of_fire", "fire_battlestaff"], "runes": ["firerune"] },
                    { "items": ["staff_of_water"], "runes": ["waterrune"] }""",
            ),
            objTypes, seqs, spotanims,
        )
        assertEquals(1, spells.size)
        assertEquals(setOf(FIRE), spells.runesFrom(staffOfFire.id))
    }

    @Test
    fun `a bad file fails with where it went wrong`() {
        fun fails(text: String, expected: String) {
            val e = assertFailsWith<IllegalArgumentException> { Spells.parse(text, objTypes, seqs, spotanims) }
            assertTrue(expected in e.message.orEmpty(), e.message)
        }
        fails(file(FIRE_STRIKE.replace("11.5", "11.55")), "spells entry 1: xp 11.55 isn't a whole number of tenths")
        fails(file(FIRE_STRIKE.replace("firestrike_impact", "nothing")), "spells entry 1: no spotanim called nothing")
        fails(file(FIRE_STRIKE.replace("\"human_caststrike\"", "\"nothing\"")), "spells entry 1: no animation called nothing")
        fails(file("$FIRE_STRIKE, $FIRE_STRIKE"), "spells entry 2: component 19 is listed twice")
        val wind = WIND_STRIKE.replace("\"xp\"", "\"autocast\": 4, \"xp\"")
        fails(file(FIRE_STRIKE.replace("\"xp\"", "\"autocast\": 4, \"xp\"") + ", " + wind), "spells entry 2: autocast key 4 is listed twice")
        fails(file(staves = """{ "items": ["staff_of_fire", "staff_of_fire"], "runes": ["firerune"] }"""), "staves entry 1: Staff of fire (1387) is listed twice")
        // An obj that isn't a spell has none of the params.
        fails(file(FIRE_STRIKE.replace("13_fire_strike", "staff_of_fire")), "spells entry 1: staff_of_fire has no spell name")
    }

    @Test
    fun `the real file names only what the 241 cache has, and every spell in it`() {
        // Every name was checked against the cache's gameval dumps when the file was written; here the file must at
        // least parse with nothing known, and list the twenty standard combat spells.
        val text = Spells::class.java.getResource(Spells.RESOURCE)!!.readText()
        assertEquals(20, Regex("\"spell\":").findAll(text).count())
        // Enum 1986's keys for the twenty: 1 to 16 the strikes to waves, 48 to 51 the surges.
        assertEquals((1..16).toList() + (48..51).toList(), Regex("\"autocast\": (\\d+)").findAll(text).map { it.groupValues[1].toInt() }.toList())
        // Each spell's element is the one in its name, wind for air.
        for (match in Regex("\"spell\": \"\\d+_(\\w+?)_\\w+\", \"autocast\": \\d+, \"element\": \"(\\w+)\"").findAll(text)) {
            val (word, element) = match.destructured
            assertEquals(if (word == "wind") "air" else word, element, match.value)
        }
        assertEquals(20, Regex("\"element\":").findAll(text).count())
        assertEquals(0, Spells.parse(text, ObjTypes.of(), SeqNames.NONE, SpotanimNames.NONE).size)
    }

    private fun spellObj(id: Int, internalName: String, name: String, level: Int, component: Int, maxHit: Int, vararg runes: Pair<Int, Int>): ObjType {
        val params = HashMap<Int, Any>()
        params[Spells.PARAM_NAME] = name
        params[Spells.PARAM_LEVEL] = level
        params[Spells.PARAM_COMPONENT] = (Spells.SPELLBOOK shl 16) or component
        params[Spells.PARAM_MAX_HIT] = maxHit
        runes.forEachIndexed { i, (rune, count) ->
            val (runeParam, countParam) = Spells.PARAM_RUNES[i]
            params[runeParam] = rune
            params[countParam] = count
        }
        return ObjType(id, name, params = params, internalName = internalName)
    }

    companion object {
        const val AIR = 556
        const val FIRE = 554
        const val MIND = 558

        const val WIND_STRIKE = """{ "spell": "01_wind_strike", "xp": 5.5, "anim": "human_caststrike", "staffAnim": "human_caststrike_staff",
            "casting": "firestrike_casting", "travel": "firestrike_travel", "impact": "firestrike_impact" }"""

        const val FIRE_STRIKE = """{ "spell": "13_fire_strike", "xp": 11.5, "anim": "human_caststrike", "staffAnim": "human_caststrike_staff",
            "casting": "firestrike_casting", "travel": "firestrike_travel", "impact": "firestrike_impact" }"""
    }
}
