package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.skill.MakeLines

/** The Sawmill operator's lines; from the Wiki's Transcript:Sawmill operator. */
object SawmillLines : Lines("sawmill_operator", "Transcript:Sawmill operator") {
    val HELLO = line("hello", "Talk-to, his first line: planks, or other housing supplies")
    val MAKE_PLANKS = line("make_planks", "his menu, option 1, which the player then says")
    val SUPPLIES = line("supplies", "his menu, option 2, which the player then says")
    val GOOD = line("good", "his menu, option 3, which the player then says")
    val OF_COURSE = line("of_course", "after option 2, before his shop opens")
    val COME_BACK = line("come_back", "after option 3: his last line")
    val MORE_LOGS = line("more_logs", "a plank chosen with none of its logs in the inventory")
    val COST = line("cost", "a plank chosen without the coins for every log: the fee for each (guessed: the transcript's [X])", "cost")
}

/** Tolna's lines; from the Wiki's Transcript:Tolna, after A Soul's Bane. */
object TolnaLines : Lines("tolna", "Transcript:Tolna") {
    val TRAINING = line("training", "the player asks about the training area")
    val RIFT = line("rift", "his reply: the rift and its four kinds of beasts")
    val NOT_MANY = line("not_many", "the player: no problems beating them before")
    val HARDER = line("harder", "his warning: harder now, no treasure")
    val THANKS = line("thanks", "the player's thanks")
}

/** Our own line for Tolna's rift, and the warning signs' text (Transcript:Warning sign (Tolna's rift)). */
object TolnaRiftLines : Lines("tolna_rift", "Transcript:Warning sign (Tolna's rift)") {
    val SIGN = line("sign", "Read on a warning sign: the text carved into it")
    val DUNGEON_LATER = line("dungeon_later", "Enter on the rift: our own line, as the rift's dungeon isn't built yet")
}

/**
 * Misthalin piece 7 step 6: the Lumber Yard north-east of Varrock, the Earth Altar's ruins south of it and the entrance
 * to Tolna's rift. Sources: the OSRS Wiki's Lumber Yard, Sawmill, Sawmill operator, Broken fence (Lumber Yard), Crate
 * (Gertrude's Cat), Tolna, Tolna's rift, Warning sign (Tolna's rift) and Rift (Tolna's rift) pages, read 2026-11-10, and
 * the 242 cache. The NPCs are in lumber-yard.json; the broken fence is an obstacle in agility.json; the Construction
 * supplies shop is in shops.json.
 *
 * - The Sawmill operator (`poh_sawmill_opp` 3101) in his room on the yard's south wall: Talk-to follows the transcript,
 *   with option 2 opening his shop. Buy-plank (and the talk's option 1) opens the Make-X box with the seven planks, as
 *   live has since 16 April 2020 (Sawmill page); each chosen plank takes one unnoted log of its kind and the Wiki's fee
 *   (100, 250, 500, 1500, 2500, 5000 and 7500 coins). With none of the logs he says so; with too few coins he makes as
 *   many as the coins pay for and says what each costs (inferred from the transcript's "for all of them").
 * - The six mewing crates (`kittens_mew` 3499, NPCs) and the yard's crates and barrels (2620, 2619): Search finds
 *   nothing once Gertrude's Cat is done (inferred), the crates with the crate line, the barrels with "Nothing
 *   interesting happens" (guessed).
 * - The Earth Altar's ruins (34816, a multiloc, and 29099, its Enter form): Enter and an earth talisman or tiara refuse,
 *   as the Water Altar's do, until the Runecraft skill thread builds the altar.
 * - Tolna (`soulbane_tolna_multi` 2010, which shows `soulbane_tolna_top` 1058 beside the rift after the quest) talks as
 *   the Wiki's transcript after A Soul's Bane has it. Launa isn't spawned: the Wiki's Launa page says she went home with
 *   Brana after the quest, and her multi-NPC shows nothing once it's done. The rift's holes and its rope (13967 to
 *   13978) refuse with [TolnaRiftLines.DUNGEON_LATER] until the dungeon is built; the five warning signs (the multiloc
 *   14001, showing 14002) read as the Wiki says.
 *
 * Every account has done A Soul's Bane, so [osrsps.game.Quests.AFTER_QUESTS] sets the varbits that show Tolna at the top,
 * the rope in the rift and the warning signs (inferred from their names and the Wiki's post-quest pictures).
 * Left out: sawmill vouchers (Forestry), Gertrude's cat and kittens (home with Gertrude after the quest), the clue crate.
 */
object LumberYard {
    const val SAWMILL_OPERATOR = 3101
    const val TALK_TO = 1
    const val BUY_PLANK = 3

    const val MEWING_CRATE = 3499
    const val CRATE = 2620
    const val BARREL = 2619
    const val SEARCH = 1

    /** `soulbane_tolna_multi`, spawned, and `soulbane_tolna_top`, what it shows after A Soul's Bane (the one clicked). */
    const val TOLNA = 2010
    const val TOLNA_SHOWN = 1058

    /** `earthtemple_ruined`, the multiloc, and `earthtemple_ruined_new`, what it shows with Enter. */
    const val EARTH_RUINS = 34816
    const val EARTH_RUINS_ENTER = 29099
    const val EARTH_TALISMAN = 1440
    const val EARTH_TIARA = 5535
    const val ENTER = 1

    /** The rift's holes: `soulbane_falloff1` to `_10`, the rope's multiloc and the rope (13967 to 13978). */
    val RIFTS = (13967..13978).toList()
    /** `soulbane_warning_sign_multi`, on the map, which shows `soulbane_warning_sign` (14002) after the quest. */
    const val WARNING_SIGN = 14001
    const val WARNING_SIGN_SHOWN = 14002
    const val READ = 1

    const val COINS = 995

    /** A plank the sawmill makes: from [log], for [fee] coins each (the Wiki's Sawmill page). */
    data class Plank(val plank: Int, val log: Int, val fee: Int)

    val PLANKS = listOf(
        Plank(960, 1511, 100), // woodplank from logs
        Plank(8778, 1521, 250), // plank_oak
        Plank(8780, 6333, 500), // plank_teak
        Plank(8782, 6332, 1500), // plank_mahogany
        Plank(31432, 32904, 2500), // plank_camphor
        Plank(31435, 32907, 5000), // plank_ironwood
        Plank(31438, 32910, 7500), // plank_rosewood
    )

    internal suspend fun Dialogue.sawmill(world: World) = with(SawmillLines) {
        chatNpc(ChatAnim.HAPPY, HELLO)
        when (choice(MAKE_PLANKS, SUPPLIES, GOOD)) {
            1 -> {
                chatPlayer(ChatAnim.NEUTRAL, MAKE_PLANKS)
                planks(world)
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, SUPPLIES)
                chatNpc(ChatAnim.HAPPY, OF_COURSE)
                world.shopTypes.all.firstOrNull { SAWMILL_OPERATOR in it.npcs }?.let { world.shopScreen.open(player, it) }
            }
            else -> {
                chatPlayer(ChatAnim.NEUTRAL, GOOD)
                chatNpc(ChatAnim.HAPPY, COME_BACK)
            }
        }
    }

    /** The Make-X box of planks, then the logs and coins swapped for what was chosen. */
    internal suspend fun Dialogue.planks(world: World) {
        val (index, quantity) = makeX(MakeLines.TITLE, PLANKS.map { it.plank }, MAX_QUANTITY)
        val plank = PLANKS.getOrNull(index) ?: return
        val logs = player.inventory.count(plank.log).toInt()
        if (logs == 0) return chatNpc(ChatAnim.NEUTRAL, SawmillLines.MORE_LOGS)
        val wanted = minOf(quantity, logs)
        val coins = player.inventory.count(COINS).toInt()
        val made = minOf(wanted, coins / plank.fee)
        val type = world.objTypes[plank.plank]
        if (made > 0 && type != null) {
            Ned.take(player, plank.log, made)
            AlKharidTraders.pay(player, made * plank.fee)
            player.inventory.add(type, made)
        }
        if (made < wanted) chatNpc(ChatAnim.NEUTRAL, SawmillLines.COST, "cost" to plank.fee)
    }

    internal suspend fun Dialogue.tolna() = with(TolnaLines) {
        chatPlayer(ChatAnim.QUIZ, TRAINING)
        chatNpc(ChatAnim.NEUTRAL, RIFT)
        chatPlayer(ChatAnim.NEUTRAL, NOT_MANY)
        chatNpc(ChatAnim.SCARED, HARDER)
        chatPlayer(ChatAnim.HAPPY, THANKS)
    }

    /** The most the Make-X box offers: a full inventory of logs. */
    const val MAX_QUANTITY = 28
}

/** The sawmill, the crates and barrels, the Earth Altar's ruins, Tolna and his rift. */
fun registerLumberYard(world: World) {
    with(LumberYard) {
        world.handlers.onNpc(SAWMILL_OPERATOR, TALK_TO) { op -> world.dialogues.start(this, op.npc) { sawmill(world) } }
        world.handlers.onNpc(SAWMILL_OPERATOR, BUY_PLANK) { op -> world.dialogues.start(this, op.npc) { planks(world) } }
        world.npcInteractions.reachOverCounter(SAWMILL_OPERATOR)
        world.handlers.onNpc(MEWING_CRATE, SEARCH) { world.message(player, SearchLines.CRATE_EMPTY) }
        registerSearch(world, CRATE, SEARCH, SearchLines.CRATE_EMPTY)
        registerSearch(world, BARREL, SEARCH, GameLines.NOTHING_INTERESTING)
        for (ruins in listOf(EARTH_RUINS, EARTH_RUINS_ENTER)) {
            world.handlers.onLoc(ruins, ENTER) { world.message(player, LumbridgeSwampLines.RUNECRAFT_CLOSED) }
            for (obj in listOf(EARTH_TALISMAN, EARTH_TIARA)) {
                world.handlers.onObjOnLoc(ruins, obj) { world.message(player, LumbridgeSwampLines.RUNECRAFT_CLOSED) }
            }
        }
        world.handlers.onNpc(TOLNA_SHOWN, TALK_TO) { op -> world.dialogues.start(this, op.npc) { tolna() } }
        for (rift in RIFTS) world.handlers.onLoc(rift, ENTER) { world.message(player, TolnaRiftLines.DUNGEON_LATER) }
        for (sign in listOf(WARNING_SIGN, WARNING_SIGN_SHOWN)) {
            world.handlers.onLoc(sign, READ) { world.dialogues.start(this) { mesbox(TolnaRiftLines.SIGN) } }
        }
    }
}
