package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
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
