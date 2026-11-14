package osrsps.testclient

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
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
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.farming.FarmingPatches
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Farming step 1 through the headless client: Falador's north-west allotment raked, planted, watered, grown with
 * `::farmtick` and on the clock, harvested until it empties and growing weeds again; and a crop that grows while its
 * planter is logged out. The patch's state is read the way the client draws it: the `farming_transmit_a` varbit, bits
 * 0 to 7 of varp 529. The rl-client script test-runner/examples/farming-allotment.json does the first in the real client,
 * at the real patch.
 */
class HeadlessFarmingTest {
    /** The farming clock the world runs on, moved by the tests: 2026-11-14 00:00 UTC to start. */
    private val clock = AtomicLong(1_794_614_400_000L)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(SNAPE_GRASS_SEED + 1)
        for ((id, name) in mapOf(
            RAKE to "Rake", DIBBER to "Seed dibber", SPADE to "Spade", WEEDS to "Weeds", POTATO_SEED to "Potato seed",
            POTATO to "Potato", WATERMELON_SEED to "Watermelon seed", SNAPE_GRASS_SEED to "Snape grass seed",
            GRIMY_GUAM to "Grimy guam leaf", LIMPWURT_ROOT to "Limpwurt root",
        )) objs[id] = ObjType(id, name)
        for ((id, name) in mapOf(
            POTATO_SEED to "Potato seed", WATERMELON_SEED to "Watermelon seed", GUAM_SEED to "Guam seed", LIMPWURT_SEED to "Limpwurt seed",
        )) objs[id] = ObjType(id, name, stackable = true)
        for (id in listOf(5331) + (5333..5340)) objs[id] = ObjType(id, "Watering can")
        World(
            crcs, CollisionMap().apply { markLoaded(X shr 6, Z shr 6) }, objTypes = ObjTypes(objs),
            varTypes = VarTypes(
                6_000,
                mapOf(
                    TRANSMIT_A to VarbitType(529, 0, 7), TRANSMIT_B to VarbitType(529, 8, 15),
                    TRANSMIT_C to VarbitType(529, 16, 23), TRANSMIT_D to VarbitType(529, 24, 31),
                ),
                21_000,
            ),
            random = GameRandom(1),
            locTypes = LocTypes(
                mapOf(
                    ALLOTMENT to LocType(ALLOTMENT, "null", List(5) { null }, LocCollisionType(width = 1, length = 1), multiloc = true),
                    FLOWER to LocType(FLOWER, "null", List(5) { null }, LocCollisionType(width = 2, length = 2), multiloc = true),
                    HERB to LocType(HERB, "null", List(5) { null }, LocCollisionType(width = 2, length = 2), multiloc = true),
                ),
            ),
            staticLocs = StaticLocs().apply {
                for (x in 3050..3053) add(ZoneLoc(ALLOTMENT, LocShape.CENTREPIECE_STRAIGHT, 0, x, PATCH_Z, 0))
                add(ZoneLoc(FLOWER, LocShape.CENTREPIECE_STRAIGHT, 0, FLOWER_X, FLOWER_Z, 0))
                add(ZoneLoc(HERB, LocShape.CENTREPIECE_STRAIGHT, 0, HERB_X, HERB_Z, 0))
            },
            farmingClock = clock::get,
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filterNotNull().filter { it.id == id }.sumOf { it.count }

    private fun HeadlessClient.slotOf(id: Int) = inventory(BACKPACK).indexOfFirst { it?.id == id }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    /** The north-west allotment as the client draws it. */
    private fun HeadlessClient.patch() = varp(529) and 0xFF

    private fun HeadlessClient.awaitPatch(value: Int, timeoutMillis: Long = 10_000) =
        await("the patch at $value (it is ${patch()})", timeoutMillis) { if (patch() == value) Unit else null }

    private fun HeadlessClient.xp() = stat(FARMING)?.xp ?: 0

    private fun HeadlessClient.give(item: Int, count: Int = 1) {
        val before = count(item)
        command("item $item $count")
        await("item $item") { if (count(item) >= before + count) Unit else null }
    }

    private fun HeadlessClient.use(item: Int) = locTarget(ALLOTMENT, 3052, PATCH_Z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(item), item)

    private fun HeadlessClient.useOn(loc: Int, x: Int, z: Int, item: Int) = locTarget(loc, x, z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(item), item)

    /** Bits 16 to 23 of varp 529, `farming_transmit_c`: the flower patch; 24 to 31, `_d`: the herb patch. */
    private fun HeadlessClient.flower() = (varp(529) ushr 16) and 0xFF

    private fun HeadlessClient.herb() = (varp(529) ushr 24) and 0xFF

    private fun HeadlessClient.awaitValue(what: String, value: Int, read: HeadlessClient.() -> Int) =
        await("the $what at $value (it is ${read()})") { if (read() == value) Unit else null }

    private fun HeadlessClient.toFalador() {
        await("the login rebuild") { position }
        command("tele $X $Z")
        await("at Falador") { position?.takeIf { it.x == X && it.z == Z } }
    }

    @Test
    fun `an allotment is raked, planted, watered, grown, harvested and grows weeds again`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.toFalador()
                // A patch nobody has touched is overgrown: value 0, as every varbit starts.
                assertEquals(0, client.patch())

                client.locOp(ALLOTMENT, 3052, PATCH_Z, 1)
                client.seen("[farming.need_rake]")
                client.give(RAKE)
                client.locOp(ALLOTMENT, 3052, PATCH_Z, 1)
                client.seen("[farming.raked]")
                // Three weeds, 4 XP each, and the patch drawn raked (the inventory can land after the message).
                client.awaitPatch(3)
                client.await("three weeds") { if (client.count(WEEDS) == 3) Unit else null }
                client.await("12 XP") { if (client.xp() == 12) Unit else null }

                client.give(POTATO_SEED, 2)
                client.use(POTATO_SEED)
                client.seen("[farming.need_dibber]")
                client.give(DIBBER)
                client.use(POTATO_SEED)
                client.seen("[farming.need_seeds count=3 patch=[farming.patch_allotment]]")
                client.give(POTATO_SEED, 1)
                client.use(POTATO_SEED)
                client.seen("[farming.planted count=3 seeds=potato seed patch=[farming.patch_allotment]]")
                client.awaitPatch(6)
                client.await("the seeds used") { if (client.count(POTATO_SEED) == 0) Unit else null }
                // The planting XP waits for the last crop.
                assertEquals(12, client.xp())

                // Watering draws the watered seed and takes a dose.
                client.give(CAN_8)
                client.use(CAN_8)
                client.awaitPatch(70)
                client.await("a dose used") { if (client.count(CAN_8 - 1) == 1) Unit else null }

                // Any two farming ticks hold one ten-minute allotment tick: one stage, and the water is used up.
                client.command("farmtick 2")
                client.awaitPatch(7)
                // Six more hold three: grown, drawn with its three lives.
                client.command("farmtick 6")
                client.awaitPatch(10)

                client.locOp(ALLOTMENT, 3052, PATCH_Z, 1)
                client.seen("[farming.need_spade]")
                client.give(SPADE)
                client.locOp(ALLOTMENT, 3052, PATCH_Z, 1)
                client.seen("[farming.harvest_start patch=[farming.patch_allotment]]")
                client.seen("[farming.empty patch=[farming.patch_allotment]]")
                client.awaitPatch(3)
                // Every potato is 9 XP and the planting's 8 comes with the last; wait for the last stat and inventory updates.
                client.await("the harvest XP (${client.xp()} XP, ${client.count(POTATO)} potatoes)") {
                    if (client.count(POTATO) >= 3 && client.xp() == 12 + 8 + 9 * client.count(POTATO)) Unit else null
                }

                // Bare soil grows a weed stage each farming tick until it is overgrown again.
                client.command("farmtick 1")
                client.awaitPatch(2)
                client.command("farmtick 2")
                client.awaitPatch(0)
            }
        }
    }

    @Test
    fun `seeds need the level and a raked empty patch, and crops grow on the clock while the planter is away`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.toFalador()
                client.give(DIBBER)
                client.give(WATERMELON_SEED, 3)
                client.use(WATERMELON_SEED)
                client.seen("[farming.needs_weeding]")
                client.give(RAKE)
                client.locOp(ALLOTMENT, 3052, PATCH_Z, 1)
                client.awaitPatch(3)
                client.use(WATERMELON_SEED)
                client.seen("[farming.level level=47]")
                client.command("setlevel farming 47")
                client.await("Farming 47") { client.stat(FARMING)?.takeIf { it.level == 47 } }
                client.use(WATERMELON_SEED)
                client.awaitPatch(52)
                client.give(POTATO_SEED, 3)
                client.use(POTATO_SEED)
                client.seen("[farming.occupied]")
                client.ifButton(World.LOGOUT_TAB, World.LOGOUT_BUTTON, 1)
                client.await("the logout") { client.packets().firstOrNull { it.prot.name == "LOGOUT" } }
            }
            waitFor("bob gone") { if (server.world.playerNamed("bob") == null) Unit else null }
            // Eight ten-minute ticks grow a watermelon; an hour and a half away covers them whatever the offset.
            clock.addAndGet(90 * 60_000L)
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.awaitPatch(60)
                val patch = server.world.playerNamed("bob")!!.farming.patches.getValue(FarmingPatches.FALADOR_NW.key)
                assertEquals(listOf("watermelon", true, 3), listOf(patch.crop?.key, patch.grown, patch.lives))
            }
        }
    }

    @Test
    fun `a paused farming clock grows crops only by farmtick, and catches up when it runs again`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.toFalador()
                client.give(RAKE)
                client.give(DIBBER)
                client.locOp(ALLOTMENT, 3052, PATCH_Z, 1)
                client.awaitPatch(3)
                client.give(POTATO_SEED, 3)
                client.use(POTATO_SEED)
                client.awaitPatch(6)
                client.command("farmclock pause")
                client.seen("Farming clock paused: crops grow only by ::farmtick")
                // An hour of the real clock grows nothing while it is held; ::farmtick still does.
                clock.addAndGet(60 * 60_000L)
                client.command("farmtick 2")
                client.awaitPatch(7)
                client.command("farmclock run")
                // Run again, the crop catches up on the hour: grown.
                client.awaitPatch(10)
            }
        }
    }

    @Test
    fun `a flower is watered, grown and picked once, and a herb is picked with a spade until it is empty`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.toFalador()
                client.command("setlevel farming 26")
                client.await("Farming 26") { client.stat(FARMING)?.takeIf { it.level == 26 } }
                val start = client.xp()
                client.give(RAKE)
                client.give(DIBBER)

                // A limpwurt: raked, planted, watered, four 5-minute ticks to grow, then picked by hand for 3 to 5 roots.
                client.locOp(FLOWER, FLOWER_X, FLOWER_Z, 1)
                client.awaitValue("flower patch", 3) { flower() }
                client.give(LIMPWURT_SEED)
                client.useOn(FLOWER, FLOWER_X, FLOWER_Z, LIMPWURT_SEED)
                client.awaitValue("flower patch", 28) { flower() }
                client.give(CAN_8)
                client.useOn(FLOWER, FLOWER_X, FLOWER_Z, CAN_8)
                client.awaitValue("flower patch", 92) { flower() }
                client.command("farmtick 4")
                client.awaitValue("flower patch", 32) { flower() }
                client.locOp(FLOWER, FLOWER_X, FLOWER_Z, 1)
                client.seen("[farming.picked produce=limpwurt root]")
                client.awaitValue("flower patch", 3) { flower() }
                // Both XPs at once, 21.5 and 120, on top of the raking's 12.
                client.await("the limpwurt XP") { if (client.xp() == start + 12 + 141) Unit else null }
                val roots = client.count(LIMPWURT_ROOT)
                assertTrue(roots in 3..5, "3 roots and 0 to 2 more at level 26, not $roots")

                // A guam: one seed, four 20-minute growths (16 farming ticks), then Pick with a spade, a herb at a time.
                val beforeHerb = client.xp()
                client.locOp(HERB, HERB_X, HERB_Z, 1)
                client.awaitValue("herb patch", 3) { herb() }
                client.give(GUAM_SEED)
                client.useOn(HERB, HERB_X, HERB_Z, GUAM_SEED)
                client.awaitValue("herb patch", 4) { herb() }
                // Any 16 farming ticks hold exactly four of a herb's 20-minute ticks.
                client.command("farmtick 16")
                client.awaitValue("herb patch", 8) { herb() }
                client.locOp(HERB, HERB_X, HERB_Z, 1)
                client.seen("[farming.need_spade]")
                client.give(SPADE)
                client.locOp(HERB, HERB_X, HERB_Z, 1)
                client.seen("[farming.harvest_start patch=[farming.patch_herb]]")
                client.seen("[farming.empty patch=[farming.patch_herb]]")
                client.awaitValue("herb patch", 3) { herb() }
                // 12.5 XP a herb, the planting's 11 with the last, 12 for the weeds; the client sees whole XP, rounded down.
                client.await("the herb XP (${client.xp() - beforeHerb} XP, ${client.count(GRIMY_GUAM)} herbs)") {
                    val herbs = client.count(GRIMY_GUAM)
                    val tenths = start * 10 + 120 + 1415 + 120 + 110 + 125 * herbs
                    if (herbs >= 3 && client.xp() == tenths / 10) Unit else null
                }
            }
        }
    }

    @Test
    fun `a second click two ticks into a harvest picks two herbs at each roll`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("tele ${HERB_X - 1} $HERB_Z")
                client.await("beside the herb patch") { client.position?.takeIf { it.x == HERB_X - 1 && it.z == HERB_Z } }
                client.command("setlevel farming 99")
                client.await("Farming 99") { client.stat(FARMING)?.takeIf { it.level == 99 } }
                client.give(RAKE)
                client.give(DIBBER)
                client.give(SPADE)
                client.locOp(HERB, HERB_X, HERB_Z, 1)
                client.awaitValue("herb patch", 3) { herb() }
                client.give(GUAM_SEED)
                client.useOn(HERB, HERB_X, HERB_Z, GUAM_SEED)
                client.awaitValue("herb patch", 4) { herb() }
                client.command("farmtick 16")
                client.awaitValue("herb patch", 8) { herb() }

                // Paused and stepped: a click while paused is held for the next tick, so each lands on a known tick.
                client.commandReply("pause") { it.startsWith("Paused after tick") }
                client.locOp(HERB, HERB_X, HERB_Z, 1)
                client.stepTicks(1)
                client.seen("[farming.harvest_start patch=[farming.patch_herb]]")
                // The second click, on the harvest's next tick.
                client.locOp(HERB, HERB_X, HERB_Z, 1)
                val counts = mutableListOf<Int>()
                repeat(60) {
                    if (client.herb() == 3) return@repeat
                    client.stepTicks(1)
                    counts += client.count(GRIMY_GUAM)
                }
                client.commandReply("resume") { it.startsWith("Resumed") }
                assertEquals(3, client.herb(), "the patch empties; herb counts by tick: $counts")
                client.seen("[farming.empty patch=[farming.patch_herb]]")
                // Two herbs at each roll, every 4 ticks from the start: the pack goes from none to two, never one.
                val changes = counts.zipWithNext().filter { (a, b) -> a != b }.map { (a, b) -> b - a }
                assertTrue(1 !in counts && changes.first() == 2 && changes.dropLast(1).all { it == 2 }, "herb counts by tick: $counts")
                assertEquals(2, counts.indexOfFirst { it > 0 }, "the first pair on the third tick after the start: $counts")
                assertEquals(1, client.messages().count { it.text == "[farming.harvest_start patch=[farming.patch_herb]]" })
            }
        }
    }

    private fun <T : Any> waitFor(what: String, timeoutMillis: Long = 10_000, value: () -> T?): T {
        val end = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < end) {
            value()?.let { return it }
            Thread.sleep(20)
        }
        throw AssertionError("Timed out waiting for $what")
    }

    private companion object {
        const val BACKPACK = 93
        const val FARMING = 19
        const val X = 3053
        const val Z = 3306
        const val PATCH_Z = 3307
        const val ALLOTMENT = 8550
        const val TRANSMIT_A = 4771
        const val TRANSMIT_B = 4772
        const val TRANSMIT_C = 4773
        const val TRANSMIT_D = 4774
        const val FLOWER = 7847
        const val FLOWER_X = 3054
        const val FLOWER_Z = 3307
        const val HERB = 8150
        const val HERB_X = 3058
        const val HERB_Z = 3311
        const val GUAM_SEED = 5291
        const val GRIMY_GUAM = 199
        const val LIMPWURT_SEED = 5100
        const val LIMPWURT_ROOT = 225
        const val RAKE = 5341
        const val DIBBER = 5343
        const val SPADE = 952
        const val WEEDS = 6055
        const val POTATO_SEED = 5318
        const val POTATO = 1942
        const val WATERMELON_SEED = 5321
        const val SNAPE_GRASS_SEED = 22879
        const val CAN_8 = 5340
    }
}
