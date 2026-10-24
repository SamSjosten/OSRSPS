package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.map.CollisionMap

/** Inventories, stats, varps and run energy, as the headless client reads them off the wire. */
class HeadlessStateTest {
    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val types = arrayOfNulls<ObjType>(BRONZE_AXE + 1)
        types[BRONZE_AXE] = ObjType(BRONZE_AXE, "Bronze axe")
        types[COINS] = ObjType(COINS, "Coins", stackable = true)
        World(crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(types), cycleMillis = millis)
    }

    @Test
    fun `a new account's stats and full run energy arrive at login`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("Hitpoints") { client.stat(HITPOINTS) }
                assertEquals(10, client.stat(HITPOINTS)!!.level)
                assertEquals(1_154, client.stat(HITPOINTS)!!.xp)
                assertEquals(1, client.stat(ATTACK)!!.level)
                client.await("run energy") { client.runEnergyHundredths.takeIf { it >= 0 } }
                assertEquals(10_000, client.runEnergyHundredths)
            }
        }
    }

    @Test
    fun `items from the item command land in the backpack, a big stack too`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $BRONZE_AXE")
                client.command("item $COINS 100000")
                client.await("the axe and the coins") {
                    client.inventory(BACKPACK).filterNotNull().takeIf { it.size == 2 }
                }
                assertEquals(listOf(InvObj(BRONZE_AXE, 1), InvObj(COINS, 100_000)), client.inventory(BACKPACK))
            }
        }
    }

    @Test
    fun `setlevel and varp changes reach the client`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("Attack") { client.stat(ATTACK) }
                client.command("setlevel attack 50")
                client.await("Attack 50") { client.stat(ATTACK)?.takeIf { it.level == 50 } }
                assertEquals(101_333, client.stat(ATTACK)!!.xp)

                client.command("varp 1000 5")
                client.command("varp 1001 100000")
                client.await("both varps") { if (client.varp(1000) == 5 && client.varp(1001) == 100_000) Unit else null }
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val ATTACK = 0
        const val HITPOINTS = 3
        const val BRONZE_AXE = 1351
        const val COINS = 995
    }
}
