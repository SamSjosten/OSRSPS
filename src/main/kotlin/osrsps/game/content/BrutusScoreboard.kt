package osrsps.game.content

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import osrsps.game.FrameSlot
import osrsps.game.Player
import osrsps.game.World

private val logger = KotlinLogging.logger {}

/**
 * Brutus's records across the whole server, the scoreboard's global column (Brutus step 3b): every kill, every death in
 * a field and the fastest kill, kept in [file] (none in tests).
 */
class BrutusRecords(private val file: Path? = null) {
    var kills = 0L
        private set
    var deaths = 0L
        private set
    /** The fastest kill in ticks; 0 for none yet. */
    var bestTicks = 0L
        private set

    init {
        if (file != null && Files.exists(file)) {
            try {
                val root = Json.parseToJsonElement(Files.readString(file)).jsonObject
                kills = root["kills"]?.jsonPrimitive?.long ?: 0
                deaths = root["deaths"]?.jsonPrimitive?.long ?: 0
                bestTicks = root["bestTicks"]?.jsonPrimitive?.long ?: 0
            } catch (e: Exception) {
                logger.warn(e) { "Couldn't read $file; Brutus's global records start from nothing" }
            }
        }
    }

    fun killed(ticks: Long?) {
        kills++
        if (ticks != null && ticks > 0 && (bestTicks == 0L || ticks < bestTicks)) bestTicks = ticks
        save()
    }

    fun died() {
        deaths++
        save()
    }

    private fun save() {
        val file = file ?: return
        try {
            val json = buildJsonObject {
                put("kills", kills)
                put("deaths", deaths)
                put("bestTicks", bestTicks)
            }
            val temp = file.resolveSibling("${file.fileName}.tmp")
            Files.writeString(temp, Json.encodeToString(JsonObject.serializer(), json))
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            logger.warn(e) { "Couldn't save Brutus's global records to $file" }
        }
    }

    companion object {
        const val FILE_NAME = "brutus-records.json"
    }
}

/**
 * The scoreboard west of Brutus's pen (Brutus step 3b; the OSRS Wiki's Scoreboard (Brutus), read 2026-11-22; ids and
 * components from the 242 cache): "replaces a plant upon killing him once" (`cowboss_scoreboard_multi` 60766 by
 * `cowboss_scoreboards`, varbit 20090: 0 the plant, 1 the scoreboard), and Read shows "the player's killcount, amount of
 * deaths, and their fastest kill, as well as the corresponding global statistics" in `cowboss_scoreboard` (950), the
 * global ones this server's ([BrutusRecords]).
 *
 * A kill's time runs from the player's first attack on him to his death (the Wiki's global best of 0:01.20 is two
 * ticks, so it can't start at his spawn). Deaths are those in a field.
 *
 * GUESSED until recorded: the time's start (the first attack), shown as m:ss.cc; the interface opened as a main modal
 * with its numbers set by text alone, with commas, and "-" for no time yet. Left out: Read (Demonic), with Demonic
 * Brutus (steps 6-7).
 */
object BrutusScoreboard {
    /** `cowboss_scoreboard_multi`, the loc on the map: the client sends its id whichever form it shows. */
    const val SCOREBOARD_MULTI = 60766
    const val SCOREBOARD = 60767
    const val SCOREBOARD_DEMONIC = 60768
    const val READ = 1

    /** `cowboss_scoreboards`: 1 once the player has killed Brutus (the scoreboard in place of the plant). */
    const val SCOREBOARDS_VARBIT = 20090

    const val INTERFACE = 950
    const val PERSONAL_KILLS = 8
    const val PERSONAL_DEATHS = 10
    const val PERSONAL_BEST = 12
    const val GLOBAL_KILLS = 14
    const val GLOBAL_DEATHS = 16
    const val GLOBAL_BEST = 18

    // Guessed: what the board shows before any time is set.
    const val NO_TIME = "-"

    /** [ticks] as the scoreboard shows a time: minutes, seconds and hundredths, 2 ticks being 0:01.20. */
    fun time(ticks: Long): String {
        if (ticks <= 0) return NO_TIME
        // Guessed: m:ss.cc, from the Wiki's 0:01.20 for two ticks.
        val centis = ticks * 60
        val minutes = centis / 6_000
        val seconds = centis / 100 % 60
        return "%d:%02d.%02d".format(minutes, seconds, centis % 100)
    }

    // Guessed: numbers with commas, set as text.
    private fun number(value: Long) = "%,d".format(value)

    /** A kill of Brutus by [player], [ticks] after their first attack (null when unknown). */
    fun killed(world: World, player: Player, ticks: Long?) {
        world.brutusRecords.killed(ticks)
        val best = world.customVars.brutusBestVarp
        if (ticks != null && ticks > 0 && best < player.vars.types.varpCount) {
            val had = player.vars[best]
            if (had == 0 || ticks < had) player.vars[best] = ticks.toInt()
        }
        val types = player.vars.types
        if (types.varbit(SCOREBOARDS_VARBIT) != null && player.vars.getVarbit(SCOREBOARDS_VARBIT) == 0) player.vars.setVarbit(SCOREBOARDS_VARBIT, 1)
    }

    /** [player] died in a field. Guessed: kept in our own varp, live's isn't known. */
    fun died(world: World, player: Player) {
        world.brutusRecords.died()
        val deaths = world.customVars.brutusDeathsVarp
        if (deaths < player.vars.types.varpCount) player.vars[deaths] = player.vars[deaths] + 1
    }

    /** Read: the player's numbers and the server's. */
    fun read(world: World, player: Player) {
        val records = world.brutusRecords
        fun varp(id: Int) = if (id < player.vars.types.varpCount) player.vars[id].toLong() else 0L
        // Guessed: opened as a main modal, its numbers set by text alone.
        world.interfaces.openModal(player, FrameSlot.MAINMODAL, INTERFACE)
        val texts = listOf(
            PERSONAL_KILLS to number(varp(Brutus.KILL_COUNT_VARP)),
            PERSONAL_DEATHS to number(varp(world.customVars.brutusDeathsVarp)),
            PERSONAL_BEST to time(varp(world.customVars.brutusBestVarp)),
            GLOBAL_KILLS to number(records.kills),
            GLOBAL_DEATHS to number(records.deaths),
            GLOBAL_BEST to time(records.bestTicks),
        )
        for ((component, text) in texts) world.interfaces.setText(player, INTERFACE, component, text)
    }
}

fun registerBrutusScoreboard(world: World) {
    for (id in listOf(BrutusScoreboard.SCOREBOARD_MULTI, BrutusScoreboard.SCOREBOARD, BrutusScoreboard.SCOREBOARD_DEMONIC)) {
        world.handlers.onLoc(id, BrutusScoreboard.READ) {
            arriveDelay()
            BrutusScoreboard.read(world, player)
        }
    }
}
