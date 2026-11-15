package osrsps.audit

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import osrsps.cache.LocTypeDecoder
import osrsps.game.skill.hunter.Pitfalls

/**
 * Hunter step 5's pit table against the real cache, which the headless tests (no cache) can't check: each pit multiloc
 * shows the bare pit, the spiked pit and the falling-in form on values 0 to 2 of the varbit [Pitfalls.PITS] gives it, and
 * its full form (value 3) is its creature's.
 *
 * Needs OSRSPS_FLATCACHE: a checkout of github.com/abextm/osrs-cache at the server's revision.
 */
class PitfallCacheAuditTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "OSRSPS_FLATCACHE", matches = ".+")
    fun `every pit's varbit and forms are the cache's`() {
        val locs = FlatCache(Path(System.getenv("OSRSPS_FLATCACHE"))).group(CONFIG_INDEX, LOC_GROUP)
        val full = mapOf(
            Pitfalls.KYATT to 19233, Pitfalls.LARUPIA to 19232, Pitfalls.GRAAHK to 19231,
            Pitfalls.SUNLIGHT to ANTELOPE_FULL, Pitfalls.MOONLIGHT to ANTELOPE_FULL,
        )
        val problems = Pitfalls.PITS.values.mapNotNull { pit ->
            val type = LocTypeDecoder.decode(pit.id, locs.getValue(pit.id))
            val forms = type.multiLocs
            val expected = listOf(BARE, SPIKED, FALLING, full.getValue(pit.creature))
            if (type.multiVarbit == pit.varbit && forms.take(4) == expected && type.width == 2 && type.length == 2) null
            else "${pit.id}: varbit ${type.multiVarbit} forms $forms ${type.width}x${type.length}"
        }
        assertEquals(emptyList(), problems)
    }

    /** Where the map puts each pit (rev 242, by the loc file): on level 1 of a bridged column, so level 0 in play. */
    @Test
    @EnabledIfEnvironmentVariable(named = "OSRSPS_FLATCACHE", matches = ".+")
    fun `every pit stands on level 0 where its creatures live`() {
        val locs = RealMap.load().map.locs
        val placed = mapOf(
            19253 to (2700 to 3795), 19259 to (2556 to 2893), 19264 to (2766 to 3010), 51673 to (1749 to 3014), 51678 to (1563 to 9424),
        )
        val missing = placed.filter { (id, at) -> locs.at(at.first, at.second, 0).none { it.id == id } }
        assertEquals(emptyMap(), missing)
    }

    private companion object {
        const val CONFIG_INDEX = 2
        const val LOC_GROUP = 6
        const val BARE = 19227 // hunting_pitfall_invis_empty, "Pit"
        const val SPIKED = 19228 // hunting_pitfall_invis_set, "Spiked pit"
        const val FALLING = 19229 // hunting_pitfall_invis_catching
        const val ANTELOPE_FULL = 53024
    }
}
