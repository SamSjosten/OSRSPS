package osrsps.game.content

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.skill.Slayer
import osrsps.game.skill.SlayerMaster

/**
 * What every Slayer master says alike (the Wiki's Transcript:Aya, Transcript:Spria and Transcript:Vannaka, read
 * 2026-11-15: their lines are word for word the same but where a master has a line of their own below).
 */
object SlayerMasterLines : Lines("slayer_master", "Transcript:Vannaka") {
    val GREETING = line("greeting", "Talk-to: the master's first line")
    val NEED_TASK = line("need_task", "first menu, option 1, which the player then says: another assignment")
    val REWARDS = line("rewards", "first menu, option 2, which the player then says: rewards or trade")
    val DIFFICULTY = line("difficulty", "first menu, option 3, which the player then says: the difficulty of assignments")
    val NOTHING = line("nothing", "first menu, last option, which the player then says")
    val NEW_TASK = line("new_task", "with no task: the master gives the new one", "amount", "task")
    val TIPS = line("tips", "after a new task, option 1, which the player then says: tips")
    val TIPS_THANKS = line("tips_thanks", "after the tip: the player's thanks")
    val OKAY_GREAT = line("okay_great", "after a new task, option 2, which the player then says")
    val GOOD_LUCK = line("good_luck", "option 2: the master's answer, to come back for another")
    val STILL_HUNTING = line("still_hunting", "with a task already: Aya's and Spria's line, the task and how many are left", "task", "amount")
    val STILL_HUNTING_COME_BACK = line("still_hunting_come_back", "with a task already: Vannaka's line, which also says to come back when it's done", "task", "amount")
    val REWARDS_ANSWER = line("rewards_answer", "option 2: the master's answer, before the rewards menu")
    val LOOK_REWARDS = line("look_rewards", "the rewards menu, option 1")
    val LOOK_SHOP = line("look_shop", "the rewards menu, option 2")
    val CANCEL = line("cancel", "the rewards menu, option 3")
    val CHECK_OFF = line("check_off", "option 3 with the combat check off: the master says any task may be given")
    val ANY_TASK = line("any_task", "that menu, option 1, which the player then says: any task is fine")
    val SPIRIT = line("spirit", "option 1: the master's answer")
    val NOT_TOO_TOUGH = line("not_too_tough", "that menu, option 2, which the player then says: nothing too tough")
    val CHECK_ON_NOW = line("check_on_now", "option 2: the master turns the check on for every master")
    val CHECK_ON = line("check_on", "option 3 with the combat check on: the master says they check the combat level")
    val KEEP_CHECKING = line("keep_checking", "that menu, option 1, which the player then says")
    val KEEP_CHECKING_ANSWER = line("keep_checking_answer", "option 1: the master's answer")
    val STOP_CHECKING = line("stop_checking", "that menu, option 2, which the player then says: anything will do")
    val CHECK_OFF_NOW = line("check_off_now", "option 2: the master turns the check off for every master")
    val STRONG_MAZCHNA = line("strong_mazchna", "Aya or Spria to a player of combat 20 to 39: Mazchna would suit them better (Transcript:Spria)")
    val STRONG_VANNAKA = line("strong_vannaka", "Aya or Spria to combat 40 to 69: Vannaka would (Transcript:Spria)")
    val STRONG_CHAELDAR = line("strong_chaeldar", "Aya or Spria to combat 70 and over: Chaeldar would")
    val TAKE_FROM_YOU = line("take_from_you", "that menu, option 1, which the player then says: a task from them anyway")
    val GO_MAZCHNA = line("go_mazchna", "that menu, option 2, which the player then says: off to Mazchna")
    val GO_VANNAKA = line("go_vannaka", "that menu, option 2, which the player then says: off to Vannaka")
    val GO_CHAELDAR = line("go_chaeldar", "that menu, option 2, which the player then says: off to Chaeldar")
    val STILL_HUNTING_WITH = line("still_hunting_with", "with a task the player is no longer fit for: the task and how many are left", "task", "amount")
    val NOT_SUITABLE = line("not_suitable", "then: the offer to cancel it, which keeps the streaks")
    val YES_CANCEL = line("yes_cancel", "that menu, option 1, which the player then says")
    val CANCELLED = line("cancelled", "option 1: the master's answer, the task gone")
    val NO_TRY = line("no_try", "that menu, option 2, which the player then says")
    val GOOD_LUCK_WITH_THAT = line("good_luck_with_that", "option 2: the master's answer")
    val TOO_WEAK = line("too_weak", "a master whose combat level the player hasn't reached; the Wiki's transcripts leave it out, words unsourced")
    val NOTHING_TO_GIVE = line("nothing_to_give", "a master with no task the player may be given; words unsourced")
}

/** Aya's own lines (the Wiki's Transcript:Aya, read 2026-11-15). */
object AyaLines : Lines("aya", "Transcript:Aya") {
    val NOT_MINE = line("not_mine", "with another master's task that isn't on her list: she offers a new one")
    val RESET_STREAK = line("reset_streak", "the warning that a new task resets the standard streak but not the Wilderness one", "streak", "wilderness")
    val YES_PLEASE = line("yes_please", "that menu, option 1")
    val NO_THANKS = line("no_thanks", "that menu, option 2, which the player then says")
    val ABOUT_TURAEL = line("about_turael", "first menu after While Guthix Sleeps, which the player then says")
    val CONDOLENCES = line("condolences", "that talk: Aya's first line")
    val YOU_HAVE = line("you_have", "the player's answer")
    val THRILL_SEEKER = line("thrill_seeker", "Aya on her brother")
    val SORRY = line("sorry", "the player's apology")
    val HERO = line("hero", "Aya's last line")
}

/** Mazchna's own lines (the Wiki's Transcript:Mazchna, read 2026-11-15). */
object MazchnaLines : Lines("mazchna", "Transcript:Mazchna") {
    val STILL_HUNTING = line("still_hunting", "with a task already: her line, worded with a comma where the others have a semicolon", "task", "amount")
    val ABOUT_LUCIEN = line("about_lucien", "first menu after While Guthix Sleeps, which the player then says")
    val RECKONING = line("reckoning", "that talk: Mazchna's first line")
    val SO_WILL_I = line("so_will_i", "the player's answer")
    val SIDE_BY_SIDE = line("side_by_side", "Mazchna's last line")
}

/** Steve's own lines (the Wiki's Transcript:Steve, read 2026-11-15), after Monkey Madness II. */
object SteveLines : Lines("steve", "Transcript:Steve") {
    val NEW_MASTER = line("new_master", "first menu, which the player then says: the new Slayer Master here")
    val NIEVE_WAS = line("nieve_was", "after Monkey Madness II: Steve on his cousin Nieve")
    val NO_SHIELD = line("no_shield", "then: no shield like Nieve's")
    val HOW_SAD = line("how_sad", "the player's answer")
}

/** Kuradal's own lines (the Wiki's Transcript:Kuradal, read 2026-11-15). */
object KuradalLines : Lines("kuradal", "Transcript:Kuradal") {
    val SKILLCAPE = line("skillcape", "first menu below 99 Slayer, which the player then says")
    val SKILLCAPE_ABOUT = line("skillcape_about", "Kuradal on the Slayer cape")
    val MORE_TRAINING = line("more_training", "then: more training needed")
}

/** The tip a master gives for each task (the Wiki's Transcript:Slayer task tips), by the cache's task id. */
object SlayerTipLines : Lines("slayer_tip", "Transcript:Slayer task tips") {
    private val SLUGS = mapOf(
        1 to "monkeys", 2 to "goblins", 3 to "rats", 4 to "spiders", 5 to "birds", 6 to "cows", 7 to "scorpions", 8 to "bats",
        9 to "wolves", 10 to "zombies", 11 to "skeletons", 12 to "ghosts", 13 to "bears", 14 to "hill_giants", 15 to "ice_giants",
        16 to "fire_giants", 17 to "moss_giants", 18 to "trolls", 19 to "ice_warriors", 20 to "ogres", 21 to "hobgoblins",
        22 to "dogs", 23 to "ghouls", 24 to "green_dragons", 25 to "blue_dragons", 26 to "red_dragons", 27 to "black_dragons",
        28 to "lesser_demons", 29 to "greater_demons", 30 to "black_demons", 31 to "hellhounds", 32 to "shadow_warriors",
        33 to "werewolves", 34 to "vampyres", 35 to "dagannoth", 36 to "turoth", 37 to "cave_crawlers", 38 to "banshees",
        39 to "crawling_hands", 40 to "infernal_mages", 41 to "aberrant_spectres", 42 to "abyssal_demons", 43 to "basilisks",
        44 to "cockatrice", 45 to "kurask", 46 to "gargoyles", 47 to "pyrefiends", 48 to "bloodveld", 49 to "dust_devils",
        50 to "jellies", 51 to "rockslugs", 52 to "nechryael", 53 to "kalphites", 54 to "earth_warriors", 55 to "otherworldly_beings",
        56 to "elves", 57 to "dwarves", 58 to "bronze_dragons", 59 to "iron_dragons", 60 to "steel_dragons", 61 to "wall_beasts",
        62 to "cave_slimes", 63 to "cave_bugs", 64 to "shades", 65 to "crocodiles", 66 to "dark_beasts", 67 to "mogres",
        68 to "lizards", 69 to "fever_spiders", 70 to "harpie_bug_swarms", 71 to "sea_snakes", 72 to "skeletal_wyverns",
        73 to "killerwatts", 74 to "mutated_zygomites", 75 to "icefiends", 76 to "minotaurs", 77 to "fleshcrawlers", 78 to "catablepon",
        79 to "ankou", 80 to "cave_horrors", 81 to "jungle_horrors", 83 to "suqahs", 84 to "brine_rats", 85 to "scabarites",
        86 to "terror_dogs", 87 to "molanisks", 88 to "waterfiends", 89 to "spiritual_creatures", 90 to "lizardmen", 91 to "magic_axes",
        92 to "cave_kraken", 93 to "mithril_dragons", 94 to "aviansies", 95 to "smoke_devils", 96 to "tzhaar", 97 to "tztok_jad",
        99 to "mammoths", 100 to "rogues", 101 to "ents", 102 to "bandits", 103 to "dark_warriors", 104 to "lava_dragons",
        105 to "tzkal_zuk", 106 to "fossil_island_wyverns", 107 to "revenants", 108 to "adamant_dragons", 109 to "rune_dragons",
        110 to "chaos_druids", 111 to "wyrms", 112 to "drakes", 113 to "hydras", 114 to "temple_spiders", 115 to "undead_druids",
        116 to "sulphur_lizards", 117 to "brutal_black_dragons", 118 to "sand_crabs", 119 to "black_knights", 120 to "pirates",
        121 to "sourhogs", 122 to "warped_creatures", 123 to "lesser_nagua", 124 to "araxytes", 125 to "crabs",
        126 to "custodian_stalkers", 127 to "metal_dragons", 128 to "gryphons", 129 to "aquanites", 130 to "frost_dragons",
        131 to "venators",
    )

    /** By task id; the 242 cache's 130 tasks but the boss task (98), whose tips are each boss's. */
    val BY_TASK: Map<Int, Line> = SLUGS.mapValues { (_, slug) -> line(slug, "the tip for the ${slug.replace('_', ' ')} task") }
}

/**
 * The Slayer masters with every quest done: Aya in Burthorpe, Spria in Draynor and Vannaka in Edgeville Dungeon (Slayer
 * step 2), and Mazchna in Canifis, Chaeldar in Zanaris, Steve at the Stronghold Slayer Cave and Kuradal in Shilo Village
 * (step 3, built in place and reached by ::tele). Talk-to and Assignment, from the Wiki's transcripts. With every quest
 * done Aya holds Turael's place (since While Guthix Sleeps), Kuradal Duradel's (the same) and Steve Nieve's (since Monkey
 * Madness II) (OSRS Wiki, Aya, Kuradal, Steve). Talk-to's menu: another assignment, rewards or trade ("Look at rewards" opens
 * [SlayerRewards], "Look at shop" and Trade the Slayer Equipment shop), the difficulty of assignments (turning the combat level check off and on
 * for every master, varbit combat_level_reqs_disabled), each master's own talk (Aya's about Turael, Mazchna's about
 * Lucien, Steve's about Nieve, Kuradal's about the Slayer cape below 99 Slayer), or nothing. Assignment goes straight to
 * the assignment.
 *
 * Asked for an assignment, a master below whose combat (and Slayer) level the player is turns them away; with a task
 * the player is no longer fit for ([Slayer.fit]) they offer to cancel it, keeping the streaks; with another task, they
 * say what is left; with none they give a new one ([Slayer.roll]). Aya and Spria first suggest a higher master to a
 * strong player (Spria's transcript's bands: Mazchna 20 to 39, Vannaka 40 to 69, Chaeldar 70 and over; inferred the same
 * for Aya, whose transcript gives only Chaeldar), and Mazchna suggests Chaeldar (inferred at 70 and over). After a new
 * task every master but Aya, Spria and Steve offers a tip ([SlayerTipLines]). Aya swaps another master's task that isn't
 * on her own list for one of hers, which resets the streak (OSRS Wiki, Aya); Spria doesn't.
 *
 * Guessed: the chathead expressions; every line the transcripts leave out (a master turning a weak player away, a master
 * with nothing to give, what follows Aya's "Yes, please", taken to be her new task line); the cancel offer coming before
 * Aya's swap; Mazchna's band. Left out: Aya's blessed axe talk (Animal Magnetism's axe), Kuradal's Slayer cape sale at 99
 * and her talk about Duradel (Duradel's notes), Spria's cancel of a Morytania task, and the Slayer cape letting a master
 * overlook the combat level. Tiles are mejrs's (live) tiles.
 */
class SlayerMasters(private val world: World) {
    companion object {
        const val TALK_TO = 1
        const val ASSIGNMENT = 3

        /** What Talk-to's rewards menu leaves to open once the talk is over. */
        private const val LOOK_REWARDS = 1
        private const val LOOK_SHOP = 2

        /** The masters placed so far; Vannaka stood waiting in Edgeville Dungeon. */
        val BUILT = listOf(
            SlayerMaster.AYA, SlayerMaster.SPRIA, SlayerMaster.VANNAKA, SlayerMaster.MAZCHNA, SlayerMaster.CHAELDAR,
            SlayerMaster.STEVE, SlayerMaster.KURADAL,
        )

        /** The masters whose transcripts end at the new task, with no tip offered. */
        private val NO_TIPS = setOf(SlayerMaster.AYA, SlayerMaster.SPRIA, SlayerMaster.STEVE)
    }

    private val slayer get() = world.slayer

    fun register() {
        for (master in BUILT) {
            world.handlers.onNpc(master.npc, TALK_TO) { op ->
                var look = 0
                world.dialogues.start(this, op.npc) { look = talk(master) }
                when (look) {
                    LOOK_REWARDS -> world.slayerRewards.open(player, master)
                    LOOK_SHOP -> trade(player, master)
                }
            }
            world.handlers.onNpc(master.npc, ASSIGNMENT) { op -> world.dialogues.start(this, op.npc) { assignment(master) } }
        }
    }

    /** The master's own option in Talk-to's menu, before "Er... Nothing...", if they have one. */
    private fun Dialogue.ownOption(master: SlayerMaster): Line? = when (master) {
        SlayerMaster.AYA -> AyaLines.ABOUT_TURAEL
        SlayerMaster.MAZCHNA -> MazchnaLines.ABOUT_LUCIEN
        SlayerMaster.STEVE -> SteveLines.NEW_MASTER
        SlayerMaster.KURADAL -> KuradalLines.SKILLCAPE.takeIf { player.skills.baseLevel(Skill.SLAYER) < 99 }
        else -> null
    }

    /** The Slayer Equipment shop, which every master runs (OSRS Wiki, Slayer Equipment); Trade opens it through [registerShops]. */
    private fun trade(player: Player, master: SlayerMaster) {
        world.shopTypes.ofNpc(master.npc)?.let { world.shopScreen.open(player, it) }
    }

    /**
     * Talk-to; [LOOK_REWARDS] or [LOOK_SHOP] when the player picked "Look at rewards" or "Look at shop", which opens the
     * rewards screen or the shop once the talk is over, else 0.
     */
    private suspend fun Dialogue.talk(master: SlayerMaster): Int = with(SlayerMasterLines) {
        chatNpc(ChatAnim.NEUTRAL, GREETING)
        val options = listOfNotNull(NEED_TASK, REWARDS, DIFFICULTY, ownOption(master), NOTHING)
        when (options[choice(*options.toTypedArray()) - 1]) {
            NEED_TASK -> {
                chatPlayer(ChatAnim.NEUTRAL, NEED_TASK)
                assignment(master)
            }
            REWARDS -> {
                chatPlayer(ChatAnim.NEUTRAL, REWARDS)
                chatNpc(ChatAnim.NEUTRAL, REWARDS_ANSWER)
                return when (choice(LOOK_REWARDS, LOOK_SHOP, CANCEL)) {
                    1 -> SlayerMasters.LOOK_REWARDS
                    2 -> SlayerMasters.LOOK_SHOP
                    else -> 0
                }
            }
            DIFFICULTY -> {
                chatPlayer(ChatAnim.NEUTRAL, DIFFICULTY)
                difficulty()
            }
            AyaLines.ABOUT_TURAEL -> aboutTurael()
            MazchnaLines.ABOUT_LUCIEN -> aboutLucien()
            SteveLines.NEW_MASTER -> aboutNieve()
            KuradalLines.SKILLCAPE -> aboutSkillcape()
            else -> chatPlayer(ChatAnim.NEUTRAL, NOTHING)
        }
        0
    }

    private suspend fun Dialogue.aboutTurael(): Unit = with(AyaLines) {
        chatPlayer(ChatAnim.NEUTRAL, ABOUT_TURAEL)
        chatNpc(ChatAnim.SAD, CONDOLENCES)
        chatPlayer(ChatAnim.QUIZ, YOU_HAVE)
        chatNpc(ChatAnim.SAD, THRILL_SEEKER)
        chatPlayer(ChatAnim.SAD, SORRY)
        chatNpc(ChatAnim.NEUTRAL, HERO)
    }

    private suspend fun Dialogue.aboutLucien(): Unit = with(MazchnaLines) {
        chatPlayer(ChatAnim.NEUTRAL, ABOUT_LUCIEN)
        chatNpc(ChatAnim.ANGRY, RECKONING)
        chatPlayer(ChatAnim.ANGRY, SO_WILL_I)
        chatNpc(ChatAnim.ANGRY, SIDE_BY_SIDE)
    }

    private suspend fun Dialogue.aboutNieve(): Unit = with(SteveLines) {
        chatPlayer(ChatAnim.NEUTRAL, NEW_MASTER)
        chatNpc(ChatAnim.NEUTRAL, NIEVE_WAS)
        chatNpc(ChatAnim.SAD, NO_SHIELD)
        chatPlayer(ChatAnim.NEUTRAL, HOW_SAD)
    }

    private suspend fun Dialogue.aboutSkillcape(): Unit = with(KuradalLines) {
        chatPlayer(ChatAnim.QUIZ, SKILLCAPE)
        chatNpc(ChatAnim.NEUTRAL, SKILLCAPE_ABOUT)
        chatNpc(ChatAnim.NEUTRAL, MORE_TRAINING)
    }

    /** "I need another assignment", or the Assignment option. */
    private suspend fun Dialogue.assignment(master: SlayerMaster): Unit = with(SlayerMasterLines) {
        if (!slayer.meets(player, master)) return chatNpc(ChatAnim.NEUTRAL, TOO_WEAK)
        val task = slayer.task(player)
        if (task != null) {
            if (!slayer.fit(player, task)) return offerCancel(task.nameLower)
            val line = when (master) {
                SlayerMaster.AYA, SlayerMaster.SPRIA -> STILL_HUNTING
                SlayerMaster.MAZCHNA -> MazchnaLines.STILL_HUNTING
                else -> STILL_HUNTING_COME_BACK
            }
            chatNpc(ChatAnim.NEUTRAL, line, "task" to task.nameLower, "amount" to slayer.remaining(player))
            // Spria's tasks can't be skipped through Turael (OSRS Wiki, Spria), so Aya keeps hers too.
            if (master == SlayerMaster.AYA && slayer.master(player) != SlayerMaster.SPRIA && slayer.data.entry(SlayerMaster.AYA.id, task.id) == null) swap()
            return
        }
        if (!suggestHigher(master)) return
        newTask(master)
    }

    /** A task the player is no longer fit for: the master offers to cancel it, which keeps the streaks. */
    private suspend fun Dialogue.offerCancel(task: String): Unit = with(SlayerMasterLines) {
        chatNpc(ChatAnim.NEUTRAL, STILL_HUNTING_WITH, "task" to task, "amount" to slayer.remaining(player))
        chatNpc(ChatAnim.QUIZ, NOT_SUITABLE)
        if (choice(YES_CANCEL, NO_TRY) == 1) {
            chatPlayer(ChatAnim.NEUTRAL, YES_CANCEL)
            slayer.clear(player)
            chatNpc(ChatAnim.NEUTRAL, CANCELLED)
        } else {
            chatPlayer(ChatAnim.NEUTRAL, NO_TRY)
            chatNpc(ChatAnim.NEUTRAL, GOOD_LUCK_WITH_THAT)
        }
    }

    /** Aya, Spria and Mazchna to a strong player: false when they go to the higher master instead. */
    private suspend fun Dialogue.suggestHigher(master: SlayerMaster): Boolean = with(SlayerMasterLines) {
        val combat = player.skills.combatLevel
        val (suggest, go) = when {
            master !in setOf(SlayerMaster.AYA, SlayerMaster.SPRIA, SlayerMaster.MAZCHNA) -> return true
            combat >= SlayerMaster.CHAELDAR.combat -> STRONG_CHAELDAR to GO_CHAELDAR
            master == SlayerMaster.MAZCHNA -> return true
            combat >= SlayerMaster.VANNAKA.combat -> STRONG_VANNAKA to GO_VANNAKA
            combat >= SlayerMaster.MAZCHNA.combat -> STRONG_MAZCHNA to GO_MAZCHNA
            else -> return true
        }
        chatNpc(ChatAnim.QUIZ, suggest)
        if (choice(TAKE_FROM_YOU, go) == 1) {
            chatPlayer(ChatAnim.NEUTRAL, TAKE_FROM_YOU)
            return true
        }
        chatPlayer(ChatAnim.NEUTRAL, go)
        return false
    }

    private suspend fun Dialogue.newTask(master: SlayerMaster): Unit = with(SlayerMasterLines) {
        val (entry, amount) = slayer.roll(player, master) ?: return chatNpc(ChatAnim.NEUTRAL, NOTHING_TO_GIVE)
        slayer.assign(player, master, entry.task, amount)
        chatNpc(ChatAnim.HAPPY, NEW_TASK, "amount" to amount, "task" to entry.task.nameLower)
        if (master !in NO_TIPS) tips(entry.task.id)
    }

    /** "Got any tips for me?" or "Okay, great!". */
    private suspend fun Dialogue.tips(task: Int): Unit = with(SlayerMasterLines) {
        if (choice(TIPS, OKAY_GREAT) == 1) {
            chatPlayer(ChatAnim.QUIZ, TIPS)
            SlayerTipLines.BY_TASK[task]?.let { chatNpc(ChatAnim.NEUTRAL, it) }
            chatPlayer(ChatAnim.HAPPY, TIPS_THANKS)
        } else {
            chatPlayer(ChatAnim.HAPPY, OKAY_GREAT)
            chatNpc(ChatAnim.HAPPY, GOOD_LUCK)
        }
    }

    /** Aya's offer to swap another master's task, which resets the standard streak. */
    private suspend fun Dialogue.swap(): Unit = with(AyaLines) {
        chatNpc(ChatAnim.NEUTRAL, NOT_MINE)
        chatNpc(ChatAnim.NEUTRAL, RESET_STREAK, "streak" to slayer.streak(player), "wilderness" to slayer.wildernessStreak(player))
        if (choice(YES_PLEASE, NO_THANKS) != 1) {
            chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
            return
        }
        val (entry, amount) = slayer.roll(player, SlayerMaster.AYA) ?: return chatNpc(ChatAnim.NEUTRAL, SlayerMasterLines.NOTHING_TO_GIVE)
        slayer.setStreak(player, 0)
        slayer.assign(player, SlayerMaster.AYA, entry.task, amount)
        chatNpc(ChatAnim.HAPPY, SlayerMasterLines.NEW_TASK, "amount" to amount, "task" to entry.task.nameLower)
    }

    private suspend fun Dialogue.difficulty(): Unit = with(SlayerMasterLines) {
        if (!slayer.combatChecked(player)) {
            chatNpc(ChatAnim.NEUTRAL, CHECK_OFF)
            if (choice(ANY_TASK, NOT_TOO_TOUGH) == 1) {
                chatPlayer(ChatAnim.NEUTRAL, ANY_TASK)
                chatNpc(ChatAnim.HAPPY, SPIRIT)
            } else {
                chatPlayer(ChatAnim.NEUTRAL, NOT_TOO_TOUGH)
                slayer.setCombatChecked(player, true)
                chatNpc(ChatAnim.NEUTRAL, CHECK_ON_NOW)
            }
        } else {
            chatNpc(ChatAnim.NEUTRAL, CHECK_ON)
            if (choice(KEEP_CHECKING, STOP_CHECKING) == 1) {
                chatPlayer(ChatAnim.NEUTRAL, KEEP_CHECKING)
                chatNpc(ChatAnim.NEUTRAL, KEEP_CHECKING_ANSWER)
            } else {
                chatPlayer(ChatAnim.NEUTRAL, STOP_CHECKING)
                slayer.setCombatChecked(player, false)
                chatNpc(ChatAnim.NEUTRAL, CHECK_OFF_NOW)
            }
        }
    }
}
