package osrsps.cache

import osrsps.game.VarbitType

/**
 * Writes var types of our own as config archives 14 (varbits) and 16 (varps) hold them, with the opcodes
 * [VarTypeLoader] reads back, for the cache overlay to serve. The client sizes its varp array by archive 16's files,
 * so a varp of ours needs a type there even though the type itself says nothing.
 */
object VarTypeEncoder {
    /** Opcode 1: the base varp and the bit range, as RuneLite's VarbitLoader reads it. */
    fun encodeVarbit(type: VarbitType): ByteArray = byteArrayOf(
        1, (type.baseVar ushr 8).toByte(), type.baseVar.toByte(), type.startBit.toByte(), type.endBit.toByte(), 0,
    )

    /** A varp type with nothing in it: the client only reads opcode 5 (a client code) from one, which ours don't need. */
    fun encodeVarp(): ByteArray = byteArrayOf(0)
}
