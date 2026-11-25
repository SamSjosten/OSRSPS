package osrsps.game.content

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.shop.ShopScreen
import osrsps.game.skill.ToolCheck
import osrsps.game.skill.WoodcuttingLines
import kotlin.math.abs

/** Saniboch's words, the Wiki's Transcript:Saniboch (read 2026-11-18). */
object SanibochLines : Lines("saniboch", "Transcript:Saniboch") {
    val GREETING = line("greeting", "his greeting")
    val CAN_I_GO = line("can_i_go", "option 1, which the player then says")
    val ALREADY_GIVEN = line("already_given", "option 1, already paid: most certainly")
    val CHARGE = line("charge", "option 1, not paid: 875 coins first")
    val NOT_THAT_MUCH = line("not_that_much", "its menu without 875 coins, option 1, which the player then says")
    val RIFF_RAFF = line("riff_raff", "his answer without the fee (also his Pay option's without it)")
    val HERES_875 = line("heres_875", "its menu with the fee, option 1, which the player then says")
    val GIVE_875 = line("give_875", "the box as the 875 coins go")
    val MANY_THANKS = line("many_thanks", "then: the player may pass the door")
    val WHY_WORTH = line("why_worth", "its menu with the fee, option 2, which the player then says")
    val WORTH_1 = line("worth_1", "why it's worth it: giants and strange dogs")
    val WORTH_2 = line("worth_2", "then: he knows not what lies deeper")
    val PERMANENT = line("permanent", "the option asking for permanent access, which the player then says")
    val SUPPOSE_SO = line("suppose_so", "permanent access: 1,000,000 coins")
    val NO_MILLION = line("no_million", "permanent access without 1,000,000 coins: the player hasn't got it")
    val PERHAPS_875 = line("perhaps_875", "then: perhaps 875 for one visit")
    val HERES_MILLION = line("heres_million", "permanent access with 1,000,000 coins, option 1, which the player then says")
    val GIVE_MILLION = line("give_million", "the box as the 1,000,000 coins go")
    val THANKS_FOREVER = line("thanks_forever", "then: the player may pass as often as they wish")
    val NEVER_MIND = line("never_mind", "the option to leave it, which the player then says")
    val WHERE_LEAD = line("where_lead", "option 2, which the player then says")
    val LEAD_1 = line("lead_1", "option 2: a huge fearsome dungeon")
    val LEAD_2 = line("lead_2", "option 2: he knows not what lies deeper")
    val IMPRESSED = line("impressed", "option 3, which the player then says")
    val FYBURGLARS = line("fyburglars", "option 3: the Fyburglars tree")
    val GOOD_DAY = line("good_day", "option 4, which the player then says")
    val PAY_TITLE = line("pay_title", "his Pay option with 875 coins but not 1,000,000: the menu's title")
    val YES = line("yes", "that menu's option 1")
    val NO = line("no", "that menu's option 2")
    val PAY_ONCE = line("pay_once", "his Pay option with 1,000,000 coins: option 1")
    val PAY_FOREVER = line("pay_forever", "that menu's option 2")
    val CANCEL = line("cancel", "that menu's option 3")
    val LOVELY_COINS = line("lovely_coins", "his Pay option, paid once: the player may go in")
    val OKAY_THANKS = line("okay_thanks", "then the menu's option 1, which the player then says")
    val NICE_COINS = line("nice_coins", "his Pay option with permanent access")
}

/**
 * The way into Brimhaven Dungeon (Karamja step K3.3), as the Wiki's Brimhaven Dungeon, Saniboch, Dungeon entrance and
 * Vines (Brimhaven Dungeon) pages have them (read 2026-11-18):
 *
 * - **Saniboch** (2345) by the entrance takes 875 coins for one visit, or 1,000,000 for good, through his talk or his Pay
 *   option. What's paid is the cache's `karam_dungeon_doordata` varbit ([DOOR_VARBIT]): 1 for one visit, 8 for good,
 *   which the entrance (20877, a multiloc) shows as open (20876) or shut (34713).
 * - **The entrance**'s Pay asks Saniboch's Pay; Enter takes the player in, using up a one-visit fee. The exit (20878)
 *   leads back out.
 * - **The vines** (21731 to 21735) are chopped through toward the dungeon's depths with an axe and 10, 27, 22, 34 and 34
 *   Woodcutting; back toward the exit they part by hand.
 * - **The stairs** to the upper level (21722 and 21725 up, 21724 and 21726 down). The Agility shortcuts are
 *   skills/agility.json's.
 *
 * Inferred: the vines' refusals are Woodcutting's own level and axe messages; "Never mind" on both of the door menus
 * (the transcript's tree puts it after them). Guessed: the arrival tiles inside, outside and at each stair's end; the
 * axe's swing for 2 ticks before a vine is passed (and no swing by hand); the chathead faces. Left out: Banisoch's
 * back way (step K4.2), Hieve's metal dragon chamber (a Slayer task), the clue step, and the vines' Woodcutting XP
 * (none, the Wiki's 0).
 */
object BrimhavenDungeon {
    const val SANIBOCH = 2345
    const val TALK_TO = 1
    const val PAY = 3
    const val ENTRANCE = 20877
    const val EXIT = 20878
    const val ENTER = 1
    const val LEAVE = 1
    const val CHOP = 1
    const val CLIMB = 1

    const val FEE = 875
    const val FOREVER = 1_000_000

    /** karam_dungeon_doordata: 1 once paid for a visit, 8 once paid for good (the entrance's multiloc shows either open). */
    const val DOOR_VARBIT = 8123
    const val PAID_ONCE = 1
    const val PAID_FOREVER = 8

    /** Where the entrance and exit put the player: inside by the exit, outside south of the entrance. */
    val INSIDE = Destination(2713, 9564, 0)
    val OUTSIDE = Destination(2744, 3152, 0)

    /** A vine: its loc, the side toward the exit (where an axe is needed to go on) and the deeper side, and the level. */
    class Vine(val loc: Int, val near: Destination, val deep: Destination, val level: Int)

    val VINES = listOf(
        Vine(21731, Destination(2691, 9564, 0), Destination(2689, 9564, 0), 10),
        Vine(21732, Destination(2683, 9568, 0), Destination(2683, 9570, 0), 27),
        Vine(21733, Destination(2672, 9499, 0), Destination(2674, 9499, 0), 22),
        Vine(21734, Destination(2674, 9479, 0), Destination(2676, 9479, 0), 34),
        Vine(21735, Destination(2693, 9482, 0), Destination(2695, 9482, 0), 34),
    )

    /** The stairs: each loc and where it puts the player. */
    val STAIRS = mapOf(
        21722 to Destination(2643, 9594, 2),
        21724 to Destination(2649, 9591, 0),
        21725 to Destination(2636, 9510, 2),
        21726 to Destination(2636, 9518, 0),
    )

    /** Guessed: the ticks the axe swings before a vine is passed. */
    const val VINE_SWING_TICKS = 2

    fun paid(player: Player): Int = if (player.vars.types.varbit(DOOR_VARBIT) != null) player.vars.getVarbit(DOOR_VARBIT) else 0

    private fun pay(player: Player, flag: Int) {
        if (player.vars.types.varbit(DOOR_VARBIT) != null) player.vars.setVarbit(DOOR_VARBIT, paid(player) or flag)
    }

    private fun coins(player: Player) = player.inventory.count(ShopScreen.COINS)

    private suspend fun Dialogue.payOnce() = with(SanibochLines) {
        if (!AlKharidTraders.pay(player, FEE)) return@with
        pay(player, PAID_ONCE)
        mesbox(GIVE_875)
        chatNpc(ChatAnim.HAPPY, MANY_THANKS)
    }

    private suspend fun Dialogue.payForever() = with(SanibochLines) {
        if (!AlKharidTraders.pay(player, FOREVER)) return@with
        pay(player, PAID_FOREVER)
        mesbox(GIVE_MILLION)
        chatNpc(ChatAnim.HAPPY, THANKS_FOREVER)
    }

    private suspend fun Dialogue.permanent() = with(SanibochLines) {
        chatPlayer(ChatAnim.QUIZ, PERMANENT)
        chatNpc(ChatAnim.NEUTRAL, SUPPOSE_SO)
        if (coins(player) < FOREVER) {
            chatPlayer(ChatAnim.SAD, NO_MILLION)
            chatNpc(ChatAnim.NEUTRAL, PERHAPS_875)
            if (choice(HERES_875, NEVER_MIND) == 1) {
                chatPlayer(ChatAnim.NEUTRAL, HERES_875)
                if (coins(player) >= FEE) payOnce() else chatNpc(ChatAnim.ANGRY, RIFF_RAFF)
            } else {
                chatPlayer(ChatAnim.NEUTRAL, NEVER_MIND)
            }
            return@with
        }
        if (choice(HERES_MILLION, NEVER_MIND) == 1) {
            chatPlayer(ChatAnim.HAPPY, HERES_MILLION)
            payForever()
        } else {
            chatPlayer(ChatAnim.NEUTRAL, NEVER_MIND)
        }
    }

    /** Option 1: the fee, if not yet paid. */
    private suspend fun Dialogue.door() = with(SanibochLines) {
        chatPlayer(ChatAnim.QUIZ, CAN_I_GO)
        if (paid(player) != 0) {
            chatNpc(ChatAnim.HAPPY, ALREADY_GIVEN)
            return@with
        }
        chatNpc(ChatAnim.NEUTRAL, CHARGE)
        if (coins(player) < FEE) {
            when (choice(NOT_THAT_MUCH, PERMANENT, NEVER_MIND)) {
                1 -> {
                    chatPlayer(ChatAnim.SAD, NOT_THAT_MUCH)
                    chatNpc(ChatAnim.ANGRY, RIFF_RAFF)
                }
                2 -> permanent()
                else -> chatPlayer(ChatAnim.NEUTRAL, NEVER_MIND)
            }
            return@with
        }
        while (true) {
            when (choice(HERES_875, WHY_WORTH, PERMANENT, NEVER_MIND)) {
                1 -> {
                    chatPlayer(ChatAnim.NEUTRAL, HERES_875)
                    payOnce()
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, WHY_WORTH)
                    chatNpc(ChatAnim.NEUTRAL, WORTH_1)
                    chatNpc(ChatAnim.NEUTRAL, WORTH_2)
                    continue
                }
                3 -> permanent()
                else -> chatPlayer(ChatAnim.NEUTRAL, NEVER_MIND)
            }
            return@with
        }
    }

    /** His talk. */
    internal suspend fun Dialogue.saniboch() = with(SanibochLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        while (true) {
            when (choice(CAN_I_GO, WHERE_LEAD, IMPRESSED, GOOD_DAY)) {
                1 -> door()
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, WHERE_LEAD)
                    chatNpc(ChatAnim.NEUTRAL, LEAD_1)
                    chatNpc(ChatAnim.NEUTRAL, LEAD_2)
                    continue
                }
                3 -> {
                    chatPlayer(ChatAnim.HAPPY, IMPRESSED)
                    chatNpc(ChatAnim.NEUTRAL, FYBURGLARS)
                    continue
                }
                else -> chatPlayer(ChatAnim.HAPPY, GOOD_DAY)
            }
            return@with
        }
    }

    /** His Pay option, and the shut entrance's. */
    internal suspend fun Dialogue.payOption() = with(SanibochLines) {
        val paid = paid(player)
        when {
            paid and PAID_FOREVER != 0 -> chatNpc(ChatAnim.HAPPY, NICE_COINS)
            paid and PAID_ONCE != 0 -> {
                chatNpc(ChatAnim.HAPPY, LOVELY_COINS)
                if (choice(OKAY_THANKS, PERMANENT) == 1) chatPlayer(ChatAnim.HAPPY, OKAY_THANKS) else permanent()
            }
            coins(player) < FEE -> chatNpc(ChatAnim.ANGRY, RIFF_RAFF)
            coins(player) < FOREVER -> if (choice(YES, NO, title = text(PAY_TITLE)) == 1) payOnce()
            else -> when (choice(PAY_ONCE, PAY_FOREVER, CANCEL)) {
                1 -> payOnce()
                2 -> payForever()
            }
        }
    }

    /** The entrance's Enter: in, using up a one-visit fee. */
    internal fun enter(world: World, player: Player) {
        val paid = paid(player)
        if (paid and PAID_FOREVER == 0 && player.vars.types.varbit(DOOR_VARBIT) != null) player.vars.setVarbit(DOOR_VARBIT, paid and PAID_ONCE.inv())
        world.teleport(player, INSIDE.x, INSIDE.z, INSIDE.level)
    }

    /** A vine: chopped through with an axe and the level from [Vine.near], parted by hand from [Vine.deep]. */
    internal suspend fun ScriptContext.vine(world: World, vine: Vine) {
        arriveDelay()
        val fromNear = distance(vine.near) <= distance(vine.deep)
        val to = if (fromNear) vine.deep else vine.near
        if (fromNear) {
            val text = world.textFor(player)
            if (player.skills.currentLevel(Skill.WOODCUTTING) < vine.level) {
                player.message(text[WoodcuttingLines.LEVEL, "level" to vine.level])
                return
            }
            val axe = when (val check = world.woodcutting.data.tools.best(player)) {
                is ToolCheck.Found -> check.tool
                else -> {
                    player.message(text[WoodcuttingLines.NO_AXE])
                    return
                }
            }
            player.anim(axe.anim)
            delay(VINE_SWING_TICKS)
        }
        world.teleport(player, to.x, to.z, to.level)
    }

    private fun ScriptContext.distance(tile: Destination) = maxOf(abs(player.x - tile.x), abs(player.z - tile.z))
}

fun registerBrimhavenDungeon(world: World) {
    with(BrimhavenDungeon) {
        world.handlers.onNpc(SANIBOCH, TALK_TO) { op -> world.dialogues.start(this, op.npc) { saniboch() } }
        world.handlers.onNpc(SANIBOCH, PAY) { op -> world.dialogues.start(this, op.npc) { payOption() } }
        world.handlers.onLoc(ENTRANCE, ENTER) {
            arriveDelay()
            if (paid(player) != 0) {
                enter(world, player)
                return@onLoc
            }
            val saniboch = world.npcs.all.firstOrNull { it.type.id == SANIBOCH && it.level == player.level && maxOf(abs(it.x - player.x), abs(it.z - player.z)) <= 16 }
            if (saniboch != null) world.dialogues.start(this, saniboch) { payOption() }
        }
        world.handlers.onLoc(EXIT, LEAVE) {
            arriveDelay()
            world.teleport(player, OUTSIDE.x, OUTSIDE.z, OUTSIDE.level)
        }
        for (vine in VINES) world.handlers.onLoc(vine.loc, CHOP) { vine(world, vine) }
        for ((stairs, to) in STAIRS) world.handlers.onLoc(stairs, CLIMB) {
            arriveDelay()
            world.teleport(player, to.x, to.z, to.level)
        }
    }
}
