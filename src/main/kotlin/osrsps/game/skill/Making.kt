package osrsps.game.skill

import osrsps.game.Inventory
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Chatbox
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.script.QueueType
import osrsps.game.script.ScriptContext

/** The words the artisan skills' Make-X box shares, in the dialogue file. */
object MakeLines : Lines("make", "Make-X") {
    val TITLE = line("title", "the Make-X box's title over what to make (fletching, crafting, herblore)")
    val NO_SPACE = line("no_space", "the message when what is made won't fit in the inventory (Inventory page)")
}

/** [count] of obj [obj]. */
data class ObjCount(val obj: Int, val count: Int)

/**
 * A material that lasts several actions: one [obj] is used up every [uses] actions, counted in the player's [charge]
 * ([osrsps.game.Charges], so it survives a logout), with [usedUp] as the message. Thread, which the Wiki says lasts 5.
 */
/**
 * A recipe that can fail: success rolls the [low], [high] pair out of 256 ([SuccessRoll]); a miss gives [failed] (a
 * crushed gem; -1 for nothing, as a pot cracking in the oven) and [failedXpTenths] instead, with [failedLine] as the
 * message.
 */
data class MakeChance(val low: Int, val high: Int, val failed: Int, val failedXpTenths: Int, val failedLine: Line)

data class WearingMaterial(val obj: Int, val uses: Int, val charge: String, val usedUp: Line)

/**
 * One thing an artisan skill makes: [amount] of [product] from [inputs], with [tools] held but kept, at [level] in
 * [skill] for [xpTenths]. The first comes [first] ticks after the choice (0: as the choice is made), the second [second]
 * ticks after it and the rest every [interval] ticks, each with [anim] (-1 for none). [made] is the message on making it and [tooLow] the one when the level is too low; both are
 * given `amount`, `item` (the product's name in lower case), `input` (the first input's name, as the cache has it) and
 * `level` where they ask for them.
 *
 * A [batch] recipe makes up to [amount] at a time, each one taking [inputs] once and giving [xpTenths]: arrows come 15 at
 * a time, or fewer when fewer are left. Otherwise each action takes [inputs] and gives [amount] and [xpTenths] once.
 * [wears] is held like a tool and used up every so many actions. A [chance] recipe can fail. [byproducts] come back
 * with each product: the empty bucket from watering clay.
 */
class MakeRecipe(
    val key: String,
    val skill: Skill,
    val product: Int,
    val amount: Int,
    val level: Int,
    val xpTenths: Int,
    val inputs: List<ObjCount>,
    val tools: List<Int>,
    val anim: Int,
    val first: Int,
    val interval: Int,
    val made: Line,
    val tooLow: Line,
    val batch: Boolean = false,
    val wears: WearingMaterial? = null,
    val chance: MakeChance? = null,
    val byproducts: List<ObjCount> = emptyList(),
    val second: Int = interval,
) {
    init {
        require(amount >= 1 && level in 1..99 && xpTenths >= 0) { "Bad numbers in recipe $key" }
        require(inputs.isNotEmpty() && inputs.all { it.count >= 1 }) { "Recipe $key needs inputs" }
        require(first >= 0 && second >= 1 && interval >= 1) { "Recipe $key needs at least a tick between products" }
    }

    /** How many actions [inventory] holds everything for, tools included; a batch's last one may be short. */
    fun times(inventory: Inventory): Int {
        val units = units(inventory)
        return if (batch) ((units + amount - 1) / amount).toInt() else units.toInt()
    }

    /** How many times [inventory] holds every input, tools included. */
    fun units(inventory: Inventory): Long {
        if (tools.any { inventory.count(it) == 0L }) return 0
        if (wears != null && inventory.count(wears.obj) == 0L) return 0
        return inputs.minOf { inventory.count(it.obj) / it.count }
    }
}

/**
 * The loop the artisan skills share: an item used on another opens the Make-X box over what the pair can make (live's
 * skillmulti, as cooking uses), and the choice queues the making. Each product is a weak queue entry ([QueueType.WEAK]),
 * so walking off, closing an interface or starting something else ends it, as on live; it also ends when the inputs or
 * tools run out, the count is done, the level is too low (a boost wore off), or a product levels the skill up (live's
 * level-up box stops it, as it does the gathering skills).
 *
 * Sources: the OSRS Wiki's item pages give each product's ticks ("Make-X is 2, then 3 ticks" on Arrow shaft, read
 * 2026-10-30). Guessed: the box's quantity starting on as many as can be made (as cooking's), the animation played as
 * each product starts, and the order of message, XP and animation.
 */
class Making(private val world: World) {
    /** What each player is making: the recipe and how many are left. */
    private class Job(val recipe: MakeRecipe, var left: Int, var made: Int = 0)

    /**
     * [recipes] (what one pair of items can make, in the box's order), offered in the Make-X box under [title], then
     * made. With [alwaysAsk] off, the box is skipped when there is only one recipe and only one can be made.
     */
    suspend fun choose(context: ScriptContext, recipes: List<MakeRecipe>, title: Line = MakeLines.TITLE, alwaysAsk: Boolean = true) {
        require(recipes.size in 1..Chatbox.SKILLMULTI_MAX_ITEMS) { "The Make-X box shows 1 to ${Chatbox.SKILLMULTI_MAX_ITEMS} items, not ${recipes.size}" }
        val player = context.player
        val most = recipes.maxOf { it.times(player.inventory) }
        if (most <= 0) return
        val (index, quantity) = if (!alwaysAsk && recipes.size == 1 && most == 1) {
            0 to 1
        } else {
            var picked = 0 to 1
            world.dialogues.start(context) { picked = makeX(title, recipes.map { it.product }, most) }
            picked
        }
        val recipe = recipes.getOrNull(index) ?: return
        start(context, recipe, quantity)
    }

    /**
     * Starts making [quantity] of [recipe] for the context's player, after checking their level. [fromMovement] when it
     * starts in the movement phase, as an item used on a loc on arrival does.
     */
    fun start(context: ScriptContext, recipe: MakeRecipe, quantity: Int, fromMovement: Boolean = false) {
        val player = context.player
        // The box closes as the making starts, so the weak queue isn't held up by it.
        world.interfaces.closeChatModal(player)
        world.scripts.clearQueue(player, QUEUE)
        if (!levelled(player, recipe, tell = true)) return
        if (quantity <= 0 || recipe.times(player.inventory) <= 0) return
        val job = Job(recipe, quantity)
        if (recipe.anim >= 0) player.anim(recipe.anim)
        // The movement phase comes after the tick's script phase, which is where this queue's delay first counts down.
        val delay = if (fromMovement) recipe.first - 1 else recipe.first
        if (recipe.first == 0) context.makeOne(job) else context.queue(QUEUE, QueueType.WEAK, delay) { makeOne(job) }
    }

    /**
     * One action of [recipe] straight away, with no box or queue: darts, which live makes on every click (the Wiki gives
     * them 0 ticks). Tells the player if their level is too low.
     */
    fun makeNow(context: ScriptContext, recipe: MakeRecipe) {
        val player = context.player
        if (!levelled(player, recipe, tell = true)) return
        if (recipe.times(player.inventory) <= 0) return
        if (recipe.anim >= 0) player.anim(recipe.anim)
        make(player, recipe)
    }

    private fun ScriptContext.makeOne(job: Job) {
        val recipe = job.recipe
        if (recipe.times(player.inventory) <= 0) return
        // Guessed: a boost wearing off below the level stops it without a word.
        if (!levelled(player, recipe, tell = false)) return
        val before = player.skills.baseLevel(recipe.skill)
        if (!make(player, recipe)) return
        job.left--
        job.made++
        if (player.skills.baseLevel(recipe.skill) > before) return
        if (job.left <= 0 || recipe.times(player.inventory) <= 0) return
        if (recipe.anim >= 0) player.anim(recipe.anim)
        queue(QUEUE, QueueType.WEAK, if (job.made == 1) recipe.second else recipe.interval) { makeOne(job) }
    }

    /**
     * One action of [recipe]: the inputs taken, the product given, the XP and the message. False, with everything put
     * back, when the product won't all fit (a full stack, or no slot).
     */
    private fun make(player: Player, recipe: MakeRecipe): Boolean {
        val units = if (recipe.batch) minOf(recipe.amount.toLong(), recipe.units(player.inventory)).toInt() else 1
        val chance = recipe.chance
        val failed = chance != null && !SuccessRoll.roll(world.random, chance.low, chance.high, player.skills.currentLevel(recipe.skill))
        val product = if (failed) chance!!.failed else recipe.product
        val type = if (product < 0) null else world.objTypes[product] ?: error("No obj type $product for recipe ${recipe.key}")
        val saved = player.inventory.contents().toMap()
        for (input in recipe.inputs) removeObj(player.inventory, input.obj, input.count * units)
        val made = if (recipe.batch) units else recipe.amount
        val outputs = listOfNotNull(type?.let { it to made }) + recipe.byproducts.map { by ->
            (world.objTypes[by.obj] ?: error("No obj type ${by.obj} for recipe ${recipe.key}")) to by.count * units
        }
        if (outputs.any { (output, count) -> player.inventory.add(output, count) < count }) {
            // Rather than lose the inputs. Guessed message.
            for (slot in 0 until player.inventory.capacity) player.inventory[slot] = saved[slot]
            player.message(world.textFor(player)[MakeLines.NO_SPACE])
            return false
        }
        val line = if (failed) chance!!.failedLine else recipe.made
        player.skills.addTenths(recipe.skill, (if (failed) chance!!.failedXpTenths else recipe.xpTenths) * units)
        player.message(world.textFor(player).get(line, *args(recipe, line, made)))
        recipe.wears?.let { wear(player, it) }
        return true
    }

    /** One more action on [material]: every [WearingMaterial.uses]th uses one up. */
    private fun wear(player: Player, material: WearingMaterial) {
        val used = player.charges[material.charge] + 1
        if (used < material.uses) {
            player.charges[material.charge] = used
            return
        }
        player.charges[material.charge] = 0
        removeObj(player.inventory, material.obj, 1)
        player.message(world.textFor(player)[material.usedUp])
    }

    /** Whether [player]'s level is enough for [recipe], telling them if not and [tell]. */
    private fun levelled(player: Player, recipe: MakeRecipe, tell: Boolean): Boolean {
        if (player.skills.currentLevel(recipe.skill) >= recipe.level) return true
        if (tell) player.message(world.textFor(player).get(recipe.tooLow, *args(recipe, recipe.tooLow, recipe.amount)))
        return false
    }

    /** The values [line] asks for, of `amount`, `item`, `input` and `level`. */
    private fun args(recipe: MakeRecipe, line: Line, amount: Int): Array<Pair<String, Any>> {
        val name = world.objTypes[recipe.product]?.name?.lowercase() ?: "item ${recipe.product}"
        val input = recipe.inputs[0].obj.let { world.objTypes[it]?.name ?: "item $it" }
        val all = mapOf("amount" to amount, "item" to name, "input" to input, "level" to recipe.level)
        return line.args.mapNotNull { arg -> all[arg]?.let { arg to it } }.toTypedArray()
    }

    companion object {
        /** The weak queue entry a product waits in. */
        const val QUEUE = "make"

        /** Takes [count] of [obj] from [inventory], from as many slots as it takes. */
        fun removeObj(inventory: Inventory, obj: Int, count: Int) {
            var left = count
            for ((slot, held) in inventory.contents()) {
                if (left <= 0) break
                if (held.id != obj) continue
                left -= inventory.remove(slot, left)
            }
        }
    }
}
