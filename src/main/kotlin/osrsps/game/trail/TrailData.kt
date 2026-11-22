package osrsps.game.trail

/** A Treasure Trail's tier, with its shared clue item (beginner and master), its scroll box and its reward casket. */
enum class TrailTier(
    val key: String,
    /** The obj param set to 1 on every clue scroll of the tier (osrs-dumps' dump.obj, rev 242). */
    val param: Int,
    /** Trail length, inclusive (OSRS Wiki, Treasure Trails, read 2026-11-19). */
    val minSteps: Int,
    val maxSteps: Int,
    /** `league_clue_box_<tier>`, the Scroll box (tier) X Marks the Spot gives in place of clue scrolls. */
    val scrollBox: Int,
    /** `trail_reward_casket_<tier>`. */
    val rewardCasket: Int,
    /** `trail_clue_beginner` and `trail_clue_master`: one item for every step, the step kept on the player. -1 for the tiers whose every step is its own item. */
    val sharedClue: Int = -1,
) {
    BEGINNER("beginner", 726, 1, 3, 24361, 23245, sharedClue = 23182),
    EASY("easy", 347, 2, 4, 24362, 20546),
    MEDIUM("medium", 348, 3, 5, 24363, 20545),
    HARD("hard", 349, 4, 6, 24364, 20544),
    ELITE("elite", 350, 5, 7, 24365, 20543),
    MASTER("master", 393, 6, 8, 24366, 19836, sharedClue = 19835),
    ;

    companion object {
        /** The cluehelper tables' difficulty column: 0 beginner to 5 master (6 is X Marks the Spot's own clues). */
        fun byDifficulty(difficulty: Int): TrailTier? = entries.getOrNull(difficulty)

        fun byKey(key: String): TrailTier? = entries.firstOrNull { it.key == key.lowercase() }
    }
}

/** A clue step's type: which cluehelper table it is a row of (osrs-dumps' dump.dbtable, rev 242). */
enum class StepType(val key: String, val table: Int) {
    ANAGRAM("anagram", 4),
    MAP("map", 5),
    CIPHER("cipher", 6),
    COORDINATE("coordinate", 7),
    CRYPTIC("cryptic", 8),
    EMOTE("emote", 9),
    FAIRY_RING("fairyring", 10),
    FALO("falobard", 11),
    HOT_COLD("hotcold", 12),
    MUSIC("music", 13),
    SKILL_CHALLENGE("skillchallenge", 14),
    ;

    companion object {
        fun byTable(table: Int): StepType? = entries.firstOrNull { it.table == table }
    }
}

/** A tile: the cache's packed coord (level shl 28, x shl 14, z). */
data class TrailTile(val x: Int, val z: Int, val level: Int) {
    override fun toString(): String = "$x,$z,$level"

    companion object {
        fun unpack(coord: Int) = TrailTile((coord ushr 14) and 0x3FFF, coord and 0x3FFF, (coord ushr 28) and 0x3)
    }
}

/** Where a step is solved: one of the cluehelper target tables (15 to 20). */
sealed interface TrailTarget {
    /** The tile the target stands on, or the area's south-west corner. */
    val tile: TrailTile

    /** Dig on [tile] (cluehelper_target_coord). */
    data class Dig(override val tile: TrailTile) : TrailTarget

    /** Talk to [npc] (or [fallbackNpc]) near [tile] (cluehelper_target_npc). */
    data class Npc(val npc: Int, val fallbackNpc: Int, override val tile: TrailTile) : TrailTarget

    /** Search [loc] (or [fallbackLoc]) on [tile] (cluehelper_target_loc). */
    data class Loc(val loc: Int, val fallbackLoc: Int, override val tile: TrailTile) : TrailTarget

    /** Somewhere between [tile] and [northEast] (cluehelper_target_mapzone). */
    data class Zone(override val tile: TrailTile, val northEast: TrailTile) : TrailTarget

    /** Search [loc] on [tile] holding [key], which one of [npcs] drops (cluehelper_target_key). */
    data class Key(val loc: Int, override val tile: TrailTile, val npcs: List<Int>, val key: Int) : TrailTarget

    /** Kill one of [npcs] (cluehelper_target_kill). */
    data class Kill(val npcs: List<Int>, override val tile: TrailTile) : TrailTarget
}

/**
 * What an emote step has the player wear (cluehelper_outfit): for some equipment slots, any of [items]; for others, any
 * item of an item group ([groups], the group in obj param 258, as every charge of a ring of dueling); and items of
 * [anyGroups] in any slot. [description] is the clue helper's words for it.
 */
data class TrailOutfit(
    val description: String,
    val items: Map<Int, Set<Int>> = emptyMap(),
    val groups: Map<Int, Int> = emptyMap(),
    val anyGroups: List<Int> = emptyList(),
) {
    /** Whether [worn] (an item id by equipment slot, null for an empty slot) and [groupOf] (an item's group) fit it. */
    fun fits(worn: (Int) -> Int?, groupOf: (Int) -> Int?): Boolean {
        for ((slot, ids) in items) if (worn(slot) !in ids) return false
        for ((slot, group) in groups) if (worn(slot)?.let(groupOf) != group) return false
        val all = (0 until EQUIPMENT_SLOTS).mapNotNull(worn)
        for (group in anyGroups) if (all.none { groupOf(it) == group }) return false
        return true
    }

    companion object {
        const val EQUIPMENT_SLOTS = 14
        /** The obj param naming an item's Treasure Trail item group. */
        const val PARAM_ITEM_GROUP = 258
    }
}

/** What a step's target asks before it gives the next clue (the clue helper's challenge column). */
sealed interface TrailChallenge {
    /** A question to answer with a number, any of [answers] right (cluehelper_challenge_question). */
    data class Question(val text: String, val answers: Set<Int>) : TrailChallenge

    /** A puzzle box or light box to solve (cluehelper_challenge_box). */
    data class Box(val description: String) : TrailChallenge
}

/**
 * One clue step, as Jagex's own clue helper lists it in the cache: [id] is the row's own id column (stable across
 * caches, unlike the row's number), [text] the clue's words (null for maps and hot-cold steps, which show no text),
 * [items] the step's own clue scroll items (empty for beginner and master steps, which share one), [combat] whether a
 * fight comes with it (a coordinate's wizard, an emote's double agent), [challenge] what the target asks first and
 * [challengeItem] the challenge scroll that carries a question (-1 for none).
 */
data class ClueStep(
    val type: StepType,
    val id: Int,
    val tier: TrailTier,
    val text: String?,
    val targets: List<TrailTarget>,
    val items: List<Int> = emptyList(),
    val combat: Boolean = false,
    val challenge: TrailChallenge? = null,
    val challengeItem: Int = -1,
    /** An emote step's emotes, in order (the emote tab's indexes): the first calls Uri, the second comes before talking to him. */
    val emotes: List<Int> = emptyList(),
    /** What an emote step has the player wear: any one of these, none for no rule. */
    val outfits: List<TrailOutfit> = emptyList(),
    /** The STASH unit for the step's items (the clue helper's hidey_hole_loc), -1 for none. */
    val stash: Int = -1,
) {
    /** How a save and `::clue` name the step: `map:107`. */
    val key: String get() = "${type.key}:$id"

    override fun toString(): String = "$key (${tier.key})"
}

/** Every clue step the cache lists, by key, and the clue items that are one step each. */
class TrailData(steps: Collection<ClueStep>) {
    val steps: Map<String, ClueStep> = steps.associateBy { it.key }

    /** The step each own-step clue item is. */
    val byItem: Map<Int, ClueStep> = buildMap { for (step in steps) for (item in step.items) put(item, step) }

    operator fun get(key: String): ClueStep? = steps[key]

    fun tier(tier: TrailTier): List<ClueStep> = steps.values.filter { it.tier == tier }

    companion object {
        val NONE = TrailData(emptyList())
    }
}
