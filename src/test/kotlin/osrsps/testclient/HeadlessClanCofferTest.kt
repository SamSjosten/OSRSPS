package osrsps.testclient

import org.junit.jupiter.api.io.TempDir
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.ClanCoffer
import osrsps.game.content.ClanHall
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.social.ClanStore
import osrsps.game.social.Clans
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The clan coffer in the clan hall through the headless client: a member deposits coins from the side panel, the clan's
 * clients get the new sum (varclan_3), withdrawing needs the Withdraw from coffer rank, and a withdrawal tells the
 * clan. The rl-client script test-runner/examples/clan-coffer.json does the same on the real map.
 */
class HeadlessClanCofferTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(ClanHall.TEMPLATE_X shr 6, ClanHall.TEMPLATE_Z shr 6)
                markLoaded(PORTAL_X shr 6, PORTAL_Z shr 6)
            },
            objTypes = ObjTypes.of(ObjType(COINS, "Coins", stackable = true)),
            locTypes = LocTypes(
                listOf(
                    LocType(ClanHall.PORTAL, "Clan hall portal", ops = listOf("Enter", "Enter-member", "Enter-guest", null, null), collision = LocCollisionType(width = 1, length = 1)),
                    LocType(ClanCoffer.COFFER, "Clan Coffer", ops = listOf("Use", null, null, null, null), collision = LocCollisionType(width = 1, length = 1)),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(ClanHall.PORTAL, LocShape.CENTREPIECE_STRAIGHT, 0, PORTAL_X, PORTAL_Z, 0))
                add(ZoneLoc(ClanCoffer.COFFER, LocShape.CENTREPIECE_STRAIGHT, 0, COFFER_X, COFFER_Z, 1))
            },
            cycleMillis = millis,
            clansDir = dir.resolve("data"),
        )
    }

    private fun HeadlessClient.coins(): Int = inventory(BACKPACK).filterNotNull().filter { it.id == COINS }.sumOf { it.count }

    @Test
    fun `members deposit into the coffer, and those the rank allows withdraw, telling the clan`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                server.login("bob").use { bob ->
                    alice.await("the login rebuild") { alice.position }
                    bob.await("the login rebuild") { bob.position }
                    alice.commandReply("clanmake Odins Gate") { it.startsWith("Clan") }
                    alice.commandReply("clanjoin Odins Gate") { it.startsWith("Joined") }
                    bob.commandReply("clanjoin Odins Gate") { it.startsWith("Joined") }
                    assertEquals(0, alice.await("the coffer's sum") { alice.varClan(Clans.VARCLAN_COFFER) })
                    alice.commandReply("item $COINS 5000") { true }
                    alice.await("the coins") { alice.coins().takeIf { it == 5000 } }

                    alice.command("tele ${PORTAL_X - 1} $PORTAL_Z 0")
                    alice.await("beside the portal") { alice.position?.takeIf { it == Coord(PORTAL_X - 1, PORTAL_Z, 0) } }
                    alice.locOp(ClanHall.PORTAL, PORTAL_X, PORTAL_Z, ClanHall.ENTER)
                    val at = alice.await("the hall") { alice.position?.takeIf { it.x >= 6400 } }
                    val hall = server.world.instances.at(at.x, at.z)!!
                    val cofferX = hall.baseX + COFFER_X - ClanHall.TEMPLATE_X
                    val cofferZ = hall.baseZ + COFFER_Z - ClanHall.TEMPLATE_Z
                    alice.command("tele ${cofferX - 1} $cofferZ 1")
                    alice.await("beside the coffer") { alice.position?.takeIf { it == Coord(cofferX - 1, cofferZ, 1) } }
                    alice.locOp(ClanCoffer.COFFER, cofferX, cofferZ, ClanCoffer.USE)
                    alice.await("the coffer") { if (ClanCoffer.MAIN in alice.openInterfaces() && ClanCoffer.SIDE in alice.openInterfaces()) Unit else null }

                    // Deposit 1k, then X: 250.
                    alice.ifButton(ClanCoffer.SIDE, 6, 1)
                    alice.await("1k in") { alice.varClan(Clans.VARCLAN_COFFER)?.takeIf { it == 1000 } }
                    bob.await("bob sees it") { bob.varClan(Clans.VARCLAN_COFFER)?.takeIf { it == 1000 } }
                    alice.ifButton(ClanCoffer.SIDE, 9, 1)
                    alice.awaitTicks(2)
                    alice.resumeCount(250)
                    alice.await("250 more") { alice.varClan(Clans.VARCLAN_COFFER)?.takeIf { it == 1250 } }
                    assertEquals(3750, alice.await("the coins left") { alice.coins().takeIf { it == 3750 } })

                    // A founder may not withdraw by default; let founders, then withdraw all.
                    alice.ifButton(ClanCoffer.MAIN, 12, 1)
                    alice.await("refused") { alice.messages().firstOrNull { it.text == "[clan_coffer.no_withdraw]" } }
                    server.world.clans.clanNamed("Odins Gate")!!.settings[Clans.VAR_COFFER_WITHDRAW] = Clans.RANK_FOUNDER
                    alice.ifButton(ClanCoffer.MAIN, 12, 1)
                    alice.await("all out") { alice.varClan(Clans.VARCLAN_COFFER)?.takeIf { it == 0 } }
                    alice.await("the coins back") { alice.coins().takeIf { it == 5000 } }
                    bob.await("told") { bob.messages().firstOrNull { it.text == "[clan.coffer_withdrawn name=alice amount=1250]" } }

                    // Withdraw-X with the permission taken away while the amount is asked: refused, the coffer untouched.
                    alice.awaitTicks(ClanCoffer.COOLDOWN_TICKS.toInt())
                    alice.ifButton(ClanCoffer.SIDE, 6, 1)
                    alice.await("1k in again") { alice.varClan(Clans.VARCLAN_COFFER)?.takeIf { it == 1000 } }
                    alice.awaitTicks(ClanCoffer.COOLDOWN_TICKS.toInt())
                    val refusals = alice.messages().count { it.text == "[clan_coffer.no_withdraw]" }
                    alice.ifButton(ClanCoffer.MAIN, 11, 1)
                    alice.awaitTicks(2)
                    server.world.clans.clanNamed("Odins Gate")!!.settings[Clans.VAR_COFFER_WITHDRAW] = Clans.RANK_OWNER
                    alice.resumeCount(500)
                    alice.await("refused after the amount") { alice.messages().count { it.text == "[clan_coffer.no_withdraw]" }.takeIf { it > refusals } }
                    assertEquals(1000, server.world.clans.clanNamed("Odins Gate")!!.coffer)
                    assertEquals(4000, alice.coins())

                    // The clan file can't be written (a folder where its temp file goes): a withdrawal gives nothing, and a
                    // deposit's coins come back, the coffer untouched both ways (exploit review R58C-1, R58C-2).
                    val clan = server.world.clans.clanNamed("Odins Gate")!!
                    clan.settings[Clans.VAR_COFFER_WITHDRAW] = Clans.RANK_FOUNDER
                    val blocker = dir.resolve("data").resolve("${ClanStore.FILE_NAME}.tmp")
                    Files.createDirectories(blocker)
                    Files.writeString(blocker.resolve("in-the-way"), "x")
                    alice.awaitTicks(ClanCoffer.COOLDOWN_TICKS.toInt())
                    alice.ifButton(ClanCoffer.MAIN, 8, 1)
                    alice.awaitTicks(ClanCoffer.COOLDOWN_TICKS.toInt() + 1)
                    alice.ifButton(ClanCoffer.SIDE, 6, 1)
                    alice.awaitTicks(ClanCoffer.COOLDOWN_TICKS.toInt() + 1)
                    assertEquals(1000, clan.coffer)
                    assertEquals(4000, alice.coins())
                    assertEquals(1000, alice.varClan(Clans.VARCLAN_COFFER))
                    blocker.toFile().deleteRecursively()
                    alice.ifButton(ClanCoffer.MAIN, 8, 1)
                    alice.await("written again") { alice.varClan(Clans.VARCLAN_COFFER)?.takeIf { it == 0 } }
                    assertEquals(0, ClanStore(dir.resolve("data").resolve(ClanStore.FILE_NAME)).byId(clan.id)!!.coffer)
                }
            }
        }
    }

    private companion object {
        const val PORTAL_X = 3186
        const val PORTAL_Z = 3471
        const val COFFER_X = 1748
        const val COFFER_Z = 5472
        const val COINS = 995
        const val BACKPACK = 93
    }
}
