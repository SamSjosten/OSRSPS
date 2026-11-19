package osrsps.game.skill.magic

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.FountainOfRune
import osrsps.game.GameFrame
import osrsps.game.GameLines
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.CombatLines
import osrsps.game.combat.RuneCost
import osrsps.game.combat.Spellbook
import osrsps.game.combat.Spells
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.skill.Making

private val logger = KotlinLogging.logger {}

/** What alchemy says; its words are on the server (data/dialogue.txt), not in git. */
object AlchemyLines : Lines("alchemy", "High Level Alchemy") {
    val COINS = line("coins", "casting an alchemy spell on coins")
    val CANT = line("cant", "casting an alchemy spell on an item that can't be alchemised; words guessed")
    val WARNING_TITLE = line("warning_title", "the warning over the two options before alchemising a valuable or untradeable item; words guessed", "spell", "item")
    val WARNING_YES = line("warning_yes", "the warning's option that casts the spell; words guessed")
    val WARNING_NO = line("warning_no", "the warning's option that keeps the item; words guessed")
}

/**
 * One alchemy spell: its spell obj ([obj], [key]), the magic tab [component], the Magic [level] and [runes] (all the
 * cache's), the base XP in tenths ([xpTenths]), the ticks before the next cast ([ticks]), whether it pays the high
 * alchemy value ([high]) and its animation and graphic.
 */
class AlchemySpell(
    val obj: Int,
    val key: String,
    val name: String,
    val component: Int,
    val level: Int,
    val runes: List<RuneCost>,
    val xpTenths: Int,
    val ticks: Int,
    val high: Boolean,
    val anim: Int,
    val spotanim: Int,
)

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
 * Guessed: the graphic's height (92, the teleports'); that the magic tab comes back after a cast; that a click before
 * the spell's ticks are up is dropped rather than queued, and that the two spells share one clock; that a stack of
 * coins that can't take the yield (or no room for coins at all) refuses the cast with nothing taken; that an item
 * worth less than 3 coins still goes for no coins; that a tradeable item the price snapshot lacks is judged by its
 * value; that an account that never set All Settings 66 (0) gets no value warning, as the drop warning treats its 0;
 * and the order of the refusals (spellbook, level, coins, can't alchemise, runes, room).
 */
class Alchemy(private val world: World, private val spells: List<AlchemySpell>) {
    /** The tick before which each player can't cast alchemy again. */
    private val nextCast = java.util.WeakHashMap<Player, Long>()

    fun register() {
        for (spell in spells) world.handlers.onSpellOnObj(Spells.SPELLBOOK, spell.component) { op -> cast(spell, op.sub, op.obj, warned = false) }
    }

    private suspend fun ScriptContext.cast(spell: AlchemySpell, slot: Int, obj: Int, warned: Boolean) {
        if (Spellbook.of(player) != Spellbook.STANDARD) return
        val held = player.inventory[slot]?.takeIf { it.id == obj } ?: return
        val type = world.objTypes[held.id] ?: return
        val base = unnoted(type, world.objTypes)
        if ((nextCast[player] ?: 0L) > world.tick) return
        val text = world.textFor(player)
        if (player.skills.currentLevel(Skill.MAGIC) < spell.level) {
            player.message(text[CombatLines.MAGIC_LEVEL])
            return
        }
        if (base.id == COINS) {
            player.message(text[AlchemyLines.COINS])
            return
        }
        if (base.intParam(ObjType.PARAM_NO_ALCHEMY) != 0) {
            player.message(text[AlchemyLines.CANT])
            return
        }
        val costs = FountainOfRune.runesToPay(player, spell.runes, world.spells.runesFrom(player.equipment[WearPos.WEAPON]?.id))
        val short = costs.firstOrNull { player.inventory.count(it.rune) < it.count }
        if (short != null) {
            player.message(text[CombatLines.NOT_ENOUGH_RUNES, "runes" to plural(short.rune)])
            return
        }
        if (!warned && warns(player, base)) {
            var yes = false
            world.dialogues.start(this) {
                yes = choice(AlchemyLines.WARNING_YES, AlchemyLines.WARNING_NO, title = text(AlchemyLines.WARNING_TITLE, "spell" to spell.name, "item" to type.name)) == 1
            }
            world.interfaces.closeChatModal(player)
            // The menu waited: everything is checked again, on the same item in the same slot.
            if (yes) cast(spell, slot, obj, warned = true)
            return
        }
        val value = if (spell.high) base.highAlch else base.lowAlch
        val saved = player.inventory.contents().toMap()
        player.inventory.remove(slot, 1)
        val coins = world.objTypes[COINS]
        if (value > 0 && (coins == null || player.inventory.add(coins, value) < value)) {
            for (i in 0 until player.inventory.capacity) player.inventory[i] = saved[i]
            player.message(text[GameLines.NO_SPACE])
            return
        }
        for (cost in costs) Making.removeObj(player.inventory, cost.rune, cost.count)
        nextCast[player] = world.tick + spell.ticks
        // The cast ends an attack, not a walk.
        player.interaction = null
        if (!FountainOfRune.covers(player)) player.skills.addTenths(Skill.MAGIC, spell.xpTenths)
        player.anim(spell.anim)
        player.spotanim(spell.spotanim, SPOTANIM_HEIGHT)
        world.interfaces.switchSideTab(player, GameFrame.SIDE_TAB_MAGIC)
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

    /** The item's name as the not-enough message has it, as [Teleports] does: Nature Runes. */
    private fun plural(item: Int): String {
        val name = world.objTypes[item]?.name ?: "rune"
        return name.split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } } + "s"
    }

    companion object {
        const val COINS = 995

        /** All Settings 65 ("Casting Alchemy spells on untradeable items always triggers a warning") and 66 (its value). */
        const val VARBIT_UNTRADEABLE_WARNING = 6092
        const val VARBIT_WARNING_VALUE = 6091

        /** OSRS Wiki, High Level Alchemy: the value warning a new account starts with. */
        const val DEFAULT_WARNING_VALUE = 1_000

        const val SPOTANIM_HEIGHT = 92

        /** The spells: gameval name, base XP in tenths, ticks to the next cast, high, animation and graphic. */
        private val DEFINITIONS = listOf(
            Definition("21_low_alchemy", 310, 3, high = false, anim = 712, spotanim = 112),
            Definition("55_high_alchemy", 650, 5, high = true, anim = 713, spotanim = 113),
        )

        private class Definition(val key: String, val xpTenths: Int, val ticks: Int, val high: Boolean, val anim: Int, val spotanim: Int)

        /** The item a note stands for, else the item itself. */
        fun unnoted(type: ObjType, objTypes: ObjTypes): ObjType = if (type.isCert) objTypes[type.certlink] ?: type else type

        /**
         * The spells whose objs [objTypes] has, with their level, runes and component from the obj's params; a spell
         * obj without them fails the load. A test's handful of objs may have neither.
         */
        fun load(objTypes: ObjTypes): List<AlchemySpell> = DEFINITIONS.mapNotNull { d ->
            val type = objTypes.find(d.key) ?: return@mapNotNull null
            val packed = type.intParam(Spells.PARAM_COMPONENT, -1)
            require(packed ushr 16 == Spells.SPELLBOOK) { "${d.key} isn't on the magic tab (param ${Spells.PARAM_COMPONENT} is $packed)" }
            val level = type.intParam(Spells.PARAM_LEVEL, -1)
            require(level in 1..99) { "${d.key} has Magic level $level" }
            val runes = Spells.PARAM_RUNES.mapNotNull { (runeParam, countParam) ->
                val rune = type.intParam(runeParam, -1)
                if (rune < 0) null else RuneCost(rune, type.intParam(countParam, 0).also { require(it >= 1) { "${d.key}: rune $rune costs $it" } })
            }
            require(runes.isNotEmpty()) { "${d.key} needs no runes" }
            val name = type.stringParam(Spells.PARAM_NAME) ?: d.key
            AlchemySpell(type.id, d.key, name, packed and 0xFFFF, level, runes, d.xpTenths, d.ticks, d.high, d.anim, d.spotanim)
        }.also { if (it.isEmpty()) logger.debug { "No alchemy spell objs in the cache" } }
    }
}
