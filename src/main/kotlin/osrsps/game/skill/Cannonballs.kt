package osrsps.game.skill

import osrsps.game.GameLines
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.script.ScriptContext

/**
 * Cannonballs: a metal bar or an ammo mould used on a furnace, with the mould held, opens the Make-X box over the
 * cannonballs the bars held make, and each action casts one bar into 4 cannonballs. A double ammo mould casts two bars
 * into 8 at once, for twice the XP; it is used when held, ahead of the plain mould.
 *
 * Sources: the OSRS Wiki's cannonball pages (Bronze, Iron, Steel, Mithril, Adamant and Rune cannonball, Ammo mould and
 * Double ammo mould, read 2026-11-10) for the levels, XP, 4 a bar (8 for two with the double mould), and the ticks:
 * "Make-X is 8, then 10". Every account counts as having done Dwarf Cannon and Sleeping Giants (quest requirements are
 * dropped). Guessed: the box's title (the artisan skills' shared one), the animation (human_furnace, as smelting's), a double mould
 * with one bar left casting it alone into 4, the box listing only the cannonballs the bars held make, and one message an
 * action (live sends several as the metal heats, pours and cools; only the last is sent). Out: granite cannonballs (granite
 * dust on steel cannonballs, Slayer and Sailing), the Grimstone Dungeon's ancient furnace (Sailing 87), Nulodion's shop
 * for a new mould, and the dwarf multicannon itself.
 */
object Cannonballs {
    const val AMMO_MOULD = 4 // ammo_mould
    const val DOUBLE_AMMO_MOULD = 27012 // double_ammo_mould

    /** A cannonball: the bar it is cast from, the cannonball obj, the level and XP in tenths for one bar. */
    class Ball(val bar: Int, val obj: Int, val level: Int, val xpTenths: Int)

    /** Lowest first, as the Wiki's Cannonball page lists them. */
    val BALLS = listOf(
        Ball(2349, 31906, 5, 88), // bronze_bar, bronze_cannonball
        Ball(2351, 31908, 20, 172), // iron_bar, iron_cannonball
        Ball(2353, 2, 35, 256), // steel_bar, mcannonball (Steel cannonball)
        Ball(2359, 31910, 55, 340), // mithril_bar, mithril_cannonball
        Ball(2361, 31912, 75, 424), // adamantite_bar, adamant_cannonball
        Ball(2363, 31914, 90, 508), // runite_bar, rune_cannonball
    )

    const val PER_BAR = 4
    const val FIRST_TICKS = 8
    const val NEXT_TICKS = 10

    /** One bar into 4 with the plain mould. */
    val SINGLE: Map<Int, MakeRecipe> = BALLS.associate { it.bar to recipe(it, double = false) }

    /** Up to two bars into 8 with the double mould. */
    val DOUBLE: Map<Int, MakeRecipe> = BALLS.associate { it.bar to recipe(it, double = true) }

    private fun recipe(ball: Ball, double: Boolean) = MakeRecipe(
        key = if (double) "cannonball_double_${ball.obj}" else "cannonball_${ball.obj}",
        skill = Skill.SMITHING,
        product = ball.obj,
        amount = if (double) 2 else PER_BAR,
        level = ball.level,
        xpTenths = ball.xpTenths,
        inputs = listOf(ObjCount(ball.bar, 1)),
        tools = listOf(if (double) DOUBLE_AMMO_MOULD else AMMO_MOULD),
        anim = Smithing.FURNACE_ANIM,
        first = FIRST_TICKS,
        interval = NEXT_TICKS,
        made = SmithingLines.CANNONBALLS,
        tooLow = SmithingLines.CANNONBALL_LEVEL,
        batch = double,
        batchEach = if (double) PER_BAR else 1,
    )

    /** The recipes the player's mould and bars make, lowest first; the double mould's when it is held. */
    fun held(context: ScriptContext): List<MakeRecipe> {
        val inventory = context.player.inventory
        val recipes = if (inventory.count(DOUBLE_AMMO_MOULD) > 0) DOUBLE else SINGLE
        return BALLS.mapNotNull { recipes.getValue(it.bar).takeIf { recipe -> recipe.times(inventory) > 0 } }
    }

    fun register(world: World) {
        for (furnace in Smithing.FURNACES) {
            for (obj in BALLS.map { it.bar } + AMMO_MOULD + DOUBLE_AMMO_MOULD) {
                world.handlers.onObjOnLoc(furnace, obj) {
                    val held = held(this)
                    // Guessed: a bar on the furnace without a mould does what any other item would.
                    if (held.isEmpty()) return@onObjOnLoc player.message(world.textFor(player)[GameLines.NOTHING_INTERESTING])
                    world.making.choose(this, held)
                }
            }
        }
    }
}
