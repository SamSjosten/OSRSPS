package osrsps.testclient

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket
import java.nio.file.Path
import osrsps.Revision
import osrsps.game.IpBans
import osrsps.game.World
import osrsps.map.CollisionMap
import osrsps.net.ProxyHeaders
import osrsps.net.ProxyProtocol
import osrsps.net.ProxyProtocol.Mode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.io.TempDir

/**
 * PROXY headers on the game port over real sockets. Tests connect from 127.0.0.1, so it is made the relay to test one,
 * and left out (a TEST-NET address is the relay instead) to test everyone else. The player's address is read back with
 * the admin command `::ip`, as an admin would.
 */
class HeadlessProxyTest {
    private val relayIsUs = ProxyProtocol(setOf("127.0.0.1"), Mode.OPTIONAL)
    private val relayIsElsewhere = ProxyProtocol(setOf("192.0.2.1"), Mode.OPTIONAL)
    private val player = ProxyHeaders.v2("203.0.113.7", 51234)

    @Test
    fun `a v2 header from the relay gives the player's real address`(@TempDir dir: Path) {
        TestServer(dir, proxy = relayIsUs).use { server ->
            server.login("alice", header = player).use { alice ->
                assertEquals("alice is on 203.0.113.7", alice.commandReply("::ip alice") { it.startsWith("alice is on") })
            }
        }
    }

    @Test
    fun `a v1 header from the relay is read too`(@TempDir dir: Path) {
        TestServer(dir, proxy = relayIsUs).use { server ->
            server.login("alice", header = ProxyHeaders.v1("198.51.100.4", 4000)).use { alice ->
                assertEquals("alice is on 198.51.100.4", alice.commandReply("::ip alice") { it.startsWith("alice is on") })
            }
        }
    }

    @Test
    fun `a client that isn't a relay is known by its own address`(@TempDir dir: Path) {
        TestServer(dir, proxy = relayIsElsewhere).use { server ->
            server.login("bob").use { bob ->
                assertEquals("bob is on 127.0.0.1", bob.commandReply("::ip bob") { it.startsWith("bob is on") })
            }
        }
    }

    @Test
    fun `a header from a peer that isn't a relay drops the connection`(@TempDir dir: Path) {
        TestServer(dir, proxy = relayIsElsewhere).use { server ->
            assertDropped(js5Handshake(server.port, player))
            assertFailsWith<IOException> { server.login("mallory", header = player) }
        }
    }

    @Test
    fun `with no relays nobody may send a header`(@TempDir dir: Path) {
        TestServer(dir, proxy = ProxyProtocol.OFF).use { server -> assertDropped(js5Handshake(server.port, player)) }
    }

    @Test
    fun `in optional mode the relay may connect without a header`(@TempDir dir: Path) {
        TestServer(dir, proxy = relayIsUs).use { server ->
            server.login("carol").use { carol ->
                assertEquals("carol is on 127.0.0.1", carol.commandReply("::ip carol") { it.startsWith("carol is on") })
            }
        }
    }

    @Test
    fun `in required mode the relay must send a header`(@TempDir dir: Path) {
        TestServer(dir, proxy = ProxyProtocol(setOf("127.0.0.1"), Mode.REQUIRED)).use { server ->
            assertDropped(js5Handshake(server.port, ByteArray(0)))
            server.login("dave", header = player).use { dave ->
                assertEquals("dave is on 203.0.113.7", dave.commandReply("::ip dave") { it.startsWith("dave is on") })
            }
        }
    }

    @Test
    fun `the Home Lab probe, a JS5 handshake behind a header in one write, is answered`(@TempDir dir: Path) {
        TestServer(dir, proxy = relayIsUs).use { server ->
            assertEquals(0, js5Handshake(server.port, player))
        }
    }

    @Test
    fun `a header that arrives on its own, ahead of the client's first bytes, is read`(@TempDir dir: Path) {
        TestServer(dir, proxy = relayIsUs).use { server ->
            Socket("127.0.0.1", server.port).use { socket ->
                socket.soTimeout = 5000
                val out = socket.getOutputStream()
                // As HAProxy does: the header as soon as it connects, the player's bytes when they come.
                out.write(player)
                out.flush()
                Thread.sleep(100)
                out.write(js5Init())
                out.flush()
                assertEquals(0, socket.getInputStream().read(), "JS5 handshake response")
            }
        }
    }

    @Test
    fun `a header cut across two reads still counts as one`(@TempDir dir: Path) {
        TestServer(dir, proxy = relayIsUs).use { server ->
            Socket("127.0.0.1", server.port).use { socket ->
                socket.soTimeout = 5000
                val out = socket.getOutputStream()
                out.write(player, 0, 5)
                out.flush()
                Thread.sleep(100)
                out.write(player, 5, player.size - 5)
                out.write(js5Init())
                out.flush()
                assertEquals(0, socket.getInputStream().read(), "JS5 handshake response")
            }
        }
    }

    @Test
    fun `a banned address can't log in, and banning a player logs them out`(@TempDir dir: Path) {
        val world = { crcs: IntArray, millis: Long ->
            World(crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, ipBans = IpBans(relays = setOf("127.0.0.1")), cycleMillis = millis)
        }
        TestServer(dir, world = world, proxy = relayIsUs).use { server ->
            server.login("erin", header = ProxyHeaders.v2("198.51.100.9", 1)).use { erin ->
                server.login("frank", header = player).use { frank ->
                    // The relay itself can't be banned: every headerless internet player comes from it.
                    assertEquals(
                        "127.0.0.1 is the relay every internet player comes through; ban the player once they connect with their own address",
                        erin.commandReply("::ipban 127.0.0.1") { it.startsWith("127.0.0.1") },
                    )
                    assertEquals("Banned 203.0.113.7; logging out frank", erin.commandReply("::ipban frank cheating") { it.startsWith("Banned") })
                    frank.await("frank's logout") { frank.packets().firstOrNull { it.prot.name == "LOGOUT" } }
                }
                val refused = assertFailsWith<LoginFailedException> { server.login("frank", header = player) }
                assertEquals(IP_BLOCKED, refused.code)
                // Another name from that address is refused too; another address, or the relay's own, isn't.
                assertEquals(IP_BLOCKED, assertFailsWith<LoginFailedException> { server.login("grace", header = player) }.code)
                server.login("grace", header = ProxyHeaders.v2("198.51.100.10", 1)).close()
                server.login("heidi").close()
                assertEquals("203.0.113.7 by erin (frank) cheating", erin.commandReply("::ipbans") { it.startsWith("203.") })
                assertEquals("Unbanned 203.0.113.7", erin.commandReply("::ipunban 203.0.113.7") { it.startsWith("Unbanned") })
            }
            server.login("frank", header = player).close()
        }
    }

    private fun js5Init(): ByteArray {
        val bytes = java.io.ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeByte(15) // INIT_JS5REMOTE_CONNECTION
            out.writeInt(Revision.MAJOR)
            repeat(4) { out.writeInt(0) }
        }
        return bytes.toByteArray()
    }

    /** Sends [header] and a JS5 handshake in one write; the first byte back, or -1 if the server closed the connection. */
    private fun js5Handshake(port: Int, header: ByteArray): Int = Socket("127.0.0.1", port).use { socket ->
        socket.soTimeout = 5000
        val out = DataOutputStream(socket.getOutputStream())
        out.write(header + js5Init())
        out.flush()
        try {
            DataInputStream(socket.getInputStream()).read()
        } catch (e: java.net.SocketException) {
            -1 // Reset rather than closed.
        }
    }

    private fun assertDropped(response: Int) = assertEquals(-1, response, "the server should have closed the connection")

    private companion object {
        /** "Your address has been blocked", LoginResponse.IPBlocked. */
        const val IP_BLOCKED = 26
    }
}
