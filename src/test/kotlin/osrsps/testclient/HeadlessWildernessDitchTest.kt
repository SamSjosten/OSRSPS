package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameFrame
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.command.WildernessJumps
import osrsps.game.content.WildernessDitch
import osrsps.game.content.WildernessDitchLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import osrsps.map.Tile

/**
 * Wilderness step W1.1 through the headless client: crossing the ditch north of Edgeville both ways, the warning screen
 * on the way in (Don't go, Enter Wilderness, the toggle after the seventh showing), the Wilderness overlay and
 * inside_wilderness following the player, and `::wildy`. The ditch is placed as the 242 cache has it (1 by 2 tiles, angle
 * 0 at z 3521) on open ground, and the three varbits with the cache's layouts. The rl-client script
 * test-runner/examples/ditch-cross.json does the same on the real map.
 */
class HeadlessWildernessDitchTest {
    private val types = LocTypes(
        listOf(WildernessDitch.DITCH, WildernessDitch.DITCH_MEMBERS).associateWith {
            LocType(it, "Wilderness Ditch", ops = listOf("Cross", null, null, null, null), collision = LocCollisionType(width = 1, length = 2))
        },
    )

    private val vars = VarTypes(
        6_000,
        mapOf(
            // cws_warning_7 on cws_var, cws_warning_id on smithbars, inside_wilderness on wilderness_statistics (242 cache).
            WildernessDitch.VARBIT_WARNING to VarbitType(CWS_VAR, 19, 21),
            WildernessDitch.VARBIT_WARNING_ID to VarbitType(210, 15, 20),
            World.VARBIT_INSIDE_WILDERNESS to VarbitType(WILDERNESS_STATISTICS, 22, 22),
        ),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply {
            for (sz in (3456 shr 6)..(3712 shr 6)) markLoaded(3090 shr 6, sz)
            // Rock south of the second run, as at x 3042 on the 242 map.
            add(3100, 3520, 0, osrsps.map.CollisionFlag.LOC)
        }
        val ditch = ZoneLoc(WildernessDitch.DITCH, LocShape.CENTREPIECE_STRAIGHT, 0, 3090, 3521, 0)
        // A second run, with the rock on its south side.
        val blocked = ZoneLoc(WildernessDitch.DITCH, LocShape.CENTREPIECE_STRAIGHT, 0, 3100, 3521, 0)
        // A members piece, as the 242 map has from x 3328 to 3340.
        val members = ZoneLoc(WildernessDitch.DITCH_MEMBERS, LocShape.CENTREPIECE_STRAIGHT, 0, 3110, 3521, 0)
        World(crcs, map, locTypes = types, staticLocs = StaticLocs().apply { add(ditch); add(blocked); add(members) }, varTypes = vars, cycleMillis = millis)
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.warningCount() = (varp(CWS_VAR) ushr 19) and 7

    private fun HeadlessClient.inside() = (varp(WILDERNESS_STATISTICS) ushr 22) and 1

    private fun HeadlessClient.overlayOpen() = GameFrame.PVP_ICONS in openInterfaces()

    private fun HeadlessClient.cross() = locOp(WildernessDitch.DITCH, 3090, 3521, WildernessDitch.CROSS)

    @Test
    fun `no jump onto rock on the far side`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("eli").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3100, 3523)
                client.locOp(WildernessDitch.DITCH, 3100, 3521, WildernessDitch.CROSS)
                client.await("the refusal") { client.messages().firstOrNull { it.text == "[game.cant_reach]" } }
                client.awaitTicks(4)
                assertEquals(Coord(3100, 3523, 0), client.position, "still north of the ditch")
            }
        }
    }

    @Test
    fun `the warning shows on the way in, Don't go stays and Enter Wilderness jumps, and the way back has none`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dana").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3090, 3520)
                client.awaitTicks(2)
                assertFalse(client.overlayOpen(), "no overlay south of the ditch, though the client's sum says level 1")
                assertEquals(0, client.inside())

                client.cross()
                client.await("the warning screen") { WildernessDitch.WARNING_SCREEN.takeIf { it in client.openInterfaces() } }
                assertEquals(DialogueText.NONE[WildernessDitchLines.WARNING], client.text(WildernessDitch.WARNING_SCREEN, WildernessDitch.WARNING_MESSAGE))
                // The client sends these clicks only with the pause button allowed in events1 (Codex on #917).
                for (button in listOf(WildernessDitch.DONT_GO, WildernessDitch.ENTER)) {
                    assertTrue(client.pauseEnabled(WildernessDitch.WARNING_SCREEN, WildernessDitch.WARNING_UNIVERSE, button), "button $button")
                }
                assertTrue(client.pauseEnabled(WildernessDitch.WARNING_SCREEN, WildernessDitch.WARNING_TOGGLE, 0), "the toggle")
                assertEquals(WildernessDitch.WARNING_ID, (client.varp(210) ushr 15) and 63, "cws_warning_id names the ditch's warning")
                client.resumePause(WildernessDitch.WARNING_SCREEN, WildernessDitch.WARNING_UNIVERSE, WildernessDitch.DONT_GO)
                client.await("the screen closed") { Unit.takeIf { WildernessDitch.WARNING_SCREEN !in client.openInterfaces() } }
                client.awaitTicks(3)
                assertEquals(Coord(3090, 3520, 0), client.position, "Don't go stays south")
                assertEquals(1, client.warningCount(), "seen once")

                client.cross()
                client.await("the warning screen again") { WildernessDitch.WARNING_SCREEN.takeIf { it in client.openInterfaces() } }
                val moves = client.exactMoveTicks().size
                client.resumePause(WildernessDitch.WARNING_SCREEN, WildernessDitch.WARNING_UNIVERSE, WildernessDitch.ENTER)
                client.await("across the ditch") { client.position?.takeIf { it == Coord(3090, 3523, 0) } }
                assertTrue(client.exactMoveTicks().size > moves, "the jump is an exact move")
                assertEquals(2, client.warningCount())
                client.await("the overlay open") { Unit.takeIf { client.overlayOpen() } }
                client.await("inside_wilderness set") { Unit.takeIf { client.inside() == 1 } }

                client.cross()
                client.await("back south") { client.position?.takeIf { it == Coord(3090, 3520, 0) } }
                assertFalse(WildernessDitch.WARNING_SCREEN in client.openInterfaces(), "no warning on the way out")
                client.await("the overlay closed") { Unit.takeIf { !client.overlayOpen() } }
                assertEquals(0, client.inside())
            }
        }
    }

    @Test
    fun `from the seventh showing the toggle turns the warning off, and off the ditch is crossed straight away`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3090, 3520)
                client.command("varbit ${WildernessDitch.VARBIT_WARNING} ${WildernessDitch.TOGGLE_SHOWN}")
                client.await("six showings counted") { Unit.takeIf { client.warningCount() == WildernessDitch.TOGGLE_SHOWN } }

                client.cross()
                client.await("the warning screen") { WildernessDitch.WARNING_SCREEN.takeIf { it in client.openInterfaces() } }
                client.resumePause(WildernessDitch.WARNING_SCREEN, WildernessDitch.WARNING_TOGGLE, 0)
                client.await("the warning off") { Unit.takeIf { client.warningCount() == WildernessDitch.WARNING_OFF } }
                assertTrue(WildernessDitch.WARNING_SCREEN in client.openInterfaces(), "the screen stays up after the toggle")
                client.resumePause(WildernessDitch.WARNING_SCREEN, WildernessDitch.WARNING_TOGGLE, 0)
                client.await("the warning on again") { Unit.takeIf { client.warningCount() == WildernessDitch.TOGGLE_SHOWN } }
                client.resumePause(WildernessDitch.WARNING_SCREEN, WildernessDitch.WARNING_TOGGLE, 0)
                client.await("and off") { Unit.takeIf { client.warningCount() == WildernessDitch.WARNING_OFF } }
                client.resumePause(WildernessDitch.WARNING_SCREEN, WildernessDitch.WARNING_UNIVERSE, WildernessDitch.ENTER)
                client.await("across") { client.position?.takeIf { it == Coord(3090, 3523, 0) } }
                assertEquals(WildernessDitch.WARNING_OFF, client.warningCount(), "answering doesn't count past the toggle")

                client.cross()
                client.await("back south") { client.position?.takeIf { it == Coord(3090, 3520, 0) } }
                client.cross()
                client.await("north again with no screen") { client.position?.takeIf { it == Coord(3090, 3523, 0) } }
                assertFalse(WildernessDitch.WARNING_SCREEN in client.openInterfaces())
            }
        }
    }

    @Test
    fun `the members pieces cross like the rest, warning and all`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ivy").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3110, 3520)
                client.locOp(WildernessDitch.DITCH_MEMBERS, 3110, 3521, WildernessDitch.CROSS)
                client.await("the warning screen") { WildernessDitch.WARNING_SCREEN.takeIf { it in client.openInterfaces() } }
                client.resumePause(WildernessDitch.WARNING_SCREEN, WildernessDitch.WARNING_UNIVERSE, WildernessDitch.ENTER)
                client.await("across the ditch") { client.position?.takeIf { it == Coord(3110, 3523, 0) } }
                client.locOp(WildernessDitch.DITCH_MEMBERS, 3110, 3521, WildernessDitch.CROSS)
                client.await("back south") { client.position?.takeIf { it == Coord(3110, 3520, 0) } }
            }
        }
    }

    @Test
    fun `before the seventh showing the toggle does nothing`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("fern").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3090, 3520)
                client.cross()
                client.await("the warning screen") { WildernessDitch.WARNING_SCREEN.takeIf { it in client.openInterfaces() } }
                client.resumePause(WildernessDitch.WARNING_SCREEN, WildernessDitch.WARNING_TOGGLE, 0)
                client.awaitTicks(2)
                assertEquals(0, client.warningCount(), "a forged toggle can't turn off a warning not yet seen enough")
            }
        }
    }

    @Test
    fun `wildy jumps to the level asked for and opens the overlay`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("gwen").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("wildy 20")
                val at = client.await("the jump") { client.position?.takeIf { it.z > 3600 } }
                assertEquals(20, Wilderness.level(at.x, at.z))
                client.await("the overlay open") { Unit.takeIf { client.overlayOpen() } }
                client.command("wildy 1")
                val first = client.await("the jump to level 1") { client.position?.takeIf { it.z < 3600 } }
                assertEquals(Coord(3090, 3523, 0), first, "level 1 lands north of the ditch")
                val reply = client.commandReply("wildy 57") { it.startsWith("Usage") }
                assertTrue("56" in reply)
            }
        }
    }

    @Test
    fun `a reconnect opens the overlay again inside the Wilderness`(@TempDir dir: Path) {
        server(dir).use { server ->
            val hal = server.login("hal")
            hal.await("the login rebuild") { hal.position }
            hal.command("wildy 5")
            hal.await("the overlay open") { Unit.takeIf { hal.overlayOpen() } }
            // The new connection knows nothing the old one was sent: the open must come again.
            server.reconnect("hal", hal).use { back ->
                back.await("the overlay open again") { Unit.takeIf { back.overlayOpen() } }
            }
            hal.close()
        }
    }

    @Test
    fun `a piece is jumped along its two-tile side, either way, and refused from its ends`() {
        val piece = ZoneLoc(WildernessDitch.DITCH, LocShape.CENTREPIECE_STRAIGHT, 0, 3090, 3521, 0)
        assertEquals(Tile(3090, 3523), WildernessDitch.landing(piece, 1, 2, Tile(3090, 3520)))
        assertEquals(Tile(3090, 3520), WildernessDitch.landing(piece, 1, 2, Tile(3090, 3523)))
        assertNull(WildernessDitch.landing(piece, 1, 2, Tile(3089, 3521)), "beside its short side")
        assertNull(WildernessDitch.landing(piece, 1, 2, Tile(3091, 3520)), "diagonal")
        // The run at x 2996 is turned (angle 3): 2 tiles along x, 1 along z, so it is jumped east and west.
        val turned = ZoneLoc(WildernessDitch.DITCH, LocShape.CENTREPIECE_STRAIGHT, 3, 2996, 3530, 0)
        assertEquals(Tile(2998, 3530), WildernessDitch.landing(turned, 1, 2, Tile(2995, 3530)))
        assertEquals(Tile(2995, 3530), WildernessDitch.landing(turned, 1, 2, Tile(2998, 3530)))
        assertNull(WildernessDitch.landing(turned, 1, 2, Tile(2996, 3529)))
    }

    @Test
    fun `the wildy line finds a tile when the line is blocked`() {
        val blocked = setOf(3090 to 3672, 3091 to 3672)
        val tile = WildernessJumps.tileFor(20) { x, z -> (x to z) !in blocked }
        assertEquals(3089 to 3672, tile, "the first row's next free tile west")
        assertNull(WildernessJumps.tileFor(20) { _, _ -> false })
    }

    private companion object {
        const val CWS_VAR = 1045
        const val WILDERNESS_STATISTICS = 1105
    }
}
