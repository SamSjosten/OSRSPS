package osrsps.game

import osrsps.game.combat.RuneCost
import osrsps.game.content.arriveDelay
import osrsps.game.content.message
import osrsps.game.dialogue.Lines

/** The Fountain of Rune's recharge messages (the Wiki's Transcript:Fountain of Rune). */
object FountainOfRuneLines : Lines("fountain_of_rune", "Transcript:Fountain of Rune") {
    val RECHARGE = line("recharge", "jewellery used on the fountain: everything that can be is recharged")
    val ETERNAL = line("eternal", "in place of recharge, when an amulet of glory turns into an amulet of eternal glory")
}

/**
 * The Fountain of Rune south of the Blighted Volcano, level 47 to 48 Wilderness (Wilderness step W3.4d), as the Wiki's
 * page has it:
 *
 * - **Free spells**: a player standing in the area it covers ([covers], the Wiki's polygon of the tiles in sight of
 *   it, by Mod Ash's picture) casts without runes and gets no XP for the cast: combat spells on NPCs and players, and
 *   bolt enchanting. Teleports can't leave this deep in the Wilderness anyway.
 * - **Recharging**: an amulet of glory, a ring of wealth, a skills necklace or a combat bracelet used on the fountain
 *   (multiloc `fountain_of_rune_obelisk` 26782 at 3373,3892) recharges every one in the backpack and worn to six
 *   charges (the ring to five); skills necklaces and combat bracelets in noted form too, glories and rings not. Each
 *   glory has a 1 in 25,000 chance to become an amulet of eternal glory, and that message replaces the usual one.
 *   Every quest counts as done here, so Heroes' and Legends' Quests are never asked for.
 *
 * Inferred: a tile is covered when its middle is inside the Wiki's polygon; "no experience" covering every skill the
 * cast would train (Magic, Hitpoints, Defence); the eternal roll made for each glory recharged; trimmed glories becoming
 * the plain eternal glory; anything else used on it being nothing interesting. Left out: the three seconds before a
 * player can log out after a free cast (no logout timer yet), imbued rings of wealth (the Wiki doesn't say), any
 * animation on a recharge.
 */
object FountainOfRune {
    const val FOUNTAIN = 26782
    const val ETERNAL_GLORY = 19707
    const val ETERNAL_CHANCE = 25_000

    /** The Wiki's polygon, tile coordinates as its map gives them. */
    private val AREA = listOf(
        3366 to 3899, 3367 to 3899, 3367 to 3898, 3368 to 3898, 3368 to 3897, 3369 to 3897, 3369 to 3896, 3368 to 3896,
        3368 to 3893, 3367 to 3893, 3367 to 3890, 3377 to 3890, 3377 to 3889, 3378 to 3889, 3378 to 3888, 3379 to 3888,
        3379 to 3887, 3380 to 3887, 3380 to 3886, 3381 to 3886, 3381 to 3885, 3388 to 3885, 3388 to 3895, 3387 to 3896,
        3386 to 3897, 3385 to 3898, 3384 to 3899, 3383 to 3900, 3382 to 3901, 3381 to 3902, 3380 to 3903, 3366 to 3903,
    )

    /** A kind of jewellery the fountain charges: its charged forms, fewest first, and the one it gives. */
    data class Jewellery(val forms: List<Int>, val full: Int, val notedToo: Boolean, val glory: Boolean = false)

    val JEWELLERY = listOf(
        Jewellery(listOf(1704, 1706, 1708, 1710, 1712, 11976), full = 11978, notedToo = false, glory = true),
        Jewellery(listOf(10362, 10360, 10358, 10356, 10354, 11966), full = 11964, notedToo = false, glory = true),
        Jewellery(listOf(2572, 11988, 11986, 11984, 11982), full = 11980, notedToo = false),
        Jewellery(listOf(11113, 11111, 11109, 11107, 11105, 11970), full = 11968, notedToo = true),
        Jewellery(listOf(11126, 11124, 11122, 11120, 11118, 11974), full = 11972, notedToo = true),
    )

    private val BY_FORM: Map<Int, Jewellery> = JEWELLERY.flatMap { j -> j.forms.map { it to j } }.toMap()

    /** Whether the fountain covers tile [x], [z] on [level]: its middle inside the polygon. */
    fun covers(x: Int, z: Int, level: Int): Boolean {
        if (level != 0) return false
        val px = x + 0.5
        val pz = z + 0.5
        var inside = false
        var j = AREA.size - 1
        for (i in AREA.indices) {
            val (xi, zi) = AREA[i]
            val (xj, zj) = AREA[j]
            if ((zi > pz) != (zj > pz) && px < (xj - xi) * (pz - zi) / (zj - zi).toDouble() + xi) inside = !inside
            j = i
        }
        return inside
    }

    fun covers(player: Player) = covers(player.x, player.z, player.level)

    /** The runes [player] pays for a spell costing [runes]: none at the fountain, else those [staffRunes] don't cover. */
    fun runesToPay(player: Player, runes: List<RuneCost>, staffRunes: Set<Int>): List<RuneCost> =
        if (covers(player)) emptyList() else runes.filter { it.rune !in staffRunes }

    /** The jewellery obj [id] is, unnoted ([note] the noted form's certlink), or null. */
    fun jewellery(id: Int, objTypes: ObjTypes): Jewellery? {
        BY_FORM[id]?.let { return it }
        val type = objTypes[id] ?: return null
        if (!type.isCert) return null
        return BY_FORM[type.certlink]?.takeIf { it.notedToo }
    }

    /**
     * Recharges every piece of jewellery the fountain charges in [player]'s backpack and equipment; true when an amulet
     * turned eternal. [eternal] is asked once for each glory recharged.
     */
    fun recharge(player: Player, objTypes: ObjTypes, eternal: () -> Boolean): Boolean {
        var turned = false
        for (inv in listOf(player.inventory, player.equipment)) {
            for (slot in 0 until inv.capacity) {
                val held = inv[slot] ?: continue
                val unnoted = BY_FORM[held.id]
                if (unnoted != null) {
                    val to = if (unnoted.glory && eternal()) ETERNAL_GLORY.also { turned = true } else unnoted.full
                    inv[slot] = InvObj(to, held.count)
                    continue
                }
                val type = objTypes[held.id] ?: continue
                if (!type.isCert) continue
                val kind = BY_FORM[type.certlink]?.takeIf { it.notedToo } ?: continue
                val noted = objTypes[kind.full]?.certlink ?: continue
                if (noted >= 0) mergeNote(inv, slot, held, noted)
            }
        }
        return turned
    }

    /**
     * The notes in [slot] turned to [noted]: onto a stack of [noted] already held, as stacks merge, or in place. Any that
     * would take that stack past [Inventory.MAX_STACK] stay in [slot] as they were.
     */
    private fun mergeNote(inv: Inventory, slot: Int, held: InvObj, noted: Int) {
        val into = (0 until inv.capacity).firstOrNull { it != slot && inv[it]?.id == noted }
        if (into == null) {
            inv[slot] = InvObj(noted, held.count)
            return
        }
        val have = inv[into]!!.count
        val moved = minOf(held.count.toLong(), Inventory.MAX_STACK.toLong() - have).toInt()
        if (moved <= 0) return
        inv[into] = InvObj(noted, have + moved)
        inv[slot] = if (moved == held.count) null else InvObj(held.id, held.count - moved)
    }

    fun register(world: World) {
        world.handlers.onObjOnLoc(FOUNTAIN) { op ->
            if (jewellery(op.obj, world.objTypes) == null) return@onObjOnLoc world.message(player, GameLines.NOTHING_INTERESTING)
            arriveDelay()
            val turned = recharge(player, world.objTypes) { world.random.nextInt(ETERNAL_CHANCE) == 0 }
            world.message(player, if (turned) FountainOfRuneLines.ETERNAL else FountainOfRuneLines.RECHARGE)
        }
    }
}
