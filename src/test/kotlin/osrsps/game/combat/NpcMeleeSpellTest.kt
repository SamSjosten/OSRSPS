package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.Equipment
import osrsps.game.GameRandom
import osrsps.game.Movement
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.SeqNames
import osrsps.game.Skill
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.Npcs
import osrsps.game.zone.Projectile
import osrsps.map.CollisionMap

/**
 * A melee NPC's spell (Edgeville step 13b, the Catablepon's Weaken): in reach it casts instead of swinging, each cast
 * lowers Strength from where it is, and it stops casting once 8% + 3 of the level is gone.
 */
class NpcMeleeSpellTest {
    private var tick = 0L
    private val glarer = NpcType(600, "Glarer", ops = listOf(null, "Attack", null, null, null), internalName = "glarer", stats = NpcStats(hitpoints = 50, attack = 1, magic = 99))
    private val npcTypes = NpcTypes.of(glarer)
    private val seqs = SeqNames(mapOf("swing" to 4271, "glare" to 4272, "parry" to 4273, "death" to 4270))

    private fun combatFile(chance: Int) = NpcCombat.parse(
        """{ "npcs": [ { "npcs": ["glarer"], "attackSpeed": 4, "maxHit": 1, "attackType": "stab", "attackBonus": -500, "strengthBonus": 0,
            "magicAttack": 5000,
            "defence": { "stab": 0, "slash": 0, "crush": 0, "magic": 0, "light": 0, "standard": 0, "heavy": 0 },
            "anims": { "attack": "swing", "defend": "parry", "death": "death" }, "respawnTicks": 20, "source": "test",
            "spell": { "anim": "glare", "projectile": 106, "impact": 107, "maxHit": 0, "chance": $chance,
              "drain": { "skill": "strength", "percent": 5, "stacks": true }, "stopsAt": { "percent": 8, "flat": 3 } } } ] }""",
        npcTypes, seqs,
    )

    private val map = CollisionMap().apply { markLoaded(50, 50) }
    private val npcs = Npcs(map, GameRandom(1))
    private val projectiles = mutableListOf<Projectile>()
    private var protectFromMagic = false
    private val alice = Player(1, "alice", 3212, 3210, 0)

    private fun melee(chance: Int) = MeleeCombat(
        npcs, map, Movement(map), Combat(Equipment(ObjTypes.of()), AttackStyles.DEFAULT, combatFile(chance)), GameRandom(5), { tick },
        removeNpc = { npcs.remove(it) }, spawnNpc = {},
        players = { sequenceOf(alice) },
        protects = { _, type -> protectFromMagic && type == AttackType.MAGIC },
        projectile = { projectiles += it },
    )

    private fun fight(chance: Int, ticks: Int, each: (Int, Npc) -> Unit = { _, _ -> }) {
        val melee = melee(chance)
        val npc = npcs.add(NpcSpawn(glarer, 3213, 3210, source = "test"))!!
        melee.provoke(npc, alice, tick)
        for (t in 1..ticks) {
            tick++
            melee.process()
            npcs.process()
            each(t, npc)
            alice.effects.flush({}, { _, _ -> }, {}, {})
            npc.effects.flush({}, {}, {})
            alice.hitpoints = 10
        }
    }

    @Test
    fun `Weaken lowers Strength 5% a cast from where it is, and stops once 8% + 3 is drained`() {
        alice.skills.setLevel(Skill.STRENGTH, 99)
        val anims = ArrayList<Int?>()
        fight(chance = 1, ticks = 24) { t, npc -> if (t % 4 == 1) anims += npc.effects.anim?.id }
        // 4 a cast: 95, 91, 87; 12 drained is past 10 (8% of 99 is 7, plus 3), so it only swings after.
        assertEquals(87, alice.skills.currentLevel(Skill.STRENGTH))
        assertEquals(listOf<Int?>(4272, 4272, 4272, 4271, 4271, 4271), anims)
        assertEquals(3, projectiles.size)
        assertTrue(projectiles.all { it.id == 106 })
    }

    @Test
    fun `Protect from Magic blocks Weaken`() {
        alice.skills.setLevel(Skill.STRENGTH, 99)
        protectFromMagic = true
        fight(chance = 1, ticks = 12)
        assertEquals(99, alice.skills.currentLevel(Skill.STRENGTH))
    }

    @Test
    fun `one turn in 4 it glares, the rest it swings`() {
        alice.skills.setLevel(Skill.STRENGTH, 99)
        var glares = 0
        var swings = 0
        fight(chance = 4, ticks = 400) { t, npc ->
            if (t % 4 == 1) {
                when (npc.effects.anim?.id) {
                    4272 -> glares++
                    4271 -> swings++
                }
            }
            // Strength back each turn so the spell never stops.
            alice.skills.setCurrentLevel(Skill.STRENGTH, 99)
        }
        assertEquals(100, glares + swings)
        assertTrue(glares in 10..45, "glares $glares of 100")
    }
}
