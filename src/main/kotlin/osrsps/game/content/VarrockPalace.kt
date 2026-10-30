package osrsps.game.content

import kotlin.math.abs
import osrsps.game.Inventory
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.shop.ShopScreen

/** King Roald's lines, from the Wiki's Transcript:King Roald (After The Blood Moon Rises), read 2026-10-30. */
object KingRoaldLines : Lines("king_roald", "Transcript:King Roald") {
    val GREETING = line("greeting", "his first line: does the citizen need something?")
    val MORYTANIA = line("morytania", "option 1: how are things with Morytania, which the player then says")
    val FRIENDSHIP = line("friendship", "option 1: Morytania wanted friendship after all")
    val BUT = line("but", "option 1: the player starts to disagree and trails off")
    val VERY_GOOD = line("very_good", "option 1: the alliance is very good")
    val RECONSIDER = line("reconsider", "option 1: Aeonisig Raispher wants the alliance reconsidered")
    val BALANCE = line("balance", "option 1: the alliance gives the balance the Guthixians wanted")
    val CHURCH = line("church", "option 1: Aeonisig Raispher worries about the Church of Saradomin")
    val SAFE_DISTANCE = line("safe_distance", "option 1: friends at a safe distance")
    val WANTED = line("wanted", "option 1: what did the citizen want?")
    val ON_MY_WAY = line("on_my_way", "option 1: the player will be on their way")
    val NOT_REALLY = line("not_really", "option 2, which the player then says")
    val BUSY = line("busy", "option 2: he has a kingdom to run")
}

/** Aeonisig Raispher's lines, from the Wiki's Transcript:Aeonisig Raispher (Standard dialogue), read 2026-10-30. */
object AeonisigLines : Lines("aeonisig", "Transcript:Aeonisig Raispher") {
    val GREETING = line("greeting", "his first line: only bother the King with what matters")
    val WHO = line("who", "option 1, which the player then says")
    val WHO_1 = line("who_1", "option 1: the King's advisor on spiritual matters")
    val WHO_2 = line("who_2", "option 1: the player asks what that means")
    val WHO_3 = line("who_3", "option 1: the King's decisions touch the kingdom's spirit")
    val WHO_4 = line("who_4", "option 1: the player's answer")
    val WHAT = line("what", "option 2, which the player then says")
    val WHAT_1 = line("what_1", "option 2: the King hears every side, the Saradominists' above all")
    val WHAT_2 = line("what_2", "option 2: the player says the King should decide for himself")
    val WHAT_3 = line("what_3", "option 2: an interesting view, but unworkable")
    val WHERE = line("where", "option 3, which the player then says")
    val WHERE_1 = line("where_1", "option 3: training abroad and fighting in the Wilderness")
    val WHERE_2 = line("where_2", "option 3: what makes him worth the King's ear")
    val HOW = line("how", "option 4, which the player then says")
    val HOW_1 = line("how_1", "option 4: the King needs the best advisors")
    val HOW_2 = line("how_2", "option 4: the Church wanted a full-time advisor")
    val HOW_3 = line("how_3", "option 4: the player asks why no other faith has one")
    val HOW_4 = line("how_4", "option 4: he dismisses other faiths")
    val FAIR_ENOUGH = line("fair_enough", "option 5 before any other was asked, which the player then says")
    val BEST_BE_OFF = line("best_be_off", "option 5 once another was asked, which the player then says")
}

/** Ambassador Ferrnook's lines, from the Wiki's Transcript:Ambassador Ferrnook (Standard dialogue), read 2026-10-30. */
object FerrnookLines : Lines("ferrnook", "Transcript:Ambassador Ferrnook") {
    val HELLO = line("hello", "the player greets him: is he here to see the King?")
    val SORT_OF = line("sort_of", "sort of, but the King is usually busy")
    val NOT_BOTHERED = line("not_bothered", "the player: he doesn't seem to mind")
    val PATIENCE = line("patience", "he likes travelling, and diplomats need patience")
    val REMEMBER = line("remember", "the player will remember that")
}

/** Queen Ellamaria's lines, from the Wiki's Transcript:Queen Ellamaria (After Garden of Tranquillity), read 2026-10-30. */
object EllamariaLines : Lines("ellamaria", "Transcript:Queen Ellamaria") {
    val FARMING = line("farming", "the player asks to farm in her garden")
    val REFUSE = line("refuse", "her refusal")
    val SORRY = line("sorry", "the player's apology")
    val WHITE_TREE = line("white_tree", "she lets the player pick the White Tree's fruit")
    val ITEM = line("item", "an item used on her: leave the garden")
}

/** Sir Prysin's lines, from the Wiki's Transcript:Sir Prysin (Standard dialogue), read 2026-10-30. */
object PrysinLines : Lines("prysin", "Transcript:Sir Prysin") {
    val GREETING = line("greeting", "his first line: the player stopped the demon")
    val THATS_RIGHT = line("thats_right", "option 1, which the player then says")
    val GOOD_JOB = line("good_job", "option 1: his congratulations")
    val THANKS = line("thanks", "option 1: the player's thanks")
    val LOST = line("lost", "option 2, shown with no Silverlight held, worn or banked, which the player then says")
    val RECOVERED = line("recovered", "option 2: everyone knows, but he has Silverlight back")
    val RELIEF = line("relief", "the second menu's option 1, which the player then says")
    val BORROW = line("borrow", "the second menu's option 2, which the player then says")
    val NOT_FREE = line("not_free", "option 2: it has grown stronger and isn't lent freely")
    val PRICE = line("price", "option 2: its price, 500 coins for Silverlight (the transcript's line names Darklight's 1000)")
    val NOT_WORTH = line("not_worth", "the third menu's option 1, which the player then says")
    val PAY = line("pay", "the third menu's option 2, which the player then says")
    val TAKE_CARE = line("take_care", "paid: his send-off as Silverlight is handed over")
    val NO_MONEY = line("no_money", "fewer than 500 coins: the player can't afford it")
    val NO_ROOM = line("no_room", "a full backpack: no room to carry it")
}

/** Captain Rovin's lines, from the Wiki's Transcript:Captain Rovin (Standard dialogue), read 2026-10-30. */
object RovinLines : Lines("rovin", "Transcript:Captain Rovin") {
    val GREETING = line("greeting", "his first line: only palace guards up here")
    val GUARD = line("guard", "option 1, which the player then says")
    val KNOW_ALL = line("know_all", "option 1: he knows every guard")
    val RECRUIT = line("recruit", "option 1's menu, option 1, which the player then says")
    val INTERVIEW = line("interview", "the recruit: he interviews every recruit")
    val THAT_DISPROVES = line("that_disproves", "the recruit: the player concedes")
    val OUT_OF_SIGHT = line("out_of_sight", "the recruit: get out of his sight")
    val SURGERY = line("surgery", "option 1's menu, option 2, which the player then says")
    val WHAT_SURGERY = line("what_surgery", "the surgery: what surgery, the player looks healthy")
    val STILL_NOT = line("still_not", "the surgery: the player still shouldn't be here")
    val KING = line("king", "option 2, which the player then says")
    val KING_MAY = line("king_may", "option 2: the King could come up if he wanted")
    val NOT_KING = line("not_king", "option 2: the player isn't the King, so go")
    val IMPORTANT = line("important", "option 3, which the player then says")
    val LISTENING = line("listening", "option 3: what's so important?")
    val FORGOT = line("forgot", "option 3's menu, option 1, which the player then says")
    val NOT_IMPORTANT = line("not_important", "forgot: then it can't be important")
    val HOW_KNOW = line("how_know", "forgot: the player asks how he knows")
    val GO_AWAY = line("go_away", "forgot: go away")
    val ALE = line("ale", "option 3's menu, option 2, which the player then says")
    val KITCHEN = line("kitchen", "the ale: important, but tell the kitchen staff")
}

/** The palace guards' training Captain's lines, from the Wiki's Transcript:Captain (Standard and Overhead dialogue), read 2026-10-30. */
object GuardCaptainLines : Lines("guard_captain", "Transcript:Captain") {
    val GREETING = line("greeting", "his first line: what does the civilian want?")
    val DOING = line("doing", "option 1, which the player then says")
    val TRAINING = line("training", "option 1: he trains useless recruits")
    val ARMOUR = line("armour", "option 2 (and the later menus' option 1), which the player then says")
    val ONLY_GUARDS = line("only_guards", "the armour: only Varrock guards wear it")
    val BECOME = line("become", "the armour: the player offers to become a guard")
    val RECORD = line("record", "the armour, at Thieving 31 or more: the player's criminal record")
    val REPENT = line("repent", "the armour, at Thieving 31 or more: the player will repent")
    val REALLY = line("really", "the armour: does the player really want to be a guard?")
    val YES = line("yes", "the armour: the player does")
    val LIFE = line("life", "the armour: a guard's short life")
    val NOT_WORRIED = line("not_worried", "the armour's menu, option 1, which the player then says")
    val FAILED = line("failed", "not worried: the player failed the intelligence test")
    val FORGET_IT = line("forget_it", "the armour's menu, option 2, which the player then says")
    val COWARD = line("coward", "forget it: a coward")
    val NOTHING = line("nothing", "option 3, which the player then says")
    val LUCKY = line("lucky", "option 3: lucky, now go")
    val FIT = line("fit", "the recruits' menu: they look fit, which the player then says")
    val WEAKLINGS = line("weaklings", "fit: they're weaklings")
    val WEAK = line("weak", "the recruits' menu: they look weak, which the player then says")
    val RESPECT = line("respect", "weak: respect his men")
    val CONTRADICT = line("contradict", "the player says the other one: a contradiction, now go")
    val SHOUTS = (1..10).map { line("shout_$it", "over his head at the recruits: shout $it of the Wiki's ten") }
}

/** Reldo's lines, from the Wiki's Transcript:Reldo (Standard dialogue after Shield of Arrav), read 2026-10-30. */
object ReldoLines : Lines("reldo", "Transcript:Reldo") {
    val GREETING = line("greeting", "his first line: hello, {name}")
    val TRADE = line("trade", "option 1, which the player then says")
    val KNOWLEDGE = line("knowledge", "option 1: he only has knowledge")
    val PRICE = line("price", "option 1: the player asks what he wants for it")
    val JOKE = line("joke", "option 1: a joke, he isn't a trader")
    val AH_WELL = line("ah_well", "option 1: the player's answer")
    val GOING = line("going", "option 2, which the player then says")
    val GOODBYE = line("goodbye", "option 2: until next time")
}

/** What the palace library's bookcases say (the Varrock Palace Library page). */
object PalaceLibraryLines : Lines("palace_library", "Varrock Palace Library") {
    val NOTHING = line("nothing", "Search on most of the library's bookcases: nothing interests the player")
    val TITLES = line("titles", "Search on the north-east alcove's east bookcase: its joke titles")
    val ANTS = line("ants", "Search on the north-east alcove's west bookcase: ants on a book")
}

/**
 * Varrock Palace (the Varrock piece's step 6), as it is once every quest is done. Its floors, stairs, ladders, doors and
 * furniture are step 1's ([Varrock]); its altar (409) is Lumbridge church's ([Church]), and its 22 guards are step 2's.
 * This adds its people (npc-spawns/varrock.json, on mejrs's tiles): King Roald (5215) with his advisor Aeonisig
 * Raispher (3774), Queen Ellamaria (1390) in her garden, Sir Prysin (5083), Captain Rovin (5085) on the roof, Reldo
 * (6203, a multi-NPC whose two types talk the same) in the library, Ambassador Ferrnook (2557), the training Captain
 * (11906) and his five Trainee Guards (11907 to 11910, the 2022 looks) by the roof's dummies, and the Monk of Zamorak (528) in the cage under the
 * stairs; and the library's bookcases.
 *
 * Talk is from each Wiki transcript, as it is once every quest is done. Guessed: every chathead expression; that
 * King Roald talks as after The Blood Moon Rises rather than after Priest in Peril (the Wiki lists both); that
 * Aeonisig's menu keeps every topic until the player leaves; that Sir Prysin offers his lost-Silverlight option only
 * when the player has none held, worn or banked (with one, the player just answers), and asks 500 coins (the Sir Prysin page: Silverlight 500, Darklight 1000;
 * the transcript's 1000 is Darklight's); that he checks coins before room; which of the alcove's two bookcases says which; that the library's quest bookcases
 * (questbookcase, lost_tribe_bookcase, ds2_varrock_bookcase) say nothing interests the player, as most do; the Captain's
 * shouting every 15 to 30 ticks. Left out: Reldo's Wise Old Man message, Darklight (Sir Prysin's members-world trade),
 * the Monk's combat (he casts, which waits on NPC magic; the city's fighters step), the Varrock Census, Surok Magis
 * (gone from the library once What Lies Below is done), the White Tree's fruit and Treznor's tree patch (Farming).
 */
object VarrockPalace {
    const val KING_ROALD = 5215
    const val AEONISIG = 3774
    const val ELLAMARIA = 1390
    const val PRYSIN = 5083
    const val ROVIN = 5085
    /** reldo_normal and reldo_withbook, the two types multi-NPC 6203 shows. */
    val RELDO = listOf(4243, 4242)
    const val FERRNOOK = 2557
    /** fai_varrock_guard_trainer02, the Captain's look since the guards' 2022 update (the old one is 3013). */
    const val GUARD_CAPTAIN = 11906
    const val TALK_TO = 1

    const val SILVERLIGHT = 2402
    const val SILVERLIGHT_PRICE = 500

    /** Silverlight, its dyed form, Darklight, Arclight and Emberlight: each counts as having it. Guessed list. */
    private val SILVERLIGHTS = listOf(SILVERLIGHT, 6745, 6746, 19675, 29589)

    /** How far from the King Aeonisig may stand to speak up; past it his lines show as a chathead anyway. Guessed. */
    private const val ADVISOR_RANGE = 10

    /** questbookcase, lost_tribe_bookcase and ds2_varrock_bookcase. */
    val QUEST_BOOKCASES = listOf(2402, 6916, 32462)
    const val ALCOVE_WEST_BOOKCASE = 23091
    const val ALCOVE_EAST_BOOKCASE = 23092

    const val SHOUT_MIN = 15
    const val SHOUT_MAX = 30

    internal suspend fun Dialogue.roald(world: World) = with(KingRoaldLines) {
        chatNpc(ChatAnim.NEUTRAL, GREETING)
        if (choice(MORYTANIA, NOT_REALLY) == 2) {
            chatPlayer(ChatAnim.NEUTRAL, NOT_REALLY)
            chatNpc(ChatAnim.NEUTRAL, BUSY)
            return@with
        }
        chatPlayer(ChatAnim.QUIZ, MORYTANIA)
        chatNpc(ChatAnim.HAPPY, FRIENDSHIP)
        chatPlayer(ChatAnim.CONFUSED, BUT)
        chatNpc(ChatAnim.HAPPY, VERY_GOOD)
        advisor(world, ChatAnim.ANGRY, RECONSIDER)
        chatNpc(ChatAnim.NEUTRAL, BALANCE)
        advisor(world, ChatAnim.SAD, CHURCH)
        chatNpc(ChatAnim.HAPPY, SAFE_DISTANCE)
        chatNpc(ChatAnim.QUIZ, WANTED)
        chatPlayer(ChatAnim.NEUTRAL, ON_MY_WAY)
    }

    /** Aeonisig speaks up in the King's talk: the one nearest the King, or his chathead if none is close. */
    private suspend fun Dialogue.advisor(world: World, anim: ChatAnim, line: Line) {
        val king = npc
        val near: Npc? = world.npcs.all.firstOrNull {
            it.type.id == AEONISIG && king != null && it.level == king.level && abs(it.x - king.x) <= ADVISOR_RANGE && abs(it.z - king.z) <= ADVISOR_RANGE
        }
        if (near != null) {
            chatNpc(near, anim, text(line))
        } else {
            val type = world.npcTypes[AEONISIG] ?: return
            chatNpc(type, type.name, anim, line)
        }
    }

    internal suspend fun Dialogue.aeonisig() = with(AeonisigLines) {
        chatNpc(ChatAnim.NEUTRAL, GREETING)
        var asked = false
        while (true) {
            when (choice(WHO, WHAT, WHERE, HOW, if (asked) BEST_BE_OFF else FAIR_ENOUGH)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, WHO)
                    chatNpc(ChatAnim.NEUTRAL, WHO_1)
                    chatPlayer(ChatAnim.QUIZ, WHO_2)
                    chatNpc(ChatAnim.NEUTRAL, WHO_3)
                    chatPlayer(ChatAnim.NEUTRAL, WHO_4)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, WHAT)
                    chatNpc(ChatAnim.NEUTRAL, WHAT_1)
                    chatPlayer(ChatAnim.NEUTRAL, WHAT_2)
                    chatNpc(ChatAnim.NEUTRAL, WHAT_3)
                }
                3 -> {
                    chatPlayer(ChatAnim.QUIZ, WHERE)
                    chatNpc(ChatAnim.NEUTRAL, WHERE_1)
                    chatNpc(ChatAnim.NEUTRAL, WHERE_2)
                }
                4 -> {
                    chatPlayer(ChatAnim.QUIZ, HOW)
                    chatNpc(ChatAnim.NEUTRAL, HOW_1)
                    chatNpc(ChatAnim.NEUTRAL, HOW_2)
                    chatPlayer(ChatAnim.QUIZ, HOW_3)
                    chatNpc(ChatAnim.ANGRY, HOW_4)
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, if (asked) BEST_BE_OFF else FAIR_ENOUGH)
                    return@with
                }
            }
            asked = true
        }
    }

    internal suspend fun Dialogue.reldo() = with(ReldoLines) {
        chatNpc(ChatAnim.HAPPY, GREETING, "name" to player.username)
        if (choice(TRADE, GOING) == 1) {
            chatPlayer(ChatAnim.QUIZ, TRADE)
            chatNpc(ChatAnim.HAPPY, KNOWLEDGE)
            chatPlayer(ChatAnim.QUIZ, PRICE)
            chatNpc(ChatAnim.LAUGH, JOKE)
            chatPlayer(ChatAnim.SAD, AH_WELL)
        } else {
            chatPlayer(ChatAnim.NEUTRAL, GOING)
            chatNpc(ChatAnim.HAPPY, GOODBYE)
        }
    }

    internal suspend fun Dialogue.ferrnook() = with(FerrnookLines) {
        chatPlayer(ChatAnim.QUIZ, HELLO)
        chatNpc(ChatAnim.NEUTRAL, SORT_OF)
        chatPlayer(ChatAnim.NEUTRAL, NOT_BOTHERED)
        chatNpc(ChatAnim.HAPPY, PATIENCE)
        chatPlayer(ChatAnim.HAPPY, REMEMBER)
    }

    internal suspend fun Dialogue.ellamaria() = with(EllamariaLines) {
        chatPlayer(ChatAnim.QUIZ, FARMING)
        chatNpc(ChatAnim.ANGRY, REFUSE)
        chatPlayer(ChatAnim.SAD, SORRY)
        chatNpc(ChatAnim.NEUTRAL, WHITE_TREE)
    }

    fun hasSilverlight(player: Player): Boolean =
        SILVERLIGHTS.any { player.inventory.count(it) > 0 || player.equipment.count(it) > 0 || player.bank.count(it) > 0 }

    /** Sir Prysin's talk; [give] hands over Silverlight. */
    internal suspend fun Dialogue.prysin(give: (Int) -> Unit) = with(PrysinLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        // With Silverlight in hand there is nothing to choose, so the player just answers. Guessed.
        if (hasSilverlight(player) || choice(THATS_RIGHT, LOST) == 1) {
            chatPlayer(ChatAnim.HAPPY, THATS_RIGHT)
            chatNpc(ChatAnim.HAPPY, GOOD_JOB)
            chatPlayer(ChatAnim.HAPPY, THANKS)
            return@with
        }
        chatPlayer(ChatAnim.SAD, LOST)
        chatNpc(ChatAnim.NEUTRAL, RECOVERED)
        if (choice(RELIEF, BORROW) == 1) {
            chatPlayer(ChatAnim.HAPPY, RELIEF)
            return@with
        }
        chatPlayer(ChatAnim.QUIZ, BORROW)
        chatNpc(ChatAnim.NEUTRAL, NOT_FREE)
        chatNpc(ChatAnim.NEUTRAL, PRICE)
        if (choice(NOT_WORTH, PAY) == 1) {
            chatPlayer(ChatAnim.ANGRY, NOT_WORTH)
            return@with
        }
        chatPlayer(ChatAnim.NEUTRAL, PAY)
        when {
            player.inventory.count(ShopScreen.COINS) < SILVERLIGHT_PRICE -> chatPlayer(ChatAnim.SAD, NO_MONEY)
            player.inventory.contents().size >= Inventory.INV_CAPACITY -> chatNpc(ChatAnim.ANGRY, NO_ROOM)
            else -> {
                // Paid and handed over with nothing suspending between, then his line.
                if (AlKharidTraders.pay(player, SILVERLIGHT_PRICE)) give(SILVERLIGHT)
                chatNpc(ChatAnim.HAPPY, TAKE_CARE)
            }
        }
    }

    internal suspend fun Dialogue.rovin() = with(RovinLines) {
        chatNpc(ChatAnim.ANGRY, GREETING)
        when (choice(GUARD, KING, IMPORTANT)) {
            1 -> {
                chatPlayer(ChatAnim.NEUTRAL, GUARD)
                chatNpc(ChatAnim.ANGRY, KNOW_ALL)
                if (choice(RECRUIT, SURGERY) == 1) {
                    chatPlayer(ChatAnim.NEUTRAL, RECRUIT)
                    chatNpc(ChatAnim.ANGRY, INTERVIEW)
                    chatPlayer(ChatAnim.SAD, THAT_DISPROVES)
                    chatNpc(ChatAnim.ANGRY, OUT_OF_SIGHT)
                } else {
                    chatPlayer(ChatAnim.NEUTRAL, SURGERY)
                    chatNpc(ChatAnim.CONFUSED, WHAT_SURGERY)
                    chatNpc(ChatAnim.ANGRY, STILL_NOT)
                }
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, KING)
                chatNpc(ChatAnim.NEUTRAL, KING_MAY)
                chatNpc(ChatAnim.ANGRY, NOT_KING)
            }
            else -> {
                chatPlayer(ChatAnim.NEUTRAL, IMPORTANT)
                chatNpc(ChatAnim.QUIZ, LISTENING)
                if (choice(FORGOT, ALE) == 1) {
                    chatPlayer(ChatAnim.SAD, FORGOT)
                    chatNpc(ChatAnim.NEUTRAL, NOT_IMPORTANT)
                    chatPlayer(ChatAnim.QUIZ, HOW_KNOW)
                    chatNpc(ChatAnim.ANGRY, GO_AWAY)
                } else {
                    chatPlayer(ChatAnim.HAPPY, ALE)
                    chatNpc(ChatAnim.NEUTRAL, KITCHEN)
                }
            }
        }
    }

    internal suspend fun Dialogue.guardCaptain(thieving: Int) = with(GuardCaptainLines) {
        chatNpc(ChatAnim.ANGRY, GREETING)
        when (choice(DOING, ARMOUR, NOTHING)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, DOING)
                chatNpc(ChatAnim.ANGRY, TRAINING)
                // Whichever the player says first, the other is a contradiction; armour stays on offer.
                val fit = choice(FIT, WEAK) == 1
                chatPlayer(ChatAnim.NEUTRAL, if (fit) FIT else WEAK)
                chatNpc(ChatAnim.ANGRY, if (fit) WEAKLINGS else RESPECT)
                if (choice(ARMOUR, if (fit) WEAK else FIT) == 2) {
                    chatPlayer(ChatAnim.NEUTRAL, if (fit) WEAK else FIT)
                    chatNpc(ChatAnim.ANGRY, CONTRADICT)
                    return@with
                }
                armour(thieving)
            }
            2 -> armour(thieving)
            else -> {
                chatPlayer(ChatAnim.NEUTRAL, NOTHING)
                chatNpc(ChatAnim.ANGRY, LUCKY)
            }
        }
    }

    private suspend fun Dialogue.armour(thieving: Int) = with(GuardCaptainLines) {
        chatPlayer(ChatAnim.QUIZ, ARMOUR)
        chatNpc(ChatAnim.NEUTRAL, ONLY_GUARDS)
        chatPlayer(ChatAnim.QUIZ, BECOME)
        if (thieving >= RECORD_THIEVING) {
            chatNpc(ChatAnim.SHIFTY, RECORD)
            chatPlayer(ChatAnim.SAD, REPENT)
        }
        chatNpc(ChatAnim.QUIZ, REALLY)
        chatPlayer(ChatAnim.HAPPY, YES)
        chatNpc(ChatAnim.QUIZ, LIFE)
        if (choice(NOT_WORRIED, FORGET_IT) == 1) {
            chatPlayer(ChatAnim.HAPPY, NOT_WORRIED)
            chatNpc(ChatAnim.ANGRY, FAILED)
        } else {
            chatPlayer(ChatAnim.SCARED, FORGET_IT)
            chatNpc(ChatAnim.ANGRY, COWARD)
        }
    }

    /** The Thieving level at which the Captain brings up the player's record (the transcript). */
    const val RECORD_THIEVING = 31
}

/** The palace's people and library. */
fun registerVarrockPalace(world: World) {
    with(VarrockPalace) {
        val talks: List<Pair<Int, suspend Dialogue.() -> Unit>> = listOf(
            KING_ROALD to { roald(world) },
            AEONISIG to { aeonisig() },
            ELLAMARIA to { ellamaria() },
            PRYSIN to { prysin { id -> world.objTypes[id]?.let { player.inventory.add(it, 1) } } },
            ROVIN to { rovin() },
            RELDO[0] to { reldo() },
            RELDO[1] to { reldo() },
            FERRNOOK to { ferrnook() },
            GUARD_CAPTAIN to { guardCaptain(player.skills.baseLevel(Skill.THIEVING)) },
        )
        for ((id, talk) in talks) {
            world.handlers.onNpc(id, TALK_TO) { op -> world.dialogues.start(this, op.npc) { talk() } }
        }
        world.handlers.onObjOnNpc(ELLAMARIA) { op ->
            world.dialogues.start(this, op.npc) { chatNpc(ChatAnim.ANGRY, EllamariaLines.ITEM) }
        }

        for (bookcase in QUEST_BOOKCASES) registerSearch(world, bookcase, 1, PalaceLibraryLines.NOTHING)
        registerSearch(world, ALCOVE_WEST_BOOKCASE, 1, PalaceLibraryLines.ANTS)
        registerSearch(world, ALCOVE_EAST_BOOKCASE, 1, PalaceLibraryLines.TITLES)

        world.npcScripts.onTimer(listOf(GUARD_CAPTAIN), SHOUT_MIN) { captain ->
            captain.effects.say(world.dialogueText[GuardCaptainLines.SHOUTS[world.random.between(0, GuardCaptainLines.SHOUTS.size - 1)]])
            world.random.between(SHOUT_MIN, SHOUT_MAX)
        }
    }
}
