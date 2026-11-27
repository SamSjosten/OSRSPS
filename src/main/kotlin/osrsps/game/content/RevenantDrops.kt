package osrsps.game.content

import kotlin.math.sqrt
import osrsps.game.GameRandom
import osrsps.game.ObjType
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.combat.Drop

/**
 * The revenants' shared drops (Wilderness step W5.3; the Wiki's Template:Revenants/Drops, from Jagex's data, read
 * 2026-11-15). They hang on the revenant's combat level `c`, with `A = floor(2200 / floor(sqrt(c)))` and
 * `B = 15 + floor((c + 60)^2 / 200)`:
 *
 * - **Always** revenant ether, an even count from 2 to `(floor(sqrt(c)) + 1) * 2`.
 * - **The uniques**: 1 in `A * 26.667` (`A * 14.667` skulled), then an amulet of avarice 2 in 5 and Craw's bow,
 *   Thammaron's sceptre or Viggora's chainmace 1 in 5 each.
 * - **The main roll** of 1 to `A`: 1 is the ancient statuettes table ([STATUETTES], of 40; [STATUETTES_SKULLED], of
 *   22, where the emblem is rarer and the dragon med helm gone); 2 to `min(A, B)` the weapons, armour and resources
 *   ([MAIN], of 198); above `B` coins, 1 to `1 + 25 * floor(sqrt(c))`.
 * - **Secondary**: 1 in `250 - c` for each hundredth of [SECONDARY]'s blighted supplies.
 * - **Tertiary**: a looting bag (1 in 15 below combat 10, 1 in 3 above 50, else 1 in `floor(150 / c)`), and the imp's,
 *   goblin's and hobgoblin's champion scroll at 1 in 5,000.
 *
 * INFERRED: the uniques rolled apart from the main roll, and the statuettes table's slots not taken by an item giving
 * nothing (the Wiki's rates then come out as it gives them). Not built: the uniques' fivefold rate on a revenants Slayer
 * task, the amulet of avarice noting the drops, the Wilderness Slayer tertiary table.
 */
object RevenantDrops {
    /** A drop line: an item, its count's range and its weight in its table. */
    class Line(val item: Int, val min: Int, val max: Int, val weight: Int)

    const val ETHER = 21820
    const val COINS = 995
    const val AVARICE = 22557
    const val CRAWS_BOW = 22547
    const val THAMMARONS_SCEPTRE = 22552
    const val VIGGORAS_CHAINMACE = 22542
    const val LOOTING_BAG = 11941
    const val LOOTING_BAG_OPEN = 22586

    /**
     * Whether [player] has a looting bag, closed or open, in the backpack or bank, one dropped for them still on the
     * ground (so kills before a pickup don't pile them up: Codex review on #1014), or one in their grave (exploit review
     * R65B-7).
     */
    fun ownsBag(world: World, player: Player): Boolean {
        val grave = world.deaths.graveOf(player)?.items
        return listOf(LOOTING_BAG, LOOTING_BAG_OPEN).any {
            player.inventory.count(it) > 0 || player.bank.count(it) > 0 || world.groundObjs.hasDropped(player, it) || (grave?.count(it) ?: 0) > 0
        }
    }

    const val ANCIENT_EMBLEM = 21807
    const val ANCIENT_TOTEM = 21810
    const val ANCIENT_STATUETTE = 21813
    const val ANCIENT_CRYSTAL = 21804
    const val ANCIENT_MEDALLION = 22299
    const val ANCIENT_EFFIGY = 22302
    const val ANCIENT_RELIC = 22305

    val STATUETTES = listOf(
        Line(ANCIENT_RELIC, 1, 1, 1), Line(ANCIENT_EFFIGY, 1, 1, 1), Line(ANCIENT_MEDALLION, 1, 1, 1), Line(ANCIENT_STATUETTE, 1, 1, 2),
        Line(5316, 2, 6, 4), Line(5315, 2, 6, 4), Line(ANCIENT_CRYSTAL, 1, 1, 3), Line(ANCIENT_TOTEM, 1, 1, 4), Line(ANCIENT_EMBLEM, 1, 1, 6),
        Line(1149, 1, 1, 13),
    )
    const val STATUETTES_OF = 40

    val STATUETTES_SKULLED = listOf(
        Line(ANCIENT_RELIC, 1, 1, 1), Line(ANCIENT_EFFIGY, 1, 1, 1), Line(ANCIENT_MEDALLION, 1, 1, 1), Line(ANCIENT_STATUETTE, 1, 1, 2),
        Line(5316, 2, 6, 4), Line(5315, 2, 6, 4), Line(ANCIENT_CRYSTAL, 1, 1, 3), Line(ANCIENT_TOTEM, 1, 1, 4), Line(ANCIENT_EMBLEM, 1, 1, 1),
    )
    const val STATUETTES_SKULLED_OF = 22

    /**
     * The weapons, armour, resources and other drops, of 198. The template's noted drops by their noted ids (unnoted id
     * + 1: mahogany planks 8783 for 8782, not 8784, gold leaf (exploit review R65B-1); coal 454, yew logs 1516 and magic
     * logs 1514 are the notes already), so each lands as one stack (Codex review
     * on #1014). The bracelet of ethereum and the stackables are as they are.
     */
    val MAIN = listOf(
        Line(21817, 1, 1, 30), Line(1392, 4, 4, 10),
        Line(1164, 2, 2, 4), Line(1128, 2, 2, 4), Line(1080, 2, 2, 4), Line(1202, 2, 2, 4), Line(1348, 2, 2, 4),
        Line(4088, 1, 2, 1), Line(4586, 1, 2, 1), Line(1216, 2, 2, 2), Line(1306, 2, 2, 2),
        Line(24598, 1, 3, 6), Line(24589, 10, 15, 8), Line(454, 30, 60, 12), Line(2362, 4, 6, 12), Line(452, 2, 4, 8),
        Line(2364, 2, 3, 6), Line(1748, 4, 4, 8), Line(8783, 8, 16, 6), Line(1516, 20, 40, 8), Line(1514, 8, 16, 4),
        Line(1632, 2, 5, 2),
        Line(21802, 1, 5, 10), Line(9193, 20, 40, 6), Line(9194, 3, 6, 6), Line(563, 20, 45, 10), Line(560, 30, 60, 10),
        Line(565, 50, 100, 10),
    )
    const val MAIN_OF = 198

    /** The blighted supplies, of 100, rolled at 100 in `250 - c`. */
    val SECONDARY = listOf(
        Line(24607, 1, 10, 10), Line(24613, 1, 10, 20), Line(24615, 1, 10, 10), Line(24621, 1, 10, 10),
        Line(24589, 1, 2, 15), Line(24592, 1, 2, 15), Line(24595, 1, 2, 10), Line(24598, 1, 1, 5), Line(21802, 1, 1, 5),
    )

    /** The champion scrolls, by revenant type. */
    val CHAMPION_SCROLLS = mapOf(7881 to 6803, 7931 to 6801, 7933 to 6802)
    const val CHAMPION_SCROLL_ODDS = 5_000

    fun rootLevel(combat: Int): Int = sqrt(combat.toDouble()).toInt()

    fun a(combat: Int): Int = 2200 / rootLevel(combat)

    fun b(combat: Int): Int = 15 + (combat + 60) * (combat + 60) / 200

    /**
     * One kill's drops of a revenant of [typeId] and [combat] level, for a killer [skulled] or not; [types] names the
     * items. No looting bag for a killer who [ownsBag] (OSRS Wiki, Looting bag: "Only one looting bag can be in a
     * player's possession at a time").
     */
    fun roll(random: GameRandom, typeId: Int, combat: Int, skulled: Boolean, ownsBag: Boolean = false, types: (Int) -> ObjType?): List<Drop> {
        val out = ArrayList<Drop>()
        fun add(id: Int, count: Int) {
            types(id)?.let { out += Drop(it, count) }
        }
        val root = rootLevel(combat)
        val a = a(combat)
        add(ETHER, 2 * random.between(1, root + 1))
        // 26.667 is 80/3 and 14.667 is 44/3.
        if (random.chance(3, a * if (skulled) 44 else 80)) {
            add(
                when (random.between(1, 5)) {
                    1, 2 -> AVARICE
                    3 -> CRAWS_BOW
                    4 -> THAMMARONS_SCEPTRE
                    else -> VIGGORAS_CHAINMACE
                },
                1,
            )
        }
        val main = random.between(1, a)
        when {
            main == 1 -> pick(random, if (skulled) STATUETTES_SKULLED else STATUETTES, if (skulled) STATUETTES_SKULLED_OF else STATUETTES_OF)
                ?.let { add(it.item, random.between(it.min, it.max)) }
            main <= minOf(a, b(combat)) -> pick(random, MAIN, MAIN_OF)?.let { add(it.item, random.between(it.min, it.max)) }
            else -> add(COINS, random.between(1, 1 + 25 * root))
        }
        if (random.between(1, 250 - combat) <= 100) pick(random, SECONDARY, 100)?.let { add(it.item, random.between(it.min, it.max)) }
        val bag = when {
            combat < 10 -> 15
            combat > 50 -> 3
            else -> 150 / combat
        }
        if (random.chance(1, bag) && !ownsBag) add(LOOTING_BAG, 1)
        CHAMPION_SCROLLS[typeId]?.let { if (random.chance(1, CHAMPION_SCROLL_ODDS)) add(it, 1) }
        return out
    }

    /** A line of [lines] by weight out of [of], or null for a slot no line takes. */
    private fun pick(random: GameRandom, lines: List<Line>, of: Int): Line? {
        var roll = random.between(1, of)
        for (line in lines) {
            roll -= line.weight
            if (roll <= 0) return line
        }
        return null
    }
}
