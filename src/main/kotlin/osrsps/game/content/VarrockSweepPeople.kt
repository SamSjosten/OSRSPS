package osrsps.game.content

import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** Guidor's lines after Biohazard, from the Wiki's Transcript:Guidor, read 2026-11-11. */
object GuidorLines : Lines("guidor", "Transcript:Guidor") {
    val HOW = line("how", "the player greets him and asks how he is")
    val COPING = line("coping", "his answer: he is coping")
    val GOOD = line("good", "the player's last line")
}

/** Chancy's lines in Varrock after Biohazard, from the Wiki's Transcript:Chancy, read 2026-11-11. */
object ChancyLines : Lines("chancy", "Transcript:Chancy") {
    val MORNING = line("morning", "the player's greeting")
    val NOT_NOW = line("not_now", "his answer, refusing to talk (members' world version)")
}

/** Pox's lines after Ratcatchers, without a catspeak amulet, from the Wiki's Transcript:Pox, read 2026-11-11. */
object PoxLines : Lines("pox", "Transcript:Pox") {
    val HELLO = line("hello", "the player greets the cat")
    val MEOW = line("meow", "her answer, a meow")
}

/** Hooknosed Jack's lines after Ratcatchers, from the Wiki's Transcript:Hooknosed Jack, read 2026-11-11. */
object HooknosedJackLines : Lines("hooknosed_jack", "Transcript:Hooknosed Jack") {
    val WHERE = line("where", "menu option 1: where to find someone")
    val PITS = line("pits", "menu option 2: about the pits, which the player then says")
    val PITS_NOT_YET = line("pits_not_yet", "about the pits (the Rat Pits aren't built): his answer, guessed")
    val SHOW = line("show", "menu option 3: show me to the rat pits, which the player then says")
    val MANHOLE = line("manhole", "show me: he points out the manhole")
    val GRIMESQUIT = line("grimesquit", "where: option, Grimesquit and Phingspet, which the player then says")
    val GRIMESQUIT_1 = line("grimesquit_1", "where, Grimesquit and Phingspet: his answer (the sewers)")
    val JIMMY = line("jimmy", "where: option, Jimmy Dazzler, which the player then says")
    val JIMMY_1 = line("jimmy_1", "where, Jimmy Dazzler: his first answer")
    val JIMMY_2 = line("jimmy_2", "where, Jimmy Dazzler: the player's second line")
    val JIMMY_3 = line("jimmy_3", "where, Jimmy Dazzler: his second answer")
    val JIMMY_4 = line("jimmy_4", "where, Jimmy Dazzler: the player's third line")
    val JIMMY_5 = line("jimmy_5", "where, Jimmy Dazzler: his third answer")
    val JIMMY_6 = line("jimmy_6", "where, Jimmy Dazzler: the player's last line")
    val JIMMY_7 = line("jimmy_7", "where, Jimmy Dazzler: his doubt")
    val JIMMY_8 = line("jimmy_8", "where, Jimmy Dazzler: where to find him")
    val JOE = line("joe", "where: option, Smokin' Joe, which the player then says")
    val JOE_1 = line("joe_1", "where, Smokin' Joe: his first answer (Keldagrim)")
    val JOE_2 = line("joe_2", "where, Smokin' Joe: his second box (say hello)")
    val FACE = line("face", "where: option, The Face, which the player then says")
    val FACE_1 = line("face_1", "where, The Face: his answer (Port Sarim)")
    val FELKRASH = line("felkrash", "where: option, Felkrash, which the player then says")
    val FELKRASH_1 = line("felkrash_1", "where, Felkrash: his answer (the Port Sarim rat pits)")
}

/** Anna Jones's lines after What Lies Below, the statue still standing, from the Wiki's Transcript:Anna Jones, read 2026-11-11. */
object AnnaJonesLines : Lines("anna_jones", "Transcript:Anna Jones") {
    val GREETING = line("greeting", "her greeting, by name, giving a bronze pickaxe", "name")
    val THANKS = line("thanks", "the player thanks her")
    val SUROK = line("surok", "her employer says the player may use the tunnel")
    val OK = line("ok", "the player's acknowledgement")
    val AWAITS = line("awaits", "the tunnel awaits, before the menu")
    val TUNNEL = line("tunnel", "menu option 1: what tunnel, which the player then says")
    val TUNNEL_1 = line("tunnel_1", "what tunnel: her first answer")
    val TUNNEL_2 = line("tunnel_2", "what tunnel: the player's second line")
    val TUNNEL_3 = line("tunnel_3", "what tunnel: her second answer")
    val TUNNEL_4 = line("tunnel_4", "what tunnel: the player's third line")
    val TUNNEL_5 = line("tunnel_5", "what tunnel: her third answer")
    val TUNNEL_6 = line("tunnel_6", "what tunnel: the player's last line")
    val TUNNEL_7 = line("tunnel_7", "what tunnel: her last answer, asking the player to try")
    val WHO = line("who", "menu option 2: who are you, which the player then says")
    val WHO_1 = line("who_1", "who are you: her answer")
    val WHO_2 = line("who_2", "who are you: the player's second line")
    val WHO_3 = line("who_3", "who are you: her last answer")
    val STATUE = line("statue", "menu option 3: who the statue is, which the player then says")
    val STATUE_1 = line("statue_1", "the statue: her answer (Saradomin)")
    val STATUE_2 = line("statue_2", "the statue: the player's admiring remark")
    val STATUE_3 = line("statue_3", "the statue: her last answer")
    val DOING = line("doing", "menu option 4: what she is doing here, which the player then says")
    val DOING_1 = line("doing_1", "what she's doing: her first answer")
    val DOING_2 = line("doing_2", "what she's doing: the player's second line")
    val DOING_3 = line("doing_3", "what she's doing: her second answer")
    val DOING_4 = line("doing_4", "what she's doing: the player's third line")
    val DOING_5 = line("doing_5", "what she's doing: her last answer")
    val DOING_6 = line("doing_6", "what she's doing: the player's last line (they might)")
    val GO = line("go", "menu option 5: the player had better go, which they then say")
    val FAREWELL = line("farewell", "her farewell")
}

/** The Mahogany Homes clients' lines when the player has no contract with them, from their Wiki transcripts, read 2026-11-11. */
object MahoganyClientLines : Lines("mahogany_client", "Transcript:Jeff (Mahogany Homes)") {
    val JEFF = line("jeff", "Jeff's only line: he's busy")
    val BOB = line("bob", "Bob's only line: he's busy (Transcript:Bob (Mahogany Homes))")
    val SARAH = line("sarah", "Sarah's only line: she's busy (Transcript:Sarah (Mahogany Homes))")
}

/** The Master smithing tutor's lines, from the Wiki's Transcript:Master smithing tutor, read 2026-11-11. */
object MasterSmithingTutorLines : Lines("master_smithing_tutor", "Transcript:Master smithing tutor") {
    val HELLO = line("hello", "the player's greeting")
    val GREETING = line("greeting", "his greeting and offer to talk smithing")
    val TRAIN = line("train", "menu option 1: how to train smithing, which the player then says")
    val NO_HAMMER = line("no_hammer", "how to train: the player has no hammer, so he gives one")
    val HAS_HAMMER = line("has_hammer", "how to train: the player has a hammer")
    val LOW_1 = line("low_1", "levels 1 to 20: bars")
    val LOW_2 = line("low_2", "levels 1 to 20: the Lumbridge furnace")
    val LOW_3 = line("low_3", "levels 1 to 20: the Varrock mine")
    val LOW_4 = line("low_4", "levels 1 to 20: the anvil interface")
    val LOW_5 = line("low_5", "levels 1 to 20: the colours, first box")
    val LOW_6 = line("low_6", "levels 1 to 20: the colours, second box")
    val LOW_THANKS = line("low_thanks", "levels 1 to 20: the player thanks him")
    val MID_1 = line("mid_1", "levels 21 to 37: banking bars")
    val MID_2 = line("mid_2", "levels 21 to 37: boosts")
    val MID_3 = line("mid_3", "levels 21 to 37: where to buy a boost")
    val HIGH_99 = line("high_99", "level 99: his extra first box")
    val HIGH_1 = line("high_1", "levels 38 to 99: higher metals")
    val HIGH_2 = line("high_2", "levels 38 to 99: coal")
    val HIGH_3 = line("high_3", "levels 38 to 99: rings of forging")
    val HIGH_4 = line("high_4", "levels 38 to 99: the Blast Furnace")
    val HIGH_5 = line("high_5", "levels 38 to 99: Otto Godblessed")
    val HIGH_OTTO = line("high_otto", "levels 38 to 99, Barbarian Smithing done: where Otto is")
    val ANYTHING_ELSE = line("anything_else", "after an answer: anything else?")
    val WHAT = line("what", "menu option 2: what can be smithed, which the player then says")
    val WHAT_1 = line("what_1", "what can be smithed: item types")
    val WHAT_2 = line("what_2", "what can be smithed: weapons' cost")
    val WHAT_3 = line("what_3", "what can be smithed: armour's cost")
    val WHAT_4 = line("what_4", "what can be smithed: items that need knowledge")
    val WHAT_5 = line("what_5", "what can be smithed: items made without bars")
    val WHAT_6 = line("what_6", "what can be smithed: his closing question")
    val NOT_NOW = line("not_now", "menu option 3: not right now, which the player then says")
    val COME_BACK = line("come_back", "not right now: he invites the player back")
}

/**
 * Varrock's people the Misthalin sweep found unspawned, as far as their talk goes without a system that isn't built;
 * spawned in varrock.json on mejrs's tiles. Every quest is taken as done (the post-quest reading):
 *
 * - Guidor (1110) and Chancy (1106), after Biohazard; Pox (4574) and Hooknosed Jack (4588), after Ratcatchers (Pox
 *   without a catspeak amulet); Anna Jones (4162), after What Lies Below with the statue standing.
 * - Jeff, Bob and Sarah (10415, 10414, 10416), Mahogany Homes clients: their one line with no contract.
 * - The Master smithing tutor (3225), with his level bands (1-20, 21-37, 38-99, 99), a hammer when the player has none.
 * - Jeremy Clerksin (5042), asleep in a church pew, and BigRedJapan (5921) on the rooftops: no options.
 *
 * Left out: Hooknosed Jack's pit topics (the Rat Pits aren't built: a guessed line ends it) and his Rat Pits manual;
 * Anna Jones's pickaxe (she says she gives one; guessed: only when the player carries none). Guessed: every chathead
 * expression.
 */
object VarrockSweepPeople {
    const val GUIDOR = 1110
    const val CHANCY = 1106
    const val POX = 4574
    const val HOOKNOSED_JACK = 4588
    const val ANNA_JONES = 4162
    const val JEFF = 10415
    const val BOB = 10414
    const val SARAH = 10416
    const val MASTER_SMITHING_TUTOR = 3225
    const val TALK_TO = 1

    const val HAMMER = 2347
    const val BRONZE_PICKAXE = 1265

    internal suspend fun Dialogue.guidor() = with(GuidorLines) {
        chatPlayer(ChatAnim.QUIZ, HOW)
        chatNpc(ChatAnim.HAPPY, COPING)
        chatPlayer(ChatAnim.HAPPY, GOOD)
    }

    internal suspend fun Dialogue.chancy() = with(ChancyLines) {
        chatPlayer(ChatAnim.HAPPY, MORNING)
        chatNpc(ChatAnim.ANGRY, NOT_NOW)
    }

    internal suspend fun Dialogue.pox() = with(PoxLines) {
        chatPlayer(ChatAnim.HAPPY, HELLO)
        chatNpc(ChatAnim.NEUTRAL, MEOW)
    }

    internal suspend fun Dialogue.hooknosedJack() = with(HooknosedJackLines) {
        when (choice(WHERE, PITS, SHOW)) {
            1 -> when (choice(GRIMESQUIT, JIMMY, JOE, FACE, FELKRASH)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, GRIMESQUIT)
                    chatNpc(ChatAnim.NEUTRAL, GRIMESQUIT_1)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, JIMMY)
                    chatNpc(ChatAnim.NEUTRAL, JIMMY_1)
                    chatPlayer(ChatAnim.QUIZ, JIMMY_2)
                    chatNpc(ChatAnim.NEUTRAL, JIMMY_3)
                    chatPlayer(ChatAnim.QUIZ, JIMMY_4)
                    chatNpc(ChatAnim.NEUTRAL, JIMMY_5)
                    chatPlayer(ChatAnim.NEUTRAL, JIMMY_6)
                    chatNpc(ChatAnim.ANGRY, JIMMY_7)
                    chatNpc(ChatAnim.NEUTRAL, JIMMY_8)
                }
                3 -> {
                    chatPlayer(ChatAnim.QUIZ, JOE)
                    chatNpc(ChatAnim.NEUTRAL, JOE_1)
                    chatNpc(ChatAnim.HAPPY, JOE_2)
                }
                4 -> {
                    chatPlayer(ChatAnim.QUIZ, FACE)
                    chatNpc(ChatAnim.NEUTRAL, FACE_1)
                }
                else -> {
                    chatPlayer(ChatAnim.QUIZ, FELKRASH)
                    chatNpc(ChatAnim.NEUTRAL, FELKRASH_1)
                }
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, PITS)
                chatNpc(ChatAnim.NEUTRAL, PITS_NOT_YET)
            }
            else -> {
                chatPlayer(ChatAnim.QUIZ, SHOW)
                chatNpc(ChatAnim.NEUTRAL, MANHOLE)
            }
        }
    }

    internal suspend fun Dialogue.annaJones(world: World) = with(AnnaJonesLines) {
        chatNpc(ChatAnim.HAPPY, GREETING, "name" to player.username)
        giveOne(world, BRONZE_PICKAXE)
        chatPlayer(ChatAnim.HAPPY, THANKS)
        chatNpc(ChatAnim.NEUTRAL, SUROK)
        chatPlayer(ChatAnim.NEUTRAL, OK)
        chatNpc(ChatAnim.NEUTRAL, AWAITS)
        while (true) {
            when (choice(TUNNEL, WHO, STATUE, DOING, GO)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, TUNNEL)
                    chatNpc(ChatAnim.NEUTRAL, TUNNEL_1)
                    chatPlayer(ChatAnim.QUIZ, TUNNEL_2)
                    chatNpc(ChatAnim.NEUTRAL, TUNNEL_3)
                    chatPlayer(ChatAnim.QUIZ, TUNNEL_4)
                    chatNpc(ChatAnim.NEUTRAL, TUNNEL_5)
                    chatPlayer(ChatAnim.QUIZ, TUNNEL_6)
                    chatNpc(ChatAnim.NEUTRAL, TUNNEL_7)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, WHO)
                    chatNpc(ChatAnim.HAPPY, WHO_1)
                    chatPlayer(ChatAnim.HAPPY, WHO_2)
                    chatNpc(ChatAnim.HAPPY, WHO_3)
                }
                3 -> {
                    chatPlayer(ChatAnim.QUIZ, STATUE)
                    chatNpc(ChatAnim.NEUTRAL, STATUE_1)
                    chatPlayer(ChatAnim.HAPPY, STATUE_2)
                    chatNpc(ChatAnim.NEUTRAL, STATUE_3)
                }
                4 -> {
                    chatPlayer(ChatAnim.QUIZ, DOING)
                    chatNpc(ChatAnim.NEUTRAL, DOING_1)
                    chatPlayer(ChatAnim.QUIZ, DOING_2)
                    chatNpc(ChatAnim.SAD, DOING_3)
                    chatPlayer(ChatAnim.QUIZ, DOING_4)
                    chatNpc(ChatAnim.HAPPY, DOING_5)
                    chatPlayer(ChatAnim.NEUTRAL, DOING_6)
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, GO)
                    chatNpc(ChatAnim.HAPPY, FAREWELL)
                    return@with
                }
            }
        }
    }

    /**
     * One [item] into the backpack if the player has none carried, worn or banked. A full backpack is refused, as ExamCentre's trowel is,
     * rather than one dropped on the floor each talk.
     */
    private fun Dialogue.giveOne(world: World, item: Int) {
        // Worn or banked counts as having one, so banking each can't take an endless supply (exploit round 29).
        if (MuseumCleaning.owned(player, item)) return
        if (player.inventory.freeSlots == 0) return world.message(player, osrsps.game.GameLines.NO_SPACE)
        world.objTypes[item]?.let { player.inventory.add(it, 1) }
    }

    internal suspend fun Dialogue.masterSmithingTutor(world: World) = with(MasterSmithingTutorLines) {
        chatPlayer(ChatAnim.HAPPY, HELLO)
        chatNpc(ChatAnim.HAPPY, GREETING)
        while (true) {
            when (choice(TRAIN, WHAT, NOT_NOW)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, TRAIN)
                    if (player.inventory.count(HAMMER) == 0L) {
                        chatNpc(ChatAnim.NEUTRAL, NO_HAMMER)
                        giveOne(world, HAMMER)
                    } else {
                        chatNpc(ChatAnim.NEUTRAL, HAS_HAMMER)
                    }
                    val level = player.skills.baseLevel(Skill.SMITHING)
                    when {
                        level <= 20 -> {
                            for (line in listOf(LOW_1, LOW_2, LOW_3, LOW_4, LOW_5, LOW_6)) chatNpc(ChatAnim.NEUTRAL, line)
                            chatPlayer(ChatAnim.HAPPY, LOW_THANKS)
                        }
                        // Every quest done: no Knight's Sword lines.
                        level <= 37 -> for (line in listOf(MID_1, MID_2, MID_3)) chatNpc(ChatAnim.NEUTRAL, line)
                        else -> {
                            if (level >= 99) chatNpc(ChatAnim.HAPPY, HIGH_99)
                            // Barbarian Smithing done too: where Otto is.
                            for (line in listOf(HIGH_1, HIGH_2, HIGH_3, HIGH_4, HIGH_5, HIGH_OTTO)) chatNpc(ChatAnim.NEUTRAL, line)
                        }
                    }
                    chatNpc(ChatAnim.QUIZ, ANYTHING_ELSE)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, WHAT)
                    for (line in listOf(WHAT_1, WHAT_2, WHAT_3, WHAT_4, WHAT_5)) chatNpc(ChatAnim.NEUTRAL, line)
                    chatNpc(ChatAnim.QUIZ, WHAT_6)
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, NOT_NOW)
                    chatNpc(ChatAnim.HAPPY, COME_BACK)
                    return@with
                }
            }
        }
    }
}

fun registerVarrockSweepPeople(world: World) {
    with(VarrockSweepPeople) {
        fun talk(id: Int, block: suspend Dialogue.() -> Unit) =
            world.handlers.onNpc(id, TALK_TO) { op -> world.dialogues.start(this, op.npc) { block() } }
        talk(GUIDOR) { guidor() }
        talk(CHANCY) { chancy() }
        talk(POX) { pox() }
        talk(HOOKNOSED_JACK) { hooknosedJack() }
        talk(ANNA_JONES) { annaJones(world) }
        talk(MASTER_SMITHING_TUTOR) { masterSmithingTutor(world) }
        talk(JEFF) { chatNpc(ChatAnim.NEUTRAL, MahoganyClientLines.JEFF) }
        talk(BOB) { chatNpc(ChatAnim.NEUTRAL, MahoganyClientLines.BOB) }
        talk(SARAH) { chatNpc(ChatAnim.NEUTRAL, MahoganyClientLines.SARAH) }
    }
}
