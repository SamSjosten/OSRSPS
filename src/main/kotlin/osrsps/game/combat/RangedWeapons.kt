package osrsps.game.combat

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.SpotanimNames
import osrsps.game.Weapons

private val logger = KotlinLogging.logger {}

/** What a ranged weapon fires: arrows from bows, bolts from crossbows, darts from the blowpipe, and chinchompas. */
enum class AmmoKind {
    ARROWS,
    BOLTS,
    DARTS,

    /** Thrown from the weapon slot ([RangedWeapon.thrown]), and blowing up in a 3x3 area ([RangedWeapon.splash]). */
    CHINCHOMPAS,
}

/** How a weapon's accuracy and damage grow with what it is fired at. */
enum class RangedScaling {
    /** The twisted bow's: on the target's Magic ([CombatMath.twistedBowAccuracy], [CombatMath.twistedBowDamage]). */
    TWISTED_BOW,
}

/**
 * A bow or crossbow: it fires [kind] of [tier] or lower ([RangedAmmo.tier]), [shots] at a time, from up to [range]
 * tiles on accurate and rapid, with the [anim] animation (-1 for none). [scaling] changes its numbers by target,
 * [bounces] is how many more NPCs each shot can go on to in multi-way combat (the Venator bow's), and [launch] and
 * [travel] are graphics of its own that replace the ammunition's (-1 for the ammunition's).
 */
data class RangedWeapon(
    val kind: AmmoKind,
    val tier: Int,
    val range: Int,
    val shots: Int,
    val anim: Int,
    val source: String,
    val scaling: RangedScaling? = null,
    val bounces: Int = 0,
    val launch: Int = -1,
    val travel: Int = -1,
    /** Whether it fires what it holds ([Blowpipe]) rather than what is in the ammo slot. */
    val loaded: Boolean = false,
    /** How long its hits take to land: [HitDelay.BOW], or [HitDelay.THROWN] for the blowpipe and thrown weapons. */
    val delay: HitDelay = HitDelay.BOW,
    /** Whether it is its own ammunition: each throw takes one from the stack in the weapon slot. */
    val thrown: Boolean = false,
) {
    fun fires(ammo: RangedAmmo): Boolean = ammo.kind == kind && ammo.tier <= tier

    /** Whether it hits everything around its target in multi-way combat, as chinchompas do. */
    val splash: Boolean get() = kind == AmmoKind.CHINCHOMPAS
}

/**
 * Arrows or bolts: the graphic on the player as one is fired ([launch], [doubleLaunch] when two go at once; -1 for
 * none) and the projectile it flies as ([travel]).
 */
data class RangedAmmo(val kind: AmmoKind, val tier: Int, val launch: Int, val doubleLaunch: Int, val travel: Int)

/**
 * The ranged weapons and the ammunition they fire, by item id. Tiers stand for the Wiki's "up to and including" lists
 * on each weapon's page (an iron crossbow fires bronze, blurite and iron bolts); ranges are the Wiki's Attack range
 * table. Weapons that aren't here (thrown weapons other than chinchompas, crystal bows, the dragon hunter crossbow with
 * its own rules, ballistas) come with later steps.
 */
class RangedWeapons(private val weapons: Map<Int, RangedWeapon>, private val ammo: Map<Int, RangedAmmo>) {
    fun weapon(type: ObjType?): RangedWeapon? = type?.let { weapons[it.id] }

    fun ammo(type: ObjType?): RangedAmmo? = type?.let { ammo[it.id] }

    /** The item ids of every ammunition of [kind]. */
    fun ammoOfKind(kind: AmmoKind): List<Int> = ammo.filterValues { it.kind == kind }.keys.sorted()

    val weaponCount: Int get() = weapons.size

    val ammoCount: Int get() = ammo.size

    companion object {
        val NONE = RangedWeapons(emptyMap(), emptyMap())

        const val RESOURCE = "/ranged-weapons.json"

        /** Reads [RESOURCE]; see [parse]. */
        fun load(objTypes: ObjTypes, seqs: SeqNames, spotanims: SpotanimNames): RangedWeapons {
            val text = RangedWeapons::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            return parse(text, objTypes, seqs, spotanims)
        }

        /**
         * Parses a ranged weapons file:
         *
         * ```
         * { "weapons": [ { "items": ["shortbow"], "ammo": "arrows", "tier": 1, "range": 7, "shots": 1,
         *                  "anim": "human_bow", "source": "OSRS Wiki, Shortbow" } ],
         *   "ammo": [ { "items": ["bronze_arrow"], "ammo": "arrows", "tier": 1, "launch": "bronze_arrow_launch",
         *               "doubleLaunch": "double_bronze_arrow_launch", "travel": "bronze_arrow_travel" } ] }
         * ```
         *
         * Items are gameval names. `shots`, `launch` and `doubleLaunch` may be left out (1, none, none). A weapon may
         * also have `"scaling": "twisted_bow"`, `"bounces"` (0 to 2), its own `launch` and `travel`, `"loaded": true`
         * (the blowpipe), `"thrown": true` (chinchompas, which must be) and a `"delay"` other than the bow's. Animations and
         * spotanims are gameval names too; one [seqs] or [spotanims] doesn't know fails the file, unless it knows no
         * names at all (a cache with no gameval index), when they are all left out. An item the cache doesn't have is
         * skipped with a warning.
         */
        fun parse(text: String, objTypes: ObjTypes, seqs: SeqNames, spotanims: SpotanimNames, fileName: String = RESOURCE): RangedWeapons {
            val root = Json.parseToJsonElement(text).jsonObject
            val unknown = ArrayList<String>()
            fun <T> read(key: String, build: (JsonObject) -> T): Map<Int, T> {
                val entries = root[key]?.jsonArray ?: throw IllegalArgumentException("$fileName: no \"$key\" list")
                val byId = HashMap<Int, T>()
                entries.forEachIndexed { i, element ->
                    try {
                        val entry = element.jsonObject
                        val value = build(entry)
                        for (name in entry.required("items").jsonArray.map { it.jsonPrimitive.content }) {
                            val type = objTypes.find(name)
                            if (type == null) {
                                unknown += name
                                continue
                            }
                            require(type.id !in byId) { "${type.name} (${type.id}) is listed twice" }
                            byId[type.id] = value
                            check(type, value)
                        }
                    } catch (e: Exception) {
                        throw IllegalArgumentException("$fileName: $key entry ${i + 1}: ${e.message}", e)
                    }
                }
                return byId
            }
            fun spotanim(entry: JsonObject, key: String): Int {
                val name = entry[key]?.jsonPrimitive?.content ?: return -1
                if (spotanims.isEmpty) return -1
                return spotanims[name] ?: throw IllegalArgumentException("no spotanim called $name")
            }
            val weapons = read("weapons") { entry ->
                val anim = entry.required("anim").jsonPrimitive.content
                RangedWeapon(
                    kind = kind(entry),
                    tier = entry.required("tier").jsonPrimitive.int.also { require(it >= 1) { "tier $it" } },
                    range = entry.required("range").jsonPrimitive.int.also { require(it in 1..MAX_RANGE) { "range $it" } },
                    shots = entry["shots"]?.jsonPrimitive?.int?.also { require(it in 1..2) { "shots $it" } } ?: 1,
                    anim = if (seqs.isEmpty) -1 else seqs[anim] ?: throw IllegalArgumentException("no animation called $anim"),
                    source = entry.required("source").jsonPrimitive.content.ifBlank { throw IllegalArgumentException("blank source") },
                    scaling = entry["scaling"]?.jsonPrimitive?.content?.let { text ->
                        RangedScaling.entries.firstOrNull { it.name.equals(text, ignoreCase = true) }
                            ?: throw IllegalArgumentException("unknown scaling '$text'")
                    },
                    bounces = entry["bounces"]?.jsonPrimitive?.int?.also { require(it in 0..MAX_BOUNCES) { "bounces $it" } } ?: 0,
                    launch = spotanim(entry, "launch"),
                    travel = spotanim(entry, "travel"),
                    loaded = entry["loaded"]?.jsonPrimitive?.content?.toBooleanStrict() ?: false,
                    delay = entry["delay"]?.jsonPrimitive?.content?.let { text ->
                        HitDelay.entries.firstOrNull { it.name.equals(text, ignoreCase = true) }
                            ?: throw IllegalArgumentException("unknown delay '$text'")
                    } ?: HitDelay.BOW,
                    thrown = entry["thrown"]?.jsonPrimitive?.content?.toBooleanStrict() ?: false,
                ).also { require(it.kind != AmmoKind.CHINCHOMPAS || it.thrown) { "chinchompas are thrown" } }
            }
            val ammo = read("ammo") { entry ->
                RangedAmmo(
                    kind = kind(entry),
                    tier = entry.required("tier").jsonPrimitive.int.also { require(it >= 1) { "tier $it" } },
                    launch = spotanim(entry, "launch"),
                    doubleLaunch = spotanim(entry, "doubleLaunch"),
                    travel = entry.required("travel").let { spotanim(entry, "travel") },
                )
            }
            if (unknown.isNotEmpty()) logger.warn { "$fileName: skipped items the cache doesn't have: ${unknown.joinToString()}" }
            return RangedWeapons(weapons, ammo)
        }

        /**
         * Warns of a weapon whose combat tab isn't the bow's or crossbow's: its styles wouldn't be ranged, so it would
         * never fire. The category comes from the cache ([Weapons.category]), so this is where a mismatch shows.
         */
        private fun check(type: ObjType, value: Any?) {
            if (value !is RangedWeapon) return
            val category = Weapons.category(type)
            val expected = when (value.kind) {
                AmmoKind.ARROWS -> Weapons.BOW
                AmmoKind.BOLTS -> Weapons.CROSSBOW
                AmmoKind.DARTS -> Weapons.THROWN
                AmmoKind.CHINCHOMPAS -> Weapons.GRENADE
            }
            if (category != expected) logger.warn { "${type.name} (${type.id}) has weapon category $category, not $expected; it won't fire" }
        }

        /** The Wiki's longest attack range; longrange's +2 stops here too. */
        const val MAX_RANGE = 10

        /** The Venator bow's: a shot goes on to up to two more targets (OSRS Wiki, Venator bow). */
        const val MAX_BOUNCES = 2

        private fun kind(entry: JsonObject): AmmoKind {
            val text = entry.required("ammo").jsonPrimitive.content
            return AmmoKind.entries.firstOrNull { it.name.equals(text, ignoreCase = true) }
                ?: throw IllegalArgumentException("unknown ammo '$text'")
        }

        private fun JsonObject.required(key: String): JsonElement = get(key) ?: throw IllegalArgumentException("no \"$key\"")
    }
}
