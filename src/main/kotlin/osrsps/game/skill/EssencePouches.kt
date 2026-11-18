package osrsps.game.skill

import osrsps.game.InvObj
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.dialogue.Lines

/** Essence pouches' words, in the dialogue file. */
object EssencePouchLines : Lines("runecraft_pouch", "Essence pouch") {
    val CHECK = line("check", "Check on a pouch holding essence; {count} is how many, {essence} the essence obj's name as the cache has it, Pure essence for example", "count", "essence")
    val CHECK_EMPTY = line("check_empty", "Check on an empty pouch")
    val LEVEL = line("level", "Fill below the pouch's Runecraft level (unboosted); {level} is it", "level")
    val FULL = line("full", "Fill on a pouch already holding all it can")
    val NO_ESSENCE = line("no_essence", "Fill with no essence in the backpack")
    val WRONG_ESSENCE = line("wrong_essence", "Fill holding only the other essence than the pouch already holds")
    val EMPTY_NOTHING = line("empty_nothing", "Empty on an empty pouch")
    val EMPTY_NO_SPACE = line("empty_no_space", "Empty with no free backpack slot")
    val DECAYED = line("decayed", "a pouch's capacity dropping through use")
    val DESTROYED = line("destroyed", "a pouch falling apart through use")
    val DROPPED = line("dropped", "Drop on a pouch holding essence, the essence lost (the Wiki's Large pouch page quotes it)")
}

/**
 * One essence pouch: its obj and degraded obj (null for the small pouch, which never degrades), its level, the varbits
 * holding its count and its essence type, where its degradation counter is kept, and its capacity by the times it has
 * degraded with the counter value at which each step starts (the last value destroys it).
 */
class EssencePouch(
    val key: String,
    val obj: Int,
    val degraded: Int?,
    val level: Int,
    val countVarbit: Int,
    val typeVarbit: Int,
    val decay: Decay?,
    val capacities: List<Int>,
    val thresholds: List<Int>,
) {
    init {
        require(thresholds.size == capacities.size) { "$key: a threshold for each capacity step past the first, and the last destroys it" }
    }

    /** The capacity with the counter at [counter], or 0 once destroyed. */
    fun capacity(counter: Int): Int {
        if (decay == null) return capacities[0]
        val step = thresholds.count { counter >= it }
        return capacities.getOrElse(step) { 0 }
    }

    fun destroyed(counter: Int): Boolean = decay != null && counter >= thresholds.last()
}

/** Where a pouch keeps its degradation counter: a whole varp, or a varbit. */
sealed interface Decay {
    data class Varp(val id: Int) : Decay
    data class Varbit(val id: Int) : Decay
}

/**
 * Runecraft, step RC4: the small, medium, large, giant and colossal essence pouches: Fill, Check and Empty.
 *
 * Sources: the OSRS Wiki's Essence pouch page (read 2026-11-15) and each pouch's page: capacities 3, 6, 9, 12 and 40;
 * levels 1, 25, 50, 75 and 25, unboostable; rune, pure, daeyalt or guardian essence; the degradation table (a counter
 * raised by a random 0 to 3 for each essence put in, the capacity dropping at set counter values until the pouch is
 * destroyed; filling just before a step fills only to the degraded capacity; essence over a lowered capacity goes back to
 * the backpack when it all fits, else is lost); no degradation in a Runecraft or max cape; the colossal pouch's capacity
 * by base level (8 at 25, 16 at 50, 27 at 75, 40 at 85) and its message "Your pouch has decayed through use.". The 242
 * cache for the objs (Fill iop1, Check iop2, Empty iop3) and the vars: count varbits small/medium/large/giant
 * _essence_pouch (603-606) and colossal_essence_pouch (13682), type varbits *_essence_pouch_type (2088-2091, 13684),
 * counters rcu_pouch_degradation_med/large/giant (varps 488-490) and rcu_pouch_degradation_colossal (varbit 13683).
 *
 * Inferred: a pouch holds one essence type at a time (live keeps one type varbit per pouch; a pouch with essence fills
 * only with its own type); the colossal counter rises by one per essence, not 0 to 3 (the Wiki's "8 full uses (320
 * essence)" at 85 and "40 full uses" at 25 against its 320 threshold). Guessed: the type values (1 rune essence, 2 pure
 * essence); an empty pouch takes the essence in the backpack's first essence slot; the colossal pouch's degraded steps
 * below 85 scale its 85+ steps by its capacity over 40, rounded down; the decay message on every step; a destroyed
 * pouch's essence goes back to the backpack when it fits, else is lost; the refusal order and every message's wording.
 * Out: getting a pouch (the Abyss, Guardians of the Rift, the abyssal needle), repairs (the Abyss's dark mage, Astral
 * Contact, Apprentice Cordelia), daeyalt and guardian essence, the abyssal lantern, Fill and Empty in the bank, the
 * left-click swap between Fill and Empty, keeping pouches on death (emptyondeath_essencepouch), the one-colossal rule.
 */
class EssencePouches(private val world: World) {
    fun register() {
        for (pouch in POUCHES) {
            for (obj in listOfNotNull(pouch.obj, pouch.degraded)) {
                world.handlers.onObj(obj, FILL) { op -> fill(player, pouch, op.sub) }
                world.handlers.onObj(obj, CHECK) { check(player, pouch) }
                world.handlers.onObj(obj, EMPTY) { empty(player, pouch) }
            }
        }
    }

    /**
     * [obj] left [player]'s backpack by Drop: a pouch's essence is lost with it (the Wiki's Essence pouch page), so its
     * count and type go back to empty and picking it up again finds it empty. The count is per kind of pouch, so while
     * another of the same kind (or its degraded version) is still in the backpack or the bank, that one keeps it
     * (exploit review round 40, R40A-2).
     */
    fun dropped(player: Player, obj: Int) {
        val pouch = POUCHES.firstOrNull { obj == it.obj || obj == it.degraded } ?: return
        val ids = listOfNotNull(pouch.obj, pouch.degraded)
        if (ids.any { player.inventory.count(it) > 0 || player.bank.count(it) > 0 }) return
        if (player.vars.getVarbit(pouch.countVarbit) > 0) player.message(world.textFor(player)[EssencePouchLines.DROPPED])
        player.vars.setVarbit(pouch.countVarbit, 0)
        player.vars.setVarbit(pouch.typeVarbit, 0)
    }

    private fun check(player: Player, pouch: EssencePouch) {
        val text = world.textFor(player)
        val count = player.vars.getVarbit(pouch.countVarbit)
        val essence = essenceOf(player.vars.getVarbit(pouch.typeVarbit))
        if (count == 0 || essence == null) return player.message(text[EssencePouchLines.CHECK_EMPTY])
        val name = world.objTypes[essence]?.name ?: error("No obj type $essence for essence")
        player.message(text[EssencePouchLines.CHECK, "count" to count, "essence" to name])
    }

    private fun fill(player: Player, pouch: EssencePouch, slot: Int) {
        val text = world.textFor(player)
        val level = player.skills.baseLevel(Skill.RUNECRAFT)
        if (level < pouch.level) return player.message(text[EssencePouchLines.LEVEL, "level" to pouch.level])
        val inventory = player.inventory
        val held = player.vars.getVarbit(pouch.countVarbit)
        val heldType = essenceOf(player.vars.getVarbit(pouch.typeVarbit)).takeIf { held > 0 }
        val firstEssence = inventory.contents().firstOrNull { it.second.id in ESSENCE }?.second?.id
            ?: return player.message(text[EssencePouchLines.NO_ESSENCE])
        val essence = heldType ?: firstEssence
        if (inventory.count(essence) == 0L) return player.message(text[EssencePouchLines.WRONG_ESSENCE])
        var counter = counter(player, pouch)
        if (held >= capacity(pouch, counter, level)) return player.message(text[EssencePouchLines.FULL])
        val wearsCape = wearsCape(player)
        val stepBefore = step(pouch, counter)
        var count = held
        while (count < capacity(pouch, counter, level) && !pouch.destroyed(counter)) {
            val essenceSlot = inventory.contents().firstOrNull { it.second.id == essence }?.first ?: break
            inventory.remove(essenceSlot, 1)
            count++
            if (pouch.decay != null && !wearsCape) counter += if (pouch.key == "colossal") 1 else world.random.between(0, 3)
        }
        if (pouch.decay != null && !wearsCape) setCounter(player, pouch, counter)
        player.vars.setVarbit(pouch.typeVarbit, typeOf(essence))
        if (pouch.destroyed(counter)) {
            inventory.remove(slot, 1)
            giveBack(player, essence, count)
            setContents(player, pouch, 0, essence)
            setCounter(player, pouch, 0)
            return player.message(text[EssencePouchLines.DESTROYED])
        }
        val capacity = capacity(pouch, counter, level)
        if (count > capacity) {
            giveBack(player, essence, count - capacity)
            count = capacity
        }
        setContents(player, pouch, count, essence)
        if (step(pouch, counter) > stepBefore) {
            val degraded = pouch.degraded
            if (degraded != null && inventory[slot]?.id == pouch.obj) inventory[slot] = InvObj(degraded, 1)
            player.message(text[EssencePouchLines.DECAYED])
        }
    }

    private fun empty(player: Player, pouch: EssencePouch) {
        val text = world.textFor(player)
        val count = player.vars.getVarbit(pouch.countVarbit)
        val essence = essenceOf(player.vars.getVarbit(pouch.typeVarbit))
        if (count == 0 || essence == null) return player.message(text[EssencePouchLines.EMPTY_NOTHING])
        val essenceType = world.objTypes[essence] ?: error("No obj type $essence for essence")
        val added = if (player.inventory.contents().size < player.inventory.capacity) player.inventory.add(essenceType, count) else 0
        if (added == 0) return player.message(text[EssencePouchLines.EMPTY_NO_SPACE])
        setContents(player, pouch, count - added, essence)
    }

    /** All [count] of [essence] back into the backpack when they fit, else none (the Wiki's lowered-capacity rule). */
    private fun giveBack(player: Player, essence: Int, count: Int) {
        if (count <= 0) return
        val free = player.inventory.capacity - player.inventory.contents().size
        if (free < count) return
        val type = world.objTypes[essence] ?: error("No obj type $essence for essence")
        player.inventory.add(type, count)
    }

    private fun setContents(player: Player, pouch: EssencePouch, count: Int, essence: Int) {
        player.vars.setVarbit(pouch.countVarbit, count)
        player.vars.setVarbit(pouch.typeVarbit, if (count == 0) 0 else typeOf(essence))
    }

    private fun wearsCape(player: Player): Boolean = player.equipment[WearPos.CAPE]?.id in NO_DECAY_CAPES

    private fun counter(player: Player, pouch: EssencePouch): Int = when (val decay = pouch.decay) {
        null -> 0
        is Decay.Varp -> player.vars[decay.id]
        is Decay.Varbit -> player.vars.getVarbit(decay.id)
    }

    private fun setCounter(player: Player, pouch: EssencePouch, value: Int) {
        when (val decay = pouch.decay) {
            null -> Unit
            is Decay.Varp -> player.vars[decay.id] = value
            is Decay.Varbit -> player.vars.setVarbit(decay.id, minOf(value, player.vars.types.varbit(decay.id)!!.mask))
        }
    }

    companion object {
        const val FILL = 1
        const val CHECK = 2
        const val EMPTY = 3

        const val SMALL = 5509 // rcu_pouch_small
        const val MEDIUM = 5510 // rcu_pouch_medium
        const val MEDIUM_DEGRADED = 5511 // rcu_pouch_medium_degrade
        const val LARGE = 5512 // rcu_pouch_large
        const val LARGE_DEGRADED = 5513 // rcu_pouch_large_degrade
        const val GIANT = 5514 // rcu_pouch_giant
        const val GIANT_DEGRADED = 5515 // rcu_pouch_giant_degrade
        const val COLOSSAL = 26784 // rcu_pouch_colossal
        const val COLOSSAL_DEGRADED = 26786 // rcu_pouch_colossal_degrade

        /** Guessed type varbit values. */
        const val TYPE_RUNE = 1
        const val TYPE_PURE = 2

        private val ESSENCE = setOf(Mining.RUNE_ESSENCE, Mining.PURE_ESSENCE)

        /**
         * skillcape_runecrafting, its trimmed cape, and every max cape variant (the Wiki: any max cape stops decay): the
         * 242 cache's wearable objs named "... max cape", the locked (l) trouver ones too.
         */
        val NO_DECAY_CAPES = setOf(9765, 9766) + setOf(
            13280, 13329, 13331, 13333, 13335, 13337, 13342, 20760, 21186, 21284, 21285, 21776, 21780, 21784, 21898,
            24133, 24134, 24135, 24232, 24233, 24234, 24855, 27363, 27365, 28902, 28906, 34625,
        )

        fun typeOf(essence: Int): Int = if (essence == Mining.PURE_ESSENCE) TYPE_PURE else TYPE_RUNE

        fun essenceOf(type: Int): Int? = when (type) {
            TYPE_RUNE -> Mining.RUNE_ESSENCE
            TYPE_PURE -> Mining.PURE_ESSENCE
            else -> null
        }

        /** The colossal pouch's full capacity at base Runecraft [level] (the Wiki's Capacity table). */
        fun colossalCapacity(level: Int): Int = when {
            level >= 85 -> 40
            level >= 75 -> 27
            level >= 50 -> 16
            else -> 8
        }

        /** The capacity at [counter], the colossal pouch's scaled to base level [level]. */
        fun capacity(pouch: EssencePouch, counter: Int, level: Int): Int {
            val full = pouch.capacity(counter)
            return if (pouch.key == "colossal") full * colossalCapacity(level) / 40 else full
        }

        /** How many times the pouch has degraded at [counter]. */
        fun step(pouch: EssencePouch, counter: Int): Int = if (pouch.decay == null) 0 else pouch.thresholds.count { counter >= it }

        val POUCHES: List<EssencePouch> = listOf(
            EssencePouch("small", SMALL, null, 1, 603, 2088, null, listOf(3), listOf(Int.MAX_VALUE)),
            EssencePouch("medium", MEDIUM, MEDIUM_DEGRADED, 25, 604, 2089, Decay.Varp(488), listOf(6, 3), listOf(400, 800)),
            EssencePouch("large", LARGE, LARGE_DEGRADED, 50, 605, 2090, Decay.Varp(489), listOf(9, 7, 5, 3), listOf(400, 600, 800, 1000)),
            EssencePouch(
                "giant", GIANT, GIANT_DEGRADED, 75, 606, 2091, Decay.Varp(490),
                listOf(12, 9, 8, 7, 6, 5, 3), listOf(200, 300, 400, 600, 800, 1000, 1200),
            ),
            EssencePouch(
                "colossal", COLOSSAL, COLOSSAL_DEGRADED, 25, 13682, 13684, Decay.Varbit(13683),
                listOf(40, 35, 30, 25, 20, 15, 10, 5), listOf(320, 565, 745, 870, 950, 995, 1015, 1021),
            ),
        )
    }
}
