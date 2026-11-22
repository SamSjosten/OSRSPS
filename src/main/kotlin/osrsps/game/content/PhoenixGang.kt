package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.GroundObjs
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc
import osrsps.game.zone.ZoneObj
import osrsps.map.Tile
import kotlin.math.abs

/** Straven's lines to a Phoenix Gang member after Shield of Arrav (the Wiki's Transcript:Shield of Arrav, Post-quest). */
object StravenLines : Lines("straven", "Transcript:Shield of Arrav") {
    val GREETING = line("greeting", "Straven's greeting to a fellow gang member")
    val TREASURES = line("treasures", "option and player line: the treasures in this place")
    val TREASURES_1 = line("treasures_1", "Straven: the candlesticks, stolen from the palace")
    val TREASURES_2 = line("treasures_2", "player: and the Shield of Arrav?")
    val TREASURES_3 = line("treasures_3", "Straven, after the quest: once one of their most prized possessions")
    val TREASURES_4 = line("treasures_4", "player: once?")
    val TREASURES_5 = line("treasures_5", "Straven, after the quest: the Black Arm Gang stole the other half too")
    val TREASURES_6 = line("treasures_6", "player: they'll keep an eye out for Black Arm members")
    val THIEVING = line("thieving", "option and player line: where to go thieving")
    val THIEVING_1 = line("thieving_1", "Straven: the Ardougne marketplace")
    val GO = line("go", "option and player line: they had better get going")
    val LOST_KEY = line("lost_key", "player, without a weapon store key: they lost the key")
    val LOST_KEY_1 = line("lost_key_1", "Straven: be more careful, here's a spare")
    val HANDS_KEY = line("hands_key", "the box as Straven hands over the key")
}

/** Katrine's line to a Phoenix Gang member. */
object KatrineLines : Lines("katrine", "Transcript:Shield of Arrav") {
    val PHOENIX_SCUM = line("phoenix_scum", "Katrine to a Phoenix Gang member: get lost")
}

/** The Weaponsmaster's talk to a Phoenix Gang member. */
object WeaponsmasterLines : Lines("weaponsmaster", "Transcript:Shield of Arrav") {
    val HELLO = line("hello", "player: hello")
    val GREETING = line("greeting", "the Weaponsmaster: hello fellow Phoenix")
    val WEAPONS = line("weapons", "option and player line: after a weapon or two")
    val WEAPONS_1 = line("weapons_1", "the Weaponsmaster: feel free to look around")
    val TREASURE = line("treasure", "option and player line: looking for treasure")
    val TREASURE_1 = line("treasure_1", "the Weaponsmaster: none up here, go mug someone")
    val STRAVENS = line("stravens", "a Phoenix crossbow taken from the store: that's Straven's")
}

/** The two gangs' houses' messages. */
object GangHouseLines : Lines("gang_house", "Transcript:Shield of Arrav") {
    val SECURELY_LOCKED = line("securely_locked", "the weapon store door without its key, and the Black Arm Gang's inner door to a Phoenix member")
    val CHEST_SEARCH = line("chest_search", "the Phoenix Gang's chest opened: the player searches it")
    val CHEST_NOTHING = line("chest_nothing", "the Phoenix Gang's chest after the quest: nothing of interest inside")
}

/**
 * Shield of Arrav as a Phoenix Gang member finishes it (post-quest content step 2; Sam chose the Phoenix Gang,
 * 2026-11-19). From the OSRS Wiki's Transcript:Shield of Arrav (its Phoenix Gang and Post-quest parts), Straven,
 * Katrine, Weaponsmaster, Phoenix Gang Hideout, Phoenix crossbow and Grapes pages (read 2026-11-19); tiles from the 242
 * cache's map and mejrs's live NPC list.
 *
 * - **The hideout** under the house in the southern alley: the street's deep ladder (11803, 3244,3383) leads down to the
 *   hideout's ladder (`phoenixladder` 2405, 3244,9783), which climbs back up. Straven (5212) stands by the hideout's
 *   door (`phoenixdoor` 2397, 3247,9779, on the north edge of its tile), which lets a member through either way. The
 *   chest beyond (`phoenixshutchest` 2403) held the shield's left half; after the quest it is in the museum, so the chest
 *   is found empty (the transcript's line for a player who already has the half; inferred for after the quest).
 *   Grapes (1987) lie by it (3237,9761), back about every 43 seconds (72 ticks).
 * - **Straven** greets a fellow member; a member with no weapon store key (759) held, worn or banked is handed a spare
 *   first, as the transcript has it. His menu after the quest: the treasures (now that the Black Arm Gang has the whole
 *   shield), where to go thieving, and leaving.
 * - **The weapon store**: the door east of the house (`phoenixdoor2` 2398, 3251,3386, on the south edge of its tile)
 *   opens for a player holding the key and is securely locked without; from inside it lets anyone out (guessed). The
 *   Weaponsmaster (14137, shown by multi-NPC 5211) upstairs greets a member; the two Phoenix crossbows (767) on the
 *   store's floor (3243,3383 and 3245,3385, level 1, back after 100 ticks) are Straven's, and he stops a member taking
 *   one, as the transcript has it. A crossbow anywhere else, or with no Weaponsmaster there to see, takes as any item does
 *   (guessed).
 * - **The Black Arm Gang's house** west of the south gate: Katrine (5210) in its front room sends a Phoenix member away,
 *   and its inner door (`blackarmdoor` 2399, 3185,3388, on the south edge of its tile) is securely locked to them; from
 *   the far side it lets the player out (guessed). Behind it, the cupboard upstairs is the Black Arm Gang's side (left
 *   out).
 * - The VTAM Corporation's plaque (23636) by the door is [MisthalinOptionPack]'s.
 *
 * Guessed: every chathead expression; each door's two open ticks and the posh door's swing (as the Draynor jail's); the
 * chest's open look for 3 ticks with the human_openchest animation; where Straven and Katrine stand (mejrs's tiles,
 * no wander). Left out: the thieves' armband Straven hands back before the key (Heroes' Quest), the Black Arm Gang's
 * side (Sam's choice), the antique lamp from King Roald.
 */
object PhoenixGang {
    const val STRAVEN = 5212
    const val KATRINE = 5210
    /** weaponsmaster_vis, the type multi-NPC 5211 shows after the quest. */
    const val WEAPONSMASTER = 14137
    /** The Weaponsmaster's spawn type, multi-NPC 5211. */
    const val WEAPONSMASTER_SPAWN = 5211
    const val TALK_TO = 1

    const val HIDEOUT_LADDER = 2405
    const val HIDEOUT_DOOR = 2397
    const val STORE_DOOR = 2398
    const val BLACK_ARM_DOOR = 2399
    const val CHEST = 2403
    const val CHEST_OPEN = 2404
    /** poshdooropen, the open look the gangs' doors swing to, as the Draynor jail's door does. Guessed. */
    const val DOOR_OPEN = 1541
    const val OP1 = 1

    const val WEAPON_STORE_KEY = 759
    const val PHOENIX_CROSSBOW = 767

    const val DOOR_TICKS = 2
    const val CHEST_TICKS = 3

    /** The weapon store's floor upstairs, where the crossbows lie: the house's level 1 from the map. */
    private val STORE_X = 3242..3253
    private val STORE_Z = 3382..3387

    /** How far the Weaponsmaster sees a crossbow taken from. Guessed: the store's width. */
    private const val WATCH_RANGE = 10

    fun hasKey(player: Player): Boolean =
        player.inventory.count(WEAPON_STORE_KEY) > 0 || player.equipment.count(WEAPON_STORE_KEY) > 0 || player.bank.count(WEAPON_STORE_KEY) > 0

    /** Whether [obj] is one of the store's crossbows: a spawn on the store's floor, not one a player dropped. */
    fun inStore(obj: ZoneObj): Boolean = obj.droppedBy == null && obj.level == 1 && obj.x in STORE_X && obj.z in STORE_Z

    internal suspend fun Dialogue.straven(giveKey: () -> Boolean) = with(StravenLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        if (!hasKey(player)) {
            chatPlayer(ChatAnim.SAD, LOST_KEY)
            chatNpc(ChatAnim.ANGRY, LOST_KEY_1)
            if (giveKey()) objbox(WEAPON_STORE_KEY, HANDS_KEY)
            return@with
        }
        while (true) {
            when (choice(TREASURES, THIEVING, GO)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, TREASURES)
                    chatNpc(ChatAnim.HAPPY, TREASURES_1)
                    chatPlayer(ChatAnim.QUIZ, TREASURES_2)
                    chatNpc(ChatAnim.NEUTRAL, TREASURES_3)
                    chatPlayer(ChatAnim.QUIZ, TREASURES_4)
                    chatNpc(ChatAnim.ANGRY, TREASURES_5)
                    chatPlayer(ChatAnim.NEUTRAL, TREASURES_6)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, THIEVING)
                    chatNpc(ChatAnim.NEUTRAL, THIEVING_1)
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, GO)
                    return@with
                }
            }
        }
    }

    internal suspend fun Dialogue.weaponsmaster() = with(WeaponsmasterLines) {
        chatPlayer(ChatAnim.NEUTRAL, HELLO)
        chatNpc(ChatAnim.HAPPY, GREETING)
        when (choice(WEAPONS, TREASURE)) {
            1 -> {
                chatPlayer(ChatAnim.NEUTRAL, WEAPONS)
                chatNpc(ChatAnim.NEUTRAL, WEAPONS_1)
            }
            else -> {
                chatPlayer(ChatAnim.NEUTRAL, TREASURE)
                chatNpc(ChatAnim.NEUTRAL, TREASURE_1)
            }
        }
    }

    /**
     * Through a single gang door on the edge of its tile: [inside] says whether the player stands on the side that
     * needs no leave; from the other side [allowed] must hold or the player is told [refused]. The player steps through
     * onto the tile across the edge, as the Draynor jail's door does.
     */
    internal suspend fun ScriptContext.throughDoor(world: World, door: ZoneLoc, inside: Boolean, allowed: Boolean) {
        if (!inside && !allowed) {
            world.message(player, GangHouseLines.SECURELY_LOCKED)
            return
        }
        val (from, to) = sides(door)
        val here = if (player.x == from.x && player.z == from.z) from else if (player.x == to.x && player.z == to.z) to else null
        val there = if (here == to) from else to
        world.doors.openAs(door, DOOR_OPEN, shutAfter = DOOR_TICKS)
        player.waypoints.clear()
        if (here == null) player.waypoints.add(if (there == to) from else to)
        player.waypoints.add(there)
    }

    /** The two tiles a wall door at [door] stands between: its own, and the one across the edge its angle names. */
    fun sides(door: ZoneLoc): Pair<Tile, Tile> {
        val own = Tile(door.x, door.z)
        val across = when (door.angle) {
            0 -> Tile(door.x - 1, door.z)
            1 -> Tile(door.x, door.z + 1)
            2 -> Tile(door.x + 1, door.z)
            else -> Tile(door.x, door.z - 1)
        }
        return own to across
    }

    /** Whether the player is on [door]'s tile side or across it ([sides]); the nearer when on neither. */
    fun onOwnSide(door: ZoneLoc, x: Int, z: Int): Boolean {
        val (own, across) = sides(door)
        return abs(x - own.x) + abs(z - own.z) <= abs(x - across.x) + abs(z - across.z)
    }

    internal suspend fun ScriptContext.openChest(world: World, chest: ZoneLoc) {
        arriveDelay()
        player.anim(Searching.ANIM_OPEN)
        world.message(player, GangHouseLines.CHEST_SEARCH)
        delay(1)
        if (world.locs.exists(chest)) world.locs.replace(chest, CHEST_OPEN, revertAfter = CHEST_TICKS)
        world.message(player, GangHouseLines.CHEST_NOTHING)
    }

    internal suspend fun ScriptContext.takeCrossbow(world: World, obj: ZoneObj) {
        val watcher: Npc? = if (!inStore(obj)) null else world.npcs.inPlay.firstOrNull {
            it.type.id == WEAPONSMASTER_SPAWN && !it.outOfPlay && it.level == obj.level && abs(it.x - obj.x) <= WATCH_RANGE && abs(it.z - obj.z) <= WATCH_RANGE
        }
        if (watcher != null) {
            world.dialogues.start(this, watcher) { chatNpc(ChatAnim.ANGRY, WeaponsmasterLines.STRAVENS) }
            return
        }
        if (player.x != obj.x || player.z != obj.z) {
            delay(1)
            player.anim(GroundObjs.ANIM_PICKUP_TABLE)
        }
        world.groundObjs.take(player, obj)
    }
}

fun registerPhoenixGang(world: World) = with(PhoenixGang) {
    val talks: List<Pair<Int, suspend Dialogue.() -> Unit>> = listOf(
        STRAVEN to {
            straven { world.objTypes[WEAPON_STORE_KEY]?.let { player.inventory.add(it, 1) == 1 } ?: false }
        },
        KATRINE to { chatNpc(ChatAnim.ANGRY, KatrineLines.PHOENIX_SCUM) },
        WEAPONSMASTER to { weaponsmaster() },
    )
    for ((id, talk) in talks) world.handlers.onNpc(id, TALK_TO) { op -> world.dialogues.start(this, op.npc) { talk() } }

    world.handlers.onLoc(HIDEOUT_LADDER, OP1) { op ->
        val loc = op.loc!!
        climbBetween(world, loc, Tile(loc.x, loc.z), Tile(loc.x, loc.z - Climbing.DUNGEON_OFFSET), Climbing.ANIM_REACH_FOR_LADDER)
    }
    // Members pass Straven's door both ways; the store's door wants the key from the alley; the Black Arm Gang's inner
    // door keeps a Phoenix member out. Each by its tile, so a copy of the type elsewhere doesn't let anyone through.
    val doors = mapOf(
        HIDEOUT_DOOR to Tile(3247, 9779),
        STORE_DOOR to Tile(3251, 3386),
        BLACK_ARM_DOOR to Tile(3185, 3388),
    )
    for ((id, at) in doors) {
        world.doors.addClosedType(id, DOOR_OPEN)
        world.handlers.onLoc(id, OP1) { op ->
            val door = op.loc!!
            if (door.x != at.x || door.z != at.z || door.level != 0) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            arriveDelay()
            // The side needing no leave: the hideout from either side (a member), the store from inside (south of its
            // edge), the Black Arm house from its back (north of its edge).
            val ownSide = onOwnSide(door, player.x, player.z)
            when (id) {
                HIDEOUT_DOOR -> throughDoor(world, door, inside = true, allowed = true)
                // The key held, not banked: Straven's spare goes by any key the player owns ([hasKey]).
                STORE_DOOR -> throughDoor(world, door, inside = !ownSide, allowed = player.inventory.count(WEAPON_STORE_KEY) > 0)
                else -> throughDoor(world, door, inside = ownSide, allowed = false)
            }
        }
    }
    world.handlers.onLoc(CHEST, OP1) { op -> openChest(world, op.loc!!) }
    world.handlers.onGroundObj(PHOENIX_CROSSBOW, GroundObjs.TAKE_OPTION) { op -> takeCrossbow(world, op.groundObj!!) }
}
