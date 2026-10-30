package osrsps.game.trade

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * A completed trade's two saves, written together before either save file so the trade can't land on one side only.
 *
 * Saves are written one file at a time, so a crash between the two would leave one player with both offers and the
 * other with theirs still: a dupe. Instead the trade's two new saves go into this journal, forced to the disk, before
 * either save file is written; the journal goes once both are. A server that starts and finds a journal writes its
 * saves again, skipping a player whose save already records the trade (their [osrsps.game.Player.lastTrade] is its
 * [id]), so a journal that couldn't be cleared never rolls anyone back. No new trade is journaled while one is left
 * unfinished, so a save naming any other trade is older than this one. A crash before the journal is written loses the
 * trade whole: both players keep what they had.
 */
class TradeJournal(
    /** Unique across restarts (a hash of both players' saves before the trade), so a save's lastTrade names this trade alone. */
    val id: String,
    /** Each player's new save, by save key. */
    val saves: Map<String, String>,
) {
    fun toJson(): String = Json.encodeToString(
        kotlinx.serialization.json.JsonObject.serializer(),
        buildJsonObject {
            put("id", id)
            putJsonObject("saves") { for ((key, text) in saves) put(key, text) }
        },
    )

    companion object {
        /** Reads a journal written by [toJson]. Throws [IllegalArgumentException] if it isn't one. */
        fun fromJson(text: String): TradeJournal = try {
            val root = Json.parseToJsonElement(text).jsonObject
            TradeJournal(root.getValue("id").jsonPrimitive.content, root.getValue("saves").jsonObject.mapValues { it.value.jsonPrimitive.content })
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a trade journal: ${e.message}", e)
        }
    }
}
