package osrsps.testclient

import io.netty.buffer.Unpooled
import java.math.BigInteger
import net.rsprot.buffer.JagByteBuf
import net.rsprot.buffer.extensions.toJagByteBuf
import osrsps.Revision

/**
 * Builds the GAMELOGIN payload the desktop client sends: a plain header, an RSA block with the ISAAC seed and the
 * password, then an XTEA block (keyed by the seed) with the username, window, machine info and cache CRCs. Written
 * to match RSProt's login decoder (LoginBlockDecoder, GameLoginDecoder, DesktopLoginCrcDecoder), field for field.
 */
internal object LoginBlockWriter {
    private const val CLIENT_TYPE_DESKTOP = 1
    private const val OTP_NONE = 2
    private const val PASSWORD_AUTHENTICATION = 0
    private const val CRC_COUNT = 23

    fun write(
        username: String,
        password: String,
        sessionId: Long,
        crcs: IntArray,
        rsaModulus: BigInteger,
        rsaExponent: BigInteger,
        seed: IntArray,
        resizable: Boolean = false,
    ): ByteArray {
        val out = buffer()
        out.p4(Revision.MAJOR)
        out.p4(1) // sub-version; RSProt doesn't check it
        out.p4(0) // server version
        out.p1(CLIENT_TYPE_DESKTOP)
        out.p1(0) // platform
        out.p1(0) // external authenticator

        val rsa = buffer()
        rsa.p1(1) // RSA check byte
        seed.forEach(rsa::p4)
        rsa.p8(sessionId)
        rsa.p1(OTP_NONE)
        rsa.p4(0)
        rsa.p1(PASSWORD_AUTHENTICATION)
        rsa.pjstr(password)
        val encrypted = BigInteger(rsa.bytes()).modPow(rsaExponent, rsaModulus).toByteArray()
        out.p2(encrypted.size)
        out.pdata(encrypted)

        val xtea = buffer()
        xtea.pjstr(username)
        xtea.p1(if (resizable) 0x2 else 0)
        xtea.p2(765)
        xtea.p2(503)
        repeat(24) { xtea.p1(0) } // machine uuid
        xtea.pjstr("") // site settings
        xtea.p4(0) // affiliate
        xtea.p1(0) // deep links
        writeHostPlatformStats(xtea)
        xtea.p1(CLIENT_TYPE_DESKTOP)
        xtea.p4(0) // reflection checker constant
        writeCrcs(xtea, crcs.copyOf(CRC_COUNT))
        out.pdata(Xtea.encrypt(xtea.bytes(), seed))
        return out.bytes()
    }

    /** All zeros and empty strings: RSProt reads but doesn't check them. */
    private fun writeHostPlatformStats(out: JagByteBuf) {
        out.p1(9) // version
        out.p1(1) // os type
        out.p1(1) // 64 bit
        out.p2(0) // os version
        out.p1(0) // java vendor
        repeat(3) { out.p1(0) } // java version
        out.p1(1) // not an applet
        out.p2(0) // max memory
        out.p1(1) // processors
        out.p3(0) // system memory
        out.p2(0) // system speed
        repeat(4) { out.pjstr2("") } // gpu names and versions
        out.p1(0) // gpu driver month
        out.p2(0) // gpu driver year
        out.pjstr2("") // cpu manufacturer
        out.pjstr2("") // cpu brand
        out.p1(1)
        out.p1(1)
        repeat(3) { out.p4(0) } // cpu features
        out.p4(0) // cpu signature
        out.pjstr2("osrsps-headless") // client name
        out.pjstr2("") // device name
    }

    /** The CRCs in the order and byte orders DesktopLoginCrcDecoder.decodeLive reads them. */
    private fun writeCrcs(out: JagByteBuf, crc: IntArray) {
        out.p4Alt3(crc[5])
        out.p4Alt3(crc[4])
        out.p4(crc[1])
        out.p4(crc[17])
        out.p4Alt2(crc[21])
        out.p4Alt3(crc[9])
        out.p4Alt1(crc[8])
        out.p4Alt2(crc[22])
        out.p4Alt3(crc[15])
        out.p4Alt1(crc[19])
        out.p4Alt3(crc[7])
        out.p4Alt3(crc[0])
        out.p4Alt3(crc[18])
        out.p4Alt3(crc[12])
        out.p4Alt1(crc[11])
        out.p4Alt2(crc[14])
        out.p4(crc[3])
        out.p4Alt3(crc[13])
        out.p4(crc[2])
        out.p4Alt2(crc[10])
        out.p4(crc[6])
        out.p4(crc[16])
        out.p4Alt1(crc[20])
    }

    private fun buffer(): JagByteBuf = Unpooled.buffer().toJagByteBuf()

    private fun JagByteBuf.pdata(bytes: ByteArray) {
        buffer.writeBytes(bytes)
    }
}

internal fun JagByteBuf.bytes(): ByteArray = ByteArray(buffer.readableBytes()).also { buffer.getBytes(buffer.readerIndex(), it) }

/** XTEA over whole 8-byte blocks, leaving a short tail as it is: the inverse of RSProt's xteaDecrypt. */
internal object Xtea {
    private const val GOLDEN_RATIO = -0x61c88647
    private const val ROUNDS = 32

    fun encrypt(data: ByteArray, key: IntArray): ByteArray {
        val buf = Unpooled.wrappedBuffer(data.copyOf())
        for (i in 0 until data.size / 8) {
            val offset = i * 8
            var v0 = buf.getInt(offset)
            var v1 = buf.getInt(offset + 4)
            var sum = 0
            repeat(ROUNDS) {
                v0 += (v1 shl 4 xor v1.ushr(5)) + v1 xor sum + key[sum and 3]
                sum += GOLDEN_RATIO
                v1 += (v0 shl 4 xor v0.ushr(5)) + v0 xor sum + key[sum.ushr(11) and 3]
            }
            buf.setInt(offset, v0)
            buf.setInt(offset + 4, v1)
        }
        return buf.array()
    }
}
