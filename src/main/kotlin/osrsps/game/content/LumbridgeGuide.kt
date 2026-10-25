package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines

private val logger = KotlinLogging.logger {}

/**
 * The Lumbridge Guide's lines; the words are in the dialogue file, from the Wiki's Transcript:Lumbridge Guide (Standard
 * dialogue, for a player who has the Amulet of the Eye or hasn't unlocked it), read 2026-10-25. A line that comes in
 * several branches, such as his "anything else?" or the player's "No thank you", is one key: the Wiki shows the same
 * words each time.
 */
object LumbridgeGuideLines : Lines("lumbridge_guide", "Transcript:Lumbridge Guide") {
    val GREETING = line("greeting", "the Guide's first line")
    val QUEST = line("quest", "first menu, option 1: finding a quest, which the player then says")
    val MONSTERS = line("monsters", "first menu, option 2: which monsters to fight, which the player then says")
    val MONEY = line("money", "first menu, option 3: making money, which the player then says")
    val INFO = line("info", "first menu, option 4: more information, which the player then says")
    val MORE_OPTIONS = line("more_options", "first menu, option 5: more options")

    val ANYTHING_ELSE = line("anything_else", "the Guide asking if the player needs anything else (after most answers)")
    val YES_PLEASE = line("yes_please", "the 'anything else' menu: yes please, which the player then says")
    val YES_PLEASE_MODERATION = line("yes_please_moderation", "the 'anything else' menu after in-game moderation: yes, please (with a comma there), which the player then says")
    val NO_THANKS = line("no_thanks", "the 'anything else' menu: no thank you, which the player then says")
    val SOMETHING_ELSE = line("something_else", "the 'anything else' menu: something else, which the player then says")
    val WHAT_ELSE = line("what_else", "something else: the Guide asking what the player would like to know")

    val NO_MORE_QUESTS = line("no_more_quests", "quest, with all six quests done: the Guide's answer")
    val QUEST_ICON = line("quest_icon", "quest, with all six quests done: the box with the quest icon")

    val HIGH_LEVEL = line("high_level", "monsters, above his combat level: the Guide's answer")
    val COMBAT_TUTOR_ICON = line("combat_tutor_icon", "monsters, above his combat level: the box with the combat tutor icon")
    val NEED_WEAPON = line("need_weapon", "monsters, below his combat level with no weapon: the Guide's answer")
    val GET_WEAPON = line("get_weapon", "no weapon menu, option 1: getting a weapon, which the player then says")
    val STAFF = line("staff", "no weapon menu, option 2: a staff like his, which the player then says")
    val ONCE_ARMED = line("once_armed", "no weapon menu, option 3: what to fight once armed, which the player then says")
    val MAKE_FIND_BUY = line("make_find_buy", "getting a weapon: the Guide's answer")
    val FIND_WEAPON = line("find_weapon", "getting a weapon menu, option 1: finding one")
    val FIND_WEAPON_SAID = line("find_weapon_said", "finding a weapon: what the player then says")
    val FIND_1 = line("find_1", "finding a weapon: the Guide's answer, page 1")
    val FIND_2 = line("find_2", "finding a weapon: page 2")
    val FIND_3 = line("find_3", "finding a weapon: page 3 (one word)")
    val MAKE_WEAPON = line("make_weapon", "getting a weapon menu, option 2: making one, which the player then says")
    val SMITHING_KNOWN = line("smithing_known", "making a weapon, above his Smithing level: the Guide's answer")
    val SMITHING = line("smithing", "making a weapon: the Guide on Smithing")
    val WHERE_SMITH = line("where_smith", "making a weapon: the player asking where to smith")
    val SMITHING_TUTOR = line("smithing_tutor", "making a weapon: the Guide on the Smithing tutor")
    val SMITHING_TUTOR_ICON = line("smithing_tutor_icon", "making a weapon: the box with the Smithing tutor icon")
    val MINING_ICON = line("mining_icon", "making a weapon: the box with the mining site icon")
    val MINE_ORE = line("mine_ore", "making a weapon: the Guide on mining ore")
    val BUY_WEAPON = line("buy_weapon", "getting a weapon menu, option 3: buying one, which the player then says")
    val BUY_1 = line("buy_1", "buying a weapon: the Guide's answer, page 1")
    val BUY_2 = line("buy_2", "buying a weapon: page 2")
    val BUY_3 = line("buy_3", "buying a weapon: page 3")
    val MORE_WEAPONS = line("more_weapons", "the 'anything else' menu after weapons: more about getting weapons")
    val ONE_STAFF = line("one_staff", "a staff like his: the Guide's answer")

    val OVERVIEW_1 = line("overview_1", "monsters, with a weapon: the Guide's answer, page 1")
    val OVERVIEW_2 = line("overview_2", "monsters, with a weapon: page 2")
    val OVERVIEW_3 = line("overview_3", "monsters, with a weapon: page 3")
    val OVERVIEW_4 = line("overview_4", "monsters, with a weapon: page 4")
    val FOOD = line("food", "monsters menu, option 1: food, which the player then says")
    val BETTER_GEAR = line("better_gear", "monsters menu, option 2: better armour and weapons, which the player then says")
    val GO_KILL = line("go_kill", "monsters menu, option 3: off to kill things, which the player then says")
    val KILL_PLAYERS = line("kill_players", "monsters menu, option 4: killing other players, which the player then says")
    val FOODS = line("foods", "food: the Guide's answer")
    val CABBAGES = line("cabbages", "food menu, option 1: cabbages, which the player then says")
    val FISH = line("fish", "food menu, option 2: fishing, which the player then says")
    val MEAT = line("meat", "food menu, option 3: meat, which the player then says")
    val CABBAGE_FIELD = line("cabbage_field", "cabbages: the Guide's answer")
    val FARMING_PATCH = line("farming_patch", "cabbages: the Guide on growing them")
    val FISHING_KNOWN = line("fishing_known", "fishing, above his Fishing level: the Guide's answer")
    val FISHING_1 = line("fishing_1", "fishing: the Guide's answer, page 1")
    val FISHING_2 = line("fishing_2", "fishing: page 2")
    val FISHING_ICON = line("fishing_icon", "fishing: the box with the fishing spot icon")
    val NET_BOX = line("net_box", "fishing: the box with the small fishing net")
    val WHERE_NET = line("where_net", "fishing: the player asking where to get a net")
    val NET_1 = line("net_1", "fishing: the Guide on buying a net, page 1")
    val NET_2 = line("net_2", "fishing: the Guide on buying a net, page 2")
    val MEAT_1 = line("meat_1", "meat: the Guide's answer, page 1")
    val MEAT_2 = line("meat_2", "meat: page 2")
    val RANGE_ICON = line("range_icon", "meat: the box with the cooking range icon")
    val OTHER_FOOD = line("other_food", "the 'anything else' menu after food: other kinds of food")
    val GEAR_WAYS = line("gear_ways", "better armour and weapons: the Guide's answer")
    val WILDERNESS_1 = line("wilderness_1", "killing other players: the Guide's answer, page 1")
    val WILDERNESS_2 = line("wilderness_2", "killing other players: page 2")
    val MORE_COMBAT = line("more_combat", "the 'anything else' menu after killing players: more about combat")

    val MONEY_WAYS = line("money_ways", "money: the Guide's answer")
    val NO_BEGGING = line("no_begging", "money: the Guide on begging")
    val MONEY_SMITH = line("money_smith", "money menu, option 1: where to smith, which the player then says")
    val MONEY_FISH = line("money_fish", "money menu, option 2: how to fish, which the player then says")
    val MONEY_MONSTERS = line("money_monsters", "money menu, option 3: which monsters to fight (no player line follows)")

    val WIKI_1 = line("wiki_1", "information: the Guide's answer, page 1")
    val WIKI_2 = line("wiki_2", "information: page 2")

    val SECURITY = line("security", "more options menu, option 1: security, which the player then says")
    val BANK = line("bank", "more options menu, option 2: finding a bank, which the player then says")
    val NO_HELP = line("no_help", "more options menu, option 3: no help needed")
    val NO_HELP_SAID = line("no_help_said", "no help needed: what the player then says")
    val PREVIOUS = line("previous", "more options menu, option 4: previous options")
    val SECURITY_1 = line("security_1", "security: the Guide's answer")
    val SECURITY_TOPICS_1 = line("security_topics_1", "security: the Guide listing the topics, page 1")
    val SECURITY_TOPICS_2 = line("security_topics_2", "security: the topics, page 2")
    val PASSWORDS = line("passwords", "security menu, option 1: password security, which the player then says")
    val SCAMS = line("scams", "security menu, option 2: item scams, which the player then says")
    val MODERATION = line("moderation", "security menu, option 3: in-game moderation, which the player then says")
    val STRONGHOLD = line("stronghold", "security menu, option 4: the Stronghold of Security, which the player then says")
    val MORE_SECURITY = line("more_security", "the 'anything else' menu after security: more about security")
    val PASSWORD_1 = line("password_1", "password security: the Guide's answer, page 1")
    val PASSWORD_2 = line("password_2", "password security: page 2")
    val PASSWORD_3 = line("password_3", "password security: page 3")
    val MORE_TO_KNOW = line("more_to_know", "password security: the player asking if there is more")
    val FAKE_SITES_1 = line("fake_sites_1", "password security: the Guide on fake websites, page 1")
    val FAKE_SITES_2 = line("fake_sites_2", "password security: fake websites, page 2")
    val TROJANS_1 = line("trojans_1", "password security: the Guide on trojans and keyloggers, page 1")
    val TROJANS_2 = line("trojans_2", "password security: trojans, page 2")
    val TROJANS_3 = line("trojans_3", "password security: trojans, page 3")
    val NOT_HIDDEN = line("not_hidden", "password security: the Guide on the game not hiding passwords")
    val SCAMS_1 = line("scams_1", "item scams: the Guide's answer, page 1")
    val SCAMS_2 = line("scams_2", "item scams: page 2")
    val SCAMS_3 = line("scams_3", "item scams: page 3")
    val SCAMS_4 = line("scams_4", "item scams: page 4")
    val SCAMS_5 = line("scams_5", "item scams: page 5")
    val SCAMS_6 = line("scams_6", "item scams: page 6")
    val SCAMS_7 = line("scams_7", "item scams: page 7")
    val SCAMS_8 = line("scams_8", "item scams: page 8")
    val SCAMS_9 = line("scams_9", "item scams: page 9")
    val MODERATION_1 = line("moderation_1", "in-game moderation: the Guide's answer, page 1")
    val MODERATION_2 = line("moderation_2", "in-game moderation: page 2")
    val MODERATION_3 = line("moderation_3", "in-game moderation: page 3")
    val MODERATION_4 = line("moderation_4", "in-game moderation: page 4")
    val BECOME_MOD = line("become_mod", "in-game moderation: the player asking how to become a moderator")
    val BECOME_MOD_1 = line("become_mod_1", "in-game moderation: the Guide's answer, page 1")
    val BECOME_MOD_2 = line("become_mod_2", "in-game moderation: the Guide's answer, page 2")
    val STRONGHOLD_1 = line("stronghold_1", "Stronghold of Security: the Guide on the miner's cave")
    val FELL_ON_HEAD = line("fell_on_head", "Stronghold of Security: the player's first question")
    val STRONGHOLD_2 = line("stronghold_2", "Stronghold of Security: the Guide's answer")
    val HOW_SO = line("how_so", "Stronghold of Security: the player's second question")
    val STRONGHOLD_3 = line("stronghold_3", "Stronghold of Security: the Guide on the explorer")
    val WHERE_EXPLORER = line("where_explorer", "Stronghold of Security: the player asking where the explorer is")
    val STRONGHOLD_4 = line("stronghold_4", "Stronghold of Security: the Guide's answer")
    val SECURITY_BOOK = line("security_book", "Stronghold of Security, with no security book: the box as he hands one over")
    val BANK_UPSTAIRS = line("bank_upstairs", "bank: the Guide's answer")
    val BANK_ICON = line("bank_icon", "bank: the message box about the bank icon")
}

/**
 * The Lumbridge Guide (NPC 306) outside Lumbridge Castle's gate. From the Wiki: Talk-to's standard conversation for a
 * player who has the Amulet of the Eye or hasn't unlocked it, with every quest the Guide can point to done. Its first
 * menu comes back after most answers ("Yes please", "Something else"), and "more about ..." options go back to the menu
 * the answer was picked from.
 *
 * Not built: the lost Amulet of the Eye (there is no way to lose it here), the Tarnished locket, his Treasure Trails and
 * Astral Contacts parts, and opening the account management tab's useful links (the information answer only talks).
 * Guessed: every chathead expression; the combat, Smithing and Fishing levels his answers change at (the Wiki has "X");
 * the quest box's icon (the Wiki shows the quest list icon, Quests.png; the cache has quest_start_icon_dummy); that the
 * money menu's "where to smith" goes straight to the Smithing tutor and its "which monsters" to the monsters answer, with
 * no player line for the last (the Wiki has "same as above"); that "has a weapon" means one worn or carried that goes in
 * the weapon slot; that "has a security book" means carried (there is no bank yet); and that with a full inventory the
 * book lands at the player's feet, as the Duke's shield does.
 */
object LumbridgeGuide {
    const val ID = 306
    const val TALK_TO = 1

    /** Guessed: at this combat level and above he leaves the choice of monsters to the player. */
    const val HIGH_COMBAT_LEVEL = 20

    /** Guessed: at this Smithing level and above he says the player knows how to make weapons. */
    const val SMITHING_KNOWN_LEVEL = 20

    /** Guessed: at this Fishing level and above he says the player knows how to fish. */
    const val FISHING_KNOWN_LEVEL = 20

    /** The items shown in his boxes, with their 241 cache names. */
    const val QUEST_ICON_OBJ = 5092 // quest_start_icon_dummy (guessed, see above)
    const val COMBAT_TUTOR_ICON_OBJ = 22577 // combat_tutor_icon_dummy
    const val SMITHING_TUTOR_ICON_OBJ = 22574 // smithing_tutor_icon_dummy
    const val MINING_ICON_OBJ = 5082 // mining_site_icon_dummy
    const val FISHING_ICON_OBJ = 5088 // fishing_spot_icon_dummy
    const val RANGE_ICON_OBJ = 5090 // range_icon_dummy
    const val SMALL_FISHING_NET = 303 // net
    const val SECURITY_BOOK_OBJ = 9003 // sos_security_book

    /** Where an answer leads once said. */
    private enum class Next {
        /** The conversation ends. */
        END,

        /** The first menu, again. */
        FIRST_MENU,

        /** The menu the answer was picked from, again. */
        BACK,

        /** The security answer, again from the player's question. */
        SECURITY,
    }

    internal suspend fun Dialogue.talk(world: World) {
        chatNpc(ChatAnim.HAPPY, LumbridgeGuideLines.GREETING)
        while (firstMenu(world) != Next.END) Unit
    }

    private suspend fun Dialogue.firstMenu(world: World): Next = with(LumbridgeGuideLines) {
        when (choice(QUEST, MONSTERS, MONEY, INFO, MORE_OPTIONS)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, QUEST)
                chatNpc(ChatAnim.NEUTRAL, NO_MORE_QUESTS)
                objbox(QUEST_ICON_OBJ, QUEST_ICON)
                anythingElse(YES_PLEASE, NO_THANKS)
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, MONSTERS)
                monsters(world)
            }
            3 -> money(world)
            4 -> {
                chatPlayer(ChatAnim.QUIZ, INFO)
                chatNpc(ChatAnim.HAPPY, WIKI_1)
                chatNpc(ChatAnim.HAPPY, WIKI_2)
                anythingElse(YES_PLEASE, NO_THANKS)
            }
            else -> moreOptions(world)
        }
    }

    /** "Is there anything else?" and its menu of [options]. */
    private suspend fun Dialogue.anythingElse(vararg options: Line): Next = with(LumbridgeGuideLines) {
        chatNpc(ChatAnim.HAPPY, ANYTHING_ELSE)
        when (val picked = options[choice(*options) - 1]) {
            YES_PLEASE, YES_PLEASE_MODERATION -> {
                chatPlayer(ChatAnim.HAPPY, picked)
                Next.FIRST_MENU
            }
            NO_THANKS -> {
                chatPlayer(ChatAnim.HAPPY, NO_THANKS)
                Next.END
            }
            SOMETHING_ELSE -> {
                chatPlayer(ChatAnim.QUIZ, SOMETHING_ELSE)
                chatNpc(ChatAnim.HAPPY, WHAT_ELSE)
                Next.FIRST_MENU
            }
            MORE_SECURITY -> Next.SECURITY
            else -> Next.BACK // more weapons, other food, more combat
        }
    }

    /** Shows [menu] until an answer leads somewhere other than back to it. */
    private suspend fun Dialogue.menu(vararg options: Line, answer: suspend Dialogue.(Line) -> Next): Next {
        while (true) {
            val next = answer(options[choice(*options) - 1])
            if (next != Next.BACK) return next
        }
    }

    private suspend fun Dialogue.monsters(world: World): Next = with(LumbridgeGuideLines) {
        when {
            player.skills.combatLevel >= HIGH_COMBAT_LEVEL -> {
                chatNpc(ChatAnim.NEUTRAL, HIGH_LEVEL)
                objbox(COMBAT_TUTOR_ICON_OBJ, COMBAT_TUTOR_ICON)
                Next.FIRST_MENU
            }
            !hasWeapon(world, player) -> {
                chatNpc(ChatAnim.NEUTRAL, NEED_WEAPON)
                menu(GET_WEAPON, STAFF, ONCE_ARMED) { picked ->
                    when (picked) {
                        GET_WEAPON -> {
                            chatPlayer(ChatAnim.QUIZ, GET_WEAPON)
                            chatNpc(ChatAnim.NEUTRAL, MAKE_FIND_BUY)
                            menu(FIND_WEAPON, MAKE_WEAPON, BUY_WEAPON) { weapon(world, it) }
                        }
                        STAFF -> weapon(world, STAFF)
                        else -> {
                            chatPlayer(ChatAnim.QUIZ, ONCE_ARMED)
                            armed(world)
                        }
                    }
                }
            }
            else -> armed(world)
        }
    }

    /** Finding, making or buying a weapon, or a staff like his. */
    private suspend fun Dialogue.weapon(world: World, picked: Line): Next = with(LumbridgeGuideLines) {
        when (picked) {
            FIND_WEAPON -> {
                chatPlayer(ChatAnim.QUIZ, FIND_WEAPON_SAID)
                for (line in listOf(FIND_1, FIND_2, FIND_3)) chatNpc(ChatAnim.NEUTRAL, line)
                anythingElse(NO_THANKS, MORE_WEAPONS, SOMETHING_ELSE)
            }
            MAKE_WEAPON -> {
                chatPlayer(ChatAnim.QUIZ, MAKE_WEAPON)
                if (player.skills.baseLevel(Skill.SMITHING) >= SMITHING_KNOWN_LEVEL) {
                    chatNpc(ChatAnim.HAPPY, SMITHING_KNOWN)
                    return@with Next.END
                }
                chatNpc(ChatAnim.NEUTRAL, SMITHING)
                chatPlayer(ChatAnim.QUIZ, WHERE_SMITH)
                smithingTutor()
            }
            BUY_WEAPON -> {
                chatPlayer(ChatAnim.QUIZ, BUY_WEAPON)
                for (line in listOf(BUY_1, BUY_2, BUY_3)) chatNpc(ChatAnim.NEUTRAL, line)
                anythingElse(NO_THANKS, MORE_WEAPONS, SOMETHING_ELSE)
            }
            else -> {
                chatPlayer(ChatAnim.QUIZ, STAFF)
                chatNpc(ChatAnim.HAPPY, ONE_STAFF)
                anythingElse(MORE_WEAPONS, SOMETHING_ELSE, NO_THANKS)
            }
        }
    }

    /** Where to smith and mine, from making a weapon or making money. */
    private suspend fun Dialogue.smithingTutor(): Next = with(LumbridgeGuideLines) {
        chatNpc(ChatAnim.NEUTRAL, SMITHING_TUTOR)
        objbox(SMITHING_TUTOR_ICON_OBJ, SMITHING_TUTOR_ICON)
        objbox(MINING_ICON_OBJ, MINING_ICON)
        chatNpc(ChatAnim.NEUTRAL, MINE_ORE)
        anythingElse(YES_PLEASE, NO_THANKS)
    }

    /** What to fight with a weapon, and its menu. */
    private suspend fun Dialogue.armed(world: World): Next = with(LumbridgeGuideLines) {
        for (line in listOf(OVERVIEW_1, OVERVIEW_2, OVERVIEW_3, OVERVIEW_4)) chatNpc(ChatAnim.NEUTRAL, line)
        menu(FOOD, BETTER_GEAR, GO_KILL, KILL_PLAYERS) { picked ->
            chatPlayer(if (picked == GO_KILL) ChatAnim.HAPPY else ChatAnim.QUIZ, picked)
            when (picked) {
                FOOD -> {
                    chatNpc(ChatAnim.NEUTRAL, FOODS)
                    menu(CABBAGES, FISH, MEAT) { food(world, it) }
                }
                BETTER_GEAR -> {
                    chatNpc(ChatAnim.NEUTRAL, GEAR_WAYS)
                    menu(MAKE_WEAPON, BUY_WEAPON, STAFF) { weapon(world, it) }
                }
                GO_KILL -> Next.END
                else -> {
                    chatNpc(ChatAnim.NEUTRAL, WILDERNESS_1)
                    chatNpc(ChatAnim.NEUTRAL, WILDERNESS_2)
                    anythingElse(NO_THANKS, MORE_COMBAT, SOMETHING_ELSE)
                }
            }
        }
    }

    private suspend fun Dialogue.food(world: World, picked: Line): Next = with(LumbridgeGuideLines) {
        when (picked) {
            CABBAGES -> {
                chatPlayer(ChatAnim.QUIZ, CABBAGES)
                chatNpc(ChatAnim.NEUTRAL, CABBAGE_FIELD)
                chatNpc(ChatAnim.NEUTRAL, FARMING_PATCH)
                anythingElse(OTHER_FOOD, SOMETHING_ELSE, NO_THANKS)
            }
            FISH -> {
                chatPlayer(ChatAnim.QUIZ, FISH)
                fishing()
            }
            else -> {
                chatPlayer(ChatAnim.QUIZ, MEAT)
                chatNpc(ChatAnim.NEUTRAL, MEAT_1)
                chatNpc(ChatAnim.NEUTRAL, MEAT_2)
                objbox(RANGE_ICON_OBJ, RANGE_ICON)
                anythingElse(OTHER_FOOD, SOMETHING_ELSE, NO_THANKS)
            }
        }
    }

    /** How to fish, from food or making money. */
    private suspend fun Dialogue.fishing(): Next = with(LumbridgeGuideLines) {
        if (player.skills.baseLevel(Skill.FISHING) >= FISHING_KNOWN_LEVEL) {
            chatNpc(ChatAnim.HAPPY, FISHING_KNOWN)
            return@with Next.END
        }
        chatNpc(ChatAnim.NEUTRAL, FISHING_1)
        chatNpc(ChatAnim.NEUTRAL, FISHING_2)
        objbox(FISHING_ICON_OBJ, FISHING_ICON)
        objbox(SMALL_FISHING_NET, NET_BOX)
        chatPlayer(ChatAnim.QUIZ, WHERE_NET)
        chatNpc(ChatAnim.NEUTRAL, NET_1)
        chatNpc(ChatAnim.NEUTRAL, NET_2)
        anythingElse(OTHER_FOOD, SOMETHING_ELSE, NO_THANKS)
    }

    private suspend fun Dialogue.money(world: World): Next = with(LumbridgeGuideLines) {
        chatPlayer(ChatAnim.QUIZ, MONEY)
        chatNpc(ChatAnim.NEUTRAL, MONEY_WAYS)
        chatNpc(ChatAnim.NEUTRAL, NO_BEGGING)
        menu(MONEY_SMITH, MONEY_FISH, MONEY_MONSTERS) { picked ->
            when (picked) {
                MONEY_SMITH -> {
                    chatPlayer(ChatAnim.QUIZ, MONEY_SMITH)
                    smithingTutor()
                }
                MONEY_FISH -> {
                    chatPlayer(ChatAnim.QUIZ, MONEY_FISH)
                    fishing()
                }
                else -> monsters(world)
            }
        }
    }

    private suspend fun Dialogue.moreOptions(world: World): Next = with(LumbridgeGuideLines) {
        menu(SECURITY, BANK, NO_HELP, PREVIOUS) { picked ->
            when (picked) {
                SECURITY -> security(world)
                BANK -> {
                    chatPlayer(ChatAnim.QUIZ, BANK)
                    chatNpc(ChatAnim.NEUTRAL, BANK_UPSTAIRS)
                    mesbox(BANK_ICON)
                    anythingElse(YES_PLEASE, NO_THANKS)
                }
                NO_HELP -> {
                    chatPlayer(ChatAnim.NEUTRAL, NO_HELP_SAID)
                    Next.END
                }
                else -> Next.FIRST_MENU
            }
        }
    }

    /** Security and its four topics; "more about security" comes back here from each. */
    private suspend fun Dialogue.security(world: World): Next = with(LumbridgeGuideLines) {
        var next: Next
        do {
            chatPlayer(ChatAnim.QUIZ, SECURITY)
            chatNpc(ChatAnim.NEUTRAL, SECURITY_1)
            chatNpc(ChatAnim.NEUTRAL, SECURITY_TOPICS_1)
            chatNpc(ChatAnim.NEUTRAL, SECURITY_TOPICS_2)
            val picked = listOf(PASSWORDS, SCAMS, MODERATION, STRONGHOLD).let { it[choice(*it.toTypedArray()) - 1] }
            chatPlayer(ChatAnim.QUIZ, picked)
            next = when (picked) {
                PASSWORDS -> {
                    for (line in listOf(PASSWORD_1, PASSWORD_2, PASSWORD_3)) chatNpc(ChatAnim.NEUTRAL, line)
                    chatPlayer(ChatAnim.QUIZ, MORE_TO_KNOW)
                    for (line in listOf(FAKE_SITES_1, FAKE_SITES_2, TROJANS_1, TROJANS_2, TROJANS_3, NOT_HIDDEN)) chatNpc(ChatAnim.NEUTRAL, line)
                    anythingElse(MORE_SECURITY, SOMETHING_ELSE, NO_THANKS)
                }
                SCAMS -> {
                    for (line in listOf(SCAMS_1, SCAMS_2, SCAMS_3, SCAMS_4, SCAMS_5, SCAMS_6, SCAMS_7, SCAMS_8, SCAMS_9)) chatNpc(ChatAnim.NEUTRAL, line)
                    anythingElse(MORE_SECURITY, SOMETHING_ELSE, NO_THANKS)
                }
                MODERATION -> {
                    for (line in listOf(MODERATION_1, MODERATION_2, MODERATION_3, MODERATION_4)) chatNpc(ChatAnim.NEUTRAL, line)
                    chatPlayer(ChatAnim.QUIZ, BECOME_MOD)
                    chatNpc(ChatAnim.NEUTRAL, BECOME_MOD_1)
                    chatNpc(ChatAnim.NEUTRAL, BECOME_MOD_2)
                    anythingElse(NO_THANKS, YES_PLEASE_MODERATION, MORE_SECURITY)
                }
                else -> {
                    chatNpc(ChatAnim.NEUTRAL, STRONGHOLD_1)
                    chatPlayer(ChatAnim.CONFUSED, FELL_ON_HEAD)
                    chatNpc(ChatAnim.LAUGH, STRONGHOLD_2)
                    chatPlayer(ChatAnim.QUIZ, HOW_SO)
                    chatNpc(ChatAnim.NEUTRAL, STRONGHOLD_3)
                    chatPlayer(ChatAnim.QUIZ, WHERE_EXPLORER)
                    chatNpc(ChatAnim.NEUTRAL, STRONGHOLD_4)
                    if (player.inventory.count(SECURITY_BOOK_OBJ) == 0L) {
                        giveBook(world, player)
                        objbox(SECURITY_BOOK_OBJ, SECURITY_BOOK)
                    }
                    anythingElse(MORE_SECURITY, SOMETHING_ELSE, NO_THANKS)
                }
            }
        } while (next == Next.SECURITY)
        next
    }

    fun hasWeapon(world: World, player: Player): Boolean =
        player.equipment[WearPos.WEAPON] != null ||
            player.inventory.contents().any { (_, obj) -> world.objTypes[obj.id]?.wearpos1 == WearPos.WEAPON }

    private fun giveBook(world: World, player: Player) {
        world.objTypes[SECURITY_BOOK_OBJ]?.let { world.groundObjs.give(player, it, 1) } ?: logger.warn { "No security book in the cache" }
    }
}

/** The Lumbridge Guide's Talk-to. */
fun registerLumbridgeGuide(world: World) {
    world.handlers.onNpc(LumbridgeGuide.ID, LumbridgeGuide.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(LumbridgeGuide) { talk(world) } }
    }
}
