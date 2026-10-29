package osrsps.game.npc

/**
 * What an NPC was given this cycle for everyone who sees it, sent at the end of the cycle by [osrsps.game.World]
 * through NPC info: an animation, spotanims, hitsplats, health bars and overhead text. Kept apart from the avatar so tests, which have no
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

    /** Spotanim [id] at [height] above the ground, in its [slot] (see [spotanim]). */
    class SpotAnim(val slot: Int, val id: Int, val height: Int, val delay: Int)

    /** The animation to send, or null if none was set; the last one set in a cycle wins, as for players. */
    var anim: Anim? = null
        private set

    /** The overhead text to send, or null for none; the last one set in a cycle wins. */
    var say: String? = null
        private set

    private val hits = ArrayList<Hit>()
    private val headBars = ArrayList<HeadBar>()
    private val spotanims = LinkedHashMap<Int, SpotAnim>()

    fun anim(id: Int, delay: Int = 0) {
        anim = Anim(id, delay)
    }

    /** Shows [text] over the NPC's head, as a duck's quack ([osrsps.game.content.DuckLines]); RSProt's NPC say block. */
    fun say(text: String) {
        say = text
    }

    fun hit(source: Int, selfType: Int, otherType: Int, value: Int, delay: Int = 0) {
        hits += Hit(source, selfType, otherType, value, delay)
    }

    fun headBar(source: Int, type: Int, fill: Int) {
        headBars += HeadBar(source, type, fill)
    }

    /** One spotanim per slot, as for players: a later one in the same slot this cycle replaces it. */
    fun spotanim(slot: Int, id: Int, height: Int, delay: Int = 0) {
        require(slot in 0 until SPOTANIM_SLOTS) { "Spotanim slot $slot must be 0 to ${SPOTANIM_SLOTS - 1}" }
        require(id == -1 || id in 0..MAX_SHORT) { "Spotanim id $id must be -1 or 0 to $MAX_SHORT" }
        require(height in 0..MAX_SHORT) { "Spotanim height $height must be 0 to $MAX_SHORT" }
        require(delay in 0..MAX_SHORT) { "Spotanim delay $delay must be 0 to $MAX_SHORT client cycles" }
        spotanims[slot] = SpotAnim(slot, id, height, delay)
    }

    /** The hitsplats to send, for tests. */
    val pendingHits: List<Hit> get() = hits

    /** The health bars to send, for tests. */
    val pendingHeadBars: List<HeadBar> get() = headBars

    /** The spotanims to send, by slot, for tests. */
    val pendingSpotanims: List<SpotAnim> get() = spotanims.values.toList()

    /** Hands everything set since the last flush to the senders and forgets it. */
    fun flush(anim: (Anim) -> Unit, hit: (Hit) -> Unit, headBar: (HeadBar) -> Unit, spotanim: (SpotAnim) -> Unit = {}, say: (String) -> Unit = {}) {
        val sendAnim = this.anim
        val sendSay = this.say
        val sendHits = hits.toList()
        val sendBars = headBars.toList()
        val sendSpotanims = spotanims.values.toList()
        this.anim = null
        this.say = null
        hits.clear()
        headBars.clear()
        spotanims.clear()
        sendAnim?.let(anim)
        sendHits.forEach(hit)
        sendBars.forEach(headBar)
        sendSpotanims.forEach(spotanim)
        sendSay?.let(say)
    }

    companion object {
        /** Added to a player's index as a hitsplat's or health bar's source, as RSProt's NPC info wants it. */
        const val PLAYER_SOURCE = 0x10000

        /** As for players: the client keeps one spotanim in each of 256 slots. */
        const val SPOTANIM_SLOTS = 256

        private const val MAX_SHORT = 0xFFFF
    }
}
