package osrsps.game.content

import osrsps.game.InvObj
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** The bottomless milk bucket's lines (keys only; the words are in data/dialogue.txt). */
object BottomlessLines : Lines("bottomless_milk_bucket", "Transcript:Bottomless milk bucket") {
    val CHECK_EMPTY = line("check_empty", "Check on an empty bucket")
    val CHECK = line("check", "Check: {amount} use{s} of {type} (a type_* line)", "amount", "s", "type")
    val TYPE_MILK = line("type_milk", "the regular milk's name in Check and Fill")
    val TYPE_CHOCOLATEY = line("type_chocolatey", "chocolatey milk's name in Check and Fill")
    val TYPE_CREAMY = line("type_creamy", "the milky mixture's name in Check and Fill (\"creamy milk\")")
    val TYPE_HANGOVER = line("type_hangover", "the hangover cure's name in Check and Fill")
    val NO_MILK = line("no_milk", "Fill with no milk of any kind in the backpack")
    val WRONG_MILK = line("wrong_milk", "Fill with only milk of another type than the bucket holds")
    val FIRST_WARNING = line("first_warning", "the first fill of an empty bucket: the warning before the question")
    val FIRST_PROCEED = line("first_proceed", "the first fill's question")
    val YES = line("yes", "the first fill's Yes")
    val NO = line("no", "the first fill's No")
    val HOW_MANY = line("how_many", "Fill with more than one bucket of the milk: the amount prompt")
    val FILLED_ONE = line("filled_one", "Fill with one bucket: {type}, {total} uses in all", "type", "total")
    val FILLED = line("filled", "Fill with several: {amount} buckets of {type}, {total} uses in all", "amount", "type", "total")
    val FULL = line("full", "the bucket filled to 10,000, or a cow milked into a full one (the Wiki's trivia)")
    val ALREADY_FULL = line("already_full", "milk used on, or Fill on, a full bucket (the Wiki's trivia)")
    val EMPTY_WARNING = line("empty_warning", "Empty: the warning")
    val EMPTY_PROCEED = line("empty_proceed", "Empty: the question")
    val EMPTY_YES = line("empty_yes", "Empty: the Yes")
    val EMPTY_NO = line("empty_no", "Empty: the No")
    val EMPTIED = line("emptied", "Empty, confirmed")
    val CHOCOLATE = line("chocolate", "chocolate dust used on a bucket of regular milk (not on the Wiki: ours)")
    val NOT_MILK = line("not_milk", "chocolate dust used on a bucket that doesn't hold regular milk (not on the Wiki: ours)")
}

/**
 * The bottomless milk bucket (Brutus step 5; the OSRS Wiki's Bottomless milk bucket and its transcript, read
 * 2026-11-22; ids and options from the 242 cache): up to [MAX_USES] uses of one kind of milk ([TYPES]).
 *
 * - **Fill**, or a bucket of the milk used on it: the empty bucket asks first ("ALL remaining milk ... will be LOST"),
 *   more than one bucket asks how many; each gives its bucket back.
 * - **Check** and **Empty** (with its warning).
 * - **Milked into**: a dairy cow milked with the bucket in the backpack puts a use in it every 7 ticks (6 with the
 *   cowbell amulet worn), on until it is full ([milkable], from [GroatsFarm]).
 * - **Chocolate dust** on it (regular milk) makes a chocolatey milk for a use.
 * - **Churned**: the dairy churn takes its regular milk ([osrsps.game.skill.Churn]).
 *
 * GUESSED until recorded: the uses belong to the player, not to each bucket (as every charged item's here), so two
 * buckets share them; the bucket milked into before empty buckets in the backpack, and with no message for each use;
 * a full one milked into stopping with the full message; each filled bucket giving an empty bucket back (the milky
 * mixture's and hangover cure's too); chocolate dust's result going where the dust was, and its messages; the churn
 * using the bucket only when the backpack has no milk, cream or butter (or the bucket used on it). Left out: quests'
 * milk (no quests), cat food, the Hell-Rat Behemoths.
 */
object BottomlessMilkBucket {
    const val EMPTY = 33091
    const val FILLED = 33089
    val BUCKETS = listOf(EMPTY, FILLED)
    const val FILL = 3
    const val CHECK = 4
    const val EMPTY_OP = 5

    const val MAX_USES = 10_000
    const val MILK_TICKS = 7
    const val USES = "bottomless_milk"
    const val TYPE = "bottomless_milk_type"

    const val BUCKET = 1925
    const val BUCKET_OF_MILK = 1927
    const val CHOCOLATEY_MILK = 1977
    const val MILKY_MIXTURE = 7471
    const val HANGOVER_CURE = 1504
    const val CHOCOLATE_DUST = 1975

    class MilkType(val obj: Int, val name: Line)

    /** By the number kept in [TYPE], from 1. */
    val TYPES = listOf(
        MilkType(BUCKET_OF_MILK, BottomlessLines.TYPE_MILK),
        MilkType(CHOCOLATEY_MILK, BottomlessLines.TYPE_CHOCOLATEY),
        MilkType(MILKY_MIXTURE, BottomlessLines.TYPE_CREAMY),
        MilkType(HANGOVER_CURE, BottomlessLines.TYPE_HANGOVER),
    )
    val REGULAR = TYPES[0]

    fun uses(player: Player) = player.charges[USES]

    fun type(player: Player): MilkType? = if (uses(player) > 0) TYPES.getOrNull(player.charges[TYPE] - 1) else null

    fun held(player: Player) = BUCKETS.any { player.inventory.count(it) > 0 }

    private fun s(count: Int) = if (count == 1) "" else "s"

    /** Sets the uses to [uses] of [type] and the buckets in the backpack to match. */
    fun set(player: Player, type: MilkType?, uses: Int) {
        player.charges[USES] = if (type == null) 0 else uses
        player.charges[TYPE] = if (type == null || uses == 0) 0 else TYPES.indexOf(type) + 1
        val (from, to) = if (uses(player) > 0) EMPTY to FILLED else FILLED to EMPTY
        for ((slot, held) in player.inventory.contents()) if (held.id == from) player.inventory[slot] = InvObj(to, 1)
    }

    /** Whether a dairy cow milked now would fill the bucket: one held, empty or holding regular milk. */
    fun milkable(player: Player) = held(player) && (uses(player) == 0 || type(player) == REGULAR)

    /** A use of regular milk from a cow; false, with the full message, when it is full. */
    fun milked(world: World, player: Player): Boolean {
        val have = uses(player)
        if (have >= MAX_USES) {
            world.message(player, BottomlessLines.FULL)
            return false
        }
        set(player, REGULAR, have + 1)
        if (have + 1 >= MAX_USES) world.message(player, BottomlessLines.FULL)
        return true
    }

    /** Fill, or [used] (a milk) on the bucket. */
    suspend fun ScriptContext.fill(world: World, used: Int? = null) {
        val holding = type(player)
        val type = when {
            used != null -> TYPES.firstOrNull { it.obj == used } ?: return
            holding != null -> holding
            else -> TYPES.firstOrNull { player.inventory.count(it.obj) > 0 }
        }
        if (type == null) return world.message(player, BottomlessLines.NO_MILK)
        if (holding != null && holding != type) return world.message(player, BottomlessLines.WRONG_MILK)
        val held = player.inventory.count(type.obj).toInt()
        if (held <= 0) {
            val other = TYPES.any { player.inventory.count(it.obj) > 0 }
            return world.message(player, if (other) BottomlessLines.WRONG_MILK else BottomlessLines.NO_MILK)
        }
        val have = uses(player)
        if (have >= MAX_USES) return world.message(player, BottomlessLines.ALREADY_FULL)
        if (have == 0) {
            var yes = false
            world.dialogues.start(this) {
                mesbox(BottomlessLines.FIRST_WARNING)
                yes = choice(BottomlessLines.YES, BottomlessLines.NO, title = text(BottomlessLines.FIRST_PROCEED)) == 1
            }
            if (!yes) return
        }
        val wanted = if (held == 1) 1 else countDialog(world.textFor(player)[BottomlessLines.HOW_MANY])
        val amount = minOf(wanted, player.inventory.count(type.obj).toInt(), MAX_USES - uses(player))
        if (amount <= 0) return
        // The type rechecked: the bucket may have been filled with something else while the question was open.
        val now = type(player)
        if (now != null && now != type) return
        var filled = 0
        for ((slot, item) in player.inventory.contents()) {
            if (filled >= amount) break
            if (item.id != type.obj) continue
            player.inventory[slot] = InvObj(BUCKET, 1)
            filled++
        }
        val total = uses(player) + filled
        set(player, type, total)
        val name = world.textFor(player)[type.name]
        if (filled == 1) {
            world.message(player, BottomlessLines.FILLED_ONE, "type" to name, "total" to total)
        } else {
            world.message(player, BottomlessLines.FILLED, "amount" to filled, "type" to name, "total" to total)
        }
        if (total >= MAX_USES) world.message(player, BottomlessLines.FULL)
    }

    fun check(world: World, player: Player) {
        val type = type(player) ?: return world.message(player, BottomlessLines.CHECK_EMPTY)
        val have = uses(player)
        world.message(player, BottomlessLines.CHECK, "amount" to have, "s" to s(have), "type" to world.textFor(player)[type.name])
    }

    suspend fun ScriptContext.empty(world: World) {
        var yes = false
        world.dialogues.start(this) {
            mesbox(BottomlessLines.EMPTY_WARNING)
            yes = choice(BottomlessLines.EMPTY_YES, BottomlessLines.EMPTY_NO, title = text(BottomlessLines.EMPTY_PROCEED)) == 1
        }
        if (!yes) return
        set(player, null, 0)
        world.message(player, BottomlessLines.EMPTIED)
    }

    /** Chocolate dust on a bucket of regular milk: a chocolatey milk where the dust was, for a use. */
    fun chocolate(world: World, player: Player) {
        if (type(player) != REGULAR) return world.message(player, BottomlessLines.NOT_MILK)
        // Unnoted dust doesn't stack, so its slot takes the milk.
        val slot = player.inventory.contents().firstOrNull { it.second.id == CHOCOLATE_DUST }?.first ?: return
        player.inventory[slot] = InvObj(CHOCOLATEY_MILK, 1)
        set(player, REGULAR, uses(player) - 1)
        world.message(player, BottomlessLines.CHOCOLATE)
    }

    /** One use of regular milk taken for the churn; false when there is none. */
    fun takeMilk(player: Player): Boolean {
        if (type(player) != REGULAR) return false
        set(player, REGULAR, uses(player) - 1)
        return true
    }
}

/** The cow slippers' lines. */
object CowSlippersLines : Lines("cow_slippers", "Transcript:Cow slippers") {
    val SWAP_RARE = line("swap_rare", "Swap on the rare style: the question")
    val YES = line("yes", "the question's Yes")
    val NO = line("no", "the question's No")
}

/**
 * Cow slippers (Brutus step 5; the OSRS Wiki's Cow slippers and its transcript, read 2026-11-22; ids from the 242
 * cache): Swap turns a pair into another of the three styles, with "a small chance" of the rare fourth (the Isle of
 * Souls cows' colours), which asks before swapping away from it. Only the first style is tradeable (the cache's).
 *
 * GUESSED until recorded: the order (each to the next, the third back to the first), the rare one's 1 in [RARE_CHANCE],
 * and the rare one swapping to the first style.
 */
object CowSlippers {
    const val PLAIN = 33093
    const val RARE = 33098
    val STYLES = listOf(PLAIN, 33096, 33097)
    val ALL = STYLES + RARE
    const val SWAP = 3
    const val RARE_CHANCE = 100

    suspend fun ScriptContext.swap(world: World, from: Int) {
        val slot = player.inventory.contents().firstOrNull { it.second.id == from }?.first ?: return
        if (from == RARE) {
            var yes = false
            world.dialogues.start(this) { yes = choice(CowSlippersLines.YES, CowSlippersLines.NO, title = text(CowSlippersLines.SWAP_RARE)) == 1 }
            if (!yes || player.inventory[slot]?.id != RARE) return
        }
        val to = when {
            from != RARE && world.random.chance(1, RARE_CHANCE) -> RARE
            from == RARE -> PLAIN
            else -> STYLES[(STYLES.indexOf(from) + 1) % STYLES.size]
        }
        player.inventory[slot] = InvObj(to, 1)
    }
}

fun registerBrutusUniques(world: World) {
    with(BottomlessMilkBucket) {
        for (bucket in BUCKETS) {
            world.handlers.onObj(bucket, FILL) { fill(world) }
            world.handlers.onObj(bucket, CHECK) { check(world, player) }
            for (type in TYPES) world.handlers.onObjOnObj(type.obj, bucket) { fill(world, type.obj) }
            world.handlers.onObjOnObj(CHOCOLATE_DUST, bucket) { chocolate(world, player) }
        }
        world.handlers.onObj(FILLED, EMPTY_OP) { empty(world) }
    }
    with(CowSlippers) {
        for (style in ALL) world.handlers.onObj(style, SWAP) { swap(world, style) }
    }
}
