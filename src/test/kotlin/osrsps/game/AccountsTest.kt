package osrsps.game

import java.nio.file.Files
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
    fun `a password set by another process wins over a login that claims after it`() {
        val dir = Files.createTempDirectory("accounts")
        try {
            val server = Accounts(FileSaveStore(dir), iterations = 1_000, lock = FileAccountLock(dir))
            val tool = Accounts(FileSaveStore(dir), iterations = 1_000, lock = FileAccountLock(dir))
            assertTrue(tool.setPassword("bob", "the owner's password".toCharArray()))
            assertEquals(Accounts.Check.WRONG_PASSWORD, server.check("bob", "a stranger's".toCharArray()))
            assertEquals(Accounts.Check.MATCHED, server.check("bob", "the owner's password".toCharArray()))
            assertTrue(Files.exists(dir.resolve(".lock")))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
