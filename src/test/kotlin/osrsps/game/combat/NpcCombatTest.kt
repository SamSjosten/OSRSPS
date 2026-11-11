package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            "human_caststrike" to 711, "human_castconfuse" to 716, "human_castweaken" to 717,
            "giant_update_moss_attack" to 4658, "giant_update_moss_defend" to 4657, "giant_update_moss_death" to 4659,
            "ghost_update_tendrill_attack" to 5540, "ghost_update_tendrill_defend" to 5541, "ghost_update_tendrill_death" to 5542,
            "mouse_attack" to 2705, "mouse_defend" to 2706, "mouse_death" to 2707,
            "imp_attack" to 169, "imp_block" to 170, "imp_death" to 172,
            "cow_update_attack" to 5849, "cow_update_defend" to 5850, "cow_update_death" to 5851,
            "chicken_attack" to 55, "chicken_block" to 56, "chicken_death" to 57,
            "sheep_attack" to 250, "sheep_block" to 251, "sheep_death" to 252, "duck_rework_death" to 3468,
            "slice_surface_goblin_squat_unarmed_attack" to 6184, "slice_surface_goblin_defend" to 6183, "slice_surface_goblin_death" to 6182,
            "spider_update_attack" to 5327, "spider_update_defend" to 5328, "spider_update_death" to 5329,
            "bat_rework_attack" to 4915, "bat_rework_defend" to 4916, "bat_rework_death" to 4917,
            "bear_rework_attack" to 4925, "bear_rework_defend" to 4927, "bear_rework_death" to 4929,
            "fear_attack" to 3812, "fear_parry" to 3811, "fear_death" to 3813,
            "confattack" to 3816, "confparry" to 3820, "confdeath" to 3821,
            "lessattack" to 3823, "lessparry" to 3826, "lessdeath" to 3827,
            "giant_rat_update_attack" to 4933, "giant_rat_update_defend" to 4934, "giant_rat_update_death" to 4935,
            "giant_frog_attack" to 1793, "giant_frog_block" to 1794, "giant_frog_death" to 1795,
            "slice_surface_goblin_squat_attack_spear" to 6188, "slice_surface_goblin_defend_spear" to 6189, "slice_surface_goblin_death_spear" to 6190,
            "unicorn_rework_attack" to 6376, "unicorn_rework_defend" to 6375, "unicorn_rework_death" to 6377,
            "human_sword_slash" to 390, "human_sword_block" to 387, "human_sword_stab" to 386,
            "gnome_attackunarmed" to 12044, "gnome_block" to 12046, "gnome_death" to 12048,
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
            "mino_attack" to 4266, "mino_parry" to 4267, "mino_death" to 4265,
            "kalphite_attack_mandibles" to 1184, "kalphite_block" to 1186, "kalphite_death" to 1190,
            "dog_update_wolf_attack" to 6559, "dog_update_wolf_defend" to 6557, "dog_update_wolf_death" to 6558,
            "catpon_attack" to 4271, "catpon_attack_breath" to 4272, "catpon_parry" to 4273, "catpon_death" to 4270,
            "small_spider_update_attack" to 6249, "small_spider_update_defend" to 6250, "small_spider_update_death" to 6251,
            "bear_rework_attack" to 4925, "bear_rework_defend" to 4927, "bear_rework_death" to 4929,
            "npc_archive_guardian_range_attack01" to 14751, "npc_archive_guardian_melee_attack01" to 14752, "npc_archive_guardian_death01" to 14749,
            "npc_ezekiel_melee01" to 14685, "npc_ezekiel_death01" to 14689,
            "npc_bork_gong_melee01" to 14542, "npc_bork_death01" to 14541, "npc_smork_cannon_attack01" to 14554,
            "npc_ocellus_phase1_basic_projectile_start" to 14806,
            "npc_ocellus_phase3_melee_basic" to 14842,
            "npc_ocellus_phase3_death" to 14872,
            "dreadnoth_melee01" to 14779, "dreadnoth_death01" to 14782, "jelly_attack" to 1586, "jelly_death" to 1587,
            "moia_projectile_attack01" to 14785, "moia_death01" to 14784, "moia_minion_attack" to 14791, "moia_minion_death" to 14792,
            "xarth_attack_magic_01" to 14984, "xarth_death_01" to 14995, "xarth_corpse_death_01" to 15003,
            "npc_dagonhai_attack_ranged_01" to 14593, "npc_dagonhai_death_01" to 14652, "npc_dagonhai_death_02" to 14653,
            "npc_dagonhai_death_03" to 14654, "leagues_6_thrall_imp_melee_attack" to 13851, "leagues_6_thrall_imp_melee_despawn" to 13856,
            "demon_attack" to 64, "demon_block" to 65, "demon_death" to 67,
            "shade_attack" to 1283, "shade_block" to 1286, "shade_sink" to 1287,
            "monalisk_cave_bug_attack" to 6079, "monalisk_cave_bug_defend" to 6080, "monalisk_mini_cave_bug_death" to 6082,
            "cave_slime_attack" to 1789, "cave_slime_defend" to 1791, "cave_slime_death" to 1792,
            "crawler_attack" to 227, "crawler_parry" to 1504, "crawler_death" to 228,
            "dorgesh_goblin_unarmed_attack" to 6001, "dorgesh_goblin_unarmed_defend" to 6002, "dorgesh_goblin_death" to 6003,
            "rockslug_attack" to 1567, "rockslug_defend" to 1565, "rockslug_death" to 1568,
            "wall_beast_specialattack_attack" to 1802, "wall_beast_specialattack_defend" to 1803, "wall_beast_specialattack_death" to 1804,
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
    fun `the Varrock Sewers' moss giants, spiders and ghosts fight as the Wiki has them, each max hit the formula's`() {
        // The 241 cache's (and the DPS calculator's) Strength levels.
        val sewer = mapOf(
            "mossgiant" to 30, "mossgiant2" to 30, "mossgiant3" to 30, "mossgiant4" to 30, "giantspider2" to 24,
            "deadly_red_spider" to 25, "ghost" to 13, "ghost5" to 13, "ghost8" to 13, "zombie_unarmed_sewer4" to 9,
        )
        val sewerTypes = NpcTypes.of(*sewer.entries.mapIndexed { i, (name, str) -> NpcType(2500 + i, name, internalName = name, stats = NpcStats(strength = str)) }.toTypedArray())
        val combat = NpcCombat.load(sewerTypes, seqs)
        assertEquals(sewer.size, combat.size)
        fun def(name: String) = combat[sewerTypes.find(name)!!]!!
        for (name in sewer.keys) {
            assertEquals(CombatMath.npcMaxHit(sewer.getValue(name), def(name).strengthBonus), def(name).maxHit, name)
            assertTrue(def(name).aggression != null, "$name is aggressive")
        }
        assertEquals(listOf(6, 3, 3, 2, 2), listOf("mossgiant", "giantspider2", "deadly_red_spider", "ghost", "zombie_unarmed_sewer4").map { def(it).maxHit })
        assertEquals(6, def("mossgiant").attackSpeed)
        assertEquals(AttackType.CRUSH, def("mossgiant").attackType)
        assertEquals(AttackType.STAB, def("deadly_red_spider").attackType)
        assertEquals(NpcCombatAnims(4658, 4657, 4659), def("mossgiant2").anims)
        assertEquals(NpcCombatAnims(5540, 5541, 5542), def("ghost8").anims)
        assertEquals(ElementalWeakness(Element.FIRE, 40), def("deadly_red_spider").weakness)
        assertEquals(ElementalWeakness(Element.AIR, 50), def("ghost5").weakness)
        assertEquals(listOf(30, 30, 30, 40), listOf("mossgiant", "giantspider2", "deadly_red_spider", "ghost").map { def(it).respawnTicks })
        assertEquals(16, def("deadly_red_spider").defence.against(AttackType.SLASH))
    }

    @Test
    fun `Varrock's level 20 dark wizards cast Earth Strike and Weaken from 10 tiles`() {
        val wizards = listOf("bearded_dark_wizard", "qip_ds_young_dark_wizard3", "qip_ds_young_dark_wizard4")
        val wizardTypes = NpcTypes.of(*wizards.mapIndexed { i, name -> NpcType(2400 + i, "Dark wizard", internalName = name, stats = NpcStats(strength = 17)) }.toTypedArray())
        val combat = NpcCombat.load(wizardTypes, seqs)
        assertEquals(wizards.size, combat.size)
        val def = combat[wizardTypes.find("qip_ds_young_dark_wizard3")!!]!!
        assertEquals(AttackType.MAGIC, def.attackType)
        assertEquals(10, def.range)
        assertEquals(6, def.maxHit)
        assertEquals(4, def.attackSpeed)
        assertEquals(50, def.respawnTicks)
        assertTrue(def.aggression != null)
        assertEquals(3, def.defence.against(AttackType.MAGIC))
        assertEquals(
            listOf(
                NpcRangedAttack(711, 96, 97, 98),
                NpcRangedAttack(717, 105, 106, 107, maxHit = 0, drain = StatDrain(Skill.STRENGTH, 5)),
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
    fun `the Vault of War's goblins, minotaurs, wolves and rats fight as the Wiki has them, each max hit the formula's`() {
        // The 241 cache's Strength levels (the DPS calculator's); 2489 is taken as 2487's.
        val vault = mapOf(
            "sos_war_goblin_armed" to 2, "sos_war_goblin_green_soldier_1" to 3, "sos_war_goblin_armed2" to 11,
            "sos_war_goblin_green_soldier_2" to 11, "sos_war_goblin_helmet" to 13, "sos_war_goblin_helmet2" to 23,
            "sos_war_minotaur" to 10, "sos_war_minotaur3" to 25, "sos_war_wolf" to 15, "sos_war_wolf2" to 10, "sos_war_rat" to 1,
        )
        val vaultTypes = NpcTypes.of(*vault.entries.mapIndexed { i, (name, str) -> NpcType(2300 + i, name, internalName = name, stats = NpcStats(strength = str)) }.toTypedArray())
        val combat = NpcCombat.load(vaultTypes, seqs)
        assertEquals(vault.size, combat.size)
        fun def(name: String) = combat[vaultTypes.find(name)!!]!!
        for (name in vault.keys) {
            val type = vaultTypes.find(name)!!
            assertEquals(CombatMath.npcMaxHit(type.stats.strength, def(name).strengthBonus), def(name).maxHit, name)
            assertNull(def(name).aggression, "$name is not aggressive")
        }
        assertEquals(listOf(1, 1, 3, 2, 3, 2, 3, 2, 2, 0), listOf(
            "sos_war_goblin_armed", "sos_war_goblin_green_soldier_1", "sos_war_goblin_armed2", "sos_war_goblin_helmet",
            "sos_war_goblin_helmet2", "sos_war_minotaur", "sos_war_minotaur3", "sos_war_wolf", "sos_war_wolf2", "sos_war_rat",
        ).map { def(it).maxHit })
        assertEquals(listOf(6, 4, 4, 4, 4), listOf("sos_war_goblin_armed", "sos_war_goblin_helmet", "sos_war_minotaur", "sos_war_wolf", "sos_war_rat").map { def(it).attackSpeed })
        assertEquals(listOf(35, 35, 15, 90, 2), listOf("sos_war_goblin_armed", "sos_war_goblin_helmet2", "sos_war_minotaur3", "sos_war_wolf2", "sos_war_rat").map { def(it).respawnTicks })
        assertEquals(NpcCombatAnims(4266, 4267, 4265), def("sos_war_minotaur").anims)
        assertEquals(NpcCombatAnims(6559, 6557, 6558), def("sos_war_wolf").anims)
        assertEquals(NpcCombatAnims(6188, 6189, 6190), def("sos_war_goblin_helmet").anims)
        assertEquals(ElementalWeakness(Element.FIRE, 25), def("sos_war_wolf2").weakness)
        assertEquals(-21, def("sos_war_minotaur3").defence.against(AttackType.MAGIC))
        assertEquals(18, def("sos_war_goblin_helmet2").defence.against(AttackType.CRUSH))
    }

    @Test
    fun `the Catacomb of Famine's flesh crawlers, zombies and giant rats fight as the Wiki has them, each max hit the formula's`() {
        // The 241 cache's Strength levels (the DPS calculator's).
        val famine = mapOf(
            "sos_fam_fleshcrawler" to 2, "sos_fam_fleshcrawler3" to 2, "sos_fam_zombie_unarmed" to 29, "sos_fam_zombie2_c" to 40,
            "sos_fam_zombie_armed" to 40, "sos_fam_zombie_armed3" to 40, "sos_fam_giantrat2" to 23, "sos_fam_rat" to 1,
        )
        val famineTypes = NpcTypes.of(*famine.entries.mapIndexed { i, (name, str) -> NpcType(2400 + i, name, internalName = name, stats = NpcStats(strength = str)) }.toTypedArray())
        val combat = NpcCombat.load(famineTypes, seqs)
        assertEquals(famine.size, combat.size)
        fun def(name: String) = combat[famineTypes.find(name)!!]!!
        for (name in famine.keys) {
            val type = famineTypes.find(name)!!
            assertEquals(CombatMath.npcMaxHit(type.stats.strength, def(name).strengthBonus), def(name).maxHit, name)
        }
        for (name in listOf("sos_fam_fleshcrawler", "sos_fam_zombie_unarmed", "sos_fam_zombie2_c", "sos_fam_zombie_armed3")) {
            assertTrue(def(name).aggression != null, "$name is aggressive")
        }
        assertNull(def("sos_fam_giantrat2").aggression)
        // "Flesh crawlers are aggressive to any level players" (OSRS Wiki, Flesh Crawler); the zombies keep the level rule.
        assertTrue(def("sos_fam_fleshcrawler3").aggression!!.ignoresLevels)
        assertTrue(def("sos_fam_fleshcrawler3").aggression!!.tolerant)
        assertFalse(def("sos_fam_zombie_armed3").aggression!!.ignoresLevels)
        assertEquals(listOf(1, 4, 5, 5, 3), listOf("sos_fam_fleshcrawler", "sos_fam_zombie_unarmed", "sos_fam_zombie2_c", "sos_fam_zombie_armed", "sos_fam_giantrat2").map { def(it).maxHit })
        assertEquals(listOf(3, 5, 4), listOf("sos_fam_fleshcrawler3", "sos_fam_zombie_armed", "sos_fam_giantrat2").map { def(it).attackSpeed })
        assertEquals(listOf(15, 35, 30), listOf("sos_fam_fleshcrawler", "sos_fam_zombie2_c", "sos_fam_giantrat2").map { def(it).respawnTicks })
        assertEquals(NpcCombatAnims(1184, 1186, 1190), def("sos_fam_fleshcrawler").anims)
        assertEquals(NpcCombatAnims(5578, 5579, 5580), def("sos_fam_zombie_armed").anims)
        assertEquals(NpcCombatAnims(5571, 5574, 5575), def("sos_fam_zombie_armed3").anims)
        assertEquals(22, def("sos_fam_zombie2_c").defence.against(AttackType.CRUSH))
        assertEquals(ElementalWeakness(Element.FIRE, 20), def("sos_fam_fleshcrawler3").weakness)
    }

    @Test
    fun `the Pit of Pestilence's catablepons, spiders and scorpions fight as the Wiki has them, each max hit the formula's`() {
        // The 241 cache's Strength levels (the DPS calculator's).
        val pit = mapOf(
            "sos_pest_catablepon" to 40, "sos_pest_catablepon2" to 50, "sos_pest_catablepon3" to 60, "sos_pest_giantspider1" to 51,
            "sos_pest_spider" to 21, "sos_pest_scorpion" to 52, "sos_pest_scorpion2" to 32,
        )
        val pitTypes = NpcTypes.of(*pit.entries.mapIndexed { i, (name, str) -> NpcType(2500 + i, name, internalName = name, stats = NpcStats(strength = str)) }.toTypedArray())
        val combat = NpcCombat.load(pitTypes, seqs)
        assertEquals(pit.size, combat.size)
        fun def(name: String) = combat[pitTypes.find(name)!!]!!
        for (name in pit.keys) {
            val type = pitTypes.find(name)!!
            assertEquals(CombatMath.npcMaxHit(type.stats.strength, def(name).strengthBonus), def(name).maxHit, name)
            assertTrue(def(name).aggression != null, "$name is aggressive")
        }
        assertEquals(listOf(5, 6, 7, 7, 6, 6, 4), pit.keys.map { def(it).maxHit })
        assertEquals(listOf(20, 25, 27, 22, 25), listOf("sos_pest_catablepon", "sos_pest_giantspider1", "sos_pest_spider", "sos_pest_scorpion", "sos_pest_scorpion2").map { def(it).respawnTicks })
        assertEquals(NpcCombatAnims(4271, 4273, 4270), def("sos_pest_catablepon2").anims)
        assertEquals(NpcCombatAnims(6249, 6250, 6251), def("sos_pest_spider").anims)
        assertEquals(ElementalWeakness(Element.FIRE, 50), def("sos_pest_giantspider1").weakness)
        // Weaken: a drain-only glare, one turn in 4, Strength down 5% a cast from where it is, until 8% + 3 is gone.
        for (name in listOf("sos_pest_catablepon", "sos_pest_catablepon2", "sos_pest_catablepon3")) {
            val spell = def(name).spell!!
            assertEquals(4, spell.chance)
            assertEquals(NpcRangedAttack(anim = 4272, projectile = 106, impact = 107, maxHit = 0, drain = StatDrain(Skill.STRENGTH, 5, stacks = true)), spell.attack)
            // 99 Strength: 8% is 7, plus 3 is 10, so it casts at 90 and stops at 89.
            assertTrue(spell.casts(99, 99))
            assertTrue(spell.casts(99, 90))
            assertFalse(spell.casts(99, 89))
        }
        assertNull(def("sos_pest_giantspider1").spell)
        // "aggressive towards any level" (OSRS Wiki, Stronghold of Security); the others keep the level rule.
        assertTrue(def("sos_pest_giantspider1").aggression!!.ignoresLevels)
        assertFalse(def("sos_pest_scorpion").aggression!!.ignoresLevels)
    }

    @Test
    fun `the Sepulchre of Death's ankou, ghosts, skeletons, shades and zombie fight as the Wiki has them, each max hit the formula's`() {
        // The 241 cache's Strength levels (the DPS calculator's).
        val sepulchre = mapOf(
            "sos_death_ankou" to 70, "sos_death_ankou2" to 70, "sos_death_ankou3" to 75, "sos_death_ghost" to 63, "sos_death_ghost2" to 63,
            "sos_death_skeleton_unarmed" to 50, "sos_death_skeleton_unarmed2" to 50, "sos_death_skeleton_armed" to 74, "macro_shade" to 130,
            "macro_zombie" to 130,
        )
        val types = NpcTypes.of(*sepulchre.entries.mapIndexed { i, (name, str) -> NpcType(2500 + i, name, internalName = name, stats = NpcStats(strength = str)) }.toTypedArray())
        val combat = NpcCombat.load(types, seqs)
        assertEquals(sepulchre.size, combat.size)
        fun def(name: String) = combat[types.find(name)!!]!!
        for (name in sepulchre.keys) {
            val type = types.find(name)!!
            assertEquals(CombatMath.npcMaxHit(type.stats.strength, def(name).strengthBonus), def(name).maxHit, name)
            // The shades and the zombie by the ladder are left alone until attacked; the rest come for the player.
            assertEquals(!name.startsWith("macro_"), def(name).aggression != null, "$name aggressive")
            assertEquals(4, def(name).attackSpeed, name)
        }
        assertEquals(listOf(8, 8, 8, 7, 7, 6, 6, 10, 14, 14), sepulchre.keys.map { def(it).maxHit })
        assertEquals(listOf(50, 40, 70, 70, 50, 75), listOf("sos_death_ankou", "sos_death_ghost", "sos_death_skeleton_unarmed", "sos_death_skeleton_armed", "macro_shade", "macro_zombie").map { def(it).respawnTicks })
        assertEquals(NpcCombatAnims(422, 424, 836), def("sos_death_ankou").anims)
        assertEquals(NpcCombatAnims(5540, 5541, 5542), def("sos_death_ghost").anims)
        assertEquals(NpcCombatAnims(5532, 5533, 5534), def("sos_death_ghost2").anims)
        assertEquals(NpcCombatAnims(5487, 5489, 5491), def("sos_death_skeleton_armed").anims)
        assertEquals(NpcCombatAnims(1283, 1286, 1287), def("macro_shade").anims)
        assertEquals(ElementalWeakness(Element.AIR, 40), def("sos_death_ankou").weakness)
        assertEquals(ElementalWeakness(Element.EARTH, 35), def("sos_death_skeleton_armed").weakness)
        assertEquals(79, def("sos_death_skeleton_armed").defence.against(AttackType.STAB))
        // The anti-macro shades and zombie: "1 experience for 10 damage dealt".
        assertEquals(listOf(-975, -975), listOf("macro_shade", "macro_zombie").map { def(it).xpBonusTenths })
    }

    @Test
    fun `a spell on a magic NPC fails the file`() {
        val bad = """{ "npcs": [ { "npcs": ["cow"], "attackSpeed": 4, "maxHit": 1, "attackType": "magic", "attackBonus": 0, "strengthBonus": 0,
            "defence": { "stab": 0, "slash": 0, "crush": 0, "magic": 0, "light": 0, "standard": 0, "heavy": 0 },
            "anims": { "attack": null, "defend": null, "death": null }, "respawnTicks": 10, "source": "test",
            "spell": { "chance": 2 } } ] }"""
        assertFailsWith<IllegalArgumentException> { NpcCombat.parse(bad, types, SeqNames.NONE) }
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
    fun `Bork and the Smorks fight as the Wiki has them, each max hit the formula's`() {
        val bork = NpcType(16640, "Bork", size = 4, internalName = "tfa_bork", stats = NpcStats(attack = 200, defence = 300, strength = 200, hitpoints = 1500, ranged = 200, magic = 90))
        val smorks = NpcType(16641, "Smorks", size = 3, internalName = "tfa_smork", stats = NpcStats(attack = 1, defence = 220, strength = 1, hitpoints = 600, ranged = 185, magic = 185))
        val combat = NpcCombat.load(NpcTypes.of(bork, smorks), seqs)
        val him = combat[bork]!!
        val them = combat[smorks]!!
        assertEquals(listOf(7, 7), listOf(him.attackSpeed, them.attackSpeed))
        assertEquals(AttackType.CRUSH, him.attackType)
        assertEquals(AttackType.RANGED, them.attackType)
        // The Wiki lists neither max hit: the formula's from the levels and bonuses.
        assertEquals(CombatMath.npcMaxHit(200, 40), him.maxHit)
        // Theirs is the most seen on live (Sam's recording of 2026-11-09), well under the formula's 28.
        assertEquals(7, them.maxHit)
        assertEquals(listOf(100, 250, 185), listOf(him.attackBonus, them.attackBonus, them.magicAttack))
        assertEquals(NpcCombatAnims(14542, -1, 14541), him.anims)
        assertEquals(listOf(NpcRangedAttack(anim = 14554, projectile = 4056, impact = 4055)), them.attacks)
        assertEquals(ElementalWeakness(Element.EARTH, 40), him.weakness)
        assertEquals(ElementalWeakness(Element.EARTH, 85), them.weakness)
        assertEquals(mapOf(NpcStat.DEFENCE to 40), him.drainCaps)
        assertTrue(him.poisonImmune && him.venomImmune && them.poisonImmune && !them.venomImmune)
        assertTrue(him.freezeImmune && them.freezeImmune)
        // His body goes 7 ticks after he falls, their tower 9 after they do (tsps capture notes).
        assertEquals(listOf(7, 9), listOf(him.deathTicks, them.deathTicks))
        assertEquals(listOf(140, 130, 10, 300), listOf(AttackType.STAB, AttackType.SLASH, AttackType.CRUSH, AttackType.MAGIC).map { him.defence.against(it) })
        assertEquals(listOf(220, 170, 160, 400), listOf(AttackType.STAB, AttackType.SLASH, AttackType.CRUSH, AttackType.MAGIC).map { them.defence.against(it) })
    }

    @Test
    fun `Ocellus's first phase fights as the Wiki has it, both his shots at the +150`() {
        val ocellus = NpcType(16674, "Ocellus", size = 3, internalName = "tfa_ocellus_p1", stats = NpcStats(attack = 1, defence = 215, strength = 1, hitpoints = 2500, ranged = 250, magic = 350))
        val him = NpcCombat.load(NpcTypes.of(ocellus), seqs)[ocellus]!!
        assertEquals(listOf(5, 36), listOf(him.attackSpeed, him.maxHit))
        // His fireball rolls ranged and his water ball magic: ranged is the primary style, so each finds its +150.
        assertEquals(AttackType.RANGED, him.attackType)
        assertEquals(listOf(150, 150), listOf(him.attackBonus, him.magicAttack))
        assertEquals(listOf(150, 95, 60, 100), listOf(AttackType.STAB, AttackType.SLASH, AttackType.CRUSH, AttackType.MAGIC).map { him.defence.against(it) })
        assertEquals(mapOf(NpcStat.DEFENCE to 60), him.drainCaps)
        // Nor does a barrage hold him from his corners (exploit review round 22, R22B-2).
        assertTrue(him.poisonImmune && him.venomImmune && him.freezeImmune)
    }

    @Test
    fun `Ocellus's third phase fights as the Wiki has it, speed 3 and melee max 30`() {
        val ocellus = NpcType(16677, "Ocellus", size = 3, internalName = "tfa_ocellus_p3", stats = NpcStats(attack = 325, defence = 250, strength = 250, hitpoints = 5250, ranged = 300, magic = 300))
        val him = NpcCombat.load(NpcTypes.of(ocellus), seqs)[ocellus]!!
        assertEquals(listOf(3, 30), listOf(him.attackSpeed, him.maxHit))
        assertEquals(AttackType.SLASH, him.attackType)
        assertEquals(NpcCombatAnims(14842, -1, 14872), him.anims)
        assertEquals(listOf(350, 100, 30, 200), listOf(AttackType.STAB, AttackType.SLASH, AttackType.CRUSH, AttackType.MAGIC).map { him.defence.against(it) })
        assertEquals(mapOf(NpcStat.DEFENCE to 60), him.drainCaps)
        assertTrue(him.poisonImmune && him.venomImmune && him.freezeImmune)
    }

    @Test
    fun `Ocellus's void flares have the Wiki's defences, the Rift and Augmenter weak to earth`() {
        fun flare(id: Int, name: String) = NpcType(id, name, internalName = name, stats = NpcStats(attack = 1, defence = 175, strength = 1, hitpoints = 44, ranged = 1, magic = 300))
        val rift = flare(16678, "tfa_ocellus_p3_bombportal_basic")
        val siphon = flare(16679, "tfa_ocellus_p3_bombportal_heal")
        val augmenter = flare(16680, "tfa_ocellus_p3_bombportal_buff")
        val combat = NpcCombat.load(NpcTypes.of(rift, siphon, augmenter), seqs)
        val types = listOf(AttackType.STAB, AttackType.SLASH, AttackType.CRUSH, AttackType.MAGIC)
        assertEquals(listOf(0, 0, 0, 0), types.map { combat[rift]!!.defence.against(it) })
        assertEquals(listOf(50, 50, 50, 0), types.map { combat[siphon]!!.defence.against(it) })
        assertEquals(listOf(50, 50, 50, 0), types.map { combat[augmenter]!!.defence.against(it) })
        assertEquals(listOf(0, 50, 50), with(combat[augmenter]!!.defence) { listOf(light, standard, heavy) })
        assertEquals(ElementalWeakness(Element.EARTH, 75), combat[rift]!!.weakness)
        assertEquals(ElementalWeakness(Element.EARTH, 75), combat[augmenter]!!.weakness)
        assertNull(combat[siphon]!!.weakness)
        assertTrue(combat[rift]!!.poisonImmune && combat[siphon]!!.venomImmune)
        // The Augmenter's 0% resistance: it can be poisoned.
        assertTrue(!combat[augmenter]!!.poisonImmune && !combat[augmenter]!!.venomImmune)
        assertTrue(listOf(rift, siphon, augmenter).all { combat[it]!!.freezeImmune })
    }

    @Test
    fun `Dreadnoth and his jellies fight as the Wiki has them, each max hit the formula's`() {
        val dreadnoth = NpcType(16665, "Dreadnoth the Jellied", size = 5, internalName = "tfa_jelly_boss", stats = NpcStats(attack = 280, defence = 125, strength = 285, hitpoints = 2100, ranged = 250, magic = 220))
        val jelly = NpcType(16666, "Jelly", internalName = "tfa_jelly_minion", stats = NpcStats(attack = 200, defence = 100, strength = 160, hitpoints = 75, ranged = 1, magic = 100))
        val combat = NpcCombat.load(NpcTypes.of(dreadnoth, jelly), seqs)
        val him = combat[dreadnoth]!!
        val minion = combat[jelly]!!
        assertEquals(listOf(4, 5), listOf(him.attackSpeed, minion.attackSpeed))
        assertEquals(listOf(AttackType.CRUSH, AttackType.CRUSH), listOf(him.attackType, minion.attackType))
        // The Wiki's 45 and 17 are the formula's too.
        assertEquals(listOf(45, 17), listOf(him.maxHit, minion.maxHit))
        assertEquals(CombatMath.npcMaxHit(285, 35), him.maxHit)
        assertEquals(CombatMath.npcMaxHit(160, 0), minion.maxHit)
        assertEquals(NpcCombatAnims(14779, -1, 14782), him.anims)
        assertEquals(NpcCombatAnims(1586, -1, 1587), minion.anims)
        assertEquals(ElementalWeakness(Element.FIRE, 85), him.weakness)
        assertEquals(ElementalWeakness(Element.FIRE, 60), minion.weakness)
        assertEquals(mapOf(NpcStat.ATTACK to 50, NpcStat.STRENGTH to 50, NpcStat.RANGED to 50, NpcStat.MAGIC to 50), him.drainCaps)
        assertTrue(him.poisonImmune && !him.venomImmune && !minion.poisonImmune)
        assertEquals(775, him.xpBonusTenths)
        assertEquals(listOf(15, 20, 5, 20), listOf(AttackType.STAB, AttackType.SLASH, AttackType.CRUSH, AttackType.MAGIC).map { him.defence.against(it) })
    }

    @Test
    fun `Moia and her binders fight as the Wiki has them, her daggers ranged and her binders' max hit guessed`() {
        val moia = NpcType(16667, "Moia", size = 3, internalName = "tfa_moia", stats = NpcStats(attack = 1, defence = 195, strength = 1, hitpoints = 1900, ranged = 200, magic = 350))
        val binder = NpcType(16670, "Undead binder", internalName = "tfa_moia_minion", stats = NpcStats(attack = 120, defence = 225, strength = 120, hitpoints = 95, ranged = 1, magic = 50))
        val combat = NpcCombat.load(NpcTypes.of(moia, binder), seqs)
        val her = combat[moia]!!
        val minion = combat[binder]!!
        assertEquals(listOf(5, 5), listOf(her.attackSpeed, minion.attackSpeed))
        assertEquals(listOf(AttackType.RANGED, AttackType.CRUSH), listOf(her.attackType, minion.attackType))
        // The Wiki's ranged max hit; the binder's is guessed.
        assertEquals(listOf(32, 16), listOf(her.maxHit, minion.maxHit))
        assertEquals(listOf(350, 300), listOf(her.attackBonus, her.magicAttack))
        assertEquals(listOf(14785), her.attacks.map { it.anim })
        assertEquals(NpcCombatAnims(14785, -1, 14784), her.anims)
        assertEquals(NpcCombatAnims(14791, -1, 14792), minion.anims)
        assertEquals(ElementalWeakness(Element.AIR, 80), her.weakness)
        assertEquals(mapOf(NpcStat.DEFENCE to 40), her.drainCaps)
        assertEquals(listOf(20, 110, 15, 30), listOf(AttackType.STAB, AttackType.SLASH, AttackType.CRUSH, AttackType.MAGIC).map { her.defence.against(it) })
        assertEquals(listOf(12, 15, 10, -20), listOf(AttackType.STAB, AttackType.SLASH, AttackType.CRUSH, AttackType.MAGIC).map { minion.defence.against(it) })
    }

    @Test
    fun `the Dagon'hai trio and their imps fight as the Wiki has them, the trio's weakened forms sharing their entries`() {
        fun caster(id: Int, internal: String) = NpcType(
            id, "Dagon'hai", size = 2, internalName = internal,
            stats = NpcStats(attack = 1, defence = 100, strength = 1, hitpoints = 640, ranged = 260, magic = 290),
        )
        val trio = listOf(
            caster(16643, "tfa_dagonhai_leader"), caster(16644, "tfa_dagonhai_leader_unshielded"),
            caster(16645, "tfa_dagonhai_minion_1"), caster(16646, "tfa_dagonhai_minion_1_unshielded"),
            caster(16647, "tfa_dagonhai_minion_2"), caster(16648, "tfa_dagonhai_minion_2_unshielded"),
        )
        val imp = NpcType(16650, "Imp", internalName = "tfa_dagonhai_demon", stats = NpcStats(attack = 250, defence = 50, strength = 195, hitpoints = 44, ranged = 66, magic = 125))
        // The common imp, which the file names `imp`: without it the file's entry would take ours by its name.
        val commonImp = NpcType(5007, "Imp", internalName = "imp", stats = NpcStats(attack = 4, defence = 4, strength = 5, hitpoints = 8))
        val combat = NpcCombat.load(NpcTypes.of(*(trio + imp + commonImp).toTypedArray()), seqs)
        for (type in trio) {
            val def = combat[type]!!
            assertEquals(listOf(6, 27, 180, 250), listOf(def.attackSpeed, def.maxHit, def.attackBonus, def.magicAttack), type.internalName)
            assertEquals(AttackType.RANGED, def.attackType)
            assertEquals(listOf(50, 50, 50, 100), listOf(AttackType.STAB, AttackType.SLASH, AttackType.CRUSH, AttackType.MAGIC).map { def.defence.against(it) })
        }
        assertEquals(listOf(14652, 14652, 14653, 14653, 14654, 14654), trio.map { combat[it]!!.anims.death })
        val demon = combat[imp]!!
        assertEquals(listOf(4, 20), listOf(demon.attackSpeed, demon.maxHit))
        assertEquals(CombatMath.npcMaxHit(imp.stats.strength, demon.strengthBonus), demon.maxHit, "the formula's")
        assertEquals(NpcCombatAnims(13851, -1, 13856), demon.anims)
    }

    @Test
    fun `Xarth fights as the Wiki has him, magic in phase one`() {
        val xarth = NpcType(16684, "Xarth", size = 3, internalName = "tfa_xarth", stats = NpcStats(attack = 250, defence = 170, strength = 225, hitpoints = 2600, ranged = 220, magic = 350))
        val him = NpcCombat.load(NpcTypes.of(xarth), seqs)[xarth]!!
        assertEquals(4, him.attackSpeed)
        assertEquals(AttackType.MAGIC, him.attackType)
        // The Wiki's magic max hit and magic attack bonus.
        assertEquals(36, him.maxHit)
        assertEquals(250, him.attackBonus)
        assertEquals(listOf(NpcRangedAttack(anim = 14984, projectile = 4034)), him.attacks)
        assertEquals(NpcCombatAnims(14984, -1, 14995), him.anims)
        assertEquals(ElementalWeakness(Element.WATER, 85), him.weakness)
        assertEquals(mapOf(NpcStat.DEFENCE to 40), him.drainCaps)
        assertTrue(him.poisonImmune && him.venomImmune && !him.freezeImmune)
        assertEquals(6, him.deathTicks)
        assertEquals(listOf(115, 105, 20, 205), listOf(AttackType.STAB, AttackType.SLASH, AttackType.CRUSH, AttackType.MAGIC).map { him.defence.against(it) })
    }

    @Test
    fun `Xarth's Corpse never fights, and goes four ticks after it dies`() {
        val corpse = NpcType(16687, "Corpse", internalName = "tfa_xarth_corpse", stats = NpcStats(attack = 50, defence = 100, strength = 40, hitpoints = 62, ranged = 1, magic = 100))
        val it = NpcCombat.load(NpcTypes.of(corpse), seqs)[corpse]!!
        assertEquals(0, it.maxHit)
        assertEquals(NpcCombatAnims(-1, -1, 15003), it.anims)
        assertEquals(ElementalWeakness(Element.WATER, 40), it.weakness)
        assertEquals(4, it.deathTicks)
        assertEquals(listOf(100, 100, 100, 0), listOf(AttackType.STAB, AttackType.SLASH, AttackType.CRUSH, AttackType.MAGIC).map { c -> it.defence.against(c) })
    }

    @Test
    fun `the Wizards' Tower's wizards cast Fire Strike, and the caged lesser demon fights as the Wiki has it`() {
        // The 241 cache's Strength levels: the wizard's 8, the lesser demon's 70.
        val tower = mapOf("wizard" to 8, "lesser_demon" to 70)
        val towerTypes = NpcTypes.of(*tower.entries.mapIndexed { i, (name, str) -> NpcType(2500 + i, name, internalName = name, stats = NpcStats(strength = str)) }.toTypedArray())
        val combat = NpcCombat.load(towerTypes, seqs)
        assertEquals(tower.size, combat.size)
        fun def(name: String) = combat[towerTypes.find(name)!!]!!
        val wizard = def("wizard")
        assertEquals(AttackType.MAGIC, wizard.attackType)
        assertEquals(listOf(4, 8, 30), listOf(wizard.maxHit, wizard.range, wizard.respawnTicks))
        assertEquals(null, wizard.aggression)
        assertEquals(3, wizard.defence.against(AttackType.MAGIC))
        assertEquals(listOf(NpcRangedAttack(711, 99, 100, 101)), wizard.attacks)
        val demon = def("lesser_demon")
        assertEquals(CombatMath.npcMaxHit(70, demon.strengthBonus), demon.maxHit)
        assertEquals(listOf(8, 4, 30), listOf(demon.maxHit, demon.attackSpeed, demon.respawnTicks))
        assertEquals(AttackType.SLASH, demon.attackType)
        assertEquals(-10, demon.defence.against(AttackType.MAGIC))
        assertEquals(ElementalWeakness(Element.WATER, 40), demon.weakness)
        assertTrue(demon.aggression != null)
        assertEquals(NpcCombatAnims(64, 65, 67), demon.anims)
    }

    @Test
    fun `the Lumbridge Swamp Caves' monsters fight as the Wiki has them, with their Slayer levels and fast regeneration`() {
        // The 242 cache's Strength levels.
        val caves = mapOf(
            "swamp_cave_bug" to 5, "swamp_cave_slime" to 13, "slayer_cave_crawler_1" to 18, "slayer_cave_crawler_2" to 18,
            "slayer_cave_crawler_3" to 18, "slayer_cave_crawler_4" to 18, "medium_frog" to 20, "giant_frog" to 80,
            "cave_goblin" to 1, "cave_goblin2" to 1, "cave_goblin3" to 1, "cave_goblin4" to 1,
        )
        val caveTypes = NpcTypes.of(*caves.entries.mapIndexed { i, (name, str) -> NpcType(2700 + i, name, internalName = name, stats = NpcStats(strength = str)) }.toTypedArray())
        val combat = NpcCombat.load(caveTypes, seqs)
        assertEquals(caves.size, combat.size)
        fun def(name: String) = combat[caveTypes.find(name)!!]!!
        for (name in caves.keys) {
            assertEquals(CombatMath.npcMaxHit(caveTypes.find(name)!!.stats.strength, def(name).strengthBonus), def(name).maxHit, name)
        }
        val shown = listOf("swamp_cave_bug", "swamp_cave_slime", "slayer_cave_crawler_1", "medium_frog", "giant_frog", "cave_goblin")
        assertEquals(listOf(1, 2, 3, 3, 9, 1), shown.map { def(it).maxHit })
        assertEquals(listOf(7, 17, 10, 0, 0, 0), shown.map { def(it).slayerLevel })
        assertEquals(listOf(5, MeleeCombat.REGEN_TICKS, 5, MeleeCombat.REGEN_TICKS, MeleeCombat.REGEN_TICKS, MeleeCombat.REGEN_TICKS), shown.map { def(it).regenTicks })
        assertEquals(listOf(true, false, true, false, false, false), shown.map { def(it).aggression != null })
        assertEquals(listOf(0, 15, 40, 0, 0, 0), shown.map { def(it).poison })
        assertEquals(listOf(35, 15, 15, 50, 50, 50), shown.map { def(it).respawnTicks })
        assertEquals(ElementalWeakness(Element.EARTH, 50), def("swamp_cave_slime").weakness)
        assertEquals(ElementalWeakness(Element.FIRE, 40), def("cave_goblin4").weakness)
        assertEquals(NpcCombatAnims(6079, 6080, 6082), def("swamp_cave_bug").anims)
        assertEquals(NpcCombatAnims(227, 1504, 228), def("slayer_cave_crawler_3").anims)
        assertEquals(NpcCombatAnims(1793, 1794, 1795), def("giant_frog").anims)
    }

    @Test
    fun `rockslugs stop at 1 hitpoint and wall beasts out of their holes need Slayer 35, as the Wiki has them`() {
        // The 242 cache's Strength levels.
        val names = mapOf("slayer_rockslug" to 27, "slayer_rockslug_baby" to 27, "swamp_wallbeast_combat" to 30)
        val slayerTypes = NpcTypes.of(*names.entries.mapIndexed { i, (name, str) -> NpcType(2800 + i, name, internalName = name, stats = NpcStats(strength = str)) }.toTypedArray())
        val combat = NpcCombat.load(slayerTypes, seqs)
        assertEquals(names.size, combat.size)
        fun def(name: String) = combat[slayerTypes.find(name)!!]!!
        for (name in names.keys) {
            assertEquals(CombatMath.npcMaxHit(slayerTypes.find(name)!!.stats.strength, def(name).strengthBonus), def(name).maxHit, name)
        }
        val shown = listOf("slayer_rockslug", "swamp_wallbeast_combat")
        assertEquals(listOf(4, 4), shown.map { def(it).maxHit })
        assertEquals(listOf(20, 35), shown.map { def(it).slayerLevel })
        assertEquals(listOf(1, 0), shown.map { def(it).minHitpoints })
        assertEquals(listOf(false, true), shown.map { def(it).aggression != null })
        assertEquals(1, def("slayer_rockslug_baby").minHitpoints)
        assertEquals(ElementalWeakness(Element.EARTH, 25), def("slayer_rockslug").weakness)
        assertEquals(NpcCombatAnims(1567, 1565, 1568), def("slayer_rockslug").anims)
        assertEquals(NpcCombatAnims(1802, 1803, 1804), def("swamp_wallbeast_combat").anims)
    }

    @Test
    fun `farmers stab at speed 6 for at most 1, with no defence bonuses`() {
        val names = listOf("farmer1", "farmer2", "farmer3", "farmer1_f", "farmer2_f", "farmer3_f", "farmer4")
        val farmerTypes = NpcTypes.of(*names.mapIndexed { i, name -> NpcType(3114 + i, name, internalName = name, stats = NpcStats(strength = 4)) }.toTypedArray())
        val combat = NpcCombat.load(farmerTypes, seqs)
        assertEquals(names.size, combat.size)
        for (name in names) {
            val def = combat[farmerTypes.find(name)!!]!!
            assertEquals(listOf(6, 1, 5, 6, 25), listOf(def.attackSpeed, def.maxHit, def.attackBonus, def.strengthBonus, def.respawnTicks), name)
            assertEquals(CombatMath.npcMaxHit(4, def.strengthBonus), def.maxHit, name)
            assertTrue(def.aggression == null, name)
        }
    }

    @Test
    fun `Thieving 2's Kandarin and Wilderness NPCs fight as the Wiki has them, the humans' max hits the formula's`() {
        // The 242 cache's Strength levels; the gnomes have none (1).
        val strength = mapOf(
            "rogue" to 13, "knight_of_ardougne" to 40, "paladin" to 54, "paladin2" to 54, "hero" to 55, "yanille_watchman" to 31,
            "gnome" to 1, "browclothedgnome" to 1, "darkskinned_gnome" to 1, "gnomefemale" to 1, "gnomefemale_dskinned" to 1,
            "gnomechild" to 1, "gnomechildblue" to 1, "gnomechildgreen" to 1,
        )
        val npcTypes = NpcTypes.of(*strength.entries.mapIndexed { i, (name, str) -> NpcType(5000 + i, name, internalName = name, stats = NpcStats(strength = str)) }.toTypedArray())
        val combat = NpcCombat.load(npcTypes, seqs)
        assertEquals(strength.size, combat.size)
        fun def(name: String) = combat[npcTypes.find(name)!!]!!
        // speed, max hit, attack bonus, strength bonus, respawn
        val wiki = mapOf(
            "rogue" to listOf(4, 2, 5, 5, 50), "knight_of_ardougne" to listOf(5, 6, 8, 10, 25), "paladin" to listOf(5, 8, 20, 22, 50),
            "paladin2" to listOf(5, 8, 20, 22, 50), "hero" to listOf(5, 9, 20, 22, 50), "yanille_watchman" to listOf(4, 4, 0, 0, 50),
            "gnome" to listOf(4, 1, -42, -42, 60), "gnomefemale" to listOf(4, 1, -42, -42, 50), "gnomechild" to listOf(4, 0, -42, -42, 50),
        )
        for ((name, row) in wiki) {
            val d = def(name)
            assertEquals(row, listOf(d.attackSpeed, d.maxHit, d.attackBonus, d.strengthBonus, d.respawnTicks), name)
            assertTrue(d.aggression == null, name)
        }
        // The humans' max hits are the formula's; the gnomes' -42 gives 0, and the Wiki's 1 stands for the men and women.
        for (name in listOf("rogue", "knight_of_ardougne", "paladin", "paladin2", "hero", "yanille_watchman")) {
            assertEquals(CombatMath.npcMaxHit(strength.getValue(name), def(name).strengthBonus), def(name).maxHit, name)
        }
        assertEquals(87, def("paladin").defence.stab)
        assertEquals(-11, def("knight_of_ardougne").defence.magic)
    }

    @Test
    fun `the HAM guards hit with crush at the Wiki's speeds and max hits, unarmed`() {
        // The 242 cache's Strength levels.
        val names = mapOf("favour_guard_male_no_beard" to 12, "favour_guard_male_bearded" to 18, "favour_guard_long_beard" to 20)
        val guardTypes = NpcTypes.of(*names.entries.mapIndexed { i, (name, str) -> NpcType(2900 + i, name, internalName = name, stats = NpcStats(strength = str)) }.toTypedArray())
        val combat = NpcCombat.load(guardTypes, seqs)
        assertEquals(names.size, combat.size)
        fun def(name: String) = combat[guardTypes.find(name)!!]!!
        assertEquals(listOf(7, 6, 5), names.keys.map { def(it).attackSpeed })
        assertEquals(listOf(2, 3, 3), names.keys.map { def(it).maxHit })
        for ((name, str) in names) assertEquals(CombatMath.npcMaxHit(str, def(name).strengthBonus), def(name).maxHit, name)
        assertTrue(names.keys.all { def(it).aggression == null && def(it).respawnTicks == 25 })
        assertEquals(NpcCombatAnims(422, 424, 836), def("favour_guard_long_beard").anims)
    }

    @Test
    fun `the HAM Storerooms' guards hit as the level 22 hideout guards do`() {
        val names = (1..5).map { "dttd_ham_guard_${it}_postquest" }
        val guardTypes = NpcTypes.of(*names.mapIndexed { i, name -> NpcType(4522 + i, name, internalName = name, stats = NpcStats(strength = 20)) }.toTypedArray())
        val combat = NpcCombat.load(guardTypes, seqs)
        assertEquals(names.size, combat.size)
        for (name in names) {
            val def = combat[guardTypes.find(name)!!]!!
            assertEquals(5 to 3, def.attackSpeed to def.maxHit, name)
            assertTrue(def.aggression == null && def.respawnTicks == 25, name)
        }
    }

    @Test
    fun `the black bear on the Varrock road slashes for 3 at speed 4, weak to fire, and waits to be attacked`() {
        val bearTypes = NpcTypes.of(NpcType(2839, "darkbear", internalName = "darkbear", stats = NpcStats(strength = 16)))
        val def = NpcCombat.load(bearTypes, seqs)[bearTypes.find("darkbear")!!]!!
        assertEquals(CombatMath.npcMaxHit(16, def.strengthBonus), def.maxHit)
        assertEquals(listOf(4, 3, 50), listOf(def.attackSpeed, def.maxHit, def.respawnTicks))
        assertEquals(ElementalWeakness(Element.FIRE, 30), def.weakness)
        assertNull(def.aggression)
        assertEquals(NpcCombatAnims(4925, 4927, 4929), def.anims)
    }

    @Test
    fun `Silvarea's bats stab for 1 at speed 4, weak to air, back after 35 ticks and never attacking first`() {
        val batTypes = NpcTypes.of(NpcType(2827, "Bat", internalName = "small_bat", stats = NpcStats(strength = 5)))
        val def = NpcCombat.load(batTypes, seqs)[batTypes.find("small_bat")!!]!!
        assertEquals(CombatMath.npcMaxHit(5, def.strengthBonus), def.maxHit)
        assertEquals(listOf(4, 1, 35), listOf(def.attackSpeed, def.maxHit, def.respawnTicks))
        assertEquals(ElementalWeakness(Element.AIR, 35), def.weakness)
        assertNull(def.aggression)
        assertEquals(NpcCombatAnims(4915, 4916, 4917), def.anims)
    }

    @Test
    fun `Tolna's rift's monsters hit as their levels give at speed 4, attack first and are back after 25 ticks`() {
        // Strength 41 for the Anger room's, 48, 55 and 62 for the fear reaper, confusion beast and hopeless creature.
        val rift = mapOf(
            "soulbane_bear" to 41, "soulbane_unicorn" to 41, "soulbane_rat" to 41, "soulbane_rat2" to 41, "soulbane_goblin" to 41,
            "soulbane_fear_beast" to 48, "soulbane_conf_beast" to 55, "soulbane_less_beast3" to 62,
        )
        val riftTypes = NpcTypes.of(*rift.entries.mapIndexed { i, (name, str) -> NpcType(4688 + i, name, internalName = name, stats = NpcStats(strength = str)) }.toTypedArray())
        val combat = NpcCombat.load(riftTypes, seqs)
        val maxHits = mutableListOf<Int>()
        for ((name, str) in rift) {
            val def = combat[riftTypes.find(name)!!]!!
            assertEquals(CombatMath.npcMaxHit(str, def.strengthBonus), def.maxHit, name)
            assertEquals(listOf(4, 25), listOf(def.attackSpeed, def.respawnTicks), name)
            assertTrue(def.aggression != null, name)
            maxHits += def.maxHit
        }
        assertEquals(listOf(5, 5, 5, 5, 5, 6, 6, 7), maxHits)
        assertEquals(NpcCombatAnims(4925, 4927, 4929), combat[riftTypes.find("soulbane_bear")!!]!!.anims)
        assertEquals(NpcCombatAnims(3816, 3820, 3821), combat[riftTypes.find("soulbane_conf_beast")!!]!!.anims)
        assertEquals(ElementalWeakness(Element.FIRE, 40), combat[riftTypes.find("soulbane_less_beast3")!!]!!.weakness)
        assertNull(combat[riftTypes.find("soulbane_fear_beast")!!]!!.weakness)
    }

    @Test
    fun `the highwayman stabs for 1 at speed 4, aggressive, back after 100 ticks`() {
        val types = NpcTypes.of(NpcType(518, "Highwayman", internalName = "highwayman", stats = NpcStats(strength = 2)))
        val def = NpcCombat.load(types, seqs)[types.find("highwayman")!!]!!
        assertEquals(CombatMath.npcMaxHit(2, def.strengthBonus), def.maxHit)
        assertEquals(listOf(4, 1, 100), listOf(def.attackSpeed, def.maxHit, def.respawnTicks))
        assertEquals(listOf(6, 7), listOf(def.attackBonus, def.strengthBonus))
        assertTrue(def.aggression != null)
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
