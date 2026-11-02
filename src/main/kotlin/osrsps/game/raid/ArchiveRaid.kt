package osrsps.game.raid

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.combat.DeathPlace
import osrsps.game.instance.Instance
import osrsps.game.instance.InstancePart
import osrsps.game.npc.Npc
import osrsps.map.Tile

private val logger = KotlinLogging.logger {}

/** Which of a row's two doors a room is behind, as the nexus faces north: west is the Wiki's left. */
enum class DoorSide { WEST, EAST }

/**
 * One of The Fractured Archive's rooms: the boss in it, the [stage] of the run it is fought at (1 the Guardian, 2 to 4
 * the three rows of doors, 5 Ocellus), and its map square ([squareX], [squareZ]) in the 241.4 cache, level 0. Tiles
 * are the cache's; [ArchiveRaids] maps them into a run's instance.
 *
 * From the cache: each boss room's map square, told by its locs' names (`tfa_corpse_ezekiel01`, `tfa_moia_bones`,
 * `tfa_xarth01_corpse`, `tfa_dagonhai_book01`, `tfa_soulprison`, Bork's forge), its Passageway (`tfa_exit`, 62765, Exit)
 * and its Archive transporter (Use, Quick-use); the nexus's six doors (`tfa_nexus_section1_door_west` and on, Enter,
 * Quick-enter); the Guardian's corpse (`tfa_corpse_guardian01`, 5 by 5) in the middle of the nexus on the nexus's two
 * later levels; and the arena north of the nexus, past its Passageway, with The Blade on its level above. From the
 * Wiki: the order, the Guardian first and Ocellus last, and the pairs. Inferred: that each west door leads to the west
 * room of its pair (all three west rooms are in map column 63, the east ones in 65), which room is whose by its locs,
 * that the Guardian is fought in the nexus's hall, where its corpse lies, and Ocellus in the arena north of it.
 */
enum class ArchiveRoom(
    val boss: String,
    val stage: Int,
    val side: DoorSide?,
    val squareX: Int,
    val squareZ: Int,
    /** The nexus door into it; -1 for none (the Guardian's is the nexus's hall, Ocellus's is past the nexus's Passageway). */
    val door: Int,
    /** Where a player going in lands, and where `::raid tp` takes them. */
    val entryX: Int,
    val entryZ: Int,
    /** Its Archive transporter; -1 for none. */
    val transporter: Int,
) {
    GUARDIAN("Archive Guardian", 1, null, 64, 87, -1, 4127, 5590, -1),
    EZEKIEL("Ezekiel", 2, DoorSide.WEST, 63, 86, ArchiveRaids.DOOR_1_WEST, 4083, 5535, ArchiveRaids.TRANSPORTER),
    BORK("Bork and the Smorks", 2, DoorSide.EAST, 65, 87, ArchiveRaids.DOOR_1_EAST, 4167, 5599, ArchiveRaids.TRANSPORTER),
    MOIA("Moia", 3, DoorSide.WEST, 63, 88, ArchiveRaids.DOOR_2_WEST, 4084, 5663, ArchiveRaids.TRANSPORTER_MOIA),
    DREADNOTH("Dreadnoth the Jellied", 3, DoorSide.EAST, 65, 88, ArchiveRaids.DOOR_2_EAST, 4172, 5663, ArchiveRaids.TRANSPORTER_JELLY),
    DAGONHAI("Dagon'hai Elder", 4, DoorSide.WEST, 63, 87, ArchiveRaids.DOOR_3_WEST, 4086, 5599, ArchiveRaids.TRANSPORTER),
    XARTH("Xarth", 4, DoorSide.EAST, 65, 86, ArchiveRaids.DOOR_3_EAST, 4172, 5535, ArchiveRaids.TRANSPORTER),
    OCELLUS("Ocellus", 5, null, 64, 88, -1, 4121, 5634, -1),
    ;

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
 *   done. Ocellus's room is past the nexus's own Passageway (`tfa_exit_large`), open once the fourth room is done.
 * - The nexus is the cache's map square 64,87 on one of its four levels: 0 at the start, 2 (the Guardian's corpse and a
 *   camp of crates and barricades) once the Guardian is done, 3 (the same and the Passageway north) once the fourth
 *   room is done. The Guardian is fought in the nexus's hall, from its first row of doors north; the Archive Door lands
 *   in the entrance south of it, where the party gathers.
 * - A room's fight begins as the first party member stands in it. A room whose boss isn't built yet (all of them, so
 *   far) is done as soon as it begins: that is what lets a run go through empty rooms.
 * - A room's points are [ROOM_POINTS], shared by damage among those who did any, or evenly among who was there when it
 *   began if nobody did. The number is made up: the Wiki has none.
 * - A party member who dies with someone still standing keeps everything and is down until the room is done: in the
 *   nexus, in front of the room's door (in the entrance, for the Guardian's hall), and sent out again if they walk back
 *   in. Spectating isn't built.
 * - The wipe: once nobody in the raid is left standing, each member goes to the lobby, in front of the Archive Door,
 *   and what they lose goes in a grave there.
 * - A member who leaves the party is sent to the lobby. Logging out saves them there, as any instance does at its exit.
 */
class ArchiveRaids(private val world: World) {
    private val runs = LinkedHashMap<Party, ArchiveRun>()
    private var nextId = 1

    /** Every run, oldest first. */
    val all: Collection<ArchiveRun> get() = runs.values

    fun of(party: Party): ArchiveRun? = runs[party]

    /** The run whose instance holds tile [x], [z]. */
    fun at(x: Int, z: Int): ArchiveRun? {
        val instance = world.instances.at(x, z) ?: return null
        return runs.values.firstOrNull { it.instance === instance }
    }

    fun register() {
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
        logger.info { "$run opened by ${player.username} in $instance" }
        return run
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
        world.teleport(player, to.x, to.z, 0)
    }

    /** A boss room's Passageway: back out to the nexus. */
    fun passageway(player: Player) {
        val run = at(player.x, player.z) ?: return
        val room = roomAt(run, player.x, player.z)?.takeIf { it.door >= 0 } ?: return
        out(player, run, room)
    }

    /** A boss room's Archive transporter: back to the nexus, once the room is done. */
    fun transporter(player: Player) {
        val run = at(player.x, player.z) ?: return
        val room = roomAt(run, player.x, player.z)?.takeIf { it.door >= 0 } ?: return
        if (room !in run.cleared) return
        out(player, run, room)
    }

    private fun out(player: Player, run: ArchiveRun, room: ArchiveRoom) {
        val to = tile(run, room.doorFront.x, room.doorFront.z)
        world.teleport(player, to.x, to.z, 0)
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
        runs.values.removeIf { run -> world.instances.all.none { it === run.instance } }
        for (run in runs.values.toList()) {
            if (run.wiped) continue
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
                if (room !in run.started && room !in run.cleared && room.stage == run.stage) begin(run, room)
            }
            val present = present(run)
            if (present.isNotEmpty() && present.none { it.dead } && standing(run).isEmpty()) wipe(run, null)
        }
    }

    /** [room]'s fight begins, with whoever of the party stands in it. No boss is built yet, so it is done at once. */
    private fun begin(run: ArchiveRun, room: ArchiveRoom) {
        run.started += room
        run.presentAtStart[room] = standing(run).filter { inRoom(run, room, it.x, it.z) }.map { it.saveKey }.toSet()
        logger.info { "$run began ${room.boss}'s room with ${run.presentAtStart[room]}" }
        clear(run, room)
    }

    /**
     * [room] is done: its points are shared out, whoever was down is up again, and the next row opens. After the third
     * and fourth rooms the nexus changes ([nexusLevel]).
     */
    fun clear(run: ArchiveRun, room: ArchiveRoom) {
        if (room in run.cleared) return
        run.started += room
        run.cleared += room
        run.chosen[room.stage] = room
        share(run, room)
        run.downed.clear()
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
        if (run.wiped) return wipePlace(run)
        run.deaths[player.saveKey] = (run.deaths[player.saveKey] ?: 0) + 1
        val others = standing(run).filter { it !== player }
        if (others.isEmpty()) {
            wipe(run, player)
            return wipePlace(run)
        }
        run.downed += player.saveKey
        val room = roomAt(run, player.x, player.z)
        // A death in the hall once the Guardian is done (a stray hit) has no fight to be out of.
        val front = room?.takeIf { it !in run.cleared }?.doorFront ?: Tile(NEXUS_X, NEXUS_Z)
        val to = tile(run, front.x, front.z)
        return DeathPlace(keepItems = true, to.x, to.z, 0, to.x, to.z, 0)
    }

    private fun wipePlace(run: ArchiveRun) = DeathPlace(
        keepItems = false, run.instance.exitX, run.instance.exitZ, run.instance.exitLevel, run.instance.exitX, run.instance.exitZ, run.instance.exitLevel,
    )

    /**
     * Nobody of [run]'s party stands: each member goes to the lobby, losing what a death loses into a grave there.
     * [dying] is the one whose death did it, whose own respawn carries on after; the others lying dead go as they get up.
     */
    private fun wipe(run: ArchiveRun, dying: Player?) {
        run.wiped = true
        logger.info { "$run wiped${dying?.let { " as ${it.username} died" }.orEmpty()}" }
        for (member in present(run)) {
            if (member === dying || member.dead) continue
            world.deaths.respawn(member, wipePlace(run))
        }
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

        const val STAGES = 5

        /** The locs in a run's instance whose options are handled here, on their copies. */
        val INSTANCE_LOCS: Set<Int> get() = (DOORS + TRANSPORTERS + PASSAGEWAY + NEXUS_PASSAGEWAY).toSet()

        /** The z of the nexus's row of doors for [stage] 2 to 4 (each door covers it and the 4 tiles north). */
        fun doorZ(stage: Int): Int = 5584 + (stage - 2) * 13

        /** The nexus tile the Archive Door lands on, just inside its south end. Guessed. */
        const val NEXUS_X = 4127
        const val NEXUS_Z = 5573

        /** In front of the nexus's own Passageway (4124,5625, 7 by 2). Guessed. */
        const val PASSAGE_FRONT_X = 4127
        const val PASSAGE_FRONT_Z = 5623

        /** The nexus's map square in the cache, and the z its hall starts at: its first row of doors' south edge. */
        const val NEXUS_SQUARE_X = 64
        const val NEXUS_SQUARE_Z = 87
        const val HALL_Z = 5583

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
         * lobby's square (64,86), left out, and the nexus (64,87), copied from the level [nexusLevel] gives. Nothing
         * else is above level 0.
         */
        const val FROM_X = 63 shl 6
        const val FROM_Z = 86 shl 6
        const val WIDTH_ZONES = 24
        const val HEIGHT_ZONES = 24

        fun nexusPart(level: Int) = InstancePart(NEXUS_SQUARE_X shl 6, NEXUS_SQUARE_Z shl 6, level, 8, 8, 8, 8, 0)

        fun parts(nexusLevel: Int): List<InstancePart> =
            ArchiveRoom.entries.filter { it != ArchiveRoom.GUARDIAN }.map { room ->
                InstancePart(room.squareX shl 6, room.squareZ shl 6, 0, 8, 8, (room.squareX - 63) * 8, (room.squareZ - 86) * 8, 0)
            } + nexusPart(nexusLevel)
    }
}
