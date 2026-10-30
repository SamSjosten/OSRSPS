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
}

/** [count] of obj [obj]. */
data class ObjCount(val obj: Int, val count: Int)

/**
 * One thing an artisan skill makes: [amount] of [product] from [inputs], with [tools] held but kept, at [level] in
 * [skill] for [xpTenths]. The first comes [first] ticks after the choice, the rest every [interval] ticks, each with
 * [anim]. [made] is the message on making it and [tooLow] the one when the level is too low; both are given `amount`,
 * `item` (the product's name in lower case) and `level` where they ask for them.
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
) {
    init {
        require(amount >= 1 && level in 1..99 && xpTenths >= 0) { "Bad numbers in recipe $key" }
        require(inputs.isNotEmpty() && inputs.all { it.count >= 1 }) { "Recipe $key needs inputs" }
        require(first >= 1 && interval >= 1) { "Recipe $key needs at least a tick between products" }
    }

    /** How many times [inventory] holds everything for one, tools included. */
    fun times(inventory: Inventory): Int {
        if (tools.any { inventory.count(it) == 0L }) return 0
        return inputs.minOf { (inventory.count(it.obj) / it.count).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
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
    private class Job(val recipe: MakeRecipe, var left: Int)

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

    /** Starts making [quantity] of [recipe] for the context's player, after checking their level. */
    fun start(context: ScriptContext, recipe: MakeRecipe, quantity: Int) {
        val player = context.player
        // The box closes as the making starts, so the weak queue isn't held up by it.
        world.interfaces.closeChatModal(player)
        world.scripts.clearQueue(player, QUEUE)
        if (!levelled(player, recipe, tell = true)) return
        if (quantity <= 0 || recipe.times(player.inventory) <= 0) return
        val job = Job(recipe, quantity)
        player.anim(recipe.anim)
        context.queue(QUEUE, QueueType.WEAK, recipe.first) { makeOne(job) }
    }

    private fun ScriptContext.makeOne(job: Job) {
        val recipe = job.recipe
        if (recipe.times(player.inventory) <= 0) return
        // Guessed: a boost wearing off below the level stops it without a word.
        if (!levelled(player, recipe, tell = false)) return
        val before = player.skills.baseLevel(recipe.skill)
        for (input in recipe.inputs) removeObj(player.inventory, input.obj, input.count)
        val type = world.objTypes[recipe.product] ?: error("No obj type ${recipe.product} for recipe ${recipe.key}")
        player.inventory.add(type, recipe.amount)
        player.skills.addTenths(recipe.skill, recipe.xpTenths)
        player.message(world.textFor(player).get(recipe.made, *args(recipe, recipe.made)))
        job.left--
        if (player.skills.baseLevel(recipe.skill) > before) return
        if (job.left <= 0 || recipe.times(player.inventory) <= 0) return
        player.anim(recipe.anim)
        queue(QUEUE, QueueType.WEAK, recipe.interval) { makeOne(job) }
    }

    /** Whether [player]'s level is enough for [recipe], telling them if not and [tell]. */
    private fun levelled(player: Player, recipe: MakeRecipe, tell: Boolean): Boolean {
        if (player.skills.currentLevel(recipe.skill) >= recipe.level) return true
        if (tell) player.message(world.textFor(player).get(recipe.tooLow, *args(recipe, recipe.tooLow)))
        return false
    }

    /** The values [line] asks for, of `amount`, `item` and `level`. */
    private fun args(recipe: MakeRecipe, line: Line): Array<Pair<String, Any>> {
        val name = world.objTypes[recipe.product]?.name?.lowercase() ?: "item ${recipe.product}"
        val all = mapOf("amount" to recipe.amount, "item" to name, "level" to recipe.level)
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
