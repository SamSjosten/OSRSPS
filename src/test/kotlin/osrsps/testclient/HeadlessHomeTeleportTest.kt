package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.combat.Spells
import osrsps.game.content.HomeTeleport

/**
 * Lumbridge Home Teleport through the headless client: Cast on the spellbook plays the five stages and lands the player
 * in the castle courtyard 24 ticks after the click, a second cast is refused for the cooldown, and a cast cut short by
 * walking away leaves the player where they walked and starts no cooldown. The rl-client script
 * test-runner/examples/magic-home-teleport.json makes the same clicks in the real client.
 */
class HeadlessHomeTeleportTest {
    private fun HeadlessClient.cast() = ifButton(Spells.SPELLBOOK, HomeTeleport.COMPONENT, HomeTeleport.CAST)

    @Test
    fun `home teleport lands in Lumbridge, then waits out its cooldown, and walking away stops it`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.commandReply("tele 3240 3218") { it.startsWith("Teleported") }
                client.await("the teleport") { client.position?.takeIf { it == Coord(3240, 3218) } }
                val animsBefore = client.animationTicks().size
                val ticksAtClick = client.ticksSeen
                client.cast()
                client.await("the landing", timeoutMillis = 20_000) { client.position?.takeIf { it == Coord(3222, 3218) } }
                val landedAfter = client.ticksSeen - ticksAtClick
                // The click lands in the next tick's input; the landing shows 24 ticks after that.
                check(landedAfter in 24..26) { "landed $landedAfter ticks after the click" }
                val stages = client.animationTicks().drop(animsBefore).take(5)
                assertEquals(listOf(7, 6, 3, 3), stages.zipWithNext { a, b -> b - a }, "the gaps between the stages, from $stages")

                client.cast()
                client.awaitMessage("the cooldown") { it.startsWith("[home_teleport.cooldown minutes=") }

                client.commandReply("varp ${HomeTeleport.VARP_LAST_CAST} 0") { it.startsWith("Varp ${HomeTeleport.VARP_LAST_CAST}") }
                client.commandReply("tele 3240 3218") { it.startsWith("Teleported") }
                client.await("the teleport") { client.position?.takeIf { it == Coord(3240, 3218) } }
                val before = client.animationTicks().size
                client.cast()
                client.await("the first stage") { if (client.animationTicks().size > before) Unit else null }
                client.walk(3240, 3214)
                client.awaitCycles(35)
                assertEquals(Coord(3240, 3214), client.position)
                assertNotEquals(Coord(3222, 3218), client.position)
                assertEquals("Varp ${HomeTeleport.VARP_LAST_CAST} = 0", client.commandReply("varp ${HomeTeleport.VARP_LAST_CAST}") { it.startsWith("Varp") })
            }
        }
    }
}
