package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import org.junit.jupiter.api.io.TempDir
import osrsps.game.World
import osrsps.game.content.CustomLocs
import osrsps.game.content.Lodestones
import osrsps.game.loc.LocTypes
import osrsps.map.CollisionMap

/**
 * The Lumbridge lodestone over the game protocol: the world places it and the client is told, Activate shows the
 * active stone, and the dormant one is back exactly [Lodestones.PREVIEW_TICKS] ticks later. The world is paused and
 * stepped so the test counts game ticks, not test-thread time.
 */
class HeadlessLodestoneTest {
    private val stone = Lodestones.LUMBRIDGE
    private val dormant = LocChange(Lodestones.DORMANT, stone.shape, stone.angle, Coord(stone.x, stone.z))
    private val active = LocChange(Lodestones.ACTIVE, stone.shape, stone.angle, Coord(stone.x, stone.z))

    /** The stone's map square, open ground, with the lodestone's own loc types so the world places the stone. */
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(stone.x shr 6, stone.z shr 6) },
            locTypes = LocTypes.NONE.with(CustomLocs.types), cycleMillis = millis,
        )
    }

    private fun World.stoneId() = locs[stone.x, stone.z, stone.level, stone.layer]?.id

    @Test
    fun `Activate shows the active stone, and the dormant one is back after the preview`(@TempDir dir: Path) {
        server(dir).use { server ->
            val world = server.world
            assertEquals(Lodestones.DORMANT, world.stoneId(), "the stone placed at start")
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele ${stone.x + 4} ${stone.z + 1} 0")
                client.await("the placed stone to reach the client") { client.locChanges().takeIf { dormant in it } }
                client.commandReply("pause") { it.startsWith("Paused after tick") }
                client.locOp(Lodestones.DORMANT, stone.x, stone.z, Lodestones.ACTIVATE)
                // Walking up to the stone takes a tick or two; the handler runs on the tick the player arrives.
                repeat(10) { if (world.stoneId() != Lodestones.ACTIVE) client.stepTicks(1) }
                if (world.stoneId() != Lodestones.ACTIVE) fail("Activate didn't swap the stone within 10 ticks")
                val activated = world.tick
                client.await("the active stone to reach the client") { client.locChanges().takeIf { active in it && dormant !in it } }

                client.stepTicks(Lodestones.PREVIEW_TICKS - 1)
                assertEquals(activated + Lodestones.PREVIEW_TICKS - 1, world.tick)
                assertEquals(Lodestones.ACTIVE, world.stoneId(), "still active a tick before the preview ends")
                client.await("the active stone to still show") { client.locChanges().takeIf { active in it } }

                client.stepTicks(1)
                assertEquals(Lodestones.DORMANT, world.stoneId(), "dormant again ${Lodestones.PREVIEW_TICKS} ticks after Activate")
                client.await("the dormant stone to reach the client") { client.locChanges().takeIf { dormant in it && active !in it } }
                assertEquals(1, world.locs.changed, "the placed stone is the only change left")
            }
        }
    }
}
