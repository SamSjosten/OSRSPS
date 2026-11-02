package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile

/** The Guildmaster's lines, from the Wiki's Transcript:Guildmaster, read 2026-11-02. */
object GuildmasterLines : Lines("guildmaster", "Transcript:Guildmaster") {
    val GREETING = line("greeting", "his first line, before the menu")
    val WHAT_PLACE = line("what_place", "the menu's option (no line from the player)")
    val WHAT_PLACE_ANSWER = line("what_place_answer", "his answer: what the guild is")
    val WELCOME = line("welcome", "at the guild's door, as a player with enough quest points comes in")
}

/** Scavvo's lines, from the Wiki's Transcript:Scavvo, read 2026-11-02. */
object ScavvoLines : Lines("scavvo", "Transcript:Scavvo") {
    val GREETING = line("greeting", "his first line, before the menu")
    val NO_TOYS = line("no_toys", "option 1, which the player then says")
    val LETS_SEE = line("lets_see", "option 2: the shop opens, with no line from the player")
    val GOODY = line("goody", "option 3")
    val GOODY_SAID = line("goody_said", "option 3: what the player says before the shop opens (not the option's words)")
    val WHY = line("why", "option 4, which the player then says")
    val WHY_ANSWER = line("why_answer", "option 4: his answer")
}

/** Valaine's lines, from the Wiki's Transcript:Valaine, read 2026-11-02. */
object ValaineLines : Lines("valaine", "Transcript:Valaine") {
    val GREETING = line("greeting", "her first line, before the menu")
    val YES = line("yes", "option 1: the shop opens, with no line from the player")
    val NO = line("no", "option 2, which the player then says")
}

/**
 * The Champions' Guild (the Varrock piece's step 10): its door, the Guildmaster, Scavvo's and Valaine's shops (Trade is
 * [registerShops]'s, as for every shop) and the trapdoor to the basement. Every account has every quest done, so every
 * player has the 32 quest points the door asks for. From the Wiki's transcripts and the 241 cache. Guessed: every
 * expression; that the Guildmaster greets only those coming in, not those leaving; that the menu with one option shows
 * as a menu; where the trapdoor and the basement ladder put the player. Left out: the Guildmaster's Champion's scroll
 * and maze key options (members' and quest content), and the basement's Champions' Challenge (the portcullis, its ladder
 * and the Champion Statue).
 */
object VarrockChampionsGuild {
    const val GUILDMASTER = 814
    const val SCAVVO = 2871
    const val VALAINE = 2870
    const val TALK_TO = 1

    /** championdoor, on the south edge of its tile: the guild is the tile south of it. */
    const val DOOR = 1805
    const val DOOR_OPEN = 23637
    const val OPEN = 1
    const val DOOR_X = 3191
    const val DOOR_Z = 3363

    /** How long the door stays open behind the player, as the Draynor Manor doors that let one through. */
    const val DOOR_TICKS = 3

    /** The trapdoor by the stairs (Open) and its open form (Climb-down, Close). */
    const val TRAPDOOR = 10558
    const val TRAPDOOR_OPEN = 10559
    const val CLIMB_DOWN = 1
    const val CLOSE = 2

    const val TRAPDOOR_X = 3190
    const val TRAPDOOR_Z = 3355

    /** The basement's ladder under the trapdoor (Climb-up). */
    const val LADDER = 10560
    const val CLIMB_UP = 1
    const val LADDER_X = 3190
    const val LADDER_Z = 9758

    /** Where the trapdoor puts the player: beside the ladder below it (guessed). */
    val BASEMENT = Destination(3189, 9758, 0)

    /** Where the ladder puts the player: beside the trapdoor, north of it (guessed). */
    val GROUND = Destination(3190, 3356, 0)

    /** Whether a player at [z] stands inside the guild, south of its door. */
    fun inside(z: Int): Boolean = z < DOOR_Z

    internal suspend fun Dialogue.guildmaster() = with(GuildmasterLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        choice(WHAT_PLACE)
        chatNpc(ChatAnim.NEUTRAL, WHAT_PLACE_ANSWER)
    }

    /** Scavvo's talk; true when the shop should open. */
    internal suspend fun Dialogue.scavvo(): Boolean = with(ScavvoLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        when (choice(NO_TOYS, LETS_SEE, GOODY, WHY)) {
            1 -> {
                chatPlayer(ChatAnim.NEUTRAL, NO_TOYS)
                false
            }
            2 -> true
            3 -> {
                chatPlayer(ChatAnim.HAPPY, GOODY_SAID)
                true
            }
            else -> {
                chatPlayer(ChatAnim.QUIZ, WHY)
                chatNpc(ChatAnim.HAPPY, WHY_ANSWER)
                false
            }
        }
    }

    /** Valaine's talk; true when the shop should open. */
    internal suspend fun Dialogue.valaine(): Boolean = with(ValaineLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        if (choice(YES, NO) == 1) return@with true
        chatPlayer(ChatAnim.NEUTRAL, NO)
        false
    }

    /**
     * Through the door from whichever side the player stands: the door swings open for [DOOR_TICKS] and the player walks
     * onto the tile beyond it. Coming in, the Guildmaster (when he's by the door) greets them first.
     */
    suspend fun ScriptContext.throughDoor(world: World, door: ZoneLoc) {
        arriveDelay()
        val comingIn = !inside(player.z)
        if (comingIn) {
            val guildmaster = world.npcs.all.firstOrNull {
                it.type.id == GUILDMASTER && it.level == 0 && it.x in DOOR_X - 6..DOOR_X + 6 && it.z in DOOR_Z - 8..DOOR_Z
            }
            if (guildmaster != null) world.dialogues.start(this, guildmaster) { chatNpc(ChatAnim.HAPPY, GuildmasterLines.WELCOME) }
        }
        if (!world.locs.exists(door)) return
        world.doors.openAs(door, DOOR_OPEN, shutAfter = DOOR_TICKS)
        player.waypoints.clear()
        player.waypoints.add(Tile(DOOR_X, if (comingIn) DOOR_Z - 1 else DOOR_Z))
    }
}

/** The Champions' Guild: its door, trapdoor and basement ladder, and its three NPCs' Talk-to. */
fun registerVarrockChampionsGuild(world: World) {
    with(VarrockChampionsGuild) {
        // Its open form is the guild's own: closing it by hand turns it back into this door.
        world.doors.addClosedType(DOOR, DOOR_OPEN)
        world.handlers.onLoc(DOOR, OPEN) { op ->
            val door = op.loc!!
            if (door.x != DOOR_X || door.z != DOOR_Z) return@onLoc
            throughDoor(world, door)
        }
        world.handlers.onLoc(TRAPDOOR, OPEN) { op -> swapFurniture(world, op.loc!!, TRAPDOOR_OPEN, Searching.ANIM_OPEN) }
        world.handlers.onLoc(TRAPDOOR_OPEN, CLOSE) { op -> swapFurniture(world, op.loc!!, TRAPDOOR, Searching.ANIM_SHUT) }
        // Both types may stand elsewhere one day: only this trapdoor and this ladder lead between the guild and its basement.
        world.handlers.onLoc(TRAPDOOR_OPEN, CLIMB_DOWN) { op ->
            if (op.loc!!.x != TRAPDOOR_X || op.loc!!.z != TRAPDOOR_Z) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            arriveDelay()
            player.anim(Climbing.ANIM_PICKUP_FLOOR)
            delay(1)
            world.teleport(player, BASEMENT.x, BASEMENT.z, BASEMENT.level)
        }
        world.handlers.onLoc(LADDER, CLIMB_UP) { op ->
            if (op.loc!!.x != LADDER_X || op.loc!!.z != LADDER_Z) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            arriveDelay()
            player.anim(Climbing.ANIM_REACH_FOR_LADDER)
            delay(1)
            world.teleport(player, GROUND.x, GROUND.z, GROUND.level)
        }
        world.handlers.onNpc(GUILDMASTER, TALK_TO) { op -> world.dialogues.start(this, op.npc) { guildmaster() } }
        val talks: List<Pair<Int, suspend Dialogue.() -> Boolean>> = listOf(SCAVVO to { scavvo() }, VALAINE to { valaine() })
        for ((id, talk) in talks) {
            world.handlers.onNpc(id, TALK_TO) { op ->
                var trade = false
                world.dialogues.start(this, op.npc) { trade = talk() }
                if (trade) world.shopTypes.ofNpc(id)?.let { world.shopScreen.open(player, it) }
            }
        }
    }
}
