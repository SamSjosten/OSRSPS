package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import osrsps.game.GameRandom
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.Quests
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.SlayerMasters
import osrsps.game.content.SlayerRewards
import osrsps.game.npc.NpcSpawn
import osrsps.game.dialogue.DialogueText
import osrsps.game.shop.ShopLines
import osrsps.game.shop.ShopScreen
import osrsps.game.shop.ShopTypes
import osrsps.game.skill.Slayer
import osrsps.game.skill.SlayerData
import osrsps.game.skill.SlayerMaster
import osrsps.game.skill.SlayerMasterTask
import osrsps.game.skill.SlayerTask
import osrsps.game.skill.SlayerUnlock
import osrsps.map.CollisionMap

/**
 * Slayer step 4 through the headless client: the rewards screen opened from Vannaka, an unlock bought and a refundable
 * one turned off, too few points, "Extend everything", a task cancelled, and a task blocked so Vannaka no longer gives it,
 * then unblocked; and step 5's Trade on the Slayer Equipment shop and the rewards screen's Buy tab.
 */
class HeadlessSlayerRewardsTest {
    private val ops = listOf("Talk-to", null, "Assignment", "Trade", "Rewards")
    private val vannaka = NpcType(SlayerMaster.VANNAKA.npc, "Vannaka", ops = ops, internalName = "slayer_master_3")

    private val cows = SlayerTask(6219, 6, "cows", "Cows")
    private val rats = SlayerTask(6216, 3, "rats", "Rats")

    // The 242 cache's rows for these unlocks: Gargoyle Smasher (bit 0, 120), Seeing Red (bit 34, 50, refundable) and two
    // extends, Need More Darkness (bit 4, 100) and Ankou Very Much (bit 8, 100), both refundable as every extend is.
    private val data = SlayerData(
        listOf(cows, rats),
        listOf(SlayerMasterTask(SlayerMaster.VANNAKA.id, rats, 7, 40, 80), SlayerMasterTask(SlayerMaster.VANNAKA.id, cows, 7, 40, 80)),
        listOf(
            SlayerUnlock(6392, 0, 120), SlayerUnlock(6408, 34, 50, refundable = true),
            SlayerUnlock(6399, 4, 100, refundable = true, list = 1), SlayerUnlock(6400, 8, 100, refundable = true, list = 1),
            // I Hope You Mith Me (bit 16, 80): on no list, so the client never draws it.
            SlayerUnlock(6409, 16, 80, refundable = true, list = -1),
        ),
    )

    // Every master who runs the Slayer Equipment shop, so the real shops.json entry loads; only Vannaka stands here.
    private val masters = listOf(vannaka) + SlayerMasters.BUILT.filter { it != SlayerMaster.VANNAKA }.map { NpcType(it.npc, it.name, ops = ops, internalName = INTERNAL.getValue(it)) }

    // The shop's stock as its entry names it, and the Buy tab's ring, bolts and looting bag (the 242 cache's names).
    private val objs = run {
        val shop = Json.parseToJsonElement(ShopTypes::class.java.getResource(ShopTypes.RESOURCE)!!.readText()).jsonObject["shops"]!!.jsonArray
            .map { it.jsonObject }.first { it["key"]!!.jsonPrimitive.content == "slayer_equipment" }
        val stock = shop["stock"]!!.jsonArray.map { it.jsonObject }.map {
            val id = it["item"]!!.jsonPrimitive.int
            ObjType(id, it["name"]!!.jsonPrimitive.content, cost = 100, stackable = id == 4160 || id == 4161)
        }
        ObjTypes.of(
            ObjType(COINS, "Coins", stackable = true), ObjType(RING, "Slayer ring (8)"), ObjType(BOLTS, "Broad bolts", stackable = true),
            ObjType(LOOTING_BAG, "Looting bag"), ObjType(OPEN_LOOTING_BAG, "Looting bag"), ObjType(HERB_SACK, "Herb sack"),
            ObjType(RUNE_POUCH, "Rune pouch"), ObjType(DIVINE_RUNE_POUCH, "Divine rune pouch"), *stock.toTypedArray(),
        )
    }

    private val shops = ShopTypes.parse(ShopTypes::class.java.getResource(ShopTypes.RESOURCE)!!.readText(), NpcTypes.of(*masters.toTypedArray()), objs)
        .let { all -> ShopTypes(listOf(all["slayer_equipment"]!!)) }

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, random = GameRandom(5),
            // The 242 cache's Slayer varbits, Vannaka's block slots (slayer_rewards_blocked_5 and _6, the diary slot in
            // _diary_1) and slayer_master_in_focus (slayer_misc).
            varTypes = VarTypes(
                20_000,
                osrsps.game.content.DIARY_VARBITS + mapOf(
                    Slayer.VARBIT_MASTER to VarbitType(661, 2, 5), Slayer.VARBIT_POINTS to VarbitType(661, 6, 22),
                    Slayer.VARBIT_STREAK to VarbitType(1077, 0, 13), Slayer.VARBIT_COMBAT_CHECK_OFF to VarbitType(1077, 29, 29),
                    Slayer.VARBIT_WILDERNESS_STREAK to VarbitType(1565, 0, 13),
                    17824 to VarbitType(4833, 0, 7), 17825 to VarbitType(4833, 8, 15), 17826 to VarbitType(4833, 16, 23),
                    17827 to VarbitType(4833, 24, 31), 17828 to VarbitType(4834, 0, 7), 17829 to VarbitType(4834, 8, 15),
                    17862 to VarbitType(4842, 16, 23), SlayerRewards.VARBIT_MASTER_IN_FOCUS to VarbitType(4844, 0, 5),
                ),
            ),
            npcTypes = NpcTypes.of(*masters.toTypedArray()), objTypes = objs, shopTypes = shops,
            npcSpawns = listOf(NpcSpawn(vannaka, 3224, 3218, wander = 0, source = "test")),
            slayerData = data, cycleMillis = millis,
        )
    }

    private fun HeadlessClient.open(): Int {
        val npc = await("Vannaka in view") { npcs().firstOrNull { it.id == vannaka.id } }
        npcOp(npc.index, SlayerRewards.REWARDS)
        await("the rewards screen") { if (SlayerRewards.INTERFACE in openInterfaces()) Unit else null }
        await("the confirm box's op") { if (opEnabled(SlayerRewards.INTERFACE, SlayerRewards.CONFIRM_BUTTON, SlayerRewards.BLOCK, 1)) Unit else null }
        return npc.index
    }

    private fun HeadlessClient.confirm(child: Int) = ifButton(SlayerRewards.INTERFACE, SlayerRewards.CONFIRM_BUTTON, 1, child)

    private fun HeadlessClient.points() = commandReply("slayer") { it.contains("points") }.substringAfterLast(", ").substringBefore(" points").toInt()

    private fun HeadlessClient.awaitVarp(varp: Int, what: String, test: (Int) -> Boolean) = await(what) { if (test(varp(varp))) Unit else null }

    @Test
    fun `unlocks are bought with points, a refundable one turned off, and Extend everything buys the rest`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.commandReply("slayer points 170") { it.startsWith("Streak") }
                client.open()
                assertEquals(SlayerMaster.VANNAKA.id, client.varp(4844) and 0x3F)

                client.confirm(0)
                client.awaitVarp(Slayer.UNLOCK_VARPS[0], "Gargoyle Smasher bought") { it and 1 != 0 }
                assertEquals(50, client.points())

                client.confirm(34)
                client.awaitVarp(Slayer.UNLOCK_VARPS[1], "Seeing Red bought") { it and (1 shl 2) != 0 }
                assertEquals(0, client.points())
                client.confirm(34)
                client.awaitVarp(Slayer.UNLOCK_VARPS[1], "Seeing Red off") { it and (1 shl 2) == 0 }
                assertEquals(0, client.points())

                // A row the screen doesn't draw buys nothing, whatever a client sends (exploit review round 35, R35B-2).
                client.commandReply("slayer points 80") { it.startsWith("Streak") }
                client.confirm(16)
                client.awaitTicks(3)
                assertEquals(0, client.varp(Slayer.UNLOCK_VARPS[0]) and (1 shl 16))
                assertEquals(80, client.points())
                client.commandReply("slayer points 0") { it.startsWith("Streak") }

                client.confirm(SlayerRewards.EXTEND_ALL)
                client.awaitMessage("[slayer_rewards.not_enough_points cost=200]") { it == "[slayer_rewards.not_enough_points cost=200]" }
                client.commandReply("slayer points 200") { it.startsWith("Streak") }
                client.confirm(SlayerRewards.EXTEND_ALL)
                client.awaitVarp(Slayer.UNLOCK_VARPS[0], "both extends bought") { it and EXTENDS == EXTENDS }
                assertEquals(0, client.points())
            }
        }
    }

    @Test
    fun `a task is cancelled for 30 points, and a blocked task is no longer given until unblocked`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.commandReply("varp ${Quests.VARP_QUEST_POINTS} 100") { true }
                client.commandReply("setlevel all 40") { it.startsWith("Every skill") }
                client.commandReply("slayer points 200") { it.startsWith("Streak") }
                client.commandReply("slayer task rats 50 vannaka") { it.startsWith("Task") }
                val npc = client.open()
                assertEquals(3, client.varp(SlayerRewards.VARP_IF_TASK))
                assertEquals(50, client.varp(SlayerRewards.VARP_IF_COUNT))

                client.confirm(SlayerRewards.CANCEL)
                client.awaitVarp(Slayer.VARP_COUNT, "the task cancelled") { it == 0 }
                assertEquals(170, client.points())
                client.confirm(SlayerRewards.CANCEL)
                client.awaitMessage("[slayer_rewards.no_task]") { it == "[slayer_rewards.no_task]" }

                client.commandReply("slayer task rats 50 vannaka") { it.startsWith("Task") }
                client.confirm(SlayerRewards.BLOCK)
                client.awaitVarp(4833, "rats in Vannaka's first block slot") { it and 0xFF == 3 }
                // The count's varp may come a packet after the block's.
                client.awaitVarp(Slayer.VARP_COUNT, "the blocked task gone") { it == 0 }
                assertEquals(110, client.points())

                // Vannaka gives only cows now.
                client.closeModal()
                repeat(3) {
                    client.npcOp(npc, SlayerMasters.ASSIGNMENT)
                    val line = client.awaitDialogue("a new task") { it is DialogueView.Chat && it.text.startsWith("[slayer_master.new_task ") }
                    assertTrue((line as DialogueView.Chat).text.endsWith(" task=cows]"), line.text)
                    client.continueDialogue()
                    client.awaitDialogue("the tips menu") { it is DialogueView.Menu }
                    client.chooseOption(2)
                    client.awaitDialogue("okay great") { it is DialogueView.Chat }
                    client.continueDialogue()
                    client.commandReply("slayer clear") { it.startsWith("No task") }
                }

                client.open()
                client.confirm(SlayerRewards.UNBLOCK[0])
                client.awaitVarp(4833, "Vannaka's first block slot empty") { it and 0xFF == 0 }
            }
        }
    }

    @Test
    fun `with no quest points only the Lumbridge & Draynor elite tier opens a block slot, the seventh`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.commandReply("setlevel all 40") { it.startsWith("Every skill") }
                client.commandReply("slayer points 200") { it.startsWith("Streak") }
                client.commandReply("slayer task rats 50 vannaka") { it.startsWith("Task") }
                client.open()
                client.confirm(SlayerRewards.BLOCK)
                client.awaitMessage("[slayer_rewards.no_block_slot]") { it == "[slayer_rewards.no_block_slot]" }
                assertEquals(200, client.points())

                client.closeModal()
                client.commandReply("diary complete lumbridge hard all") { "task(s) done" in it }
                client.open()
                client.confirm(SlayerRewards.BLOCK)
                client.awaitMessage("[slayer_rewards.no_block_slot] again") { _ -> client.messages().count { it.text == "[slayer_rewards.no_block_slot]" } == 2 }

                client.closeModal()
                client.commandReply("diary complete lumbridge elite all") { "task(s) done" in it }
                client.open()
                client.confirm(SlayerRewards.BLOCK)
                client.awaitVarp(4842, "rats in Vannaka's diary block slot") { (it shr 16) and 0xFF == 3 }
                assertEquals(140, client.points())
            }
        }
    }

    private fun HeadlessClient.count(id: Int) = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.buy(child: Int, op: Int) = ifButton(SlayerRewards.INTERFACE, SlayerRewards.BUY_ITEMS, op, child)

    @Test
    fun `Trade opens the Slayer Equipment shop, and the Buy tab spends points on as many as can be paid for`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.npcOp(vannakaIndex(client), TRADE)
                client.await("the shop") { if (ShopScreen.MAIN in client.openInterfaces()) Unit else null }
                client.closeModal()

                client.commandReply("slayer points 300") { it.startsWith("Streak") }
                client.open()
                client.await("the Buy tab's ops") { if (client.opEnabled(SlayerRewards.INTERFACE, SlayerRewards.BUY_ITEMS, 0, 3)) Unit else null }
                // Buy 5 rings with points for 4.
                client.buy(0, 3)
                client.await("four rings") { if (client.count(RING) == 4) Unit else null }
                assertEquals(0, client.points())
                client.buy(1, 2)
                client.awaitMessage("[slayer_rewards.not_enough_points cost=35]") { it == "[slayer_rewards.not_enough_points cost=35]" }

                // Buy 10 sets of broad bolts with points for 2: 500 bolts, 30 points left.
                client.commandReply("slayer points 100") { it.startsWith("Streak") }
                client.buy(1, 4)
                client.await("500 bolts") { if (client.count(BOLTS) == 500) Unit else null }
                assertEquals(30, client.points())

                // The looting bag comes one at a time: Buy 5 does nothing, Buy 1 buys it.
                client.buy(5, 3)
                client.buy(5, 2)
                client.await("a looting bag") { if (client.count(LOOTING_BAG) == 1) Unit else null }
                assertEquals(20, client.points())
            }
        }
    }

    @Test
    fun `the herb sack needs base Herblore 58, a second pouch or bag is refused, and bolts stop at the stack limit`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                val dave = server.world.playerNamed("dave")!!
                client.commandReply("slayer points 5000") { it.startsWith("Streak") }
                client.open()
                client.await("the Buy tab's ops") { if (client.opEnabled(SlayerRewards.INTERFACE, SlayerRewards.BUY_ITEMS, 0, 3)) Unit else null }

                // Herblore 57 boosted to 60 still can't buy the herb sack; base 58 can.
                client.command("setlevel herblore 57")
                client.command("boost herblore 3")
                client.buy(3, 2)
                client.awaitMessage("need_herblore") { it == "[slayer_rewards.need_herblore]" }
                assertEquals(0, client.count(HERB_SACK))
                client.command("setlevel herblore 58")
                client.buy(3, 2)
                client.await("a herb sack") { if (client.count(HERB_SACK) == 1) Unit else null }
                assertEquals(4250, client.points())

                // A divine rune pouch in the bank counts as a rune pouch, and an open looting bag as a looting bag.
                dave.bank.add(server.world.objTypes[DIVINE_RUNE_POUCH]!!, 1)
                client.buy(4, 2)
                client.awaitMessage("already_own rune pouch") { it == "[slayer_rewards.already_own item=Rune pouch]" }
                client.command("item $OPEN_LOOTING_BAG")
                client.buy(5, 2)
                client.awaitMessage("already_own looting bag") { it == "[slayer_rewards.already_own item=Looting bag]" }
                assertEquals(0, client.count(RUNE_POUCH))
                assertEquals(0, client.count(LOOTING_BAG))
                assertEquals(4250, client.points())

                // 250 short of the stack limit: Buy 10 sets takes one set, 35 points; full, it takes none.
                client.command("item $BOLTS ${Int.MAX_VALUE - 250}")
                client.await("the bolts") { if (client.count(BOLTS) == Int.MAX_VALUE - 250) Unit else null }
                client.buy(1, 4)
                client.await("a full stack") { if (client.count(BOLTS) == Int.MAX_VALUE) Unit else null }
                assertEquals(4215, client.points())
                client.buy(1, 4)
                client.awaitMessage("no_room") { it == "[slayer_rewards.no_room]" }
                assertEquals(4215, client.points())
            }
        }
    }

    @Test
    fun `the Slayer Equipment shop buys back a witchwood icon but no other untradeable`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.npcOp(vannakaIndex(client), TRADE)
                client.await("Sell on the inventory") { if (client.opEnabled(ShopScreen.SIDE, ShopScreen.SIDE_ITEMS, 27, 2)) Unit else null }
                client.command("item $WITCHWOOD_ICON")
                client.command("item $ENCHANTED_GEM")
                client.await("the icon and gem") { if (client.count(WITCHWOOD_ICON) == 1 && client.count(ENCHANTED_GEM) == 1) Unit else null }
                client.ifButton(ShopScreen.SIDE, ShopScreen.SIDE_ITEMS, 2, client.slotOf(ENCHANTED_GEM), ENCHANTED_GEM)
                client.awaitMessage("no selling the gem") { it == DialogueText.NONE[ShopLines.CANT_SELL] }
                client.ifButton(ShopScreen.SIDE, ShopScreen.SIDE_ITEMS, 2, client.slotOf(WITCHWOOD_ICON), WITCHWOOD_ICON)
                client.await("coins for the icon") { if (client.count(WITCHWOOD_ICON) == 0 && client.count(COINS) > 0) Unit else null }
                assertEquals(1, client.count(ENCHANTED_GEM))
            }
        }
    }

    private fun HeadlessClient.slotOf(id: Int) = inventory(Inventory.INV).indexOfFirst { it?.id == id }

    private fun vannakaIndex(client: HeadlessClient) = client.await("Vannaka in view") { client.npcs().firstOrNull { it.id == vannaka.id } }.index

    private companion object {
        const val COINS = 995

        /** Need More Darkness's and Ankou Very Much's bits. */
        const val EXTENDS = (1 shl 4) or (1 shl 8)

        /** Vannaka's op 4, Trade. */
        const val TRADE = 4
        const val RING = 11866
        const val BOLTS = 11875
        const val LOOTING_BAG = 11941
        const val OPEN_LOOTING_BAG = 22586
        const val HERB_SACK = 13226
        const val RUNE_POUCH = 12791
        const val DIVINE_RUNE_POUCH = 27281
        const val WITCHWOOD_ICON = 8923
        const val ENCHANTED_GEM = 4155

        /** The masters' 242 cache types, by which shops.json names them. */
        val INTERNAL = mapOf(
            SlayerMaster.AYA to "slayer_master_1_aya", SlayerMaster.SPRIA to "slayer_master_9_active",
            SlayerMaster.VANNAKA to "slayer_master_3", SlayerMaster.MAZCHNA to "slayer_master_2_mazchna",
            SlayerMaster.CHAELDAR to "slayer_master_4", SlayerMaster.STEVE to "slayer_master_steve",
            SlayerMaster.KURADAL to "slayer_master_5_kuradal",
            SlayerMaster.KRYSTILIA to "slayer_master_7",
        )
    }
}
