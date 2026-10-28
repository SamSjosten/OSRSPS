package osrsps.game.combat

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes

private val logger = KotlinLogging.logger {}

/** An item a kill can give: [min] to [max] of [type], both included. */
data class DropItem(val type: ObjType, val min: Int, val max: Int) {
    init {
        require(min in 1..max) { "Bad drop count $min to $max of $type" }
    }

    /** One of the counts, each equally likely; a fixed count takes no draw. */
    fun count(random: GameRandom): Int = if (min == max) min else random.between(min, max)
}

/** One line of a [DropTable]: [weight] of its slots give [item], or a roll on [table], or nothing when both are null. */
class DropSlot(val weight: Int, val item: DropItem? = null, val table: DropTable? = null) {
    init {
        require(weight >= 1) { "A drop table line needs a weight, had $weight" }
        require(item == null || table == null) { "A drop table line gives an item or a table, not both" }
    }

    val isNothing: Boolean get() = item == null && table == null
}

/**
 * A weighted table as the OSRS Wiki writes drop rates: [slots] slots, each [DropSlot.weight] of them a line, as in
 * "3/128". The weights add up to the slots, so one draw always lands on a line.
 */
class DropTable(val slots: Int, val lines: List<DropSlot>) {
    init {
        require(slots >= 1) { "A drop table needs slots, had $slots" }
        val total = lines.sumOf { it.weight }
        require(total == slots) { "The lines' weights add up to $total, not the table's $slots slots" }
    }

    /** One draw: the item of the line it lands on, rolling a line's table in turn; null for nothing. */
    fun roll(random: GameRandom): DropItem? {
        var left = random.nextInt(slots)
        for (line in lines) {
            left -= line.weight
            if (left < 0) return line.table?.roll(random) ?: line.item
        }
        error("A draw of $slots slots landed on no line")
    }
}

/** A drop rolled on its own after the main table, as the Wiki's tertiary drops are: 1 in [denominator] kills. */
class TertiaryDrop(val item: DropItem, val denominator: Int) {
    init {
        require(denominator >= 1) { "Bad tertiary rate 1/$denominator" }
    }
}

/**
 * What one kill of an NPC drops: every [always] item, one roll on [table] (OSRS Wiki, Drop rate: the main table is
 * rolled once), and each [tertiary] drop on its own.
 */
class NpcDropsDef(val always: List<DropItem>, val table: DropTable?, val tertiary: List<TertiaryDrop>, val source: String) {
    /** Whether a kill can give nothing at all, as a rat's does. */
    val isEmpty: Boolean get() = always.isEmpty() && table == null && tertiary.isEmpty()

    /**
     * The items one kill gives, in the order they are rolled, so a test can replay the draws: the main table's
     * draw, then a ranged count's, then each tertiary's. Items of no count (a nothing line) are left out.
     */
    fun roll(random: GameRandom): List<Drop> {
        val drops = ArrayList<Drop>()
        for (item in always) drops += Drop(item.type, item.count(random))
        table?.roll(random)?.let { drops += Drop(it.type, it.count(random)) }
        for (extra in tertiary) if (random.nextInt(extra.denominator) == 0) drops += Drop(extra.item.type, extra.item.count(random))
        return drops
    }
}

/** [count] of [type] on the ground after a kill. */
data class Drop(val type: ObjType, val count: Int)

/**
 * The drop tables of NPCs by type, from [RESOURCE]: the OSRS Wiki's tables as weights of 128 (or whatever the Wiki's
 * denominator is), with the always drops and the tertiary drops beside them. Items are given by id and checked against
 * the cache's name, so a wrong id shows in the deploy log rather than as the wrong item on the floor.
 */
class NpcDrops(private val byType: Map<Int, NpcDropsDef>, /** What the loader warned about, for tests. */ val warnings: List<String> = emptyList()) {
    operator fun get(type: NpcType): NpcDropsDef? = byType[type.id]

    operator fun get(typeId: Int): NpcDropsDef? = byType[typeId]

    val size: Int get() = byType.size

    companion object {
        val NONE = NpcDrops(emptyMap())

        const val RESOURCE = "/npc-drops.json"

        fun load(types: NpcTypes, objTypes: ObjTypes): NpcDrops {
            val text = NpcDrops::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            return parse(text, types, objTypes)
        }

        /**
         * Reads the drop file: `tables` names shared tables (the herb table) that a line may refer to by `table`;
         * each `npcs` entry gives `always` items, a `table` of `slots` and `lines` (each an `item` with `count` or
         * `min`/`max`, a `table` name, or `nothing`), and `tertiary` drops with a `chance` such as "1/128". `npcs`
         * takes type ids, gameval names or menu names, as spawn files do; an NPC the cache doesn't have is skipped
         * with a warning. An item is an id plus its `name`: a name the cache doesn't agree with is warned about, and
         * an item the cache doesn't have at all leaves its NPCs without drops, with a warning, rather than stopping
         * the server (a test cache has few items).
         */
        fun parse(text: String, types: NpcTypes, objTypes: ObjTypes, fileName: String = RESOURCE): NpcDrops {
            val root = Json.parseToJsonElement(text).jsonObject
            val shared = HashMap<String, DropTable>()
            val warnings = ArrayList<String>()
            // The names of the tables and entries whose items the cache lacks; what uses them is skipped.
            val missingItems = ArrayList<String>()
            fun item(entry: JsonObject): DropItem {
                val id = entry.required("item").jsonPrimitive.int
                val name = entry.required("name").jsonPrimitive.content
                val type = objTypes[id] ?: throw MissingItem("no item $id ($name) in the cache")
                if (!type.name.equals(name, ignoreCase = true)) warnings += "$id is ${type.name}, not $name"
                val min = entry["min"]?.jsonPrimitive?.int ?: entry["count"]?.jsonPrimitive?.int ?: 1
                val max = entry["max"]?.jsonPrimitive?.int ?: min
                return DropItem(type, min, max)
            }
            fun table(obj: JsonObject): DropTable {
                val lines = obj.required("lines").jsonArray.mapIndexed { i, element ->
                    val line = element.jsonObject
                    try {
                        val weight = line.required("weight").jsonPrimitive.int
                        when {
                            line["nothing"]?.jsonPrimitive?.booleanOrNull == true -> DropSlot(weight)
                            line["table"] != null -> {
                                val name = line["table"]!!.jsonPrimitive.content
                                if (name in missingItems) throw MissingItem("table $name has items the cache lacks")
                                DropSlot(weight, table = shared[name] ?: throw IllegalArgumentException("no table called $name"))
                            }
                            else -> DropSlot(weight, item = item(line))
                        }
                    } catch (e: MissingItem) {
                        throw MissingItem("line ${i + 1}: ${e.message}")
                    } catch (e: IllegalArgumentException) {
                        throw IllegalArgumentException("line ${i + 1}: ${e.message}", e)
                    }
                }
                return DropTable(obj.required("slots").jsonPrimitive.int, lines)
            }
            root["tables"]?.jsonObject?.forEach { (name, element) ->
                try {
                    shared[name] = table(element.jsonObject)
                } catch (e: MissingItem) {
                    missingItems += name
                    warnings += "table $name: ${e.message}"
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("$fileName: table $name: ${e.message}", e)
                }
            }
            val entries = root["npcs"]?.jsonArray ?: throw IllegalArgumentException("$fileName: no \"npcs\" list")
            val byType = HashMap<Int, NpcDropsDef>()
            val unknown = ArrayList<String>()
            entries.forEachIndexed { i, element ->
                try {
                    val entry = element.jsonObject
                    val names = entry.required("npcs").jsonArray.map { it.jsonPrimitive.content }
                    val def = try {
                        NpcDropsDef(
                            always = entry["always"]?.jsonArray?.map { item(it.jsonObject) } ?: emptyList(),
                            table = entry["table"]?.let { table(it.jsonObject) },
                            tertiary = entry["tertiary"]?.jsonArray?.map { tertiary(it.jsonObject, ::item) } ?: emptyList(),
                            source = entry.required("source").jsonPrimitive.content.ifBlank { throw IllegalArgumentException("blank source") },
                        )
                    } catch (e: MissingItem) {
                        warnings += "${names.joinToString()} drop nothing: ${e.message}"
                        return@forEachIndexed
                    }
                    for (name in names) {
                        val type = types.lookup(name)
                        if (type == null) {
                            unknown += name
                            continue
                        }
                        require(type.id !in byType) { "${type.name} (${type.id}) is listed twice" }
                        byType[type.id] = def
                    }
                } catch (e: Exception) {
                    throw IllegalArgumentException("$fileName: entry ${i + 1}: ${e.message}", e)
                }
            }
            if (unknown.isNotEmpty()) logger.warn { "$fileName: skipped NPCs the cache doesn't have: ${unknown.joinToString()}" }
            for (warning in warnings) logger.warn { "$fileName: $warning" }
            return NpcDrops(byType, unknown.map { "no NPC called $it" } + warnings)
        }

        private fun tertiary(entry: JsonObject, item: (JsonObject) -> DropItem): TertiaryDrop {
            val chance = entry.required("chance").jsonPrimitive.content
            val parts = chance.split('/')
            val denominator = parts.getOrNull(1)?.trim()?.toIntOrNull()
            require(parts.size == 2 && parts[0].trim() == "1" && denominator != null) { "a tertiary chance is \"1/N\", had \"$chance\"" }
            return TertiaryDrop(item(entry), denominator)
        }

        private fun JsonObject.required(key: String): JsonElement = get(key) ?: throw IllegalArgumentException("no \"$key\"")

        /** An item the cache lacks: the entry is skipped, not the whole file. */
        private class MissingItem(message: String) : RuntimeException(message)
    }
}
