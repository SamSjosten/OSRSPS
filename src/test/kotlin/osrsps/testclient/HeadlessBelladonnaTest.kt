package osrsps.testclient

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Draynor Manor's belladonna patch through the headless client: raked, planted at level 63, grown through its four
 * 80-minute stages with `::farmtick`, then picked: bare-handed for a hit of 2 and nothing, in leather gloves for 6 or
 * more nightshade and both XPs, and the patch is bare again. The patch is read as the client draws it, `farming_transmit_a`
 * (bits 0 to 7 of varp 529). The rl-client script test-runner/examples/misthalin-fixes-8.json plants it on the live cache.
 */
class HeadlessBelladonnaTest {
    private val clock = AtomicLong(1_794_614_400_000L)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(NIGHTSHADE + 1)
        for ((id, name) in mapOf(RAKE to "Rake", DIBBER to "Seed dibber", WEEDS to "Weeds")) objs[id] = ObjType(id, name)
        objs[SEED] = ObjType(SEED, "Belladonna seed", stackable = true)
        objs[NIGHTSHADE] = ObjType(NIGHTSHADE, "Nightshade", stackable = true)
        objs[GLOVES] = ObjType(GLOVES, "Leather gloves", iops = listOf(null, "Wear", null, null, "Drop"), wearpos1 = WearPos.HANDS)
        World(
            crcs, CollisionMap().apply { markLoaded(X shr 6, Z shr 6) }, objTypes = ObjTypes(objs),
            varTypes = VarTypes(6_000, mapOf(TRANSMIT_A to VarbitType(529, 0, 7)), 21_000),
            random = GameRandom(3),
            locTypes = LocTypes(mapOf(PATCH to LocType(PATCH, "null", List(5) { null }, LocCollisionType(width = 2, length = 2), multiloc = true))),
            staticLocs = StaticLocs().apply { add(ZoneLoc(PATCH, LocShape.CENTREPIECE_STRAIGHT, 0, X, Z, 0)) },
            farmingClock = clock::get, farmingDiseases = false, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.patch() = varp(529) and 0xFF

    private fun HeadlessClient.awaitPatch(value: Int) = await("the patch at $value (it is ${patch()})") { if (patch() == value) Unit else null }

    private fun HeadlessClient.give(item: Int) {
        command("item $item 1")
        await("item $item") { if (count(item) > 0) Unit else null }
    }

    @Test
    fun `belladonna grows in 320 minutes and is picked only with gloves on`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele ${X - 1} $Z")
                client.await("by the patch") { client.position?.takeIf { it.x == X - 1 && it.z == Z } }
                client.command("setlevel farming 63")
                client.await("Farming 63") { client.stat(FARMING)?.takeIf { it.level == 63 } }
                client.command("setlevel hitpoints 10")
                client.await("10 hitpoints") { client.stat(HITPOINTS)?.takeIf { it.level == 10 } }
                val start = client.stat(FARMING)!!.xp
                client.give(RAKE)
                client.give(DIBBER)
                client.locOp(PATCH, X, Z, 1)
                client.awaitPatch(3)
                client.give(SEED)
                val slot = client.inventory(BACKPACK).indexOfFirst { it?.id == SEED }
                client.locTarget(PATCH, X, Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slot, SEED)
                client.awaitPatch(4)
                // Any 64 farming ticks hold exactly four of its 80-minute ticks.
                client.command("farmtick 48")
                client.awaitPatch(7)
                client.command("farmtick 16")
                client.awaitPatch(8)

                client.locOp(PATCH, X, Z, 1)
                assertEquals(8, client.await("the hit") { client.stat(HITPOINTS)?.takeIf { it.level < 10 } }.level)
                assertEquals(8, client.patch())
                assertEquals(0, client.count(NIGHTSHADE))

                client.give(GLOVES)
                client.ifButton(INVENTORY, INVENTORY_ITEMS, WEAR_BUTTON, client.inventory(BACKPACK).indexOfFirst { it?.id == GLOVES }, GLOVES)
                client.await("the gloves on") { client.inventory(WORN).firstOrNull { it?.id == GLOVES } }
                client.locOp(PATCH, X, Z, 1)
                client.await("[farming.picked produce=nightshade]") { client.messages().firstOrNull { it.text == "[farming.picked produce=nightshade]" } }
                client.awaitPatch(3)
                val picked = client.count(NIGHTSHADE)
                // 6, and 0 to 62 / 8 more at level 63.
                assertTrue(picked in 6..13, "6 to 13 nightshade at 63, not $picked")
                // 91 and 512 XP at once, on top of the raking's 12.
                client.await("the XP (${client.stat(FARMING)!!.xp - start} XP)") { if (client.stat(FARMING)!!.xp == start + 12 + 603) Unit else null }
            }
        }
    }

    @Test
    fun `a full pack still takes the pick onto a nightshade stack it holds`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele ${X - 1} $Z")
                client.await("by the patch") { client.position?.takeIf { it.x == X - 1 && it.z == Z } }
                client.command("setlevel farming 63")
                client.await("Farming 63") { client.stat(FARMING)?.takeIf { it.level == 63 } }
                client.give(RAKE)
                client.give(DIBBER)
                client.locOp(PATCH, X, Z, 1)
                client.awaitPatch(3)
                client.give(SEED)
                val slot = client.inventory(BACKPACK).indexOfFirst { it?.id == SEED }
                client.locTarget(PATCH, X, Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slot, SEED)
                client.awaitPatch(4)
                client.command("farmtick 64")
                client.awaitPatch(8)
                client.give(GLOVES)
                client.ifButton(INVENTORY, INVENTORY_ITEMS, WEAR_BUTTON, client.inventory(BACKPACK).indexOfFirst { it?.id == GLOVES }, GLOVES)
                client.await("the gloves on") { client.inventory(WORN).firstOrNull { it?.id == GLOVES } }
                client.give(NIGHTSHADE)
                val free = 28 - client.inventory(BACKPACK).count { it != null }
                client.command("item $WEEDS $free")
                client.await("a full pack") { if (client.inventory(BACKPACK).count { it != null } == 28) Unit else null }

                client.locOp(PATCH, X, Z, 1)
                client.awaitPatch(3)
                assertTrue(client.count(NIGHTSHADE) >= 7, "1 held and 6 or more picked, not ${client.count(NIGHTSHADE)}")
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val WORN = 94
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
        const val WEAR_BUTTON = 3
        const val FARMING = 19
        const val HITPOINTS = 3
        const val TRANSMIT_A = 4771
        const val PATCH = 7572
        const val X = 3086
        const val Z = 3354
        const val SEED = 5281
        const val NIGHTSHADE = 27790
        const val GLOVES = 1059
        const val RAKE = 5341
        const val DIBBER = 5343
        const val WEEDS = 6055
    }
}
