package osrsps.game.content.randomevents

import osrsps.game.GroundObjs
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.content.XpLamp
import osrsps.game.content.message
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext
import osrsps.map.Tile

/** The Freaky Forester's lines; the words are in the dialogue file, from his transcript and page (read 2026-11-19). */
object FreakyForesterLines : Lines("freaky_forester", "Transcript:Freaky Forester and the Freaky Forester page") {
    val NAME = line("name", "Overhead dialogue: dialogue 1", "name")
    val HELLO = line("hello", "Overhead dialogue: dialogue 2", "name")
    val TALK_TO_ME = line("talk_to_me", "Overhead dialogue: dialogue 3", "name")
    val ARE_YOU_THERE = line("are_you_there", "Overhead dialogue: dialogue 4", "name")
    val LIKE_TO_TALK = line("like_to_talk", "Overhead dialogue: dialogue 5", "name")
    val MAYBE_LATER = line("maybe_later", "Overhead dialogue: if the player does not respond in time")
    val HELP = line("help", "Initial dialogue: his question", "name")
    val OKAY = line("okay", "Initial dialogue: the first option, and the player's words")
    val BUSY = line("busy", "Initial dialogue: the second option, and the player's words")
    val LOOKING_FOR = line("looking_for", "Other people's Freaky Forester", "name")
    val ASK_WORDS = line("ask_words", "In the forest: dialogue 1, {tails} the number in words (two, three, four)", "name", "tails")
    val ASK_KILL = line("ask_kill", "In the forest: dialogue 2, {tails} the number in figures", "name", "tails")
    val ASK_NEED = line("ask_need", "In the forest: dialogue 3, {tails} the number in figures", "name", "tails")
    val BRING_IT = line("bring_it", "After killing the assigned pheasant")
    val THANKS = line("thanks", "After killing the assigned pheasant: the pheasant given", "name")
    val LEAVE_NOW = line("leave_now", "After killing the assigned pheasant, talking to Freaky Forester again", "name")
    val NO_MORE_PHEASANTS = line("no_more_pheasants", "Attempting to give the Freaky Forester a second pheasant")
    val REWARD = line("reward", "Leaving the event area: without the whole outfit, {piece} hat, top or shorts", "piece")
    val REWARD_LAMP = line("reward_lamp", "Leaving the event area: with the whole outfit")
    val ALLOWED_TO_LEAVE = line("allowed_to_leave", "the message attacking more pheasants after handing one in")
    val ALREADY_KILLED = line("already_killed", "the message attacking a pheasant with a carcass held (the Freaky Forester page)")
}

/**
 * The Freaky Forester (OSRS Wiki, Freaky Forester and Pheasant pages and the transcript, read 2026-11-19). He comes as
 * NPC 6748 (macro_forester_invitation; Talk-to, Dismiss) and asks for help; yes takes the player to the Freaky Forest
 * (map square 40,74, [EventAreas]), where he (372, macro_forester_m) asks for a pheasant with two, three or four tails
 * (never one). Every pheasant gives the same raw pheasant (obj 11704) and he takes any, for a piece of the Lederhosen
 * outfit the player hasn't got yet, or a lamp once they have it all, given as they leave by the portal. The portal
 * leaves at any time, with nothing for an unfinished event. Pheasants (373, 5500, 374, 5502 by their tails) have Kill,
 * not Attack: the first blow kills, for no experience, and they come back 5 ticks on.
 *
 * Guessed: where the player lands and where the eight pheasants (two of each) stand; that pheasants stay where they are
 * put; the Kill as an unarmed punch with the pheasant's own death animation; which missing piece is given (one at
 * random); what counts as having a piece (backpack, worn or banked); that leaving takes any raw pheasant still held; the
 * talk on arrival following straight on (the transcript goes on after the teleport); that "Sorry, I'm busy" sends him
 * off. Left out: the second carcass's pick-up message, and being put in combat by a Kill.
 */
object FreakyForester : RandomEvent("freaky_forester", 6748) {
    const val FOREST_FORESTER = 372
    const val RAW_PHEASANT = 11704
    const val PORTAL = 20843
    const val USE = 1
    const val KILL = 2
    const val PHEASANT_DEATH = 2373

    /** human_unarmedpunch. */
    const val PUNCH = 422
    const val RESPAWN_TICKS = 5
    const val DEATH_TICKS = 2
    const val HAT = 6182
    const val TOP = 6180
    const val SHORTS = 6181

    /** The pheasants by their tails, 1 to 4. */
    val PHEASANTS = mapOf(373 to 1, 5500 to 2, 374 to 3, 5502 to 4)

    /** The zones copied: the clearing of map square 40,74. */
    const val FROM_X = 2584
    const val FROM_Z = 4760
    const val WIDTH_ZONES = 4
    const val HEIGHT_ZONES = 3

    val ARRIVE = Tile(2602, 4776)
    val FORESTER_AT = Tile(2599, 4775)
    val PHEASANT_SPAWNS: List<Pair<Int, Tile>> = listOf(
        373 to Tile(2597, 4770), 373 to Tile(2607, 4779),
        5500 to Tile(2605, 4771), 5500 to Tile(2596, 4778),
        374 to Tile(2602, 4779), 374 to Tile(2604, 4768),
        5502 to Tile(2607, 4774), 5502 to Tile(2600, 4772),
    )

    private val TAILS_WORDS = mapOf(2 to "two", 3 to "three", 4 to "four")

    /** A stay in the forest. */
    class Forest(val tails: Int, val forester: Npc?) {
        var killed = false
        var handedIn = false

        /** The exit gave the reward (the hand-in stays spent, so no second pheasant earns another). */
        var rewarded = false

        /** Pheasants killed and not yet gone. */
        val dying = HashSet<Npc>()
    }

    override val teleports = true
    override val nags = with(FreakyForesterLines) { listOf(NAME, HELLO, TALK_TO_ME, ARE_YOU_THERE, LIKE_TO_TALK) }
    override val leaving = FreakyForesterLines.MAYBE_LATER
    override val notYours = FreakyForesterLines.LOOKING_FOR

    override suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean = with(FreakyForesterLines) {
        chatNpc(ChatAnim.HAPPY, HELP, "name" to player.username)
        if (choice(OKAY, BUSY) != 1) {
            chatPlayer(ChatAnim.NEUTRAL, BUSY)
            return true
        }
        chatPlayer(ChatAnim.HAPPY, OKAY)
        val away = events.areas.takeAway(player, this@FreakyForester, FROM_X, FROM_Z, WIDTH_ZONES, HEIGHT_ZONES, ARRIVE) ?: return true
        val forest = Forest(2 + world.random.nextInt(3), spawn(world, away, FOREST_FORESTER, FORESTER_AT))
        away.state = forest
        for ((id, at) in PHEASANT_SPAWNS) spawn(world, away, id, at)
        delay(2)
        val forester = forest.forester ?: return true
        ask(world, forester, forest)
        true
    }

    private suspend fun Dialogue.ask(world: World, forester: Npc, forest: Forest) = with(FreakyForesterLines) {
        val name = "name" to player.username
        when (world.random.nextInt(3)) {
            0 -> chatNpc(forester, ChatAnim.NEUTRAL, text(ASK_WORDS, name, "tails" to TAILS_WORDS.getValue(forest.tails)))
            1 -> chatNpc(forester, ChatAnim.NEUTRAL, text(ASK_KILL, name, "tails" to forest.tails))
            else -> chatNpc(forester, ChatAnim.NEUTRAL, text(ASK_NEED, name, "tails" to forest.tails))
        }
    }

    private fun spawn(world: World, away: EventAreas.Away, id: Int, at: Tile): Npc? {
        val type = world.npcTypes[id] ?: return null
        val tile = away.inside(at.x, at.z)
        return world.spawnNpc(NpcSpawn(type, tile.x, tile.z, 0, source = "freaky forest for ${away.owner}", respawns = false))
    }

    private fun forestOf(world: World, player: Player): Pair<EventAreas.Away, Forest>? {
        val away = world.randomEvents.areas.awayOf(player) ?: return null
        val forest = away.state as? Forest ?: return null
        return away to forest
    }

    /** Talk-to on the Forester in the forest. */
    suspend fun ScriptContext.talkInForest(world: World, npc: Npc) {
        val (_, forest) = forestOf(world, player) ?: return
        world.dialogues.start(this, npc) {
            with(FreakyForesterLines) {
                when {
                    forest.handedIn -> chatNpc(ChatAnim.HAPPY, LEAVE_NOW, "name" to player.username)
                    player.inventory.count(RAW_PHEASANT) > 0 -> {
                        val slot = (0 until player.inventory.capacity).first { player.inventory[it]?.id == RAW_PHEASANT }
                        player.inventory.remove(slot, 1)
                        forest.handedIn = true
                        chatNpc(ChatAnim.HAPPY, THANKS, "name" to player.username)
                    }
                    forest.killed -> chatNpc(ChatAnim.NEUTRAL, BRING_IT)
                    else -> ask(world, npc, forest)
                }
            }
        }
    }

    /** Use on the raw pheasant on him once one is handed in. */
    suspend fun ScriptContext.secondPheasant(world: World, npc: Npc) {
        val (_, forest) = forestOf(world, player) ?: return
        if (!forest.handedIn) return talkInForest(world, npc)
        world.dialogues.start(this, npc) { chatNpc(ChatAnim.NEUTRAL, FreakyForesterLines.NO_MORE_PHEASANTS) }
    }

    /** Kill on a pheasant: refused once one is handed in or one is held; else the first blow kills it. */
    fun kill(world: World, player: Player, npc: Npc) {
        val (away, forest) = forestOf(world, player) ?: return
        if (npc.dead || npc in forest.dying) return
        when {
            forest.handedIn -> return world.message(player, FreakyForesterLines.ALLOWED_TO_LEAVE)
            player.inventory.count(RAW_PHEASANT) > 0 -> return world.message(player, FreakyForesterLines.ALREADY_KILLED)
        }
        player.anim(PUNCH)
        forest.dying += npc
        npc.effects.anim(PHEASANT_DEATH)
        forest.killed = true
        val spawnAt = away.outside(npc.spawn.x, npc.spawn.z)
        world.npcScripts.later(DEATH_TICKS) {
            val type = world.objTypes[RAW_PHEASANT]
            if (type != null) world.groundObjs.addPrivate(type, 1, npc.x, npc.z, npc.level, player.saveKey, GroundObjs.DROP_PRIVATE_TICKS, GroundObjs.DROP_DESPAWN_TICKS)
            world.removeNpc(npc)
            forest.dying -= npc
        }
        world.npcScripts.later(DEATH_TICKS + RESPAWN_TICKS) {
            if (world.instances.all.none { it === away.instance }) return@later
            spawn(world, away, npc.type.id, spawnAt)
        }
    }

    /** Use on the exit portal: out, with the reward if a pheasant was handed in. */
    suspend fun ScriptContext.leave(world: World) {
        val (away, forest) = forestOf(world, player) ?: return
        for (slot in 0 until player.inventory.capacity) if (player.inventory[slot]?.id == RAW_PHEASANT) player.inventory.remove(slot)
        if (forest.handedIn && !forest.rewarded) {
            // The reward is given and the hand-in spent before his line, so shutting it neither loses nor repeats the
            // reward; the portal then simply sends the player out.
            val missing = listOf(HAT to "hat", TOP to "top", SHORTS to "shorts").filter { (id, _) -> !owns(player, id) }
            val forester = forest.forester
            val piece = missing.takeIf { it.isNotEmpty() }?.let { it[world.random.nextInt(it.size)] }
            world.randomEvents.reward(player, piece?.first ?: XpLamp.LAMP)
            forest.rewarded = true
            if (forester != null && !forester.dead) {
                world.dialogues.start(this, forester) {
                    if (piece != null) chatNpc(ChatAnim.HAPPY, FreakyForesterLines.REWARD, "piece" to piece.second)
                    else chatNpc(ChatAnim.HAPPY, FreakyForesterLines.REWARD_LAMP)
                }
            }
        }
        world.randomEvents.areas.bringBack(player, away)
    }

    private fun owns(player: Player, id: Int) = player.inventory.count(id) > 0 || player.equipment.count(id) > 0 || player.bank.count(id) > 0

    /** The forest's locs whose handlers work in its copies. */
    val INSTANCE_LOCS: Set<Int> = setOf(PORTAL)

    fun register(world: World) {
        world.handlers.onNpc(FOREST_FORESTER, RandomEvents.TALK_TO) { op -> talkInForest(world, op.npc ?: return@onNpc) }
        for (id in PHEASANTS.keys) world.handlers.onNpc(id, KILL) { op -> kill(world, player, op.npc ?: return@onNpc) }
        world.handlers.onObjOnNpc(FOREST_FORESTER, RAW_PHEASANT) { op -> secondPheasant(world, op.npc ?: return@onObjOnNpc) }
        world.handlers.onLoc(PORTAL, USE) { leave(world) }
    }
}
