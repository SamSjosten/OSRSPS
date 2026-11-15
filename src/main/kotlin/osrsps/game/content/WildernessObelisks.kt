package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.zone.ZoneLoc

/** The Wilderness obelisks' words (keys only; the words are in data/dialogue.txt). */
object WildernessObeliskLines : Lines("obelisk", "Wilderness Obelisk") {
    val TELEPORTED = line("teleported", "a player the obelisk moved, on arrival (the Wiki's Wilderness Obelisk page)")
}

/**
 * The six Wilderness obelisks (Wilderness step W3.1). Each is four corner stones round a 3x3 centre; Activate on a
 * corner lights all four, and after a delay everyone standing in that centre goes to one obelisk picked at random
 * from all six, the same one included, with "Ancient magic teleports you somewhere in the Wilderness!". A Tele Blocked
 * player is left behind. The six stand at levels 13, 18 to 19, 27, 35, 44 and 50 (the Wiki's Wilderness Obelisk page:
 * the six, their levels, the 3x3, the random pick, the Tele Block rule and the message).
 *
 * The stones are `wilderness_portal_stone_0` to `_5` (14826 to 14831), one type per obelisk, at the corners the 242 map
 * gives; lit they are `wilderness_portal_stone_glowing` (14825, its glow animation its own).
 *
 * Guessed until recorded: the delay ([TELEPORT_TICKS], the page says only "a short delay"), the stones staying lit
 * until the teleport, the `wilderness_portal` (661) spotanim on the centre's nine tiles and the
 * `human_teleport_other_impact` (1816) animation with `teleport_other_impact` (342) on each player [GLOW_TICKS] before
 * they go (the names fit; the timing doesn't come from a recording), Activate on a lit obelisk doing nothing more, and
 * each player keeping their place in the 3x3 at the far end. Not built: Teleport to Destination and Set Destination
 * (the hard Wilderness Diary), and the player-owned house's obelisk.
 */
class WildernessObelisks(private val world: World) {
    /** One obelisk: its stones' type and the centre tile of its 3x3. */
    data class Obelisk(val stone: Int, val x: Int, val z: Int) {
        val corners get() = listOf(-2 to -2, -2 to 2, 2 to -2, 2 to 2).map { (dx, dz) -> x + dx to z + dz }

        fun inCentre(px: Int, pz: Int, level: Int) = level == 0 && px in x - 1..x + 1 && pz in z - 1..z + 1
    }

    private class Activation(val obelisk: Obelisk, val goTick: Long, val destination: Obelisk)

    private val active = HashMap<Obelisk, Activation>()

    /** Activate on one of [obelisk]'s stones: lights it and sets everyone in its centre going, unless it is already lit. */
    fun activate(obelisk: Obelisk): Boolean {
        if (obelisk in active) return false
        for ((cx, cz) in obelisk.corners) {
            val stone = world.locs.find(obelisk.stone, cx, cz, 0) ?: continue
            world.locs.replace(stone, GLOWING, revertAfter = TELEPORT_TICKS)
        }
        active[obelisk] = Activation(obelisk, world.tick + TELEPORT_TICKS, ALL[world.random.nextInt(ALL.size)])
        return true
    }

    fun process() {
        if (active.isEmpty()) return
        val tick = world.tick
        val done = ArrayList<Obelisk>()
        for ((obelisk, activation) in active) {
            val left = activation.goTick - tick
            if (left == GLOW_TICKS.toLong()) glow(obelisk)
            if (left > 0) continue
            done += obelisk
            val dest = activation.destination
            for (player in riders(obelisk)) {
                world.teleport(player, dest.x + (player.x - obelisk.x), dest.z + (player.z - obelisk.z), 0)
                world.message(player, WildernessObeliskLines.TELEPORTED)
            }
        }
        done.forEach(active::remove)
    }

    private fun glow(obelisk: Obelisk) {
        for (dx in -1..1) for (dz in -1..1) world.zones.mapAnim(obelisk.x + dx, obelisk.z + dz, 0, PORTAL_SPOTANIM)
        for (player in riders(obelisk)) {
            player.anim(TELEPORT_ANIM)
            player.spotanim(TELEPORT_SPOTANIM)
        }
    }

    /** The players the obelisk takes: in its centre, alive and not Tele Blocked. */
    private fun riders(obelisk: Obelisk): List<Player> = world.onlinePlayers.filter {
        !it.loggedOut && !it.dead && it.teleblockTicks <= 0 && obelisk.inCentre(it.x, it.z, it.level)
    }

    companion object {
        const val GLOWING = 14825
        const val ACTIVATE = 1
        const val PORTAL_SPOTANIM = 661
        const val TELEPORT_ANIM = 1816
        const val TELEPORT_SPOTANIM = 342

        /** Guessed: from Activate to the move. */
        const val TELEPORT_TICKS = 8

        /** Guessed: how long before the move the centre lights up and the players animate. */
        const val GLOW_TICKS = 2

        /** The six, by stone type, with their centres (the 242 map's corner stones) and levels. */
        val LEVEL_44 = Obelisk(14826, 2980, 3866)
        val LEVEL_27 = Obelisk(14827, 3035, 3732)
        val LEVEL_35 = Obelisk(14828, 3106, 3794)
        val LEVEL_13 = Obelisk(14829, 3156, 3620)
        val LEVEL_19 = Obelisk(14830, 3227, 3667)
        val LEVEL_50 = Obelisk(14831, 3307, 3916)
        val ALL = listOf(LEVEL_13, LEVEL_19, LEVEL_27, LEVEL_35, LEVEL_44, LEVEL_50)

        /** The obelisk [stone] belongs to, if it is one of its four corners. */
        fun of(stone: ZoneLoc): Obelisk? =
            ALL.firstOrNull { it.stone == stone.id && stone.level == 0 && (stone.x to stone.z) in it.corners }
    }
}

fun registerWildernessObelisks(world: World) {
    for (obelisk in WildernessObelisks.ALL) {
        world.handlers.onLoc(obelisk.stone, WildernessObelisks.ACTIVATE) { op ->
            val target = WildernessObelisks.of(op.loc!!) ?: return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            arriveDelay()
            world.obelisks.activate(target)
        }
    }
}
