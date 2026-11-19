package osrsps.game.skill

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import osrsps.game.Inventory
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.Op
import osrsps.game.script.ScriptContext
import osrsps.game.zone.ZoneLoc
import osrsps.map.LocShape

/** Shooting Stars' words, in the dialogue file. */
object ShootingStarLines : Lines("shooting_stars", "Transcript:Crashed Star") {
    val DISCOVERED = line("discovered", "When a player is the first to discover a crashed star")
    val PROSPECT = line("prospect", "Prospecting the crashed star; {size} 1 to 9, {level} ten times it, {percent} the layer mined", "size", "level", "percent")
    val NO_PICKAXE = line("no_pickaxe", "Mining the crashed star: no pickaxe, or none the player has the level for")
    val LEVEL = line("level", "Mining the crashed star: below the layer's level", "level")
    val SWING = line("swing", "Mining the crashed star: the swing")
    val BONUS_LEFT = line("bonus_left", "Mining with a discovery bonus: {left} is 250, 200, 150, 100 or 50", "left")
    val BONUS_USED_UP = line("bonus_used_up", "Mining: the last of the discovery bonus (red)")
    val DISINTEGRATES = line("disintegrates", "If the star disappears")
    val INVENTORY_FULL = line("inventory_full", "Not on the Wiki: Mine with a full backpack and no stardust stack to add to")
    val TELESCOPE = line("telescope", "Telescope: the next star's {region} and when it lands, from {from} to {to} (each a minutes or hours_minutes)", "region", "from", "to")
    val TELESCOPE_NOTHING = line("telescope_nothing", "Telescope: no star planned")
    val MINUTES = line("minutes", "Telescope: a time under an hour", "minutes")
    val HOURS_MINUTES = line("hours_minutes", "Telescope: a time of an hour or more", "hours", "minutes")
}

/** The study's three telescopes (Construction) and the minutes of their window (OSRS Wiki, Shooting Stars, Telescopes). */
enum class Telescope(val accuracyMinutes: Int) {
    OAK(24),
    TEAK(9),
    MAHOGANY(2),
}

/** What a telescope shows of the next star: its region, and a window of minutes from now that it lands in. */
class Sighting(val region: StarRegion, val fromMinutes: Int, val toMinutes: Int)

/** One place a star can land: its key, its name on the Wiki and its south-west tile (level 0). */
class StarSite(val key: String, val name: String, val region: StarRegion, val x: Int, val z: Int) {
    override fun toString() = key
}

/** A telescope region ("Misthalin") and its sites. */
class StarRegion(val key: String, val name: String) {
    val sites = mutableListOf<StarSite>()
}

/** shooting-stars.json: every landing site by region, and the one site a repeated roll goes to, if it is in. */
class StarSites(val regions: List<StarRegion>, val fallback: StarSite?) {
    /** Every site the roll picks from: the fallback is left out of it, as live's is. */
    val rolled: List<StarSite> = regions.flatMap { it.sites }.filter { it !== fallback }
    val all: List<StarSite> = regions.flatMap { it.sites }

    operator fun get(key: String): StarSite? = all.firstOrNull { it.key == key.lowercase() }

    companion object {
        const val RESOURCE = "/skills/shooting-stars.json"

        fun load(): StarSites {
            val text = StarSites::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            return parse(text)
        }

        fun parse(text: String): StarSites {
            val root = Json.parseToJsonElement(text).jsonObject
            val regions = root.getValue("regions").jsonArray.map { it.jsonObject }.map { json ->
                val region = StarRegion(json.string("key"), json.string("name"))
                for (site in json.getValue("sites").jsonArray.map { it.jsonObject }) {
                    region.sites += StarSite(site.string("key"), site.string("name"), region, site.int("x"), site.int("z"))
                }
                region
            }
            val keys = regions.flatMap { r -> r.sites.map { it.key } }
            require(keys.size == keys.toSet().size) { "A star site key is listed twice" }
            val fallbackKey = root["fallback"]?.jsonPrimitive?.contentOrNull
            val fallback = fallbackKey?.let { key -> regions.flatMap { it.sites }.firstOrNull { it.key == key } ?: error("No star site $key for the fallback") }
            return StarSites(regions, fallback)
        }

        private fun JsonObject.string(name: String) = getValue(name).jsonPrimitive.content
        private fun JsonObject.int(name: String) = getValue(name).jsonPrimitive.int
    }
}

/**
 * Shooting Stars (OSRS Wiki, Shooting Stars, Crashed Star, Stardust and Transcript:Crashed Star, read 2026-11-19). One
 * star at a time on the world. Each lands at a site picked at random from [StarSites.rolled], never the last one again,
 * as a size 6 to 9 crashed star (25%, 30%, 25% and 20% for 6, 7, 8 and 9). The next lands 90 minutes after, give or take
 * up to 15; the first 20 to 50 minutes after the server starts (the Wiki's note on system updates). A star still there
 * when the next lands is removed.
 *
 * A star's size is its layer: mining a layer needs ten times its size in Mining, and each layer lasts 7 minutes (700
 * ticks) of mining, counted on every tick that at least one player is mining it, however many are (since 20 Sep 2023).
 * Then it drops a size (the next loc's own animation plays the change), and after size 1 it disintegrates. Mining is the
 * pickaxe's interval on the skill clock, as at rocks ([Mining]): each roll first has the mining gem table's chance (with a
 * charged glory's extra roll), then the stardust roll, 73/256 to 117/256 by level (the Wiki's chart, the same at every
 * size), for one stardust and 32 XP. Mining enhancers don't touch it. A new layer's loc ends the swing, so a player
 * clicks again each layer (inferred from the Wiki's "only requiring input every 7 minutes").
 *
 * The first player to Mine or Prospect a new star discovers it: a message, the star_mining_boost jingle, and a bonus of
 * 300 extra stardust (star_bonus_dust, kept in the cache's star_transmit varp so it survives logouts) that gives one more
 * dust per dust mined, without XP, from any star, until used up; a new discovery puts it back to 300. Every interaction
 * sets star_mined, which Dusuri asks about.
 *
 * Guessed: the even spread of the 90 ± 15 and 20 to 50 minutes (the Wiki gives only the ranges); that a site is picked
 * when the star before it lands, so a telescope can show it; with no fallback site in (only part of live's list is),
 * a repeated roll rolls again among the others; the landing loc (star_landing_star, its impact animation about 3.4
 * ticks) shown for [LANDING_TICKS] before the star; a player counting as mining from their click, before the first
 * roll; Prospect's percent rounded down; the full-backpack message (none on the Wiki; stardust stacks, so it only
 * stops a player with no stack and no free slot, or a stack too near the cap for the roll's dust). Inferred: the bonus
 * messages at every 50 left, from the Wiki's list.
 *
 * Telescopes ([scout]): the next star's region and a window holding its landing, 24, 9 or 2 minutes wide for an oak,
 * teak or mahogany telescope, shown in the plain message box. Guessed: where the landing falls in the window (drawn once
 * per star and telescope, so looking again shows the same window, moved on with the clock); minutes rounded down; the
 * no-star text only when no star is planned (the Wiki's "recently fallen" read as before the next is picked); "1 hour"
 * and "1 minute" sharing the plural keys.
 *
 * Out: the falling whistle and crash sounds (the synths have no names), clue geodes (Treasure Trails), rock golems, the
 * noticeboard at the Observatory, the buff bar's counter, the prospector kit's XP, degrading a star below its level
 * (a Wiki note on a check order), star_last_mined_location (its site numbers aren't known) and free-to-play halving.
 */
class ShootingStars(private val world: World, val sites: StarSites = StarSites.load(), private val running: Boolean = false) {
    /** A star on the ground: its site, layer, the loc now there, and the layer's ticks mined. */
    class Star(val site: StarSite, size: Int, val landedTick: Long) {
        var size: Int = size
            internal set
        var loc: ZoneLoc? = null
            internal set
        var landing: Boolean = true
            internal set
        var minedTicks: Int = 0
            internal set
        var discoveredBy: String? = null
            internal set

        /** The tick a player last mined it, and who: the miners told when it disintegrates. */
        internal val miners = HashMap<String, Long>()

        val percent: Int get() = minedTicks * 100 / LAYER_TICKS
    }

    var star: Star? = null
        private set

    /** The tick the next star lands, and where; -1 while nothing is planned (a world with the cycle off). */
    var nextLanding: Long = -1
        private set
    var nextSite: StarSite? = null
        private set
    private var lastSite: StarSite? = null

    fun register() {
        for (id in STAR_LOCS) {
            world.handlers.onLoc(id, MINE) { op -> mine(op) }
            world.handlers.onLoc(id, PROSPECT) { op -> prospect(op) }
        }
    }

    /** Once a tick, in the NPC phase: the cycle, the landing loc and the layers. */
    fun process(tick: Long) {
        if (running && nextLanding < 0) schedule(tick, world.random.between(FIRST_MIN_TICKS, FIRST_MAX_TICKS))
        if (nextLanding in 0..tick) land(nextSite ?: pickSite(), pickSize(), tick)
        val star = star ?: return
        if (star.landing) {
            if (tick - star.landedTick >= LANDING_TICKS) settle(star)
            return
        }
        // Mined last tick: the miners' scripts run after this phase.
        if (star.miners.values.any { it >= tick - 1 }) {
            star.minedTicks++
            if (star.minedTicks >= LAYER_TICKS) nextLayer(star, tick)
        }
    }

    /**
     * Lands a star of [size] at [site] now, removing any star there was, and plans the next one 90 ± 15 minutes on
     * (when the cycle runs, or [next] is given).
     */
    fun land(site: StarSite, size: Int, tick: Long, next: Int? = null): Star {
        require(size in 1..MAX_SIZE) { "No star size $size" }
        clear()
        val landing = ZoneLoc(LANDING, LocShape.CENTREPIECE_STRAIGHT, 0, site.x, site.z, 0)
        world.locs.add(landing)
        pushOff(site)
        val star = Star(site, size, tick).also { it.loc = landing }
        this.star = star
        lastSite = site
        nextSite = null
        when {
            next != null -> schedule(tick, next)
            running -> schedule(tick, world.random.between(CYCLE_MIN_TICKS, CYCLE_MAX_TICKS))
            else -> {
                nextLanding = -1
                windowOffsets.clear()
            }
        }
        return star
    }

    /** Plans the next star [ticks] from now, at a site picked now. */
    fun schedule(tick: Long, ticks: Int) {
        nextLanding = tick + ticks
        nextSite = pickSite()
        windowOffsets.clear()
    }

    /** Per telescope, how many minutes of its window come before the next star's landing; drawn once per star. */
    private val windowOffsets = HashMap<Telescope, Int>()

    /**
     * What [telescope] shows of the next star at [tick]: its region and a window [Telescope.accuracyMinutes] wide that
     * holds its landing, or null when none is planned. The Construction study's telescopes call this; `::star scout`
     * shows it now.
     */
    fun scout(telescope: Telescope, tick: Long): Sighting? {
        val site = nextSite ?: return null
        if (nextLanding < 0) return null
        val minutes = ((nextLanding - tick).coerceAtLeast(0) * 600 / 60_000).toInt()
        val offset = windowOffsets.getOrPut(telescope) { world.random.between(0, telescope.accuracyMinutes) }
        val from = (minutes - offset).coerceAtLeast(0)
        return Sighting(site.region, from, from + telescope.accuracyMinutes)
    }

    /** The plain message box's text for [sighting] (from [scout]). */
    fun sightingText(player: Player, sighting: Sighting?): String {
        val text = world.textFor(player)
        if (sighting == null) return text[ShootingStarLines.TELESCOPE_NOTHING]
        fun time(minutes: Int) = if (minutes < 60) text[ShootingStarLines.MINUTES, "minutes" to minutes]
            else text[ShootingStarLines.HOURS_MINUTES, "hours" to minutes / 60, "minutes" to minutes % 60]
        return text[ShootingStarLines.TELESCOPE, "region" to sighting.region.name, "from" to time(sighting.fromMinutes), "to" to time(sighting.toMinutes)]
    }

    /** Looking through [telescope]: the plain message box with what it shows. */
    suspend fun ScriptContext.lookThrough(telescope: Telescope) {
        val text = sightingText(player, scout(telescope, world.tick))
        world.dialogues.start(this) { mesbox(text) }
    }

    /** Takes the star away, if there is one, with no message. */
    fun clear() {
        val star = star ?: return
        star.loc?.let { if (world.locs.exists(it)) takeAway(it) }
        this.star = null
    }

    /** Ends the star's current layer now, as if it had been mined 7 minutes (tests). */
    fun finishLayer(tick: Long) {
        val star = star ?: return
        if (star.landing) settle(star)
        nextLayer(star, tick)
    }

    /** A site at random, never the last again: the fallback if there is one, otherwise another roll among the rest. */
    private fun pickSite(): StarSite {
        val pool = sites.rolled
        require(pool.isNotEmpty()) { "No star sites" }
        val site = world.random.pick(pool)
        if (site !== lastSite) return site
        sites.fallback?.let { return it }
        val rest = pool.filter { it !== lastSite }
        return if (rest.isEmpty()) site else world.random.pick(rest)
    }

    private fun pickSize(): Int {
        var roll = world.random.nextInt(SIZE_WEIGHTS.values.sum())
        for ((size, weight) in SIZE_WEIGHTS) {
            if (roll < weight) return size
            roll -= weight
        }
        error("Unreachable")
    }

    private fun settle(star: Star) {
        val loc = star.loc ?: return
        star.loc = world.locs.replace(loc, idFor(star.size)) ?: loc
        star.landing = false
    }

    private fun nextLayer(star: Star, tick: Long) {
        star.minedTicks = 0
        star.size--
        val loc = star.loc ?: return
        if (star.size >= 1) {
            star.loc = world.locs.replace(loc, idFor(star.size)) ?: loc
            return
        }
        if (world.locs.exists(loc)) takeAway(loc)
        this.star = null
        for (player in world.onlinePlayers) {
            val last = star.miners[player.saveKey] ?: continue
            if (last >= tick - 1) {
                player.message(world.textFor(player)[ShootingStarLines.DISINTEGRATES])
                player.anim(-1)
            }
        }
    }

    /** Players on the star's tiles are moved off it, to the nearest open tile around it (OSRS Wiki, Crashed Star). */
    /** Takes a star's loc away, putting back whatever the cache has on that tile and layer. */
    private fun takeAway(loc: ZoneLoc) = world.locs.restore(loc.x, loc.z, loc.level, loc.layer)

    /** Whether a star fits on [x], [z] (its south-west tile): ground level, outside instances, open ground with no loc. */
    fun canLand(x: Int, z: Int): Boolean = !world.instances.inDynamicArea(x, z) &&
        (x until x + SIZE).all { tx -> (z until z + SIZE).all { tz -> world.canStand(tx, tz, 0) && world.locs.at(tx, tz, 0).isEmpty() } }

    private fun pushOff(site: StarSite) {
        for (player in world.onlinePlayers) {
            if (player.level != 0 || player.x !in site.x until site.x + SIZE || player.z !in site.z until site.z + SIZE) continue
            val to = ring(site).filter { (x, z) -> world.canStand(x, z, 0) }
                .minByOrNull { (x, z) -> maxOf(kotlin.math.abs(x - player.x), kotlin.math.abs(z - player.z)) } ?: continue
            world.teleport(player, to.first, to.second, 0)
        }
    }

    /** The tiles just outside the star, west and south first. */
    private fun ring(site: StarSite): List<Pair<Int, Int>> = buildList {
        for (x in site.x - 1..site.x + SIZE) {
            for (z in site.z - 1..site.z + SIZE) {
                val inside = x in site.x until site.x + SIZE && z in site.z until site.z + SIZE
                if (!inside) add(x to z)
            }
        }
    }

    /** The star whose loc was clicked, if it is still the star there. */
    private fun starAt(loc: ZoneLoc): Star? = star?.takeIf { !it.landing && it.loc?.let { l -> l.x == loc.x && l.z == loc.z && l.level == loc.level } == true }

    /** The first Mine or Prospect on a new star; every interaction sets star_mined. */
    private fun interact(player: Player, star: Star) {
        setVarbit(player, VARBIT_MINED, 1)
        if (star.discoveredBy != null) return
        star.discoveredBy = player.saveKey
        setVarbit(player, VARBIT_BONUS, DISCOVERY_BONUS)
        player.message(world.textFor(player)[ShootingStarLines.DISCOVERED])
        player.jingle(JINGLE_DISCOVERY)
    }

    private suspend fun ScriptContext.prospect(op: Op) {
        val star = op.loc?.let(::starAt) ?: return
        interact(player, star)
        player.message(world.textFor(player)[ShootingStarLines.PROSPECT, "size" to star.size, "level" to star.size * LEVEL_PER_SIZE, "percent" to star.percent])
    }

    private suspend fun ScriptContext.mine(op: Op) {
        val loc = op.loc ?: return
        if (op.repeat && player.modalOpen) return stop()
        val star = starAt(loc) ?: return stop()
        if (!op.repeat) interact(player, star)
        val text = world.textFor(player)
        val mining = world.mining
        val tool = (mining.tools.best(player) as? ToolCheck.Found)?.tool
        if (tool == null) {
            player.message(text[ShootingStarLines.NO_PICKAXE])
            return stop()
        }
        val level = star.size * LEVEL_PER_SIZE
        if (player.skills.currentLevel(Skill.MINING) < level) {
            player.message(text[ShootingStarLines.LEVEL, "level" to level])
            return stop()
        }
        if (!dustFits(player)) {
            player.message(text[ShootingStarLines.INVENTORY_FULL])
            return stop()
        }
        val pickaxe = mining.pickaxe(tool)
        if (!op.repeat) {
            player.message(text[ShootingStarLines.SWING])
            player.anim(tool.anim)
        }
        star.miners[player.saveKey] = tick
        val ticks = if (player.actionDelay < tick) mining.interval(pickaxe) + (if (op.repeat) 0 else 1) else 1
        if (SkillClock.step(player, tick, ticks) == ClockStep.DUE) {
            player.anim(tool.anim)
            if (mining.gemTableRoll(Mining.wearsChargedGlory(player))) {
                val gem = BirdNests.pick(world.random, Mining.GEMS)
                if (gem != Mining.NOTHING && player.inventory.freeSlots > 0) {
                    val gemType = world.objTypes[gem] ?: error("No obj type $gem for a mined gem")
                    player.inventory.add(gemType, 1)
                    Mining.GEM_LINES[gem]?.let { player.message(text[it]) }
                }
            } else if (SuccessRoll.roll(world.random, LOW, HIGH, player.skills.currentLevel(Skill.MINING) + Mining.celestialBoost(player))) {
                giveDust(player)
            }
        }
        world.repeatOp(player, op)
    }

    /** One stardust and its XP, and one more while the discovery bonus lasts. */
    private fun giveDust(player: Player) {
        val dust = world.objTypes[STARDUST] ?: error("No obj type $STARDUST for stardust")
        val text = world.textFor(player)
        val bonus = varbit(player, VARBIT_BONUS)
        // Nothing, not even the XP or the bonus, unless the whole roll fits.
        if (!dustFits(player)) return
        player.inventory.add(dust, if (bonus > 0) 2 else 1)
        player.skills.addTenths(Skill.MINING, XP_TENTHS)
        if (bonus <= 0) return
        val left = bonus - 1
        setVarbit(player, VARBIT_BONUS, left)
        when {
            left == 0 -> player.message(text[ShootingStarLines.BONUS_USED_UP])
            left % BONUS_MESSAGE_EVERY == 0 -> player.message(text[ShootingStarLines.BONUS_LEFT, "left" to left])
        }
    }

    /** Whether a roll's stardust (two with the bonus) fits: onto the stack without passing the cap, or into a free slot. */
    private fun dustFits(player: Player): Boolean {
        val payout = if (varbit(player, VARBIT_BONUS) > 0) 2 else 1
        val held = player.inventory.count(STARDUST)
        return if (held > 0) held + payout <= Inventory.MAX_STACK else player.inventory.freeSlots > 0
    }

    private fun ScriptContext.stop() {
        player.anim(-1)
    }

    /** The player's star discovery bonus left (0 in a cache without the varbit, as the tests' small ones). */
    fun bonus(player: Player): Int = varbit(player, VARBIT_BONUS)

    fun setBonus(player: Player, value: Int) = setVarbit(player, VARBIT_BONUS, value)

    private fun varbit(player: Player, id: Int): Int = if (player.vars.types.varbit(id) != null) player.vars.getVarbit(id) else 0

    private fun setVarbit(player: Player, id: Int, value: Int) {
        if (player.vars.types.varbit(id) != null) player.vars.setVarbit(id, value)
    }

    companion object {
        const val MINE = 1
        const val PROSPECT = 2

        /** star_landing_star: the impact, shown before the star. */
        const val LANDING = 41019
        const val LANDING_TICKS = 4

        /** star_size_one_star to star_size_nine_star, by size. */
        val SIZE_LOCS = mapOf(1 to 41229, 2 to 41228, 3 to 41227, 4 to 41226, 5 to 41225, 6 to 41224, 7 to 41223, 8 to 41021, 9 to 41020)
        val STAR_LOCS: Collection<Int> = SIZE_LOCS.values

        fun idFor(size: Int): Int = SIZE_LOCS.getValue(size)

        const val MAX_SIZE = 9
        const val SIZE = 2
        const val LEVEL_PER_SIZE = 10

        /** Sizes a star lands at and their weights, out of 100 (OSRS Wiki, since 25 Oct 2023). */
        val SIZE_WEIGHTS = linkedMapOf(6 to 25, 7 to 30, 8 to 25, 9 to 20)

        /** 7 minutes of mining a layer. */
        const val LAYER_TICKS = 700

        /** 90 minutes give or take 15 between landings; 20 to 50 minutes to the first. */
        const val CYCLE_MIN_TICKS = 7_500
        const val CYCLE_MAX_TICKS = 10_500
        const val FIRST_MIN_TICKS = 2_000
        const val FIRST_MAX_TICKS = 5_000

        /** The stardust success pair, out of 256 (the Wiki's chart: 29% at 1 to 46% at 99). */
        const val LOW = 73
        const val HIGH = 117
        const val XP_TENTHS = 320

        const val STARDUST = 25527 // star_dust
        const val DISCOVERY_BONUS = 300
        const val BONUS_MESSAGE_EVERY = 50

        /** star_mined and star_bonus_dust, bits of star_transmit (2924). */
        const val VARBIT_MINED = 11886
        const val VARBIT_BONUS = 15351

        /** star_mining_boost. */
        const val JINGLE_DISCOVERY = 287
    }
}
