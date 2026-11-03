package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Graceful
import osrsps.game.Inventory
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.map.CollisionMap

/**
 * Agility step 3's graceful bonus through the headless client: the pieces worn by the backpack's Wear option, then the
 * run energy the player sees come back each tick while standing, against the same player before. Agility 1 restores
 * 15 a tick; the whole set makes it ⌊1.3 × 15⌋ = 19.
 */
class HeadlessGracefulEnergyTest {
    private val pieces = listOf(
        piece(11850, "graceful_hood", WearPos.HEAD), piece(11854, "graceful_top", WearPos.BODY),
        piece(11856, "graceful_legs", WearPos.LEGS), piece(11858, "graceful_gloves", WearPos.HANDS),
        piece(11860, "graceful_boots", WearPos.FEET), piece(11852, "graceful_cape", WearPos.CAPE),
    )

    private fun piece(id: Int, internalName: String, wearpos: Int) =
        ObjType(id, internalName, internalName = internalName, iops = listOf(null, "Wear", null, null, "Drop"), wearpos1 = wearpos)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes.of(*pieces.toTypedArray()), cycleMillis = millis)
    }

    private fun HeadlessClient.wear(id: Int) {
        command("item $id 1")
        val slot = await("item $id in the backpack") { inventory(Inventory.INV).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }
        ifButton(INVENTORY, INVENTORY_ITEMS, WEAR_BUTTON, slot, id)
        await("item $id worn") { inventory(WORN).firstOrNull { it?.id == id } }
    }

    /** Runs [tiles] east and back to spend some energy, then stands. */
    private fun HeadlessClient.runAndStand(tiles: Int) {
        val start = position!!
        walk(start.x + tiles, start.z, keyCombination = 1)
        await("run east") { position?.takeIf { it.x == start.x + tiles } }
        walk(start.x, start.z, keyCombination = 1)
        await("run back") { position?.takeIf { it.x == start.x } }
    }

    /** The energy one standing tick gives back: the gap between two updates after the next one. */
    private fun HeadlessClient.restorePerTick(): Int {
        val first = runEnergyHundredths
        val a = await("an energy update") { runEnergyHundredths.takeIf { it != first } }
        val b = await("the next energy update") { runEnergyHundredths.takeIf { it != a } }
        assertTrue(b < MAX_ENERGY, "still below full: $b")
        return b - a
    }

    @Test
    fun `the full graceful set worn from the backpack makes standing energy come back 30 percent faster`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.runAndStand(12)
                assertEquals(BARE, client.restorePerTick())
                for (piece in pieces) client.wear(piece.id)
                client.runAndStand(12)
                assertEquals(Graceful.boosted(BARE, Graceful.FULL_SET), client.restorePerTick())
                assertEquals(19, Graceful.boosted(BARE, Graceful.FULL_SET))
            }
        }
    }

    private companion object {
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
        const val WORN = 94
        const val WEAR_BUTTON = 3
        const val MAX_ENERGY = 10_000

        /** Agility 1's restore a tick: 1 / 10 + 15. */
        const val BARE = 15
    }
}
