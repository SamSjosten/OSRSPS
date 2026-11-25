package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.WearPos
import osrsps.game.GameLines
import osrsps.map.Tile
import osrsps.game.script.ScriptContext
import osrsps.game.skill.Runecraft

/**
 * The Tunnel of Chaos, under the Saradomin statue south of the Jolly Boar Inn, east of Varrock (OSRS Wiki, Tunnel of
 * Chaos and Statue (Tunnel of Chaos), read 2026-11-19): as after What Lies Below, the statue stands open and Enter takes
 * the player down; the stairs (`23074`, and the wall steps 23073 beside them, Climb) bring them back up; the portal at the tunnel's far north-west end (`23095`,
 * Use) goes to the Chaos Altar's maze, its second level, but only with a chaos talisman carried, or a chaos or catalytic
 * tiara, a Runecraft cape or a max cape worn (a catalytic talisman doesn't do); the maze's portal there (`23098`, Use)
 * comes back.
 *
 * From the 242 cache: the statue is a multiloc (`surok_statuebase`, 23096) on `surok_tunnelcheck` (3524, 0 the statue to
 * excavate, 1 the open one), and the maze's portal (23098) on `surok_portal` (3529, 0 bare floor, 1 the portal); both are
 * set after the quest ([osrsps.game.Quests.AFTER_QUESTS]). The stairs and portal tiles, and the maze portal on level 1
 * (the Wiki's second level, from where the maze leads west to the ladder down beside the altar), are the cache's.
 *
 * Guessed: every landing tile (the tunnel's beside the stairs' foot, the surface's south of the statue, the maze's east
 * of its portal and the tunnel's east of its portal), the animation going down, no animation or message on the stairs or
 * through a portal, and the portal without the means giving the nothing-interesting message ([GameLines.NOTHING_INTERESTING]), as a closed set of ruins does.
 * Left out: Excavate with Anna Jones's bronze pickaxe (the statue is only shut before the quest), and the three Dagon'hai
 * who are in the tunnel during the quest.
 */
object TunnelOfChaos {
    const val STATUE = 23096
    const val STAIRS = 23074

    /** The stairs' wall steps beside them (no name, Climb), which go up the same way. */
    const val STAIRS_WALL = 23073
    const val PORTAL = 23095
    const val MAZE_PORTAL = 23098
    const val OP = 1

    const val VARBIT_TUNNEL_OPEN = 3524
    const val VARBIT_MAZE_PORTAL = 3529

    const val CHAOS_TALISMAN = 1452
    const val CHAOS_TIARA = 5543
    const val MAX_CAPE = 13280

    val TUNNEL_LANDING = Tile(3179, 5191)
    val SURFACE_LANDING = Tile(3283, 3467)
    val MAZE_LANDING = Tile(2271, 4836)
    const val MAZE_LEVEL = 1
    val PORTAL_LANDING = Tile(3153, 5233)

    /** Whether [player] may use the tunnel's portal: a chaos talisman carried, or a chaos or catalytic tiara, a Runecraft cape or a max cape worn. */
    fun mayUsePortal(player: Player): Boolean {
        if (player.inventory.count(CHAOS_TALISMAN) > 0) return true
        val head = player.equipment[WearPos.HEAD]?.id
        val cape = player.equipment[WearPos.CAPE]?.id
        return head == CHAOS_TIARA || head == Runecraft.CATALYTIC_TIARA ||
            cape == Runecraft.RUNECRAFT_CAPE || cape == Runecraft.RUNECRAFT_CAPE_TRIMMED || cape == MAX_CAPE
    }

    internal suspend fun ScriptContext.go(world: World, to: Tile, level: Int, anim: Int = -1) {
        arriveDelay()
        if (anim >= 0) {
            player.anim(anim)
            delay(1)
        }
        world.teleport(player, to.x, to.z, level)
    }
}

fun registerTunnelOfChaos(world: World) {
    with(TunnelOfChaos) {
        world.handlers.onLoc(STATUE, OP) { go(world, TUNNEL_LANDING, 0, Climbing.ANIM_PICKUP_FLOOR) }
        for (stairs in listOf(STAIRS, STAIRS_WALL)) world.handlers.onLoc(stairs, OP) { go(world, SURFACE_LANDING, 0) }
        world.handlers.onLoc(PORTAL, OP) {
            if (!mayUsePortal(player)) {
                arriveDelay()
                return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            }
            go(world, MAZE_LANDING, MAZE_LEVEL)
        }
        world.handlers.onLoc(MAZE_PORTAL, OP) { go(world, PORTAL_LANDING, 0) }
    }
}
