package osrsps.game.skill.hunter

import osrsps.game.Player
import osrsps.game.WearPos
import osrsps.map.Tile

/**
 * A deadfall: Set-trap on a [boulder] props it up ([setting] while the player lays it, then [set]); a kebbit reaching
 * either long side of it is caught under it (the creature's [Creature.catching] from the south or west side, its
 * [mirrored] loc from the north or east, then [Creature.full]), or the boulder falls on nothing ([failing]) and the
 * trap is over. The trap is the boulder itself, so it stands on its tiles and catches whoever stands near.
 */
class DeadfallKind(
    val boulder: Int,
    val setting: Int,
    set: Int,
    failing: Int,
    creatures: List<Creature>,
    /** Each creature's struggling loc from the north or east side, by NPC type. */
    val mirrored: Map<Int, Int>,
) : TrapKind("deadfall", -1, set, failing, -1, Deadfalls.LAY_ANIM, creatures, HunterLines.INVESTIGATE_DEADFALL) {
    override val laidFromItem: Boolean get() = false
    override val brokenLocs: List<Int> get() = emptyList()
    override val onAnchor: Boolean get() = true
    override val returnsItem: Boolean get() = false
    override val endsOnMiss: Boolean get() = true
    override val playerBlocks: Boolean get() = false
    override val firstLook: Long get() = Deadfalls.FIRST_LOOK
    override val maxLaid: Int get() = Deadfalls.MAX_LAID

    override fun owns(locId: Int): Boolean = super.owns(locId) || locId in mirrored.values

    override fun look(trap: Trap, state: TrapState, locId: Int): TrapLook {
        val boulder = trap.anchor ?: error("A deadfall is set on a boulder")
        return TrapLook(listOf(boulder.copy(id = locId)))
    }

    /** The tiles along both long sides of the 2 by 1 boulder: south and north of it, or west and east when it is turned. */
    override fun catchTiles(trap: Trap): List<Tile> {
        val (near, far) = sides(trap)
        return near + far
    }

    override fun catchingLoc(trap: Trap, creature: Creature, npcX: Int, npcZ: Int): Int {
        val far = sides(trap).second
        return if (far.any { it.x == npcX && it.z == npcZ }) mirrored[creature.npc] ?: creature.catching else creature.catching
    }

    /** The south (or west) side's two tiles, then the north (or east) side's. */
    private fun sides(trap: Trap): Pair<List<Tile>, List<Tile>> {
        val x = trap.x
        val z = trap.z
        return if (trap.rotation % 2 == 0) {
            listOf(Tile(x, z - 1), Tile(x + 1, z - 1)) to listOf(Tile(x, z + 1), Tile(x + 1, z + 1))
        } else {
            listOf(Tile(x - 1, z), Tile(x - 1, z + 1)) to listOf(Tile(x + 1, z), Tile(x + 1, z + 1))
        }
    }
}

/**
 * Deadfall trapping (Hunter step 4): a boulder propped up with logs near wild, barb-tailed, prickly and sabre-toothed
 * kebbits.
 *
 * Sources: the OSRS Wiki's Deadfall page (read 2026-11-15) for Set-trap on a boulder with a knife or fletching knife and
 * any logs but redwood and arctic pine, the log kept three times in four, two deadfalls at most, and that standing by
 * one doesn't stop it; its creature table for the levels and XP; the 19 August 2026 update (Summer Sweep Up) for the
 * first catch 8 ticks after laying, the two-trap limit and the log kept; each kebbit's page for its drops and its catch
 * pair (barb-tailed -220 to 1037, sabre-toothed -434 to 820) and where it lives. Ids and sizes from the rev 242 cache:
 * hunting_deadfall_boulder, _setting, _trap, _trapping_<creature> and _m, _full_<creature>, _failing, all 2 by 1.
 * RuneLite's Hunter plugin for the miss: the boulder comes back as it was.
 *
 * Guessed: the wild and prickly kebbits' catch pairs are their pages' figures from before 19 August 2026 (29 to 385 and
 * -70 to 331), as the update raised them by an amount not yet given; a kebbit is caught on reaching either long side
 * (the _m loc for the north or east side); laying takes 4 ticks with human_laytrap; Dismantle gives nothing back. Left
 * out: pyre foxes (Varlamore), maniacal monkeys (Monkey Madness II's lab), baiting and smoking, the tufts (Hunters'
 * Rumours).
 */
object Deadfalls {
    const val BOULDER = 19215 // hunting_deadfall_boulder
    const val SETTING = 19216 // hunting_deadfall_setting
    const val SET = 19217 // hunting_deadfall_trap
    const val FAILING = 20652 // hunting_deadfall_failing

    /** Guessed: human_laytrap. */
    const val LAY_ANIM = 5208

    /** The Summer Sweep Up: deadfalls begin catching 8 ticks after they are laid. */
    const val FIRST_LOOK = 8L

    /** The Summer Sweep Up: two deadfalls at once. */
    const val MAX_LAID = 2

    const val WILD_KEBBIT = 1349 // huntingbeast_claws
    const val BARB_TAILED_KEBBIT = 1348 // huntingbeast_barbedtail
    const val PRICKLY_KEBBIT = 1346 // huntingbeast_spiky
    const val SABRE_TOOTHED_KEBBIT = 1347 // huntingbeast_sabreteeth

    const val BONES = 526
    const val KEBBIT_CLAWS = 10113 // huntingbeast_claws
    const val BARB_TAIL_HARPOON = 10129 // hunting_barbed_harpoon
    const val RAW_BARB_TAILED_KEBBIT = 29101
    const val KEBBIT_SPIKE = 10105 // huntingbeast_spike
    const val KEBBIT_TEETH = 10109 // huntingbeast_sabreteeth

    const val FLETCHING_KNIFE = 31043

    /** A knife or a fletching knife. */
    val KNIVES = listOf(946, FLETCHING_KNIFE)

    /**
     * A knife in the backpack, or the fletching knife worn in the off-hand: the OSRS Wiki's Fletching knife page says it
     * sets up Hunter traps "including while equipped".
     */
    fun hasKnife(player: Player): Boolean =
        KNIVES.any { player.inventory.count(it) > 0L } || player.equipment[WearPos.SHIELD]?.id == FLETCHING_KNIFE

    /** Every log but redwood and arctic pine: logs, achey, oak, willow, teak, maple, mahogany, yew, magic, camphor, ironwood, rosewood. */
    val LOGS = listOf(1511, 2862, 1521, 1519, 6333, 1517, 6332, 1515, 1513, 32904, 32907, 32910)

    val CREATURES = listOf(
        // _trapping_claw (and _m), _full_claw.
        Creature(WILD_KEBBIT, 23, 1024, 29, 385, 20131, 20651, listOf(CreatureLoot(BONES, 1), CreatureLoot(KEBBIT_CLAWS, 1))),
        // _trapping_barbed, _full_barbed.
        Creature(BARB_TAILED_KEBBIT, 33, 1344, -220, 1037, 20129, 20650, listOf(CreatureLoot(BONES, 1), CreatureLoot(BARB_TAIL_HARPOON, 1), CreatureLoot(RAW_BARB_TAILED_KEBBIT, 1))),
        // _trapping_spike, _full_spike.
        Creature(PRICKLY_KEBBIT, 37, 1472, -70, 331, 19218, 20648, listOf(CreatureLoot(BONES, 1), CreatureLoot(KEBBIT_SPIKE, 1))),
        // _trapping_sabre, _full_sabre.
        Creature(SABRE_TOOTHED_KEBBIT, 51, 1600, -434, 820, 19851, 20649, listOf(CreatureLoot(BONES, 1), CreatureLoot(KEBBIT_TEETH, 1))),
    )

    val KIND = DeadfallKind(
        BOULDER, SETTING, SET, FAILING, CREATURES,
        mapOf(WILD_KEBBIT to 20647, BARB_TAILED_KEBBIT to 20130, PRICKLY_KEBBIT to 19219, SABRE_TOOTHED_KEBBIT to 20128),
    )
}
