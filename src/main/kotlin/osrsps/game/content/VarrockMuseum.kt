package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** Curator Haig Halen's lines, from the Wiki's Transcript:Curator Haig Halen (standard dialogue, after Bone Voyage), read 2026-11-02. */
object CuratorLines : Lines("curator", "Transcript:Curator Haig Halen") {
    val GREETING = line("greeting", "his first line, before the menu")
    val FOSSIL_ISLAND = line("fossil_island", "option 1 (after Bone Voyage)")
    val FOSSIL_ISLAND_SAID = line("fossil_island_said", "option 1: what the player says")
    val FOSSIL_ISLAND_ANSWER = line("fossil_island_answer", "option 1: his answer")
    val FOSSILS_FOR = line("fossils_for", "option 1's first option, which the player then says")
    val FOSSILS_FOR_ANSWER = line("fossils_for_answer", "option 1's first option: his answer")
    val DROP_TOPIC = line("drop_topic", "option 1's second option, which the player then says")
    val NEWS = line("news", "option 2, which the player then says")
    val NEWS_1 = line("news_1", "option 2 after Bone Voyage: his answer, the first of two boxes")
    val NEWS_2 = line("news_2", "option 2 after Bone Voyage: the second box")
    val TREASURE = line("treasure", "option 3, which the player then says")
    val TREASURE_1 = line("treasure_1", "option 3: his answer")
    val TREASURE_2 = line("treasure_2", "option 3: the player's reply")
    val TREASURE_3 = line("treasure_3", "option 3: his last line")
    val MEDALLION = line("medallion", "option 4, the medallion")
    val NO_PENDANT = line("no_pendant", "option 4 before the player has found a digsite pendant: his only line")
}

/** The information clerk's lines, from the Wiki's Transcript:Information clerk (no rewards waiting), read 2026-11-02. */
object MuseumClerkLines : Lines("museum_clerk", "Transcript:Information clerk") {
    val WELCOME = line("welcome", "her welcome, before the topic menu")
    val MAP = line("map", "topic option 1: take a museum map")
    val MAP_TAKEN = line("map_taken", "option 1: the box as the player takes the map")
    val DIG_SITE = line("dig_site", "topic option 2")
    val DIG_SITE_SAID = line("dig_site_said", "option 2: what the player says")
    val DIG_SITE_ABOUT = line("dig_site_about", "option 2: her first line")
    val DIG_SITE_CLEAN_1 = line("dig_site_clean_1", "option 2, The Dig Site done but not helped the exhibit: the first of two boxes")
    val DIG_SITE_CLEAN_2 = line("dig_site_clean_2", "option 2, the same: the second box")
    val TIMELINE = line("timeline", "topic option 3")
    val TIMELINE_SAID = line("timeline_said", "option 3: what the player says")
    val TIMELINE_ABOUT = line("timeline_about", "option 3: her first line")
    val TIMELINE_HELP_1 = line("timeline_help_1", "option 3, not yet contributed: the first of two boxes")
    val TIMELINE_HELP_2 = line("timeline_help_2", "option 3, not yet contributed: the second box")
    val NUMBERS = line("numbers", "option 3: the player's thanks and question about the display numbers")
    val NUMBERS_1 = line("numbers_1", "option 3: her answer, the first of two boxes")
    val NUMBERS_2 = line("numbers_2", "option 3: the second box")
    val NUMBERS_OK = line("numbers_ok", "option 3: the player's answer")
    val NATURAL_HISTORY = line("natural_history", "topic option 4")
    val NATURAL_HISTORY_SAID = line("natural_history_said", "option 4: what the player says")
    val NATURAL_HISTORY_ABOUT = line("natural_history_about", "option 4: her first line")
    val ORLANDO = line("orlando", "option 4, no display corrected: her second line (Orlando's quizzes)")
    val LIVING = line("living", "option 4's first option")
    val LIVING_SAID = line("living_said", "option 4's first option: what the player says")
    val LIVING_ANSWER = line("living_answer", "option 4's first option: her answer")
    val KUDOS = line("kudos", "topic option 5")
    val KUDOS_SAID = line("kudos_said", "option 5: what the player says")
    val KUDOS_ABOUT = line("kudos_about", "option 5: her answer")
    val KUDOS_FOR = line("kudos_for", "option 5: the player asks what it's for")
    val KUDOS_FOR_1 = line("kudos_for_1", "option 5: her answer, the first of two boxes")
    val KUDOS_FOR_2 = line("kudos_for_2", "option 5: the second box")
    val QUALIFY = line("qualify", "option 5: the player asks if they qualify")
    val NOT_QUALIFIED = line("not_qualified", "option 5, not enough Kudos: her answer")
    val KUDOS_THANKS = line("kudos_thanks", "option 5, not enough Kudos: the player's answer")
    val SOMETHING_ELSE = line("something_else", "the follow-up menu's option 1, back to the topics (no speech)")
    val BYE = line("bye", "the follow-up menu's option 2")
    val BYE_SAID = line("bye_said", "option 2: what the player says")
    val GOODBYE = line("goodbye", "after bye: her farewell")
}

/** The museum guards' lines, from the Wiki's Transcript:Museum guard (after The Dig Site), read 2026-11-02. */
object MuseumGuardLines : Lines("museum_guard", "Transcript:Museum guard") {
    val GATE_ASK = line("gate_ask", "the museum gate (cleaning area): his question")
    val GATE_IN = line("gate_in", "the gate's option 1: go in (no speech)")
    val GATE_OUTSIDE = line("gate_outside", "the gate's option 2, which the player then says")
    val DOORS = (1..6).map { line("doors_$it", "the museum doors (back entrance), box $it of the six, the guard's and the player's in turn") }
}

/**
 * The Varrock Museum (the Varrock piece's step 9a): the building, its staff by the entrance, and every museum loc's
 * examine key (examines.json). The ground floor's straight stairs and the ones between the upper floors are
 * Varrock's own ([Varrock.straightStairs]); the stairs down to the Natural History floor (`vm_basement_wall_stairs_down`
 * 24428 at 3255, 3451) and back up (`vm_basement_wall_stairs_up` 24427 at 1758, 4959) teleport, the back entrance's
 * doors (`fai_varrock_museum_door_closed_l`/`_r` 24565/24567) open as a pair, and the cleaning area's gate
 * (`vm_barrier_guard_gate` 24536) lets the player in through its guard.
 *
 * Every quest counts complete (Bone Voyage and The Dig Site included) and nobody has Kudos, so the curator, the clerk
 * and the guards say what they say to such a player. From the Wiki's transcripts; the stairs' landing tiles and every
 * expression are guessed (each stair puts the player on the tile in front of the other one, from the 241 map's walls).
 * Also guessed: the gate guard asks every time (the Wiki has him ask on the first talk after login); the player walks
 * out of the cleaning area through the gate freely. Left out: the displays' Study (their interfaces), the cleaning
 * activity, Kudos and the clerk's rewards, the curator's relics, certificates and medallion trade, and his Pickpocket.
 */
object VarrockMuseum {
    const val CURATOR = 5214
    const val CLERK = 1909
    const val GATE_GUARD = 1910
    const val DOOR_GUARD = 1912
    const val TALK_TO = 1

    const val MUSEUM_MAP = 11184

    const val STAIRS_DOWN = 24428
    const val STAIRS_UP = 24427
    const val WALK = 1

    /** Where the stairs put a player: in front of the basement's stairs up, and of the ground floor's stairs down. */
    val BASEMENT_LANDING = Destination(1759, 4958, 0)
    val GROUND_LANDING = Destination(3254, 3452, 0)

    /** The ground floor's stairs down, so the basement's own copy of that loc (1758, 4963) isn't taken for them. */
    const val GROUND_STAIRS_Z = 3451

    const val DOOR_LEFT = 24565
    const val DOOR_LEFT_OPEN = 24566
    const val DOOR_RIGHT = 24567
    const val DOOR_RIGHT_OPEN = 24568

    const val GATE = 24536
    const val OPEN = 1

    /** The gate is on the north edge of (3261, 3446): the cleaning area to the south, the hall to the north. */
    const val GATE_X = 3261
    const val GATE_Z = 3446

    internal suspend fun Dialogue.curator() = with(CuratorLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        when (choice(FOSSIL_ISLAND, NEWS, TREASURE, MEDALLION)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, FOSSIL_ISLAND_SAID)
                chatNpc(ChatAnim.HAPPY, FOSSIL_ISLAND_ANSWER)
                if (choice(FOSSILS_FOR, DROP_TOPIC) == 1) {
                    chatPlayer(ChatAnim.QUIZ, FOSSILS_FOR)
                    chatNpc(ChatAnim.NEUTRAL, FOSSILS_FOR_ANSWER)
                } else {
                    chatPlayer(ChatAnim.NEUTRAL, DROP_TOPIC)
                }
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, NEWS)
                chatNpc(ChatAnim.HAPPY, NEWS_1)
                chatNpc(ChatAnim.HAPPY, NEWS_2)
            }
            3 -> {
                chatPlayer(ChatAnim.QUIZ, TREASURE)
                chatNpc(ChatAnim.NEUTRAL, TREASURE_1)
                chatPlayer(ChatAnim.QUIZ, TREASURE_2)
                chatNpc(ChatAnim.NEUTRAL, TREASURE_3)
            }
            else -> chatNpc(ChatAnim.NEUTRAL, NO_PENDANT)
        }
    }

    /** The clerk's talk; [giveMap] hands the player a museum map. */
    internal suspend fun Dialogue.clerk(giveMap: () -> Unit) = with(MuseumClerkLines) {
        chatNpc(ChatAnim.HAPPY, WELCOME)
        while (true) {
            when (choice(MAP, DIG_SITE, TIMELINE, NATURAL_HISTORY, KUDOS)) {
                1 -> {
                    giveMap()
                    mesbox(MAP_TAKEN)
                    return@with
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, DIG_SITE_SAID)
                    chatNpc(ChatAnim.HAPPY, DIG_SITE_ABOUT)
                    chatNpc(ChatAnim.HAPPY, DIG_SITE_CLEAN_1)
                    chatNpc(ChatAnim.HAPPY, DIG_SITE_CLEAN_2)
                }
                3 -> {
                    chatPlayer(ChatAnim.QUIZ, TIMELINE_SAID)
                    chatNpc(ChatAnim.HAPPY, TIMELINE_ABOUT)
                    chatNpc(ChatAnim.HAPPY, TIMELINE_HELP_1)
                    chatNpc(ChatAnim.HAPPY, TIMELINE_HELP_2)
                    chatPlayer(ChatAnim.QUIZ, NUMBERS)
                    chatNpc(ChatAnim.NEUTRAL, NUMBERS_1)
                    chatNpc(ChatAnim.NEUTRAL, NUMBERS_2)
                    chatPlayer(ChatAnim.HAPPY, NUMBERS_OK)
                }
                4 -> {
                    chatPlayer(ChatAnim.QUIZ, NATURAL_HISTORY_SAID)
                    chatNpc(ChatAnim.HAPPY, NATURAL_HISTORY_ABOUT)
                    chatNpc(ChatAnim.HAPPY, ORLANDO)
                    var pick = choice(LIVING, SOMETHING_ELSE, BYE)
                    if (pick == 1) {
                        chatPlayer(ChatAnim.QUIZ, LIVING_SAID)
                        chatNpc(ChatAnim.NEUTRAL, LIVING_ANSWER)
                        pick = choice(SOMETHING_ELSE, BYE) + 1
                    }
                    if (pick == 2) continue
                    goodbye()
                    return@with
                }
                else -> {
                    chatPlayer(ChatAnim.QUIZ, KUDOS_SAID)
                    chatNpc(ChatAnim.NEUTRAL, KUDOS_ABOUT)
                    chatPlayer(ChatAnim.QUIZ, KUDOS_FOR)
                    chatNpc(ChatAnim.NEUTRAL, KUDOS_FOR_1)
                    chatNpc(ChatAnim.NEUTRAL, KUDOS_FOR_2)
                    chatPlayer(ChatAnim.QUIZ, QUALIFY)
                    chatNpc(ChatAnim.NEUTRAL, NOT_QUALIFIED)
                    chatPlayer(ChatAnim.NEUTRAL, KUDOS_THANKS)
                }
            }
            if (choice(SOMETHING_ELSE, BYE) == 1) continue
            goodbye()
            return@with
        }
    }

    private suspend fun Dialogue.goodbye() = with(MuseumClerkLines) {
        chatPlayer(ChatAnim.NEUTRAL, BYE_SAID)
        chatNpc(ChatAnim.HAPPY, GOODBYE)
    }

    internal suspend fun Dialogue.doorGuard() {
        for ((i, line) in MuseumGuardLines.DOORS.withIndex()) {
            if (i % 2 == 0) chatNpc(ChatAnim.NEUTRAL, line) else chatPlayer(ChatAnim.NEUTRAL, line)
        }
    }

    /** The gate guard's question; true when the player chose to go in. */
    internal suspend fun Dialogue.gateGuard(): Boolean = with(MuseumGuardLines) {
        chatNpc(ChatAnim.NEUTRAL, GATE_ASK)
        if (choice(GATE_IN, GATE_OUTSIDE) == 1) return@with true
        chatPlayer(ChatAnim.NEUTRAL, GATE_OUTSIDE)
        false
    }

    /** Takes the player through the gate from whichever side they're on, stepping up to it first. */
    suspend fun ScriptContext.throughGate(world: World) {
        val inside = player.z <= GATE_Z
        val front = if (inside) GATE_Z else GATE_Z + 1
        if (player.x != GATE_X || player.z != front) {
            world.teleport(player, GATE_X, front, 0)
            delay(1)
        }
        world.teleport(player, GATE_X, if (inside) GATE_Z + 1 else GATE_Z, 0)
    }
}

/** The museum's stairs, doors, gate and staff. */
fun registerVarrockMuseum(world: World) {
    with(VarrockMuseum) {
        // Jagex's _l and _r are as seen from outside (the south): seen along the wall's angle, the _r leaf (3264) is
        // the left one, so the two swing apart into the building.
        world.doors.registerDouble(DOOR_RIGHT, DOOR_RIGHT_OPEN, DOOR_LEFT, DOOR_LEFT_OPEN)
        world.handlers.onLoc(STAIRS_DOWN, WALK) { op ->
            if (op.loc!!.z != GROUND_STAIRS_Z) return@onLoc
            arriveDelay()
            world.teleport(player, BASEMENT_LANDING.x, BASEMENT_LANDING.z, BASEMENT_LANDING.level)
        }
        world.handlers.onLoc(STAIRS_UP, WALK) {
            arriveDelay()
            world.teleport(player, GROUND_LANDING.x, GROUND_LANDING.z, GROUND_LANDING.level)
        }
        world.handlers.onLoc(GATE, OPEN) {
            if (player.z <= GATE_Z) {
                throughGate(world)
                return@onLoc
            }
            val guard = world.npcs.all.firstOrNull { it.type.id == GATE_GUARD && it.level == 0 && it.x in GATE_X - 4..GATE_X + 4 && it.z in GATE_Z - 4..GATE_Z + 4 }
            var goIn = guard == null
            if (guard != null) world.dialogues.start(this, guard) { goIn = gateGuard() }
            if (goIn) throughGate(world)
        }
        world.handlers.onNpc(GATE_GUARD, TALK_TO) { op ->
            var goIn = false
            world.dialogues.start(this, op.npc) { goIn = gateGuard() }
            if (goIn) throughGate(world)
        }
        world.handlers.onNpc(DOOR_GUARD, TALK_TO) { op -> world.dialogues.start(this, op.npc) { doorGuard() } }
        world.handlers.onNpc(CURATOR, TALK_TO) { op -> world.dialogues.start(this, op.npc) { curator() } }
        world.handlers.onNpc(CLERK, TALK_TO) { op ->
            world.dialogues.start(this, op.npc) {
                clerk { world.objTypes[MUSEUM_MAP]?.let { world.groundObjs.give(player, it, 1) } }
            }
        }
    }
}
