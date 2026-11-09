package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.ObjType
import osrsps.game.PlayerEffects
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.AmmoKind
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.skill.Agility
import osrsps.game.zone.ZoneLoc
import osrsps.map.Tile

/** What the rest of Lumbridge Swamp says; keys only, the words are in the dialogue file. */
object LumbridgeSwampLines : Lines("lumbridge_swamp", "Lumbridge Swamp") {
    val ZANARIS_CLOSED = line("zanaris_closed", "the shed's door opened wielding a dramen or lunar staff: our own line, as Zanaris isn't built (piece 8)")
    val TOOLS_WHICH = line("tools_which", "the shed's tools taken holding neither a rake nor a spade: the menu's title; words guessed")
    val TOOLS_RAKE = line("tools_rake", "that menu, option 1: the rake; words guessed")
    val TOOLS_SPADE = line("tools_spade", "that menu, option 2: the spade; words guessed")
    val TOOLS_HAVE_BOTH = line("tools_have_both", "the shed's tools taken holding a rake and a spade; words guessed")
    val TOOLS_NO_ROOM = line("tools_no_room", "the shed's tools taken with a full inventory; words guessed")
    val RUNECRAFT_CLOSED = line("runecraft_closed", "a water talisman or tiara used on the Water Altar's ruins, or Enter: our own line, as Runecraft isn't built")
    val GRAPPLE_LEVELS = line("grapple_levels", "the broken raft's Grapple below 8 Agility, 19 Strength or 37 Ranged; words guessed", "agility", "strength", "ranged")
    val GRAPPLE_GEAR = line("grapple_gear", "the broken raft's Grapple without a crossbow wielded and a mith grapple worn; words guessed")
    val GRAPPLE_BROKEN = line("grapple_broken", "the mith grapple breaking after a crossing (the Wiki's Mith grapple page quotes it)")
}

/**
 * The rest of Lumbridge Swamp (Misthalin piece 7, step 1), from the OSRS Wiki's Lumbridge Swamp, Shed (Lumbridge Swamp),
 * Tools (gardening), Mysterious ruins, Broken Raft and Mith grapple pages, read 2026-11-09, and the 242 cache.
 *
 * - The shed in the swamp's middle (`zanarisdoor` 2406 at 3202,3169, on the west edge of the shed's doorway tile):
 *   Open lets the player through, as the Champions' Guild's door does, since its open type (`zanarisdoor_open` 10363)
 *   has no Close; unless the player wields a dramen or lunar staff, which on live takes them to Zanaris. Zanaris is
 *   piece 8's, so that says so instead. The Elite Lumbridge Diary does the same on live; there are no diaries, so it
 *   doesn't here. Guessed: the door stands open 3 ticks, as the guild's does.
 * - The tools inside (`farming_shed_tools` 10375 at 3203,3170): Take gives a rake or a spade, whichever the player
 *   doesn't hold, and a choice of the two when they hold neither. Guessed: nothing at all with both held, and no
 *   animation.
 * - The Water Altar's ruins (`watertemple_ruined` 34815 at 3184,3164, a multiloc that shows Enter with a water tiara
 *   worn): Runecraft isn't built, so a water talisman or tiara used on them, or Enter, gets our own refusal.
 * - The broken raft in the River Lum (`xbows_raft_br` 17068 at 3252,3179): Grapple, with a crossbow wielded and a mith
 *   grapple worn, at 8 Agility, 19 Strength and 37 Ranged (the infobox's 19; its text says 17), from either bank's
 *   shortcut icon (3246,3179 and 3259,3179, as the 242 map places `agility_shortcut_icon`) to the other, no XP. A
 *   grapple breaks on 1 crossing in 25 (Mod Ash, quoted on the Mith grapple page). The raft's Jump-to, at 48 Agility
 *   without a grapple, is agility.json's.
 *
 * Hewey, Abigale and Sergeants Mossfists and Slimetoes are spawns only (lumbridge-swamp-quest.json): the 242 cache
 * shows each of them only part way through their quest, so after it they are there but hidden.
 */
object LumbridgeSwamp {
    const val SHED_DOOR = 2406
    const val SHED_DOOR_OPEN = 10363
    const val SHED_DOOR_TILE_X = 3202
    const val SHED_DOOR_TILE_Z = 3169
    const val SHED_DOOR_TICKS = 3
    const val OPEN = 1
    const val TOOLS = 10375
    const val TAKE = 1
    const val WATER_RUINS = 34815

    /** `watertemple_ruined_new`: what the ruins show with `rc_no_tally_required_water` set, with its Enter. */
    const val WATER_RUINS_ENTER = 29097
    const val ENTER = 1
    const val RAFT = 17068
    const val GRAPPLE = 1

    const val DRAMEN_STAFF = 772
    const val LUNAR_STAFF = 9084
    const val RAKE = 5341
    const val SPADE = 952
    const val WATER_TALISMAN = 1444
    const val WATER_TIARA = 5531
    const val MITH_GRAPPLE = 9419

    const val GRAPPLE_AGILITY = 8
    const val GRAPPLE_STRENGTH = 19
    const val GRAPPLE_RANGED = 37

    /** One crossing in 25 breaks the grapple. */
    const val GRAPPLE_BREAK_CHANCE = 25

    val RAFT_WEST = Tile(3246, 3179)
    val RAFT_EAST = Tile(3259, 3179)

    /** `xbows_human_fire_and_climb_grapple`. Guessed: the raft's firing animation, and its 2 ticks. */
    const val ANIM_FIRE_GRAPPLE = 4455
    const val FIRE_TICKS = 2

    /** `xbows_lumbridge_west_swimming_anim` and `_east_`: by their names, the pull across from each bank (guessed which). */
    const val ANIM_SWIM_FROM_WEST = 4467
    const val ANIM_SWIM_FROM_EAST = 4466

    /** Guessed: the pull across takes 6 ticks. */
    const val SWIM_TICKS = 6

    /** Whether [loc] is the shed's door where the 242 map has it: the type is also Zanaris's other doors' elsewhere. */
    fun isShedDoor(loc: ZoneLoc): Boolean = loc.x == SHED_DOOR_TILE_X && loc.z == SHED_DOOR_TILE_Z && loc.level == 0

    /** The shed's door is on the west edge of its tile: the tile itself is inside, the one west of it outside. */
    fun insideShed(x: Int): Boolean = x >= SHED_DOOR_TILE_X

    /** The tool a Take gives: the one not held, null for a choice (neither held) or nothing (both). */
    fun toolFor(hasRake: Boolean, hasSpade: Boolean): Int? = when {
        hasRake && !hasSpade -> SPADE
        hasSpade && !hasRake -> RAKE
        else -> null
    }

    /** The bank the raft is crossed to from [from], or null when [from] isn't one of its two banks. */
    fun raftLanding(from: Tile): Tile? = when (from) {
        RAFT_WEST -> RAFT_EAST
        RAFT_EAST -> RAFT_WEST
        else -> null
    }

    internal suspend fun ScriptContext.shedDoor(world: World, door: ZoneLoc) {
        arriveDelay()
        if (!isShedDoor(door) || !world.locs.exists(door)) return world.message(player, GameLines.NOTHING_INTERESTING)
        val staff = player.equipment[WearPos.WEAPON]?.id
        if (staff == DRAMEN_STAFF || staff == LUNAR_STAFF) return world.message(player, LumbridgeSwampLines.ZANARIS_CLOSED)
        val inside = insideShed(player.x)
        val front = Tile(if (inside) SHED_DOOR_TILE_X else SHED_DOOR_TILE_X - 1, SHED_DOOR_TILE_Z)
        // A player beside the doorway (it is reached from the tiles either side too) steps in front of it first.
        if (player.x != front.x || player.z != front.z) {
            world.teleport(player, front.x, front.z, 0)
            delay(1)
            if (!world.locs.exists(door)) return
        }
        world.doors.openAs(door, SHED_DOOR_OPEN, shutAfter = SHED_DOOR_TICKS)
        player.waypoints.clear()
        player.waypoints.add(Tile(if (inside) SHED_DOOR_TILE_X - 1 else SHED_DOOR_TILE_X, SHED_DOOR_TILE_Z))
    }

    internal suspend fun ScriptContext.takeTools(world: World) {
        arriveDelay()
        val hasRake = player.inventory.count(RAKE) > 0
        val hasSpade = player.inventory.count(SPADE) > 0
        if (hasRake && hasSpade) return world.message(player, LumbridgeSwampLines.TOOLS_HAVE_BOTH)
        if (player.inventory.freeSlots == 0) return world.message(player, LumbridgeSwampLines.TOOLS_NO_ROOM)
        val tool = toolFor(hasRake, hasSpade) ?: run {
            var picked = RAKE
            world.dialogues.start(this) {
                val choice = choice(LumbridgeSwampLines.TOOLS_RAKE, LumbridgeSwampLines.TOOLS_SPADE, title = text(LumbridgeSwampLines.TOOLS_WHICH))
                picked = if (choice == 2) SPADE else RAKE
            }
            // The menu waited: check again that it still fits and isn't held.
            if (player.inventory.freeSlots == 0 || player.inventory.count(picked) > 0) return
            picked
        }
        val type: ObjType = world.objTypes[tool] ?: return
        player.inventory.add(type, 1)
    }

    internal suspend fun ScriptContext.grapple(world: World) {
        arriveDelay()
        val from = Tile(player.x, player.z)
        val to = raftLanding(from) ?: return world.message(player, GameLines.CANT_REACH)
        val skills = player.skills
        if (skills.currentLevel(Skill.AGILITY) < GRAPPLE_AGILITY || skills.currentLevel(Skill.STRENGTH) < GRAPPLE_STRENGTH ||
            skills.currentLevel(Skill.RANGED) < GRAPPLE_RANGED
        ) {
            return world.message(
                player, LumbridgeSwampLines.GRAPPLE_LEVELS,
                "agility" to GRAPPLE_AGILITY, "strength" to GRAPPLE_STRENGTH, "ranged" to GRAPPLE_RANGED,
            )
        }
        val weapon = world.rangedWeapons.weapon(player.equipment[WearPos.WEAPON]?.id?.let { world.objTypes[it] })
        if (weapon?.kind != AmmoKind.BOLTS || player.equipment[WearPos.AMMO]?.id != MITH_GRAPPLE) {
            return world.message(player, LumbridgeSwampLines.GRAPPLE_GEAR)
        }
        player.anim(ANIM_FIRE_GRAPPLE)
        delay(FIRE_TICKS)
        val angle = Agility.angle(to.x - from.x, to.z - from.z)
        player.effects.exactMove(PlayerEffects.ExactMove(from.x, from.z, to.x, to.z, 15, SWIM_TICKS * 30, angle))
        player.anim(if (from == RAFT_WEST) ANIM_SWIM_FROM_WEST else ANIM_SWIM_FROM_EAST)
        delay(SWIM_TICKS)
        world.teleport(player, to.x, to.z, 0)
        // Inferred: the roll is made as the rope is untied, on the far bank, as the message says.
        if (world.random.between(1, GRAPPLE_BREAK_CHANCE) == 1 && player.equipment[WearPos.AMMO]?.id == MITH_GRAPPLE) {
            player.equipment.remove(WearPos.AMMO, 1)
            world.message(player, LumbridgeSwampLines.GRAPPLE_BROKEN)
        }
    }
}

/** The shed's door and tools, the Water Altar's ruins and the raft's Grapple. */
fun registerLumbridgeSwamp(world: World) {
    with(LumbridgeSwamp) {
        world.handlers.onLoc(SHED_DOOR, OPEN) { op -> shedDoor(world, op.loc!!) }
        world.handlers.onLoc(TOOLS, TAKE) { takeTools(world) }
        val refuse: suspend ScriptContext.() -> Unit = {
            arriveDelay()
            world.message(player, LumbridgeSwampLines.RUNECRAFT_CLOSED)
        }
        world.handlers.onLoc(WATER_RUINS_ENTER, ENTER) { refuse() }
        for (ruins in listOf(WATER_RUINS, WATER_RUINS_ENTER)) {
            for (obj in listOf(WATER_TALISMAN, WATER_TIARA)) world.handlers.onObjOnLoc(ruins, obj) { refuse() }
        }
        world.handlers.onLoc(RAFT, GRAPPLE) { grapple(world) }
        // The raft is in the river: a click walks to the nearer bank's shortcut tile.
        world.useLocFrom(RAFT) { _, _ -> listOf(RAFT_WEST, RAFT_EAST) }
    }
}
