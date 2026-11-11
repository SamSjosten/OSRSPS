package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.ge.PriceGuides.Guide

/** Brugsen Bursen's words, in the dialogue file, from the Wiki's Transcript:Brugsen Bursen, read 2026-11-11. */
object BrugsenLines : Lines("brugsen", "Transcript:Brugsen Bursen") {
    val GREETING = line("greeting", "Talk-to: his greeting, asking how he can help")
    val ABOUT = line("about", "menu option: the player asks about the place, which they then say")
    val ABOUT_1 = line("about_1", "about the place: his first box, the best trading spot")
    val ABOUT_2 = line("about_2", "about the place: his second box, people come from all over")
    val ABOUT_3 = line("about_3", "about the place: his third box, the clerks in the middle")
    val ABOUT_4 = line("about_4", "about the place: his last box, can he do anything else, before the menu again")
    val PRICE = line("price", "menu option: the player asks an item's price, which they then say")
    val WHICH_ITEM = line("which_item", "an item's price: he asks which item (then the guide prices window opens)")
    val NO_THANKS = line("no_thanks", "menu option: the player declines, which they then say")
}

/** Murky Matt's words, from the Wiki's Transcript:Murky Matt (runes), read 2026-11-11. */
object MurkyMattLines : Lines("murky_matt", "Transcript:Murky Matt (runes)") {
    val GREETING = line("greeting", "Talk-to: his first line")
    val PIRATE = line("pirate", "menu option 1: what's a pirate doing here, which the player then says")
    val PIRATE_1 = line("pirate_1", "option 1: his first answer")
    val PIRATE_2 = line("pirate_2", "option 1: the player's second line")
    val PIRATE_3 = line("pirate_3", "option 1: his second answer")
    val PIRATE_4 = line("pirate_4", "option 1: the player's third line")
    val PIRATE_5 = line("pirate_5", "option 1: his third answer")
    val PIRATE_6 = line("pirate_6", "option 1: the player's fourth line")
    val PIRATE_7 = line("pirate_7", "option 1: his last answer (before the Lunar Diplomacy lines, left out)")
    val PRICES = line("prices", "menu option 2: rune prices, which the player then says (the list opens)")
    val COMBINE = line("combine", "menu option 3: combine teleport jewellery charges, which the player then says")
    val FORGING = line("forging", "menu option 4: rings of forging, which the player then says")
    val NOT_YET = line("not_yet", "options 3 and 4 (not built here): his answer, guessed")
    val SWAB = line("swab", "menu option 5: the player has to swab some decks, which they then say")
    val SWAB_1 = line("swab_1", "option 5: his answer, first box")
    val SWAB_2 = line("swab_2", "option 5: his answer, second box")
    val SWAB_3 = line("swab_3", "option 5: the player's second line")
    val SWAB_4 = line("swab_4", "option 5: his \"...\" line")
    val SWAB_5 = line("swab_5", "option 5: the player's last line")
}

/** Relobo Blinyo's words, from the Wiki's Transcript:Relobo Blinyo (logs), read 2026-11-11. */
object ReloboLines : Lines("relobo", "Transcript:Relobo Blinyo (logs)") {
    val GREETING = line("greeting", "Talk-to: he greets the player and offers log prices")
    val TRAVELLED = line("travelled", "menu option 1: the player remarks he looks travel-worn, which they then say")
    val TRAVEL_1 = line("travel_1", "option 1: his first answer")
    val TRAVEL_2 = line("travel_2", "option 1: the player's second line")
    val TRAVEL_3 = line("travel_3", "option 1: his second answer")
    val TRAVEL_4 = line("travel_4", "option 1: the player's third line")
    val TRAVEL_5 = line("travel_5", "option 1: his third answer, first box")
    val TRAVEL_6 = line("travel_6", "option 1: his third answer, second box")
    val TRAVEL_7 = line("travel_7", "option 1: the player's fourth line")
    val TRAVEL_8 = line("travel_8", "option 1: his last answer, before the second menu")
    val PRICES = line("prices", "menu option: log prices, which the player then says (the list opens)")
    val LEAVE = line("leave", "menu option: the player must leave, which they then say")
    val FAREWELL = line("farewell", "after the player leaves: his farewell")
}

/** Hofuthand's words, from the Wiki's Transcript:Hofuthand (weapons and armour), read 2026-11-11. */
object HofuthandLines : Lines("hofuthand", "Transcript:Hofuthand (weapons and armour)") {
    val OPENING = line("opening", "Talk-to: the player's opening line")
    val GREETING = line("greeting", "Talk-to: his answer, before the menu")
    val FLUSTERED = line("flustered", "menu option 1: he seems flustered, which the player then says")
    val FLUSTERED_1 = line("flustered_1", "option 1: his first answer")
    val FLUSTERED_2 = line("flustered_2", "option 1: the player's second line")
    val FLUSTERED_3 = line("flustered_3", "option 1: his second answer")
    val FLUSTERED_4 = line("flustered_4", "option 1: the player's third line")
    val FLUSTERED_5 = line("flustered_5", "option 1: his last answer, before the short menu")
    val PRICES = line("prices", "menu option 2: weapon and armour prices, which the player then says (the list opens)")
    val CHARGING = line("charging", "menu option 3: the bank's auto-charging, which the player then says")
    val CHARGING_1 = line("charging_1", "option 3: his first answer")
    val CHARGING_2 = line("charging_2", "option 3: the player's second line")
    val CHARGING_3 = line("charging_3", "option 3: his second answer (the threshold menu after it isn't built: the talk ends)")
    val LEAVE = line("leave", "menu option 4: the player will leave him alone, which they then say")
    val FAREWELL = line("farewell", "option 4: his answer")
}

/** Farid Morrisane's words, from the Wiki's Transcript:Farid Morrisane (ores and bars), read 2026-11-11. */
object FaridLines : Lines("farid", "Transcript:Farid Morrisane (ores and bars)") {
    val OPENING = line("opening", "Talk-to: the player's opening line")
    val REBUKE = line("rebuke", "Talk-to: his rebuke, before the menu")
    val CALM = line("calm", "menu option 1: calm down, junior, which the player then says")
    val CALM_1 = line("calm_1", "option 1: his first answer, don't tell him to calm down")
    val CALM_2 = line("calm_2", "option 1: his second box, who he is")
    val CALM_3 = line("calm_3", "option 1: the player's second line")
    val CALM_4 = line("calm_4", "option 1: his third box")
    val CALM_5 = line("calm_5", "option 1: the player's third line")
    val CALM_6 = line("calm_6", "option 1: his last answer, before the menu again")
    val PRICES = line("prices", "menu option 2: ore and bar prices, which the player then says (the list opens)")
    val LEAVE = line("leave", "menu option 3: someone more his height, which the player then says")
    val FAREWELL = line("farewell", "option 3: his answer")
}

/**
 * The Grand Exchange's market experts (Misthalin sweep): Brugsen Bursen (2152) at the middle, Murky Matt (runes,
 * 5450), Relobo Blinyo (logs, 5451), Hofuthand (weapons and armour, 5452) and Farid Morrisane (ores and bars, 3115),
 * spawned in varrock.json. Prices opens their price list ([osrsps.game.ge.PriceGuides]) and so does the talk's prices
 * option; Brugsen's Prices, and his talk's item price, open the guide prices window (the price checker), the Wiki's
 * "search for an item's guide price". Bob Barter's Prices is here too (his talk is in [osrsps.game.skill.Decanting]).
 *
 * Talk-to follows each transcript's tree; the words are in the dialogue file. Left out: Murky Matt's Decant (teleport
 * jewellery charges) and rings of forging, which end the talk with a guessed line; his Lunar Diplomacy lines; Hofuthand's
 * auto-charging threshold, which ends after his explanation; Brugsen's Deadman options; Farid's options after "calm
 * down" (the Wiki doesn't list them: the menu comes back); the experts' overhead lines. Guessed: every chathead
 * expression.
 */
object GrandExchangeExperts {
    const val BRUGSEN = 2152
    const val MURKY_MATT = 5450
    const val RELOBO = 5451
    const val HOFUTHAND = 5452
    const val FARID = 3115
    const val BOB_BARTER = 5449

    const val TALK_TO = 1
    const val PRICES = 3

    val GUIDES = mapOf(MURKY_MATT to Guide.RUNES, RELOBO to Guide.LOGS, HOFUTHAND to Guide.WEAPONS_AND_ARMOUR, FARID to Guide.ORES_AND_BARS, BOB_BARTER to Guide.HERBS)

    /** What a talk ends at. */
    enum class Outcome { NOTHING, PRICE_LIST, PRICE_CHECKER }

    internal suspend fun Dialogue.brugsen(): Outcome = with(BrugsenLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        // About the place brings the menu back; the others end the talk.
        var picked = choice(ABOUT, PRICE, NO_THANKS)
        while (picked == 1) {
            chatPlayer(ChatAnim.QUIZ, ABOUT)
            chatNpc(ChatAnim.HAPPY, ABOUT_1)
            chatNpc(ChatAnim.NEUTRAL, ABOUT_2)
            chatNpc(ChatAnim.NEUTRAL, ABOUT_3)
            chatNpc(ChatAnim.QUIZ, ABOUT_4)
            picked = choice(ABOUT, PRICE, NO_THANKS)
        }
        if (picked == 2) {
            chatPlayer(ChatAnim.QUIZ, PRICE)
            chatNpc(ChatAnim.QUIZ, WHICH_ITEM)
            Outcome.PRICE_CHECKER
        } else {
            chatPlayer(ChatAnim.NEUTRAL, NO_THANKS)
            Outcome.NOTHING
        }
    }

    internal suspend fun Dialogue.murkyMatt(): Outcome = with(MurkyMattLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        var picked = choice(PIRATE, PRICES, COMBINE, FORGING, SWAB)
        if (picked == 1) {
            chatPlayer(ChatAnim.QUIZ, PIRATE)
            chatNpc(ChatAnim.HAPPY, PIRATE_1)
            chatPlayer(ChatAnim.QUIZ, PIRATE_2)
            chatNpc(ChatAnim.NEUTRAL, PIRATE_3)
            chatPlayer(ChatAnim.QUIZ, PIRATE_4)
            chatNpc(ChatAnim.NEUTRAL, PIRATE_5)
            chatPlayer(ChatAnim.NEUTRAL, PIRATE_6)
            chatNpc(ChatAnim.HAPPY, PIRATE_7)
            // The short menu: prices, combining charges, or swabbing decks, each as at the top.
            picked = when (choice(PRICES, COMBINE, SWAB)) {
                1 -> 2
                2 -> 3
                else -> 5
            }
        }
        when (picked) {
            2 -> {
                chatPlayer(ChatAnim.NEUTRAL, PRICES)
                Outcome.PRICE_LIST
            }
            3, 4 -> {
                chatPlayer(ChatAnim.QUIZ, if (picked == 3) COMBINE else FORGING)
                chatNpc(ChatAnim.NEUTRAL, NOT_YET)
                Outcome.NOTHING
            }
            else -> {
                chatPlayer(ChatAnim.HAPPY, SWAB)
                chatNpc(ChatAnim.LAUGH, SWAB_1)
                chatNpc(ChatAnim.LAUGH, SWAB_2)
                chatPlayer(ChatAnim.NEUTRAL, SWAB_3)
                chatNpc(ChatAnim.NEUTRAL, SWAB_4)
                chatPlayer(ChatAnim.NEUTRAL, SWAB_5)
                Outcome.NOTHING
            }
        }
    }

    internal suspend fun Dialogue.relobo(): Outcome = with(ReloboLines) {
        chatNpc(ChatAnim.HAPPY, GREETING)
        var picked = choice(TRAVELLED, PRICES, LEAVE)
        if (picked == 1) {
            chatPlayer(ChatAnim.NEUTRAL, TRAVELLED)
            chatNpc(ChatAnim.NEUTRAL, TRAVEL_1)
            chatPlayer(ChatAnim.QUIZ, TRAVEL_2)
            chatNpc(ChatAnim.NEUTRAL, TRAVEL_3)
            chatPlayer(ChatAnim.QUIZ, TRAVEL_4)
            chatNpc(ChatAnim.NEUTRAL, TRAVEL_5)
            chatNpc(ChatAnim.NEUTRAL, TRAVEL_6)
            chatPlayer(ChatAnim.NEUTRAL, TRAVEL_7)
            chatNpc(ChatAnim.HAPPY, TRAVEL_8)
            picked = if (choice(PRICES, LEAVE) == 1) 2 else 3
        }
        if (picked == 2) {
            chatPlayer(ChatAnim.NEUTRAL, PRICES)
            Outcome.PRICE_LIST
        } else {
            chatPlayer(ChatAnim.NEUTRAL, LEAVE)
            chatNpc(ChatAnim.HAPPY, FAREWELL)
            Outcome.NOTHING
        }
    }

    internal suspend fun Dialogue.hofuthand(): Outcome = with(HofuthandLines) {
        chatPlayer(ChatAnim.NEUTRAL, OPENING)
        chatNpc(ChatAnim.NEUTRAL, GREETING)
        var picked = choice(FLUSTERED, PRICES, CHARGING, LEAVE)
        if (picked == 1) {
            chatPlayer(ChatAnim.NEUTRAL, FLUSTERED)
            chatNpc(ChatAnim.ANGRY, FLUSTERED_1)
            chatPlayer(ChatAnim.QUIZ, FLUSTERED_2)
            chatNpc(ChatAnim.ANGRY, FLUSTERED_3)
            chatPlayer(ChatAnim.NEUTRAL, FLUSTERED_4)
            chatNpc(ChatAnim.NEUTRAL, FLUSTERED_5)
            picked = if (choice(PRICES, LEAVE) == 1) 2 else 4
        }
        when (picked) {
            2 -> {
                chatPlayer(ChatAnim.NEUTRAL, PRICES)
                Outcome.PRICE_LIST
            }
            3 -> {
                chatPlayer(ChatAnim.QUIZ, CHARGING)
                chatNpc(ChatAnim.NEUTRAL, CHARGING_1)
                chatPlayer(ChatAnim.QUIZ, CHARGING_2)
                chatNpc(ChatAnim.NEUTRAL, CHARGING_3)
                Outcome.NOTHING
            }
            else -> {
                chatPlayer(ChatAnim.NEUTRAL, LEAVE)
                chatNpc(ChatAnim.NEUTRAL, FAREWELL)
                Outcome.NOTHING
            }
        }
    }

    internal suspend fun Dialogue.farid(): Outcome = with(FaridLines) {
        chatPlayer(ChatAnim.NEUTRAL, OPENING)
        chatNpc(ChatAnim.ANGRY, REBUKE)
        // Calm down brings the menu back; the others end the talk.
        var picked = choice(CALM, PRICES, LEAVE)
        while (picked == 1) {
            chatPlayer(ChatAnim.NEUTRAL, CALM)
            chatNpc(ChatAnim.ANGRY, CALM_1)
            chatNpc(ChatAnim.ANGRY, CALM_2)
            chatPlayer(ChatAnim.NEUTRAL, CALM_3)
            chatNpc(ChatAnim.ANGRY, CALM_4)
            chatPlayer(ChatAnim.NEUTRAL, CALM_5)
            chatNpc(ChatAnim.NEUTRAL, CALM_6)
            picked = choice(CALM, PRICES, LEAVE)
        }
        if (picked == 2) {
            chatPlayer(ChatAnim.QUIZ, PRICES)
            Outcome.PRICE_LIST
        } else {
            chatPlayer(ChatAnim.NEUTRAL, LEAVE)
            chatNpc(ChatAnim.ANGRY, FAREWELL)
            Outcome.NOTHING
        }
    }
}

fun registerGrandExchangeExperts(world: World) {
    val talks: Map<Int, suspend Dialogue.() -> GrandExchangeExperts.Outcome> = mapOf(
        GrandExchangeExperts.BRUGSEN to { with(GrandExchangeExperts) { brugsen() } },
        GrandExchangeExperts.MURKY_MATT to { with(GrandExchangeExperts) { murkyMatt() } },
        GrandExchangeExperts.RELOBO to { with(GrandExchangeExperts) { relobo() } },
        GrandExchangeExperts.HOFUTHAND to { with(GrandExchangeExperts) { hofuthand() } },
        GrandExchangeExperts.FARID to { with(GrandExchangeExperts) { farid() } },
    )
    fun prices(player: osrsps.game.Player, npc: Int) {
        val guide = GrandExchangeExperts.GUIDES[npc]
        if (guide == null) world.priceChecker.open(player) else world.priceGuides.open(player, guide)
    }
    for ((npc, talk) in talks) {
        world.handlers.onNpc(npc, GrandExchangeExperts.TALK_TO) { op ->
            var outcome = GrandExchangeExperts.Outcome.NOTHING
            world.dialogues.start(this, op.npc) { outcome = talk() }
            if (outcome != GrandExchangeExperts.Outcome.NOTHING) prices(player, npc)
        }
    }
    for (npc in talks.keys + GrandExchangeExperts.BOB_BARTER) {
        world.handlers.onNpc(npc, GrandExchangeExperts.PRICES) { prices(player, npc) }
    }
}
