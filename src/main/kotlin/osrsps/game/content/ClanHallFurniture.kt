package osrsps.game.content

import net.rsprot.protocol.game.outgoing.sound.MidiSongV2
import net.rsprot.protocol.game.outgoing.sound.SynthSound
import osrsps.game.FrameSlot
import osrsps.game.Player
import osrsps.game.World

/**
 * The clan hall's piano and dummy. Sources: the OSRS Wiki's Clan hall page, read 2026-11-19 (the piano "has three
 * right-click options": Entertain, "clicking notes on the piano are played in a group on the next tick for all players
 * in the Clan Hall to hear"; Play-quietly, the keyboard's number row (Grave, 1 to 0, Backspace) plays keys "for only the
 * player to hear, and each note is played instantly"; Background-music, "which unlocks the track Rhapsody, playing it
 * from the music player for only the player that selected it"; the dummy "can be attacked, but unlike the Dummy in
 * Varrock ... no experience is granted even if the player is below 8 Attack"), and the 242 cache: clan_piano (41651 at
 * 1756, 5463) and clan_sworddummy (41652 at 1744, 5456); the piano window clan_piano (698), whose keys and listeners
 * script clan_hall_piano (4321) sets up, and whose 24 key labels (698:31 to 44 and 47 to 56) send op 1 (Play-c and so
 * on) with each note's synth in enum_3799's structs (param_1239).
 *
 * - Play-quietly opens the window and runs clan_hall_piano: every note plays on the player's client alone.
 * - Entertain opens the same window without clan_hall_piano, so a clicked key isn't played by the client at once but
 *   sent as the label's own op; the server plays it for everyone in the hall, the player too, in that tick's packets,
 *   heard on the next tick as the Wiki says (Codex on PR 1005). Without the script, the keyboard and key highlights
 *   don't work in Entertain (inferred: the Wiki speaks only of clicking for it).
 * - Background-music plays Rhapsody (midi 692) for the player. This server has no music player or track unlocks, so
 *   nothing is unlocked.
 * - The window's close button closes it.
 * - The dummy's Attack swings as the Varrock dummies do ([TrainingDummies]), at the weapon's speed, with no experience.
 *
 * Guessed: the sounds' volume and timing, the dummy's hit line (the Varrock one) and ranged refusal.
 * Not built: Sit-on for the stools and benches (the sitting animations, 9022 and 9024, need a stance override the server
 * doesn't have), the bookcases' and weapons rack's Search, and the combat ring (which works as a player-owned house's,
 * not built either).
 */
class ClanHallFurniture(private val world: World) {
    /** Players with the piano open to entertain, not to play quietly. */
    private val entertaining = HashSet<Player>()

    fun register() {
        world.handlers.onLoc(PIANO, ENTERTAIN) {
            arriveDelay()
            open(player, entertain = true)
        }
        world.handlers.onLoc(PIANO, PLAY_QUIETLY) {
            arriveDelay()
            open(player, entertain = false)
        }
        world.handlers.onLoc(PIANO, BACKGROUND_MUSIC) {
            arriveDelay()
            player.session.queue(MidiSongV2(RHAPSODY, 0, 0, 0, 0))
        }
        world.handlers.onButton(WINDOW, CLOSE) { world.interfaces.closeModals(player) }
        for ((component, synth) in KEYS) world.handlers.onButton(WINDOW, component, 1) { play(player, synth) }
        // clan_hall_piano sets %smithing_listening, which keypress_permit then uses to hold keys for any other main
        // window; smithing_close is the only script that clears it, so it runs whenever the piano closes (inferred:
        // the live server's own close isn't seen; nightly 27 Nov, a stuck chatbox after Play-quietly).
        world.interfaces.onClose(WINDOW) {
            entertaining.remove(it)
            lastNote.remove(it)
            world.interfaces.runClientScript(it, SCRIPT_SMITHING_CLOSE, emptyList())
        }
        world.handlers.onLoc(DUMMY, ATTACK) { hitDummy(world, xp = false) }
    }

    /** Drops [player] on leaving: closing a modal doesn't run its close hook when the player is removed (Codex on PR 1005). */
    fun forget(player: Player) {
        entertaining.remove(player)
        lastNote.remove(player)
    }

    private fun open(player: Player, entertain: Boolean) {
        world.interfaces.closeModals(player)
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, WINDOW)
        if (entertain) entertaining += player else world.interfaces.runClientScript(player, SCRIPT_PIANO, emptyList())
    }

    /** The tick each entertaining player last played a note heard by the hall. */
    private val lastNote = HashMap<Player, Long>()

    /**
     * A key pressed while entertaining: the note to everyone in the hall. One a tick per player, and the hall's own
     * bounds checked for each listener rather than a search of every instance, so a client sending many key presses a
     * tick can't make the game thread walk every player and instance for each (exploit review R65C-4).
     */
    private fun play(player: Player, synth: Int) {
        if (player.modals[FrameSlot.MAINMODAL] != WINDOW || player !in entertaining) return
        if (lastNote[player] == world.tick) return
        val hall = world.instances.at(player.x, player.z) ?: return
        if (world.clanHall.clanAt(player.x, player.z) == null) return
        lastNote[player] = world.tick
        for (other in world.onlinePlayers) {
            if (hall.contains(other.x, other.z)) other.session.queue(SynthSound(synth, 1, 0))
        }
    }

    companion object {
        const val PIANO = 41651
        const val ENTERTAIN = 1
        const val PLAY_QUIETLY = 2
        const val BACKGROUND_MUSIC = 3

        const val DUMMY = 41652
        const val ATTACK = 1

        const val WINDOW = 698
        const val CLOSE = 45
        const val SCRIPT_PIANO = 4321

        /** smithing_close: sets %smithing_listening (varc 989) back to 0. */
        const val SCRIPT_SMITHING_CLOSE = 6123

        /** rhapsody. */
        const val RHAPSODY = 692

        /** Each key label (clan_piano:label_c1 and on) to its note's synth (grim_piano_c4 and on), enum_3799's order. */
        val KEYS = mapOf(
            31 to 3696, 32 to 3698, 33 to 3700, 34 to 3702, 35 to 3704, 36 to 3692, 37 to 3694,
            38 to 3697, 39 to 3699, 40 to 3701, 41 to 3703, 42 to 3705, 43 to 3693, 44 to 3695,
            47 to 5621, 48 to 5620, 49 to 5617, 50 to 5629, 51 to 5633,
            52 to 5625, 53 to 5624, 54 to 5619, 55 to 5626, 56 to 5630,
        )

        /** The names the startup check ([osrsps.cache.GameFrameCheck]) holds these ids to. */
        val CACHE_NAMES: Map<Int, Pair<String, Map<Int, String>>> = mapOf(
            WINDOW to ("clan_piano" to mapOf(CLOSE to "close_button", 31 to "label_c1", 44 to "label_b2", 47 to "label_csharp1", 56 to "label_asharp2")),
        )
    }
}
