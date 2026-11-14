package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

/** Historian Minas's lines, from the Wiki's Transcript:Historian Minas, read 2026-11-02. */
object HistorianMinasLines : Lines("historian_minas", "Transcript:Historian Minas") {
    val HELLO = line("hello", "the player's greeting")
    val WELCOME = line("welcome", "his welcome, before the menu")
    val MUSEUM = line("museum", "option 1 (about the Museum), which the player then says")
    val MUSEUM_ANSWER = line("museum_answer", "option 1: his answer, before its menu")
    val NEW_DIG = line("new_dig", "option 1's option 1 (the new dig), which the player then says")
    val NEW_DIG_1 = line("new_dig_1", "the new dig: his answer, the first of three boxes")
    val NEW_DIG_2 = line("new_dig_2", "the new dig: the second box")
    val NEW_DIG_3 = line("new_dig_3", "the new dig: the third box")
    val NEW_DIG_THANKS = line("new_dig_thanks", "the new dig: the player's answer")
    val INFORMATION = line("information", "option 2 (some information), which the player then says")
    val INFORMATION_SAID = line("information_said", "option 2: what the player says (not the option's words)")
    val INFORMATION_1 = line("information_1", "option 2: his answer, one box")
    val NO_NEW_QUESTS = line("no_new_quests", "option 2 with no new quests to tell: the player's second thoughts")
    val NO_MATTER = line("no_matter", "no new quests: his answer")
    val GO_ON = line("go_on", "no new quests: the player's last line")
    val OTHER_SECTIONS = line("other_sections", "option 3 (other sections), which the player then says")
    val OTHER_SECTIONS_ANSWER = line("other_sections_answer", "option 3: his answer, before its menu")
    val NATURAL_HISTORY = line("natural_history", "the sections menu's option 1, which the player then says")
    val NATURAL_HISTORY_SAID = line("natural_history_said", "Natural history: what the player says (not the option's words)")
    val NATURAL_HISTORY_1 = line("natural_history_1", "Natural History: his answer, the first of two boxes")
    val NATURAL_HISTORY_2 = line("natural_history_2", "Natural History: the second box")
    val NATURAL_HISTORY_WHERE = line("natural_history_where", "Natural History: the player's question")
    val NATURAL_HISTORY_SEE = line("natural_history_see", "Natural History: his answer")
    val NATURAL_HISTORY_GUESS = line("natural_history_guess", "Natural History: the player's last line")
    val DIG_SITE = line("dig_site", "the sections menu's option 2, which the player then says")
    val DIG_SITE_SAID = line("dig_site_said", "Dig site: what the player says (not the option's words)")
    val DIG_SITE_ANSWER = line("dig_site_answer", "the Dig Site exhibit: his answer")
    val CLEANING = line("cleaning", "the sections menu's option 3, which the player then says")
    val CLEANING_SAID = line("cleaning_said", "Cleaning: what the player says (not the option's words)")
    val CLEANING_ANSWER = line("cleaning_answer", "the cleaning area: his answer")
    val QUALIFICATIONS = line("qualifications", "the cleaning area: the player asks what qualifications")
    val EARTH_SCIENCE = line("earth_science", "the cleaning area: his answer (Level 3 Earth Science)")
    val PASSED = line("passed", "the cleaning area, exams passed: the player's line")
    val PASSED_ANSWER = line("passed_answer", "the cleaning area, exams passed: his answer")
    val TIMELINE = line("timeline", "the sections menu's option 4, which the player then says")
    val TIMELINE_SAID = line("timeline_said", "Timeline: what the player says (not the option's words)")
    val TIMELINE_1 = line("timeline_1", "the Timeline: his answer, the first of three boxes")
    val TIMELINE_2 = line("timeline_2", "the Timeline: the second box")
    val TIMELINE_3 = line("timeline_3", "the Timeline: the third box")
    val TIMELINE_WOW = line("timeline_wow", "the Timeline: the player's answer")
}

/** Art Critic Jacques's lines, from the Wiki's Transcript:Art Critic Jacques (standard dialogue), read 2026-11-02. */
object ArtCriticLines : Lines("art_critic_jacques", "Transcript:Art Critic Jacques") {
    val HELLO = line("hello", "the player's greeting")
    val GREETINGS = line("greetings", "his greeting, before the menu")
    val WHO = line("who", "option 1, which the player then says")
    val WHO_LINES = (1..7).map { line("who_$it", "option 1: box $it of the seven after it, his and the player's in turn (his first)") }
    val WHAT = line("what", "option 2, which the player then says")
    val CRITIQUE = line("critique", "option 2: his answer, before its menu")
    val JUST_A_PAINTING = line("just_a_painting", "option 2's option 1, which the player then says")
    val JUST_A_PAINTING_SAID = line("just_a_painting_said", "option 2's option 1: what the player says (not the option's words)")
    val JUST = line("just", "option 2's option 1: his answer")
    val YES_I_SEE = line("yes_i_see", "option 2's option 2, which the player then says")
    val LOOK_AT = line("look_at", "option 2's option 2: the player's second line")
    val SO_IT_IS = line("so_it_is", "option 2's option 2: his answer, the first of two boxes")
    val NO_WORDS = line("no_words", "option 2's option 2: the second box")
}

/** The museum's teachers' lines, from the Wiki's Transcript:Teacher and pupil, read 2026-11-02. */
object MuseumTeacherLines : Lines("museum_teacher", "Transcript:Teacher and pupil") {
    val GROUND_TEACHER = line("ground_teacher", "the ground floor's pair: the teacher's line")
    val GROUND_PUPIL = line("ground_pupil", "the ground floor's pair: the pupil's answer")
    val UPSTAIRS_PUPIL = line("upstairs_pupil", "the 2nd floor's pair: the pupil's line")
    val UPSTAIRS_TEACHER = line("upstairs_teacher", "the 2nd floor's pair: the teacher's answer")
}

/** A schoolgirl's lines, one at random, from the Wiki's Transcript:Schoolgirl, read 2026-11-02. */
object SchoolgirlLines : Lines("museum_schoolgirl", "Transcript:Schoolgirl") {
    val RANDOM = (1..9).map { line("random_$it", "dialogue $it of the Wiki's nine") }
}

/** A schoolboy's lines, one at random, from the Wiki's Transcript:Schoolboy, read 2026-11-02. */
object SchoolboyLines : Lines("museum_schoolboy", "Transcript:Schoolboy") {
    val RANDOM = (1..9).map { line("random_$it", "dialogue $it of the Wiki's nine") }
}

/**
 * Torrcs's and Marfet's lines, from the Wiki's Transcript:Torrcs, read 2026-11-02 (Transcript:Marfet has the same five
 * talks). One of five at random; the fourth as after The Dig Site.
 */
object MuseumAdventurerLines : Lines("museum_adventurer", "Transcript:Torrcs") {
    /** Each talk's boxes: P for the player's, N for his. */
    val TALKS = listOf("PNPPNPNNPNPN", "NPNPNPNPN", "PNPNPNP", "NPNPNP", "PNPNPNPNPNPNPNPNP")
    val LINES = TALKS.mapIndexed { t, talk ->
        talk.indices.map { line("talk_${t + 1}_${it + 1}", "dialogue ${t + 1} (the fourth after The Dig Site), box ${it + 1}") }
    }
}

/** Orlando Smith's lines, from the Wiki's Transcript:Orlando Smith, read 2026-11-02 and (during and after the quiz) 2026-11-14. */
object OrlandoSmithLines : Lines("orlando_smith", "Transcript:Orlando Smith") {
    /** The talk before the menu: O for his boxes, P for the player's. */
    const val OPENING = "OPOPOOPOPO"
    val OPENING_LINES = OPENING.indices.map { line("opening_${it + 1}", "before the quiz: box ${it + 1} of ten, before the menu") }
    val SURE = line("sure", "option 1, which the player then says")
    val SURE_1 = line("sure_1", "option 1: his answer, the first of two boxes")
    val SURE_2 = line("sure_2", "option 1: the second box")
    val WILL_DO = line("will_do", "option 1: the player's last line")
    val NO_THANKS = line("no_thanks", "option 2")
    val NO_THANKS_SAID = line("no_thanks_said", "option 2: what the player says (not the option's words)")
    val FAIR_DINKUM = line("fair_dinkum", "option 2: his answer")
    val HOW_COMING = line("how_coming", "quiz agreed, not done or rewarded: his greeting")
    val NOT_YET = line("not_yet", "quiz agreed, no display done: the player's answer")
    val NO_WORRIES = line("no_worries", "quiz agreed, no display done: his answer (then the player's Will do)")
    val GETTING_THERE = line("getting_there", "some displays done: the player's answer")
    val DOING_GREAT = line("doing_great", "some displays done: his answer")
    val RIGHTY_HO = line("righty_ho", "some displays done: the player's next line")
    val GOOD_LUCK = line("good_luck", "some displays done: his last line")
    val ALL_DONE = line("all_done", "every display done: the player's answer")
    val BEAUT = line("beaut", "every display done: his answer")
    val GLAD = line("glad", "every display done: the player's next line")
    val CHAT_BOSS = line("chat_boss", "every display done: his next box")
    val EXPERIENCED = line("experienced", "every display done: his box before the XP")
    val THANK_YOU = line("thank_you", "every display done: the player's thanks after the XP")
    val TAKE_CARE = line("take_care", "every display done: his last box")
    val GOODBYE = line("goodbye", "every display done: the player's last line")
    val THANKS_HELP = line("thanks_help", "the quiz done and rewarded: his only line")
}

/** The natural historians' lines, from the Wiki's Transcript:Natural historian, read 2026-11-02. */
object NaturalHistorianLines : Lines("natural_historian", "Transcript:Natural historian") {
    /**
     * A lecture: the menu's [topic], what the player then [said] (the option's words but where live differs), his
     * [intro], then [lines] and its [end], which names the animal.
     */
    class Lecture(val topic: Line, val said: Line, val intro: Line, val lines: List<Line>, val end: Line)

    private fun lecture(name: String, count: Int, saysOther: Boolean = false): Lecture {
        val topic = line(name, if (saysOther) "the menu's option" else "the menu's option, which the player then says")
        return Lecture(
            topic,
            if (saysOther) line("${name}_said", "what the player says on picking it (not the option's words)") else topic,
            line("${name}_intro", "his first line of the lecture"),
            (1..count).map { line("${name}_$it", "the lecture, box $it of $count after the follow-me line") },
            line("${name}_end", "the lecture's last line: it concludes the lecture on this animal"),
        )
    }

    val FIRST_1 = line("first_1", "the first talk ever: his greeting")
    val FIRST_2 = line("first_2", "the first talk ever: the player's question")
    val FIRST_3 = line("first_3", "the first talk ever: his answer, the first of two boxes")
    val FIRST_4 = line("first_4", "the first talk ever: the second box")
    val FIRST_5 = line("first_5", "the first talk ever: the player's line")
    val FIRST_6 = line("first_6", "the first talk ever: his line, before the menu")
    val HELLO_AGAIN = line("hello_again", "later talks: his greeting")
    val HOPING = line("hoping", "later talks: the player's line, before the menu")
    val FOLLOW = line("follow", "each lecture's second line: follow me to the display")
    val ENOUGH = line("enough", "every menu's last option, which the player then says")
    val NONSENSE = line("nonsense", "enough: his answer, the first of two boxes")
    val SCIENCE = line("science", "enough: the second box")
    val NATURAL_HISTORY = line("natural_history", "the southern menu's option 1, which the player then says")
    val NATURAL_HISTORY_LINES = (1..4).map { line("natural_history_$it", "natural history: his answer, box $it of four") }

    val LIZARDS = lecture("lizards", 8)
    val TORTOISES = lecture("tortoises", 8, saysOther = true)
    val DRAGONS = lecture("dragons", 12)
    val WYVERNS = lecture("wyverns", 10, saysOther = true)
    val SNAILS = lecture("snails", 10)
    val MONKEYS = lecture("monkeys", 8)
    val SEA_SLUGS = lecture("sea_slugs", 10, saysOther = true)
    val SNAKES = lecture("snakes", 9)
    val TERRORBIRDS = lecture("terrorbirds", 11, saysOther = true)
    val KALPHITES = lecture("kalphites", 12)
    val CAMELS = lecture("camels", 9)
    val LEECHES = lecture("leeches", 11)
    val MOLES = lecture("moles", 7)
    val PENGUINS = lecture("penguins", 8)
}

/**
 * The Varrock Museum's other people (the Varrock piece's step 9b): Historian Minas and Torrcs on the first floor, Art
 * Critic Jacques on the second, Marfet in the cleaning area, the school visitors on every floor, and on the Natural
 * History floor its four natural historians (one a section), Orlando Smith and the animal displays (NPCs with no
 * options). Spawns are in npc-spawns/varrock.json.
 *
 * Everyone talks as once every quest is done, from the Wiki's transcripts. Guessed: every expression; the teachers on
 * the ground and Natural History floors saying the ground floor pair; a natural historian's first-talk lines once per
 * account (kept in charges as [MET]), then the menu without the later greeting. Left out: Historian Minas's quest
 * information blocks (with their lamps: Kudos isn't built, so he hears no new quests), the lectures' walk to the display
 * and the historian's pose (they're told where he stands); Marfet's talk about The Dig Site (the Wiki has only its
 * before-quest form). Orlando's quiz is [NaturalHistoryQuiz]; guessed there: the quiz taken on as the player says
 * Sure thing, the during-quiz talk once a display is done (the Wiki's page wonders whether one right answer does it).
 */
object VarrockMuseumPeople {
    const val HISTORIAN_MINAS = 1902
    const val ART_CRITIC = 1901
    const val TORRCS = 6019
    const val MARFET = 6020
    const val TEACHER_AND_BOY = 1913
    const val TEACHER_AND_GIRL = 1922
    val SCHOOLGIRLS = listOf(1914, 1921)
    val SCHOOLBOYS = listOf(1919, 1920)
    const val ORLANDO_SMITH = 6001
    const val HISTORIAN_NORTH = 6002
    const val HISTORIAN_SOUTH = 6003
    const val HISTORIAN_EAST = 6004
    const val HISTORIAN_WEST = 6005
    const val TALK_TO = 1

    /** Which of [MuseumAdventurerLines.TALKS] each says: Marfet's page has no after-Dig Site talk, so not the fourth. */
    val TORRCS_TALKS = listOf(0, 1, 2, 3, 4)
    val MARFET_TALKS = listOf(0, 1, 2, 4)

    /** Set once a player has heard a natural historian's first talk. */
    const val MET = "natural_historian_met"

    /** Each section's historian's lectures, in his menu's order. */
    val LECTURES: Map<Int, List<NaturalHistorianLines.Lecture>> = with(NaturalHistorianLines) {
        mapOf(
            HISTORIAN_NORTH to listOf(LIZARDS, TORTOISES, DRAGONS, WYVERNS),
            HISTORIAN_EAST to listOf(SNAILS, MONKEYS, SEA_SLUGS, SNAKES),
            HISTORIAN_SOUTH to listOf(TERRORBIRDS, KALPHITES),
            HISTORIAN_WEST to listOf(CAMELS, LEECHES, MOLES, PENGUINS),
        )
    }

    internal suspend fun Dialogue.minas() = with(HistorianMinasLines) {
        chatPlayer(ChatAnim.HAPPY, HELLO)
        chatNpc(ChatAnim.HAPPY, WELCOME)
        var pick = choice(MUSEUM, INFORMATION, OTHER_SECTIONS)
        if (pick == 1) {
            chatPlayer(ChatAnim.QUIZ, MUSEUM)
            chatNpc(ChatAnim.HAPPY, MUSEUM_ANSWER)
            while (true) {
                pick = choice(NEW_DIG, INFORMATION, OTHER_SECTIONS)
                if (pick != 1) break
                chatPlayer(ChatAnim.QUIZ, NEW_DIG)
                chatNpc(ChatAnim.HAPPY, NEW_DIG_1)
                chatNpc(ChatAnim.HAPPY, NEW_DIG_2)
                chatNpc(ChatAnim.HAPPY, NEW_DIG_3)
                chatPlayer(ChatAnim.HAPPY, NEW_DIG_THANKS)
            }
        }
        if (pick == 2) {
            chatPlayer(ChatAnim.HAPPY, INFORMATION_SAID)
            chatNpc(ChatAnim.HAPPY, INFORMATION_1)
            chatPlayer(ChatAnim.NEUTRAL, NO_NEW_QUESTS)
            chatNpc(ChatAnim.NEUTRAL, NO_MATTER)
            chatPlayer(ChatAnim.NEUTRAL, GO_ON)
            return@with
        }
        chatPlayer(ChatAnim.QUIZ, OTHER_SECTIONS)
        chatNpc(ChatAnim.HAPPY, OTHER_SECTIONS_ANSWER)
        // The sections menu has no way out but walking away, as on the Wiki.
        while (true) {
            when (choice(NATURAL_HISTORY, DIG_SITE, CLEANING, TIMELINE)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, NATURAL_HISTORY_SAID)
                    chatNpc(ChatAnim.HAPPY, NATURAL_HISTORY_1)
                    chatNpc(ChatAnim.HAPPY, NATURAL_HISTORY_2)
                    chatPlayer(ChatAnim.QUIZ, NATURAL_HISTORY_WHERE)
                    chatNpc(ChatAnim.NEUTRAL, NATURAL_HISTORY_SEE)
                    chatPlayer(ChatAnim.NEUTRAL, NATURAL_HISTORY_GUESS)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, DIG_SITE_SAID)
                    chatNpc(ChatAnim.NEUTRAL, DIG_SITE_ANSWER)
                }
                3 -> {
                    chatPlayer(ChatAnim.QUIZ, CLEANING_SAID)
                    chatNpc(ChatAnim.NEUTRAL, CLEANING_ANSWER)
                    chatPlayer(ChatAnim.QUIZ, QUALIFICATIONS)
                    chatNpc(ChatAnim.NEUTRAL, EARTH_SCIENCE)
                    chatPlayer(ChatAnim.HAPPY, PASSED)
                    chatNpc(ChatAnim.HAPPY, PASSED_ANSWER)
                }
                else -> {
                    chatPlayer(ChatAnim.QUIZ, TIMELINE_SAID)
                    chatNpc(ChatAnim.HAPPY, TIMELINE_1)
                    chatNpc(ChatAnim.HAPPY, TIMELINE_2)
                    chatNpc(ChatAnim.HAPPY, TIMELINE_3)
                    chatPlayer(ChatAnim.HAPPY, TIMELINE_WOW)
                }
            }
        }
    }

    internal suspend fun Dialogue.artCritic() = with(ArtCriticLines) {
        chatPlayer(ChatAnim.HAPPY, HELLO)
        chatNpc(ChatAnim.HAPPY, GREETINGS)
        if (choice(WHO, WHAT) == 1) {
            chatPlayer(ChatAnim.QUIZ, WHO)
            for ((i, line) in WHO_LINES.withIndex()) {
                if (i % 2 == 0) chatNpc(ChatAnim.HAPPY, line) else chatPlayer(ChatAnim.QUIZ, line)
            }
            return@with
        }
        chatPlayer(ChatAnim.QUIZ, WHAT)
        chatNpc(ChatAnim.HAPPY, CRITIQUE)
        if (choice(JUST_A_PAINTING, YES_I_SEE) == 1) {
            chatPlayer(ChatAnim.CONFUSED, JUST_A_PAINTING_SAID)
            chatNpc(ChatAnim.ANGRY, JUST)
        } else {
            chatPlayer(ChatAnim.HAPPY, YES_I_SEE)
            chatPlayer(ChatAnim.HAPPY, LOOK_AT)
            chatNpc(ChatAnim.HAPPY, SO_IT_IS)
            chatNpc(ChatAnim.HAPPY, NO_WORDS)
        }
    }

    /** The teacher's pair: the 2nd floor's when [upstairs], else the ground floor's. */
    internal suspend fun Dialogue.teacher(upstairs: Boolean) = with(MuseumTeacherLines) {
        if (upstairs) {
            chatNpc(ChatAnim.QUIZ, UPSTAIRS_PUPIL)
            chatNpc(ChatAnim.NEUTRAL, UPSTAIRS_TEACHER)
        } else {
            chatNpc(ChatAnim.ANGRY, GROUND_TEACHER)
            chatNpc(ChatAnim.SAD, GROUND_PUPIL)
        }
    }

    /** Torrcs's or Marfet's talk number [talk] (0 to 4). */
    internal suspend fun Dialogue.adventurer(talk: Int) = with(MuseumAdventurerLines) {
        for ((speaker, line) in TALKS[talk].toList().zip(LINES[talk])) {
            if (speaker == 'P') chatPlayer(ChatAnim.NEUTRAL, line) else chatNpc(ChatAnim.HAPPY, line)
        }
    }

    /**
     * Orlando Smith: his quiz offer until the player takes it on ([NaturalHistoryQuiz.VARBIT_AGREED]), then how the
     * displays are coming along, and once all fourteen are done his 1,000 Slayer and Hunter XP, once.
     */
    internal suspend fun Dialogue.orlando(world: World) = with(OrlandoSmithLines) {
        when {
            NaturalHistoryQuiz.rewardGiven(player) -> return@with chatNpc(ChatAnim.HAPPY, THANKS_HELP)
            !NaturalHistoryQuiz.agreed(player) -> {}
            NaturalHistoryQuiz.allDone(player) -> {
                chatNpc(ChatAnim.HAPPY, HOW_COMING)
                chatPlayer(ChatAnim.HAPPY, ALL_DONE)
                chatNpc(ChatAnim.HAPPY, BEAUT)
                chatPlayer(ChatAnim.HAPPY, GLAD)
                chatNpc(ChatAnim.HAPPY, CHAT_BOSS)
                chatNpc(ChatAnim.HAPPY, EXPERIENCED)
                NaturalHistoryQuiz.reward(world, player)
                chatPlayer(ChatAnim.HAPPY, THANK_YOU)
                chatNpc(ChatAnim.HAPPY, TAKE_CARE)
                chatPlayer(ChatAnim.NEUTRAL, GOODBYE)
                return@with
            }
            NaturalHistoryQuiz.doneCount(player) == 0 -> {
                chatNpc(ChatAnim.HAPPY, HOW_COMING)
                chatPlayer(ChatAnim.NEUTRAL, NOT_YET)
                chatNpc(ChatAnim.HAPPY, NO_WORRIES)
                chatPlayer(ChatAnim.HAPPY, WILL_DO)
                return@with
            }
            else -> {
                chatNpc(ChatAnim.HAPPY, HOW_COMING)
                chatPlayer(ChatAnim.NEUTRAL, GETTING_THERE)
                chatNpc(ChatAnim.HAPPY, DOING_GREAT)
                chatPlayer(ChatAnim.HAPPY, RIGHTY_HO)
                chatNpc(ChatAnim.HAPPY, GOOD_LUCK)
                return@with
            }
        }
        for ((speaker, line) in OPENING.toList().zip(OPENING_LINES)) {
            if (speaker == 'P') chatPlayer(ChatAnim.QUIZ, line) else chatNpc(ChatAnim.HAPPY, line)
        }
        if (choice(SURE, NO_THANKS) == 1) {
            chatPlayer(ChatAnim.HAPPY, SURE)
            player.vars.setVarbit(NaturalHistoryQuiz.VARBIT_AGREED, 1)
            chatNpc(ChatAnim.HAPPY, SURE_1)
            chatNpc(ChatAnim.HAPPY, SURE_2)
            chatPlayer(ChatAnim.HAPPY, WILL_DO)
        } else {
            chatPlayer(ChatAnim.NEUTRAL, NO_THANKS_SAID)
            chatNpc(ChatAnim.NEUTRAL, FAIR_DINKUM)
        }
    }

    /**
     * A natural historian of [section] (his NPC type). [firstTime] plays the first talk ever and [heard] records it.
     */
    internal suspend fun Dialogue.naturalHistorian(section: Int, firstTime: Boolean, heard: () -> Unit) = with(NaturalHistorianLines) {
        if (firstTime) {
            chatNpc(ChatAnim.HAPPY, FIRST_1)
            chatPlayer(ChatAnim.QUIZ, FIRST_2)
            chatNpc(ChatAnim.HAPPY, FIRST_3)
            chatNpc(ChatAnim.HAPPY, FIRST_4)
            chatPlayer(ChatAnim.HAPPY, FIRST_5)
            chatNpc(ChatAnim.HAPPY, FIRST_6)
            heard()
        } else {
            chatNpc(ChatAnim.HAPPY, HELLO_AGAIN)
            chatPlayer(ChatAnim.HAPPY, HOPING)
        }
        val lectures = LECTURES.getValue(section)
        val south = section == HISTORIAN_SOUTH
        val options = (if (south) listOf(NATURAL_HISTORY) else emptyList()) + lectures.map { it.topic } + ENOUGH
        val pick = choice(*options.toTypedArray())
        val chosen = options[pick - 1]
        val lecture = LECTURES.getValue(section).firstOrNull { it.topic == chosen }
        chatPlayer(ChatAnim.QUIZ, lecture?.said ?: chosen)
        when {
            chosen == ENOUGH -> {
                chatNpc(ChatAnim.HAPPY, NONSENSE)
                chatNpc(ChatAnim.HAPPY, SCIENCE)
            }
            chosen == NATURAL_HISTORY -> for (line in NATURAL_HISTORY_LINES) chatNpc(ChatAnim.HAPPY, line)
            else -> {
                lecture!!
                chatNpc(ChatAnim.HAPPY, lecture.intro)
                chatNpc(ChatAnim.HAPPY, FOLLOW)
                for (line in lecture.lines) chatNpc(ChatAnim.NEUTRAL, line)
                chatNpc(ChatAnim.HAPPY, lecture.end)
            }
        }
    }

    /** One of [lines] at random. */
    private fun pick(world: World, lines: List<Line>): Line = lines[world.random.between(0, lines.size - 1)]

    /** Whether [player] has heard a natural historian's first talk. */
    fun metHistorian(player: Player): Boolean = player.charges[MET] > 0

    fun register(world: World) {
        world.handlers.onNpc(HISTORIAN_MINAS, TALK_TO) { op -> world.dialogues.start(this, op.npc) { minas() } }
        world.handlers.onNpc(ART_CRITIC, TALK_TO) { op -> world.dialogues.start(this, op.npc) { artCritic() } }
        for (teacher in listOf(TEACHER_AND_BOY, TEACHER_AND_GIRL)) {
            world.handlers.onNpc(teacher, TALK_TO) { op -> world.dialogues.start(this, op.npc) { teacher(upstairs = op.npc!!.level == 2) } }
        }
        for (girl in SCHOOLGIRLS) {
            world.handlers.onNpc(girl, TALK_TO) { op ->
                val line = pick(world, SchoolgirlLines.RANDOM)
                world.dialogues.start(this, op.npc) { chatNpc(ChatAnim.HAPPY, line) }
            }
        }
        for (boy in SCHOOLBOYS) {
            world.handlers.onNpc(boy, TALK_TO) { op ->
                val line = pick(world, SchoolboyLines.RANDOM)
                world.dialogues.start(this, op.npc) { chatNpc(ChatAnim.HAPPY, line) }
            }
        }
        for ((adventurer, talks) in listOf(TORRCS to TORRCS_TALKS, MARFET to MARFET_TALKS)) {
            world.handlers.onNpc(adventurer, TALK_TO) { op ->
                val talk = talks[world.random.between(0, talks.size - 1)]
                world.dialogues.start(this, op.npc) { adventurer(talk) }
            }
        }
        world.handlers.onNpc(ORLANDO_SMITH, TALK_TO) { op -> world.dialogues.start(this, op.npc) { orlando(world) } }
        for (section in LECTURES.keys) {
            world.handlers.onNpc(section, TALK_TO) { op ->
                val firstTime = !metHistorian(player)
                world.dialogues.start(this, op.npc) { naturalHistorian(section, firstTime) { player.charges[MET] = 1 } }
            }
        }
    }
}
