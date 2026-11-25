package osrsps.game.trail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.GameRandom
import osrsps.game.ObjType
import osrsps.game.ObjTypes

/**
 * trail-rewards.json against a cache made from its own names (the real cache's check is TrailCacheAuditTest): every
 * tier loads, each table's weights add up to its slots (the Wiki's per-roll rates sum to one), and a casket gives
 * between its tier's fewest and most rolls.
 */
class TrailRewardsTest {
    private val text = TrailRewards::class.java.getResource(TrailRewards.RESOURCE)!!.readText()

    /** An item for every id the file names, noted ones as notes of a made-up item. */
    private val objTypes: ObjTypes = run {
        val objs = arrayOfNulls<ObjType>(40_000)
        var spare = 39_000
        fun put(entry: kotlinx.serialization.json.JsonObject) {
            val id = entry.getValue("item").jsonPrimitive.int
            val name = entry.getValue("name").jsonPrimitive.content
            if (objs[id] != null) return
            if (name.endsWith(" (noted)")) {
                val base = spare++
                objs[base] = ObjType(base, name.removeSuffix(" (noted)"), certlink = id)
                objs[id] = ObjType(id, name.removeSuffix(" (noted)"), stackable = true, certlink = base, certtemplate = 799)
            } else {
                objs[id] = ObjType(id, name)
            }
        }
        for ((_, tier) in Json.parseToJsonElement(text).jsonObject.getValue("tiers").jsonObject) {
            for (key in listOf("lines", "extra", "milestones")) {
                for (entry in tier.jsonObject.getValue(key).jsonArray) {
                    put(entry.jsonObject)
                    entry.jsonObject["with"]?.jsonArray?.forEach { put(it.jsonObject) }
                }
            }
        }
        ObjTypes(objs)
    }

    @Test
    fun `every tier's table loads with its weights adding up to its slots`() {
        val rewards = TrailRewards.parse(text, objTypes)
        assertEquals(emptyList(), rewards.warnings)
        val rolls = TrailTier.entries.associateWith { rewards[it]!!.let { r -> r.minRolls to r.maxRolls } }
        assertEquals(
            mapOf(TrailTier.BEGINNER to (1 to 3), TrailTier.EASY to (2 to 4), TrailTier.MEDIUM to (3 to 5), TrailTier.HARD to (4 to 6), TrailTier.ELITE to (4 to 6), TrailTier.MASTER to (5 to 7)),
            rolls,
        )
        // The scroll cases at the Wiki's counts. The master scroll box isn't an extra line: TreasureTrails.masterBox rolls
        // it, under the master cap (exploit review R55B-1: an extra line rolled it a second time, past the cap).
        val boxes = TrailTier.entries.map { it.scrollBox }.toSet()
        for (tier in TrailTier.entries) assertEquals(emptyList(), rewards[tier]!!.extra.map { it.item.type.id }.filter { it in boxes }, "$tier")
        assertEquals(listOf(100, 200, 500), rewards[TrailTier.EASY]!!.milestones.map { it.count })
        assertEquals(listOf(30906, 30908), TreasureTrails.SCROLL_CASES.getValue(TrailTier.EASY))
        assertEquals(TreasureTrails.SCROLL_CASES.getValue(TrailTier.EASY), rewards[TrailTier.EASY]!!.milestones.take(2).map { it.type.id })
    }

    @Test
    fun `a casket gives one item a roll, as many rolls as its tier allows`() {
        val rewards = TrailRewards.parse(text, objTypes)
        val random = GameRandom(7)
        for (tier in TrailTier.entries) {
            val casket = rewards[tier]!!
            repeat(200) {
                // A roll gives one item, or the three super potions together; the extras may add one more each.
                val items = casket.roll(random).size
                assertTrue(items >= casket.minRolls && items <= casket.maxRolls * 3 + casket.extra.size, "$tier gave $items")
            }
        }
    }
}
