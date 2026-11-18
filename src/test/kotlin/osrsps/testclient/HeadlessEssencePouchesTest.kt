package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.skill.EssencePouches
import osrsps.game.skill.Mining
import osrsps.map.CollisionMap

/**
 * Runecraft step RC4 through the headless client: filling, checking and emptying essence pouches, the level, one
 * essence type a pouch, and a medium pouch decaying and falling apart.
 */
class HeadlessEssencePouchesTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(EssencePouches.COLOSSAL_DEGRADED + 1)
        objs[Mining.RUNE_ESSENCE] = ObjType(Mining.RUNE_ESSENCE, "Rune essence")
        objs[Mining.PURE_ESSENCE] = ObjType(Mining.PURE_ESSENCE, "Pure essence")
        val ops = listOf("Fill", "Check", "Empty", null, "Drop")
        for (pouch in EssencePouches.POUCHES) {
            for (obj in listOfNotNull(pouch.obj, pouch.degraded)) objs[obj] = ObjType(obj, "${pouch.key} pouch", iops = ops)
        }
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), random = GameRandom(5),
            varTypes = VarTypes(4_000, VARBITS),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.ready(level: Int) {
        await("the login rebuild") { position }
        command("setlevel runecraft $level")
        await("level $level") { stat(RUNECRAFT)?.takeIf { it.level == level } }
    }

    private fun HeadlessClient.give(id: Int, count: Int) {
        val before = count(id)
        command("item $id $count")
        await("$count of $id") { if (count(id) >= before + count) Unit else null }
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    /** iop [iop] (1 Fill, 2 Check, 3 Empty) on the first [obj] in the backpack: button ops are iop + 1. */
    private fun HeadlessClient.op(obj: Int, iop: Int) =
        ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, iop + 1, inventory(BACKPACK).indexOfFirst { it?.id == obj }, obj)

    private fun HeadlessClient.seen(text: String, n: Int = 1) = await("$n of $text") { messages().filter { it.text == text }.takeIf { it.size >= n } }

    @Test
    fun `a small pouch fills with three essence, checks, refuses the other essence and empties back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.ready(1)
                client.give(EssencePouches.SMALL, 1)
                client.op(EssencePouches.SMALL, 2)
                client.seen("[runecraft_pouch.check_empty]")
                client.op(EssencePouches.SMALL, 1)
                client.seen("[runecraft_pouch.no_essence]")
                client.give(Mining.PURE_ESSENCE, 5)
                client.op(EssencePouches.SMALL, 1)
                client.await("3 essence in") { if (client.count(Mining.PURE_ESSENCE) == 2) Unit else null }
                client.op(EssencePouches.SMALL, 2)
                client.seen("[runecraft_pouch.check count=3 essence=Pure essence]")
                client.op(EssencePouches.SMALL, 1)
                client.seen("[runecraft_pouch.full]")
                client.op(EssencePouches.SMALL, 3)
                client.await("3 essence out") { if (client.count(Mining.PURE_ESSENCE) == 5) Unit else null }
                client.command("empty")
                client.await("an empty backpack") { if (client.inventory(BACKPACK).all { it == null }) Unit else null }
                client.give(EssencePouches.SMALL, 1)
                client.give(Mining.PURE_ESSENCE, 1)
                client.op(EssencePouches.SMALL, 1)
                client.await("1 essence in") { if (client.count(Mining.PURE_ESSENCE) == 0) Unit else null }
                client.give(Mining.RUNE_ESSENCE, 2)
                client.op(EssencePouches.SMALL, 1)
                client.seen("[runecraft_pouch.wrong_essence]")
                assertEquals(2, client.count(Mining.RUNE_ESSENCE))
            }
        }
    }

    @Test
    fun `the medium pouch needs 25 unboosted, decays at 400 and falls apart at 800`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.ready(24)
                val player = server.world.playerNamed("bob")!!
                client.give(EssencePouches.MEDIUM, 1)
                client.give(Mining.RUNE_ESSENCE, 10)
                client.command("boost runecraft 25")
                client.await("boosted to 25") { client.stat(RUNECRAFT)?.takeIf { it.level == 25 } }
                client.op(EssencePouches.MEDIUM, 1)
                client.seen("[runecraft_pouch.level level=25]")
                client.command("setlevel runecraft 25")
                client.await("level 25") { client.stat(RUNECRAFT)?.takeIf { it.level == 25 } }
                player.vars[MEDIUM_DECAY] = 399
                client.op(EssencePouches.MEDIUM, 1)
                client.seen("[runecraft_pouch.decayed]")
                client.await("the degraded pouch") { if (client.count(EssencePouches.MEDIUM_DEGRADED) == 1) Unit else null }
                assertEquals(0, client.count(EssencePouches.MEDIUM))
                assertEquals(3, player.vars.getVarbit(MEDIUM_COUNT), "filled only to the degraded capacity")
                client.op(EssencePouches.MEDIUM_DEGRADED, 3)
                client.await("emptied") { if (client.count(Mining.RUNE_ESSENCE) == 10) Unit else null }
                player.vars[MEDIUM_DECAY] = 799
                client.op(EssencePouches.MEDIUM_DEGRADED, 1)
                client.seen("[runecraft_pouch.destroyed]")
                client.await("the pouch gone") { if (client.count(EssencePouches.MEDIUM_DEGRADED) == 0) Unit else null }
                client.await("its essence back") { if (client.count(Mining.RUNE_ESSENCE) == 10) Unit else null }
                assertEquals(0, player.vars[MEDIUM_DECAY])
                assertEquals(0, player.vars.getVarbit(MEDIUM_COUNT))
            }
        }
    }

    @Test
    fun `a dropped pouch loses its essence, so picking it up finds it empty`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.ready(1)
                val player = server.world.playerNamed("carol")!!
                client.give(EssencePouches.SMALL, 1)
                client.give(Mining.PURE_ESSENCE, 3)
                client.op(EssencePouches.SMALL, 1)
                client.await("3 essence in") { if (client.count(Mining.PURE_ESSENCE) == 0) Unit else null }
                assertEquals(3, player.vars.getVarbit(SMALL_COUNT))
                // Drop is iop5, button op 7 (InventoryTab.click).
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 7, client.inventory(BACKPACK).indexOfFirst { it?.id == EssencePouches.SMALL }, EssencePouches.SMALL)
                client.await("the pouch dropped") { if (client.count(EssencePouches.SMALL) == 0) Unit else null }
                client.seen("[runecraft_pouch.dropped]")
                assertEquals(0, player.vars.getVarbit(SMALL_COUNT))
                client.give(EssencePouches.SMALL, 1)
                client.op(EssencePouches.SMALL, 2)
                client.seen("[runecraft_pouch.check_empty]")
            }
        }
    }

    @Test
    fun `dropping a spare pouch keeps the essence in the one still held`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.ready(1)
                val player = server.world.playerNamed("dave")!!
                client.give(EssencePouches.SMALL, 2)
                client.give(Mining.PURE_ESSENCE, 3)
                client.op(EssencePouches.SMALL, 1)
                client.await("3 essence in") { if (client.count(Mining.PURE_ESSENCE) == 0) Unit else null }
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, 7, client.inventory(BACKPACK).indexOfFirst { it?.id == EssencePouches.SMALL }, EssencePouches.SMALL)
                client.await("one pouch dropped") { if (client.count(EssencePouches.SMALL) == 1) Unit else null }
                assertEquals(3, player.vars.getVarbit(SMALL_COUNT))
            }
        }
    }

    private companion object {
        const val SMALL_COUNT = 603
        const val BACKPACK = 93
        const val RUNECRAFT = 20
        const val MEDIUM_COUNT = 604
        const val MEDIUM_DECAY = 488

        /** The pouches' varbits as the 242 cache has them. */
        val VARBITS = mapOf(
            603 to VarbitType(486, 0, 2), 604 to VarbitType(486, 3, 8), 605 to VarbitType(486, 9, 17), 606 to VarbitType(486, 18, 29),
            2088 to VarbitType(720, 0, 2), 2089 to VarbitType(720, 3, 5), 2090 to VarbitType(720, 6, 8), 2091 to VarbitType(720, 9, 11),
            13682 to VarbitType(3393, 0, 5), 13683 to VarbitType(3394, 0, 9), 13684 to VarbitType(720, 12, 14),
        )
    }
}
