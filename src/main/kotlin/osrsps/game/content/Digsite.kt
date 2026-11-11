package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.script.ScriptContext

/** The Digsite's workmen and students after The Dig Site; from the Wiki's Transcript:Digsite workman and Transcript:Student. */
object DigsitePeopleLines : Lines("digsite_people", "Transcript:Digsite workman, Transcript:Student") {
    val HELLO = line("hello", "the player's greeting to a workman or the teddy student")
    val GREAT_ARCHAEOLOGIST = line("great_archaeologist", "a Digsite workman after The Dig Site: congratulations on the find")
    val CAUGHT = line("caught", "a Digsite workman who catches a thief: overhead")
    val FAMOUS = line("famous", "the animal skull student after the quest: the player is famous here now")
    val GREAT_EXPLORER = line("great_explorer", "the special cup student after the quest: thanks for the help")
    val THANKS_AGAIN = line("thanks_again", "the teddy student after the quest: thanks again, the player is an expert now")
}

/** Elissa's lines; from the Wiki's Transcript:Elissa. */
object ElissaLines : Lines("elissa", "Transcript:Elissa") {
    val HELLO = line("hello", "her greeting")
    val WHAT_DO_YOU_DO = line("what_do_you_do", "option 1, which the player then says")
    val ARCHITECTURE = line("architecture", "option 1: her answer, Third Age architecture")
    val WHAT_PLACE = line("what_place", "option 2, which the player then says")
    val GREAT_CITY = line("great_city", "option 2: her answer, a great Third Age city")
    val VARROCK = line("varrock", "option 2's first sub-option")
    val VARROCK_SAID = line("varrock_said", "that sub-option as the player says it (the transcript words it differently)")
    val BURIED = line("buried", "her answer: buried for three thousand years")
    val WHAT_HAPPENED = line("what_happened", "option 2's second sub-option, which the player then says")
    val NO_ONE_KNOWS = line("no_one_knows", "her answer, the first box")
    val DESTRUCTION = line("destruction", "the second box: the gods at war")
    val CIVILIZATIONS = line("civilizations", "the third box")
}

/** The Panning guide's lines; from the Wiki's Transcript:Panning guide. */
object PanningGuideLines : Lines("panning_guide", "Transcript:Panning guide") {
    val WHO = line("who", "the player's greeting")
    val I_AM = line("i_am", "his introduction")
    val EXCELLENT = line("excellent", "the player's reply")
    val HOW_IT_WORKS = line("how_it_works", "how panning works: a tray in the panning points")
    val GOLD = line("gold", "gold goes to the archaeological expert")
    val TOOLS = line("tools", "option 1, which the player then says")
    val ROCK_PICKS = line("rock_picks", "option 1: rock picks")
    val SAMPLE_JARS = line("sample_jars", "option 1, the player: sample jars?")
    val JARS_EVERYWHERE = line("jars_everywhere", "option 1: jars are everywhere")
    val BRUSH = line("brush", "option 1, the player: a specimen brush?")
    val BRUSH_SHORTAGE = line("brush_shortage", "option 1: a shortage; borrow one from a workman")
    val SNEAKY = line("sneaky", "option 1, the player: sneaky; a trowel?")
    val EXAMS = line("exams", "option 1: the trowel is earned by passing the exams")
    val ANYTHING_ELSE = line("anything_else", "option 1, the player: anything else?")
    val TERRY = line("terry", "option 1: Terry identifies things")
    val OK_THANKS = line("ok_thanks", "option 1, the player's last line")
    val THANK_YOU = line("thank_you", "option 2, which the player then says")
}

/**
 * The barge's people after Bone Voyage, the Captain's crashed glider and the workers' gate's guard; from the Wiki's
 * transcripts of the Barge guard, Barge foreman, Barge workman, Ed Wood, Lead Navigator, Junior Navigator, Captain Errdo
 * and Museum guard.
 */
object BargeLines : Lines("digsite_barge", "Transcripts: Barge guard, Barge foreman, Barge workman, Ed Wood, Lead Navigator, Junior Navigator, Captain Errdo, Museum guard") {
    val HELLO = line("hello", "the player's greeting to a barge guard or a navigator")
    val CAN_I_HELP = line("can_i_help", "a barge guard's reply")
    val BOARD = line("board", "the canal guard's option 1, which the player then says")
    val ABSOLUTELY_BOARD = line("absolutely_board", "the canal guard's answer: the navigators are waiting")
    val HELPS_BOARD = line("helps_board", "the message as the guard helps the player aboard")
    val FOSSIL_ISLAND = line("fossil_island", "the canal guard's option 2")
    val NO_THANKS = line("no_thanks", "the guards' and Lead Navigator's last option, which the player then says")
    val GET_OFF = line("get_off", "the barge's guard's option 1, which the player then says")
    val ABSOLUTELY_OFF = line("absolutely_off", "the barge's guard's answer")
    val HELPS_OFF = line("helps_off", "the message as the guard helps the player off")
    val TRAVEL_FOSSIL = line("travel_fossil", "the Lead Navigator's question")
    val TRAVEL_TITLE = line("travel_title", "the title over his yes/no menu")
    val YES_PLEASE = line("yes_please", "his menu's option 1")
    val LEAD_NAVIGATOR = line("lead_navigator", "the Junior Navigator's answer to Talk-to")
    val LEAD_NAVIGATOR_TRAVEL = line("lead_navigator_travel", "the Junior Navigator's answer to Travel")
    val FOREMAN_THANKS = line("foreman_thanks", "the Barge foreman after Bone Voyage, by the player's name", "name")
    val BUSY = (1..6).map { line("busy_$it", "the Barge workman's and Ed Wood's overhead line $it of the Wiki's six") }
    val EMBARRASSING = line("embarrassing", "Captain Errdo by his crashed glider east of Varrock")
    val WHAT_HAPPENED = line("what_happened", "the player's question")
    val LANDING_GEAR = line("landing_gear", "his answer: the landing gear")
    val WORKMANS_GATE = line("workmans_gate", "the workers' gate's guard, without 153 Kudos")
    val NEED_KUDOS = line("need_kudos", "the message box after him")
    val WORKMANS_GATE_ONLY = line("workmans_gate_only", "his overhead line when the player tries the gate")
    val FOSSIL_LATER = line("fossil_later", "Quick-Travel, Travel and the trip to Fossil Island: our own line, as the island is outside Misthalin")
}

/** The Digsite's signposts; from the Wiki's Signpost (Digsite). */
object DigsiteSignLines : Lines("digsite_sign", "Signpost (Digsite)") {
    val TRAINING = line("training", "Read on a training site's signpost")
    val LEVEL_1 = line("level_1", "Read on a level 1 site's signpost")
    val LEVEL_2 = line("level_2", "Read on a level 2 site's signpost")
    val LEVEL_3 = line("level_3", "Read on a level 3 site's signpost")
    val PRIVATE = line("private", "Read on the private dig's signpost")
}

/**
 * Misthalin piece 7 step 9a: the Digsite's people and the canal barge, after The Dig Site and Bone Voyage. Sources: the
 * OSRS Wiki's Digsite, Digsite workman, Student, Canal barge, Kudos, Gnome glider, Captain Errdo and Signpost (Digsite)
 * pages and the transcripts named on each Lines object, read 2026-11-11, mejrs's NPC list and the 242 cache. The NPCs
 * are in digsite.json.
 *
 * - The Digsite workmen (`digworkman1`, `qip_digsite_digworkman_03` and `_04`) greet the player as the great
 *   archaeologist. Their Steal-from and the teddy student's Pickpocket are [osrsps.game.skill.Pickpocketing]'s.
 * - The three students each thank the player for the item the quest found: 3632 the animal skull, 3633 the special cup,
 *   3634 the teddy (the Wiki's Student page gives which is which).
 * - Elissa and the Panning guide talk as their transcripts have them; panning itself is step 9b.
 * - The canal barge (Bone Voyage is done, so `fossilquest_progress` shows each multi-NPC's post-quest type): the canal's
 *   barge guard (`fossilquest_barge_guard_port` 7767, showing 8012) helps the player aboard by talk or Embark, and the
 *   barge's own guard (7768) helps them off by talk or Disembark. Quick-Travel, the trip in the canal guard's menu and
 *   the Lead Navigator's Travel refuse with [BargeLines.FOSSIL_LATER]: Fossil Island is its own region. The Junior
 *   Navigator sends the player to the Lead Navigator. John and David are hidden once the quest is done.
 * - The Barge foreman thanks the player; the Barge workman and Ed Wood say one of their six busy lines overhead.
 * - Captain Errdo by his crashed glider (Lemanto Andra) says the landing gear is broken; the glider goes nowhere.
 * - The workers' gate east of Varrock (`vm_fencegate_l`/`_r` 24560/24561) wants 153 Kudos; the museum counts none yet
 *   ([VarrockMuseum]), so its guard (`vm_museum_guard2` 1911) turns everyone away, by talk or by the gate.
 * - The signposts read their site's level.
 *
 * Guessed: every chathead expression; the tiles the boarding and leaving put the player on (beside each guard on the
 * map's walkable deck and quay); Embark and Disembark doing what the talk does without its chat; the Fossil Island
 * refusal. Left out: walking up to the workers' gate (live's guard calls out as the player nears it), the workmen's
 * routes to the museum and the barge workers' animations' props, the Digsite pendant.
 */
object Digsite {
    const val TALK_TO = 1

    /** `digworkman1`, `qip_digsite_digworkman_03` and `_04`. */
    val WORKMEN = listOf(3628, 3630, 3631)

    /** The students by the item each lost: the animal skull, the special cup and the teddy. */
    const val STUDENT_SKULL = 3632
    const val STUDENT_CUP = 3633
    const val STUDENT_TEDDY = 3634

    const val ELISSA = 5138
    const val PANNING_GUIDE = 3640

    /** `fossilquest_barge_guard_port_postquest`, shown by the canal's guard (7767): Talk-to, Embark, Quick-Travel. */
    const val CANAL_GUARD = 8012
    const val EMBARK = 3
    const val QUICK_TRAVEL = 4

    /** `fossilquest_barge_guard_barge`, on the barge: Talk-to, Disembark. */
    const val BARGE_GUARD = 7768
    const val DISEMBARK = 3

    /** The navigators' post-quest types (shown by 7819 and 7820): Talk-to, Travel. */
    const val LEAD_NAVIGATOR = 7762
    const val JUNIOR_NAVIGATOR = 7764
    const val TRAVEL = 3

    const val FOREMAN = 1938
    const val BARGE_WORKMAN = 1937
    const val ED_WOOD = 1939

    /** `pilot_digsite`: Talk-to is his op 3. */
    const val CAPTAIN_ERRDO = 6088
    const val ERRDO_TALK = 3

    /** `vm_museum_guard2`, west of the workers' gate. */
    const val GATE_GUARD = 1911
    const val GATE_LEFT = 24560
    const val GATE_RIGHT = 24561
    const val GATE_X = 3296
    const val GATE_Z = 3428

    /** Where boarding and leaving put the player (guessed: beside each guard). */
    const val ON_BARGE_X = 3362
    const val ON_BARGE_Z = 3452
    const val ON_QUAY_X = 3362
    const val ON_QUAY_Z = 3445

    /** The signposts, training to private, and what each reads. */
    val SIGNS: Map<Int, Line> = mapOf(
        2366 to DigsiteSignLines.TRAINING,
        2367 to DigsiteSignLines.LEVEL_1,
        2368 to DigsiteSignLines.LEVEL_2,
        2369 to DigsiteSignLines.LEVEL_3,
        2370 to DigsiteSignLines.PRIVATE,
    )
    const val READ = 1

    internal suspend fun Dialogue.workman() = with(DigsitePeopleLines) {
        chatPlayer(ChatAnim.NEUTRAL, HELLO)
        chatNpc(ChatAnim.HAPPY, GREAT_ARCHAEOLOGIST)
    }

    internal suspend fun Dialogue.student(id: Int) = with(DigsitePeopleLines) {
        when (id) {
            STUDENT_SKULL -> chatNpc(ChatAnim.HAPPY, FAMOUS)
            STUDENT_CUP -> chatNpc(ChatAnim.HAPPY, GREAT_EXPLORER)
            else -> {
                chatPlayer(ChatAnim.NEUTRAL, HELLO)
                chatNpc(ChatAnim.HAPPY, THANKS_AGAIN)
            }
        }
    }

    internal suspend fun Dialogue.elissa() = with(ElissaLines) {
        chatNpc(ChatAnim.NEUTRAL, HELLO)
        if (choice(WHAT_DO_YOU_DO, WHAT_PLACE) == 1) {
            chatPlayer(ChatAnim.QUIZ, WHAT_DO_YOU_DO)
            chatNpc(ChatAnim.NEUTRAL, ARCHITECTURE)
            return
        }
        chatPlayer(ChatAnim.QUIZ, WHAT_PLACE)
        chatNpc(ChatAnim.HAPPY, GREAT_CITY)
        if (choice(VARROCK, WHAT_HAPPENED) == 1) {
            chatPlayer(ChatAnim.NEUTRAL, VARROCK_SAID)
            chatNpc(ChatAnim.ANGRY, BURIED)
            return
        }
        chatPlayer(ChatAnim.QUIZ, WHAT_HAPPENED)
        chatNpc(ChatAnim.NEUTRAL, NO_ONE_KNOWS)
        chatNpc(ChatAnim.NEUTRAL, DESTRUCTION)
        chatNpc(ChatAnim.SAD, CIVILIZATIONS)
    }

    internal suspend fun Dialogue.panningGuide() = with(PanningGuideLines) {
        chatPlayer(ChatAnim.QUIZ, WHO)
        chatNpc(ChatAnim.HAPPY, I_AM)
        chatPlayer(ChatAnim.HAPPY, EXCELLENT)
        chatNpc(ChatAnim.NEUTRAL, HOW_IT_WORKS)
        chatNpc(ChatAnim.NEUTRAL, GOLD)
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

    /** The canal guard's talk; true when the player asked to board. */
    internal suspend fun Dialogue.canalGuard(): Boolean = with(BargeLines) {
        chatPlayer(ChatAnim.NEUTRAL, HELLO)
        chatNpc(ChatAnim.NEUTRAL, CAN_I_HELP)
        when (choice(BOARD, FOSSIL_ISLAND, NO_THANKS)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, BOARD)
                chatNpc(ChatAnim.HAPPY, ABSOLUTELY_BOARD)
                return true
            }
            2 -> mesbox(FOSSIL_LATER)
            else -> chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
        }
        false
    }

    /** The barge guard's talk; true when the player asked to get off. */
    internal suspend fun Dialogue.bargeGuard(): Boolean = with(BargeLines) {
        chatPlayer(ChatAnim.NEUTRAL, HELLO)
        chatNpc(ChatAnim.NEUTRAL, CAN_I_HELP)
        if (choice(GET_OFF, NO_THANKS) == 1) {
            chatPlayer(ChatAnim.QUIZ, GET_OFF)
            chatNpc(ChatAnim.HAPPY, ABSOLUTELY_OFF)
            return true
        }
        chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
        false
    }

    internal suspend fun Dialogue.leadNavigator() = with(BargeLines) {
        chatPlayer(ChatAnim.NEUTRAL, HELLO)
        chatNpc(ChatAnim.QUIZ, TRAVEL_FOSSIL)
        if (choice(YES_PLEASE, NO_THANKS, title = text(TRAVEL_TITLE)) == 1) {
            mesbox(FOSSIL_LATER)
            return
        }
        chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
    }

    internal suspend fun Dialogue.juniorNavigator() = with(BargeLines) {
        chatPlayer(ChatAnim.NEUTRAL, HELLO)
        chatNpc(ChatAnim.NEUTRAL, LEAD_NAVIGATOR)
    }

    internal suspend fun Dialogue.errdo() = with(BargeLines) {
        chatNpc(ChatAnim.SAD, EMBARRASSING)
        chatPlayer(ChatAnim.QUIZ, WHAT_HAPPENED)
        chatNpc(ChatAnim.SAD, LANDING_GEAR)
    }

    internal suspend fun Dialogue.gateGuard() = with(BargeLines) {
        chatNpc(ChatAnim.NEUTRAL, WORKMANS_GATE)
        mesbox(NEED_KUDOS)
    }

    fun ScriptContext.board(world: World) {
        world.message(player, BargeLines.HELPS_BOARD)
        world.teleport(player, ON_BARGE_X, ON_BARGE_Z, 1)
    }

    fun ScriptContext.leave(world: World) {
        world.message(player, BargeLines.HELPS_OFF)
        world.teleport(player, ON_QUAY_X, ON_QUAY_Z, 0)
    }

    /** The workers' gate's guard, if he's in play near it. */
    fun gateGuard(world: World): Npc? = world.npcs.inPlay.firstOrNull {
        it.spawn.type.id == GATE_GUARD && !it.outOfPlay && it.level == 0 && maxOf(kotlin.math.abs(it.x - GATE_X), kotlin.math.abs(it.z - GATE_Z)) <= 8
    }
}

/** The Digsite's people, the canal barge, Captain Errdo, the workers' gate and the signposts. */
fun registerDigsite(world: World) {
    with(Digsite) {
        for (workman in WORKMEN) world.handlers.onNpc(workman, TALK_TO) { op -> world.dialogues.start(this, op.npc) { workman() } }
        for (student in listOf(STUDENT_SKULL, STUDENT_CUP, STUDENT_TEDDY)) {
            world.handlers.onNpc(student, TALK_TO) { op -> world.dialogues.start(this, op.npc) { student(student) } }
        }
        world.handlers.onNpc(ELISSA, TALK_TO) { op -> world.dialogues.start(this, op.npc) { elissa() } }
        world.handlers.onNpc(PANNING_GUIDE, TALK_TO) { op -> world.dialogues.start(this, op.npc) { panningGuide() } }

        world.handlers.onNpc(CANAL_GUARD, TALK_TO) { op ->
            var aboard = false
            world.dialogues.start(this, op.npc) { aboard = canalGuard() }
            if (aboard) board(world)
        }
        world.handlers.onNpc(CANAL_GUARD, EMBARK) { board(world) }
        world.handlers.onNpc(CANAL_GUARD, QUICK_TRAVEL) { world.message(player, BargeLines.FOSSIL_LATER) }
        world.handlers.onNpc(BARGE_GUARD, TALK_TO) { op ->
            var off = false
            world.dialogues.start(this, op.npc) { off = bargeGuard() }
            if (off) leave(world)
        }
        world.handlers.onNpc(BARGE_GUARD, DISEMBARK) { leave(world) }
        world.handlers.onNpc(LEAD_NAVIGATOR, TALK_TO) { op -> world.dialogues.start(this, op.npc) { leadNavigator() } }
        world.handlers.onNpc(LEAD_NAVIGATOR, TRAVEL) { world.message(player, BargeLines.FOSSIL_LATER) }
        world.handlers.onNpc(JUNIOR_NAVIGATOR, TALK_TO) { op -> world.dialogues.start(this, op.npc) { juniorNavigator() } }
        world.handlers.onNpc(JUNIOR_NAVIGATOR, TRAVEL) { op ->
            world.dialogues.start(this, op.npc) { chatNpc(ChatAnim.NEUTRAL, BargeLines.LEAD_NAVIGATOR_TRAVEL) }
        }
        world.handlers.onNpc(FOREMAN, TALK_TO) { op ->
            world.dialogues.start(this, op.npc) { chatNpc(ChatAnim.HAPPY, BargeLines.FOREMAN_THANKS, "name" to player.username) }
        }
        for (busy in listOf(BARGE_WORKMAN, ED_WOOD)) {
            world.handlers.onNpc(busy, TALK_TO) { op ->
                val npc = op.npc ?: return@onNpc
                npc.effects.say(world.textFor(player)[BargeLines.BUSY[world.random.nextInt(BargeLines.BUSY.size)]])
            }
        }
        world.handlers.onNpc(CAPTAIN_ERRDO, ERRDO_TALK) { op -> world.dialogues.start(this, op.npc) { errdo() } }

        world.handlers.onNpc(GATE_GUARD, TALK_TO) { op -> world.dialogues.start(this, op.npc) { gateGuard() } }
        for (gate in listOf(GATE_LEFT, GATE_RIGHT)) {
            world.handlers.onLoc(gate, 1) {
                val guard = gateGuard(world) ?: return@onLoc world.dialogues.start(this) { mesbox(BargeLines.NEED_KUDOS) }
                guard.effects.say(world.textFor(player)[BargeLines.WORKMANS_GATE_ONLY])
                world.dialogues.start(this, guard) { gateGuard() }
            }
        }
        for ((sign, line) in SIGNS) world.handlers.onLoc(sign, READ) { world.message(player, line) }
    }
}
