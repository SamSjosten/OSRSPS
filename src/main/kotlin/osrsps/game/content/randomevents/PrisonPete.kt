package osrsps.game.content.randomevents

import net.rsprot.protocol.game.outgoing.interfaces.IfSetModelV2
import osrsps.game.FrameSlot
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.content.WildernessLevers
import osrsps.game.content.XpLamp
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.QueueType
import osrsps.game.script.ScriptContext
import osrsps.map.Tile

/** Prison Pete's lines; the words are in the dialogue file, from Evil Bob's transcript (read 2026-11-19). */
object PrisonPeteLines : Lines("prison_pete", "Transcript:Evil Bob") {
    val PET = line("pet", "Prison Pete version: Evil Bob's question")
    val CAPTURED_TOO = line("captured_too", "Continuation after getting teleported: his first line")
    val CAPTURED = line("captured", "Continuation after getting teleported: the player's first line")
    val STUCK_ME = line("stuck_me", "Continuation after getting teleported: his second line")
    val HOW_GET_OUT = line("how_get_out", "The player's question (also the talk's second option)")
    val BALLOON_KEYS = line("balloon_keys", "His explanation: the first line")
    val PULL_LEVER = line("pull_lever", "His explanation: the second line")
    val BRING_KEYS = line("bring_keys", "His explanation: the last line")
    val OKAY = line("okay", "After the explanation: the first option, and the player's words")
    val LEAVE_NOW_OPTION = line("leave_now_option", "After the explanation: the second option")
    val LEAVE_NOW = line("leave_now", "But I want to leave now: the player's words")
    val STILL_STUCK = line("still_stuck", "But I want to leave now: his answer")
    val YES = line("yes", "Leaving: the first option")
    val NO = line("no", "Leaving: the second option")
    val WHAT_PLACE = line("what_place", "Talked to again: the first option, and the player's words")
    val DONT_REMEMBER = line("dont_remember", "What is this place?: his first line")
    val MY_RIGHTS = line("my_rights", "What is this place?: the player's answer")
    val DOESNT_CARE = line("doesnt_care", "What is this place?: his second line")
    val COME_ON = line("come_on", "After unlocking all gates")
    val THIS_EXIT = line("this_exit", "When trying to use the main door")
    val PULL_FIRST = line("pull_first", "When attempting to pop a balloon animal before pulling the lever")
    val THATS_RIGHT = line("thats_right", "When pulling the lever")
    val GOT_KEY = line("got_key", "When popping a balloon animal: his first line")
    val OOH_THANKS = line("ooh_thanks", "When popping a balloon animal: his line as he takes the key")
    val WRONG_KEY = line("wrong_key", "The key from the wrong shape of balloon animal")
    val RIGHT_ONE = line("right_one", "The right key, not the last gate")
    val ALL_KEYS = line("all_keys", "The right key for the last gate: his line")
    val GET_OUT = line("get_out", "The right key for the last gate: the player's answer")
    val BRING_THAT_KEY = line("bring_that_key", "Popping with a prison key in the inventory")
    val PRESENT = line("present", "When leaving through the unlocked gates: his line")
    val SEE_YOU = line("see_you", "When leaving through the unlocked gates: the player's answer")
}

/**
 * Prison Pete (OSRS Wiki, Prison Pete, Balloon Animal, Prison key (Prison Pete) and ScapeRune pages and Evil Bob's
 * transcript, read 2026-11-19). Evil Bob comes in his prison form, NPC 6754 (macro_evil_bob_prison; Talk-to,
 * Dismiss), and asks if the player wants to meet his pet; yes takes them to ScapeRune's prison (the Falador Party
 * Room's facsimile, map square 32,69, [EventAreas]), where Prison Pete (368) explains. The lever (24296) shows which
 * balloon animal holds the right key (interface prisonpete, its model set to the shape's: dog, cat, sheep or goat,
 * the balloon NPCs' models 16034, 10736, 10737 and 27098); popping one (Pop) gives a prison key (6966), which Pete takes and tries. Three
 * energy barriers (prisonpete_door_1 to _3, 20840 to 20842, shown by varbit macro_transmitted) stand in the exit:
 * each right key takes one away, each wrong one puts one back while fewer than three stand, so three right in a row
 * frees him. Walking out through the opened exit takes the player home, where Pete gives a present (the Wiki's
 * members table, 5,656 logged). Talking to Pete and saying they want to leave takes them home with nothing.
 *
 * Guessed: where the player lands; Pete standing still where the Wiki's map has him; eight balloon animals, two of each
 * shape (one of each colour), wandering the room and coming back [POP_RESPAWN_TICKS] after a pop; the lever pulled
 * with the Wilderness levers' animation, picking a shape at random each pull, and a key needing a fresh pull after Pete
 * tries it; the key's shape being the popped animal's; the snapdragons' weight as what is left of the 5,656 after the
 * others (the Wiki gives Mod Ash's 7/50); leaving once the player walks into the first barrier's tiles. Inferred: the
 * barrier count shown by macro_transmitted (0 for all three, 3 for none, by the doors' multilocs); the talk's stay
 * and leave options the same as the first talk's. Left out: the walk to Pete after a pop, the
 * jingles, a dropped key vanishing, the protection on death, the spell message and Pete's anger at littering.
 */
object PrisonPete : RandomEvent("prison_pete", 6754) {
    const val PETE = 368
    const val LEVER = 24296
    const val PULL = 1
    const val POP = 1
    const val RETURN = 1
    const val OPEN = 1
    const val KEY = 6966
    val DOORS = listOf(20840, 20841, 20842)
    val MAIN_DOORS = listOf(24293, 24294)
    const val GATES = 3
    const val POP_RESPAWN_TICKS = 10

    const val INTERFACE = 273
    const val PICTURE = 4

    /** macro_transmitted: how many barriers are gone. */
    const val BARRIERS_VARBIT = 1547

    /** The four shapes: each one's two NPCs (two colours) and the model the lever's window shows. */
    enum class Animal(val npcs: List<Int>, val model: Int) {
        DOG(listOf(5488, 370), 16034),
        CAT(listOf(5489, 371), 10736),
        SHEEP(listOf(5493, 369), 10737),
        GOAT(listOf(5491, 5492), 27098),
        ;

        companion object {
            fun of(npc: Int): Animal? = entries.firstOrNull { npc in it.npcs }
        }
    }

    val BALLOONS = Animal.entries.flatMap { it.npcs }

    /** The zones copied: the prison of map square 32,69. */
    const val FROM_X = 2072
    const val FROM_Z = 4456
    const val WIDTH_ZONES = 4
    const val HEIGHT_ZONES = 3

    val ARRIVE = Tile(2086, 4466)
    val PETE_AT = Tile(2093, 4466)
    val LEVER_AT = Tile(2094, 4464)
    val DOOR_TILES = listOf(Tile(2098, 4466), Tile(2099, 4466), Tile(2100, 4466))
    const val OUT_X = 2098
    const val BALLOON_WANDER = 4
    val BALLOONS_AT = listOf(
        Tile(2079, 4469), Tile(2083, 4462), Tile(2088, 4469), Tile(2092, 4462),
        Tile(2080, 4462), Tile(2085, 4470), Tile(2090, 4468), Tile(2084, 4465),
    )

    /** The present, by the Wiki's members table: item, count range, weight out of 5,656. */
    class Present(val obj: Int, val min: Int, val max: Int, val weight: Int)

    val PRESENTS = listOf(
        Present(XpLamp.LAMP, 1, 1, 1194),
        Present(1620, 4, 4, 580), // uncut rubies, noted
        Present(1618, 3, 3, 631), // uncut diamonds, noted
        Present(42, 45, 50, 49), // mithril arrowtips
        Present(1885, 2, 2, 65), // ugthanki kebabs
        Present(562, 19, 25, 569), // chaos runes
        Present(563, 7, 10, 555), // law runes
        Present(3050, 6, 6, 1225), // grimy toadflax, noted
        Present(3052, 4, 4, 788), // grimy snapdragon, noted
    )

    /** A stay in the prison: the shape the lever showed, the key's shape, and the barriers gone. */
    class Prison(val pete: Npc?) {
        var target: Animal? = null
        var key: Animal? = null
        var open = 0
        var free = false
    }

    override val teleports = true
    override val placeItems = setOf(KEY)
    override val nags = EvilBob.nags
    override val leaving = EvilBob.leaving
    override val notYours = EvilBob.notYours
    override val notYoursInBox = true
    override val gaveUp = EvilBob.gaveUp

    override suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean {
        chatNpc(ChatAnim.NEUTRAL, PrisonPeteLines.PET)
        if (choice(EvilBobLines.YES, EvilBobLines.NO, title = text(EvilBobLines.FOLLOW)) != 1) {
            mesbox(EvilBobLines.LEAVE_ALONE)
            return true
        }
        val away = events.areas.takeAway(player, this@PrisonPete, FROM_X, FROM_Z, WIDTH_ZONES, HEIGHT_ZONES, ARRIVE) ?: return true
        val prison = Prison(spawn(world, away, PETE_AT, PETE, 0))
        away.state = prison
        for ((i, tile) in BALLOONS_AT.withIndex()) spawn(world, away, tile, BALLOONS[i], BALLOON_WANDER)
        setBarriers(player, 0)
        delay(2)
        val pete = prison.pete ?: return true
        with(PrisonPeteLines) {
            chatNpc(pete, ChatAnim.QUIZ, text(CAPTURED_TOO))
            chatPlayer(ChatAnim.QUIZ, CAPTURED)
            chatNpc(pete, ChatAnim.SAD, text(STUCK_ME))
        }
        explain(world, pete, away)
        return true
    }

    private fun spawn(world: World, away: EventAreas.Away, at: Tile, id: Int, wander: Int): Npc? {
        val type = world.npcTypes[id] ?: return null
        val tile = away.inside(at.x, at.z)
        return world.spawnNpc(NpcSpawn(type, tile.x, tile.z, 0, wander = wander, source = "prison for ${away.owner}", respawns = false))
    }

    /** The stay [player] is in and its prison, if they are in one. */
    fun prisonOf(world: World, player: Player): Pair<EventAreas.Away, Prison>? {
        val away = world.randomEvents.areas.awayOf(player) ?: return null
        val prison = away.state as? Prison ?: return null
        return away to prison
    }

    private fun setBarriers(player: Player, open: Int) {
        if (player.vars.types.varbit(BARRIERS_VARBIT) != null) player.vars.setVarbit(BARRIERS_VARBIT, open)
    }

    /** The player asking the way out ([PrisonPeteLines.HOW_GET_OUT]), his answer, and the choice to stay or go. */
    private suspend fun Dialogue.explain(world: World, pete: Npc, away: EventAreas.Away) = with(PrisonPeteLines) {
        chatPlayer(ChatAnim.ANGRY, HOW_GET_OUT)
        chatNpc(pete, ChatAnim.NEUTRAL, text(BALLOON_KEYS))
        chatNpc(pete, ChatAnim.NEUTRAL, text(PULL_LEVER))
        chatNpc(pete, ChatAnim.NEUTRAL, text(BRING_KEYS))
        if (choice(OKAY, LEAVE_NOW_OPTION) == 1) {
            chatPlayer(ChatAnim.NEUTRAL, OKAY)
            return
        }
        chatPlayer(ChatAnim.NEUTRAL, LEAVE_NOW)
        chatNpc(pete, ChatAnim.SAD, text(STILL_STUCK))
        if (choice(YES, NO) == 1) leave(world, player, away)
    }

    /** Talk-to on Pete in the prison. */
    suspend fun ScriptContext.talkToPete(world: World, npc: Npc) {
        val (away, prison) = prisonOf(world, player) ?: return
        if (npc !== prison.pete) return
        world.dialogues.start(this, npc) {
            with(PrisonPeteLines) {
                if (prison.free) return@start chatNpc(ChatAnim.HAPPY, COME_ON)
                if (choice(WHAT_PLACE, HOW_GET_OUT) == 1) {
                    chatPlayer(ChatAnim.QUIZ, WHAT_PLACE)
                    chatNpc(ChatAnim.NEUTRAL, DONT_REMEMBER)
                    chatPlayer(ChatAnim.ANGRY, MY_RIGHTS)
                    chatNpc(ChatAnim.SAD, DOESNT_CARE)
                }
                explain(world, npc, away)
            }
        }
    }

    /** Pull on the lever: the shape to pop, shown in its window. */
    suspend fun ScriptContext.pull(world: World) {
        val (_, prison) = prisonOf(world, player) ?: return
        val pete = prison.pete ?: return
        if (prison.free) return world.dialogues.start(this, pete) { chatNpc(ChatAnim.HAPPY, PrisonPeteLines.COME_ON) }
        player.anim(WildernessLevers.PULL_ANIM)
        val animal = Animal.entries[world.random.nextInt(Animal.entries.size)]
        prison.target = animal
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, INTERFACE)
        player.session.queue(IfSetModelV2(INTERFACE, PICTURE, animal.model))
        world.dialogues.start(this, pete) { chatNpc(ChatAnim.NEUTRAL, PrisonPeteLines.THATS_RIGHT) }
    }

    /** Pop on a balloon animal: a key, which Pete tries. */
    suspend fun ScriptContext.pop(world: World, npc: Npc) {
        val (away, prison) = prisonOf(world, player) ?: return
        val pete = prison.pete ?: return
        val animal = Animal.of(npc.type.id) ?: return
        with(PrisonPeteLines) {
            when {
                prison.free -> return world.dialogues.start(this@pop, pete) { chatNpc(ChatAnim.HAPPY, COME_ON) }
                player.inventory.count(KEY) > 0 -> return world.dialogues.start(this@pop, pete) { chatNpc(ChatAnim.ANGRY, BRING_THAT_KEY) }
                prison.target == null -> return world.dialogues.start(this@pop, pete) { chatNpc(ChatAnim.ANGRY, PULL_FIRST) }
            }
        }
        val key = world.objTypes[KEY] ?: return
        if (player.inventory.add(key, 1) == 0) return
        prison.key = animal
        val spawnTile = Tile(npc.spawn.x, npc.spawn.z)
        val id = npc.spawn.type.id
        world.removeNpc(npc)
        world.npcScripts.later(POP_RESPAWN_TICKS) {
            if (prisonOf(world, player)?.second === prison) {
                val type = world.npcTypes[id] ?: return@later
                world.spawnNpc(NpcSpawn(type, spawnTile.x, spawnTile.z, 0, wander = BALLOON_WANDER, source = "prison for ${away.owner}", respawns = false))
            }
        }
        world.dialogues.start(this, pete) {
            chatNpc(ChatAnim.HAPPY, PrisonPeteLines.GOT_KEY)
            tryKey(world, away, prison)
        }
    }

    /** Return on the key: Pete tries it. */
    suspend fun ScriptContext.returnKey(world: World) {
        val (away, prison) = prisonOf(world, player) ?: return
        val pete = prison.pete ?: return
        world.dialogues.start(this, pete) { tryKey(world, away, prison) }
    }

    private suspend fun Dialogue.tryKey(world: World, away: EventAreas.Away, prison: Prison) = with(PrisonPeteLines) {
        val slot = (0 until player.inventory.capacity).firstOrNull { player.inventory[it]?.id == KEY } ?: return
        chatNpc(ChatAnim.HAPPY, OOH_THANKS)
        player.inventory.remove(slot, 1)
        val right = prison.key != null && prison.key == prison.target
        prison.key = null
        prison.target = null
        if (!right) {
            prison.open = (prison.open - 1).coerceAtLeast(0)
            setBarriers(player, prison.open)
            chatNpc(ChatAnim.SAD, WRONG_KEY)
            return
        }
        prison.open++
        setBarriers(player, prison.open)
        if (prison.open < GATES) {
            chatNpc(ChatAnim.HAPPY, RIGHT_ONE)
            return
        }
        prison.free = true
        openExit(world, player, away, prison)
        chatNpc(ChatAnim.HAPPY, ALL_KEYS)
        chatPlayer(ChatAnim.NEUTRAL, GET_OUT)
    }

    /** The barriers gone from the map, and the player home with a present once they walk out through them. */
    private fun openExit(world: World, player: Player, away: EventAreas.Away, prison: Prison) {
        for ((i, tile) in DOOR_TILES.withIndex()) {
            val inside = away.inside(tile.x, tile.z)
            world.locs.at(inside.x, inside.z, 0).firstOrNull { it.id == DOORS[i] }?.let { world.locs.remove(it) }
        }
        world.npcScripts.laterUntil(1) {
            if (prisonOf(world, player)?.second !== prison || player !in world.onlinePlayers) return@laterUntil true
            if (away.outside(player.x, player.z).x < OUT_X) return@laterUntil false
            world.scripts.queue(player, "prison_pete_free", QueueType.STRONG, 0) { freed(world, away) }
            true
        }
    }

    private suspend fun ScriptContext.freed(world: World, away: EventAreas.Away) {
        leave(world, player, away)
        // The present before his line, so shutting it loses nothing.
        present(world, player)
        val type = world.npcTypes[PETE] ?: return
        world.dialogues.start(this) {
            chatNpc(type, type.name, ChatAnim.HAPPY, PrisonPeteLines.PRESENT)
            chatPlayer(ChatAnim.HAPPY, PrisonPeteLines.SEE_YOU)
        }
    }

    private fun present(world: World, player: Player) {
        var roll = world.random.nextInt(PRESENTS.sumOf { it.weight })
        val present = PRESENTS.first { roll -= it.weight; roll < 0 }
        world.randomEvents.reward(player, present.obj, present.min + world.random.nextInt(present.max - present.min + 1))
    }

    private fun leave(world: World, player: Player, away: EventAreas.Away) {
        for (slot in 0 until player.inventory.capacity) if (player.inventory[slot]?.id == KEY) player.inventory.remove(slot)
        setBarriers(player, 0)
        world.randomEvents.areas.bringBack(player, away)
    }

    /** The prison's locs whose handlers work in its copies. */
    val INSTANCE_LOCS: Set<Int> = (MAIN_DOORS + LEVER).toSet()

    fun register(world: World) {
        world.handlers.onNpc(PETE, RandomEvents.TALK_TO) { op -> talkToPete(world, op.npc ?: return@onNpc) }
        for (id in BALLOONS) world.handlers.onNpc(id, POP) { op -> pop(world, op.npc ?: return@onNpc) }
        world.handlers.onLoc(LEVER, PULL) { pull(world) }
        for (id in MAIN_DOORS) world.handlers.onLoc(id, OPEN) {
            val pete = prisonOf(world, player)?.second?.pete ?: return@onLoc
            world.dialogues.start(this, pete) { chatNpc(ChatAnim.ANGRY, PrisonPeteLines.THIS_EXIT) }
        }
        world.handlers.onObj(KEY, RETURN) { returnKey(world) }
    }

    /** Interface names in the rev 242 cache, for [osrsps.cache.GameFrameCheck]. */
    val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>>
        get() = mapOf(INTERFACE to ("prisonpete" to mapOf(PICTURE to "balloon1")))
}
