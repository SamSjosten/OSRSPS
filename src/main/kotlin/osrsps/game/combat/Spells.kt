package osrsps.game.combat

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.math.abs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.SpotanimNames

private val logger = KotlinLogging.logger {}

/** [count] of the rune with item id [rune], as a spell needs them. */
data class RuneCost(val rune: Int, val count: Int)

/**
 * A combat spell on the standard spellbook. What the cache's spell obj has: its [name], the spellbook component it is
 * cast from, the Magic [level] it needs, its base [maxHit] and its [runes]. What the data file adds: the base XP in
 * tenths ([xpTenths]), the casting animation (with and without a staff, -1 for none) and its graphics: [casting] on the
 * player, the projectile it flies as ([travel]) and [impact] on the target (-1 for none), and its key in the cache's
 * enum 1986 ([autocast]), which numbers the spells of the autocast menu (-1 for a spell that can't be autocast), its
 * [element], which a monster can be weak to (null for none), the base XP of a splash when it differs ([splashXpTenths]),
 * and the ticks a hit freezes the target for ([freezeTicks], 0 for none). A spell that [holds] (Bind, Snare and
 * Entangle, whose descriptions say "Holds your opponent") can't be cast on a target that is frozen or immune to it.
 * The cache has no max hit for the hold spells; the data file gives theirs.
 */
data class CombatSpell(
    val obj: Int,
    val name: String,
    val component: Int,
    val level: Int,
    val maxHit: Int,
    val runes: List<RuneCost>,
    val xpTenths: Int,
    val anim: Int,
    val staffAnim: Int,
    val casting: Int,
    val travel: Int,
    val impact: Int,
    val autocast: Int = -1,
    val element: Element? = null,
    val splashXpTenths: Int = xpTenths,
    val freezeTicks: Int = 0,
    val holds: Boolean = false,
)

/**
 * The combat spells by spellbook component, and the staves that stand in for runes (OSRS Wiki, Elemental staves: a staff
 * of air "provides an infinite number of air runes"; battlestaves, mystic staves and the combination staves likewise).
 * Combination runes, the rune pouch and tomes come later.
 */
class Spells(
    private val byComponent: Map<Int, CombatSpell>,
    private val staves: Map<Int, Set<Int>>,
    /** The graphic on a target a spell missed (OSRS Wiki, Splashing); -1 for none. */
    val splash: Int = -1,
) {
    /** The spell cast from component [component] of the standard spellbook, or null for one that isn't a combat spell here. */
    fun spell(component: Int): CombatSpell? = byComponent[component]

    private val byAutocast: Map<Int, CombatSpell> = byComponent.values.filter { it.autocast >= 0 }.associateBy { it.autocast }

    /** The spell the autocast menu numbers [key] (its enum 1986 key), or null for none here. */
    fun autocast(key: Int): CombatSpell? = byAutocast[key]

    /** The runes a staff (by item id) provides as many of as are needed; empty for anything else. */
    fun runesFrom(staff: Int?): Set<Int> = staff?.let { staves[it] }.orEmpty()

    val all: Collection<CombatSpell> get() = byComponent.values

    val size: Int get() = byComponent.size

    companion object {
        val NONE = Spells(emptyMap(), emptyMap())

        const val RESOURCE = "/spells.json"

        /** The standard spellbook (gameval `magic_spellbook`). */
        const val SPELLBOOK = 218

        /** Spell obj params in the 241 cache: name, level, the packed spellbook component, base max hit. */
        const val PARAM_NAME = 601
        const val PARAM_LEVEL = 604
        const val PARAM_COMPONENT = 596
        const val PARAM_MAX_HIT = 1935

        /** Rune and count params, in pairs: 365 the first rune and 366 how many, then 367/368 and 369/370. */
        val PARAM_RUNES = listOf(365 to 366, 367 to 368, 369 to 370)

        /** Reads [RESOURCE]; see [parse]. */
        fun load(objTypes: ObjTypes, seqs: SeqNames, spotanims: SpotanimNames): Spells {
            val text = Spells::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            return parse(text, objTypes, seqs, spotanims)
        }

        /**
         * Parses a spells file:
         *
         * ```
         * { "splash": "failedspell_impact",
         *   "spells": [ { "spell": "13_fire_strike", "autocast": 4, "element": "fire", "xp": 11.5, "anim": "human_caststrike", "staffAnim": "human_caststrike_staff",
         *                 "casting": "firestrike_casting", "travel": "firestrike_travel", "impact": "firestrike_impact" } ],
         *   "staves": [ { "items": ["staff_of_fire", "fire_battlestaff"], "runes": ["firerune"] } ] }
         * ```
         *
         * A spell that freezes has "hold" (the hold spells) or "freeze" with its ticks, and may have "splashXp" when a
         * splash gives less, and "maxHit" when its obj has no max hit param (0 only for a spell that freezes).
         *
         * Spells, items and runes are gameval names; the rest of a spell comes from its obj's params, and a spell obj without
         * them fails the file. Animations and spotanims are gameval names too: one [seqs] or [spotanims] doesn't know
         * fails the file, unless it knows no names at all (a cache with no gameval index), when they are all left out.
         * "autocast" may be left out for a spell that can't be autocast, "element" for one of no element. A
         * spell, staff or rune the cache doesn't have is skipped with a warning, a staff of a missing rune with it.
         */
        fun parse(text: String, objTypes: ObjTypes, seqs: SeqNames, spotanims: SpotanimNames, fileName: String = RESOURCE): Spells {
            val root = Json.parseToJsonElement(text).jsonObject
            val unknown = ArrayList<String>()
            fun seq(entry: JsonObject, key: String): Int {
                val name = entry.required(key).jsonPrimitive.content
                if (seqs.isEmpty) return -1
                return seqs[name] ?: throw IllegalArgumentException("no animation called $name")
            }
            fun spotanim(entry: JsonObject, key: String): Int {
                val name = entry.required(key).jsonPrimitive.content
                if (spotanims.isEmpty) return -1
                return spotanims[name] ?: throw IllegalArgumentException("no spotanim called $name")
            }
            val byComponent = HashMap<Int, CombatSpell>()
            root.required("spells").jsonArray.forEachIndexed { i, element ->
                try {
                    val entry = element.jsonObject
                    val name = entry.required("spell").jsonPrimitive.content
                    val type = objTypes.find(name)
                    if (type == null) {
                        unknown += name
                        return@forEachIndexed
                    }
                    val spellName = type.stringParam(PARAM_NAME) ?: throw IllegalArgumentException("$name has no spell name (param $PARAM_NAME)")
                    val xpTenths = tenths(entry.required("xp").jsonPrimitive.double)
                    val hold = entry["hold"]?.jsonPrimitive?.int
                    val freeze = entry["freeze"]?.jsonPrimitive?.int
                    require(hold == null || freeze == null) { "both \"hold\" and \"freeze\"" }
                    val freezeTicks = (hold ?: freeze ?: 0).also { require(it >= 0) { "a freeze of $it ticks" } }
                    val cacheMaxHit = type.intParam(PARAM_MAX_HIT, -1)
                    val dataMaxHit = entry["maxHit"]?.jsonPrimitive?.int
                    require(cacheMaxHit < 0 || dataMaxHit == null) { "\"maxHit\" given, but the cache has $cacheMaxHit (param $PARAM_MAX_HIT)" }
                    val maxHit = if (cacheMaxHit >= 0) cacheMaxHit else dataMaxHit ?: -1
                    require(maxHit >= 1 || (maxHit == 0 && freezeTicks > 0)) { "$name has max hit $maxHit (param $PARAM_MAX_HIT, or \"maxHit\")" }
                    val spell = CombatSpell(
                        obj = type.id,
                        name = spellName,
                        component = component(type),
                        level = type.intParam(PARAM_LEVEL, -1).also { require(it in 1..99) { "$name has Magic level $it (param $PARAM_LEVEL)" } },
                        maxHit = maxHit,
                        runes = runes(type),
                        xpTenths = xpTenths,
                        anim = seq(entry, "anim"),
                        staffAnim = seq(entry, "staffAnim"),
                        casting = spotanim(entry, "casting"),
                        travel = spotanim(entry, "travel"),
                        impact = spotanim(entry, "impact"),
                        autocast = entry["autocast"]?.jsonPrimitive?.int?.also { require(it >= 1) { "autocast key $it" } } ?: -1,
                        element = entry["element"]?.jsonPrimitive?.content?.let(Element::parse),
                        splashXpTenths = entry["splashXp"]?.jsonPrimitive?.double?.let(::tenths) ?: xpTenths,
                        freezeTicks = freezeTicks,
                        holds = hold != null,
                    )
                    require(spell.component !in byComponent) { "component ${spell.component} is listed twice" }
                    require(spell.autocast < 0 || byComponent.values.none { it.autocast == spell.autocast }) { "autocast key ${spell.autocast} is listed twice" }
                    byComponent[spell.component] = spell
                } catch (e: Exception) {
                    throw IllegalArgumentException("$fileName: spells entry ${i + 1}: ${e.message}", e)
                }
            }
            val staves = HashMap<Int, Set<Int>>()
            root.required("staves").jsonArray.forEachIndexed { i, element ->
                try {
                    val entry = element.jsonObject
                    val runeNames = entry.required("runes").jsonArray.map { it.jsonPrimitive.content }
                    require(runeNames.isNotEmpty()) { "no runes" }
                    val items = entry.required("items").jsonArray.map { it.jsonPrimitive.content }
                    val runeTypes = runeNames.map { objTypes.find(it) }
                    if (null in runeTypes) {
                        // A cache without one of its runes has none of these staves either, as far as this file goes.
                        unknown += runeNames.filterIndexed { r, _ -> runeTypes[r] == null } + items
                        return@forEachIndexed
                    }
                    val runes = runeTypes.map { it!!.id }.toSet()
                    for (item in items) {
                        val type = objTypes.find(item)
                        if (type == null) {
                            unknown += item
                            continue
                        }
                        require(type.id !in staves) { "${type.name} (${type.id}) is listed twice" }
                        staves[type.id] = runes
                    }
                } catch (e: Exception) {
                    throw IllegalArgumentException("$fileName: staves entry ${i + 1}: ${e.message}", e)
                }
            }
            if (unknown.isNotEmpty()) logger.warn { "$fileName: skipped items the cache doesn't have: ${unknown.joinToString()}" }
            return Spells(byComponent, staves, spotanim(root, "splash"))
        }

        /** The spell's component of the standard spellbook, from its packed interface and component. */
        private fun component(type: ObjType): Int {
            val packed = type.intParam(PARAM_COMPONENT, -1)
            require(packed ushr 16 == SPELLBOOK) { "${type.internalName} isn't on the standard spellbook (param $PARAM_COMPONENT is $packed)" }
            return packed and 0xFFFF
        }

        private fun runes(type: ObjType): List<RuneCost> {
            val runes = PARAM_RUNES.mapNotNull { (runeParam, countParam) ->
                val rune = type.intParam(runeParam, -1)
                if (rune < 0) null else RuneCost(rune, type.intParam(countParam, 0).also { require(it >= 1) { "rune $rune costs $it" } })
            }
            require(runes.isNotEmpty()) { "${type.internalName} needs no runes (params ${PARAM_RUNES.first().first} on)" }
            return runes
        }

        private fun tenths(xp: Double): Int {
            val tenths = Math.round(xp * 10).toInt()
            require(tenths > 0 && abs(tenths - xp * 10) < 1e-6) { "xp $xp isn't a whole number of tenths" }
            return tenths
        }

        private fun JsonObject.required(key: String): JsonElement = get(key) ?: throw IllegalArgumentException("no \"$key\"")
    }
}
