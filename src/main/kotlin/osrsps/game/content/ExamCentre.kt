package osrsps.game.content

import osrsps.game.InvObj
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** The Exam Centre's people after The Dig Site, from the Wiki's Transcript:Examiner and Transcript:Researcher. */
object ExamCentreLines : Lines("exam_centre", "Transcripts: Examiner, Researcher, The Dig Site; Bookcase (Exam Centre); Cupboard (rock pick), Cupboard (specimen jar)") {
    val ZAROS_ALTAR = line("zaros_altar", "an examiner's greeting after The Dig Site")
    val GREAT_SCHOLAR = line("great_scholar", "her second line")
    val THANKS = line("thanks", "option 1, which the player then says")
    val LOST_TROWEL = line("lost_trowel", "option 2, which the player then says")
    val ANOTHER_TROWEL = line("another_trowel", "option 2 with no trowel carried: she gives one")
    val LOOK_IN_BACKPACK = line("look_in_backpack", "option 2 with a trowel carried")
    val BIG_FIND = line("big_find", "the Researcher's greeting after The Dig Site; his menu is the Panning guide's")
    val EARTH_SCIENCES = line("earth_sciences", "Search on a bookcase: the message box (the quest's, taken for after it)")
    val FIND_ROCK_PICK = line("find_rock_pick", "Search on the open north cupboard: the item box")
    val FIND_SPECIMEN_JAR = line("find_specimen_jar", "Search on the open south cupboard: the item box")
    val CUPBOARD_EMPTY = line("cupboard_empty", "OUR OWN: Search on an open cupboard with its tool already carried")
}

/** Terry Balando, the archaeological expert, after The Dig Site: his talk and the finds used on him (Transcript:Terry Balando). */
object TerryLines : Lines("terry", "Transcript: Terry Balando") {
    val DT1_OPTION = line("dt1_option", "the menu's first option after Desert Treasure I")
    val DT1_ASK = line("dt1_ask", "his question about the translation")
    val DT1_THANKS = line("dt1_thanks", "the player's answer")
    val DIG_OPTION = line("dig_option", "the menu's second option")
    val STUDYING = line("studying", "the Digsite quest: the altar he studies")
    val ZAROS = line("zaros", "the Digsite quest: a god named Zaros")
    val GREAT_FIND = line("great_find", "the Digsite quest: his last line")
    val STAFF_INCREDIBLE = line("staff_incredible", "Talk-to with an ancient staff wielded, or the staff used on him")
    val STAFF_BANISHED = line("staff_banished", "the player's answer")
    val STAFF_HEARD = line("staff_heard", "his reply")
    val ANYWAY = line("anyway", "his last line before the Digsite quest's lines")
    val WHAT_DO_YOU_THINK = line("what_do_you_think", "the player, using an ancient staff on him")
    val DO_YOU_KNOW = line("do_you_know", "the player, using unidentified liquid on him")
    val WHERE_GET = line("where_get", "his question")
    val FROM_BARRELS = line("from_barrels", "the player's answer")
    val NITRO_DANGEROUS = line("nitro_dangerous", "his warning; the liquid is now nitroglycerin")
    val TELL_ME_MORE = line("tell_me_more", "the player, using nitroglycerin on him")
    val NITRO_MIXED = line("nitro_mixed", "his reply")
    val NITRO_VOLATILE = line("nitro_volatile", "his second line")
    val HAVE_A_LOOK = line("have_a_look", "the player, using ammonium nitrate on him")
    val AMMONIUM = line("ammonium", "his reply")
    val LOOK_AT_THIS = line("look_at_this", "the player, using mixed chemicals on him")
    val MIXED = line("mixed", "his reply")
    val SEE_COMPOUND = line("see_compound", "the player, using mixed chemicals with charcoal on him")
    val DEATH_WISH = line("death_wish", "his reply")
    val FIND_A_USE = line("find_a_use", "the player's answer")
    val WHAT_DO_YOU_THINK_THIS = line("what_do_you_think_this", "the player, using the chemical compound on him")
    val CONCOCTED = line("concocted", "his reply")
    val NUGGETS_HAVE = line("nuggets", "the player, using nuggets on him")
    val NUGGETS_FEW = line("nuggets_few", "with fewer than 3")
    val NUGGETS_EXCHANGED = line("nuggets_exchanged", "with 3 or more: 3 nuggets for a gold ore")
    val EXCELLENT = line("excellent", "the player's answer")
    val NEEDLE = line("needle", "the player, using a needle on him")
    val NEEDLEWORK = line("needlework", "his reply")
    val ROTTEN_APPLES = line("rotten_apples", "the player, using a rotten apple on him")
    val THROW_AWAY = line("throw_away", "his reply")
    val GLASS = line("glass", "the player, using broken glass on him")
    val CUT_FINGERS = line("cut_fingers", "his reply")
    val ARROW = line("arrow", "the player, using a broken arrow on him")
    val SPLIT_ARROW = line("split_arrow", "his reply")
    val NO_NEED_TRAYS = line("no_need_trays", "an empty panning tray used on him")
    val SEARCHED_TRAY = line("searched_tray", "a panning tray with something in it: his question")
    val NOT_REMEMBER = line("not_remember", "the player's answer")
    val DIRTY_HANDS = line("dirty_hands", "his reply")
    val HANDS_BACK = line("hands_back", "the message after")
    val BONES = line("bones", "the player, using bones on him")
    val COW_BONES = line("cow_bones", "his reply")
    val BUTTONS = line("buttons", "the player, using buttons on him")
    val NOBILITY = line("nobility", "his reply")
    val ROCK = line("rock", "the player, using a cracked sample on him")
    val CRACKED = line("cracked", "his reply")
    val HEY_LOOK = line("hey_look", "the player, using an old tooth on him")
    val TOOTH = line("tooth", "his reply")
    val SWORD = line("sword", "the player, using a rusty sword on him")
    val RUSTY = line("rusty", "his reply")
    val STAFF = line("staff", "the player, using a broken staff on him")
    val CLERIC = line("cleric", "his reply")
    val CIVILIZATION = line("civilization", "his second line")
    val ARMOUR = line("armour", "the player, using broken armour on him")
    val BATTLE = line("battle", "his reply")
    val OLD_ARMOUR = line("old_armour", "the player, using damaged armour on him")
    val NOT_MATCH = line("not_match", "his reply")
    val POTTERY = line("pottery", "the player, using ceramic remains on him")
    val POTTERS = line("potters", "his reply")
    val POTIONS = line("potions", "the player's answer")
    val CLAY = line("clay", "his last line")
    val UNUSUAL = line("unusual", "the player, using a belt buckle on him")
    val BUCKLE = line("buckle", "his reply")
    val SKULL = line("skull", "his reply to an animal skull (the player says have_a_look_this)")
    val HAVE_A_LOOK_THIS = line("have_a_look_this", "the player, using an animal skull, the special cup, a teddy, an old boot or the stone tablet on him")
    val CLEANS_MUD = line("cleans_mud", "the special cup: the message box")
    val CUP = line("cup", "his reply to the special cup")
    val TEDDY = line("teddy", "his reply to a teddy")
    val BOOT = line("boot", "his reply to an old boot")
    val SEEN_TALISMAN = line("seen_talisman", "the ancient talisman used on him: the message box")
    val NO_POINT = line("no_point", "the invitation letter used on him")
    val ANOTHER_TABLET = line("another_tablet", "his reply to the stone tablet")
}

/** Doug Deeping in the Digsite Dungeon, and the winches' messages (Transcripts: Doug Deeping, The Dig Site; Winch). */
object DigsiteDungeonLines : Lines("digsite_dungeon", "Transcripts: Doug Deeping, The Dig Site; Winch") {
    val DOUG_HELLO = line("doug_hello", "Doug Deeping after The Dig Site")
    val DOUG_STEAL = line("doug_steal", "Steal-from on Doug: he catches the player at once")
    val DOUG_SORRY = line("doug_sorry", "the player's answer")
    val OPERATE = line("operate", "Operate on the west winch: the first message")
    val BUCKET = line("bucket", "the second message")
    val COULD_FIT = line("could_fit", "the player's line after")
    val TIE_ROPE = line("tie_rope", "a rope used on the west winch")
    val TRY_CLIMB = line("try_climb", "climbing down: the first message")
    val NEED_AGILITY = line("need_agility", "without 10 Agility")
    val LOWER = line("lower", "with 10 Agility: the first message")
    val CAVERN = line("cavern", "the second message")
}

/**
 * Misthalin piece 7 step 10: the Exam Centre and the Digsite Dungeon after The Dig Site. Sources: the OSRS Wiki's Exam
 * Centre, Digsite Dungeon, Winch, Examiner, Researcher, Terry Balando, Doug Deeping, Cupboard (rock pick), Cupboard
 * (specimen jar) and Bookcase (Exam Centre) pages and the transcripts on the Lines objects, read 2026-11-11; mejrs's
 * data_osrs NPC list for the people and skeletons; the 242 cache's map.
 *
 * - The three examiners congratulate the player and give a lost trowel back; the Researcher talks as the Panning guide
 *   does; Terry Balando's menu asks about Desert Treasure I and the Digsite quest, he admires a wielded ancient staff,
 *   and every find used on him gets his line. Three nuggets on him become a gold ore, and unidentified liquid becomes
 *   nitroglycerin.
 * - The north cupboard holds a rock pick and the south one a specimen jar, each for a player carrying none. The
 *   bookcases say the helpful books have been taken.
 * - The west winch (2350) wants a rope used on it, which stays in the backpack; the private dig's winch (2351) needs
 *   none, every account having Terry's permission. Both need 10 Agility and give 2 Agility XP. The map has the dungeon
 *   twice; the copy with the quest's bricks gone (z 9743 to 9770) is the one after the quest. The private winch lands by
 *   Doug Deeping, the west winch by the rope to the south chamber's skeletons and silver rocks, as the Wiki says. Each
 *   rope climbs back up beside its winch.
 *
 * Guessed: every chathead expression; the private winch's messages (the west winch's after its rope), the tiles each
 * climb lands on, the 2 Agility XP on each way down, the climbing animations and waits, a rope being needed every time,
 * the bookcases' message after the quest, the cupboards' line with the tool carried. Out: the clue chest, the beaten
 * book, Varmen's notes and the book on chemicals (the quests'), the barrel's liquid (step 9b leaves the barrel out),
 * Desert Treasure II's winch, the STASH units.
 */
object ExamCentre {
    val EXAMINERS = listOf(3635, 3636, 3637)
    const val RESEARCHER = 3638
    const val TERRY = 3639
    const val DOUG = 3629
    const val TALK_TO = 1
    const val STEAL_FROM = 3

    const val CUPBOARD_PICK = 17300
    const val CUPBOARD_PICK_OPEN = 17301
    const val CUPBOARD_JAR = 17302
    const val CUPBOARD_JAR_OPEN = 17303
    const val OPEN = 1
    const val CLOSE = 1
    const val SEARCH = 2
    val BOOKCASES = listOf(6292, 17319, 17320, 17321, 17382)

    const val WEST_WINCH = 2350
    const val PRIVATE_WINCH = 2351
    const val OPERATE = 1
    const val ROPE_PRIVATE = 2352
    const val ROPE_WEST = 2353
    const val CLIMB_UP = 1

    /** Where each way down lands and each rope back up puts the player (guessed: beside the rope or winch). */
    val PRIVATE_BOTTOM = Destination(3352, 9753, 0)
    val WEST_BOTTOM = Destination(3369, 9763, 0)
    val PRIVATE_TOP = Destination(3370, 3427, 0)
    val WEST_TOP = Destination(3352, 3416, 0)

    const val AGILITY_LEVEL = 10
    const val AGILITY_XP_TENTHS = 20

    const val ROPE = 954
    const val TROWEL = 676
    const val ROCK_PICK = 675
    const val SPECIMEN_JAR = 669
    const val ANCIENT_STAFF = 4675
    const val NUGGETS = 680
    const val GOLD_ORE = 444
    const val UNIDENTIFIED_LIQUID = 702
    const val NITROGLYCERIN = 703
    const val TRAY_EMPTY = 677
    val TRAYS_FULL = listOf(678, 679)
    const val SPECIAL_CUP = 672
    const val TALISMAN = 681
    const val INVITATION = 696
    const val STONE_TABLET = 699

    /** The finds whose use on Terry is lines alone: each obj, and who says what (true for the player). */
    val FINDS: Map<Int, List<Pair<Line, Boolean>>> = with(TerryLines) {
        mapOf(
            NITROGLYCERIN to listOf(TELL_ME_MORE to true, NITRO_MIXED to false, NITRO_VOLATILE to false),
            701 to listOf(HAVE_A_LOOK to true, AMMONIUM to false),
            705 to listOf(LOOK_AT_THIS to true, MIXED to false),
            706 to listOf(SEE_COMPOUND to true, DEATH_WISH to false, FIND_A_USE to true),
            707 to listOf(WHAT_DO_YOU_THINK_THIS to true, CONCOCTED to false),
            1733 to listOf(NEEDLE to true, NEEDLEWORK to false),
            1984 to listOf(ROTTEN_APPLES to true, THROW_AWAY to false),
            690 to listOf(GLASS to true, CUT_FINGERS to false),
            687 to listOf(ARROW to true, SPLIT_ARROW to false),
            526 to listOf(BONES to true, COW_BONES to false),
            688 to listOf(BUTTONS to true, NOBILITY to false),
            674 to listOf(ROCK to true, CRACKED to false),
            695 to listOf(HEY_LOOK to true, TOOTH to false),
            686 to listOf(SWORD to true, RUSTY to false),
            689 to listOf(STAFF to true, CLERIC to false, CIVILIZATION to false),
            698 to listOf(ARMOUR to true, BATTLE to false),
            697 to listOf(OLD_ARMOUR to true, NOT_MATCH to false),
            694 to listOf(POTTERY to true, POTTERS to false, POTIONS to true, CLAY to false),
            684 to listOf(UNUSUAL to true, BUCKLE to false),
            671 to listOf(HAVE_A_LOOK_THIS to true, SKULL to false),
            673 to listOf(HAVE_A_LOOK_THIS to true, TEDDY to false),
            685 to listOf(HAVE_A_LOOK_THIS to true, BOOT to false),
            STONE_TABLET to listOf(HAVE_A_LOOK_THIS to true, ANOTHER_TABLET to false),
        )
    }

    internal suspend fun Dialogue.say(lines: List<Pair<Line, Boolean>>) {
        for ((line, isPlayer) in lines) if (isPlayer) chatPlayer(ChatAnim.NEUTRAL, line) else chatNpc(ChatAnim.NEUTRAL, line)
    }

    suspend fun ScriptContext.examiner(world: World, npc: osrsps.game.npc.Npc?) = with(ExamCentreLines) {
        var give = false
        world.dialogues.start(this@examiner, npc) {
            chatNpc(ChatAnim.HAPPY, ZAROS_ALTAR)
            chatNpc(ChatAnim.HAPPY, GREAT_SCHOLAR)
            if (choice(THANKS, LOST_TROWEL) == 1) {
                chatPlayer(ChatAnim.HAPPY, THANKS)
                return@start
            }
            chatPlayer(ChatAnim.SAD, LOST_TROWEL)
            if (player.inventory.count(TROWEL) > 0) {
                chatNpc(ChatAnim.NEUTRAL, LOOK_IN_BACKPACK)
            } else {
                chatNpc(ChatAnim.NEUTRAL, ANOTHER_TROWEL)
                give = true
            }
        }
        if (!give || player.inventory.count(TROWEL) > 0) return@with
        // A full backpack is refused, as the cupboards do, rather than a trowel dropped on the floor each talk.
        if (player.inventory.freeSlots == 0) return@with world.message(player, osrsps.game.GameLines.NO_SPACE)
        world.objTypes[TROWEL]?.let { player.inventory.add(it, 1) }
    }

    internal suspend fun Dialogue.researcher() {
        chatNpc(ChatAnim.HAPPY, ExamCentreLines.BIG_FIND)
        with(PanningGuideLines) {
            if (choice(TOOLS, THANK_YOU) == 2) {
                chatPlayer(ChatAnim.HAPPY, THANK_YOU)
                return
            }
            chatPlayer(ChatAnim.QUIZ, TOOLS)
            chatNpc(ChatAnim.HAPPY, ROCK_PICKS)
            chatPlayer(ChatAnim.QUIZ, SAMPLE_JARS)
            chatNpc(ChatAnim.NEUTRAL, JARS_EVERYWHERE)
            chatPlayer(ChatAnim.QUIZ, BRUSH)
            chatNpc(ChatAnim.NEUTRAL, BRUSH_SHORTAGE)
            chatPlayer(ChatAnim.SHIFTY, SNEAKY)
            chatNpc(ChatAnim.NEUTRAL, EXAMS)
            chatPlayer(ChatAnim.QUIZ, ANYTHING_ELSE)
            chatNpc(ChatAnim.NEUTRAL, TERRY)
            chatPlayer(ChatAnim.HAPPY, OK_THANKS)
        }
    }

    private suspend fun Dialogue.digsiteQuest() = with(TerryLines) {
        chatNpc(ChatAnim.NEUTRAL, STUDYING)
        chatNpc(ChatAnim.CONFUSED, ZAROS)
        chatNpc(ChatAnim.HAPPY, GREAT_FIND)
    }

    private suspend fun Dialogue.ancientStaff() = with(TerryLines) {
        chatNpc(ChatAnim.SHOCKED, STAFF_INCREDIBLE)
        chatPlayer(ChatAnim.NEUTRAL, STAFF_BANISHED)
        chatNpc(ChatAnim.HAPPY, STAFF_HEARD)
        chatNpc(ChatAnim.NEUTRAL, ANYWAY)
        digsiteQuest()
    }

    internal suspend fun Dialogue.terry(staffWielded: Boolean) = with(TerryLines) {
        if (staffWielded) return ancientStaff()
        // Every account has done Desert Treasure I, so its option always shows.
        if (choice(DT1_OPTION, DIG_OPTION) == 1) {
            chatNpc(ChatAnim.QUIZ, DT1_ASK)
            chatPlayer(ChatAnim.HAPPY, DT1_THANKS)
        } else {
            digsiteQuest()
        }
    }

    /** Item [obj] used on Terry. */
    suspend fun ScriptContext.useOnTerry(world: World, npc: osrsps.game.npc.Npc?, obj: Int) = with(TerryLines) {
        val lines = FINDS[obj]
        when {
            lines != null -> world.dialogues.start(this@useOnTerry, npc) { say(lines) }
            obj == ANCIENT_STAFF -> world.dialogues.start(this@useOnTerry, npc) {
                chatPlayer(ChatAnim.NEUTRAL, WHAT_DO_YOU_THINK)
                ancientStaff()
            }
            obj == UNIDENTIFIED_LIQUID -> {
                val nitro = world.objTypes[NITROGLYCERIN] ?: return@with
                for ((slot, item) in player.inventory.contents()) if (item.id == UNIDENTIFIED_LIQUID) player.inventory[slot] = InvObj(nitro.id, item.count)
                world.dialogues.start(this@useOnTerry, npc) {
                    say(listOf(DO_YOU_KNOW to true, WHERE_GET to false, FROM_BARRELS to true, NITRO_DANGEROUS to false))
                }
            }
            obj == NUGGETS -> world.dialogues.start(this@useOnTerry, npc) {
                chatPlayer(ChatAnim.NEUTRAL, NUGGETS_HAVE)
                if (player.inventory.count(NUGGETS) < 3) {
                    chatNpc(ChatAnim.NEUTRAL, NUGGETS_FEW)
                    return@start
                }
                val slot = player.inventory.contents().firstOrNull { it.second.id == NUGGETS }?.first ?: return@start
                player.inventory.remove(slot, 3)
                world.objTypes[GOLD_ORE]?.let { world.groundObjs.give(player, it, 1) }
                chatNpc(ChatAnim.HAPPY, NUGGETS_EXCHANGED)
                chatPlayer(ChatAnim.HAPPY, EXCELLENT)
            }
            obj == TRAY_EMPTY -> world.dialogues.start(this@useOnTerry, npc) { chatNpc(ChatAnim.NEUTRAL, NO_NEED_TRAYS) }
            obj in TRAYS_FULL -> {
                world.dialogues.start(this@useOnTerry, npc) {
                    chatNpc(ChatAnim.QUIZ, SEARCHED_TRAY)
                    chatPlayer(ChatAnim.CONFUSED, NOT_REMEMBER)
                    chatNpc(ChatAnim.NEUTRAL, DIRTY_HANDS)
                }
                world.message(player, HANDS_BACK)
            }
            obj == SPECIAL_CUP -> world.dialogues.start(this@useOnTerry, npc) {
                chatPlayer(ChatAnim.NEUTRAL, HAVE_A_LOOK_THIS)
                mesbox(CLEANS_MUD)
                chatNpc(ChatAnim.NEUTRAL, CUP)
            }
            obj == TALISMAN -> world.dialogues.start(this@useOnTerry, npc) { mesbox(SEEN_TALISMAN) }
            obj == INVITATION -> world.dialogues.start(this@useOnTerry, npc) { chatNpc(ChatAnim.NEUTRAL, NO_POINT) }
            else -> world.message(player, osrsps.game.GameLines.NOTHING_INTERESTING)
        }
    }

    /** Search on an open cupboard holding [tool], with its item box [line]. */
    suspend fun ScriptContext.searchCupboard(world: World, tool: Int, line: Line) {
        arriveDelay()
        if (player.inventory.count(tool) > 0) return world.message(player, ExamCentreLines.CUPBOARD_EMPTY)
        if (player.inventory.freeSlots == 0) return world.message(player, osrsps.game.GameLines.NO_SPACE)
        world.objTypes[tool]?.let { player.inventory.add(it, 1) }
        world.dialogues.start(this) { objbox(tool, line) }
    }

    /** Down a winch's shaft to [to], once the Agility check passes. */
    suspend fun ScriptContext.climbDown(world: World, to: Destination) = with(DigsiteDungeonLines) {
        world.message(player, TRY_CLIMB)
        if (player.skills.currentLevel(Skill.AGILITY) < AGILITY_LEVEL) return@with world.message(player, NEED_AGILITY)
        world.message(player, LOWER)
        player.anim(Climbing.ANIM_PICKUP_FLOOR)
        delay(2)
        world.teleport(player, to.x, to.z, to.level)
        player.skills.addTenths(Skill.AGILITY, AGILITY_XP_TENTHS)
        world.message(player, CAVERN)
    }

    suspend fun ScriptContext.operateWestWinch(world: World) = with(DigsiteDungeonLines) {
        arriveDelay()
        world.message(player, OPERATE)
        world.message(player, BUCKET)
        world.dialogues.start(this@operateWestWinch) { chatPlayer(ChatAnim.QUIZ, COULD_FIT) }
    }

    suspend fun ScriptContext.ropeOnWinch(world: World) {
        arriveDelay()
        world.message(player, DigsiteDungeonLines.TIE_ROPE)
        climbDown(world, WEST_BOTTOM)
    }

    suspend fun ScriptContext.climbUp(world: World, to: Destination) {
        arriveDelay()
        player.anim(Climbing.ANIM_REACH_FOR_LADDER)
        delay(2)
        world.teleport(player, to.x, to.z, to.level)
    }

    internal fun registerCupboard(world: World, closed: Int, open: Int, tool: Int, line: Line) {
        world.handlers.onLoc(closed, OPEN) { op -> swapFurniture(world, op.loc!!, open, Searching.ANIM_OPEN) }
        world.handlers.onLoc(open, CLOSE) { op -> swapFurniture(world, op.loc!!, closed, Searching.ANIM_SHUT) }
        world.handlers.onLoc(open, SEARCH) { searchCupboard(world, tool, line) }
    }
}

fun registerExamCentre(world: World) {
    with(ExamCentre) {
        for (examiner in EXAMINERS) world.handlers.onNpc(examiner, TALK_TO) { op -> examiner(world, op.npc) }
        world.handlers.onNpc(RESEARCHER, TALK_TO) { op -> world.dialogues.start(this, op.npc) { researcher() } }
        world.handlers.onNpc(TERRY, TALK_TO) { op ->
            val staff = player.equipment[WearPos.WEAPON]?.id == ANCIENT_STAFF
            world.dialogues.start(this, op.npc) { terry(staff) }
        }
        world.handlers.onObjOnNpc(TERRY) { op -> useOnTerry(world, op.npc, op.obj) }
        registerCupboard(world, CUPBOARD_PICK, CUPBOARD_PICK_OPEN, ROCK_PICK, ExamCentreLines.FIND_ROCK_PICK)
        registerCupboard(world, CUPBOARD_JAR, CUPBOARD_JAR_OPEN, SPECIMEN_JAR, ExamCentreLines.FIND_SPECIMEN_JAR)
        for (bookcase in BOOKCASES) world.handlers.onLoc(bookcase, 1) {
            arriveDelay()
            world.dialogues.start(this) { mesbox(ExamCentreLines.EARTH_SCIENCES) }
        }

        world.handlers.onLoc(WEST_WINCH, OPERATE) { operateWestWinch(world) }
        world.handlers.onObjOnLoc(WEST_WINCH, ROPE) { ropeOnWinch(world) }
        world.handlers.onLoc(PRIVATE_WINCH, OPERATE) {
            arriveDelay()
            climbDown(world, PRIVATE_BOTTOM)
        }
        world.handlers.onLoc(ROPE_PRIVATE, CLIMB_UP) { climbUp(world, PRIVATE_TOP) }
        world.handlers.onLoc(ROPE_WEST, CLIMB_UP) { climbUp(world, WEST_TOP) }

        world.handlers.onNpc(DOUG, TALK_TO) { op ->
            world.dialogues.start(this, op.npc) { chatNpc(ChatAnim.HAPPY, DigsiteDungeonLines.DOUG_HELLO) }
        }
        world.handlers.onNpc(DOUG, STEAL_FROM) { op ->
            world.dialogues.start(this, op.npc) {
                chatNpc(ChatAnim.ANGRY, DigsiteDungeonLines.DOUG_STEAL)
                chatPlayer(ChatAnim.SAD, DigsiteDungeonLines.DOUG_SORRY)
            }
        }
    }
}
