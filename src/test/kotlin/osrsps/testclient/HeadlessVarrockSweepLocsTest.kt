package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.VarrockSweepLocs
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.shop.ShopScreen
import osrsps.game.shop.ShopTypes
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Varrock's locs from the Misthalin sweep through the headless client: the West Bank's cellar stairs down and up, the
 * cellar ladder down and up keeping the player's side where they can stand there, a fairy crate's Search, the pillory cage's Unlock, and the
 * rotten tomato crate's Buy opening the Pelters' Veg Stall from the real shops.json. Places are the 241 cache's.
 */
class HeadlessVarrockSweepLocsTest {
    private fun loc(id: Int, name: String, op: String) = LocType(id, name, listOf(op, null, null, null, null), LocCollisionType(width = 1, length = 1))

    private val objs = ObjTypes.of(ObjType(COINS, "Coins", stackable = true, tradeable = true), ObjType(TOMATO, "Rotten tomato", cost = 1))

    private val shops = ShopTypes.parse(ShopTypes::class.java.getResource(ShopTypes.RESOURCE)!!.readText(), osrsps.game.NpcTypes.of(), objs)
        .let { all -> ShopTypes(listOf(all[VarrockSweepLocs.PELTERS_STALL]!!)) }

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                markLoaded(3187 shr 6, 3433 shr 6)
                markLoaded(3187 shr 6, 9833 shr 6)
                markLoaded(3232 shr 6, 3402 shr 6)
                markLoaded(3232 shr 6, 9802 shr 6)
                // The cellar's wall north of the ladder below, where the street above is open.
                add(3232, 9803, 0, CollisionFlag.LOC)
                // A thin wall east of the ladder below: the tile beyond it can be stood on but not stepped to from the ladder.
                add(3232, 9802, 0, CollisionFlag.WALL_EAST)
                add(3233, 9802, 0, CollisionFlag.WALL_WEST)
            },
            objTypes = objs, shopTypes = shops,
            locTypes = LocTypes(
                listOf(
                    loc(VarrockSweepLocs.BANK_STAIRS_DOWN, "Staircase", "Climb-down"), loc(VarrockSweepLocs.CELLAR_STAIRS_UP, "Staircase", "Climb-up"),
                    loc(VarrockSweepLocs.LADDER_DOWN, "Ladder", "Climb-down"), loc(VarrockSweepLocs.LADDER_UP, "Ladder", "Climb-up"),
                    loc(FAIRY_CRATE, "Crate", "Search"), loc(VarrockSweepLocs.PILLORY_CAGE, "Cage", "Unlock"),
                    loc(VarrockSweepLocs.TOMATO_CRATE, "Crate", "Buy"),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(VarrockSweepLocs.BANK_STAIRS_DOWN, LocShape.CENTREPIECE_STRAIGHT, 1, 3187, 3433, 0))
                add(ZoneLoc(VarrockSweepLocs.CELLAR_STAIRS_UP, LocShape.CENTREPIECE_STRAIGHT, 3, 3187, 9833, 0))
                add(ZoneLoc(VarrockSweepLocs.LADDER_DOWN, LocShape.CENTREPIECE_STRAIGHT, 1, 3232, 3402, 0))
                add(ZoneLoc(VarrockSweepLocs.LADDER_UP, LocShape.CENTREPIECE_STRAIGHT, 1, 3232, 9802, 0))
                add(ZoneLoc(FAIRY_CRATE, LocShape.CENTREPIECE_STRAIGHT, 0, 3224, 3218, 0))
                add(ZoneLoc(VarrockSweepLocs.PILLORY_CAGE, LocShape.CENTREPIECE_STRAIGHT, 0, 3226, 3218, 0))
                add(ZoneLoc(VarrockSweepLocs.TOMATO_CRATE, LocShape.CENTREPIECE_STRAIGHT, 0, 3228, 3218, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.at(x: Int, z: Int, level: Int, what: String) =
        await(what) { position?.takeIf { it.x == x && it.z == z && it.level == level } }

    @Test
    fun `the bank's cellar stairs and the cellar ladder go down and back up`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele 3186 3433 0")
                client.at(3186, 3433, 0, "beside the bank's stairs")
                client.locOp(VarrockSweepLocs.BANK_STAIRS_DOWN, 3187, 3433, 1)
                client.at(VarrockSweepLocs.CELLAR_LANDING.x, VarrockSweepLocs.CELLAR_LANDING.z, 0, "in the bank's cellar")
                client.locOp(VarrockSweepLocs.CELLAR_STAIRS_UP, 3187, 9833, 1)
                client.at(VarrockSweepLocs.BANK_LANDING.x, VarrockSweepLocs.BANK_LANDING.z, 0, "back in the bank")

                client.command("tele 3231 3402 0")
                client.at(3231, 3402, 0, "beside the ladder")
                client.locOp(VarrockSweepLocs.LADDER_DOWN, 3232, 3402, 1)
                client.at(3231, 9802, 0, "down the ladder on the same side")
                client.locOp(VarrockSweepLocs.LADDER_UP, 3232, 9802, 1)
                client.at(3231, 3402, 0, "back up")

                // From the north, where the cellar has a wall: down on the free tile south of the ladder below instead.
                client.command("tele 3232 3403 0")
                client.at(3232, 3403, 0, "north of the ladder")
                client.locOp(VarrockSweepLocs.LADDER_DOWN, 3232, 3402, 1)
                client.at(3232, 9801, 0, "down the ladder, south of it, off the wall")
                client.locOp(VarrockSweepLocs.LADDER_UP, 3232, 9802, 1)
                client.at(3232, 3401, 0, "back up on the same side")

                // From the east, where a thin wall stands east of the ladder below: never behind that wall.
                client.command("tele 3233 3402 0")
                client.at(3233, 3402, 0, "east of the ladder")
                client.locOp(VarrockSweepLocs.LADDER_DOWN, 3232, 3402, 1)
                client.at(3232, 9801, 0, "down the ladder, south of it, not behind the wall")
            }
        }
    }

    @Test
    fun `the fairy crate, the pillory cage and the tomato crate`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.locOp(FAIRY_CRATE, 3224, 3218, 1)
                client.awaitMessage("the crate's message") { it == "[search.crate_empty]" }
                client.locOp(VarrockSweepLocs.PILLORY_CAGE, 3226, 3218, 1)
                client.awaitMessage("the cage's message") { it == "[pillory.unlock]" }
                client.locOp(VarrockSweepLocs.TOMATO_CRATE, 3228, 3218, 1)
                client.await("the stall") { if (ShopScreen.MAIN in client.openInterfaces()) Unit else null }
                val stock = client.await("the stock") { client.inventory(PELTERS_INV).firstOrNull() }
                assertEquals(TOMATO to 2000, stock.id to stock.count)
            }
        }
    }

    private companion object {
        const val COINS = 995
        const val TOMATO = 2518
        const val FAIRY_CRATE = 12105

        /** pillory_rottenfruitshop, the stall's inv in shops.json. */
        const val PELTERS_INV = 334
    }
}
