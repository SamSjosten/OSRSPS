package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

/** One speaker's turn in a fixed run of talk: the NPC's (W) or the player's (P), as [ArchiveLobby.say] plays them. */
private fun Lines.turns(speakers: String, line: (index: Int, npc: Boolean) -> Line): List<Said> =
    speakers.mapIndexed { i, who -> Said(who == 'W', line(i + 1, who == 'W')) }

/**
 * General Montoya's lines, from the Wiki's Transcript:General_Montoya, "Standard dialogue" (before the player has
 * finished the raid), read 2026-11-01. Each run is `<topic>_1`, `<topic>_2`…, W for her lines and P for the player's.
 */
object GeneralMontoyaLines : Lines("general_montoya", "Transcript:General_Montoya") {
    val GREETING_TALK = said("greeting", "PWPW")
    val ARCHIVE = line("archive", "her menu, option 1: the Archive, which the player then says")
    val ENEMIES = line("enemies", "her menu, option 2: our enemies, which the player then says")
    val EVERYBODY = line("everybody", "her menu, option 3: everybody else, which the player then says")
    val THANKS = line("thanks", "her menu, option 4: thanks, which the player then says")
    val ARCHIVE_TALK = said("archive", "WWPWPWWPWWPWP")
    val ENEMIES_TALK = said("enemies", "WPWPWWPWPWPWPWPWPW")
    val EVERYBODY_TALK = said("everybody", "WPW")
    val THANKS_ANSWER = line("thanks_1", "thanks, line 1: hers, with the player's name", "name")

    private fun said(topic: String, speakers: String) = turns(speakers) { i, npc ->
        line("${topic}_$i", "$topic, line $i: ${if (npc) "hers" else "the player's"}")
    }
}

/**
 * The Record Keeper's lines, from the Wiki's Transcript:Record_Keeper, "Initial dialogue", read 2026-11-01. Each run is
 * `<topic>_1`, `<topic>_2`…, W for his lines and P for the player's.
 */
object RecordKeeperLines : Lines("record_keeper", "Transcript:Record_Keeper") {
    val INTRO_TALK = said("intro", "PWPWPWPWPWPWWPWPWWWPWPW")
    val VIEW_STATS = line("view_stats", "the title of the Yes/No menu after the introduction and each topic")
    val YES = line("yes", "that menu, option 1")
    val NO = line("no", "that menu, option 2")
    val DOING = line("doing", "his menu, option 1: what he's doing, which the player then says")
    val THINGS = line("things", "his menu, option 2: how things are, which the player then says")
    val GOODBYE = line("goodbye", "his menu, option 3, which the player then says")
    val DOING_TALK = said("doing", "WPWPWWPWPW")
    val THINGS_TALK = said("things", "WW")
    val GOODBYE_TALK = said("goodbye", "W")

    private fun said(topic: String, speakers: String) = turns(speakers) { i, npc ->
        line("${topic}_$i", "$topic, line $i: ${if (npc) "his" else "the player's"}")
    }
}

/**
 * The Fractured Archive's Wizard apprentice's lines, from the Wiki's Transcript:Wizard_apprentice, read 2026-11-01. The
 * first talk is `first_1`… The later talk is `later_1` to `later_3`, then the first talk from its 16th line on: the
 * transcript's two runs match line for line from there (inferred from their openings).
 */
object WizardApprenticeLines : Lines("tfa_wizard_apprentice", "Transcript:Wizard_apprentice") {
    val FIRST_TALK = said("first", "PWPWPWPWPWPWPWPWWPWPWPWPWP")
    val LATER_TALK = said("later", "PWP") + FIRST_TALK.drop(15)

    private fun said(topic: String, speakers: String) = turns(speakers) { i, npc ->
        line("${topic}_$i", "$topic, line $i: ${if (npc) "his" else "the player's"}")
    }
}

/**
 * The Fractured Archive's Banker's lines, from the Wiki's Transcript:Banker_(The_Fractured_Archive), read 2026-11-01.
 * Her menu's first four options are the standard banker's ([BankerLines]): the transcript's openings match.
 */
object ArchiveBankerLines : Lines("tfa_banker", "Transcript:Banker_(The_Fractured_Archive)") {
    val GREETING = line("greeting", "her first line")
    val FUNCTION = line("function", "her menu, option 5: her function here, which the player then says")
    val FUNCTION_TALK = turns("WPWPWPWPWWWPPWPWPWPW") { i, npc ->
        line("function_$i", "function, line $i: ${if (npc) "hers" else "the player's"}")
    }
}

/**
 * The Fractured Archive's lobby (raid step 14), map square 64,86 of the 241.4 cache, and the way in from the Chasm of
 * Tears. From the cache: every loc and NPC type, the lobby's map and the cavity's varbit; from the Wiki: who is in the
 * lobby and what each says.
 *
 * - The Chasm of Tears' cavity (`luc2_raid_entry_multi`, 62542, at 4050,4567) is a multiloc on varbit 16354
 *   (`wgs_tfa_shortcut_unlocked`): 1 shows the Opening (Crawl-through). While Guthix Sleeps counts as done at login
 *   ([osrsps.game.Quests.AFTER_QUESTS]), and the miniquest that starts at the first entry is left out: no quests.
 *   The map also puts an Opening (`luc2_raid_entry2`, 54113) on the level above, which crawls the same.
 * - The lobby's Opening (`tfa_lobby_raid_exit`, 63009, at 4126,5505) goes back to the chasm.
 * - The bank chest's Use and Collect are [registerBanking]'s, the banker's Bank and Collect [registerBankers]' own.
 * - Not built yet: the Archive Door (the raid, step 15), the Grouping Board (`::raidparty` stands in), the Alliance
 *   records and the Record Keeper's statistics, the Strategy board, the Ascension rocks and the Loot Wheelbarrow.
 *   The guards, druids and Alliance builders wait for a recording of the lobby for their tiles.
 *
 * Guessed: where each NPC stands (npc-spawns/fractured-archive-lobby.json), the tiles each crawl lands on, no animation
 * on either crawl, every chathead expression, that Montoya's and the Record Keeper's menus come back after each topic,
 * that the Record Keeper's statistics prompt answers Yes by closing, and that his introduction plays each time (the
 * var live keeps for it isn't known).
 */
object ArchiveLobby {
    const val RECORD_KEEPER = 16692
    const val WIZARD_APPRENTICE = 16693
    const val BANKER = 16704
    const val GENERAL_MONTOYA = 16705
    const val TALK_TO = 1

    /** The Banker's Bank (op3) and Collect (op4), as the standard bankers have them. */
    const val BANK = 3
    const val COLLECT = 4

    /** tfa_wizard_apprentice_met: 1 once the player has had the apprentice's first talk. */
    const val VARBIT_APPRENTICE_MET = 16427

    const val CAVITY = 62542
    const val CAVITY_UPPER = 54113
    const val LOBBY_EXIT = 63009
    const val CRAWL = 1
    const val ENTER = 1

    /** wgs_tfa_shortcut_unlocked: 1 opens the cavity. */
    const val VARBIT_CAVITY = 16354

    /** North of the lobby's Opening, inside. Guessed. */
    const val LOBBY_X = 4127
    const val LOBBY_Z = 5508

    /** East of the cavity, in the Chasm of Tears. Guessed. */
    const val CHASM_X = 4054
    const val CHASM_Z = 4567

    internal suspend fun Dialogue.say(talk: List<Said>, anim: ChatAnim = ChatAnim.NEUTRAL) {
        for (said in talk) if (said.npc) chatNpc(anim, said.line) else chatPlayer(anim, said.line)
    }

    internal suspend fun Dialogue.montoya(): Unit = with(GeneralMontoyaLines) {
        say(GREETING_TALK)
        while (true) {
            val (asked, talk) = when (choice(ARCHIVE, ENEMIES, EVERYBODY, THANKS)) {
                1 -> ARCHIVE to ARCHIVE_TALK
                2 -> ENEMIES to ENEMIES_TALK
                3 -> EVERYBODY to EVERYBODY_TALK
                else -> {
                    chatPlayer(ChatAnim.HAPPY, THANKS)
                    chatNpc(ChatAnim.HAPPY, THANKS_ANSWER, "name" to player.username)
                    return@with
                }
            }
            chatPlayer(ChatAnim.QUIZ, asked)
            say(talk)
        }
    }

    /** Whether the player asked to see the statistics. */
    private suspend fun Dialogue.viewStats(): Boolean = with(RecordKeeperLines) {
        choice(YES, NO, title = text(VIEW_STATS)) == 1
    }

    internal suspend fun Dialogue.recordKeeper(): Unit = with(RecordKeeperLines) {
        say(INTRO_TALK)
        if (viewStats()) return@with
        while (true) {
            val (asked, talk) = when (choice(DOING, THINGS, GOODBYE)) {
                1 -> DOING to DOING_TALK
                2 -> THINGS to THINGS_TALK
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, GOODBYE)
                    say(GOODBYE_TALK)
                    return@with
                }
            }
            chatPlayer(ChatAnim.QUIZ, asked)
            say(talk)
            if (viewStats()) return@with
        }
    }

    internal suspend fun Dialogue.apprentice(met: Boolean) =
        say(if (met) WizardApprenticeLines.LATER_TALK else WizardApprenticeLines.FIRST_TALK)

    internal suspend fun Dialogue.banker(): Bankers.Ask {
        chatNpc(ChatAnim.HAPPY, ArchiveBankerLines.GREETING)
        when (choice(BankerLines.OPEN_BANK, BankerLines.PIN, BankerLines.COLLECT, BankerLines.BUY_SLOTS, ArchiveBankerLines.FUNCTION)) {
            1 -> return Bankers.Ask.BANK
            2 -> return Bankers.Ask.PIN
            3 -> return Bankers.Ask.COLLECT
            4 -> chatPlayer(ChatAnim.QUIZ, BankerLines.BUY_SLOTS)
            5 -> {
                chatPlayer(ChatAnim.QUIZ, ArchiveBankerLines.FUNCTION)
                say(ArchiveBankerLines.FUNCTION_TALK)
            }
        }
        return Bankers.Ask.NOTHING
    }
}

fun registerArchiveLobby(world: World) {
    with(ArchiveLobby) {
        world.handlers.onNpc(GENERAL_MONTOYA, TALK_TO) { op -> world.dialogues.start(this, op.npc) { montoya() } }
        world.handlers.onNpc(RECORD_KEEPER, TALK_TO) { op -> world.dialogues.start(this, op.npc) { recordKeeper() } }
        world.handlers.onNpc(WIZARD_APPRENTICE, TALK_TO) { op ->
            val met = player.vars.getVarbit(VARBIT_APPRENTICE_MET) == 1
            world.dialogues.start(this, op.npc) { apprentice(met) }
            if (!met && player.vars.types.varbit(VARBIT_APPRENTICE_MET) != null) player.vars.setVarbit(VARBIT_APPRENTICE_MET, 1)
        }
        world.handlers.onNpc(BANKER, TALK_TO) { op ->
            var ask = Bankers.Ask.NOTHING
            world.dialogues.start(this, op.npc) { ask = banker() }
            when (ask) {
                Bankers.Ask.BANK -> world.bankScreen.open(player)
                Bankers.Ask.PIN -> world.bankPinSettings.open(player)
                Bankers.Ask.COLLECT -> world.grandExchangeScreen.openCollectionBox(player)
                Bankers.Ask.NOTHING -> Unit
            }
        }
        world.handlers.onNpc(BANKER, BANK) { world.bankScreen.open(player) }
        world.handlers.onNpc(BANKER, COLLECT) { world.grandExchangeScreen.openCollectionBox(player) }

        world.handlers.onLoc(CAVITY, CRAWL) {
            // Only the Opening crawls; the blocked cavity has no options to click.
            if (player.vars.getVarbit(VARBIT_CAVITY) != 1) return@onLoc
            world.teleport(player, LOBBY_X, LOBBY_Z, 0)
        }
        world.handlers.onLoc(CAVITY_UPPER, CRAWL) { world.teleport(player, LOBBY_X, LOBBY_Z, 0) }
        world.handlers.onLoc(LOBBY_EXIT, ENTER) { world.teleport(player, CHASM_X, CHASM_Z, 0) }
    }
}
