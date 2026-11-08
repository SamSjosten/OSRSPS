package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

/** Wizard Traiborn's standard lines (the Wiki's Transcript:Traiborn; 2004Scape's traiborn.rs2 for the boxes and faces). */
object TraibornLines : Lines("traiborn", "Transcript:Traiborn") {
    val HELLO = line("hello", "his greeting")
    val WHATS_THAT = line("whats_that", "option 1, which the player then says")
    val WHERE = line("where", "option 1: his first answer")
    val PESKY = line("pesky", "option 1: his second")
    val CALLED_ME = line("called_me", "option 1's menu, option 1 (the player says it without its article)")
    val CALLED_ME_SAID = line("called_me_said", "option 1's menu, option 1, as the player says it")
    val UP_CLOSE = line("up_close", "option 1, 1: his first answer")
    val PROOF = line("proof", "option 1, 1: his second")
    val BE_OFF = line("be_off", "option 1, 1's menu, option 1, which the player then says")
    val SHEEP = line("sheep", "option 1, 1, 1: his farewell")
    val MAD = line("mad", "option 1, 1's menu, option 2, which the player then says")
    val PITY = line("pity", "option 1, 1, 2: his answer")
    val MASH = line("mash", "option 1's menu, option 2, which the player then says")
    val RIDICULOUS = line("ridiculous", "option 1, 2: his first answer")
    val INVISIBLE = line("invisible", "option 1, 2: his second")
    val TEACH = line("teach", "option 2, which the player then says")
    val NO_TRUCK = line("no_truck", "option 2: his answer")
    val ARENT_YOU = line("arent_you", "option 2's menu, option 1, which the player then says")
    val FROG = line("frog", "option 2, 1: his answer")
    val STOP_TALKING = line("stop_talking", "option 2's menu, option 2, which the player then says")
    val CHEERIO = line("cheerio", "option 2, 2: his answer")
}

/** Wizard Mizgog's lines once Imp Catcher is done (the Wiki's Transcript:Wizard Mizgog). */
object MizgogLines : Lines("mizgog", "Transcript:Wizard Mizgog") {
    val QUESTS = line("quests", "option 1, which the player then says")
    val ALL_GOOD = line("all_good", "option 1: his answer")
    val SPELLS = line("spells", "option 2, which the player then says")
    val MEDITATION = line("meditation", "option 2: his answer")
    val AMULET = line("amulet", "option 3, which the player then says")
    val SPARE = line("spare", "option 3: his answer, asking for the four beads")
    val HAVE_THEM = line("have_them", "with all four beads, the menu's option 1")
    val HERE_YOU_GO = line("here_you_go", "with all four beads, option 1 as the player says it")
    val GIVES = line("gives", "the item box as he takes the beads and gives an amulet of accuracy")
    val THANKS = line("thanks", "the player's thanks")
    val WHAT_FOR = line("what_for", "the player's question about the beads")
    val DONT_WANT = line("dont_want", "his last line")
    val LATER = line("later", "with all four beads, the menu's option 2, which the player then says")
    val ANOTHER_TIME = line("another_time", "his answer to Maybe later")
    val COME_BACK = line("come_back", "without all four beads: the player's line")
    val SEE_YOU = line("see_you", "without all four beads: his answer")
    val NO_BEADS = line("no_beads", "Purchase Amulet without all four beads: his line")
}

/** Wizard Grayzag's lines once Imp Catcher is done (the Wiki's Transcript:Wizard Grayzag). */
object GrayzagLines : Lines("grayzag", "Transcript:Wizard Grayzag") {
    val CLEVER = line("clever", "his first line")
    val WELL_YES = line("well_yes", "the player's answer")
    val WATCH = line("watch", "his second line")
    val MEDDLER = line("meddler", "his last line")
}

/** Professor Onglewip's standard lines (the Wiki's Transcript:Professor Onglewip). */
object OnglewipLines : Lines("onglewip", "Transcript:Professor Onglewip") {
    val LIVE_HERE = line("live_here", "the player's opening line")
    val STRONGHOLD = line("stronghold", "his answer")
    val WHERE = line("where", "the player's question")
    val NORTH_WEST = line("north_west", "his answer's first box")
    val FOOD = line("food", "his answer's second box")
    val MAKE_TIME = line("make_time", "the player's reply")
    val GNOMES = line("gnomes", "his last line")
}

/**
 * The Wizards' Tower's people (Draynor piece step 15b), as they are once every quest is done:
 * - Wizard Traiborn (`traiborn` 5081) on the first floor: his standard talk (Demon Slayer's key is done).
 * - Wizard Mizgog on the second floor, spawned as `wizard_mizgog_post` 7747, the type his multi-NPC (`wizard_mizgog`
 *   5005, varp 160) shows after Imp Catcher: Talk-to and Purchase Amulet, which trade one each of the black, white,
 *   yellow and red beads for an amulet of accuracy.
 * - Wizard Grayzag (`wizard_grayzag` 5006) beside him: four fixed lines. His anniversary-event lines are left out (no
 *   events here).
 * - Professor Onglewip (`aluft_gnome_mage` 2560) on the ground floor.
 *
 * Left out: Temple of the Eye's apprentices (Cordelia, Felix, Tamara), who after the quest are at the Temple of the Eye,
 * not here. Guessed: every chathead face that 2004Scape doesn't give, and that Purchase Amulet with the four beads trades
 * at once with the talk's item box.
 */
object WizardsTowerPeople {
    const val TRAIBORN = 5081
    const val MIZGOG = 7747
    const val GRAYZAG = 5006
    const val ONGLEWIP = 2560
    const val TALK_TO = 1
    const val PURCHASE_AMULET = 3

    const val RED_BEAD = 1470
    const val YELLOW_BEAD = 1472
    const val BLACK_BEAD = 1474
    const val WHITE_BEAD = 1476
    val BEADS = listOf(BLACK_BEAD, WHITE_BEAD, YELLOW_BEAD, RED_BEAD)
    const val AMULET_OF_ACCURACY = 1478

    internal suspend fun Dialogue.traiborn(): Unit = with(TraibornLines) {
        chatNpc(ChatAnim.SHOCKED, HELLO)
        if (choice(WHATS_THAT, TEACH) == 1) {
            chatPlayer(ChatAnim.SHOCKED, WHATS_THAT)
            chatNpc(ChatAnim.SHOCKED, WHERE)
            chatNpc(ChatAnim.SHOCKED, PESKY)
            if (choice(CALLED_ME, MASH) == 1) {
                chatPlayer(ChatAnim.HAPPY, CALLED_ME_SAID)
                chatNpc(ChatAnim.SHOCKED, UP_CLOSE)
                chatNpc(ChatAnim.SHOCKED, PROOF)
                if (choice(BE_OFF, MAD) == 1) {
                    chatPlayer(ChatAnim.NEUTRAL, BE_OFF)
                    chatNpc(ChatAnim.CONFUSED, SHEEP)
                } else {
                    chatPlayer(ChatAnim.HAPPY, MAD)
                    chatNpc(ChatAnim.SAD, PITY)
                }
            } else {
                chatPlayer(ChatAnim.ANGRY, MASH)
                chatNpc(ChatAnim.SHOCKED, RIDICULOUS)
                chatNpc(ChatAnim.SHOCKED, INVISIBLE)
            }
        } else {
            chatPlayer(ChatAnim.NEUTRAL, TEACH)
            chatNpc(ChatAnim.SHOCKED, NO_TRUCK)
            if (choice(ARENT_YOU, STOP_TALKING) == 1) {
                chatPlayer(ChatAnim.NEUTRAL, ARENT_YOU)
                chatNpc(ChatAnim.ANGRY, FROG)
            } else {
                chatPlayer(ChatAnim.NEUTRAL, STOP_TALKING)
                chatNpc(ChatAnim.HAPPY, CHEERIO)
            }
        }
    }

    fun hasBeads(player: Player): Boolean = BEADS.all { player.inventory.count(it) > 0 }

    /** Takes one of each bead and gives the amulet, with the item box. */
    internal suspend fun Dialogue.trade(give: (Player) -> Unit) {
        for (bead in BEADS) Ned.take(player, bead, 1)
        give(player)
        objbox(AMULET_OF_ACCURACY, MizgogLines.GIVES)
    }

    internal suspend fun Dialogue.mizgog(give: (Player) -> Unit): Unit = with(MizgogLines) {
        when (choice(QUESTS, SPELLS, AMULET)) {
            1 -> {
                chatPlayer(ChatAnim.NEUTRAL, QUESTS)
                chatNpc(ChatAnim.NEUTRAL, ALL_GOOD)
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, SPELLS)
                chatNpc(ChatAnim.NEUTRAL, MEDITATION)
            }
            else -> {
                chatPlayer(ChatAnim.QUIZ, AMULET)
                chatNpc(ChatAnim.NEUTRAL, SPARE)
                if (!hasBeads(player)) {
                    chatPlayer(ChatAnim.NEUTRAL, COME_BACK)
                    chatNpc(ChatAnim.HAPPY, SEE_YOU)
                } else if (choice(HAVE_THEM, LATER) == 1) {
                    chatPlayer(ChatAnim.HAPPY, HERE_YOU_GO)
                    trade(give)
                    chatPlayer(ChatAnim.HAPPY, THANKS)
                    chatPlayer(ChatAnim.QUIZ, WHAT_FOR)
                    chatNpc(ChatAnim.NEUTRAL, DONT_WANT)
                } else {
                    chatPlayer(ChatAnim.NEUTRAL, LATER)
                    chatNpc(ChatAnim.NEUTRAL, ANOTHER_TIME)
                }
            }
        }
    }

    internal suspend fun Dialogue.purchaseAmulet(give: (Player) -> Unit) {
        if (hasBeads(player)) trade(give) else chatNpc(ChatAnim.NEUTRAL, MizgogLines.NO_BEADS)
    }

    internal suspend fun Dialogue.grayzag(): Unit = with(GrayzagLines) {
        chatNpc(ChatAnim.ANGRY, CLEVER)
        chatPlayer(ChatAnim.HAPPY, WELL_YES)
        chatNpc(ChatAnim.ANGRY, WATCH)
        chatNpc(ChatAnim.ANGRY, MEDDLER)
    }

    internal suspend fun Dialogue.onglewip(): Unit = with(OnglewipLines) {
        chatPlayer(ChatAnim.QUIZ, LIVE_HERE)
        chatNpc(ChatAnim.NEUTRAL, STRONGHOLD)
        chatPlayer(ChatAnim.QUIZ, WHERE)
        chatNpc(ChatAnim.NEUTRAL, NORTH_WEST)
        chatNpc(ChatAnim.HAPPY, FOOD)
        chatPlayer(ChatAnim.NEUTRAL, MAKE_TIME)
        chatNpc(ChatAnim.HAPPY, GNOMES)
    }
}

fun registerWizardsTowerPeople(world: World) {
    val give: (Player) -> Unit = { player ->
        world.objTypes[WizardsTowerPeople.AMULET_OF_ACCURACY]?.let { world.groundObjs.give(player, it, 1) }
            ?: error("No obj type ${WizardsTowerPeople.AMULET_OF_ACCURACY} for the amulet of accuracy")
    }
    with(WizardsTowerPeople) {
        world.handlers.onNpc(TRAIBORN, TALK_TO) { op -> world.dialogues.start(this, op.npc) { traiborn() } }
        world.handlers.onNpc(MIZGOG, TALK_TO) { op -> world.dialogues.start(this, op.npc) { mizgog(give) } }
        world.handlers.onNpc(MIZGOG, PURCHASE_AMULET) { op -> world.dialogues.start(this, op.npc) { purchaseAmulet(give) } }
        world.handlers.onNpc(GRAYZAG, TALK_TO) { op -> world.dialogues.start(this, op.npc) { grayzag() } }
        world.handlers.onNpc(ONGLEWIP, TALK_TO) { op -> world.dialogues.start(this, op.npc) { onglewip() } }
    }
}

/** Wizard Jalarast's lines on a members' world (the Wiki's Transcript:Wizard Jalarast). */
object JalarastLines : Lines("jalarast", "Transcript:Wizard Jalarast") {
    val HELLO = line("hello", "his greeting")
    val WHAT_DO = line("what_do", "option 1, which the player then says")
    val STUDYING = line("studying", "option 1: his answer")
    val WHATS_THAT = line("whats_that", "option 1's menu, option 1, which the player then says")
    val SPECIAL = line("special", "his line on what splitbark armour is")
    val EASY = line("easy", "his line on its being easy to make but the pieces being hard to get")
    val GOOD_LUCK = line("good_luck", "the menu after his easy line, option 1, which the player then says; the talk ends")
    val MAKE_SOME = line("make_some", "the option asking him to make some, which the player then says")
    val WEARING = line("wearing", "option 2, which the player then says")
    val THIS_IS = line("this_is", "option 2: his answer")
    val NEED_BARK = line("need_bark", "after the player asks him to make some: what he needs and where it comes from")
    val DISCOUNT = line("discount", "his offer to make it at a discount")
    val GO_LOOKING = line("go_looking", "the menu after his offer, option 1, which the player then says; the talk ends")
    val HOW_MUCH = line("how_much", "the menu after his offer, option 2, which the player then says")
    val PIECES = line("pieces", "his answer: how many of each the pieces take")
    val PRICES = line("prices", "his answer: what each piece costs")
    val MAKE_ARMOUR = line("make_armour", "option 3, which the player then says")
    val CERTAINLY = line("certainly", "option 3: his answer, before the Buy menu")
    val NO_THANKS = line("no_thanks", "option 4, which the player then says; the talk ends")
    val BUY_HELM = line("buy_helm", "the Buy menu's option 1")
    val BUY_BODY = line("buy_body", "the Buy menu's option 2")
    val BUY_LEGS = line("buy_legs", "the Buy menu's option 3")
    val BUY_GAUNTLETS = line("buy_gauntlets", "the Buy menu's option 4")
    val BUY_BOOTS = line("buy_boots", "the Buy menu's option 5")
    val THERE_YOU_GO = line("there_you_go", "his line when he makes a piece")
    val NEED_HELM = line("need_helm", "his line when the player lacks a helm's bark, cloth or coins")
    val NEED_BODY = line("need_body", "his line when the player lacks a body's bark, cloth or coins")
    val NEED_LEGS = line("need_legs", "his line when the player lacks the legs' bark, cloth or coins")
    val NEED_GAUNTLETS = line("need_gauntlets", "his line when the player lacks the gauntlets' bark, cloth or coins")
    val NEED_BOOTS = line("need_boots", "his line when the player lacks the boots' bark, cloth or coins")
}

/**
 * Wizard Jalarast (`armourmaking_wizard` 3232) on the Wizards' Tower's first floor (Draynor piece step 15c), who makes
 * splitbark armour from hollow bark and fine cloth for coins, as the Wiki's transcript and Wizard Jalarast page have it
 * (a members' world). His talk's third option and the Buy menu make a piece; Trade opens the Buy menu.
 *
 * Guessed: that Trade goes straight to the Buy menu (the Wiki doesn't say), and every chathead face.
 */
object Jalarast {
    const val JALARAST = 3232
    const val TALK_TO = 1
    const val TRADE = 3

    const val BARK = 3239
    const val FINE_CLOTH = 3470

    /** A piece: what it is, how many bark and fine cloth it takes, its coins, and his line when the player lacks them. */
    enum class Piece(val obj: Int, val materials: Int, val coins: Int, val option: Line, val need: Line) {
        HELM(3385, 2, 6_000, JalarastLines.BUY_HELM, JalarastLines.NEED_HELM),
        BODY(3387, 4, 37_000, JalarastLines.BUY_BODY, JalarastLines.NEED_BODY),
        LEGS(3389, 3, 32_000, JalarastLines.BUY_LEGS, JalarastLines.NEED_LEGS),
        GAUNTLETS(3391, 1, 1_000, JalarastLines.BUY_GAUNTLETS, JalarastLines.NEED_GAUNTLETS),
        BOOTS(3393, 1, 1_000, JalarastLines.BUY_BOOTS, JalarastLines.NEED_BOOTS),
    }

    fun canMake(player: Player, piece: Piece): Boolean =
        player.inventory.count(BARK) >= piece.materials && player.inventory.count(FINE_CLOTH) >= piece.materials &&
            player.inventory.count(Ned.COINS) >= piece.coins

    internal suspend fun Dialogue.buy(give: (Player, Int) -> Unit) {
        val piece = Piece.entries[choice(*Piece.entries.map { it.option }.toTypedArray()) - 1]
        if (!canMake(player, piece)) return chatNpc(ChatAnim.NEUTRAL, piece.need)
        Ned.take(player, BARK, piece.materials)
        Ned.take(player, FINE_CLOTH, piece.materials)
        Ned.take(player, Ned.COINS, piece.coins)
        give(player, piece.obj)
        chatNpc(ChatAnim.HAPPY, JalarastLines.THERE_YOU_GO)
    }

    /** From his explanation of the armour: the menu to end or ask him to make some. */
    private suspend fun Dialogue.afterEasy() = with(JalarastLines) {
        if (choice(GOOD_LUCK, MAKE_SOME) == 1) chatPlayer(ChatAnim.NEUTRAL, GOOD_LUCK) else makeSome()
    }

    private suspend fun Dialogue.makeSome() = with(JalarastLines) {
        chatPlayer(ChatAnim.QUIZ, MAKE_SOME)
        chatNpc(ChatAnim.NEUTRAL, NEED_BARK)
        chatNpc(ChatAnim.HAPPY, DISCOUNT)
        if (choice(GO_LOOKING, HOW_MUCH) == 2) {
            chatPlayer(ChatAnim.QUIZ, HOW_MUCH)
            chatNpc(ChatAnim.NEUTRAL, PIECES)
            chatNpc(ChatAnim.NEUTRAL, PRICES)
        }
        chatPlayer(ChatAnim.HAPPY, GO_LOOKING)
    }

    internal suspend fun Dialogue.talk(give: (Player, Int) -> Unit): Unit = with(JalarastLines) {
        chatNpc(ChatAnim.NEUTRAL, HELLO)
        when (choice(WHAT_DO, WEARING, MAKE_ARMOUR, NO_THANKS)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, WHAT_DO)
                chatNpc(ChatAnim.NEUTRAL, STUDYING)
                if (choice(WHATS_THAT, MAKE_SOME) == 1) {
                    chatPlayer(ChatAnim.QUIZ, WHATS_THAT)
                    chatNpc(ChatAnim.NEUTRAL, SPECIAL)
                    chatNpc(ChatAnim.NEUTRAL, EASY)
                    afterEasy()
                } else {
                    makeSome()
                }
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, WEARING)
                chatNpc(ChatAnim.NEUTRAL, THIS_IS)
                chatNpc(ChatAnim.NEUTRAL, EASY)
                afterEasy()
            }
            3 -> {
                chatPlayer(ChatAnim.QUIZ, MAKE_ARMOUR)
                chatNpc(ChatAnim.HAPPY, CERTAINLY)
                buy(give)
            }
            else -> chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
        }
    }
}

fun registerJalarast(world: World) {
    val give: (Player, Int) -> Unit = { player, obj ->
        world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } ?: error("No obj type $obj for splitbark armour")
    }
    with(Jalarast) {
        world.handlers.onNpc(JALARAST, TALK_TO) { op -> world.dialogues.start(this, op.npc) { talk(give) } }
        world.handlers.onNpc(JALARAST, TRADE) { op -> world.dialogues.start(this, op.npc) { buy(give) } }
    }
}
