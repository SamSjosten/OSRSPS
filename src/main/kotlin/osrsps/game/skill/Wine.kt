package osrsps.game.skill

import java.util.WeakHashMap
import osrsps.game.InvObj
import osrsps.game.Inventory
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.script.TimerType

/**
 * Wine fermenting (Fishing and Cooking 2 step FC5b). Grapes squeezed into a jug of water make unfermented wine
 * ([CookingMixes]); [FERMENT_TICKS] after the last one is made, every unfermented wine the player holds, in the backpack
 * and the bank, ferments at once: each one rolls the 48/352 pair and becomes a jug of wine for 200 XP, or a jug of bad
 * wine for none. The bank's wine rolls in [BATCHES] batches, every wine in a batch the same way, and its remainder one
 * by one; the backpack's rolls one by one. A login with unfermented wine starts the wait again.
 *
 * Sources: the OSRS Wiki's Jug of wine and Unfermented wine pages (read 2026-11-10: level 35, 200 XP, the pair, the
 * 12-second timer reset by each new jug, banked wine fermenting, Mod Ash's 15 batches); 2004Scape's wine script for the
 * timer's 20 ticks, the bank first in batches, the backpack one by one, and the login restart.
 * Guessed: the wait watched each tick rather than restarted by the making itself (one tick either way), the bank's wine
 * not fermenting when the bank has no room for a wine or bad wine stack, and no message on fermenting.
 */
class Wine(private val world: World) {
    /** Each watched player's unfermented wine at the last look and the tick it last went up. */
    private class Watch(var held: Long, var since: Long)

    private val watching = WeakHashMap<Player, Watch>()

    /** Starts (or keeps) watching [player]'s unfermented wine: the grapes were used on a jug, or they logged in. */
    fun watch(player: Player) {
        watching.getOrPut(player) { Watch(held(player), world.tick) }.since = world.tick
        world.scripts.setTimer(player, TIMER, 1, TimerType.SOFT) { check(player) }
    }

    /** At login: unfermented wine left from before waits its [FERMENT_TICKS] again. */
    fun login(player: Player) {
        if (held(player) > 0) watch(player)
    }

    private fun held(player: Player) = player.inventory.count(UNFERMENTED) + player.bank.count(UNFERMENTED)

    private fun check(player: Player) {
        val watch = watching[player] ?: return stop(player)
        val held = held(player)
        if (held > watch.held) watch.since = world.tick
        watch.held = held
        val waited = world.tick - watch.since >= FERMENT_TICKS
        // Grapes and a jug still held may yet make wine (the first one comes ticks after the grapes are used), so the
        // watch goes on; with neither, it ends once the wait is over.
        if (held <= 0L) {
            if (waited && !canMake(player)) stop(player)
            return
        }
        if (!waited) return
        ferment(player)
        watch.held = 0
        if (!canMake(player)) stop(player)
    }

    private fun canMake(player: Player) =
        player.inventory.count(CookingMixes.GRAPES) > 0 && player.inventory.count(CookingMixes.JUG_OF_WATER) > 0

    private fun stop(player: Player) {
        watching.remove(player)
        world.scripts.clearTimer(player, TIMER)
    }

    /** Ferments everything [player] holds now. */
    fun ferment(player: Player) {
        val level = player.skills.currentLevel(Skill.COOKING)
        fun roll() = SuccessRoll.roll(world.random, LOW, HIGH, level)
        var good = 0L
        val banked = player.bank.count(UNFERMENTED)
        if (banked > 0 && bankHasRoom(player)) {
            val perBatch = banked / BATCHES
            var wine = 0L
            if (perBatch > 0) repeat(BATCHES) { if (roll()) wine += perBatch }
            repeat((banked % BATCHES).toInt()) { if (roll()) wine++ }
            val tabs = world.bankScreen.tabs(player)
            world.bank.take(player.bank, tabs, UNFERMENTED, banked.toInt())
            if (wine > 0) deposit(player, JUG_OF_WINE, wine.toInt())
            if (banked - wine > 0) deposit(player, BAD_WINE, (banked - wine).toInt())
            good += wine
        }
        val wineType = world.objTypes[JUG_OF_WINE] ?: error("No obj type $JUG_OF_WINE")
        val badType = world.objTypes[BAD_WINE] ?: error("No obj type $BAD_WINE")
        for ((slot, obj) in player.inventory.contents()) {
            if (obj.id != UNFERMENTED) continue
            val fermented = roll()
            player.inventory[slot] = InvObj((if (fermented) wineType else badType).id, 1)
            if (fermented) good++
        }
        if (good > 0) player.skills.addTenths(Skill.COOKING, (good * XP_TENTHS).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }

    /**
     * Whether the bank can take the wine and bad wine stacks once the unfermented stack's slot is free: each that has no
     * slot yet needs one, and neither stack may overflow.
     */
    private fun bankHasRoom(player: Player): Boolean {
        val new = listOf(JUG_OF_WINE, BAD_WINE).count { id -> player.bank.contents().none { it.second.id == id } }
        // Each stack must also take every banked wine, should all of them land in it.
        val banked = player.bank.count(UNFERMENTED)
        val fits = listOf(JUG_OF_WINE, BAD_WINE).all { player.bank.count(it) + banked <= Int.MAX_VALUE }
        return fits && world.bank.used(player.bank) - 1 + new <= world.bank.capacity()
    }

    private fun deposit(player: Player, id: Int, count: Int) {
        val from = Inventory(-1, 1)
        from[0] = InvObj(id, count)
        world.bank.deposit(player.bank, world.bankScreen.tabs(player), from, 0, count)
    }

    companion object {
        const val UNFERMENTED = CookingMixes.UNFERMENTED_WINE
        const val JUG_OF_WINE = 1993
        const val BAD_WINE = 1991
        const val FERMENT_TICKS = 20
        const val BATCHES = 15
        const val LOW = 48
        const val HIGH = 352
        const val XP_TENTHS = 2000L
        const val TIMER = "fermenting_wine"
    }
}
