package osrsps.game.combat

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.InvObj
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

private val logger = KotlinLogging.logger {}

/** The words of eating, in the dialogue file like every other line of Jagex text. `{food}` is the food's name in lower case. */
object FoodLines : Lines("food", "Food") {
    val EAT = line("eat", "eating a food of one bite", "food")
    val HEALS = line("heals", "after a bite that gave hitpoints back")
    val EAT_HALF = line("eat_half", "the first of a pie's or a pizza's two bites (the Meat pie and Plain pizza pages)", "food")
    val EAT_REST = line("eat_rest", "a pie's or a pizza's second bite", "food")
    val CAKE_FIRST = line("cake_first", "a whole cake's first bite (the Cake page)", "food")
    val CAKE_SECOND = line("cake_second", "the second bite, of the 2/3 cake", "food")
    val CAKE_LAST = line("cake_last", "the last bite, the slice", "food")

    /** The data file's names for them. */
    val BY_NAME: Map<String, Line> = mapOf(
        "eat" to EAT, "eat_half" to EAT_HALF, "eat_rest" to EAT_REST,
        "cake_first" to CAKE_FIRST, "cake_second" to CAKE_SECOND, "cake_last" to CAKE_LAST,
    )
}

/**
 * One bite of a food, by its [obj]: the hitpoints it [heals], what is left in the slot after it ([next], -1 for nothing),
 * the ticks before the next food ([eatDelay]) and those it adds to an attack still waiting ([attackDelay]), whether it
 * is a [combo] food, the [message] line and the [name] that line shows (the whole food's, for a pie's second half).
 * [anglerfish] heals by the Hitpoints level instead of [heals], and past it.
 */
data class Food(
    val obj: Int,
    val name: String,
    val heals: Int,
    val next: Int = -1,
    val eatDelay: Int = Foods.DEFAULT_DELAY,
    val attackDelay: Int = Foods.DEFAULT_DELAY,
    val combo: Boolean = false,
    val message: Line = FoodLines.EAT,
    val anglerfish: Boolean = false,
)

/**
 * Eating (OSRS Wiki, Food and Combo eating, read 2026-10-29). A food's Eat option heals it and starts the eat delay, the
 * ticks before another food: 3 for most, less for pie and pizza bites. While the player's next attack is still waiting,
 * eating adds the food's attack delay to it (3, or 2 for karambwan and halibut); with the weapon ready it adds nothing.
 * Combo foods (karambwan, halibut, gnome crunchies) have a timer of their own, so one can follow other food in the
 * same tick and both heal then; eaten, a combo food holds back both kinds. A bite never heals past the Hitpoints level,
 * except anglerfish, which heals the level / 10 + 2 to 13 by the level's band and as far past it.
 *
 * Guessed: that a combo food blocks other food for its eat delay (the Wiki gives only the order food then combo food);
 * pies' and pizzas' attack delays matching their eat delays, and cakes' 3 for every bite (the Wiki says only that cake
 * bites alternate); "heals some health" only after a bite that gave hitpoints back; that a food can be eaten at full
 * hitpoints; the animation (human_eat), and no sound, as the 241 synth id for eating isn't known. The other edible items
 * (drinks with stat changes, kebabs, teas, hunter meats) come with stat boosts, step 6b.
 */
class Foods(
    val all: Map<Int, Food>,
    private val words: (Player) -> DialogueText = { DialogueText.NONE },
    private val message: (Player, String) -> Unit = Player::message,
) {
    /** Makes Eat on each food eat it. */
    fun register(world: World) {
        for (food in all.values) {
            val option = world.objTypes[food.obj]?.iops?.indexOfFirst { it == EAT }?.takeIf { it >= 0 }?.plus(1) ?: continue
            world.handlers.onObj(food.obj, option) { op -> eat(player, op.sub, food.obj, world.tick) }
        }
    }

    /** [player] eats the [id] in [slot] on [tick]; nothing while its timer runs. Returns false when [id] isn't a food. */
    fun eat(player: Player, slot: Int, id: Int, tick: Long): Boolean {
        val food = all[id] ?: return false
        if (player.dead || player.inventory[slot]?.id != id) return true
        if (tick < if (food.combo) player.comboFoodDelayUntil else player.foodDelayUntil) return true
        player.foodDelayUntil = tick + food.eatDelay
        if (food.combo) player.comboFoodDelayUntil = tick + food.eatDelay
        if (player.nextAttackTick > tick) player.nextAttackTick += food.attackDelay
        player.inventory[slot] = if (food.next >= 0) InvObj(food.next, 1) else null
        player.anim(EAT_ANIM)
        val before = player.hitpoints
        player.hitpoints = healed(player, food)
        val text = words(player)
        message(player, text[food.message, "food" to food.name])
        if (player.hitpoints > before) message(player, text[FoodLines.HEALS])
        return true
    }

    private fun healed(player: Player, food: Food): Int {
        val level = player.skills.baseLevel(Skill.HITPOINTS)
        val hp = player.hitpoints
        if (!food.anglerfish) return maxOf(hp, minOf(hp + food.heals, level))
        val heals = anglerfishHeals(level)
        return maxOf(hp, minOf(hp + heals, level + heals))
    }

    companion object {
        const val RESOURCE = "/food.json"
        const val EAT = "Eat"
        const val DEFAULT_DELAY = 3

        /** human_eat. */
        const val EAT_ANIM = 829

        /** The Hitpoints level / 10, plus 2, 4, 6, 8 or 13 for the bands below 25, 50, 75, 93 and up (OSRS Wiki, Anglerfish). */
        fun anglerfishHeals(level: Int): Int = level / 10 + when {
            level < 25 -> 2
            level < 50 -> 4
            level < 75 -> 6
            level < 93 -> 8
            else -> 13
        }

        /** Reads [RESOURCE]; see [parse]. */
        fun load(objTypes: ObjTypes): Map<Int, Food> {
            val text = Foods::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            return parse(text, objTypes)
        }

        /**
         * The foods in [text], by cache internal name; one the cache doesn't have is left out with a warning, as is a
         * food whose [Food.next] it doesn't have. A bite's [Food.name] is the first bite's, found by following the nexts.
         */
        fun parse(text: String, objTypes: ObjTypes, fileName: String = RESOURCE): Map<Int, Food> {
            val root = Json.parseToJsonElement(text).jsonObject
            val unknown = ArrayList<String>()
            val foods = LinkedHashMap<Int, Food>()
            root.required("foods").jsonArray.forEachIndexed { i, element ->
                try {
                    val entry = element.jsonObject
                    val unknownKeys = entry.keys - KEYS
                    require(unknownKeys.isEmpty()) { "unknown keys ${unknownKeys.joinToString()}" }
                    val objName = entry.required("food").jsonPrimitive.content
                    val type = objTypes.find(objName)
                    val nextName = entry["next"]?.jsonPrimitive?.content
                    val next = nextName?.let { objTypes.find(it) }
                    if (type == null || (nextName != null && next == null)) {
                        unknown += if (type == null) objName else nextName!!
                        return@forEachIndexed
                    }
                    require(type.iops.any { it == EAT }) { "$objName has no $EAT option" }
                    require(type.id !in foods) { "$objName is listed twice" }
                    fun delay(key: String) = entry[key]?.jsonPrimitive?.int?.also { require(it in 1..10) { "\"$key\": $it" } } ?: DEFAULT_DELAY
                    val message = entry["message"]?.jsonPrimitive?.content?.let { FoodLines.BY_NAME[it] ?: throw IllegalArgumentException("\"message\": \"$it\"") }
                    foods[type.id] = Food(
                        obj = type.id,
                        name = type.name.lowercase(),
                        heals = entry.required("heals").jsonPrimitive.int.also { require(it in 0..99) { "heals $it" } },
                        next = next?.id ?: -1,
                        eatDelay = delay("eatDelay"),
                        attackDelay = delay("attackDelay"),
                        combo = entry["combo"]?.jsonPrimitive?.boolean ?: false,
                        message = message ?: FoodLines.EAT,
                        anglerfish = entry["anglerfish"]?.jsonPrimitive?.boolean ?: false,
                    )
                } catch (e: Exception) {
                    throw IllegalArgumentException("$fileName: foods entry ${i + 1}: ${e.message}", e)
                }
            }
            if (unknown.isNotEmpty()) logger.warn { "$fileName: skipped foods the cache doesn't have: ${unknown.joinToString()}" }
            // A later bite shows the whole food's name: "the remaining meat pie", not "half a meat pie".
            val firstOf = HashMap<Int, Int>()
            for (food in foods.values) if (food.next in foods) firstOf[food.next] = food.obj
            return foods.mapValues { (id, food) ->
                var first = id
                while (true) first = firstOf[first] ?: break
                food.copy(name = foods.getValue(first).name)
            }
        }

        private val KEYS = setOf("food", "heals", "next", "eatDelay", "attackDelay", "combo", "message", "anglerfish")

        private fun JsonObject.required(key: String): JsonElement = get(key) ?: throw IllegalArgumentException("no \"$key\"")
    }
}
