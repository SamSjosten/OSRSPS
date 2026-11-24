package osrsps.game.trail

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.GameRandom
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.combat.Drop
import osrsps.game.combat.DropItem
import osrsps.game.combat.DropSlot
import osrsps.game.combat.DropTable
import osrsps.game.combat.TertiaryDrop

private val logger = KotlinLogging.logger {}

/** An item a tier's casket gives when its completed count reaches [count]. */
class TrailMilestone(val count: Int, val type: ObjType)

/**
 * What a tier's reward casket gives: [minRolls] to [maxRolls] rolls on [table], each [extra] drop rolled once besides,
 * and the [milestones] that come with the casket reaching their count.
 */
class CasketRewards(
    val minRolls: Int,
    val maxRolls: Int,
    val table: DropTable,
    val extra: List<TertiaryDrop> = emptyList(),
    val milestones: List<TrailMilestone> = emptyList(),
) {
    init {
        require(minRolls in 1..maxRolls) { "Bad roll count $minRolls to $maxRolls" }
    }

    /** One casket's rolls and extra drops, in the order drawn: the roll count, each roll, then each extra. */
    fun roll(random: GameRandom): List<Drop> {
        val drops = ArrayList<Drop>()
        repeat(random.between(minRolls, maxRolls)) {
            for (item in table.roll(random)) drops += Drop(item.type, item.count(random))
        }
        for (drop in extra) if (drop.hits(random)) drops += Drop(drop.item.type, drop.item.count(random))
        return drops
    }
}

/** Every tier's casket rewards, from [RESOURCE]. A tier the file or the cache lacks gives nothing. */
class TrailRewards(private val tiers: Map<TrailTier, CasketRewards>, val warnings: List<String> = emptyList()) {
    operator fun get(tier: TrailTier): CasketRewards? = tiers[tier]

    companion object {
        const val RESOURCE = "/trail-rewards.json"
        val NONE = TrailRewards(emptyMap())

        fun load(objTypes: ObjTypes): TrailRewards {
            val text = TrailRewards::class.java.getResourceAsStream(RESOURCE)?.bufferedReader()?.use { it.readText() }
                ?: return NONE.also { logger.warn { "No $RESOURCE: caskets give nothing" } }
            return parse(text, objTypes).also { rewards -> rewards.warnings.forEach { logger.warn { "$RESOURCE: $it" } } }
        }

        /**
         * Reads the file: `tiers` by key, each with `rolls` (`min`, `max`), a table of `slots` whose `lines` are an
         * `item` (an id with its cache `name`), a `count` or `min`/`max`, a `weight`, and items that come `with` it;
         * `extra` drops each with a `chance` ("1/50"); `milestones` of a `count` and an item. A name the cache
         * disagrees with is warned about (a noted item's name is its note's, " (noted)" written after it); a tier
         * with an item the cache lacks is left out with a warning, as a test cache has few items.
         */
        fun parse(text: String, objTypes: ObjTypes): TrailRewards {
            val root = Json.parseToJsonElement(text).jsonObject
            val warnings = ArrayList<String>()
            val tiers = HashMap<TrailTier, CasketRewards>()
            for ((key, value) in root.getValue("tiers").jsonObject) {
                val tier = TrailTier.byKey(key) ?: run { warnings += "no tier $key"; null } ?: continue
                try {
                    tiers[tier] = tier(value.jsonObject, objTypes, warnings)
                } catch (e: MissingItem) {
                    warnings += "$key left out: ${e.message}"
                }
            }
            return TrailRewards(tiers, warnings)
        }

        private class MissingItem(message: String) : RuntimeException(message)

        private fun tier(obj: JsonObject, objTypes: ObjTypes, warnings: MutableList<String>): CasketRewards {
            fun type(entry: JsonObject): ObjType {
                val id = entry.getValue("item").jsonPrimitive.int
                val name = entry.getValue("name").jsonPrimitive.content
                val type = objTypes[id] ?: throw MissingItem("no item $id ($name) in the cache")
                val cacheName = if (type.isCert) objTypes[type.certlink]?.name?.let { "$it (noted)" } else type.name
                if (!name.equals(cacheName, ignoreCase = true)) warnings += "$id is $cacheName, not $name"
                return type
            }
            fun item(entry: JsonObject): DropItem {
                val count = entry["count"]?.jsonPrimitive?.int
                val min = entry["min"]?.jsonPrimitive?.int ?: count ?: 1
                val max = entry["max"]?.jsonPrimitive?.int ?: count ?: 1
                return DropItem(type(entry), min, max)
            }
            val rolls = obj.getValue("rolls").jsonObject
            val lines = obj.getValue("lines").jsonArray.map { it.jsonObject }.map { line ->
                DropSlot(
                    line.getValue("weight").jsonPrimitive.int, item(line),
                    with = line["with"]?.jsonArray.orEmpty().map { item(it.jsonObject) },
                )
            }
            val extra = obj["extra"]?.jsonArray.orEmpty().map { it.jsonObject }.map { entry ->
                val (numerator, denominator) = entry.getValue("chance").jsonPrimitive.content.split("/").map(String::toInt)
                TertiaryDrop(item(entry), denominator, numerator)
            }
            val milestones = obj["milestones"]?.jsonArray.orEmpty().map { it.jsonObject }.map { entry ->
                TrailMilestone(entry.getValue("count").jsonPrimitive.int, type(entry))
            }
            return CasketRewards(
                rolls.getValue("min").jsonPrimitive.int, rolls.getValue("max").jsonPrimitive.int,
                DropTable(obj.getValue("slots").jsonPrimitive.int, lines), extra, milestones.sortedBy { it.count },
            )
        }
    }
}
