package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

private val logger = KotlinLogging.logger {}

/** The Sheared Ram's Bartender's lines; the words are in the dialogue file, from the Wiki's Transcript:Bartender (The Sheared Ram), read 2026-10-29. */
object RamBartenderLines : Lines("ram_bartender", "Transcript:Bartender (The Sheared Ram)") {
    val WELCOME = line("welcome", "the Bartender's first line")
    val BEER = line("beer", "option 1, which the player then says")
    val PRICE = line("price", "option 1: the Bartender's answer")
    val CANT_AFFORD = line("cant_afford", "option 1, with fewer than two coins: the player's line")
    val RUMOURS = line("rumours", "option 2, which the player then says")
    val VEOS = line("veos", "option 2: the Bartender's answer")
    val FINE = line("fine", "option 3 (the talk ends with no line from the player)")
}

/** Arthur the Clue Hunter's lines; the words are in the dialogue file, from the Wiki's Transcript:Arthur the Clue Hunter, read 2026-10-29. */
object ArthurLines : Lines("arthur", "Transcript:Arthur the Clue Hunter") {
    val GREETING = line("greeting", "Standard dialogue: Arthur's first line")
    val BEGINNER = line("beginner", "first menu, option 1")
    val BEGINNER_SAID = line("beginner_said", "option 1: what the player says")
    val EYES_LIGHT_UP = line("eyes_light_up", "option 1: the message box after the player's line")
    val BEGINNER_1 = line("beginner_1", "option 1: Arthur's first line, the lowest tier")
    val BEGINNER_2 = line("beginner_2", "option 1: his second line, how they are gained")
    val BEGINNER_3 = line("beginner_3", "option 1: his third line, slaying monsters")
    val BEGINNER_4 = line("beginner_4", "option 1: his fourth line, someone left one")
    val BEGINNER_5 = line("beginner_5", "option 1: his fifth line, geodes, bottles and nests")
    val BEGINNER_6 = line("beginner_6", "option 1: his sixth line, rare from nests")
    val BEGINNER_7 = line("beginner_7", "option 1: his seventh line, the rewards")
    val ADVANCED = line("advanced", "first menu, option 2")
    val ADVANCED_SAID = line("advanced_said", "option 2: what the player says")
    val ADVANCED_1 = line("advanced_1", "option 2: Arthur's first line, the tiers")
    val ADVANCED_2 = line("advanced_2", "option 2: his second line, how they are gained")
    val ADVANCED_3 = line("advanced_3", "option 2: his third line, the chance to get one")
    val ADVANCED_4 = line("advanced_4", "option 2: his fourth line, the one exception")
    val ADVANCED_5 = line("advanced_5", "option 2: his fifth line, master clues")
    val ADVANCED_6 = line("advanced_6", "option 2: his sixth line, better rewards")
    val TYPES = line("types", "first menu, option 3 (the types menu follows with no line from the player)")
    val TOGGLE = line("toggle", "first menu, option 4: toggle vesseled clue scrolls")
    val GOODBYE = line("goodbye", "first menu, option 5, which the player then says")
    val SO_LONG = line("so_long", "option 5: Arthur's answer")

    val ANAGRAMS = line("anagrams", "types menu: Anagrams")
    val ANAGRAMS_1 = line("anagrams_1", "Anagrams: first line")
    val ANAGRAMS_2 = line("anagrams_2", "Anagrams: second line")
    val CHALLENGE = line("challenge", "types menu: Challenge scrolls")
    val CHALLENGE_1 = line("challenge_1", "Challenge scrolls: first line")
    val CHALLENGE_2 = line("challenge_2", "Challenge scrolls: second line")
    val CIPHER = line("cipher", "types menu: Cipher clues")
    val CIPHER_1 = line("cipher_1", "Cipher clues: first line")
    val CIPHER_2 = line("cipher_2", "Cipher clues: second line")
    val CIPHER_3 = line("cipher_3", "Cipher clues: third line, the example")
    val COORDINATE = line("coordinate", "types menu: Coordinate clues")
    val COORDINATE_1 = line("coordinate_1", "Coordinate clues: first line")
    val COORDINATE_2 = line("coordinate_2", "Coordinate clues: second line")
    val CRYPTIC = line("cryptic", "types menu: Cryptic clues")
    val CRYPTIC_1 = line("cryptic_1", "Cryptic clues: first line")
    val CRYPTIC_2 = line("cryptic_2", "Cryptic clues: second line")
    val EMOTE = line("emote", "types menu: Emote clues")
    val EMOTE_1 = line("emote_1", "Emote clues: first line")
    val EMOTE_2 = line("emote_2", "Emote clues: second line")
    val EMOTE_3 = line("emote_3", "Emote clues: third line")
    val EMOTE_4 = line("emote_4", "Emote clues: fourth line")
    val EMOTE_5 = line("emote_5", "Emote clues: fifth line")
    val HOT_COLD = line("hot_cold", "types menu: Hot Cold clues")
    val HOT_COLD_1 = line("hot_cold_1", "Hot Cold clues: first line")
    val HOT_COLD_2 = line("hot_cold_2", "Hot Cold clues: second line")
    val PUZZLE = line("puzzle", "types menu: Puzzle clues")
    val PUZZLE_1 = line("puzzle_1", "Puzzle clues: first line")
    val PUZZLE_2 = line("puzzle_2", "Puzzle clues: second line, puzzle boxes")
    val PUZZLE_3 = line("puzzle_3", "Puzzle clues: third line, light boxes")
    val PUZZLE_4 = line("puzzle_4", "Puzzle clues: fourth line")
    val MAP = line("map", "types menu: Map clues")
    val MAP_1 = line("map_1", "Map clues: first line")
    val MAP_2 = line("map_2", "Map clues: second line")
    val FAIRY_RING = line("fairy_ring", "types menu: Fairy ring clues")
    val FAIRY_RING_1 = line("fairy_ring_1", "Fairy ring clues: first line")
    val FAIRY_RING_2 = line("fairy_ring_2", "Fairy ring clues: second line")
    val MORE = line("more", "the types menu's last option on its first two pages (RSMod's, as the Wiki lists the ten types as one menu)")
    val SOMETHING_ELSE = line("something_else", "the types menu's third page, back to the first menu (RSMod's)")

    val STOP_ASK = line("stop_ask", "toggle, while vesseled clues are on: the player's question")
    val START_ASK = line("start_ask", "toggle, while vesseled clues are off: the player's question")
    val VESSELS_STOP = line("vessels_stop", "toggle, while on: Arthur's answer listing the vessels")
    val VESSELS_START = line("vessels_start", "toggle, while off: Arthur's answer listing the vessels")
    val STOP = line("stop", "toggle, while on: the yes option, which the player then says")
    val START = line("start", "toggle, while off: the yes option, which the player then says")
    val NEVERMIND = line("nevermind", "toggle: the no option (back to the first menu)")
    val STOPPED = line("stopped", "toggle, while on: Arthur's line after yes")
    val STARTED = line("started", "toggle, while off: Arthur's line after yes")
    val ANYTHING_ELSE = line("anything_else", "toggle: Arthur asking if there is anything else, after yes")
}

/**
 * The Sheared Ram, north-east of Lumbridge Castle: its Bartender (7546) and Arthur the Clue Hunter (8665). Veos, who
 * also stands here on live, is only in Lumbridge during X Marks the Spot (the Wiki: he is in Port Sarim after it), and
 * the cache hides him by varbit veos_lumbridge_vis, so with every quest done he isn't spawned.
 *
 * From the Wiki: both conversations, that a beer costs two coins and that Arthur's toggle turns vesseled clue scrolls
 * on and off. Expressions are RSMod's (rev 233 scripts), as are the three pages of Arthur's types menu, as the Wiki
 * lists its ten options in one menu and the chatbox shows at most five. Guessed: the beer goes in after the coins are
 * taken, at the player's feet with no room (the Wiki gives no message), and Arthur's overhead lines are left out
 * until NPCs can say things overhead.
 */
object ShearedRam {
    const val BARTENDER = 7546
    const val ARTHUR = 8665
    const val TALK_TO = 1

    const val COINS = 995
    const val BEER_OBJ = 1917
    const val BEER_PRICE = 2

    /** Varbit vesseled_clues_disabled: 1 while clue scrolls don't come in geodes, bottles and nests. Saved with the varps. */
    const val VARBIT_VESSELS_DISABLED = 9365

    internal suspend fun Dialogue.bartender(give: (Player, Int) -> Unit) = with(RamBartenderLines) {
        chatNpc(ChatAnim.HAPPY, WELCOME)
        when (choice(BEER, RUMOURS, FINE)) {
            1 -> {
                chatPlayer(ChatAnim.HAPPY, BEER)
                chatNpc(ChatAnim.HAPPY, PRICE)
                if (player.inventory.count(COINS) < BEER_PRICE) {
                    chatPlayer(ChatAnim.SAD, CANT_AFFORD)
                } else {
                    takeCoins(player, BEER_PRICE)
                    give(player, BEER_OBJ)
                }
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, RUMOURS)
                chatNpc(ChatAnim.NEUTRAL, VEOS)
            }
            else -> Unit
        }
    }

    internal suspend fun Dialogue.arthur() = with(ArthurLines) {
        chatNpc(ChatAnim.NEUTRAL, GREETING)
        while (true) {
            when (choice(BEGINNER, ADVANCED, TYPES, TOGGLE, GOODBYE)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, BEGINNER_SAID)
                    mesbox(EYES_LIGHT_UP)
                    for (line in listOf(BEGINNER_1, BEGINNER_2, BEGINNER_3, BEGINNER_4, BEGINNER_5)) chatNpc(ChatAnim.HAPPY, line)
                    chatNpc(ChatAnim.SAD, BEGINNER_6)
                    chatNpc(ChatAnim.SAD, BEGINNER_7)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, ADVANCED_SAID)
                    for (line in listOf(ADVANCED_1, ADVANCED_2, ADVANCED_3)) chatNpc(ChatAnim.HAPPY, line)
                    chatNpc(ChatAnim.SAD, ADVANCED_4)
                    chatNpc(ChatAnim.NEUTRAL, ADVANCED_5)
                    chatNpc(ChatAnim.HAPPY, ADVANCED_6)
                }
                3 -> clueTypes()
                4 -> toggleVessels()
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, GOODBYE)
                    chatNpc(ChatAnim.NEUTRAL, SO_LONG)
                    return@with
                }
            }
        }
    }

    /** The types menu's three pages, each type's lines bringing back the page it was on; returns for the first menu. */
    private suspend fun Dialogue.clueTypes() = with(ArthurLines) {
        val pages = listOf(
            listOf(ANAGRAMS, CHALLENGE, CIPHER, COORDINATE, MORE),
            listOf(CRYPTIC, EMOTE, HOT_COLD, PUZZLE, MORE),
            listOf(MAP, FAIRY_RING, MORE, SOMETHING_ELSE),
        )
        val explained = mapOf(
            ANAGRAMS to listOf(ANAGRAMS_1, ANAGRAMS_2),
            CHALLENGE to listOf(CHALLENGE_1, CHALLENGE_2),
            CIPHER to listOf(CIPHER_1, CIPHER_2, CIPHER_3),
            COORDINATE to listOf(COORDINATE_1, COORDINATE_2),
            CRYPTIC to listOf(CRYPTIC_1, CRYPTIC_2),
            EMOTE to listOf(EMOTE_1, EMOTE_2, EMOTE_3, EMOTE_4, EMOTE_5),
            HOT_COLD to listOf(HOT_COLD_1, HOT_COLD_2),
            PUZZLE to listOf(PUZZLE_1, PUZZLE_2, PUZZLE_3, PUZZLE_4),
            MAP to listOf(MAP_1, MAP_2),
            FAIRY_RING to listOf(FAIRY_RING_1, FAIRY_RING_2),
        )
        var page = 0
        while (true) {
            val options = pages[page]
            when (val picked = options[choice(*options.toTypedArray()) - 1]) {
                MORE -> page = (page + 1) % pages.size
                SOMETHING_ELSE -> return@with
                else -> for (line in explained.getValue(picked)) chatNpc(if (line == EMOTE_4) ChatAnim.SCARED else ChatAnim.NEUTRAL, line)
            }
        }
    }

    private suspend fun Dialogue.toggleVessels() = with(ArthurLines) {
        val disabled = vesselsDisabled(player)
        chatPlayer(ChatAnim.QUIZ, if (disabled) START_ASK else STOP_ASK)
        chatNpc(ChatAnim.HAPPY, if (disabled) VESSELS_START else VESSELS_STOP)
        val yes = if (disabled) START else STOP
        if (choice(yes, NEVERMIND) != 1) return@with
        chatPlayer(ChatAnim.HAPPY, yes)
        setVesselsDisabled(player, !disabled)
        chatNpc(ChatAnim.SAD, if (disabled) STARTED else STOPPED)
        chatNpc(ChatAnim.NEUTRAL, ANYTHING_ELSE)
    }

    /** Whether vesseled clue scrolls are off; never, with no varbit in the cache (a test's). */
    fun vesselsDisabled(player: Player): Boolean =
        player.vars.types.varbit(VARBIT_VESSELS_DISABLED) != null && player.vars.getVarbit(VARBIT_VESSELS_DISABLED) == 1

    private fun setVesselsDisabled(player: Player, disabled: Boolean) {
        if (player.vars.types.varbit(VARBIT_VESSELS_DISABLED) == null) return
        player.vars.setVarbit(VARBIT_VESSELS_DISABLED, if (disabled) 1 else 0)
    }

    /** Takes [count] coins from the inventory, from its first coin slots on. */
    private fun takeCoins(player: Player, count: Int) {
        var left = count
        for ((slot, held) in player.inventory.contents()) {
            if (left == 0) break
            if (held.id == COINS) left -= player.inventory.remove(slot, left)
        }
    }
}

/** The Bartender's Talk-to, across his bar, and Arthur's. */
fun registerShearedRam(world: World) {
    val give: (Player, Int) -> Unit = { player, obj ->
        world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } ?: logger.warn { "No item $obj in the cache" }
    }
    world.npcInteractions.reachOverCounter(ShearedRam.BARTENDER)
    world.handlers.onNpc(ShearedRam.BARTENDER, ShearedRam.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(ShearedRam) { bartender(give) } }
    }
    world.handlers.onNpc(ShearedRam.ARTHUR, ShearedRam.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(ShearedRam) { arthur() } }
    }
}
