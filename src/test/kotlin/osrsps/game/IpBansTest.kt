package osrsps.game

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class IpBansTest {
    @Test
    fun `bans last across a restart`(@TempDir dir: Path) {
        val file = dir.resolve(IpBans.FILE_NAME)
        val bans = IpBans(file)
        assertEquals(IpBans.Result.BANNED, bans.ban("203.0.113.7", "by Swaggy (mallory) spam"))
        assertEquals(IpBans.Result.BANNED, bans.ban("2001:db8::1"))
        assertEquals(IpBans.Result.ALREADY_BANNED, bans.ban("203.0.113.7"))

        val again = IpBans(file)
        assertTrue(again.isBanned("203.0.113.7"))
        assertTrue(again.isBanned("2001:db8:0:0:0:0:0:1"), "another spelling of the same address")
        assertFalse(again.isBanned("203.0.113.8"))
        assertEquals(mapOf("2001:db8:0:0:0:0:0:1" to "", "203.0.113.7" to "by Swaggy (mallory) spam"), again.all)

        assertEquals(IpBans.Result.UNBANNED, again.unban("203.0.113.7"))
        assertEquals(IpBans.Result.NOT_BANNED, again.unban("203.0.113.7"))
        assertFalse(IpBans(file).isBanned("203.0.113.7"))
    }

    @Test
    fun `a hand-edited file is read, skipping what isn't an address`(@TempDir dir: Path) {
        val file = dir.resolve(IpBans.FILE_NAME)
        Files.writeString(file, "# comment\n\n198.51.100.4 spammer\n  198.51.100.5\nnot-an-address\n999.1.1.1\n::ffff:198.51.100.6 mapped\n")
        val bans = IpBans(file)
        assertEquals(setOf("198.51.100.4", "198.51.100.5", "198.51.100.6"), bans.all.keys)
        assertEquals("spammer", bans.all["198.51.100.4"])
    }

    @Test
    fun `only IP addresses can be banned, and never a relay`() {
        val bans = IpBans(relays = setOf("192.168.0.216"))
        assertEquals(IpBans.Result.RELAY, bans.ban("192.168.0.216"))
        assertEquals(IpBans.Result.NOT_AN_ADDRESS, bans.ban("example.com"))
        assertEquals(IpBans.Result.NOT_AN_ADDRESS, bans.ban("1.2.3"))
        assertEquals(IpBans.Result.NOT_AN_ADDRESS, bans.ban("1.2.3.256"))
        assertEquals(IpBans.Result.NOT_AN_ADDRESS, bans.ban("01.2.3.4"))
        assertFalse(bans.isBanned("192.168.0.216"))
        assertFalse(bans.isBanned("none"))
        // A note can't break the file's one-line-per-ban layout.
        assertEquals(IpBans.Result.BANNED, bans.ban("10.0.0.1", "two\nlines"))
        assertEquals("two lines", bans.all["10.0.0.1"])
    }
}
