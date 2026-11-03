package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.content.VarrockSewers
import osrsps.game.content.Webs
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Varrock step 15 through the headless client: the manhole opens, climbs down to the sewers 6400 below and the ladder
 * there climbs back up; the web won't give to a bare hand, and a knife cuts it apart in a few tries. The rl-client
 * script test-runner/examples/varrock-sewers.json goes down the real manhole, cuts the real web and fights a moss giant.
 */
class HeadlessVarrockSewersTest {
    private fun loc(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops.toList() + List(5 - ops.size) { null }, LocCollisionType(width = 1, length = 1))

    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(1_000)
        objs[Webs.KNIFE] = ObjType(Webs.KNIFE, "Knife")
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6); markLoaded(3222 shr 6, (3218 + 6400) shr 6) },
            objTypes = ObjTypes(objs), random = GameRandom(3),
            locTypes = LocTypes(
                listOf(
                    loc(VarrockSewers.MANHOLE, "Manhole", "Open"),
                    loc(VarrockSewers.MANHOLE_OPEN, "Manhole", "Climb-down", "Close"),
                    loc(VarrockSewers.MANHOLE_LADDER, "Ladder", "Climb-up"),
                    loc(Webs.WEB, "Web", "Slash"),
                    loc(Webs.WEB_SLASHED, "Slashed web"),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(VarrockSewers.MANHOLE, LocShape.CENTREPIECE_STRAIGHT, 0, MANHOLE_X, Z, 0))
                add(ZoneLoc(VarrockSewers.MANHOLE_LADDER, LocShape.CENTREPIECE_STRAIGHT, 0, MANHOLE_X, Z + 6400, 0))
                add(ZoneLoc(Webs.WEB, LocShape.CENTREPIECE_STRAIGHT, 0, WEB_X, Z, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.locAt(x: Int, z: Int) = locChanges().lastOrNull { it.coord.x == x && it.coord.z == z }?.id

    @Test
    fun `the manhole opens, climbs down 6400 tiles, and the ladder below climbs back up`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.locOp(VarrockSewers.MANHOLE, MANHOLE_X, Z, VarrockSewers.OPEN)
                client.await("the open manhole") { if (client.locAt(MANHOLE_X, Z) == VarrockSewers.MANHOLE_OPEN) Unit else null }
                client.locOp(VarrockSewers.MANHOLE_OPEN, MANHOLE_X, Z, VarrockSewers.CLIMB)
                val below = client.await("the sewers") { client.position?.takeIf { it.z > 9000 } }
                assertTrue(kotlin.math.abs(below.x - MANHOLE_X) <= 1 && kotlin.math.abs(below.z - (Z + 6400)) <= 1, "landed at $below")
                client.locOp(VarrockSewers.MANHOLE_LADDER, MANHOLE_X, Z + 6400, VarrockSewers.CLIMB)
                val up = client.await("back up") { client.position?.takeIf { it.z < 9000 } }
                assertTrue(kotlin.math.abs(up.x - MANHOLE_X) <= 1 && kotlin.math.abs(up.z - Z) <= 1, "landed at $up")
            }
        }
    }

    @Test
    fun `a bare hand can't cut the web, and a knife cuts it apart`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.locOp(Webs.WEB, WEB_X, Z, Webs.SLASH)
                client.await("no blade") { client.messages().firstOrNull { it.text == "[web.no_blade]" } }
                assertEquals(null, client.locAt(WEB_X, Z))
                client.command("item ${Webs.KNIFE} 1")
                var tries = 0
                while (client.locAt(WEB_X, Z) != Webs.WEB_SLASHED) {
                    assertTrue(++tries <= 20, "a 50% knife cuts within 20 tries")
                    val seen = client.messages().count { it.text == "[web.failed]" }
                    client.locOp(Webs.WEB, WEB_X, Z, Webs.SLASH)
                    client.await("the slash") {
                        if (client.locAt(WEB_X, Z) == Webs.WEB_SLASHED || client.messages().count { it.text == "[web.failed]" } > seen) Unit else null
                    }
                }
                client.await("the message") { client.messages().firstOrNull { it.text == "[web.slashed]" } }
            }
        }
    }

    @Test
    fun `a weapon's chance rises with its slash bonus, and only blades count`() {
        fun weapon(slash: Int) = ObjType(1, "Sword", wearpos1 = osrsps.game.WearPos.WEAPON, params = mapOf(osrsps.game.EquipmentBonuses.PARAMS[osrsps.game.EquipmentBonuses.SLASH_ATTACK] to slash))
        assertEquals(50, Webs.chance(ObjType(Webs.KNIFE, "Knife")))
        assertEquals(0, Webs.chance(weapon(0)))
        assertEquals(20, Webs.chance(weapon(5)))
        assertEquals(20, Webs.chance(weapon(20)))
        assertEquals(60, Webs.chance(weapon(60)))
        assertEquals(100, Webs.chance(weapon(100)))
        // A slash bonus on what isn't a weapon, such as an amulet, doesn't cut.
        assertEquals(0, Webs.chance(ObjType(2, "Amulet", wearpos1 = osrsps.game.WearPos.NECK, params = mapOf(osrsps.game.EquipmentBonuses.PARAMS[osrsps.game.EquipmentBonuses.SLASH_ATTACK] to 10))))
    }

    private companion object {
        const val MANHOLE_X = 3226
        const val WEB_X = 3220
        const val Z = 3218
    }
}
