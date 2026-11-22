package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.content.Brutus
import osrsps.game.content.BrutusFight
import osrsps.game.content.BrutusLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.encounter.Encounter
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.Npc
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import osrsps.map.Tile

/**
 * Brutus step 2 through the headless client: his butts and his specials in a field of the player's own on open ground,
 * the charge and the stomps taking turns after four or five butts, his overheads, the stomp aimed at the player's
 * tile, and the *huff* that brings him back beside a player he can't reach. The seed is fixed, so his picks replay.
 */
class HeadlessBrutusFightTest {
    private fun type(id: Int, vararg ops: String?) =
        LocType(id, "Gate", ops = ops.toList() + List(5 - ops.size) { null }, collision = LocCollisionType(width = 1, length = 1))

    private val types = LocTypes(
        listOf(type(Brutus.GATE_LEFT, "Release"), type(Brutus.GATE_RIGHT, "Release"), type(Brutus.EXIT_LEFT, "Leave", "Quick-escape"), type(Brutus.EXIT_RIGHT, "Leave", "Quick-escape"))
            .associateBy { it.id },
    )

    private val brutus = NpcType(
        Brutus.BOSS, "Brutus", size = 3, ops = listOf(null, "Attack", null, null, null), combatLevel = 30, internalName = "cowboss",
        stats = NpcStats(attack = 12, defence = 10, strength = 25, hitpoints = 58, ranged = 1, magic = 8),
    )

    private fun server(dir: Path, seed: Long = 5) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply { for (x in (3240 shr 6)..(3271 shr 6)) for (z in (3256 shr 6)..(3303 shr 6)) markLoaded(x, z) }
        val npcTypes = NpcTypes.of(brutus)
        World(
            crcs, map, random = GameRandom(seed), locTypes = types,
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(Brutus.GATE_LEFT, LocShape.WALL_STRAIGHT, 1, 3262, 3294, 0))
                add(ZoneLoc(Brutus.GATE_RIGHT, LocShape.WALL_STRAIGHT, 1, 3263, 3294, 0))
            },
            npcTypes = npcTypes, npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE), varTypes = VarTypes(6_000, emptyMap()), cycleMillis = millis,
        )
    }

    private class InField(val boss: Npc, val fight: Encounter, val inside: (Int, Int) -> Tile)

    /** In through the gate (no menu: no don't-ask varbit here, so it asks), then to [x], [z] of the real field. */
    private fun HeadlessClient.goIn(server: TestServer, name: String, x: Int, z: Int): InField {
        await("the login rebuild") { position }
        commandReply("god") { it.startsWith("You can") }
        command("tele ${Brutus.OUTSIDE.x} ${Brutus.OUTSIDE.z} 0")
        await("outside the gate") { position?.takeIf { it == Coord(Brutus.OUTSIDE.x, Brutus.OUTSIDE.z, 0) } }
        locOp(Brutus.GATE_LEFT, 3262, 3294, Brutus.RELEASE)
        awaitDialogue("Release's menu") { it is DialogueView.Menu }
        chooseOption(1)
        val player = server.world.playerNamed(name)!!
        val run = await("in a field") { server.world.brutusPens.runOf(player) }
        val boss = run.boss!!
        val to = run.inside(x, z)
        command("tele ${to.x} ${to.z} 0")
        await("at $x, $z") { position?.takeIf { it == Coord(to.x, to.z, 0) } }
        val fight = await("his fight") { server.world.encounters.all.firstOrNull { it.boss === boss } }
        return InField(boss, fight) { fx, fz -> run.inside(fx, fz) }
    }

    @Test
    fun `four or five butts, then a special, then the other special, each with its overhead`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val field = client.goIn(server, "alice", 3262, 3270)
                val state = BrutusFight.fight(field.boss)
                client.await("two specials", timeoutMillis = 60_000) { state.specials.takeIf { it.size >= 2 } }
                assertNotEquals(state.specials[0], state.specials[1], "they take turns: ${state.specials}")
                val lines = field.fight.lines.toList()
                val first = lines.indexOfFirst { it.contains("charges") || it.contains("stomps") }
                val second = lines.withIndex().indexOfFirst { (i, it) -> i > first && (it.contains("charges") || it.contains("stomps")) }
                val before = lines.subList(0, first).count { it.contains("butts alice") }
                val between = lines.subList(first, second).count { it.contains("butts alice") }
                assertTrue(before in 4..5, "$before butts before the first special: $lines")
                assertTrue(between in 4..5, "$between butts between the specials: $lines")
                val said = client.npcSaid().filter { it.index == field.boss.index }.map { it.text }
                assertTrue(DialogueText.NONE[BrutusLines.GROWLS] in said && DialogueText.NONE[BrutusLines.SNORTS] in said, "*growls* and *snorts* overhead: $said")
            }
        }
    }

    @Test
    fun `his charge runs over the player and his stomp lands on their tile`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                val field = client.goIn(server, "bob", 3262, 3270)
                val bob = server.world.playerNamed("bob")!!
                val state = BrutusFight.fight(field.boss)
                client.await("both specials", timeoutMillis = 60_000) { state.specials.takeIf { it.toSet().size == 2 } }
                client.awaitTicks(BrutusFight.CHARGE_WINDOW + 3)
                val lines = field.fight.lines.toList()
                // The charge: he runs over Bob, who stands still, a hit (0 under ::god) after the window.
                val charge = lines.indexOfFirst { it.contains("charges") }
                assertTrue(lines.drop(charge + 1).take(4).any { it.contains("hit bob for") }, "the charge hits Bob: $lines")
                // The stomp: aimed at Bob's tile, landing 4 ticks on.
                val stomp = lines.indexOfFirst { it.contains("stomps") }
                val hazard = lines.subList(0, stomp).last { it.contains("hazard on") }
                assertTrue(hazard.endsWith("from tick ${BrutusFight.STOMP_TICKS} for 1"), hazard)
                val tiles = hazard.substringAfter("hazard on ").substringBefore(" from tick").split(" ")
                assertTrue(tiles.size in 4..6, hazard)
                assertTrue("${bob.x},${bob.z}" in tiles, "the stomp lands on Bob's tile: $hazard")
            }
        }
    }

    @Test
    fun `out of his reach for 15 ticks he huffs and lands beside the player`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                val field = client.goIn(server, "carol", 3262, 3270)
                val carol = server.world.playerNamed("carol")!!
                // Held where he stands, so he can't walk to her: only the *huff* reaches her.
                server.world.npcs.freeze(field.boss, 100)
                // Out of reach but in view (his overheads go only to those who see him).
                val far = Tile(field.boss.x + 1, field.boss.z - 8)
                client.command("tele ${far.x} ${far.z} 0")
                client.await("far off") { client.position?.takeIf { it == Coord(far.x, far.z, 0) } }
                client.await("the huff", timeoutMillis = 30_000) { field.fight.lines.firstOrNull { it.contains("huffs at carol") } }
                client.await("beside her", timeoutMillis = 10_000) { field.fight.lines.firstOrNull { it.contains("lands at") } }
                assertTrue(server.world.meleeCombat.reaches(field.boss, carol), "in reach once he lands")
                assertEquals(1, BrutusFight.fight(field.boss).huffs)
                val said = client.await("*huff* overhead") { client.npcSaid().firstOrNull { it.index == field.boss.index && it.text == DialogueText.NONE[BrutusLines.HUFF] } }
                assertEquals(DialogueText.NONE[BrutusLines.HUFF], said.text)
            }
        }
    }

    @Test
    fun `safespotted one tile away, he stomps the corner she stands at`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                val field = client.goIn(server, "dave", 3262, 3270)
                server.world.npcs.freeze(field.boss, 100)
                // Diagonal off his north-east corner: one tile away, out of his reach.
                val corner = Tile(field.boss.x + 3, field.boss.z + 3)
                client.command("tele ${corner.x} ${corner.z} 0")
                client.await("at his corner") { client.position?.takeIf { it == Coord(corner.x, corner.z, 0) } }
                client.await("the safespot stomp", timeoutMillis = 20_000) { field.fight.lines.firstOrNull { it.contains("safespotted one tile away") } }
                val lines = field.fight.lines.toList()
                val hazard = lines.last { it.contains("hazard on") }
                assertTrue("${corner.x},${corner.z}" in hazard, "the L round his corner holds her tile: $hazard")
                assertEquals(1, BrutusFight.fight(field.boss).safespotStomps)
                assertTrue(lines.none { it.contains("huffs") }, "a stomp, not the huff")
            }
        }
    }

    @Test
    fun `a stomp's tiles are the side the player is beside, or the corner's L`() {
        // Brutus's south-west tile on 10,10, 3 by 3: 10-12 on both axes.
        val south = BrutusFight.stompTiles(10, 10, 3, 0 to -1).toSet()
        assertEquals(setOf(Tile(10, 9), Tile(11, 9), Tile(12, 9), Tile(10, 10), Tile(11, 10), Tile(12, 10)), south)
        val east = BrutusFight.stompTiles(10, 10, 3, 1 to 0).toSet()
        assertEquals(setOf(Tile(13, 10), Tile(13, 11), Tile(13, 12), Tile(12, 10), Tile(12, 11), Tile(12, 12)), east)
        val northWest = BrutusFight.stompTiles(10, 10, 3, -1 to 1).toSet()
        assertEquals(setOf(Tile(9, 13), Tile(10, 13), Tile(9, 12), Tile(10, 12)), northWest)
    }
}
