package osrsps.game

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountsTest {
    private val store = MemorySaveStore()
    private val accounts = Accounts(store, iterations = 1_000)

    @Test
    fun `the first login claims a name and later ones must match its password`() {
        assertEquals(Accounts.Check.CLAIMED, accounts.check("Zezima", "hunter2".toCharArray()))
        assertEquals(Accounts.Check.MATCHED, accounts.check("Zezima", "hunter2".toCharArray()))
        assertEquals(Accounts.Check.WRONG_PASSWORD, accounts.check("Zezima", "hunter3".toCharArray()))
        assertEquals(Accounts.Check.WRONG_PASSWORD, accounts.check("Zezima", CharArray(0)))
    }

    @Test
    fun `names match the way saves do`() {
        accounts.check("Iron Man", "pw".toCharArray())
        assertEquals(Accounts.Check.MATCHED, accounts.check("iron_man ", "pw".toCharArray()))
        assertEquals(Accounts.Check.WRONG_PASSWORD, accounts.check("IRON MAN", "other".toCharArray()))
    }

    @Test
    fun `each name is its own account`() {
        accounts.check("alice", "a".toCharArray())
        assertEquals(Accounts.Check.CLAIMED, accounts.check("bob", "a".toCharArray()))
    }

    @Test
    fun `the file keeps a salted hash, never the password`() {
        accounts.check("alice", "correct horse".toCharArray())
        accounts.check("bob", "correct horse".toCharArray())
        val alice = store.saves.getValue("alice")
        assertFalse("correct horse" in alice, alice)
        assertTrue(""""algorithm":"PBKDF2WithHmacSHA256"""" in alice, alice)
        // Same password, different salt: the hashes differ.
        fun hash(key: String) = Regex(""""hash":"([^"]+)"""").find(store.saves.getValue(key))!!.groupValues[1]
        assertTrue(hash("alice") != hash("bob"))
    }

    @Test
    fun `an account keeps the rounds it was hashed with`() {
        accounts.check("alice", "pw".toCharArray())
        val stronger = Accounts(store, iterations = 2_000)
        assertEquals(Accounts.Check.MATCHED, stronger.check("alice", "pw".toCharArray()))
    }

    @Test
    fun `a name no save can be kept under is left to the login`() {
        assertEquals(Accounts.Check.NO_SUCH_NAME, accounts.check("   ", "pw".toCharArray()))
        assertTrue(store.saves.isEmpty())
    }

    @Test
    fun `names keep to live's rules`() {
        for (name in listOf("Zezima", "iron man", "a_b-c", "123456789012", " padded ")) assertTrue(Accounts.validName(name), name)
        for (name in listOf("", "   ", "1234567890123", "a.b", "tab\tname", "line\nbreak", "Zеzima", "ünder")) {
            assertFalse(Accounts.validName(name), name)
            assertEquals(Accounts.Check.NO_SUCH_NAME, accounts.check(name, "pw".toCharArray()), name)
        }
        assertTrue(store.saves.isEmpty())
    }

    @Test
    fun `a name that may not be claimed is left unclaimed, but one already claimed still logs in`() {
        assertEquals(Accounts.Check.NOT_CLAIMABLE, accounts.check("boss", "pw".toCharArray(), mayClaim = false))
        assertTrue(store.saves.isEmpty())
        accounts.check("boss", "pw".toCharArray())
        assertEquals(Accounts.Check.MATCHED, accounts.check("boss", "pw".toCharArray(), mayClaim = false))
        assertEquals(Accounts.Check.WRONG_PASSWORD, accounts.check("boss", "nope".toCharArray(), mayClaim = false))
    }

    @Test
    fun `setting a password replaces the old one`() {
        accounts.check("alice", "old".toCharArray())
        assertTrue(accounts.setPassword("Alice", "new password".toCharArray()))
        assertEquals(Accounts.Check.WRONG_PASSWORD, accounts.check("alice", "old".toCharArray()))
        assertEquals(Accounts.Check.MATCHED, accounts.check("alice", "new password".toCharArray()))
        assertFalse("new password" in store.saves.getValue("alice"))
    }

    @Test
    fun `setting a password claims a name nobody has, even one a login may not claim`() {
        assertEquals(Accounts.Check.NOT_CLAIMABLE, accounts.check("admin", "pw".toCharArray(), mayClaim = false))
        assertTrue(accounts.setPassword("admin", "a long password".toCharArray()))
        assertEquals(Accounts.Check.MATCHED, accounts.check("admin", "a long password".toCharArray(), mayClaim = false))
    }

    @Test
    fun `setting a password refuses names a login would`() {
        assertFalse(accounts.setPassword("sam@example.com", "a long password".toCharArray()))
        assertFalse(accounts.setPassword("   ", "a long password".toCharArray()))
        assertTrue(store.saves.isEmpty())
    }

    @Test
    fun `a password set between a login's first read and its claim wins`() {
        val dir = Files.createTempDirectory("accounts")
        try {
            val files = FileSaveStore(dir)
            val tool = Accounts(files, iterations = 1_000, lock = FileAccountLock(dir))
            // The login's first read finds no file; set-password writes one right then, before the claim.
            var first = true
            val racing = object : SaveStore {
                override fun read(key: String): String? = files.read(key).also {
                    if (first) {
                        first = false
                        assertTrue(tool.setPassword("bob", "the owner's password".toCharArray()))
                    }
                }

                override fun write(key: String, text: String) = files.write(key, text)
            }
            val server = Accounts(racing, iterations = 1_000, lock = FileAccountLock(dir))
            assertEquals(Accounts.Check.WRONG_PASSWORD, server.check("bob", "a stranger's".toCharArray()))
            assertEquals(Accounts.Check.MATCHED, server.check("bob", "the owner's password".toCharArray()))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `the account lock lets one writer in at a time`() {
        val dir = Files.createTempDirectory("accounts")
        try {
            val holding = CountDownLatch(1)
            val release = CountDownLatch(1)
            val entered = CountDownLatch(1)
            val first = thread { FileAccountLock(dir).withLock { holding.countDown(); release.await() } }
            assertTrue(holding.await(5, TimeUnit.SECONDS))
            val second = thread { FileAccountLock(dir).withLock { entered.countDown() } }
            assertFalse(entered.await(300, TimeUnit.MILLISECONDS), "the second writer got in while the first held the lock")
            release.countDown()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            first.join()
            second.join()
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
