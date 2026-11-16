package osrsps.game.content

import osrsps.game.GameLines
import osrsps.game.Inventory
import osrsps.game.Player
import osrsps.game.PlayerEffects
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.game.npc.NpcSpawn
import osrsps.game.script.ScriptContext
import osrsps.game.skill.Agility
import osrsps.game.skill.Mining
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
 * the cache's and work as their skills have them.
 *
 * Step W3.6b: the four dark crab spots (osrsps.game.skill.Fishing) and the two **runite golems** (6600): a golem killed
 * leaves its head as runite rocks (6601) on its tile for [ROCK_TICKS], which anyone with 85 Mining can mine as a runite
 * rock. Inferred: one ore and the rock is gone, as a runite rock gives one; no gem table; the rock coming as the golem
 * falls. Left out: the Mining level scaling melee against a golem, its flat armour.
 */
object ResourceArea {
    const val GATE = 26760
    const val OPEN = 1
    const val PEEK = 2
    const val MANDRITH = 6599
    const val PILES = 13
    const val TALK_TO = 1
    const val COINS = 995

    /** The runite golems (`wilderness_rune_golem`) and the rock a dead one's head becomes (`wilderness_golem_rune_rock`). */
    const val GOLEM = 6600
    const val GOLEM_ROCK = 6601
    const val MINE = 1

    /** How long a golem's head stays a rock: a minute (the Wiki). */
    const val ROCK_TICKS = 100

    const val FEE = 7_500
    const val NOTE_FEE = 50

    /** How long the step across the gate takes (guessed, as the H.A.M. storeroom doors'). */
    const val STEP_TICKS = 1

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

    /**
     * Whether the tile is inside the area's wall: x 3174 to 3196, z 3924 to 3944, every tile walked to from inside with the
     * gate shut (osrsps.audit.ResourceAreaAuditTest; round 38 found the old box a tile short on the west and south).
     */
    fun inside(x: Int, z: Int, level: Int): Boolean = level == 0 && x in 3174..3196 && z in 3924..3944

    fun inside(player: Player) = inside(player.x, player.z, player.level)

    /**
     * The tile in front of the gate on the side a player at [x], [z] reaches it from: their own if they stand in front of
     * it, the one beside them if they reached it from along the wall (as reaching a wall allows); else null.
     */
    fun crossingFrom(x: Int, z: Int, level: Int): Tile? {
        val gate = GATE_TILES.tiles.first()
        if (level != GATE_TILES.level || kotlin.math.abs(x - gate.x) > 1) return null
        return when (z) {
            gate.z -> gate
            OUTSIDE.z -> OUTSIDE
            else -> null
        }
    }

    /** Where a player saved at [x], [z] on [level] comes back: outside the gate if they were inside, else null. */
    fun loginPlace(x: Int, z: Int, level: Int): Tile? = OUTSIDE.takeIf { inside(x, z, level) }

    /**
     * How many of [held] items Piles notes with [coins], when [notedHeld] of their banknotes are already carried: no more
     * than the banknote stack has room for, so none are taken or paid for that can't come back as notes.
     */
    fun noteable(held: Long, coins: Long, notedHeld: Long = 0): Int =
        minOf(held, coins / NOTE_FEE, Inventory.MAX_STACK.toLong() - notedHeld).toInt().coerceAtLeast(0)
}

fun registerResourceArea(world: World) {
    with(ResourceArea) {
        world.handlers.onLoc(GATE, OPEN) { op ->
            val loc = op.loc!!
            val from = crossingFrom(player.x, player.z, player.level)
                ?: return@onLoc world.message(player, GameLines.NOTHING_INTERESTING)
            val to = MemberGates.through(GATE_TILES, from.x, from.z)!!
            arriveDelay()
            if (player.x != from.x || player.z != from.z) {
                // Reached from beside the gate along its wall: in front of it first, on the same side.
                world.teleport(player, from.x, from.z, loc.level)
                delay(1)
            }
            if (!inside(to.x, to.z, loc.level)) return@onLoc stepThrough(world, to, loc.level)
            var paid = false
            world.dialogues.start(this) { paid = payFee() }
            if (!paid) return@onLoc
            stepThrough(world, to, loc.level)
        }
        world.handlers.onLoc(GATE, PEEK) {
            arriveDelay()
            if (inside(player)) return@onLoc world.message(player, ResourceAreaLines.PEEK_INSIDE)
            val count = world.onlinePlayers.count { !it.loggedOut && inside(it) }
            world.dialogues.start(this) {
                if (count == 0) mesbox(ResourceAreaLines.PEEK_NONE) else mesbox(ResourceAreaLines.PEEK_SOME, "count" to count)
            }
        }
        world.npcScripts.onDeath(listOf(GOLEM)) { golem ->
            val type = world.npcTypes[GOLEM_ROCK] ?: return@onDeath
            val rock = world.spawnNpc(NpcSpawn(type, golem.x, golem.z, golem.level, source = "a runite golem's head", respawns = false))
                ?: return@onDeath
            world.npcScripts.later(ROCK_TICKS) { if (world.npcs[rock.index] === rock) world.removeNpc(rock) }
        }
        world.mining.onNpcRock(GOLEM_ROCK, MINE, Mining.RUNITE) { rock -> if (world.npcs[rock.index] === rock) world.removeNpc(rock) }
        world.handlers.onNpc(MANDRITH, TALK_TO) { op -> world.dialogues.start(this, op.npc) { mandrith() } }
        world.handlers.onNpc(PILES, TALK_TO) { op -> world.dialogues.start(this, op.npc) { piles() } }
        world.handlers.onObjOnNpc(PILES) { op -> world.dialogues.start(this, op.npc) { note(world, op.obj) } }
    }
}

/**
 * Across the gate to [to]: the player steps over in [ResourceArea.STEP_TICKS] and the gate stays shut, so nobody waiting
 * beside it can walk in behind a payer without paying (the review of W3.6a found an opened gate let them). Guessed: live
 * swings the gate open as the player walks through.
 */
private suspend fun ScriptContext.stepThrough(world: World, to: Tile, level: Int) {
    val fromX = player.x
    val fromZ = player.z
    player.waypoints.clear()
    val angle = Agility.angle(to.x - fromX, to.z - fromZ)
    player.effects.exactMove(PlayerEffects.ExactMove(fromX, fromZ, to.x, to.z, 0, ResourceArea.STEP_TICKS * 30, angle))
    delay(ResourceArea.STEP_TICKS)
    world.teleport(player, to.x, to.z, level)
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
    val count = ResourceArea.noteable(player.inventory.count(id), player.inventory.count(ResourceArea.COINS), player.inventory.count(noted.id))
    if (count <= 0) return chatNpc(ChatAnim.NEUTRAL, PILES_NO_COINS)
    Ned.take(player, ResourceArea.COINS, count * ResourceArea.NOTE_FEE)
    Ned.take(player, id, count)
    player.inventory.add(noted, count)
}
