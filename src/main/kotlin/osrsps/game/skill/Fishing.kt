package osrsps.game.skill

import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.npc.Npc
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptContext
import osrsps.map.Tile

/** Fishing's words, in the dialogue file. */
object FishingLines : Lines("fishing", "Fishing") {
    val CAST_NET = line("cast_net", "the message on starting to fish with a small net")
    val CAST_LINE = line("cast_line", "the message on starting to fish with a rod")
    val CATCH_SHRIMPS = line("catch_shrimps", "Raw shrimps: the message for each catch")
    val CATCH_ANCHOVIES = line("catch_anchovies", "Raw anchovies: the message for each catch")
    val CATCH_SARDINE = line("catch_sardine", "Raw sardine: the message for each catch")
    val CATCH_HERRING = line("catch_herring", "Raw herring: the message for each catch")
    val CATCH_TROUT = line("catch_trout", "Raw trout: the message for each catch")
    val CATCH_SALMON = line("catch_salmon", "Raw salmon: the message for each catch")
    val CATCH_PIKE = line("catch_pike", "Raw pike: the message for each catch")
    val LEVEL_BAIT = line("level_bait", "the message when bait fishing needs a higher level", "level")
    val LEVEL_LURE = line("level_lure", "the message when lure fishing needs a higher level", "level")
    val NO_NET = line("no_net", "Small fishing net: the message on Net without one")
    val NO_ROD = line("no_rod", "Fishing rod: the message on Bait without one")
    val NO_FLY_ROD = line("no_fly_rod", "Fly fishing rod: the message on Lure without one")
    val NO_BAIT = line("no_bait", "Fishing bait: the message on Bait with none left")
    val NO_FEATHERS = line("no_feathers", "Feather: the message on Lure with none left")
    val INVENTORY_FULL = line("inventory_full", "the message when the backpack is full")
}

/** One fish: the obj caught, the level and XP in tenths, its success pair (out of 256) and its catch message. */
class Fish(val obj: Int, val level: Int, val xpTenths: Int, val low: Int, val high: Int, val line: Line)

/** Bait a method uses up, one per catch, and what the player is told when it runs out. */
class Bait(val obj: Int, val none: Line)

/**
 * One way of fishing a spot: its option ([name] as the cache spells it), the tool, the bait, the animations, the fish
 * (the best first; each roll tries them in turn) and the lowest level that catches anything.
 */
class FishingMethod(
    val name: String,
    val tool: Int,
    val noTool: Line,
    val bait: Bait?,
    val cast: Line,
    val castAnim: Int,
    val rollAnim: Int,
    val fish: List<Fish>,
    val levelLine: Line?,
    /** The Wiki's pike: 1 in 5 rolls recasts, so the next roll is a tick later. */
    val recasts: Boolean = false,
) {
    val level: Int get() = fish.minOf { it.level }
}

/** A kind of fishing spot (an NPC) and the methods its options use. */
class SpotType(val npc: Int, val methods: List<FishingMethod>)

/**
 * Fishing: an option on a fishing spot (an NPC), or the tool used on it, checks the level, tool, bait and backpack, then
 * rolls every [CYCLE] ticks on the skill clock; the op re-runs each tick ([World.repeatOp]) until the backpack fills or
 * the spot moves. Each roll tries the method's fish best first, each with its own success pair, so a higher fish comes
 * up as often as its chart says and a lower one only when it misses. A catch uses up a piece of bait or a feather.
 * Spots move every 250 to 530 ticks to a free tile among their area's.
 *
 * Sources: the OSRS Wiki's Fishing, Fishing spot and fish pages (read 2026-10-29) for levels, XP, tools, bait, the spot
 * movement times, a roll every 5 ticks and the pike's recast (1 in 5 rolls take 6 ticks); each fish's pair is fitted to
 * the Wiki's success charts, which also show the better fish rolled first (`FishingTest` checks the points). The flow
 * (the click's cast message and animation, the first roll 4 ticks after the click, the checks before every roll, the
 * last bait freeing its own slot) and the river spots' tiles are the 2004Scape project's fishing scripts; the spawn
 * tiles are RSMod's (rev 233). Guessed: the swamp spots' area (their five spawn tiles, so they never find a free one and
 * stay put; the Wiki only says most spots move) and the player stopping when their spot moves. Out: pearl and
 * barbarian rods, stripy feathers (rainbow fish), the big net, harpoons, cages, karambwanji, fishing outfits and other
 * catch boosts, heron pets, the random fishing spot whirlpools. No sounds: the 241 synth ids have no names.
 */
class Fishing(private val world: World) {
    /** The tick each spot moves next, set the first time it is seen. */
    private val nextMove = HashMap<Npc, Long>()

    fun register() {
        for (spot in SPOTS) {
            val type = world.npcTypes[spot.npc] ?: continue
            for (method in spot.methods) {
                val option = (1..5).firstOrNull { type.op(it) == method.name } ?: continue
                world.handlers.onNpc(spot.npc, option) { op -> fish(op, method) }
                world.handlers.onObjOnNpc(spot.npc, method.tool) { op ->
                    // A tool used on a spot fishes it, as clicking the option does (2004Scape).
                    val npc = op.npc ?: return@onObjOnNpc
                    fish(Op(OpTarget.NPC, spot.npc, option, npc = npc), method)
                }
            }
        }
    }

    /** Moves the fishing spots whose time is up. Call once a tick, in the NPC phase. */
    fun process() {
        val tick = world.tick
        for (npc in world.npcs.all) {
            if (npc.spawn.type.id !in SPOT_IDS) continue
            val due = nextMove.getOrPut(npc) { tick + moveDelay() }
            if (due > tick) continue
            nextMove[npc] = tick + moveDelay()
            move(npc)
        }
        if (tick % CLEAN_UP_TICKS == 0L) nextMove.keys.removeIf { world.npcs[it.index] !== it }
    }

    private fun moveDelay(): Long = world.random.between(MOVE_LOW, MOVE_HIGH).toLong()

    /** Moves [npc] to a tile of its area that no other spot is on; nowhere when its area has none free. */
    private fun move(npc: Npc) {
        val here = Tile(npc.x, npc.z)
        val area = AREAS.firstOrNull { here in it } ?: return
        val free = area.filter { tile ->
            tile != here && world.npcs.near(tile.x, tile.z, npc.level, 0).none { it.spawn.type.id in SPOT_IDS }
        }
        if (free.isEmpty()) return
        val to = world.random.pick(free)
        world.npcs.teleport(npc, to.x, to.z)
    }

    private suspend fun ScriptContext.fish(op: Op, method: FishingMethod) {
        val npc = op.npc ?: return
        // As for woodcutting: a box opened since the last tick (the level-up a catch gave) ends it.
        if (op.repeat && player.modalOpen) return stop()
        val text = world.textFor(player)
        val level = player.skills.currentLevel(Skill.FISHING)
        if (level < method.level) {
            method.levelLine?.let { player.message(text[it, "level" to method.level]) }
            return stop()
        }
        if (player.inventory.count(method.tool) < 1) {
            player.message(text[method.noTool])
            return stop()
        }
        val bait = method.bait
        val baitLeft = if (bait == null) 0L else player.inventory.count(bait.obj)
        if (bait != null && baitLeft < 1) {
            player.message(text[bait.none])
            return stop()
        }
        // The last piece of bait frees its own slot for the fish (2004Scape).
        if (player.inventory.freeSlots == 0 && !(bait != null && baitLeft == 1L)) {
            player.message(text[FishingLines.INVENTORY_FULL])
            return stop()
        }
        if (!op.repeat) {
            player.message(text[method.cast])
            player.anim(method.castAnim)
        }
        val step = SkillClock.step(player, tick, CYCLE)
        if (step == ClockStep.DUE) {
            if (method.recasts && world.random.chance(RECAST_CHANCE, RECAST_DENOMINATOR)) {
                // The next roll is a tick later: 6 ticks on, not 5.
                player.actionDelay = tick + CYCLE + 1
                player.anim(method.castAnim)
            } else {
                player.anim(method.rollAnim)
            }
            roll(method, level)
        } else if (step == ClockStep.WAITING && method.recasts && player.actionDelay - tick == CYCLE.toLong()) {
            // The tick after a recast (only then is a whole cycle still to wait): the second cast the Wiki shows.
            player.anim(method.castAnim)
        }
        world.repeatOp(player, Op(OpTarget.NPC, op.id, op.option, npc = npc))
    }

    /** Tries each fish the player can catch, best first; the first success is caught. */
    private fun ScriptContext.roll(method: FishingMethod, level: Int) {
        for (fish in method.fish) {
            if (level < fish.level || !SuccessRoll.roll(world.random, fish.low, fish.high, level)) continue
            val type = world.objTypes[fish.obj] ?: error("No obj type ${fish.obj} for a caught fish")
            method.bait?.let { bait ->
                val slot = player.inventory.contents().firstOrNull { it.second.id == bait.obj }?.first
                if (slot != null) player.inventory.remove(slot, 1)
            }
            player.inventory.add(type, 1)
            player.skills.addTenths(Skill.FISHING, fish.xpTenths)
            player.message(world.textFor(player)[fish.line])
            return
        }
    }

    private fun ScriptContext.stop() {
        player.anim(-1)
    }

    companion object {
        /** The Wiki: a roll every 5 ticks. */
        const val CYCLE = 5

        /** The Wiki's pike: 1 in 5 rolls take a tick longer. */
        const val RECAST_CHANCE = 1
        const val RECAST_DENOMINATOR = 5

        /** The Wiki: most spots move after 250 to 530 ticks. */
        const val MOVE_LOW = 250
        const val MOVE_HIGH = 530

        private const val CLEAN_UP_TICKS = 100L

        const val NET = 303 // net
        const val FISHING_ROD = 307 // fishing_rod
        const val FLY_FISHING_ROD = 309 // fly_fishing_rod
        const val FISHING_BAIT = 313 // fishing_bait
        const val FEATHER = 314 // feather

        /** human_smallnet, human_fishing_casting, human_fish_onspot. */
        const val NET_ANIM = 621
        const val CAST_ANIM = 622
        const val ROD_ANIM = 623

        /** Levels and XP from the Wiki's fish pages; pairs fitted to their charts. Obj ids by gameval name. */
        val SHRIMPS = Fish(317, 1, 100, 48, 256, FishingLines.CATCH_SHRIMPS) // raw_shrimp
        val ANCHOVIES = Fish(321, 15, 400, 24, 128, FishingLines.CATCH_ANCHOVIES) // raw_anchovies
        val SARDINE = Fish(327, 5, 200, 32, 192, FishingLines.CATCH_SARDINE) // raw_sardine
        val HERRING = Fish(345, 10, 300, 24, 128, FishingLines.CATCH_HERRING) // raw_herring
        val TROUT = Fish(335, 20, 500, 32, 192, FishingLines.CATCH_TROUT) // raw_trout
        val SALMON = Fish(331, 30, 700, 16, 96, FishingLines.CATCH_SALMON) // raw_salmon
        val PIKE = Fish(349, 25, 600, 16, 96, FishingLines.CATCH_PIKE) // raw_pike

        private val BAIT = Bait(FISHING_BAIT, FishingLines.NO_BAIT)
        private val FEATHERS = Bait(FEATHER, FishingLines.NO_FEATHERS)

        val SMALL_NET = FishingMethod(
            "Net", NET, FishingLines.NO_NET, null, FishingLines.CAST_NET, NET_ANIM, NET_ANIM, listOf(ANCHOVIES, SHRIMPS), null,
        )
        val SEA_BAIT = FishingMethod(
            "Bait", FISHING_ROD, FishingLines.NO_ROD, BAIT, FishingLines.CAST_LINE, CAST_ANIM, ROD_ANIM, listOf(HERRING, SARDINE),
            FishingLines.LEVEL_BAIT,
        )
        val LURE = FishingMethod(
            "Lure", FLY_FISHING_ROD, FishingLines.NO_FLY_ROD, FEATHERS, FishingLines.CAST_LINE, CAST_ANIM, ROD_ANIM,
            listOf(SALMON, TROUT), FishingLines.LEVEL_LURE,
        )
        val RIVER_BAIT = FishingMethod(
            "Bait", FISHING_ROD, FishingLines.NO_ROD, BAIT, FishingLines.CAST_LINE, CAST_ANIM, ROD_ANIM, listOf(PIKE),
            FishingLines.LEVEL_BAIT, recasts = true,
        )

        /** Net/Bait spots (`0_50_49_saltfish` in Lumbridge Swamp) and Lure/Bait spots (`0_50_50_freshfish` on the River Lum). */
        const val SWAMP_SPOT = 1530
        const val RIVER_SPOT = 1527

        val SPOTS = listOf(
            SpotType(SWAMP_SPOT, listOf(SMALL_NET, SEA_BAIT)),
            SpotType(RIVER_SPOT, listOf(LURE, RIVER_BAIT)),
        )
        private val SPOT_IDS = SPOTS.map { it.npc }.toSet()

        /**
         * The tiles each group of spots moves between. The river's are 2004Scape's (`fishing_movement_lumbridge_river`),
         * which hold RSMod's two spawn tiles; the swamp's are its five spawn tiles (guessed).
         */
        val AREAS: List<List<Tile>> = listOf(
            listOf(Tile(3239, 3241), Tile(3239, 3242), Tile(3239, 3243), Tile(3239, 3244), Tile(3238, 3251), Tile(3238, 3252),
                Tile(3238, 3254), Tile(3238, 3255)),
            listOf(Tile(3240, 3146), Tile(3240, 3147), Tile(3244, 3150), Tile(3245, 3152), Tile(3246, 3155)),
        )
    }
}
