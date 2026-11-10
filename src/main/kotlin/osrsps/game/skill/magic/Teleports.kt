package osrsps.game.skill.magic

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.combat.CombatLines
import osrsps.game.combat.RuneCost
import osrsps.game.combat.Spellbook
import osrsps.game.combat.Spells
import osrsps.game.content.HomeTeleportLines
import osrsps.game.script.ScriptContext

private val logger = KotlinLogging.logger {}

/**
 * Where a teleport lands: a tile picked at random from [areas] (each `minX, minZ, maxX, maxZ`, inclusive) on [level],
 * among those a player can stand on; the first area's centre when none is (a map not loaded).
 */
class Landing(val level: Int, val areas: List<IntArray>) {
    init {
        require(areas.isNotEmpty()) { "no landing area" }
        for (area in areas) require(area.size == 4 && area[0] <= area[2] && area[1] <= area[3]) { "landing area ${area.toList()} isn't minX, minZ, maxX, maxZ" }
    }

    /** Every tile of the areas, west to east then south to north, each once. */
    val tiles: List<Pair<Int, Int>> = areas.flatMap { (minX, minZ, maxX, maxZ) -> (minZ..maxZ).flatMap { z -> (minX..maxX).map { x -> x to z } } }.distinct()

    val centre: Pair<Int, Int> get() = areas[0].let { (minX, minZ, maxX, maxZ) -> (minX + maxX) / 2 to (minZ + maxZ) / 2 }

    /** One tile [canStand] allows, picked by [random] (given the count, it returns an index); [centre] when none is. */
    fun pick(canStand: (Int, Int) -> Boolean, random: (Int) -> Int): Pair<Int, Int> {
        val free = tiles.filter { (x, z) -> canStand(x, z) }
        return if (free.isEmpty()) centre else free[random(free.size)]
    }
}

/**
 * One standard teleport spell: what its cache obj has (the [name], the magic tab [component] it is cast from, the Magic
 * [level] and the [runes], and any [extra] item it takes besides, Ape Atoll's banana), and what the data file adds:
 * its base XP in tenths ([xpTenths]), where it lands ([landing]) and the teleport tablet that goes there ([tablet], -1
 * for none).
 */
class TeleportSpell(
    val obj: Int,
    val name: String,
    val component: Int,
    val level: Int,
    val runes: List<RuneCost>,
    val extra: RuneCost?,
    val xpTenths: Int,
    val landing: Landing,
    val tablet: Int = -1,
)

/**
 * The standard spellbook's teleports to places ([TeleportSpell]), from `skills/magic-teleports.json` and the cache's
 * spell objs; see [parse].
 */
class TeleportData(val spells: List<TeleportSpell>) {
    val byComponent: Map<Int, TeleportSpell> = spells.associateBy { it.component }
    val byTablet: Map<Int, TeleportSpell> = spells.filter { it.tablet >= 0 }.associateBy { it.tablet }

    companion object {
        val NONE = TeleportData(emptyList())

        const val RESOURCE = "/skills/magic-teleports.json"

        /** Spell obj params the 241 and 242 caches have beyond [Spells]'s: an item the spell takes besides runes, and how many. */
        const val PARAM_EXTRA_ITEM = 606
        const val PARAM_EXTRA_COUNT = 607

        fun load(objTypes: ObjTypes): TeleportData {
            val text = TeleportData::class.java.getResource(RESOURCE)?.readText() ?: error("No $RESOURCE in the resources")
            return parse(text, objTypes)
        }

        /**
         * Parses the teleports file:
         *
         * ```
         * { "teleports": [ { "spell": "25_varrock_teleport", "xp": 35, "tablet": "poh_tablet_varrockteleport",
         *                    "level": 0, "land": [[3211, 3422, 3215, 3426]] } ] }
         * ```
         *
         * Spells and tablets are gameval names; the level, runes, extra item and magic tab component come from the
         * spell obj's params, and a spell obj without them fails the file. "tablet" may be left out, "level" is the
         * landing's plane (0 when left out). A spell or tablet the cache doesn't have is skipped, with a warning when
         * the cache has some of the others (a test's handful of objs has none, and isn't warned about).
         */
        fun parse(text: String, objTypes: ObjTypes, fileName: String = RESOURCE): TeleportData {
            val root = Json.parseToJsonElement(text).jsonObject
            val unknown = ArrayList<String>()
            val spells = ArrayList<TeleportSpell>()
            root.required("teleports").jsonArray.forEachIndexed { i, element ->
                try {
                    val entry = element.jsonObject
                    val name = entry.required("spell").jsonPrimitive.content
                    val type = objTypes.find(name)
                    if (type == null) {
                        unknown += name
                        return@forEachIndexed
                    }
                    val tabletName = entry["tablet"]?.jsonPrimitive?.content
                    val tablet = tabletName?.let { objTypes.find(it) }
                    if (tabletName != null && tablet == null) unknown += tabletName
                    val landing = Landing(
                        entry["level"]?.jsonPrimitive?.int ?: 0,
                        entry.required("land").jsonArray.map { area -> area.jsonArray.map { it.jsonPrimitive.int }.toIntArray() },
                    )
                    require(landing.level in 0..3) { "landing level ${landing.level}" }
                    val spell = TeleportSpell(
                        obj = type.id,
                        name = type.stringParam(Spells.PARAM_NAME) ?: throw IllegalArgumentException("$name has no spell name (param ${Spells.PARAM_NAME})"),
                        component = component(type),
                        level = type.intParam(Spells.PARAM_LEVEL, -1).also { require(it in 1..99) { "$name has Magic level $it (param ${Spells.PARAM_LEVEL})" } },
                        runes = runes(type),
                        extra = type.intParam(PARAM_EXTRA_ITEM, -1).takeIf { it >= 0 }?.let { RuneCost(it, type.intParam(PARAM_EXTRA_COUNT, 1).also { n -> require(n >= 1) { "$name takes $n of item $it" } }) },
                        xpTenths = tenths(entry.required("xp").jsonPrimitive.double),
                        landing = landing,
                        tablet = tablet?.id ?: -1,
                    )
                    require(spells.none { it.component == spell.component }) { "component ${spell.component} is listed twice" }
                    require(spell.tablet < 0 || spells.none { it.tablet == spell.tablet }) { "tablet $tabletName is listed twice" }
                    spells += spell
                } catch (e: Exception) {
                    throw IllegalArgumentException("$fileName: teleports entry ${i + 1}: ${e.message}", e)
                }
            }
            if (unknown.isNotEmpty() && spells.isNotEmpty()) logger.warn { "$fileName: skipped items the cache doesn't have: ${unknown.joinToString()}" }
            return TeleportData(spells)
        }

        private fun component(type: ObjType): Int {
            val packed = type.intParam(Spells.PARAM_COMPONENT, -1)
            require(packed ushr 16 == Spells.SPELLBOOK) { "${type.internalName} isn't on the magic tab (param ${Spells.PARAM_COMPONENT} is $packed)" }
            return packed and 0xFFFF
        }

        private fun runes(type: ObjType): List<RuneCost> {
            val runes = Spells.PARAM_RUNES.mapNotNull { (runeParam, countParam) ->
                val rune = type.intParam(runeParam, -1)
                if (rune < 0) null else RuneCost(rune, type.intParam(countParam, 0).also { require(it >= 1) { "rune $rune costs $it" } })
            }
            require(runes.isNotEmpty()) { "${type.internalName} needs no runes (params ${Spells.PARAM_RUNES.first().first} on)" }
            return runes
        }

        private fun tenths(xp: Double): Int {
            val tenths = Math.round(xp * 10).toInt()
            require(tenths > 0 && kotlin.math.abs(tenths - xp * 10) < 1e-6) { "xp $xp isn't a whole number of tenths" }
            return tenths
        }

        private fun JsonObject.required(key: String): JsonElement = get(key) ?: throw IllegalArgumentException("no \"$key\"")
    }
}

/**
 * The standard spellbook's teleports to places, Varrock Teleport to Civitas illa Fortis Teleport ([TeleportData]),
 * cast with op1 (Cast) on their magic tab component, and the teleport tablets that go to the same places, broken with
 * their op1 (Break).
 *
 * Verified on the OSRS Wiki (each spell's page and Magic tablet, read 2026-11-10): the levels, runes and base XP (the
 * levels and runes are the cache's own), that a tablet needs no Magic level and gives no XP, that both are refused
 * above level 20 Wilderness, and the landing areas the spell pages map for Lumbridge, Falador, Ardougne, Trollheim, Ape
 * Atoll, Kourend Castle and Civitas illa Fortis. The quests that unlock Ardougne, Watchtower, Trollheim, Ape Atoll,
 * Kourend Castle and Civitas illa Fortis on live (the cache's param 1189 names them) count as done here (`Quests.kt`).
 *
 * Inferred from the 2004Scape project's teleport script (its scripts follow the 2004 game): the cast plays
 * `human_castteleport` (714) with `teleport_casting` (111) at height 92 and lands 3 ticks later (its `p_delay(2)`, our
 * delay(3)) with the animation stopped; the player is delayed meanwhile, so the cast can't be walked out of and being
 * attacked doesn't stop it; it closes any interface first; the runes and XP are taken as it is cast; and it lands on a
 * random free tile within 2 of the spell's tile, which is the area here for Varrock and Camelot, whose Wiki pages give
 * one tile. Guessed: Watchtower lands on its Wiki tile only (the top floor is small); a tablet plays
 * `poh_smash_magic_tablet` (4069) on the click, then `poh_absorb_tablet_teleport` (4071) with `poh_absorb_tablet_magic`
 * (678) 2 ticks in, as the smash lasts 44 client cycles, and lands 2 ticks after that; the tablet goes as it is broken.
 *
 * Not built here: the Achievement Diary destinations and their Configure options (Varrock's Grand Exchange, Camelot's
 * Seers' Village, Watchtower's Yanille; their tablets' ops 2 to 4), Teleport to House, Tele Other, Teleport Block, the
 * Wilderness level checked at the landing, and the items a teleport breaks on the way (2004Scape's rum and plague sample).
 */
class Teleports(private val world: World, private val data: TeleportData) {
    fun register() {
        for (spell in data.spells) {
            world.handlers.onButton(Spells.SPELLBOOK, spell.component, CAST) { cast(spell) }
            if (spell.tablet >= 0) world.handlers.onObj(spell.tablet, BREAK) { op -> breakTablet(spell, op.sub) }
        }
    }

    private suspend fun ScriptContext.cast(spell: TeleportSpell) {
        // Only the standard book's teleports are built; their buttons are the standard book's own.
        if (Spellbook.of(player) != Spellbook.STANDARD) return
        if (!allowedHere(player)) return
        val text = world.textFor(player)
        if (player.skills.currentLevel(Skill.MAGIC) < spell.level) {
            player.message(text[CombatLines.MAGIC_LEVEL])
            return
        }
        val provided = world.spells.runesFrom(player.equipment[WearPos.WEAPON]?.id)
        val costs = spell.runes.filter { it.rune !in provided } + listOfNotNull(spell.extra)
        val short = costs.firstOrNull { player.inventory.count(it.rune) < it.count }
        if (short != null) {
            player.message(text[CombatLines.NOT_ENOUGH_RUNES, "runes" to plural(short.rune)])
            return
        }
        for (cost in costs) take(player, cost)
        player.skills.addTenths(Skill.MAGIC, spell.xpTenths)
        start()
        player.anim(CAST_ANIM)
        player.spotanim(CAST_SPOTANIM, CAST_HEIGHT)
        delay(CAST_TICKS)
        land(spell)
        player.anim(-1)
    }

    private suspend fun ScriptContext.breakTablet(spell: TeleportSpell, slot: Int) {
        if (player.inventory[slot]?.id != spell.tablet) return
        if (!allowedHere(player)) return
        player.inventory.remove(slot, 1)
        start()
        player.anim(TABLET_SMASH_ANIM)
        delay(TABLET_ABSORB_TICK)
        player.anim(TABLET_ABSORB_ANIM)
        player.spotanim(TABLET_SPOTANIM)
        delay(TABLET_TICKS - TABLET_ABSORB_TICK)
        land(spell)
        player.anim(-1)
    }

    /** Whether [player] may teleport from where they stand, telling them why not: every teleport stops above level 20 Wilderness. */
    private fun allowedHere(player: Player): Boolean {
        if (Wilderness.level(player.x, player.z) <= Wilderness.TELEPORT_LIMIT) return true
        val text = world.textFor(player)
        player.message(text[HomeTeleportLines.WILDERNESS])
        player.message(text[HomeTeleportLines.WILDERNESS_2])
        return false
    }

    /** The teleport stops whatever the player was doing: an open interface, a route, an interaction, a queued activity. */
    private fun ScriptContext.start() {
        world.interfaces.closeModals(player)
        player.scripts.dropWeak()
        player.waypoints.clear()
        player.interaction = null
    }

    private fun ScriptContext.land(spell: TeleportSpell) {
        val landing = spell.landing
        val (x, z) = landing.pick({ x, z -> world.canStand(x, z, landing.level) }) { world.random.nextInt(it) }
        world.teleport(player, x, z, landing.level)
    }

    /** Takes [cost] from the inventory, from the first stacks of the item on. */
    private fun take(player: Player, cost: RuneCost) {
        var left = cost.count
        for ((slot, obj) in player.inventory.contents()) {
            if (left == 0) break
            if (obj.id == cost.rune) left -= player.inventory.remove(slot, left)
        }
    }

    /** The item's name as the not-enough message has it, as [osrsps.game.combat.MagicCombat] does: Law Runes, Bananas. */
    private fun plural(item: Int): String {
        val name = world.objTypes[item]?.name ?: "rune"
        return name.split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } } + "s"
    }

    companion object {
        /** The spells' op1, Cast, and the tablets' iop1, Break. */
        const val CAST = 1
        const val BREAK = 1

        /** `human_castteleport` and `teleport_casting`, at 2004Scape's height. */
        const val CAST_ANIM = 714
        const val CAST_SPOTANIM = 111
        const val CAST_HEIGHT = 92

        /** From the cast to the landing: 2004Scape's `p_delay(2)`. */
        const val CAST_TICKS = 3

        /** `poh_smash_magic_tablet`, then `poh_absorb_tablet_teleport` with `poh_absorb_tablet_magic`. */
        const val TABLET_SMASH_ANIM = 4069
        const val TABLET_ABSORB_ANIM = 4071
        const val TABLET_SPOTANIM = 678

        /** Guessed: the absorb starts 2 ticks after the break, and the player lands 4 ticks after it. */
        const val TABLET_ABSORB_TICK = 2
        const val TABLET_TICKS = 4
    }
}
