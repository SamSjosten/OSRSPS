package osrsps.testclient

import org.junit.jupiter.api.io.TempDir
import osrsps.game.World
import osrsps.game.content.ClanHub
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.social.Clans
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Clan Hub's recruitment board through the headless client: View opens the board with the listed clans, List your
 * clan puts a recruiter's clan on it, the filters narrow it, and View clan opens the clan's settings page as its guest.
 * The rl-client script test-runner/examples/clan-board.json does the same on the real map.
 */
class HeadlessClanBoardTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply { markLoaded(TABLE_X shr 6, TABLE_Z shr 6) },
            locTypes = LocTypes(
                mapOf(
                    ClanHub.RECRUITMENT_TABLE to LocType(
                        ClanHub.RECRUITMENT_TABLE, "Clan recruitment board", ops = listOf("View", null, null, null, null),
                        collision = LocCollisionType(width = 2, length = 1),
                    ),
                ),
            ),
            staticLocs = StaticLocs().apply { add(ZoneLoc(ClanHub.RECRUITMENT_TABLE, LocShape.CENTREPIECE_STRAIGHT, 2, TABLE_X, TABLE_Z, 0)) },
            cycleMillis = millis,
            clansDir = dir.resolve("data"),
            // A fixed clock, so the clan's age (by UTC date) can't tick over midnight mid-test.
            clansClock = { CLOCK },
        )
    }

    private fun HeadlessClient.view() {
        command("tele $TABLE_X ${TABLE_Z - 1} 0")
        await("beside the table") { position?.takeIf { it == Coord(TABLE_X, TABLE_Z - 1, 0) } }
        locOp(ClanHub.RECRUITMENT_TABLE, TABLE_X, TABLE_Z, ClanHub.VIEW)
        await("the board") { if (Clans.BOARD in openInterfaces()) Unit else null }
    }

    /** The board's last full list: its 45 clan_board_addline texts, waiting for [after] scripts to have come first. */
    private fun HeadlessClient.board(after: Int): List<String> = await("the board's list") {
        val lines = clientScripts().drop(after).filter { it.id == Clans.SCRIPT_BOARD_ADD_LINE }
        if (lines.size >= Clans.BOARD_SLOTS) lines.takeLast(Clans.BOARD_SLOTS).map { it.args[1] as String } else null
    }

    @Test
    fun `a recruiter lists the clan, others see it through the filters, and View clan opens it as a guest`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.await("the login rebuild") { alice.position }
                    bob.await("the login rebuild") { bob.position }

                    // Nothing listed yet; in no clan, List your clan is refused.
                    bob.view()
                    assertTrue(bob.board(0).all { it.isEmpty() })
                    bob.resumePause(Clans.BOARD, Clans.BOARD_UNIVERSE, Clans.BOARD_MY_CLAN)
                    bob.await("refused") { bob.messages().firstOrNull { it.text == "[clan.board_no_clan]" } }

                    alice.commandReply("clanmake Odins Gate") { it.startsWith("Clan") }
                    alice.commandReply("clanjoin Odins Gate recruiter") { it.startsWith("Joined") }
                    alice.view()
                    assertEquals(0, alice.varp(Clans.VARP_BOARD_LISTED))
                    alice.resumePause(Clans.BOARD, Clans.BOARD_UNIVERSE, Clans.BOARD_MY_CLAN)
                    alice.await("listed") { alice.messages().firstOrNull { it.text == "[clan.board_listed clan=Odins Gate]" } }
                    alice.await("De-list") { alice.varp(Clans.VARP_BOARD_LISTED).takeIf { it == 1 } }
                    // Its own members don't see it.
                    assertTrue(alice.board(alice.clientScripts().size - Clans.BOARD_SLOTS).all { it.isEmpty() })

                    var seen = bob.clientScripts().size
                    bob.resumePause(Clans.BOARD, Clans.BOARD_UNIVERSE, Clans.BOARD_REFRESH)
                    val list = bob.board(seen)
                    assertEquals("Odins Gate|${server.world.clans.clanNamed("Odins Gate")!!.members.size}|0|0|${"0".repeat(Clans.INTERESTS)}||", list[0])
                    assertTrue(list.drop(1).all { it.isEmpty() })

                    // Wanting an interest it doesn't list leaves nothing; not wanting it shows it again; Reset clears both.
                    seen = bob.clientScripts().size
                    bob.resumePause(Clans.BOARD, Clans.BOARD_FILTERS, 2)
                    assertTrue(bob.board(seen).all { it.isEmpty() })
                    assertEquals(1 shl 2, bob.varp(Clans.VARP_BOARD_REQUIRE))
                    seen = bob.clientScripts().size
                    bob.resumePause(Clans.BOARD, Clans.BOARD_FILTERS, 2)
                    assertEquals("Odins Gate", bob.board(seen)[0].substringBefore('|'))
                    assertEquals(1 shl 2, bob.varp(Clans.VARP_BOARD_EXCLUDE))
                    seen = bob.clientScripts().size
                    bob.resumePause(Clans.BOARD, Clans.BOARD_UNIVERSE, Clans.BOARD_RESET)
                    bob.board(seen)
                    bob.await("filters off") { if (bob.varp(Clans.VARP_BOARD_EXCLUDE) == 0 && bob.varp(Clans.VARP_BOARD_REQUIRE) == 0) Unit else null }

                    // View clan: its settings page, as its guest.
                    bob.resumePause(Clans.BOARD, Clans.BOARD_LIST, 0)
                    bob.await("the clan's settings page") { if (Clans.INFO in bob.openInterfaces()) Unit else null }
                    val clan = server.world.clans.clanNamed("Odins Gate")!!
                    assertEquals(clan.id, server.world.clans.guestClanOf(server.world.playerNamed("bob")!!)?.id)

                    // De-list takes it off.
                    alice.resumePause(Clans.BOARD, Clans.BOARD_UNIVERSE, Clans.BOARD_MY_CLAN)
                    alice.await("de-listed") { alice.messages().firstOrNull { it.text == "[clan.board_delisted clan=Odins Gate]" } }
                    alice.await("List your clan") { alice.varp(Clans.VARP_BOARD_LISTED).takeIf { it == 0 } }
                    assertTrue(server.world.clans.boardClans(server.world.playerNamed("bob")!!).isEmpty())
                }
            }
        }
    }

    private companion object {
        val CLOCK: Instant = Instant.parse("2026-11-19T12:00:00Z")
        const val TABLE_X = 3177
        const val TABLE_Z = 3469
    }
}
