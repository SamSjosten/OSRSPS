package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.VarTypes
import osrsps.game.World
import osrsps.game.content.Kudos
import osrsps.game.content.MUSEUM_VARBITS
import osrsps.game.content.MuseumDisplays
import osrsps.game.content.MuseumTimeline
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Misthalin fixes 13 through the headless client: Study on a museum display case opens its interface with its number
 * and words, which follow what the case shows: a fixed case, the staff of Armadyl's empty, Temple of Ikov and Curse of
 * the Empty Lord texts, a Dig Site find's case before and after the find, and the terracotta case left empty after The
 * Golem. The rl-client script test-runner/examples/misthalin-fixes-13.json does the same on the live cache.
 */
class HeadlessMuseumDisplaysTest {
    private fun case(id: Int) = LocType(id, "Display case", ops = listOf("Study", null, null, null, null), collision = LocCollisionType(width = 1, length = 1))

    private val placed = mapOf(MAP to (3220 to 3222), STAFF to (3222 to 3222), OLD_COIN to (3224 to 3222), TERRACOTTA to (3226 to 3222))

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) },
            locTypes = LocTypes(placed.keys.associateWith { case(it) }),
            staticLocs = StaticLocs().apply {
                for ((id, at) in placed) add(ZoneLoc(id, LocShape.CENTREPIECE_STRAIGHT, 0, at.first, at.second, 0))
            },
            varTypes = VarTypes(6_000, MUSEUM_VARBITS),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.study(id: Int, interfaceId: Int, number: Int, key: String) {
        val (x, z) = placed.getValue(id)
        locOp(id, x, z, MuseumDisplays.STUDY)
        val textComponent = if (interfaceId == MuseumDisplays.DIGSITE) MuseumDisplays.DIGSITE_TEXT else MuseumDisplays.TIMELINE_TEXT
        val numberComponent = if (interfaceId == MuseumDisplays.DIGSITE) MuseumDisplays.DIGSITE_NUMBER else MuseumDisplays.TIMELINE_NUMBER
        await("display $number saying $key") {
            if (interfaceId in openInterfaces() && text(interfaceId, textComponent) == "[museum_display.$key]") Unit else null
        }
        assertEquals(number.toString(), text(interfaceId, numberComponent))
        ifButton(interfaceId, MuseumDisplays.CLOSE, 1)
        await("display $number closed") { if (interfaceId !in openInterfaces()) Unit else null }
    }

    @Test
    fun `a case's words follow what it shows`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.study(MAP, MuseumDisplays.TIMELINE, 47, "case_47")

                client.study(STAFF, MuseumDisplays.TIMELINE, 28, "no_information")
                client.setVarbit(MuseumTimeline.VARBIT_STAFF_OF_ARMADYL, 1)
                client.study(STAFF, MuseumDisplays.TIMELINE, 28, "case_28")
                client.setVarbit(MuseumTimeline.VARBIT_STAFF_OF_ARMADYL, 2)
                client.study(STAFF, MuseumDisplays.TIMELINE, 28, "case_28_after")

                client.study(OLD_COIN, MuseumDisplays.DIGSITE, 45, "no_information")
                client.setVarbit(Kudos.VARBIT_OLD_COIN, 1)
                client.study(OLD_COIN, MuseumDisplays.DIGSITE, 45, "case_45")
            }
        }
    }

    @Test
    fun `after The Golem the terracotta case is empty, and the Dig Site's tablet and talisman are in theirs`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                assertEquals(1, client.varbit(MuseumDisplays.VARBIT_GOLEM_STATUETTE))
                assertTrue(client.varbit(MuseumDisplays.VARBIT_TABLET) == 1 && client.varbit(MuseumDisplays.VARBIT_TALISMAN) == 1)
                client.study(TERRACOTTA, MuseumDisplays.TIMELINE, 30, "case_30_after")
            }
        }
    }

    private companion object {
        const val MAP = 24661
        const val STAFF = 24623
        const val OLD_COIN = 15484
        const val TERRACOTTA = 24626
    }
}
