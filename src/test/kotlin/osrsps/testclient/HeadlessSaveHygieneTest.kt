package osrsps.testclient

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import osrsps.game.Accounts
import osrsps.game.MemorySaveStore
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.PlayerSave
import osrsps.game.SaveStore
import osrsps.game.World
import osrsps.game.command.Admins
import osrsps.map.CollisionMap

/**
 * Saves and names, from the exploit review of 29 Oct 2026: a drop handed to another player is saved on both sides at
 * once, a save that can't be written isn't lost, names keep to live's rules, and a listed admin's name can't be claimed
 * by whoever logs in as it first.
 */
class HeadlessSaveHygieneTest {
    /** Saves the test thread can read while the game thread writes, and that throw while [failing]. */
    private class FlakyStore : SaveStore {
        val saves = ConcurrentHashMap<String, String>()

        /** Every key written, in order. */
        val written: MutableList<String> = java.util.Collections.synchronizedList(ArrayList())

        @Volatile
        var failing = false

        override fun read(key: String): String? = saves[key]

        override fun write(key: String, text: String) {
            if (failing) throw IOException("disk full (test)")
            saves[key] = text
            written += key
        }
    }

    private fun server(dir: Path, saves: SaveStore, cycleMillis: Long = 20, accounts: Accounts? = null, admins: Admins = Admins.EVERYONE) =
        TestServer(dir, cycleMillis = cycleMillis) { crcs, millis ->
            val objs = arrayOfNulls<ObjType>(AXE + 1)
            objs[AXE] = ObjType(AXE, "Bronze axe", tradeable = true, iops = listOf(null, "Wield", null, null, "Drop"))
            World(
                crcs, CollisionMap().apply { markLoaded(World.SPAWN_X shr 6, World.SPAWN_Z shr 6) }, objTypes = ObjTypes(objs),
                saves = saves, accounts = accounts ?: Accounts(MemorySaveStore(), iterations = 1_000), admins = admins, cycleMillis = millis,
            )
        }

    private fun axes(save: String?): Int = PlayerSave.fromJson(save!!).inventory.values.count { it.id == AXE }

    @Test
    fun `taking another player's drop saves them both at once`(@TempDir dir: Path) {
        val store = FlakyStore()
        server(dir, store).use { server ->
            server.login("frank").use { frank ->
                server.login("gina").use { gina ->
                    frank.await("the login rebuild") { frank.position }
                    gina.await("the login rebuild") { gina.position }
                    frank.command("item $AXE")
                    val slot = frank.await("the axe") { frank.inventory(BACKPACK).indexOfFirst { it?.id == AXE }.takeIf { it >= 0 } }
                    frank.ifButton(INVENTORY, INVENTORY_ITEMS, DROP, slot, AXE)
                    // Public after 100 ticks, 2 s here.
                    gina.await("the axe turning public", timeoutMillis = 10_000) { gina.groundObjs().firstOrNull { it.id == AXE } }
                    store.saves.clear()
                    store.written.clear()
                    gina.objOp(AXE, World.SPAWN_X, World.SPAWN_Z, TAKE)
                    gina.await("the axe in her backpack") { gina.inventory(BACKPACK).firstOrNull { it?.id == AXE } }
                    // Both saved by the take itself, with both still in the world: a crash now gives the axe to gina alone.
                    // Within a second, long before the autosave (every 300 cycles, 6 s here) could have written them.
                    gina.await("both saves", timeoutMillis = 1_000) { if (store.saves.keys.containsAll(listOf("frank", "gina"))) Unit else null }
                    assertEquals(0, axes(store.saves["frank"]))
                    assertEquals(1, axes(store.saves["gina"]))
                    // Frank first: a crash between the writes loses the axe rather than leaving it in both saves.
                    assertEquals(listOf("frank", "gina"), store.written.take(2))
                }
            }
        }
    }

    @Test
    fun `a save that can't be written is kept, loaded in its place, and written once it can be`(@TempDir dir: Path) {
        val store = FlakyStore()
        server(dir, store).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                alice.command("item $AXE")
                alice.await("the axe") { alice.inventory(BACKPACK).firstOrNull { it?.id == AXE } }
                store.failing = true
                alice.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                alice.await("the logout") { alice.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            await("alice gone") { if (server.world.playerNamed("alice") == null) Unit else null }
            assertEquals(setOf("alice"), server.world.unwrittenSaves)
            assertEquals(null, store.saves["alice"])
            // Logging back in loads the save the disk refused, not the file (here none: a new player with no axe).
            server.login("alice").use { alice ->
                alice.await("the axe, from the unwritten save") { alice.inventory(BACKPACK).firstOrNull { it?.id == AXE } }
                alice.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                alice.await("the logout") { alice.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            await("alice gone again") { if (server.world.playerNamed("alice") == null) Unit else null }
            store.failing = false
            // The next autosave (300 cycles, 6 s here) writes it though alice is no longer in the world.
            await("the autosave", timeoutMillis = 20_000) { store.saves["alice"] }
            assertEquals(1, axes(store.saves["alice"]))
            await("nothing left unwritten") { if (server.world.unwrittenSaves.isEmpty()) Unit else null }
        }
    }

    @Test
    fun `names outside live's rules are refused before they are logged or claimed`(@TempDir dir: Path) {
        val accountStore = MemorySaveStore()
        val logged = ListAppender<ILoggingEvent>().apply { start() }
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        root.addAppender(logged)
        try {
            server(dir, MemorySaveStore(), cycleMillis = 100, accounts = Accounts(accountStore, iterations = 1_000)).use { server ->
                for (name in listOf("fake\nline", "thirteenchars", "Zеzima", "a.b")) {
                    val refused = assertFailsWith<LoginFailedException>(name) { server.login(name, "pw") }
                    assertEquals(INVALID_USERNAME_OR_PASSWORD, refused.code, name)
                }
                server.login("iron man", "pw").close()
            }
        } finally {
            root.detachAppender(logged)
        }
        val lines = logged.list.map { it.formattedMessage }
        assertTrue(lines.none { "fake" in it || "thirteen" in it }, lines.joinToString("\n"))
        assertEquals(setOf("iron_man"), accountStore.saves.keys)
    }

    @Test
    fun `a listed admin's name can't be claimed by a login, and works once claimed before listing`(@TempDir dir: Path) {
        val accountStore = MemorySaveStore()
        val accounts = Accounts(accountStore, iterations = 1_000)
        server(dir, MemorySaveStore(), cycleMillis = 100, accounts = accounts, admins = Admins.parse("boss,Owner")).use { server ->
            val refused = assertFailsWith<LoginFailedException> { server.login("Boss", "stranger") }
            assertEquals(INVALID_USERNAME_OR_PASSWORD, refused.code)
            assertTrue("boss" !in accountStore.saves, "a stranger's login didn't claim the admin's name")
            // Anyone else's name is claimed as before.
            server.login("alice", "pw").close()
        }
        // The owner claimed it while it wasn't listed (here, straight through the accounts); listed now, it logs in.
        assertEquals(Accounts.Check.CLAIMED, accounts.check("owner", "theirs".toCharArray()))
        server(dir.resolve("again"), MemorySaveStore(), cycleMillis = 100, accounts = accounts, admins = Admins.parse("boss,Owner")).use { server ->
            server.login("Owner", "theirs").use { owner -> owner.await("the login rebuild") { owner.position } }
            assertEquals(INVALID_USERNAME_OR_PASSWORD, assertFailsWith<LoginFailedException> { server.login("owner", "guess") }.code)
        }
    }

    private fun <T : Any> await(what: String, timeoutMillis: Long = 10_000, value: () -> T?): T {
        val end = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < end) {
            value()?.let { return it }
            Thread.sleep(20)
        }
        throw AssertionError("Timed out waiting for $what")
    }

    private companion object {
        const val BACKPACK = 93
        const val AXE = 1351
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
        const val DROP = 7
        const val TAKE = 3

        /** The client's login response for "Invalid username or password". */
        const val INVALID_USERNAME_OR_PASSWORD = 3
    }
}
