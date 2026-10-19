package osrsps.net

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ClientConfigTest {
    private val config = ClientConfig("192.168.0.210", 43595)

    @Test
    fun `jav config points the launcher at the public host`() {
        val lines = config.javConfig.lines()
        assertContains(lines, "codebase=http://192.168.0.210/")
        assertContains(lines, "param=17=http://192.168.0.210:43595/world_list.ws")
        assertContains(lines, "param=25=241")
        assertContains(lines, "param=12=${ClientConfig.WORLD_ID}")
    }

    @Test
    fun `world list decodes back to one world on the public host`() {
        val input = DataInputStream(ByteArrayInputStream(config.worldList))
        val payloadSize = input.readInt()
        assertEquals(config.worldList.size - 4, payloadSize)
        assertEquals(1, input.readUnsignedShort())
        assertEquals(ClientConfig.WORLD_ID, input.readUnsignedShort())
        assertEquals(ClientConfig.WORLD_PROPERTIES, input.readInt())
        assertEquals("192.168.0.210", input.readJagexString())
        assertEquals("OSRSPS test world", input.readJagexString())
        assertEquals(0, input.readUnsignedByte())
        assertEquals(1, input.readUnsignedShort())
        assertEquals(0, input.available())
    }

    private fun DataInputStream.readJagexString(): String {
        val bytes = generateSequence { readUnsignedByte().takeIf { it != 0 } }.map { it.toByte() }.toList().toByteArray()
        return String(bytes, Charsets.ISO_8859_1)
    }
}
