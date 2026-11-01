package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.shop.ShopScreen

/** Romeo's lines, from the Wiki's Transcript:Romeo (after Romeo & Juliet), read 2026-11-01. */
object RomeoLines : Lines("romeo", "Transcript:Romeo") {
    val DREADFUL = line("dreadful", "his first line: Juliet's death was dreadful")
    val COUSIN = line("cousin", "his second line: he gets on with her cousin, thanks")
}

/** Juliet's lines, from the Wiki's Transcript:Juliet (after Romeo & Juliet), read 2026-11-01. */
object JulietLines : Lines("juliet", "Transcript:Juliet") {
    val CRYPT = line("crypt", "her first line: she waited long in the crypt")
    val WORTHLESS = line("worthless", "her second line: Romeo never came")
    val INDIGESTION = line("indigestion", "her third line: only indigestion, no more men like him")
    val GO_AWAY = line("go_away", "her last line: go, or she calls her father")
}

/** Draul Leptoc's line, from the Wiki's Transcript:Draul Leptoc (after Romeo & Juliet), read 2026-11-01. */
object DraulLeptocLines : Lines("draul_leptoc", "Transcript:Draul Leptoc") {
    val PROUD = line("proud", "his only line: is the player proud now?")
}

/** Phillipa's line, from the Wiki's Transcript:Phillipa (after Romeo & Juliet), read 2026-11-01. */
object PhillipaLines : Lines("phillipa", "Transcript:Phillipa") {
    val ROMEO = line("romeo", "her only line: she gets on with Romeo, pities Juliet")
}

/** Gertrude's lines, from the Wiki's Transcript:Gertrude (after Gertrude's Cat) and Gertrude, read 2026-11-01. */
object GertrudeLines : Lines("gertrude", "Transcript:Gertrude") {
    val HELLO_AGAIN = line("hello_again", "the player's greeting")
    val RATCATCHERS = line("ratcatchers", "her first line: did the player meet the ratcatchers?")
    val STRANGE = line("strange", "the player: they were strange")
    val HELP = line("help", "her question: what can she help with?")
    val KITTENS = line("kittens", "option 1, which the player then says")
    val BYE = line("bye", "option 2, which the player then says")
    val COST = line("cost", "kittens: they cost 100 coins, want one?")
    val YES = line("yes", "the kitten menu's option 1, which the player then says")
    val NO = line("no", "the kitten menu's option 2, which the player then says (they paid the boy already)")
    val SHORT = line("short", "kittens, fewer than 100 coins: the player will come back")
    val GIVEN = line("given", "the kitten handed over, shown with it")
    val NOT_ENOUGH = line("not_enough", "Kitten, fewer than 100 coins: a kitten costs 100 coins")
    val WAIT = line("wait", "a kitten or cat already owned: wait until it's grown")
}

/** Shilop's lines, from the Wiki's Transcript:Shilop, read 2026-11-01. */
object ShilopLines : Lines("shilop", "Transcript:Shilop") {
    val HELLO = line("hello", "the player's greeting")
    val STRANGERS = line("strangers", "his answer: no talking to strange old people")
    val NOT_OLD = line("not_old", "the player objects")
}

/** Wilough's lines, from the Wiki's Transcript:Wilough, read 2026-11-01. */
object WiloughLines : Lines("wilough", "Transcript:Wilough") {
    val HELLO = line("hello", "the player's greeting")
    val STRANGERS = line("strangers", "his answer: no talking to strange old people")
    val NOT_OLD = line("not_old", "the player objects")
}

/** Philop's and Kanel's lines, from the Wiki's Transcript:Philop and Transcript:Kanel, read 2026-11-01. */
object PlayingChildLines : Lines("playing_child", "Transcript:Philop") {
    val HELLO = line("hello", "the player's greeting")
    val IGNORED = line("ignored", "a message box: the child plays on, ignoring the player")
}

/** Aris's lines, from the Wiki's Transcript:Aris (after Demon Slayer), read 2026-11-01. */
object ArisLines : Lines("aris", "Transcript:Aris") {
    val GREETING = line("greeting", "her first line: hello, young one")
    val DEMON = line("demon", "her second line: well done slaying the demon")
    val HOW_KNEW = line("how_knew", "option 1, which the player then says")
    val KNOWING = line("knowing", "option 1: she's good at knowing things")
    val THANKS = line("thanks", "option 2, which the player then says")
    val NOT_YOUNG = line("not_young", "option 3, which the player then says")
    val GRAND_SCHEME = line("grand_scheme", "option 3: young in the grand scheme")
    val HOW_OLD = line("how_old", "option 3's menu, option 1, which the player then says")
    val RIDDLE = line("riddle", "how old: a riddle of stool legs")
    val NEVER_MIND = line("never_mind", "how old: the player gives up on the riddle")
    val FAIR = line("fair", "option 3's menu, option 2, which the player then says")
    val WISE = line("wise", "fair: she calls the player wise")
}

/** Charlie the Tramp's lines, from the Wiki's Transcript:Charlie the Tramp, read 2026-11-01. */
object CharlieLines : Lines("charlie", "Transcript:Charlie the Tramp") {
    val SPARE = line("spare", "his first line: any spare change?")
    val WHO = line("who", "option 1, which the player then says")
    val NAME = line("name", "option 1: his name, then the menu again")
    val NONE = line("none", "option 2, which the player then says")
    val THANKS_ANYWAY = line("thanks_anyway", "option 2, or no coins: thanks anyway")
    val JOB = line("job", "option 3, which the player then says")
    val CURSE = line("curse", "option 3: he threatens a curse")
    val HERE = line("here", "option 4, which the player then says")
    val THANKS = line("thanks", "option 4, a coin given: his thanks")
    val NO_PROBLEM = line("no_problem", "the thanks menu's option 1, which the player then says")
    val HINT = line("hint", "the thanks menu's option 2, which the player then says")
    val FOOD = line("food", "hint: he only wanted money for food")
    val NO_MONEY = line("no_money", "option 4, no coins: the player has none")
    val ALLEY = line("alley", "option 5, which the player then says")
    val BLACK_ARM = line("black_arm", "option 5: the Black Arm Gang's headquarters")
    val WARNING = line("warning", "the alley menu's option 1, which the player then says")
    val WELCOME = line("welcome", "warning: no problem")
    val JOIN = line("join", "the alley menu's option 2, which the player then says")
    val MEMBER = line("member", "join: the player's already a Black Arm Gang member")
}

/** Dr Harlow's lines, from the Wiki's Transcript:Dr Harlow (Standard dialogue), read 2026-11-01. */
object DrHarlowLines : Lines("dr_harlow", "Transcript:Dr Harlow") {
    val DRINK = line("drink", "his slurred ask for a drink")
    val ENOUGH = line("enough", "the player: he's had enough")
}

/** Jonny the Beard's line, from the Wiki's Transcript:Jonny the Beard, read 2026-11-01. */
object JonnyTheBeardLines : Lines("jonny_the_beard", "Transcript:Jonny the Beard") {
    val NOT_INTERESTED = line("not_interested", "his only line: not interested")
}

/** Iffie's lines, from the Wiki's Transcript:Iffie (the Halloween 2021 event not done), read 2026-11-01. */
object IffieLines : Lines("iffie", "Transcript:Iffie") {
    val HELLO = line("hello", "the player's greeting")
    val COUNT = line("count", "her first line: she'll lose count")
    val SISTER = line("sister", "her second line: her sister's upstairs in the church")
}

/** The Varrock Town Crier's lines, from the Wiki's Transcript:Town Crier (regular players), read 2026-11-01. */
object TownCrierLines : Lines("town_crier", "Transcript:Town Crier") {
    val GREETING = line("greeting", "his first line: player moderators or the rules?")
    val PMODS = line("pmods", "option 1, which the player then says")
    val EXPLAIN = line("explain", "option 1: he'll explain player moderators")
    val WHAT_IS = line("what_is", "the moderator menu's option 1, which the player then says")
    val TRUSTED = line("trusted", "what is: trusted players with a silver crown")
    val WHAT_CAN = line("what_can", "the moderator menu's option 2, which the player then says")
    val POWERS = line("powers", "what can: muting, priority reports, the centre")
    val BECOME = line("become", "the moderator menu's option 3, which the player then says")
    val TIPS = line("tips", "become: how to be picked")
    val CANNOT = line("cannot", "the moderator menu's option 4, which the player then says")
    val LIMITS = line("limits", "cannot: no bans, no recoveries, not staff")
    val RULES = line("rules", "option 2, which the player then says")
    val BOOK = line("book", "option 2: the Rules of Conduct (his line; the book itself is left out)")
    val TIP = line("tip", "option 3, which the player then says")
    val TIP_LINES = (1..27).map { line("tip_$it", "option 3: tip $it of the Wiki's 27, one at random") }
    val GOODBYE = line("goodbye", "option 4, which the player then says")
    val FAREWELL = line("farewell", "goodbye: his farewell")
}

/** The tramp's lines, from the Wiki's Transcript:Tramp, read 2026-11-01. */
object TrampLines : Lines("tramp", "Transcript:Tramp") {
    val SPARE = line("spare", "his first line: any spare money?")
    val GIVE = line("give", "option 1, which the player then says")
    val THANKS = line("thanks", "option 1, a coin given: his thanks")
    val EARN = line("earn", "option 2, which the player then says")
    val SHRUG = line("shrug", "option 2: he shrugs it off")
    val NO_MONEY = line("no_money", "no coins: the player has none (no menu)")
    val DOUBT = line("doubt", "no coins: he doubts it")
    val OVERHEAD = (1..3).map { line("overhead_$it", "refused: overhead line $it of the Wiki's three, one at random") }
}

/**
 * Varrock's townsfolk (the Varrock piece's step 8a), talking as once every quest is done: Romeo (5037) in the square;
 * Juliet (5035, upstairs), her father Draul Leptoc (5039) and Phillipa (5040, upstairs) in the house west of the city;
 * Gertrude (7723, the after-quest type her multi-NPC 3500 shows), who sells kittens for 100 coins, and the children
 * Shilop (3501), Wilough (3503), Philop (3502) and Kanel (3504); Aris (11868) in her tent; Charlie the Tramp (5209)
 * and the tramp (3255), each begging a coin; Dr Harlow (16277) and Jonny the Beard (14139, the after-quest type) in the
 * Blue Moon Inn; Iffie (2899) knitting in Thessalia's shop; and the Town Crier (276) in the square.
 *
 * From the Wiki's transcripts. Guessed: every expression; Charlie's alley talk (the player is a Black Arm Gang member,
 * one of the two gangs Shield of Arrav ends in); the Town Crier's menu as one loop; that Gertrude's talk also refuses
 * a kitten while one is owned, and which pets count as owned ([OWNED_CATS]); that her kitten comes shown in an item
 * box. Left out: Gertrude's "pick my own" option and the Ring of Charos(a) colour screen; Shilop's and Wilough's talk
 * after being paid (quest); Charlie's treasure trail requests; Jonny the Beard's Attack (the fighters' step); the
 * tramp's "again" opening after a refusal; Iffie's talk after the Halloween 2021 event; the Town Crier's moderator
 * branch, Rules of Conduct book and overheads; Toby (the Achievement Diary).
 */
object VarrockTownsfolk {
    const val ROMEO = 5037
    const val JULIET = 5035
    const val DRAUL_LEPTOC = 5039
    const val PHILLIPA = 5040
    const val GERTRUDE = 7723
    const val SHILOP = 3501
    const val PHILOP = 3502
    const val WILOUGH = 3503
    const val KANEL = 3504
    const val ARIS = 11868
    const val CHARLIE = 5209
    const val TRAMP = 3255
    const val DR_HARLOW = 16277
    const val JONNY_THE_BEARD = 14139
    const val IFFIE = 2899
    const val TOWN_CRIER = 276
    const val TALK_TO = 1
    const val KITTEN_OP = 3

    const val KITTEN_PRICE = 100

    /** Pet kittens, one of each colour; Gertrude's is one at random. */
    val KITTENS = (1555..1560).toList()

    /**
     * A cat these held or banked stops Gertrude selling another: kittens and pet cats (1561 to 1566). Not overgrown cats
     * (1567 to 1572): once a cat is overgrown she sells another (Wiki, Gertrude); nor lazy or wily cats (6549 to 6560),
     * which a player may keep beside a kitten or cat (Wiki, Wily cat).
     */
    val OWNED_CATS = KITTENS + (1561..1566)

    private fun Dialogue.coins() = player.inventory.count(ShopScreen.COINS)

    private fun Dialogue.ownsCat() = OWNED_CATS.any { player.inventory.count(it) > 0 || player.bank.count(it) > 0 }

    internal suspend fun Dialogue.romeo() = with(RomeoLines) {
        chatNpc(ChatAnim.SAD, DREADFUL)
        chatNpc(ChatAnim.HAPPY, COUSIN)
    }

    internal suspend fun Dialogue.juliet() = with(JulietLines) {
        chatNpc(ChatAnim.ANGRY, CRYPT)
        chatNpc(ChatAnim.ANGRY, WORTHLESS)
        chatNpc(ChatAnim.ANGRY, INDIGESTION)
        chatNpc(ChatAnim.ANGRY, GO_AWAY)
    }

    internal suspend fun Dialogue.gertrude(world: World, give: (Int) -> Unit) = with(GertrudeLines) {
        chatPlayer(ChatAnim.HAPPY, HELLO_AGAIN)
        chatNpc(ChatAnim.HAPPY, RATCATCHERS)
        chatPlayer(ChatAnim.CONFUSED, STRANGE)
        chatNpc(ChatAnim.HAPPY, HELP)
        if (choice(KITTENS, BYE) == 2) {
            chatPlayer(ChatAnim.HAPPY, BYE)
            return@with
        }
        chatPlayer(ChatAnim.QUIZ, KITTENS)
        chatNpc(ChatAnim.HAPPY, COST)
        if (coins() < KITTEN_PRICE) {
            chatPlayer(ChatAnim.SAD, SHORT)
            return@with
        }
        if (choice(YES, NO) == 2) {
            chatPlayer(ChatAnim.NEUTRAL, NO)
            return@with
        }
        chatPlayer(ChatAnim.HAPPY, YES)
        sellKitten(world, give)
    }

    /** The Kitten option: no talk but the refusals. */
    internal suspend fun Dialogue.gertrudeKitten(world: World, give: (Int) -> Unit) = with(GertrudeLines) {
        if (coins() < KITTEN_PRICE) chatNpc(ChatAnim.NEUTRAL, NOT_ENOUGH) else sellKitten(world, give)
    }

    private suspend fun Dialogue.sellKitten(world: World, give: (Int) -> Unit) = with(GertrudeLines) {
        if (ownsCat()) {
            chatNpc(ChatAnim.NEUTRAL, WAIT)
            return@with
        }
        val kitten = VarrockTownsfolk.KITTENS[world.random.between(0, VarrockTownsfolk.KITTENS.size - 1)]
        if (!AlKharidTraders.pay(player, KITTEN_PRICE)) return@with
        give(kitten)
        objbox(kitten, GIVEN)
    }

    internal suspend fun Dialogue.boy(hello: Line, strangers: Line, notOld: Line) {
        chatPlayer(ChatAnim.HAPPY, hello)
        chatNpc(ChatAnim.ANGRY, strangers)
        chatPlayer(ChatAnim.ANGRY, notOld)
    }

    internal suspend fun Dialogue.playingChild() = with(PlayingChildLines) {
        chatPlayer(ChatAnim.HAPPY, HELLO)
        mesbox(IGNORED)
    }

    internal suspend fun Dialogue.aris() = with(ArisLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        chatNpc(ChatAnim.HAPPY, DEMON)
        when (choice(HOW_KNEW, THANKS, NOT_YOUNG)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, HOW_KNEW)
                chatNpc(ChatAnim.HAPPY, KNOWING)
            }
            2 -> chatPlayer(ChatAnim.HAPPY, THANKS)
            else -> {
                chatPlayer(ChatAnim.ANGRY, NOT_YOUNG)
                chatNpc(ChatAnim.HAPPY, GRAND_SCHEME)
                if (choice(HOW_OLD, FAIR) == 1) {
                    chatPlayer(ChatAnim.QUIZ, HOW_OLD)
                    chatNpc(ChatAnim.LAUGH, RIDDLE)
                    chatPlayer(ChatAnim.CONFUSED, NEVER_MIND)
                } else {
                    chatPlayer(ChatAnim.NEUTRAL, FAIR)
                    chatNpc(ChatAnim.HAPPY, WISE)
                }
            }
        }
    }

    internal suspend fun Dialogue.charlie() = with(CharlieLines) {
        chatNpc(ChatAnim.SAD, SPARE)
        while (true) {
            when (choice(WHO, NONE, JOB, HERE, ALLEY)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, WHO)
                    chatNpc(ChatAnim.HAPPY, NAME)
                }
                2 -> {
                    chatPlayer(ChatAnim.SAD, NONE)
                    chatNpc(ChatAnim.SAD, THANKS_ANYWAY)
                    return@with
                }
                3 -> {
                    chatPlayer(ChatAnim.ANGRY, JOB)
                    chatNpc(ChatAnim.ANGRY, CURSE)
                    return@with
                }
                4 -> {
                    chatPlayer(ChatAnim.HAPPY, HERE)
                    if (!AlKharidTraders.pay(player, 1)) {
                        chatPlayer(ChatAnim.SAD, NO_MONEY)
                        chatNpc(ChatAnim.SAD, THANKS_ANYWAY)
                        return@with
                    }
                    chatNpc(ChatAnim.HAPPY, THANKS)
                    if (choice(NO_PROBLEM, HINT) == 1) {
                        chatPlayer(ChatAnim.HAPPY, NO_PROBLEM)
                    } else {
                        chatPlayer(ChatAnim.QUIZ, HINT)
                        chatNpc(ChatAnim.NEUTRAL, FOOD)
                    }
                    return@with
                }
                else -> {
                    chatPlayer(ChatAnim.QUIZ, ALLEY)
                    chatNpc(ChatAnim.SHIFTY, BLACK_ARM)
                    if (choice(WARNING, JOIN) == 1) {
                        chatPlayer(ChatAnim.HAPPY, WARNING)
                        chatNpc(ChatAnim.HAPPY, WELCOME)
                    } else {
                        chatPlayer(ChatAnim.QUIZ, JOIN)
                        chatNpc(ChatAnim.NEUTRAL, MEMBER)
                    }
                    return@with
                }
            }
        }
    }

    internal suspend fun Dialogue.tramp(world: World) = with(TrampLines) {
        chatNpc(ChatAnim.SAD, SPARE)
        if (coins() == 0L) {
            chatPlayer(ChatAnim.SAD, NO_MONEY)
            chatNpc(ChatAnim.ANGRY, DOUBT)
            return@with
        }
        if (choice(GIVE, EARN) == 1) {
            chatPlayer(ChatAnim.HAPPY, GIVE)
            if (AlKharidTraders.pay(player, 1)) chatNpc(ChatAnim.HAPPY, THANKS)
        } else {
            chatPlayer(ChatAnim.ANGRY, EARN)
            chatNpc(ChatAnim.NEUTRAL, SHRUG)
            // A tick after the talk closes (guessed; the Wiki gives no timing), queued on him so the player isn't held.
            val line = OVERHEAD[world.random.between(0, OVERHEAD.size - 1)]
            npc?.let { tramp -> world.npcScripts.queue(tramp, 1) { it.effects.say(world.dialogueText[line]) } }
        }
    }

    internal suspend fun Dialogue.iffie() = with(IffieLines) {
        chatPlayer(ChatAnim.HAPPY, HELLO)
        chatNpc(ChatAnim.NEUTRAL, COUNT)
        chatNpc(ChatAnim.HAPPY, SISTER)
    }

    internal suspend fun Dialogue.townCrier(world: World) = with(TownCrierLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        while (true) {
            when (choice(PMODS, RULES, TIP, GOODBYE)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, PMODS)
                    chatNpc(ChatAnim.HAPPY, EXPLAIN)
                    val (ask, answer) = when (choice(WHAT_IS, WHAT_CAN, BECOME, CANNOT)) {
                        1 -> WHAT_IS to TRUSTED
                        2 -> WHAT_CAN to POWERS
                        3 -> BECOME to TIPS
                        else -> CANNOT to LIMITS
                    }
                    chatPlayer(ChatAnim.QUIZ, ask)
                    chatNpc(ChatAnim.NEUTRAL, answer)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, RULES)
                    chatNpc(ChatAnim.NEUTRAL, BOOK)
                }
                3 -> {
                    chatPlayer(ChatAnim.QUIZ, TIP)
                    chatNpc(ChatAnim.HAPPY, TIP_LINES[world.random.between(0, TIP_LINES.size - 1)])
                }
                else -> {
                    chatPlayer(ChatAnim.HAPPY, GOODBYE)
                    chatNpc(ChatAnim.HAPPY, FAREWELL)
                    return@with
                }
            }
        }
    }
}

/** The townsfolk's talk, and Gertrude's Kitten option. */
fun registerVarrockTownsfolk(world: World) {
    with(VarrockTownsfolk) {
        fun talk(id: Int, block: suspend Dialogue.() -> Unit) =
            world.handlers.onNpc(id, TALK_TO) { op -> world.dialogues.start(this, op.npc) { block() } }
        fun line(id: Int, anim: ChatAnim, line: Line) = talk(id) { chatNpc(anim, line) }
        val give: Dialogue.(Int) -> Unit = { obj -> world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } }

        talk(ROMEO) { romeo() }
        talk(JULIET) { juliet() }
        line(DRAUL_LEPTOC, ChatAnim.ANGRY, DraulLeptocLines.PROUD)
        line(PHILLIPA, ChatAnim.SAD, PhillipaLines.ROMEO)
        talk(GERTRUDE) { gertrude(world) { give(it) } }
        world.handlers.onNpc(GERTRUDE, KITTEN_OP) { op -> world.dialogues.start(this, op.npc) { gertrudeKitten(world) { give(it) } } }
        talk(SHILOP) { with(ShilopLines) { boy(HELLO, STRANGERS, NOT_OLD) } }
        talk(WILOUGH) { with(WiloughLines) { boy(HELLO, STRANGERS, NOT_OLD) } }
        talk(PHILOP) { playingChild() }
        talk(KANEL) { playingChild() }
        talk(ARIS) { aris() }
        talk(CHARLIE) { charlie() }
        talk(TRAMP) { tramp(world) }
        talk(DR_HARLOW) {
            chatNpc(ChatAnim.DRUNK, DrHarlowLines.DRINK)
            chatPlayer(ChatAnim.NEUTRAL, DrHarlowLines.ENOUGH)
        }
        line(JONNY_THE_BEARD, ChatAnim.ANGRY, JonnyTheBeardLines.NOT_INTERESTED)
        talk(IFFIE) { iffie() }
        talk(TOWN_CRIER) { townCrier(world) }
    }
}
