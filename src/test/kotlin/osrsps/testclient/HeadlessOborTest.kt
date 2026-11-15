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
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.DropTestItems
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.content.GiantBonesLines
import osrsps.game.content.GiantLairs
import osrsps.game.content.Obor
import osrsps.game.content.OborLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Obor (Misthalin bosses step 1) through the headless client and the registered handlers: the gate in locked without a
 * giant key, unlocked for good with one (kept), a lair of the player's own, the rocks down into his pit and back, his
 * fight, the giant bones on the floor, the chest taking the key for its loot and the kill count, his respawn, and the gate
 * out. The locs are placed as the 242 cache has them, on open ground. The rl-client script test-runner/examples/obor.json
 * does the same on the real map.
 */
class HeadlessOborTest {
    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = 1, length = 1))

    private val types = LocTypes(
        listOf(
            type(Obor.ENTRANCE_LEFT, "Gate", "Open"),
            type(Obor.ENTRANCE_RIGHT, "Gate", "Open"),
            type(Obor.EXIT_LEFT, "Gate", "Open", null, "Quick-exit"),
            type(Obor.EXIT_RIGHT, "Gate", "Open", null, "Quick-exit"),
            type(Obor.ROCKS, "Rocks", "Climb"),
            type(Obor.CHEST, "Chest", "Open"),
        ).associateBy { it.id },
    )

    private val locs = listOf(
        ZoneLoc(Obor.ENTRANCE_LEFT, LocShape.WALL_STRAIGHT, 0, 3095, 9832, 0),
        ZoneLoc(Obor.ENTRANCE_RIGHT, LocShape.WALL_STRAIGHT, 0, 3095, 9833, 0),
        ZoneLoc(Obor.EXIT_LEFT, LocShape.WALL_STRAIGHT, 1, 3091, 9815, 0),
        ZoneLoc(Obor.EXIT_RIGHT, LocShape.WALL_STRAIGHT, 1, 3092, 9815, 0),
        ZoneLoc(Obor.ROCKS, LocShape.CENTREPIECE_STRAIGHT, 0, 3091, 9805, 0),
        ZoneLoc(Obor.ROCKS, LocShape.CENTREPIECE_STRAIGHT, 0, 3092, 9805, 0),
        ZoneLoc(Obor.ROCKS, LocShape.CENTREPIECE_STRAIGHT, 0, 3091, 9806, 0),
        ZoneLoc(Obor.ROCKS, LocShape.CENTREPIECE_STRAIGHT, 0, 3092, 9806, 0),
        ZoneLoc(Obor.CHEST, LocShape.CENTREPIECE_STRAIGHT, 2, 3085, 9797, 0),
    )

    private val obor = NpcType(
        Obor.BOSS, "Obor", size = 2, ops = listOf(null, "Attack", null, null, null), combatLevel = 106, internalName = "hillgiant_boss",
        stats = NpcStats(attack = 90, defence = 60, strength = 100, hitpoints = 120, ranged = 120, magic = 1),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply { markLoaded(3072 shr 6, 9792 shr 6) }
        val npcTypes = NpcTypes.of(obor)
        val drops = DropTestItems.objTypes()
        val extra = listOf(
            ObjType(Obor.GIANT_KEY, "Giant key", stackable = true), ObjType(GiantLairs.GIANT_BONES, "Giant bones", ops = listOf(null, null, "Bury", "Take", null)),
            ObjType(20756, "Hill giant club"), ObjType(6800, "Giant champion scroll"), ObjType(226, "Limpwurt root", stackable = true),
            ObjType(533, "Big bones", stackable = true), ObjType(1620, "Uncut ruby", stackable = true), ObjType(1618, "Uncut diamond", stackable = true),
        )
        val objTypes = ObjTypes.of(*((0 until drops.size).mapNotNull { drops[it] }.filter { d -> extra.none { it.id == d.id } } + extra).toTypedArray())
        World(
            crcs, map, random = GameRandom(5), objTypes = objTypes, locTypes = types, staticLocs = StaticLocs().apply { locs.forEach(::add) },
            npcTypes = npcTypes, npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE), npcDrops = NpcDrops.load(npcTypes, objTypes), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int, level: Int) {
        command("tele $x $z $level")
        await("the teleport to $x, $z, $level") { position?.takeIf { it == Coord(x, z, level) } }
    }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.keys(): Int = inventory(BACKPACK).filterNotNull().filter { it.id == Obor.GIANT_KEY }.sumOf { it.count }

    @Test
    fun `the gate is locked without a key, unlocks for good with one, and the key is kept`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(Obor.EXIT.x, Obor.EXIT.z, 0)
                client.locOp(Obor.ENTRANCE_LEFT, 3095, 9832, Obor.OPEN)
                client.seen(DialogueText.NONE[OborLines.GATE_LOCKED])
                assertNull(server.world.giantLairs.runOf(server.world.playerNamed("alice")!!), "still outside")

                client.commandReply("item ${Obor.GIANT_KEY} 1") { it.contains("Giant key") }
                // The menu's No leaves the gate as it was.
                client.locOp(Obor.ENTRANCE_RIGHT, 3095, 9833, Obor.OPEN)
                client.pick(OborLines.GATE_TITLE, 2)
                client.awaitTicks(2)
                assertNull(server.world.giantLairs.runOf(server.world.playerNamed("alice")!!), "No keeps the player out")
                assertFalse(server.world.giantLairs.unlocked(server.world.playerNamed("alice")!!, Obor.LAIR), "No unlocks nothing")
                client.locOp(Obor.ENTRANCE_RIGHT, 3095, 9833, Obor.OPEN)
                client.pick(OborLines.GATE_TITLE, 1)
                client.seen(DialogueText.NONE[OborLines.GATE_OPENED])
                val run = client.await("inside a lair") { server.world.giantLairs.runOf(server.world.playerNamed("alice")!!) }
                val entry = run.inside(Obor.ENTRY.x, Obor.ENTRY.z)
                client.await("at the lair's entry") { client.position?.takeIf { it == Coord(entry.x, entry.z, 0) } }
                assertEquals(1, client.keys(), "the key is kept")

                // Out by the gate, and in again with no key: it stays unlocked.
                val exit = run.inside(3091, 9815)
                client.locOp(Obor.EXIT_LEFT, exit.x, exit.z, Obor.QUICK_EXIT)
                client.await("back outside the gate in") { client.position?.takeIf { it == Coord(Obor.EXIT.x, Obor.EXIT.z, 0) } }
                client.command("empty")
                client.await("an empty backpack") { if (client.keys() == 0) Unit else null }
                client.locOp(Obor.ENTRANCE_LEFT, 3095, 9832, Obor.OPEN)
                client.pick(OborLines.GATE_TITLE, 1)
                client.await("inside a lair again") { server.world.giantLairs.runOf(server.world.playerNamed("alice")!!) }
                assertTrue(server.world.giantLairs.unlocked(server.world.playerNamed("alice")!!, Obor.LAIR), "unlocked for good")
            }
        }
    }

    @Test
    fun `down the rocks he fights, his bones bury from the floor, the chest takes the key and he comes back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(Obor.EXIT.x, Obor.EXIT.z, 0)
                client.commandReply("item ${Obor.GIANT_KEY} 2") { it.contains("Giant key") }
                client.locOp(Obor.ENTRANCE_LEFT, 3095, 9832, Obor.OPEN)
                client.pick(OborLines.GATE_TITLE, 1)
                val run = client.await("inside a lair") { server.world.giantLairs.runOf(server.world.playerNamed("bob")!!) }
                val boss = assertNotNull(run.boss, "Obor is in his pit")
                val fight = server.world.encounters.all.single { it.boss === boss }

                // On the ledge nobody is in his fight.
                client.awaitTicks(3)
                assertTrue(fight.lines.none { it.contains("takes on") }, fight.lines.toString())

                // Down the rocks, into his pit.
                val rock = run.inside(3091, 9806)
                client.locOp(Obor.ROCKS, rock.x, rock.z, Obor.CLIMB)
                val pit = run.inside(3091, Obor.ROCKS_SOUTH - 1)
                client.await("down in the pit") { client.position?.takeIf { it == Coord(pit.x, pit.z, 0) } }
                client.await("his first attack", timeoutMillis = 20_000) { fight.lines.firstOrNull { it.contains("swings at bob") || it.contains("throws at bob") } }

                // A real hit on him from the pit, then ::kill finishes him (a knock-back stops the player's own attacks).
                client.commandReply("god") { it.startsWith("You can") }
                client.command("setlevel attack 99")
                client.command("setlevel strength 99")
                client.npcOp(boss.index, 2)
                client.await("a hit on him", timeoutMillis = 30_000) { if (boss.hitpoints < boss.maxHitpoints) Unit else null }
                client.commandReply("kill ${boss.index}") { it.startsWith("Killed") }
                client.await("his death") { if (boss.dead) Unit else null }
                val bones = client.await("his giant bones", timeoutMillis = 20_000) { client.groundObjs().firstOrNull { it.id == GiantLairs.GIANT_BONES } }

                // The bones can't be taken; they bury from the floor.
                client.objOp(GiantLairs.GIANT_BONES, bones.coord.x, bones.coord.z, GiantLairs.TAKE)
                client.seen(DialogueText.NONE[GiantBonesLines.NO_FIT])
                val prayerXp = client.stat(Skill.PRAYER.id)?.xp ?: 0
                client.objOp(GiantLairs.GIANT_BONES, bones.coord.x, bones.coord.z, GiantLairs.BURY)
                client.await("150 Prayer XP") { client.stat(Skill.PRAYER.id)?.takeIf { it.xp == prayerXp + 150 } }
                client.await("the bones gone") { if (client.groundObjs().none { it.id == GiantLairs.GIANT_BONES && it.coord == bones.coord }) Unit else null }

                // Back up the rocks to the chest is not needed: it is in the pit. Its loot goes on the floor, the key goes.
                val chest = run.inside(3085, 9797)
                val keysBefore = client.keys()
                client.locOp(Obor.CHEST, chest.x, chest.z, Obor.OPEN)
                client.seen(DialogueText.NONE[OborLines.CHEST_STIR])
                client.seen(DialogueText.NONE[OborLines.CHEST_LOOT])
                assertEquals(keysBefore - 1, client.keys(), "one key used")
                client.await("the kill count") { client.messages().firstOrNull { it.text.contains("obor.kill_count") } }
                assertEquals(1, server.world.playerNamed("bob")!!.vars[Obor.KILL_COUNT_VARP])
                client.await("the chest's clue on the floor") { client.groundObjs().firstOrNull { it.id == CLUE_BEGINNER } }

                // A second opening on the same kill gives nothing more.
                client.awaitTicks(1)
                assertNull(run.boss, "no Obor until the respawn")
                val back = client.await("Obor back after 15 ticks", timeoutMillis = 30_000) { run.boss?.takeIf { !it.dead } }
                assertEquals(keysBefore - 1, client.keys(), "the respawn takes no key")

                // Up the rocks and out, with him killed again so no knock-back stops the climb.
                client.commandReply("kill ${back.index}") { it.startsWith("Killed") }
                client.await("his second death") { if (back.dead) Unit else null }
                // With no key the chest only stirs him.
                client.command("empty")
                client.await("an empty backpack") { if (client.keys() == 0) Unit else null }
                client.locOp(Obor.CHEST, chest.x, chest.z, Obor.OPEN)
                client.seen(DialogueText.NONE[OborLines.CHEST_NO_KEY])
                assertEquals(1, server.world.playerNamed("bob")!!.vars[Obor.KILL_COUNT_VARP], "no key, no count")
                val ledge = run.inside(3091, 9805)
                client.locOp(Obor.ROCKS, ledge.x, ledge.z, Obor.CLIMB)
                val top = run.inside(3091, Obor.ROCKS_NORTH + 1)
                client.await("back on the ledge") { client.position?.takeIf { it == Coord(top.x, top.z, 0) } }
                val gate = run.inside(3092, 9815)
                client.locOp(Obor.EXIT_RIGHT, gate.x, gate.z, Obor.OPEN)
                client.pick(OborLines.EXIT_TITLE, 2)
                client.awaitTicks(2)
                assertNotNull(server.world.giantLairs.runOf(server.world.playerNamed("bob")!!), "Stay keeps the player in")
                client.locOp(Obor.EXIT_RIGHT, gate.x, gate.z, Obor.OPEN)
                client.pick(OborLines.EXIT_TITLE, 1)
                client.await("outside") { client.position?.takeIf { it == Coord(Obor.EXIT.x, Obor.EXIT.z, 0) } }
                client.await("the lair gone once empty") { if (server.world.instances.all.none { it === run.instance }) Unit else null }
            }
        }
    }

    @Test
    fun `his moves follow the prayers as the Wiki has them`() {
        val always1 = { _: Int, _: Int -> 1 }
        val always2 = { _: Int, _: Int -> 2 }
        // Beside: no prayer, he swings; Protect from Melee, swing or throw; Protect from Missiles, throw one in three.
        assertEquals(Obor.Move.SWING, Obor.decide(beside = true, protectMelee = false, protectMissiles = false, roll = always2))
        assertEquals(Obor.Move.SWING, Obor.decide(beside = true, protectMelee = true, protectMissiles = false, roll = always1))
        assertEquals(Obor.Move.THROW_AND_STAND, Obor.decide(beside = true, protectMelee = true, protectMissiles = false, roll = always2))
        assertEquals(Obor.Move.THROW_AND_STAND, Obor.decide(beside = true, protectMelee = false, protectMissiles = true, roll = always1))
        assertEquals(Obor.Move.SWING, Obor.decide(beside = true, protectMelee = false, protectMissiles = true, roll = always2))
        // Away: always a throw; he approaches always under Protect from Missiles, else one in three.
        assertEquals(Obor.Move.THROW_AND_APPROACH, Obor.decide(beside = false, protectMelee = false, protectMissiles = true, roll = always2))
        assertEquals(Obor.Move.THROW_AND_APPROACH, Obor.decide(beside = false, protectMelee = false, protectMissiles = false, roll = always1))
        assertEquals(Obor.Move.THROW_AND_STAND, Obor.decide(beside = false, protectMelee = true, protectMissiles = false, roll = always2))
    }

    private companion object {
        const val CLUE_BEGINNER = 23182
        const val BACKPACK = 93
    }

    /** Waits for the menu titled [title] (shown by key) and picks [option] of it. */
    private fun HeadlessClient.pick(title: osrsps.game.dialogue.Line, option: Int) {
        awaitDialogue("the menu ${title.key}") { it is DialogueView.Menu && it.title == DialogueText.NONE[title] }
        chooseOption(option)
    }
}
