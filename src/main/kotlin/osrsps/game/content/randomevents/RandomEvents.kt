package osrsps.game.content.randomevents

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.math.abs
import osrsps.game.GroundObjs
import osrsps.game.Player
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.content.KingBlackDragon
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

    /** Overhead as it appears, with `{name}`, the player's name. */
    abstract val greeting: Line

    /** Overhead while it waits, one at random each time; may hold `{name}`. */
    abstract val nags: List<Line>

    /** Overhead as it gives up and goes. */
    abstract val leaving: Line

    /** Talk-to by anyone but its player. */
    abstract val notYours: Line

    /** Talk-to by its player, which ends the event when it returns true: its NPC then goes. */
    abstract suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean
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
 * [BANK_RANGE] tiles), a boss area (the King Black Dragon's lair) and a minigame (any instance); the NPC's tile (the
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
        const val CATCH_UP = 10
        const val BANK_RANGE = 4

        const val TALK_TO = 1
        const val DISMISS = 5

        /** When the next event is due, in minutes since 1970; 0 before the first login with events. */
        const val DUE = "random_event_due"

        /** 1 while `::randomevent off` holds events back for the account. */
        const val OFF = "random_events_off"

        /** The tiles beside a player the NPC may appear on, in the order tried. */
        private val AROUND = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1, -1 to -1, 1 to -1, -1 to 1, 1 to 1)
    }

    /** One event NPC out for its player. */
    class Out(val owner: String, val event: RandomEvent, val npc: Npc, val since: Long) {
        var nextNag: Long = since + NAG_TICKS
        var leaveAt: Long = -1
    }

    private val all = LinkedHashMap<String, RandomEvent>()
    private val out = LinkedHashMap<Npc, Out>()

    /** Players whose event is due but who were busy: they get it the first tick they are free. */
    private val held = HashSet<String>()

    val events: Collection<RandomEvent> get() = all.values

    fun event(key: String): RandomEvent? = all[key.lowercase()]

    fun register(event: RandomEvent) {
        require(all.put(event.key, event) == null) { "Two random events called ${event.key}" }
        world.handlers.onNpc(event.npcId, TALK_TO) { op -> talk(this, op.npc ?: return@onNpc) }
        world.handlers.onNpc(event.npcId, DISMISS) { op -> dismiss(this, op.npc ?: return@onNpc) }
    }

    private fun nowMinutes(): Int = (clock() / 60_000).toInt()

    fun dueIn(player: Player): Int = player.charges[DUE] - nowMinutes()

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

    fun login(player: Player) {
        val due = player.charges[DUE]
        when {
            due == 0 -> setNext(player)
            due <= nowMinutes() -> player.charges[DUE] = nowMinutes() + world.random.between(OFFLINE_MIN_MINUTES, OFFLINE_MAX_MINUTES)
        }
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
        val key = player.saveKey
        if (key !in held) {
            if (player.playedTicks % CHECK_TICKS != 0L) return
            if (player.charges[DUE] > nowMinutes()) return
            held += key
        }
        if (busy(player)) return
        held -= key
        setNext(player)
        if (outFor(player) != null) return
        val allowed = all.values.filter { allowedAt(player, it) }
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
        if (event.teleports && Wilderness.inside(x, z)) return false
        if (!event.entersBanks && inBank(x, z, level)) return false
        return true
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
     * nothing, when no tile beside them is free or they have an event NPC out already.
     */
    fun start(player: Player, event: RandomEvent): Out? {
        if (outFor(player) != null) return null
        val type = world.npcTypes[event.npcId] ?: return null.also { logger.warn { "No NPC ${event.npcId} for random event ${event.key}" } }
        val (x, z) = freeTileBeside(player, type.size) ?: return null
        val npc = world.spawnNpc(NpcSpawn(type, x, z, player.level, source = "random event ${event.key} for ${player.saveKey}", respawns = false)) ?: return null
        val started = Out(player.saveKey, event, npc, world.tick)
        out[npc] = started
        world.npcs.follow(npc, player)
        say(started, player, event.greeting)
        return started
    }

    private fun freeTileBeside(player: Player, size: Int): Pair<Int, Int>? = AROUND.asSequence()
        .map { (dx, dz) -> player.x + dx to player.z + dz }
        .firstOrNull { (x, z) ->
            (0 until size).all { sx -> (0 until size).all { sz -> world.canStand(x + sx, z + sz, player.level) } } &&
                world.npcs.near(x, z, player.level, size).none { it.covers(x, z) }
        }

    private fun say(it: Out, owner: Player, line: Line) {
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
            if (!allowedAt(owner, it.event)) {
                remove(it)
                continue
            }
            val far = owner.level != it.npc.level || maxOf(abs(owner.x - it.npc.x), abs(owner.z - it.npc.z)) > CATCH_UP
            if (far && !catchUp(it, owner)) {
                remove(it)
                continue
            }
            when {
                tick - it.since >= LINGER_TICKS -> leave(it, owner)
                tick >= it.nextNag -> {
                    say(it, owner, it.event.nags[world.random.nextInt(it.event.nags.size)])
                    it.nextNag = tick + NAG_TICKS
                }
            }
        }
    }

    /** Puts [it]'s NPC beside [owner] again; false when no tile there is free. */
    private fun catchUp(it: Out, owner: Player): Boolean {
        val (x, z) = freeTileBeside(owner, it.npc.type.size) ?: return false
        if (it.npc.level != owner.level) {
            // An NPC can't change level in place: a fresh one takes its place beside the player.
            val type = it.npc.type
            val npc = world.spawnNpc(NpcSpawn(type, x, z, owner.level, source = it.npc.spawn.source, respawns = false)) ?: return false
            val moved = Out(it.owner, it.event, npc, it.since).also { m -> m.nextNag = it.nextNag }
            out.remove(it.npc)
            world.removeNpc(it.npc)
            out[npc] = moved
            world.npcs.follow(npc, owner)
            return true
        }
        world.npcs.teleport(it.npc, x, z)
        world.npcs.follow(it.npc, owner)
        return true
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
    fun removed(npc: Npc) {
        out.remove(npc)
    }

    private suspend fun talk(context: ScriptContext, npc: Npc) {
        val player = context.player
        val it = out[npc] ?: return
        if (it.owner != player.saveKey) {
            world.dialogues.start(context, npc) { chatNpc(ChatAnim.NEUTRAL, it.event.notYours) }
            return
        }
        // Guessed: once it has said its leaving line it has nothing more for its player.
        if (it.leaveAt >= 0) return
        world.dialogues.start(context, npc) {
            if (with(it.event) { talk(world, this@RandomEvents) }) remove(it)
        }
    }

    private suspend fun dismiss(context: ScriptContext, npc: Npc) {
        val player = context.player
        val it = out[npc] ?: return
        if (it.owner != player.saveKey) {
            world.dialogues.start(context, npc) { chatNpc(ChatAnim.NEUTRAL, it.event.notYours) }
            return
        }
        remove(it)
    }

    /** Gives [player] [count] of item [id], or puts what doesn't fit on the ground under them, theirs alone. Guessed. */
    fun reward(player: Player, id: Int, count: Int = 1) {
        val type = world.objTypes[id] ?: return logger.warn { "No item $id in the cache for a random event reward" }
        val left = count - player.inventory.add(type, count)
        if (left <= 0) return
        val stacks = if (type.stackable) listOf(left) else List(left) { 1 }
        for (n in stacks) {
            world.groundObjs.addPrivate(type, n, player.x, player.z, player.level, player.saveKey, GroundObjs.DROP_PRIVATE_TICKS, GroundObjs.DROP_DESPAWN_TICKS)
        }
    }
}
