package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.npc.NpcSpawn
import osrsps.game.skill.Fishing
import osrsps.map.CollisionMap

/**
 * Gathering step 5 through the headless client: the Lumbridge Swamp's Net/Bait spots and the River Lum's Lure/Bait spot,
 * the checks before a cast, the roll timing and a spot moving on. The rl-client script
 * test-runner/examples/fishing-lumbridge.json nets shrimps in the swamp in the real client.
 */
class HeadlessFishingTest {
    private val swamp = NpcType(Fishing.SWAMP_SPOT, "Fishing spot", ops = listOf("Net", null, "Bait", "hidden", "hidden"))
    private val river = NpcType(Fishing.RIVER_SPOT, "Rod Fishing spot", ops = listOf("Lure", null, "Bait", "hidden", "hidden"))

    // Draynor's shore spots spell the net option "Small Net" in the 241 cache.
    private val draynor = NpcType(Fishing.DRAYNOR_SPOT, "Fishing spot", ops = listOf("Small Net", null, "Bait", null, null))

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 20) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(POT + 1)
        for ((id, name) in listOf(
            Fishing.NET to "Small fishing net", Fishing.FISHING_ROD to "Fishing rod", Fishing.FLY_FISHING_ROD to "Fly fishing rod",
            Fishing.FISHING_BAIT to "Fishing bait", Fishing.FEATHER to "Feather", POT to "Pot",
        )) objs[id] = ObjType(id, name)
        objs[Fishing.FISHING_BAIT] = ObjType(Fishing.FISHING_BAIT, "Fishing bait", stackable = true)
        objs[Fishing.FEATHER] = ObjType(Fishing.FEATHER, "Feather", stackable = true)
        for (fish in listOf(Fishing.SHRIMPS, Fishing.ANCHOVIES, Fishing.SARDINE, Fishing.HERRING, Fishing.TROUT, Fishing.SALMON, Fishing.PIKE)) {
            objs[fish.obj] = ObjType(fish.obj, "Fish ${fish.obj}")
        }
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                markLoaded(SWAMP_X shr 6, SWAMP_Z shr 6)
                markLoaded(RIVER_X shr 6, RIVER_Z shr 6)
                markLoaded(DRAYNOR_X shr 6, DRAYNOR_Z shr 6)
            },
            objTypes = ObjTypes(objs), npcTypes = NpcTypes.of(swamp, river, draynor), random = GameRandom(5),
            npcSpawns = listOf(NpcSpawn(swamp, SWAMP_X, SWAMP_Z, source = "test"), NpcSpawn(river, RIVER_X, RIVER_Z, source = "test"),
                NpcSpawn(draynor, DRAYNOR_X, DRAYNOR_Z, source = "test"),
            ),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.catches() = messages().filter { it.text.startsWith("[fishing.catch_") }

    private fun HeadlessClient.spot(id: Int) = await("the spot in view") { npcs().firstOrNull { it.id == id } }

    private fun HeadlessClient.goTo(x: Int, z: Int) {
        command("tele $x $z")
        await("the teleport") { position?.takeIf { it.x == x && it.z == z } }
    }

    @Test
    fun `Net needs a net, then nets shrimps for 10 XP each`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.goTo(SWAMP_X, SWAMP_Z + 1)
                val spot = client.spot(Fishing.SWAMP_SPOT)
                client.npcOp(spot.index, 1)
                client.seen("[fishing.no_net]")

                client.command("item ${Fishing.NET}")
                client.await("the net") { if (client.count(Fishing.NET) == 1) Unit else null }
                client.npcOp(spot.index, 1)
                client.seen("[fishing.cast_net]")
                client.await("a shrimp", timeoutMillis = 30_000) { if (client.count(Fishing.SHRIMPS.obj) == 1) Unit else null }
                client.seen("[fishing.catch_shrimps]")
                assertEquals(10, client.await("Fishing XP") { client.stat(FISHING)?.takeIf { it.xp > 0 } }.xp)
            }
        }
    }

    @Test
    fun `Small Net on Draynor's shore spots nets shrimps as Net does`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.goTo(DRAYNOR_X + 1, DRAYNOR_Z)
                client.command("item ${Fishing.NET}")
                client.command("setlevel fishing 99")
                client.await("level 99") { client.stat(FISHING)?.takeIf { it.level == 99 } }
                client.npcOp(client.spot(Fishing.DRAYNOR_SPOT).index, 1)
                client.seen("[fishing.cast_net]")
                client.await("a catch", timeoutMillis = 30_000) { client.catches().firstOrNull() }
            }
        }
    }

    @Test
    fun `at 99 every roll catches, the first 4 ticks after the click and then every 5`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.goTo(SWAMP_X, SWAMP_Z + 1)
                client.command("item ${Fishing.NET}")
                client.command("setlevel fishing 99")
                client.await("level 99") { client.stat(FISHING)?.takeIf { it.level == 99 } }
                client.npcOp(client.spot(Fishing.SWAMP_SPOT).index, 1)
                val cast = client.seen("[fishing.cast_net]")
                val catches = client.await("three catches") { client.catches().takeIf { it.size >= 3 } }
                assertEquals(listOf(4, 9, 14), catches.take(3).map { it.tick - cast.tick })
            }
        }
    }

    @Test
    fun `Bait needs level 5 and bait, and the last bait makes room for its fish`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.goTo(SWAMP_X, SWAMP_Z + 1)
                val spot = client.spot(Fishing.SWAMP_SPOT)
                client.npcOp(spot.index, 3)
                client.seen("[fishing.level_bait level=5]")

                client.command("setlevel fishing 99")
                client.command("item ${Fishing.FISHING_ROD}")
                client.await("the rod") { if (client.count(Fishing.FISHING_ROD) == 1) Unit else null }
                client.npcOp(spot.index, 3)
                client.seen("[fishing.no_bait]")

                // A full backpack, the last slot the one bait: the catch takes the bait's place.
                client.command("item ${Fishing.FISHING_BAIT}")
                client.command("item $POT 26")
                client.await("a full backpack") { if (client.inventory(BACKPACK).count { it != null } == 28) Unit else null }
                client.npcOp(spot.index, 3)
                client.seen("[fishing.cast_line]")
                client.await("a fish", timeoutMillis = 30_000) { client.catches().firstOrNull() }
                assertEquals(0, client.count(Fishing.FISHING_BAIT))
                assertEquals(1, client.count(Fishing.SARDINE.obj) + client.count(Fishing.HERRING.obj))
                // With no bait left the line comes in.
                client.await("no bait left") { client.messages().count { it.text == "[fishing.no_bait]" }.takeIf { it == 2 } }
            }
        }
    }

    @Test
    fun `Lure needs a fly fishing rod and feathers, and a full backpack stops a net`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.goTo(RIVER_X + 1, RIVER_Z)
                val spot = client.spot(Fishing.RIVER_SPOT)
                client.npcOp(spot.index, 1)
                client.seen("[fishing.level_lure level=20]")
                client.command("setlevel fishing 20")
                client.await("level 20") { client.stat(FISHING)?.takeIf { it.level == 20 } }
                client.npcOp(spot.index, 1)
                client.seen("[fishing.no_fly_rod]")
                client.command("item ${Fishing.FLY_FISHING_ROD}")
                client.await("the fly rod") { if (client.count(Fishing.FLY_FISHING_ROD) == 1) Unit else null }
                client.npcOp(spot.index, 1)
                client.seen("[fishing.no_feathers]")

                client.goTo(SWAMP_X, SWAMP_Z + 1)
                client.command("item ${Fishing.NET}")
                client.command("item $POT 26")
                client.await("a full backpack") { if (client.inventory(BACKPACK).count { it != null } == 28) Unit else null }
                client.npcOp(client.spot(Fishing.SWAMP_SPOT).index, 1)
                client.seen("[fishing.inventory_full]")
            }
        }
    }

    @Test
    fun `the river spot moves on within 530 ticks and the player stops fishing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.goTo(RIVER_X + 1, RIVER_Z)
                client.command("setlevel fishing 99")
                client.command("item ${Fishing.FLY_FISHING_ROD}")
                client.command("item ${Fishing.FEATHER} 1000")
                client.await("the feathers") { if (client.count(Fishing.FEATHER) == 1000) Unit else null }
                val spot = client.spot(Fishing.RIVER_SPOT)
                client.npcOp(spot.index, 1)
                client.await("a catch", timeoutMillis = 30_000) { client.catches().firstOrNull() }
                val moved = client.await("the spot moved", timeoutMillis = 30_000) {
                    client.npcs().firstOrNull { it.index == spot.index && (it.coord.x != RIVER_X || it.coord.z != RIVER_Z) }
                }
                assertTrue(Fishing.AREAS[0].any { it.x == moved.coord.x && it.z == moved.coord.z }, "moved to ${moved.coord}")
                val movedTick = client.ticksSeen
                val caught = client.catches().size
                client.await("12 ticks on") { if (client.ticksSeen >= movedTick + 12) Unit else null }
                // A roll may land on the tick the spot moves; none after.
                assertTrue(client.catches().size <= caught + 1, "still catching after the spot moved")
                assertEquals(client.catches().size, client.catches().count { it.tick <= movedTick + 1 })
            }
        }
    }

    private companion object {
        /** A swamp spawn tile and a river one, as the Lumbridge spawn file has them. */
        const val SWAMP_X = 3240
        const val SWAMP_Z = 3146
        const val RIVER_X = 3239
        const val RIVER_Z = 3241
        const val DRAYNOR_X = 3086
        const val DRAYNOR_Z = 3227
        const val POT = 1931
        const val BACKPACK = 93
        const val FISHING = 10
    }
}
