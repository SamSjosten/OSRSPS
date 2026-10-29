package osrsps.game.skill

import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptContext

/** Mining's words, in the dialogue file. */
object MiningLines : Lines("mining", "Mining") {
    val SWING = line("swing", "the message on starting to mine")
    val GET_ORE = line("get_ore", "the message for each ore", "ore")
    val GEM = line("gem", "the message on finding a gem instead of ore", "gem")
    val LEVEL = line("level", "the message when the rock needs a higher level", "level")
    val NO_PICKAXE = line("no_pickaxe", "the message with no pickaxe the player's level can use")
    val INVENTORY_FULL = line("inventory_full", "the message when the backpack is full", "ore")
    val EMPTY = line("empty", "the message on Mine on a rock with no ore left")
}

/** A pickaxe: its tool for [SkillTools], its ticks between rolls, and a chance of one tick fewer (dragon, crystal). */
class Pickaxe(val tool: SkillTool, val ticks: Int, val fastNumerator: Int = 0, val fastDenominator: Int = 1)

/** One kind of rock: level, XP in tenths, the ore, its success pair (out of 256) and ticks to come back. */
class RockType(val key: String, val level: Int, val xpTenths: Int, val ore: Int, val low: Int, val high: Int, val respawn: Int)

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
 * them); the swing message and animation on the click's own tick (2004Scape waits a tick). Out: the amulet of glory's
 * gem chance, mining gloves, Varrock armour and other extra-ore items, the prospector kit, rock golems, gem rocks,
 * essence. No sounds: the 241 synth ids have no names.
 */
class Mining(private val world: World) {
    val tools = SkillTools(Skill.MINING, PICKAXES.map { it.tool })
    private val pickaxes = PICKAXES.associateBy { it.tool.obj }

    fun register() {
        for ((locId, rock) in ROCKS) {
            val option = mineOption(locId)
            world.handlers.onLoc(locId, option) { op -> mine(op, rock) }
        }
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
        val ore = world.objTypes[rock.type.ore]
        val oreName = ore?.name?.lowercase() ?: "ore"
        if (player.inventory.freeSlots == 0) {
            player.message(text[MiningLines.INVENTORY_FULL, "ore" to oreName])
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
            if (world.random.chance(GEM_TABLE_CHANCE, GEM_TABLE_DENOMINATOR)) {
                val gem = BirdNests.pick(world.random, GEMS)
                if (gem != NOTHING) {
                    val type = world.objTypes[gem] ?: error("No obj type $gem for a mined gem")
                    player.inventory.add(type, 1)
                    player.message(text[MiningLines.GEM, "gem" to type.name.lowercase()])
                }
            } else if (SuccessRoll.roll(world.random, rock.type.low, rock.type.high, player.skills.currentLevel(Skill.MINING))) {
                ore ?: error("No obj type ${rock.type.ore} for ${rock.type.key} ore")
                world.locs.replace(loc, rock.empty, revertAfter = rock.type.respawn)
                player.inventory.add(ore, 1)
                player.skills.addTenths(Skill.MINING, rock.type.xpTenths)
                player.message(text[MiningLines.GET_ORE, "ore" to oreName])
                return stop()
            }
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
        private val COAL = RockType("coal", 30, 500, 453, 16, 100, 50)
        private val MITHRIL = RockType("mithril", 55, 800, 447, 4, 50, 200)
        private val ADAMANTITE = RockType("adamantite", 70, 950, 449, 2, 25, 400)

        const val ROCKS1 = 11390 // rocks1
        const val ROCKS2 = 11391 // rocks2
        val EMPTY_ROCKS = listOf(ROCKS1, ROCKS2)

        /**
         * Rock locs by gameval (rockN becomes rocksN). Pairs fitted to the Wiki's charts, respawns from its pages in
         * ticks: 1.2 s is 2, 2.4 s 4, 5.4 s 9, 30 s 50, 2 minutes 200, 4 minutes 400.
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
            11372 to Rock(MITHRIL, ROCKS1), // mithrilrock1
            11373 to Rock(MITHRIL, ROCKS2), // mithrilrock2
            11374 to Rock(ADAMANTITE, ROCKS1), // adamantiterock1
            11375 to Rock(ADAMANTITE, ROCKS2), // adamantiterock2
        )

        /** The mining gem table: 128 in 32,768 a roll (1/256), most of it gems. */
        const val GEM_TABLE_CHANCE = 1
        const val GEM_TABLE_DENOMINATOR = 256
        const val NOTHING = -1
        val GEMS = listOf(
            NestEntry(NOTHING, 70),
            NestEntry(1623, 32), // uncut_sapphire
            NestEntry(1621, 16), // uncut_emerald
            NestEntry(1619, 8), // uncut_ruby
            NestEntry(1617, 2), // uncut_diamond
        )
    }
}
