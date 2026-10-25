package osrsps.game.dialogue

/**
 * One line of a conversation: an NPC's line, the player's, or an option in a menu. The repo has only its [key], where on
 * the Wiki its words are ([source]) and the `{placeholders}` they may hold ([args]); the words themselves are in the
 * server's dialogue file, outside git (see [DialogueText]).
 */
class Line internal constructor(val key: String, val source: String, val args: List<String>) {
    override fun toString() = key
}

/**
 * The lines of one NPC's conversations, keyed `<prefix>.<name>`, with their words on the OSRS Wiki page [page]. Each
 * content file declares its own in an object:
 *
 * ```
 * object HansLines : Lines("hans", "Transcript:Hans") {
 *     val GREETING = line("greeting", "Talk-to, his first line")
 * }
 * ```
 *
 * and lists that object in [osrsps.game.content.DialogueLines.ALL], which the dialogue file is checked against.
 */
abstract class Lines(val prefix: String, val page: String) {
    private val declared = mutableListOf<Line>()

    /** Every line, in the order declared. */
    val lines: List<Line> get() = declared

    /** The Wiki page's address. */
    val url: String get() = WIKI + page.replace(' ', '_')

    init {
        require(NAME.matches(prefix)) { "Bad lines prefix '$prefix'" }
    }

    /** A line called [name], found on [page] at [where], whose words may hold `{arg}` for each of [args]. */
    protected fun line(name: String, where: String, vararg args: String): Line {
        require(NAME.matches(name)) { "Bad line name '$name' in $prefix" }
        require(args.all(NAME::matches)) { "Bad placeholder in $prefix.$name" }
        require(declared.none { it.key == "$prefix.$name" }) { "$prefix.$name is declared twice" }
        return Line("$prefix.$name", where, args.toList()).also { declared += it }
    }

    private companion object {
        val NAME = Regex("[a-z][a-z0-9_]*")
        const val WIKI = "https://oldschool.runescape.wiki/w/"
    }
}
