package osrsps.game.content

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.map.Tile

/**
 * Father Aereck's lines once The Restless Ghost is done, as since 242.1 (he took over the Prayer tutor's tips), from the
 * Wiki's Transcript:Father Aereck, read 2026-11-08.
 */
object AereckLines : Lines("father_aereck", "Transcript:Father Aereck") {
    val GREETING = line("greeting", "his first line, welcoming the player to the church")
    val MORE_PRAYERS = line("more_prayers", "the option about prayers")
    val PRAYERS = line("prayers", "prayers: what the player says (shorter than the option)")
    val PRAYERS_1 = line("prayers_1", "prayers: Saradomin's benefits for his followers")
    val SARADOMIN_THING = line("saradomin_thing", "prayers: the player asks whether prayers are only Saradomin's")
    val OTHER_GODS = line("other_gods", "prayers: why pray to other gods")
    val CURIOUS = line("curious", "prayers: the player asks what the benefits are")
    val BATTLE = line("battle", "prayers: useful in battle")
    val PROTECTION = line("protection", "prayers: protection from attacks")
    val HINT_PRAYERS = line("hint_prayers", "prayers: the Prayer panel box")
    val NOT_FREE = line("not_free", "prayers: it takes focus and faith")
    val HINT_POINTS = line("hint_points", "prayers: the Prayer Points box")
    val FAITH = line("faith", "prayers: the player asks how not to lose faith")
    val ALTARS = line("altars", "prayers: the church's altar")
    val HINT_ALTAR = line("hint_altar", "prayers: the altar icon's box (item 9714)")
    val ANYTHING_ELSE = line("anything_else", "prayers: the player asks for anything else")
    val DEDICATION = line("dedication", "prayers: the greater benefits need dedication")
    val HOW = line("how", "prayers: the player asks how")
    val BURY = line("bury", "prayers: burying bones")
    val HINT_BONES = line("hint_bones", "prayers: the bones box (item 526)")
    val COVERS = line("covers", "prayers: that covers everything")
    val HINT_GUIDE = line("hint_guide", "prayers: the Skills panel box")
    val THANKS = line("thanks", "prayers: the player's thanks")
    val SARADOMIN = line("saradomin", "the option about Saradomin, which the player then says")
    val SARADOMIN_1 = line("saradomin_1", "Saradomin: the god of all that is good")
    val SARADOMIN_2 = line("saradomin_2", "Saradomin: the largest religion")
    val INFO_THANKS = line("info_thanks", "Saradomin: option 1, which the player then says")
    val SURPRISED = line("surprised", "Saradomin, option 1: his surprise")
    val NOT_FROM_WORLD = line("not_from_world", "Saradomin: option 2, which the player then says")
    val TENTACLES = line("tentacles", "Saradomin, option 2: no slime or tentacles")
    val ONLINE_GAME = line("online_game", "Saradomin, option 2: sub-option 1, which the player then says")
    val PARDON = line("pardon", "Saradomin, online game: his reply")
    val NEVER_MIND = line("never_mind", "Saradomin, online game: the player's last line")
    val JOKING = line("joking", "Saradomin, option 2: sub-option 2, which the player then says")
    val I_SEE = line("i_see", "Saradomin, joking: his reply")
    val LEAVE_FIRST = line("leave_first", "the leaving option before another was picked, which the player then says")
    val LEAVE = line("leave", "the leaving option after another was picked, which the player then says")
    val GUIDE_YOU = line("guide_you", "his last line")
}

/**
 * Nigel's lines on a normal world, from the Wiki's Transcript:Nigel (its standard dialogue), read 2026-10-30. The
 * Deadman questions are two pages of options.
 */
object NigelLines : Lines("nigel", "Transcript:Nigel") {
    val WHO = line("who", "the player's first line")
    val INTRO = line("intro", "he tells the player about Deadman Mode")
    val QUESTION = line("question", "the option, which the player then says")
    val WHAT_LIKE = line("what_like", "question: what the player would like to know")
    val WHAT_IS = line("what_is", "questions page 1, option 1, which the player then says")
    val WHAT_IS_1 = line("what_is_1", "what is: the mode in short")
    val LUCRATIVE = line("lucrative", "what is: the player picks up on lucrative")
    val WHAT_IS_2 = line("what_is_2", "what is: a kill's rewards and the loser's losses")
    val BRUTAL = line("brutal", "what is: the player's reply")
    val WHAT_IS_3 = line("what_is_3", "what is: killing is hard")
    val WHAT_IS_4 = line("what_is_4", "what is: the guards")
    val HOW_PLAY = line("how_play", "questions page 1, option 2, which the player then says")
    val HOW_PLAY_1 = line("how_play_1", "how to play: pick a Deadman world")
    val NEW_ACCOUNT = line("new_account", "how to play: the player asks about a new account")
    val HOW_PLAY_2 = line("how_play_2", "how to play: an existing account, with membership")
    val TRANSFER = line("transfer", "how to play: the player asks whether progress transfers")
    val HOW_PLAY_3 = line("how_play_3", "how to play: it doesn't")
    val DIE = line("die", "questions page 1, option 3, which the player then says")
    val DIE_1 = line("die_1", "dying: to a player")
    val DIE_2 = line("die_2", "dying: to a monster, skulled")
    val DIE_3 = line("die_3", "dying: to a monster, unskulled")
    val KILL = line("kill", "questions page 1, option 4, which the player then says")
    val KILL_1 = line("kill_1", "killing a player: their items and a key, part 1")
    val KILL_2 = line("kill_2", "killing a player: the chest, part 2")
    val MORE = line("more", "questions page 1, option 5")
    val PROTECT_ITEMS = line("protect_items", "questions page 2, option 1, which the player then says")
    val PROTECT_ITEMS_1 = line("protect_items_1", "protecting items: the safe deposit box")
    val PROTECT_STATS = line("protect_stats", "questions page 2, option 2, which the player then says")
    val PROTECT_STATS_1 = line("protect_stats_1", "protecting stats: which skills")
    val PROTECT_STATS_2 = line("protect_stats_2", "protecting stats: the guards' warning")
    val PROTECT_STATS_3 = line("protect_stats_3", "protecting stats: Gelin's hitpoints insurance")
    val SAFE_ZONES = line("safe_zones", "questions page 2, option 3, which the player then says")
    val SAFE_ZONES_1 = line("safe_zones_1", "safe zones: the guards")
    val PREVIOUS = line("previous", "questions page 2, option 4")
    val REWARDS = line("rewards", "the option")
    val STORE_CLOSED = line("store_closed", "rewards, or Trade: the store isn't open")
    val GOODBYE = line("goodbye", "the option, which the player then says")
}

/**
 * Count Check's lines as since 242.1, which made his talk "more in-universe"; from the Wiki's Transcript:Count Check
 * (Standard dialogue), read 2026-11-08. The Wiki gives only a passed check, so [FAIL] keeps the old talk's words.
 */
object CountCheckLines : Lines("count_check", "Transcript:Count Check") {
    val INTRO_CHECK = line("intro_check", "his first line: the renowned expert on checking, does the player wish to be checked")
    val YES_PLEASE = line("yes_please", "the option to be checked, which the player then says")
    val BEGIN = line("begin", "checked: let us begin")
    val FAIL = line("fail", "checked: no Jagex Account and no Bank PIN (the Wiki gives only the pass; the old talk's words kept)")
    val WHO_ARE_YOU = line("who_are_you", "the option asking who he is, which the player then says")
    val LIKE_I_SAID = line("like_i_said", "who: a vampyre from Morytania, warning of perils")
    val PERILS = line("perils", "who: the player's reply")
    val WARNED = line("warned", "who: be warned, his vampyric ways behind him")
    val OKAY = line("okay", "who: the player's reply")
    val NOW_CHECKED = line("now_checked", "now, would the player like to be checked")
    val SALVE_CROSS = line("salve_cross", "after who: the Salve option, which the player then says")
    val WHAT_MEAN = line("what_mean", "the Salve: 'if' he is a vampyre")
    val ROLE_PLAY = line("role_play", "the Salve: not a role-playing enthusiast")
    val RIGHT_DOTS = line("right_dots", "the Salve: the player's reply")
    val SO_CHECKED = line("so_checked", "the Salve: so, would the player like to be checked")
    val ANYTHING_ELSE = line("anything_else", "the option asking what else he does, which the player then says")
    val NO_BANG = line("no_bang", "anything else, on a main game world: his answer")
    val BEST_BE_OFF = line("best_be_off", "the leaving option (said as is after a check)")
    val MAYBE_LATER = line("maybe_later", "what the player says leaving before a check; also the leaving option after who")
}

/** The altar's game messages; every one's words guessed but the Wiki's use-an-item line. */
object AltarLines : Lines("altar", "Altar") {
    val RECHARGE = line("recharge", "Pray-at, the points recharged")
    val FULL = line("full", "Pray-at with full points")
    val WRATH = line("wrath", "an item used on the altar (the Wiki's words)")
}

/**
 * Lumbridge church and its graveyard: Father Aereck (2812) as he is after The Restless Ghost, with the Prayer tips he
 * took over when 242.1 removed the Prayer tutor, Count Check (7414) and the altar (409) that recharges prayer points.
 * With them Nigel the Deadman tutor (6093, which shows as 8391 on a normal world), whom 242.1 moved to the Grand
 * Exchange on normal worlds. Every account is a member and has every quest done ([osrsps.game.Quests]).
 *
 * From the Wiki: each NPC's lines and branches; Count Check (as since 242.1), asked what else he does, says "No!", as
 * the Wiki has on a main game world, so his once-only teleport to Barbarian Village is never offered here; Father
 * Aereck's picture boxes show the altar icon (item 9714) and bones. Inferred: Nigel's normal world talk offers the
 * Deadman questions, the rewards store (shut, as there is no Deadman Mode) and goodbye, and leaves out the Deadman
 * worlds' options (access to a temporary event, stopping XP); his Trade says the store is shut too. Guessed: that Count
 * Check's check always fails (no account here is a Jagex Account and the bank has no PIN), with the old talk's fail
 * line, and that the fail is followed by the menu the Wiki shows after a pass (the Wiki gives only a pass); that
 * Aereck's sprite boxes (the Prayer panel, the Prayer orb, the Skills panel) are message boxes here; every chathead
 * expression; that each answer goes back to the menu it came from (the Count's "would you like to be checked" questions
 * to his first menu, but after who he is, to the menu with the Salve). The altar: the Wiki says Pray-at recharges prayer
 * points and resets prayer-draining effects' timers (the drain counter here); guessed are the kneel (human_pray,
 * seq 645), that points above the level (a boost) are kept, the messages and that full points still kneel.
 */
object Church {
    const val FATHER_AERECK = 2812
    /** deadman_nigel, a multi-NPC on varp deadman_mode. */
    const val NIGEL = 6093
    /** deadman_nigel_regular, the Nigel a normal world shows. */
    const val NIGEL_REGULAR = 8391
    const val COUNT_CHECK = 7414
    const val ALTAR = 409

    /** The items Father Aereck's boxes show: the altar map icon and bones. */
    const val ALTAR_ICON = 9714
    const val BONES = 526

    const val TALK_TO = 1
    const val TRADE = 4
    const val PRAY_AT = 1

    /** human_pray. */
    const val ANIM_PRAY = 645

    /**
     * Beside the Stronghold of Security's entrance in Barbarian Village (loc 20790 at 3081, 3420), where the Skull
     * sceptre takes the player. Guessed.
     */
    val STRONGHOLD = Tile(3081, 3421)

    /** The first menu, again after each topic; the leaving option's words change once a topic was picked. */
    internal suspend fun Dialogue.aereck() = with(AereckLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        var asked = false
        while (true) {
            when (choice(MORE_PRAYERS, SARADOMIN, if (asked) LEAVE else LEAVE_FIRST)) {
                1 -> prayers()
                2 -> saradomin()
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, if (asked) LEAVE else LEAVE_FIRST)
                    chatNpc(ChatAnim.HAPPY, GUIDE_YOU)
                    return@with
                }
            }
            asked = true
        }
    }

    private suspend fun Dialogue.prayers() = with(AereckLines) {
        chatPlayer(ChatAnim.QUIZ, PRAYERS)
        chatNpc(ChatAnim.HAPPY, PRAYERS_1)
        chatPlayer(ChatAnim.QUIZ, SARADOMIN_THING)
        chatNpc(ChatAnim.NEUTRAL, OTHER_GODS)
        chatPlayer(ChatAnim.QUIZ, CURIOUS)
        chatNpc(ChatAnim.NEUTRAL, BATTLE)
        chatNpc(ChatAnim.NEUTRAL, PROTECTION)
        mesbox(HINT_PRAYERS)
        chatNpc(ChatAnim.NEUTRAL, NOT_FREE)
        mesbox(HINT_POINTS)
        chatPlayer(ChatAnim.QUIZ, FAITH)
        chatNpc(ChatAnim.HAPPY, ALTARS)
        objbox(ALTAR_ICON, HINT_ALTAR)
        chatPlayer(ChatAnim.QUIZ, ANYTHING_ELSE)
        chatNpc(ChatAnim.NEUTRAL, DEDICATION)
        chatPlayer(ChatAnim.QUIZ, HOW)
        chatNpc(ChatAnim.NEUTRAL, BURY)
        objbox(BONES, HINT_BONES)
        chatNpc(ChatAnim.HAPPY, COVERS)
        mesbox(HINT_GUIDE)
        chatPlayer(ChatAnim.HAPPY, THANKS)
    }

    private suspend fun Dialogue.saradomin() = with(AereckLines) {
        chatPlayer(ChatAnim.QUIZ, SARADOMIN)
        chatNpc(ChatAnim.SHOCKED, SARADOMIN_1)
        chatNpc(ChatAnim.HAPPY, SARADOMIN_2)
        if (choice(INFO_THANKS, NOT_FROM_WORLD) == 1) {
            chatPlayer(ChatAnim.NEUTRAL, INFO_THANKS)
            chatNpc(ChatAnim.CONFUSED, SURPRISED)
            return@with
        }
        chatPlayer(ChatAnim.NEUTRAL, NOT_FROM_WORLD)
        chatNpc(ChatAnim.CONFUSED, TENTACLES)
        if (choice(ONLINE_GAME, JOKING) == 1) {
            chatPlayer(ChatAnim.NEUTRAL, ONLINE_GAME)
            chatNpc(ChatAnim.CONFUSED, PARDON)
            chatPlayer(ChatAnim.SAD, NEVER_MIND)
        } else {
            chatPlayer(ChatAnim.NEUTRAL, JOKING)
            chatNpc(ChatAnim.NEUTRAL, I_SEE)
        }
    }

    internal suspend fun Dialogue.nigel() = with(NigelLines) {
        chatPlayer(ChatAnim.QUIZ, WHO)
        chatNpc(ChatAnim.NEUTRAL, INTRO)
        when (choice(QUESTION, REWARDS, GOODBYE)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, QUESTION)
                chatNpc(ChatAnim.NEUTRAL, WHAT_LIKE)
                deadmanQuestions()
            }
            2 -> chatNpc(ChatAnim.SAD, STORE_CLOSED)
            else -> chatPlayer(ChatAnim.NEUTRAL, GOODBYE)
        }
    }

    /** The two pages of Deadman questions; each answer goes back to the page it came from, until a pick ends it. */
    private suspend fun Dialogue.deadmanQuestions() = with(NigelLines) {
        var page = 1
        while (true) {
            if (page == 1) {
                when (choice(WHAT_IS, HOW_PLAY, DIE, KILL, MORE)) {
                    1 -> {
                        chatPlayer(ChatAnim.QUIZ, WHAT_IS)
                        chatNpc(ChatAnim.NEUTRAL, WHAT_IS_1)
                        chatPlayer(ChatAnim.QUIZ, LUCRATIVE)
                        chatNpc(ChatAnim.NEUTRAL, WHAT_IS_2)
                        chatPlayer(ChatAnim.SHOCKED, BRUTAL)
                        chatNpc(ChatAnim.NEUTRAL, WHAT_IS_3)
                        chatNpc(ChatAnim.NEUTRAL, WHAT_IS_4)
                    }
                    2 -> {
                        chatPlayer(ChatAnim.QUIZ, HOW_PLAY)
                        chatNpc(ChatAnim.NEUTRAL, HOW_PLAY_1)
                        chatPlayer(ChatAnim.QUIZ, NEW_ACCOUNT)
                        chatNpc(ChatAnim.NEUTRAL, HOW_PLAY_2)
                        chatPlayer(ChatAnim.QUIZ, TRANSFER)
                        chatNpc(ChatAnim.NEUTRAL, HOW_PLAY_3)
                    }
                    3 -> {
                        chatPlayer(ChatAnim.QUIZ, DIE)
                        chatNpc(ChatAnim.NEUTRAL, DIE_1)
                        chatNpc(ChatAnim.NEUTRAL, DIE_2)
                        chatNpc(ChatAnim.NEUTRAL, DIE_3)
                    }
                    4 -> {
                        chatPlayer(ChatAnim.QUIZ, KILL)
                        chatNpc(ChatAnim.NEUTRAL, KILL_1)
                        chatNpc(ChatAnim.NEUTRAL, KILL_2)
                    }
                    else -> page = 2
                }
            } else {
                when (choice(PROTECT_ITEMS, PROTECT_STATS, SAFE_ZONES, PREVIOUS)) {
                    1 -> {
                        chatPlayer(ChatAnim.QUIZ, PROTECT_ITEMS)
                        chatNpc(ChatAnim.NEUTRAL, PROTECT_ITEMS_1)
                    }
                    2 -> {
                        chatPlayer(ChatAnim.QUIZ, PROTECT_STATS)
                        chatNpc(ChatAnim.NEUTRAL, PROTECT_STATS_1)
                        chatNpc(ChatAnim.NEUTRAL, PROTECT_STATS_2)
                        chatNpc(ChatAnim.NEUTRAL, PROTECT_STATS_3)
                    }
                    3 -> {
                        chatPlayer(ChatAnim.QUIZ, SAFE_ZONES)
                        chatNpc(ChatAnim.NEUTRAL, SAFE_ZONES_1)
                    }
                    else -> page = 1
                }
            }
        }
    }

    internal suspend fun Dialogue.nigelTrade() = chatNpc(ChatAnim.SAD, NigelLines.STORE_CLOSED)

    /** Count Check's talk: his first menu, the one after who he is, and the one after a check. */
    internal suspend fun Dialogue.countCheck() = with(CountCheckLines) {
        chatNpc(ChatAnim.LAUGH, INTRO_CHECK)
        var menu = 0
        while (true) {
            val picked = when (menu) {
                0 -> listOf(YES_PLEASE, WHO_ARE_YOU, ANYTHING_ELSE, BEST_BE_OFF)
                1 -> listOf(YES_PLEASE, SALVE_CROSS, ANYTHING_ELSE, MAYBE_LATER)
                else -> listOf(WHO_ARE_YOU, ANYTHING_ELSE, BEST_BE_OFF)
            }.let { it[choice(*it.toTypedArray()) - 1] }
            when (picked) {
                YES_PLEASE -> {
                    chatPlayer(ChatAnim.HAPPY, YES_PLEASE)
                    chatNpc(ChatAnim.LAUGH, BEGIN)
                    // Guessed: the check fails with the old talk's line, then the menu the Wiki shows after a pass.
                    chatNpc(ChatAnim.SAD, FAIL)
                    menu = 2
                }
                WHO_ARE_YOU -> {
                    chatPlayer(ChatAnim.QUIZ, WHO_ARE_YOU)
                    chatNpc(ChatAnim.NEUTRAL, LIKE_I_SAID)
                    chatPlayer(ChatAnim.QUIZ, PERILS)
                    chatNpc(ChatAnim.NEUTRAL, WARNED)
                    chatPlayer(ChatAnim.CONFUSED, OKAY)
                    chatNpc(ChatAnim.QUIZ, NOW_CHECKED)
                    menu = 1
                }
                SALVE_CROSS -> {
                    chatPlayer(ChatAnim.QUIZ, SALVE_CROSS)
                    chatNpc(ChatAnim.ANGRY, WHAT_MEAN)
                    chatNpc(ChatAnim.ANGRY, ROLE_PLAY)
                    chatPlayer(ChatAnim.NEUTRAL, RIGHT_DOTS)
                    chatNpc(ChatAnim.QUIZ, SO_CHECKED)
                }
                ANYTHING_ELSE -> {
                    chatPlayer(ChatAnim.QUIZ, ANYTHING_ELSE)
                    chatNpc(ChatAnim.ANGRY, NO_BANG)
                    chatNpc(ChatAnim.QUIZ, NOW_CHECKED)
                    menu = 0
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, if (picked == BEST_BE_OFF && menu == 2) BEST_BE_OFF else MAYBE_LATER)
                    return@with
                }
            }
        }
    }

    /** Pray-at: the points back up to the Prayer level, the drain counter reset. */
    fun pray(player: Player) {
        player.anim(ANIM_PRAY)
        player.prayerDrainCounter = 0
        val level = player.skills.baseLevel(Skill.PRAYER)
        if (player.skills.currentLevel(Skill.PRAYER) < level) player.skills.setCurrentLevel(Skill.PRAYER, level)
    }
}

/** The church NPCs' options and the altar's Pray-at. */
fun registerChurch(world: World) {
    world.handlers.onNpc(Church.FATHER_AERECK, Church.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(Church) { aereck() } }
    }
    world.handlers.onNpc(Church.NIGEL_REGULAR, Church.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(Church) { nigel() } }
    }
    world.handlers.onNpc(Church.NIGEL_REGULAR, Church.TRADE) { op ->
        world.dialogues.start(this, op.npc) { with(Church) { nigelTrade() } }
    }
    world.handlers.onNpc(Church.COUNT_CHECK, Church.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(Church) { countCheck() } }
    }
    world.handlers.onLoc(Church.ALTAR, Church.PRAY_AT) { prayAt(world) }
    world.handlers.onObjOnLoc(Church.ALTAR) { world.message(player, AltarLines.WRATH) }
}

/** Pray-at on an altar: arrive, then recharge prayer points. Varrock's church and chapel altars use it too. */
internal suspend fun ScriptContext.prayAt(world: World) {
    arriveDelay()
    val full = player.skills.currentLevel(Skill.PRAYER) >= player.skills.baseLevel(Skill.PRAYER)
    Church.pray(player)
    world.message(player, if (full) AltarLines.FULL else AltarLines.RECHARGE)
}
