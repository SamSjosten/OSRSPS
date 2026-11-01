package osrsps.game.combat

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
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
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.Npcs
import osrsps.map.CollisionMap
import osrsps.map.Tile

/**
 * NPCs retreating: attacked from outside their aggression range, or low on hitpoints, they walk away and don't fight.
 * And NPCs letting go of a player who stands off past where they can follow.
 */
class RetreatTest {
    private var tick = 0L
    private val man = NpcType(3106, "Man", combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))
    private val unicorn = NpcType(2837, "Unicorn", combatLevel = 15, internalName = "unicorn", stats = NpcStats(hitpoints = 19))
    private val spider = NpcType(3017, "Giant spider", combatLevel = 2, internalName = "giantspider1", stats = NpcStats(hitpoints = 5))
    private val types = NpcTypes.of(man, unicorn, spider)
    private val npcCombat = NpcCombat.parse(
        """{ "npcs": [ ${entry("man")}, ${entry("unicorn", """"retreatsAt": 25,""")}, ${entry("giantspider1", """"aggressive": true,""")} ] }""",
        types, SeqNames.NONE,
    )
    private val map = CollisionMap().apply { markLoaded(50, 50) }
    private val npcs = Npcs(map, GameRandom(1))
    private val alice = Player(1, "alice", 3220, 3220, 0)
    private val bob = Player(2, "bob", 3240, 3240, 0)
    private val melee = MeleeCombat(
        npcs, map, Movement(map), Combat(Equipment(ObjTypes.of()), AttackStyles.fromRows(emptyList()), npcCombat), GameRandom(5), { tick },
        removeNpc = { npcs.remove(it) }, spawnNpc = {}, players = { sequenceOf(alice, bob) },
    )
    private val aggression = Aggression(npcs, map, { npcCombat[it.type] }, melee, GameRandom(7), { tick }) { sequenceOf(alice, bob) }

    private fun entry(name: String, extra: String = "") = """{ "npcs": ["$name"], "attackSpeed": 4, "maxHit": 1, "attackType": "crush",
        "attackBonus": 0, "strengthBonus": 0, $extra
        "defence": { "stab": 0, "slash": 0, "crush": 0, "magic": 0, "light": 0, "standard": 0, "heavy": 0 },
        "anims": { "attack": null, "defend": null, "death": null }, "respawnTicks": 25, "source": "test" }"""

    private fun spawn(type: NpcType = man) = npcs.add(NpcSpawn(type, 3220, 3220, source = "test"))!!

    private fun process() {
        tick++
        aggression.process()
        melee.process()
        npcs.process()
    }

    @Test
    fun `attacked from outside its aggression range, it walks away and doesn't fight back, keeping within its max range`() {
        val npc = spawn()
        // Max range 7 plus melee's 1 is 8: a shot from 9 west.
        alice.x = 3220 - 9
        melee.hit(npc, alice, 1, tick, attackedFrom = Tile(alice.x, alice.z))
        assertSame(alice, npc.retreatingFrom)
        assertNull(npc.target)
        repeat(12) { process() }
        assertEquals(3220 + Npcs.MAX_RANGE to 3220, npc.x to npc.z, "east, away from her, as far as its max range")
        assertNull(npc.target)
        assertTrue(alice.effects.pendingHits.isEmpty(), "it never hit her")
    }

    @Test
    fun `attacked from within its aggression range, it fights back`() {
        val npc = spawn()
        alice.x = 3220 - 8
        melee.hit(npc, alice, 1, tick, attackedFrom = Tile(alice.x, alice.z))
        assertSame(alice, npc.target)
        assertNull(npc.retreatingFrom)
    }

    @Test
    fun `where the player stood as they attacked counts, not where they are when it lands`() {
        val npc = spawn()
        alice.x = 3240
        melee.hit(npc, alice, 1, tick, attackedFrom = Tile(3221, 3220))
        assertNull(npc.retreatingFrom)
        assertSame(alice, npc.target)
    }

    @Test
    fun `a retreat ends once the player leaves its interaction range, or with a hit from within its aggression range`() {
        val npc = spawn()
        alice.x = 3220 - 10
        melee.hit(npc, alice, 1, tick, attackedFrom = Tile(alice.x, alice.z))
        process()
        assertSame(alice, npc.retreatingFrom)
        // Another shot from out there keeps it retreating; one from close by and it turns on her (guessed).
        melee.hit(npc, alice, 0, tick, attackedFrom = Tile(alice.x, alice.z))
        assertSame(alice, npc.retreatingFrom)
        melee.hit(npc, alice, 0, tick, attackedFrom = Tile(3219, 3220))
        assertNull(npc.retreatingFrom)
        assertSame(alice, npc.target)
        val other = spawn()
        melee.hit(other, alice, 0, tick, attackedFrom = Tile(3210, 3220))
        assertSame(alice, other.retreatingFrom)
        // The max range of 7 plus 11 is its interaction range.
        alice.x = 3220 - MeleeCombat.CHASE_RANGE - 1
        process()
        assertNull(other.retreatingFrom)
    }

    @Test
    fun `a unicorn retreats at a quarter of its hitpoints, rounded up, and keeps retreating however close the player is`() {
        val npc = spawn(unicorn)
        alice.x = 3221
        // 25% of 19 is 4.75: 5 hitpoints and below.
        melee.hit(npc, alice, 13, tick, attackedFrom = Tile(3221, 3220))
        assertEquals(6, npc.hitpoints)
        assertSame(alice, npc.target)
        melee.hit(npc, alice, 1, tick, attackedFrom = Tile(3221, 3220))
        assertSame(alice, npc.retreatingFrom)
        assertNull(npc.target)
        melee.hit(npc, alice, 1, tick, attackedFrom = Tile(3221, 3220))
        assertSame(alice, npc.retreatingFrom, "a hit from beside it doesn't end this retreat")
        process()
        assertTrue(npc.x < 3220 || npc.x > 3221 || npc.z != 3220, "it moved off: ${npc.x}, ${npc.z}")
    }

    @Test
    fun `a fighting unicorn poisoned down to a quarter of its hitpoints gives up the fight at once`() {
        val npc = spawn(unicorn)
        alice.x = 3221
        melee.hit(npc, alice, 0, tick, attackedFrom = Tile(3221, 3220))
        assertSame(alice, npc.target)
        melee.statusHit(npc, 14, Poison.POISON_HITSPLAT)
        // World.tick poisons after the combat phase, so the retreat has to start with the hit, before the movement.
        assertSame(alice, npc.retreatingFrom)
        assertNull(npc.target)
        process()
        assertSame(alice, npc.retreatingFrom)
        assertNull(npc.target)
    }

    @Test
    fun `a retreating aggressive NPC doesn't hunt`() {
        val npc = spawn(spider)
        alice.x = 3220 - 9
        melee.hit(npc, alice, 0, tick, attackedFrom = Tile(alice.x, alice.z))
        alice.x = 3222
        process()
        assertSame(alice, npc.retreatingFrom)
        assertNull(npc.target)
        assertTrue(abs(npc.x - 3220) <= Npcs.MAX_RANGE)
    }

    @Test
    fun `a retreat ends 8 ticks after the last hit, and an aggressive NPC hunts again`() {
        val npc = spawn(spider)
        alice.x = 3220 - 9
        melee.hit(npc, alice, 0, tick, attackedFrom = Tile(alice.x, alice.z))
        // Hit again from out there 5 ticks on, it keeps going past the first 8.
        repeat(5) { process() }
        melee.hit(npc, alice, 0, tick, attackedFrom = Tile(alice.x, alice.z))
        repeat(MeleeCombat.IN_COMBAT_TICKS) { process() }
        assertSame(alice, npc.retreatingFrom, "8 ticks after the second hit")
        // Bob comes up beside it while she stands out there; once she has stopped hitting it, it goes for him.
        bob.x = npc.x + 1
        bob.z = npc.z
        process()
        assertNull(npc.retreatingFrom)
        // It hunts from the next tick: hunting comes before fighting in a tick.
        process()
        assertSame(bob, npc.target)
    }

    @Test
    fun `an aggressive NPC lured past its range gives up after 8 ticks, walks home and hunts again`() {
        val npc = spawn(spider)
        alice.x = 3221
        process()
        assertSame(alice, npc.target)
        // She steps off 12 east of its spawn: past the 7 it follows to, within the 18 it gives up at.
        alice.x = 3220 + 12
        repeat(MeleeCombat.IN_COMBAT_TICKS) { process() }
        assertSame(alice, npc.target, "8 ticks after it went for her")
        assertEquals(3220 + Npcs.MAX_RANGE, npc.x, "it followed her to its max range")
        process()
        assertNull(npc.target)
        repeat(Npcs.MAX_RANGE) { process() }
        assertEquals(3220 to 3220, npc.x to npc.z, "home")
        assertTrue(alice.effects.pendingHits.isEmpty(), "it never reached her")
        bob.x = 3220
        bob.z = 3219
        process()
        assertSame(bob, npc.target)
    }

    @Test
    fun `a fighting NPC keeps on a player outside its range who is hitting it, and one it swung at lately`() {
        val npc = spawn(spider)
        alice.x = 3221
        process()
        alice.x = 3220 + 12
        repeat(MeleeCombat.IN_COMBAT_TICKS - 1) { process() }
        // A hit from within its aggression range (her last swing as she stepped off) holds it 8 more ticks.
        melee.hit(npc, alice, 0, tick, attackedFrom = Tile(3228, 3220))
        repeat(MeleeCombat.IN_COMBAT_TICKS) { process() }
        assertSame(alice, npc.target)
        process()
        assertNull(npc.target)
    }
}
