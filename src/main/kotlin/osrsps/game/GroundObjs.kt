package osrsps.game

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.SetMapFlagV2
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptRunner
import osrsps.game.zone.ZoneObj
import osrsps.game.zone.Zones
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap
import kotlin.math.abs

private val logger = KotlinLogging.logger {}

/**
 * An item spawn: [count] of obj [id] on a tile for anyone to take, back [respawnTicks] after it is taken on an empty
 * world. See [GroundObjs.respawnDelay] for how a busier world shortens that.
 */
data class ObjSpawn(val id: Int, val count: Int, val x: Int, val z: Int, val level: Int, val respawnTicks: Int = GroundObjs.DEFAULT_RESPAWN_TICKS) {
    init {
        require(id >= 0 && count >= 1) { "Bad obj spawn $id x $count" }
        require(respawnTicks >= 1) { "An obj spawn needs a respawn time of at least 1 tick, not $respawnTicks" }
    }
}

/** Something the player walks to and then does. [World] checks it after the player's movement each tick. */
fun interface Interaction {
    /** Called after the player moved, unless they are delayed. Returns true once it is done or has given up. */
    fun process(player: Player): Boolean
}

/**
 * Items on the ground: dropping them from the inventory, walking to them and taking them, the timers that turn a drop
 * public and make it disappear, and item spawns that come back after they are taken. What each client is sent is
 * [Zones]' business.
 *
 * Timers count down once a tick, in [process], before any player's input. So a drop made during tick T turns public at
 * the start of tick T + 100. Which phase of the tick live runs them in is not known.
 */
class GroundObjs(
    private val zones: Zones,
    private val objTypes: ObjTypes,
    private val scripts: ScriptRunner,
    private val collision: CollisionMap,
    /** Whether the player with this save key is in the world, a dropped connection waiting to reconnect included. */
    private val isOnline: (String) -> Boolean,
    /** Players in the world, for respawn times. */
    private val population: () -> Int,
    private val send: (Player, OutgoingGameMessage) -> Unit = { player, message -> player.session.queue(message) },
) {
    /** Objs whose timers are running, in the order they were put down. */
    private val timed = LinkedHashSet<ZoneObj>()

    /** The spawn each spawned obj came from. */
    private val spawned = HashMap<ZoneObj, ObjSpawn>()

    private class Respawn(val spawn: ObjSpawn, var ticksLeft: Int)

    private val respawns = ArrayList<Respawn>()

    /** The item spawns, for tests and commands. */
    val spawns: List<ObjSpawn> get() = spawnList

    private val spawnList = ArrayList<ObjSpawn>()

    /** Puts [spawn]'s obj on the ground now, for everyone, and brings it back each time it is taken. */
    fun addSpawn(spawn: ObjSpawn) {
        val type = requireNotNull(objTypes[spawn.id]) { "No obj type ${spawn.id} for $spawn" }
        require(type.stackable || spawn.count == 1) { "$spawn: only stackable objs can spawn more than one to a stack" }
        spawnList += spawn
        placeSpawn(spawn)
    }

    private fun placeSpawn(spawn: ObjSpawn) {
        val obj = ZoneObj(spawn.id, spawn.count, spawn.x, spawn.z, spawn.level)
        zones.addObj(obj)
        spawned[obj] = spawn
    }

    /**
     * Drops the whole stack in [slot] on the player's tile. It is theirs alone for [DROP_PRIVATE_TICKS], then everyone's
     * if it can be traded, and it disappears [DROP_DESPAWN_TICKS] after the drop. An untradeable item never turns
     * public, and its timer stands still while its owner is logged out (wiki, Drop).
     *
     * Not built: the warning before dropping an item worth 30,000 coins or more (needs dialogue, task 18), the
     * Wilderness rules, and items placed on tables.
     */
    fun drop(player: Player, slot: Int): ZoneObj? {
        val held = player.inventory[slot] ?: return null
        val type = objTypes[held.id] ?: run {
            logger.warn { "${player.username} dropped unknown obj ${held.id}" }
            return null
        }
        player.inventory.remove(slot)
        return addPrivate(type, held.count, player.x, player.z, player.level, player.saveKey, DROP_PRIVATE_TICKS, DROP_DESPAWN_TICKS, neverPublic = !type.tradeable)
    }

    /**
     * Puts [count] of [type] down for [owner] (a save key) alone, turning public after [privateTicks] unless
     * [neverPublic], and gone after [despawnTicks] (or never, with [ZoneObj.NEVER]). Drops, and later NPC loot, come
     * through here.
     *
     * A stackable obj lands on [owner]'s private stack of the same item on that tile, if there is one, as RSMod does;
     * the timers then start again. Guessed: live may keep the old stack's timers. Anything else goes down one per obj.
     */
    fun addPrivate(
        type: ObjType,
        count: Int,
        x: Int,
        z: Int,
        level: Int,
        owner: String,
        privateTicks: Int,
        despawnTicks: Int,
        neverPublic: Boolean = !type.tradeable,
    ): ZoneObj {
        require(count >= 1) { "Can't put $count of $type on the ground" }
        if (type.stackable) {
            val stack = zones.objsAt(x, z, level).firstOrNull {
                it.id == type.id && it.owner == owner && it.neverPublic == neverPublic && it.count.toLong() + count <= Int.MAX_VALUE
            }
            if (stack != null) {
                zones.setObjCount(stack, stack.count + count)
                stack.ticksUntilPublic = privateTicks
                stack.ticksUntilDespawn = despawnTicks
                timed += stack
                return stack
            }
        }
        val pieces = if (type.stackable) 1 else count
        require(pieces <= MAX_SPLIT) { "Won't put $count separate ${type.name} on one tile" }
        var last: ZoneObj? = null
        repeat(pieces) {
            val obj = ZoneObj(
                type.id, if (type.stackable) count else 1, x, z, level, owner = owner,
                ticksUntilPublic = privateTicks, ticksUntilDespawn = despawnTicks, neverPublic = neverPublic,
            )
            zones.addObj(obj)
            timed += obj
            last = obj
        }
        return last!!
    }

    /** Puts [count] of [type] down for everyone, gone after [despawnTicks] or never. Non-stackable objs go down one each. */
    fun addPublic(type: ObjType, count: Int, x: Int, z: Int, level: Int, despawnTicks: Int = ZoneObj.NEVER): List<ZoneObj> {
        require(count >= 1) { "Can't put $count of $type on the ground" }
        val pieces = if (type.stackable) 1 else count
        require(pieces <= MAX_SPLIT) { "Won't put $count separate ${type.name} on one tile" }
        return List(pieces) {
            val obj = ZoneObj(type.id, if (type.stackable) count else 1, x, z, level, ticksUntilDespawn = despawnTicks)
            zones.addObj(obj)
            if (despawnTicks != ZoneObj.NEVER) timed += obj
            obj
        }
    }

    /** Removes everything on a tile. Spawns there come back as if taken. Returns how many objs went. */
    fun clear(x: Int, z: Int, level: Int): Int {
        val objs = zones.objsAt(x, z, level)
        objs.forEach(::remove)
        return objs.size
    }

    private fun remove(obj: ZoneObj): Boolean {
        if (!zones.removeObj(obj)) return false
        timed -= obj
        spawned.remove(obj)?.let { respawns += Respawn(it, respawnDelay(it)) }
        return true
    }

    /**
     * How long [spawn] takes to come back: the wiki's ⌊BaseRate × (4000 − Population) / 4000⌋ (Item spawn), with the
     * players in the world as the population, counted when it is taken. At least a tick.
     */
    fun respawnDelay(spawn: ObjSpawn): Int {
        val players = population().coerceIn(0, MAX_POPULATION)
        return (spawn.respawnTicks.toLong() * (MAX_POPULATION - players) / MAX_POPULATION).toInt().coerceAtLeast(1)
    }

    /** Runs a tick of the timers: objs turning public, disappearing, and spawns coming back. */
    fun process() {
        val iterator = timed.iterator()
        while (iterator.hasNext()) {
            val obj = iterator.next()
            if (!zones.hasObj(obj)) {
                iterator.remove()
                continue
            }
            val owner = obj.owner
            // Wiki (Drop): an untradeable item's timer pauses while its owner is logged out.
            val paused = owner != null && obj.neverPublic && !isOnline(owner)
            if (obj.ticksUntilDespawn != ZoneObj.NEVER && !paused && --obj.ticksUntilDespawn <= 0) {
                iterator.remove()
                remove(obj)
                continue
            }
            // After the despawn count, so the others are sent the time that is really left.
            if (owner != null && !obj.neverPublic && --obj.ticksUntilPublic <= 0) zones.revealObj(obj)
        }
        val due = respawns.filter { --it.ticksLeft <= 0 }
        if (due.isEmpty()) return
        respawns.removeAll(due)
        for (respawn in due) placeSpawn(respawn.spawn)
    }

    /** The objs [player] can see on a tile, oldest first. */
    fun visibleAt(player: Player, x: Int, z: Int, level: Int): List<ZoneObj> =
        zones.objsAt(x, z, level).filter { it.visibleTo(player.saveKey) }

    /** The obj [player] means by a click on [id] on a tile: guessed to be the oldest one they can see there. */
    private fun find(player: Player, id: Int, x: Int, z: Int, level: Int): ZoneObj? =
        visibleAt(player, x, z, level).firstOrNull { it.id == id }

    /** The item spawn [obj] came from, or null if it wasn't spawned. */
    fun spawnOf(obj: ZoneObj): ObjSpawn? = spawned[obj]

    /** Respawns that are waiting, as (spawn, ticks left), for commands and tests. */
    fun pendingRespawns(): List<Pair<ObjSpawn, Int>> = respawns.map { it.spawn to it.ticksLeft }

    /**
     * A click on [option] (1 to 5) of obj [id] on a tile. Returns the interaction to walk to it with, or null when the
     * player can't see such an obj there or it has no such option: the client was behind, so the click is dropped.
     */
    fun click(player: Player, id: Int, x: Int, z: Int, option: Int): Interaction? {
        val type = objTypes[id] ?: return null
        if (option !in 1..5 || type.ops.getOrNull(option - 1) == null) {
            logger.info { "${player.username} sent op $option on $type on the ground, which has no such option" }
            return null
        }
        if (find(player, id, x, z, player.level) == null) return null
        return GroundObjInteraction(id, x, z, player.level, option)
    }

    private inner class GroundObjInteraction(val id: Int, val x: Int, val z: Int, val level: Int, val option: Int) : Interaction {
        override fun process(player: Player): Boolean {
            if (player.level != level) return true
            // Guessed: an obj that vanished while the player walked to it just ends the walk, with no message.
            val obj = find(player, id, x, z, level) ?: return true
            if (player.x == x && player.z == z) {
                run(player, obj, option, far = false)
                return true
            }
            if (player.waypoints.isNotEmpty()) return false
            if (reachableFromBeside(player, x, z, level)) {
                run(player, obj, option, far = true)
            } else {
                message(player, CANT_REACH)
            }
            // Guessed: the client's flag sits on the obj's tile, which the player didn't reach, so clear it.
            send(player, SetMapFlagV2.RESET)
            return true
        }
    }

    /**
     * Whether the player can take an obj from the tile beside it: the obj's tile can't be walked on (a table) and the
     * player stands next to it, north, south, east or west, with no wall between. Inferred from how items on tables
     * work on live and RSMod's reach rule, not measured.
     */
    private fun reachableFromBeside(player: Player, x: Int, z: Int, level: Int): Boolean {
        val dx = x - player.x
        val dz = z - player.z
        if (abs(dx) + abs(dz) != 1) return false
        if (collision[x, z, level] and CollisionFlag.BLOCK_WALK == 0) return false
        val (out, into) = when {
            dx == 1 -> CollisionFlag.WALL_EAST to CollisionFlag.WALL_WEST
            dx == -1 -> CollisionFlag.WALL_WEST to CollisionFlag.WALL_EAST
            dz == 1 -> CollisionFlag.WALL_NORTH to CollisionFlag.WALL_SOUTH
            else -> CollisionFlag.WALL_SOUTH to CollisionFlag.WALL_NORTH
        }
        return collision[player.x, player.z, level] and out == 0 && collision[x, z, level] and into == 0
    }

    /**
     * The player reached [obj]: the content handler for [option] runs if there is one, else Take for op3. Taking from
     * beside the tile waits a tick first, as RSMod does (rev 233), where it also plays human_pickuptable (seq 832).
     */
    private fun run(player: Player, obj: ZoneObj, option: Int, far: Boolean) {
        if (scripts.trigger(player, Op(OpTarget.GROUND_OBJ, obj.id, option, groundObj = obj))) return
        if (option != TAKE_OPTION) {
            message(player, InventoryTab.NOTHING_INTERESTING)
            return
        }
        if (!far) {
            take(player, obj)
            return
        }
        val type = objTypes[obj.id] ?: return
        if (!fits(player.inventory, type, obj.count)) {
            message(player, NO_SPACE)
            return
        }
        scripts.start(player) {
            delay(1)
            take(player, obj)
        }
    }

    /**
     * Takes [obj] into [player]'s inventory if it fits. Returns false, after telling them why, if it doesn't or is gone.
     * Messages are RSMod's (rev 233), not checked against live.
     */
    fun take(player: Player, obj: ZoneObj): Boolean {
        if (!zones.hasObj(obj) || !obj.visibleTo(player.saveKey)) {
            message(player, TOO_LATE)
            return false
        }
        val type = objTypes[obj.id] ?: return false
        if (!fits(player.inventory, type, obj.count)) {
            message(player, NO_SPACE)
            return false
        }
        remove(obj)
        player.inventory.add(type, obj.count)
        return true
    }

    /** Whether all of [count] of [type] would go into [inventory]. */
    private fun fits(inventory: Inventory, type: ObjType, count: Int): Boolean {
        if (!type.stackable) return inventory.freeSlots >= count
        val have = inventory.count(type.id)
        if (have > 0) return have + count <= Inventory.MAX_STACK
        return inventory.freeSlots > 0
    }

    private fun message(player: Player, text: String) = send(player, MessageGame(CHATTYPE_GAMEMESSAGE, text))

    companion object {
        /** Wiki (Drop, Dropped items): a drop is the dropper's alone for 60 seconds. */
        const val DROP_PRIVATE_TICKS = 100

        /**
         * Wiki (Drop): a tradeable drop turns public after 60 seconds and disappears 120 seconds later; an untradeable
         * one disappears after 180 seconds. Both are 3 minutes from the drop. The Dropped items page gives 200 ticks for
         * tradeable monster drops instead: see [LOOT_DESPAWN_TICKS].
         */
        const val DROP_DESPAWN_TICKS = 300

        /** Wiki (Dropped items): tradeable monster drops stay 200 ticks. For NPC loot, later. */
        const val LOOT_DESPAWN_TICKS = 200

        /** Guessed, as RSMod's default: the wiki gives no respawn times, and each spawn should set its own. */
        const val DEFAULT_RESPAWN_TICKS = 100

        /** Take is op3 for every obj on the ground, as the cache's default ground options have it. */
        const val TAKE_OPTION = 3

        /** The wiki's respawn formula counts players out of 4,000; a full world (2,000) halves the time. */
        private const val MAX_POPULATION = 4000

        /**
         * Wiki (Drop): 128 different items fit on one tile. What live does past that isn't known, so this only stops
         * one call putting down more than that many separate objs.
         */
        const val MAX_SPLIT = 128

        const val CANT_REACH = "I can't reach that!"
        const val TOO_LATE = "Too late - it's gone!"
        const val NO_SPACE = "You don't have enough inventory space to hold that item."

        private const val CHATTYPE_GAMEMESSAGE = 0
    }
}
