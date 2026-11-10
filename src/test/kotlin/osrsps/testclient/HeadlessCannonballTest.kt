package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
import osrsps.game.skill.Cannonballs
import osrsps.game.skill.Smithing
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Gathering 2, step G5 through the headless client: steel bars used on a furnace with an ammo mould, then a double ammo
 * mould. The rl-client script test-runner/examples/smithing-cannonballs.json does the same at Edgeville's furnace.
 */
class HeadlessCannonballTest {
    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(Cannonballs.BALLS.maxOf { it.obj } + 1)
        for (ball in Cannonballs.BALLS) {
            objs[ball.bar] = ObjType(ball.bar, "Bar ${ball.bar}", tradeable = true)
            objs[ball.obj] = ObjType(ball.obj, "Cannonball ${ball.obj}", stackable = true, tradeable = true)
        }
        objs[STEEL_BAR] = ObjType(STEEL_BAR, "Steel bar", tradeable = true)
        objs[STEEL_CANNONBALL] = ObjType(STEEL_CANNONBALL, "Steel cannonball", stackable = true, tradeable = true)
        objs[Cannonballs.AMMO_MOULD] = ObjType(Cannonballs.AMMO_MOULD, "Ammo mould")
        objs[Cannonballs.DOUBLE_AMMO_MOULD] = ObjType(Cannonballs.DOUBLE_AMMO_MOULD, "Double ammo mould")
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), random = GameRandom(5),
            locTypes = LocTypes(
                mapOf(Smithing.FURNACE to LocType(Smithing.FURNACE, "Furnace", listOf(null, "Smelt", null, null, null), LocCollisionType(width = 1, length = 1))),
            ),
            staticLocs = StaticLocs().apply { add(ZoneLoc(Smithing.FURNACE, LocShape.CENTREPIECE_STRAIGHT, 0, FURNACE_X, FURNACE_Z, 0)) },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = count(id)
        command("item $id $count")
        await("$count of $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.box(after: Int = 0) = await("the Make-X box") {
        clientScripts().filter { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }.takeIf { it.size > after }?.last()
    }

    private fun HeadlessClient.useOnFurnace(obj: Int) =
        locTarget(Smithing.FURNACE, FURNACE_X, FURNACE_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(obj), obj)

    private fun HeadlessClient.smith(level: Int) {
        await("the login rebuild") { position }
        command("setlevel smithing $level")
        await("level $level") { stat(SMITHING)?.takeIf { it.level == level } }
    }

    @Test
    fun `a bar needs the mould, and steel needs 35`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.smith(34)
                client.give(STEEL_BAR)
                client.useOnFurnace(STEEL_BAR)
                client.seen("[game.nothing_interesting]")
                client.give(Cannonballs.AMMO_MOULD)
                client.useOnFurnace(STEEL_BAR)
                client.box()
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 1)
                client.seen("[smithing.cannonball_level level=35 item=steel cannonball]")
                assertEquals(1, client.count(STEEL_BAR))
            }
        }
    }

    @Test
    fun `the ammo mould casts each steel bar into four cannonballs, 8 then 10 ticks, for 25,6 XP a bar`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.smith(35)
                val start = client.stat(SMITHING)!!.xp
                client.give(Cannonballs.AMMO_MOULD)
                client.give(STEEL_BAR, 5)
                client.useOnFurnace(Cannonballs.AMMO_MOULD)
                val setup = client.box()
                assertTrue(STEEL_CANNONBALL in setup.args, "the box: ${setup.args}")
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 5)
                val made = client.await("five casts", timeoutMillis = 20_000) {
                    client.messages().filter { it.text == "[smithing.cannonballs amount=4 item=steel cannonball]" }.takeIf { it.size >= 5 }
                }
                assertTrue(made.zipWithNext { a, b -> b.tick - a.tick }.all { it == 10 }, "10 ticks a bar after the first: ${made.map { it.tick }}")
                client.await("20 cannonballs") { if (client.count(STEEL_CANNONBALL) == 20) Unit else null }
                assertEquals(0, client.count(STEEL_BAR))
                assertEquals(1, client.count(Cannonballs.AMMO_MOULD))
                assertEquals(start + 128, client.await("128 XP") { client.stat(SMITHING)?.takeIf { it.xp >= start + 128 } }.xp)
            }
        }
    }

    @Test
    fun `the double ammo mould casts two bars into eight, and a last odd bar into four`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.smith(35)
                val start = client.stat(SMITHING)!!.xp
                client.give(Cannonballs.DOUBLE_AMMO_MOULD)
                client.give(STEEL_BAR, 5)
                client.useOnFurnace(STEEL_BAR)
                client.box()
                client.resumePause(Chatbox.SKILLMULTI, Chatbox.SKILLMULTI_FIRST_ITEM, 3)
                client.await("two casts of eight") {
                    client.messages().filter { it.text == "[smithing.cannonballs amount=8 item=steel cannonball]" }.takeIf { it.size >= 2 }
                }
                client.seen("[smithing.cannonballs amount=4 item=steel cannonball]")
                client.await("20 cannonballs") { if (client.count(STEEL_CANNONBALL) == 20) Unit else null }
                assertEquals(0, client.count(STEEL_BAR))
                assertEquals(start + 128, client.await("128 XP") { client.stat(SMITHING)?.takeIf { it.xp >= start + 128 } }.xp)
            }
        }
    }

    private companion object {
        const val FURNACE_X = 3220
        const val FURNACE_Z = 3218
        const val STEEL_BAR = 2353
        const val STEEL_CANNONBALL = 2
        const val BACKPACK = 93
        const val SMITHING = 13
    }
}
