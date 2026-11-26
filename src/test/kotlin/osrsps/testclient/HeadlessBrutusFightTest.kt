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
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.combat.Prayers
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
import osrsps.map.CollisionFlag
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

    private var map: CollisionMap? = null

    private fun server(dir: Path, seed: Long = 5) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply { for (x in (3240 shr 6)..(3271 shr 6)) for (z in (3256 shr 6)..(3303 shr 6)) markLoaded(x, z) }
        this.map = map
        val npcTypes = NpcTypes.of(brutus)
        val objs = arrayOfNulls<ObjType>(protectFromMelee.id + 1)
        objs[protectFromMelee.id] = protectFromMelee
        val objTypes = ObjTypes(objs)
        World(
            crcs, map, random = GameRandom(seed), locTypes = types, objTypes = objTypes, prayerList = Prayers.load(objTypes),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(Brutus.GATE_LEFT, LocShape.WALL_STRAIGHT, 1, 3262, 3294, 0))
                add(ZoneLoc(Brutus.GATE_RIGHT, LocShape.WALL_STRAIGHT, 1, 3263, 3294, 0))
            },
            npcTypes = npcTypes, npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE), varTypes = VarTypes(6_000, emptyMap()), cycleMillis = millis,
        )
    }

    // Shaped like the cache's prayer obj for Protect from Melee, as HeadlessPvpMeleeTest has it.
    private val protectFromMelee = ObjType(
        20817, "Null", internalName = "placeholder_xmas16_marionette_blue",
        params = mapOf(630 to 14, 1751 to (Prayers.PRAYERBOOK shl 16 or PROTECT_FROM_MELEE), 1752 to "Protect from Melee", 1753 to 43, 1755 to 2676),
    )

    private class InField(val boss: Npc, val fight: Encounter, val inside: (Int, Int) -> Tile)

    /** In through the gate (no menu: no don't-ask varbit here, so it asks), then to [x], [z] of the real field. */
    private fun HeadlessClient.goIn(server: TestServer, name: String, x: Int, z: Int, god: Boolean = true): InField {
        await("the login rebuild") { position }
        if (god) commandReply("god on") { it.startsWith("You can") }
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

    /** The damage of every special that has landed on [name] so far: the charge's hits and the stomps'. */
    private fun specialHits(field: InField, name: String): Pair<List<Int>, List<Int>> {
        val lines = field.fight.lines.toList()
        fun dealt(prefix: String) = lines.filter { it.contains("$prefix $name for ") && (prefix != "hit" || !it.contains("hazard hit")) }
            .map { it.substringAfterLast(" for ").trim().toInt() }
        return dealt("hit") to dealt("hazard hit")
    }

    @Test
    fun `without god mode his charge and stomps land 15 to 19 through Protect from Melee`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel hitpoints 99") { true }
                client.commandReply("setlevel prayer 99") { true }
                client.await("hitpoints at 99") { Unit.takeIf { client.stat(HITPOINTS)?.level == 99 } }
                client.ifButton(Prayers.PRAYERBOOK, PROTECT_FROM_MELEE, 1)
                client.await("Protect from Melee on") { Unit.takeIf { client.varp(Prayers.VARP_PRAYERS) == 1 shl 14 } }
                val erin = server.world.playerNamed("erin")!!
                val field = client.goIn(server, "erin", 3262, 3270, god = false)
                assertTrue(!erin.god)
                val range = BrutusFight.specialDamage(erin)
                assertEquals(BrutusFight.SPECIAL_MIN..BrutusFight.SPECIAL_MAX, range)
                val hits = client.await("a charge hit and a stomp hit", timeoutMillis = 90_000) {
                    specialHits(field, "erin").takeIf { (charge, stomp) -> charge.isNotEmpty() && stomp.isNotEmpty() }
                }
                assertTrue(server.world.prayers.protects(erin, osrsps.game.combat.AttackType.CRUSH), "still praying")
                for (dealt in hits.first + hits.second) assertTrue(dealt in range, "$dealt in $range through the prayer: ${field.fight.lines}")
                assertTrue(erin.hitpoints < 99, "they hurt")
            }
        }
    }

    @Test
    fun `without god mode a low Hitpoints player takes less from a special`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("finn").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel hitpoints 30") { true }
                client.await("hitpoints at 30") { Unit.takeIf { client.stat(HITPOINTS)?.level == 30 } }
                val finn = server.world.playerNamed("finn")!!
                val field = client.goIn(server, "finn", 3262, 3270, god = false)
                val range = BrutusFight.specialDamage(finn)
                // 30% to 40% of 30.
                assertEquals(9..12, range)
                val hits = client.await("the first special's hit", timeoutMillis = 60_000) {
                    specialHits(field, "finn").let { it.first + it.second }.takeIf { it.isNotEmpty() }
                }
                assertTrue(hits.first() in range, "${hits.first()} in $range: ${field.fight.lines}")
            }
        }
    }

    @Test
    fun `ticks out of reach during a special count toward the huff`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("gwen").use { client ->
                val field = client.goIn(server, "gwen", 3262, 3270)
                val state = BrutusFight.fight(field.boss)
                // Wait for his first special to start, then run off and hold him where he stands.
                client.await("a special", timeoutMillis = 60_000) { state.specials.firstOrNull() }
                val busyFor = (state.busyUntil - server.world.tick).toInt()
                val huffs = state.huffs
                server.world.npcs.freeze(field.boss, 100)
                val far = Tile(field.boss.x + 1, field.boss.z - 8)
                val started = server.world.tick
                client.command("tele ${far.x} ${far.z} 0")
                client.await("the huff", timeoutMillis = 30_000) { Unit.takeIf { state.huffs > huffs } }
                val took = server.world.tick - started
                assertTrue(busyFor >= 3, "the special keeps him busy a while: $busyFor")
                // Counted from the tick she ran, the special's ticks included, not from the special's end.
                assertTrue(took <= BrutusFight.OUT_OF_REACH_TICKS + 2, "the huff $took ticks after she ran, $busyFor of them in the special: ${field.fight.lines}")
            }
        }
    }

    @Test
    fun `hemmed in where he has no room to land, he lands near and stomps her from there`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("hana").use { client ->
                val field = client.goIn(server, "hana", 3262, 3270)
                val hana = server.world.playerNamed("hana")!!
                server.world.npcs.freeze(field.boss, 100)
                // Fenced in on all eight sides, 8 tiles south of him: no tile for his 3 by 3 body beside her.
                val spot = Tile(field.boss.x + 1, field.boss.z - 8)
                client.command("tele ${spot.x} ${spot.z} 0")
                client.await("hemmed in") { client.position?.takeIf { it == Coord(spot.x, spot.z, 0) } }
                for (dx in -1..1) for (dz in -1..1) if (dx != 0 || dz != 0) map!!.add(spot.x + dx, spot.z + dz, 0, CollisionFlag.LOC)
                val state = BrutusFight.fight(field.boss)
                val huffs = state.huffs
                client.await("the huff", timeoutMillis = 30_000) { Unit.takeIf { state.huffs > huffs } }
                client.await("the stomp from afar", timeoutMillis = 10_000) { field.fight.lines.firstOrNull { it.contains("stomps hana from afar") } }
                val lines = field.fight.lines.toList()
                assertTrue(lines.any { it.contains("lands at") }, "he lands as near as he can: $lines")
                val stomp = lines.indexOfFirst { it.contains("stomps hana from afar") }
                assertTrue(lines.subList(0, stomp).last().contains("hit hana for"), "the stomp hits her where she stands: $lines")
                assertEquals(1, state.farStomps)
                val gap = maxOf(field.boss.x - hana.x, hana.x - (field.boss.x + 2), field.boss.z - hana.z, hana.z - (field.boss.z + 2)) - 1
                assertTrue(gap in 1..BrutusFight.NEAR_TILES, "landed $gap tiles off her")
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

    private companion object {
        const val PROTECT_FROM_MELEE = 23
        const val HITPOINTS = 3
    }
}
