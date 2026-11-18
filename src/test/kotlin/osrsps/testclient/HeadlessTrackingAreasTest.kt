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
import osrsps.game.skill.hunter.Tracking
import osrsps.game.skill.hunter.TrackingArea
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType

/**
 * Hunter step 8b through the headless client: a desert devil trail in the Uzer Hunter area and a razor-backed kebbit
 * trail in Piscatoris, each followed from a burrow as a player would (inspecting the connectors at each node until one
 * shows the next segment) to the hiding spot where the footprints stop, and the creature noosed there. The burrows,
 * connectors and hiding spots are where the 242 cache places them, the varbits on the cache's varps and bits. The
 * razor-backed kebbit's level 49 is needed to start a trail.
 */
class HeadlessTrackingAreasTest {
    /** One area's placed locs (type, x, z) and its segments' varbits (varbit to varp and low bit). */
    private class Trails(val area: TrackingArea, val placed: List<Triple<Int, Int, Int>>, val bits: Map<Int, Pair<Int, Int>>) {
        fun at(id: Int) = placed.first { it.first == id }
    }

    private fun server(dir: Path, trails: Trails) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val area = trails.area
        val objs = arrayOfNulls<ObjType>(11_000)
        objs[Tracking.NOOSE_WAND] = ObjType(
            Tracking.NOOSE_WAND, "Noose wand", stackable = false, tradeable = true,
            iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON,
        )
        for (id in listOf(Tracking.BONES, Tracking.RAW_BEAST_MEAT, area.creature.item)) objs[id] = ObjType(id, "Obj $id", stackable = false, tradeable = true)
        val open = LocCollisionType(interactType = 0, blocksProjectile = false)
        val inspect = listOf("Inspect", null, null, null, null)
        val types = (area.dens + area.segments.flatMap { it.connectors }).associateWith { LocType(it, "Burrow", inspect, open) } +
            area.hides.associateWith { LocType(it, "Bush", listOf("Search", "Attack", null, null, null), open) }
        val (x, z) = trails.placed.first().let { it.second to it.third }
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6); markLoaded(x shr 6, z shr 6); markLoaded(x shr 6, (z shr 6) + 1) },
            objTypes = ObjTypes(objs),
            random = GameRandom(5),
            locTypes = LocTypes(types),
            staticLocs = StaticLocs().apply { trails.placed.forEach { (id, lx, lz) -> add(ZoneLoc(id, osrsps.map.LocShape.CENTREPIECE_STRAIGHT, 0, lx, lz, 0)) } },
            varTypes = VarTypes(6_000, trails.bits.mapValues { (_, at) -> VarbitType(at.first, at.second, at.second + 2) }),
            scenarios = osrsps.game.Scenarios(listOf(osrsps.game.Scenario.parse("here", """{ "position": { "x": 3222, "z": 3218 } }"""))),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.seen(text: String, from: Int) =
        await(text, 15_000) { messages().drop(from).firstOrNull { it.text == text } }

    private fun HeadlessClient.shown(trails: Trails): Set<Int> =
        trails.bits.filter { (_, at) -> (varp(at.first) shr at.second) and 7 != 0 }.keys

    private fun HeadlessClient.wieldNoose() {
        command("item ${Tracking.NOOSE_WAND} 1")
        val slot = await("the noose wand") { inventory(BACKPACK).indexOfFirst { it?.id == Tracking.NOOSE_WAND }.takeIf { it >= 0 } }
        // iop2 (Wield) is button op 3 (see InventoryTab.click).
        ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 3, slot, Tracking.NOOSE_WAND)
        await("the noose wand worn") { inventory(WORN).getOrNull(WearPos.WEAPON)?.takeIf { it.id == Tracking.NOOSE_WAND } }
    }

    /** Inspects [id] where it is placed; the segment it showed, or null when it said there was nothing. */
    private fun HeadlessClient.inspect(trails: Trails, id: Int): Int? {
        val (_, x, z) = trails.at(id)
        val before = shown(trails)
        val from = messages().size
        locOp(id, x, z, Tracking.INSPECT)
        return await("the inspect's answer", 15_000) {
            val said = messages().drop(from).map { it.text }
            when {
                "[hunter.track_nothing]" in said -> Unit to null
                "[hunter.track_found]" in said -> (shown(trails) - before).singleOrNull()?.let { Unit to it }
                else -> null
            }
        }.second
    }

    /** From [den], follows the trail to its end as a player does; the node it ends at. */
    private fun follow(client: HeadlessClient, trails: Trails, den: Int): Int {
        val area = trails.area
        val start = area.denAt(trails.at(den).second, trails.at(den).third)!!
        val first = client.inspect(trails, den) ?: error("the burrow showed no footprints")
        val followed = arrayListOf(first)
        var node = area.segment(first).other(start)
        var nodes = 2
        while (true) {
            val untried = area.nodes[node].segments.filter { it !in followed }
            val next = untried.firstNotNullOfOrNull { client.inspect(trails, area.segment(it).connectors.first()) } ?: break
            assertTrue(next in untried, "the connector showed one of this node's segments")
            followed += next
            node = area.segment(next).other(node)
            nodes++
        }
        assertTrue(nodes >= area.minNodes, "the trail passed ${area.minNodes} nodes, not $nodes")
        return node
    }

    private fun catchAt(client: HeadlessClient, trails: Trails, den: Int, xp: IntRange) {
        val area = trails.area
        val before = client.await("Hunter XP") { client.stat(HUNTER)?.takeIf { it.level == 99 } }.xp
        val hide = area.nodes[follow(client, trails, den)].hide!!
        val from = client.messages().size
        client.locOp(area.hides.first(), hide.x, hide.z, Tracking.ATTACK)
        client.seen("[hunter.track_caught creature=${area.creature.name}]", from)
        client.await("the XP") { if (client.stat(HUNTER)!!.xp - before in xp) Unit else null }
        assertEquals(1, client.count(Tracking.BONES))
        assertEquals(1, client.count(Tracking.RAW_BEAST_MEAT))
        assertEquals(1, client.count(area.creature.item))
        client.await("the footprints gone") { if (client.shown(trails).isEmpty()) Unit else null }
    }

    @Test
    fun `a desert devil trail from a burrow ends at disturbed sand, where the noose wand catches the devil`(@TempDir dir: Path) {
        server(dir, DESERT).use { server ->
            server.login("dd").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel hunter 99")
                client.wieldNoose()
                client.command("tele 3402 3129 0")
                client.await("at Uzer") { client.position?.takeIf { it.x == 3402 } }
                catchAt(client, DESERT, 19526, 66..66)
            }
        }
    }

    @Test
    fun `a razor-backed kebbit trail needs level 49 to start, and ends with a long kebbit spike`(@TempDir dir: Path) {
        server(dir, RAZOR).use { server ->
            server.login("rb").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel hunter 48")
                client.await("level 48") { client.stat(HUNTER)?.takeIf { it.level == 48 } }
                client.command("tele 2353 3593 0")
                client.await("at Piscatoris") { client.position?.takeIf { it.x == 2353 } }
                val from = client.messages().size
                client.locOp(19579, 2353, 3595, Tracking.INSPECT)
                client.seen("[hunter.track_level level=49]", from)
                assertTrue(client.shown(RAZOR).isEmpty())
                client.command("setlevel hunter 99")
                client.wieldNoose()
                // 348.5 XP: the whole number shown goes up by 348 or 349.
                catchAt(client, RAZOR, 19579, 348..349)
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val WORN = 94
        const val HUNTER = 21

        /** The Uzer burrows, cacti, rockslides and disturbed sand where the 242 cache places them. */
        val DESERT = Trails(
            Tracking.UZER,
            listOf(
                Triple(19388, 3393, 3129), Triple(19389, 3403, 3126), Triple(19390, 3408, 3128), Triple(19391, 3396, 3121),
                Triple(19392, 3401, 3123), Triple(19393, 3405, 3122), Triple(19394, 3409, 3121), Triple(19395, 3392, 3112),
                Triple(19396, 3402, 3116), Triple(19397, 3406, 3118), Triple(19398, 3411, 3114), Triple(19399, 3399, 3111),
                Triple(19400, 3403, 3110), Triple(19401, 3406, 3111), Triple(19402, 3402, 3101), Triple(19430, 3393, 3122),
                Triple(19430, 3400, 3114), Triple(19430, 3405, 3124), Triple(19430, 3407, 3121), Triple(19430, 3411, 3108),
                Triple(19430, 3414, 3121), Triple(19526, 3402, 3131), Triple(19552, 3396, 3106),
            ),
            mapOf(
                3016 to (922 to 26), 3017 to (922 to 29), 3018 to (923 to 0), 3019 to (923 to 3), 3020 to (923 to 6),
                3021 to (923 to 9), 3022 to (923 to 12), 3023 to (923 to 15), 3024 to (923 to 18), 3025 to (923 to 21),
                3026 to (923 to 24), 3027 to (923 to 27), 3030 to (924 to 0), 3031 to (924 to 3), 3032 to (924 to 6),
            ),
        )

        /** The razor-backed kebbits' burrows, plants and bushes in Piscatoris, where the 242 cache places them. */
        val RAZOR = Trails(
            Tracking.PISCATORIS_RAZOR,
            listOf(
                Triple(19356, 2362, 3598), Triple(19357, 2355, 3598), Triple(19358, 2347, 3603), Triple(19359, 2358, 3599),
                Triple(19360, 2352, 3603), Triple(19361, 2358, 3603), Triple(19362, 2362, 3610), Triple(19362, 2363, 3602),
                Triple(19363, 2358, 3607), Triple(19363, 2359, 3613), Triple(19364, 2355, 3608), Triple(19365, 2351, 3608),
                Triple(19372, 2363, 3617), Triple(19373, 2349, 3620), Triple(19374, 2356, 3620), Triple(19375, 2344, 3612),
                Triple(19375, 2347, 3607), Triple(19376, 2348, 3612), Triple(19376, 2352, 3612), Triple(19377, 2345, 3614),
                Triple(19377, 2349, 3617), Triple(19378, 2352, 3618), Triple(19378, 2356, 3618), Triple(19379, 2362, 3614),
                Triple(19380, 2360, 3618), Triple(19428, 2349, 3604), Triple(19428, 2351, 3619), Triple(19428, 2354, 3609),
                Triple(19428, 2355, 3601), Triple(19428, 2357, 3607), Triple(19428, 2358, 3620), Triple(19428, 2360, 3602),
                Triple(19428, 2362, 3615), Triple(19438, 2357, 3624), Triple(19579, 2353, 3595), Triple(19580, 2360, 3611),
            ),
            mapOf(
                2974 to (919 to 0), 2975 to (919 to 3), 2976 to (919 to 6), 2977 to (919 to 9), 2978 to (919 to 12),
                2979 to (919 to 15), 2980 to (919 to 18), 2981 to (919 to 21), 2982 to (919 to 24), 2983 to (919 to 27),
                2985 to (920 to 0), 2986 to (920 to 3), 2987 to (920 to 6), 2988 to (920 to 9), 2989 to (920 to 12),
                2990 to (920 to 15), 2991 to (920 to 18), 2992 to (920 to 21), 2993 to (920 to 24),
            ),
        )
    }
}
