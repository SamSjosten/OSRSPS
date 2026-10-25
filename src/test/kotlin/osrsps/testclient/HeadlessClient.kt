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
import osrsps.game.dialogue.Chatbox

/** One packet from the server, with its payload as sent. */
class ServerPacket(val prot: GameServerProt, val payload: ByteArray) {
    override fun toString() = "$prot (${payload.size} bytes)"
}

/** A game message the server sent (MESSAGE_GAME): the chatbox line and its type, 0 for a plain game message. */
data class GameMessage(
    val type: Int,
    val text: String,
    val name: String? = null,
    /** How many SERVER_TICK_END packets had arrived before it: the tick it came in ends with tick end [tick] + 1. */
    val tick: Int = 0,
)

/** A move of the local player, with the number of ticks the client had seen end before it. */
data class TickMove(val tick: Int, val move: PlayerMove)

/** One inventory slot's item. */
data class InvObj(val id: Int, val count: Int)

/**
 * A skill as UPDATE_STAT_V2 sends it: the level shown (with boosts), the XP, and the "invisible boosted" level the
 * client keeps for hidden checks. The base level isn't sent; the client works it out from the XP.
 */
data class Stat(val level: Int, val xp: Int, val invisibleLevel: Int)

/** A client script the server ran (RUNCLIENTSCRIPT), with its arguments in the script's order. */
data class ClientScript(val id: Int, val args: List<Any>)

/** What the chatbox dialogue shows. */
sealed interface DialogueView {
    /** An NPC (chat_left) or the player (chat_right) talking. */
    data class Chat(val interfaceId: Int, val name: String, val text: String) : DialogueView

    /** Select an option: the options in order, option 1 first. */
    data class Menu(val title: String, val options: List<String>) : DialogueView

    /** Plain text with no head. */
    data class Message(val text: String) : DialogueView

    /** Some other interface in the chatbox, by id. */
    data class Other(val interfaceId: Int) : DialogueView
}

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
    private val npcInfo = NpcInfoReader()
    private val zones = ZoneReader()

    /** Each open sub-interface by the component it is open in (interface id shl 16 or component), plus the top level. */
    private val subs = HashMap<Int, Int>()
    private var topLevel = -1
    private val texts = HashMap<Int, String>()
    private val scripts = ArrayList<ClientScript>()
    private var dialogue: DialogueView? = null
    private val invs = HashMap<Int, MutableList<InvObj?>>()
    private val stats = HashMap<Int, Stat>()
    private val varps = HashMap<Int, Int>()
    private var runEnergy = -1

    /** Counts changes of [dialogue]; [dialogueMark] is its value when the last packet was sent. */
    private var dialogueGeneration = 0
    private var dialogueMark = 0
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

    /** The NPCs in view, in the order the server keeps them. */
    fun npcs(): List<NpcView> = lock.withLock { npcInfo.npcs() }

    /** The top-level interface and every interface open in another's component. */
    fun openInterfaces(): Set<Int> = lock.withLock { subs.values.toSet() + topLevel }

    /** The text last set on a component (IF_SETTEXT), or null if none has been. */
    fun text(interfaceId: Int, componentId: Int): String? = lock.withLock { texts[(interfaceId shl 16) or componentId] }

    fun clientScripts(): List<ClientScript> = lock.withLock { scripts.toList() }

    /**
     * Inventory [inventoryId]'s slots (93 is the backpack, 94 worn items), null for an empty slot; empty if never sent.
     * Only as long as the server sent it: our server's full update stops at the last filled slot.
     */
    fun inventory(inventoryId: Int): List<InvObj?> = lock.withLock { invs[inventoryId]?.toList().orEmpty() }

    /** Skill [skill] (0 Attack, 1 Defence, 2 Strength, 3 Hitpoints...), or null if the server hasn't sent it. */
    fun stat(skill: Int): Stat? = lock.withLock { stats[skill] }

    /** Varp [id] as the server last set it; 0, as in the client, if it never has. */
    fun varp(id: Int): Int = lock.withLock { varps[id] ?: 0 }

    /** The ground objs in the build area, from zone updates. */
    fun groundObjs(): List<GroundObjView> = lock.withLock { zones.groundObjs() }

    /** Locs the server added, changed or removed in the build area; the cache's own locs aren't known here. */
    fun locChanges(): List<LocChange> = lock.withLock { zones.locChanges() }

    /** Run energy in hundredths of a percent (10000 is full), or -1 before the server sends it. */
    val runEnergyHundredths: Int get() = lock.withLock { runEnergy }

    /**
     * What the chatbox dialogue showed at the end of the last server tick, or null if none was open. Read at tick ends
     * so a page is never seen half-sent (opened, but its text not set yet).
     */
    val dialogueView: DialogueView? get() = lock.withLock { dialogue }

    /** Op [op] (1-5) on the NPC at [index], as the menu's OPNPC1-5. */
    fun npcOp(index: Int, op: Int, ctrl: Boolean = false, subop: Int = 0) {
        val key = if (ctrl) 1 else 0
        when (op) {
            1 -> send(GameClientProt.OPNPC1_V2) { p1Alt1(subop); p2Alt3(index); p1Alt2(key) }
            2 -> send(GameClientProt.OPNPC2_V2) { p1Alt1(key); p1Alt3(subop); p2Alt3(index) }
            3 -> send(GameClientProt.OPNPC3_V2) { p2Alt1(index); p1(subop); p1Alt1(key) }
            4 -> send(GameClientProt.OPNPC4_V2) { p1Alt3(key); p1Alt3(subop); p2Alt1(index) }
            5 -> send(GameClientProt.OPNPC5_V2) { p2Alt2(index); p1Alt1(key); p1Alt2(subop) }
            else -> throw IllegalArgumentException("NPC ops are 1-5, not $op")
        }
    }

    /** Examine on NPC type [id], as OPNPC6. */
    fun examineNpc(id: Int) = send(GameClientProt.OPNPC6) { p2(id) }

    /** Examine on loc type [id], as OPLOC6. */
    fun examineLoc(id: Int) = send(GameClientProt.OPLOC6) { p2Alt3(id) }

    /** Op [op] (1-5) on loc [id] whose south-west tile is [x], [z], as OPLOC1-5. */
    fun locOp(id: Int, x: Int, z: Int, op: Int, ctrl: Boolean = false, subop: Int = 0) {
        val key = if (ctrl) 1 else 0
        when (op) {
            1 -> send(GameClientProt.OPLOC1_V2) { p2Alt1(z); p2Alt3(id); p1(subop); p2(x); p1Alt3(key) }
            2 -> send(GameClientProt.OPLOC2_V2) { p1Alt3(key); p2(z); p2Alt2(id); p1Alt2(subop); p2(x) }
            3 -> send(GameClientProt.OPLOC3_V2) { p2Alt1(z); p2Alt1(x); p1Alt2(subop); p2Alt3(id); p1Alt1(key) }
            4 -> send(GameClientProt.OPLOC4_V2) { p2Alt2(id); p1Alt1(subop); p1Alt3(key); p2Alt1(x); p2Alt1(z) }
            5 -> send(GameClientProt.OPLOC5_V2) { p2Alt3(z); p2Alt1(id); p1(subop); p1Alt1(key); p2Alt2(x) }
            else -> throw IllegalArgumentException("Loc ops are 1-5, not $op")
        }
    }

    /** Op [op] (1-5; 3 is Take) on ground obj [id] at [x], [z] (OPOBJ1_V2 to OPOBJ5_V2). */
    fun objOp(id: Int, x: Int, z: Int, op: Int, ctrl: Boolean = false, subop: Int = 0) {
        val key = if (ctrl) 1 else 0
        when (op) {
            1 -> send(GameClientProt.OPOBJ1_V2) { p1(subop); p2(x); p2(id); p1(key); p2Alt3(z) }
            2 -> send(GameClientProt.OPOBJ2_V2) { p2Alt2(x); p2(id); p1Alt2(subop); p2Alt2(z); p1Alt1(key) }
            3 -> send(GameClientProt.OPOBJ3_V2) { p1(subop); p2(x); p2(id); p2Alt1(z); p1Alt3(key) }
            4 -> send(GameClientProt.OPOBJ4_V2) { p2Alt1(z); p2(x); p1Alt2(key); p1Alt1(subop); p2Alt2(id) }
            5 -> send(GameClientProt.OPOBJ5_V2) { p2Alt3(id); p2Alt1(z); p1Alt3(key); p2Alt2(x); p1Alt3(subop) }
            else -> throw IllegalArgumentException("Obj ops are 1-5, not $op")
        }
    }

    /** A click on a pause button (RESUME_PAUSEBUTTON): a dialogue's continue, or a menu option as [sub]. */
    fun resumePause(interfaceId: Int, componentId: Int, sub: Int = -1) = send(GameClientProt.RESUME_PAUSEBUTTON) {
        p2Alt1(sub and 0xFFFF)
        p4Alt1((interfaceId shl 16) or componentId)
    }

    /** Clicks "Click here to continue" on the dialogue showing now. */
    fun continueDialogue() {
        when (val view = dialogueView) {
            is DialogueView.Chat -> resumePause(view.interfaceId, Chatbox.CHAT_CONTINUE)
            is DialogueView.Message -> resumePause(Chatbox.MESSAGEBOX, Chatbox.MESSAGEBOX_CONTINUE)
            else -> throw IllegalStateException("No dialogue page to continue: $view")
        }
    }

    /** Picks option [option] (1 for the first) of the menu showing now. */
    fun chooseOption(option: Int) {
        val view = dialogueView
        check(view is DialogueView.Menu && option in 1..view.options.size) { "No option $option to pick in $view" }
        resumePause(Chatbox.CHATMENU, Chatbox.CHATMENU_OPTIONS, option)
    }

    /**
     * Waits for the dialogue to change after the last packet this client sent (a click, a continue, a pick) and match
     * [predicate]. A change that arrived before this is called still counts, as long as it came after the send.
     */
    fun awaitDialogue(description: String, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS, predicate: (DialogueView) -> Boolean = { true }): DialogueView {
        val mark = lock.withLock { dialogueMark }
        return await("dialogue $description", timeoutMillis) {
            dialogue?.takeIf { dialogueGeneration > mark && predicate(it) }
        }
    }

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
            lock.withLock { dialogueMark = dialogueGeneration }
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

    /**
     * Sends a command and returns the first game message after it that [isReply] accepts. Messages carry no link to
     * the command that caused them, and others (the welcome message, content) can arrive in between, so the caller
     * says what the reply looks like.
     */
    fun commandReply(text: String, timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS, isReply: (String) -> Boolean): String {
        val from = lock.withLock { messages.size }
        command(text)
        return await("a reply to ::$text", timeoutMillis) { messages.drop(from).firstOrNull { isReply(it.text) }?.text }
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
            GameServerProt.REBUILD_NORMAL_V2 -> {
                if (!rebuilt) {
                    buf.skipRead(playerInfo.init(packet.payload))
                    rebuilt = true
                }
                val zoneZ = buf.g2()
                zones.rebuild(centreX = buf.g2Alt1(), centreZ = zoneZ)
            }
            GameServerProt.PLAYER_INFO -> {
                for (move in playerInfo.read(packet.payload)) {
                    if (move.index == index) moves += TickMove(ticks, move)
                }
            }
            GameServerProt.MESSAGE_GAME -> {
                val type = buf.gSmart1or2()
                val name = if (buf.g1() == 1) buf.gjstr() else null
                messages += GameMessage(type, buf.gjstr(), name, ticks)
            }
            GameServerProt.NPC_INFO_SMALL_V6, GameServerProt.NPC_INFO_LARGE_V6 -> npcInfo.read(
                packet.payload,
                large = packet.prot == GameServerProt.NPC_INFO_LARGE_V6,
                local = playerInfo.coord(index) ?: error("NPC info before the login rebuild"),
            )
            GameServerProt.IF_OPENTOP -> topLevel = buf.g2Alt2()
            GameServerProt.IF_OPENSUB -> {
                val destination = buf.g4Alt3()
                subs[destination] = buf.g2Alt2()
            }
            GameServerProt.IF_CLOSESUB -> subs.remove(buf.g4())
            GameServerProt.IF_MOVESUB -> {
                val from = buf.g4Alt1()
                val to = buf.g4Alt1()
                subs.remove(from)?.let { subs[to] = it }
            }
            GameServerProt.IF_SETTEXT -> {
                val component = buf.g4()
                texts[component] = buf.gjstr()
            }
            GameServerProt.RUNCLIENTSCRIPT -> scripts += readClientScript(buf)
            GameServerProt.UPDATE_INV_FULL -> {
                buf.g4() // the interface component it is shown on, or -1
                val inventoryId = buf.g2()
                invs[inventoryId] = MutableList(buf.g2()) {
                    val id = buf.g2Alt1() - 1
                    var count = buf.g1Alt3()
                    if (count == 0xFF) count = buf.g4Alt2()
                    if (id < 0) null else InvObj(id, count)
                }
            }
            GameServerProt.UPDATE_INV_PARTIAL -> {
                buf.g4()
                val slots = invs.getOrPut(buf.g2()) { ArrayList() }
                while (buf.isReadable) {
                    val slot = buf.gSmart1or2()
                    val id = buf.g2() - 1
                    val obj = if (id < 0) {
                        null
                    } else {
                        var count = buf.g1()
                        if (count == 0xFF) count = buf.g4()
                        InvObj(id, count)
                    }
                    while (slots.size <= slot) slots += null
                    slots[slot] = obj
                }
            }
            GameServerProt.UPDATE_STAT_V2 -> {
                val xp = buf.g4Alt1()
                val invisibleLevel = buf.g1Alt1()
                val skill = buf.g1Alt2()
                stats[skill] = Stat(buf.g1Alt2(), xp, invisibleLevel)
            }
            GameServerProt.VARP_SMALL -> {
                val value = buf.g1sAlt3()
                varps[buf.g2Alt1()] = value
            }
            GameServerProt.VARP_LARGE -> {
                val id = buf.g2Alt3()
                varps[id] = buf.g4Alt3()
            }
            GameServerProt.VARP_RESET -> varps.clear()
            GameServerProt.UPDATE_RUNENERGY -> runEnergy = buf.g2()
            GameServerProt.SERVER_TICK_END -> {
                ticks++
                val view = readDialogue()
                if (view != dialogue) {
                    dialogue = view
                    dialogueGeneration++
                }
            }
            else -> zones.read(packet.prot, packet.payload)
        }
    }

    private fun readClientScript(buf: JagByteBuf): ClientScript {
        val types = buf.gjstr()
        val args = arrayOfNulls<Any>(types.length)
        for (i in types.indices.reversed()) {
            args[i] = when (types[i]) {
                's' -> buf.gjstr()
                'Ï' -> buf.g8()
                'W' -> IntArray(buf.gVarInt2()) { buf.gVarInt2s() }.toList()
                'X' -> List(buf.gVarInt2()) { buf.gjstr() }
                else -> buf.g4()
            }
        }
        return ClientScript(buf.g4(), args.map { it!! })
    }

    private fun readDialogue(): DialogueView? {
        val open = subs[(Chatbox.CHATBOX shl 16) or Chatbox.CHATMODAL] ?: return null
        fun text(component: Int) = texts[(open shl 16) or component].orEmpty()
        return when (open) {
            Chatbox.CHAT_LEFT, Chatbox.CHAT_RIGHT -> DialogueView.Chat(open, text(Chatbox.CHAT_NAME), text(Chatbox.CHAT_TEXT))
            Chatbox.MESSAGEBOX -> DialogueView.Message(text(Chatbox.MESSAGEBOX_TEXT))
            Chatbox.CHATMENU -> {
                val init = scripts.lastOrNull { it.id == Chatbox.MULTI_INIT_SCRIPT }
                    ?: return DialogueView.Other(open)
                DialogueView.Menu(init.args[0] as String, (init.args[1] as String).split('|'))
            }
            else -> DialogueView.Other(open)
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
