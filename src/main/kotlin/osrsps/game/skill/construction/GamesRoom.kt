package osrsps.game.skill.construction

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.arriveDelay
import osrsps.game.content.message
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/**
 * Construction step CON6e (the games room): its furniture builds ([FurnitureBuilds]), and the hoop and stick is played,
 * from the Wiki's Games room and Hoop and stick pages (read 2026-11-27).
 *
 * Hoop and stick: "the player will make 10 attempts at landing the hoop on the stick", each landing by the player's
 * Ranged level and worth 2.5 Ranged XP ([hoop]). The page's Jagex chart gives about 29.7% at level 1 and 94.1% at 99:
 * 76 and 241 in 256, the usual skilling roll's ends ([landChance]). GUESSED: a throw every [THROW_TICKS]; the
 * human_throw_hoop1 animation for each throw; the score told at the end (the page's scoreboard is left out); the
 * words. Anyone in the house can play.
 *
 * Next: the attack stones, then the dartboard and archery target, the prize chest, and the game space's games and the
 * elemental balances.
 */
class GamesRoom(private val world: World, private val houses: Houses) {
    fun register() {
        world.handlers.onLoc(HOOP_AND_STICK, HOOP) { op ->
            arriveDelay()
            hoop(this, op.loc ?: return@onLoc)
        }
    }

    /** Hoop on the hoop and stick [loc]: ten throws, then the score. */
    private suspend fun hoop(context: ScriptContext, loc: ZoneLoc) {
        val player = context.player
        if (houses.houseAt(loc.x, loc.z) == null) return
        var landed = 0
        repeat(THROWS) {
            if (!world.locs.exists(loc) || houses.houseAt(loc.x, loc.z) == null) return
            player.anim(THROW_ANIM)
            context.delay(THROW_TICKS)
            if (world.random.nextInt(256) < landChance(player.skills.currentLevel(Skill.RANGED))) {
                landed++
                player.skills.addTenths(Skill.RANGED, LAND_XP_TENTHS)
            }
        }
        world.message(player, ConstructionLines.HOOP_SCORE, "score" to landed)
        logger.info { "${player.username} landed $landed of $THROWS hoops" }
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        /** Poh_stick+hoop1, as built, and its Hoop (op 1). */
        const val HOOP_AND_STICK = 13398
        const val HOOP = 1

        const val THROWS = 10

        /** 2.5 Ranged XP a landing (the Hoop and stick page). */
        const val LAND_XP_TENTHS = 25

        /** GUESSED: how long a throw takes. */
        const val THROW_TICKS = 3

        /** human_throw_hoop1 (GUESSED that it is the throw; there is a _hoop2). */
        const val THROW_ANIM = 3602

        /**
         * In 256, the chance a hoop lands at Ranged [level]: 76 at 1 and 241 at 99, in a straight line between (the Hoop
         * and stick page's chart: 29.7% and 94.1%).
         */
        fun landChance(level: Int): Int {
            val at = level.coerceIn(1, 99) - 1
            return 1 + (75 * (98 - at) + 240 * at) / 98
        }
    }
}
