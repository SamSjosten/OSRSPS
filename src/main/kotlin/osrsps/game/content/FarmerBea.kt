package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** Farmer Bea's talk after The Birds and the Wasps (the Wiki's Transcript:Farmer Bea). */
object FarmerBeaLines : Lines("farmer_bea", "Transcript:Farmer Bea") {
    val GREETING = line("greeting", "Bea's greeting")
    val FARM = line("farm", "option and player line: how's the farm going")
    val FARM_1 = line("farm_1", "Bea: how the farm is going")
    val FARM_2 = line("farm_2", "player: good to hear")
    val FARM_3 = line("farm_3", "Bea: the player may hunt her creatures, and she swaps darts")
    val FOR_SALE = line("for_sale", "option and player line: anything for sale")
    val FOR_SALE_1 = line("for_sale_1", "Bea, before her shop opens")
    val DARTS = line("darts", "option and player line: can they trade in some darts")
    val DARTS_RULE = line("darts_rule", "Bea: 35 wasp darts or 20 killer wasp darts for a bag of supplies")
    val TIPS = line("tips", "option and player line: any hunting tips")
    val TIPS_1 = line("tips_1", "Bea, before her tips menu")
    val TIPS_LOCAL = line("tips_local", "tips option and player line: hunting around here")
    val TIPS_LOCAL_1 = line("tips_local_1", "Bea: the local birds and wasps")
    val TIPS_LOCAL_2 = line("tips_local_2", "Bea: coloured birds and butterflies nearby")
    val TIPS_LOCAL_3 = line("tips_local_3", "Bea: how to catch them, butterflies in jars")
    val TIPS_FAR = line("tips_far", "tips option and player line: hunting further afield")
    val TIPS_FAR_1 = line("tips_far_1", "Bea: the Hunter Guild in Varlamore")
    val TIPS_FAR_2 = line("tips_far_2", "Bea: three hunting areas further out")
    val TIPS_BACK = line("tips_back", "tips option: something else, back to her menu")
    val BYE = line("bye", "option and player line: they'd best be off")
    val NOT_ENOUGH = line("not_enough", "Swap-darts without 35 wasp darts or 20 killer wasp darts")
    val SWAPPED = line("swapped", "Swap-darts: the darts go and Bea's supplies come")
}

/**
 * Farmer Bea at her farm north-west of the Exam Centre, after The Birds and the Wasps (post-quest content step 3). Her
 * shop is shops.json's (Bea's Hunting Gear, Hunter step 9); this is her Talk-to, Swap-darts and the supplies she swaps.
 * From the OSRS Wiki's Farmer Bea, Transcript:Farmer Bea and Bea's supplies pages (read 2026-11-19).
 *
 * - **Talk-to**: her greeting and menu: the farm, the shop (her line, then the shop), the dart swap's rule, hunting
 *   tips (local, further afield, back), and leaving. Every branch but the shop and leaving comes back to the menu.
 * - **Swap-darts**: 35 wasp darts (34787) or 20 killer wasp darts (34789) for one Bea's supplies (34791).
 * - **Bea's supplies**, Open: one of the Wiki's ten rewards, weighted as its 1,178-sample table (out of 60): headless
 *   arrows 16, coins 8, bronze arrowtips 8, bronze darts 8, leather 5, iron arrowtips 5, iron darts 5, hard leather 3,
 *   steel arrowtips 1, steel darts 1, each in the table's range.
 *
 * - Inferred: one reward a bag (the table's rarities add up to 60/60).
 * - Guessed: every chathead expression; wasp darts taken before killer wasp darts when the player has both; one bag a
 *   swap; the swap and the open wanting room ([GameLines.NO_SPACE]) when no slot comes free. The transcript gives no
 *   line for a swap that works, so it shows [FarmerBeaLines.SWAPPED] as a message. Left out: hunting the farm's wasps
 *   and birds, and fletching the darts (no system for them yet).
 */
object FarmerBea {
    /** baw_bea_3ops, the Bea who trades after the quest. */
    const val BEA = 3218
    const val TALK_TO = 1
    const val SWAP_DARTS = 4

    const val WASP_DART = 34787
    const val KILLER_WASP_DART = 34789
    const val SUPPLIES = 34791
    const val OPEN = 1

    const val WASP_DARTS_A_BAG = 35
    const val KILLER_WASP_DARTS_A_BAG = 20

    class Reward(val item: Int, val min: Int, val max: Int, val weight: Int)

    /** The Wiki's table, out of 60. */
    val REWARDS = listOf(
        Reward(53, 60, 140, 16), // headless arrow
        Reward(995, 150, 300, 8), // coins
        Reward(39, 60, 140, 8), // bronze arrowtips
        Reward(806, 30, 70, 8), // bronze dart
        Reward(1741, 2, 6, 5), // leather
        Reward(40, 60, 140, 5), // iron arrowtips
        Reward(807, 30, 70, 5), // iron dart
        Reward(1743, 2, 6, 3), // hard leather
        Reward(41, 60, 140, 1), // steel arrowtips
        Reward(808, 30, 70, 1), // steel dart
    )

    fun roll(world: World): Pair<Int, Int> {
        var n = world.random.nextInt(REWARDS.sumOf { it.weight })
        for (reward in REWARDS) {
            if (n < reward.weight) return reward.item to world.random.between(reward.min, reward.max)
            n -= reward.weight
        }
        error("unreachable")
    }

    /** The darts [player] would swap: the first kind they hold enough of, and how many. */
    fun darts(player: Player): Pair<Int, Int>? = when {
        player.inventory.count(WASP_DART) >= WASP_DARTS_A_BAG -> WASP_DART to WASP_DARTS_A_BAG
        player.inventory.count(KILLER_WASP_DART) >= KILLER_WASP_DARTS_A_BAG -> KILLER_WASP_DART to KILLER_WASP_DARTS_A_BAG
        else -> null
    }

    /** Takes [count] of [id] out of [player]'s backpack (a stack, so one slot). */
    private fun take(player: Player, id: Int, count: Int) {
        val slot = player.inventory.contents().firstOrNull { it.second.id == id }?.first ?: return
        player.inventory.remove(slot, count)
    }

    fun swap(world: World, player: Player) {
        val (dart, count) = darts(player) ?: return world.message(player, FarmerBeaLines.NOT_ENOUGH)
        val type = world.objTypes[SUPPLIES] ?: return
        if (player.inventory.freeSlots == 0 && player.inventory.count(dart) > count) return world.message(player, GameLines.NO_SPACE)
        take(player, dart, count)
        player.inventory.add(type, 1)
        world.message(player, FarmerBeaLines.SWAPPED)
    }

    fun open(world: World, player: Player, slot: Int) {
        if (player.inventory[slot]?.id != SUPPLIES) return
        val (item, count) = roll(world)
        val type = world.objTypes[item] ?: return
        val stacks = player.inventory.count(item) > 0 && type.stackable
        // The bag's own slot comes free, so a non-stacking reward of one fits; leather and hard leather come several.
        if (!type.stackable && count > player.inventory.freeSlots + 1) return world.message(player, GameLines.NO_SPACE)
        player.inventory.remove(slot, 1)
        if (type.stackable || stacks) player.inventory.add(type, count) else repeat(count) { player.inventory.add(type, 1) }
    }

    /** Bea's talk; true when her shop should open. */
    internal suspend fun Dialogue.talk(): Boolean = with(FarmerBeaLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        while (true) {
            when (choice(FARM, FOR_SALE, DARTS, TIPS, BYE)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, FARM)
                    chatNpc(ChatAnim.HAPPY, FARM_1)
                    chatPlayer(ChatAnim.HAPPY, FARM_2)
                    chatNpc(ChatAnim.NEUTRAL, FARM_3)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, FOR_SALE)
                    chatNpc(ChatAnim.HAPPY, FOR_SALE_1)
                    return@with true
                }
                3 -> {
                    chatPlayer(ChatAnim.QUIZ, DARTS)
                    chatNpc(ChatAnim.NEUTRAL, DARTS_RULE)
                }
                4 -> {
                    chatPlayer(ChatAnim.QUIZ, TIPS)
                    chatNpc(ChatAnim.HAPPY, TIPS_1)
                    tips@ while (true) {
                        when (choice(TIPS_LOCAL, TIPS_FAR, TIPS_BACK)) {
                            1 -> {
                                chatPlayer(ChatAnim.QUIZ, TIPS_LOCAL)
                                chatNpc(ChatAnim.NEUTRAL, TIPS_LOCAL_1)
                                chatNpc(ChatAnim.NEUTRAL, TIPS_LOCAL_2)
                                chatNpc(ChatAnim.NEUTRAL, TIPS_LOCAL_3)
                            }
                            2 -> {
                                chatPlayer(ChatAnim.QUIZ, TIPS_FAR)
                                chatNpc(ChatAnim.NEUTRAL, TIPS_FAR_1)
                                chatNpc(ChatAnim.NEUTRAL, TIPS_FAR_2)
                            }
                            else -> break@tips
                        }
                    }
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, BYE)
                    return@with false
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        false
    }
}

fun registerFarmerBea(world: World) = with(FarmerBea) {
    world.handlers.onNpc(BEA, TALK_TO) { op ->
        var shop = false
        world.dialogues.start(this, op.npc) { shop = talk() }
        if (shop) world.shopTypes.ofNpc(BEA)?.let { world.shopScreen.open(player, it) }
    }
    world.handlers.onNpc(BEA, SWAP_DARTS) { swap(world, player) }
    world.handlers.onObj(SUPPLIES, OPEN) { op -> open(world, player, op.sub) }
}
