package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.Equipment
import osrsps.game.GameRandom
import osrsps.game.Interactions
import osrsps.game.InvObj
import osrsps.game.Movement
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.SeqNames
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcEffects
import osrsps.game.npc.NpcInteractions
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.Npcs
import osrsps.game.script.Handlers
import osrsps.game.script.ScriptRunner
import osrsps.map.CollisionMap

/** Fights run the way World runs them: the NPC phase, scripts, then each player's interaction around their step. */
class MeleeCombatTest {
    private var tick = 0L
    private val man = NpcType(3106, "Man", ops = listOf("Talk-to", "Attack", "Pickpocket", null, null), combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))
    private val hans = NpcType(3105, "Hans", ops = listOf("Talk-to", null, "Attack", null, null), internalName = "hans")
    private val npcTypes = NpcTypes.of(man, hans)
    private val shortbow = ObjType(841, "Shortbow", wearpos1 = WearPos.WEAPON, wearpos2 = WearPos.SHIELD, params = mapOf(14 to 5), internalName = "shortbow", category = 64)
    private val styles = AttackStyles.fromRows(
        listOf(
            listOf<Any>(0) to listOf<Any>(
                0, "Punch", "(Accurate)<br>(Crush)<br>(Attack XP)", 0,
                1, "Kick", "(Aggressive)<br>(Crush)<br>(Strength XP)", 0,
                3, "Block", "(Defensive)<br>(Crush)<br>(Defence XP)", 0,
            ),
            listOf<Any>(3) to listOf<Any>(0, "Accurate", "(Accurate)<br>(Standard)<br>(Ranged XP)", 0),
        ),
    )
    private val npcCombat = NpcCombat.parse(
        """{ "npcs": [ { "npcs": ["man"], "attackSpeed": 4, "maxHit": 1, "attackType": "crush", "attackBonus": 0,
            "strengthBonus": 0,
            "defence": { "stab": -21, "slash": -21, "crush": -21, "magic": -21, "light": -21, "standard": -21, "heavy": -21 },
            "anims": { "attack": "punch", "defend": "block", "death": "death" }, "respawnTicks": 25, "source": "test" } ] }""",
        npcTypes, SeqNames(mapOf("punch" to 422, "block" to 424, "death" to 836)),
    )
    private val map = CollisionMap().apply { markLoaded(50, 50) }
    private val random = GameRandom(SEED)
    private val npcs = Npcs(map, GameRandom(1))
    private val handlers = Handlers()
    private val scripts = ScriptRunner(handlers, { it.modals.clear() }) { tick }
    private val movement = Movement(map)
    private val messages = mutableListOf<Pair<Player, String>>()
    private val faced = mutableListOf<Pair<Player, Npc?>>()
    private val removed = mutableListOf<Npc>()
    private val respawned = mutableListOf<NpcSpawn>()
    private val combat = Combat(Equipment(ObjTypes.of(shortbow)), styles, npcCombat)
    private lateinit var melee: MeleeCombat
    private val interactions = NpcInteractions(
        npcs, npcTypes, map, movement, handlers, scripts, { tick },
        message = { player, text -> messages += player to text },
        faceNpc = { player, npc -> faced += player to npc },
        clearFlag = {},
        attack = { player, npc -> melee.attack(player, npc) },
    )
    private val shared = Interactions { player, text -> messages += player to text }
    private val alice = Player(1, "alice", 3212, 3210, 0)
    private val bob = Player(2, "bob", 3214, 3210, 0)

    init {
        melee = MeleeCombat(
            npcs, map, movement, combat, random, { tick }, removeNpc = { npcs.remove(it); removed += it }, spawnNpc = { respawned += it },
            message = { player, text -> messages += player to text },
            face = { player, npc -> faced += player to npc }, stopFacing = { player -> faced += player to null },
        )
    }

    private fun spawn(type: NpcType = man, x: Int = 3213, z: Int = 3210, wander: Int = 0) =
        npcs.add(NpcSpawn(type, x, z, wander = wander, source = "test"))!!

    private fun tick(vararg players: Player = arrayOf(alice)) {
        tick++
        interactions.releaseNpcs()
        melee.process()
        npcs.process()
        for (player in players) {
            scripts.process(player)
            shared.process(player) { movement.process(player) }
        }
    }

    private fun attack(player: Player, npc: Npc) = interactions.click(player, npc.index, 2, -1, invertRun = false)

    /** The hits a fresh [GameRandom] of the same seed gives, as the engine draws them: accuracy, then damage. */
    private fun expectedHits(count: Int, attackRoll: Int = 768, defenceRoll: Int = 430, maxHit: Int = 1): List<Int> {
        val replay = GameRandom(SEED)
        return (1..count).map { if (CombatMath.rollHits(replay, attackRoll, defenceRoll)) CombatMath.rollDamage(replay, maxHit) else 0 }
    }

    @Test
    fun `a punch lands on the tick of the click and every four ticks after`() {
        val npc = spawn()
        attack(alice, npc)
        val hits = ArrayList<Int>()
        repeat(9) {
            tick()
            if (it == 0) assertIs<MeleeInteraction>(alice.interaction)
            val hit = npc.effects.pendingHits.singleOrNull()
            if (hit != null) hits += hit.value
            npc.effects.flush({}, {}, {})
        }
        // Ticks 1, 5 and 9.
        assertEquals(expectedHits(3), hits)
        assertEquals(7 - hits.sum(), npc.hitpoints)
        assertEquals(13L, alice.nextAttackTick)
        assertIs<MeleeInteraction>(alice.interaction)
        assertTrue(messages.isEmpty(), messages.toString())
    }

    @Test
    fun `a swing animates the player, shows the splat and health bar, holds the NPC and gives XP`() {
        val npc = spawn(wander = 5)
        attack(alice, npc)
        tick()
        assertEquals(422, alice.effects.anim?.id)
        val hit = npc.effects.pendingHits.single()
        assertEquals(NpcEffects.PLAYER_SOURCE + alice.index, hit.source)
        val dealt = expectedHits(1).single()
        assertEquals(dealt, hit.value)
        if (dealt > 0) assertEquals(HitsplatType.DAMAGE_ME to HitsplatType.DAMAGE_OTHER, hit.selfType to hit.otherType)
        else assertEquals(HitsplatType.BLOCK_ME to HitsplatType.BLOCK_OTHER, hit.selfType to hit.otherType)
        val bar = npc.effects.pendingHeadBars.single()
        assertEquals(MeleeCombat.HEALTH_BAR, bar.type)
        assertEquals(MeleeCombat.healthBarFill(7 - dealt, 7), bar.fill)
        assertEquals(424, npc.effects.anim?.id)
        assertEquals(alice, npc.attacker)
        assertEquals(alice, npc.facing)
        assertEquals(dealt * 40, alice.skills.tenths(Skill.ATTACK) - 0)
        assertEquals(dealt * 40 / 3, alice.skills.tenths(Skill.HITPOINTS) - NEW_HITPOINTS_TENTHS)
        // Held still by the fight: eight ticks with no wandering, however the dice fall.
        repeat(7) { tick() }
        assertEquals(3213 to 3210, npc.x to npc.z)
    }

    @Test
    fun `the player walks up to a far NPC and swings on arrival`() {
        val npc = spawn(x = 3217)
        attack(alice, npc)
        repeat(3) { tick() }
        assertEquals(3215 to 3210, alice.x to alice.z)
        assertTrue(npc.effects.pendingHits.isEmpty())
        tick()
        assertEquals(3216 to 3210, alice.x to alice.z)
        assertEquals(1, npc.effects.pendingHits.size)
        assertEquals(4L + 4, alice.nextAttackTick)
    }

    @Test
    fun `an NPC someone else is fighting can't be attacked until they stop`() {
        val npc = spawn()
        attack(bob, npc)
        tick(bob)
        attack(alice, npc)
        tick(alice)
        assertNull(alice.interaction)
        assertEquals(listOf(alice to MeleeCombat.SOMEONE_ELSE), messages)
        // Bob stops; eight ticks after his last hit, Alice may.
        bob.interaction = null
        repeat(MeleeCombat.IN_COMBAT_TICKS - 2) { tick(bob) }
        attack(alice, npc)
        tick(alice)
        assertNull(alice.interaction)
        attack(alice, npc)
        tick(alice)
        assertIs<MeleeInteraction>(alice.interaction)
    }

    @Test
    fun `the killing blow plays the death, the body goes after four ticks and the NPC is back after its respawn time`() {
        val npc = spawn()
        attack(alice, npc)
        tick()
        tick = 10
        assertEquals(7 - expectedHits(1).single(), npc.hitpoints)
        assertEquals(npc.hitpoints, melee.hit(npc, alice, 100, tick))
        assertEquals(0, npc.hitpoints)
        assertTrue(npc.dead)
        assertEquals(836, npc.effects.anim?.id)
        assertEquals(0, npc.effects.pendingHeadBars.last().fill)
        assertEquals(0, melee.hit(npc, alice, 5, tick))
        tick()
        // The fight is over for Alice; her next click on it finds nothing.
        assertNull(alice.interaction)
        assertEquals(1, melee.respawning)
        repeat(3) { tick() }
        assertEquals(listOf(npc), removed)
        assertNull(npcs[npc.index])
        assertTrue(respawned.isEmpty())
        repeat(20) { tick() }
        assertTrue(respawned.isEmpty())
        tick()
        assertEquals(listOf(npc.spawn), respawned)
        assertEquals(0, melee.respawning)
    }

    @Test
    fun `a bow says only melee is in, and Hans can't be fought at all`() {
        val npc = spawn()
        alice.equipment[WearPos.WEAPON] = InvObj(841, 1)
        attack(alice, npc)
        tick()
        assertNull(alice.interaction)
        assertEquals(listOf(alice to MeleeCombat.MELEE_ONLY), messages)
        messages.clear()
        alice.equipment[WearPos.WEAPON] = null
        val other = spawn(hans, x = 3211)
        interactions.click(alice, other.index, 3, -1, invertRun = false)
        tick()
        assertEquals(listOf(alice to NpcInteractions.NOTHING_INTERESTING), messages)
        assertFalse(melee.attack(alice, other))
    }

    @Test
    fun `health bar pixels`() {
        assertEquals(30, MeleeCombat.healthBarFill(7, 7))
        assertEquals(25, MeleeCombat.healthBarFill(6, 7))
        assertEquals(1, MeleeCombat.healthBarFill(1, 100))
        assertEquals(0, MeleeCombat.healthBarFill(0, 7))
    }

    companion object {
        private const val SEED = 5L

        /** A new account's 1,154 Hitpoints XP in tenths. */
        private const val NEW_HITPOINTS_TENTHS = 11_540
    }
}
