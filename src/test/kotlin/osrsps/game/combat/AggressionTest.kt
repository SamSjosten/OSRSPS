package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
import osrsps.game.Skill
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.Npcs
import osrsps.map.CollisionMap
import osrsps.map.Tile

/** Aggressive NPCs: who they go for, from where, and the ten minutes after which tolerant ones stop. */
class AggressionTest {
    private var tick = 0L
    private val spider = NpcType(3017, "Giant spider", combatLevel = 2, internalName = "giantspider1", stats = NpcStats(hitpoints = 5))
    private val boss = NpcType(16659, "Archive Guardian", size = 3, combatLevel = 400, internalName = "archive_guardian", stats = NpcStats(hitpoints = 1000))
    private val ogre = NpcType(2095, "Ogre", combatLevel = 53, internalName = "ogre", stats = NpcStats(hitpoints = 60))
    private val man = NpcType(3106, "Man", combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))
    private val types = NpcTypes.of(spider, boss, ogre, man)
    private val npcCombat = NpcCombat.parse(
        """{ "npcs": [ ${entry("giantspider1", """"aggressive": true,""")}, ${entry("ogre", """"aggressive": { "huntRange": 3, "ignoresLevels": true },""")},
            ${entry("archive_guardian", """"aggressive": { "ignoresLevels": true, "tolerant": false },""")}, ${entry("man")} ] }""",
        types, SeqNames.NONE,
    )
    private val map = CollisionMap().apply { markLoaded(50, 50) }
    private val npcs = Npcs(map, GameRandom(1))
    private val multi = MultiCombat()
    private val players = ArrayList<Player>()
    private val alice = Player(1, "alice", 3220, 3220, 0)
    private val bob = Player(2, "bob", 3220, 3220, 0)
    private val melee = MeleeCombat(
        npcs, map, Movement(map), Combat(Equipment(ObjTypes.of()), AttackStyles.fromRows(emptyList()), npcCombat), GameRandom(5), { tick },
        removeNpc = { npcs.remove(it) }, spawnNpc = {}, players = { players.asSequence() }, multi = multi,
    )
    private val aggression = Aggression(npcs, { npcCombat[it.type] }, melee, GameRandom(7), { tick }) { players.asSequence() }

    private fun entry(name: String, extra: String = "") = """{ "npcs": ["$name"], "attackSpeed": 4, "maxHit": 1, "attackType": "crush",
        "attackBonus": 0, "strengthBonus": 0, $extra
        "defence": { "stab": 0, "slash": 0, "crush": 0, "magic": 0, "light": 0, "standard": 0, "heavy": 0 },
        "anims": { "attack": null, "defend": null, "death": null }, "respawnTicks": 25, "source": "test" }"""

    private fun spawn(type: NpcType, x: Int = 3220, z: Int = 3220) = npcs.add(NpcSpawn(type, x, z, source = "test"))!!

    private fun at(player: Player, x: Int, z: Int): Player {
        player.x = x
        player.z = z
        if (player !in players) players += player
        return player
    }

    private fun process() {
        tick++
        aggression.process()
    }

    @Test
    fun `the combat file reads aggressive as true or with its hunt range, levels and tolerance`() {
        assertEquals(NpcAggression(), npcCombat[spider]!!.aggression)
        assertEquals(NpcAggression(huntRange = 3, ignoresLevels = true), npcCombat[ogre]!!.aggression)
        assertEquals(NpcAggression(ignoresLevels = true, tolerant = false), npcCombat[boss]!!.aggression)
        assertNull(npcCombat[man]!!.aggression)
        val bad = assertFailsWith<IllegalArgumentException> {
            NpcCombat.parse("""{ "npcs": [ ${entry("man", """"aggressive": { "range": 3 },""")} ] }""", types, SeqNames.NONE)
        }
        assertTrue("not range" in bad.message!!, bad.message)
    }

    @Test
    fun `it goes for a player within its hunt range, and not one a tile further`() {
        val npc = spawn(spider)
        at(alice, 3220 + NpcAggression.DEFAULT_HUNT_RANGE + 1, 3220)
        process()
        assertNull(npc.target)
        at(alice, 3220 + NpcAggression.DEFAULT_HUNT_RANGE, 3220 + 2)
        process()
        assertSame(alice, npc.target)
        assertSame(alice, npc.following, "it chases them")
    }

    @Test
    fun `a man that isn't aggressive leaves a player beside him alone`() {
        val npc = spawn(man)
        at(alice, 3221, 3220)
        repeat(3) { process() }
        assertNull(npc.target)
    }

    @Test
    fun `a big NPC hunts from its south-west tile, so a player off its north-east side can be out of range`() {
        val npc = spawn(boss)
        // Two tiles off its east side, but six from its south-west tile.
        at(alice, 3220 + 3 + 3, 3222)
        process()
        assertNull(npc.target)
        // As far off its west side, five from that tile.
        at(alice, 3220 - 5, 3222)
        process()
        assertSame(alice, npc.target)
    }

    @Test
    fun `it doesn't go for a player beyond its aggression range of its spawn, wherever it has wandered`() {
        val npc = spawn(spider)
        npc.x = 3226
        // Three tiles from the spider, nine from its spawn: max range 7 plus melee's 1 is 8.
        at(alice, 3229, 3220)
        process()
        assertNull(npc.target)
        at(alice, 3228, 3220)
        process()
        assertSame(alice, npc.target)
    }

    @Test
    fun `it leaves a player of more than twice its level alone, unless it ignores levels or is level 63 or more`() {
        val spiderNpc = spawn(spider)
        val ogreNpc = spawn(ogre, 3230, 3230)
        // Level 3 to begin with: twice the spider's 2 is 4.
        for (skill in listOf(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE)) alice.skills.setLevel(skill, 10)
        assertTrue(alice.skills.combatLevel > 4, "level ${alice.skills.combatLevel}")
        at(alice, 3221, 3220)
        process()
        assertNull(spiderNpc.target)
        at(alice, 3231, 3230)
        process()
        assertSame(alice, ogreNpc.target, "the ogre's file says it ignores levels")
        val strong = spawn(NpcType(9999, "Strong", combatLevel = Aggression.ALWAYS_LEVEL), 3240, 3240)
        val aggressive = NpcAggression()
        for (skill in Skill.entries) alice.skills.setLevel(skill, 99)
        at(alice, 3241, 3240)
        assertTrue(aggression.huntable(strong, aggressive, alice), "level ${alice.skills.combatLevel} against 63")
    }

    @Test
    fun `it picks the nearer of two players`() {
        val npc = spawn(spider)
        at(alice, 3224, 3220)
        at(bob, 3222, 3221)
        process()
        assertSame(bob, npc.target)
    }

    @Test
    fun `in single-way combat it leaves a player another NPC is fighting alone, and in multi-way it joins in`() {
        val npc = spawn(spider)
        val other = spawn(spider, 3230, 3230)
        at(alice, 3222, 3222)
        melee.aggress(other, alice, tick)
        alice.attackedBy = other
        alice.attackedTick = tick
        process()
        assertNull(npc.target)
        multi.set(0, 3222, 3222, true)
        process()
        assertSame(alice, npc.target)
    }

    @Test
    fun `after ten minutes in the area tolerant monsters stop, and a boss doesn't`() {
        val spiderNpc = spawn(spider)
        val bossNpc = spawn(boss, 3216, 3216)
        at(alice, 3221, 3221)
        repeat(Aggression.TOLERANCE_TICKS) { aggression.count(alice) }
        assertFalse(alice.tolerance.tolerant, "the first tick starts the count")
        aggression.count(alice)
        assertTrue(alice.tolerance.tolerant)
        assertEquals(Tile(3221, 3221), alice.tolerance.newer)
        val aggressive = NpcAggression()
        assertFalse(aggression.huntable(spiderNpc, aggressive, alice))
        assertTrue(aggression.huntable(bossNpc, NpcAggression(ignoresLevels = true, tolerant = false), alice))
    }

    @Test
    fun `leaving both areas starts the count again, coming back to the older keeps it, and a teleport forgets both`() {
        at(alice, 3220, 3220)
        aggression.count(alice)
        repeat(50) { aggression.count(alice) }
        assertEquals(50, alice.tolerance.ticks)
        // Ten tiles is still in the area; eleven isn't.
        at(alice, 3230, 3220)
        aggression.count(alice)
        assertEquals(51, alice.tolerance.ticks)
        at(alice, 3231, 3220)
        aggression.count(alice)
        assertEquals(0, alice.tolerance.ticks)
        assertEquals(Tile(3220, 3220), alice.tolerance.older)
        assertEquals(Tile(3231, 3220), alice.tolerance.newer)
        // Back in the first area only, which is still the older: the count goes on.
        at(alice, 3218, 3220)
        aggression.count(alice)
        assertEquals(1, alice.tolerance.ticks)
        at(alice, 3218 + Aggression.TELEPORT_TILES + 1, 3220)
        aggression.count(alice)
        assertNull(alice.tolerance.older)
        assertEquals(Tile(3218 + Aggression.TELEPORT_TILES + 1, 3220), alice.tolerance.newer)
        assertEquals(0, alice.tolerance.ticks)
    }

    @Test
    fun `an NPC already fighting someone, or a dead one, doesn't hunt`() {
        val npc = spawn(spider)
        at(alice, 3221, 3220)
        at(bob, 3222, 3220)
        melee.aggress(npc, bob, tick)
        process()
        assertSame(bob, npc.target)
        val dead: Npc = spawn(spider, 3240, 3240)
        dead.dead = true
        at(alice, 3241, 3240)
        process()
        assertNull(dead.target)
    }
}
