package osrsps.game.content

import java.util.WeakHashMap
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc

/** The events' messages: the tuber's two are the Wiki's (Gout tuber); the rest are held for a recording. */
object TaiBwoEventLines : Lines("tbw_event", "Gout tuber; Tai Bwo Wannai Cleanup") {
    val TUBER = line("tuber", "a gout tuber event: the jungle hacked away to reveal an unusual plant")
    val NOT_YOURS = line("not_yours", "a spade on someone else's gout tuber plant")
    val DUG = line("dug", "INFERRED words wanted: the gout tuber dug up")
    val NO_ROOM = line("no_room", "INFERRED words wanted: a spade on the gout tuber plant with no room for the tuber")
    val GEMS = line("gems", "INFERRED words wanted: a gem rock event, the jungle hacked away to show it")
    val MONSTER = line("monster", "INFERRED words wanted: a creature leaping out of the jungle")
}

/**
 * The Tai Bwo Wannai Cleanup's events while hacking (Karamja step K4.2b; OSRS Wiki: Light Jungle § Events, Tai Bwo
 * Wannai Cleanup § Events, Gout tuber, Gem rocks, Jungle spider, Bush snake, Large mosquito, Mosquito swarm and
 * Tribesman, read 2026-11-19).
 *
 * - **The chance.** Each player has a counter, 0 to 7: the cycle's skill tick adds 1 (up to 7), and each tick between a
 *   cycle's first and its skill tick rolls [counter] out of 101 for an event, which sets it back to 0. It lasts until
 *   logout (the [Player] goes, and the map with it).
 * - **Which event.** First the gout tuber, 1/100 at Woodcutting 10 to 1/33 at 99, in between linearly ([tuberChance]);
 *   if that misses, the table out of 99 ([TABLE]): jungle spider 22, tribesman 21, bush snake 21, large mosquito 8, the
 *   level 17 swarm 7, the level 20 swarm 6, a gem rock 5 and the three Broodoo victims 4, 3 and 2 (food, poison and
 *   disease, built in K4.2c: until then they fall through to no event).
 * - **The tuber.** The jungle becomes the Gout Tuber Plant (9033) for 10 minutes, the finder's alone while they stay
 *   logged in; a spade on it gives the gout tuber (6311). Someone else's is refused ([TaiBwoEventLines.NOT_YOURS]),
 *   and so is a dig with a full backpack, the plant left for later.
 * - **The gem rock** (9030, then 9031 and 9032): Mining 40, three gems from the gem rock table at 65 XP each, then gone.
 * - **The creatures** (6271, 6103, 6272, 6273, 6402, 6407) come out beside the player and attack them; killing one gives
 *   its favour to whoever killed it ([Creature.favour]: 1%, or 1 to 2% for the spider and the tribesman). Wild jungle
 *   spiders (3020) give the spider's too, once the killer has had Murcaily's first trading sticks.
 *
 * Guessed: an event ends the hack (the jungle's tile changes, or something attacks); the creature comes out on the
 * first free tile beside the player and leaves after 50 minutes (the Wiki's Broodoo time, taken for all) or once its
 * target has gone; the attackable type straight away (the cache's `_instant` tribesman, with no options, left out);
 * the gem rock lasting 10 minutes as the tuber does and its stages one per gem; the jungle growing back
 * [TaiBwoCleanup.REGROW_TICKS] after the tuber is dug or the rock mined out; the event creatures' favour only after
 * Murcaily's first sticks, as the Wiki says of the wild spiders; the dig taking a tick with the spade animation.
 */
object TaiBwoEvents {
    const val COUNTER_MAX = 7
    const val CHANCE_OUT_OF = 101
    const val TABLE_TOTAL = 99

    const val TUBER_PLANT = 9033
    val GEM_ROCKS = listOf(9030, 9031, 9032)
    const val GOUT_TUBER = 6311
    const val SPADE = 952
    const val DIG_ANIM = 830

    /** Ten minutes, the tuber's despawn. */
    const val PLANT_TICKS = 1000

    /** Fifty minutes (600 ms ticks), as the Wiki gives for a Broodoo victim. */
    const val CREATURE_TICKS = 5000

    /** How often an event creature checks its player is still about, and how far off counts as gone. GUESSED. */
    const val WATCH_TICKS = 5
    const val LEAVE_DISTANCE = 15

    const val SURPRISED = 2390
    const val WILD_SPIDER = 3020

    enum class Creature(val npc: Int, val weight: Int, val favourLow: Int, val favourHigh: Int, val surprise: Int) {
        SPIDER(6271, 22, 10, 20, 2391),
        TRIBESMAN(6407, 21, 10, 20, 2400),
        SNAKE(6103, 21, 10, 10, 2392),
        MOSQUITO(6272, 8, 10, 10, -1),
        SWARM_3(6273, 7, 10, 10, -1),
        SWARM_5(6402, 6, 10, 10, -1),
    }

    sealed interface Event {
        data object Tuber : Event
        data object GemRock : Event
        data class Monster(val creature: Creature) : Event

        /** A Broodoo victim, for K4.2c: no event yet. */
        data class Broodoo(val weight: Int) : Event
    }

    /** The table rolled when the tuber misses, out of [TABLE_TOTAL]. */
    val TABLE: List<Pair<Event, Int>> =
        Creature.entries.map { Event.Monster(it) to it.weight } +
            listOf(Event.GemRock to 5, Event.Broodoo(4) to 4, Event.Broodoo(3) to 3, Event.Broodoo(2) to 2)

    /** The tuber's chance at Woodcutting [level], as a fraction: 1/100 at 10 to 1/33 at 99, linear between. */
    fun tuberChance(level: Int): Double {
        val l = level.coerceIn(10, 99)
        return 0.01 + (1.0 / 33 - 0.01) * (l - 10) / 89
    }

    private val counters = WeakHashMap<Player, Int>()

    /** Who found each tuber plant, by tile: their [Player.saveKey], so a logged-out finder isn't kept in memory. */
    private val tuberOwners = HashMap<Long, String>()

    /** The jungle each plant or gem rock came out of, by tile. */
    private val jungleAt = HashMap<Long, TaiBwoCleanup.Jungle>()

    /** The tick each gem rock goes back to jungle, by tile, carried across its stages. */
    private val rockExpiry = HashMap<Long, Long>()

    /** Events forced by `::tbwevent`, for the next roll of each player's. */
    private val forced = WeakHashMap<Player, Event>()

    fun counter(player: Player): Int = counters[player] ?: 0

    /** The skill tick of a cycle: the counter goes up by one. */
    fun skillTick(player: Player) {
        counters[player] = minOf(COUNTER_MAX, counter(player) + 1)
    }

    /** `::tbwevent`: [event] at the next roll, whatever the counter. */
    fun force(player: Player, event: Event) {
        forced[player] = event
    }

    /** One of the ticks before the skill tick: true when an event came of it (the hack then ends). */
    fun roll(world: World, player: Player, loc: ZoneLoc, jungle: TaiBwoCleanup.Jungle): Boolean {
        val event = forced.remove(player) ?: run {
            if (world.random.nextInt(CHANCE_OUT_OF) >= counter(player)) return false
            counters[player] = 0
            pick(world, player.skills.currentLevel(Skill.WOODCUTTING))
        }
        counters[player] = 0
        return start(world, player, loc, jungle, event)
    }

    /** Which event: the tuber's roll, then the table. */
    fun pick(world: World, level: Int): Event {
        if (world.random.nextLong().ushr(11).toDouble() / (1L shl 53) < tuberChance(level)) return Event.Tuber
        var roll = world.random.nextInt(TABLE_TOTAL)
        for ((event, weight) in TABLE) {
            if (roll < weight) return event
            roll -= weight
        }
        error("the table's weights add to ${TABLE.sumOf { it.second }}, not $TABLE_TOTAL")
    }

    private fun start(world: World, player: Player, loc: ZoneLoc, jungle: TaiBwoCleanup.Jungle, event: Event): Boolean =
        when (event) {
            Event.Tuber -> {
                val plant = world.locs.replace(loc, TUBER_PLANT)
                if (plant != null) {
                    world.locs.restoreAfter(loc.x, loc.z, loc.level, loc.layer, PLANT_TICKS)
                    tuberOwners[key(loc)] = player.saveKey
                    jungleAt[key(loc)] = jungle
                    forgetAfter(world, loc, PLANT_TICKS)
                    world.message(player, TaiBwoEventLines.TUBER)
                }
                plant != null
            }
            Event.GemRock -> {
                val rock = world.locs.replace(loc, GEM_ROCKS.first())
                if (rock != null) {
                    world.locs.restoreAfter(loc.x, loc.z, loc.level, loc.layer, PLANT_TICKS)
                    jungleAt[key(loc)] = jungle
                    rockExpiry[key(loc)] = world.tick + PLANT_TICKS
                    forgetAfter(world, loc, PLANT_TICKS)
                    world.message(player, TaiBwoEventLines.GEMS)
                }
                rock != null
            }
            is Event.Monster -> spawn(world, player, event.creature)
            is Event.Broodoo -> false
        }

    private fun spawn(world: World, player: Player, creature: Creature): Boolean {
        val type = world.npcTypes[creature.npc] ?: return false
        val (x, z) = besidePlayer(world, player)
        val npc = world.spawnNpc(NpcSpawn(type, x, z, player.level, wander = 0, source = "tai bwo wannai event", respawns = false)) ?: return false
        if (creature.surprise >= 0) npc.effects.anim(creature.surprise)
        player.anim(SURPRISED)
        world.message(player, TaiBwoEventLines.MONSTER)
        world.meleeCombat.aggress(npc, player, world.tick)
        watch(world, npc, player, world.tick + CREATURE_TICKS)
        return true
    }

    /**
     * Every [WATCH_TICKS] the creature is checked: it goes once [until] is reached, or once the player it came out for
     * has logged out, changed level or gone more than [LEAVE_DISTANCE] tiles away, so it never turns on someone else
     * (Codex review on #1033).
     */
    private fun watch(world: World, npc: Npc, player: Player, until: Long) {
        world.npcScripts.later(WATCH_TICKS) {
            if (world.npcs[npc.index] !== npc || npc.dead) return@later
            val gone = player.loggedOut || player.level != npc.level ||
                maxOf(kotlin.math.abs(player.x - npc.x), kotlin.math.abs(player.z - npc.z)) > LEAVE_DISTANCE
            if (gone || world.tick >= until) world.removeNpc(npc) else watch(world, npc, player, until)
        }
    }

    /** The first tile beside [player] that can be stood on, west, east, south then north; their own when none. */
    private fun besidePlayer(world: World, player: Player): Pair<Int, Int> {
        for ((dx, dz) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
            if (world.canStand(player.x + dx, player.z + dz, player.level)) return player.x + dx to player.z + dz
        }
        return player.x to player.z
    }

    private fun key(loc: ZoneLoc): Long = (loc.level.toLong() shl 40) or (loc.x.toLong() shl 20) or loc.z.toLong()

    /** A spade on the plant: the tuber, for its finder (or anyone once they've logged out). */
    private suspend fun osrsps.game.script.ScriptContext.dig(world: World, loc: ZoneLoc) {
        arriveDelay()
        val owner = tuberOwners[key(loc)]?.let { world.playerBySaveKey(it) }
        if (owner != null && owner !== player && !owner.loggedOut) return world.message(player, TaiBwoEventLines.NOT_YOURS)
        val tuber = world.objTypes[GOUT_TUBER] ?: return
        // No room for it: the plant stays (Codex review on #1033).
        if (player.inventory.freeSlots == 0) return world.message(player, TaiBwoEventLines.NO_ROOM)
        // Locked to whoever starts digging.
        tuberOwners[key(loc)] = player.saveKey
        player.anim(DIG_ANIM)
        delay(1)
        if (!world.locs.exists(loc)) return
        if (player.inventory.add(tuber, 1) == 0) return world.message(player, TaiBwoEventLines.NO_ROOM)
        world.message(player, TaiBwoEventLines.DUG)
        cleared(world, loc)
    }

    /**
     * Once the plant or rock has had its time and the jungle is back, what was kept for its tile goes too (the expiry
     * doesn't pass through [cleared]; Codex review on #1033). A newer event on the tile keeps its own.
     */
    private fun forgetAfter(world: World, loc: ZoneLoc, ticks: Int) {
        val k = key(loc)
        world.npcScripts.later(ticks + 1) {
            if (world.locs.at(loc.x, loc.z, loc.level).none { it.id == TUBER_PLANT || it.id in GEM_ROCKS }) {
                tuberOwners.remove(k)
                jungleAt.remove(k)
                rockExpiry.remove(k)
            }
        }
    }

    /** The plant dug or the rock mined out: the chopped-away jungle, growing back as after a hack. */
    private fun cleared(world: World, loc: ZoneLoc) {
        tuberOwners.remove(key(loc))
        rockExpiry.remove(key(loc))
        val jungle = jungleAt.remove(key(loc))
        if (jungle == null) {
            world.locs.remove(loc)
        } else {
            world.locs.replace(loc, jungle.chopped)
        }
        world.locs.restoreAfter(loc.x, loc.z, loc.level, loc.layer, TaiBwoCleanup.REGROW_TICKS)
    }

    /** A gem from the rock: its next stage, or gone after the third. */
    private fun mined(world: World, loc: ZoneLoc) {
        val stage = GEM_ROCKS.indexOf(loc.id)
        if (stage in 0 until GEM_ROCKS.size - 1) {
            world.locs.replace(loc, GEM_ROCKS[stage + 1]) ?: return
            // The replace drops the rock's expiry: what was left of it carries on (Codex review on #1033).
            val left = ((rockExpiry[key(loc)] ?: (world.tick + PLANT_TICKS)) - world.tick).coerceAtLeast(1)
            world.locs.restoreAfter(loc.x, loc.z, loc.level, loc.layer, left.toInt())
        } else {
            cleared(world, loc)
        }
    }

    /** A creature killed: its favour to the killer, once they've had Murcaily's first sticks. */
    private fun killed(world: World, npc: Npc, low: Int, high: Int) {
        val killer = npc.lastAttacker?.takeIf { !it.loggedOut } ?: return
        if (!TaiBwoWannai.has(killer, TaiBwoWannai.Progress.MURCAILY_PAID)) return
        TaiBwoWannai.addFavour(killer, world.random.between(low, high))
    }

    fun register(world: World) {
        world.handlers.onObjOnLoc(TUBER_PLANT, SPADE) { op -> dig(world, op.loc!!) }
        for (id in GEM_ROCKS) world.mining.onGemRock(id) { _, loc -> mined(world, loc) }
        for (creature in Creature.entries) {
            world.npcScripts.onDeath(listOf(creature.npc)) { killed(world, it, creature.favourLow, creature.favourHigh) }
        }
        world.npcScripts.onDeath(listOf(WILD_SPIDER)) { killed(world, it, Creature.SPIDER.favourLow, Creature.SPIDER.favourHigh) }
    }
}
