package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import osrsps.game.Equipment
import osrsps.game.NpcStat
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.SeqNames
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn

/** Drained monster stats: percentages of the level now, the per-monster cap, a level back a minute, and the rolls. */
class NpcDrainsTest {
    private val ogre = NpcType(2095, "Ogre", combatLevel = 53, internalName = "ogre", stats = NpcStats(attack = 43, defence = 75, strength = 43, hitpoints = 60, magic = 30))
    private val boss = NpcType(16659, "Archive Guardian", combatLevel = 400, internalName = "archive_guardian", stats = NpcStats(attack = 200, defence = 75, hitpoints = 1000, magic = 200))
    private val types = NpcTypes.of(ogre, boss)
    private val npcCombat = NpcCombat.parse(
        """{ "npcs": [ ${entry("ogre")}, ${entry("archive_guardian", """"drainCap": { "defence": 60 },""")} ] }""",
        types, SeqNames.NONE,
    )

    private fun entry(name: String, extra: String = "") = """{ "npcs": ["$name"], "attackSpeed": 4, "maxHit": 1, "attackType": "crush",
        "attackBonus": 0, "strengthBonus": 0, $extra
        "defence": { "stab": 0, "slash": 0, "crush": 0, "magic": 0, "light": 0, "standard": 0, "heavy": 0 },
        "anims": { "attack": null, "defend": null, "death": null }, "respawnTicks": 25, "source": "test" }"""

    private val drains = NpcDrains { npcCombat[it.type] }

    private fun npc(type: NpcType) = Npc(1, NpcSpawn(type, 3222, 3218, source = "test"))

    @Test
    fun `a percentage drain takes that much of the level now, so drains stack, as the dragon warhammer's do`() {
        val npc = npc(ogre)
        // The Wiki's example: 75 Defence drained by 30% to 53, then to 38.
        assertEquals(22, drains.drainPercent(npc, NpcStat.DEFENCE, 30))
        assertEquals(53, npc.level(NpcStat.DEFENCE))
        assertEquals(15, drains.drainPercent(npc, NpcStat.DEFENCE, 30))
        assertEquals(38, npc.level(NpcStat.DEFENCE))
        assertEquals(43, npc.level(NpcStat.ATTACK), "other stats untouched")
    }

    @Test
    fun `a level can be drained to 0 with no cap, and a cap keeps the rest`() {
        val ogre = npc(ogre)
        assertEquals(75, drains.drain(ogre, NpcStat.DEFENCE, 100))
        assertEquals(0, ogre.level(NpcStat.DEFENCE))
        assertEquals(0, drains.drain(ogre, NpcStat.DEFENCE, 5))
        val boss = npc(boss)
        // 60% of 75 is 45: the Defence stops at 30.
        assertEquals(45, drains.drain(boss, NpcStat.DEFENCE, 100))
        assertEquals(30, boss.level(NpcStat.DEFENCE))
        assertEquals(0, drains.drainPercent(boss, NpcStat.DEFENCE, 30))
        assertEquals(190, drains.drain(boss, NpcStat.MAGIC, 190), "magic has no cap")
        assertEquals(10, boss.level(NpcStat.MAGIC))
    }

    @Test
    fun `drained stats come back a level a minute, and the timer stops once they are all back`() {
        val npc = npc(ogre)
        drains.drain(npc, NpcStat.DEFENCE, 2)
        drains.drain(npc, NpcStat.ATTACK, 1)
        assertEquals(-1, npc.drainRestoreTicks, "the timer waits for the tick")
        assertEquals("drained attack 42/43, defence 73/75, a level back in 100 ticks", drains.describe(npc))
        // The drain's own tick starts the timer without counting it, then a full minute.
        drains.process(npc)
        assertEquals(NpcDrains.RESTORE_TICKS, npc.drainRestoreTicks)
        repeat(NpcDrains.RESTORE_TICKS - 1) { drains.process(npc) }
        assertEquals(73, npc.level(NpcStat.DEFENCE))
        drains.process(npc)
        assertEquals(74, npc.level(NpcStat.DEFENCE))
        assertEquals(43, npc.level(NpcStat.ATTACK))
        assertEquals("drained defence 74/75, a level back in 100 ticks", drains.describe(npc))
        repeat(NpcDrains.RESTORE_TICKS) { drains.process(npc) }
        assertEquals(75, npc.level(NpcStat.DEFENCE))
        assertEquals(-1, npc.drainRestoreTicks)
        assertNull(drains.describe(npc))
    }

    @Test
    fun `a dead NPC can't be drained`() {
        val npc = npc(ogre)
        npc.dead = true
        assertEquals(0, drains.drain(npc, NpcStat.DEFENCE, 10))
    }

    @Test
    fun `combat rolls use the drained levels`() {
        val combat = Combat(Equipment(ObjTypes.of()), AttackStyles.fromRows(emptyList()), npcCombat)
        val npc = npc(ogre)
        val player = Player(1, "alice", 3222, 3218, 0)
        val before = combat.npcAttack(npc, player)!!.attackRoll
        drains.drain(npc, NpcStat.ATTACK, 43)
        assertEquals(CombatMath.npcAttackRoll(0, 0), combat.npcAttack(npc, player)!!.attackRoll)
        assertEquals(CombatMath.npcAttackRoll(43, 0), before)
        drains.drain(npc, NpcStat.MAGIC, 10)
        assertEquals(20, npc.level(NpcStat.MAGIC))
    }

    @Test
    fun `a drained Strength lowers the max hit by what the formula takes off`() {
        val combat = Combat(Equipment(ObjTypes.of()), AttackStyles.fromRows(emptyList()), npcCombat)
        val npc = npc(ogre)
        val player = Player(1, "alice", 3222, 3218, 0)
        // The file's max hit of 1 stands while the Strength does: the formula gives 5 from 43.
        assertEquals(1, combat.npcAttack(npc, player)!!.maxHit)
        drains.drain(npc, NpcStat.STRENGTH, 10)
        assertEquals(CombatMath.npcMaxHit(43, 0) - CombatMath.npcMaxHit(33, 0), 1)
        assertEquals(0, combat.npcAttack(npc, player)!!.maxHit)
        assertEquals(0, combat.matchup(player, npc)!!.npcMaxHit)
        drains.drain(npc, NpcStat.STRENGTH, 33)
        assertEquals(0, combat.npcAttack(npc, player)!!.maxHit, "never below 0")
    }
}
