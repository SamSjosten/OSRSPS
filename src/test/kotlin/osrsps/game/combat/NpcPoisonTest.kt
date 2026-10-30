package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.Equipment
import osrsps.game.GameRandom
import osrsps.game.Movement
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.Npcs
import osrsps.map.CollisionMap

/**
 * Poison and venom on NPCs: their hits every 30 ticks, the immunities in the combat file, a poison as strong starting
 * again, venom never turned back into poison; which weapons poison and how often; and monsters whose attacks poison.
 */
class NpcPoisonTest {
    private val man = NpcType(3106, "Man", combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 50))
    private val cow = NpcType(2790, "Cow", combatLevel = 2, internalName = "cow", stats = NpcStats(hitpoints = 8))
    private val chicken = NpcType(1173, "Chicken", combatLevel = 1, internalName = "chicken", stats = NpcStats(hitpoints = 3))
    private val spider = NpcType(3019, "Poison spider", combatLevel = 64, internalName = "poison_spider", stats = NpcStats(hitpoints = 64))
    private val types = NpcTypes.of(man, cow, chicken, spider)
    private val npcCombat = NpcCombat.parse(
        """{ "npcs": [
            ${entry("man")},
            ${entry("cow", """"immune": ["poison", "venom"],""")},
            ${entry("chicken", """"immune": ["poison", "venom"],""")},
            ${entry("poison_spider", """"poison": 6,""")}
        ] }""",
        types, SeqNames.NONE,
    )

    private fun entry(name: String, extra: String = "") = """{ "npcs": ["$name"], "attackSpeed": 4, "maxHit": 1, "attackType": "crush",
        "attackBonus": 0, "strengthBonus": 0, $extra
        "defence": { "stab": 0, "slash": 0, "crush": 0, "magic": 0, "light": 0, "standard": 0, "heavy": 0 },
        "anims": { "attack": null, "defend": null, "death": null }, "respawnTicks": 25, "source": "test" }"""

    private val hits = mutableListOf<Pair<Int, Int>>()
    private val poison = Poison(
        hitNpc = { npc, damage, hitsplat ->
            hits += damage to hitsplat
            npc.hitpoints -= damage
        },
        npcCombat = { npcCombat[it.type] },
    )

    private fun npc(type: NpcType) = Npc(1, NpcSpawn(type, 3222, 3218, source = "test"))

    private fun run(npc: Npc, ticks: Int) = repeat(ticks) { poison.process(npc) }

    @Test
    fun `the combat file gives a monster's poison as five times its starting damage, and its immunities`() {
        assertEquals(30, npcCombat[spider]!!.poison)
        assertEquals(0, npcCombat[man]!!.poison)
        assertTrue(npcCombat[cow]!!.poisonImmune && npcCombat[cow]!!.venomImmune)
        assertTrue(npcCombat[chicken]!!.poisonImmune && npcCombat[chicken]!!.venomImmune)
    }

    @Test
    fun `a poisoned NPC is hit every 30 ticks, going down as a player's poison does, and the poison runs out`() {
        val npc = npc(man)
        assertTrue(poison.poison(npc, 6))
        assertEquals("poisoned, hitting 2 in 30 ticks", poison.describe(npc))
        // The tick it was poisoned on starts the timer; the first hit is a full 30 ticks after it.
        run(npc, 1)
        assertEquals("poisoned, hitting 2 in 30 ticks", poison.describe(npc))
        run(npc, Poison.TICKS - 1)
        assertTrue(hits.isEmpty())
        run(npc, 1)
        assertEquals(listOf(2 to Poison.POISON_HITSPLAT), hits)
        run(npc, Poison.TICKS * 5)
        assertEquals(listOf(2, 1, 1, 1, 1, 1), hits.map { it.first })
        assertEquals(0, npc.poison)
        assertNull(poison.describe(npc))
        run(npc, Poison.TICKS)
        assertEquals(6, hits.size)
    }

    @Test
    fun `poisoned again the tick after its poison ran out, an NPC still waits a full 30 ticks`() {
        val npc = npc(man)
        poison.poison(npc, 1)
        run(npc, 1 + Poison.TICKS)
        assertEquals(listOf(1 to Poison.POISON_HITSPLAT), hits)
        assertEquals(0, npc.poison)
        // The next tick a weapon hit lands before the poison is processed.
        assertTrue(poison.poison(npc, 6))
        run(npc, 1 + Poison.TICKS - 1)
        assertEquals(1, hits.size, "no hit before 30 ticks")
        run(npc, 1)
        assertEquals(listOf(1, 2), hits.map { it.first })
    }

    @Test
    fun `a poison as strong starts it again, a weaker one does nothing`() {
        val npc = npc(man)
        poison.poison(npc, 20)
        run(npc, 1 + Poison.TICKS)
        assertEquals(19, npc.poison)
        assertFalse(poison.poison(npc, 6))
        assertTrue(poison.poison(npc, 20))
        assertEquals(20, npc.poison)
    }

    @Test
    fun `venom hits 6 and 2 more each time, replaces poison, and poison never turns it back`() {
        val npc = npc(man)
        poison.poison(npc, 30)
        assertTrue(poison.envenom(npc))
        assertFalse(poison.poison(npc, 30))
        assertFalse(poison.envenom(npc), "envenomed already")
        run(npc, 1 + Poison.TICKS * 3)
        assertEquals(listOf(6 to Poison.VENOM_HITSPLAT, 8 to Poison.VENOM_HITSPLAT, 10 to Poison.VENOM_HITSPLAT), hits)
    }

    @Test
    fun `cows and chickens can't be poisoned or envenomed`() {
        assertFalse(poison.poison(npc(cow), 20))
        assertFalse(poison.envenom(npc(cow)))
        assertFalse(poison.poison(npc(chicken), 20))
        assertFalse(poison.envenom(npc(chicken)))
    }

    @Test
    fun `a dead NPC takes no poison, and its timer stops`() {
        val npc = npc(man)
        poison.poison(npc, 20)
        npc.dead = true
        run(npc, Poison.TICKS * 2)
        assertTrue(hits.isEmpty())
        assertEquals(-1, npc.poisonTicks)
        assertFalse(poison.poison(npc, 20))
    }

    @Test
    fun `weapons ending (p), (p+) and (p++) poison at 20, 25 and 30, ranged ones 14 less`() {
        fun obj(name: String) = ObjType(1, name)
        assertEquals(20, PoisonedWeapons.melee(obj("Bronze dagger(p)")))
        assertEquals(25, PoisonedWeapons.melee(obj("Dragon dagger(p+)")))
        assertEquals(30, PoisonedWeapons.melee(obj("Dragon dagger(p++)")))
        assertEquals(0, PoisonedWeapons.melee(obj("Dragon dagger")))
        assertEquals(0, PoisonedWeapons.melee(null))
        assertEquals(6, PoisonedWeapons.ranged(obj("Bronze arrow(p)")))
        assertEquals(11, PoisonedWeapons.ranged(obj("Rune dart(p+)")))
        assertEquals(16, PoisonedWeapons.ranged(obj("Dragon bolts(p++)")))
        assertEquals(0, PoisonedWeapons.ranged(obj("Bronze arrow")))
        // The starting damage the Wiki gives: 4, 5 and 6 for melee, 2, 3 and 4 for ranged.
        assertEquals(listOf(4, 5, 6, 2, 3, 4), listOf(20, 25, 30, 6, 11, 16).map(Poison::damage))
    }

    private val random = GameRandom(7)
    private val poisoned = mutableListOf<Int>()
    private var envenomed = 0
    private val map = CollisionMap().apply { markLoaded(50, 50) }
    private val melee = MeleeCombat(
        Npcs(map, GameRandom(1)), map, Movement(map), Combat(Equipment(ObjTypes.of()), AttackStyles.fromRows(emptyList()), npcCombat),
        random, { 0 }, removeNpc = {}, spawnNpc = {},
        poisonNpc = { _, severity -> poisoned += severity }, envenomNpc = { envenomed++ },
    )

    @Test
    fun `a damaging melee hit poisons one time in four, a ranged one in eight, the blowpipe envenoms one in four, and a 0 never`() {
        val npc = npc(man)
        repeat(8000) { melee.weaponPoison(npc, 1, 30, venom = false, ranged = false) }
        assertEquals(2000.0, poisoned.size.toDouble(), 120.0)
        assertTrue(poisoned.all { it == 30 })
        poisoned.clear()
        repeat(8000) { melee.weaponPoison(npc, 1, 16, venom = false, ranged = true) }
        assertEquals(1000.0, poisoned.size.toDouble(), 90.0)
        poisoned.clear()
        repeat(8000) { melee.weaponPoison(npc, 1, 0, venom = true, ranged = true) }
        assertEquals(2000.0, envenomed.toDouble(), 120.0)
        assertTrue(poisoned.isEmpty())
        envenomed = 0
        repeat(100) { melee.weaponPoison(npc, 0, 30, venom = true, ranged = false) }
        repeat(100) { melee.weaponPoison(npc, 3, 0, venom = false, ranged = false) }
        assertTrue(poisoned.isEmpty() && envenomed == 0, "a 0 hit, or an unpoisoned weapon, does nothing")
    }
}
