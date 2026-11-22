package osrsps.game.content.randomevents

import net.rsprot.protocol.game.outgoing.camera.CamLookAtV3
import net.rsprot.protocol.game.outgoing.camera.CamMoveToV3
import net.rsprot.protocol.game.outgoing.camera.CamReset
import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.message
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext
import osrsps.game.skill.Fishing
import osrsps.game.skill.FishingLines
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile

/** Evil Bob's lines; the words are in the dialogue file, from his transcript (read 2026-11-19). */
object EvilBobLines : Lines("evil_bob", "Transcript:Evil Bob") {
    val MEW = line("mew", "Overhead dialogue: dialogue 1")
    val YOWL = line("yowl", "Overhead dialogue: dialogue 2")
    val MEEEOW = line("meeeow", "Overhead dialogue: dialogue 3")
    val MEEEEOW = line("meeeeow", "Overhead dialogue: dialogue 4")
    val MEOWBY_LATER = line("meowby_later", "Overhead dialogue: if the player does not respond in time")
    val COME_FISHING = line("come_fishing", "Island version: his question", "name")
    val FOLLOW = line("follow", "Island version: the options' title")
    val YES = line("yes", "Island version: the first option")
    val NO = line("no", "Island version: the second option")
    val LEAVE_ALONE = line("leave_alone", "Island version: the second option's message box")
    val NOT_INTERESTED = line("not_interested", "When talking to another player's Evil Bob event (a message box)")
    val NO_LONGER = line("no_longer", "After losing interest (a message box)")
    val HUH = line("huh", "Continuation after getting teleported: the player's first line")
    val WHERE_AM_I = line("where_am_i", "Continuation after getting teleported: the player's second line")
    val MY_ISLAND = line("my_island", "Continuation after getting teleported: his first line")
    val YOUR_LEADER = line("your_leader", "Continuation after getting teleported: the player's third line")
    val I_AM_LEADER = line("i_am_leader", "Continuation after getting teleported: his second line")
    val NOT_A_SLAVE = line("not_a_slave", "Continuation after getting teleported: the player's last line")
    val AHAHA = line("ahaha", "Continuation after getting teleported: his laugh")
    val LET_ME_OUT = line("let_me_out", "Talking before the right fish: the player's first line")
    val NEVER_LET_GO = line("never_let_go", "Talking before the right fish: his answer", "name")
    val WHY_NOT = line("why_not", "Talking: the first option, and the player's words")
    val UHM = line("uhm", "Why not?: his first line")
    val SAY_SO = line("say_so", "Why not?: his second line")
    val HUNGRY = line("hungry", "Why not?: his third line")
    val WHAT_FISH = line("what_fish", "Why not?: the player's question")
    val HURRY = line("hurry", "Why not?: his last line")
    val ALL_ABOUT = line("all_about", "Talking: the second option, and the player's words")
    val SIT_DOWN = line("sit_down", "What's it all about?: his first line")
    val GO_ON = line("go_on", "What's it all about?: the player's first line")
    val SKILLED = line("skilled", "What's it all about?: his second line")
    val A_SLAVE = line("a_slave", "What's it all about?: the player's second line")
    val REASONABLE = line("reasonable", "What's it all about?: his third line")
    val NO_DEALS = line("no_deals", "What's it all about?: the player's third line")
    val IS_IT = line("is_it", "What's it all about?: his fourth line")
    val WONT_HOLD = line("wont_hold", "What's it all about?: the player's last line")
    val WONT_I = line("wont_i", "What's it all about?: his fifth line")
    val NO_WAY_OUT = line("no_way_out", "What's it all about?: his sixth line")
    val ASK_SERVANTS = line("ask_servants", "What's it all about?: his last line")
    val HOW_TALKING = line("how_talking", "Talking: the third option, and the player's words")
    val NOT_MEOWING = line("not_meowing", "How is it possible that you're talking?: his first line")
    val WHY_MEOWING = line("why_meowing", "How is it possible that you're talking?: the player's question")
    val AMULET = line("amulet", "How is it possible that you're talking?: his second line")
    val DREADFUL = line("dreadful", "How is it possible that you're talking?: his last line")
    val HEADACHE = line("headache", "How is it possible that you're talking?: the player's last line")
    val TO_BOB = line("to_bob", "Talking: the fourth option, and the player's words")
    val I_AM_BOB = line("i_am_bob", "What did you do to Bob?: his first line")
    val INCARNATION = line("incarnation", "What did you do to Bob?: his second line")
    val MIRROR = line("mirror", "What did you do to Bob?: the player's question")
    val SERVANT_CALLED = line("servant_called", "What did you do to Bob?: his third line", "name")
    val FISH_FOR_ME = line("fish_for_me", "What did you do to Bob?: his last line")
    val CATCH = line("catch", "Upon catching a fishlike thing (a box with the fish)")
    val NOT_EATING = line("not_eating", "Attempting to eat the fishlike thing (a message box)")
    val COOKED = line("cooked", "Using a fishlike thing on Evil Bob: his first line")
    val UNCOOK_IT = line("uncook_it", "Using a fishlike thing on Evil Bob: the player's answer")
    val COLD_FIRE = line("cold_fire", "Using a fishlike thing on Evil Bob: his last line")
    val DISGUSTING = line("disgusting", "The wrong raw fishlike thing: his first line")
    val WHAT_KIND = line("what_kind", "The wrong raw fishlike thing: his second line")
    val MORE_ATTENTIVE = line("more_attentive", "The wrong raw fishlike thing: the message box")
    val DELICIOUS = line("delicious", "The final raw fishlike thing: his first line")
    val CATNAP = line("catnap", "The final raw fishlike thing: his second line")
    val ZZZ = line("zzz", "The final raw fishlike thing: his overhead")
    val THATS_DELICIOUS = line("thats_delicious", "More raw fishlike things needed: his first line")
    val ANOTHER_ONE = line("another_one", "More raw fishlike things needed: his second line")
    val LESS_ATTENTIVE = line("less_attentive", "More raw fishlike things needed: the message box")
    val ANYTHING_ELSE = line("anything_else", "Using anything else on Evil Bob")
    val SLEEPING = line("sleeping", "Trying to talk with Evil Bob while he's asleep (a message box)")
    val BETTER_AT = line("better_at", "Evil Bob page: the message after the reward, naming the skill", "skill")
}

/** The servant's lines; the words are in the dialogue file, from her transcript (read 2026-11-19). */
object EvilBobServantLines : Lines("evil_bob_servant", "Transcript:Servant (ScapeRune)") {
    val KIDNAPPED = line("kidnapped", "Before Evil Bob sleeps, the first talk: the player's first line")
    val CANT_HELP = line("cant_help", "Before Evil Bob sleeps, the first talk: her first line")
    val LISTEN = line("listen", "Before Evil Bob sleeps, the first talk: the player's second line")
    val FISH_HE_LIKES = line("fish_he_likes", "Before Evil Bob sleeps: her line (the whole talk once talked to)")
    val OVER_THERE = line("over_there", "Before Evil Bob sleeps, the first talk: her last line, before the cutscene")
    val FALLEN_ASLEEP = line("fallen_asleep", "After Evil Bob sleeps: the player's first line")
    val COME_WHERE = line("come_where", "After Evil Bob sleeps: her first line")
    val AWAY = line("away", "After Evil Bob sleeps: the player's second line")
    val DONT_BELONG = line("dont_belong", "After Evil Bob sleeps: her second line", "name")
    val LOVE_YOU = line("love_you", "After Evil Bob sleeps: the first option, and the player's words")
    val NEVER_BE = line("never_be", "But I love you!: her first line", "name")
    val GO_NOW = line("go_now", "But I love you!: her last line")
    val ALRIGHT_OPTION = line("alright_option", "After Evil Bob sleeps: the second option")
    val ALRIGHT = line("alright", "Oh, alright then.: the player's words")
}

/** The uncooking pot's lines; the words are in the dialogue file, from its transcript (read 2026-11-19). */
object UncookingPotLines : Lines("uncooking_pot", "Transcript:Uncooking pot") {
    val LEVEL = line("level", "Using regular cooked food on the pot: the first message box", "level")
    val NO_SKILL = line("no_skill", "Using regular cooked food on the pot: the second message box")
    val BURNT = line("burnt", "Using burnt food on the pot (a message box)")
}

/**
 * Evil Bob's island (OSRS Wiki, Evil Bob, ScapeRune, Servant (ScapeRune), Fishlike thing, Raw fishlike thing, Uncooking
 * pot, Fishing spot (ScapeRune), Portal (Evil Bob) and Small fishing net (Evil Bob) pages and the transcripts, read
 * 2026-11-19). Evil Bob comes as NPC 390 (macro_evil_bob_outside; Talk-to, Dismiss) and asks the player along if they
 * like fishing; yes takes them to his island in ScapeRune (map square 39,74, [EventAreas]). There are four sets of
 * fishing spots (loc 23114), one at each compass point, each marked by a statue; one set holds the fish he likes. His
 * servant (393) shows which in a cutscene (interface macro_evil_bob). Net on a spot
 * with a small fishing net (one of seven on the beach, obj 6209, or the player's own small or big net) catches a
 * fishlike thing, already cooked (6202 from the right set, 6206 from another); the uncooking pot (23113) makes it raw
 * (6200, 6204), and it is used on Evil Bob. One right fish puts him to sleep (391); each wrong one makes him need one
 * more. The portal (23115) leaves at any time, the player blowing a raspberry at him: once he sleeps, with 650 Fishing
 * XP, or with an unboosted Magic level of 51 or more a half chance of 650 Magic XP instead (Mod Ash), and the message
 * naming the skill. The island's fish and nets go as they leave.
 *
 * Guessed: which set is right (one of the four at random); where the player lands; Bob standing still where the
 * Wiki's map has him, and his servant likewise; the cutscene's camera (moved between the island's middle and the
 * statue, looking at the statue, for [CUTSCENE_TICKS]) and the overlay it opens in the floater slot; that a catch comes
 * after the first roll's 4 ticks ([CATCH_TICKS]) every time, the raspberry's [LEAVE_TICKS] before the portal
 * sends the player home, with the small net's message and animation (the spot works "the same as
 * a regular small fishing net"); the nets lying until the island ends; the uncooking pot's Uncooking level drawn at
 * random from 40, 50, 60 and 70 each time; the XP at the server's rate, as other XP the game grants. Inferred: Evil
 * Bob on his island is 390 awake (the Wiki's ids) and turns into 391 as he falls asleep. Left out: the player's farewell
 * overhead as they enter the portal (players have no overhead text here yet), the portal's reminder about dropped items, and the
 * catch that the transcript's kitten trivia hints at (talking to the servant again to fish again).
 */
object EvilBob : RandomEvent("evil_bob", 390) {
    const val ASLEEP = 391
    const val SERVANT = 393
    const val UNCOOKING_POT = 23113
    const val FISHING_SPOT = 23114
    const val PORTAL = 23115
    const val NET_OP = 1
    const val ENTER = 1

    const val RAW_RIGHT = 6200
    const val COOKED_RIGHT = 6202
    const val RAW_WRONG = 6204
    const val COOKED_WRONG = 6206
    const val NET = 6209
    val FISH = listOf(RAW_RIGHT, COOKED_RIGHT, RAW_WRONG, COOKED_WRONG)
    val NETS = listOf(NET, Fishing.NET, Fishing.BIG_NET)

    /** macro_evil_bob: the cutscene's caption. */
    const val CUTSCENE_INTERFACE = 186
    const val CUTSCENE_TICKS = 8
    const val CATCH_TICKS = 4
    const val RASPBERRY = 2110
    const val LEAVE_TICKS = 2
    const val XP_TENTHS = 6_500
    const val MAGIC_LEVEL = 51
    val UNCOOKING_LEVELS = listOf(40, 50, 60, 70)

    /** Long enough that he sleeps until the island ends. */
    private const val SLEEP_TICKS = 100_000

    /** The four sets of spots: where each statue stands (the cache's macro_evil_bob_statue_1 to _4) and which tiles. */
    enum class Spots(val statue: Tile, private val holds: (Int, Int) -> Boolean) {
        SOUTH(Tile(2525, 4767), { _, z -> z == 4764 }),
        EAST(Tile(2538, 4777), { x, _ -> x == 2543 }),
        NORTH(Tile(2526, 4786), { _, z -> z == 4791 }),
        WEST(Tile(2514, 4776), { x, _ -> x == 2510 }),
        ;

        companion object {
            /** The set a spot on the real map's tile [x], [z] belongs to. */
            fun of(x: Int, z: Int): Spots? = entries.firstOrNull { it.holds(x, z) }
        }
    }

    /** The zones copied: the island of map square 39,74. */
    const val FROM_X = 2504
    const val FROM_Z = 4760
    const val WIDTH_ZONES = 6
    const val HEIGHT_ZONES = 5

    val MIDDLE = Tile(2526, 4777)
    val ARRIVE = Tile(2526, 4776)
    val BOB_AT = Tile(2524, 4777)
    val SERVANT_AT = Tile(2527, 4777)
    val NETS_AT = listOf(
        Tile(2518, 4768), Tile(2533, 4769), Tile(2535, 4772), Tile(2536, 4783), Tile(2533, 4784), Tile(2516, 4784), Tile(2520, 4786),
    )

    /** A stay on the island: the right set, how many right fish he still wants, and whether the servant has shown it. */
    class Island(val bob: Npc?, val servant: Npc?, val right: Spots) {
        var wanted = 1
        var shown = false
        var asleep = false
    }

    override val teleports = true
    override val placeItems = (FISH + NET).toSet()
    override val nags = with(EvilBobLines) { listOf(MEW, YOWL, MEEEOW, MEEEEOW) }
    override val leaving = EvilBobLines.MEOWBY_LATER
    override val notYours = EvilBobLines.NOT_INTERESTED
    override val notYoursInBox = true
    override val gaveUp = EvilBobLines.NO_LONGER

    override suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean = with(EvilBobLines) {
        chatNpc(ChatAnim.NEUTRAL, COME_FISHING, "name" to player.username)
        if (choice(YES, NO, title = text(FOLLOW)) != 1) {
            mesbox(LEAVE_ALONE)
            return true
        }
        val away = events.areas.takeAway(player, this@EvilBob, FROM_X, FROM_Z, WIDTH_ZONES, HEIGHT_ZONES, ARRIVE) ?: return true
        val island = Island(spawn(world, away, BOB_AT, npcId), spawn(world, away, SERVANT_AT, SERVANT), Spots.entries[world.random.nextInt(Spots.entries.size)])
        away.state = island
        for (tile in NETS_AT) {
            val inside = away.inside(tile.x, tile.z)
            world.objTypes[NET]?.let { world.groundObjs.addPublic(it, 1, inside.x, inside.z, 0) }
        }
        delay(2)
        val bob = island.bob ?: return true
        chatPlayer(ChatAnim.QUIZ, HUH)
        chatPlayer(ChatAnim.QUIZ, WHERE_AM_I)
        chatNpc(bob, ChatAnim.NEUTRAL, text(MY_ISLAND))
        chatPlayer(ChatAnim.ANGRY, YOUR_LEADER)
        chatNpc(bob, ChatAnim.SHIFTY, text(I_AM_LEADER))
        chatPlayer(ChatAnim.ANGRY, NOT_A_SLAVE)
        chatNpc(bob, ChatAnim.LAUGH, text(AHAHA))
        true
    }

    private fun spawn(world: World, away: EventAreas.Away, at: Tile, id: Int): Npc? {
        val type = world.npcTypes[id] ?: return null
        val tile = away.inside(at.x, at.z)
        return world.spawnNpc(NpcSpawn(type, tile.x, tile.z, 0, source = "evil bob's island for ${away.owner}", respawns = false))
    }

    /** The stay [player] is in and its island, if they are on one. */
    fun islandOf(world: World, player: Player): Pair<EventAreas.Away, Island>? {
        val away = world.randomEvents.areas.awayOf(player) ?: return null
        val island = away.state as? Island ?: return null
        return away to island
    }

    /** Talk-to on Evil Bob awake on his island. */
    override suspend fun ScriptContext.talkElsewhere(world: World, npc: Npc) {
        val (_, island) = islandOf(world, player) ?: return
        if (npc !== island.bob) return
        world.dialogues.start(this, npc) {
            with(EvilBobLines) {
                chatPlayer(ChatAnim.ANGRY, LET_ME_OUT)
                chatNpc(ChatAnim.SHIFTY, NEVER_LET_GO, "name" to player.username)
                when (choice(WHY_NOT, ALL_ABOUT, HOW_TALKING, TO_BOB)) {
                    1 -> {
                        chatPlayer(ChatAnim.QUIZ, WHY_NOT)
                        chatNpc(ChatAnim.CONFUSED, UHM)
                        chatNpc(ChatAnim.ANGRY, SAY_SO)
                        chatNpc(ChatAnim.NEUTRAL, HUNGRY)
                        chatPlayer(ChatAnim.QUIZ, WHAT_FISH)
                        chatNpc(ChatAnim.ANGRY, HURRY)
                    }
                    2 -> {
                        chatPlayer(ChatAnim.QUIZ, ALL_ABOUT)
                        chatNpc(ChatAnim.NEUTRAL, SIT_DOWN)
                        chatPlayer(ChatAnim.NEUTRAL, GO_ON)
                        chatNpc(ChatAnim.NEUTRAL, SKILLED)
                        chatPlayer(ChatAnim.ANGRY, A_SLAVE)
                        chatNpc(ChatAnim.NEUTRAL, REASONABLE)
                        chatPlayer(ChatAnim.ANGRY, NO_DEALS)
                        chatNpc(ChatAnim.QUIZ, IS_IT)
                        chatPlayer(ChatAnim.ANGRY, WONT_HOLD)
                        chatNpc(ChatAnim.QUIZ, WONT_I)
                        chatNpc(ChatAnim.SHIFTY, NO_WAY_OUT)
                        chatNpc(ChatAnim.LAUGH, ASK_SERVANTS)
                    }
                    3 -> {
                        chatPlayer(ChatAnim.QUIZ, HOW_TALKING)
                        chatNpc(ChatAnim.QUIZ, NOT_MEOWING)
                        chatPlayer(ChatAnim.SHOCKED, WHY_MEOWING)
                        chatNpc(ChatAnim.NEUTRAL, AMULET)
                        chatNpc(ChatAnim.NEUTRAL, DREADFUL)
                        chatPlayer(ChatAnim.SAD, HEADACHE)
                    }
                    else -> {
                        chatPlayer(ChatAnim.ANGRY, TO_BOB)
                        chatNpc(ChatAnim.NEUTRAL, I_AM_BOB)
                        chatNpc(ChatAnim.NEUTRAL, INCARNATION)
                        chatPlayer(ChatAnim.QUIZ, MIRROR)
                        chatNpc(ChatAnim.NEUTRAL, SERVANT_CALLED, "name" to player.username)
                        chatNpc(ChatAnim.ANGRY, FISH_FOR_ME)
                    }
                }
            }
        }
    }

    /** Talk-to on his servant: the way to the fish, or, once he sleeps, her farewell. */
    suspend fun ScriptContext.talkToServant(world: World, npc: Npc) {
        val (away, island) = islandOf(world, player) ?: return
        if (npc !== island.servant) return
        world.dialogues.start(this, npc) {
            with(EvilBobServantLines) {
                if (island.asleep) {
                    chatPlayer(ChatAnim.HAPPY, FALLEN_ASLEEP)
                    chatNpc(ChatAnim.QUIZ, COME_WHERE)
                    chatPlayer(ChatAnim.NEUTRAL, AWAY)
                    chatNpc(ChatAnim.SAD, DONT_BELONG, "name" to player.username)
                    if (choice(LOVE_YOU, ALRIGHT_OPTION) == 1) {
                        chatPlayer(ChatAnim.SAD, LOVE_YOU)
                        chatNpc(ChatAnim.SAD, NEVER_BE, "name" to player.username)
                        chatNpc(ChatAnim.SAD, GO_NOW)
                    } else {
                        chatPlayer(ChatAnim.HAPPY, ALRIGHT)
                    }
                    return@start
                }
                if (island.shown) {
                    chatNpc(ChatAnim.SCARED, FISH_HE_LIKES)
                    return@start
                }
                chatPlayer(ChatAnim.SHOCKED, KIDNAPPED)
                chatNpc(ChatAnim.SCARED, CANT_HELP)
                chatPlayer(ChatAnim.ANGRY, LISTEN)
                chatNpc(ChatAnim.SCARED, FISH_HE_LIKES)
                chatNpc(ChatAnim.SCARED, OVER_THERE)
                island.shown = true
                cutscene(world, player, away, island.right)
            }
        }
    }

    /** The camera on the right set's statue, under the caption, then back. */
    private suspend fun Dialogue.cutscene(world: World, player: Player, away: EventAreas.Away, spots: Spots) {
        val statue = away.inside(spots.statue.x, spots.statue.z)
        val middle = away.inside(MIDDLE.x, MIDDLE.z)
        world.interfaces.openFloater(player, CUTSCENE_INTERFACE)
        player.session.queue(CamMoveToV3((middle.x + statue.x) / 2, (middle.z + statue.z) / 2, CAMERA_HEIGHT, CAMERA_RATE, CAMERA_RATE2, true))
        player.session.queue(CamLookAtV3(statue.x, statue.z, LOOK_HEIGHT, CAMERA_RATE, CAMERA_RATE2, true))
        delay(CUTSCENE_TICKS)
        player.session.queue(CamReset)
        world.interfaces.closeFloater(player)
    }

    private const val CAMERA_HEIGHT = 800
    private const val LOOK_HEIGHT = 100
    private const val CAMERA_RATE = 10
    private const val CAMERA_RATE2 = 100

    /** Net on a fishing spot: a fishlike thing, the one he likes from the right set. */
    suspend fun ScriptContext.net(world: World, loc: ZoneLoc) {
        val (away, island) = islandOf(world, player) ?: return
        val real = away.outside(loc.x, loc.z)
        val spots = Spots.of(real.x, real.z) ?: return
        if (NETS.none { player.inventory.count(it) > 0 }) return world.message(player, FishingLines.NO_NET)
        if (player.inventory.contents().size >= player.inventory.capacity) return world.message(player, GameLines.NO_SPACE)
        world.message(player, FishingLines.CAST_NET)
        player.anim(Fishing.NET_ANIM)
        delay(CATCH_TICKS)
        if (islandOf(world, player)?.second !== island) return
        player.anim(-1)
        val fish = if (spots == island.right) COOKED_RIGHT else COOKED_WRONG
        val type = world.objTypes[fish] ?: return
        if (player.inventory.add(type, 1) == 0) return world.message(player, GameLines.NO_SPACE)
        world.dialogues.start(this) { objbox(fish, EvilBobLines.CATCH) }
    }

    /** A fishlike thing on the uncooking pot: raw. */
    fun uncook(world: World, player: Player, slot: Int, obj: Int) {
        val raw = when (obj) {
            COOKED_RIGHT -> RAW_RIGHT
            COOKED_WRONG -> RAW_WRONG
            else -> return
        }
        if (player.inventory[slot]?.id != obj) return
        player.inventory[slot] = osrsps.game.InvObj(raw, 1)
    }

    /** Anything else on the pot: burnt food, other cooked food, or nothing at all. */
    suspend fun ScriptContext.uncookOther(world: World, obj: Int) {
        val type = world.objTypes[obj] ?: return
        when {
            type.name.startsWith("Burnt") -> world.dialogues.start(this) { mesbox(UncookingPotLines.BURNT) }
            obj in world.foods.all -> world.dialogues.start(this) {
                mesbox(UncookingPotLines.LEVEL, "level" to UNCOOKING_LEVELS[world.random.nextInt(UNCOOKING_LEVELS.size)])
                mesbox(UncookingPotLines.NO_SKILL)
            }
            else -> world.message(player, GameLines.NOTHING_INTERESTING)
        }
    }

    /** An item used on Evil Bob on his island: a fish he eats, or his complaint. */
    suspend fun ScriptContext.feed(world: World, npc: Npc, slot: Int, obj: Int) {
        val (_, island) = islandOf(world, player) ?: return
        if (npc !== island.bob || island.asleep) return
        world.dialogues.start(this, npc) {
            with(EvilBobLines) {
                when (obj) {
                    COOKED_RIGHT, COOKED_WRONG -> {
                        chatNpc(ChatAnim.ANGRY, COOKED)
                        chatPlayer(ChatAnim.QUIZ, UNCOOK_IT)
                        chatNpc(ChatAnim.ANGRY, COLD_FIRE)
                    }
                    RAW_WRONG -> {
                        if (player.inventory[slot]?.id != obj) return@start
                        player.inventory.remove(slot, 1)
                        island.wanted++
                        chatNpc(ChatAnim.ANGRY, DISGUSTING)
                        chatNpc(ChatAnim.ANGRY, WHAT_KIND)
                        mesbox(MORE_ATTENTIVE)
                    }
                    RAW_RIGHT -> {
                        if (player.inventory[slot]?.id != obj) return@start
                        player.inventory.remove(slot, 1)
                        island.wanted--
                        if (island.wanted > 0) {
                            chatNpc(ChatAnim.HAPPY, THATS_DELICIOUS)
                            chatNpc(ChatAnim.ANGRY, ANOTHER_ONE)
                            mesbox(LESS_ATTENTIVE)
                            return@start
                        }
                        // Asleep before his lines, so shutting them can't lose the hand-in.
                        island.asleep = true
                        npc.effects.say(text(ZZZ))
                        world.npcTypes[ASLEEP]?.let { world.npcs.transform(npc, it, SLEEP_TICKS) }
                        chatNpc(ChatAnim.HAPPY, DELICIOUS)
                        chatNpc(ChatAnim.HAPPY, CATNAP)
                    }
                    else -> chatNpc(ChatAnim.ANGRY, ANYTHING_ELSE)
                }
            }
        }
    }

    /** Enter on the portal: home, with the XP once he sleeps. */
    suspend fun ScriptContext.enterPortal(world: World) {
        val (away, island) = islandOf(world, player) ?: return
        player.anim(RASPBERRY)
        delay(LEAVE_TICKS)
        if (islandOf(world, player)?.second !== island) return
        for (slot in 0 until player.inventory.capacity) if (player.inventory[slot]?.id in FISH + NET) player.inventory.remove(slot)
        world.randomEvents.areas.bringBack(player, away)
        if (!island.asleep) return
        val magic = player.skills.baseLevel(Skill.MAGIC) >= MAGIC_LEVEL && world.random.nextInt(2) == 0
        val skill = if (magic) Skill.MAGIC else Skill.FISHING
        player.skills.addTenths(skill, XP_TENTHS)
        world.message(player, EvilBobLines.BETTER_AT, "skill" to skill.displayName)
    }

    /** The island's locs whose handlers work in its copies. */
    val INSTANCE_LOCS: Set<Int> = setOf(UNCOOKING_POT, FISHING_SPOT, PORTAL)

    fun register(world: World) {
        world.handlers.onNpc(SERVANT, RandomEvents.TALK_TO) { op -> talkToServant(world, op.npc ?: return@onNpc) }
        world.handlers.onNpc(ASLEEP, RandomEvents.TALK_TO) {
            if (islandOf(world, player) != null) world.dialogues.start(this) { mesbox(EvilBobLines.SLEEPING) }
        }
        world.handlers.onObjOnNpc(npcId) { op -> feed(world, op.npc ?: return@onObjOnNpc, op.sub, op.obj) }
        world.handlers.onLoc(FISHING_SPOT, NET_OP) { op -> net(world, op.loc ?: return@onLoc) }
        world.handlers.onObjOnLoc(UNCOOKING_POT, COOKED_RIGHT) { op -> uncook(world, player, op.sub, op.obj) }
        world.handlers.onObjOnLoc(UNCOOKING_POT, COOKED_WRONG) { op -> uncook(world, player, op.sub, op.obj) }
        world.handlers.onObjOnLoc(UNCOOKING_POT) { op -> uncookOther(world, op.obj) }
        world.handlers.onLoc(PORTAL, ENTER) { enterPortal(world) }
        for (fish in listOf(COOKED_RIGHT, COOKED_WRONG)) {
            world.handlers.onObj(fish, EAT) { world.dialogues.start(this) { mesbox(EvilBobLines.NOT_EATING) } }
        }
    }

    private const val EAT = 1

    /** Interface names in the rev 242 cache, for [osrsps.cache.GameFrameCheck]. */
    val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>>
        get() = mapOf(CUTSCENE_INTERFACE to ("macro_evil_bob" to emptyMap()))
}
