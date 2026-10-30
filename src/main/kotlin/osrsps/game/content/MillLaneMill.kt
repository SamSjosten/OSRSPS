package osrsps.game.content

import osrsps.game.InvObj
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** Millie Miller's lines; the words are in the dialogue file, from the Wiki's Transcript:Millie Miller, read 2026-10-29. */
object MillieLines : Lines("millie_miller", "Transcript:Millie Miller") {
    val WELCOME = line("welcome", "Millie's first line, welcoming the player to the mill")
    val WHO = line("who", "option 1, which the player then says")
    val I_AM = line("i_am", "option 1: Millie says who she is")
    val GOOD_BUSINESS = line("good_business", "option 1: the player, people will always need flour")
    val PLACE = line("place", "option 2, which the player then says")
    val THIS_IS = line("this_is", "option 2: Millie names the mill")
    val WE_TAKE = line("we_take", "option 2: Millie, they mill grain from the field")
    val HOW = line("how", "option 3, which the player also says after options 1 and 2")
    val MAKING = line("making", "how to mill flour: Millie's first line, you need grain")
    val FIELD = line("field", "how to mill flour: Millie, there is a field just outside")
    val BRING = line("bring", "how to mill flour: the player asks if they bring it here")
    val ANY_MILL = line("any_mill", "how to mill flour: Millie, here or any other mill")
    val HOPPER = line("hopper", "how to mill flour: Millie, the hopper on the top floor and its lever")
    val WHERE = line("where", "how to mill flour: the player asks where the flour goes")
    val APPEARS = line("appears", "how to mill flour: Millie, the flour appears in the bin; bring a pot")
    val THATS_IT = line("thats_it", "how to mill flour: Millie's last line, ask a cook about baking")
    val GREAT = line("great", "how to mill flour: the player's last line")
    val FINE = line("fine", "option 4, which the player then says")
}

/** The mill's one dialogue box (Transcript:Hopper). */
object HopperLines : Lines("hopper", "Transcript:Hopper") {
    val NOTHING_TO_FILL = line("nothing_to_fill", "Fill with no grain: an item box with the grain")
}

/** Making flour's messages, those the 2004Scape project lists from live. */
object MillLines : Lines("mill", "Windmill") {
    val PUT_GRAIN = line("put_grain", "Fill on the hopper, or grain used on it")
    val ALREADY_GRAIN = line("already_grain", "Fill with grain in the hopper already")
    val EMPTY_HOPPER = line("empty_hopper", "Operate on the controls with nothing in the hopper")
    val OPERATED = line("operated", "Operate sending the grain down")
    val BIN_FULL = line("bin_full", "Operate with the flour bin full")
    val BIN_NOW_FULL = line("bin_now_full", "Operate filling the bin's last pot's worth")
    val BIN_EMPTY = line("bin_empty", "Empty on an empty flour bin")
    val NO_POT = line("no_pot", "Empty on the bin with no empty pot")
    val FILLED_POT = line("filled_pot", "a pot filled, with flour left in the bin")
    val LAST_POT = line("last_pot", "a pot filled with the bin's last flour")
}

/**
 * Mill Lane Mill, north-west of Lumbridge by the wheat field: Millie Miller (4627) on the ground floor, the hopper and
 * its controls on the top floor, the flour bin on the ground floor, and the ladders between them.
 *
 * Making flour (the OSRS Wiki, Windmill and Transcript:Hopper; RS2's rules from the 2004Scape project's windmill
 * script): Fill on the hopper, or grain used on it, puts one grain in; Operate on the controls sends it down to the
 * bin, one pot's worth; the bin holds 30, and the hopper can still take one grain while the bin is full. Empty on the
 * bin, or a pot used on it, fills one pot. The flour in the bin is the player's own, and shared between windmills
 * (the Wiki): the 241 cache keeps it in varbit mill_flour and shows the bin with flour in it by varbit mill_showflour.
 * The messages are those 2004Scape lists from live. Guessed: that the grain in the hopper isn't in a var the client
 * sees, so it is kept with the account by name, as [osrsps.game.Charges] are; the human_pickuptable animation for
 * filling and operating (2004Scape's); and Millie's expressions. Left out: sweetcorn (Farming), which this mill grinds
 * into cornflour, and the controls' in-use look 2004Scape gives them, which the 241 cache has no type for.
 */
object MillLaneMill {
    const val MILLIE = 4627
    const val TALK_TO = 1

    const val LADDER_BOTTOM = 12964
    const val LADDER_MIDDLE = 12965
    const val LADDER_TOP = 12966
    const val CLIMB = 1
    const val CLIMB_UP = 2
    const val CLIMB_DOWN = 3

    const val HOPPER = 24961
    const val FILL = 1
    const val CONTROLS = 24964
    const val OPERATE = 1

    /** millbase, which shows as millbase_empty or millbase_flour by [VARBIT_SHOW_FLOUR]. */
    const val FLOUR_BIN = 1781
    const val EMPTY = 1

    const val GRAIN = 1947
    const val POT = 1931
    const val POT_OF_FLOUR = 1933

    /** mill_flour: the pots of flour in the bin, 0 to [BIN_CAPACITY]. */
    const val VARBIT_FLOUR = 4920

    /** mill_showflour: 1 shows the bin with flour in it. */
    const val VARBIT_SHOW_FLOUR = 5325

    const val BIN_CAPACITY = 30

    /** The name the grain in a player's hopper is kept under. */
    const val HOPPER_GRAIN = "mill_hopper_grain"

    /** human_pickuptable. */
    const val ANIM_USE = 832

    fun flour(player: Player): Int = player.vars.getVarbit(VARBIT_FLOUR)

    internal fun setFlour(player: Player, pots: Int) {
        player.vars.setVarbit(VARBIT_FLOUR, pots)
        player.vars.setVarbit(VARBIT_SHOW_FLOUR, if (pots > 0) 1 else 0)
    }

    fun hopperFull(player: Player): Boolean = player.charges[HOPPER_GRAIN] > 0

    internal suspend fun Dialogue.millie() = with(MillieLines) {
        chatNpc(ChatAnim.HAPPY, WELCOME)
        when (choice(WHO, PLACE, HOW, FINE)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, WHO)
                chatNpc(ChatAnim.HAPPY, I_AM)
                chatPlayer(ChatAnim.HAPPY, GOOD_BUSINESS)
                chatPlayer(ChatAnim.QUIZ, HOW)
                flour()
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, PLACE)
                chatNpc(ChatAnim.HAPPY, THIS_IS)
                chatNpc(ChatAnim.HAPPY, WE_TAKE)
                chatPlayer(ChatAnim.QUIZ, HOW)
                flour()
            }
            3 -> {
                chatPlayer(ChatAnim.QUIZ, HOW)
                flour()
            }
            else -> chatPlayer(ChatAnim.NEUTRAL, FINE)
        }
    }

    private suspend fun Dialogue.flour() = with(MillieLines) {
        chatNpc(ChatAnim.HAPPY, MAKING)
        chatNpc(ChatAnim.HAPPY, FIELD)
        chatPlayer(ChatAnim.QUIZ, BRING)
        chatNpc(ChatAnim.HAPPY, ANY_MILL)
        chatNpc(ChatAnim.HAPPY, HOPPER)
        chatPlayer(ChatAnim.QUIZ, WHERE)
        chatNpc(ChatAnim.HAPPY, APPEARS)
        chatNpc(ChatAnim.HAPPY, THATS_IT)
        chatPlayer(ChatAnim.HAPPY, GREAT)
    }
}

/** Millie's Talk-to, the mill's ladders, the hopper, its controls and the flour bin. */
fun registerMillLaneMill(world: World) {
    world.handlers.onNpc(MillLaneMill.MILLIE, MillLaneMill.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(MillLaneMill) { millie() } }
    }

    world.handlers.onLoc(MillLaneMill.LADDER_BOTTOM, 1) { op -> climbLadder(world, op.loc!!, 1, Climbing.ANIM_REACH_FOR_LADDER) }
    world.handlers.onLoc(MillLaneMill.LADDER_MIDDLE, MillLaneMill.CLIMB) { op -> climbLadderMenu(world, op.loc!!) }
    world.handlers.onLoc(MillLaneMill.LADDER_MIDDLE, MillLaneMill.CLIMB_UP) { op -> climbLadder(world, op.loc!!, 1, Climbing.ANIM_REACH_FOR_LADDER) }
    world.handlers.onLoc(MillLaneMill.LADDER_MIDDLE, MillLaneMill.CLIMB_DOWN) { op -> climbLadder(world, op.loc!!, -1, Climbing.ANIM_REACH_FOR_LADDER) }
    world.handlers.onLoc(MillLaneMill.LADDER_TOP, 1) { op -> climbLadder(world, op.loc!!, -1, Climbing.ANIM_PICKUP_FLOOR) }

    world.handlers.onLoc(MillLaneMill.HOPPER, MillLaneMill.FILL) { fillHopper(world) }
    world.handlers.onObjOnLoc(MillLaneMill.HOPPER, MillLaneMill.GRAIN) { fillHopper(world) }
    world.handlers.onLoc(MillLaneMill.CONTROLS, MillLaneMill.OPERATE) { operate(world) }
    world.handlers.onLoc(MillLaneMill.FLOUR_BIN, MillLaneMill.EMPTY) { emptyBin(world) }
    world.handlers.onObjOnLoc(MillLaneMill.FLOUR_BIN, MillLaneMill.POT) { emptyBin(world) }
}

private suspend fun ScriptContext.fillHopper(world: World) {
    arriveDelay()
    if (MillLaneMill.hopperFull(player)) {
        world.message(player, MillLines.ALREADY_GRAIN)
        return
    }
    val slot = slotOf(MillLaneMill.GRAIN)
    if (slot == null) {
        world.dialogues.start(this) { objbox(MillLaneMill.GRAIN, HopperLines.NOTHING_TO_FILL) }
        return
    }
    player.inventory.remove(slot, 1)
    player.charges[MillLaneMill.HOPPER_GRAIN] = 1
    player.anim(MillLaneMill.ANIM_USE)
    world.message(player, MillLines.PUT_GRAIN)
}

private suspend fun ScriptContext.operate(world: World) {
    // 2004Scape: this message comes before the arrive delay, the animation after it.
    if (!MillLaneMill.hopperFull(player)) world.message(player, MillLines.EMPTY_HOPPER)
    arriveDelay()
    player.anim(MillLaneMill.ANIM_USE)
    if (!MillLaneMill.hopperFull(player)) return
    val flour = MillLaneMill.flour(player)
    if (flour >= MillLaneMill.BIN_CAPACITY) {
        world.message(player, MillLines.BIN_FULL)
        return
    }
    world.message(player, MillLines.OPERATED)
    player.charges[MillLaneMill.HOPPER_GRAIN] = 0
    MillLaneMill.setFlour(player, flour + 1)
    if (flour + 1 == MillLaneMill.BIN_CAPACITY) world.message(player, MillLines.BIN_NOW_FULL)
}

private fun ScriptContext.emptyBin(world: World) {
    val flour = MillLaneMill.flour(player)
    if (flour == 0) {
        world.message(player, MillLines.BIN_EMPTY)
        return
    }
    val slot = slotOf(MillLaneMill.POT)
    if (slot == null) {
        world.message(player, MillLines.NO_POT)
        return
    }
    player.inventory[slot] = InvObj(MillLaneMill.POT_OF_FLOUR, 1)
    MillLaneMill.setFlour(player, flour - 1)
    world.message(player, if (flour > 1) MillLines.FILLED_POT else MillLines.LAST_POT)
}

private fun ScriptContext.slotOf(id: Int): Int? = player.inventory.contents().firstOrNull { it.second.id == id }?.first
