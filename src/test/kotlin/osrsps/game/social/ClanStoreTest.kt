package osrsps.game.social

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClanStoreTest {
    private fun founders() = listOf(ClanStore.Member("alice", "alice", 126, 1), ClanStore.Member("bob", "bob", 0, 1))

    @Test
    fun `a clan founded while the file can't be written is written at the next flush`(@TempDir dir: Path) {
        // A file where the data directory should be: creating it fails until the file goes.
        val blocker = dir.resolve("data")
        Files.writeString(blocker, "in the way")
        val store = ClanStore(blocker.resolve(ClanStore.FILE_NAME))
        store.create("Odins Gate", 0, 20_000, founders())
        assertTrue(store.unwritten)
        Files.delete(blocker)
        store.flush()
        assertFalse(store.unwritten)
        val reloaded = ClanStore(blocker.resolve(ClanStore.FILE_NAME))
        assertEquals(listOf("alice", "bob"), reloaded.clanOf("bob")!!.members.map { it.name })
        assertEquals(20_000, reloaded.foundedDay("alice"))
    }

    @Test
    fun `names are compared as player names are, and a deleted clan's members may found again`(@TempDir dir: Path) {
        val store = ClanStore(dir.resolve(ClanStore.FILE_NAME))
        val clan = store.create("Odins Gate", 0, 20_000, founders())
        assertTrue(store.nameTaken("odins_gate"))
        assertFalse(store.nameTaken("Odins Gates"))
        store.delete(clan)
        assertNull(store.clanOf("alice"))
        assertNull(store.foundedDay("alice"))
        assertFalse(ClanStore(dir.resolve(ClanStore.FILE_NAME)).nameTaken("Odins Gate"))
    }

    @Test
    fun `a name that only reads as a taken one, by an inner double space or an accent, isn't allowed`() {
        val store = ClanStore(null)
        store.create("Odins Gate", 0, 0, founders())
        assertTrue(store.nameTaken("odins  gate"))
        assertFalse(Clans.validName("Odins  Gate"))
        assertFalse(Clans.validName("Ódins Gate"))
        assertTrue(Clans.validName("Odins Gate 2"))
    }

    @Test
    fun `applicants and leaving members are kept in the file`(@TempDir dir: Path) {
        val store = ClanStore(dir.resolve(ClanStore.FILE_NAME))
        val clan = store.create("Odins Gate", 0, 20_000, founders())
        store.addApplicant(clan, "carol", "Carol")
        store.addApplicant(clan, "carol", "Carol")
        store.addApplicant(clan, "dave", "dave")
        assertTrue(store.removeApplicant(clan, "dave"))
        assertFalse(store.removeApplicant(clan, "dave"))
        // Applicants wait for the autosave's flush; a member leaving is written at once, with them.
        assertTrue(store.unwritten)
        assertTrue(ClanStore(dir.resolve(ClanStore.FILE_NAME)).byName("Odins Gate")!!.applicants.isEmpty())
        store.flush()
        assertFalse(store.unwritten)
        assertTrue(store.removeMember(clan, "bob"))
        val reloaded = ClanStore(dir.resolve(ClanStore.FILE_NAME)).byName("Odins Gate")!!
        assertEquals(listOf("carol" to "Carol"), reloaded.applicants.map { it.key to it.name })
        assertEquals(listOf("alice"), reloaded.members.map { it.key })
    }

    @Test
    fun `an applicant's levels are kept, and joining a clan drops every application`(@TempDir dir: Path) {
        val store = ClanStore(dir.resolve(ClanStore.FILE_NAME))
        val clan = store.create("Odins Gate", 0, 20_000, founders())
        val other = store.create("Rivals", 0, 20_000, listOf(ClanStore.Member("erin", "erin", 126, 9000)))
        store.addApplicant(clan, "carol", "Carol", "3|33")
        store.addApplicant(other, "carol", "Carol")
        store.addApplicant(other, "dave", "dave")
        store.flush()
        store.setApplicantStats(clan.applicant("carol")!!, "3|33")
        assertFalse(store.unwritten)
        store.setApplicantStats(clan.applicant("carol")!!, "4|40")
        assertTrue(store.unwritten)
        store.flush()
        val reloaded = ClanStore(dir.resolve(ClanStore.FILE_NAME))
        assertEquals("4|40", reloaded.byName("Odins Gate")!!.applicant("carol")!!.stats)
        // Never taken: none.
        assertEquals("", reloaded.byName("Rivals")!!.applicant("carol")!!.stats)

        store.removeApplications("carol")
        assertTrue(store.unwritten)
        assertTrue(clan.applicants.isEmpty())
        assertEquals(listOf("dave"), other.applicants.map { it.key })
    }
}
