package osrsps.game.skill.magic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import osrsps.game.ObjTypes
import osrsps.game.combat.RuneCost

/** The teleports file against objs shaped as the rev 242 cache has them ([TeleportObjs]), and the landing picks. */
class TeleportDataTest {
    private val data = TeleportData.load(TeleportObjs.objTypes())

    @Test
    fun `every teleport in the file loads with its cache level, runes, component and the Wiki's XP`() {
        with(TeleportObjs) {
            // Spell, component, level, XP in tenths (OSRS Wiki, each spell's page), tablet.
            val expected = listOf(
                listOf(VARROCK, VARROCK_COMPONENT, 25, 350, VARROCK_TABLET),
                listOf(LUMBRIDGE, LUMBRIDGE_COMPONENT, 31, 410, LUMBRIDGE_TABLET),
                listOf(FALADOR, FALADOR_COMPONENT, 37, 480, FALADOR_TABLET),
                listOf(CAMELOT, CAMELOT_COMPONENT, 45, 555, CAMELOT_TABLET),
                listOf(KOUREND, KOUREND_COMPONENT, 48, 580, KOUREND_TABLET),
                listOf(ARDOUGNE, ARDOUGNE_COMPONENT, 51, 610, ARDOUGNE_TABLET),
                listOf(FORTIS, FORTIS_COMPONENT, 54, 640, FORTIS_TABLET),
                listOf(WATCHTOWER, WATCHTOWER_COMPONENT, 58, 680, WATCHTOWER_TABLET),
                listOf(TROLLHEIM, TROLLHEIM_COMPONENT, 61, 680, -1),
                listOf(APE_ATOLL, APE_ATOLL_COMPONENT, 64, 740, -1),
            )
            assertEquals(expected, data.spells.map { listOf(it.obj, it.component, it.level, it.xpTenths, it.tablet) })
            assertEquals(listOf(RuneCost(LAW, 1), RuneCost(AIR, 3), RuneCost(FIRE, 1)), data.byComponent.getValue(VARROCK_COMPONENT).runes)
            assertEquals("Kourend Castle Teleport", data.byComponent.getValue(KOUREND_COMPONENT).name)
            assertEquals(RuneCost(BANANA, 1), data.byComponent.getValue(APE_ATOLL_COMPONENT).extra)
            assertEquals(null, data.byComponent.getValue(VARROCK_COMPONENT).extra)
            assertEquals(VARROCK, data.byTablet.getValue(VARROCK_TABLET).obj)
        }
    }

    @Test
    fun `the landing areas are the Wiki's, Watchtower on the top floor`() {
        fun landing(component: Int) = data.byComponent.getValue(component).landing
        with(TeleportObjs) {
            // Varrock: within 2 of the Wiki's 3213, 3424.
            assertEquals(25, landing(VARROCK_COMPONENT).tiles.size)
            assertTrue((3213 to 3424) in landing(VARROCK_COMPONENT).tiles)
            assertEquals(3213 to 3424, landing(VARROCK_COMPONENT).centre)
            // Lumbridge: the Wiki's plus, 10 tiles across and 10 up, sharing 4.
            val lumbridge = landing(LUMBRIDGE_COMPONENT).tiles
            assertEquals(16, lumbridge.size)
            assertTrue((3219 to 3218) in lumbridge && (3223 to 3219) in lumbridge && (3221 to 3216) in lumbridge && (3222 to 3220) in lumbridge)
            assertTrue((3219 to 3216) !in lumbridge && (3223 to 3220) !in lumbridge)
            assertEquals(49, landing(ARDOUGNE_COMPONENT).tiles.size)
            assertEquals(2, landing(WATCHTOWER_COMPONENT).level)
            assertEquals(listOf(2546 to 3114), landing(WATCHTOWER_COMPONENT).tiles)
            assertEquals(2798 to 2799, landing(APE_ATOLL_COMPONENT).centre)
            assertEquals(1641 to 3673, landing(KOUREND_COMPONENT).centre)
            assertEquals(1681 to 3133, landing(FORTIS_COMPONENT).centre)
        }
    }

    @Test
    fun `a landing picks among the free tiles, and the centre when none is free`() {
        val landing = Landing(0, listOf(intArrayOf(10, 10, 12, 12)))
        val free = setOf(10 to 10, 12 to 12)
        val picks = (0 until 2).map { i -> landing.pick({ x, z -> (x to z) in free }) { count -> assertEquals(2, count); i } }
        assertEquals(listOf(10 to 10, 12 to 12), picks)
        assertEquals(11 to 11, landing.pick({ _, _ -> false }) { error("nothing to pick from") })
    }

    @Test
    fun `a cache without the spells loads none, and a bad entry fails the file`() {
        assertEquals(0, TeleportData.load(ObjTypes.NONE).spells.size)
        val objs = TeleportObjs.objTypes()
        assertFailsWith<IllegalArgumentException> {
            TeleportData.parse("""{ "teleports": [ { "spell": "25_varrock_teleport", "xp": 35, "land": [[5, 5, 4, 4]] } ] }""", objs)
        }
        assertFailsWith<IllegalArgumentException> {
            TeleportData.parse("""{ "teleports": [ { "spell": "25_varrock_teleport", "xp": 35, "land": [[1, 1, 2, 2]] }, { "spell": "25_varrock_teleport", "xp": 35, "land": [[1, 1, 2, 2]] } ] }""", objs)
        }
        assertFailsWith<IllegalArgumentException> {
            TeleportData.parse("""{ "teleports": [ { "spell": "lawrune", "xp": 35, "land": [[1, 1, 2, 2]] } ] }""", objs)
        }
    }
}
