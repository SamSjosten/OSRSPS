package osrsps.game.skill

import osrsps.game.Skill
import osrsps.game.WearPos
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
    val LEVEL_NET = line("level_net", "the message when netting the swamp caves' spots needs a higher level (33); words guessed", "level")
    val CATCH_FROG_SPAWN = line("catch_frog_spawn", "Frog spawn: the message for each catch in the swamp caves; words guessed")
    val CATCH_SWAMP_WEED = line("catch_swamp_weed", "Swamp weed: the message for each one netted in the swamp caves; words guessed")
    val CATCH_CAVE_EEL = line("catch_cave_eel", "Raw cave eel: the message for each catch (the Raw cave eel page's changes, 27 November 2024)")
    val CATCH_SLIMY_EEL = line("catch_slimy_eel", "Raw slimy eel: the message for each catch (the Raw slimy eel page's changes)")
    val NO_NET = line("no_net", "Small fishing net: the message on Net without one")
    val NO_ROD = line("no_rod", "Fishing rod: the message on Bait without one")
    val NO_FLY_ROD = line("no_fly_rod", "Fly fishing rod: the message on Lure without one")
    val NO_BAIT = line("no_bait", "Fishing bait: the message on Bait with none left")
    val NO_FEATHERS = line("no_feathers", "Feather: the message on Lure with none left")
    val INVENTORY_FULL = line("inventory_full", "the message when the backpack is full")
    val CAST_HARPOON = line("cast_harpoon", "the message on starting to fish with a harpoon")
    val CAST_CAGE = line("cast_cage", "the message on starting to fish with a lobster pot")
    val CATCH_LOBSTER = line("catch_lobster", "Raw lobster: the message for each catch")
    val CATCH_TUNA = line("catch_tuna", "Raw tuna: the message for each catch (the Raw tuna page's changes, 27 November 2024)")
    val CATCH_SWORDFISH = line("catch_swordfish", "Raw swordfish: the message for each catch (the Raw swordfish page's changes, 27 November 2024)")
    val CATCH_SHARK = line("catch_shark", "Raw shark: the message for each catch")
    val LEVEL_CAGE = line("level_cage", "the message when caging lobsters needs a higher level (40)", "level")
    val LEVEL_HARPOON = line("level_harpoon", "the message when harpooning a spot's fish needs a higher level (35 tuna, 76 shark)", "level")
    val NO_HARPOON = line("no_harpoon", "Harpoon: the message on Harpoon without one")
    val NO_CAGE = line("no_cage", "Lobster pot: the message on Cage without one")
    val CATCH_MACKEREL = line("catch_mackerel", "Raw mackerel: the message for each one netted (the Raw mackerel page's changes, 27 November 2024)")
    val CATCH_COD = line("catch_cod", "Raw cod: the message for each one netted (the Raw cod page's changes, 27 November 2024)")
    val CATCH_BASS = line("catch_bass", "Raw bass: the message for each one netted (the Raw bass page's changes, 27 November 2024)")
    val CATCH_BOOTS = line("catch_boots", "Leather boots: the message when the big net brings up a pair")
    val CATCH_GLOVES = line("catch_gloves", "Leather gloves: the message when the big net brings up a pair")
    val CATCH_SEAWEED = line("catch_seaweed", "Seaweed: the message when the big net brings some up")
    val CATCH_OYSTER = line("catch_oyster", "Oyster: the message when the big net brings one up")
    val CATCH_CASKET = line("catch_casket", "Casket: the message when the big net brings one up")
    val LEVEL_BIG_NET = line("level_big_net", "the message when big net fishing needs a higher level (16)", "level")
    val NO_BIG_NET = line("no_big_net", "Big fishing net: the message on Big Net without one")
}

/** One fish: the obj caught, the level and XP in tenths, its success pair (out of 256) and its catch message. */
class Fish(val obj: Int, val level: Int, val xpTenths: Int, val low: Int, val high: Int, val line: Line)

/**
 * A tool that fishes as well as, or better than, a method's own: the wieldable harpoons. It is used from the backpack or
 * the weapon slot, from [level] Fishing; [percent] scales each fish's pair, rounded down (the Wiki's dragon harpoon
 * charts are the harpoon's times 1.2, its crystal harpoon's times 1.35), and [anim] is the player's animation with it.
 */
class FishingTool(val obj: Int, val level: Int, val percent: Int, val anim: Int)

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
    /** Other spellings of the option on some spots: Draynor's Net/Bait spots say "Small Net". */
    val otherNames: Set<String> = emptySet(),
    /** Tools that also fish this way, best first; the method's own [tool] is tried after them. */
    val otherTools: List<FishingTool> = emptyList(),
    /**
     * The big net's way: every entry of [fish] is rolled on its own, in order, so one roll can bring up several (the
     * Wiki's Fishing spot (big net, harpoon) page and Mod Ash's 9 rolls); otherwise the first success is the catch.
     */
    val rollsEach: Boolean = false,
) {
    val level: Int get() = fish.minOf { it.level }

    /** Every obj that fishes this way, used on a spot or held. */
    val tools: List<Int> get() = otherTools.map { it.obj } + tool
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
 * stay put; the Wiki only says most spots move) and the player stopping when their spot moves.
 *
 * Cages and harpoons (Fishing and Cooking 2, step FC1) work the same way at Catherby and Musa Point, which are built in
 * place and reached by ::tele. Their cast messages, animations and checks follow 2004Scape's rarefish and memberfish
 * scripts; guessed: their first roll comes as the other methods' does (2004Scape has the harpoon's a tick later, which
 * no Wiki page confirms), and the wieldable harpoons' animations playing for the cast as well as each roll.
 *
 * The big net (step FC2) fishes Catherby's Big Net/Harpoon spots: each of its nine rolls is made on its own, so one
 * roll can bring up mackerel, cod, bass and junk together.
 *
 * Out: pearl and barbarian rods, stripy feathers (rainbow fish), karambwanji, big bass, big swordfish and big shark,
 * barehanded fishing, the infernal and crystal harpoons, the skills necklace's casket boost, fishing outfits and other
 * catch boosts, heron pets, the random fishing spot whirlpools. No sounds: the 241 synth ids have no names.
 */
class Fishing(private val world: World) {
    /** The tick each spot moves next, set the first time it is seen. */
    private val nextMove = HashMap<Npc, Long>()

    fun register() {
        for (spot in SPOTS) {
            val type = world.npcTypes[spot.npc] ?: continue
            for (method in spot.methods) {
                val option = (1..5).firstOrNull { type.op(it) == method.name || type.op(it) in method.otherNames } ?: continue
                world.handlers.onNpc(spot.npc, option) { op -> fish(op, method) }
                for (tool in method.tools) {
                    world.handlers.onObjOnNpc(spot.npc, tool) { op ->
                        // A tool used on a spot fishes it, as clicking the option does (2004Scape).
                        val npc = op.npc ?: return@onObjOnNpc
                        fish(Op(OpTarget.NPC, spot.npc, option, npc = npc), method)
                    }
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
        val tool = toolFor(method, level)
        if (tool == null) {
            player.message(text[method.noTool])
            return stop()
        }
        val castAnim = if (tool === PLAIN) method.castAnim else tool.anim
        val rollAnim = if (tool === PLAIN) method.rollAnim else tool.anim
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
            player.anim(castAnim)
        }
        val step = SkillClock.step(player, tick, CYCLE)
        if (step == ClockStep.DUE) {
            if (method.recasts && world.random.chance(RECAST_CHANCE, RECAST_DENOMINATOR)) {
                // The next roll is a tick later: 6 ticks on, not 5.
                player.actionDelay = tick + CYCLE + 1
                player.anim(castAnim)
            } else {
                player.anim(rollAnim)
            }
            roll(method, level, tool.percent)
        } else if (step == ClockStep.WAITING && method.recasts && player.actionDelay - tick == CYCLE.toLong()) {
            // The tick after a recast (only then is a whole cycle still to wait): the second cast the Wiki shows.
            player.anim(castAnim)
        }
        world.repeatOp(player, Op(OpTarget.NPC, op.id, op.option, npc = npc))
    }

    /**
     * The best tool for [method] the player holds, in the backpack or the weapon slot, that their [level] can use;
     * [PLAIN] for the method's own tool, null for none. Guessed: a better tool the level can't use yet counts as no
     * tool, so the method's own tool or the no-tool message comes next.
     */
    private fun ScriptContext.toolFor(method: FishingMethod, level: Int): FishingTool? {
        val weapon = player.equipment[WearPos.WEAPON]?.id
        fun held(obj: Int) = weapon == obj || player.inventory.count(obj) > 0
        return method.otherTools.firstOrNull { level >= it.level && held(it.obj) } ?: PLAIN.takeIf { held(method.tool) }
    }

    /** Tries each fish the player can catch, best first, with pairs scaled by the tool's [percent]; the first success is caught. */
    private fun ScriptContext.roll(method: FishingMethod, level: Int, percent: Int) {
        if (method.rollsEach) return rollEach(method, level)
        for (fish in method.fish) {
            if (level < fish.level) continue
            if (!SuccessRoll.roll(world.random, fish.low * percent / 100, fish.high * percent / 100, level)) continue
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

    /**
     * The big net: every entry the level allows is rolled, each catch added with its XP and message. Guessed: a catch
     * that finds the backpack full is lost and the rest of the rolls are skipped (the next tick's check then stops).
     */
    private fun ScriptContext.rollEach(method: FishingMethod, level: Int) {
        val text = world.textFor(player)
        for (fish in method.fish) {
            if (level < fish.level) continue
            if (!SuccessRoll.roll(world.random, fish.low, fish.high, level)) continue
            if (player.inventory.freeSlots == 0) return
            val type = world.objTypes[fish.obj] ?: error("No obj type ${fish.obj} for a caught fish")
            player.inventory.add(type, 1)
            player.skills.addTenths(Skill.FISHING, fish.xpTenths)
            player.message(text[fish.line])
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
        const val LOBSTER_POT = 301 // lobster_pot
        const val HARPOON = 311 // harpoon
        const val BARB_TAIL_HARPOON = 10129 // hunting_barbed_harpoon
        const val DRAGON_HARPOON = 21028 // dragon_harpoon
        const val BIG_NET = 305 // big_net

        /** Stands for a method's own tool in [toolFor]: its pairs as they are, and the method's animations. */
        private val PLAIN = FishingTool(-1, 1, 100, -1)

        /** human_smallnet, human_fishing_casting, human_fish_onspot. */
        const val NET_ANIM = 621
        const val CAST_ANIM = 622
        const val ROD_ANIM = 623

        /** human_harpoon, human_lobster, human_harpoon_barbed, human_harpoon_dragon. */
        const val HARPOON_ANIM = 618
        const val CAGE_ANIM = 619
        const val BARB_TAIL_HARPOON_ANIM = 5108
        const val DRAGON_HARPOON_ANIM = 7401

        /** human_largenet. */
        const val BIG_NET_ANIM = 620

        /** Levels and XP from the Wiki's fish pages; pairs fitted to their charts. Obj ids by gameval name. */
        val SHRIMPS = Fish(317, 1, 100, 48, 256, FishingLines.CATCH_SHRIMPS) // raw_shrimp
        val ANCHOVIES = Fish(321, 15, 400, 24, 128, FishingLines.CATCH_ANCHOVIES) // raw_anchovies
        val SARDINE = Fish(327, 5, 200, 32, 192, FishingLines.CATCH_SARDINE) // raw_sardine
        val HERRING = Fish(345, 10, 300, 24, 128, FishingLines.CATCH_HERRING) // raw_herring
        val TROUT = Fish(335, 20, 500, 32, 192, FishingLines.CATCH_TROUT) // raw_trout
        val SALMON = Fish(331, 30, 700, 16, 96, FishingLines.CATCH_SALMON) // raw_salmon
        val PIKE = Fish(349, 25, 600, 16, 96, FishingLines.CATCH_PIKE) // raw_pike

        /**
         * The swamp caves' spots: their pairs are the Wiki's own charts (Frog spawn: frog spawn 16/96 and swamp weed 10/10
         * from 33; Raw cave eel: cave eel and slimy eel 10/80 each, the cave eel tried first).
         */
        val FROG_SPAWN = Fish(5004, 33, 750, 16, 96, FishingLines.CATCH_FROG_SPAWN) // giant_frogspawn
        val SWAMP_WEED = Fish(10978, 33, 10, 10, 10, FishingLines.CATCH_SWAMP_WEED) // dorgesh_swamp_weed
        val CAVE_EEL = Fish(5001, 38, 800, 10, 80, FishingLines.CATCH_CAVE_EEL) // raw_cave_eel
        val SLIMY_EEL = Fish(3379, 28, 650, 10, 80, FishingLines.CATCH_SLIMY_EEL) // mort_slimey_eel

        /**
         * Cage and harpoon fish: levels, XP and pairs are the Wiki's (Raw lobster, Raw tuna, Raw swordfish and Raw shark
         * pages, read 2026-11-10, whose success charts give each pair; swordfish is tried before tuna, the charts'
         * cascade).
         */
        val LOBSTER = Fish(377, 40, 900, 6, 95, FishingLines.CATCH_LOBSTER) // raw_lobster
        val TUNA = Fish(359, 35, 800, 8, 64, FishingLines.CATCH_TUNA) // raw_tuna
        val SWORDFISH = Fish(371, 50, 1000, 4, 48, FishingLines.CATCH_SWORDFISH) // raw_swordfish
        val SHARK = Fish(383, 76, 1100, 3, 40, FishingLines.CATCH_SHARK) // raw_shark

        private val BAIT = Bait(FISHING_BAIT, FishingLines.NO_BAIT)
        private val FEATHERS = Bait(FEATHER, FishingLines.NO_FEATHERS)

        val SMALL_NET = FishingMethod(
            "Net", NET, FishingLines.NO_NET, null, FishingLines.CAST_NET, NET_ANIM, NET_ANIM, listOf(ANCHOVIES, SHRIMPS), null,
            otherNames = setOf("Small Net"),
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

        /**
         * The harpoons that wield: the barb-tail fishes as the harpoon does, the dragon harpoon 20% better from 61 Fishing
         * (the Wiki's Harpoon, Barb-tail harpoon and Dragon harpoon pages). Out: the infernal harpoon (its charges and the
         * fish it cooks) and the crystal harpoon (its charges), and the dragon harpoon's special attack.
         */
        private val HARPOONS = listOf(
            FishingTool(DRAGON_HARPOON, 61, 120, DRAGON_HARPOON_ANIM),
            FishingTool(BARB_TAIL_HARPOON, 1, 100, BARB_TAIL_HARPOON_ANIM),
        )

        /**
         * The big net's catches, each its own roll in this order (Mod Ash, 3 October 2020: boots, seaweed, gloves,
         * mackerel, another mackerel, oyster, casket, cod, bass). The mackerel's pairs (5/65, the second 10/10), cod's,
         * bass's and the casket's rate are the Wiki's (Raw mackerel, Raw cod, Raw bass and Casket pages, read
         * 2026-11-10; the casket's 1 in 128 rising to 1 in 85.3 is 1/2); the boots', seaweed's, gloves' and oyster's
         * pairs and the junk's XP are 2004Scape's (the Wiki only calls them uncommon), and agree with the Wiki where both
         * give them.
         */
        val BIG_NET_BOOTS = Fish(1061, 16, 10, 10, 10, FishingLines.CATCH_BOOTS) // leather_boots
        val BIG_NET_SEAWEED = Fish(401, 16, 1, 10, 10, FishingLines.CATCH_SEAWEED) // seaweed
        val BIG_NET_GLOVES = Fish(1059, 16, 10, 5, 5, FishingLines.CATCH_GLOVES) // leather_gloves
        val MACKEREL = Fish(353, 16, 200, 5, 65, FishingLines.CATCH_MACKEREL) // raw_mackerel
        val MACKEREL_AGAIN = Fish(353, 16, 200, 10, 10, FishingLines.CATCH_MACKEREL) // raw_mackerel, the second roll
        val BIG_NET_OYSTER = Fish(407, 16, 100, 3, 7, FishingLines.CATCH_OYSTER) // oystershell
        val BIG_NET_CASKET = Fish(405, 16, 100, 1, 2, FishingLines.CATCH_CASKET) // casket
        val COD = Fish(341, 23, 450, 4, 55, FishingLines.CATCH_COD) // raw_cod
        val BASS = Fish(363, 46, 1000, 3, 40, FishingLines.CATCH_BASS) // raw_bass

        val BIG_NET_FISHING = FishingMethod(
            "Big Net", BIG_NET, FishingLines.NO_BIG_NET, null, FishingLines.CAST_NET, BIG_NET_ANIM, BIG_NET_ANIM,
            listOf(BIG_NET_BOOTS, BIG_NET_SEAWEED, BIG_NET_GLOVES, MACKEREL, MACKEREL_AGAIN, BIG_NET_OYSTER, BIG_NET_CASKET, COD, BASS),
            FishingLines.LEVEL_BIG_NET, rollsEach = true,
        )

        val CAGE = FishingMethod(
            "Cage", LOBSTER_POT, FishingLines.NO_CAGE, null, FishingLines.CAST_CAGE, CAGE_ANIM, CAGE_ANIM, listOf(LOBSTER),
            FishingLines.LEVEL_CAGE,
        )
        val HARPOON_TUNA = FishingMethod(
            "Harpoon", HARPOON, FishingLines.NO_HARPOON, null, FishingLines.CAST_HARPOON, HARPOON_ANIM, HARPOON_ANIM,
            listOf(SWORDFISH, TUNA), FishingLines.LEVEL_HARPOON, otherTools = HARPOONS,
        )
        val HARPOON_SHARK = FishingMethod(
            "Harpoon", HARPOON, FishingLines.NO_HARPOON, null, FishingLines.CAST_HARPOON, HARPOON_ANIM, HARPOON_ANIM,
            listOf(SHARK), FishingLines.LEVEL_HARPOON, otherTools = HARPOONS,
        )

        val CAVE_NET = FishingMethod(
            "Small Net", NET, FishingLines.NO_NET, null, FishingLines.CAST_NET, NET_ANIM, NET_ANIM, listOf(FROG_SPAWN, SWAMP_WEED),
            FishingLines.LEVEL_NET,
        )
        val CAVE_BAIT = FishingMethod(
            "Bait", FISHING_ROD, FishingLines.NO_ROD, BAIT, FishingLines.CAST_LINE, CAST_ANIM, ROD_ANIM, listOf(CAVE_EEL, SLIMY_EEL),
            FishingLines.LEVEL_BAIT,
        )

        /** Net/Bait spots (`0_50_49_saltfish` in Lumbridge Swamp) and Lure/Bait spots (`0_50_50_freshfish` on the River Lum). */
        const val SWAMP_SPOT = 1530
        const val RIVER_SPOT = 1527

        /** Net/Bait spots on Draynor Village's shore by the willows (`0_48_50_saltfish`). */
        const val DRAYNOR_SPOT = 1525
        /** Al Kharid's Small Net/Bait spots (`0_51_49_saltfish`), south-west of the town by the sea. */
        const val AL_KHARID_SPOT = 1528

        /** Lure/Bait spots on the River Barbarian by Barbarian Village (`0_48_53_freshfish`), as the River Lum's. */
        const val BARBARIAN_SPOT = 1526

        /**
         * The Lumbridge Swamp Caves' Small Net/Bait spots (`0_49_149_cavefish` and `0_50_149_cavefish`), for frog spawn and
         * eels. They have no area, so they stay put (guessed; the Wiki says only that the Dorgesh-Kaan one never moves).
         */
        const val SWAMP_CAVES_WEST_SPOT = 1497
        const val SWAMP_CAVES_EAST_SPOT = 1498

        /**
         * Catherby's beach (built in place, reached by ::tele): Net/Bait (`0_44_53_saltfish`), Cage/Harpoon
         * (`0_44_53_rarefish`) and Big Net/Harpoon (`0_44_53_memberfish`).
         */
        const val CATHERBY_NET_SPOT = 1518
        const val CATHERBY_CAGE_SPOT = 1519
        const val CATHERBY_SHARK_SPOT = 1520

        /** Musa Point on Karamja (built in place, reached by ::tele): Net/Bait (`0_45_49_saltfish`) and Cage/Harpoon (`0_45_49_rarefish`). */
        const val MUSA_POINT_NET_SPOT = 1521
        const val MUSA_POINT_CAGE_SPOT = 1522

        val SPOTS = listOf(
            SpotType(SWAMP_SPOT, listOf(SMALL_NET, SEA_BAIT)),
            SpotType(RIVER_SPOT, listOf(LURE, RIVER_BAIT)),
            SpotType(DRAYNOR_SPOT, listOf(SMALL_NET, SEA_BAIT)),
            SpotType(AL_KHARID_SPOT, listOf(SMALL_NET, SEA_BAIT)),
            SpotType(BARBARIAN_SPOT, listOf(LURE, RIVER_BAIT)),
            SpotType(SWAMP_CAVES_WEST_SPOT, listOf(CAVE_NET, CAVE_BAIT)),
            SpotType(SWAMP_CAVES_EAST_SPOT, listOf(CAVE_NET, CAVE_BAIT)),
            SpotType(CATHERBY_NET_SPOT, listOf(SMALL_NET, SEA_BAIT)),
            SpotType(CATHERBY_CAGE_SPOT, listOf(CAGE, HARPOON_TUNA)),
            SpotType(CATHERBY_SHARK_SPOT, listOf(BIG_NET_FISHING, HARPOON_SHARK)),
            SpotType(MUSA_POINT_NET_SPOT, listOf(SMALL_NET, SEA_BAIT)),
            SpotType(MUSA_POINT_CAGE_SPOT, listOf(CAGE, HARPOON_TUNA)),
        )
        private val SPOT_IDS = SPOTS.map { it.npc }.toSet()

        /**
         * The tiles each group of spots moves between. The river's are 2004Scape's (`fishing_movement_lumbridge_river`),
         * which hold RSMod's two spawn tiles; the swamp's are its five spawn tiles (guessed); Draynor's are 2004Scape's
         * (`fishing_movement_draynor_village_enum`), which hold the two tiles mejrs's live NPC list has the spots on; Al
         * Kharid's are below.
         */
        val AREAS: List<List<Tile>> = listOf(
            listOf(Tile(3239, 3241), Tile(3239, 3242), Tile(3239, 3243), Tile(3239, 3244), Tile(3238, 3251), Tile(3238, 3252),
                Tile(3238, 3254), Tile(3238, 3255)),
            listOf(Tile(3240, 3146), Tile(3240, 3147), Tile(3244, 3150), Tile(3245, 3152), Tile(3246, 3155)),
            listOf(Tile(3086, 3227), Tile(3086, 3228), Tile(3085, 3230), Tile(3085, 3231)),
            // Al Kharid's: the six tiles 2004Scape's fishing_movement_al_kharid_enum notes as live's since March 2021.
            listOf(Tile(3266, 3148), Tile(3267, 3148), Tile(3268, 3147), Tile(3275, 3140), Tile(3276, 3140), Tile(3277, 3139)),
            // Barbarian Village's: 2004Scape's fishing_movement_barbarian_village_enum, which holds the two tiles mejrs's
            // live NPC list has the spots on (3104, 3424 and 3110, 3434).
            listOf(Tile(3110, 3434), Tile(3110, 3433), Tile(3110, 3432), Tile(3104, 3425), Tile(3104, 3424)),
            // Catherby's, which all three kinds of spot share: 2004Scape's fishing_movement_catherby_enum, which holds the
            // seven tiles mejrs's live NPC list and the Wiki's spot pages have the spots on.
            listOf(Tile(2836, 3431), Tile(2837, 3431), Tile(2838, 3431), Tile(2844, 3429), Tile(2845, 3429), Tile(2846, 3429),
                Tile(2853, 3423), Tile(2854, 3423), Tile(2855, 3423), Tile(2859, 3426), Tile(2860, 3426)),
            // Musa Point's, shared by both kinds: 2004Scape's fishing_movement_musa_point_enum, which holds the six tiles
            // mejrs's live NPC list has the spots on.
            listOf(Tile(2921, 3178), Tile(2926, 3176), Tile(2926, 3179), Tile(2926, 3180), Tile(2925, 3181), Tile(2924, 3181),
                Tile(2923, 3180), Tile(2923, 3179)),
        )
    }
}
