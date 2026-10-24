package osrsps.testclient

import io.netty.buffer.Unpooled
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import net.rsprot.buffer.JagByteBuf
import net.rsprot.buffer.extensions.toJagByteBuf
import net.rsprot.crypto.cipher.IsaacRandom
import net.rsprot.protocol.Prot
import net.rsprot.protocol.game.incoming.prot.GameClientProt
import net.rsprot.protocol.game.outgoing.prot.GameServerProt

/** One packet from the server, with its payload as sent. */
class ServerPacket(val prot: GameServerProt, val payload: ByteArray) {
    override fun toString() = "$prot (${payload.size} bytes)"
}

/** A game message the server sent (MESSAGE_GAME): the chatbox line and its type, 0 for a plain game message. */
data class GameMessage(val type: Int, val text: String, val name: String? = null)

/** A move of the local player, with the number of ticks the client had seen end before it. */
data class TickMove(val tick: Int, val move: PlayerMove)

class LoginFailedException(val code: Int) : Exception("Login refused with response code $code")

/**
 * A scripted game client for tests: logs in over a real socket the way the desktop client does (JS5 CRCs, RSA and
 * XTEA login block, ISAAC opcodes), then sends clicks and commands and keeps what the server sent back. Only ever
 * points at our own server.
 *
 * Packets are read on a background thread. Every getter returns a snapshot; [await] waits for a condition on the
 * client's state and fails the test if the server doesn't get there in time.
 */
class HeadlessClient private constructor(
    private val socket: Socket,
    private val encoder: IsaacRandom,
    private val decoder: IsaacRandom,
    /** The player's index, from the login response. */
    val index: Int,
) : AutoCloseable {
    private val input = DataInputStream(socket.getInputStream().buffered())
    private val output = DataOutputStream(socket.getOutputStream())
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val playerInfo = PlayerInfoReader(index)
    private var rebuilt = false

    private val packets = ArrayList<ServerPacket>()
    private val messages = ArrayList<GameMessage>()
    private val moves = ArrayList<TickMove>()
    private var ticks = 0
    private var failure: Throwable? = null
    private var closed = false

    private val reader = Thread(::readLoop, "headless-client-$index").apply {
        isDaemon = true
        start()
    }

    /** Where the server last put the local player; null until the login rebuild has arrived. */
    val position: Coord? get() = lock.withLock { if (rebuilt) playerInfo.coord(index) else null }

    /** How many SERVER_TICK_END packets have arrived: one per server tick since login. */
    val ticksSeen: Int get() = lock.withLock { ticks }

    fun packets(): List<ServerPacket> = lock.withLock { packets.toList() }

    fun messages(): List<GameMessage> = lock.withLock { messages.toList() }

    /** The local player's moves, in order, each with the tick it arrived in. */
    fun moves(): List<TickMove> = lock.withLock { moves.toList() }

    /** The other players in view and where they are. */
    fun otherPlayers(): Map<Int, Coord> = lock.withLock { playerInfo.others() }

    /** A click on the game view at [x], [z], as MOVE_GAMECLICK. [keyCombination] 1 is ctrl, 2 ctrl and shift. */
    fun walk(x: Int, z: Int, keyCombination: Int = 0) = send(GameClientProt.MOVE_GAMECLICK) {
        p1Alt1(keyCombination)
        p2Alt2(z)
        p2Alt2(x)
    }

    /** A `::` command typed in the chatbox, without the colons. */
    fun command(text: String) = send(GameClientProt.CLIENT_CHEAT) {
        pjstr(text)
        p1(0)
    }

    /** Sends a packet built by [payload]. For packets this class has no method for yet. */
    fun send(prot: GameClientProt, payload: JagByteBuf.() -> Unit) {
        val body = Unpooled.buffer().toJagByteBuf().apply(payload).bytes()
        when (prot.size) {
            Prot.VAR_BYTE -> require(body.size <= 0xFF) { "$prot payload too long: ${body.size}" }
            Prot.VAR_SHORT -> require(body.size <= 0xFFFF) { "$prot payload too long: ${body.size}" }
            else -> require(body.size == prot.size) { "$prot takes ${prot.size} bytes, got ${body.size}" }
        }
        synchronized(output) {
            output.writeByte((prot.opcode + encoder.nextInt()) and 0xFF)
            when (prot.size) {
                Prot.VAR_BYTE -> output.writeByte(body.size)
                Prot.VAR_SHORT -> output.writeShort(body.size)
            }
            output.write(body)
            output.flush()
        }
    }

    /**
     * Waits until [condition] returns non-null and returns it. Fails with [description] after [timeoutMillis], or as
     * soon as the connection breaks.
     */
    fun <T : Any> await(description: String, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS, condition: () -> T?): T {
        var left = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        lock.withLock {
            while (true) {
                condition()?.let { return it }
                failure?.let { throw AssertionError("Connection failed while waiting for $description", it) }
                if (closed) throw AssertionError("Connection closed while waiting for $description")
                if (left <= 0) throw AssertionError("Timed out after $timeoutMillis ms waiting for $description")
                left = changed.awaitNanos(left)
            }
        }
    }

    /** Waits for [count] more server ticks to end. */
    fun awaitTicks(count: Int, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS) {
        val target = ticksSeen + count
        await("tick $target", timeoutMillis) { if (ticks >= target) Unit else null }
    }

    /** Waits for a game message matching [predicate] that arrives after this call, and returns it. */
    fun awaitMessage(description: String, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS, predicate: (String) -> Boolean): GameMessage {
        val from = lock.withLock { messages.size }
        return await("message $description", timeoutMillis) { messages.drop(from).firstOrNull { predicate(it.text) } }
    }

    /** Sends a command and returns the first game message after it, for commands that answer with one line. */
    fun commandReply(text: String, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS): String {
        val from = lock.withLock { messages.size }
        command(text)
        return await("a reply to ::$text", timeoutMillis) { messages.getOrNull(from)?.text }
    }

    /** Disconnects. Throws if reading the server's packets failed at any point, so a decode error can't go unseen. */
    override fun close() {
        lock.withLock { closed = true }
        socket.close()
        reader.join(2000)
        lock.withLock { failure }?.let { throw AssertionError("Reading the server's packets failed", it) }
    }

    private fun readLoop() {
        try {
            while (true) {
                val packet = readPacket() ?: break
                lock.withLock {
                    packets += packet
                    handle(packet)
                    changed.signalAll()
                }
            }
        } catch (e: Throwable) {
            lock.withLock {
                if (!closed) failure = e
            }
        } finally {
            lock.withLock {
                closed = true
                changed.signalAll()
            }
        }
    }

    private fun readPacket(): ServerPacket? {
        val first = try {
            input.readUnsignedByte()
        } catch (_: EOFException) {
            return null
        }
        var opcode = (first - decoder.nextInt()) and 0xFF
        if (opcode >= 0x80) opcode = ((opcode - 0x80) shl 8) or ((input.readUnsignedByte() - decoder.nextInt()) and 0xFF)
        val prot = SERVER_PROTS[opcode] ?: error("Unknown server opcode $opcode after ${packets.lastOrNull()}")
        val size = when (prot.size) {
            Prot.VAR_BYTE -> input.readUnsignedByte()
            Prot.VAR_SHORT -> input.readUnsignedShort()
            else -> prot.size
        }
        return ServerPacket(prot, ByteArray(size).also(input::readFully))
    }

    private fun handle(packet: ServerPacket) {
        val buf = Unpooled.wrappedBuffer(packet.payload).toJagByteBuf()
        when (packet.prot) {
            // RSProt sends REBUILD_LOGIN under REBUILD_NORMAL's opcode, with the player info init block in front.
            GameServerProt.REBUILD_NORMAL_V2 -> if (!rebuilt) {
                playerInfo.init(packet.payload)
                rebuilt = true
            }
            GameServerProt.PLAYER_INFO -> {
                for (move in playerInfo.read(packet.payload)) {
                    if (move.index == index) moves += TickMove(ticks, move)
                }
            }
            GameServerProt.MESSAGE_GAME -> {
                val type = buf.gSmart1or2()
                val name = if (buf.g1() == 1) buf.gjstr() else null
                messages += GameMessage(type, buf.gjstr(), name)
            }
            GameServerProt.SERVER_TICK_END -> ticks++
            else -> {}
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 10_000L

        private const val INIT_GAME_CONNECTION = 14
        private const val GAMELOGIN = 16
        private const val LOGIN_SUCCESSFUL = 0
        private const val LOGIN_OK = 2

        private val SERVER_PROTS: Map<Int, GameServerProt> = GameServerProt.entries.associateBy { it.opcode }

        /**
         * Logs in as [username] and returns once the server has accepted the login. The JS5 CRCs are fetched from the
         * same port first. [rsaModulus] and [rsaExponent] are the server's public key.
         */
        fun login(
            host: String,
            port: Int,
            rsaModulus: BigInteger,
            rsaExponent: BigInteger,
            username: String,
            password: String = "test",
            timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS.toInt(),
        ): HeadlessClient {
            val crcs = Js5Crcs.fetch(host, port, timeoutMillis)
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(host, port), timeoutMillis)
                socket.soTimeout = timeoutMillis
                val out = DataOutputStream(socket.getOutputStream())
                val input = DataInputStream(socket.getInputStream())
                out.writeByte(INIT_GAME_CONNECTION)
                out.flush()
                val status = input.readUnsignedByte()
                if (status != LOGIN_SUCCESSFUL) throw LoginFailedException(status)
                val sessionId = input.readLong()

                val random = SecureRandom()
                val seed = IntArray(4) { random.nextInt() }
                val block = LoginBlockWriter.write(username, password, sessionId, crcs, rsaModulus, rsaExponent, seed)
                out.writeByte(GAMELOGIN)
                out.writeShort(block.size)
                out.write(block)
                out.flush()

                val response = input.readUnsignedByte()
                if (response != LOGIN_OK) throw LoginFailedException(response)
                input.readUnsignedByte() // RSProt always writes 37 here, whatever the length
                val encoder = IsaacRandom(seed)
                val decoder = IsaacRandom(IntArray(4) { seed[it] + 50 })
                val authenticator = input.readUnsignedByte()
                if (authenticator == 1) repeat(4) { decoder.nextInt(); input.readUnsignedByte() } else input.readInt()
                input.readUnsignedByte() // staff mod level
                input.readUnsignedByte() // player mod
                val index = input.readUnsignedShort()
                input.readUnsignedByte() // member
                repeat(3) { input.readLong() } // account hash, user id, user hash
                // Game packets come in at the server's pace; the reader waits as long as it has to.
                socket.soTimeout = 0
                return HeadlessClient(socket, encoder, decoder, index)
            } catch (e: Throwable) {
                socket.close()
                throw e
            }
        }
    }
}
