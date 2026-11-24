package osrsps.game.content

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.combat.HitsplatType
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** Mor Ul Rek's words, in the dialogue file. */
object MorUlRekLines : Lines("mor_ul_rek", "Mor Ul Rek; Karamjan rum") {
    val RUM_EXPLODES = line("rum_explodes", "the message as the heat breaks the rum the player carries in (our own line: the Wiki tells it, gives no words)")
}

/** TzHaar-Ket-Zuh's words, the Wiki's Transcript:TzHaar-Ket-Zuh (read 2026-11-18). [name] is the player's TzHaar name. */
object TzHaarBankerLines : Lines("tzhaar_banker", "Transcript:TzHaar-Ket-Zuh") {
    val GREETING = line("greeting", "his first line, naming the player", "name")
    val ACCESS = line("access", "option 1: the bank (no line said)")
    val WHAT_CALL = line("what_call", "option 2 (no line said)")
    val ARE_YOU_NOT = line("are_you_not", "his answer, naming the player", "name")
    val WHATS_CASTE = line("whats_caste", "that menu's option 1, which the player then says", "caste")
    val GUESS_SO = line("guess_so", "option 2, which the player then says")
    val NOT = line("not", "option 3, which the player then says")
    val KET_IS = line("ket_is", "his answer to option 1 for a JalYt-Ket")
    val MEJ_IS = line("mej_is", "his answer to option 1 for a JalYt-Mej")
    val XIL_IS = line("xil_is", "his answer to option 1 for a JalYt-Xil")
    val HUR_IS = line("hur_is", "his answer to option 1 for a JalYt-Hur")
    val SUPPOSE = line("suppose", "the player: they suppose they are")
    val THEN_YOU = line("then_you", "his reply, naming the caste", "caste")
    val WHAT_ARE_YOU = line("what_are_you", "the next menu's option 1, which the player then says")
    val THANKS = line("thanks", "its option 2, which the player then says")
    val I_AM_KET = line("i_am_ket", "his answer: he is a TzHaar-Ket")
    val THE_CASTES = line("the_castes", "then: the other three castes")
    val NO_PROBLEMS = line("no_problems", "his answer to 'I guess so'")
    val CRAZY = line("crazy", "his answer to 'No I'm not!'")
    val PIN = line("pin", "option 3: PIN settings, which the player then says")
    val MORE = line("more", "option 4: the second page")
    val COLLECT = line("collect", "the second page's option 1, which the player then says")
    val BUY_SLOTS = line("buy_slots", "its option 2 (the Wiki has no transcript after it)")
    val FINE = line("fine", "its option 3, which the player then says")
    val PREVIOUS = line("previous", "its option 4: back to the first page")
}

/**
 * What the TzHaar call a player, as the Wiki's TzHaar page has it (read 2026-11-18): JalYt (a human), then the caste
 * whose two skills add up highest (Mej: Magic and Prayer; Ket: Strength and Defence; Xil: Attack and Agility; Hur:
 * Crafting and Smithing), then Xo once the inner city is open to them (step K2.3, [MorUlRekInner]), then
 * the player's name. Inferred: a tie going to the first of Mej, Ket, Xil, Hur (the Wiki doesn't say; this is the
 * order its page lists them in). Left out: Kal (TzKal-Zuk killed), which the Inferno will add.
 */
object TzHaarNames {
    enum class Caste(val word: String, val a: Skill, val b: Skill) {
        MEJ("Mej", Skill.MAGIC, Skill.PRAYER),
        KET("Ket", Skill.STRENGTH, Skill.DEFENCE),
        XIL("Xil", Skill.ATTACK, Skill.AGILITY),
        HUR("Hur", Skill.CRAFTING, Skill.SMITHING),
    }

    /** The first caste in Mej, Ket, Xil, Hur order whose two base levels add up highest (the tie order inferred). */
    fun caste(player: Player): Caste {
        var best = Caste.MEJ
        var top = -1
        for (caste in Caste.entries) {
            val sum = player.skills.baseLevel(caste.a) + player.skills.baseLevel(caste.b)
            if (sum > top) {
                top = sum
                best = caste
            }
        }
        return best
    }

    /** JalYt and the caste, as "JalYt-Ket". */
    fun casteTitle(player: Player): String = "JalYt-${caste(player).word}"

    /** The whole name, as "JalYt-Ket-Ada", or "JalYt-Ket-Xo-Ada" once the inner city is open to them ([MorUlRekInner.hasAccess]). */
    fun name(player: Player): String = "${casteTitle(player)}-${if (MorUlRekInner.hasAccess(player)) "Xo-" else ""}${player.username}"
}

/**
 * Mor Ul Rek's outer city (Karamja step K2.1), as the Wiki's Mor Ul Rek, Cave entrance (to Mor Ul Rek), Cave exit (Mor
 * Ul Rek), Karamjan rum, Lava forge, Sulphur vent, TzHaar-Ket-Zuh and Mor Ul Rek north bank pages have it (read
 * 2026-11-18), with ids and tiles from the 242 cache and map:
 *
 * - **The cave entrance** (11835, at the dungeon's south end, 2863,9571) takes the player into the city by its cave
 *   exit (11836, 2479,5176), and the exit back out. Karamjan rum carried in that way breaks in the heat, 1 damage a
 *   bottle (the Wiki's rum page); rum banked in the city is safe.
 * - **TzHaar-Ket-Zuh** (7677) minds the north bank: his talk (the transcript, with the player's TzHaar name,
 *   [TzHaarNames]), Bank and Collect. The bank chest and deposit box beside him are Banking's own.
 * - **The lava forge** (11978) smelts as a furnace does and **the sulphur vents** (11851) cook as a fire does (Smithing
 *   and Cooking list them). The mines' rocks are Mining's own.
 * - **The city's TzHaar** (TzHaar-Hur, -Mej, -Xil and level 149 -Ket) wander it, attackable (npc-combat.json), at the
 *   Wiki's spawn points (`mor-ul-rek.json`).
 *
 * Inferred: the rum's message (keys only) and its damage in one hit; the caste names shown as the transcript's
 * brackets have them. Guessed: the tiles the cave entrance and exit put the player on; the chathead faces. Left out
 * here: the shops (step K2.2, [TzHaarShops]), the inner city, its barrier guards and the level 221 TzHaar-Ket (K2.3,
 * [MorUlRekInner]), the eels
 * (K2.4), the Fight Cave and Inferno and their TzHaar (the Fight Caves thread), the Fight Pits and TzHaar-Ket-Rak, the
 * fairy ring and buying bank slots.
 */
object MorUlRek {
    const val CAVE_ENTRANCE = 11835
    const val CAVE_EXIT = 11836
    const val ENTER = 1

    /** Where the entrance puts the player in the city, and the exit in the dungeon. */
    val IN_CITY = Destination(2480, 5175, 0)
    val IN_DUNGEON = Destination(2862, 9572, 0)

    /** The one tile each cave stands on, on the 242 map: a copy elsewhere does nothing (exploit review R50B-1). */
    val PLACED = mapOf(CAVE_ENTRANCE to Destination(2863, 9571, 0), CAVE_EXIT to Destination(2479, 5176, 0))

    const val BANKER = 7677

    /**
     * TzHaar-Ket-Yil, the inner city's east bank (step K2.3): the Wiki's transcript of him is TzHaar-Ket-Zuh's word for
     * word (but for a slip, "JalYt-Hur-[TzHaar caste]", in his greeting), so he says the same lines.
     */
    const val BANKER_INNER = 7678
    const val TALK_TO = 1
    const val BANK = 3
    const val COLLECT = 4

    /** Hurt by [Player.inventory]'s rum: the number of bottles, each taken. */
    fun breakRum(player: Player): Int {
        var count = 0
        for (rum in KaramjaShips.RUM) {
            val held = player.inventory.count(rum).toInt()
            if (held > 0) Ned.take(player, rum, held)
            count += held
        }
        return count
    }

    /** What the banker's talk ends in. */
    internal suspend fun Dialogue.banker(player: Player): Bankers.Ask = with(TzHaarBankerLines) {
        var page = 1
        val name = TzHaarNames.name(player)
        val caste = TzHaarNames.casteTitle(player)
        chatNpc(ChatAnim.NEUTRAL, GREETING, "name" to name)
        while (page > 0) {
            when (choice(ACCESS, WHAT_CALL, PIN, MORE)) {
                1 -> return Bankers.Ask.BANK
                2 -> {
                    chatNpc(ChatAnim.NEUTRAL, ARE_YOU_NOT, "name" to name)
                    when (choice(text(WHATS_CASTE, "caste" to caste), text(GUESS_SO), text(NOT))) {
                        1 -> {
                            chatPlayer(ChatAnim.QUIZ, WHATS_CASTE, "caste" to caste)
                            chatNpc(ChatAnim.NEUTRAL, when (TzHaarNames.caste(player)) {
                                TzHaarNames.Caste.KET -> KET_IS
                                TzHaarNames.Caste.MEJ -> MEJ_IS
                                TzHaarNames.Caste.XIL -> XIL_IS
                                TzHaarNames.Caste.HUR -> HUR_IS
                            })
                            chatPlayer(ChatAnim.NEUTRAL, SUPPOSE)
                            chatNpc(ChatAnim.NEUTRAL, THEN_YOU, "caste" to caste)
                            if (choice(WHAT_ARE_YOU, THANKS) == 1) {
                                chatPlayer(ChatAnim.QUIZ, WHAT_ARE_YOU)
                                chatNpc(ChatAnim.NEUTRAL, I_AM_KET)
                                chatNpc(ChatAnim.NEUTRAL, THE_CASTES)
                            } else {
                                chatPlayer(ChatAnim.NEUTRAL, THANKS)
                            }
                        }
                        2 -> {
                            chatPlayer(ChatAnim.NEUTRAL, GUESS_SO)
                            chatNpc(ChatAnim.NEUTRAL, NO_PROBLEMS)
                        }
                        else -> {
                            chatPlayer(ChatAnim.ANGRY, NOT)
                            chatNpc(ChatAnim.NEUTRAL, CRAZY)
                        }
                    }
                    return Bankers.Ask.NOTHING
                }
                3 -> {
                    chatPlayer(ChatAnim.NEUTRAL, PIN)
                    return Bankers.Ask.PIN
                }
                else -> when (choice(COLLECT, BUY_SLOTS, FINE, PREVIOUS)) {
                    1 -> {
                        chatPlayer(ChatAnim.NEUTRAL, COLLECT)
                        return Bankers.Ask.COLLECT
                    }
                    2 -> return Bankers.Ask.NOTHING
                    3 -> {
                        chatPlayer(ChatAnim.NEUTRAL, FINE)
                        return Bankers.Ask.NOTHING
                    }
                    else -> page++
                }
            }
        }
        Bankers.Ask.NOTHING
    }
}

fun registerMorUlRek(world: World) {
    with(MorUlRek) {
        world.handlers.onLoc(CAVE_ENTRANCE, ENTER) { op ->
            arriveDelay()
            if (!placedAt(world, op.loc, PLACED[CAVE_ENTRANCE])) return@onLoc
            world.teleport(player, IN_CITY.x, IN_CITY.z, IN_CITY.level)
            val rum = breakRum(player)
            if (rum > 0) {
                world.message(player, MorUlRekLines.RUM_EXPLODES)
                world.meleeCombat.statusHit(player, rum, HitsplatType.DAMAGE_ME, HitsplatType.DAMAGE_OTHER)
            }
        }
        world.handlers.onLoc(CAVE_EXIT, ENTER) { op ->
            arriveDelay()
            if (!placedAt(world, op.loc, PLACED[CAVE_EXIT])) return@onLoc
            world.teleport(player, IN_DUNGEON.x, IN_DUNGEON.z, IN_DUNGEON.level)
        }
        for (keeper in listOf(BANKER, BANKER_INNER)) {
            world.handlers.onNpc(keeper, TALK_TO) { op ->
                var ask = Bankers.Ask.NOTHING
                world.dialogues.start(this, op.npc) { ask = banker(player) }
                answer(world, ask)
            }
            world.handlers.onNpc(keeper, BANK) { world.bankScreen.open(player) }
            world.handlers.onNpc(keeper, COLLECT) { world.grandExchangeScreen.openCollectionBox(player) }
        }
    }
}

private fun ScriptContext.answer(world: World, ask: Bankers.Ask) = when (ask) {
    Bankers.Ask.BANK -> world.bankScreen.open(player)
    Bankers.Ask.PIN -> world.bankPinSettings.open(player)
    Bankers.Ask.COLLECT -> world.grandExchangeScreen.openCollectionBox(player)
    Bankers.Ask.NOTHING -> Unit
}
