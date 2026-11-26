package osrsps.game.trail

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.content.message
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

object HotColdLines : Lines("hotcold", "Treasure Trails hot-cold") {
    val CLUE_BEGINNER = line("clue_beginner", "a beginner hot-cold clue's words, on trail_cluetext (the Wiki's Hot Cold guide quotes them)")
    val CLUE_MASTER = line("clue_master", "a master hot-cold clue's words (the same, with Jorral for Reldo)")
    val FIRST = line("first", "Feel on a strange device, the first time on this step (RuneLite's hot-cold parser: \"The device is \" and the temperature)", "temperature")
    val AGAIN = line("again", "Feel again: the temperature and how it compares with last time (RuneLite's parser: \"..., and warmer than last time.\")", "temperature", "change")
    val WARMER = line("warmer", "the change after again: warmer than last time")
    val SAME = line("same", "the change after again: the same temperature as last time")
    val COLDER = line("colder", "the change after again: colder than last time")
    val ICE_COLD = line("ice_cold", "a temperature: 500 tiles or more from the spot")
    val VERY_COLD = line("very_cold", "a temperature: 200 to 499 tiles")
    val COLD = line("cold", "a temperature: 150 to 199 tiles")
    val WARM = line("warm", "a temperature: 100 to 149 tiles")
    val HOT = line("hot", "a temperature: 70 to 99 tiles")
    val VERY_HOT = line("very_hot", "a temperature: 30 to 69 tiles")
    val INCREDIBLY_HOT = line("incredibly_hot", "a temperature: 4 (master 5) to 29 tiles")
    val SHAKING = line("shaking", "a temperature: on the spot, 0 to 3 (master 4) tiles")
    val INACTIVE = line("inactive", "Feel with no hot-cold step of the device's tier under way (no source seen)")
    val GIVES_DEVICE = line("gives_device", "Reldo or Jorral, handing over the strange device to a player on a hot-cold step (no source seen)")
}

/**
 * Hot-cold steps (step TT8): a beginner or master clue with no words of its own but the Wiki's quoted lines, which
 * send the player to Reldo (beginner) or Jorral (master) for a strange device. Feel on the device says how near the dig
 * spot is; Dig near it solves the step, after a master step's Brassican Mage ([TrailFights]). Sources: the Wiki's
 * Strange device and Hot Cold guide pages and RuneLite's hot-cold solver (its temperatures, bands and messages), read
 * 2026-11-19.
 *
 * - Talk-to on the step's NPC (Reldo, or Jorral) with the clue and no device of the tier in the backpack or bank gives
 *   one; it needn't be given back.
 * - Feel: the Chebyshev distance to the spot (the Wiki: "the number of steps it would take ... ignoring any
 *   obstacles"), as a temperature band (RuneLite's HotColdTemperature), and from the second feel on the step, warmer,
 *   colder or the same as last time. The master device hits for 3 to 8 each feel (the Wiki).
 * - Dig within [DIG_RANGE] of the spot solves the step (the Wiki's Hot Cold guide: "a 9x9 square in which you can
 *   dig", both tiers).
 *
 * Guessed: another floor counts as ice cold; warmer and colder compare the distance, not the band; no animation on
 * Feel.
 */
class HotCold(private val world: World, private val trails: TreasureTrails) {
    /**
     * The step and distance each player last felt on each tier's device, for warmer and colder: a beginner and a master
     * trail can be under way together. Not saved, and weak, so a logged-out player's entry goes with it (Codex review
     * on #899).
     */
    private val last = java.util.WeakHashMap<Player, MutableMap<TrailTier, Pair<String, Int>>>()

    /** The hot-cold step [player] is on with [device] (its tier's), or null. */
    private fun stepFor(player: Player, device: Int): Pair<TrailTier, ClueStep>? {
        val tier = DEVICES.entries.firstOrNull { it.value == device }?.key ?: return null
        val step = player.trails[tier]?.let { trails.data[it.step] }?.takeIf { it.type == StepType.HOT_COLD } ?: return null
        return tier to step
    }

    fun feel(context: ScriptContext, slot: Int) {
        val player = context.player
        val device = player.inventory[slot]?.id ?: return
        val (tier, step) = stepFor(player, device) ?: return world.message(player, HotColdLines.INACTIVE)
        if (tier == TrailTier.MASTER) world.meleeCombat.hitPlayer(player, null, MASTER_MIN_HIT + world.random.nextInt(MASTER_MAX_HIT - MASTER_MIN_HIT + 1), world.tick)
        val spot = step.targets.first().tile
        val distance = if (spot.level != player.level) Int.MAX_VALUE else maxOf(kotlin.math.abs(spot.x - player.x), kotlin.math.abs(spot.z - player.z))
        val words = world.textFor(player)
        val temperature = words[band(tier, distance)]
        val before = last.getOrPut(player) { HashMap() }.put(tier, step.key to distance)?.takeIf { it.first == step.key }?.second
        if (before == null) {
            player.message(words[HotColdLines.FIRST, "temperature" to temperature])
            return
        }
        val change = when {
            distance < before -> HotColdLines.WARMER
            distance > before -> HotColdLines.COLDER
            else -> HotColdLines.SAME
        }
        player.message(words[HotColdLines.AGAIN, "temperature" to temperature, "change" to words[change]])
    }

    /** Talk-to on the step's NPC: the device, if the player has none of the tier's. */
    suspend fun giveDevice(context: ScriptContext, npc: osrsps.game.npc.Npc, tier: TrailTier) {
        val player = context.player
        val device = DEVICES[tier] ?: return
        if (player.inventory.count(device) > 0 || player.bank.count(device) > 0) return
        val type = world.objTypes[device] ?: return
        world.dialogues.start(context, npc) { chatNpc(osrsps.game.dialogue.ChatAnim.NEUTRAL, HotColdLines.GIVES_DEVICE) }
        if (player.inventory.add(type, 1) == 0) world.message(player, osrsps.game.GameLines.NO_SPACE)
    }

    companion object {
        /** The strange devices: beginner_device and master_device. */
        val DEVICES: Map<TrailTier, Int> = mapOf(TrailTier.BEGINNER to 23183, TrailTier.MASTER to 19939)

        /** Who hands them over: Reldo (the clue helper's reldo, and his two other library forms) and Jorral. */
        val GIVERS: Map<TrailTier, Set<Int>> = mapOf(TrailTier.BEGINNER to setOf(6203, 4242, 4243), TrailTier.MASTER to setOf(3490))

        const val MASTER_MIN_HIT = 3
        const val MASTER_MAX_HIT = 8

        /** How near the spot Dig counts: the Wiki's 9x9 square, 4 tiles either way, for both tiers. */
        const val DIG_RANGE = 4

        /** The edge of the hottest band, "visibly shaking" (RuneLite's HotColdTemperature): 3 tiles, master 4. */
        private fun shakingRange(tier: TrailTier): Int = if (tier == TrailTier.MASTER) 4 else 3

        fun band(tier: TrailTier, distance: Int) = when {
            distance >= 500 -> HotColdLines.ICE_COLD
            distance >= 200 -> HotColdLines.VERY_COLD
            distance >= 150 -> HotColdLines.COLD
            distance >= 100 -> HotColdLines.WARM
            distance >= 70 -> HotColdLines.HOT
            distance >= 30 -> HotColdLines.VERY_HOT
            distance > shakingRange(tier) -> HotColdLines.INCREDIBLY_HOT
            else -> HotColdLines.SHAKING
        }
    }
}
