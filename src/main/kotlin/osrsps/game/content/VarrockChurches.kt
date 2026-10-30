package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

/** Father Lawrence's lines, from the Wiki's Transcript:Father Lawrence (after Romeo & Juliet), read 2026-10-30. */
object FatherLawrenceLines : Lines("father_lawrence", "Transcript:Father Lawrence") {
    val WHISKEY = line("whiskey", "his first line: a father in the times of whiskey")
    val GUTTERS = line("gutters", "his second line: he sings, drinks and wakes in gutters")
    val MORNING = line("morning", "then, half the time: top of the morning")
    val FORGIVE = line("forgive", "then, the other half: to forgive is difficult")
    val DRINK = line("drink", "after forgive: he needs a drink to think")
}

/** Elsie's lines, from the Wiki's Transcript:Elsie, read 2026-10-30. */
object ElsieLines : Lines("elsie", "Transcript:Elsie") {
    val GREETING = line("greeting", "her first line: can she help?")
    val MAKING = line("making", "option 1, which the player then says")
    val STOLE = line("stole", "option 1: a stole for Father Lawrence")
    val STORY = line("story", "option 2, which the player then says")
    val FOR_TEA = line("for_tea", "option 2: a story for a cup of tea")
    val THINK = line("think", "option 2's only option, which ends the talk")
    val RICH = line("rich", "option 3, which the player then says")
    val TRADE = line("trade", "option 3: get a trade, don't fight in the Wilderness")
    val ORE_FOOD = line("ore_food", "option 3: folk nearby buy ore and food")
    val THANKS = line("thanks", "option 3: the player's thanks")
    val TEA = line("tea", "a cup of tea used on her: is it for her?")
    val TEA_STORY = line("tea_story", "a cup of tea used on her: a story for it")
    val HAVE_IT = line("have_it", "the tea menu's option 1, which the player then says")
    val KEEP_IT = line("keep_it", "the tea menu's option 2, which the player then says")
    val ANOTHER_TIME = line("another_time", "kept the tea: maybe another time, then her menu")
    val TALE = (1..9).map { line("tale_$it", "the story, line $it of the Wiki's nine (from thanks for the tea to marrying Freddie)") }
    val LEAVE = line("leave", "after the story: the player leaves her to her knitting")
    val NOT_THAT = line("not_that", "any other item used on her: she doesn't want it")
}

/** The Mage of Zamorak's lines, from the Wiki's Transcript:Mage of Zamorak (After completing Enter the Abyss), read 2026-10-30. */
object MageOfZamorakLines : Lines("mage_of_zamorak", "Transcript:Mage of Zamorak") {
    val GREETING = line("greeting", "his first line: what does the player want?")
    val INCANTATION = line("incantation", "option 1, which the player then says")
    val RIFT = line("rift", "option 1, asked before: the incantation let the Order open a rift")
    val FLESH = line("flesh", "option 1, asked before: the Order's human flesh story")
    val RIGHT = line("right", "option 1, asked before: the player's answer")
    val ABYSS = line("abyss", "option 2, which the player then says")
    val ABYSS_LINES = (1..13).map { line("abyss_$it", "option 2: line $it of the Wiki's thirteen after the player asks, the player's and his in turn") }
    val GROUP = line("group", "option 3, which the player then says")
    val GROUP_LINES = (1..12).map { line("group_$it", "option 3: line $it of the Wiki's twelve after the player asks, the player's and his in turn") }
    val LOOKING = line("looking", "option 4, which the player then says")
    val VERY_WELL = line("very_well", "option 4: his answer")
}

/**
 * Varrock's church and the Zamorakian chapel (the Varrock piece's step 7). The church's altar
 * (`fai_varrock_church_altar` 14860) and the chapel's chaos altar (`thrantaxaltar` 61) recharge prayer as Lumbridge's
 * does ([prayAt]); Father Lawrence (5038) and Elsie (2900, upstairs) in the church and the Mage of Zamorak in the chapel
 * (multi-NPC 3229, showing `rcu_zammy_mage1_edgeb` 2582 once Rune Mysteries is done) talk as once every quest is done.
 *
 * From the Wiki's transcripts. Guessed: every expression; which line in the Mage's and Elsie's long passages a player
 * says (they alternate as the Wiki lists them; [ABYSS_PLAYER] and [GROUP_PLAYER] name which); that the Mage's
 * incantation answer is always the asked-before one (no per-player memory of the first ask). Left out: the chaos
 * altar's Check, the sleeping church-goers, and the Mage's treasure trail talk.
 */
object VarrockChurches {
    const val FATHER_LAWRENCE = 5038
    const val ELSIE = 2900
    const val MAGE_OF_ZAMORAK = 2582
    const val TALK_TO = 1

    const val CHURCH_ALTAR = 14860
    const val CHAOS_ALTAR = 61
    const val PRAY_AT = 1

    const val CUP_OF_TEA = 1978

    /** Which of [MageOfZamorakLines.ABYSS_LINES] the player says (the rest are his), from the transcript. */
    val ABYSS_PLAYER = setOf(2, 7, 9, 11)

    /** Which of [MageOfZamorakLines.GROUP_LINES] the player says. */
    val GROUP_PLAYER = setOf(3, 5, 10)

    internal suspend fun Dialogue.fatherLawrence(firstHalf: Boolean) = with(FatherLawrenceLines) {
        chatNpc(ChatAnim.DRUNK, WHISKEY)
        chatNpc(ChatAnim.DRUNK, GUTTERS)
        if (firstHalf) {
            chatNpc(ChatAnim.HAPPY, MORNING)
        } else {
            chatNpc(ChatAnim.DRUNK, FORGIVE)
            chatNpc(ChatAnim.DRUNK, DRINK)
        }
    }

    internal suspend fun Dialogue.elsie() = with(ElsieLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        menu()
    }

    private suspend fun Dialogue.menu() = with(ElsieLines) {
        while (true) {
            when (choice(MAKING, STORY, RICH)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, MAKING)
                    chatNpc(ChatAnim.HAPPY, STOLE)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, STORY)
                    chatNpc(ChatAnim.HAPPY, FOR_TEA)
                    choice(THINK)
                    return@with
                }
                else -> {
                    chatPlayer(ChatAnim.QUIZ, RICH)
                    chatNpc(ChatAnim.NEUTRAL, TRADE)
                    chatNpc(ChatAnim.NEUTRAL, ORE_FOOD)
                    chatPlayer(ChatAnim.HAPPY, THANKS)
                }
            }
        }
    }

    /** A cup of tea used on Elsie; [takeTea] takes it, false when it's gone. */
    internal suspend fun Dialogue.elsieTea(takeTea: () -> Boolean) = with(ElsieLines) {
        chatNpc(ChatAnim.HAPPY, TEA)
        chatNpc(ChatAnim.HAPPY, TEA_STORY)
        if (choice(HAVE_IT, KEEP_IT) == 2) {
            chatPlayer(ChatAnim.ANGRY, KEEP_IT)
            chatNpc(ChatAnim.NEUTRAL, ANOTHER_TIME)
            menu()
            return@with
        }
        chatPlayer(ChatAnim.HAPPY, HAVE_IT)
        if (!takeTea()) return@with
        for (line in TALE) chatNpc(ChatAnim.HAPPY, line)
        chatPlayer(ChatAnim.HAPPY, LEAVE)
    }

    internal suspend fun Dialogue.mageOfZamorak() = with(MageOfZamorakLines) {
        chatNpc(ChatAnim.NEUTRAL, GREETING)
        while (true) {
            when (choice(INCANTATION, ABYSS, GROUP, LOOKING)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, INCANTATION)
                    chatNpc(ChatAnim.NEUTRAL, RIFT)
                    chatNpc(ChatAnim.ANGRY, FLESH)
                    chatPlayer(ChatAnim.NEUTRAL, RIGHT)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, ABYSS)
                    passage(ABYSS_LINES, ABYSS_PLAYER)
                }
                3 -> {
                    chatPlayer(ChatAnim.QUIZ, GROUP)
                    passage(GROUP_LINES, GROUP_PLAYER)
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, LOOKING)
                    chatNpc(ChatAnim.NEUTRAL, VERY_WELL)
                    return@with
                }
            }
        }
    }

    private suspend fun Dialogue.passage(lines: List<Line>, player: Set<Int>) {
        for ((i, line) in lines.withIndex()) {
            if (i + 1 in player) chatPlayer(ChatAnim.QUIZ, line) else chatNpc(ChatAnim.NEUTRAL, line)
        }
    }
}

/** The church's and the chapel's altars and people. */
fun registerVarrockChurches(world: World) {
    with(VarrockChurches) {
        world.handlers.onLoc(CHURCH_ALTAR, PRAY_AT) { prayAt(world) }
        world.handlers.onLoc(CHAOS_ALTAR, PRAY_AT) { prayAt(world) }
        world.handlers.onNpc(FATHER_LAWRENCE, TALK_TO) { op ->
            val firstHalf = world.random.between(0, 1) == 0
            world.dialogues.start(this, op.npc) { fatherLawrence(firstHalf) }
        }
        world.handlers.onNpc(ELSIE, TALK_TO) { op -> world.dialogues.start(this, op.npc) { elsie() } }
        world.handlers.onObjOnNpc(ELSIE, CUP_OF_TEA) { op ->
            world.dialogues.start(this, op.npc) {
                elsieTea {
                    val slot = player.inventory.contents().firstOrNull { it.second.id == CUP_OF_TEA }?.first
                    slot != null && player.inventory.remove(slot, 1) == 1
                }
            }
        }
        world.handlers.onObjOnNpc(ELSIE) { op -> world.dialogues.start(this, op.npc) { chatNpc(ChatAnim.NEUTRAL, ElsieLines.NOT_THAT) } }
        world.handlers.onNpc(MAGE_OF_ZAMORAK, TALK_TO) { op -> world.dialogues.start(this, op.npc) { mageOfZamorak() } }
    }
}
