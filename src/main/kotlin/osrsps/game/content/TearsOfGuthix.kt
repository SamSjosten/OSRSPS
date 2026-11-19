package osrsps.game.content

import java.time.LocalDate
import java.time.ZoneOffset
import osrsps.game.Player
import osrsps.game.Quests
import osrsps.game.Skill
import osrsps.game.Stance
import osrsps.game.WearPos
import osrsps.game.bank.getVarbitIfKnown
import osrsps.game.bank.setVarbitIfKnown
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/** Juna's words (Transcript:Juna). */
object JunaLines : Lines("juna", "Transcript:Juna, read 2026-11-19") {
    val TELL_ME = line("tell_me", "Talk-to")
    val OKAY = line("okay", "option and player")
    val YOU_TELL = line("you_tell", "option and player: You tell me a story.")
    val NOT_NOW = line("not_now", "option and player")
    val EMPTY_HANDS = line("empty_hands", "a weapon or shield worn")
    val STORIES = line("stories", "the box as the player tells her stories")
    val POOR_SORT = line("poor_sort", "Talk-to: not 1 quest point or 100,000 XP since the last visit")
    val NO_NEW_STORIES = line("no_new_stories", "Story: not enough since the last visit, 7 days or more ago")
    val NO_NEW_STORIES_TOMORROW = line("no_new_stories_tomorrow", "Story: not enough since the last visit, which was 6 days ago")
    val NO_NEW_STORIES_DAYS = line("no_new_stories_days", "Story: not enough since the last visit, under 6 days ago", "days")
    val NEED_MORE = line("need_more", "the box after either refusal", "xp")
    val NOT_LONG_TOMORROW = line("not_long_tomorrow", "enough since the last visit, which was 6 days ago")
    val NOT_LONG_DAYS = line("not_long_days", "enough since the last visit, under 6 days ago", "days")
    val ENTERTAINED = line("entertained", "eligible")
    val COLLECT = line("collect", "eligible, then into the cave")
    val ABOUT_TEARS = line("about_tears", "option and player: the first story again")
    val ABOUT_LIGHT = line("about_light", "option and player: the second story again")
    val ABOUT_GOBLINS = line("about_goblins", "option and player: the third story again")
    val NEW_STORY = line("new_story", "option: the next story")

    /** Her three stories, page by page: the Tears, the light-creatures and the cave goblins. */
    val TEARS_STORY: List<Line> = (1..9).map { line("tears_$it", "the story of the Tears, page $it") }
    val LIGHT_STORY: List<Line> = (1..7).map { line("light_$it", "the story of the light-creatures, page $it") }
    val GOBLIN_STORY: List<Line> = (1..8).map { line("goblins_$it", "the story of the cave goblins, page $it") }
}

/** The weeping walls' and the drink's messages (Tears of Guthix (minigame)). */
object TearsLines : Lines("tears_of_guthix", "Tears of Guthix (minigame), read 2026-11-19") {
    /** The message for the XP's skill, by skill (the Wiki's table). */
    val SKILL: Map<Skill, Line> = Skill.entries.associateWith { line("skill_${it.name.lowercase()}", "the drink's XP in ${it.displayName}") }
    val NOT_PLAYING = line("not_playing", "Collect-from without Juna's bowl; our own line")
    val NOTHING = line("nothing", "Mine on the magical rocks after the quest; our own line")
}

/**
 * The Chasm of Tears and the Tears of Guthix minigame (post-quest step 10; every account has finished Tears of
 * Guthix):
 *
 * - **The tunnels**: the Lumbridge Swamp Caves' tunnel ([TUNNEL_DOWN], 6659) leads to the chasm's west ledge, and the
 *   ledge's ([TUNNEL_UP], 6658) back.
 * - **The climbing rocks** ([ROCKS_UP], [ROCKS_DOWN]) take the player across the rock slopes between the ledges.
 * - **The magical rocks** ([MAGICAL_ROCKS]) give nothing after the quest (the Wiki: magic stones only during it).
 * - **Juna** ([JUNA], the map's multiloc on varbit 451, `tog_juna_bowl`, 2 after the quest showing Talk-to and Story)
 *   tells her three stories in turn (varbit 452, `tog_juna_stories`) and lets a player in once every 7 days if they have
 *   gained 1 quest point or 100,000 XP since their last drink (the first time always). Their hands must be empty.
 * - **The game**: the player holds the bowl ([BOWL_STANCE]) in the walls' room for a tick per quest point, the
 *   overlay ([OVERLAY], `tog_sidepanel`) showing the tears (varbit 455) and the time (varbit 5099). Collect-from on a
 *   weeping wall gathers a tear a tick from a blue stream and loses one from a green, until the player does anything
 *   else. Six streams (three blue, three green) move one after another, each staying 16 ticks, to a wall none of the
 *   others is on, in the world's own order of colours ([Streams]).
 * - **The drink**: the time up, the player is put back by Juna and drinks: XP per tear min(60, 10 + floor(XP / 27) /
 *   10) in the skill with the least XP (ties by the Wiki's list), its message, and the visit is recorded
 *   ([CustomVars.togVisitVarp]). Leaving early (logging out, dying) loses the tears and records nothing.
 *
 * Sources: the OSRS Wiki's Tears of Guthix (minigame), Juna, Transcript:Juna, Weeping wall, Chasm of Tears, Magical
 * rocks and Light creature pages, read 2026-11-19; ids, varbits, the overlay and the map from the 242 cache. Inferred:
 * the tunnels' and rocks' landing tiles (the map), the bowl's stance from the seq names (`tog_ready_bowl` and so on),
 * the visit recorded at the drink (the Wiki: dying first lets the player try again). Guessed: the tile the game starts
 * on and the one by Juna it ends on, the overlay in the HUD slot and its varbits' meaning, every animation's timing, the
 * streams' first places and the offsets between their moves, the tear counted on the tick after the click, Construction
 * counted as owned (every account has a house here), the 7 days counted in UTC days. Left out: the stories the player
 * tells (a random line each), the reminder toggle and its login message, the light creatures (the climbing rocks reach
 * the same ledges), the Guthixian icon and staff of balance, the hard Lumbridge diary's 10% (no diaries), the games
 * necklace teleport.
 */
class TearsOfGuthix(private val world: World) {
    /** Today as a UTC day number; tests set their own. */
    var today: () -> Long = { LocalDate.now(ZoneOffset.UTC).toEpochDay() }

    /** A stream: its colour, the wall it is on (an index of [WALLS], -1 before its first place) and its next move. */
    private class Stream(val blue: Boolean, var wall: Int = -1, var moveAt: Long = 0)

    /**
     * The six streams in the order they move, three blue and three green in the world's own order (the Wiki: each
     * world has its own sequence), and their walls' current stream locs.
     */
    private val streams: List<Stream> = (List(3) { true } + List(3) { false }).let { colours ->
        val order = colours.toMutableList()
        for (i in order.indices.reversed()) {
            val j = world.random.nextInt(i + 1)
            order[i] = order[j].also { order[j] = order[i] }
        }
        order.map { Stream(it) }
    }
    private var running = false

    /** A player's game: the ticks left and the tears in the bowl, shown in varbits 5099 and 455. */
    private class Game(var ticksLeft: Int, var tears: Int = 0)

    /** The players in the game, by save key. */
    private val playing = HashMap<String, Game>()

    /** The tears in [player]'s bowl, 0 out of the game. */
    fun tears(player: Player): Int = playing[player.saveKey]?.tears ?: 0

    fun isPlaying(player: Player): Boolean = player.saveKey in playing

    /** What the wall at [index] runs now: true blue, false green, null none. */
    fun streamAt(index: Int): Boolean? = streams.firstOrNull { it.wall == index }?.blue

    // ---- Eligibility ------------------------------------------------------------------------------------------------

    private fun totalXp(player: Player): Long = Skill.entries.sumOf { player.skills.xp(it).toLong() }

    private fun visitVarp(player: Player): Int? = player.vars.types.namedVarps[CustomVars.TOG_VISIT]
    private fun visitXpVarp(player: Player): Int? = player.vars.types.namedVarps[CustomVars.TOG_VISIT_XP]

    /** The day of [player]'s last drink, null before the first. */
    fun lastVisit(player: Player): Long? = visitVarp(player)?.let { player.vars[it] }?.takeIf { it != 0 }?.let { (it and 0xFFFF).toLong() }

    /** The XP [player] still needs before Juna lets them in again, 0 when they have had enough adventures. */
    fun xpDue(player: Player): Long {
        val varp = visitVarp(player) ?: return 0
        val packed = player.vars[varp]
        if (packed == 0) return 0
        if (player.vars[Quests.VARP_QUEST_POINTS] > packed ushr 16) return 0
        val then = (visitXpVarp(player)?.let { player.vars[it] } ?: 0).toLong() * 10
        return (then + XP_BETWEEN - totalXp(player)).coerceAtLeast(0)
    }

    /** The days until [player] may come back, 0 when they may now. */
    fun daysLeft(player: Player): Int = lastVisit(player)?.let { (it + DAYS_BETWEEN - today()).coerceAtLeast(0).toInt() } ?: 0

    private fun recordVisit(player: Player) {
        val day = today().toInt() and 0xFFFF
        visitVarp(player)?.let { player.vars[it] = day or (player.vars[Quests.VARP_QUEST_POINTS] shl 16) }
        visitXpVarp(player)?.let { player.vars[it] = (totalXp(player) / 10).toInt() }
    }

    // ---- Juna -------------------------------------------------------------------------------------------------------

    private suspend fun Dialogue.juna(anim: ChatAnim, line: Line, vararg values: Pair<String, Any>) {
        val type = world.npcTypes[JUNA_HEAD] ?: return mesbox(text(line, *values))
        chatNpc(type, type.name, anim, text(line, *values))
    }

    suspend fun ScriptContext.talk() {
        arriveDelay()
        var enter = false
        world.dialogues.start(this) {
            juna(ChatAnim.NEUTRAL, JunaLines.TELL_ME)
            when (choice(JunaLines.OKAY, JunaLines.YOU_TELL, JunaLines.NOT_NOW)) {
                1 -> {
                    chatPlayer(ChatAnim.NEUTRAL, JunaLines.OKAY)
                    enter = storyTime(viaStory = false)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, JunaLines.YOU_TELL)
                    herStory()
                }
                else -> chatPlayer(ChatAnim.NEUTRAL, JunaLines.NOT_NOW)
            }
        }
        if (enter) start(player)
    }

    suspend fun ScriptContext.story() {
        arriveDelay()
        var enter = false
        world.dialogues.start(this) { enter = storyTime(viaStory = true) }
        if (enter) start(player)
    }

    /** The player's stories and Juna's judgement; true when she lets them in. */
    private suspend fun Dialogue.storyTime(viaStory: Boolean): Boolean {
        if (player.equipment[WearPos.WEAPON] != null || player.equipment[WearPos.SHIELD] != null) {
            juna(ChatAnim.NEUTRAL, JunaLines.EMPTY_HANDS)
            return false
        }
        mesbox(JunaLines.STORIES)
        val due = xpDue(player)
        val days = daysLeft(player)
        if (due > 0) {
            when {
                !viaStory -> juna(ChatAnim.NEUTRAL, JunaLines.POOR_SORT)
                days == 0 -> juna(ChatAnim.NEUTRAL, JunaLines.NO_NEW_STORIES)
                days == 1 -> juna(ChatAnim.NEUTRAL, JunaLines.NO_NEW_STORIES_TOMORROW)
                else -> juna(ChatAnim.NEUTRAL, JunaLines.NO_NEW_STORIES_DAYS, "days" to days)
            }
            mesbox(JunaLines.NEED_MORE, "xp" to "%,d".format(due))
            return false
        }
        if (days > 0) {
            if (days == 1) juna(ChatAnim.NEUTRAL, JunaLines.NOT_LONG_TOMORROW) else juna(ChatAnim.NEUTRAL, JunaLines.NOT_LONG_DAYS, "days" to days)
            return false
        }
        juna(ChatAnim.HAPPY, JunaLines.ENTERTAINED)
        juna(ChatAnim.NEUTRAL, JunaLines.COLLECT)
        return true
    }

    /** Juna's stories: the first, then each new one in turn, the old ones to hear again. */
    private suspend fun Dialogue.herStory() {
        val heard = player.vars.getVarbitIfKnown(VARBIT_STORIES).coerceIn(0, 3)
        val stories = listOf(JunaLines.TEARS_STORY, JunaLines.LIGHT_STORY, JunaLines.GOBLIN_STORY)
        val story = if (heard == 0) {
            0
        } else {
            val again = listOf(JunaLines.ABOUT_TEARS, JunaLines.ABOUT_LIGHT, JunaLines.ABOUT_GOBLINS).take(heard)
            val options = if (heard < 3) again + JunaLines.NEW_STORY else again
            val picked = choice(*options.toTypedArray()) - 1
            if (picked < heard) chatPlayer(ChatAnim.QUIZ, again[picked])
            picked
        }
        for (page in stories[story]) juna(ChatAnim.NEUTRAL, page)
        if (story + 1 > heard) player.vars.setVarbitIfKnown(VARBIT_STORIES, story + 1)
    }

    // ---- The game ---------------------------------------------------------------------------------------------------

    /**
     * A player who logged out in the walls' room is back by Juna a tick after logging in, the game lost with the
     * logout (guessed: live puts them outside; the room has no way out but the game's end).
     */
    fun login(player: Player) {
        if (!inRoom(player.x, player.z, player.level)) return
        val key = player.saveKey
        world.npcScripts.later(1) {
            val back = world.onlinePlayers.firstOrNull { it.saveKey == key } ?: return@later
            if (!isPlaying(back) && inRoom(back.x, back.z, back.level)) world.teleport(back, END.x, END.z, END.level)
        }
    }

    /** [player] goes into the walls' room with the bowl for a tick per quest point. */
    fun start(player: Player) {
        val ticks = player.vars[Quests.VARP_QUEST_POINTS].coerceAtLeast(1)
        playing[player.saveKey] = Game(ticks)
        player.stanceOverride = BOWL_STANCE
        world.teleport(player, START.x, START.z, START.level)
        player.vars.setVarbitIfKnown(VARBIT_TEARS, 0)
        player.vars.setVarbitIfKnown(VARBIT_COUNTDOWN, ticks)
        world.interfaces.openInside(player, player.frame.interfaceId, player.frame.component(osrsps.game.FrameSlot.OVERLAY_HUD), OVERLAY)
        watch(player.saveKey)
        if (!running) {
            running = true
            // The streams pick up again where they stood, each moving at its own offset.
            for ((i, stream) in streams.withIndex()) {
                if (stream.wall < 0) move(stream, world.tick + MOVE_OFFSETS[i]) else stream.moveAt = world.tick + MOVE_OFFSETS[i]
            }
            world.npcScripts.later(1) { tickStreams() }
        }
    }

    /** Each tick of a game: the time runs down, and the drink once it is up; a player gone ends it with nothing. */
    private fun watch(key: String) {
        world.npcScripts.later(1) {
            val game = playing[key] ?: return@later
            val player = world.onlinePlayers.firstOrNull { it.saveKey == key }
            if (player == null || player.dead || !inRoom(player.x, player.z, player.level)) {
                playing.remove(key)
                player?.let(::leave)
                return@later
            }
            if (game.ticksLeft <= 1) return@later finish(player, game)
            game.ticksLeft--
            player.vars.setVarbitIfKnown(VARBIT_COUNTDOWN, game.ticksLeft)
            watch(key)
        }
    }

    /** The bowl and the overlay go, and the player's collecting with them. */
    private fun leave(player: Player) {
        player.stanceOverride = null
        player.vars.setVarbitIfKnown(VARBIT_COLLECTING, 0)
        world.interfaces.closeInside(player, player.frame.interfaceId, player.frame.component(osrsps.game.FrameSlot.OVERLAY_HUD))
    }

    /** Time up: back by Juna, the drink and its XP. */
    private fun finish(player: Player, game: Game) {
        playing.remove(player.saveKey)
        val tears = game.tears
        leave(player)
        world.teleport(player, END.x, END.z, END.level)
        player.anim(ANIM_DRINK)
        val skill = lowestSkill(player)
        val tenths = tears * tenthsPerTear(player.skills.tenths(skill))
        if (tenths > 0) player.skills.addExactTenths(skill, tenths)
        world.message(player, TearsLines.SKILL.getValue(skill))
        if (tears > player.vars.getVarbitIfKnown(VARBIT_BEST)) player.vars.setVarbitIfKnown(VARBIT_BEST, tears.coerceAtMost(MAX_TEARS))
        recordVisit(player)
    }

    /** Collect-from on the weeping wall at [wall]: a tear a tick, or one lost, while the player stays at it. */
    suspend fun ScriptContext.collect(wall: ZoneLoc) {
        val index = WALLS.indexOfFirst { it.first == wall.x && it.second == wall.z }
        if (index < 0) return
        if (!isPlaying(player)) return world.message(player, TearsLines.NOT_PLAYING)
        arriveDelay()
        player.vars.setVarbitIfKnown(VARBIT_COLLECTING, 1)
        try {
            while (isPlaying(player)) {
                player.anim(ANIM_COLLECT)
                delay(1)
                val game = playing[player.saveKey] ?: break
                when (streamAt(index)) {
                    true -> game.tears = (game.tears + 1).coerceAtMost(MAX_TEARS)
                    false -> game.tears = (game.tears - 1).coerceAtLeast(0)
                    null -> {}
                }
                player.vars.setVarbitIfKnown(VARBIT_TEARS, game.tears)
            }
        } finally {
            player.vars.setVarbitIfKnown(VARBIT_COLLECTING, 0)
        }
    }

    // ---- The streams ------------------------------------------------------------------------------------------------

    private fun tickStreams() {
        if (playing.isEmpty()) {
            running = false
            return
        }
        for (stream in streams) if (world.tick >= stream.moveAt) move(stream, world.tick + STREAM_TICKS)
        world.npcScripts.later(1) { tickStreams() }
    }

    /** [stream] leaves its wall dry and takes one no other stream is on, until [until]. */
    private fun move(stream: Stream, until: Long) {
        val old = stream.wall
        val free = WALLS.indices.filter { i -> streams.none { it !== stream && it.wall == i } }
        stream.wall = free[world.random.nextInt(free.size)]
        stream.moveAt = until
        if (old >= 0 && old != stream.wall) show(old, null)
        show(stream.wall, stream.blue)
    }

    /** The wall at [index]'s stream loc, the map's side of it (left or right) kept. */
    private fun show(index: Int, blue: Boolean?) {
        val (x, z) = WALLS[index]
        val current = world.locs.at(x, z, LEVEL).firstOrNull { it.id in STREAM_LOCS } ?: return
        val right = current.id in RIGHT
        val id = when (blue) {
            true -> if (right) BLUE_R else BLUE_L
            false -> if (right) GREEN_R else GREEN_L
            null -> if (right) NONE_R else NONE_L
        }
        if (current.id != id) world.locs.replace(current, id)
    }

    // ---- The way there ----------------------------------------------------------------------------------------------

    /** Climb on the rock slopes: over to the other side, along the same row. */
    suspend fun ScriptContext.climb(rocks: ZoneLoc, up: Boolean) {
        arriveDelay()
        val toX = if (player.x < rocks.x) rocks.x + 1 else rocks.x - 1
        player.anim(if (up) ANIM_CLIMB_UP else ANIM_CLIMB_DOWN)
        delay(CLIMB_TICKS)
        world.teleport(player, toX, rocks.z, rocks.level)
    }

    companion object {
        /** tog_juna: the map's multiloc on varbit 451 (tog_juna_bowl), its forms 28928 (Talk-to) and 28929 (and Story). */
        const val JUNA = 3193
        const val TALK_TO = 1
        const val STORY = 2
        /** tog_juna_dummy: her chathead. */
        const val JUNA_HEAD = 5785

        const val TUNNEL_DOWN = 6659
        const val TUNNEL_UP = 6658
        const val ENTER = 1
        const val ROCKS_UP = 6673
        const val ROCKS_DOWN = 6672
        const val CLIMB = 1
        const val MAGICAL_ROCKS_1 = 6669
        const val MAGICAL_ROCKS_2 = 6670
        const val MAGICAL_ROCKS_3 = 6671
        val MAGICAL_ROCKS = listOf(MAGICAL_ROCKS_1, MAGICAL_ROCKS_2, MAGICAL_ROCKS_3)
        const val MINE = 1

        /** tog_weepingwall and its stream locs on the wall-decoration layer, right and left. */
        const val WALL = 6660
        const val COLLECT = 1
        const val BLUE_R = 6661
        const val GREEN_R = 6662
        const val NONE_R = 6663
        const val BLUE_L = 6665
        const val GREEN_L = 6666
        const val NONE_L = 6667
        private val RIGHT = setOf(BLUE_R, GREEN_R, NONE_R)
        private val STREAM_LOCS = RIGHT + setOf(BLUE_L, GREEN_L, NONE_L)

        /** The nine weeping walls, from the map. */
        val WALLS: List<Pair<Int, Int>> = listOf(
            3257 to 9514, 3258 to 9514, 3259 to 9514, 3257 to 9520, 3258 to 9520, 3259 to 9520, 3261 to 9516, 3261 to 9517, 3261 to 9518,
        )
        const val LEVEL = 2

        /** tog_sidepanel. */
        const val OVERLAY = 276

        const val VARBIT_JUNA = 451
        const val VARBIT_STORIES = 452
        const val VARBIT_COLLECTING = 453
        const val VARBIT_TEARS = 455
        const val VARBIT_COUNTDOWN = 5099
        const val VARBIT_BEST = 10730
        /** Varbit 455 (and the best, 10730) hold 9 bits; a tick per quest point never comes near it. */
        const val MAX_TEARS = 511

        /** tog_ready_bowl, tog_walk_bowl (turn and every walk, guessed) and tog_run_bowl. */
        val BOWL_STANCE = Stance(2040, 2041, 2041, 2041, 2041, 2041, 2042)
        /** tog_lean_forward_bowl, tog_drink_bowl, tog_climb_slope_up and _down. */
        const val ANIM_COLLECT = 2043
        const val ANIM_DRINK = 2045
        const val ANIM_CLIMB_UP = 2049
        const val ANIM_CLIMB_DOWN = 2050
        const val CLIMB_TICKS = 2

        /** How long each stream stays (the Wiki: 16 ticks), and when each of the six first moves (guessed: evenly). */
        const val STREAM_TICKS = 16
        val MOVE_OFFSETS = listOf(3, 6, 8, 11, 14, 16)

        const val DAYS_BETWEEN = 7
        const val XP_BETWEEN = 100_000L

        /** Where the tunnels land (inferred from the map: in front of each tunnel's middle). */
        val CHASM_LANDING = DorgeshuunTunnels.Spot(3219, 9532, 2)
        val CAVES_LANDING = DorgeshuunTunnels.Spot(3226, 9542, 0)
        /** Where the game starts, in the walls' room, and ends, by Juna (both guessed). */
        val START = DorgeshuunTunnels.Spot(3255, 9517, 2)
        val END = DorgeshuunTunnels.Spot(3251, 9516, 2)

        fun inRoom(x: Int, z: Int, level: Int): Boolean = level == LEVEL && x in 3253..3261 && z in 9514..9520

        /** The Wiki's order for skills tied on XP. */
        val TIE_ORDER: List<Skill> = listOf(
            Skill.ATTACK, Skill.STRENGTH, Skill.RANGED, Skill.MAGIC, Skill.DEFENCE, Skill.HITPOINTS, Skill.PRAYER, Skill.AGILITY,
            Skill.HERBLORE, Skill.THIEVING, Skill.CRAFTING, Skill.RUNECRAFT, Skill.MINING, Skill.SMITHING, Skill.FISHING, Skill.COOKING,
            Skill.FIREMAKING, Skill.WOODCUTTING, Skill.FLETCHING, Skill.SLAYER, Skill.FARMING, Skill.CONSTRUCTION, Skill.HUNTER, Skill.SAILING,
        )

        /** The skill the tears go to: the least XP, the first in [TIE_ORDER] on a tie. */
        fun lowestSkill(player: Player): Skill = TIE_ORDER.minBy { player.skills.tenths(it) }

        /** Tenths of XP a tear gives in a skill at [tenths]: min(60, 10 + floor(XP / 27) / 10), in tenths. */
        fun tenthsPerTear(tenths: Int): Int = minOf(600, 100 + tenths / 10 / 27)
    }
}

fun registerTearsOfGuthix(world: World, tears: TearsOfGuthix) = with(tears) {
    world.handlers.onLoc(TearsOfGuthix.JUNA, TearsOfGuthix.TALK_TO) { talk() }
    world.handlers.onLoc(TearsOfGuthix.JUNA, TearsOfGuthix.STORY) { story() }
    world.handlers.onLoc(TearsOfGuthix.WALL, TearsOfGuthix.COLLECT) { op -> collect(op.loc!!) }
    world.handlers.onLoc(TearsOfGuthix.ROCKS_UP, TearsOfGuthix.CLIMB) { op -> climb(op.loc!!, up = true) }
    world.handlers.onLoc(TearsOfGuthix.ROCKS_DOWN, TearsOfGuthix.CLIMB) { op -> climb(op.loc!!, up = false) }
    for (rocks in TearsOfGuthix.MAGICAL_ROCKS) {
        world.handlers.onLoc(rocks, TearsOfGuthix.MINE) {
            arriveDelay()
            world.message(player, TearsLines.NOTHING)
        }
    }
    world.handlers.onLoc(TearsOfGuthix.TUNNEL_DOWN, TearsOfGuthix.ENTER) {
        arriveDelay()
        val to = TearsOfGuthix.CHASM_LANDING
        world.teleport(player, to.x, to.z, to.level)
    }
    world.handlers.onLoc(TearsOfGuthix.TUNNEL_UP, TearsOfGuthix.ENTER) {
        arriveDelay()
        val to = TearsOfGuthix.CAVES_LANDING
        world.teleport(player, to.x, to.z, to.level)
    }
}
