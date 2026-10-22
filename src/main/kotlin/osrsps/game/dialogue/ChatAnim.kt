package osrsps.game.dialogue

/**
 * A chathead expression: four animations (seqs) in a row, for a page of one to four lines, which move the head's mouth
 * for that long. Ids and names are from the 241 cache's gameval index (seqs `chatneu1` to `chatneu4` and so on), checked
 * on every start by [osrsps.cache.GameFrameCheck]. Picking the animation by line count is how RSMod does it; which
 * expression a line of live dialogue uses isn't in the cache or the wiki's transcripts, so content marks its choices
 * as guessed unless they come from a recording.
 */
enum class ChatAnim(val firstSeq: Int, val cacheName: String) {
    QUIZ(554, "chatquiz"),
    BORED(562, "chatbored"),
    HAPPY(567, "chathap"),
    SHOCKED(571, "chatshock"),
    CONFUSED(575, "chatcon"),
    GOBLIN(580, "chatgoblin"),
    NEUTRAL(588, "chatneu"),
    SHIFTY(592, "chatshifty"),
    SCARED(596, "chatscared"),
    DRUNK(600, "chatdrunk"),
    LAUGH(605, "chatlaugh"),
    SAD(610, "chatsad"),
    ANGRY(614, "chatang"),
    ;

    /** The seq for a page of [lines] lines; more than four (which a page never has) uses the four-line one. */
    fun seq(lines: Int): Int = firstSeq + lines.coerceIn(1, MAX_LINES) - 1

    companion object {
        const val MAX_LINES = 4
    }
}
