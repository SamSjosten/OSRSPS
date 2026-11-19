package osrsps.game.content

import osrsps.game.PlayerEffects
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext
import osrsps.game.skill.Agility

/** Mosol Rei's words once Shilo Village is done, the Wiki's Transcript:Mosol Rei (read 2026-11-19). */
object MosolReiLines : Lines("mosol_rei", "Transcript:Mosol Rei") {
    val GREETINGS = line("greetings", "the player's greeting, after Shilo Village")
    val THREAT_REMOVED = line("threat_removed", "his answer: Rashiliyia's threat is gone, some undead still about")
    val SHOW_THE_WAY = line("show_the_way", "his offer to show the player into the village")
    val ENTER_TITLE = line("enter_title", "the menu's title")
    val YES = line("yes", "option 1 (no line from the player)")
    val OTHER_TIME = line("other_time", "option 2 (no line from the player)")
    val LEADS = line("leads", "option 1's message box")
    val LEAVES = line("leaves", "option 1's game message, once inside (and Follow's)")
    val DECIDE_NOT = line("decide_not", "option 2's game message")
}

/** The broken cart's words, the Wiki's Transcript:Broken cart (Shilo Village) (read 2026-11-19). */
object ShiloCartLines : Lines("shilo_cart", "Transcript:Broken cart (Shilo Village)") {
    val FROM_EAST = line("from_east", "Climb over from the east side (outside the village)")
    val FROM_WEST_1 = line("from_west_1", "Climb over from the west side, the first message")
    val FROM_WEST_2 = line("from_west_2", "Climb over from the west side, the second message")
}

/** Obli's words, the Wiki's Transcript:Obli (read 2026-11-19). */
object ObliLines : Lines("obli", "Transcript:Obli") {
    val WELCOME = line("welcome", "his greeting")
    val YES_PLEASE = line("yes_please", "option 1, which the player then says (the shop opens)")
    val NO_THANKS = line("no_thanks", "option 2, which the player then says")
}

/** Fernahei's words, the Wiki's Transcript:Fernahei (read 2026-11-19). */
object FernaheiLines : Lines("fernahei", "Transcript:Fernahei") {
    val WELCOME = line("welcome", "his greeting")
    val YES_PLEASE = line("yes_please", "option 1, which the player then says (the shop opens)")
    val NO_THANKS = line("no_thanks", "option 2, which the player then says")
    val THATS_FINE = line("thats_fine", "option 2: his answer")
}

/**
 * Shilo Village (Karamja step K4.3), open as after the Shilo Village quest, as every quest is here (OSRS Wiki: Shilo
 * Village (location), Mosol Rei, Broken cart (Shilo Village), Obli, Fernahei, Shilo Village bank, read 2026-11-19):
 *
 * - **Mosol Rei** (`mosol_rei_multi` 8695, shown as `mosol_rei_complete` 8696) outside the gate: Follow takes the player
 *   into the village; Talk-to offers the same.
 * - **The broken cart** (`shilo_brokencart` 2216, shown as `shilo_brokencart_postquest`) is climbed over either way, with
 *   the Wiki's messages for each side.
 * - **The metal and wooden gates** (2259/2260 and 2261/2262) have no open types in the cache: Open takes the player
 *   through to the other side.
 * - **The bankers** (`shilobanker` 3093) are [Bankers.STANDARD]'s; the bank deposit box is [registerBanking]'s.
 * - **Obli** (5362) and **Fernahei** (5363) talk and run their shops (shops.json).
 * - **The undead ones** at the gate fight (npc-combat.json); the fishing spots are [osrsps.game.skill.Fishing]'s; the
 *   stepping stone over the river is an Agility shortcut (skills/agility.json); the gem rocks are Mining's.
 *
 * Guessed: where Mosol Rei leaves the player (just inside the wooden gate); the climb's animation and timing (as the
 * Agility fences'); the gates' walk through (one tick, no animation, no message); the chathead faces. Left out: the
 * pre-quest cart and Mosol Rei (no quests), the cart's warning toggle, the jungle foresters (Legends' Quest only).
 */
object ShiloVillage {
    const val MOSOL_REI = 8696
    const val FOLLOW = 1
    const val MOSOL_TALK_TO = 3
    const val TALK_TO = 1

    const val BROKEN_CART = 2216
    const val CLIMB_OVER = 2

    /** The cart's tiles: 3 by 3 from 2877, 2951. */
    const val CART_WEST_X = 2876
    const val CART_EAST_X = 2880
    const val CART_SOUTH_Z = 2951
    const val CART_NORTH_Z = 2953

    const val OPEN = 1

    /** The metal gate's wall on the west edge of x 2875, the wooden gate's on the east edge of 2867. */
    val METAL_GATE = listOf(2259, 2260)
    const val METAL_GATE_EAST_X = 2875
    val WOODEN_GATE = listOf(2261, 2262)
    const val WOODEN_GATE_EAST_X = 2868

    /** Where Mosol Rei leaves the player: just inside the wooden gate (guessed). */
    const val INSIDE_X = 2866
    const val INSIDE_Z = 2952

    const val OBLI = 5362
    const val FERNAHEI = 5363

    /** Climbing over: human_jump_hurdle, as the Agility fences' (guessed). */
    const val CLIMB_ANIM = 1603
    const val CLIMB_TICKS = 2

    internal suspend fun Dialogue.mosolRei(): Boolean = with(MosolReiLines) {
        chatPlayer(ChatAnim.HAPPY, GREETINGS)
        chatNpc(ChatAnim.HAPPY, THREAT_REMOVED)
        chatNpc(ChatAnim.QUIZ, SHOW_THE_WAY)
        if (choice(YES, OTHER_TIME, title = text(ENTER_TITLE)) == 1) {
            mesbox(LEADS)
            true
        } else {
            false
        }
    }

    internal suspend fun Dialogue.obli(): Boolean = with(ObliLines) {
        chatNpc(ChatAnim.HAPPY, WELCOME)
        if (choice(YES_PLEASE, NO_THANKS) == 1) {
            chatPlayer(ChatAnim.HAPPY, YES_PLEASE)
            true
        } else {
            chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
            false
        }
    }

    internal suspend fun Dialogue.fernahei(): Boolean = with(FernaheiLines) {
        chatNpc(ChatAnim.HAPPY, WELCOME)
        if (choice(YES_PLEASE, NO_THANKS) == 1) {
            chatPlayer(ChatAnim.HAPPY, YES_PLEASE)
            true
        } else {
            chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
            chatNpc(ChatAnim.HAPPY, THATS_FINE)
            false
        }
    }

    /** Mosol Rei shows the player in. */
    internal fun ScriptContext.enter(world: World) {
        world.teleport(player, INSIDE_X, INSIDE_Z, 0)
        world.message(player, MosolReiLines.LEAVES)
    }

    /** Over the cart to its other side, from either side. */
    internal suspend fun ScriptContext.climbCart(world: World) {
        val fromEast = player.x >= CART_EAST_X
        val toX = if (fromEast) CART_WEST_X else CART_EAST_X
        val toZ = player.z.coerceIn(CART_SOUTH_Z, CART_NORTH_Z)
        if (fromEast) {
            world.message(player, ShiloCartLines.FROM_EAST)
        } else {
            world.message(player, ShiloCartLines.FROM_WEST_1)
        }
        slide(world, toX, toZ, CLIMB_ANIM, CLIMB_TICKS)
        if (!fromEast) world.message(player, ShiloCartLines.FROM_WEST_2)
    }

    /** Through a gate whose wall runs north to south, with [eastX] the first tile east of it. */
    internal suspend fun ScriptContext.passGate(world: World, eastX: Int) {
        val toX = if (player.x >= eastX) eastX - 1 else eastX
        slide(world, toX, player.z, -1, 1)
    }

    private suspend fun ScriptContext.slide(world: World, toX: Int, toZ: Int, anim: Int, ticks: Int) {
        val angle = Agility.angle(toX - player.x, toZ - player.z)
        player.effects.exactMove(PlayerEffects.ExactMove(player.x, player.z, toX, toZ, 0, ticks * 30, angle))
        if (anim >= 0) player.anim(anim)
        delay(ticks)
        world.teleport(player, toX, toZ, player.level)
    }
}

fun registerShiloVillage(world: World) {
    with(ShiloVillage) {
        world.handlers.onNpc(MOSOL_REI, FOLLOW) { enter(world) }
        world.handlers.onNpc(MOSOL_REI, MOSOL_TALK_TO) { op ->
            var enter = false
            world.dialogues.start(this, op.npc) { enter = mosolRei() }
            if (enter) enter(world) else world.message(player, MosolReiLines.DECIDE_NOT)
        }
        world.handlers.onLoc(BROKEN_CART, CLIMB_OVER) { climbCart(world) }
        for (gate in METAL_GATE) world.handlers.onLoc(gate, OPEN) { passGate(world, METAL_GATE_EAST_X) }
        for (gate in WOODEN_GATE) world.handlers.onLoc(gate, OPEN) { passGate(world, WOODEN_GATE_EAST_X) }
        world.handlers.onNpc(OBLI, TALK_TO) { op ->
            var trade = false
            world.dialogues.start(this, op.npc) { trade = obli() }
            if (trade) world.shopTypes.ofNpc(OBLI)?.let { world.shopScreen.open(player, it) }
        }
        world.handlers.onNpc(FERNAHEI, TALK_TO) { op ->
            var trade = false
            world.dialogues.start(this, op.npc) { trade = fernahei() }
            if (trade) world.shopTypes.ofNpc(FERNAHEI)?.let { world.shopScreen.open(player, it) }
        }
    }
}
