package osrsps.game

/**
 * What a player's charged items hold, by name (`blowpipe_scales`), as counts. They belong to the player, not to one
 * item: two of the same item share them. Guessed: live keeps some charges on the item and some on the player, and the
 * Wiki doesn't say which for each. Saved with the account ([PlayerSave]).
 */
class Charges {
    private val counts = LinkedHashMap<String, Int>()

    operator fun get(name: String): Int = counts[name] ?: 0

    /** Sets the count; 0 removes it. */
    operator fun set(name: String, count: Int) {
        require(count >= 0) { "$name can't be $count" }
        if (count == 0) counts -= name else counts[name] = count
    }

    fun contents(): Map<String, Int> = counts.toMap()

    fun clear() = counts.clear()
}
