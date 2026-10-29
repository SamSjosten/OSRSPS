package osrsps.game.combat

import osrsps.game.dialogue.Lines

/**
 * Combat's game messages. Their words are in the dialogue file, like every other line of Jagex text (Sam's rule,
 * 2026-10-29, which moved these out of the code); each line's `where` says what shows it and, for the guessed ones, that
 * the words are guessed.
 */
object CombatLines : Lines("combat", "Combat") {
    val SOMEONE_ELSE = line("someone_else", "attacking an NPC that is fighting someone else, outside multi-way combat")
    val ALREADY_UNDER_ATTACK = line("already_under_attack", "attacking another NPC while one fights you, outside multi-way combat")
    val DEAD = line("dead", "on dying (the Death page)")

    val NO_AMMO = line("no_ammo", "a bow or crossbow with no ammunition worn (the Ammunition page)")
    val WRONG_ARROWS = line("wrong_arrows", "a bow with ammunition it can't fire; words guessed, live's aren't on the Wiki")
    val WRONG_BOLTS = line("wrong_bolts", "a crossbow with ammunition it can't fire; words guessed")

    val ALREADY_HELD = line("already_held", "a hold spell on a frozen target (the Snare page)")
    val IMMUNE = line("immune", "a hold spell on a target in the ticks after a freeze (the Snare page)")
    val MAGIC_LEVEL = line("magic_level", "a spell above the Magic level; words guessed, from memory")
    val NOT_ENOUGH_RUNES = line("not_enough_runes", "a spell without its runes, {runes} the rune's name in plural (Fire Runes); words guessed, from memory", "runes")

    val NO_ROOM = line("no_room", "a blowpipe or powered staff emptied with no room for what comes out; words guessed")

    // The toxic blowpipe's: every one's words guessed.
    val BLOWPIPE_NO_SCALES = line("blowpipe_no_scales", "the toxic blowpipe fired or wielded with no Zulrah's scales")
    val BLOWPIPE_NO_DARTS = line("blowpipe_no_darts", "the toxic blowpipe fired or unloaded with no darts")
    val BLOWPIPE_RAN_OUT = line("blowpipe_ran_out", "the toxic blowpipe's last scale used")
    val BLOWPIPE_OTHER_DARTS = line("blowpipe_other_darts", "darts of another kind used on a loaded blowpipe")
    val BLOWPIPE_FULL_DARTS = line("blowpipe_full_darts", "darts used on a blowpipe holding the most it can")
    val BLOWPIPE_FULL_SCALES = line("blowpipe_full_scales", "scales used on a blowpipe holding the most it can")
    val BLOWPIPE_CHECK = line(
        "blowpipe_check",
        "Check on a blowpipe with darts: {dart} their name, {darts} and {scales} the counts, {percent} the scales' share of the most",
        "dart", "darts", "scales", "percent",
    )
    val BLOWPIPE_CHECK_EMPTY = line("blowpipe_check_empty", "Check on a blowpipe with no darts", "scales", "percent")

    // The powered staves': every one's words guessed. {staff} is the staff's name.
    val STAFF_NO_CHARGES = line("staff_no_charges", "a powered staff's spell with no charges", "staff")
    val STAFF_RAN_OUT = line("staff_ran_out", "a powered staff's last charge used", "staff")
    val STAFF_CHECK = line("staff_check", "Check on a powered staff, {charges} its charges", "staff", "charges")
    val STAFF_FULL = line("staff_full", "charging a powered staff that holds the most it can")
    val STAFF_NO_RUNES = line("staff_no_runes", "charging a powered staff without the runes")
    val STAFF_HOW_MANY = line("staff_how_many", "Charge's count prompt, {most} the most charges that can be added", "most")

    // The grave's: words guessed; live's Check opens a timer interface, and a stranger's grave says something else.
    val GRAVE_NOT_YOURS = line("grave_not_yours", "Check or Loot on another player's grave")
    val GRAVE_COLLAPSED = line("grave_collapsed", "one's grave's time running out with items still in it")
    val GRAVE_NO_SPACE = line("grave_no_space", "taking an item from a grave with no inventory space")
    val GRAVE_MINUTES = line("grave_minutes", "Check on one's own grave with {minutes} minutes left", "minutes")
    val GRAVE_MINUTE = line("grave_minute", "Check on one's own grave with a minute left")
}

/** The prayer book's game messages, in the dialogue file as [CombatLines] are; every one's words guessed. */
object PrayerLines : Lines("prayer", "Prayer") {
    val LEVEL = line("level", "a prayer clicked below its Prayer {level}, {prayer} its name", "level", "prayer")
    val LEVELS = line("levels", "a prayer clicked below its Defence level too (Chivalry, Piety, Rigour, Augury)", "level", "defence", "prayer")
    val OUT_OF_POINTS = line("out_of_points", "the last prayer point drained")
    val NO_QUICK_PRAYERS = line("no_quick_prayers", "the quick prayer orb with none picked; RSMod's words")
    val QUICK_OUT_OF_POINTS = line("quick_out_of_points", "the quick prayer orb with no prayer points; RSMod's words")
}
