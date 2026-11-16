package osrsps.game.content

import osrsps.game.World
import osrsps.game.combat.PvpLines
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

/** The teleport levers' words (keys only; the words are in data/dialogue.txt). */
object WildernessLeverLines : Lines("wildy_lever", "Teleport levers") {
    val PULL = line("pull", "Pull on the Edgeville, Ardougne or Deserted Keep lever, first (the Wiki's lever pages)")
    val INTO = line("into", "the Edgeville or Ardougne lever's second line, on arrival in the Deserted Keep (the Wiki's lever pages)")
    val OUT = line("out", "the Deserted Keep lever's second line, on arrival in Ardougne (the Wiki's Lever (Deserted Keep) page)")
}

/**
 * The Wilderness's teleport levers (Wilderness step W3.2): Edgeville's (`edgeville_wildy_lever` 26761) and East
 * Ardougne's (`wildinlever` 1814) to the Deserted Keep at level 51, the Keep's (`wildoutlever` 1815, a multiloc showing
 * `wildoutlever_default` 9472 before the easy Wilderness Diary) back to East Ardougne, and the Mage Arena bank's two
 * (`magearena_lever_to_cellar` 5959 in the hut north of the arena, `magearena_lever_from_cellar` 5960 in the bank). A
 * Tele Blocked player can't use any of them (the Wiki's Tele Block page), and none minds the Wilderness's level-20
 * teleport limit. The Edgeville, Ardougne and Keep levers send [WildernessLeverLines.PULL] on the pull, then
 * [WildernessLeverLines.INTO] or [WildernessLeverLines.OUT] on arrival (the Wiki's three lever pages).
 *
 * Inferred: each lands the player on the far lever's own tile (a wall decoration, so standable). Guessed until
 * recorded: the pull (`human_leverdown` 834, as the project's other levers), the standard teleport's animation and
 * graphic 2 ticks later and the move 3 ticks after that, and the Tele Block refusal being the teleports' one. Not built:
 * the Edgeville and Ardougne levers' warning (its words and settings toggle aren't in the cache's Doomsayer list), the
 * Keep lever's Edgeville option and Swap-options (the easy Wilderness Diary), and the Mage Arena bank levers' messages
 * (the Wiki gives none).
 */
object WildernessLevers {
    const val EDGEVILLE = EdgevillePeople.LEVER
    const val ARDOUGNE = 1814
    const val KEEP = 1815
    const val KEEP_DEFAULT = 9472
    const val TO_MAGE_BANK = 5959
    const val FROM_MAGE_BANK = 5960
    const val PULL = 1

    const val PULL_ANIM = 834
    const val TELEPORT_ANIM = SkullSceptre.TELEPORT_ANIM
    const val TELEPORT_SPOTANIM = SkullSceptre.TELEPORT_SPOTANIM
    const val TELEPORT_SPOTANIM_HEIGHT = SkullSceptre.TELEPORT_SPOTANIM_HEIGHT

    /** Guessed: from the pull to the teleport's animation, and from that to the move. */
    const val PULL_TICKS = 2
    const val TELEPORT_TICKS = 3

    /** Each lever's far end: the other lever's tile. */
    data class Destination(val x: Int, val z: Int, val level: Int = 0)

    val KEEP_TILE = Destination(3153, 3923)
    val ARDOUGNE_TILE = Destination(2561, 3311)
    val MAGE_BANK_TILE = Destination(2539, 4712)
    val MAGE_HUT_TILE = Destination(3090, 3956)

    /** Lever type to where it goes and its arrival line (null where the Wiki gives none). */
    val LEVERS: Map<Int, Pair<Destination, Line?>> = mapOf(
        EDGEVILLE to (KEEP_TILE to WildernessLeverLines.INTO),
        ARDOUGNE to (KEEP_TILE to WildernessLeverLines.INTO),
        KEEP to (ARDOUGNE_TILE to WildernessLeverLines.OUT),
        KEEP_DEFAULT to (ARDOUGNE_TILE to WildernessLeverLines.OUT),
        TO_MAGE_BANK to (MAGE_BANK_TILE to null),
        FROM_MAGE_BANK to (MAGE_HUT_TILE to null),
    )
}

fun registerWildernessLevers(world: World) {
    for ((lever, target) in WildernessLevers.LEVERS) {
        val (to, arrival) = target
        world.handlers.onLoc(lever, WildernessLevers.PULL) {
            arriveDelay()
            if (player.teleblockTicks > 0) return@onLoc world.message(player, PvpLines.TELEPORT_BLOCKED)
            player.anim(WildernessLevers.PULL_ANIM)
            if (arrival != null) world.message(player, WildernessLeverLines.PULL)
            delay(WildernessLevers.PULL_TICKS)
            // A Tele Block landing during the pull or the teleport still stops it (Codex review of W3.2).
            if (player.teleblockTicks > 0) {
                player.anim(-1)
                return@onLoc world.message(player, PvpLines.TELEPORT_BLOCKED)
            }
            player.anim(WildernessLevers.TELEPORT_ANIM)
            player.spotanim(WildernessLevers.TELEPORT_SPOTANIM, WildernessLevers.TELEPORT_SPOTANIM_HEIGHT)
            delay(WildernessLevers.TELEPORT_TICKS)
            if (player.teleblockTicks > 0) {
                player.anim(-1)
                return@onLoc world.message(player, PvpLines.TELEPORT_BLOCKED)
            }
            // The arrival's line and the reset go in the move's own tick, just before it.
            player.anim(-1)
            if (arrival != null) world.message(player, arrival)
            world.teleport(player, to.x, to.z, to.level)
        }
    }
}
