package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Wilderness
import osrsps.game.World
import osrsps.game.content.ChaosTemple
import osrsps.game.content.ChaosTempleLines
import osrsps.game.dialogue.DialogueText
import osrsps.game.dialogue.Line
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.npc.NpcSpawn
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Wilderness step W3.4c through the headless client: bones offered on the Chaos Temple's altar for 3.5 times their XP,
 * some spared, until none are left; demonic ashes refused; Pray-at; and the Elder Chaos druid's talk and his exchange
 * of noted bones for 50 coins each, with his refusals. The altar and druid stand on open ground at their 242 tiles.
 * The rl-client script test-runner/examples/chaos-temple-altar.json does the same on the real map.
 */
class HeadlessChaosTempleTest {
    private val druid = NpcType(ChaosTemple.DRUID, "Elder Chaos druid", ops = listOf("Talk-to", null, null, null, null), internalName = "wild_chaos_uncerter")

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(VILE + 1)
        objs[BONES] = ObjType(BONES, "Bones", iops = listOf("Bury", null, null, null, "Drop"), certlink = BONES_NOTED, internalName = "bones")
        objs[BONES_NOTED] = ObjType(BONES_NOTED, "Bones", stackable = true, certlink = BONES, certtemplate = 799, internalName = "cert_bones")
        objs[LOGS] = ObjType(LOGS, "Logs", certlink = LOGS_NOTED)
        objs[LOGS_NOTED] = ObjType(LOGS_NOTED, "Logs", stackable = true, certlink = LOGS, certtemplate = 799)
        objs[COINS] = ObjType(COINS, "Coins", stackable = true)
        objs[VILE] = ObjType(VILE, "Vile ashes", iops = listOf("Scatter", null, null, null, "Drop"))
        val altar = LocType(ChaosTemple.ALTAR, "Chaos altar", ops = listOf("Pray-at", null, null, null, null), collision = LocCollisionType(width = 1, length = 1))
        World(
            crcs, CollisionMap().apply { markLoaded(ChaosTemple.ALTAR_X shr 6, ChaosTemple.ALTAR_Z shr 6) }, objTypes = ObjTypes(objs),
            random = GameRandom(7), locTypes = LocTypes(mapOf(ChaosTemple.ALTAR to altar)),
            staticLocs = StaticLocs().apply { add(ZoneLoc(ChaosTemple.ALTAR, LocShape.CENTREPIECE_STRAIGHT, 0, ChaosTemple.ALTAR_X, ChaosTemple.ALTAR_Z, 0)) },
            npcTypes = NpcTypes.of(druid), npcSpawns = listOf(NpcSpawn(druid, 2954, 3818, wander = 0, source = "test")),
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

    private fun HeadlessClient.pick(option: Int) {
        awaitDialogue("a menu") { it is DialogueView.Menu }
        chooseOption(option)
    }

    @Test
    fun `bones offered on the altar give three and a half times their XP until none are left`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("vael").use { client ->
                client.await("the login rebuild") { client.position }
                client.commandReply("keys on") { it == "Dialogue shows keys" }
                assertEquals(38, Wilderness.level(ChaosTemple.ALTAR_X, ChaosTemple.ALTAR_Z))
                // Well clear of a level-up, whose box would stop the offerings.
                client.command("setlevel prayer 50")
                client.await("level 50") { client.stat(PRAYER)?.takeIf { it.level == 50 } }
                val player = server.world.playerNamed("vael")!!
                val before = player.skills.tenths(osrsps.game.Skill.PRAYER)
                client.command("item $BONES 5")
                client.await("five bones") { Unit.takeIf { client.count(BONES) == 5L } }
                client.tele(ChaosTemple.ALTAR_X, ChaosTemple.ALTAR_Z - 1)
                client.locTarget(ChaosTemple.ALTAR, ChaosTemple.ALTAR_X, ChaosTemple.ALTAR_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(BONES), BONES)
                client.await("every bone offered", timeoutMillis = 30_000) { Unit.takeIf { client.count(BONES) == 0L } }
                val spared = client.messages().count { it.text == "[chaos_temple.spared]" }
                assertTrue(spared > 0, "seed 7 spares some")
                // Each offering, spared or not, gives 15.7 XP (4.5 times 3.5, to a tenth).
                assertEquals((5 + spared) * 157, player.skills.tenths(osrsps.game.Skill.PRAYER) - before)
                // One offering every three ticks, the first repeat included, though the player started beside the altar.
                val offers = client.animationTicks().takeLast(5 + spared)
                assertEquals(List(4 + spared) { ChaosTemple.OFFER_TICKS }, offers.zipWithNext { a, b -> b - a }, "ticks $offers")

                client.command("item $VILE")
                client.locTarget(ChaosTemple.ALTAR, ChaosTemple.ALTAR_X, ChaosTemple.ALTAR_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(VILE), VILE)
                client.awaitMessage("the wrath line") { it == "[altar.wrath]" }
                assertEquals(1L, client.count(VILE))

                client.locOp(ChaosTemple.ALTAR, ChaosTemple.ALTAR_X, ChaosTemple.ALTAR_Z, ChaosTemple.PRAY_AT)
                client.awaitMessage("prayer recharged or full") { it == "[altar.full]" || it == "[altar.recharge]" }
            }
        }
    }

    @Test
    fun `the Elder Chaos druid talks and exchanges noted bones for 50 coins each`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("wren").use { client ->
                client.await("the login rebuild") { client.position }
                client.tele(2954, 3817)
                val index = server.world.npcs.all.first { it.type.id == ChaosTemple.DRUID }.index
                with(ChaosTempleLines) {
                    client.npcOp(index, ChaosTemple.TALK_TO)
                    client.sees(WELCOME, EXCHANGE, FEE)
                    client.pick(2)
                    client.sees(NOT_IMPORTANT)
                    client.pick(1)
                    client.sees(HAND_ME)

                    client.command("item $BONES_NOTED 3")
                    client.npcTarget(index, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(BONES_NOTED), BONES_NOTED)
                    client.sees(NO_COINS)

                    client.command("item $COINS 1000")
                    client.await("the coins") { Unit.takeIf { client.count(COINS) == 1000L } }
                    client.npcTarget(index, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(BONES_NOTED), BONES_NOTED)
                    client.await("three bones") { Unit.takeIf { client.count(BONES) == 3L } }
                    assertEquals(0L, client.count(BONES_NOTED))
                    assertEquals(850L, client.count(COINS))

                    client.npcTarget(index, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(BONES), BONES)
                    client.sees(NOT_A_NOTE)
                    client.command("item $LOGS_NOTED 2")
                    client.npcTarget(index, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.slotOf(LOGS_NOTED), LOGS_NOTED)
                    client.sees(NOT_SACRIFICE)
                }
            }
        }
    }

    @Test
    fun `the druid exchanges no more than the coins and free slots allow`() {
        assertEquals(3, ChaosTemple.exchangeable(notes = 3, coins = 1000, freeSlots = 20))
        assertEquals(2, ChaosTemple.exchangeable(notes = 10, coins = 100, freeSlots = 20))
        assertEquals(4, ChaosTemple.exchangeable(notes = 10, coins = 10_000, freeSlots = 4))
        assertEquals(0, ChaosTemple.exchangeable(notes = 10, coins = 49, freeSlots = 4))
        assertEquals(157, ChaosTemple.offerXp(BONES))
        assertEquals(2520, ChaosTemple.offerXp(536), "dragon bones: 72 times 3.5")
        assertEquals(null, ChaosTemple.offerXp(VILE), "ashes can't be offered")
        // Wolf, bat and lava dragon bones, buriable since this step, offer at 3.5 times their XP too.
        assertEquals(157, ChaosTemple.offerXp(2859))
        assertEquals(185, ChaosTemple.offerXp(530))
        assertEquals(2975, ChaosTemple.offerXp(11943))
    }

    private companion object {
        const val BONES = 526
        const val BONES_NOTED = 527
        const val LOGS = 1511
        const val LOGS_NOTED = 1512
        const val COINS = 995
        const val VILE = 25769
        const val BACKPACK = 93
        const val PRAYER = 5
    }
}
