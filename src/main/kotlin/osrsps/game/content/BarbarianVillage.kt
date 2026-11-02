package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.zone.ZoneLoc

/** Peksa's lines; the words are in the dialogue file, from the Wiki's Transcript:Peksa. */
object PeksaLines : Lines("peksa", "Transcript:Peksa") {
    val GREETING = line("greeting", "his first line: buying or selling a helmet?")
    val YES = line("yes", "option 1: yes, and the shop opens with no line from the player")
    val NO = line("no", "option 2: no, which the player then says")
    val NO_REPLY = line("no_reply", "option 2: his farewell, inviting the player back")
}

/** Tassie Slipcast's lines, from the Wiki's Transcript:Tassie Slipcast. */
object TassieSlipcastLines : Lines("tassie_slipcast", "Transcript:Tassie Slipcast") {
    val USE_WHEEL = line("use_wheel", "her first box: use the wheel, put finished pots in the kiln")
    val TIDY = line("tidy", "her second box: tidy up afterwards")
}

/** Hunding's lines, from the Wiki's Transcript:Hunding. */
object HundingLines : Lines("hunding", "Transcript:Hunding") {
    val HELLO = line("hello", "the player's opening line")
    val WHAT_DOING = line("what_doing", "his answer: what is the player doing here?")
    val NOTHING_MUCH = line("nothing_much", "option 1, which the player then says")
    val LONGHALL = line("longhall", "option 1: his answer, sending the player to the longhall")
    val BEAR_IN_MIND = line("bear_in_mind", "option 1: the player's last line")
    val EXPLORING = line("exploring", "option 2, which the player then says")
    val CANNOT_HOPE = line("cannot_hope", "option 2: his answer, before the second menu")
    val TRIBE = line("tribe", "second menu, option 1: the tribe, as the menu shows it")
    val TRIBE_SAID = line("tribe_said", "second menu, option 1: what the player says (asking him to tell more)")
    val TRIBE_1 = line("tribe_1", "the tribe: his first box (not so ignorant)")
    val TRIBE_2 = line("tribe_2", "the tribe: second box (our elders remember)")
    val TRIBE_3 = line("tribe_3", "the tribe: third box")
    val TRIBE_4 = line("tribe_4", "the tribe: fourth box")
    val TRIBE_5 = line("tribe_5", "the tribe: fifth box")
    val TRIBE_6 = line("tribe_6", "the tribe: sixth box")
    val TRIBE_7 = line("tribe_7", "the tribe: seventh box")
    val TRIBE_8 = line("tribe_8", "the tribe: eighth box")
    val TRIBE_9 = line("tribe_9", "the tribe: ninth box (I have said enough)")
    val THANK_YOU = line("thank_you", "the tribe: the player's thanks")
    val SAVAGES = line("savages", "second menu, option 2, which the player then says")
    val SAVAGES_REPLY = line("savages_reply", "second menu, option 2: his answer")
    val BORED = line("bored", "second menu, option 3, which the player then says")
    val BORED_REPLY = line("bored_reply", "second menu, option 3: his answer, sending the player to the longhall")
    val BORED_BEAR = line("bored_bear", "second menu, option 3: the player's last line")
    val KILL = line("kill", "option 3, which the player then says")
    val KILL_REPLY = line("kill_reply", "option 3: his answer")
}

/** Atlas's lines after Below Ice Mountain (his first paid workout), from the Wiki's Transcript:Atlas. */
object AtlasLines : Lines("atlas", "Transcript:Atlas") {
    val PROVEN = line("proven", "first workout: his first line, praising the player")
    val AGAIN = line("again", "first workout: the player asks to do it again")
    val WHY = line("why", "first workout: he asks why")
    val FUN = line("fun", "first workout: the player says it was fun")
    val FEE = line("fee", "first workout: nobody asked before, with the player's name; the 25,000 coins", "name")
    val PAY_TITLE = line("pay_title", "the menu's title: pay Atlas 25,000 coins to train again?")
    val YES = line("yes", "the menu's first option")
    val NO = line("no", "the menu's second option")
    val LETS_DO = line("lets_do", "yes, with the coins: the player's line")
    val NOT_EASIER = line("not_easier", "yes, with the coins: his warning it won't be easier")
    val CANT_AFFORD = line("cant_afford", "yes, without the coins: his first line")
    val TOO_EXCITED = line("too_excited", "yes, without the coins: the player's apology")
    val COME_BACK = line("come_back", "yes, without the coins: come back when you can pay")

    /** Our own words, not live's: the workout is a cutscene that isn't built, so no coins are taken. */
    val WORKOUT_CLOSED = line("workout_closed", "yes, with the coins: ours, the workout isn't built yet")
}

/** Checkal's lines in Barbarian Village after Below Ice Mountain, from the Wiki's Transcript:Checkal. */
object CheckalLines : Lines("checkal", "Transcript:Checkal") {
    val GLAD = line("glad", "after Below Ice Mountain: his first line, with the player's name", "name")
    val WHY_NOT = line("why_not", "the player's first line")
    val FRIENDS = line("friends", "the player's second line: we were friends")
    val SORRY = line("sorry", "his apology, with the player's name", "name")
    val UNDERSTAND = line("understand", "the player understands")
    val DONT_LET = line("dont_let", "his last line")
}

/** Litara's lines before the Stronghold of Security is finished, from the Wiki's Transcript:Litara. */
object LitaraLines : Lines("litara", "Transcript:Litara") {
    val GREETING = line("greeting", "her first line: the player looks lost")
    val STRONGHOLD = line("stronghold", "option 1: looking for a stronghold, which the player then says")
    val STRONGHOLD_REPLY = line("stronghold_reply", "option 1: her answer, naming the Stronghold of Security")
    val POINTS = line("points", "option 1: the message box, her pointing at the hole")
    val DEEP = line("deep", "option 1: the player: deep and dark")
    val BROTHER = line("brother", "option 1: tell that to my brother")
    val YOUR_BROTHER = line("your_brother", "option 1: the player: your brother?")
    val EXPLORER = line("explorer", "option 1: he's an explorer too")
    val NOT_GOOD = line("not_good", "option 1: the player: that's not good")
    val LOTS = line("lots", "option 1: lots of people have been down there")
    val EYES_OPEN = line("eyes_open", "option 1: the player's last line")
    val PASSING = line("passing", "option 2: just passing through, which the player then says")
}

/** Sigurd's lines, from the Wiki's Transcript:Sigurd. */
object SigurdLines : Lines("sigurd", "Transcript:Sigurd") {
    val HELLO = line("hello", "the player's opening line")
    val HA_HA = line("ha_ha", "his greeting")
    val WHO = line("who", "option 1, which the player then says")
    val GREAT = line("great", "who: his name")
    val WHY_CALLED = line("why_called", "who: the player asks why he's called that")
    val INVENTED = line("invented", "who: he invented the Log Canoe")
    val LOG_CANOE = line("log_canoe", "who: the player: Log Canoe?")
    val COUSINS = line("cousins", "who: him and his cousins")
    val SMACK = line("smack", "who: the player: Smack the Tree?")
    val GAME = line("game", "who: the game, first box")
    val WON = line("won", "who: he won the game")
    val WOKE = line("woke", "who: he woke up hours later")
    val SO_INVENTED = line("so_invented", "who: the player: so you invented")
    val REFINED = line("refined", "who: he refined the design")
    val BRANCHES = line("branches", "who: he cut the branches off, before the yes/no menu")
    val YES = line("yes", "who's menu: yes, into the teaching with no line of its own")
    val NO = line("no", "who's menu: no, which the player then says")
    val TEACH = line("teach", "option 2: canoeing, as the menu shows it")
    val TEACH_SAID = line("teach_said", "teaching: what the player says")
    val LOW_1 = line("low_1", "below 12 Woodcutting: his first box")
    val LOW_2 = line("low_2", "below 12 Woodcutting: second box (level 12 needed)")
    val LOW_3 = line("low_3", "below 12 Woodcutting: third box")
    val SIMPLE = line("simple", "12 Woodcutting or more: quite simple")
    val AXE = line("axe", "12 Woodcutting or more: take your axe to it")
    val LOG_1 = line("log_1", "12 to 26: you can make a log canoe")
    val LOG_2 = line("log_2", "12 to 26: the snooty fella")
    val DUGOUT = line("dugout", "27 to 41: a Dugout canoe")
    val HANDY = line("handy", "42 to 56: you're pretty handy")
    val STABLE = line("stable", "42 to 56: a Stable Dugout")
    val RECKONS = line("reckons", "42 to 56: he reckons his canoes")
    val KNOW = line("know", "57 or more: you look like you know")
    val WHAT_WAKA = line("what_waka", "57 or more: the player: what's a Waka?")
    val HARI_1 = line("hari_1", "57 or more: only ever seen Hari")
    val HARI_2 = line("hari_2", "57 or more: Hari hangs around")
    val HARI_3 = line("hari_3", "57 or more: a nice bloke")
}

/** A village barbarian's lines, from the Wiki's Transcript:Barbarian: one of six, picked at random, before he attacks. */
object BarbarianLines : Lines("barbarian", "Transcript:Barbarian") {
    val RANDOM = listOf(
        line("random_1", "random line 1: wanna fight?"),
        line("random_2", "random line 2: you come for fight?"),
        line("random_3", "random line 3: you look funny"),
        line("random_4", "random line 4: a growl"),
        line("random_5", "random line 5: what you want?"),
        line("random_6", "random line 6: go away"),
    )
}

/** Gunthor the Brave's lines, from the Wiki's Transcript:Gunthor the brave: one of six talks, picked at random, before he attacks. */
object GunthorLines : Lines("gunthor", "Transcript:Gunthor the brave") {
    val COME_FOR_FIGHT = line("come_for_fight", "talk 1: you've come for a fight")
    val WHAT_WANT = line("what_want", "talk 2: his first box, what does the player want?")
    val OFFERING = line("offering", "talk 2: the player asks what he's offering")
    val A_FIGHT = line("a_fight", "talk 2: his answer, a fight")
    val LOOK_ODD = line("look_odd", "talk 3: the player looks odd")
    val WANNA_FIGHT = line("wanna_fight", "talk 4: does the player want to fight?")
    val GROWL = line("growl", "talk 5: a growl")
    val GO_AWAY = line("go_away", "talk 6: go away")
}

/**
 * Barbarian Village's buildings and people (Edgeville piece step 5): the longhall's double doors and its item spawns,
 * Peksa's Helmet Shop, the spinning hut, Tassie Slipcast's studio and the lookout tower, and the talk of Peksa (2872),
 * Tassie Slipcast (1352), Hunding (3063, at the top of the tower), Atlas (10658), Checkal (`bim_checkal_barb` 10645,
 * a multi-NPC showing 10657), Litara (`sos_barb` 3363, showing 3362) and Sigurd (1324, by the canoe station). Loc and
 * NPC ids and tiles are from the live 241 cache and mejrs's live NPC list; the conversations from the Wiki.
 *
 * Every account has every quest done, so Checkal and Atlas talk as after Below Ice Mountain. Atlas's paid workout is a
 * cutscene that isn't built: a player who can pay is told so and keeps the coins. Litara talks as before the Stronghold
 * of Security is finished (the Stronghold is steps 12 to 14). The spinning wheel, potter's wheel and pottery oven work
 * by name ([osrsps.game.skill.Crafting]), the single doors by name ([osrsps.game.loc.Doors]), the chests, boxes and
 * sacks as Lumbridge's and Al Kharid's types, and the fires as every fire. Left out: Sigurd's Store-axe, Hunding's
 * beginner clue, the barbarian anvil (members' Barbarian Smithing) and the Stronghold's entrance (step 12). Expressions
 * and wander ranges are guessed, as the Wiki notes none.
 */
object BarbarianVillage {
    const val PEKSA = 2872
    const val TASSIE_SLIPCAST = 1352
    const val HUNDING = 3063
    const val ATLAS = 10658

    /** bim_checkal: the type Checkal's multi-NPC (bim_checkal_barb 10645) shows, whose options it has. */
    const val CHECKAL = 10657

    /** sos_barb_visible: the type Litara's multi-NPC (sos_barb 3363) shows. */
    const val LITARA = 3362
    const val SIGURD = 1324
    const val TALK_TO = 1

    /** fai_barbarian_large_doubledoor_l/r and their open types: the longhall's front doors (3078 and 3079, 3435). */
    const val LONGHALL_DOOR_LEFT = 11621
    const val LONGHALL_DOOR_LEFT_OPEN = 11625
    const val LONGHALL_DOOR_RIGHT = 11620
    const val LONGHALL_DOOR_RIGHT_OPEN = 11624

    /** ladder_directional and laddertop_directional: the lookout tower (3096 and 3097, 3433), three levels. */
    const val TOWER_LADDER = 17026
    const val TOWER_LADDER_TOP = 16685

    /** fai_barbarian_small_crates, Search: Peksa's shop. */
    const val SMALL_CRATES = 11600

    const val WORKOUT_PRICE = 25_000
    const val COINS = 995

    /** Peksa's talk; true when the player asked to trade. */
    internal suspend fun Dialogue.peksa(): Boolean = with(PeksaLines) {
        chatNpc(ChatAnim.NEUTRAL, GREETING)
        if (choice(YES, NO) == 1) return@with true
        chatPlayer(ChatAnim.NEUTRAL, NO)
        chatNpc(ChatAnim.NEUTRAL, NO_REPLY)
        false
    }

    internal suspend fun Dialogue.tassie() = with(TassieSlipcastLines) {
        chatNpc(ChatAnim.HAPPY, USE_WHEEL)
        chatNpc(ChatAnim.NEUTRAL, TIDY)
    }

    internal suspend fun Dialogue.hunding() = with(HundingLines) {
        chatPlayer(ChatAnim.NEUTRAL, HELLO)
        chatNpc(ChatAnim.ANGRY, WHAT_DOING)
        when (choice(NOTHING_MUCH, EXPLORING, KILL)) {
            1 -> {
                chatPlayer(ChatAnim.NEUTRAL, NOTHING_MUCH)
                chatNpc(ChatAnim.ANGRY, LONGHALL)
                chatPlayer(ChatAnim.NEUTRAL, BEAR_IN_MIND)
            }
            2 -> {
                chatPlayer(ChatAnim.NEUTRAL, EXPLORING)
                chatNpc(ChatAnim.ANGRY, CANNOT_HOPE)
                when (choice(TRIBE, SAVAGES, BORED)) {
                    1 -> {
                        chatPlayer(ChatAnim.QUIZ, TRIBE_SAID)
                        for (box in listOf(TRIBE_1, TRIBE_2, TRIBE_3, TRIBE_4, TRIBE_5, TRIBE_6, TRIBE_7, TRIBE_8, TRIBE_9)) chatNpc(ChatAnim.NEUTRAL, box)
                        chatPlayer(ChatAnim.HAPPY, THANK_YOU)
                    }
                    2 -> {
                        chatPlayer(ChatAnim.NEUTRAL, SAVAGES)
                        chatNpc(ChatAnim.ANGRY, SAVAGES_REPLY)
                    }
                    else -> {
                        chatPlayer(ChatAnim.NEUTRAL, BORED)
                        chatNpc(ChatAnim.ANGRY, BORED_REPLY)
                        chatPlayer(ChatAnim.NEUTRAL, BORED_BEAR)
                    }
                }
            }
            else -> {
                chatPlayer(ChatAnim.ANGRY, KILL)
                chatNpc(ChatAnim.LAUGH, KILL_REPLY)
            }
        }
    }

    /** Atlas's talk as after Below Ice Mountain, before any paid workout (none can be had yet). */
    internal suspend fun Dialogue.atlas() = with(AtlasLines) {
        chatNpc(ChatAnim.HAPPY, PROVEN)
        chatPlayer(ChatAnim.HAPPY, AGAIN)
        chatNpc(ChatAnim.QUIZ, WHY)
        chatPlayer(ChatAnim.HAPPY, FUN)
        chatNpc(ChatAnim.NEUTRAL, FEE, "name" to player.username)
        if (choice(YES, NO, title = text(PAY_TITLE)) != 1) return@with
        if (player.inventory.count(COINS) < WORKOUT_PRICE) {
            chatNpc(ChatAnim.NEUTRAL, CANT_AFFORD)
            chatPlayer(ChatAnim.SAD, TOO_EXCITED)
            chatNpc(ChatAnim.NEUTRAL, COME_BACK)
            return@with
        }
        chatPlayer(ChatAnim.HAPPY, LETS_DO)
        chatNpc(ChatAnim.NEUTRAL, NOT_EASIER)
        mesbox(WORKOUT_CLOSED)
    }

    internal suspend fun Dialogue.checkal() = with(CheckalLines) {
        chatNpc(ChatAnim.HAPPY, GLAD, "name" to player.username)
        chatPlayer(ChatAnim.QUIZ, WHY_NOT)
        chatPlayer(ChatAnim.SAD, FRIENDS)
        chatNpc(ChatAnim.SAD, SORRY, "name" to player.username)
        chatPlayer(ChatAnim.NEUTRAL, UNDERSTAND)
        chatNpc(ChatAnim.NEUTRAL, DONT_LET)
    }

    internal suspend fun Dialogue.litara() = with(LitaraLines) {
        chatNpc(ChatAnim.NEUTRAL, GREETING)
        if (choice(STRONGHOLD, PASSING) == 2) return@with chatPlayer(ChatAnim.NEUTRAL, PASSING)
        chatPlayer(ChatAnim.QUIZ, STRONGHOLD)
        chatNpc(ChatAnim.NEUTRAL, STRONGHOLD_REPLY)
        mesbox(POINTS)
        chatPlayer(ChatAnim.NEUTRAL, DEEP)
        chatNpc(ChatAnim.SAD, BROTHER)
        chatPlayer(ChatAnim.QUIZ, YOUR_BROTHER)
        chatNpc(ChatAnim.SAD, EXPLORER)
        chatPlayer(ChatAnim.SAD, NOT_GOOD)
        chatNpc(ChatAnim.SAD, LOTS)
        chatPlayer(ChatAnim.NEUTRAL, EYES_OPEN)
    }

    internal suspend fun Dialogue.sigurd() = with(SigurdLines) {
        chatPlayer(ChatAnim.HAPPY, HELLO)
        chatNpc(ChatAnim.LAUGH, HA_HA)
        if (choice(WHO, TEACH) == 2) return@with sigurdTeach()
        chatPlayer(ChatAnim.QUIZ, WHO)
        chatNpc(ChatAnim.HAPPY, GREAT)
        chatPlayer(ChatAnim.QUIZ, WHY_CALLED)
        chatNpc(ChatAnim.HAPPY, INVENTED)
        chatPlayer(ChatAnim.QUIZ, LOG_CANOE)
        chatNpc(ChatAnim.HAPPY, COUSINS)
        chatPlayer(ChatAnim.QUIZ, SMACK)
        chatNpc(ChatAnim.HAPPY, GAME)
        chatNpc(ChatAnim.HAPPY, WON)
        chatNpc(ChatAnim.NEUTRAL, WOKE)
        chatPlayer(ChatAnim.QUIZ, SO_INVENTED)
        chatNpc(ChatAnim.NEUTRAL, REFINED)
        chatNpc(ChatAnim.QUIZ, BRANCHES)
        // Inferred: the Wiki's "Same as below" for yes, read as Hari's yes is, the teaching from the player's question.
        if (choice(YES, NO) == 1) sigurdTeach() else chatPlayer(ChatAnim.NEUTRAL, NO)
    }

    private suspend fun Dialogue.sigurdTeach() = with(SigurdLines) {
        chatPlayer(ChatAnim.QUIZ, TEACH_SAID)
        val level = player.skills.baseLevel(Skill.WOODCUTTING)
        if (level < BarfyBill.LOG_LEVEL) {
            for (box in listOf(LOW_1, LOW_2, LOW_3)) chatNpc(ChatAnim.NEUTRAL, box)
            return@with
        }
        chatNpc(ChatAnim.HAPPY, SIMPLE)
        chatNpc(ChatAnim.NEUTRAL, AXE)
        when {
            level < BarfyBill.DUGOUT_LEVEL -> {
                chatNpc(ChatAnim.NEUTRAL, LOG_1)
                chatNpc(ChatAnim.NEUTRAL, LOG_2)
            }
            level < BarfyBill.STABLE_DUGOUT_LEVEL -> chatNpc(ChatAnim.NEUTRAL, DUGOUT)
            level < BarfyBill.WAKA_LEVEL -> {
                chatNpc(ChatAnim.HAPPY, HANDY)
                chatNpc(ChatAnim.NEUTRAL, STABLE)
                chatNpc(ChatAnim.NEUTRAL, RECKONS)
            }
            else -> {
                chatNpc(ChatAnim.HAPPY, KNOW)
                chatPlayer(ChatAnim.QUIZ, WHAT_WAKA)
                chatNpc(ChatAnim.NEUTRAL, HARI_1)
                chatNpc(ChatAnim.NEUTRAL, HARI_2)
                chatNpc(ChatAnim.HAPPY, HARI_3)
            }
        }
    }

    /**
     * The lookout tower's ladders, by tile: up from 3096, 3433 on the ground and 3097, 3433 on the first floor, down from
     * the same tiles a level above (241 map). The same types stand in 40-odd other places, so the handlers check the tile
     * (the exploit review's round 11, R11C-1).
     */
    private val TOWER_LADDERS_UP = setOf(Triple(3096, 3433, 0), Triple(3097, 3433, 1))
    private val TOWER_LADDERS_DOWN = setOf(Triple(3096, 3433, 1), Triple(3097, 3433, 2))

    /** Whether [loc] is one of the lookout tower's ladders, of the type that stands on its tile. */
    fun isTowerLadder(loc: ZoneLoc): Boolean = Triple(loc.x, loc.z, loc.level) in when (loc.id) {
        TOWER_LADDER -> TOWER_LADDERS_UP
        TOWER_LADDER_TOP -> TOWER_LADDERS_DOWN
        else -> emptySet()
    }

    /** fai_barbarian_1 to _14 and fai_barbarian_female_1 to _4, less Hunding (fai_barbarian_9): the village's fighters. */
    val BARBARIANS = (3055..3072).filter { it != HUNDING }

    const val GUNTHOR = 299

    /**
     * A village barbarian's talk: one of his six lines, [roll] giving a number below its argument. He attacks once it
     * is over ([registerBarbarianVillage]). Guessed: the expression, and the lines as even chances (the Wiki gives none).
     */
    internal suspend fun Dialogue.barbarian(roll: (Int) -> Int) {
        chatNpc(ChatAnim.ANGRY, BarbarianLines.RANDOM[roll(BarbarianLines.RANDOM.size)])
    }

    /** Gunthor's talk: one of his six, [roll] as for [barbarian]. He attacks once it is over. */
    internal suspend fun Dialogue.gunthor(roll: (Int) -> Int) = with(GunthorLines) {
        when (roll(6)) {
            0 -> chatNpc(ChatAnim.ANGRY, COME_FOR_FIGHT)
            1 -> {
                chatNpc(ChatAnim.ANGRY, WHAT_WANT)
                chatPlayer(ChatAnim.QUIZ, OFFERING)
                chatNpc(ChatAnim.ANGRY, A_FIGHT)
            }
            2 -> chatNpc(ChatAnim.ANGRY, LOOK_ODD)
            3 -> chatNpc(ChatAnim.ANGRY, WANNA_FIGHT)
            4 -> chatNpc(ChatAnim.ANGRY, GROWL)
            else -> chatNpc(ChatAnim.ANGRY, GO_AWAY)
        }
    }
}

fun registerBarbarianVillage(world: World) {
    with(BarbarianVillage) {
        world.doors.registerDouble(LONGHALL_DOOR_LEFT, LONGHALL_DOOR_LEFT_OPEN, LONGHALL_DOOR_RIGHT, LONGHALL_DOOR_RIGHT_OPEN)
        // Only the tower's: the same directional ladder types stand in 40-odd other places, which their own areas handle.
        world.handlers.onLoc(TOWER_LADDER, 1) { op ->
            val loc = op.loc!!
            if (!isTowerLadder(loc)) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            climbLadder(world, loc, 1, Climbing.ANIM_REACH_FOR_LADDER)
        }
        world.handlers.onLoc(TOWER_LADDER_TOP, 1) { op ->
            val loc = op.loc!!
            if (!isTowerLadder(loc)) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            climbLadder(world, loc, -1, Climbing.ANIM_PICKUP_FLOOR)
        }
        registerSearch(world, SMALL_CRATES, 1, SearchLines.CRATE_EMPTY)

        world.handlers.onNpc(PEKSA, TALK_TO) { op ->
            var trade = false
            world.dialogues.start(this, op.npc) { trade = peksa() }
            if (trade) world.shopTypes.ofNpc(PEKSA)?.let { world.shopScreen.open(player, it) }
        }
        world.handlers.onNpc(TASSIE_SLIPCAST, TALK_TO) { op -> world.dialogues.start(this, op.npc) { tassie() } }
        world.handlers.onNpc(HUNDING, TALK_TO) { op -> world.dialogues.start(this, op.npc) { hunding() } }
        world.handlers.onNpc(ATLAS, TALK_TO) { op -> world.dialogues.start(this, op.npc) { atlas() } }
        // A multi-NPC's options are the shown type's.
        world.handlers.onNpc(CHECKAL, TALK_TO) { op -> world.dialogues.start(this, op.npc) { checkal() } }
        world.handlers.onNpc(LITARA, TALK_TO) { op -> world.dialogues.start(this, op.npc) { litara() } }
        world.handlers.onNpc(SIGURD, TALK_TO) { op -> world.dialogues.start(this, op.npc) { sigurd() } }
        // The barbarians and Gunthor attack whoever finishes talking to them (Wiki, Transcript:Barbarian and Gunthor the
        // Brave); walking off mid-talk ends the script before the attack, as the Wiki says it does.
        for (id in BARBARIANS) {
            world.handlers.onNpc(id, TALK_TO) { op ->
                world.dialogues.start(this, op.npc) { barbarian { world.random.nextInt(it) } }
                op.npc?.let { world.meleeCombat.provoke(it, player, world.tick) }
            }
        }
        world.handlers.onNpc(GUNTHOR, TALK_TO) { op ->
            world.dialogues.start(this, op.npc) { gunthor { world.random.nextInt(it) } }
            op.npc?.let { world.meleeCombat.provoke(it, player, world.tick) }
        }
    }
}
