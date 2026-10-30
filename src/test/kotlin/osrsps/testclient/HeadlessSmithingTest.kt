package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.dialogue.Chatbox
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.AnvilItems
import osrsps.game.skill.Smithing
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Gathering step 7 through the headless client: the furnace's Make-X box and its 4-then-5 tick clock, iron's impure
 * ore, the checks before smelting, and the anvil interface with its quantity buttons and 5-tick clock. The rl-client
 * script test-runner/examples/smithing-lumbridge.json smelts and smiths north of Lumbridge Castle.
 */
class HeadlessSmithingTest {
    private fun server(dir: Path) = TestServer(dir, cycleMillis = 40) { crcs, millis ->
        val ids = Smithing.BARS.flatMap { bar -> bar.ores.map { it.first } + bar.obj } + Smithing.HAMMER +
            AnvilItems.BY_BAR.getValue(Smithing.BRONZE.obj).map { it.obj }
        val objs = arrayOfNulls<ObjType>(ids.max() + 1)
        for (id in ids) objs[id] = ObjType(id, "Obj $id", stackable = id == NAILS_OBJ)
        fun loc(id: Int, ops: List<String?>) = LocType(id, "Loc $id", ops, LocCollisionType(width = 1, length = 1))
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), random = GameRandom(7),
            locTypes = LocTypes(
                listOf(
                    // Smelt is the furnace's second option, as on Lumbridge's.
                    loc(Smithing.FURNACE, listOf(null, "Smelt", null, null, null)),
                    loc(Smithing.RUSTED_ANVIL, listOf("Smith", null, null, null, null)),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(Smithing.FURNACE, LocShape.CENTREPIECE_STRAIGHT, 0, FURNACE_X, FURNACE_Z, 0))
                add(ZoneLoc(Smithing.RUSTED_ANVIL, LocShape.CENTREPIECE_STRAIGHT, 0, ANVIL_X, ANVIL_Z, 0))
            },
            varTypes = VARS,
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.made(prefix: String) = messages().filter { it.text.startsWith(prefix) }

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = count(id)
        command("item $id $count")
        await("$count of $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.smeltBox() = await("the Make-X box") { clientScripts().firstOrNull { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT } }

    /** The Make-X box's button for [bar]. */
    private fun barButton(bar: osrsps.game.skill.Bar) = Chatbox.SKILLMULTI_FIRST_ITEM + Smithing.BARS.indexOf(bar)

    @Test
    fun `two bronze bars from the box come 4 then 5 ticks after the pick, 6_2 XP each`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.give(COPPER, 2)
                client.give(TIN, 2)
                client.locOp(Smithing.FURNACE, FURNACE_X, FURNACE_Z, 2)
                val setup = client.smeltBox()
                assertEquals(Smithing.BARS.map { it.obj }, setup.args.filterIsInstance<Int>().filter { it in Smithing.BARS.map { bar -> bar.obj } })
                val picked = client.ticksSeen
                client.resumePause(Chatbox.SKILLMULTI, barButton(Smithing.BRONZE), 2)
                val bars = client.await("two bars", timeoutMillis = 20_000) { client.made("[smithing.smelted").takeIf { it.size >= 2 } }
                assertEquals("[smithing.smelted bar=bronze]", bars[0].text)
                assertTrue(bars[0].tick - picked in 4..6, "the first bar ${bars[0].tick - picked} ticks after the pick")
                assertEquals(5, bars[1].tick - bars[0].tick)
                client.await("the bars") { if (client.count(Smithing.BRONZE.obj) == 2) Unit else null }
                assertEquals(0, client.count(COPPER) + client.count(TIN))
                assertEquals(12, client.await("Smithing XP") { client.stat(SMITHING)?.takeIf { it.xp >= 12 } }.xp)
            }
        }
    }

    @Test
    fun `the first bar animates at once while another skill's clock still runs`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.give(COPPER, 2)
                client.give(TIN, 2)
                client.locOp(Smithing.FURNACE, FURNACE_X, FURNACE_Z, 2)
                client.smeltBox()
                // As if mining had set the shared skill clock a moment ago: its roll is still to come.
                server.world.playerNamed("dave")!!.actionDelay = server.world.tick + 30
                val picked = client.ticksSeen
                client.resumePause(Chatbox.SKILLMULTI, barButton(Smithing.BRONZE), 2)
                // Before the fix, nothing animated until the clock ran out and the first bar was made.
                val anim = client.await("the furnace animation") { client.animationTicks().firstOrNull { it >= picked } }
                assertTrue(anim - picked < 4, "animated ${anim - picked} ticks after the pick")
                val bar = client.seen("[smithing.smelted bar=bronze]")
                assertTrue(anim < bar.tick, "animated at ${anim - picked}, the bar at ${bar.tick - picked}")
            }
        }
    }

    @Test
    fun `one bar takes 6 ticks, and the box checks the level and the ore`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.locOp(Smithing.FURNACE, FURNACE_X, FURNACE_Z, 2)
                client.smeltBox()
                client.resumePause(Chatbox.SKILLMULTI, barButton(Smithing.STEEL), 1)
                client.seen("[smithing.smelt_level level=30 bar=steel]")

                client.locOp(Smithing.FURNACE, FURNACE_X, FURNACE_Z, 2)
                client.await("a second box") { client.clientScripts().filter { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }.takeIf { it.size >= 2 } }
                client.resumePause(Chatbox.SKILLMULTI, barButton(Smithing.BRONZE), 1)
                client.seen("[smithing.smelt_no_ore bar=bronze]")

                client.give(COPPER)
                client.give(TIN)
                client.locTarget(Smithing.FURNACE, FURNACE_X, FURNACE_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, client.inventory(BACKPACK).indexOfFirst { it?.id == COPPER }, COPPER)
                client.await("a third box") { client.clientScripts().filter { it.id == Chatbox.SKILLMULTI_SETUP_SCRIPT }.takeIf { it.size >= 3 } }
                val picked = client.ticksSeen
                client.resumePause(Chatbox.SKILLMULTI, barButton(Smithing.BRONZE), 1)
                val bar = client.seen("[smithing.smelted bar=bronze]")
                assertTrue(bar.tick - picked in 6..8, "the bar ${bar.tick - picked} ticks after the pick")
            }
        }
    }

    @Test
    fun `iron ore is impure half the time, losing the ore`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel smithing 15")
                client.await("level 15") { client.stat(SMITHING)?.takeIf { it.level == 15 } }
                client.give(IRON_ORE, 10)
                client.locOp(Smithing.FURNACE, FURNACE_X, FURNACE_Z, 2)
                client.smeltBox()
                client.resumePause(Chatbox.SKILLMULTI, barButton(Smithing.IRON), 10)
                client.await("ten tries", timeoutMillis = 30_000) {
                    if (client.made("[smithing.smelted").size + client.made("[smithing.impure").size == 10) Unit else null
                }
                client.await("no ore left") { if (client.count(IRON_ORE) == 0) Unit else null }
                val bars = client.count(Smithing.IRON.obj)
                assertTrue(bars in 1..9, "$bars iron bars from 10 ore")
                assertEquals(10 - bars, client.made("[smithing.impure]").size)
            }
        }
    }

    @Test
    fun `the anvil needs a hammer and bars, then All makes daggers 5 ticks apart`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.locOp(Smithing.RUSTED_ANVIL, ANVIL_X, ANVIL_Z, 1)
                client.seen("[smithing.no_bars]")
                client.give(Smithing.BRONZE.obj, 3)
                client.locOp(Smithing.RUSTED_ANVIL, ANVIL_X, ANVIL_Z, 1)
                client.seen("[smithing.no_hammer]")
                client.give(Smithing.HAMMER)

                client.locOp(Smithing.RUSTED_ANVIL, ANVIL_X, ANVIL_Z, 1)
                client.await("the anvil") { if (Smithing.INTERFACE in client.openInterfaces()) Unit else null }
                assertEquals(Smithing.BRONZE.anvilType, client.varp(VARP_SMITHBARS) and 7)
                // A platebody needs level 18 and 5 bars.
                client.ifButton(Smithing.INTERFACE, PLATEBODY, 1)
                client.seen("[smithing.anvil_level level=18 item=obj 1117]")

                client.ifButton(Smithing.INTERFACE, MAKE_ALL, 1)
                client.await("All") { if (client.varp(Smithing.VARP_QUANTITY) == Smithing.MAX_QUANTITY) Unit else null }
                client.ifButton(Smithing.INTERFACE, DAGGER, 1)
                val daggers = client.await("three daggers", timeoutMillis = 20_000) { client.made("[smithing.smithed").takeIf { it.size >= 3 } }
                assertEquals("[smithing.smithed bar=bronze item=obj 1205]", daggers[0].text)
                assertEquals(listOf(5, 5), daggers.zipWithNext { a, b -> b.tick - a.tick })
                assertTrue(Smithing.INTERFACE !in client.openInterfaces(), "the anvil closes as smithing starts")
                client.await("the daggers") { if (client.count(DAGGER_OBJ) == 3) Unit else null }
                assertEquals(0, client.count(Smithing.BRONZE.obj))
                assertEquals(37, client.await("Smithing XP") { client.stat(SMITHING)?.takeIf { it.xp >= 37 } }.xp)
                // The last item made shows highlighted next time: bronze (1) and the dagger (1).
                assertEquals(1 shl 24 or (1 shl 27), client.varp(VARP_LAST) and (0xFF shl 24))
            }
        }
    }

    @Test
    fun `X asks how many, and the anvil stops when the bars run short`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel smithing 99")
                client.give(Smithing.HAMMER)
                client.give(Smithing.BRONZE.obj, 5)
                client.useBarOnAnvil()
                client.await("the anvil") { if (Smithing.INTERFACE in client.openInterfaces()) Unit else null }
                client.ifButton(Smithing.INTERFACE, MAKE_X, 1)
                client.await("the amount prompt") { client.clientScripts().lastOrNull { it.id == ENTER_AMOUNT_SCRIPT } }
                client.resumeCount(4)
                client.await("X is 4") { if (client.varp(Smithing.VARP_QUANTITY) == 4) Unit else null }
                // Scimitars take 2 bars: 5 bars make 2, though 4 were asked for.
                client.ifButton(Smithing.INTERFACE, SCIMITAR, 1)
                client.await("two scimitars", timeoutMillis = 20_000) { if (client.count(SCIMITAR_OBJ) == 2) Unit else null }
                client.awaitTicks(12)
                assertEquals(2, client.made("[smithing.smithed").size)
                assertEquals(1, client.count(Smithing.BRONZE.obj))
            }
        }
    }

    @Test
    fun `the rusted anvil smiths only bronze`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("fred").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel smithing 99")
                client.give(Smithing.HAMMER)
                client.give(Smithing.IRON.obj)
                client.locOp(Smithing.RUSTED_ANVIL, ANVIL_X, ANVIL_Z, 1)
                client.await("Smith refused") { client.made("[smithing.bronze_only]").takeIf { it.size == 1 } }
                val slot = client.inventory(BACKPACK).indexOfFirst { it?.id == Smithing.IRON.obj }
                client.locTarget(Smithing.RUSTED_ANVIL, ANVIL_X, ANVIL_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slot, Smithing.IRON.obj)
                client.await("the iron bar refused") { client.made("[smithing.bronze_only]").takeIf { it.size == 2 } }
                assertTrue(Smithing.INTERFACE !in client.openInterfaces())
            }
        }
    }

    @Test
    fun `nails that would pass the largest stack aren't made and keep the bar`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("gina").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel smithing 99")
                client.give(Smithing.HAMMER)
                client.give(NAILS_OBJ, Int.MAX_VALUE - 5)
                client.give(Smithing.BRONZE.obj)
                client.useBarOnAnvil()
                client.await("the anvil") { if (Smithing.INTERFACE in client.openInterfaces()) Unit else null }
                client.ifButton(Smithing.INTERFACE, NAILS, 1)
                client.seen("[game.no_space]")
                client.awaitTicks(8)
                assertEquals(1, client.count(Smithing.BRONZE.obj))
                assertEquals(Int.MAX_VALUE - 5, client.count(NAILS_OBJ))
                assertEquals(0, client.made("[smithing.smithed").size)
            }
        }
    }

    private fun HeadlessClient.useBarOnAnvil() {
        val slot = inventory(BACKPACK).indexOfFirst { it?.id == Smithing.BRONZE.obj }
        locTarget(Smithing.RUSTED_ANVIL, ANVIL_X, ANVIL_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slot, Smithing.BRONZE.obj)
    }

    private companion object {
        const val FURNACE_X = 3224
        const val FURNACE_Z = 3218
        const val ANVIL_X = 3220
        const val ANVIL_Z = 3218
        const val BACKPACK = 93
        const val SMITHING = 13
        const val COPPER = 436
        const val TIN = 438
        const val IRON_ORE = 440
        const val DAGGER = 9
        const val DAGGER_OBJ = 1205
        const val SCIMITAR = 11
        const val SCIMITAR_OBJ = 1321
        const val PLATEBODY = 22
        const val NAILS = 23
        const val NAILS_OBJ = 4819
        const val MAKE_X = 6
        const val MAKE_ALL = 7
        const val VARP_SMITHBARS = 210
        const val VARP_LAST = 549
        const val ENTER_AMOUNT_SCRIPT = 108
        val VARS = VarTypes(
            6_000,
            mapOf(
                Smithing.VARBIT_BAR_TYPE to VarbitType(VARP_SMITHBARS, 0, 2),
                Smithing.VARBIT_LAST_BAR to VarbitType(VARP_LAST, 24, 26),
                Smithing.VARBIT_LAST_ITEM to VarbitType(VARP_LAST, 27, 31),
            ),
        )
    }
}
