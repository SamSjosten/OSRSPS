package osrsps.game.tzhaar

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** TzHaar-Ket-Keh's talk by the Inferno (Transcript:TzHaar-Ket-Keh, read 2026-11-17). */
object KetKehLines : Lines("ket_keh", "Transcript:TzHaar-Ket-Keh") {
    val GREETING = line("greeting", "his first line: answering the player by caste and name", "caste", "name")
    val ABOUT = line("about", "menu: tell me about the Inferno, and what the player says")
    val HELP = line("help", "menu: can I help, and what the player says")
    val NEVER_MIND = line("never_mind", "menu: never mind, and what the player says")

    val HISTORY_1 = line("history_1", "about: born from eggs in lava pools")
    val HISTORY_2 = line("history_2", "about: some memories are lost; we experiment")
    val HISTORY_3 = line("history_3", "about: the largest birthing pool, deeper eggs")
    val HISTORY_4 = line("history_4", "about: we went too deep")
    val HISTORY_5 = line("history_5", "about: the sinkhole, the Inferno")
    val HISTORY_6 = line("history_6", "about: eggs from the Fight Cave")
    val HISTORY_7 = line("history_7", "about: bigger and stronger, they fought")

    val NOT_ALONE = line("not_alone", "about, cape given: dangerous creatures, and another being")
    val HUNGRY = line("hungry", "about, cape given (and after the sacrifice): TzKal-Zuk is hungry")
    val PRISON = line("prison", "about, cape given (and after the sacrifice): his goal is the city; sacrifices")
    val FEAR = line("fear", "about, not beaten yet (and after the sacrifice): we fear he can't be defeated")
    val HOPE = line("hope", "about, the Inferno beaten: you showed us he can be stopped")

    val CANT_TRUST = line("cant_trust", "about, no cape given: it gets worse, but can he trust the player", "caste", "name")
    val TRUSTWORTHY = line("trustworthy", "the player: pretty trustworthy")
    val ONE_IDEA = line("one_idea", "about: sacrifice your fire cape and I can take your word")
    val PROVE = line("prove", "help: first prove I can trust you")
    val TAKE_WORD = line("take_word", "help: sacrifice your fire cape and I can take your word")

    val SACRIFICE_TITLE = line("sacrifice_title", "the first menu's title: whether to sacrifice the fire cape")
    val YES = line("yes", "the sacrifice menus: agreeing")
    val NO = line("no", "the sacrifice menus: refusing")
    val SACRIFICE = line("sacrifice", "the player: I'd like to sacrifice my fire cape")
    val HAND_OVER = line("hand_over", "the sacrifice agreed: his answer, asking for the cape")
    val REALLY_TITLE = line("really_title", "the second menu's title, the cape's name in red: asking again")
    val KEEP_IT = line("keep_it", "the player: sorry, I want to keep it for now")
    val HANDED = line("handed", "the message box with the fire cape: you hand over your cape")
    val ENTER_NOW = line("enter_now", "thank you, you can enter the Inferno now", "caste", "name")
    val WHAT_DO = line("what_do", "the player: what should I do down there?")
    val KILL_SOME = line("kill_some", "if you can kill some of them it would help")
    val WARNING = line("warning", "now you can be trusted, a warning: another being")
    val CAN_HELP = line("can_help", "the player: I can help with that")
    val THANK_YOU = line("thank_you", "his thanks for the help offered, by caste and name", "caste", "name")
    val TAKE_OFF = line("take_off", "the player, with the fire cape worn: I'll need to take it off first")
    val NO_CAPE = line("no_cape", "the player, with no fire cape: I don't have one on me")

    val IMPRESSIVE = line("impressive", "TzKal-Zuk defeated: very impressive for a JalYt")
    val TOKEN = line("token", "TzKal-Zuk defeated: please accept this cape")
    val REWARD = line("reward", "TzKal-Zuk defeated: the message box with the infernal cape and TokKul", "tokkul")
}

/**
 * TzHaar-Ket-Keh by the Inferno's entrance in Mor Ul Rek (Inferno step IN1). He is the cache's multinpc
 * `inferno_master` (7690) on `total_zuk_kills` (varp 1585): `inferno_master_1op` (11247, Talk-to) until the player has
 * killed TzKal-Zuk, `inferno_master_2op` (11248, Talk-to and Exchange) after; the Wiki's infobox map has him at
 * 2496,5115 (npc-spawns/inferno.json). From his transcript's standard dialogue:
 *
 * - Talk-to: [KetKehLines.ABOUT] (the TzHaar's history, then TzKal-Zuk once the player has given a fire cape, or his
 *   doubt and the sacrifice offer before), [KetKehLines.HELP] (the sacrifice offer) and [KetKehLines.NEVER_MIND].
 * - The sacrifice, with a fire cape in the backpack: two menus ([KetKehLines.SACRIFICE_TITLE], then [KetKehLines.REALLY_TITLE]), then the cape
 *   goes for good and the Inferno's entrance shows Jump-in ([Inferno.sacrifice]). With one worn: [KetKehLines.TAKE_OFF].
 *   With none: [KetKehLines.NO_CAPE].
 * - TzKal-Zuk's death: his thanks and the infernal cape and TokKul message box ([victory]).
 *
 * Not built yet: Exchange (the infernal cape for a chance at Jal-Nib-Rek, IN4), his lines as wave 69 starts (IN4), the
 * TzKal slayer helmet talk. GUESSED: [KetKehLines.HELP] after the cape is given (the transcript has it only before)
 * says his [KetKehLines.KILL_SOME] line; the caste ([MejJal.caste]); every chathead.
 */
object KetKeh {
    const val TZHAAR_KET_KEH = 7690
    const val BEFORE_ZUK = 11247
    const val AFTER_ZUK = 11248
    const val TALK_TO = 1

    private fun capeIn(player: Player) = player.inventory.count(FightCaves.FIRE_CAPE) > 0

    private fun capeWorn(player: Player) = player.equipment.count(FightCaves.FIRE_CAPE) > 0

    private fun beaten(player: Player) =
        player.vars.types.varpCount > Inferno.VARP_ZUK_KILLS && player.vars[Inferno.VARP_ZUK_KILLS] > 0

    internal suspend fun Dialogue.talk(world: World, inferno: Inferno) = with(KetKehLines) {
        val caste = MejJal.caste(player)
        val name = player.username
        chatNpc(ChatAnim.NEUTRAL, GREETING, "caste" to caste, "name" to name)
        when (choice(ABOUT, HELP, NEVER_MIND)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, ABOUT)
                about(world, inferno, caste, name)
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, HELP)
                if (inferno.sacrificed(player)) return@with chatNpc(ChatAnim.NEUTRAL, KILL_SOME)
                chatNpc(ChatAnim.NEUTRAL, PROVE)
                chatNpc(ChatAnim.NEUTRAL, TAKE_WORD)
                offer(world, inferno, caste, name)
            }
            else -> chatPlayer(ChatAnim.NEUTRAL, NEVER_MIND)
        }
    }

    private suspend fun Dialogue.about(world: World, inferno: Inferno, caste: String, name: String) = with(KetKehLines) {
        for (line in listOf(HISTORY_1, HISTORY_2, HISTORY_3, HISTORY_4, HISTORY_5, HISTORY_6, HISTORY_7)) chatNpc(ChatAnim.NEUTRAL, line)
        if (inferno.sacrificed(player)) {
            chatNpc(ChatAnim.NEUTRAL, NOT_ALONE)
            chatNpc(ChatAnim.NEUTRAL, HUNGRY)
            chatNpc(ChatAnim.NEUTRAL, PRISON)
            chatNpc(ChatAnim.NEUTRAL, if (beaten(player)) HOPE else FEAR)
            return@with
        }
        chatNpc(ChatAnim.NEUTRAL, CANT_TRUST, "caste" to caste, "name" to name)
        chatPlayer(ChatAnim.NEUTRAL, TRUSTWORTHY)
        chatNpc(ChatAnim.NEUTRAL, ONE_IDEA)
        offer(world, inferno, caste, name)
    }

    /** The fire cape asked for: the sacrifice with one in the backpack, else why not. */
    private suspend fun Dialogue.offer(world: World, inferno: Inferno, caste: String, name: String) = with(KetKehLines) {
        if (!capeIn(player)) return@with chatPlayer(ChatAnim.NEUTRAL, if (capeWorn(player)) TAKE_OFF else NO_CAPE)
        if (choice(YES, NO, title = text(SACRIFICE_TITLE)) != 1) return@with
        chatPlayer(ChatAnim.NEUTRAL, SACRIFICE)
        chatNpc(ChatAnim.NEUTRAL, HAND_OVER)
        if (choice(NO, YES, title = text(REALLY_TITLE)) != 2) return@with chatPlayer(ChatAnim.NEUTRAL, KEEP_IT)
        // The cape may have gone while the menus were up.
        if (!takeCape(player)) return@with
        inferno.sacrifice(player)
        mesbox(HANDED)
        chatNpc(ChatAnim.NEUTRAL, ENTER_NOW, "caste" to caste, "name" to name)
        chatPlayer(ChatAnim.QUIZ, WHAT_DO)
        chatNpc(ChatAnim.NEUTRAL, KILL_SOME)
        chatNpc(ChatAnim.NEUTRAL, WARNING)
        chatNpc(ChatAnim.NEUTRAL, HUNGRY)
        chatNpc(ChatAnim.NEUTRAL, PRISON)
        chatNpc(ChatAnim.NEUTRAL, FEAR)
        chatPlayer(ChatAnim.NEUTRAL, CAN_HELP)
        chatNpc(ChatAnim.NEUTRAL, THANK_YOU, "caste" to caste, "name" to name)
    }

    private fun takeCape(player: Player): Boolean {
        for (slot in 0 until player.inventory.capacity) {
            if (player.inventory[slot]?.id == FightCaves.FIRE_CAPE) {
                player.inventory.remove(slot, 1)
                return true
            }
        }
        return false
    }

    /** TzKal-Zuk's death: his thanks, and the message box with the cape and [tokkul] (already given). */
    fun victory(world: World, player: Player, tokkul: Int) {
        val type = world.npcTypes[BEFORE_ZUK] ?: world.npcTypes[TZHAAR_KET_KEH] ?: return
        world.scripts.start(player) {
            world.dialogues.start(this) {
                chatNpc(type, type.name, ChatAnim.HAPPY, KetKehLines.IMPRESSIVE)
                chatNpc(type, type.name, ChatAnim.NEUTRAL, KetKehLines.TOKEN)
                mesbox(KetKehLines.REWARD, "tokkul" to tokkul)
            }
        }
    }

    fun register(world: World, inferno: Inferno) {
        for (id in listOf(TZHAAR_KET_KEH, BEFORE_ZUK, AFTER_ZUK)) {
            world.handlers.onNpc(id, TALK_TO) { op -> world.dialogues.start(this, op.npc) { talk(world, inferno) } }
        }
    }
}
