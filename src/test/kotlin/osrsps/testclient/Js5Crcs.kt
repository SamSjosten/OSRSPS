package osrsps.testclient

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import osrsps.Revision

/**
 * Asks a server's JS5 for the master index (255/255) and returns each archive's CRC, as the real client does before
 * it logs in. The login block carries these CRCs and the server refuses a login whose CRCs don't match its cache.
 */
object Js5Crcs {
    private const val INIT_JS5REMOTE_CONNECTION = 15
    private const val URGENT_REQUEST = 1
    private const val MASTER = 255

    /** JS5 responses are cut into 512-byte blocks; every block after the first starts with 0xFF. */
    private const val BLOCK_LENGTH = 512

    fun fetch(host: String, port: Int, timeoutMillis: Int = 5000): IntArray = Socket().use { socket ->
        socket.connect(InetSocketAddress(host, port), timeoutMillis)
        socket.soTimeout = timeoutMillis
        val out = DataOutputStream(socket.getOutputStream())
        val input = DataInputStream(socket.getInputStream())
        out.writeByte(INIT_JS5REMOTE_CONNECTION)
        out.writeInt(Revision.MAJOR)
        repeat(4) { out.writeInt(0) }
        out.flush()
        val status = input.readUnsignedByte()
        check(status == 0) { "JS5 handshake refused with status $status" }

        out.writeByte(URGENT_REQUEST)
        out.writeByte(MASTER)
        out.writeShort(MASTER)
        out.flush()
        val container = readResponse(input, MASTER, MASTER)
        check(container[0].toInt() == 0) { "Expected the master index uncompressed, got compression ${container[0]}" }
        val length = readInt(container, 1)
        IntArray(length / 8) { readInt(container, 5 + it * 8) }
    }

    /** Reads one group's container, taking out the block separators. */
    private fun readResponse(input: DataInputStream, archive: Int, group: Int): ByteArray {
        check(input.readUnsignedByte() == archive && input.readUnsignedShort() == group) { "JS5 answered for another group" }
        var blockRemaining = BLOCK_LENGTH - 3
        fun next(): Int {
            if (blockRemaining == 0) {
                check(input.readUnsignedByte() == 0xFF) { "Missing JS5 block separator" }
                blockRemaining = BLOCK_LENGTH - 1
            }
            blockRemaining--
            return input.readUnsignedByte()
        }
        val header = ByteArray(5) { next().toByte() }
        val compressedLength = readInt(header, 1)
        val total = 5 + compressedLength + if (header[0].toInt() != 0) 4 else 0
        return ByteArray(total) { if (it < 5) header[it] else next().toByte() }
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)
}
