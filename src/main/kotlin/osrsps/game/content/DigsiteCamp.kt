package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/** The Digsite camp's leftovers' words, in the dialogue file. */
object DigsiteCampLines : Lines("digsite_camp", "OSRS Wiki, Transcript:The Dig Site (the teddy's bush, the chest, the barrel), read 2026-11-19") {
    val BUSH_NOTHING = line("bush_nothing", "Search on a Digsite bush with nothing in it; words unsourced (the Wiki: nothing of interest)")
    val TEDDY_FOUND = line("teddy_found", "player: searching the teddy's bush with no teddy owned")
    val TEDDY_BOX = line("teddy_box", "the item box for the teddy")
    val CHEST_LOCKED = line("chest_locked", "Open on the tent's chest without its key; words unsourced")
    val CHEST_POWDER = line("chest_powder", "the item box for the chemical powder from the open chest")
    val CHEST_EMPTY = line("chest_empty", "Search on the open chest with chemical powder already carried; words unsourced")
    val BARREL_SHUT = line("barrel_shut", "player: Open on the sealed barrel")
    val BARREL_CLOSED = line("barrel_closed", "player: Search on the sealed barrel")
    val BARREL_OPENED = line("barrel_opened", "player: a trowel on the sealed barrel")
    val BARREL_BARE_HANDS = line("barrel_bare_hands", "player: Search on the open barrel")
    val BARREL_FILL = line("barrel_fill", "the item box for the vial filled at the open barrel")
    val BARREL_CAREFUL = line("barrel_careful", "player: after filling the vial")
    val BARREL_LID = line("barrel_lid", "the message as the lid goes back on")
    val TABLET_HAVE = line("tablet_have", "Take on the stone tablet with one already owned; words unsourced")
}

/**
 * The Digsite's camp and dungeon leftovers after The Dig Site (post-quest step 7), which the Digsite's other content
 * ([DigsiteDigs], [ExamCentre], [Digsite]) leaves out:
 *
 * - The bushes ([BUSH], 40 of them) hold nothing; the teddy's bush ([TEDDY_BUSH], 3357,3372) gives the teddy to a player
 *   who owns none (held, worn or banked), as the Wiki's drop table says.
 * - The chest in the panning tent ([CHEST], 3374,3378) opens with the Digsite chest key ([CHEST_KEY]), as the Wiki says it
 *   still does after the quest, and stays open for [Searching.CHANGE_TICKS] ticks (the Wiki's 3 minutes) for anyone;
 *   Search on it open gives chemical powder ([CHEMICAL_POWDER]); Close shuts it.
 * - The barrel west of the tent ([BARREL], the map's multiloc on varbit [BARREL_VARBIT] `qip_digsite_barrel`): Open and
 *   Search on it sealed get the player's lines, a trowel opens it (the varbit at 1), Search on it open gets the bare-hands
 *   line, and a vial used on it open fills with unidentified liquid ([UNIDENTIFIED_LIQUID]) and puts the lid back.
 * - The stone tablet in the dungeon's after-quest copy ([TABLET], 3373,9746, the multiloc on varbit 2548, still 0 so
 *   the tablet shows): Take gives a stone tablet to a player who owns none.
 *
 * Sources: the OSRS Wiki's Bush (The Dig Site), Chest (Chemical powder), Chest key (Digsite), Barrel (Digsite) and Stone
 * tablet (The Dig Site) pages and Transcript:The Dig Site, read 2026-11-19; ids and varbits from the 242 cache.
 *
 * Guessed: the bushes' message, the locked chest's, the opened chest's and the owned tablet's (their own keys, words
 * unsourced); the open chest giving one powder to a player carrying none; the tablet staying for every take; each
 * chathead expression; a vial's slot taking the liquid. Left out: Doug Deeping handing out a chest key after the quest
 * (his talk is [ExamCentre]'s, the quest's line never comes again), Desert Treasure II's winch (48918, east of the
 * camp: its dig is the Desert Treasure II quest's, outside Misthalin's content), and the liquid exploding when dropped.
 */
object DigsiteCamp {
    /** digsitebush. */
    const val BUSH = 2357
    /** digsitebushsample: the teddy's bush. */
    const val TEDDY_BUSH = 2358
    const val TEDDY = 673

    /** digchestclosed and digchestopen. */
    const val CHEST = 2361
    const val CHEST_OPEN = 2360
    /** digchestkey. */
    const val CHEST_KEY = 709
    /** unidentified_powder. */
    const val CHEMICAL_POWDER = 700

    /** digbarrelclosed: the map's multiloc, showing qip_digsite_barrel (17296) at 0 and its open form (17297) at 1. */
    const val BARREL = 2359
    const val BARREL_VARBIT = 2547
    const val TROWEL = 676
    const val VIAL = 229
    const val UNIDENTIFIED_LIQUID = 702

    /** qip_digsite_zaros_stone_tablet_multiloc. */
    const val TABLET = 17369
    const val STONE_TABLET = 699

    const val SEARCH = 1
    const val OPEN = 1
    const val CHEST_SEARCH = 1
    const val CHEST_CLOSE = 2
    const val BARREL_OPEN = 1
    const val BARREL_SEARCH = 2
    const val TAKE = 1

    /** Carried, worn, banked, dropped by the player and still on the floor, or in their grave (exploit review R55C-2). */
    fun owns(world: World, player: Player, obj: Int): Boolean =
        player.inventory.count(obj) > 0 || player.equipment.count(obj) > 0 || player.bank.count(obj) > 0 ||
            world.groundObjs.hasDropped(player, obj) || (world.deaths.graveOf(player)?.items?.count(obj) ?: 0) > 0

    fun barrelOpen(player: Player): Boolean =
        player.vars.types.varbit(BARREL_VARBIT) != null && player.vars.getVarbit(BARREL_VARBIT) == 1

    internal suspend fun ScriptContext.searchTeddyBush(world: World) {
        arriveDelay()
        if (owns(world, player, TEDDY)) return world.message(player, DigsiteCampLines.BUSH_NOTHING)
        val teddy = world.objTypes[TEDDY] ?: return
        world.dialogues.start(this) {
            chatPlayer(ChatAnim.QUIZ, DigsiteCampLines.TEDDY_FOUND)
            if (player.inventory.add(teddy, 1) > 0) objbox(TEDDY, DigsiteCampLines.TEDDY_BOX)
        }
    }

    internal suspend fun ScriptContext.openChest(world: World, chest: ZoneLoc) {
        if (player.inventory.count(CHEST_KEY) == 0L) {
            arriveDelay()
            return world.message(player, DigsiteCampLines.CHEST_LOCKED)
        }
        swapFurniture(world, chest, CHEST_OPEN, Searching.ANIM_OPEN)
    }

    /** Search on the open [chest]: it is looked up again after the arrival, so one shut meanwhile gives nothing. */
    internal suspend fun ScriptContext.searchChest(world: World, chest: ZoneLoc) {
        arriveDelay()
        if (!world.locs.exists(chest)) return
        if (player.inventory.count(CHEMICAL_POWDER) > 0) return world.message(player, DigsiteCampLines.CHEST_EMPTY)
        val powder = world.objTypes[CHEMICAL_POWDER] ?: return
        if (player.inventory.add(powder, 1) == 0) return world.message(player, GameLines.NO_SPACE)
        world.dialogues.start(this) { objbox(CHEMICAL_POWDER, DigsiteCampLines.CHEST_POWDER) }
    }

    internal suspend fun ScriptContext.barrelLine(world: World, line: Line) {
        arriveDelay()
        world.dialogues.start(this) { chatPlayer(ChatAnim.NEUTRAL, line) }
    }

    internal suspend fun ScriptContext.fillVial(world: World, slot: Int) {
        arriveDelay()
        if (!barrelOpen(player)) return world.message(player, GameLines.NOTHING_INTERESTING)
        if (player.inventory[slot]?.id != VIAL) return
        val liquid = world.objTypes[UNIDENTIFIED_LIQUID] ?: return
        player.inventory.remove(slot, 1)
        player.inventory.add(liquid, 1)
        // The lid goes back with the swap, so walking off during the talk can't leave the barrel open.
        player.vars.setVarbit(BARREL_VARBIT, 0)
        world.dialogues.start(this) {
            objbox(UNIDENTIFIED_LIQUID, DigsiteCampLines.BARREL_FILL)
            chatPlayer(ChatAnim.NEUTRAL, DigsiteCampLines.BARREL_CAREFUL)
        }
        world.message(player, DigsiteCampLines.BARREL_LID)
    }

    internal suspend fun ScriptContext.takeTablet(world: World) {
        arriveDelay()
        if (owns(world, player, STONE_TABLET)) return world.message(player, DigsiteCampLines.TABLET_HAVE)
        val tablet = world.objTypes[STONE_TABLET] ?: return
        if (player.inventory.add(tablet, 1) == 0) world.message(player, GameLines.NO_SPACE)
    }
}

fun registerDigsiteCamp(world: World) = with(DigsiteCamp) {
    registerSearch(world, BUSH, SEARCH, DigsiteCampLines.BUSH_NOTHING)
    world.handlers.onLoc(TEDDY_BUSH, SEARCH) { searchTeddyBush(world) }

    world.handlers.onLoc(CHEST, OPEN) { op -> openChest(world, op.loc!!) }
    world.handlers.onLoc(CHEST_OPEN, CHEST_SEARCH) { op -> searchChest(world, op.loc!!) }
    world.handlers.onLoc(CHEST_OPEN, CHEST_CLOSE) { op -> swapFurniture(world, op.loc!!, CHEST, Searching.ANIM_SHUT) }

    world.handlers.onLoc(BARREL, BARREL_OPEN) { if (!barrelOpen(player)) barrelLine(world, DigsiteCampLines.BARREL_SHUT) }
    world.handlers.onLoc(BARREL, BARREL_SEARCH) {
        barrelLine(world, if (barrelOpen(player)) DigsiteCampLines.BARREL_BARE_HANDS else DigsiteCampLines.BARREL_CLOSED)
    }
    world.handlers.onObjOnLoc(BARREL, TROWEL) {
        arriveDelay()
        if (barrelOpen(player)) return@onObjOnLoc world.message(player, GameLines.NOTHING_INTERESTING)
        player.vars.setVarbit(BARREL_VARBIT, 1)
        world.dialogues.start(this) { chatPlayer(ChatAnim.HAPPY, DigsiteCampLines.BARREL_OPENED) }
    }
    world.handlers.onObjOnLoc(BARREL, VIAL) { op -> fillVial(world, op.sub) }

    world.handlers.onLoc(TABLET, TAKE) { takeTablet(world) }
}
