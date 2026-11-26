package osrsps.testclient

import org.junit.jupiter.api.io.TempDir
import osrsps.game.World
import osrsps.game.content.ClanHall
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.social.Clans
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The clan hall portal and the clan halls through the headless client: Enter and Enter-member into one's own clan's
 * hall (one instance for the clan, shared), Enter-guest by a clan's name, the Enter clan hall permission and the ban
 * list keeping guests out, and the exit portal's Leave. The rl-client script test-runner/examples/clan-hall.json does
 * the same on the real map.
 */
class HeadlessClanHallTest {
    private val types = listOf(
        LocType(ClanHall.PORTAL, "Clan hall portal", ops = listOf("Enter", "Enter-member", "Enter-guest", null, null), collision = LocCollisionType(width = 1, length = 1)),
        LocType(ClanHall.EXIT, "Portal", ops = listOf("Leave", null, null, null, null), collision = LocCollisionType(width = 2, length = 1)),
    ).associateBy { it.id }

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(ClanHall.TEMPLATE_X shr 6, ClanHall.TEMPLATE_Z shr 6)
                markLoaded(PORTAL_X shr 6, PORTAL_Z shr 6)
            },
            locTypes = LocTypes(types),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(ClanHall.PORTAL, LocShape.CENTREPIECE_STRAIGHT, 0, PORTAL_X, PORTAL_Z, 0))
                add(ZoneLoc(ClanHall.EXIT, LocShape.CENTREPIECE_STRAIGHT, 0, EXIT_X, EXIT_Z, 0))
            },
            cycleMillis = millis,
            clansDir = dir.resolve("data"),
        )
    }

    private fun HeadlessClient.ready() = await("the login rebuild") { position }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.saw(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    /** The portal's [op] from beside it; waits to be in a hall, and returns where. */
    private fun HeadlessClient.goIn(op: Int): Coord {
        tele(PORTAL_X - 1, PORTAL_Z)
        locOp(ClanHall.PORTAL, PORTAL_X, PORTAL_Z, op)
        return await("the hall") { position?.takeIf { it.x >= 6400 } }
    }

    private fun arrival(server: TestServer, at: Coord) = server.world.instances.at(at.x, at.z)!!.let {
        Coord(it.baseX + ClanHall.ARRIVE_X - ClanHall.TEMPLATE_X, it.baseZ + ClanHall.ARRIVE_Z - ClanHall.TEMPLATE_Z, 0)
    }

    @Test
    fun `members go into their clan's one hall by Enter or Enter-member, and Leave puts them outside the portal`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.ready()
                    bob.ready()
                    alice.tele(PORTAL_X - 1, PORTAL_Z)
                    alice.locOp(ClanHall.PORTAL, PORTAL_X, PORTAL_Z, ClanHall.ENTER_MEMBER)
                    alice.saw("[clan_hall.no_clan]")

                    alice.commandReply("clanmake Odins Gate") { it.startsWith("Clan") }
                    alice.commandReply("clanjoin Odins Gate") { it.startsWith("Joined") }
                    bob.commandReply("clanjoin Odins Gate") { it.startsWith("Joined") }
                    val aliceAt = alice.goIn(ClanHall.ENTER)
                    assertEquals(arrival(server, aliceAt), aliceAt)
                    val hall = assertNotNull(server.world.instances.at(aliceAt.x, aliceAt.z))
                    val clan = server.world.clans.clanNamed("Odins Gate")!!
                    assertEquals(clan.id, server.world.clanHall.clanAt(aliceAt.x, aliceAt.z))
                    // The client draws the hall's map square.
                    val zones = alice.await("the region rebuild") { alice.regionZones() }
                    assertEquals(CopiedZone(ClanHall.TEMPLATE_X shr 3, ClanHall.TEMPLATE_Z shr 3, 0, 0), zones[Triple(hall.baseX shr 3, hall.baseZ shr 3, 0)])

                    // bob's Enter-member goes into the same one.
                    val bobAt = bob.goIn(ClanHall.ENTER_MEMBER)
                    assertSame(hall, server.world.instances.at(bobAt.x, bobAt.z))

                    alice.locOp(ClanHall.EXIT, hall.baseX + EXIT_X - ClanHall.TEMPLATE_X, hall.baseZ + EXIT_Z - ClanHall.TEMPLATE_Z, ClanHall.LEAVE)
                    alice.await("outside") { alice.position?.takeIf { it == Coord(ClanHall.OUTSIDE_X, ClanHall.OUTSIDE_Z, 0) } }
                    bob.tele(PORTAL_X - 2, PORTAL_Z)
                    // Empty, it ends.
                    alice.await("the hall ended") { if (hall in server.world.instances.all) null else Unit }
                    assertNull(server.world.clanHall.clanAt(aliceAt.x, aliceAt.z))
                }
            }
        }
    }

    @Test
    fun `Enter-guest goes in by the clan's name while its permission lets guests in, and not when banned or barred`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { carol ->
                carol.ready()
                carol.commandReply("clanmake Odins Gate") { it.startsWith("Clan") }
                carol.tele(PORTAL_X - 1, PORTAL_Z)
                carol.locOp(ClanHall.PORTAL, PORTAL_X, PORTAL_Z, ClanHall.ENTER_GUEST)
                carol.awaitTicks(2)
                carol.resumeString("Nobody Here")
                carol.saw("[clan_hall.no_such_clan name=Nobody Here]")

                // Guests may enter by default (the cache's default for Enter clan hall is Guest).
                carol.locOp(ClanHall.PORTAL, PORTAL_X, PORTAL_Z, ClanHall.ENTER_GUEST)
                carol.awaitTicks(2)
                carol.resumeString("odins gate")
                val at = carol.await("the hall") { carol.position?.takeIf { it.x >= 6400 } }
                val clan = server.world.clans.clanNamed("Odins Gate")!!
                assertEquals(clan.id, server.world.clanHall.clanAt(at.x, at.z))

                // Members only: refused. Enter, in no clan and with no guest clan, asks for the name too.
                carol.tele(PORTAL_X - 1, PORTAL_Z)
                clan.settings[Clans.VAR_ENTER_HALL] = Clans.RANK_FOUNDER
                carol.locOp(ClanHall.PORTAL, PORTAL_X, PORTAL_Z, ClanHall.ENTER)
                carol.awaitTicks(2)
                carol.resumeString("Odins Gate")
                carol.saw("[clan_hall.no_guests name=Odins Gate]")

                // Guests let in again, but she is on the ban list.
                clan.settings.remove(Clans.VAR_ENTER_HALL)
                val key = server.world.playerNamed("carol")!!.saveKey
                clan.banned += osrsps.game.social.ClanStore.Banned(key, "carol")
                carol.locOp(ClanHall.PORTAL, PORTAL_X, PORTAL_Z, ClanHall.ENTER_GUEST)
                carol.awaitTicks(2)
                carol.resumeString("Odins Gate")
                carol.awaitTicks(3)
                assertTrue(carol.messages().count { it.text == "[clan_hall.no_guests name=Odins Gate]" } >= 2)
                assertEquals(Coord(PORTAL_X - 1, PORTAL_Z, 0), carol.position)
            }
        }
    }

    @Test
    fun `a guest in the hall is put outside once the Enter clan hall permission keeps guests out`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("carol").use { carol ->
                    alice.ready()
                    carol.ready()
                    alice.commandReply("clanmake Odins Gate") { it.startsWith("Clan") }
                    alice.commandReply("clanjoin Odins Gate recruiter") { it.startsWith("Joined") }
                    val clan = server.world.clans.clanNamed("Odins Gate")!!
                    alice.goIn(ClanHall.ENTER)
                    // carol, in no clan, goes in as a guest (Guest is the default).
                    carol.tele(PORTAL_X - 1, PORTAL_Z)
                    carol.locOp(ClanHall.PORTAL, PORTAL_X, PORTAL_Z, ClanHall.ENTER_GUEST)
                    carol.awaitTicks(2)
                    carol.resumeString("Odins Gate")
                    carol.await("the hall") { carol.position?.takeIf { it.x >= 6400 } }

                    // Exploit review R55C-4: members only now, so she is put outside; alice stays.
                    alice.commandReply("pause") { it.contains("aused after tick") }
                    server.world.clans.setPermission(server.world.playerNamed("alice")!!, clan.id, Clans.VAR_ENTER_HALL, Clans.RANK_FOUNDER)
                    alice.stepTicks(2)
                    carol.await("outside") { carol.position?.takeIf { it == Coord(ClanHall.OUTSIDE_X, ClanHall.OUTSIDE_Z, 0) } }
                    carol.saw("[clan_hall.removed]")
                    assertNotNull(alice.position?.takeIf { it.x >= 6400 })
                }
            }
        }
    }

    private companion object {
        const val PORTAL_X = 3186
        const val PORTAL_Z = 3471
        const val EXIT_X = 1759
        const val EXIT_Z = 5488
    }
}
