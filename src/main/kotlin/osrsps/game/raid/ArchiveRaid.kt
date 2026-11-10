package osrsps.game.raid

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.combat.CombatLines
import osrsps.game.combat.DeathPlace
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Lines
import osrsps.game.encounter.Encounter
import osrsps.game.instance.Instance
import osrsps.game.instance.InstancePart
import osrsps.game.npc.Npc
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc
import osrsps.map.LocShape
import osrsps.map.Tile

private val logger = KotlinLogging.logger {}

/**
 * The Alliance Navigator's lines to whoever follows him out of a room that is won, each with the player's name. Seen in
 * Sam's recordings of 9 November 2026 (clip notes): [ONWARD] after Bork and the Smorks, [VAULT] after Xarth, the fourth
 * room. Guessed: [ONWARD] after the third room too.
 */
object ArchiveNavigatorLines : Lines("tfa_navigator", "The_Fractured_Archive") {
    val ONWARD = line("onward", "followed out of the second or third room: his one line, with the player's name", "name")
    val VAULT = line("vault", "followed out of the fourth room: his one line, with the player's name", "name")
}

/** Which of a row's two doors a room is behind, as the nexus faces north: west is the Wiki's left. */
enum class DoorSide { WEST, EAST }

/**
 * One of The Fractured Archive's rooms: the boss in it, the [stage] of the run it is fought at (1 the Guardian, 2 to 4
 * the three rows of doors, 5 Ocellus), and its map square ([squareX], [squareZ]) in the 241.4 cache, on [level]. Tiles
 * are the cache's; [ArchiveRaids] maps them into a run's instance.
 *
 * From the cache: each boss room's map square, told by its locs' names (`tfa_corpse_ezekiel01`, `tfa_moia_bones`,
 * `tfa_xarth01_corpse`, `tfa_dagonhai_book01`, `tfa_soulprison`, Bork's forge), its Passageway (`tfa_exit`, 62765, Exit)
 * and its Archive transporter (Use, Quick-use); the nexus's six doors (`tfa_nexus_section1_door_west` and on, Enter,
 * Quick-enter); the Guardian's corpse (`tfa_corpse_guardian01`, 5 by 5) in the middle of the nexus on the nexus's two
 * later levels; and the arena north of the nexus, past its Passageway, on its level 1 with The Blade. From the
 * Wiki: the order, the Guardian first and Ocellus last, and the pairs. From the tsps capture notes and Sam's recordings
 * of 2026-11-09: Ocellus's arena is that square's level 1, where The Blade is too. Inferred: that each west door leads
 * to the west room of its pair (all three west rooms are in map column 63, the east ones in 65), which room is whose by
 * its locs, and that the Guardian is fought in the nexus's hall, where its corpse lies.
 */
enum class ArchiveRoom(
    val boss: String,
    val stage: Int,
    val side: DoorSide?,
    val squareX: Int,
    val squareZ: Int,
    /** The nexus door into it; -1 for none (the Guardian's is the nexus's hall, Ocellus's is past the nexus's Passageway). */
    val door: Int,
    /**
     * Where a player going in lands, and where `::raid tp` takes them: the tsps capture notes' arrivals for Ezekiel's,
     * Bork's, Dreadnoth's and Xarth's rooms; the rest guessed.
     */
    val entryX: Int,
    val entryZ: Int,
    /** Its Archive transporter; -1 for none. */
    val transporter: Int,
) {
    GUARDIAN("Archive Guardian", 1, null, 64, 87, -1, 4127, 5590, -1),
    EZEKIEL("Ezekiel", 2, DoorSide.WEST, 63, 86, ArchiveRaids.DOOR_1_WEST, 4079, 5536, ArchiveRaids.TRANSPORTER),
    BORK("Bork and the Smorks", 2, DoorSide.EAST, 65, 87, ArchiveRaids.DOOR_1_EAST, 4173, 5599, ArchiveRaids.TRANSPORTER),
    MOIA("Moia", 3, DoorSide.WEST, 63, 88, ArchiveRaids.DOOR_2_WEST, 4084, 5663, ArchiveRaids.TRANSPORTER_MOIA),
    DREADNOTH("Dreadnoth the Jellied", 3, DoorSide.EAST, 65, 88, ArchiveRaids.DOOR_2_EAST, 4174, 5664, ArchiveRaids.TRANSPORTER_JELLY),
    DAGONHAI("Dagon'hai Elder", 4, DoorSide.WEST, 63, 87, ArchiveRaids.DOOR_3_WEST, 4086, 5599, ArchiveRaids.TRANSPORTER),
    XARTH("Xarth", 4, DoorSide.EAST, 65, 86, ArchiveRaids.DOOR_3_EAST, 4175, 5535, ArchiveRaids.TRANSPORTER),
    OCELLUS("Ocellus", 5, null, 64, 88, -1, 4128, 5637, -1),
    ;

    /**
     * The level its fight is on in a run's instance: the cache's own, which the instance copies as it is. Ocellus's arena
     * is on the vault's upper floor, level 1 (tsps capture notes; Sam's recordings of 2026-11-09 land a player at
     * 4127-4128,5637 on plane 1 and fight on 4116-4136 x 5665-5685 there); every other room is on level 0.
     */
    val level: Int get() = if (this == OCELLUS) 1 else 0

    /**
     * The nexus tile a player coming back out of the room lands on: in front of its door, in front of the nexus's
     * Passageway for Ocellus's, and the nexus's entrance for the Guardian's hall. Guessed.
     */
    val doorFront: Tile
        get() = when {
            side == DoorSide.WEST -> Tile(4115, ArchiveRaids.doorZ(stage) + 2)
            side == DoorSide.EAST -> Tile(4139, ArchiveRaids.doorZ(stage) + 2)
            this == OCELLUS -> Tile(ArchiveRaids.PASSAGE_FRONT_X, ArchiveRaids.PASSAGE_FRONT_Z)
            else -> Tile(ArchiveRaids.NEXUS_X, ArchiveRaids.NEXUS_Z)
        }

    companion object {
        fun ofDoor(door: Int): ArchiveRoom? = entries.firstOrNull { it.door == door }

        /** The rooms of [stage]: the pair behind its row of doors, or the one room. */
        fun atStage(stage: Int): List<ArchiveRoom> = entries.filter { it.stage == stage }

        /** By name, ignoring case, its boss's first word or its own name: `ezekiel`, `bork`, `dagonhai`. */
        fun named(name: String): ArchiveRoom? = entries.firstOrNull {
            it.name.equals(name, ignoreCase = true) || it.boss.substringBefore(' ').replace("'", "").equals(name.replace("'", ""), ignoreCase = true)
        }
    }
}

/**
 * One party's run of The Fractured Archive, in its own [instance]. See [ArchiveRaids].
 */
class ArchiveRun internal constructor(val id: Int, val party: Party, val instance: Instance) {
    /** Who has gone in, by [Player.saveKey], in the order they went in. */
    val entered = LinkedHashSet<String>()

    /** The room the party chose at each stage of doors, once someone has gone through one of its doors. */
    val chosen = HashMap<Int, ArchiveRoom>()

    /** Rooms whose fight has begun, and rooms done. */
    val started = LinkedHashSet<ArchiveRoom>()
    val cleared = LinkedHashSet<ArchiveRoom>()

    /** Who was in each room as its fight began, by [Player.saveKey]. */
    val presentAtStart = HashMap<ArchiveRoom, Set<String>>()

    /** The damage each player has done in each room, by [Player.saveKey]. */
    val damage = HashMap<ArchiveRoom, MutableMap<String, Int>>()

    /** Contribution points, by [Player.saveKey]. */
    val points = LinkedHashMap<String, Int>()

    /** Deaths in the run, by [Player.saveKey], for the loot to come (raid step 29). */
    val deaths = LinkedHashMap<String, Int>()

    /** Who is down now, by [Player.saveKey]: dead in this room, with the party still standing. Up again once it is done. */
    val downed = LinkedHashSet<String>()

    /** The level of the cache's nexus (map square 64,87) the instance shows; see [ArchiveRaids.nexusLevel]. */
    var nexusLevel: Int = ArchiveRaids.NEXUS_START
        internal set

    /** The Guardian in its hall: waiting to be touched, then the boss once its fight begins; null once it is done. */
    var guardian: Npc? = null
        internal set

    /** The boss fights going on, by room; a room's goes once its boss dies, and the room is done. */
    val fights = LinkedHashMap<ArchiveRoom, Encounter>()

    /** Ocellus's hooded figure in his arena, waiting for the party to ready up; null once his fight begins ([Ocellus]). */
    var ocellusFigure: Npc? = null
        internal set

    /** Who has readied up at the hooded figure, by [Player.saveKey], and the tick the first did; -1 before anyone. */
    val ready = LinkedHashSet<String>()
    var firstReadyTick: Long = -1
        internal set

    /** Rooms won whose Alliance Navigator is still to come, by the tick he does ([ArchiveRaids.NAVIGATOR_DELAY]). */
    internal val navigatorDue = HashMap<ArchiveRoom, Long>()

    /** The tick the last room was done on; a member who fell before it gets up standing. -1 before any. */
    var clearedTick: Long = -1
        internal set

    /** The whole party went down: it is out of the run, with graves in the lobby. */
    var wiped: Boolean = false
        internal set

    /** Ocellus's room is done. The reward room is raid step 28. */
    val completed: Boolean get() = ArchiveRoom.OCELLUS in cleared

    /** The stage the party is at: the first not done. */
    val stage: Int get() = (1..ArchiveRaids.STAGES).firstOrNull { s -> ArchiveRoom.atStage(s).none { it in cleared } } ?: (ArchiveRaids.STAGES + 1)

    override fun toString() = "Archive run #$id for party #${party.id}"
}

/**
 * The Fractured Archive's raid flow (raid step 15): the Archive Door into a party's own copy of the raid, the
 * Guardian's room first, then one room of each row of doors in the nexus, then Ocellus; rooms that unlock as the one
 * before is done; contribution points; deaths that leave the party standing, and the wipe that sends it back to the
 * lobby with gravestones.
 *
 * From the OSRS Wiki (The Fractured Archive, and the "Everything You Need To Know" update), read 2026-11-02:
 * - "you can choose to tackle the left door or the right door on each row": the Guardian always first and Ocellus last,
 *   with Ezekiel or Bork, then Moia or Dreadnoth, then Xarth or the Dagon'hai Elder, in fixed pairs.
 * - "Each room has a finite amount of contribution it can dish out": a player's share is their damage, minions'
 *   included, and the two rooms of a pair give the same. Individual deaths count for less than team deaths.
 * - A wipe removes the team from the Archive to the lobby, "where a normal gravestone appears nearby" (Theatre of
 *   Blood's system), and doesn't break the party up (28 October 2026). Unprotected items are lost.
 * - Bosses scale to the players present, a lone player as two.
 *
 * Guessed, until a recording shows a run (each also in the README):
 * - Any party member opens the run through the Archive Door, a player in no party as a party of one, and the others
 *   can follow until the Guardian's fight begins; nobody comes in after, or back after leaving.
 * - Going through one door of a row chooses it for the party: the other stays shut for the run. A row's doors open
 *   once the room before is done. Enter and Quick-enter both go straight in, as do the transporter's Use and
 *   Quick-use: their difference, likely a warning first, isn't known.
 * - The Passageway goes back to the nexus at any time, in front of the room's door; the transporter, once the room is
 *   done. Before then Bork's room's transporter puts the player on the arena's floor (tsps capture notes: 4184,5599),
 *   past a gap in its west wall too narrow for him. Ocellus's room is past the nexus's own Passageway (`tfa_exit_large`), open once the fourth room is done.
 * - STAND-IN until the reward room (step 28): once Ocellus's room is done, a Passageway at its south edge leaves to the
 *   lobby; the cache has no way out of that room.
 * - The nexus is the cache's map square 64,87 on one of its four levels: 0 at the start, 2 (the Guardian's corpse and a
 *   camp of crates and barricades) once the Guardian is done, 3 (the same and the Passageway north) once the fourth
 *   room is done. The Guardian is fought in the nexus's hall, from its first row of doors north; the Archive Door lands
 *   in the entrance south of it, where the party gathers.
 * - A room's fight begins as the first party member stands in it. A room whose boss isn't built yet is done as soon
 *   as it begins: that is what lets a run go through empty rooms. The Guardian's begins instead as a member touches
 *   it, waiting in the hall as the cache's "Inert Guardian" (`tfa_guardian_start`, Touch) from the run's start; its
 *   party size is everyone standing in the run then ([ArchiveGuardian], raid step 16).
 * - The whole of a run's instance is multi-way, as a raid of up to eight against one boss must be. Inferred.
 * - A room's points are [ROOM_POINTS], shared by damage among those who did any, or evenly among who was there when it
 *   began if nobody did. The number is made up: the Wiki has none.
 * - A party member who dies with someone still standing keeps everything and is down until the room is done: in the
 *   nexus, in front of the room's door (in the entrance, for the Guardian's hall), and sent out again if they walk back
 *   in. Spectating isn't built.
 * - The wipe: once nobody in the raid is left standing, each member goes to the lobby, in front of the Archive Door,
 *   and what they lose goes in a grave there.
 * - A member who leaves the party is sent to the lobby. Logging out saves them there, as any instance does at its exit.
 * - A member down who gets out (a teleport, leaving the party, logging out) dies of it, with a grave in the lobby, as
 *   if they had been the last standing (Sam's decision of 8 November 2026 on exploit review RAID-2). A reconnect within
 *   the linger is no escape.
 */
class ArchiveRaids(private val world: World) {
    private val runs = LinkedHashMap<Party, ArchiveRun>()
    private var nextId = 1

    fun of(party: Party): ArchiveRun? = runs[party]

    /**
     * Whether [player] is down in a run: out of the fight till its room is done, their items at stake. Wherever they
     * stand, as [leaving] looks: a Home Teleport lands them outside a tick before the escape is settled.
     */
    fun isDown(player: Player): Boolean = runs.values.any { !it.wiped && player.saveKey in it.downed }

    /** The run whose instance holds tile [x], [z]. */
    fun at(x: Int, z: Int): ArchiveRun? {
        val instance = world.instances.at(x, z) ?: return null
        return runs.values.firstOrNull { it.instance === instance }
    }

    fun register() {
        world.multiCombat.dynamic = { _, x, z -> at(x, z) != null }
        world.handlers.onNpc(ArchiveGuardian.DORMANT, ArchiveGuardian.TOUCH) { op -> touch(player, op.npc!!) }
        // The figure is a multi-NPC: an op names the type the player is shown, met or not (242 cache).
        for (type in listOf(Ocellus.INTRO, Ocellus.INTRO_NOT_MET, Ocellus.INTRO_MET)) {
            for (op in Ocellus.READY_OPS) world.handlers.onNpc(type, op) { o -> readyUp(player, o.npc!!) }
        }
        world.handlers.onLoc(ARCHIVE_DOOR, ENTER) { enter(player) }
        for (door in DOORS) {
            world.handlers.onLoc(door, ENTER) { door(player, door) }
            world.handlers.onLoc(door, QUICK_ENTER) { door(player, door) }
        }
        world.handlers.onLoc(PASSAGEWAY, EXIT) { passageway(player) }
        world.handlers.onLoc(NEXUS_PASSAGEWAY, EXIT) { nexusPassageway(player) }
        for (transporter in TRANSPORTERS) {
            world.handlers.onLoc(transporter, USE) { transporter(player) }
            world.handlers.onLoc(transporter, QUICK_USE) { transporter(player) }
        }
        // His options by their names in the cache, whichever slots they are in; the first two if it hasn't them.
        val ops = world.npcTypes[NAVIGATOR]?.ops.orEmpty()
        val follow = ops.indexOf(FOLLOW).takeIf { it >= 0 }?.plus(1) ?: 1
        val quick = ops.indexOf(QUICK_FOLLOW).takeIf { it >= 0 }?.plus(1) ?: 2
        world.handlers.onNpc(NAVIGATOR, follow) { op -> follow(this, op.npc!!, quick = false) }
        world.handlers.onNpc(NAVIGATOR, quick) { op -> follow(this, op.npc!!, quick = true) }
        world.combat.shutOut = ::shutOut
    }

    /** The cache's tile [x], [z] in [run]'s instance. */
    fun tile(run: ArchiveRun, x: Int, z: Int): Tile = Tile(run.instance.baseX + x - FROM_X, run.instance.baseZ + z - FROM_Z)

    /**
     * Whether tile [x], [z] of [run]'s instance is in [room]: its map square, but for the Guardian's, the nexus's hall
     * from its first row of doors north, which leaves out the entrance the Archive Door lands in.
     */
    fun inRoom(run: ArchiveRun, room: ArchiveRoom, x: Int, z: Int): Boolean {
        val base = tile(run, room.squareX shl 6, room.squareZ shl 6)
        val minZ = if (room == ArchiveRoom.GUARDIAN) HALL_Z - (room.squareZ shl 6) else 0
        return x - base.x in 0 until 64 && z - base.z in minZ until 64
    }

    /** The room of [run] tile [x], [z] is in; null for the nexus's entrance, and outside the rooms. */
    fun roomAt(run: ArchiveRun, x: Int, z: Int): ArchiveRoom? = ArchiveRoom.entries.firstOrNull { inRoom(run, it, x, z) }

    /** Whether tile [x], [z] of [run]'s instance is in the nexus, its entrance or its hall. */
    fun inNexus(run: ArchiveRun, x: Int, z: Int): Boolean {
        val base = tile(run, NEXUS_SQUARE_X shl 6, NEXUS_SQUARE_Z shl 6)
        return x - base.x in 0 until 64 && z - base.z in 0 until 64
    }

    /** The players of [run]'s party in its instance now, dead or alive. */
    fun present(run: ArchiveRun): List<Player> =
        world.onlinePlayers.filter { run.instance.contains(it.x, it.z) && it.saveKey in run.entered && world.parties.of(it) === run.party }

    /** Who of [run]'s party is in it, alive and not down. */
    fun standing(run: ArchiveRun): List<Player> = present(run).filter { !it.dead && it.saveKey !in run.downed }

    /** Through the Archive Door: into the party's run, opening one if it has none. */
    fun enter(player: Player) {
        val party = world.parties.start(player)
        val run = runs[party] ?: open(party, player) ?: return
        // Once the Guardian's fight has begun, nobody comes in, or back in: that would be a way to the bank mid-raid.
        if (run.wiped || run.started.isNotEmpty()) return
        run.entered += player.saveKey
        val to = tile(run, NEXUS_X, NEXUS_Z)
        world.teleport(player, to.x, to.z, 0)
    }

    private fun open(party: Party, player: Player): ArchiveRun? {
        val exit = Tile(LOBBY_X, LOBBY_Z)
        val instance = world.instances.create(WIDTH_ZONES, HEIGHT_ZONES, parts(NEXUS_START), exit.x, exit.z, 0)
        if (instance == null) {
            logger.warn { "No room for an Archive instance for ${player.username}'s party" }
            return null
        }
        val run = ArchiveRun(nextId++, party, instance)
        runs[party] = run
        if (built(ArchiveRoom.GUARDIAN)) {
            val at = tile(run, GUARDIAN_X, GUARDIAN_Z)
            val dormant = world.npcTypes[ArchiveGuardian.DORMANT]!!
            run.guardian = world.spawnNpc(NpcSpawn(dormant, at.x, at.z, 0, source = "$run", respawns = false))
        }
        placeOcellusFigure(run)
        logger.info { "$run opened by ${player.username} in $instance" }
        return run
    }

    /**
     * Whether [room]'s boss is built: its fight is in, and for the Guardian the cache's waiting form too. A room whose
     * boss isn't is done as its fight begins.
     */
    fun built(room: ArchiveRoom): Boolean = when (room) {
        ArchiveRoom.GUARDIAN -> world.encounters.defs[ArchiveGuardian.NAME] != null && world.npcTypes[ArchiveGuardian.DORMANT] != null
        ArchiveRoom.EZEKIEL -> world.encounters.defs[Ezekiel.NAME] != null
        ArchiveRoom.BORK -> world.encounters.defs[Bork.NAME] != null
        ArchiveRoom.DREADNOTH -> world.encounters.defs[Dreadnoth.NAME] != null
        ArchiveRoom.XARTH -> world.encounters.defs[Xarth.NAME] != null
        ArchiveRoom.OCELLUS -> world.encounters.defs[Ocellus.NAME] != null && world.npcTypes[Ocellus.INTRO] != null
        else -> false
    }

    /**
     * Touch on the waiting Guardian: its fight begins, if the toucher is in the run and standing and the fight hasn't
     * already. It wakes where it waited, as the levitating form, scaled to everyone standing in the run.
     */
    fun touch(player: Player, npc: Npc) {
        val run = at(npc.x, npc.z) ?: return
        val room = ArchiveRoom.GUARDIAN
        if (run.guardian !== npc || run.wiped || room in run.started || player.saveKey !in run.entered || player.saveKey in run.downed) return
        val def = world.encounters.defs[ArchiveGuardian.NAME] ?: return
        run.started += room
        val standing = standing(run)
        run.presentAtStart[room] = standing.filter { inRoom(run, room, it.x, it.z) }.map { it.saveKey }.toSet()
        world.removeNpc(npc)
        val boss = world.spawnNpc(NpcSpawn(def.boss, npc.x, npc.z, 0, source = "$run", respawns = false))
        if (boss == null) {
            logger.warn { "$run: no room for the Archive Guardian" }
            return
        }
        run.guardian = boss
        world.partyScaling.scale(boss, standing.size.coerceIn(1, PartyScaling.MAX_PARTY))
        boss.effects.anim(ArchiveGuardian.ANIM_WAKE)
        boss.effects.spotanim(0, ArchiveGuardian.SPOT_WAKE, 0)
        run.fights[room] = world.encounters.start(def, boss, { x, z -> inRoom(run, room, x, z) })
        logger.info { "$run began the Guardian's fight with ${standing.size} standing, woken by ${player.username}" }
    }

    /**
     * The first of the party onto Ezekiel's arena (by its transporter, [EZEKIEL_ARENA_X]): his fight begins, him where he
     * waits ([EZEKIEL_X]), scaled to everyone standing in the run, [first] his tank.
     */
    private fun beginEzekiel(run: ArchiveRun, first: Player) {
        val room = ArchiveRoom.EZEKIEL
        val def = world.encounters.defs[Ezekiel.NAME] ?: return
        val at = tile(run, EZEKIEL_X, EZEKIEL_Z)
        val boss = world.spawnNpc(NpcSpawn(def.boss, at.x, at.z, 0, source = "$run", respawns = false))
        // With no NPC slot free, the room isn't started (exploit review round 18, R18A-3): it would then never begin,
        // nor open the next row. The next tick with one of the party in it tries again.
        if (boss == null) {
            logger.warn { "$run: no room for Ezekiel" }
            return
        }
        run.started += room
        val standing = standing(run)
        run.presentAtStart[room] = standing.filter { inRoom(run, room, it.x, it.z) }.map { it.saveKey }.toSet()
        world.partyScaling.scale(boss, standing.size.coerceIn(1, PartyScaling.MAX_PARTY))
        world.meleeCombat.aggress(boss, first, world.tick)
        run.fights[room] = world.encounters.start(def, boss, { x, z -> inRoom(run, room, x, z) })
        logger.info { "$run began Ezekiel's fight with ${standing.size} standing, ${first.username} first in" }
    }

    /**
     * The first of the party onto Xarth's arena (by its transporter, [XARTH_ARENA_X]): his fight begins, him where he
     * stands for good ([XARTH_X]), scaled to everyone standing in the run. With no NPC slot free for him or his heaps
     * the room isn't started, as for Ezekiel and Bork: the next tick with one of the party in it tries again.
     */
    private fun beginXarth(run: ArchiveRun, first: Player) {
        val room = ArchiveRoom.XARTH
        val def = world.encounters.defs[Xarth.NAME] ?: return
        val at = tile(run, XARTH_X, XARTH_Z)
        val boss = world.spawnNpc(NpcSpawn(def.boss, at.x, at.z, 0, source = "$run", respawns = false))
        if (boss == null) {
            logger.warn { "$run: no room for Xarth" }
            return
        }
        val standing = standing(run)
        world.partyScaling.scale(boss, standing.size.coerceIn(1, PartyScaling.MAX_PARTY))
        // Only those on his arena are in the fight: not one still at the room's arrival, behind the wall.
        val fight = world.encounters.start(def, boss, { x, z -> Xarth.inArena(at.x, at.z, x, z) })
        if (fight.adds.count { it.type.id == Xarth.HEAP } < Xarth.Heap.entries.size) {
            world.encounters.stop(fight)
            world.removeNpc(boss)
            logger.warn { "$run: no room for Xarth's heaps" }
            return
        }
        run.started += room
        run.presentAtStart[room] = standing.filter { inRoom(run, room, it.x, it.z) }.map { it.saveKey }.toSet()
        run.fights[room] = fight
        logger.info { "$run began Xarth's fight with ${standing.size} standing, ${first.username} first in" }
    }

    /**
     * The first of the party into Bork's room: his and the Smorks' fight begins, its anchor ([Bork.ANCHOR]) where their
     * cart stands ([BORK_CART_X], guessed: the middle of the room), scaled to everyone standing in the run, [first]
     * Bork's tank.
     */
    private fun beginBork(run: ArchiveRun, first: Player) {
        val room = ArchiveRoom.BORK
        val def = world.encounters.defs[Bork.NAME] ?: return
        val at = tile(run, BORK_CART_X, BORK_CART_Z)
        val standing = standing(run)
        // With no NPC slot free for the anchor, Bork or the Smorks, the room isn't started (as R18A-3 for Ezekiel): it
        // would never begin, nor open the next row. The next tick with one of the party in it tries again.
        val anchor = world.spawnNpc(NpcSpawn(def.boss, at.x, at.z, 0, source = "$run", respawns = false))
        if (anchor == null) {
            logger.warn { "$run: no room for Bork and the Smorks" }
            return
        }
        world.partyScaling.scale(anchor, standing.size.coerceIn(1, PartyScaling.MAX_PARTY))
        val fight = world.encounters.start(def, anchor, { x, z -> inRoom(run, room, x, z) })
        if (fight.adds.size < 2) {
            world.encounters.stop(fight)
            world.removeNpc(anchor)
            logger.warn { "$run: no room for Bork and the Smorks" }
            return
        }
        run.started += room
        run.presentAtStart[room] = standing.filter { inRoom(run, room, it.x, it.z) }.map { it.saveKey }.toSet()
        run.fights[room] = fight
        fight.adds.firstOrNull { it.type.id == Bork.BORK }?.let { world.meleeCombat.aggress(it, first, world.tick) }
        logger.info { "$run began Bork and the Smorks' fight with ${standing.size} standing, ${first.username} first in" }
    }

    /**
     * Ocellus's hooded figure put in his arena, if his room is built and not yet begun and the figure isn't there: as the
     * run opens, and again each tick after, so a run opened with no NPC slot free still gets it once one is (Codex,
     * PR 552), rather than its last room never beginning.
     */
    private fun placeOcellusFigure(run: ArchiveRun) {
        val room = ArchiveRoom.OCELLUS
        if (run.ocellusFigure != null || room in run.started || run.wiped || !built(room)) return
        val at = tile(run, Ocellus.FIGURE_X, Ocellus.FIGURE_Z)
        run.ocellusFigure = world.spawnNpc(NpcSpawn(world.npcTypes[Ocellus.INTRO]!!, at.x, at.z, room.level, source = "$run", respawns = false))
    }

    /**
     * An option on Ocellus's hooded figure: [player] is ready, if they are in the run, standing, in his room and it is
     * the run's next. They are told how many of those standing are ready, and the fight may begin ([ocellusReady]).
     */
    fun readyUp(player: Player, npc: Npc) {
        val run = at(npc.x, npc.z) ?: return
        val room = ArchiveRoom.OCELLUS
        if (run.ocellusFigure !== npc || run.wiped || room in run.started || room.stage != run.stage) return
        if (player.saveKey !in run.entered || player.saveKey in run.downed || roomAt(run, player.x, player.z) != room) return
        if (run.ready.add(player.saveKey) && run.firstReadyTick < 0) run.firstReadyTick = world.tick
        val standing = standing(run)
        val words = world.textFor(player)
        player.message(words[OcellusLines.READY, "ready" to standing.count { it.saveKey in run.ready }, "total" to standing.size])
        player.message(words[OcellusLines.READY_WAIT])
        if (ocellusReady(run)) beginOcellus(run)
    }

    /**
     * Whether Ocellus's fight begins now: everyone standing is in his room and ready, or [Ocellus.READY_TIMEOUT] ticks
     * have passed since the first ready (the game's own words in Sam's recording of 2026-11-09).
     */
    private fun ocellusReady(run: ArchiveRun): Boolean {
        if (run.firstReadyTick < 0) return false
        if (world.tick - run.firstReadyTick >= Ocellus.READY_TIMEOUT) return true
        val standing = standing(run)
        return standing.isNotEmpty() && standing.all { it.saveKey in run.ready && roomAt(run, it.x, it.z) == ArchiveRoom.OCELLUS }
    }

    /**
     * Ocellus's fight begins: the hooded figure becomes his first phase where it stood, scaled to everyone standing in
     * the run, and the party in his room is in it.
     */
    private fun beginOcellus(run: ArchiveRun) {
        val room = ArchiveRoom.OCELLUS
        val def = world.encounters.defs[Ocellus.NAME] ?: return
        val figure = run.ocellusFigure ?: return
        val boss = world.spawnNpc(NpcSpawn(def.boss, figure.x, figure.z, figure.level, source = "$run", respawns = false))
        // With no NPC slot free, the figure waits and the next tick tries again (as R18A-3 for Ezekiel).
        if (boss == null) {
            logger.warn { "$run: no room for Ocellus" }
            return
        }
        world.removeNpc(figure)
        run.ocellusFigure = null
        run.started += room
        val standing = standing(run)
        run.presentAtStart[room] = standing.filter { inRoom(run, room, it.x, it.z) }.map { it.saveKey }.toSet()
        world.partyScaling.scale(boss, standing.size.coerceIn(1, PartyScaling.MAX_PARTY))
        run.fights[room] = world.encounters.start(def, boss, { x, z -> inRoom(run, room, x, z) })
        logger.info { "$run began Ocellus's fight with ${standing.size} standing, ${run.ready.size} ready" }
    }

    /**
     * The first of the party onto Dreadnoth's arena (by its transporter, [Dreadnoth.ARENA_ENTRY_X]): his fight begins,
     * him where he waits ([Dreadnoth.X]), scaled to everyone standing in the run, [first] his target.
     */
    private fun beginDreadnoth(run: ArchiveRun, first: Player) {
        val room = ArchiveRoom.DREADNOTH
        val def = world.encounters.defs[Dreadnoth.NAME] ?: return
        val at = tile(run, Dreadnoth.X, Dreadnoth.Z)
        val boss = world.spawnNpc(NpcSpawn(def.boss, at.x, at.z, 0, source = "$run", respawns = false))
        // With no NPC slot free, the room isn't started (as R18A-3 for Ezekiel): the next tick with one of the party in
        // it tries again.
        if (boss == null) {
            logger.warn { "$run: no room for Dreadnoth" }
            return
        }
        run.started += room
        val standing = standing(run)
        run.presentAtStart[room] = standing.filter { inRoom(run, room, it.x, it.z) }.map { it.saveKey }.toSet()
        world.partyScaling.scale(boss, standing.size.coerceIn(1, PartyScaling.MAX_PARTY))
        world.meleeCombat.aggress(boss, first, world.tick)
        // Only those on his arena are in his fight: one still at the way in, short of the transporter, isn't hurt.
        run.fights[room] = world.encounters.start(def, boss, { x, z -> Dreadnoth.inArena(at.x, at.z, x, z) })
        logger.info { "$run began Dreadnoth's fight with ${standing.size} standing, ${first.username} first in" }
    }

    /** A nexus door: into its room, if its row is open to the party. */
    fun door(player: Player, door: Int) {
        val run = at(player.x, player.z) ?: return
        val room = ArchiveRoom.ofDoor(door) ?: return
        if (!mayEnter(run, player, room)) return
        run.chosen[room.stage] = room
        go(player, run, room)
    }

    private fun mayEnter(run: ArchiveRun, player: Player, room: ArchiveRoom): Boolean {
        if (run.wiped || player.saveKey !in run.entered || player.saveKey in run.downed) return false
        val before = ArchiveRoom.atStage(room.stage - 1)
        if (before.isNotEmpty() && before.none { it in run.cleared }) return false
        val chosen = run.chosen[room.stage]
        return chosen == null || chosen == room
    }

    private fun go(player: Player, run: ArchiveRun, room: ArchiveRoom) {
        val to = tile(run, room.entryX, room.entryZ)
        world.teleport(player, to.x, to.z, room.level)
    }

    /**
     * A boss room's Passageway: back out to the nexus. In Ocellus's room, once the run is done, the stand-in one
     * ([placeOcellusExit]) leaves to the lobby.
     */
    fun passageway(player: Player) {
        val run = at(player.x, player.z) ?: return
        val room = roomAt(run, player.x, player.z) ?: return
        if (room == ArchiveRoom.OCELLUS) {
            if (run.completed) world.teleport(player, run.instance.exitX, run.instance.exitZ, run.instance.exitLevel)
            return
        }
        if (room.door < 0) return
        out(player, run, room)
    }

    /**
     * STAND-IN until the reward room (raid step 28): a way out of Ocellus's room, which has none in the cache, once the
     * run is done. A room's Passageway (`tfa_exit`, [PASSAGEWAY]) is put at its south edge, in front of where the party
     * comes in, as the nexus's own sits at its north; its Exit goes to the lobby, in front of the Archive Door, the
     * instance's exit. Sam's decision of 8 November 2026 on exploit review RAID-3; where it stands is guessed.
     */
    private fun placeOcellusExit(run: ArchiveRun) {
        val at = tile(run, OCELLUS_EXIT_X, OCELLUS_EXIT_Z)
        world.locs.add(ZoneLoc(PASSAGEWAY, LocShape.CENTREPIECE_STRAIGHT, 0, at.x, at.z, ArchiveRoom.OCELLUS.level))
    }

    /**
     * A boss room's Archive transporter: back to the nexus, once the room is done. Before then Bork's, Ezekiel's and
     * Dreadnoth's and Xarth's put the player in the arena ([BORK_ARENA_X], [EZEKIEL_ARENA_X], [Dreadnoth.ARENA_ENTRY_X], [XARTH_ARENA_X]), the way into the fight: the arena is walled off from
     * the way in (tsps capture notes: "between the arrival and the arena").
     */
    fun transporter(player: Player) {
        val run = at(player.x, player.z) ?: return
        val room = roomAt(run, player.x, player.z)?.takeIf { it.door >= 0 } ?: return
        if (room == ArchiveRoom.BORK && room !in run.cleared) {
            val to = tile(run, BORK_ARENA_X, BORK_ARENA_Z)
            if (!inBorkArena(run, player)) world.teleport(player, to.x, to.z, 0)
            return
        }
        if (room == ArchiveRoom.EZEKIEL && room !in run.cleared) {
            val to = tile(run, EZEKIEL_ARENA_X, EZEKIEL_ARENA_Z)
            if (!inEzekielArena(run, player)) world.teleport(player, to.x, to.z, 0)
            return
        }
        if (room == ArchiveRoom.DREADNOTH && room !in run.cleared) {
            val to = tile(run, Dreadnoth.ARENA_ENTRY_X, Dreadnoth.ARENA_ENTRY_Z)
            if (!inDreadnothArena(run, player)) world.teleport(player, to.x, to.z, 0)
            return
        }
        if (room == ArchiveRoom.XARTH && room !in run.cleared) {
            val to = tile(run, XARTH_ARENA_X, XARTH_ARENA_Z)
            if (!inXarthArena(run, player)) world.teleport(player, to.x, to.z, 0)
            return
        }
        if (room !in run.cleared) return
        out(player, run, room)
    }

    /** Whether [player] stands on the floor of Xarth's arena in [run]. */
    private fun inXarthArena(run: ArchiveRun, player: Player): Boolean =
        onArena(run, ArchiveRoom.XARTH, player.x, player.z) == true

    /** Whether [player] stands in Ezekiel's arena in [run] (tsps capture notes: 4056-4068 x 5528-5540). */
    private fun inEzekielArena(run: ArchiveRun, player: Player): Boolean =
        onArena(run, ArchiveRoom.EZEKIEL, player.x, player.z) == true

    /** Whether [player] stands on the floor of Dreadnoth's arena in [run] (tsps capture notes: 4181-4198 x 5656-5670). */
    private fun inDreadnothArena(run: ArchiveRun, player: Player): Boolean =
        onArena(run, ArchiveRoom.DREADNOTH, player.x, player.z) == true

    /** Whether [player] stands on the floor of Bork's arena in [run]. */
    private fun inBorkArena(run: ArchiveRun, player: Player): Boolean =
        onArena(run, ArchiveRoom.BORK, player.x, player.z) == true

    private fun out(player: Player, run: ArchiveRun, room: ArchiveRoom) {
        val to = tile(run, room.doorFront.x, room.doorFront.z)
        world.teleport(player, to.x, to.z, NEXUS_PLANE)
    }

    /** Where [room]'s transporter puts a player, on its arena (the cache's tile); null for a room with no arena built. */
    private fun arenaEntry(room: ArchiveRoom): Tile? = when (room) {
        ArchiveRoom.EZEKIEL -> Tile(EZEKIEL_ARENA_X, EZEKIEL_ARENA_Z)
        ArchiveRoom.BORK -> Tile(BORK_ARENA_X, BORK_ARENA_Z)
        ArchiveRoom.DREADNOTH -> Tile(Dreadnoth.ARENA_ENTRY_X, Dreadnoth.ARENA_ENTRY_Z)
        ArchiveRoom.XARTH -> Tile(XARTH_ARENA_X, XARTH_ARENA_Z)
        else -> null
    }

    /** Whether tile [x], [z] is on [room]'s arena in [run]; null for a room with no arena built. */
    private fun onArena(run: ArchiveRun, room: ArchiveRoom, x: Int, z: Int): Boolean? = when (room) {
        ArchiveRoom.EZEKIEL -> tile(run, EZEKIEL_X, EZEKIEL_Z).let { Ezekiel.inArena(it.x, it.z, x, z) }
        ArchiveRoom.BORK -> tile(run, BORK_CART_X, BORK_CART_Z).let { Bork.inArena(it.x, it.z, x, z) }
        ArchiveRoom.DREADNOTH -> tile(run, Dreadnoth.X, Dreadnoth.Z).let { Dreadnoth.inArena(it.x, it.z, x, z) }
        ArchiveRoom.XARTH -> tile(run, XARTH_X, XARTH_Z).let { Xarth.inArena(it.x, it.z, x, z) }
        else -> null
    }

    /**
     * Whether [player] is shut out of attacking [npc]: it is in a room of a run with an arena, and they aren't on that
     * arena. The arena is walled off from the room's way in (tsps capture notes), so on live nobody fights from there;
     * here the walls are the cache's, and whether they stop a bow or a spell isn't known, so the rule is kept outright
     * (exploit review round 21, R21A-2: the way in was out of Dreadnoth's and Xarth's fights, and out of Ezekiel's and
     * Bork's reach, yet their bosses could be shot from it). Guessed: that live refuses it, and with what words.
     */
    fun shutOut(player: Player, npc: Npc): Boolean {
        val run = at(npc.x, npc.z) ?: return false
        val room = roomAt(run, npc.x, npc.z) ?: return false
        return onArena(run, room, player.x, player.z) == false
    }

    /**
     * The Alliance Navigator comes to the arena of each room [run] has won, [NAVIGATOR_DELAY] ticks after (tsps capture
     * notes: he "appears in the arena three ticks later"), where its transporter puts a player. He is the way back:
     * the arena is walled off from the room's way in, where its transporter and Passageway are (exploit review round
     * 21, R21A-1: whoever won a room was left on its arena). Guessed: the tile. His fade-in isn't sent. With no
     * Navigator in the cache, or no NPC slot for him, whoever is on the arena is sent out instead.
     */
    private fun navigators(run: ArchiveRun) {
        for ((room, due) in run.navigatorDue.entries.toList()) {
            if (world.tick < due) continue
            run.navigatorDue -= room
            val entry = arenaEntry(room) ?: continue
            val at = tile(run, entry.x, entry.z)
            val npc = world.npcTypes[NAVIGATOR]?.let { world.spawnNpc(NpcSpawn(it, at.x, at.z, 0, source = "$run", respawns = false)) }
            if (npc != null) continue
            logger.warn { "$run: no Alliance Navigator for ${room.boss}'s room, so its arena is emptied" }
            for (player in present(run)) {
                if (!player.dead && onArena(run, room, player.x, player.z) == true) out(player, run, room)
            }
        }
    }

    /**
     * Follow on the Alliance Navigator in a room that is won: back to the nexus, in front of the room's door (tsps
     * capture notes: "beside the door the party took"), after his one line. Quick-follow goes at once. Guessed: that his
     * line comes with Follow and not as he appears, that Quick-follow skips it, and that only the one who follows goes.
     * Live fades out and moves four ticks later; here the move is at once, as a door's is.
     */
    suspend fun follow(context: ScriptContext, npc: Npc, quick: Boolean) {
        val player = context.player
        val run = at(npc.x, npc.z) ?: return
        val room = roomAt(run, npc.x, npc.z) ?: return
        if (room !in run.cleared || player.saveKey !in run.entered || world.parties.of(player) !== run.party) return
        if (quick) return out(player, run, room)
        val line = if (room.stage >= LAST_DOORS) ArchiveNavigatorLines.VAULT else ArchiveNavigatorLines.ONWARD
        world.dialogues.start(context, npc) {
            chatNpc(ChatAnim.NEUTRAL, line, "name" to player.username)
            // Still in the room: a teleport or a death while he spoke took them elsewhere already.
            if (at(player.x, player.z) === run && roomAt(run, player.x, player.z) == room) out(player, run, room)
        }
    }

    /** The nexus's own Passageway, north: on to Ocellus, once the fourth room is done. */
    fun nexusPassageway(player: Player) {
        val run = at(player.x, player.z) ?: return
        if (!inNexus(run, player.x, player.z)) return
        if (!mayEnter(run, player, ArchiveRoom.OCELLUS)) return
        go(player, run, ArchiveRoom.OCELLUS)
    }

    /**
     * Each tick, after deaths: runs whose instance has ended go; members who left the party are sent out; a room's fight
     * begins as the first member stands in it; a member who is down and walks back into a room is sent out; and a party
     * with nobody left standing (the last standing one logged out) wipes.
     */
    fun process() {
        runs.values.removeIf { run ->
            val ended = world.instances.all.none { it === run.instance }
            // Everyone out at once (the last standing with a member down, both by Home Teleport on one tick): the
            // instance ended before this look, and whoever was down got out all the same.
            if (ended && !run.wiped) settleEscapes(run, ended = true)
            ended
        }
        for (run in runs.values.toList()) {
            if (run.wiped) continue
            // A fight whose boss has died: its room is done.
            for ((room, fight) in run.fights.entries.toList()) {
                if (!fight.ended) continue
                run.fights -= room
                if (fight.boss.dead) clear(run, room)
            }
            for (player in world.onlinePlayers) {
                if (!run.instance.contains(player.x, player.z) || player.saveKey !in run.entered || player.dead) continue
                if (world.parties.of(player) !== run.party) {
                    world.teleport(player, run.instance.exitX, run.instance.exitZ, run.instance.exitLevel)
                    continue
                }
                val room = roomAt(run, player.x, player.z) ?: continue
                if (player.saveKey in run.downed && room !in run.cleared) {
                    out(player, run, room)
                    continue
                }
                if (room !in run.started && room !in run.cleared && room.stage == run.stage) {
                    when {
                        !built(room) -> begin(run, room)
                        // The Guardian waits for a Touch; Ezekiel's, Bork and the Smorks', Dreadnoth's and Xarth's begin
                        // with the first into the arena (by its transporter), which is walled off from the way in.
                        room == ArchiveRoom.EZEKIEL -> if (inEzekielArena(run, player)) beginEzekiel(run, player)
                        room == ArchiveRoom.BORK -> if (inBorkArena(run, player)) beginBork(run, player)
                        room == ArchiveRoom.DREADNOTH -> if (inDreadnothArena(run, player)) beginDreadnoth(run, player)
                        room == ArchiveRoom.XARTH -> if (inXarthArena(run, player)) beginXarth(run, player)
                    }
                }
            }
            placeOcellusFigure(run)
            if (ArchiveRoom.OCELLUS !in run.started && ArchiveRoom.OCELLUS.stage == run.stage && run.ocellusFigure != null && ocellusReady(run)) beginOcellus(run)
            navigators(run)
            settleEscapes(run, ended = false)
            val present = present(run)
            if (present.isNotEmpty() && present.none { it.dead } && standing(run).isEmpty()) wipe(run, null)
        }
    }

    /**
     * A member down who got out of [run], by a teleport or by leaving the party, dies of it (RAID-2). Once its
     * instance has [ended], everyone is out of it. One who left the party counts at once, before [process] sends them out
     * of the instance: a room done in the same input would otherwise get them up first.
     */
    private fun settleEscapes(run: ArchiveRun, ended: Boolean) {
        for (key in run.downed.toList()) {
            val player = world.onlinePlayers.firstOrNull { it.saveKey == key } ?: continue
            val out = ended || !run.instance.contains(player.x, player.z) || world.parties.of(player) !== run.party
            if (!player.dead && out) escaped(run, player)
        }
    }

    /**
     * [player] is leaving the world (logged out, or their wait for a reconnect is over): if they are down in a run, that
     * counts as their death (RAID-2), before they are saved. Found by their being down, not by where they stand: a
     * Home Teleport landing out of the instance on the tick they log out is past [process]'s look for the run that
     * tick, and out of it by the next.
     */
    fun leaving(player: Player) {
        if (player.dead) return
        val run = runs.values.firstOrNull { !it.wiped && player.saveKey in it.downed } ?: return
        escaped(run, player)
    }

    /**
     * [player], down in [run], got out of it before it was done with them: they die as the last one standing would,
     * losing what a death loses into a grave in the lobby, and come back there. Without this a member down kept
     * everything and could teleport out or log out to keep it, so with an alt standing every death was free (exploit
     * review round 12, RAID-2, and its round 13 addendum). Sam's decision of 8 November 2026; the death's place is guessed.
     */
    private fun escaped(run: ArchiveRun, player: Player) {
        run.downed -= player.saveKey
        run.deaths[player.saveKey] = (run.deaths[player.saveKey] ?: 0) + 1
        logger.info { "$run: ${player.username} got out while down, so dies of it" }
        player.message(world.textFor(player)[CombatLines.DEAD])
        world.deaths.respawn(player, wipePlace(run))
    }

    /** [room]'s fight begins, with whoever of the party stands in it. Its boss isn't built yet, so it is done at once. */
    private fun begin(run: ArchiveRun, room: ArchiveRoom) {
        run.started += room
        run.presentAtStart[room] = standing(run).filter { inRoom(run, room, it.x, it.z) }.map { it.saveKey }.toSet()
        logger.info { "$run began ${room.boss}'s room with ${run.presentAtStart[room]}" }
        clear(run, room)
    }

    /**
     * `::raid clear`: [room] is done as if its boss died now. A room whose fight never began (the Guardian is only woken
     * by Touch) counts whoever of the party stands in it as there at its start, so its points go to them as a kill's
     * would, rather than to nobody.
     */
    fun clearNow(run: ArchiveRun, room: ArchiveRoom) {
        if (room !in run.presentAtStart) {
            run.presentAtStart[room] = standing(run).filter { inRoom(run, room, it.x, it.z) }.map { it.saveKey }.toSet()
        }
        clear(run, room)
    }

    /**
     * [room] is done: its points are shared out, whoever was down is up again, and the next row opens. After the third
     * and fourth rooms the nexus changes ([nexusLevel]).
     */
    fun clear(run: ArchiveRun, room: ArchiveRoom) {
        if (room in run.cleared) return
        // Done with its boss still up (::raid clear): the fight stops, and the boss goes.
        stopFight(run, room)
        if (room == ArchiveRoom.GUARDIAN) {
            run.guardian?.let { if (!it.dead && world.npcs[it.index] === it) world.removeNpc(it) }
            run.guardian = null
        }
        // Done before his fight began (::raid clear): the hooded figure goes too.
        if (room == ArchiveRoom.OCELLUS) {
            run.ocellusFigure?.let { if (world.npcs[it.index] === it) world.removeNpc(it) }
            run.ocellusFigure = null
        }
        run.started += room
        run.cleared += room
        run.chosen[room.stage] = room
        // Whoever got out while down (a Home Teleport landing last tick) dies of it before the room's end gets them up.
        settleEscapes(run, ended = false)
        share(run, room)
        if (room == ArchiveRoom.OCELLUS) placeOcellusExit(run)
        run.downed.clear()
        run.clearedTick = world.tick
        if (arenaEntry(room) != null && built(room)) run.navigatorDue[room] = world.tick + NAVIGATOR_DELAY
        val level = nexusLevel(run.stage)
        if (level != run.nexusLevel) {
            run.nexusLevel = level
            world.instances.replace(run.instance, nexusPart(level))
            world.redrawInstance(run.instance)
        }
        logger.info { "$run cleared ${room.boss}'s room; points ${run.points}" }
    }

    private fun share(run: ArchiveRun, room: ArchiveRoom) {
        val dealt = run.damage[room].orEmpty().filterValues { it > 0 }
        val total = dealt.values.sum()
        val shares: Map<String, Int> = if (total > 0) {
            dealt.mapValues { (_, damage) -> (ROOM_POINTS.toLong() * damage / total).toInt() }
        } else {
            val there = run.presentAtStart[room].orEmpty()
            if (there.isEmpty()) emptyMap() else there.associateWith { ROOM_POINTS / there.size }
        }
        for ((key, points) in shares) run.points[key] = (run.points[key] ?: 0) + points
    }

    /** A party member's hit on [npc] in a room of a run counts towards their share of its points. */
    fun damaged(npc: Npc, player: Player, dealt: Int) {
        val run = at(npc.x, npc.z) ?: return
        if (player.saveKey !in run.entered) return
        val room = roomAt(run, npc.x, npc.z) ?: return
        if (room !in run.started || room in run.cleared) return
        val byPlayer = run.damage.getOrPut(room, ::LinkedHashMap)
        byPlayer[player.saveKey] = (byPlayer[player.saveKey] ?: 0) + dealt
    }

    /**
     * Where [player], dying in a run, comes back: down in the nexus with everything kept while someone still stands, or
     * the lobby with a grave there when they were the last, which wipes the party. Null outside a run.
     */
    fun deathPlace(player: Player): DeathPlace? {
        val run = at(player.x, player.z) ?: return null
        if (player.saveKey !in run.entered) return null
        run.deaths[player.saveKey] = (run.deaths[player.saveKey] ?: 0) + 1
        if (run.wiped) return wipePlace(run)
        val others = standing(run).filter { it !== player }
        if (others.isEmpty()) {
            wipe(run, player)
            return wipePlace(run)
        }
        // A room done while they lay dying is a fight they're no longer out of: they get up standing.
        val fell = player.deathTick
        if (fell < 0 || run.clearedTick < fell) run.downed += player.saveKey
        val room = roomAt(run, player.x, player.z)
        // A death in the hall once the Guardian is done (a stray hit) has no fight to be out of.
        val front = room?.takeIf { it !in run.cleared }?.doorFront ?: Tile(NEXUS_X, NEXUS_Z)
        val to = tile(run, front.x, front.z)
        return DeathPlace(keepItems = true, to.x, to.z, NEXUS_PLANE, to.x, to.z, NEXUS_PLANE)
    }

    private fun wipePlace(run: ArchiveRun) = DeathPlace(
        keepItems = false, run.instance.exitX, run.instance.exitZ, run.instance.exitLevel, run.instance.exitX, run.instance.exitZ, run.instance.exitLevel,
    )

    /**
     * Nobody of [run]'s party stands: each member goes to the lobby, losing what a death loses into a grave there.
     * [dying] is the one whose death did it, whose own respawn carries on after; the others lying dead go as they get up.
     */
    private fun wipe(run: ArchiveRun, dying: Player?) {
        // Whoever got out while down (a Home Teleport landing last tick) dies of it first: the wipe only sees who is in.
        settleEscapes(run, ended = false)
        run.wiped = true
        for (room in run.fights.keys.toList()) stopFight(run, room)
        logger.info { "$run wiped${dying?.let { " as ${it.username} died" }.orEmpty()}" }
        for (member in present(run)) {
            if (member === dying || member.dead) continue
            world.deaths.respawn(member, wipePlace(run))
        }
    }

    /** Ends [room]'s fight in [run], if one is going on, and takes its boss out unless it lies dying. */
    private fun stopFight(run: ArchiveRun, room: ArchiveRoom) {
        val fight = run.fights.remove(room) ?: return
        if (!fight.ended) world.encounters.stop(fight)
        val boss = fight.boss
        if (!boss.dead && world.npcs[boss.index] === boss) world.removeNpc(boss)
    }

    /** What `::raid run` shows of [run]. */
    fun describe(run: ArchiveRun): String {
        val done = run.cleared.joinToString { it.boss }.ifEmpty { "none" }
        val next = when {
            run.wiped -> "wiped"
            run.completed -> "complete"
            else -> "next ${ArchiveRoom.atStage(run.stage).filter { run.chosen[run.stage] == null || run.chosen[run.stage] == it }.joinToString(" or ") { it.boss }}"
        }
        val points = run.points.entries.joinToString { "${name(it.key)} ${it.value}" }.ifEmpty { "none" }
        val downed = if (run.downed.isEmpty()) "" else "; down: ${run.downed.joinToString(transform = ::name)}"
        val deaths = if (run.deaths.isEmpty()) "" else "; deaths: ${run.deaths.entries.joinToString { "${name(it.key)} ${it.value}" }}"
        return "$run: done $done; $next; nexus level ${run.nexusLevel}; points $points$downed$deaths"
    }

    private fun name(saveKey: String) = world.onlinePlayers.firstOrNull { it.saveKey == saveKey }?.username ?: saveKey

    companion object {
        /** tfa_lobby_raid_entry, the lobby's Archive Door: Enter. */
        const val ARCHIVE_DOOR = 62768
        const val ENTER = 1
        const val QUICK_ENTER = 2

        /** tfa_nexus_section1_door_west and on: the nexus's three rows of doors, from the south. */
        const val DOOR_1_WEST = 63088
        const val DOOR_1_EAST = 63091
        const val DOOR_2_WEST = 63096
        const val DOOR_2_EAST = 63099
        const val DOOR_3_WEST = 63102
        const val DOOR_3_EAST = 63105
        val DOORS = listOf(DOOR_1_WEST, DOOR_1_EAST, DOOR_2_WEST, DOOR_2_EAST, DOOR_3_WEST, DOOR_3_EAST)

        /** tfa_exit, a boss room's Passageway, and tfa_exit_large, the nexus's own north: Exit. */
        const val PASSAGEWAY = 62765
        const val NEXUS_PASSAGEWAY = 62766
        const val EXIT = 1

        /** tfa_boss_teleporter, tfa_teleporter_moia and tfa_teleporter_jelly, the Archive transporters: Use, Quick-use. */
        const val TRANSPORTER = 63087
        const val TRANSPORTER_MOIA = 62610
        const val TRANSPORTER_JELLY = 63134
        val TRANSPORTERS = listOf(TRANSPORTER, TRANSPORTER_MOIA, TRANSPORTER_JELLY)
        const val USE = 1
        const val QUICK_USE = 2

        /**
         * The Alliance Navigator, who comes to a won room's arena [NAVIGATOR_DELAY] ticks after and leads back to the
         * nexus: Follow, Quick-follow (tsps capture notes).
         */
        const val NAVIGATOR = 16700
        const val FOLLOW = "Follow"
        const val QUICK_FOLLOW = "Quick-follow"
        const val NAVIGATOR_DELAY = 3

        const val STAGES = 5

        /** The stage of the last row of doors, the fourth room: Ocellus's is next. */
        const val LAST_DOORS = 4

        /** The locs in a run's instance whose options are handled here, on their copies. */
        val INSTANCE_LOCS: Set<Int> get() = (DOORS + TRANSPORTERS + PASSAGEWAY + NEXUS_PASSAGEWAY).toSet()

        /** The z of the nexus's row of doors for [stage] 2 to 4 (each door covers it and the 4 tiles north). */
        fun doorZ(stage: Int): Int = 5584 + (stage - 2) * 13

        /** The nexus tile the Archive Door lands on, just inside its south end. Guessed. */
        const val NEXUS_X = 4127
        const val NEXUS_Z = 5573

        /**
         * The stand-in exit's south-west tile in Ocellus's room, on its level: its 5 by 2 just south of where the party
         * comes in. Guessed.
         */
        const val OCELLUS_EXIT_X = 4126
        const val OCELLUS_EXIT_Z = 5635

        /**
         * The level of a run's instance the nexus is on, where a player leaving a room or down in one goes. The cache's
         * nexus level is copied onto it ([nexusPart]).
         */
        const val NEXUS_PLANE = 0

        /** Ezekiel's south-west tile as his fight begins (tsps capture notes). */
        const val EZEKIEL_X = 4062
        const val EZEKIEL_Z = 5537

        /** Where Ezekiel's room's transporter puts the player: the east edge of his arena (tsps capture notes). */
        const val EZEKIEL_ARENA_X = 4067
        const val EZEKIEL_ARENA_Z = 5535


        /**
         * The Smorks' cart's south-west tile as Bork and the Smorks' fight begins, its 3 by 3 round the one blocked tile
         * in the middle of their room's floor (4191,5599). Guessed.
         */
        const val BORK_CART_X = 4190
        const val BORK_CART_Z = 5598

        /** Where Bork's room's transporter puts the player: the west edge of his arena's floor (tsps capture notes). */
        const val BORK_ARENA_X = 4184
        const val BORK_ARENA_Z = 5599

        /** Xarth's south-west tile, where he stands the whole fight (tsps capture notes). */
        const val XARTH_X = 4190
        const val XARTH_Z = 5534

        /** Where Xarth's room's transporter puts the player: the west edge of his arena's floor (tsps capture notes). */
        const val XARTH_ARENA_X = 4184
        const val XARTH_ARENA_Z = 5535

        /** In front of the nexus's own Passageway (4124,5625, 7 by 2). Guessed. */
        const val PASSAGE_FRONT_X = 4127
        const val PASSAGE_FRONT_Z = 5623

        /** The nexus's map square in the cache, and the z its hall starts at: its first row of doors' south edge. */
        const val NEXUS_SQUARE_X = 64
        const val NEXUS_SQUARE_Z = 87
        const val HALL_Z = 5583

        /**
         * Where the Guardian waits and is fought, its south-west tile: where its corpse (`tfa_corpse_guardian01`, 5 by
         * 5) lies on the nexus's later levels, the middle of the hall. Inferred from the corpse.
         */
        const val GUARDIAN_X = 4125
        const val GUARDIAN_Z = 5597

        /** In front of the Archive Door, in the lobby: where a wipe lands and its graves go, and the instance's exit. Guessed. */
        const val LOBBY_X = 4127
        const val LOBBY_Z = 5560

        /**
         * The cache's levels of the nexus: at the start; with the Guardian's corpse and the camp of crates and
         * barricades; and with the Passageway north as well. Level 1, with the Passageway but no corpse, isn't used.
         */
        const val NEXUS_START = 0
        const val NEXUS_CAMP = 2
        const val NEXUS_OCELLUS = 3

        /** The level of the nexus for a run at [stage]: the corpse and camp once the Guardian is done, the way north after the fourth room. */
        fun nexusLevel(stage: Int): Int = when {
            stage > 4 -> NEXUS_OCELLUS
            stage > 1 -> NEXUS_CAMP
            else -> NEXUS_START
        }

        /** Each room's points, shared by damage. Guessed: the Wiki gives no number. */
        const val ROOM_POINTS = 1000

        /**
         * The instance: the cache's map squares 63 to 65 by 86 to 88, level 0, laid out as they are, but for the
         * lobby's square (64,86), left out, and the nexus (64,87), copied from the level [nexusLevel] gives; and
         * Ocellus's arena, his square's level 1, on level 1 ([ocellusArena]).
         */
        const val FROM_X = 63 shl 6
        const val FROM_Z = 86 shl 6
        const val WIDTH_ZONES = 24
        const val HEIGHT_ZONES = 24

        fun nexusPart(level: Int) = InstancePart(NEXUS_SQUARE_X shl 6, NEXUS_SQUARE_Z shl 6, level, 8, 8, 8, 8, 0)

        fun parts(nexusLevel: Int): List<InstancePart> =
            ArchiveRoom.entries.filter { it != ArchiveRoom.GUARDIAN }.map { room ->
                InstancePart(room.squareX shl 6, room.squareZ shl 6, 0, 8, 8, (room.squareX - 63) * 8, (room.squareZ - 86) * 8, 0)
            } + ocellusArena() + nexusPart(nexusLevel)

        /** Ocellus's arena: his square's level 1, copied onto the instance's level 1 as live shows it ([ArchiveRoom.level]). */
        private fun ocellusArena(): InstancePart = ArchiveRoom.OCELLUS.let { room ->
            InstancePart(room.squareX shl 6, room.squareZ shl 6, room.level, 8, 8, (room.squareX - 63) * 8, (room.squareZ - 86) * 8, room.level)
        }
    }
}
