package osrsps.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScenarioTest {
    // Made-up items shaped like the 241 cache's.
    private val types = ObjTypes.of(
        ObjType(379, "Lobster", certlink = 380, internalName = "lobster"),
        ObjType(380, "Lobster", stackable = true, certlink = 379, certtemplate = 799, internalName = "cert_lobster"),
        ObjType(995, "Coins", stackable = true, internalName = "coins"),
        ObjType(1323, "Iron scimitar", wearpos1 = WearPos.WEAPON, internalName = "iron_scimitar"),
        ObjType(1191, "Iron kiteshield", wearpos1 = WearPos.SHIELD, internalName = "iron_kiteshield"),
        ObjType(1309, "Iron 2h sword", wearpos1 = WearPos.WEAPON, wearpos2 = WearPos.SHIELD, internalName = "iron_2h_sword"),
        ObjType(1325, "Steel scimitar", wearpos1 = WearPos.WEAPON, internalName = "steel_scimitar"),
        ObjType(884, "Iron arrow", stackable = true, wearpos1 = WearPos.AMMO, internalName = "iron_arrow"),
    )

    private fun scenario(body: String) = Scenario.parse("test", """{ "position": { "x": 3259, "z": 3263 }$body }""")

    @Test
    fun `a full scenario reads every field`() {
        val scenario = Scenario.parse(
            "cows",
            """
            {
              "about": "Cows.", "notes": "ignored",
              "position": { "x": 3259, "z": 3263, "level": 1 },
              "skills": { "att": 20, "hitpoints": { "level": 30, "current": 25 } },
              "runEnergy": 5000, "run": false,
              "inventory": [ { "item": "lobster", "count": 2 }, { "item": "995", "count": 100 } ],
              "equipment": [ { "item": "iron_scimitar" } ],
              "varps": { "43": 1 }, "varbits": { "357": 2 }
            }
            """,
        )
        assertEquals("Cows.", scenario.about)
        assertEquals(listOf(3259, 3263, 1), listOf(scenario.x, scenario.z, scenario.level))
        assertEquals(listOf(Skill.ATTACK, Skill.HITPOINTS), scenario.skills.keys.toList())
        assertEquals(20 to 20, scenario.skills.getValue(Skill.ATTACK).let { it.level to it.current })
        assertEquals(30 to 25, scenario.skills.getValue(Skill.HITPOINTS).let { it.level to it.current })
        assertEquals(5000, scenario.runEnergy)
        assertEquals(false, scenario.run)
        assertEquals(mapOf(43 to 1), scenario.varps)
        assertEquals(mapOf(357 to 2), scenario.varbits)

        val loadout = scenario.loadout(types)
        assertEquals(mapOf(0 to InvObj(379, 1), 1 to InvObj(379, 1), 2 to InvObj(995, 100)), loadout.inventory)
        assertEquals(mapOf(WearPos.WEAPON to InvObj(1323, 1)), loadout.equipment)
    }

    @Test
    fun `only the position is needed`() {
        val scenario = scenario("")
        assertNull(scenario.about)
        assertEquals(0, scenario.level)
        assertTrue(scenario.skills.isEmpty())
        assertEquals(Movement.MAX_ENERGY, scenario.runEnergy)
        assertNull(scenario.run)
        assertTrue(scenario.inventory.isEmpty() && scenario.equipment.isEmpty())
    }

    @Test
    fun `malformed files are refused with the scenario's name`() {
        val bad = listOf(
            """{ "skills": {} }""",
            """{ "position": { "x": 3259 } }""",
            """{ "position": { "x": 3259, "z": 3263, "level": 4 } }""",
            """{ "position": { "x": 3259, "z": 3263 }, "skills": { "fishing": 100 } }""",
            """{ "position": { "x": 3259, "z": 3263 }, "skills": { "cheese": 10 } }""",
            """{ "position": { "x": 3259, "z": 3263 }, "skills": { "att": 10, "attack": 20 } }""",
            """{ "position": { "x": 3259, "z": 3263 }, "runEnergy": 10001 }""",
            """{ "position": { "x": 3259, "z": 3263 }, "inventory": [ { "count": 1 } ] }""",
            """{ "position": { "x": 3259, "z": 3263 }, "inventory": [ { "item": "lobster", "count": 0 } ] }""",
            """{ "position": { "x": 3259, "z": 3263 }, "varps": { "run": 1 } }""",
            "not json",
        )
        for (text in bad) {
            val e = assertFailsWith<IllegalArgumentException>(text) { Scenario.parse("cows", text) }
            assertTrue(e.message!!.startsWith("Scenario cows: "), e.message)
        }
        assertFailsWith<IllegalArgumentException> { Scenario.parse("Cows", """{ "position": { "x": 3259, "z": 3263 } }""") }
    }

    @Test
    fun `loadouts that can't be put on are refused`() {
        fun problem(body: String) = assertFailsWith<IllegalArgumentException> { scenario(body).loadout(types) }.message

        assertEquals("The cache has no item 'dragon_claws'", problem(""", "inventory": [ { "item": "dragon_claws" } ]"""))
        assertEquals("29 x Lobster don't fit in the inventory", problem(""", "inventory": [ { "item": "lobster", "count": 29 } ]"""))
        assertEquals("Lobster (379) can't be worn", problem(""", "equipment": [ { "item": "lobster" } ]"""))
        assertEquals("Only one Iron scimitar can be worn", problem(""", "equipment": [ { "item": "iron_scimitar", "count": 2 } ]"""))
        assertEquals(
            "Iron scimitar and Steel scimitar both go in worn slot 3",
            problem(""", "equipment": [ { "item": "iron_scimitar" }, { "item": "steel_scimitar" } ]"""),
        )
        assertEquals(
            "Iron 2h sword is two-handed, so no shield can be worn with it",
            problem(""", "equipment": [ { "item": "iron_2h_sword" }, { "item": "iron_kiteshield" } ]"""),
        )
    }

    @Test
    fun `stackable items wear as a stack and stack in the inventory`() {
        val loadout = scenario(
            """, "inventory": [ { "item": "coins", "count": 5 }, { "item": "coins", "count": 10 } ],
                "equipment": [ { "item": "iron_arrow", "count": 500 } ]""",
        ).loadout(types)
        assertEquals(mapOf(0 to InvObj(995, 15)), loadout.inventory)
        assertEquals(mapOf(WearPos.AMMO to InvObj(884, 500)), loadout.equipment)
    }

    @Test
    fun `bank items take a slot each, tabs first in order, and the tab sizes come with them`() {
        val loadout = scenario(
            """, "bank": [ { "item": "coins", "count": 1000 }, { "item": "lobster", "count": 3, "tab": 2 },
                { "item": "iron_scimitar", "tab": 1 }, { "item": "steel_scimitar", "tab": 2 } ]""",
        ).loadout(types)
        assertEquals(
            mapOf(0 to InvObj(1323, 1), 1 to InvObj(379, 3), 2 to InvObj(1325, 1), 3 to InvObj(995, 1000)),
            loadout.bank,
        )
        assertEquals(listOf(1, 2, 0, 0, 0, 0, 0, 0, 0), loadout.bankTabs)
        fun problem(body: String) = assertFailsWith<IllegalArgumentException> { scenario(body).loadout(types) }.message
        assertEquals("The bank lists an item twice: the bank stacks everything", problem(""", "bank": [ { "item": "coins" }, { "item": "995" } ]"""))
        assertEquals("Bank tabs must be 1 up with none left out", problem(""", "bank": [ { "item": "coins", "tab": 2 } ]"""))
        // An account's bank holds 900, though its inventory has 1,410 slots.
        val many = ObjTypes.of(*Array(901) { ObjType(10_000 + it, "Thing") })
        val tooMany = (0 until 901).joinToString(", ") { """{ "item": "${it + 10_000}" }""" }
        assertEquals(
            "The bank holds at most 900 items",
            assertFailsWith<IllegalArgumentException> { scenario(""", "bank": [ $tooMany ]""").loadout(many) }.message,
        )
        // A note is banked as its item, so it and the item are the same entry twice.
        assertEquals(mapOf(0 to InvObj(379, 4)), scenario(""", "bank": [ { "item": "cert_lobster", "count": 4 } ]""").loadout(types).bank)
        assertEquals(
            "The bank lists an item twice: the bank stacks everything",
            problem(""", "bank": [ { "item": "lobster" }, { "item": "cert_lobster" } ]"""),
        )
        assertEquals(
            "Scenario test: inventory entry 1: only bank items go in a tab",
            assertFailsWith<IllegalArgumentException> { scenario(""", "inventory": [ { "item": "coins", "tab": 1 } ]""") }.message,
        )
    }

    @Test
    fun `every scenario file in the resources reads`() {
        val scenarios = Scenarios.load()
        assertEquals(Scenarios.FILES.toSet(), scenarios.names)
        val cows = scenarios["lumbridge-cows"]!!
        // The cow field, by the cows in npc-spawns/lumbridge.json.
        assertTrue(cows.x in 3253..3265 && cows.z in 3255..3298 && cows.level == 0)
        assertTrue(cows.equipment.isNotEmpty() && cows.inventory.isNotEmpty())
        val bare = scenarios["lumbridge-bare"]!!
        assertTrue(bare.equipment.isEmpty() && bare.inventory.isEmpty() && bare.skills.isEmpty())
        val tabs = scenarios["lumbridge-bank-tabs"]!!
        assertEquals(listOf(0, 1, 1), tabs.bank.map { it.tab })
    }
}
