package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.map.Tile

/** What the Wizards' Tower's bookcases say (the OSRS Wiki's Transcript:Bookcase (Wizards' Tower)). */
object WizardsTowerLines : Lines("wizards_tower", "Wizards' Tower bookcases (the Wiki's Transcript:Bookcase (Wizards' Tower))") {
    val BOOKCASE = line("bookcase", "Search on a bookcase: the first box, before the book picked")
    val BOOKS = (1..8).map { line("book_$it", "Search on a bookcase: the title of book $it of 8, in the Transcript's order") }
    val INTERESTING = line("interesting", "Search on a bookcase: the last box, after the title")
}

/**
 * The Wizards' Tower's building (Draynor piece step 14). Its people, Sedridor's essence mine teleport and the basement's
 * rooms come with step 15.
 *
 * - The spiral staircase on the west side joins the three floors (`fai_wiztower_spiralstairs` 12536 up from the ground,
 *   `_middle` 12537 with Climb, Climb-up and Climb-down, `fai_wiztower_spiralstairstop` 12538 down from the second),
 *   as other spiral stairs do ([climbStairs]).
 * - The ladder by the ground floor's library (`wizards_tower_laddertop` 2147 at 3104,3162) goes down to 3104,9576 beside
 *   the basement's (`wizards_tower_ladder` 2148 at 3103,9576), which comes back up to 3105,3162: 2004Scape's ladders.rs2
 *   has both, the tower's map then as now.
 * - The doors (`fai_wiztower_poor_door` 23972 and the basement's `poordoor` 1535) pair with their open types by name
 *   ([osrsps.game.loc.Doors]); the closed chest and drawers are Lumbridge's and Draynor's.
 * - A bookcase (`fai_wiztower_bookcase` 12539, `_wall` 12540) shows a message box, one of the eight books at random and a
 *   last box, as the Wiki's transcript has them. Guessed: that all three are message boxes (the transcript names no
 *   speaker) and that each book is as likely as the others.
 * - The first floor's boxes (12545) hold nothing, as boxes elsewhere.
 * - The level 9 wizards fight with Fire Strike (npc-combat.json, npc-drops.json); the second floor's lesser demon sits in
 *   a cage of walls and a railing (`railing_modelled` 24097, which the cache lets projectiles through), so only ranged
 *   attacks and spells reach it and it never reaches anyone. Its drops fall in the cage.
 * - Item spawns: obj-spawns/wizards-tower.tsv; NPCs: npc-spawns/wizards-tower.json.
 */
object WizardsTower {
    const val STAIRS_BOTTOM = 12536
    const val STAIRS_MIDDLE = 12537
    const val STAIRS_TOP = 12538
    const val CLIMB = 1
    const val CLIMB_UP = 2
    const val CLIMB_DOWN = 3

    const val LADDER_DOWN = 2147
    const val LADDER_UP = 2148
    val LADDER_DOWN_TO = Tile(3104, 9576)
    val LADDER_UP_TO = Tile(3105, 3162)

    val BOOKCASES = listOf(12539, 12540)
    const val BOXES = 12545
    const val SEARCH = 1
}

/** A ladder whose other end is somewhere else on the map: the climb animation, a tick, then [to]. */
private suspend fun ScriptContext.climbLadderTo(world: World, to: Tile, level: Int, anim: Int) {
    arriveDelay()
    player.anim(anim)
    delay(1)
    world.teleport(player, to.x, to.z, level)
}

fun registerWizardsTower(world: World) {
    with(WizardsTower) {
        world.handlers.onLoc(STAIRS_BOTTOM, CLIMB) { op -> climbStairs(world, op.loc!!, 1) }
        world.handlers.onLoc(STAIRS_MIDDLE, CLIMB) { op -> climbStairsMenu(world, op.loc!!) }
        world.handlers.onLoc(STAIRS_MIDDLE, CLIMB_UP) { op -> climbStairs(world, op.loc!!, 1) }
        world.handlers.onLoc(STAIRS_MIDDLE, CLIMB_DOWN) { op -> climbStairs(world, op.loc!!, -1) }
        world.handlers.onLoc(STAIRS_TOP, CLIMB) { op -> climbStairs(world, op.loc!!, -1) }
        world.handlers.onLoc(LADDER_DOWN, CLIMB) { op -> climbLadderTo(world, LADDER_DOWN_TO, op.loc!!.level, Climbing.ANIM_PICKUP_FLOOR) }
        world.handlers.onLoc(LADDER_UP, CLIMB) { op -> climbLadderTo(world, LADDER_UP_TO, op.loc!!.level, Climbing.ANIM_REACH_FOR_LADDER) }
        for (bookcase in BOOKCASES) {
            world.handlers.onLoc(bookcase, SEARCH) {
                val book = WizardsTowerLines.BOOKS[world.random.nextInt(WizardsTowerLines.BOOKS.size)]
                world.dialogues.start(this) {
                    mesbox(WizardsTowerLines.BOOKCASE)
                    mesbox(book)
                    mesbox(WizardsTowerLines.INTERESTING)
                }
            }
        }
        registerSearch(world, BOXES, SEARCH, SearchLines.BOXES_EMPTY)
    }
}
