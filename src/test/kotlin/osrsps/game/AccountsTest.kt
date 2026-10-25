package osrsps.game

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
}
