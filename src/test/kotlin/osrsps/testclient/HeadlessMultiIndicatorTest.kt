package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import org.junit.jupiter.api.io.TempDir
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.combat.MultiCombat
import osrsps.map.CollisionMap

/**
 * Edgeville step 6 through the headless client: the multi-way icon's varbit (multiway_indicator 4605, bit 5 of varp
 * 1021 in the 241 cache) turns on in Barbarian Village and off again outside it, after a teleport and after a walk.
 */
class HeadlessMultiIndicatorTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3071 shr 6, 3420 shr 6)
                markLoaded(3072 shr 6, 3420 shr 6)
            },
            varTypes = VarTypes(6_000, mapOf(MultiCombat.VARBIT_INDICATOR to VarbitType(VARP, BIT, BIT))),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.icon(): Int = (varp(VARP) ushr BIT) and 1

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    @Test
    fun `the icon shows in Barbarian Village and goes outside it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3080, 3420)
                client.await("the icon on") { Unit.takeIf { client.icon() == 1 } }
                client.tele(3060, 3420)
                client.await("the icon off") { Unit.takeIf { client.icon() == 0 } }
                client.tele(3071, 3420)
                client.walk(3073, 3420)
                client.await("the icon on after walking in") { Unit.takeIf { client.icon() == 1 } }
            }
        }
    }

    private companion object {
        const val VARP = 1021
        const val BIT = 5
    }
}
