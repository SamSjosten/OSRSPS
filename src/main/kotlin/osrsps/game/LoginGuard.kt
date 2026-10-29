package osrsps.game

/**
 * Slows down password guessing: after [maxWrong] wrong passwords in a row for a name from one address, that name takes
 * no logins from that address for [lockMillis]. Counted by name and address together, so a stranger guessing at a name
 * locks out only themselves, not its owner (the exploit review of 29 Oct 2026 found anyone could lock any account,
 * admins too). The address is the player's own when the relay sends a PROXY header ([osrsps.net.ProxyProtocol]);
 * without one every internet player shares the relay's, and a lock there holds for all of them. Our own rule; live's
 * limits aren't known.
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
        var lastWrong = 0L
    }

    /** By name and address, only those with a wrong password since their last right one. */
    private val strikes = HashMap<Pair<String, String>, Strikes>()

    /** Whether [key] is locked from [address] now; a lock that has run out is forgotten. */
    fun locked(key: String, address: String): Boolean {
        val entry = strikes[key to address] ?: return false
        if (entry.lockedUntil == 0L) return false
        if (now() < entry.lockedUntil) return true
        strikes.remove(key to address)
        return false
    }

    /** Counts a wrong password for [key] from [address], locking the pair on the [maxWrong]th in a row. */
    fun wrongPassword(key: String, address: String) {
        if (strikes.size >= PRUNE_AT) prune()
        val entry = strikes.getOrPut(key to address, ::Strikes)
        entry.lastWrong = now()
        if (++entry.wrong >= maxWrong) entry.lockedUntil = now() + lockMillis
    }

    /** The right password clears [key]'s count from [address]; a guesser elsewhere stays locked. */
    fun rightPassword(key: String, address: String) {
        strikes.remove(key to address)
    }

    /** Forgets every count for [key], as when a reset name is claimed with a new password. */
    fun forget(key: String) {
        strikes.keys.removeIf { it.first == key }
    }

    /** Drops counts with no wrong password for a lock's length and no lock running, so guessing from many addresses can't grow the map without end. */
    private fun prune() {
        val time = now()
        strikes.values.removeIf { time >= it.lockedUntil && time - it.lastWrong >= lockMillis }
    }

    /** How many counts are kept, for tests. */
    internal val size: Int get() = strikes.size

    companion object {
        const val MAX_WRONG = 5
        const val LOCK_MILLIS = 5 * 60 * 1000L
        private const val PRUNE_AT = 4096
    }
}
