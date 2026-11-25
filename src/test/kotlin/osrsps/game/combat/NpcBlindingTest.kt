package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import osrsps.game.Equipment
import osrsps.game.GameRandom
import osrsps.game.InvObj
import osrsps.game.Movement
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.SeqNames
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.Npcs
import osrsps.game.zone.Projectile
import osrsps.map.CollisionMap

/**
 * A melee NPC's ranged spit that burns the eyes (post-quest step 8, the sourhog's): without reinforced goggles it hits 20
 * to 30 and drains Attack and Defence 90%; with them it is a plain spit of up to 6; Protect from Missiles blocks it.
 */
class NpcBlindingTest {
    private var tick = 0L
    private val spitter = NpcType(601, "Spitter", ops = listOf(null, "Attack", null, null, null), internalName = "spitter", stats = NpcStats(hitpoints = 50, attack = 1, ranged = 99))
    private val npcTypes = NpcTypes.of(spitter)
    private val seqs = SeqNames(mapOf("swing" to 8769, "spit" to 8770, "death" to 8771))

    private val combatFile = NpcCombat.parse(
        """{ "npcs": [ { "npcs": ["spitter"], "attackSpeed": 5, "maxHit": 6, "attackType": "crush", "attackBonus": -500, "strengthBonus": 0,
            "rangedAttack": 5000,
            "defence": { "stab": 0, "slash": 0, "crush": 0, "magic": 0, "light": 0, "standard": 0, "heavy": 0 },
            "anims": { "attack": "swing", "defend": null, "death": "death" }, "respawnTicks": 15, "source": "test",
            "spell": { "type": "ranged", "anim": "spit", "projectile": 1817, "impact": 1818, "maxHit": 6, "chance": 1 },
            "blinds": { "protection": [$GOGGLES], "min": 20, "max": 30, "drainPercent": 90, "skills": ["attack", "defence"] } } ] }""",
        npcTypes, seqs,
    )

    private val map = CollisionMap().apply { markLoaded(50, 50) }
    private val npcs = Npcs(map, GameRandom(1))
    private val projectiles = mutableListOf<Projectile>()
    private var protectFromMissiles = false
    private val alice = Player(1, "alice", 3212, 3210, 0).apply {
        for (skill in listOf(Skill.ATTACK, Skill.DEFENCE, Skill.HITPOINTS)) skills.setLevel(skill, 99)
    }

    /** One spit at alice: what it took off her. */
    private fun spit(): Int {
        val melee = MeleeCombat(
            npcs, map, Movement(map), Combat(Equipment(ObjTypes.of()), AttackStyles.DEFAULT, combatFile), GameRandom(5), { tick },
            removeNpc = { npcs.remove(it) }, spawnNpc = {},
            players = { sequenceOf(alice) },
            protects = { _, type -> protectFromMissiles && type == AttackType.RANGED },
            projectile = { projectiles += it },
        )
        val npc = npcs.add(NpcSpawn(spitter, 3213, 3210, source = "test"))!!
        melee.provoke(npc, alice, tick)
        for (t in 1..4) {
            tick++
            melee.process()
            npcs.process()
            alice.effects.flush({}, { _, _ -> }, {}, {})
            npc.effects.flush({}, {}, {})
        }
        assertEquals(listOf(1817), projectiles.map { it.id })
        return 99 - alice.hitpoints
    }

    @Test
    fun `without goggles the spit hits 20 to 30 and drains Attack and Defence 90%`() {
        val damage = spit()
        assertTrue(damage in 20..30, "hit $damage")
        assertEquals(10, alice.skills.currentLevel(Skill.ATTACK))
        assertEquals(10, alice.skills.currentLevel(Skill.DEFENCE))
    }

    @Test
    fun `goggles worn make it a plain spit`() {
        alice.equipment[WearPos.HEAD] = InvObj(GOGGLES, 1)
        val damage = spit()
        assertTrue(damage in 0..6, "hit $damage")
        assertEquals(99, alice.skills.currentLevel(Skill.ATTACK))
        assertEquals(99, alice.skills.currentLevel(Skill.DEFENCE))
    }

    @Test
    fun `Protect from Missiles blocks the spit and the burn`() {
        protectFromMissiles = true
        assertEquals(0, spit())
        assertEquals(99, alice.skills.currentLevel(Skill.ATTACK))
    }

    private companion object {
        const val GOGGLES = 24942
    }
}
