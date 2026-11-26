package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import osrsps.game.Equipment
import osrsps.game.GameRandom
import osrsps.game.Movement
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.SeqNames
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.Npcs
import osrsps.map.CollisionMap

/**
 * A phoenix necklace's heal while NPCs' shots fly (Codex review on #956): the other shots coming for its wearer land
 * nowhere, marked rather than taken out of the list being walked, and another player's shot landing the same tick still
 * lands; and a ring of life's escape doesn't start a fight back.
 */
class PhoenixShotsTest {
    private var tick = 0L
    private val wizard = NpcType(512, "Dark wizard", ops = listOf(null, "Attack", null, null, null), combatLevel = 7, internalName = "young_dark_wizard", stats = NpcStats(hitpoints = 12, magic = 6))
    private val npcCombat = NpcCombat.parse(
        """{ "npcs": [ { "npcs": ["young_dark_wizard"], "attackSpeed": 4, "maxHit": 20, "attackType": "magic", "attackBonus": 5000, "strengthBonus": 0,
            "range": 10, "attacks": [ { "cast": 93, "projectile": 94, "impact": 95 } ],
            "defence": { "stab": 0, "slash": 0, "crush": 0, "magic": 3, "light": 0, "standard": 0, "heavy": 0 },
            "anims": { "attack": "cast", "defend": "block", "death": "death" }, "respawnTicks": 50, "source": "test" } ] }""",
        NpcTypes.of(wizard), SeqNames(mapOf("cast" to 711, "block" to 424, "death" to 836)),
    )
    private val map = CollisionMap().apply { markLoaded(50, 50) }
    private val npcs = Npcs(map, GameRandom(1))
    // Hits of 0 save no one (round 59, R59A-3), so the wizards hit hard and the players can take it.
    private val alice = Player(1, "alice", 3212, 3210, 0).apply { hitpoints = 250 }
    private val bob = Player(2, "bob", 3212, 3214, 0).apply { hitpoints = 250 }
    private val rescued = mutableListOf<Player>()
    private var aliceRescue = MeleeCombat.Rescue.HEALED
    private val melee = MeleeCombat(
        npcs, map, Movement(map), Combat(Equipment(ObjTypes.of()), AttackStyles.DEFAULT, npcCombat), GameRandom(5), { tick },
        removeNpc = { npcs.remove(it) }, spawnNpc = {},
        players = { sequenceOf(alice, bob) },
        hurtAlive = { player ->
            rescued += player
            if (player === alice) aliceRescue else MeleeCombat.Rescue.NONE
        },
    )

    private fun spawn(x: Int, z: Int): Npc = npcs.add(NpcSpawn(wizard, x, z, source = "test"))!!

    @Test
    fun `after the heal the second shot at her lands nowhere, and his still lands`() {
        val first = spawn(3217, 3210)
        val second = spawn(3217, 3211)
        val third = spawn(3217, 3214)
        melee.provoke(first, alice, tick)
        melee.provoke(second, alice, tick)
        melee.provoke(third, bob, tick)
        repeat(4) {
            tick++
            melee.process()
            npcs.process()
        }
        assertEquals(listOf(alice, bob), rescued, "one of her two shots landed, then the other was dropped; his landed")
        assertEquals(0, melee.shotsInFlight)
    }

    @Test
    fun `after a ring of life's escape the other shot at her lands nowhere either (round 59, R59A-2)`() {
        aliceRescue = MeleeCombat.Rescue.ESCAPED
        val first = spawn(3217, 3210)
        val second = spawn(3217, 3211)
        melee.provoke(first, alice, tick)
        melee.provoke(second, alice, tick)
        repeat(4) {
            tick++
            melee.process()
            npcs.process()
        }
        assertEquals(listOf(alice), rescued, "the second shot was dropped")
        assertEquals(0, melee.shotsInFlight)
    }

    @Test
    fun `a ring of life's escape doesn't hit back`() {
        aliceRescue = MeleeCombat.Rescue.ESCAPED
        val wizard = spawn(3217, 3210)
        melee.hitPlayer(alice, wizard, 1, tick)
        assertEquals(listOf(alice), rescued)
        assertNull(alice.interaction, "no fight started with the wizard")
    }
}
