package osrsps.game.collection

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.GameLines
import osrsps.game.Player
import osrsps.game.World
import osrsps.game.content.message
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines

private val logger = KotlinLogging.logger {}

/** The Collector's lines, from the Wiki's Transcript:The Collector (standard dialogue), read 2026-11-19. */
object CollectorLines : Lines("collector", "Transcript:The Collector") {
    val BEAUTIFUL = line("beautiful", "Talk-to: his first line")
    val WHAT_IS = line("what_is", "Talk-to: the player's question")
    val EVERYTHING = line("everything", "Talk-to: his answer, the museum's wonders")
    val GUESS_SO = line("guess_so", "Talk-to: the player's answer; with a log already, the end")
    val COLLECTOR = line("collector", "Talk-to without a log: he keeps a record of everything")
    val WOULD_YOU = line("would_you", "Talk-to without a log: he offers one, before the menu")
    val YES = line("yes", "the menu's option 1")
    val YES_SAID = line("yes_said", "option 1: what the player says (not the option's words)")
    val NO = line("no", "the menu's option 2")
    val NO_SAID = line("no_said", "option 2: what the player says (not the option's words)")
    val NO_TASTE = line("no_taste", "option 2: his answer, the end")
    val GIVES_LOG = line("gives_log", "Get-log, or option 1, without a log: the item box with the log he hands over")
    val THERE = line("there", "after the log: his line")
    val THANKS = line("thanks", "after the log: the player's last line")
    val CANT_CARRY = line("cant_carry", "Get-log without a log and with a full inventory: his line")
    val COME_BACK = line("come_back", "a full inventory: the player's last line")
    val HAVE_ONE = line("have_one", "Get-log with a log carried, worn or banked: his line")
    val YOURE_RIGHT = line("youre_right", "with a log already: the player's last line")
    val COUNT_PLEASE = line("count_please", "Claim-log-rewards: the player's question")
    val LOVE_TO = line("love_to", "Claim-log-rewards: his answer")
    val TAKE_A_SEAT = line("take_a_seat", "Claim-log-rewards: his second line")
    val COUNTING = line("counting", "Claim-log-rewards: the plain box while he counts")
    val COLLECTED = line("collected", "Claim-log-rewards: the count, {count} the items logged", "count")
    val RANK_NONE = line("rank_none", "the count under 100 (the Wiki marks it the same as the iron rank's)")
    val RANK_BRONZE = line("rank_bronze", "the count at 100 or more")
    val RANK_IRON = line("rank_iron", "the count at 300 or more")
    val RANK_STEEL = line("rank_steel", "the count at 500 or more")
    val RANK_BLACK = line("rank_black", "the count at 700 or more")
    val RANK_MITHRIL = line("rank_mithril", "the count at 900 or more")
    val RANK_ADAMANT = line("rank_adamant", "the count at 1,000 or more")
    val RANK_RUNE = line("rank_rune", "the count at 1,100 or more")
    val RANK_DRAGON = line("rank_dragon", "the count at 1,200 or more")
    val RANK_GILDED = line("rank_gilded", "the count at 90% of the log or more")
    val COMPLETE = line("complete", "the whole log done")
    val REWARD = line("reward", "after the count: he offers a reward")
    val HANDS_STAFF = line("hands_staff", "the item box with each staff he hands over, {staff} its name", "staff")
    val ALL_REWARDS = line("all_rewards", "no staff left to hand over: his last line")
    val DESTROY_LOG = line("destroy_log", "the destroy check's text for the collection log (the Wiki's infobox)")
    val DESTROY_BOOK = line("destroy_book", "the destroy check's text for a ranked collection log (the Wiki's infobox)")
    val BOOK_NO_ROOM = line("book_no_room", "Create-book on a staff with a full inventory; not on the Wiki")
}

/**
 * The Collector (`vm_collector`, 8491) by the Varrock Museum's stairs, and the items he gives (collection log step 3):
 * the collection log (22711), the staves of collection by rank and the ranked logs their Create-book makes.
 *
 * From the Wiki and the rev 242 cache:
 * - Talk-to, Get-log and Claim-log-rewards as the transcript has them. A log counts as had when it is carried, worn or
 *   banked (the transcript says "inventory or bank").
 * - Ranks: the staves (bronze to dragon, then gilded) at 100, 300, 500, 700, 900, 1,000, 1,100 and 1,200 items logged,
 *   and gilded at 90% of the log rounded down to 25, read against `collection_count` as the overview's rank script
 *   (script7800/script7801) reads them.
 * - The log's and the ranked logs' Read and Collections overview, and worn, Collection Log and Collections Overview;
 *   the staves' worn ops the same, and Create-book on the staff carried or worn.
 * - Destroy on a log asks with the destroy check and takes it.
 *
 * INFERRED: Talk-to's Yes goes on as Get-log does without a log; the count's comment is the one for the highest rank
 * reached (the transcript lists them all), then gilded's and the finished log's on top where reached. GUESSED: every
 * chathead expression; each staff he hands over is one the player has unlocked and has nowhere (carried, worn or
 * banked), highest first, and he stops at a full inventory with the full-inventory lines; Create-book makes one book
 * however many the player has, and says [CollectorLines.BOOK_NO_ROOM] with no room; Destroy says nothing after.
 * Left out: the Varlamore tokens' dialogue (no Varlamore), and Use of an item on the log (Ironmen only).
 */
object TheCollector {
    const val ID = 8491
    const val TALK_TO = 1
    const val GET_LOG = 3
    const val CLAIM_REWARDS = 4

    /** collection_log. */
    const val LOG = 22711

    /** collection_log_bronze to collection_log_gilded, by rank. */
    val BOOKS = List(RANKS) { 30579 + it * 2 }

    /** collection_staff_bronze to collection_staff_gilded, by rank. */
    val STAVES = List(RANKS) { 30597 + it * 2 }

    /** Items logged for the bronze to dragon ranks; gilded's is [gilded]. */
    val THRESHOLDS = listOf(100, 300, 500, 700, 900, 1_000, 1_100, 1_200)

    /** The logs' item ops: Read, Collections overview and Destroy. The staves': Create-book. */
    const val READ = 3
    const val OVERVIEW = 4
    const val DESTROY = 5
    const val CREATE_BOOK = 3

    /** Worn ops (params 451 to 453): Collection Log, Collections Overview, and on a staff Create-book. */
    const val WORN_LOG = 1
    const val WORN_OVERVIEW = 2
    const val WORN_CREATE_BOOK = 3

    private const val RANKS = 9

    /** Gilded's count: 90% of the log rounded down to 25 (script7801); never reached with an empty catalog. */
    fun gilded(max: Int): Int = if (max <= 0) Int.MAX_VALUE else 25 * (max * 90 / 100 / 25)

    /** The ranks [count] items of a [max]-item log have reached, from bronze (0), in order. */
    fun ranks(count: Int, max: Int): List<Int> =
        (THRESHOLDS.indices.filter { count >= THRESHOLDS[it] } + listOfNotNull((RANKS - 1).takeIf { count >= gilded(max) })).distinct()

    /** Whether [player] has [obj] carried, worn or banked. */
    fun has(player: Player, obj: Int): Boolean =
        player.inventory.count(obj) > 0 || player.equipment.count(obj) > 0 || player.bank.count(obj) > 0

    private fun hasLog(player: Player): Boolean = has(player, LOG) || BOOKS.any { has(player, it) }

    internal suspend fun Dialogue.talk(give: (Player, Int) -> Boolean): Unit = with(CollectorLines) {
        chatNpc(ChatAnim.HAPPY, BEAUTIFUL)
        chatPlayer(ChatAnim.CONFUSED, WHAT_IS)
        chatNpc(ChatAnim.HAPPY, EVERYTHING)
        chatPlayer(ChatAnim.NEUTRAL, GUESS_SO)
        if (hasLog(player)) return
        chatNpc(ChatAnim.HAPPY, COLLECTOR)
        chatNpc(ChatAnim.QUIZ, WOULD_YOU)
        if (choice(YES, NO) == 1) {
            chatPlayer(ChatAnim.HAPPY, YES_SAID)
            handOver(give)
        } else {
            chatPlayer(ChatAnim.NEUTRAL, NO_SAID)
            chatNpc(ChatAnim.ANGRY, NO_TASTE)
        }
    }

    internal suspend fun Dialogue.getLog(give: (Player, Int) -> Boolean): Unit = with(CollectorLines) {
        if (hasLog(player)) {
            chatNpc(ChatAnim.QUIZ, HAVE_ONE)
            chatPlayer(ChatAnim.NEUTRAL, YOURE_RIGHT)
        } else {
            handOver(give)
        }
    }

    private suspend fun Dialogue.handOver(give: (Player, Int) -> Boolean): Unit = with(CollectorLines) {
        if (!give(player, LOG)) return fullInventory()
        objbox(LOG, GIVES_LOG)
        chatNpc(ChatAnim.HAPPY, THERE)
        chatPlayer(ChatAnim.HAPPY, THANKS)
    }

    private suspend fun Dialogue.fullInventory(): Unit = with(CollectorLines) {
        chatNpc(ChatAnim.NEUTRAL, CANT_CARRY)
        chatPlayer(ChatAnim.NEUTRAL, COME_BACK)
    }

    internal suspend fun Dialogue.claim(log: CollectionLog, give: (Player, Int) -> Boolean, name: (Int) -> String): Unit = with(CollectorLines) {
        chatPlayer(ChatAnim.QUIZ, COUNT_PLEASE)
        chatNpc(ChatAnim.HAPPY, LOVE_TO)
        chatNpc(ChatAnim.NEUTRAL, TAKE_A_SEAT)
        mesbox(COUNTING)
        val count = log.obtained(player)
        val max = log.catalog.items.size
        chatNpc(ChatAnim.NEUTRAL, COLLECTED, "count" to count)
        val reached = ranks(count, max)
        val comment = listOf(RANK_BRONZE, RANK_IRON, RANK_STEEL, RANK_BLACK, RANK_MITHRIL, RANK_ADAMANT, RANK_RUNE, RANK_DRAGON)
        chatNpc(ChatAnim.HAPPY, reached.filter { it < THRESHOLDS.size }.maxOrNull()?.let { comment[it] } ?: RANK_NONE)
        if (RANKS - 1 in reached) chatNpc(ChatAnim.HAPPY, RANK_GILDED)
        if (max > 0 && count >= max) chatNpc(ChatAnim.SHOCKED, COMPLETE)
        chatNpc(ChatAnim.HAPPY, REWARD)
        val owed = reached.sortedDescending().map { STAVES[it] }.filter { !has(player, it) }
        if (owed.isEmpty()) return chatNpc(ChatAnim.NEUTRAL, ALL_REWARDS)
        for (staff in owed) {
            if (!give(player, staff)) return fullInventory()
            objbox(staff, text(HANDS_STAFF, "staff" to name(staff)))
        }
    }

    /** Create-book: the ranked log of the staff [staff]. */
    fun createBook(world: World, player: Player, staff: Int) {
        val book = BOOKS.getOrNull(STAVES.indexOf(staff)) ?: return
        val type = world.objTypes[book] ?: return logger.warn { "No item $book in the cache" }
        if (player.inventory.add(type, 1) != 1) world.message(player, CollectorLines.BOOK_NO_ROOM)
    }

    fun register(world: World) {
        val give: (Player, Int) -> Boolean = { player, obj ->
            val type = world.objTypes[obj]
            if (type == null) logger.warn { "No item $obj in the cache" }
            type != null && player.inventory.add(type, 1) == 1
        }
        val name: (Int) -> String = { world.objTypes[it]?.name ?: "Item $it" }
        world.handlers.onNpc(ID, TALK_TO) { op -> world.dialogues.start(this, op.npc) { talk(give) } }
        world.handlers.onNpc(ID, GET_LOG) { op -> world.dialogues.start(this, op.npc) { getLog(give) } }
        world.handlers.onNpc(ID, CLAIM_REWARDS) { op -> world.dialogues.start(this, op.npc) { claim(world.collectionLog, give, name) } }

        for (log in listOf(LOG) + BOOKS) {
            world.handlers.onObj(log, READ) { world.collectionLogWindow.open(player) }
            world.handlers.onObj(log, OVERVIEW) { world.collectionLogWindow.openOverview(player) }
            world.handlers.onWorn(log, WORN_LOG) { world.collectionLogWindow.open(player) }
            world.handlers.onWorn(log, WORN_OVERVIEW) { world.collectionLogWindow.openOverview(player) }
            val text = if (log == LOG) CollectorLines.DESTROY_LOG else CollectorLines.DESTROY_BOOK
            world.handlers.onObj(log, DESTROY) { op ->
                val slot = op.sub
                world.dialogues.start(this) {
                    if (!confirmDestroy(log, 1, text(GameLines.DESTROY_TITLE), text(text))) return@start
                    // The log may have moved while the check was open: only the one in that slot goes.
                    if (player.inventory[slot]?.id == log) player.inventory.remove(slot, 1)
                }
            }
        }
        for (staff in STAVES) {
            world.handlers.onObj(staff, CREATE_BOOK) { createBook(world, player, staff) }
            world.handlers.onWorn(staff, WORN_LOG) { world.collectionLogWindow.open(player) }
            world.handlers.onWorn(staff, WORN_OVERVIEW) { world.collectionLogWindow.openOverview(player) }
            world.handlers.onWorn(staff, WORN_CREATE_BOOK) { createBook(world, player, staff) }
        }
    }
}
