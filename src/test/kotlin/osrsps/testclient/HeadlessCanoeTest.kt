package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.CanoeDestination
import osrsps.game.skill.CanoeStation
import osrsps.game.skill.CanoeType
import osrsps.game.skill.Canoes
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Gathering 2, step G3 through the headless client: the Lumbridge canoe station's tree chopped, a canoe shaped, floated
 * and paddled to the Champions' Guild, and the checks on the way, on open ground (no cache collision in tests). The
 * rl-client script test-runner/examples/canoes.json does the same in the real client.
 */
class HeadlessCanoeTest {
    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(RUNE_AXE + 1)
        objs[RUNE_AXE] = ObjType(RUNE_AXE, "Rune axe")
        fun station(id: Int) = LocType(id, "Canoe Station", listOf("Make-Canoe", null, null, null, null), LocCollisionType(width = 5, length = 2), multiloc = true)
        World(
            crcs,
            CollisionMap().apply {
                markLoaded(3222 shr 6, 3218 shr 6)
                markLoaded(LUM_X shr 6, LUM_Z shr 6)
                markLoaded(CHAMPIONS_X shr 6, CHAMPIONS_Z shr 6)
            },
            objTypes = ObjTypes(objs),
            random = GameRandom(5),
            locTypes = LocTypes(CanoeStation.entries.map { station(it.loc) }.associateBy { it.id }),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(CanoeStation.LUMBRIDGE.loc, LocShape.CENTREPIECE_STRAIGHT, 0, LUM_X, LUM_Z, 0))
                add(ZoneLoc(CanoeStation.CHAMPIONS_GUILD.loc, LocShape.CENTREPIECE_STRAIGHT, 0, CHAMPIONS_X, CHAMPIONS_Z, 0))
            },
            varTypes = VarTypes(
                6_000,
                mapOf(
                    CanoeStation.LUMBRIDGE.varbit to VarbitType(STATIONS_VARP, 0, 7),
                    CanoeStation.CHAMPIONS_GUILD.varbit to VarbitType(STATIONS_VARP, 8, 15),
                    CanoeStation.BARBARIAN_VILLAGE.varbit to VarbitType(STATIONS_VARP, 16, 23),
                    CanoeStation.EDGEVILLE.varbit to VarbitType(STATIONS_VARP, 24, 31),
                    CanoeStation.FEROX_ENCLAVE.varbit to VarbitType(2742, 0, 7),
                    Canoes.VARBIT_CANOE_TYPE to VarbitType(675, 0, 7),
                    Canoes.VARBIT_STARTFROM to VarbitType(STARTFROM_VARP, 0, 8),
                    Canoes.VARBIT_RIVER to VarbitType(STARTFROM_VARP, 9, 11),
                ),
            ),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.lumbridge(): Int = varp(STATIONS_VARP) and 0xFF

    private fun HeadlessClient.champions(): Int = (varp(STATIONS_VARP) ushr 8) and 0xFF

    private fun HeadlessClient.state(value: Int, station: HeadlessClient.() -> Int = { lumbridge() }) =
        await("station state $value", timeoutMillis = 20_000) { if (station() == value) Unit else null }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z")
        await("at $x, $z") { position?.takeIf { it.x == x && it.z == z } }
    }

    private fun HeadlessClient.canoeist(level: Int, axe: Boolean = true) {
        await("the login rebuild") { position }
        if (axe) command("item $RUNE_AXE")
        command("setlevel woodcutting $level")
        await("level $level") { stat(WOODCUTTING)?.takeIf { it.level == level } }
        if (axe) await("the axe") { inventory(BACKPACK).firstOrNull { it?.id == RUNE_AXE } }
        tele(LUM_X + 2, LUM_Z - 1)
    }

    /** Chops the Lumbridge tree, shapes [type] and floats it, checking each state and the XP. */
    private fun HeadlessClient.makeAndFloat(type: CanoeType) {
        val station = CanoeStation.LUMBRIDGE.loc
        locOp(station, LUM_X, LUM_Z, 1)
        state(Canoes.FALLING)
        state(Canoes.FALLEN)
        locOp(station, LUM_X, LUM_Z, 1)
        await("the shaping box") { if (Canoes.SHAPING in openInterfaces()) Unit else null }
        // The events come in the packet after the box's own: wait for them rather than race the reader.
        await("Make on ${type.name} clickable") { if (opEnabled(Canoes.SHAPING, type.component, 0, 1)) Unit else null }
        val xp = stat(WOODCUTTING)!!.xp
        ifButton(Canoes.SHAPING, type.component, 1, sub = 0)
        state(type.number)
        assertEquals(xp + type.xpTenths / 10, await("the canoe's XP") { stat(WOODCUTTING)?.takeIf { it.xp > xp } }.xp)
        locOp(station, LUM_X, LUM_Z, 1)
        state(type.number + 4)
        state(type.number + 10)
        locOp(station, LUM_X, LUM_Z, 1)
        await("the river map") { if (Canoes.MAP in openInterfaces()) Unit else null }
        assertEquals(CanoeStation.LUMBRIDGE.index, varp(STARTFROM_VARP) and 0x1FF)
        await("Travel to the Champions' Guild clickable") {
            if (opEnabled(Canoes.MAP, CanoeDestination.CHAMPIONS_GUILD.component, -1, 1)) Unit else null
        }
    }

    @Test
    fun `a stable dugout made at Lumbridge takes the player to the Champions' Guild and sinks`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.canoeist(99)
                client.makeAndFloat(CanoeType.STABLE_DUGOUT)
                client.ifButton(Canoes.MAP, CanoeDestination.CHAMPIONS_GUILD.component, 1)
                val to = CanoeDestination.CHAMPIONS_GUILD
                client.await("landed", timeoutMillis = 20_000) { client.position?.takeIf { it.x == to.x && it.z == to.z } }
                client.seen("[canoe.arrive_champions]")
                client.seen("[canoe.sinks]")
                client.state(Canoes.TREE)
            }
        }
    }

    @Test
    fun `a log canoe only reaches the next station`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.canoeist(99)
                client.makeAndFloat(CanoeType.LOG)
                // Barbarian Village is two stations on: the client offers no Travel there, and a forged click does nothing.
                client.ifButton(Canoes.MAP, CanoeDestination.BARBARIAN_VILLAGE.component, 1)
                client.awaitTicks(3)
                assertEquals(LUM_X + 2, client.position!!.x)
                assertTrue(Canoes.MAP in client.openInterfaces())
                assertEquals(CanoeType.LOG.number + 10, client.lumbridge())
                client.ifButton(Canoes.MAP, CanoeDestination.CHAMPIONS_GUILD.component, 1)
                val to = CanoeDestination.CHAMPIONS_GUILD
                client.await("landed", timeoutMillis = 20_000) { client.position?.takeIf { it.x == to.x && it.z == to.z } }
            }
        }
    }

    @Test
    fun `a waka's Ferox Enclave and Wilderness Pond refuse until the Wilderness is built`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.canoeist(99)
                client.makeAndFloat(CanoeType.WAKA)
                client.ifButton(Canoes.MAP, CanoeDestination.FEROX_ENCLAVE.component, 1)
                client.seen("[canoe.wilderness]")
                assertEquals(CanoeType.WAKA.number + 10, client.lumbridge())
                client.locOp(CanoeStation.LUMBRIDGE.loc, LUM_X, LUM_Z, 1)
                client.await("the river map again") { if (Canoes.MAP in client.openInterfaces()) Unit else null }
                client.ifButton(Canoes.MAP, CanoeDestination.EDGEVILLE.component, 1)
                val to = CanoeDestination.EDGEVILLE
                client.await("landed", timeoutMillis = 20_000) { client.position?.takeIf { it.x == to.x && it.z == to.z } }
                client.seen("[canoe.arrive_edgeville]")
            }
        }
    }

    @Test
    fun `the station needs level 12 Woodcutting and an axe`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.canoeist(11)
                client.locOp(CanoeStation.LUMBRIDGE.loc, LUM_X, LUM_Z, 1)
                client.seen("[canoe.level level=12]")
            }
            server.login("erin").use { client ->
                client.canoeist(12, axe = false)
                client.locOp(CanoeStation.LUMBRIDGE.loc, LUM_X, LUM_Z, 1)
                client.seen("[woodcutting.no_axe]")
                assertEquals(Canoes.TREE, client.lumbridge())
            }
        }
    }

    @Test
    fun `starting another station's tree clears the canoe left at the first`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("frank").use { client ->
                client.canoeist(99)
                client.locOp(CanoeStation.LUMBRIDGE.loc, LUM_X, LUM_Z, 1)
                client.state(Canoes.FALLEN)
                client.tele(CHAMPIONS_X + 2, CHAMPIONS_Z - 1)
                client.locOp(CanoeStation.CHAMPIONS_GUILD.loc, CHAMPIONS_X, CHAMPIONS_Z, 1)
                client.state(Canoes.TREE)
                client.state(Canoes.FALLEN) { champions() }
            }
        }
    }

    @Test
    fun `a fall cut short (a death mid-fall) leaves a fallen tree to shape`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("gina").use { client ->
                client.canoeist(99)
                client.command("varbit ${CanoeStation.LUMBRIDGE.varbit} ${Canoes.FALLING}")
                client.state(Canoes.FALLING)
                client.locOp(CanoeStation.LUMBRIDGE.loc, LUM_X, LUM_Z, 1)
                client.state(Canoes.FALLEN)
                client.await("the shaping box") { if (Canoes.SHAPING in client.openInterfaces()) Unit else null }
            }
        }
    }

    private companion object {
        const val LUM_X = 3241
        const val LUM_Z = 3235
        const val CHAMPIONS_X = 3200
        const val CHAMPIONS_Z = 3341
        const val STATIONS_VARP = 674
        const val STARTFROM_VARP = 5481
        const val RUNE_AXE = 1359
        const val BACKPACK = 93
        const val WOODCUTTING = 8
    }
}
