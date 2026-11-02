package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

/** Professor Oddenstein's lines once Ernest the Chicken is done (the Wiki's Transcript:Professor Oddenstein, read 2026-11-02). */
object OddensteinLines : Lines("oddenstein", "Transcript:Professor Oddenstein") {
    val CAREFUL = line("careful", "his greeting (the Wiki's standard dialogue; that it still comes after the quest is guessed)")
    val PORTAL = line("portal", "option 1 after Ernest the Chicken, which the player then says")
    val RIFT = line("rift", "option 1: his first answer")
    val HUH = line("huh", "option 1: the player's reply")
    val TELEPORT = line("teleport", "option 1: his last line")
    val HOUSE = line("house", "option 2, which the player then says")
    val TENANTS = line("tenants", "option 2: his answer")
}

/** The witch's lines once Animal Magnetism is done (the Wiki's Transcript:Witch (Draynor Manor), read 2026-11-02). */
object ManorWitchLines : Lines("manor_witch", "Transcript:Witch (Draynor Manor)") {
    val ICKLE = line("ickle", "after Animal Magnetism: the witch's first line")
    val WONDERED = line("wondered", "the player's answer")
    val CLEARLY_NOT = line("clearly_not", "her last line")
}

/** Ava's lines once Animal Magnetism is done, on a members' world (the Wiki's Transcript:Ava, read 2026-11-02). */
object AvaLines : Lines("ava", "Transcript:Ava") {
    val HELLO = line("hello", "her greeting")
    val DEVICES = line("devices", "option 1, which the player then says; Ava's Devices opens")
    val INFORMATION = line("information", "option 2, which the player then says")
    val INFO_1 = line("info_1", "option 2: Ava's first line")
    val INFO_2 = line("info_2", "option 2: her second")
    val INFO_3 = line("info_3", "option 2: her third")
    val INFO_4 = line("info_4", "option 2: her fourth")
    val INFO_5 = line("info_5", "option 2: her fifth (its first page)")
    val INFO_6 = line("info_6", "option 2: the fifth's second page")
    val INFO_7 = line("info_7", "option 2: her sixth")
    val INFO_8 = line("info_8", "option 2: her seventh")
    val INFO_9 = line("info_9", "option 2: her last")
    val ORBS = line("orbs", "option 3, which the player then says; the locator orb interface would open")
    val MORE = line("more", "option 4")
    val STUFF = line("stuff", "More options, option 1, which the player then says")
    val SHOP = line("shop", "More options, option 1: Ava's answer; the shop opens")
    val HEAD_OFF = line("head_off", "More options, option 2, which the player then says")
    val BED = line("bed", "More options, option 2: Ava's answer")
    val PREVIOUS = line("previous", "More options, option 3; back to the first menu")
}

/**
 * Draynor Manor's people (Draynor piece step 12b): Professor Oddenstein (`professor_oddenstein` 3562) on the top floor,
 * and on the ground floor the witch (`anma_witch` 4409) and Ava (`anma_assistant` 4407). The witch and Ava are spawned as
 * the types their multi-NPCs (`anma_witch_multi` 4410, `anma_assistant_multi` 4408, varbit 32) show once Animal Magnetism
 * is done, which every account counts as; Oddenstein talks as he does once Ernest the Chicken is done. Ava's Odds and
 * Ends is in shops.json and opens on Trade ([registerShops]) and from her talk; Ava's Devices ([AvaDevicesShop], step 12c)
 * opens on her Devices option and from her option 1 (as for a player with no Dizana's quiver).
 *
 * Left out: her locator orbs (option 3 ends after the player's line), Dizana's quiver and the Pandemonium cannonball option (Sailing)
 * aren't built; Oddenstein's portal to the Killerwatt plane isn't spawned. Guessed: every chathead expression, that
 * Oddenstein still greets the player after the quest, and that "Previous options" goes back to Ava's menu without her
 * greeting again.
 */
object DraynorManorPeople {
    const val ODDENSTEIN = 3562
    const val WITCH = 4409
    const val AVA = 4407
    const val TALK_TO = 1
    const val DEVICES = 4

    internal suspend fun Dialogue.oddenstein() = with(OddensteinLines) {
        chatNpc(ChatAnim.NEUTRAL, CAREFUL)
        if (choice(PORTAL, HOUSE) == 1) {
            chatPlayer(ChatAnim.QUIZ, PORTAL)
            chatNpc(ChatAnim.HAPPY, RIFT)
            chatPlayer(ChatAnim.CONFUSED, HUH)
            chatNpc(ChatAnim.NEUTRAL, TELEPORT)
        } else {
            chatPlayer(ChatAnim.QUIZ, HOUSE)
            chatNpc(ChatAnim.NEUTRAL, TENANTS)
        }
    }

    internal suspend fun Dialogue.witch() = with(ManorWitchLines) {
        chatNpc(ChatAnim.NEUTRAL, ICKLE)
        chatPlayer(ChatAnim.NEUTRAL, WONDERED)
        chatNpc(ChatAnim.NEUTRAL, CLEARLY_NOT)
    }

    /** Where Ava's talk ends: nowhere, in her shop, or in Ava's Devices. */
    enum class AvaEnd { NONE, SHOP, DEVICES }

    /** Ava's talk, and where it ends. */
    internal suspend fun Dialogue.ava(): AvaEnd = with(AvaLines) {
        chatNpc(ChatAnim.NEUTRAL, HELLO)
        var end: AvaEnd? = null
        while (end == null) {
            end = when (choice(DEVICES, INFORMATION, ORBS, MORE)) {
                1 -> {
                    chatPlayer(ChatAnim.NEUTRAL, DEVICES)
                    AvaEnd.DEVICES
                }
                2 -> {
                    chatPlayer(ChatAnim.NEUTRAL, INFORMATION)
                    for (info in listOf(INFO_1, INFO_2, INFO_3, INFO_4, INFO_5, INFO_6, INFO_7, INFO_8, INFO_9)) chatNpc(ChatAnim.NEUTRAL, info)
                    AvaEnd.NONE
                }
                3 -> {
                    chatPlayer(ChatAnim.QUIZ, ORBS)
                    AvaEnd.NONE
                }
                else -> when (choice(STUFF, HEAD_OFF, PREVIOUS)) {
                    1 -> {
                        chatPlayer(ChatAnim.NEUTRAL, STUFF)
                        chatNpc(ChatAnim.HAPPY, SHOP)
                        AvaEnd.SHOP
                    }
                    2 -> {
                        chatPlayer(ChatAnim.NEUTRAL, HEAD_OFF)
                        chatNpc(ChatAnim.HAPPY, BED)
                        AvaEnd.NONE
                    }
                    else -> null
                }
            }
        }
        end ?: AvaEnd.NONE
    }
}

/** The manor's people's Talk-to. Ava's Trade is [registerShops]'s. */
fun registerDraynorManorPeople(world: World) {
    world.handlers.onNpc(DraynorManorPeople.ODDENSTEIN, DraynorManorPeople.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(DraynorManorPeople) { oddenstein() } }
    }
    world.handlers.onNpc(DraynorManorPeople.WITCH, DraynorManorPeople.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(DraynorManorPeople) { witch() } }
    }
    world.handlers.onNpc(DraynorManorPeople.AVA, DraynorManorPeople.TALK_TO) { op ->
        var end = DraynorManorPeople.AvaEnd.NONE
        world.dialogues.start(this, op.npc) { end = with(DraynorManorPeople) { ava() } }
        when (end) {
            DraynorManorPeople.AvaEnd.SHOP -> world.shopTypes.ofNpc(DraynorManorPeople.AVA)?.let { world.shopScreen.open(player, it) }
            DraynorManorPeople.AvaEnd.DEVICES -> AvaDevicesShop.open(world, player)
            DraynorManorPeople.AvaEnd.NONE -> Unit
        }
    }
    world.handlers.onNpc(DraynorManorPeople.AVA, DraynorManorPeople.DEVICES) { AvaDevicesShop.open(world, player) }
    AvaDevicesShop.register(world)
}
