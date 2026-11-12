package osrsps.audit

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import osrsps.cache.ObjTypeLoader
import osrsps.game.Scenarios

/**
 * Every scenario's loadout against the real cache's items and gameval names, which the headless tests (no cache) can't
 * check: a name the cache doesn't have makes `::scenario` fail on the live server alone (nightly 2026-11-11, the
 * `fishing-cooking` scenario).
 *
 * Needs OSRSPS_FLATCACHE: a checkout of github.com/abextm/osrs-cache at the server's revision (2.flatcache and
 * 24.flatcache).
 */
class ScenarioCacheAuditTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "OSRSPS_FLATCACHE", matches = ".+")
    fun `every scenario's items are in the live cache`() {
        val cache = FlatCache(Path(System.getenv("OSRSPS_FLATCACHE")))
        val names = cache.group(GAMEVAL_INDEX, GAMEVAL_OBJS)
        val decoded = cache.group(CONFIG_INDEX, OBJ_GROUP).mapValues { (id, data) ->
            ObjTypeLoader.decode(id, data, names[id]?.let { String(it, Charsets.ISO_8859_1) })
        }
        val objs = ObjTypeLoader.link(decoded)
        val scenarios = Scenarios.load()
        val problems = scenarios.names.mapNotNull { name ->
            runCatching { scenarios[name]!!.loadout(objs) }.exceptionOrNull()?.let { "$name: ${it.message}" }
        }
        assertEquals(emptyList(), problems)
    }

    private companion object {
        const val CONFIG_INDEX = 2
        const val OBJ_GROUP = 10
        const val GAMEVAL_INDEX = 24
        const val GAMEVAL_OBJS = 0
    }
}
