package osrsps.testclient

import org.junit.jupiter.api.io.TempDir
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.ClanHall
import osrsps.game.content.ClanParty
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The clan hall party chest through the headless client: a member opens the chest, offers items from the side panel
 * (untradeables refused), takes one back, confirms the rest into the chest (sent as the chest's other copy), and the
 * lever drops balloons for 1,000 coins; bursting them all drops what the chest held. Closing the window gives back an
 * offer not confirmed. The rl-client script test-runner/examples/clan-party.json does the same on the real map.
 */
class HeadlessClanPartyTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(ClanHall.TEMPLATE_X shr 6, ClanHall.TEMPLATE_Z shr 6)
                markLoaded(PORTAL_X shr 6, PORTAL_Z shr 6)
            },
            objTypes = ObjTypes.of(
                ObjType(COINS, "Coins", stackable = true, tradeable = true),
                ObjType(DAGGER, "Bronze dagger", tradeable = true),
                ObjType(CAPE, "Untradeable cape"),
            ),
            locTypes = LocTypes(
                (
                    listOf(
                        LocType(ClanHall.PORTAL, "Clan hall portal", ops = listOf("Enter", "Enter-member", "Enter-guest", null, null), collision = LocCollisionType(width = 1, length = 1)),
                        LocType(ClanParty.CHEST_CLOSED, "Chest", ops = listOf("Open", null, null, "Check-permissions", null), collision = LocCollisionType(width = 1, length = 1)),
                        LocType(ClanParty.CHEST_OPEN, "Chest", ops = listOf(null, "Deposit", "Shut", "Check-permissions", null), collision = LocCollisionType(width = 1, length = 1)),
                        LocType(ClanParty.LEVER, "Lever", ops = listOf("Pull", null, null, null, null), collision = LocCollisionType(width = 1, length = 1)),
                    ) +
                        (ClanParty.BALLOONS + ClanParty.POPS).map {
                            LocType(it, "Party Balloon", ops = listOf(if (it in ClanParty.BALLOONS) "Burst" else null, null, null, null, null), collision = LocCollisionType(width = 1, length = 1, interactType = 0))
                        }
                    ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(ClanHall.PORTAL, LocShape.CENTREPIECE_STRAIGHT, 0, PORTAL_X, PORTAL_Z, 0))
                add(ZoneLoc(ClanParty.CHEST_CLOSED, LocShape.CENTREPIECE_STRAIGHT, 0, CHEST_X, CHEST_Z, 0))
                add(ZoneLoc(ClanParty.LEVER, LocShape.CENTREPIECE_STRAIGHT, 0, LEVER_X, LEVER_Z, 0))
            },
            cycleMillis = millis,
            clansDir = dir.resolve("data"),
        )
    }

    private fun HeadlessClient.held(id: Int): Int = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.slotOf(id: Int): Int = inventory(BACKPACK).indexOfFirst { it?.id == id }

    @Test
    fun `members offer, confirm into the chest, and the lever's balloons drop it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                alice.commandReply("clanmake Odins Gate") { it.startsWith("Clan") }
                alice.commandReply("clanjoin Odins Gate") { it.startsWith("Joined") }
                alice.commandReply("item $COINS 1500") { true }
                alice.commandReply("item $DAGGER 3") { true }
                alice.commandReply("item $CAPE 1") { true }
                alice.await("the items") { alice.held(DAGGER).takeIf { it == 3 && alice.held(CAPE) == 1 } }

                alice.command("tele ${PORTAL_X - 1} $PORTAL_Z 0")
                alice.await("beside the portal") { alice.position?.takeIf { it == Coord(PORTAL_X - 1, PORTAL_Z, 0) } }
                alice.locOp(ClanHall.PORTAL, PORTAL_X, PORTAL_Z, ClanHall.ENTER)
                val at = alice.await("the hall") { alice.position?.takeIf { it.x >= 6400 } }
                val hall = server.world.instances.at(at.x, at.z)!!
                fun x(template: Int) = hall.baseX + template - ClanHall.TEMPLATE_X
                fun z(template: Int) = hall.baseZ + template - ClanHall.TEMPLATE_Z

                // Open the chest, then Deposit.
                alice.command("tele ${x(CHEST_X) - 1} ${z(CHEST_Z)} 0")
                alice.await("beside the chest") { alice.position?.takeIf { it == Coord(x(CHEST_X) - 1, z(CHEST_Z), 0) } }
                alice.locOp(ClanParty.CHEST_CLOSED, x(CHEST_X), z(CHEST_Z), ClanParty.OPEN)
                alice.await("the chest open") { alice.locChanges().firstOrNull { it.id == ClanParty.CHEST_OPEN } }
                alice.locOp(ClanParty.CHEST_OPEN, x(CHEST_X), z(CHEST_Z), ClanParty.DEPOSIT)
                alice.await("the window") { if (ClanParty.MAIN in alice.openInterfaces() && ClanParty.SIDE in alice.openInterfaces()) Unit else null }

                // Offer: the cape is refused; two daggers go in (op 1 is the picked 1, twice), and one comes back (op 2: 1).
                alice.ifButton(ClanParty.SIDE, ClanParty.SIDE_ITEMS, 1, alice.slotOf(CAPE), CAPE)
                alice.await("refused") { alice.messages().firstOrNull { it.text == "[clan_party.not_tradeable]" } }
                alice.ifButton(ClanParty.SIDE, ClanParty.SIDE_ITEMS, 1, alice.slotOf(DAGGER), DAGGER)
                alice.await("one offered") { alice.inventory(ClanParty.OFFER_INV).count { it?.id == DAGGER }.takeIf { it == 1 } }
                alice.ifButton(ClanParty.SIDE, ClanParty.SIDE_ITEMS, 1, alice.slotOf(DAGGER), DAGGER)
                alice.await("two offered") { alice.inventory(ClanParty.OFFER_INV).count { it?.id == DAGGER }.takeIf { it == 2 } }
                alice.ifButton(ClanParty.MAIN, ClanParty.OFFER_ITEMS, 2, 0, DAGGER)
                alice.await("one back") { alice.inventory(ClanParty.OFFER_INV).count { it?.id == DAGGER }.takeIf { it == 1 } }
                assertEquals(2, alice.held(DAGGER))

                // Confirm: into the chest, sent as its other copy.
                alice.ifButton(ClanParty.MAIN, ClanParty.CONFIRM, 1)
                alice.await("in the chest") {
                    alice.inventory(ClanParty.CHEST_INV + HeadlessClient.OTHER_INV).count { it?.id == DAGGER }.takeIf { it == 1 }
                }
                assertTrue(alice.inventory(ClanParty.OFFER_INV).all { it == null })

                // An offer not confirmed comes back when the window closes.
                alice.ifButton(ClanParty.SIDE, ClanParty.SIDE_ITEMS, 1, alice.slotOf(DAGGER), DAGGER)
                alice.await("offered again") { alice.inventory(ClanParty.OFFER_INV).count { it?.id == DAGGER }.takeIf { it == 1 } }
                alice.closeModal()
                alice.await("given back") { alice.held(DAGGER).takeIf { it == 2 } }

                // Pull: 1,000 coins, and balloons fall.
                alice.command("tele ${x(LEVER_X) - 1} ${z(LEVER_Z)} 0")
                alice.await("beside the lever") { alice.position?.takeIf { it == Coord(x(LEVER_X) - 1, z(LEVER_Z), 0) } }
                alice.locOp(ClanParty.LEVER, x(LEVER_X), z(LEVER_Z), ClanParty.PULL)
                alice.await("paid") { alice.held(COINS).takeIf { it == 500 } }
                val balloons = alice.await("balloons") { alice.locChanges().filter { it.id in ClanParty.BALLOONS }.takeIf { it.size == ClanParty.balloonCount(1) } }
                alice.locOp(ClanParty.LEVER, x(LEVER_X), z(LEVER_Z), ClanParty.PULL)
                alice.await("one drop at a time") { alice.messages().firstOrNull { it.text == "[clan_party.drop_on]" } }

                // Burst them all: the last one up at the latest drops the dagger.
                for (balloon in balloons) {
                    if (alice.groundObjs().any { it.id == DAGGER }) break
                    alice.command("tele ${balloon.coord.x} ${balloon.coord.z - 1} 0")
                    alice.await("beside a balloon") { alice.position?.takeIf { it == Coord(balloon.coord.x, balloon.coord.z - 1, 0) } }
                    alice.locOp(balloon.id, balloon.coord.x, balloon.coord.z, ClanParty.BURST)
                    alice.await("burst") { alice.locChanges().firstOrNull { it.coord == balloon.coord && it.id != balloon.id } }
                }
                alice.await("the dagger dropped") { alice.groundObjs().firstOrNull { it.id == DAGGER } }
                assertEquals(0, server.world.clanParty.chestCount(hall))
            }
        }
    }

    private companion object {
        const val PORTAL_X = 3186
        const val PORTAL_Z = 3471
        const val CHEST_X = 1763
        const val CHEST_Z = 5462
        const val LEVER_X = 1764
        const val LEVER_Z = 5463
        const val COINS = 995
        const val DAGGER = 1205
        const val CAPE = 9999
        const val BACKPACK = 93
    }
}
