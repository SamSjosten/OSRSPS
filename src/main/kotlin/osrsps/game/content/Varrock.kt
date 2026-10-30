package osrsps.game.content

import kotlin.math.abs
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/** What Varrock's furniture says (keys only; the words are in data/dialogue.txt). */
object VarrockSearchLines : Lines("varrock_search", "Bookcase") {
    val BOOKCASE_EMPTY = line("bookcase_empty", "Search on a bookcase in a Varrock house")
    val SHELVES_EMPTY = line("shelves_empty", "Search on bookshelves in a Varrock house (the Shelves page)")
}

/** The banker behind the West Bank's locked door (the Varrock West Bank page). */
object WestBankDoorLines : Lines("varrock_bank_door", "Varrock West Bank") {
    val KNOCK = line("knock", "Knock-at on the locked door: the banker behind it won't let the player in")
}

/** What a stray dog and its player say over their heads (the Stray dog page). */
object StrayDogLines : Lines("stray_dog", "Stray dog") {
    val WHINE = line("whine", "Shoo-away: the dog's answer to the player's shoo")
    val WOOF = line("woof", "Pet: the dog's answer to the player's question")
}

/**
 * Varrock (the Misthalin roadmap's Varrock piece), step 1: the city's townsfolk (npc-spawns/varrock.json), its houses'
 * stairs, ladders and furniture, and its stray dogs. Loc ids and places are the live 241 cache's (map squares 49 to 51
 * by 52 to 54). The city's doors (`fai_varrock_door`, `_poor_door`, `_castle_door`, `_shanty_door` and the rest) pair by
 * name in [osrsps.game.loc.Doors], its field gates in [osrsps.game.loc.Gates], and the fountains fill vials as any water
 * source does ([osrsps.game.skill.Herblore]).
 *
 * Straight stairs (`fai_varrock_stairs`, 2x3, and their 2x2 tops) climb the way the loc's angle points: north at angle
 * 0, then east, south and west. The top piece covers the bottom's far two rows, so up puts the player on the tile past
 * the far end, and down on the tile before the near end, in the same column the player stood in. Read off the 241
 * map's eleven straight staircases, whose tops all sit where that rule puts them (and Draynor's two agree); not yet
 * watched on live. Spiral stairs and ladders work as Lumbridge's ([Climbing]).
 */
object Varrock {
    const val SPIRAL_STAIRS = 11789
    const val SPIRAL_STAIRS_TALLER = 11790
    const val SPIRAL_STAIRS_MIDDLE_TALLER = 11792
    const val SPIRAL_STAIRS_TOP = 11793

    const val LADDER = 11794
    const val LADDER_TOP = 11795
    const val LADDER_TALLER = 11801
    const val LADDER_TALLER_TOP = 11802

    /** `fai_varrock_stairs`, `_taller`, `_woodenstairs_castle` and `_taller_new_fix`: 2x3 bottoms. */
    val STRAIGHT_STAIRS = listOf(11796, 11797, 11798, 11807)

    /** `fai_varrock_stairs_top`: the 2x2 top every straight staircase shares. */
    const val STRAIGHT_STAIRS_TOP = 11799

    /** Bottoms are 3 tiles long; tops 2. */
    private const val BOTTOM_LENGTH = 3
    private const val TOP_LENGTH = 2

    /** Spiral stairs' middle piece: Climb (a menu), Climb-up, Climb-down. */
    const val CLIMB = 1
    const val CLIMB_UP = 2
    const val CLIMB_DOWN = 3

    /** Closed types with Open; their open types have Search first and Shut or Close second. */
    const val CHEST = 5108
    const val CHEST_OPEN = 5109
    const val DRAWERS = 7194
    const val DRAWERS_OPEN = 7195
    const val POSH_DRAWERS = 7442
    const val POSH_DRAWERS_OPEN = 7443

    /** `fai_varrock_large_crate`, `_large_crates`, `_small_crates`, `_red_crate` and `_green_crate`. */
    val CRATES = listOf(5106, 5107, 5110, 5113, 5115)

    /** `fai_varrock_boxes`. */
    const val BOXES = 5111

    /** `fai_varrock_shelf_books` and `_shelves_books`. */
    val SHELVES = listOf(7170, 7171)

    /** `fai_varrock_bookcase` and the posh ones in the richer houses. */
    val BOOKCASES = listOf(7176, 7177, 7178, 7179, 7180, 7181, 7431)

    /** `dog_stray` (grey) and `dog_stray2` (brown). */
    val STRAY_DOGS = listOf(2902, 2922)
    const val SHOO_AWAY = 4
    const val PET = 5

    /** `wolf_death` (Shoo-away) and `barklike` (Pet), the Stray dog page's sounds. */
    const val SHOO_SOUND = 911
    const val PET_SOUND = 652

    /** `fai_varrock_bank_locked_door`, the West Bank's door to its basement room, and the banker behind it. */
    const val WEST_BANK_LOCKED_DOOR = 11788
    const val KNOCK_AT = 1
    const val WEST_BANK_DOORGUARD = 8666

    /** Where straight stairs [loc] (a bottom when [up], else a top) put a player standing at [x], [z]. */
    fun straightStairs(loc: ZoneLoc, up: Boolean, x: Int, z: Int): Destination {
        val angle = loc.angle and 3
        // Across the stairs the player keeps their column (or row), held to the stairs' two.
        val acrossX = x.coerceIn(loc.x, loc.x + 1)
        val acrossZ = z.coerceIn(loc.z, loc.z + 1)
        val level = loc.level + if (up) 1 else -1
        // How far along the climb from the loc's south-west corner the player lands: going up, the tile past the
        // bottom's far end; coming down, the tile before the bottom's near end, which is a row behind the top's.
        val ahead = if (up) BOTTOM_LENGTH else -TOP_LENGTH
        val behind = if (up) -1 else TOP_LENGTH + 1
        return when (angle) {
            0 -> Destination(acrossX, loc.z + ahead, level)
            1 -> Destination(loc.x + ahead, acrossZ, level)
            2 -> Destination(acrossX, loc.z + behind, level)
            else -> Destination(loc.x + behind, acrossZ, level)
        }
    }
}

fun registerVarrock(world: World) {
    registerGrandExchangeClerks(world)
    registerVarrockChurches(world)
    registerVarrockInns(world)
    registerVarrockPalace(world)
    registerVarrockShopkeepers(world)
    registerVarrockStores(world)
    registerVarrockTraders(world)
    with(Varrock) {
        world.handlers.onLoc(SPIRAL_STAIRS, 1) { op -> climbStairs(world, op.loc!!, 1) }
        world.handlers.onLoc(SPIRAL_STAIRS_TALLER, 1) { op -> climbStairs(world, op.loc!!, 1) }
        world.handlers.onLoc(SPIRAL_STAIRS_MIDDLE_TALLER, CLIMB) { op -> climbStairsMenu(world, op.loc!!) }
        world.handlers.onLoc(SPIRAL_STAIRS_MIDDLE_TALLER, CLIMB_UP) { op -> climbStairs(world, op.loc!!, 1) }
        world.handlers.onLoc(SPIRAL_STAIRS_MIDDLE_TALLER, CLIMB_DOWN) { op -> climbStairs(world, op.loc!!, -1) }
        world.handlers.onLoc(SPIRAL_STAIRS_TOP, 1) { op -> climbStairs(world, op.loc!!, -1) }

        for (bottom in listOf(LADDER, LADDER_TALLER)) {
            world.handlers.onLoc(bottom, 1) { op -> climbLadder(world, op.loc!!, 1, Climbing.ANIM_REACH_FOR_LADDER) }
        }
        for (top in listOf(LADDER_TOP, LADDER_TALLER_TOP)) {
            world.handlers.onLoc(top, 1) { op -> climbLadder(world, op.loc!!, -1, Climbing.ANIM_PICKUP_FLOOR) }
        }

        for (bottom in STRAIGHT_STAIRS) world.handlers.onLoc(bottom, 1) { op -> climbStraight(world, op.loc!!, up = true) }
        world.handlers.onLoc(STRAIGHT_STAIRS_TOP, 1) { op -> climbStraight(world, op.loc!!, up = false) }

        registerOpenable(world, CHEST, CHEST_OPEN, SearchLines.CHEST_EMPTY, search = 1, shut = 2)
        registerOpenable(world, DRAWERS, DRAWERS_OPEN, SearchLines.DRAWERS_EMPTY, search = 1, shut = 2)
        registerOpenable(world, POSH_DRAWERS, POSH_DRAWERS_OPEN, SearchLines.DRAWERS_EMPTY, search = 1, shut = 2)
        for (crate in CRATES) registerSearch(world, crate, 1, SearchLines.CRATE_EMPTY)
        registerSearch(world, BOXES, 1, SearchLines.BOXES_EMPTY)
        for (shelves in SHELVES) registerSearch(world, shelves, 1, VarrockSearchLines.SHELVES_EMPTY)
        for (bookcase in BOOKCASES) registerSearch(world, bookcase, 1, VarrockSearchLines.BOOKCASE_EMPTY)

        // The banker answers from behind the door; one line, and its chathead expression, guessed.
        world.handlers.onLoc(WEST_BANK_LOCKED_DOOR, KNOCK_AT) { op ->
            val door = op.loc!!
            val banker = world.npcs.all.firstOrNull { it.type.id == WEST_BANK_DOORGUARD && it.level == door.level && abs(it.x - door.x) <= 3 && abs(it.z - door.z) <= 3 }
            if (banker == null) {
                world.message(player, WestBankDoorLines.KNOCK)
            } else {
                world.dialogues.start(this, banker) { chatNpc(ChatAnim.NEUTRAL, WestBankDoorLines.KNOCK) }
            }
        }

        for (dog in STRAY_DOGS) {
            world.handlers.onNpc(dog, SHOO_AWAY) { op ->
                player.sound(SHOO_SOUND)
                op.npc!!.effects.say(world.dialogueText[StrayDogLines.WHINE])
            }
            world.handlers.onNpc(dog, PET) { op ->
                player.sound(PET_SOUND)
                op.npc!!.effects.say(world.dialogueText[StrayDogLines.WOOF])
            }
        }
    }
}

/** Straight stairs [loc]: up from a bottom piece, down from a top. No wait and no animation, as spiral stairs. */
private fun ScriptContext.climbStraight(world: World, loc: ZoneLoc, up: Boolean) {
    val to = Varrock.straightStairs(loc, up, player.x, player.z)
    world.teleport(player, to.x, to.z, to.level)
}
