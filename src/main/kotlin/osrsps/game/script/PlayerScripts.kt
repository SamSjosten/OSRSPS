package osrsps.game.script

import kotlin.coroutines.Continuation

/** A player's queues, timers and delay. Only touched on the game thread, through [ScriptRunner]. */
class PlayerScripts {
    /** Normal, strong and soft scripts, oldest first. */
    internal val queue = ArrayList<QueuedScript>()
    internal val weakQueue = ArrayList<QueuedScript>()

    /** By name, in the order they were first set. */
    internal val timers = LinkedHashMap<String, ScriptTimer>()

    /** The script waiting out a [ScriptContext.delay], if any. */
    internal var suspended: Continuation<Unit>? = null

    /** The tick [suspended] carries on in. */
    var delayedUntil: Long = 0
        internal set

    /** A script has the player waiting out a delay. */
    val delayed: Boolean get() = suspended != null

    val queued: List<QueuedScript> get() = queue + weakQueue
    val activeTimers: Collection<ScriptTimer> get() = timers.values
}

class QueuedScript(val name: String, val type: QueueType, delay: Int, internal val script: Script) {
    /** Script phases left before it is due; it is due once this is 0 or less. */
    var delay: Int = delay
        internal set

    /** The script phase that last looked at it. */
    internal var lastPass: Long = -1
}

class ScriptTimer(val name: String, val type: TimerType, val interval: Int, internal val script: Script) {
    /** Counts down once a timer phase; the timer fires when it reaches 0 and starts again from [interval]. */
    var clock: Int = interval
        internal set
}
