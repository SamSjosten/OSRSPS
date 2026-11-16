package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.VolcanicForge
import osrsps.game.content.VolcanicForgeLines
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Wilderness step W4.4b through the headless client: a malediction shard used on the Volcanic Forge with all three in
 * the backpack forging the malediction ward, and two odium shards alone left as they are. The rl-client script
 * test-runner/examples/volcanic-forge.json does the same on the real map.
 */
class HeadlessVolcanicForgeTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(VolcanicForge.MALEDICTION.shards.max() + 1)
        objs[VolcanicForge.MALEDICTION.ward] = ObjType(VolcanicForge.MALEDICTION.ward, "Malediction ward")
        objs[VolcanicForge.ODIUM.ward] = ObjType(VolcanicForge.ODIUM.ward, "Odium ward")
        for (ward in VolcanicForge.WARDS) for (shard in ward.shards) objs[shard] = ObjType(shard, "Shard $shard")
        val forge = LocType(VolcanicForge.FORGE, "Volcanic Forge", collision = LocCollisionType(width = 1, length = 1))
        World(
            crcs, CollisionMap().apply { markLoaded(X shr 6, Z shr 6) }, random = GameRandom(5), objTypes = ObjTypes(objs),
            locTypes = LocTypes(mapOf(VolcanicForge.FORGE to forge)),
            staticLocs = StaticLocs().apply { add(ZoneLoc(VolcanicForge.FORGE, LocShape.CENTREPIECE_STRAIGHT, 2, X, Z, 0)) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).count { it?.id == id }

    private fun HeadlessClient.useOnForge(id: Int) {
        val slot = inventory(BACKPACK).indexOfFirst { it?.id == id }
        locTarget(VolcanicForge.FORGE, X, Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slot, id)
    }

    @Test
    fun `three malediction shards forge the ward, and two odium shards alone do nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dax").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.command("tele $X ${Z - 1} 0")
                client.await("beside the forge") { client.position?.takeIf { it == Coord(X, Z - 1, 0) } }
                val odium = VolcanicForge.ODIUM.shards
                for (id in VolcanicForge.MALEDICTION.shards + odium.take(2)) client.command("item $id")
                client.await("the shards") { Unit.takeIf { client.count(odium[1]) == 1 } }

                client.useOnForge(odium[0])
                client.awaitMessage("nothing interesting") { it == "[game.nothing_interesting]" }
                assertEquals(1, client.count(odium[0]), "the odium shards kept")

                client.useOnForge(VolcanicForge.MALEDICTION.shards[1])
                client.awaitMessage("the forging") { it == "[${VolcanicForgeLines.FORGED.key}]" }
                client.await("the ward") { Unit.takeIf { client.count(VolcanicForge.MALEDICTION.ward) == 1 } }
                for (shard in VolcanicForge.MALEDICTION.shards) assertEquals(0, client.count(shard), "shard $shard used")
                assertEquals(2, odium.take(2).sumOf { client.count(it) }, "the odium shards still there")
            }
        }
    }

    private companion object {
        const val X = 3366
        const val Z = 3937
        const val BACKPACK = 93
    }
}
