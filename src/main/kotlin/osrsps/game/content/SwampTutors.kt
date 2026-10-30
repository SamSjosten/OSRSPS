package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player
import osrsps.game.Skill
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.skill.Mining

private val logger = KotlinLogging.logger {}

/** The Mining tutor's lines; the words are in the dialogue file, from the Wiki's Transcript:Mining tutor (members' worlds), read 2026-10-30. */
object MiningTutorLines : Lines("mining_tutor", "Transcript:Mining tutor") {
    val BASICS = line("basics", "Mining 1-24: option, which the player then says")
    val TIPS = line("tips", "Mining 25-38: option, which the player then says")
    val ADVANCED = line("advanced", "Mining 39-99: option, which the player then says")
    val QUESTS = line("quests", "every level: the quests option")
    val ROCKS_AND_PICKS = line("rocks_and_picks", "every level: rocks and picks, which the player then says")
    val GOODBYE = line("goodbye", "every level: goodbye, which the player then says")

    val SITE_HINT = line("site_hint", "basics: the mining site icon's box (item 5082)")
    val HAVE_A_PICK = line("have_a_pick", "with no pickaxe: the tutor's line as he hands one over")
    val NO_ROOM = line("no_room", "with no pickaxe and a full inventory: the tutor's line")
    val ALREADY_PICK = line("already_pick", "with a pickaxe: the tutor's line")
    val TELL_ORE = line("tell_ore", "basics: telling the ore from the rock")
    val CLICK_ROCK = line("click_rock", "basics: click on the rock")
    val FULL_INVENTORY = line("full_inventory", "basics: when you have a full inventory")
    val BANK_HINT = line("bank_hint", "basics: the bank icon's box (item 5080)")

    val DIFFERENT_PLACES = line("different_places", "tips: different rocks in different places")
    val AL_KHARID = line("al_kharid", "tips: the Al Kharid mine")
    val SCORPIONS = line("scorpions", "tips: the scorpions")
    val SHOW_AL_KHARID = line("show_al_kharid", "tips: the offer to show the mine on the map")
    val YES_PLEASE = line("yes_please", "map offers: option, which the player then says")
    val NO_THANKS = line("no_thanks", "map offers: option, which the player then says")

    val GUILD = line("guild", "advanced: the Mining Guild")
    val COAL_TRUCKS = line("coal_trucks", "advanced, members' worlds: the coal mine west of Seers'")
    val THANK_YOU = line("thank_you", "advanced: the player's line")
    val SHOW_EITHER = line("show_either", "advanced, members' worlds: the offer to show either place on the map")
    val SHOW_GUILD = line("show_guild", "advanced map offer: option, which the player then says")
    val SHOW_COAL = line("show_coal", "advanced map offer: option, which the player then says")

    val DORIC = line("doric", "quests: Doric")
    val DIG_SITE = line("dig_site", "quests, members: the Dig Site")

    val HERE_GOES = line("here_goes", "rocks and picks: the tutor's first line")
    val ROCKS_AND_ORES = line("rocks_and_ores", "rocks and picks menu: option")
    val PICKAXES = line("pickaxes", "rocks and picks menu: option")
    val TEACH_MINING = line("teach_mining", "rocks, picks and after-topic menus: back to the first menu")
    val TEACH_ROCKS = line("teach_rocks", "after-topic menu: back to the rocks menu")
    val MORE = line("more", "rocks menu: the second page")
    val BACK = line("back", "rocks menu, second page: back to the first page")

    val CLAY_ESSENCE = line("clay_essence", "rocks menu: option")
    val CLAY_HINT = line("clay_hint", "clay and rune essence: the box with clay")
    val ESSENCE_HINT = line("essence_hint", "clay and rune essence: the box with rune essence")
    val CLAY_USES = line("clay_uses", "clay and rune essence: the tutor's line")
    val COPPER_TIN = line("copper_tin", "rocks menu: option")
    val COPPER_HINT = line("copper_hint", "copper and tin: the box with copper ore")
    val TIN_HINT = line("tin_hint", "copper and tin: the box with tin ore")
    val IRON_COAL = line("iron_coal", "rocks menu: option")
    val IRON_HINT = line("iron_hint", "iron and coal: the box with iron ore")
    val FURNACE_HINT = line("furnace_hint", "iron and coal: the furnace icon's box (item 5084)")
    val COAL_HINT = line("coal_hint", "iron and coal: the box with coal")
    val SILVER_GOLD = line("silver_gold", "rocks menu, second page: option")
    val SILVER_HINT = line("silver_hint", "silver and gold: the box with silver ore")
    val GOLD_HINT = line("gold_hint", "silver and gold: the box with gold ore")
    val MITHRIL_RUNITE = line("mithril_runite", "rocks menu, second page, and the pickaxes menu: option")
    val MITHRIL_HINT = line("mithril_hint", "mithril, adamantite and runite rocks: the box with mithril and adamantite ore")
    val RUNITE_HINT = line("runite_hint", "mithril, adamantite and runite rocks: the box with runite ore")
    val BRONZE_STEEL = line("bronze_steel", "pickaxes menu: option")
    val BRONZE_PICK_HINT = line("bronze_pick_hint", "bronze, iron and steel: the box with a bronze pickaxe")
    val IRON_STEEL_PICK_HINT = line("iron_steel_pick_hint", "bronze, iron and steel: the box with iron and steel pickaxes")
    val MITHRIL_PICKS_HINT = line("mithril_picks_hint", "mithril, adamantite and rune pickaxes: the box with them")
}

/** The Fishing tutor's lines; the words are in the dialogue file, from the Wiki's Transcript:Fishing tutor (members' worlds), read 2026-10-30. */
object FishingTutorLines : Lines("fishing_tutor", "Transcript:Fishing tutor") {
    val BASICS = line("basics", "Fishing 1-28: option, which the player then says")
    val TIPS = line("tips", "Fishing 29-38: option, which the player then says")
    val ADVANCED = line("advanced", "Fishing 39-99: option, which the player then says")
    val FISH = line("fish", "every level: different fish, which the player then says")
    val WHERE = line("where", "every level: where and what, which the player then says")
    val GOODBYE = line("goodbye", "every level: goodbye, which the player then says")

    val LOST_NET = line("lost_net", "with no net and a free slot: the tutor's line as he hands one over")
    val HAVE_NET = line("have_net", "with a net: the tutor's line")
    val SPOT_HINT = line("spot_hint", "basics: the fishing spot icon's box (item 5088)")
    val HOW_TO = line("how_to", "basics: how to fish")
    val IS_THAT_IT = line("is_that_it", "basics: the player's question")
    val PROGRESS = line("progress", "basics: there's far more as you progress")
    val FULL_INVENTORY = line("full_inventory", "basics: when you have a full inventory")

    val CHOOSE = line("choose", "tips: choose carefully")
    val HANG_ON = line("hang_on", "tips: hang on to your fish")
    val TRAWLER = line("trawler", "tips, members' worlds: the trawler")
    val MURPHY = line("murphy", "tips, members' worlds: Murphy at Port Khazard")
    val SHOW_MAP = line("show_map", "tips: the offer to show the map")
    val YES_PLEASE = line("yes_please", "map offer: option")
    val NO_THANKS = line("no_thanks", "map offer: option")

    val TUNA_SWORDFISH = line("tuna_swordfish", "advanced: tuna and swordfish")
    val SHARK = line("shark", "advanced, members' worlds: shark, manta ray and sea turtle")
    val POTION = line("potion", "advanced, members' worlds: the fishing potion")
    val NOW_QUESTS = line("now_quests", "advanced, members' worlds: now, quests")
    val NO_QUESTS = line("no_quests", "advanced, with Sea Slug and Tai Bwo Wannai Trio started: nothing to handle")

    val SMALL_NET = line("small_net", "fish menu: option")
    val SMALL_NET_HINT = line("small_net_hint", "small net fish: the box with a small fishing net")
    val SMALL_NET_CATCH = line("small_net_catch", "small net fish: the box with shrimps and anchovies")
    val BIG_NET = line("big_net", "fish menu, members' worlds: option")
    val BIG_NET_HINT = line("big_net_hint", "big net fish: the box with a big fishing net")
    val BIG_NET_CATCH = line("big_net_catch", "big net fish: the box with mackerel and cod")
    val RICH_REWARDS = line("rich_rewards", "big net fish: the tutor's line")
    val ROD_FLY = line("rod_fly", "fish menu: option")
    val ROD_HINT = line("rod_hint", "rod and fly fishing: the box with a fishing rod")
    val ROD_CATCH = line("rod_catch", "rod and fly fishing: the box with sardines and pike")
    val FLY_HINT = line("fly_hint", "rod and fly fishing: the box with a fly fishing rod")
    val FLY_CATCH = line("fly_catch", "rod and fly fishing: the box with trout and salmon")
    val LOBSTER = line("lobster", "fish menu, Fishing 29-38: option")
    val LOBSTER_HINT = line("lobster_hint", "lobster pot: the box with a lobster pot")
    val LOBSTER_CATCH = line("lobster_catch", "lobster pot: the box with a lobster")
    val IS_THAT_ALL = line("is_that_all", "lobster pot: the player's question")
    val HARPOON = line("harpoon", "fish menu, Fishing 39-99: option")
    val HARPOON_HINT = line("harpoon_hint", "harpoon fish: the box with a harpoon")
    val HARPOON_CATCH = line("harpoon_catch", "harpoon fish: the box with tuna and swordfish")
    val AND_SHARK = line("and_shark", "harpoon fish, members' worlds: the box with a shark")
    val FISHING_GUILD = line("fishing_guild", "harpoon fish, members' worlds: the Fishing Guild")
    val TELL_ME_ABOUT = line("tell_me_about", "fish menu: back to the first menu")

    val YOUNG = line("young", "where and what, Fishing 1-9: the tutor's line")
    val WHAT_PARROT = line("what_parrot", "where and what, 1-9: the player's question")
    val PERCY = line("percy", "where and what, 1-9: the tutor's answer")
    val OOKAY = line("ookay", "where and what, 1-9: the player's line")
    val ARRR = line("arrr", "where and what, 1-9: the tutor's last line")
    val HERRING = line("herring", "where and what, members' worlds, Fishing 10-15")
    val MACKEREL = line("mackerel", "where and what, members' worlds, Fishing 16-22")
    val COD = line("cod", "where and what, members' worlds, Fishing 23-27")
    val SLIMY_EEL = line("slimy_eel", "where and what, members' worlds, Fishing 28-37")
    val CAVE_EEL = line("cave_eel", "where and what, members' worlds, Fishing 38-45")
    val BASS = line("bass", "where and what, members' worlds, Fishing 46-49")
    val CATHERBY = line("catherby", "where and what, members' worlds, Fishing 50-99: first line")
    val MANTA = line("manta", "where and what, members' worlds, Fishing 50-99: second line")
}

/**
 * The Mining tutor (3222) by the swamp's mine and the Fishing tutor (3221) by its fishing spots. From the Wiki: each
 * conversation as on a members' world with every quest done (so the Fishing tutor names no quest and the Mining tutor
 * adds the Dig Site), the level ranges, the menus and where each option leads, the hint boxes' icons and the bronze
 * pickaxe and small fishing net handed over.
 *
 * Guessed: every chathead expression; which item each unlabelled box shows; that a banked pickaxe or net counts as owned
 * (the Wiki says equipped or carried; the bank is counted as for the Woodsman tutor's axe, so the free one can't be sold
 * for coins from nothing); that with no net and no free slot the Fishing tutor says nothing about the net; that his fish
 * menu, which can't show six options, has one slot that is the lobster pot at Fishing 29-38 and the harpoon from 39;
 * and that the map offers lead back to the first menu without opening the world map, which isn't built yet.
 */
object SwampTutors {
    const val FISHING_TUTOR = 3221
    const val MINING_TUTOR = 3222
    const val TALK_TO = 1

    const val BANK_ICON = 5080
    const val MINING_SITE_ICON = 5082
    const val FURNACE_ICON = 5084
    const val FISHING_SPOT_ICON = 5088

    const val CLAY = 434
    const val RUNE_ESSENCE = 1436
    const val COPPER_ORE = 436
    const val TIN_ORE = 438
    const val IRON_ORE = 440
    const val COAL = 453
    const val SILVER_ORE = 442
    const val GOLD_ORE = 444
    const val MITHRIL_ORE = 447
    const val ADAMANTITE_ORE = 449
    const val RUNITE_ORE = 451
    const val BRONZE_PICKAXE = 1265
    const val IRON_PICKAXE = 1267
    const val STEEL_PICKAXE = 1269
    const val MITHRIL_PICKAXE = 1273
    const val ADAMANT_PICKAXE = 1271
    const val RUNE_PICKAXE = 1275

    const val SMALL_FISHING_NET = 303
    const val BIG_FISHING_NET = 305
    const val FISHING_ROD = 307
    const val FLY_FISHING_ROD = 309
    const val HARPOON_OBJ = 311
    const val LOBSTER_POT = 301
    const val RAW_SHRIMPS = 317
    const val RAW_ANCHOVIES = 321
    const val RAW_SARDINE = 327
    const val RAW_PIKE = 349
    const val RAW_TROUT = 335
    const val RAW_SALMON = 331
    const val RAW_MACKEREL = 353
    const val RAW_COD = 341
    const val RAW_LOBSTER = 377
    const val RAW_TUNA = 359
    const val RAW_SWORDFISH = 371
    const val RAW_SHARK = 383

    /** Every pickaxe the Mining skill takes. */
    val PICKAXE_OBJS = Mining.PICKAXES.map { it.tool.obj }

    internal suspend fun Dialogue.miningTutor(give: (Player, Int) -> Unit) = with(MiningTutorLines) {
        val level = player.skills.baseLevel(Skill.MINING)
        val first = when {
            level <= 24 -> BASICS
            level <= 38 -> TIPS
            else -> ADVANCED
        }
        while (true) {
            when (choice(first, QUESTS, ROCKS_AND_PICKS, GOODBYE)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, first)
                    when (first) {
                        BASICS -> {
                            objbox(MINING_SITE_ICON, SITE_HINT)
                            pickIfNeeded(give)
                            chatNpc(ChatAnim.NEUTRAL, TELL_ORE)
                            chatNpc(ChatAnim.NEUTRAL, CLICK_ROCK)
                            chatNpc(ChatAnim.HAPPY, FULL_INVENTORY)
                            objbox(BANK_ICON, BANK_HINT)
                        }
                        TIPS -> {
                            pickIfNeeded(give)
                            chatNpc(ChatAnim.NEUTRAL, DIFFERENT_PLACES)
                            chatNpc(ChatAnim.NEUTRAL, AL_KHARID)
                            chatNpc(ChatAnim.NEUTRAL, SCORPIONS)
                            chatNpc(ChatAnim.QUIZ, SHOW_AL_KHARID)
                            val picked = if (choice(YES_PLEASE, NO_THANKS) == 1) YES_PLEASE else NO_THANKS
                            chatPlayer(ChatAnim.NEUTRAL, picked)
                        }
                        else -> {
                            pickIfNeeded(give)
                            chatNpc(ChatAnim.HAPPY, GUILD)
                            chatNpc(ChatAnim.NEUTRAL, COAL_TRUCKS)
                            chatPlayer(ChatAnim.HAPPY, THANK_YOU)
                            chatNpc(ChatAnim.QUIZ, SHOW_EITHER)
                            val options = listOf(SHOW_GUILD, SHOW_COAL, NO_THANKS)
                            chatPlayer(ChatAnim.NEUTRAL, options[choice(*options.toTypedArray()) - 1])
                        }
                    }
                }
                2 -> {
                    chatNpc(ChatAnim.HAPPY, DORIC)
                    chatNpc(ChatAnim.NEUTRAL, DIG_SITE)
                }
                3 -> {
                    chatPlayer(ChatAnim.QUIZ, ROCKS_AND_PICKS)
                    chatNpc(ChatAnim.HAPPY, HERE_GOES)
                    rocksAndPicks()
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, GOODBYE)
                    return@with
                }
            }
        }
    }

    /** The rocks and picks menus, until Teach me about mining (back to the first menu). */
    private suspend fun Dialogue.rocksAndPicks() = with(MiningTutorLines) {
        when (choice(ROCKS_AND_ORES, PICKAXES, TEACH_MINING)) {
            1 -> rocks()
            2 -> pickaxes()
        }
    }

    /** The rocks menu's two pages; each topic ends on a menu back to the rocks or the first menu. */
    private suspend fun Dialogue.rocks() = with(MiningTutorLines) {
        var secondPage = false
        while (true) {
            if (!secondPage) {
                when (choice(CLAY_ESSENCE, COPPER_TIN, IRON_COAL, TEACH_MINING, MORE)) {
                    1 -> {
                        objbox(CLAY, CLAY_HINT)
                        objbox(RUNE_ESSENCE, ESSENCE_HINT)
                        chatNpc(ChatAnim.NEUTRAL, CLAY_USES)
                    }
                    2 -> {
                        objbox(COPPER_ORE, COPPER_HINT)
                        objbox(TIN_ORE, TIN_HINT)
                    }
                    3 -> {
                        objbox(IRON_ORE, IRON_HINT)
                        objbox(FURNACE_ICON, FURNACE_HINT)
                        objbox(COAL, COAL_HINT)
                    }
                    4 -> return@with
                    else -> {
                        secondPage = true
                        continue
                    }
                }
            } else {
                when (choice(SILVER_GOLD, MITHRIL_RUNITE, TEACH_MINING, BACK)) {
                    1 -> {
                        objbox(SILVER_ORE, SILVER_HINT)
                        objbox(GOLD_ORE, GOLD_HINT)
                    }
                    2 -> {
                        doubleobjbox(MITHRIL_ORE, ADAMANTITE_ORE, text(MITHRIL_HINT))
                        objbox(RUNITE_ORE, RUNITE_HINT)
                    }
                    3 -> return@with
                    else -> {
                        secondPage = false
                        continue
                    }
                }
            }
            if (choice(TEACH_MINING, TEACH_ROCKS) == 1) return@with
            secondPage = false
        }
    }

    /** The pickaxes menu, until Teach me about mining. */
    private suspend fun Dialogue.pickaxes() = with(MiningTutorLines) {
        while (true) {
            when (choice(BRONZE_STEEL, MITHRIL_RUNITE, TEACH_MINING)) {
                1 -> {
                    objbox(BRONZE_PICKAXE, BRONZE_PICK_HINT)
                    doubleobjbox(IRON_PICKAXE, STEEL_PICKAXE, text(IRON_STEEL_PICK_HINT))
                }
                2 -> doubleobjbox(MITHRIL_PICKAXE, RUNE_PICKAXE, text(MITHRIL_PICKS_HINT))
                else -> return@with
            }
        }
    }

    /** With no pickaxe of their own, a bronze one, or a line saying there's no room for it. */
    private suspend fun Dialogue.pickIfNeeded(give: (Player, Int) -> Unit) = with(MiningTutorLines) {
        when {
            PICKAXE_OBJS.any { owns(it) } -> chatNpc(ChatAnim.NEUTRAL, ALREADY_PICK)
            player.inventory.freeSlots == 0 -> chatNpc(ChatAnim.SAD, NO_ROOM)
            else -> {
                give(player, BRONZE_PICKAXE)
                chatNpc(ChatAnim.HAPPY, HAVE_A_PICK)
            }
        }
    }

    internal suspend fun Dialogue.fishingTutor(give: (Player, Int) -> Unit) = with(FishingTutorLines) {
        val level = player.skills.baseLevel(Skill.FISHING)
        val first = when {
            level <= 28 -> BASICS
            level <= 38 -> TIPS
            else -> ADVANCED
        }
        while (true) {
            when (choice(first, FISH, WHERE, GOODBYE)) {
                1 -> {
                    chatPlayer(ChatAnim.QUIZ, first)
                    netIfNeeded(give)
                    when (first) {
                        BASICS -> {
                            objbox(FISHING_SPOT_ICON, SPOT_HINT)
                            chatNpc(ChatAnim.HAPPY, HOW_TO)
                            chatPlayer(ChatAnim.QUIZ, IS_THAT_IT)
                            chatNpc(ChatAnim.LAUGH, PROGRESS)
                            chatNpc(ChatAnim.NEUTRAL, FULL_INVENTORY)
                        }
                        TIPS -> {
                            chatNpc(ChatAnim.NEUTRAL, CHOOSE)
                            chatNpc(ChatAnim.HAPPY, HANG_ON)
                            chatNpc(ChatAnim.HAPPY, TRAWLER)
                            chatNpc(ChatAnim.NEUTRAL, MURPHY)
                            chatNpc(ChatAnim.QUIZ, SHOW_MAP)
                            choice(YES_PLEASE, NO_THANKS)
                        }
                        else -> {
                            chatNpc(ChatAnim.HAPPY, TUNA_SWORDFISH)
                            chatNpc(ChatAnim.HAPPY, SHARK)
                            chatNpc(ChatAnim.NEUTRAL, POTION)
                            chatNpc(ChatAnim.NEUTRAL, NOW_QUESTS)
                            chatNpc(ChatAnim.NEUTRAL, NO_QUESTS)
                        }
                    }
                }
                2 -> {
                    chatPlayer(ChatAnim.QUIZ, FISH)
                    fish(level)
                }
                3 -> {
                    chatPlayer(ChatAnim.QUIZ, WHERE)
                    where(level)
                }
                else -> {
                    chatPlayer(ChatAnim.NEUTRAL, GOODBYE)
                    return@with
                }
            }
        }
    }

    /** The fish menu, until Tell me about... (back to the first menu). */
    private suspend fun Dialogue.fish(level: Int) = with(FishingTutorLines) {
        while (true) {
            val options = listOfNotNull(
                SMALL_NET,
                BIG_NET,
                ROD_FLY,
                when {
                    level >= 39 -> HARPOON
                    level >= 29 -> LOBSTER
                    else -> null
                },
                TELL_ME_ABOUT,
            )
            when (options[choice(*options.toTypedArray()) - 1]) {
                SMALL_NET -> {
                    objbox(SMALL_FISHING_NET, SMALL_NET_HINT)
                    doubleobjbox(RAW_SHRIMPS, RAW_ANCHOVIES, text(SMALL_NET_CATCH))
                }
                BIG_NET -> {
                    objbox(BIG_FISHING_NET, BIG_NET_HINT)
                    doubleobjbox(RAW_MACKEREL, RAW_COD, text(BIG_NET_CATCH))
                    chatNpc(ChatAnim.HAPPY, RICH_REWARDS)
                }
                ROD_FLY -> {
                    objbox(FISHING_ROD, ROD_HINT)
                    doubleobjbox(RAW_SARDINE, RAW_PIKE, text(ROD_CATCH))
                    objbox(FLY_FISHING_ROD, FLY_HINT)
                    doubleobjbox(RAW_TROUT, RAW_SALMON, text(FLY_CATCH))
                }
                LOBSTER -> {
                    objbox(LOBSTER_POT, LOBSTER_HINT)
                    objbox(RAW_LOBSTER, LOBSTER_CATCH)
                    chatPlayer(ChatAnim.QUIZ, IS_THAT_ALL)
                }
                HARPOON -> {
                    objbox(HARPOON_OBJ, HARPOON_HINT)
                    doubleobjbox(RAW_TUNA, RAW_SWORDFISH, text(HARPOON_CATCH))
                    objbox(RAW_SHARK, AND_SHARK)
                    chatNpc(ChatAnim.HAPPY, FISHING_GUILD)
                }
                else -> return@with
            }
        }
    }

    /** Where and what to fish, by level; members' worlds' lines from Fishing 10. */
    private suspend fun Dialogue.where(level: Int) = with(FishingTutorLines) {
        when {
            level <= 9 -> {
                chatNpc(ChatAnim.HAPPY, YOUNG)
                chatPlayer(ChatAnim.CONFUSED, WHAT_PARROT)
                chatNpc(ChatAnim.HAPPY, PERCY)
                chatPlayer(ChatAnim.SHIFTY, OOKAY)
                chatNpc(ChatAnim.LAUGH, ARRR)
            }
            level <= 15 -> chatNpc(ChatAnim.NEUTRAL, HERRING)
            level <= 22 -> chatNpc(ChatAnim.NEUTRAL, MACKEREL)
            level <= 27 -> chatNpc(ChatAnim.NEUTRAL, COD)
            level <= 37 -> chatNpc(ChatAnim.NEUTRAL, SLIMY_EEL)
            level <= 45 -> chatNpc(ChatAnim.NEUTRAL, CAVE_EEL)
            level <= 49 -> chatNpc(ChatAnim.NEUTRAL, BASS)
            else -> {
                chatNpc(ChatAnim.HAPPY, CATHERBY)
                chatNpc(ChatAnim.HAPPY, MANTA)
            }
        }
    }

    /** With no net and a free slot, a small fishing net; with one, a line saying so. */
    private suspend fun Dialogue.netIfNeeded(give: (Player, Int) -> Unit) = with(FishingTutorLines) {
        when {
            owns(SMALL_FISHING_NET) -> chatNpc(ChatAnim.NEUTRAL, HAVE_NET)
            player.inventory.freeSlots > 0 -> {
                give(player, SMALL_FISHING_NET)
                chatNpc(ChatAnim.HAPPY, LOST_NET)
            }
        }
    }

    /** Whether the player has [obj] carried, worn or banked; see the class comment on the bank. */
    private fun Dialogue.owns(obj: Int): Boolean =
        player.inventory.count(obj) > 0 || player.equipment.count(obj) > 0 || player.bank.count(obj) > 0
}

/** The two swamp tutors' Talk-to. */
fun registerSwampTutors(world: World) {
    val give: (Player, Int) -> Unit = { player, obj ->
        world.objTypes[obj]?.let { world.groundObjs.give(player, it, 1) } ?: logger.warn { "No item $obj in the cache" }
    }
    world.handlers.onNpc(SwampTutors.MINING_TUTOR, SwampTutors.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(SwampTutors) { miningTutor(give) } }
    }
    world.handlers.onNpc(SwampTutors.FISHING_TUTOR, SwampTutors.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(SwampTutors) { fishingTutor(give) } }
    }
}
