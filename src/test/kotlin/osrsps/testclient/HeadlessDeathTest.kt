package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Scenario
import osrsps.game.Scenarios
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
    private val shrimps = ObjType(315, "Shrimps", cost = 5, internalName = "shrimps")
    private val shrimping = Scenario.parse(
        "shrimping",
        """{ "position": { "x": 3222, "z": 3222 }, "inventory": [ { "item": "shrimps" }, { "item": "shrimps" }, { "item": "shrimps" }, { "item": "shrimps" }, { "item": "shrimps" } ] }""",
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes.of(coins, sword, bones, lobster, shrimps),
            npcTypes = NpcTypes.of(grave), scenarios = Scenarios(listOf(shrimping)), cycleMillis = millis,
        )
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
                client.await("[combat.dead]") { client.messages().firstOrNull { it.text == "[combat.dead]" } }
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
                    bob.await("[combat.grave_not_yours]", timeoutMillis = 10_000) { bob.messages().firstOrNull { it.text == "[combat.grave_not_yours]" } }
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
                // The close can come in a later packet than the grave's removal.
                client.await("the gravestone closed") { if (Deaths.GRAVESTONE in client.openInterfaces()) null else Unit }
                assertTrue(client.messages().none { it.text == "[combat.grave_no_space]" })
            }
        }
    }

    @Test
    fun `a grave raised in a scenario goes with the next scenario and with reset, and one from before stays`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                val world = server.world
                val alice = world.playerNamed("alice")!!
                fun die() {
                    client.commandReply("hit 99 me") { it.startsWith("Hit yourself") }
                    client.await("the respawn", timeoutMillis = 10_000) { client.position?.takeIf { it == Coord(3222, 3218) && !alice.dead } }
                }
                // Her own grave, from before any scenario: its items are hers, and a scenario leaves it.
                for (command in listOf("item 379", "item 1277", "item 995 10", "item 526 2")) client.command(command)
                client.await("the bag filled") { client.inventory(93).takeIf { it.count { obj -> obj != null } == 5 } }
                die()
                val own = assertNotNull(world.deaths.graveOf(alice))
                assertFalse(own.scenario)
                client.commandReply("scenario shrimping") { it.startsWith("Started") }
                assertTrue(world.deaths.graveOf(alice) === own)
                client.commandReply("reset") { it.startsWith("Back to") }
                assertTrue(world.deaths.graveOf(alice) === own)
                client.npcOp(own.npc.index, Deaths.LOOT)
                client.await("the gravestone open", timeoutMillis = 10_000) { if (Deaths.GRAVESTONE in client.openInterfaces()) Unit else null }
                client.ifButton(Deaths.GRAVESTONE, Deaths.FREEBUTTON, op = 1)
                client.await("her grave emptied and gone") { world.deaths.graveOf(alice)?.let { null } ?: Unit }

                // A script that died in its scenario, never getting its ::reset in: the next scenario finds no grave, so
                // its own death's isn't merged into the first's (nightly 2026-11-15, archive-raid-death-costs).
                client.commandReply("scenario shrimping") { it.startsWith("Started") }
                die()
                assertTrue(assertNotNull(world.deaths.graveOf(alice)).scenario)
                client.commandReply("scenario shrimping") { it.startsWith("Started") }
                assertNull(world.deaths.graveOf(alice))
                die()
                assertFalse(assertNotNull(world.deaths.graveOf(alice)).merged)
                client.commandReply("reset") { it.startsWith("Back to") }
                assertNull(world.deaths.graveOf(alice))
                assertTrue(client.messages().none { it.text == "[combat.grave_collapsed]" }, "gone without a word")
            }
        }
    }

    private companion object {
        const val HITPOINTS = 3
    }
}
