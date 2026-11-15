package osrsps.game.skill.hunter

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.message
import osrsps.game.dialogue.Line
import osrsps.game.npc.Npc
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptContext
import osrsps.game.skill.ClockStep
import osrsps.game.skill.SkillClock
import osrsps.game.skill.SuccessRoll
import osrsps.game.zone.ZoneLoc
import osrsps.game.zone.ZoneObj
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap
import osrsps.map.LocShape
import osrsps.map.Steps
import osrsps.map.Tile
import kotlin.math.abs

/** One drop a creature gives when its trap is checked: [count] to [countMax] of obj [obj]. */
class CreatureLoot(val obj: Int, val count: Int, val countMax: Int = count)

/**
 * A creature a trap catches: NPC type [npc], the Hunter [level] it needs, the XP in tenths, the catch pair for
 * [SuccessRoll] (the Wiki's "Skilling success chart" for it, Mod Ash's figures), the trap's loc while it struggles
 * ([catching]) and once it is caught ([full]), and what checking the trap gives. A box trap has a struggling loc for each
 * side the creature came in from, [catchingFrom] (north, east, south, west), and [catching] is then the north one.
 */
class Creature(
    val npc: Int,
    val level: Int,
    val xpTenths: Int,
    val low: Int,
    val high: Int,
    val catching: Int,
    val full: Int,
    val loot: List<CreatureLoot>,
    val catchingFrom: List<Int> = emptyList(),
) {
    /**
     * The struggling loc for a creature that stepped in from [dx], [dz] off the trap's tile. Guessed: the cache's _n,
     * _e, _s and _w types are named for the side it came from; a diagonal step counts as its north or south side.
     */
    fun catchingLoc(dx: Int, dz: Int): Int {
        if (catchingFrom.size != 4) return catching
        return when {
            dz > 0 -> catchingFrom[0]
            dz < 0 -> catchingFrom[2]
            dx > 0 -> catchingFrom[1]
            dx < 0 -> catchingFrom[3]
            else -> catching
        }
    }
}

/**
 * What a trap shows: the [locs] it puts up, and the cache's locs it takes away while they stand ([hidden]): a net trap's
 * young tree, under a caught salamander's net.
 */
class TrapLook(val locs: List<ZoneLoc>, val hidden: List<ZoneLoc> = emptyList())

/**
 * A kind of trap a player lays from an item: the [item] laid, its locs when set, failing and failed (with its Dismantle
 * and Reset ops), the player's animation while laying it, and the [creatures] it catches. A net trap ([NetTraps]) is
 * set on a young tree instead, and shows more than one loc.
 */
open class TrapKind(
    val name: String,
    val item: Int,
    val set: Int,
    val failing: Int,
    val broken: Int,
    val layAnim: Int,
    val creatures: List<Creature>,
    /** What Investigate on the owner's set trap says. */
    val investigate: Line = HunterLines.INVESTIGATE,
) {
    fun creature(npcType: Int): Creature? = creatures.firstOrNull { it.npc == npcType }

    /** The Hunter level laying it needs: its lowest creature's (the Wiki's 1 for a bird snare, 27 for a box trap). */
    open val level: Int get() = creatures.minOf { it.level }

    /** Whether the trap is laid from its item (Lay); false for one set on a loc, which registers that itself. */
    open val laidFromItem: Boolean get() = true

    /** The locs answering Dismantle and Investigate while the trap is set. */
    open val setLocs: List<Int> get() = listOf(set)

    /** The locs answering Dismantle and Reset once it has failed. */
    open val brokenLocs: List<Int> get() = listOf(broken)

    /** Whether [locId] is one of this kind's locs, in any state. */
    open fun owns(locId: Int): Boolean =
        locId in setLocs || locId == failing || locId in brokenLocs || creatures.any { locId == it.catching || locId == it.full || locId in it.catchingFrom }

    /** What [trap] shows in [state], [locId] being the loc for it ([set], a creature's [Creature.full], ...): one loc on its tile. */
    open fun look(trap: Trap, state: TrapState, locId: Int): TrapLook =
        TrapLook(listOf(ZoneLoc(locId, LocShape.CENTREPIECE_STRAIGHT, 0, trap.x, trap.z, trap.level)))

    /** Whether nothing on [x], [z], [level] is in the trap's way, [locs] holding what is there. */
    open fun fits(locs: List<ZoneLoc>): Boolean = locs.isEmpty()
}

/** Where a laid trap is. */
enum class TrapState {
    /** Waiting for a creature. */
    SET,

    /** A creature walked in and is caught; the trap shows it struggling. */
    CATCHING,

    /** Caught, ready to check. */
    FULL,

    /** A creature got away; the trap shows it snapping shut. */
    FAILING,

    /** Snapped shut with nothing in it, ready to dismantle or reset. */
    BROKEN,
}

/**
 * A trap a player has laid on [x], [z], [level], the tile a creature steps onto to spring it. [anchor] is the cache loc
 * it is set on (a net trap's young tree) and [rotation] the side of it the trap is on. [stateTick] is when it entered
 * [state]; [collapseTick] when it falls, a minute from its laying and again from its catch or miss; [lured] the creature
 * walking into it.
 */
class Trap(val owner: String, val kind: TrapKind, val x: Int, val z: Int, val level: Int, val anchor: ZoneLoc? = null, val rotation: Int = 0) {
    /** What it shows now. */
    var look = TrapLook(emptyList())
    var state = TrapState.SET
    var stateTick = 0L
    var collapseTick = 0L
    var creature: Creature? = null
    var lured: Npc? = null
    var luredTick = 0L

    /** Whether the trap stands on [tx], [tz]: its own tile, or the tree it is set on. */
    fun covers(tx: Int, tz: Int): Boolean = (x == tx && z == tz) || (anchor != null && anchor.x == tx && anchor.z == tz)

    /** Where the lured creature last stood off the trap, for the side it steps in from. */
    var luredFromX = 0
    var luredFromZ = 0

    /** Its locs now. */
    val locs: List<ZoneLoc> get() = look.locs
}

/**
 * Hunter's trap engine: traps a player lays and owns, which hunt the creatures around them.
 *
 * Sources: the OSRS Wiki's Hunter page (Multiple traps, read 2026-11-15) for the trap count, 2 from level 1 (since 19
 * August 2026), 3 at 40, 4 at 60, 5 at 80, by the boosted level; its Box trap page (Mechanics, citing Mod Ash) for
 * the hunt: a set trap looks for a creature every 3 ticks, any within 2 tiles of it (a 5 by 5 square), and catches
 * nothing while a player stands on it; the Tick manipulation page for the timing: laying uses the skill clock and takes
 * 4 ticks ([SkillClock], so a clock already running lays sooner), and picking a trap up takes 3 ticks and stalls the
 * player. RuneLite's Hunter plugin (HunterTrap.TRAP_TIME, observed on live) for the collapse: a trap stands a minute
 * ([TRAP_TICKS]) and the minute starts again when it catches or fails; the plugin also expects the trap's loc to
 * appear on the tile the player stood on the tick before, so the player steps off after it stands (west, else east,
 * south, north, as for a fire: inferred). A creature caught with too low a level always fails (Wiki, Box trap).
 *
 * Guessed unless said, none of it on the Wiki: the trap goes for one creature in range at random and always lures it; it walks to
 * the trap and the roll is made the tick it steps on (given up after [LURE_TICKS]); a struggle and a snap each show
 * for [CATCHING_TICKS] and [FAILING_TICKS] ticks (the second the failing animation's length); a caught creature comes
 * back where it spawned [RESPAWN_TICKS] later; a collapsed trap leaves its item on its tile (the Wiki's Net trap page:
 * it reverts to the item, gone a minute later, [COLLAPSED_DESPAWN_TICKS]), its owner's alone at first as a drop is
 * (guessed); a trap collapses at once when its owner leaves the world, goes to another level or is more than
 * [OWNER_RANGE] tiles away; traps go on any tile a fire could (no loc, walkable), indoors and banks included, but
 * not in an instance (ours: nothing there could take one down again).
 */
class Traps(private val world: World, private val collision: CollisionMap) {
    private val traps = ArrayList<Trap>()

    private class Respawn(val npc: Npc, val tick: Long)

    private val respawns = ArrayList<Respawn>()

    /** The traps [owner] (a save key) has laid. */
    fun of(owner: String): List<Trap> = traps.filter { it.owner == owner }

    /** The trap on [x], [z], [level], if any. */
    fun at(x: Int, z: Int, level: Int): Trap? = traps.firstOrNull { it.x == x && it.z == z && it.level == level }

    /** The trap showing [loc], if any. */
    fun showing(loc: ZoneLoc): Trap? = traps.firstOrNull { loc in it.locs }

    /** Every trap laid, for tests and commands. */
    val all: List<Trap> get() = traps

    /** Registers the Lay, Dismantle, Investigate, Check and Reset options of [kind]'s item and locs. */
    fun register(kind: TrapKind) {
        if (kind.laidFromItem) {
            val type = world.objTypes[kind.item]
            val layHeld = type?.iops?.indexOfFirst { it == "Lay" }?.takeIf { it >= 0 }?.plus(1) ?: LAY_HELD
            val layGround = type?.ops?.indexOfFirst { it == "Lay" }?.takeIf { it >= 0 }?.plus(1) ?: LAY_GROUND
            world.handlers.onObj(kind.item, layHeld) { op -> layHeld(kind, op.sub) }
            world.handlers.onGroundObj(kind.item, layGround) { op -> lay(kind, op.groundObj ?: return@onGroundObj, op.repeat, layGround) }
        }
        if (kind is NetTrapKind) world.handlers.onLoc(kind.tree, SET_TRAP) { op -> setTrap(kind, op.loc ?: return@onLoc) }
        for (set in kind.setLocs) {
            world.handlers.onLoc(set, DISMANTLE) { op -> dismantle(kind, op.loc ?: return@onLoc, reset = false) }
            world.handlers.onLoc(set, INVESTIGATE) { op -> investigate(op.loc ?: return@onLoc) }
        }
        for (broken in kind.brokenLocs) {
            world.handlers.onLoc(broken, DISMANTLE) { op -> dismantle(kind, op.loc ?: return@onLoc, reset = false) }
            world.handlers.onLoc(broken, RESET) { op -> dismantle(kind, op.loc ?: return@onLoc, reset = true) }
        }
        for (full in kind.creatures.map { it.full }.distinct()) {
            world.handlers.onLoc(full, CHECK) { op -> dismantle(kind, op.loc ?: return@onLoc, reset = false) }
            world.handlers.onLoc(full, RESET) { op -> dismantle(kind, op.loc ?: return@onLoc, reset = true) }
        }
    }

    /**
     * One tick of every trap. The world calls it in the NPC phase, before NPCs move, so a creature lured this tick takes
     * its first step this tick; [arrivals] then rolls for the ones that stepped on.
     */
    fun process() {
        val tick = world.tick
        respawns.removeAll { respawn ->
            if (respawn.tick > tick) return@removeAll false
            val npc = respawn.npc
            if (npc.hidden) {
                world.npcs.teleport(npc, npc.spawn.x, npc.spawn.z)
                world.show(npc)
            }
            true
        }
        if (traps.isEmpty()) return
        for (trap in traps.toList()) {
            val owner = world.playerBySaveKey(trap.owner)
            if (owner == null || owner.level != trap.level || abs(owner.x - trap.x) > OWNER_RANGE || abs(owner.z - trap.z) > OWNER_RANGE) {
                collapse(trap, owner)
                continue
            }
            val age = tick - trap.stateTick
            when (trap.state) {
                TrapState.SET -> {
                    if (tick >= trap.collapseTick) collapse(trap, owner) else hunt(trap, age)
                }
                TrapState.CATCHING -> if (age >= CATCHING_TICKS) change(trap, TrapState.FULL, trap.creature!!.full)
                TrapState.FAILING -> if (age >= FAILING_TICKS) change(trap, TrapState.BROKEN, trap.kind.broken)
                TrapState.FULL, TrapState.BROKEN -> if (tick >= trap.collapseTick) collapse(trap, owner)
            }
        }
    }

    /** A set trap's tick before NPCs move: walks its creature in, or looks for one every [LOOK_TICKS]. */
    private fun hunt(trap: Trap, age: Long) {
        val lured = trap.lured
        if (lured != null) {
            if (lured.outOfPlay || world.tick - trap.luredTick > LURE_TICKS) {
                release(trap)
            } else {
                // A blocked step drops the walk (Npcs.step), so it is asked again every tick it is on its way.
                lured.destination = Tile(trap.x, trap.z)
                trap.luredFromX = lured.x
                trap.luredFromZ = lured.z
            }
            return
        }
        if (age == 0L || age % LOOK_TICKS != 0L) return
        if (stoodOn(trap)) return
        val taken = traps.mapNotNullTo(HashSet()) { it.lured }
        val candidates = world.npcs.near(trap.x, trap.z, trap.level, LOOK_RANGE)
            .filter { it !in taken && trap.kind.creature(it.type.id) != null && it.target == null && it.engagedWith == null }
        if (candidates.isEmpty()) return
        val npc = candidates[world.random.between(0, candidates.size - 1)]
        trap.lured = npc
        trap.luredTick = world.tick
        trap.luredFromX = npc.x
        trap.luredFromZ = npc.z
        npc.destination = Tile(trap.x, trap.z)
    }

    /**
     * After NPCs move: each set trap whose creature stepped onto it this tick springs on it, the same tick. With a player
     * on the trap it catches nothing, so the creature is let go.
     */
    fun arrivals() {
        for (trap in traps.toList()) {
            if (trap.state != TrapState.SET) continue
            val lured = trap.lured ?: continue
            if (lured.x != trap.x || lured.z != trap.z || lured.level != trap.level) continue
            val owner = world.playerBySaveKey(trap.owner) ?: continue
            if (stoodOn(trap)) release(trap) else spring(trap, owner, lured)
        }
    }

    private fun stoodOn(trap: Trap): Boolean = world.onlinePlayers.any { it.level == trap.level && it.x == trap.x && it.z == trap.z }

    /** [npc] stepped onto [trap]: the catch roll, at the owner's boosted level; always a miss below the creature's level. */
    private fun spring(trap: Trap, owner: Player, npc: Npc) {
        val creature = trap.kind.creature(npc.type.id) ?: return release(trap)
        trap.lured = null
        val level = owner.skills.currentLevel(Skill.HUNTER)
        val caught = level >= creature.level && SuccessRoll.roll(world.random, creature.low, creature.high, level)
        if (caught) {
            trap.creature = creature
            world.hide(npc)
            respawns += Respawn(npc, world.tick + RESPAWN_TICKS)
            change(trap, TrapState.CATCHING, creature.catchingLoc(trap.luredFromX - trap.x, trap.luredFromZ - trap.z))
        } else {
            npc.destination = null
            change(trap, TrapState.FAILING, trap.kind.failing)
        }
        // The minute starts again from the catch or the miss, not from the struggle's end.
        trap.collapseTick = world.tick + TRAP_TICKS
    }

    private fun release(trap: Trap) {
        val npc = trap.lured ?: return
        trap.lured = null
        if (npc.destination == Tile(trap.x, trap.z)) npc.destination = null
    }

    private fun change(trap: Trap, state: TrapState, locId: Int) {
        if (trap.locs.any { !world.locs.exists(it) }) {
            // Something else took a tile; the trap is gone.
            traps.remove(trap)
            release(trap)
            return
        }
        show(trap, state, locId)
        trap.state = state
        trap.stateTick = world.tick
    }

    /** Takes down what [trap] shows and puts up its look for [state]. */
    private fun show(trap: Trap, state: TrapState, locId: Int) {
        clear(trap)
        val look = trap.kind.look(trap, state, locId)
        for (hidden in look.hidden) world.locs.remove(hidden)
        for (loc in look.locs) world.locs.add(loc)
        trap.look = look
    }

    /** Puts every tile [trap] touched back as the cache has it: bare, or its young tree. */
    private fun clear(trap: Trap) {
        for (loc in trap.look.locs) if (world.locs.exists(loc)) world.locs.restore(loc.x, loc.z, loc.level, loc.layer)
        for (hidden in trap.look.hidden) if (world.locs[hidden.x, hidden.z, hidden.level, hidden.layer] == null) world.locs.restore(hidden.x, hidden.z, hidden.level, hidden.layer)
        trap.look = TrapLook(emptyList())
    }

    /**
     * Takes [trap] down, leaving its item on its tile. A tile already full of objs takes nothing off it for the item
     * (exploit review): the item goes to its owner's backpack if there is room, or else the trap stands until a tick
     * when it fits.
     */
    private fun collapse(trap: Trap, owner: Player?) {
        val type = world.objTypes[trap.kind.item]
        if (type != null && !world.groundObjs.fits(type, 1, trap.x, trap.z, trap.level, owner?.saveKey ?: "")) {
            // No room on its tile: the owner's backpack takes it, or (guessed) with the owner gone it is lost, so the trap
            // never stays up waiting on a tile to clear.
            if (owner != null && owner.inventory.add(type, 1) != 1) return
            remove(trap)
            return
        }
        remove(trap)
        if (type == null) return
        if (owner != null) {
            world.groundObjs.addPrivate(type, 1, trap.x, trap.z, trap.level, owner.saveKey, osrsps.game.GroundObjs.DROP_PRIVATE_TICKS, COLLAPSED_DESPAWN_TICKS)
        } else {
            world.groundObjs.addPublic(type, 1, trap.x, trap.z, trap.level, COLLAPSED_DESPAWN_TICKS)
        }
    }

    private fun remove(trap: Trap) {
        traps.remove(trap)
        release(trap)
        clear(trap)
    }

    /** Takes down every trap [owner] (a save key) has laid, giving nothing back: ::reset and ::scenario clearing a run. */
    fun takeDown(owner: String) {
        of(owner).forEach(::remove)
    }

    /** Forgets the traps on the tiles where [inside] holds (an instance ending), so none acts there later. */
    fun forgetArea(inside: (Int, Int) -> Boolean) {
        traps.filter { inside(it.x, it.z) }.forEach(::release)
        traps.removeIf { inside(it.x, it.z) }
    }

    private suspend fun ScriptContext.layHeld(kind: TrapKind, slot: Int) {
        if (player.inventory[slot]?.id != kind.item) return
        if (!canLay(kind, player.x, player.z, player.level)) return
        val type = world.objTypes[kind.item] ?: return
        // The trap goes down on the tile first: a tile already full of objs refuses it, as dropping does, rather than lose one.
        if (!world.groundObjs.fits(type, 1, player.x, player.z, player.level, player.saveKey)) return world.message(player, HunterLines.CANT_HERE)
        if (player.inventory.remove(slot, 1) != 1) return
        val obj = world.groundObjs.addPrivate(
            type, 1, player.x, player.z, player.level, player.saveKey,
            osrsps.game.GroundObjs.DROP_PRIVATE_TICKS, osrsps.game.GroundObjs.DROP_DESPAWN_TICKS,
        )
        lay(kind, obj, repeat = false, option = LAY_GROUND)
    }

    /**
     * Lays [kind] from [obj] on the ground: the first try says so and animates; the trap stands when the skill clock
     * comes due, re-running the op each tick till then, as lighting logs does.
     */
    private suspend fun ScriptContext.lay(kind: TrapKind, obj: ZoneObj, repeat: Boolean, option: Int) {
        if (repeat && player.modalOpen) return player.anim(-1)
        if (!canLay(kind, obj.x, obj.z, obj.level)) return player.anim(-1)
        val step = SkillClock.step(player, tick, LAY_TICKS)
        if (!repeat) {
            player.anim(kind.layAnim)
            world.message(player, HunterLines.LAYING)
        }
        if (step == ClockStep.DUE) {
            if (!world.groundObjs.useOne(player, obj)) return player.anim(-1)
            stand(kind, obj.x, obj.z, obj.level)
            stepOff(player, obj.x, obj.z)
            return
        }
        world.repeatOp(player, Op(OpTarget.GROUND_OBJ, obj.id, option, groundObj = obj, repeat = true))
    }

    /** Puts [player]'s trap of [kind] up on [x], [z], [level], set on [anchor] (on its [rotation] side) if it has one. */
    private fun ScriptContext.stand(kind: TrapKind, x: Int, z: Int, level: Int, anchor: ZoneLoc? = null, rotation: Int = 0) {
        val trap = Trap(player.saveKey, kind, x, z, level, anchor, rotation)
        show(trap, TrapState.SET, kind.set)
        trap.stateTick = tick
        trap.collapseTick = tick + TRAP_TICKS
        traps += trap
    }

    /** Whether [player] may lay [kind] on [x], [z], set on [anchor] if it has one, telling them why not. */
    private fun ScriptContext.canLay(kind: TrapKind, x: Int, z: Int, level: Int, anchor: ZoneLoc? = null): Boolean {
        val hunter = player.skills.currentLevel(Skill.HUNTER)
        if (hunter < kind.level) {
            world.message(player, HunterLines.TRAP_LEVEL, "level" to kind.level)
            return false
        }
        val max = maxTrapsAt(hunter)
        if (of(player.saveKey).size >= max) {
            world.message(player, HunterLines.TOO_MANY, "count" to max)
            return false
        }
        val occupied = !kind.fits(world.locs.at(x, z, level)) || world.locs.reverting(x, z, level) ||
            (anchor != null && (!world.locs.exists(anchor) || world.locs.reverting(anchor.x, anchor.z, anchor.level))) ||
            // A standing trap keeps its own tile and its tree's whatever it shows: a net trap's 1 by 2 loc may start on
            // the tree's tile and leave the net's without a loc of its own.
            traps.any { it.level == level && (it.covers(x, z) || anchor != null && it.covers(anchor.x, anchor.z)) }
        // Ours: an instance's locs answer only the ones it lets through, so a trap there could never be taken down.
        if (occupied || world.instances.inDynamicArea(x, z) || collision[x, z, level] and CollisionFlag.BLOCK_WALK != 0) {
            world.message(player, HunterLines.CANT_HERE)
            return false
        }
        return true
    }

    /**
     * Set-trap on a young tree: the net goes on the player's tile beside it, so they must stand square to one of its
     * sides. The tree bends while the player sets it, and the trap stands on the skill clock's fourth tick, the net
     * trap leaving the backpack only then, so a trap cut short loses nothing and the tree springs back by itself.
     */
    private suspend fun ScriptContext.setTrap(kind: NetTrapKind, tree: ZoneLoc) {
        val rotation = NetTrapKind.sideOf(player.x - tree.x, player.z - tree.z) ?: return world.message(player, HunterLines.CANT_HERE)
        if (player.inventory.count(kind.item) == 0L) return world.message(player, HunterLines.NO_NET)
        if (player.skills.currentLevel(Skill.HUNTER) < kind.level) return world.message(player, HunterLines.NET_LEVEL, "level" to kind.level)
        val x = player.x
        val z = player.z
        if (!canLay(kind, x, z, player.level, tree)) return
        player.anim(kind.layAnim)
        world.message(player, HunterLines.LAYING)
        // The skill clock as lay() steps it: a running clock's due tick stands, else a new cycle starts now.
        val due = if (player.actionDelay >= tick) player.actionDelay else (tick + LAY_TICKS - 1).also { player.actionDelay = it }
        val wait = (due - tick).toInt()
        world.locs.replace(tree, kind.setting, revertAfter = wait + 2)
        if (wait > 0) delay(wait)
        val bent = world.locs.find(kind.setting, tree.x, tree.z, tree.level) ?: return
        world.locs.restore(bent.x, bent.z, bent.level, bent.layer)
        if (!canLay(kind, x, z, tree.level, tree)) return
        val slot = player.inventory.contents().firstOrNull { it.second.id == kind.item }?.first ?: return world.message(player, HunterLines.NO_NET)
        if (player.inventory.remove(slot, 1) != 1) return
        stand(kind, x, z, tree.level, tree, rotation)
        stepOff(player, x, z)
    }

    /**
     * Dismantle on a set or failed trap, Check on a full one, and Reset on either: only its owner may. The trap and
     * what it caught come off at once and the player stands 3 ticks; with [reset] a new trap is laid where it stood.
     * While a reset waits, the trap's item lies on its tile, the owner's, so a death or a cut-off script loses nothing.
     */
    private suspend fun ScriptContext.dismantle(kind: TrapKind, loc: ZoneLoc, reset: Boolean) {
        val trap = showing(loc) ?: return
        if (trap.owner != player.saveKey) return world.message(player, HunterLines.NOT_YOURS)
        val item = world.objTypes[kind.item] ?: return
        val creature = trap.creature.takeIf { trap.state == TrapState.FULL }
        if (trap.state == TrapState.CATCHING || trap.state == TrapState.FAILING) return
        // Room for every drop, and for the trap unless it is laid again.
        val gains = creature?.loot.orEmpty().map { it.obj to it.countMax } + if (reset) emptyList() else listOf(kind.item to 1)
        if (player.inventory.freeSlots < slotsNeeded(player, gains)) return world.message(player, HunterLines.NO_SPACE)
        if (reset && !world.groundObjs.fits(item, 1, trap.x, trap.z, trap.level, player.saveKey)) return world.message(player, HunterLines.CANT_HERE)
        remove(trap)
        player.anim(if (creature != null) CHECK_ANIM else DISMANTLE_ANIM)
        if (creature != null) {
            for (drop in creature.loot) {
                val type = world.objTypes[drop.obj] ?: continue
                world.groundObjs.give(player, type, world.random.between(drop.count, drop.countMax))
            }
            player.skills.addTenths(Skill.HUNTER, creature.xpTenths)
            val name = world.npcTypes[creature.npc]?.name?.lowercase() ?: "creature"
            world.message(player, HunterLines.CAUGHT, "creature" to name)
        } else {
            world.message(player, HunterLines.DISMANTLED)
        }
        if (reset) {
            // Laid again where it stood, from the item left on the tile, as laying from the floor does.
            val obj = world.groundObjs.addPrivate(
                item, 1, trap.x, trap.z, trap.level, player.saveKey,
                osrsps.game.GroundObjs.DROP_PRIVATE_TICKS, osrsps.game.GroundObjs.DROP_DESPAWN_TICKS,
            )
            delay(PICK_UP_TICKS)
            if (canLay(kind, trap.x, trap.z, trap.level, trap.anchor)) {
                player.anim(kind.layAnim)
                world.message(player, HunterLines.LAYING)
                player.actionDelay = tick + LAY_TICKS - 1
                delay(LAY_TICKS - 1)
                if (at(trap.x, trap.z, trap.level) == null && canLay(kind, trap.x, trap.z, trap.level, trap.anchor) && world.groundObjs.useOne(player, obj)) {
                    stand(kind, trap.x, trap.z, trap.level, trap.anchor, trap.rotation)
                    stepOff(player, trap.x, trap.z)
                    return
                }
            }
            // Not laid: the item comes back to the player's backpack, if it is still there for them and there is room;
            // otherwise it stays where it lies, theirs, rather than dropping on a tile that may be full (Codex review).
            val room = player.inventory.freeSlots > 0 || (item.stackable && player.inventory.count(item.id) > 0L)
            if (room && world.groundObjs.useOne(player, obj)) player.inventory.add(item, 1)
            return
        }
        world.groundObjs.give(player, item, 1)
        delay(PICK_UP_TICKS)
    }

    /** The free slots [gains] (obj, most it could be) take: none for a stack already held, one per non-stacking obj. */
    private fun slotsNeeded(player: Player, gains: List<Pair<Int, Int>>): Int = gains.sumOf { (id, count) ->
        val type = world.objTypes[id]
        when {
            type == null -> 0
            type.stackable -> if (player.inventory.count(id) > 0) 0 else 1
            else -> count
        }
    }

    private fun ScriptContext.investigate(loc: ZoneLoc) {
        val trap = showing(loc) ?: return
        world.message(player, if (trap.owner == player.saveKey) trap.kind.investigate else HunterLines.NOT_YOURS)
    }

    /** Walks [player] a step off the trap's tile: west if they can, else east, south, north. */
    private fun stepOff(player: Player, x: Int, z: Int) {
        if (player.x != x || player.z != z) return
        for ((dx, dz) in STEPS) {
            if (!Steps.canStep(collision, player.level, x, z, dx, dz)) continue
            player.waypoints.clear()
            player.waypoints.add(Tile(x + dx, z + dz))
            return
        }
    }

    companion object {
        /** How many traps a player with Hunter at [level] may have laid at once. */
        fun maxTrapsAt(level: Int): Int = when {
            level >= 80 -> 5
            level >= 60 -> 4
            level >= 40 -> 3
            else -> 2
        }

        /** A minute: RuneLite's HunterTrap.TRAP_TIME. */
        const val TRAP_TICKS = 100

        /** Mod Ash via the Wiki: about every 3 ticks. */
        const val LOOK_TICKS = 3L

        /** Mod Ash via the Wiki: 2 tiles. */
        const val LOOK_RANGE = 2

        /** The Tick manipulation page: laying takes four ticks on the skill clock. */
        const val LAY_TICKS = 4

        /** The Tick manipulation page: picking a trap up takes three ticks. */
        const val PICK_UP_TICKS = 3

        /** Guessed: how long a lured creature has to reach the trap. */
        const val LURE_TICKS = 10

        /** Guessed: the struggle shown before the trap reads as full. */
        const val CATCHING_TICKS = 2

        /** Inferred from hunting_ojibway_fail's length (59 client cycles, about 2 ticks). */
        const val FAILING_TICKS = 2

        /** Guessed: a caught creature's respawn. */
        const val RESPAWN_TICKS = 10

        /** The Wiki's Net trap page: a collapsed trap's item disappears if not picked up within the next minute. */
        const val COLLAPSED_DESPAWN_TICKS = 100

        /** Guessed: how far the owner may go before their traps collapse. */
        const val OWNER_RANGE = 32

        /** The cache's ops: op1 Dismantle (set, failed), Check (full); op2 Investigate (set), Reset (full, failed). */
        const val DISMANTLE = 1
        const val CHECK = 1
        const val INVESTIGATE = 2
        const val RESET = 2

        /** The young tree's op1. */
        const val SET_TRAP = 1

        /** The item's own ops when the type doesn't say (tests): iop1 Lay, ground op4 Lay. */
        const val LAY_HELD = 1
        const val LAY_GROUND = 4

        /** Guessed: picking a trap up plays human_laytrap again, as live shows no other animation for it that we know of. */
        const val DISMANTLE_ANIM = 5208
        const val CHECK_ANIM = 5208

        private val STEPS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
    }
}
