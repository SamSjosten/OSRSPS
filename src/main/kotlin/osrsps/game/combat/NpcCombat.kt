package osrsps.game.combat

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.NpcStat
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.SeqNames
import osrsps.game.Skill

private val logger = KotlinLogging.logger {}

/** An NPC's defence bonuses: melee by attack type, magic, and ranged by the weight of the ammunition. */
data class NpcDefence(
    val stab: Int,
    val slash: Int,
    val crush: Int,
    val magic: Int,
    val light: Int,
    val standard: Int,
    val heavy: Int,
) {
    /** The bonus that defends against an attack of [type]; for ranged, the one for [weight] (standard when not given). */
    fun against(type: AttackType, weight: RangedWeight? = null): Int = when (type) {
        AttackType.STAB -> stab
        AttackType.SLASH -> slash
        AttackType.CRUSH -> crush
        AttackType.MAGIC -> magic
        AttackType.RANGED -> when (weight) {
            RangedWeight.LIGHT -> light
            RangedWeight.HEAVY -> heavy
            RangedWeight.STANDARD, null -> standard
        }
        AttackType.NONE -> 0
    }
}

/** The four elements of the standard spellbook's combat spells (air for the wind spells). */
enum class Element {
    AIR,
    WATER,
    EARTH,
    FIRE,
    ;

    companion object {
        fun parse(text: String): Element =
            entries.firstOrNull { it.name.equals(text, ignoreCase = true) } ?: throw IllegalArgumentException("unknown element '$text'")
    }
}

/**
 * A monster's weakness to the spells of [element]: [percent] more accuracy and damage for each such spell cast on it
 * (OSRS Wiki, Elemental weakness; in since Project Rebalance, 29 May 2024).
 */
data class ElementalWeakness(val element: Element, val percent: Int)

/** The animations an NPC fights with; -1 where it has none. */
data class NpcCombatAnims(val attack: Int, val defend: Int, val death: Int) {
    companion object {
        val NONE = NpcCombatAnims(-1, -1, -1)
    }
}

/**
 * What an NPC needs to fight beyond the levels in its cache type ([NpcType.stats]): its bonuses, how it attacks and how
 * long it takes to come back. From the OSRS Wiki's infobox for the monster; [source] says which page.
 */
data class NpcCombatDef(
    val attackSpeed: Int,
    /** The Wiki's max hit, which [CombatMath.npcMaxHit] gives from the levels too for these NPCs; a test checks it. */
    val maxHit: Int,
    val attackType: AttackType,
    val attackBonus: Int,
    val strengthBonus: Int,
    val defence: NpcDefence,
    val anims: NpcCombatAnims,
    /** Ticks from its death until it is back at its spawn. */
    val respawnTicks: Int,
    val source: String,
    /** The Wiki's magic attack bonus, which the twisted bow scales on when it beats the Magic level. */
    val magicAttack: Int = 0,
    /** The Wiki's elemental weakness, null for "No elemental weakness". */
    val weakness: ElementalWeakness? = null,
    /** The poison severity its attacks give ([Poison]), 0 for none: five times the Wiki's starting damage. */
    val poison: Int = 0,
    /** The Wiki's immunities: poison and venom from players' weapons do nothing to it. */
    val poisonImmune: Boolean = false,
    val venomImmune: Boolean = false,
    /** The most of each stat's level drains can take, in percent ([NpcDrains]); a stat not listed can be drained to 0. */
    val drainCaps: Map<NpcStat, Int> = emptyMap(),
    /** How it attacks players unprovoked ([Aggression]); null for a monster that only fights back. */
    val aggression: NpcAggression? = null,
    /** At or below this share of its hitpoints, in percent and rounded up, it retreats ([MeleeCombat]); 0 never. */
    val retreatsAt: Int = 0,
    /** How far it attacks from, Chebyshev edge to edge: 1 for melee, more for a magic or ranged NPC ([attacks]). */
    val range: Int = 1,
    /** A magic or ranged NPC's attacks, one picked by weight each time; empty for melee. */
    val attacks: List<NpcRangedAttack> = emptyList(),
    /**
     * The Wiki's XP bonus in tenths of a percent, 0 for none: the outlaws' -97.5% is -975, so they give a fortieth of the
     * XP for their damage ([CombatMath.withXpBonus]).
     */
    val xpBonusTenths: Int = 0,
)

/** A stat a hit lowers by [percent] of its base level (Confuse's Attack), when it isn't lowered that far already. */
data class StatDrain(val skill: Skill, val percent: Int)

/**
 * One of a magic or ranged NPC's attacks: its animation (-1 for the NPC's own attack one), the spotanim on it as it
 * casts or shoots, the projectile that flies and the spotanim on the player when it lands (-1 for none of each), its
 * [weight] among the NPC's others, the most it hits for (null for the NPC's max hit, 0 for a spell that only drains)
 * and what it drains when it hits.
 */
data class NpcRangedAttack(
    val anim: Int = -1,
    val cast: Int = -1,
    val projectile: Int = -1,
    val impact: Int = -1,
    val weight: Int = 1,
    val maxHit: Int? = null,
    val drain: StatDrain? = null,
)

/** NPC combat definitions by NPC type id. An NPC with none can't be attacked. */
class NpcCombat(private val byType: Map<Int, NpcCombatDef>) {
    operator fun get(type: NpcType): NpcCombatDef? = byType[type.id]

    operator fun get(typeId: Int): NpcCombatDef? = byType[typeId]

    val size: Int get() = byType.size

    companion object {
        val NONE = NpcCombat(emptyMap())

        const val RESOURCE = "/npc-combat.json"

        /** Reads [RESOURCE]. [seqs] turns an animation's gameval name into its id; see [parse]. */
        fun load(types: NpcTypes, seqs: SeqNames): NpcCombat {
            val text = NpcCombat::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            return parse(text, types, seqs)
        }

        /**
         * Parses an NPC combat file:
         *
         * ```
         * { "npcs": [
         *   { "npcs": ["man", "man2"], "attackSpeed": 4, "maxHit": 1, "attackType": "crush", "attackBonus": 0,
         *     "strengthBonus": 0,
         *     "defence": { "stab": -21, "slash": -21, "crush": -21, "magic": -21, "light": -21, "standard": -21, "heavy": -21 },
         *     "anims": { "attack": "human_unarmedpunch", "defend": "human_unarmedblock", "death": "human_death" },
         *     "respawnTicks": 25, "source": "OSRS Wiki, Man" }
         * ] }
         * ```
         *
         * An animation may be `null` for none. An entry may add `"magicAttack"`, `"weakness": { "element": "water", "percent": 10 }`,
         * `"poison"` (the Wiki's starting damage of the poison its attacks give), `"immune": ["poison", "venom"]` and
         * `"drainCap": { "defence": 60 }` (the most of a level drains can take, in percent) and `"aggressive"`: `true`, or
         * `{ "huntRange": 3, "ignoresLevels": true, "tolerant": false }` with any of the three ([NpcAggression]), and
         * `"retreatsAt": 25` (the share of its hitpoints, in percent, at which it retreats) and `"xpBonus": -97.5` (the
         * Wiki's XP bonus, in percent to a tenth).
         * A magic or ranged NPC adds `"range"` and its `"attacks"`, each `{ "anim": "human_caststrike", "cast": 93,
         * "projectile": 94, "impact": 95, "weight": 1, "maxHit": 2, "drain": { "skill": "attack", "percent": 5 } }`, all
         * optional ([NpcRangedAttack]); its `attackBonus` is its bonus for its attack type, magic or ranged.
         *
         * `npcs` takes type ids, gameval names or menu names, as spawn files do. Animations are gameval names, which
         * [seqs] turns into ids; a name it doesn't know fails the file, unless it knows no names at all (a cache with
         * no gameval index), when every animation is left out. An NPC the cache doesn't have is skipped with a warning.
         */
        fun parse(text: String, types: NpcTypes, seqs: SeqNames, fileName: String = RESOURCE): NpcCombat {
            val root = Json.parseToJsonElement(text).jsonObject
            val entries = root["npcs"]?.jsonArray ?: throw IllegalArgumentException("$fileName: no \"npcs\" list")
            val byType = HashMap<Int, NpcCombatDef>()
            val unknown = ArrayList<String>()
            entries.forEachIndexed { i, element ->
                try {
                    val entry = element.jsonObject
                    val def = def(entry, seqs)
                    for (name in entry.required("npcs").jsonArray.map { it.jsonPrimitive.content }) {
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
            return NpcCombat(byType)
        }

        private fun def(entry: JsonObject, seqs: SeqNames): NpcCombatDef {
            val defence = entry.required("defence").jsonObject
            fun bonus(key: String) = defence.required(key).jsonPrimitive.int
            val anims = entry.required("anims").jsonObject
            fun anim(key: String): Int {
                val value = anims.required(key)
                // null for an NPC with no such animation, as ducks have no attack or block.
                if (value is JsonNull) return -1
                val name = value.jsonPrimitive.content
                return seqs[name] ?: throw IllegalArgumentException("no animation called $name")
            }
            val immune = entry["immune"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty().toSet()
            require(immune.all { it == "poison" || it == "venom" }) { "immune to ${immune.joinToString()}: only poison and venom" }
            val attackType = entry.required("attackType").jsonPrimitive.content.let { text ->
                AttackType.entries.firstOrNull { it.name.equals(text, ignoreCase = true) }
                    ?: throw IllegalArgumentException("unknown attack type '$text'")
            }
            return NpcCombatDef(
                attackSpeed = entry.required("attackSpeed").jsonPrimitive.int.also { require(it > 0) { "attack speed $it" } },
                maxHit = entry.required("maxHit").jsonPrimitive.int.also { require(it >= 0) { "max hit $it" } },
                attackType = attackType,
                attackBonus = entry.required("attackBonus").jsonPrimitive.int,
                strengthBonus = entry.required("strengthBonus").jsonPrimitive.int,
                defence = NpcDefence(bonus("stab"), bonus("slash"), bonus("crush"), bonus("magic"), bonus("light"), bonus("standard"), bonus("heavy")),
                anims = if (seqs.isEmpty) NpcCombatAnims.NONE else NpcCombatAnims(anim("attack"), anim("defend"), anim("death")),
                respawnTicks = entry.required("respawnTicks").jsonPrimitive.int.also { require(it > 0) { "respawn $it" } },
                source = entry.required("source").jsonPrimitive.content.ifBlank { throw IllegalArgumentException("blank source") },
                magicAttack = entry["magicAttack"]?.jsonPrimitive?.int ?: 0,
                weakness = entry["weakness"]?.jsonObject?.let { weakness ->
                    ElementalWeakness(
                        Element.parse(weakness.required("element").jsonPrimitive.content),
                        weakness.required("percent").jsonPrimitive.int.also { require(it in 1..100) { "weakness of $it%" } },
                    )
                },
                poison = (entry["poison"]?.jsonPrimitive?.int ?: 0).also { require(it in 0..Poison.VENOM_MAX) { "poison starting at $it" } } * 5,
                poisonImmune = "poison" in immune,
                venomImmune = "venom" in immune,
                drainCaps = entry["drainCap"]?.jsonObject?.entries?.associate { (stat, percent) ->
                    NpcStat.parse(stat) to percent.jsonPrimitive.int.also { require(it in 0..100) { "a drain cap of $it%" } }
                }.orEmpty(),
                aggression = entry["aggressive"]?.let(::aggression),
                retreatsAt = (entry["retreatsAt"]?.jsonPrimitive?.int ?: 0).also { require(it in 0..100) { "retreats at $it%" } },
                range = (entry["range"]?.jsonPrimitive?.int ?: 1).also { require(it in 1..MAX_RANGE) { "range $it" } },
                attacks = entry["attacks"]?.jsonArray?.map { attack(it.jsonObject, seqs) }.orEmpty().also {
                    require(it.isEmpty() || attackType == AttackType.MAGIC || attackType == AttackType.RANGED) { "attacks on a $attackType NPC" }
                },
                xpBonusTenths = (entry["xpBonus"]?.jsonPrimitive?.double ?: 0.0).let { percent ->
                    require(percent > -100.0 && percent <= 1000.0) { "an XP bonus of $percent%" }
                    Math.round(percent * 10).toInt()
                },
            )
        }

        private fun aggression(value: JsonElement): NpcAggression? {
            if (value is JsonPrimitive) return if (value.boolean) NpcAggression() else null
            val entry = value.jsonObject
            val known = setOf("huntRange", "ignoresLevels", "tolerant")
            require(entry.keys.all { it in known }) { "aggressive takes ${known.joinToString()}, not ${(entry.keys - known).joinToString()}" }
            return NpcAggression(
                huntRange = entry["huntRange"]?.jsonPrimitive?.int ?: NpcAggression.DEFAULT_HUNT_RANGE,
                ignoresLevels = entry["ignoresLevels"]?.jsonPrimitive?.boolean ?: false,
                tolerant = entry["tolerant"]?.jsonPrimitive?.boolean ?: true,
            )
        }

        /** The farthest an NPC attacks from here: a spell's 10 tiles (OSRS Wiki, Attack range). */
        const val MAX_RANGE = 10

        private fun attack(entry: JsonObject, seqs: SeqNames): NpcRangedAttack {
            fun id(key: String) = entry[key]?.jsonPrimitive?.int ?: -1
            val anim = entry["anim"]?.jsonPrimitive?.content?.let { name ->
                if (seqs.isEmpty) -1 else seqs[name] ?: throw IllegalArgumentException("no animation called $name")
            } ?: -1
            return NpcRangedAttack(
                anim = anim,
                cast = id("cast"),
                projectile = id("projectile"),
                impact = id("impact"),
                weight = (entry["weight"]?.jsonPrimitive?.int ?: 1).also { require(it > 0) { "weight $it" } },
                maxHit = entry["maxHit"]?.jsonPrimitive?.int?.also { require(it >= 0) { "max hit $it" } },
                drain = entry["drain"]?.jsonObject?.let { drain ->
                    val name = drain.required("skill").jsonPrimitive.content
                    StatDrain(
                        Skill.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: throw IllegalArgumentException("no skill $name"),
                        drain.required("percent").jsonPrimitive.int.also { require(it in 1..100) { "drain of $it%" } },
                    )
                },
            )
        }

        private fun JsonObject.required(key: String): JsonElement = get(key) ?: throw IllegalArgumentException("no \"$key\"")
    }
}
