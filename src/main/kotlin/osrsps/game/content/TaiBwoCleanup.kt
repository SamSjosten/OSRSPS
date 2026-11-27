package osrsps.game.content

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.Op
import osrsps.game.script.OpTarget
import osrsps.game.script.ScriptContext
import osrsps.game.skill.SuccessRoll
import osrsps.game.zone.ZoneLoc

/** The Cleanup's messages: none of them are on the Wiki's pages, so every one is held for a recording. */
object TaiBwoCleanupLines : Lines("tbw_cleanup", "Tai Bwo Wannai Cleanup; Light Jungle; Medium Jungle; Dense Jungle; Village fence") {
    val NOT_MET = line("not_met", "INFERRED words wanted: Hack before the player has spoken to Murcaily")
    val NO_MACHETE = line("no_machete", "INFERRED words wanted: Hack, Repair or Reinforce without a machete")
    val LEVEL = line("level", "INFERRED words wanted: Hack below the jungle's Woodcutting level", "level")
    val INVENTORY_FULL = line("inventory_full", "INFERRED words wanted: Hack with no room for the thatch spar")
    val NO_SPAR = line("no_spar", "INFERRED words wanted: Repair or Reinforce without a thatch spar")
}

/**
 * The Tai Bwo Wannai Cleanup's jungle and fence (Karamja step K4.2; OSRS Wiki: Tai Bwo Wannai Cleanup, Light Jungle,
 * Medium Jungle, Dense Jungle, Thatch spar light/med/dense, Rotten village fence, Village fence and Machete, read
 * 2026-11-19). Favour is [TaiBwoWannai]'s.
 *
 * - **Jungle** (light 9010, medium 9015, dense 9020) is hacked with a machete, carried or wielded, after speaking to
 *   Murcaily, in four sections: each success turns the loc into the next type and gives a thatch spar, the jungle's
 *   Woodcutting XP and favour (light 10, 32 XP, 0.3%; medium 20, 55, 0.5%; dense 35, 80, 1%); the fourth leaves the
 *   chopped-away type (id + 4). A cycle lasts 16 ticks at level 10 to 19, one less each ten levels, 8 from 90 ([cycle]);
 *   the roll (the Wiki's chart for each machete) comes 4 ticks before its end, then the 3-tick success or fail
 *   animation; a fail gives 1 XP. Each machete has its hack animation (tbw_cleanup_player_hack and its opal, jade and
 *   red topaz ones), and the best held is used.
 * - **The village fence** (rotten 9025, partial 9026, short 9027, medium 9028, whole 9029): Repair with a machete and
 *   a thatch spar takes the spar and gives its favour and XP (light 0.1%, 28.1; med 0.3%, 42.1; dense 0.5%, 65.1);
 *   Reinforce on the whole fence the same. The fence is the world's, shared by everyone.
 *
 * Guessed: how far each spar repairs ([Spar.stages]: one stage for light, two for medium, four for dense, from "more
 * spars required" for light and "very good for repairing" for dense); the jungle growing back [REGROW_TICKS] after
 * its last hack and a fence rotting [DECAY_TICKS] after its last repair or reinforcement; a carried machete doing as a
 * wielded one does; the fence's three animations a tick each; the best spar used when Repair is clicked; no message
 * on a success. The events while hacking are [TaiBwoEvents]'.
 */
object TaiBwoCleanup {
    const val HACK = 4
    const val REPAIR = 1

    const val HACK_SUCCESS = 2387
    const val HACK_FAIL = 2388
    const val FAIL_XP_TENTHS = 10

    const val REGROW_TICKS = 100
    const val DECAY_TICKS = 1000

    enum class Machete(val obj: Int, val hack: Int, val fence: List<Int>) {
        PLAIN(975, 2382, listOf(2384, 2385, 2386)),
        OPAL(6313, 6085, listOf(2420, 2421, 2422)),
        JADE(6315, 6086, listOf(2423, 2424, 2425)),
        RED_TOPAZ(6317, 6087, listOf(2426, 2427, 2428)),
    }

    /** A jungle type: its first (whole) loc, then three more sections, then the chopped-away loc at [first] + 4. */
    enum class Jungle(val first: Int, val level: Int, val xpTenths: Int, val favour: Int, val spar: Int, val rates: List<Pair<Int, Int>>) {
        LIGHT(9010, 10, 320, 3, 6281, listOf(49 to 169, 54 to 174, 70 to 190, 85 to 205)),
        MEDIUM(9015, 20, 550, 5, 6283, listOf(34 to 85, 39 to 90, 54 to 105, 70 to 121)),
        DENSE(9020, 35, 800, 10, 6285, listOf(16 to 44, 21 to 49, 36 to 64, 52 to 80)),
        ;

        val hackable: IntRange get() = first..first + SECTIONS - 1
        val chopped: Int get() = first + SECTIONS
    }

    const val SECTIONS = 4

    enum class Spar(val obj: Int, val stages: Int, val favour: Int, val xpTenths: Int) {
        LIGHT(6281, 1, 1, 281),
        MEDIUM(6283, 2, 3, 421),
        DENSE(6285, 4, 5, 651),
    }

    const val FENCE_ROTTEN = 9025
    const val FENCE_WHOLE = 9029
    val FENCES = FENCE_ROTTEN..FENCE_WHOLE

    /** The ticks of a hack cycle at Woodcutting [level]: 16 at 10 to 19, one less each ten levels, 8 from 90. */
    fun cycle(level: Int): Int = (17 - level / 10).coerceIn(8, 16)

    fun jungle(locId: Int): Jungle? = Jungle.entries.firstOrNull { locId in it.hackable }

    /** The best machete [player] carries or wields, or null. */
    fun machete(player: Player): Machete? =
        Machete.entries.lastOrNull { player.inventory.count(it.obj) > 0 || player.equipment.count(it.obj) > 0 }

    /** The best thatch spar [player] carries, or null. */
    fun spar(player: Player): Spar? = Spar.entries.lastOrNull { player.inventory.count(it.obj) > 0 }

    /** A hack cycle's start and its length, fixed when it starts so a boost wearing off mid-cycle can't roll twice. */
    private class Hack(var start: Long, var cycle: Int)

    /** Each hacking player's cycle, by save key; dropped for players gone offline (see [register]). */
    private val hacks = HashMap<String, Hack>()

    private suspend fun ScriptContext.hack(world: World, op: Op) {
        val loc = op.loc ?: return
        val jungle = jungle(loc.id) ?: return
        if (op.repeat && player.modalOpen) return stopHacking()
        val machete = checks(world, jungle) ?: return stopHacking()
        if (!op.repeat || hacks[player.saveKey] == null) {
            hacks[player.saveKey] = Hack(tick, cycle(player.skills.currentLevel(Skill.WOODCUTTING)))
            player.anim(machete.hack)
            return world.repeatOp(player, op)
        }
        val hack = hacks.getValue(player.saveKey)
        if (!world.locs.exists(loc)) return stopHacking()
        val level = player.skills.currentLevel(Skill.WOODCUTTING)
        val cycle = hack.cycle
        val elapsed = (tick - hack.start).toInt()
        var next = loc
        if (elapsed in 1 until cycle - ROLL_BEFORE_END && TaiBwoEvents.roll(world, player, loc, jungle)) {
            // An event ends the hack; the creature's surprise or the new loc shows instead of the swing.
            hacks.remove(player.saveKey)
            return
        }
        if (elapsed == cycle - ROLL_BEFORE_END) {
            TaiBwoEvents.skillTick(player)
            val (low, high) = jungle.rates[machete.ordinal]
            if (SuccessRoll.roll(world.random, low, high, level)) {
                player.anim(HACK_SUCCESS)
                next = cut(world, player, loc, jungle) ?: return
                if (player.inventory.freeSlots == 0) {
                    // The spar took the last slot: stop here, letting the success animation play out.
                    hacks.remove(player.saveKey)
                    return world.message(player, TaiBwoCleanupLines.INVENTORY_FULL)
                }
            } else {
                player.anim(HACK_FAIL)
                player.skills.addTenths(Skill.WOODCUTTING, FAIL_XP_TENTHS)
            }
        } else if (elapsed >= cycle) {
            hack.start = tick
            hack.cycle = cycle(level)
            player.anim(machete.hack)
        }
        world.repeatOp(player, if (next === loc) op else Op(OpTarget.LOC, next.id, op.option, loc = next, repeat = true))
    }

    /** What stops a hack before it starts or between cycles; the machete to use when nothing does. */
    private fun ScriptContext.checks(world: World, jungle: Jungle): Machete? {
        if (!TaiBwoWannai.has(player, TaiBwoWannai.Progress.MET_MURCAILY)) {
            world.message(player, TaiBwoCleanupLines.NOT_MET)
            return null
        }
        if (player.skills.currentLevel(Skill.WOODCUTTING) < jungle.level) {
            world.message(player, TaiBwoCleanupLines.LEVEL, "level" to jungle.level)
            return null
        }
        val machete = machete(player)
        if (machete == null) {
            world.message(player, TaiBwoCleanupLines.NO_MACHETE)
            return null
        }
        if (player.inventory.freeSlots == 0) {
            world.message(player, TaiBwoCleanupLines.INVENTORY_FULL)
            return null
        }
        return machete
    }

    private fun ScriptContext.stopHacking() {
        hacks.remove(player.saveKey)
        player.anim(-1)
    }

    /**
     * One section of [loc] hacked away by [player]: the spar, XP and favour, and the loc's next type. Returns the new loc
     * while sections are left, null once it's chopped away (the hack ends).
     */
    internal fun cut(world: World, player: Player, loc: ZoneLoc, jungle: Jungle): ZoneLoc? {
        world.objTypes[jungle.spar]?.let { player.inventory.add(it, 1) }
        player.skills.addTenths(Skill.WOODCUTTING, jungle.xpTenths)
        TaiBwoWannai.addFavour(player, jungle.favour)
        TaiBwoWannai.mark(player, TaiBwoWannai.Progress.HACKED)
        val next = world.locs.replace(loc, loc.id + 1) ?: return null
        world.locs.restoreAfter(loc.x, loc.z, loc.level, loc.layer, REGROW_TICKS)
        if (next.id == jungle.chopped) {
            hacks.remove(player.saveKey)
            return null
        }
        return next
    }

    /** Repair or Reinforce, with [chosen] or the best spar held. */
    private suspend fun ScriptContext.repair(world: World, loc: ZoneLoc, chosen: Spar?) {
        arriveDelay()
        val machete = machete(player) ?: return world.message(player, TaiBwoCleanupLines.NO_MACHETE)
        val spar = chosen ?: spar(player) ?: return world.message(player, TaiBwoCleanupLines.NO_SPAR)
        for (anim in machete.fence) {
            player.anim(anim)
            delay(1)
        }
        if (!world.locs.exists(loc) || player.inventory.count(spar.obj) == 0L) return
        mend(world, player, loc, spar)
    }

    /** [spar] put into [loc]: the favour and XP, and the fence [Spar.stages] nearer whole (or kept whole longer). */
    internal fun mend(world: World, player: Player, loc: ZoneLoc, spar: Spar) {
        Ned.take(player, spar.obj, 1)
        player.skills.addTenths(Skill.WOODCUTTING, spar.xpTenths)
        TaiBwoWannai.addFavour(player, spar.favour)
        TaiBwoWannai.mark(player, TaiBwoWannai.Progress.REPAIRED)
        world.locs.replace(loc, minOf(FENCE_WHOLE, loc.id + spar.stages)) ?: return
        world.locs.restoreAfter(loc.x, loc.z, loc.level, loc.layer, DECAY_TICKS)
    }

    private const val PRUNE_TICKS = 100

    /** The roll comes this many ticks before a cycle's end. */
    const val ROLL_BEFORE_END = 4

    fun register(world: World) {
        // A hack left by walking off or logging out leaves its entry; it goes once the player is offline.
        world.tickHooks += { tick ->
            if (tick % PRUNE_TICKS == 0L && hacks.isNotEmpty()) hacks.keys.retainAll(world.onlinePlayers.mapTo(HashSet()) { it.saveKey })
        }
        TaiBwoEvents.register(world)
        for (jungle in Jungle.entries) for (id in jungle.hackable) world.handlers.onLoc(id, HACK) { op -> hack(world, op) }
        for (fence in FENCES) {
            world.handlers.onLoc(fence, REPAIR) { op -> repair(world, op.loc!!, null) }
            for (spar in Spar.entries) world.handlers.onObjOnLoc(fence, spar.obj) { op -> repair(world, op.loc!!, spar) }
        }
    }
}
