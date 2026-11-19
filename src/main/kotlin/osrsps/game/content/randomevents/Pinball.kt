package osrsps.game.content.randomevents

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile

/** Flippa's and Tilt's lines; the words are in the dialogue file, from their transcripts (read 2026-11-19). */
object PinballLines : Lines("pinball", "Transcript:Flippa and Transcript:Tilt") {
    val COME_PLAY = line("come_play", "Flippa, overhead dialogue: dialogue 1", "name")
    val GOOD_GAME = line("good_game", "Flippa, overhead dialogue: dialogue 2", "name")
    val WANT_PLAY = line("want_play", "Flippa, overhead dialogue: dialogue 3", "name")
    val HITTING_THINGS = line("hitting_things", "Flippa, overhead dialogue: dialogue 4", "name")
    val MAYBE_LATER = line("maybe_later", "Flippa, overhead dialogue: if the player does not respond in time")
    val INVITE = line("invite", "Flippa, initial dialogue: his question", "name")
    val YES = line("yes", "Flippa, initial dialogue: the first option, and the player's words")
    val NO_THANKS = line("no_thanks", "Flippa, initial dialogue: the second option, and the player's words")
    val GOING_AWAY = line("going_away", "Flippa: attempting to speak to him again after choosing \"No thanks.\"")
    val WRONG_PERSON = line("wrong_person", "Flippa: if he spawned for another player")
    val ARRIVAL = line("arrival", "Tilt: upon getting to the random event area")
    val WHAT_TO_DO = line("what_to_do", "Inside the minigame, before finishing: the player's first words")
    val POKE_PILLARS = line("poke_pillars", "Inside the minigame, before finishing: the troll's first answer")
    val ALL_OF_THEM = line("all_of_them", "Inside the minigame, before finishing: the player's second words")
    val NOT_RINGS = line("not_rings", "Inside the minigame, before finishing: the troll's second answer")
    val FLASHING = line("flashing", "Inside the minigame, before finishing: the player's third words")
    val TEN_TIMES = line("ten_times", "Inside the minigame, before finishing: the troll's last answer")
    val FREE_TO_GO = line("free_to_go", "Inside the minigame, after finishing: the player's first words")
    val GET_GOING = line("get_going", "Inside the minigame, after finishing: the troll's answer")
    val NICE_BREAK = line("nice_break", "Inside the minigame, after finishing: the player's last words")
}

/**
 * Pinball (OSRS Wiki, Pinball, Pinball Post, Flippa and Tilt pages and the trolls' transcripts, read 2026-11-19). Flippa
 * comes as NPC 6744 (pinball_invitation; Talk-to, Dismiss) and asks his player to play; yes takes them to the pinball
 * room (map square 30,78, the Pinball page's map, through [EventAreas]), where Flippa (5427) and Tilt (5428) stand with
 * their clubs and Tilt tells them the rules. Five posts stand in an arc (the Pinball Post page's map, ids as its
 * infobox, and the cache's _inactive pairs): tagging the one with glowing rings scores a point, tagging another puts
 * the score back to 0. Ten points finish it. The cave exit (9293) lets the player out at any time since 8 November
 * 2018, with one reward from the Pinball page's table if they finished and nothing if they didn't. The score shows in
 * pinball_overlay (542, in the floater), whose client script macro_pinball_score reads varbit macro_pinball_score.
 *
 * Guessed: where the player lands and where the trolls stand; which post glows first and next (one at random, never
 * the same twice running), kept in varbits macro_pinball_current and macro_pinball_next as 1 to 5 in the Wiki's order;
 * macro_pinball_complete set at ten; no post glowing once finished, and tags after it doing nothing; that declining
 * leaves Flippa waiting until his time is up or he is talked to again (GOING_AWAY); the trolls' chathead
 * expressions. Left out: the player's tag animation and the trolls' club swings, the Pinball Wizard music.
 */
object Pinball : RandomEvent("pinball", 6744) {
    const val FLIPPA = 5427
    const val TILT = 5428
    const val EXIT = 9293
    const val TAG = 1
    const val EXIT_OP = 1

    /** pinball_overlay. */
    const val OVERLAY = 542

    /** macro_pinball_current, macro_pinball_next, macro_pinball_score and macro_pinball_complete (of varp 727). */
    const val CURRENT_VARBIT = 2119
    const val NEXT_VARBIT = 2120
    const val SCORE_VARBIT = 2121
    const val COMPLETE_VARBIT = 2122

    const val TO_WIN = 10

    /** A post: where it stands, its plain id and its glowing one (the cache's pinball_post_X_inactive and pinball_post_X). */
    class Post(val name: String, val at: Tile, val plain: Int, val glowing: Int)

    val POSTS = listOf(
        Post("air", Tile(1967, 5046), 8982, 8983),
        Post("earth", Tile(1969, 5049), 8984, 8994),
        Post("fire", Tile(1972, 5050), 9079, 8995),
        Post("nature", Tile(1975, 5049), 9081, 9080),
        Post("water", Tile(1977, 5046), 9258, 9259),
    )

    /** The zones copied: the room, in map square 30,78. */
    const val FROM_X = 1960
    const val FROM_Z = 5032
    const val WIDTH_ZONES = 3
    const val HEIGHT_ZONES = 3

    /** Guessed: where the player lands, and where the trolls stand (by the exit, either side). */
    val ARRIVE = Tile(1972, 5042)
    val FLIPPA_AT = Tile(1965, 5041)
    val TILT_AT = Tile(1978, 5041)

    /** Guessed: how often the room checks its player is still in it, to take the score window away if not. */
    const val WATCH_TICKS = 2

    /** Pinball's table (the Pinball page), out of 10: noted gems. */
    val REWARDS: List<Triple<Int, Int, Int>> = listOf(Triple(1608, 5, 3), Triple(1606, 5, 3), Triple(1604, 5, 3), Triple(1602, 2, 1))
    const val REWARD_ROLL = 10

    /** A game: the score, the glowing post and the next one (indexes into [POSTS]), and the trolls. */
    class Game {
        var score = 0
        var current = 0
        var next = 0
        var done = false
        var over = false
        var trolls: List<Npc> = emptyList()
        val complete get() = score >= TO_WIN
    }

    /** The players who said no, whose Flippa says he is going on the next Talk-to. */
    private val refused = HashSet<String>()

    override val teleports = true
    override val nags = with(PinballLines) { listOf(COME_PLAY, GOOD_GAME, WANT_PLAY, HITTING_THINGS) }
    override val leaving = PinballLines.MAYBE_LATER
    override val notYours = PinballLines.WRONG_PERSON

    override fun started(world: World, out: RandomEvents.Out) {
        refused -= out.owner
    }

    override fun loggedOut(player: Player) {
        refused -= player.saveKey
    }

    override suspend fun osrsps.game.dialogue.Dialogue.talk(world: World, events: RandomEvents): Boolean = with(PinballLines) {
        if (refused.remove(player.saveKey)) {
            chatNpc(ChatAnim.NEUTRAL, GOING_AWAY)
            return true
        }
        chatNpc(ChatAnim.HAPPY, INVITE, "name" to player.username)
        if (choice(YES, NO_THANKS) != 1) {
            chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
            refused += player.saveKey
            return false
        }
        chatPlayer(ChatAnim.HAPPY, YES)
        val away = events.areas.takeAway(player, this@Pinball, FROM_X, FROM_Z, WIDTH_ZONES, HEIGHT_ZONES, ARRIVE) ?: return true
        val game = Game()
        away.state = game
        game.trolls = listOfNotNull(spawn(world, away, FLIPPA, FLIPPA_AT), spawn(world, away, TILT, TILT_AT))
        game.current = world.random.nextInt(POSTS.size)
        game.next = other(world, game.current)
        glow(world, away, game.current, true)
        world.interfaces.openFloater(player, OVERLAY)
        show(player, game)
        watch(world, player, away, game)
        delay(2)
        val tilt = game.trolls.firstOrNull { it.type.id == TILT }
        if (tilt != null) chatNpc(tilt, ChatAnim.NEUTRAL, text(ARRIVAL))
        true
    }

    private fun spawn(world: World, away: EventAreas.Away, id: Int, at: Tile): Npc? {
        val type = world.npcTypes[id] ?: return null
        val tile = away.inside(at.x, at.z)
        return world.spawnNpc(NpcSpawn(type, tile.x, tile.z, 0, source = "pinball room for ${away.owner}", respawns = false))
    }

    /** A post at random, other than [not]. */
    private fun other(world: World, not: Int): Int {
        val pick = world.random.nextInt(POSTS.size - 1)
        return if (pick >= not) pick + 1 else pick
    }

    /** Sets post [index] glowing or plain, in [away]'s copy. */
    private fun glow(world: World, away: EventAreas.Away, index: Int, on: Boolean) {
        val post = POSTS[index]
        val at = away.inside(post.at.x, post.at.z)
        val loc = world.locs.at(at.x, at.z, 0).firstOrNull { it.id == post.plain || it.id == post.glowing } ?: return
        val id = if (on) post.glowing else post.plain
        if (loc.id != id) world.locs.replace(loc, id)
    }

    private fun show(player: Player, game: Game) {
        setVarbit(player, CURRENT_VARBIT, if (game.complete) 0 else game.current + 1)
        setVarbit(player, NEXT_VARBIT, if (game.complete) 0 else game.next + 1)
        setVarbit(player, SCORE_VARBIT, game.score)
        setVarbit(player, COMPLETE_VARBIT, if (game.complete) 1 else 0)
    }

    private fun clear(player: Player) {
        for (id in listOf(CURRENT_VARBIT, NEXT_VARBIT, SCORE_VARBIT, COMPLETE_VARBIT)) setVarbit(player, id, 0)
    }

    private fun setVarbit(player: Player, id: Int, value: Int) {
        if (player.vars.types.varbit(id) != null) player.vars.setVarbit(id, value)
    }

    /**
     * Inferred: a player who leaves the room another way (a teleport, logging out) loses the score window too. Also
     * brings the window back once the floater is free again.
     */
    private fun watch(world: World, player: Player, away: EventAreas.Away, game: Game) {
        world.npcScripts.later(WATCH_TICKS) {
            if (game.over) return@later
            if (world.randomEvents.areas.awayOf(player) !== away) {
                game.over = true
                if (player.floater == OVERLAY) world.interfaces.closeFloater(player)
                clear(player)
                return@later
            }
            // Another window that shares the floater (All Settings, the world map) shut: the score window comes back.
            if (player.floater < 0) {
                world.interfaces.openFloater(player, OVERLAY)
                show(player, game)
            }
            watch(world, player, away, game)
        }
    }

    fun gameOf(world: World, player: Player): Pair<EventAreas.Away, Game>? {
        val away = world.randomEvents.areas.awayOf(player) ?: return null
        val game = away.state as? Game ?: return null
        return away to game
    }

    /** Tag on a post: the glowing one scores and the next glows; any other puts the score back to 0. */
    fun tag(world: World, player: Player, loc: ZoneLoc) {
        val (away, game) = gameOf(world, player) ?: return
        if (game.over || game.complete) return
        val at = away.outside(loc.x, loc.z)
        val index = POSTS.indexOfFirst { it.at == at }
        if (index < 0) return
        if (index != game.current) {
            game.score = 0
            return show(player, game)
        }
        game.score++
        glow(world, away, game.current, false)
        if (!game.complete) {
            game.current = game.next
            game.next = other(world, game.current)
            glow(world, away, game.current, true)
        }
        show(player, game)
    }

    /** Exit on the cave exit: home, with a reward once finished. */
    fun exit(world: World, player: Player) {
        val (away, game) = gameOf(world, player) ?: return
        if (game.over) return
        game.over = true
        val won = game.complete
        if (player.floater == OVERLAY) world.interfaces.closeFloater(player)
        clear(player)
        world.randomEvents.areas.bringBack(player, away)
        if (!won) return
        var roll = world.random.nextInt(REWARD_ROLL)
        for ((id, count, weight) in REWARDS) {
            if (roll < weight) {
                world.randomEvents.reward(player, id, count)
                return
            }
            roll -= weight
        }
    }

    /** Talk-to on Flippa or Tilt in the room: the rules again, or that the player is free to go. */
    suspend fun ScriptContext.talkInRoom(world: World, npc: Npc) {
        val (_, game) = gameOf(world, player) ?: return
        if (npc !in game.trolls) return
        world.dialogues.start(this, npc) {
            with(PinballLines) {
                if (game.complete) {
                    chatPlayer(ChatAnim.NEUTRAL, FREE_TO_GO)
                    chatNpc(ChatAnim.NEUTRAL, GET_GOING)
                    chatPlayer(ChatAnim.NEUTRAL, NICE_BREAK)
                    return@start
                }
                chatPlayer(ChatAnim.NEUTRAL, WHAT_TO_DO)
                chatNpc(ChatAnim.NEUTRAL, POKE_PILLARS)
                chatPlayer(ChatAnim.NEUTRAL, ALL_OF_THEM)
                chatNpc(ChatAnim.NEUTRAL, NOT_RINGS)
                chatPlayer(ChatAnim.NEUTRAL, FLASHING)
                chatNpc(ChatAnim.NEUTRAL, TEN_TIMES)
            }
        }
    }

    /** The interface's gameval names, checked against the cache. */
    val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>>
        get() = mapOf(OVERLAY to ("pinball_overlay" to mapOf(2 to "dodger", 3 to "content")))

    /** The room's locs whose handlers work in its copies. */
    val INSTANCE_LOCS: Set<Int> = POSTS.flatMap { listOf(it.plain, it.glowing) }.toSet() + EXIT

    fun register(world: World) {
        for (id in POSTS.flatMap { listOf(it.plain, it.glowing) }) world.handlers.onLoc(id, TAG) { op -> tag(world, player, op.loc ?: return@onLoc) }
        world.handlers.onLoc(EXIT, EXIT_OP) { exit(world, player) }
        for (id in listOf(FLIPPA, TILT)) world.handlers.onNpc(id, RandomEvents.TALK_TO) { op -> talkInRoom(world, op.npc ?: return@onNpc) }
    }
}
