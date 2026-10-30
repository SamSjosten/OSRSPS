package osrsps.game.content

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.FoodLines
import osrsps.game.combat.Foods
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.shop.ShopScreen

/** Karim's lines; the words are in the dialogue file, from the Wiki's Transcript:Karim, read 2026-10-30. */
object KarimLines : Lines("karim", "Transcript:Karim") {
    val OFFER = line("offer", "his first line: a kebab for one gold")
    val MISS = line("miss", "option 1, which the player then says")
    val YES_PLEASE = line("yes_please", "option 2, which the player then says")
    val OOPS = line("oops", "option 2 with no coins: the player's line")
    val COME_BACK = line("come_back", "option 2 with no coins: his answer")
    val ALI = line("ali", "option 3 (after Rogue Trader): Ali Morrisane, which the player then says")
    val ALI_1 = line("ali_1", "the Ali Morrisane topic: his first line")
    val ALI_2 = line("ali_2", "the Ali Morrisane topic: second line")
    val ALI_3 = line("ali_3", "the Ali Morrisane topic: third line")
    val RUMOURS = line("rumours", "the Ali Morrisane topic: the player asks what rumours")
    val APPARENTLY = line("apparently", "the Ali Morrisane topic: his answer")
    val HIMSELF = line("himself", "the Ali Morrisane topic: his next line")
    val REALLY = line("really", "the Ali Morrisane topic: the player's one word")
    val KEBAB = line("kebab", "the Ali Morrisane topic: his last line, before the menu again")
}

/** The Silk trader's lines; the words are in the dialogue file, from the Wiki's Transcript:Silk trader, read 2026-10-30. */
object SilkTraderLines : Lines("silk_trader", "Transcript:Silk trader") {
    val OFFER = line("offer", "his first line")
    val HOW_MUCH = line("how_much", "option 1, which the player then says")
    val THREE_GP = line("three_gp", "his first price")
    val NOT_FOR_ME = line("not_for_me", "option 2, which the player then says")
    val TOO_MUCH = line("too_much", "the price menu, option 1, which the player then says")
    val SOUNDS_GOOD = line("sounds_good", "the price menu, option 2, which the player then says")
    val TWO_GP = line("two_gp", "his lower price")
    val VARROCK = line("varrock", "his line after the lower price")
    val TWO_GP_GOOD = line("two_gp_good", "the lower price menu, option 1, which the player then says")
    val DONT_WANT = line("dont_want", "the lower price menu, option 2, which the player then says")
    val BEST_PRICE = line("best_price", "his answer to that")
    val BOUGHT = line("bought", "the item box after buying silk; {price} is what it cost", "price")
    val NO_MONEY = line("no_money", "not enough coins: the player's line")
    val COME_BACK = line("come_back", "not enough coins: his answer")
}

/** The kebab's messages, from the Wiki's Kebab page (read 2026-10-30), after the food's "eat" line. */
object KebabLines : Lines("kebab", "Kebab") {
    val AMAZING = line("amazing", "the 1/32 outcome: a big heal and melee stats raised")
    val GOOD = line("good", "the 8/32 outcome: a good heal")
    val NO_EFFECT = line("no_effect", "the 1/32 outcome with no effect")
    val DODGY = line("dodgy", "the 1/32 outcome draining a skill")
    val DAMAGED = line("damaged", "after dodgy: the skill drained; {skill} is its name", "skill")
    val VERY_DODGY = line("very_dodgy", "the 1/32 outcome draining melee and a skill")
    val DAMAGED_SOME = line("damaged_some", "after very dodgy")
}

/**
 * Karim, the kebab seller north of the bank (`kebab_seller`, 2877), and the Silk trader (2873), who sell through their
 * talk alone, and the kebab itself.
 *
 * From the Wiki: Karim's talk (as after Rogue Trader), a kebab for 1 coin; the Silk trader's talk, silk for 3 gp, or
 * 2 gp after haggling. The Wiki shows no message when Karim sells a kebab, so there is none. Guessed: the expressions
 * (2004Scape's), that a full backpack drops the bought item at the player's feet (as the Duke's shield does).
 */
object AlKharidTraders {
    const val KARIM = 2877
    const val SILK_TRADER = 2873
    const val TALK_TO = 1
    const val KEBAB = 1971
    const val SILK = 950
    const val KEBAB_PRICE = 1

    /** Karim's talk; [give] hands over what the player bought. True when the player paid for a kebab. */
    internal suspend fun Dialogue.karim(give: (Int) -> Unit): Boolean = with(KarimLines) {
        chatNpc(ChatAnim.QUIZ, OFFER)
        var bought: Boolean? = null
        while (bought == null) {
            when (choice(MISS, YES_PLEASE, ALI)) {
                1 -> {
                    chatPlayer(ChatAnim.NEUTRAL, MISS)
                    bought = false
                }
                2 -> {
                    chatPlayer(ChatAnim.NEUTRAL, YES_PLEASE)
                    bought = pay(player, KEBAB_PRICE)
                    if (bought) {
                        give(AlKharidTraders.KEBAB)
                    } else {
                        chatPlayer(ChatAnim.SAD, OOPS)
                        chatNpc(ChatAnim.NEUTRAL, COME_BACK)
                    }
                }
                else -> {
                    chatPlayer(ChatAnim.QUIZ, ALI)
                    chatNpc(ChatAnim.ANGRY, ALI_1)
                    chatNpc(ChatAnim.ANGRY, ALI_2)
                    chatNpc(ChatAnim.NEUTRAL, ALI_3)
                    chatPlayer(ChatAnim.QUIZ, RUMOURS)
                    chatNpc(ChatAnim.SHIFTY, APPARENTLY)
                    chatNpc(ChatAnim.SHIFTY, HIMSELF)
                    chatPlayer(ChatAnim.SHOCKED, REALLY)
                    chatNpc(ChatAnim.QUIZ, KEBAB)
                }
            }
        }
        bought == true
    }

    /** The Silk trader's talk; [give] hands over the silk. The price paid for silk, or 0 when the player bought none. */
    internal suspend fun Dialogue.silkTrader(give: (Int) -> Unit): Int = with(SilkTraderLines) {
        chatNpc(ChatAnim.NEUTRAL, OFFER)
        if (choice(HOW_MUCH, NOT_FOR_ME) == 2) {
            chatPlayer(ChatAnim.NEUTRAL, NOT_FOR_ME)
            return@with 0
        }
        chatPlayer(ChatAnim.NEUTRAL, HOW_MUCH)
        chatNpc(ChatAnim.NEUTRAL, THREE_GP)
        if (choice(TOO_MUCH, SOUNDS_GOOD) == 2) {
            chatPlayer(ChatAnim.NEUTRAL, SOUNDS_GOOD)
            return@with buySilk(3, give)
        }
        chatPlayer(ChatAnim.NEUTRAL, TOO_MUCH)
        chatNpc(ChatAnim.QUIZ, TWO_GP)
        chatNpc(ChatAnim.SAD, VARROCK)
        if (choice(TWO_GP_GOOD, DONT_WANT) == 2) {
            chatPlayer(ChatAnim.NEUTRAL, DONT_WANT)
            chatNpc(ChatAnim.NEUTRAL, BEST_PRICE)
            return@with 0
        }
        chatPlayer(ChatAnim.NEUTRAL, TWO_GP_GOOD)
        buySilk(2, give)
    }

    private suspend fun Dialogue.buySilk(price: Int, give: (Int) -> Unit): Int = with(SilkTraderLines) {
        if (!pay(player, price)) {
            chatPlayer(ChatAnim.SAD, NO_MONEY)
            chatNpc(ChatAnim.ANGRY, COME_BACK)
            return@with 0
        }
        give(SILK)
        objbox(SILK, text(BOUGHT, "price" to price))
        price
    }

    /** Takes [price] coins from [player]'s backpack; false, taking nothing, when they hold fewer. */
    fun pay(player: Player, price: Int): Boolean {
        if (player.inventory.count(ShopScreen.COINS) < price) return false
        var left = price
        for ((slot, held) in player.inventory.contents()) {
            if (left == 0) break
            if (held.id == ShopScreen.COINS) left -= player.inventory.remove(slot, left)
        }
        return true
    }
}

/**
 * Eating a kebab (OSRS Wiki, Kebab, read 2026-10-30): "You eat the kebab", then one of six outcomes out of 32. 1: heals
 * 7 + 24% of the Hitpoints level and raises Attack, Strength and Defence by 2; 8: heals 6 + 14%; 20: heals 3 + 7%; 1: no
 * effect; 1: drains a random skill other than Hitpoints by 3; 1: drains the three melee stats by 3 and a random skill
 * by 4. It eats as other food does (the eat delay and the attack delay of [Foods]).
 *
 * Guessed: percentages round down; the +2 raises each stat to at most 2 over its base, never lowering a boost; a drain
 * stops at level 2 (the Wiki: a random skill is "not drained below level 2", and at level 2 or lower only the first
 * message shows), and the melee drain at level 1; "heals some health" follows only the smallest heal, and only when it
 * gave hitpoints back.
 */
object Kebab {
    const val ROLLS = 32

    fun eat(world: World, player: Player, slot: Int) {
        val tick = world.tick
        if (player.dead || player.inventory[slot]?.id != AlKharidTraders.KEBAB) return
        if (tick < player.foodDelayUntil) return
        player.foodDelayUntil = tick + Foods.DEFAULT_DELAY
        if (player.nextAttackTick > tick) player.nextAttackTick += Foods.DEFAULT_DELAY
        player.inventory.remove(slot, 1)
        player.anim(Foods.EAT_ANIM)
        val text = world.textFor(player)
        player.message(text[FoodLines.EAT, "food" to "kebab"])
        outcome(player, world.random.nextInt(ROLLS), { world.random.pick(it) }) { line, values -> player.message(text.get(line, *values)) }
    }

    /** What roll [roll] (0 to 31) does to [player]; [pick] chooses the random skill, [say] sends each message. */
    internal fun outcome(player: Player, roll: Int, pick: (List<Skill>) -> Skill, say: (Line, Array<Pair<String, Any>>) -> Unit) {
        val level = player.skills.baseLevel(Skill.HITPOINTS)
        when {
            roll == 0 -> {
                heal(player, 7 + level * 24 / 100)
                for (skill in MELEE) raise(player, skill, 2)
                say(KebabLines.AMAZING, emptyArray())
            }
            roll <= 8 -> {
                heal(player, 6 + level * 14 / 100)
                say(KebabLines.GOOD, emptyArray())
            }
            roll <= 28 -> {
                if (heal(player, 3 + level * 7 / 100)) say(FoodLines.HEALS, emptyArray())
            }
            roll == 29 -> say(KebabLines.NO_EFFECT, emptyArray())
            roll == 30 -> {
                say(KebabLines.DODGY, emptyArray())
                val skill = pick(DRAINABLE)
                if (drain(player, skill, 3, floor = 2)) say(KebabLines.DAMAGED, arrayOf("skill" to skill.displayName))
            }
            else -> {
                say(KebabLines.VERY_DODGY, emptyArray())
                for (skill in MELEE) drain(player, skill, 3, floor = 1)
                if (drain(player, pick(DRAINABLE), 4, floor = 2)) say(KebabLines.DAMAGED_SOME, emptyArray())
            }
        }
    }

    internal val MELEE = listOf(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE)
    internal val DRAINABLE = Skill.entries.filter { it != Skill.HITPOINTS }

    /** Heals [amount] up to the Hitpoints level; true when it gave any back. */
    private fun heal(player: Player, amount: Int): Boolean {
        val before = player.hitpoints
        player.hitpoints = maxOf(before, minOf(before + amount, player.skills.baseLevel(Skill.HITPOINTS)))
        return player.hitpoints > before
    }

    private fun raise(player: Player, skill: Skill, by: Int) {
        val current = player.skills.currentLevel(skill)
        player.skills.setCurrentLevel(skill, maxOf(current, minOf(current + by, player.skills.baseLevel(skill) + by)))
    }

    /** Drains [skill] by [by], not below [floor]; false when it was already at or below the floor. */
    private fun drain(player: Player, skill: Skill, by: Int, floor: Int): Boolean {
        val current = player.skills.currentLevel(skill)
        if (current <= floor) return false
        player.skills.setCurrentLevel(skill, maxOf(floor, current - by))
        return true
    }
}

/** Karim's and the Silk trader's talk, and Eat on a kebab. */
fun registerAlKharidTraders(world: World) {
    with(AlKharidTraders) {
        world.npcInteractions.reachOverCounter(KARIM)
        world.handlers.onNpc(KARIM, TALK_TO) { op ->
            val give = { obj: Int -> world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } ?: Unit }
            world.dialogues.start(this, op.npc) { karim(give) }
        }
        world.handlers.onNpc(SILK_TRADER, TALK_TO) { op ->
            val give = { obj: Int -> world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } ?: Unit }
            world.dialogues.start(this, op.npc) { silkTrader(give) }
        }
    }
    val eat = world.objTypes[AlKharidTraders.KEBAB]?.iops?.indexOfFirst { it == Foods.EAT }?.takeIf { it >= 0 }?.plus(1) ?: 1
    world.handlers.onObj(AlKharidTraders.KEBAB, eat) { op -> Kebab.eat(world, player, op.sub) }
}
