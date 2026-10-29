package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.combat.Foods
import osrsps.map.CollisionMap

/**
 * Eating through the headless client, with the real food file: a shark and then a karambwan clicked one after the other
 * both heal, the plan's check for step 6a. The rl-client script test-runner/examples/food-combo.json eats them in the
 * real client.
 */
class HeadlessFoodTest {
    private fun food(id: Int, internal: String, name: String) = ObjType(id, name, iops = listOf("Eat", null, null, null, "Drop"), internalName = internal)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(KARAMBWAN + 1)
        objs[SHARK] = food(SHARK, "shark", "Shark")
        objs[KARAMBWAN] = food(KARAMBWAN, "tbwt_cooked_karambwan", "Cooked karambwan")
        val objTypes = ObjTypes(objs)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = objTypes, foodList = Foods.load(objTypes),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    @Test
    fun `a shark then a karambwan both heal, and the food is gone`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("setlevel hitpoints 99") { it.contains("set to level 99") }
                client.commandReply("hit 49 me") { it == "Hit yourself for 49: 50/99 hp" }
                client.command("item $SHARK")
                client.command("item $KARAMBWAN")
                val shark = client.slotOf(SHARK)
                val karambwan = client.slotOf(KARAMBWAN)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, EAT_BUTTON_OP, shark, SHARK)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, EAT_BUTTON_OP, karambwan, KARAMBWAN)
                // 20 and 18.
                client.await("88 hitpoints") { client.stat(HITPOINTS)?.takeIf { it.level == 88 } }
                client.await("both eaten") { if (client.inventory(BACKPACK).none { it?.id == SHARK || it?.id == KARAMBWAN }) Unit else null }
                client.awaitTicks(1)
                assertEquals(88, client.stat(HITPOINTS)!!.level)
            }
        }
    }

    private companion object {
        const val SHARK = 385
        const val KARAMBWAN = 3144
        const val BACKPACK = 93
        const val HITPOINTS = 3

        /** Eat is iop1, which the inventory sends as button op 2. */
        const val EAT_BUTTON_OP = 2
    }
}
