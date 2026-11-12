package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.MisthalinTownGaps
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * The small town lists through the headless client: a Wizards' Tower crate, a Wise Old Man bookshelf and the Lumbridge
 * bookcase search empty, a manor cabbage picks the manor's cabbage, and the crypt stairs go down and back up. The real
 * places are the 241 cache's; here the searchable ones stand by the spawn.
 */
class HeadlessMisthalinTownGapsTest {
    private fun loc(id: Int, name: String, op: String, width: Int = 1, length: Int = 1) =
        LocType(id, name, listOf(op, null, null, null, null), LocCollisionType(width = width, length = length))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                markLoaded(3115 shr 6, 3357 shr 6)
                markLoaded(3077 shr 6, 9768 shr 6)
            },
            objTypes = ObjTypes.of(ObjType(MisthalinTownGaps.MANOR_CABBAGE, "Cabbage")),
            locTypes = LocTypes(
                listOf(
                    loc(CRATE, "Crate", "Search"), loc(SHELF, "Old Bookshelf", "Search"), loc(MisthalinTownGaps.LUMBRIDGE_BOOKCASE, "Bookcase", "Search"),
                    loc(MisthalinTownGaps.MAGIC_CABBAGE_LOC, "Cabbage", "Pick"),
                    loc(MisthalinTownGaps.CRYPT_STAIRS_DOWN, "Stairs", "Climb-down", 2, 3), loc(MisthalinTownGaps.CRYPT_STAIRS_UP, "Stairs", "Climb-up", 2, 3),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(CRATE, LocShape.CENTREPIECE_STRAIGHT, 0, 3224, 3218, 0))
                add(ZoneLoc(SHELF, LocShape.CENTREPIECE_STRAIGHT, 0, 3226, 3218, 0))
                add(ZoneLoc(MisthalinTownGaps.LUMBRIDGE_BOOKCASE, LocShape.CENTREPIECE_STRAIGHT, 0, 3228, 3218, 0))
                add(ZoneLoc(MisthalinTownGaps.MAGIC_CABBAGE_LOC, LocShape.CENTREPIECE_STRAIGHT, 0, 3222, 3220, 0))
                add(ZoneLoc(MisthalinTownGaps.CRYPT_STAIRS_DOWN, LocShape.CENTREPIECE_STRAIGHT, 2, 3115, 3357, 0))
                add(ZoneLoc(MisthalinTownGaps.CRYPT_STAIRS_UP, LocShape.CENTREPIECE_STRAIGHT, 2, 3077, 9768, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.at(x: Int, z: Int, what: String) = await(what) { position?.takeIf { it.x == x && it.z == z && it.level == 0 } }

    private fun HeadlessClient.searchSays(id: Int, x: Int, z: Int, key: String) {
        val seen = messages().count { it.text == key }
        locOp(id, x, z, 1)
        await("$key from $id") { if (messages().count { it.text == key } > seen) Unit else null }
    }

    @Test
    fun `the searches, the manor cabbage and the crypt stairs`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.searchSays(CRATE, 3224, 3218, "[search.crate_empty]")
                client.searchSays(SHELF, 3226, 3218, "[draynor_search.bookcase_empty]")
                client.searchSays(MisthalinTownGaps.LUMBRIDGE_BOOKCASE, 3228, 3218, "[varrock_search.bookcase_empty]")
                client.locOp(MisthalinTownGaps.MAGIC_CABBAGE_LOC, 3222, 3220, 1)
                client.await("the manor cabbage") { client.inventory(Inventory.INV).firstOrNull { it?.id == MisthalinTownGaps.MANOR_CABBAGE } }

                client.command("tele 3115 3360 0")
                client.at(3115, 3360, "by the crypt stairs")
                client.locOp(MisthalinTownGaps.CRYPT_STAIRS_DOWN, 3115, 3357, 1)
                client.at(MisthalinTownGaps.CRYPT_LANDING.x, MisthalinTownGaps.CRYPT_LANDING.z, "in the crypt")
                client.locOp(MisthalinTownGaps.CRYPT_STAIRS_UP, 3077, 9768, 1)
                client.at(MisthalinTownGaps.GRAVEYARD_LANDING.x, MisthalinTownGaps.GRAVEYARD_LANDING.z, "back up")
            }
        }
    }

    private companion object {
        const val CRATE = 12548
        const val SHELF = 9146
    }
}
