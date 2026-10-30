package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.Crops
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.Firemaking
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * A fire lit where a crop was just picked used to take over the tile and drop the crop's respawn, so one player could
 * strip a field for good (third exploit review, 30 Oct 2026). Now the fire is refused until the crop is back.
 */
class HeadlessFireOnCropTest {
    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Crops.ONION + 1)
        val ground = listOf(null, null, "Take", "Light", null)
        objs[LOGS] = ObjType(LOGS, "Logs", ops = ground, tradeable = true)
        objs[Firemaking.TINDERBOX] = ObjType(Firemaking.TINDERBOX, "Tinderbox", tradeable = true)
        objs[Firemaking.ASHES] = ObjType(Firemaking.ASHES, "Ashes", tradeable = true)
        objs[Crops.ONION] = ObjType(Crops.ONION, "Onion", iops = listOf("Eat", null, null, null, "Drop"), internalName = "onion")
        // As HeadlessFredsFarmTest has it: a ground loc (shape 10, layer 2) the player walks through.
        val onion = LocType(Crops.ONION_LOC, "Onion", ops = listOf(null, "Pick", null, null, null), collision = LocCollisionType(interactType = 0))
        val fire = LocType(Firemaking.FIRE, "Fire", collision = LocCollisionType(width = 1, length = 1))
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            random = GameRandom(1),
            locTypes = LocTypes(mapOf(Crops.ONION_LOC to onion, Firemaking.FIRE to fire)),
            staticLocs = StaticLocs().apply { add(ZoneLoc(Crops.ONION_LOC, LocShape.CENTREPIECE_STRAIGHT, 0, ONION_X, ONION_Z, 0)) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.seen(text: String, timeoutMillis: Long = 10_000) =
        await(text, timeoutMillis) { messages().firstOrNull { it.text == text } }

    @Test
    fun `logs can't be lit on a picked crop's tile, and the crop grows back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("farmer").use { client ->
                client.await("the login rebuild") { client.position }
                fun onion() = server.world.locs.find(Crops.ONION_LOC, ONION_X, ONION_Z, 0)
                client.command("setlevel firemaking 43")
                client.command("item ${Firemaking.TINDERBOX}")
                client.command("item $LOGS")
                client.slotOf(LOGS)
                client.slotOf(Firemaking.TINDERBOX)

                client.locOp(Crops.ONION_LOC, ONION_X, ONION_Z, Crops.PICK)
                client.seen(DialogueText.NONE[Crops.ONIONS.picked])
                client.await("the onion") { if (client.count(Crops.ONION) == 1) Unit else null }
                assertNull(onion())

                client.walk(ONION_X, ONION_Z)
                client.await("on the onion's tile") { client.position?.takeIf { it.x == ONION_X && it.z == ONION_Z } }
                client.ifButtonT(
                    InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(Firemaking.TINDERBOX), Firemaking.TINDERBOX,
                    InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(LOGS), LOGS,
                )
                client.seen("[firemaking.cant_here]")
                assertNull(server.world.locs.find(Firemaking.FIRE, ONION_X, ONION_Z, 0))
                assertEquals(1, client.count(LOGS), "the logs stay in the backpack")

                client.await("the onion back", timeoutMillis = 30_000) { onion() }
            }
        }
    }

    private companion object {
        const val LOGS = 1511
        const val BACKPACK = 93
        const val ONION_X = 3220
        const val ONION_Z = 3220
    }
}
