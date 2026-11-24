package osrsps.audit

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import osrsps.cache.DbRows
import osrsps.cache.ObjTypeLoader
import osrsps.cache.LocTypeDecoder
import osrsps.cache.TrailLoader
import osrsps.cache.VarTypeLoader
import osrsps.game.trail.StashUnits
import osrsps.game.trail.StepType
import osrsps.game.trail.TrailBoxes
import osrsps.game.trail.TrailChallenge
import osrsps.game.trail.TrailRewards
import osrsps.game.trail.TrailTier
import osrsps.game.trail.TreasureTrails

/**
 * Treasure Trails against the real cache, which the headless tests (no cache) can't check: the clue helper tables load
 * into every step the Wiki lists, the Misthalin dig, talk, search, emote, fight and hot-cold steps built so far (TT1,
 * TT3, TT4, TT7, TT8) are the ones the plan counted (Treasure Trails plan, 19 Nov 2026, from the same tables), every
 * built map has its interface, every clue scroll item is read, and Misthalin's STASH units (TT5) are the Wiki's, each a
 * Build and Search multiloc.
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
            TrailTier.BEGINNER to 21, TrailTier.EASY to 51, TrailTier.MEDIUM to 24, TrailTier.HARD to 23, TrailTier.ELITE to 3, TrailTier.MASTER to 9,
        ), built.mapValues { it.value.size }, built.toString())

        // The fights inside Misthalin: a Saradomin wizard (hard coordinate), the hard and master double agents, and the
        // Brassican Mage of the master hot-cold steps.
        assertEquals(setOf(2955, 1778, 7312, 7310), data.steps.values.filter(TreasureTrails::built).flatMap { it.combatNpcs }.toSet())

        val problems = ArrayList<String>()
        for (step in data.steps.values) {
            if (!TreasureTrails.built(step)) continue
            if (step.type == StepType.MAP) {
                val map = if (step.tier == TrailTier.BEGINNER) TreasureTrails.BEGINNER_MAPS[step.id] else step.items.firstNotNullOfOrNull { TreasureTrails.MAPS[it] }
                if (map == null) problems += "$step: no map interface"
            } else if (step.text.isNullOrBlank() && step.type != StepType.HOT_COLD) {
                problems += "$step: no text"
            }
            for (item in step.items) if (objs[item]?.iop(1) != "Read") problems += "$step: item $item isn't read"
            if (step.type == StepType.EMOTE && step.emotes.any { it !in osrsps.game.content.Emotes.EMOTES }) problems += "$step: emotes ${step.emotes}"
            for (outfit in step.outfits) for (item in outfit.items.values.flatten()) if (objs[item] == null) problems += "$step: outfit item $item"
            // A box step's puzzle box is the item after its clue (trail_hard_anagram_exp9 and _puzzlebox), and opens.
            // A medium or hard box step with no clue item of its own has no box to hand over, so it stays out of the
            // pool (R50C-3); none should be built.
            if (step.challenge is TrailChallenge.Box && step.items.isEmpty() && step.tier in setOf(TrailTier.MEDIUM, TrailTier.HARD)) problems += "$step: a box step with no clue item"
            if (step.challenge is TrailChallenge.Box && step.items.isNotEmpty()) {
                val box = step.items.first() + 1
                if (box !in TrailBoxes.PUZZLES || objs[box]?.name?.startsWith(TrailBoxes.PUZZLE_BOX) != true) problems += "$step: no puzzle box $box"
            }
        }
        for ((box, first) in TrailBoxes.PUZZLES) {
            if (objs[box]?.iop(1) != "Open") problems += "puzzle box $box isn't opened"
            if ((first until first + 24).any { objs[it] == null }) problems += "puzzle box $box: pieces from $first"
        }
        if (objs[TrailBoxes.LIGHT_BOX]?.name != "Light box") problems += "no light box"
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

    @Test
    @EnabledIfEnvironmentVariable(named = "OSRSPS_FLATCACHE", matches = ".+")
    fun `every Misthalin STASH unit is a multiloc of Build and Search forms on a one-bit varbit`() {
        val cache = FlatCache(Path(System.getenv("OSRSPS_FLATCACHE")))
        val objs = ObjTypeLoader.link(cache.group(CONFIG_INDEX, OBJ_GROUP).mapValues { (id, data) -> ObjTypeLoader.decode(id, data) })
        val rows = cache.group(CONFIG_INDEX, DBROW_GROUP).mapValues { (_, data) -> DbRows.decode(data) }
        val itemRows = HashMap<Int, MutableList<Int>>()
        for (id in 0 until objs.size) {
            val row = objs[id]?.params?.get(TrailLoader.PARAM_STEP_ROW) as? Int ?: continue
            itemRows.getOrPut(row) { ArrayList() } += id
        }
        val units = StashUnits.units(TrailLoader.build(rows, itemRows))
        val locs = cache.group(CONFIG_INDEX, LOC_GROUP)
        val varbits = cache.group(CONFIG_INDEX, VARBIT_GROUP)
        val problems = ArrayList<String>()
        for (stash in units.values) {
            val type = LocTypeDecoder.decode(stash.loc, locs[stash.loc] ?: continue.also { problems += "${stash.loc}: no loc" })
            val forms = type.multiLocs.map { id -> locs[id]?.let { LocTypeDecoder.decode(id, it) } }
            val bits = varbits[type.multiVarbit]?.let(VarTypeLoader::decodeVarbit)
            if (bits == null || bits.startBit != bits.endBit) problems += "${stash.loc}: varbit ${type.multiVarbit} is $bits"
            if (forms.getOrNull(0)?.ops?.get(StashUnits.BUILD - 1) != "Build") problems += "${stash.loc}: unbuilt form ${forms.getOrNull(0)?.ops}"
            if (forms.getOrNull(1)?.ops?.get(StashUnits.SEARCH - 1) != "Search") problems += "${stash.loc}: built form ${forms.getOrNull(1)?.ops}"
            if (forms.getOrNull(1)?.name != "STASH (${stash.tier.key})") problems += "${stash.loc}: built form named ${forms.getOrNull(1)?.name}"
        }
        assertEquals(emptyList(), problems)
        // As many as the Wiki's STASH page lists inside Misthalin's outline (read 2026-11-19).
        assertEquals(EXPECTED_UNITS, units.values.groupingBy { it.tier }.eachCount(), units.values.map { "${it.tier.key} ${it.loc}" }.sorted().toString())
    }

    private companion object {
        val EXPECTED_UNITS = mapOf(TrailTier.BEGINNER to 3, TrailTier.EASY to 13, TrailTier.MEDIUM to 5, TrailTier.HARD to 1, TrailTier.MASTER to 2)
        const val LOC_GROUP = 6
        const val VARBIT_GROUP = 14
        const val CONFIG_INDEX = 2
        const val OBJ_GROUP = 10
        const val DBROW_GROUP = 38
    }
}
