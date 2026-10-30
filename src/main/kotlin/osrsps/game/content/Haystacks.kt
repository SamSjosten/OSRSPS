package osrsps.game.content

import osrsps.game.World
import osrsps.game.combat.HitsplatType
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** What searching a haystack says; the words are in the dialogue file, from the Wiki's Haystack page. */
object HaystackLines : Lines("haystack", "Haystack") {
    val NOTHING = line("nothing", "Search: nothing found, most of the time")
    val NEEDLE = line("needle", "Search: a needle found (the message remarks on the odds)")
    val PRICKED = line("pricked", "Search: the player is pricked and loses a hitpoint")
}

/**
 * Haystacks and hay bales (`haystack`, `haystack2`, `haystack3` and `haybail`: 298 to 300 and 304): Search finds a
 * needle 10% of the time and pricks the player for 1 hitpoint 2% of the time, else nothing (the OSRS Wiki's Haystack
 * page, read 2026-10-30, which lists Edgeville's grange and the farms north of Lumbridge among their places). The page
 * names haystacks only; the hay bales share the search here, as they share the option. Guessed: one roll decides all
 * three outcomes (prick first, then needle), a needle with a full backpack is not found, no animation or wait, and the
 * prick is a plain damage hitsplat that can't be blocked.
 */
object Haystacks {
    val TYPES = listOf(298, 299, 300, 304)
    const val SEARCH = 1

    /** Needle. */
    const val NEEDLE = 1733

    /** Out of [ROLL]: below [PRICK] pricks, below [PRICK] + [FIND] finds a needle. */
    const val ROLL = 100
    const val PRICK = 2
    const val FIND = 10
}

fun registerHaystacks(world: World) {
    for (id in Haystacks.TYPES) world.handlers.onLoc(id, Haystacks.SEARCH) { searchHaystack(world) }
}

private fun ScriptContext.searchHaystack(world: World) {
    val roll = world.random.nextInt(Haystacks.ROLL)
    when {
        roll < Haystacks.PRICK -> {
            world.message(player, HaystackLines.PRICKED)
            world.meleeCombat.statusHit(player, 1, HitsplatType.DAMAGE_ME, HitsplatType.DAMAGE_OTHER)
        }
        roll < Haystacks.PRICK + Haystacks.FIND && player.inventory.freeSlots > 0 -> {
            world.objTypes[Haystacks.NEEDLE]?.let { player.inventory.add(it, 1) }
            world.message(player, HaystackLines.NEEDLE)
        }
        else -> world.message(player, HaystackLines.NOTHING)
    }
}
