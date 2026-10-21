package osrsps.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.interfaces.IfCloseSub
import net.rsprot.protocol.game.outgoing.interfaces.IfMoveSub
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenSub
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenTop
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.game.outgoing.varp.VarpLarge
import net.rsprot.protocol.game.outgoing.varp.VarpSmall
import net.rsprot.protocol.message.OutgoingGameMessage

class InterfacesTest {
    private val sent = mutableListOf<OutgoingGameMessage>()
    private val interfaces = Interfaces { _, message -> sent += message }

    private fun player(frame: GameFrame) = Player(1, "test", 3222, 3218, 0).apply { this.frame = frame }

    private inline fun <reified T> sentOf() = sent.filterIsInstance<T>()

    @Test
    fun `login frame follows the client's window mode`() {
        assertEquals(GameFrame.FIXED, GameFrame.forLogin(resizable = false, stoneArrangement = 1))
        assertEquals(GameFrame.RESIZABLE_CLASSIC, GameFrame.forLogin(resizable = true, stoneArrangement = 0))
        assertEquals(GameFrame.RESIZABLE_MODERN, GameFrame.forLogin(resizable = true, stoneArrangement = 1))
    }

    @Test
    fun `fixed mode opens every overlay into the fixed top-level`() {
        interfaces.openGameFrame(player(GameFrame.FIXED))
        assertEquals(IfOpenTop(548), sent.first())
        val subs = sentOf<IfOpenSub>().filter { it.destinationInterfaceId != JournalTab.SIDE_JOURNAL }
        assertEquals(GameFrame.overlays.size, subs.size)
        assertTrue(subs.all { it.destinationInterfaceId == 548 })
        // With no tab remembered, the quest tab shows the character summary.
        assertTrue(IfOpenSub(629, 43, 712, Interfaces.OVERLAY) in sent)
        // The inventory goes into side3, which is component 84 in fixed and 79 in resizable classic.
        assertTrue(IfOpenSub(548, 84, 149, Interfaces.OVERLAY) in subs)
        assertTrue(RunClientScript(GameFrame.ZOOM_LIMITS_DEFAULT_SCRIPT, emptyList()) in sent)
    }

    @Test
    fun `fixed to resizable switches window mode then moves everything two ticks later`() {
        val player = player(GameFrame.FIXED)
        interfaces.requestFrame(player, GameFrame.RESIZABLE_CLASSIC, tick = 10)
        assertEquals(listOf<OutgoingGameMessage>(RunClientScript(GameFrame.CLIENT_MODE_SCRIPT, listOf(1))), sent)
        interfaces.processFrameMove(player, tick = 11)
        assertEquals(1, sent.size)
        interfaces.processFrameMove(player, tick = 12)
        assertEquals(GameFrame.RESIZABLE_CLASSIC, player.frame)
        assertNull(player.pendingFrameMove)
        assertEquals(listOf(IfOpenTop(161)), sentOf<IfOpenTop>())
        val moves = sentOf<IfMoveSub>()
        assertEquals(FrameSlot.entries.size, moves.size)
        assertTrue(IfMoveSub(548, 84, 161, 79) in moves)
        assertTrue(IfMoveSub(548, 11, 161, 96) in moves)
    }

    @Test
    fun `classic to modern moves next tick and sets the stone arrangement`() {
        val player = player(GameFrame.RESIZABLE_CLASSIC)
        interfaces.requestFrame(player, GameFrame.RESIZABLE_MODERN, tick = 10)
        assertTrue(sent.isEmpty())
        interfaces.processFrameMove(player, tick = 11)
        assertEquals(GameFrame.RESIZABLE_MODERN, player.frame)
        assertEquals(1, player.stoneArrangement)
        assertEquals(listOf(VarpLarge(GameFrame.STONE_ARRANGEMENT_VARP, 1 shl GameFrame.STONE_ARRANGEMENT_BIT)), sentOf<VarpLarge>())
        assertEquals(listOf(IfOpenTop(164)), sentOf<IfOpenTop>())
    }

    @Test
    fun `the stone arrangement follows its varp`() {
        val player = player(GameFrame.RESIZABLE_MODERN)
        // As ::varp 1055 would set it, with another bit of the varp also set.
        player.vars[GameFrame.STONE_ARRANGEMENT_VARP] = (1 shl GameFrame.STONE_ARRANGEMENT_BIT) or 1
        assertEquals(1, player.stoneArrangement)
        interfaces.requestFrame(player, GameFrame.RESIZABLE_CLASSIC, tick = 10)
        interfaces.processFrameMove(player, tick = 11)
        assertEquals(0, player.stoneArrangement)
        assertEquals(listOf(VarpSmall(GameFrame.STONE_ARRANGEMENT_VARP, 1)), sentOf<VarpSmall>())
    }

    @Test
    fun `fixed to modern goes through classic first`() {
        val player = player(GameFrame.FIXED)
        interfaces.requestFrame(player, GameFrame.RESIZABLE_MODERN, tick = 10)
        interfaces.processFrameMove(player, tick = 12)
        assertEquals(listOf(IfOpenTop(161), IfOpenTop(164)), sentOf<IfOpenTop>())
        assertEquals(GameFrame.RESIZABLE_MODERN, player.frame)
    }

    @Test
    fun `choosing the current layout does nothing`() {
        val player = player(GameFrame.RESIZABLE_CLASSIC)
        interfaces.requestFrame(player, GameFrame.RESIZABLE_CLASSIC, tick = 10)
        assertNull(player.pendingFrameMove)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `modals close in whatever frame is showing`() {
        val player = player(GameFrame.RESIZABLE_CLASSIC)
        interfaces.openModal(player, FrameSlot.MAINMODAL, 12)
        assertEquals(IfOpenSub(161, 16, 12, Interfaces.MODAL), sent.last())
        player.frame = GameFrame.FIXED
        interfaces.closeModals(player)
        assertEquals(IfCloseSub(548, 41), sent.last())
        assertTrue(player.modals.isEmpty())
        sent.clear()
        interfaces.closeModals(player)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a second choice before the move replaces the first`() {
        val player = player(GameFrame.RESIZABLE_CLASSIC)
        interfaces.requestFrame(player, GameFrame.FIXED, tick = 10)
        interfaces.requestFrame(player, GameFrame.RESIZABLE_CLASSIC, tick = 11)
        assertNull(player.pendingFrameMove)
        assertEquals<List<OutgoingGameMessage>>(
            listOf(RunClientScript(GameFrame.CLIENT_MODE_SCRIPT, listOf(0)), RunClientScript(GameFrame.CLIENT_MODE_SCRIPT, listOf(1))),
            sent,
        )
        interfaces.requestFrame(player, GameFrame.FIXED, tick = 12)
        interfaces.requestFrame(player, GameFrame.RESIZABLE_MODERN, tick = 13)
        interfaces.processFrameMove(player, tick = 14)
        assertEquals(GameFrame.RESIZABLE_MODERN, player.frame)
        assertEquals(listOf(IfOpenTop(164)), sentOf<IfOpenTop>())
        assertEquals(4, sentOf<RunClientScript>().count { it.id == GameFrame.CLIENT_MODE_SCRIPT })
    }

    @Test
    fun `picking a quest tab sub-tab opens it and remembers it for the next login`() {
        val types = VarTypes(9_000, mapOf(JournalTab.VARBIT_SIDE_JOURNAL_TAB to VarbitType(1_141, 0, 2)))
        val player = Player(1, "test", 3222, 3218, 0, types).apply { frame = GameFrame.FIXED }
        interfaces.openJournalTab(player, JournalTab.QUESTS)
        assertEquals(listOf(IfOpenSub(629, 43, 399, Interfaces.OVERLAY)), sentOf<IfOpenSub>())
        assertEquals(1, player.vars.getVarbit(JournalTab.VARBIT_SIDE_JOURNAL_TAB))

        sent.clear()
        interfaces.openGameFrame(player)
        assertTrue(IfOpenSub(629, 43, 399, Interfaces.OVERLAY) in sent)
    }
}
