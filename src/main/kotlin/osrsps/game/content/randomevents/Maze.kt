package osrsps.game.content.randomevents

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile

/** The Maze's lines; the words are in the dialogue file, from the Mysterious Old Man's transcript (read 2026-11-19). */
object MazeLines : Lines("maze", "Transcript:Mysterious Old Man, the Maze version") {
    val INVITE = line("invite", "Standard dialogue - Maze version: his question", "name")
    val YES = line("yes", "Maze version: the first option, and the player's words")
    val BUSY = line("busy", "Maze version: the second option, and the player's words")
    val REACH_CENTRE = line("reach_centre", "Maze version: after the teleport")
}

/**
 * The Maze (OSRS Wiki, Maze, Strange shrine and Chest (Maze) pages and the Mysterious Old Man's transcript, read
 * 2026-11-19). The Mysterious Old Man comes as NPC 6752 (macro_maze_invitation; Talk-to, Dismiss) and asks his player
 * to solve a maze; yes takes them to one of the maze's four corners (map square 45,71, [EventAreas]). The window
 * mazetimer (209, in the floater) shows the reward potential, which client script macro_maze_timer reads from varp
 * macro_transmit (531): it starts at 100 and loses 1 every 5 ticks (Mod Ash, cited by the Wiki), and at 0 the player is
 * sent back with nothing. Touch on the strange shrine (14985) at the centre sends the player back with three rolls of
 * the shrine's table, each [TotalLevel × potential × 10/3 ÷ divisor] of one of nine items (the Wiki's formula, with the
 * potential as a fraction). The chests (14986) on the way give one of ten small rewards for 1% of the potential and
 * stand open and empty (14988) for 60 seconds.
 *
 * Inferred: the rounding of the shrine's amounts (down, as the Wiki's examples are), and a roll that comes to 0 giving
 * nothing. Guessed: that the potential doesn't drop while a chat dialogue is open in the chatbox (the Wiki says so
 * for the chests' dialogue), and only then: another window (the collection log, the equipment stats) doesn't hold it,
 * as one can be kept open while walking (exploit review R52A-1); that a chest taking the last point ends the run as the
 * timer does, after its reward; the Old Man's chathead expressions. Left out: the maze turned to face another way (the Wiki says it may
 * be), run energy not draining inside, the chests' reward box (its words aren't on the Wiki), the Time Out music and
 * the Maze Centre jingle.
 */
object Maze : RandomEvent("maze", 6752) {
    const val SHRINE = 14985
    const val CHEST = 14986
    const val CHEST_EMPTY = 14988
    const val TOUCH = 1
    const val OPEN = 1

    /** mazetimer. */
    const val TIMER_INTERFACE = 209

    /** macro_transmit: the potential the timer shows. */
    const val POTENTIAL_VARP = 531
    const val FULL = 100
    const val DRAIN_TICKS = 5
    const val CHEST_COST = 1
    const val CHEST_OPEN_TICKS = 100

    /** The zones copied: all of map square 45,71. */
    const val FROM_X = 2880
    const val FROM_Z = 4544
    const val WIDTH_ZONES = 8
    const val HEIGHT_ZONES = 8

    /** The four corners the Wiki's map pins as starts. */
    val STARTS = listOf(Tile(2891, 4597), Tile(2933, 4597), Tile(2891, 4555), Tile(2933, 4555))

    /** The shrine's items and their divisors (the Maze page): coal and mithril ore noted. */
    val SHRINE_TABLE = listOf(995 to 1, 314 to 2, 884 to 3, 562 to 9, 886 to 12, 560 to 18, 454 to 45, 448 to 162, 561 to 180)
    const val SHRINE_ROLLS = 3

    /** The chests' table (Chest (Maze)/Rewards): runes, arrows, bolts and 2-dose potions, 1/10 each. */
    val CHEST_TABLE = listOf(556 to 15, 555 to 10, 557 to 10, 554 to 10, 882 to 20, 877 to 10, 884 to 15, 123 to 1, 117 to 1, 135 to 1)

    /** A run of the maze. */
    class Run {
        var potential = FULL
        var done = false
        /** Ticks since the start, for [DRAIN_TICKS]. */
        var ticks = 0
    }

    override val teleports = true
    override val nags = with(MysteriousOldManLines) { listOf(NAME, HELLO, TALK_TO_ME, ARE_YOU_THERE, LIKE_TO_TALK) }
    override val leaving = MysteriousOldManLines.MAYBE_LATER
    override val notYours = MysteriousOldManLines.SOMEONE_ELSE

    override suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean = with(MazeLines) {
        chatNpc(ChatAnim.HAPPY, INVITE, "name" to player.username)
        if (choice(YES, BUSY) != 1) {
            chatPlayer(ChatAnim.NEUTRAL, BUSY)
            return true
        }
        chatPlayer(ChatAnim.HAPPY, YES)
        val type = world.npcTypes[npcId]
        val start = STARTS[world.random.nextInt(STARTS.size)]
        val away = events.areas.takeAway(player, this@Maze, FROM_X, FROM_Z, WIDTH_ZONES, HEIGHT_ZONES, start) ?: return true
        val run = Run()
        away.state = run
        world.interfaces.openFloater(player, TIMER_INTERFACE)
        showPotential(player, run.potential)
        drain(world, player, away, run)
        delay(2)
        if (type != null) chatNpc(type, type.name, ChatAnim.NEUTRAL, REACH_CENTRE)
        true
    }

    private fun showPotential(player: Player, potential: Int) {
        if (POTENTIAL_VARP < player.vars.types.varpCount) player.vars[POTENTIAL_VARP] = potential
    }

    /**
     * Every [DRAIN_TICKS] the potential drops by 1, unless a chat dialogue is open; at 0 the player goes back with nothing.
     * Checked each tick, so the timer comes back as soon as another window that shares its floater (All Settings, the
     * world map) is shut. Inferred: a player who leaves the maze another way loses the timer too.
     */
    private fun drain(world: World, player: Player, away: EventAreas.Away, run: Run) {
        world.npcScripts.later(1) {
            if (run.done) return@later
            // Gone some other way (a teleport, logging out): the timer goes with them.
            if (world.randomEvents.areas.awayOf(player) !== away) {
                run.done = true
                if (player.floater == TIMER_INTERFACE) world.interfaces.closeFloater(player)
                return@later
            }
            if (player.floater < 0) {
                world.interfaces.openFloater(player, TIMER_INTERFACE)
                showPotential(player, run.potential)
            }
            run.ticks++
            if (run.ticks % DRAIN_TICKS == 0 && player.chatModal < 0) {
                run.potential--
                showPotential(player, run.potential)
                if (run.potential <= 0) return@later out(world, player, away, run)
            }
            drain(world, player, away, run)
        }
    }

    private fun out(world: World, player: Player, away: EventAreas.Away, run: Run) {
        run.done = true
        world.interfaces.closeFloater(player)
        showPotential(player, 0)
        world.randomEvents.areas.bringBack(player, away)
    }

    fun runOf(world: World, player: Player): Pair<EventAreas.Away, Run>? {
        val away = world.randomEvents.areas.awayOf(player) ?: return null
        val run = away.state as? Run ?: return null
        return away to run
    }

    /** How many of an item with [divisor] one shrine roll gives. */
    fun shrineAmount(totalLevel: Int, potential: Int, divisor: Int): Int = totalLevel * potential * 10 / (3 * 100 * divisor)

    /** Touch on the shrine: home, then the three rolls. */
    fun touchShrine(world: World, player: Player) {
        val (away, run) = runOf(world, player) ?: return
        if (run.done) return
        val potential = run.potential
        out(world, player, away, run)
        val total = Skill.entries.sumOf { player.skills.baseLevel(it) }
        repeat(SHRINE_ROLLS) {
            val (id, divisor) = SHRINE_TABLE[world.random.nextInt(SHRINE_TABLE.size)]
            val count = shrineAmount(total, potential, divisor)
            if (count > 0) world.randomEvents.reward(player, id, count)
        }
    }

    /**
     * Open on a chest: one small reward for a point of potential, and the chest stands empty for a minute. The last
     * point ends the run as the timer does (R52A-1: chests can't be farmed at 1 for ever); the player is sent back
     * first and rewarded there.
     */
    fun openChest(world: World, player: Player, loc: ZoneLoc) {
        val (away, run) = runOf(world, player) ?: return
        if (run.done || world.locs.replace(loc, CHEST_EMPTY, CHEST_OPEN_TICKS) == null) return
        run.potential = (run.potential - CHEST_COST).coerceAtLeast(0)
        showPotential(player, run.potential)
        if (run.potential == 0) out(world, player, away, run)
        val (id, count) = CHEST_TABLE[world.random.nextInt(CHEST_TABLE.size)]
        world.randomEvents.reward(player, id, count)
    }

    /** The interface's gameval names, checked against the cache. */
    val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>>
        get() = mapOf(TIMER_INTERFACE to ("mazetimer" to mapOf(8 to "com_8")))

    /** The maze's locs whose handlers work in its copies. */
    val INSTANCE_LOCS: Set<Int> = setOf(SHRINE, CHEST)

    fun register(world: World) {
        world.handlers.onLoc(SHRINE, TOUCH) { touchShrine(world, player) }
        world.handlers.onLoc(CHEST, OPEN) { op -> openChest(world, player, op.loc ?: return@onLoc) }
    }
}
