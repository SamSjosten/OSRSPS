package osrsps.game.content

import osrsps.game.FrameSlot
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.QueueType
import osrsps.game.script.ScriptContext

/**
 * The cleaning activity's messages. Only [CRUMBLE] is quoted on the Wiki (Uncleaned find); the rest are guessed.
 */
object MuseumCleaningLines : Lines("museum_cleaning", "Uncleaned find") {
    val CRUMBLE = line("crumble", "a find crumbles while being cleaned (the Wiki's Uncleaned find page)")
    val NEED_GEAR = line("need_gear", "Clean without leather gloves and boots worn (guessed)")
    val NEED_TOOLS = line("need_tools", "Clean without a trowel, rock pick and specimen brush (guessed)")
    val NO_FINDS = line("no_finds", "Clean with no uncleaned finds (guessed)")
    val CLEANED = line("cleaned", "a find cleaned into {item} (guessed)", "item")
    val HAVE_TOOLS = line("have_tools", "Take on the tool rack with every tool held already (guessed)")
    val NO_ROOM = line("no_room", "Take on the rocks or the tools, or Add finds, with no room in the backpack (guessed)")
    val NO_ARTEFACTS = line("no_artefacts", "Add finds on the storage crate with nothing to add (guessed)")
    val REWARD = line("reward", "an artefact goes in the crate and {item} comes out (guessed)", "item")
}

/**
 * The Varrock Museum's finds cleaning, behind the cleaning area's gate (The Dig Site counts complete): Take on the tool
 * rack (`vm_digsite_tools` 24535) gives the trowel, rock pick, specimen brush, leather gloves and boots the player lacks; Take on the specimen
 * rocks (24557 to 24559) gives an uncleaned find; Clean on a specimen table (24556), or a find used on it, cleans finds
 * one after another while the player wears leather gloves and boots and carries the three tools; Add finds on the
 * storage crate (`vm_crate` 24534) trades a common artefact for a reward. The wall maps (24390 to 24392) show the
 * museum map (`vm_museum_map`, 527) and give one; the map's own Look-at shows it too.
 *
 * From the Wiki's Uncleaned find and Kudos pages, read 2026-11-11: each clean has a 14% chance to crumble; otherwise 38
 * in 51 a common artefact (pottery, jewellery, old chipped vase or arrowheads, evenly), and 13 in 51 a unique one (the
 * four symbols and coins 3 each, the clean necklace 1), which is an unremarkable find instead when the player already
 * holds or banks it; the crate's 195-weight table, its rare antique lamp included. Guessed: every message but the
 * crumble, the brushing animation's 3 ticks a find, one find per Take on the rocks (no repeating pickup), one artefact
 * per Add finds, the tool rack giving all missing items at once, and which antique lamp (`vm_reward_digsite`). Left
 * out: Kudos and the display cases (nothing counts Kudos yet), showing finds to the archaeologists, and rubbing the
 * lamp (no lamp interface yet).
 */
object MuseumCleaning {
    const val TOOLS = 24535
    val ROCKS = listOf(24557, 24558, 24559)
    const val TABLE = 24556
    const val CRATE = 24534
    val WALL_MAPS = listOf(24390, 24391, 24392)
    const val TAKE = 1
    const val CLEAN = 1
    const val ADD_FINDS = 1
    const val LOOK_AT = 1
    const val MAP_TAKE = 2

    const val MAP_INTERFACE = 527

    const val TROWEL = 676
    const val ROCK_PICK = 675
    const val SPECIMEN_BRUSH = 670
    val TOOL_IDS = listOf(TROWEL, ROCK_PICK, SPECIMEN_BRUSH)
    const val LEATHER_GLOVES = 1059
    const val LEATHER_BOOTS = 1061
    val GEAR_IDS = listOf(LEATHER_GLOVES, LEATHER_BOOTS)

    const val UNCLEANED_FIND = 11175
    const val ARROWHEADS = 11176
    const val JEWELLERY = 11177
    const val POTTERY = 11178
    const val OLD_COIN = 11179
    const val ANCIENT_COIN = 11180
    const val ANCIENT_SYMBOL = 11181
    const val OLD_SYMBOL = 11182
    const val OLD_CHIPPED_VASE = 11183
    const val CLEAN_NECKLACE = 11195
    const val ANTIQUE_LAMP = 11189

    val COMMON = listOf(POTTERY, JEWELLERY, OLD_CHIPPED_VASE, ARROWHEADS)
    val UNIQUE_FOUR = listOf(OLD_SYMBOL, ANCIENT_SYMBOL, OLD_COIN, ANCIENT_COIN)

    /** vm_player_use_specimen_brush. */
    const val ANIM_BRUSH = 6217
    const val CLEAN_TICKS = 3
    const val CRUMBLE_PERCENT = 14

    /** An item, its count range and weight. */
    data class Drop(val obj: Int, val min: Int, val max: Int, val weight: Int)

    /** The unremarkable finds, by the Wiki's out-of-459 rates. */
    val UNREMARKABLE = listOf(
        Drop(687, 1, 1, 26), Drop(1469, 1, 1, 26), Drop(1203, 1, 1, 26), Drop(1627, 1, 1, 13), Drop(526, 1, 1, 13), Drop(995, 5, 5, 13),
    )

    /** The storage crate's rewards, out of 195. */
    val CRATE_REWARDS = listOf(
        Drop(995, 17, 69, 19), Drop(1923, 1, 1, 21), Drop(1931, 1, 1, 21), Drop(9140, 1, 10, 10), Drop(1203, 1, 1, 10),
        Drop(9420, 1, 1, 10), Drop(9440, 1, 1, 10), Drop(438, 1, 1, 10), Drop(436, 1, 1, 10), Drop(440, 1, 1, 7), Drop(453, 1, 1, 10),
        Drop(447, 1, 1, 5), Drop(863, 1, 1, 10), Drop(807, 1, 50, 10), Drop(40, 1, 49, 10), Drop(1627, 1, 1, 5), Drop(1625, 1, 1, 5),
        Drop(532, 1, 1, 10), Drop(ANTIQUE_LAMP, 1, 1, 2),
    )

    fun pick(world: World, table: List<Drop>): Pair<Int, Int> {
        var roll = world.random.nextInt(table.sumOf { it.weight })
        for (drop in table) {
            if (roll < drop.weight) return drop.obj to world.random.between(drop.min, drop.max)
            roll -= drop.weight
        }
        error("unreachable")
    }

    private fun held(player: Player, obj: Int) = player.inventory.count(obj) > 0 || player.bank.count(obj) > 0

    /** Carried, worn or banked. */
    internal fun owned(player: Player, obj: Int) = held(player, obj) || player.equipment.count(obj) > 0

    /** What one cleaned find turns into: an item and count, or null when it crumbles. */
    fun clean(world: World, player: Player): Pair<Int, Int>? {
        if (world.random.nextInt(100) < CRUMBLE_PERCENT) return null
        val roll = world.random.nextInt(51)
        // 38 in 51 a common artefact; the other 13 a unique one, or an unremarkable find when that one is held already.
        if (roll >= 13) return COMMON[world.random.nextInt(COMMON.size)] to 1
        val unique = if (roll < 12) UNIQUE_FOUR[roll / 3] else CLEAN_NECKLACE
        return if (held(player, unique)) pick(world, UNREMARKABLE) else unique to 1
    }

    fun removeOne(player: Player, obj: Int): Boolean {
        val inv = player.inventory
        val slot = (0 until inv.capacity).firstOrNull { inv[it]?.id == obj } ?: return false
        return inv.remove(slot, 1) == 1
    }

    fun give(world: World, player: Player, obj: Int, count: Int) {
        world.objTypes[obj]?.let { world.groundObjs.give(player, it, count) }
    }
}

private suspend fun ScriptContext.cleanFinds(world: World) {
    arriveDelay()
    startClean(world)
}

/**
 * Starts brushing the next find when the player is dressed for it and has the tools and a find, and queues its end: a
 * weak queue, so walking off or clicking anything else stops the cleaning between finds, as the dairy cows' milking does.
 */
private fun ScriptContext.startClean(world: World): Unit = with(MuseumCleaning) {
    if (!readyToClean(world)) return@with
    player.anim(ANIM_BRUSH)
    queue(CLEAN_QUEUE, QueueType.WEAK, CLEAN_TICKS) { cleanOne(world) }
}

/**
 * Whether the player wears the gloves and boots and carries the tools and a find, saying what's missing when not. Asked
 * again as each find ends, since taking off the gloves doesn't stop the queue.
 */
private fun ScriptContext.readyToClean(world: World): Boolean = with(MuseumCleaning) {
    val missing = when {
        player.equipment.count(LEATHER_GLOVES) == 0L || player.equipment.count(LEATHER_BOOTS) == 0L -> MuseumCleaningLines.NEED_GEAR
        TOOL_IDS.any { player.inventory.count(it) == 0L } -> MuseumCleaningLines.NEED_TOOLS
        player.inventory.count(UNCLEANED_FIND) == 0L -> MuseumCleaningLines.NO_FINDS
        else -> return@with true
    }
    world.message(player, missing)
    false
}

/** One find cleaned (or crumbled), then the next started while finds are left. */
private fun ScriptContext.cleanOne(world: World): Unit = with(MuseumCleaning) {
    if (!readyToClean(world) || !removeOne(player, UNCLEANED_FIND)) return@with
    val found = clean(world, player)
    if (found == null) {
        world.message(player, MuseumCleaningLines.CRUMBLE)
    } else {
        give(world, player, found.first, found.second)
        world.objTypes[found.first]?.let { world.message(player, MuseumCleaningLines.CLEANED, "item" to it.name) }
    }
    if (player.inventory.count(UNCLEANED_FIND) > 0L) startClean(world)
}

private const val CLEAN_QUEUE = "museum_clean"

fun registerMuseumCleaning(world: World) {
    with(MuseumCleaning) {
        world.handlers.onLoc(TOOLS, TAKE) {
            // The rack holds the gloves and boots too (the Wiki's Kudos page): those not worn or carried come with the tools.
            // Guessed: banked ones count too (the Wiki doesn't say), so banking each set can't take an endless supply (exploit round 29).
            val missing = (TOOL_IDS + GEAR_IDS).filter { !owned(player, it) }
            when {
                missing.isEmpty() -> world.message(player, MuseumCleaningLines.HAVE_TOOLS)
                player.inventory.freeSlots < missing.size -> world.message(player, MuseumCleaningLines.NO_ROOM)
                else -> for (tool in missing) give(world, player, tool, 1)
            }
        }
        for (rocks in ROCKS) {
            world.handlers.onLoc(rocks, TAKE) {
                arriveDelay()
                // Checked on arrival, so a pack filled on the way doesn't drop the find on the floor.
                if (player.inventory.freeSlots == 0) return@onLoc world.message(player, MuseumCleaningLines.NO_ROOM)
                player.anim(Climbing.ANIM_PICKUP_FLOOR)
                give(world, player, UNCLEANED_FIND, 1)
            }
        }
        world.handlers.onLoc(TABLE, CLEAN) { cleanFinds(world) }
        world.handlers.onObjOnLoc(TABLE, UNCLEANED_FIND) { cleanFinds(world) }
        world.handlers.onLoc(CRATE, ADD_FINDS) {
            val artefact = COMMON.firstOrNull { player.inventory.count(it) > 0 } ?: return@onLoc world.message(player, MuseumCleaningLines.NO_ARTEFACTS)
            removeOne(player, artefact)
            val (obj, count) = pick(world, CRATE_REWARDS)
            give(world, player, obj, count)
            world.objTypes[obj]?.let { world.message(player, MuseumCleaningLines.REWARD, "item" to it.name) }
        }
        for (map in WALL_MAPS) {
            world.handlers.onLoc(map, LOOK_AT) { world.interfaces.openModal(player, FrameSlot.MAINMODAL, MAP_INTERFACE) }
            world.handlers.onLoc(map, MAP_TAKE) {
                if (player.inventory.freeSlots == 0) return@onLoc world.message(player, MuseumCleaningLines.NO_ROOM)
                give(world, player, VarrockMuseum.MUSEUM_MAP, 1)
            }
        }
        world.handlers.onObj(VarrockMuseum.MUSEUM_MAP, LOOK_AT) { world.interfaces.openModal(player, FrameSlot.MAINMODAL, MAP_INTERFACE) }
    }
}
