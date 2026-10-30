package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames

class NpcCombatTest {
    // The castle's targets as the 241 cache names them; all have level 1 everywhere but Hitpoints.
    private val names = listOf(
        "man", "man2", "man3", "man4", "woman", "woman2", "woman3", "rat", "imp",
        "cow", "cow2", "cow3", "cow2_calf", "cow3_calf", "calf",
        "chicken", "chicken_brown", "ramunsheered", "ramunsheered2", "ramunsheered3", "duck", "duck_female", "duckling",
    ) + (1..8).map { "goblin_unarmed_melee_$it" } + "giantspider1" + listOf(
        "giantrat", "giantrat_grey", "giantrat_grey2", "giantrat_grey3", "giantrat1_2", "giantrat1_3",
        "little_frog_nodrops", "medium_frog_nodrops", "giant_frog_nodrops", "goblin_armed", "unicorn",
        "al_kharid_man", "al_kharid_warrior", "scorpion", "shantay_guard",
    )
    // The cache's Strength levels where they aren't 1, for the max hit check.
    private val strength = mapOf(
        "giantrat" to 3, "giantrat_grey" to 3, "giantrat_grey2" to 3, "giantrat_grey3" to 3, "giantrat1_2" to 5, "giantrat1_3" to 5,
        "little_frog_nodrops" to 4, "medium_frog_nodrops" to 6, "giant_frog_nodrops" to 8, "unicorn" to 13,
        "al_kharid_warrior" to 5, "scorpion" to 12, "shantay_guard" to 16,
    )
    private val types = NpcTypes.of(
        *names.mapIndexed { i, name -> NpcType(1000 + i, name, internalName = name, stats = NpcStats(strength = strength[name] ?: 1)) }.toTypedArray(),
    )

    // The 241 cache's ids for the animations the file names.
    private val seqs = SeqNames(
        mapOf(
            "human_unarmedpunch" to 422, "human_unarmedblock" to 424, "human_death" to 836,
            "mouse_attack" to 2705, "mouse_defend" to 2706, "mouse_death" to 2707,
            "imp_attack" to 169, "imp_block" to 170, "imp_death" to 172,
            "cow_update_attack" to 5849, "cow_update_defend" to 5850, "cow_update_death" to 5851,
            "chicken_attack" to 55, "chicken_block" to 56, "chicken_death" to 57,
            "sheep_attack" to 250, "sheep_block" to 251, "sheep_death" to 252, "duck_rework_death" to 3468,
            "slice_surface_goblin_squat_unarmed_attack" to 6184, "slice_surface_goblin_defend" to 6183, "slice_surface_goblin_death" to 6182,
            "spider_update_attack" to 5327, "spider_update_defend" to 5328, "spider_update_death" to 5329,
            "giant_rat_update_attack" to 4933, "giant_rat_update_defend" to 4934, "giant_rat_update_death" to 4935,
            "giant_frog_attack" to 1793, "giant_frog_block" to 1794, "giant_frog_death" to 1795,
            "slice_surface_goblin_squat_attack_spear" to 6188, "slice_surface_goblin_defend_spear" to 6189, "slice_surface_goblin_death_spear" to 6190,
            "unicorn_rework_attack" to 6376, "unicorn_rework_defend" to 6375, "unicorn_rework_death" to 6377,
            "human_sword_slash" to 390, "human_sword_block" to 387, "human_sword_stab" to 386,
            "scorpion_update_attack_tail" to 6254, "scorpion_update_defend" to 6255, "scorpion_update_death" to 6256,
        ),
    )

    @Test
    fun `the resource gives every castle target its Wiki numbers`() {
        val combat = NpcCombat.load(types, seqs)
        assertEquals(names.size, combat.size)
        // No attack or block animation for a duck: null in the file.
        assertEquals(NpcCombatAnims(-1, -1, 3468), combat[types.find("duckling")!!]!!.anims)
        val man = combat[types.find("man")!!]!!
        assertEquals(4, man.attackSpeed)
        assertEquals(AttackType.CRUSH, man.attackType)
        assertEquals(-21, man.defence.against(AttackType.SLASH))
        assertEquals(NpcCombatAnims(422, 424, 836), man.anims)
        assertEquals(25, man.respawnTicks)
        assertEquals(AttackType.STAB, combat[types.find("imp")!!]!!.attackType)
        assertEquals(2, combat[types.find("rat")!!]!!.respawnTicks)
        // The imp and the rams alone have an elemental weakness among them (OSRS Wiki: the others say "No elemental weakness").
        assertEquals(ElementalWeakness(Element.WATER, 10), combat[types.find("imp")!!]!!.weakness)
        assertEquals(ElementalWeakness(Element.FIRE, 20), combat[types.find("ramunsheered")!!]!!.weakness)
        assertEquals(ElementalWeakness(Element.FIRE, 50), combat[types.find("giantspider1")!!]!!.weakness)
        assertEquals(
            listOf("imp", "ramunsheered", "ramunsheered2", "ramunsheered3", "giantspider1", "little_frog_nodrops", "medium_frog_nodrops", "giant_frog_nodrops"),
            names.filter { combat[types.find(it)!!]!!.weakness != null },
        )
        assertEquals(ElementalWeakness(Element.EARTH, 40), combat[types.find("little_frog_nodrops")!!]!!.weakness)
        assertEquals(ElementalWeakness(Element.EARTH, 25), combat[types.find("giant_frog_nodrops")!!]!!.weakness)
        // Fred's farm: a chicken stabs for nothing and comes back in 25 ticks, a ram crushes for 1 and takes 45.
        val chicken = combat[types.find("chicken_brown")!!]!!
        assertEquals(AttackType.STAB, chicken.attackType)
        assertEquals(0, chicken.maxHit)
        assertEquals(NpcCombatAnims(55, 56, 57), chicken.anims)
        assertEquals(25, chicken.respawnTicks)
        val ram = combat[types.find("ramunsheered2")!!]!!
        assertEquals(1, ram.maxHit)
        assertEquals(45, ram.respawnTicks)
        // East of the river: every goblin crushes for 1 and comes back in 35 ticks; the giant spider stabs, back in 30.
        for (i in 1..8) {
            val goblin = combat[types.find("goblin_unarmed_melee_$i")!!]!!
            assertEquals(AttackType.CRUSH, goblin.attackType)
            assertEquals(1, goblin.maxHit)
            assertEquals(-15, goblin.defence.against(AttackType.STAB))
            assertEquals(NpcCombatAnims(6184, 6183, 6182), goblin.anims)
            assertEquals(35, goblin.respawnTicks)
        }
        val spider = combat[types.find("giantspider1")!!]!!
        assertEquals(AttackType.STAB, spider.attackType)
        assertEquals(NpcCombatAnims(5327, 5328, 5329), spider.anims)
        assertEquals(30, spider.respawnTicks)
        // The swamp: giant rats stab for 1 and come back in 30 ticks, frogs crush and take 50, the level 5 goblin is
        // slower (6) with its spear, and the unicorn takes 90.
        val rat = combat[types.find("giantrat1_3")!!]!!
        assertEquals(AttackType.STAB, rat.attackType)
        assertEquals(NpcCombatAnims(4933, 4934, 4935), rat.anims)
        assertEquals(30, rat.respawnTicks)
        val frog = combat[types.find("medium_frog_nodrops")!!]!!
        assertEquals(AttackType.CRUSH, frog.attackType)
        assertEquals(2, frog.maxHit)
        assertEquals(NpcCombatAnims(1793, 1794, 1795), frog.anims)
        assertEquals(50, frog.respawnTicks)
        val goblin = combat[types.find("goblin_armed")!!]!!
        assertEquals(6, goblin.attackSpeed)
        assertEquals(12, goblin.attackBonus)
        assertEquals(NpcCombatAnims(6188, 6189, 6190), goblin.anims)
        assertEquals(90, combat[types.find("unicorn")!!]!!.respawnTicks)
        // Al Kharid: its man is any man; the warrior slashes for 2, the scorpion stabs for 2 and comes back in 35 ticks.
        assertEquals(man, combat[types.find("al_kharid_man")!!]!!)
        val warrior = combat[types.find("al_kharid_warrior")!!]!!
        assertEquals(AttackType.SLASH, warrior.attackType)
        assertEquals(2, warrior.maxHit)
        assertEquals(15, warrior.defence.against(AttackType.SLASH))
        assertEquals(NpcCombatAnims(390, 387, 836), warrior.anims)
        assertEquals(25, warrior.respawnTicks)
        val scorpion = combat[types.find("scorpion")!!]!!
        assertEquals(AttackType.STAB, scorpion.attackType)
        assertEquals(2, scorpion.maxHit)
        assertEquals(NpcCombatAnims(6254, 6255, 6256), scorpion.anims)
        assertEquals(35, scorpion.respawnTicks)
        // The Shantay Guard stabs for 3 and comes back in 50 ticks.
        val guard = combat[types.find("shantay_guard")!!]!!
        assertEquals(AttackType.STAB, guard.attackType)
        assertEquals(3, guard.maxHit)
        assertEquals(50, guard.respawnTicks)
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
        fun weak(weakness: String) = entry.format("\"man\"", "crush").replace("\"respawnTicks\"", "\"weakness\": $weakness, \"respawnTicks\"")
        val fire = NpcCombat.parse("""{ "npcs": [${weak("""{ "element": "fire", "percent": 30 }""")}] }""", types, seqs)
        assertEquals(ElementalWeakness(Element.FIRE, 30), fire[types.find("man")!!]!!.weakness)
        for (bad in listOf("""{ "element": "lava", "percent": 30 }""", """{ "element": "fire", "percent": 0 }""", """{ "element": "fire" }""")) {
            assertFailsWith<IllegalArgumentException>(bad) { NpcCombat.parse("""{ "npcs": [${weak(bad)}] }""", types, seqs) }
        }
    }
}
