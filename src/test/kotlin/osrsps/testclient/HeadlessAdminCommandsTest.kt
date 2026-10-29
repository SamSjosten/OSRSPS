package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Accounts
import osrsps.game.MemorySaveStore
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Scenario
import osrsps.game.Scenarios
import osrsps.game.World
import osrsps.game.combat.MeleeCombat
import osrsps.game.command.Admins
import osrsps.map.CollisionMap

/**
 * The testing commands and the ctrl+shift teleport are for admins only: in an ordinary player's hands they made items
 * (`::item`; `::scenario`, drop, `::reset`, take) and kept every item through a death (`::scenario` while dead). From
 * the exploit review of 29 Oct 2026.
 */
class HeadlessAdminCommandsTest {
    private val coins = ObjType(COINS, "Coins", cost = 1, stackable = true, tradeable = true)
    private val gift = Scenario.parse("gift", """{ "position": { "x": 3222, "z": 3218 }, "inventory": [ { "item": "$COINS", "count": 100 } ] }""")

    /**
     * Lumbridge's map square, open ground; only [ADMIN] is an admin. Its name is claimed up front, with the password
     * [TestServer.login] uses, as a listed admin's name can't be claimed by a login.
     */
    private fun server(dir: Path, admins: Admins = Admins.parse(ADMIN)) = TestServer(dir) { crcs, millis ->
        val accounts = Accounts(MemorySaveStore(), iterations = 1_000).apply { check(ADMIN, "test".toCharArray()) }
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            objTypes = ObjTypes.of(coins), scenarios = Scenarios(listOf(gift)), admins = admins, accounts = accounts, cycleMillis = millis,
        )
    }

    @Test
    fun `an ordinary player can't make items, XP, levels or vars, teleport, open interfaces or start a scenario`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("mallory").use { mallory ->
                mallory.await("the login rebuild") { mallory.position }
                for (command in listOf("item $COINS 1000", "empty", "setlevel all 99", "addxp magic 1000", "boost strength 255", "varp 173 1", "varbit 357 1", "tele 3223 3218", "if 672", "ifevents 116 95 -1 -1 1", "scenario gift", "reset", "grounditems", "anim 836", "queue weak")) {
                    val name = command.substringBefore(' ')
                    assertEquals("::$name is a testing tool, so only admins can use it here.", mallory.commandReply(command) { it.startsWith("::$name") })
                }
                // The ones that only read or only show the player something still work.
                assertTrue(mallory.commandReply("pos") { it.contains("3222") }.contains("3218"))
                assertTrue(mallory.commandReply("tick") { it.startsWith("Tick") }.startsWith("Tick"))
                assertTrue(mallory.inventory(BACKPACK).all { it == null })
                assertEquals(Coord(3222, 3218), mallory.position)
            }
        }
    }

    @Test
    fun `the admin still has them`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login(ADMIN).use { boss ->
                boss.await("the login rebuild") { boss.position }
                assertTrue(boss.commandReply("item $COINS 1000") { it.startsWith("Added") }.startsWith("Added"))
                assertTrue(boss.commandReply("scenario gift") { it.startsWith("Started") }.startsWith("Started"))
                assertTrue(boss.commandReply("reset") { it.startsWith("Back to") }.startsWith("Back to"))
                boss.await("the coins back") { boss.inventory(BACKPACK).firstOrNull()?.takeIf { it == InvObj(COINS, 1000) } }
            }
        }
    }

    @Test
    fun `an ordinary player's ctrl+shift click walks, and an admin's won't land where there is no map`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("mallory").use { mallory ->
                server.login(ADMIN).use { boss ->
                    mallory.await("the login rebuild") { mallory.position }
                    boss.await("the login rebuild") { boss.position }
                    // Two tiles away: a walk takes a tick or more a tile, a teleport lands at once and sends no walk steps.
                    mallory.walk(3224, 3218, keyCombination = CTRL_SHIFT)
                    mallory.await("the walk there") { mallory.position?.takeIf { it == Coord(3224, 3218) } }
                    val there = mallory.moves().map { it.move }.filter { it.to == Coord(3224, 3218) }
                    assertTrue(there.isNotEmpty() && there.none { it.type == MoveType.TELEPORT }, "walked, not teleported: $there")
                    // Far away, the walk can't get there and she stays in Lumbridge.
                    mallory.walk(0, 0, keyCombination = CTRL_SHIFT)
                    mallory.awaitTicks(3)
                    assertTrue(mallory.position!!.x > 3000)

                    boss.walk(3100, 3100, keyCombination = CTRL_SHIFT)
                    assertEquals("No map at 3100, 3100", boss.awaitMessage("no map") { it.startsWith("No map") }.text)
                    boss.walk(3230, 3230, keyCombination = CTRL_SHIFT)
                    boss.await("the admin's teleport") { boss.position?.takeIf { it == Coord(3230, 3230) } }
                }
            }
        }
    }

    @Test
    fun `a dead player can't run the admin commands, so a scenario can't keep their items from the grave`(@TempDir dir: Path) {
        server(dir, Admins.EVERYONE).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $COINS 1000")
                client.await("the coins") { client.inventory(BACKPACK).firstOrNull() }
                client.command("hit 99 me")
                client.await("[combat.dead]") { client.messages().firstOrNull { it.text == "[combat.dead]" } }
                assertEquals("You can't use ::scenario while you are dead.", client.commandReply("scenario gift") { it.contains("::scenario") })
                assertEquals("You can't use ::tele while you are dead.", client.commandReply("tele 3230 3230") { it.contains("::tele") })
                assertTrue(client.commandReply("pos") { it.contains("3218") }.isNotEmpty())
            }
        }
    }

    private companion object {
        const val ADMIN = "boss"
        const val COINS = 995
        const val BACKPACK = 93
        const val CTRL_SHIFT = 2
    }
}
