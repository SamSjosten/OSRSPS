package osrsps.game.npc

import net.rsprot.protocol.game.outgoing.info.npcinfo.NpcAvatar
import osrsps.game.NpcType
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
    /** Tiles walked to in turn, over and over, instead of wandering. Empty for a wandering or still NPC. */
    val patrol: List<Tile> = emptyList(),
    val source: String,
) {
    init {
        require(level in 0..3) { "Bad level $level" }
        require(wander >= 0) { "Bad wander range $wander" }
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

    /** What the client is told it does through NPC info; null until the world gives it one (and in tests). */
    var avatar: NpcAvatar? = null

    /** Whether it covers [tileX], [tileZ] on its level. */
    fun covers(tileX: Int, tileZ: Int): Boolean = tileX in x until x + type.size && tileZ in z until z + type.size

    override fun toString(): String = "${type.name} (${type.id}) #$index at $x, $z, $level"
}
