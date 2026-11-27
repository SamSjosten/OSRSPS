package osrsps.game.content

import java.lang.ref.WeakReference
import java.util.WeakHashMap
import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.npc.Npc
import osrsps.map.Tile

/**
 * The Deep Wilderness Dungeon, levels 49 to 56 (Wilderness step W6.1; the Wiki's Deep Wilderness Dungeon, Crevice (Deep
 * Wilderness Dungeon) and each monster's page, read 2026-11-15). Members only, all single-way combat.
 *
 * - **The stairs**: the broken house's staircase (`stairs_cellar` 16664 at 3044,3924, Climb-down) and the dungeon's
 *   (`stairs_from_cellar` 16665 at 3044,10324, Climb-up). Both types stand elsewhere too, so only these two are
 *   [STAIRS]' and the rest answer nothing interesting until their places are built.
 * - **The monsters** (npc-spawns/deep-wilderness-dungeon.json): hill giants, chaos dwarves, shadow spiders, fire giants
 *   and monks of Zamorak where the Wiki's maps pin them. "The shadow spiders along the way will drain half of the
 *   player's current Prayer points when combat is initiated and at regular intervals" (and since 30 November 2022 "no
 *   longer ... if they are not in combat with the player"): [drainPrayer].
 * - **The crevice** (19043, level 46 Agility) between the stairs and the fire giants is an obstacle in
 *   skills/agility.json; its medium Wilderness Diary check is left out, as the diary-gated lairs' are (Sam, 9 Nov).
 * - **Item spawns**: the grimy guam leaf and attack potion(1) (obj-spawns/deep-wilderness-dungeon.tsv). The sack at
 *   3022,10356 is the Wilderness sacks' (empty: [MageArenaBank]).
 *
 * Where the stairs put the player is from RuneLite's Shortest Path plugin's transports (read 2026-11-15): 3044,10322
 * down and 3044,3927 up. GUESSED: the drain every [DRAIN_TICKS] while the spider fights the player, half rounded down.
 * Not built: the wine of Zamorak spawn (Telekinetic Grab isn't built), the members check, the monks' punch.
 */
object DeepWildernessDungeon {
    const val STAIRS_DOWN = 16664
    const val STAIRS_UP = 16665
    const val CLIMB = 1

    val STAIRS_DOWN_AT = Tile(3044, 3924)
    val STAIRS_UP_AT = Tile(3044, 10324)
    val ARRIVAL = Tile(3044, 10322)
    val EXIT = Tile(3044, 3927)

    /** Each staircase handled here, by its loc type and tile, and where it puts the player. */
    val STAIRS: Map<Pair<Int, Tile>, Tile> = mapOf(
        (STAIRS_DOWN to STAIRS_DOWN_AT) to ARRIVAL,
        (STAIRS_UP to STAIRS_UP_AT) to EXIT,
    )

    const val SHADOW_SPIDER = 3016
    const val DRAIN_TICKS = 10

    /** The dungeon: map square 47,161 on the ground level. */
    fun inDungeon(x: Int, z: Int): Boolean = x shr 6 == 47 && z shr 6 == 161

    /**
     * The tick each spider's fight with its target began, and whom it was with. The player is held weakly: a strong
     * hold would keep the key spider alive through the player's own `attackedNpc`, so a dead spider's record (its tick
     * no longer run to clear it) would never leave the map (Codex review on #1039).
     */
    private class Fight(player: Player, val start: Long) {
        private val ref = WeakReference(player)
        val player: Player? get() = ref.get()
    }

    private val fights = WeakHashMap<Npc, Fight>()

    /** Half [player]'s current Prayer points, rounded down, gone. */
    fun drainPrayer(player: Player) {
        val points = player.skills.currentLevel(Skill.PRAYER)
        player.skills.setCurrentLevel(Skill.PRAYER, points - points / 2)
    }

    /** One tick of a shadow spider: as its fight with a player begins, and every [DRAIN_TICKS] after, it drains them. */
    fun tick(world: World, npc: Npc) {
        val player = npc.target
        if (player == null || npc.dead || player.dead) {
            fights.remove(npc)
            return
        }
        val fight = fights[npc]?.takeIf { it.player === player } ?: Fight(player, world.tick).also { fights[npc] = it }
        if ((world.tick - fight.start) % DRAIN_TICKS == 0L) drainPrayer(player)
    }
}

fun registerDeepWildernessDungeon(world: World) {
    with(DeepWildernessDungeon) {
        for (type in listOf(STAIRS_DOWN, STAIRS_UP)) {
            world.handlers.onLoc(type, CLIMB) { op ->
                val loc = op.loc!!
                arriveDelay()
                val to = STAIRS[type to Tile(loc.x, loc.z)]
                if (loc.level != 0 || to == null) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
                world.teleport(player, to.x, to.z, 0)
            }
        }
        world.npcScripts.onTick(listOf(SHADOW_SPIDER)) { npc -> tick(world, npc) }
    }
}
