package osrsps.game.skill.magic

import osrsps.game.ObjTypes
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.Spells
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.skill.Bar
import osrsps.game.skill.Making
import osrsps.game.skill.Smithing
import osrsps.game.skill.SmithingLines

/** What Superheat Item says; its words are on the server (data/dialogue.txt), not in git. */
object SuperheatLines : Lines("superheat", "Superheat Item") {
    val NOT_ORE = line("not_ore", "casting Superheat Item on something that isn't ore (the Wiki's trivia)")
    val ELEMENTAL = line("elemental", "casting Superheat Item on elemental ore (the Wiki's trivia)")
}

/**
 * Superheat Item (Magic utility step U2): cast from the magic tab on an ore in the inventory, the spell smelts the bar
 * that ore makes there and then, with the ore and coal a furnace would take ([Smithing.BARS]), for the bar's smelting
 * XP as well as the spell's.
 *
 * Verified on the OSRS Wiki (Superheat Item and Iron bar, read 2026-11-19): level 43, 53 XP, 4 fire runes and a nature
 * rune (the cache's own), a cast every 3 ticks, the furnace's Smithing level, coal and XP, iron ore with at least two
 * coal making a steel bar and otherwise an iron bar that never fails as it can at a furnace, noted ore refused, and
 * the two refusals the Wiki quotes: anything that isn't ore, which also shows the splash graphic (combat's
 * `failedspell_impact`, at the height guessed), and elemental ore.
 * A cast clicked before the last one's 3 ticks are up is kept and cast then (the Wiki's changes: "will now queue up
 * one spell if you cast them too quickly"), checked again as it is.
 * The animation and graphic are the cache's `human_castsuperheatitem` (725) and `superheatitem_casting` (148).
 *
 * Guessed: bronze cast on either copper or tin; coal itself counted as not ore; the furnace's level and missing-ore
 * messages (`smithing.smelt_level`, `smithing.smelt_no_ore`) for too low a Smithing level or too little of the rest,
 * with nothing taken; no message for the bar itself; nothing taken on a refusal; the refusal order (spellbook,
 * Magic level, not ore, Smithing level, the rest of the ore, runes). The clock and the cast's effects are
 * [ItemSpells]'.
 *
 * Not built here: the goldsmith gauntlets' and Smithing cape's gold XP and the smithing catalyst (the furnace doesn't
 * have them yet either), and the bars no furnace here makes (blurite, lovakite, the Varlamore bars).
 */
class Superheat(private val world: World, private val spell: ItemSpell?) {
    fun register() {
        val spell = spell ?: return
        world.handlers.onSpellOnObj(Spells.SPELLBOOK, spell.component) { op -> world.itemSpells.castOrQueue(this) { cast(spell, op.sub, op.obj) } }
    }

    private fun ScriptContext.cast(spell: ItemSpell, slot: Int, obj: Int) {
        val casting = world.itemSpells
        if (player.inventory[slot]?.id != obj) return
        val text = world.textFor(player)
        if (!casting.hasLevel(player, spell)) return
        if (obj == ELEMENTAL_ORE) {
            player.message(text[SuperheatLines.ELEMENTAL])
            return
        }
        val bar = barFor(obj, player.inventory.count(COAL))
        if (bar == null) {
            player.message(text[SuperheatLines.NOT_ORE])
            if (world.spells.splash >= 0) player.spotanim(world.spells.splash, ItemSpells.SPOTANIM_HEIGHT)
            return
        }
        if (player.skills.currentLevel(Skill.SMITHING) < bar.level) {
            player.message(text[SmithingLines.SMELT_LEVEL, "level" to bar.level, "bar" to bar.key])
            return
        }
        if (!bar.ores.all { (ore, count) -> player.inventory.count(ore) >= count }) {
            player.message(text[SmithingLines.SMELT_NO_ORE, "bar" to bar.key])
            return
        }
        val costs = casting.runesToPay(player, spell) ?: return
        val barType = world.objTypes[bar.obj] ?: return
        // One transaction, as alchemy's: the ore cast on goes first (so its slot is the one the bar can fill), then the
        // rest of the ore, then the runes; the runes falling short puts the backpack back as it was.
        val saved = player.inventory.contents().toMap()
        player.inventory.remove(slot, 1)
        for ((ore, count) in bar.ores) Making.removeObj(player.inventory, ore, if (ore == obj) count - 1 else count)
        if (!casting.pay(player, costs)) {
            for (i in 0 until player.inventory.capacity) player.inventory[i] = saved[i]
            return
        }
        player.inventory.add(barType, 1)
        player.skills.addTenths(Skill.SMITHING, bar.smeltXpTenths)
        casting.cast(this, spell)
    }

    companion object {
        const val COAL = 453
        const val IRON_ORE = 440

        /** `elemental_workshop_ore`. */
        const val ELEMENTAL_ORE = 2892

        /**
         * The bar ore [obj] makes: iron ore makes steel with [coal] of at least two (the Wiki's Superheat Item), and
         * otherwise the furnace bar whose ores, coal aside, include it. Null for anything else, coal among them.
         */
        fun barFor(obj: Int, coal: Long): Bar? {
            if (obj == COAL) return null
            if (obj == IRON_ORE) return if (coal >= 2) Smithing.STEEL else Smithing.IRON
            return Smithing.BARS.firstOrNull { bar -> bar.ores.any { it.first == obj } }
        }

        fun load(objTypes: ObjTypes): ItemSpell? = ItemSpell.load(objTypes, "43_superheat_item", 530, 3, anim = 725, spotanim = 148)
    }
}
