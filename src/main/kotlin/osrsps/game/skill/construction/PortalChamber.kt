package osrsps.game.skill.construction

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.Ned
import osrsps.game.content.arriveDelay
import osrsps.game.content.message
import osrsps.game.script.ScriptContext
import osrsps.game.skill.magic.Teleports
import osrsps.game.skill.magic.TeleportSpell
import osrsps.game.zone.ZoneLoc

/**
 * Construction step CON7c (the portal chamber), from the Wiki's Portal chamber, Teak, Mahogany and Marble portal,
 * Teleport focus, Greater teleport focus and Scrying pool pages (read 2026-11-28; notes in the project's
 * construction/wiki-portal-chamber.md).
 *
 * - The room's three portal spaces take a teak, mahogany or marble frame, and its centrepiece space a teleport focus,
 *   greater focus or scrying pool ([FurnitureBuilds]). "The higher tier versions are just cosmetic upgrades": any frame
 *   holds any destination, and the frame's tier only picks its look ([PortalDestinations]).
 * - Direct-portal on the centrepiece ([direct]): which of the three portals, then where. It takes the spell's Magic level
 *   (boosts count) and 100 times its runes, elemental staves not standing in, and gives 5 times one cast's XP. Building
 *   mode isn't needed. A new destination costs its runes again, nothing refunded.
 * - A directed portal's Enter ([enter]) teleports anyone in the house there, as the spell lands; Varrock's lands at the
 *   Grand Exchange while its varbit says so (the cache's multiloc on `varrock_ge_teleport` shows the Grand Exchange
 *   first then).
 * - A removed frame keeps its destination, which comes back on the next frame built there ([PortalDestinations.apply]).
 *
 * Only this server's standard teleports are offered ([osrsps.game.skill.magic.TeleportData]): the Arceuus, Ancient and
 * Lunar ones and the quest places wait on those spellbooks. GUESSED: the owner alone directs; a portal space with no
 * frame can't be directed; the two chat menus (live has its own interface), four places a page; no animation on Enter
 * (the teleport's own); the words. Left out: the multilocs' second option and Toggle (Grand Exchange, Seers' Village,
 * Yanille, for the diaries), the scrying pool's Scry.
 */
class PortalChamber(private val world: World, private val houses: Houses) {
    fun register() {
        for (focus in FOCI) {
            world.handlers.onLoc(focus, DIRECT) { op -> arriveDelay(); direct(this, op.loc ?: return@onLoc) }
        }
        for ((spell, locs) in PortalDestinations.LOCS) {
            for (loc in locs) world.handlers.onLoc(loc, ENTER) { op -> arriveDelay(); enter(this, op.loc ?: return@onLoc, spell) }
        }
    }

    /** Direct-portal on the centrepiece [loc]: a portal, a place, the runes, then the portal shows it. */
    private suspend fun direct(context: ScriptContext, loc: ZoneLoc) {
        val player = context.player
        val house = houses.houseAt(loc.x, loc.z) ?: return
        if (house.owner != player.saveKey) return world.message(player, ConstructionLines.PORTAL_NOT_OWNER)
        val state = player.house ?: return
        val roomX = (loc.x - house.instance.baseX) shr 3
        val roomZ = (loc.z - house.instance.baseZ) shr 3
        val room = state.room(roomX, roomZ, loc.level)?.takeIf { it.type == RoomType.PORTAL_CHAMBER } ?: return
        var portal = 0
        world.dialogues.start(context) {
            portal = choice(ConstructionLines.PORTAL_1, ConstructionLines.PORTAL_2, ConstructionLines.PORTAL_3, title = text(ConstructionLines.PORTAL_WHICH))
        }
        val space = PORTAL_SPACES.getOrNull(portal - 1) ?: return
        if (PortalDestinations.FRAMES.none { it == PortalDestinations.frame(room.built[space] ?: -1) }) return world.message(player, ConstructionLines.PORTAL_NO_FRAME)
        val spell = pickPlace(context) ?: return
        if (state.room(roomX, roomZ, loc.level) !== room || !world.locs.exists(loc) || !houses.stillHere(house, player)) return
        val frame = PortalDestinations.FRAMES.indexOf(PortalDestinations.frame(room.built[space] ?: -1))
        if (frame < 0) return
        if (player.skills.currentLevel(Skill.MAGIC) < spell.level) return world.message(player, ConstructionLines.PORTAL_LEVEL, "level" to spell.level)
        val costs = (spell.runes + listOfNotNull(spell.extra)).map { it.rune to it.count * RUNE_TIMES }
        if (costs.any { (rune, count) -> player.inventory.count(rune) < count }) return world.message(player, ConstructionLines.PORTAL_RUNES)
        for ((rune, count) in costs) Ned.take(player.inventory, rune, count)
        player.skills.addTenths(Skill.MAGIC, spell.xpTenths * XP_TIMES)
        room.built[-space] = spell.obj
        PortalDestinations.apply(room, space)
        world.furniture.redraw(house, room, space)
        world.message(player, ConstructionLines.PORTAL_DIRECTED)
        logger.info { "${player.username} directed portal $portal to ${spell.name}" }
    }

    /** The places, four a page with More at the end: the spell picked, or null. */
    private suspend fun pickPlace(context: ScriptContext): TeleportSpell? {
        val places = world.teleports.data.spells.filter { it.obj in PortalDestinations.LOCS }.sortedBy { it.level }
        var page = 0
        while (true) {
            val shown = places.drop(page * PER_PAGE).take(PER_PAGE)
            val more = places.size > (page + 1) * PER_PAGE || page > 0
            var pick = 0
            world.dialogues.start(context) {
                val names = shown.map { it.name } + if (more) listOf(text(ConstructionLines.PORTAL_MORE)) else emptyList()
                pick = choice(*names.toTypedArray(), title = text(ConstructionLines.PORTAL_WHERE))
            }
            if (pick in 1..shown.size) return shown[pick - 1]
            if (!more || pick == 0) return null
            page = if (places.size > (page + 1) * PER_PAGE) page + 1 else 0
        }
    }

    /** Enter on a directed portal [loc] to [spell]'s place: the teleport, as the spell lands. */
    private suspend fun enter(context: ScriptContext, loc: ZoneLoc, spell: Int) {
        val player = context.player
        if (houses.houseAt(loc.x, loc.z) == null) return
        val teleport = world.teleports.data.spells.firstOrNull { it.obj == spell } ?: return
        if (!world.teleports.allowedHere(player)) return
        val landing = if (spell == Teleports.VARROCK_SPELL && player.vars.types.varbit(Teleports.VARBIT_VARROCK_GE) != null &&
            player.vars.getVarbit(Teleports.VARBIT_VARROCK_GE) == 1) Teleports.GE_LANDING else teleport.landing
        world.teleports.start(context)
        world.teleports.land(context, landing)
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        /** The teleport focus, greater teleport focus and scrying pool (poh_teleport_centrepiece, _grand, poh_scrying_pool), and Direct-portal (op 1). */
        val FOCI = setOf(13640, 13641, 13639)
        const val DIRECT = 1

        /** The portal spaces, portals 1 to 3 (poh_teleroom_1 to _3), and the centrepiece space (poh_teleroom_7). */
        val PORTAL_SPACES = listOf(15406, 15407, 15408)
        const val CENTREPIECE_SPACE = 15409

        /** A directed portal's Enter (op 1; Varrock's, Camelot's and Yanille's first option). */
        const val ENTER = 1

        /** The runes are the spell's times 100, the XP one cast's times 5 (the Portal chamber page). */
        const val RUNE_TIMES = 100
        const val XP_TIMES = 5

        private const val PER_PAGE = 4
    }
}

/**
 * The places a portal goes ([LOCS]): by the teleport spell's obj, the portal it shows in a teak, mahogany and marble
 * frame (the cache's `poh_portal_teak_*`, `_mag_*` and `_marble_*`; Varrock's, Camelot's and Yanille's are multilocs).
 * A room keeps each portal space's place under the space's id negated ([apply]), so a rebuilt frame shows it again.
 */
object PortalDestinations {
    val LOCS: Map<Int, List<Int>> = mapOf(
        3286 to listOf(13615, 13622, 13629), // Varrock
        3289 to listOf(13616, 13623, 13630), // Lumbridge
        3292 to listOf(13617, 13624, 13631), // Falador
        3296 to listOf(13618, 13625, 13632), // Camelot
        21836 to listOf(29345, 29353, 29361), // Kourend Castle
        3301 to listOf(13619, 13626, 13633), // Ardougne
        24612 to listOf(50713, 50714, 50715), // Civitas illa Fortis
        3306 to listOf(13620, 13627, 13634), // Watchtower (Yanille)
        3312 to listOf(60790, 60800, 60810), // Trollheim
        7619 to listOf(29344, 29352, 29360), // Ape Atoll (Marim)
    )

    /** The empty teak, mahogany and marble frames (poh_portal_teak_empty, _mag_empty, _marble_empty). */
    val FRAMES = listOf(13636, 13637, 13638)

    /** Each directed portal's empty frame. */
    private val FRAME_OF: Map<Int, Int> = LOCS.values.flatMap { locs -> locs.mapIndexed { tier, id -> id to FRAMES[tier] } }.toMap()

    /** Every directed portal. */
    val DIRECTED: Set<Int> get() = FRAME_OF.keys

    /** The frame [loc] is, directed or not: the loc itself for anything else. */
    fun frame(loc: Int): Int = FRAME_OF[loc] ?: loc

    /** Whether [value] may be kept under portal space [space] negated: a place's spell on one of the three spaces. */
    fun remembers(space: Int, value: Int): Boolean = space in PortalChamber.PORTAL_SPACES && value in LOCS

    /** Shows [room]'s portal [space] directed to its kept place, if a frame stands there. */
    fun apply(room: HouseRoom, space: Int) {
        val spell = room.built[-space] ?: return
        val tier = FRAMES.indexOf(frame(room.built[space] ?: return))
        if (tier < 0) return
        room.built[space] = LOCS[spell]?.get(tier) ?: return
    }
}
