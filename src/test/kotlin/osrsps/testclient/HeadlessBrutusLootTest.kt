package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
import osrsps.game.content.Brutus
import osrsps.game.content.BrutusLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import osrsps.map.Tile

/**
 * Brutus step 3 through the headless client: a kill in the player's own field counts (`total_cowboss_kills`, with the
 * kill-count message), his drop lands on his tile nearest the player with the bull bones and a raw t-bone steak always
 * in it and his table read from npc-drops.json, and the bull bones bury where they lie for 40 Prayer XP.
 */
class HeadlessBrutusLootTest {
    private val types = LocTypes(
        listOf(
            LocType(Brutus.GATE_LEFT, "Gate", ops = listOf("Release", null, null, null, null), collision = LocCollisionType(width = 1, length = 1)),
            LocType(Brutus.EXIT_LEFT, "Gate", ops = listOf("Leave", "Quick-escape", null, null, null), collision = LocCollisionType(width = 1, length = 1)),
        ).associateBy { it.id },
    )

    private val brutus = NpcType(
        Brutus.BOSS, "Brutus", size = 3, ops = listOf(null, "Attack", null, null, null), combatLevel = 30, internalName = "cowboss",
        stats = NpcStats(attack = 12, defence = 10, strength = 25, hitpoints = 58, ranged = 1, magic = 8),
    )

    private val objs = ObjTypes.of(
        ObjType(Brutus.BULL_BONES, "Bull bones", ops = listOf(null, null, "Bury", null, null)),
        ObjType(33106, "Raw t-bone steak"), ObjType(33107, "Raw t-bone steak", stackable = true), ObjType(33101, "Mooleta"),
        ObjType(33091, "Bottomless milk bucket (empty)"), ObjType(33093, "Cow slippers"), ObjType(33124, "Beef"),
        ObjType(1153, "Iron full helm"), ObjType(1115, "Iron platebody"), ObjType(1067, "Iron platelegs"), ObjType(1081, "Iron plateskirt"),
        ObjType(884, "Iron arrow", stackable = true), ObjType(556, "Air rune", stackable = true), ObjType(558, "Mind rune", stackable = true),
        ObjType(562, "Chaos rune", stackable = true), ObjType(5318, "Potato seed", stackable = true), ObjType(5312, "Acorn", stackable = true),
        ObjType(1739, "Cowhide"), ObjType(1521, "Oak logs"), ObjType(1511, "Logs"), ObjType(23182, "Clue scroll (beginner)"), ObjType(2677, "Clue scroll (easy)"),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply { for (x in (3240 shr 6)..(3271 shr 6)) for (z in (3256 shr 6)..(3303 shr 6)) markLoaded(x, z) }
        val npcTypes = NpcTypes.of(brutus)
        World(
            crcs, map, random = GameRandom(11), objTypes = objs, locTypes = types,
            staticLocs = StaticLocs().apply { add(ZoneLoc(Brutus.GATE_LEFT, LocShape.WALL_STRAIGHT, 1, 3262, 3294, 0)) },
            npcTypes = npcTypes, npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE), npcDrops = NpcDrops.load(npcTypes, objs),
            varTypes = VarTypes(6_000, emptyMap()), cycleMillis = millis,
        )
    }

    @Test
    fun `a kill counts, drops on his tile nearest the player, and the bull bones bury where they lie`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.command("tele ${Brutus.OUTSIDE.x} ${Brutus.OUTSIDE.z} 0")
                client.await("outside the gate") { client.position?.takeIf { it == Coord(Brutus.OUTSIDE.x, Brutus.OUTSIDE.z, 0) } }
                client.locOp(Brutus.GATE_LEFT, 3262, 3294, Brutus.RELEASE)
                client.awaitDialogue("Release's menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                val alice = server.world.playerNamed("alice")!!
                val run = client.await("in a field") { server.world.brutusPens.runOf(alice) }
                val boss = run.boss!!
                // Due east of his middle row, so his east edge's middle tile is nearest.
                server.world.npcs.freeze(boss, 100)
                val to = Tile(boss.x + 6, boss.z + 1)
                client.command("tele ${to.x} ${to.z} 0")
                client.await("east of him") { client.position?.takeIf { it == Coord(to.x, to.z, 0) } }
                val nearest = Tile(boss.x + 2, boss.z + 1)
                client.commandReply("kill ${boss.index}") { it.startsWith("Killed") }

                client.await("the kill count") { client.messages().lastOrNull { it.text == DialogueText.NONE[BrutusLines.KILL_COUNT, "count" to 1] } }
                assertEquals(1, alice.vars[Brutus.KILL_COUNT_VARP])
                val bones = client.await("the bull bones") { client.groundObjs().firstOrNull { it.id == Brutus.BULL_BONES } }
                assertEquals(Coord(nearest.x, nearest.z, 0), bones.coord, "on his tile nearest her")
                assertNotNull(client.groundObjs().firstOrNull { it.id == 33106 && it.coord == Coord(nearest.x, nearest.z, 0) }, "a raw t-bone steak always")
                assertTrue(client.groundObjs().count { it.coord == Coord(nearest.x, nearest.z, 0) } >= 3, "and a roll on his table: ${client.groundObjs()}")

                val before = alice.skills.tenths(Skill.PRAYER)
                client.objOp(Brutus.BULL_BONES, bones.coord.x, bones.coord.z, Brutus.BURY)
                client.await("buried") { if (client.groundObjs().none { it.id == Brutus.BULL_BONES }) Unit else null }
                client.await("40 Prayer XP") { (alice.skills.tenths(Skill.PRAYER) - before).takeIf { it > 0 } }
                assertEquals(Brutus.BULL_BONES_XP_TENTHS, alice.skills.tenths(Skill.PRAYER) - before)
            }
        }
    }
}
