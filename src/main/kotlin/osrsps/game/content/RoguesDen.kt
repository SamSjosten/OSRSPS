package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.World
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/**
 * The Rogues' Den under the Toad and Chicken in Burthorpe (Agility step 3), for Grace's shop and nothing else: Sam left
 * the maze out, so its doorway, passageways and people stay as they are.
 *
 * - The trapdoor behind the inn's bar (`7257` at 2905,3537, Enter) takes the player down into the den's north-east
 *   alcove, between the passageway (`7258` at 3061,4986) and the door (`7259` at 3061,4984). The 241 map has no other
 *   way out of the den, so the passageway (Enter) is taken as the way back up to the inn.
 * - The door lets the player through, either way, between the alcove and the den's main room.
 * - Grace (`rooftops_grace` 5919, npc-spawns/rogues-den.json) trades her shop (shops.json) from the generic Trade.
 *
 * Each is keyed on its tile as well as its type ([TRAPDOOR_AT], [PASSAGEWAY_AT], [DOOR_AT]): the den's maze, which isn't
 * built, has the same door type, so a copy anywhere else is nothing interesting rather than a way into the den.
 *
 * Inferred from the 241 map: the alcove and the door. Guessed: every tile a crossing lands on (the alcove's north tile,
 * the inn's floor east of the trapdoor), that the passageway is the exit, the trapdoor's animation (the one the other
 * trapdoors use) and the waits, and that the door is a plain way through.
 */
object RoguesDen {
    const val TRAPDOOR = 7257
    const val PASSAGEWAY = 7258
    const val DOOR = 7259
    const val ENTER = 1
    const val OPEN = 1

    /** Where the trapdoor, the passageway and the door stand (the 241 map). */
    val TRAPDOOR_AT = Destination(2905, 3537, 0)
    val PASSAGEWAY_AT = Destination(3061, 4986, 1)
    val DOOR_AT = Destination(3061, 4984, 1)

    /** Where the trapdoor puts the player, and the passageway. */
    val ARRIVAL = Destination(3061, 4985, 1)
    val INN = Destination(2906, 3537, 0)

    /** The two sides of the door: the alcove's south tile, and the main room's tile below it. */
    val DOOR_ALCOVE = Destination(3061, 4984, 1)
    val DOOR_ROOM = Destination(3061, 4983, 1)
}

fun registerRoguesDen(world: World) = with(RoguesDen) {
    world.handlers.onLoc(TRAPDOOR, ENTER) { op ->
        if (op.loc.isAt(TRAPDOOR_AT)) cross(world, ARRIVAL, Climbing.ANIM_PICKUP_FLOOR) else world.message(player, GameLines.NOTHING_INTERESTING)
    }
    world.handlers.onLoc(PASSAGEWAY, ENTER) { op ->
        if (op.loc.isAt(PASSAGEWAY_AT)) cross(world, INN, anim = -1) else world.message(player, GameLines.NOTHING_INTERESTING)
    }
    world.handlers.onLoc(DOOR, OPEN) { op ->
        if (!op.loc.isAt(DOOR_AT)) return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
        cross(world, if (player.z >= DOOR_ALCOVE.z) DOOR_ROOM else DOOR_ALCOVE, anim = -1)
    }
}

private fun ZoneLoc?.isAt(at: Destination) = this != null && x == at.x && z == at.z && level == at.level

/** Takes the player to [to] a tick after they arrive, with [anim] first when there is one (-1 for none). */
private suspend fun ScriptContext.cross(world: World, to: Destination, anim: Int) {
    arriveDelay()
    if (anim >= 0) player.anim(anim)
    delay(1)
    world.teleport(player, to.x, to.z, to.level)
}
