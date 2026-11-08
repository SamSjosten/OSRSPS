package osrsps.game.content

import osrsps.cache.LocTypeEncoder
import osrsps.cache.MapElementCodec
import osrsps.game.FrameSlot
import osrsps.game.Interfaces
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.script.Handlers
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc
import osrsps.map.LocShape

/** The lodestone network's words: the teleport menu and what a stone says. RS3's, so the Words thread finds them on its Wiki. */
object LodestoneLines : Lines("lodestone", "Lodestone_Network") {
    val TITLE = line("title", "The lodestone map's title (RS3's teleport interface)")
    val LUMBRIDGE = line("lumbridge", "The Lumbridge stone's name, in the menu and the messages")
    val DRAYNOR = line("draynor", "The Draynor Village stone's name")
    val VARROCK = line("varrock", "The Varrock stone's name")
    val AL_KHARID = line("al_kharid", "The Al Kharid stone's name")
    val EDGEVILLE = line("edgeville", "The Edgeville stone's name")
    val ACTIVATED = line("activated", "Said when the player activates a stone, naming it (the words are RS3's; the Words thread has them)", "name")
    val LOCKED = line("locked", "::lodestone to a stone the player hasn't activated (RS3 greys it out; a message is ours)", "name")
    val PREVIOUS = line("previous", "The map's Previous destination button, naming the stone last teleported to (RS3's option on the spell)", "name")
}

/**
 * Lodestones, custom content after RS3's lodestone network: a stone at each major town that a player activates once
 * and can then teleport to, free, from any stone, with Home Teleport or with `::lodestone`. Verified on the RS3 Wiki
 * (Lodestone Network, Home Teleport; read 2026-11-02): Lumbridge's is active from the start and every other stone is
 * activated by clicking it once; Home Teleport opens the network and lands on the stone picked; the teleport takes 17
 * seconds, so [CAST_TICKS]; it can't start in combat, and being attacked stops it; it's refused above level 20
 * Wilderness; there is no cooldown. Sam's picks (2 Nov 2026): RS3's timing, the Wilderness block like other teleports.
 * RS3's "Previous destination" (an option on the spell there; the spell's right-click has no room for it in OSRS's
 * spellbook) is a button on the map ([LodestoneMap.PREVIOUS]), the stone last teleported to.
 *
 * Each site is one loc of our own, a multiloc whose varbit picks what the client draws, so every player sees their own
 * stone: [DORMANT] (model 65000, option Activate) until they activate it, [ACTIVE] (model 65001, option Teleport)
 * after. The varbit is a bit of the lodestones' varp ([CustomVars.lodestonesVarp]), saved with the player; its id
 * follows the loaded cache's, so the site's loc type is built for a world's [CustomVars] ([Site.type]). The server
 * sees the one loc ([Site.id]) and the one option on it, and answers by the player's varbit. The cast is Home
 * Teleport's machinery ([HomeTeleport.castTo]) with the stones' own timing, no cooldown and an animation of our own
 * ([STAGES], [CustomAnims]).
 *
 * Guessed: the stones' tiles (RS3's spots, by the Wiki's descriptions, moved to where the 241 map's ground is flat,
 * outdoors and clear, the tile around included: RS3's Al Kharid spot is inside a house here, Edgeville's in the river,
 * Draynor's under an oak; `::pos` and a change to [SITES] move one), landing on the stone's centre tile, the cast's
 * pace and colour ([CustomAnims]), and the map's look ([LodestoneMap]).
 */
object Lodestones {
    /** The child types the client draws: the dormant stone and the active one. */
    const val DORMANT = 65000
    const val ACTIVE = 65001
    const val DORMANT_MODEL = 65000
    const val ACTIVE_MODEL = 65001

    /** The first site's loc id; sites count up from it. */
    const val FIRST_STONE = 65002

    /**
     * The map element (config archive 35, above the cache's own, which end at 1,267 in the 241 cache) both looks
     * name, so the minimap draws a stone's icon ([LodestoneMap.MINIMAP_SPRITE]) where it stands. The world map's
     * icons are the world map index's, built with the map, so none there (step 7's second half, if wanted).
     */
    const val MAP_ELEMENT = 65000
    val MAP_ELEMENT_TYPE = MapElementCodec.Definition(sprite = LodestoneMap.MINIMAP_SPRITE)

    /** Op1 of both looks: Activate on the dormant stone, Teleport on the active one. */
    const val ACTIVATE = 1
    const val TELEPORT = 1

    /** From the click to landing: RS3's 17 seconds, at 0.6 s a tick (28.3, rounded down). */
    const val CAST_TICKS = 28

    /**
     * The lodestones' varp holds the stone last teleported to as well, for Previous destination: its site index plus
     * one (0 for none) in these bits, above the sites' own bits ([CustomVars.lodestonePreviousVarbit]).
     */
    const val PREVIOUS_LOW_BIT = 8
    const val PREVIOUS_HIGH_BIT = 11

    /** The rise starts this many ticks before the landing: Tele Other's 16 frames at 4 cycles are 1.3 s, two ticks and a bit. */
    const val RISE_TICKS = 3

    /**
     * The cast's two stages (step 8, after RS3's player who stands briefly and then flies up as a white pulse): from
     * the click, the arms-raised pose while the white pulse builds and stays; [RISE_TICKS] before the landing, Tele
     * Other's rise with the whole swirl, so the player flies up and is gone. All ours, served by the overlay
     * ([CustomAnims]).
     */
    val STAGES = listOf(
        HomeTeleport.Stage(0, CustomAnims.LODESTONE_CHARGE, CustomAnims.LODESTONE_PULSE),
        HomeTeleport.Stage(CAST_TICKS - RISE_TICKS, CustomAnims.LODESTONE_RISE, CustomAnims.LODESTONE_FLASH),
    )

    /**
     * One site: its stone's south-west tile ([x], [z]; the stone is 3 by 3), its loc and its name. Its varbit, 1 once
     * the player has activated the stone, is bit [index] of the lodestones' varp: [CustomVars.lodestoneVarbit].
     */
    class Site(val index: Int, val name: Line, val x: Int, val z: Int) {
        /** The loc the server places and the client's clicks name. */
        val id: Int = FIRST_STONE + index

        /** `::lodestone <slug>`: the name's key without its prefix (`lumbridge`, `al_kharid`). */
        val slug: String = name.key.substringAfter('.')

        /** Where the teleport lands: the stone's centre tile. */
        val arrival = Destination(x + 1, z + 1, 0)

        val loc = ZoneLoc(id, LocShape.CENTREPIECE_STRAIGHT, 0, x, z, 0)

        /** The loc type: a flat 3 by 3 "Lodestone" whose look [varbit] (the site's in a world's [CustomVars]) picks: dormant, then active. */
        fun type(varbit: Int) = CustomLocs.Custom(
            id,
            LocTypeEncoder.Definition(
                "Lodestone", listOf(DORMANT_MODEL), ops = listOf("Activate", null, null, null, null), width = 3, length = 3, solid = false,
                transform = LocTypeEncoder.Transform(varbit, listOf(DORMANT, ACTIVE)),
            ),
        )
    }

    /**
     * The child types: flat 3 by 3 stones with our models, each with the one option its look offers and the minimap's
     * icon (the icon rides on the look the client draws, as the cache's own multilocs carry it, not on the site's loc).
     */
    private fun child(model: Int, op: String) = LocTypeEncoder.Definition(
        "Lodestone", listOf(model), ops = listOf(op, null, null, null, null), width = 3, length = 3, solid = false,
        mapElement = MAP_ELEMENT,
    )

    val DORMANT_TYPE = CustomLocs.Custom(DORMANT, child(DORMANT_MODEL, "Activate"))
    val ACTIVE_TYPE = CustomLocs.Custom(ACTIVE, child(ACTIVE_MODEL, "Teleport"))

    /**
     * The sites, in the menu's order. Lumbridge's stone is outside the castle's east gate on the way to the bridge;
     * Draynor's on the village's north road, west of the oak at the signpost; Varrock's south of the city's south
     * gate; Al Kharid's north of the palace's north door, between the well and the cactus; Edgeville's west of the
     * bank, on the river's east bank below the general store. Tiles guessed (see the class note); each 3 by 3 and
     * the ring of tiles around it are open ground on the 241 map (Lumbridge's west side stands against the castle
     * wall), so a player can walk onto every stone.
     */
    val LUMBRIDGE = Site(0, LodestoneLines.LUMBRIDGE, 3232, 3220)
    val DRAYNOR = Site(1, LodestoneLines.DRAYNOR, 3098, 3298)
    val VARROCK = Site(2, LodestoneLines.VARROCK, 3213, 3375)
    val AL_KHARID = Site(3, LodestoneLines.AL_KHARID, 3295, 3185)
    val EDGEVILLE = Site(4, LodestoneLines.EDGEVILLE, 3072, 3504)

    val SITES = listOf(LUMBRIDGE, DRAYNOR, VARROCK, AL_KHARID, EDGEVILLE)

    init {
        require(SITES.mapIndexed { i, site -> site.index == i }.all { it }) { "Sites are numbered in order" }
        require(SITES.map { it.slug }.toSet().size == SITES.size) { "Two sites share a slug" }
        require(SITES.size <= PREVIOUS_LOW_BIT && SITES.size < 1 shl (PREVIOUS_HIGH_BIT - PREVIOUS_LOW_BIT + 1)) { "${SITES.size} sites don't fit the varp's bits" }
    }

    /** The site called [slug] (`::lodestone varrock`), in any case, or null. */
    fun site(slug: String): Site? = SITES.firstOrNull { it.slug.equals(slug, ignoreCase = true) }
}

/**
 * The network's rules for one world: what a click on a stone does, the map ([LodestoneMap]), the teleport, what a new
 * login has, and Home Teleport, whose Cast opens the same map ([HomeTeleport.target]) and casts with the stones'
 * timing and no cooldown. [vars] holds the world's ids for the sites' varbits; [interfaces] opens the map and sets what
 * it shows; [words] gives a line's words for the player, for the names on the map and in messages.
 */
class LodestoneNetwork(
    private val vars: CustomVars,
    private val homeTeleport: HomeTeleport,
    private val interfaces: Interfaces,
    private val message: (Player, Line, Array<out Pair<String, Any>>) -> Unit,
    private val words: (Player, Line, Array<out Pair<String, Any>>) -> String,
) {
    fun register(handlers: Handlers) {
        for (site in Lodestones.SITES) {
            handlers.onLoc(site.id, Lodestones.ACTIVATE) { touch(site) }
            handlers.onButton(LodestoneMap.INTERFACE, LodestoneMap.iconButton(site), LodestoneMap.TELEPORT) { pick(site) }
            handlers.onButton(LodestoneMap.INTERFACE, LodestoneMap.row(site), LodestoneMap.TELEPORT) { pick(site) }
        }
        handlers.onButton(LodestoneMap.INTERFACE, LodestoneMap.PREVIOUS, LodestoneMap.TELEPORT) { previous(player)?.let { pick(it) } }
        handlers.onButton(LodestoneMap.INTERFACE, LodestoneMap.CLOSE) { if (isOpen(player)) interfaces.closeModals(player) }
        // Combat and the Wilderness are refused before this is asked, so the map opens only where a cast could start.
        homeTeleport.target = {
            open(player)
            null
        }
    }

    /** Lumbridge's stone is active from the start (RS3 Wiki): set at every login, so an old save gets it too. */
    fun login(player: Player) = unlock(player, Lodestones.LUMBRIDGE)

    /** The site's varbit in this world: see [CustomVars.lodestoneVarbit]. */
    fun varbit(site: Lodestones.Site): Int = vars.lodestoneVarbit(site)

    fun unlocked(player: Player, site: Lodestones.Site): Boolean = player.vars.getVarbit(varbit(site)) == 1

    fun unlock(player: Player, site: Lodestones.Site) = player.vars.setVarbit(varbit(site), 1)

    /** The sites [player] can teleport to, in the list's order. */
    fun unlockedSites(player: Player): List<Lodestones.Site> = Lodestones.SITES.filter { unlocked(player, it) }

    /** The stone [player] last teleported to, or null: Previous destination. */
    fun previous(player: Player): Lodestones.Site? = Lodestones.SITES.getOrNull(player.vars.getVarbit(vars.lodestonePreviousVarbit) - 1)

    /** Whether the map is the player's open modal. */
    fun isOpen(player: Player): Boolean = player.modals[FrameSlot.MAINMODAL] == LodestoneMap.INTERFACE

    /** A click on a stone's one option: Activate while it's dormant for the player, the map once active. */
    private fun ScriptContext.touch(site: Lodestones.Site) {
        if (unlocked(player, site)) {
            open(player)
        } else {
            unlock(player, site)
            message(player, LodestoneLines.ACTIVATED, arrayOf("name" to words(player, site.name, emptyArray())))
        }
    }

    /**
     * Opens the map and sets what it shows for [player]: the title, each stone's name (grey while locked) and lit or
     * grey icon, Previous destination when there is one, and the events that let the client send a click on the
     * activated stones' icons and rows, on Previous destination and on the close button (nothing else is clickable,
     * so a locked stone offers no option, as RS3's greyed one). The client keeps a component's text and hiding
     * between opens, so every open sets all of it.
     */
    fun open(player: Player) {
        interfaces.openModal(player, FrameSlot.MAINMODAL, LodestoneMap.INTERFACE)
        interfaces.setText(player, LodestoneMap.INTERFACE, LodestoneMap.TITLE, words(player, LodestoneLines.TITLE, emptyArray()))
        for (site in Lodestones.SITES) {
            val active = unlocked(player, site)
            interfaces.setHidden(player, LodestoneMap.INTERFACE, LodestoneMap.activeIcon(site), !active)
            interfaces.setHidden(player, LodestoneMap.INTERFACE, LodestoneMap.dormantIcon(site), active)
            val name = words(player, site.name, emptyArray())
            interfaces.setText(player, LodestoneMap.INTERFACE, LodestoneMap.rowText(site), if (active) name else "<col=${LodestoneMap.LOCKED_COLOUR}>$name</col>")
            if (active) {
                enable(player, LodestoneMap.iconButton(site))
                enable(player, LodestoneMap.row(site))
            }
        }
        val previous = previous(player)?.takeIf { unlocked(player, it) }
        interfaces.setHidden(player, LodestoneMap.INTERFACE, LodestoneMap.PREVIOUS, previous == null)
        if (previous != null) {
            val name = words(player, previous.name, emptyArray())
            interfaces.setText(player, LodestoneMap.INTERFACE, LodestoneMap.PREVIOUS_TEXT, words(player, LodestoneLines.PREVIOUS, arrayOf("name" to name)))
            enable(player, LodestoneMap.PREVIOUS)
        }
        enable(player, LodestoneMap.CLOSE)
    }

    private fun enable(player: Player, component: Int) = interfaces.setEvents(player, LodestoneMap.INTERFACE, component, -1, -1, Interfaces.IF_EVENT_OP1)

    /** A click on the map's Teleport: closes it and starts the teleport. One without the map open (a stale click) does nothing. */
    private fun ScriptContext.pick(site: Lodestones.Site) {
        if (!isOpen(player)) return
        interfaces.closeModals(player)
        teleportTo(site)
    }

    /** Starts the teleport to [site], or says why not. Returns whether it started; a start makes [site] the previous destination. */
    fun ScriptContext.teleportTo(site: Lodestones.Site): Boolean {
        if (!unlocked(player, site)) {
            message(player, LodestoneLines.LOCKED, arrayOf("name" to words(player, site.name, emptyArray())))
            return false
        }
        val started = with(homeTeleport) { castTo(site.arrival, Lodestones.CAST_TICKS, Lodestones.STAGES, cooldown = false) }
        if (started) player.vars.setVarbit(vars.lodestonePreviousVarbit, site.index + 1)
        return started
    }
}

/**
 * A world whose loc types don't include the lodestones (a test's world) gets no stones: the server's always do, for
 * good, so `::resetlocs` over a stone's tile (the nightly's around the courtyard) keeps it.
 */
fun registerLodestones(world: World) {
    for (site in Lodestones.SITES) if (world.locs.types[site.id] != null) world.locs.addPermanent(site.loc)
    world.lodestones.register(world.handlers)
}
