package osrsps.game.skill.magic

import osrsps.game.GameLines
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.combat.Spells
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** What alchemy says; its words are on the server (data/dialogue.txt), not in git. */
object AlchemyLines : Lines("alchemy", "High Level Alchemy") {
    val COINS = line("coins", "casting an alchemy spell on coins")
    val CANT = line("cant", "casting an alchemy spell on an item that can't be alchemised; words guessed")
    val WARNING_TITLE = line("warning_title", "the warning over the two options before alchemising a valuable or untradeable item; words guessed", "spell", "item")
    val WARNING_YES = line("warning_yes", "the warning's option that casts the spell; words guessed")
    val WARNING_NO = line("warning_no", "the warning's option that keeps the item; words guessed")
}

/** One alchemy spell: the item spell, and whether it pays the high alchemy value ([high]). */
class AlchemySpell(val spell: ItemSpell, val high: Boolean)

/**
 * Low and High Level Alchemy (Magic utility step U1): cast from the magic tab on an inventory item, the spell turns one
 * of it (one of a stack, noted or not) into coins: 40% of its value for Low Level Alchemy and 60% for High Level
 * Alchemy, rounded down ([ObjType.lowAlch], [ObjType.highAlch]).
 *
 * Verified on the OSRS Wiki (Low Level Alchemy and High Level Alchemy, read 2026-11-19): levels 21 and 55, 31 and 65
 * XP, 3 fire and 5 fire runes with a nature rune (the levels and runes are the cache's own, params 604 and 365 to 370),
 * a cast every 3 and 5 ticks, the yields from the item's value and not its Grand Exchange price, one item of a stack at
 * a time, noted items alchemised as their item, no XP when the Fountain of Rune pays the runes, and the warning: an
 * untradeable item always warns while All Settings 65 is on, and so does an item whose Grand Exchange price is at
 * least the value in All Settings 66 (the Wiki says "1,000 coins or more" by default; the cache's own text says "more
 * than"). Casting doesn't stop the player walking (the Wiki's alching on an Agility course), but it does end an
 * attack (the Wiki's re-aggro tip). The cache marks items that can't be alchemised with param 295.
 *
 * The animations and graphics are the cache's `human_castlowlvlalchemy` (712) with `lowlvlalchemy_casting` (112) and
 * `human_casthighlvlalchemy` (713) with `highlvlalchemy_casting` (113); the `_fire` versions the cache also has are
 * taken to be an animation override and aren't used.
 *
 * The clock, the rune and level checks and the cast's effects are [ItemSpells]'. Guessed: that a stack of
 * coins that can't take the yield (or no room for coins at all) refuses the cast with nothing taken; that an item
 * worth less than 3 coins still goes for no coins; that a rune cast on as the item counts for the spell only if more of it are held; that a tradeable item the price snapshot lacks is judged by its
 * value; that an account that never set All Settings 66 (0) gets no value warning, as the drop warning treats its 0;
 * and the order of the refusals (spellbook, level, coins, can't alchemise, runes, room).
 */
class Alchemy(private val world: World, private val spells: List<AlchemySpell>) {
    fun register() {
        for (alchemy in spells) world.handlers.onSpellOnObj(Spells.SPELLBOOK, alchemy.spell.component) { op -> cast(alchemy, op.sub, op.obj, warned = false) }
    }

    private suspend fun ScriptContext.cast(alchemy: AlchemySpell, slot: Int, obj: Int, warned: Boolean) {
        val spell = alchemy.spell
        val casting = world.itemSpells
        if (!casting.ready(player)) return
        val held = player.inventory[slot]?.takeIf { it.id == obj } ?: return
        val type = world.objTypes[held.id] ?: return
        val base = unnoted(type, world.objTypes)
        val text = world.textFor(player)
        if (!casting.hasLevel(player, spell)) return
        if (base.id == COINS) {
            player.message(text[AlchemyLines.COINS])
            return
        }
        if (base.intParam(ObjType.PARAM_NO_ALCHEMY) != 0) {
            player.message(text[AlchemyLines.CANT])
            return
        }
        val costs = casting.runesToPay(player, spell) ?: return
        if (!warned && warns(player, base)) {
            var yes = false
            world.dialogues.start(this) {
                yes = choice(AlchemyLines.WARNING_YES, AlchemyLines.WARNING_NO, title = text(AlchemyLines.WARNING_TITLE, "spell" to spell.name, "item" to type.name)) == 1
            }
            world.interfaces.closeChatModal(player)
            // The menu waited: everything is checked again, on the same item in the same slot.
            if (yes) cast(alchemy, slot, obj, warned = true)
            return
        }
        val value = if (alchemy.high) base.highAlch else base.lowAlch
        // One transaction: the item, then the runes (the item may be one of them, and paying may free the coins' slot),
        // then the coins; any of it failing puts the backpack back as it was.
        val saved = player.inventory.contents().toMap()
        fun restore() { for (i in 0 until player.inventory.capacity) player.inventory[i] = saved[i] }
        player.inventory.remove(slot, 1)
        if (!casting.pay(player, costs)) {
            restore()
            return
        }
        val coins = world.objTypes[COINS]
        if (value > 0 && (coins == null || player.inventory.add(coins, value) < value)) {
            restore()
            player.message(text[GameLines.NO_SPACE])
            return
        }
        casting.cast(this, spell)
    }

    /** Whether alchemising [type] (unnoted) asks first: All Settings 65 for untradeables, 66 for a price at or above it. */
    private fun warns(player: Player, type: ObjType): Boolean {
        val types = player.vars.types
        if (!type.tradeable) return types.varbit(VARBIT_UNTRADEABLE_WARNING) != null && player.vars.getVarbit(VARBIT_UNTRADEABLE_WARNING) == 1
        if (types.varbit(VARBIT_WARNING_VALUE) == null) return false
        val threshold = player.vars.getVarbit(VARBIT_WARNING_VALUE).takeIf { it > 0 } ?: return false
        val price = world.itemPrices.price(type) ?: type.cost.toLong()
        return price >= threshold
    }

    companion object {
        const val COINS = 995

        /** All Settings 65 ("Casting Alchemy spells on untradeable items always triggers a warning") and 66 (its value). */
        const val VARBIT_UNTRADEABLE_WARNING = 6092
        const val VARBIT_WARNING_VALUE = 6091

        /** OSRS Wiki, High Level Alchemy: the value warning a new account starts with. */
        const val DEFAULT_WARNING_VALUE = 1_000

        /** The item a note stands for, else the item itself. */
        fun unnoted(type: ObjType, objTypes: ObjTypes): ObjType = if (type.isCert) objTypes[type.certlink] ?: type else type

        /** The spells whose objs [objTypes] has (a test's handful of objs may have neither). */
        fun load(objTypes: ObjTypes): List<AlchemySpell> = listOfNotNull(
            ItemSpell.load(objTypes, "21_low_alchemy", 310, 3, anim = 712, spotanim = 112)?.let { AlchemySpell(it, high = false) },
            ItemSpell.load(objTypes, "55_high_alchemy", 650, 5, anim = 713, spotanim = 113)?.let { AlchemySpell(it, high = true) },
        )
    }
}
