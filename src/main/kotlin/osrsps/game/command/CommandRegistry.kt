package osrsps.game.command

import io.github.oshai.kotlinlogging.KotlinLogging
import osrsps.game.Player

private val logger = KotlinLogging.logger {}

/** A `::` command typed in the chatbox. */
class Command(
    val name: String,
    /** Shown when the arguments don't fit, e.g. `::tele x z [level]`. */
    val usage: String,
    /** Only [Admins] may run it: it changes the world for everyone, not just the player who typed it. */
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
 * Every player can run the commands that only touch themselves; the ones marked admin need [isAdmin].
 */
class CommandRegistry(private val isAdmin: (Player) -> Boolean = { true }) {
    private val commands = sortedMapOf<String, Command>()

    val all: Collection<Command> get() = commands.values

    fun register(name: String, usage: String, admin: Boolean = false, run: CommandContext.() -> Unit) {
        val key = name.lowercase()
        require(key.isNotEmpty() && key.none(Char::isWhitespace)) { "Bad command name '$name'" }
        require(key !in commands) { "::$key is already registered" }
        commands[key] = Command(key, usage, admin, run)
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
            reply("::$name changes the world for everyone, so only admins can use it here.")
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
