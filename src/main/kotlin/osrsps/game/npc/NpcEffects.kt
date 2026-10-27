package osrsps.game.npc

/**
 * What an NPC was given this cycle for everyone who sees it, sent at the end of the cycle by [osrsps.game.World]
 * through NPC info: an animation, hitsplats and health bars. Kept apart from the avatar so tests, which have no
 * network, can read them. Delays are in client cycles (20 ms).
 */
class NpcEffects {
    class Anim(val id: Int, val delay: Int)

    /**
     * A hitsplat from [source] (a player's index plus [PLAYER_SOURCE], an NPC's as is, -1 for none) showing [value]:
     * [selfType] to the source and the NPC's own viewers, [otherType] to everyone else.
     */
    class Hit(val source: Int, val selfType: Int, val otherType: Int, val value: Int, val delay: Int)

    /** Health bar [type] from [source] (as for [Hit]) filled to [fill] of its width. */
    class HeadBar(val source: Int, val type: Int, val fill: Int)

    /** The animation to send, or null if none was set; the last one set in a cycle wins, as for players. */
    var anim: Anim? = null
        private set

    private val hits = ArrayList<Hit>()
    private val headBars = ArrayList<HeadBar>()

    fun anim(id: Int, delay: Int = 0) {
        anim = Anim(id, delay)
    }

    fun hit(source: Int, selfType: Int, otherType: Int, value: Int, delay: Int = 0) {
        hits += Hit(source, selfType, otherType, value, delay)
    }

    fun headBar(source: Int, type: Int, fill: Int) {
        headBars += HeadBar(source, type, fill)
    }

    /** The hitsplats to send, for tests. */
    val pendingHits: List<Hit> get() = hits

    /** The health bars to send, for tests. */
    val pendingHeadBars: List<HeadBar> get() = headBars

    /** Hands everything set since the last flush to the senders and forgets it. */
    fun flush(anim: (Anim) -> Unit, hit: (Hit) -> Unit, headBar: (HeadBar) -> Unit) {
        val sendAnim = this.anim
        val sendHits = hits.toList()
        val sendBars = headBars.toList()
        this.anim = null
        hits.clear()
        headBars.clear()
        sendAnim?.let(anim)
        sendHits.forEach(hit)
        sendBars.forEach(headBar)
    }

    companion object {
        /** Added to a player's index as a hitsplat's or health bar's source, as RSProt's NPC info wants it. */
        const val PLAYER_SOURCE = 0x10000
    }
}
