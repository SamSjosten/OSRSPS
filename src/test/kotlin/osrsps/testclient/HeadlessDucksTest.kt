package osrsps.testclient

import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.World
import osrsps.game.content.Ducks
import osrsps.game.npc.MoveRestrict
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionFlag
import osrsps.map.CollisionMap

/**
 * Rest of Lumbridge step 7b through the headless client: the world's own duck scripts quack over a drake's head, a
 * ducklings group follows a duck and answers her call, and a walking duck runs from the player. With no dialogue file
 * what they say shows its key. Unit details (the death cries, keeping to water) are in DucksTest.
 */
class HeadlessDucksTest {
    private val drake = NpcType(Ducks.DRAKE, "Duck", internalName = "duck")
    private val female = NpcType(Ducks.FEMALE_DUCK, "Duck", internalName = "duck_female")
    private val ducklings = NpcType(Ducks.DUCKLINGS_GROUP, "Ducklings", internalName = "duck_update_ducklings")
    private val landDuck = NpcType(LAND_DUCK, "Duck", internalName = "duck_update_on_land_duck")

    /** A pond east of the spawn over x 3226 to 3236, z 3212 to 3224, a drake, a duck and her ducklings on it, and a duck walking west of the spawn. */
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply {
            markLoaded(3222 shr 6, 3218 shr 6)
            for (x in 3226..3236) for (z in 3212..3224) add(x, z, 0, CollisionFlag.FLOOR)
        }
        fun swimmer(type: NpcType, x: Int, z: Int) = NpcSpawn(type, x, z, restrict = MoveRestrict.BLOCKED, source = "test")
        World(
            crcs, map, npcTypes = NpcTypes.of(drake, female, ducklings, landDuck), cycleMillis = millis,
            npcSpawns = listOf(
                swimmer(drake, 3234, 3214), swimmer(female, 3230, 3218), swimmer(ducklings, 3232, 3220),
                NpcSpawn(landDuck, 3214, 3218, source = "test"),
            ),
        )
    }

    private fun HeadlessClient.npc(id: Int) = await("NPC $id in view") { npcs().firstOrNull { it.id == id } }

    private fun HeadlessClient.saw(index: Int, text: String, timeoutMillis: Long = 20_000) =
        await("NPC #$index saying $text", timeoutMillis) { npcSaid().firstOrNull { it.index == index && it.text == text } }

    @Test
    fun `a drake quacks, the ducklings follow the duck and answer her call, and a walking duck runs off`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                val drakeView = client.npc(drake.id)
                val duck = client.npc(female.id)
                val group = client.npc(ducklings.id)
                val walker = client.npc(LAND_DUCK)

                // 25 ticks after it appears, then the queued quack a tick later.
                client.saw(drakeView.index, "[duck.quack]")
                // The ducklings, 2 tiles from her, find her within 11 ticks and come to her side.
                client.await("the ducklings beside her") {
                    val (d, g) = client.npcs().let { views -> views.first { it.index == duck.index } to views.first { it.index == group.index } }
                    if (maxOf(abs(d.coord.x - g.coord.x), abs(d.coord.z - g.coord.z)) == 1) Unit else null
                }
                // 50 ticks in: her call, and their answer 2 ticks after.
                val call = client.saw(duck.index, "[duck.call]")
                val answer = client.saw(group.index, "[duck.eep]")
                assertTrue(answer.ticks - call.ticks in 1..3, "${answer.ticks - call.ticks} ticks between call and answer")

                // Two tiles from the walking duck: it runs straight away, west.
                client.command("tele ${walker.coord.x + 2} ${walker.coord.z}")
                client.await("the walking duck running off") {
                    val now = client.npcs().firstOrNull { it.index == walker.index } ?: return@await null
                    if (now.coord.x <= walker.coord.x - 2) Unit else null
                }
            }
        }
    }

    private companion object {
        const val LAND_DUCK = 2003
    }
}
