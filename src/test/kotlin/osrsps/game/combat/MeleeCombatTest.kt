package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import osrsps.game.Equipment
import osrsps.game.EquipmentTab
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
    private val rat = NpcType(2854, "Rat", ops = listOf(null, "Attack", null, null, null), combatLevel = 1, internalName = "rat", stats = NpcStats(hitpoints = 2))
    private val npcTypes = NpcTypes.of(man, hans, rat)
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
            "anims": { "attack": "punch", "defend": "block", "death": "death" }, "respawnTicks": 25, "source": "test" },
            { "npcs": ["rat"], "attackSpeed": 4, "maxHit": 1, "attackType": "crush", "attackBonus": 0, "strengthBonus": 0,
            "defence": { "stab": -21, "slash": -21, "crush": -21, "magic": -21, "light": -21, "standard": -21, "heavy": -21 },
            "anims": { "attack": "punch", "defend": "block", "death": "death" }, "respawnTicks": 2, "source": "test" } ] }""",
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
    private val deadPlayers = mutableListOf<Player>()
    private val looted = mutableListOf<Pair<Npc, Player?>>()
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
            players = { sequenceOf(alice, bob) },
            face = { player, npc -> faced += player to npc }, stopFacing = { player -> faced += player to null },
            onDeath = { deadPlayers += it },
            loot = { npc, killer -> looted += npc to killer },
            protects = { _, type -> protectFromMelee && type.melee },
        )
    }

    /** Stands in for Protect from Melee; see [Prayers.protects]. */
    private var protectFromMelee = false

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
    private fun expectedHits(count: Int, attackRoll: Int = 768, defenceRoll: Int = 430, maxHit: Int = 1): List<Int> =
        replayHits(listOf(Rolls(attackRoll, defenceRoll, maxHit)), count)

    private class Rolls(val attackRoll: Int, val defenceRoll: Int, val maxHit: Int)

    /** [count] hits as the engine draws them from a fresh seed, the swings taking turns through [rolls]. */
    private fun replayHits(rolls: List<Rolls>, count: Int): List<Int> {
        val replay = GameRandom(SEED)
        return (0 until count).map {
            val roll = rolls[it % rolls.size]
            if (CombatMath.rollHits(replay, roll.attackRoll, roll.defenceRoll)) CombatMath.rollDamage(replay, roll.maxHit) else 0
        }
    }

    @Test
    fun `a punch thrown on the tick of the click lands the tick after, and so every four ticks`() {
        val npc = spawn()
        attack(alice, npc)
        val hits = ArrayList<Int>()
        repeat(10) {
            tick()
            if (it == 0) assertIs<MeleeInteraction>(alice.interaction)
            val hit = npc.effects.pendingHits.singleOrNull()
            if (hit != null) hits += hit.value
            npc.effects.flush({}, {}, {})
        }
        // Thrown on ticks 1, 5 and 9, landing on 2, 6 and 10; the man's swings back on 3 and 7 take their draws in between.
        val roll = combat.npcAttack(npc, alice)!!
        val exchange = replayHits(listOf(Rolls(768, 430, 1), Rolls(roll.attackRoll, roll.defenceRoll, roll.maxHit)), 6)
        assertEquals(listOf(exchange[0], exchange[2], exchange[4]), hits)
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
        val dealt = expectedHits(1).single()
        // The XP comes with the swing; the hit lands in the next tick's NPC phase (OSRS Wiki, Hit delay).
        assertEquals(dealt * 40, alice.skills.tenths(Skill.ATTACK) - 0)
        assertEquals(dealt * 40 / 3, alice.skills.tenths(Skill.HITPOINTS) - NEW_HITPOINTS_TENTHS)
        assertTrue(npc.effects.pendingHits.isEmpty())
        assertEquals(1, melee.swingsLanding)
        assertEquals(dealt, npc.incoming)
        tick()
        assertEquals(0, melee.swingsLanding)
        assertEquals(0, npc.incoming)
        val hit = npc.effects.pendingHits.single()
        assertEquals(NpcEffects.PLAYER_SOURCE + alice.index, hit.source)
        assertEquals(dealt, hit.value)
        if (dealt > 0) assertEquals(HitsplatType.DAMAGE_ME to HitsplatType.DAMAGE_OTHER, hit.selfType to hit.otherType)
        else assertEquals(HitsplatType.BLOCK_ME to HitsplatType.BLOCK_OTHER, hit.selfType to hit.otherType)
        val bar = npc.effects.pendingHeadBars.single()
        assertEquals(MeleeCombat.HEALTH_BAR, bar.type)
        assertEquals(MeleeCombat.healthBarFill(7 - dealt, 7), bar.fill)
        assertEquals(424, npc.effects.anim?.id)
        assertEquals(alice, npc.attacker)
        assertEquals(alice, npc.facing)
        // Held still by the fight: eight ticks with no wandering, however the dice fall.
        repeat(7) { tick() }
        assertEquals(3213 to 3210, npc.x to npc.z)
    }

    @Test
    fun `a swing pays no XP for the hitpoints a shot on its way already has`() {
        val npc = spawn()
        npc.hitpoints = 1
        // As RangedCombat leaves it with an arrow for 1 in the air.
        npc.incoming = 1
        attack(alice, npc)
        tick()
        assertEquals(0, alice.skills.tenths(Skill.ATTACK))
        assertEquals(NEW_HITPOINTS_TENTHS, alice.skills.tenths(Skill.HITPOINTS))
        tick()
        assertEquals(HitsplatType.BLOCK_ME, npc.effects.pendingHits.single().selfType)
        assertEquals(1, npc.incoming)
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
        assertEquals(4L + 4, alice.nextAttackTick)
        tick()
        assertEquals(1, npc.effects.pendingHits.size)
    }

    @Test
    fun `an NPC someone else is fighting can't be attacked until they stop`() {
        // Bob doesn't hit back when the man turns on him, or his fight would never end.
        bob.vars[EquipmentTab.VARP_AUTO_RETALIATE_OFF] = 1
        val npc = spawn()
        attack(bob, npc)
        tick(bob)
        attack(alice, npc)
        tick(alice)
        assertNull(alice.interaction)
        assertEquals(listOf(alice to MeleeCombat.SOMEONE_ELSE), messages)
        // Bob stops; eight ticks after his last hit landed, on tick 2, Alice may.
        bob.interaction = null
        repeat(MeleeCombat.IN_COMBAT_TICKS - 1) { tick(bob) }
        attack(alice, npc)
        tick(alice)
        assertNull(alice.interaction)
        attack(alice, npc)
        tick(alice)
        assertIs<MeleeInteraction>(alice.interaction)
        // The man was still hitting Bob; now it turns on her.
        tick(alice)
        assertEquals(alice, npc.target)
    }

    @Test
    fun `the killing blow plays the death, the body goes after four ticks and the NPC is back after its respawn time`() {
        val npc = spawn()
        attack(alice, npc)
        repeat(2) { tick() }
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
        repeat(2) { tick() }
        assertTrue(looted.isEmpty())
        tick()
        assertEquals(listOf(npc), removed)
        assertNull(npcs[npc.index])
        // The drops go down for the killer as the body goes.
        assertEquals(listOf<Pair<Npc, Player?>>(npc to alice), looted)
        assertTrue(respawned.isEmpty())
        repeat(20) { tick() }
        assertTrue(respawned.isEmpty())
        tick()
        assertEquals(listOf(npc.spawn), respawned)
        assertEquals(0, melee.respawning)
    }

    @Test
    fun `an NPC put in with the npc command stays dead`() {
        val npc = npcs.add(NpcSpawn(man, 3213, 3210, source = "::npc by alice", respawns = false))!!
        tick = 10
        melee.hit(npc, alice, 100, tick)
        repeat(30) { tick() }
        assertEquals(listOf(npc), removed)
        assertTrue(respawned.isEmpty())
        assertEquals(0, melee.respawning)
    }

    @Test
    fun `a respawn shorter than the death waits for the body to go`() {
        val npc = spawn(rat)
        tick = 10
        // Killed by nobody, as ::hit with no player would: the drops are everyone's.
        melee.hit(npc, null, 100, tick)
        repeat(3) { tick() }
        assertTrue(removed.isEmpty())
        assertTrue(respawned.isEmpty())
        tick()
        assertEquals(listOf(npc), removed)
        assertEquals(listOf<Pair<Npc, Player?>>(npc to null), looted)
        assertEquals(listOf(npc.spawn), respawned)
        assertEquals(0, melee.respawning)
    }

    @Test
    fun `an NPC left alone forgets its attacker and stops facing them once the hold is over`() {
        val npc = spawn()
        attack(alice, npc)
        tick()
        // She goes far away, so the man gives up the chase.
        alice.interaction = null
        alice.x = 3240
        repeat(MeleeCombat.IN_COMBAT_TICKS) { tick() }
        assertNull(npc.target)
        assertNull(npc.facing)
        assertEquals(alice, npc.attacker)
        tick()
        assertNull(npc.attacker)
        // Free to be fought by anyone again.
        attack(bob, npc)
        tick(bob)
        assertIs<MeleeInteraction>(bob.interaction)
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
    fun `a hit NPC strikes back from the next tick and every four after, and its hits show on the player`() {
        val npc = spawn()
        attack(alice, npc)
        repeat(2) { tick() }
        assertEquals(alice, npc.target)
        assertEquals(alice, npc.following)
        assertEquals(3L, npc.nextAttackTick)
        assertTrue(alice.effects.pendingHits.isEmpty())
        alice.effects.flush({}, { _, _ -> }, {}, {})
        npc.effects.flush({}, {}, {})
        val roll = combat.npcAttack(npc, alice)!!
        val taken = ArrayList<Int>()
        for (t in 3..11) {
            tick()
            val hit = alice.effects.pendingHits.singleOrNull()
            if (hit != null) {
                taken += hit.value
                assertEquals(npc.index, hit.source)
                if (hit.value > 0) assertEquals(listOf(HitsplatType.DAMAGE_ME, HitsplatType.DAMAGE_OTHER, HitsplatType.DAMAGE_OTHER), listOf(hit.selfType, hit.sourceType, hit.otherType))
                else assertEquals(listOf(HitsplatType.BLOCK_ME, HitsplatType.BLOCK_OTHER, HitsplatType.BLOCK_OTHER), listOf(hit.selfType, hit.sourceType, hit.otherType))
                val bar = alice.effects.pendingHeadBars.single()
                assertEquals(npc.index to MeleeCombat.HEALTH_BAR, bar.source to bar.type)
                assertEquals(MeleeCombat.healthBarFill(alice.hitpoints, 10), bar.fill)
                // She blocks on the NPC's ticks, which are never her own swing's.
                assertEquals(424, alice.effects.anim?.id)
                assertEquals(422, npc.effects.anim?.id)
                assertEquals(npc, alice.attackedBy)
                assertEquals(tick, alice.attackedTick)
                assertTrue(t in listOf(3, 7, 11), "hit on tick $t")
            }
            alice.effects.flush({}, { _, _ -> }, {}, {})
            npc.effects.flush({}, {}, {})
        }
        // Her swings on 1, 5 and 9 and its on 3, 7 and 11 share the dice, each drawing accuracy then damage.
        val exchange = replayHits(listOf(Rolls(768, 430, 1), Rolls(roll.attackRoll, roll.defenceRoll, roll.maxHit)), 6)
        assertEquals(listOf(exchange[1], exchange[3], exchange[5]), taken)
        assertEquals(10 - taken.sum(), alice.hitpoints)
        assertEquals(15L, npc.nextAttackTick)
        assertIs<MeleeInteraction>(alice.interaction)
    }

    @Test
    fun `a protection prayer blocks the NPC's hits, and the dice roll on as they would have`() {
        protectFromMelee = true
        val npc = spawn()
        attack(alice, npc)
        val taken = ArrayList<Int>()
        for (t in 1..11) {
            tick()
            alice.effects.pendingHits.singleOrNull()?.let { taken += it.value }
            alice.effects.flush({}, { _, _ -> }, {}, {})
            npc.effects.flush({}, {}, {})
        }
        val roll = combat.npcAttack(npc, alice)!!
        val exchange = replayHits(listOf(Rolls(768, 430, 1), Rolls(roll.attackRoll, roll.defenceRoll, roll.maxHit)), 6)
        // Unprotected she would have taken some of these; her own punches land as they would have.
        assertTrue(listOf(exchange[1], exchange[3], exchange[5]).sum() > 0, "$exchange")
        assertEquals(listOf(0, 0, 0), taken)
        assertEquals(10, alice.hitpoints)
        assertEquals(7 - (exchange[0] + exchange[2] + exchange[4]), npc.hitpoints)
    }

    @Test
    fun `auto retaliate turns a hit player on the NPC with half their weapon's delay`() {
        val npc = spawn()
        attack(alice, npc)
        tick()
        // She clicks away after one punch; it lands on tick 2 and the man comes after her all the same, hitting on 3.
        alice.interaction = null
        alice.effects.flush({}, { _, _ -> }, {}, {})
        repeat(2) { tick() }
        assertEquals(1, alice.effects.pendingHits.size)
        val fight = assertIs<MeleeInteraction>(alice.interaction)
        assertEquals(npc, fight.npc)
        // Speed 4 halves to 2: her swing comes on tick 5, two after the hit.
        assertEquals(5L, alice.nextAttackTick)
        npc.effects.flush({}, {}, {})
        tick()
        assertEquals(0, melee.swingsLanding, "no swing on tick 4")
        tick()
        assertEquals(1, melee.swingsLanding)
        assertEquals(9L, alice.nextAttackTick)
        tick()
        assertEquals(1, npc.effects.pendingHits.size)
    }

    @Test
    fun `with auto retaliate off a hit player stands there`() {
        alice.vars[EquipmentTab.VARP_AUTO_RETALIATE_OFF] = 1
        val npc = spawn()
        attack(alice, npc)
        tick()
        alice.interaction = null
        alice.effects.flush({}, { _, _ -> }, {}, {})
        val taken = ArrayList<Int>()
        repeat(10) {
            tick()
            alice.effects.pendingHits.singleOrNull()?.let { taken += it.value }
            alice.effects.flush({}, { _, _ -> }, {}, {})
            assertNull(alice.interaction)
        }
        assertEquals(3, taken.size)
        assertEquals(10 - taken.sum(), alice.hitpoints)
        assertEquals(npc, alice.attackedBy)
        assertEquals(alice, npc.target)
        // Nor does a modal open over the hit, or a bow in hand, start a fight.
        alice.vars[EquipmentTab.VARP_AUTO_RETALIATE_OFF] = 0
        alice.chatModal = 1
        melee.hitPlayer(alice, npc, 0, tick)
        assertNull(alice.interaction)
        alice.chatModal = -1
        alice.equipment[WearPos.WEAPON] = InvObj(841, 1)
        melee.hitPlayer(alice, npc, 0, tick)
        assertNull(alice.interaction)
        alice.equipment[WearPos.WEAPON] = null
        melee.hitPlayer(alice, npc, 0, tick)
        assertIs<MeleeInteraction>(alice.interaction)
    }

    @Test
    fun `a player one NPC is hitting can't attack another until that fight is over`() {
        val first = spawn()
        val second = spawn(x = 3211)
        attack(alice, first)
        repeat(3) { tick() }
        assertEquals(first, alice.attackedBy)
        attack(alice, second)
        tick()
        assertNull(alice.interaction)
        assertEquals(listOf(alice to MeleeCombat.ALREADY_UNDER_ATTACK), messages)
        assertNull(second.target)
        // The first man dies; the second is fair game.
        melee.hit(first, alice, 100, tick)
        assertNull(first.target)
        attack(alice, second)
        repeat(2) { tick() }
        assertEquals(second, assertIs<MeleeInteraction>(alice.interaction).npc)
        assertEquals(alice, second.target)
    }

    @Test
    fun `an NPC that lands a hit before your first swing at another stops that attack`() {
        val first = spawn()
        val second = spawn(x = 3211)
        attack(alice, first)
        tick()
        // Her punch lands on tick 2 and the first man turns on her; he swings on tick 3, after her click on the second
        // but before her swing on 5.
        attack(alice, second)
        tick()
        assertEquals(second, assertIs<MeleeInteraction>(alice.interaction).npc)
        assertTrue(messages.isEmpty(), messages.toString())
        tick()
        assertEquals(first, alice.attackedBy)
        assertNull(alice.interaction)
        assertEquals(listOf(alice to MeleeCombat.ALREADY_UNDER_ATTACK), messages)
        repeat(2) { tick() }
        assertTrue(second.effects.pendingHits.isEmpty())
        assertNull(second.target)
        // Once she is fighting the second man, a newcomer's hit doesn't stop her.
        melee.hit(first, alice, 100, tick)
        attack(alice, second)
        repeat(2) { tick() }
        assertEquals(1, second.effects.pendingHits.size)
        val third = spawn(x = 3209)
        melee.hit(third, alice, 0, tick)
        melee.hitPlayer(alice, third, 1, tick)
        // She keeps punching the second man: the swing on 10 lands on 11.
        repeat(5) { tick() }
        assertEquals(second, assertIs<MeleeInteraction>(alice.interaction).npc)
        assertEquals(2, second.effects.pendingHits.size)
    }

    @Test
    fun `an NPC chases its attacker no further than seven tiles from its spawn and gives up past eighteen`() {
        val npc = spawn()
        attack(alice, npc)
        tick()
        alice.interaction = null
        alice.x = 3225
        alice.effects.flush({}, { _, _ -> }, {}, {})
        repeat(7) { tick() }
        assertEquals(3220 to 3210, npc.x to npc.z)
        assertEquals(alice, npc.target)
        repeat(3) { tick() }
        assertEquals(3220 to 3210, npc.x to npc.z)
        assertTrue(alice.effects.pendingHits.isEmpty())
        // Back in reach, it hits her; beyond its chase range, it lets her go.
        alice.x = 3221
        tick()
        assertEquals(1, alice.effects.pendingHits.size)
        alice.x = 3232
        tick()
        assertNull(npc.target)
        assertNull(npc.following)
        assertEquals(3220 to 3210, npc.x to npc.z)
    }

    @Test
    fun `an NPC gives up on an attacker who logs out or leaves the level`() {
        val npc = spawn()
        attack(alice, npc)
        tick()
        alice.level = 1
        tick()
        assertNull(npc.target)
        alice.level = 0
        attack(alice, npc)
        repeat(4) { tick() }
        assertEquals(alice, npc.target)
        alice.loggedOut = true
        tick()
        assertNull(npc.target)
    }

    @Test
    fun `hitpoints come back one a minute for the hurt`() {
        val npc = spawn()
        val other = spawn(hans, x = 3211)
        alice.hitpoints = 5
        bob.hitpoints = 10
        npc.hitpoints = 3
        tick = MeleeCombat.REGEN_TICKS - 2L
        tick()
        assertEquals(5, alice.hitpoints)
        tick()
        assertEquals(6, alice.hitpoints)
        assertEquals(10, bob.hitpoints)
        assertEquals(4, npc.hitpoints)
        assertEquals(other.type.stats.hitpoints, other.hitpoints)
        tick = 2L * MeleeCombat.REGEN_TICKS - 1
        tick()
        assertEquals(7, alice.hitpoints)
        assertEquals(5, npc.hitpoints)
    }

    @Test
    fun `god takes no damage and still shows the block`() {
        val npc = spawn()
        alice.god = true
        assertEquals(0, melee.hitPlayer(alice, npc, 5, 1))
        assertEquals(10, alice.hitpoints)
        val hit = alice.effects.pendingHits.single()
        assertEquals(HitsplatType.BLOCK_ME to 0, hit.selfType to hit.value)
        assertEquals(MeleeCombat.HEALTH_BAR_WIDTH, alice.effects.pendingHeadBars.single().fill)
        assertEquals(npc, alice.attackedBy)
    }

    @Test
    fun `a player out of hitpoints is told, handed to the deaths, and let go of`() {
        val npc = spawn()
        attack(alice, npc)
        tick()
        alice.hitpoints = 1
        assertEquals(1, melee.hitPlayer(alice, npc, 5, tick))
        assertEquals(listOf(alice to MeleeCombat.DEAD), messages)
        assertEquals(listOf(alice), deadPlayers)
        assertEquals(0, alice.hitpoints)
        // Lying dead, nothing more lands on them.
        assertEquals(0, melee.hitPlayer(alice, npc, 5, tick))
        assertEquals(listOf(alice), deadPlayers)
        assertNull(alice.interaction)
        assertNull(alice.attackedBy)
        assertNull(npc.target)
        assertNull(npc.following)
        assertEquals(alice to null, faced.last())
        assertEquals(0, alice.effects.pendingHeadBars.single().fill)
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
