package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.hunter.TrackingArea
import osrsps.game.skill.hunter.Tracking
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Hunter step 8 through the headless client, on the Rellekka Hunter area's polar kebbit trails as the cache places
 * them: Inspect on a hole shows a first segment's footprints (the player's own varbits); the test follows the trail as a
 * player would, inspecting the connectors at each node until one shows the next segment, and nooses the kebbit in the
 * snow drift where the footprints stop. Then a connector that isn't the next one, a hiding spot with no trail, and
 * Attack without a noose wand.
 */
class HeadlessTrackingTest {
    private val area = Tracking.POLAR

    /** The polar segments' varbits, 3 bits each on varp 926 (hunting_trail_basevar8), slot N at bit 3N. */
    private val vars = VarTypes(6_000, area.segments.associate { it.varbit to VarbitType(TRAIL_VARP, slot(it.varbit) * 3, slot(it.varbit) * 3 + 2) })

    private fun slot(varbit: Int) = varbit - area.segments.first().varbit

    private fun server(dir: Path, seed: Long = 7) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(11_000)
        objs[Tracking.NOOSE_WAND] = ObjType(
            Tracking.NOOSE_WAND, "Noose wand", stackable = false, tradeable = true,
            iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON,
        )
        for (id in listOf(Tracking.BONES, Tracking.RAW_BEAST_MEAT, Tracking.POLAR_KEBBIT_FUR)) objs[id] = ObjType(id, "Obj $id", stackable = false, tradeable = true)
        val open = LocCollisionType(interactType = 0, blocksProjectile = false)
        val types = (area.dens.map { it to "Hole" } + area.hides.map { it to "Snow drift" }).associate { (id, name) ->
            id to LocType(id, name, if (id in area.hides) listOf("Search", "Attack", null, null, null) else listOf("Inspect", null, null, null, null), open)
        } + area.segments.flatMap { it.connectors }.associateWith { LocType(it, "Tunnel", listOf("Inspect", null, null, null, null), open) }
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6); markLoaded(2712 shr 6, 3824 shr 6) },
            objTypes = ObjTypes(objs),
            random = GameRandom(seed),
            locTypes = LocTypes(types),
            staticLocs = StaticLocs().apply { PLACED.forEach { (id, x, z) -> add(ZoneLoc(id, osrsps.map.LocShape.CENTREPIECE_STRAIGHT, 0, x, z, 1)) } },
            varTypes = vars,
            scenarios = osrsps.game.Scenarios(listOf(osrsps.game.Scenario.parse("here", """{ "position": { "x": 3222, "z": 3218 } }"""))),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.seen(text: String, from: Int, timeoutMillis: Long = 10_000) =
        await(text, timeoutMillis) { messages().drop(from).firstOrNull { it.text == text } }

    /** The segments whose footprints show, by varbit. */
    private fun HeadlessClient.shown(): Set<Int> =
        area.segments.filter { (varp(TRAIL_VARP) shr (slot(it.varbit) * 3)) and 7 != 0 }.map { it.varbit }.toSet()

    private fun HeadlessClient.wieldNoose() {
        command("item ${Tracking.NOOSE_WAND} 1")
        val slot = await("the noose wand") { inventory(BACKPACK).indexOfFirst { it?.id == Tracking.NOOSE_WAND }.takeIf { it >= 0 } }
        // iop2 (Wield) is button op 3 (see InventoryTab.click).
        ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 3, slot, Tracking.NOOSE_WAND)
        await("the noose wand worn") { inventory(WORN).getOrNull(WearPos.WEAPON)?.takeIf { it.id == Tracking.NOOSE_WAND } }
    }

    /**
     * Inspects [connector] at its placed tile; returns the segment it showed, or null when it said there was nothing.
     */
    private fun HeadlessClient.inspect(connector: Int): Int? {
        val (id, x, z) = PLACED.first { it.first == connector }
        val before = shown()
        val from = messages().size
        locOp(id, x, z, Tracking.INSPECT)
        return await("the inspect's answer", 15_000) {
            val said = messages().drop(from).map { it.text }
            when {
                "[hunter.track_nothing]" in said -> Unit to null
                "[hunter.track_found]" in said -> (shown() - before).singleOrNull()?.let { Unit to it }
                else -> null
            }
        }.second
    }

    private fun startAt(client: HeadlessClient, den: Int): Int {
        val node = area.denAt(PLACED.first { it.first == den }.second, PLACED.first { it.first == den }.third)!!
        val (id, x, z) = PLACED.first { it.first == den }
        val from = client.messages().size
        client.locOp(id, x, z, Tracking.INSPECT)
        client.seen("[hunter.track_found]", from, 15_000)
        return node
    }

    /** Follows the trail from [start] as a player does, and returns the node it ends at, and the segments followed. */
    private fun follow(client: HeadlessClient, start: Int): Pair<Int, List<Int>> {
        var node = start
        val followed = ArrayList<Int>()
        followed += client.await("the first footprints") { client.shown().singleOrNull() }
        node = area.segment(followed.last()).other(node)
        while (true) {
            val untried = area.nodes[node].segments.filter { it !in followed }
            val next = untried.firstNotNullOfOrNull { segment -> client.inspect(area.segment(segment).connectors.first()) } ?: break
            assertTrue(next in untried, "the connector showed its own segment")
            followed += next
            node = area.segment(next).other(node)
        }
        return node to followed
    }

    @Test
    fun `a polar kebbit trail followed from a hole ends at a snow drift, where the noose wand catches the kebbit`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("pk").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel hunter 99")
                client.wieldNoose()
                val before = client.await("Hunter XP") { client.stat(HUNTER)?.takeIf { it.level == 99 } }.xp
                client.command("tele 2717 3817 1")
                client.await("at Rellekka") { client.position?.takeIf { it.x == 2717 } }
                val start = startAt(client, HOLE_SOUTH)
                val (end, followed) = follow(client, start)
                val nodes = listOf(start) + followed.runningFold(start) { node, s -> area.segment(s).other(node) }.drop(1)
                assertTrue(nodes.count { !area.nodes[it].hub } >= area.minNodes, "the trail passed ${area.minNodes} nodes: $nodes")
                assertEquals(followed.size, followed.toSet().size, "no segment twice")
                val hide = area.nodes[end].hide!!
                val from = client.messages().size
                client.locOp(HIDE, hide.x, hide.z, Tracking.ATTACK)
                client.seen("[hunter.track_caught creature=polar kebbit]", from, 15_000)
                client.await("30 XP") { if (client.stat(HUNTER)!!.xp - before == 30) Unit else null }
                assertEquals(1, client.count(Tracking.BONES))
                assertEquals(1, client.count(Tracking.RAW_BEAST_MEAT))
                assertEquals(1, client.count(Tracking.POLAR_KEBBIT_FUR))
                client.await("the footprints gone") { if (client.shown().isEmpty()) Unit else null }
            }
        }
    }

    @Test
    fun `a wrong connector shows nothing, a hiding spot with no trail is empty, and no noose wand scares the kebbit off`(@TempDir dir: Path) {
        server(dir, seed = 11).use { server ->
            server.login("pn").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele 2711 3828 1")
                client.await("at Rellekka") { client.position?.takeIf { it.x == 2711 } }
                var from = client.messages().size
                client.locOp(HIDE, 2716, 3827, Tracking.ATTACK)
                client.seen("[hunter.track_empty]", from, 15_000)
                val start = startAt(client, HOLE_NORTH)
                val first = client.await("the first footprints") { client.shown().singleOrNull() }
                // A connector of a segment that doesn't meet the node the trail leads to.
                val at = area.segment(first).other(start)
                val wrong = area.segments.first { at != it.from && at != it.to }
                assertEquals(null, client.inspect(wrong.connectors.first()))
                val (end, _) = follow(client, start)
                val hide = area.nodes[end].hide!!
                from = client.messages().size
                client.locOp(HIDE, hide.x, hide.z, Tracking.ATTACK)
                client.seen("[hunter.track_no_noose creature=polar kebbit]", from, 15_000)
                client.await("the footprints gone") { if (client.shown().isEmpty()) Unit else null }
                assertEquals(0, client.count(Tracking.POLAR_KEBBIT_FUR))
                // The trail is over: the same drift is empty now.
                from = client.messages().size
                client.locOp(HIDE, hide.x, hide.z, Tracking.ATTACK)
                client.seen("[hunter.track_empty]", from, 15_000)
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val WORN = 94
        const val HUNTER = 21
        const val TRAIL_VARP = 926
        const val HIDE = 19435
        const val HOLE_NORTH = 19640
        const val HOLE_SOUTH = 19641

        /** The polar area's dens, connectors and snow drifts where the 242 cache places them, all on level 1. */
        val PLACED = listOf(
            Triple(19418, 2712, 3815), Triple(19419, 2711, 3819), Triple(19420, 2715, 3820), Triple(19421, 2721, 3827),
            Triple(19422, 2708, 3825), Triple(19423, 2714, 3821), Triple(19424, 2718, 3829), Triple(19425, 2713, 3827),
            Triple(19426, 2718, 3832), Triple(19435, 2708, 3819), Triple(19435, 2712, 3831), Triple(19435, 2716, 3827),
            Triple(19435, 2718, 3820), Triple(19640, 2711, 3830), Triple(19641, 2717, 3819),
        )
    }
}

/** Polar trails from both holes, drawn many times: each ends at a hiding spot past the minimum, no segment twice. */
class TrackingPathTest {
    @Test
    fun `every drawn trail is a walk of unused segments that ends at a hiding spot at or past the minimum`() {
        for (area in Tracking.AREAS) checkPaths(area)
    }

    private fun checkPaths(area: TrackingArea) {
        val random = GameRandom(3)
        for ((start, node) in area.nodes.withIndex()) {
            if (node.den == null) continue
            repeat(500) {
                val steps = Tracking.path(area, start, random) ?: error("${area.creature.name}: no trail from node $start")
                var at = start
                var count = 1
                for (step in steps) {
                    assertTrue(step.segment.varbit in area.nodes[at].segments)
                    assertEquals(step.forward, step.segment.from == at)
                    at = step.segment.other(at)
                    if (!area.nodes[at].hub) count++
                }
                assertEquals(steps.size, steps.map { it.segment.varbit }.toSet().size)
                assertTrue(count >= area.minNodes)
                assertTrue(area.nodes[at].hide != null)
            }
        }
    }
}
