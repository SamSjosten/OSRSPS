package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
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
import osrsps.game.content.Brutus
import osrsps.game.content.DemonicBrutus
import osrsps.game.content.DemonicLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Brutus step 6 through the headless client: the abyssal potato plucked and eaten, fed to Brutus on release for a field
 * with Demonic Brutus (who doesn't come back), his kill count, and his specials: three butts, then four bulls, the real
 * one first, going clockwise from the west or south, at the player, the charge's start swapping after each charge.
 */
class HeadlessDemonicBrutusTest {
    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = 1, length = 1))

    private val types = LocTypes(
        listOf(
            type(Brutus.GATE_LEFT, "Gate", "Release"), type(Brutus.GATE_RIGHT, "Gate", "Release"),
            type(Brutus.EXIT_LEFT, "Gate", "Leave", "Quick-escape"), type(Brutus.EXIT_RIGHT, "Gate", "Leave", "Quick-escape"),
            type(DemonicBrutus.GROWTH, "Growth", "Pluck"),
        ).associateBy { it.id },
    )

    private val brutus = NpcType(
        Brutus.BOSS, "Brutus", size = 3, ops = listOf(null, "Attack", null, null, null), combatLevel = 30, internalName = "cowboss",
        stats = NpcStats(attack = 12, defence = 10, strength = 25, hitpoints = 58, ranged = 1, magic = 8),
    )
    private val demonic = NpcType(
        DemonicBrutus.BOSS, "Demonic Brutus", size = 3, ops = listOf(null, "Attack", null, null, null), combatLevel = 1224, internalName = "cowboss_hardmode",
        stats = NpcStats(attack = 380, defence = 200, strength = 418, hitpoints = 750, ranged = 1, magic = 272),
    )
    private val ghost = NpcType(DemonicBrutus.GHOST, "Demonic Brutus", size = 3, combatLevel = 1224, internalName = "cowboss_hardmode_ghost")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply { for (x in (3240 shr 6)..(3271 shr 6)) for (z in (3256 shr 6)..(3303 shr 6)) markLoaded(x, z) }
        val npcTypes = NpcTypes.of(brutus, demonic, ghost)
        World(
            crcs, map, random = GameRandom(9), locTypes = types,
            objTypes = ObjTypes.of(ObjType(DemonicBrutus.POTATO, "Abyssal potato", iops = listOf("Eat", null, null, null, "Drop"))),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(Brutus.GATE_LEFT, LocShape.WALL_STRAIGHT, 1, 3262, 3294, 0))
                add(ZoneLoc(Brutus.GATE_RIGHT, LocShape.WALL_STRAIGHT, 1, 3263, 3294, 0))
                // Stood in for the Scar's growth, near the pen.
                add(ZoneLoc(DemonicBrutus.GROWTH, LocShape.CENTREPIECE_STRAIGHT, 0, 3256, 3290, 0))
            },
            npcTypes = npcTypes, npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE), varTypes = VarTypes(6_000, emptyMap()), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(93).filterNotNull().filter { it.id == id }.sumOf { it.count.toLong() }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().lastOrNull { it.text == text } }

    /** Pluck a potato, then Release with it and feed it: into a field with Demonic Brutus, standing at [x], [z] of the real field. */
    private fun HeadlessClient.feedIn(server: TestServer, name: String, x: Int, z: Int): osrsps.game.content.BrutusPens.Run {
        await("the login rebuild") { position }
        commandReply("god") { it.startsWith("You can") }
        command("tele 3256 3289 0")
        await("by the growth") { position?.takeIf { it == Coord(3256, 3289, 0) } }
        locOp(DemonicBrutus.GROWTH, 3256, 3290, DemonicBrutus.PLUCK)
        seen(DialogueText.NONE[DemonicLines.HARVEST])
        await("the potato") { if (count(DemonicBrutus.POTATO) == 1L) Unit else null }
        command("tele ${Brutus.OUTSIDE.x} ${Brutus.OUTSIDE.z} 0")
        await("outside the gate") { position?.takeIf { it == Coord(Brutus.OUTSIDE.x, Brutus.OUTSIDE.z, 0) } }
        locOp(Brutus.GATE_LEFT, 3262, 3294, Brutus.RELEASE)
        awaitDialogue("the potato's menu") { it is DialogueView.Menu && it.title == DialogueText.NONE[DemonicLines.FEED_TITLE] }
        chooseOption(1)
        val player = server.world.playerNamed(name)!!
        val run = await("in a field") { server.world.brutusPens.runOf(player) }
        assertTrue(run.demonic)
        assertEquals(DemonicBrutus.BOSS, run.boss!!.type.id)
        await("fed to him") { if (count(DemonicBrutus.POTATO) == 0L) Unit else null }
        val to = run.inside(x, z)
        command("tele ${to.x} ${to.z} 0")
        await("at $x, $z") { position?.takeIf { it == Coord(to.x, to.z, 0) } }
        return run
    }

    @Test
    fun `a death to Demonic Brutus isn't counted on Brutus's scoreboard`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.feedIn(server, "dave", 3262, 3270)
                val dave = server.world.playerNamed("dave")!!
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("hit 200 me") { it.startsWith("Hit yourself") }
                client.await("dead") { if (dave.dead) Unit else null }
                client.await("out of the field", timeoutMillis = 20_000) { if (!dave.dead && server.world.brutusPens.runOf(dave) == null) Unit else null }
                assertEquals(0L, server.world.brutusRecords.deaths)
            }
        }
    }

    @Test
    fun `the potato plucks and eats for a strength point, and fed on release brings Demonic Brutus, whose kill counts and who stays dead`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele 3256 3289 0")
                client.await("by the growth") { client.position?.takeIf { it == Coord(3256, 3289, 0) } }
                client.locOp(DemonicBrutus.GROWTH, 3256, 3290, DemonicBrutus.PLUCK)
                client.seen(DialogueText.NONE[DemonicLines.HARVEST])
                val slot = client.await("the potato") { client.inventory(93).indexOfFirst { it?.id == DemonicBrutus.POTATO }.takeIf { it >= 0 } }
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 2, slot, DemonicBrutus.POTATO)
                client.seen(DialogueText.NONE[DemonicLines.EAT])
                val alice = server.world.playerNamed("alice")!!
                assertEquals(alice.skills.baseLevel(Skill.STRENGTH) + 1, alice.skills.currentLevel(Skill.STRENGTH))
                client.await("eaten") { if (client.count(DemonicBrutus.POTATO) == 0L) Unit else null }

                // A drained Strength comes up by 1, not back to its level (as a potion, exploit review R58A-2).
                client.commandReply("setlevel strength 60") { it.contains("set to level 60") }
                client.commandReply("boost strength 30") { it == "Strength is now 30/60" }
                client.command("item ${DemonicBrutus.POTATO}")
                client.awaitTicks(4)
                val again = client.await("a second potato") { client.inventory(93).indexOfFirst { it?.id == DemonicBrutus.POTATO }.takeIf { it >= 0 } }
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 2, again, DemonicBrutus.POTATO)
                client.await("Strength 31") { if (alice.skills.currentLevel(Skill.STRENGTH) == 31) Unit else null }

                val run = client.feedIn(server, "alice", 3262, 3270)
                val boss = run.boss!!
                client.await("his Moo") { if (DemonicBrutus.fight(boss).mooed) Unit else null }
                client.commandReply("kill ${boss.index}") { it.startsWith("Killed") }
                client.seen(DialogueText.NONE[DemonicLines.KILL_COUNT, "count" to 1])
                assertEquals(1, alice.vars[Brutus.HARDMODE_KILL_COUNT_VARP])
                assertEquals(0, alice.vars[Brutus.KILL_COUNT_VARP], "not Brutus's count")
                client.awaitTicks(50)
                assertTrue(server.world.npcs.all.none { !it.dead && it.type.id == DemonicBrutus.BOSS && run.instance.contains(it.x, it.z) }, "no respawn")
            }
        }
    }

    @Test
    fun `three butts, then four bulls clockwise from the west or south, the real one first, then the other special`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                val run = client.feedIn(server, "bob", 3262, 3270)
                val boss = run.boss!!
                val fight = DemonicBrutus.fight(boss)
                val bob = server.world.playerNamed("bob")!!
                client.await("two specials", timeoutMillis = 60_000) { fight.specials.takeIf { it.size >= 2 && fight.bulls.size >= 8 } }
                assertTrue(fight.specials[0] != fight.specials[1], "they take turns: ${fight.specials}")
                val encounter = server.world.encounters.all.first { it.boss === boss }
                val lines = encounter.lines.toList()
                val first = lines.indexOfFirst { it.contains("charge from") || it.contains("slam from") }
                assertEquals(DemonicBrutus.BASICS, lines.subList(0, first).count { it.contains("butts bob") }, "$lines")
                // Each bull stands where the order says round the tile it was aimed at (the player's, as it appeared).
                val bulls = fight.bulls.take(4)
                assertTrue(bulls.all { it.special == fight.specials[0] })
                for (bull in bulls) {
                    val expected = if (bull.special == DemonicBrutus.Special.CHARGE) {
                        DemonicBrutus.chargeFrom(bull.aim.x, bull.aim.z, DemonicBrutus.SIDES[bull.index]).first
                    } else {
                        DemonicBrutus.slamFrom(bull.aim.x, bull.aim.z, DemonicBrutus.CORNERS[bull.index])
                    }
                    assertEquals(expected, bull.at)
                }
                val firstIndex = bulls[0].index
                if (bulls[0].special == DemonicBrutus.Special.CHARGE) assertTrue(firstIndex == 0 || firstIndex == 3, "the first charge from the west or south: $firstIndex")
                assertEquals(DemonicBrutus.order(firstIndex, true), bulls.map { it.index }, "clockwise, the real one first")
                // A slam's bulls land on the player's own tile, so all four hit (0 under ::god); Bob's auto-retaliate walks him about between bulls.
                client.awaitTicks(DemonicBrutus.SLAM_WINDOW + 2)
                assertTrue(encounter.lines.count { it.contains("hazard hit bob") } >= 4, "${encounter.lines}")
                // His ghosts are gone after their hits.
                assertTrue(server.world.npcs.all.none { it.type.id == DemonicBrutus.GHOST && !it.dead }, "the ghosts gone")
            }
        }
    }
}
