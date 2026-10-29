package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.FredsFarm
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Rest of Lumbridge step 6 through the headless client: shearing a sheep, which then shows as its sheared type until
 * its wool grows back, and picking an onion, which grows back too. The headless client doesn't read the NPC info's
 * transformation block, so the sheep's type is read from the world. The rl-client script
 * test-runner/examples/lumbridge-freds-farm.json does the same in the real client, and kills a chicken.
 */
class HeadlessFredsFarmTest {
    private val sheep = NpcType(SHEEP, "Sheep", ops = listOf("Shear", null, null, null, null), internalName = "sheepunsheered")
    private val sheared = NpcType(SHEARED, "Sheep", ops = listOf(null, null, null, null, null), internalName = "sheepsheered")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(FredsFarm.ONION + 1)
        objs[FredsFarm.SHEARS] = ObjType(FredsFarm.SHEARS, "Shears", iops = listOf(null, null, null, null, "Drop"), internalName = "shears")
        objs[FredsFarm.WOOL] = ObjType(FredsFarm.WOOL, "Wool", iops = listOf(null, null, null, null, "Drop"), internalName = "wool")
        objs[FredsFarm.ONION] = ObjType(FredsFarm.ONION, "Onion", iops = listOf("Eat", null, null, null, "Drop"), internalName = "onion")
        val onion = LocType(FredsFarm.ONION_LOC, "Onion", ops = listOf(null, "Pick", null, null, null), collision = LocCollisionType(interactType = 0))
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), npcTypes = NpcTypes.of(sheep, sheared),
            npcSpawns = listOf(NpcSpawn(sheep, 3224, 3218, source = "test")),
            locTypes = LocTypes(mapOf(FredsFarm.ONION_LOC to onion)),
            staticLocs = StaticLocs().apply { add(ZoneLoc(FredsFarm.ONION_LOC, LocShape.CENTREPIECE_STRAIGHT, 0, 3220, 3220, 0)) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    @Test
    fun `Shear needs shears, gives a wool, and the sheep shows sheared until its wool grows back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                val npc = client.await("the sheep in view") { client.npcs().firstOrNull { it.id == SHEEP } }
                client.await("the login rebuild") { client.position }
                fun shownAs() = server.world.npcs[npc.index]!!.type.id

                client.npcOp(npc.index, FredsFarm.SHEAR)
                client.seen(FredsFarm.NO_SHEARS)
                assertEquals(SHEEP, shownAs())

                client.command("item ${FredsFarm.SHEARS}")
                client.await("the shears") { if (client.count(FredsFarm.SHEARS) == 1) Unit else null }
                client.npcOp(npc.index, FredsFarm.SHEAR)
                client.seen(FredsFarm.GOT_WOOL)
                client.await("a wool") { if (client.count(FredsFarm.WOOL) == 1) Unit else null }
                val shornAt = client.ticksSeen
                assertEquals(SHEARED, shownAs())

                // The shears used on it now: it has been shorn, and no more wool comes.
                val slot = client.inventory(BACKPACK).indexOfFirst { it?.id == FredsFarm.SHEARS }
                client.npcTarget(npc.index, InventoryTab.INTERFACE, InventoryTab.ITEMS, slot, FredsFarm.SHEARS)
                client.seen(FredsFarm.ALREADY_SHORN)
                assertEquals(1, client.count(FredsFarm.WOOL))

                val grown = client.await("the wool grown back", timeoutMillis = 30_000) { if (shownAs() == SHEEP) client.ticksSeen else null }
                assertTrue(grown - shornAt in FredsFarm.REGROW_TICKS - 2..FredsFarm.REGROW_TICKS + 2, "${grown - shornAt} ticks to regrow")
            }
        }
    }

    @Test
    fun `Pick takes the onion, which grows back after 50 ticks`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.locOp(FredsFarm.ONION_LOC, 3220, 3220, FredsFarm.PICK)
                client.seen(FredsFarm.PICKED_ONION)
                client.await("an onion") { if (client.count(FredsFarm.ONION) == 1) Unit else null }
                val picked = client.ticksSeen
                assertTrue(server.world.locs.find(FredsFarm.ONION_LOC, 3220, 3220, 0) == null, "the onion is still there")

                val back = client.await("the onion grown back", timeoutMillis = 30_000) {
                    if (server.world.locs.find(FredsFarm.ONION_LOC, 3220, 3220, 0) != null) client.ticksSeen else null
                }
                assertTrue(back - picked in FredsFarm.ONION_RESPAWN_TICKS - 2..FredsFarm.ONION_RESPAWN_TICKS + 2, "${back - picked} ticks to grow back")
            }
        }
    }

    private companion object {
        const val SHEEP = 2693
        const val SHEARED = 1178
        const val BACKPACK = 93
    }
}
