package osrsps.game

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.rsprot.protocol.game.incoming.messaging.MessagePublic
import net.rsprot.protocol.game.incoming.messaging.MessagePublic.MessageColourPattern

class PublicChatTest {
    private fun chat(
        text: String = "hello",
        type: Int = 0,
        colour: Int = 0,
        effect: Int = 0,
        pattern: ByteArray? = null,
    ) = PublicChat.from(MessagePublic(type, colour, effect, text, pattern?.let(::MessageColourPattern), -1))

    @Test
    fun `plain chat passes through unchanged`() {
        val chat = chat("hello")!!
        assertEquals("hello", chat.text)
        assertEquals(0, chat.colour)
        assertEquals(0, chat.effect)
        assertNull(chat.pattern)
        assertFalse(chat.autotyper)
    }

    @Test
    fun `colour and effect are kept`() {
        val chat = chat("hi", colour = 12, effect = 3)!!
        assertEquals(12, chat.colour)
        assertEquals(3, chat.effect)
    }

    @Test
    fun `autotyper is type 1`() {
        assertTrue(chat(type = 1)!!.autotyper)
    }

    @Test
    fun `chat-channel types are not public chat`() {
        for (type in 2..4) assertNull(chat(type = type))
    }

    @Test
    fun `a pattern the colour asks for is kept`() {
        // Colour 15 is a three-colour pattern.
        val chat = chat(colour = 15, pattern = byteArrayOf(0, 3, 5))!!
        assertEquals(15, chat.colour)
        assertContentEquals(byteArrayOf(0, 3, 5), chat.pattern)
    }

    @Test
    fun `a missing, short or bad pattern falls back to yellow`() {
        for (pattern in listOf(null, byteArrayOf(0, 1), byteArrayOf(0, 1, -1), byteArrayOf(0, 1, 120))) {
            val chat = chat(colour = 15, pattern = pattern)!!
            assertEquals(0, chat.colour)
            assertNull(chat.pattern)
        }
    }

    @Test
    fun `out-of-range colour and effect are dropped`() {
        val chat = chat(colour = 21, effect = 6)!!
        assertEquals(0, chat.colour)
        assertEquals(0, chat.effect)
    }

    @Test
    fun `text is cut at 80 characters`() {
        assertEquals(PublicChat.MAX_LENGTH, chat("a".repeat(100))!!.text.length)
    }
}

class AllowChatTest {
    private fun world(types: VarTypes) = World(IntArray(0), osrsps.map.CollisionMap(), types)

    @Test
    fun `login sets the display-name varbit so the chatbox takes typing`() {
        // The base varp and bits here are made up; the real ones come from the cache.
        val types = VarTypes(2000, mapOf(World.VARBIT_HAS_DISPLAYNAME to VarbitType(1737, 31, 31)))
        val player = Player(1, "test", 3222, 3218, 0, types)
        world(types).allowChat(player)
        assertEquals(1, player.vars.getVarbit(World.VARBIT_HAS_DISPLAYNAME))
    }

    @Test
    fun `public chat outside a tick is held like a click`() {
        val player = Player(1, "test", 3222, 3218, 0)
        world(VarTypes.NONE).onPublicChat(player, MessagePublic(0, 0, 0, "hello", null, -1))
        assertEquals(1, player.heldActions.size)
    }

    @Test
    fun `a cache without the varbit doesn't stop the login`() {
        val player = Player(1, "test", 3222, 3218, 0)
        world(VarTypes.NONE).allowChat(player)
    }
}
