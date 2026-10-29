package osrsps.game.combat

import osrsps.game.EquipmentTab
import osrsps.game.Player

/**
 * The spellbook a player has open, in varbit spellbook (4070 in the 241 cache: varp 439, alternate_spells, bits 0 and
 * 1), which the magic tab's scripts redraw the book from. Read and written as raw varp bits, as [Autocast] is, so it
 * works without the cache's varbit types (tests). A spell obj's param 336 says which book it is in
 * ([CombatSpell.spellbook]), with the same numbers. Saved with the other varps.
 */
object Spellbook {
    const val STANDARD = 0
    const val ANCIENT = 1
    const val LUNAR = 2
    const val ARCEUUS = 3

    /** The names `::spellbook` takes, by book. */
    val NAMES = listOf("standard", "ancient", "lunar", "arceuus")

    private const val MASK = 3

    /** The book [player] has open. */
    fun of(player: Player): Int = player.vars[Autocast.VARP_ALTERNATE_SPELLS] and MASK

    /**
     * Opens [book] for [player]. A spell picked to autocast doesn't carry over to another book (from memory of live):
     * it is cleared, and the combat tab's first style is lit again if no other was.
     */
    fun set(player: Player, book: Int) {
        require(book in STANDARD..ARCEUUS) { "spellbook $book" }
        if (book == of(player)) return
        player.vars[Autocast.VARP_ALTERNATE_SPELLS] = (player.vars[Autocast.VARP_ALTERNATE_SPELLS] and MASK.inv()) or book
        if (Autocast.key(player) != 0) {
            Autocast.clear(player)
            if (player.vars[EquipmentTab.VARP_COM_MODE] == Autocast.COM_MODE) player.vars[EquipmentTab.VARP_COM_MODE] = 0
        }
    }
}
