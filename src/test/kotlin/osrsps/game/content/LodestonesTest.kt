package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import osrsps.cache.LocTypeDecoder
import osrsps.game.World
import osrsps.game.loc.LocTypes
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.map.CollisionMap

class LodestonesTest {
    private val map = CollisionMap().apply { for (site in Lodestones.SITES) markLoaded(site.x shr 6, site.z shr 6) }

    private fun world() = World(IntArray(0), map, locTypes = LocTypes.NONE.with(CustomLocs.types))

    @Test
    fun `the custom loc types are what the client gets, decoded`() {
        for (custom in CustomLocs.ALL) {
            val type = LocTypeDecoder.decode(custom.id, custom.encoded)
            assertEquals(type, custom.type)
            assertTrue(custom.id >= CustomLocs.FIRST_ID)
        }
        assertEquals(CustomLocs.ALL.map { it.id }, CustomLocs.overlayFiles.map { it.file })
        assertEquals(listOf(65000, 65001, 65002, 65003, 65004, 65005, 65006), CustomLocs.ALL.map { it.id })
        assertTrue(CustomLocs.overlayFiles.all { it.index == 2 && it.group == 6 })
        assertContentEquals(Lodestones.DORMANT_TYPE.encoded, CustomLocs.overlayFiles.first { it.file == Lodestones.DORMANT }.data)
    }

    @Test
    fun `the two looks are flat 3 by 3 stones with Activate or Teleport and our models`() {
        val dormant = Lodestones.DORMANT_TYPE.type
        val active = Lodestones.ACTIVE_TYPE.type
        for (type in listOf(dormant, active)) {
            assertEquals("Lodestone", type.name)
            assertEquals(3, type.width)
            assertEquals(3, type.length)
            assertEquals(0, type.collision.interactType, "players walk over the stone")
            assertFalse(type.multiloc)
        }
        assertEquals(listOf("Activate", null, null, null, null), dormant.ops)
        assertEquals(listOf("Teleport", null, null, null, null), active.ops)
        assertEquals(listOf(Lodestones.DORMANT_MODEL), Lodestones.DORMANT_TYPE.definition.models)
        assertEquals(listOf(Lodestones.ACTIVE_MODEL), Lodestones.ACTIVE_TYPE.definition.models)
        assertEquals(1, dormant.op("Activate"))
        assertEquals(1, active.op("Teleport"))
    }

    @Test
    fun `each site's stone is a multiloc that its varbit turns from the dormant look to the active one`() {
        for (site in Lodestones.SITES) {
            val transform = site.type.definition.transform
            assertNotNull(transform, site.slug)
            assertEquals(site.varbit, transform.varbit)
            assertEquals(listOf(Lodestones.DORMANT, Lodestones.ACTIVE), transform.children, "0 dormant, 1 active")
            assertTrue(site.type.type.multiloc)
            assertEquals("Lodestone", site.type.type.name)
            assertEquals(3, site.type.type.width)
            assertEquals(0, site.type.type.collision.interactType)
            assertEquals(site.id, site.loc.id)
            assertEquals(Destination(site.x + 1, site.z + 1, 0), site.arrival, "lands on the centre tile")
            assertEquals(CustomVars.FIRST_VARBIT + site.index, site.varbit)
        }
        assertEquals(listOf("lumbridge", "draynor", "varrock", "al_kharid", "edgeville"), Lodestones.SITES.map { it.slug })
        assertEquals(listOf(65002, 65003, 65004, 65005, 65006), Lodestones.SITES.map { it.id })
    }

    @Test
    fun `a world without the lodestone types has no stones, so other tests' loc changes stay their own`() {
        val world = World(IntArray(0), map)
        assertEquals(0, world.locs.changed)
        for (site in Lodestones.SITES) assertTrue(world.locs.at(site.x, site.z, 0).isEmpty(), site.slug)
    }

    @Test
    fun `the world places every stone and answers its option and Examine`() {
        val world = world()
        for (site in Lodestones.SITES) {
            assertEquals(listOf(site.loc), world.locs.at(site.x, site.z, 0), site.slug)
            assertNotNull(world.handlers.find(Op(OpTarget.LOC, site.id, Lodestones.ACTIVATE)), site.slug)
            assertEquals("examine.loc_65000", Examines.loc(site.id)?.key, "${site.slug} shares the examine")
            // Walking over the stone: nothing blocks on its tiles.
            for (x in site.x until site.x + 3) for (z in site.z until site.z + 3) assertEquals(0, map[x, z, 0], "tile $x, $z")
        }
        assertEquals(Lodestones.SITES.size, world.locs.changed, "one change per stone, sent to whoever comes by")
        assertEquals("examine.loc_65000", Examines.loc(Lodestones.DORMANT)?.key)
        assertEquals("examine.loc_65000", Examines.loc(Lodestones.ACTIVE)?.key, "the looks share the text")
    }

    @Test
    fun `the world's var types hold our varbits, so a player's lodestone bits can be set`() {
        val world = World(IntArray(0), CollisionMap())
        for (site in Lodestones.SITES) assertEquals(CustomVars.varbits[site.varbit], world.varTypes.varbit(site.varbit))
        assertTrue(world.varTypes.varpCount > CustomVars.LODESTONES_VARP)
    }
}
