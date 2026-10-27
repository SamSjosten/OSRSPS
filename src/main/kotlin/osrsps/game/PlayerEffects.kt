package osrsps.game

import java.util.TreeMap

/**
 * The animation, spotanims, hitsplats, health bars, sound effects and jingle a player was given this cycle, sent at
 * the end of it by [World]: the animation, spotanims, hitsplats and health bars through player info, so everyone who
 * can see the player sees them, the sound effects and jingle to the player alone. Delays are in client cycles (20 ms)
 * after the client gets the update.
 */
class PlayerEffects {
    class Anim(val id: Int, val delay: Int)

    /**
     * A hitsplat from [source] (an NPC's index, a player's plus [osrsps.game.npc.NpcEffects.PLAYER_SOURCE], -1 for
     * none) showing [value]: [selfType] to this player, [sourceType] to the source, [otherType] to everyone else.
     */
    class Hit(val source: Int, val selfType: Int, val sourceType: Int, val otherType: Int, val value: Int, val delay: Int)

    /** Health bar [type] from [source] (as for [Hit]) filled to [fill] of its width. */
    class HeadBar(val source: Int, val type: Int, val fill: Int)

    class SpotAnim(val id: Int, val height: Int, val delay: Int)

    class Sound(val id: Int, val loops: Int, val delay: Int)

    /** The animation to send, or null if none was set. Id -1 stops the current one. */
    var anim: Anim? = null
        private set

    /** By slot. Id -1 clears the slot. */
    private val spotanims = TreeMap<Int, SpotAnim>()

    private val sounds = ArrayList<Sound>()
    private val hits = ArrayList<Hit>()
    private val headBars = ArrayList<HeadBar>()

    var jingle: Int? = null
        private set

    fun hit(source: Int, selfType: Int, sourceType: Int, otherType: Int, value: Int, delay: Int = 0) {
        hits += Hit(source, selfType, sourceType, otherType, value, delay)
    }

    fun headBar(source: Int, type: Int, fill: Int) {
        headBars += HeadBar(source, type, fill)
    }

    /** The hitsplats to send, for tests. */
    val pendingHits: List<Hit> get() = hits

    /** The health bars to send, for tests. */
    val pendingHeadBars: List<HeadBar> get() = headBars

    /**
     * Guessed: the last animation set in a cycle is the one sent. RS2's engine is thought to compare the seq types'
     * priorities here, which needs seq types decoded; that comes when two animations can land on one tick (combat).
     * The client still applies its own priority rules against the animation already playing.
     */
    fun anim(id: Int, delay: Int) {
        require(id == -1 || id in 0..MAX_SHORT) { "Animation id $id must be -1 or 0 to $MAX_SHORT" }
        require(delay in 0..MAX_ANIM_DELAY) { "Animation delay $delay must be 0 to $MAX_ANIM_DELAY client cycles" }
        anim = Anim(id, delay)
    }

    /** One spotanim per slot, as the client keeps them; a later one in the same slot replaces it. */
    fun spotanim(slot: Int, id: Int, height: Int, delay: Int) {
        require(slot in 0 until SPOTANIM_SLOTS) { "Spotanim slot $slot must be 0 to ${SPOTANIM_SLOTS - 1}" }
        require(id == -1 || id in 0..MAX_SHORT) { "Spotanim id $id must be -1 or 0 to $MAX_SHORT" }
        require(height in 0..MAX_SHORT) { "Spotanim height $height must be 0 to $MAX_SHORT" }
        requireDelay(delay)
        spotanims[slot] = SpotAnim(id, height, delay)
    }

    /** Every sound effect is sent, in the order played. */
    fun sound(id: Int, loops: Int, delay: Int) {
        require(id in 0..MAX_SHORT) { "Sound id $id must be 0 to $MAX_SHORT" }
        require(loops in 0..MAX_LOOPS) { "Sound loops $loops must be 0 to $MAX_LOOPS" }
        requireDelay(delay)
        sounds += Sound(id, loops, delay)
    }

    /** The client plays one jingle at a time, so only the last one in a cycle is sent. */
    fun jingle(id: Int) {
        require(id in 0..MAX_SHORT) { "Jingle id $id must be 0 to $MAX_SHORT" }
        jingle = id
    }

    /** The spotanims to send, by slot, for tests. */
    val pendingSpotanims: Map<Int, SpotAnim> get() = spotanims

    /** The sound effects to send, for tests. */
    val pendingSounds: List<Sound> get() = sounds

    /** Hands everything set since the last flush to the senders and forgets it. */
    fun flush(
        anim: (Anim) -> Unit,
        spotanim: (slot: Int, SpotAnim) -> Unit,
        sound: (Sound) -> Unit,
        jingle: (Int) -> Unit,
        hit: (Hit) -> Unit = {},
        headBar: (HeadBar) -> Unit = {},
    ) {
        // Forgotten before sending, so a failed send can't repeat them next cycle.
        val sendAnim = this.anim
        val sendSpots = spotanims.toList()
        val sendSounds = sounds.toList()
        val sendJingle = this.jingle
        val sendHits = hits.toList()
        val sendBars = headBars.toList()
        this.anim = null
        spotanims.clear()
        sounds.clear()
        this.jingle = null
        hits.clear()
        headBars.clear()
        sendAnim?.let(anim)
        for ((slot, spot) in sendSpots) spotanim(slot, spot)
        sendSounds.forEach(sound)
        sendJingle?.let(jingle)
        sendHits.forEach(hit)
        sendBars.forEach(headBar)
    }

    // Checked here rather than when sent, so a bad value fails the script that set it, not the whole cycle's updates.
    private fun requireDelay(delay: Int) = require(delay in 0..MAX_SHORT) { "Delay $delay must be 0 to $MAX_SHORT client cycles" }

    companion object {
        private const val MAX_SHORT = 65535
        private const val MAX_LOOPS = 255

        /** An animation's delay goes in one byte of player info (RSProt's PlayerSequenceEncoder); the others take two. */
        const val MAX_ANIM_DELAY = 255

        /** The client's spotanim slots on one player, as RSProt sizes them. */
        const val SPOTANIM_SLOTS = 256
    }
}
