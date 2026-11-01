package osrsps.game.raid

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.npc.Npc

private val logger = KotlinLogging.logger {}

/**
 * Raid NPCs' hitpoints by party size, from [RESOURCE]. The OSRS Wiki (The Fractured Archive) says every boss's
 * hitpoints scale to the players in the raid, "the minimum scaling being set to two players if the player enters
 * alone", so a party of one gets two players' hitpoints ([counted]).
 */
class PartyScaling internal constructor(private val byType: Map<Int, IntArray>) {
    val size: Int get() = byType.size

    /** Whether [type] has hitpoints by party size. */
    operator fun contains(type: NpcType): Boolean = type.id in byType

    /** [type]'s hitpoints for a party of [partySize]; null when its hitpoints don't scale. */
    fun hitpoints(type: NpcType, partySize: Int): Int? = byType[type.id]?.get(counted(partySize) - 1)

    /**
     * Gives [npc] its hitpoints for a party of [partySize], full, and returns them; null, leaving it as it was, when its
     * type's hitpoints don't scale.
     */
    fun scale(npc: Npc, partySize: Int): Int? {
        val hitpoints = hitpoints(npc.type, partySize) ?: return null
        npc.scaledFor = counted(partySize)
        npc.scaledHitpoints = hitpoints
        npc.hitpoints = hitpoints
        return hitpoints
    }

    companion object {
        val NONE = PartyScaling(emptyMap())

        const val RESOURCE = "/party-scaling.json"

        /** The fewest players hitpoints are scaled for: a player alone gets two players' (OSRS Wiki). */
        const val MIN_COUNTED = 2

        /** The size of a party a table goes up to, and the most a raid party holds (OSRS Wiki: 1 to 8 participants). */
        const val MAX_PARTY = 8

        /** The party size hitpoints are scaled for when [partySize] players are in the raid. */
        fun counted(partySize: Int): Int {
            require(partySize in 1..MAX_PARTY) { "a party of $partySize" }
            return partySize.coerceAtLeast(MIN_COUNTED)
        }

        fun load(types: NpcTypes): PartyScaling {
            val text = PartyScaling::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            return parse(text, types)
        }

        /**
         * Parses a scaling file: `{ "npcs": [ { "npcs": ["tfa_guardian"], "hitpoints": [550, ...], "source": "..." } ] }`,
         * with [MAX_PARTY] hitpoints, for parties of 1 to 8. `npcs` takes type ids, gameval names or menu names. An NPC
         * the cache doesn't have is skipped with a warning.
         */
        fun parse(text: String, types: NpcTypes, fileName: String = RESOURCE): PartyScaling {
            val entries = Json.parseToJsonElement(text).jsonObject["npcs"]?.jsonArray ?: throw IllegalArgumentException("$fileName: no \"npcs\" list")
            val byType = HashMap<Int, IntArray>()
            val unknown = ArrayList<String>()
            entries.forEachIndexed { i, element ->
                try {
                    val entry = element.jsonObject
                    val hitpoints = (entry["hitpoints"] ?: throw IllegalArgumentException("no \"hitpoints\"")).jsonArray.map { it.jsonPrimitive.int }.toIntArray()
                    require(hitpoints.size == MAX_PARTY) { "${hitpoints.size} hitpoints, not one for each party size 1 to $MAX_PARTY" }
                    require(hitpoints.all { it > 0 }) { "hitpoints must be above 0" }
                    require((1 until MAX_PARTY).all { hitpoints[it] >= hitpoints[it - 1] }) { "hitpoints fall as the party grows" }
                    requireNotNull(entry["source"]) { "no \"source\"" }
                    for (name in (entry["npcs"] ?: throw IllegalArgumentException("no \"npcs\"")).jsonArray.map { it.jsonPrimitive.content }) {
                        val type = types.lookup(name)
                        if (type == null) {
                            unknown += name
                            continue
                        }
                        require(type.id !in byType) { "${type.name} (${type.id}) is listed twice" }
                        byType[type.id] = hitpoints
                    }
                } catch (e: Exception) {
                    throw IllegalArgumentException("$fileName: entry ${i + 1}: ${e.message}", e)
                }
            }
            if (unknown.isNotEmpty()) logger.warn { "$fileName: skipped NPCs the cache doesn't have: ${unknown.joinToString()}" }
            return PartyScaling(byType)
        }
    }
}
