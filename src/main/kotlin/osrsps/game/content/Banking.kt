package osrsps.game.content

import osrsps.game.World

/**
 * Bank booths and deposit boxes: every loc type in the cache with a Bank option opens the bank, as the castle's booths
 * (aide_bankbooth, 18491, and its Deadman multiloc 27291) do with op2. Every bank deposit box's Deposit (op1, as the
 * castle's top floor box has it) and every Deposit-box option (Deadman's booths) open the deposit box, and an item
 * used on a deposit box goes in. A bank booth's or Grand Exchange booth's Collect opens the Grand Exchange collection
 * box, and a Grand Exchange booth's Exchange its offers screen. The bankers' own options are in [Bankers].
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
        val collect = type.op(COLLECT)
        val geBooth = type.name.equals(GE_BOOTH, ignoreCase = true)
        if (collect != 0 && (bank != 0 || geBooth)) world.handlers.onLoc(type.id, collect) { world.grandExchangeScreen.openCollectionBox(player) }
        val exchange = type.op(EXCHANGE)
        if (exchange != 0 && geBooth) {
            world.handlers.onLoc(type.id, exchange) { world.grandExchangeScreen.open(player) }
        }
    }
}

private const val BANK = "Bank"
private const val DEPOSIT = "Deposit"
private const val DEPOSIT_BOX = "Bank deposit box"
private const val DEPOSIT_BOX_OPTION = "Deposit-box"
private const val COLLECT = "Collect"
private const val EXCHANGE = "Exchange"
private const val GE_BOOTH = "Grand Exchange booth"
