package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.Lines

/** The emote tab's lines (keys only). */
object EmoteLines : Lines("emotes", "Emotes") {
    val LOCKED = line("locked", "Perform or Loop on an emote the player hasn't unlocked; words not found")
}

/**
 * The emote tab (`emote` 216): its emotes play from `emote:contents` (component 2), one client-made child per emote,
 * numbered as the cache's enum 1000 names them (0 Yes to 55 Crab dance). Each is shown unlocked or not by the client's
 * own emote_checkunlocked, which [unlocked] mirrors, so a locked emote's click is refused as its greyed icon says
 * (osrs-dumps: emote_init, emote_update, emote_checkunlocked; 242 cache).
 *
 * The animations and spotanims are the cache's by name (`emote_yes` 855 for Yes, `emote_lightbulb` and
 * `emote_lightbulb_spot` for Idea, and so on; see [EMOTES]); which name goes with which emote is read from the names
 * and is inferred where the name differs from the emote's (`emote_ya_boo_sucks` for Raspberry, `ash_emote` for
 * Hypermobile Drinker, `bday17_bling` and `bday17_lasso` for Smooth and Crazy dance). The Stronghold of Security's four
 * (Flap, Slap Head, Idea, Stamp) are why this is built now.
 *
 * Guessed: an emote stops the player where they stand and plays at once, the spotanims at height 0, and a new emote
 * replaces one still playing. Left out: Loop (it plays once, as Perform does; live repeats it until the player acts),
 * Blow Kiss's hearts, and the emotes that need more than an animation: Skill Cape (each cape's own), Uri transform,
 * Premier Shield and Relic unlock, which do nothing yet.
 */
object Emotes {
    const val INTERFACE = 216
    const val CONTENTS = 2
    const val LAST = 55

    /** One emote: the tab's index for it, its animation and its spotanim (-1 for none). */
    class Emote(val index: Int, val anim: Int, val spotanim: Int = -1)

    val EMOTES: Map<Int, Emote> = listOf(
        Emote(0, 855), // emote_yes
        Emote(1, 856), // emote_no
        Emote(2, 858), // emote_bow
        Emote(3, 859), // emote_angry
        Emote(4, 857), // emote_think
        Emote(5, 863), // emote_wave
        Emote(6, 2113), // emote_shrug
        Emote(7, 862), // emote_cheer
        Emote(8, 864), // emote_beckon
        Emote(9, 861), // emote_laugh
        Emote(10, 2109), // emote_jump_with_joy
        Emote(11, 2111), // emote_yawn
        Emote(12, 866), // emote_dance
        Emote(13, 2106), // emote_dance_scottish
        Emote(14, 2107), // emote_dance_spin
        Emote(15, 2108), // emote_dance_headbang
        Emote(16, 860), // emote_cry
        Emote(17, 1374), // emote_blow_kiss
        Emote(18, 2105), // emote_panic
        Emote(19, 2110), // emote_ya_boo_sucks
        Emote(20, 865), // emote_clap
        Emote(21, 2112), // emote_fremmenik_salute
        Emote(22, 2127), // human_cave_goblin_bow
        Emote(23, 2128), // human_cave_goblin_dance
        Emote(24, 1131), // emote_glass_box
        Emote(25, 1130), // emote_climbing_rope
        Emote(26, 1129), // emote_mime_lean
        Emote(27, 1128), // emote_glass_wall
        Emote(28, 4276, 712), // emote_lightbulb, emote_lightbulb_spot
        Emote(29, 4278, 713), // emote_stampfeet, emote_duststamp_spot
        Emote(30, 4280), // emote_panic_flap
        Emote(31, 4275), // emote_slap_head
        Emote(32, 3544), // zombie_walk_emote
        Emote(33, 3543), // zombie_dance
        Emote(34, 2836), // terrified_emote
        Emote(35, 6111), // rabbit_emote
        Emote(36, 874), // emote_situps_5
        Emote(37, 2756), // pushups
        Emote(38, 2761), // starjump_5
        Emote(39, 2764), // run_on_spot
        Emote(40, 8917), // emote_flex
        Emote(41, 1708, 320), // hw07_arm_from_the_ground_emote and its spotanim
        Emote(42, 7131), // ash_emote
        Emote(44, 4751, 1239), // emote_air_guitar, air_guitar_spotanim
        Emote(46, 7533), // bday17_bling
        Emote(47, 7537), // bday17_lasso
        Emote(49, 8541), // emote_explore
        Emote(51, 10031, 2365), // emote_party, fx_emote_party01_active
        Emote(52, 10503), // emote_trick
        Emote(53, 10796), // emote_varlamore_salute
        Emote(54, 10053), // emote_sit
        Emote(55, 10051), // human_emote_crabdance
    ).associateBy { it.index }

    // The varbits and varp emote_checkunlocked reads (242 cache gamevals).
    private const val EMOTE_GLASSBOX = 1368
    private const val EMOTE_CLIMBROPE = 1369
    private const val EMOTE_LEAN = 1370
    private const val EMOTE_GLASSWALL = 1367
    private const val SOS_EMOTE_IDEA = 2311
    private const val SOS_EMOTE_STAMP = 2312
    private const val SOS_EMOTE_FLAP = 2309
    private const val SOS_EMOTE_DOH = 2310
    private const val EMOTE_ZOMBIE_WALK = 1921
    private const val EMOTE_ZOMBIE_DANCE = 1920
    private const val EMOTE_TERRIFIED = 1371
    private const val EMOTE_BUNNY_HOP = 2055
    private const val EMOTE_ZOMBIE_HAND = 1000
    private const val EMOTE_MUSICCAPE = 4673
    private const val EMOTE_DRILLDEMON = 4732
    private const val EMOTE_FLEX = 12062
    private const val EMOTE_ASH = 4802
    private const val EMOTE_HOTLINE_BLING = 5597
    private const val EMOTE_GANGNAM = 5598
    private const val EMOTE_EXPLORE = 10115
    private const val EMOTE_PARTY = 6550
    private const val EMOTE_TRICK = 15322
    private const val EMOTE_CRAB = 10065
    private const val COLOSSEUM_GLORY_VARP = 4130

    /** script4922's top rank: 20,000 glory or more, which Fortis Salute needs. */
    private const val FORTIS_GLORY = 20_000

    private val BY_VARBIT = mapOf(
        24 to EMOTE_GLASSBOX, 25 to EMOTE_CLIMBROPE, 26 to EMOTE_LEAN, 27 to EMOTE_GLASSWALL,
        28 to SOS_EMOTE_IDEA, 29 to SOS_EMOTE_STAMP, 30 to SOS_EMOTE_FLAP, 31 to SOS_EMOTE_DOH,
        32 to EMOTE_ZOMBIE_WALK, 33 to EMOTE_ZOMBIE_DANCE, 34 to EMOTE_TERRIFIED, 35 to EMOTE_BUNNY_HOP,
        36 to EMOTE_DRILLDEMON, 37 to EMOTE_DRILLDEMON, 38 to EMOTE_DRILLDEMON, 39 to EMOTE_DRILLDEMON,
        40 to EMOTE_FLEX, 41 to EMOTE_ZOMBIE_HAND, 42 to EMOTE_ASH, 44 to EMOTE_MUSICCAPE,
        46 to EMOTE_HOTLINE_BLING, 47 to EMOTE_GANGNAM, 49 to EMOTE_EXPLORE, 51 to EMOTE_PARTY, 52 to EMOTE_TRICK,
        55 to EMOTE_CRAB,
    )

    /**
     * Whether [player] has emote [index], as emote_checkunlocked decides it for the icon. Goblin Bow and Salute are
     * The Lost Tribe's, which every account counts as done (Quests.kt).
     */
    fun unlocked(player: Player, index: Int): Boolean {
        BY_VARBIT[index]?.let { return varbit(player, it) != 0 }
        if (index == 53) return varp(player, COLOSSEUM_GLORY_VARP) >= FORTIS_GLORY
        return index in 0..LAST
    }

    private fun varbit(player: Player, id: Int): Int = if (player.vars.types.varbit(id) != null) player.vars.getVarbit(id) else 0

    private fun varp(player: Player, id: Int): Int = if (id in 0 until player.vars.types.varpCount) player.vars[id] else 0
}

fun registerEmotes(world: World) {
    world.handlers.onButton(Emotes.INTERFACE, Emotes.CONTENTS) { op ->
        val emote = Emotes.EMOTES[op.sub] ?: return@onButton
        if (!Emotes.unlocked(player, emote.index)) return@onButton world.message(player, EmoteLines.LOCKED)
        // Inferred, as a walk click: a delayed player (an agility obstacle) can't break off for an emote.
        if (player.scripts.delayed) return@onButton
        // Guessed: an emote replaces whatever the player was doing, as a cast does: a follow or other interaction, and
        // a queued activity (a home teleport's stages stop here, before the emote's animation goes on).
        player.scripts.dropWeak()
        player.interaction = null
        world.npcInteractions.leave(player)
        player.waypoints.clear()
        player.anim(emote.anim)
        if (emote.spotanim >= 0) player.spotanim(emote.spotanim)
        world.trails.emoted(player, emote.index)
    }
}
