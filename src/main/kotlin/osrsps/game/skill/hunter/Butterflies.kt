package osrsps.game.skill.hunter

import osrsps.game.InvObj
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.content.message
import osrsps.game.npc.Npc
import osrsps.game.script.ScriptContext
import osrsps.game.skill.SuccessRoll

/** What releasing a butterfly or moth does (OSRS Wiki, Butterfly (Hunter)). */
sealed class ButterflyEffect {
    /** [skill] up by 15% of its base level, rounded down, plus 4, as a potion's boost. */
    class Boost(val skill: Skill) : ButterflyEffect()

    /** [amount] hitpoints back, up to the base level. */
    class Heal(val amount: Int) : ButterflyEffect()

    /** The sunlight moth: 20% + 6 of every lowered stat but Prayer back, and 8 hitpoints. */
    object Sunlight : ButterflyEffect()

    /** The moonlight moth: 22 prayer points back. */
    object Moonlight : ButterflyEffect()
}

/**
 * A butterfly or moth: NPC types [npcs], the Hunter [level] to catch it with a net (10 more barehanded), the XP in tenths,
 * the catch pairs for [SuccessRoll] with an insect net ([netPair]) and with a magic insect net ([magicPair]) and
 * barehanded ([barePair]), its jarred item [jarred], and what releasing it does.
 */
class Butterfly(
    val npcs: List<Int>,
    val level: Int,
    val xpTenths: Int,
    val netPair: Pair<Int, Int>,
    val magicPair: Pair<Int, Int>,
    val barePair: Pair<Int, Int>,
    val jarred: Int,
    val effect: ButterflyEffect,
)

/**
 * Butterflies and moths (Hunter step 6a): Catch on one, wielding an insect net (or barehanded at 10 levels over its
 * requirement, with nothing wielded); with a butterfly jar in the backpack the jar becomes the jarred butterfly, without
 * one it is let go at once and its effect applies. Release on a jarred one applies the effect and leaves the empty jar.
 * A caught butterfly goes and comes back a little later where it spawned.
 *
 * Sources: the OSRS Wiki's Butterfly (Hunter) page (read 2026-11-15) for the levels, XP, effects, barehanded at 10
 * levels over, and that a jar is needed to keep one; each creature's page for its catch pairs (black warlock 20 to 296,
 * 40 to 316 with the magic net or barehanded; sunlight moth 20 to 296, 40 to 316 with the magic net only; moonlight moth
 * 0 to 276, 20 to 286 with the magic net) and where it lives; the jarred items' pages for Release leaving the empty jar
 * and the boost formula; Snowy knight's page for a release sharing the potions' 3-tick delay. Ids from the rev 242
 * cache: the NPCs' Catch op, insect net 10010, magic insect net 11259, butterfly jar 10012, human_butterflynet_swing.
 *
 * Guessed: the ruby harvest, sapphire glacialis and snowy knight's pairs (the Wiki gives none): the black warlock's
 * spread, set so each is caught as often at its own level as a black warlock at 45 (ruby 105 to 381, sapphire 76 to
 * 352, snowy 48 to 324; 20 more each with the magic net or barehanded); barehanded meaning no weapon wielded, with
 * human_catch; one attempt per Catch, judged the tick after the swing, the player held [CATCH_TICKS]; the respawn
 * ([RESPAWN_TICKS]); every release, not only the snowy knight's, sharing the potions' delay; moonlight moths all the
 * catchable type (12771). Left out: hunter's mixes, using a jar on another player in multicombat, the butterfly wings
 * (Hunters' Rumours), Puro-Puro, the inaccessible Crypt of Tonali spawns.
 */
class Butterflies(private val world: World) {
    private class Respawn(val npc: Npc, val tick: Long)

    private val respawns = ArrayList<Respawn>()

    fun register() {
        for (butterfly in ALL) {
            for (npc in butterfly.npcs) world.handlers.onNpc(npc, CATCH) { op -> catch(butterfly, op.npc ?: return@onNpc) }
            world.handlers.onObj(butterfly.jarred, RELEASE) { op -> release(butterfly, op.sub) }
        }
    }

    /** Call once a tick: brings caught butterflies back. */
    fun process() {
        val tick = world.tick
        respawns.removeAll { respawn ->
            if (respawn.tick > tick) return@removeAll false
            val npc = respawn.npc
            if (npc.hidden) {
                world.npcs.teleport(npc, npc.spawn.x, npc.spawn.z)
                world.show(npc)
            }
            true
        }
    }

    private suspend fun ScriptContext.catch(butterfly: Butterfly, npc: Npc) {
        if (npc.hidden) return
        val hunter = player.skills.currentLevel(Skill.HUNTER)
        val weapon = player.equipment[WearPos.WEAPON]?.id
        val pair = when {
            weapon == MAGIC_INSECT_NET -> if (hunter >= butterfly.level) butterfly.magicPair else null
            weapon == INSECT_NET -> if (hunter >= butterfly.level) butterfly.netPair else null
            weapon == null && hunter >= butterfly.level + BAREHANDED_LEVELS -> butterfly.barePair
            weapon == null -> return world.message(player, HunterLines.NEED_NET)
            else -> return world.message(player, HunterLines.NEED_NET)
        } ?: return world.message(player, HunterLines.CATCH_LEVEL, "level" to butterfly.level)
        player.anim(if (weapon == null) BAREHANDED_ANIM else NET_ANIM)
        player.actionDelay = tick + CATCH_TICKS
        delay(1)
        if (npc.hidden || npc.dead) return
        if (!SuccessRoll.roll(world.random, pair.first, pair.second, hunter)) return world.message(player, HunterLines.CATCH_MISS)
        world.hide(npc)
        respawns += Respawn(npc, world.tick + RESPAWN_TICKS)
        player.skills.addTenths(Skill.HUNTER, butterfly.xpTenths)
        val name = world.npcTypes[npc.type.id]?.name?.lowercase() ?: "butterfly"
        val jar = player.inventory.contents().firstOrNull { it.second.id == BUTTERFLY_JAR }?.first
        if (jar != null) {
            player.inventory[jar] = InvObj(butterfly.jarred, 1)
            world.message(player, HunterLines.CAUGHT, "creature" to name)
        } else {
            apply(player, butterfly.effect)
            world.message(player, HunterLines.CAUGHT_RELEASED, "creature" to name)
        }
    }

    private fun ScriptContext.release(butterfly: Butterfly, slot: Int) {
        if (player.inventory[slot]?.id != butterfly.jarred || world.tick < player.potionDelayUntil) return
        player.potionDelayUntil = world.tick + RELEASE_DELAY
        player.inventory[slot] = InvObj(BUTTERFLY_JAR, 1)
        apply(player, butterfly.effect)
        world.message(player, HunterLines.RELEASED)
    }

    private fun apply(player: Player, effect: ButterflyEffect) {
        val skills = player.skills
        when (effect) {
            is ButterflyEffect.Boost -> {
                val base = skills.baseLevel(effect.skill)
                val boost = boost(base)
                val current = skills.currentLevel(effect.skill)
                val boosted = minOf(base + boost, current + boost)
                if (boosted > current) skills.setCurrentLevel(effect.skill, boosted)
            }
            is ButterflyEffect.Heal -> heal(player, effect.amount)
            ButterflyEffect.Sunlight -> {
                for (skill in Skill.entries) {
                    if (skill == Skill.PRAYER || skill == Skill.HITPOINTS) continue
                    val base = skills.baseLevel(skill)
                    val current = skills.currentLevel(skill)
                    if (current < base) skills.setCurrentLevel(skill, minOf(base, current + base * 20 / 100 + 6))
                }
                heal(player, 8)
            }
            ButterflyEffect.Moonlight -> {
                val base = skills.baseLevel(Skill.PRAYER)
                val current = skills.currentLevel(Skill.PRAYER)
                if (current < base) skills.setCurrentLevel(Skill.PRAYER, minOf(base, current + 22))
            }
        }
    }

    private fun heal(player: Player, amount: Int) {
        val max = player.skills.baseLevel(Skill.HITPOINTS)
        if (player.hitpoints < max) player.hitpoints = minOf(max, player.hitpoints + amount)
    }

    companion object {
        /** The NPCs' op1 Catch; the jarred items' iop4 Release (the 242 cache). */
        const val CATCH = 1
        const val RELEASE = 4

        const val INSECT_NET = 10010
        const val MAGIC_INSECT_NET = 11259
        const val BUTTERFLY_JAR = 10012

        const val NET_ANIM = 5209 // human_butterflynet_swing

        /** Guessed: human_catch. */
        const val BAREHANDED_ANIM = 782

        /** The Wiki: barehanded needs 10 levels over the creature's requirement. */
        const val BAREHANDED_LEVELS = 10

        /** Guessed. */
        const val CATCH_TICKS = 3
        const val RESPAWN_TICKS = 10

        /** Snowy knight's page: a release shares the potions' 3 ticks. */
        const val RELEASE_DELAY = 3

        /** The jarred items' pages: 15% of the level, rounded down, plus 4. */
        fun boost(level: Int): Int = level * 15 / 100 + 4

        private fun warlockSpread(low: Int): Pair<Int, Int> = low to low + 276

        val RUBY_HARVEST = Butterfly(listOf(5556), 15, 240, warlockSpread(105), warlockSpread(125), warlockSpread(125), 10020, ButterflyEffect.Boost(Skill.ATTACK))
        val SAPPHIRE_GLACIALIS = Butterfly(listOf(5555), 25, 340, warlockSpread(76), warlockSpread(96), warlockSpread(96), 10018, ButterflyEffect.Boost(Skill.DEFENCE))
        val SNOWY_KNIGHT = Butterfly(listOf(5554), 35, 440, warlockSpread(48), warlockSpread(68), warlockSpread(68), 10016, ButterflyEffect.Heal(15))
        val BLACK_WARLOCK = Butterfly(listOf(5553), 45, 540, 20 to 296, 40 to 316, 40 to 316, 10014, ButterflyEffect.Boost(Skill.STRENGTH))
        val SUNLIGHT_MOTH = Butterfly(listOf(12770), 65, 740, 20 to 296, 40 to 316, 20 to 296, 28890, ButterflyEffect.Sunlight)
        val MOONLIGHT_MOTH = Butterfly(listOf(12771, 12772), 75, 840, 0 to 276, 20 to 286, 0 to 276, 28893, ButterflyEffect.Moonlight)

        val ALL = listOf(RUBY_HARVEST, SAPPHIRE_GLACIALIS, SNOWY_KNIGHT, BLACK_WARLOCK, SUNLIGHT_MOTH, MOONLIGHT_MOTH)
    }
}
