package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.map.Tile

/** The Resource Area's words: Mandrith's and Piles' transcripts and the gate's (the Wiki's Transcript pages). */
object ResourceAreaLines : Lines("resource_area", "Transcript:Mandrith; Transcript:Piles; Transcript:Gate (Resource Area)") {
    val WHO = line("who", "the player's question to Mandrith: who he is and what the place is")
    val NAME = line("name", "Mandrith gives his name")
    val COLLECT = line("collect", "Mandrith: he collects resources and sells access to them")
    val LOOK = line("look", "Mandrith: take a look inside his arena")
    val WHATEVER = line("whatever", "the player asks whether they can take whatever they want")
    val ALL_YOURS = line("all_yours", "Mandrith: it's all theirs for the fee")
    val KILL_ME = line("kill_me", "the player asks whether others can kill them inside")
    val WALLS = line("walls", "Mandrith: the walls only hold them back so long")
    val STOP_THEM = line("stop_them", "the player asks whether he'll stop them")
    val PRICE = line("price", "Mandrith laughs: he lets anyone in for the right price")
    val RIGHT = line("right", "the player: Right...")
    val TREASURES = line("treasures", "Mandrith: his arena's treasures came at great expense")
    val ONE_DAY = line("one_day", "Mandrith: one day a larger, more dangerous arena")

    val PAY = line("pay", "GUESSED: the title of the menu on Open from outside, asking for the fee", "fee")
    val PAY_YES = line("pay_yes", "GUESSED: the menu's option to pay and go in")
    val PAY_NO = line("pay_no", "GUESSED: the menu's option to stay out")
    val NO_FEE = line("no_fee", "GUESSED: Open from outside with fewer coins than the fee in the backpack", "fee")
    val PEEK_NONE = line("peek_none", "Peek from outside with nobody in the area (the transcript's words)")
    val PEEK_SOME = line("peek_some", "Peek from outside with players in the area: how many (not in the transcript)", "count")
    val PEEK_INSIDE = line("peek_inside", "Peek from inside: the barren wasteland (the transcript's words)")

    val PILES_HELLO = line("piles_hello", "Piles: he converts items to banknotes for 50 coins each")
    val PILES_WHO = line("piles_who", "the option and the player's line: Who are you?")
    val PILES_THANKS = line("piles_thanks", "the option and the player's line: Thanks.")
    val PILES_DRAYNOR = line("piles_draynor", "Piles: he saw three men noting items in Draynor's market")
    val PILES_NAMES = line("piles_names", "Piles: Niles, Miles and Giles, and his change of name")
    val PILES_NOT_HERE = line("piles_not_here", "INFERRED words wanted: an item he won't note used on Piles (not in the transcript)")
    val PILES_NO_COINS = line("piles_no_coins", "INFERRED words wanted: an item used on Piles with fewer than 50 coins (not in the transcript)")
}

/**
 * The Resource Area, level 51 to 54 Wilderness (Wilderness step W3.6a), as the Wiki's Resource Area, Mandrith, Piles and
 * Gate (Resource Area) pages have it:
 *
 * - **The gate** (`wilderness_resource_gate` 26760 at 3184,3944, on its tile's north edge): Open from outside takes the
 *   7,500-coin fee and lets the player in; from inside it lets them out. Peek from outside counts the players inside;
 *   from inside it shows only the wasteland.
 * - **Logging out inside** puts the player outside the gate (3184,3945) when they log back in, so they pay again.
 * - **Mandrith** (6599) outside the gate talks as his transcript has him.
 * - **Piles** (13) inside notes items that can be got in the area for 50 coins each ([NOTEABLE]).
 *
 * Inferred: the fee taken from the backpack; the area as the tiles inside its wall ([inside]); the items Piles notes
 * (the area's ores, logs and dark crabs, and the bars its ores make at its furnace); Piles noting as many as the coins
 * allow, as the Elder Chaos druid does. Guessed: a menu before paying (its words held), the refusal's words. Left out:
 * the diary discounts (no diaries yet; the full 7,500 is always asked), the message on being put outside after a
 * logout (its words aren't sourced), Mandrith's and Piles' clue steps. The rocks, trees, furnace, anvil and fire are
 * the cache's and work as their skills have them; the dark crab spots and runite golems come in W3.6b.
 */
object ResourceArea {
    const val GATE = 26760
    const val OPEN = 1
    const val PEEK = 2
    const val MANDRITH = 6599
    const val PILES = 13
    const val TALK_TO = 1
    const val COINS = 995

    const val FEE = 7_500
    const val NOTE_FEE = 50

    val GATE_TILES = MemberGates.Gate(listOf(Tile(3184, 3944)), edge = 1)
    val OUTSIDE = Tile(3184, 3945)

    /**
     * What Piles notes (inferred): the area's iron, coal, gold, mithril, adamantite and runite ores, yew and magic logs,
     * raw, cooked and burnt dark crabs, and the iron, steel, gold, mithril, adamantite and runite bars its ores make.
     */
    val NOTEABLE = setOf(
        440, 453, 444, 447, 449, 451,
        1515, 1513,
        11934, 11936, 11938,
        2351, 2353, 2357, 2359, 2361, 2363,
    )

    /** Whether the tile is inside the area's wall. */
    fun inside(x: Int, z: Int, level: Int): Boolean = level == 0 && x in 3175..3196 && z in 3925..3944

    fun inside(player: Player) = inside(player.x, player.z, player.level)

    /** Where a player saved at [x], [z] on [level] comes back: outside the gate if they were inside, else null. */
    fun loginPlace(x: Int, z: Int, level: Int): Tile? = OUTSIDE.takeIf { inside(x, z, level) }

    /** How many of [held] items Piles notes with [coins]. */
    fun noteable(held: Long, coins: Long): Int = minOf(held, coins / NOTE_FEE).toInt().coerceAtLeast(0)
}

fun registerResourceArea(world: World) {
    with(ResourceArea) {
        world.handlers.onLoc(GATE, OPEN) { op ->
            val loc = op.loc!!
            val to = MemberGates.through(GATE_TILES, player.x, player.z)
                ?: return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            arriveDelay()
            if (!inside(to.x, to.z, loc.level)) {
                world.doors.openAs(loc, loc.id, shutAfter = MemberGates.OPEN_TICKS)
                delay(1)
                world.teleport(player, to.x, to.z, loc.level)
                return@onLoc
            }
            var paid = false
            world.dialogues.start(this) { paid = payFee() }
            if (!paid) return@onLoc
            world.doors.openAs(loc, loc.id, shutAfter = MemberGates.OPEN_TICKS)
            delay(1)
            world.teleport(player, to.x, to.z, loc.level)
        }
        world.handlers.onLoc(GATE, PEEK) {
            arriveDelay()
            if (inside(player)) return@onLoc world.message(player, ResourceAreaLines.PEEK_INSIDE)
            val count = world.onlinePlayers.count { !it.loggedOut && inside(it) }
            world.dialogues.start(this) {
                if (count == 0) mesbox(ResourceAreaLines.PEEK_NONE) else mesbox(ResourceAreaLines.PEEK_SOME, "count" to count)
            }
        }
        world.handlers.onNpc(MANDRITH, TALK_TO) { op -> world.dialogues.start(this, op.npc) { mandrith() } }
        world.handlers.onNpc(PILES, TALK_TO) { op -> world.dialogues.start(this, op.npc) { piles() } }
        world.handlers.onObjOnNpc(PILES) { op -> world.dialogues.start(this, op.npc) { note(world, op.obj) } }
    }
}

/** The fee asked at the gate: true once it is paid. */
private suspend fun Dialogue.payFee(): Boolean = with(ResourceAreaLines) {
    if (player.inventory.count(ResourceArea.COINS) < ResourceArea.FEE) {
        mesbox(NO_FEE, "fee" to "7,500")
        return false
    }
    if (choice(PAY_YES, PAY_NO, title = text(PAY, "fee" to "7,500")) != 1) return false
    // The coins may have gone while the menu was open.
    if (player.inventory.count(ResourceArea.COINS) < ResourceArea.FEE) return false
    Ned.take(player, ResourceArea.COINS, ResourceArea.FEE)
    return true
}

private suspend fun Dialogue.mandrith(): Unit = with(ResourceAreaLines) {
    chatPlayer(ChatAnim.NEUTRAL, WHO)
    chatNpc(ChatAnim.NEUTRAL, NAME)
    chatNpc(ChatAnim.NEUTRAL, COLLECT)
    chatNpc(ChatAnim.NEUTRAL, LOOK)
    chatPlayer(ChatAnim.NEUTRAL, WHATEVER)
    chatNpc(ChatAnim.NEUTRAL, ALL_YOURS)
    chatPlayer(ChatAnim.NEUTRAL, KILL_ME)
    chatNpc(ChatAnim.NEUTRAL, WALLS)
    chatPlayer(ChatAnim.NEUTRAL, STOP_THEM)
    chatNpc(ChatAnim.LAUGH, PRICE)
    chatPlayer(ChatAnim.NEUTRAL, RIGHT)
    chatNpc(ChatAnim.NEUTRAL, TREASURES)
    chatNpc(ChatAnim.NEUTRAL, ONE_DAY)
}

private suspend fun Dialogue.piles(): Unit = with(ResourceAreaLines) {
    chatNpc(ChatAnim.NEUTRAL, PILES_HELLO)
    if (choice(PILES_WHO, PILES_THANKS) == 1) {
        chatPlayer(ChatAnim.NEUTRAL, PILES_WHO)
        chatNpc(ChatAnim.NEUTRAL, PILES_DRAYNOR)
        chatNpc(ChatAnim.NEUTRAL, PILES_NAMES)
    }
    chatPlayer(ChatAnim.NEUTRAL, PILES_THANKS)
}

/** Item [id] used on Piles: refused, or every one held noted at 50 coins each, as many as the coins allow. */
private suspend fun Dialogue.note(world: World, id: Int): Unit = with(ResourceAreaLines) {
    val type = world.objTypes[id]
    val noted = type?.certlink?.let { world.objTypes[it] }
    if (type == null || type.isCert || id !in ResourceArea.NOTEABLE || noted == null) return chatNpc(ChatAnim.NEUTRAL, PILES_NOT_HERE)
    val count = ResourceArea.noteable(player.inventory.count(id), player.inventory.count(ResourceArea.COINS))
    if (count <= 0) return chatNpc(ChatAnim.NEUTRAL, PILES_NO_COINS)
    Ned.take(player, ResourceArea.COINS, count * ResourceArea.NOTE_FEE)
    Ned.take(player, id, count)
    player.inventory.add(noted, count)
}
