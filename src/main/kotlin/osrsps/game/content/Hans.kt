package osrsps.game.content

import osrsps.game.Player
import osrsps.game.World
import osrsps.game.dialogue.ChatAnim
import osrsps.game.dialogue.Dialogue
import osrsps.map.Tile
import kotlin.math.sign

/**
 * Hans (NPC 3105) in Lumbridge Castle's courtyard: Talk-to and Age. The words are the OSRS Wiki's transcript
 * (Transcript:Hans, read 2026-10-22). Not from the wiki, so guessed: every chathead expression, that Age gives the
 * time-played lines on their own, and how far and where Hans runs when threatened.
 */
object Hans {
    const val ID = 3105
    const val TALK_TO = 1
    const val AGE = 2

    /** How far Hans runs from a player who threatens him, in tiles along each axis. Guessed. */
    const val FLEE_DISTANCE = 4

    internal suspend fun Dialogue.talk(nowMillis: Long) {
        chatNpc(ChatAnim.NEUTRAL, "Hello. What are you doing here?")
        when (
            choice(
                "I'm looking for whoever is in charge of this place.",
                "I have come to kill everyone in this castle!",
                "I don't know. I'm lost. Where am I?",
                "Can you tell me how long I've been here?",
                "Nothing.",
            )
        ) {
            1 -> {
                chatPlayer(ChatAnim.NEUTRAL, "I'm looking for whoever is in charge of this place.")
                chatNpc(ChatAnim.NEUTRAL, "Who, the Duke? He's in his study, on the first floor.")
            }
            2 -> {
                chatPlayer(ChatAnim.ANGRY, "I have come to kill everyone in this castle!")
                flee()
                chatNpc(ChatAnim.SCARED, "Help! Help!")
            }
            3 -> {
                chatPlayer(ChatAnim.CONFUSED, "I don't know. I'm lost. Where am I?")
                chatNpc(
                    ChatAnim.NEUTRAL,
                    "You are in Lumbridge Castle, in the Kingdom of Misthalin. Across the river, the road leads north to " +
                        "Varrock, and to the west lies Draynor Village.",
                )
            }
            4 -> {
                chatPlayer(ChatAnim.QUIZ, "Can you tell me how long I've been here?")
                age(nowMillis)
            }
            else -> chatPlayer(ChatAnim.NEUTRAL, "Nothing.")
        }
    }

    internal suspend fun Dialogue.age(nowMillis: Long) {
        chatNpc(
            ChatAnim.NEUTRAL,
            "Ahh, I see all the newcomers arriving in Lumbridge, fresh-faced and eager for adventure. I remember you...",
        )
        chatNpc(ChatAnim.NEUTRAL, timePlayed(player, nowMillis))
    }

    /**
     * The wiki's "You've spent [amount] days, [amount] hours, [amount] minutes in the world since you arrived [amount]
     * days ago." Time played counts game ticks logged in (0.6 s each); whether live counts the same way, and how it
     * words a count of 1, isn't known.
     */
    fun timePlayed(player: Player, nowMillis: Long): String {
        val minutes = player.playedTicks * World.TICK_MILLIS / 60_000
        val days = minutes / (24 * 60)
        val hours = minutes / 60 % 24
        val daysAgo = ((nowMillis - player.createdAt) / (24 * 60 * 60 * 1000L)).coerceAtLeast(0)
        return "You've spent $days days, $hours hours, ${minutes % 60} minutes in the world since you arrived $daysAgo days ago."
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
