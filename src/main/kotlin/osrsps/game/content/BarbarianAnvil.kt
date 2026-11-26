package osrsps.game.content

import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.skill.MakeRecipe
import osrsps.game.skill.ObjCount
import osrsps.game.skill.Smithing
import osrsps.game.skill.SmithingLines

/** The barbarian anvil's words, in the dialogue file. */
object BarbarianAnvilLines : Lines("barbarian_anvil", "Barbarian anvil") {
    val MADE = line("made", "the message on making a spear or hasta at the barbarian anvil", "item")
}

/**
 * The barbarian anvil in Barbarian Village's pottery (`brut_anvil` 25349 at 3082,3411; Otto's Grotto's in Kandarin
 * has the same type), from the OSRS Wiki's Barbarian anvil, Barbarian Training and each spear's and hasta's page (read
 * 2026-11-19): Smith on it, or a bar or logs used on it, makes a spear or a hasta from one bar and its logs with a
 * hammer, at the bar's level and XP (bronze and logs 5 and 25, iron and oak 20 and 50, steel and willow 35 and 75,
 * mithril and maple 55 and 100, adamantite and yew 75 and 125, runite and magic 90 and 150), 4 ticks each. Nothing
 * else is made on it. The server is post-quest, so Barbarian Training's Smithing is taken as done (the Wiki's message
 * for an untrained player is never given).
 *
 * Guessed: the Make-X box offering a spear and a hasta for every metal whose bar and logs are held, the anvil's
 * hammering animation, and the messages. The Zamorakian hasta (Otto's conversion) isn't here.
 */
object BarbarianAnvil {
    const val ANVIL = 25349
    const val HAMMER = 2347
    const val SMITH = 1

    /** A metal: its bar, logs, spear and hasta (`brut_*_spear`), and the level and XP for either. */
    class Metal(val bar: Int, val logs: Int, val spear: Int, val hasta: Int, val level: Int, val xp: Int)

    val METALS = listOf(
        Metal(2349, 1511, 1237, 11367, 5, 25),
        Metal(2351, 1521, 1239, 11369, 20, 50),
        Metal(2353, 1519, 1241, 11371, 35, 75),
        Metal(2359, 1517, 1243, 11373, 55, 100),
        Metal(2361, 1515, 1245, 11375, 75, 125),
        Metal(2363, 1513, 1247, 11377, 90, 150),
    )

    private const val TICKS = 4

    private fun recipe(metal: Metal, product: Int) = MakeRecipe(
        "barbarian_$product", Skill.SMITHING, product, 1, metal.level, metal.xp * 10,
        listOf(ObjCount(metal.bar, 1), ObjCount(metal.logs, 1)), listOf(HAMMER), Smithing.ANVIL_ANIM, TICKS, TICKS,
        BarbarianAnvilLines.MADE, SmithingLines.ANVIL_LEVEL,
    )

    /** Every recipe, a spear and a hasta a metal. */
    val RECIPES: List<MakeRecipe> = METALS.flatMap { listOf(recipe(it, it.spear), recipe(it, it.hasta)) }

    internal suspend fun ScriptContext.smith(world: World) {
        if (player.inventory.count(HAMMER) == 0L) return world.message(player, SmithingLines.NO_HAMMER)
        val held = RECIPES.filter { it.times(player.inventory) > 0 }
        if (held.isEmpty()) return world.message(player, SmithingLines.NO_BARS)
        world.making.choose(this, held)
    }
}

/** Smith on the barbarian anvil, and its bars and logs used on it. */
fun registerBarbarianAnvil(world: World) = with(BarbarianAnvil) {
    world.handlers.onLoc(ANVIL, SMITH) { smith(world) }
    for (id in METALS.flatMap { listOf(it.bar, it.logs) } + HAMMER) world.handlers.onObjOnLoc(ANVIL, id) { smith(world) }
}
