package osrsps.game.raid

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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

private val logger = KotlinLogging.logger {}

/**
 * One of a party at the end of a run, for the loot: their [contribution] ([ArchiveLoot.contribution]), whether they
 * own an Archivist's folio already, and which elemental fragments they own or have broken, by item id.
 */
data class LootMember(val key: String, val contribution: Int, val ownsFolio: Boolean = false, val fragments: Set<Int> = emptySet())

/** What a run's end gave: each member's chest, by [LootMember.key] in the party's order, and who found the unique. */
class LootResult(val chests: Map<String, List<Drop>>, val unique: String?)

/**
 * The reward vault's loot (raid step 29a), from [RESOURCE]: the OSRS Wiki's page Chest (The Fractured Archive), read
 * 2026-11-10.
 *
 * - **The unique.** One roll for the party at the raid's end: 1 in [uniqueOneIn] (14) alone or as two, and "in trio
 *   and larger sizes, each additional player will increase the drop rate of a unique by a multiplicative 4%". The
 *   Wiki's own figures (1/13.46 for three, 1/90.32 a player for eight, so 1/11.29 for the party) fit 4% of the base
 *   added for each player past two, not 4% compounded: [uniqueChance]. Inferred from those figures.
 * - **Who finds it.** "The player's contribution is rolled against the total contribution obtained by the team": one
 *   member, each as likely as their share of the total. Which item, by the unique table's weights (5, 4, 3, 2, 1 of 15).
 * - **Everyone else.** "The standard loot table is rolled three times", each roll with a 1 in [fiveTimesOneIn] (51)
 *   chance "to be multiplied five times"; then the tertiary drops, each on its own: an elite clue, the inert
 *   Archivist's folio (never with one owned), and an elemental fragment (the Wiki's footnote: 1/20 for a fragment,
 *   never one already owned, none once all four are).
 * - **Cabbage.** "If players perform poorly they receive only cabbage. Each death increases the chance to receive
 *   cabbage", and a cabbage rules out every tertiary drop. The Wiki gives no numbers.
 *
 * Guessed: that the one who finds the unique gets nothing else (the Wiki lists the tertiary drops under the standard
 * loot); that a cabbage is what a member with no contribution left gets, and only them (the chance each death adds
 * isn't known, so it isn't rolled); that a fragment is one of those not owned, each as likely.
 *
 * Not in yet: Orion, the pet (1/750), until pets are built; the clue's better rate with the elite Combat
 * Achievements; scroll boxes.
 */
class ArchiveLoot(
    val uniqueOneIn: Int,
    /** Hundredths of the base chance each player past two adds. */
    val perPlayerPastTwo: Int,
    val uniques: DropTable?,
    val standardRolls: Int,
    val fiveTimesOneIn: Int,
    val standard: DropTable?,
    val clue: DropItem?,
    val clueOneIn: Int,
    val folio: ObjType?,
    val folioOneIn: Int,
    /** Item ids that count as owning the folio: the inert one and its charged form. */
    val folioForms: Set<Int>,
    /** The four elemental fragments, each with the varbit that says it has been broken. */
    val fragments: List<Fragment>,
    val fragmentOneIn: Int,
    val junk: ObjType?,
    /** What the loader warned about, for tests. */
    val warnings: List<String> = emptyList(),
) {
    /** An elemental fragment, and the varbit set once it is broken (241 cache: `tfa_mote_*_unlocked`, 16434 to 16437). */
    data class Fragment(val type: ObjType, val unlocked: Int)

    /**
     * The party's chance of a unique as a fraction, numerator to denominator: 1 in [uniqueOneIn] for one or two, and
     * [perPlayerPastTwo] hundredths of that more for each player past two. Never more than certain.
     */
    fun uniqueChance(size: Int): Pair<Int, Int> =
        (100 + perPlayerPastTwo * (size - 2).coerceAtLeast(0)).coerceAtMost(uniqueOneIn * 100) to uniqueOneIn * 100

    /**
     * Rolls a run's loot for [members], in the party's order, so a test can replay the draws: the party's unique, who
     * finds it and which; then for each other member the standard rolls (line, count, five times), the clue, the folio
     * and the fragment (which one, when it hits). A member with no contribution takes no draw.
     */
    fun roll(random: GameRandom, members: List<LootMember>): LootResult {
        val total = members.sumOf { it.contribution.coerceAtLeast(0) }
        var finder: String? = null
        var found: Drop? = null
        val (numerator, denominator) = uniqueChance(members.size)
        if (uniques != null && total > 0 && random.chance(numerator, denominator)) {
            var left = random.nextInt(total)
            for (member in members) {
                left -= member.contribution.coerceAtLeast(0)
                if (left < 0) {
                    finder = member.key
                    break
                }
            }
            found = uniques.roll(random).firstOrNull()?.let { Drop(it.type, it.count(random)) }
        }
        val chests = LinkedHashMap<String, List<Drop>>()
        for (member in members) {
            chests[member.key] = when {
                member.contribution <= 0 -> listOfNotNull(junk?.let { Drop(it, 1) })
                member.key == finder && found != null -> listOf(found)
                else -> standardChest(random, member)
            }
        }
        return LootResult(chests, finder.takeIf { found != null })
    }

    private fun standardChest(random: GameRandom, member: LootMember): List<Drop> {
        val counts = LinkedHashMap<ObjType, Int>()
        fun add(type: ObjType, count: Int) {
            counts[type] = (counts[type] ?: 0) + count
        }
        if (standard != null) {
            repeat(standardRolls) {
                for (item in standard.roll(random)) {
                    val count = item.count(random)
                    add(item.type, if (random.chance(1, fiveTimesOneIn)) count * FIVE_TIMES else count)
                }
            }
        }
        if (clue != null && random.chance(1, clueOneIn)) add(clue.type, clue.count(random))
        if (folio != null && !member.ownsFolio && random.chance(1, folioOneIn)) add(folio, 1)
        val wanted = fragments.filter { it.type.id !in member.fragments }
        if (wanted.isNotEmpty() && random.chance(1, fragmentOneIn)) add(random.pick(wanted).type, 1)
        return counts.map { (type, count) -> Drop(type, count) }
    }

    companion object {
        const val RESOURCE = "/archive-loot.json"

        /** What a roll that is "multiplied five times" gives of its count. */
        const val FIVE_TIMES = 5

        /** The team's points in a whole run: four rooms of 100 and Ocellus's 300 (the Wiki). */
        const val RUN_POINTS = 700

        /** No table at all: a world whose cache has none of the items (tests), where chests come up empty. */
        val NONE = ArchiveLoot(14, 4, null, 3, 51, null, null, 10, null, 15, emptySet(), emptyList(), 20, null)

        /**
         * A member's contribution for the loot: their [points], less a tenth of what one of [size] is expected to earn
         * for each of their [deaths] ("All deaths penalise the player for 10% of their expected contribution": 35 of a
         * duo's 350, "8 or 9" of an eight's 87.5). Never below nothing. Guessed: that the tenth is rounded down.
         */
        fun contribution(points: Int, deaths: Int, size: Int): Int =
            (points - deaths * (RUN_POINTS / (size.coerceAtLeast(1) * 10))).coerceAtLeast(0)

        fun load(objTypes: ObjTypes): ArchiveLoot {
            val text = ArchiveLoot::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            // One line, not one an item: a test's cache has next to none of them.
            return parse(text, objTypes).also { loot ->
                if (loot.warnings.isNotEmpty()) logger.warn { "$RESOURCE: ${loot.warnings.size} warnings, the first: ${loot.warnings.first()}" }
            }
        }

        /**
         * Reads the loot file. An item is an id plus its `name`: a name the cache doesn't agree with is warned about,
         * and an item the cache doesn't have is left out with a warning, its line's weight with it, rather than
         * stopping the server (a test cache has few items).
         */
        fun parse(text: String, objTypes: ObjTypes): ArchiveLoot {
            val root = Json.parseToJsonElement(text).jsonObject
            val warnings = ArrayList<String>()
            fun type(entry: JsonObject): ObjType? {
                val id = entry.required("item").jsonPrimitive.int
                val name = entry.required("name").jsonPrimitive.content
                val type = objTypes[id]
                if (type == null) warnings += "no item $id ($name) in the cache"
                else if (!type.name.equals(name, ignoreCase = true)) warnings += "$id is ${type.name}, not $name"
                return type
            }
            fun item(entry: JsonObject): DropItem? {
                val type = type(entry) ?: return null
                val min = entry["min"]?.jsonPrimitive?.int ?: entry["count"]?.jsonPrimitive?.int ?: 1
                return DropItem(type, min, entry["max"]?.jsonPrimitive?.int ?: min)
            }
            fun table(obj: JsonObject, what: String): DropTable? {
                val all = obj.required("lines").jsonArray.map { it.jsonObject }
                val slots = obj.required("slots").jsonPrimitive.int
                require(all.sumOf { it.required("weight").jsonPrimitive.int } == slots) { "the $what table's weights don't add up to its $slots slots" }
                val lines = all.mapNotNull { line -> item(line)?.let { DropSlot(line.required("weight").jsonPrimitive.int, item = it) } }
                return if (lines.isEmpty()) null else DropTable(lines.sumOf { it.weight }, lines)
            }
            fun oneIn(entry: JsonObject): Int {
                val chance = entry.required("chance").jsonPrimitive.content
                val parts = chance.split('/')
                require(parts.size == 2 && parts[0].trim() == "1") { "a chance is written 1/n, not $chance" }
                return parts[1].trim().toIntOrNull()?.takeIf { it >= 1 } ?: throw IllegalArgumentException("bad chance $chance")
            }
            try {
                val unique = root.required("unique").jsonObject
                val standard = root.required("standard").jsonObject
                val tertiary = root.required("tertiary").jsonObject
                val clue = tertiary.required("clue").jsonObject
                val folio = tertiary.required("folio").jsonObject
                val fragments = tertiary.required("fragments").jsonObject
                val percent = unique.required("perPlayerPastTwo").jsonPrimitive.content.removeSuffix("%").trim().toIntOrNull()
                    ?: throw IllegalArgumentException("perPlayerPastTwo is written as a percentage, 4%")
                val folioType = type(folio)
                return ArchiveLoot(
                    uniqueOneIn = unique.required("oneIn").jsonPrimitive.int,
                    perPlayerPastTwo = percent,
                    uniques = table(unique, "unique"),
                    standardRolls = standard.required("rolls").jsonPrimitive.int,
                    fiveTimesOneIn = standard.required("fiveTimesOneIn").jsonPrimitive.int,
                    standard = table(standard, "standard"),
                    clue = item(clue),
                    clueOneIn = oneIn(clue),
                    folio = folioType,
                    folioOneIn = oneIn(folio),
                    folioForms = setOfNotNull(folioType?.id) + folio["alsoOwnedAs"]?.jsonArray.orEmpty().map { it.jsonPrimitive.int },
                    fragments = fragments.required("items").jsonArray.mapNotNull { element ->
                        val entry = element.jsonObject
                        type(entry)?.let { Fragment(it, entry.required("unlocked").jsonPrimitive.int) }
                    },
                    fragmentOneIn = oneIn(fragments),
                    junk = type(root.required("junk").jsonObject),
                    warnings = warnings,
                )
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("$RESOURCE: ${e.message}", e)
            }
        }

        private fun JsonObject.required(key: String): JsonElement = get(key) ?: throw IllegalArgumentException("no \"$key\"")
    }
}
