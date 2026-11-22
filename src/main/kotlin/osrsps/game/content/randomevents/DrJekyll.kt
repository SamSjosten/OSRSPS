package osrsps.game.content.randomevents

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** Dr Jekyll's lines; the words are in the dialogue file, from his transcript (read 2026-11-19). */
object DrJekyllLines : Lines("dr_jekyll", "Transcript:Dr Jekyll") {
    val GREETING = line("greeting", "Overhead dialogue: upon spawning in for the player", "name")
    val HEY = line("hey", "Overhead dialogue: while waiting, dialogue 1", "name")
    val EXCUSE_ME = line("excuse_me", "Overhead dialogue: while waiting, dialogue 2", "name")
    val HEAR_ME = line("hear_me", "Overhead dialogue: while waiting, dialogue 3", "name")
    val JUST_A_HERB = line("just_a_herb", "Overhead dialogue: while waiting, dialogue 4", "name")
    val FREE_POTION = line("free_potion", "Overhead dialogue: while waiting, dialogue 5", "name")
    val MAYBE_LATER = line("maybe_later", "Overhead dialogue: if the player does not respond in time")
    val ASK = line("ask", "Standard dialogue: his question, the same for every herb but its name, in lower case ({herb})", "herb")
    val DONT_HAVE = line("dont_have", "Standard dialogue: the player has no herbs (the player's words)")
    val HERE_YOU_ARE = line("here_you_are", "Standard dialogue: the first option, and the player's words")
    val NEED_IT = line("need_it", "Standard dialogue: the second option, and the player's words")
    val THANK_YOU = line("thank_you", "Standard dialogue: after the herb")
    val FAIR_ENOUGH = line("fair_enough", "Standard dialogue: no herb, or the player keeps it")
    val SOMEONE_ELSE = line("someone_else", "Talking to a different player's Dr Jekyll", "name")
}

/**
 * Dr Jekyll (NPC 307, macro_jekyll; Talk-to, Dismiss), from the OSRS Wiki's Dr Jekyll page, Rewards table and
 * transcript (read 2026-11-19): he asks for the highest-level clean herb the player carries (Mod Ash) and gives its
 * potion for it; with no herb, or if the player keeps it, a Strength potion(2). With none he asks for guam leaf, as the
 * transcript shows. He is one of the three event NPCs that follow their player into a bank, and a members' event
 * (every world here is one).
 *
 * Guessed: the chathead expressions, and that he goes at once. Left out: the underwater Dr Jekyll, as nowhere here is
 * underwater.
 */
object DrJekyll : RandomEvent("dr_jekyll", 307) {
    /** Strength potion(2), 2dose1strength. */
    const val SPARE_POTION = 117

    /** A clean herb, its Herblore level and the four-dose potion it gets. */
    data class Herb(val id: Int, val level: Int, val potion: Int)

    /** The Rewards table, by the herbs' Cleaning levels. */
    val HERBS: List<Herb> = listOf(
        Herb(249, 3, 113), // guam_leaf: strength4
        Herb(251, 5, 2446), // marentill: 4doseantipoison
        Herb(253, 11, 2428), // tarromin: 4dose1attack
        Herb(255, 20, 2430), // harralander: 4dosestatrestore
        Herb(257, 25, 3008), // ranarr_weed: 4dose1energy
        Herb(2998, 30, 2432), // toadflax: 4dose1defense
        Herb(259, 40, 3032), // irit_leaf: 4dose1agility
        Herb(261, 48, 2436), // avantoe: 4dose2attack
        Herb(263, 54, 3016), // kwuarm: 4dose2energy
        Herb(3000, 59, 2440), // snapdragon: 4dose2strength
        Herb(265, 65, 3024), // cadantine: 4dose2restore
        Herb(2481, 67, 2442), // lantadyme: 4dose2defense
        Herb(267, 70, 3040), // dwarf_weed: 4dose1magic
        Herb(269, 75, 12625), // torstol: 4dosestamina
    )

    override val entersBanks = true
    override val greeting = DrJekyllLines.GREETING
    override val nags = with(DrJekyllLines) { listOf(HEY, EXCUSE_ME, HEAR_ME, JUST_A_HERB, FREE_POTION) }
    override val leaving = DrJekyllLines.MAYBE_LATER
    override val notYours = DrJekyllLines.SOMEONE_ELSE

    /** The highest-level herb [player] carries, or null for none. */
    fun wanted(inventory: osrsps.game.Inventory): Herb? = HERBS.lastOrNull { inventory.count(it.id) > 0 }

    override suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean = with(DrJekyllLines) {
        val herb = wanted(player.inventory)
        val name = (world.objTypes[herb?.id ?: HERBS.first().id]?.name ?: "herb").lowercase()
        chatNpc(ChatAnim.NEUTRAL, ASK, "herb" to name)
        if (herb == null) {
            chatPlayer(ChatAnim.NEUTRAL, DONT_HAVE)
        } else if (choice(HERE_YOU_ARE, NEED_IT) == 1) {
            chatPlayer(ChatAnim.NEUTRAL, HERE_YOU_ARE)
            val slot = (0 until player.inventory.capacity).firstOrNull { player.inventory[it]?.id == herb.id }
            if (slot != null) {
                // Herb out and potion in on the same tick, before his thanks: shutting the chat can't lose the herb.
                player.inventory.remove(slot, 1)
                events.reward(player, herb.potion)
                chatNpc(ChatAnim.HAPPY, THANK_YOU)
                return true
            }
            // Guessed: the herb went in the meantime, so it is as if the player kept it.
        } else {
            chatPlayer(ChatAnim.NEUTRAL, NEED_IT)
        }
        chatNpc(ChatAnim.NEUTRAL, FAIR_ENOUGH)
        events.reward(player, SPARE_POTION)
        true
    }
}
