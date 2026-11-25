package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
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
import osrsps.game.dialogue.DialogueText
import osrsps.game.npc.NpcSpawn
import osrsps.game.npc.Npcs
import osrsps.map.CollisionMap

/**
 * Karamja step K3.3b: a metallic dragon's dragonfire (the Brimhaven Dungeon's bronze, iron and steel dragons) ignores
 * Protect from Magic (the Wiki's Dragonfire page, read 2026-11-25), while a chromatic dragon's is held to 10 by it.
 * Each dragon here breathes every turn in reach.
 */
class MetallicDragonfireTest {
    private var tick = 0L
    private val dragon = NpcType(274, "Dragon", ops = listOf(null, "Attack", null, null, null), internalName = "dragon", stats = NpcStats(hitpoints = 50, magic = 1))

    private fun combatFile(metallic: Boolean) = NpcCombat.parse(
        """{ "npcs": [ { "npcs": ["dragon"], "attackSpeed": 4, "maxHit": 1, "attackType": "slash", "attackBonus": 0, "strengthBonus": 0,
            "defence": { "stab": 0, "slash": 0, "crush": 0, "magic": 0, "light": 0, "standard": 0, "heavy": 0 },
            "anims": { "attack": null, "defend": null, "death": null }, "respawnTicks": 15, "source": "test",
            "dragonfire": { "chance": 1, "metallic": $metallic } } ] }""",
        NpcTypes.of(dragon), SeqNames.NONE,
    )

    /** Every message a breath at alice, praying Protect from Magic, gave her. */
    private fun breathPraying(metallic: Boolean): List<String> {
        val map = CollisionMap().apply { markLoaded(50, 50) }
        val npcs = Npcs(map, GameRandom(1))
        val alice = Player(1, "alice", 3212, 3210, 0).apply { skills.setLevel(Skill.HITPOINTS, 99) }
        val messages = mutableListOf<String>()
        val melee = MeleeCombat(
            npcs, map, Movement(map), Combat(Equipment(ObjTypes.of()), AttackStyles.DEFAULT, combatFile(metallic)), GameRandom(5), { tick },
            removeNpc = { npcs.remove(it) }, spawnNpc = {},
            players = { sequenceOf(alice) },
            message = { _, text -> messages += text },
            protects = { _, type -> type == AttackType.MAGIC },
        )
        val npc = npcs.add(NpcSpawn(dragon, 3213, 3210, source = "test"))!!
        melee.provoke(npc, alice, tick)
        for (t in 1..4) {
            tick++
            melee.process()
            npcs.process()
            alice.effects.flush({}, { _, _ -> }, {}, {})
            npc.effects.flush({}, {}, {})
        }
        return messages
    }

    private fun fireLines(messages: List<String>) = messages.filter { it.startsWith("[dragonfire.") }

    @Test
    fun `the metal dragons' combat entries breathe metallic dragonfire, the red dragons' chromatic`() {
        val names = listOf("red_dragon", "bronze_dragon", "iron_dragon", "steel_dragon")
        val types = NpcTypes.of(*names.mapIndexed { i, name -> NpcType(247 + i, name, internalName = name) }.toTypedArray())
        val combat = NpcCombat.load(types, SeqNames.NONE)
        assertEquals(mapOf("red_dragon" to false, "bronze_dragon" to true, "iron_dragon" to true, "steel_dragon" to true), names.associateWith { combat[types.find(it)!!]!!.dragonfire!!.metallic })
    }

    @Test
    fun `Protect from Magic does nothing against a metal dragon's breath`() {
        val lines = fireLines(breathPraying(metallic = true))
        assertTrue(lines.isNotEmpty(), "a breath")
        assertTrue(lines.none { it == DialogueText.NONE[DragonfireLines.PRAYER] }, "$lines")
        assertTrue(lines.all { it == DialogueText.NONE[DragonfireLines.BURNT] || it == DialogueText.NONE[DragonfireLines.RESISTED] }, "$lines")
    }

    @Test
    fun `Protect from Magic still holds a chromatic dragon's breath`() {
        val lines = fireLines(breathPraying(metallic = false))
        assertTrue(lines.isNotEmpty(), "a breath")
        assertTrue(lines.all { it == DialogueText.NONE[DragonfireLines.PRAYER] }, "$lines")
    }
}
