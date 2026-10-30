package osrsps.game.skill

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.GameLines
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.message
import osrsps.game.dialogue.Lines

/** Crafting's words, in the dialogue file. */
object CraftingLines : Lines("crafting", "Crafting") {
    val SPIN = line("spin", "Ball of wool and Bow string: the message on spinning one", "item")
    val LEVEL = line("level", "the message when the item needs a higher level", "level", "item")
}

/** crafting.json: what a spinning wheel spins, in the Make-X box's order. */
class CraftingData(val spinning: List<MakeRecipe>) {
    companion object {
        const val RESOURCE = "/skills/crafting.json"

        fun load(): CraftingData {
            val text = CraftingData::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            return parse(text)
        }

        fun parse(text: String): CraftingData {
            val root = Json.parseToJsonElement(text).jsonObject
            val spinning = root.getValue("spinning").jsonObject
            val spun = spinning.getValue("recipes").jsonArray.map { it.jsonObject }.map { recipe ->
                MakeRecipe(
                    "spin:${recipe.getValue("name").jsonPrimitive.content}", Skill.CRAFTING, recipe.int("product"), 1,
                    recipe.int("level"), recipe.int("xpTenths"), listOf(ObjCount(recipe.int("input"), 1)), emptyList(),
                    spinning.int("anim"), spinning.int("first"), spinning.int("interval"), CraftingLines.SPIN, CraftingLines.LEVEL,
                )
            }
            require(spun.map { it.inputs[0].obj }.toSet().size == spun.size) { "A spinning input is listed twice" }
            return CraftingData(spun)
        }

        private fun JsonObject.int(name: String): Int = getValue(name).jsonPrimitive.int
    }
}

/**
 * Crafting: Spin on a spinning wheel opens the Make-X box over what the player holds to spin; wool or flax used on a
 * wheel opens it over that alone. The making is [Making]'s. Later steps add leather, gems, jewellery, glass and pottery.
 *
 * Sources: the OSRS Wiki's Ball of wool and Bow string pages (read 2026-10-30), see crafting.json. Guessed: Spin with
 * nothing to spin saying Nothing interesting happens (as cooking's Cook does), the box opening even for one, and the
 * player's animation. Out: sinew, magic roots, yak hair and the other members' spinning.
 */
class Crafting(private val world: World, val data: CraftingData) {
    fun register() {
        for (wheel in wheels(world)) {
            val option = wheel.ops.indexOfFirst { it == SPIN } + 1
            world.handlers.onLoc(wheel.id, option) {
                val held = data.spinning.filter { it.times(player.inventory) > 0 }
                if (held.isEmpty()) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
                world.making.choose(this, held)
            }
            for (recipe in data.spinning) {
                world.handlers.onObjOnLoc(wheel.id, recipe.inputs[0].obj) { world.making.choose(this, listOf(recipe)) }
            }
        }
    }

    companion object {
        const val SPIN = "Spin"

        /** Lumbridge Castle's, on the first floor at 3209, 3212. */
        const val LUMBRIDGE_WHEEL = 14889

        /** Every loc the cache calls a spinning wheel that has a Spin option. */
        fun wheels(world: World) = world.locs.types.all.filter { it.name.equals("Spinning wheel", ignoreCase = true) && SPIN in it.ops }
    }
}
