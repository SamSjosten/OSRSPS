package osrsps.audit

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import osrsps.cache.NpcTypeLoader
import osrsps.cache.ObjTypeLoader
import osrsps.game.skill.hunter.HunterShops

/**
 * Hunter step 9 against the real cache: the packs' Open, Asyff's Fur Clothing, the Hunter shopkeepers' Trade, and every
 * fur clothing piece and fur named as the cache names it.
 *
 * Needs OSRSPS_FLATCACHE: a checkout of github.com/abextm/osrs-cache at the server's revision.
 */
class HunterShopsCacheAuditTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "OSRSPS_FLATCACHE", matches = ".+")
    fun `the packs, Asyff, the shopkeepers and the fur pieces are the cache's`() {
        val cache = FlatCache(Path(System.getenv("OSRSPS_FLATCACHE")))
        val npcs = cache.group(CONFIG_INDEX, NPC_GROUP)
        val objs = ObjTypeLoader.link(cache.group(CONFIG_INDEX, OBJ_GROUP).mapValues { (id, data) -> ObjTypeLoader.decode(id, data) })
        fun ops(id: Int) = NpcTypeLoader.decode(id, npcs.getValue(id)).ops
        assertEquals("Fur Clothing", ops(HunterShops.ASYFF)[HunterShops.FUR_CLOTHING - 1])
        for (keeper in SHOPKEEPERS) assertEquals(true, "Trade" in ops(keeper), "keeper $keeper: ${ops(keeper)}")
        assertEquals(
            listOf("Bird snare pack" to "Bird snare", "Box trap pack" to "Box trap", "Magic imp box pack" to "Magic box"),
            HunterShops.PACKS.map { (pack, item) -> objs[pack]!!.name to objs[item]!!.name },
        )
        for (pack in HunterShops.PACKS.keys) assertEquals("Open", objs[pack]!!.iop(HunterShops.OPEN), "pack $pack")
        assertEquals(true, HunterShops.PACKS.values.all { objs[it]!!.stackable })
        assertEquals(
            listOf(
                "Polar camo top", "Polar camo legs", "Wood camo top", "Wood camo legs", "Jungle camo top", "Jungle camo legs",
                "Desert camo top", "Desert camo legs", "Larupia hat", "Larupia top", "Larupia legs", "Graahk headdress",
                "Graahk top", "Graahk legs", "Kyatt hat", "Kyatt top", "Kyatt legs", "Gloves of silence", "Spotted cape", "Spottier cape",
            ),
            HunterShops.PIECES.map { objs[it.item]!!.name },
        )
        assertEquals(
            listOf("Polar kebbit fur", "Common kebbit fur", "Feldip weasel fur", "Desert devil fur", "Larupia fur", "Tatty larupia fur",
                "Graahk fur", "Tatty graahk fur", "Kyatt fur", "Tatty kyatt fur", "Dark kebbit fur", "Spotted kebbit fur", "Dashing kebbit fur"),
            HunterShops.PIECES.flatMap { it.furs }.distinct().map { objs[it]!!.name },
        )
    }

    private companion object {
        const val CONFIG_INDEX = 2
        const val NPC_GROUP = 9
        const val OBJ_GROUP = 10
        val SHOPKEEPERS = listOf(1501, 1350, 3218, 14832)
    }
}
