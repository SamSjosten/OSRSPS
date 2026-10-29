package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.Bones
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Rest of Lumbridge step 1 through the headless client: burying bones, and an item used on a loc (OPLOCT). The
 * rl-client script test-runner/examples/lumbridge-bury-bones.json buries bones in the real client.
 */
class HeadlessBonesTest {
    private val bones = ObjType(BONES, "Bones", iops = listOf("Bury", null, null, null, "Drop"), internalName = "bones")
    private val bucket = ObjType(BUCKET, "Bucket", iops = listOf(null, null, null, null, "Drop"), internalName = "bucket_empty")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(BUCKET + 1)
        objs[BONES] = bones
        objs[BUCKET] = bucket
        val well = LocType(WELL, "Well", collision = LocCollisionType(width = 1, length = 1))
        val pump = LocType(PUMP, "Pump", collision = LocCollisionType(width = 1, length = 1))
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            locTypes = LocTypes(mapOf(WELL to well, PUMP to pump)),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(WELL, LocShape.CENTREPIECE_STRAIGHT, 0, 3225, 3218, 0))
                add(ZoneLoc(PUMP, LocShape.CENTREPIECE_STRAIGHT, 0, 3219, 3218, 0))
            },
            cycleMillis = millis,
        ).apply {
            handlers.onObjOnLoc(WELL, BUCKET) { player.message("You fill the bucket.") }
        }
    }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    @Test
    fun `Bury takes the bones, says so and gives 4,5 Prayer XP`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $BONES 2")
                val slot = client.slotOf(BONES)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, BURY_BUTTON_OP, slot, BONES)
                client.seen(Bones.DIG)
                client.seen(Bones.BURIED)
                client.await("one bone left") { if (client.inventory(BACKPACK).count { it?.id == BONES } == 1) Unit else null }
                // 45 tenths: the stat packet carries whole XP.
                assertEquals(4, client.await("Prayer XP") { client.stat(PRAYER)?.takeIf { it.xp > 0 } }.xp)

                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, BURY_BUTTON_OP, client.slotOf(BONES), BONES)
                client.await("no bones left") { if (client.inventory(BACKPACK).none { it?.id == BONES }) Unit else null }
                assertEquals(9, client.await("9 Prayer XP") { client.stat(PRAYER)?.takeIf { it.xp >= 9 } }.xp)
            }
        }
    }

    @Test
    fun `an item used on a loc walks there and runs its handler, or does nothing interesting`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $BUCKET")
                val slot = client.slotOf(BUCKET)
                client.locTarget(WELL, 3225, 3218, InventoryTab.INTERFACE, InventoryTab.ITEMS, slot, BUCKET)
                client.seen("You fill the bucket.")
                client.locTarget(PUMP, 3219, 3218, InventoryTab.INTERFACE, InventoryTab.ITEMS, slot, BUCKET)
                client.seen("[game.nothing_interesting]")
            }
        }
    }

    private companion object {
        const val BONES = 526
        const val BUCKET = 1925
        const val WELL = 9010
        const val PUMP = 9011
        const val BACKPACK = 93
        const val PRAYER = 5

        /** iop1 is button op 2 (see InventoryTab.click). */
        const val BURY_BUTTON_OP = 2
    }
}
