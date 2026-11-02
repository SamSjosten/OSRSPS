package osrsps.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.interfaces.IfCloseSub
import net.rsprot.protocol.message.OutgoingGameMessage

/** All Settings' close, which whatever takes the floater over (the world map's globe) goes through too. */
class AllSettingsCloseTest {
    private val sent = mutableListOf<OutgoingGameMessage>()
    private val interfaces = Interfaces { _, message -> sent += message }
    private val settings = AllSettings(SettingsCatalog.NONE, interfaces)
    private val player = Player(1, "Zezima", 3222, 3218, 0, VarTypes.NONE)

    @Test
    fun `closing the window lets go of its prompt and picker and shuts the floater`() {
        player.floater = AllSettings.INTERFACE
        player.settingsInputOpen = true
        player.colourPickerOpen = true
        player.settingsDropdown = 3
        settings.close(player)
        assertEquals(-1, player.floater)
        assertFalse(player.settingsInputOpen)
        assertFalse(player.colourPickerOpen)
        assertEquals(-1, player.settingsDropdown)
        val closed = sent.filterIsInstance<IfCloseSub>()
        assertEquals(1, closed.size)
        assertEquals(player.frame.component(FrameSlot.FLOATER), closed.single().componentId)
    }

    @Test
    fun `closing leaves another window in the floater alone`() {
        player.floater = WorldMapScreen.INTERFACE
        player.settingsInputOpen = true
        settings.close(player)
        assertEquals(WorldMapScreen.INTERFACE, player.floater)
        assertTrue(player.settingsInputOpen, "not settings' prompt to drop")
        assertTrue(sent.isEmpty())
        player.floater = -1
        settings.close(player)
        assertTrue(sent.isEmpty())
    }
}
