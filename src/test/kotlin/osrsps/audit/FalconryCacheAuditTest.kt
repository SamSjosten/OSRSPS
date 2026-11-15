package osrsps.audit

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import osrsps.cache.NpcTypeLoader
import osrsps.cache.ObjTypeLoader
import osrsps.game.WearPos
import osrsps.game.skill.hunter.Falconry

/**
 * Hunter step 7 against the real cache: Matthias's Talk-to and Quick-falcon, each kebbit's Catch, each falcon on its
 * prey's Retrieve, the glove worn as a weapon over the shield, and the drops' names.
 *
 * Needs OSRSPS_FLATCACHE: a checkout of github.com/abextm/osrs-cache at the server's revision.
 */
class FalconryCacheAuditTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "OSRSPS_FLATCACHE", matches = ".+")
    fun `the falconry NPCs' ops, the glove and the drops are the cache's`() {
        val cache = FlatCache(Path(System.getenv("OSRSPS_FLATCACHE")))
        val npcs = cache.group(CONFIG_INDEX, NPC_GROUP)
        val objs = ObjTypeLoader.link(cache.group(CONFIG_INDEX, OBJ_GROUP).mapValues { (id, data) -> ObjTypeLoader.decode(id, data) })
        fun ops(id: Int) = NpcTypeLoader.decode(id, npcs.getValue(id)).ops
        assertEquals("Talk-to", ops(Falconry.MATTHIAS)[Falconry.TALK_TO - 1])
        assertEquals("Quick-falcon", ops(Falconry.MATTHIAS)[Falconry.QUICK_FALCON - 1])
        for (kebbit in Falconry.KEBBITS) {
            assertEquals("Catch", ops(kebbit.npc)[Falconry.CATCH - 1], "kebbit ${kebbit.npc}")
            assertEquals("Retrieve", ops(kebbit.falcon)[Falconry.RETRIEVE - 1], "falcon ${kebbit.falcon}")
        }
        for (id in listOf(Falconry.GLOVE_EMPTY, Falconry.GLOVE_FALCON)) {
            val glove = objs[id]!!
            assertEquals("Falconer's glove" to (WearPos.WEAPON to WearPos.SHIELD), glove.name to (glove.wearpos1 to glove.wearpos2))
        }
        assertEquals(
            listOf("Bones", "Spotted kebbit fur", "Bones", "Dark kebbit fur", "Bones", "Dashing kebbit fur", "Raw dashing kebbit"),
            Falconry.KEBBITS.flatMap { k -> k.drops.map { objs[it]!!.name } },
        )
    }

    private companion object {
        const val CONFIG_INDEX = 2
        const val NPC_GROUP = 9
        const val OBJ_GROUP = 10
    }
}
