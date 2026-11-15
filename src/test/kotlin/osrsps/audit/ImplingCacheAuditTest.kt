package osrsps.audit

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import osrsps.cache.NpcTypeLoader
import osrsps.cache.ObjTypeLoader
import osrsps.game.NpcTypes
import osrsps.game.combat.NpcDrops
import osrsps.game.skill.hunter.Butterflies
import osrsps.game.skill.hunter.Implings

/**
 * Hunter steps 6a and 6b against the real cache, which the headless tests (no cache) can't check: every impling's NPCs have
 * Catch as op1 and its jar Loot as iop3 (each butterfly's Catch and its jar Release as iop1), and every item of the loot file is named as the cache names it (a noted line's
 * id is a note).
 *
 * Needs OSRSPS_FLATCACHE: a checkout of github.com/abextm/osrs-cache at the server's revision.
 */
class ImplingCacheAuditTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "OSRSPS_FLATCACHE", matches = ".+")
    fun `the implings' ops, the jars' Loot and the loot file's items are the cache's`() {
        val cache = FlatCache(Path(System.getenv("OSRSPS_FLATCACHE")))
        val objs = ObjTypeLoader.link(cache.group(CONFIG_INDEX, OBJ_GROUP).mapValues { (id, data) -> ObjTypeLoader.decode(id, data) })
        val npcs = cache.group(CONFIG_INDEX, NPC_GROUP)
        val problems = ArrayList<String>()
        for (impling in Implings.ALL) {
            for (id in impling.npcs) {
                val type = NpcTypeLoader.decode(id, npcs.getValue(id))
                if (type.ops.firstOrNull() != "Catch") problems += "npc $id (${type.name}): ops ${type.ops}"
            }
            val jar = objs[impling.jarred]
            if (jar?.iop(Implings.LOOT) != "Loot") problems += "jar ${impling.jarred} (${jar?.name}): iops ${jar?.iops}"
        }
        // Step 6a's butterflies and moths the same way: Catch, and Release on the jarred one.
        for (butterfly in Butterflies.ALL) {
            for (id in butterfly.npcs) {
                val type = NpcTypeLoader.decode(id, npcs.getValue(id))
                if (type.ops.firstOrNull() != "Catch") problems += "npc $id (${type.name}): ops ${type.ops}"
            }
            val jar = objs[butterfly.jarred]
            if (jar?.iop(Butterflies.RELEASE) != "Release") problems += "jar ${butterfly.jarred} (${jar?.name}): iops ${jar?.iops}"
        }
        assertEquals(emptyList(), problems)
        val text = Implings::class.java.getResource(Implings.RESOURCE)!!.readText()
        assertEquals(emptyList(), NpcDrops.parse(text, NpcTypes.NONE, objs).warnings)
    }

    private companion object {
        const val CONFIG_INDEX = 2
        const val NPC_GROUP = 9
        const val OBJ_GROUP = 10
    }
}
