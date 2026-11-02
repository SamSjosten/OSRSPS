package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.World
import osrsps.game.combat.Spells
import osrsps.game.content.HomeTeleport
import osrsps.game.content.Lodestones
import osrsps.map.CollisionMap

/**
 * Home Teleport through the headless client: Cast on the spellbook opens the lodestone menu (RS3's rule; see
 * [Lodestones]), the Lumbridge pick plays the five stages and lands the player on the stone 28 ticks after the pick,
 * a second cast opens the menu again with no cooldown, and a cast cut short by walking away leaves the player where
 * they walked. The rl-client script test-runner/examples/magic-home-teleport.json makes the same clicks in the real client.
 */
class HeadlessHomeTeleportTest {
    private val lumbridge = Lodestones.LUMBRIDGE
    private val stone = Coord(lumbridge.x + 1, lumbridge.z + 1)

    private fun HeadlessClient.cast() = ifButton(Spells.SPELLBOOK, HomeTeleport.COMPONENT, HomeTeleport.CAST)

    @Test
    fun `home teleport opens the lodestone menu, a pick lands on the stone with no cooldown, and walking away stops it`(@TempDir dir: Path) {
        TestServer(dir) { crcs, millis -> World(crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, cycleMillis = millis) }.use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.commandReply("tele 3240 3218") { it.startsWith("Teleported") }
                client.await("the teleport") { client.position?.takeIf { it == Coord(3240, 3218) } }
                val animsBefore = client.animationTicks().size
                client.cast()
                val menu = client.awaitDialogue("the lodestone menu") { it is DialogueView.Menu }
                assertEquals(DialogueView.Menu("[lodestone.title]", listOf("[lodestone.lumbridge]")), menu, "a new account has Lumbridge's stone")
                val ticksAtPick = client.ticksSeen
                client.chooseOption(1)
                client.await("the landing", timeoutMillis = 30_000) { client.position?.takeIf { it == stone } }
                val landedAfter = client.ticksSeen - ticksAtPick
                // The pick lands in the next tick's input; the landing shows 28 ticks after that.
                check(landedAfter in 28..30) { "landed $landedAfter ticks after the pick" }
                val stages = client.animationTicks().drop(animsBefore).take(5)
                assertEquals(listOf(8, 7, 4, 3), stages.zipWithNext { a, b -> b - a }, "the lodestones' stages, from $stages")
                assertEquals("Varp ${HomeTeleport.VARP_LAST_CAST} = 0", client.commandReply("varp ${HomeTeleport.VARP_LAST_CAST}") { it.startsWith("Varp") }, "no cooldown")

                // A second cast right away: the menu again, not the cooldown's refusal.
                client.cast()
                assertEquals(menu, client.awaitDialogue("the menu again") { it is DialogueView.Menu })
                client.closeModal()
                client.awaitCycles(2)
                assertEquals(stone, client.position, "closed without a pick: no cast")

                client.commandReply("tele 3240 3218") { it.startsWith("Teleported") }
                client.await("the teleport") { client.position?.takeIf { it == Coord(3240, 3218) } }
                val before = client.animationTicks().size
                client.cast()
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.await("the first stage") { if (client.animationTicks().size > before) Unit else null }
                client.walk(3240, 3214)
                client.awaitCycles(35)
                assertEquals(Coord(3240, 3214), client.position)
                assertNotEquals(stone, client.position)
            }
        }
    }
}
