package osrsps.game.content

import osrsps.game.Player
import osrsps.game.Wilderness
import osrsps.game.combat.Spellbook
import osrsps.game.combat.Spells
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.script.Handlers
import osrsps.game.script.QueueType
import osrsps.game.script.ScriptContext

/** What Home Teleport says when it can't be cast. The Wiki pages don't quote either message, so the words need finding. */
object HomeTeleportLines : Lines("home_teleport", "Lumbridge_Home_Teleport") {
    val COOLDOWN = line("cooldown", "Cast within 30 minutes of the last home teleport: the minutes left (the page's Cooldown note)", "minutes")
    val IN_COMBAT = line("in_combat", "Cast while in combat (the page says it can't be)")
    val WILDERNESS = line("wilderness", "Cast above level 20 Wilderness: the line every teleport shows there (OSRS Wiki, Wilderness level: teleports don't work above level 20)")
}

/**
 * Lumbridge Home Teleport, the standard spellbook's first spell (`magic_spellbook:teleport_home_standard`, 218:6 in the
 * 241 cache, op1 Cast). Free, no Magic level, no XP. Verified on the OSRS Wiki (Lumbridge Home Teleport, Home Teleport,
 * Teleport override; read 2026-11-01): a 30-minute cooldown shared by every home teleport, the default animation lasts
 * 24 ticks, it can't be cast in combat or for 10 seconds after (as logging out, so the logout's [inCombatTicks]), and
 * combat interrupts it. Verified in RuneLite (TimersAndBuffs): the cooldown is varp 892 (`aide_tele_timer`), the
 * minute (since 1970) of the last home teleport. Verified in the 241 cache: the five stages' animations and graphics
 * ([STAGES]; the `aide_*` seqs and spotanims, from the Lumbridge Guide's old "aide" teleport).
 *
 * Guessed: the tick each stage starts on (the Wiki gives only the 24 ticks in all), landing on the castle courtyard's
 * spawn tile, the cooldown starting when the player lands (so a cast that is cut short costs nothing), the remaining
 * minutes rounded down from the varp's whole minutes, and a second click while casting doing nothing. Above level 20
 * Wilderness the cast is refused, as every teleport's is (OSRS Wiki, Wilderness level; see [Wilderness]).
 *
 * Any click that interrupts the player (walking, an NPC, a loc, closing an interface) removes the weak queue, which
 * stops the cast; being attacked stops it at the next tick.
 *
 * Two seams for other teleports to swap in from [osrsps.game.World]'s init, without touching this file: [destination]
 * (null cancels the cast; a lodestone menu could open here) and [cooldownApplies]. Teleports of the same family
 * (the lodestones', [Lodestones]) start their own cast with [castTo], with their own timing and no cooldown.
 */
class HomeTeleport(
    private val currentTick: () -> Long,
    /** Minutes since 1970, as varp 892 holds them. */
    private val nowMinutes: () -> Long,
    /** Whether the player was attacked too lately to cast. */
    private val inCombat: (Player) -> Boolean,
    private val teleport: (Player, Destination) -> Unit,
    private val message: (Player, Line, Array<out Pair<String, Any>>) -> Unit,
    /** The Wilderness level the player stands at; 0 outside it. */
    private val wildernessLevel: (Player) -> Int = { Wilderness.level(it.x, it.z) },
) {
    /** Where the cast will land, chosen when the player clicks Cast, before the animation starts. Null cancels it. */
    var destination: suspend ScriptContext.() -> Destination? = { LUMBRIDGE }

    /** Whether the 30-minute cooldown applies to this cast. */
    var cooldownApplies: (Player) -> Boolean = { true }

    fun register(handlers: Handlers) {
        handlers.onButton(Spells.SPELLBOOK, COMPONENT, CAST) { cast() }
    }

    /** Whole minutes left before [player] can cast again, 0 when they can. */
    fun minutesLeft(player: Player): Int {
        val last = player.vars[VARP_LAST_CAST]
        if (last <= 0) return 0
        val since = nowMinutes() - last
        return if (since in 0 until COOLDOWN_MINUTES) (COOLDOWN_MINUTES - since).toInt() else 0
    }

    /** Whether [player] is part way through a cast. */
    fun casting(player: Player): Boolean = player.scripts.weakQueue.any { it.name == QUEUE }

    private suspend fun ScriptContext.cast() {
        // Only the standard book's home teleport is built; the button is the standard book's own.
        if (Spellbook.of(player) != Spellbook.STANDARD) return
        if (casting(player)) return
        if (!canCast(player, cooldownApplies(player))) return
        val to = destination() ?: return
        castTo(to)
    }

    /** Whether [player] can start a cast now, saying why not when they can't; [cooldown] is whether theirs applies. */
    private fun canCast(player: Player, cooldown: Boolean): Boolean {
        if (inCombat(player)) {
            message(player, HomeTeleportLines.IN_COMBAT, emptyArray())
            return false
        }
        if (wildernessLevel(player) > Wilderness.TELEPORT_LIMIT) {
            message(player, HomeTeleportLines.WILDERNESS, emptyArray())
            return false
        }
        if (cooldown) {
            val left = minutesLeft(player)
            if (left > 0) {
                message(player, HomeTeleportLines.COOLDOWN, arrayOf("minutes" to left))
                return false
            }
        }
        return true
    }

    /**
     * Starts a cast that lands on [to] after [ticks] ticks, playing [stages] on the way, and records the cooldown at
     * the landing when [cooldown]. Refused, with the reason said, part way through another cast, in combat, above
     * level 20 Wilderness, or within the cooldown when it applies. Returns whether the cast started.
     */
    fun ScriptContext.castTo(to: Destination, ticks: Int = CAST_TICKS, stages: List<Stage> = STAGES, cooldown: Boolean = cooldownApplies(player)): Boolean {
        if (casting(player)) return false
        if (!canCast(player, cooldown)) return false
        // Casting stops whatever the player was doing and keeps them where they stand (inferred: the stages are
        // animations on the spot); a queued activity (milking, making) would otherwise carry on beside the cast.
        player.scripts.weakQueue.clear()
        player.waypoints.clear()
        player.interaction = null
        step(Cast(to, ticks, stages, cooldown), currentTick(), 0)
        return true
    }

    private class Cast(val to: Destination, val ticks: Int, val stages: List<Stage>, val cooldown: Boolean)

    /** Stage [elapsed] ticks into [cast], started on tick [started]; queues the next tick's. */
    private fun ScriptContext.step(cast: Cast, started: Long, elapsed: Int) {
        if (player.attackedTick >= started) return
        if (elapsed >= cast.ticks) {
            teleport(player, cast.to)
            player.anim(-1)
            if (cast.cooldown) player.vars[VARP_LAST_CAST] = nowMinutes().toInt()
            return
        }
        cast.stages.firstOrNull { it.tick == elapsed }?.let { stage ->
            player.anim(stage.anim)
            player.spotanim(stage.spotanim)
        }
        queue(QUEUE, QueueType.WEAK, delay = 1) { step(cast, started, elapsed + 1) }
    }

    /** One stage of the cast: from [tick] ticks in, animation [anim] with graphic [spotanim] on the player. */
    data class Stage(val tick: Int, val anim: Int, val spotanim: Int)

    companion object {
        /** `magic_spellbook:teleport_home_standard`, and its op1, Cast. */
        const val COMPONENT = 6
        const val CAST = 1

        /** `aide_tele_timer`: the minute of the last home teleport, which RuneLite's home teleport timer reads. */
        const val VARP_LAST_CAST = 892

        const val COOLDOWN_MINUTES = 30L

        /** From the click to landing (OSRS Wiki, Teleport override: "The default home teleport animation lasts 24 ticks"). */
        const val CAST_TICKS = 24

        const val QUEUE = "home_teleport"

        /** The Lumbridge Castle courtyard spawn (guessed as the landing tile). */
        val LUMBRIDGE = Destination(3222, 3218, 0)

        /**
         * The seqs and spotanims by their 241 cache names: drawing the chalk circle (4847, 800), sitting down in it
         * (4850, the circle left on the ground, 801), getting the book (4853, 802), the incantation (4855, the portal,
         * 803) and teleporting (4857, 804). The start ticks are guessed.
         */
        val STAGES = listOf(
            Stage(0, 4847, 800),
            Stage(7, 4850, 801),
            Stage(13, 4853, 802),
            Stage(16, 4855, 803),
            Stage(19, 4857, 804),
        )
    }
}
