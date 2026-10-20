package osrsps.game.command

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player

private val logger = KotlinLogging.logger {}

/** A `::` command typed in the chatbox. */
class Command(
    val name: String,
    /** Shown when the arguments don't fit, e.g. `::tele x z [level]`. */
    val usage: String,
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

private class CommandFailure(message: String) : RuntimeException(message)

/**
 * The `::` commands, by name. The client sends what follows `::` as a ClientCheat packet; names are matched
 * without regard to case and the rest of the line is split on whitespace.
 *
 * Every player can use every command: this is a test server.
 */
class CommandRegistry {
    private val commands = sortedMapOf<String, Command>()

    val all: Collection<Command> get() = commands.values

    fun register(name: String, usage: String, run: CommandContext.() -> Unit) {
        val key = name.lowercase()
        require(key.isNotEmpty() && key.none(Char::isWhitespace)) { "Bad command name '$name'" }
        require(key !in commands) { "::$key is already registered" }
        commands[key] = Command(key, usage, run)
    }

    /** Runs the command in [input], with or without its leading `::`. Answers go to [reply]. */
    fun execute(player: Player, input: String, reply: (String) -> Unit) {
        val words = input.removePrefix("::").trim().split(WHITESPACE).filter { it.isNotEmpty() }
        if (words.isEmpty()) return
        val name = words[0].lowercase()
        val command = commands[name]
        if (command == null) {
            reply("Unknown command ::$name. Commands: ${commands.keys.joinToString { "::$it" }}")
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

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
