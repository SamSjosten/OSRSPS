package osrsps.testclient

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
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
        )) objs[id] = ObjType(id, name)
        objs[POTATO_SEED] = ObjType(POTATO_SEED, "Potato seed", stackable = true)
        objs[WATERMELON_SEED] = ObjType(WATERMELON_SEED, "Watermelon seed", stackable = true)
        for (id in listOf(5331) + (5333..5340)) objs[id] = ObjType(id, "Watering can")
        World(
            crcs, CollisionMap().apply { markLoaded(X shr 6, Z shr 6) }, objTypes = ObjTypes(objs),
            varTypes = VarTypes(6_000, mapOf(TRANSMIT_A to VarbitType(529, 0, 7), TRANSMIT_B to VarbitType(529, 8, 15)), 21_000),
            random = GameRandom(1),
            locTypes = LocTypes(
                mapOf(
                    ALLOTMENT to LocType(ALLOTMENT, "null", List(5) { null }, LocCollisionType(width = 1, length = 1), multiloc = true),
                ),
            ),
            staticLocs = StaticLocs().apply { for (x in 3050..3054) add(ZoneLoc(ALLOTMENT, LocShape.CENTREPIECE_STRAIGHT, 0, x, PATCH_Z, 0)) },
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
