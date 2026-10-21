package osrsps.game

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A testing scenario: where to stand and what to have, set in one go by `::scenario name`. Not live content. Read from
 * JSON in the resources' `scenarios` folder, the file name being the scenario's name:
 *
 * ```
 * {
 *   "about": "Lumbridge cow field with iron gear.",
 *   "position": { "x": 3259, "z": 3263, "level": 0 },
 *   "skills": { "attack": 20, "hitpoints": 20, "strength": { "level": 20, "current": 25 } },
 *   "runEnergy": 10000, "run": true,
 *   "inventory": [ { "item": "lobster", "count": 5 } ],
 *   "equipment": [ { "item": "iron_scimitar" }, { "item": "bronze_arrow", "count": 100 } ],
 *   "varps": { "43": 1 }, "varbits": { "357": 2 }
 * }
 * ```
 *
 * Only `position` is required; other keys, such as `notes`, are ignored. A scenario is a whole loadout, so what it
 * leaves out is reset rather than kept: skills it doesn't name are a new account's (1, Hitpoints 10), run energy is
 * full, and the inventory and equipment hold only what it lists. Varps and varbits it doesn't name, and the run orb
 * unless `run` is given, are left as they are: they hold settings and the quest states every account gets.
 *
 * `item` is a gameval name (`bronze_axe`) or an id. Inventory items go in as `::item` puts them: a stackable item on one
 * stack, anything else one per slot. A skill's level is its XP level; `current` sets a boost or drain.
 */
class Scenario(
    val name: String,
    val about: String?,
    val x: Int,
    val z: Int,
    val level: Int,
    val skills: Map<Skill, ScenarioSkill>,
    val runEnergy: Int,
    /** The run orb, or null to leave it as the player has it. */
    val run: Boolean?,
    val inventory: List<ScenarioObj>,
    val equipment: List<ScenarioObj>,
    val varps: Map<Int, Int>,
    val varbits: Map<Int, Int>,
) {
    class ScenarioSkill(val level: Int, val current: Int)

    /** An item as the file names it; [ObjTypes] turns it into a type when the scenario starts. */
    class ScenarioObj(val item: String, val count: Int)

    /** The scenario's items as slot contents, worked out before anything about the player changes. */
    class Loadout(val inventory: Map<Int, InvObj>, val equipment: Map<Int, InvObj>)

    /**
     * Finds the scenario's items in [types] and puts them in slots. Throws [IllegalArgumentException] naming the
     * problem: an item the cache doesn't have, too much for the inventory, or two items for one worn slot. Level
     * requirements aren't checked: a scenario can hand out gear its stats couldn't wear, which is sometimes the test.
     */
    fun loadout(types: ObjTypes): Loadout {
        val inventory = Inventory(Inventory.INV, Inventory.INV_CAPACITY)
        for (obj in this.inventory) {
            val type = find(types, obj.item)
            val added = inventory.add(type, obj.count)
            require(added == obj.count) { "${obj.count} x ${type.name} don't fit in the inventory" }
        }
        val equipment = LinkedHashMap<Int, InvObj>()
        for (obj in this.equipment) {
            val type = find(types, obj.item)
            require(type.wearable) { "${type.name} (${type.id}) can't be worn" }
            require(obj.count == 1 || type.stackable) { "Only one ${type.name} can be worn" }
            val wearpos = type.wearpos1
            val taken = equipment.put(wearpos, InvObj(type.id, obj.count))
            if (taken != null) throw IllegalArgumentException("${types[taken.id]?.name} and ${type.name} both go in worn slot $wearpos")
        }
        val weapon = equipment[WearPos.WEAPON]?.let { types[it.id] }
        if (weapon != null && weapon.twoHanded && WearPos.SHIELD in equipment) {
            throw IllegalArgumentException("${weapon.name} is two-handed, so no shield can be worn with it")
        }
        return Loadout(inventory.contents().toMap(), equipment)
    }

    private fun find(types: ObjTypes, item: String): ObjType {
        val type = item.toIntOrNull()?.let { types[it] } ?: types.find(item)
        requireNotNull(type) { "The cache has no item '$item'" }
        return type
    }

    companion object {
        /** Lower case letters, digits and dashes, as the file names are typed after `::scenario`. */
        private val NAME = Regex("[a-z0-9][a-z0-9-]*")

        /** Reads one scenario file. Throws [IllegalArgumentException] naming [name] and what is wrong with it. */
        fun parse(name: String, text: String): Scenario {
            require(NAME.matches(name)) { "Scenario name '$name' must be lower case letters, digits and dashes" }
            try {
                val root = Json.parseToJsonElement(text).jsonObject
                val position = root.required("position").jsonObject
                val x = position.required("x").jsonPrimitive.int
                val z = position.required("z").jsonPrimitive.int
                val level = position["level"]?.jsonPrimitive?.int ?: 0
                require(x in 0 until MAX_COORD && z in 0 until MAX_COORD && level in 0..3) { "no such tile $x, $z, $level" }

                val skills = LinkedHashMap<Skill, ScenarioSkill>()
                for ((key, value) in root["skills"]?.jsonObject.orEmpty()) {
                    val skill = Skill.find(key) ?: throw IllegalArgumentException("no skill '$key'")
                    require(skill !in skills) { "${skill.displayName} is set twice" }
                    skills[skill] = skill(skill, value)
                }

                val runEnergy = root["runEnergy"]?.jsonPrimitive?.int ?: Movement.MAX_ENERGY
                require(runEnergy in 0..Movement.MAX_ENERGY) { "runEnergy must be 0 to ${Movement.MAX_ENERGY}" }
                val run = root["run"]?.jsonPrimitive?.boolean

                val inventory = objs(root, "inventory")
                val equipment = objs(root, "equipment")
                val varps = ids(root, "varps")
                val varbits = ids(root, "varbits")
                val about = (root["about"] as? JsonPrimitive)?.content
                return Scenario(name, about, x, z, level, skills, runEnergy, run, inventory, equipment, varps, varbits)
            } catch (e: Exception) {
                throw IllegalArgumentException("Scenario $name: ${e.message}", e)
            }
        }

        private fun skill(skill: Skill, value: JsonElement): ScenarioSkill {
            val entry = value as? JsonObject
            val level = (entry?.required("level") ?: value).jsonPrimitive.int
            require(level in 1..Experience.MAX_LEVEL) { "${skill.displayName} level must be 1 to ${Experience.MAX_LEVEL}" }
            val current = entry?.get("current")?.jsonPrimitive?.int ?: level
            require(current in 0..PlayerSkills.MAX_CURRENT_LEVEL) { "${skill.displayName} current level must be 0 to ${PlayerSkills.MAX_CURRENT_LEVEL}" }
            return ScenarioSkill(level, current)
        }

        private fun objs(root: JsonObject, key: String): List<ScenarioObj> =
            root[key]?.jsonArray.orEmpty().mapIndexed { i, element ->
                val entry = element.jsonObject
                val item = entry.required("item").jsonPrimitive.content
                val count = entry["count"]?.jsonPrimitive?.int ?: 1
                require(count >= 1) { "$key entry ${i + 1}: count must be at least 1" }
                ScenarioObj(item, count)
            }

        /** A `{ "id": value }` object; whether the ids exist is checked against the cache when the scenario starts. */
        private fun ids(root: JsonObject, key: String): Map<Int, Int> =
            root[key]?.jsonObject.orEmpty().entries.associate { (id, value) ->
                val number = id.toIntOrNull()?.takeIf { it >= 0 } ?: throw IllegalArgumentException("$key id '$id' is not a number")
                number to value.jsonPrimitive.int
            }

        private fun JsonObject.required(key: String): JsonElement = this[key] ?: throw IllegalArgumentException("no \"$key\"")

        /** Coordinates are 14 bits in the client's coord grid. */
        private const val MAX_COORD = 1 shl 14
    }
}

/** The scenarios `::scenario` can start, by name. */
class Scenarios(scenarios: Collection<Scenario>) {
    private val byName = scenarios.associateBy { it.name }.toSortedMap()

    val names: Set<String> get() = byName.keys

    operator fun get(name: String): Scenario? = byName[name.lowercase()]

    companion object {
        val NONE = Scenarios(emptyList())

        /** The scenario files the server loads. Add a new file's name here. */
        val FILES = listOf("lumbridge-cows")

        /** Reads every file in [FILES] from the resources. A file that is missing or malformed stops the server. */
        fun load(): Scenarios = Scenarios(
            FILES.map { name ->
                val text = Scenarios::class.java.getResource("/scenarios/$name.json")?.readText()
                    ?: error("No scenario file $name.json in the resources")
                Scenario.parse(name, text)
            },
        )
    }
}
