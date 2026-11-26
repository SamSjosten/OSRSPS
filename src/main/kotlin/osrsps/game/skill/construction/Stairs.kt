package osrsps.game.skill.construction

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.GameLines
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.Ned
import osrsps.game.content.arriveDelay
import osrsps.game.content.message
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

private val logger = KotlinLogging.logger {}

/**
 * Construction step CON5: a house's upper floor and the hall staircases that reach it, from the Wiki's Player-owned
 * house, Hall (skill trophies), Hall (quest trophies) and Stair Space pages (read 2026-11-26).
 *
 * - A staircase goes on a hall's stair space; in a ground floor hall the player picks "Up" or "Down" first. Down
 *   waits for the dungeon (step CON10).
 * - Climb-up on a staircase with nothing above, in building mode, asks which room to build at its top: a skill hall or
 *   a quest hall, whichever hall the staircase is in. That room ([RoomType.upstairs]) is built over the hall, turned the
 *   same way, with the same staircase at its top, so the staircase's materials are taken again (the Hall pages: a marble
 *   spiral with a skill hall above "costs 20x Teak plank + 14x Marble block") and its XP given again (GUESSED).
 * - Each end of a staircase is built, and paid for, on its own: twice the materials at once wouldn't fit in a backpack
 *   with a hammer and saw (Codex review on #990). Climb-up where the hall above faces the same way with a bare stair
 *   space, in building mode, builds the top there and climbs (the pages: it "will connect to the room above or below
 *   by default if and only if the room is facing the proper direction"). Upstairs, only the staircase of the hall
 *   below, or any while its stair space is bare, can go in.
 * - Climb-up and Climb-down move the player straight up or down, on their own tile (GUESSED: the pages don't say where
 *   a climb lands); a spiral's Climb goes up from the ground floor and down from upstairs.
 * - Remove-room on a staircase removes its room, as Build on a doorway to it would.
 *
 * GUESSED: the prompts' words and layout; a staircase that leads to the other floor can't be removed until the room at
 * its top is ([ConstructionLines.STAIRS_CONNECTED]), and removing the room upstairs puts whoever is in it, or stranded
 * upstairs with no staircase left, on the floor below on the same tile. Guests (step CON11) can't climb yet.
 */
class Stairs(private val world: World, private val houses: Houses, private val furniture: Furniture) {
    fun register() {
        for (loc in FOOT) world.handlers.onLoc(loc, 1) { op -> arriveDelay(); up(this, op.loc ?: return@onLoc) }
        for (loc in TOP) world.handlers.onLoc(loc, 1) { op -> arriveDelay(); down(this, op.loc ?: return@onLoc) }
        for (loc in SPIRALS) {
            world.handlers.onLoc(loc, 1) { op ->
                arriveDelay()
                val at = op.loc ?: return@onLoc
                if (at.level == House.GROUND) up(this, at) else down(this, at)
            }
            world.handlers.onLoc(loc, 2) { op -> arriveDelay(); up(this, op.loc ?: return@onLoc) }
            world.handlers.onLoc(loc, 3) { op -> arriveDelay(); down(this, op.loc ?: return@onLoc) }
        }
        for (loc in FOOT + TOP + SPIRALS) {
            world.handlers.onLoc(loc, REMOVE_ROOM) { op ->
                arriveDelay()
                val at = op.loc ?: return@onLoc
                val (house, room) = houses.buildingRoom(player, at.x, at.z, at.level) ?: return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
                houses.remove(this, house, player.house ?: return@onLoc, room)
            }
        }
    }

    /** The owner's house and the room [loc] is built in; null (with a message) for anyone else, or anywhere else. */
    private fun roomOf(context: ScriptContext, loc: ZoneLoc): Triple<Houses.Open, HouseState, HouseRoom>? {
        val player = context.player
        val house = houses.houseAt(loc.x, loc.z)
        val state = player.house
        if (house == null || state == null || house.owner != player.saveKey) {
            world.message(player, GameLines.NOTHING_INTERESTING)
            return null
        }
        val room = state.room((loc.x - house.instance.baseX) shr 3, (loc.z - house.instance.baseZ) shr 3, loc.level) ?: return null
        if (room.built[stairSpace(room.type) ?: return null] != loc.id) return null
        return Triple(house, state, room)
    }

    /** Climb-up on [loc]: up to the hall at its top; with nothing there, in building mode, the prompt to build one. */
    private suspend fun up(context: ScriptContext, loc: ZoneLoc) {
        val player = context.player
        if (loc.level != House.GROUND) return world.message(player, GameLines.NOTHING_INTERESTING)
        val (house, state, room) = roomOf(context, loc) ?: return
        val above = state.room(room.x, room.z, House.UPPER)
        val waiting = otherEnd(state, room)?.takeIf(::bare)
        when {
            connected(state, room) -> world.teleport(player, player.x, player.z, House.UPPER)
            house.buildingMode && waiting != null -> connectTop(context, house, state, room, waiting)
            above != null -> world.message(player, ConstructionLines.STAIRS_BLOCKED)
            !house.buildingMode -> world.message(player, ConstructionLines.STAIRS_NOTHING_ABOVE)
            else -> buildTop(context, house, state, room)
        }
    }

    /**
     * Climb-up on [hall]'s staircase where [top], the hall above, waits for it: its top built there for its materials,
     * and the climb.
     */
    private fun connectTop(context: ScriptContext, house: Houses.Open, state: HouseState, hall: HouseRoom, top: HouseRoom) {
        val player = context.player
        val row = staircase(hall) ?: return
        val build = pay(context, hall, row) ?: return
        val space = stairSpace(top.type) ?: return
        furniture.fill(top, space, build)
        houses.lay(house, state, top.type, Houses.Cell(top.x, top.z, top.floor), top.rotation)
        world.teleport(player, player.x, player.z, House.UPPER)
        logger.info { "${player.username} built the top of their staircase at ${top.x}, ${top.z}" }
    }

    /**
     * The level, tools and materials for staircase [row] in [hall]'s place, taken, with its XP given; null (with a
     * message) when the player lacks any.
     */
    private fun pay(context: ScriptContext, hall: HouseRoom, row: Int): FurnitureBuilds.Build? {
        val player = context.player
        val piece = furniture.data.options(hall.type.base.id, 0).firstOrNull { it.row == row }
        val build = FurnitureBuilds.BUILDS[row]
        if (piece == null || build == null) {
            world.message(player, GameLines.NOTHING_INTERESTING)
            return null
        }
        val lacks = when {
            player.skills.currentLevel(Skill.CONSTRUCTION) < piece.level -> {
                world.message(player, ConstructionLines.FURNITURE_LEVEL, "level" to piece.level)
                true
            }
            !furniture.hasTools(player) -> {
                world.message(player, ConstructionLines.FURNITURE_TOOLS)
                true
            }
            else -> false
        }
        if (lacks) return null
        val nails = furniture.nailsFor(player, piece) ?: run {
            world.message(player, ConstructionLines.FURNITURE_MATERIALS)
            return null
        }
        for ((obj, count) in piece.materials) if (obj != Furniture.ANY_NAILS) Ned.take(player.inventory, obj, count)
        for ((kind, count) in nails) Ned.take(player.inventory, kind, count)
        player.skills.addTenths(Skill.CONSTRUCTION, build.xp * 10)
        return build
    }

    /** Climb-down on [loc]: down to the hall at its foot. */
    private fun down(context: ScriptContext, loc: ZoneLoc) {
        val player = context.player
        if (loc.level != House.UPPER) return world.message(player, GameLines.NOTHING_INTERESTING)
        val (_, state, room) = roomOf(context, loc) ?: return
        if (!connected(state, room)) return world.message(player, ConstructionLines.STAIRS_BLOCKED)
        world.teleport(player, player.x, player.z, House.GROUND)
    }

    /** The prompt for the room at the top of [hall]'s staircase, then that room built with the staircase's top, and the climb. */
    private suspend fun buildTop(context: ScriptContext, house: Houses.Open, state: HouseState, hall: HouseRoom) {
        val player = context.player
        val row = staircase(hall) ?: return
        var pick = 0
        world.dialogues.start(context) {
            pick = choice(
                ConstructionLines.STAIRS_TOP_SKILL_HALL, ConstructionLines.STAIRS_TOP_QUEST_HALL, ConstructionLines.STAIRS_TOP_CANCEL,
                title = text(ConstructionLines.STAIRS_TOP_TITLE),
            )
        }
        val type = when (pick) {
            1 -> RoomType.SKILL_HALL_STAIRS
            2 -> RoomType.QUEST_HALL_STAIRS
            else -> return
        }
        // Checked after the prompt's wait: the hall, its staircase and the empty cell above must all still be there.
        if (!houses.stillHere(house, player) || state.room(hall.x, hall.z) !== hall || staircase(hall) != row || state.room(hall.x, hall.z, House.UPPER) != null) return
        val level = player.skills.baseLevel(Skill.CONSTRUCTION)
        if (state.rooms.size >= House.maxRooms(level)) return world.message(player, ConstructionLines.ROOM_LIMIT, "max" to House.maxRooms(level))
        if (player.skills.currentLevel(Skill.CONSTRUCTION) < type.level) return world.message(player, ConstructionLines.ROOM_LEVEL, "level" to type.level)
        if (player.inventory.count(EstateAgent.COINS) < type.cost) return world.message(player, ConstructionLines.ROOM_COINS, "cost" to type.cost)
        val build = pay(context, hall, row) ?: return
        Ned.take(player.inventory, EstateAgent.COINS, type.cost)
        val top = HouseRoom(type, hall.x, hall.z, House.UPPER, hall.rotation)
        furniture.fill(top, stairSpace(type) ?: return, build)
        state.rooms += top
        houses.lay(house, state, type, Houses.Cell(top.x, top.z, top.floor), top.rotation)
        world.teleport(player, player.x, player.z, House.UPPER)
        logger.info { "${player.username} built a ${type.key} at ${top.x}, ${top.z} upstairs, over their staircase, for ${type.cost} coins" }
    }


    companion object {
        /** A staircase's foot (Climb-up): `poh_stairs_3` to `5`. */
        val FOOT = setOf(13497, 13499, 13501)

        /** A staircase's top (Climb-down): `poh_stairstop_3` to `5`. */
        val TOP = setOf(13498, 13500, 13502)

        /** The spiral staircases (Climb, Climb-up, Climb-down), the same loc at the foot and the top. */
        val SPIRALS = setOf(13503, 13505)

        /** Every staircase's op 4. */
        const val REMOVE_ROOM = 4

        /** [type]'s stair space: `poh_hall1_1_stairs_up` and `_top`, `poh_hall2_1_stairs_up` and `_top`; null for any other room. */
        fun stairSpace(type: RoomType): Int? = when (type) {
            RoomType.SKILL_HALL -> 15380
            RoomType.SKILL_HALL_STAIRS -> 15381
            RoomType.QUEST_HALL -> 15390
            RoomType.QUEST_HALL_STAIRS -> 15391
            else -> null
        }

        /** The staircase (its furniture row) built in [room], if any. */
        fun staircase(room: HouseRoom): Int? {
            val space = stairSpace(room.type) ?: return null
            return FurnitureBuilds.builtRow(room.built, space, room.type)?.takeIf { it in FurnitureBuilds.STAIRCASES }
        }

        /**
         * The hall at the other end of [room]'s stair space: over a ground floor hall, the hall at the top of a staircase
         * in the same cell upstairs, turned the same way; and the other way round. Null when there is none.
         */
        fun otherEnd(state: HouseState, room: HouseRoom): HouseRoom? {
            if (stairSpace(room.type) == null) return null
            val floor = if (room.floor == House.GROUND) House.UPPER else House.GROUND
            val other = state.room(room.x, room.z, floor) ?: return null
            val top = other.type == RoomType.SKILL_HALL_STAIRS || other.type == RoomType.QUEST_HALL_STAIRS
            return other.takeIf { top == (floor == House.UPPER) && stairSpace(it.type) != null && it.rotation == room.rotation }
        }

        /** Whether [room]'s staircase leads to the other floor: the hall at its other end has the same staircase. */
        fun connected(state: HouseState, room: HouseRoom): Boolean {
            val row = staircase(room) ?: return false
            val other = otherEnd(state, room) ?: return false
            return staircase(other) == row
        }

        /** Whether nothing is built at [room]'s stair space's place. */
        fun bare(room: HouseRoom): Boolean {
            val space = stairSpace(room.type) ?: return false
            return FurnitureBuilds.siblings(space, room.type).none { it in room.built }
        }

        /** Whether staircase [row] can go in [room]: anywhere downstairs; upstairs over a hall whose place is bare or holds [row]. */
        fun fits(state: HouseState, room: HouseRoom, row: Int): Boolean {
            if (room.floor != House.UPPER) return true
            val below = otherEnd(state, room) ?: return false
            return bare(below) || staircase(below) == row
        }

        /**
         * Before staircase [row] goes in [room]: on the ground floor, the prompt for its way (Down waits for the
         * dungeon); upstairs, the check that it is the staircase of the hall below ([fits]). False (with a message, but
         * for a prompt left) where it can't.
         */
        suspend fun prepare(world: World, context: ScriptContext, state: HouseState, room: HouseRoom, row: Int): Boolean {
            val player = context.player
            if (room.floor == House.GROUND) {
                var pick = 0
                world.dialogues.start(context) {
                    pick = choice(ConstructionLines.STAIRS_UP, ConstructionLines.STAIRS_DOWN, title = text(ConstructionLines.STAIRS_TITLE))
                }
                if (pick == 2) world.message(player, ConstructionLines.STAIRS_DOWN_LATER)
                return pick == 1
            }
            if (fits(state, room, row)) return true
            world.message(player, ConstructionLines.STAIRS_NO_MATCH)
            return false
        }

        /**
         * After a room upstairs at [cell] goes: whoever stood in it, and everyone upstairs once no staircase leads down,
         * goes to the floor below on the same tile (GUESSED), where the room under them still stands.
         */
        fun strand(world: World, house: Houses.Open, state: HouseState, cell: Houses.Cell) {
            val way = state.rooms.any { it.floor == House.UPPER && connected(state, it) }
            val instance = house.instance
            for (player in world.onlinePlayers) {
                if (player.level != House.UPPER || !instance.contains(player.x, player.z)) continue
                val inCell = (player.x - instance.baseX) shr 3 == cell.x && (player.z - instance.baseZ) shr 3 == cell.z
                if (inCell || !way) world.teleport(player, player.x, player.z, House.GROUND)
            }
        }
    }
}
