package osrsps.game.skill.magic

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.content.EdgevilleRespawn
import osrsps.game.combat.MeleeCombat
import osrsps.game.combat.Teleblock
import osrsps.game.dialogue.Lines

/** What the jewellery that saves its wearer says; its words are on the server (data/dialogue.txt), not in git. */
object JewelleryEffectLines : Lines("jewellery_effect", "Ring of life") {
    val RING_OF_LIFE = line("ring_of_life", "a ring of life teleporting its wearer away (Ring of life)")
    val PHOENIX = line("phoenix", "a phoenix necklace healing its wearer; words guessed")
    val FAITH = line("faith", "a necklace of faith restoring its wearer's prayer; words guessed")
}

/**
 * The enchanted jewellery that saves its wearer from a hit they live through (Magic utility step U6a), each used up as
 * it works: the phoenix necklace, the necklace of faith and the ring of life.
 *
 * Verified on the OSRS Wiki (Phoenix necklace, Necklace of faith, Ring of life, read 2026-11-19):
 * - The **phoenix necklace** heals 30% of the Hitpoints level, rounded down, when a hit leaves its wearer at 20% of it
 *   or below, and the attacks on their way to them land nowhere; it goes before the ring of life and before Redemption.
 * - The **necklace of faith** restores 25% of the Prayer level when a hit leaves its wearer below 20% of their
 *   Hitpoints, before Redemption.
 * - The **ring of life** teleports its wearer to their respawn point when a hit leaves them at 10% of their Hitpoints
 *   or below, with its message; it works up to level 30 Wilderness and not while Tele Blocked; it doesn't cure poison.
 * - A hit from full to nothing kills, whatever is worn.
 *
 * Guessed: the percentages of the Hitpoints and Prayer levels are of the base levels; the necklace of faith restores up
 * to the Prayer level and not past it; the phoenix necklace's and necklace of faith's messages; a poison or venom hit
 * and another player's hit count as a hit (the Wiki's self-inflicted damage, the dwarven rock cake, isn't built to set
 * these off); the ring of life lands at once, with no animation, at the player's respawn point (Krystilia's Edgeville
 * one when they have it switched on, [EdgevilleRespawn], else within 2 of the Wiki's Lumbridge respawn tile, as the
 * ring of returning lands), and does nothing inside an instance
 * (a raid or the TzHaar caves, whose deaths have their own rules). Not built here: the Ardougne respawn the medium
 * Ardougne Diary unlocks, the Defence cape, the Escape crystal, Wintertodt's warmth, and the duel arena's and Tombs of
 * Amascut's food rules.
 */
class JewelleryEffects(private val world: World) {
    private val ringOfLife = world.objTypes.find("ring_of_life")?.id ?: -1
    private val phoenix = world.objTypes.find("jewl_necklace_of_phoenix")?.id ?: -1
    private val faith = world.objTypes.find("necklace_of_faith")?.id ?: -1

    /**
     * [player] lived through a hit: a phoenix necklace or necklace of faith works if they are low enough, then a ring of
     * life if they still are. Says what happened that combat cares about: a phoenix necklace's heal, or the ring's escape.
     */
    fun hurt(player: Player): MeleeCombat.Rescue {
        val max = player.skills.baseLevel(Skill.HITPOINTS)
        val neck = player.equipment[WearPos.NECK]?.id
        val text = world.textFor(player)
        var rescue = MeleeCombat.Rescue.NONE
        if (neck != null && neck == phoenix && player.hitpoints * 5 <= max) {
            player.equipment[WearPos.NECK] = null
            player.hitpoints = minOf(max, player.hitpoints + max * 3 / 10)
            player.message(text[JewelleryEffectLines.PHOENIX])
            rescue = MeleeCombat.Rescue.HEALED
        } else if (neck != null && neck == faith && player.hitpoints * 5 < max) {
            player.equipment[WearPos.NECK] = null
            val prayer = player.skills.baseLevel(Skill.PRAYER)
            val current = player.skills.currentLevel(Skill.PRAYER)
            val restored = minOf(prayer, current + prayer / 4)
            if (restored > current) player.skills.setCurrentLevel(Skill.PRAYER, restored)
            player.message(text[JewelleryEffectLines.FAITH])
        }
        if (player.equipment[WearPos.RING]?.id == ringOfLife && ringOfLife >= 0 && player.hitpoints * 10 <= max && canEscape(player)) {
            player.equipment[WearPos.RING] = null
            player.message(text[JewelleryEffectLines.RING_OF_LIFE])
            world.interfaces.closeModals(player)
            player.waypoints.clear()
            player.interaction = null
            if (EdgevilleRespawn.active(world, player)) {
                world.teleport(player, EdgevilleRespawn.X, EdgevilleRespawn.Z, 0)
            } else {
                val (x, z) = RESPAWN.pick({ x, z -> world.canStand(x, z, RESPAWN.level) }) { world.random.nextInt(it) }
                world.teleport(player, x, z, RESPAWN.level)
            }
            rescue = MeleeCombat.Rescue.ESCAPED
        }
        return rescue
    }

    /** Whether the ring of life may take [player] away: not deeper than level 30 Wilderness, Tele Blocked or in an instance. */
    private fun canEscape(player: Player): Boolean =
        Teleblock.refusal(player, Wilderness.level(player.x, player.z), RING_OF_LIFE_WILDERNESS).isEmpty() &&
            !world.instances.inDynamicArea(player.x, player.z)

    companion object {
        /** The deepest Wilderness level the ring of life works at (the Wiki's dragonstone-like exception). */
        const val RING_OF_LIFE_WILDERNESS = 30

        /** Lumbridge, the respawn point: within 2 of the Wiki's tile (Ring of returning's Lumbridge). */
        val RESPAWN = Landing(0, listOf(intArrayOf(3219, 3217, 3223, 3221)))
    }
}
