package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.NpcStat
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.Skill
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
        "al_kharid_man", "al_kharid_warrior", "scorpion", "shantay_guard", "spider", "guard1", "monk",
    ) + VARROCK_GUARDS + OUTLAWS + BARBARIANS.keys + "gunthor_the_brave" + (1..4).map { "fai_barbarian_goblin_armed_$it" }
    // The cache's Strength levels where they aren't 1, for the max hit check.
    private val strength = mapOf(
        "giantrat" to 3, "giantrat_grey" to 3, "giantrat_grey2" to 3, "giantrat_grey3" to 3, "giantrat1_2" to 5, "giantrat1_3" to 5,
        "little_frog_nodrops" to 4, "medium_frog_nodrops" to 6, "giant_frog_nodrops" to 8, "unicorn" to 13,
        "al_kharid_warrior" to 5, "scorpion" to 12, "shantay_guard" to 16, "guard1" to 18, "monk" to 2,
    ) + VARROCK_GUARDS.associateWith { 18 } + OUTLAWS.associateWith { 30 } + BARBARIANS + ("gunthor_the_brave" to 22)
    private val types = NpcTypes.of(
        *names.mapIndexed { i, name -> NpcType(1000 + i, name, internalName = name, stats = NpcStats(strength = strength[name] ?: 1)) }.toTypedArray(),
    )

    // The 241 cache's ids for the animations the file names.
    private val seqs = SeqNames(
        mapOf(
            "human_unarmedpunch" to 422, "human_unarmedblock" to 424, "human_death" to 836,
            "human_caststrike" to 711, "human_castconfuse" to 716,
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
            "human_blunt_pound" to 401, "human_shield_defence" to 1156,
            "scorpion_update_attack_tail" to 6254, "scorpion_update_defend" to 6255, "scorpion_update_death" to 6256,
            "zombie_update_attack_normal" to 5568, "zombie_update_defend_normal" to 5567, "zombie_update_death_normal" to 5569,
            "zombie_update_attack_weapon" to 5571, "zombie_update_defend_weapon" to 5574, "zombie_update_death_weapon" to 5575,
            "zombie_update_attack_dragging" to 5578, "zombie_update_defend_dragging" to 5579, "zombie_update_death_dragging" to 5580,
            "human_dhsword_chop" to 406, "human_dhsword_block" to 410, "barrow_dharok_crush" to 2067,
            "human_staff_pound" to 413, "human_staff_block" to 415, "human_bow" to 426,
            "skeleton_update_attack_weapon" to 5485, "skeleton_update_attack_sword" to 5487, "skeleton_update_defend" to 5489, "skeleton_update_death" to 5491,
            "giant_update_basic_attack" to 4652, "giant_update_basic_defend" to 4651, "giant_update_basic_death" to 4653,
            "hobgoblin_attack" to 163, "hobgoblin_attackunarmed" to 164, "hobgoblin_block" to 165, "hobgoblin_death" to 167,
            "ghost_update_normal_attack" to 5532, "ghost_update_normal_defend" to 5533, "ghost_update_normal_death" to 5534,
            "bear_rework_attack" to 4925, "bear_rework_defend" to 4927, "bear_rework_death" to 4929,
            "npc_archive_guardian_range_attack01" to 14751, "npc_archive_guardian_melee_attack01" to 14752, "npc_archive_guardian_death01" to 14749,
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
            listOf("imp", "ramunsheered", "ramunsheered2", "ramunsheered3", "giantspider1", "little_frog_nodrops", "medium_frog_nodrops", "giant_frog_nodrops", "spider"),
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
        // Varrock's guards crush for 3 and Edgeville's stab for 3, each back in 50 ticks.
        for (name in VARROCK_GUARDS) {
            val guard = combat[types.find(name)!!]!!
            assertEquals(AttackType.CRUSH, guard.attackType)
            assertEquals(3, guard.maxHit)
            assertEquals(30, guard.defence.against(AttackType.SLASH))
            // They hold a longsword and a kiteshield: they swing the sword (human_sword_slash) and block with the shield
            // (human_shield_defence), not a mace's pound and block.
            assertEquals(NpcCombatAnims(390, 1156, 836), guard.anims)
            assertEquals(50, guard.respawnTicks)
        }
        val edgeville = combat[types.find("guard1")!!]!!
        assertEquals(AttackType.STAB, edgeville.attackType)
        assertEquals(3, edgeville.maxHit)
        assertEquals(18, edgeville.defence.against(AttackType.STAB))
        assertEquals(NpcCombatAnims(386, 387, 836), edgeville.anims)
        assertEquals(50, edgeville.respawnTicks)
        // The outlaws punch for 3 with no defence bonuses, come back in 2 ticks and give a fortieth of the XP (-97.5%).
        for (name in OUTLAWS) {
            val outlaw = combat[types.find(name)!!]!!
            assertEquals(AttackType.CRUSH, outlaw.attackType)
            assertEquals(3, outlaw.maxHit)
            assertEquals(-21, outlaw.strengthBonus)
            assertEquals(0, outlaw.defence.against(AttackType.CRUSH))
            assertEquals(NpcCombatAnims(422, 424, 836), outlaw.anims)
            assertEquals(2, outlaw.respawnTicks)
            assertEquals(-975, outlaw.xpBonusTenths)
            assertNull(outlaw.aggression)
        }
        // The monastery's monks punch for 1 and come back in 25 ticks.
        val monk = combat[types.find("monk")!!]!!
        assertEquals(AttackType.CRUSH, monk.attackType)
        assertEquals(1, monk.maxHit)
        assertEquals(NpcCombatAnims(422, 424, 836), monk.anims)
        assertEquals(25, monk.respawnTicks)
        assertTrue((names - OUTLAWS.toSet()).all { combat[types.find(it)!!]!!.xpBonusTenths == 0 })
    }

    @Test
    fun `Draynor's dark wizards cast Water Strike and Confuse from 10 tiles`() {
        val wizard = NpcType(512, "Dark wizard", internalName = "young_dark_wizard")
        val def = NpcCombat.load(NpcTypes.of(wizard), seqs)[wizard]!!
        assertEquals(AttackType.MAGIC, def.attackType)
        assertEquals(10, def.range)
        assertEquals(2, def.maxHit)
        assertEquals(3, def.defence.against(AttackType.MAGIC))
        assertEquals(
            listOf(
                NpcRangedAttack(711, 93, 94, 95),
                NpcRangedAttack(716, 102, 103, 104, maxHit = 0, drain = StatDrain(Skill.ATTACK, 5)),
            ),
            def.attacks,
        )
    }

    @Test
    fun `the Draynor Sewers' zombies and skeletons fight as the Wiki has them, each max hit the formula's`() {
        // The 241 cache's Strength levels and the Wiki's: zombies 9 and 21, skeletons 18 and 24.
        val sewer = mapOf(
            "zombie_unarmed_sewer1" to 9, "zombie_unarmed_sewer2" to 9, "zombie_unarmed_sewer3" to 9,
            "zombie_armed_sewer1" to 21, "zombie_armed_sewer2" to 21, "zombie_armed_sewer3" to 21, "zombie_armed_sewer4" to 21,
            "skeleton_unarmed" to 18, "skeleton_unarmed3" to 18, "skeleton_armed" to 24, "skeleton_armed3" to 24,
        )
        val sewerTypes = NpcTypes.of(*sewer.entries.mapIndexed { i, (name, str) -> NpcType(2000 + i, name, internalName = name, stats = NpcStats(strength = str)) }.toTypedArray())
        val combat = NpcCombat.load(sewerTypes, seqs)
        assertEquals(sewer.size, combat.size)
        fun def(name: String) = combat[sewerTypes.find(name)!!]!!
        for (name in sewer.keys) {
            val type = sewerTypes.find(name)!!
            assertEquals(CombatMath.npcMaxHit(type.stats.strength, def(name).strengthBonus), def(name).maxHit, name)
            assertTrue(def(name).aggression != null, "$name is aggressive")
        }
        assertEquals(listOf(2, 3, 3, 4), listOf("zombie_unarmed_sewer1", "zombie_armed_sewer1", "skeleton_unarmed", "skeleton_armed").map { def(it).maxHit })
        assertEquals(AttackType.CRUSH, def("zombie_unarmed_sewer2").attackType)
        assertEquals(AttackType.SLASH, def("zombie_armed_sewer3").attackType)
        assertEquals(5, def("zombie_armed_sewer1").attackSpeed)
        assertEquals(NpcCombatAnims(5568, 5567, 5569), def("zombie_unarmed_sewer3").anims)
        assertEquals(NpcCombatAnims(5571, 5574, 5575), def("zombie_armed_sewer2").anims)
        assertEquals(NpcCombatAnims(5578, 5579, 5580), def("zombie_armed_sewer4").anims)
        assertEquals(NpcCombatAnims(5487, 5489, 5491), def("skeleton_armed3").anims)
        assertEquals(ElementalWeakness(Element.FIRE, 50), def("zombie_armed_sewer4").weakness)
        assertEquals(ElementalWeakness(Element.EARTH, 35), def("skeleton_unarmed3").weakness)
        assertEquals(listOf(35, 70, 60), listOf("zombie_unarmed_sewer1", "skeleton_unarmed", "skeleton_armed").map { def(it).respawnTicks })
        assertEquals(-5, def("skeleton_unarmed").defence.against(AttackType.CRUSH))
        assertEquals(15, def("skeleton_armed").attackBonus)
    }

    @Test
    fun `the Edgeville Dungeon's zombies, skeletons and hobgoblins fight as the Wiki has them, each max hit the formula's`() {
        // The 241 cache's Strength levels: zombies 13, skeletons 18, 17 and 24, hobgoblins 24 and 31.
        val dungeon = mapOf(
            "zombie2" to 13, "zombie2_b" to 13, "zombie2_c" to 13,
            "skeleton_unarmed2" to 18, "skeleton_unarmed4" to 18,
            "skeleton_unagressive" to 17, "skeleton_unagressive2" to 17, "skeleton_unagressive3" to 17,
            "skeleton_armed2" to 24, "skeleton_armed4" to 24, "skeleton_armed5" to 24,
            "hobgoblin_unarmed" to 24, "hobgoblin_armed" to 31,
            "giant" to 22, "giant4" to 22, "giant6" to 22,
        )
        val dungeonTypes = NpcTypes.of(*dungeon.entries.mapIndexed { i, (name, str) -> NpcType(2200 + i, name, internalName = name, stats = NpcStats(strength = str)) }.toTypedArray())
        val combat = NpcCombat.load(dungeonTypes, seqs)
        assertEquals(dungeon.size, combat.size)
        fun def(name: String) = combat[dungeonTypes.find(name)!!]!!
        for (name in dungeon.keys) {
            val type = dungeonTypes.find(name)!!
            assertEquals(CombatMath.npcMaxHit(type.stats.strength, def(name).strengthBonus), def(name).maxHit, name)
            assertTrue(def(name).aggression != null, "$name is aggressive")
        }
        assertEquals(listOf(2, 3, 3, 4, 3, 5), listOf("zombie2", "skeleton_unarmed4", "skeleton_unagressive", "skeleton_armed5", "hobgoblin_unarmed", "hobgoblin_armed").map { def(it).maxHit })
        assertEquals(AttackType.SLASH, def("zombie2_b").attackType)
        assertEquals(AttackType.CRUSH, def("skeleton_unagressive2").attackType)
        assertEquals(AttackType.STAB, def("hobgoblin_armed").attackType)
        assertEquals(listOf(5, 4, 6), listOf("zombie2_c", "hobgoblin_unarmed", "hobgoblin_armed").map { def(it).attackSpeed })
        assertEquals(NpcCombatAnims(5578, 5579, 5580), def("zombie2").anims)
        assertEquals(NpcCombatAnims(5568, 5567, 5569), def("zombie2_b").anims)
        assertEquals(NpcCombatAnims(5571, 5574, 5575), def("zombie2_c").anims)
        assertEquals(NpcCombatAnims(5485, 5489, 5491), def("skeleton_unagressive3").anims)
        assertEquals(NpcCombatAnims(164, 165, 167), def("hobgoblin_unarmed").anims)
        assertEquals(NpcCombatAnims(163, 165, 167), def("hobgoblin_armed").anims)
        assertEquals(ElementalWeakness(Element.FIRE, 50), def("zombie2").weakness)
        assertEquals(ElementalWeakness(Element.EARTH, 35), def("skeleton_unagressive").weakness)
        assertNull(def("hobgoblin_armed").weakness)
        assertEquals(listOf(35, 35, 70, 60, 35), listOf("zombie2", "skeleton_unagressive", "skeleton_unarmed2", "skeleton_armed4", "hobgoblin_armed").map { def(it).respawnTicks })
        assertEquals(1, def("hobgoblin_armed").defence.against(AttackType.STAB))
        assertEquals(8, def("hobgoblin_armed").attackBonus)
        // Step 11's hill giants: 22 Strength and +16 make 4.
        assertEquals(4, def("giant4").maxHit)
        assertEquals(6, def("giant").attackSpeed)
        assertEquals(AttackType.CRUSH, def("giant6").attackType)
        assertEquals(NpcCombatAnims(4652, 4651, 4653), def("giant").anims)
        assertEquals(ElementalWeakness(Element.EARTH, 25), def("giant4").weakness)
        assertEquals(30, def("giant6").respawnTicks)
    }

    @Test
    fun `Draynor Manor's ghosts and closet skeleton fight as the Wiki has them, each max hit the formula's`() {
        // The 241 cache's Strength levels and the Wiki's: the ghosts 13, the skeleton 18.
        val manor = mapOf(
            "house_ghost" to 13, "house_ghost2" to 13, "house_ghost3" to 13, "house_ghost4" to 13, "house_ghost5" to 13,
            "draynor_skeleton" to 18,
        )
        val manorTypes = NpcTypes.of(*manor.entries.mapIndexed { i, (name, str) -> NpcType(2100 + i, name, internalName = name, stats = NpcStats(strength = str)) }.toTypedArray())
        val combat = NpcCombat.load(manorTypes, seqs)
        assertEquals(manor.size, combat.size)
        fun def(name: String) = combat[manorTypes.find(name)!!]!!
        for (name in manor.keys) {
            val type = manorTypes.find(name)!!
            assertEquals(CombatMath.npcMaxHit(type.stats.strength, def(name).strengthBonus), def(name).maxHit, name)
            assertTrue(def(name).aggression != null, "$name is aggressive")
        }
        assertEquals(2, def("house_ghost4").maxHit)
        assertEquals(3, def("draynor_skeleton").maxHit)
        assertEquals(AttackType.CRUSH, def("house_ghost").attackType)
        assertEquals(AttackType.SLASH, def("draynor_skeleton").attackType)
        assertEquals(NpcCombatAnims(5532, 5533, 5534), def("house_ghost5").anims)
        assertEquals(NpcCombatAnims(5487, 5489, 5491), def("draynor_skeleton").anims)
        assertEquals(ElementalWeakness(Element.AIR, 50), def("house_ghost2").weakness)
        assertEquals(listOf(40, 60), listOf("house_ghost3", "draynor_skeleton").map { def(it).respawnTicks })
        assertEquals(-5, def("house_ghost").defence.against(AttackType.MAGIC))
    }

    @Test
    fun `Varrock's fighters fight as the Wiki has them, each max hit the formula's`() {
        // The 241 cache's (and the DPS calculator's) Strength levels.
        val city = mapOf(
            "mugger" to 5, "black_knight" to 25, "thief1" to 13, "qip_soa_thief_male01" to 13, "qip_soa_thief_female01" to 13,
            "qip_soa_thief_female02" to 13, "barbarianwoman_varrock" to 5, "warrior_woman" to 22, "brownbear" to 18, "giantrat1" to 5,
        )
        val cityTypes = NpcTypes.of(*city.entries.mapIndexed { i, (name, str) -> NpcType(2300 + i, name, internalName = name, stats = NpcStats(strength = str)) }.toTypedArray())
        val combat = NpcCombat.load(cityTypes, seqs)
        assertEquals(city.size, combat.size)
        fun def(name: String) = combat[cityTypes.find(name)!!]!!
        for (name in city.keys) {
            val type = cityTypes.find(name)!!
            assertEquals(CombatMath.npcMaxHit(type.stats.strength, def(name).strengthBonus), def(name).maxHit, name)
        }
        assertEquals(listOf(1, 4, 2, 2, 3, 3), listOf("mugger", "black_knight", "thief1", "barbarianwoman_varrock", "warrior_woman", "brownbear").map { def(it).maxHit })
        // Muggers, the bear and the giant rats go for players; the rest wait to be attacked.
        assertEquals(setOf("mugger", "brownbear", "giantrat1"), city.keys.filter { def(it).aggression != null }.toSet())
        assertEquals(listOf(5, 6, 4), listOf("black_knight", "barbarianwoman_varrock", "qip_soa_thief_female02").map { def(it).attackSpeed })
        assertEquals(AttackType.SLASH, def("black_knight").attackType)
        assertEquals(AttackType.STAB, def("warrior_woman").attackType)
        assertEquals(NpcCombatAnims(422, 424, 836), def("mugger").anims)
        assertEquals(NpcCombatAnims(390, 1156, 836), def("black_knight").anims)
        assertEquals(NpcCombatAnims(4925, 4927, 4929), def("brownbear").anims)
        assertEquals(ElementalWeakness(Element.FIRE, 20), def("brownbear").weakness)
        assertEquals(listOf(25, 25, 25, 50, 50), listOf("mugger", "black_knight", "thief1", "warrior_woman", "brownbear").map { def(it).respawnTicks })
        assertEquals(76, def("black_knight").defence.against(AttackType.SLASH))
    }

    @Test
    fun `the Archive Guardian's two forms fight as the Wiki has them, levitating out of melee's reach`() {
        val flying = NpcType(16659, "Archive Guardian", size = 5, internalName = "tfa_guardian", stats = NpcStats(attack = 250, defence = 260, strength = 240, hitpoints = 550, ranged = 280, magic = 250))
        val grounded = NpcType(16660, "Archive Guardian", size = 5, internalName = "tfa_guardian_unshielded", stats = NpcStats(attack = 250, defence = 260, strength = 240, hitpoints = 1100, ranged = 280, magic = 250))
        val combat = NpcCombat.load(NpcTypes.of(flying, grounded), seqs)
        val up = combat[flying]!!
        val down = combat[grounded]!!
        assertEquals(listOf(5, 5), listOf(up.attackSpeed, down.attackSpeed))
        assertEquals(listOf(25, 25), listOf(up.maxHit, down.maxHit))
        assertEquals(AttackType.RANGED, up.attackType)
        assertEquals(AttackType.CRUSH, down.attackType)
        assertEquals(true, up.flying)
        assertEquals(false, down.flying)
        assertEquals(listOf(NpcRangedAttack(anim = 14751, cast = 4199, projectile = 4201, impact = 4200)), up.attacks)
        assertEquals(NpcCombatAnims(14752, -1, 14749), down.anims)
        assertEquals(ElementalWeakness(Element.FIRE, 80), up.weakness)
        assertEquals(ElementalWeakness(Element.FIRE, 40), down.weakness)
        assertEquals(mapOf(NpcStat.DEFENCE to 60), up.drainCaps)
        assertEquals(mapOf(NpcStat.DEFENCE to 60), down.drainCaps)
        assertTrue(up.poisonImmune && up.venomImmune && down.poisonImmune && down.venomImmune)
        // The cache's slash bonus is 110 levitating and 90 grounded; the Wiki's crush 20 for both.
        assertEquals(listOf(110, 90), listOf(up.defence.against(AttackType.SLASH), down.defence.against(AttackType.SLASH)))
        assertEquals(20, down.defence.against(AttackType.CRUSH))
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

    private companion object {
        val VARROCK_GUARDS = listOf(
            "fai_varrock_guard02", "fai_varrock_guard02_variant01", "fai_varrock_guard02_variant02",
            "fai_varrock_guard02_f", "fai_varrock_guard02_f_variant01", "fai_varrock_guard02_f_variant02", "fai_varrock_guard_captain02",
        )

        /** The camp south-east of Edgeville's ten outlaws, one type each (4167 to 4176). */
        val OUTLAWS = (1..10).map { "surok_outlaw$it" }

        /**
         * Barbarian Village's melee barbarians (Edgeville step 7) and their cache Strength levels: 7 at levels 9 and 10,
         * 14 at 17. Aitan (fai_barbarian_14) shoots, so the melee formula doesn't give his max hit.
         */
        val BARBARIANS = listOf(2, 5, 6, 7, 10, 11, 12, 13).associate { "fai_barbarian_$it" to 7 } +
            listOf(1, 3, 4, 8).associate { "fai_barbarian_$it" to 14 } +
            listOf(2, 3, 4).associate { "fai_barbarian_female_$it" to 7 } + ("fai_barbarian_female_1" to 14)
    }
}
