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
import osrsps.game.Movement
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

private val logger = KotlinLogging.logger {}

/** The words of drinking, in the dialogue file like every other line of Jagex text. `{potion}` is the potion's name in lower case, without its doses. */
object PotionLines : Lines("potion", "Potions") {
    val DRINK = line("drink", "a dose of a potion whose cache name ends in 'potion' (Attack potion)", "potion")
    val DRINK_POTION = line("drink_potion", "a dose of one whose doesn't (Super attack), the words adding it", "potion")
    val DRINK_BREW = line("drink_brew", "a dose of a brew (Saradomin brew, Zamorak brew, Ancient brew, Forgotten brew)", "potion")
    val DOSES_LEFT = line("doses_left", "after a dose, with {doses} doses left", "doses")
    val DOSE_LEFT = line("dose_left", "after a dose, with one dose left")
    val FINISHED = line("finished", "after the last dose")
    val DIVINE_HITPOINTS = line("divine_hitpoints", "a divine potion with 10 hitpoints or fewer (the Divine super combat potion page)")

    /** The data file's names for the first lines. */
    val BY_NAME: Map<String, Line> = mapOf("drink" to DRINK, "drink_potion" to DRINK_POTION, "drink_brew" to DRINK_BREW)
}

/**
 * What a dose does to a stat, in order. [Boost] raises [skills] by [percent] of the base level plus [constant], up to
 * that much past the base and never lowering one already higher; [Restore] raises them toward the base level ([over]
 * percent of it past, for the brews' prayer points), and [wrench] more percent of Prayer with a holy wrench or a prayer
 * cape; [Drain] lowers them by [percent] of the current level plus [constant], to 0 at the least; [Damage] takes
 * [percent] of the current hitpoints, down to 1 at the least; [Energy] gives back [percent] run energy.
 */
sealed interface PotionEffect {
    data class Boost(val skills: List<Skill>, val percent: Int, val constant: Int) : PotionEffect

    data class Restore(val skills: List<Skill>, val percent: Int, val constant: Int, val over: Int = 0, val wrench: Int = 0) : PotionEffect

    data class Drain(val skills: List<Skill>, val percent: Int, val constant: Int) : PotionEffect

    data class Damage(val percent: Int) : PotionEffect

    data class Energy(val percent: Int) : PotionEffect
}

/**
 * A potion by its doses' objs, most first ([doses], 4 to 1); the dose objs left out are those the cache doesn't have.
 * [name] is the cache's without the dose count, in lower case. A divine potion costs 10 hitpoints and holds what it
 * boosts for five minutes, counted down in its [divineVarbit] (-1 for other potions).
 */
data class Potion(
    val name: String,
    val doses: List<Int>,
    val effects: List<PotionEffect>,
    val message: Line = PotionLines.DRINK,
    val divineVarbit: Int = -1,
) {
    val divine: Boolean get() = divineVarbit >= 0

    /** The skills its boosts raise. */
    val boosted: Set<Skill> get() = effects.filterIsInstance<PotionEffect.Boost>().flatMap { it.skills }.toSet()
}

/**
 * A divine potion's hold: while [varbit] (the cache's `divine*_potion_time`, ticks left, which RuneLite's Timers & Buffs
 * shows) isn't 0, [skills] don't wear off; see [StatRestore].
 */
data class DivineHold(val varbit: Int, val skills: Set<Skill>)

/**
 * Drinking (OSRS Wiki, Potions, Combo eating and each potion's page, read 2026-10-29). A dose's Drink option applies its
 * effects, leaves the next dose or a vial, and starts the drink delay, 3 ticks on a timer of its own: a potion can be
 * drunk in the tick that food was eaten, and a combo food after it, but drinking holds back ordinary food for the delay
 * (Combo eating: food, potion, combo food, in that order). It adds nothing to the attack delay. A divine potion needs
 * more than 10 hitpoints, takes 10 and holds the stats it boosts from wearing off for five minutes ([StatRestore]), in
 * varbits that are saved with the rest, so the hold goes on over a logout and RuneLite shows its timer.
 *
 * Guessed: a boost on a drained stat adding to the current level, capped at the boost past the base (RSMod's statBoost);
 * Prayer restores going by the base Prayer level where the Wiki says "current Prayer level"; the hitsplats for the
 * divine and Zamorak brew hitpoints, from no one; that a divine dose drunk during the effect starts the five minutes
 * again, and the hold carrying on over a logout; the animation (human_eat, as for food), and no sound, as the 241 synth id for drinking isn't known. Stamina
 * potions, antifire and the antipoisons (step 6d) aren't here yet, nor barbarian mixes, which share the food timer.
 */
class Potions(
    private val data: Data,
    private val words: (Player) -> DialogueText = { DialogueText.NONE },
    private val message: (Player, String) -> Unit = Player::message,
) {
    /** The potions, by dose obj. */
    val all: Map<Int, Potion> get() = data.potions

    /** Makes Drink on each dose drink it. */
    fun register(world: World) {
        for (id in all.keys) {
            val option = world.objTypes[id]?.iops?.indexOfFirst { it == DRINK }?.takeIf { it >= 0 }?.plus(1) ?: continue
            world.handlers.onObj(id, option) { op -> drink(player, op.sub, id, world.tick) }
        }
    }

    /** [player] drinks the [id] in [slot] on [tick]; nothing while the drink delay runs. Returns false when [id] isn't a potion. */
    fun drink(player: Player, slot: Int, id: Int, tick: Long): Boolean {
        val potion = all[id] ?: return false
        if (player.dead || player.inventory[slot]?.id != id || tick < player.potionDelayUntil) return true
        val text = words(player)
        if (potion.divine && player.hitpoints <= DIVINE_COST) {
            message(player, text[PotionLines.DIVINE_HITPOINTS])
            return true
        }
        player.potionDelayUntil = tick + DRINK_DELAY
        player.foodDelayUntil = maxOf(player.foodDelayUntil, tick + DRINK_DELAY)
        val left = potion.doses.size - 1 - potion.doses.indexOf(id)
        player.inventory[slot] = if (left > 0) InvObj(potion.doses[potion.doses.size - left], 1) else if (data.vial >= 0) InvObj(data.vial, 1) else null
        player.anim(Foods.EAT_ANIM)
        message(player, text[potion.message, "potion" to potion.name])
        for (effect in potion.effects) apply(player, effect)
        if (potion.divine) {
            damage(player, DIVINE_COST)
            // One over, as this tick's StatRestore counts it down: the client sees 500, and the hold ends 500 ticks on.
            if (player.vars.types.varbit(potion.divineVarbit) != null) player.vars.setVarbit(potion.divineVarbit, DIVINE_TICKS + 1)
        }
        message(player, when (left) {
            0 -> text[PotionLines.FINISHED]
            1 -> text[PotionLines.DOSE_LEFT]
            else -> text[PotionLines.DOSES_LEFT, "doses" to left.toString()]
        })
        return true
    }

    private fun apply(player: Player, effect: PotionEffect) {
        val skills = player.skills
        when (effect) {
            is PotionEffect.Boost -> for (skill in effect.skills) {
                val base = skills.baseLevel(skill)
                val boost = base * effect.percent / 100 + effect.constant
                val current = skills.currentLevel(skill)
                val boosted = minOf(base + boost, current + boost)
                if (boosted > current) skills.setCurrentLevel(skill, boosted)
            }
            is PotionEffect.Restore -> for (skill in effect.skills) {
                val base = skills.baseLevel(skill)
                val percent = effect.percent + if (skill == Skill.PRAYER && hasWrench(player)) effect.wrench else 0
                val current = skills.currentLevel(skill)
                val restored = minOf(base + base * effect.over / 100, current + base * percent / 100 + effect.constant)
                if (restored > current) skills.setCurrentLevel(skill, restored)
            }
            is PotionEffect.Drain -> for (skill in effect.skills) {
                val current = skills.currentLevel(skill)
                skills.setCurrentLevel(skill, maxOf(0, current - current * effect.percent / 100 - effect.constant))
            }
            is PotionEffect.Damage -> damage(player, minOf(player.hitpoints * effect.percent / 100, player.hitpoints - 1))
            is PotionEffect.Energy -> player.runEnergy = minOf(Movement.MAX_ENERGY, player.runEnergy + Movement.MAX_ENERGY * effect.percent / 100)
        }
    }

    /** Ends every divine hold on [player] at once, leaving the levels as they are: a scenario starting. */
    fun clearDivine(player: Player) {
        for (hold in data.divineHolds) if (player.vars.types.varbit(hold.varbit) != null) player.vars.setVarbit(hold.varbit, 0)
    }

    private fun hasWrench(player: Player): Boolean =
        player.inventory.contents().any { (_, obj) -> obj.id in data.wrenchCarried } ||
            player.equipment.contents().any { (_, obj) -> obj.id in data.wrenchCarried || obj.id in data.wrenchWorn }

    /** [amount] of [player]'s hitpoints taken by the potion itself, shown as a hitsplat from no one. */
    private fun damage(player: Player, amount: Int) {
        if (amount <= 0) return
        player.hitpoints -= amount
        player.effects.hit(-1, HitsplatType.DAMAGE_ME, HitsplatType.DAMAGE_OTHER, HitsplatType.DAMAGE_OTHER, amount)
        player.effects.headBar(-1, MeleeCombat.HEALTH_BAR, MeleeCombat.healthBarFill(player.hitpoints, player.skills.baseLevel(Skill.HITPOINTS)))
    }

    /**
     * What [load] reads: the potions, by dose obj; the objs that give [PotionEffect.Restore.wrench] carried or worn, and
     * those that give it worn only; and the vial a last dose leaves, -1 for none.
     */
    class Data(val potions: Map<Int, Potion>, val wrenchCarried: Set<Int> = emptySet(), val wrenchWorn: Set<Int> = emptySet(), val vial: Int = -1) {
        /** Each divine potion's hold, for [StatRestore]. */
        val divineHolds: List<DivineHold> = potions.values.filter { it.divine }.distinct().map { DivineHold(it.divineVarbit, it.boosted) }

        companion object {
            val NONE = Data(emptyMap())
        }
    }

    companion object {
        const val RESOURCE = "/potions.json"
        const val DRINK = "Drink"
        const val DRINK_DELAY = 3

        /** What a divine dose costs, and the hitpoints it needs more than. */
        const val DIVINE_COST = 10

        /** Five minutes. */
        const val DIVINE_TICKS = 500

        /** The 241 cache's `<potion>_potion_time` varbit for each divine potion, by its doses' stem. */
        val DIVINE_VARBITS = mapOf(
            "divineattack" to 8429, "divinestrength" to 8430, "divinedefence" to 8431, "divinerange" to 8432, "divinemagic" to 8433,
            "divinecombat" to 13663, "divinebastion" to 13664, "divinebattlemage" to 13665,
        )

        /** Reads [RESOURCE]; see [parse]. */
        fun load(objTypes: ObjTypes): Data {
            val text = Potions::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            return parse(text, objTypes)
        }

        /**
         * The potions in [text], their doses found by cache internal name as `<n>dose<potion>`; a potion the cache has no
         * dose of is left out with a warning, as are wrench objs and a vial it doesn't have.
         */
        fun parse(text: String, objTypes: ObjTypes, fileName: String = RESOURCE): Data {
            val root = Json.parseToJsonElement(text).jsonObject
            val unknown = ArrayList<String>()
            fun objs(key: String): Set<Int> = root[key]?.jsonArray.orEmpty().mapNotNull {
                val name = it.jsonPrimitive.content
                objTypes.find(name)?.id.also { if (it == null) unknown += name }
            }.toSet()
            val potions = LinkedHashMap<Int, Potion>()
            root.required("potions").jsonArray.forEachIndexed { i, element ->
                try {
                    val entry = element.jsonObject
                    val unknownKeys = entry.keys - KEYS
                    require(unknownKeys.isEmpty()) { "unknown keys ${unknownKeys.joinToString()}" }
                    val stem = entry.required("potion").jsonPrimitive.content
                    val types = (4 downTo 1).mapNotNull { objTypes.find("${it}dose$stem") }
                    if (types.isEmpty()) {
                        unknown += "4dose$stem"
                        return@forEachIndexed
                    }
                    for (type in types) {
                        require(type.iops.any { it == DRINK }) { "${type.internalName} has no $DRINK option" }
                        require(type.id !in potions) { "${type.internalName} is listed twice" }
                    }
                    val message = entry["message"]?.jsonPrimitive?.content?.let { PotionLines.BY_NAME[it] ?: throw IllegalArgumentException("\"message\": \"$it\"") }
                    val name = types.first().name.substringBefore('(').trim().lowercase()
                    val potion = Potion(
                        name = name,
                        doses = types.map { it.id },
                        effects = entry.required("effects").jsonArray.map { effect(it.jsonObject) },
                        message = message ?: if (name.endsWith("potion")) PotionLines.DRINK else PotionLines.DRINK_POTION,
                        divineVarbit = if (entry["divine"]?.jsonPrimitive?.boolean == true) DIVINE_VARBITS[stem] ?: throw IllegalArgumentException("no divine varbit for $stem") else -1,
                    )
                    for (type in types) potions[type.id] = potion
                } catch (e: Exception) {
                    throw IllegalArgumentException("$fileName: potions entry ${i + 1}: ${e.message}", e)
                }
            }
            val wrenchCarried = objs("wrenchCarried")
            val wrenchWorn = objs("wrenchWorn")
            val vialName = root.required("vial").jsonPrimitive.content
            val vial = objTypes.find(vialName)?.id ?: run {
                unknown += vialName
                -1
            }
            if (unknown.isNotEmpty()) logger.warn { "$fileName: skipped objs the cache doesn't have: ${unknown.joinToString()}" }
            return Data(potions, wrenchCarried, wrenchWorn, vial)
        }

        private fun effect(entry: JsonObject): PotionEffect {
            val kind = EFFECTS.firstOrNull { it in entry } ?: throw IllegalArgumentException("an effect without one of ${EFFECTS.joinToString()}")
            val unknownKeys = entry.keys - EFFECT_KEYS - kind
            require(unknownKeys.isEmpty()) { "unknown effect keys ${unknownKeys.joinToString()}" }
            fun number(key: String, default: Int = 0) = entry[key]?.jsonPrimitive?.int?.also { require(it in 0..100) { "\"$key\": $it" } } ?: default
            val skills by lazy { skills(entry.getValue(kind)) }
            return when (kind) {
                "boost" -> PotionEffect.Boost(skills, number("percent"), number("constant"))
                "restore" -> PotionEffect.Restore(skills, number("percent"), number("constant"), number("over"), number("wrench"))
                "drain" -> PotionEffect.Drain(skills, number("percent"), number("constant"))
                "damage" -> PotionEffect.Damage(entry.getValue(kind).jsonPrimitive.int.also { require(it in 0..100) { "\"damage\": $it" } })
                else -> PotionEffect.Energy(entry.getValue(kind).jsonPrimitive.int.also { require(it in 0..100) { "\"energy\": $it" } })
            }
        }

        /** A list of skill names, or "all" for every skill but Hitpoints. */
        private fun skills(element: JsonElement): List<Skill> {
            val names = element.jsonArray.map { it.jsonPrimitive.content }
            if (names == listOf("all")) return Skill.entries.filter { it != Skill.HITPOINTS }
            return names.map { name -> Skill.find(name) ?: throw IllegalArgumentException("no skill '$name'") }
        }

        private val KEYS = setOf("potion", "effects", "message", "divine")
        private val EFFECTS = listOf("boost", "restore", "drain", "damage", "energy")
        private val EFFECT_KEYS = setOf("percent", "constant", "over", "wrench")

        private fun JsonObject.required(key: String): JsonElement = get(key) ?: throw IllegalArgumentException("no \"$key\"")
    }
}
