package osrsps.game.content.randomevents

import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.interfaces.IfSetModelV2
import osrsps.game.FrameSlot
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.bank.BankScreen
import osrsps.game.content.message
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.map.Tile

/** The Pillory Guard's lines; the words are in the dialogue file, from his transcript (read 2026-11-19). */
object PilloryGuardLines : Lines("pillory_guard", "Transcript:Pillory Guard") {
    val UNDER_ARREST = line("under_arrest", "Overhead dialogue: dialogue 1", "name")
    val COME_TO_ARREST = line("come_to_arrest", "Overhead dialogue: dialogue 2", "name")
    val LOCKED_UP = line("locked_up", "Overhead dialogue: dialogue 3", "name")
    val REPORT = line("report", "Overhead dialogue: dialogue 4", "name")
    val MAYBE_LATER = line("maybe_later", "Overhead dialogue: if the player does not respond in time")
    val ARREST = line("arrest", "Standard dialogue: his words")
    val COME_QUIETLY = line("come_quietly", "Standard dialogue: the first option, and the player's words")
    val NOT_COMING = line("not_coming", "Standard dialogue: the second option, and the player's words")
    val HMMPH = line("hmmph", "The \"I'm not coming.\" option: his answer")
    val HERE_TO_ARREST = line("here_to_arrest", "Other players' Pillory Guard", "name")
}

/**
 * The Pillory (OSRS Wiki, Pillory and Pillory Guard pages and the guard's transcript, read 2026-11-19). The Pillory
 * Guard (380, macro_pillory_guard; Talk-to, Dismiss) comes to arrest the player; coming quietly puts them in one of the
 * cages (pillory_railing, 20873) of the pillory in Varrock (behind the general store), Yanille or Seers' Village, on the
 * real map, and opens the lock window (pillory, 27): a big lock, three keys, and small locks for the locks to undo.
 * Select on the key matching the big lock undoes one; a wrong key adds a lock (up to six) and starts the count again.
 * Three, or however many there are by then, undone send the player back where they were with one reward from Mod
 * Ash's table (out of 120). Nothing else lets them out. Unlock on a cage tells anyone else it won't open (the
 * Varrock pillory's line, [osrsps.game.content.PilloryLines]).
 *
 * Guessed: which lock models go with which keys: the three lock models (13390, 13393, 13394, one size) paired in id
 * order with the three key models (4141, 13395, 13396) the window's keys start with; the keys shown being the three
 * shapes in a random order; the small locks shown, one for each lock to undo, with a flash on each undone; which town
 * and cage (one at random, an empty cage first); the Unlock on the cage from inside opening the window again (the
 * window can be closed and nothing else is left to open it); that a player who logs out in a cage gets the window again
 * on logging in and is sent to Lumbridge when done (the way back isn't saved). Left out: the tramp and his rotten
 * tomatoes, and other players pelting the prisoner.
 */
object Pillory : RandomEvent("pillory", 380) {
    const val CAGE = osrsps.game.content.VarrockSweepLocs.PILLORY_CAGE
    const val UNLOCK = 1
    const val INTERFACE = 27
    const val LOCK = 3
    val KEYS = listOf(4, 5, 6)
    val BUTTONS = listOf(7, 8, 9)
    val LOCKS_DONE = (10..15).toList()
    val FLASHES = (16..21).toList()

    const val START_LOCKS = 3
    const val MAX_LOCKS = 6

    /** The shapes, by the big lock's model and its key's (see the class comment: the pairing is a guess). */
    enum class Shape(val lockModel: Int, val keyModel: Int) { FIRST(13390, 4141), SECOND(13393, 13395), THIRD(13394, 13396) }

    /** The cages, by town: the tile inside each (its railing on the side away from the street). */
    val TOWNS: List<List<Tile>> = listOf(
        listOf(Tile(3226, 3407), Tile(3228, 3407), Tile(3230, 3407)),
        listOf(Tile(2604, 3105), Tile(2606, 3105), Tile(2608, 3105)),
        listOf(Tile(2681, 3489), Tile(2683, 3489), Tile(2685, 3489)),
    )
    val CELLS: Set<Tile> = TOWNS.flatten().toSet()

    /** Mod Ash's table (the Pillory page), out of 120: coins, uncut gems, a cosmic talisman and the key halves. */
    val REWARDS: List<Triple<Int, Int, Int>> = listOf(
        Triple(995, 20, 10), Triple(995, 40, 10), Triple(995, 80, 6), Triple(995, 160, 10), Triple(995, 320, 10), Triple(995, 640, 10),
        Triple(1623, 1, 32), Triple(1621, 1, 16), Triple(1619, 1, 8), Triple(1617, 1, 2),
        Triple(1454, 1, 4), Triple(985, 1, 1), Triple(987, 1, 1),
    )
    const val REWARD_ROLL = 120

    /** Guessed: how soon after logging in a prisoner gets the window back. */
    const val LOGIN_OPEN_TICKS = 2

    /** A spell in a cage: where the player goes back to, and the locks. */
    class Stay(val back: Tile, val backLevel: Int) {
        var locks = START_LOCKS
        var undone = 0
        var shape = Shape.FIRST
        var keys: List<Shape> = Shape.entries
    }

    /** By player: a new login is a new player, and [loggedIn] finds a cage's prisoner again. */
    private val stays = java.util.WeakHashMap<Player, Stay>()

    override val teleports = true
    override val nags = with(PilloryGuardLines) { listOf(UNDER_ARREST, COME_TO_ARREST, LOCKED_UP, REPORT) }
    override val leaving = PilloryGuardLines.MAYBE_LATER
    override val notYours = PilloryGuardLines.HERE_TO_ARREST

    override suspend fun Dialogue.talk(world: World, events: RandomEvents): Boolean = with(PilloryGuardLines) {
        chatNpc(ChatAnim.ANGRY, ARREST)
        if (choice(COME_QUIETLY, NOT_COMING) != 1) {
            chatPlayer(ChatAnim.ANGRY, NOT_COMING)
            chatNpc(ChatAnim.ANGRY, HMMPH)
            return true
        }
        chatPlayer(ChatAnim.SAD, COME_QUIETLY)
        lockUp(world, player)
        true
    }

    fun stayOf(player: Player): Stay? = stays[player]

    private fun lockUp(world: World, player: Player) {
        val town = TOWNS[world.random.nextInt(TOWNS.size)]
        val free = town.filter { cell -> world.onlinePlayers.none { it.x == cell.x && it.z == cell.z && it.level == 0 } }
        val cell = if (free.isNotEmpty()) free[world.random.nextInt(free.size)] else town[world.random.nextInt(town.size)]
        val stay = Stay(Tile(player.x, player.z), player.level)
        stays[player] = stay
        world.teleport(player, cell.x, cell.z, 0)
        newLock(world, stay)
        open(world, player, stay)
    }

    private fun newLock(world: World, stay: Stay) {
        stay.shape = Shape.entries[world.random.nextInt(Shape.entries.size)]
        val keys = Shape.entries.toMutableList()
        for (i in keys.indices.reversed()) {
            val j = world.random.nextInt(i + 1)
            keys[i] = keys[j].also { keys[j] = keys[i] }
        }
        stay.keys = keys
    }

    /** The lock window, drawn for the stay as it is. */
    fun open(world: World, player: Player, stay: Stay) {
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, INTERFACE)
        for (button in BUTTONS) player.session.queue(IfSetEventsV2(INTERFACE, button, -1, -1, 0, BankScreen.opMask(1..1)))
        draw(world, player, stay)
    }

    private fun draw(world: World, player: Player, stay: Stay) {
        player.session.queue(IfSetModelV2(INTERFACE, LOCK, stay.shape.lockModel))
        for ((i, key) in KEYS.withIndex()) player.session.queue(IfSetModelV2(INTERFACE, key, stay.keys[i].keyModel))
        for ((i, component) in LOCKS_DONE.withIndex()) world.interfaces.setHidden(player, INTERFACE, component, i >= stay.locks)
        for ((i, component) in FLASHES.withIndex()) world.interfaces.setHidden(player, INTERFACE, component, i >= stay.undone)
    }

    /** Select on key [index] (0 to 2): right undoes a lock, wrong adds one and starts again. */
    fun select(world: World, player: Player, index: Int) {
        val stay = stayOf(player) ?: return
        if (stay.keys.getOrNull(index) == stay.shape) {
            stay.undone++
            if (stay.undone >= stay.locks) return free(world, player, stay)
        } else {
            stay.locks = (stay.locks + 1).coerceAtMost(MAX_LOCKS)
            stay.undone = 0
        }
        newLock(world, stay)
        draw(world, player, stay)
    }

    private fun free(world: World, player: Player, stay: Stay) {
        letOut(world, player, stay)
        var roll = world.random.nextInt(REWARD_ROLL)
        for ((id, count, weight) in REWARDS) {
            if (roll < weight) {
                world.randomEvents.reward(player, id, count)
                return
            }
            roll -= weight
        }
    }

    private fun letOut(world: World, player: Player, stay: Stay) {
        stays.remove(player)
        world.interfaces.closeModals(player)
        val (x, z, level) = if (world.canStand(stay.back.x, stay.back.z, stay.backLevel)) Triple(stay.back.x, stay.back.z, stay.backLevel)
        else Triple(World.SPAWN_X, World.SPAWN_Z, 0)
        world.teleport(player, x, z, level)
    }

    override fun end(world: World, player: Player): Boolean {
        letOut(world, player, stayOf(player) ?: return false)
        return true
    }

    /** Unlock on a cage: the prisoner gets the window back; anyone else is told it stays shut. */
    fun unlock(world: World, player: Player) {
        val stay = stayOf(player)
        if (stay != null) return open(world, player, stay)
        world.message(player, osrsps.game.content.PilloryLines.UNLOCK)
    }

    /** A player who logged out in a cage is still locked in. */
    override fun loggedIn(world: World, player: Player) {
        if (player.level != 0 || Tile(player.x, player.z) !in CELLS) return
        val stay = stays.getOrPut(player) { Stay(Tile(World.SPAWN_X, World.SPAWN_Z), 0).also { newLock(world, it) } }
        // After the game frame is up, which login sends later in the same tick.
        world.npcScripts.later(LOGIN_OPEN_TICKS) { if (stays[player] === stay && player in world.onlinePlayers) open(world, player, stay) }
    }

    override fun loggedOut(player: Player) {
        stays.remove(player)
    }

    fun register(world: World) {
        world.handlers.onLoc(CAGE, UNLOCK) { unlock(world, player) }
        for ((i, button) in BUTTONS.withIndex()) world.handlers.onButton(INTERFACE, button, 1) { if (INTERFACE in player.modals.values) select(world, player, i) }
    }

    /** The interface's gameval names, checked against the cache. */
    val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>>
        get() = mapOf(
            INTERFACE to ("pillory" to mapOf(
                LOCK to "pillory_lock", 4 to "pillory_key1", 5 to "pillory_key2", 6 to "pillory_key3",
                7 to "button1", 8 to "button2", 9 to "button3", 10 to "pillory_lock_done_1", 15 to "pillory_lock_done_6",
                16 to "pillory_flash1", 21 to "pillory_flash6",
            )),
        )
}
