package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import osrsps.game.Equipment
import osrsps.game.NpcType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn

/** XP paid by the damage alone, as the house's attack stones pay it ([Combat.damageXpPermille]). */
class DamageXpTest {
    private val stone = NpcType(1, "Clay attack stone")
    private val man = NpcType(2, "Man")
    private val combat = Combat(Equipment(ObjTypes.of()), AttackStyles.fromRows(emptyList()), NpcCombat.NONE).apply {
        damageXpPermille = { npc -> if (npc.type === stone) 25 else null }
    }

    private fun npc(type: NpcType) = Npc(1, NpcSpawn(type, 3222, 3218, source = "test"))

    private fun tenths(player: Player, skill: Skill) = player.skills.xp(skill)

    @Test
    fun `2,5 percent of the damage is paid, a part of a tenth carried over, and an ordinary NPC pays nothing this way`() {
        val player = Player(1, "alice", 3222, 3218, 0)
        val before = tenths(player, Skill.ATTACK)
        assertFalse(combat.damageXp(player, npc(man), listOf(Skill.ATTACK), 40))
        // Ten hits of 4: each a tenth of XP's worth (4 x 2.5% = 0.1), 1 XP in all.
        repeat(10) { assertTrue(combat.damageXp(player, npc(stone), listOf(Skill.ATTACK), 4)) }
        assertEquals(before + 1, tenths(player, Skill.ATTACK))
        // Forty hits of 1: a quarter of a tenth each, carried over, 1 XP more.
        repeat(40) { combat.damageXp(player, npc(stone), listOf(Skill.ATTACK), 1) }
        assertEquals(before + 2, tenths(player, Skill.ATTACK))
    }

    @Test
    fun `a shared style splits it evenly, each skill carrying its own part of a tenth`() {
        val player = Player(1, "alice", 3222, 3218, 0)
        val shared = listOf(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE)
        val before = shared.map { tenths(player, it) }
        // 120 hits of 4 is 480 damage, 12 XP: 4 each (Codex review on #1051: not all to Attack).
        repeat(120) { combat.damageXp(player, npc(stone), shared, 4) }
        assertEquals(before.map { it + 4 }, shared.map { tenths(player, it) })
    }
}
