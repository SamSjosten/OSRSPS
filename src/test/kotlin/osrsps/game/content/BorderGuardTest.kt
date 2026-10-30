package osrsps.game.content

import kotlin.test.Test
import kotlin.test.assertEquals

/** Al Kharid step 1: the Border Guards' talk after Prince Ali Rescue, with no dialogue file, so every line shows its key. */
class BorderGuardTest {
    private val h = DialogueHarness()

    @Test
    fun `a Border Guard lets the player through free, two lines and no menu`() {
        h.talk { with(TollGate) { borderGuard() } }
        h.expect("border_guard.come_through", "border_guard.friend")
        h.ends()
    }

    @Test
    fun `both Border Guards share an examine, and the gate's four types share one`() {
        assertEquals("examine.npc_4287", Examines.npc(TollGate.BORDER_GUARD_WEST)?.key)
        assertEquals("examine.npc_4287", Examines.npc(TollGate.BORDER_GUARD_EAST)?.key)
        for (id in 44050..44053) assertEquals("examine.loc_44050", Examines.loc(id)?.key, "gate $id")
    }
}
