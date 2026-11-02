package osrsps.game.content

import osrsps.cache.LocTypeEncoder
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
    val TITLE = line("title", "The teleport menu's title (RS3 shows a map; ours is a chat menu of the activated stones)")
    val LUMBRIDGE = line("lumbridge", "The Lumbridge stone's name, in the menu and the messages")
    val DRAYNOR = line("draynor", "The Draynor Village stone's name")
    val VARROCK = line("varrock", "The Varrock stone's name")
    val AL_KHARID = line("al_kharid", "The Al Kharid stone's name")
    val EDGEVILLE = line("edgeville", "The Edgeville stone's name")
    val ACTIVATED = line("activated", "Said when the player activates a stone, naming it (the words are RS3's; the Words thread has them)", "name")
    val LOCKED = line("locked", "::lodestone to a stone the player hasn't activated (RS3 greys it out; a message is ours)", "name")
}

/**
 * Lodestones, custom content after RS3's lodestone network: a stone at each major town that a player activates once
 * and can then teleport to, free, from any stone, with Home Teleport or with `::lodestone`. Verified on the RS3 Wiki
 * (Lodestone Network, Home Teleport; read 2026-11-02): Lumbridge's is active from the start and every other stone is
 * activated by clicking it once; Home Teleport opens the network and lands on the stone picked; the teleport takes 17
 * seconds, so [CAST_TICKS]; it can't start in combat, and being attacked stops it; it's refused above level 20
 * Wilderness; there is no cooldown. Sam's picks (2 Nov 2026): RS3's timing, the Wilderness block like other teleports.
 * Not yet: RS3's "Previous destination" on the spell (the spell's right-click has no room for it in OSRS's spellbook,
 * and the chat menu's five options are the five sites), which waits for the map interface (plan step 6).
 *
 * Each site is one loc of our own, a multiloc whose varbit picks what the client draws, so every player sees their own
 * stone: [DORMANT] (model 65000, option Activate) until they activate it, [ACTIVE] (model 65001, option Teleport)
 * after. The varbit is a bit of the lodestones' varp ([CustomVars.lodestonesVarp]), saved with the player; its id
 * follows the loaded cache's, so the site's loc type is built for a world's [CustomVars] ([Site.type]). The server
 * sees the one loc ([Site.id]) and the one option on it, and answers by the player's varbit. The cast is Home
 * Teleport's machinery ([HomeTeleport.castTo]) with the stones' own timing and no cooldown.
 *
 * Guessed: the stones' tiles (RS3's spots, by the Wiki's descriptions and the OSRS map, placed where the ground is
 * flat and clear; `::pos` and a change to [SITES] move one), landing on the stone's centre tile, the stages' ticks
 * (Home Teleport's, stretched to 28), and the chat menu in place of RS3's map interface.
 */
object Lodestones {
    /** The child types the client draws: the dormant stone and the active one. */
    const val DORMANT = 65000
    const val ACTIVE = 65001
    const val DORMANT_MODEL = 65000
    const val ACTIVE_MODEL = 65001

    /** The first site's loc id; sites count up from it. */
    const val FIRST_STONE = 65002

    /** Op1 of both looks: Activate on the dormant stone, Teleport on the active one. */
    const val ACTIVATE = 1
    const val TELEPORT = 1

    /** From the click to landing: RS3's 17 seconds, at 0.6 s a tick (28.3, rounded down). */
    const val CAST_TICKS = 28

    /** Home Teleport's five stages stretched over the 28 ticks (its own run 0, 7, 13, 16, 19 of 24). Guessed. */
    val STAGES = listOf(
        HomeTeleport.Stage(0, 4847, 800),
        HomeTeleport.Stage(8, 4850, 801),
        HomeTeleport.Stage(15, 4853, 802),
        HomeTeleport.Stage(19, 4855, 803),
        HomeTeleport.Stage(22, 4857, 804),
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

    /** The child types: flat 3 by 3 stones with our models, each with the one option its look offers. */
    private fun child(model: Int, op: String) = LocTypeEncoder.Definition(
        "Lodestone", listOf(model), ops = listOf(op, null, null, null, null), width = 3, length = 3, solid = false,
    )

    val DORMANT_TYPE = CustomLocs.Custom(DORMANT, child(DORMANT_MODEL, "Activate"))
    val ACTIVE_TYPE = CustomLocs.Custom(ACTIVE, child(ACTIVE_MODEL, "Teleport"))

    /**
     * The sites, in the menu's order. Lumbridge's stone is outside the castle's east gate on the way to the bridge;
     * Draynor's by the village's north road; Varrock's south of the city's south gate; Al Kharid's north of the
     * palace, by the gate; Edgeville's south of the bank, by the river. Tiles guessed (see the class note).
     */
    val LUMBRIDGE = Site(0, LodestoneLines.LUMBRIDGE, 3232, 3220)
    val DRAYNOR = Site(1, LodestoneLines.DRAYNOR, 3104, 3297)
    val VARROCK = Site(2, LodestoneLines.VARROCK, 3213, 3375)
    val AL_KHARID = Site(3, LodestoneLines.AL_KHARID, 3296, 3191)
    val EDGEVILLE = Site(4, LodestoneLines.EDGEVILLE, 3066, 3504)

    val SITES = listOf(LUMBRIDGE, DRAYNOR, VARROCK, AL_KHARID, EDGEVILLE)

    init {
        require(SITES.mapIndexed { i, site -> site.index == i }.all { it }) { "Sites are numbered in order" }
        require(SITES.map { it.slug }.toSet().size == SITES.size) { "Two sites share a slug" }
    }

    /** The site called [slug] (`::lodestone varrock`), in any case, or null. */
    fun site(slug: String): Site? = SITES.firstOrNull { it.slug.equals(slug, ignoreCase = true) }
}

/**
 * The network's rules for one world: what a click on a stone does, the menu, the teleport, what a new login has, and
 * Home Teleport, whose Cast opens the same menu ([HomeTeleport.target]) and casts with the stones' timing and no
 * cooldown. [vars] holds the world's ids for the sites' varbits.
 * [menu] shows [LodestoneLines.TITLE] over the sites' names and returns the pick from 1, or 0 for none (the world's
 * is a chat menu); [words] gives a line's words for the player, for the names in messages.
 */
class LodestoneNetwork(
    private val vars: CustomVars,
    private val homeTeleport: HomeTeleport,
    private val message: (Player, Line, Array<out Pair<String, Any>>) -> Unit,
    private val words: (Player, Line) -> String,
    private val menu: suspend ScriptContext.(title: Line, options: List<Line>) -> Int,
) {
    fun register(handlers: Handlers) {
        for (site in Lodestones.SITES) handlers.onLoc(site.id, Lodestones.ACTIVATE) { touch(site) }
        homeTeleport.target = { pick()?.let { HomeTeleport.Target(it.arrival, Lodestones.CAST_TICKS, Lodestones.STAGES, cooldown = false) } }
    }

    /** Lumbridge's stone is active from the start (RS3 Wiki): set at every login, so an old save gets it too. */
    fun login(player: Player) = unlock(player, Lodestones.LUMBRIDGE)

    /** The site's varbit in this world: see [CustomVars.lodestoneVarbit]. */
    fun varbit(site: Lodestones.Site): Int = vars.lodestoneVarbit(site)

    fun unlocked(player: Player, site: Lodestones.Site): Boolean = player.vars.getVarbit(varbit(site)) == 1

    fun unlock(player: Player, site: Lodestones.Site) = player.vars.setVarbit(varbit(site), 1)

    /** The sites [player] can teleport to, in the menu's order. */
    fun unlockedSites(player: Player): List<Lodestones.Site> = Lodestones.SITES.filter { unlocked(player, it) }

    /** A click on a stone's one option: Activate while it's dormant for the player, the teleport menu once active. */
    private suspend fun ScriptContext.touch(site: Lodestones.Site) {
        if (unlocked(player, site)) {
            open()
        } else {
            unlock(player, site)
            message(player, LodestoneLines.ACTIVATED, arrayOf("name" to words(player, site.name)))
        }
    }

    /** Shows the activated stones and returns the one picked, or null for none. */
    suspend fun ScriptContext.pick(): Lodestones.Site? {
        val sites = unlockedSites(player)
        // Every login has Lumbridge's; only an admin's ::varp empties the list, and a chat menu needs an option.
        if (sites.isEmpty()) return null
        val picked = menu(LodestoneLines.TITLE, sites.map { it.name })
        return sites.getOrNull(picked - 1)
    }

    /** Shows the activated stones and starts the teleport to the one picked. */
    suspend fun ScriptContext.open() {
        pick()?.let { teleportTo(it) }
    }

    /** Starts the teleport to [site], or says why not. Returns whether it started. */
    suspend fun ScriptContext.teleportTo(site: Lodestones.Site): Boolean {
        if (!unlocked(player, site)) {
            message(player, LodestoneLines.LOCKED, arrayOf("name" to words(player, site.name)))
            return false
        }
        return with(homeTeleport) { castTo(site.arrival, Lodestones.CAST_TICKS, Lodestones.STAGES, cooldown = false) }
    }
}

/** A world whose loc types don't include the lodestones (a test's world) gets no stones: the server's always do. */
fun registerLodestones(world: World) {
    for (site in Lodestones.SITES) if (world.locs.types[site.id] != null) world.locs.add(site.loc)
    world.lodestones.register(world.handlers)
}
