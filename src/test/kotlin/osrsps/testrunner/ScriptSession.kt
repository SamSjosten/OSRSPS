package osrsps.testrunner

import osrsps.testclient.HeadlessClient

/**
 * One account playing the runner's scripts through a [HeadlessClient], as the RuneLite plugin plays them through its
 * client: the same [Runner] polled every few milliseconds, told of each game tick and chat message, and logged in
 * again after a logout, as the plugin's auto login does.
 */
internal class ScriptSession(
    private val account: String,
    /** Logs a new client in as the account; throws when the server refuses. */
    private val login: (String) -> HeadlessClient,
    types: HeadlessGame.Types,
    private val clock: () -> Long = System::currentTimeMillis,
) : AutoCloseable {
    @Volatile
    private var client: HeadlessClient? = null
    private val game = HeadlessGame({ client }, account, types)
    private val runner = Runner(game, { clock() }, { ACCOUNTS })
    private var ticksSeen = 0
    private var messagesSeen = 0
    private var loginAnnounced = false
    private var nextLoginAt = 0L
    private var nextKeepAliveAt = 0L

    /**
     * Closes whatever interface a script left open (a bank, a shop), as a player would before the next script. The
     * nightly's reset couldn't on the VM; here it is one packet, and a modal left open can refuse the next commands.
     */
    fun closeInterfaces() {
        client?.takeUnless { it.isClosed }?.closeModal()
    }

    /** Plays [script] to its end and returns its result. A script stuck past [limitMillis] is stopped as a failure. */
    fun run(script: Script, trigger: String, limitMillis: Long = SCRIPT_LIMIT_MILLIS): Result {
        client?.forgetPackets()
        val deadline = clock() + limitMillis
        runner.start(script, trigger)
        while (true) {
            pump()?.let { return runner.abort(it) }
            runner.poll()?.let { return it }
            if (clock() > deadline) return runner.abort("the script ran past ${limitMillis / 1000} s")
            Thread.sleep(POLL_MILLIS)
        }
    }

    /**
     * Logs in when there is no client, then passes on what arrived since the last pump: one [Runner.gameTick] per
     * server tick and each chat message. Returns why the run can't go on, or null.
     */
    private fun pump(): String? {
        val current = client
        if (current == null || current.isClosed) {
            if (current != null) {
                client = null
                val failure = current.readFailure
                // The socket stays open after a read failure; closed, the server logs the player out as it would a client gone.
                runCatching { current.close() }
                failure?.let {
                    it.printStackTrace()
                    return "the headless client couldn't read the server's packets: $it"
                }
                // As the plugin's auto login: a few seconds on the login screen, then the account logs in again.
                nextLoginAt = clock() + RELOGIN_MILLIS
            }
            if (clock() >= nextLoginAt) {
                try {
                    client = login(account)
                    game.newLogin()
                    ticksSeen = 0
                    messagesSeen = 0
                    loginAnnounced = false
                } catch (e: Exception) {
                    // Logged, not thrown: a step that needs the login fails at its own timeout, with this in the log.
                    println("Login as $account failed, trying again in ${RELOGIN_MILLIS / 1000} s: $e")
                    nextLoginAt = clock() + RELOGIN_MILLIS
                }
            }
            return null
        }
        // The plugin hears of a login once the client is in the game: here, once the login's map rebuild arrived.
        if (!loginAnnounced && current.position != null) {
            loginAnnounced = true
            runner.loggedIn()
        }
        if (clock() >= nextKeepAliveAt) {
            current.keepAlive()
            nextKeepAliveAt = clock() + KEEP_ALIVE_MILLIS
        }
        game.noteInventoryBindings()
        val messages = current.messages()
        for (i in messagesSeen until messages.size) runner.message(messages[i].text)
        messagesSeen = messages.size
        val ticks = current.ticksSeen
        repeat(ticks - ticksSeen) { runner.gameTick() }
        ticksSeen = ticks
        return null
    }

    override fun close() {
        val current = client ?: return
        client = null
        runCatching { current.close() }
    }

    companion object {
        /** The plugin's default Test accounts: the only names a script may play. */
        val ACCOUNTS = listOf("test", "test2")

        /** The plugin polls every client cycle, 20 ms. */
        private const val POLL_MILLIS = 20L

        /** The plugin's auto login waits 3 s on the login screen. */
        private const val RELOGIN_MILLIS = 3_000L

        /** How often an idle client says it is still there. Guessed: well inside RSProt's idle timeout. */
        private const val KEEP_ALIVE_MILLIS = 5_000L

        /** As the nightly's 900 s wait for a result. */
        const val SCRIPT_LIMIT_MILLIS = 900_000L
    }
}
