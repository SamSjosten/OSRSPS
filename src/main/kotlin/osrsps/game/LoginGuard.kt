package osrsps.game

/**
 * Slows down password guessing: after [maxWrong] wrong passwords in a row for a name, that name takes no logins for
 * [lockMillis]. Counted by name, not by address: every player from the internet reaches us through one relay address,
 * so an address limit would lock them all out at once. Our own rule; live's limits aren't known.
 *
 * Names are save keys ([SaveStore.saveKey]). Used only from the one login-auth thread, so it needs no locking.
 */
class LoginGuard(
    private val now: () -> Long = System::currentTimeMillis,
    private val maxWrong: Int = MAX_WRONG,
    private val lockMillis: Long = LOCK_MILLIS,
) {
    private class Strikes {
        var wrong = 0
        var lockedUntil = 0L
    }

    /** Only names with a wrong password since their last right one, so it grows no larger than the accounts do. */
    private val strikes = HashMap<String, Strikes>()

    /** Whether [key] is locked now; a lock that has run out is forgotten. */
    fun locked(key: String): Boolean {
        val entry = strikes[key] ?: return false
        if (entry.lockedUntil == 0L) return false
        if (now() < entry.lockedUntil) return true
        strikes.remove(key)
        return false
    }

    /** Counts a wrong password for [key], locking it on the [maxWrong]th in a row. */
    fun wrongPassword(key: String) {
        val entry = strikes.getOrPut(key, ::Strikes)
        if (++entry.wrong >= maxWrong) entry.lockedUntil = now() + lockMillis
    }

    /** The right password clears [key]'s count. */
    fun rightPassword(key: String) {
        strikes.remove(key)
    }

    companion object {
        const val MAX_WRONG = 5
        const val LOCK_MILLIS = 5 * 60 * 1000L
    }
}
