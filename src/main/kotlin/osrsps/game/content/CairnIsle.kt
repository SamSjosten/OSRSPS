package osrsps.game.content

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.HitsplatType
import osrsps.game.dialogue.Lines
import osrsps.game.skill.Agility
import osrsps.game.skill.SuccessRoll
import osrsps.map.Tile

/** Cairn Isle's words, the Wiki's Cairn Isle page (read 2026-11-19). */
object CairnIsleLines : Lines("cairn_isle", "Cairn Isle") {
    val BALANCE = line("balance", "the bridge crossed: the player kept their balance")
    val FELL = line("fell", "the bridge failed: washed back to the shore (keys only: the Wiki gives no words)")
}

/**
 * The bridge to Cairn Isle (Karamja step K4.1), as the Wiki's Cairn Isle page has it (read 2026-11-19): stepping onto
 * either end of the bridge (z 2979, x 2771 to 2785 on the 242 map, from the promontory the rock slide climbs to) checks
 * Agility, walking or running alike, with the Wiki's success chart (75/256 at 1 to 250/256 at 99). Across, the player is
 * told they kept their balance and gets 10 Agility XP; a fall deals three hits of an eleventh of their
 * Hitpoints each, rounded down, and washes them back to the shore.
 *
 * Inferred: the check comes on the bridge's first tile and the player is walked the rest of the way. Guessed: the fall
 * comes halfway, and the shore it washes them to (the beach south of the bridge, [SHORE]); its message is a key only.
 */
object CairnIsle {
    const val Z = 2979
    const val WEST_END = 2771
    const val EAST_END = 2785
    const val XP_TENTHS = 100
    val SUCCESS = 75 to 250

    /** Guessed: where a fall washes the player up. */
    val SHORE = Tile(2779, 2975)

    /** A fall's three hits, each [hitpoints] / 11. */
    fun fallHit(hitpoints: Int): Int = hitpoints / 11

    const val FALL_HITS = 3
}

fun registerCairnIsle(world: World) {
    world.stepHooks += { player, trail -> stepped(world, player, trail) }
    // A run stops on the bridge's end, so the check starts from the tile it is made on.
    world.stepStops += { player ->
        player.level == 0 && !player.scripts.delayed && player.z == CairnIsle.Z && (player.x == CairnIsle.WEST_END || player.x == CairnIsle.EAST_END)
    }
}

private fun stepped(world: World, player: Player, trail: List<Tile>) {
    if (player.level != 0 || player.scripts.delayed) return
    // The step onto an end of the bridge from off it: onto the east end from the promontory, or the west end from Cairn Isle.
    val target = trail.zipWithNext().firstNotNullOfOrNull { (from, to) ->
        when {
            from.z != CairnIsle.Z || to.z != CairnIsle.Z -> null
            from.x > CairnIsle.EAST_END && to.x == CairnIsle.EAST_END -> CairnIsle.WEST_END - 1
            from.x < CairnIsle.WEST_END && to.x == CairnIsle.WEST_END -> CairnIsle.EAST_END + 1
            else -> null
        }
    } ?: return
    world.scripts.start(player) {
        player.waypoints.clear()
        player.forcedSteps.clear()
        val success = SuccessRoll.roll(world.random, CairnIsle.SUCCESS.first, CairnIsle.SUCCESS.second, player.skills.currentLevel(Skill.AGILITY))
        val middle = (CairnIsle.WEST_END + CairnIsle.EAST_END) / 2
        val end = if (success) target else middle
        player.forcedSteps.addAll(Agility.steps(Tile(player.x, player.z), listOf(Tile(end, CairnIsle.Z))))
        while (player.forcedSteps.isNotEmpty()) delay(1)
        if (success) {
            player.skills.addTenths(Skill.AGILITY, CairnIsle.XP_TENTHS)
            player.message(world.textFor(player)[CairnIsleLines.BALANCE])
            return@start
        }
        val hit = CairnIsle.fallHit(player.skills.currentLevel(Skill.HITPOINTS))
        repeat(CairnIsle.FALL_HITS) { world.meleeCombat.statusHit(player, hit, HitsplatType.DAMAGE_ME, HitsplatType.DAMAGE_OTHER) }
        player.message(world.textFor(player)[CairnIsleLines.FELL])
        delay(1)
        world.teleport(player, CairnIsle.SHORE.x, CairnIsle.SHORE.z, 0)
    }
}
