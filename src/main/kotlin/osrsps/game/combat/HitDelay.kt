package osrsps.game.combat

/**
 * Ticks from an attack to its hitsplat, by how the attack travels, with [distance] the Chebyshev distance edge to edge
 * ([osrsps.map.LineOfSight.distance]). From the OSRS Wiki's Hit delay page; the magic formula is the one it calls likely.
 *
 * These are the delays as the attacker sees them. The Wiki adds a tick when the target is processed before the attacker
 * in the tick, as NPCs are before players, so a player's hit on an NPC lands a tick later than the formula; that tick is
 * [ON_NPC] and is added where the hit is queued, not here.
 */
enum class HitDelay(private val delay: (Int) -> Int) {
    /** Lands on the tick it is rolled. */
    MELEE({ 0 }),

    /** Bows and crossbows: 1 + (3 + distance) / 6. */
    BOW({ 1 + (3 + it) / 6 }),

    /** Thrown weapons (darts, knives, thrownaxes, the blowpipe) and chinchompas: 1 + distance / 6. */
    THROWN({ 1 + it / 6 }),

    /** Spells and powered staves: 1 + (1 + distance) / 3. */
    MAGIC({ 1 + (1 + it) / 3 }),
    ;

    fun ticks(distance: Int): Int = delay(distance.coerceAtLeast(0))

    companion object {
        /** The tick a player's hit on an NPC waits because NPCs are processed first (Wiki, Hit delay). */
        const val ON_NPC = 1
    }
}
