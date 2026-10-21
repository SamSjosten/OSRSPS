package osrsps.game

/**
 * The fixed order of a game tick. Each phase runs for every player before the next phase starts.
 *
 * Guessed: live interleaves players (in PID order) and NPCs in ways we have not checked yet; compare against the
 * Tick Recorder recordings before relying on same-tick ordering between players.
 */
enum class TickPhase {
    /** The world's own timers: ground objs turning public, disappearing and respawning. */
    WORLD,

    /** Logins, disconnects, then each player's packets from the client. */
    INPUT,

    /** Each player's delayed script, queues and timers; see [osrsps.game.script.ScriptRunner]. */
    SCRIPTS,
    MOVEMENT,

    /** Player and NPC info, then each client's packets are flushed. */
    UPDATES,
}

/**
 * Decides whether each 600 ms cycle of the game thread is a game tick. Running, every cycle is a tick. Paused, a cycle
 * only serves the clients (logins, commands, the connection) until [step] asks for ticks or [resume] is called.
 *
 * Only touched on the game thread.
 */
class TickClock {
    var paused: Boolean = false
        private set

    /** Ticks [step] asked for that haven't run yet. */
    var stepsLeft: Int = 0
        private set

    /** Pauses once the current tick, if any, has finished. Returns the number of queued steps it dropped. */
    fun pause(): Int {
        val dropped = stepsLeft
        paused = true
        stepsLeft = 0
        return dropped
    }

    fun resume() {
        paused = false
        stepsLeft = 0
    }

    /** Runs [ticks] more ticks while paused, one a cycle, so the client sees them at the normal pace. */
    fun step(ticks: Int) {
        check(paused) { "The clock is running" }
        require(ticks > 0) { "Can't step $ticks ticks" }
        stepsLeft += ticks
    }

    /** Called once a cycle. True when this cycle is a tick. */
    fun advance(): Boolean {
        if (!paused) return true
        if (stepsLeft == 0) return false
        stepsLeft--
        return true
    }
}
