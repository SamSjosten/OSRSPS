package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.combat.Deaths
import osrsps.game.combat.MeleeCombat
import osrsps.map.CollisionMap

/** Dying and looting the grave through the headless client: combat step 6. */
class HeadlessDeathTest {
    private val grave = NpcType(Deaths.GRAVE, "Grave", ops = listOf("Check", null, "Loot", null, null))
    private val coins = ObjType(995, "Coins", cost = 1, stackable = true)
    private val sword = ObjType(1277, "Bronze sword", cost = 26)
    private val bones = ObjType(526, "Bones", cost = 1)
    private val lobster = ObjType(379, "Lobster", cost = 150)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes.of(coins, sword, bones, lobster), npcTypes = NpcTypes.of(grave), cycleMillis = millis)
    }

    @Test
    fun `dying with a full bag keeps the three most valuable items, respawns at Lumbridge, and the grave gives the rest back`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                for (command in listOf("item 379", "item 1277", "item 995 10", "item 526 2")) client.command(command)
                client.await("the bag filled") { client.inventory(93).takeIf { it.count { obj -> obj != null } == 5 } }
                assertEquals("Teleported to 3222, 3222, 0", client.commandReply("tele 3222 3222 0") { it.startsWith("Teleported") })
                assertEquals("Hit yourself for 10: 0/10 hp, dead", client.commandReply("hit 99 me") { it.startsWith("Hit yourself") })
                // Said in the same tick as the command's reply, so look back over every message.
                client.await(MeleeCombat.DEAD) { client.messages().firstOrNull { it.text == MeleeCombat.DEAD } }
                // Dead, a walk click does nothing; the respawn then puts her at the spawn.
                client.walk(3225, 3225)
                client.await("the respawn", timeoutMillis = 10_000) { client.position?.takeIf { it == Coord(3222, 3218) } }
                // The stats update can come after the respawn's move.
                client.await("the hitpoints back") { client.stat(HITPOINTS)?.takeIf { it.level == 10 } }
                assertEquals(listOf(InvObj(lobster.id, 1), InvObj(sword.id, 1), InvObj(coins.id, 1), null, null), client.inventory(93).take(5))
                val npc = client.await("the grave") { client.npcs().firstOrNull { it.id == Deaths.GRAVE } }
                assertEquals(Coord(3222, 3222), npc.coord)

                server.login("bob").use { bob ->
                    bob.await("the grave in view") { bob.npcs().firstOrNull { it.id == Deaths.GRAVE } }
                    bob.npcOp(npc.index, Deaths.CHECK)
                    bob.await(Deaths.NOT_YOURS, timeoutMillis = 10_000) { bob.messages().firstOrNull { it.text == Deaths.NOT_YOURS } }
                }

                // Loot walks her back to it and opens the gravestone with what she lost.
                client.npcOp(npc.index, Deaths.LOOT)
                client.await("the gravestone open", timeoutMillis = 10_000) { if (Deaths.GRAVESTONE in client.openInterfaces()) Unit else null }
                assertEquals(listOf(InvObj(coins.id, 9), InvObj(bones.id, 1), InvObj(bones.id, 1)), client.inventory(Deaths.GRAVE_INV))
                client.ifButton(Deaths.GRAVESTONE, Deaths.FREEITEMS, op = 1, sub = 1, obj = bones.id)
                client.await("the bones taken") { client.inventory(93).getOrNull(3)?.takeIf { it == InvObj(bones.id, 1) } }
                assertEquals(listOf(InvObj(coins.id, 9), null, InvObj(bones.id, 1)), client.inventory(Deaths.GRAVE_INV))
                client.ifButton(Deaths.GRAVESTONE, Deaths.FREEBUTTON, op = 1)
                client.await("the grave emptied and gone") { if (client.npcs().none { it.id == Deaths.GRAVE }) Unit else null }
                assertEquals(listOf(InvObj(lobster.id, 1), InvObj(sword.id, 1), InvObj(coins.id, 10), InvObj(bones.id, 1), InvObj(bones.id, 1)), client.inventory(93).take(5))
                assertFalse(Deaths.GRAVESTONE in client.openInterfaces())
                assertTrue(client.messages().none { it.text == Deaths.NO_SPACE })
            }
        }
    }

    private companion object {
        const val HITPOINTS = 3
    }
}
