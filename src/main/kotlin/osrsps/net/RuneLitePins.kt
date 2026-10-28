package osrsps.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The RuneLite build that plays [revision]: [commit] of RuneLite's static.runelite.net history, whose bootstrap.json
 * (at raw.githubusercontent.com/runelite/static.runelite.net/<commit>/bootstrap.json) lists that build's jars
 * ([runelite] is its version), and the SHA-256 of that bootstrap.json. Launchers download RuneLite from RuneLite itself
 * at that commit, so friends stay on the server's revision after live and RuneLite move on; only where to find it is
 * kept here, never RuneLite's or Jagex's files.
 */
data class RuneLitePin(val revision: Int, val runelite: String, val commit: String, val bootstrapSha256: String)

/** The RuneLite build for each revision the server has run, from [RESOURCE]. */
class RuneLitePins(private val pins: Map<Int, RuneLitePin>) {
    operator fun get(revision: Int): RuneLitePin? = pins[revision]

    val size: Int get() = pins.size

    companion object {
        val NONE = RuneLitePins(emptyMap())

        const val RESOURCE = "/runelite-pins.json"

        private val COMMIT = Regex("[0-9a-f]{40}")
        private val SHA256 = Regex("[0-9a-f]{64}")

        /** Reads [RESOURCE]; see [parse]. */
        fun load(): RuneLitePins {
            val text = RuneLitePins::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            return parse(text)
        }

        /**
         * Parses a pins file:
         *
         * ```
         * { "pins": [ { "revision": 241, "runelite": "1.13.4", "commit": "1e634e64...", "bootstrapSha256": "25f22ff8...",
         *               "source": "static.runelite.net commit 'Release 1.13.4' (2026-10-24)" } ] }
         * ```
         *
         * The commit is all 40 lowercase hex digits and the hash all 64, since a launcher puts the commit into a URL and
         * compares the hash with what it downloads. One pin per revision.
         */
        fun parse(text: String, fileName: String = RESOURCE): RuneLitePins {
            val root = Json.parseToJsonElement(text).jsonObject
            val entries = root["pins"]?.jsonArray ?: throw IllegalArgumentException("$fileName: no \"pins\" list")
            val byRevision = LinkedHashMap<Int, RuneLitePin>()
            entries.forEachIndexed { i, element ->
                try {
                    val entry = element.jsonObject
                    val pin = RuneLitePin(
                        revision = entry.required("revision").jsonPrimitive.int,
                        runelite = entry.required("runelite").jsonPrimitive.content.ifBlank { throw IllegalArgumentException("blank runelite version") },
                        commit = entry.required("commit").jsonPrimitive.content.also {
                            require(COMMIT.matches(it)) { "commit '$it' isn't 40 lowercase hex digits" }
                        },
                        bootstrapSha256 = entry.required("bootstrapSha256").jsonPrimitive.content.also {
                            require(SHA256.matches(it)) { "bootstrapSha256 '$it' isn't 64 lowercase hex digits" }
                        },
                    )
                    entry.required("source").jsonPrimitive.content.ifBlank { throw IllegalArgumentException("blank source") }
                    require(pin.revision !in byRevision) { "revision ${pin.revision} is pinned twice" }
                    byRevision[pin.revision] = pin
                } catch (e: Exception) {
                    throw IllegalArgumentException("$fileName: entry ${i + 1}: ${e.message}", e)
                }
            }
            return RuneLitePins(byRevision)
        }

        private fun JsonObject.required(key: String): JsonElement = get(key) ?: throw IllegalArgumentException("no \"$key\"")
    }
}
