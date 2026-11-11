package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.cache.DbRow
import osrsps.cache.OmnishopLoader
import osrsps.game.Inventory
import osrsps.game.NpcType
import osrsps.game.NpcTypes
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.combat.Foods
import osrsps.game.dialogue.DialogueText
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.raid.ArchiveRaids
import osrsps.game.raid.ArchiveRoom
import osrsps.game.raid.ArchiveRun
import osrsps.game.raid.ArchiveSupplies
import osrsps.game.shop.OmnishopScreen
import osrsps.game.shop.Omnishops
import osrsps.game.shop.ShopLines
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * The Quartermaster and his Alliance Supplies (raid step 21) through the headless client: the cache's omnishop rows as
 * the loader reads them; him and his wheelbarrow coming to the nexus once the third row's room is done, with each
 * member's 50 Supply Credits, and going as the fourth row's fight begins; Resupply opening the omnishop window, its list
 * and buttons buying from the credits and the member's own stock, and its refusals; his Talk-to; the failure shop
 * with Leftovers after deaths; and the credits gone as a new run is entered. The rl-client script
 * test-runner/examples/archive-supplies.json does the same in the real client.
 */
class HeadlessArchiveSuppliesTest {
    private val quartermaster = NpcType(ArchiveSupplies.QUARTERMASTER, "Quartermaster", ops = listOf("Resupply", null, "Talk-to", null, null), internalName = "tfa_midraid_supplies_npc")

    private val locTypes = LocTypes(
        mapOf(
            ArchiveRaids.ARCHIVE_DOOR to LocType(ArchiveRaids.ARCHIVE_DOOR, "Archive Door", ops = listOf("Enter", null, null, null, null), collision = LocCollisionType(width = 9, length = 1)),
            ArchiveSupplies.WHEELBARROW to LocType(ArchiveSupplies.WHEELBARROW, "Supplies Wheelbarrow", collision = LocCollisionType(width = 2, length = 2)),
        ),
    )
    private val staticLocs = StaticLocs().apply { add(ZoneLoc(ArchiveRaids.ARCHIVE_DOOR, LocShape.CENTREPIECE_STRAIGHT, 0, 4123, 5562, 0)) }

    /** The shop's items, as the 241 cache has them (ids, names, and that none stacks). */
    private val objTypes = ObjTypes.of(
        ObjType(BREW, "Saradomin brew(4)", internalName = "4dosepotionofsaradomin"),
        ObjType(RESTORE, "Super restore(4)", internalName = "4dose2restore"),
        ObjType(PRAYER_4, "Prayer potion(4)", internalName = "4doseprayerrestore"),
        ObjType(MANTA, "Manta ray", internalName = "mantaray"),
        ObjType(HALIBUT, "Halibut", internalName = "halibut"),
        ObjType(KRILL, "Giant krill", internalName = "giant_krill"),
        ObjType(PRAYER_2, "Prayer potion(2)", internalName = "2doseprayerrestore"),
        ObjType(RANGING_2, "Ranging potion(2)", internalName = "2doserangerspotion"),
        ObjType(COMBAT_2, "Super combat potion(2)", internalName = "2dose2combat"),
        ObjType(LOG, "Alliance log", internalName = "tfa_midraid_supplies_note"),
        ObjType(ArchiveSupplies.LEFTOVERS, "Leftovers", iops = listOf("Eat", null, null, null, null), internalName = "tfa_midraid_supplies_leftovers"),
        ObjType(AXE, "Bronze axe", internalName = "bronze_axe"),
    )

    /** The cache's db rows for the shop (241 cache, read 2026-11-11), its texts left out. */
    private fun stockRow(obj: Int, cost: Int, extra: Map<Int, List<Any>> = emptyMap()) = DbRow(OmnishopLoader.STOCK_TABLE, mapOf(0 to listOf<Any>(obj), 19 to listOf<Any>(0), 20 to listOf<Any>(0), 7 to listOf<Any>(CURRENCY, cost)) + extra)

    private fun shopRow(stock: List<Int>) = DbRow(
        OmnishopLoader.SHOP_TABLE,
        mapOf(0 to listOf("Alliance Supplies"), 1 to listOf(ArchiveSupplies.STOCK_INV), 2 to listOf(CURRENCY), 5 to stock, 8 to listOf(0), 9 to listOf(CURRENCY), 16 to listOf(0), 19 to listOf(0), 20 to listOf(1), 21 to listOf(5), 22 to listOf(6), 23 to listOf(2)),
    )

    private val omnishops: Omnishops = OmnishopLoader.build(
        shops = mapOf(
            ArchiveSupplies.SHOP to shopRow(listOf(17152, 17159, 17160, 17158, 17155, 17156, 17154, 17157, 17161, 17162)),
            ArchiveSupplies.FAILURE_SHOP to shopRow(listOf(17152, 17153, 17159, 17160, 17158, 17155, 17156, 17154, 17157, 17161, 17162)),
        ),
        stock = mapOf(
            17152 to stockRow(LOG, 0, mapOf(18 to listOf(0), 21 to listOf(1))),
            17153 to stockRow(ArchiveSupplies.LEFTOVERS, 0),
            17154 to stockRow(KRILL, 1),
            17155 to stockRow(MANTA, 2),
            17156 to stockRow(HALIBUT, 2),
            17157 to stockRow(PRAYER_2, 1),
            17158 to stockRow(PRAYER_4, 2),
            17159 to stockRow(BREW, 3),
            17160 to stockRow(RESTORE, 3),
            17161 to stockRow(RANGING_2, 2),
            17162 to stockRow(COMBAT_2, 2),
        ),
        currencies = mapOf(CURRENCY to DbRow(OmnishopLoader.CURRENCY_TABLE, mapOf(1 to listOf("Supply Credit"), 2 to listOf("Supply Credits")))),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        World(
            crcs,
            CollisionMap().apply {
                for (x in 63..65) for (z in 86..88) markLoaded(x, z)
                markLoaded(3222 shr 6, 3218 shr 6)
            },
            locTypes = locTypes, staticLocs = staticLocs, objTypes = objTypes,
            npcTypes = NpcTypes.of(quartermaster), omnishops = omnishops,
            // tfa_supply_points: bits 1 to 7 of its varp, as the 241 cache has it (the varp's id here is the test's).
            varTypes = VarTypes(6_000, mapOf(ArchiveSupplies.CREDITS_VARBIT to VarbitType(CREDITS_VARP, 1, 7))),
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tele(x: Int, z: Int) {
        command("tele $x $z 0")
        await("the teleport to $x, $z") { position?.takeIf { it == Coord(x, z, 0) } }
    }

    private fun HeadlessClient.credits(): Int = (varp(CREDITS_VARP) ushr 1) and 0x7f

    private fun HeadlessClient.awaitCredits(credits: Int) = await("$credits credits") { credits().takeIf { it == credits } }

    private fun HeadlessClient.held(id: Int): Int = inventory(Inventory.INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.awaitHeld(id: Int, count: Int) = await("$count of $id held") { held(id).takeIf { it == count } }

    private fun HeadlessClient.stock(id: Int): Int = inventory(ArchiveSupplies.STOCK_INV).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.awaitStock(id: Int, count: Int) = await("$count of $id in stock") { stock(id).takeIf { it == count } }

    /** Into a run from the lobby's Archive Door. */
    private fun HeadlessClient.intoRun(server: TestServer, username: String): ArchiveRun {
        tele(ArchiveRaids.LOBBY_X, ArchiveRaids.LOBBY_Z)
        locOp(ArchiveRaids.ARCHIVE_DOOR, 4123, 5562, ArchiveRaids.ENTER)
        await("into the instance") { position?.takeIf { server.world.instances.inDynamicArea(it.x, it.z) } }
        return assertNotNull(server.world.parties.of(server.world.playerNamed(username)!!)?.let(server.world.archiveRaids::of))
    }

    /** Through [room], done as it is walked into (no boss is built in this world). */
    private fun HeadlessClient.clear(run: ArchiveRun, room: ArchiveRoom) {
        commandReply("raid tp ${room.name.lowercase()}") { it.startsWith("To ") }
        await("${room.boss}'s room done") { room.takeIf { it in run.cleared } }
    }

    /** Back into the nexus, beside the Quartermaster's tile. */
    private fun HeadlessClient.toNexus(server: TestServer, run: ArchiveRun) {
        commandReply("raid tp ${ArchiveSupplies.X - 1} ${ArchiveSupplies.Z - 2}") { it.startsWith("To ") }
        val at = server.world.archiveRaids.tile(run, ArchiveSupplies.X - 1, ArchiveSupplies.Z - 2)
        await("in the nexus") { position?.takeIf { it == Coord(at.x, at.z, 0) } }
    }

    private fun HeadlessClient.quartermaster() = npcs().firstOrNull { it.id == ArchiveSupplies.QUARTERMASTER }

    private fun HeadlessClient.wheelbarrowShown(server: TestServer, run: ArchiveRun): Boolean {
        val at = server.world.archiveRaids.tile(run, ArchiveSupplies.WHEELBARROW_X, ArchiveSupplies.WHEELBARROW_Z)
        return locChanges().any { it.id == ArchiveSupplies.WHEELBARROW && it.coord == Coord(at.x, at.z, 0) }
    }

    private fun HeadlessClient.resupply() {
        val npc = await("the Quartermaster") { quartermaster() }
        npcOp(npc.index, ArchiveSupplies.RESUPPLY)
        await("Alliance Supplies open") { (OmnishopScreen.MAIN in openInterfaces()).takeIf { it } }
    }

    /** A click on the list's entry [entry]: op 1 selects, 2 to 5 buy 1, 2, 3 and 5, 10 examines. */
    private fun HeadlessClient.entry(entry: Int, op: Int) = ifButton(OmnishopScreen.MAIN, OmnishopScreen.LIST, op, entry * OmnishopScreen.ENTRY_COMPONENTS)

    @Test
    fun `the cache's rows read as Alliance Supplies, its failure row with Leftovers, buying 1, 2, 3 and 5, and Leftovers heal 5`() {
        val shop = assertNotNull(omnishops[ArchiveSupplies.SHOP])
        assertEquals("Alliance Supplies", shop.name)
        assertEquals(ArchiveSupplies.STOCK_INV, shop.inv)
        assertEquals("Supply Credits", shop.currency?.plural)
        assertEquals(listOf(1, 2, 3, 5), shop.amounts, "q1 to q4 of 1, 5, 6 and 2 through enum_5844")
        assertEquals(listOf(LOG, BREW, RESTORE, PRAYER_4, MANTA, HALIBUT, KRILL, PRAYER_2, RANGING_2, COMBAT_2), shop.stock.map { it.obj })
        assertEquals(listOf(0, 3, 3, 2, 2, 2, 1, 1, 2, 2), shop.stock.map { it.cost })
        assertTrue(shop.stock[0].viewOnly && !shop.stock[0].buyable, "the Alliance log is only shown")
        assertTrue(shop.stock.drop(1).all { it.buyable && !it.viewOnly && it.multiplier == 1 })
        val failure = assertNotNull(omnishops[ArchiveSupplies.FAILURE_SHOP])
        assertEquals(ArchiveSupplies.LEFTOVERS, failure.stock[1].obj)
        assertEquals(0, failure.stock[1].cost)
        // Every item the shop sells has a stock, and every stock item is in the shop.
        assertEquals(shop.stock.drop(1).map { it.obj }.toSet(), ArchiveSupplies.STOCK.keys)
        assertEquals(5, Foods.load(ObjTypes.of(objTypes[ArchiveSupplies.LEFTOVERS]!!))[ArchiveSupplies.LEFTOVERS]?.heals, "Leftovers heal 5 (the Wiki)")
    }

    @Test
    fun `he comes with 50 credits once the third row's room is done, and the shop's list and buttons buy from them and the stock`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                // Credits left from before go as a run is entered.
                client.command("varbit ${ArchiveSupplies.CREDITS_VARBIT} 9")
                client.awaitCredits(9)
                val run = client.intoRun(server, "bob")
                client.awaitCredits(0)
                client.clear(run, ArchiveRoom.GUARDIAN)
                client.clear(run, ArchiveRoom.BORK)
                client.toNexus(server, run)
                client.awaitTicks(2)
                assertEquals(null, client.quartermaster(), "not after the second row")
                client.clear(run, ArchiveRoom.DREADNOTH)
                client.toNexus(server, run)
                val npc = client.await("the Quartermaster") { client.quartermaster() }
                val at = server.world.archiveRaids.tile(run, ArchiveSupplies.X, ArchiveSupplies.Z)
                assertEquals(Coord(at.x, at.z, 0), npc.coord)
                client.await("his wheelbarrow") { client.wheelbarrowShown(server, run).takeIf { it } }
                client.awaitCredits(ArchiveSupplies.CREDITS)

                client.resupply()
                assertTrue(OmnishopScreen.SIDE in client.openInterfaces())
                assertEquals(ArchiveSupplies.SHOP, client.varp(OmnishopScreen.LASTSHOP_VARP))
                assertEquals(-1, client.varp(OmnishopScreen.SELECTED_VARP))
                val scripts = client.clientScripts()
                assertTrue(ClientScript(OmnishopScreen.MAINMODAL_BACKGROUND_SCRIPT, listOf(-1, -3)) in scripts, "$scripts")
                assertTrue(ClientScript(OmnishopScreen.MAIN_INIT_SCRIPT, listOf(ArchiveSupplies.SHOP, ArchiveSupplies.STOCK_INV, -1)) in scripts, "$scripts")
                assertTrue(ClientScript(OmnishopScreen.SIDE_INIT_SCRIPT, listOf(ArchiveSupplies.SHOP)) in scripts, "$scripts")
                assertTrue((1..5).all { client.opEnabled(OmnishopScreen.MAIN, OmnishopScreen.LIST, 12, it) } && client.opEnabled(OmnishopScreen.MAIN, OmnishopScreen.LIST, 12, 10))
                client.awaitStock(BREW, 6)
                for ((id, count) in ArchiveSupplies.STOCK) assertEquals(count, client.stock(id), "stock of $id")

                // Buy-2 on the brews: 6 credits.
                client.entry(1, 3)
                client.awaitHeld(BREW, 2)
                client.awaitCredits(44)
                client.awaitStock(BREW, 4)
                client.await("the currency redrawn") { client.clientScripts().lastOrNull { it.id == OmnishopScreen.CURRENCY_REDRAW_SCRIPT }?.takeIf { it.args == listOf(ArchiveSupplies.SHOP) } }
                // Select the Manta rays, then the info panel's fourth button, Buy-5.
                client.entry(4, 1)
                client.await("the Manta rays selected") { client.varp(OmnishopScreen.SELECTED_VARP).takeIf { it == 4 } }
                client.ifButton(OmnishopScreen.MAIN, OmnishopScreen.BUTTONS.last, 1)
                client.awaitHeld(MANTA, 5)
                client.awaitCredits(34)
                client.awaitStock(MANTA, 5)
                // Selected again, it is unselected, and a button buys nothing.
                client.entry(4, 1)
                client.await("nothing selected") { client.varp(OmnishopScreen.SELECTED_VARP).takeIf { it == -1 } }
                client.ifButton(OmnishopScreen.MAIN, OmnishopScreen.BUTTONS.first, 1)
                client.awaitTicks(2)
                assertEquals(5, client.held(MANTA))
                assertEquals(34, client.credits())
                // Buy-5 on the brews with four left: four.
                client.entry(1, 5)
                client.awaitHeld(BREW, 6)
                client.awaitCredits(22)
                client.awaitStock(BREW, 0)
                // Buy-5 krill and Buy-5 restores (four in stock): 5 and 12.
                client.entry(6, 5)
                client.awaitHeld(KRILL, 5)
                client.entry(2, 5)
                client.awaitHeld(RESTORE, 4)
                client.awaitCredits(5)
                // Buy-3 on Prayer potion(4) at 2: two, then not enough credits.
                client.entry(3, 4)
                client.awaitMessage("not enough credits") { it == DialogueText.NONE.get(ShopLines.NOT_ENOUGH_CURRENCY, "currency" to "Supply Credits") }
                client.awaitHeld(PRAYER_4, 2)
                client.awaitCredits(1)
                // The Alliance log is only shown.
                client.entry(0, 2)
                client.awaitTicks(2)
                assertEquals(0, client.held(LOG))
                // A full inventory: no room.
                client.command("item $AXE 6")
                client.await("a full inventory") { client.inventory(Inventory.INV).count { it != null }.takeIf { it == Inventory.INV_CAPACITY } }
                client.entry(7, 2)
                client.awaitMessage("no room") { it == DialogueText.NONE[ShopLines.NOT_ENOUGH_SPACE] }
                assertEquals(1, client.credits())
                assertEquals(1, client.stock(PRAYER_2))
            }
        }
    }

    @Test
    fun `his Talk-to, the failure shop after two deaths, and he goes as the fourth row's fight begins`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                val run = client.intoRun(server, "bob")
                client.clear(run, ArchiveRoom.GUARDIAN)
                client.clear(run, ArchiveRoom.BORK)
                // Two deaths so far in the run: the failure shop.
                run.deaths[server.world.playerNamed("bob")!!.saveKey] = ArchiveSupplies.FAILURE_DEATHS
                client.clear(run, ArchiveRoom.DREADNOTH)
                client.toNexus(server, run)
                val npc = client.await("the Quartermaster") { client.quartermaster() }

                // Talk-to: fine for now ends it.
                client.npcOp(npc.index, ArchiveSupplies.TALK_TO)
                for (key in listOf("hello", "greeting")) {
                    client.awaitDialogue(key) { it is DialogueView.Chat && it.text == "[tfa_quartermaster.$key]" }
                    client.continueDialogue()
                }
                val menu = client.awaitDialogue("his menu") { it is DialogueView.Menu } as DialogueView.Menu
                assertEquals(listOf("[tfa_quartermaster.resupply]", "[tfa_quartermaster.who]", "[tfa_quartermaster.fine]"), menu.options)
                client.chooseOption(3)
                for (key in listOf("fine", "advice", "keep_in_mind")) {
                    client.awaitDialogue(key) { it is DialogueView.Chat && it.text == "[tfa_quartermaster.$key]" }
                    client.continueDialogue()
                }
                client.awaitTicks(2)
                assertFalse(OmnishopScreen.MAIN in client.openInterfaces())
                // Talk-to again: who he is, then resupply opens the shop.
                client.npcOp(npc.index, ArchiveSupplies.TALK_TO)
                for (key in listOf("hello", "greeting")) {
                    client.awaitDialogue(key) { it is DialogueView.Chat && it.text == "[tfa_quartermaster.$key]" }
                    client.continueDialogue()
                }
                client.awaitDialogue("his menu") { it is DialogueView.Menu }
                client.chooseOption(2)
                for (key in listOf("who", "intro")) {
                    client.awaitDialogue(key) { it is DialogueView.Chat && it.text == "[tfa_quartermaster.$key]" }
                    client.continueDialogue()
                }
                client.awaitDialogue("the second menu") { it is DialogueView.Menu }
                client.chooseOption(1)
                for (key in listOf("resupply", "agree")) {
                    client.awaitDialogue(key) { it is DialogueView.Chat && it.text == "[tfa_quartermaster.$key]" }
                    client.continueDialogue()
                }
                client.await("Alliance Supplies open") { (OmnishopScreen.MAIN in client.openInterfaces()).takeIf { it } }
                assertEquals(ArchiveSupplies.FAILURE_SHOP, client.varp(OmnishopScreen.LASTSHOP_VARP), "the failure row after two deaths")
                client.awaitStock(ArchiveSupplies.LEFTOVERS, ArchiveSupplies.LEFTOVERS_STOCK)
                // Leftovers are free: entry 1 of the failure row; the list moves one down after them.
                client.entry(1, 5)
                client.awaitHeld(ArchiveSupplies.LEFTOVERS, ArchiveSupplies.LEFTOVERS_STOCK)
                assertEquals(ArchiveSupplies.CREDITS, client.credits())
                client.entry(2, 2)
                client.awaitHeld(BREW, 1)
                client.awaitCredits(ArchiveSupplies.CREDITS - 3)

                // Into the fourth row: he and his wheelbarrow go, and his shop closes.
                client.clear(run, ArchiveRoom.XARTH)
                client.await("the shop closed") { (OmnishopScreen.MAIN !in client.openInterfaces()).takeIf { it } }
                assertEquals(null, run.quartermaster)
                val barrow = server.world.archiveRaids.tile(run, ArchiveSupplies.WHEELBARROW_X, ArchiveSupplies.WHEELBARROW_Z)
                assertEquals(null, server.world.locs.find(ArchiveSupplies.WHEELBARROW, barrow.x, barrow.z, 0))
                client.toNexus(server, run)
                client.awaitTicks(2)
                assertEquals(null, client.quartermaster(), "gone for the rest of the run")
            }
        }
    }

    private companion object {
        const val CREDITS_VARP = 4_500
        const val CURRENCY = 17149
        const val BREW = 6685
        const val RESTORE = 3024
        const val PRAYER_4 = 2434
        const val MANTA = 391
        const val HALIBUT = 32336
        const val KRILL = 32312
        const val PRAYER_2 = 141
        const val RANGING_2 = 171
        const val COMBAT_2 = 12699
        const val LOG = 34729
        const val AXE = 1351
    }
}
