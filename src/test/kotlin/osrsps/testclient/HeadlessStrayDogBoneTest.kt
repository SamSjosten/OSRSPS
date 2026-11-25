package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.Varrock
import osrsps.game.npc.NpcSpawn
import osrsps.map.CollisionMap

/** Bones used on a Varrock stray dog: one goes, and it's the Varrock easy diary's "Give a dog a bone". */
class HeadlessStrayDogBoneTest {
    private val dog = NpcType(Varrock.STRAY_DOGS.first(), "Stray dog", ops = listOf(null, "Shoo-away", "Pet", null, null))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            npcTypes = NpcTypes.of(dog), objTypes = ObjTypes.of(ObjType(Varrock.BONES, "Bones")),
            npcSpawns = listOf(NpcSpawn(dog, 3224, 3218, wander = 0, source = "test")),
            cycleMillis = millis,
        )
    }

    @Test
    fun `a bone feeds the dog and does the diary task`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item ${Varrock.BONES} 2")
                val slot = client.await("the bones") { client.inventory(Inventory.INV).indexOfFirst { it?.id == Varrock.BONES }.takeIf { it >= 0 } }
                val npc = client.await("the dog") { client.npcs().firstOrNull { it.id == dog.id } }
                client.npcTarget(npc.index, InventoryTab.INTERFACE, InventoryTab.ITEMS, sub = slot, obj = Varrock.BONES)
                client.await("the diary task") { Unit.takeIf { client.messages().any { it.text.startsWith("[diary.task_done") } } }
                assertEquals(1, client.inventory(Inventory.INV).count { it?.id == Varrock.BONES })
            }
        }
    }
}
