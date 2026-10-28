package osrsps.net

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.InetAddress

/** PROXY protocol headers written byte by byte from the spec, as HAProxy's send-proxy-v2 sends them, for tests. */
object ProxyHeaders {
    private val V2_SIGNATURE = byteArrayOf(0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A)

    /** A v2 PROXY command for a TCP connection over IPv4 from [source]:[sourcePort] to [destination]:[destinationPort]. */
    fun v2(source: String, sourcePort: Int, destination: String = "192.168.0.210", destinationPort: Int = 43594): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.write(V2_SIGNATURE)
            out.writeByte(0x21) // version 2, PROXY
            out.writeByte(0x11) // AF_INET, STREAM
            out.writeShort(12) // two addresses and two ports
            out.write(InetAddress.getByName(source).address)
            out.write(InetAddress.getByName(destination).address)
            out.writeShort(sourcePort)
            out.writeShort(destinationPort)
        }
        return bytes.toByteArray()
    }

    /** A v1 (text) header, which HAProxy's send-proxy sends. */
    fun v1(source: String, sourcePort: Int, destination: String = "192.168.0.210", destinationPort: Int = 43594): ByteArray =
        "PROXY TCP4 $source $destination $sourcePort $destinationPort\r\n".toByteArray(Charsets.US_ASCII)
}
