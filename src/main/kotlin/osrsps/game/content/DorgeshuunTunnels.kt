package osrsps.game.content

import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** Kazgar's words, in the dialogue file. */
object KazgarLines : Lines("kazgar", "OSRS Wiki, Transcript:Kazgar, read 2026-11-19") {
    val GREETING = line("greeting", "Kazgar's greeting")
    val WHO = line("who", "option and player line: who is he")
    val WHO_1 = line("who_1", "Kazgar: Mistag posted him here, and he guides surface-dwellers through the tunnels")
    val MINES = line("mines", "option and player line: the way to the mines")
    val MINES_1 = line("mines_1", "Kazgar, before he shows the way to the mines")
    val MILL = line("mill", "option and player line: the way to the water mill cellar")
    val MILL_1 = line("mill_1", "Kazgar, before he shows the way to the water mill cellar")
    val LATER = line("later", "option and player line, after who he is: maybe some other time")
    val BYE = line("bye", "option and player line: good bye, cave-dweller")
}

/** Dartog's words, in the dialogue file. */
object DartogLines : Lines("dartog", "OSRS Wiki, Transcript:Dartog, read 2026-11-19") {
    val GREETING = line("greeting", "Dartog's greeting")
    val WHO = line("who", "option and player line: who is he")
    val WHO_1 = line("who_1", "Dartog: the council posted him to guard this tunnel")
    val MINES = line("mines", "option and player line: the way to the mines")
    val MINES_1 = line("mines_1", "Dartog, before he shows the way to the mines")
    val CELLAR = line("cellar", "option and player line: the way to Lumbridge Castle cellar")
    val CELLAR_1 = line("cellar_1", "Dartog, before he shows the way to the cellar")
    val FINE = line("fine", "option and player line: they're fine, thanks")
}

/** Mistag's words, in the dialogue file. */
object MistagLines : Lines("mistag", "OSRS Wiki, Transcript:Mistag, read 2026-11-19") {
    val GREETING = line("greeting", "Mistag's greeting")
    val SELL = line("sell", "option and player line: can they sell him some ore")
    val SELL_NONE = line("sell_none", "Mistag, to a player with no unnoted iron or silver ore")
    val SELL_OFFER = line("sell_offer", "Mistag's offer for the ore the player carries", "coins")
    val SELL_OK = line("sell_ok", "option: okay, to his offer")
    val SELL_OK_SAY = line("sell_ok_say", "player line: okay, to his offer")
    val SELL_NO = line("sell_no", "option: no, to his offer")
    val SELL_NO_SAY = line("sell_no_say", "player line: no, to his offer")
    val WHY = line("why", "option and player line: why the Dorgeshuun live underground")
    val WHY_1 = line("why_1", "Mistag: goblins were made by an evil god to fight a war")
    val WHY_2 = line("why_2", "Mistag: their ancestors hid in a hole, sealed by an earthquake")
    val WAR_OVER = line("war_over", "option and player line: the war is over, they can return")
    val WAR_OVER_1 = line("war_over_1", "Mistag: humans slaughter goblins in the caves")
    val WAR_OVER_2 = line("war_over_2", "Mistag: they would not want to leave their home")
    val GOD = line("god", "option and player line: the name of their god")
    val GOD_1 = line("god_1", "Mistag: the name is forgotten")
    val GOD_2 = line("god_2", "Mistag: they are better off without gods")
    val ARM = line("arm", "option and player line: what happened to his arm")
    val ARM_1 = line("arm_1", "Mistag: a mining accident")
    val ARM_2 = line("arm_2", "Mistag: the markers on the wall show the safest path")
    val OUT = line("out", "option and player line: the way out of the mines")
    val MILL = line("mill", "option and player line: the way to the water mill cellar")
    val COME_BACK = line("come_back", "Mistag, before he shows the way out or to the water mill cellar")
}

/** Nardok's words, in the dialogue file. */
object NardokLines : Lines("nardok", "OSRS Wiki, Transcript:Nardok, read 2026-11-19 (after The Lost Tribe)") {
    val GREETING = line("greeting", "Nardok's whispered greeting")
    val WHAT = line("what", "option and player line: what has he got")
    val WHAT_1 = line("what_1", "Nardok: the bone club and bone spear")
    val WHAT_2 = line("what_2", "Nardok: the bone crossbow and dagger")
    val WHAT_3 = line("what_3", "Nardok: good for prey unawares, with the special technique")
    val WHAT_4 = line("what_4", "Nardok: so, is the player interested")
    val MIGHT = line("might", "option and player line: they might be (opens the shop)")
    val NO = line("no", "option and player line: no thanks")
    val NO_1 = line("no_1", "Nardok: stay out of trouble")
    val TECHNIQUE = line("technique", "option and player line: what is this special technique")
    val TECHNIQUE_1 = line("technique_1", "Nardok: the defence-lowering aim")
    val TECHNIQUE_2 = line("technique_2", "Nardok: it always hits an unwary enemy")
    val TECHNIQUE_3 = line("technique_3", "Nardok: few people know it")
    val TECHNIQUE_4 = line("technique_4", "player: Zanik showed them")
    val TECHNIQUE_5 = line("technique_5", "Nardok: good for them, interested?")
    val WHISPER = line("whisper", "option and player line: why is he whispering")
    val WHISPER_1 = line("whisper_1", "Nardok: selling weapons isn't reputable")
    val WHISPER_2 = line("whisper_2", "player: why not")
    val WHISPER_3 = line("whisper_3", "Nardok: weapons hurt people, so the trade is thought dirty")
    val WHISPER_4 = line("whisper_4", "Nardok: is it like that on the surface")
    val WHISPER_5 = line("whisper_5", "player: on the surface weapon-making is noble")
    val WHISPER_6 = line("whisper_6", "Nardok: surface people are strange")
}

/** The Lost Tribe's leftovers' words, in the dialogue file. */
object LostTribeLines : Lines("lost_tribe", "OSRS Wiki, Chest (The Lost Tribe) and the guides' transcripts, read 2026-11-19") {
    val CHEST_FOUND = line("chest_found", "Open on Sigmund's chest in Lumbridge Castle after the quest (the chest page's message)")
    val SHOWS_THE_WAY = line("shows_the_way", "a guide's travel: the message as they lead the player through the tunnels", "guide")
}

/**
 * The tunnels between Lumbridge Castle's cellar, the Dorgeshuun mines and the water mill cellar under Groats' farm (post-quest
 * step 6), as they are once The Lost Tribe and Death to the Dorgeshuun are done, which every account has ([osrsps.game.Quests]
 * sets varbit 532 to 11 and 2258 to 13, so the cache's multilocs and multinpcs show their after-quest forms):
 *
 * - The cellar's hole (6898 on the cellar side, 6899 on the tunnel side, both showing `lost_tribe_cavewall_hole_walldecor`
 *   6905) and the hole between the Goblin Maze and the Lumbridge Swamp Caves (6912) are Agility shortcuts in
 *   skills/agility.json.
 * - The guides, each with Talk-to and two travel options that take the player to the other two ([travel]): Kazgar
 *   ([KAZGAR], `lost_tribe_guide_3ops`) in the tunnel by the cellar, Mistag ([MISTAG], `lost_tribe_mistag_3ops`) in the
 *   mines, who also buys unnoted iron and silver ore, and Dartog ([DARTOG], `dttd_maze_guide_there`) by the tunnel to the
 *   water mill cellar. Spawns in npc-spawns/dorgeshuun-tunnels.json.
 * - Nardok ([NARDOK], `dttd_bone_dealer`) in the mines: Talk-to, and Trade (the shops' own, [registerShops]) opens
 *   Nardok's Bone Weapons (shops.json).
 * - The water mill cellar: the trapdoor in Groats' farmhouse ([MILL_TRAPDOOR], open after the quest) goes down, the
 *   ladder ([MILL_LADDER]) climbs back up, and the tunnel between the cellar and the maze ([TUNNEL_MAZE_SIDE],
 *   [TUNNEL_MILL_SIDE]) is squeezed through both ways. The quest's crates there and at the farm search empty
 *   ([MisthalinSweep.EMPTY_CRATES]).
 * - Sigmund's chest in Lumbridge Castle ([SIGMUND_CHEST]): Open gives the Wiki's after-quest message.
 *
 * Sources: the OSRS Wiki's Kazgar, Mistag, Dartog, Nardok, Nardok's Bone Weapons, Dorgesh-Kaan mine, Water mill cellar,
 * Trapdoor (Water mill cellar), Hole (Lumbridge Castle cellar), Hole (Lumbridge Swamp Caves) and Chest (The Lost Tribe)
 * pages and the guides' transcripts (read 2026-11-19); the guides' and Nardok's tiles from their Wiki maps; ids and the
 * varbits from the 242 cache; the landing tiles from the 242 map. Mistag's ore prices are the Wiki's (13.6 coins an iron
 * ore, 60 a silver ore); the total rounded down is inferred.
 *
 * Inferred: the goblins' lines use the cache's goblin chathead animation (`chatgoblin`), as their heads are goblins'.
 *
 * Guessed: where each guide's travel lands (the tile west of the guide met, or beside Dartog); that a travel option and a
 * travel picked in talk both show the message box and then move the player, with no walk or fade; the trapdoor and
 * ladder tiles ([MILL_BELOW], [MILL_ABOVE]) and their animations; the tunnel's crawl as the Grand Exchange tunnel's.
 *
 * Left out: the dark in the maze and the mines (the Swamp Caves' dark tiles don't reach them), the maze's traps and
 * markers (they only matter on the quest's path), Mistag's Mining helmet for the brooch (a quest item no account
 * holds), Dorgesh-Kaan itself, and the mines' iron and silver rocks (the Mining skill's, if its rock data has them).
 */
object DorgeshuunTunnels {
    /** lost_tribe_guide_3ops, the Kazgar the multinpc `lost_tribe_guide` shows after both quests. */
    const val KAZGAR = 7301
    /** lost_tribe_mistag_3ops. */
    const val MISTAG = 7299
    /** dttd_maze_guide_there, the Dartog `dttd_maze_guide` shows after Death to the Dorgeshuun. */
    const val DARTOG = 997
    /** dttd_bone_dealer. */
    const val NARDOK = 996

    const val TALK_TO = 1
    /** Kazgar's Mines, Mistag's Cellar, Dartog's Mines. */
    const val TRAVEL_1 = 3
    /** Kazgar's and Mistag's Watermill, Dartog's Cellar. */
    const val TRAVEL_2 = 4

    /** dttd_mill_trapdoor: shows `dttd_mill_trapdoor_open` (Go-down) once the quest is past its first steps. */
    const val MILL_TRAPDOOR = 15765
    /** dttd_ladder_up_out_of_mill_basement. */
    const val MILL_LADDER = 15746
    /** dttd_tunnel_mineside: shows `dttd_cave_entrance_hamside_open` (Squeeze-through) after the quest. */
    const val TUNNEL_MAZE_SIDE = 15763
    /** dttd_tunnel_millside: shows `dttd_cave_entrance_millside_open` (Squeeze-through) after the quest. */
    const val TUNNEL_MILL_SIDE = 15764
    /** lost_tribe_chest. */
    const val SIGMUND_CHEST = 6910
    const val OP_1 = 1

    const val IRON_ORE = 440
    const val SILVER_ORE = 442
    const val COINS = 995
    /** Mistag's prices in tenths of a coin: 13.6 an iron ore, 60 a silver ore (80% of their values). */
    const val IRON_TENTHS = 136
    const val SILVER_TENTHS = 600

    data class Spot(val x: Int, val z: Int, val level: Int = 0)

    /** Beside Kazgar (3230,9611), by the cellar. */
    val CELLAR = Spot(3229, 9611)
    /** Beside Mistag (3320,9615), in the mines. */
    val MINES = Spot(3319, 9615)
    /** Beside Dartog (3246,9648), at the tunnel to the water mill cellar. */
    val WATERMILL = Spot(3245, 9648)

    /** The water mill cellar, beside the ladder (3240,9695). */
    val MILL_BELOW = Spot(3239, 9695)
    /** Groats' farmhouse, beside the trapdoor (3230,3286). */
    val MILL_ABOVE = Spot(3230, 3285)
    /** The maze side of the tunnel (its 3 tiles start at 3245,9649) and the cellar side (3245,9661). */
    val TUNNEL_MAZE_END = Spot(3245, 9648)
    val TUNNEL_MILL_END = Spot(3245, 9662)

    /** human_longcrawl, as the Grand Exchange tunnel's (guessed). */
    const val ANIM_CRAWL = 2796
    const val CRAWL_TICKS = 3

    /** What Mistag pays, in coins, for [iron] iron ore and [silver] silver ore. */
    fun orePrice(iron: Long, silver: Long): Long = (iron * IRON_TENTHS + silver * SILVER_TENTHS) / 10

    /** The guide's message box, then the player is moved to [to]. */
    internal suspend fun ScriptContext.travel(world: World, guide: String, to: Spot) {
        world.dialogues.start(this) { mesbox(LostTribeLines.SHOWS_THE_WAY, "guide" to guide) }
        world.teleport(player, to.x, to.z, to.level)
    }

    /** Kazgar's talk: where he is to take the player, or null. */
    internal suspend fun Dialogue.kazgar(): Spot? = with(KazgarLines) {
        chatNpc(ChatAnim.GOBLIN, GREETING)
        when (choice(WHO, MINES, MILL, BYE)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, WHO)
                chatNpc(ChatAnim.GOBLIN, WHO_1)
                when (choice(MINES, MILL, LATER)) {
                    1 -> kazgarMines()
                    2 -> kazgarMill()
                    else -> { chatPlayer(ChatAnim.NEUTRAL, LATER); null }
                }
            }
            2 -> kazgarMines()
            3 -> kazgarMill()
            else -> { chatPlayer(ChatAnim.NEUTRAL, BYE); null }
        }
    }

    private suspend fun Dialogue.kazgarMines(): Spot = with(KazgarLines) {
        chatPlayer(ChatAnim.QUIZ, MINES)
        chatNpc(ChatAnim.GOBLIN, MINES_1)
        DorgeshuunTunnels.MINES
    }

    private suspend fun Dialogue.kazgarMill(): Spot = with(KazgarLines) {
        chatPlayer(ChatAnim.QUIZ, MILL)
        chatNpc(ChatAnim.GOBLIN, MILL_1)
        WATERMILL
    }

    /** Dartog's talk: where he is to take the player, or null. */
    internal suspend fun Dialogue.dartog(): Spot? = with(DartogLines) {
        chatNpc(ChatAnim.GOBLIN, GREETING)
        when (choice(WHO, MINES, CELLAR, FINE)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, WHO)
                chatNpc(ChatAnim.GOBLIN, WHO_1)
                null
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, MINES)
                chatNpc(ChatAnim.GOBLIN, MINES_1)
                DorgeshuunTunnels.MINES
            }
            3 -> {
                chatPlayer(ChatAnim.QUIZ, CELLAR)
                chatNpc(ChatAnim.GOBLIN, CELLAR_1)
                DorgeshuunTunnels.CELLAR
            }
            else -> { chatPlayer(ChatAnim.NEUTRAL, FINE); null }
        }
    }

    /** Mistag's talk: where he is to take the player, or null. */
    internal suspend fun Dialogue.mistag(world: World): Spot? = with(MistagLines) {
        chatNpc(ChatAnim.GOBLIN, GREETING)
        when (choice(SELL, WHY, ARM, OUT, MILL)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, SELL)
                sellOre(world)
                null
            }
            2 -> {
                chatPlayer(ChatAnim.QUIZ, WHY)
                chatNpc(ChatAnim.GOBLIN, WHY_1)
                chatNpc(ChatAnim.GOBLIN, WHY_2)
                if (choice(WAR_OVER, GOD) == 1) {
                    chatPlayer(ChatAnim.NEUTRAL, WAR_OVER)
                    chatNpc(ChatAnim.GOBLIN, WAR_OVER_1)
                    chatNpc(ChatAnim.GOBLIN, WAR_OVER_2)
                } else {
                    chatPlayer(ChatAnim.QUIZ, GOD)
                    chatNpc(ChatAnim.GOBLIN, GOD_1)
                    chatNpc(ChatAnim.GOBLIN, GOD_2)
                }
                null
            }
            3 -> {
                chatPlayer(ChatAnim.QUIZ, ARM)
                chatNpc(ChatAnim.GOBLIN, ARM_1)
                chatNpc(ChatAnim.GOBLIN, ARM_2)
                null
            }
            4 -> {
                chatPlayer(ChatAnim.QUIZ, OUT)
                chatNpc(ChatAnim.GOBLIN, COME_BACK)
                DorgeshuunTunnels.CELLAR
            }
            else -> {
                chatPlayer(ChatAnim.QUIZ, MILL)
                chatNpc(ChatAnim.GOBLIN, COME_BACK)
                WATERMILL
            }
        }
    }

    /** Mistag buys every unnoted iron and silver ore the player carries, if they say yes to his price. */
    private suspend fun Dialogue.sellOre(world: World) = with(MistagLines) {
        val iron = player.inventory.count(IRON_ORE)
        val silver = player.inventory.count(SILVER_ORE)
        if (iron + silver == 0L) return@with chatNpc(ChatAnim.GOBLIN, SELL_NONE)
        val price = orePrice(iron, silver)
        chatNpc(ChatAnim.GOBLIN, SELL_OFFER, "coins" to price)
        if (choice(SELL_OK, SELL_NO) != 1) return@with chatPlayer(ChatAnim.NEUTRAL, SELL_NO_SAY)
        chatPlayer(ChatAnim.NEUTRAL, SELL_OK_SAY)
        // Counted again: the backpack may have changed while the box was open.
        val ironNow = player.inventory.count(IRON_ORE)
        val silverNow = player.inventory.count(SILVER_ORE)
        if (ironNow != iron || silverNow != silver) return@with
        if (player.inventory.count(COINS) + price > Int.MAX_VALUE) return@with
        for ((slot, obj) in player.inventory.contents().toList()) {
            if (obj.id == IRON_ORE || obj.id == SILVER_ORE) player.inventory.remove(slot)
        }
        // Each ore taken frees a slot, so the coins always fit.
        if (price > 0) world.objTypes[COINS]?.let { player.inventory.add(it, price.toInt()) }
    }

    /** Nardok's talk: true when his shop should open. */
    internal suspend fun Dialogue.nardok(): Boolean = with(NardokLines) {
        chatNpc(ChatAnim.GOBLIN, GREETING)
        when (choice(WHAT, NO, WHISPER)) {
            1 -> {
                chatPlayer(ChatAnim.QUIZ, WHAT)
                chatNpc(ChatAnim.GOBLIN, WHAT_1)
                chatNpc(ChatAnim.GOBLIN, WHAT_2)
                chatNpc(ChatAnim.GOBLIN, WHAT_3)
                chatNpc(ChatAnim.GOBLIN, WHAT_4)
                var told = false
                while (true) {
                    val picked = if (told) choice(MIGHT, NO) else choice(MIGHT, NO, TECHNIQUE)
                    when (picked) {
                        1 -> { chatPlayer(ChatAnim.NEUTRAL, MIGHT); return@with true }
                        2 -> { noThanks(); return@with false }
                        else -> {
                            told = true
                            chatPlayer(ChatAnim.QUIZ, TECHNIQUE)
                            chatNpc(ChatAnim.GOBLIN, TECHNIQUE_1)
                            chatNpc(ChatAnim.GOBLIN, TECHNIQUE_2)
                            chatNpc(ChatAnim.GOBLIN, TECHNIQUE_3)
                            chatPlayer(ChatAnim.HAPPY, TECHNIQUE_4)
                            chatNpc(ChatAnim.GOBLIN, TECHNIQUE_5)
                        }
                    }
                }
                false
            }
            2 -> { noThanks(); false }
            else -> {
                chatPlayer(ChatAnim.QUIZ, WHISPER)
                chatNpc(ChatAnim.GOBLIN, WHISPER_1)
                chatPlayer(ChatAnim.QUIZ, WHISPER_2)
                chatNpc(ChatAnim.GOBLIN, WHISPER_3)
                chatNpc(ChatAnim.GOBLIN, WHISPER_4)
                chatPlayer(ChatAnim.NEUTRAL, WHISPER_5)
                chatNpc(ChatAnim.GOBLIN, WHISPER_6)
                false
            }
        }
    }

    private suspend fun Dialogue.noThanks() = with(NardokLines) {
        chatPlayer(ChatAnim.NEUTRAL, NO)
        chatNpc(ChatAnim.GOBLIN, NO_1)
    }
}

private typealias Spot = DorgeshuunTunnels.Spot

fun registerDorgeshuunTunnels(world: World) = with(DorgeshuunTunnels) {
    fun guide(id: Int, name: String, talk: suspend Dialogue.() -> Spot?, first: Spot, second: Spot) {
        world.handlers.onNpc(id, TALK_TO) { op ->
            var to: Spot? = null
            world.dialogues.start(this, op.npc) { to = talk() }
            to?.let { travel(world, name, it) }
        }
        world.handlers.onNpc(id, TRAVEL_1) { travel(world, name, first) }
        world.handlers.onNpc(id, TRAVEL_2) { travel(world, name, second) }
    }
    guide(KAZGAR, "Kazgar", { kazgar() }, MINES, WATERMILL)
    guide(MISTAG, "Mistag", { mistag(world) }, CELLAR, WATERMILL)
    guide(DARTOG, "Dartog", { dartog() }, MINES, CELLAR)

    world.handlers.onNpc(NARDOK, TALK_TO) { op ->
        var shop = false
        world.dialogues.start(this, op.npc) { shop = nardok() }
        if (shop) world.shopTypes.ofNpc(NARDOK)?.let { world.shopScreen.open(player, it) }
    }

    world.handlers.onLoc(MILL_TRAPDOOR, OP_1) {
        arriveDelay()
        player.anim(Climbing.ANIM_PICKUP_FLOOR)
        delay(1)
        world.teleport(player, MILL_BELOW.x, MILL_BELOW.z, MILL_BELOW.level)
    }
    world.handlers.onLoc(MILL_LADDER, OP_1) {
        arriveDelay()
        player.anim(Climbing.ANIM_REACH_FOR_LADDER)
        delay(1)
        world.teleport(player, MILL_ABOVE.x, MILL_ABOVE.z, MILL_ABOVE.level)
    }
    for ((tunnel, to) in listOf(TUNNEL_MAZE_SIDE to TUNNEL_MILL_END, TUNNEL_MILL_SIDE to TUNNEL_MAZE_END)) {
        world.handlers.onLoc(tunnel, OP_1) {
            arriveDelay()
            player.anim(ANIM_CRAWL)
            delay(CRAWL_TICKS)
            world.teleport(player, to.x, to.z, to.level)
        }
    }
    world.handlers.onLoc(SIGMUND_CHEST, OP_1) {
        arriveDelay()
        world.message(player, LostTribeLines.CHEST_FOUND)
    }
}
