package osrsps.game.content

import osrsps.game.Player
import osrsps.game.skill.LightSources

/**
 * A box of tiles a task has to be done in, on [level] or on any level when it is null. Each is Quest Helper's zone for
 * the task where it has one (its achievementdiaries helpers, read 2026-11-19), so inferred, not checked on live; the
 * rest are guessed, as noted.
 */
class DiaryZone(private val minX: Int, private val minZ: Int, private val maxX: Int, private val maxZ: Int, private val level: Int? = null) {
    fun has(player: Player): Boolean =
        player.x in minX..maxX && player.z in minZ..maxZ && (level == null || player.level == level)

    companion object {
        /**
         * Guessed: Lumbridge as the Wiki's map draws it, from the swamp's west edge to the River Lum and from the swamp to
         * the farms north of the castle, every floor.
         */
        val LUMBRIDGE = DiaryZone(3136, 3136, 3267, 3327)

        /** Guessed: Draynor Village, from the market to the jail, west of the manor's woods. */
        val DRAYNOR = DiaryZone(3072, 3226, 3135, 3290)

        /** Guessed: Al Kharid, the town and its shore south of the mine, east of the toll gate. */
        val AL_KHARID = DiaryZone(3264, 3136, 3335, 3240)

        /** Guessed: the Al Kharid mine, north of the town (Quest Helper's iron rock is 3303,3284). */
        val AL_KHARID_MINE = DiaryZone(3285, 3270, 3310, 3320, 0)

        /** Guessed: the castle kitchen, round Quest Helper's range at 3212,3216. */
        val LUMBRIDGE_KITCHEN = DiaryZone(3205, 3212, 3212, 3217, 0)

        val LUMBRIDGE_COW_PEN = DiaryZone(3253, 3255, 3265, 3298, 0)
        val DRAYNOR_SEWERS = DiaryZone(3077, 9641, 3132, 9699, 0)
        val LUMBRIDGE_SWAMP_CAVES = DiaryZone(3140, 9537, 3261, 9602, 0)
        val FIRE_ALTAR = DiaryZone(2553, 4802, 2623, 4863, 0)
        val LUMBRIDGE_BASEMENT = DiaryZone(3206, 9613, 3221, 9626, 0)
        val WISE_OLD_MAN_HOUSE = DiaryZone(3087, 3251, 3094, 3255, 0)

        /** Guessed: the mine south-east of Varrock, round its rocks at 3281 to 3290, 3360 to 3371. */
        val VARROCK_SE_MINE = DiaryZone(3278, 3357, 3294, 3374, 0)

        /** Guessed: the Lumber Yard inside its fence, with the sawmill. */
        val LUMBER_YARD = DiaryZone(3294, 3486, 3330, 3520, 0)

        /** Guessed: Barbarian Village, from the river to the longhall, every floor. */
        val BARBARIAN_VILLAGE = DiaryZone(3072, 3400, 3112, 3447)

        /** Guessed: round Quest Helper's altar at 3377,3285, in the Emir's Arena's prayer room. */
        val EMIRS_ARENA_ALTAR = DiaryZone(3370, 3278, 3384, 3292, 0)

        /** The top of Varrock's church: Quest Helper's church4 zone, level 3 (Codex on #952). */
        val VARROCK_CHURCH = DiaryZone(3249, 3469, 3262, 3489, 3)

        /** Guessed: round the palace's tree patch at 3229,3459. */
        val VARROCK_PALACE_TREE_PATCH = DiaryZone(3225, 3455, 3235, 3465, 0)

        /** Guessed: the palace's first floor, where its altar is. */
        val VARROCK_PALACE_ALTAR = DiaryZone(3199, 3466, 3227, 3501, 1)

        /** Guessed: the West Bank's building, inside its walls. */
        val VARROCK_WEST_BANK = DiaryZone(3179, 3432, 3194, 3447, 0)

        /** Guessed: the Cooks' Guild north of its front door at 3143,3443, every floor. */
        val COOKS_GUILD = DiaryZone(3138, 3444, 3148, 3453)

        /** Guessed: Varrock inside its walls, from the West Bank to the East Bank's east wall and the palace's north wall. */
        val VARROCK = DiaryZone(3175, 3375, 3290, 3510)
    }
}

/**
 * Which actions in the content built here do which diary task: content reports what it made, burned, killed or
 * pickpocketed through [Diaries], and these tables decide. A task whose content isn't built isn't listed.
 */
internal object DiaryHooks {
    /** An item made or gathered: [min] of [obj] at once (runes crafted in one go), inside [zone] when there is one. */
    class Made(val obj: Int, val task: DiaryTask, val zone: DiaryZone? = null, val min: Int = 1)

    /** An NPC by its name (every type of it, as the sewer zombies have several), inside [zone]. */
    class ByName(val name: String, val task: DiaryTask, val zone: DiaryZone? = null)

    val MADE = listOf(
        // Water runes come from the water altar alone.
        Made(WATER_RUNE, DiaryTask.LUMBRIDGE_EASY_4),
        Made(RAW_ANCHOVIES, DiaryTask.LUMBRIDGE_EASY_9, DiaryZone.AL_KHARID),
        Made(BREAD, DiaryTask.LUMBRIDGE_EASY_10, DiaryZone.LUMBRIDGE_KITCHEN),
        Made(IRON_ORE, DiaryTask.LUMBRIDGE_EASY_11, DiaryZone.AL_KHARID_MINE),
        Made(RAW_SALMON, DiaryTask.LUMBRIDGE_MEDIUM_6, DiaryZone.LUMBRIDGE),
        Made(COIF, DiaryTask.LUMBRIDGE_MEDIUM_7, DiaryZone.LUMBRIDGE_COW_PEN),
        Made(WILLOW_LOGS, DiaryTask.LUMBRIDGE_MEDIUM_8, DiaryZone.DRAYNOR),
        Made(LAVA_RUNE, DiaryTask.LUMBRIDGE_MEDIUM_12, DiaryZone.FIRE_ALTAR),
        // A mining helmet lit counts as one made lit.
        Made(LightSources.MINING_HELMET_LIT, DiaryTask.LUMBRIDGE_HARD_9, DiaryZone.LUMBRIDGE_BASEMENT),
        Made(ADAMANT_PLATEBODY, DiaryTask.LUMBRIDGE_ELITE_4, DiaryZone.DRAYNOR_SEWERS),
        Made(WATER_RUNE, DiaryTask.LUMBRIDGE_ELITE_5, min = 140),
        Made(IRON_ORE, DiaryTask.VARROCK_EASY_3, DiaryZone.VARROCK_SE_MINE),
        Made(PLANK, DiaryTask.VARROCK_EASY_4, DiaryZone.LUMBER_YARD),
        // Inferred: the bowl spun anywhere, its firing in the village's oven counts.
        Made(BOWL, DiaryTask.VARROCK_EASY_10, DiaryZone.BARBARIAN_VILLAGE),
        Made(EARTH_RUNE, DiaryTask.VARROCK_EASY_12),
        Made(RAW_TROUT, DiaryTask.VARROCK_EASY_13, DiaryZone.BARBARIAN_VILLAGE),
        // "Make 20 Mahogany Planks in one go": at any sawmill.
        Made(MAHOGANY_PLANK, DiaryTask.VARROCK_MEDIUM_10, min = 20),
        // Yew roots dug from the stump: two at once.
        Made(YEW_ROOTS, DiaryTask.VARROCK_HARD_8, DiaryZone.VARROCK_PALACE_TREE_PATCH, min = 2),
        Made(SUPER_COMBAT, DiaryTask.VARROCK_ELITE_1, DiaryZone.VARROCK_WEST_BANK),
        Made(SUMMER_PIE, DiaryTask.VARROCK_ELITE_3, DiaryZone.COOKS_GUILD),
        // Inferred: "Smith and fletch ten rune darts within Varrock" counts on the fletching, a set of ten at once, wherever
        // the tips were smithed.
        Made(RUNE_DART, DiaryTask.VARROCK_ELITE_4, DiaryZone.VARROCK, min = 10),
        Made(EARTH_RUNE, DiaryTask.VARROCK_ELITE_5, min = 100),
    )

    /** Pray-at with Smite on, by the altar's place. */
    val SMITE_ALTARS = listOf(
        DiaryZone.EMIRS_ARENA_ALTAR to DiaryTask.LUMBRIDGE_HARD_10,
        DiaryZone.VARROCK_PALACE_ALTAR to DiaryTask.VARROCK_HARD_9,
    )

    /**
     * Logs lit. Inferred: "Chop and burn some oak logs in Lumbridge" counts when they're burned there, whoever chopped
     * them (the Wiki gives no more).
     */
    val BURNED = listOf(
        Made(OAK_LOGS, DiaryTask.LUMBRIDGE_EASY_7, DiaryZone.LUMBRIDGE),
        // The same for "Chop some yew logs in Varrock and burn them at the top of the Varrock church".
        Made(YEW_LOGS, DiaryTask.VARROCK_HARD_6, DiaryZone.VARROCK_CHURCH),
    )

    val KILLED = listOf(
        ByName("Zombie", DiaryTask.LUMBRIDGE_EASY_8, DiaryZone.DRAYNOR_SEWERS),
        ByName("Cave bug", DiaryTask.LUMBRIDGE_EASY_2, DiaryZone.LUMBRIDGE_SWAMP_CAVES),
    )

    val PICKPOCKETED = listOf(
        ByName("Man", DiaryTask.LUMBRIDGE_EASY_6, DiaryZone.LUMBRIDGE),
        ByName("Woman", DiaryTask.LUMBRIDGE_EASY_6, DiaryZone.LUMBRIDGE),
    )

    /** The tasks [count] of [obj] does, made or lit where the player stands. */
    fun made(hooks: List<Made>, player: Player, obj: Int, count: Int): List<DiaryTask> =
        hooks.filter { it.obj == obj && count >= it.min && it.zone?.has(player) != false }.map { it.task }

    /** The tasks an NPC called [name] does, killed or pickpocketed where the player stands. */
    fun named(hooks: List<ByName>, player: Player, name: String): List<DiaryTask> =
        hooks.filter { it.name.equals(name, ignoreCase = true) && it.zone?.has(player) != false }.map { it.task }

    const val WATER_RUNE = 555
    const val LAVA_RUNE = 4699
    const val RAW_ANCHOVIES = 321
    const val RAW_SALMON = 331
    const val BREAD = 2309
    const val IRON_ORE = 440
    const val COIF = 1169
    const val OAK_LOGS = 1521
    const val WILLOW_LOGS = 1519
    const val ADAMANT_PLATEBODY = 1123
    const val EARTH_RUNE = 557
    const val RAW_TROUT = 335
    const val PLANK = 960
    const val MAHOGANY_PLANK = 8782
    const val BOWL = 1923
    const val YEW_LOGS = 1515
    const val YEW_ROOTS = 6049
    const val SUPER_COMBAT = 12695
    const val SUMMER_PIE = 7218
    const val RUNE_DART = 811
    const val RUNE_DART_TIP = 824
    const val SPOTTIER_CAPE = 10071

    /** The museum's full kudos, which Orlando Smith's task asks for. */
    const val ORLANDO_KUDOS = 153

    /** `fai_varrock_dead_tree`, the Lumber Yard's dying trees, and the tea stall loc. */
    const val DYING_TREE = 3648
    const val TEA_STALL = 635

    const val VARROCK_FENCE = "shortcut_varrock_fence"

    /** The level 51 pipe in the Edgeville Dungeon (the Misthalin fixes thread's key; the level 60 south pipe isn't it). */
    const val EDGEVILLE_PIPE = "shortcut_edgeville_dungeon_pipe"
    const val THESSALIA_SHOP = "thessalias_fine_clothes"
}
