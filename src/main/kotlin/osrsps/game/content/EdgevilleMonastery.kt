package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.math.abs
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.zone.ZoneLoc
import osrsps.game.script.ScriptContext

/** The Edgeville monks' lines, from the Wiki's Transcript:Monk (its Edgeville Monastery section). */
object EdgevilleMonkLines : Lines("edgeville_monk", "Transcript:Monk") {
    val GREETING = line("greeting", "his first line")
    val HEAL = line("heal", "option 1: asking to be healed, which the player then says")
    val HEAL_YES = line("heal_yes", "option 1: he agrees")
    val HEALED = line("healed", "option 1: the box as the player is healed")
    val OUT_OF_WAY = line("out_of_way", "option 2: the remark about the monastery's spot, which the player then says")
    val OUT_OF_WAY_REPLY = line("out_of_way_reply", "option 2: his answer")
    val GET_IN = line("get_in", "option 3: how to get in, which the player then says")
    val GET_IN_REPLY = line("get_in_reply", "option 3: he sends the player to Abbot Langley")
    val LADDER = line("ladder", "the south-western ladder, not yet one of the order: a monk stops the player")
}

/** Abbot Langley's lines, from the Wiki's Transcript:Abbot Langley. */
object AbbotLangleyLines : Lines("abbot_langley", "Transcript:Abbot Langley") {
    val GREETING = line("greeting", "his first line")
    val HEAL = line("heal", "option 1: asking to be healed, which the player then says")
    val HEAL_YES = line("heal_yes", "option 1: his answer")
    val HEALED = line("healed", "option 1: the box as the player feels a little better")
    val WHERE = line("where", "option 2: where the player is, which the player then says")
    val WHERE_REPLY = line("where_reply", "option 2: his answer")
    val ACCESS = line("access", "option 3, not yet one of the order: going upstairs, which the player then says")
    val ACCESS_REPLY = line("access_reply", "option 3: only members of the order may go up")
    val MEMBERS_ONLY = line("members_only", "the south-eastern ladder, not yet one of the order: his line")
    val JOIN = line("join", "the join menu, option 1: asking to join, which the player then says")
    val TOO_LOW = line("too_low", "join, under 31 Prayer: he refuses")
    val REQUIREMENT = line("requirement", "join, under 31 Prayer: the box giving the level needed")
    val ACCEPTED = line("accepted", "join, 31 Prayer or more: he lets the player in")
    val SORRY = line("sorry", "the join menu, option 2: an apology, which the player then says")
    val SHIELDS = line("shields", "option 4: the spirit shields, which the player then says")
    val SHIELDS_REPLY = line("shields_reply", "option 4: his answer")
    val SIGILS = line("sigils", "option 4: the player asks for the sigils back")
    val SHIELDS_WARNING = line("shields_warning", "option 4: he can manage it, but only the sigil comes back")
    val SHIELDS_WARNING_2 = line("shields_warning_2", "option 4: break all the shields in your inventory now?")
    val YES = line("yes", "option 4, the yes/no menu: option 1")
    val NO = line("no", "option 4, the yes/no menu: option 2, which the player then says")
    val BROKEN = line("broken", "option 4, yes: the box as the shields are broken, sigils kept")
}

/** Brother Jered's lines, from the Wiki's Transcript:Brother Jered. */
object BrotherJeredLines : Lines("brother_jered", "Transcript:Brother Jered") {
    val HELP = line("help", "option 1: asking him to help an adventurer, which the player then says")
    val PRAISE = line("praise", "option 2: the praise, which the player then says")
    val PRAISE_REPLY = line("praise_reply", "option 2: his answer")
    val TOPICS = line("topics", "help, no unblessed symbol: what he can help with")
    val SYMBOLS = line("symbols", "the holy symbols option")
    val SYMBOLS_REPLY = line("symbols_reply", "holy symbols: how blessing works")
    val CAPE = line("cape", "the Skillcape of Prayer option")
    val CAPE_1 = line("cape_1", "the Skillcape of Prayer: first box")
    val CAPE_2 = line("cape_2", "the Skillcape of Prayer: second box, ending with anything else?")
    val NOTHING_ELSE = line("nothing_else", "anything else, option 2: no, which the player then says")
    val HAS_SYMBOL = line("has_symbol", "help, carrying an unblessed symbol: what he can do")
    val BLESS = line("bless", "carrying an unblessed symbol: the bless option, which the player then says")
    val BLESSED = line("blessed", "the box as the symbol is blessed")
    val NO_THANKS = line("no_thanks", "carrying an unblessed symbol: the decline option, which the player then says")
    val MASTER = line("master", "help, 99 Prayer: he offers the cape")
    val TOO_DEAR = line("too_dear", "99 Prayer menus: the decline, which the player then says")
    val TOO_DEAR_REPLY = line("too_dear_reply", "the decline: his answer")
    val WANT_CAPE = line("want_cape", "99 Prayer menu: accept, which the player then says")
    val PRICE = line("price", "the cape's price: a donation of 99,000 coins")
    val DONATE = line("donate", "the price menu: agreeing to give, which the player then says")
    val NO_MONEY = line("no_money", "donate, under 99,000 coins: the player's line")
    val NO_MONEY_REPLY = line("no_money_reply", "donate, under 99,000 coins: his answer")
    val NO_SPACE = line("no_space", "donate, without room for the cape and hood")
    val SOLD = line("sold", "donate: his line as the cape and hood are handed over")
}

/** Brother Althric's lines, from the Wiki's Transcript:Brother Althric (its standard dialogue). */
object BrotherAlthricLines : Lines("brother_althric", "Transcript:Brother Althric") {
    val ROSES = line("roses", "the player's line on the rosebushes")
    val ROSES_REPLY = line("roses_reply", "his answer")
}

private val logger = KotlinLogging.logger {}

/**
 * Edgeville Monastery (Edgeville piece step 4), from the OSRS Wiki's Edgeville Monastery, Monk, Abbot Langley and the
 * transcripts of its people, read 2026-11-01:
 * - Monks (2579) and Abbot Langley (2577) heal on request: 2 and a fifth of the Hitpoints level (the Monk page); that
 *   the Abbot heals as much is guessed (his transcript says only "a little better").
 * - The upstairs is the order's: Abbot Langley lets a player join at 31 Prayer, kept on varbit 4907 (monastery_enter;
 *   that it is the joined flag is inferred from its name). Until then the south-eastern ladder brings the Abbot's join
 *   menu and the south-western one a monk's line; once joined both climb.
 * - Brother Jered (2578) blesses an unblessed symbol (1716) into a holy symbol (1718) and, at 99 Prayer, sells the
 *   Skillcape of Prayer for 99,000 coins (trimmed when another skill is 99, as every skillcape seller here).
 * - Brother Althric (1397) talks about his roses, as after Garden of Tranquillity.
 * - The upstairs altar (2640) recharges prayer to the level and 2 above it (the Wiki's +2).
 * - Abbot Langley's spirit shields option breaks each spectral, arcane or elysian spirit shield in the backpack,
 *   giving its sigil back (the transcript: the shield is destroyed, the sigil kept). With none, the yes does nothing;
 *   guessed.
 * Guessed: every chathead expression; the heal's rounding (down); the climb animation.
 */
object EdgevilleMonastery {
    const val MONK = 2579
    const val ABBOT_LANGLEY = 2577
    const val BROTHER_JERED = 2578
    const val BROTHER_ALTHRIC = 1397
    const val TALK_TO = 1

    /** The ground floor's two ladders (3046, 3483 south-west and 3057, 3483 south-east); the tops are 16679. */
    const val LADDER = 2641
    const val SOUTH_EAST_LADDER_X = 3057
    const val ALTAR = 2640
    const val PRAY_AT = 1

    /** monastery_enter: 1 once the player has joined the order. */
    const val VARBIT_JOINED = 4907
    const val JOIN_LEVEL = 31

    /** The altar's bonus above the Prayer level. */
    const val ALTAR_BONUS = 2

    const val UNBLESSED_SYMBOL = 1716
    const val HOLY_SYMBOL = 1718

    /** skillcape_prayer, its trimmed one and skillcape_prayer_hood. */
    const val PRAYER_CAPE = 9759
    const val PRAYER_CAPE_TRIMMED = 9760
    const val PRAYER_HOOD = 9761
    const val COINS = 995
    const val CAPE_PRICE = 99_000
    const val MAX_LEVEL = 99

    /** Spectral, arcane and elysian spirit shields, each to its sigil. */
    val SIGILS = mapOf(12821 to 12823, 12825 to 12827, 12817 to 12819)

    /** What a monk's (and the Abbot's) heal gives: 2 and a fifth of the Hitpoints level, rounded down. */
    fun healAmount(hitpointsLevel: Int): Int = 2 + hitpointsLevel / 5

    fun joined(player: Player): Boolean = player.vars.types.varbit(VARBIT_JOINED) != null && player.vars.getVarbit(VARBIT_JOINED) == 1

    private fun heal(player: Player) {
        val level = player.skills.baseLevel(Skill.HITPOINTS)
        if (player.hitpoints < level) player.hitpoints = minOf(level, player.hitpoints + healAmount(level))
    }

    internal suspend fun Dialogue.monk() = with(EdgevilleMonkLines) {
        chatNpc(ChatAnim.NEUTRAL, GREETING)
        when (choice(HEAL, OUT_OF_WAY, GET_IN)) {
            1 -> {
                chatPlayer(ChatAnim.NEUTRAL, HEAL)
                chatNpc(ChatAnim.NEUTRAL, HEAL_YES)
                heal(player)
                mesbox(HEALED)
            }
            2 -> {
                chatPlayer(ChatAnim.NEUTRAL, OUT_OF_WAY)
                chatNpc(ChatAnim.NEUTRAL, OUT_OF_WAY_REPLY)
            }
            else -> {
                chatPlayer(ChatAnim.NEUTRAL, GET_IN)
                chatNpc(ChatAnim.NEUTRAL, GET_IN_REPLY)
            }
        }
    }

    internal suspend fun Dialogue.abbot(join: (Player) -> Unit, breakShields: (Player) -> Unit) = with(AbbotLangleyLines) {
        chatNpc(ChatAnim.NEUTRAL, GREETING)
        val member = joined(player)
        val options = if (member) arrayOf(HEAL, WHERE, SHIELDS) else arrayOf(HEAL, WHERE, ACCESS, SHIELDS)
        when (options[choice(*options) - 1]) {
            HEAL -> {
                chatPlayer(ChatAnim.NEUTRAL, HEAL)
                chatNpc(ChatAnim.NEUTRAL, HEAL_YES)
                heal(player)
                mesbox(HEALED)
            }
            WHERE -> {
                chatPlayer(ChatAnim.QUIZ, WHERE)
                chatNpc(ChatAnim.NEUTRAL, WHERE_REPLY)
            }
            ACCESS -> {
                chatPlayer(ChatAnim.QUIZ, ACCESS)
                chatNpc(ChatAnim.NEUTRAL, ACCESS_REPLY)
                joinMenu(join)
            }
            else -> {
                chatPlayer(ChatAnim.QUIZ, SHIELDS)
                chatNpc(ChatAnim.NEUTRAL, SHIELDS_REPLY)
                chatPlayer(ChatAnim.NEUTRAL, SIGILS)
                chatNpc(ChatAnim.NEUTRAL, SHIELDS_WARNING)
                chatNpc(ChatAnim.NEUTRAL, SHIELDS_WARNING_2)
                if (choice(YES, NO) == 1) {
                    breakShields(player)
                    mesbox(BROKEN)
                } else {
                    chatPlayer(ChatAnim.NEUTRAL, NO)
                }
            }
        }
    }

    /** The Abbot's join menu: from his option 3 and from the south-eastern ladder. */
    internal suspend fun Dialogue.joinMenu(join: (Player) -> Unit) = with(AbbotLangleyLines) {
        if (choice(JOIN, SORRY) == 2) {
            chatPlayer(ChatAnim.SAD, SORRY)
            return@with
        }
        chatPlayer(ChatAnim.QUIZ, JOIN)
        if (player.skills.baseLevel(Skill.PRAYER) < JOIN_LEVEL) {
            chatNpc(ChatAnim.NEUTRAL, TOO_LOW)
            mesbox(REQUIREMENT)
        } else {
            join(player)
            chatNpc(ChatAnim.HAPPY, ACCEPTED)
        }
    }

    internal suspend fun Dialogue.jered(swap: (Player, Int, Int) -> Unit, give: (Player, Int) -> Unit, takeCoins: (Player, Int) -> Unit) = with(BrotherJeredLines) {
        if (choice(HELP, PRAISE) == 2) {
            chatPlayer(ChatAnim.HAPPY, PRAISE)
            chatNpc(ChatAnim.HAPPY, PRAISE_REPLY)
            return@with
        }
        chatPlayer(ChatAnim.QUIZ, HELP)
        val master = player.skills.baseLevel(Skill.PRAYER) >= MAX_LEVEL
        when {
            // A symbol comes first so a 99 can still have it blessed (Wiki, Unblessed symbol); the cape option then
            // leads to the sale. Inferred: the Wiki's transcript doesn't show a 99 carrying a symbol.
            player.inventory.count(UNBLESSED_SYMBOL) > 0 -> {
                chatNpc(ChatAnim.NEUTRAL, HAS_SYMBOL)
                when (choice(BLESS, CAPE, NO_THANKS)) {
                    1 -> {
                        chatPlayer(ChatAnim.HAPPY, BLESS)
                        swap(player, UNBLESSED_SYMBOL, HOLY_SYMBOL)
                        objbox(HOLY_SYMBOL, BLESSED)
                    }
                    2 -> if (master) master(give, takeCoins) else cape()
                    else -> chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
                }
            }
            master -> master(give, takeCoins)
            else -> topics()
        }
    }

    private suspend fun Dialogue.topics(): Unit = with(BrotherJeredLines) {
        chatNpc(ChatAnim.NEUTRAL, TOPICS)
        if (choice(SYMBOLS, CAPE) == 1) {
            chatPlayer(ChatAnim.QUIZ, SYMBOLS)
            chatNpc(ChatAnim.NEUTRAL, SYMBOLS_REPLY)
        } else {
            cape()
        }
    }

    private suspend fun Dialogue.cape(): Unit = with(BrotherJeredLines) {
        chatPlayer(ChatAnim.QUIZ, CAPE)
        chatNpc(ChatAnim.NEUTRAL, CAPE_1)
        chatNpc(ChatAnim.QUIZ, CAPE_2)
        if (choice(HELP, NOTHING_ELSE) == 1) {
            chatPlayer(ChatAnim.QUIZ, HELP)
            topics()
        } else {
            chatPlayer(ChatAnim.NEUTRAL, NOTHING_ELSE)
        }
    }

    private suspend fun Dialogue.master(give: (Player, Int) -> Unit, takeCoins: (Player, Int) -> Unit) = with(BrotherJeredLines) {
        chatNpc(ChatAnim.HAPPY, MASTER)
        if (choice(TOO_DEAR, WANT_CAPE) == 1) return@with tooDear()
        chatPlayer(ChatAnim.HAPPY, WANT_CAPE)
        chatNpc(ChatAnim.NEUTRAL, PRICE)
        if (choice(TOO_DEAR, DONATE) == 1) return@with tooDear()
        chatPlayer(ChatAnim.HAPPY, DONATE)
        val coins = player.inventory.count(COINS)
        when {
            coins < CAPE_PRICE -> {
                chatPlayer(ChatAnim.SAD, NO_MONEY)
                chatNpc(ChatAnim.NEUTRAL, NO_MONEY_REPLY)
            }
            // The coins' slot frees up when they are all handed over.
            player.inventory.freeSlots + (if (coins == CAPE_PRICE.toLong()) 1 else 0) < 2 -> chatNpc(ChatAnim.NEUTRAL, NO_SPACE)
            else -> {
                takeCoins(player, CAPE_PRICE)
                val trimmed = Skill.entries.any { it != Skill.PRAYER && player.skills.baseLevel(it) >= MAX_LEVEL }
                give(player, if (trimmed) PRAYER_CAPE_TRIMMED else PRAYER_CAPE)
                give(player, PRAYER_HOOD)
                chatNpc(ChatAnim.HAPPY, SOLD)
            }
        }
    }

    private suspend fun Dialogue.tooDear() = with(BrotherJeredLines) {
        chatPlayer(ChatAnim.SAD, TOO_DEAR)
        chatNpc(ChatAnim.NEUTRAL, TOO_DEAR_REPLY)
    }

    internal suspend fun Dialogue.althric() = with(BrotherAlthricLines) {
        chatPlayer(ChatAnim.HAPPY, ROSES)
        chatNpc(ChatAnim.HAPPY, ROSES_REPLY)
    }

    /** Pray-at on the upstairs altar: prayer back to the level and [ALTAR_BONUS] over, a boost above that kept. */
    internal suspend fun ScriptContext.prayAtMonastery(world: World) {
        arriveDelay()
        val boosted = player.skills.baseLevel(Skill.PRAYER) + ALTAR_BONUS
        val full = player.skills.currentLevel(Skill.PRAYER) >= boosted
        Church.pray(player)
        if (player.skills.currentLevel(Skill.PRAYER) < boosted) player.skills.setCurrentLevel(Skill.PRAYER, boosted)
        world.message(player, if (full) AltarLines.FULL else AltarLines.RECHARGE)
    }

    /**
     * Climb-up on a ground-floor ladder: up for one of the order, else the Abbot (south-east, with his join menu) or the
     * nearest monk (south-west) stops them. With neither in the building, only the line, as the type's chathead.
     */
    internal suspend fun ScriptContext.ladder(world: World, loc: ZoneLoc, join: (Player) -> Unit) {
        if (joined(player)) return climbLadder(world, loc, 1, Climbing.ANIM_REACH_FOR_LADDER)
        val abbotLadder = loc.x == SOUTH_EAST_LADDER_X
        val typeId = if (abbotLadder) ABBOT_LANGLEY else MONK
        val line = if (abbotLadder) AbbotLangleyLines.MEMBERS_ONLY else EdgevilleMonkLines.LADDER
        val npc = world.npcs.all.filter { it.type.id == typeId && it.level == loc.level && abs(it.x - loc.x) <= NEAR && abs(it.z - loc.z) <= NEAR }
            .minByOrNull { abs(it.x - loc.x) + abs(it.z - loc.z) }
        if (npc != null) {
            world.dialogues.start(this, npc) {
                chatNpc(ChatAnim.NEUTRAL, line)
                if (abbotLadder) joinMenu(join)
            }
            return
        }
        val type = world.npcTypes[typeId] ?: return logger.warn { "No NPC type $typeId in the cache" }
        world.dialogues.start(this) { chatNpc(type, type.name, ChatAnim.NEUTRAL, line) }
    }

    /** How far from a ladder the Abbot or a monk may be to stop the player: the monastery's size. */
    private const val NEAR = 16
}

/** The monastery's people, ladders and altar. */
fun registerEdgevilleMonastery(world: World) {
    with(EdgevilleMonastery) {
        val give: (Player, Int) -> Unit = { player, obj ->
            world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } ?: logger.warn { "No item $obj in the cache" }
        }
        val swap: (Player, Int, Int) -> Unit = { player, from, to ->
            val slot = player.inventory.contents().firstOrNull { it.second.id == from }?.first
            if (slot != null) {
                player.inventory.remove(slot, 1)
                give(player, to)
            }
        }
        val join: (Player) -> Unit = { it.vars.setVarbit(VARBIT_JOINED, 1) }
        val breakShields: (Player) -> Unit = { player ->
            for ((slot, held) in player.inventory.contents()) {
                val sigil = SIGILS[held.id] ?: continue
                player.inventory.remove(slot, 1)
                give(player, sigil)
            }
        }
        val takeCoins: (Player, Int) -> Unit = { player, count ->
            var left = count
            for ((slot, held) in player.inventory.contents()) {
                if (left == 0) break
                if (held.id == COINS) left -= player.inventory.remove(slot, left)
            }
        }
        world.handlers.onNpc(MONK, TALK_TO) { op -> world.dialogues.start(this, op.npc) { monk() } }
        world.handlers.onNpc(ABBOT_LANGLEY, TALK_TO) { op -> world.dialogues.start(this, op.npc) { abbot(join, breakShields) } }
        world.handlers.onNpc(BROTHER_JERED, TALK_TO) { op -> world.dialogues.start(this, op.npc) { jered(swap, give, takeCoins) } }
        world.handlers.onNpc(BROTHER_ALTHRIC, TALK_TO) { op -> world.dialogues.start(this, op.npc) { althric() } }
        world.handlers.onLoc(LADDER, 1) { op -> ladder(world, op.loc!!, join) }
        world.handlers.onLoc(ALTAR, PRAY_AT) { prayAtMonastery(world) }
        world.handlers.onObjOnLoc(ALTAR) { world.message(player, AltarLines.WRATH) }
    }
}
