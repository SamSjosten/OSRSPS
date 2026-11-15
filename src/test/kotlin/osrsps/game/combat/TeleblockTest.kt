package osrsps.game.combat

import kotlin.test.Test
import kotlin.test.assertEquals
import osrsps.game.Player
import osrsps.game.content.HomeTeleportLines

class TeleblockTest {
    private val caster = Player(1, "alice", 3090, 3800, 0)
    private val target = Player(2, "bob", 3095, 3800, 0)

    @Test
    fun `teleports stop above level 20 and while Tele Blocked`() {
        assertEquals(emptyList(), Teleblock.refusal(target, 20))
        assertEquals(listOf(HomeTeleportLines.WILDERNESS, HomeTeleportLines.WILDERNESS_2), Teleblock.refusal(target, 21))
        Teleblock.block(target, caster, Teleblock.TICKS)
        assertEquals(listOf(PvpLines.TELEPORT_BLOCKED), Teleblock.refusal(target, 0))
        assertEquals(listOf(HomeTeleportLines.WILDERNESS, HomeTeleportLines.WILDERNESS_2), Teleblock.refusal(target, 36), "the level first")
    }

    @Test
    fun `a block counts down to its end, and ends outside the Wilderness`() {
        Teleblock.block(target, caster, 2)
        assertEquals("alice", target.teleblockedBy)
        Teleblock.countDown(target)
        assertEquals(1, target.teleblockTicks)
        Teleblock.countDown(target)
        assertEquals(0, target.teleblockTicks)
        assertEquals(null, target.teleblockedBy)

        Teleblock.block(target, caster, Teleblock.TICKS)
        target.x = 3095
        target.z = 3500
        Teleblock.countDown(target)
        assertEquals(0, target.teleblockTicks, "south of the ditch")
    }
}
