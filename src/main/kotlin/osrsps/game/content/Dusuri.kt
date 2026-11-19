package osrsps.game.content

import osrsps.game.Inventory
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.skill.BirdNests
import osrsps.game.skill.NestEntry
import osrsps.game.skill.ShootingStars

/** Dusuri's lines, from the Wiki's Transcript:Dusuri. */
object DusuriLines : Lines("dusuri", "Transcript:Dusuri") {
    val HELLO = line("hello", "his first line, both talks")
    val WHAT_DO_YOU_DO = line("what_do_you_do", "Introduction: the player's first line")
    val I_SPECIALISE = line("i_specialise", "Introduction: minerals not found on Gielinor")
    val WHERE_FROM = line("where_from", "Introduction: the player asks where they come from")
    val IN_STARS = line("in_stars", "Introduction: found within shooting stars")
    val SHOOTING_STARS = line("shooting_stars", "Introduction, never touched a star: the player")
    val THEY_FALL = line("they_fall", "Introduction, never touched a star: they fall and can be mined")
    val HAPPY_TO_TRADE = line("happy_to_trade", "Introduction, never touched a star: he'd trade for stardust")
    val SEEN_THEM = line("seen_them", "Introduction, has touched a star: the player has seen them")
    val WONDERFUL = line("wonderful", "Introduction, has touched a star: he'd trade for stardust")
    val HAVE_SOME = line("have_some", "Introduction with stardust: the option, which the player then says")
    val BEAR_IN_MIND = line("bear_in_mind", "Introduction: the player's goodbye, also an option")
    val GOOD_LUCK = line("good_luck", "Introduction: his goodbye")
    val OF_COURSE = line("of_course", "members: his line before the shop opens")
    val CAN_WE_TRADE = line("can_we_trade", "Standard dialogue: the option, which the player then says")
    val WHAT_DO_YOU_SELL = line("what_do_you_sell", "Standard dialogue: the option, which the player then says")
    val PACKS = line("packs", "What do you sell: packs of gems and soft clay, star fragments")
    val RING = line("ring", "What do you sell: the ring found in a star")
    val ABOUT_STARS = line("about_stars", "Standard dialogue: the option, which the player then says")
    val NOT_A_LOT = line("not_a_lot", "About stars: not a lot really")
    val OBSERVATORY = line("observatory", "About stars: the observatory near Ardougne")
    val IM_OFF = line("im_off", "Standard dialogue: the leaving option, which the player then says")
    val PACK_SPACE = line("pack_space", "Not on the Wiki: Open on a bag or pack without room for what it holds")
}

/**
 * Shooting stars step 2 (OSRS Wiki, Dusuri, Dusuri's Star Shop, Bag full of gems, Soft clay pack and Celestial ring,
 * read 2026-11-19): Dusuri by the Mining Guild's entrance in Falador (npc-spawns/shooting-stars.json; Falador isn't
 * built, so he is reached by ::tele), his Talk-to, and what his shop sells (shops.json, paid in stardust; Trade is the
 * generic one in [registerShops]).
 *
 * - He is the multi-NPC star_trader: Talk-to only until the introduction ends with varbit star_trader_met set, then
 *   Trade as well. The introduction asks whether the player has touched a star (star_mined, set by [ShootingStars]) and
 *   offers to trade there and then when they carry stardust. Every world counts as members, so his free-to-play refusal is never used.
 * - A bag full of gems opens to 40 noted uncut gems, each rolled on its table (sapphire 100, emerald 69, ruby 24,
 *   diamond 6, dragonstone 1 out of 200, and onyx 1 in 100,000,000 first, logged in the collection log); a soft clay
 *   pack to 100 noted soft clay.
 * - The celestial ring's +4 is [osrsps.game.skill.Mining.celestialBoost].
 *
 * Guessed: every chathead expression; a bag or pack needing room for everything it could hold (for the bag, 40 of each
 * of the six gems, checked before the roll so a refusal never re-rolls), opening one at a time, and the message when it
 * hasn't (none on the Wiki). Out: the ring's Charge, Check and Uncharge (its charges only matter
 * for the celestial signet's extra ore, which isn't in), the celestial signet, and recolouring prospector pieces with
 * a star fragment.
 */
object Dusuri {
    /** star_trader_1op and star_trader_2op: Dusuri before and after he has met the player. */
    const val BEFORE = 10630
    const val AFTER = 10631
    const val TALK_TO = 1

    /** star_trader_met. */
    const val VARBIT_MET = 11887

    const val BAG_FULL_OF_GEMS = 25537 // star_reward_gem_bag
    const val SOFT_CLAY_PACK = 25533 // star_pack_softclay
    const val SOFT_CLAY = 1761
    const val OPEN = 1
    const val GEMS_PER_BAG = 40
    const val CLAY_PER_PACK = 100

    /** The bag's table, out of 200 (OSRS Wiki, Bag full of gems: all three bags share it). */
    val GEM_TABLE = listOf(
        NestEntry(1623, 100), // uncut_sapphire
        NestEntry(1621, 69), // uncut_emerald
        NestEntry(1619, 24), // uncut_ruby
        NestEntry(1617, 6), // uncut_diamond
        NestEntry(1631, 1), // uncut_dragonstone
    )
    const val UNCUT_ONYX = 6571
    const val ONYX_CHANCE = 100_000_000

    private fun varbit(player: Player, id: Int): Int = if (player.vars.types.varbit(id) != null) player.vars.getVarbit(id) else 0

    /** Talk-to; true when the player asked to trade and the shop should open. */
    internal suspend fun Dialogue.talk(): Boolean = with(DusuriLines) {
        chatNpc(ChatAnim.HAPPY, HELLO)
        if (varbit(player, VARBIT_MET) == 0) return introduction()
        when (choice(CAN_WE_TRADE, WHAT_DO_YOU_SELL, ABOUT_STARS, IM_OFF)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, CAN_WE_TRADE)
                chatNpc(ChatAnim.HAPPY, OF_COURSE)
                return true
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, WHAT_DO_YOU_SELL)
                chatNpc(ChatAnim.NEUTRAL, PACKS)
                chatNpc(ChatAnim.NEUTRAL, RING)
            }
            3 -> {
                chatPlayer(ChatAnim.QUIZ, ABOUT_STARS)
                chatNpc(ChatAnim.NEUTRAL, NOT_A_LOT)
                chatNpc(ChatAnim.NEUTRAL, OBSERVATORY)
            }
            else -> chatPlayer(ChatAnim.NEUTRAL, IM_OFF)
        }
        return false
    }

    private suspend fun Dialogue.introduction(): Boolean = with(DusuriLines) {
        chatPlayer(ChatAnim.QUIZ, WHAT_DO_YOU_DO)
        chatNpc(ChatAnim.NEUTRAL, I_SPECIALISE)
        chatPlayer(ChatAnim.CONFUSED, WHERE_FROM)
        chatNpc(ChatAnim.NEUTRAL, IN_STARS)
        val seen = varbit(player, ShootingStars.VARBIT_MINED) == 1
        if (!seen) {
            chatPlayer(ChatAnim.CONFUSED, SHOOTING_STARS)
            chatNpc(ChatAnim.NEUTRAL, THEY_FALL)
            chatNpc(ChatAnim.HAPPY, HAPPY_TO_TRADE)
            met(player)
        } else {
            chatPlayer(ChatAnim.HAPPY, SEEN_THEM)
            chatNpc(ChatAnim.HAPPY, WONDERFUL)
            met(player)
            if (player.inventory.count(ShootingStars.STARDUST) > 0 && choice(HAVE_SOME, BEAR_IN_MIND) == 1) {
                chatPlayer(ChatAnim.QUIZ, HAVE_SOME)
                chatNpc(ChatAnim.HAPPY, OF_COURSE)
                return true
            }
        }
        chatPlayer(ChatAnim.NEUTRAL, BEAR_IN_MIND)
        chatNpc(ChatAnim.HAPPY, GOOD_LUCK)
        return false
    }

    /** Dusuri gains Trade: the multi-NPC shows star_trader_2op from now on. */
    private fun met(player: Player) {
        if (player.vars.types.varbit(VARBIT_MET) != null) player.vars.setVarbit(VARBIT_MET, 1)
    }

    /** Whether [count] of [type] fit: onto its stack without passing the cap, or into a free slot. */
    internal fun fits(inv: Inventory, types: Map<ObjType, Int>, freedSlot: Boolean): Boolean {
        var slots = inv.freeSlots + if (freedSlot) 1 else 0
        for ((type, count) in types) {
            val held = inv.count(type.id)
            if (held > 0) {
                if (held + count > Inventory.MAX_STACK) return false
            } else if (--slots < 0) return false
        }
        return true
    }
}

fun registerDusuri(world: World) {
    with(Dusuri) {
        for (id in listOf(BEFORE, AFTER)) {
            world.handlers.onNpc(id, TALK_TO) { op ->
                var trade = false
                world.dialogues.start(this, op.npc) { trade = talk() }
                if (trade) world.shopTypes["dusuris_star_shop"]?.let { world.shopScreen.open(player, it) }
            }
        }
        world.handlers.onObj(BAG_FULL_OF_GEMS, OPEN) { op ->
            if (player.inventory[op.sub]?.id != BAG_FULL_OF_GEMS) return@onObj
            // Noted: each gem's certlink, the gem itself where the cache gives none (the tests' small ones).
            fun noted(gem: Int): ObjType {
                val type = world.objTypes[gem] ?: error("No obj type $gem for a bag full of gems")
                return type.certlink.takeIf { it >= 0 }?.let { world.objTypes[it] } ?: type
            }
            // Room for all 40 of any gem it could hold, checked before the roll so a refusal can't be used to re-roll.
            val possible = (GEM_TABLE.map { it.obj } + UNCUT_ONYX).associate { noted(it) to GEMS_PER_BAG }
            if (!fits(player.inventory, possible, freedSlot = true)) return@onObj world.message(player, DusuriLines.PACK_SPACE)
            val rolled = LinkedHashMap<ObjType, Int>()
            var onyxes = 0
            repeat(GEMS_PER_BAG) {
                val gem = if (world.random.chance(1, ONYX_CHANCE)) UNCUT_ONYX else BirdNests.pick(world.random, GEM_TABLE)
                if (gem == UNCUT_ONYX) onyxes++
                rolled.merge(noted(gem), 1, Int::plus)
            }
            player.inventory.remove(op.sub, 1)
            for ((type, count) in rolled) player.inventory.add(type, count)
            // An onyx from the bag goes in the collection log (OSRS Wiki, Bag full of gems), as the unnoted gem.
            world.collectionLog.add(player, UNCUT_ONYX, onyxes)
        }
        world.handlers.onObj(SOFT_CLAY_PACK, OPEN) { op ->
            if (player.inventory[op.sub]?.id != SOFT_CLAY_PACK) return@onObj
            val clay = world.objTypes[SOFT_CLAY] ?: return@onObj
            val noted = clay.certlink.takeIf { it >= 0 }?.let { world.objTypes[it] } ?: clay
            if (!fits(player.inventory, mapOf(noted to CLAY_PER_PACK), freedSlot = true)) return@onObj world.message(player, DusuriLines.PACK_SPACE)
            player.inventory.remove(op.sub, 1)
            player.inventory.add(noted, CLAY_PER_PACK)
        }
    }
}
