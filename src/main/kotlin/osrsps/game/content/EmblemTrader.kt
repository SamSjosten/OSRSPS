package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** The Emblem Trader's words, outside Daimon's Crater (the Wiki's Transcript:Emblem Trader). */
object EmblemTraderLines : Lines("emblem_trader", "Transcript:Emblem Trader") {
    val ARTEFACTS = line("artefacts", "his greeting off a Deadman world, asking about emblems")
    val NOTHING = line("nothing", "the player's reply to the greeting")
    val BRING_THEM = line("bring_them", "his reply: what to do with any found")
    val TRADE = line("trade", "the option to trade, which the player then says")
    val NO_REWARDS = line("no_rewards", "his reply to trading: why he has no rewards here")
    val DAIMON = line("daimon", "his line on where rewards are sold")
    val SKULL_ASK = line("skull_ask", "the option for an unskulled player to ask for a skull, which they then say")
    val SKULL_WARNING = line("skull_warning", "the skull menu's title: the skull's warning")
    val SKULL_GIVE = line("skull_give", "the skull menu's option to take the skull")
    val CANCEL = line("cancel", "the skull menu's other option")
    val SKULLED = line("skulled", "the box with the skull, on being skulled")
    val EXTEND_ASK = line("extend_ask", "the option for a skulled player to ask for a longer skull")
    val EXTEND_TITLE = line("extend_title", "the extend confirmation's title")
    val YES = line("yes", "the confirmation's Yes")
    val NO = line("no", "the confirmation's No")
    val EXTENDED = line("extended", "the box with the skull, on the skull being extended")
    val CANT_EXTEND = line("cant_extend", "his, when the skull can't be extended")
    val LEAVE = line("leave", "the option to leave, which the player then says")
}

/**
 * The Emblem Trader in the Revenant Caves (Wilderness step W5.3; the Wiki's Emblem Trader and Transcript:Emblem Trader,
 * read 2026-11-15): `wild_cave_trader` 7943, wandering near 3196,10224 (npc-spawns/revenant-caves.json).
 *
 * - **Talk-to** as his transcript has him outside Daimon's Crater: no rewards out here, a PK skull for an unskulled
 *   player after the warning, or for a skulled one the offer to make it last the full 20 minutes.
 * - **Rewards**: his two lines, no rewards out here. **Skull**: the skull menu, or the extension for a skulled player.
 *
 * Live, "he will only accept [ancient artefacts] on the official Bounty Hunter world"; this server has no Bounty Hunter,
 * so as on every other world he takes none (artefacts sell on the Grand Exchange). INFERRED: Skull going straight to
 * the menu that Talk-to's option opens; a skull that "can be extended" as one with less than the 20 minutes left; the
 * skull's 20 minutes. GUESSED: the chathead faces. Not built: the skull colours by risk, his Daimon's Crater and Grand
 * Exchange forms, the Bounty Hunter store.
 */
object EmblemTrader {
    const val ID = 7943
    const val TALK_TO = 1
    const val REWARDS = 3
    const val SKULL = 5
    const val SKULL_OBJ = 964

    /** 20 minutes. */
    const val SKULL_TICKS = 2_000

    fun canExtend(player: Player): Boolean = player.skullTicks in 1 until SKULL_TICKS
}

fun registerEmblemTrader(world: World) {
    with(EmblemTrader) {
        world.handlers.onNpc(ID, TALK_TO) { op -> world.dialogues.start(this, op.npc) { talk(player) } }
        world.handlers.onNpc(ID, REWARDS) { op -> world.dialogues.start(this, op.npc) { noRewards() } }
        world.handlers.onNpc(ID, SKULL) { op ->
            world.dialogues.start(this, op.npc) { if (player.skullTicks > 0) extend(player) else skull(player) }
        }
    }
}

private suspend fun Dialogue.talk(player: Player) = with(EmblemTraderLines) {
    chatNpc(ChatAnim.NEUTRAL, ARTEFACTS)
    chatPlayer(ChatAnim.NEUTRAL, NOTHING)
    chatNpc(ChatAnim.NEUTRAL, BRING_THEM)
    val skulled = player.skullTicks > 0
    when (choice(TRADE, if (skulled) EXTEND_ASK else SKULL_ASK, LEAVE)) {
        1 -> {
            chatPlayer(ChatAnim.NEUTRAL, TRADE)
            noRewards()
        }
        2 -> if (skulled) {
            extend(player)
        } else {
            chatPlayer(ChatAnim.NEUTRAL, SKULL_ASK)
            skull(player)
        }
        else -> chatPlayer(ChatAnim.NEUTRAL, LEAVE)
    }
}

private suspend fun Dialogue.noRewards() = with(EmblemTraderLines) {
    chatNpc(ChatAnim.NEUTRAL, NO_REWARDS)
    chatNpc(ChatAnim.NEUTRAL, DAIMON)
}

private suspend fun Dialogue.skull(player: Player) = with(EmblemTraderLines) {
    if (choice(SKULL_GIVE, CANCEL, title = text(SKULL_WARNING)) != 1) return
    player.skullTicks = EmblemTrader.SKULL_TICKS
    objbox(EmblemTrader.SKULL_OBJ, SKULLED)
}

private suspend fun Dialogue.extend(player: Player) = with(EmblemTraderLines) {
    if (!EmblemTrader.canExtend(player)) return chatNpc(ChatAnim.NEUTRAL, CANT_EXTEND)
    if (choice(YES, NO, title = text(EXTEND_TITLE)) != 1) return
    player.skullTicks = EmblemTrader.SKULL_TICKS
    objbox(EmblemTrader.SKULL_OBJ, EXTENDED)
}
