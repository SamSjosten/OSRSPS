package osrsps.net

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

/** The login key: the live server's must be the one whose modulus is committed, and it never makes a new one. */
class RsaKeysTest {
    @Test
    fun `a local server makes a key when it has none, and keeps it`(@TempDir dir: Path) {
        val made = RsaKeys.load(dir)
        assertTrue(Files.exists(dir.resolve("rsa.key")))
        assertEquals(made.modulus.toString(16) + "\n", Files.readString(dir.resolve("rsa-modulus.txt")))
        assertEquals(made.modulus, RsaKeys.load(dir).modulus)
    }

    @Test
    fun `the live server won't start without its key, and makes none`(@TempDir dir: Path) {
        assertFailsWith<IllegalStateException> { RsaKeys.load(dir, RsaKeys.committedModulus()) }
        assertFalse(Files.exists(dir.resolve("rsa.key")))
    }

    @Test
    fun `the live server won't start with another key than the committed one`(@TempDir dir: Path) {
        RsaKeys.load(dir)
        val refused = assertFailsWith<IllegalStateException> { RsaKeys.load(dir, RsaKeys.committedModulus()) }
        assertContains(refused.message!!, "committed")
    }

    @Test
    fun `the live server takes the key whose modulus it expects`(@TempDir dir: Path) {
        val modulus = RsaKeys.load(dir).modulus
        assertEquals(modulus, RsaKeys.load(dir, modulus).modulus)
    }

    @Test
    fun `the committed modulus is a 1024-bit key's`() {
        assertEquals(RsaKeys.BITS, RsaKeys.committedModulus().bitLength())
    }
}
