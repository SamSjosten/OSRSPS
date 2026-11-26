package osrsps.game.content

import osrsps.cache.VarTypeEncoder
import osrsps.cache.overlay.CacheOverlay
import osrsps.game.VarTypes
import osrsps.game.VarbitType

/**
 * Varps and varbits of our own, with ids right after the loaded cache's ([after]): the first varp is the cache's varp
 * count and the first varbit its varbit count, whatever build of the revision the cache is (OpenRS2's 241 builds differ
 * in how far their varps go: 5,890 in one, 5,995 in another). They are served to the client through the cache overlay
 * as config archives 16 and 14 hold them, and known to the server's [VarTypes] too, so a varbit set here changes what
 * the client draws (a loc with a varbit transform) the way the cache's own do. The varp id is the next one after the
 * cache's, as the client sizes its varp array by that archive's files; the overlay refuses an id the cache has, so a
 * build that reaches ours would fail at startup, not quietly, which deriving the ids from the cache prevents. A varp
 * of ours is saved with the player like any other, so a cache with more varps than the last one moves the save's old
 * value out of the way (nothing is read back from it).
 *
 * Content and tests refer to the vars by name ([lodestonesVarp], [lodestoneVarbit], [lodestonePreviousVarbit], [varp]
 * and [varbit]), never by number: `::varp lodestones` and `::varbit lodestone.varrock` for the runner scripts and the chatbox.
 */
class CustomVars(
    /** The first varp of ours: the cache's varp count. */
    val firstVarp: Int,
    /** The first varbit of ours: the cache's varbit count. */
    val firstVarbit: Int,
) {
    init {
        require(firstVarp >= 0 && firstVarbit >= 0) { "Custom var ids start at $firstVarp and $firstVarbit" }
    }

    /** The lodestones' varp: one bit per site, 1 once the player has activated it ([Lodestones]). */
    val lodestonesVarp: Int = firstVarp

    /** The varbit that is bit [Lodestones.Site.index] of the lodestones' varp: 1 once the player has activated the site. */
    fun lodestoneVarbit(site: Lodestones.Site): Int = firstVarbit + site.index

    /**
     * The varbit holding the stone the player last teleported to (its site index plus one; 0 for none), for the map's
     * Previous destination: bits [Lodestones.PREVIOUS_LOW_BIT] to [Lodestones.PREVIOUS_HIGH_BIT] of the lodestones' varp.
     */
    val lodestonePreviousVarbit: Int = firstVarbit + Lodestones.SITES.size

    /**
     * The second (since the epoch) Captain Harlan last handed the player free weapons, 0 for never: he gives them once
     * every [Harlan.CLAIM_MINUTES] ([Harlan]). Live keeps this somewhere we haven't found; ours is guessed.
     */
    val harlanClaimVarp: Int = firstVarp + 1

    /**
     * The giant bosses' lairs the player has unlocked for good, a bit each ([GiantLairs.Lair.bit]): a giant key or mossy
     * key opens a gate once, and it stays open after (OSRS Wiki, Gate (Obor)). Live keeps this somewhere we haven't
     * found; ours is guessed.
     */
    val giantLairsVarp: Int = firstVarp + 2

    /**
     * 1 while the player has been in a Wilderness boss lair since they last left the Wilderness, so a PvP death takes
     * the lairs' fee ([WildernessLairFee]). Live keeps this somewhere we haven't found; ours is guessed.
     */
    val lairVisitVarp: Int = firstVarp + 3

    /**
     * The TzHaar Fight Cave run the player is part way through, for a logout or a lost connection to come back to
     * ([osrsps.game.tzhaar.FightCaves.State]): the wave to start at their next login and the rotation, 0 for none. Live
     * keeps this somewhere we haven't found; ours is guessed.
     */
    val fightCaveVarp: Int = firstVarp + 4

    /**
     * The Inferno run the player is part way through, for a logout or a lost connection to come back to
     * ([osrsps.game.tzhaar.Inferno]): the wave to start at their next login, 0 for none. Live keeps this somewhere we
     * haven't found; ours is guessed.
     */
    val infernoVarp: Int = firstVarp + 5

    /**
     * The farming tick ([osrsps.game.skill.farming.Farming.tickOf]) the White Tree in Varrock Palace's garden last grew
     * a fruit from, 0 while it bears all four ([PalaceGarden]). Live keeps this somewhere we haven't found; ours is
     * guessed.
     */
    val whiteTreeVarp: Int = firstVarp + 6

    /**
     * Luthas's banana crate: the bananas in it (bits 0 to 3, 0 to [MusaPoint.CRATE_SIZE]) and whether the player took his
     * job (bit 4) ([MusaPoint]). Live keeps this somewhere we haven't found; ours is guessed.
     */
    val bananaCrateVarp: Int = firstVarp + 7

    /**
     * 1 once the player has shown a fire cape to a TzHaar-Ket at Mor Ul Rek's barriers, which lets them through the hot
     * vent doors for good and makes them Xo ([MorUlRekInner]). Live keeps this somewhere we haven't found; ours is guessed.
     */
    val morUlRekAccessVarp: Int = firstVarp + 8

    /**
     * The balloon log storage's normal, oak, willow and yew logs, 7 bits each from bit 0, and the player's preference
     * above them ([LogStorage]). Live keeps this somewhere we haven't found; ours is guessed.
     */
    val logStorageVarp: Int = firstVarp + 9

    /** The balloon log storage's magic logs ([LogStorage]). Live keeps this somewhere we haven't found; ours is guessed. */
    val logStorageMagicVarp: Int = firstVarp + 10

    /**
     * The Champions' Challenge duel the player has arranged with Larxus, its [ChampionsChallenge.Champion]'s ordinal plus
     * one, 0 for none ([ChampionsChallenge]). Live keeps this somewhere we haven't found; ours is guessed.
     */
    val championsVarp: Int = firstVarp + 11

    /**
     * Brutus's scoreboard, the player's column: deaths in his fields, and the fastest kill in ticks (0 for none)
     * ([BrutusScoreboard]). Live keeps these somewhere we haven't found; ours are guessed.
     */
    val brutusDeathsVarp: Int = firstVarp + 12
    val brutusBestVarp: Int = firstVarp + 13

    /**
     * The player's last drink of the Tears of Guthix: the UTC day (days since 1970) in its low 16 bits and their quest
     * points then above them, 0 before the first ([TearsOfGuthix]). Live keeps this somewhere we haven't found; ours is
     * guessed.
     */
    val togVisitVarp: Int = firstVarp + 14

    /** The player's total XP at their last drink of the Tears, in tens of XP ([TearsOfGuthix]); guessed as above. */
    val togVisitXpVarp: Int = firstVarp + 15

    /** The same as Brutus's for Demonic Brutus, the scoreboard's Read (Demonic). */
    val brutusDemonicDeathsVarp: Int = firstVarp + 16
    val brutusDemonicBestVarp: Int = firstVarp + 17

    /**
     * The player's pet cat: its growth cycles ([Cats.GROWTH_CYCLE_TICKS] each) in the low 16 bits and the rats their
     * cats have caught above them ([Cats]). Live keeps these somewhere we haven't found; ours is guessed.
     */
    val catVarp: Int = firstVarp + 18

    /**
     * A kitten's care ([Cats]): ticks until it runs off hungry in bits 0 to 11, lonely in bits 12 to 25, and bit 30 set
     * once it has been out. Guessed as above.
     */
    val catCareVarp: Int = firstVarp + 19

    /** Every varbit of ours, by id. */
    val varbits: Map<Int, VarbitType> = Lodestones.SITES.associate { lodestoneVarbit(it) to VarbitType(lodestonesVarp, it.index, it.index) } +
        (lodestonePreviousVarbit to VarbitType(lodestonesVarp, Lodestones.PREVIOUS_LOW_BIT, Lodestones.PREVIOUS_HIGH_BIT))

    /** Every varp of ours. */
    val varps: List<Int> = listOf(lodestonesVarp, harlanClaimVarp, giantLairsVarp, lairVisitVarp, fightCaveVarp, infernoVarp, whiteTreeVarp, bananaCrateVarp, morUlRekAccessVarp, logStorageVarp, logStorageMagicVarp, championsVarp, brutusDeathsVarp, brutusBestVarp, togVisitVarp, togVisitXpVarp, brutusDemonicDeathsVarp, brutusDemonicBestVarp, catVarp, catCareVarp)

    /** The varps by name, for `::varp name`: [LODESTONES], [HARLAN_CLAIMED], [GIANT_LAIRS], [LAIR_VISIT], [FIGHT_CAVE], [INFERNO], [WHITE_TREE], [BANANA_CRATE], [MOR_UL_REK_ACCESS], [LOG_STORAGE], [LOG_STORAGE_MAGIC], [CHAMPIONS], [BRUTUS_DEATHS], [BRUTUS_BEST], [TOG_VISIT], [TOG_VISIT_XP], [BRUTUS_DEMONIC_DEATHS], [BRUTUS_DEMONIC_BEST], [CAT] and [CAT_CARE]. */
    val varpNames: Map<String, Int> = mapOf(
        LODESTONES to lodestonesVarp, HARLAN_CLAIMED to harlanClaimVarp, GIANT_LAIRS to giantLairsVarp, LAIR_VISIT to lairVisitVarp,
        FIGHT_CAVE to fightCaveVarp, INFERNO to infernoVarp, WHITE_TREE to whiteTreeVarp, BANANA_CRATE to bananaCrateVarp, MOR_UL_REK_ACCESS to morUlRekAccessVarp,
        LOG_STORAGE to logStorageVarp, LOG_STORAGE_MAGIC to logStorageMagicVarp,
        CHAMPIONS to championsVarp,
        BRUTUS_DEATHS to brutusDeathsVarp, BRUTUS_BEST to brutusBestVarp,
        TOG_VISIT to togVisitVarp, TOG_VISIT_XP to togVisitXpVarp,
        BRUTUS_DEMONIC_DEATHS to brutusDemonicDeathsVarp, BRUTUS_DEMONIC_BEST to brutusDemonicBestVarp,
        CAT to catVarp, CAT_CARE to catCareVarp,
    )

    /** The varbits by name, for `::varbit name`: each site's line key (`lodestone.lumbridge`, `lodestone.al_kharid`) and [LODESTONE_PREVIOUS]. */
    val varbitNames: Map<String, Int> = Lodestones.SITES.associate { it.name.key to lodestoneVarbit(it) } + (LODESTONE_PREVIOUS to lodestonePreviousVarbit)

    /** The varp called [name], in any case, or null. */
    fun varp(name: String): Int? = varpNames[name.lowercase()]

    /** The varbit called [name], in any case, or null. */
    fun varbit(name: String): Int? = varbitNames[name.lowercase()]

    /** What the overlay adds to the var type groups: a type for each varp and varbit. */
    val overlayFiles: List<CacheOverlay.File>
        get() = varps.map { CacheOverlay.File(CONFIG_INDEX, VARP_GROUP, it, VarTypeEncoder.encodeVarp()) } +
            varbits.entries.sortedBy { it.key }.map { (id, type) -> CacheOverlay.File(CONFIG_INDEX, VARBIT_GROUP, id, VarTypeEncoder.encodeVarbit(type)) }

    override fun toString(): String = "varp $firstVarp, varbits $firstVarbit to ${firstVarbit + varbits.size - 1}"

    companion object {
        const val CONFIG_INDEX = 2
        const val VARBIT_GROUP = 14
        const val VARP_GROUP = 16

        /** The lodestones' varp's name, and the previous destination varbit's. */
        const val LODESTONES = "lodestones"
        const val LODESTONE_PREVIOUS = "lodestone.previous"

        /** Captain Harlan's last handout's varp's name. */
        const val HARLAN_CLAIMED = "harlan.claimed"

        /** The giant lairs' varp's name. */
        const val GIANT_LAIRS = "giant_lairs"

        /** The Wilderness lairs' visit varp's name. */
        const val LAIR_VISIT = "wilderness_lair_visit"

        /** The Fight Cave run's varp's name. */
        const val FIGHT_CAVE = "fight_cave"

        /** The Inferno run's varp's name. */
        const val INFERNO = "inferno"

        /** The White Tree's regrowth varp's name. */
        const val WHITE_TREE = "white_tree"

        /** Luthas's banana crate's varp's name. */
        const val BANANA_CRATE = "banana_crate"

        /** Mor Ul Rek's inner city access varp's name. */
        const val MOR_UL_REK_ACCESS = "mor_ul_rek_access"

        /** The balloon log storage's varps' names. */
        const val LOG_STORAGE = "log_storage"
        const val LOG_STORAGE_MAGIC = "log_storage.magic"

        /** The Champions' Challenge arrangement's varp's name. */
        const val CHAMPIONS = "champions_challenge"

        /** Brutus's scoreboard's varps' names: the player's deaths and fastest kill. */
        const val BRUTUS_DEATHS = "brutus.deaths"
        const val BRUTUS_BEST = "brutus.best_ticks"
        const val BRUTUS_DEMONIC_DEATHS = "brutus.demonic_deaths"
        const val BRUTUS_DEMONIC_BEST = "brutus.demonic_best_ticks"

        /** The Tears of Guthix's last-visit varps' names. */
        const val TOG_VISIT = "tears_of_guthix"
        const val TOG_VISIT_XP = "tears_of_guthix.xp"

        /** The pet cat's varps' names. */
        const val CAT = "cat"
        const val CAT_CARE = "cat.care"

        /** Our vars with ids right after [cache]'s, the loaded cache's own types (before ours are added to them). */
        fun after(cache: VarTypes): CustomVars = CustomVars(cache.varpCount, cache.varbitCount)
    }
}
