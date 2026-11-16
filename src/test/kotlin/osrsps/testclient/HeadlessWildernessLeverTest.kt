package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.content.WildernessLevers
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap


/**
 * Wilderness step W3.2 through the headless client: the Edgeville lever to the Deserted Keep, the Keep's back to East
 * Ardougne and Ardougne's to the Keep again, with the Wiki's lines; the Mage Arena bank's levers in and out; and a Tele
 * Blocked player refused. The levers are placed as the 242 map has them.
 */
class HeadlessWildernessLeverTest {
    private companion object {
        /** The map's loc shapes 4 and 5: straight wall decorations, without and with the offset. */
        const val WALLDECOR_NOOFFSET = 4
        const val WALLDECOR_OFFSET = 5
    }

    private val placed = listOf(
        ZoneLoc(WildernessLevers.EDGEVILLE, WALLDECOR_NOOFFSET, 0, 3090, 3475, 0),
        ZoneLoc(WildernessLevers.ARDOUGNE, WALLDECOR_NOOFFSET, 0, 2561, 3311, 0),
        ZoneLoc(WildernessLevers.KEEP_DEFAULT, WALLDECOR_OFFSET, 0, 3153, 3923, 0),
        ZoneLoc(WildernessLevers.TO_MAGE_BANK, WALLDECOR_NOOFFSET, 0, 3090, 3956, 0),
        ZoneLoc(WildernessLevers.FROM_MAGE_BANK, WALLDECOR_NOOFFSET, 3, 2539, 4712, 0),
    )

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 50) { crcs, millis ->
        val ops = listOf("Pull", null, null, null, null)
        World(
            crcs, CollisionMap().apply { for (l in placed) markLoaded(l.x shr 6, l.z shr 6) },
            random = GameRandom(3), locTypes = LocTypes(placed.associate { it.id to LocType(it.id, "Lever", ops) }),
            staticLocs = StaticLocs().apply { placed.forEach(::add) }, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    /** Waits for [key] among every message so far: the arrival's line comes in the same tick as the move. */
    private fun HeadlessClient.seen(key: String) = await(key) { messages().firstOrNull { it.text == key } }

    private fun HeadlessClient.awaitAt(x: Int, z: Int) =
        await("$x, $z", timeoutMillis = 15_000) { position?.takeIf { it == Coord(x, z, 0) } }

    @Test
    fun `Edgeville's lever goes to the Deserted Keep, the Keep's to Ardougne, and Ardougne's back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("rae").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.tele(3090, 3475)
                client.locOp(WildernessLevers.EDGEVILLE, 3090, 3475, WildernessLevers.PULL)
                client.seen("[wildy_lever.pull]")
                client.awaitAt(3153, 3923)
                client.seen("[wildy_lever.into]")
                assertEquals(51, Wilderness.level(3153, 3923))
                client.locOp(WildernessLevers.KEEP_DEFAULT, 3153, 3923, WildernessLevers.PULL)
                client.awaitAt(2561, 3311)
                client.seen("[wildy_lever.out]")
                client.locOp(WildernessLevers.ARDOUGNE, 2561, 3311, WildernessLevers.PULL)
                client.awaitAt(3153, 3923)
            }
        }
    }

    @Test
    fun `the Mage Arena bank's levers go in and out, and none takes a Tele Blocked player`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("sol").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.tele(3090, 3956)
                client.locOp(WildernessLevers.TO_MAGE_BANK, 3090, 3956, WildernessLevers.PULL)
                client.awaitAt(2539, 4712)
                client.locOp(WildernessLevers.FROM_MAGE_BANK, 2539, 4712, WildernessLevers.PULL)
                client.awaitAt(3090, 3956)

                server.world.playerNamed("sol")!!.teleblockTicks = 400
                client.locOp(WildernessLevers.TO_MAGE_BANK, 3090, 3956, WildernessLevers.PULL)
                client.awaitMessage("the Tele Block refusal") { it == "[pvp.teleport_blocked]" }
                client.tele(3153, 3923)
                server.world.playerNamed("sol")!!.teleblockTicks = 400
                client.locOp(WildernessLevers.KEEP_DEFAULT, 3153, 3923, WildernessLevers.PULL)
                val refusals = { client.messages().count { it.text == "[pvp.teleport_blocked]" } }
                client.await("the Keep lever's refusal") { Unit.takeIf { refusals() == 2 } }
                assertEquals(Coord(3153, 3923, 0), client.position)
            }
        }
    }

    @Test
    fun `a Tele Block landing during the pull or the teleport still keeps the player`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("tam").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                val tam = server.world.playerNamed("tam")!!
                val refusals = { client.messages().count { it.text == "[pvp.teleport_blocked]" } }
                for (after in listOf(1, 1 + WildernessLevers.PULL_TICKS)) {
                    client.tele(3153, 3923)
                    tam.teleblockTicks = 0
                    val before = refusals()
                    client.commandReply("pause") { it.startsWith("Paused after tick") }
                    client.locOp(WildernessLevers.KEEP_DEFAULT, 3153, 3923, WildernessLevers.PULL)
                    client.stepTicks(after)
                    tam.teleblockTicks = 400
                    client.stepTicks(WildernessLevers.PULL_TICKS + WildernessLevers.TELEPORT_TICKS + 2)
                    client.commandReply("resume") { it.startsWith("Resumed") }
                    client.await("the refusal ${after} ticks in") { Unit.takeIf { refusals() == before + 1 } }
                    assertEquals(Coord(3153, 3923, 0), client.position, "blocked $after ticks into the pull")
                }
                tam.teleblockTicks = 0
            }
        }
    }
}
