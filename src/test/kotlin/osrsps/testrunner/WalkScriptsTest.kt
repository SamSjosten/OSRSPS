package osrsps.testrunner

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The road walks (`lumbridge-walk.json`, `misthalin-walk.json`) click no further than [MAX_STAGE] tiles from where the
 * player stands, so the tile is on screen on rl-client and the server's own pathing takes each stage. That every stage
 * is reached on the real map is checked by playing the scripts with `headlessScripts`.
 */
class WalkScriptsTest {
    private fun stages(file: String): List<Pair<Pair<Int, Int>, Pair<Int, Int>>> {
        val steps = Json.parseToJsonElement(Path.of("test-runner/examples", file).readText()).jsonObject["steps"]!!.jsonArray
        var at: Pair<Int, Int>? = null
        val stages = ArrayList<Pair<Pair<Int, Int>, Pair<Int, Int>>>()
        for (step in steps.map { it.jsonObject }) {
            val text = (step["text"]?.jsonPrimitive?.content).orEmpty()
            when {
                text.startsWith("::tele ") -> text.split(' ').let { at = it[1].toInt() to it[2].toInt() }
                step.name() == "walk" -> {
                    val to = step["x"]!!.jsonPrimitive.int to step["y"]!!.jsonPrimitive.int
                    stages += checkNotNull(at) { "$file walks before any ::tele" } to to
                    at = to
                }
            }
        }
        return stages
    }

    private fun JsonObject.name() = this["step"]?.jsonPrimitive?.content

    @Test
    fun `every stage of the road walks is at most 4 tiles`() {
        for (file in listOf("lumbridge-walk.json", "misthalin-walk.json")) {
            val stages = stages(file)
            assertTrue(stages.size > 100, "$file has ${stages.size} stages")
            val far = stages.filter { (from, to) -> max(abs(to.first - from.first), abs(to.second - from.second)) > MAX_STAGE }
            assertEquals(emptyList(), far, file)
        }
    }

    @Test
    fun `the Misthalin walk reaches each place the piece built`() {
        val ends = stages("misthalin-walk.json").map { it.second }.toSet()
        for (place in listOf(3212 to 3390, 3165 to 3251, 3306 to 3477, 3361 to 3486, 3407 to 3489, 3356 to 3344, 3361 to 3445)) {
            assertTrue(place in ends, "no stage ends at $place")
        }
    }

    private companion object {
        const val MAX_STAGE = 4
    }
}
