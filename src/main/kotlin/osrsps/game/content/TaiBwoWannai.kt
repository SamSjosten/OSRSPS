package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Line
import osrsps.game.dialogue.Lines
import osrsps.game.content.TaiBwoWannai.GabootyShop
import osrsps.game.content.TaiBwoWannai.Villager
import osrsps.game.script.ScriptContext

/**
 * What Tai Bwo Wannai's trading-stick villagers share (the Wiki's Transcript pages of Sharimika, Mamma Bufetta, Layleen,
 * Karaday, Safta Doc, Gabooty, Fanellaman, Jagbakoba, Murcaily and Rionasta, read 2026-11-19): the greeting, the menu,
 * the five answers to "anything interesting", the trading-stick lines and the screams at a tribal mask. The answers to
 * "What do you do here?" of the villagers with nothing more to say are here too.
 */
object TaiBwoVillagerLines : Lines(
    "tbw_villager",
    "Transcript:Layleen; Transcript:Sharimika; Transcript:Karaday; Transcript:Jagbakoba; Transcript:Fanellaman; Transcript:Murcaily",
) {
    val GREETING = line("greeting", "Hello there...thanks for helping out Trufitus: the greeting (Layleen's, Sharimika's, Jagbakoba's, Murcaily's first)")
    val WHAT = line("what", "the menu's What do you do here?, which the player then says")
    val INTERESTING = line("interesting", "the menu's Is there anything interesting to do here? (the option's words)")
    val INTERESTING_SAY = line("interesting_say", "what the player says for it: ...to do around here?")
    val WORK = line("work", "the menu's I've been doing some work around the village., which the player then says")
    val OK_THANKS = line("ok_thanks", "the menu's Ok, thanks., which the player then says")
    val EXPLODE = line("explode", "anything interesting, Dialogue 1: if anyone else asks, they'll explode")
    val SUNSHINE = line("sunshine", "Dialogue 2: sit back and enjoy the sunshine")
    val BUSY = line("busy", "Dialogue 3: already busy, go and talk to Murcaily (Gabooty's only answer)")
    val SOME_WORK = line("some_work", "Dialogue 4: some work to be done, Murcaily tends the hardwood grove")
    val VILLAGE_LIFE = line("village_life", "Dialogue 5: village life as normal")
    val CLAIM = line("claim", "the work option with trading sticks to give: Yeah, you've been doing a lot of good work")
    val RECEIVE = line("receive", "Receiving trading sticks: Hey, I've seen you working around the village")
    val GOT_STICKS = line("got_sticks", "the box: You get [amount] trading sticks. You are left with [percentage]% favour.", "sticks", "favour")
    val ALREADY_GIVEN = line("already_given", "the work option without trading sticks to give: already given some")
    val NO_FAVOUR = line("no_favour", "INFERRED words wanted: the work option with no favour left (the transcripts' tmissing)")
    val MASK_BEAST = line("mask_beast", "wearing a tribal mask, Dialogue 1: get away from me you beast")
    val MASK_PLAYER = line("mask_player", "then the player: oh, I'm wearing this big scary mask")
    val MASK_BROODOO = line("mask_broodoo", "Dialogue 2: da evil broodoo beast")
    val MASK_MONSTER = line("mask_monster", "Dialogue 3: It's a monster!")
    val MASK_AH = line("mask_ah", "Dialogue 4: Ahhhhhhhh!! (Fanellaman's Dialogue 2 has one !)")
    val FANELLAMAN_MASK_AH = line("fanellaman_mask_ah", "Fanellaman's mask Dialogue 2: Ahhhhhhhh! (one !)")

    val LAYLEEN_WHAT = line("layleen_what", "Layleen: she walks around, helps her Mum and Dad")
    val KARADAY_WHAT = line("karaday_what", "Karaday: a fisherman, helps his father Safta Doc")
    val JAGBAKOBA_WHAT = line("jagbakoba_what", "Jagbakoba: a fearless jungle hunter")
    val FANELLAMAN_WHAT = line("fanellaman_what", "Fanellaman (with no line from the player): retired fisherman")
}

/** Sharimika's talk about jungle slashing (the Wiki's Transcript:Sharimika, read 2026-11-19). */
object SharimikaLines : Lines("sharimika", "Transcript:Sharimika") {
    val WHAT = line("what", "What do you do here?: not doing a great deal, could tell you about jungle slashing")
    val TELL_ME = line("tell_me", "its menu, option 1, which the player then says")
    val PREVIOUS = line("previous", "its menu, option 2 (back to the first menu)")
    val MACHETE = line("machete", "you'll need a machete, gem edged ones from Gabooty")
    val SAFTA = line("safta", "Safta Doc makes them; scary creatures lurk inside")
    val WHAT_CREATURES = line("what_creatures", "the player: What sort of creatures?")
    val SPIDERS = line("spiders", "spiders, his wife cooks them")
    val GO_ON = line("go_on", "the player: Ok go on!")
    val SNAKES = line("snakes", "snakes are poisonous, a bush snake has a useful hide")
    val SNAKESKIN = line("snakeskin", "tanned it's a snakeskin, still quite small")
    val SEVERAL = line("several", "you may need several skins")
    val SOUNDS_INTERESTING = line("sounds_interesting", "the player: Hmm, sounds interesting!")
    val MOSQUITOES = line("mosquitoes", "giant mosquitoes, fast and agile")
    val PROBOSCIS = line("proboscis", "their proboscis, Timfraku's sons")
    val ANYTHING_ELSE = line("anything_else", "the player: that sounds helpful...anything else?")
    val TRIBESMEN = line("tribesmen", "the tribesmen steal our chickens")
    val POISON_SPEARS = line("poison_spears", "they hide in the bushes, poison on their spears")
    val GOOD_ADVICE = line("good_advice", "the player: Right Ok, good advice... 'poison on spears....'")
    val ANCIENT = line("ancient", "the jungle is very ancient, criminals buried alive")
    val BROODOO = line("broodoo", "the curse of the Broodoo")
    val MAGICAL = line("magical", "they attack with magical abilities")
    val MEANS_OF_DEATH = line("means_of_death", "the means of their death, the green one died from poison")
    val YELLOW_PALE = line("yellow_pale", "a yellow and a pale broodoo")
    val SCARY = line("scary", "the player: Erk! Well that sounds pretty scary!")
    val YES_BWANA = line("yes_bwana", "Sharimika: Yes Bwana...")
    val THANKS_A_LOT = line("thanks_a_lot", "the player: Ok, thanks a lot....")
    val MORE = line("more", "Sharimika: Wait Bwana, there's more!")
    val BLIMEY = line("blimey", "the player: More... Blimey! Ok carry on...")
    val PLANTS = line("plants", "strange plants, the useful part underground")
    val ROCK = line("rock", "an uncommon rock type; that concludes all the help")
    val QUITE_A_LOT = line("quite_a_lot", "the player: that's quite a lot of information")
    val WELCOME = line("welcome", "Sharimika: You're more than welcome bwana!")
}

/** Mamma Bufetta's roasted spiders (the Wiki's Transcript:Mamma Bufetta, read 2026-11-19). */
object MammaBufettaLines : Lines("mamma_bufetta", "Transcript:Mamma Bufetta") {
    val WHAT = line("what", "What do you do here?: cooking up roasted spiders")
    val YUCK = line("yuck", "its menu, option 1, which the player then says")
    val SUIT_YOURSELF = line("suit_yourself", "option 1: suit yourself, they're delicious")
    val MMM = line("mmm", "then: Mmmm, mmm, mmmmmm!")
    val DELICACY = line("delicacy", "option 2, which the player then says")
    val SKEWER = line("skewer", "option 2: a suitable stick, then roast it")
    val WHAT_STICK = line("what_stick", "the player: What sort of stick and where would I get one?")
    val SKEWER_STICK = line("skewer_stick", "a skewer stick from a thatching spar, or an arrow shaft (one page with the next)")
    val SPIDER = line("spider", "then: spider. (the end of that line)")
    val OK_THANKS = line("ok_thanks", "the player: Ok, thanks!")
    val ANOTHER_QUESTION = line("another_question", "option 3 (back to the first menu)")
    val HAVE_TO_GO = line("have_to_go", "option 4, which the player then says")
}

/** Safta Doc's machetes (the Wiki's Transcript:Safta Doc, read 2026-11-19). */
object SaftaDocLines : Lines("safta_doc", "Transcript:Safta Doc") {
    val WHAT = line("what", "What do you do here?: making machetes for Gabooty's shop")
    val DEAL = line("deal", "then: we can do a deal")
    val YES_MACHETE = line("yes_machete", "its menu, option 1, which the player then says")
    val NO_QUESTION = line("no_question", "option 2 (back to the first menu)")
    val NO_GO = line("no_go", "option 3, which the player then says")
    val THREE_TYPES = line("three_types", "option 1: three gem bladed machete types")
    val OPAL = line("opal", "the machete menu's Opal machete.")
    val JADE = line("jade", "the machete menu's Jade machete.")
    val RED_TOPAZ = line("red_topaz", "the machete menu's Red Topaz machete.")
    val TALK = line("talk", "the machete menu's Talk to Safta-doc")
    val OPAL_BOX = line("opal_box", "the box: 3 opals, 1 gout tuber plant and 300 trading sticks")
    val JADE_BOX = line("jade_box", "the box: 3 jades, 1 gout tuber plant and 600 trading sticks")
    val RED_TOPAZ_BOX = line("red_topaz_box", "the box: 3 red topaz, 1 gout tuber plant and 1200 trading sticks")
    val ORDER_OPAL = line("order_opal", "the box's menu: Order the Opal machete.")
    val ORDER_JADE = line("order_jade", "Order the Jade machete.")
    val ORDER_RED_TOPAZ = line("order_red_topaz", "Order the Red Topaz machete.")
    val GO_BACK = line("go_back", "the box's menu: Go back to machete descriptions")
    val NEED_OPAL = line("need_opal", "ordering: 3 opals, 1 gout plant tuber and 300 trading sticks please")
    val NEED_JADE = line("need_jade", "ordering: 3 jades, 600")
    val NEED_RED_TOPAZ = line("need_red_topaz", "ordering: 3 Red Topaz, 1200")
    val NOT_ENOUGH = line("not_enough", "the player without them: I don't have enough trading sticks, sorry.")
    val COME_BACK = line("come_back", "Safta Doc: come back when you have them")
    val THERE_YOU_GO_OPAL = line("there_you_go_opal", "INFERRED words wanted: the opal machete made (the transcript's tmissing)")
    val THERE_YOU_GO_JADE = line("there_you_go_jade", "INFERRED words wanted: the jade machete made (the transcript's tmissing)")
    val THERE_YOU_GO_RED_TOPAZ = line("there_you_go_red_topaz", "the red topaz machete made: There you go Bwana")
    val LIKE_THE_LOOK = line("like_the_look", "Talk to Safta-doc: do you like the look of the machetes?")
    val WHY_GEMS = line("why_gems", "its menu: Why do you need gems?, which the player then says")
    val TOUGHER = line("tougher", "they're tougher than metal")
    val WHY_TUBER = line("why_tuber", "Why do you need a gout plant tuber?, which the player then says")
    val TUBER_1 = line("tuber_1", "one of the harder to find resources")
    val TUBER_2 = line("tuber_2", "you can sometimes find the plant hacking the jungle")
    val WHY_EXPENSIVE = line("why_expensive", "Why is it so expensive?, which the player then says")
    val REASONABLE = line("reasonable", "it's quite reasonably priced, children to feed")
    val SEE_AGAIN = line("see_again", "Let me see the machetes again.")
}

/** Rionasta's parcel service, as far as his talk goes before it opens (the Wiki's Transcript:Rionasta, read 2026-11-19). */
object RionastaLines : Lines("rionasta", "Transcript:Rionasta") {
    val WHAT = line("what", "What do you do here?: the local parcel service")
    val PARCEL = line("parcel", "the player: Parcel service?")
    val BANK = line("bank", "items to individuals' bank accounts, for a fee")
    val HOW_MUCH = line("how_much", "its menu, How much does it cost?, which the player then says")
    val COSTS = line("costs", "10 trading sticks per item")
    val WHAT_STICKS = line("what_sticks", "What are trading sticks and how do I get some?, which the player then says")
    val LOCAL_CURRENCY = line("local_currency", "our local currency, earned by work")
    val MURCAILY = line("murcaily", "have a chat with Murcaily")
}

/** Gabooty's talk (the Wiki's Transcript:Gabooty, read 2026-11-19). */
object GabootyLines : Lines("gabooty", "Transcript:Gabooty") {
    val OPEN_SHOP = line("open_shop", "the menu's Can you open the shop please? (once he's said what he does)")
    val WHAT = line("what", "What do you do here?: runs a local shop")
    val WHAT_STICKS = line("what_sticks", "its menu, What are trading sticks?, which the player then says")
    val CURRENCY = line("currency", "the local currency, odd jobs")
    val SELL_TO_ME = line("sell_to_me", "or sell me something the villagers like")
    val WHAT_SORT = line("what_sort", "What sort of things do the local villagers like?, which the player then says")
    val COCKTAILS = line("cocktails", "Gnome cocktails!")
    val COCKTAILS_2 = line("cocktails_2", "made by the adventurers passing through")
    val HOW_TRY = line("how_try", "the player: How did the villagers ever get to try any gnome cocktails?")
    val PILOT = line("pilot", "the gnome pilot who crashed his glider")
    val PILOT_2 = line("pilot_2", "must love the jungle")
    val RUN_A_SHOP = line("run_a_shop", "You run a shop?, which the player then says")
    val COOPERATIVE = line("cooperative", "the Tai Bwo cooperative")
    val GOOD_PRICE = line("good_price", "bring them to me, a good price")
    val TAKE_A_LOOK = line("take_a_look", "Can I take a look at what you have to sell?, which the player then says")
    val WHICH_SHOP = line("which_shop", "which shop, the Cooperative or Drinks store?")
    val THE_COOPERATIVE = line("the_cooperative", "its menu: The Cooperative")
    val THE_DRINKS = line("the_drinks", "The Drinks Store")
    val NONE_THANKS = line("none_thanks", "None thanks..., which the player then says")
    val ONE_SORT = line("one_sort", "Why do you only take one sort of Gnome cocktail?, which the player then says")
    val GOOD_DEAL = line("good_deal", "a good deal with the glider's pilot")
    val GOOD_DEAL_2 = line("good_deal_2", "adventurers passing through this way.")
    val DIFFERENCE = line("difference", "the player: Why, what's the difference?")
    val FRESHER = line("fresher", "made fresher, a slight twist")
    val ANOTHER_QUESTION = line("another_question", "I want to ask another question., which the player then says")
    val SURE = line("sure", "Sure, what do you want to ask?")
    val OK_THANKS = line("ok_thanks", "the drinks talk's Ok, thanks (no full stop)")
}

/** Murcaily's talk (the Wiki's Transcript:Murcaily, read 2026-11-19). */
object MurcailyLines : Lines("murcaily", "Transcript:Murcaily") {
    val HACKED = line("hacked", "first time after hacking jungle, the fence not repaired: go and fix the fence")
    val REPAIRED = line("repaired", "first time after repairing the fence: let me give you something")
    val WHAT = line("what", "What do you do here?: tends the hardwood grove")
    val WHAT_2 = line("what_2", "then: a simple villager")
    val HANDYMAN = line("handyman", "anything interesting, not hacked yet: handymanship about the village")
    val MACHETE = line("machete", "then: good with da machete, thatching spars")
    val COVERED = line("covered", "anything interesting, hacked: we've got most things covered")
    val ASK_JUNGLE = line("ask_jungle", "its menu: Ask about cutting back the jungle.")
    val ANYTHING_FOR_IT = line("anything_for_it", "Do I get anything for doing this?, which the player then says")
    val WOODCUTTING = line("woodcutting", "experience in woodcutting, trading sticks")
    val TRADING_STICKS = line("trading_sticks", "the player: Trading sticks?")
    val CURRENCY = line("currency", "our form of local currency, a maximum limit")
    val HUNDRED = line("hundred", "at one hundred percent you cannot gain any more favour")
    val WHO_STICKS = line("who_sticks", "Who do I get trading sticks from?, which the player then says")
    val TEN_VILLAGERS = line("ten_villagers", "a good ten villagers, who do you want to ask about?")
    val SHARIMIKA = line("sharimika", "the villager menu: Sharimika.")
    val ABOUT_SHARIMIKA = line("about_sharimika", "married to Mamma Bufetta")
    val MAMMA_BUFETTA = line("mamma_bufetta", "Mamma Bufetta.")
    val ABOUT_MAMMA_BUFETTA = line("about_mamma_bufetta", "lives facing the stone statue")
    val LAYLEEN = line("layleen", "Layleen.")
    val ABOUT_LAYLEEN = line("about_layleen", "a young girl")
    val KARADAY = line("karaday", "Karaday.")
    val ABOUT_KARADAY = line("about_karaday", "a fisherman like his father")
    val MENU_2 = line("menu_2", "-- Villager Menu 2 --")
    val SAFTA_DOC = line("safta_doc", "Safta Doc.")
    val ABOUT_SAFTA_DOC = line("about_safta_doc", "just about retired, near the anvil")
    val ABOUT_SAFTA_DOC_2 = line("about_safta_doc_2", "slightly north of the statue")
    val GABOOTY = line("gabooty", "Gabooty.")
    val ABOUT_GABOOTY = line("about_gabooty", "a bit of a mystery")
    val FANELLAMAN = line("fanellaman", "Fanellaman.")
    val ABOUT_FANELLAMAN = line("about_fanellaman", "retired, walks the western beach")
    val JAGBAKOBA = line("jagbakoba", "Jagbakoba.")
    val ABOUT_JAGBAKOBA = line("about_jagbakoba", "a wild hunter near the fires")
    val MENU_3 = line("menu_3", "-- Villager Menu 3 --")
    val RIONASTA = line("rionasta", "Rionasta.")
    val ABOUT_RIONASTA = line("about_rionasta", "an enterprising man")
    val MURCAILY = line("murcaily", "Murcaily.")
    val ABOUT_MURCAILY = line("about_murcaily", "That's me!")
    val BACK = line("back", "* Back to original questions *")
    val ASK_ANOTHER = line("ask_another", "* Ask another question *")
    val MENU_1 = line("menu_1", "-- Villager Menu 1 --")
    val WHERE_MACHETE = line("where_machete", "Where can I get a machete from?, which the player then says")
    val JIMINUA = line("jiminua", "Jiminua's store, Gabooty, Safta Doc")
    val WHERE_JUNGLE = line("where_jungle", "Where is this jungle?, which the player then says")
    val SOUTH = line("south", "the southern end of Tai Bwo Wannai")
    val BEWARE = line("beware", "those bushes can conceal dangers or trophies")
    val WHAT_MEAN = line("what_mean", "the player: What do you mean by that?")
    val VAGUE = line("vague", "something you wish you hadn't, chat to Sharimika")
    val WHERE_FENCE = line("where_fence", "Where is the fence that needs to be repaired?, which the player then says")
    val TO_THE_SOUTH = line("to_the_south", "to the South, sections are rotten")
    val SPARS = line("spars", "thatching spars and a machete")
}

/** Jiminua's words (the Wiki's Transcript:Jiminua, read 2026-11-19). */
object JiminuaLines : Lines("jiminua", "Transcript:Jiminua") {
    val WELCOME = line("welcome", "Welcome to the Jungle Store")
    val YES = line("yes", "option 1 (the option's words, no question mark)")
    val YES_SAY = line("yes_say", "option 1: what the player says (with a question mark)")
    val NO = line("no", "option 2, which the player then says")
}

/**
 * Tai Bwo Wannai, Karamja step K4.2 (OSRS Wiki: Tai Bwo Wannai, Tai Bwo Wannai Cleanup, Trading sticks, Hardwood Grove,
 * Jiminua's Jungle Store., Gabooty's Tai Bwo Wannai Cooperative., Gabooty's Tai Bwo Wannai Drinky Store. and the
 * villagers' pages and transcripts, read 2026-11-19), open as after Jungle Potion, as every quest is here:
 *
 * - **The ten trading-stick villagers** are the cache's multi-NPCs (tbwcu_*_multinpc), each shown by its own varbit
 *   (chat_sharimika 892 to chat_rion 901): 0 hides them (before Jungle Potion), 1 shows the villager with trading sticks
 *   in their off-hand, 2 to 7 without. At login every 0 becomes 1. Giving sticks sets [COOLDOWN_START]; every
 *   [COOLDOWN_TICKS] ticks each logged-in player's villagers come down one, so a villager has sticks again after 1,500
 *   to 1,800 ticks (Mod Ash, on the Cleanup page: six steps, one about every three minutes).
 * - **Favour** is varbit favour_percentage (907), in tenths of a percent up to [MAX_FAVOUR] (the Wiki's 0.1% steps); it
 *   comes from the Cleanup ([TaiBwoCleanup]). A villager gives sticks for it once the player has repaired a fence and
 *   had their first from Murcaily (Murcaily himself needs only the fence). Guessed: each claim takes up to
 *   [CLAIM_TENTHS] of favour plus [CLOTHING_TENTHS] for each piece of villager clothing worn, and gives a trading
 *   stick for each tenth taken (the Wiki: "if you are given 100 sticks your favour will be reduced by around 10%");
 *   the box's favour shown as a whole percent.
 * - **Talk**: each villager's transcript. Talk-to while they have sticks for the player opens with the Receiving
 *   trading sticks lines; otherwise the menu (with its work option once the player can claim). A tribal mask worn
 *   gets one of the four screams. Gabooty's Trade-Co-op (op 3) opens the Cooperative; his Trade-Drinks (op 4) the
 *   Drinky Store, which only buys player-made gnome cocktails. Jiminua's Trade opens his general store.
 * - **The Hardwood Grove doors** (9038, 9039): from outside, Open or Quick-Pay(100) takes 100 trading sticks and lets
 *   the player in, once they've had sticks from Murcaily (the Wiki: "Do any part of the minigame"); from inside, out
 *   for free. Guessed: Open from outside paying as Quick-Pay does, and the messages (held keys).
 *
 * Left out, for step K4.2b: the Cleanup's events (monsters, gout tubers, gem rocks), Rionasta's parcel service (his
 * talk stops before it), Aisles's banknotes, Banisoch's door, Mamma Bufetta's skewer sticks, the favour overlay,
 * Timfraku's, Trufitus's and Tai Bwo Wannai Trio's talk (quests), the grove's refusal to let unhelped players chop and
 * its warning on leaving without logs. Safta Doc's machetes need gout tubers, which come with the events.
 */
object TaiBwoWannai {
    const val TALK_TO = 1
    const val TRADE_DRINKS = 4
    const val JIMINUA = 2893
    const val TRADING_STICKS = 6306
    const val GOUT_TUBER = 6311

    const val FAVOUR_VARBIT = 907
    const val MAX_FAVOUR = 1000

    const val HAS_STICKS = 1
    const val COOLDOWN_START = 7
    const val COOLDOWN_TICKS = 300

    /** Guessed: favour taken by a claim, in tenths of a percent, and how much more each piece of villager clothing takes. */
    const val CLAIM_TENTHS = 100
    const val CLOTHING_TENTHS = 20

    const val DRINKY_STORE = "gabootys_tai_bwo_wannai_drinky_store"

    val TRIBAL_MASKS = setOf(6335, 6337, 6339)

    /** The four outfits' tops, robes, hats, armbands and sandals (tbw_villager_*: cream, blue, yellow and purple). */
    val VILLAGER_CLOTHES: Set<Int> = (6341..6379 step 2).toSet()

    enum class Villager(val multi: Int, val withSticks: Int, val withoutSticks: Int, val varbit: Int, val greets: Boolean) {
        SHARIMIKA(6548, 6414, 6415, 892, true),
        MAMMA_BUFETTA(6549, 6416, 6417, 893, false),
        LAYLEEN(6550, 6418, 6419, 894, true),
        KARADAY(6551, 6420, 6421, 895, false),
        SAFTA_DOC(6556, 6422, 6423, 896, false),
        GABOOTY(6557, 6424, 6425, 897, false),
        FANELLAMAN(6558, 6426, 6427, 898, false),
        JAGBAKOBA(6559, 6428, 6429, 899, true),
        MURCAILY(6577, 6430, 6431, 900, false),
        RIONASTA(6578, 6432, 6433, 901, false),
    }

    /** The bits of [CustomVars.tbwCleanupVarp]. */
    object Progress {
        const val MET_MURCAILY = 1
        const val HACKED = 2
        const val TOLD_FENCE = 4
        const val REPAIRED = 8
        const val MURCAILY_PAID = 16
        const val ASKED_GABOOTY = 32
    }

    private fun progressVarp(player: Player): Int? = player.vars.types.namedVarps[CustomVars.TBW_CLEANUP]

    fun has(player: Player, bit: Int): Boolean = progressVarp(player)?.let { player.vars[it] and bit != 0 } ?: false

    fun mark(player: Player, bit: Int) {
        val varp = progressVarp(player) ?: return
        player.vars[varp] = player.vars[varp] or bit
    }

    private fun varbit(player: Player, id: Int): Int = if (player.vars.types.varbit(id) != null) player.vars.getVarbit(id) else 0

    private fun setVarbit(player: Player, id: Int, value: Int) {
        if (player.vars.types.varbit(id) != null) player.vars.setVarbit(id, value)
    }

    fun favour(player: Player): Int = varbit(player, FAVOUR_VARBIT)

    /** Adds [tenths] of a percent of favour, to [MAX_FAVOUR] at most. */
    fun addFavour(player: Player, tenths: Int) = setVarbit(player, FAVOUR_VARBIT, (favour(player) + tenths).coerceIn(0, MAX_FAVOUR))

    /** The villager's state for [player]: 0 hidden, [HAS_STICKS], or 2 to [COOLDOWN_START] while their sticks come back. */
    fun state(player: Player, villager: Villager): Int = varbit(player, villager.varbit)

    internal fun setState(player: Player, villager: Villager, value: Int) = setVarbit(player, villager.varbit, value)

    /** Whether [villager] will give [player] trading sticks at all: after a fence repaired, and Murcaily's first. */
    fun canClaim(player: Player, villager: Villager): Boolean =
        has(player, Progress.REPAIRED) && (villager == Villager.MURCAILY || has(player, Progress.MURCAILY_PAID))

    /** Whether [villager] has trading sticks for [player] now. */
    fun hasSticks(player: Player, villager: Villager): Boolean =
        canClaim(player, villager) && state(player, villager) == HAS_STICKS && favour(player) > 0

    /** Pieces of villager clothing worn. */
    fun clothing(player: Player): Int = VILLAGER_CLOTHES.count { player.equipment.count(it) > 0 }

    /** Gives [player] [villager]'s trading sticks for their favour; returns how many (0 when there is no favour). */
    fun claim(world: World, player: Player, villager: Villager): Int {
        val taken = minOf(favour(player), CLAIM_TENTHS + CLOTHING_TENTHS * clothing(player))
        if (taken <= 0) return 0
        setVarbit(player, FAVOUR_VARBIT, favour(player) - taken)
        world.objTypes[TRADING_STICKS]?.let { player.inventory.add(it, taken) }
        setState(player, villager, COOLDOWN_START)
        if (villager == Villager.MURCAILY) mark(player, Progress.MURCAILY_PAID)
        return taken
    }

    /** At login: every villager the player can't see yet (before Jungle Potion) is shown, with sticks. */
    fun login(player: Player) {
        for (villager in Villager.entries) if (state(player, villager) == 0) setState(player, villager, HAS_STICKS)
    }

    /** Every [COOLDOWN_TICKS] ticks: each villager on cool-down comes one step nearer having sticks again. */
    fun coolDown(player: Player) {
        for (villager in Villager.entries) {
            val state = state(player, villager)
            if (state > HAS_STICKS) setState(player, villager, state - 1)
        }
    }

    fun byNpc(id: Int): Villager? = Villager.entries.firstOrNull { id == it.withSticks || id == it.withoutSticks }

    /** Takes [count] trading sticks from the backpack; false, taking none, when fewer are held. */
    fun pay(player: Player, count: Int): Boolean {
        if (player.inventory.count(TRADING_STICKS) < count) return false
        Ned.take(player, TRADING_STICKS, count)
        return true
    }

    // ---- Talk ----

    /** The four screams at a tribal mask, the first three followed by the player's realising (Dialogue 1's). */
    internal suspend fun Dialogue.mask(world: World, villager: Villager) = with(TaiBwoVillagerLines) {
        val screams = if (villager == Villager.FANELLAMAN) listOf(MASK_BEAST, FANELLAMAN_MASK_AH, MASK_MONSTER, MASK_BROODOO) else listOf(MASK_BEAST, MASK_BROODOO, MASK_MONSTER, MASK_AH)
        chatNpc(ChatAnim.SCARED, world.random.pick(screams))
        chatPlayer(ChatAnim.SHOCKED, MASK_PLAYER)
    }

    /** The work option, or the Receiving trading sticks lines when [receiving]: sticks for favour, or why not. */
    internal suspend fun Dialogue.work(world: World, villager: Villager, receiving: Boolean) = with(TaiBwoVillagerLines) {
        if (!receiving) chatPlayer(ChatAnim.NEUTRAL, WORK)
        when {
            state(player, villager) != HAS_STICKS -> chatNpc(ChatAnim.NEUTRAL, ALREADY_GIVEN)
            favour(player) <= 0 -> chatNpc(ChatAnim.NEUTRAL, NO_FAVOUR)
            else -> {
                chatNpc(ChatAnim.HAPPY, if (receiving) RECEIVE else CLAIM)
                val sticks = claim(world, player, villager)
                mesbox(GOT_STICKS, "sticks" to sticks, "favour" to favour(player) / 10)
            }
        }
    }

    private suspend fun Dialogue.interesting(world: World, villager: Villager) = with(TaiBwoVillagerLines) {
        if (villager != Villager.LAYLEEN) chatPlayer(ChatAnim.QUIZ, INTERESTING_SAY)
        val answer = if (villager == Villager.GABOOTY) BUSY else world.random.pick(listOf(EXPLODE, SUNSHINE, BUSY, SOME_WORK, VILLAGE_LIFE))
        chatNpc(ChatAnim.NEUTRAL, answer)
    }

    /** A villager's Talk-to (Murcaily's and Gabooty's have their own): the mask, sticks given straight away, or the menu. */
    internal suspend fun Dialogue.villager(world: World, villager: Villager): Unit = with(TaiBwoVillagerLines) {
        if (TRIBAL_MASKS.any { player.equipment.count(it) > 0 }) return mask(world, villager)
        if (hasSticks(player, villager)) return work(world, villager, receiving = true)
        if (villager.greets) chatNpc(ChatAnim.HAPPY, GREETING)
        while (true) {
            val options = buildList {
                add(WHAT)
                add(INTERESTING)
                if (canClaim(player, villager)) add(WORK)
                add(OK_THANKS)
            }
            when (options[choice(*options.toTypedArray()) - 1]) {
                WHAT -> if (!what(world, villager)) return
                INTERESTING -> return interesting(world, villager)
                WORK -> return work(world, villager, receiving = false)
                else -> return chatPlayer(ChatAnim.NEUTRAL, OK_THANKS)
            }
        }
    }

    /** What do you do here?: each villager's answer; true to show the menu again (the transcript's initial or previous). */
    private suspend fun Dialogue.what(world: World, villager: Villager): Boolean = with(TaiBwoVillagerLines) {
        if (villager != Villager.FANELLAMAN) chatPlayer(ChatAnim.QUIZ, WHAT)
        when (villager) {
            Villager.LAYLEEN -> chatNpc(ChatAnim.NEUTRAL, LAYLEEN_WHAT)
            Villager.KARADAY -> chatNpc(ChatAnim.NEUTRAL, KARADAY_WHAT)
            Villager.JAGBAKOBA -> chatNpc(ChatAnim.ANGRY, JAGBAKOBA_WHAT)
            Villager.FANELLAMAN -> chatNpc(ChatAnim.NEUTRAL, FANELLAMAN_WHAT)
            Villager.SHARIMIKA -> return sharimika()
            Villager.MAMMA_BUFETTA -> return mammaBufetta()
            Villager.SAFTA_DOC -> return saftaDoc(world)
            Villager.RIONASTA -> return rionasta()
            else -> {}
        }
        true
    }

    private suspend fun Dialogue.sharimika(): Boolean = with(SharimikaLines) {
        chatNpc(ChatAnim.NEUTRAL, WHAT)
        if (choice(TELL_ME, PREVIOUS) == 2) return@with true
        chatPlayer(ChatAnim.HAPPY, TELL_ME)
        val talk = listOf(
            false to MACHETE, false to SAFTA, true to WHAT_CREATURES, false to SPIDERS, true to GO_ON, false to SNAKES,
            false to SNAKESKIN, false to SEVERAL, true to SOUNDS_INTERESTING, false to MOSQUITOES, false to PROBOSCIS,
            true to ANYTHING_ELSE, false to TRIBESMEN, false to POISON_SPEARS, true to GOOD_ADVICE, false to ANCIENT,
            false to BROODOO, false to MAGICAL, false to MEANS_OF_DEATH, false to YELLOW_PALE, true to SCARY,
            false to YES_BWANA, true to THANKS_A_LOT, false to MORE, true to BLIMEY, false to PLANTS, false to ROCK,
            true to QUITE_A_LOT, false to WELCOME,
        )
        for ((fromPlayer, line) in talk) if (fromPlayer) chatPlayer(ChatAnim.QUIZ, line) else chatNpc(ChatAnim.NEUTRAL, line)
        false
    }

    private suspend fun Dialogue.mammaBufetta(): Boolean = with(MammaBufettaLines) {
        chatNpc(ChatAnim.HAPPY, WHAT)
        when (choice(YUCK, DELICACY, ANOTHER_QUESTION, HAVE_TO_GO)) {
            1 -> {
                chatPlayer(ChatAnim.DRUNK, YUCK)
                chatNpc(ChatAnim.NEUTRAL, SUIT_YOURSELF)
                chatNpc(ChatAnim.HAPPY, MMM)
            }
            2 -> {
                chatPlayer(ChatAnim.NEUTRAL, DELICACY)
                chatNpc(ChatAnim.HAPPY, SKEWER)
                chatPlayer(ChatAnim.QUIZ, WHAT_STICK)
                chatNpc(ChatAnim.NEUTRAL, SKEWER_STICK)
                chatNpc(ChatAnim.NEUTRAL, SPIDER)
                chatPlayer(ChatAnim.HAPPY, OK_THANKS)
            }
            3 -> return@with true
            else -> chatPlayer(ChatAnim.NEUTRAL, HAVE_TO_GO)
        }
        false
    }

    private suspend fun Dialogue.rionasta(): Boolean = with(RionastaLines) {
        chatNpc(ChatAnim.HAPPY, WHAT)
        chatPlayer(ChatAnim.QUIZ, PARCEL)
        chatNpc(ChatAnim.NEUTRAL, BANK)
        while (true) {
            if (choice(HOW_MUCH, TaiBwoVillagerLines.OK_THANKS) == 2) break
            chatPlayer(ChatAnim.QUIZ, HOW_MUCH)
            chatNpc(ChatAnim.NEUTRAL, COSTS)
            if (choice(WHAT_STICKS, TaiBwoVillagerLines.OK_THANKS) == 2) break
            chatPlayer(ChatAnim.QUIZ, WHAT_STICKS)
            chatNpc(ChatAnim.NEUTRAL, LOCAL_CURRENCY)
            chatNpc(ChatAnim.NEUTRAL, MURCAILY)
        }
        chatPlayer(ChatAnim.NEUTRAL, TaiBwoVillagerLines.OK_THANKS)
        false
    }

    /** Safta Doc's machetes: three gems (cut or uncut), a gout tuber and trading sticks for each. */
    enum class GemMachete(val machete: Int, val cut: Int, val uncut: Int, val sticks: Int) {
        OPAL(6313, 1609, 1625, 300),
        JADE(6315, 1611, 1627, 600),
        RED_TOPAZ(6317, 1613, 1629, 1200),
    }

    const val MACHETE_GEMS = 3

    /** Whether [player] holds what [machete] takes. */
    fun canOrder(player: Player, machete: GemMachete): Boolean =
        player.inventory.count(machete.cut) + player.inventory.count(machete.uncut) >= MACHETE_GEMS &&
            player.inventory.count(GOUT_TUBER) >= 1 && player.inventory.count(TRADING_STICKS) >= machete.sticks

    /** Takes what [machete] costs (cut gems first, guessed) and gives the machete. */
    fun order(world: World, player: Player, machete: GemMachete) {
        val cut = minOf(MACHETE_GEMS.toLong(), player.inventory.count(machete.cut)).toInt()
        if (cut > 0) Ned.take(player, machete.cut, cut)
        if (cut < MACHETE_GEMS) Ned.take(player, machete.uncut, MACHETE_GEMS - cut)
        Ned.take(player, GOUT_TUBER, 1)
        Ned.take(player, TRADING_STICKS, machete.sticks)
        world.objTypes[machete.machete]?.let { player.inventory.add(it, 1) }
    }

    private suspend fun Dialogue.saftaDoc(world: World): Boolean = with(SaftaDocLines) {
        chatNpc(ChatAnim.HAPPY, WHAT)
        chatNpc(ChatAnim.NEUTRAL, DEAL)
        when (choice(YES_MACHETE, NO_QUESTION, NO_GO)) {
            2 -> return@with true
            3 -> {
                chatPlayer(ChatAnim.NEUTRAL, NO_GO)
                return@with false
            }
        }
        chatPlayer(ChatAnim.HAPPY, YES_MACHETE)
        chatNpc(ChatAnim.NEUTRAL, THREE_TYPES)
        while (true) {
            val picked = choice(OPAL, JADE, RED_TOPAZ, TALK)
            if (picked == 4) {
                if (!saftaTalk()) return@with false
                continue
            }
            val machete = GemMachete.entries[picked - 1]
            val (box, order, need, made) = when (machete) {
                GemMachete.OPAL -> listOf(OPAL_BOX, ORDER_OPAL, NEED_OPAL, THERE_YOU_GO_OPAL)
                GemMachete.JADE -> listOf(JADE_BOX, ORDER_JADE, NEED_JADE, THERE_YOU_GO_JADE)
                GemMachete.RED_TOPAZ -> listOf(RED_TOPAZ_BOX, ORDER_RED_TOPAZ, NEED_RED_TOPAZ, THERE_YOU_GO_RED_TOPAZ)
            }
            objbox(machete.machete, box)
            if (choice(order, GO_BACK) == 2) continue
            chatNpc(ChatAnim.NEUTRAL, need)
            if (!canOrder(player, machete)) {
                chatPlayer(ChatAnim.SAD, NOT_ENOUGH)
                chatNpc(ChatAnim.NEUTRAL, COME_BACK)
                return@with false
            }
            order(world, player, machete)
            chatNpc(ChatAnim.HAPPY, made)
            return@with false
        }
        @Suppress("UNREACHABLE_CODE")
        false
    }

    /** Talk to Safta-doc: his questions; true to show the machetes again. */
    private suspend fun Dialogue.saftaTalk(): Boolean = with(SaftaDocLines) {
        chatNpc(ChatAnim.QUIZ, LIKE_THE_LOOK)
        while (true) {
            when (choice(WHY_GEMS, WHY_TUBER, WHY_EXPENSIVE, SEE_AGAIN, TaiBwoVillagerLines.OK_THANKS)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, WHY_GEMS)
                    chatNpc(ChatAnim.NEUTRAL, TOUGHER)
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, WHY_TUBER)
                    chatNpc(ChatAnim.NEUTRAL, TUBER_1)
                    chatNpc(ChatAnim.NEUTRAL, TUBER_2)
                }
                3 -> {
                    chatPlayer(ChatAnim.QUIZ, WHY_EXPENSIVE)
                    chatNpc(ChatAnim.ANGRY, REASONABLE)
                }
                4 -> return@with true
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, TaiBwoVillagerLines.OK_THANKS)
                    return@with false
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        false
    }

    /** What Gabooty's talk ends with: nothing, or a shop to open. */
    enum class GabootyShop { NONE, COOPERATIVE, DRINKS }

    internal suspend fun Dialogue.gabooty(world: World): GabootyShop = with(GabootyLines) {
        val villager = Villager.GABOOTY
        if (TRIBAL_MASKS.any { player.equipment.count(it) > 0 }) {
            mask(world, villager)
            return@with GabootyShop.NONE
        }
        if (hasSticks(player, villager)) {
            work(world, villager, receiving = true)
            return@with GabootyShop.NONE
        }
        while (true) {
            val options = buildList {
                if (has(player, Progress.ASKED_GABOOTY)) add(OPEN_SHOP)
                add(TaiBwoVillagerLines.WHAT)
                add(TaiBwoVillagerLines.INTERESTING)
                if (canClaim(player, villager)) add(TaiBwoVillagerLines.WORK)
                add(TaiBwoVillagerLines.OK_THANKS)
            }
            when (options[choice(*options.toTypedArray()) - 1]) {
                OPEN_SHOP -> return@with GabootyShop.COOPERATIVE
                TaiBwoVillagerLines.WHAT -> {
                    chatPlayer(ChatAnim.QUIZ, TaiBwoVillagerLines.WHAT)
                    chatNpc(ChatAnim.NEUTRAL, WHAT)
                    mark(player, Progress.ASKED_GABOOTY)
                    val shop = gabootyTopics()
                    if (shop != null) return@with shop
                }
                TaiBwoVillagerLines.INTERESTING -> {
                    interesting(world, villager)
                    return@with GabootyShop.NONE
                }
                TaiBwoVillagerLines.WORK -> {
                    work(world, villager, receiving = false)
                    return@with GabootyShop.NONE
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, TaiBwoVillagerLines.OK_THANKS)
                    return@with GabootyShop.NONE
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        GabootyShop.NONE
    }

    /** His topics after What do you do here?: a shop to open, [GabootyShop.NONE] to end, or null for the first menu. */
    private suspend fun Dialogue.gabootyTopics(): GabootyShop? = with(GabootyLines) {
        val ok = TaiBwoVillagerLines.OK_THANKS
        suspend fun pick(vararg options: Line): Line = options[choice(*options) - 1]
        var topic = pick(WHAT_STICKS, RUN_A_SHOP, ANOTHER_QUESTION, ok)
        while (true) {
            topic = when (topic) {
                WHAT_STICKS -> {
                    chatPlayer(ChatAnim.QUIZ, WHAT_STICKS)
                    chatNpc(ChatAnim.NEUTRAL, CURRENCY)
                    chatNpc(ChatAnim.NEUTRAL, SELL_TO_ME)
                    pick(WHAT_SORT, RUN_A_SHOP, ANOTHER_QUESTION, ok)
                }
                WHAT_SORT -> {
                    chatPlayer(ChatAnim.QUIZ, WHAT_SORT)
                    chatNpc(ChatAnim.HAPPY, COCKTAILS)
                    chatNpc(ChatAnim.HAPPY, COCKTAILS_2)
                    chatPlayer(ChatAnim.QUIZ, HOW_TRY)
                    chatNpc(ChatAnim.NEUTRAL, PILOT)
                    chatNpc(ChatAnim.NEUTRAL, PILOT_2)
                    return@with null
                }
                RUN_A_SHOP -> {
                    chatPlayer(ChatAnim.QUIZ, RUN_A_SHOP)
                    chatNpc(ChatAnim.HAPPY, COOPERATIVE)
                    chatNpc(ChatAnim.NEUTRAL, GOOD_PRICE)
                    pick(WHAT_STICKS, RUN_A_SHOP, TAKE_A_LOOK, ONE_SORT, ANOTHER_QUESTION)
                }
                TAKE_A_LOOK -> {
                    chatPlayer(ChatAnim.QUIZ, TAKE_A_LOOK)
                    chatNpc(ChatAnim.HAPPY, WHICH_SHOP)
                    return@with when (choice(THE_COOPERATIVE, THE_DRINKS, NONE_THANKS)) {
                        1 -> GabootyShop.COOPERATIVE
                        2 -> GabootyShop.DRINKS
                        else -> {
                            chatPlayer(ChatAnim.NEUTRAL, NONE_THANKS)
                            GabootyShop.NONE
                        }
                    }
                }
                ONE_SORT -> {
                    chatPlayer(ChatAnim.QUIZ, ONE_SORT)
                    chatNpc(ChatAnim.NEUTRAL, GOOD_DEAL)
                    chatNpc(ChatAnim.NEUTRAL, GOOD_DEAL_2)
                    chatPlayer(ChatAnim.QUIZ, DIFFERENCE)
                    chatNpc(ChatAnim.HAPPY, FRESHER)
                    pick(WHAT_STICKS, RUN_A_SHOP, TAKE_A_LOOK, OK_THANKS)
                }
                ANOTHER_QUESTION -> {
                    chatPlayer(ChatAnim.NEUTRAL, ANOTHER_QUESTION)
                    chatNpc(ChatAnim.HAPPY, SURE)
                    return@with null
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, ok)
                    return@with GabootyShop.NONE
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        null
    }
}

/**
 * Murcaily's Talk-to: the mask; his first greeting (which lets the player hack the jungle); the fence's lines the first
 * time after hacking; his first trading sticks the first time after a repair; sticks given straight away; or his menu.
 */
internal suspend fun Dialogue.murcaily(world: World): Unit = with(MurcailyLines) {
    val murcaily = TaiBwoWannai.Villager.MURCAILY
    val progress = TaiBwoWannai.Progress
    if (TaiBwoWannai.TRIBAL_MASKS.any { player.equipment.count(it) > 0 }) return with(TaiBwoWannai) { mask(world, murcaily) }
    when {
        !TaiBwoWannai.has(player, progress.MET_MURCAILY) -> {
            chatNpc(ChatAnim.HAPPY, TaiBwoVillagerLines.GREETING)
            TaiBwoWannai.mark(player, progress.MET_MURCAILY)
        }
        TaiBwoWannai.has(player, progress.REPAIRED) && !TaiBwoWannai.has(player, progress.MURCAILY_PAID) -> {
            chatNpc(ChatAnim.HAPPY, REPAIRED)
            val sticks = TaiBwoWannai.claim(world, player, murcaily)
            // The first sticks open the other villagers' even with no favour left to give for them. Guessed.
            TaiBwoWannai.mark(player, progress.MURCAILY_PAID)
            mesbox(TaiBwoVillagerLines.GOT_STICKS, "sticks" to sticks, "favour" to TaiBwoWannai.favour(player) / 10)
            return@with
        }
        TaiBwoWannai.has(player, progress.HACKED) && !TaiBwoWannai.has(player, progress.REPAIRED) && !TaiBwoWannai.has(player, progress.TOLD_FENCE) -> {
            chatNpc(ChatAnim.NEUTRAL, HACKED)
            TaiBwoWannai.mark(player, progress.TOLD_FENCE)
        }
        TaiBwoWannai.hasSticks(player, murcaily) -> return with(TaiBwoWannai) { work(world, murcaily, receiving = true) }
    }
    while (true) {
        val options = buildList {
            add(TaiBwoVillagerLines.WHAT)
            add(TaiBwoVillagerLines.INTERESTING)
            if (TaiBwoWannai.has(player, progress.REPAIRED)) add(TaiBwoVillagerLines.WORK)
            add(TaiBwoVillagerLines.OK_THANKS)
        }
        when (options[choice(*options.toTypedArray()) - 1]) {
            TaiBwoVillagerLines.WHAT -> {
                chatPlayer(ChatAnim.QUIZ, TaiBwoVillagerLines.WHAT)
                chatNpc(ChatAnim.NEUTRAL, WHAT)
                chatNpc(ChatAnim.NEUTRAL, WHAT_2)
            }
            TaiBwoVillagerLines.INTERESTING -> {
                chatPlayer(ChatAnim.QUIZ, TaiBwoVillagerLines.INTERESTING_SAY)
                if (!TaiBwoWannai.has(player, progress.HACKED)) {
                    chatNpc(ChatAnim.NEUTRAL, HANDYMAN)
                    chatNpc(ChatAnim.NEUTRAL, MACHETE)
                } else {
                    chatNpc(ChatAnim.NEUTRAL, COVERED)
                    if (choice(ASK_JUNGLE, TaiBwoVillagerLines.OK_THANKS) == 2) return chatPlayer(ChatAnim.NEUTRAL, TaiBwoVillagerLines.OK_THANKS)
                }
                if (!murcailyJungle(world)) return
            }
            TaiBwoVillagerLines.WORK -> return with(TaiBwoWannai) { work(world, murcaily, receiving = false) }
            else -> return chatPlayer(ChatAnim.NEUTRAL, TaiBwoVillagerLines.OK_THANKS)
        }
    }
}

/** His questions about the jungle; true to go back to his first menu. */
private suspend fun Dialogue.murcailyJungle(world: World): Boolean = with(MurcailyLines) {
    while (true) {
        when (choice(ANYTHING_FOR_IT, WHERE_MACHETE, WHERE_JUNGLE, WHERE_FENCE, TaiBwoVillagerLines.OK_THANKS)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, ANYTHING_FOR_IT)
                chatNpc(ChatAnim.HAPPY, WOODCUTTING)
                chatPlayer(ChatAnim.QUIZ, TRADING_STICKS)
                chatNpc(ChatAnim.HAPPY, CURRENCY)
                chatNpc(ChatAnim.NEUTRAL, HUNDRED)
                if (choice(WHO_STICKS) == 1) {
                    chatPlayer(ChatAnim.QUIZ, WHO_STICKS)
                    chatNpc(ChatAnim.NEUTRAL, TEN_VILLAGERS)
                    when (villagerMenus(world)) {
                        VillagerMenuEnd.BACK -> return@with true
                        VillagerMenuEnd.END -> return@with false
                    }
                }
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, WHERE_MACHETE)
                chatNpc(ChatAnim.NEUTRAL, JIMINUA)
            }
            3 -> {
                chatPlayer(ChatAnim.QUIZ, WHERE_JUNGLE)
                chatNpc(ChatAnim.NEUTRAL, SOUTH)
                chatNpc(ChatAnim.NEUTRAL, BEWARE)
                chatPlayer(ChatAnim.CONFUSED, WHAT_MEAN)
                chatNpc(ChatAnim.NEUTRAL, VAGUE)
            }
            4 -> {
                chatPlayer(ChatAnim.QUIZ, WHERE_FENCE)
                chatNpc(ChatAnim.NEUTRAL, TO_THE_SOUTH)
                chatNpc(ChatAnim.NEUTRAL, SPARS)
            }
            else -> {
                chatPlayer(ChatAnim.NEUTRAL, TaiBwoVillagerLines.OK_THANKS)
                return@with false
            }
        }
    }
    @Suppress("UNREACHABLE_CODE")
    false
}

private enum class VillagerMenuEnd { BACK, END }

/** Murcaily's three villager menus: who to ask, a line or two each, back to the same page. */
private suspend fun Dialogue.villagerMenus(world: World): VillagerMenuEnd = with(MurcailyLines) {
    var page = 1
    while (true) {
        when (page) {
            1 -> when (choice(SHARIMIKA, MAMMA_BUFETTA, LAYLEEN, KARADAY, MENU_2)) {
                1 -> chatNpc(ChatAnim.NEUTRAL, ABOUT_SHARIMIKA)
                2 -> chatNpc(ChatAnim.NEUTRAL, ABOUT_MAMMA_BUFETTA)
                3 -> chatNpc(ChatAnim.NEUTRAL, ABOUT_LAYLEEN)
                4 -> chatNpc(ChatAnim.NEUTRAL, ABOUT_KARADAY)
                else -> page = 2
            }
            2 -> when (choice(SAFTA_DOC, GABOOTY, FANELLAMAN, JAGBAKOBA, MENU_3)) {
                1 -> {
                    chatNpc(ChatAnim.NEUTRAL, ABOUT_SAFTA_DOC)
                    chatNpc(ChatAnim.NEUTRAL, ABOUT_SAFTA_DOC_2)
                }
                2 -> chatNpc(ChatAnim.NEUTRAL, ABOUT_GABOOTY)
                3 -> chatNpc(ChatAnim.NEUTRAL, ABOUT_FANELLAMAN)
                4 -> chatNpc(ChatAnim.NEUTRAL, ABOUT_JAGBAKOBA)
                else -> page = 3
            }
            else -> when (choice(RIONASTA, MURCAILY, BACK, ASK_ANOTHER, MENU_1)) {
                1 -> chatNpc(ChatAnim.HAPPY, ABOUT_RIONASTA)
                2 -> chatNpc(ChatAnim.HAPPY, ABOUT_MURCAILY)
                3 -> return@with VillagerMenuEnd.BACK
                4 -> {
                    val murcaily = TaiBwoWannai.Villager.MURCAILY
                    if (TaiBwoWannai.hasSticks(player, murcaily)) {
                        with(TaiBwoWannai) { work(world, murcaily, receiving = true) }
                        return@with VillagerMenuEnd.END
                    }
                    return@with VillagerMenuEnd.BACK
                }
                else -> page = 1
            }
        }
    }
    @Suppress("UNREACHABLE_CODE")
    VillagerMenuEnd.END
}

/** Jiminua's Talk-to; true when the player asks to see his stock. */
internal suspend fun Dialogue.jiminua(): Boolean = with(JiminuaLines) {
    chatNpc(ChatAnim.HAPPY, WELCOME)
    if (choice(YES, NO) == 1) {
        chatPlayer(ChatAnim.QUIZ, YES_SAY)
        return@with true
    }
    chatPlayer(ChatAnim.NEUTRAL, NO)
    false
}

fun registerTaiBwoWannai(world: World) {
    TaiBwoCleanup.register(world)
    with(TaiBwoWannai) {
        world.loginHooks += ::login
        world.tickHooks += { tick ->
            if (tick % COOLDOWN_TICKS == 0L) for (player in world.onlinePlayers) coolDown(player)
        }
        for (villager in Villager.entries) {
            for (npc in listOf(villager.withSticks, villager.withoutSticks)) {
                world.handlers.onNpc(npc, TALK_TO) { op ->
                    when (villager) {
                        Villager.MURCAILY -> world.dialogues.start(this, op.npc) { murcaily(world) }
                        Villager.GABOOTY -> {
                            var shop = GabootyShop.NONE
                            world.dialogues.start(this, op.npc) { shop = gabooty(world) }
                            when (shop) {
                                GabootyShop.COOPERATIVE -> world.shopTypes.ofNpc(npc)?.let { world.shopScreen.open(player, it) }
                                GabootyShop.DRINKS -> world.shopTypes[DRINKY_STORE]?.let { world.shopScreen.open(player, it) }
                                GabootyShop.NONE -> {}
                            }
                        }
                        else -> world.dialogues.start(this, op.npc) { villager(world, villager) }
                    }
                }
            }
        }
        for (npc in listOf(Villager.GABOOTY.withSticks, Villager.GABOOTY.withoutSticks)) {
            world.handlers.onNpc(npc, TRADE_DRINKS) { world.shopTypes[DRINKY_STORE]?.let { world.shopScreen.open(player, it) } }
        }
        world.handlers.onNpc(JIMINUA, TALK_TO) { op ->
            var trade = false
            world.dialogues.start(this, op.npc) { trade = jiminua() }
            if (trade) world.shopTypes.ofNpc(JIMINUA)?.let { world.shopScreen.open(player, it) }
        }
        for (door in HardwoodGrove.DOORS) {
            world.handlers.onLoc(door, HardwoodGrove.OPEN) { with(HardwoodGrove) { passDoor(world) } }
            world.handlers.onLoc(door, HardwoodGrove.QUICK_PAY) { with(HardwoodGrove) { passDoor(world) } }
        }
    }
}

/** The Hardwood Grove's doors' words: none on the Wiki's pages. */
object HardwoodGroveLines : Lines("hardwood_grove", "Hardwood Grove") {
    val PAID = line("paid", "INFERRED words wanted: the message as the doors take 100 trading sticks")
    val NO_STICKS = line("no_sticks", "INFERRED words wanted: the doors from outside with fewer than 100 trading sticks")
    val NOT_HELPED = line("not_helped", "INFERRED words wanted: the doors before the player has had trading sticks from Murcaily")
}

/**
 * The Hardwood Grove's doors (9038 and 9039 on the west edge of x [INSIDE_X], z 3083 and 3084): in for [FEE] trading
 * sticks, once Murcaily has given the player sticks; out for free.
 */
object HardwoodGrove {
    val DOORS = listOf(9038, 9039)
    const val OPEN = 1
    const val QUICK_PAY = 2
    const val FEE = 100
    const val INSIDE_X = 2817

    suspend fun ScriptContext.passDoor(world: World) {
        arriveDelay()
        val z = player.z.coerceIn(3083, 3084)
        if (player.x >= INSIDE_X) {
            world.teleport(player, INSIDE_X - 1, z, player.level)
            return
        }
        when {
            !TaiBwoWannai.has(player, TaiBwoWannai.Progress.MURCAILY_PAID) -> world.message(player, HardwoodGroveLines.NOT_HELPED)
            !TaiBwoWannai.pay(player, FEE) -> world.message(player, HardwoodGroveLines.NO_STICKS)
            else -> {
                world.message(player, HardwoodGroveLines.PAID)
                world.teleport(player, INSIDE_X, z, player.level)
            }
        }
    }
}
