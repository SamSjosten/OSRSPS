package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames

/** Wilderness step W2.4: the chromatic dragonfire table (the Wiki's Dragonfire page) and the combat file's dragonfire. */
class DragonfireTest {
    @Test
    fun `bare it hits up to 50, or 30 resisted, the shield holds it to 5 and the prayer alone to 10`() {
        assertEquals(50, Dragonfire.maxHit(shield = false, prayer = false, resisted = false))
        assertEquals(30, Dragonfire.maxHit(shield = false, prayer = false, resisted = true))
        assertEquals(10, Dragonfire.maxHit(shield = false, prayer = true, resisted = false))
        assertEquals(5, Dragonfire.maxHit(shield = true, prayer = false, resisted = false))
        assertEquals(5, Dragonfire.maxHit(shield = true, prayer = true, resisted = true))
        assertEquals(DragonfireLines.BURNT, Dragonfire.line(shield = false, prayer = false, resisted = false))
        assertEquals(DragonfireLines.RESISTED, Dragonfire.line(shield = false, prayer = false, resisted = true))
        assertEquals(DragonfireLines.PRAYER, Dragonfire.line(shield = false, prayer = true, resisted = true))
        assertEquals(DragonfireLines.SHIELD, Dragonfire.line(shield = true, prayer = true, resisted = false))
    }

    private val types = NpcTypes.of(NpcType(260, "Green dragon", internalName = "green_dragon"))
    private val seqs = SeqNames(mapOf("dragon_attack" to 80, "dragon_block" to 89, "dragon_death" to 92, "dragon_firebreath_attack" to 86))

    private fun entry(type: String, fire: String) = """{ "npcs": [{ "npcs": ["green_dragon"], "attackSpeed": 4, "maxHit": 8,
        "attackType": "$type", "attackBonus": 0, "strengthBonus": 0, "range": 10,
        "defence": { "stab": 0, "slash": 40, "crush": 40, "magic": 30, "light": 50, "standard": 50, "heavy": 10 },
        "anims": { "attack": "dragon_attack", "defend": "dragon_block", "death": "dragon_death" },
        "respawnTicks": 30, "dragonfire": $fire, "source": "test" }] }"""

    @Test
    fun `the combat file's green dragons breathe one turn in 6, and dragonfire only goes on a melee NPC`() {
        val combat = NpcCombat.load(types, SeqNames.NONE)
        assertEquals(NpcDragonfire(6, -1, 1), combat[types.find("green_dragon")!!]!!.dragonfire)
        val parsed = NpcCombat.parse(entry("slash", """{ "chance": 3, "anim": "dragon_firebreath_attack" }"""), types, seqs)
        assertEquals(NpcDragonfire(3, 86, -1), parsed[types.find("green_dragon")!!]!!.dragonfire)
        assertFailsWith<IllegalArgumentException> { NpcCombat.parse(entry("slash", """{ "chance": 0 }"""), types, seqs) }
        assertFailsWith<IllegalArgumentException> { NpcCombat.parse(entry("magic", """{ "chance": 6 }"""), types, seqs) }
    }
}
