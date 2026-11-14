package osrsps.game.skill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanoesTest {
    private val river = listOf(CanoeStation.LUMBRIDGE, CanoeStation.CHAMPIONS_GUILD, CanoeStation.BARBARIAN_VILLAGE, CanoeStation.EDGEVILLE)

    @Test
    fun `no canoe goes to the station it starts from`() {
        for (type in 1..4) for (from in CanoeStation.entries) assertFalse(Canoes.reaches(type, from, from.destination), "$type from $from")
    }

    @Test
    fun `a log goes one station, a dugout two, a stable dugout three, along the Lum (Wiki, Canoe)`() {
        for ((i, from) in river.withIndex()) {
            for ((j, to) in river.withIndex()) {
                if (i == j) continue
                for (type in 1..4) {
                    val far = kotlin.math.abs(i - j)
                    assertEquals(far <= type, Canoes.reaches(type, from, to.destination), "canoe $type from $from to $to")
                }
            }
        }
    }

    @Test
    fun `only a waka reaches the Ferox Enclave or the Wilderness Pond`() {
        for (from in river) {
            for (type in 1..3) {
                assertFalse(Canoes.reaches(type, from, CanoeDestination.FEROX_ENCLAVE))
                assertFalse(Canoes.reaches(type, from, CanoeDestination.WILDERNESS_POND))
            }
            assertTrue(Canoes.reaches(4, from, CanoeDestination.FEROX_ENCLAVE))
            assertTrue(Canoes.reaches(4, from, CanoeDestination.WILDERNESS_POND))
        }
    }

    @Test
    fun `levels and XP match the Wiki`() {
        assertEquals(listOf(12, 27, 42, 57), CanoeType.entries.map { it.level })
        assertEquals(listOf(300, 600, 900, 1500), CanoeType.entries.map { it.xpTenths })
    }

    @Test
    fun `every axe tier has a shaping pair, and every axe a carving animation`() {
        val data = WoodcuttingData.load()
        for (axe in data.axes) {
            assertTrue(axe.tier in Canoes.SHAPE_PAIRS, axe.tier)
            assertTrue(axe.tool.obj in Canoes.CARVE, "axe ${axe.tool.obj}")
        }
    }

    @Test
    fun `a rune axe at 99 always shapes, a bronze axe at 12 seldom`() {
        val (low, high) = Canoes.SHAPE_PAIRS.getValue("rune")
        assertEquals(SuccessRoll.DENOMINATOR, SuccessRoll.chance(low, high, 99))
        val (bLow, bHigh) = Canoes.SHAPE_PAIRS.getValue("bronze")
        assertTrue(SuccessRoll.chance(bLow, bHigh, 12) < SuccessRoll.DENOMINATOR / 5)
    }
}

class CanoeSaveTest {
    @Test
    fun `a canoe in a save (a restart's, an autosave's) is gone at the next login`() {
        val saves = osrsps.game.MemorySaveStore()
        val types = osrsps.game.VarTypes(
            6_000,
            mapOf(
                CanoeStation.LUMBRIDGE.varbit to osrsps.game.VarbitType(674, 0, 7),
                Canoes.VARBIT_CANOE_TYPE to osrsps.game.VarbitType(675, 0, 7),
            ),
        )
        val world = osrsps.game.World(IntArray(0), osrsps.map.CollisionMap(), saves = saves, varTypes = types)
        val player = world.loadPlayer(1, "Test")
        player.vars.setVarbit(CanoeStation.LUMBRIDGE.varbit, 14)
        player.vars.setVarbit(Canoes.VARBIT_CANOE_TYPE, 4)
        assertTrue(world.save(player))
        val loaded = world.loadPlayer(2, "test")
        assertEquals(0, loaded.vars.getVarbit(CanoeStation.LUMBRIDGE.varbit))
        assertEquals(0, loaded.vars.getVarbit(Canoes.VARBIT_CANOE_TYPE))
    }
}
