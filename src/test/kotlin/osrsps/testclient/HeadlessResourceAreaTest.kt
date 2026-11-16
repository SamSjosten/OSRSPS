package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.World.Companion.LOGOUT_BUTTON
import osrsps.game.World.Companion.LOGOUT_TAB
import osrsps.game.content.ResourceArea
import osrsps.game.content.ResourceAreaLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollision
import osrsps.map.LocCollisionType
import osrsps.map.LocShape
import osrsps.map.Tile

/**
 * Wilderness step W3.6a through the headless client: the Resource Area's gate refusing a player without the 7,500
 * coins, then taking them and letting the player in, and out again for nothing; Peek from both sides; Mandrith's talk;
 * Piles noting iron ore at 50 coins each, as many as the coins and the banknote stack allow, and refusing logs; nobody
 * walking in behind a payer; and a logout inside coming back
 * outside the gate. The gate, Mandrith and Piles stand on open ground at their 242 tiles. The rl-client script
 * test-runner/examples/resource-area.json does the same on the real map.
 */
class HeadlessResourceAreaTest {
    private val mandrith = NpcType(ResourceArea.MANDRITH, "Mandrith", ops = listOf("Talk-to", null, null, null, null), internalName = "wilderness_resource_guardian")
    private val piles = NpcType(ResourceArea.PILES, "Piles", ops = listOf("Talk-to", null, null, null, null), internalName = "wilderness_resource_certer")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(LOGS + 2)
        objs[COINS] = ObjType(COINS, "Coins", stackable = true)
        objs[IRON] = ObjType(IRON, "Iron ore", certlink = IRON_NOTED)
        objs[IRON_NOTED] = ObjType(IRON_NOTED, "Iron ore", stackable = true, certlink = IRON, certtemplate = 799)
        objs[LOGS] = ObjType(LOGS, "Logs", certlink = LOGS + 1)
        objs[LOGS + 1] = ObjType(LOGS + 1, "Logs", stackable = true, certlink = LOGS, certtemplate = 799)
        val gate = LocType(ResourceArea.GATE, "Gate", ops = listOf("Open", "Peek", null, null, null), collision = LocCollisionType(width = 1, length = 1))
        World(
            crcs, CollisionMap().apply {
                markLoaded(3184 shr 6, 3944 shr 6)
                // Static locs add no collision here: the gate's, and the wall's either side of it, far enough that going
                // round takes longer than a test waits.
                for (x in GATE_X - 12..GATE_X + 12) LocCollision(this).addLoc(LocCollisionType(), x, GATE_Z, 0, LocShape.WALL_STRAIGHT, 1)
            }, objTypes = ObjTypes(objs),
            locTypes = LocTypes(mapOf(ResourceArea.GATE to gate)),
            staticLocs = StaticLocs().apply { add(GATE) },
            npcTypes = NpcTypes.of(mandrith, piles),
            npcSpawns = listOf(NpcSpawn(mandrith, 3183, 3945, wander = 0, source = "test"), NpcSpawn(piles, 3185, 3933, wander = 0, source = "test")),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.count(id: Int): Long = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count.toLong() }

    private fun HeadlessClient.slotOf(id: Int) =
        await("item $id in the backpack") { inventory(BACKPACK).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.sees(vararg lines: Line) {
        for (line in lines) {
            awaitDialogue(line.key) { it is DialogueView.Chat && it.text == DialogueText.NONE[line] }
            continueDialogue()
        }
    }

    private fun HeadlessClient.openGate() = locOp(ResourceArea.GATE, GATE_X, GATE_Z, ResourceArea.OPEN)

    @Test
    fun `the gate takes 7,500 coins to go in, none to come out, and Peek counts who is inside`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("rhea").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                assertEquals(54, Wilderness.level(GATE_X, GATE_Z + 1))
                client.tele(GATE_X, GATE_Z + 1)

                client.locOp(ResourceArea.GATE, GATE_X, GATE_Z, ResourceArea.PEEK)
                client.awaitDialogue("nobody inside") { it is DialogueView.Message && it.text == DialogueText.NONE[ResourceAreaLines.PEEK_NONE] }
                client.continueDialogue()

                client.command("item $COINS 7499")
                client.await("the coins") { Unit.takeIf { client.count(COINS) == 7499L } }
                client.openGate()
                client.awaitDialogue("the refusal") { it is DialogueView.Message && it.text.startsWith("[resource_area.no_fee") }
                client.continueDialogue()
                assertEquals(Coord(GATE_X, GATE_Z + 1, 0), client.position)

                client.command("item $COINS 2501")
                client.await("10,000 coins") { Unit.takeIf { client.count(COINS) == 10_000L } }
                client.openGate()
                client.awaitDialogue("the fee's menu") { it is DialogueView.Menu }
                client.chooseOption(2)
                client.awaitTicks(2)
                assertEquals(Coord(GATE_X, GATE_Z + 1, 0), client.position, "No keeps the player out")
                assertEquals(10_000L, client.count(COINS))

                client.openGate()
                client.awaitDialogue("the fee's menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.await("inside") { client.position?.takeIf { it == Coord(GATE_X, GATE_Z, 0) } }
                assertEquals(2_500L, client.count(COINS))
                assertTrue(server.world.locs.exists(GATE), "the gate stays shut behind a payer")

                client.locOp(ResourceArea.GATE, GATE_X, GATE_Z, ResourceArea.PEEK)
                client.awaitMessage("the wasteland") { it == DialogueText.NONE[ResourceAreaLines.PEEK_INSIDE] }

                client.openGate()
                client.await("outside") { client.position?.takeIf { it == Coord(GATE_X, GATE_Z + 1, 0) } }
                assertEquals(2_500L, client.count(COINS), "out for nothing")
            }
        }
    }

    @Test
    fun `nobody walks in behind a player who paid`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("ash").use { payer ->
                server.login("bly").use { follower ->
                    payer.await("the login rebuild") { payer.position }
                    follower.await("the login rebuild") { follower.position }
                    payer.tele(GATE_X, GATE_Z + 1)
                    follower.tele(GATE_X - 1, GATE_Z + 1)
                    payer.command("item $COINS 7500")
                    payer.await("the fee") { Unit.takeIf { payer.count(COINS) == 7_500L } }
                    payer.openGate()
                    payer.awaitDialogue("the fee's menu") { it is DialogueView.Menu }
                    payer.chooseOption(1)
                    // The follower clicks inside every tick while the payer crosses, and after.
                    repeat(6) {
                        follower.walk(GATE_X, GATE_Z - 1)
                        follower.awaitTicks(1)
                    }
                    assertEquals(Coord(GATE_X, GATE_Z, 0), payer.position)
                    val at = follower.position!!
                    assertTrue(at.z > GATE_Z, "the follower is still outside at $at")
                }
            }
        }
    }

    @Test
    fun `the gate works from beside it along the wall, on either side`(@TempDir dir: Path) {
        assertEquals(Tile(GATE_X, GATE_Z), ResourceArea.crossingFrom(GATE_X - 1, GATE_Z, 0))
        assertEquals(Tile(GATE_X, GATE_Z + 1), ResourceArea.crossingFrom(GATE_X + 1, GATE_Z + 1, 0))
        assertNull(ResourceArea.crossingFrom(GATE_X + 2, GATE_Z, 0))
        server(dir).use { server ->
            server.login("dax").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(GATE_X - 1, GATE_Z)
                client.openGate()
                client.await("out from beside the gate") { client.position?.takeIf { it == Coord(GATE_X, GATE_Z + 1, 0) } }

                client.tele(GATE_X + 1, GATE_Z + 1)
                client.command("item $COINS 7500")
                client.await("the fee") { Unit.takeIf { client.count(COINS) == 7_500L } }
                client.openGate()
                client.awaitDialogue("the fee's menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                client.await("in from beside the gate") { client.position?.takeIf { it == Coord(GATE_X, GATE_Z, 0) } }
                assertEquals(0L, client.count(COINS))
            }
        }
    }

    @Test
    fun `Piles notes no more than the banknote stack can hold`(@TempDir dir: Path) {
        assertEquals(1, ResourceArea.noteable(held = 3, coins = 150, notedHeld = Int.MAX_VALUE - 1L))
        assertEquals(0, ResourceArea.noteable(held = 3, coins = 150, notedHeld = Int.MAX_VALUE.toLong()))
        server(dir).use { server ->
            server.login("cyd").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                val pilesIndex = server.world.npcs.all.first { it.type.id == ResourceArea.PILES }.index
                client.tele(3185, 3932)
                client.command("item $IRON_NOTED ${Int.MAX_VALUE - 1}")
                client.command("item $IRON 3")
                client.command("item $COINS 150")
                client.await("the items") { Unit.takeIf { client.count(IRON) == 3L && client.count(COINS) == 150L && client.count(IRON_NOTED) > 0 } }
                client.npcTarget(pilesIndex, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(IRON), IRON)
                client.await("one noted") { Unit.takeIf { client.count(IRON_NOTED) == Int.MAX_VALUE.toLong() } }
                assertEquals(2L, client.count(IRON), "two kept: the stack had room for one")
                assertEquals(100L, client.count(COINS), "only one paid for")
            }
        }
    }

    @Test
    fun `Mandrith talks, and Piles notes ore at 50 coins each`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("sol").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                val mandrithIndex = server.world.npcs.all.first { it.type.id == ResourceArea.MANDRITH }.index
                val pilesIndex = server.world.npcs.all.first { it.type.id == ResourceArea.PILES }.index
                client.tele(3184, 3945)
                with(ResourceAreaLines) {
                    client.npcOp(mandrithIndex, ResourceArea.TALK_TO)
                    client.sees(WHO, NAME, COLLECT, LOOK, WHATEVER, ALL_YOURS, KILL_ME, WALLS, STOP_THEM, PRICE, RIGHT, TREASURES, ONE_DAY)

                    client.tele(3185, 3932)
                    client.npcOp(pilesIndex, ResourceArea.TALK_TO)
                    client.sees(PILES_HELLO)
                    client.awaitDialogue("a menu") { it is DialogueView.Menu }
                    client.chooseOption(1)
                    client.sees(PILES_WHO, PILES_DRAYNOR, PILES_NAMES, PILES_THANKS)

                    client.command("item $IRON 3")
                    client.command("item $COINS 120")
                    client.command("item $LOGS")
                    client.await("the items") { Unit.takeIf { client.count(LOGS) == 1L && client.count(COINS) == 120L } }
                    client.npcTarget(pilesIndex, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(LOGS), LOGS)
                    client.sees(PILES_NOT_HERE)
                    client.npcTarget(pilesIndex, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(IRON), IRON)
                    client.await("two noted") { Unit.takeIf { client.count(IRON_NOTED) == 2L } }
                    assertEquals(1L, client.count(IRON), "one left: 120 coins pay for two")
                    assertEquals(20L, client.count(COINS))
                }
            }
        }
    }

    @Test
    fun `a logout inside comes back outside the gate`(@TempDir dir: Path) {
        assertEquals(Tile(GATE_X, GATE_Z + 1), ResourceArea.loginPlace(3185, 3933, 0))
        assertNull(ResourceArea.loginPlace(GATE_X, GATE_Z + 1, 0))
        assertNull(ResourceArea.loginPlace(3185, 3933, 1))
        assertTrue(ResourceArea.inside(3175, 3925, 0) && ResourceArea.inside(3196, 3944, 0))
        server(dir).use { server ->
            server.login("tam").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(3185, 3933)
                client.ifButton(LOGOUT_TAB, LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            server.awaitNobodyOn()
            server.login("tam").use { client ->
                assertEquals(Coord(GATE_X, GATE_Z + 1, 0), client.await("the login rebuild") { client.position })
            }
        }
    }

    private fun TestServer.awaitNobodyOn() {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (world.playerCount != 0) {
            check(System.nanoTime() < deadline) { "Timed out waiting for the world to let the last player go" }
            Thread.sleep(10)
        }
    }

    private companion object {
        val GATE = ZoneLoc(ResourceArea.GATE, LocShape.WALL_STRAIGHT, 1, 3184, 3944, 0)
        const val GATE_X = 3184
        const val GATE_Z = 3944
        const val COINS = 995
        const val IRON = 440
        const val IRON_NOTED = 441
        const val LOGS = 1511
        const val BACKPACK = 93
    }
}
