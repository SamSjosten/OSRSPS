package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.IdkType
import osrsps.game.IdkTypes
import osrsps.game.PlayerLook
import osrsps.game.World
import osrsps.map.CollisionMap

/**
 * Per-player looks (looks step 1) through the headless client: a new account's default look in its appearance block,
 * `::look` changing body type, a kit and a colour for the player and for someone watching, refusals of kits that don't
 * fit, and the look kept over a logout. The rl-client script test-runner/examples/look-command.json makes the same
 * changes in the real client.
 */
class HeadlessLookTest {
    // Kits shaped like the 242 cache's: body part 0 to 6 for body type A, 7 to 13 for B, at the ids the cache has them.
    private val kits = IdkTypes(
        arrayOfNulls<IdkType>(80).also { array ->
            for ((id, part) in listOf(0 to 0, 5 to 0, 10 to 1, 18 to 2, 26 to 3, 33 to 4, 36 to 5, 42 to 6, 45 to 7, 50 to 7, 56 to 9, 61 to 10, 67 to 11, 70 to 12, 79 to 13)) {
                array[id] = IdkType(id, part, selectable = true)
            }
        },
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, cycleMillis = millis, idkTypes = kits)
    }

    private fun HeadlessClient.awaitLook(of: Int, description: String, matches: (AppearanceView) -> Boolean): AppearanceView =
        await(description) { appearance(of)?.takeIf(matches) }

    @Test
    fun `a new account has the default look, and look commands change it for the player and for anyone watching`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.await("the login rebuild") { alice.position }
                    bob.await("the login rebuild") { bob.position }
                    val default = alice.awaitLook(alice.index, "alice's first appearance") { true }
                    assertEquals(PlayerLook.BODY_TYPE_A, default.bodyType)
                    assertEquals(PlayerLook.DEFAULT.kits, default.kits)
                    assertEquals(PlayerLook.DEFAULT.colours, default.colours)
                    assertEquals(PlayerLook.DEFAULT.kits, bob.awaitLook(alice.index, "alice as bob sees her") { true }.kits)

                    alice.commandReply("look body b") { it.startsWith("Look:") }
                    val typeB = listOf(45, -1, 56, 61, 67, 70, 79)
                    alice.awaitLook(alice.index, "body type B") { it.bodyType == PlayerLook.BODY_TYPE_B && it.kits == typeB }
                    bob.awaitLook(alice.index, "body type B as bob sees it") { it.bodyType == PlayerLook.BODY_TYPE_B && it.kits == typeB }

                    alice.commandReply("look kit hair 50") { it.startsWith("Look:") }
                    alice.commandReply("look colour skin 7") { it.startsWith("Look:") }
                    bob.awaitLook(alice.index, "hair 50 and skin 7") { it.kits[0] == 50 && it.colours == listOf(0, 0, 0, 0, 7) }
                }
            }
        }
    }

    @Test
    fun `kits of the other body type, of another part or out of the cache are refused, and so are colours past the last`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                // Body type A: kit 45 is body type B's hair, 10 is a jaw, 77 isn't in the cache.
                for (kit in listOf(45, 10, 77)) {
                    assertEquals("Kit $kit isn't a hair of body type A", client.commandReply("look kit hair $kit") { it.startsWith("Kit") })
                }
                assertEquals("skin colours are 0 to 13", client.commandReply("look colour skin 14") { it.endsWith("13") })
                assertEquals("feet colours are 0 to 5", client.commandReply("look colour feet 6") { it.endsWith("5") })
                // No jaw is a jaw; no hair is refused.
                client.commandReply("look kit jaw -1") { it.startsWith("Look:") }
                assertEquals("Kit -1 isn't a hair of body type A", client.commandReply("look kit hair -1") { it.startsWith("Kit") })
                client.awaitLook(client.index, "no jaw") { it.kits == PlayerLook.DEFAULT.kits.toMutableList().also { kits -> kits[1] = -1 } }
            }
        }
    }

    @Test
    fun `the look is kept over a logout, and reset puts the default back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("look body b") { it.startsWith("Look:") }
                client.commandReply("look colour hair 29") { it.startsWith("Look:") }
                client.awaitLook(client.index, "hair colour 29") { it.colours[0] == 29 }
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.loginAgain("dave").use { client ->
                val look = client.awaitLook(client.index, "the saved look") { true }
                assertEquals(PlayerLook.BODY_TYPE_B, look.bodyType)
                assertEquals(listOf(45, -1, 56, 61, 67, 70, 79), look.kits)
                assertEquals(listOf(29, 0, 0, 0, 0), look.colours)
                client.commandReply("look reset") { it.startsWith("Look:") }
                client.awaitLook(client.index, "the default look") { it.bodyType == 0 && it.kits == PlayerLook.DEFAULT.kits && it.colours == PlayerLook.DEFAULT.colours }
            }
        }
    }

    /** Logs in once the server has let the last login of [name] go: until then a login is refused as still on. */
    private fun TestServer.loginAgain(name: String): HeadlessClient {
        repeat(50) {
            runCatching { login(name) }.onSuccess { return it }
            Thread.sleep(100)
        }
        return login(name)
    }
}
