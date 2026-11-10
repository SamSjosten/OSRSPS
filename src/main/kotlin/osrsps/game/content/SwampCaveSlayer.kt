package osrsps.game.content

import osrsps.game.Player
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.CombatLines
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc

/** The rockslugs' and wall beasts' words, in the dialogue file. */
object SwampCaveSlayerLines : Lines("swamp_slayer", "OSRS Wiki, Rockslug, Bag of salt and Wall beast, read 2026-11-10") {
    val SALT_KILLS = line("salt_kills", "a bag of salt used on a rockslug at 4 hitpoints or less: it shrivels up and dies; words unsourced")
    val SALT_WASTED = line("salt_wasted", "a bag of salt used on a rockslug at 5 or more: the bag is used up for nothing; words unsourced")
    val HELMET_REPELS = line("helmet_repels", "a wall beast's grab repelled by a spiny or slayer helmet (the Wall beast page's message)")
    val GRABBED = line("grabbed", "a wall beast grabbing a player with neither helmet; words unsourced")
}

/**
 * The Lumbridge Swamp Caves' rockslugs (Misthalin piece 7 step 3b), from the OSRS Wiki's Rockslug and Bag of salt pages:
 * no hit takes one below 1 hitpoint ([osrsps.game.combat.NpcCombatDef.minHitpoints]), and down there it stops fighting
 * and wanders off. A bag of salt used on one at 4 or less finishes it; on one at 5 or more the bag is used up for
 * nothing. A brine sabre wielded kills them outright ([osrsps.game.combat.Combat.finishers]).
 *
 * Guessed: the messages; no animation for the salting; no XP for the salt's kill. Out: the Slug salter perk (Slayer
 * rewards aren't built).
 */
object Rockslugs {
    val SLUGS = listOf(421, 422)
    const val BAG_OF_SALT = 4161
    const val BRINE_SABRE = 11037
    /** The most hitpoints a bag of salt finishes (the Wiki: "4 or less"). */
    const val SALT_AT = 4
}

fun registerRockslugs(world: World) {
    for (slug in Rockslugs.SLUGS) {
        world.combat.finishers[slug] = { player -> player.equipment[WearPos.WEAPON]?.id == Rockslugs.BRINE_SABRE }
        world.handlers.onObjOnNpc(slug, Rockslugs.BAG_OF_SALT) { op ->
            val npc = op.npc ?: return@onObjOnNpc
            if (npc.dead || world.npcs[npc.index] !== npc) return@onObjOnNpc
            if (world.combat.slayerGated(player, npc)) {
                world.message(player, CombatLines.SLAYER_LEVEL)
                return@onObjOnNpc
            }
            val slot = op.sub.takeIf { it >= 0 && player.inventory[it]?.id == Rockslugs.BAG_OF_SALT } ?: return@onObjOnNpc
            player.inventory.remove(slot, 1)
            if (npc.hitpoints <= Rockslugs.SALT_AT) {
                world.message(player, SwampCaveSlayerLines.SALT_KILLS)
                world.meleeCombat.hit(npc, player, npc.hitpoints, world.tick, finishing = true)
            } else {
                world.message(player, SwampCaveSlayerLines.SALT_WASTED)
            }
        }
    }
}

/**
 * The Lumbridge Swamp Caves' wall beasts (Misthalin piece 7 step 3b), from the OSRS Wiki's Wall beast page. Each sits in
 * a hole in the wall ([HOLE], no options, never moving) and reaches for whoever steps onto the tile in front of it, the
 * one south of the hole for all seven (the 242 map: that is the only cave tile beside each). A player wearing a spiny
 * helmet or any slayer helmet repels it ("Your helmet repels the wall beast!") and it comes out as the attackable
 * [BEAST] and fights them; anyone else is grabbed: held for [GRAB_TICKS] and hurt for up to [GRAB_MAX_HIT] (the Wiki's
 * 18 without a spiny helmet) without a chance to fight back. Hurting one needs Slayer 35 (npc-combat.json). Out of a
 * fight for [BACK_TICKS], a beast goes back into its hole.
 *
 * Guessed: the grab's length, its even roll from 0 to 18 and its message; one grab each time the tile is stepped onto,
 * not again while the player stays; the animations used (the cache's wall_beast_specialattack_* and
 * human_wall_beast_specialattack_*); the ticks before it goes back in.
 */
class WallBeasts(private val world: World) {
    companion object {
        const val HOLE = 475
        const val BEAST = 476
        const val GRAB_TICKS = 4
        const val GRAB_MAX_HIT = 18
        const val BACK_TICKS = 20
        const val ANIM_GRAB = 1802
        const val ANIM_REPELLED = 1805
        const val ANIM_PLAYER_GRABBED = 1809
        const val SPINY_HELMET = 4551
    }

    /** By hole, the player it last reached for, until they step off its tile. */
    private val reached = HashMap<Npc, Player>()

    /** Whether [player] wears a helmet that repels a wall beast: a spiny helmet, or any slayer helmet by its name. */
    fun helmeted(player: Player): Boolean {
        val worn = player.equipment[WearPos.HEAD] ?: return false
        if (worn.id == SPINY_HELMET) return true
        val name = world.objTypes[worn.id]?.name?.lowercase() ?: return false
        return name.endsWith("slayer helmet") || name.endsWith("slayer helmet (i)")
    }

    /** The hole whose front tile [player] stands on, if one is there and in it. */
    private fun holeFacing(player: Player): Npc? {
        if (player.level != 0) return null
        return world.npcs.near(player.x, player.z + 1, 0, 0).firstOrNull {
            it.spawn.type.id == HOLE && it.x == player.x && it.z == player.z + 1 && !it.dead
        }
    }

    /** One tick for [player], after their step: a hole in front of them reaches for them. */
    fun process(player: Player) {
        reached.entries.removeIf { (hole, who) -> who === player && holeFacing(player) !== hole }
        if (player.dead || player.loggedOut) return
        val hole = holeFacing(player) ?: return
        if (hole.transformedTo != null || reached[hole] === player) return
        reached[hole] = player
        if (helmeted(player)) {
            world.message(player, SwampCaveSlayerLines.HELMET_REPELS)
            val beast = world.npcTypes[BEAST] ?: return
            world.npcs.transform(hole, beast, BACK_TICKS)
            hole.effects.anim(ANIM_REPELLED)
            world.meleeCombat.aggress(hole, player, world.tick)
        } else {
            hole.effects.anim(ANIM_GRAB)
            player.anim(ANIM_PLAYER_GRABBED)
            world.message(player, SwampCaveSlayerLines.GRABBED)
            world.statusEffects.stun(player, GRAB_TICKS, world.random.nextInt(GRAB_MAX_HIT + 1), spotanim = -1)
        }
    }

    /** Once a tick: a beast still fighting, or hit lately, stays out of its hole. */
    fun keepFighting() {
        for (npc in world.npcs.all) {
            if (npc.spawn.type.id != HOLE || npc.transformedTo?.id != BEAST || npc.dead) continue
            if (npc.target != null || world.tick - npc.attackedTick < BACK_TICKS) npc.transformTicks = BACK_TICKS
        }
    }

    /** Forgets [player] as they leave the world. */
    fun forget(player: Player) {
        reached.entries.removeIf { it.value === player }
    }

    internal fun reaching(hole: Npc): Player? = reached[hole]
}
