package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.Mining
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Gathering step 4 through the headless client: mining Lumbridge's copper, and the checks before a swing. The rl-client
 * script test-runner/examples/mining-lumbridge.json mines the swamp's copper and tin in the real client.
 */
class HeadlessMiningTest {
    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(POT + 1)
        objs[COPPER_ORE] = ObjType(COPPER_ORE, "Copper ore")
        objs[BRONZE_PICKAXE] = ObjType(BRONZE_PICKAXE, "Bronze pickaxe")
        objs[RUNE_PICKAXE] = ObjType(RUNE_PICKAXE, "Rune pickaxe")
        objs[POT] = ObjType(POT, "Pot")
        for (gem in Mining.GEMS.map { it.obj }.filter { it >= 0 }) objs[gem] = ObjType(gem, "Gem $gem")
        val mine = listOf("Mine", null, null, null, null)
        fun rock(id: Int, name: String) = LocType(id, name, mine, LocCollisionType(width = 1, length = 1))
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs),
            random = GameRandom(3),
            locTypes = LocTypes(
                listOf(rock(COPPER, "Copper rocks"), rock(MITHRIL, "Mithril rocks"), rock(Mining.ROCKS1, "Rocks"), rock(Mining.ROCKS2, "Rocks"))
                    .associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(COPPER, LocShape.CENTREPIECE_STRAIGHT, 0, 3223, 3218, 0))
                add(ZoneLoc(MITHRIL, LocShape.CENTREPIECE_STRAIGHT, 0, 3221, 3218, 0))
                add(ZoneLoc(Mining.ROCKS2, LocShape.CENTREPIECE_STRAIGHT, 0, 3222, 3219, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).count { it?.id == id }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.locAt(x: Int, z: Int) = locChanges().lastOrNull { it.coord.x == x && it.coord.z == z }?.id

    @Test
    fun `no pickaxe, then a bronze pickaxe mines one copper ore, the rock empties and comes back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.locOp(COPPER, 3223, 3218, 1)
                client.seen("[mining.no_pickaxe]")

                client.command("item $BRONZE_PICKAXE")
                client.await("the pickaxe") { if (client.count(BRONZE_PICKAXE) == 1) Unit else null }
                client.locOp(COPPER, 3223, 3218, 1)
                client.seen("[mining.swing]")
                client.await("copper ore", timeoutMillis = 30_000) { if (client.count(COPPER_ORE) == 1) Unit else null }
                client.seen("[mining.get_ore ore=copper]")
                // 17.5 XP: the stat packet carries whole XP.
                assertEquals(17, client.await("Mining XP") { client.stat(MINING)?.takeIf { it.xp > 0 } }.xp)
                client.await("the empty rock") { if (client.locAt(3223, 3218) == Mining.ROCKS1) Unit else null }
                // 4 ticks later the ore is back.
                client.await("the copper back") { if (client.locAt(3223, 3218) == COPPER) Unit else null }
                assertEquals(1, client.count(COPPER_ORE))
            }
        }
    }

    @Test
    fun `a rune pickaxe at 99 gets its ore 3 ticks after the click`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $RUNE_PICKAXE")
                client.command("setlevel mining 99")
                client.await("level 99") { client.stat(MINING)?.takeIf { it.level == 99 } }
                client.locOp(COPPER, 3223, 3218, 1)
                val clicked = client.await("the swing") { client.messages().firstOrNull { it.text == "[mining.swing]" } }
                val ore = client.await("the ore") { client.messages().firstOrNull { it.text == "[mining.get_ore ore=copper]" } }
                assertEquals(3, ore.tick - clicked.tick)
            }
        }
    }

    @Test
    fun `mithril needs level 55, an empty rock has no ore, and a full backpack stops it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $BRONZE_PICKAXE")
                client.locOp(MITHRIL, 3221, 3218, 1)
                client.seen("[mining.level level=55]")
                client.locOp(Mining.ROCKS2, 3222, 3219, 1)
                client.seen("[mining.empty]")
                client.command("item $POT 27")
                client.await("a full backpack") { if (client.inventory(BACKPACK).count { it != null } == 28) Unit else null }
                client.locOp(COPPER, 3223, 3218, 1)
                client.seen("[mining.inventory_full ore=copper]")
            }
        }
    }

    private companion object {
        const val COPPER = 10943
        const val MITHRIL = 11372
        const val COPPER_ORE = 436
        const val BRONZE_PICKAXE = 1265
        const val RUNE_PICKAXE = 1275
        const val POT = 1931
        const val BACKPACK = 93
        const val MINING = 14
    }
}
