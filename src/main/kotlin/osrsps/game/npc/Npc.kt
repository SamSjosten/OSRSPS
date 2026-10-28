package osrsps.game.npc

import net.rsprot.protocol.game.outgoing.info.npcinfo.NpcAvatar
import osrsps.game.Facing
import osrsps.game.NpcType
import osrsps.game.Player
import osrsps.map.Tile

/** The eight directions an NPC can face when it spawns, with the ids RSProt's NPC info sends them as. */
enum class Direction(val id: Int) {
    NORTH_WEST(0),
    NORTH(1),
    NORTH_EAST(2),
    WEST(3),
    EAST(4),
    SOUTH_WEST(5),
    SOUTH(6),
    SOUTH_EAST(7),
    ;

    companion object {
        private val SHORT = mapOf("NW" to NORTH_WEST, "N" to NORTH, "NE" to NORTH_EAST, "W" to WEST, "E" to EAST, "SW" to SOUTH_WEST, "S" to SOUTH, "SE" to SOUTH_EAST)

        /** `S`, `south`, `south_west` or `SW`, ignoring case; null for anything else. */
        fun parse(text: String): Direction? {
            val upper = text.uppercase()
            return SHORT[upper] ?: entries.firstOrNull { it.name == upper }
        }
    }
}

/**
 * Where an NPC appears and how it moves. [source] says where the type and tile came from (a wiki page, a recording, a
 * `::npc` command), so a spawn can be checked and replaced later.
 */
class NpcSpawn(
    val type: NpcType,
    val x: Int,
    val z: Int,
    val level: Int = 0,
    /** Which way it faces when it appears. */
    val direction: Direction = Direction.SOUTH,
    /**
     * How far from the spawn tile it may pick tiles to wander to, in tiles along each axis; 0 stands still. This is
     * the wiki's "wander radius".
     */
    val wander: Int = 0,
    /** Stops walked to in turn, over and over, instead of wandering. Empty for a wandering or still NPC. */
    val patrol: List<PatrolStop> = emptyList(),
    /** Whether it keeps under a roof or out from under one wherever it walks. */
    val restrict: MoveRestrict = MoveRestrict.NORMAL,
    val source: String,
) {
    init {
        require(level in 0..3) { "Bad level $level" }
        require(wander >= 0) { "Bad wander range $wander" }
    }
}

/**
 * RS2's NPC move restrictions (the 2004Scape project's `moverestrict`) for the two kinds that read the map: an
 * [INDOORS] NPC only steps onto roofed tiles and an [OUTDOORS] one only onto open sky. The Lumbridge cook keeps to the
 * castle this way.
 */
enum class MoveRestrict {
    NORMAL,
    INDOORS,
    OUTDOORS,
}

/** A tile on an NPC's patrol, and how many ticks it stands there on arriving before it walks to the next. */
data class PatrolStop(val x: Int, val z: Int, val wait: Int = 0) {
    init {
        require(wait >= 0) { "Bad patrol wait $wait" }
    }
}

/** An NPC in the world. Only touched on the game thread. */
class Npc(
    /** Its slot in the world, which the client knows it by. */
    val index: Int,
    val spawn: NpcSpawn,
) {
    val type: NpcType get() = spawn.type

    /** The south-west tile it covers; it covers [NpcType.size] tiles along each side from here. */
    var x: Int = spawn.x
        internal set
    var z: Int = spawn.z
        internal set
    var level: Int = spawn.level
        internal set

    /** The tile it is walking to, or null when it isn't going anywhere. */
    var destination: Tile? = null

    /** The next entry of [NpcSpawn.patrol] to walk to. */
    internal var patrolIndex: Int = 0

    /** Ticks left to stand at the patrol stop it last walked to. */
    internal var patrolWait: Int = 0

    /** What the client is told it does through NPC info; null until the world gives it one (and in tests). */
    var avatar: NpcAvatar? = null

    /**
     * The player this NPC is busy with: one who talked to it or used an item on it. It faces them and stands still
     * until [osrsps.game.npc.NpcInteractions] lets it go.
     */
    var engagedWith: Player? = null
        internal set

    /** The tick it became [engagedWith] that player. */
    internal var engagedTick: Long = 0

    /** Players who used an option on it while it was busy with someone else, oldest first; see [Npcs.handOver]. */
    internal val waiting = ArrayList<Player>()

    /** The player it follows, stopping beside them; see [Npcs.follow]. */
    var following: Player? = null
        internal set

    /** The player it is told to face, null when it faces where it last turned. */
    var facing: Player? = null
        private set

    /** Current hitpoints; [osrsps.game.NpcStats.hitpoints] at its spawn. */
    var hitpoints: Int = spawn.type.stats.hitpoints

    /**
     * Damage on its way, melee swings and ranged shots rolled and not landed yet, so two attacks can't both pay XP for
     * the same hitpoints; see [osrsps.game.combat.MeleeCombat].
     */
    var incoming: Int = 0
        internal set

    /** True from the hit that killed it until it is taken out of the world; it can't be attacked meanwhile. */
    var dead: Boolean = false
        internal set

    /** The player who hit it last, and the tick they did; see [osrsps.game.combat.MeleeCombat]. */
    var attacker: Player? = null
        internal set
    var attackedTick: Long = -1
        internal set

    /** Ticks it still stands where it is because it is being fought; see [Npcs.process]. */
    internal var holdTicks: Int = 0

    /** The player it fights back against, chasing and hitting them; null for none. See [osrsps.game.combat.MeleeCombat]. */
    var target: Player? = null
        internal set

    /** The first tick it may swing at its target again. */
    internal var nextAttackTick: Long = 0

    /** The animation, hitsplats and health bars given this cycle; see [NpcEffects]. */
    val effects = NpcEffects()

    /** Whether it covers [tileX], [tileZ] on its level. */
    fun covers(tileX: Int, tileZ: Int): Boolean = tileX in x until x + type.size && tileZ in z until z + type.size

    /**
     * Turns to [player] and keeps turning to them as they move, until [resetFace]. walkMode 0 is the old face-entity
     * behaviour as RSProt maps it: the NPC looks where it walks and turns back once it stops. Inferred from RSProt, not
     * checked against live.
     */
    fun face(player: Player) {
        facing = player
        val angle = Facing.angle(x, z, type.size, player.x, player.z, 1) ?: 0
        avatar?.extendedInfo?.setFacePlayer(player.index, false, 0, angle)
    }

    /** Stops turning to the player it faces. Inferred: the client leaves it looking the way it last turned. */
    fun resetFace() {
        if (facing == null) return
        facing = null
        avatar?.extendedInfo?.resetFacing()
    }

    override fun toString(): String = "${type.name} (${type.id}) #$index at $x, $z, $level"
}
