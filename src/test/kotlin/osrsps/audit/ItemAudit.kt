package osrsps.audit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.Equipment
import osrsps.game.EquipmentBonuses
import osrsps.game.ItemWeights
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Weapons
import osrsps.game.WearPos
import osrsps.game.WearRequirements
import kotlin.math.roundToInt

/**
 * One item's worn stats as the OSRS Wiki's DPS calculator has them (weirdgloop/osrs-dps-calc, cdn/json/equipment.json):
 * [bonuses] in [EquipmentBonuses] order, magic damage in tenths of a percent as the cache keeps it. [category] is the
 * Wiki's weapon type ("Slash Sword"), empty for anything but a weapon. [weight] is in grams, the worn weight for gear
 * that weighs less worn.
 */
class WikiEquipment(
    val id: Int,
    val name: String,
    val version: String,
    val slot: Int,
    val speed: Int,
    val category: String,
    val twoHanded: Boolean,
    val weight: Int,
    val bonuses: IntArray,
)

/** One item as the Wiki-derived osrsreboxed database has it (items-complete.json, last updated 2025-01-07). */
class WikiItem(
    val id: Int,
    val name: String,
    val members: Boolean,
    val tradeableOnGe: Boolean,
    val cost: Int,
    /** In grams; null when the database gives none. */
    val weight: Int?,
    val equipable: Boolean,
    /** Skill name ("attack", "combat") to level. */
    val requirements: Map<String, Int>,
)

/**
 * A stat where the cache, or the server's reading of it, and the Wiki disagree. [versioned] is set for one of several
 * versions on one Wiki page (broken, locked, charged), where the calculator often repeats the main version's stats.
 */
class Finding(val check: String, val id: Int, val name: String, val ours: String, val wiki: String, val versioned: Boolean)

/**
 * Compares every item the server knows with the Wiki: worn bonuses, attack speed, slot, two-handedness, weapon type
 * and weight against the DPS calculator's data, and requirements, value, members and Grand Exchange flags and weight
 * against osrsreboxed. "Ours" is what the server uses now: the cache, [ItemWeights] for weight (stackables weigh nothing, gear its worn weight) and [WearRequirements] for requirements. Nothing here changes the server; the findings are for the PRs that fix them.
 */
class ItemAudit(
    private val objs: ObjTypes,
    private val equipment: List<WikiEquipment>,
    private val items: List<WikiItem>,
    private val requirements: WearRequirements,
) {
    val findings = ArrayList<Finding>()

    private val weights = ItemWeights(objs)

    /** How many items each check compared. */
    val compared = LinkedHashMap<String, Int>()

    /** Items one source has and the other doesn't, by what is missing where. */
    val missing = LinkedHashMap<String, MutableList<Int>>()

    fun run(): ItemAudit {
        auditEquipment()
        auditItems()
        val wikiWearable = equipment.map { it.id }.toSet()
        missing["Wearable in the cache, not in the DPS calculator"] = (0 until objs.size)
            .mapNotNull { objs[it] }
            .filter { it.wearable && !it.isCert && !it.isPlaceholder && it.name != ObjType.DEFAULT_NAME && it.id !in wikiWearable }
            .map { it.id }
            .toMutableList()
        return this
    }

    private fun auditEquipment() {
        for (wiki in equipment) {
            val obj = objs[wiki.id]
            if (obj == null) {
                missing.getOrPut("In the DPS calculator, not in the cache") { ArrayList() } += wiki.id
                continue
            }
            val name = label(wiki.name, wiki.version)
            val versioned = wiki.version.isNotEmpty()
            count("Bonuses")
            for ((index, param) in EquipmentBonuses.PARAMS.withIndex()) {
                // Ranged strength is read from two params, as the server does.
                val ours = if (index == EquipmentBonuses.RANGED_STRENGTH) EquipmentBonuses.rangedStrength(obj) else obj.intParam(param)
                if (ours != wiki.bonuses[index]) find("Bonus: ${BONUS_NAMES[index]}", obj, name, ours, wiki.bonuses[index], versioned)
            }
            count("Slot")
            if (obj.wearpos1 != wiki.slot) find("Slot", obj, name, slotName(obj.wearpos1), slotName(wiki.slot), versioned)
            if (wiki.slot != WearPos.WEAPON) continue
            count("Weapon speed, hands, type")
            val speed = obj.intParam(Equipment.PARAM_ATTACK_SPEED, Equipment.UNARMED_ATTACK_SPEED)
            if (wiki.speed > 0 && speed != wiki.speed) find("Attack speed", obj, name, speed, wiki.speed, versioned)
            if (obj.twoHanded != wiki.twoHanded) find("Two-handed", obj, name, obj.twoHanded, wiki.twoHanded, versioned)
            val type = Weapons.categoryName(Weapons.category(obj))
            if (wiki.category.isNotEmpty() && !sameType(type, wiki.category)) find("Weapon type", obj, name, type, wiki.category, versioned)
        }
        for (wiki in equipment) {
            val obj = objs[wiki.id] ?: continue
            count("Weight (DPS calculator)")
            // The calculator gives the worn weight; only a gap of more than a gram counts, as it rounds some weights.
            val ours = weights.worn(obj)
            if (kotlin.math.abs(ours - wiki.weight) > 1) {
                find("Weight (DPS calculator)", obj, label(wiki.name, wiki.version), grams(ours), grams(wiki.weight), wiki.version.isNotEmpty())
            }
        }
    }

    private fun auditItems() {
        for (wiki in items) {
            val obj = objs[wiki.id]
            if (obj == null) {
                missing.getOrPut("In osrsreboxed, not in the cache") { ArrayList() } += wiki.id
                continue
            }
            if (obj.isCert || obj.isPlaceholder) continue
            count("Value, members, GE")
            if (obj.cost != wiki.cost) find("Value", obj, wiki.name, obj.cost, wiki.cost)
            if (obj.members != wiki.members) find("Members", obj, wiki.name, obj.members, wiki.members)
            if (obj.tradeable != wiki.tradeableOnGe) find("GE-tradeable", obj, wiki.name, obj.tradeable, wiki.tradeableOnGe)
            if (wiki.weight != null) {
                count("Weight (osrsreboxed)")
                val ours = weights.carried(obj)
                if (kotlin.math.abs(ours - wiki.weight) > 1) {
                    find("Weight (osrsreboxed)", obj, wiki.name, grams(ours), grams(wiki.weight))
                }
            }
            if (!wiki.equipable) continue
            count("Requirements")
            val ours = requirements[obj].associate { (it.skill?.name?.lowercase() ?: "combat") to it.level }
            val theirs = wiki.requirements.filterValues { it > 1 }
            if (ours != theirs) find("Requirements", obj, wiki.name, reqs(ours), reqs(theirs))
        }
    }

    private fun count(check: String) {
        compared[check] = (compared[check] ?: 0) + 1
    }

    private fun find(check: String, obj: ObjType, name: String, ours: Any, wiki: Any, versioned: Boolean = false) {
        findings += Finding(check, obj.id, name, ours.toString(), wiki.toString(), versioned)
    }

    /** The audit as Markdown: a table of counts, then every finding by check. */
    fun report(source: String): String = buildString {
        appendLine("# Item audit")
        appendLine()
        appendLine(source)
        appendLine()
        appendLine("Of the disagreements, \"versions\" are on one of several versions of a Wiki page (broken, locked, charged).")
        appendLine()
        appendLine("| Check | Items compared | Findings | Of them on versions |")
        appendLine("| --- | ---: | ---: | ---: |")
        val byCheck = findings.groupBy { it.check }
        for ((check, count) in compared) {
            val disagree = byCheck.filterKeys { it == check || belongs(it, check) }.values.flatten()
            appendLine("| $check | $count | ${disagree.size} | ${disagree.count { it.versioned }} |")
        }
        appendLine()
        for ((what, ids) in missing) appendLine("- $what: ${ids.size}")
        for ((check, list) in byCheck.toSortedMap()) {
            appendLine()
            appendLine("## $check (${list.size})")
            appendLine()
            appendLine("| Id | Item | Ours | Wiki |")
            appendLine("| ---: | --- | --- | --- |")
            for (f in list.sortedWith(compareBy({ it.versioned }, { it.id }))) appendLine("| ${f.id} | ${f.name.replace("|", "\\|")} | ${f.ours} | ${f.wiki} |")
        }
    }

    /** Requirement rows for the PR that replaces the metal-tier rule: the Wiki's levels for every wearable item. */
    fun requirementCandidates(): String = buildString {
        appendLine("# id\tname\trequirements (skill=level, from osrsreboxed)")
        for (wiki in items.sortedBy { it.id }) {
            if (!wiki.equipable) continue
            val reqs = wiki.requirements.filterValues { it > 1 }
            if (reqs.isNotEmpty()) appendLine("${wiki.id}\t${wiki.name}\t${reqs(reqs)}")
        }
    }

    private fun belongs(check: String, group: String) = when (group) {
        "Bonuses" -> check.startsWith("Bonus:")
        "Weapon speed, hands, type" -> check in setOf("Attack speed", "Two-handed", "Weapon type")
        "Value, members, GE" -> check in setOf("Value", "Members", "GE-tradeable")
        else -> false
    }

    companion object {
        val BONUS_NAMES = listOf(
            "stab attack", "slash attack", "crush attack", "magic attack", "ranged attack", "stab defence",
            "slash defence", "crush defence", "magic defence", "ranged defence", "melee strength", "ranged strength",
            "magic damage", "prayer",
        )

        private val SLOTS = mapOf(
            "head" to WearPos.HEAD, "cape" to WearPos.CAPE, "neck" to WearPos.NECK, "weapon" to WearPos.WEAPON,
            "body" to WearPos.BODY, "shield" to WearPos.SHIELD, "legs" to WearPos.LEGS, "hands" to WearPos.HANDS,
            "feet" to WearPos.FEET, "ring" to WearPos.RING, "ammo" to WearPos.AMMO,
        )

        private fun slotName(slot: Int) = SLOTS.entries.firstOrNull { it.value == slot }?.key ?: "none ($slot)"

        private fun label(name: String, version: String) = if (version.isEmpty()) name else "$name ($version)"

        private fun grams(weight: Int) = "%.3f kg".format(weight / 1000.0)

        private fun reqs(reqs: Map<String, Int>) =
            if (reqs.isEmpty()) "none" else reqs.entries.sortedBy { it.key }.joinToString(", ") { "${it.key}=${it.value}" }

        /** The Wiki writes weapon types in title case ("Slash Sword"), the combat tab names in sentence case. */
        private fun sameType(ours: String, wiki: String) =
            ours.equals(wiki, ignoreCase = true) || (ours == "Chinchompa" && wiki == "Chinchompas")

        /** Parses the DPS calculator's equipment.json. */
        fun parseEquipment(text: String): List<WikiEquipment> = Json.parseToJsonElement(text).jsonArray.mapNotNull { element ->
            val o = element.jsonObject
            val slot = SLOTS[o.string("slot")] ?: return@mapNotNull null
            val off = o.getValue("offensive").jsonObject
            val def = o.getValue("defensive").jsonObject
            val bon = o.getValue("bonuses").jsonObject
            val bonuses = intArrayOf(
                off.int("stab"), off.int("slash"), off.int("crush"), off.int("magic"), off.int("ranged"),
                def.int("stab"), def.int("slash"), def.int("crush"), def.int("magic"), def.int("ranged"),
                bon.int("str"), bon.int("ranged_str"), bon.int("magic_str"), bon.int("prayer"),
            )
            WikiEquipment(
                id = o.int("id"), name = o.string("name"), version = o.string("version"), slot = slot,
                speed = o.int("speed"), category = o.string("category"), twoHanded = o.getValue("isTwoHanded").jsonPrimitive.boolean,
                weight = ((o["weight"]?.jsonPrimitive?.doubleOrNull ?: 0.0) * 1000).roundToInt(), bonuses = bonuses,
            )
        }

        /** Parses osrsreboxed's items-complete.json, an object of items by id. Skips its duplicate entries. */
        fun parseItems(text: String): List<WikiItem> = Json.parseToJsonElement(text).jsonObject.values.mapNotNull { element ->
            val o = element.jsonObject
            if (o.bool("duplicate") == true) return@mapNotNull null
            val requirements = (o["equipment"] as? JsonObject)?.get("requirements")
                ?.let { it as? JsonObject }
                ?.mapValues { it.value.jsonPrimitive.int }
                .orEmpty()
            WikiItem(
                id = o.int("id"), name = o.string("name"), members = o.bool("members") == true,
                tradeableOnGe = o.bool("tradeable_on_ge") == true, cost = o.int("cost"),
                weight = o["weight"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.double?.let { (it * 1000).roundToInt() },
                equipable = o.bool("equipable_by_player") == true, requirements = requirements,
            )
        }

        private fun JsonObject.string(key: String) = get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.content ?: ""

        private fun JsonObject.int(key: String) = get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull ?: 0

        private fun JsonObject.bool(key: String) = get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.booleanOrNull
    }
}
