package osrsps.game.skill.hunter

import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.map.CollisionMap

/** Hunter's words, in the dialogue file. */
object HunterLines : Lines("hunter", "Hunter") {
    val LAYING = line("laying", "the message on starting to lay a trap")
    val TOO_MANY = line("too_many", "the message when the player has laid as many traps as their level allows", "count")
    val CANT_HERE = line("cant_here", "the message where a trap can't be laid")
    val NOT_YOURS = line("not_yours", "the message on someone else's trap")
    val NO_SPACE = line("no_space", "the message on checking or picking up a trap with no room for it")
    val DISMANTLED = line("dismantled", "the message on dismantling a trap")
    val CAUGHT = line("caught", "the message on checking a trap that caught something", "creature")
    val INVESTIGATE = line("investigate", "Investigate on the player's own set bird snare")
    val INVESTIGATE_BOX = line("investigate_box", "Investigate on the player's own set box trap")
    val INVESTIGATE_NET = line("investigate_net", "Investigate on the player's own set net trap or its young tree")
    val NO_NET = line("no_net", "the message on Set-trap on a young tree with no net trap in the backpack")
    val TRAP_LEVEL = line("trap_level", "the message on laying a trap below the Hunter level it needs", "level")
    val NO_KNIFE = line("no_knife", "the message on Set-trap on a boulder with no knife in the backpack")
    val NO_LOGS = line("no_logs", "the message on Set-trap on a boulder with no logs in the backpack")
    val INVESTIGATE_DEADFALL = line("investigate_deadfall", "Investigate on the player's own set deadfall")
    val NET_LEVEL = line("net_level", "the message on Set-trap on a young tree below the Hunter level its salamander needs", "level")
    val PIT_LEVEL = line("pit_level", "the message on Trap on a pit below the Hunter level its creature needs", "level")
    val NO_TEASING_STICK = line("no_teasing_stick", "the message on Tease with no teasing stick in the backpack and no hunter's spear wielded")
    val NEED_NET = line("need_net", "the message on Catch on a butterfly or moth with no insect net wielded and too low a level to catch it barehanded")
    val CATCH_LEVEL = line("catch_level", "the message on Catch below the Hunter level the butterfly or moth needs", "level")
    val CATCH_MISS = line("catch_miss", "the message on failing to catch a butterfly or moth")
    val CAUGHT_RELEASED = line("caught_released", "the message on catching a butterfly or moth with no butterfly jar, which lets it go at once", "creature")
    val RELEASED = line("released", "the message on Release on a jarred butterfly or moth")
    val IMP_NEED_NET = line("imp_need_net", "the message on Catch on an impling with no insect net wielded and too low a level to catch it barehanded")
    val IMP_LEVEL = line("imp_level", "the message on Catch below the Hunter level the impling needs", "level")
    val IMP_MISS = line("imp_miss", "the message on failing to catch an impling")
    val IMP_JARRED = line("imp_jarred", "the message on catching an impling into an impling jar")
    val IMP_LOOTED = line("imp_looted", "the message on catching an impling with no impling jar, which takes its loot at once")
    val LOOT_SPACE = line("loot_space", "the message on Loot on an impling jar with no free backpack slot")
    val GLOVE_STAYS = line("glove_stays", "the message on removing the falconer's glove, or wearing something over it (Falconer's glove page)")
    val FALCON_FLIES_BACK = line("falcon_flies_back", "the message when the falconer's glove leaves the falconry area, by a teleport (Falconer's glove page)")
    val FALCON_LEFT_PREY = line("falcon_left_prey", "the message when a falcon left on its prey flies off (Falconry page)")
    val FALCON_CAUGHT = line("falcon_caught", "the message when the falcon catches the kebbit")
    val FALCON_MISSED = line("falcon_missed", "the message when the falcon misses the kebbit")
    val FALCON_AWAY = line("falcon_away", "the message on Catch while the falcon is off the glove")
    val KEBBIT_ESCAPES = line("kebbit_escapes", "the message on Catch on a falconry kebbit with no falcon")
    val FALCON_LEVEL = line("falcon_level", "the message on Catch below the Hunter level the kebbit needs", "level")
    val NOT_YOUR_FALCON = line("not_your_falcon", "the message on Retrieve on someone else's falcon")
    val FALCON_RETRIEVED = line("falcon_retrieved", "the message on Retrieve on the player's own falcon")
    val PACK_SPACE = line("pack_space", "the message on Open on a Hunter pack with no room for what it holds")
    val FUR_VALUE = line("fur_value", "Value on a piece in Asyff's Custom Fur Clothing panel", "fee", "count")
    val FUR_NEEDED = line("fur_needed", "Buy on a fur clothing piece without its furs", "count")
    val FUR_COINS = line("fur_coins", "Buy on a fur clothing piece without its fee", "fee")
    val TEASE_LEVEL = line("tease_level", "the message on Tease below the Hunter level the creature needs", "level")
    val TRACK_LEVEL = line("track_level", "the message on Inspect on a tracking den below the Hunter level its creature needs", "level")
    val TRACK_FOUND = line("track_found", "the message on Inspect on a den or connector that shows the trail's next track segment")
    val TRACK_NOTHING = line("track_nothing", "the message on Inspect on a connector that isn't the trail's next one")
    val TRACK_HIDING = line("track_hiding", "the message on Search on the hiding spot a followed trail ends at")
    val TRACK_EMPTY = line("track_empty", "the message on Search or Attack on a hiding spot no followed trail ends at")
    val TRACK_NO_NOOSE = line("track_no_noose", "the message on Attack on the trail's hiding spot with no noose wand wielded, which scares the creature off", "creature")
    val TRACK_ESCAPED = line("track_escaped", "the message on failing to noose the creature in its hiding spot", "creature")
    val TRACK_CAUGHT = line("track_caught", "the message on noosing the creature in its hiding spot", "creature")
}

/**
 * The Hunter skill: the trap engine ([Traps]) and the kinds of trap built so far. Hunter step 1: bird snares
 * ([BirdSnares]); step 2: box traps ([BoxTraps]); step 3: net traps ([NetTraps]); step 4: deadfalls ([Deadfalls]); step 5: pitfalls ([Pitfalls]); step 6a: butterflies and moths ([Butterflies]); step 6b: implings ([Implings]); step 7: falconry ([Falconry]); step 8: tracking ([Tracking]); step 9: the Hunter shops' packs and Asyff's fur clothing ([HunterShops]). Hunter's areas are outside Misthalin; they are built where live has them and reached by ::tele.
 */
class Hunter(world: World, collision: CollisionMap) {
    val traps = Traps(world, collision)
    val pitfalls = Pitfalls(world, traps)
    val butterflies = Butterflies(world)
    val implings = Implings(world)
    val falconry = Falconry(world, collision)
    val shops = HunterShops(world)
    val tracking = Tracking(world)

    fun register() {
        traps.register(BirdSnares.KIND)
        traps.register(BoxTraps.KIND)
        NetTraps.KINDS.forEach(traps::register)
        traps.register(Deadfalls.KIND)
        pitfalls.register()
        butterflies.register()
        implings.register()
        falconry.register()
        shops.register()
        tracking.register()
    }

    /** Call once a tick, in the NPC phase before NPCs move. */
    fun process() {
        traps.process()
        pitfalls.process()
        butterflies.process()
        implings.process()
        falconry.process()
    }

    /** Call once a tick, straight after NPCs move. */
    fun arrivals() {
        traps.arrivals()
        pitfalls.arrivals()
    }
}
