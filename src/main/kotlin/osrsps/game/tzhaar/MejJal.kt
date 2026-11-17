package osrsps.game.tzhaar

import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.content.message
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** TzHaar-Mej-Jal's Talk-to and fire cape exchange (Transcript:TzHaar-Mej-Jal, Standard dialogue, read 2026-11-17). */
object MejJalLines : Lines("mej_jal", "Transcript:TzHaar-Mej-Jal") {
    val GREETING = line("greeting", "his first line: \"You want help [TzHaar caste]-[player name]?\"", "caste", "name")
    val HAVE_CAPE = line("have_cape", "first menu with a fire cape in the backpack, and what the player says")
    val WHAT_PLACE = line("what_place", "first menu with no fire cape, and what the player says")
    val CALL_ME = line("call_me", "first menu: what he called the player, and what the player says")
    val FINE = line("fine", "first menu, last option: nothing, and what the player says")

    val SELL_OFFER = line("sell_offer", "the fire cape: his offer of 8,000 TokKul")
    val SELL_TITLE = line("sell_title", "the fire cape menu's title")
    val SELL_YES = line("sell_yes", "the fire cape menu: exchange it")
    val SELL_DONE = line("sell_done", "the cape exchanged: the message box")
    val SELL_NO = line("sell_no", "the fire cape menu: keep it")
    val WISE = line("wise", "kept: his answer")
    val BARGAIN = line("bargain", "the fire cape menu, without the pet: bargain for TzRek-Jad")
    val APPRENTICE = line("apprentice", "bargain: what the player asks")
    val GIVE_CAPE = line("give_cape", "bargain: his terms")
    val CAPE_BACK = line("cape_back", "bargain: the player asks for the cape back")
    val NO_CAPE_BACK = line("no_cape_back", "bargain: his answer")
    val GAMBLE_YES = line("gamble_yes", "bargain menu: try")
    val GAMBLE_NO = line("gamble_no", "bargain menu: keep the cape")
    val KEEP_CAPE = line("keep_cape", "bargain kept: what the player says")
    val LUCKY = line("lucky", "bargain won: the pet")
    val UNLUCKY = line("unlucky", "bargain lost")

    val PLACE = line("place", "what this place is: his answer")
    val RULES = line("rules", "what this place is, menu: any rules, and what the player says")
    val SURVIVAL = line("survival", "rules: his answer")
    val WIN = line("win", "rules menu: winning anything, and what the player says")
    val QUESTIONS = line("questions", "winning: his answer")
    val SILENCE = line("silence", "winning: the player's \"...\"")
    val TOKKUL_COINS = line("tokkul_coins", "winning: TokKul is like coins")
    val GOLD = line("gold", "winning: gold is soft")
    val SOUNDS_GOOD = line("sounds_good", "rules menu: done, and what the player says")
    val OK_THANKS = line("ok_thanks", "what this place is, menu: done, and what the player says")

    val NOT_A = line("not_a", "what he called them: his answer", "caste", "name")
    val WHAT_IS = line("what_is", "his answer's menu: what the caste is, and what the player says", "caste")
    val CASTE_KET = line("caste_ket", "what JalYt-Ket is: tough and strong")
    val CASTE_MEJ = line("caste_mej", "what JalYt-Mej is: mystic powers")
    val CASTE_XIL = line("caste_xil", "what JalYt-Xil is: agile and quick")
    val CASTE_HUR = line("caste_hur", "what JalYt-Hur is: making things")
    val SUPPOSE = line("suppose", "the caste: the player agrees")
    val THEN_YOU = line("then_you", "the caste: his answer", "caste")
    val WHAT_ARE_YOU = line("what_are_you", "the caste's menu: what he is, and what the player says")
    val MYSTIC = line("mystic", "what he is: his answer")
    val OTHER_TYPES = line("other_types", "what he is, menu: the other castes, and what the player says")
    val OTHERS = line("others", "the other castes: his answer")
    val AH_OK = line("ah_ok", "what he is, menu: done, and what the player says")
    val THANKS_EXPLAINING = line("thanks_explaining", "the caste's menu: done, and what the player says")
    val GUESS_SO = line("guess_so", "his answer's menu: agreeing, and what the player says")
    val NO_PROBLEMS = line("no_problems", "agreed: his answer")
    val NOT_ME = line("not_me", "his answer's menu: denying it, and what the player says")
    val CRAZY = line("crazy", "denied: his answer")
}

/**
 * TzHaar-Mej-Jal (2180, `tzhaar_fightcave_master`) by the Fight Cave's entrance in Mor Ul Rek (the Wiki's infobox map:
 * 2440,5170, within 3 tiles; npc-spawns/fight-cave.json), Fight Caves step FC4. From his transcript:
 *
 * - **Talk-to** greets the player by their TzHaar caste and offers the fire cape, what the cave is, and what he called
 *   them. A fire cape sells back for 8,000 TokKul, or is given up for a 1 in [PET_ODDS] chance at TzRek-Jad ("Bargain
 *   for TzRek-Jad", only for a player who doesn't own the pet: the backpack, worn items and bank checked). The cape
 *   goes either way ("There is no cape back").
 * - **Exchange fire cape** (op 3) goes straight to the cape's menu.
 *
 * Guessed (the transcript is marked incomplete, its fire cape part "wrong"): where the menus fall (the cape menu holds
 * sell, keep and bargain; "I guess so..." and "No I'm not!" answer "Are you not..."; "Thanks for explaining it." ends
 * the caste's menu; "What other types are there?" and "Ah ok then." follow what he is); Exchange fire cape without a
 * cape saying the no-cape line of the first menu (his "what is this place" talk); the caste, picked by the player's
 * best skill ([caste]); every chathead.
 */
object MejJal {
    const val TALK_TO = 1
    const val EXCHANGE = 3
    const val SELL_PRICE = 8_000
    const val PET_ODDS = 200

    /** The player's TzHaar caste. Guessed: their best skill, combat styles first: melee Ket, Magic Mej, Ranged Xil, else Hur. */
    fun caste(player: Player): String {
        val skills = player.skills
        val melee = maxOf(skills.baseLevel(Skill.ATTACK), skills.baseLevel(Skill.STRENGTH), skills.baseLevel(Skill.DEFENCE))
        val magic = skills.baseLevel(Skill.MAGIC)
        val ranged = skills.baseLevel(Skill.RANGED)
        val other = Skill.entries.filter { it !in COMBAT }.maxOfOrNull { skills.baseLevel(it) } ?: 1
        val best = maxOf(melee, magic, ranged, other)
        return when (best) {
            melee -> "JalYt-Ket"
            magic -> "JalYt-Mej"
            ranged -> "JalYt-Xil"
            else -> "JalYt-Hur"
        }
    }

    private val COMBAT = setOf(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.HITPOINTS, Skill.RANGED, Skill.MAGIC, Skill.PRAYER)

    fun ownsPet(player: Player): Boolean = FightCaves.PET.let {
        player.inventory.count(it) > 0 || player.equipment.count(it) > 0 || player.bank.count(it) > 0
    }

    private fun hasCape(player: Player) = player.inventory.count(FightCaves.FIRE_CAPE) > 0

    internal suspend fun Dialogue.talk(world: World) = with(MejJalLines) {
        val caste = caste(player)
        val name = player.username
        chatNpc(ChatAnim.NEUTRAL, GREETING, "caste" to caste, "name" to name)
        val cape = hasCape(player)
        when (choice(if (cape) HAVE_CAPE else WHAT_PLACE, CALL_ME, FINE)) {
            1 -> if (cape) {
                chatPlayer(ChatAnim.NEUTRAL, HAVE_CAPE)
                capeMenu(world)
            } else {
                place()
            }
            2 -> callMe(caste, name)
            else -> chatPlayer(ChatAnim.NEUTRAL, FINE)
        }
    }

    /** Exchange fire cape: the cape's menu straight away, or, with no cape, what the cave is. */
    internal suspend fun Dialogue.exchange(world: World) {
        if (hasCape(player)) capeMenu(world) else with(MejJalLines) {
            chatPlayer(ChatAnim.NEUTRAL, WHAT_PLACE)
            place()
        }
    }

    private suspend fun Dialogue.capeMenu(world: World) = with(MejJalLines) {
        chatNpc(ChatAnim.NEUTRAL, SELL_OFFER)
        val bargain = !ownsPet(player)
        val options = if (bargain) arrayOf(SELL_YES, SELL_NO, BARGAIN) else arrayOf(SELL_YES, SELL_NO)
        when (choice(*options, title = text(SELL_TITLE))) {
            1 -> {
                if (!takeCape(player)) return@with
                world.objTypes[FightCaves.TOKKUL]?.let { world.groundObjs.give(player, it, SELL_PRICE) }
                mesbox(SELL_DONE)
            }
            2 -> chatNpc(ChatAnim.NEUTRAL, WISE)
            else -> gamble(world)
        }
    }

    private suspend fun Dialogue.gamble(world: World) = with(MejJalLines) {
        chatPlayer(ChatAnim.QUIZ, APPRENTICE)
        chatNpc(ChatAnim.NEUTRAL, GIVE_CAPE)
        chatPlayer(ChatAnim.QUIZ, CAPE_BACK)
        chatNpc(ChatAnim.NEUTRAL, NO_CAPE_BACK)
        if (choice(GAMBLE_YES, GAMBLE_NO) != 1) return@with chatPlayer(ChatAnim.NEUTRAL, KEEP_CAPE)
        // The pet may have come some other way while the menu was up, or the cape gone: no toss then.
        if (ownsPet(player) || !takeCape(player)) return@with
        if (world.random.chance(1, PET_ODDS)) {
            world.objTypes[FightCaves.PET]?.let { world.groundObjs.give(player, it, 1) }
            world.message(player, FightCaveLines.PET)
            chatNpc(ChatAnim.HAPPY, LUCKY)
        } else {
            chatNpc(ChatAnim.NEUTRAL, UNLUCKY)
        }
    }

    /** Takes one fire cape from the backpack; false with none there. */
    private fun takeCape(player: Player): Boolean {
        for (slot in 0 until player.inventory.capacity) {
            if (player.inventory[slot]?.id == FightCaves.FIRE_CAPE) {
                player.inventory.remove(slot, 1)
                return true
            }
        }
        return false
    }

    private suspend fun Dialogue.place() = with(MejJalLines) {
        chatNpc(ChatAnim.NEUTRAL, PLACE)
        if (choice(RULES, OK_THANKS) != 1) return@with chatPlayer(ChatAnim.NEUTRAL, OK_THANKS)
        chatPlayer(ChatAnim.QUIZ, RULES)
        chatNpc(ChatAnim.NEUTRAL, SURVIVAL)
        if (choice(WIN, SOUNDS_GOOD) != 1) return@with chatPlayer(ChatAnim.NEUTRAL, SOUNDS_GOOD)
        chatPlayer(ChatAnim.QUIZ, WIN)
        chatNpc(ChatAnim.NEUTRAL, QUESTIONS)
        chatPlayer(ChatAnim.NEUTRAL, SILENCE)
        chatNpc(ChatAnim.NEUTRAL, TOKKUL_COINS)
        chatNpc(ChatAnim.NEUTRAL, GOLD)
    }

    private suspend fun Dialogue.callMe(caste: String, name: String) = with(MejJalLines) {
        chatPlayer(ChatAnim.QUIZ, CALL_ME)
        chatNpc(ChatAnim.QUIZ, NOT_A, "caste" to caste, "name" to name)
        when (choice(text(WHAT_IS, "caste" to caste), text(GUESS_SO), text(NOT_ME))) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, WHAT_IS, "caste" to caste)
                chatNpc(ChatAnim.QUIZ, casteLine(caste))
                chatPlayer(ChatAnim.NEUTRAL, SUPPOSE)
                chatNpc(ChatAnim.HAPPY, THEN_YOU, "caste" to caste)
                if (choice(WHAT_ARE_YOU, THANKS_EXPLAINING) != 1) return@with chatPlayer(ChatAnim.HAPPY, THANKS_EXPLAINING)
                chatPlayer(ChatAnim.QUIZ, WHAT_ARE_YOU)
                chatNpc(ChatAnim.NEUTRAL, MYSTIC)
                if (choice(OTHER_TYPES, AH_OK) != 1) return@with chatPlayer(ChatAnim.NEUTRAL, AH_OK)
                chatPlayer(ChatAnim.QUIZ, OTHER_TYPES)
                chatNpc(ChatAnim.NEUTRAL, OTHERS)
            }
            2 -> {
                chatPlayer(ChatAnim.NEUTRAL, GUESS_SO)
                chatNpc(ChatAnim.NEUTRAL, NO_PROBLEMS)
            }
            else -> {
                chatPlayer(ChatAnim.ANGRY, NOT_ME)
                chatNpc(ChatAnim.NEUTRAL, CRAZY)
            }
        }
    }

    private fun casteLine(caste: String) = when (caste) {
        "JalYt-Mej" -> MejJalLines.CASTE_MEJ
        "JalYt-Xil" -> MejJalLines.CASTE_XIL
        "JalYt-Hur" -> MejJalLines.CASTE_HUR
        else -> MejJalLines.CASTE_KET
    }

    fun register(world: World) {
        world.handlers.onNpc(FightCaves.MEJ_JAL, TALK_TO) { op -> world.dialogues.start(this, op.npc) { talk(world) } }
        world.handlers.onNpc(FightCaves.MEJ_JAL, EXCHANGE) { op -> world.dialogues.start(this, op.npc) { exchange(world) } }
    }
}
