package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocShape

/** Ground objs and loc changes, as the headless client reads them from zone updates. */
class HeadlessZoneTest {
    private val spawn = Coord(World.SPAWN_X, World.SPAWN_Z, World.SPAWN_LEVEL)

    /** A closed door on the west edge of the tile three east of the spawn. */
    private val door = ZoneLoc(DOOR, LocShape.WALL_STRAIGHT, 0, spawn.x + 3, spawn.z, spawn.level)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(HIGH_ID + 1)
        objs[COINS] = ObjType(COINS, "Coins", stackable = true)
        objs[AXE] = ObjType(AXE, "Bronze axe", tradeable = true, iops = listOf(null, "Wield", null, null, "Drop"))
        objs[HIGH_ID] = ObjType(HIGH_ID, "Rosewood logs", tradeable = true)
        val locs = LocTypes(
            listOf(
                LocType(DOOR, "Door", ops = listOf("Open", null, null, null, null), internalName = "poordoor"),
                LocType(DOOR_OPEN, "Door", ops = listOf("Close", null, null, null, null), internalName = "poordooropen"),
            ).associateBy { it.id },
        )
        World(
            crcs, CollisionMap().apply { markLoaded(spawn.x shr 6, spawn.z shr 6) }, objTypes = ObjTypes(objs),
            locTypes = locs, staticLocs = StaticLocs().apply { add(door) }, cycleMillis = millis,
        )
    }

    @Test
    fun `a dropped stack shows on its tile, to a player who logs in later too, and goes when taken`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { alice ->
                alice.await("the login rebuild") { alice.position }
                alice.command("groundobj $COINS 500")
                val coins = GroundObjView(COINS, 500, spawn)
                alice.await("the coins") { alice.groundObjs().takeIf { it == listOf(coins) } }

                server.login("bob").use { bob ->
                    bob.await("the coins, sent in full at login") { bob.groundObjs().takeIf { it == listOf(coins) } }
                    alice.objOp(COINS, spawn.x, spawn.z, TAKE)
                    alice.await("the coins in the backpack") { alice.inventory(BACKPACK).firstOrNull() }
                    assertEquals(InvObj(COINS, 500), alice.inventory(BACKPACK).first())
                    for (client in listOf(alice, bob)) {
                        client.await("the coins gone") { if (client.groundObjs().isEmpty()) Unit else null }
                    }
                }
            }
        }
    }

    @Test
    fun `a drop is the dropper's alone for 100 ticks, then the other player sees it and can take it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("frank").use { frank ->
                server.login("gina").use { gina ->
                    frank.await("the login rebuild") { frank.position }
                    gina.await("the login rebuild") { gina.position }
                    frank.command("item $AXE")
                    val slot = frank.await("the axe") { frank.inventory(BACKPACK).indexOfFirst { it?.id == AXE }.takeIf { it >= 0 } }
                    val ticks = frank.ticksSeen
                    // Drop is the axe's iop5, button op 7 on its inventory slot.
                    frank.ifButton(INVENTORY, INVENTORY_ITEMS, DROP, slot, AXE)
                    val axe = GroundObjView(AXE, 1, spawn)
                    frank.await("the axe on the ground") { frank.groundObjs().takeIf { it == listOf(axe) } }
                    assertEquals(emptyList(), gina.groundObjs())
                    // 100 ticks of 100 ms in this server; most of them, then a check that she still sees nothing.
                    frank.await("90 ticks", timeoutMillis = 30_000) { if (frank.ticksSeen >= ticks + 90) Unit else null }
                    assertEquals(emptyList(), gina.groundObjs())
                    gina.await("the axe, public", timeoutMillis = 10_000) { gina.groundObjs().takeIf { it == listOf(axe) } }
                    val shown = frank.ticksSeen - ticks
                    assertTrue(shown in 100..110, "shown to the other player $shown ticks after the drop")

                    gina.objOp(AXE, spawn.x, spawn.z, TAKE)
                    gina.await("the axe in her backpack") { gina.inventory(BACKPACK).firstOrNull { it?.id == AXE } }
                    for (client in listOf(frank, gina)) {
                        client.await("the axe gone") { if (client.groundObjs().isEmpty()) Unit else null }
                    }
                }
            }
        }
    }

    @Test
    fun `an obj with an id above 32767 goes for the taker and an onlooker, its full id on the wire`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("hal").use { hal ->
                server.login("ivy").use { ivy ->
                    hal.await("the login rebuild") { hal.position }
                    hal.command("groundobj $HIGH_ID")
                    val logs = GroundObjView(HIGH_ID, 1, spawn)
                    for (client in listOf(hal, ivy)) {
                        client.await("the logs") { client.groundObjs().takeIf { it == listOf(logs) } }
                    }
                    hal.objOp(HIGH_ID, spawn.x, spawn.z, TAKE)
                    hal.await("the logs in his backpack") { hal.inventory(BACKPACK).firstOrNull { it?.id == HIGH_ID } }
                    // The headless client reads OBJ_DEL's id as 16 bits and drops only an exact match, so the logs go
                    // only if the server sent 32910's own id. Whether RuneLite keeps all 16 bits is the rl-client
                    // script high-id-take.json's question.
                    for (client in listOf(hal, ivy)) {
                        client.await("the logs gone") { if (client.groundObjs().isEmpty()) Unit else null }
                    }
                }
            }
        }
    }

    @Test
    fun `a rebuild drops the old scene and the server sends the zones still in view again`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                // 3250 is 82 tiles into the build area: no rebuild yet. 3258 is past the 16-tile margin, and the
                // coins' zone is one zone back, still in view.
                client.command("tele 3250 ${spawn.z}")
                client.await("the teleport") { client.position?.takeIf { it.x == 3250 } }
                client.command("groundobj $COINS 7")
                val coins = GroundObjView(COINS, 7, Coord(3250, spawn.z, spawn.level))
                client.await("the coins") { client.groundObjs().takeIf { it == listOf(coins) } }
                val rebuilds = client.packets().count { it.prot.name == "REBUILD_NORMAL_V2" }

                client.command("tele 3258 ${spawn.z}")
                client.await("the rebuild") { if (client.packets().count { it.prot.name == "REBUILD_NORMAL_V2" } > rebuilds) Unit else null }
                client.await("the coins, sent again") { client.groundObjs().takeIf { it == listOf(coins) } }
            }
        }
    }

    @Test
    fun `an opened door shows as removed on its tile and open on the next, to a late joiner too`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { dave ->
                dave.await("the login rebuild") { dave.position }
                dave.locOp(DOOR, door.x, door.z, OPEN)
                val opened = setOf(
                    LocChange(-1, LocShape.WALL_STRAIGHT, 0, Coord(door.x, door.z, door.level)),
                    LocChange(DOOR_OPEN, LocShape.WALL_STRAIGHT, 1, Coord(door.x - 1, door.z, door.level)),
                )
                dave.await("the door open") { dave.locChanges().toSet().takeIf { it == opened } }

                server.login("erin").use { erin ->
                    erin.await("the open door, sent in full at login") { erin.locChanges().toSet().takeIf { it == opened } }
                }
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val COINS = 995
        const val AXE = 1351
        /** rosewood_logs in the 241 cache. */
        const val HIGH_ID = 32910
        const val INVENTORY = 149
        const val INVENTORY_ITEMS = 0
        const val DROP = 7
        const val TAKE = 3
        const val OPEN = 1
        const val DOOR = 9101
        const val DOOR_OPEN = 9102
    }
}
