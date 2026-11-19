package osrsps.game.skill.magic

import osrsps.game.FountainOfRune
import osrsps.game.GameFrame
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.CombatLines
import osrsps.game.combat.RuneCost
import osrsps.game.combat.Spellbook
import osrsps.game.combat.Spells
import osrsps.game.script.QueueType
import osrsps.game.script.ScriptContext
import osrsps.game.skill.Making

/**
 * A standard spellbook spell cast on an inventory item (alchemy, Superheat Item): its spell obj ([obj], [key]) and
 * [name], the magic tab [component], the Magic [level] and [runes] (all the cache's), the base XP in tenths
 * ([xpTenths]), the ticks before the next item spell ([ticks]) and its animation and graphic.
 */
class ItemSpell(
    val obj: Int,
    val key: String,
    val name: String,
    val component: Int,
    val level: Int,
    val runes: List<RuneCost>,
    val xpTenths: Int,
    val ticks: Int,
    val anim: Int,
    val spotanim: Int,
) {
    companion object {
        /**
         * The spell whose obj is gameval [key], with its level, runes, name and component from the obj's params, or null
         * when [objTypes] lacks it (a test's handful of objs); a spell obj without them fails the load.
         */
        fun load(objTypes: ObjTypes, key: String, xpTenths: Int, ticks: Int, anim: Int, spotanim: Int): ItemSpell? {
            val type = objTypes.find(key) ?: return null
            val packed = type.intParam(Spells.PARAM_COMPONENT, -1)
            require(packed ushr 16 == Spells.SPELLBOOK) { "$key isn't on the magic tab (param ${Spells.PARAM_COMPONENT} is $packed)" }
            val level = type.intParam(Spells.PARAM_LEVEL, -1)
            require(level in 1..99) { "$key has Magic level $level" }
            val runes = Spells.PARAM_RUNES.mapNotNull { (runeParam, countParam) ->
                val rune = type.intParam(runeParam, -1)
                if (rune < 0) null else RuneCost(rune, type.intParam(countParam, 0).also { require(it >= 1) { "$key: rune $rune costs $it" } })
            }
            require(runes.isNotEmpty()) { "$key needs no runes" }
            return ItemSpell(type.id, key, type.stringParam(Spells.PARAM_NAME) ?: key, packed and 0xFFFF, level, runes, xpTenths, ticks, anim, spotanim)
        }
    }
}

/**
 * What the spells cast on items share: one clock (a click before the last cast's ticks are up is dropped for alchemy,
 * guessed, and kept for Superheat Item, [castOrQueue]; guessed that they share it), the level and rune checks with combat's refusals,
 * and the cast itself: the runes taken (a staff standing in for its own, none at the Fountain of Rune), the XP (none at
 * the fountain), the animation and graphic (at the teleports' height 92, guessed), an attack ended but not a walk
 * (OSRS Wiki, High Level Alchemy), and the magic tab shown again (guessed).
 */
class ItemSpells(private val world: World) {
    /** The tick before which each player can't cast an item spell again. */
    private val nextCast = java.util.WeakHashMap<Player, Long>()

    /** Whether [player] is on the standard book and past the last cast's ticks. */
    fun ready(player: Player): Boolean = Spellbook.of(player) == Spellbook.STANDARD && (nextCast[player] ?: 0L) <= world.tick

    /**
     * Runs [cast] now when [player] is [ready], or keeps it for when the last cast's ticks are up: Superheat Item and
     * the jewellery enchants "queue up one spell if you cast them too quickly" (OSRS Wiki, Superheat Item's changes).
     * One is kept, the latest click replacing an earlier one (guessed), and it checks everything again as it runs.
     */
    fun castOrQueue(context: ScriptContext, cast: ScriptContext.() -> Unit) {
        val player = context.player
        if (Spellbook.of(player) != Spellbook.STANDARD) return
        val due = nextCast[player] ?: 0L
        if (due <= world.tick) {
            context.cast()
            return
        }
        world.scripts.clearQueue(player, QUEUED)
        world.scripts.queue(player, QUEUED, QueueType.NORMAL, (due - world.tick).toInt()) { if (ready(player)) cast() }
    }

    /** Whether [player] has the level for [spell], saying so if not. */
    fun hasLevel(player: Player, spell: ItemSpell): Boolean {
        if (player.skills.currentLevel(Skill.MAGIC) >= spell.level) return true
        player.message(world.textFor(player)[CombatLines.MAGIC_LEVEL])
        return false
    }

    /** The runes [player] pays for [spell], or null (having said so) when they hold too few. */
    fun runesToPay(player: Player, spell: ItemSpell): List<RuneCost>? {
        val costs = FountainOfRune.runesToPay(player, spell.runes, world.spells.runesFrom(player.equipment[WearPos.WEAPON]?.id))
        val short = costs.firstOrNull { player.inventory.count(it.rune) < it.count } ?: return costs
        player.message(world.textFor(player)[CombatLines.NOT_ENOUGH_RUNES, "runes" to plural(short.rune)])
        return null
    }

    /**
     * Takes [costs] from what [player] holds now, after the spell has taken its item (which may itself be one of the
     * runes): true when they were all there, and false, with the not-enough message and nothing taken, when not.
     */
    fun pay(player: Player, costs: List<RuneCost>): Boolean {
        val short = costs.firstOrNull { player.inventory.count(it.rune) < it.count }
        if (short != null) {
            player.message(world.textFor(player)[CombatLines.NOT_ENOUGH_RUNES, "runes" to plural(short.rune)])
            return false
        }
        for (cost in costs) Making.removeObj(player.inventory, cost.rune, cost.count)
        return true
    }

    /** Casts [spell] once its item and runes ([pay]) are taken: starts the clock, gives the XP and shows it. */
    fun cast(context: ScriptContext, spell: ItemSpell) {
        val player = context.player
        nextCast[player] = world.tick + spell.ticks
        player.interaction = null
        if (!FountainOfRune.covers(player)) player.skills.addTenths(Skill.MAGIC, spell.xpTenths)
        player.anim(spell.anim)
        player.spotanim(spell.spotanim, SPOTANIM_HEIGHT)
        world.interfaces.switchSideTab(player, GameFrame.SIDE_TAB_MAGIC)
    }

    /** The item's name as the not-enough message has it, as [Teleports] does: Nature Runes. */
    private fun plural(item: Int): String {
        val name = world.objTypes[item]?.name ?: "rune"
        return name.split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } } + "s"
    }

    companion object {
        const val SPOTANIM_HEIGHT = 92

        /** The queued early cast's name ([castOrQueue]). */
        private const val QUEUED = "item_spell_queued"
    }
}
