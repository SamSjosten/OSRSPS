package osrsps.game.content

import java.time.LocalDate
import java.time.ZoneOffset
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.Chatbox
import osrsps.game.dialogue.Lines

/** The barrel's words, from the Wiki's Transcript:Barrel (Zaff's Superior Staffs!), read 2026-11-19. */
object ZaffBarrelLines : Lines("zaff_barrel", "Transcript:Barrel (Zaff's Superior Staffs!)") {
    val NOT_FOR_YOU = line("not_for_you", "Claim-staves with no Varrock Diary tier done: the game message")
    val NEED_SPACE = line("need_space", "Claim-staves with no free space (and no noted battlestaves to stack on), or a stack too full for the day's staves: the box")
    val BUY = line("buy", "Claim-staves: the box asking whether to buy them", "count", "total")
    val YES = line("yes", "the option to buy")
    val NO = line("no", "the option not to")
    val NEED_COINS = line("need_coins", "Yes without the coins: the box", "total")
}

/**
 * Zaff's barrel of battlestaves (the multiloc 30357 on the map: full 26462, empty 30358), the Varrock Diary's daily
 * staves: once a day, by UTC day, 15, 30, 60 or 120 noted battlestaves for 7,000 coins each, by the highest tier done
 * (the Wiki's barrel page and transcript). Varbit `zaff_last_claimed` (4539) is set once they're bought, which turns the
 * barrel empty (the Wiki: its look changes once the day's staves are bought), and cleared at login on a later day.
 *
 * Guessed: the highest tier whose tasks are all done counts, claimed or not; the barrel only fills again at a login on a
 * later day (as the Explorer's ring's uses), and a Claim-staves sent while it's empty does nothing.
 */
class ZaffBarrel(private val world: World, private val today: () -> Long = { LocalDate.now(ZoneOffset.UTC).toEpochDay() }) {
    fun register() {
        // The map holds the multiloc (fai_varrock_battlestaff_barrel_multi, by zaff_last_claimed), whose id the client
        // sends; its Claim-staves is the full barrel's op1.
        for (loc in listOf(MULTI, FULL, EMPTY)) {
            val option = world.locs.types[loc]?.ops?.indexOfFirst { it == "Claim-staves" }?.takeIf { it >= 0 }?.plus(1) ?: 1
            world.handlers.onLoc(loc, option) {
                val tier = world.diaries.highestDone(player, DiaryArea.VARROCK)
                    ?: return@onLoc world.message(player, ZaffBarrelLines.NOT_FOR_YOU)
                if (claimedToday(player)) return@onLoc
                val count = STAVES[tier.ordinal]
                val total = count * PRICE
                world.dialogues.start(this) {
                    if (player.inventory.freeSlots == 0 && player.inventory.count(NOTED_BATTLESTAFF) == 0L ||
                        player.inventory.count(NOTED_BATTLESTAFF) + count > Int.MAX_VALUE
                    ) {
                        return@start mesbox(ZaffBarrelLines.NEED_SPACE)
                    }
                    mesbox(ZaffBarrelLines.BUY, "count" to count, "total" to "%,d".format(total))
                    if (choice(ZaffBarrelLines.YES, ZaffBarrelLines.NO, title = Chatbox.SELECT_AN_OPTION) != 1) return@start
                    // Checked again after the boxes: the coins or the space may have gone meanwhile, or the day's
                    // staves been bought in another talk.
                    if (claimedToday(player)) return@start
                    if (player.inventory.count(COINS) < total) return@start mesbox(ZaffBarrelLines.NEED_COINS, "total" to "%,d".format(total))
                    val noted = world.objTypes[NOTED_BATTLESTAFF] ?: return@start
                    if (player.inventory.freeSlots == 0 && player.inventory.count(NOTED_BATTLESTAFF) == 0L) return@start mesbox(ZaffBarrelLines.NEED_SPACE)
                    // The whole buy must fit on the stack, so no coins go for staves that can't be held.
                    if (player.inventory.count(NOTED_BATTLESTAFF) + count > Int.MAX_VALUE) return@start mesbox(ZaffBarrelLines.NEED_SPACE)
                    var left = total
                    for ((slot, held) in player.inventory.contents()) {
                        if (left == 0) break
                        if (held.id == COINS) left -= player.inventory.remove(slot, left)
                    }
                    player.inventory.add(noted, count)
                    player.charges[DAY] = today().toInt()
                    setVarbit(player, 1)
                }
            }
        }
    }

    /** At login: the barrel full again on a day after the last buy. */
    fun login(player: Player) = setVarbit(player, if (claimedToday(player)) 1 else 0)

    private fun claimedToday(player: Player) = player.charges[DAY] == today().toInt() && player.charges[DAY] != 0

    private fun setVarbit(player: Player, value: Int) {
        if (player.vars.types.varbit(VARBIT_CLAIMED) != null) player.vars.setVarbit(VARBIT_CLAIMED, value)
    }

    companion object {
        /** fai_varrock_battlestaff_barrel_multi: the barrel on the map, full or empty by varbit 4539. */
        const val MULTI = 30357
        const val FULL = 26462
        const val EMPTY = 30358
        const val VARBIT_CLAIMED = 4539
        const val COINS = 995
        const val NOTED_BATTLESTAFF = 1392
        const val PRICE = 7_000

        /** The staves a day by the highest Varrock tier done: easy, medium, hard, elite. */
        val STAVES = listOf(15, 30, 60, 120)

        const val DAY = "zaff_staves_day"
    }
}
