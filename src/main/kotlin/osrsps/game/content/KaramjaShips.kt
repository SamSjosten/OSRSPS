package osrsps.game.content

import net.rsprot.protocol.game.outgoing.interfaces.IfSetAnim
import osrsps.game.FrameSlot
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/**
 * The Karamja boat's words: the three Port Sarim seamen and the Musa Point customs officer (the Wiki's Transcript pages,
 * read 2026-11-17). Every account has finished Pandemonium and Pirate's Treasure, so these are the lines after both.
 */
object KaramjaShipLines : Lines("karamja_ship", "Transcript:Captain Tobias; Transcript:Seaman Lorris; Transcript:Seaman Thresnor; Transcript:Customs officer") {
    val SEAMAN_GREETING = line("seaman_greeting", "a seaman's greeting after Pandemonium, without Karamja gloves: Musa Point or the Pandemonium, 30 coins")
    val SEAMAN_GREETING_GLOVES = line("seaman_greeting_gloves", "the same, wearing Karamja gloves: Musa Point for 15 coins, the Pandemonium for 30")
    val YES_PLEASE = line("yes_please", "the seaman's menu, option 1, which the player then says")
    val NO_THANK_YOU = line("no_thank_you", "the seaman's menu, option 2")
    val WHERE = line("where", "the seaman asks where to go")
    val TO_MUSA_POINT = line("to_musa_point", "the where menu, option 1, which the player then says")
    val TO_PANDEMONIUM = line("to_pandemonium", "the where menu, option 2, which the player then says")
    val NOWHERE = line("nowhere", "the where menu, option 3, which the player then says")
    val NO_MONEY = line("no_money", "the player, without the fare (the seaman's pre-Pandemonium transcript; inferred to stay after it)")

    val CUSTOMS_HELP = line("customs_help", "the customs officer's opening question")
    val JOURNEY = line("journey", "her menu, option 1, which the player then says")
    val KNOW_YOU = line("know_you", "after Pirate's Treasure, without rum: she knows the player from the plantation")
    val BOARDING_CHARGE = line("boarding_charge", "then: the 30-coin boarding charge")
    val OK = line("ok", "the boarding menu, option 1, which the player then says")
    val NOT_BOTHER = line("not_bother", "the boarding menu, option 2, which the player then says")
    val GLOVES_HALF_PRICE = line("gloves_half_price", "option 1 wearing Karamja gloves: she spots them and halves the fare")
    val SPOT_INSPECTION = line("spot_inspection", "after Pirate's Treasure, with rum: her spot inspection")
    val SMUGGLING = line("smuggling", "then: she finds the rum")
    val PERSONAL_USE = line("personal_use", "the rum menu's one option, which the player then says")
    val CONFISCATED = line("confiscated", "the message: she takes the rum")
    val CUSTOMS_ASK = line("customs_ask", "her menu, option 2 (the option's words)")
    val CUSTOMS_ASK_SAY = line("customs_ask_say", "option 2: what the player says, which has one word more than the option")
    val NOT_THAT_SORT = line("not_that_sort", "option 2: her answer")

    val PAID = line("paid", "the message on paying a fare and boarding", "fare")
    val NOT_ENOUGH_COINS = line("not_enough_coins", "the Port Sarim (right-click) option without 30 coins")
    val NOT_ENOUGH_COINS_GLOVES = line("not_enough_coins_gloves", "the right-click option wearing Karamja gloves, without 15 coins")
    val GLOVES_SMILE = line("gloves_smile", "the right-click option wearing Karamja gloves with 15 coins")
    val ARRIVES_MUSA_POINT = line("arrives_musa_point", "the box on landing at Musa Point (INFERRED from the customs officer's 'The ship arrives at Port Sarim.')")
    val ARRIVES_PORT_SARIM = line("arrives_port_sarim", "the box on landing at Port Sarim")
    val PANDEMONIUM_CLOSED = line("pandemonium_closed", "ours: the Pandemonium (a Sailing island) isn't built, so no boat goes there")
}

/**
 * The boat between Port Sarim and Musa Point (Karamja step K1.1), as the Wiki's Captain Tobias, Seaman Lorris, Seaman
 * Thresnor, Customs officer and Musa Point pages have it:
 *
 * - **The seamen** on Port Sarim's dock (`captain_tobias`, `seaman_lorris`, `seaman_thresnor`) take a player to Musa Point
 *   for 30 coins, 15 wearing Karamja gloves, from their talk or their Musa Point option.
 * - **The customs officer** on Musa Point's dock takes them back for the same fare, from her talk or her Port Sarim
 *   option, and first takes any Karamjan rum (Asgarnia bans it).
 * - **The trip:** the coins go, the ship journey screen (`ship_journey`) shows the boat's crossing, and the player lands
 *   on the other ship's deck, to leave by its gangplank. The gangplanks cross between deck and dock both ways.
 *
 * All four are the cache's multi-NPCs on `sailing_intro`, which shows their after-Pandemonium types (a Musa Point or Port
 * Sarim option and a Pandemonium one); every account has finished it. The Pandemonium is a Sailing island and isn't
 * built, so going there gives a message of ours.
 *
 * Inferred: Lorris's and Thresnor's talk being Tobias's (the Wiki has only their pre-Pandemonium talk, which matches his);
 * the seamen's paid message and arrival box being the customs officer's, for the other port; the player's no-money line
 * staying after Pandemonium; the right-click options taking rum as the talk does; every Karamjan rum (the Tai Bwo Wannai
 * Trio's two as well) being rum; the crossing lasting [JOURNEY_TICKS], the length of the boat's animation
 * (`sarim_karamja`, 191 client cycles). Guessed: the decks' landing tiles (beside each ship's gangplank, open deck on the
 * 242 map), the gangplanks' dock tiles, the chathead faces, and that a gangplank needs no fare (the ship goes nowhere
 * without one). Left out: the Ring of Charos(a)'s charm options (Garden of Tranquillity's ring), and the
 * Pandemonium.
 */
object KaramjaShips {
    const val CAPTAIN_TOBIAS = 3644
    const val SEAMAN_LORRIS = 3645
    const val SEAMAN_THRESNOR = 3646
    const val CUSTOMS_OFFICER = 3648
    val SEAMEN = listOf(CAPTAIN_TOBIAS, SEAMAN_LORRIS, SEAMAN_THRESNOR)

    const val TALK_TO = 1

    /** The after-Pandemonium types' options: Musa Point (or Port Sarim) and The Pandemonium. */
    const val TRAVEL = 3
    const val PANDEMONIUM = 4

    const val COINS = 995
    const val FARE = 30
    const val GLOVES_FARE = 15

    /** Karamja gloves 1 to 4 (`atjun_gloves_*`): any of them worn halves the fare. */
    val KARAMJA_GLOVES = listOf(11136, 11138, 11140, 13103)

    /** Karamjan rum: the plain bottle and the Tai Bwo Wannai Trio's two with a banana in it. */
    val RUM = listOf(431, 3164, 3165)

    /** Interface ship_journey: the sea chart with the boat ([JOURNEY_BOAT]) crossing it. */
    const val JOURNEY = 299
    const val JOURNEY_BOAT = 6

    /** The boat's crossing on the chart: seqs sarim_karamja and karamja_sarim. */
    const val SEQ_SARIM_KARAMJA = 634
    const val SEQ_KARAMJA_SARIM = 635

    /** The crossing's 191 client cycles (15 + 44 frames of 4), 3.8 s, rounded up to ticks. */
    const val JOURNEY_TICKS = 7

    /** Gangplanks: `_on` from the dock onto the deck, `_off` back. The Musa Point ship is the Sarim one, and the other way round. */
    const val SARIM_SHIP_PLANK_ON = 2081
    const val SARIM_SHIP_PLANK_OFF = 2082
    const val KARAMJA_SHIP_PLANK_ON = 2083
    const val KARAMJA_SHIP_PLANK_OFF = 2084
    const val CROSS = 1

    /** One end of the route: where a passenger lands on the ship's deck, and the dock below its gangplank. */
    class Port(val deck: Destination, val dock: Destination)

    /** Musa Point's ship lies south of its dock, its gangplank at 2956, 3144 to 3145. */
    val MUSA_POINT = Port(Destination(2956, 3143, 1), Destination(2956, 3146, 0))

    /** Port Sarim's lies east of its dock, its gangplank at 3030 to 3031, 3217. */
    val PORT_SARIM = Port(Destination(3032, 3217, 1), Destination(3029, 3217, 0))

    fun wearsGloves(player: Player): Boolean = KARAMJA_GLOVES.any { player.equipment.count(it) > 0 }

    fun fare(player: Player): Int = if (wearsGloves(player)) GLOVES_FARE else FARE

    fun hasRum(player: Player): Boolean = RUM.any { player.inventory.count(it) > 0 }
}

/** What a boat talk ended in; [SAIL] once the fare is taken. */
private enum class Boat { STAY, SAIL, PANDEMONIUM }

fun registerKaramjaShips(world: World) {
    with(KaramjaShips) {
        for (seaman in SEAMEN) {
            world.handlers.onNpc(seaman, TALK_TO) { op ->
                var boat = Boat.STAY
                var fare = 0
                world.dialogues.start(this, op.npc) { boat = seaman { fare = it } }
                when (boat) {
                    Boat.SAIL -> {
                        world.message(player, KaramjaShipLines.PAID, "fare" to fare)
                        sail(world, MUSA_POINT, SEQ_SARIM_KARAMJA, KaramjaShipLines.ARRIVES_MUSA_POINT)
                    }
                    Boat.PANDEMONIUM -> world.message(player, KaramjaShipLines.PANDEMONIUM_CLOSED)
                    Boat.STAY -> Unit
                }
            }
            world.handlers.onNpc(seaman, TRAVEL) {
                if (payAtOnce(world)) sail(world, MUSA_POINT, SEQ_SARIM_KARAMJA, KaramjaShipLines.ARRIVES_MUSA_POINT)
            }
            world.handlers.onNpc(seaman, PANDEMONIUM) { world.message(player, KaramjaShipLines.PANDEMONIUM_CLOSED) }
        }

        world.handlers.onNpc(CUSTOMS_OFFICER, TALK_TO) { op ->
            var fare = 0
            world.dialogues.start(this, op.npc) { customs { fare = it } }
            if (fare > 0) {
                world.message(player, KaramjaShipLines.PAID, "fare" to fare)
                sail(world, PORT_SARIM, SEQ_KARAMJA_SARIM, KaramjaShipLines.ARRIVES_PORT_SARIM)
            }
        }
        world.handlers.onNpc(CUSTOMS_OFFICER, TRAVEL) {
            if (confiscate(world)) return@onNpc
            if (payAtOnce(world, customs = true)) sail(world, PORT_SARIM, SEQ_KARAMJA_SARIM, KaramjaShipLines.ARRIVES_PORT_SARIM)
        }
        world.handlers.onNpc(CUSTOMS_OFFICER, PANDEMONIUM) { world.message(player, KaramjaShipLines.PANDEMONIUM_CLOSED) }

        world.handlers.onLoc(SARIM_SHIP_PLANK_ON, CROSS) { cross(world, MUSA_POINT.deck) }
        world.handlers.onLoc(SARIM_SHIP_PLANK_OFF, CROSS) { cross(world, MUSA_POINT.dock) }
        world.handlers.onLoc(KARAMJA_SHIP_PLANK_ON, CROSS) { cross(world, PORT_SARIM.deck) }
        world.handlers.onLoc(KARAMJA_SHIP_PLANK_OFF, CROSS) { cross(world, PORT_SARIM.dock) }
    }
}

private suspend fun ScriptContext.cross(world: World, to: Destination) {
    arriveDelay()
    world.teleport(player, to.x, to.z, to.level)
}

/**
 * A right-click fare: the coins, or the message saying how many it takes. True once paid. The customs officer smiles at
 * Karamja gloves ([customs]); a seaman just takes the 15 (inferred: the Wiki has no seaman's line for it).
 */
private fun ScriptContext.payAtOnce(world: World, customs: Boolean = false): Boolean = with(KaramjaShips) {
    val gloves = wearsGloves(player)
    val fare = fare(player)
    if (player.inventory.count(COINS) < fare) {
        world.message(player, if (gloves) KaramjaShipLines.NOT_ENOUGH_COINS_GLOVES else KaramjaShipLines.NOT_ENOUGH_COINS)
        return false
    }
    Ned.take(player, COINS, fare)
    if (gloves && customs) world.message(player, KaramjaShipLines.GLOVES_SMILE) else world.message(player, KaramjaShipLines.PAID, "fare" to fare)
    true
}

/** The customs officer's right-click search: true when she took rum, which ends it there. */
private fun ScriptContext.confiscate(world: World): Boolean {
    if (!KaramjaShips.hasRum(player)) return false
    for (rum in KaramjaShips.RUM) Ned.take(player, rum, Int.MAX_VALUE)
    world.message(player, KaramjaShipLines.CONFISCATED)
    return true
}

/**
 * The crossing, once paid: the chart with the boat sailing across it for [KaramjaShips.JOURNEY_TICKS], then the player on
 * [to]'s deck and the arrival box. The player is delayed throughout, so nothing they click interrupts it.
 */
private suspend fun ScriptContext.sail(world: World, to: KaramjaShips.Port, seq: Int, arrives: Line) {
    world.interfaces.closeModals(player)
    world.interfaces.openModal(player, FrameSlot.MAINMODAL, KaramjaShips.JOURNEY)
    player.session.queue(IfSetAnim(KaramjaShips.JOURNEY, KaramjaShips.JOURNEY_BOAT, seq))
    delay(KaramjaShips.JOURNEY_TICKS)
    world.teleport(player, to.deck.x, to.deck.z, to.deck.level)
    world.interfaces.closeModals(player)
    world.dialogues.start(this) { mesbox(arrives) }
}

/** A seaman's talk, after Pandemonium: what it ends in. Takes the fare when the player sails, and tells [paid] how much. */
private suspend fun Dialogue.seaman(paid: (Int) -> Unit): Boat = with(KaramjaShipLines) {
    val gloves = KaramjaShips.wearsGloves(player)
    chatNpc(ChatAnim.HAPPY, if (gloves) SEAMAN_GREETING_GLOVES else SEAMAN_GREETING)
    if (choice(YES_PLEASE, NO_THANK_YOU) != 1) {
        chatPlayer(ChatAnim.NEUTRAL, NO_THANK_YOU)
        return Boat.STAY
    }
    chatPlayer(ChatAnim.HAPPY, YES_PLEASE)
    chatNpc(ChatAnim.QUIZ, WHERE)
    when (choice(TO_MUSA_POINT, TO_PANDEMONIUM, NOWHERE)) {
        1 -> {
            chatPlayer(ChatAnim.NEUTRAL, TO_MUSA_POINT)
            val fare = KaramjaShips.fare(player)
            if (player.inventory.count(KaramjaShips.COINS) < fare) {
                chatPlayer(ChatAnim.SAD, NO_MONEY)
                return Boat.STAY
            }
            Ned.take(player, KaramjaShips.COINS, fare)
            paid(fare)
            return Boat.SAIL
        }
        2 -> {
            chatPlayer(ChatAnim.NEUTRAL, TO_PANDEMONIUM)
            return Boat.PANDEMONIUM
        }
        else -> chatPlayer(ChatAnim.NEUTRAL, NOWHERE)
    }
    Boat.STAY
}

/** The customs officer's talk, after Pirate's Treasure. Takes the fare when the player boards, and tells [paid] how much. */
private suspend fun Dialogue.customs(paid: (Int) -> Unit): Unit = with(KaramjaShipLines) {
    chatNpc(ChatAnim.NEUTRAL, CUSTOMS_HELP)
    if (choice(JOURNEY, CUSTOMS_ASK) != 1) {
        chatPlayer(ChatAnim.QUIZ, CUSTOMS_ASK_SAY)
        chatNpc(ChatAnim.NEUTRAL, NOT_THAT_SORT)
        return
    }
    chatPlayer(ChatAnim.QUIZ, JOURNEY)
    if (KaramjaShips.hasRum(player)) {
        chatNpc(ChatAnim.NEUTRAL, SPOT_INSPECTION)
        chatNpc(ChatAnim.ANGRY, SMUGGLING)
        choice(PERSONAL_USE)
        chatPlayer(ChatAnim.SAD, PERSONAL_USE)
        for (rum in KaramjaShips.RUM) Ned.take(player, rum, Int.MAX_VALUE)
        context.player.message(text(CONFISCATED))
        return
    }
    chatNpc(ChatAnim.HAPPY, KNOW_YOU)
    chatNpc(ChatAnim.NEUTRAL, BOARDING_CHARGE)
    if (choice(OK, NOT_BOTHER) != 1) {
        chatPlayer(ChatAnim.NEUTRAL, NOT_BOTHER)
        return
    }
    chatPlayer(ChatAnim.NEUTRAL, OK)
    val gloves = KaramjaShips.wearsGloves(player)
    val fare = KaramjaShips.fare(player)
    if (player.inventory.count(KaramjaShips.COINS) < fare) {
        chatPlayer(ChatAnim.SAD, NO_MONEY)
        return
    }
    if (gloves) chatNpc(ChatAnim.HAPPY, GLOVES_HALF_PRICE)
    Ned.take(player, KaramjaShips.COINS, fare)
    paid(fare)
}
