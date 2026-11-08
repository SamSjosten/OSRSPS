package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.MemorySaveStore
import osrsps.game.PlayerSave
import osrsps.game.SaveStore
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.content.CustomLocs
import osrsps.game.content.CustomVars
import osrsps.game.content.LodestoneMap
import osrsps.game.content.Lodestones
import osrsps.game.loc.LocTypes
import osrsps.map.CollisionMap

/**
 * The lodestone network over the game protocol: a login has Lumbridge's bit set, Activate on another stone sets its
 * bit (which is what turns the client's drawing of that stone active) and says so, `::lodestone` opens the map, which
 * shows each stone's state and offers Teleport on the activated ones, a pick lands on the stone [Lodestones.CAST_TICKS]
 * ticks later, Previous destination names it next time, a stone not yet activated is refused by name, and nothing
 * casts above level 20 Wilderness. The rl-client script test-runner/examples/lodestone-network.json makes the same
 * clicks in the real client.
 */
class HeadlessLodestoneTest {
    private val lumbridge = Lodestones.LUMBRIDGE
    private val draynor = Lodestones.DRAYNOR
    private val varrock = Lodestones.VARROCK

    /** Our vars after the test world's (none): the stones' loc types name the varbits, so the world gets the same. */
    private val vars = CustomVars.after(VarTypes.NONE)

    private val saves = MemorySaveStore()

    /** The stones' map squares and a Wilderness one, open ground, with our loc types so the world places the stones. */
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                for (site in Lodestones.SITES) markLoaded(site.x shr 6, site.z shr 6)
                markLoaded(WILDERNESS.x shr 6, WILDERNESS.z shr 6)
            },
            customVars = vars, locTypes = LocTypes.NONE.with(CustomLocs(vars).types), saves = saves, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(to: Coord) {
        commandReply("tele ${to.x} ${to.z}") { it.startsWith("Teleported") }
        await("the teleport") { position?.takeIf { it == to } }
    }

    /** Waits for the map to be open with everything the server sets at an open: the close button's events come last. */
    private fun HeadlessClient.awaitMap() {
        await("the lodestone map") { if (MAP in openInterfaces() && opEnabled(MAP, LodestoneMap.CLOSE, -1, LodestoneMap.CLOSE_OP)) Unit else null }
    }

    @Test
    fun `activate, the menu, the cast, a locked stone and the Wilderness`(@TempDir dir: Path) {
        server(dir).use { server ->
            val world = server.world
            for (site in Lodestones.SITES) assertEquals(site.id, world.locs[site.x, site.z, 0, site.loc.layer]?.id, site.slug)
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                client.await("Lumbridge's bit at login") { client.varp(vars.lodestonesVarp).takeIf { it == 1 } }

                client.tele(Coord(draynor.x + 4, draynor.z + 1))
                val stone = LocChange(draynor.id, draynor.loc.shape, draynor.loc.angle, Coord(draynor.x, draynor.z))
                client.await("the Draynor stone to reach the client") { client.locChanges().takeIf { stone in it } }
                client.locOp(draynor.id, draynor.x, draynor.z, Lodestones.ACTIVATE)
                client.awaitMessage("the activation") { it == "[lodestone.activated name=[lodestone.draynor]]" }
                client.await("Draynor's bit") { client.varp(vars.lodestonesVarp).takeIf { it == 3 } }

                client.command("lodestone")
                client.awaitMap()
                assertEquals("[lodestone.title]", client.text(MAP, LodestoneMap.TITLE))
                assertEquals("[lodestone.lumbridge]", client.text(MAP, LodestoneMap.rowText(lumbridge)))
                assertEquals("[lodestone.draynor]", client.text(MAP, LodestoneMap.rowText(draynor)))
                assertEquals("<col=9f9f9f>[lodestone.varrock]</col>", client.text(MAP, LodestoneMap.rowText(varrock)), "a locked stone's name is grey")
                assertEquals(false, client.hidden(MAP, LodestoneMap.activeIcon(lumbridge)))
                assertEquals(true, client.hidden(MAP, LodestoneMap.dormantIcon(lumbridge)))
                assertEquals(true, client.hidden(MAP, LodestoneMap.activeIcon(varrock)))
                assertEquals(false, client.hidden(MAP, LodestoneMap.dormantIcon(varrock)))
                assertTrue(client.opEnabled(MAP, LodestoneMap.row(draynor), -1, LodestoneMap.TELEPORT))
                assertTrue(client.opEnabled(MAP, LodestoneMap.iconButton(draynor), -1, LodestoneMap.TELEPORT))
                assertFalse(client.opEnabled(MAP, LodestoneMap.row(varrock), -1, LodestoneMap.TELEPORT), "a locked stone offers no option")
                assertEquals(true, client.hidden(MAP, LodestoneMap.PREVIOUS), "no previous destination yet")
                // The world is paused and stepped from here, so the cast is counted in game ticks, not test-thread time.
                client.commandReply("pause") { it.startsWith("Paused after tick") }
                val animsBefore = client.animationTicks().size
                val lumbridgeCentre = Coord(lumbridge.x + 1, lumbridge.z + 1)
                val standing = client.position
                client.ifButton(MAP, LodestoneMap.row(lumbridge), LodestoneMap.TELEPORT)
                // The pick is the next tick's input, and the cast's first stage plays on that tick.
                repeat(3) { if (client.animationTicks().size == animsBefore) client.stepTicks(1) }
                assertEquals(1, client.animationTicks().size - animsBefore, "the first stage on the tick the pick is processed")
                client.await("the map closed on the pick") { if (MAP !in client.openInterfaces()) Unit else null }
                val started = client.ticksSeen
                client.stepTicks(Lodestones.CAST_TICKS - 1)
                assertEquals(standing, client.position, "still beside the Draynor stone a tick before the landing")
                client.stepTicks(1)
                client.await("the landing") { client.position?.takeIf { it == lumbridgeCentre } }
                assertEquals(started + Lodestones.CAST_TICKS, client.ticksSeen, "lands ${Lodestones.CAST_TICKS} ticks after the first stage")
                val allStages = client.animationTicks().drop(animsBefore).take(Lodestones.STAGES.size)
                assertEquals(listOf(Lodestones.CAST_TICKS - Lodestones.RISE_TICKS), allStages.zipWithNext { a, b -> b - a }, "the gap between the charge and the rise, from $allStages")
                client.commandReply("resume") { it.startsWith("Resumed after tick") }

                // Standing on the stone, Teleport (op1 of the active look) opens the same map, now with Previous destination.
                client.locOp(lumbridge.id, lumbridge.x, lumbridge.z, Lodestones.TELEPORT)
                client.awaitMap()
                assertEquals(false, client.hidden(MAP, LodestoneMap.PREVIOUS))
                assertEquals("[lodestone.previous name=[lodestone.lumbridge]]", client.text(MAP, LodestoneMap.PREVIOUS_TEXT))
                assertTrue(client.opEnabled(MAP, LodestoneMap.PREVIOUS, -1, LodestoneMap.TELEPORT))
                client.ifButton(MAP, LodestoneMap.iconButton(draynor), LodestoneMap.TELEPORT)
                client.await("the landing at Draynor", timeoutMillis = 30_000) { client.position?.takeIf { it == Coord(draynor.x + 1, draynor.z + 1) } }

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

                // The activated stones and the previous destination go in the save by name, so a logout and a login keep Draynor's.
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.loginAgain("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.await("both stones' bits and Draynor as the previous destination from the save") { client.varp(vars.lodestonesVarp).takeIf { it == SAVED } }
                val save = PlayerSave.fromJson(saves.read(SaveStore.saveKey("alice")!!)!!)
                assertEquals(mapOf("lodestones" to SAVED), save.customVars)
                assertFalse(vars.lodestonesVarp in save.varps, "not by id")
            }
        }
    }

    /**
     * The nightly's `::resetlocs 10` in the courtyard (3222, 3218) reaches Lumbridge's stone at 3232, 3220. It took the
     * stone away until a restart when a restore put back the bare cache tile; the stone is the world's for good.
     */
    @Test
    fun `resetlocs around the courtyard keeps Lumbridge's stone, on the server and at the client`(@TempDir dir: Path) {
        server(dir).use { server ->
            val world = server.world
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(Coord(3222, 3218))
                val stone = LocChange(lumbridge.id, lumbridge.loc.shape, lumbridge.loc.angle, Coord(lumbridge.x, lumbridge.z))
                client.await("the Lumbridge stone to reach the client") { client.locChanges().takeIf { stone in it } }
                client.commandReply("resetlocs 10") { it.startsWith("Put the locs within 10 of 3222, 3218") }
                assertEquals(lumbridge.loc, world.locs[lumbridge.x, lumbridge.z, 0, lumbridge.loc.layer])
                // A tick later (the teleport's round trip) the client still has the stone: nothing took it away.
                client.tele(Coord(3223, 3218))
                assertTrue(stone in client.locChanges(), client.locChanges().toString())
                client.locOp(lumbridge.id, lumbridge.x, lumbridge.z, Lodestones.TELEPORT)
                client.awaitMap()
            }
        }
    }

    /** Logs in once the server has let the last login of [name] go: until then a login is refused as still on. */
    private fun TestServer.loginAgain(name: String): HeadlessClient {
        repeat(50) {
            runCatching { login(name) }.onSuccess { return it }
            Thread.sleep(100)
        }
        return login(name)
    }

    private companion object {
        /** Level 23 Wilderness. */
        val WILDERNESS = Coord(3100, 3700)
        const val MAP = LodestoneMap.INTERFACE

        /** Lumbridge's and Draynor's bits, and Draynor (index 1, so 2) as the previous destination in bits 8 to 11. */
        const val SAVED = 3 or (2 shl Lodestones.PREVIOUS_LOW_BIT)
    }
}
