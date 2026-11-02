package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import osrsps.cache.LocTypeDecoder
import osrsps.game.World
import osrsps.game.loc.LocTypes
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.map.CollisionMap

class LodestonesTest {
    private val map = CollisionMap().apply { markLoaded(Lodestones.LUMBRIDGE.x shr 6, Lodestones.LUMBRIDGE.z shr 6) }

    private fun world() = World(IntArray(0), map, locTypes = LocTypes.NONE.with(CustomLocs.types))

    @Test
    fun `the custom loc types are what the client gets, decoded`() {
        for (custom in CustomLocs.ALL) {
            val type = LocTypeDecoder.decode(custom.id, custom.encoded)
            assertEquals(type, custom.type)
            assertTrue(custom.id >= CustomLocs.FIRST_ID)
        }
        assertEquals(CustomLocs.ALL.map { it.id }, CustomLocs.overlayFiles.map { it.file })
        assertTrue(CustomLocs.overlayFiles.all { it.index == 2 && it.group == 6 })
        assertContentEquals(Lodestones.DORMANT_TYPE.encoded, CustomLocs.overlayFiles.first { it.file == Lodestones.DORMANT }.data)
    }

    @Test
    fun `the lodestone types are flat 3 by 3 stones with Activate or Teleport and our models`() {
        val dormant = Lodestones.DORMANT_TYPE.type
        val active = Lodestones.ACTIVE_TYPE.type
        for (type in listOf(dormant, active)) {
            assertEquals("Lodestone", type.name)
            assertEquals(3, type.width)
            assertEquals(3, type.length)
            assertEquals(0, type.collision.interactType, "players walk over the stone")
        }
        assertEquals(listOf("Activate", null, null, null, null), dormant.ops)
        assertEquals(listOf("Teleport", null, null, null, null), active.ops)
        assertEquals(listOf(Lodestones.DORMANT_MODEL), Lodestones.DORMANT_TYPE.definition.models)
        assertEquals(listOf(Lodestones.ACTIVE_MODEL), Lodestones.ACTIVE_TYPE.definition.models)
        assertEquals(1, dormant.op("Activate"))
        assertEquals(1, active.op("Teleport"))
    }

    @Test
    fun `a world without the lodestone types has no stone, so other tests' loc changes stay their own`() {
        val world = World(IntArray(0), CollisionMap().apply { markLoaded(Lodestones.LUMBRIDGE.x shr 6, Lodestones.LUMBRIDGE.z shr 6) })
        assertEquals(0, world.locs.changed)
        assertTrue(world.locs.at(Lodestones.LUMBRIDGE.x, Lodestones.LUMBRIDGE.z, 0).isEmpty())
    }

    @Test
    fun `the world places the dormant Lumbridge stone and answers Activate and Examine`() {
        val world = world()
        val stone = Lodestones.LUMBRIDGE
        assertEquals(listOf(stone), world.locs.at(stone.x, stone.z, stone.level))
        assertEquals(1, world.locs.changed, "a change on top of the cache's map, sent to whoever comes by")
        assertNotNull(world.handlers.find(Op(OpTarget.LOC, Lodestones.DORMANT, Lodestones.ACTIVATE)), "Activate")
        assertEquals("examine.loc_65000", Examines.loc(Lodestones.DORMANT)?.key)
        assertEquals("examine.loc_65000", Examines.loc(Lodestones.ACTIVE)?.key, "the active stone shares the text")
        // Walking over the stone: nothing blocks on its tiles.
        for (x in stone.x until stone.x + 3) for (z in stone.z until stone.z + 3) {
            assertEquals(0, map[x, z, 0], "tile $x, $z")
        }
    }
}
