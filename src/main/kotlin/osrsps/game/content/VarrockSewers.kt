package osrsps.game.content

import osrsps.game.EquipmentBonuses
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.WearPos
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc

/** The webs' messages; the words are in the dialogue file, from the Wiki's Web page. */
object WebLines : Lines("web", "Web") {
    val NO_BLADE = line("no_blade", "Slash on a web with no blade worn or carried")
    val SLASHED = line("slashed", "a web cut apart")
    val FAILED = line("failed", "a slash that doesn't cut the web")
}

/**
 * The Varrock Sewers (the Varrock piece's step 15), under the city, as the 241 cache's map has them.
 *
 * - **The manhole** east of the palace (`manholeclosed` 881 at 3237,3458) opens into `manholeopen` (882). That one climbs
 *   down to the ladder 6400 below (`fai_varrock_manhole_ladder` 11806 at 3237,9858) and closes again. The ladder climbs
 *   back up. Every manhole of these types works the same way, since the map has them only over dungeons.
 * - **The bear pen's trapdoor** (`fai_varrock_bearpit_trapdoor` 7142 at 3230,3504) climbs down to the cellar ladder
 *   below (`ladder_from_cellar` 17385 at 3230,9904), which already climbs up ([LumbridgeCastle]).
 * - **The web** east of the deadly red spiders (`bigweb_slashable` 733): Slash, or a blade used on it, cuts it
 *   (`bigweb_slashed` 734) for [Webs.SLASHED_TICKS]. A blade is a knife, or a weapon with a slash bonus, worn or
 *   carried. Each try succeeds as the Wiki's Web page gives it: a knife 50%, a weapon with slash +100 or more 100%, and
 *   20% below +20.
 * - The rats, spiders, zombies, skeletons, ghosts, moss giants and scorpions fight and drop as npc-combat.json and
 *   npc-drops.json have them (npc-spawns/varrock-sewers.json).
 *
 * From the Wiki's Varrock Sewers, Web and Manhole pages (read 2026-11-03). Guessed:
 * - the manhole staying open for [Searching.CHANGE_TICKS], as other furniture does;
 * - how long a cut web stays cut ([Webs.SLASHED_TICKS]);
 * - a slash bonus from +20 to +99 rising in a straight line from 20% to 100% (the Wiki says only that it scales);
 * - the slash animation.
 *
 * Left out:
 * - the Wilderness swords and the 50% least for slash swords;
 * - the Aranea boots;
 * - the obstacle pipe shortcut, which is the Agility thread's;
 * - Bryophyta's gate (a members' boss).
 */
object VarrockSewers {
    const val MANHOLE = 881
    const val MANHOLE_OPEN = 882
    const val MANHOLE_LADDER = 11806
    const val BEAR_PIT_TRAPDOOR = 7142
    const val OPEN = 1
    const val CLIMB = 1
    const val CLOSE = 2
}

/** Cutting webs; see [VarrockSewers]. */
object Webs {
    const val WEB = 733
    const val WEB_SLASHED = 734
    const val SLASH = 1

    /** A cut web grows back after a minute (guessed). */
    const val SLASHED_TICKS = 100

    /** knife. */
    const val KNIFE = 946

    /** human_sword_slash, guessed for every blade. */
    const val ANIM_SLASH = 390

    /** A slash bonus of at least this cuts every time (the Wiki). */
    const val SURE_BONUS = 100

    /** Below this slash bonus a weapon cuts one try in five (the Wiki). */
    const val LOW_BONUS = 20

    /** The chance, out of 100, that [type] cuts a web; 0 when it isn't a blade. */
    fun chance(type: ObjType): Int {
        if (type.id == KNIFE) return 50
        if (type.wearpos1 != WearPos.WEAPON) return 0
        val slash = type.intParam(EquipmentBonuses.PARAMS[EquipmentBonuses.SLASH_ATTACK])
        return when {
            slash <= 0 -> 0
            slash >= SURE_BONUS -> 100
            slash < LOW_BONUS -> 20
            else -> 20 + (slash - LOW_BONUS) * 80 / (SURE_BONUS - LOW_BONUS)
        }
    }

    /** The best chance among [player]'s worn weapon and what they carry. */
    fun bestChance(world: World, player: Player): Int {
        val held = player.equipment.contents().map { it.second.id } + player.inventory.contents().map { it.second.id }
        return held.mapNotNull { world.objTypes[it] }.maxOfOrNull { chance(it) } ?: 0
    }
}

fun registerVarrockSewers(world: World) {
    with(VarrockSewers) {
        world.handlers.onLoc(MANHOLE, OPEN) { op -> swapFurniture(world, op.loc!!, MANHOLE_OPEN, Searching.ANIM_OPEN) }
        world.handlers.onLoc(MANHOLE_OPEN, CLOSE) { op -> swapFurniture(world, op.loc!!, MANHOLE, Searching.ANIM_SHUT) }
        world.handlers.onLoc(MANHOLE_OPEN, CLIMB) { op ->
            climbDungeonLadder(world, op.loc!!, down = true, anim = Climbing.ANIM_PICKUP_FLOOR)
        }
        world.handlers.onLoc(MANHOLE_LADDER, CLIMB) { op ->
            climbDungeonLadder(world, op.loc!!, down = false, anim = Climbing.ANIM_REACH_FOR_LADDER)
        }
        world.handlers.onLoc(BEAR_PIT_TRAPDOOR, CLIMB) { op ->
            climbDungeonLadder(world, op.loc!!, down = true, anim = Climbing.ANIM_PICKUP_FLOOR)
        }
    }
    world.handlers.onLoc(Webs.WEB, Webs.SLASH) { op -> slashWeb(world, op.loc!!, Webs.bestChance(world, player)) }
    // A blade used on the web tries with that blade.
    for (type in (0 until world.objTypes.size).mapNotNull { world.objTypes[it] }) {
        if (Webs.chance(type) > 0) world.handlers.onObjOnLoc(Webs.WEB, type.id) { op -> slashWeb(world, op.loc!!, Webs.chance(type)) }
    }
}

private suspend fun ScriptContext.slashWeb(world: World, web: ZoneLoc, chance: Int) {
    arriveDelay()
    if (!world.locs.exists(web)) return
    if (chance <= 0) {
        world.message(player, WebLines.NO_BLADE)
        return
    }
    player.anim(Webs.ANIM_SLASH)
    delay(1)
    // Someone else may have cut it meanwhile.
    if (!world.locs.exists(web)) return
    if (world.random.chance(chance, 100)) {
        world.locs.replace(web, Webs.WEB_SLASHED, revertAfter = Webs.SLASHED_TICKS)
        world.message(player, WebLines.SLASHED)
    } else {
        world.message(player, WebLines.FAILED)
    }
}
