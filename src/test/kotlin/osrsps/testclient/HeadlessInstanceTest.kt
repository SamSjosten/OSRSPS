package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/**
 * Instances through two headless players (raid step 10's check): each makes a copy of the same spot with `::instance`,
 * gets a REBUILD_REGION drawing it from the real map, and can't see the other; leaving puts them back, with a
 * REBUILD_NORMAL, an instance nobody is in ends, and a save from inside one is at the spot it was made from.
 */
class HeadlessInstanceTest {
    @Test
    fun `two players in copies of the same spot don't see each other, and leaving puts them back`(@TempDir dir: Path) {
        TestServer(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.await("the login rebuild") { alice.position }
                    bob.await("the login rebuild") { bob.position }
                    alice.await("bob in view") { alice.otherPlayers().takeIf { it.isNotEmpty() } }
                    val start = assertNotNull(alice.position)
                    val bobStart = assertNotNull(bob.position)

                    val made = alice.commandReply("instance 2") { it.startsWith("Made instance") }
                    bob.commandReply("instance 2") { it.startsWith("Made instance") }
                    val inside = alice.await("alice inside") { alice.position?.takeIf { it.x >= 6400 } }
                    bob.await("bob inside") { bob.position?.takeIf { it.x >= 6400 } }
                    assertTrue(made.endsWith("you at ${inside.x}, ${inside.z}, 0"), made)
                    assertEquals(2, server.world.instances.all.size)

                    // The client draws alice's zone as the one she came from.
                    val zones = alice.await("the region rebuild") { alice.regionZones() }
                    assertEquals(CopiedZone(start.x shr 3, start.z shr 3, 0, 0), zones[Triple(inside.x shr 3, inside.z shr 3, 0)])
                    // 5 x 5 zones copied, on each of the 4 levels.
                    assertEquals(100, zones.size)

                    alice.stepTicksWhile { alice.otherPlayers().isNotEmpty() }
                    assertEquals(emptyMap(), alice.otherPlayers(), "bob is in his own copy")
                    assertEquals(emptyMap(), bob.otherPlayers())

                    alice.commandReply("instance leave") { it.startsWith("Left instance") }
                    alice.await("alice back") { alice.position?.takeIf { it == start } }
                    alice.await("the world's own rebuild") { if (alice.regionZones() == null) Unit else null }
                    // Hers ends once nobody is in it; bob's stays.
                    alice.await("her instance ended") { server.world.instances.all.singleOrNull() }
                    assertNotNull(server.world.instances.at(bob.position!!.x, bob.position!!.z))
                    // A save while he is in it puts him where he made it, as a logout there would.
                    val save = server.world.snapshot(server.world.playerNamed("bob")!!)
                    assertEquals(bobStart, Coord(save.x, save.z, save.level))
                }
            }
        }
    }
}

/** Waits a few ticks for [condition] to stop holding. */
private fun HeadlessClient.stepTicksWhile(condition: () -> Boolean) {
    await("the view to change", timeoutMillis = 5_000) { if (condition()) null else Unit }
}
