package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile

/** Ruantun's talk once Desert Treasure I is done (the Wiki's Transcript:Ruantun, read 2026-11-01: its Standard dialogue). */
object RuantunLines : Lines("ruantun", "Transcript:Ruantun") {
    val HELLO = line("hello", "the player's greeting")
    val GO_AWAY = line("go_away", "Ruantun's answer")
    val WHO = line("who", "the menu's first option, which the player then says")
    val WHO_SERVE = line("who_serve", "Ruantun's answer to it")
    val WHO_OKAY = line("who_okay", "the player's last line after it")
    val WHY = line("why", "the menu's second option, which the player then says")
    val WHY_DARK = line("why_dark", "Ruantun's answer to it")
    val WHY_OKAY = line("why_okay", "the player's last line after it")
    val ANVIL = line("anvil", "the menu's third option, which the player then says")
    val ANVIL_YES = line("anvil_yes", "Ruantun's answer to it")
    val ANVIL_THANKS = line("anvil_thanks", "the player's last line after it")
}

/**
 * The Draynor Sewers (Draynor piece step 10), under the village, as the 241 cache's map has them.
 *
 * - The east entrance, west of the jail: the trapdoor (`6434` at 3118,3244) opens into the open trapdoor (`6435`,
 *   Climb-down) for everyone, and the ladder below (`6436` at 3118,9643) climbs back up. The ladder is a tile further
 *   south of its trapdoor than a dungeon's 6400, so a climb keeps the player where they stood beside the one, beside the
 *   other ([EAST]) when that tile is free, else on a free tile beside it ([landing]), and climbing up never lands on the
 *   trapdoor itself. Both types climb only at these tiles; the 241 map has them nowhere else. The west entrance, north of the market: the same
 *   trapdoor at 3084,3272, whose ladder below (`ladder_from_cellar` 17385 at 3084,9672, the 6400 below) is the Lumbridge
 *   cellar's type and already climbs up ([LumbridgeCastle]). Opening uses the furniture's open animation and stays open
 *   for [Searching.CHANGE_TICKS] before it shuts again, as 2004Scape's furniture does: both guessed (the open type has
 *   no Close), as is where each climb puts the player.
 * - The zombies, skeletons and rats fight and drop as npc-combat.json and npc-drops.json have them
 *   (npc-spawns/draynor-sewers.json).
 * - The fallen-down house's anvil (`2097`) smiths and its fire (`26185`, a lit fire's type) cooks as any other.
 * - Ruantun (`malak` 3461) gives his Standard dialogue, after Desert Treasure I, which every account counts as done.
 *   Every chathead expression is guessed.
 */
object DraynorSewers {
    const val TRAPDOOR = 6434
    const val TRAPDOOR_OPEN = 6435
    const val EAST_LADDER = 6436
    const val OPEN = 1
    const val CLIMB = 1

    /** The east entrance's trapdoor and the ladder under it. */
    val EAST = Entrance(Tile(3118, 3244), Tile(3118, 9643))

    /** The west entrance's, which are a dungeon's 6400 apart. */
    val WEST = Entrance(Tile(3084, 3272), Tile(3084, 9672))

    /** A trapdoor on the ground and the ladder below it in the sewers. */
    data class Entrance(val trapdoor: Tile, val ladder: Tile)

    /** The entrance whose trapdoor is at [x], [z] on the ground. */
    fun entranceAt(x: Int, z: Int): Entrance? = listOf(EAST, WEST).firstOrNull { it.trapdoor.x == x && it.trapdoor.z == z }

    /**
     * Where a climb from [from] to [to] puts a player standing at [x], [z]: as far from [to] as they stood from [from]
     * when [canStand] there, else the first tile south, north, west or east of [to] they can stand on, else [to]'s
     * south tile (guessed: no entrance has all four blocked).
     */
    fun landing(from: Tile, to: Tile, x: Int, z: Int, canStand: (Int, Int) -> Boolean): Tile {
        val kept = Tile(to.x + (x - from.x).coerceIn(-1, 1), to.z + (z - from.z).coerceIn(-1, 1))
        if (kept != to && canStand(kept.x, kept.z)) return kept
        val around = listOf(Tile(to.x, to.z - 1), Tile(to.x, to.z + 1), Tile(to.x - 1, to.z), Tile(to.x + 1, to.z))
        return around.firstOrNull { canStand(it.x, it.z) } ?: around.first()
    }

    const val RUANTUN = 3461
    const val TALK_TO = 1

    internal suspend fun Dialogue.ruantun(): Unit = with(RuantunLines) {
        chatPlayer(ChatAnim.NEUTRAL, HELLO)
        chatNpc(ChatAnim.NEUTRAL, GO_AWAY)
        when (choice(WHO, WHY, ANVIL)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, WHO)
                chatNpc(ChatAnim.NEUTRAL, WHO_SERVE)
                chatPlayer(ChatAnim.NEUTRAL, WHO_OKAY)
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, WHY)
                chatNpc(ChatAnim.NEUTRAL, WHY_DARK)
                chatPlayer(ChatAnim.NEUTRAL, WHY_OKAY)
            }
            else -> {
                chatPlayer(ChatAnim.QUIZ, ANVIL)
                chatNpc(ChatAnim.NEUTRAL, ANVIL_YES)
                chatPlayer(ChatAnim.NEUTRAL, ANVIL_THANKS)
            }
        }
    }
}

fun registerDraynorSewers(world: World) {
    with(DraynorSewers) {
        world.handlers.onLoc(TRAPDOOR, OPEN) { op -> swapFurniture(world, op.loc!!, TRAPDOOR_OPEN, Searching.ANIM_OPEN) }
        // Keyed on tile as well as type: the open trapdoor and the ladder climb only between the entrances' tiles. The
        // 241 map has the trapdoor and the ladder nowhere else, so anywhere else is nothing interesting.
        world.handlers.onLoc(TRAPDOOR_OPEN, CLIMB) { op ->
            val loc = op.loc!!
            val entrance = entranceAt(loc.x, loc.z)
            if (entrance == null || loc.level != 0) world.message(player, GameLines.NOTHING_INTERESTING)
            else climbBetween(world, loc, entrance.trapdoor, entrance.ladder, Climbing.ANIM_PICKUP_FLOOR)
        }
        world.handlers.onLoc(EAST_LADDER, CLIMB) { op ->
            val loc = op.loc!!
            if (loc.x == EAST.ladder.x && loc.z == EAST.ladder.z && loc.level == 0) {
                climbBetween(world, loc, EAST.ladder, EAST.trapdoor, Climbing.ANIM_REACH_FOR_LADDER)
            } else {
                world.message(player, GameLines.NOTHING_INTERESTING)
            }
        }
        world.handlers.onNpc(RUANTUN, TALK_TO) { op -> world.dialogues.start(this, op.npc) { ruantun() } }
    }
}

/**
 * Climbs from [from] (the loc [loc] the player uses) to [to], keeping where the player stands beside it: they come out
 * as far from [to] as they stood from [from], when that tile can be stood on, and otherwise on the first of the tiles
 * south, north, west and east of [to] that can ([landing]). As for [climbDungeonLadder], the tile is taken before the wait.
 */
internal suspend fun ScriptContext.climbBetween(world: World, loc: ZoneLoc, from: Tile, to: Tile, anim: Int) {
    val land = DraynorSewers.landing(from, to, player.x, player.z) { x, z -> world.canStand(x, z, loc.level) }
    arriveDelay()
    player.anim(anim)
    delay(1)
    world.teleport(player, land.x, land.z, loc.level)
}
