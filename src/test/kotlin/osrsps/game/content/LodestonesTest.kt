package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import osrsps.cache.LocTypeDecoder
import osrsps.cache.LocTypeEncoder
import osrsps.cache.MapElementCodec
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.loc.LocTypes
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.map.CollisionMap

class LodestonesTest {
    private val map = CollisionMap().apply { for (site in Lodestones.SITES) markLoaded(site.x shr 6, site.z shr 6) }

    /** Our vars after the test world's (none), and the loc types built for them. */
    private val vars = CustomVars.after(VarTypes.NONE)
    private val locs = CustomLocs(vars)

    private fun world() = World(IntArray(0), map, customVars = vars, locTypes = LocTypes.NONE.with(locs.types))

    @Test
    fun `the custom loc types are what the client gets, decoded`() {
        for (custom in locs.all) {
            val type = LocTypeDecoder.decode(custom.id, custom.encoded)
            assertEquals(type, custom.type)
            assertTrue(custom.id >= CustomLocs.FIRST_ID)
        }
        val locFiles = locs.overlayFiles.filter { it.group == CustomLocs.LOC_TYPE_GROUP }
        assertEquals(locs.all.map { it.id }, locFiles.map { it.file })
        assertEquals(listOf(65000, 65001, 65002, 65003, 65004, 65005, 65006), locs.all.map { it.id })
        assertTrue(locs.overlayFiles.all { it.index == 2 && (it.group == 6 || it.group == MapElementCodec.GROUP) })
        assertContentEquals(Lodestones.DORMANT_TYPE.encoded, locFiles.first { it.file == Lodestones.DORMANT }.data)
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
    fun `each site's stone is a multiloc that its varbit, after the cache's, turns from the dormant look to the active one`() {
        for (site in Lodestones.SITES) {
            val varbit = vars.lodestoneVarbit(site)
            val type = locs.all.first { it.id == site.id }
            assertContentEquals(site.type(varbit).encoded, type.encoded, "${site.slug}'s type is built for the world's vars")
            val transform = type.definition.transform
            assertNotNull(transform, site.slug)
            assertEquals(varbit, transform.varbit)
            assertEquals(listOf(Lodestones.DORMANT, Lodestones.ACTIVE), transform.children, "0 dormant, 1 active")
            assertTrue(type.type.multiloc)
            assertEquals("Lodestone", type.type.name)
            assertEquals(3, type.type.width)
            assertEquals(0, type.type.collision.interactType)
            assertEquals(site.id, site.loc.id)
            assertEquals(Destination(site.x + 1, site.z + 1, 0), site.arrival, "lands on the centre tile")
            assertEquals(vars.firstVarbit + site.index, varbit)
            assertEquals(VarbitType(vars.lodestonesVarp, site.index, site.index), vars.varbits[varbit], "bit ${site.index} of the lodestones' varp")
            // Another cache's ids give another type, so a stone can't be built before the cache is read.
            assertEquals(varbit + 100, site.type(varbit + 100).definition.transform?.varbit)
        }
        assertEquals(listOf("lumbridge", "draynor", "varrock", "al_kharid", "edgeville"), Lodestones.SITES.map { it.slug })
        assertEquals(listOf(65002, 65003, 65004, 65005, 65006), Lodestones.SITES.map { it.id })
    }

    @Test
    fun `both looks carry the minimap's icon, a map element of ours, and a site's multiloc leaves it to them`() {
        assertEquals(Lodestones.MAP_ELEMENT, Lodestones.DORMANT_TYPE.definition.mapElement)
        assertEquals(Lodestones.MAP_ELEMENT, Lodestones.ACTIVE_TYPE.definition.mapElement)
        for (site in Lodestones.SITES) assertEquals(LocTypeEncoder.NO_MAP_ELEMENT, site.type(0).definition.mapElement, "site ${site.id}")
        val element = locs.overlayFiles.single { it.group == MapElementCodec.GROUP }
        assertEquals(CustomLocs.CONFIG_INDEX to Lodestones.MAP_ELEMENT, element.index to element.file)
        assertEquals(MapElementCodec.Definition(sprite = LodestoneMap.MINIMAP_SPRITE), MapElementCodec.decode(element.data))
        assertEquals(locs.all.size + 1, locs.overlayFiles.size, "the loc types and the one map element")
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
    fun `the world's var types hold our varbits, after its cache's, so a player's lodestone bits can be set`() {
        val world = World(IntArray(0), CollisionMap())
        assertEquals(vars.toString(), world.customVars.toString(), "a world without a cache's vars gets the same ids as ours")
        for (site in Lodestones.SITES) {
            val varbit = world.customVars.lodestoneVarbit(site)
            assertEquals(world.customVars.varbits[varbit], world.varTypes.varbit(varbit))
            assertEquals(varbit, world.lodestones.varbit(site))
        }
        assertTrue(world.varTypes.varpCount > world.customVars.lodestonesVarp)
        assertEquals("lodestones", world.varTypes.varpName(world.customVars.lodestonesVarp), "saved by name")

        val cache = VarTypes(5996, mapOf(20410 to VarbitType(5995, 0, 3)), 20411)
        val other = World(IntArray(0), CollisionMap(), cache)
        assertEquals(5996, other.customVars.lodestonesVarp, "right after this cache's varps")
        assertEquals(20411, other.lodestones.varbit(Lodestones.LUMBRIDGE))
        assertEquals(5998, other.varTypes.varpCount, "after ours: the lodestones and Captain Harlan's varps")
        assertEquals(VarbitType(5996, 4, 4), other.varTypes.varbit(20415))
        assertEquals(mapOf("lodestones" to 5996, "harlan.claimed" to 5997), other.varTypes.namedVarps)
    }
}
