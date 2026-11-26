package osrsps.testclient

import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.NpcStats
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.SeqNames
import osrsps.game.World
import osrsps.game.combat.NpcCombat
import osrsps.game.content.Brutus
import osrsps.game.content.CowbellAmulet
import osrsps.game.content.CowbellLines
import osrsps.game.content.GroatsFarm
import osrsps.game.dialogue.Chatbox
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
 * Brutus step 4 through the headless client: Gillie hands a cowbell amulet to a player without one, air runes charge
 * it (as many as asked), Check and Uncharge, its teleport to the cow field for a charge (empty after the last), and
 * Ring in Brutus's field bringing him back 13 ticks on.
 */
class HeadlessCowbellAmuletTest {
    private val types = LocTypes(
        listOf(LocType(Brutus.GATE_LEFT, "Gate", ops = listOf("Release", null, null, null, null), collision = LocCollisionType(width = 1, length = 1)))
            .associateBy { it.id },
    )

    private val brutus = NpcType(
        Brutus.BOSS, "Brutus", size = 3, ops = listOf(null, "Attack", null, null, null), combatLevel = 30, internalName = "cowboss",
        stats = NpcStats(attack = 12, defence = 10, strength = 25, hitpoints = 58, ranged = 1, magic = 8),
    )
    private val gillie = NpcType(GroatsFarm.GILLIE, "Gillie Groats", ops = listOf("Talk-to", null, null, null, null))

    private val objs = ObjTypes.of(
        ObjType(CowbellAmulet.EMPTY, "Cowbell amulet", iops = listOf(null, "Wear", "Ring", "Charge", "Drop")),
        ObjType(CowbellAmulet.CHARGED, "Cowbell amulet", iops = listOf("Teleport", "Wear", "Ring", "Check", "Uncharge")),
        ObjType(CowbellAmulet.AIR_RUNE, "Air rune", stackable = true),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val map = CollisionMap().apply { for (x in (3240 shr 6)..(3271 shr 6)) for (z in (3256 shr 6)..(3303 shr 6)) markLoaded(x, z) }
        val npcTypes = NpcTypes.of(brutus, gillie)
        World(
            crcs, map, random = GameRandom(17), objTypes = objs, locTypes = types,
            staticLocs = StaticLocs().apply { add(ZoneLoc(Brutus.GATE_LEFT, LocShape.WALL_STRAIGHT, 1, 3262, 3294, 0)) },
            npcTypes = npcTypes, npcCombat = NpcCombat.load(npcTypes, SeqNames.NONE), cycleMillis = millis,
        )
    }

    private fun HeadlessClient.slotOf(id: Int) = await("item $id in the backpack") { inventory(93).indexOfFirst { it?.id == id }.takeIf { it >= 0 } }

    private fun HeadlessClient.count(id: Int) = inventory(93).filterNotNull().filter { it.id == id }.sumOf { it.count.toLong() }

    private fun HeadlessClient.op(id: Int, op: Int) = ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, InventoryTab.IOP_BY_BUTTON_OP.entries.first { it.value == op }.key, slotOf(id), id)

    private fun HeadlessClient.objbox() {
        awaitDialogue("the item box") { it is DialogueView.Other }
        resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)
    }

    @Test
    fun `Gillie hands one over, air runes charge it, Check, Uncharge, and the teleport empties it with the last charge`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele 3256 3277 0")
                client.await("in the field") { client.position?.takeIf { it == Coord(3256, 3277, 0) } }
                val alice = server.world.playerNamed("alice")!!
                val npc = server.world.spawnNpc(NpcSpawn(gillie, 3257, 3277, 0, source = "test"))!!
                client.await("Gillie seen") { client.npcs().firstOrNull { it.index == npc.index } }
                client.npcOp(npc.index, 1)
                client.awaitDialogue("I've lost it") { it is DialogueView.Chat && it.text == DialogueText.NONE[CowbellLines.LOST] }
                client.continueDialogue()
                client.awaitDialogue("here's another") { it is DialogueView.Chat && it.text == DialogueText.NONE[CowbellLines.ANOTHER] }
                client.continueDialogue()
                client.objbox()
                client.await("an empty amulet") { client.inventory(93).firstOrNull { it?.id == CowbellAmulet.EMPTY } }

                // Five air runes asked for, of seven: five charges.
                client.commandReply("item ${CowbellAmulet.AIR_RUNE} 7") { it.contains("Air rune") }
                val runes = client.slotOf(CowbellAmulet.AIR_RUNE)
                client.ifButtonT(InventoryTab.INTERFACE, InventoryTab.ITEMS, runes, CowbellAmulet.AIR_RUNE, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(CowbellAmulet.EMPTY), CowbellAmulet.EMPTY)
                client.awaitTicks(2)
                client.resumeCount(5)
                client.objbox()
                client.await("charged") { client.inventory(93).firstOrNull { it?.id == CowbellAmulet.CHARGED } }
                assertEquals(5, alice.charges[CowbellAmulet.CHARGES])
                assertEquals(2, client.count(CowbellAmulet.AIR_RUNE))
                client.op(CowbellAmulet.CHARGED, CowbellAmulet.CHECK)
                
                client.await("Check") { client.messages().lastOrNull { it.text == DialogueText.NONE[CowbellLines.CHECK, "amount" to 5, "s" to "s"] } }

                // Uncharge: the five back, the amulet empty.
                client.op(CowbellAmulet.CHARGED, CowbellAmulet.UNCHARGE)
                client.objbox()
                client.await("empty again") { client.inventory(93).firstOrNull { it?.id == CowbellAmulet.EMPTY } }
                assertEquals(7, client.count(CowbellAmulet.AIR_RUNE))
                assertEquals(0, alice.charges[CowbellAmulet.CHARGES])

                // One charge, and away to the field with it.
                client.ifButtonT(InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(CowbellAmulet.AIR_RUNE), CowbellAmulet.AIR_RUNE, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(CowbellAmulet.EMPTY), CowbellAmulet.EMPTY)
                client.awaitTicks(2)
                client.resumeCount(1)
                client.objbox()
                client.command("tele 3222 3218 0")
                client.await("at Lumbridge") { client.position?.takeIf { it == Coord(3222, 3218, 0) } }
                client.op(CowbellAmulet.CHARGED, CowbellAmulet.TELEPORT)
                val landed = client.await("in the cow field") { client.position?.takeIf { abs(it.x - 3259) <= 2 && abs(it.z - 3277) <= 2 } }
                assertTrue(landed.level == 0)
                client.await("ran out") { client.messages().lastOrNull { it.text == DialogueText.NONE[CowbellLines.RAN_OUT] } }
                assertEquals(CowbellAmulet.EMPTY, client.inventory(93).filterNotNull().first { it.id in CowbellAmulet.AMULETS }.id)

                // She has one now: Gillie talks as she always does.
                client.awaitTicks(2)
                assertTrue(CowbellAmulet.anywhere(alice))
            }
        }
    }

    @Test
    fun `charging counts the air runes again once the amount is given, so none are lost to a smaller stack`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                val carol = server.world.playerNamed("carol")!!
                client.commandReply("item ${CowbellAmulet.EMPTY}") { it.contains("Cowbell amulet") }
                client.commandReply("item ${CowbellAmulet.AIR_RUNE} 7") { it.contains("Air rune") }
                client.ifButtonT(InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(CowbellAmulet.AIR_RUNE), CowbellAmulet.AIR_RUNE, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(CowbellAmulet.EMPTY), CowbellAmulet.EMPTY)
                client.awaitTicks(2)
                // While the prompt is open the stack drops to 2 (as an autocast would use them).
                client.commandReply("empty") { it == "Inventory emptied." }
                client.commandReply("item ${CowbellAmulet.EMPTY}") { it.contains("Cowbell amulet") }
                client.commandReply("item ${CowbellAmulet.AIR_RUNE} 2") { it.contains("Air rune") }
                client.await("two air runes") { if (client.count(CowbellAmulet.AIR_RUNE) == 2L) Unit else null }
                client.resumeCount(5)
                client.objbox()
                client.await("charged") { client.inventory(93).firstOrNull { it?.id == CowbellAmulet.CHARGED } }
                assertEquals(2, carol.charges[CowbellAmulet.CHARGES])
                assertEquals(0L, client.count(CowbellAmulet.AIR_RUNE))
            }
        }
    }

    @Test
    fun `Ring in his field brings Brutus back 13 ticks on, and does nothing while he lives`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("god") { it.startsWith("You can") }
                client.commandReply("item ${CowbellAmulet.EMPTY} 1") { it.contains("Cowbell") }
                client.command("tele ${Brutus.OUTSIDE.x} ${Brutus.OUTSIDE.z} 0")
                client.await("outside the gate") { client.position?.takeIf { it == Coord(Brutus.OUTSIDE.x, Brutus.OUTSIDE.z, 0) } }
                client.locOp(Brutus.GATE_LEFT, 3262, 3294, Brutus.RELEASE)
                client.awaitDialogue("Release's menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                val bob = server.world.playerNamed("bob")!!
                val run = client.await("in a field") { server.world.brutusPens.runOf(bob) }
                val boss = run.boss!!
                client.op(CowbellAmulet.EMPTY, CowbellAmulet.RING)
                client.awaitDialogue("nothing happens") { it is DialogueView.Other }
                client.resumePause(Chatbox.OBJECTBOX, Chatbox.OBJECTBOX_UNIVERSE, 0)

                client.commandReply("kill ${boss.index}") { it.startsWith("Killed") }
                client.awaitTicks(2)
                client.op(CowbellAmulet.EMPTY, CowbellAmulet.RING)
                val rung = server.world.tick
                client.objbox()
                val back = client.await("Brutus back") {
                    server.world.npcs.all.firstOrNull { it.type.id == Brutus.BOSS && !it.dead && run.instance.contains(it.x, it.z) }?.let { server.world.tick }
                }
                assertTrue(back - rung in 12L..16L, "back about 13 ticks after the ring (${back - rung})")
            }
        }
    }
}
