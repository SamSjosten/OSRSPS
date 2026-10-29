package osrsps.game.npc

/**
 * What content gives NPCs of a type to do on their own, RS2's NPC scripts as RSMod has them: a timer that fires every
 * so many ticks ([onTimer], RSMod's ai timer), something every tick ([onTick]), something when one dies ([onDeath]),
 * and actions queued to run a few ticks later ([queue]). Ducks quack and ducklings follow them this way.
 *
 * [process] runs once a tick in the NPC phase, in NPC index order, timers before queues. A timer counts from the tick
 * the NPC appeared on ([Npc.spawnTick]), so one that respawns in the combat phase keeps the same timing as one put in
 * at startup; a queued action runs [queue]'s delay ticks after the tick it was queued on. Neither runs for an NPC that
 * is dead or has left the world. Only touched on the game thread.
 */
class NpcScripts(private val npcs: Npcs, private val currentTick: () -> Long) {
    private class Timer(val first: Int, val fire: (Npc) -> Int)

    private val timers = HashMap<Int, Timer>()
    private val ticks = HashMap<Int, MutableList<(Npc) -> Unit>>()
    private val deaths = HashMap<Int, MutableList<(Npc) -> Unit>>()

    /**
     * Fires [fire] for every NPC of [types] [first] ticks after it appears, and after that as many ticks later as
     * [fire] returns each time. One timer to a type.
     */
    fun onTimer(types: Collection<Int>, first: Int, fire: (Npc) -> Int) {
        require(first > 0) { "a first timer of $first ticks" }
        for (type in types) {
            require(type !in timers) { "NPC type $type already has a timer" }
            timers[type] = Timer(first, fire)
        }
    }

    /** Runs [action] every tick for every NPC of [types]. */
    fun onTick(types: Collection<Int>, action: (Npc) -> Unit) {
        for (type in types) ticks.getOrPut(type) { ArrayList() } += action
    }

    /** Runs [action] when an NPC of [types] is killed, on the tick of the killing blow. */
    fun onDeath(types: Collection<Int>, action: (Npc) -> Unit) {
        for (type in types) deaths.getOrPut(type) { ArrayList() } += action
    }

    /** Runs [action] for [npc] [delay] ticks from now, unless it has died or left the world by then. */
    fun queue(npc: Npc, delay: Int, action: (Npc) -> Unit) {
        require(delay > 0) { "a queue of $delay ticks" }
        npc.queued += Npc.Queued(currentTick() + delay, action)
    }

    /** [npc] was just killed. */
    fun died(npc: Npc) {
        deaths[npc.spawn.type.id]?.forEach { it(npc) }
    }

    fun process() {
        for (npc in npcs.all.toList()) {
            if (npc.dead || npcs[npc.index] !== npc) continue
            val type = npc.spawn.type.id
            val tick = currentTick()
            timers[type]?.let { timer ->
                if (npc.timerTick < 0) npc.timerTick = npc.spawnTick + timer.first
                if (tick >= npc.timerTick) {
                    val next = timer.fire(npc)
                    require(next > 0) { "a next timer of $next ticks" }
                    npc.timerTick = tick + next
                }
            }
            ticks[type]?.forEach { it(npc) }
            if (npc.queued.isEmpty()) continue
            val due = npc.queued.filter { it.tick <= tick }
            npc.queued.removeAll(due)
            for (queued in due) {
                if (npc.dead || npcs[npc.index] !== npc) break
                queued.action(npc)
            }
        }
    }
}
