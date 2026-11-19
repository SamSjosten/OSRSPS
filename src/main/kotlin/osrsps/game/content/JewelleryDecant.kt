package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.skill.Making

/**
 * Murky Matt's combining of teleport jewellery charges (OSRS Wiki, Murky Matt (runes) and its transcript, read
 * 2026-11-19): like decanting potions, the charges on all of a kind in the backpack, noted or not, are put together on
 * as few pieces as they fill (two one-charge games necklaces become one with two charges); a piece charged past its normal
 * capacity (a glory charged to 6 at the Fountain of Rune) isn't taken. Rings of forging he enchants from ruby rings for
 * 250 coins each.
 *
 * Inferred: the kinds, those whose cache names run a charge chain from full to one (games necklace and ring of dueling
 * 8, slayer ring 8, glory and glory (t) 4, skills necklace and combat bracelet 4, ring of wealth and ring of wealth (i)
 * 5, ring of returning, necklace of passage and burning amulet 5). Guessed: the pieces come back unnoted, full ones
 * first then the remainder; the pieces left over come back as the kind's empty form where it has one (an uncharged
 * glory, skills necklace, combat bracelet or ring of wealth) and are gone where it crumbles; a kind with nothing to
 * gain (one piece, or already as few as can be) is left alone; and the backpack has to hold what comes back, all or
 * nothing. The Wiki's Pharaoh's sceptre and his line about dragonstone and the Fountain of Rune aren't here.
 */
object JewelleryDecant {
    /** A kind of jewellery: its ids by charge, [byCharge][0] holding one charge, and its empty form (-1 for none). */
    class Kind(val key: String, fullFirst: List<Int>, val empty: Int, val noted: Boolean = true) {
        val byCharge: List<Int> = fullFirst.reversed()
        val capacity: Int get() = byCharge.size

        /** The charges [id] holds as this kind, noted or not; 0 for none of it. */
        fun charges(id: Int): Int {
            val index = byCharge.indexOf(id).takeIf { it >= 0 } ?: if (noted) byCharge.indexOf(id - 1) else -1
            return index + 1
        }
    }

    private fun every2(first: Int, count: Int) = List(count) { first + 2 * it }

    val KINDS = listOf(
        Kind("games_necklace", every2(3853, 8), -1),
        Kind("ring_of_dueling", every2(2552, 8), -1),
        Kind("slayer_ring", (11866..11873).toList(), -1, noted = false),
        Kind("amulet_of_glory", listOf(1712, 1710, 1708, 1706), 1704),
        Kind("amulet_of_glory_t", listOf(10354, 10356, 10358, 10360), 10362),
        Kind("skills_necklace", listOf(11105, 11107, 11109, 11111), 11113),
        Kind("combat_bracelet", listOf(11118, 11120, 11122, 11124), 11126),
        Kind("ring_of_wealth", every2(11980, 5), 2572),
        Kind("ring_of_wealth_i", (20786..20790).toList(), 12785, noted = false),
        Kind("ring_of_returning", listOf(21129, 21132, 21134, 21136, 21138), -1),
        Kind("necklace_of_passage", listOf(21146, 21149, 21151, 21153, 21155), -1),
        Kind("burning_amulet", listOf(21166, 21169, 21171, 21173, 21175), -1),
    )

    const val RUBY_RING = 1641
    const val RING_OF_FORGING = 2568
    const val COINS = 995
    const val FORGING_PRICE = 250

    /** What combining [player]'s jewellery comes to. */
    enum class Result { NOTHING, NO_SPACE, DONE }

    /** The pieces [pieces] of charges come back as: full ones, then the remainder, then the empty forms. */
    private fun combined(kind: Kind, pieces: List<Int>): List<Int> {
        val total = pieces.sum()
        val full = total / kind.capacity
        val rest = total % kind.capacity
        val charged = List(full) { kind.capacity } + listOfNotNull(rest.takeIf { it > 0 })
        val ids = charged.map { kind.byCharge[it - 1] }
        return if (kind.empty >= 0) ids + List(pieces.size - charged.size) { kind.empty } else ids
    }

    /** Combines every kind in [player]'s backpack that can be, all or nothing. */
    fun combine(world: World, player: Player): Result {
        val inventory = player.inventory
        val saved = inventory.contents().toMap()
        val outputs = ArrayList<Int>()
        var any = false
        for (kind in KINDS) {
            val pieces = ArrayList<Int>()
            val slots = ArrayList<Int>()
            for ((slot, held) in inventory.contents()) {
                val charges = kind.charges(held.id)
                if (charges <= 0) continue
                repeat(held.count) { pieces += charges }
                slots += slot
            }
            val back = combined(kind, pieces)
            // Nothing to gain: as many charged pieces come back as went in.
            if (pieces.size < 2 || back.count { it != kind.empty } >= pieces.size) continue
            any = true
            for (slot in slots) inventory[slot] = null
            outputs += back
        }
        if (!any) return Result.NOTHING
        for (id in outputs) {
            val type = world.objTypes[id] ?: error("No obj type $id")
            if (inventory.add(type, 1) < 1) {
                for (slot in 0 until inventory.capacity) inventory[slot] = saved[slot]
                return Result.NO_SPACE
            }
        }
        return Result.DONE
    }

    /** How many ruby rings [player] holds, noted or not. */
    fun rubyRings(player: Player): Long = player.inventory.count(RUBY_RING) + player.inventory.count(RUBY_RING + 1)

    /**
     * Enchants up to [wanted] ruby rings, as many as are held and paid for at [FORGING_PRICE], into rings of forging;
     * how many were. Guessed: unnoted rings first, the rings of forging coming back noted for noted rubies.
     */
    fun enchant(world: World, player: Player, wanted: Int): Int {
        val inventory = player.inventory
        val afford = (inventory.count(COINS) / FORGING_PRICE).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val count = minOf(wanted.toLong(), rubyRings(player), afford.toLong()).toInt()
        if (count <= 0) return 0
        val saved = inventory.contents().toMap()
        val plain = minOf(count.toLong(), inventory.count(RUBY_RING)).toInt()
        val noted = count - plain
        if (plain > 0) Making.removeObj(inventory, RUBY_RING, plain)
        if (noted > 0) Making.removeObj(inventory, RUBY_RING + 1, noted)
        Making.removeObj(inventory, COINS, count * FORGING_PRICE)
        val forging = world.objTypes[RING_OF_FORGING] ?: error("No ring of forging")
        val forgingNoted = world.objTypes[RING_OF_FORGING + 1]
        val fits = (plain == 0 || inventory.add(forging, plain) == plain) && (noted == 0 || (forgingNoted != null && inventory.add(forgingNoted, noted) == noted))
        if (!fits) {
            for (slot in 0 until inventory.capacity) inventory[slot] = saved[slot]
            return 0
        }
        return count
    }
}
