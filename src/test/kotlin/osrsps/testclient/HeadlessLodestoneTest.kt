package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.World
import osrsps.game.content.CustomLocs
import osrsps.game.content.CustomVars
import osrsps.game.content.Lodestones
import osrsps.game.loc.LocTypes
import osrsps.map.CollisionMap

/**
 * The lodestone network over the game protocol: a login has Lumbridge's bit set, Activate on another stone sets its
 * bit (which is what turns the client's drawing of that stone active) and says so, `::lodestone` opens the menu of the
 * activated stones and a pick lands on the stone [Lodestones.CAST_TICKS] ticks later, a stone not yet activated is
 * refused by name, and nothing casts above level 20 Wilderness. The rl-client script
 * test-runner/examples/lodestone-network.json makes the same clicks in the real client.
 */
class HeadlessLodestoneTest {
    private val lumbridge = Lodestones.LUMBRIDGE
    private val draynor = Lodestones.DRAYNOR

    /** The stones' map squares and a Wilderness one, open ground, with our loc types so the world places the stones. */
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                for (site in Lodestones.SITES) markLoaded(site.x shr 6, site.z shr 6)
                markLoaded(WILDERNESS.x shr 6, WILDERNESS.z shr 6)
            },
            locTypes = LocTypes.NONE.with(CustomLocs.types), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(to: Coord) {
        commandReply("tele ${to.x} ${to.z}") { it.startsWith("Teleported") }
        await("the teleport") { position?.takeIf { it == to } }
    }

    @Test
    fun `activate, the menu, the cast, a locked stone and the Wilderness`(@TempDir dir: Path) {
        server(dir).use { server ->
            val world = server.world
            for (site in Lodestones.SITES) assertEquals(site.id, world.locs[site.x, site.z, 0, site.loc.layer]?.id, site.slug)
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.await("Lumbridge's bit at login") { client.varp(CustomVars.LODESTONES_VARP).takeIf { it == 1 } }

                client.tele(Coord(draynor.x + 4, draynor.z + 1))
                val stone = LocChange(draynor.id, draynor.loc.shape, draynor.loc.angle, Coord(draynor.x, draynor.z))
                client.await("the Draynor stone to reach the client") { client.locChanges().takeIf { stone in it } }
                client.locOp(draynor.id, draynor.x, draynor.z, Lodestones.ACTIVATE)
                client.awaitMessage("the activation") { it == "[lodestone.activated name=[lodestone.draynor]]" }
                client.await("Draynor's bit") { client.varp(CustomVars.LODESTONES_VARP).takeIf { it == 3 } }

                client.command("lodestone")
                val menu = client.awaitDialogue("the lodestone menu") { it is DialogueView.Menu }
                assertEquals(DialogueView.Menu("[lodestone.title]", listOf("[lodestone.lumbridge]", "[lodestone.draynor]")), menu)
                val animsBefore = client.animationTicks().size
                val ticksAtPick = client.ticksSeen
                client.chooseOption(1)
                client.await("the landing", timeoutMillis = 20_000) { client.position?.takeIf { it == Coord(lumbridge.x + 1, lumbridge.z + 1) } }
                val landedAfter = client.ticksSeen - ticksAtPick
                // The pick lands in the next tick's input; the landing shows 28 ticks after that.
                check(landedAfter in 28..30) { "landed $landedAfter ticks after the pick" }
                val stages = client.animationTicks().drop(animsBefore).take(5)
                assertEquals(listOf(8, 7, 4, 3), stages.zipWithNext { a, b -> b - a }, "the gaps between the stages, from $stages")

                // Standing on the stone, Teleport (op1 of the active look) opens the same menu.
                client.locOp(lumbridge.id, lumbridge.x, lumbridge.z, Lodestones.TELEPORT)
                assertEquals(menu, client.awaitDialogue("the menu from the active stone") { it is DialogueView.Menu })
                client.chooseOption(2)
                client.await("the landing at Draynor", timeoutMillis = 20_000) { client.position?.takeIf { it == Coord(draynor.x + 1, draynor.z + 1) } }

                client.command("lodestone varrock")
                client.awaitMessage("the locked stone") { it == "[lodestone.locked name=[lodestone.varrock]]" }
                client.awaitCycles(2)
                assertEquals(Coord(draynor.x + 1, draynor.z + 1), client.position)

                client.tele(WILDERNESS)
                val animsBeforeWilderness = client.animationTicks().size
                val messagesBefore = client.messages().size
                client.command("lodestone lumbridge")
                // Both lines come in the same tick, so one wait looks for both.
                client.await("the Wilderness refusal's two lines") {
                    client.messages().drop(messagesBefore).map { it.text }.takeIf { "[home_teleport.wilderness]" in it && "[home_teleport.wilderness_2]" in it }
                }
                client.awaitCycles(Lodestones.CAST_TICKS + 2)
                assertEquals(WILDERNESS, client.position)
                assertEquals(animsBeforeWilderness, client.animationTicks().size, "no cast started in the Wilderness")
            }
        }
    }

    private companion object {
        /** Level 23 Wilderness. */
        val WILDERNESS = Coord(3100, 3700)
    }
}
