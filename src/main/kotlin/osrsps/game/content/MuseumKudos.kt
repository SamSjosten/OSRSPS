package osrsps.game.content

import osrsps.game.FrameSlot
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World

/**
 * The Varrock Museum's Kudos, kept as live keeps it: in the cache's own museum varbits, so the client's Kudos List (the
 * information booth's Check Kudos, `vm_kudos_info` 600) and anything else reading them sees what live shows.
 *
 * Each way of earning Kudos sets its own varbit (a display filled, a quiz passed, a story told), and [total] adds up
 * what those are worth, by the Wiki's Kudos page and the client's own list (enums 1740 and 1741, procs
 * `vm_kudos_get_status` and `vm_info_draw`). The total is written to `vm_kudos` (3637, the count the client shows) each
 * time a source changes and at login. Inferred: live keeps `vm_kudos` as a running count it adds to; working it out from
 * the sources gives the same number, can't count one source twice, and puts right a save made before a source existed.
 *
 * Built so far: the Dig Site exhibit's five display cases (10 each, [MuseumFinds]), the natural history quiz's
 * fourteen displays (2 each, [NaturalHistoryQuiz]) and Historian Minas's timeline (80, [MuseumTimeline]), 158 in all.
 * Not built, so worth nothing here yet: Fossil Island's displays, Camdozaal's and Sailing's relics.
 */
object Kudos {
    /** `vm_kudos`: varp `vm` (1010) bits 0 to 10. */
    const val VARBIT_KUDOS = 3637

    /** `vm_necklace`: the clean necklace shown to an archaeologist, who then teaches the Digsite pendant's enchantment. */
    const val VARBIT_NECKLACE = 3639

    /** `vm_digsite_symbol_anc`, `_symbol_old`, `_coin_senn` (the ancient coin), `_coin_sara` (the old coin), `_pottery`: varp `vm_displays` (1011) bits 2 to 6. */
    const val VARBIT_ANCIENT_SYMBOL = 3645
    const val VARBIT_OLD_SYMBOL = 3646
    const val VARBIT_ANCIENT_COIN = 3647
    const val VARBIT_OLD_COIN = 3648
    const val VARBIT_POTTERY = 3649

    /** `vm_reward_50`, `_100`, `_150`: the information clerk's three rewards, claimed. */
    const val VARBIT_REWARD_50 = 3640
    const val VARBIT_REWARD_100 = 3641
    const val VARBIT_REWARD_150 = 3642

    /** Everything the museum gives Kudos for on live (the Wiki's Kudos page; the client's counter shows it as the most). */
    const val MAX = 243

    /** A source: worth [kudos] once [varbit] is at least [atLeast]. */
    class Source(val varbit: Int, val kudos: Int, val atLeast: Int = 1)

    /** The Dig Site exhibit's displays, in the client's list order (enum 1741 rows 0 to 4), 10 Kudos each (enum 1740). */
    val DIG_SITE = listOf(
        Source(VARBIT_OLD_COIN, 10),
        Source(VARBIT_ANCIENT_COIN, 10),
        Source(VARBIT_ANCIENT_SYMBOL, 10),
        Source(VARBIT_OLD_SYMBOL, 10),
        Source(VARBIT_POTTERY, 10),
    )

    /** The natural history quiz's fourteen displays, in the client's list order (rows 5 to 18), 2 Kudos each once at 3. */
    val NATURAL_HISTORY = NaturalHistoryQuiz.DISPLAYS.map { Source(it.varbit, NaturalHistoryQuiz.KUDOS_EACH, NaturalHistoryQuiz.DONE) }

    /**
     * Historian Minas's timeline displays, in the client's list order (rows 19 to 33, [MuseumTimeline]): 5 Kudos each
     * but Curse of the Empty Lord's 10; the staff and the River Salve displays count twice, at 1 and at 2.
     */
    val TIMELINE = with(MuseumTimeline) {
        listOf(
            Source(VARBIT_STAFF_OF_ARMADYL, 5),
            Source(VARBIT_STAFF_OF_ARMADYL, 10, 2),
            Source(VARBIT_SHIELD_OF_ARRAV, 5),
            Source(VARBIT_OBSERVATORY, 5),
            Source(VARBIT_EXCALIBUR, 5),
            Source(VARBIT_SILVERLIGHT, 5),
            Source(VARBIT_GLIDER, 5),
            Source(VARBIT_ROBERT, 5),
            Source(VARBIT_LATHAS, 5),
            Source(VARBIT_SALVE_KNIGHTS, 5),
            Source(VARBIT_SALVE_KNIGHTS, 5, 2),
            Source(VARBIT_ARDOUGNE, 5),
            Source(VARBIT_SUROK, 5),
            Source(VARBIT_RUNES, 5),
            Source(VARBIT_DEFENDER, 5),
        )
    }

    val SOURCES: List<Source> = DIG_SITE + NATURAL_HISTORY + TIMELINE

    /** The Kudos the Digsite's workers' gate wants ([Digsite]), from the Wiki's Transcript:Museum guard. */
    const val DIGSITE_GATE = 153

    fun total(player: Player): Int = SOURCES.sumOf { if (counts(player, it)) it.kudos else 0 }

    /** How many of [sources] the player has done. */
    fun done(player: Player, sources: List<Source>): Int = sources.count { counts(player, it) }

    /** Whether [source] is done; never in a world whose cache lacks its varbit (headless tests building part of the museum). */
    private fun counts(player: Player, source: Source): Boolean =
        player.vars.types.varbit(source.varbit) != null && player.vars.getVarbit(source.varbit) >= source.atLeast

    /** Writes [total] to the client's count. Called whenever a source changes. */
    fun refresh(player: Player) {
        player.vars.setVarbit(VARBIT_KUDOS, total(player))
    }

    /**
     * At login: the count again, in case the save predates a source. Skipped, with nothing said, when the cache has no
     * museum varbits, as the headless tests' worlds that don't build the museum have none.
     */
    fun login(player: Player) {
        if (player.vars.types.varbit(VARBIT_KUDOS) == null) return
        refresh(player)
    }

    /**
     * The information clerk's rewards, from the Wiki's Kudos page: Mining XP at 51 Kudos, Crafting and Mining at 101,
     * and Crafting, Hunter, Prayer, Slayer and Smithing at 151, each claimed once.
     */
    enum class Reward(val varbit: Int, val kudos: Int, val xp: Map<Skill, Int>) {
        AT_51(VARBIT_REWARD_50, 51, mapOf(Skill.MINING to 1_000)),
        AT_101(VARBIT_REWARD_100, 101, mapOf(Skill.CRAFTING to 2_500, Skill.MINING to 2_500)),
        AT_151(VARBIT_REWARD_150, 151, listOf(Skill.CRAFTING, Skill.HUNTER, Skill.PRAYER, Skill.SLAYER, Skill.SMITHING).associateWith { 4_000 }),
    }

    /** The rewards the player has the Kudos for and hasn't claimed. */
    fun pendingRewards(player: Player): List<Reward> {
        val kudos = total(player)
        return Reward.entries.filter { kudos >= it.kudos && player.vars.getVarbit(it.varbit) == 0 }
    }

    /**
     * Gives every reward in [rewards] and marks it claimed. Inferred: the XP is exact, whatever the server's XP rate, as
     * a fixed reward rather than XP for an action.
     */
    fun claim(player: Player, rewards: List<Reward>) {
        for (reward in rewards) {
            if (player.vars.getVarbit(reward.varbit) != 0) continue
            player.vars.setVarbit(reward.varbit, 1)
            for ((skill, xp) in reward.xp) player.skills.addExactTenths(skill, xp * 10)
        }
    }

    /** The information booth (`vm_information_booth` 24452). */
    const val BOOTH = 24452
    const val BOOTH_LOOK_AT = 1
    const val BOOTH_CHECK_KUDOS = 2

    /** `vm_kudos_info`: the Kudos List, drawn by its own onload script from the museum varbits. */
    const val KUDOS_LIST = 600
}

/** The information booth: Look at shows the museum map (guessed: the Wiki says the booth gives one) and Check Kudos the Kudos List. */
fun registerMuseumKudos(world: World) {
    with(Kudos) {
        world.handlers.onLoc(BOOTH, BOOTH_LOOK_AT) { world.interfaces.openModal(player, FrameSlot.MAINMODAL, MuseumCleaning.MAP_INTERFACE) }
        world.handlers.onLoc(BOOTH, BOOTH_CHECK_KUDOS) { world.interfaces.openModal(player, FrameSlot.MAINMODAL, KUDOS_LIST) }
    }
}
