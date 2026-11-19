package osrsps.game.content

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** The White Tree's messages. */
object PalaceGardenLines : Lines("palace_garden", "White Tree") {
    val PICKED = line("picked", "Pick-fruit on the White Tree: the player picks a fruit")
    val NO_ROOM = line("no_room", "Pick-fruit on the White Tree with a full inventory")
}

/**
 * Varrock Palace's garden as Garden of Tranquillity leaves it (post-quest content, step 1). The beds, the statues and
 * Marlo's mended house are [osrsps.game.Quests.AFTER_QUESTS]' varbits; this is the White Tree's fruit.
 *
 * From the OSRS Wiki's White tree fruit and White Tree pages (read 2026-11-19): after the quest the tree in the
 * garden (the White Tree patch, multiloc 9209 on varbit 985 at 3229,3474) grows a fruit about every 5 minutes and holds
 * up to 4; picking one gives the fruit (6469) and 12 Farming XP. The varbit shows the fruit: 8 the bare tree, 9 to 12
 * one to four fruit (`garden_white_tree_fruit_1` to `_4`, whose op1 is Pick-fruit).
 *
 * - Inferred: the fruit grows on the farming clock's 5-minute ticks, one a tick, as a bush's berries do (the Wiki's
 *   Farming info gives the tree a 5-minute regrow), so it grows while the player is logged out too.
 * - Guessed: the picking animation (human_pickupfloor, as the fields' crops) and the one tick before the fruit comes;
 *   that every account finds all four on the tree the first time.
 */
object PalaceGarden {
    /** garden_white_tree_patch, the multiloc the map places. */
    const val WHITE_TREE = 9209
    const val PICK_FRUIT = 1
    const val VARBIT_WHITE_TREE = 985
    const val WHITE_TREE_FRUIT = 6469

    /** The varbit's value for the grown tree with no fruit; each fruit adds one. */
    const val BARE = 8
    const val MAX_FRUIT = 4
    const val PICK_XP_TENTHS = 120

    /** How many fruit [player]'s tree bears, or -1 when the varbit isn't on a grown tree (a test world, or a cache without it). */
    fun fruit(player: Player): Int {
        if (player.vars.types.varbit(VARBIT_WHITE_TREE) == null) return -1
        val value = player.vars.getVarbit(VARBIT_WHITE_TREE)
        return if (value in BARE..BARE + MAX_FRUIT) value - BARE else -1
    }

    private fun setFruit(player: Player, fruit: Int) = player.vars.setVarbit(VARBIT_WHITE_TREE, BARE + fruit)

    /**
     * Grows [player]'s fruit to farming tick [now]: one for each tick since the last grew, up to four. Varp [grownVarp]
     * holds that tick, 0 once the tree is full, so a tree never picked (a new account) shows all four.
     */
    fun grow(player: Player, grownVarp: Int, now: Long) {
        val fruit = fruit(player)
        if (fruit < 0 || fruit == MAX_FRUIT) return
        val since = player.vars[grownVarp].toLong()
        val grown = if (since == 0L) MAX_FRUIT.toLong() else (now - since).coerceAtLeast(0)
        if (grown == 0L) return
        val total = (fruit + grown).coerceAtMost(MAX_FRUIT.toLong()).toInt()
        setFruit(player, total)
        player.vars[grownVarp] = if (total == MAX_FRUIT) 0 else (since + (total - fruit)).toInt()
    }

    /** Every tick, so a fruit that grows shows while the player stands there; nothing changes between farming ticks. */
    fun process(world: World, player: Player) = grow(player, world.customVars.whiteTreeVarp, world.farming.tickOf(player))

    internal suspend fun ScriptContext.pickFruit(world: World) {
        process(world, player)
        if (fruit(player) <= 0) return
        if (player.inventory.freeSlots == 0) {
            world.message(player, PalaceGardenLines.NO_ROOM)
            return
        }
        player.anim(Crops.ANIM_PICK)
        delay(1)
        val fruit = fruit(player)
        if (fruit <= 0) return
        val type = world.objTypes[WHITE_TREE_FRUIT] ?: return
        if (player.inventory.add(type, 1) == 0) return
        // A full tree starts growing its next fruit from now.
        if (fruit == MAX_FRUIT) player.vars[world.customVars.whiteTreeVarp] = world.farming.tickOf(player).toInt()
        setFruit(player, fruit - 1)
        player.skills.addTenths(Skill.FARMING, PICK_XP_TENTHS)
        world.message(player, PalaceGardenLines.PICKED)
    }
}

fun registerPalaceGarden(world: World) {
    world.handlers.onLoc(PalaceGarden.WHITE_TREE, PalaceGarden.PICK_FRUIT) { with(PalaceGarden) { pickFruit(world) } }
}
