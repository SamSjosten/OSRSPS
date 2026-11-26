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
import osrsps.game.VarbitType
import osrsps.game.content.BrutusScoreboard
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.combat.NpcDrops
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
import osrsps.map.Tile

/**
 * Brutus step 6 through the headless client: the abyssal potato plucked and eaten, fed to Brutus on release for a field
 * with Demonic Brutus (who doesn't come back), his kill count, and his specials: three butts, then four bulls, the real
 * one first, going clockwise from the west or south, at the player, the charge's start swapping after each charge.
 */
class HeadlessDemonicBrutusTest {
    private companion object {
        const val SLIPPERS = 33099
        const val BEEF = 33124
    }

    private fun type(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = 1, length = 1))

    private val types = LocTypes(
        listOf(
            type(Brutus.GATE_LEFT, "Gate", "Release"), type(Brutus.GATE_RIGHT, "Gate", "Release"),
            type(Brutus.EXIT_LEFT, "Gate", "Leave", "Quick-escape"), type(Brutus.EXIT_RIGHT, "Gate", "Leave", "Quick-escape"),
            type(DemonicBrutus.GROWTH, "Growth", "Pluck"),
            LocType(BrutusScoreboard.SCOREBOARD_MULTI, "null", collision = LocCollisionType(width = 1, length = 1), multiloc = true),
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
        val objs = ObjTypes.of(
            ObjType(DemonicBrutus.POTATO, "Abyssal potato", iops = listOf("Eat", null, null, null, "Drop")),
            ObjType(SLIPPERS, "Brutus slippers"), ObjType(BEEF, "Beef"),
        )
        World(
            crcs, map, random = GameRandom(9), locTypes = types,
            objTypes = objs, npcDrops = NpcDrops.load(npcTypes, objs),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(Brutus.GATE_LEFT, LocShape.WALL_STRAIGHT, 1, 3262, 3294, 0))
                add(ZoneLoc(Brutus.GATE_RIGHT, LocShape.WALL_STRAIGHT, 1, 3263, 3294, 0))
                // Stood in for the Scar's growth, near the pen.
                add(ZoneLoc(DemonicBrutus.GROWTH, LocShape.CENTREPIECE_STRAIGHT, 0, 3256, 3290, 0))
                add(ZoneLoc(BrutusScoreboard.SCOREBOARD_MULTI, LocShape.CENTREPIECE_STRAIGHT, 1, 3258, 3298, 0))
            },
            npcTypes = npcTypes, npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE), varTypes = VarTypes(6_000, mapOf(BrutusScoreboard.SCOREBOARDS_VARBIT to VarbitType(5405, 0, 2))), cycleMillis = millis,
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
                // Beside him, to see his drop.
                client.command("tele ${boss.x - 1} ${boss.z} 0")
                client.await("beside him") { client.position?.takeIf { it.x == boss.x - 1 || it.x == boss.x + 3 } }
                // His first 0 is his second phase: his MOO_MOO line and 487 hitpoints.
                client.commandReply("kill ${boss.index}") { it.startsWith("Killed") }
                client.await("phase two") { if (DemonicBrutus.fight(boss).phaseTwo) Unit else null }
                assertTrue(!boss.dead)
                assertEquals(DemonicBrutus.PHASE_TWO_HEAL, boss.hitpoints)
                client.commandReply("kill ${boss.index}") { it.startsWith("Killed") }
                client.seen(DialogueText.NONE[DemonicLines.KILL_COUNT, "count" to 1])
                assertEquals(BrutusScoreboard.SCOREBOARD_DEMONIC_SHOWN, alice.vars.getVarbit(BrutusScoreboard.SCOREBOARDS_VARBIT), "Read (Demonic) on the board")
                assertEquals(1L, server.world.brutusDemonicRecords.kills)
                assertEquals(0L, server.world.brutusRecords.kills, "not Brutus's records")
                client.await("the slippers") { client.groundObjs().firstOrNull { it.id == SLIPPERS } }

                // Read (Demonic) on the scoreboard: his numbers.
                client.command("tele 3258 3297 0")
                client.await("by the board") { client.position?.takeIf { it == Coord(3258, 3297, 0) } }
                client.locOp(BrutusScoreboard.SCOREBOARD_MULTI, 3258, 3298, BrutusScoreboard.READ_DEMONIC)
                val texts = client.await("the Demonic page") { client.texts(BrutusScoreboard.INTERFACE).takeIf { it.size >= 6 } }
                assertEquals("1", texts[BrutusScoreboard.PERSONAL_KILLS])
                assertEquals("1", texts[BrutusScoreboard.GLOBAL_KILLS])
                assertEquals(1, alice.vars[Brutus.HARDMODE_KILL_COUNT_VARP])
                assertEquals(0, alice.vars[Brutus.KILL_COUNT_VARP], "not Brutus's count")
                client.awaitTicks(50)
                assertTrue(server.world.npcs.all.none { !it.dead && it.type.id == DemonicBrutus.BOSS && run.instance.contains(it.x, it.z) }, "no respawn")
            }
        }
    }

    @Test
    fun `out of his reach for 15 ticks he comes on with a special anyway, so there's no safespot`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                val run = client.feedIn(server, "dave", 3262, 3270)
                val boss = run.boss!!
                val fight = DemonicBrutus.fight(boss)
                val encounter = client.await("his fight") { server.world.encounters.all.firstOrNull { it.boss === boss } }
                // Counted only once she has attacked him (her auto-retaliate does, after his first butt).
                client.await("she's attacked him", timeoutMillis = 30_000) { run.fightStart.takeIf { it >= 0 } }
                // Held where he is, with her well off: he can't reach her, and no butts count down.
                server.world.npcs.freeze(boss, 200)
                val off = Tile(boss.x + 6, boss.z)
                client.command("tele ${off.x} ${off.z} 0")
                client.await("away from him") { client.position?.takeIf { it == Coord(off.x, off.z, 0) } }
                client.await("out of reach", timeoutMillis = 30_000) { encounter.lines.firstOrNull { it.contains("out of reach") } }
                client.await("a special", timeoutMillis = 10_000) { fight.specials.firstOrNull() }
                assertEquals(DemonicBrutus.BASICS, fight.basicsLeft, "the butts before the next start again")
            }
        }
    }

    @Test
    fun `his bulls stay in his field, and his fight ends with the field, taking him wherever he stands`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                val run = client.feedIn(server, "erin", 3262, 3270)
                val boss = run.boss!!
                val low = run.inside(Brutus.FIELD_X, Brutus.FIELD_Z)
                // A slam from the south-west of a player on the field's south-west corner would stand him off it.
                assertEquals(low, DemonicBrutus.fieldTile(run, DemonicBrutus.slamFrom(low.x, low.z, DemonicBrutus.CORNERS[0]), 3))
                val highX = low.x + Brutus.FIELD_WIDTH_ZONES * 8 - 3
                assertEquals(Tile(highX, low.z + 5), DemonicBrutus.fieldTile(run, Tile(highX + 4, low.z + 5), 3))
                val encounter = client.await("his fight") { server.world.encounters.all.firstOrNull { it.boss === boss } }
                // Stood off the field anyway (as a bull could before), then the field ends.
                server.world.npcs.teleport(boss, run.instance.maxX + 10, run.instance.baseZ)
                server.world.endInstance(run.instance)
                client.await("his fight called off") { if (encounter.ended) Unit else null }
                client.await("him gone") { if (server.world.npcs[boss.index] !== boss) Unit else null }
            }
        }
    }

    @Test
    fun `quicker below 500, anticlockwise below 250, and in his second phase four orbs a special, magic, melee and ranged first`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                val run = client.feedIn(server, "carol", 3262, 3270)
                val boss = run.boss!!
                val fight = DemonicBrutus.fight(boss)
                client.await("his fight") { server.world.encounters.all.firstOrNull { it.boss === boss } }
                boss.hitpoints = 400
                client.await("a quick special", timeoutMillis = 60_000) { fight.timings.firstOrNull() }.let { (window, gap) ->
                    assertEquals(DemonicBrutus.FAST_GHOST_GAP, gap)
                    assertTrue(window == DemonicBrutus.FAST_CHARGE_WINDOW || window == DemonicBrutus.FAST_SLAM_WINDOW, "window $window")
                }
                assertTrue(fight.clockwise)
                boss.hitpoints = 200
                client.await("an anticlockwise special", timeoutMillis = 60_000) { fight.bulls.drop(4).take(4).takeIf { it.size == 4 } }.let { bulls ->
                    assertEquals(DemonicBrutus.order(bulls[0].index, false), bulls.map { it.index })
                }
                // His second phase: orbs.
                client.commandReply("kill ${boss.index}") { it.startsWith("Killed") }
                client.await("phase two") { if (fight.phaseTwo) Unit else null }
                client.await("four orbs", timeoutMillis = 60_000) { fight.orbs.takeIf { it.size >= 4 } }
                assertEquals(DemonicBrutus.ORB_STYLES, fight.orbs.take(3).map { it.first })
                assertTrue(fight.orbs[3].first in DemonicBrutus.ORB_STYLES)
                // All four spawn graphics go out, each in its own slot.
                assertEquals(listOf(1, 2, 3, 4), fight.orbSpawns.map { it.first })
                assertEquals(fight.orbs.take(4).map { DemonicBrutus.SPOT_ORB_SPAWN.getValue(it.first) }, fight.orbSpawns.map { it.second })
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
