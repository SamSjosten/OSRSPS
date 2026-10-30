package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.World
import osrsps.game.bank.BankScreen
import osrsps.game.content.PortSarimJailLines
import osrsps.game.content.ShantayPass
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Al Kharid step 8 through the headless client: the Shantay Pass gate without a pass, the bank chest, the outlaw
 * jailed in Shantay's cell, and, with no coins, sent to Port Sarim's prison and picking its lock. The rl-client script
 * test-runner/examples/al-kharid-shantay-pass.json does the same in the real client.
 */
class HeadlessShantayPassTest {
    private val types = LocTypes(
        listOf(
            LocType(ShantayPass.GATE, "Shantay pass", ops = listOf("Go-through", "Look-at", null, null, null), collision = LocCollisionType(width = 3)),
            LocType(ShantayPass.BANK_CHEST, "Bank chest", ops = listOf("Use", "Collect", null, null, null), collision = LocCollisionType()),
            LocType(ShantayPass.CELL_DOOR, "Prison door", ops = listOf("Open", null, null, null, null), collision = LocCollisionType()),
            LocType(ShantayPass.PRISON_DOOR, "Door", ops = listOf("Open", "Pick-lock", null, null, null), collision = LocCollisionType()),
        ).associateBy { it.id },
    )

    private val shantay = NpcType(ShantayPass.SHANTAY, "Shantay", ops = listOf("Talk-to", null, "Trade", "Buy-pass", null), internalName = "shantay")
    private val gateGuard = NpcType(ShantayPass.GATE_GUARD, "Shantay Guard", ops = listOf("Talk-to", null, "Pass", null, null), internalName = "shantay_guard_still")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply {
                markLoaded(3304 shr 6, 3120 shr 6)
                markLoaded(ShantayPass.PRISON_X shr 6, ShantayPass.PRISON_Z shr 6)
            }, locTypes = types,
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(ShantayPass.GATE, LocShape.CENTREPIECE_STRAIGHT, 0, 3302, 3116, 0))
                add(ZoneLoc(ShantayPass.BANK_CHEST, LocShape.CENTREPIECE_STRAIGHT, 3, 3309, 3120, 0))
                // The cell door on the cell's east edge, and the Port Sarim cell door on its arrival tile's south edge.
                add(ZoneLoc(ShantayPass.CELL_DOOR, LocShape.WALL_STRAIGHT, 2, 3297, 3124, 0))
                add(ZoneLoc(ShantayPass.PRISON_DOOR, LocShape.WALL_STRAIGHT, 3, ShantayPass.PRISON_X, ShantayPass.PRISON_Z, 0))
            },
            npcTypes = NpcTypes.of(shantay, gateGuard), objTypes = ObjTypes.of(ObjType(COINS, "Coins", stackable = true)),
            npcSpawns = listOf(NpcSpawn(shantay, 3304, 3123, source = "test"), NpcSpawn(gateGuard, 3303, 3118, source = "test")),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    /** Waits for the chat or box with key [key] and clicks on past it. */
    private fun HeadlessClient.line(key: String) {
        awaitDialogue(key) { (it is DialogueView.Chat && it.text == "[$key]") || (it is DialogueView.Message && it.text == "[$key]") }
        continueDialogue()
    }

    private fun HeadlessClient.choose(option: Int) {
        awaitDialogue("a menu") { it is DialogueView.Menu }
        chooseOption(option)
    }

    /** Shantay's talk up to the arrest, which ends in his cell. */
    private fun HeadlessClient.arrested() {
        tele(3304, 3122)
        val npc = await("Shantay in view") { npcs().firstOrNull { it.id == ShantayPass.SHANTAY } }
        npcOp(npc.index, ShantayPass.TALK_TO)
        listOf("shantay.hello", "shantay.see_you").forEach { line(it) }
        choose(1)
        listOf("shantay.what_is", "shantay.this_is", "shantay.my_men").forEach { line(it) }
        choose(1)
        listOf("shantay.outlaw", "shantay.very_funny", "shantay.arrest").forEach { line(it) }
        await("the cell") { position?.takeIf { it == Coord(ShantayPass.CELL_INSIDE_X, ShantayPass.CELL_INSIDE_Z, 0) } }
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
                client.arrested()
            }
        }
    }

    @Test
    fun `the outlaw who pays steps out through the cell door, which stays shut`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("item $COINS 5")
                client.await("the coins") { client.inventory(Inventory.INV).filterNotNull().firstOrNull { it.id == COINS && it.count == 5 } }
                client.arrested()
                listOf("shantay.arrested", "shantay.pay_now").forEach { client.line(it) }
                client.choose(1)
                listOf("shantay.yes_okay", "shantay.senses", "shantay.hands_over", "shantay.keep_peace", "shantay.unlocks").forEach { client.line(it) }
                client.locOp(ShantayPass.CELL_DOOR, 3297, 3124, 1)
                client.await("out of the cell") { client.position?.takeIf { it == Coord(3298, 3124, 0) } }
                // Back in from outside, as anyone can: the door still doesn't stay open behind them.
                client.locOp(ShantayPass.CELL_DOOR, 3297, 3124, 1)
                client.await("in the cell") { client.position?.takeIf { it == Coord(3297, 3124, 0) } }
            }
        }
    }

    @Test
    fun `with no coins the jailed outlaw goes to Port Sarim and picks the lock out`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel thieving 99")
                client.await("Thieving at 99") { client.stat(THIEVING)?.takeIf { it.level == 99 } }
                client.arrested()
                client.locOp(ShantayPass.CELL_DOOR, 3297, 3124, 1)
                listOf("shantay.locked", "shantay.walks_over", "shantay.jail_pay").forEach { client.line(it) }
                client.choose(1)
                listOf("shantay.yes_okay", "shantay.senses", "shantay.no_cash", "shantay.transported", "shantay.lesson", "shantay.prison").forEach { client.line(it) }
                client.await("the Port Sarim cell") { client.position?.takeIf { it == Coord(ShantayPass.PRISON_X, ShantayPass.PRISON_Z, 0) } }

                val xp = client.stat(THIEVING)!!.xp
                client.locOp(ShantayPass.PRISON_DOOR, ShantayPass.PRISON_X, ShantayPass.PRISON_Z, 1)
                client.awaitMessage("the door locked") { it == DialogueText.NONE[PortSarimJailLines.LOCKED] }
                // At 99 the fourth try in a row always works, so at most four.
                var out = false
                repeat(4) {
                    if (out) return@repeat
                    client.locOp(ShantayPass.PRISON_DOOR, ShantayPass.PRISON_X, ShantayPass.PRISON_Z, ShantayPass.PICK_LOCK)
                    val result = client.awaitMessage("the try's outcome") { it == DialogueText.NONE[PortSarimJailLines.PICK_DONE] || it == DialogueText.NONE[PortSarimJailLines.PICK_FAIL] }
                    out = result.text == DialogueText.NONE[PortSarimJailLines.PICK_DONE]
                }
                assertEquals(true, out, "the lock picked within four tries")
                client.await("outside the cell") { client.position?.takeIf { it == Coord(ShantayPass.PRISON_X, ShantayPass.PRISON_Z - 1, 0) } }
                client.await("the Thieving XP") { client.stat(THIEVING)?.takeIf { it.xp >= xp + 4 } }
            }
        }
    }

    private companion object {
        const val THIEVING = 17
        const val COINS = 995
    }
}
