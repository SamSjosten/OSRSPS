package osrsps.game.skill.construction

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.InvObj
import osrsps.game.World
import osrsps.game.content.Ned
import osrsps.game.content.arriveDelay
import osrsps.game.content.message
import osrsps.game.script.ScriptContext
import osrsps.game.skill.Telescope
import osrsps.game.zone.ZoneLoc

/**
 * Construction step CON7a (the study): its furniture builds ([FurnitureBuilds]), the telescopes are looked through and
 * the crystal balls change elemental staves, from the Wiki's Study page and its pieces' pages (read 2026-11-27).
 *
 * - Telescopes: Observe shows where and roughly when the next shooting star lands, to within 24, 9 or 2 minutes for
 *   the oak, teak and mahogany one (the telescope pages; [osrsps.game.skill.ShootingStars.lookThrough]).
 * - Crystal ball, elemental sphere and crystal of power: an elemental staff used on one is changed to another element
 *   ([changeStaff]). The crystal ball changes the basic staves, free; the elemental sphere a battlestaff too, for 100
 *   runes of the new element; the crystal of power a mystic staff too, for 1,000. Combination staves aren't elemental
 *   staves here. GUESSED: the element is picked from a menu of the other three; the words.
 * - Globes, orreries and charts are for looks (the Small orrery page: "no practical use ... except for ornamentation").
 *
 * Next (CON7b): the lecterns' magic tablets. Left out: the charts' Study and the S.T.A.S.H. chart's View (no S.T.A.S.H.
 * units yet).
 */
class Study(private val world: World, private val houses: Houses) {
    fun register() {
        for ((loc, telescope) in TELESCOPES) {
            world.handlers.onLoc(loc, OBSERVE) { op ->
                arriveDelay()
                val at = op.loc ?: return@onLoc
                if (houses.houseAt(at.x, at.z) == null) return@onLoc
                with(world.shootingStars) { lookThrough(telescope) }
            }
        }
        for ((loc, tier) in BALLS) {
            for (staff in STAVES.keys) {
                world.handlers.onObjOnLoc(loc, staff) { op -> changeStaff(this, op.loc ?: return@onObjOnLoc, tier, op.sub, op.obj) }
            }
        }
    }

    /** [staff], in pack slot [slot], used on a crystal ball [loc] that changes staves up to [tier]: to another element. */
    private suspend fun changeStaff(context: ScriptContext, loc: ZoneLoc, tier: Int, slot: Int, staff: Int) {
        val player = context.player
        if (houses.houseAt(loc.x, loc.z) == null) return
        val (element, kind) = STAVES[staff] ?: return
        if (kind > tier) return world.message(player, ConstructionLines.CRYSTAL_BALL_STAFF)
        val others = Element.entries.filter { it != element }
        var pick = 0
        world.dialogues.start(context) {
            pick = choice(*others.map { it.line }.toTypedArray(), title = text(ConstructionLines.CRYSTAL_BALL_TITLE))
        }
        val to = others.getOrNull(pick - 1) ?: return
        if (player.inventory[slot]?.id != staff || houses.houseAt(loc.x, loc.z) == null || !world.locs.exists(loc)) return
        val runes = RUNE_COST[kind]
        if (player.inventory.count(to.rune) < runes) return world.message(player, ConstructionLines.CRYSTAL_BALL_RUNES, "runes" to runes)
        if (runes > 0) Ned.take(player.inventory, to.rune, runes)
        player.inventory[slot] = InvObj(to.staves[kind], 1)
        logger.info { "${player.username} changed staff $staff to ${to.staves[kind]} at a crystal ball" }
    }

    /** The four elements: each one's staff, battlestaff and mystic staff, its rune, and its line in the menu. */
    enum class Element(val staves: List<Int>, val rune: Int, val line: osrsps.game.dialogue.Line) {
        AIR(listOf(1381, 1397, 1405), 556, ConstructionLines.CRYSTAL_BALL_AIR),
        WATER(listOf(1383, 1395, 1403), 555, ConstructionLines.CRYSTAL_BALL_WATER),
        EARTH(listOf(1385, 1399, 1407), 557, ConstructionLines.CRYSTAL_BALL_EARTH),
        FIRE(listOf(1387, 1393, 1401), 554, ConstructionLines.CRYSTAL_BALL_FIRE),
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        /** The telescopes (poh_telescope_1 to _3) and their Observe (op 1). */
        val TELESCOPES: Map<Int, Telescope> = mapOf(13656 to Telescope.OAK, 13657 to Telescope.TEAK, 13658 to Telescope.MAHOGANY)
        const val OBSERVE = 1

        /** The crystal ball, elemental sphere and crystal of power (poh_crystalball_1 to _3), and the staves each changes: 0 basic, 1 battle, 2 mystic. */
        val BALLS: Map<Int, Int> = mapOf(13659 to 0, 13660 to 1, 13661 to 2)

        /** The runes of the new element each kind of staff costs (the Elemental sphere and Crystal of power pages). */
        val RUNE_COST = listOf(0, 100, 1000)

        /** Every elemental staff, battlestaff and mystic staff, with its element and kind. */
        val STAVES: Map<Int, Pair<Element, Int>> = Element.entries.flatMap { e -> e.staves.mapIndexed { kind, id -> id to (e to kind) } }.toMap()
    }
}
