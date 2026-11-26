package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.Lines
import osrsps.game.script.ScriptContext

/** The balloon log storage's words (the Wiki's Transcript:Log storage, read 23 Nov 2026, unless a line says otherwise). */
object LogStorageLines : Lines("log_storage", "Log_storage") {
    val STORE_WHICH = line("store_which", "the title over Store's options, one a log the player holds")
    val STORE_HOW_MANY = line("store_how_many", "the amount prompt, with more than one of the log held")
    val STORED = line("stored", "the box with the log after storing (the log's name is the cache's)", "logs", "amount")
    val NONE = line("none", "the message for Store with no logs that go in the crate")
    val FULL = line("full", "the message for a log type at 100")
    val PREFERENCES = line("preferences", "the box before Preferences' options")
    val PREFERENCE_WHICH = line("preference_which", "the title over Preferences' options")
    val INVENTORY_FIRST = line("inventory_first", "a preference option")
    val STORAGE_FIRST = line("storage_first", "a preference option")
    val ASK = line("ask", "a preference option")
    val USE_INVENTORY = line("use_inventory", "the option, for a trip with the ask preference and logs in both")
    val USE_STORAGE = line("use_storage", "the option, for a trip with the ask preference and logs in both")
    val CHECK = line("check", "Check's box", "logs", "oak", "willow", "yew", "magic")
    val LEFT = line("left", "the message for a trip paid from storage; the Wiki (4 May 2022 update) says it gives how many are left, not its words", "logs", "amount")
}

/** A log the storage takes, with the bits of its count in its varp. */
enum class StoredLog(val obj: Int, val magicVarp: Boolean, val shift: Int) {
    LOGS(1511, false, 0),
    OAK(1521, false, 7),
    WILLOW(1519, false, 14),
    YEW(1515, false, 21),
    MAGIC(1513, true, 0),
    ;

    companion object {
        fun of(obj: Int): StoredLog? = entries.firstOrNull { it.obj == obj }
    }
}

/** Which logs a balloon trip burns when the player has some stored and some in the backpack. */
enum class LogPreference { INVENTORY_FIRST, STORAGE_FIRST, ASK }

/**
 * The balloon log storage (travel networks step T6): a crate beside each balloon basket, every one sharing the
 * player's store of up to [MAX] each of normal, oak, willow, yew and magic logs, which [Balloons] pays trips from.
 *
 * - Store: an option per log the player holds (noted logs count), then an amount when more than one is held; using a
 *   log on the crate stores that log the same way.
 * - Preferences: inventory first, storage first, or ask every trip.
 * - Check: how many of each are stored.
 * - Logs never come back out of the crate.
 *
 * Sources: the Wiki's Log storage page and Transcript:Log storage (read 23 Nov 2026) for the logs, the limit, the
 * sharing between stations, noted logs and the words; the 242 cache for the crates (zep_storage 31572 with Store,
 * Preferences and Check, shown by each station's multiloc 31573 to 31578 once the station's basket stands, which
 * [osrsps.game.Quests.AFTER_QUESTS] sets), placed on the 242 map where the Wiki's map pins put them.
 *
 * Inferred: the preference options leave out the one already chosen (the transcript lists all three across its
 * branches); using a log on the crate going straight to the amount for that log. Guessed: the store being kept in two
 * varps of ours ([CustomVars.logStorageVarp], [CustomVars.logStorageMagicVarp]); inventory first being the preference
 * of a player who never chose one; the amount box offering at most what fits; a trip with the ask preference asking
 * only when both hold the log; the words of the message for a trip paid from storage (a key with no words until a
 * recording).
 */
class LogStorage(private val world: World) {
    fun register() {
        for (crate in CRATES) {
            world.handlers.onLoc(crate, STORE) { arriveDelay(); store(null) }
            world.handlers.onLoc(crate, PREFERENCES) { arriveDelay(); preferences() }
            world.handlers.onLoc(crate, CHECK) { arriveDelay(); check() }
            for (log in StoredLog.entries) {
                world.handlers.onObjOnLoc(crate, log.obj) { arriveDelay(); store(log) }
                val noted = world.objTypes[log.obj]?.certlink ?: -1
                if (noted >= 0) world.handlers.onObjOnLoc(crate, noted) { arriveDelay(); store(log) }
            }
        }
    }

    /** How many of [log] [player] has stored. */
    fun stored(player: Player, log: StoredLog): Int {
        val varp = varp(player, log) ?: return 0
        return (player.vars[varp] ushr log.shift) and COUNT_MASK
    }

    private fun setStored(player: Player, log: StoredLog, count: Int) {
        val varp = varp(player, log) ?: return
        val mask = COUNT_MASK shl log.shift
        player.vars[varp] = (player.vars[varp] and mask.inv()) or (count.coerceIn(0, MAX) shl log.shift)
    }

    /** Takes one of [log] from [player]'s store, returning how many are left, or null when there was none. */
    fun takeOne(player: Player, log: StoredLog): Int? {
        val have = stored(player, log)
        if (have == 0) return null
        setStored(player, log, have - 1)
        return have - 1
    }

    fun preference(player: Player): LogPreference {
        val varp = player.vars.types.namedVarps[CustomVars.LOG_STORAGE] ?: return LogPreference.INVENTORY_FIRST
        return LogPreference.entries.getOrElse((player.vars[varp] ushr PREFERENCE_SHIFT) and PREFERENCE_MASK) { LogPreference.INVENTORY_FIRST }
    }

    private fun setPreference(player: Player, preference: LogPreference) {
        val varp = player.vars.types.namedVarps[CustomVars.LOG_STORAGE] ?: return
        val mask = PREFERENCE_MASK shl PREFERENCE_SHIFT
        player.vars[varp] = (player.vars[varp] and mask.inv()) or (preference.ordinal shl PREFERENCE_SHIFT)
    }

    private fun varp(player: Player, log: StoredLog): Int? =
        player.vars.types.namedVarps[if (log.magicVarp) CustomVars.LOG_STORAGE_MAGIC else CustomVars.LOG_STORAGE]

    /** The ids that count as [log] in the backpack: the log and its note. */
    private fun ids(log: StoredLog): Set<Int> = setOfNotNull(log.obj, world.objTypes[log.obj]?.certlink?.takeIf { it >= 0 })

    private fun held(player: Player, log: StoredLog): Long = ids(log).sumOf { player.inventory.count(it) }

    private suspend fun ScriptContext.store(only: StoredLog?) {
        val text = world.textFor(player)
        val held = StoredLog.entries.filter { (only == null || it == only) && held(player, it) > 0 }
        if (held.isEmpty()) return player.message(text[LogStorageLines.NONE])
        val log = if (held.size == 1) {
            held.single()
        } else {
            var picked: StoredLog? = null
            world.dialogues.start(this) {
                val names = held.map { world.objTypes[it.obj]?.name ?: it.name }
                picked = held[choice(*names.toTypedArray(), title = text[LogStorageLines.STORE_WHICH]) - 1]
            }
            picked ?: return
        }
        val room = MAX - stored(player, log)
        if (room <= 0) return player.message(text[LogStorageLines.FULL])
        val have = held(player, log)
        val asked = if (have > 1) countDialog(text[LogStorageLines.STORE_HOW_MANY]) else 1
        // The prompt waited: count again, and fit what the crate has room for.
        val amount = minOf(asked.toLong(), held(player, log), (MAX - stored(player, log)).toLong()).toInt()
        if (amount <= 0) return
        val removed = remove(player, log, amount)
        if (removed == 0) return
        setStored(player, log, stored(player, log) + removed)
        val name = world.objTypes[log.obj]?.name ?: log.name
        world.dialogues.start(this) {
            objbox(log.obj, text[LogStorageLines.STORED, "logs" to name, "amount" to stored(player, log)])
        }
    }

    /** Takes up to [amount] of [log], noted or not, from the backpack, returning how many came off. */
    private fun remove(player: Player, log: StoredLog, amount: Int): Int {
        val ids = ids(log)
        var left = amount
        for ((slot, obj) in player.inventory.contents()) {
            if (left == 0) break
            if (obj.id in ids) left -= player.inventory.remove(slot, left)
        }
        return amount - left
    }

    private suspend fun ScriptContext.preferences() {
        val current = preference(player)
        val options = listOf(
            LogPreference.INVENTORY_FIRST to LogStorageLines.INVENTORY_FIRST,
            LogPreference.STORAGE_FIRST to LogStorageLines.STORAGE_FIRST,
            LogPreference.ASK to LogStorageLines.ASK,
        ).filter { it.first != current }
        world.dialogues.start(this) {
            mesbox(LogStorageLines.PREFERENCES)
            val picked = choice(*options.map { text(it.second) }.toTypedArray(), title = text(LogStorageLines.PREFERENCE_WHICH))
            setPreference(player, options[picked - 1].first)
        }
    }

    private suspend fun ScriptContext.check() {
        world.dialogues.start(this) {
            mesbox(
                LogStorageLines.CHECK,
                "logs" to stored(player, StoredLog.LOGS), "oak" to stored(player, StoredLog.OAK),
                "willow" to stored(player, StoredLog.WILLOW), "yew" to stored(player, StoredLog.YEW),
                "magic" to stored(player, StoredLog.MAGIC),
            )
        }
    }

    companion object {
        /** `zep_multi_storage_entrana`, `_tav`, `_cast`, `_gno`, `_craft` and `_varr`: the crates as the map places them. */
        val CRATES = 31573..31578

        const val STORE = 1
        const val PREFERENCES = 2
        const val CHECK = 3

        /** The Wiki: up to 100 of each. */
        const val MAX = 100

        private const val COUNT_MASK = 0x7F
        private const val PREFERENCE_SHIFT = 28
        private const val PREFERENCE_MASK = 0x3
    }
}
