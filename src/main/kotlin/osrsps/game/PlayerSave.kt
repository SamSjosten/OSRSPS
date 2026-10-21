package osrsps.game

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private val logger = KotlinLogging.logger {}

/**
 * What a player keeps between sessions. Scripts, open interfaces and the route being walked are not kept: they are
 * gone after a logout on live too, apart from some timers (poison, farming) that come with the content that has them.
 */
class PlayerSave(
    val x: Int,
    val z: Int,
    val level: Int,
    val runEnergy: Int,
    /** Varps that aren't 0, by id. */
    val varps: Map<Int, Int>,
    val skills: Map<Skill, SkillSave>,
    /** The inventory's filled slots, by slot. */
    val inventory: Map<Int, InvObj> = emptyMap(),
    /** What is worn, by [WearPos]. */
    val equipment: Map<Int, InvObj> = emptyMap(),
) {
    class SkillSave(val tenths: Int, val currentLevel: Int)

    /** Gives [player] this save's state. The player must be new: varps the save doesn't name are left as they are. */
    fun applyTo(player: Player) {
        player.x = x
        player.z = z
        player.level = level
        player.buildAreaZoneX = x shr 3
        player.buildAreaZoneZ = z shr 3
        player.runEnergy = runEnergy
        for ((varp, value) in varps) {
            if (varp >= player.vars.types.varpCount) {
                // A revision can drop varps; losing one beats refusing the login.
                logger.warn { "Dropping saved varp $varp of ${player.username}: the cache has ${player.vars.types.varpCount}" }
                continue
            }
            player.vars[varp] = value
        }
        for ((skill, save) in skills) {
            player.skills.setTenths(skill, save.tenths)
            player.skills.setCurrentLevel(skill, save.currentLevel)
        }
        // Item ids aren't checked against the cache: live doesn't remove items between revisions.
        for ((slot, obj) in inventory) player.inventory[slot] = obj
        // Not checked against levels either: a save is what the player had, even if something has changed since.
        for ((slot, obj) in equipment) player.equipment[slot] = obj
    }

    fun toJson(username: String): String {
        val json = buildJsonObject {
            put("version", VERSION)
            // For whoever opens the file; the file name is what finds it.
            put("username", username)
            putJsonObject("position") {
                put("x", x)
                put("z", z)
                put("level", level)
            }
            put("runEnergy", runEnergy)
            putJsonObject("skills") {
                for ((skill, save) in skills) {
                    putJsonObject(skill.name.lowercase()) {
                        put("xpTenths", save.tenths)
                        put("currentLevel", save.currentLevel)
                    }
                }
            }
            putJsonObject("varps") {
                for ((varp, value) in varps.toSortedMap()) put(varp.toString(), value)
            }
            putJsonArray("inventory") { putObjs(inventory) }
            putJsonArray("equipment") { putObjs(equipment) }
        }
        return format.encodeToString(JsonObject.serializer(), json) + "\n"
    }

    companion object {
        /** Raised when the format changes in a way older saves can't be read as. */
        const val VERSION = 1

        private val format = Json { prettyPrint = true }

        fun of(player: Player): PlayerSave {
            val varps = LinkedHashMap<Int, Int>()
            player.vars.forEachNonZero { varp, value -> varps[varp] = value }
            val skills = Skill.entries.associateWith { SkillSave(player.skills.tenths(it), player.skills.currentLevel(it)) }
            val inventory = player.inventory.contents().toMap()
            val equipment = player.equipment.contents().toMap()
            return PlayerSave(player.x, player.z, player.level, player.runEnergy, varps, skills, inventory, equipment)
        }

        /** Reads a save written by [toJson]. Throws [IllegalArgumentException] if it can't be used as it is. */
        fun fromJson(text: String): PlayerSave {
            val root = try {
                Json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                throw IllegalArgumentException("Not a JSON object: ${e.message}", e)
            }
            try {
                val version = root.int("version")
                require(version in 1..VERSION) { "Save version $version is newer than this server's $VERSION" }
                val position = root.getValue("position").jsonObject
                val x = position.int("x")
                val z = position.int("z")
                val level = position.int("level")
                require(x in 0..MAX_COORD && z in 0..MAX_COORD && level in 0..3) { "No such tile $x, $z, $level" }
                val runEnergy = root.int("runEnergy")
                require(runEnergy in 0..Movement.MAX_ENERGY) { "Run energy $runEnergy is out of range" }
                val skills = LinkedHashMap<Skill, SkillSave>()
                for ((name, value) in root.getValue("skills").jsonObject) {
                    val skill = Skill.entries.firstOrNull { it.name.lowercase() == name } ?: throw IllegalArgumentException("No skill '$name'")
                    val entry = value.jsonObject
                    val tenths = entry.int("xpTenths")
                    val currentLevel = entry.int("currentLevel")
                    require(tenths in 0..Experience.MAX_TENTHS) { "$name XP $tenths is out of range" }
                    require(currentLevel in 0..PlayerSkills.MAX_CURRENT_LEVEL) { "$name level $currentLevel is out of range" }
                    skills[skill] = SkillSave(tenths, currentLevel)
                }
                val varps = LinkedHashMap<Int, Int>()
                for ((id, value) in root.getValue("varps").jsonObject) {
                    val varp = id.toIntOrNull() ?: throw IllegalArgumentException("Varp id '$id' is not a number")
                    require(varp >= 0) { "Varp id $varp is negative" }
                    varps[varp] = value.jsonPrimitive.int
                }
                // Optional: saves written before the inventory or equipment existed have none.
                val inventory = root.objs("inventory", "Inventory", Inventory.INV_CAPACITY)
                val equipment = root.objs("equipment", "Equipment", WearPos.COUNT)
                return PlayerSave(x, z, level, runEnergy, varps, skills, inventory, equipment)
            } catch (e: IllegalArgumentException) {
                throw e
            } catch (e: Exception) {
                // A missing key or a value of the wrong type.
                throw IllegalArgumentException(e.message, e)
            }
        }

        private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int

        private fun JsonArrayBuilder.putObjs(objs: Map<Int, InvObj>) {
            for ((slot, obj) in objs.toSortedMap()) {
                addJsonObject {
                    put("slot", slot)
                    put("id", obj.id)
                    put("count", obj.count)
                }
            }
        }

        /** The {slot, id, count} entries under [key], none if it is missing. */
        private fun JsonObject.objs(key: String, what: String, capacity: Int): Map<Int, InvObj> {
            val objs = LinkedHashMap<Int, InvObj>()
            for (element in this[key]?.jsonArray.orEmpty()) {
                val entry = element.jsonObject
                val slot = entry.int("slot")
                require(slot in 0 until capacity) { "$what slot $slot is out of range" }
                require(slot !in objs) { "$what slot $slot is saved twice" }
                objs[slot] = InvObj(entry.int("id"), entry.int("count"))
            }
            return objs
        }

        private const val MAX_COORD = 16383
    }
}
