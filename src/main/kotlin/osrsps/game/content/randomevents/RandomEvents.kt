package osrsps.game.content.randomevents

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.math.abs
import osrsps.game.GroundObjs
import osrsps.game.Player
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.content.KingBlackDragon
import osrsps.game.content.Scurrius
import osrsps.game.content.ShantayPass
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext

private val logger = KotlinLogging.logger {}

/**
 * One random event: who comes ([npcId], whose Talk-to is op 1 and Dismiss op 5, as the cache gives every event NPC),
 * what they say overhead while they wait for their player, and what Talk-to does.
 */
abstract class RandomEvent(
    /** Its name in `::randomevent`. */
    val key: String,
    val npcId: Int,
) {
    /** Whether it takes the player to its own area: those never come in the Wilderness (OSRS Wiki, Random events). */
    open val teleports: Boolean = false

    /** Whether its NPC stays when its player walks into a bank: only Count Check, Dr Jekyll and the Genie do (Wiki). */
    open val entersBanks: Boolean = false

    /** The NPCs that may come for it, one at random each time (the three certer brothers); [npcId] alone for most. */
    open val npcIds: List<Int> get() = listOf(npcId)

    /** Whether its NPC follows its player; one that doesn't (the Strange plant) stays where it came up. */
    open val follows: Boolean = true

    /** Overhead as it appears, with `{name}`, the player's name; null for none. */
    open val greeting: Line? = null

    /** Overhead while it waits, one at random each time; may hold `{name}`. */
    open val nags: List<Line> = emptyList()

    /** Overhead as it gives up and goes; null for none. */
    open val leaving: Line? = null

    /** Talk-to by anyone but its player, with `{name}` their player's name; null for nothing at all. */
    open val notYours: Line? = null

    /** A message box for Talk-to by its player once it has given up and is going; null for nothing at all. */
    open val gaveUp: Line? = null

    /** Whether [notYours] shows in a message box rather than as the NPC's words (Evil Bob's does). */
    open val notYoursInBox: Boolean = false

    /** Items that belong to its place and stay there: taken as the player leaves it, by any way out, logging out too. */
    open val placeItems: Set<Int> = emptySet()

    /** Called once its NPC is out, before its first words. */
    open fun started(world: World, out: RandomEvents.Out) {}

    /** Talk-to by its player, which ends the event when it returns true: its NPC then goes. */
    abstract suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean

    /** Talk-to on one of [npcIds] that is no event's NPC out in the world: Evil Bob on his own island. */
    open suspend fun ScriptContext.talkElsewhere(world: World, npc: Npc) {}
}

/**
 * Random events (OSRS Wiki, Random events, read 2026-11-19, which cites Mod Ash for each rule):
 *
 * - Each account has a time its next event is due, 60 to 120 minutes after its last, kept on a real clock so it runs
 *   down while the player is offline too. A player who is offline when it comes gets it moved 10 to 70 minutes on.
 * - The game looks every 5 minutes of game time; once the time has come, an event is picked with equal weight from
 *   those allowed where the player stands. With none allowed there the chance is missed and a new time is set.
 * - An open interface or a Make-X run holds the event back until the player is free (it turns up soon after).
 * - Events that take the player away never come in the Wilderness; none come in banks, boss areas or minigames, and an
 *   NPC following its player into one goes and doesn't come back, but for Count Check, Dr Jekyll and the Genie in a
 *   bank.
 * - No second event NPC while one is still about for the player.
 * - The NPC appears beside its player and follows them, and only they can talk to it; ignored, it goes after a minute.
 *
 * Inferred: the 5-minute look counts the player's own logged-in ticks ([Player.playedTicks]); a player found offline
 * gets one 10 to 70 minute move from the moment they log back in; the times are whole minutes, kept in
 * [Player.charges]. Guessed: what counts as a bank (a loc whose name starts "Bank" with a Bank or Use option within
 * [BANK_RANGE] tiles), a boss area (the King Black Dragon's lair and Scurrius's lair) and a minigame (any instance,
 * and the Archive raid's lobby); that an event that takes the player away doesn't come, and its NPC goes, while they are
 * in combat, Tele Blocked or in Shantay's cell, so it is never a way out (exploit review R50); that none come during a
 * test setup (the Leagues Tutor's or `::scenario`), whose snapshot would otherwise wind the timer back; the NPC's tile (the
 * first free tile west, east, south or north of the player, then the corners); a nag every [NAG_TICKS] ticks (the
 * pre-2014 Genie's 12 seconds); the NPC catching up by stepping beside its player when they get more than
 * [CATCH_UP] tiles away or change level; its leaving line shown for [LEAVE_TICKS] ticks before it goes; Dismiss
 * sending it off at once without a word; a reward that doesn't fit going on the ground under the player. Transforming
 * (a ring of stone, a greegree) isn't built, so it can't hold an event back yet.
 *
 * Testing tools, not live's: `::randomevent` starts any event at once, makes the next due, or turns events off for an
 * account (saved); see [osrsps.game.command.registerRandomEventCommands]. Every roll is [World.random]'s, so a fixed
 * seed (`::seed`) replays the same times, events and puzzles.
 */
class RandomEvents(private val world: World, private val clock: () -> Long) {
    companion object {
        const val CHECK_TICKS = 500
        const val DUE_MIN_MINUTES = 60
        const val DUE_MAX_MINUTES = 120
        const val OFFLINE_MIN_MINUTES = 10
        const val OFFLINE_MAX_MINUTES = 70

        /** How long an ignored NPC stays: a minute. */
        const val LINGER_TICKS = 100
        const val NAG_TICKS = 20
        const val LEAVE_TICKS = 2
        const val CLAIMED_TICKS = 10
        const val CATCH_UP = 10
        const val BANK_RANGE = 4

        const val TALK_TO = 1
        const val DISMISS = 5

        /** When the next event is due, in minutes since 1970; 0 before the first login with events. */
        const val DUE = "random_event_due"

        /** 1 while `::randomevent off` holds events back for the account. */
        const val OFF = "random_events_off"

        /** The charges that are the account's, not its loadout's: a scenario keeps them. */
        val ACCOUNT_CHARGES = listOf(DUE, OFF)

        /** The tiles beside a player the NPC may appear on, in the order tried. */
        private val AROUND = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, 1 to -1, -1 to 1, 1 to 1)
    }

    /** One event NPC out for its player. */
    class Out(val owner: String, val ownerName: String, val event: RandomEvent, val npc: Npc, val since: Long) {
        var nextNag: Long = since + NAG_TICKS
        var leaveAt: Long = -1

        /** Set while its player talks to it; its timers wait until that script ends (a window can be open meanwhile). */
        var talking: Boolean = false

        /** The event's own progress while it is out, kept across talks (a talk shut partway starts again from here). */
        var state: Any? = null
    }

    /** The places teleporting events take their player to. */
    val areas = EventAreas(world)

    private val all = LinkedHashMap<String, RandomEvent>()
    private val out = LinkedHashMap<Npc, Out>()

    /** Players whose event is due but who were busy: they get it the first tick they are free. */
    private val held = HashSet<String>()

    val events: Collection<RandomEvent> get() = all.values

    fun event(key: String): RandomEvent? = all[key.lowercase()]

    fun register(event: RandomEvent) {
        require(all.put(event.key, event) == null) { "Two random events called ${event.key}" }
        for (id in event.npcIds) {
            world.handlers.onNpc(id, TALK_TO) { op -> talk(this, op.npc ?: return@onNpc) }
            world.handlers.onNpc(id, DISMISS) { op -> dismiss(this, op.npc ?: return@onNpc) }
        }
    }

    private fun nowMinutes(): Int = (clock() / 60_000).toInt()

    fun dueIn(player: Player): Int {
        if (player.charges[DUE] == 0) setNext(player)
        return player.charges[DUE] - nowMinutes()
    }

    fun isOff(player: Player): Boolean = player.charges[OFF] == 1

    fun setOff(player: Player, off: Boolean) {
        player.charges[OFF] = if (off) 1 else 0
        held -= player.saveKey
    }

    private fun setNext(player: Player) {
        player.charges[DUE] = nowMinutes() + world.random.between(DUE_MIN_MINUTES, DUE_MAX_MINUTES)
    }

    /** Makes the next event due now, for `::randomevent now`: it comes at the next tick the player is free. */
    fun makeDue(player: Player) {
        player.charges[DUE] = nowMinutes()
        held += player.saveKey
    }

    /**
     * An account that was offline when its event came gets it moved on. A new account's first time is set at its first
     * 500-tick look instead (or `::randomevent`), so logging in draws nothing from [World.random] and a seeded test
     * that never runs that long sees the same numbers it did before random events.
     */
    fun login(player: Player) {
        val due = player.charges[DUE]
        if (due != 0 && due <= nowMinutes()) player.charges[DUE] = nowMinutes() + world.random.between(OFFLINE_MIN_MINUTES, OFFLINE_MAX_MINUTES)
    }

    fun leaving(player: Player) {
        held -= player.saveKey
        for (it in out.values.filter { it.owner == player.saveKey }) remove(it)
    }

    /** The event NPC out for [player], if any. */
    fun outFor(player: Player): Out? = out.values.firstOrNull { it.owner == player.saveKey }

    /** Each tick, in the scripts phase: is [player]'s event due, and are they free for it? */
    fun process(player: Player) {
        if (player.loggedOut || player.dead || isOff(player)) return
        // None during a test setup, one held back included: it waits, due, till the setup is put back (R50A-1).
        if (player.beforeScenario != null) return
        val key = player.saveKey
        if (key !in held) {
            if (player.playedTicks % CHECK_TICKS != 0L) return
            if (player.charges[DUE] == 0) {
                setNext(player)
                return
            }
            if (player.charges[DUE] > nowMinutes()) return
            held += key
        }
        if (busy(player)) return
        held -= key
        setNext(player)
        if (outFor(player) != null) return
        val allowed = all.values.filter { allowedAt(player, it) && it.npcIds.any { id -> world.npcTypes[id] != null } }
        if (allowed.isEmpty()) return
        start(player, allowed[world.random.nextInt(allowed.size)])
    }

    /** An open interface (a dialogue too) or a Make-X run going (the weak queue) holds an event back. */
    private fun busy(player: Player): Boolean = world.scripts.busy(player) || player.scripts.weakQueue.isNotEmpty()

    /** Whether [event] may come for [player] where they stand, and its NPC stay with them there. */
    fun allowedAt(player: Player, event: RandomEvent): Boolean {
        val (x, z, level) = Triple(player.x, player.z, player.level)
        if (world.instances.inDynamicArea(x, z)) return false
        if (KingBlackDragon.inLair(x, z)) return false
        if (Scurrius.inPublicLair(x, z) && level == 0) return false
        if (world.archiveRaids.inLobby(player)) return false
        if (event.teleports && (Wilderness.inside(x, z) || !freeToLeave(player))) return false
        if (!event.entersBanks && inBank(x, z, level)) return false
        return true
    }

    /** Whether [player] may be taken away: not in combat, not Tele Blocked and not jailed in Shantay's cell. */
    private fun freeToLeave(player: Player): Boolean =
        !world.inCombat(player) && player.teleblockTicks <= 0 && !(player.charges[ShantayPass.JAILED] != 0 && ShantayPass.inCell(player))

    /** Ends [player]'s event NPC, if one is out, without a word: a test setup starting or being put back. */
    fun end(player: Player) {
        for (it in out.values.filter { it.owner == player.saveKey }) remove(it)
    }

    /** Whether a bank booth, chest or deposit box is within [BANK_RANGE] tiles of [x], [z]. Guessed; see the class. */
    fun inBank(x: Int, z: Int, level: Int): Boolean {
        for (dx in -BANK_RANGE..BANK_RANGE) for (dz in -BANK_RANGE..BANK_RANGE) {
            for (loc in world.locs.at(x + dx, z + dz, level)) {
                val type = world.locs.types[loc.id] ?: continue
                if (!type.name.startsWith("Bank", ignoreCase = true)) continue
                if (type.ops.any { it.equals("Bank", ignoreCase = true) || it.equals("Use", ignoreCase = true) || it.equals("Deposit", ignoreCase = true) }) return true
            }
        }
        return false
    }

    /**
     * Sends [event]'s NPC to [player] now, wherever they are: the timer's pick, or `::randomevent`. Returns null, sending
     * nothing, when no tile beside them is free or they have an event NPC out already. [npcId] picks which of the
     * event's NPCs comes (`::randomevent certer 5437`, for scripts); otherwise one at random.
     */
    fun start(player: Player, event: RandomEvent, npcId: Int? = null): Out? {
        if (outFor(player) != null) return null
        val ids = event.npcIds.filter { world.npcTypes[it] != null && (npcId == null || it == npcId) }
        if (ids.isEmpty()) return null.also { logger.warn { "No NPC ${event.npcIds} for random event ${event.key}" } }
        val type = world.npcTypes[ids[world.random.nextInt(ids.size)]]!!
        val (x, z) = freeTileBeside(player, type.size) ?: return null
        val npc = world.spawnNpc(NpcSpawn(type, x, z, player.level, source = "random event ${event.key} for ${player.saveKey}", respawns = false)) ?: return null
        val started = Out(player.saveKey, player.username, event, npc, world.tick)
        out[npc] = started
        if (event.follows) world.npcs.follow(npc, player)
        event.started(world, started)
        // With no greeting of its own, it opens with one of its waiting lines (the Mysterious Old Man's transcript).
        say(started, player, event.greeting ?: event.nags.takeIf { it.isNotEmpty() }?.let { it[world.random.nextInt(it.size)] })
        return started
    }

    private fun freeTileBeside(player: Player, size: Int): Pair<Int, Int>? = AROUND.asSequence()
        .map { (dx, dz) -> player.x + dx to player.z + dz }
        .firstOrNull { (x, z) ->
            (0 until size).all { sx -> (0 until size).all { sz -> world.canStand(x + sx, z + sz, player.level) } } &&
                world.npcs.near(x, z, player.level, size).none { it.covers(x, z) }
        }

    private fun say(it: Out, owner: Player, line: Line?) {
        if (line == null) return
        val words = world.textFor(owner)
        it.npc.effects.say(if ("name" in line.args) words.get(line, "name" to owner.username) else words[line])
    }

    /** Each tick, in the NPC phase: the NPCs out keep up with their players, nag, and go when their time is up. */
    fun processNpcs() {
        if (out.isEmpty()) return
        val tick = world.tick
        for (it in out.values.toList()) {
            val owner = world.onlinePlayers.firstOrNull { p -> p.saveKey == it.owner && !p.loggedOut }
            if (owner == null || it.npc.dead) {
                remove(it)
                continue
            }
            if (it.leaveAt >= 0) {
                if (tick >= it.leaveAt) remove(it)
                continue
            }
            // Its timers wait while its player talks to it (an NPC in a dialogue is engaged with that player).
            if (it.npc.engagedWith === owner) continue
            if (it.talking) {
                if (world.scripts.busy(owner)) continue
                it.talking = false
            }
            if (!allowedAt(owner, it.event)) {
                remove(it)
                continue
            }
            // One that doesn't follow stays put, wherever its player goes, till its minute is up.
            val far = it.event.follows && (owner.level != it.npc.level || maxOf(abs(owner.x - it.npc.x), abs(owner.z - it.npc.z)) > CATCH_UP)
            // Changing level puts a new NPC, with its own entry, in its place: the timers carry on with that.
            val current = if (far) catchUp(it, owner) else it
            if (current == null) {
                remove(it)
                continue
            }
            when {
                tick - current.since >= LINGER_TICKS -> leave(current, owner)
                tick >= current.nextNag && current.event.nags.isNotEmpty() -> {
                    say(current, owner, current.event.nags[world.random.nextInt(current.event.nags.size)])
                    current.nextNag = tick + NAG_TICKS
                }
            }
        }
    }

    /** Puts [it]'s NPC beside [owner] again and returns its entry (a new one after a change of level); null when no tile there is free. */
    private fun catchUp(it: Out, owner: Player): Out? {
        val (x, z) = freeTileBeside(owner, it.npc.type.size) ?: return null
        if (it.npc.level != owner.level) {
            // An NPC can't change level in place: a fresh one takes its place beside the player.
            val type = it.npc.type
            val npc = world.spawnNpc(NpcSpawn(type, x, z, owner.level, source = it.npc.spawn.source, respawns = false)) ?: return null
            val moved = Out(it.owner, it.ownerName, it.event, npc, it.since).also { m -> m.nextNag = it.nextNag; m.state = it.state }
            out.remove(it.npc)
            world.removeNpc(it.npc)
            out[npc] = moved
            world.npcs.follow(npc, owner)
            return moved
        }
        world.npcs.teleport(it.npc, x, z)
        world.npcs.follow(it.npc, owner)
        return it
    }

    /**
     * [player]'s event has given its reward but still has a line to say: from now on it has nothing more for them (a
     * Talk-to does nothing), and it goes [CLAIMED_TICKS] later if the talk is cut off before it ends (it goes at once
     * when it does end). Guessed: the time, and that it says nothing as it goes.
     */
    fun claimed(player: Player) {
        val it = outFor(player) ?: return
        world.npcs.stopFollowing(it.npc)
        it.leaveAt = world.tick + CLAIMED_TICKS
    }

    /** [it] gives up: its leaving line, then it goes. */
    fun leave(it: Out, owner: Player) {
        say(it, owner, it.event.leaving)
        world.npcs.stopFollowing(it.npc)
        it.leaveAt = world.tick + LEAVE_TICKS
    }

    private fun remove(it: Out) {
        if (out.remove(it.npc) == null) return
        world.removeNpc(it.npc)
    }

    /** The world took [npc] out (`::npcs`, an instance ending): forget its event. */
    /** The event out as [npc], if it is one. */
    fun outOf(npc: Npc?): Out? = npc?.let { out[it] }

    fun removed(npc: Npc) {
        out.remove(npc)
    }

    private suspend fun talk(context: ScriptContext, npc: Npc) {
        val player = context.player
        val it = out[npc] ?: return elsewhere(context, npc)
        if (it.owner != player.saveKey) {
            notYours(context, npc, it)
            return
        }
        // Guessed: once it has said its leaving line it has nothing more for its player but its gave-up box, if it has one.
        if (it.leaveAt >= 0) {
            val line = it.event.gaveUp ?: return
            world.dialogues.start(context, npc) { mesbox(line) }
            return
        }
        it.talking = true
        world.dialogues.start(context, npc) {
            if (with(it.event) { talk(world, this@RandomEvents) }) remove(it)
        }
    }

    private suspend fun elsewhere(context: ScriptContext, npc: Npc) {
        val event = all.values.firstOrNull { npc.type.id in it.npcIds } ?: return
        with(event) { context.talkElsewhere(world, npc) }
    }

    /** Another player's Talk-to or Dismiss: the event's line for them, naming its own player if the words do. */
    private suspend fun notYours(context: ScriptContext, npc: Npc, it: Out) {
        val line = it.event.notYours ?: return
        world.dialogues.start(context, npc) {
            val values = if ("name" in line.args) arrayOf("name" to it.ownerName) else emptyArray()
            if (it.event.notYoursInBox) mesbox(line, *values) else chatNpc(ChatAnim.NEUTRAL, line, *values)
        }
    }

    private suspend fun dismiss(context: ScriptContext, npc: Npc) {
        val player = context.player
        val it = out[npc] ?: return
        if (it.owner != player.saveKey) {
            notYours(context, npc, it)
            return
        }
        remove(it)
    }

    /**
     * Gives [player] [count] of item [id], or puts what doesn't fit on the ground under them, theirs alone for the
     * usual drop's while (guessed) or, with [ownTicks], theirs alone for that long and then gone. Returns false when
     * anything went on the ground.
     */
    fun reward(player: Player, id: Int, count: Int = 1, ownTicks: Int? = null): Boolean {
        val type = world.objTypes[id] ?: return true.also { logger.warn { "No item $id in the cache for a random event reward" } }
        val left = count - player.inventory.add(type, count)
        if (left <= 0) return true
        val stacks = if (type.stackable) listOf(left) else List(left) { 1 }
        for (n in stacks) {
            if (ownTicks != null) {
                world.groundObjs.addPrivate(type, n, player.x, player.z, player.level, player.saveKey, ownTicks, ownTicks, neverPublic = true)
            } else {
                world.groundObjs.addPrivate(type, n, player.x, player.z, player.level, player.saveKey, GroundObjs.DROP_PRIVATE_TICKS, GroundObjs.DROP_DESPAWN_TICKS)
            }
        }
        return false
    }
}

/** Every random event built, and the strange fruit's Eat. */
fun registerRandomEvents(world: World) {
    val events = listOf(
        Genie, CountCheckEvent, DrunkenDwarf, RickTurpentine, MysteriousOldMan, DrJekyll, StrangePlant, Certer, SandwichLady,
        FreakyForester, DrillDemon, Gravedigger, Mime, Beekeeper, CaptArnav, EvilBob, PrisonPete,
    )
    for (event in events) {
        world.randomEvents.register(event)
    }
    FreakyForester.register(world)
    DrillDemon.register(world)
    Gravedigger.register(world)
    Mime.register(world)
    Beekeeper.register(world)
    EvilBob.register(world)
    PrisonPete.register(world)
    world.handlers.onObj(StrangePlant.FRUIT, 1) { op -> StrangePlant.eat(world, player, op.sub) }
}
