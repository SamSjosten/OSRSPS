package osrsps.game.skill.magic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.InvObj
import osrsps.game.ObjTypes
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.Spells
import osrsps.game.dialogue.Lines
import osrsps.game.script.QueueType
import osrsps.game.script.ScriptContext

/** What jewellery enchanting says; its words are on the server (data/dialogue.txt), not in git. */
object JewelleryEnchantLines : Lines("jewellery_enchant", "Jewellery Enchantments") {
    val WRONG_ITEM = line("wrong_item", "casting an enchantment on an item that spell doesn't enchant; words guessed")
}

/** One item a spell enchants: [plain] becomes [enchanted]; [ring] picks the ring's animation and graphic. */
data class JewelleryPair(val plain: Int, val enchanted: Int, val ring: Boolean)

/** One of the seven spells: the item spell, its level (1 to 7) and the items it enchants. */
class JewelleryEnchantment(val spell: ItemSpell, val tier: Int, val items: List<JewelleryPair>)

/** The spells of `skills/jewellery-enchant.json`; see [parse]. */
class JewelleryEnchantData(val enchantments: List<JewelleryEnchantment>) {
    companion object {
        const val RESOURCE = "/skills/jewellery-enchant.json"

        fun load(objTypes: ObjTypes): JewelleryEnchantData {
            val text = JewelleryEnchantData::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            return parse(text, objTypes)
        }

        /**
         * Parses `{ "enchants": [ { "spell": "07_enchant_amulet_lvl1", "xp": 17.5, "items": [["sapphire_ring", "ring_of_recoil"]] } ] }`,
         * names being gameval names, in level order. The level, runes and component come from the spell obj ([ItemSpell.load]).
         * A spell or pair the cache lacks is skipped (a test's handful of objs).
         */
        fun parse(text: String, objTypes: ObjTypes, fileName: String = RESOURCE): JewelleryEnchantData {
            val root = Json.parseToJsonElement(text).jsonObject
            val list = ArrayList<JewelleryEnchantment>()
            root.required("enchants").jsonArray.forEachIndexed { i, element ->
                try {
                    val entry = element.jsonObject
                    val key = entry.required("spell").jsonPrimitive.content
                    val xp = entry.required("xp").jsonPrimitive.double
                    val tenths = Math.round(xp * 10).toInt()
                    require(tenths > 0 && kotlin.math.abs(tenths - xp * 10) < 1e-6) { "xp $xp isn't a whole number of tenths" }
                    val tier = i + 1
                    val (anim, spotanim) = ANIMS[tier - 1]
                    val spell = ItemSpell.load(objTypes, key, tenths, CAST_TICKS, anim, spotanim) ?: return@forEachIndexed
                    val items = entry.required("items").jsonArray.mapNotNull { pair ->
                        val (plain, enchanted) = pair.jsonArray.map { it.jsonPrimitive.content }.also { require(it.size == 2) { "an item pair is two names" } }
                        val from = objTypes.find(plain)
                        val to = objTypes.find(enchanted)
                        if (from == null || to == null) null else JewelleryPair(from.id, to.id, from.wearpos1 == WearPos.RING)
                    }
                    list += JewelleryEnchantment(spell, tier, items)
                } catch (e: Exception) {
                    throw IllegalArgumentException("$fileName: enchants entry ${i + 1}: ${e.message}", e)
                }
            }
            val plains = list.flatMap { e -> e.items.map { it.plain } }
            require(plains.size == plains.toSet().size) { "$fileName: an item is listed twice" }
            return JewelleryEnchantData(list)
        }

        /**
         * The cast's animation and graphic by level: `human_enchantamuletlvl1` to `3` (719 to 721) with
         * `enchant_amulet_lvl1` to `3` (114 to 116), then `enchant_amulet2_lvl4` to `6` (153, 154, 452). Rings play
         * [RING_ANIM] with [RING_SPOTANIM] instead.
         */
        private val ANIMS = listOf(719 to 114, 720 to 115, 721 to 116, 721 to 153, 721 to 154, 721 to 452, 721 to 452)

        /** `human_cast_enchantring` and `enchant_ring`. */
        const val RING_ANIM = 931
        const val RING_SPOTANIM = 238

        /** OSRS Wiki, each spell: cast by hand every 3 ticks. */
        const val CAST_TICKS = 3

        private fun JsonObject.required(key: String): JsonElement = get(key) ?: throw IllegalArgumentException("no \"$key\"")
    }
}

/**
 * Lvl-1 to Lvl-7 Enchant (Magic utility step U3): cast from the magic tab on a piece of jewellery, the spell turns it
 * into its enchanted form in the same slot, then goes on enchanting the next of the same item every [RECAST_TICKS]
 * ticks until none are left, the runes or level run out, or the player does something else.
 *
 * Verified on the OSRS Wiki (Lvl-1 Enchant to Lvl-7 Enchant, Jewellery Enchantments and Enchantment spells, read
 * 2026-11-19): the levels, runes and XP (levels and runes the cache's own), each spell's items and what they become,
 * the casts by hand every 3 ticks and the recast every 7 when left alone (the 21 February 2019 change). The magic tab's
 * Jewellery Enchantments button (218:15) only shows the seven spells ("View the various jewellery enchantment spells",
 * its obj's param 602), which is inferred to be the client's own doing, so the server has nothing to do for it.
 *
 * Guessed: the animations for Lvl-4 and up (Lvl-3's, with each level's graphic, Lvl-7 sharing Lvl-6's) and for rings
 * (`human_cast_enchantring` with `enchant_ring` at every level); the message for an item the spell doesn't enchant;
 * that the recast is a weak queue, as making is, so walking or another click ends it; that it takes the first of the
 * same item in the backpack; and that Lvl-5 makes the uncharged dragonstone jewellery, as the Wiki lists it.
 */
class JewelleryEnchant(private val world: World, private val data: JewelleryEnchantData) {
    fun register() {
        for (enchantment in data.enchantments) {
            world.handlers.onSpellOnObj(Spells.SPELLBOOK, enchantment.spell.component) { op ->
                world.itemSpells.castOrQueue(this) { cast(enchantment, op.sub, op.obj) }
            }
        }
    }

    private fun ScriptContext.cast(enchantment: JewelleryEnchantment, slot: Int, obj: Int) {
        val spell = enchantment.spell
        val casting = world.itemSpells
        if (player.inventory[slot]?.id != obj) return
        val pair = enchantment.items.firstOrNull { it.plain == obj }
        if (pair == null) {
            player.message(world.textFor(player)[JewelleryEnchantLines.WRONG_ITEM])
            return
        }
        if (!casting.hasLevel(player, spell)) return
        val costs = casting.runesToPay(player, spell) ?: return
        // The enchanted piece takes the plain one's slot.
        player.inventory[slot] = InvObj(pair.enchanted, 1)
        if (!casting.pay(player, costs)) {
            player.inventory[slot] = InvObj(obj, 1)
            return
        }
        if (pair.ring) casting.cast(this, spell, JewelleryEnchantData.RING_ANIM, JewelleryEnchantData.RING_SPOTANIM)
        else casting.cast(this, spell)
        val next = player.inventory.contents().firstOrNull { it.second.id == obj }?.first ?: return
        queue(QUEUE, QueueType.WEAK, RECAST_TICKS) { cast(enchantment, next, obj) }
    }

    companion object {
        /** OSRS Wiki, Enchantment spells: left alone, the spell casts again every 7 ticks. */
        const val RECAST_TICKS = 7

        const val QUEUE = "jewellery_enchant"
    }
}
