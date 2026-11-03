package osrsps.testclient

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.InventoryTab
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.WearPos
import osrsps.game.World
import osrsps.game.combat.AttackStyles
import osrsps.game.content.TrainingDummies
import osrsps.game.content.WaterSources
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.skill.Cooking
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Varrock step 11 through the headless client: its range and oven cook, its dying trees chop down to their stump, its
 * sinks fill buckets, jugs and bowls (and Herblore's vials), its wells only buckets, and its dummies give Attack XP
 * until level 8. Its mines and anvils, oaks and yews are the ids the mining, smithing and woodcutting tests already cover. The
 * rl-client script test-runner/examples/varrock-skilling.json does the same in the real city.
 */
class HeadlessVarrockSkillingTest {
    private val none = List<String?>(5) { null }
    private fun loc(id: Int, name: String, vararg ops: String?) =
        LocType(id, name, ops.toList() + List(5 - ops.size) { null }, LocCollisionType(width = 1, length = 1))

    // The unarmed and bow rows of dbtable 78 as the 241 cache has them, as HeadlessRangedTest's.
    private val styles = AttackStyles.fromRows(
        listOf(
            listOf<Any>(0) to listOf<Any>(
                0, "Punch", "(Accurate)<br>(Crush)<br>(Attack XP)", 0,
                1, "Kick", "(Aggressive)<br>(Crush)<br>(Strength XP)", 0,
                3, "Block", "(Defensive)<br>(Crush)<br>(Defence XP)", 0,
            ),
            listOf<Any>(3) to listOf<Any>(
                0, "Accurate", "(Accurate)<br>(Standard)<br>(Ranged XP)", 0,
                1, "Rapid", "(Rapid)<br>(Standard)<br>(Ranged XP)", 0,
                3, "Longrange", "(Longrange)<br>(Standard)<br>(Ranged XP)<br>(Defence XP)", 0,
            ),
        ),
    )

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = arrayOfNulls<ObjType>(maxOf(Cooking.SHRIMPS.burnt, SHORTBOW) + 1)
        for (id in listOf(Cooking.SHRIMPS.raw, Cooking.SHRIMPS.cooked, Cooking.SHRIMPS.burnt)) objs[id] = ObjType(id, "Food $id")
        for ((empty, full) in WaterSources.VESSELS.map { it.empty to it.full } + (VIAL to VIAL_OF_WATER)) {
            objs[empty] = ObjType(empty, "Empty $empty")
            objs[full] = ObjType(full, "Full $full")
        }
        objs[LOGS] = ObjType(LOGS, "Logs")
        objs[BRONZE_AXE] = ObjType(BRONZE_AXE, "Bronze axe")
        // Shaped like the 241 cache's, as HeadlessRangedTest's: obj category 64 is bows.
        objs[SHORTBOW] = ObjType(SHORTBOW, "Shortbow", iops = listOf(null, "Wield", null, null, "Drop"), wearpos1 = WearPos.WEAPON, params = mapOf(4 to 8, 14 to 4), internalName = "shortbow", category = 64)
        World(
            crcs, CollisionMap().apply { markLoaded(3222 shr 6, 3218 shr 6) }, objTypes = ObjTypes(objs), random = GameRandom(3), attackStyles = styles,
            locTypes = LocTypes(
                listOf(
                    loc(Cooking.VARROCK_RANGE, "Range", "Cook"),
                    loc(Cooking.VARROCK_OVEN, "Oven", "Cook"),
                    loc(DYING_TREE, "Dying tree", "Chop down"),
                    LocType(DYING_STUMP, "Dying tree Stump", none, LocCollisionType(width = 1, length = 1)),
                    loc(SINK, "Sink"),
                    loc(WELL, "Well"),
                    loc(TrainingDummies.DUMMY, "Dummy", "Attack"),
                ).associateBy { it.id },
            ),
            staticLocs = StaticLocs().apply {
                add(ZoneLoc(Cooking.VARROCK_RANGE, LocShape.CENTREPIECE_STRAIGHT, 0, 3226, 3218, 0))
                add(ZoneLoc(Cooking.VARROCK_OVEN, LocShape.CENTREPIECE_STRAIGHT, 0, 3226, 3222, 0))
                add(ZoneLoc(DYING_TREE, LocShape.CENTREPIECE_STRAIGHT, 0, 3218, 3218, 0))
                add(ZoneLoc(SINK, LocShape.CENTREPIECE_STRAIGHT, 0, 3218, 3222, 0))
                add(ZoneLoc(WELL, LocShape.CENTREPIECE_STRAIGHT, 0, 3214, 3222, 0))
                add(ZoneLoc(TrainingDummies.DUMMY, LocShape.CENTREPIECE_STRAIGHT, 0, 3214, 3218, 0))
            },
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.count(id: Int) = inventory(BACKPACK).filter { it?.id == id }.sumOf { it!!.count }

    private fun HeadlessClient.slotOf(id: Int) = inventory(BACKPACK).indexOfFirst { it?.id == id }

    private fun HeadlessClient.seen(text: String) = await(text) { messages().firstOrNull { it.text == text } }

    private fun HeadlessClient.locAt(x: Int, z: Int) = locChanges().lastOrNull { it.coord.x == x && it.coord.z == z }?.id

    private fun HeadlessClient.give(id: Int, count: Int = 1) {
        val before = count(id)
        command("item $id $count")
        await("$count of $id") { if (count(id) == before + count) Unit else null }
    }

    private fun HeadlessClient.use(id: Int, loc: Int, x: Int, z: Int) =
        locTarget(loc, x, z, InventoryTab.INTERFACE, InventoryTab.ITEMS, slotOf(id), id)

    @Test
    fun `Varrock's range and oven cook`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.await("the login rebuild") { client.position }
                client.command("setlevel cooking 99")
                for ((id, z) in listOf(Cooking.VARROCK_RANGE to 3218, Cooking.VARROCK_OVEN to 3222)) {
                    val before = client.count(Cooking.SHRIMPS.cooked)
                    client.give(Cooking.SHRIMPS.raw)
                    client.locOp(id, 3226, z, 1)
                    client.await("a cooked shrimp on $id", timeoutMillis = 30_000) { if (client.count(Cooking.SHRIMPS.cooked) == before + 1) Unit else null }
                    assertEquals(0, client.count(Cooking.SHRIMPS.raw))
                }
            }
        }
    }

    @Test
    fun `a dying tree gives logs and turns to its stump`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.await("the login rebuild") { client.position }
                client.give(BRONZE_AXE)
                client.locOp(DYING_TREE, 3218, 3218, 1)
                client.await("logs", timeoutMillis = 30_000) { if (client.count(LOGS) == 1) Unit else null }
                assertEquals(25, client.await("Woodcutting XP") { client.stat(WOODCUTTING)?.takeIf { it.xp > 0 } }.xp)
                client.await("the stump") { if (client.locAt(3218, 3218) == DYING_STUMP) Unit else null }
            }
        }
    }

    @Test
    fun `a sink fills every empty of a kind, a well only buckets`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("carol").use { client ->
                client.await("the login rebuild") { client.position }
                val bucket = WaterSources.BUCKET
                client.give(bucket.empty, 2)
                client.give(VIAL)
                client.use(bucket.empty, SINK, 3218, 3222)
                client.await("both buckets filled", timeoutMillis = 15_000) { if (client.count(bucket.full) == 2) Unit else null }
                client.seen("[water.fill_bucket source=sink]")
                // The vial wasn't touched: only the kind used fills. Vials are Herblore's, and fill at Varrock's sinks too.
                assertEquals(1, client.count(VIAL))
                client.use(VIAL, SINK, 3218, 3222)
                client.await("the vial filled", timeoutMillis = 15_000) { if (client.count(VIAL_OF_WATER) == 1) Unit else null }

                for (v in listOf(WaterSources.JUG, WaterSources.BOWL)) {
                    client.give(v.empty)
                    client.use(v.empty, SINK, 3218, 3222)
                    client.await("${v.empty} filled", timeoutMillis = 15_000) { if (client.count(v.full) == 1) Unit else null }
                }

                client.give(VIAL)
                client.use(VIAL, WELL, 3214, 3222)
                client.seen("[game.nothing_interesting]")
                assertEquals(1, client.count(VIAL))
                client.give(bucket.empty)
                client.use(bucket.empty, WELL, 3214, 3222)
                client.await("a bucket filled at the well", timeoutMillis = 15_000) { if (client.count(bucket.full) == 3) Unit else null }
                client.seen("[water.fill_bucket source=well]")
            }
        }
    }

    @Test
    fun `a sink fills one a tick, whether the player walked there or stood beside it`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("erin").use { client ->
                client.await("the login rebuild") { client.position }
                val bucket = WaterSources.BUCKET
                val fills = "[water.fill_bucket source=sink]"
                fun fillTicks() = client.messages().filter { it.text == fills }.map { it.tick }
                // From two tiles off: a step there, then a fill each tick.
                client.command("tele 3218 3224 0")
                client.await("the teleport") { client.position?.takeIf { it == Coord(3218, 3224, 0) } }
                client.give(bucket.empty, 3)
                client.use(bucket.empty, SINK, 3218, 3222)
                client.await("three filled", timeoutMillis = 15_000) { if (client.count(bucket.full) == 3) Unit else null }
                client.await("three messages") { fillTicks().takeIf { it.size == 3 } }
                val walked = fillTicks()
                assertEquals(listOf(1, 1), walked.zipWithNext { a, b -> b - a }, "fill ticks $walked")
                // Already beside it: the first at once, the rest a tick apart all the same.
                client.give(bucket.empty, 3)
                client.use(bucket.empty, SINK, 3218, 3222)
                client.await("six filled", timeoutMillis = 15_000) { if (client.count(bucket.full) == 6) Unit else null }
                client.await("six messages") { fillTicks().takeIf { it.size == 6 } }
                val beside = fillTicks().drop(3)
                assertEquals(listOf(1, 1), beside.zipWithNext { a, b -> b - a }, "fill ticks $beside")
            }
        }
    }

    @Test
    fun `a dummy gives Attack XP below level 8, then nothing more`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("dave").use { client ->
                client.await("the login rebuild") { client.position }
                client.locOp(TrainingDummies.DUMMY, 3214, 3218, TrainingDummies.ATTACK)
                assertEquals(5, client.await("Attack XP") { client.stat(ATTACK)?.takeIf { it.xp > 0 } }.xp)
                client.seen("[dummy.hit]")
                // A second click straight away comes before the weapon could swing again: no more XP from it.
                client.locOp(TrainingDummies.DUMMY, 3214, 3218, TrainingDummies.ATTACK)
                client.awaitTicks(1)
                assertEquals(5, client.stat(ATTACK)!!.xp)
                client.awaitTicks(4)
                client.locOp(TrainingDummies.DUMMY, 3214, 3218, TrainingDummies.ATTACK)
                assertEquals(10, client.await("more Attack XP") { client.stat(ATTACK)?.takeIf { it.xp >= 10 } }.xp)

                client.command("setlevel attack 8")
                client.await("Attack 8") { client.stat(ATTACK)?.takeIf { it.level == 8 } }
                val xp = client.stat(ATTACK)!!.xp
                client.awaitTicks(5)
                client.locOp(TrainingDummies.DUMMY, 3214, 3218, TrainingDummies.ATTACK)
                client.seen("[dummy.nothing_more]")
                assertEquals(xp, client.stat(ATTACK)!!.xp)

                // A bow's styles are ranged: the dummy refuses them, whatever the level.
                client.command("setlevel attack 1")
                client.await("Attack 1") { client.stat(ATTACK)?.takeIf { it.level == 1 } }
                client.give(SHORTBOW)
                client.ifButton(InventoryTab.INTERFACE, InventoryTab.ITEMS, WIELD_BUTTON_OP, client.slotOf(SHORTBOW), SHORTBOW)
                client.await("the bow worn") { client.inventory(WORN).getOrNull(WearPos.WEAPON)?.takeIf { it.id == SHORTBOW } }
                val before = client.stat(ATTACK)!!.xp
                client.locOp(TrainingDummies.DUMMY, 3214, 3218, TrainingDummies.ATTACK)
                client.seen("[dummy.no_ranged]")
                client.awaitTicks(5)
                assertEquals(before, client.stat(ATTACK)!!.xp)
            }
        }
    }

    private companion object {
        const val BACKPACK = 93
        const val ATTACK = 0
        const val WOODCUTTING = 8
        const val LOGS = 1511
        const val BRONZE_AXE = 1351
        const val DYING_TREE = 3648
        const val DYING_STUMP = 3649
        const val SINK = 1763
        const val WELL = 3646
        const val VIAL = 229
        const val SHORTBOW = 841
        const val WORN = 94
        /** iop2 (Wield) is button op 3 (see InventoryTab.click). */
        const val WIELD_BUTTON_OP = 3
        const val VIAL_OF_WATER = 227
    }
}
