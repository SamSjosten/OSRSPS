package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames

/**
 * Misthalin bosses step 3's fighters from the real npc-combat.json and npc-drops.json: the caged Monk of Zamorak fights
 * and drops what the Wiki lists, and the Sheared Ram's man drops as the other men do.
 */
class MisthalinGapsDataTest {
    private val monk = NpcType(528, "Monk of Zamorak", combatLevel = 17, internalName = "chaosmonk2", stats = NpcStats(attack = 8, defence = 12, strength = 8, hitpoints = 10, magic = 25))
    private val man = NpcType(6818, "Man", combatLevel = 2, internalName = "man_indoor", stats = NpcStats(hitpoints = 7))
    private val types = NpcTypes.of(monk, man)
    private val combat = NpcCombat.load(types, SeqNames.NONE)
    private val drops = NpcDrops.load(types, DropTestItems.objTypes())

    @Test
    fun `the monk and the indoor man fight as the Wiki gives them`() {
        val monkDef = assertNotNull(combat[monk])
        assertEquals(4, monkDef.attackSpeed)
        assertEquals(5, monkDef.maxHit)
        assertEquals(AttackType.CRUSH, monkDef.attackType)
        assertEquals(250, monkDef.respawnTicks)
        assertEquals(1, assertNotNull(combat[man]).maxHit)
    }

    @Test
    fun `the monk drops bones and a robe piece a tenth of the time`() {
        val random = GameRandom(3)
        var robes = 0
        val kills = 10_000
        repeat(kills) {
            val dropped = drops[monk]!!.roll(random)
            assertTrue(dropped.any { it.type.id == BONES }, "bones every time")
            robes += dropped.count { it.type.id == MONK_BOTTOM || it.type.id == MONK_TOP }
        }
        // 2/20 of 10,000 is 1,000; four standard deviations either side.
        assertTrue(robes in 880..1120, "$robes robe pieces")
        assertNotNull(drops[man])
    }

    private companion object {
        const val BONES = 526
        const val MONK_BOTTOM = 1033
        const val MONK_TOP = 1035
    }
}
