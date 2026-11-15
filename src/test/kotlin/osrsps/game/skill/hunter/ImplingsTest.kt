package osrsps.game.skill.hunter

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.combat.NpcDrops
import osrsps.map.CollisionMap

/**
 * Hunter step 6b's data against the Wiki (levels, XP, tiers, points, the loot file) and its spawning: a cycle's
 * spawners by tier, unseen for two minutes, then implings at their points, all gone and rolled again 30 minutes on.
 */
class ImplingsTest {
    private val text = Implings::class.java.getResource(Implings.RESOURCE)!!.readText()

    /** Stand-ins for every item the loot file names, named as the file names them. */
    private fun objTypes(): ObjTypes {
        val items = HashMap<Int, ObjType>()
        fun collect(obj: JsonObject) {
            obj["item"]?.let { id -> items.getOrPut(id.jsonPrimitive.int) { ObjType(id.jsonPrimitive.int, obj["name"]!!.jsonPrimitive.content, stackable = false, tradeable = true) } }
            for ((_, value) in obj) {
                runCatching { value.jsonObject }.getOrNull()?.let(::collect)
                runCatching { value.jsonArray }.getOrNull()?.forEach { runCatching { it.jsonObject }.getOrNull()?.let(::collect) }
            }
        }
        collect(Json.parseToJsonElement(text).jsonObject)
        return ObjTypes.of(*items.values.toTypedArray())
    }

    private val types = Implings.ALL.flatMap { impling -> impling.npcs.map { NpcType(it, "Impling $it", ops = listOf("Catch", null, null, null, null)) } }

    @Test
    fun `levels, XP and jars are the impling pages'`() {
        assertEquals(
            listOf(17 to 200, 22 to 220, 28 to 240, 36 to 270, 42 to 290, 50 to 320, 58 to 360, 65 to 2160, 74 to 2400, 83 to 3000, 80 to 2800),
            Implings.ALL.map { it.level to it.xpTenths },
        )
        assertEquals((11238..11256 step 2).toList() + 23768, Implings.ALL.map { it.jarred })
        assertEquals(17, Implings.CRYSTAL.npcs.size)
    }

    @Test
    fun `the tiers and points are the Impling page's, the lucky impling left out`() {
        assertEquals(listOf(100, 100, 100), listOf(Implings.LOW, Implings.MID, Implings.HIGH).map { it.total })
        assertEquals(listOf(49, 11, 12), listOf(Implings.VARIABLE_POINTS, Implings.LOW_POINTS, Implings.PRIF_POINTS).map { it.size })
        assertEquals(Implings.VARIABLE_POINTS.size, Implings.VARIABLE_POINTS.toSet().size)
    }

    @Test
    fun `the loot file reads, every impling has a table, and only the gourmet has a pre-roll and the crystal a tertiary`() {
        val drops = NpcDrops.parse(text, NpcTypes.NONE, objTypes())
        assertEquals(emptyList(), drops.warnings)
        for (impling in Implings.ALL) assertTrue(drops.chests[impling.loot]?.table != null, impling.loot)
        assertEquals(listOf("gourmet"), Implings.ALL.filter { drops.chests[it.loot]!!.preroll.isNotEmpty() }.map { it.loot })
        assertEquals(listOf("crystal"), Implings.ALL.filter { drops.chests[it.loot]!!.tertiary.isNotEmpty() }.map { it.loot })
        assertEquals(500, drops.chests["gourmet"]!!.preroll.single().denominator)
        assertEquals(128, drops.chests["crystal"]!!.tertiary.single().denominator)
        // A thousand dragon jars always give something, each from the table.
        val random = GameRandom(3)
        repeat(1000) { assertTrue(drops.chests["dragon"]!!.roll(random).isNotEmpty()) }
    }

    @Test
    fun `a cycle rolls 4 high, N mid and 2N low spawners plus the fixed ones, shown two minutes on and replaced at 30`() {
        val world = World(IntArray(0), CollisionMap(), random = GameRandom(11), npcTypes = NpcTypes.of(*types.toTypedArray()), objTypes = objTypes())
        val implings = world.hunter.implings
        implings.process(0)
        val first = implings.all.toList()
        val variable = first.count { it.tier !== Implings.CRYSTAL_TIER } - Implings.LOW_POINTS.size
        val n = (variable - Implings.HIGH_ROLLS) / 3
        assertTrue(n in Implings.MID_MIN..Implings.MID_MAX, "N = $n")
        assertEquals(Implings.HIGH_ROLLS, first.count { it.tier === Implings.HIGH })
        assertEquals(n, first.count { it.tier === Implings.MID })
        assertEquals(2 * n + Implings.LOW_POINTS.size, first.count { it.tier === Implings.LOW })
        assertEquals(1, first.count { it.tier === Implings.CRYSTAL_TIER && (it.x to it.z) in Implings.PRIF_POINTS })
        assertTrue(first.filter { it.tier === Implings.HIGH || it.tier === Implings.MID }.all { (it.x to it.z) in Implings.VARIABLE_POINTS })
        assertTrue(first.all { it.npc == null })
        assertEquals(0, world.npcs.count)
        implings.process(Implings.UNSEEN_TICKS.toLong() - 1)
        assertEquals(0, world.npcs.count)
        implings.process(Implings.UNSEEN_TICKS.toLong())
        assertEquals(first.size, world.npcs.count)
        for (spawner in first) {
            val npc = spawner.npc!!
            // Unloaded map here, so the unseen roam can't move it (the roam has its own test).
            assertEquals(spawner.x to spawner.z, npc.x to npc.z)
            assertTrue(spawner.tier.lines.any { npc.type.id in it.first.npcs }, "${npc.type.id} from ${spawner.tier.name}")
        }
        // Thirty minutes on, the old ones go and a new set waits unseen.
        implings.process(Implings.CYCLE_TICKS.toLong())
        assertEquals(0, world.npcs.count)
        assertTrue(implings.all.isNotEmpty() && implings.all.none { it in first })
    }

    @Test
    fun `an impling roams while unseen, so it shows away from its point but inside its roam`() {
        // Open ground around every point (an unloaded map blocks every step, as in the test above).
        val collision = CollisionMap()
        val points = Implings.VARIABLE_POINTS + Implings.LOW_POINTS + Implings.PRIF_POINTS
        for ((x, z) in points) for (rx in (x - Implings.ROAM shr 6)..(x + Implings.ROAM shr 6)) for (rz in (z - Implings.ROAM shr 6)..(z + Implings.ROAM shr 6)) collision.markLoaded(rx, rz)
        val world = World(IntArray(0), collision, random = GameRandom(11), npcTypes = NpcTypes.of(*types.toTypedArray()), objTypes = objTypes())
        val implings = world.hunter.implings
        implings.process(0)
        implings.process(Implings.UNSEEN_TICKS.toLong())
        val shown = implings.all.map { it to it.npc!! }
        assertTrue(shown.count { (spawner, npc) -> spawner.x to spawner.z != npc.x to npc.z } > shown.size / 2, "most moved")
        for ((spawner, npc) in shown) {
            assertTrue(abs(npc.x - spawner.x) <= Implings.ROAM && abs(npc.z - spawner.z) <= Implings.ROAM, "${npc.x}, ${npc.z} from ${spawner.x}, ${spawner.z}")
            // Still roaming around the point, not around where it showed.
            assertEquals(spawner.x to spawner.z, npc.spawn.x to npc.spawn.z)
        }
    }

    @Test
    fun `nothing is rolled in a world whose cache has no implings`() {
        val world = World(IntArray(0), CollisionMap(), random = GameRandom(11))
        world.hunter.implings.process(0)
        assertTrue(world.hunter.implings.all.isEmpty())
    }
}
