package osrsps.game

/**
 * The graceful outfit's run energy bonus, by the OSRS Wiki (Graceful outfit and Energy, read 2026-11-02): each piece
 * worn makes energy come back faster (hood 3%, top 4%, legs 4%, gloves 3%, boots 3%, cape 3%: 20% for all six), and the
 * whole set adds 10% more, 30% in all. The Agility cape counts as the graceful cape for the set.
 *
 * Pieces are known by their names in the cache, so every colour counts: `graceful_hood`, `graceful_hood_hallowed`,
 * `zeah_graceful_hood_arceuus` and so on, never their `_worn` objs (which no one holds). Inferred: the Agility cape
 * also gives the cape's own 3%, as the cape it stands in for; the trimmed cape counts as the untrimmed one.
 */
class Graceful(private val objTypes: ObjTypes) {
    /** The bonus each graceful item gives, by id. */
    private val bonus: Map<Int, Piece> by lazy {
        val map = HashMap<Int, Piece>()
        for (id in 0 until objTypes.size) {
            val name = objTypes[id]?.internalName ?: continue
            pieceOf(name)?.let { map[id] = it }
        }
        map
    }

    /** The percentage faster [player]'s energy comes back for what they wear: 0 to [FULL_SET]. */
    fun restorePercent(player: Player): Int {
        var percent = 0
        var worn = 0
        for ((_, obj) in player.equipment.contents()) {
            val piece = bonus[obj.id] ?: continue
            if (worn and (1 shl piece.ordinal) != 0) continue
            worn = worn or (1 shl piece.ordinal)
            percent += piece.percent
        }
        return if (worn == ALL) FULL_SET else percent
    }

    enum class Piece(val percent: Int) { HOOD(3), TOP(4), LEGS(4), GLOVES(3), BOOTS(3), CAPE(3) }

    companion object {
        /** All six pieces: their 20% and the set's 10%. */
        const val FULL_SET = 30
        private val ALL = (1 shl Piece.entries.size) - 1

        private val NAME = Regex("""(?:zeah_)?graceful_(hood|top|legs|gloves|boots|cape)(?:_[a-z]+)?""")

        /** The piece an obj called [internalName] is, or null. */
        fun pieceOf(internalName: String): Piece? {
            if (internalName.endsWith("_worn")) return null
            if (internalName == "skillcape_agility" || internalName == "skillcape_agility_trimmed") return Piece.CAPE
            val match = NAME.matchEntire(internalName) ?: return null
            return Piece.valueOf(match.groupValues[1].uppercase())
        }

        /** [restore] units a tick made [percent] faster, rounded down: ⌊1.3 × R⌋ for the whole set, as the Energy page has it. */
        fun boosted(restore: Int, percent: Int): Int = restore * (100 + percent) / 100
    }
}
