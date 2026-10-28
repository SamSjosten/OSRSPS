package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import osrsps.game.EquipmentTab
import osrsps.game.InvObj
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.SeqNames
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

class CombatTest {
    private val man = NpcType(3106, "Man", combatLevel = 2, internalName = "man", stats = NpcStats(hitpoints = 7))
    private val hans = NpcType(3105, "Hans", internalName = "hans")
    private val npcTypes = NpcTypes.of(man, hans)
    private val shortbow = ObjType(841, "Shortbow", wearpos1 = WearPos.WEAPON, wearpos2 = WearPos.SHIELD, params = mapOf(14 to 5), internalName = "shortbow", category = 64)
    private val sword = ObjType(1277, "Bronze sword", wearpos1 = WearPos.WEAPON, params = mapOf(0 to 4, 1 to 3, 10 to 5, 14 to 4), internalName = "bronze_sword", category = 25)
    private val styles = AttackStyles.fromRows(
        listOf(
            listOf<Any>(0) to listOf<Any>(
                0, "Punch", "(Accurate)<br>(Crush)<br>(Attack XP)", 0,
                1, "Kick", "(Aggressive)<br>(Crush)<br>(Strength XP)", 0,
                3, "Block", "(Defensive)<br>(Crush)<br>(Defence XP)", 0,
            ),
            listOf<Any>(3) to listOf<Any>(
                0, "Accurate", "(Accurate)<br>(Standard)<br>(Ranged XP)", 0,
                1, "Rapid", "(Rapid)<br>(Standard)<br>(Ranged XP)", 0,
                3, "Longrange", "(Longrange)<br>(Standard)<br>(Ranged XP)", 0,
            ),
            // Stab swords (weapon category 17), as the 241 cache has them.
            listOf<Any>(17) to listOf<Any>(
                0, "Stab", "(Accurate)<br>(Stab)<br>(Attack XP)", 0,
                1, "Lunge", "(Aggressive)<br>(Stab)<br>(Strength XP)", 0,
                2, "Slash", "(Aggressive)<br>(Slash)<br>(Strength XP)", 0,
                3, "Block", "(Defensive)<br>(Stab)<br>(Defence XP)", 0,
            ),
        ),
    )
    private val npcCombat = NpcCombat.parse(
        """{ "npcs": [ { "npcs": ["man"], "attackSpeed": 4, "maxHit": 1, "attackType": "crush", "attackBonus": 0,
            "strengthBonus": 0,
            "defence": { "stab": -21, "slash": -21, "crush": -21, "magic": -21, "light": -21, "standard": -21, "heavy": -21 },
            "anims": { "attack": "a", "defend": "b", "death": "c" }, "respawnTicks": 25, "source": "test" } ] }""",
        npcTypes, SeqNames.NONE,
    )
    private val world = World(
        IntArray(0), CollisionMap(), objTypes = ObjTypes.of(sword, shortbow), npcTypes = npcTypes, attackStyles = styles, npcCombat = npcCombat,
    )
    private val player = Player(1, "test", 3222, 3218, 0)
    private val replies = mutableListOf<String>()

    private fun run(input: String) = world.commands.execute(player, input, replies::add)

    @Test
    fun `a new account punching a man`() {
        val npc = world.spawnNpc(NpcSpawn(man, 3223, 3218, 0, source = "test"))!!
        val m = world.combat.matchup(player, npc)!!
        assertEquals("Punch", m.style.name)
        assertEquals(768, m.attackRoll)
        assertEquals(430, m.defenceRoll)
        assertEquals(1, m.maxHit)
        assertEquals(1 - 432.0 / 1538, m.hitChance, 1e-9)
        assertEquals(640, m.npcAttackRoll)
        assertEquals(576, m.playerDefenceRoll)
        assertEquals(1, m.npcMaxHit)
        assertEquals(4, world.combat.attackSpeed(player))
    }

    @Test
    fun `the style follows the weapon and the combat tab's button`() {
        val npc = world.spawnNpc(NpcSpawn(man, 3223, 3218, 0, source = "test"))!!
        player.equipment[WearPos.WEAPON] = InvObj(1277, 1)
        player.vars[EquipmentTab.VARP_COM_MODE] = 2
        player.skills.setLevel(Skill.STRENGTH, 10)
        val m = world.combat.matchup(player, npc)!!
        assertEquals("Slash", m.style.name)
        assertEquals(AttackType.SLASH, m.style.type)
        // Attack 1 + 8 with +3 slash; Strength 10 + 3 + 8 with +5.
        assertEquals(9 * 67, m.attackRoll)
        assertEquals((21 * 69 + 320) / 640, m.maxHit)
        // A button the category doesn't have falls back to its first style.
        player.equipment[WearPos.WEAPON] = null
        player.vars[EquipmentTab.VARP_COM_MODE] = 2
        assertEquals("Punch", world.combat.style(player).name)
    }

    @Test
    fun `a ranged or magic style has no melee matchup, a style with no type hits as crush`() {
        val npc = world.spawnNpc(NpcSpawn(man, 3223, 3218, 0, source = "test"))!!
        val bow = AttackStyle(0, "Accurate", AttackStance.ACCURATE, AttackType.RANGED, listOf(Skill.RANGED))
        val block = AttackStyle(3, "Block", AttackStance.OTHER, AttackType.NONE, emptyList())
        assertNull(world.combat.meleeType(bow))
        assertNull(world.combat.meleeType(AttackStyle(0, "Spell", AttackStance.OTHER, AttackType.MAGIC, listOf(Skill.MAGIC))))
        assertEquals(AttackType.CRUSH, world.combat.meleeType(block))
        assertEquals(AttackType.SLASH, world.combat.meleeType(AttackStyle(2, "Slash", AttackStance.AGGRESSIVE, AttackType.SLASH, listOf(Skill.STRENGTH))))
        // Shortbow, category 3 in the 241 cache: its styles are ranged.
        world.spawnNpc(NpcSpawn(man, 3225, 3218, 0, source = "test"))
        player.equipment[WearPos.WEAPON] = InvObj(841, 1)
        assertNull(world.combat.matchup(player, npc))
        run("combat man")
        assertEquals(listOf("Accurate is a ranged style; only melee is in so far"), replies)
    }

    @Test
    fun `fight, hit and kill`() {
        world.spawnNpc(NpcSpawn(hans, 3222, 3219, 0, source = "test"))
        val npc = world.spawnNpc(NpcSpawn(man, 3225, 3218, 0, source = "test"))!!
        run("fight")
        run("hit 3")
        run("hit 2 hans")
        run("hit x")
        run("kill man")
        run("kill 2")
        assertEquals(
            listOf(
                "You are not fighting anything.",
                "Hit Man #2 for 3: 4/7 hp",
                "Hans #1 can't be fought",
                "Usage: ::hit damage [index|name|me]",
                "Killed Man #2.",
                "Man #2 is already dead",
            ),
            replies,
        )
        assertEquals(0, npc.hitpoints)
        assertEquals(1, world.meleeCombat.respawning)
        replies.clear()
        player.interaction = MeleeInteraction(npc, world.meleeCombat)
        player.nextAttackTick = 3
        run("fight")
        assertEquals(listOf("You are fighting Man #2 (0/7 hp); next swing in 3 ticks."), replies)
    }

    @Test
    fun `an NPC with no combat can't be fought`() {
        val npc = world.spawnNpc(NpcSpawn(hans, 3223, 3218, 0, source = "test"))!!
        assertNull(world.combat.matchup(player, npc))
    }

    @Test
    fun `combat shows the numbers against the nearest NPC that fights`() {
        world.spawnNpc(NpcSpawn(hans, 3222, 3219, 0, source = "test"))
        world.spawnNpc(NpcSpawn(man, 3225, 3218, 0, source = "test"))
        run("combat")
        run("combat hans")
        run("combat goblin")
        assertEquals(
            listOf(
                "Punch (accurate, crush), speed 4: attack roll 768, max hit 1",
                "vs Man #2 (level 2, 7 hp): defence roll 430, you hit 71.9%",
                "It: attack roll 640 vs your 576, hits 54.9%, max hit 1",
                "Hans #1 can't be fought",
                "No NPC called 'goblin' within 15 tiles",
            ),
            replies,
        )
    }
}
