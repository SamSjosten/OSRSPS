package osrsps.game.content

import osrsps.game.FrameSlot
import osrsps.game.World
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.loc.Locs
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/**
 * The wild cadava and redberry bushes' messages. NO_BERRIES and ALWAYS_EMPTY are the Wiki's (Cadava bush (wild) and
 * Redberry bush (wild), read 2026-11-19); the Wiki gives no message for a pick or a full backpack, so those are guessed
 * and stay empty until a recording has them.
 */
object WildBushLines : Lines("wild_bush", "Cadava bush (wild)") {
    val NO_BERRIES = line("no_berries", "Pick-from on a bush with no berries left")
    val ALWAYS_EMPTY = line("always_empty", "Pick-from on the south-western cadava bush, which never has berries")
    val CADAVA_PICKED = line("cadava_picked", "a cadava berry picked (guessed: the Wiki gives no message)")
    val REDBERRY_PICKED = line("redberry_picked", "a redberry picked (guessed: the Wiki gives no message)")
    val NO_ROOM = line("no_room", "Pick-from with a full backpack (guessed: the Wiki gives no message)")
}

/**
 * What each signpost's four scrolls say, from the Wiki's Signpost page gallery (its pictures of each post's interface,
 * read 2026-11-19), and the Exam Centre's one line from the Signpost (Exam Centre) page.
 */
object SignpostLines : Lines("signpost", "Signpost") {
    val WEST_LUM_NORTH = line("west_lum_north", "the signpost west of the River Lum bridge, 3235,3228: the north scroll")
    val WEST_LUM_EAST = line("west_lum_east", "west of the Lum bridge: the east scroll")
    val WEST_LUM_SOUTH = line("west_lum_south", "west of the Lum bridge: the south scroll")
    val WEST_LUM_WEST = line("west_lum_west", "west of the Lum bridge: the west scroll")
    val EAST_LUM_NORTH = line("east_lum_north", "the signpost east of the River Lum bridge, 3261,3230: the north scroll")
    val EAST_LUM_EAST = line("east_lum_east", "east of the Lum bridge: the east scroll")
    val EAST_LUM_SOUTH = line("east_lum_south", "east of the Lum bridge: the south scroll")
    val EAST_LUM_WEST = line("east_lum_west", "east of the Lum bridge: the west scroll")
    val DRAYNOR_NORTH = line("draynor_north", "the Draynor crossroads signpost, 3107,3296: the north scroll")
    val DRAYNOR_EAST = line("draynor_east", "the Draynor crossroads: the east scroll")
    val DRAYNOR_SOUTH = line("draynor_south", "the Draynor crossroads: the south scroll")
    val DRAYNOR_WEST = line("draynor_west", "the Draynor crossroads: the west scroll")
    val WEST_VARROCK_NORTH = line("west_varrock_north", "the western signpost south of Varrock, 3268,3332: the north scroll")
    val WEST_VARROCK_EAST = line("west_varrock_east", "the western one south of Varrock: the east scroll")
    val WEST_VARROCK_SOUTH = line("west_varrock_south", "the western one south of Varrock: the south scroll")
    val WEST_VARROCK_WEST = line("west_varrock_west", "the western one south of Varrock: the west scroll")
    val EAST_VARROCK_NORTH = line("east_varrock_north", "the eastern signpost south of Varrock, 3283,3333: the north scroll")
    val EAST_VARROCK_EAST = line("east_varrock_east", "the eastern one south of Varrock: the east scroll")
    val EAST_VARROCK_SOUTH = line("east_varrock_south", "the eastern one south of Varrock: the south scroll")
    val EAST_VARROCK_WEST = line("east_varrock_west", "the eastern one south of Varrock: the west scroll")
    val EXAM_CENTRE = line("exam_centre", "Read on the Exam Centre's signpost: its one message")
}

/** Things to read in Varrock: the Combat Scroll (Wiki, Transcript:Combat Scroll) and the VTAM Corporation plaque. */
object VarrockReadingLines : Lines("varrock_reading", "Transcript:Combat Scroll") {
    val COMBAT_SCROLL = line("combat_scroll", "Read on the Combat Scroll: Ali Morrisane's guide, all five lines")
    val VTAM_PLAQUE = line("vtam_plaque", "Read on the VTAM Corporation plaque by the Phoenix Gang's hideout (no source yet: needs a recording)")
}

/**
 * Misthalin fixes 3, the first of the review's small option pack (19 Nov 2026): options the 242 cache gives Misthalin's
 * locs that had no handler.
 *
 * - **Wild cadava and redberry bushes** south-east of Varrock (Wiki, Cadava bush (wild) and Redberry bush (wild), read
 *   2026-11-19): each holds two berries, Pick-from takes one, and each picked berry grows back after 200 ticks. The
 *   cache has a type for two berries, one and none; a bush with two goes to one for 200 ticks, and one with one goes
 *   to none for 200 ticks and then to one for 200 more. That the second berry's clock starts only once the first is
 *   back is inferred from "it takes 200 ticks before another grows". The south-western cadava bush
 *   (`fai_varrock_cadavabush_tailored` 33183, Romeo and Juliet's) is always empty, with its own message. Guessed: the
 *   pick animation (Farming's berry pick, 2281) and its one tick.
 * - **Signposts** by the Lum bridge, at the Draynor crossroads and south of Varrock open `aide_compass` (135), each
 *   scroll's lower text set to that post's words; the Exam Centre's says one message.
 * - **Combat Scroll** and the **VTAM Corporation plaque** show their words in a message box (guessed: how live lays
 *   them out).
 * - **Boxes** (359) in Lumbridge and upstairs in Edgeville: Search finds nothing, as the other boxes do.
 * - **The cabbage and onion field** east of the south-east Varrock mine (`avium_cabbage01/02`, `avium_onion01/02`) picks as the other
 *   fields do ([Crops]).
 */
object MisthalinOptionPack {
    const val PICK_FROM = 1
    const val READ = 1
    const val SEARCH = 1

    /** Two berries, one, none. */
    val CADAVA = listOf(23625, 23626, 23627)
    val REDBERRY = listOf(23628, 23629, 23630)
    const val CADAVA_ALWAYS_EMPTY = 33183
    const val CADAVA_BERRIES = 753
    const val REDBERRIES = 1951
    const val BERRY_TICKS = 200

    /** picking_mid, Farming's berry pick (guessed for the wild bushes). */
    const val ANIM_PICK = 2281

    class Bush(val stages: List<Int>, val berry: Int, val picked: Line)

    val BUSHES = listOf(
        Bush(CADAVA, CADAVA_BERRIES, WildBushLines.CADAVA_PICKED),
        Bush(REDBERRY, REDBERRIES, WildBushLines.REDBERRY_PICKED),
    )

    const val SIGNPOST_LUMBRIDGE = 18493
    const val SIGNPOST_VARROCK = 7141
    const val SIGNPOST_EXAM_CENTRE = 2371

    /** aide_compass: each direction's lower, smaller text (the client draws the upper one, the direction's name, itself). */
    const val COMPASS = 135
    const val COMPASS_NORTH = 2
    const val COMPASS_EAST = 7
    const val COMPASS_SOUTH = 8
    const val COMPASS_WEST = 11

    /** One signpost: where it stands and its north, east, south and west words. */
    class Signpost(val id: Int, val x: Int, val z: Int, val north: Line, val east: Line, val south: Line, val west: Line)

    val SIGNPOSTS = with(SignpostLines) {
        listOf(
            Signpost(SIGNPOST_LUMBRIDGE, 3235, 3228, WEST_LUM_NORTH, WEST_LUM_EAST, WEST_LUM_SOUTH, WEST_LUM_WEST),
            Signpost(SIGNPOST_LUMBRIDGE, 3261, 3230, EAST_LUM_NORTH, EAST_LUM_EAST, EAST_LUM_SOUTH, EAST_LUM_WEST),
            Signpost(SIGNPOST_LUMBRIDGE, 3107, 3296, DRAYNOR_NORTH, DRAYNOR_EAST, DRAYNOR_SOUTH, DRAYNOR_WEST),
            Signpost(SIGNPOST_VARROCK, 3268, 3332, WEST_VARROCK_NORTH, WEST_VARROCK_EAST, WEST_VARROCK_SOUTH, WEST_VARROCK_WEST),
            Signpost(SIGNPOST_VARROCK, 3283, 3333, EAST_VARROCK_NORTH, EAST_VARROCK_EAST, EAST_VARROCK_SOUTH, EAST_VARROCK_WEST),
        )
    }

    const val COMBAT_SCROLL = 5094
    const val VTAM_PLAQUE = 23636
    const val BOXES = 359

    /**
     * When the last berry is picked on tick [now], the ticks until the one picked first is back: that berry keeps its
     * own deadline [due], and the second one's countdown starts once it is back. Null when it is due back this very
     * tick (the world's timers run after scripts), so it is back as this one is picked: one left, the next in full time.
     */
    internal fun ticksUntilFirstBack(due: Long?, now: Long): Int? {
        if (due == null) return BERRY_TICKS
        val left = due - now
        return if (left <= 0) null else left.toInt()
    }

    /** Takes one berry off [loc], a stage of [bush] that has one, on tick [now], and starts the bush growing back. */
    internal fun takeBerry(locs: Locs, loc: ZoneLoc, bush: Bush, now: Long) {
        val stage = bush.stages.indexOf(loc.id)
        val firstBack = if (stage == 0) null else ticksUntilFirstBack(locs.revertDue(loc), now)
        when {
            stage == 0 -> locs.replace(loc, bush.stages[1], BERRY_TICKS)
            firstBack == null -> {
                // The first berry is back as this one goes: the full bush, less this berry, growing back from now.
                locs.restore(loc.x, loc.z, loc.level, loc.layer)
                locs.cached(loc.x, loc.z, loc.level, loc.layer)?.let { locs.replace(it, bush.stages[1], BERRY_TICKS) }
            }
            else -> locs.replaceInStages(loc, bush.stages[2], firstBack, bush.stages[1], BERRY_TICKS)
        }
    }

    internal suspend fun ScriptContext.pickBerry(world: World, loc: ZoneLoc, bush: Bush) {
        val stage = bush.stages.indexOf(loc.id)
        if (stage == bush.stages.lastIndex) {
            world.message(player, WildBushLines.NO_BERRIES)
            return
        }
        if (player.inventory.freeSlots == 0) {
            world.message(player, WildBushLines.NO_ROOM)
            return
        }
        player.anim(ANIM_PICK)
        delay(1)
        // Someone else may have picked the same berry this tick.
        if (!world.locs.exists(loc)) return
        takeBerry(world.locs, loc, bush, world.tick)
        world.objTypes[bush.berry]?.let { player.inventory.add(it, 1) }
        world.message(player, bush.picked)
    }

    internal fun ScriptContext.readSignpost(world: World, post: Signpost) {
        val text = world.textFor(player)
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, COMPASS)
        world.interfaces.setText(player, COMPASS, COMPASS_NORTH, text[post.north])
        world.interfaces.setText(player, COMPASS, COMPASS_EAST, text[post.east])
        world.interfaces.setText(player, COMPASS, COMPASS_SOUTH, text[post.south])
        world.interfaces.setText(player, COMPASS, COMPASS_WEST, text[post.west])
    }
}

fun registerMisthalinOptionPack(world: World) = with(MisthalinOptionPack) {
    for (bush in BUSHES) {
        for (id in bush.stages) world.handlers.onLoc(id, PICK_FROM) { op -> pickBerry(world, op.loc!!, bush) }
    }
    world.handlers.onLoc(CADAVA_ALWAYS_EMPTY, PICK_FROM) { world.message(player, WildBushLines.ALWAYS_EMPTY) }

    for (id in SIGNPOSTS.map { it.id }.distinct()) {
        world.handlers.onLoc(id, READ) { op ->
            val loc = op.loc!!
            // A post of a type we have no words for says nothing rather than another post's.
            SIGNPOSTS.firstOrNull { it.id == loc.id && it.x == loc.x && it.z == loc.z }?.let { readSignpost(world, it) }
        }
    }
    world.handlers.onLoc(SIGNPOST_EXAM_CENTRE, READ) { world.message(player, SignpostLines.EXAM_CENTRE) }

    world.handlers.onLoc(COMBAT_SCROLL, READ) { world.dialogues.start(this) { mesbox(VarrockReadingLines.COMBAT_SCROLL) } }
    world.handlers.onLoc(VTAM_PLAQUE, READ) { world.dialogues.start(this) { mesbox(VarrockReadingLines.VTAM_PLAQUE) } }

    registerSearch(world, BOXES, SEARCH, SearchLines.BOXES_EMPTY)
}
