package osrsps.game.trade

import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.FileSaveStore
import osrsps.game.MemorySaveStore
import osrsps.game.Player
import osrsps.game.PlayerSave
import osrsps.game.World
import osrsps.map.CollisionMap

/** The trade journal: a crash between the two save files of a trade never lands it on one side only. */
class TradeJournalTest {
    /** Keeps saves in memory, and throws on the write of [failOn] while it is set: the crash between two saves. */
    private class CrashingStore : osrsps.game.SaveStore {
        val inner = MemorySaveStore()
        var failOn: String? = null

        override fun read(key: String): String? = inner.read(key)

        override fun write(key: String, text: String) {
            if (key == failOn) throw IOException("power cut (test)")
            inner.write(key, text)
        }

        override fun readJournal(): String? = inner.journal

        override fun writeJournal(text: String) = inner.writeJournal(text)

        override fun clearJournal() = inner.clearJournal()
    }

    private fun world(saves: osrsps.game.SaveStore) = World(IntArray(0), CollisionMap(), saves = saves)

    private fun player(name: String) = Player(1, name, 3222, 3218, 0)

    @Test
    fun `a journal reads back as it was written, and anything else is refused`() {
        val journal = TradeJournal("0000000000001-000000001", mapOf("alice" to "{\"a\":1}", "bob" to "{}"))
        val back = TradeJournal.fromJson(journal.toJson())
        assertEquals(journal.id, back.id)
        assertEquals(journal.saves, back.saves)
        assertFailsWith<IllegalArgumentException> { TradeJournal.fromJson("{\"id\":\"x\"}") }
        assertFailsWith<IllegalArgumentException> { TradeJournal.fromJson("half a jour") }
    }

    @Test
    fun `a completed trade writes both saves naming it, and clears the journal`() {
        val store = MemorySaveStore()
        val world = world(store)
        val alice = player("alice")
        val bob = player("bob")
        var applied = false
        world.commitTrade(alice, bob) { applied = true }
        assertTrue(applied)
        val id = assertNotNull(alice.lastTrade)
        assertEquals(id, bob.lastTrade)
        assertEquals(id, PlayerSave.lastTradeIn(store.saves.getValue("alice")))
        assertEquals(id, PlayerSave.lastTradeIn(store.saves.getValue("bob")))
        assertNull(store.journal)
        // The next trade's id sorts after this one's.
        world.commitTrade(alice, bob) {}
        assertTrue(alice.lastTrade!! > id)
    }

    @Test
    fun `a crash after the first save leaves the journal, and the next start writes the second`() {
        val store = CrashingStore()
        store.inner.write("bob", PlayerSave.of(player("bob")).toJson("bob"))
        store.failOn = "bob"
        val alice = player("alice")
        val bob = player("bob")
        world(store).commitTrade(alice, bob) {}
        val id = alice.lastTrade!!
        assertEquals(id, PlayerSave.lastTradeIn(store.inner.saves.getValue("alice")))
        assertNull(PlayerSave.lastTradeIn(store.inner.saves.getValue("bob")))
        assertNotNull(store.inner.journal)
        // The restart.
        store.failOn = null
        world(store)
        assertEquals(id, PlayerSave.lastTradeIn(store.inner.saves.getValue("bob")))
        assertNull(store.inner.journal)
    }

    @Test
    fun `a journal never rolls back a save that already has that trade or a later one`() {
        val store = MemorySaveStore()
        val alice = player("alice").apply { lastTrade = "0000000000002-000000001" }
        val newer = PlayerSave.of(alice).toJson("alice")
        store.write("alice", newer)
        val old = PlayerSave.of(player("alice").apply { lastTrade = "0000000000001-000000001" }).toJson("alice")
        val bobsSave = PlayerSave.of(player("bob").apply { lastTrade = "0000000000001-000000001" }).toJson("bob")
        store.writeJournal(TradeJournal("0000000000001-000000001", mapOf("alice" to old, "bob" to bobsSave)).toJson())
        world(store)
        assertEquals(newer, store.saves["alice"])
        assertEquals(bobsSave, store.saves["bob"])
        assertNull(store.journal)
    }

    @Test
    fun `a journal that can't be read is left for a look`() {
        val store = MemorySaveStore()
        store.writeJournal("half a jour")
        world(store)
        assertEquals("half a jour", store.journal)
    }

    @Test
    fun `the file store keeps the journal beside the saves, and clears it`(@TempDir dir: Path) {
        val store = FileSaveStore(dir)
        assertNull(store.readJournal())
        store.writeJournal("{\"id\":\"1\"}")
        assertEquals("{\"id\":\"1\"}", store.readJournal())
        assertEquals(listOf("trade.journal"), dir.listDirectoryEntries().map { it.name })
        store.clearJournal()
        assertNull(store.readJournal())
        store.clearJournal()
    }
}
