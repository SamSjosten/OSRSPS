package osrsps.game.trail

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.ShearedRam

/**
 * Clue scrolls from skilling (step TT9): a successful catch, log or ore rolls once for each tier from beginner to elite
 * the player may still take (held below [TreasureTrails.cap]), each on its own, and a hit puts that tier's scroll box in
 * the backpack (X Marks the Spot is done, so no geode, nest or bottle comes first: the Wiki's Clue geode page, "After
 * completing the quest, Scroll boxes are found instead"). Arthur's toggle (varbit vesseled_clues_disabled) stops it.
 *
 * Sources: the Wiki's Clue geode, Clue nest and Clue bottle pages (read 2026-11-19): 1 in
 * floor(floor(base × [MINING_FACTOR for mining]) / (100 + level) × the tier's modifier), the level the visible one, the
 * base chances per rock, tree and fish, and the modifiers (Mod Ash: beginner 0.2, easy 1.7, medium 2, hard 3.3, elite 10).
 *
 * Guessed: no message with the box (none seen); a full backpack puts it on the player's tile; Arthur's toggle still
 * counts once boxes come in place of vessels. Out: Combat Achievements' 5% (not built), the ring of wealth (i) in the
 * Wilderness, bird houses, twitcher's gloves.
 */
class SkillingClues(private val world: World, private val trails: TreasureTrails) {
    /** Players (save keys) whose every roll hits (and every casket's master roll), set by `::clue lucky` for tests and runner scripts. Not saved. */
    val lucky = HashSet<String>()

    /** A successful [skill] action whose base chance is [base] (one of [MINING], [WOODCUTTING] or [FISHING]'s). */
    fun rolled(player: Player, skill: Skill, base: Int?) {
        base ?: return
        if (ShearedRam.vesselsDisabled(player)) return
        val level = player.skills.currentLevel(skill)
        val scaled = if (skill == Skill.MINING) (base * MINING_FACTOR).toInt() else base
        for (tier in TIERS) {
            val box = world.objTypes[tier.scrollBox] ?: continue
            if (trails.held(player, tier) >= trails.cap(player, tier)) continue
            if (player.saveKey !in lucky && world.random.nextInt(chance(scaled, level, tier)) != 0) continue
            world.groundObjs.give(player, box, 1)
        }
    }

    companion object {
        /** The tiers skilling gives, with the Wiki's modifiers on the chance's denominator. */
        val MODIFIERS: Map<TrailTier, Double> = mapOf(
            TrailTier.BEGINNER to 0.2, TrailTier.EASY to 1.7, TrailTier.MEDIUM to 2.0, TrailTier.HARD to 3.3, TrailTier.ELITE to 10.0,
        )
        private val TIERS = MODIFIERS.keys.toList()

        /** Mining's base chance is taken at 70% (the Wiki's formula, the rates raised by about 30% on 8 May 2024). */
        const val MINING_FACTOR = 0.7

        /** The 1-in chance for [tier] at [level], from a base already scaled for mining. */
        fun chance(base: Int, level: Int, tier: TrailTier): Int =
            maxOf(1, (base.toDouble() / (100 + level) * MODIFIERS.getValue(tier)).toInt())

        /** The Wiki's Clue geode base chances, by [osrsps.game.skill.RockType.key]. */
        val MINING: Map<String, Int> = mapOf(
            "clay" to 741_600, "copper" to 741_600, "tin" to 741_600, "iron" to 741_600, "silver" to 741_600,
            "limestone" to 741_600, "granite" to 741_600, "sandstone" to 741_600, "coal" to 290_640, "gold" to 296_640,
            "gem" to 211_886, "mithril" to 148_320, "adamantite" to 59_328, "runite" to 42_377, "amethyst" to 46_350,
        )

        /** The Wiki's Clue nest base chances, by [osrsps.game.skill.TreeType.key]. */
        val WOODCUTTING: Map<String, Int> = mapOf(
            "regular" to 317_647, "dead" to 317_647, "evergreen" to 317_647, "oak" to 361_146, "willow" to 289_286,
            "teak" to 264_336, "maple" to 221_918, "mahogany" to 220_623, "arctic_pine" to 145_758, "yew" to 145_013,
            "magic" to 72_321, "redwood" to 72_321,
        )

        /** The Wiki's Clue bottle base chances, by the raw fish's obj id. */
        val FISHING: Map<Int, Int> = mapOf(
            317 to 870_330, 321 to 870_330, 3150 to 443_697, 327 to 1_056_000, 345 to 1_056_000, 353 to 1_147_827,
            341 to 1_147_827, 363 to 1_147_827, 335 to 923_616, 331 to 923_616, 349 to 305_792, 359 to 257_770,
            371 to 257_770, 377 to 116_129, 5001 to 257_770, 7944 to 138_583, 3142 to 170_874, 383 to 82_243,
            13439 to 78_649, 11934 to 149_434,
        )
    }
}
