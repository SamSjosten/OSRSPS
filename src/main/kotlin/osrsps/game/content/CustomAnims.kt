package osrsps.game.content

import osrsps.cache.SeqCodec
import osrsps.cache.SpotanimCodec
import osrsps.cache.overlay.CacheOverlay

/**
 * Animations and graphics of our own: sequences (config archive 12) and spotanims (13) with ids above the cache's
 * (14,520 and 4,022 at most in the 241 cache; ours start at 65,000 and stop short of the 65,535 that means none on
 * the wire), encoded as the cache holds them and served to the client through the cache overlay, as the loc types
 * are. The client has no frames of ours, so a sequence of ours plays frames of the cache's own player animations, at
 * our pace and in our order (as the cache's 715 plays 714's backwards), and a spotanim of ours is a cache graphic's
 * model with its colours swapped.
 *
 * The lodestone cast is the first (the lodestones' step 8; Sam's yes on 8 Nov 2026). RS3's Home Teleport (the RS3
 * Wiki, read 8 Nov 2026): the player stands on the pedestal briefly, then "flies into the air as white pulse and
 * flies back down at the lodestone". RS3's own frames can't be converted, so the cast is OSRS's Tele Other, the one
 * teleport whose player floats up, drawn white-cyan instead of blue: [LODESTONE_CHARGE], the cast pose with the arms
 * raised (the standard teleport's first frames, 714, held) while [LODESTONE_PULSE] builds around the player and
 * stays, then [LODESTONE_RISE] (Tele Other's recipient, 1816) with [LODESTONE_FLASH], the whole swirl, in the cast's
 * last ticks, so the player flies up and is gone ([Lodestones.STAGES]); on the arrival tile, [LODESTONE_LAND], the
 * same frames backwards with [LODESTONE_LAND_FLASH], so the player comes down at the stone ([Lodestones.LANDING]).
 * Which frames the cache's sequences play, how
 * they set the hands and the vertical offset, and which colour its own recoloured variants swap were read from the
 * 241 cache; the pace, the held frames and the shade of white are guesses for a look in game to settle.
 */
object CustomAnims {
    const val FIRST_ID = 65000
    const val MAX_ID = 0xFFFE

    /** Sequences: the player's two stages and the landing, the pulse graphic's own (the swirl building, then held) and the landing graphic's (the swirl backwards). */
    const val LODESTONE_CHARGE = 65000
    const val LODESTONE_RISE = 65001
    const val LODESTONE_PULSE_SEQ = 65002
    const val LODESTONE_LAND = 65003
    const val LODESTONE_LAND_FLASH_SEQ = 65004

    /** Spotanims: the glow that builds and stays, the whole swirl at the rise, and the swirl backwards at the landing. */
    const val LODESTONE_PULSE = 65000
    const val LODESTONE_FLASH = 65001
    const val LODESTONE_LAND_FLASH = 65002

    /** The cache's frame groups the cast plays: the standard teleport's (sequence 714), Tele Other's recipient's (1816) and Tele Other's graphic's (1813). */
    const val CAST_TELEPORT_FRAMES = 11003
    const val TELE_OTHER_FRAMES = 11679
    const val TELE_OTHER_GRAPHIC_FRAMES = 7588

    /** Tele Other's graphic (spotanim 342): its model, and the sequence the cache plays it with. */
    const val TELE_OTHER_GRAPHIC_MODEL = 5802
    const val TELE_OTHER_GRAPHIC_SEQ = 1813

    /** The cache's teleports hide the weapon and shield (opcodes 6 and 7 at 0), set the vertical offset to 8 and a forced priority of 6. */
    const val HANDS_EMPTY = 0
    const val PLAYER_VERTICAL_OFFSET = 8
    const val TELEPORT_PRIORITY = 6

    /** Tele Other's blue, the colour the cache's own cyan and dark variants (2059, 2285) swap out, and the white-cyan ours draws it as (the shade guessed). */
    const val TELE_OTHER_BLUE = 49969
    val LODESTONE_WHITE = SpotanimCodec.hsl(29, 2, 100)

    /** A frame held this many client cycles (40 s) outlasts any cast, so the pose or the glow stays until the teleport ends it. */
    const val HOLD = 2000

    private fun files(group: Int, range: IntProgression) = range.map { SeqCodec.frame(group, it) }

    /** The standard teleport's arms rising (714's first five frames, 6 cycles each as the cache plays them), the raised pose held. */
    val CHARGE = SeqCodec.Definition(
        frames = files(CAST_TELEPORT_FRAMES, 1..5),
        delays = listOf(6, 6, 6, 6, HOLD),
        priority = TELEPORT_PRIORITY, leftHand = HANDS_EMPTY, rightHand = HANDS_EMPTY, verticalOffset = PLAYER_VERTICAL_OFFSET,
    )

    /** Tele Other's recipient (1816) as the cache plays it: 16 frames at 4 cycles, the floating pose held. */
    val RISE = SeqCodec.Definition(
        frames = files(TELE_OTHER_FRAMES, 1..17),
        delays = List(16) { 4 } + HOLD,
        priority = TELEPORT_PRIORITY, leftHand = HANDS_EMPTY, rightHand = HANDS_EMPTY, verticalOffset = PLAYER_VERTICAL_OFFSET,
    )

    /** The rise backwards at the cache's pace (as 715 plays 714 backwards): the player comes down from the float and stands, 1.4 s. */
    val LAND = SeqCodec.Definition(
        frames = files(TELE_OTHER_FRAMES, 17 downTo 1),
        delays = List(17) { 4 },
        priority = TELEPORT_PRIORITY, leftHand = HANDS_EMPTY, rightHand = HANDS_EMPTY, verticalOffset = PLAYER_VERTICAL_OFFSET,
    )

    /** The swirl's first nine frames slowly (the cache plays them at 4), the ninth held: a glow that builds and stays. */
    val PULSE_SEQ = SeqCodec.Definition(frames = files(TELE_OTHER_GRAPHIC_FRAMES, 1..9), delays = List(8) { 10 } + HOLD)

    /** The whole swirl backwards at the cache's pace: the pulse closing in as the player lands. */
    val LAND_FLASH_SEQ = SeqCodec.Definition(frames = files(TELE_OTHER_GRAPHIC_FRAMES, 17 downTo 1), delays = List(17) { 4 })

    private fun teleOtherGraphic(seq: Int) = SpotanimCodec.Definition(
        model = TELE_OTHER_GRAPHIC_MODEL, seq = seq, ambient = 100, contrast = 100, recolours = listOf(TELE_OTHER_BLUE to LODESTONE_WHITE),
    )

    val PULSE = teleOtherGraphic(LODESTONE_PULSE_SEQ)
    val FLASH = teleOtherGraphic(TELE_OTHER_GRAPHIC_SEQ)
    val LAND_FLASH = teleOtherGraphic(LODESTONE_LAND_FLASH_SEQ)

    val seqs: Map<Int, SeqCodec.Definition> = mapOf(
        LODESTONE_CHARGE to CHARGE, LODESTONE_RISE to RISE, LODESTONE_PULSE_SEQ to PULSE_SEQ, LODESTONE_LAND to LAND, LODESTONE_LAND_FLASH_SEQ to LAND_FLASH_SEQ,
    )
    val spotanims: Map<Int, SpotanimCodec.Definition> = mapOf(LODESTONE_PULSE to PULSE, LODESTONE_FLASH to FLASH, LODESTONE_LAND_FLASH to LAND_FLASH)

    init {
        require(seqs.keys.all { it in FIRST_ID..MAX_ID } && spotanims.keys.all { it in FIRST_ID..MAX_ID }) { "A custom animation id is $FIRST_ID to $MAX_ID" }
        for ((id, spotanim) in spotanims) require(spotanim.seq < FIRST_ID || spotanim.seq in seqs) { "Spotanim $id plays sequence ${spotanim.seq}, neither the cache's nor ours" }
    }

    /** What the overlay adds to the config index: our sequences in the sequences group, our spotanims in theirs. */
    val overlayFiles: List<CacheOverlay.File> =
        seqs.entries.sortedBy { it.key }.map { (id, seq) -> CacheOverlay.File(SeqCodec.INDEX, SeqCodec.GROUP, id, SeqCodec.encode(seq)) } +
            spotanims.entries.sortedBy { it.key }.map { (id, spotanim) -> CacheOverlay.File(SpotanimCodec.INDEX, SpotanimCodec.GROUP, id, SpotanimCodec.encode(spotanim)) }
}
