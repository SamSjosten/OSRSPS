package osrsps.game.skill.magic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.ObjTypes
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.CombatLines
import osrsps.game.combat.RuneCost
import osrsps.game.combat.Spellbook
import osrsps.game.combat.Spells
import osrsps.game.dialogue.Lines
import osrsps.game.script.QueueType
import osrsps.game.script.ScriptContext
import osrsps.game.skill.MakeLines
import osrsps.game.skill.Making

/** What the bolt enchanting says; its words are on the server (data/dialogue.txt), not in git. */
object BoltEnchantLines : Lines("bolt_enchant", "Enchant Crossbow Bolt") {
    val TITLE = line("title", "the box of bolt enchantments the spell opens; words guessed")
    val NO_BOLTS = line("no_bolts", "casting Enchant Crossbow Bolt with no bolts it can enchant; words guessed")
}

/** One bolt the spell enchants: [plain] into [enchanted]. */
data class BoltPair(val plain: Int, val enchanted: Int)

/**
 * One of the spell's enchantments: the sub-spell obj ([obj], whose params hold the [runes]), the Magic [level], the
 * base XP in tenths ([xpTenths]) and the bolts it enchants, the gem's ordinary bolts first ([bolts]).
 */
class BoltEnchantment(val obj: Int, val key: String, val level: Int, val xpTenths: Int, val runes: List<RuneCost>, val bolts: List<BoltPair>) {
    init {
        require(bolts.isNotEmpty()) { "$key enchants no bolts" }
    }
}

/** The enchantments of `skills/bolt-enchant.json`, in the box's order; see [parse]. */
class BoltEnchantData(val enchantments: List<BoltEnchantment>) {
    companion object {
        val NONE = BoltEnchantData(emptyList())

        const val RESOURCE = "/skills/bolt-enchant.json"

        fun load(objTypes: ObjTypes): BoltEnchantData {
            val text = BoltEnchantData::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            return parse(text, objTypes)
        }

        /**
         * Parses the file:
         *
         * ```
         * { "enchants": [ { "spell": "04_enchant_bolt_opal", "level": 4, "xp": 9,
         *                   "bolts": [["opal_bolt", "xbows_crossbow_bolts_bronze_tipped_opal_enchanted"]] } ] }
         * ```
         *
         * Names are gameval names. The runes come from the spell obj's params ([Spells.PARAM_RUNES]), and its name
         * must start with the level. An enchantment whose spell obj the cache lacks is skipped (a test's handful of
         * objs), and so is a bolt pair it lacks; a spell obj without runes fails the file.
         */
        fun parse(text: String, objTypes: ObjTypes, fileName: String = RESOURCE): BoltEnchantData {
            val root = Json.parseToJsonElement(text).jsonObject
            val list = ArrayList<BoltEnchantment>()
            root.required("enchants").jsonArray.forEachIndexed { i, element ->
                try {
                    val entry = element.jsonObject
                    val name = entry.required("spell").jsonPrimitive.content
                    val type = objTypes.find(name) ?: return@forEachIndexed
                    val level = entry.required("level").jsonPrimitive.int
                    require(level in 1..99) { "level $level" }
                    require(name.substringBefore('_').toIntOrNull() == level) { "$name doesn't start with its level $level" }
                    val runes = Spells.PARAM_RUNES.mapNotNull { (runeParam, countParam) ->
                        val rune = type.intParam(runeParam, -1)
                        if (rune < 0) null else RuneCost(rune, type.intParam(countParam, 0).also { require(it >= 1) { "rune $rune costs $it" } })
                    }
                    require(runes.isNotEmpty()) { "$name needs no runes (params ${Spells.PARAM_RUNES.first().first} on)" }
                    val bolts = entry.required("bolts").jsonArray.mapNotNull { pair ->
                        val (plain, enchanted) = pair.jsonArray.map { it.jsonPrimitive.content }.also { require(it.size == 2) { "a bolt pair is two names" } }
                        val from = objTypes.find(plain)
                        val to = objTypes.find(enchanted)
                        if (from == null || to == null) null else BoltPair(from.id, to.id)
                    }
                    if (bolts.isEmpty()) return@forEachIndexed
                    val xp = entry.required("xp").jsonPrimitive.double
                    val tenths = Math.round(xp * 10).toInt()
                    require(tenths > 0 && kotlin.math.abs(tenths - xp * 10) < 1e-6) { "xp $xp isn't a whole number of tenths" }
                    list += BoltEnchantment(type.id, name, level, tenths, runes, bolts)
                } catch (e: Exception) {
                    throw IllegalArgumentException("$fileName: enchants entry ${i + 1}: ${e.message}", e)
                }
            }
            require(list.size <= osrsps.game.dialogue.Chatbox.SKILLMULTI_MAX_ITEMS) { "$fileName: more enchantments than the Make-X box shows" }
            return BoltEnchantData(list)
        }

        private fun JsonObject.required(key: String): JsonElement = get(key) ?: throw IllegalArgumentException("no \"$key\"")
    }
}

/**
 * Enchant Crossbow Bolt: Cast on the magic tab's button opens the Make-X box over the enchantments whose bolts the
 * player holds, each shown as the bolt it makes, and the choice casts that enchantment the picked number of times, one
 * cast every [INTERVAL] ticks, each taking its runes (an elemental staff standing in for its own) and up to [PER_CAST]
 * bolts, giving as many enchanted and the spell's XP, with [ANIM] and [SPOTANIM]. Each cast is a weak queue entry, as
 * the artisan skills' making is ([Making]), so walking off ends it. See `skills/bolt-enchant.json` for what is verified.
 */
class BoltEnchant(private val world: World, private val data: BoltEnchantData) {
    fun register() {
        if (data.enchantments.isEmpty()) return
        world.handlers.onButton(Spells.SPELLBOOK, COMPONENT, CAST) { open() }
    }

    private suspend fun ScriptContext.open() {
        if (Spellbook.of(player) != Spellbook.STANDARD) return
        val text = world.textFor(player)
        val held = data.enchantments.mapNotNull { e -> boltsHeld(e)?.let { e to it } }
        if (held.isEmpty()) {
            player.message(text[BoltEnchantLines.NO_BOLTS])
            return
        }
        val most = held.maxOf { (e, _) -> casts(e) }
        var picked = 0 to 0
        world.dialogues.start(this) { picked = makeX(BoltEnchantLines.TITLE, held.map { it.second.enchanted }, most) }
        val (index, quantity) = picked
        val enchantment = held.getOrNull(index)?.first ?: return
        if (quantity <= 0) return
        world.interfaces.closeChatModal(player)
        world.scripts.clearQueue(player, Making.QUEUE)
        player.interaction = null
        castOne(enchantment, quantity)
    }

    /** The first of [enchantment]'s bolts the player holds, or null. */
    private fun ScriptContext.boltsHeld(enchantment: BoltEnchantment): BoltPair? =
        enchantment.bolts.firstOrNull { player.inventory.count(it.plain) > 0 }

    /** How many casts the held bolts of [enchantment] make, each kind's short last one counting. */
    private fun ScriptContext.casts(enchantment: BoltEnchantment): Int =
        enchantment.bolts.sumOf { ((player.inventory.count(it.plain) + PER_CAST - 1) / PER_CAST).toInt() }

    /** One cast, then the next queued while [left] casts remain and the level, runes and bolts last. */
    private fun ScriptContext.castOne(enchantment: BoltEnchantment, left: Int) {
        val text = world.textFor(player)
        val pair = boltsHeld(enchantment) ?: return
        if (player.skills.currentLevel(Skill.MAGIC) < enchantment.level) {
            player.message(text[CombatLines.MAGIC_LEVEL])
            return
        }
        val provided = world.spells.runesFrom(player.equipment[WearPos.WEAPON]?.id)
        val costs = enchantment.runes.filter { it.rune !in provided }
        val short = costs.firstOrNull { player.inventory.count(it.rune) < it.count }
        if (short != null) {
            player.message(text[CombatLines.NOT_ENOUGH_RUNES, "runes" to plural(short.rune)])
            return
        }
        val count = minOf(PER_CAST.toLong(), player.inventory.count(pair.plain)).toInt()
        val type = world.objTypes[pair.enchanted] ?: return
        val saved = player.inventory.contents().toMap()
        for (cost in costs) Making.removeObj(player.inventory, cost.rune, cost.count)
        Making.removeObj(player.inventory, pair.plain, count)
        if (player.inventory.add(type, count) < count) {
            // Rather than lose the bolts: an enchanted stack that won't fit (a full stack). Guessed, as Making's.
            for (slot in 0 until player.inventory.capacity) player.inventory[slot] = saved[slot]
            player.message(text[MakeLines.NO_SPACE])
            return
        }
        player.skills.addTenths(Skill.MAGIC, enchantment.xpTenths)
        player.anim(ANIM)
        player.spotanim(SPOTANIM)
        if (left <= 1 || boltsHeld(enchantment) == null) return
        queue(Making.QUEUE, QueueType.WEAK, INTERVAL) { castOne(enchantment, left - 1) }
    }

    /** The item's name as the not-enough message has it, as [Teleports] does: Cosmic Runes. */
    private fun plural(item: Int): String {
        val name = world.objTypes[item]?.name ?: "rune"
        return name.split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } } + "s"
    }

    companion object {
        /** magic_spellbook:xbows_enchant, and its op1, Cast. */
        const val COMPONENT = 13
        const val CAST = 1

        /** Bolts a cast enchants (Wiki). */
        const val PER_CAST = 10

        /** Ticks between casts; guessed. */
        const val INTERVAL = 3

        /** `human_xbow_enchant_arrowtip` and `xbows_enchanting_tips_spot`. */
        const val ANIM = 4462
        const val SPOTANIM = 759
    }
}
