package osrsps.audit

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import osrsps.cache.DbRows
import osrsps.cache.ObjTypeLoader
import osrsps.cache.TrailLoader
import osrsps.game.trail.StepType
import osrsps.game.trail.TrailRewards
import osrsps.game.trail.TrailTier
import osrsps.game.trail.TreasureTrails

/**
 * Treasure Trails against the real cache, which the headless tests (no cache) can't check: the clue helper tables load
 * into every step the Wiki lists, the Misthalin dig, talk, search and emote steps built so far (TT1, TT3, TT4) are the ones the plan counted (Treasure Trails plan,
 * 19 Nov 2026, from the same tables), every built map has its interface, and every clue scroll item is read.
 *
 * Needs OSRSPS_FLATCACHE: a checkout of github.com/abextm/osrs-cache at the server's revision.
 */
class TrailCacheAuditTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "OSRSPS_FLATCACHE", matches = ".+")
    fun `the clue helper tables give Misthalin's dig steps, each map with its interface`() {
        val cache = FlatCache(Path(System.getenv("OSRSPS_FLATCACHE")))
        val objs = ObjTypeLoader.link(cache.group(CONFIG_INDEX, OBJ_GROUP).mapValues { (id, data) -> ObjTypeLoader.decode(id, data) })
        val rows = cache.group(CONFIG_INDEX, DBROW_GROUP).mapValues { (_, data) -> DbRows.decode(data) }
        val itemRows = HashMap<Int, MutableList<Int>>()
        for (id in 0 until objs.size) {
            val row = objs[id]?.params?.get(TrailLoader.PARAM_STEP_ROW) as? Int ?: continue
            itemRows.getOrPut(row) { ArrayList() } += id
        }
        val data = TrailLoader.build(rows, itemRows)
        // Every tier is there, in the hundreds for easy to master (the Wiki's guides list about as many).
        for (tier in TrailTier.entries) assertTrue(data.tier(tier).size >= if (tier == TrailTier.BEGINNER) 20 else 90, "$tier: ${data.tier(tier).size}")

        val built = TrailTier.entries.associateWith { tier -> data.tier(tier).filter { step -> TreasureTrails.built(step) }.map { it.key }.sorted() }
        assertEquals(mapOf(
            TrailTier.BEGINNER to 18, TrailTier.EASY to 51, TrailTier.MEDIUM to 21, TrailTier.HARD to 20, TrailTier.ELITE to 3, TrailTier.MASTER to 1,
        ), built.mapValues { it.value.size }, built.toString())

        val problems = ArrayList<String>()
        for (step in data.steps.values) {
            if (!TreasureTrails.built(step)) continue
            if (step.type == StepType.MAP) {
                val map = if (step.tier == TrailTier.BEGINNER) TreasureTrails.BEGINNER_MAPS[step.id] else step.items.firstNotNullOfOrNull { TreasureTrails.MAPS[it] }
                if (map == null) problems += "$step: no map interface"
            } else if (step.text.isNullOrBlank()) {
                problems += "$step: no text"
            }
            for (item in step.items) if (objs[item]?.iop(1) != "Read") problems += "$step: item $item isn't read"
            if (step.type == StepType.EMOTE && step.emotes.any { it !in osrsps.game.content.Emotes.EMOTES }) problems += "$step: emotes ${step.emotes}"
            for (outfit in step.outfits) for (item in outfit.items.values.flatten()) if (objs[item] == null) problems += "$step: outfit item $item"
        }
        for (tier in TrailTier.entries) {
            if (tier.sharedClue >= 0 && objs[tier.sharedClue]?.intParam(tier.param) != 1) problems += "$tier: shared clue ${tier.sharedClue} lacks param ${tier.param}"
            if (objs[tier.scrollBox]?.iop(1) != "Open") problems += "$tier: scroll box ${tier.scrollBox} isn't opened"
            if (objs[tier.rewardCasket]?.iop(1) != "Open") problems += "$tier: casket ${tier.rewardCasket} isn't opened"
        }
        assertEquals(emptyList(), problems)
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OSRSPS_FLATCACHE", matches = ".+")
    fun `every casket reward is the cache's item of that name, and every tier's casket and scroll cases open`() {
        val cache = FlatCache(Path(System.getenv("OSRSPS_FLATCACHE")))
        val objs = ObjTypeLoader.link(cache.group(CONFIG_INDEX, OBJ_GROUP).mapValues { (id, data) -> ObjTypeLoader.decode(id, data) })
        val rewards = TrailRewards.parse(TrailRewards::class.java.getResource(TrailRewards.RESOURCE)!!.readText(), objs)
        assertEquals(emptyList(), rewards.warnings)
        for (tier in TrailTier.entries) {
            assertTrue(rewards[tier] != null, "$tier")
            for (case in TreasureTrails.SCROLL_CASES.getValue(tier)) assertEquals("Open", objs[case]?.iop(1), "$tier case $case")
        }
    }

    private companion object {
        const val CONFIG_INDEX = 2
        const val OBJ_GROUP = 10
        const val DBROW_GROUP = 38
    }
}
