package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
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
 * Edgeville piece step 8 through the headless client: the River Barbarian's Lure/Bait spot (1526) lures a fish and
 * moves on between its area's five tiles. The rl-client script test-runner/examples/barbarian-mine-fish.json does the
 * same in the real client.
 */
class HeadlessBarbarianFishingTest {
    private val spotType = NpcType(Fishing.BARBARIAN_SPOT, "Rod Fishing spot", ops = listOf("Lure", null, "Bait", null, null))

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 20) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(maxOf(Fishing.FEATHER, Fishing.TROUT.obj, Fishing.SALMON.obj, Fishing.PIKE.obj) + 1)
        objs[Fishing.FLY_FISHING_ROD] = ObjType(Fishing.FLY_FISHING_ROD, "Fly fishing rod")
        objs[Fishing.FEATHER] = ObjType(Fishing.FEATHER, "Feather", stackable = true)
        for (fish in listOf(Fishing.TROUT, Fishing.SALMON, Fishing.PIKE)) objs[fish.obj] = ObjType(fish.obj, "Fish ${fish.obj}")
        World(
            crcs,
            CollisionMap().apply { markLoaded(SPOT_X shr 6, SPOT_Z shr 6) },
            objTypes = ObjTypes(objs), npcTypes = NpcTypes.of(spotType), random = GameRandom(5),
            npcSpawns = listOf(NpcSpawn(spotType, SPOT_X, SPOT_Z, source = "test")),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    @Test
    fun `the River Barbarian's spot lures a fish and moves on within its area`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele ${SPOT_X - 1} $SPOT_Z")
                client.await("the teleport") { client.position?.takeIf { it.x == SPOT_X - 1 && it.z == SPOT_Z } }
                client.command("setlevel fishing 99")
                client.command("item ${Fishing.FLY_FISHING_ROD}")
                client.command("item ${Fishing.FEATHER} 1000")
                client.await("the feathers") { if (client.count(Fishing.FEATHER) == 1000) Unit else null }
                val spot = client.await("the spot in view") { client.npcs().firstOrNull { it.id == Fishing.BARBARIAN_SPOT } }
                client.npcOp(spot.index, 1)
                client.await("a catch", timeoutMillis = 30_000) { client.messages().firstOrNull { it.text.startsWith("[fishing.catch_") } }
                val area = Fishing.AREAS.single { tiles -> tiles.any { it.x == SPOT_X && it.z == SPOT_Z } }
                val moved = client.await("the spot moved", timeoutMillis = 30_000) {
                    client.npcs().firstOrNull { it.index == spot.index && (it.coord.x != SPOT_X || it.coord.z != SPOT_Z) }
                }
                assertTrue(area.any { it.x == moved.coord.x && it.z == moved.coord.z }, "moved to ${moved.coord}")
            }
        }
    }

    private companion object {
        /** The north spawn, as the Barbarian Village spawn file has it. */
        const val SPOT_X = 3110
        const val SPOT_Z = 3434
        const val BACKPACK = 93
    }
}
