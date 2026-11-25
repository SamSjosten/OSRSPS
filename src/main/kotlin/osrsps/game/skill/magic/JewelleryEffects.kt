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
import osrsps.game.script.ScriptContext

/** What the jewellery that saves its wearer says; its words are on the server (data/dialogue.txt), not in git. */
object JewelleryEffectLines : Lines("jewellery_effect", "Ring of life") {
    val RING_OF_LIFE = line("ring_of_life", "a ring of life teleporting its wearer away (Ring of life)")
    val PHOENIX = line("phoenix", "a phoenix necklace healing its wearer; words guessed")
    val FAITH = line("faith", "a necklace of faith restoring its wearer's prayer; words guessed")
    val CHECK = line("check", "Check on charged jewellery: the uses left before it crumbles; words guessed", "item", "count")
    val CRUMBLED = line("crumbled", "charged jewellery whose last use was just used; words guessed", "item")
    val BREAK_TITLE = line("break_title", "the question over Break's two options; words guessed", "item")
    val BREAK_YES = line("break_yes", "Break's option that breaks it; words guessed")
    val BREAK_NO = line("break_no", "Break's option that keeps it; words guessed")
    val BROKEN = line("broken", "after Break: the next one starts full; words guessed", "item")
    val EXPEDITIOUS = line("expeditious", "an expeditious bracelet counting a kill twice (Expeditious bracelet)")
    val SLAUGHTER = line("slaughter", "a bracelet of slaughter keeping a kill off the task count; words guessed")
    val CHEMISTRY = line("chemistry", "an amulet of chemistry making a 4-dose potion; words guessed")
    val BOUNTY = line("bounty", "an amulet of bounty saving seeds; words guessed")
}

/**
 * Jewellery whose uses are the player's, not the piece's (the Wiki's "charges are stored per player"): [obj] worn in
 * [wearpos] has [uses] before it crumbles, counted under [charges] in [osrsps.game.Charges] as the uses spent.
 */
class ChargedJewel(val key: String, val obj: Int, val wearpos: Int, val uses: Int) {
    val charges: String get() = "jewel_$key"
}

/**
 * The enchanted jewellery that saves its wearer from a hit they live through (Magic utility step U6a), each used up as
 * it works: the phoenix necklace, the necklace of faith and the ring of life; and the charged jewellery of skilling and
 * Slayer (step U6b, [ChargedJewel]).
 *
 * U6b, verified on the OSRS Wiki (Ring of forging, Bracelet of clay, Amulet of chemistry, Amulet of bounty, Expeditious
 * bracelet, Bracelet of slaughter, read 2026-11-19): the uses are the player's, shared by every piece of the kind, and
 * the last crumbles the worn piece, the next starting full; Break destroys a piece and fills the next. The **ring of
 * forging** (140 uses) makes every iron ore smelted at a furnace a bar, a use each; the **bracelet of clay** (28) mines
 * soft clay from clay rocks; the **amulet of chemistry** (5) makes a 3-dose potion a 4-dose one 5% of the time, no
 * extra XP; the **amulet of bounty** (10) plants an allotment with one seed instead of three 25% of the time, three
 * still being needed; the **expeditious bracelet** (30) counts a Slayer task kill as two 25% of the time, no extra XP;
 * the **bracelet of slaughter** (30) counts one as none 25% of the time, the XP still given. The cache gives each its
 * Check (worn, and for some in the inventory) and Break. The expeditious bracelet's message is the Wiki's.
 *
 * U6b, guessed: every other message and the Break question; the chemistry roll only on the potions of a secondary on an
 * unfinished potion whose 3-dose form is made (not the mixed ones, such as stamina by the dose), its use spent even when a full backpack then stops the potion; the
 * soft clay's mining message naming it; a superior's kill counted by the bracelets as any other. Not built: the amulet
 * of chemistry's Options (keep making or stop when it crumbles), the alchemist's amulet, Varrock armour's second bar,
 * the Trahaearn mine's double soft clay, the Combat Achievements' regenerating bracelets, and the inoculation bracelet
 * (there is no disease).
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
    private fun charged(key: String, gameval: String, wearpos: Int, uses: Int) = world.objTypes.find(gameval)?.let { ChargedJewel(key, it.id, wearpos, uses) }

    val forging = charged("forging", "ring_of_forging", WearPos.RING, 140)
    val clay = charged("clay", "jewl_bracelet_of_clay", WearPos.HANDS, 28)
    val chemistry = charged("chemistry", "amulet_of_chemistry", WearPos.NECK, 5)
    val bounty = charged("bounty", "amulet_of_bounty", WearPos.NECK, 10)
    val expeditious = charged("expeditious", "expeditious_bracelet", WearPos.HANDS, 30)
    val slaughter = charged("slaughter", "bracelet_of_slaughter", WearPos.HANDS, 30)
    private val allCharged = listOfNotNull(forging, clay, chemistry, bounty, expeditious, slaughter)

    fun register() {
        for (jewel in allCharged) {
            val type = world.objTypes[jewel.obj] ?: continue
            if (type.stringParam(osrsps.game.EquipmentTab.PARAM_WORN_OP1) == CHECK) world.handlers.onWorn(jewel.obj, 1) { check(jewel) }
            for ((index, option) in type.iops.withIndex()) {
                when (option) {
                    CHECK -> world.handlers.onObj(jewel.obj, index + 1) { check(jewel) }
                    BREAK -> world.handlers.onObj(jewel.obj, index + 1) { op -> breakJewel(jewel, op.sub) }
                }
            }
        }
    }

    private fun ScriptContext.check(jewel: ChargedJewel) {
        player.message(world.textFor(player)[JewelleryEffectLines.CHECK, "item" to name(jewel), "count" to jewel.uses - player.charges[jewel.charges]])
    }

    private suspend fun ScriptContext.breakJewel(jewel: ChargedJewel, slot: Int) {
        if (player.inventory[slot]?.id != jewel.obj) return
        var yes = false
        world.dialogues.start(this) {
            yes = choice(JewelleryEffectLines.BREAK_YES, JewelleryEffectLines.BREAK_NO, title = text(JewelleryEffectLines.BREAK_TITLE, "item" to name(jewel))) == 1
        }
        world.interfaces.closeChatModal(player)
        if (!yes || player.inventory[slot]?.id != jewel.obj) return
        player.inventory.remove(slot, 1)
        player.charges[jewel.charges] = 0
        player.message(world.textFor(player)[JewelleryEffectLines.BROKEN, "item" to name(jewel)])
    }

    private fun name(jewel: ChargedJewel): String = world.objTypes[jewel.obj]?.name?.lowercase() ?: jewel.key

    /** Whether [player] wears [jewel]. */
    fun wears(player: Player, jewel: ChargedJewel?): Boolean = jewel != null && player.equipment[jewel.wearpos]?.id == jewel.obj

    /**
     * Spends one of [player]'s uses of [jewel], if they wear it: true when it worked. The last use crumbles the piece and
     * the next one starts full.
     */
    fun use(player: Player, jewel: ChargedJewel?): Boolean {
        if (jewel == null || !wears(player, jewel)) return false
        val spent = player.charges[jewel.charges] + 1
        if (spent >= jewel.uses) {
            player.charges[jewel.charges] = 0
            player.equipment[jewel.wearpos] = null
            player.message(world.textFor(player)[JewelleryEffectLines.CRUMBLED, "item" to name(jewel)])
        } else {
            player.charges[jewel.charges] = spent
        }
        return true
    }

    /** A worn [jewel]'s chance of [numerator] in [denominator] working, spending a use when it does. */
    fun roll(player: Player, jewel: ChargedJewel?, numerator: Int, denominator: Int): Boolean =
        wears(player, jewel) && world.random.chance(numerator, denominator) && use(player, jewel)

    /** Ore mined from a clay rock: soft clay with a bracelet of clay, a use spent. */
    fun minedOre(player: Player, ore: Int): Int = if (ore == CLAY && use(player, clay)) SOFT_CLAY else ore

    /** A Slayer task kill's count: 2 for an expeditious bracelet's quarter, 0 for a bracelet of slaughter's, else 1. */
    fun taskKills(player: Player): Int = when {
        roll(player, expeditious, 1, 4) -> 2.also { player.message(world.textFor(player)[JewelleryEffectLines.EXPEDITIOUS]) }
        roll(player, slaughter, 1, 4) -> 0.also { player.message(world.textFor(player)[JewelleryEffectLines.SLAUGHTER]) }
        else -> 1
    }

    /** The potion a Herblore make gives: a 3-dose [product]'s 4-dose with an amulet of chemistry's 5%, else [product]. */
    fun potion(player: Player, product: Int): Int {
        val (family, index) = world.decanting.families[product] ?: return product
        if (index != 2 || family.doses[2] != product || !roll(player, chemistry, 1, 20)) return product
        player.message(world.textFor(player)[JewelleryEffectLines.CHEMISTRY])
        return family.doses[3]
    }

    /** The seeds an allotment takes of [seeds]: 1 of 3 with an amulet of bounty's quarter. */
    fun allotmentSeeds(player: Player, seeds: Int): Int {
        if (seeds != 3 || !roll(player, bounty, 1, 4)) return seeds
        player.message(world.textFor(player)[JewelleryEffectLines.BOUNTY])
        return 1
    }

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
        const val CHECK = "Check"
        const val BREAK = "Break"
        const val CLAY = 434
        const val SOFT_CLAY = 1761

        /** The deepest Wilderness level the ring of life works at (the Wiki's dragonstone-like exception). */
        const val RING_OF_LIFE_WILDERNESS = 30

        /** Lumbridge, the respawn point: within 2 of the Wiki's tile (Ring of returning's Lumbridge). */
        val RESPAWN = Landing(0, listOf(intArrayOf(3219, 3217, 3223, 3221)))
    }
}
