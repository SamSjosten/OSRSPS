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
    val TEASE_LEVEL = line("tease_level", "the message on Tease below the Hunter level the creature needs", "level")
}

/**
 * The Hunter skill: the trap engine ([Traps]) and the kinds of trap built so far. Hunter step 1: bird snares
 * ([BirdSnares]); step 2: box traps ([BoxTraps]); step 3: net traps ([NetTraps]); step 4: deadfalls ([Deadfalls]); step 5: pitfalls ([Pitfalls]); step 6a: butterflies and moths ([Butterflies]); step 6b: implings ([Implings]). Hunter's areas are outside Misthalin; they are built where live has them and reached by ::tele.
 */
class Hunter(world: World, collision: CollisionMap) {
    val traps = Traps(world, collision)
    val pitfalls = Pitfalls(world, traps)
    val butterflies = Butterflies(world)
    val implings = Implings(world)

    fun register() {
        traps.register(BirdSnares.KIND)
        traps.register(BoxTraps.KIND)
        NetTraps.KINDS.forEach(traps::register)
        traps.register(Deadfalls.KIND)
        pitfalls.register()
        butterflies.register()
        implings.register()
    }

    /** Call once a tick, in the NPC phase before NPCs move. */
    fun process() {
        traps.process()
        pitfalls.process()
        butterflies.process()
        implings.process()
    }

    /** Call once a tick, straight after NPCs move. */
    fun arrivals() {
        traps.arrivals()
        pitfalls.arrivals()
    }
}
