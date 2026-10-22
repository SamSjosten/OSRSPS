package osrsps.game.command

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZonePartialEnclosed
import net.rsprot.protocol.message.OutgoingGameMessage
import osrsps.game.Player
import osrsps.game.World
import osrsps.map.CollisionMap

class ZoneCommandsTest {
    private val world = World(IntArray(0), CollisionMap())
    private val player = Player(1, "test", 3222, 3218, 0)
    private val other = Player(2, "other", 3225, 3220, 0)
    private val replies = mutableListOf<String>()
    private val sent = mutableListOf<OutgoingGameMessage>()

    private fun run(input: String) = world.commands.execute(player, input, replies::add)

    /** One cycle of zone updates for [observer]. */
    private fun cycle(observer: Player): List<OutgoingGameMessage> {
        val out = mutableListOf<OutgoingGameMessage>()
        world.zones.update(observer, out::add)
        world.zones.endCycle()
        sent += out
        return out
    }

    @AfterTest
    fun release() {
        sent.filterIsInstance<UpdateZonePartialEnclosed>().forEach { it.release() }
    }

    @Test
    fun `gfx is seen by another player nearby`() {
        cycle(other)
        run("gfx 85")
        assertEquals(listOf("Spotanim 85 on 3222, 3218"), replies)
        val update = cycle(other).single() as UpdateZonePartialEnclosed
        // Zone (402, 402) in the other player's build area, which starts at zone (397, 396).
        assertEquals(listOf(40, 48), listOf(update.zoneX, update.zoneZ))
    }

    @Test
    fun `gfx and proj check their arguments`() {
        run("gfx")
        run("gfx abc")
        run("gfx 70000")
        run("proj 91 0 0")
        run("proj 91 1")
        run("proj 91 -2147483648 0")
        assertEquals(
            listOf(
                "Usage: ::gfx id [height] [delay]",
                "Usage: ::gfx id [height] [delay] ('abc' is not a number)",
                "Spotanim id must be 0 to 65535",
                "dx and dz can't both be 0: the client doesn't draw a projectile that goes nowhere",
                "Usage: ::proj id [dx dz]",
                "dx and dz must be within 20",
            ),
            replies,
        )
        cycle(other)
        assertEquals(emptyList(), cycle(other), "nothing was sent")
    }

    @Test
    fun `proj is seen by another player nearby`() {
        cycle(other)
        run("proj 91")
        assertEquals(listOf("Projectile 91 from 3222, 3218 to 3222, 3223"), replies)
        assertTrue(cycle(other).single() is UpdateZonePartialEnclosed)
    }
}
