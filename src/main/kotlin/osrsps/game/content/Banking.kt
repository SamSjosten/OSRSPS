package osrsps.game.content

import osrsps.game.World

/**
 * Bank booths: every loc type in the cache with a Bank option opens the bank, as the castle's booths (aide_bankbooth,
 * 18491, and its Deadman multiloc 27291) do with op2. Collect and Deposit-box do nothing yet. The bankers' own options
 * are in [Bankers].
 */
fun registerBanking(world: World) {
    for (type in world.locs.types.all) {
        val option = type.op(BANK)
        if (option != 0) world.handlers.onLoc(type.id, option) { world.bankScreen.open(player) }
    }
}

private const val BANK = "Bank"
