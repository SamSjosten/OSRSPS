package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames

class NpcCombatTest {
    // The castle's targets as the 241 cache names them; all have level 1 everywhere but Hitpoints.
    private val names = listOf(
        "man", "man2", "man3", "man4", "woman", "woman2", "woman3", "rat", "imp",
        "cow", "cow2", "cow3", "cow2_calf", "cow3_calf", "calf",
    )
    private val types = NpcTypes.of(*names.mapIndexed { i, name -> NpcType(1000 + i, name, internalName = name) }.toTypedArray())

    // The 241 cache's ids for the animations the file names.
    private val seqs = SeqNames(
        mapOf(
            "human_unarmedpunch" to 422, "human_unarmedblock" to 424, "human_death" to 836,
            "mouse_attack" to 2705, "mouse_defend" to 2706, "mouse_death" to 2707,
            "imp_attack" to 169, "imp_block" to 170, "imp_death" to 172,
            "cow_update_attack" to 5849, "cow_update_defend" to 5850, "cow_update_death" to 5851,
        ),
    )

    @Test
    fun `the resource gives every castle target its Wiki numbers`() {
        val combat = NpcCombat.load(types, seqs)
        assertEquals(names.size, combat.size)
        val man = combat[types.find("man")!!]!!
        assertEquals(4, man.attackSpeed)
        assertEquals(AttackType.CRUSH, man.attackType)
        assertEquals(-21, man.defence.against(AttackType.SLASH))
        assertEquals(NpcCombatAnims(422, 424, 836), man.anims)
        assertEquals(25, man.respawnTicks)
        assertEquals(AttackType.STAB, combat[types.find("imp")!!]!!.attackType)
        assertEquals(2, combat[types.find("rat")!!]!!.respawnTicks)
    }

    @Test
    fun `each max hit in the file is what the formula gives from the levels`() {
        val combat = NpcCombat.load(types, seqs)
        for (name in names) {
            val type = types.find(name)!!
            val def = combat[type]!!
            assertEquals(CombatMath.npcMaxHit(type.stats.strength, def.strengthBonus), def.maxHit, name)
        }
    }

    @Test
    fun `with no animation names, every NPC fights without animations`() {
        val combat = NpcCombat.load(types, SeqNames.NONE)
        assertEquals(NpcCombatAnims.NONE, combat[types.find("cow")!!]!!.anims)
    }

    @Test
    fun `an NPC the cache lacks is skipped, a bad entry fails the file`() {
        val entry = """{ "npcs": [%s], "attackSpeed": 4, "maxHit": 1, "attackType": "%s", "attackBonus": 0, "strengthBonus": 0,
            "defence": { "stab": 0, "slash": 0, "crush": 0, "magic": 0, "light": 0, "standard": 0, "heavy": 0 },
            "anims": { "attack": "human_unarmedpunch", "defend": "human_unarmedblock", "death": "human_death" },
            "respawnTicks": 25, "source": "test" }"""
        val skipped = NpcCombat.parse("""{ "npcs": [${entry.format("\"man\", \"goblin\"", "crush")}] }""", types, seqs)
        assertEquals(1, skipped.size)
        assertNull(skipped[types.find("woman")!!])
        val badType = assertFailsWith<IllegalArgumentException> {
            NpcCombat.parse("""{ "npcs": [${entry.format("\"man\"", "poke")}] }""", types, seqs, "test.json")
        }
        assertTrue("entry 1" in badType.message!! && "poke" in badType.message!!, badType.message)
        assertFailsWith<IllegalArgumentException> {
            NpcCombat.parse("""{ "npcs": [${entry.format("\"man\"", "crush")}, ${entry.format("\"man\"", "crush")}] }""", types, seqs)
        }
        assertFailsWith<IllegalArgumentException> {
            NpcCombat.parse("""{ "npcs": [${entry.format("\"man\"", "crush")}] }""", types, SeqNames(mapOf("other" to 1)))
        }
    }
}
