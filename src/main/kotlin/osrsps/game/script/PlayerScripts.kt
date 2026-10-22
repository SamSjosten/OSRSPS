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

    /** The script waiting for a click on a dialogue's continue or one of its options, if any. */
    internal var paused: PausedScript? = null

    /** A script waits for the player to continue a dialogue or pick an option. */
    val waitingForDialogue: Boolean get() = paused != null

    /** Drops the script waiting for a dialogue click, which never carries on. Returns whether there was one. */
    internal fun dropPaused(): Boolean {
        val had = paused != null
        paused = null
        return had
    }

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

/**
 * A script waiting for the client's resume_pausebutton on component [component] (packed), with a slot in [subs]: a
 * dialogue's "Click here to continue" (slot -1) or a chat menu option (slots 1 up).
 */
class PausedScript(val component: Int, val subs: IntRange, internal val continuation: Continuation<Int>)
