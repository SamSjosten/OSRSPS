package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.World
import osrsps.game.bank.BankScreen
import osrsps.game.content.ShantayPass
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Al Kharid step 8 through the headless client: the Shantay Pass gate without a pass, the bank chest, and the outlaw
 * jailed in Shantay's cell. The rl-client script test-runner/examples/al-kharid-shantay-pass.json does the same in the
 * real client.
 */
class HeadlessShantayPassTest {
    private val types = LocTypes(
        listOf(
            LocType(ShantayPass.GATE, "Shantay pass", ops = listOf("Go-through", "Look-at", null, null, null), collision = LocCollisionType(width = 3)),
            LocType(ShantayPass.BANK_CHEST, "Bank chest", ops = listOf("Use", "Collect", null, null, null), collision = LocCollisionType()),
        ).associateBy { it.id },
    )

    private val shantay = NpcType(ShantayPass.SHANTAY, "Shantay", ops = listOf("Talk-to", null, "Trade", "Buy-pass", null), internalName = "shantay")
    private val gateGuard = NpcType(ShantayPass.GATE_GUARD, "Shantay Guard", ops = listOf("Talk-to", null, "Pass", null, null), internalName = "shantay_guard_still")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3304 shr 6, 3120 shr 6) }, locTypes = types,
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(ShantayPass.GATE, LocShape.CENTREPIECE_STRAIGHT, 0, 3302, 3116, 0))
                add(ZoneLoc(ShantayPass.BANK_CHEST, LocShape.CENTREPIECE_STRAIGHT, 3, 3309, 3120, 0))
            },
            npcTypes = NpcTypes.of(shantay, gateGuard),
            npcSpawns = listOf(NpcSpawn(shantay, 3304, 3123, source = "test"), NpcSpawn(gateGuard, 3303, 3118, source = "test")),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    @Test
    fun `without a pass the gate guard sends the player to Shantay`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3303, 3119)
                client.locOp(ShantayPass.GATE, 3302, 3116, ShantayPass.GO_THROUGH)
                val page = client.awaitDialogue("the guard") { it is DialogueView.Chat && it.text == "[shantay_guard.need_pass]" } as DialogueView.Chat
                assertEquals("Shantay Guard", page.name)
                assertEquals(true, client.position!!.z > 3116)
            }
        }
    }

    @Test
    fun `the bank chest's Use opens the bank`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3308, 3120)
                client.locOp(ShantayPass.BANK_CHEST, 3309, 3120, ShantayPass.USE)
                client.await("the bank") { client.openInterfaces().takeIf { BankScreen.MAIN in it } }
            }
        }
    }

    @Test
    fun `the outlaw ends up in Shantay's cell`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3304, 3122)
                val npc = client.await("Shantay in view") { client.npcs().firstOrNull { it.id == ShantayPass.SHANTAY } }
                client.npcOp(npc.index, ShantayPass.TALK_TO)
                for (key in listOf("shantay.hello", "shantay.see_you")) {
                    client.awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
                    client.continueDialogue()
                }
                client.awaitDialogue("the menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                for (key in listOf("shantay.what_is", "shantay.this_is", "shantay.my_men")) {
                    client.awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
                    client.continueDialogue()
                }
                client.awaitDialogue("the second menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                for (key in listOf("shantay.outlaw", "shantay.very_funny", "shantay.arrest")) {
                    client.awaitDialogue(key) { it is DialogueView.Chat && it.text == "[$key]" }
                    client.continueDialogue()
                }
                client.await("the cell") { client.position?.takeIf { it == Coord(ShantayPass.CELL_INSIDE_X, ShantayPass.CELL_INSIDE_Z, 0) } }
            }
        }
    }
}
