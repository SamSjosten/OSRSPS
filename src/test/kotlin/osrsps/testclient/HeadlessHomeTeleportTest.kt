package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.World
import osrsps.game.combat.Spells
import osrsps.game.content.HomeTeleport
import osrsps.game.content.LodestoneMap
import osrsps.game.content.Lodestones
import osrsps.map.CollisionMap

/**
 * Home Teleport through the headless client: Cast on the spellbook opens the lodestone map (RS3's rule; see
 * [Lodestones]), Teleport on Lumbridge's row plays the cast's stages and lands the player on the stone
 * [Lodestones.CAST_TICKS] ticks after the pick, a second cast opens the map again with no cooldown, its close button casts nothing, and a cast cut short
 * by walking away leaves the player where they walked. The rl-client script test-runner/examples/magic-home-teleport.json
 * makes the same clicks in the real client.
 */
class HeadlessHomeTeleportTest {
    private val lumbridge = Lodestones.LUMBRIDGE
    private val stone = Coord(lumbridge.x + 1, lumbridge.z + 1)

    private fun HeadlessClient.cast() = ifButton(Spells.SPELLBOOK, HomeTeleport.COMPONENT, HomeTeleport.CAST)

    /** Waits for the map to be open with everything the server sets at an open: the close button's events come last. */
    private fun HeadlessClient.awaitMap() {
        await("the lodestone map") { if (MAP in openInterfaces() && opEnabled(MAP, LodestoneMap.CLOSE, -1, LodestoneMap.CLOSE_OP)) Unit else null }
    }

    private fun HeadlessClient.pickLumbridge() = ifButton(MAP, LodestoneMap.row(lumbridge), LodestoneMap.TELEPORT)

    @Test
    fun `home teleport opens the lodestone map, a pick lands on the stone with no cooldown, and walking away stops it`(@TempDir dir: Path) {
        TestServer(dir) { crcs, millis -> World(crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, cycleMillis = millis) }.use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.commandReply("tele 3240 3218") { it.startsWith("Teleported") }
                client.await("the teleport") { client.position?.takeIf { it == Coord(3240, 3218) } }
                val animsBefore = client.animationTicks().size
                client.cast()
                client.awaitMap()
                assertEquals("[lodestone.title]", client.text(MAP, LodestoneMap.TITLE))
                assertEquals(false, client.hidden(MAP, LodestoneMap.activeIcon(lumbridge)), "a new account has Lumbridge's stone")
                assertEquals(true, client.hidden(MAP, LodestoneMap.activeIcon(Lodestones.DRAYNOR)))
                assertFalse(client.opEnabled(MAP, LodestoneMap.row(Lodestones.DRAYNOR), -1, LodestoneMap.TELEPORT), "and no other")
                val ticksAtPick = client.ticksSeen
                client.pickLumbridge()
                client.await("the landing", timeoutMillis = 30_000) { client.position?.takeIf { it == stone } }
                val landedAfter = client.ticksSeen - ticksAtPick
                // The pick lands in the next tick's input; the landing shows CAST_TICKS ticks after that.
                check(landedAfter in Lodestones.CAST_TICKS..Lodestones.CAST_TICKS + 2) { "landed $landedAfter ticks after the pick" }
                val stages = client.animationTicks().drop(animsBefore).take(Lodestones.STAGES.size + 1)
                assertEquals(listOf(Lodestones.CAST_TICKS - Lodestones.RISE_TICKS, Lodestones.RISE_TICKS), stages.zipWithNext { a, b -> b - a }, "the lodestones' stages and landing, from $stages")
                assertEquals("Varp ${HomeTeleport.VARP_LAST_CAST} = 0", client.commandReply("varp ${HomeTeleport.VARP_LAST_CAST}") { it.startsWith("Varp") }, "no cooldown")

                // A second cast right away: the map again, not the cooldown's refusal; its close button casts nothing.
                client.cast()
                client.awaitMap()
                client.ifButton(MAP, LodestoneMap.CLOSE, LodestoneMap.CLOSE_OP)
                client.await("the map closed") { if (MAP !in client.openInterfaces()) Unit else null }
                client.awaitCycles(2)
                assertEquals(stone, client.position, "closed without a pick: no cast")

                client.commandReply("tele 3240 3218") { it.startsWith("Teleported") }
                client.await("the teleport") { client.position?.takeIf { it == Coord(3240, 3218) } }
                val before = client.animationTicks().size
                client.cast()
                client.awaitMap()
                client.pickLumbridge()
                client.await("the first stage") { if (client.animationTicks().size > before) Unit else null }
                client.walk(3240, 3214)
                client.awaitCycles(35)
                assertEquals(Coord(3240, 3214), client.position)
                assertNotEquals(stone, client.position)
            }
        }
    }

    private companion object {
        const val MAP = LodestoneMap.INTERFACE
    }
}
