package osrsps.game.content

import osrsps.game.World

/**
 * Bank booths and deposit boxes: every loc type in the cache with a Bank option opens the bank, as the castle's booths
 * (aide_bankbooth, 18491, and its Deadman multiloc 27291) do with op2. Every bank deposit box's Deposit (op1, as the
 * castle's top floor box has it) and every Deposit-box option (Deadman's booths) open the deposit box, and an item
 * used on a deposit box goes in. Collect does nothing yet. The bankers' own options are in [Bankers].
 */
fun registerBanking(world: World) {
    for (type in world.locs.types.all) {
        val bank = type.op(BANK)
        if (bank != 0) world.handlers.onLoc(type.id, bank) { world.bankScreen.open(player) }
        val box = type.op(DEPOSIT)
        if (box != 0 && type.name.equals(DEPOSIT_BOX, ignoreCase = true)) {
            world.handlers.onLoc(type.id, box) { world.depositBox.open(player) }
            world.handlers.onObjOnLoc(type.id) { op -> world.depositBox.useItem(player, op.sub, op.obj) }
        }
        val boothBox = type.op(DEPOSIT_BOX_OPTION)
        if (boothBox != 0) world.handlers.onLoc(type.id, boothBox) { world.depositBox.open(player) }
    }
}

private const val BANK = "Bank"
private const val DEPOSIT = "Deposit"
private const val DEPOSIT_BOX = "Bank deposit box"
private const val DEPOSIT_BOX_OPTION = "Deposit-box"
