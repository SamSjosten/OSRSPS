package osrsps.game.skill

import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.content.arriveDelay
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.map.Tile

/** Runecraft's words, in the dialogue file. */
object RunecraftLines : Lines("runecraft", "Runecraft") {
    val ENTER = line("enter", "the message on entering mysterious ruins, by a talisman or a tiara (Mysterious ruins)")
    val LOCATE_NORTH = line("locate_north", "a talisman's Locate when its ruins lie to the north")
    val LOCATE_NORTH_EAST = line("locate_north_east", "a talisman's Locate when its ruins lie to the north-east")
    val LOCATE_EAST = line("locate_east", "a talisman's Locate when its ruins lie to the east")
    val LOCATE_SOUTH_EAST = line("locate_south_east", "a talisman's Locate when its ruins lie to the south-east")
    val LOCATE_SOUTH = line("locate_south", "a talisman's Locate when its ruins lie to the south")
    val LOCATE_SOUTH_WEST = line("locate_south_west", "a talisman's Locate when its ruins lie to the south-west")
    val LOCATE_WEST = line("locate_west", "a talisman's Locate when its ruins lie to the west")
    val LOCATE_NORTH_WEST = line("locate_north_west", "a talisman's Locate when its ruins lie to the north-west")
    val LOCATE_UNSURE = line("locate_unsure", "the elemental talisman's Locate when two of its ruins are equally near (Elemental talisman)")
    val PORTAL = line("portal", "the message on using an altar's exit portal")
}

/**
 * One runic altar reached through mysterious ruins: [ruins] is the map's multiloc (3x3), which shows [ruinsTypes]
 * (no options, then Enter) by [varbit] (`rc_no_tally_required_*`); [talisman] and [tiara] are its own; [ruinsTile] is
 * the multiloc's south-west tile; [inside] is where the ruins may put the player in the altar's map (one picked at
 * random when there are several) and [outside] where the exit [portal] puts them back.
 */
class RunicAltar(
    val key: String,
    val ruins: Int,
    val ruinsTypes: List<Int>,
    val varbit: Int,
    val altar: Int,
    val portal: Int,
    val talisman: Int,
    val tiara: Int,
    val ruinsTile: Tile,
    val inside: List<Place>,
    val outside: Place,
    val elemental: Boolean,
) {
    /** The ruins' middle tile, which Locate points to. */
    val ruinsCentre: Tile get() = Tile(ruinsTile.x + 1, ruinsTile.z + 1)
}

/** A tile on a level. */
data class Place(val x: Int, val z: Int, val level: Int = 0)

/** The eight ways a talisman pulls, with their words. */
enum class Pull { NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST, NORTH_WEST }

/**
 * Runecraft, step RC1: the mysterious ruins, talismans, tiaras and the altars' exit portals.
 *
 * Sources: the OSRS Wiki's Mysterious ruins, Runic altar, Talisman, Tiara, Elemental talisman, Catalytic talisman and
 * each altar's page (read 2026-11-15) for which item opens which ruins and where the ruins are; the 242 cache's loc,
 * obj and varbit types (Joshua-F/osrs-dumps 2026-11-11-rev242) for every id; the 242 map for the altars, the portals
 * and the tiles beside them.
 *
 * - Using an altar's talisman, the elemental talisman (air, water, earth, fire) or the catalytic talisman (the rest) on
 *   its ruins sends the player inside. The ruins are a multiloc that shows its Enter option when the varbit for that
 *   altar is set, which wearing its tiara, the elemental or catalytic tiara (head) or the Runecraft cape (cape) does;
 *   Enter then sends them in. The server sets the varbits itself whenever what is worn changes ([wornChanged]).
 * - Each altar's exit portal puts the player back beside its ruins.
 * - A talisman's Locate says which of eight ways its ruins lie; the elemental talisman picks the nearest of its four
 *   and can't tell when two are equally near; the catalytic talisman's says nothing interesting happens (Catalytic
 *   talisman).
 * - No quests: Lost City (cosmic), Troll Stronghold (law), Mourning's End Part II (death), Dragon Slayer II (wrath)
 *   and Sins of the Father (blood) count as done. The ruins outside Misthalin are reached by ::tele (README).
 *
 * Inferred: the tile inside each altar (the walkable tile beside its exit portal, toward the altar; for the cosmic and
 * blood altars, beside each of their portals, picked at random) and the tile outside (a walkable tile beside the ruins'
 * south side), read from the 242 map's collision; Locate's eight
 * ways, each a 45-degree slice centred on its compass point. Guessed: the send happens as soon as the player is there (after
 * [arriveDelay]'s tick when they walked) with no animation; a talisman used on the wrong ruins does what any other item would; Enter clicked without a tiara (the
 * client shouldn't show it) does the same. Out: the Hat of the Eye and other Guardians of the Rift tiaras, the Abyss,
 * Entrana's no-weapons rule, the Death Altar's quest route through the Temple of Light.
 */
class Runecraft(private val world: World) {
    fun register() {
        for (altar in ALTARS) {
            for (id in listOf(altar.ruins) + altar.ruinsTypes) {
                world.handlers.onLoc(id, 1) { enterByTiara(altar) }
                for (talisman in talismansFor(altar)) {
                    world.handlers.onObjOnLoc(id, talisman) { enter(altar) }
                }
            }
            world.handlers.onLoc(altar.portal, 1) {
                arriveDelay()
                player.message(world.textFor(player)[RunecraftLines.PORTAL])
                world.teleport(player, altar.outside.x, altar.outside.z, altar.outside.level)
            }
            world.handlers.onObj(altar.talisman, LOCATE_OP) { locate(listOf(altar)) }
        }
        world.handlers.onObj(ELEMENTAL_TALISMAN, LOCATE_OP) { locate(ALTARS.filter { it.elemental }) }
        world.handlers.onObj(CATALYTIC_TALISMAN, LOCATE_OP) { player.message(world.textFor(player)[GameLines.NOTHING_INTERESTING]) }
    }

    private suspend fun ScriptContext.enter(altar: RunicAltar) {
        arriveDelay()
        player.message(world.textFor(player)[RunecraftLines.ENTER])
        val to = altar.inside[if (altar.inside.size == 1) 0 else world.random.nextInt(altar.inside.size)]
        world.teleport(player, to.x, to.z, to.level)
    }

    private suspend fun ScriptContext.enterByTiara(altar: RunicAltar) {
        if (altar.key !in opens(player)) {
            arriveDelay()
            player.message(world.textFor(player)[GameLines.NOTHING_INTERESTING])
            return
        }
        enter(altar)
    }

    private fun ScriptContext.locate(altars: List<RunicAltar>) {
        val text = world.textFor(player)
        val distances = altars.map { it to distanceSquared(player.x, player.z, it.ruinsCentre) }.sortedBy { it.second }
        if (distances.size > 1 && distances[0].second == distances[1].second) return player.message(text[RunecraftLines.LOCATE_UNSURE])
        val target = distances.first().first.ruinsCentre
        player.message(text[PULL_LINES.getValue(pull(target.x - player.x, target.z - player.z))])
    }

    /** Sets each altar's varbit to whether what [player] wears lets them Enter its ruins. */
    fun wornChanged(player: Player) {
        val open = opens(player)
        for (altar in ALTARS) {
            if (player.vars.types.varbit(altar.varbit) == null) continue
            val value = if (altar.key in open) 1 else 0
            if (player.vars.getVarbit(altar.varbit) != value) player.vars.setVarbit(altar.varbit, value)
        }
    }

    companion object {
        const val LOCATE_OP = 4

        const val ELEMENTAL_TALISMAN = 5516 // elemental_talisman
        const val CATALYTIC_TALISMAN = 26798 // catalytic_talisman
        const val ELEMENTAL_TIARA = 26804 // tiara_elemental
        const val CATALYTIC_TIARA = 26801 // tiara_catalytic
        const val RUNECRAFT_CAPE = 9765 // skillcape_runecrafting
        const val RUNECRAFT_CAPE_TRIMMED = 9766 // skillcape_runecrafting_trimmed

        /**
         * The altars reached through ruins, in the order of their ruins multilocs. The ruins' tiles are the 242 map's
         * (the Wiki's Mysterious ruins pins agree); inside and outside are inferred as the class comment says.
         */
        val ALTARS: List<RunicAltar> = listOf(
            altar("air", 34813, 28914, 29090, 607, 34760, 34748, 1438, 5527, 2984, 3291, listOf(Place(2841, 4829)), Place(2985, 3290), true),
            altar("mind", 34814, 29094, 29095, 608, 34761, 34749, 1448, 5529, 2981, 3513, listOf(Place(2793, 4828)), Place(2982, 3512), false),
            altar("water", 34815, 29096, 29097, 609, 34762, 34750, 1444, 5531, 3184, 3164, listOf(Place(2726, 4832)), Place(3185, 3163), true),
            altar("earth", 34816, 29098, 29099, 610, 34763, 34751, 1440, 5535, 3305, 3473, listOf(Place(2655, 4830)), Place(3306, 3472), true),
            altar("fire", 34817, 30371, 30372, 611, 34764, 34752, 1442, 5537, 3312, 3254, listOf(Place(2574, 4849)), Place(3312, 3253), true),
            altar("body", 34818, 30373, 31584, 612, 34765, 34753, 1446, 5533, 3052, 3444, listOf(Place(2521, 4834)), Place(3053, 3443), false),
            // Four portals stand round the Cosmic Altar, one each way; the ruins put the player before one of them.
            altar(
                "cosmic", 34819, 31607, 31725, 613, 34766, 34754, 1454, 5539, 2407, 4376,
                listOf(Place(2142, 4813), Place(2162, 4833), Place(2142, 4853), Place(2122, 4833)), Place(2407, 4375), false,
            ),
            altar("law", 34820, 32489, 32490, 614, 34767, 34755, 1458, 5545, 2857, 3380, listOf(Place(2464, 4821)), Place(2858, 3379), false),
            altar("nature", 34821, 32491, 32492, 615, 34768, 34756, 1462, 5541, 2868, 3018, listOf(Place(2400, 4835)), Place(2869, 3017), false),
            // The Chaos Altar's temple is a maze: the ruins put the player on its top floor, beside a portal there.
            altar("chaos", 34822, 34742, 34743, 616, 34769, 34757, 1452, 5543, 3059, 3590, listOf(Place(2275, 4847, 3)), Place(3060, 3589), false),
            altar("death", 34823, 34744, 34745, 617, 34770, 34758, 1456, 5547, 1859, 4638, listOf(Place(2208, 4830)), Place(1860, 4637), false),
            altar("wrath", 34824, 34746, 34747, 6220, 34772, 34759, 22118, 22121, 2445, 2824, listOf(Place(2335, 4826)), Place(2446, 2823), false),
            // Two portals stand either side of the true Blood Altar.
            altar(
                "blood", 25380, 43476, 43477, 13782, 43479, 43478, 1450, 5549, 3560, 9780,
                listOf(Place(3225, 4832), Place(3239, 4832)), Place(3561, 9779), false,
            ),
        )

        private fun altar(
            key: String, ruins: Int, old: Int, new: Int, varbit: Int, altar: Int, portal: Int, talisman: Int, tiara: Int,
            ruinsX: Int, ruinsZ: Int, inside: List<Place>, outside: Place, elemental: Boolean,
        ) = RunicAltar(key, ruins, listOf(old, new), varbit, altar, portal, talisman, tiara, Tile(ruinsX, ruinsZ), inside, outside, elemental)

        /** The talismans that open [altar]'s ruins: its own and the elemental or catalytic one. */
        fun talismansFor(altar: RunicAltar): List<Int> =
            listOf(altar.talisman, if (altar.elemental) ELEMENTAL_TALISMAN else CATALYTIC_TALISMAN)

        /** The keys of the altars whose ruins [player]'s worn items let them Enter. */
        fun opens(player: Player): Set<String> {
            val head = player.equipment[WearPos.HEAD]?.id
            val cape = player.equipment[WearPos.CAPE]?.id
            if (cape == RUNECRAFT_CAPE || cape == RUNECRAFT_CAPE_TRIMMED) return ALTARS.mapTo(HashSet()) { it.key }
            return when (head) {
                null -> emptySet()
                ELEMENTAL_TIARA -> ALTARS.filter { it.elemental }.mapTo(HashSet()) { it.key }
                CATALYTIC_TIARA -> ALTARS.filter { !it.elemental }.mapTo(HashSet()) { it.key }
                else -> ALTARS.filter { it.tiara == head }.mapTo(HashSet()) { it.key }
            }
        }

        private fun distanceSquared(x: Int, z: Int, tile: Tile): Long {
            val dx = (tile.x - x).toLong()
            val dz = (tile.z - z).toLong()
            return dx * dx + dz * dz
        }

        /**
         * The way a talisman pulls toward something [dx] east and [dz] north of the player: eight 45-degree slices,
         * each centred on its compass point (inferred). Standing on it counts as north (guessed).
         */
        fun pull(dx: Int, dz: Int): Pull {
            if (dx == 0 && dz == 0) return Pull.NORTH
            // Degrees clockwise from north, 0 until 360.
            val bearing = (Math.toDegrees(kotlin.math.atan2(dx.toDouble(), dz.toDouble())) + 360.0) % 360.0
            return Pull.entries[((bearing + 22.5) / 45.0).toInt() % 8]
        }

        val PULL_LINES = mapOf(
            Pull.NORTH to RunecraftLines.LOCATE_NORTH,
            Pull.NORTH_EAST to RunecraftLines.LOCATE_NORTH_EAST,
            Pull.EAST to RunecraftLines.LOCATE_EAST,
            Pull.SOUTH_EAST to RunecraftLines.LOCATE_SOUTH_EAST,
            Pull.SOUTH to RunecraftLines.LOCATE_SOUTH,
            Pull.SOUTH_WEST to RunecraftLines.LOCATE_SOUTH_WEST,
            Pull.WEST to RunecraftLines.LOCATE_WEST,
            Pull.NORTH_WEST to RunecraftLines.LOCATE_NORTH_WEST,
        )
    }
}
