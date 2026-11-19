package osrsps.game.content.randomevents

import osrsps.game.World

/**
 * Rick Turpentine's and the Mysterious Old Man's gift (the OSRS Wiki's Rick Turpentine and Mysterious Old Man pages,
 * Rewards, read 2026-11-19; Mod Ash gave the talisman's 2/75 and said the two share the table). Out of 150: the six coin
 * amounts, kebab, spinach roll, the four uncut gems, a cosmic talisman and the two crystal key halves. The key halves
 * are members' (640 coins on a free world, Mod Ash), and every world here is a members' one.
 */
object Gifts {
    data class Gift(val id: Int, val count: Int, val weight: Int)

    const val COINS = 995

    val TABLE: List<Gift> = listOf(
        Gift(COINS, 80, 10), Gift(COINS, 160, 10), Gift(COINS, 240, 6), Gift(COINS, 320, 10), Gift(COINS, 480, 10), Gift(COINS, 640, 10),
        Gift(1971, 1, 16), // kebab
        Gift(1969, 1, 14), // spinach_roll
        Gift(1623, 1, 32), // uncut_sapphire
        Gift(1621, 1, 16), // uncut_emerald
        Gift(1619, 1, 8), // uncut_ruby
        Gift(1617, 1, 2), // uncut_diamond
        Gift(1454, 1, 4), // cosmic_talisman
        Gift(985, 1, 1), // keyhalf1, the tooth half
        Gift(987, 1, 1), // keyhalf2, the loop half
    )

    val TOTAL: Int = TABLE.sumOf { it.weight }

    /** One gift, by [World.random]. */
    fun roll(world: World): Gift {
        var n = world.random.nextInt(TOTAL)
        for (gift in TABLE) {
            if (n < gift.weight) return gift
            n -= gift.weight
        }
        error("unreachable")
    }
}
