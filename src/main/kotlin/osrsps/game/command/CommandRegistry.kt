package osrsps.game.command

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player

private val logger = KotlinLogging.logger {}

/** A `::` command typed in the chatbox. */
class Command(
    val name: String,
    /** Shown when the arguments don't fit, e.g. `::tele x z [level]`. */
    val usage: String,
    /** Only [Admins] may run it. Every command is, except the ones that only read or only show the player something. */
    val admin: Boolean,
    val run: CommandContext.() -> Unit,
)

/** What a command sees: who typed it, the words after its name, and a way to answer in their chatbox. */
class CommandContext(
    val player: Player,
    val args: List<String>,
    private val output: (String) -> Unit,
) {
    fun reply(message: String) = output(message)

    /** Stops the command and tells the player [message]. */
    fun fail(message: String): Nothing = throw CommandFailure(message)
}

internal class CommandFailure(message: String) : RuntimeException(message)

/**
 * The `::` commands, by name. The client sends what follows `::` as a ClientCheat packet; names are matched
 * without regard to case and the rest of the line is split on whitespace.
 *
 * Commands are for admins ([isAdmin]) unless registered with `admin = false`, which is for the ones that only read the
 * world or only show the typing player something (`::pos`, `::combat`, `::sound`). Commands that give items, XP or
 * levels, set vars, open interfaces or move the player are admin ones even though they touch only the player who types
 * them: in a non-admin's hands they make items and skip the game's rules (`::item`, `::scenario` then `::reset`).
 *
 * A dead player can run only the non-admin ones: anything else could move the body or the items before the grave
 * takes them.
 */
class CommandRegistry(private val isAdmin: (Player) -> Boolean = { true }) {
    private val commands = sortedMapOf<String, Command>()

    val all: Collection<Command> get() = commands.values

    fun register(name: String, usage: String, admin: Boolean = true, run: CommandContext.() -> Unit) {
        val key = name.lowercase()
        require(key.isNotEmpty() && key.none(Char::isWhitespace)) { "Bad command name '$name'" }
        require(key !in commands) { "::$key is already registered" }
        require(CLIENT_PREFIXES.none { key.startsWith(it) }) { "::$key never reaches the server: the client keeps lines starting ::${CLIENT_PREFIXES.first { key.startsWith(it) }}" }
        commands[key] = Command(key, usage, admin, run)
    }

    /** Whether [input] names an admin command (one that can change the world), with or without its leading `::`. */
    fun isAdminCommand(input: String): Boolean {
        val name = input.removePrefix("::").trim().split(WHITESPACE).firstOrNull()?.lowercase() ?: return false
        return commands[name]?.admin == true
    }

        /** Runs the command in [input], with or without its leading `::`. Answers go to [reply]. */
    fun execute(player: Player, input: String, reply: (String) -> Unit) {
        val words = input.removePrefix("::").trim().split(WHITESPACE).filter { it.isNotEmpty() }
        if (words.isEmpty()) return
        val name = words[0].lowercase()
        val admin = isAdmin(player)
        val command = commands[name]
        if (command == null) {
            val usable = commands.values.filter { admin || !it.admin }
            reply("Unknown command ::$name. Commands: ${usable.joinToString { "::${it.name}" }}")
            return
        }
        if (command.admin && !admin) {
            logger.info { "${player.username} tried ::${words.joinToString(" ")}, which is for admins" }
            reply("::$name is a testing tool, so only admins can use it here.")
            return
        }
        if (command.admin && player.dead) {
            reply("You can't use ::$name while you are dead.")
            return
        }
        logger.info { "${player.username} ran ::${words.joinToString(" ")}" }
        try {
            CommandContext(player, words.drop(1), reply).apply(command.run)
        } catch (e: CommandFailure) {
            reply(e.message ?: "Usage: ${command.usage}")
        } catch (e: Exception) {
            logger.error(e) { "::$name failed for ${player.username}" }
            reply("::$name failed: $e")
        }
    }

    companion object {
        private val WHITESPACE = Regex("\\s+")

        /**
         * Starts of `::` lines the client handles itself and never sends as a ClientCheat, so a command named with one
         * can't be run (rev 241's chatdefault_onkey, osrs-dumps 2026-10-28): `::bank` says a line in public chat instead,
         * `::toggleroof` and `::wiki` stay in the client, and a line starting with an emote's name plays that emote
         * (emote_from_chat). Matched as prefixes, as the client does.
         */
        val CLIENT_PREFIXES = listOf(
            "bank", "toggleroof", "wiki",
            "yes", "nod", "no", "shakehead", "bow", "angry", "think", "wave", "shrug", "cheer", "beckon", "laugh", "jump",
            "yawn", "dance", "jig", "spin", "headbang", "cry", "blowkiss", "panic", "raspberry", "clap", "salute",
            "goblinbow", "goblinsalute", "glassbox", "climbrope", "lean", "glasswall", "idea", "stamp", "flap", "slaphead",
            "zombiewalk", "zombiedance", "scared", "rabbithop", "bunnyhop", "skillcape", "zombiehand", "musiccape",
            "situp", "pushup", "pressup", "starjump", "jog", "run", "flex", "ash", "hypermobiledrinker", "uri",
            "smoothdance", "crazydance", "premiershield", "explore", "relic", "party", "trick", "bats", "sit", "fortis",
        )
    }
}
