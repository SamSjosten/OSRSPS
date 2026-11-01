package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
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

/** Magic and ranged NPCs (Draynor piece step 9): a dark wizard's spells, as World runs the NPC phase. */
class NpcRangedAttackTest {
    private var tick = 0L
    private val wizard = NpcType(512, "Dark wizard", ops = listOf(null, "Attack", null, null, null), combatLevel = 7, internalName = "young_dark_wizard", stats = NpcStats(hitpoints = 12, magic = 6))
    private val confuser = NpcType(513, "Confuser", ops = listOf(null, "Attack", null, null, null), internalName = "confuser", stats = NpcStats(hitpoints = 12, magic = 99))
    private val npcTypes = NpcTypes.of(wizard, confuser)

    private fun entry(name: String, attackBonus: Int, attacks: String) =
        """{ "npcs": ["$name"], "attackSpeed": 4, "maxHit": 2, "attackType": "magic", "attackBonus": $attackBonus, "strengthBonus": 0,
            "range": 10, "attacks": [ $attacks ],
            "defence": { "stab": 0, "slash": 0, "crush": 0, "magic": 3, "light": 0, "standard": 0, "heavy": 0 },
            "anims": { "attack": "cast", "defend": "block", "death": "death" }, "respawnTicks": 50, "source": "test" }"""

    private val seqs = SeqNames(mapOf("cast" to 711, "confuse" to 716, "block" to 424, "death" to 836))
    private val npcCombat = NpcCombat.parse(
        """{ "npcs": [
            ${entry("young_dark_wizard", 0, """{ "cast": 93, "projectile": 94, "impact": 95 }""")},
            ${entry("confuser", 5_000, """{ "anim": "confuse", "cast": 102, "projectile": 103, "impact": 104, "maxHit": 0, "drain": { "skill": "attack", "percent": 5 } }""")}
        ] }""",
        npcTypes, seqs,
    )
    private val map = CollisionMap().apply { markLoaded(50, 50) }
    private val npcs = Npcs(map, GameRandom(1))
    private val combat = Combat(Equipment(ObjTypes.of()), AttackStyles.DEFAULT, npcCombat)
    private val projectiles = mutableListOf<Projectile>()
    private var protectFromMagic = false
    private val alice = Player(1, "alice", 3212, 3210, 0)
    private val melee = MeleeCombat(
        npcs, map, Movement(map), combat, GameRandom(5), { tick }, removeNpc = { npcs.remove(it) }, spawnNpc = {},
        players = { sequenceOf(alice) },
        protects = { _, type -> protectFromMagic && type == AttackType.MAGIC },
        projectile = { projectiles += it },
    )

    private fun spawn(type: NpcType, x: Int, z: Int = 3210): Npc = npcs.add(NpcSpawn(type, x, z, source = "test"))!!

    private fun tick() {
        tick++
        melee.process()
        npcs.process()
    }

    private fun flush(npc: Npc) {
        alice.effects.flush({}, { _, _ -> }, {}, {})
        npc.effects.flush({}, {}, {})
    }

    @Test
    fun `a dark wizard casts from where it stands every 4 ticks, and its spell lands after the magic delay`() {
        val npc = spawn(wizard, 3217)
        melee.provoke(npc, alice, tick)
        var landed = 0
        for (t in 1..9) {
            tick()
            // In range and sight, it doesn't walk up to her.
            assertEquals(3217, npc.x)
            assertNull(npc.following)
            if (t == 1 || t == 5 || t == 9) {
                assertEquals(711, npc.effects.anim?.id, "the cast on tick $t")
                val shot = projectiles.last()
                assertEquals(listOf(94, 3217, 3212, npc.index + 1, -(alice.index + 1)), listOf(shot.id, shot.fromX, shot.toX, shot.sourceIndex, shot.targetIndex))
            }
            // 5 tiles apart: 1 + (1 + 5) / 3 = 3 ticks on.
            if (t == 4 || t == 8) {
                val hit = alice.effects.pendingHits.singleOrNull()
                val spot = alice.effects.pendingSpotanims.values.single()
                if (hit != null) {
                    landed++
                    assertEquals(npc.index, hit.source)
                    assertTrue(hit.value in 0..2)
                    assertEquals(95, spot.id)
                } else {
                    assertEquals(MeleeCombat.SPLASH, spot.id)
                }
                assertEquals(npc, alice.attackedBy)
            } else {
                assertTrue(alice.effects.pendingHits.isEmpty(), "no hit on tick $t")
            }
            flush(npc)
        }
        assertEquals(3, projectiles.size)
        assertTrue(alice.hitpoints in 6..10 && landed <= 2)
    }

    @Test
    fun `out of its range it walks after the player, and stops once in range`() {
        val npc = spawn(wizard, 3224)
        melee.provoke(npc, alice, tick)
        tick()
        assertEquals(alice, npc.following)
        assertTrue(projectiles.isEmpty())
        repeat(6) { tick() }
        assertTrue(npc.x <= 3223, "it came closer: ${npc.x}")
        assertNull(npc.following)
        assertTrue(projectiles.isNotEmpty())
    }

    @Test
    fun `a wandering caster stands still while it fights, and keeps the fight its own`() {
        val npc = npcs.add(NpcSpawn(wizard, 3217, 3210, wander = 4, source = "test"))!!
        melee.provoke(npc, alice, tick)
        repeat(40) {
            tick()
            assertEquals(3217 to 3210, npc.x to npc.z, "on tick $tick")
            assertNull(npc.destination)
            // It refreshes its fight with every cast, as a melee swing does.
            assertTrue(tick - npc.fightTick <= 4, "fought on tick ${npc.fightTick}, now $tick")
            flush(npc)
            alice.hitpoints = 10
        }
    }

    @Test
    fun `a spell that leaves no hitsplat still sets an auto-retaliating player on the caster`() {
        alice.skills.setLevel(Skill.ATTACK, 40)
        val npc = spawn(confuser, 3216)
        melee.provoke(npc, alice, tick)
        assertTrue(alice.autoRetaliate)
        repeat(4) { tick(); flush(npc) }
        assertEquals(38, alice.skills.currentLevel(Skill.ATTACK), "Confuse landed")
        assertEquals(npc, alice.attackedBy)
        assertTrue(alice.interaction != null, "she turns on it")
    }

    @Test
    fun `Confuse lowers Attack 5% with no hitsplat, once, and Protect from Magic blocks it`() {
        alice.skills.setLevel(Skill.ATTACK, 40)
        val npc = spawn(confuser, 3216)
        melee.provoke(npc, alice, tick)
        tick()
        assertEquals(716, npc.effects.anim?.id, "the confuse animation")
        assertEquals(103, projectiles.single().id)
        flush(npc)
        repeat(3) { tick(); assertTrue(alice.effects.pendingHits.isEmpty()); flush(npc) }
        assertEquals(38, alice.skills.currentLevel(Skill.ATTACK))
        repeat(8) { tick(); flush(npc) }
        assertEquals(38, alice.skills.currentLevel(Skill.ATTACK), "a second Confuse does nothing")
        alice.skills.setLevel(Skill.ATTACK, 40)
        protectFromMagic = true
        repeat(8) { tick(); flush(npc) }
        assertEquals(40, alice.skills.currentLevel(Skill.ATTACK))
        assertEquals(10, alice.hitpoints)
    }

    @Test
    fun `attacks are refused on a melee NPC`() {
        val error = assertFailsWith<IllegalArgumentException> {
            NpcCombat.parse(
                """{ "npcs": [ { "npcs": ["confuser"], "attackSpeed": 4, "maxHit": 1, "attackType": "crush", "attackBonus": 0, "strengthBonus": 0,
                    "attacks": [ { "projectile": 1 } ],
                    "defence": { "stab": 0, "slash": 0, "crush": 0, "magic": 0, "light": 0, "standard": 0, "heavy": 0 },
                    "anims": { "attack": "cast", "defend": "block", "death": "death" }, "respawnTicks": 5, "source": "test" } ] }""",
                npcTypes, seqs,
            )
        }
        assertTrue("attacks on a CRUSH NPC" in error.message.orEmpty(), error.message)
    }
}
