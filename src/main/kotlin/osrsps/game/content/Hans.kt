package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.game.dialogue.Lines
import osrsps.map.Tile
import kotlin.math.sign

/** Hans's lines; the words are in the dialogue file, from the Wiki's Transcript:Hans (read 2026-10-22). */
object HansLines : Lines("hans", "Transcript:Hans") {
    val GREETING = line("greeting", "Talk-to: Hans's first line")
    val IN_CHARGE = line("in_charge", "Talk-to: option 1, which the player then says")
    val KILL = line("kill", "Talk-to: option 2, which the player then says")
    val LOST = line("lost", "Talk-to: option 3, which the player then says")
    val HOW_LONG = line("how_long", "Talk-to: option 4, which the player then says")
    val NOTHING = line("nothing", "Talk-to: option 5, which the player then says")
    val DUKE = line("duke", "Talk-to: Hans's answer to option 1")
    val HELP = line("help", "Talk-to: Hans's answer to option 2")
    val WHERE = line("where", "Talk-to: Hans's answer to option 3")
    val NEWCOMERS = line("newcomers", "Age (and option 4): Hans's first line")
    val TIME_PLAYED = line(
        "time_played", "Age (and option 4): Hans's second line, each [amount] written as its placeholder",
        "days", "hours", "minutes", "days_ago",
    )
}

/**
 * Hans (NPC 3105) in Lumbridge Castle's courtyard: Talk-to and Age, as the OSRS Wiki's transcript has them. Not from the
 * wiki, so guessed: every chathead expression, that Age gives the time-played lines on their own, and how far and where
 * Hans runs when threatened.
 */
object Hans {
    const val ID = 3105
    const val TALK_TO = 1
    const val AGE = 2

    /** How far Hans runs from a player who threatens him, in tiles along each axis. Guessed. */
    const val FLEE_DISTANCE = 4

    internal suspend fun Dialogue.talk(nowMillis: Long) = with(HansLines) {
        chatNpc(ChatAnim.NEUTRAL, GREETING)
        when (choice(IN_CHARGE, KILL, LOST, HOW_LONG, NOTHING)) {
            1 -> {
                chatPlayer(ChatAnim.NEUTRAL, IN_CHARGE)
                chatNpc(ChatAnim.NEUTRAL, DUKE)
            }
            2 -> {
                chatPlayer(ChatAnim.ANGRY, KILL)
                flee()
                chatNpc(ChatAnim.SCARED, HELP)
            }
            3 -> {
                chatPlayer(ChatAnim.CONFUSED, LOST)
                chatNpc(ChatAnim.NEUTRAL, WHERE)
            }
            4 -> {
                chatPlayer(ChatAnim.QUIZ, HOW_LONG)
                age(nowMillis)
            }
            else -> chatPlayer(ChatAnim.NEUTRAL, NOTHING)
        }
    }

    internal suspend fun Dialogue.age(nowMillis: Long) {
        chatNpc(ChatAnim.NEUTRAL, HansLines.NEWCOMERS)
        chatNpc(ChatAnim.NEUTRAL, HansLines.TIME_PLAYED, *timePlayed(player, nowMillis))
    }

    /**
     * The four amounts in the wiki's time-played line: days, hours and minutes played, and how many days ago the account
     * was made. Time played counts game ticks logged in (0.6 s each); whether live counts the same way, and how it
     * words a count of 1, isn't known.
     */
    fun timePlayed(player: Player, nowMillis: Long): Array<Pair<String, Any>> {
        val minutes = player.playedTicks * World.TICK_MILLIS / 60_000
        val daysAgo = ((nowMillis - player.createdAt) / (24 * 60 * 60 * 1000L)).coerceAtLeast(0)
        return arrayOf("days" to minutes / (24 * 60), "hours" to minutes / 60 % 24, "minutes" to minutes % 60, "days_ago" to daysAgo)
    }

    /**
     * Hans "runs away from the player screaming" (wiki): he heads [FLEE_DISTANCE] tiles away from them, once he is let
     * go at the end of the dialogue. He walks, as NPCs here can't run yet; live has him run. Guessed.
     */
    private fun Dialogue.flee() {
        val npc = npc ?: return
        val dx = (npc.x - player.x).sign.let { if (it == 0 && npc.z == player.z) 1 else it }
        val dz = (npc.z - player.z).sign
        npc.destination = Tile(npc.x + dx * FLEE_DISTANCE, npc.z + dz * FLEE_DISTANCE)
    }
}

/** Hans's options. */
fun registerHans(world: World, now: () -> Long = System::currentTimeMillis) {
    world.handlers.onNpc(Hans.ID, Hans.TALK_TO) { op ->
        world.dialogues.start(this, op.npc) { with(Hans) { talk(now()) } }
    }
    world.handlers.onNpc(Hans.ID, Hans.AGE) { op ->
        world.dialogues.start(this, op.npc) { with(Hans) { age(now()) } }
    }
}
