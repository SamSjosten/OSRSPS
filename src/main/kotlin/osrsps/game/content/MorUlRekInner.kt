package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.tzhaar.Inferno

/** The barrier guards' words, the Wiki's Transcript:TzHaar-Ket (read 2026-11-18). [name] is the player's TzHaar name. */
object TzHaarGuardLines : Lines("tzhaar_guard", "Transcript:TzHaar-Ket") {
    val GREETING = line("greeting", "the guard's first line, naming the player", "name")
    val WHY_CANT = line("why_cant", "option 1, which the player then says")
    val WHAT_GUARDING = line("what_guarding", "option 2, which the player then says")
    val WHAT_CALL = line("what_call", "option 3 (no line said)")
    val FINE = line("fine", "option 4, which the player then says")
    val CLOSED = line("closed", "the answer to 'Why can't I go past here?'")
    val WHYS_THAT = line("whys_that", "that menu's option 1, which the player then says")
    val FAIR_ENOUGH = line("fair_enough", "its option 3, which the player then says")
    val NOT_TRUSTED = line("not_trusted", "the answer to 'Why's that?'")
    val SHAME = line("shame", "that menu's option 1, which the player then says")
    val PROVE = line("prove", "its option 2")
    val PROVE_SAID = line("prove_said", "what the player says for it (not quite the option's words)")
    val HARDENED = line("hardened", "its option 3, which the player then says")
    val ACCEPTABLE = line("acceptable", "the answer to proving it, naming the player", "name")
    val GOLD = line("gold", "that menu's first option")
    val GOLD_SAID = line("gold_said", "what the player says for it (not quite the option's words)")
    val FIRE_CAPE = line("fire_cape", "the option shown only with a fire cape, which the player then says")
    val SORRY = line("sorry", "the last option, which the player then says")
    val BAD_CURRENCY = line("bad_currency", "the answer to the gold")
    val SHOW_CAPE = line("show_cape", "the box with the fire cape")
    val IMPRESSIVE = line("impressive", "the guard on seeing it, naming the player", "name")
    val SURELY = line("surely", "the player then asks")
    val GRANTED = line("granted", "the guard lets the player in")
    val BACK_OFF = line("back_off", "the answer to the hardened adventurer, naming the player", "name")
    val TRUSTED = line("trusted", "the answer to 'What are you guarding?' once the player has access, naming them", "name")
    val SECRET = line("secret", "the answer to it without access, naming the player", "name")
    val GOODBYE = line("goodbye", "then the menu's option 1")
    val GOODBYE_SAID = line("goodbye_said", "what the player says for it (not quite the option's words)")
    val OVER_AND_OUT = line("over_and_out", "its option 2, which the player then says", "name")
    val ACCENT = line("accent", "the guard's answer to it")
    val HELPS = line("helps", "the player's last word")
    val ARE_YOU_NOT = line("are_you_not", "the answer to 'What did you call me?', naming the player", "name")
    val WHATS_CASTE = line("whats_caste", "the player then asks", "caste")
    val KET_IS = line("ket_is", "the answer for a JalYt-Ket")
    val MEJ_IS = line("mej_is", "the answer for a JalYt-Mej")
    val XIL_IS = line("xil_is", "the answer for a JalYt-Xil")
    val HUR_IS = line("hur_is", "the answer for a JalYt-Hur")
    val SUPPOSE = line("suppose", "the player: they suppose they are")
    val THEN_YOU = line("then_you", "the reply, naming the caste", "caste")
    val WHAT_ARE_YOU = line("what_are_you", "the menu's option 1, which the player then says")
    val THANKS = line("thanks", "option 2, which the player then says")
    val GUESS_SO = line("guess_so", "option 3, which the player then says")
    val NOT = line("not", "option 4, which the player then says")
    val I_AM_KET = line("i_am_ket", "the answer to 'What are you then?'")
    val THE_CASTES = line("the_castes", "then: the other three castes")
    val NO_PROBLEMS = line("no_problems", "the answer to 'I guess so'")
    val CRAZY = line("crazy", "the answer to 'No I'm not!'")
    val HELLO = line("hello", "inside: the guard's first line, naming the player", "name")
    val HELLO_PLAYER = line("hello_player", "inside: the player's answer")
    val NEED_HELP = line("need_help", "inside: the guard")
    val INFERNO = line("inferno", "inside: the player")
    val ALONE = line("alone", "inside: the guard")
    val WORTH_A_TRY = line("worth_a_try", "inside: the player's last word")
    val NO_PASS = line("no_pass", "the message when the hot vent door won't let the player through (our own line: the Wiki gives none)")
}

/** The terminal chambers' elders' words, the Wiki's Transcript:TzHaar-Mej-Bal and -Dir (read 2026-11-18). */
object TzHaarElderLines : Lines("tzhaar_elder", "Transcript:TzHaar-Mej-Bal; Transcript:TzHaar-Mej-Dir") {
    val HELLO = line("hello", "the elder's first line, naming the player", "name")
    val WHATS_HAPPENING = line("whats_happening", "the player")
    val CHAMBER = line("chamber", "the elder")
    val WHY = line("why", "the player")
    val LOSE_HEAT = line("lose_heat", "the elder")
    val SOLIDIFY = line("solidify", "the player")
    val HAAR_TOK = line("haar_tok", "the elder")
    val DEAD = line("dead", "the player")
    val CONSCIOUS = line("conscious", "the elder")
    val INTRIGUING = line("intriguing", "the player's last word")
}

/**
 * Mor Ul Rek's inner city (Karamja step K2.3), as the Wiki's Mor Ul Rek, TzHaar-Ket (barrier guard), Hot vent door,
 * TzHaar, TzHaar-Ket-Yil, TzHaar-Mej-Bal, TzHaar-Mej-Dir and the inner shops' pages have it (read 2026-11-18), with ids
 * and tiles from the 242 cache and map:
 *
 * - **The barrier guards** (TzHaar-Ket, 2187 at the outer gates and 2186 by the central one) talk (the transcript);
 *   showing them a fire cape or fire max cape, worn or carried, opens the inner city to the player for good (a varp of
 *   ours, [CustomVars.morUlRekAccessVarp]) and makes them Xo in their TzHaar name ([TzHaarNames.name]).
 * - **The hot vent doors** (30266, five of them) let a player with access in, and anyone inside out.
 * - **TzHaar-Ket-Yil** (7678, [MorUlRek.BANKER_INNER]) minds the east bank; **TzHaar-Hur-Zal** (7688) and
 *   **TzHaar-Hur-Rin** (7689) keep the inner shops ([TzHaarShops]); **TzHaar-Mej-Bal** (7681) and **-Dir** (7680)
 *   tell of the terminal chambers. The level 221 TzHaar-Ket wander the inner city (`mor-ul-rek.json`).
 *
 * Inferred: the guards at the central gate (2186) have the transcript's "Inside Mor Ul Rek" talk once the player has
 * access, the outer ones always the standard one. Guessed: which side of each door is inside (the guards' side is
 * out), the tiles a door puts the player on, the refusal's message, the chathead faces. Left out: the stalls and the
 * guards' shouts at thieves (step K2.4), the Inferno and TzHaar-Ket-Rak (the Fight Caves thread).
 */
object MorUlRekInner {
    const val DOOR = 30266
    const val PASS = 1
    const val GUARD_CENTRAL = 2186
    const val GUARD_BORDER = 2187
    const val TALK_TO = 1
    const val ELDER_DIR = 7680
    const val ELDER_BAL = 7681

    /** A fire cape and a fire max cape: what the guards want to see. */
    val FIRE_CAPES = listOf(6570, 13329)

    /** A hot vent door at [x], [z], and the tiles either side: [outside] (the guards' side) and [inside]. */
    data class Door(val x: Int, val z: Int, val outside: Destination, val inside: Destination)

    val DOORS = listOf(
        Door(2436, 5120, Destination(2436, 5121, 0), Destination(2436, 5119, 0)),
        Door(2457, 5119, Destination(2457, 5120, 0), Destination(2457, 5118, 0)),
        Door(2474, 5137, Destination(2474, 5138, 0), Destination(2474, 5136, 0)),
        Door(2494, 5174, Destination(2493, 5174, 0), Destination(2495, 5174, 0)),
        Door(2495, 5157, Destination(2494, 5157, 0), Destination(2496, 5157, 0)),
    )

    private fun varp(player: Player): Int? = player.vars.types.namedVarps[CustomVars.MOR_UL_REK_ACCESS]

    /** An infernal cape and an infernal max cape, which only a player who gave TzHaar-Ket-Keh a fire cape can have. */
    val INFERNAL_CAPES = listOf(Inferno.INFERNAL_CAPE, 21285)

    /**
     * Whether the player may pass the hot vent doors: they have shown a fire cape to a barrier guard, or they have given
     * one to TzHaar-Ket-Keh or hold an infernal cape (accounts that did either before the guards came, exploit review
     * R52B-3; inferred, as the Wiki's guards want only to have seen a fire cape).
     */
    fun hasAccess(player: Player): Boolean =
        (varp(player)?.let { player.vars[it] != 0 } ?: false) || sacrificedFireCape(player) ||
            INFERNAL_CAPES.any { player.inventory.count(it) > 0 || player.equipment.count(it) > 0 }

    private fun sacrificedFireCape(player: Player): Boolean =
        player.vars.types.varbit(Inferno.VARBIT_SACRIFICED) != null && player.vars.getVarbit(Inferno.VARBIT_SACRIFICED) >= Inferno.SACRIFICED

    internal fun grant(player: Player) {
        varp(player)?.let { player.vars[it] = 1 }
    }

    private fun hasFireCape(player: Player): Boolean =
        FIRE_CAPES.any { player.inventory.count(it) > 0 || player.equipment.count(it) > 0 }

    /** Where passing [door] takes a player standing at [x], [z]: through to the far side. */
    fun through(door: Door, x: Int, z: Int): Destination = if (onOutside(door, x, z)) door.inside else door.outside

    /** Whether a player at [x], [z] stands on [door]'s outside (the guards') side. */
    fun onOutside(door: Door, x: Int, z: Int): Boolean {
        val outside = door.outside
        return if (outside.x == door.x) (z - door.z) * (outside.z - door.z) > 0 else (x - door.x) * (outside.x - door.x) > 0
    }

    /** A barrier guard's talk: [central] for the central gate's guards. */
    internal suspend fun Dialogue.guard(player: Player, central: Boolean): Unit = with(TzHaarGuardLines) {
        val name = TzHaarNames.name(player)
        if (central && hasAccess(player)) {
            chatNpc(ChatAnim.NEUTRAL, HELLO, "name" to name)
            chatPlayer(ChatAnim.HAPPY, HELLO_PLAYER)
            chatNpc(ChatAnim.NEUTRAL, NEED_HELP)
            chatPlayer(ChatAnim.NEUTRAL, INFERNO)
            chatNpc(ChatAnim.NEUTRAL, ALONE)
            chatPlayer(ChatAnim.SAD, WORTH_A_TRY)
            return@with
        }
        chatNpc(ChatAnim.NEUTRAL, GREETING, "name" to name)
        when (choice(WHY_CANT, WHAT_GUARDING, WHAT_CALL, FINE)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, WHY_CANT)
                chatNpc(ChatAnim.NEUTRAL, CLOSED)
                when (choice(WHYS_THAT, WHAT_GUARDING, FAIR_ENOUGH)) {
                    1 -> whysThat(player, name)
                    2 -> guarding(player, name)
                    else -> chatPlayer(ChatAnim.NEUTRAL, FAIR_ENOUGH)
                }
            }
            2 -> guarding(player, name)
            3 -> caste(player, name)
            else -> chatPlayer(ChatAnim.NEUTRAL, FINE)
        }
    }

    private suspend fun Dialogue.whysThat(player: Player, name: String) = with(TzHaarGuardLines) {
        chatPlayer(ChatAnim.QUIZ, WHYS_THAT)
        chatNpc(ChatAnim.NEUTRAL, NOT_TRUSTED)
        when (choice(SHAME, PROVE, HARDENED)) {
            1 -> chatPlayer(ChatAnim.SAD, SHAME)
            2 -> {
                chatPlayer(ChatAnim.QUIZ, PROVE_SAID)
                chatNpc(ChatAnim.NEUTRAL, ACCEPTABLE, "name" to name)
                val cape = hasFireCape(player)
                val options = if (cape) arrayOf(GOLD, FIRE_CAPE, SORRY) else arrayOf(GOLD, SORRY)
                when (options[choice(*options) - 1]) {
                    GOLD -> {
                        chatPlayer(ChatAnim.QUIZ, GOLD_SAID)
                        chatNpc(ChatAnim.NEUTRAL, BAD_CURRENCY)
                    }
                    FIRE_CAPE -> {
                        chatPlayer(ChatAnim.HAPPY, FIRE_CAPE)
                        objbox(FIRE_CAPES.first { player.inventory.count(it) > 0 || player.equipment.count(it) > 0 }, SHOW_CAPE)
                        chatNpc(ChatAnim.NEUTRAL, IMPRESSIVE, "name" to name)
                        chatPlayer(ChatAnim.QUIZ, SURELY)
                        grant(player)
                        chatNpc(ChatAnim.NEUTRAL, GRANTED)
                    }
                    else -> chatPlayer(ChatAnim.SAD, SORRY)
                }
            }
            else -> {
                chatPlayer(ChatAnim.ANGRY, HARDENED)
                chatNpc(ChatAnim.ANGRY, BACK_OFF, "name" to name)
            }
        }
    }

    private suspend fun Dialogue.guarding(player: Player, name: String) = with(TzHaarGuardLines) {
        chatPlayer(ChatAnim.QUIZ, WHAT_GUARDING)
        chatNpc(ChatAnim.NEUTRAL, if (hasAccess(player)) TRUSTED else SECRET, "name" to name)
        if (choice(text(GOODBYE), text(OVER_AND_OUT, "name" to name)) == 1) {
            chatPlayer(ChatAnim.NEUTRAL, GOODBYE_SAID)
        } else {
            chatPlayer(ChatAnim.NEUTRAL, OVER_AND_OUT, "name" to name)
            chatNpc(ChatAnim.LAUGH, ACCENT)
            chatPlayer(ChatAnim.NEUTRAL, HELPS)
        }
    }

    private suspend fun Dialogue.caste(player: Player, name: String) = with(TzHaarGuardLines) {
        val caste = TzHaarNames.casteTitle(player)
        chatNpc(ChatAnim.NEUTRAL, ARE_YOU_NOT, "name" to name)
        chatPlayer(ChatAnim.QUIZ, WHATS_CASTE, "caste" to caste)
        chatNpc(ChatAnim.NEUTRAL, when (TzHaarNames.caste(player)) {
            TzHaarNames.Caste.KET -> KET_IS
            TzHaarNames.Caste.MEJ -> MEJ_IS
            TzHaarNames.Caste.XIL -> XIL_IS
            TzHaarNames.Caste.HUR -> HUR_IS
        })
        chatPlayer(ChatAnim.NEUTRAL, SUPPOSE)
        chatNpc(ChatAnim.NEUTRAL, THEN_YOU, "caste" to caste)
        when (choice(WHAT_ARE_YOU, THANKS, GUESS_SO, NOT)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, WHAT_ARE_YOU)
                chatNpc(ChatAnim.NEUTRAL, I_AM_KET)
                chatNpc(ChatAnim.NEUTRAL, THE_CASTES)
            }
            2 -> chatPlayer(ChatAnim.NEUTRAL, THANKS)
            3 -> {
                chatPlayer(ChatAnim.NEUTRAL, GUESS_SO)
                chatNpc(ChatAnim.NEUTRAL, NO_PROBLEMS)
            }
            else -> {
                chatPlayer(ChatAnim.ANGRY, NOT)
                chatNpc(ChatAnim.NEUTRAL, CRAZY)
            }
        }
    }

    /** An elder's talk about the terminal chambers. */
    internal suspend fun Dialogue.elder(player: Player): Unit = with(TzHaarElderLines) {
        chatNpc(ChatAnim.NEUTRAL, HELLO, "name" to TzHaarNames.name(player))
        chatPlayer(ChatAnim.QUIZ, WHATS_HAPPENING)
        chatNpc(ChatAnim.NEUTRAL, CHAMBER)
        chatPlayer(ChatAnim.QUIZ, WHY)
        chatNpc(ChatAnim.NEUTRAL, LOSE_HEAT)
        chatPlayer(ChatAnim.QUIZ, SOLIDIFY)
        chatNpc(ChatAnim.NEUTRAL, HAAR_TOK)
        chatPlayer(ChatAnim.QUIZ, DEAD)
        chatNpc(ChatAnim.NEUTRAL, CONSCIOUS)
        chatPlayer(ChatAnim.NEUTRAL, INTRIGUING)
    }
}

fun registerMorUlRekInner(world: World) {
    with(MorUlRekInner) {
        world.handlers.onLoc(DOOR, PASS) { op ->
            val loc = op.loc ?: return@onLoc
            val door = DOORS.firstOrNull { it.x == loc.x && it.z == loc.z } ?: return@onLoc
            arriveDelay()
            // Anyone inside may always leave (the Inferno's exits land inside the doors, exploit review R52B-3).
            if (onOutside(door, player.x, player.z) && !hasAccess(player)) {
                world.message(player, TzHaarGuardLines.NO_PASS)
                return@onLoc
            }
            val to = through(door, player.x, player.z)
            world.teleport(player, to.x, to.z, to.level)
        }
        for (keeper in listOf(GUARD_CENTRAL, GUARD_BORDER)) {
            world.handlers.onNpc(keeper, TALK_TO) { op ->
                world.dialogues.start(this, op.npc) { guard(player, central = keeper == GUARD_CENTRAL) }
            }
        }
        for (keeper in listOf(ELDER_BAL, ELDER_DIR)) {
            world.handlers.onNpc(keeper, TALK_TO) { op -> world.dialogues.start(this, op.npc) { elder(player) } }
        }
    }
}
