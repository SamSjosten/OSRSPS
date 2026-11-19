package osrsps.testclient

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import osrsps.game.GameRandom
import osrsps.game.ObjType
import osrsps.game.ObjTypes
import osrsps.game.PlayerVars
import osrsps.game.Quests
import osrsps.game.VarTypes
import osrsps.game.VarbitType
import osrsps.game.World
import osrsps.game.content.PalaceGarden
import osrsps.game.loc.LocType
import osrsps.game.loc.LocTypes
import osrsps.game.loc.StaticLocs
import osrsps.game.zone.ZoneLoc
import osrsps.map.CollisionMap
import osrsps.map.LocCollisionType
import osrsps.map.LocShape

/**
 * Post-quest content step 1 through the headless client: the White Tree in Varrock Palace's garden, as Garden of
 * Tranquillity leaves it. A new account finds four fruit on it; each Pick-fruit gives one and 12 Farming XP, a full
 * inventory is told so, and the fruit grow back one a farming tick, up to four. The tree is the map's multiloc 9209 on
 * varbit 985 (bits 0 to 4 of a varp of the test's). The rl-client script test-runner/examples/palace-garden.json does
 * the same in the real client.
 */
class HeadlessPalaceGardenTest {
    private val clock = AtomicLong(1_794_614_400_000L)

    private fun server(dir: Path) = TestServer(dir) { crcs, millis ->
        val objs = Array<ObjType?>(OBJS) { ObjType(it, "Obj $it") }
        objs[PalaceGarden.WHITE_TREE_FRUIT] = ObjType(PalaceGarden.WHITE_TREE_FRUIT, "White tree fruit")
        val tree = LocType(
            PalaceGarden.WHITE_TREE, "null", List(5) { null }, LocCollisionType(width = 2, length = 2), multiloc = true,
            multiVarbit = PalaceGarden.VARBIT_WHITE_TREE, multiLocs = List(8) { -1 } + listOf(GROWN, FRUIT_1, FRUIT_1 + 1, FRUIT_1 + 2, FRUIT_1 + 3, -1),
        )
        val forms = mapOf(GROWN to LocType(GROWN, "White Tree patch", List(5) { null }, LocCollisionType(width = 2, length = 2))) +
            (0 until 4).associate { FRUIT_1 + it to LocType(FRUIT_1 + it, "White Tree patch", listOf("Pick-fruit", null, null, null, null), LocCollisionType(width = 2, length = 2)) }
        World(
            crcs, CollisionMap().apply { markLoaded(X shr 6, Z shr 6) }, objTypes = ObjTypes(objs),
            varTypes = VarTypes(6_000, mapOf(PalaceGarden.VARBIT_WHITE_TREE to VarbitType(TREE_VARP, 0, 4)), 21_000),
            random = GameRandom(1),
            locTypes = LocTypes(mapOf(PalaceGarden.WHITE_TREE to tree) + forms),
            staticLocs = StaticLocs().apply { add(ZoneLoc(PalaceGarden.WHITE_TREE, LocShape.CENTREPIECE_STRAIGHT, 1, TREE_X, TREE_Z, 0)) },
            farmingClock = clock::get,
            farmingDiseases = false,
            cycleMillis = millis,
        )
    }

    private fun HeadlessClient.tree() = varp(TREE_VARP) and 0x1F

    private fun HeadlessClient.awaitTree(value: Int) =
        await("the White Tree at $value (${tree()})") { if (tree() == value) Unit else null }

    private fun HeadlessClient.fruit() = inventory(BACKPACK).filterNotNull().filter { it.id == PalaceGarden.WHITE_TREE_FRUIT }.sumOf { it.count }

    private fun HeadlessClient.awaitFruit(count: Int) = await("$count fruit (${fruit()})") { if (fruit() == count) Unit else null }

    private fun HeadlessClient.xp() = stat(FARMING)?.xp ?: 0

    private fun HeadlessClient.toGarden() {
        await("the login rebuild") { position }
        command("tele $X $Z")
        await("beside the White Tree") { position?.takeIf { it.x == X && it.z == Z } }
        command("farmclock pause")
    }

    @Test
    fun `the White Tree bears four fruit, each picked for 12 XP, and they grow back one a farming tick`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("alice").use { client ->
                client.toGarden()
                // Set fully grown by the quests (8), and the four fruit hung on it at once.
                client.awaitTree(12)
                val xp = client.xp()
                for (left in 3 downTo 0) {
                    client.locOp(PalaceGarden.WHITE_TREE, TREE_X, TREE_Z, 1)
                    client.awaitTree(8 + left)
                }
                client.awaitFruit(4)
                assertEquals(xp + 48, client.await("48 XP") { client.xp().takeIf { it == xp + 48 } })
                assertEquals(4, client.messages().count { it.text == "[palace_garden.picked]" })

                // One fruit a farming tick, and never more than four.
                client.command("farmtick 1")
                client.awaitTree(9)
                client.command("farmtick 10")
                client.awaitTree(12)
            }
        }
    }

    @Test
    fun `a full inventory leaves the fruit on the tree`(@TempDir dir: Path) {
        server(dir).use { server ->
            server.login("bob").use { client ->
                client.toGarden()
                client.awaitTree(12)
                client.command("item 1 28")
                client.await("a full pack") { if (client.inventory(BACKPACK).count { it != null } == 28) Unit else null }
                client.locOp(PalaceGarden.WHITE_TREE, TREE_X, TREE_Z, 1)
                client.await("the no-room line") { client.messages().firstOrNull { it.text == "[palace_garden.no_room]" } }
                client.awaitTicks(2)
                assertEquals(12, client.tree())
            }
        }
    }

    @Test
    fun `the quests leave the garden grown, both statues placed, Lumbridge's plinth bare and Marlo's house mended`() {
        val ids = listOf(979, 980, 981, 982, 983, 984, 985, 986, 987, 963, 964, 10563, 10564, 10565, 10566, 10567, 10568, 10569, 2567)
        val vars = PlayerVars(VarTypes(6_000, ids.withIndex().associate { (i, id) -> id to VarbitType(2000 + i, 0, 7) }))
        Quests.NONE.completeAll(vars)
        // Every flower bed, the vines, the rose bushes and both orchid pots fully grown; the White Tree grown, bare until
        // PalaceGarden hangs its fruit.
        for (bed in listOf(979, 980, 981, 982, 983, 984, 986, 987)) assertEquals(7, vars.getVarbit(bed), "bed $bed")
        assertEquals(8, vars.getVarbit(985))
        assertEquals(2, vars.getVarbit(963))
        assertEquals(2, vars.getVarbit(964))
        for (piece in 10563..10569) assertEquals(3, vars.getVarbit(piece), "Marlo's furniture $piece")
        assertEquals(1, vars.getVarbit(2567))
    }

    companion object {
        const val OBJS = 7_000
        const val TREE_VARP = 1000
        /** garden_white_tree_fullygrown and garden_white_tree_fruit_1, the cache's forms. */
        const val GROWN = 9218
        const val FRUIT_1 = 9219
        const val TREE_X = 3229
        const val TREE_Z = 3474
        /** The tile south of the tree. */
        const val X = 3229
        const val Z = 3473
        const val BACKPACK = 93
        const val FARMING = 19
    }
}
