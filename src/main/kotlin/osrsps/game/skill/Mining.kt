package osrsps.game.skill

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptContext

/** Mining's words, in the dialogue file. */
object MiningLines : Lines("mining", "Mining") {
    val SWING = line("swing", "the message on starting to mine")
    val GET_ORE = line("get_ore", "the message for each ore; {ore} is the rock's metal or mineral, as live names it", "ore")
    val GEM_SAPPHIRE = line("gem_sapphire", "the message on finding an uncut sapphire instead of ore")
    val GEM_EMERALD = line("gem_emerald", "the message on finding an uncut emerald instead of ore")
    val GEM_RUBY = line("gem_ruby", "the message on finding an uncut ruby instead of ore")
    val GEM_DIAMOND = line("gem_diamond", "the message on finding an uncut diamond instead of ore")
    val LEVEL = line("level", "the message when the rock needs a higher level", "level")
    val NO_PICKAXE = line("no_pickaxe", "the message with no pickaxe the player's level can use")
    val INVENTORY_FULL = line("inventory_full", "the message when the backpack is full", "ore")
    val EMPTY = line("empty", "the message on Mine on a rock with no ore left")
    val ESSENCE = line("essence", "the message for each essence mined at the Rune Essence rock (2004Scape's)")
    val ESSENCE_FULL = line("essence_full", "the message on Mine on the Rune Essence rock with a full backpack (2004Scape's)")
    val GEM_ROCK_OPAL = line("gem_rock_opal", "the message on mining an uncut opal from a gem rock")
    val GEM_ROCK_JADE = line("gem_rock_jade", "the message on mining an uncut jade from a gem rock")
    val GEM_ROCK_RED_TOPAZ = line("gem_rock_red_topaz", "the message on mining an uncut red topaz from a gem rock")
    val GEM_ROCK_SAPPHIRE = line("gem_rock_sapphire", "the message on mining an uncut sapphire from a gem rock")
    val GEM_ROCK_EMERALD = line("gem_rock_emerald", "the message on mining an uncut emerald from a gem rock")
    val GEM_ROCK_RUBY = line("gem_rock_ruby", "the message on mining an uncut ruby from a gem rock")
    val GEM_ROCK_DIAMOND = line("gem_rock_diamond", "the message on mining an uncut diamond from a gem rock")
    val GEM_ROCK_FULL = line("gem_rock_full", "the message on Mine on a gem rock with a full backpack")
}

/** A pickaxe: its tool for [SkillTools], its ticks between rolls, and a chance of one tick fewer (dragon, crystal). */
class Pickaxe(val tool: SkillTool, val ticks: Int, val fastNumerator: Int = 0, val fastDenominator: Int = 1)

/**
 * One kind of rock: level, XP in tenths, the ore, its success pair (out of 256) and ticks to come back. Most rocks empty
 * on their first ore; [minYield] to [maxYield] ores for those that last longer (amethyst). A [gemRock] gives a gem from
 * [Mining.GEM_ROCK_TABLE] in place of [ore] and has no pre-roll at the mining gem table.
 */
class RockType(
    val key: String,
    val level: Int,
    val xpTenths: Int,
    val ore: Int,
    val low: Int,
    val high: Int,
    val respawn: Int,
    val minYield: Int = 1,
    val maxYield: Int = 1,
    val gemRock: Boolean = false,
)

/**
 * Mining: Mine on a rock with ore checks the backpack, level and pickaxe, then rolls every few ticks (the pickaxe's
 * interval; the first roll that many ticks after the click) with the rock's success pair. Each roll first has a 1/256
 * chance at the mining gem table, which gives a gem (or nothing) in place of ore; otherwise a success gives the ore and
 * XP and turns the rock into an empty one until it respawns.
 *
 * Sources: the OSRS Wiki's Mining, pickaxe and rock pages (read 2026-10-29) for levels, XP, intervals, respawn times and
 * the gem table; each rock's pair is fitted to the Wiki's success chart points, which it matches exactly; the flow of a
 * swing (the click starts the clock, the message and animation, the roll when due, re-running the op each tick) is the
 * 2004Scape project's mining script. Guessed: which empty rock each rock becomes (rockN to rocksN, as the gamevals pair
 * them; runite and gem rocks share their models); the swing message and animation on the click's own tick (2004Scape
 * waits a tick). Out: mining gloves, Varrock armour and other extra-ore items, the prospector kit, rock golems,
 * unidentified minerals, the Mining Guild's doors and level 60 (it is reached by ::tele), the visible-boost cap at 99.
 * No sounds: the 241 synth ids have no names.
 *
 * Gathering 2, step G1 (OSRS Wiki, Runite rocks, Amethyst crystals, Gem rocks, Mining Guild and the mining gem table
 * template, read 2026-11-10):
 * - Runite rocks (85, 125 XP, pair 1/18) respawn in 12 minutes, 6 in the Mining Guild and the Wilderness.
 * - Amethyst crystals (92, 240 XP, pair -18/10, Mining Guild only) give 2 to 3 amethysts before they become an empty
 *   wall for 75 seconds; mining goes on between them. Guessed: the count is drawn evenly on the first amethyst and kept
 *   until the crystals empty.
 * - Gem rocks (40, 65 XP, pair 28/70, 84/210 with a charged amulet of glory: Shilo Village and the Lunar Isle mine) give
 *   one gem from their own table and respawn in 99 ticks; they never roll the mining gem table.
 * - A charged amulet of glory adds a second 3/256 roll at the mining gem table when the first misses (1/64.2 overall).
 * - The Mining Guild's members' area gives an invisible +7 to the success roll (not to the level a rock needs) and
 *   halves every rock's respawn but amethyst's (inferred: amethyst grows nowhere else, so its 75 seconds is the
 *   guild's). Inferred: the area's bounds, drawn around every members' rock tile the Wiki lists.
 *
 * The Rune Essence rock (`blankrunestone` 34773, in the rune essence mine) is its own kind: every roll succeeds, at the
 * pickaxe's interval, it never empties and has no gem table, and it gives pure essence from Mining 30 (the world counts
 * as a members world) or rune essence below, for 5 XP each (OSRS Wiki, Rune Essence (rock)); the player keeps mining
 * until the backpack is full. Its two messages are 2004Scape's, unchecked against live.
 */
class Mining(private val world: World) {
    val tools = SkillTools(Skill.MINING, PICKAXES.map { it.tool })
    private val pickaxes = PICKAXES.associateBy { it.tool.obj }

    /** Ores left in each part-mined rock that lasts more than one ore, by tile; at most one entry per such rock. */
    private val yieldsLeft = HashMap<Long, Int>()

    fun register() {
        for ((locId, rock) in ROCKS) {
            val option = mineOption(locId)
            world.handlers.onLoc(locId, option) { op -> mine(op, rock) }
        }
        world.handlers.onLoc(ESSENCE_ROCK, mineOption(ESSENCE_ROCK)) { op -> mineEssence(op) }
        for (empty in EMPTY_ROCKS) {
            val option = mineOption(empty)
            world.handlers.onLoc(empty, option) { player.message(world.textFor(player)[MiningLines.EMPTY]) }
        }
    }

    /** The rock's Mine option, 1 to 5; 1 when the cache doesn't say (tests). */
    private fun mineOption(locId: Int): Int {
        val ops = world.locs.types[locId]?.ops ?: return 1
        val index = ops.indexOfFirst { it == "Mine" }
        return if (index >= 0) index + 1 else 1
    }

    private suspend fun ScriptContext.mine(op: Op, rock: Rock) {
        val loc = op.loc ?: return
        // As for woodcutting: a box opened since the last tick (the level-up an ore gave) ends it.
        if (op.repeat && player.modalOpen) return stop()
        val text = world.textFor(player)
        val type = rock.type
        // Live names the metal or mineral ("some copper"), not the ore item: the rock's key.
        val oreName = type.key
        if (player.inventory.freeSlots == 0) {
            player.message(if (type.gemRock) text[MiningLines.GEM_ROCK_FULL] else text[MiningLines.INVENTORY_FULL, "ore" to oreName])
            return stop()
        }
        if (player.skills.currentLevel(Skill.MINING) < rock.type.level) {
            player.message(text[MiningLines.LEVEL, "level" to rock.type.level])
            return stop()
        }
        val tool = when (val check = tools.best(player)) {
            is ToolCheck.Found -> check.tool
            else -> {
                player.message(text[MiningLines.NO_PICKAXE])
                return stop()
            }
        }
        val pickaxe = pickaxes.getValue(tool.obj)
        if (!op.repeat) {
            player.message(text[MiningLines.SWING])
            player.anim(tool.anim)
        }
        // The click's first roll is a whole interval away; after a roll the next one is (2004Scape). Only drawn when the
        // clock starts again, the only time SkillClock reads it.
        val ticks = if (player.actionDelay < tick) interval(pickaxe) + (if (op.repeat) 0 else 1) else 1
        if (SkillClock.step(player, tick, ticks) == ClockStep.DUE) {
            player.anim(tool.anim)
            val guild = inGuild(loc.x, loc.z, loc.level)
            val level = player.skills.currentLevel(Skill.MINING) + if (guild) GUILD_BOOST else 0
            val glory = wearsChargedGlory(player)
            if (type.gemRock) {
                val low = if (glory) type.low * GLORY_GEM_ROCK_MULTIPLIER else type.low
                val high = if (glory) type.high * GLORY_GEM_ROCK_MULTIPLIER else type.high
                if (SuccessRoll.roll(world.random, low, high, level)) {
                    val gem = BirdNests.pick(world.random, GEM_ROCK_TABLE)
                    val gemType = world.objTypes[gem] ?: error("No obj type $gem for a gem rock's gem")
                    world.locs.replace(loc, rock.empty, revertAfter = respawn(type, loc.x, loc.z, loc.level))
                    player.inventory.add(gemType, 1)
                    player.skills.addTenths(Skill.MINING, type.xpTenths)
                    GEM_ROCK_LINES[gem]?.let { player.message(text[it]) }
                    return stop()
                }
            } else if (gemTableRoll(glory)) {
                val gem = BirdNests.pick(world.random, GEMS)
                if (gem != NOTHING) {
                    val gemType = world.objTypes[gem] ?: error("No obj type $gem for a mined gem")
                    player.inventory.add(gemType, 1)
                    GEM_LINES[gem]?.let { player.message(text[it]) }
                }
            } else if (SuccessRoll.roll(world.random, type.low, type.high, level)) {
                val ore = world.objTypes[type.ore] ?: error("No obj type ${type.ore} for ${type.key} ore")
                val emptied = takeYield(type, loc.x, loc.z, loc.level)
                if (emptied) world.locs.replace(loc, rock.empty, revertAfter = respawn(type, loc.x, loc.z, loc.level))
                player.inventory.add(ore, 1)
                player.skills.addTenths(Skill.MINING, type.xpTenths)
                player.message(text[MiningLines.GET_ORE, "ore" to oreName])
                if (emptied) return stop()
                // Crystals with ore left: the swing goes on (a full backpack ends it on the next run).
            }
        }
        world.repeatOp(player, op)
    }

    /** The mining gem table's roll: 1/256, and with a charged glory a second 3/256 when that misses. */
    private fun gemTableRoll(glory: Boolean): Boolean =
        world.random.chance(GEM_TABLE_CHANCE, GEM_TABLE_DENOMINATOR) ||
            (glory && world.random.chance(GLORY_GEM_TABLE_CHANCE, GEM_TABLE_DENOMINATOR))

    /** Takes one ore from the rock at the tile; true when that was its last and it empties. */
    private fun takeYield(type: RockType, x: Int, z: Int, level: Int): Boolean {
        if (type.maxYield <= 1) return true
        val key = tileKey(x, z, level)
        val left = (yieldsLeft[key] ?: world.random.between(type.minYield, type.maxYield)) - 1
        if (left <= 0) {
            yieldsLeft.remove(key)
            return true
        }
        yieldsLeft[key] = left
        return false
    }

    /** Mine on the Rune Essence rock: an essence each roll until the backpack is full. */
    private suspend fun ScriptContext.mineEssence(op: Op) {
        if (op.loc == null) return
        if (op.repeat && player.modalOpen) return stop()
        val text = world.textFor(player)
        if (player.inventory.freeSlots == 0) {
            player.message(text[MiningLines.ESSENCE_FULL])
            return stop()
        }
        val tool = when (val check = tools.best(player)) {
            is ToolCheck.Found -> check.tool
            else -> {
                player.message(text[MiningLines.NO_PICKAXE])
                return stop()
            }
        }
        val pickaxe = pickaxes.getValue(tool.obj)
        if (!op.repeat) {
            player.message(text[MiningLines.SWING])
            player.anim(tool.anim)
        }
        val ticks = if (player.actionDelay < tick) interval(pickaxe) + (if (op.repeat) 0 else 1) else 1
        if (SkillClock.step(player, tick, ticks) == ClockStep.DUE) {
            player.anim(tool.anim)
            val essence = essenceFor(player.skills.currentLevel(Skill.MINING))
            val type = world.objTypes[essence] ?: error("No obj type $essence for mined essence")
            player.inventory.add(type, 1)
            player.skills.addTenths(Skill.MINING, ESSENCE_XP_TENTHS)
            player.message(text[MiningLines.ESSENCE])
            // A full backpack ends it on the next run, with the message.
        }
        world.repeatOp(player, op)
    }

    /** Ticks until the next roll with [pickaxe]: dragon and crystal sometimes take one fewer. */
    private fun interval(pickaxe: Pickaxe): Int =
        if (pickaxe.fastNumerator > 0 && world.random.chance(pickaxe.fastNumerator, pickaxe.fastDenominator)) pickaxe.ticks - 1
        else pickaxe.ticks

    private fun ScriptContext.stop() {
        player.anim(-1)
    }

    private fun tileKey(x: Int, z: Int, level: Int): Long = (level.toLong() shl 40) or (x.toLong() shl 20) or z.toLong()

    /** A rock loc and the empty rock it becomes. */
    class Rock(val type: RockType, val empty: Int)

    companion object {
        /** Best first. Levels, intervals and the fast chances from the Wiki; animations from the 241 gamevals. */
        val PICKAXES = listOf(
            Pickaxe(SkillTool(23680, 71, 8347), 3, 1, 4), // crystal_pickaxe
            Pickaxe(SkillTool(20014, 61, 7283), 3, 1, 6), // 3a_pickaxe
            Pickaxe(SkillTool(13243, 61, 4482), 3, 1, 6), // infernal_pickaxe
            Pickaxe(SkillTool(11920, 61, 7139), 3, 1, 6), // dragon_pickaxe
            Pickaxe(SkillTool(23276, 41, 8313), 3), // trail_gilded_pickaxe
            Pickaxe(SkillTool(1275, 41, 624), 3), // rune_pickaxe
            Pickaxe(SkillTool(1271, 31, 628), 4), // adamant_pickaxe
            Pickaxe(SkillTool(1273, 21, 629), 5), // mithril_pickaxe
            Pickaxe(SkillTool(12297, 11, 3873), 5), // black_pickaxe
            Pickaxe(SkillTool(1269, 6, 627), 6), // steel_pickaxe
            Pickaxe(SkillTool(1267, 1, 626), 7), // iron_pickaxe
            Pickaxe(SkillTool(1265, 1, 625), 8), // bronze_pickaxe
        )

        private val CLAY = RockType("clay", 1, 50, 434, 128, 400, 2)
        private val COPPER = RockType("copper", 1, 175, 436, 100, 350, 4)
        private val TIN = RockType("tin", 1, 175, 438, 100, 350, 4)
        private val IRON = RockType("iron", 15, 350, 440, 96, 350, 9)
        private val SILVER = RockType("silver", 20, 400, 442, 25, 200, 100)
        private val COAL = RockType("coal", 30, 500, 453, 16, 100, 50)
        private val GOLD = RockType("gold", 40, 650, 444, 7, 75, 100)
        private val MITHRIL = RockType("mithril", 55, 800, 447, 4, 50, 200)
        private val ADAMANTITE = RockType("adamantite", 70, 950, 449, 2, 25, 400)
        private val RUNITE = RockType("runite", 85, 1250, 451, 1, 18, 1200)
        private val AMETHYST = RockType("amethyst", 92, 2400, 21347, -18, 10, 125, minYield = 2, maxYield = 3)
        private val GEM_ROCK = RockType("gem", 40, 650, NOTHING, 28, 70, 99, gemRock = true)

        /** Runite's respawn in the Wilderness (OSRS Wiki, Runite rocks); the Wiki gives no Wilderness time for others. */
        const val RUNITE_WILDERNESS_RESPAWN = 600

        /** The Mining Guild's members' area: the invisible boost and the halved respawns. */
        const val GUILD_BOOST = 7
        private const val GUILD_WEST = 2998
        private const val GUILD_EAST = 3059
        private const val GUILD_SOUTH = 9695
        private const val GUILD_NORTH = 9729

        /** Whether the tile is in the Mining Guild's members' area (inferred bounds; the free area starts at 9732). */
        fun inGuild(x: Int, z: Int, level: Int): Boolean = level == 0 && x in GUILD_WEST..GUILD_EAST && z in GUILD_SOUTH..GUILD_NORTH

        /** Ticks until a rock of [type] mined at the tile comes back. */
        fun respawn(type: RockType, x: Int, z: Int, level: Int): Int = when {
            // Inferred: amethyst grows only in the guild, so the Wiki's 75 seconds is already the guild's time.
            type === AMETHYST -> type.respawn
            inGuild(x, z, level) -> type.respawn / 2
            type === RUNITE && Wilderness.level(x, z) > 0 -> RUNITE_WILDERNESS_RESPAWN
            else -> type.respawn
        }

        /** Every charged amulet of glory, the trimmed and eternal ones too (OSRS Wiki, mining gem table). */
        val CHARGED_GLORIES = setOf(
            1706, 1708, 1710, 1712, 11976, 11978, // amulet_of_glory_1 to _6
            10360, 10358, 10356, 10354, 11966, 11964, // trail_amulet_of_glory_1 to _6
            19707, // amulet_of_glory_inf
        )
        const val GLORY_GEM_TABLE_CHANCE = 3
        const val GLORY_GEM_ROCK_MULTIPLIER = 3

        fun wearsChargedGlory(player: Player): Boolean = player.equipment[WearPos.NECK]?.id in CHARGED_GLORIES

        /** blankrunestone, the rune essence mine's four outcrops. */
        const val ESSENCE_ROCK = 34773
        const val RUNE_ESSENCE = 1436 // blankrune
        const val PURE_ESSENCE = 7936 // blankrune_high
        const val PURE_ESSENCE_LEVEL = 30
        const val ESSENCE_XP_TENTHS = 50

        /** What the essence rock gives at Mining [level] on a members world. */
        fun essenceFor(level: Int): Int = if (level >= PURE_ESSENCE_LEVEL) PURE_ESSENCE else RUNE_ESSENCE

        const val ROCKS1 = 11390 // rocks1
        const val ROCKS2 = 11391 // rocks2
        const val AMETHYST_EMPTY = 11393 // amethystrock_empty, "Empty wall"
        val EMPTY_ROCKS = listOf(ROCKS1, ROCKS2, AMETHYST_EMPTY)

        /**
         * Rock locs by gameval (rockN becomes rocksN). Pairs fitted to the Wiki's charts, respawns from its pages in
         * ticks: 1.2 s is 2, 2.4 s 4, 5.4 s 9, 30 s 50, 1 minute 100, 2 minutes 200, 4 minutes 400. Silver and gold (Al
         * Kharid's mine; Wiki, Silver rocks and Gold rocks, read 2026-10-30) fit their charts' two points exactly.
         */
        val ROCKS: Map<Int, Rock> = mapOf(
            11362 to Rock(CLAY, ROCKS1), // clayrock1
            11363 to Rock(CLAY, ROCKS2), // clayrock2
            10943 to Rock(COPPER, ROCKS1), // copperrock1
            11161 to Rock(COPPER, ROCKS2), // copperrock2
            11360 to Rock(TIN, ROCKS1), // tinrock1
            11361 to Rock(TIN, ROCKS2), // tinrock2
            11364 to Rock(IRON, ROCKS1), // ironrock1
            11365 to Rock(IRON, ROCKS2), // ironrock2
            11366 to Rock(COAL, ROCKS1), // coalrock1
            11367 to Rock(COAL, ROCKS2), // coalrock2
            11368 to Rock(SILVER, ROCKS1), // silverrock1
            11369 to Rock(SILVER, ROCKS2), // silverrock2
            11370 to Rock(GOLD, ROCKS1), // goldrock1
            11371 to Rock(GOLD, ROCKS2), // goldrock2
            11372 to Rock(MITHRIL, ROCKS1), // mithrilrock1
            11373 to Rock(MITHRIL, ROCKS2), // mithrilrock2
            11374 to Rock(ADAMANTITE, ROCKS1), // adamantiterock1
            11375 to Rock(ADAMANTITE, ROCKS2), // adamantiterock2
            11376 to Rock(RUNITE, ROCKS1), // runiterock1
            11377 to Rock(RUNITE, ROCKS2), // runiterock2
            11388 to Rock(AMETHYST, AMETHYST_EMPTY), // amethystrock1
            11389 to Rock(AMETHYST, AMETHYST_EMPTY), // amethystrock2
            11380 to Rock(GEM_ROCK, ROCKS1), // gemrock1
            11381 to Rock(GEM_ROCK, ROCKS2), // gemrock
        )

        /** The mining gem table: 128 in 32,768 a roll (1/256), most of it gems. */
        const val GEM_TABLE_CHANCE = 1
        const val GEM_TABLE_DENOMINATOR = 256
        const val NOTHING = -1
        val GEM_LINES = mapOf(
            1623 to MiningLines.GEM_SAPPHIRE, 1621 to MiningLines.GEM_EMERALD, 1619 to MiningLines.GEM_RUBY, 1617 to MiningLines.GEM_DIAMOND,
        )

        val GEMS = listOf(
            NestEntry(NOTHING, 70),
            NestEntry(1623, 32), // uncut_sapphire
            NestEntry(1621, 16), // uncut_emerald
            NestEntry(1619, 8), // uncut_ruby
            NestEntry(1617, 2), // uncut_diamond
        )

        /** A gem rock's gem, out of 128 (OSRS Wiki, Gem rocks). */
        val GEM_ROCK_TABLE = listOf(
            NestEntry(1625, 60), // uncut_opal
            NestEntry(1627, 30), // uncut_jade
            NestEntry(1629, 15), // uncut_red_topaz
            NestEntry(1623, 9), // uncut_sapphire
            NestEntry(1621, 5), // uncut_emerald
            NestEntry(1619, 5), // uncut_ruby
            NestEntry(1617, 4), // uncut_diamond
        )
        val GEM_ROCK_LINES = mapOf(
            1625 to MiningLines.GEM_ROCK_OPAL, 1627 to MiningLines.GEM_ROCK_JADE, 1629 to MiningLines.GEM_ROCK_RED_TOPAZ,
            1623 to MiningLines.GEM_ROCK_SAPPHIRE, 1621 to MiningLines.GEM_ROCK_EMERALD, 1619 to MiningLines.GEM_ROCK_RUBY,
            1617 to MiningLines.GEM_ROCK_DIAMOND,
        )
    }
}
