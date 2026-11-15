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
}

/**
 * The Hunter skill: the trap engine ([Traps]) and the kinds of trap built so far. Hunter step 1: bird snares
 * ([BirdSnares]); step 2: box traps ([BoxTraps]). Hunter's areas are outside Misthalin; they are built where live has them and reached by ::tele.
 */
class Hunter(world: World, collision: CollisionMap) {
    val traps = Traps(world, collision)

    fun register() {
        traps.register(BirdSnares.KIND)
        traps.register(BoxTraps.KIND)
    }

    /** Call once a tick, in the NPC phase before NPCs move. */
    fun process() = traps.process()

    /** Call once a tick, straight after NPCs move. */
    fun arrivals() = traps.arrivals()
}
