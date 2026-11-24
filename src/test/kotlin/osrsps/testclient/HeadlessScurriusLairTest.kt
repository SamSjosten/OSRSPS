package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.content.HistorianAldoLines
import osrsps.game.content.Scurrius
import osrsps.game.content.ScurriusLairLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Scurrius step 1 through the headless client and the registered handlers: the broken bars into the public lair and a
 * private one, Peek, the sewage water out and the delay before coming back, the food piles, the grave beside Historian
 * Aldo and his talk. The locs are placed as the 242 cache has them, on open ground.
 */
class HeadlessScurriusLairTest {
    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = 1, length = 1))

    private val types = LocTypes(
        listOf(
            type(Scurrius.BARS, "Broken bars", "Climb-through (normal)", "Climb-through (private)", "Peek (normal)"),
            type(Scurrius.WATER, "Sewage water", "Cross", "Quick-escape"),
            type(Scurrius.FOOD_PILE, "Food Pile", "Eat"),
            type(Scurrius.FOOD_PILE_2, "Food Pile", "Eat"),
        ).associateBy { it.id },
    )

    private val locs = listOf(
        ZoneLoc(Scurrius.BARS, LocShape.CENTREPIECE_STRAIGHT, 1, 3282, 9867, 0),
        ZoneLoc(Scurrius.WATER, LocShape.CENTREPIECE_STRAIGHT, 3, 3288, 9867, 0),
        ZoneLoc(Scurrius.FOOD_PILE, LocShape.CENTREPIECE_STRAIGHT, 1, 3307, 9867, 0),
    )

    private fun scurrius(id: Int, hitpoints: Int) = NpcType(
        id, "Scurrius", size = 3, ops = listOf(null, "Attack", null, null, null), combatLevel = 200, internalName = "rat_boss_instance",
        stats = NpcStats(attack = 300, defence = 60, strength = 100, hitpoints = hitpoints, ranged = 50, magic = 50),
    )
    private val aldo = NpcType(Scurrius.ALDO_TALK, "Historian Aldo", ops = listOf("Talk-to", null, null, null, null), internalName = "rat_boss_biologist_op1")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply { markLoaded(3282 shr 6, 9867 shr 6) }
        val npcTypes = NpcTypes.of(scurrius(Scurrius.BOSS_PRIVATE, 500), aldo)
        World(
            crcs, map, random = GameRandom(7), locTypes = types, staticLocs = StaticLocs().apply { locs.forEach(::add) },
            npcTypes = npcTypes, npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.seen(line: Line) = await(line.key) { messages().lastOrNull { it.text.contains(line.key) } }

    private fun HeadlessClient.at(x: Int, z: Int) = await("at $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }

    @Test
    fun `the bars take players into the public lair or one of their own, and the water out starts the delay`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(Scurrius.OUTSIDE.x, Scurrius.OUTSIDE.z, 0)
                val alice = server.world.playerNamed("alice")!!
                val lairs = server.world.scurriusLairs

                client.locOp(Scurrius.BARS, 3282, 9867, Scurrius.PEEK)
                client.seen(ScurriusLairLines.PEEK_EMPTY)

                // The public lair is the real map's: no instance, multi-way.
                client.locOp(Scurrius.BARS, 3282, 9867, Scurrius.CLIMB_PUBLIC)
                client.at(Scurrius.INSIDE.x, Scurrius.INSIDE.z)
                assertNull(lairs.runOf(alice))
                assertTrue(lairs.inLair(0, alice.x, alice.z))
                assertEquals(1, lairs.inPublic())
                assertTrue(server.world.multiCombat.at(0, alice.x, alice.z), "multi-way in the lair")
                assertFalse(server.world.multiCombat.at(0, Scurrius.OUTSIDE.x, Scurrius.OUTSIDE.z), "single-way outside it")

                // Out by the water, then straight back is refused for the delay.
                client.locOp(Scurrius.WATER, 3288, 9867, Scurrius.CROSS)
                client.at(Scurrius.OUTSIDE.x, Scurrius.OUTSIDE.z)
                client.locOp(Scurrius.BARS, 3282, 9867, Scurrius.CLIMB_PRIVATE)
                client.seen(ScurriusLairLines.REENTRY)
                assertNull(lairs.runOf(alice))
                client.awaitTicks(Scurrius.REENTRY_TICKS)

                // A private lair: an instance with the solo Scurrius in it.
                client.locOp(Scurrius.BARS, 3282, 9867, Scurrius.CLIMB_PRIVATE)
                val run = client.await("in a lair of her own") { lairs.runOf(alice) }
                val entry = run.inside(Scurrius.INSIDE.x, Scurrius.INSIDE.z)
                client.at(entry.x, entry.z)
                assertEquals(0, lairs.inPublic(), "not in the public lair")
                assertTrue(lairs.inLair(0, alice.x, alice.z))
                assertTrue(server.world.multiCombat.at(0, alice.x, alice.z), "a private lair is multi-way too")
                val spawn = run.inside(Scurrius.SPAWN.x, Scurrius.SPAWN.z)
                val boss = assertNotNull(server.world.npcs.all.firstOrNull { it.type.id == Scurrius.BOSS_PRIVATE && it.x == spawn.x && it.z == spawn.z })
                assertEquals(500, boss.maxHitpoints)

                // A death in a lair: the grave beside Aldo, back at the spawn.
                val place = assertNotNull(lairs.deathPlace(alice))
                assertEquals(Scurrius.GRAVE.x, place.graveX)
                assertEquals(Scurrius.GRAVE.z, place.graveZ)
                assertEquals(World.SPAWN_X, place.toX)

                val water = run.inside(3288, 9867)
                client.locOp(Scurrius.WATER, water.x, water.z, Scurrius.QUICK_ESCAPE)
                client.at(Scurrius.OUTSIDE.x, Scurrius.OUTSIDE.z)
                assertNull(lairs.deathPlace(alice), "outside, the usual grave")
            }
        }
    }

    @Test
    fun `a food pile heals in full out of combat, once every ten minutes`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3306, 9867, 0)
                val bob = server.world.playerNamed("bob")!!
                bob.skills.setCurrentLevel(Skill.HITPOINTS, 3)
                client.locOp(Scurrius.FOOD_PILE, 3307, 9867, Scurrius.EAT)
                client.seen(ScurriusLairLines.EAT_HEALED)
                assertEquals(bob.skills.baseLevel(Skill.HITPOINTS), bob.skills.currentLevel(Skill.HITPOINTS))

                bob.skills.setCurrentLevel(Skill.HITPOINTS, 3)
                client.locOp(Scurrius.FOOD_PILE, 3307, 9867, Scurrius.EAT)
                client.seen(ScurriusLairLines.EAT_WAIT)
                assertTrue(client.messages().last { it.text.contains(ScurriusLairLines.EAT_WAIT.key) }.text.contains("10"), "ten minutes to wait")
                assertEquals(3, bob.skills.currentLevel(Skill.HITPOINTS))

                // Just attacked: refused, whatever the cooldown.
                bob.attackedTick = server.world.tick
                client.locOp(Scurrius.FOOD_PILE, 3307, 9867, Scurrius.EAT)
                client.seen(ScurriusLairLines.EAT_COMBAT)

                // Just attacked something, untouched: refused too.
                bob.attackedTick = -1
                bob.attackedNpcTick = server.world.tick
                client.locOp(Scurrius.FOOD_PILE, 3307, 9867, Scurrius.EAT)
                client.await("refused again") { client.messages().count { it.text.contains(ScurriusLairLines.EAT_COMBAT.key) }.takeIf { it >= 2 } }
                assertEquals(3, bob.skills.currentLevel(Skill.HITPOINTS))
            }
        }
    }

    @Test
    fun `Historian Aldo talks of the bars, the spine and bone weapons`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3276, 9870, 0)
                client.commandReply("npc ${Scurrius.ALDO_TALK}") { it.isNotEmpty() }
                val npc = client.await("Aldo") { server.world.npcs.all.firstOrNull { it.type.id == Scurrius.ALDO_TALK } }
                client.npcOp(npc.index, Scurrius.TALK_TO)
                client.awaitDialogue("his greeting") { it is DialogueView.Chat && it.text == DialogueText.NONE[HistorianAldoLines.HELLO] }
                client.continueDialogue()
                client.awaitDialogue("the menu") { it is DialogueView.Menu && it.options.size == 4 }
                client.chooseOption(3)
                client.awaitDialogue("the player asks") { it is DialogueView.Chat && it.text == DialogueText.NONE[HistorianAldoLines.ASK_WEAPON] }
                client.continueDialogue()
                client.awaitDialogue("what to bring") { it is DialogueView.Chat && it.text == DialogueText.NONE[HistorianAldoLines.WEAPON_NEEDS] }
                client.continueDialogue()
                client.awaitDialogue("the fee") { it is DialogueView.Chat && it.text == DialogueText.NONE[HistorianAldoLines.WEAPON_FEE] }
                client.continueDialogue()
                client.awaitDialogue("come back") { it is DialogueView.Chat && it.text == DialogueText.NONE[HistorianAldoLines.WEAPON_COME_BACK] }
                client.continueDialogue()
                client.awaitDialogue("the menu again") { it is DialogueView.Menu && it.options.size == 4 }
                client.chooseOption(4)
                client.awaitDialogue("goodbye") { it is DialogueView.Chat && it.text == DialogueText.NONE[HistorianAldoLines.GOODBYE] }
            }
        }
    }
}
