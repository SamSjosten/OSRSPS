package osrsps.cache

import io.netty.buffer.Unpooled
import java.io.ByteArrayOutputStream

/**
 * Writes and reads a sequence (an animation) as config archive 12 holds one, with the opcodes RuneLite's
 * SequenceLoader reads from revision 226 on (checked against the 241 cache on 2026-11-08: all 14,514 sequences read
 * back to their last byte with that table). A sequence is a list of frames with a delay each, so one of ours can play
 * frames the cache already has, at our pace and in our order: a frame id ([frame]) is a frame group of index 0 in its
 * top 16 bits and the file in that group in its low 16, and a delay is in client cycles of 20 ms. Jagex composes
 * sequences the same way (`human_castteleport_reverse`, 715, is 714's frames backwards). The encoder writes what ours
 * use and the decoder reads those back, so a test sees ours as the client will; an opcode ours don't write is refused.
 */
object SeqCodec {
    const val INDEX = 2
    const val GROUP = 12

    /** The most frames opcode 1 counts, and the most client cycles a delay holds. */
    const val MAX_FRAMES = 0xFFFF
    const val MAX_DELAY = 0xFFFF

    /** A frame id: group [group] of the frames index, file [file] in it. */
    fun frame(group: Int, file: Int): Int {
        require(group in 0..0xFFFF && file in 0..0xFFFF) { "A frame is a group and a file of 0 to 65535, not $group/$file" }
        return (group shl 16) or file
    }

    /**
     * A sequence: [frames] (ids from [frame]) with [delays] (client cycles) to match. The rest is null for the client's
     * default: [loopOffset] (opcode 2, how many frames back the sequence restarts when it ends; without it, it plays
     * once), [priority] (5, the forced priority), [leftHand] and [rightHand] (6 and 7: what the hands hold meanwhile;
     * 0 hides the weapon or shield, as the cache's teleports do), [maxLoops] (8), [precedenceAnimating] (9),
     * [priorityWalking] (10), [replyMode] (11) and [verticalOffset] (16, a signed byte the cache's player sequences set
     * to 8). The hands and the offset are copied from the cache's sequences that ours take frames from.
     */
    class Definition(
        val frames: List<Int>,
        val delays: List<Int>,
        val loopOffset: Int? = null,
        val priority: Int? = null,
        val leftHand: Int? = null,
        val rightHand: Int? = null,
        val maxLoops: Int? = null,
        val precedenceAnimating: Int? = null,
        val priorityWalking: Int? = null,
        val replyMode: Int? = null,
        val verticalOffset: Int? = null,
    ) {
        init {
            require(frames.isNotEmpty() && frames.size <= MAX_FRAMES) { "A sequence has 1 to $MAX_FRAMES frames, not ${frames.size}" }
            require(delays.size == frames.size) { "${frames.size} frames need ${frames.size} delays, not ${delays.size}" }
            require(frames.all { it >= 0 }) { "Bad frame id in $frames" }
            require(delays.all { it in 0..MAX_DELAY }) { "A delay is 0 to $MAX_DELAY client cycles: $delays" }
            require(loopOffset == null || loopOffset in 0..0xFFFF) { "Bad loop offset $loopOffset" }
            require(priority == null || priority in 0..255) { "Bad priority $priority" }
            require(leftHand == null || leftHand in 0..0xFFFF) { "Bad left hand $leftHand" }
            require(rightHand == null || rightHand in 0..0xFFFF) { "Bad right hand $rightHand" }
            require(maxLoops == null || maxLoops in 0..255) { "Bad loop count $maxLoops" }
            require(precedenceAnimating == null || precedenceAnimating in 0..255) { "Bad precedence $precedenceAnimating" }
            require(priorityWalking == null || priorityWalking in 0..255) { "Bad walking priority $priorityWalking" }
            require(replyMode == null || replyMode in 0..255) { "Bad reply mode $replyMode" }
            require(verticalOffset == null || verticalOffset in -128..127) { "Bad vertical offset $verticalOffset" }
        }

        /** How long the sequence plays once through, in client cycles. */
        val cycles: Int get() = delays.sum()

        override fun equals(other: Any?): Boolean = other is Definition && other.fields() == fields()
        override fun hashCode(): Int = fields().hashCode()
        override fun toString(): String = "sequence (${frames.size} frames, $cycles cycles)"

        private fun fields() = listOf(frames, delays, loopOffset, priority, leftHand, rightHand, maxLoops, precedenceAnimating, priorityWalking, replyMode, verticalOffset)
    }

    fun encode(definition: Definition): ByteArray {
        val out = ByteArrayOutputStream()
        fun u8(opcode: Int, value: Int?) {
            if (value != null) {
                out.write(opcode)
                out.write(value and 0xFF)
            }
        }
        fun u16(opcode: Int, value: Int?) {
            if (value != null) {
                out.write(opcode)
                out.write(value ushr 8)
                out.write(value and 0xFF)
            }
        }
        u16(2, definition.loopOffset)
        u8(5, definition.priority)
        u16(6, definition.leftHand)
        u16(7, definition.rightHand)
        u8(8, definition.maxLoops)
        u8(9, definition.precedenceAnimating)
        u8(10, definition.priorityWalking)
        u8(11, definition.replyMode)
        u8(16, definition.verticalOffset)
        // The frames last, as the cache writes them: the count, every delay, every frame's file, every frame's group.
        out.write(1)
        out.write(definition.frames.size ushr 8)
        out.write(definition.frames.size and 0xFF)
        for (delay in definition.delays) {
            out.write(delay ushr 8)
            out.write(delay and 0xFF)
        }
        for (frame in definition.frames) {
            out.write(frame ushr 8 and 0xFF)
            out.write(frame and 0xFF)
        }
        for (frame in definition.frames) {
            out.write(frame ushr 24 and 0xFF)
            out.write(frame ushr 16 and 0xFF)
        }
        out.write(0)
        return out.toByteArray()
    }

    /** Reads a sequence back as the client would, the opcodes [encode] writes; another opcode, or a sequence without frames, is refused. */
    fun decode(data: ByteArray): Definition {
        val buf = Unpooled.wrappedBuffer(data)
        var frames: List<Int>? = null
        var delays: List<Int>? = null
        var loopOffset: Int? = null
        var priority: Int? = null
        var leftHand: Int? = null
        var rightHand: Int? = null
        var maxLoops: Int? = null
        var precedenceAnimating: Int? = null
        var priorityWalking: Int? = null
        var replyMode: Int? = null
        var verticalOffset: Int? = null
        while (true) {
            when (val opcode = buf.readUnsignedByte().toInt()) {
                0 -> break
                1 -> {
                    val count = buf.readUnsignedShort()
                    val lengths = List(count) { buf.readUnsignedShort() }
                    val ids = IntArray(count) { buf.readUnsignedShort() }
                    for (i in 0 until count) ids[i] += buf.readUnsignedShort() shl 16
                    frames = ids.toList()
                    delays = lengths
                }
                2 -> loopOffset = buf.readUnsignedShort()
                5 -> priority = buf.readUnsignedByte().toInt()
                6 -> leftHand = buf.readUnsignedShort()
                7 -> rightHand = buf.readUnsignedShort()
                8 -> maxLoops = buf.readUnsignedByte().toInt()
                9 -> precedenceAnimating = buf.readUnsignedByte().toInt()
                10 -> priorityWalking = buf.readUnsignedByte().toInt()
                11 -> replyMode = buf.readUnsignedByte().toInt()
                16 -> verticalOffset = buf.readByte().toInt()
                else -> error("Sequence opcode $opcode is not one of ours")
            }
        }
        require(buf.readableBytes() == 0) { "${buf.readableBytes()} byte(s) after the sequence's end" }
        require(frames != null && delays != null) { "A sequence without frames" }
        return Definition(frames, delays, loopOffset, priority, leftHand, rightHand, maxLoops, precedenceAnimating, priorityWalking, replyMode, verticalOffset)
    }
}
