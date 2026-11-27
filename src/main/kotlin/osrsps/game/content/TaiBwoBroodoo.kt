package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.InvObj
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.AttackType
import osrsps.game.combat.CombatLines
import osrsps.game.npc.Npc

/**
 * The Tai Bwo Wannai Cleanup's Broodoo victims (Karamja step K4.2c; OSRS Wiki: Broodoo victim and Tai Bwo Wannai
 * Cleanup § Broodoo victims, read 2026-11-19). They come out of the jungle as [TaiBwoEvents] creatures (4, 3 and 2 in 99
 * of the event table) and fight with magic (npc-combat.json); a player's own attacks hit them for at most 3. Each has a
 * weakness, used on it:
 *
 * - **White** (6413, "combat"): food. Fruit and vegetables (the item categories 86 and 131) hit 10 to 35, the Wiki's;
 *   twice the damage as Cooking XP. Drinks don't count: only what [osrsps.game.combat.Foods] eats.
 * - **Green** (6409, "poison"): a dose of a poison cure potion, not a mix: antipoison up to 20, superantipoison and sanfew
 *   serum 30, antidote+ 40, antidote++ and anti-venom(+) 50, the Wiki's table; twice the damage as Herblore XP.
 * - **Yellow** (6411, "disease"): a dose of Relicym's balm or sanfew serum; twice the damage as Herblore XP.
 *
 * Anything else: the usual nothing-happens message. Druidic Ritual's XP condition counts as met (no quests).
 *
 * Guessed: every other food's range (1 to 20); Relicym's balm and sanfew serum's against the yellow victim (up to 20 and
 * 30, as sanfew's against the green); hits from 0 up to each item's max, as an attack's are; a player's landed hit rolled
 * again from 0 to the lesser of it and 3 (a 0 stays a 0); the victim's mask-eating animation (tbw_broodoo_mask_eat)
 * for any item it's given; a dose or bite going even when the hit is 0. Left out: the stat drain the Wiki mentions
 * without saying which stats or how much.
 */
object TaiBwoBroodoo {
    const val FOOD = 6413
    const val POISON = 6409
    const val DISEASE = 6411
    val VICTIMS = listOf(FOOD, POISON, DISEASE)

    /** What a player's own hits are held to. */
    const val PLAYER_MAX_HIT = 3

    const val EAT_ANIM = 2845
    const val FRUIT = 86
    const val VEGETABLE = 131
    const val FRUIT_MIN = 10
    const val FRUIT_MAX = 35
    const val OTHER_FOOD_MIN = 1
    const val OTHER_FOOD_MAX = 20

    /**
     * Calquat fruit and the gout tuber: on the Wiki's list for the white victim, though not eaten (Codex review on
     * #1034). GUESSED: the fruit and vegetables' 10 to 35; given up whole.
     */
    val WHITE_EXTRAS = setOf(5980, TaiBwoEvents.GOUT_TUBER)

    /** Twice the damage, in tenths. */
    const val XP_TENTHS_PER_DAMAGE = 20

    /** The poison cures' max hits on the green victim, by the potions' names. */
    val POISON_CURES = mapOf(
        "antipoison" to 20, "superantipoison" to 30, "sanfew serum" to 30, "antidote+" to 40, "antidote++" to 50,
        "anti-venom" to 50, "anti-venom+" to 50,
    )

    /** Relicym's balm, 4 doses to 1; not in potions.json, as it does nothing drunk here yet. */
    val RELICYMS_BALM = listOf(4842, 4844, 4846, 4848)
    const val RELICYMS_MAX = 20
    const val SANFEW_DISEASE_MAX = 30
    const val VIAL = 229

    /** The most [obj] hits [victim] for, or null when it does nothing to it. */
    fun maxHit(world: World, victim: Int, obj: Int): IntRange? = when (victim) {
        FOOD -> if (obj in WHITE_EXTRAS) FRUIT_MIN..FRUIT_MAX else world.foods.all[obj]?.let {
            val category = world.objTypes[obj]?.category
            if (category == FRUIT || category == VEGETABLE) FRUIT_MIN..FRUIT_MAX else OTHER_FOOD_MIN..OTHER_FOOD_MAX
        }
        POISON -> world.potions.all[obj]?.let { POISON_CURES[it.name] }?.let { 0..it }
        DISEASE -> when {
            obj in RELICYMS_BALM -> 0..RELICYMS_MAX
            world.potions.all[obj]?.name == "sanfew serum" -> 0..SANFEW_DISEASE_MAX
            else -> null
        }
        else -> null
    }

    /** The item after one use: the next bite or dose, a vial, or nothing. */
    private fun used(world: World, obj: Int): InvObj? {
        world.foods.all[obj]?.let { return if (it.next >= 0) InvObj(it.next, 1) else null }
        val doses = world.potions.all[obj]?.doses ?: RELICYMS_BALM.takeIf { obj in it }
        if (doses != null) {
            val next = doses.indexOf(obj) + 1
            return InvObj(if (next < doses.size) doses[next] else VIAL, 1)
        }
        return null
    }

    private fun give(world: World, player: Player, npc: Npc, obj: Int, slot: Int) {
        if (npc.dead || world.npcs[npc.index] !== npc) return
        val range = maxHit(world, npc.type.id, obj) ?: return world.message(player, GameLines.NOTHING_INTERESTING)
        if (player.inventory[slot]?.id != obj) return
        // Single-way, as any attack: not someone else's victim, nor while another monster fights the player (Codex
        // review on #1034).
        if (world.meleeCombat.underAttackByAnother(player, npc)) return world.message(player, CombatLines.ALREADY_UNDER_ATTACK)
        if (world.meleeCombat.foughtByAnother(player, npc)) return world.message(player, CombatLines.SOMEONE_ELSE)
        player.inventory[slot] = used(world, obj)
        npc.effects.anim(EAT_ANIM)
        val damage = world.meleeCombat.hit(npc, player, world.random.between(range.first, range.last), world.tick)
        if (damage <= 0) return
        val skill = if (npc.type.id == FOOD) Skill.COOKING else Skill.HERBLORE
        player.skills.addTenths(skill, damage * XP_TENTHS_PER_DAMAGE)
    }

    fun register(world: World) {
        for (victim in VICTIMS) world.handlers.onObjOnNpc(victim) { op -> give(world, player, op.npc!!, op.obj, op.sub) }
        val rolledOn = world.combat.rolledOn
        world.combat.rolledOn = { player, npc, type: AttackType, rolled ->
            val own = rolledOn(player, npc, type, rolled)
            if (npc.type.id in VICTIMS && own > 0) world.random.between(0, minOf(own, PLAYER_MAX_HIT)) else own
        }
    }
}
