package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** A camel's talk without a camulet; the words are in the dialogue file, from the Wiki's Transcript:Camel, read 2026-10-30. */
object CamelLines : Lines("camel", "Transcript:Camel") {
    val DUNG = line("dung", "option 1, which the player then says")
    val DUNG_NO = line("dung_no", "the box after it: the camel doesn't understand")
    val UNPLEASANT = line("unpleasant", "option 2")
    val NEITHER = line("neither", "option 3, which the player then says")
    val RUDE_1 = line("rude_1", "option 2: the player's first random line (the kebab)")
    val RUDE_2 = line("rude_2", "option 2: the player's second random line (the bitten hand)")
    val RUDE_3 = line("rude_3", "option 2: the player's third random line (fleas)")
    val REACTS_1 = line("reacts_1", "option 2: the box with the camel's first random reaction (the player jumps back quickly)")
    val REACTS_2 = line("reacts_2", "option 2: the box with its second (it glares)")
    val REACTS_3 = line("reacts_3", "option 2: the box with its third (the player jumps back hurriedly)")
}

/**
 * Al Kharid's camels (OSRS Wiki, Camel and each named camel's page, read 2026-10-30): the plain Camel (2835) north of
 * town, and Al, Elly, Ollie, Cam and Alice, all Talk-to only. Without a camulet a camel's talk is a menu of three: ask
 * for dung (it doesn't understand), say something unpleasant (one of three lines, then one of three reactions, each at
 * random), or neither.
 *
 * Alice is `alice_the_camel_multinpc` (5953) on the map, which varbit 1353 shows as a plain camel or as Alice (after
 * Rogue Trader, per the Wiki). Left out: the camulet's talk and the dung it leads to (members, Enakhra's Lament and My
 * Arm's Big Adventure), and Cam's treasure trail step. Guessed: the expressions, that the random lines are even chances.
 */
object AlKharidCamels {
    const val CAMEL = 2835
    const val AL = 5949
    const val ELLY = 5950
    const val OLLIE = 5951
    const val CAM = 5952
    const val ALICE = 5954
    const val TALK_TO = 1

    val ALL = listOf(CAMEL, AL, ELLY, OLLIE, CAM, ALICE)

    /** A camel's talk; [roll] gives a number below its argument, for the random lines. */
    internal suspend fun Dialogue.camel(roll: (Int) -> Int) = with(CamelLines) {
        when (choice(DUNG, UNPLEASANT, NEITHER)) {
            1 -> {
                chatPlayer(ChatAnim.NEUTRAL, DUNG)
                mesbox(DUNG_NO)
            }
            2 -> {
                chatPlayer(ChatAnim.NEUTRAL, listOf(RUDE_1, RUDE_2, RUDE_3)[roll(3)])
                mesbox(listOf(REACTS_1, REACTS_2, REACTS_3)[roll(3)])
            }
            else -> chatPlayer(ChatAnim.NEUTRAL, NEITHER)
        }
    }
}

fun registerAlKharidCamels(world: World) {
    for (camel in AlKharidCamels.ALL) {
        world.handlers.onNpc(camel, AlKharidCamels.TALK_TO) { op ->
            world.dialogues.start(this, op.npc) { with(AlKharidCamels) { camel { world.random.nextInt(it) } } }
        }
    }
}
