package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import osrsps.game.NpcStat
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.SeqNames
import osrsps.game.WearPos
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn

/** The special attack bar: lighting it, paying for a special, energy coming back, and the warhammer's Smash landing. */
class SpecialAttacksTest {
    private val warhammer = ObjType(13576, "Dragon warhammer", wearpos1 = WearPos.WEAPON, internalName = "dragon_warhammer")
    private val dagger = ObjType(1215, "Dragon dagger", wearpos1 = WearPos.WEAPON, internalName = "dragon_dagger")
    private val bronzeSword = ObjType(1277, "Bronze sword", wearpos1 = WearPos.WEAPON, internalName = "bronze_sword")
    private val costs = mapOf(warhammer.id to 500, dagger.id to 250)

    private val ogre = NpcType(2095, "Ogre", combatLevel = 53, internalName = "ogre", stats = NpcStats(attack = 43, defence = 75, strength = 43, hitpoints = 60))
    private val boss = NpcType(16659, "Archive Guardian", combatLevel = 400, internalName = "archive_guardian", stats = NpcStats(attack = 200, defence = 75, hitpoints = 1000))
    private val npcCombat = NpcCombat.parse(
        """{ "npcs": [ ${entry("ogre")}, ${entry("archive_guardian", """"drainCap": { "defence": 60 },""")} ] }""",
        NpcTypes.of(ogre, boss), SeqNames.NONE,
    )

    private fun entry(name: String, extra: String = "") = """{ "npcs": ["$name"], "attackSpeed": 4, "maxHit": 1, "attackType": "crush",
        "attackBonus": 0, "strengthBonus": 0, $extra
        "defence": { "stab": 0, "slash": 0, "crush": 0, "magic": 0, "light": 0, "standard": 0, "heavy": 0 },
        "anims": { "attack": null, "defend": null, "death": null }, "respawnTicks": 25, "source": "test" }"""

    private val messages = mutableListOf<String>()
    private val specials = SpecialAttacks(costs, NpcDrains { npcCombat[it.type] }, message = { _, text -> messages += text })
    private val alice = Player(1, "alice", 3222, 3218, 0).also { specials.refill(it) }

    @Test
    fun `the bar lights for a weapon whose special is in, and goes out on a second click`() {
        specials.toggle(alice, warhammer)
        assertTrue(specials.active(alice))
        assertEquals(1, alice.vars[SpecialAttacks.VARP_ACTIVE])
        specials.toggle(alice, warhammer)
        assertFalse(specials.active(alice))
    }

    @Test
    fun `a weapon with no special, or one not in yet, leaves the bar unlit`() {
        specials.toggle(alice, bronzeSword)
        assertFalse(specials.active(alice))
        specials.toggle(alice, dagger)
        assertFalse(specials.active(alice), "the dragon dagger's special isn't in yet")
        specials.toggle(alice, null)
        assertFalse(specials.active(alice))
        assertEquals(emptyList(), messages)
    }

    @Test
    fun `without the energy the bar stays unlit and the player is told`() {
        specials.setEnergy(alice, 499)
        specials.toggle(alice, warhammer)
        assertFalse(specials.active(alice))
        assertEquals(listOf("[combat.spec_not_enough]"), messages)
    }

    @Test
    fun `a swing with the bar lit takes the energy and puts it out, and the next swing is ordinary`() {
        assertNull(specials.use(alice, warhammer), "not lit")
        specials.toggle(alice, warhammer)
        assertSame(SpecialAttacks.DRAGON_WARHAMMER, specials.use(alice, warhammer))
        assertEquals(500, specials.energy(alice))
        assertFalse(specials.active(alice))
        assertNull(specials.use(alice, warhammer))
        specials.toggle(alice, warhammer)
        specials.use(alice, warhammer)
        assertEquals(0, specials.energy(alice))
        specials.toggle(alice, warhammer)
        assertFalse(specials.active(alice))
    }

    @Test
    fun `a switch to a weapon with no special puts the bar out`() {
        specials.toggle(alice, warhammer)
        specials.weaponChanged(alice, warhammer)
        assertTrue(specials.active(alice))
        specials.weaponChanged(alice, bronzeSword)
        assertFalse(specials.active(alice))
        specials.toggle(alice, warhammer)
        assertNull(specials.use(alice, bronzeSword), "lit, then swung with another weapon")
        assertEquals(1000, specials.energy(alice))
    }

    @Test
    fun `energy comes back 10 percent every 50 ticks, up to full`() {
        specials.setEnergy(alice, 950)
        repeat(SpecialAttacks.REGEN_TICKS - 1) { specials.process(alice) }
        assertEquals(950, specials.energy(alice))
        assertEquals("Special attack energy 95%, 10% back in 1 ticks.", specials.describe(alice))
        specials.process(alice)
        assertEquals(1000, specials.energy(alice))
        assertEquals("Special attack energy 100%.", specials.describe(alice))
        repeat(SpecialAttacks.REGEN_TICKS * 2) { specials.process(alice) }
        assertEquals(1000, specials.energy(alice))
    }

    @Test
    fun `Smash hits half as hard again, as accurately as a swing`() {
        assertEquals(31, SpecialAttacks.DRAGON_WARHAMMER.maxHit(21))
        assertEquals(30, SpecialAttacks.DRAGON_WARHAMMER.maxHit(20))
        assertEquals(12_345, SpecialAttacks.DRAGON_WARHAMMER.attackRoll(12_345))
    }

    @Test
    fun `Smash drains 30 percent of the Defence it has now on a hit that deals damage, down to a cap`() {
        val ogre = npc(ogre)
        specials.landed(ogre, 0, SpecialAttacks.DRAGON_WARHAMMER)
        assertEquals(75, ogre.level(NpcStat.DEFENCE), "a hit of 0 drains nothing")
        specials.landed(ogre, 5, SpecialAttacks.DRAGON_WARHAMMER)
        assertEquals(53, ogre.level(NpcStat.DEFENCE))
        specials.landed(ogre, 5, SpecialAttacks.DRAGON_WARHAMMER)
        assertEquals(38, ogre.level(NpcStat.DEFENCE))
        val boss = npc(boss)
        // 75, 53, 38, then the 60% cap holds it at 30.
        repeat(4) { specials.landed(boss, 5, SpecialAttacks.DRAGON_WARHAMMER) }
        assertEquals(30, boss.level(NpcStat.DEFENCE))
    }

    private fun npc(type: NpcType) = Npc(1, NpcSpawn(type, 3222, 3218, source = "test"))
}
