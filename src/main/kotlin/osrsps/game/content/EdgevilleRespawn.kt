package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.combat.DeathPlace
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** Krystilia's talk about the Edgeville respawn (the Wiki's Transcript:Krystilia, read 2026-11-19). */
object KrystiliaRespawnLines : Lines("krystilia_respawn", "Transcript:Krystilia") {
    val APART = line("apart", "first menu: what she can do apart from Slayer, which the player then says")
    val QUITE_A_LOT = line("quite_a_lot", "her answer: the guards can't keep her in")
    val OFFER = line("offer", "not unlocked: she offers to make the player respawn in Edgeville")
    val TELL_ME_MORE = line("tell_me_more", "that menu, option 1, which the player then says")
    val NOT_INTERESTED = line("not_interested", "that menu, option 2, which the player then says")
    val CANT_HANDLE = line("cant_handle", "option 2: her answer")
    val DEAL = line("deal", "the deal: Edgeville's southern ruins, items lost as normal")
    val SAFE_PLACES = line("safe_places", "then: not in places with their own magic")
    val ALL_WORLDS = line("all_worlds", "then: members' and free worlds alike")
    val FEE = line("fee", "then: the one-off 5 million coins")
    val NOT_CARRYING = line("not_carrying", "with under 5 million coins: the player's line")
    val SCARED = line("scared", "then: her answer")
    val SWITCH_TO_EDGEVILLE = line("switch_to_edgeville", "with the coins: the player's line, then paying")
    val WANT_EDGEVILLE = line("want_edgeville", "unlocked, respawning elsewhere: does the player want Edgeville again")
    val PLEASE_EDGEVILLE = line("please_edgeville", "that menu, option 1, which the player then says")
    val NO_EDGEVILLE = line("no_edgeville", "that menu, option 2, which the player then says")
    val LOOKING_FOR_FUN = line("looking_for_fun", "option 1: is the player sure they can handle it")
    val YES_EDGEVILLE = line("yes_edgeville", "that menu, option 1, which the player then says")
    val ANOTHER_TIME = line("another_time", "that menu, option 2, which the player then says")
    val DONE = line("done", "the respawn switched")
    val BORING = line("boring", "the player said no")
    val HOW_IS_IT = line("how_is_it", "respawning in Edgeville: how is it working")
    val BACK_TO_LUMBRIDGE = line("back_to_lumbridge", "that menu, option 1, which the player then says")
    val FINE_THANKS = line("fine_thanks", "that menu, option 2, which the player then says")
    val SLUM = line("slum", "option 1: is the player sure, she won't charge again")
    val YES_LUMBRIDGE = line("yes_lumbridge", "that menu, option 1, which the player then says")
    val KEEP_EDGEVILLE = line("keep_edgeville", "that menu, option 2, which the player then says")
    val GOOD_CHOICE = line("good_choice", "option 2: her answer")
}

/**
 * The Edgeville respawn Krystilia sells (OSRS Wiki, Krystilia, Transcript:Krystilia and Spawning, read 2026-11-19): for
 * a one-off 5,000,000 coins a player who dies comes back in Edgeville's ruins south of the town instead of Lumbridge,
 * losing items as anywhere else; she switches them back to Lumbridge, or to Edgeville again, for nothing. Live's own
 * varbits hold it, edgeville_spawn (621) and edgeville_spawn_unlocked (623), so a save keeps it. Places with their own
 * respawn (an instance, a raid) keep theirs, as the transcript says. Inferred: the tile, the middle of the Wiki's
 * Spawning map square for Edgeville (3094,3469); the coins taken from the backpack only, as her line asks about what the player
 * carries. Left out: Ferox Enclave's respawn, which her talk mentions, as nobody sells it yet.
 */
object EdgevilleRespawn {
    const val VARBIT_SPAWN = 621
    const val VARBIT_UNLOCKED = 623
    const val PRICE = 5_000_000
    const val COINS = 995
    const val X = 3094
    const val Z = 3469

    fun active(world: World, player: Player): Boolean = has(world, player, VARBIT_SPAWN)

    fun unlocked(world: World, player: Player): Boolean = has(world, player, VARBIT_UNLOCKED)

    /** A cache (or a test's) without the varbits has nobody respawning here. */
    private fun has(world: World, player: Player, varbit: Int) = world.varTypes.varbit(varbit) != null && player.vars.getVarbit(varbit) == 1

    /** Where [player], dying where they stand, comes back when they respawn in Edgeville; null otherwise. */
    fun deathPlace(world: World, player: Player): DeathPlace? =
        if (active(world, player)) DeathPlace(keepItems = false, player.x, player.z, player.level, X, Z, 0, respawnOnly = true) else null

    /** Her talk menu's option about what she does apart from Slayer ([KrystiliaRespawnLines.APART]). */
    internal suspend fun Dialogue.talk(world: World): Unit = with(KrystiliaRespawnLines) {
        chatPlayer(ChatAnim.QUIZ, APART)
        chatNpc(ChatAnim.HAPPY, QUITE_A_LOT)
        when {
            active(world, player) -> backToLumbridge()
            unlocked(world, player) -> edgevilleAgain()
            else -> sell()
        }
    }

    private suspend fun Dialogue.sell(): Unit = with(KrystiliaRespawnLines) {
        chatNpc(ChatAnim.NEUTRAL, OFFER)
        if (choice(TELL_ME_MORE, NOT_INTERESTED) != 1) {
            chatPlayer(ChatAnim.NEUTRAL, NOT_INTERESTED)
            chatNpc(ChatAnim.LAUGH, CANT_HANDLE)
            return
        }
        chatPlayer(ChatAnim.NEUTRAL, TELL_ME_MORE)
        chatNpc(ChatAnim.NEUTRAL, DEAL)
        chatNpc(ChatAnim.NEUTRAL, SAFE_PLACES)
        chatNpc(ChatAnim.NEUTRAL, ALL_WORLDS)
        chatNpc(ChatAnim.NEUTRAL, FEE)
        if (player.inventory.count(COINS) < PRICE) {
            chatPlayer(ChatAnim.SAD, NOT_CARRYING)
            chatNpc(ChatAnim.LAUGH, SCARED)
            return
        }
        chatPlayer(ChatAnim.HAPPY, SWITCH_TO_EDGEVILLE)
        // Counted again after the line: the backpack may have changed while it showed.
        if (player.inventory.count(COINS) < PRICE) return
        var left = PRICE
        for ((slot, held) in player.inventory.contents()) {
            if (left == 0) break
            if (held.id == COINS) left -= player.inventory.remove(slot, left)
        }
        player.vars.setVarbit(VARBIT_UNLOCKED, 1)
        player.vars.setVarbit(VARBIT_SPAWN, 1)
    }

    private suspend fun Dialogue.edgevilleAgain(): Unit = with(KrystiliaRespawnLines) {
        chatNpc(ChatAnim.QUIZ, WANT_EDGEVILLE)
        if (choice(PLEASE_EDGEVILLE, NO_EDGEVILLE) != 1) {
            chatPlayer(ChatAnim.NEUTRAL, NO_EDGEVILLE)
            chatNpc(ChatAnim.NEUTRAL, BORING)
            return
        }
        chatPlayer(ChatAnim.NEUTRAL, PLEASE_EDGEVILLE)
        chatNpc(ChatAnim.QUIZ, LOOKING_FOR_FUN)
        if (choice(YES_EDGEVILLE, ANOTHER_TIME) != 1) {
            chatPlayer(ChatAnim.NEUTRAL, ANOTHER_TIME)
            chatNpc(ChatAnim.NEUTRAL, BORING)
            return
        }
        chatPlayer(ChatAnim.NEUTRAL, YES_EDGEVILLE)
        player.vars.setVarbit(VARBIT_SPAWN, 1)
        chatNpc(ChatAnim.HAPPY, DONE)
    }

    private suspend fun Dialogue.backToLumbridge(): Unit = with(KrystiliaRespawnLines) {
        chatNpc(ChatAnim.QUIZ, HOW_IS_IT)
        if (choice(BACK_TO_LUMBRIDGE, FINE_THANKS) != 1) {
            chatPlayer(ChatAnim.NEUTRAL, FINE_THANKS)
            return
        }
        chatPlayer(ChatAnim.NEUTRAL, BACK_TO_LUMBRIDGE)
        chatNpc(ChatAnim.QUIZ, SLUM)
        if (choice(YES_LUMBRIDGE, KEEP_EDGEVILLE) != 1) {
            chatPlayer(ChatAnim.NEUTRAL, KEEP_EDGEVILLE)
            chatNpc(ChatAnim.HAPPY, GOOD_CHOICE)
            return
        }
        chatPlayer(ChatAnim.NEUTRAL, YES_LUMBRIDGE)
        player.vars.setVarbit(VARBIT_SPAWN, 0)
        chatNpc(ChatAnim.NEUTRAL, DONE)
    }
}
