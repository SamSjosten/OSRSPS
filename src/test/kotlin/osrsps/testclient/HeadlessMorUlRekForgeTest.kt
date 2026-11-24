package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.dialogue.Chatbox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.Cooking
import osrsps.game.skill.Smithing
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Karamja step K2.1 through the headless client: Mor Ul Rek's lava forge smelts as a furnace does (Smelt, its op2, opens
 * the Make-X box), and food used on a sulphur vent, which has no options, cooks as on a fire.
 * test-runner/examples/mor-ul-rek.json does both in the real city.
 */
class HeadlessMorUlRekForgeTest {
    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val ids = Smithing.BRONZE.ores.map { it.first } + Smithing.BRONZE.obj + listOf(Cooking.SHRIMPS.raw, Cooking.SHRIMPS.cooked, Cooking.SHRIMPS.burnt)
        val objs = arrayOfNulls<ObjType>(ids.max() + 1)
        for (id in ids) objs[id] = ObjType(id, "Obj $id")
        fun loc(id: Int, name: String, ops: List<String?>) = LocType(id, name, ops, LocCollisionType(width = 1, length = 1))
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), random = GameRandom(7),
            locTypes = LocTypes(
                listOf(
                    loc(Smithing.LAVA_FORGE, "Lava forge", listOf(null, "Smelt", null, null, null)),
                    loc(Cooking.SULPHUR_VENT, "Sulphur vent", listOf(null, null, null, null, null)),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(Smithing.LAVA_FORGE, LocShape.CENTREPIECE_STRAIGHT, 0, FORGE_X, FORGE_Z, 0))
                add(ZoneLoc(Cooking.SULPHUR_VENT, LocShape.CENTREPIECE_STRAIGHT, 0, VENT_X, VENT_Z, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = count(id)
        command("item $id $count")
        await("$count of $id") { if (count(id) == before + count) Unit else null }
    }

    @Test
    fun `the lava forge smelts a bronze bar`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                for ((ore, _) in Smithing.BRONZE.ores) client.give(ore)
                client.locOp(Smithing.LAVA_FORGE, FORGE_X, FORGE_Z, 2)
                client.await("the Make-X box") { client.clientScripts().firstOrNull { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT } }
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM + Smithing.BARS.indexOf(Smithing.BRONZE), 1)
                client.await("the bar", timeoutMillis = 20_000) { if (client.count(Smithing.BRONZE.obj) == 1) Unit else null }
                assertEquals(0, Smithing.BRONZE.ores.sumOf { client.count(it.first) })
            }
        }
    }

    @Test
    fun `a raw shrimp used on a sulphur vent cooks`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel cooking 99")
                client.await("level 99") { client.stat(COOKING)?.takeIf { it.level == 99 } }
                client.give(Cooking.SHRIMPS.raw)
                val slot = client.inventory(BACKPACK).indexOfFirst { it?.id == Cooking.SHRIMPS.raw }
                client.locTarget(Cooking.SULPHUR_VENT, VENT_X, VENT_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slot, Cooking.SHRIMPS.raw)
                client.await("the cooked message") { client.messages().firstOrNull { it.text == "[cooking.cooked_shrimps]" } }
                client.await("the shrimps") { if (client.count(Cooking.SHRIMPS.cooked) == 1) Unit else null }
                assertEquals(0, client.count(Cooking.SHRIMPS.raw))
            }
        }
    }

    private companion object {
        const val FORGE_X = 3225
        const val FORGE_Z = 3218
        const val VENT_X = 3219
        const val VENT_Z = 3218
        const val BACKPACK = 93
        const val COOKING = 7
    }
}
